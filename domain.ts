export type CurrencyCode = 'USD' | 'CDF';
export type UserRole = 'customer' | 'seller' | 'courier' | 'admin';
export type ApprovalStatus = 'pending' | 'approved' | 'suspended' | 'rejected';

export type OrderStatus =
  | 'pending'
  | 'payment_pending'
  | 'paid'
  | 'accepted'
  | 'preparing'
  | 'ready_for_pickup'
  | 'in_delivery'
  | 'delivered'
  | 'cancelled'
  | 'refunded';

export type PaymentStatus = 'pending' | 'confirmed' | 'failed' | 'refunded';
export type DeliveryStatus =
  | 'awaiting_assignment'
  | 'assigned'
  | 'picked_up'
  | 'delivered'
  | 'cancelled';

export const LUBUMARKET_COMMISSION_PERCENT = 5;

export interface WithdrawalBreakdown {
  grossAmount: number;
  commissionRatePercent: number;
  commissionAmount: number;
  netAmount: number;
  remainingAvailableBalance: number;
  currency: CurrencyCode;
}

export function roundMoney(amount: number, currency: CurrencyCode = 'USD'): number {
  if (!Number.isFinite(amount)) {
    throw new Error('Montant invalide.');
  }
  if (currency === 'CDF') {
    return Math.round(amount * 100) / 100;
  }
  return Math.round((amount + Number.EPSILON) * 100) / 100;
}

/**
 * Règle fondamentale Lubumarket (Section 9) :
 * - 0 % à l'inscription, à la publication ou à la commande.
 * - 5 % prélevé exclusivement lors d'une demande de retrait sur le montant demandé.
 * - Retrait partiel autorisé si <= solde disponible.
 */
export function calculateWithdrawalBreakdown(
  requestedGrossAmount: number,
  currentAvailableBalance: number,
  currency: CurrencyCode
): WithdrawalBreakdown {
  const gross = roundMoney(requestedGrossAmount, currency);
  const available = roundMoney(currentAvailableBalance, currency);

  if (gross <= 0) {
    throw new Error('Le montant du retrait doit être strictement supérieur à zéro.');
  }
  if (gross > available) {
    throw new Error(
      `Solde disponible insuffisant (${available} ${currency} disponible pour ${gross} ${currency} demandé).`
    );
  }

  const commissionAmount = roundMoney((gross * LUBUMARKET_COMMISSION_PERCENT) / 100, currency);
  const netAmount = roundMoney(gross - commissionAmount, currency);
  const remainingAvailableBalance = roundMoney(available - gross, currency);

  return {
    grossAmount: gross,
    commissionRatePercent: LUBUMARKET_COMMISSION_PERCENT,
    commissionAmount,
    netAmount,
    remainingAvailableBalance,
    currency,
  };
}

export function convertAmount(
  amount: number,
  fromCurrency: CurrencyCode,
  toCurrency: CurrencyCode,
  usdToCdfRate: number
): number {
  if (usdToCdfRate <= 0 || !Number.isFinite(usdToCdfRate)) {
    throw new Error('Taux de change invalide.');
  }
  if (fromCurrency === toCurrency) {
    return roundMoney(amount, toCurrency);
  }
  if (fromCurrency === 'USD' && toCurrency === 'CDF') {
    return roundMoney(amount * usdToCdfRate, 'CDF');
  }
  return roundMoney(amount / usdToCdfRate, 'USD');
}

export interface CartCalculationItem {
  productId: string;
  title: string;
  imageUrl: string;
  sellerId: string;
  shopId: string;
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  unitPriceNative: number;
  nativeCurrency: CurrencyCode;
  quantity: number;
  availableStock: number;
}

export interface SellerSplitGroup {
  sellerId: string;
  shopId: string;
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  items: Array<{
    productId: string;
    title: string;
    imageUrl: string;
    quantity: number;
    unitPriceOrderCurrency: number;
    lineTotalOrderCurrency: number;
  }>;
  subtotalOrderCurrency: number;
  deliveryFeeShareOrderCurrency: number;
  itemsSummary: string;
}

export interface MultiSellerOrderPlan {
  orderCurrency: CurrencyCode;
  exchangeRateAtOrder: number;
  merchandiseTotal: number;
  deliveryFee: number;
  grandTotal: number;
  sellerGroups: SellerSplitGroup[];
}

/**
 * Sépare automatiquement un panier multi-vendeurs en sous-commandes vendeurs distinctes,
 * vérifie les stocks et fige les montants dans la devise choisie par le client (0 % commission à l'achat).
 */
export function buildMultiSellerOrderPlan(
  items: CartCalculationItem[],
  orderCurrency: CurrencyCode,
  deliveryFeeUSD: number,
  usdToCdfRate: number
): MultiSellerOrderPlan {
  if (!items.length) {
    throw new Error('Le panier est vide.');
  }

  const bySeller = new Map<string, CartCalculationItem[]>();
  for (const item of items) {
    if (!Number.isInteger(item.quantity) || item.quantity < 1) {
      throw new Error(`Quantité invalide pour ${item.title}.`);
    }
    if (item.quantity > item.availableStock) {
      throw new Error(
        `Stock insuffisant pour "${item.title}" (${item.availableStock} en stock, ${item.quantity} demandé).`
      );
    }
    const existing = bySeller.get(item.sellerId) ?? [];
    existing.push(item);
    bySeller.set(item.sellerId, existing);
  }

  const totalDeliveryFee = convertAmount(deliveryFeeUSD, 'USD', orderCurrency, usdToCdfRate);
  const sellerCount = bySeller.size;
  const sellerGroups: SellerSplitGroup[] = [];
  let merchandiseTotal = 0;
  let allocatedDelivery = 0;
  let idx = 0;

  for (const [sellerId, groupItems] of bySeller.entries()) {
    idx += 1;
    const first = groupItems[0];
    let subtotal = 0;
    const mappedItems = groupItems.map((it) => {
      const unitPrice = convertAmount(
        it.unitPriceNative,
        it.nativeCurrency,
        orderCurrency,
        usdToCdfRate
      );
      const lineTotal = roundMoney(unitPrice * it.quantity, orderCurrency);
      subtotal = roundMoney(subtotal + lineTotal, orderCurrency);
      return {
        productId: it.productId,
        title: it.title,
        imageUrl: it.imageUrl,
        quantity: it.quantity,
        unitPriceOrderCurrency: unitPrice,
        lineTotalOrderCurrency: lineTotal,
      };
    });

    const deliveryShare =
      idx === sellerCount
        ? roundMoney(totalDeliveryFee - allocatedDelivery, orderCurrency)
        : roundMoney(totalDeliveryFee / sellerCount, orderCurrency);
    allocatedDelivery = roundMoney(allocatedDelivery + deliveryShare, orderCurrency);
    merchandiseTotal = roundMoney(merchandiseTotal + subtotal, orderCurrency);

    const summary = mappedItems
      .map((m) => `${m.quantity}x ${m.title}`)
      .join(', ')
      .slice(0, 480);

    sellerGroups.push({
      sellerId,
      shopId: first.shopId,
      shopName: first.shopName,
      shopAddress: first.shopAddress || 'Lubumbashi',
      shopPhone: first.shopPhone || '',
      items: mappedItems,
      subtotalOrderCurrency: subtotal,
      deliveryFeeShareOrderCurrency: deliveryShare,
      itemsSummary: summary,
    });
  }

  return {
    orderCurrency,
    exchangeRateAtOrder: usdToCdfRate,
    merchandiseTotal,
    deliveryFee: totalDeliveryFee,
    grandTotal: roundMoney(merchandiseTotal + totalDeliveryFee, orderCurrency),
    sellerGroups,
  };
}

export interface DefaultCategorySeed {
  id: string;
  name: string;
  slug: string;
  description: string;
  sortOrder: number;
}

export const INITIAL_CATEGORIES: DefaultCategorySeed[] = [
  { id: 'cat-telephones', name: 'Téléphones et accessoires', slug: 'telephones-accessoires', description: 'Smartphones, chargeurs, écouteurs, coques et accessoires mobiles.', sortOrder: 1 },
  { id: 'cat-informatique', name: 'Informatique', slug: 'informatique', description: 'Ordinateurs portables, imprimantes, réseaux et périphériques.', sortOrder: 2 },
  { id: 'cat-vetements', name: 'Vêtements et chaussures', slug: 'vetements-chaussures', description: 'Mode homme, femme, enfant, pagnes, tenues de ville et chaussures.', sortOrder: 3 },
  { id: 'cat-electronique', name: 'Électronique', slug: 'electronique', description: 'Téléviseurs, systèmes audio, groupes solaires et électroménager.', sortOrder: 4 },
  { id: 'cat-alimentation', name: 'Alimentation', slug: 'alimentation', description: 'Produits alimentaires, épicerie, boissons et produits frais.', sortOrder: 5 },
  { id: 'cat-maison', name: 'Maison et meubles', slug: 'maison-meubles', description: 'Mobilier de salon, literie, décoration et arts de la table.', sortOrder: 6 },
  { id: 'cat-beaute', name: 'Beauté et cosmétiques', slug: 'beaute-cosmetiques', description: 'Soins de la peau, coiffure, parfumerie et bien-être.', sortOrder: 7 },
  { id: 'cat-construction', name: 'Matériaux de construction', slug: 'materiaux-construction', description: 'Ciment, quincaillerie, peinture, plomberie et électricité.', sortOrder: 8 },
  { id: 'cat-pro', name: 'Équipements professionnels', slug: 'equipements-professionnels', description: 'Outillage industriel, EPI minier, fournitures de bureau et commerce.', sortOrder: 9 },
  { id: 'cat-auto', name: 'Automobile', slug: 'automobile', description: 'Pièces détachées, pneus, batteries, lubrifiants et accessoires auto/moto.', sortOrder: 10 },
  { id: 'cat-sante', name: 'Santé et hygiène', slug: 'sante-hygiene', description: 'Hygiène corporelle, matériel médical courant et parapharmacie.', sortOrder: 11 },
  { id: 'cat-autres', name: 'Autres', slug: 'autres', description: 'Articles divers, loisirs, livres et fournitures scolaires.', sortOrder: 12 },
];

export interface DefaultZoneSeed {
  id: string;
  city: string;
  province: string;
  name: string;
  communesText: string;
  feeUSD: number;
  estimatedTime: string;
  isActive: boolean;
}

export const INITIAL_DELIVERY_ZONES: DefaultZoneSeed[] = [
  { id: 'zone-lushi-centre', city: 'Lubumbashi', province: 'Haut-Katanga', name: 'Lubumbashi Centre & Makomeno', communesText: 'Centre-ville, Makomeno, Baudouin, Kalubwe', feeUSD: 3, estimatedTime: '45 - 90 min', isActive: true },
  { id: 'zone-lushi-golf', city: 'Lubumbashi', province: 'Haut-Katanga', name: 'Golf, Gambela & Kasapa', communesText: 'Golf Malela, Golf Plateau, Gambela, Kasapa', feeUSD: 4, estimatedTime: '60 - 120 min', isActive: true },
  { id: 'zone-lushi-kampemba', city: 'Lubumbashi', province: 'Haut-Katanga', name: 'Kampemba, Bel-Air & Industriel', communesText: 'Bel-Air, Kampemba, Kigoma, Quartier Industriel', feeUSD: 3.5, estimatedTime: '60 - 100 min', isActive: true },
  { id: 'zone-lushi-kenya-katuba', city: 'Lubumbashi', province: 'Haut-Katanga', name: 'Kenya, Kamalondo & Katuba', communesText: 'Kenya, Kamalondo, Katuba I-III, Kisanga', feeUSD: 3.5, estimatedTime: '60 - 120 min', isActive: true },
  { id: 'zone-lushi-ruashi', city: 'Lubumbashi', province: 'Haut-Katanga', name: 'Ruashi & Annexe', communesText: 'Ruashi, Luwowoshi, Kipushi Route, Karavia', feeUSD: 4.5, estimatedTime: '90 - 150 min', isActive: true },
  { id: 'zone-kolwezi', city: 'Kolwezi', province: 'Lualaba', name: 'Kolwezi Centre (Expansion)', communesText: 'Dilala, Manika', feeUSD: 5, estimatedTime: 'Expansion à activer', isActive: false },
  { id: 'zone-likasi', city: 'Likasi', province: 'Haut-Katanga', name: 'Likasi Centre (Expansion)', communesText: 'Likasi, Kikula, Panda', feeUSD: 4.5, estimatedTime: 'Expansion à activer', isActive: false },
  { id: 'zone-kinshasa', city: 'Kinshasa', province: 'Kinshasa', name: 'Kinshasa Gombe / Ngaliema (Expansion)', communesText: 'Gombe, Ngaliema, Kintambo, Limete', feeUSD: 5, estimatedTime: 'Expansion à activer', isActive: false },
];

export interface PaymentAdapterSeed {
  id: string;
  code: string;
  label: string;
  category: 'mobile_money' | 'card' | 'bank' | 'cod' | 'diaspora';
  isEnabled: boolean;
  isTestMode: boolean;
  integrationNote: string;
}

export const INITIAL_PAYMENT_CONFIGS: PaymentAdapterSeed[] = [
  { id: 'pay-cod', code: 'cod', label: 'Paiement à la livraison (Espèces)', category: 'cod', isEnabled: true, isTestMode: false, integrationNote: 'Encaissement à la remise du colis avec rapprochement administratif obligatoire avant disponibilité vendeur.' },
  { id: 'pay-mpesa', code: 'mpesa', label: 'M-Pesa (Vodacom RDC)', category: 'mobile_money', isEnabled: true, isTestMode: true, integrationNote: 'Adaptateur Mobile Money prêt — Mode test actif en attente des clés marchandes Vodacom M-Pesa.' },
  { id: 'pay-airtel', code: 'airtel_money', label: 'Airtel Money RDC', category: 'mobile_money', isEnabled: true, isTestMode: true, integrationNote: 'Adaptateur Airtel Money prêt — Mode test actif en attente du contrat API marchand Airtel.' },
  { id: 'pay-orange', code: 'orange_money', label: 'Orange Money RDC', category: 'mobile_money', isEnabled: true, isTestMode: true, integrationNote: 'Adaptateur Orange Money prêt — Mode test actif en attente des identifiants WebPay Orange.' },
  { id: 'pay-afrimoney', code: 'afrimoney', label: 'Afrimoney', category: 'mobile_money', isEnabled: true, isTestMode: true, integrationNote: 'Adaptateur Afrimoney prêt — Mode test actif en attente de passerelle agréée.' },
  { id: 'pay-card', code: 'visa_mastercard', label: 'Carte Visa / Mastercard', category: 'card', isEnabled: true, isTestMode: true, integrationNote: 'Redirection prestataire PCI-DSS (aucun numéro complet stocké) — Mode test actif.' },
  { id: 'pay-rawbank', code: 'rawbank', label: 'Rawbank (Illicocash / Virement)', category: 'bank', isEnabled: true, isTestMode: true, integrationNote: 'Nécessite contrat marchand ou agrégateur agréé — Mode test actif.' },
  { id: 'pay-tmb', code: 'tmb', label: 'TMB (Pepele Mobile / Virement)', category: 'bank', isEnabled: true, isTestMode: true, integrationNote: 'Nécessite intégration prestataire officiel TMB — Mode test actif.' },
  { id: 'pay-equity', code: 'equity_bcdc', label: 'Equity BCDC', category: 'bank', isEnabled: true, isTestMode: true, integrationNote: 'Via passerelle marchande Equity BCDC ou virement — Mode test actif.' },
  { id: 'pay-ecobank', code: 'ecobank', label: 'Ecobank', category: 'bank', isEnabled: false, isTestMode: true, integrationNote: 'Adaptateur préparé — Désactivé par défaut en attente de contrat.' },
  { id: 'pay-uba', code: 'uba', label: 'UBA RDC', category: 'bank', isEnabled: false, isTestMode: true, integrationNote: 'Adaptateur préparé — Désactivé par défaut en attente de contrat.' },
  { id: 'pay-crdb', code: 'crdb', label: 'CRDB Bank RDC', category: 'bank', isEnabled: false, isTestMode: true, integrationNote: 'Adaptateur préparé — Désactivé par défaut en attente de contrat.' },
  { id: 'pay-bank-transfer', code: 'bank_transfer', label: 'Virement bancaire avec référence', category: 'bank', isEnabled: true, isTestMode: true, integrationNote: 'Validation manuelle par l’administrateur après réception sur relevé bancaire.' },
  { id: 'pay-diaspora', code: 'diaspora_link', label: 'Paiement par un proche à l’étranger', category: 'diaspora', isEnabled: true, isTestMode: true, integrationNote: 'Lien de paiement international pour livraison à Lubumbashi via prestataire carte agréé (Mode test).' },
];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  pending: 'En attente',
  payment_pending: 'Paiement en attente',
  paid: 'Payée',
  accepted: 'Acceptée',
  preparing: 'En préparation',
  ready_for_pickup: 'Prête à récupérer',
  in_delivery: 'En cours de livraison',
  delivered: 'Livrée',
  cancelled: 'Annulée',
  refunded: 'Remboursée',
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  pending: 'En attente',
  confirmed: 'Confirmé',
  failed: 'Échoué',
  refunded: 'Remboursé',
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  awaiting_assignment: 'En attente de livreur',
  assigned: 'Attribuée au livreur',
  picked_up: 'Colis récupéré chez le vendeur',
  delivered: 'Remise confirmée au client',
  cancelled: 'Annulée',
};

export type LogoStorageMode =
  | 'firebase_storage'
  | 'firestore_inline_fallback'
  | 'default_asset';

export const SELLER_APPROVAL_LABELS: Record<ApprovalStatus, string> = {
  pending: 'En attente de validation',
  approved: 'Approuvée',
  rejected: 'Refusée',
  suspended: 'Suspendue',
};

export interface SellerRegistrationInput {
  shopName: string;
  logoUrl?: string;
  logoStorageMode?: LogoStorageMode;
  description?: string;
  phone: string;
  address: string;
  activityZone: string;
  city?: string;
}

export interface ValidatedSellerRegistration {
  shopName: string;
  logoUrl: string;
  logoStorageMode: LogoStorageMode;
  description: string;
  phone: string;
  address: string;
  activityZone: string;
  city: string;
  status: 'pending';
  statusLabel: 'En attente de validation';
}

export function isValidImageUrlOrDataUri(raw: string): boolean {
  if (!raw || raw.length > 90000) return false;
  if (raw.startsWith('/') || raw.startsWith('https://')) return true;
  return /^data:image\/[a-zA-Z0-9.+\-]+;base64,[A-Za-z0-9+/=]+$/.test(raw);
}

export function buildValidatedSellerRegistration(
  input: SellerRegistrationInput
): ValidatedSellerRegistration {
  const shopName = String(input.shopName || '').trim().slice(0, 120);
  const phone = String(input.phone || '').trim().slice(0, 32);
  const address = String(input.address || '').trim().slice(0, 235);
  const activityZone = String(input.activityZone || 'Lubumbashi Centre & Makomeno')
    .trim()
    .slice(0, 100);
  const city = String(input.city || 'Lubumbashi').trim().slice(0, 80);
  const description = String(input.description || '').trim().slice(0, 950);
  const rawLogo = String(input.logoUrl || '/icon.svg').trim();

  if (shopName.length < 2) {
    throw new Error('Le nom commercial de la boutique doit contenir au moins 2 caractères.');
  }
  if (phone.length < 6) {
    throw new Error('Veuillez renseigner un numéro de téléphone professionnel valide.');
  }
  if (address.length < 4) {
    throw new Error('Veuillez indiquer l’adresse physique complète de récupération.');
  }
  if (activityZone.length < 2) {
    throw new Error('Veuillez sélectionner une zone d’activité valide.');
  }
  if (!isValidImageUrlOrDataUri(rawLogo)) {
    throw new Error('Le format ou la taille du logo est invalide (max 90 Ko après compression).');
  }

  let logoStorageMode: LogoStorageMode = input.logoStorageMode || 'default_asset';
  if (rawLogo.startsWith('https://firebasestorage.googleapis.com/')) {
    logoStorageMode = 'firebase_storage';
  } else if (rawLogo.startsWith('data:image/')) {
    logoStorageMode = 'firestore_inline_fallback';
  } else {
    logoStorageMode = 'default_asset';
  }

  return {
    shopName,
    logoUrl: rawLogo,
    logoStorageMode,
    description,
    phone,
    address,
    activityZone,
    city: city.length >= 2 ? city : 'Lubumbashi',
    status: 'pending',
    statusLabel: 'En attente de validation',
  };
}

export function canSellerPublishProduct(
  seller: Record<string, any> | null | undefined,
  shop: Record<string, any> | null | undefined,
  uid: string
): { allowed: boolean; reason?: string } {
  if (!seller || seller.uid !== uid) {
    return {
      allowed: false,
      reason: 'Aucun compte vendeur enregistré pour cet utilisateur.',
    };
  }
  if (seller.approvalStatus !== 'approved') {
    const label =
      SELLER_APPROVAL_LABELS[seller.approvalStatus as ApprovalStatus] ||
      String(seller.approvalStatus || 'non approuvé');
    return {
      allowed: false,
      reason: `Publication bloquée : votre boutique a le statut « ${label} ». L’approbation de l’administrateur est obligatoire.`,
    };
  }
  if (!shop || shop.id !== seller.shopId || shop.sellerId !== uid || shop.status !== 'active') {
    return {
      allowed: false,
      reason: 'Publication bloquée : la boutique associée n’est pas active.',
    };
  }
  return { allowed: true };
}

