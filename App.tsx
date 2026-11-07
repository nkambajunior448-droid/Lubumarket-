/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { Component, useState } from 'react';
import {
  Bell,
  Home,
  LogOut,
  Search,
  ShoppingBag,
  Package,
  User,
  X,
  AlertTriangle,
} from 'lucide-react';
import {
  LubumarketProvider,
  formatFsTimestamp,
  useLubumarket,
} from './context/LubumarketContext';
import { CustomerSpace, type CustomerSubView } from './components/CustomerSpace';
import { SellerSpace } from './components/SellerSpace';
import { CourierSpace } from './components/CourierSpace';
import { AdminSpace } from './components/AdminSpace';
import { LegalAndSupportViews } from './components/LegalAndSupportViews';
import { OfflineIndicator, PWAInstallButton } from './components/PWAInstallWidgets';

type ActiveWorkspace =
  | 'customer'
  | 'seller'
  | 'courier'
  | 'admin'
  | 'support'
  | 'terms'
  | 'privacy';

interface ErrorBoundaryState {
  hasError: boolean;
  message: string;
}

class LubumarketErrorBoundary extends Component<
  { children: React.ReactNode },
  ErrorBoundaryState
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    let friendly = error.message || 'Une erreur inattendue est survenue.';
    try {
      const parsed = JSON.parse(error.message);
      if (parsed && parsed.operationType) {
        friendly = `Accès refusé ou opération invalide sur ${parsed.path || 'la base de données'} (${parsed.operationType}). Détail : ${parsed.error}`;
      }
    } catch {
      // message texte standard
    }
    return { hasError: true, message: friendly };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
          <div className="max-w-lg w-full bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-4">
            <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h1 className="text-lg font-bold text-slate-900">
              Protection des données Lubumarket
            </h1>
            <p className="text-sm text-slate-600 leading-relaxed">{this.state.message}</p>
            <button
              type="button"
              onClick={() => this.setState({ hasError: false, message: '' })}
              className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-xs font-bold text-white min-h-[42px]"
            >
              Reprendre la navigation
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

const LubumarketShell: React.FC = () => {
  const {
    firebaseUser,
    isAdmin,
    displayCurrency,
    setDisplayCurrency,
    usdToCdf,
    cart,
    notifications,
    markNotificationRead,
    signInAndEnsureCustomer,
    signOutApp,
    toasts,
    dismissToast,
  } = useLubumarket();

  const [workspace, setWorkspace] = useState<ActiveWorkspace>('customer');
  const [customerSubView, setCustomerSubView] = useState<CustomerSubView>('home');
  const [notifDrawerOpen, setNotifDrawerOpen] = useState(false);

  const cartCount = cart.reduce((sum, i) => sum + i.quantity, 0);
  const unreadNotifs = notifications.filter((n) => !n.isRead).length;

  const goToCustomerView = (v: CustomerSubView) => {
    setWorkspace('customer');
    setCustomerSubView(v);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#F8FAFC] text-slate-900 pb-16 md:pb-0">
      {/* =====================================================================
          BARRE SUPÉRIEURE PRINCIPALE (3 ZONES STRICTES : MARQUE / LIENS / CTA)
          ===================================================================== */}
      <header className="sticky top-0 z-40 h-16 bg-white border-b border-slate-200/90">
        <div className="max-w-7xl mx-auto h-full px-4 sm:px-6 flex items-center justify-between gap-8">
          <button
            type="button"
            onClick={() => goToCustomerView('home')}
            className="text-xl font-extrabold tracking-tight text-[#0F3D8A] whitespace-nowrap shrink-0 min-h-[44px] flex items-center"
          >
            LUBU<span className="text-orange-600">MARKET</span>
          </button>

          <nav className="hidden md:flex items-center gap-6 text-sm font-semibold text-slate-600">
            <button
              type="button"
              onClick={() => goToCustomerView('home')}
              className={`whitespace-nowrap hover:text-[#0F3D8A] transition-colors ${
                workspace === 'customer' && customerSubView === 'home' ? 'text-[#0F3D8A]' : ''
              }`}
            >
              Accueil
            </button>
            <button
              type="button"
              onClick={() => goToCustomerView('catalog')}
              className={`whitespace-nowrap hover:text-[#0F3D8A] transition-colors ${
                workspace === 'customer' && customerSubView === 'catalog' ? 'text-[#0F3D8A]' : ''
              }`}
            >
              Catalogue
            </button>
            <button
              type="button"
              onClick={() => goToCustomerView('shops')}
              className={`whitespace-nowrap hover:text-[#0F3D8A] transition-colors ${
                workspace === 'customer' && customerSubView === 'shops' ? 'text-[#0F3D8A]' : ''
              }`}
            >
              Boutiques
            </button>
            <button
              type="button"
              onClick={() => goToCustomerView('orders')}
              className={`whitespace-nowrap hover:text-[#0F3D8A] transition-colors ${
                workspace === 'customer' && customerSubView === 'orders' ? 'text-[#0F3D8A]' : ''
              }`}
            >
              Suivi commandes
            </button>
            <button
              type="button"
              onClick={() => setWorkspace('support')}
              className={`whitespace-nowrap hover:text-[#0F3D8A] transition-colors ${
                workspace === 'support' ? 'text-[#0F3D8A]' : ''
              }`}
            >
              Assistance
            </button>
          </nav>

          <button
            type="button"
            onClick={() => goToCustomerView('cart')}
            className="inline-flex items-center gap-2 rounded-xl bg-orange-600 px-4 py-2 text-xs sm:text-sm font-bold text-white hover:bg-orange-700 transition-colors whitespace-nowrap shrink-0 min-h-[42px]"
          >
            <ShoppingBag className="w-4 h-4 shrink-0" />
            <span>Panier ({cartCount})</span>
          </button>
        </div>
      </header>

      {/* =====================================================================
          SOUS-BARRE D'ESPACES RÔLES, DEVISE USD/CDF, PWA ANDROID & COMPTE
          ===================================================================== */}
      <div className="bg-slate-100/90 border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-2 flex flex-wrap items-center justify-between gap-3">
          {/* Sélecteur des 4 espaces strictement cloisonnés */}
          <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
            <button
              type="button"
              onClick={() => setWorkspace('customer')}
              className={`rounded-lg px-3 py-1.5 text-xs font-bold whitespace-nowrap min-h-[36px] ${
                workspace === 'customer'
                  ? 'bg-[#0F3D8A] text-white'
                  : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
              }`}
            >
              1. Client
            </button>
            <button
              type="button"
              onClick={() => setWorkspace('seller')}
              className={`rounded-lg px-3 py-1.5 text-xs font-bold whitespace-nowrap min-h-[36px] ${
                workspace === 'seller'
                  ? 'bg-[#0F3D8A] text-white'
                  : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
              }`}
            >
              2. Vendeur
            </button>
            <button
              type="button"
              onClick={() => setWorkspace('courier')}
              className={`rounded-lg px-3 py-1.5 text-xs font-bold whitespace-nowrap min-h-[36px] ${
                workspace === 'courier'
                  ? 'bg-[#0F3D8A] text-white'
                  : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
              }`}
            >
              3. Livreur
            </button>
            {isAdmin && (
              <button
                type="button"
                onClick={() => setWorkspace('admin')}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold whitespace-nowrap min-h-[36px] ${
                  workspace === 'admin'
                    ? 'bg-slate-900 text-white'
                    : 'bg-white text-slate-900 border border-slate-300 hover:bg-slate-50'
                }`}
              >
                4. Administrateur
              </button>
            )}
          </div>

          {/* Contrôles Devises USD/CDF + Notifications + PWA + Authentification */}
          <div className="flex items-center gap-2 ml-auto">
            <div className="inline-flex rounded-lg border border-slate-200 bg-white overflow-hidden text-xs font-bold">
              <button
                type="button"
                onClick={() => setDisplayCurrency('USD')}
                className={`px-2.5 py-1.5 min-h-[36px] ${
                  displayCurrency === 'USD' ? 'bg-[#0F3D8A] text-white' : 'text-slate-700'
                }`}
              >
                USD
              </button>
              <button
                type="button"
                onClick={() => setDisplayCurrency('CDF')}
                className={`px-2.5 py-1.5 min-h-[36px] ${
                  displayCurrency === 'CDF' ? 'bg-[#0F3D8A] text-white' : 'text-slate-700'
                }`}
              >
                CDF
              </button>
            </div>

            <span className="hidden lg:inline text-[11px] font-mono tabular-nums text-slate-500">
              1$ = {usdToCdf.toLocaleString('fr-CD')} FC
            </span>

            <div className="hidden sm:block">
              <PWAInstallButton />
            </div>

            {firebaseUser ? (
              <>
                <button
                  type="button"
                  onClick={() => setNotifDrawerOpen(true)}
                  className="relative inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 min-h-[36px]"
                  aria-label="Notifications"
                >
                  <Bell className="w-3.5 h-3.5 text-[#0F3D8A]" />
                  <span className="font-mono">{unreadNotifs}</span>
                </button>

                <button
                  type="button"
                  onClick={() => goToCustomerView('profile')}
                  className="hidden sm:inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 max-w-[140px] truncate min-h-[36px]"
                >
                  <User className="w-3.5 h-3.5 shrink-0 text-[#0F3D8A]" />
                  <span className="truncate">
                    {firebaseUser.displayName || firebaseUser.email?.split('@')[0]}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={signOutApp}
                  className="inline-flex items-center justify-center rounded-lg border border-slate-200 bg-white p-2 text-slate-600 hover:text-red-600 min-h-[36px] min-w-[36px]"
                  title="Se déconnecter"
                  aria-label="Se déconnecter"
                >
                  <LogOut className="w-3.5 h-3.5" />
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={signInAndEnsureCustomer}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-bold text-white hover:bg-slate-800 whitespace-nowrap min-h-[36px]"
              >
                Connexion / Inscription
              </button>
            )}
          </div>
        </div>
      </div>

      {/* =====================================================================
          CONTENU PRINCIPAL SELON L'ESPACE ACTIF
          ===================================================================== */}
      <main className="flex-1">
        {workspace === 'customer' && (
          <CustomerSpace
            subView={customerSubView}
            onChangeSubView={setCustomerSubView}
            onOpenWorkspace={(ws) => setWorkspace(ws)}
          />
        )}
        {workspace === 'seller' && <SellerSpace />}
        {workspace === 'courier' && <CourierSpace />}
        {workspace === 'admin' && <AdminSpace />}
        {(workspace === 'support' || workspace === 'terms' || workspace === 'privacy') && (
          <LegalAndSupportViews
            section={workspace}
            onNavigate={(v) => setWorkspace(v as ActiveWorkspace)}
          />
        )}
      </main>

      {/* =====================================================================
          PIED DE PAGE COMMERCIAL & CONFORMITÉ GOOGLE PLAY STORE
          ===================================================================== */}
      <footer className="bg-white border-t border-slate-200 mt-12">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10 grid grid-cols-1 md:grid-cols-4 gap-8 text-xs text-slate-600">
          <div className="space-y-2">
            <p className="text-base font-extrabold text-[#0F3D8A]">
              LUBU<span className="text-orange-600">MARKET</span>
            </p>
            <p className="font-semibold text-slate-800">« Achetez en ligne, recevez chez vous. »</p>
            <p className="leading-relaxed">
              Marketplace multi-vendeurs lancée à Lubumbashi (Haut-Katanga, RDC) et conçue pour Kolwezi, Likasi, Kinshasa et toute la RDC.
            </p>
          </div>

          <div className="space-y-2">
            <p className="font-bold text-slate-900 uppercase">Acheteurs</p>
            <ul className="space-y-1.5">
              <li>
                <button type="button" onClick={() => goToCustomerView('catalog')} className="hover:underline">
                  Catalogue des 12 catégories
                </button>
              </li>
              <li>
                <button type="button" onClick={() => goToCustomerView('shops')} className="hover:underline">
                  Boutiques partenaires
                </button>
              </li>
              <li>
                <button type="button" onClick={() => goToCustomerView('favorites')} className="hover:underline">
                  Mes favoris
                </button>
              </li>
              <li>
                <button type="button" onClick={() => goToCustomerView('orders')} className="hover:underline">
                  Suivi & Codes de livraison
                </button>
              </li>
            </ul>
          </div>

          <div className="space-y-2">
            <p className="font-bold text-slate-900 uppercase">Partenaires RDC</p>
            <ul className="space-y-1.5">
              <li>
                <button type="button" onClick={() => setWorkspace('seller')} className="hover:underline">
                  Ouvrir une boutique gratuite (5 % au retrait)
                </button>
              </li>
              <li>
                <button type="button" onClick={() => setWorkspace('courier')} className="hover:underline">
                  Devenir livreur à Lubumbashi
                </button>
              </li>
              <li>
                <button type="button" onClick={() => setWorkspace('admin')} className="hover:underline">
                  Administration propriétaire
                </button>
              </li>
            </ul>
          </div>

          <div className="space-y-2">
            <p className="font-bold text-slate-900 uppercase">Légal & Google Play</p>
            <ul className="space-y-1.5">
              <li>
                <button type="button" onClick={() => setWorkspace('support')} className="hover:underline">
                  Centre d’assistance & Application Android
                </button>
              </li>
              <li>
                <button type="button" onClick={() => setWorkspace('terms')} className="hover:underline">
                  Conditions générales (CGU / CGV)
                </button>
              </li>
              <li>
                <button type="button" onClick={() => setWorkspace('privacy')} className="hover:underline">
                  Politique de confidentialité
                </button>
              </li>
              <li className="pt-1 font-mono text-[11px] text-slate-400">
                Android ID : com.lubumarket.app
              </li>
            </ul>
          </div>
        </div>
      </footer>

      {/* =====================================================================
          NAVIGATION MOBILE ANDROID FIXE EN BAS (UNIQUEMENT SUR MOBILE)
          ===================================================================== */}
      <nav
        aria-label="Navigation mobile principale"
        className="md:hidden fixed bottom-0 inset-x-0 z-40 h-14 bg-white border-t border-slate-200 grid grid-cols-5"
      >
        <button
          type="button"
          onClick={() => goToCustomerView('home')}
          className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-semibold ${
            workspace === 'customer' && customerSubView === 'home' ? 'text-[#0F3D8A]' : 'text-slate-500'
          }`}
        >
          <Home className="w-4 h-4" />
          <span>Accueil</span>
        </button>
        <button
          type="button"
          onClick={() => goToCustomerView('catalog')}
          className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-semibold ${
            workspace === 'customer' && customerSubView === 'catalog' ? 'text-[#0F3D8A]' : 'text-slate-500'
          }`}
        >
          <Search className="w-4 h-4" />
          <span>Catalogue</span>
        </button>
        <button
          type="button"
          onClick={() => goToCustomerView('cart')}
          className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-semibold ${
            workspace === 'customer' && customerSubView === 'cart' ? 'text-orange-600' : 'text-slate-500'
          }`}
        >
          <ShoppingBag className="w-4 h-4" />
          <span>Panier ({cartCount})</span>
        </button>
        <button
          type="button"
          onClick={() => goToCustomerView('orders')}
          className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-semibold ${
            workspace === 'customer' && customerSubView === 'orders' ? 'text-[#0F3D8A]' : 'text-slate-500'
          }`}
        >
          <Package className="w-4 h-4" />
          <span>Suivi</span>
        </button>
        <button
          type="button"
          onClick={() => goToCustomerView('profile')}
          className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-semibold ${
            workspace === 'customer' && customerSubView === 'profile' ? 'text-[#0F3D8A]' : 'text-slate-500'
          }`}
        >
          <User className="w-4 h-4" />
          <span>Compte</span>
        </button>
      </nav>

      {/* =====================================================================
          TIROIR DE NOTIFICATIONS TEMPS RÉEL
          ===================================================================== */}
      {notifDrawerOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 flex justify-end">
          <div className="w-full max-w-md bg-white h-full shadow-2xl flex flex-col">
            <div className="p-4 border-b border-slate-200 flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-slate-900">Notifications Lubumarket</h2>
                <p className="text-xs text-slate-500">Alertes commandes, livraisons et retraits</p>
              </div>
              <button
                type="button"
                onClick={() => setNotifDrawerOpen(false)}
                className="p-2 rounded-lg text-slate-400 hover:text-slate-700 min-h-[40px] min-w-[40px] flex items-center justify-center"
                aria-label="Fermer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto divide-y divide-slate-100 p-4">
              {notifications.length === 0 ? (
                <p className="text-sm text-slate-500 text-center py-10">
                  Aucune notification pour le moment.
                </p>
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.id}
                    onClick={() => !n.isRead && markNotificationRead(n.id)}
                    className={`py-3.5 px-3 rounded-xl cursor-pointer space-y-1 ${
                      n.isRead ? 'opacity-70' : 'bg-blue-50/50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2 text-xs">
                      <span className="font-bold text-slate-900">{n.title}</span>
                      {!n.isRead && (
                        <span className="text-[10px] font-bold text-[#0F3D8A] uppercase">Nouveau</span>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 leading-relaxed">{n.body}</p>
                    <p className="text-[11px] text-slate-400">{formatFsTimestamp(n.createdAt)}</p>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          ALERTES TOASTS NON-BLOQUANTES (ZÉRO WINDOW.ALERT)
          ===================================================================== */}
      <div className="fixed bottom-16 md:bottom-5 right-4 z-50 flex flex-col gap-2 max-w-sm w-[calc(100vw-2rem)] pointer-events-none">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`pointer-events-auto rounded-xl px-4 py-3 text-xs font-semibold shadow-lg border flex items-start justify-between gap-3 ${
              t.type === 'error'
                ? 'bg-red-950 text-white border-red-800'
                : t.type === 'success'
                  ? 'bg-slate-900 text-white border-slate-800'
                  : 'bg-white text-slate-900 border-slate-200'
            }`}
          >
            <span className="leading-relaxed">{t.text}</span>
            <button
              type="button"
              onClick={() => dismissToast(t.id)}
              className="opacity-70 hover:opacity-100 shrink-0"
              aria-label="Fermer le message"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>

      <OfflineIndicator />
    </div>
  );
};

export default function App() {
  return (
    <LubumarketErrorBoundary>
      <LubumarketProvider>
        <LubumarketShell />
      </LubumarketProvider>
    </LubumarketErrorBoundary>
  );
}
