# Spécification de Sécurité Zero-Trust — LUBUMARKET (`security_spec.md`)

## 1. Invariants de Données et d'Identité

1. **Séparation stricte des 4 rôles** : Un utilisateur possède un rôle vérifié (`customer`, `seller`, `courier`, `admin`). Aucune inscription publique ne permet d'obtenir le rôle `admin`. Seul l'administrateur propriétaire vérifié (`email_verified == true` avec email autorisé ou présence dans `/admins/{uid}`) possède les privilèges d'administration.
2. **Protection des PII (Données Personnelles)** : Les profils privés (`users`, `customers`, `sellers`, `couriers`, `addresses`) ne sont jamais accessibles en lecture globale (`isSignedIn()`). Seul le propriétaire légitime (`isOwner`) ou `isAdmin()` peut lire ses données personnelles.
3. **Isolation financière inter-vendeurs** : Un vendeur (`wallets/{sellerId}`, `withdrawals`, `commissions`, `walletTransactions`) ne peut consulter que son propre registre comptable et ne peut jamais modifier arbitrairement son solde disponible ni approuver son propre retrait.
4. **Règle fondamentale de commission de 5 % au retrait uniquement** :
   - 0 % prélevé à la publication d'un produit.
   - 0 % prélevé lors de la commande ou sur une commande non livrée.
   - 5 % prélevé exclusivement lors d'une demande de retrait validée côté serveur, avec vérification stricte que `grossAmount <= availableBalance`, `commissionAmount == round(grossAmount * 0.05)` et `netAmount == grossAmount - commissionAmount`.
5. **Isolation des livraisons et secret du code unique** :
   - Un livreur ne consulte que les livraisons où `resource.data.courierId == request.auth.uid`.
   - Le code secret de livraison à usage unique est stocké en clair uniquement dans `customerDeliveryCodes/{deliveryId}` (lisible exclusivement par le client `customerId` ou `isAdmin()`). Le document `deliveries/{deliveryId}` ne contient que l'empreinte SHA-256 salée (`codeHash`, `codeSalt`, `codeUsed`).
6. **Protection des écritures et horodatages** : Toutes les écritures imposent la validation stricte des clés (`hasAll` + `hasOnly` ou `affectedKeys().hasOnly`), des longueurs de chaînes (`.size()`), des formats d'identifiants (`isValidId`) et de l'horodatage serveur (`request.time`).

## 2. Les « Dirty Dozen » — 12 Payloads d'Attaque Bloqués (`PERMISSION_DENIED`)

1. **Auto-promotion Administrateur** : Création de `/users/{uid}` avec `{ "role": "admin" }` par un utilisateur standard.
2. **Usurpation d'email Admin non vérifié** : Écriture dans `/admins/{uid}` avec `email == 'nkambajunior448@gmail.com'` mais `email_verified == false`.
3. **Fuite PII inter-utilisateurs** : Lecture `get(/customers/victimUid)` par un autre utilisateur connecté `attackerUid`.
4. **Injection de solde frauduleux** : Mise à jour directe de `/wallets/{sellerId}` par le vendeur pour fixer `availableBalanceUSD: 50000`.
5. **Auto-approbation de retrait** : Mise à jour de `/withdrawals/{id}` par le vendeur avec `{ "status": "approved" }`.
6. **Contournement de la commission de 5 %** : Création d'un retrait de `100 USD` avec `commissionAmount: 0` et `netAmount: 100`.
7. **Vol du code secret de livraison par le livreur** : Lecture `get(/customerDeliveryCodes/{deliveryId})` par le livreur assigné.
8. **Scraping de livraisons non attribuées** : Requête `list(/deliveries)` par un livreur sans filtrer sur `courierId == request.auth.uid`.
9. **Shadow Field Injection (Champ fantôme)** : Création d'un produit avec un champ non autorisé `{ "isFeaturedHack": true }`.
10. **ID Poisoning / Denial of Wallet** : Création d'un document avec un identifiant de 500 caractères ou contenant des caractères spéciaux hors `^[a-zA-Z0-9_\-]+$`.
11. **Modification du prix d'une commande terminée (Terminal State Bypass)** : Mise à jour d'une commande au statut `delivered` ou `cancelled` par un client.
12. **Falsification d'horodatage (`createdAt` / `updatedAt`)** : Écriture d'un avis ou d'une adresse avec un timestamp client passé ou futur différent de `request.time`.

## 3. Rapport d'Audit Red Team (Synthèse)

- **Shadow Update Test** : PASS — `hasOnly()` sur toutes les créations et `affectedKeys().hasOnly()` combiné à `isValid[Entity](incoming())` sur chaque action de mise à jour.
- **Email Spoofing Test** : PASS — `request.auth.token.email_verified == true` exigé sur le bootstrap admin et sur toutes les opérations d'écriture.
- **PII Blanket Test** : PASS — Aucune lecture `if isSignedIn()` sur les collections contenant téléphones, emails, adresses ou portefeuilles.
- **Query Trust Test** : PASS — Chaque `allow list` sur des ressources privées vérifie explicitement `resource.data` sans aucun appel `get()` coûteux dans `list`.
