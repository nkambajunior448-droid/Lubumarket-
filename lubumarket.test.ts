import { describe, expect, it } from 'vitest';
import {
  INITIAL_CATEGORIES,
  LUBUMARKET_COMMISSION_PERCENT,
  SELLER_APPROVAL_LABELS,
  buildMultiSellerOrderPlan,
  buildValidatedSellerRegistration,
  calculateWithdrawalBreakdown,
  canSellerPublishProduct,
  convertAmount,
} from '../src/shared/domain';
import {
  createLubumarketApp,
  generateDeliverySecret,
  hashDeliveryCode,
} from '../server';

describe('Lubumarket — Règles métier fondamentales & sécurité serveur', () => {
  it('prélève exactement 5 % sur un retrait total de 100 USD (5 USD commission, 95 USD net, 0 USD restant)', () => {
    const res = calculateWithdrawalBreakdown(100, 100, 'USD');
    expect(LUBUMARKET_COMMISSION_PERCENT).toBe(5);
    expect(res.grossAmount).toBe(100);
    expect(res.commissionRatePercent).toBe(5);
    expect(res.commissionAmount).toBe(5);
    expect(res.netAmount).toBe(95);
    expect(res.remainingAvailableBalance).toBe(0);
  });

  it('autorise un retrait partiel de 20 USD sur un solde disponible de 100 USD (1 USD commission, 19 USD net, 80 USD restant)', () => {
    const res = calculateWithdrawalBreakdown(20, 100, 'USD');
    expect(res.grossAmount).toBe(20);
    expect(res.commissionAmount).toBe(1);
    expect(res.netAmount).toBe(19);
    expect(res.remainingAvailableBalance).toBe(80);
  });

  it('calcule également exactement 5 % en Francs Congolais (CDF)', () => {
    const res = calculateWithdrawalBreakdown(200000, 500000, 'CDF');
    expect(res.commissionAmount).toBe(10000);
    expect(res.netAmount).toBe(190000);
    expect(res.remainingAvailableBalance).toBe(300000);
  });

  it('rejette strictement tout retrait supérieur au solde disponible ou montant négatif/nul', () => {
    expect(() => calculateWithdrawalBreakdown(100.01, 100, 'USD')).toThrow(/insuffisant/i);
    expect(() => calculateWithdrawalBreakdown(0, 100, 'USD')).toThrow(/supérieur à zéro/i);
    expect(() => calculateWithdrawalBreakdown(-50, 100, 'USD')).toThrow(/supérieur à zéro/i);
  });

  it('sépare un panier multi-vendeurs en sous-commandes distinctes avec 0 % de commission à l’achat', () => {
    const plan = buildMultiSellerOrderPlan(
      [
        {
          productId: 'p1',
          title: 'Smartphone 5G',
          imageUrl: '/icon.svg',
          sellerId: 'seller-A',
          shopId: 'shop-A',
          shopName: 'Katanga Tech',
          shopAddress: 'Centre-ville',
          shopPhone: '+243990000001',
          unitPriceNative: 200,
          nativeCurrency: 'USD',
          quantity: 1,
          availableStock: 5,
        },
        {
          productId: 'p2',
          title: 'Chemise Coton',
          imageUrl: '/icon.svg',
          sellerId: 'seller-B',
          shopId: 'shop-B',
          shopName: 'Mode Gambela',
          shopAddress: 'Golf',
          shopPhone: '+243990000002',
          unitPriceNative: 57000,
          nativeCurrency: 'CDF',
          quantity: 2,
          availableStock: 10,
        },
      ],
      'USD',
      4,
      2850
    );

    expect(plan.sellerGroups).toHaveLength(2);
    // 57 000 CDF / 2850 = 20 USD * 2 = 40 USD
    expect(plan.merchandiseTotal).toBe(240);
    expect(plan.deliveryFee).toBe(4);
    expect(plan.grandTotal).toBe(244);

    // 100 % du sous-total marchandises est attribué à chaque boutique (aucune retenue de 5 % à la commande)
    const sellerA = plan.sellerGroups.find((g) => g.sellerId === 'seller-A')!;
    const sellerB = plan.sellerGroups.find((g) => g.sellerId === 'seller-B')!;
    expect(sellerA.subtotalOrderCurrency).toBe(200);
    expect(sellerB.subtotalOrderCurrency).toBe(40);
    expect(sellerA.deliveryFeeShareOrderCurrency + sellerB.deliveryFeeShareOrderCurrency).toBe(4);
  });

  it('refuse une commande si la quantité demandée dépasse le stock disponible', () => {
    expect(() =>
      buildMultiSellerOrderPlan(
        [
          {
            productId: 'p1',
            title: 'Ordinateur',
            imageUrl: '/icon.svg',
            sellerId: 'seller-A',
            shopId: 'shop-A',
            shopName: 'Katanga Tech',
            shopAddress: 'Lubumbashi',
            shopPhone: '',
            unitPriceNative: 500,
            nativeCurrency: 'USD',
            quantity: 4,
            availableStock: 2,
          },
        ],
        'USD',
        3,
        2850
      )
    ).toThrow(/Stock insuffisant/i);
  });

  it('convertit fidèlement entre USD et CDF selon le taux administrateur', () => {
    expect(convertAmount(10, 'USD', 'CDF', 2850)).toBe(28500);
    expect(convertAmount(28500, 'CDF', 'USD', 2850)).toBe(10);
    expect(convertAmount(50, 'USD', 'USD', 2850)).toBe(50);
  });

  it('contient exactement les 12 catégories officielles demandées pour Lubumarket', () => {
    expect(INITIAL_CATEGORIES).toHaveLength(12);
    const names = INITIAL_CATEGORIES.map((c) => c.name);
    expect(names).toContain('Téléphones et accessoires');
    expect(names).toContain('Matériaux de construction');
    expect(names).toContain('Santé et hygiène');
    expect(names).toContain('Autres');
  });

  it('génère et vérifie cryptographiquement le code secret de livraison à 6 chiffres', () => {
    const secret = generateDeliverySecret();
    expect(secret.code).toMatch(/^\d{6}$/);
    expect(secret.hash).toHaveLength(64);
    expect(hashDeliveryCode(secret.code, secret.salt)).toBe(secret.hash);
    expect(hashDeliveryCode('000000', secret.salt)).not.toBe(secret.hash);
  });

  it('expose /.well-known/assetlinks.json pour com.lubumarket.app et protège les routes sensibles par jeton Bearer', async () => {
    process.env.NODE_ENV = 'production';
    const app = await createLubumarketApp();
    const server = app.listen(0);
    try {
      const addr = server.address();
      const port = typeof addr === 'object' && addr ? addr.port : 0;
      const base = `http://127.0.0.1:${port}`;

      const assetRes = await fetch(`${base}/.well-known/assetlinks.json`);
      expect(assetRes.status).toBe(200);
      const assetJson = await assetRes.json();
      expect(assetJson[0].target.package_name).toBe('com.lubumarket.app');

      const pubCfgRes = await fetch(`${base}/api/config/public`);
      expect(pubCfgRes.status).toBe(200);
      const pubCfg = await pubCfgRes.json();
      expect(pubCfg.commissionPercentOnWithdrawalOnly).toBe(5);

      // Rejet strict sans jeton Firebase vérifié (401)
      const unauthOrder = await fetch(`${base}/api/orders/create`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      expect(unauthOrder.status).toBe(401);

      const unauthWdr = await fetch(`${base}/api/withdrawals/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: 100 }),
      });
      expect(unauthWdr.status).toBe(401);

      // Routes d'inscription vendeur, revue admin et publication produit protégées par jeton Bearer (401)
      const unauthSellerReg = await fetch(`${base}/api/sellers/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ shopName: 'Boutique Test' }),
      });
      expect(unauthSellerReg.status).toBe(401);

      const unauthAdminReview = await fetch(`${base}/api/admin/sellers/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sellerId: 'uid-1', decision: 'approved' }),
      });
      expect(unauthAdminReview.status).toBe(401);

      const unauthProdSave = await fetch(`${base}/api/products/save`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Article interdit' }),
      });
      expect(unauthProdSave.status).toBe(401);

      // Diagnostic réel du bucket Firebase Storage (aucune fausse confirmation)
      const storageRes = await fetch(`${base}/api/storage/status`);
      expect(storageRes.status).toBe(200);
      const storageJson = await storageRes.json();
      expect(typeof storageJson.bucket).toBe('string');
      expect(storageJson.bucket.length).toBeGreaterThan(5);
      expect(typeof storageJson.operational).toBe('boolean');
      expect(typeof storageJson.httpStatus).toBe('number');
      expect(typeof storageJson.reason).toBe('string');
    } finally {
      server.close();
      process.env.NODE_ENV = 'test';
    }
  });

  it('valide l’inscription vendeur avec le statut strict « En attente de validation » et détecte le mode réel de stockage du logo', () => {
    expect(SELLER_APPROVAL_LABELS.pending).toBe('En attente de validation');
    expect(SELLER_APPROVAL_LABELS.approved).toBe('Approuvée');
    expect(SELLER_APPROVAL_LABELS.rejected).toBe('Refusée');

    const storageLogo = buildValidatedSellerRegistration({
      shopName: 'Établissement Katanga Pro',
      logoUrl: 'https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0555201904.firebasestorage.app/o/shops%2Flogo.webp?alt=media',
      description: 'Matériel électrique et solaire à Lubumbashi.',
      phone: '+243 99 123 4567',
      address: 'Avenue Kasavubu N°42, Centre-ville',
      activityZone: 'Lubumbashi Centre & Makomeno',
      city: 'Lubumbashi',
    });
    expect(storageLogo.status).toBe('pending');
    expect(storageLogo.statusLabel).toBe('En attente de validation');
    expect(storageLogo.logoStorageMode).toBe('firebase_storage');

    const inlineFallbackLogo = buildValidatedSellerRegistration({
      shopName: 'Boutique Golf Mode',
      logoUrl: 'data:image/webp;base64,UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=',
      phone: '+243 81 000 1122',
      address: 'Avenue des Chutes, Golf',
      activityZone: 'Golf, Gambela & Kasapa',
    });
    expect(inlineFallbackLogo.status).toBe('pending');
    expect(inlineFallbackLogo.statusLabel).toBe('En attente de validation');
    expect(inlineFallbackLogo.logoStorageMode).toBe('firestore_inline_fallback');

    expect(() =>
      buildValidatedSellerRegistration({
        shopName: 'A',
        phone: '+243990000000',
        address: 'Avenue Kasavubu',
        activityZone: 'Centre',
      })
    ).toThrow(/nom commercial/i);
  });

  it('empêche tout vendeur non approuvé (« En attente de validation », « Refusée », « Suspendue ») de publier des produits et autorise uniquement après approbation', () => {
    const uid = 'seller-lubumbashi-01';
    const shopId = 'shp-lubumbashi-01';

    // 1. Vendeur en attente de validation -> BLOQUÉ
    const pendingCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'pending' },
      { id: shopId, sellerId: uid, status: 'pending' },
      uid
    );
    expect(pendingCheck.allowed).toBe(false);
    expect(pendingCheck.reason).toContain('En attente de validation');

    // 2. Vendeur refusé -> BLOQUÉ
    const rejectedCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'rejected' },
      { id: shopId, sellerId: uid, status: 'rejected' },
      uid
    );
    expect(rejectedCheck.allowed).toBe(false);
    expect(rejectedCheck.reason).toContain('Refusée');

    // 3. Vendeur suspendu -> BLOQUÉ
    const suspendedCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'suspended' },
      { id: shopId, sellerId: uid, status: 'suspended' },
      uid
    );
    expect(suspendedCheck.allowed).toBe(false);
    expect(suspendedCheck.reason).toContain('Suspendue');

    // 4. Vendeur approuvé mais boutique non active -> BLOQUÉ
    const inactiveShopCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'approved' },
      { id: shopId, sellerId: uid, status: 'pending' },
      uid
    );
    expect(inactiveShopCheck.allowed).toBe(false);

    // 5. Usurpation d'un autre UID -> BLOQUÉ
    const spoofCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'approved' },
      { id: shopId, sellerId: uid, status: 'active' },
      'intruder-uid'
    );
    expect(spoofCheck.allowed).toBe(false);

    // 6. Vendeur approuvé par l'administrateur + boutique active -> AUTORISÉ
    const approvedCheck = canSellerPublishProduct(
      { uid, shopId, approvalStatus: 'approved' },
      { id: shopId, sellerId: uid, status: 'active' },
      uid
    );
    expect(approvedCheck.allowed).toBe(true);
  });
});

