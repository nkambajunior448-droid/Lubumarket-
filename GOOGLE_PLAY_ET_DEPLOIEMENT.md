# Guide Complet : Déploiement Production, PWA Android & Publication Google Play Store (`com.lubumarket.app`)

> **Transparence d'ingénierie** : Conformément aux exigences du cahier des charges, aucun fichier `.aab` ou clé privée `.keystore` n'a été faussement prétendu signé ou publié à votre place, et aucun faux nom de domaine n'a été inventé. Tous les fichiers techniques requis (`manifest.webmanifest`, Service Worker, icônes haute résolution `512x512` standard & maskable, route serveur `/.well-known/assetlinks.json` et manifeste Bubblewrap `/android-twa/twa-manifest.json`) sont générés et prêts dans ce dépôt.

---

## 1. Compatibilité Trusted Web Activity (TWA) pour Lubumarket

Une **Trusted Web Activity (TWA)** est pleinement adaptée à **Lubumarket** car :
1. L'application respecte 100 % des critères **PWA installable** (HTTPS obligatoire, Web App Manifest complet en français, icônes PNG `192x192`, `512x512` `any` et `512x512` `maskable`, Service Worker Workbox avec mise en cache hors-ligne et navigation SPA).
2. Chrome Custom Tabs / TWA exécute l'interface plein écran **sans barre d'adresse** dès que le fichier `https://<VOTRE_DOMAINE_PRODUCTION>/.well-known/assetlinks.json` contient l'empreinte SHA-256 de signature de votre application Android.
3. Toutes les mises à jour commerciales (catalogue, prix USD/CDF, règles de retrait 5 %) sont immédiatement disponibles pour les utilisateurs Android sans attendre une nouvelle validation binaire, tout en conservant un vrai paquet Android (`com.lubumarket.app`) sur Google Play Store.

---

## 2. Préparation du Domaine de Production & Variables d'Environnement

Avant de générer le paquet `.aab`, déployez l'application sur votre hébergement HTTPS définitif (ex. Google Cloud Run avec domaine personnalisé) et renseignez les variables suivantes côté serveur :

```env
NODE_ENV="production"
PRODUCTION_DOMAIN="VOTRE_DOMAINE_PRODUCTION_HTTPS_A_RENSEIGNER"
ADMIN_BOOTSTRAP_EMAIL="nkambajunior448@gmail.com"
ANDROID_SHA256_CERT_FINGERPRINT="EMPREINTE_SHA256_GOOGLE_PLAY_APP_SIGNING"
```

### Domaines autorisés Firebase Authentication
Dans la console Firebase (`gen-lang-client-0555201904`) → **Authentication → Settings → Authorized domains**, ajoutez votre domaine de production exact afin que la connexion Google fonctionne sur Web et dans la TWA Android.

---

## 3. Génération et Signature de l'Android App Bundle (`.aab`) avec Bubblewrap CLI

### Étape 3.1 — Prérequis sur votre poste de build
- Node.js 20+
- JDK 17 (requis par Android Gradle Plugin)
- Android SDK Command-line Tools (ou laissez Bubblewrap les installer automatiquement)

```bash
npm install -g @bubblewrap/cli
```

### Étape 3.2 — Renseigner votre vrai domaine dans `android-twa/twa-manifest.json`
Ouvrez `/android-twa/twa-manifest.json` et remplacez les 4 occurrences de `VOTRE_DOMAINE_PRODUCTION_HTTPS_A_RENSEIGNER` par votre nom de domaine réel (sans `https://` pour le champ `host`, avec `https://` pour `webManifestUrl` et `iconUrl`).

### Étape 3.3 — Générer la clé de signature d'upload (Keystore)
Conservez ce fichier `.keystore` et ses mots de passe dans un coffre-fort hors du dépôt Git :

```bash
keytool -genkeypair \
  -v \
  -keystore lubumarket-upload-key.keystore \
  -alias lubumarket-upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### Étape 3.4 — Initialiser le projet Android et compiler le `.aab`
```bash
mkdir build-twa && cd build-twa
cp ../android-twa/twa-manifest.json ./twa-manifest.json
bubblewrap update
bubblewrap build
```
Cette commande produit :
- `app-release-bundle.aab` : **le fichier Android App Bundle officiel à téléverser dans Google Play Console**.
- `app-release-signed.apk` : fichier APK pour tester directement sur un téléphone Android à Lubumbashi.

### Alternative sans ligne de commande : PWABuilder
1. Déployez Lubumarket sur votre domaine HTTPS de production.
2. Ouvrez `https://www.pwabuilder.com` et saisissez l'URL de production.
3. Cliquez sur **Package for stores → Android → Google Play**.
4. Vérifiez Package ID = `com.lubumarket.app`, App Name = `Lubumarket`, Host = votre domaine, puis téléchargez l'archive contenant le `.aab` et l'outil de signature.

---

## 4. Configuration Digital Asset Links (`assetlinks.json`)

Pour supprimer la barre d'URL Chrome dans l'application Android téléchargée depuis Google Play :
1. Créez l'application `Lubumarket` (`com.lubumarket.app`) dans **Google Play Console**.
2. Activez **Play App Signing** et importez `app-release-bundle.aab` dans une piste de test interne.
3. Allez dans **Configuration → Intégrité de l'application (App integrity) → Certificat de signature de l'application** et copiez l'empreinte **SHA-256** générée par Google Play (format `AA:BB:CC:...`).
4. Définissez la variable d'environnement serveur `ANDROID_SHA256_CERT_FINGERPRINT` avec cette empreinte exacte et redéployez le serveur.
5. Vérifiez que `https://<VOTRE_DOMAINE>/.well-known/assetlinks.json` renvoie bien :
   ```json
   [
     {
       "relation": ["delegate_permission/common.handle_all_urls"],
       "target": {
         "namespace": "android_app",
         "package_name": "com.lubumarket.app",
         "sha256_cert_fingerprints": ["VOTRE_SHA256_GOOGLE_PLAY"]
       }
     }
   ]
   ```

### Gestion des futures mises à jour (`.aab`)
- Pour toute mise à jour de l'enveloppe Android (icône native, splash screen, permissions Android), incrémentez `appVersionCode` (`1` → `2`) et `appVersionName` (`"1.0.0"` → `"1.0.1"`) dans `twa-manifest.json`, exécutez `bubblewrap update && bubblewrap build`, et signez avec **exactement le même `lubumarket-upload-key.keystore`**.

---

## 5. Fiche Complète Prête pour Google Play Console (Français)

- **Nom de l'application (max 30 car.)** : `Lubumarket`
- **Identifiant du package** : `com.lubumarket.app`
- **Langue par défaut** : `Français (fr-FR / fr)`
- **Catégorie Google Play** : `Shopping` (Achats)
- **Tarification** : `Gratuite` (sans publicité tierce intrusive)

### Description courte (74 / 80 caractères max)
```text
Achetez en ligne à Lubumbashi et en RDC, payez en USD/CDF, livré chez vous
```

### Description complète (prête à coller — < 4 000 caractères)
```text
LUBUMARKET — « Achetez en ligne, recevez chez vous. »

Lubumarket est la marketplace multi-vendeurs conçue pour Lubumbashi (Haut-Katanga) et l'ensemble de la République démocratique du Congo (Kolwezi, Likasi, Kinshasa). Commandez auprès de dizaines de boutiques locales vérifiées et recevez vos marchandises directement à domicile en toute sécurité.

POUR LES CLIENTS ACHETEURS :
• Parcourez 12 catégories complètes : Téléphones et accessoires, Informatique, Vêtements et chaussures, Électronique, Alimentation, Maison et meubles, Beauté et cosmétiques, Matériaux de construction, Équipements professionnels, Automobile, Santé et hygiène, et Autres.
• Affichez et réglez vos achats en Dollars américains (USD) ou en Francs congolais (CDF) avec taux de change transparent.
• Ajoutez au panier les produits de plusieurs boutiques en une seule commande.
• Livraison sécurisée à domicile par code unique personnel à 6 chiffres : votre commande n'est marquée livrée que lorsque vous communiquez ce code au livreur lors de la remise physique.
• Évaluez les produits après réception confirmée.

POUR LES VENDEURS INDÉPENDANTS :
• Inscription et création de boutique 100 % gratuites.
• Publiez vos photos, stocks et prix en USD ou CDF.
• Règle claire : 0 % de commission à l'inscription et 0 % au moment de l'achat. Lubumarket prélève uniquement 5 % lorsque vous demandez un retrait depuis votre solde disponible vérifié.

POUR LES LIVREURS PARTENAIRES :
• Consultez vos courses attribuées par commune et zone de livraison.
• Récupérez les colis en boutique et validez la remise client par code sécurisé à usage unique.

SÉCURITÉ & PROTECTION DES DONNÉES :
• Authentification sécurisée et chiffrement HTTPS/TLS de bout en bout.
• Lubumarket ne demande jamais vos codes PIN Mobile Money ni vos mots de passe bancaires.
```

### Questionnaire « Sécurité des données » (Data Safety Google Play)
- **Chiffrement en transit** : Oui (HTTPS/TLS obligatoire sur toutes les requêtes Firebase et API).
- **Données collectées** :
  - Informations personnelles : Nom, Adresse e-mail, Numéro de téléphone, Adresse physique de livraison (Finalité : Fonctionnement de l'application, exécution des commandes et prévention de la fraude — Non vendues à des tiers).
  - Informations financières : Historique des achats et demandes de retrait vendeurs (Finalité : Comptabilité des commandes — Aucun numéro complet de carte bancaire ni code PIN Mobile Money collecté).
  - Photos : Uniquement lorsque le vendeur téléverse volontairement les photos de ses produits ou le logo de sa boutique.
- **Mécanisme de suppression de compte et des données** : Mentionné dans la Politique de Confidentialité intégrée à l'application (`Politique de confidentialité` accessible depuis le pied de page public sans connexion).

### Classification du contenu (IARC)
- Catégorie : **Application commerciale / Achats en ligne**.
- Violence, contenu sexuel, jeux d'argent, substances réglementées : **Non** → Classification attendue : **PEGI 3 / Tout public**.

---

## 6. Liste de Contrôle Finale avant Ouverture Commerciale Réelle (12 Étapes)

1. [ ] Connecter votre domaine HTTPS définitif sur le service d'hébergement Node.js / Cloud Run.
2. [ ] Ajouter ce domaine dans **Firebase Console → Authentication → Authorized domains**.
3. [ ] Vérifier les règles **Firebase Storage** pour limiter en écriture `/products/{uid}/*` et `/shops/{uid}/*` aux utilisateurs authentifiés (`request.resource.size < 4 * 1024 * 1024`).
4. [ ] Se connecter avec le compte propriétaire (`nkambajunior448@gmail.com`) et vérifier l'initialisation dans **Espace Administrateur**.
5. [ ] Ajuster le taux de change officiel **1 USD → CDF** du jour dans **Administration → Paramètres**.
6. [ ] Vérifier les tarifs de livraison en USD pour chaque commune active de Lubumbashi.
7. [ ] Signer les contrats marchands officiels (Vodacom M-Pesa, Airtel Money, Orange Money, Afrimoney, prestataire carte Visa/Mastercard) et renseigner leurs clés secrètes uniquement dans les variables d'environnement serveur avant de passer leurs adaptateurs du **Mode Test** au mode réel.
8. [ ] Mettre en place la procédure interne de caisse à Lubumbashi pour le **rapprochement quotidien des espèces encaissées à la livraison (COD)** avant validation dans l'Espace Administrateur.
9. [ ] Compiler `app-release-bundle.aab` via Bubblewrap CLI avec votre domaine de production.
10. [ ] Téléverser le `.aab` sur Google Play Console (piste de test interne) et récupérer l'empreinte SHA-256 Play App Signing.
11. [ ] Configurer `ANDROID_SHA256_CERT_FINGERPRINT` sur le serveur et vérifier `/.well-known/assetlinks.json`.
12. [ ] Tester un parcours complet sur téléphone Android réel (Commande Client → Préparation Vendeur → Livraison par Code à 6 chiffres → Retrait Vendeur avec calcul exact de la commission de 5 %) avant de promouvoir en Production sur Google Play Store.
