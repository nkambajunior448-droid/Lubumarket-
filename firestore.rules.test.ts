import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const rulesPath = path.resolve(process.cwd(), 'firestore.rules');
const rulesContent = fs.readFileSync(rulesPath, 'utf8');

describe('Audit Zero-Trust des règles Cloud Firestore (Dirty Dozen & séparation des 4 rôles)', () => {
  it('applique un verrou global Default-Deny sur tout document non explicitement autorisé', () => {
    expect(rulesContent).toContain("rules_version = '2';");
    expect(rulesContent).toMatch(/match \/\{document=\*\*\} \{\s*allow read, write: if false;\s*\}/);
  });

  it('empêche toute auto-élévation de privilège vers le rôle admin lors de la création ou mise à jour de users', () => {
    expect(rulesContent).toContain("(incoming().role != 'admin' || isBootstrapAdminEmail())");
    expect(rulesContent).toContain("existing().role == 'customer'");
    expect(rulesContent).toContain("(incoming().role == 'seller' || incoming().role == 'courier')");
  });

  it('isole strictement le code secret client en clair dans customerDeliveryCodes (jamais lisible par le livreur)', () => {
    const codeBlockStart = rulesContent.indexOf('match /customerDeliveryCodes/{deliveryId}');
    const nextBlockStart = rulesContent.indexOf('match /payments/{paymentId}');
    const codeBlock = rulesContent.slice(codeBlockStart, nextBlockStart);

    expect(codeBlock).toContain('isOwner(existing().customerId) || isAdmin()');
    expect(codeBlock).not.toContain('courierId');
  });

  it('interdit à un vendeur de lire le portefeuille comptable d’un autre vendeur et verrouille la commission à 5 %', () => {
    const walletStart = rulesContent.indexOf('match /wallets/{sellerId}');
    const walletEnd = rulesContent.indexOf('match /walletTransactions/{txId}');
    const walletBlock = rulesContent.slice(walletStart, walletEnd);

    expect(walletBlock).toContain('allow get: if isValidId(sellerId) && (isOwner(sellerId) || isAdmin());');
    expect(walletBlock).toContain('allow list: if isAdmin();');
    expect(rulesContent).toContain('d.commissionRatePercent == 5');
    expect(rulesContent).toContain('d.ratePercent == 5');
  });

  it('impose des listes blanches strictes hasOnly() et affectedKeys().hasOnly() sur les collections critiques', () => {
    expect(rulesContent).toContain('.keys().hasOnly(');
    expect(rulesContent).toContain('.diff(existing()).affectedKeys().hasOnly(');
    expect(rulesContent).toContain("getAfter(/databases/$(database)/documents/sellerSubOrders/$(incoming().lastActionRef)).data.status == 'delivered'");
    expect(rulesContent).toContain("getAfter(/databases/$(database)/documents/sellerSubOrders/$(incoming().lastActionRef)).data.paymentStatus == 'confirmed'");
  });

  it('impose le statut initial « En attente de validation » sur sellerApplications et réserve l’approbation/refus à l’administrateur', () => {
    const appStart = rulesContent.indexOf('match /sellerApplications/{applicationId}');
    const appEnd = rulesContent.indexOf('match /couriers/{courierId}');
    const appBlock = rulesContent.slice(appStart, appEnd);

    expect(appBlock).toContain("incoming().status == 'pending'");
    expect(appBlock).toContain("incoming().statusLabel == 'En attente de validation'");
    expect(appBlock).toContain('allow update: if isValidId(applicationId) && isAdmin()');
    expect(appBlock).toContain("affectedKeys().hasOnly(['status', 'statusLabel', 'rejectionReason', 'reviewedByUid', 'reviewedByEmail', 'updatedAt'])");
  });

  it('interdit à un vendeur de s’auto-approuver dans sellers ou d’auto-activer sa boutique dans shops', () => {
    const sellerStart = rulesContent.indexOf('match /sellers/{sellerId}');
    const sellerEnd = rulesContent.indexOf('match /sellerApplications/{applicationId}');
    const sellerBlock = rulesContent.slice(sellerStart, sellerEnd);

    expect(sellerBlock).toContain("(isAdmin() || (isOwner(sellerId) && incoming().approvalStatus == 'pending'))");
    expect(sellerBlock).toContain("existing().approvalStatus == 'rejected' &&");
    expect(sellerBlock).toContain("incoming().approvalStatus == 'pending'");

    const shopStart = rulesContent.indexOf('match /shops/{shopId}');
    const shopEnd = rulesContent.indexOf('match /categories/{categoryId}');
    const shopBlock = rulesContent.slice(shopStart, shopEnd);

    expect(shopBlock).toContain("(isAdmin() || (incoming().sellerId == request.auth.uid && incoming().status == 'pending'))");
    expect(shopBlock).toContain("existing().status == 'rejected' &&");
    expect(shopBlock).toContain("incoming().status == 'pending'");
  });

  it('bloque strictement toute création ou modification de produit par un vendeur non approuvé ou dont la boutique est inactive', () => {
    expect(rulesContent).toContain('function isApprovedSellerWithActiveShop(uid, shopId)');
    expect(rulesContent).toContain("get(/databases/$(database)/documents/sellers/$(uid)).data.approvalStatus == 'approved'");
    expect(rulesContent).toContain("get(/databases/$(database)/documents/shops/$(shopId)).data.status == 'active'");

    const prodStart = rulesContent.indexOf('match /products/{productId}');
    const prodEnd = rulesContent.indexOf('match /addresses/{addressId}');
    const prodBlock = rulesContent.slice(prodStart, prodEnd !== -1 ? prodEnd : undefined);

    expect(prodBlock).toContain('isApprovedSellerWithActiveShop(request.auth.uid, incoming().shopId)');
    expect(prodBlock).toContain('isApprovedSellerWithActiveShop(request.auth.uid, existing().shopId)');
  });
});

