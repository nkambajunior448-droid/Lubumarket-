import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, type User as FirebaseUser } from 'firebase/auth';
import {
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';
import {
  INITIAL_CATEGORIES,
  INITIAL_DELIVERY_ZONES,
  INITIAL_PAYMENT_CONFIGS,
  convertAmount,
  type CurrencyCode,
  type UserRole,
} from '../shared/domain';
import {
  OperationType,
  auth,
  db,
  handleFirestoreError,
  loginWithGoogle,
  logoutUser,
  testFirestoreConnection,
} from '../lib/firebase';

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info';
  text: string;
}

export interface CartItem {
  productId: string;
  quantity: number;
}

interface LubumarketContextValue {
  firebaseUser: FirebaseUser | null;
  authReady: boolean;
  userDoc: Record<string, any> | null;
  customerDoc: Record<string, any> | null;
  sellerDoc: Record<string, any> | null;
  courierDoc: Record<string, any> | null;
  isAdmin: boolean;
  isBootstrapEligible: boolean;
  displayCurrency: CurrencyCode;
  setDisplayCurrency: (c: CurrencyCode) => void;
  usdToCdf: number;
  exchangeRateUpdatedAt: string | null;
  formatPrice: (amount: number, sourceCurrency?: CurrencyCode) => string;
  categories: Array<Record<string, any>>;
  products: Array<Record<string, any>>;
  shops: Array<Record<string, any>>;
  deliveryZones: Array<Record<string, any>>;
  paymentConfigs: Array<Record<string, any>>;
  notifications: Array<Record<string, any>>;
  cart: CartItem[];
  addToCart: (productId: string, qty?: number) => void;
  updateCartQty: (productId: string, qty: number) => void;
  clearCart: () => void;
  toggleFavorite: (productId: string) => Promise<void>;
  signInAndEnsureCustomer: () => Promise<void>;
  signOutApp: () => Promise<void>;
  markNotificationRead: (notifId: string) => Promise<void>;
  notify: (text: string, type?: 'success' | 'error' | 'info') => void;
  toasts: ToastMessage[];
  dismissToast: (id: string) => void;
}

const LubumarketContext = createContext<LubumarketContextValue | null>(null);

export function formatFsTimestamp(val: any): string | null {
  if (!val) return null;
  if (typeof val.toDate === 'function') {
    return val.toDate().toLocaleString('fr-CD', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
  if (val.__fsTimestamp) {
    return new Date(val.__fsTimestamp).toLocaleString('fr-CD', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
  return null;
}

const OWNER_EMAIL = 'nkambajunior448@gmail.com';

export const LubumarketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [authReady, setAuthReady] = useState(false);

  const [userDoc, setUserDoc] = useState<Record<string, any> | null>(null);
  const [customerDoc, setCustomerDoc] = useState<Record<string, any> | null>(null);
  const [sellerDoc, setSellerDoc] = useState<Record<string, any> | null>(null);
  const [courierDoc, setCourierDoc] = useState<Record<string, any> | null>(null);
  const [adminDocExists, setAdminDocExists] = useState(false);

  const [displayCurrency, setDisplayCurrency] = useState<CurrencyCode>('USD');
  const [usdToCdf, setUsdToCdf] = useState<number>(2850);
  const [exchangeRateUpdatedAt, setExchangeRateUpdatedAt] = useState<string | null>(null);

  const [categoriesDb, setCategoriesDb] = useState<Array<Record<string, any>>>([]);
  const [products, setProducts] = useState<Array<Record<string, any>>>([]);
  const [shops, setShops] = useState<Array<Record<string, any>>>([]);
  const [zonesDb, setZonesDb] = useState<Array<Record<string, any>>>([]);
  const [paymentsDb, setPaymentsDb] = useState<Array<Record<string, any>>>([]);
  const [notifications, setNotifications] = useState<Array<Record<string, any>>>([]);

  const [cart, setCart] = useState<CartItem[]>(() => {
    try {
      const saved = localStorage.getItem('lubumarket_cart');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const notify = (text: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    setToasts((prev) => [...prev.slice(-3), { id, type, text }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  };

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  useEffect(() => {
    testFirestoreConnection();
    const unsub = onAuthStateChanged(auth, (u) => {
      setFirebaseUser(u);
      setAuthReady(true);
    });
    return () => unsub();
  }, []);

  // Flux temps réel du catalogue public (catégories actives, produits actifs, boutiques actives, zones actives, taux USD/CDF)
  useEffect(() => {
    const qCats = query(collection(db, 'categories'), where('isActive', '==', true));
    const unsubCats = onSnapshot(
      qCats,
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
        setCategoriesDb(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'categories')
    );

    const qProds = query(collection(db, 'products'), where('status', '==', 'active'));
    const unsubProds = onSnapshot(
      qProds,
      (snap) => {
        setProducts(snap.docs.map((d) => d.data()));
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'products')
    );

    const qShops = query(collection(db, 'shops'), where('status', '==', 'active'));
    const unsubShops = onSnapshot(
      qShops,
      (snap) => {
        setShops(snap.docs.map((d) => d.data()));
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'shops')
    );

    const qZones = query(collection(db, 'deliveryZones'), where('isActive', '==', true));
    const unsubZones = onSnapshot(
      qZones,
      (snap) => {
        setZonesDb(snap.docs.map((d) => d.data()));
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'deliveryZones')
    );

    const unsubRate = onSnapshot(
      doc(db, 'exchangeRates', 'current'),
      (snap) => {
        if (snap.exists()) {
          const data = snap.data();
          setUsdToCdf(Number(data.usdToCdf || 2850));
          setExchangeRateUpdatedAt(formatFsTimestamp(data.updatedAt));
        }
      },
      (err) => handleFirestoreError(err, OperationType.GET, 'exchangeRates/current')
    );

    const qPayments = query(collection(db, 'paymentConfigs'), where('isEnabled', '==', true));
    const unsubPayments = onSnapshot(
      qPayments,
      (snap) => {
        setPaymentsDb(snap.docs.map((d) => d.data()));
      },
      () => {
        // Ignorer silencieusement si pas encore initialisé
      }
    );

    return () => {
      unsubCats();
      unsubProds();
      unsubShops();
      unsubZones();
      unsubRate();
      unsubPayments();
    };
  }, []);

  // Flux temps réel des profils privés de l'utilisateur authentifié
  useEffect(() => {
    if (!authReady || !firebaseUser) {
      setUserDoc(null);
      setCustomerDoc(null);
      setSellerDoc(null);
      setCourierDoc(null);
      setAdminDocExists(false);
      setNotifications([]);
      return;
    }

    const uid = firebaseUser.uid;

    const unsubUser = onSnapshot(
      doc(db, 'users', uid),
      (snap) => setUserDoc(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `users/${uid}`)
    );

    const unsubCustomer = onSnapshot(
      doc(db, 'customers', uid),
      (snap) => setCustomerDoc(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `customers/${uid}`)
    );

    const unsubSeller = onSnapshot(
      doc(db, 'sellers', uid),
      (snap) => setSellerDoc(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `sellers/${uid}`)
    );

    const unsubCourier = onSnapshot(
      doc(db, 'couriers', uid),
      (snap) => setCourierDoc(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `couriers/${uid}`)
    );

    const unsubAdmin = onSnapshot(
      doc(db, 'admins', uid),
      (snap) => setAdminDocExists(snap.exists()),
      () => setAdminDocExists(false)
    );

    const qNotifs = query(collection(db, 'notifications'), where('recipientId', '==', uid));
    const unsubNotifs = onSnapshot(
      qNotifs,
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => {
            const ta = a.createdAt?.seconds || 0;
            const tb = b.createdAt?.seconds || 0;
            return tb - ta;
          });
        setNotifications(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'notifications')
    );

    return () => {
      unsubUser();
      unsubCustomer();
      unsubSeller();
      unsubCourier();
      unsubAdmin();
      unsubNotifs();
    };
  }, [authReady, firebaseUser]);

  // Sauvegarde locale + Firestore du panier
  useEffect(() => {
    try {
      localStorage.setItem('lubumarket_cart', JSON.stringify(cart));
    } catch {
      // ignore storage quota
    }
  }, [cart]);

  const syncCartToFirestore = async (nextCart: CartItem[]) => {
    if (!firebaseUser) return;
    try {
      await setDoc(doc(db, 'carts', firebaseUser.uid), {
        userId: firebaseUser.uid,
        itemsJson: nextCart.slice(0, 40),
        updatedAt: serverTimestamp(),
      });
    } catch {
      // non-bloquant
    }
  };

  const addToCart = (productId: string, qty = 1) => {
    const prod = products.find((p) => p.id === productId);
    if (prod && Number(prod.stock) < 1) {
      notify('Ce produit est momentanément en rupture de stock.', 'error');
      return;
    }
    setCart((prev) => {
      const existing = prev.find((i) => i.productId === productId);
      const maxStock = prod ? Number(prod.stock) : 99;
      let next: CartItem[];
      if (existing) {
        const newQty = Math.min(maxStock, existing.quantity + qty);
        next = prev.map((i) => (i.productId === productId ? { ...i, quantity: newQty } : i));
      } else {
        next = [...prev, { productId, quantity: Math.min(maxStock, qty) }];
      }
      syncCartToFirestore(next);
      return next;
    });
    notify('Article ajouté à votre panier.', 'success');
  };

  const updateCartQty = (productId: string, qty: number) => {
    setCart((prev) => {
      const next =
        qty <= 0
          ? prev.filter((i) => i.productId !== productId)
          : prev.map((i) => (i.productId === productId ? { ...i, quantity: qty } : i));
      syncCartToFirestore(next);
      return next;
    });
  };

  const clearCart = () => {
    setCart([]);
    syncCartToFirestore([]);
  };

  const signInAndEnsureCustomer = async () => {
    try {
      const u = await loginWithGoogle();
      const uid = u.uid;
      const email = (u.email || '').toLowerCase();
      const displayName = (u.displayName || email.split('@')[0] || 'Client Lubumarket').slice(0, 110);

      // Si aucun profil utilisateur n'existe encore, créer un profil Client par défaut (jamais Admin en public !)
      if (!userDoc) {
        try {
          const initialRole: UserRole = 'customer';
          await setDoc(
            doc(db, 'users', uid),
            {
              uid,
              displayName,
              email,
              phone: '',
              role: initialRole,
              status: 'active',
              city: 'Lubumbashi',
              createdAt: serverTimestamp(),
              updatedAt: serverTimestamp(),
            },
            { merge: false }
          ).catch(() => {
            // Document existe déjà
          });

          await setDoc(
            doc(db, 'customers', uid),
            {
              uid,
              fullName: displayName,
              phone: '',
              email,
              city: 'Lubumbashi',
              favoriteProductIds: [],
              createdAt: serverTimestamp(),
              updatedAt: serverTimestamp(),
            },
            { merge: false }
          ).catch(() => {
            // Document existe déjà
          });
        } catch {
          // Déjà inscrit
        }
      }
      notify(`Bienvenue sur Lubumarket, ${displayName}.`, 'success');
    } catch (err) {
      notify(
        err instanceof Error ? err.message : 'La connexion Google a été annulée.',
        'error'
      );
    }
  };

  const signOutApp = async () => {
    await logoutUser();
    notify('Vous êtes déconnecté.', 'info');
  };

  const toggleFavorite = async (productId: string) => {
    if (!firebaseUser || !customerDoc) {
      notify('Connectez-vous pour enregistrer vos favoris.', 'info');
      return;
    }
    const currentFavs: string[] = Array.isArray(customerDoc.favoriteProductIds)
      ? customerDoc.favoriteProductIds
      : [];
    const exists = currentFavs.includes(productId);
    const nextFavs = exists
      ? currentFavs.filter((id) => id !== productId)
      : [...currentFavs, productId].slice(0, 100);
    try {
      await updateDoc(doc(db, 'customers', firebaseUser.uid), {
        favoriteProductIds: nextFavs,
        updatedAt: serverTimestamp(),
      });
      notify(exists ? 'Retiré des favoris.' : 'Ajouté à vos favoris.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `customers/${firebaseUser.uid}`);
    }
  };

  const markNotificationRead = async (notifId: string) => {
    if (!firebaseUser) return;
    try {
      await updateDoc(doc(db, 'notifications', notifId), {
        isRead: true,
        updatedAt: serverTimestamp(),
      });
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `notifications/${notifId}`);
    }
  };

  const isBootstrapEligible = Boolean(
    firebaseUser?.emailVerified &&
      firebaseUser?.email?.toLowerCase() === OWNER_EMAIL
  );

  const isAdmin = Boolean(
    adminDocExists || userDoc?.role === 'admin' || isBootstrapEligible
  );

  const formatPrice = (amount: number, sourceCurrency: CurrencyCode = 'USD') => {
    const converted = convertAmount(amount, sourceCurrency, displayCurrency, usdToCdf);
    if (displayCurrency === 'CDF') {
      return `${Math.round(converted).toLocaleString('fr-CD')} FC`;
    }
    return `${converted.toLocaleString('fr-CD', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })} USD`;
  };

  const categories = useMemo(
    () => (categoriesDb.length > 0 ? categoriesDb : INITIAL_CATEGORIES),
    [categoriesDb]
  );

  const deliveryZones = useMemo(
    () =>
      zonesDb.length > 0
        ? zonesDb
        : INITIAL_DELIVERY_ZONES.filter((z) => z.isActive),
    [zonesDb]
  );

  const paymentConfigs = useMemo(
    () =>
      paymentsDb.length > 0
        ? paymentsDb
        : INITIAL_PAYMENT_CONFIGS.filter((p) => p.isEnabled),
    [paymentsDb]
  );

  return (
    <LubumarketContext.Provider
      value={{
        firebaseUser,
        authReady,
        userDoc,
        customerDoc,
        sellerDoc,
        courierDoc,
        isAdmin,
        isBootstrapEligible,
        displayCurrency,
        setDisplayCurrency,
        usdToCdf,
        exchangeRateUpdatedAt,
        formatPrice,
        categories,
        products,
        shops,
        deliveryZones,
        paymentConfigs,
        notifications,
        cart,
        addToCart,
        updateCartQty,
        clearCart,
        toggleFavorite,
        signInAndEnsureCustomer,
        signOutApp,
        markNotificationRead,
        notify,
        toasts,
        dismissToast,
      }}
    >
      {children}
    </LubumarketContext.Provider>
  );
};

export function useLubumarket() {
  const ctx = useContext(LubumarketContext);
  if (!ctx) {
    throw new Error('useLubumarket must be used inside LubumarketProvider');
  }
  return ctx;
}
