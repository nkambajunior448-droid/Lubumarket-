import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import dotenv from 'dotenv';
import express, { type Request, type Response, type NextFunction } from 'express';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import {
  INITIAL_CATEGORIES,
  INITIAL_DELIVERY_ZONES,
  INITIAL_PAYMENT_CONFIGS,
  LUBUMARKET_COMMISSION_PERCENT,
  SELLER_APPROVAL_LABELS,
  buildMultiSellerOrderPlan,
  buildValidatedSellerRegistration,
  calculateWithdrawalBreakdown,
  canSellerPublishProduct,
  isValidImageUrlOrDataUri,
  roundMoney,
  type ApprovalStatus,
  type CartCalculationItem,
  type CurrencyCode,
} from './src/shared/domain.ts';

dotenv.config();

const firebaseConfigPath = path.resolve(process.cwd(), 'firebase-applet-config.json');
const firebaseConfig = JSON.parse(fs.readFileSync(firebaseConfigPath, 'utf8')) as {
  projectId: string;
  firestoreDatabaseId: string;
  storageBucket?: string;
};

const PROJECT_ID = firebaseConfig.projectId;
const DATABASE_ID = firebaseConfig.firestoreDatabaseId || '(default)';
const STORAGE_BUCKET = firebaseConfig.storageBucket || `${PROJECT_ID}.firebasestorage.app`;
const DOCS_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/${DATABASE_ID}/documents`;
const COMMIT_URL = `${DOCS_BASE}:commit`;
const RUN_QUERY_URL = `${DOCS_BASE}:runQuery`;
const DOC_PREFIX = `projects/${PROJECT_ID}/databases/${DATABASE_ID}/documents`;

const GOOGLE_JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com')
);

const DEFAULT_BOOTSTRAP_ADMIN_EMAIL =
  (process.env.ADMIN_BOOTSTRAP_EMAIL &&
  process.env.ADMIN_BOOTSTRAP_EMAIL !== 'votre-email-admin@exemple.com'
    ? process.env.ADMIN_BOOTSTRAP_EMAIL
    : 'nkambajunior448@gmail.com'
  ).toLowerCase();

interface VerifiedAuthUser {
  uid: string;
  email: string;
  emailVerified: boolean;
  name: string;
  idToken: string;
}

interface AuthenticatedRequest extends Request {
  authUser?: VerifiedAuthUser;
}

interface FsTimestampMarker {
  __fsTimestamp: string;
}

function isFsTimestampMarker(v: unknown): v is FsTimestampMarker {
  return Boolean(v && typeof v === 'object' && '__fsTimestamp' in (v as Record<string, unknown>));
}

function toFsValue(val: unknown): Record<string, unknown> {
  if (val === null || val === undefined) return { nullValue: null };
  if (isFsTimestampMarker(val)) return { timestampValue: val.__fsTimestamp };
  if (typeof val === 'boolean') return { booleanValue: val };
  if (typeof val === 'number') {
    if (Number.isInteger(val)) return { integerValue: String(val) };
    return { doubleValue: val };
  }
  if (typeof val === 'string') return { stringValue: val };
  if (Array.isArray(val)) {
    return { arrayValue: { values: val.map((item) => toFsValue(item)) } };
  }
  if (typeof val === 'object') {
    const fields: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
      if (v !== undefined) {
        fields[k] = toFsValue(v);
      }
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

function fromFsValue(v: Record<string, unknown> | undefined): unknown {
  if (!v) return null;
  if ('nullValue' in v) return null;
  if ('booleanValue' in v) return Boolean(v.booleanValue);
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return Number(v.doubleValue);
  if ('stringValue' in v) return String(v.stringValue);
  if ('timestampValue' in v) return { __fsTimestamp: String(v.timestampValue) };
  if ('arrayValue' in v) {
    const arr = (v.arrayValue as { values?: Array<Record<string, unknown>> })?.values ?? [];
    return arr.map((item) => fromFsValue(item));
  }
  if ('mapValue' in v) {
    const fields = (v.mapValue as { fields?: Record<string, Record<string, unknown>> })?.fields ?? {};
    const out: Record<string, unknown> = {};
    for (const [k, fv] of Object.entries(fields)) {
      out[k] = fromFsValue(fv);
    }
    return out;
  }
  return null;
}

interface FsDocSnapshot {
  exists: boolean;
  updateTime?: string;
  data: Record<string, any>;
}

async function fsGetDoc(collection: string, docId: string, idToken: string): Promise<FsDocSnapshot> {
  const res = await fetch(`${DOCS_BASE}/${encodeURIComponent(collection)}/${encodeURIComponent(docId)}`, {
    headers: { Authorization: `Bearer ${idToken}` },
  });
  if (res.status === 404) {
    return { exists: false, data: {} };
  }
  if (!res.ok) {
    const txt = await res.text();
    throw new Error(`Erreur lecture Firestore (${collection}/${docId}): ${res.status} ${txt}`);
  }
  const raw = (await res.json()) as {
    fields?: Record<string, Record<string, unknown>>;
    updateTime?: string;
  };
  const data: Record<string, any> = {};
  for (const [k, fv] of Object.entries(raw.fields ?? {})) {
    data[k] = fromFsValue(fv);
  }
  return { exists: true, updateTime: raw.updateTime, data };
}

async function fsQueryDocs(
  collection: string,
  filters: Array<{ field: string; value: unknown }>,
  idToken: string,
  limit = 200
): Promise<Array<FsDocSnapshot>> {
  const structuredQuery: Record<string, unknown> = {
    from: [{ collectionId: collection }],
    limit,
  };

  if (filters.length === 1) {
    structuredQuery.where = {
      fieldFilter: {
        field: { fieldPath: filters[0].field },
        op: 'EQUAL',
        value: toFsValue(filters[0].value),
      },
    };
  } else if (filters.length > 1) {
    structuredQuery.where = {
      compositeFilter: {
        op: 'AND',
        filters: filters.map((f) => ({
          fieldFilter: {
            field: { fieldPath: f.field },
            op: 'EQUAL',
            value: toFsValue(f.value),
          },
        })),
      },
    };
  }

  const res = await fetch(RUN_QUERY_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${idToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ structuredQuery }),
  });

  if (!res.ok) {
    const txt = await res.text();
    throw new Error(`Erreur requête Firestore (${collection}): ${res.status} ${txt}`);
  }

  const rows = (await res.json()) as Array<{
    document?: {
      fields?: Record<string, Record<string, unknown>>;
      updateTime?: string;
    };
  }>;

  const results: Array<FsDocSnapshot> = [];
  for (const row of rows) {
    if (!row.document) continue;
    const data: Record<string, any> = {};
    for (const [k, fv] of Object.entries(row.document.fields ?? {})) {
      data[k] = fromFsValue(fv);
    }
    results.push({ exists: true, updateTime: row.document.updateTime, data });
  }
  return results;
}

interface CommitWriteInput {
  collection: string;
  docId: string;
  fields: Record<string, unknown>;
  serverTimeFields: string[];
  precondition?: { exists?: boolean; updateTime?: string };
}

async function fsAtomicCommit(writes: CommitWriteInput[], idToken: string): Promise<void> {
  if (writes.length === 0) return;
  const formattedWrites = writes.map((w) => {
    const encodedFields: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(w.fields)) {
      if (!w.serverTimeFields.includes(k) && v !== undefined) {
        encodedFields[k] = toFsValue(v);
      }
    }
    const writeObj: Record<string, unknown> = {
      update: {
        name: `${DOC_PREFIX}/${w.collection}/${w.docId}`,
        fields: encodedFields,
      },
    };
    if (w.precondition) {
      writeObj.currentDocument = w.precondition;
    }
    if (w.serverTimeFields.length > 0) {
      writeObj.updateTransforms = w.serverTimeFields.map((fp) => ({
        fieldPath: fp,
        setToServerValue: 'REQUEST_TIME',
      }));
    }
    return writeObj;
  });

  const res = await fetch(COMMIT_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${idToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ writes: formattedWrites }),
  });

  if (!res.ok) {
    const errBody = await res.text();
    throw new Error(`Transaction atomique refusée (${res.status}): ${errBody}`);
  }
}

function makeSafeId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${crypto.randomBytes(4).toString('hex')}`;
}

export function hashDeliveryCode(code: string, salt: string): string {
  return crypto.createHash('sha256').update(`${code.trim()}:${salt}`).digest('hex');
}

export function generateDeliverySecret(): { code: string; salt: string; hash: string } {
  const code = String(crypto.randomInt(100000, 999999));
  const salt = crypto.randomBytes(12).toString('hex');
  const hash = hashDeliveryCode(code, salt);
  return { code, salt, hash };
}

async function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> {
  try {
    const authHeader = req.headers.authorization || '';
    if (!authHeader.startsWith('Bearer ')) {
      res.status(401).json({ error: 'Authentification requise (jeton Bearer manquant).' });
      return;
    }
    const idToken = authHeader.slice(7).trim();
    const { payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
      issuer: `https://securetoken.google.com/${PROJECT_ID}`,
      audience: PROJECT_ID,
    });

    if (!payload.sub) {
      res.status(401).json({ error: 'Jeton Firebase invalide.' });
      return;
    }

    req.authUser = {
      uid: payload.sub,
      email: String(payload.email || '').toLowerCase(),
      emailVerified: Boolean(payload.email_verified),
      name: String(payload.name || payload.email || 'Utilisateur Lubumarket'),
      idToken,
    };
    next();
  } catch (err) {
    res.status(401).json({
      error: 'Session expirée ou jeton invalide. Veuillez vous reconnecter.',
      detail: err instanceof Error ? err.message : String(err),
    });
  }
}

async function verifyIsAdmin(user: VerifiedAuthUser): Promise<boolean> {
  if (!user.emailVerified) return false;
  if (user.email === DEFAULT_BOOTSTRAP_ADMIN_EMAIL) return true;
  const adminSnap = await fsGetDoc('admins', user.uid, user.idToken);
  return adminSnap.exists;
}

function sanitizeIdempotencyKey(raw: unknown): string {
  const s = String(raw || '').trim().replace(/[^a-zA-Z0-9_-]/g, '');
  if (s.length < 6 || s.length > 120) {
    throw new Error('Clé d’idempotence invalide ou manquante.');
  }
  return s;
}

/**
 * Règlement comptable interne d'un vendeur :
 * - Recalcule le solde en attente (commandes actives non encore réglées).
 * - Libère vers le solde disponible (100 % du sous-total, 0 % commission !) uniquement les sous-commandes
 *   dont la livraison est confirmée (`status === 'delivered'`) ET le paiement est vérifié (`paymentStatus === 'confirmed'`).
 */
async function syncSellerWalletLedger(sellerId: string, idToken: string): Promise<FsDocSnapshot> {
  let walletSnap = await fsGetDoc('wallets', sellerId, idToken);
  if (!walletSnap.exists) {
    await fsAtomicCommit(
      [
        {
          collection: 'wallets',
          docId: sellerId,
          fields: {
            sellerId,
            pendingBalanceUSD: 0,
            availableBalanceUSD: 0,
            lockedWithdrawalUSD: 0,
            totalSalesUSD: 0,
            totalWithdrawalsUSD: 0,
            totalCommissionsUSD: 0,
            pendingBalanceCDF: 0,
            availableBalanceCDF: 0,
            lockedWithdrawalCDF: 0,
            totalSalesCDF: 0,
            totalWithdrawalsCDF: 0,
            totalCommissionsCDF: 0,
            ledgerNotice: 'Registre comptable interne Lubumarket — Commission de 5 % uniquement au retrait.',
            lastActionRef: 'init',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        },
      ],
      idToken
    );
    walletSnap = await fsGetDoc('wallets', sellerId, idToken);
  }

  const subDocs = await fsQueryDocs('sellerSubOrders', [{ field: 'sellerId', value: sellerId }], idToken, 200);

  let computedPendingUSD = 0;
  let computedPendingCDF = 0;
  const releasable: Array<FsDocSnapshot> = [];

  for (const sDoc of subDocs) {
    const s = sDoc.data;
    if (s.status === 'cancelled' || s.status === 'refunded') continue;
    if (s.fundsReleased === true) continue;

    if (s.status === 'delivered' && s.paymentStatus === 'confirmed') {
      releasable.push(sDoc);
    } else {
      if (s.currency === 'CDF') {
        computedPendingCDF = roundMoney(computedPendingCDF + Number(s.subtotal || 0), 'CDF');
      } else {
        computedPendingUSD = roundMoney(computedPendingUSD + Number(s.subtotal || 0), 'USD');
      }
    }
  }

  // Libérer chaque sous-commande éligible une par une pour respecter strictement l'invariant getAfter de firestore.rules
  for (const relDoc of releasable) {
    const cur: CurrencyCode = relDoc.data.currency === 'CDF' ? 'CDF' : 'USD';
    const amt = Number(relDoc.data.subtotal || 0);
    const nextWallet: Record<string, any> = {
      ...walletSnap.data,
      pendingBalanceUSD: computedPendingUSD,
      pendingBalanceCDF: computedPendingCDF,
      lastActionRef: String(relDoc.data.id),
    };

    if (cur === 'USD') {
      nextWallet.availableBalanceUSD = roundMoney(Number(walletSnap.data.availableBalanceUSD || 0) + amt, 'USD');
      nextWallet.totalSalesUSD = roundMoney(Number(walletSnap.data.totalSalesUSD || 0) + amt, 'USD');
    } else {
      nextWallet.availableBalanceCDF = roundMoney(Number(walletSnap.data.availableBalanceCDF || 0) + amt, 'CDF');
      nextWallet.totalSalesCDF = roundMoney(Number(walletSnap.data.totalSalesCDF || 0) + amt, 'CDF');
    }

    const txId = makeSafeId('wtx');
    await fsAtomicCommit(
      [
        {
          collection: 'sellerSubOrders',
          docId: String(relDoc.data.id),
          fields: {
            ...relDoc.data,
            fundsReleased: true,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: relDoc.updateTime },
        },
        {
          collection: 'wallets',
          docId: sellerId,
          fields: nextWallet,
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: walletSnap.updateTime },
        },
        {
          collection: 'walletTransactions',
          docId: txId,
          fields: {
            id: txId,
            sellerId,
            type: 'sale_available',
            amount: amt,
            commissionAmount: 0,
            netAmount: amt,
            currency: cur,
            referenceId: String(relDoc.data.id),
            description: `Livraison et paiement vérifiés (${relDoc.data.orderNumber}) — 100 % crédité au solde disponible`,
          },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        },
      ],
      idToken
    );

    walletSnap = await fsGetDoc('wallets', sellerId, idToken);
  }

  // Synchroniser le solde en attente si différent
  if (
    Number(walletSnap.data.pendingBalanceUSD || 0) !== computedPendingUSD ||
    Number(walletSnap.data.pendingBalanceCDF || 0) !== computedPendingCDF
  ) {
    await fsAtomicCommit(
      [
        {
          collection: 'wallets',
          docId: sellerId,
          fields: {
            ...walletSnap.data,
            pendingBalanceUSD: computedPendingUSD,
            pendingBalanceCDF: computedPendingCDF,
            lastActionRef: 'sync-pending',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: walletSnap.updateTime },
        },
      ],
      idToken
    );
    walletSnap = await fsGetDoc('wallets', sellerId, idToken);
  }

  return walletSnap;
}

export async function createLubumarketApp() {
  const app = express();
  app.use(express.json({ limit: '1mb' }));

  // =========================================================================
  // 1. DIGITAL ASSET LINKS (/.well-known/assetlinks.json) POUR GOOGLE PLAY TWA
  // =========================================================================
  app.get('/.well-known/assetlinks.json', (_req, res) => {
    const configuredFingerprint =
      process.env.ANDROID_SHA256_CERT_FINGERPRINT?.trim() ||
      'A_REMPLACER_PAR_EMPREINTE_SHA256_GOOGLE_PLAY_CONSOLE';
    res.setHeader('Content-Type', 'application/json');
    res.json([
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: 'com.lubumarket.app',
          sha256_cert_fingerprints: [configuredFingerprint],
        },
      },
    ]);
  });

  // =========================================================================
  // 2. CONFIGURATION PUBLIQUE SERVEUR (SANS SECRETS)
  // =========================================================================
  app.get('/api/config/public', (_req, res) => {
    res.json({
      platformName: 'LUBUMARKET',
      slogan: 'Achetez en ligne, recevez chez vous.',
      launchCity: 'Lubumbashi, Haut-Katanga, RDC',
      expansionCities: ['Kolwezi', 'Likasi', 'Kinshasa', 'Bukavu', 'Goma', 'Mbuji-Mayi'],
      commissionPercentOnWithdrawalOnly: LUBUMARKET_COMMISSION_PERCENT,
      androidPackageId: 'com.lubumarket.app',
      productionDomainConfigured: Boolean(process.env.PRODUCTION_DOMAIN?.trim()),
      livePaymentGatewaysConfigured: {
        mpesa: Boolean(process.env.MPESA_VODACOM_API_KEY?.trim()),
        airtel_money: Boolean(process.env.AIRTEL_MONEY_CLIENT_SECRET?.trim()),
        orange_money: Boolean(process.env.ORANGE_MONEY_MERCHANT_KEY?.trim()),
        afrimoney: Boolean(process.env.AFRIMONEY_MERCHANT_KEY?.trim()),
        visa_mastercard: Boolean(process.env.CARD_GATEWAY_SECRET_KEY?.trim()),
      },
    });
  });

  // =========================================================================
  // 2B. VÉRIFICATION RÉELLE DU BUCKET FIREBASE STORAGE (SANS FAUSSE CONFIRMATION)
  // =========================================================================
  app.get('/api/storage/status', async (_req, res) => {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 3500);
      const probe = await fetch(
        `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(STORAGE_BUCKET)}/o?maxResults=1`,
        { signal: controller.signal }
      );
      clearTimeout(timer);

      if (probe.status === 404) {
        res.json({
          bucket: STORAGE_BUCKET,
          operational: false,
          httpStatus: 404,
          reason: `Le bucket Firebase Storage (${STORAGE_BUCKET}) n'est pas encore initialisé dans la console Firebase (HTTP 404). Les logos et photos sont compressés et enregistrés en repli sécurisé dans Cloud Firestore jusqu'à l'activation du bucket.`,
        });
        return;
      }

      res.json({
        bucket: STORAGE_BUCKET,
        operational: true,
        httpStatus: probe.status,
        reason: `Bucket Firebase Storage (${STORAGE_BUCKET}) opérationnel.`,
      });
    } catch (err) {
      res.json({
        bucket: STORAGE_BUCKET,
        operational: false,
        httpStatus: 0,
        reason: `Vérification Firebase Storage indisponible : ${err instanceof Error ? err.message : String(err)}`,
      });
    }
  });

  // =========================================================================
  // 3. PROCÉDURE PROTÉGÉE D'INITIALISATION DE L'ADMINISTRATEUR PROPRIÉTAIRE
  // =========================================================================
  app.post('/api/admin/bootstrap', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!user.emailVerified || user.email !== DEFAULT_BOOTSTRAP_ADMIN_EMAIL) {
        res.status(403).json({
          error:
            "Accès refusé : seule l'adresse email propriétaire vérifiée côté serveur est autorisée à activer l'espace Administrateur.",
        });
        return;
      }

      const [adminDoc, userDoc, rateDoc, existingCats, existingZones, existingPayments] =
        await Promise.all([
          fsGetDoc('admins', user.uid, user.idToken),
          fsGetDoc('users', user.uid, user.idToken),
          fsGetDoc('exchangeRates', 'current', user.idToken),
          fsQueryDocs('categories', [], user.idToken, 50),
          fsQueryDocs('deliveryZones', [], user.idToken, 50),
          fsQueryDocs('paymentConfigs', [], user.idToken, 50),
        ]);

      const writes: CommitWriteInput[] = [];

      if (!adminDoc.exists) {
        writes.push({
          collection: 'admins',
          docId: user.uid,
          fields: { uid: user.uid, email: user.email },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        });
      }

      if (!userDoc.exists) {
        writes.push({
          collection: 'users',
          docId: user.uid,
          fields: {
            uid: user.uid,
            displayName: user.name.slice(0, 120),
            email: user.email,
            phone: '',
            role: 'admin',
            status: 'active',
            city: 'Lubumbashi',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      } else if (userDoc.data.role !== 'admin') {
        writes.push({
          collection: 'users',
          docId: user.uid,
          fields: {
            ...userDoc.data,
            role: 'admin',
            status: 'active',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: userDoc.updateTime },
        });
      }

      if (!rateDoc.exists) {
        writes.push({
          collection: 'exchangeRates',
          docId: 'current',
          fields: {
            id: 'current',
            usdToCdf: 2850,
            updatedByUid: user.uid,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { exists: false },
        });
      }

      if (existingCats.length === 0) {
        for (const cat of INITIAL_CATEGORIES) {
          writes.push({
            collection: 'categories',
            docId: cat.id,
            fields: {
              id: cat.id,
              name: cat.name,
              slug: cat.slug,
              description: cat.description,
              sortOrder: cat.sortOrder,
              isActive: true,
            },
            serverTimeFields: ['createdAt', 'updatedAt'],
          });
        }
      }

      if (existingZones.length === 0) {
        for (const z of INITIAL_DELIVERY_ZONES) {
          writes.push({
            collection: 'deliveryZones',
            docId: z.id,
            fields: { ...z },
            serverTimeFields: ['createdAt', 'updatedAt'],
          });
        }
      }

      if (existingPayments.length === 0) {
        for (const p of INITIAL_PAYMENT_CONFIGS) {
          writes.push({
            collection: 'paymentConfigs',
            docId: p.id,
            fields: { ...p },
            serverTimeFields: ['updatedAt'],
          });
        }
      }

      const logId = makeSafeId('log');
      writes.push({
        collection: 'adminLogs',
        docId: logId,
        fields: {
          id: logId,
          actorUid: user.uid,
          actorEmail: user.email,
          action: 'ADMIN_BOOTSTRAP_AND_CONFIG_INIT',
          targetType: 'platform',
          targetId: 'lubumarket',
          details: 'Initialisation sécurisée du propriétaire administrateur, catégories, zones et moyens de paiement.',
        },
        serverTimeFields: ['createdAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, role: 'admin', initializedItems: writes.length });
    } catch (err) {
      res.status(500).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 4. CRÉATION DE COMMANDE MULTI-VENDEURS VÉRIFIÉE CÔTÉ SERVEUR (ZÉRO FUITE)
  // =========================================================================
  app.post('/api/orders/create', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!user.emailVerified) {
        res.status(403).json({ error: 'Adresse email vérifiée requise pour passer commande.' });
        return;
      }

      const idempotencyKey = sanitizeIdempotencyKey(req.body.idempotencyKey);
      const idemSnap = await fsGetDoc('idempotencyKeys', idempotencyKey, user.idToken);
      if (idemSnap.exists) {
        res.json({ ok: true, duplicate: true, orderId: idemSnap.data.resultId });
        return;
      }

      const currency: CurrencyCode = req.body.currency === 'CDF' ? 'CDF' : 'USD';
      const addressId = String(req.body.addressId || '').trim();
      const paymentMethodCode = String(req.body.paymentMethod || 'cod').trim();
      const rawItems = Array.isArray(req.body.items) ? req.body.items : [];

      if (!addressId || rawItems.length === 0 || rawItems.length > 20) {
        res.status(400).json({ error: 'Panier ou adresse de livraison invalide.' });
        return;
      }

      const [addressSnap, rateSnap, paymentConfigDocs] = await Promise.all([
        fsGetDoc('addresses', addressId, user.idToken),
        fsGetDoc('exchangeRates', 'current', user.idToken),
        fsQueryDocs('paymentConfigs', [{ field: 'isEnabled', value: true }], user.idToken, 50),
      ]);

      if (!addressSnap.exists || addressSnap.data.userId !== user.uid) {
        res.status(400).json({ error: 'Adresse de livraison introuvable ou non autorisée.' });
        return;
      }

      const zoneSnap = await fsGetDoc('deliveryZones', addressSnap.data.zoneId, user.idToken);
      const fallbackZone = INITIAL_DELIVERY_ZONES.find(
        (z) => z.id === addressSnap.data.zoneId && z.isActive
      );
      const activeZoneData =
        zoneSnap.exists && zoneSnap.data.isActive
          ? zoneSnap.data
          : !zoneSnap.exists && fallbackZone
            ? fallbackZone
            : null;

      if (!activeZoneData) {
        res.status(400).json({ error: 'La zone de livraison sélectionnée est actuellement indisponible.' });
        return;
      }

      const paymentCfg =
        paymentConfigDocs.length > 0
          ? paymentConfigDocs.find((p) => p.data.code === paymentMethodCode)?.data
          : INITIAL_PAYMENT_CONFIGS.find((p) => p.code === paymentMethodCode && p.isEnabled);
      if (!paymentCfg) {
        res.status(400).json({ error: 'Ce moyen de paiement est actuellement désactivé par Lubumarket.' });
        return;
      }

      const usdToCdf = Number(rateSnap.exists ? rateSnap.data.usdToCdf : 2850);
      const isCod = paymentMethodCode === 'cod';
      const paymentMode: 'cod' | 'test' = isCod ? 'cod' : 'test';

      const calcItems: CartCalculationItem[] = [];
      const productSnaps = new Map<string, FsDocSnapshot>();

      for (const line of rawItems) {
        const pid = String(line.productId || '').trim();
        const qty = Number(line.quantity || 0);
        if (!pid || !Number.isInteger(qty) || qty < 1) {
          res.status(400).json({ error: 'Ligne de panier invalide.' });
          return;
        }
        const pSnap = await fsGetDoc('products', pid, user.idToken);
        if (!pSnap.exists || pSnap.data.status !== 'active') {
          res.status(400).json({ error: `Produit indisponible (${pid}).` });
          return;
        }
        productSnaps.set(pid, pSnap);

        const shopSnap = await fsGetDoc('shops', pSnap.data.shopId, user.idToken);

        calcItems.push({
          productId: pid,
          title: String(pSnap.data.title),
          imageUrl: String((pSnap.data.imageUrls && pSnap.data.imageUrls[0]) || '/icon.svg'),
          sellerId: String(pSnap.data.sellerId),
          shopId: String(pSnap.data.shopId),
          shopName: String(pSnap.data.shopName),
          shopAddress: String(shopSnap.exists ? shopSnap.data.address : 'Lubumbashi'),
          shopPhone: String(shopSnap.exists ? shopSnap.data.phone : ''),
          unitPriceNative: Number(pSnap.data.price),
          nativeCurrency: pSnap.data.currency === 'CDF' ? 'CDF' : 'USD',
          quantity: qty,
          availableStock: Number(pSnap.data.stock),
        });
      }

      const plan = buildMultiSellerOrderPlan(
        calcItems,
        currency,
        Number(activeZoneData.feeUSD),
        usdToCdf
      );

      const orderId = makeSafeId('ord');
      const orderNumber = `LBM-${Date.now().toString().slice(-6)}`;
      const deliveryAddressFormatted = `${addressSnap.data.recipientName} (${addressSnap.data.phone}) — ${addressSnap.data.streetDetails}, ${addressSnap.data.commune}, ${addressSnap.data.city}`.slice(0, 295);

      const initialOrderStatus = isCod ? 'pending' : 'payment_pending';
      const writes: CommitWriteInput[] = [];

      writes.push({
        collection: 'idempotencyKeys',
        docId: idempotencyKey,
        fields: {
          key: idempotencyKey,
          uid: user.uid,
          operation: 'order_create',
          resultId: orderId,
        },
        serverTimeFields: ['createdAt'],
        precondition: { exists: false },
      });

      writes.push({
        collection: 'orders',
        docId: orderId,
        fields: {
          id: orderId,
          orderNumber,
          customerId: user.uid,
          customerName: String(addressSnap.data.recipientName).slice(0, 120),
          customerPhone: String(addressSnap.data.phone).slice(0, 32),
          deliveryAddress: deliveryAddressFormatted,
          city: String(addressSnap.data.city).slice(0, 80),
          zoneId: String(addressSnap.data.zoneId),
          zoneName: String(activeZoneData.name).slice(0, 100),
          currency,
          exchangeRateAtOrder: usdToCdf,
          merchandiseTotal: plan.merchandiseTotal,
          deliveryFee: plan.deliveryFee,
          grandTotal: plan.grandTotal,
          paymentMethod: paymentMethodCode,
          paymentMode,
          paymentStatus: 'pending',
          status: initialOrderStatus,
          subOrderCount: plan.sellerGroups.length,
          sellerIds: plan.sellerGroups.map((g) => g.sellerId),
          courierIds: [],
          customerConfirmedReceipt: false,
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      writes.push({
        collection: 'payments',
        docId: orderId,
        fields: {
          id: orderId,
          orderId,
          customerId: user.uid,
          provider: paymentMethodCode,
          mode: paymentMode,
          amount: plan.grandTotal,
          currency,
          exchangeRateAtOrder: usdToCdf,
          status: 'pending',
          collectedByUid: '',
          collectedByName: '',
          reconciledByAdmin: false,
          idempotencyKey,
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      for (const ci of calcItems) {
        const snap = productSnaps.get(ci.productId)!;
        writes.push({
          collection: 'products',
          docId: ci.productId,
          fields: {
            ...snap.data,
            stock: Number(snap.data.stock) - ci.quantity,
            soldCount: Number(snap.data.soldCount || 0) + ci.quantity,
            lastOrderRef: orderId,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: snap.updateTime },
        });
      }

      const generatedCodes: Array<{ subOrderId: string; shopName: string; code: string }> = [];

      for (const group of plan.sellerGroups) {
        const subOrderId = makeSafeId('sub');
        const deliveryId = subOrderId;
        const secret = generateDeliverySecret();
        generatedCodes.push({ subOrderId, shopName: group.shopName, code: secret.code });

        writes.push({
          collection: 'sellerSubOrders',
          docId: subOrderId,
          fields: {
            id: subOrderId,
            orderId,
            orderNumber,
            customerId: user.uid,
            customerName: String(addressSnap.data.recipientName).slice(0, 120),
            sellerId: group.sellerId,
            shopId: group.shopId,
            shopName: group.shopName,
            shopAddress: group.shopAddress,
            shopPhone: group.shopPhone,
            deliveryAddress: deliveryAddressFormatted,
            zoneName: String(activeZoneData.name).slice(0, 100),
            city: String(addressSnap.data.city).slice(0, 80),
            itemsSummary: group.itemsSummary,
            subtotal: group.subtotalOrderCurrency,
            deliveryFeeShare: group.deliveryFeeShareOrderCurrency,
            currency,
            exchangeRateAtOrder: usdToCdf,
            paymentMethod: paymentMethodCode,
            paymentStatus: 'pending',
            status: initialOrderStatus,
            courierId: '',
            courierName: '',
            fundsReleased: false,
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });

        for (const item of group.items) {
          const itemId = makeSafeId('itm');
          writes.push({
            collection: 'orderItems',
            docId: itemId,
            fields: {
              id: itemId,
              orderId,
              subOrderId,
              customerId: user.uid,
              sellerId: group.sellerId,
              shopId: group.shopId,
              productId: item.productId,
              productTitle: item.title,
              productImageUrl: item.imageUrl,
              unitPrice: item.unitPriceOrderCurrency,
              quantity: item.quantity,
              lineTotal: item.lineTotalOrderCurrency,
              currency,
            },
            serverTimeFields: ['createdAt'],
            precondition: { exists: false },
          });
        }

        const codToCollect = isCod
          ? roundMoney(group.subtotalOrderCurrency + group.deliveryFeeShareOrderCurrency, currency)
          : 0;

        writes.push({
          collection: 'deliveries',
          docId: deliveryId,
          fields: {
            id: deliveryId,
            orderId,
            subOrderId,
            orderNumber,
            customerId: user.uid,
            customerName: String(addressSnap.data.recipientName).slice(0, 120),
            customerPhone: String(addressSnap.data.phone).slice(0, 32),
            deliveryAddress: deliveryAddressFormatted,
            sellerId: group.sellerId,
            shopName: group.shopName,
            shopAddress: group.shopAddress,
            shopPhone: group.shopPhone,
            itemsSummary: group.itemsSummary,
            courierId: '',
            courierName: '',
            zoneId: String(addressSnap.data.zoneId),
            zoneName: String(activeZoneData.name).slice(0, 100),
            city: String(addressSnap.data.city).slice(0, 80),
            deliveryFee: group.deliveryFeeShareOrderCurrency,
            currency,
            isCod,
            codAmountToCollect: codToCollect,
            codCollected: false,
            codCollectedByUid: '',
            codCollectedByName: '',
            codReconciledByAdmin: false,
            codeHash: secret.hash,
            codeSalt: secret.salt,
            codeUsed: false,
            status: 'awaiting_assignment',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });

        writes.push({
          collection: 'customerDeliveryCodes',
          docId: deliveryId,
          fields: {
            deliveryId,
            orderId,
            subOrderId,
            customerId: user.uid,
            code: secret.code,
            used: false,
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });

        const notifSellerId = makeSafeId('ntf');
        writes.push({
          collection: 'notifications',
          docId: notifSellerId,
          fields: {
            id: notifSellerId,
            recipientId: group.sellerId,
            senderId: user.uid,
            eventType: 'new_order',
            title: `Nouvelle commande ${orderNumber}`,
            body: `Commande reçue (${group.itemsSummary}) — Montant marchandises : ${group.subtotalOrderCurrency} ${currency} (0 % commission à la commande).`,
            isRead: false,
            channels: ['in_app'],
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      }

      const notifCustomerId = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: notifCustomerId,
        fields: {
          id: notifCustomerId,
          recipientId: user.uid,
          senderId: user.uid,
          eventType: 'new_order',
          title: `Commande ${orderNumber} enregistrée`,
          body: `Total : ${plan.grandTotal} ${currency}. Retrouvez votre code secret de livraison à 6 chiffres dans le suivi de commande.`,
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);

      res.json({
        ok: true,
        orderId,
        orderNumber,
        paymentMode,
        grandTotal: plan.grandTotal,
        currency,
        subOrderCount: plan.sellerGroups.length,
        deliveryCodes: generatedCodes,
      });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 5. SIMULATION / VÉRIFICATION SERVEUR DE PAIEMENT EN MODE TEST (ANTI-DOUBLON)
  // =========================================================================
  app.post('/api/payments/confirm-test', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const orderId = String(req.body.orderId || '').trim();
      const simulateOutcome: 'confirmed' | 'failed' =
        req.body.outcome === 'failed' ? 'failed' : 'confirmed';

      const [orderSnap, paySnap, matchingSubs] = await Promise.all([
        fsGetDoc('orders', orderId, user.idToken),
        fsGetDoc('payments', orderId, user.idToken),
        fsQueryDocs(
          'sellerSubOrders',
          [
            { field: 'customerId', value: user.uid },
            { field: 'orderId', value: orderId },
          ],
          user.idToken,
          30
        ),
      ]);

      if (!orderSnap.exists || !paySnap.exists) {
        res.status(404).json({ error: 'Commande ou paiement introuvable.' });
        return;
      }
      if (orderSnap.data.customerId !== user.uid) {
        res.status(403).json({ error: 'Accès non autorisé à ce paiement.' });
        return;
      }
      if (paySnap.data.mode !== 'test') {
        res.status(400).json({
          error: "Le paiement à la livraison est vérifié lors de l'encaissement physique et non en mode test.",
        });
        return;
      }
      if (paySnap.data.status === 'confirmed') {
        res.json({ ok: true, alreadyConfirmed: true });
        return;
      }

      const writes: CommitWriteInput[] = [];

      writes.push({
        collection: 'payments',
        docId: orderId,
        fields: {
          ...paySnap.data,
          status: simulateOutcome,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: paySnap.updateTime },
      });

      const nextOrderStatus = simulateOutcome === 'confirmed' ? 'paid' : 'payment_pending';
      writes.push({
        collection: 'orders',
        docId: orderId,
        fields: {
          ...orderSnap.data,
          paymentStatus: simulateOutcome,
          status: nextOrderStatus,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: orderSnap.updateTime },
      });

      for (const subSnap of matchingSubs) {
        writes.push({
          collection: 'sellerSubOrders',
          docId: String(subSnap.data.id),
          fields: {
            ...subSnap.data,
            paymentStatus: simulateOutcome,
            status: nextOrderStatus,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: subSnap.updateTime },
        });
      }

      const notifId = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: notifId,
        fields: {
          id: notifId,
          recipientId: user.uid,
          senderId: user.uid,
          eventType: simulateOutcome === 'confirmed' ? 'payment_confirmed' : 'payment_failed',
          title:
            simulateOutcome === 'confirmed'
              ? `Paiement de test confirmé (${orderSnap.data.orderNumber})`
              : `Échec du paiement de test (${orderSnap.data.orderNumber})`,
          body:
            simulateOutcome === 'confirmed'
              ? `Mode test (${paySnap.data.provider}) : ${paySnap.data.amount} ${paySnap.data.currency} validé côté serveur.`
              : `La transaction de test a été marquée en échec. Vous pouvez réessayer.`,
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, paymentStatus: simulateOutcome, orderStatus: nextOrderStatus });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 6. ATTRIBUTION D'UN LIVREUR PAR L'ADMINISTRATEUR
  // =========================================================================
  app.post('/api/admin/deliveries/assign', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!(await verifyIsAdmin(user))) {
        res.status(403).json({ error: 'Droits administrateur requis.' });
        return;
      }

      const deliveryId = String(req.body.deliveryId || '').trim();
      const courierId = String(req.body.courierId || '').trim();

      const [delSnap, courierSnap] = await Promise.all([
        fsGetDoc('deliveries', deliveryId, user.idToken),
        fsGetDoc('couriers', courierId, user.idToken),
      ]);

      if (!delSnap.exists) {
        res.status(404).json({ error: 'Livraison introuvable.' });
        return;
      }
      if (!courierSnap.exists || courierSnap.data.approvalStatus !== 'approved') {
        res.status(400).json({ error: 'Le livreur sélectionné doit être validé par l’administration.' });
        return;
      }

      const [subSnap, orderSnap] = await Promise.all([
        fsGetDoc('sellerSubOrders', delSnap.data.subOrderId, user.idToken),
        fsGetDoc('orders', delSnap.data.orderId, user.idToken),
      ]);

      const courierName = String(courierSnap.data.fullName);
      const writes: CommitWriteInput[] = [
        {
          collection: 'deliveries',
          docId: deliveryId,
          fields: {
            ...delSnap.data,
            courierId,
            courierName,
            status: 'assigned',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: delSnap.updateTime },
        },
      ];

      if (subSnap.exists) {
        writes.push({
          collection: 'sellerSubOrders',
          docId: subSnap.data.id,
          fields: {
            ...subSnap.data,
            courierId,
            courierName,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: subSnap.updateTime },
        });
      }

      if (orderSnap.exists) {
        const existingCouriers: string[] = Array.isArray(orderSnap.data.courierIds)
          ? orderSnap.data.courierIds
          : [];
        const updatedCouriers = existingCouriers.includes(courierId)
          ? existingCouriers
          : [...existingCouriers, courierId].slice(0, 15);
        writes.push({
          collection: 'orders',
          docId: orderSnap.data.id,
          fields: {
            ...orderSnap.data,
            courierIds: updatedCouriers,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: orderSnap.updateTime },
        });
      }

      const notifCourier = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: notifCourier,
        fields: {
          id: notifCourier,
          recipientId: courierId,
          senderId: user.uid,
          eventType: 'courier_assigned',
          title: `Nouvelle course attribuée (${delSnap.data.orderNumber})`,
          body: `Récupération chez ${delSnap.data.shopName} (${delSnap.data.shopAddress}) vers ${delSnap.data.zoneName}.`,
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      const notifCustomer = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: notifCustomer,
        fields: {
          id: notifCustomer,
          recipientId: delSnap.data.customerId,
          senderId: user.uid,
          eventType: 'courier_assigned',
          title: `Livreur attribué (${delSnap.data.orderNumber})`,
          body: `${courierName} prend en charge votre colis (${delSnap.data.shopName}).`,
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      const logId = makeSafeId('log');
      writes.push({
        collection: 'adminLogs',
        docId: logId,
        fields: {
          id: logId,
          actorUid: user.uid,
          actorEmail: user.email,
          action: 'ASSIGN_COURIER',
          targetType: 'delivery',
          targetId: deliveryId,
          details: `Livreur ${courierName} attribué à la commande ${delSnap.data.orderNumber}`,
        },
        serverTimeFields: ['createdAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, courierName });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 7. VÉRIFICATION SERVEUR DU CODE UNIQUE DE LIVRAISON (USAGE UNIQUE STRICT)
  // =========================================================================
  app.post('/api/deliveries/confirm-handover', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const deliveryId = String(req.body.deliveryId || '').trim();
      const submittedCode = String(req.body.code || '').trim();

      if (!deliveryId || !/^\d{6}$/.test(submittedCode)) {
        res.status(400).json({ error: 'Veuillez saisir le code unique client à 6 chiffres.' });
        return;
      }

      const delSnap = await fsGetDoc('deliveries', deliveryId, user.idToken);
      if (!delSnap.exists) {
        res.status(404).json({ error: 'Livraison introuvable.' });
        return;
      }
      if (delSnap.data.courierId !== user.uid) {
        res.status(403).json({ error: 'Cette livraison ne vous est pas attribuée.' });
        return;
      }
      if (delSnap.data.codeUsed === true || delSnap.data.status === 'delivered') {
        res.status(400).json({ error: 'Ce code de livraison a déjà été utilisé.' });
        return;
      }
      if (delSnap.data.status !== 'picked_up') {
        res.status(400).json({ error: 'Vous devez d’abord confirmer la récupération du colis chez le vendeur.' });
        return;
      }

      const computedHash = hashDeliveryCode(submittedCode, String(delSnap.data.codeSalt));
      const expectedHash = String(delSnap.data.codeHash);
      const isMatch =
        computedHash.length === expectedHash.length &&
        crypto.timingSafeEqual(Buffer.from(computedHash), Buffer.from(expectedHash));

      if (!isMatch) {
        res.status(400).json({ error: 'Code de livraison incorrect. Vérifiez les 6 chiffres communiqués par le client.' });
        return;
      }

      const subSnap = await fsGetDoc('sellerSubOrders', delSnap.data.subOrderId, user.idToken);
      const isCod = Boolean(delSnap.data.isCod);

      const writes: CommitWriteInput[] = [
        {
          collection: 'deliveries',
          docId: deliveryId,
          fields: {
            ...delSnap.data,
            status: 'delivered',
            codeUsed: true,
            codCollected: isCod ? true : false,
            codCollectedByUid: isCod ? user.uid : '',
            codCollectedByName: isCod ? user.name.slice(0, 120) : '',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: delSnap.updateTime },
        },
      ];

      if (subSnap.exists) {
        writes.push({
          collection: 'sellerSubOrders',
          docId: subSnap.data.id,
          fields: {
            ...subSnap.data,
            status: 'delivered',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: subSnap.updateTime },
        });
      }

      const nCustomer = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: nCustomer,
        fields: {
          id: nCustomer,
          recipientId: delSnap.data.customerId,
          senderId: user.uid,
          eventType: 'order_delivered',
          title: `Colis livré (${delSnap.data.orderNumber})`,
          body: 'Remise validée par code unique. Vous pouvez confirmer la réception et noter vos produits.',
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      const nSeller = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: nSeller,
        fields: {
          id: nSeller,
          recipientId: delSnap.data.sellerId,
          senderId: user.uid,
          eventType: 'order_delivered',
          title: `Commande livrée (${delSnap.data.orderNumber})`,
          body: isCod
            ? 'Colis remis avec encaissement espèces par le livreur (en attente de rapprochement administratif).'
            : 'Colis remis au client. Ouvrez votre Portefeuille Vendeur pour actualiser votre solde disponible.',
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, status: 'delivered' });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 8. CONFIRMATION DE RÉCEPTION PAR LE CLIENT
  // =========================================================================
  app.post('/api/orders/confirm-receipt', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const orderId = String(req.body.orderId || '').trim();
      const [orderSnap, subSnaps] = await Promise.all([
        fsGetDoc('orders', orderId, user.idToken),
        fsQueryDocs(
          'sellerSubOrders',
          [
            { field: 'customerId', value: user.uid },
            { field: 'orderId', value: orderId },
          ],
          user.idToken,
          30
        ),
      ]);

      if (!orderSnap.exists || orderSnap.data.customerId !== user.uid) {
        res.status(404).json({ error: 'Commande introuvable.' });
        return;
      }

      const allDelivered =
        subSnaps.length > 0 && subSnaps.every((s) => s.data.status === 'delivered');
      if (!allDelivered && orderSnap.data.status !== 'delivered') {
        res.status(400).json({
          error: 'Toutes les livraisons de cette commande doivent être remises par le livreur avant la confirmation finale.',
        });
        return;
      }

      const writes: CommitWriteInput[] = [];
      if (orderSnap.data.status !== 'delivered') {
        // Étape 1 : synchroniser d'abord le statut delivered si la dernière sous-commande vient d'arriver
        await fsAtomicCommit(
          [
            {
              collection: 'orders',
              docId: orderId,
              fields: {
                ...orderSnap.data,
                status: 'delivered',
              },
              serverTimeFields: ['updatedAt'],
            },
          ],
          user.idToken
        );
      }

      const refreshed = await fsGetDoc('orders', orderId, user.idToken);
      writes.push({
        collection: 'orders',
        docId: orderId,
        fields: {
          ...refreshed.data,
          status: 'delivered',
          customerConfirmedReceipt: true,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: refreshed.updateTime },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 9. SYNCHRONISATION SERVEUR DU PORTEFEUILLE COMPTABLE VENDEUR
  // =========================================================================
  app.post('/api/wallets/sync', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const targetSellerId = String(req.body.sellerId || user.uid).trim();
      if (targetSellerId !== user.uid && !(await verifyIsAdmin(user))) {
        res.status(403).json({ error: 'Accès interdit au portefeuille d’un autre vendeur.' });
        return;
      }
      const walletSnap = await syncSellerWalletLedger(targetSellerId, user.idToken);
      res.json({ ok: true, wallet: walletSnap.data });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 10. DEMANDE DE RETRAIT VENDEUR — COMMISSION DE 5 % UNIQUEMENT AU RETRAIT
  // =========================================================================
  app.post('/api/withdrawals/request', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const idempotencyKey = sanitizeIdempotencyKey(req.body.idempotencyKey);
      const idemSnap = await fsGetDoc('idempotencyKeys', idempotencyKey, user.idToken);
      if (idemSnap.exists) {
        res.json({ ok: true, duplicate: true, withdrawalId: idemSnap.data.resultId });
        return;
      }

      const currency: CurrencyCode = req.body.currency === 'CDF' ? 'CDF' : 'USD';
      const requestedAmount = Number(req.body.amount || 0);
      const payoutChannel = String(req.body.payoutChannel || '').trim().slice(0, 80);
      const payoutRecipient = String(req.body.payoutRecipient || '').trim().slice(0, 160);

      if (!payoutChannel || payoutRecipient.length < 3) {
        res.status(400).json({ error: 'Veuillez préciser le canal de retrait et les coordonnées du bénéficiaire.' });
        return;
      }

      const sellerSnap = await fsGetDoc('sellers', user.uid, user.idToken);
      if (!sellerSnap.exists || sellerSnap.data.approvalStatus !== 'approved') {
        res.status(403).json({ error: 'Votre boutique doit être validée par Lubumarket pour demander un retrait.' });
        return;
      }

      // Toujours synchroniser les livraisons confirmées + payées avant vérification du solde disponible
      const walletSnap = await syncSellerWalletLedger(user.uid, user.idToken);

      const currentAvailable =
        currency === 'USD'
          ? Number(walletSnap.data.availableBalanceUSD || 0)
          : Number(walletSnap.data.availableBalanceCDF || 0);

      // Règle fondamentale : 5 % prélevé exclusivement sur le montant demandé en retrait
      const breakdown = calculateWithdrawalBreakdown(requestedAmount, currentAvailable, currency);

      const withdrawalId = makeSafeId('wdr');
      const txId = makeSafeId('wtx');
      const notifId = makeSafeId('ntf');
      const nextWallet: Record<string, any> = {
        ...walletSnap.data,
        lastActionRef: withdrawalId,
      };

      if (currency === 'USD') {
        nextWallet.availableBalanceUSD = breakdown.remainingAvailableBalance;
        nextWallet.lockedWithdrawalUSD = roundMoney(
          Number(walletSnap.data.lockedWithdrawalUSD || 0) + breakdown.grossAmount,
          'USD'
        );
      } else {
        nextWallet.availableBalanceCDF = breakdown.remainingAvailableBalance;
        nextWallet.lockedWithdrawalCDF = roundMoney(
          Number(walletSnap.data.lockedWithdrawalCDF || 0) + breakdown.grossAmount,
          'CDF'
        );
      }

      const writes: CommitWriteInput[] = [
        {
          collection: 'idempotencyKeys',
          docId: idempotencyKey,
          fields: {
            key: idempotencyKey,
            uid: user.uid,
            operation: 'withdrawal_request',
            resultId: withdrawalId,
          },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        },
        {
          collection: 'wallets',
          docId: user.uid,
          fields: nextWallet,
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: walletSnap.updateTime },
        },
        {
          collection: 'withdrawals',
          docId: withdrawalId,
          fields: {
            id: withdrawalId,
            sellerId: user.uid,
            shopName: String(sellerSnap.data.shopName),
            grossAmount: breakdown.grossAmount,
            commissionRatePercent: LUBUMARKET_COMMISSION_PERCENT,
            commissionAmount: breakdown.commissionAmount,
            netAmount: breakdown.netAmount,
            currency,
            payoutChannel,
            payoutRecipient,
            status: 'pending',
            adminNote: 'En attente de validation administrative (registre comptable interne).',
            idempotencyKey,
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        },
        {
          collection: 'commissions',
          docId: withdrawalId,
          fields: {
            id: withdrawalId,
            withdrawalId,
            sellerId: user.uid,
            shopName: String(sellerSnap.data.shopName),
            grossWithdrawalAmount: breakdown.grossAmount,
            ratePercent: LUBUMARKET_COMMISSION_PERCENT,
            commissionAmount: breakdown.commissionAmount,
            netSellerAmount: breakdown.netAmount,
            currency,
            status: 'pending_approval',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        },
        {
          collection: 'walletTransactions',
          docId: txId,
          fields: {
            id: txId,
            sellerId: user.uid,
            type: 'withdrawal_hold',
            amount: breakdown.grossAmount,
            commissionAmount: breakdown.commissionAmount,
            netAmount: breakdown.netAmount,
            currency,
            referenceId: withdrawalId,
            description: `Demande de retrait ${breakdown.grossAmount} ${currency} · Commission 5 % : ${breakdown.commissionAmount} ${currency} · Net : ${breakdown.netAmount} ${currency}`,
          },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        },
        {
          collection: 'notifications',
          docId: notifId,
          fields: {
            id: notifId,
            recipientId: user.uid,
            senderId: user.uid,
            eventType: 'withdrawal_requested',
            title: 'Demande de retrait enregistrée',
            body: `Montant : ${breakdown.grossAmount} ${currency} · Commission 5 % : ${breakdown.commissionAmount} ${currency} · Net vendeur : ${breakdown.netAmount} ${currency}.`,
            isRead: false,
            channels: ['in_app'],
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        },
      ];

      await fsAtomicCommit(writes, user.idToken);

      res.json({
        ok: true,
        withdrawalId,
        breakdown,
      });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 11. VALIDATION ADMINISTRATIVE D'UN RETRAIT (APPROBATION OU REFUS)
  // =========================================================================
  app.post('/api/admin/withdrawals/review', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!(await verifyIsAdmin(user))) {
        res.status(403).json({ error: 'Droits administrateur propriétaire requis.' });
        return;
      }

      const withdrawalId = String(req.body.withdrawalId || '').trim();
      const decision: 'approved' | 'rejected' =
        req.body.decision === 'rejected' ? 'rejected' : 'approved';
      const adminNote = String(
        req.body.adminNote ||
          (decision === 'approved'
            ? 'Retrait approuvé dans le registre comptable Lubumarket.'
            : 'Retrait refusé — montant recrédité au solde disponible.')
      )
        .trim()
        .slice(0, 290);

      const [wdrSnap, commSnap] = await Promise.all([
        fsGetDoc('withdrawals', withdrawalId, user.idToken),
        fsGetDoc('commissions', withdrawalId, user.idToken),
      ]);

      if (!wdrSnap.exists || wdrSnap.data.status !== 'pending') {
        res.status(400).json({ error: 'Demande de retrait introuvable ou déjà traitée.' });
        return;
      }

      const walletSnap = await fsGetDoc('wallets', wdrSnap.data.sellerId, user.idToken);
      if (!walletSnap.exists) {
        res.status(404).json({ error: 'Portefeuille vendeur introuvable.' });
        return;
      }

      const cur: CurrencyCode = wdrSnap.data.currency === 'CDF' ? 'CDF' : 'USD';
      const gross = Number(wdrSnap.data.grossAmount);
      const comm = Number(wdrSnap.data.commissionAmount);
      const net = Number(wdrSnap.data.netAmount);
      const nextWallet: Record<string, any> = { ...walletSnap.data, lastActionRef: withdrawalId };

      if (decision === 'approved') {
        if (cur === 'USD') {
          nextWallet.lockedWithdrawalUSD = Math.max(0, roundMoney(Number(walletSnap.data.lockedWithdrawalUSD || 0) - gross, 'USD'));
          nextWallet.totalWithdrawalsUSD = roundMoney(Number(walletSnap.data.totalWithdrawalsUSD || 0) + gross, 'USD');
          nextWallet.totalCommissionsUSD = roundMoney(Number(walletSnap.data.totalCommissionsUSD || 0) + comm, 'USD');
        } else {
          nextWallet.lockedWithdrawalCDF = Math.max(0, roundMoney(Number(walletSnap.data.lockedWithdrawalCDF || 0) - gross, 'CDF'));
          nextWallet.totalWithdrawalsCDF = roundMoney(Number(walletSnap.data.totalWithdrawalsCDF || 0) + gross, 'CDF');
          nextWallet.totalCommissionsCDF = roundMoney(Number(walletSnap.data.totalCommissionsCDF || 0) + comm, 'CDF');
        }
      } else {
        if (cur === 'USD') {
          nextWallet.lockedWithdrawalUSD = Math.max(0, roundMoney(Number(walletSnap.data.lockedWithdrawalUSD || 0) - gross, 'USD'));
          nextWallet.availableBalanceUSD = roundMoney(Number(walletSnap.data.availableBalanceUSD || 0) + gross, 'USD');
        } else {
          nextWallet.lockedWithdrawalCDF = Math.max(0, roundMoney(Number(walletSnap.data.lockedWithdrawalCDF || 0) - gross, 'CDF'));
          nextWallet.availableBalanceCDF = roundMoney(Number(walletSnap.data.availableBalanceCDF || 0) + gross, 'CDF');
        }
      }

      const txId = makeSafeId('wtx');
      const notifId = makeSafeId('ntf');
      const logId = makeSafeId('log');

      const writes: CommitWriteInput[] = [
        {
          collection: 'withdrawals',
          docId: withdrawalId,
          fields: {
            ...wdrSnap.data,
            status: decision,
            adminNote,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: wdrSnap.updateTime },
        },
        {
          collection: 'wallets',
          docId: wdrSnap.data.sellerId,
          fields: nextWallet,
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: walletSnap.updateTime },
        },
        {
          collection: 'walletTransactions',
          docId: txId,
          fields: {
            id: txId,
            sellerId: wdrSnap.data.sellerId,
            type: decision === 'approved' ? 'withdrawal_approved' : 'withdrawal_rejected',
            amount: gross,
            commissionAmount: decision === 'approved' ? comm : 0,
            netAmount: decision === 'approved' ? net : gross,
            currency: cur,
            referenceId: withdrawalId,
            description:
              decision === 'approved'
                ? `Retrait approuvé : Net vendeur ${net} ${cur} · Commission Lubumarket (5 %) ${comm} ${cur}`
                : `Retrait refusé : ${gross} ${cur} restitué au solde disponible (0 commission)`,
          },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        },
        {
          collection: 'notifications',
          docId: notifId,
          fields: {
            id: notifId,
            recipientId: wdrSnap.data.sellerId,
            senderId: user.uid,
            eventType: decision === 'approved' ? 'withdrawal_approved' : 'withdrawal_rejected',
            title: decision === 'approved' ? 'Retrait approuvé' : 'Retrait refusé',
            body:
              decision === 'approved'
                ? `Votre retrait de ${gross} ${cur} (net : ${net} ${cur}, commission 5 % : ${comm} ${cur}) a été approuvé.`
                : `Votre demande de retrait de ${gross} ${cur} a été refusée (${adminNote}). Votre solde disponible a été recrédité.`,
            isRead: false,
            channels: ['in_app'],
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        },
        {
          collection: 'adminLogs',
          docId: logId,
          fields: {
            id: logId,
            actorUid: user.uid,
            actorEmail: user.email,
            action: decision === 'approved' ? 'APPROVE_WITHDRAWAL' : 'REJECT_WITHDRAWAL',
            targetType: 'withdrawal',
            targetId: withdrawalId,
            details: `${wdrSnap.data.shopName} — Brut: ${gross} ${cur}, Commission 5%: ${comm} ${cur}, Net: ${net} ${cur}`,
          },
          serverTimeFields: ['createdAt'],
          precondition: { exists: false },
        },
      ];

      if (commSnap.exists) {
        writes.push({
          collection: 'commissions',
          docId: withdrawalId,
          fields: {
            ...commSnap.data,
            status: decision === 'approved' ? 'collected' : 'cancelled',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: commSnap.updateTime },
        });
      }

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, decision });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 12. RAPPROCHEMENT ADMINISTRATIF DU PAIEMENT À LA LIVRAISON (COD)
  // =========================================================================
  app.post('/api/admin/cod/reconcile', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!(await verifyIsAdmin(user))) {
        res.status(403).json({ error: 'Droits administrateur requis.' });
        return;
      }

      const deliveryId = String(req.body.deliveryId || '').trim();
      const delSnap = await fsGetDoc('deliveries', deliveryId, user.idToken);
      if (!delSnap.exists || !delSnap.data.isCod) {
        res.status(404).json({ error: 'Livraison Paiement à la livraison introuvable.' });
        return;
      }
      if (delSnap.data.status !== 'delivered') {
        res.status(400).json({ error: 'La livraison doit être confirmée remise au client avant le rapprochement comptable.' });
        return;
      }
      if (delSnap.data.codReconciledByAdmin === true) {
        res.json({ ok: true, alreadyReconciled: true });
        return;
      }

      const [subSnap, paySnap, orderSnap] = await Promise.all([
        fsGetDoc('sellerSubOrders', delSnap.data.subOrderId, user.idToken),
        fsGetDoc('payments', delSnap.data.orderId, user.idToken),
        fsGetDoc('orders', delSnap.data.orderId, user.idToken),
      ]);

      if (!subSnap.exists) {
        res.status(404).json({ error: 'Sous-commande introuvable.' });
        return;
      }

      const writes: CommitWriteInput[] = [
        {
          collection: 'deliveries',
          docId: deliveryId,
          fields: {
            ...delSnap.data,
            codCollected: true,
            codReconciledByAdmin: true,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: delSnap.updateTime },
        },
        {
          collection: 'sellerSubOrders',
          docId: subSnap.data.id,
          fields: {
            ...subSnap.data,
            paymentStatus: 'confirmed',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: subSnap.updateTime },
        },
      ];

      if (paySnap.exists) {
        writes.push({
          collection: 'payments',
          docId: paySnap.data.id,
          fields: {
            ...paySnap.data,
            status: 'confirmed',
            collectedByUid: String(delSnap.data.codCollectedByUid || user.uid),
            collectedByName: String(delSnap.data.codCollectedByName || user.name).slice(0, 120),
            reconciledByAdmin: true,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: paySnap.updateTime },
        });
      }

      if (orderSnap.exists) {
        writes.push({
          collection: 'orders',
          docId: orderSnap.data.id,
          fields: {
            ...orderSnap.data,
            paymentStatus: 'confirmed',
            status: 'delivered',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: orderSnap.updateTime },
        });
      }

      const logId = makeSafeId('log');
      writes.push({
        collection: 'adminLogs',
        docId: logId,
        fields: {
          id: logId,
          actorUid: user.uid,
          actorEmail: user.email,
          action: 'RECONCILE_COD_PAYMENT',
          targetType: 'delivery',
          targetId: deliveryId,
          details: `Encaissement espèces rapproché (${delSnap.data.codAmountToCollect} ${subSnap.data.currency})`,
        },
        serverTimeFields: ['createdAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);

      // Synchroniser immédiatement le portefeuille du vendeur pour rendre la vente retirable
      await syncSellerWalletLedger(String(delSnap.data.sellerId), user.idToken);

      res.json({ ok: true });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 13. ANNULATION & REMBOURSEMENT CONTRÔLÉS CÔTÉ SERVEUR (0 % COMMISSION)
  // =========================================================================
  app.post('/api/orders/cancel-or-refund', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const orderId = String(req.body.orderId || '').trim();
      const reason = String(req.body.reason || 'Annulation demandée').trim().slice(0, 280);
      const isAdminUser = await verifyIsAdmin(user);

      const subFilters = isAdminUser
        ? [{ field: 'orderId', value: orderId }]
        : [
            { field: 'customerId', value: user.uid },
            { field: 'orderId', value: orderId },
          ];

      const [orderSnap, paySnap, matchingSubs, matchingDels] = await Promise.all([
        fsGetDoc('orders', orderId, user.idToken),
        fsGetDoc('payments', orderId, user.idToken),
        fsQueryDocs('sellerSubOrders', subFilters, user.idToken, 30),
        fsQueryDocs('deliveries', subFilters, user.idToken, 30),
      ]);

      if (!orderSnap.exists) {
        res.status(404).json({ error: 'Commande introuvable.' });
        return;
      }
      if (!isAdminUser && orderSnap.data.customerId !== user.uid) {
        res.status(403).json({ error: 'Action non autorisée sur cette commande.' });
        return;
      }
      if (!isAdminUser && orderSnap.data.status !== 'pending' && orderSnap.data.status !== 'payment_pending') {
        res.status(400).json({
          error: 'Une commande déjà acceptée ou en préparation ne peut être annulée que par l’administration.',
        });
        return;
      }
      if (orderSnap.data.status === 'cancelled' || orderSnap.data.status === 'refunded') {
        res.status(400).json({ error: 'Cette commande est déjà annulée ou remboursée.' });
        return;
      }

      const wasPaid = orderSnap.data.paymentStatus === 'confirmed';
      const targetStatus = wasPaid && isAdminUser ? 'refunded' : 'cancelled';
      const writes: CommitWriteInput[] = [];

      writes.push({
        collection: 'orders',
        docId: orderId,
        fields: {
          ...orderSnap.data,
          status: targetStatus,
          paymentStatus: targetStatus === 'refunded' ? 'refunded' : orderSnap.data.paymentStatus,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: orderSnap.updateTime },
      });

      if (paySnap.exists && targetStatus === 'refunded' && isAdminUser) {
        writes.push({
          collection: 'payments',
          docId: orderId,
          fields: {
            ...paySnap.data,
            status: 'refunded',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: paySnap.updateTime },
        });
      }

      for (const subSnap of matchingSubs) {
        writes.push({
          collection: 'sellerSubOrders',
          docId: String(subSnap.data.id),
          fields: {
            ...subSnap.data,
            status: targetStatus,
            paymentStatus: targetStatus === 'refunded' ? 'refunded' : subSnap.data.paymentStatus,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: subSnap.updateTime },
        });

        if (targetStatus === 'refunded' && isAdminUser) {
          const refundId = makeSafeId('rfd');
          writes.push({
            collection: 'refunds',
            docId: refundId,
            fields: {
              id: refundId,
              orderId,
              subOrderId: String(subSnap.data.id),
              customerId: orderSnap.data.customerId,
              sellerId: String(subSnap.data.sellerId),
              amount: Number(subSnap.data.subtotal || 0),
              currency: subSnap.data.currency,
              reason,
              status: 'approved',
            },
            serverTimeFields: ['createdAt', 'updatedAt'],
            precondition: { exists: false },
          });
        }
      }

      for (const dSnap of matchingDels) {
        if (dSnap.data.status !== 'delivered') {
          writes.push({
            collection: 'deliveries',
            docId: String(dSnap.data.id),
            fields: {
              ...dSnap.data,
              status: 'cancelled',
            },
            serverTimeFields: ['updatedAt'],
            precondition: { updateTime: dSnap.updateTime },
          });
        }
      }

      const notifId = makeSafeId('ntf');
      writes.push({
        collection: 'notifications',
        docId: notifId,
        fields: {
          id: notifId,
          recipientId: orderSnap.data.customerId,
          senderId: user.uid,
          eventType: 'order_cancelled',
          title:
            targetStatus === 'refunded'
              ? `Commande ${orderSnap.data.orderNumber} remboursée`
              : `Commande ${orderSnap.data.orderNumber} annulée`,
          body: `Motif : ${reason}`,
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);
      res.json({ ok: true, status: targetStatus });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 14. INSCRIPTION VENDEUR VÉRIFIÉE CÔTÉ SERVEUR AVEC ID UNIQUE & LECTURE RÉELLE FIRESTORE
  // =========================================================================
  app.post('/api/sellers/register', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      if (!user.emailVerified) {
        res.status(403).json({
          error: 'Une adresse e-mail vérifiée est requise pour enregistrer une boutique vendeur.',
        });
        return;
      }

      const validated = buildValidatedSellerRegistration({
        shopName: req.body.shopName,
        logoUrl: req.body.logoUrl,
        logoStorageMode: req.body.logoStorageMode,
        description: req.body.description,
        phone: req.body.phone,
        address: req.body.address,
        activityZone: req.body.activityZone,
        city: req.body.city,
      });

      const [existingSellerSnap, existingUserSnap, existingWalletSnap] = await Promise.all([
        fsGetDoc('sellers', user.uid, user.idToken),
        fsGetDoc('users', user.uid, user.idToken),
        fsGetDoc('wallets', user.uid, user.idToken),
      ]);

      if (existingSellerSnap.exists) {
        const currentApproval = String(existingSellerSnap.data.approvalStatus || '');
        if (currentApproval === 'pending') {
          res.status(400).json({
            error: 'Une demande d’inscription vendeur est déjà en attente de validation pour ce compte.',
          });
          return;
        }
        if (currentApproval === 'approved') {
          res.status(400).json({
            error: 'Votre boutique vendeur est déjà approuvée et active.',
          });
          return;
        }
        if (currentApproval === 'suspended') {
          res.status(403).json({
            error: 'Votre boutique est actuellement suspendue par l’administration.',
          });
          return;
        }
      }

      const isResubmissionAfterRejection =
        existingSellerSnap.exists && existingSellerSnap.data.approvalStatus === 'rejected';

      const applicationId = makeSafeId('dem');
      const shopId = isResubmissionAfterRejection
        ? String(existingSellerSnap.data.shopId)
        : makeSafeId('shp');

      const existingShopSnap = isResubmissionAfterRejection
        ? await fsGetDoc('shops', shopId, user.idToken)
        : { exists: false, data: {}, updateTime: undefined };

      const writes: CommitWriteInput[] = [];

      // 1. Demande d'inscription vendeur avec identifiant unique immuable (dem-...) et statut « En attente de validation »
      writes.push({
        collection: 'sellerApplications',
        docId: applicationId,
        fields: {
          id: applicationId,
          sellerId: user.uid,
          shopId,
          shopName: validated.shopName,
          logoUrl: validated.logoUrl,
          logoStorageMode: validated.logoStorageMode,
          description: validated.description,
          phone: validated.phone,
          email: user.email,
          address: validated.address,
          activityZone: validated.activityZone,
          city: validated.city,
          status: 'pending',
          statusLabel: 'En attente de validation',
          rejectionReason: '',
          reviewedByUid: '',
          reviewedByEmail: '',
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      // 2. Boutique associée en statut 'pending' (non visible dans le catalogue public tant que non approuvée)
      if (isResubmissionAfterRejection && existingShopSnap.exists) {
        writes.push({
          collection: 'shops',
          docId: shopId,
          fields: {
            ...existingShopSnap.data,
            name: validated.shopName,
            logoUrl: validated.logoUrl,
            description: validated.description,
            phone: validated.phone,
            address: validated.address,
            activityZone: validated.activityZone,
            city: validated.city,
            status: 'pending',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: existingShopSnap.updateTime },
        });
      } else {
        writes.push({
          collection: 'shops',
          docId: shopId,
          fields: {
            id: shopId,
            sellerId: user.uid,
            name: validated.shopName,
            logoUrl: validated.logoUrl,
            description: validated.description,
            phone: validated.phone,
            address: validated.address,
            activityZone: validated.activityZone,
            city: validated.city,
            status: 'pending',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      }

      // 3. Fiche vendeur liée à l'identifiant unique de la demande
      if (isResubmissionAfterRejection) {
        writes.push({
          collection: 'sellers',
          docId: user.uid,
          fields: {
            ...existingSellerSnap.data,
            applicationId,
            shopName: validated.shopName,
            phone: validated.phone,
            address: validated.address,
            activityZone: validated.activityZone,
            city: validated.city,
            approvalStatus: 'pending',
            statusLabel: 'En attente de validation',
            rejectionReason: '',
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: existingSellerSnap.updateTime },
        });
      } else {
        writes.push({
          collection: 'sellers',
          docId: user.uid,
          fields: {
            uid: user.uid,
            applicationId,
            shopId,
            shopName: validated.shopName,
            phone: validated.phone,
            email: user.email,
            address: validated.address,
            activityZone: validated.activityZone,
            city: validated.city,
            approvalStatus: 'pending',
            statusLabel: 'En attente de validation',
            rejectionReason: '',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      }

      // 4. Portefeuille comptable interne initialisé à 0 si non existant
      if (!existingWalletSnap.exists) {
        writes.push({
          collection: 'wallets',
          docId: user.uid,
          fields: {
            sellerId: user.uid,
            pendingBalanceUSD: 0,
            availableBalanceUSD: 0,
            lockedWithdrawalUSD: 0,
            totalSalesUSD: 0,
            totalWithdrawalsUSD: 0,
            totalCommissionsUSD: 0,
            pendingBalanceCDF: 0,
            availableBalanceCDF: 0,
            lockedWithdrawalCDF: 0,
            totalSalesCDF: 0,
            totalWithdrawalsCDF: 0,
            totalCommissionsCDF: 0,
            ledgerNotice: 'Registre comptable interne Lubumarket — Commission de 5 % uniquement au retrait.',
            lastActionRef: 'init',
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      }

      // 5. Synchronisation du compte utilisateur principal (jamais d'écrasement d'un rôle admin)
      if (!existingUserSnap.exists) {
        writes.push({
          collection: 'users',
          docId: user.uid,
          fields: {
            uid: user.uid,
            displayName: (user.name || validated.shopName).slice(0, 110),
            email: user.email,
            phone: validated.phone,
            role: 'seller',
            status: 'active',
            city: validated.city,
          },
          serverTimeFields: ['createdAt', 'updatedAt'],
          precondition: { exists: false },
        });
      } else if (existingUserSnap.data.role === 'customer' && existingUserSnap.data.status === 'active') {
        writes.push({
          collection: 'users',
          docId: user.uid,
          fields: {
            ...existingUserSnap.data,
            role: 'seller',
            phone: validated.phone,
            city: validated.city,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: existingUserSnap.updateTime },
        });
      }

      await fsAtomicCommit(writes, user.idToken);

      // Relecture immédiate depuis Cloud Firestore pour garantir zéro fausse confirmation
      const [verifyAppSnap, verifySellerSnap, verifyShopSnap] = await Promise.all([
        fsGetDoc('sellerApplications', applicationId, user.idToken),
        fsGetDoc('sellers', user.uid, user.idToken),
        fsGetDoc('shops', shopId, user.idToken),
      ]);

      if (!verifyAppSnap.exists || !verifySellerSnap.exists || !verifyShopSnap.exists) {
        res.status(500).json({
          error: 'Échec de vérification de persistance dans Cloud Firestore après écriture.',
        });
        return;
      }

      res.json({
        ok: true,
        verifiedInFirestore: true,
        applicationId,
        shopId,
        status: verifySellerSnap.data.approvalStatus,
        statusLabel: verifyAppSnap.data.statusLabel,
        logoStorageMode: verifyAppSnap.data.logoStorageMode,
      });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 15. VALIDATION, REFUS OU SUSPENSION D'UNE BOUTIQUE PAR L'ADMINISTRATEUR
  // =========================================================================
  app.post('/api/admin/sellers/review', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const isAdminUser = await verifyIsAdmin(user);
      if (!isAdminUser) {
        res.status(403).json({
          error: 'Accès refusé : seul l’administrateur propriétaire peut approuver ou refuser une boutique.',
        });
        return;
      }

      const sellerId = String(req.body.sellerId || '').trim();
      const decision = String(req.body.decision || '').trim() as 'approved' | 'rejected' | 'suspended';
      const rawReason = String(req.body.reason || '').trim().slice(0, 380);

      if (!sellerId || !['approved', 'rejected', 'suspended'].includes(decision)) {
        res.status(400).json({ error: 'Paramètres de décision administrateur invalides.' });
        return;
      }

      if (decision === 'rejected' && rawReason.length < 3) {
        res.status(400).json({
          error: 'Veuillez indiquer un motif de refus (au moins 3 caractères) pour informer le vendeur.',
        });
        return;
      }

      const sellerSnap = await fsGetDoc('sellers', sellerId, user.idToken);
      if (!sellerSnap.exists) {
        res.status(404).json({ error: 'Dossier vendeur introuvable dans Firestore.' });
        return;
      }

      const shopId = String(sellerSnap.data.shopId || '');
      const applicationId = String(sellerSnap.data.applicationId || '');

      const [shopSnap, appSnap] = await Promise.all([
        fsGetDoc('shops', shopId, user.idToken),
        applicationId
          ? fsGetDoc('sellerApplications', applicationId, user.idToken)
          : Promise.resolve({ exists: false, data: {}, updateTime: undefined } as FsDocSnapshot),
      ]);

      if (!shopSnap.exists) {
        res.status(404).json({ error: 'Boutique associée introuvable dans Firestore.' });
        return;
      }

      const statusLabel = SELLER_APPROVAL_LABELS[decision as ApprovalStatus];
      const nextShopStatus =
        decision === 'approved'
          ? 'active'
          : decision === 'rejected'
            ? 'rejected'
            : 'suspended';
      const cleanReason = decision === 'approved' ? '' : rawReason;

      const writes: CommitWriteInput[] = [];

      writes.push({
        collection: 'sellers',
        docId: sellerId,
        fields: {
          ...sellerSnap.data,
          approvalStatus: decision,
          statusLabel,
          rejectionReason: cleanReason,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: sellerSnap.updateTime },
      });

      writes.push({
        collection: 'shops',
        docId: shopId,
        fields: {
          ...shopSnap.data,
          status: nextShopStatus,
        },
        serverTimeFields: ['updatedAt'],
        precondition: { updateTime: shopSnap.updateTime },
      });

      if (applicationId && appSnap.exists) {
        writes.push({
          collection: 'sellerApplications',
          docId: applicationId,
          fields: {
            ...appSnap.data,
            status: decision,
            statusLabel,
            rejectionReason: cleanReason,
            reviewedByUid: user.uid,
            reviewedByEmail: user.email,
          },
          serverTimeFields: ['updatedAt'],
          precondition: { updateTime: appSnap.updateTime },
        });
      }

      const notifId = makeSafeId('ntf');
      const notifTitle =
        decision === 'approved'
          ? `Boutique « ${sellerSnap.data.shopName} » approuvée !`
          : decision === 'rejected'
            ? `Demande boutique « ${sellerSnap.data.shopName} » refusée`
            : `Boutique « ${sellerSnap.data.shopName} » suspendue`;
      const notifBody =
        decision === 'approved'
          ? 'Votre tableau de bord vendeur est désormais déverrouillé : vous pouvez publier vos produits gratuitement sur Lubumarket.'
          : decision === 'rejected'
            ? `Motif communiqué par l’administration : ${cleanReason}. Vous pouvez corriger votre dossier et soumettre une nouvelle demande.`
            : `Votre boutique a été suspendue par l’administration${cleanReason ? ` (${cleanReason})` : ''}.`;

      writes.push({
        collection: 'notifications',
        docId: notifId,
        fields: {
          id: notifId,
          recipientId: sellerId,
          senderId: user.uid,
          eventType: `seller_${decision}`,
          title: notifTitle.slice(0, 155),
          body: notifBody.slice(0, 490),
          isRead: false,
          channels: ['in_app'],
        },
        serverTimeFields: ['createdAt', 'updatedAt'],
        precondition: { exists: false },
      });

      const logId = makeSafeId('log');
      writes.push({
        collection: 'adminLogs',
        docId: logId,
        fields: {
          id: logId,
          actorUid: user.uid,
          actorEmail: user.email,
          action:
            decision === 'approved'
              ? 'APPROVE_SELLER'
              : decision === 'rejected'
                ? 'REJECT_SELLER'
                : 'SUSPEND_SELLER',
          targetType: 'seller',
          targetId: sellerId,
          details: `Boutique ${sellerSnap.data.shopName} (${applicationId || shopId}) -> ${statusLabel}${cleanReason ? ` | Motif : ${cleanReason}` : ''}`.slice(0, 490),
        },
        serverTimeFields: ['createdAt'],
        precondition: { exists: false },
      });

      await fsAtomicCommit(writes, user.idToken);

      res.json({
        ok: true,
        sellerId,
        shopId,
        applicationId: applicationId || null,
        approvalStatus: decision,
        shopStatus: nextShopStatus,
        statusLabel,
      });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // 16. PUBLICATION / MISE À JOUR PRODUIT BLOQUÉE SI VENDEUR NON APPROUVÉ
  // =========================================================================
  app.post('/api/products/save', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const user = req.authUser!;
      const sellerSnap = await fsGetDoc('sellers', user.uid, user.idToken);
      const shopId = sellerSnap.exists ? String(sellerSnap.data.shopId || '') : '';
      const shopSnap = shopId
        ? await fsGetDoc('shops', shopId, user.idToken)
        : ({ exists: false, data: {} } as FsDocSnapshot);

      const eligibility = canSellerPublishProduct(
        sellerSnap.exists ? sellerSnap.data : null,
        shopSnap.exists ? shopSnap.data : null,
        user.uid
      );

      if (!eligibility.allowed) {
        res.status(403).json({
          error:
            eligibility.reason ||
            'Publication refusée : votre boutique vendeur doit être approuvée par l’administrateur.',
        });
        return;
      }

      const editingProductId = req.body.productId ? String(req.body.productId).trim() : '';
      const title = String(req.body.title || '').trim().slice(0, 155);
      const description = String(req.body.description || '').trim().slice(0, 1950);
      const categoryId = String(req.body.categoryId || '').trim();
      const categoryName = String(req.body.categoryName || '').trim().slice(0, 100);
      const currency: CurrencyCode = req.body.currency === 'CDF' ? 'CDF' : 'USD';
      const price = roundMoney(Number(req.body.price), currency);
      const stock = Math.max(0, Math.floor(Number(req.body.stock)));
      const status = req.body.status === 'draft' ? 'draft' : 'active';
      const rawImages: string[] = Array.isArray(req.body.imageUrls)
        ? req.body.imageUrls.map((u: unknown) => String(u || '').trim()).filter(Boolean).slice(0, 5)
        : [];

      if (title.length < 2 || price <= 0 || !categoryId || categoryName.length < 2) {
        res.status(400).json({
          error: 'Veuillez renseigner un titre valide, une catégorie et un prix supérieur à zéro.',
        });
        return;
      }
      if (rawImages.length === 0 || !rawImages.every((img) => isValidImageUrlOrDataUri(img))) {
        res.status(400).json({
          error: 'Veuillez fournir au moins une image produit valide.',
        });
        return;
      }

      if (editingProductId) {
        const existingProdSnap = await fsGetDoc('products', editingProductId, user.idToken);
        if (!existingProdSnap.exists || existingProdSnap.data.sellerId !== user.uid) {
          res.status(403).json({ error: 'Modification non autorisée sur ce produit.' });
          return;
        }

        await fsAtomicCommit(
          [
            {
              collection: 'products',
              docId: editingProductId,
              fields: {
                ...existingProdSnap.data,
                categoryId,
                categoryName,
                title,
                description,
                price,
                currency,
                stock,
                imageUrls: rawImages,
                city: String(sellerSnap.data.city || 'Lubumbashi'),
                status,
              },
              serverTimeFields: ['updatedAt'],
              precondition: { updateTime: existingProdSnap.updateTime },
            },
          ],
          user.idToken
        );

        res.json({ ok: true, productId: editingProductId, updated: true });
        return;
      }

      const productId = makeSafeId('prd');
      await fsAtomicCommit(
        [
          {
            collection: 'products',
            docId: productId,
            fields: {
              id: productId,
              sellerId: user.uid,
              shopId,
              shopName: String(shopSnap.data.name || sellerSnap.data.shopName),
              categoryId,
              categoryName,
              title,
              description,
              price,
              currency,
              stock,
              imageUrls: rawImages,
              city: String(sellerSnap.data.city || 'Lubumbashi'),
              soldCount: 0,
              ratingAvg: 0,
              ratingCount: 0,
              status,
              lastOrderRef: 'none',
              lastReviewRef: 'none',
            },
            serverTimeFields: ['createdAt', 'updatedAt'],
            precondition: { exists: false },
          },
        ],
        user.idToken
      );

      const verifyProd = await fsGetDoc('products', productId, user.idToken);
      if (!verifyProd.exists) {
        res.status(500).json({ error: 'Le produit n’a pas pu être vérifié dans Cloud Firestore.' });
        return;
      }

      res.json({ ok: true, productId, created: true });
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : String(err) });
    }
  });

  // =========================================================================
  // VITE MIDDLEWARE (DEV) OU FICHIERS STATIQUES (PROD)
  // =========================================================================
  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  return app;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  createLubumarketApp().then((app) => {
    const port = Number(process.env.PORT || 3000);
    app.listen(port, '0.0.0.0', () => {
      console.log(`Lubumarket server listening on http://0.0.0.0:${port}`);
    });
  });
}
