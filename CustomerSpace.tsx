import React, { useEffect, useMemo, useState } from 'react';
import {
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import {
  ArrowRight,
  CheckCircle2,
  Heart,
  KeyRound,
  MapPin,
  Minus,
  Package,
  Plus,
  Search,
  ShieldCheck,
  ShoppingBag,
  Star,
  Store,
  Trash2,
  Truck,
  User,
} from 'lucide-react';
import {
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  DELIVERY_STATUS_LABELS,
  buildMultiSellerOrderPlan,
  roundMoney,
  type CartCalculationItem,
  type CurrencyCode,
  type DeliveryStatus,
  type OrderStatus,
  type PaymentStatus,
} from '../shared/domain';
import {
  OperationType,
  apiPost,
  db,
  handleFirestoreError,
  makeClientId,
} from '../lib/firebase';
import { LUBUMARKET_ASSETS, SafeImage } from '../lib/catalogImages';
import { formatFsTimestamp, useLubumarket } from '../context/LubumarketContext';

export type CustomerSubView =
  | 'home'
  | 'catalog'
  | 'shops'
  | 'favorites'
  | 'cart'
  | 'orders'
  | 'profile';

interface CustomerSpaceProps {
  subView: CustomerSubView;
  onChangeSubView: (v: CustomerSubView) => void;
  onOpenWorkspace: (ws: 'seller' | 'courier' | 'admin') => void;
}

export const CustomerSpace: React.FC<CustomerSpaceProps> = ({
  subView,
  onChangeSubView,
  onOpenWorkspace,
}) => {
  const {
    firebaseUser,
    userDoc,
    customerDoc,
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
    cart,
    addToCart,
    updateCartQty,
    clearCart,
    toggleFavorite,
    signInAndEnsureCustomer,
    notify,
  } = useLubumarket();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('all');
  const [selectedShopId, setSelectedShopId] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'popular' | 'newest' | 'price_asc' | 'price_desc'>('popular');
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [detailQty, setDetailQty] = useState(1);

  // Données privées client
  const [addresses, setAddresses] = useState<Array<Record<string, any>>>([]);
  const [orders, setOrders] = useState<Array<Record<string, any>>>([]);
  const [subOrders, setSubOrders] = useState<Array<Record<string, any>>>([]);
  const [deliveries, setDeliveries] = useState<Array<Record<string, any>>>([]);
  const [deliveryCodes, setDeliveryCodes] = useState<Record<string, Record<string, any>>>({});
  const [orderItems, setOrderItems] = useState<Array<Record<string, any>>>([]);
  const [productReviews, setProductReviews] = useState<Array<Record<string, any>>>([]);

  // Formulaires checkout & adresse
  const [selectedAddressId, setSelectedAddressId] = useState<string>('');
  const [selectedPaymentCode, setSelectedPaymentCode] = useState<string>('cod');
  const [showNewAddressForm, setShowNewAddressForm] = useState(false);
  const [addrLabel, setAddrLabel] = useState('Domicile');
  const [addrRecipient, setAddrRecipient] = useState('');
  const [addrPhone, setAddrPhone] = useState('');
  const [addrZoneId, setAddrZoneId] = useState('');
  const [addrCommune, setAddrCommune] = useState('');
  const [addrStreet, setAddrStreet] = useState('');
  const [submittingOrder, setSubmittingOrder] = useState(false);
  const [busyActionId, setBusyActionId] = useState<string | null>(null);

  // Formulaire profil
  const [profName, setProfName] = useState('');
  const [profPhone, setProfPhone] = useState('');
  const [profCity, setProfCity] = useState('Lubumbashi');
  const [savingProfile, setSavingProfile] = useState(false);

  // Formulaire avis après livraison
  const [reviewOrderTarget, setReviewOrderTarget] = useState<{
    orderId: string;
    subOrderId: string;
    productId: string;
    productTitle: string;
  } | null>(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);

  useEffect(() => {
    if (customerDoc) {
      setProfName(customerDoc.fullName || '');
      setProfPhone(customerDoc.phone || '');
      setProfCity(customerDoc.city || 'Lubumbashi');
      if (!addrRecipient) setAddrRecipient(customerDoc.fullName || '');
      if (!addrPhone && customerDoc.phone) setAddrPhone(customerDoc.phone);
    }
  }, [customerDoc]);

  useEffect(() => {
    if (deliveryZones.length > 0 && !addrZoneId) {
      setAddrZoneId(deliveryZones[0].id);
    }
  }, [deliveryZones, addrZoneId]);

  // Abonnements temps réel aux données privées du client connecté
  useEffect(() => {
    if (!firebaseUser) {
      setAddresses([]);
      setOrders([]);
      setSubOrders([]);
      setDeliveries([]);
      setDeliveryCodes({});
      setOrderItems([]);
      return;
    }

    const uid = firebaseUser.uid;

    const unsubAddr = onSnapshot(
      query(collection(db, 'addresses'), where('userId', '==', uid)),
      (snap) => {
        const list = snap.docs.map((d) => d.data());
        setAddresses(list);
        if (list.length > 0 && !selectedAddressId) {
          setSelectedAddressId(list[0].id);
        }
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'addresses')
    );

    const unsubOrders = onSnapshot(
      query(collection(db, 'orders'), where('customerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setOrders(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'orders')
    );

    const unsubSubs = onSnapshot(
      query(collection(db, 'sellerSubOrders'), where('customerId', '==', uid)),
      (snap) => setSubOrders(snap.docs.map((d) => d.data())),
      (err) => handleFirestoreError(err, OperationType.LIST, 'sellerSubOrders')
    );

    const unsubDels = onSnapshot(
      query(collection(db, 'deliveries'), where('customerId', '==', uid)),
      (snap) => setDeliveries(snap.docs.map((d) => d.data())),
      (err) => handleFirestoreError(err, OperationType.LIST, 'deliveries')
    );

    const unsubCodes = onSnapshot(
      query(collection(db, 'customerDeliveryCodes'), where('customerId', '==', uid)),
      (snap) => {
        const map: Record<string, Record<string, any>> = {};
        for (const d of snap.docs) {
          const data = d.data();
          map[data.subOrderId] = data;
        }
        setDeliveryCodes(map);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'customerDeliveryCodes')
    );

    const unsubItems = onSnapshot(
      query(collection(db, 'orderItems'), where('customerId', '==', uid)),
      (snap) => setOrderItems(snap.docs.map((d) => d.data())),
      (err) => handleFirestoreError(err, OperationType.LIST, 'orderItems')
    );

    return () => {
      unsubAddr();
      unsubOrders();
      unsubSubs();
      unsubDels();
      unsubCodes();
      unsubItems();
    };
  }, [firebaseUser]);

  // Avis du produit sélectionné
  useEffect(() => {
    if (!selectedProductId) {
      setProductReviews([]);
      return;
    }
    const qRev = query(collection(db, 'reviews'), where('productId', '==', selectedProductId));
    const unsub = onSnapshot(
      qRev,
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setProductReviews(list);
      },
      () => setProductReviews([])
    );
    return () => unsub();
  }, [selectedProductId]);

  const favoriteIds: string[] = Array.isArray(customerDoc?.favoriteProductIds)
    ? customerDoc.favoriteProductIds
    : [];

  const filteredProducts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const list = products.filter((p) => {
      if (selectedCategoryId !== 'all' && p.categoryId !== selectedCategoryId) return false;
      if (selectedShopId !== 'all' && p.shopId !== selectedShopId) return false;
      if (!q) return true;
      return (
        String(p.title || '').toLowerCase().includes(q) ||
        String(p.description || '').toLowerCase().includes(q) ||
        String(p.shopName || '').toLowerCase().includes(q) ||
        String(p.categoryName || '').toLowerCase().includes(q)
      );
    });

    return [...list].sort((a, b) => {
      if (sortBy === 'newest') {
        return (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0);
      }
      if (sortBy === 'price_asc') {
        const pa = a.currency === 'CDF' ? Number(a.price) / usdToCdf : Number(a.price);
        const pb = b.currency === 'CDF' ? Number(b.price) / usdToCdf : Number(b.price);
        return pa - pb;
      }
      if (sortBy === 'price_desc') {
        const pa = a.currency === 'CDF' ? Number(a.price) / usdToCdf : Number(a.price);
        const pb = b.currency === 'CDF' ? Number(b.price) / usdToCdf : Number(b.price);
        return pb - pa;
      }
      return Number(b.soldCount || 0) - Number(a.soldCount || 0);
    });
  }, [products, searchQuery, selectedCategoryId, selectedShopId, sortBy, usdToCdf]);

  const popularProducts = useMemo(
    () =>
      [...products]
        .sort((a, b) => Number(b.soldCount || 0) - Number(a.soldCount || 0))
        .slice(0, 8),
    [products]
  );

  const newestProducts = useMemo(
    () =>
      [...products]
        .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
        .slice(0, 4),
    [products]
  );

  const selectedProduct = useMemo(
    () => products.find((p) => p.id === selectedProductId) || null,
    [products, selectedProductId]
  );

  const selectedAddress = useMemo(
    () => addresses.find((a) => a.id === selectedAddressId) || null,
    [addresses, selectedAddressId]
  );

  const activeZoneForCheckout = useMemo(() => {
    if (selectedAddress) {
      const match = deliveryZones.find((z) => z.id === selectedAddress.zoneId);
      if (match) return match;
    }
    return deliveryZones[0] || { id: 'zone-lushi-centre', name: 'Lubumbashi Centre', feeUSD: 3 };
  }, [selectedAddress, deliveryZones]);

  const cartPlanPreview = useMemo(() => {
    if (cart.length === 0) return null;
    const calcItems: CartCalculationItem[] = [];
    for (const c of cart) {
      const prod = products.find((p) => p.id === c.productId);
      if (!prod) continue;
      const shop = shops.find((s) => s.id === prod.shopId);
      calcItems.push({
        productId: prod.id,
        title: prod.title,
        imageUrl: (prod.imageUrls && prod.imageUrls[0]) || '/icon.svg',
        sellerId: prod.sellerId,
        shopId: prod.shopId,
        shopName: prod.shopName,
        shopAddress: shop?.address || 'Lubumbashi',
        shopPhone: shop?.phone || '',
        unitPriceNative: Number(prod.price),
        nativeCurrency: prod.currency === 'CDF' ? 'CDF' : 'USD',
        quantity: Math.min(c.quantity, Math.max(1, Number(prod.stock || 1))),
        availableStock: Math.max(c.quantity, Number(prod.stock || 1)),
      });
    }
    if (calcItems.length === 0) return null;
    try {
      return buildMultiSellerOrderPlan(
        calcItems,
        displayCurrency,
        Number(activeZoneForCheckout.feeUSD || 3),
        usdToCdf
      );
    } catch {
      return null;
    }
  }, [cart, products, shops, displayCurrency, activeZoneForCheckout, usdToCdf]);

  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser) {
      await signInAndEnsureCustomer();
      return;
    }
    const zone = deliveryZones.find((z) => z.id === addrZoneId);
    if (!zone) {
      notify('Veuillez choisir une zone de livraison active.', 'error');
      return;
    }
    if (addrRecipient.trim().length < 2 || addrPhone.trim().length < 6 || addrStreet.trim().length < 4) {
      notify('Veuillez renseigner le destinataire, le téléphone et les détails de l’adresse.', 'error');
      return;
    }

    const addressId = makeClientId('adr');
    try {
      await setDoc(doc(db, 'addresses', addressId), {
        id: addressId,
        userId: firebaseUser.uid,
        label: addrLabel.trim().slice(0, 80) || 'Domicile',
        recipientName: addrRecipient.trim().slice(0, 120),
        phone: addrPhone.trim().slice(0, 32),
        city: zone.city || 'Lubumbashi',
        commune: (addrCommune.trim() || zone.name).slice(0, 80),
        zoneId: zone.id,
        zoneName: zone.name,
        streetDetails: addrStreet.trim().slice(0, 240),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setSelectedAddressId(addressId);
      setShowNewAddressForm(false);
      setAddrStreet('');
      notify('Adresse de livraison enregistrée.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.CREATE, `addresses/${addressId}`);
    }
  };

  const handleDeleteAddress = async (addressId: string) => {
    try {
      await deleteDoc(doc(db, 'addresses', addressId));
      if (selectedAddressId === addressId) {
        setSelectedAddressId('');
      }
      notify('Adresse supprimée.', 'info');
    } catch (err) {
      handleFirestoreError(err, OperationType.DELETE, `addresses/${addressId}`);
    }
  };

  const handlePlaceOrder = async () => {
    if (!firebaseUser) {
      await signInAndEnsureCustomer();
      return;
    }
    if (!selectedAddressId) {
      notify('Veuillez sélectionner ou ajouter une adresse de livraison.', 'error');
      setShowNewAddressForm(true);
      return;
    }
    if (cart.length === 0) {
      notify('Votre panier est vide.', 'error');
      return;
    }

    setSubmittingOrder(true);
    try {
      const idempotencyKey = makeClientId('idem-ord');
      const res = await apiPost<{
        ok: boolean;
        orderId: string;
        orderNumber: string;
        subOrderCount: number;
      }>('/api/orders/create', {
        idempotencyKey,
        currency: displayCurrency,
        addressId: selectedAddressId,
        paymentMethod: selectedPaymentCode,
        items: cart.map((c) => ({ productId: c.productId, quantity: c.quantity })),
      });
      clearCart();
      notify(
        `Commande ${res.orderNumber} validée (${res.subOrderCount} boutique(s)). Votre code secret à 6 chiffres est prêt.`,
        'success'
      );
      onChangeSubView('orders');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur lors de la commande.', 'error');
    } finally {
      setSubmittingOrder(false);
    }
  };

  const handleTestPaymentConfirm = async (orderId: string, outcome: 'confirmed' | 'failed') => {
    setBusyActionId(orderId);
    try {
      await apiPost('/api/payments/confirm-test', { orderId, outcome });
      notify(
        outcome === 'confirmed'
          ? 'Paiement en mode test confirmé côté serveur.'
          : 'Échec de paiement simulé.',
        outcome === 'confirmed' ? 'success' : 'info'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur paiement test.', 'error');
    } finally {
      setBusyActionId(null);
    }
  };

  const handleCancelOrder = async (orderId: string) => {
    setBusyActionId(orderId);
    try {
      await apiPost('/api/orders/cancel-or-refund', {
        orderId,
        reason: 'Annulée par le client avant préparation',
      });
      notify('Commande annulée.', 'info');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Impossible d’annuler.', 'error');
    } finally {
      setBusyActionId(null);
    }
  };

  const handleConfirmReceipt = async (orderId: string) => {
    setBusyActionId(orderId);
    try {
      await apiPost('/api/orders/confirm-receipt', { orderId });
      notify('Réception définitivement confirmée. Vous pouvez évaluer vos articles.', 'success');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Confirmation refusée.', 'error');
    } finally {
      setBusyActionId(null);
    }
  };

  const handleSubmitReview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser || !reviewOrderTarget) return;
    if (reviewComment.trim().length < 3) {
      notify('Veuillez rédiger un commentaire d’au moins 3 caractères.', 'error');
      return;
    }
    const prod = products.find((p) => p.id === reviewOrderTarget.productId);
    if (!prod) {
      notify('Produit introuvable.', 'error');
      return;
    }

    setSubmittingReview(true);
    try {
      const reviewId = makeClientId('rev');
      const oldCount = Number(prod.ratingCount || 0);
      const oldAvg = Number(prod.ratingAvg || 0);
      const nextCount = oldCount + 1;
      const nextAvg = roundMoney((oldAvg * oldCount + reviewRating) / nextCount, 'USD');

      const batch = writeBatch(db);
      batch.set(doc(db, 'reviews', reviewId), {
        id: reviewId,
        productId: reviewOrderTarget.productId,
        orderId: reviewOrderTarget.orderId,
        subOrderId: reviewOrderTarget.subOrderId,
        customerId: firebaseUser.uid,
        customerName: (customerDoc?.fullName || firebaseUser.displayName || 'Client Lubumarket').slice(0, 120),
        rating: reviewRating,
        comment: reviewComment.trim().slice(0, 600),
        createdAt: serverTimestamp(),
      });
      batch.update(doc(db, 'products', reviewOrderTarget.productId), {
        ratingAvg: Math.min(5, Math.max(0, nextAvg)),
        ratingCount: nextCount,
        lastReviewRef: reviewId,
        updatedAt: serverTimestamp(),
      });
      await batch.commit();
      setReviewOrderTarget(null);
      setReviewComment('');
      setReviewRating(5);
      notify('Merci ! Votre évaluation vérifiée a été publiée.', 'success');
    } catch (err) {
      notify(
        err instanceof Error ? err.message : 'Impossible d’enregistrer votre avis.',
        'error'
      );
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser || !userDoc || !customerDoc) return;
    setSavingProfile(true);
    try {
      const batch = writeBatch(db);
      batch.update(doc(db, 'users', firebaseUser.uid), {
        displayName: profName.trim().slice(0, 120) || userDoc.displayName,
        phone: profPhone.trim().slice(0, 32),
        city: profCity.trim().slice(0, 80) || 'Lubumbashi',
        updatedAt: serverTimestamp(),
      });
      batch.update(doc(db, 'customers', firebaseUser.uid), {
        fullName: profName.trim().slice(0, 120) || customerDoc.fullName,
        phone: profPhone.trim().slice(0, 32),
        city: profCity.trim().slice(0, 80) || 'Lubumbashi',
        updatedAt: serverTimestamp(),
      });
      await batch.commit();
      notify('Profil client mis à jour.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `customers/${firebaseUser.uid}`);
    } finally {
      setSavingProfile(false);
    }
  };

  const openProductDetail = (productId: string) => {
    setSelectedProductId(productId);
    setActiveImageIndex(0);
    setDetailQty(1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const renderProductCard = (p: Record<string, any>) => {
    const isFav = favoriteIds.includes(p.id);
    const outOfStock = Number(p.stock) < 1;
    const cover = (p.imageUrls && p.imageUrls[0]) || '';

    return (
      <article
        key={p.id}
        className="group bg-white rounded-xl border border-slate-200/90 overflow-hidden flex flex-col hover:border-slate-300 transition-colors"
      >
        <div
          onClick={() => openProductDetail(p.id)}
          className="relative aspect-[4/3] bg-slate-100 cursor-pointer overflow-hidden"
        >
          <SafeImage
            src={cover}
            alt={p.title}
            className="w-full h-full object-cover group-hover:scale-[1.03] transition-transform duration-300"
          />
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              toggleFavorite(p.id);
            }}
            aria-label={isFav ? 'Retirer des favoris' : 'Ajouter aux favoris'}
            className="absolute top-2.5 right-2.5 w-10 h-10 rounded-lg bg-white/95 border border-slate-200/80 flex items-center justify-center text-slate-700 hover:text-orange-600 transition-colors"
          >
            <Heart className={`w-4 h-4 ${isFav ? 'fill-orange-600 text-orange-600' : ''}`} />
          </button>
        </div>

        <div className="p-4 flex-1 flex flex-col justify-between gap-3">
          <div>
            <p className="text-xs text-slate-500 truncate">
              {p.shopName} · {p.city || 'Lubumbashi'}
              {Number(p.ratingCount || 0) > 0
                ? ` · ★ ${Number(p.ratingAvg).toFixed(1)} (${p.ratingCount})`
                : ''}
            </p>
            <h3
              onClick={() => openProductDetail(p.id)}
              className="mt-1 text-sm font-semibold text-slate-900 line-clamp-2 cursor-pointer hover:text-[#0F3D8A]"
            >
              {p.title}
            </h3>
          </div>

          <div className="pt-2 border-t border-slate-100 flex items-end justify-between gap-2">
            <div>
              <p className="text-base font-bold text-slate-900 font-mono tabular-nums">
                {formatPrice(Number(p.price), p.currency === 'CDF' ? 'CDF' : 'USD')}
              </p>
              <p className="text-[11px] text-slate-500">
                {outOfStock ? 'Rupture temporaire' : `${p.stock} en stock`}
              </p>
            </div>
            <button
              type="button"
              disabled={outOfStock}
              onClick={() => addToCart(p.id, 1)}
              className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-[#0F3D8A] px-3 py-2 text-xs font-semibold text-white hover:bg-[#0A2B63] disabled:bg-slate-200 disabled:text-slate-500 transition-colors min-h-[40px]"
            >
              <ShoppingBag className="w-3.5 h-3.5 shrink-0" />
              <span>Ajouter</span>
            </button>
          </div>
        </div>
      </article>
    );
  };

  // =========================================================================
  // VUE FICHE PRODUIT DÉTAILLÉE (GALERIE STICKY + MODULE D'ACHAT CONTIGU)
  // =========================================================================
  if (selectedProduct) {
    const imgs: string[] =
      Array.isArray(selectedProduct.imageUrls) && selectedProduct.imageUrls.length > 0
        ? selectedProduct.imageUrls
        : [''];
    const currentImg = imgs[activeImageIndex] || imgs[0];
    const isFav = favoriteIds.includes(selectedProduct.id);
    const outOfStock = Number(selectedProduct.stock) < 1;

    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-10">
        <div className="flex items-center justify-between gap-4">
          <button
            type="button"
            onClick={() => setSelectedProductId(null)}
            className="inline-flex items-center gap-2 text-sm font-semibold text-slate-700 hover:text-[#0F3D8A] min-h-[44px]"
          >
            ← Retour au catalogue
          </button>
          <p className="text-xs text-slate-500">
            {selectedProduct.categoryName} · {selectedProduct.city || 'Lubumbashi'}
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Galerie sticky à gauche */}
          <div className="lg:col-span-7 lg:sticky lg:top-20 space-y-3">
            <div className="aspect-[4/3] w-full rounded-2xl bg-white border border-slate-200 overflow-hidden">
              <SafeImage
                src={currentImg}
                alt={selectedProduct.title}
                className="w-full h-full object-cover"
              />
            </div>
            {imgs.length > 1 && (
              <div className="flex gap-3 overflow-x-auto pb-1">
                {imgs.map((u, idx) => (
                  <button
                    key={u + idx}
                    type="button"
                    onClick={() => setActiveImageIndex(idx)}
                    className={`w-20 h-16 rounded-lg overflow-hidden border-2 shrink-0 ${
                      idx === activeImageIndex ? 'border-[#0F3D8A]' : 'border-slate-200'
                    }`}
                  >
                    <SafeImage src={u} alt={`${selectedProduct.title} ${idx + 1}`} className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Module d'achat contigu à droite */}
          <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-6">
            <div className="space-y-2 border-b border-slate-100 pb-5">
              <p className="text-xs font-medium text-slate-500">
                Boutique {selectedProduct.shopName} · {selectedProduct.city || 'Lubumbashi'}
                {Number(selectedProduct.ratingCount || 0) > 0
                  ? ` · ★ ${Number(selectedProduct.ratingAvg).toFixed(1)} (${selectedProduct.ratingCount} avis)`
                  : ' · Nouveau produit'}
              </p>
              <h1 className="text-2xl font-bold text-slate-900 leading-snug">
                {selectedProduct.title}
              </h1>
              <div className="pt-2 flex items-baseline gap-3">
                <span className="text-3xl font-extrabold text-slate-900 font-mono tabular-nums">
                  {formatPrice(
                    Number(selectedProduct.price),
                    selectedProduct.currency === 'CDF' ? 'CDF' : 'USD'
                  )}
                </span>
                <span className="text-xs text-slate-500 font-mono tabular-nums">
                  Prix boutique : {selectedProduct.price} {selectedProduct.currency}
                </span>
              </div>
            </div>

            <p className="text-sm text-slate-700 leading-relaxed whitespace-pre-line">
              {selectedProduct.description || 'Article commercialisé par une boutique partenaire vérifiée à Lubumbashi.'}
            </p>

            <div className="space-y-4 pt-2">
              <div className="flex items-center justify-between text-xs text-slate-600">
                <span>Disponibilité immédiate</span>
                <span className="font-semibold text-slate-900 font-mono tabular-nums">
                  {outOfStock ? 'Rupture de stock' : `${selectedProduct.stock} unité(s) disponible(s)`}
                </span>
              </div>

              {!outOfStock && (
                <div className="flex items-center gap-3">
                  <div className="inline-flex items-center rounded-xl border border-slate-200 bg-slate-50">
                    <button
                      type="button"
                      onClick={() => setDetailQty((q) => Math.max(1, q - 1))}
                      className="w-11 h-11 flex items-center justify-center text-slate-700 hover:text-slate-900"
                      aria-label="Diminuer la quantité"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="w-10 text-center text-sm font-bold font-mono tabular-nums">
                      {detailQty}
                    </span>
                    <button
                      type="button"
                      onClick={() =>
                        setDetailQty((q) => Math.min(Number(selectedProduct.stock), q + 1))
                      }
                      className="w-11 h-11 flex items-center justify-center text-slate-700 hover:text-slate-900"
                      aria-label="Augmenter la quantité"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      addToCart(selectedProduct.id, detailQty);
                      setSelectedProductId(null);
                      onChangeSubView('cart');
                    }}
                    className="flex-1 rounded-xl bg-orange-600 px-5 py-3 text-sm font-bold text-white hover:bg-orange-700 transition-colors min-h-[46px]"
                  >
                    Ajouter au panier
                  </button>

                  <button
                    type="button"
                    onClick={() => toggleFavorite(selectedProduct.id)}
                    className="w-12 h-12 rounded-xl border border-slate-200 flex items-center justify-center text-slate-700 hover:text-orange-600"
                    aria-label="Favori"
                  >
                    <Heart className={`w-5 h-5 ${isFav ? 'fill-orange-600 text-orange-600' : ''}`} />
                  </button>
                </div>
              )}
            </div>

            <div className="pt-4 border-t border-slate-100 space-y-2.5 text-xs text-slate-600">
              <div className="flex items-start gap-2.5">
                <Truck className="w-4 h-4 text-[#0F3D8A] shrink-0 mt-0.5" />
                <span>Livraison à domicile dans toutes les communes actives de Lubumbashi.</span>
              </div>
              <div className="flex items-start gap-2.5">
                <KeyRound className="w-4 h-4 text-[#0F3D8A] shrink-0 mt-0.5" />
                <span>Remise sécurisée par code client à 6 chiffres à usage unique.</span>
              </div>
            </div>
          </div>
        </div>

        {/* Évaluations clients après livraison confirmée */}
        <section className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-5">
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                Évaluations clients vérifiées ({productReviews.length})
              </h2>
              <p className="text-xs text-slate-500">
                Seuls les acheteurs ayant confirmé la réception de leur livraison peuvent publier un avis.
              </p>
            </div>
          </div>

          {productReviews.length === 0 ? (
            <p className="text-sm text-slate-500 py-4">
              Aucune évaluation publiée pour cet article pour le moment.
            </p>
          ) : (
            <div className="divide-y divide-slate-100">
              {productReviews.map((rev) => (
                <div key={rev.id} className="py-4 space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-slate-500">
                    <span className="font-semibold text-slate-900">
                      {rev.customerName} · {'★'.repeat(Number(rev.rating || 5))}
                    </span>
                    <span>{formatFsTimestamp(rev.createdAt) || 'Achat vérifié'}</span>
                  </div>
                  <p className="text-sm text-slate-700">{rev.comment}</p>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    );
  }

  // =========================================================================
  // 1. ACCUEIL LUBUMARKET (HERO + 3 SECTIONS FOCALISÉES)
  // =========================================================================
  if (subView === 'home') {
    return (
      <div className="space-y-12 pb-12">
        {/* Hero commercial Lubumbashi */}
        <section className="bg-slate-900 text-white relative overflow-hidden">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10 sm:py-16 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            <div className="lg:col-span-7 space-y-5 z-10">
              <p className="text-xs font-semibold tracking-wide text-orange-400 uppercase">
                Lubumbashi · Haut-Katanga · République démocratique du Congo
              </p>
              <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight leading-[1.1]">
                Achetez en ligne, recevez chez vous.
              </h1>
              <p className="text-sm sm:text-base text-slate-200 max-w-xl leading-relaxed">
                Découvrez les boutiques indépendantes de Lubumbashi sur une seule plateforme sécurisée. Prix transparents en USD et Francs Congolais, livraison à domicile protégée par code unique.
              </p>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  onChangeSubView('catalog');
                }}
                className="flex flex-col sm:flex-row gap-2.5 max-w-xl pt-1"
              >
                <div className="relative flex-1">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="search"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Rechercher un téléphone, ordinateur, vêtement, ciment..."
                    className="w-full rounded-xl bg-white pl-10 pr-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none min-h-[46px]"
                  />
                </div>
                <button
                  type="submit"
                  className="rounded-xl bg-orange-600 px-6 py-3 text-sm font-bold text-white hover:bg-orange-500 transition-colors min-h-[46px] shrink-0"
                >
                  Explorer le catalogue
                </button>
              </form>

              <p className="text-xs text-slate-300">
                Vendeurs de Lubumbashi : inscription 100 % gratuite · 0 % à la commande · 5 % uniquement au retrait
              </p>
            </div>

            <div className="lg:col-span-5">
              <div className="aspect-[16/10] rounded-2xl overflow-hidden border border-white/15 shadow-2xl">
                <img
                  src={LUBUMARKET_ASSETS.heroBanner}
                  alt="Lubumarket Lubumbashi Marketplace"
                  className="w-full h-full object-cover"
                />
              </div>
            </div>
          </div>
        </section>

        {/* Section 1 : Catégories officielles + Produits populaires */}
        <section className="max-w-7xl mx-auto px-4 sm:px-6 space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-slate-500">12 rayons officiels</p>
              <h2 className="text-xl sm:text-2xl font-bold text-slate-900">
                Parcourir par catégorie et meilleures ventes
              </h2>
            </div>
            <button
              type="button"
              onClick={() => {
                setSelectedCategoryId('all');
                setSelectedShopId('all');
                onChangeSubView('catalog');
              }}
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#0F3D8A] hover:underline"
            >
              <span>Voir tout le catalogue</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-2.5">
            {categories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => {
                  setSelectedCategoryId(cat.id);
                  onChangeSubView('catalog');
                }}
                className="text-left rounded-xl bg-white border border-slate-200 px-3.5 py-3 hover:border-[#0F3D8A] transition-colors min-h-[56px]"
              >
                <p className="text-xs font-semibold text-slate-900 line-clamp-2">{cat.name}</p>
              </button>
            ))}
          </div>

          {popularProducts.length === 0 ? (
            <div className="rounded-2xl bg-white border border-slate-200 p-8 text-center space-y-3">
              <Package className="w-10 h-10 text-[#0F3D8A] mx-auto" />
              <h3 className="text-base font-bold text-slate-900">
                Le catalogue Lubumarket est prêt à accueillir les premières marchandises
              </h3>
              <p className="text-sm text-slate-600 max-w-lg mx-auto">
                Ouvrez gratuitement une boutique dans l’Espace Vendeur pour publier vos produits en USD ou CDF, ou initialisez le catalogue de démonstration depuis l’Espace Administrateur propriétaire.
              </p>
              <div className="pt-2 flex flex-wrap justify-center gap-3">
                <button
                  type="button"
                  onClick={() => onOpenWorkspace('seller')}
                  className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#0A2B63] min-h-[44px]"
                >
                  Ouvrir une boutique gratuite
                </button>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {popularProducts.map((p) => renderProductCard(p))}
            </div>
          )}
        </section>

        {/* Section 2 : Nouveautés récemment mises en ligne */}
        {newestProducts.length > 0 && (
          <section className="max-w-7xl mx-auto px-4 sm:px-6 space-y-5">
            <div className="flex items-end justify-between">
              <div>
                <p className="text-xs font-medium text-slate-500">Arrivages récents · Lubumbashi</p>
                <h2 className="text-xl font-bold text-slate-900">Nouveautés en boutique</h2>
              </div>
              <button
                type="button"
                onClick={() => {
                  setSortBy('newest');
                  onChangeSubView('catalog');
                }}
                className="text-sm font-semibold text-[#0F3D8A] hover:underline"
              >
                Tout afficher
              </button>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {newestProducts.map((p) => renderProductCard(p))}
            </div>
          </section>
        )}

        {/* Section 3 : Boutiques indépendantes et recrutement partenaires */}
        <section className="max-w-7xl mx-auto px-4 sm:px-6 space-y-6">
          <div className="flex items-end justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500">Commerce local vérifié</p>
              <h2 className="text-xl font-bold text-slate-900">
                Boutiques partenaires à Lubumbashi ({shops.length})
              </h2>
            </div>
            <button
              type="button"
              onClick={() => onChangeSubView('shops')}
              className="text-sm font-semibold text-[#0F3D8A] hover:underline"
            >
              Toutes les boutiques
            </button>
          </div>

          {shops.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              {shops.slice(0, 3).map((shop) => (
                <div
                  key={shop.id}
                  className="bg-white rounded-2xl border border-slate-200 p-5 flex items-start gap-4"
                >
                  <div className="w-14 h-14 rounded-xl bg-slate-100 border border-slate-200 overflow-hidden shrink-0">
                    <SafeImage src={shop.logoUrl} alt={shop.name} className="w-full h-full object-cover" />
                  </div>
                  <div className="min-w-0 flex-1 space-y-1">
                    <h3 className="text-sm font-bold text-slate-900 truncate">{shop.name}</h3>
                    <p className="text-xs text-slate-500">
                      {shop.activityZone} · {shop.city}
                    </p>
                    <p className="text-xs text-slate-600 line-clamp-2">{shop.description}</p>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedShopId(shop.id);
                        onChangeSubView('catalog');
                      }}
                      className="pt-1 text-xs font-semibold text-[#0F3D8A] hover:underline"
                    >
                      Voir les produits →
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="rounded-2xl bg-[#0F3D8A] text-white p-6 sm:p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="space-y-1.5 max-w-2xl">
              <p className="text-xs font-medium text-blue-200">
                Expansion Haut-Katanga & RDC · Kolwezi · Likasi · Kinshasa
              </p>
              <h3 className="text-lg sm:text-xl font-bold">
                Vous êtes commerçant ou livreur indépendant à Lubumbashi ?
              </h3>
              <p className="text-sm text-blue-100">
                Ouvrez votre boutique gratuitement (5 % uniquement lors d’un retrait) ou rejoignez la flotte de livraison partenaire.
              </p>
            </div>
            <div className="flex flex-wrap gap-3 shrink-0">
              <button
                type="button"
                onClick={() => onOpenWorkspace('seller')}
                className="rounded-xl bg-orange-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-orange-500 transition-colors min-h-[44px]"
              >
                Devenir Vendeur
              </button>
              <button
                type="button"
                onClick={() => onOpenWorkspace('courier')}
                className="rounded-xl bg-white/10 border border-white/25 px-4 py-2.5 text-sm font-semibold text-white hover:bg-white/20 transition-colors min-h-[44px]"
              >
                Devenir Livreur
              </button>
            </div>
          </div>
        </section>
      </div>
    );
  }

  // =========================================================================
  // 2. CATALOGUE COMPLET & RECHERCHE
  // =========================================================================
  if (subView === 'catalog') {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-slate-200 pb-5">
          <div>
            <p className="text-xs text-slate-500">
              Lubumbashi · Taux de référence : 1 USD = {usdToCdf.toLocaleString('fr-CD')} FC
            </p>
            <h1 className="text-2xl font-bold text-slate-900">Catalogue des produits</h1>
          </div>

          <div className="flex flex-col sm:flex-row gap-2.5 w-full md:w-auto">
            <div className="relative flex-1 sm:w-72">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="search"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Chercher un article ou une boutique..."
                className="w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3 py-2 text-sm text-slate-900 min-h-[42px]"
              />
            </div>

            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 min-h-[42px]"
            >
              <option value="popular">Plus populaires</option>
              <option value="newest">Nouveautés</option>
              <option value="price_asc">Prix croissant</option>
              <option value="price_desc">Prix décroissant</option>
            </select>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setSelectedCategoryId('all')}
            className={`rounded-lg px-3 py-2 text-xs font-semibold min-h-[38px] border ${
              selectedCategoryId === 'all'
                ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
            }`}
          >
            Toutes catégories
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setSelectedCategoryId(c.id)}
              className={`rounded-lg px-3 py-2 text-xs font-semibold min-h-[38px] border ${
                selectedCategoryId === c.id
                  ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                  : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
              }`}
            >
              {c.name}
            </button>
          ))}
        </div>

        {selectedShopId !== 'all' && (
          <div className="flex items-center justify-between rounded-xl bg-slate-100 px-4 py-2.5 text-xs text-slate-700">
            <span>
              Filtré par boutique :{' '}
              <strong>{shops.find((s) => s.id === selectedShopId)?.name || selectedShopId}</strong>
            </span>
            <button
              type="button"
              onClick={() => setSelectedShopId('all')}
              className="font-semibold text-[#0F3D8A] hover:underline"
            >
              Effacer le filtre boutique
            </button>
          </div>
        )}

        {filteredProducts.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center space-y-2">
            <p className="text-base font-bold text-slate-900">Aucun produit trouvé pour ces critères</p>
            <p className="text-sm text-slate-500">
              Essayez un autre mot-clé ou réinitialisez les filtres de catégorie.
            </p>
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setSelectedCategoryId('all');
                setSelectedShopId('all');
              }}
              className="mt-2 rounded-xl bg-slate-900 px-4 py-2 text-xs font-semibold text-white min-h-[40px]"
            >
              Réinitialiser les filtres
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {filteredProducts.map((p) => renderProductCard(p))}
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // 3. ANNUAIRE DES BOUTIQUES
  // =========================================================================
  if (subView === 'shops') {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <div className="border-b border-slate-200 pb-5 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
          <div>
            <p className="text-xs text-slate-500">Marchands indépendants validés par Lubumarket</p>
            <h1 className="text-2xl font-bold text-slate-900">Boutiques officielles ({shops.length})</h1>
          </div>
          <button
            type="button"
            onClick={() => onOpenWorkspace('seller')}
            className="rounded-xl bg-[#0F3D8A] px-4 py-2.5 text-xs font-bold text-white hover:bg-[#0A2B63] min-h-[42px] self-start sm:self-auto"
          >
            Créer ma boutique gratuitement
          </button>
        </div>

        {shops.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center space-y-2">
            <Store className="w-8 h-8 text-slate-400 mx-auto" />
            <p className="text-sm font-semibold text-slate-800">Aucune boutique active pour le moment.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {shops.map((shop) => {
              const count = products.filter((p) => p.shopId === shop.id).length;
              return (
                <div
                  key={shop.id}
                  className="bg-white rounded-2xl border border-slate-200 p-6 flex flex-col justify-between gap-4"
                >
                  <div className="flex items-start gap-4">
                    <div className="w-16 h-16 rounded-xl bg-slate-100 border border-slate-200 overflow-hidden shrink-0">
                      <SafeImage src={shop.logoUrl} alt={shop.name} className="w-full h-full object-cover" />
                    </div>
                    <div className="space-y-1 min-w-0">
                      <h2 className="text-base font-bold text-slate-900 truncate">{shop.name}</h2>
                      <p className="text-xs text-slate-500">
                        {shop.activityZone} · {shop.city} · {count} article(s)
                      </p>
                      <p className="text-xs text-slate-600 line-clamp-3">{shop.description}</p>
                    </div>
                  </div>

                  <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                    <span className="text-slate-500 truncate">{shop.address}</span>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedShopId(shop.id);
                        onChangeSubView('catalog');
                      }}
                      className="font-bold text-[#0F3D8A] hover:underline shrink-0 ml-2"
                    >
                      Visiter la boutique →
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // 4. FAVORIS CLIENT
  // =========================================================================
  if (subView === 'favorites') {
    const favProducts = products.filter((p) => favoriteIds.includes(p.id));
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <div className="border-b border-slate-200 pb-5">
          <p className="text-xs text-slate-500">Articles enregistrés sur votre compte</p>
          <h1 className="text-2xl font-bold text-slate-900">Mes Favoris ({favProducts.length})</h1>
        </div>

        {!firebaseUser ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
            <p className="text-sm text-slate-700">
              Connectez-vous pour retrouver vos produits favoris sur tous vos appareils Android et Web.
            </p>
            <button
              type="button"
              onClick={signInAndEnsureCustomer}
              className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-sm font-bold text-white min-h-[44px]"
            >
              Se connecter avec Google
            </button>
          </div>
        ) : favProducts.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-3">
            <p className="text-sm text-slate-600">Vous n’avez encore aucun article en favori.</p>
            <button
              type="button"
              onClick={() => onChangeSubView('catalog')}
              className="rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-semibold text-white min-h-[42px]"
            >
              Parcourir les produits
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {favProducts.map((p) => renderProductCard(p))}
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // 5. PANIER MULTI-VENDEURS & VALIDATION DE COMMANDE SÉCURISÉE
  // =========================================================================
  if (subView === 'cart') {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        <div className="border-b border-slate-200 pb-5 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
          <div>
            <p className="text-xs text-slate-500">
              Séparation automatique par boutique · 0 % de frais cachés
            </p>
            <h1 className="text-2xl font-bold text-slate-900">Mon Panier & Livraison</h1>
          </div>
          {cart.length > 0 && (
            <button
              type="button"
              onClick={clearCart}
              className="text-xs font-semibold text-red-600 hover:underline self-start sm:self-auto"
            >
              Vider le panier
            </button>
          )}
        </div>

        {cart.length === 0 || !cartPlanPreview ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center space-y-4">
            <ShoppingBag className="w-10 h-10 text-slate-400 mx-auto" />
            <h2 className="text-base font-bold text-slate-900">Votre panier est vide</h2>
            <p className="text-sm text-slate-500">
              Ajoutez des produits de une ou plusieurs boutiques de Lubumbashi pour commander.
            </p>
            <button
              type="button"
              onClick={() => onChangeSubView('catalog')}
              className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-sm font-bold text-white min-h-[44px]"
            >
              Découvrir le catalogue
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Détail par boutique + Adresse + Moyen de paiement */}
            <div className="lg:col-span-7 space-y-6">
              {/* Groupes vendeurs */}
              <div className="space-y-4">
                <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wider">
                  01. Articles groupés par boutique ({cartPlanPreview.sellerGroups.length} sous-commande(s))
                </h2>
                {cartPlanPreview.sellerGroups.map((group) => (
                  <div
                    key={group.sellerId}
                    className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4"
                  >
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                      <div>
                        <p className="text-sm font-bold text-slate-900">{group.shopName}</p>
                        <p className="text-xs text-slate-500">{group.shopAddress}</p>
                      </div>
                      <span className="text-xs font-mono tabular-nums font-semibold text-slate-700">
                        Sous-total : {group.subtotalOrderCurrency.toLocaleString('fr-CD')} {displayCurrency}
                      </span>
                    </div>

                    <div className="divide-y divide-slate-100">
                      {group.items.map((it) => {
                        const prod = products.find((p) => p.id === it.productId);
                        const maxStock = prod ? Number(prod.stock) : 99;
                        return (
                          <div key={it.productId} className="py-3 flex items-center gap-4">
                            <div className="w-16 h-16 rounded-xl bg-slate-100 overflow-hidden shrink-0">
                              <SafeImage src={it.imageUrl} alt={it.title} className="w-full h-full object-cover" />
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-semibold text-slate-900 truncate">{it.title}</p>
                              <p className="text-xs text-slate-500 font-mono tabular-nums">
                                {it.unitPriceOrderCurrency.toLocaleString('fr-CD')} {displayCurrency} / unité
                              </p>
                            </div>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                onClick={() => updateCartQty(it.productId, it.quantity - 1)}
                                className="w-9 h-9 rounded-lg border border-slate-200 flex items-center justify-center text-slate-700"
                                aria-label="Moins"
                              >
                                <Minus className="w-3.5 h-3.5" />
                              </button>
                              <span className="w-7 text-center text-sm font-bold font-mono tabular-nums">
                                {it.quantity}
                              </span>
                              <button
                                type="button"
                                onClick={() =>
                                  updateCartQty(it.productId, Math.min(maxStock, it.quantity + 1))
                                }
                                className="w-9 h-9 rounded-lg border border-slate-200 flex items-center justify-center text-slate-700"
                                aria-label="Plus"
                              >
                                <Plus className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => updateCartQty(it.productId, 0)}
                                className="w-9 h-9 rounded-lg text-slate-400 hover:text-red-600 flex items-center justify-center"
                                aria-label="Supprimer"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>

              {/* Adresse de livraison */}
              <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wider">
                    02. Adresse de livraison à domicile
                  </h2>
                  {firebaseUser && (
                    <button
                      type="button"
                      onClick={() => setShowNewAddressForm((v) => !v)}
                      className="text-xs font-bold text-[#0F3D8A] hover:underline"
                    >
                      {showNewAddressForm ? 'Fermer le formulaire' : '+ Nouvelle adresse'}
                    </button>
                  )}
                </div>

                {!firebaseUser ? (
                  <div className="rounded-xl bg-slate-50 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <p className="text-xs text-slate-700">
                      Connectez-vous pour enregistrer votre adresse de livraison à Lubumbashi et valider votre commande.
                    </p>
                    <button
                      type="button"
                      onClick={signInAndEnsureCustomer}
                      className="rounded-xl bg-[#0F3D8A] px-4 py-2 text-xs font-bold text-white shrink-0 min-h-[40px]"
                    >
                      Se connecter
                    </button>
                  </div>
                ) : (
                  <>
                    {addresses.length > 0 && (
                      <div className="space-y-2.5">
                        {addresses.map((addr) => (
                          <label
                            key={addr.id}
                            className={`flex items-start justify-between gap-3 rounded-xl border p-3.5 cursor-pointer ${
                              selectedAddressId === addr.id
                                ? 'border-[#0F3D8A] bg-blue-50/40'
                                : 'border-slate-200 hover:border-slate-300'
                            }`}
                          >
                            <div className="flex items-start gap-3">
                              <input
                                type="radio"
                                name="deliveryAddress"
                                checked={selectedAddressId === addr.id}
                                onChange={() => setSelectedAddressId(addr.id)}
                                className="mt-1"
                              />
                              <div className="text-xs space-y-0.5">
                                <p className="font-bold text-slate-900">
                                  {addr.label} · {addr.recipientName} ({addr.phone})
                                </p>
                                <p className="text-slate-600">
                                  {addr.streetDetails}, {addr.commune} — {addr.zoneName}
                                </p>
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.preventDefault();
                                handleDeleteAddress(addr.id);
                              }}
                              className="text-slate-400 hover:text-red-600 p-1"
                              aria-label="Supprimer cette adresse"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </label>
                        ))}
                      </div>
                    )}

                    {(showNewAddressForm || addresses.length === 0) && (
                      <form onSubmit={handleSaveAddress} className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Libellé (Domicile, Bureau...)
                          </label>
                          <input
                            type="text"
                            required
                            value={addrLabel}
                            onChange={(e) => setAddrLabel(e.target.value)}
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Nom complet du destinataire
                          </label>
                          <input
                            type="text"
                            required
                            value={addrRecipient}
                            onChange={(e) => setAddrRecipient(e.target.value)}
                            placeholder="Ex: Jean Kabongo"
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Téléphone joignable (RDC)
                          </label>
                          <input
                            type="tel"
                            required
                            value={addrPhone}
                            onChange={(e) => setAddrPhone(e.target.value)}
                            placeholder="+243 99 000 0000"
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Zone tarifaire de livraison
                          </label>
                          <select
                            value={addrZoneId}
                            onChange={(e) => setAddrZoneId(e.target.value)}
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          >
                            {deliveryZones.map((z) => (
                              <option key={z.id} value={z.id}>
                                {z.name} ({z.feeUSD} USD · {z.estimatedTime})
                              </option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Commune / Quartier
                          </label>
                          <input
                            type="text"
                            required
                            value={addrCommune}
                            onChange={(e) => setAddrCommune(e.target.value)}
                            placeholder="Ex: Lubumbashi / Golf Malela"
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-700 mb-1">
                            Avenue, numéro et repère
                          </label>
                          <input
                            type="text"
                            required
                            value={addrStreet}
                            onChange={(e) => setAddrStreet(e.target.value)}
                            placeholder="Ex: Av. Kasavubu n°42, près de..."
                            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                          />
                        </div>
                        <div className="sm:col-span-2">
                          <button
                            type="submit"
                            className="rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-bold text-white hover:bg-slate-800 min-h-[42px]"
                          >
                            Enregistrer cette adresse
                          </button>
                        </div>
                      </form>
                    )}
                  </>
                )}
              </div>

              {/* Choix du moyen de paiement */}
              <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
                <div>
                  <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wider">
                    03. Moyen de paiement
                  </h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Protection Lubumarket : nous ne demandons jamais votre code PIN Mobile Money, vos mots de passe bancaires ni aucun numéro complet de carte.
                  </p>
                </div>

                <div className="space-y-2.5">
                  {paymentConfigs.map((pm) => (
                    <label
                      key={pm.id}
                      className={`flex items-start gap-3 rounded-xl border p-3.5 cursor-pointer ${
                        selectedPaymentCode === pm.code
                          ? 'border-[#0F3D8A] bg-blue-50/40'
                          : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <input
                        type="radio"
                        name="paymentMethod"
                        checked={selectedPaymentCode === pm.code}
                        onChange={() => setSelectedPaymentCode(pm.code)}
                        className="mt-1"
                      />
                      <div className="text-xs space-y-0.5">
                        <p className="font-bold text-slate-900">
                          {pm.label} ·{' '}
                          <span className={pm.isTestMode ? 'text-amber-700' : 'text-emerald-700'}>
                            {pm.isTestMode ? 'MODE TEST (Adaptateur prêt)' : 'ENCAISSEMENT RÉEL À LA REMISE'}
                          </span>
                        </p>
                        <p className="text-slate-600">{pm.integrationNote}</p>
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            {/* Récapitulatif financier figé */}
            <div className="lg:col-span-5 lg:sticky lg:top-20 bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
              <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                <h2 className="text-base font-bold text-slate-900">Récapitulatif de commande</h2>
                <div className="inline-flex rounded-lg border border-slate-200 overflow-hidden text-xs font-bold">
                  <button
                    type="button"
                    onClick={() => setDisplayCurrency('USD')}
                    className={`px-3 py-1.5 ${
                      displayCurrency === 'USD' ? 'bg-[#0F3D8A] text-white' : 'bg-white text-slate-700'
                    }`}
                  >
                    USD
                  </button>
                  <button
                    type="button"
                    onClick={() => setDisplayCurrency('CDF')}
                    className={`px-3 py-1.5 ${
                      displayCurrency === 'CDF' ? 'bg-[#0F3D8A] text-white' : 'bg-white text-slate-700'
                    }`}
                  >
                    CDF
                  </button>
                </div>
              </div>

              <div className="space-y-2.5 text-sm">
                <div className="flex justify-between text-slate-600">
                  <span>Marchandises ({cart.reduce((s, i) => s + i.quantity, 0)} art.)</span>
                  <span className="font-mono tabular-nums font-semibold text-slate-900">
                    {cartPlanPreview.merchandiseTotal.toLocaleString('fr-CD')} {displayCurrency}
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Livraison ({activeZoneForCheckout.name})</span>
                  <span className="font-mono tabular-nums font-semibold text-slate-900">
                    {cartPlanPreview.deliveryFee.toLocaleString('fr-CD')} {displayCurrency}
                  </span>
                </div>
                <div className="flex justify-between text-xs text-slate-500">
                  <span>Taux figé à la commande</span>
                  <span className="font-mono tabular-nums">
                    1 USD = {usdToCdf.toLocaleString('fr-CD')} FC
                    {exchangeRateUpdatedAt ? ` (${exchangeRateUpdatedAt})` : ''}
                  </span>
                </div>
                <div className="pt-3 border-t border-slate-200 flex justify-between items-baseline">
                  <span className="text-base font-bold text-slate-900">Total à payer</span>
                  <span className="text-2xl font-extrabold text-slate-900 font-mono tabular-nums">
                    {cartPlanPreview.grandTotal.toLocaleString('fr-CD')} {displayCurrency}
                  </span>
                </div>
              </div>

              <button
                type="button"
                disabled={submittingOrder}
                onClick={handlePlaceOrder}
                className="w-full rounded-xl bg-orange-600 py-3.5 text-sm font-bold text-white hover:bg-orange-700 disabled:opacity-50 transition-colors min-h-[48px]"
              >
                {submittingOrder
                  ? 'Vérification serveur en cours...'
                  : `Valider la commande (${cartPlanPreview.grandTotal.toLocaleString('fr-CD')} ${displayCurrency})`}
              </button>

              <p className="text-xs text-slate-500 leading-relaxed">
                En validant, Lubumarket réserve le stock en temps réel et génère votre code secret de réception à 6 chiffres dans votre suivi de commandes.
              </p>
            </div>
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // 6. HISTORIQUE & SUIVI DES COMMANDES + CODE SECRET DE LIVRAISON
  // =========================================================================
  if (subView === 'orders') {
    return (
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 space-y-6">
        <div className="border-b border-slate-200 pb-5">
          <p className="text-xs text-slate-500">
            Suivi en direct · Codes uniques de remise · Évaluations après réception
          </p>
          <h1 className="text-2xl font-bold text-slate-900">Mes Commandes ({orders.length})</h1>
        </div>

        {!firebaseUser ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
            <p className="text-sm text-slate-700">Connectez-vous pour suivre vos commandes et consulter vos codes de livraison.</p>
            <button
              type="button"
              onClick={signInAndEnsureCustomer}
              className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-sm font-bold text-white min-h-[44px]"
            >
              Se connecter
            </button>
          </div>
        ) : orders.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center space-y-3">
            <Package className="w-10 h-10 text-slate-400 mx-auto" />
            <p className="text-sm font-semibold text-slate-800">Aucune commande enregistrée.</p>
            <button
              type="button"
              onClick={() => onChangeSubView('catalog')}
              className="rounded-xl bg-[#0F3D8A] px-4 py-2 text-xs font-bold text-white min-h-[40px]"
            >
              Commander un article
            </button>
          </div>
        ) : (
          <div className="space-y-6">
            {orders.map((ord) => {
              const matchingSubs = subOrders.filter((s) => s.orderId === ord.id);
              const matchingItems = orderItems.filter((i) => i.orderId === ord.id);
              const allSubsDelivered =
                matchingSubs.length > 0 && matchingSubs.every((s) => s.status === 'delivered');

              return (
                <div
                  key={ord.id}
                  className="bg-white rounded-2xl border border-slate-200 p-6 space-y-5"
                >
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                    <div className="space-y-1">
                      <p className="text-xs text-slate-500">
                        {formatFsTimestamp(ord.createdAt) || 'À l’instant'} · {ord.zoneName} · Moyen :{' '}
                        <span className="font-semibold text-slate-700">{ord.paymentMethod}</span> (
                        {ord.paymentMode === 'test' ? 'Mode Test' : 'Paiement à la livraison'})
                      </p>
                      <h2 className="text-lg font-bold text-slate-900 font-mono tabular-nums">
                        {ord.orderNumber} · {Number(ord.grandTotal).toLocaleString('fr-CD')} {ord.currency}
                      </h2>
                      <p className="text-xs font-medium text-slate-700">
                        Statut commande :{' '}
                        <strong className="text-[#0F3D8A]">
                          {ORDER_STATUS_LABELS[ord.status as OrderStatus] || ord.status}
                        </strong>{' '}
                        · Paiement :{' '}
                        <strong>
                          {PAYMENT_STATUS_LABELS[ord.paymentStatus as PaymentStatus] || ord.paymentStatus}
                        </strong>
                        {ord.customerConfirmedReceipt ? ' · Réception confirmée par vos soins' : ''}
                      </p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {ord.paymentMode === 'test' &&
                        ord.paymentStatus === 'pending' &&
                        ord.status !== 'cancelled' && (
                          <>
                            <button
                              type="button"
                              disabled={busyActionId === ord.id}
                              onClick={() => handleTestPaymentConfirm(ord.id, 'confirmed')}
                              className="rounded-xl bg-[#0F3D8A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#0A2B63] min-h-[40px]"
                            >
                              Valider paiement (Mode Test)
                            </button>
                            <button
                              type="button"
                              disabled={busyActionId === ord.id}
                              onClick={() => handleTestPaymentConfirm(ord.id, 'failed')}
                              className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 min-h-[40px]"
                            >
                              Simuler échec
                            </button>
                          </>
                        )}

                      {(ord.status === 'pending' || ord.status === 'payment_pending') && (
                        <button
                          type="button"
                          disabled={busyActionId === ord.id}
                          onClick={() => handleCancelOrder(ord.id)}
                          className="rounded-xl border border-red-200 px-3.5 py-2 text-xs font-semibold text-red-700 hover:bg-red-50 min-h-[40px]"
                        >
                          Annuler la commande
                        </button>
                      )}

                      {allSubsDelivered && !ord.customerConfirmedReceipt && (
                        <button
                          type="button"
                          disabled={busyActionId === ord.id}
                          onClick={() => handleConfirmReceipt(ord.id)}
                          className="rounded-xl bg-emerald-700 px-4 py-2 text-xs font-bold text-white hover:bg-emerald-800 min-h-[40px]"
                        >
                          Confirmer la réception définitive
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Sous-commandes par boutique & Code secret à 6 chiffres */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {matchingSubs.map((sub) => {
                      const del = deliveries.find((d) => d.subOrderId === sub.id);
                      const secretDoc = deliveryCodes[sub.id];
                      return (
                        <div
                          key={sub.id}
                          className="rounded-xl bg-slate-50 border border-slate-200/80 p-4 space-y-2.5"
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <p className="text-sm font-bold text-slate-900">{sub.shopName}</p>
                              <p className="text-xs text-slate-600">{sub.itemsSummary}</p>
                            </div>
                            <span className="text-xs font-mono tabular-nums font-bold text-slate-900">
                              {Number(sub.subtotal).toLocaleString('fr-CD')} {sub.currency}
                            </span>
                          </div>

                          <p className="text-xs text-slate-600">
                            Préparation boutique :{' '}
                            <strong>
                              {ORDER_STATUS_LABELS[sub.status as OrderStatus] || sub.status}
                            </strong>
                            {del
                              ? ` · Livraison : ${
                                  DELIVERY_STATUS_LABELS[del.status as DeliveryStatus] || del.status
                                }`
                              : ''}
                            {sub.courierName ? ` · Livreur : ${sub.courierName}` : ''}
                          </p>

                          {secretDoc && (
                            <div className="rounded-lg bg-white border border-slate-200 px-3.5 py-2.5 flex items-center justify-between gap-3">
                              <div>
                                <p className="text-[11px] font-semibold text-slate-500 uppercase">
                                  Code unique de remise au livreur
                                </p>
                                <p className="text-[11px] text-slate-500">
                                  {del?.codeUsed
                                    ? 'Code vérifié et utilisé lors de la remise'
                                    : 'À communiquer uniquement à la réception physique'}
                                </p>
                              </div>
                              <span
                                className={`text-lg font-extrabold font-mono tracking-widest tabular-nums ${
                                  del?.codeUsed ? 'text-slate-400 line-through' : 'text-[#0F3D8A]'
                                }`}
                              >
                                {secretDoc.code}
                              </span>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>

                  {/* Évaluation des articles après réception confirmée */}
                  {ord.customerConfirmedReceipt && matchingItems.length > 0 && (
                    <div className="pt-3 border-t border-slate-100 space-y-3">
                      <p className="text-xs font-bold text-slate-700 uppercase">
                        Évaluer vos articles reçus
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {matchingItems.map((item) => (
                          <button
                            key={item.id}
                            type="button"
                            onClick={() =>
                              setReviewOrderTarget({
                                orderId: ord.id,
                                subOrderId: item.subOrderId,
                                productId: item.productId,
                                productTitle: item.productTitle,
                              })
                            }
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 hover:border-[#0F3D8A] min-h-[38px]"
                          >
                            <Star className="w-3.5 h-3.5 text-orange-500" />
                            <span>Noter « {item.productTitle} »</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Fenêtre modale d'évaluation vérifiée */}
        {reviewOrderTarget && (
          <div className="fixed inset-0 z-50 bg-slate-900/50 flex items-center justify-center p-4">
            <form
              onSubmit={handleSubmitReview}
              className="bg-white rounded-2xl border border-slate-200 max-w-md w-full p-6 space-y-4 shadow-xl"
            >
              <h3 className="text-base font-bold text-slate-900">
                Évaluer « {reviewOrderTarget.productTitle} »
              </h3>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Note globale (1 à 5 étoiles)
                </label>
                <div className="flex gap-2">
                  {[1, 2, 3, 4, 5].map((n) => (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setReviewRating(n)}
                      className={`w-11 h-11 rounded-xl border font-bold text-sm ${
                        reviewRating >= n
                          ? 'bg-orange-50 border-orange-500 text-orange-600'
                          : 'border-slate-200 text-slate-400'
                      }`}
                    >
                      {n}★
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Votre retour sur la qualité et la livraison
                </label>
                <textarea
                  rows={3}
                  required
                  value={reviewComment}
                  onChange={(e) => setReviewComment(e.target.value)}
                  placeholder="Article conforme à la description, bien emballé..."
                  className="w-full rounded-xl border border-slate-200 p-3 text-sm"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setReviewOrderTarget(null)}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 min-h-[42px]"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={submittingReview}
                  className="rounded-xl bg-[#0F3D8A] px-4 py-2 text-xs font-bold text-white min-h-[42px]"
                >
                  {submittingReview ? 'Publication...' : 'Publier mon avis'}
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // 7. PROFIL CLIENT & ADRESSES
  // =========================================================================
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8 space-y-8">
      <div className="border-b border-slate-200 pb-5">
        <p className="text-xs text-slate-500">Compte personnel sécurisé Firebase Authentication</p>
        <h1 className="text-2xl font-bold text-slate-900">Mon Profil Client</h1>
      </div>

      {!firebaseUser ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
          <User className="w-10 h-10 text-slate-400 mx-auto" />
          <p className="text-sm text-slate-700">
            Connectez-vous ou inscrivez-vous gratuitement en un clic pour gérer votre profil, vos adresses et vos commandes.
          </p>
          <button
            type="button"
            onClick={signInAndEnsureCustomer}
            className="rounded-xl bg-[#0F3D8A] px-5 py-3 text-sm font-bold text-white min-h-[46px]"
          >
            Inscription / Connexion Google
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
          <form
            onSubmit={handleSaveProfile}
            className="md:col-span-7 bg-white rounded-2xl border border-slate-200 p-6 space-y-4"
          >
            <h2 className="text-base font-bold text-slate-900">Informations personnelles</h2>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Email vérifié</label>
              <input
                type="email"
                disabled
                value={firebaseUser.email || ''}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500 min-h-[42px]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Nom et post-nom</label>
              <input
                type="text"
                required
                value={profName}
                onChange={(e) => setProfName(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Téléphone mobile (RDC)</label>
              <input
                type="tel"
                value={profPhone}
                onChange={(e) => setProfPhone(e.target.value)}
                placeholder="+243 99 000 0000"
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Ville principale</label>
              <select
                value={profCity}
                onChange={(e) => setProfCity(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              >
                <option value="Lubumbashi">Lubumbashi (Haut-Katanga)</option>
                <option value="Kolwezi">Kolwezi (Lualaba)</option>
                <option value="Likasi">Likasi (Haut-Katanga)</option>
                <option value="Kinshasa">Kinshasa</option>
              </select>
            </div>
            <button
              type="submit"
              disabled={savingProfile}
              className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-xs font-bold text-white hover:bg-[#0A2B63] min-h-[44px]"
            >
              {savingProfile ? 'Enregistrement...' : 'Enregistrer les modifications'}
            </button>
          </form>

          <div className="md:col-span-5 space-y-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
              <div className="flex items-center gap-2 text-[#0F3D8A]">
                <Store className="w-5 h-5" />
                <h3 className="text-sm font-bold text-slate-900">Espace Vendeur</h3>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Ouvrez gratuitement votre boutique sur Lubumarket. Aucune commission à la vente : 5 % uniquement lorsque vous demandez un retrait.
              </p>
              <button
                type="button"
                onClick={() => onOpenWorkspace('seller')}
                className="w-full rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-900 hover:bg-slate-50 min-h-[42px]"
              >
                Accéder à l’Espace Vendeur →
              </button>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
              <div className="flex items-center gap-2 text-orange-600">
                <MapPin className="w-5 h-5" />
                <h3 className="text-sm font-bold text-slate-900">Espace Livreur</h3>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Rejoignez l’équipe de livraison à Lubumbashi pour récupérer les colis en boutique et les remettre aux clients avec validation par code secret.
              </p>
              <button
                type="button"
                onClick={() => onOpenWorkspace('courier')}
                className="w-full rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-900 hover:bg-slate-50 min-h-[42px]"
              >
                Accéder à l’Espace Livreur →
              </button>
            </div>

            <div className="bg-slate-100 rounded-2xl p-5 space-y-1.5 text-xs text-slate-600">
              <div className="flex items-center gap-1.5 font-bold text-slate-800">
                <ShieldCheck className="w-4 h-4 text-[#0F3D8A]" />
                <span>Cloisonnement des permissions actif</span>
              </div>
              <p>
                Vos codes de livraison et votre historique privé ne sont jamais visibles par les autres acheteurs ni par les boutiques tierces.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
