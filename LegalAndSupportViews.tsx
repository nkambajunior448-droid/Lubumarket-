import React from 'react';
import { HelpCircle, ShieldCheck, Smartphone, FileText, Lock } from 'lucide-react';
import { PWAInstallButton } from './PWAInstallWidgets';

interface LegalViewProps {
  section: 'support' | 'terms' | 'privacy';
  onNavigate: (view: string) => void;
}

export const LegalAndSupportViews: React.FC<LegalViewProps> = ({ section, onNavigate }) => {
  if (section === 'terms') {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 space-y-8">
        <div className="border-b border-slate-200 pb-6">
          <p className="text-xs font-medium text-slate-500">Cadre contractuel · Lubumbashi, Haut-Katanga, RDC</p>
          <h1 className="mt-2 text-2xl sm:text-3xl font-bold text-slate-900">
            Conditions Générales d’Utilisation et de Vente (CGU / CGV)
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Applicables aux clients, vendeurs indépendants et livreurs partenaires opérant sur Lubumarket.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-6 text-sm leading-relaxed text-slate-700">
          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">01. Rôle de la marketplace Lubumarket</h2>
            <p>
              Lubumarket (« Achetez en ligne, recevez chez vous. ») est une plateforme commerciale multi-vendeurs lancée à Lubumbashi (Haut-Katanga, République démocratique du Congo) et préparée pour l’ensemble des villes de la RDC. Elle met en relation des vendeurs indépendants validés, des acheteurs particuliers ou professionnels et des livreurs partenaires.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">02. Gratuité d’inscription et commission unique de 5 % au retrait</h2>
            <p>
              L’ouverture d’une boutique vendeur et la publication de produits sur Lubumarket sont entièrement gratuites. Aucune commission n’est prélevée au moment de la commande ni sur une commande annulée ou non livrée.
            </p>
            <p>
              Lubumarket applique exclusivement une commission de <strong>5 %</strong> calculée côté serveur sur le montant brut demandé par le vendeur lors d’une demande de retrait (exemple : pour 100 USD demandés, 5 USD de commission Lubumarket et 95 USD nets pour le vendeur ; pour 20 USD demandés, 1 USD de commission et 19 USD nets).
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">03. Registre comptable interne et disponibilité des soldes</h2>
            <p>
              Dans cette version de la plateforme, le portefeuille vendeur constitue un <strong>registre comptable interne</strong> traçant les créances commerciales et non un établissement bancaire ou de monnaie électronique. Les montants des ventes ne passent du solde en attente au solde disponible qu’après double vérification : (1) confirmation de remise au client par code unique et (2) confirmation effective du paiement (ou rapprochement administratif pour le paiement à la livraison).
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">04. Commandes multi-vendeurs, livraison et code unique</h2>
            <p>
              Lorsqu’un panier contient des articles de plusieurs boutiques, Lubumarket génère automatiquement des sous-commandes distinctes par vendeur tout en maintenant un suivi global pour le client. Chaque livraison est protégée par un code numérique secret à 6 chiffres à usage unique que le client communique au livreur uniquement lors de la réception physique du colis.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">05. Devises USD et CDF</h2>
            <p>
              Les prix peuvent être affichés et réglés en dollars américains (USD) ou en francs congolais (CDF) selon le taux de référence configuré et horodaté par l’administration Lubumarket. La devise et le taux applicables sont figés au moment de la validation de chaque commande.
            </p>
          </section>
        </div>
      </div>
    );
  }

  if (section === 'privacy') {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 space-y-8">
        <div className="border-b border-slate-200 pb-6">
          <p className="text-xs font-medium text-slate-500">Conformité Google Play Store (com.lubumarket.app) & RGPD / RDC</p>
          <h1 className="mt-2 text-2xl sm:text-3xl font-bold text-slate-900">
            Politique de Confidentialité et Sécurité des Données
          </h1>
          <p className="mt-2 text-sm text-slate-600">
            Engagement strict de protection des données personnelles des clients, vendeurs et livreurs.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-6 text-sm leading-relaxed text-slate-700">
          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">01. Données collectées et finalités</h2>
            <p>
              Lubumarket collecte uniquement les informations strictement nécessaires à l’exécution des commandes et livraisons : nom, adresse email authentifiée, numéro de téléphone de contact, adresses de livraison à Lubumbashi / RDC, informations publiques des boutiques vendeurs et historique des commandes.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">02. Sécurité bancaire et Mobile Money</h2>
            <p>
              <strong>Lubumarket ne demande jamais votre code PIN Mobile Money (M-Pesa, Airtel Money, Orange Money, Afrimoney), ni vos mots de passe bancaires, et ne stocke jamais les numéros complets de cartes bancaires Visa ou Mastercard.</strong> Les paiements électroniques transitent uniquement par des adaptateurs de prestataires agréés sur connexion chiffrée HTTPS/TLS.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">03. Cloisonnement strict des accès (Zero-Trust)</h2>
            <p>
              Les règles de sécurité Cloud Firestore et les contrôles serveur garantissent qu’un client ne peut pas accéder aux données privées d’un vendeur, qu’un vendeur ne peut jamais consulter le portefeuille d’un autre vendeur, et qu’un livreur accède exclusivement aux courses qui lui sont attribuées (sans jamais voir le code secret client en clair).
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-base font-bold text-slate-900">04. Droits d’accès, rectification et suppression (Google Play Data Safety)</h2>
            <p>
              Conformément aux exigences Google Play Store pour l’application <span className="font-mono text-slate-900">com.lubumarket.app</span>, tout utilisateur peut modifier ses coordonnées depuis son profil ou demander la clôture et la suppression de ses données personnelles via le service d’assistance Lubumarket, sous réserve des obligations légales de conservation comptable.
            </p>
          </section>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto py-8 px-4 sm:px-6 space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-slate-200 pb-6">
        <div>
          <p className="text-xs font-medium text-slate-500">Lubumbashi · Haut-Katanga · RDC</p>
          <h1 className="mt-1 text-2xl sm:text-3xl font-bold text-slate-900">
            Centre d’Assistance & Application Android
          </h1>
          <p className="mt-2 text-sm text-slate-600 max-w-2xl">
            Tout savoir sur les achats, la livraison sécurisée par code unique, l’ouverture gratuite d’une boutique vendeur et l’installation sur Android.
          </p>
        </div>
        <PWAInstallButton />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
          <div className="w-10 h-10 rounded-xl bg-blue-50 text-[#0F3D8A] flex items-center justify-center">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <h2 className="text-base font-bold text-slate-900">Livraison protégée par code unique</h2>
          <p className="text-sm text-slate-600 leading-relaxed">
            À chaque commande, Lubumarket génère un code personnel à 6 chiffres visible uniquement dans votre espace <strong>Suivi commande</strong>. Communiquez-le au livreur uniquement lorsque vous recevez physiquement votre marchandise.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
          <div className="w-10 h-10 rounded-xl bg-orange-50 text-orange-600 flex items-center justify-center">
            <Lock className="w-5 h-5" />
          </div>
          <h2 className="text-base font-bold text-slate-900">Vendeurs : 0 $ à l’inscription, 5 % au retrait</h2>
          <p className="text-sm text-slate-600 leading-relaxed">
            Publiez vos produits gratuitement en USD ou CDF. Lubumarket ne prélève aucune commission lors de l’achat : la commission de 5 % s’applique uniquement sur le montant que vous demandez en retrait depuis votre solde disponible.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
          <div className="w-10 h-10 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center">
            <Smartphone className="w-5 h-5" />
          </div>
          <h2 className="text-base font-bold text-slate-900">Installation directe sur Android & Google Play</h2>
          <p className="text-sm text-slate-600 leading-relaxed">
            Lubumarket fonctionne dès maintenant comme application PWA installable depuis Google Chrome sur Android, et intègre l’architecture Trusted Web Activity (<span className="font-mono text-xs">com.lubumarket.app</span>) prête pour Google Play Store.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
          <div className="w-10 h-10 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center">
            <HelpCircle className="w-5 h-5" />
          </div>
          <h2 className="text-base font-bold text-slate-900">Service Client Lubumbashi & Expansion RDC</h2>
          <p className="text-sm text-slate-600 leading-relaxed">
            Zones actives à Lubumbashi (Centre-ville, Golf, Kampemba, Bel-Air, Kenya, Kamalondo, Katuba, Ruashi) avec expansion configurée pour Kolwezi, Likasi et Kinshasa.
          </p>
          <div className="pt-2 flex flex-wrap gap-4 text-xs font-semibold text-[#0F3D8A]">
            <button type="button" onClick={() => onNavigate('terms')} className="hover:underline inline-flex items-center gap-1">
              <FileText className="w-3.5 h-3.5" /> Conditions générales
            </button>
            <button type="button" onClick={() => onNavigate('privacy')} className="hover:underline inline-flex items-center gap-1">
              <Lock className="w-3.5 h-3.5" /> Politique de confidentialité
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
