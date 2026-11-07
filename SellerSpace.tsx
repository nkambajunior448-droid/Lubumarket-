import React, { useEffect, useMemo, useState } from 'react';
import {
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import {
  ArrowDownToLine,
  BookOpenCheck,
  Clock,
  Edit3,
  Lock,
  PackagePlus,
  RefreshCw,
  ShieldAlert,
  Store,
  Upload,
} from 'lucide-react';
import {
  LUBUMARKET_COMMISSION_PERCENT,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  SELLER_APPROVAL_LABELS,
  calculateWithdrawalBreakdown,
  canSellerPublishProduct,
  roundMoney,
  type ApprovalStatus,
  type CurrencyCode,
  type LogoStorageMode,
  type OrderStatus,
  type PaymentStatus,
} from '../shared/domain';
import {
  OperationType,
  apiPost,
  checkFirebaseStorageStatus,
  db,
  handleFirestoreError,
  makeClientId,
  uploadProductOrShopImage,
  type StorageStatusResponse,
} from '../lib/firebase';
import { PRESET_PRODUCT_GALLERY, SafeImage } from '../lib/catalogImages';
import { formatFsTimestamp, useLubumarket } from '../context/LubumarketContext';

type SellerTab = 'dashboard' | 'products' | 'orders' | 'wallet' | 'shop';

export const SellerSpace: React.FC = () => {
  const {
    firebaseUser,
    userDoc,
    sellerDoc,
    categories,
    deliveryZones,
    signInAndEnsureCustomer,
    notify,
  } = useLubumarket();

  const [activeTab, setActiveTab] = useState<SellerTab>('dashboard');

  // Données strictement isolées au vendeur connecté (sellerId == uid)
  const [myShop, setMyShop] = useState<Record<string, any> | null>(null);
  const [myApplications, setMyApplications] = useState<Array<Record<string, any>>>([]);
  const [myProducts, setMyProducts] = useState<Array<Record<string, any>>>([]);
  const [mySubOrders, setMySubOrders] = useState<Array<Record<string, any>>>([]);
  const [myWallet, setMyWallet] = useState<Record<string, any> | null>(null);
  const [myWithdrawals, setMyWithdrawals] = useState<Array<Record<string, any>>>([]);
  const [myCommissions, setMyCommissions] = useState<Array<Record<string, any>>>([]);
  const [myTransactions, setMyTransactions] = useState<Array<Record<string, any>>>([]);
  const [storageDiag, setStorageDiag] = useState<StorageStatusResponse | null>(null);

  // Formulaire inscription / édition boutique
  const [regShopName, setRegShopName] = useState('');
  const [regLogoUrl, setRegLogoUrl] = useState('/icon.svg');
  const [regLogoStorageMode, setRegLogoStorageMode] = useState<LogoStorageMode>('default_asset');
  const [logoUploadNote, setLogoUploadNote] = useState<string>('');
  const [regDescription, setRegDescription] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [regAddress, setRegAddress] = useState('');
  const [regZone, setRegZone] = useState('Lubumbashi Centre & Makomeno');
  const [regCity, setRegCity] = useState('Lubumbashi');
  const [submittingShop, setSubmittingShop] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);

  // Formulaire création / modification produit
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [prodTitle, setProdTitle] = useState('');
  const [prodCategoryId, setProdCategoryId] = useState('');
  const [prodDesc, setProdDesc] = useState('');
  const [prodPrice, setProdPrice] = useState('25');
  const [prodCurrency, setProdCurrency] = useState<CurrencyCode>('USD');
  const [prodStock, setProdStock] = useState('10');
  const [prodStatus, setProdStatus] = useState<'active' | 'draft'>('active');
  const [prodImages, setProdImages] = useState<string[]>([PRESET_PRODUCT_GALLERY[0].url]);
  const [uploadingProdImg, setUploadingProdImg] = useState(false);
  const [savingProduct, setSavingProduct] = useState(false);

  // Portefeuille & Retrait 5 %
  const [syncingWallet, setSyncingWallet] = useState(false);
  const [wdrCurrency, setWdrCurrency] = useState<CurrencyCode>('USD');
  const [wdrAmount, setWdrAmount] = useState('');
  const [wdrChannel, setWdrChannel] = useState('M-Pesa Vodacom RDC');
  const [wdrRecipient, setWdrRecipient] = useState('');
  const [submittingWdr, setSubmittingWdr] = useState(false);
  const [updatingSubId, setUpdatingSubId] = useState<string | null>(null);

  useEffect(() => {
    if (categories.length > 0 && !prodCategoryId) {
      setProdCategoryId(categories[0].id);
    }
  }, [categories, prodCategoryId]);

  useEffect(() => {
    if (!firebaseUser) return;
    checkFirebaseStorageStatus()
      .then((st) => setStorageDiag(st))
      .catch(() => {});
  }, [firebaseUser]);

  useEffect(() => {
    if (sellerDoc && myShop) {
      setRegShopName(myShop.name || sellerDoc.shopName || '');
      const currentLogo = myShop.logoUrl || '/icon.svg';
      setRegLogoUrl(currentLogo);
      if (currentLogo.startsWith('https://firebasestorage.googleapis.com/')) {
        setRegLogoStorageMode('firebase_storage');
      } else if (currentLogo.startsWith('data:image/')) {
        setRegLogoStorageMode('firestore_inline_fallback');
      } else {
        setRegLogoStorageMode('default_asset');
      }
      setRegDescription(myShop.description || '');
      setRegPhone(myShop.phone || sellerDoc.phone || '');
      setRegAddress(myShop.address || sellerDoc.address || '');
      setRegZone(myShop.activityZone || sellerDoc.activityZone || 'Lubumbashi Centre & Makomeno');
      setRegCity(myShop.city || sellerDoc.city || 'Lubumbashi');
    }
  }, [sellerDoc, myShop]);

  useEffect(() => {
    if (!firebaseUser || !sellerDoc) {
      setMyShop(null);
      setMyApplications([]);
      setMyProducts([]);
      setMySubOrders([]);
      setMyWallet(null);
      setMyWithdrawals([]);
      setMyCommissions([]);
      setMyTransactions([]);
      return;
    }

    const uid = firebaseUser.uid;

    const unsubShop = onSnapshot(
      doc(db, 'shops', sellerDoc.shopId),
      (snap) => setMyShop(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `shops/${sellerDoc.shopId}`)
    );

    const unsubApps = onSnapshot(
      query(collection(db, 'sellerApplications'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyApplications(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'sellerApplications')
    );

    const unsubProds = onSnapshot(
      query(collection(db, 'products'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyProducts(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'products')
    );

    const unsubSubs = onSnapshot(
      query(collection(db, 'sellerSubOrders'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMySubOrders(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'sellerSubOrders')
    );

    const unsubWallet = onSnapshot(
      doc(db, 'wallets', uid),
      (snap) => setMyWallet(snap.exists() ? snap.data() : null),
      (err) => handleFirestoreError(err, OperationType.GET, `wallets/${uid}`)
    );

    const unsubWdr = onSnapshot(
      query(collection(db, 'withdrawals'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyWithdrawals(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'withdrawals')
    );

    const unsubComm = onSnapshot(
      query(collection(db, 'commissions'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyCommissions(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'commissions')
    );

    const unsubTx = onSnapshot(
      query(collection(db, 'walletTransactions'), where('sellerId', '==', uid)),
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyTransactions(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'walletTransactions')
    );

    return () => {
      unsubShop();
      unsubApps();
      unsubProds();
      unsubSubs();
      unsubWallet();
      unsubWdr();
      unsubComm();
      unsubTx();
    };
  }, [firebaseUser, sellerDoc]);

  const handleSyncWallet = async (silent = false) => {
    if (!firebaseUser || !sellerDoc) return;
    setSyncingWallet(true);
    try {
      await apiPost('/api/wallets/sync', { sellerId: firebaseUser.uid });
      if (!silent) {
        notify('Registre comptable synchronisé avec vos livraisons vérifiées.', 'success');
      }
    } catch (err) {
      if (!silent) {
        notify(err instanceof Error ? err.message : 'Erreur synchronisation portefeuille.', 'error');
      }
    } finally {
      setSyncingWallet(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'wallet' && sellerDoc?.approvalStatus === 'approved') {
      handleSyncWallet(true);
    }
  }, [activeTab, sellerDoc?.approvalStatus]);

  const handleUploadShopLogo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingLogo(true);
    try {
      const res = await uploadProductOrShopImage(file, 'shops');
      setRegLogoUrl(res.url);
      setRegLogoStorageMode(res.storageMode);
      setLogoUploadNote(res.diagnosticMessage);
      notify(
        res.diagnosticMessage,
        res.storageMode === 'firebase_storage' ? 'success' : 'info'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Échec du traitement du logo.', 'error');
    } finally {
      setUploadingLogo(false);
    }
  };

  const handleRegisterSeller = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser) {
      await signInAndEnsureCustomer();
      return;
    }
    if (regShopName.trim().length < 2 || regPhone.trim().length < 6 || regAddress.trim().length < 4) {
      notify('Veuillez renseigner le nom de la boutique, le téléphone et l’adresse complète.', 'error');
      return;
    }

    setSubmittingShop(true);
    try {
      const res = await apiPost<{
        ok: boolean;
        verifiedInFirestore: boolean;
        applicationId: string;
        shopId: string;
        status: string;
        statusLabel: string;
      }>('/api/sellers/register', {
        shopName: regShopName.trim(),
        logoUrl: regLogoUrl || '/icon.svg',
        logoStorageMode: regLogoStorageMode,
        description: regDescription.trim(),
        phone: regPhone.trim(),
        address: regAddress.trim(),
        activityZone: regZone,
        city: regCity,
      });

      notify(
        `Demande enregistrée dans Firestore (Réf. ${res.applicationId}) — Statut : « ${res.statusLabel} ».`,
        'success'
      );
    } catch (err) {
      notify(
        err instanceof Error ? err.message : 'Erreur lors de l’enregistrement de la demande vendeur.',
        'error'
      );
    } finally {
      setSubmittingShop(false);
    }
  };

  const handleUpdateShopProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser || !sellerDoc || !myShop) return;
    setSubmittingShop(true);
    try {
      const batch = writeBatch(db);
      batch.update(doc(db, 'shops', sellerDoc.shopId), {
        name: regShopName.trim().slice(0, 120),
        logoUrl: (regLogoUrl || '/icon.svg').slice(0, 89500),
        description: regDescription.trim().slice(0, 950),
        phone: regPhone.trim().slice(0, 32),
        address: regAddress.trim().slice(0, 235),
        activityZone: regZone.slice(0, 100),
        city: regCity.slice(0, 80),
        updatedAt: serverTimestamp(),
      });
      batch.update(doc(db, 'sellers', firebaseUser.uid), {
        shopName: regShopName.trim().slice(0, 120),
        phone: regPhone.trim().slice(0, 32),
        address: regAddress.trim().slice(0, 235),
        activityZone: regZone.slice(0, 100),
        city: regCity.slice(0, 80),
        updatedAt: serverTimestamp(),
      });
      await batch.commit();
      notify('Informations de votre boutique mises à jour dans Firestore.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `shops/${sellerDoc.shopId}`);
    } finally {
      setSubmittingShop(false);
    }
  };

  const handleUploadProductPhoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingProdImg(true);
    try {
      const res = await uploadProductOrShopImage(file, 'products');
      setProdImages((prev) => [res.url, ...prev.filter((x) => x !== res.url)].slice(0, 5));
      notify(
        res.diagnosticMessage,
        res.storageMode === 'firebase_storage' ? 'success' : 'info'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur téléversement image.', 'error');
    } finally {
      setUploadingProdImg(false);
    }
  };

  const resetProductForm = () => {
    setEditingProductId(null);
    setProdTitle('');
    setProdDesc('');
    setProdPrice('25');
    setProdCurrency('USD');
    setProdStock('10');
    setProdStatus('active');
    setProdImages([PRESET_PRODUCT_GALLERY[0].url]);
  };

  const startEditProduct = (p: Record<string, any>) => {
    setEditingProductId(p.id);
    setProdTitle(p.title || '');
    setProdCategoryId(p.categoryId || (categories[0]?.id ?? ''));
    setProdDesc(p.description || '');
    setProdPrice(String(p.price ?? 10));
    setProdCurrency(p.currency === 'CDF' ? 'CDF' : 'USD');
    setProdStock(String(p.stock ?? 0));
    setProdStatus(p.status === 'draft' ? 'draft' : 'active');
    setProdImages(Array.isArray(p.imageUrls) && p.imageUrls.length > 0 ? p.imageUrls : [PRESET_PRODUCT_GALLERY[0].url]);
    setActiveTab('products');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleSaveProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser || !sellerDoc) return;

    const eligibility = canSellerPublishProduct(sellerDoc, myShop, firebaseUser.uid);
    if (!eligibility.allowed) {
      notify(
        eligibility.reason ||
          'Votre boutique doit être approuvée par l’administrateur avant toute publication de produit.',
        'error'
      );
      return;
    }

    const numericPrice = roundMoney(Number(prodPrice), prodCurrency);
    const numericStock = Math.max(0, Math.floor(Number(prodStock)));
    const cat = categories.find((c) => c.id === prodCategoryId) || categories[0];

    if (prodTitle.trim().length < 2 || numericPrice <= 0 || !cat) {
      notify('Veuillez vérifier le titre, la catégorie et le prix (> 0).', 'error');
      return;
    }

    setSavingProduct(true);
    try {
      const res = await apiPost<{ ok: boolean; productId: string; updated?: boolean }>(
        '/api/products/save',
        {
          productId: editingProductId || undefined,
          categoryId: cat.id,
          categoryName: cat.name,
          title: prodTitle.trim(),
          description: prodDesc.trim(),
          price: numericPrice,
          currency: prodCurrency,
          stock: numericStock,
          imageUrls: prodImages.slice(0, 5),
          status: prodStatus,
        }
      );

      notify(
        res.updated
          ? `Produit ${res.productId} mis à jour dans Firestore.`
          : `Produit publié dans Firestore (Réf. ${res.productId} · 0 % commission à la publication).`,
        'success'
      );
      resetProductForm();
    } catch (err) {
      notify(
        err instanceof Error ? err.message : 'Publication refusée par le serveur ou Firestore.',
        'error'
      );
    } finally {
      setSavingProduct(false);
    }
  };

  const handleUpdateSubOrderStatus = async (
    sub: Record<string, any>,
    nextStatus: 'accepted' | 'preparing' | 'ready_for_pickup'
  ) => {
    setUpdatingSubId(sub.id);
    try {
      await updateDoc(doc(db, 'sellerSubOrders', sub.id), {
        status: nextStatus,
        updatedAt: serverTimestamp(),
      });
      notify(`Commande ${sub.orderNumber} passée à « ${ORDER_STATUS_LABELS[nextStatus]} ».`, 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `sellerSubOrders/${sub.id}`);
    } finally {
      setUpdatingSubId(null);
    }
  };

  const availableForSelectedWdr =
    wdrCurrency === 'USD'
      ? Number(myWallet?.availableBalanceUSD || 0)
      : Number(myWallet?.availableBalanceCDF || 0);

  const liveWithdrawalPreview = useMemo(() => {
    const val = Number(wdrAmount);
    if (!Number.isFinite(val) || val <= 0) return null;
    try {
      return calculateWithdrawalBreakdown(val, Math.max(val, availableForSelectedWdr), wdrCurrency);
    } catch {
      return null;
    }
  }, [wdrAmount, availableForSelectedWdr, wdrCurrency]);

  const handleRequestWithdrawal = async (e: React.FormEvent) => {
    e.preventDefault();
    const numericAmt = Number(wdrAmount);
    if (!Number.isFinite(numericAmt) || numericAmt <= 0) {
      notify('Veuillez saisir un montant de retrait valide.', 'error');
      return;
    }
    if (numericAmt > availableForSelectedWdr) {
      notify(
        `Solde disponible insuffisant (${availableForSelectedWdr.toLocaleString('fr-CD')} ${wdrCurrency} disponible).`,
        'error'
      );
      return;
    }
    if (wdrRecipient.trim().length < 4) {
      notify('Veuillez indiquer le numéro ou compte bénéficiaire.', 'error');
      return;
    }

    setSubmittingWdr(true);
    try {
      const idempotencyKey = makeClientId('idem-wdr');
      const res = await apiPost<{
        ok: boolean;
        breakdown: {
          grossAmount: number;
          commissionAmount: number;
          netAmount: number;
          currency: CurrencyCode;
        };
      }>('/api/withdrawals/request', {
        idempotencyKey,
        currency: wdrCurrency,
        amount: numericAmt,
        payoutChannel: wdrChannel,
        payoutRecipient: wdrRecipient.trim(),
      });
      setWdrAmount('');
      notify(
        `Demande enregistrée : ${res.breakdown.grossAmount} ${res.breakdown.currency} · Commission 5 % : ${res.breakdown.commissionAmount} ${res.breakdown.currency} · Net : ${res.breakdown.netAmount} ${res.breakdown.currency}.`,
        'success'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur lors de la demande de retrait.', 'error');
    } finally {
      setSubmittingWdr(false);
    }
  };

  if (!firebaseUser) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-12">
        <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
          <Store className="w-12 h-12 text-[#0F3D8A] mx-auto" />
          <h1 className="text-2xl font-bold text-slate-900">
            Espace Vendeur Lubumarket — Inscription Gratuite
          </h1>
          <p className="text-sm text-slate-600 max-w-xl mx-auto">
            Créez votre boutique à Lubumbashi et vendez en USD ou CDF. Lubumarket ne prélève aucune commission lors de la commande : <strong>5 % uniquement lorsque vous demandez un retrait</strong>.
          </p>
          <button
            type="button"
            onClick={signInAndEnsureCustomer}
            className="rounded-xl bg-[#0F3D8A] px-6 py-3 text-sm font-bold text-white hover:bg-[#0A2B63] min-h-[46px]"
          >
            Se connecter avec Google pour ouvrir ma boutique
          </button>
        </div>
      </div>
    );
  }

  const renderShopRegistrationForm = (isResubmission = false) => (
    <form
      onSubmit={handleRegisterSeller}
      className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-5"
    >
      {isResubmission && (
        <div className="pb-4 border-b border-slate-100 space-y-1">
          <h2 className="text-base font-bold text-slate-900">
            Corriger et soumettre une nouvelle demande de validation
          </h2>
          <p className="text-xs text-slate-600">
            Une nouvelle référence unique de demande sera générée dans Cloud Firestore avec le statut « En attente de validation ».
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="sm:col-span-2">
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Nom commercial de la boutique *
          </label>
          <input
            type="text"
            required
            value={regShopName}
            onChange={(e) => setRegShopName(e.target.value)}
            placeholder="Ex: Katanga Tech Store, Maison Kasapa..."
            className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Téléphone professionnel (RDC) *
          </label>
          <input
            type="tel"
            required
            value={regPhone}
            onChange={(e) => setRegPhone(e.target.value)}
            placeholder="+243 97 000 0000"
            className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Zone d’activité principale *
          </label>
          <select
            value={regZone}
            onChange={(e) => setRegZone(e.target.value)}
            className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
          >
            {deliveryZones.map((z) => (
              <option key={z.id} value={z.name}>
                {z.name} ({z.city})
              </option>
            ))}
          </select>
        </div>

        <div className="sm:col-span-2">
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Adresse physique de récupération des marchandises (pour les livreurs) *
          </label>
          <input
            type="text"
            required
            value={regAddress}
            onChange={(e) => setRegAddress(e.target.value)}
            placeholder="Ex: Av. Lomami n°18, Centre-ville, Lubumbashi"
            className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
          />
        </div>

        <div className="sm:col-span-2">
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Description de la boutique et spécialités
          </label>
          <textarea
            rows={3}
            value={regDescription}
            onChange={(e) => setRegDescription(e.target.value)}
            placeholder="Présentez vos produits, garanties et horaires d’ouverture..."
            className="w-full rounded-xl border border-slate-200 p-3.5 text-sm"
          />
        </div>

        <div className="sm:col-span-2 space-y-2">
          <label className="block text-xs font-semibold text-slate-700">
            Logo de la boutique (téléversement ou visuel par défaut)
          </label>
          <div className="flex flex-wrap items-center gap-4">
            <div className="w-16 h-16 rounded-xl border border-slate-200 bg-slate-50 overflow-hidden shrink-0">
              <SafeImage src={regLogoUrl} alt="Logo boutique" className="w-full h-full object-cover" />
            </div>
            <label className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 cursor-pointer min-h-[44px]">
              <Upload className="w-4 h-4 text-[#0F3D8A]" />
              <span>{uploadingLogo ? 'Traitement de l’image...' : 'Choisir un logo (PNG, JPG, WebP · max 4 Mo)'}</span>
              <input
                type="file"
                accept="image/*"
                onChange={handleUploadShopLogo}
                className="hidden"
              />
            </label>
          </div>
          {logoUploadNote ? (
            <p className="text-xs text-slate-600">{logoUploadNote}</p>
          ) : storageDiag && !storageDiag.operational ? (
            <p className="text-xs text-amber-800">
              Diagnostic stockage : le bucket Firebase Storage (<span className="font-mono">{storageDiag.bucket}</span>) n’est pas encore initialisé dans la console Firebase (HTTP {storageDiag.httpStatus || 404}). Tout logo téléversé sera compressé et enregistré de façon persistante dans Cloud Firestore jusqu’à l’activation du bucket.
            </p>
          ) : null}
        </div>
      </div>

      <button
        type="submit"
        disabled={submittingShop || uploadingLogo}
        className="w-full rounded-xl bg-orange-600 py-3.5 text-sm font-bold text-white hover:bg-orange-700 disabled:opacity-50 min-h-[48px]"
      >
        {submittingShop
          ? 'Enregistrement dans Cloud Firestore...'
          : isResubmission
            ? 'Soumettre ma nouvelle demande de validation'
            : 'Soumettre ma boutique gratuitement'}
      </button>
    </form>
  );

  // Formulaire d'ouverture gratuite de boutique si pas encore inscrit vendeur
  if (!sellerDoc) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-10 space-y-6">
        <div className="border-b border-slate-200 pb-5">
          <p className="text-xs font-semibold text-orange-600 uppercase">
            Ouverture 100 % gratuite · 5 % uniquement au retrait
          </p>
          <h1 className="mt-1 text-2xl sm:text-3xl font-bold text-slate-900">
            Créer votre boutique sur Lubumarket
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Renseignez les coordonnées de votre commerce à Lubumbashi. Votre demande recevra un identifiant unique avec le statut « En attente de validation » jusqu’à décision de l’administrateur propriétaire.
          </p>
        </div>

        {renderShopRegistrationForm(false)}
      </div>
    );
  }

  const publishEligibility = canSellerPublishProduct(sellerDoc, myShop, firebaseUser.uid);
  const isApproved = publishEligibility.allowed;
  const currentApprovalStatus = (sellerDoc.approvalStatus || 'pending') as ApprovalStatus;
  const currentStatusLabel =
    sellerDoc.statusLabel ||
    SELLER_APPROVAL_LABELS[currentApprovalStatus] ||
    'En attente de validation';
  const latestApp = myApplications[0] || null;
  const displayedApplicationId =
    sellerDoc.applicationId || latestApp?.id || sellerDoc.shopId;

  // =========================================================================
  // VERROUILLAGE STRICT AVANT APPROBATION : TABLEAU DE BORD & PUBLICATION BLOQUÉS
  // =========================================================================
  if (sellerDoc.approvalStatus !== 'approved' || (myShop && myShop.status !== 'active')) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-10 space-y-8">
        <div className="border-b border-slate-200 pb-5 flex flex-col sm:flex-row sm:items-end justify-between gap-4">
          <div className="space-y-1">
            <p className="text-xs text-slate-500">
              Espace Vendeur · {sellerDoc.city || 'Lubumbashi'} · Statut actuel :{' '}
              <strong
                className={
                  currentApprovalStatus === 'pending'
                    ? 'text-amber-700'
                    : 'text-red-700'
                }
              >
                {currentStatusLabel}
              </strong>
            </p>
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900">
              {myShop?.name || sellerDoc.shopName}
            </h1>
          </div>
          <div className="text-xs text-slate-600 font-mono tabular-nums">
            Réf. demande : <strong className="text-slate-900">{displayedApplicationId}</strong>
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-6">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded-xl border border-slate-200 bg-slate-50 overflow-hidden shrink-0">
              <SafeImage
                src={myShop?.logoUrl || latestApp?.logoUrl || '/icon.svg'}
                alt={sellerDoc.shopName}
                className="w-full h-full object-cover"
              />
            </div>
            <div className="space-y-1 min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                <span>Statut : <strong className="text-slate-900">{currentStatusLabel}</strong></span>
                <span aria-hidden="true">·</span>
                <span className="font-mono">ID Boutique : {sellerDoc.shopId}</span>
                {formatFsTimestamp(sellerDoc.createdAt) && (
                  <>
                    <span aria-hidden="true">·</span>
                    <span>Enregistrée le {formatFsTimestamp(sellerDoc.createdAt)}</span>
                  </>
                )}
              </div>
              <h2 className="text-lg font-bold text-slate-900">
                {currentApprovalStatus === 'pending'
                  ? 'Votre demande d’inscription vendeur est enregistrée dans Cloud Firestore'
                  : currentApprovalStatus === 'rejected'
                    ? 'Votre demande d’ouverture de boutique a été refusée'
                    : 'Votre boutique est actuellement suspendue'}
              </h2>
              <p className="text-sm text-slate-600 leading-relaxed">
                {currentApprovalStatus === 'pending'
                  ? 'L’accès au tableau de bord vendeur et à la publication de produits est verrouillé tant que l’administrateur propriétaire n’a pas approuvé votre boutique. Dès son approbation, cet écran déverrouillera automatiquement votre tableau de bord en temps réel.'
                  : currentApprovalStatus === 'rejected'
                    ? 'L’administrateur a examiné votre dossier et indiqué le motif ci-dessous. Vous pouvez corriger vos informations et soumettre une nouvelle demande gratuitement.'
                    : 'La publication de produits et la réception de nouvelles commandes sont désactivées pour cette boutique.'}
              </p>
            </div>
          </div>

          {sellerDoc.rejectionReason && currentApprovalStatus !== 'pending' && (
            <div className="rounded-xl bg-red-50 border border-red-200 p-4 text-xs text-red-900 space-y-1">
              <p className="font-bold flex items-center gap-1.5">
                <ShieldAlert className="w-4 h-4 text-red-700" />
                <span>Motif communiqué par l’administrateur :</span>
              </p>
              <p className="leading-relaxed">{sellerDoc.rejectionReason}</p>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-4 border-t border-slate-100 text-xs">
            <div className="space-y-1">
              <p className="text-slate-500">Identifiant unique de la demande</p>
              <p className="font-mono font-bold text-slate-900">{displayedApplicationId}</p>
            </div>
            <div className="space-y-1">
              <p className="text-slate-500">Statut officiel du dossier</p>
              <p className="font-bold text-slate-900 flex items-center gap-1.5">
                {currentApprovalStatus === 'pending' ? (
                  <Clock className="w-3.5 h-3.5 text-amber-600" />
                ) : (
                  <Lock className="w-3.5 h-3.5 text-red-600" />
                )}
                <span>{currentStatusLabel}</span>
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-slate-500">Coordonnées & Zone d’activité</p>
              <p className="font-medium text-slate-900">
                {sellerDoc.phone} · {sellerDoc.activityZone} ({sellerDoc.city})
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-slate-500">Adresse physique de retrait livreur</p>
              <p className="font-medium text-slate-900">{sellerDoc.address}</p>
            </div>
            {myShop?.description && (
              <div className="sm:col-span-2 space-y-1">
                <p className="text-slate-500">Description enregistrée dans Firestore</p>
                <p className="text-slate-800 leading-relaxed">{myShop.description}</p>
              </div>
            )}
            <div className="sm:col-span-2 space-y-1">
              <p className="text-slate-500">Mode de stockage du logo enregistré</p>
              <p className="font-mono text-slate-700">
                {(latestApp?.logoStorageMode || regLogoStorageMode) === 'firebase_storage'
                  ? 'Firebase Cloud Storage (URL HTTPS vérifiée)'
                  : (latestApp?.logoStorageMode || regLogoStorageMode) === 'firestore_inline_fallback'
                    ? 'Cloud Firestore (image compressée persistée — bucket Firebase Storage non encore initialisé)'
                    : 'Icône Lubumarket par défaut (/icon.svg)'}
              </p>
            </div>
          </div>

          <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 flex items-center justify-between gap-4 text-xs text-slate-700">
            <div className="flex items-center gap-2.5">
              <Lock className="w-4 h-4 text-slate-600 shrink-0" />
              <span>
                <strong>Publication de produits désactivée :</strong> sécurité appliquée simultanément dans l’interface, l’API serveur (<span className="font-mono">/api/products/save</span>) et les règles Cloud Firestore (<span className="font-mono">isApprovedSellerWithActiveShop</span>).
              </span>
            </div>
          </div>
        </div>

        {currentApprovalStatus === 'rejected' && renderShopRegistrationForm(true)}

        {myApplications.length > 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
            <h3 className="text-sm font-bold text-slate-900">
              Historique de mes demandes d’inscription vendeur ({myApplications.length})
            </h3>
            <div className="divide-y divide-slate-100 text-xs">
              {myApplications.map((appItem) => (
                <div key={appItem.id} className="py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="space-y-0.5">
                    <p className="font-mono font-bold text-slate-900">
                      {appItem.id} · {appItem.shopName}
                    </p>
                    <p className="text-slate-500">
                      Soumise le {formatFsTimestamp(appItem.createdAt) || 'à l’instant'}
                      {appItem.rejectionReason ? ` · Motif : ${appItem.rejectionReason}` : ''}
                    </p>
                  </div>
                  <span className="font-bold text-slate-900">{appItem.statusLabel}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-8">
      {/* En-tête Espace Vendeur (uniquement accessible après approbation) */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-slate-200 pb-5">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-xl border border-slate-200 bg-white overflow-hidden shrink-0">
            <SafeImage src={myShop?.logoUrl || '/icon.svg'} alt={sellerDoc.shopName} className="w-full h-full object-cover" />
          </div>
          <div>
            <p className="text-xs text-slate-500">
              Espace Vendeur · {sellerDoc.city} · Réf. <span className="font-mono">{displayedApplicationId}</span> · Statut :{' '}
              <strong className="text-emerald-700">Boutique approuvée et active</strong>
            </p>
            <h1 className="text-2xl font-bold text-slate-900">{myShop?.name || sellerDoc.shopName}</h1>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {(
            [
              { id: 'dashboard', label: 'Tableau de bord' },
              { id: 'products', label: `Produits (${myProducts.length})` },
              { id: 'orders', label: `Commandes (${mySubOrders.length})` },
              { id: 'wallet', label: 'Portefeuille & Retraits (5 %)' },
              { id: 'shop', label: 'Ma Boutique' },
            ] as Array<{ id: SellerTab; label: string }>
          ).map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setActiveTab(t.id)}
              className={`rounded-xl px-3.5 py-2 text-xs font-bold min-h-[40px] border transition-colors ${
                activeTab === t.id
                  ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                  : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* ONGLET 1 : TABLEAU DE BORD VENDEUR */}
      {activeTab === 'dashboard' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Solde disponible (USD / CDF)</p>
              <p className="text-xl font-extrabold text-slate-900 font-mono tabular-nums">
                {Number(myWallet?.availableBalanceUSD || 0).toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {Number(myWallet?.availableBalanceCDF || 0).toLocaleString('fr-CD')} FC
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Solde en attente de livraison/paiement</p>
              <p className="text-xl font-extrabold text-slate-900 font-mono tabular-nums">
                {Number(myWallet?.pendingBalanceUSD || 0).toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {Number(myWallet?.pendingBalanceCDF || 0).toLocaleString('fr-CD')} FC
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Total des ventes livrées & payées</p>
              <p className="text-xl font-extrabold text-slate-900 font-mono tabular-nums">
                {Number(myWallet?.totalSalesUSD || 0).toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {Number(myWallet?.totalSalesCDF || 0).toLocaleString('fr-CD')} FC
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Commissions prélevées (5 % au retrait)</p>
              <p className="text-xl font-extrabold text-[#0F3D8A] font-mono tabular-nums">
                {Number(myWallet?.totalCommissionsUSD || 0).toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {Number(myWallet?.totalCommissionsCDF || 0).toLocaleString('fr-CD')} FC
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-slate-900">Dernières commandes boutique</h2>
                <button
                  type="button"
                  onClick={() => setActiveTab('orders')}
                  className="text-xs font-bold text-[#0F3D8A] hover:underline"
                >
                  Gérer toutes les commandes →
                </button>
              </div>
              {mySubOrders.length === 0 ? (
                <p className="text-xs text-slate-500 py-4">Aucune commande reçue pour le moment.</p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {mySubOrders.slice(0, 5).map((sub) => (
                    <div key={sub.id} className="py-3 flex items-center justify-between gap-3 text-xs">
                      <div>
                        <p className="font-bold text-slate-900">
                          {sub.orderNumber} · {sub.customerName}
                        </p>
                        <p className="text-slate-500">{sub.itemsSummary}</p>
                      </div>
                      <div className="text-right font-mono tabular-nums">
                        <p className="font-bold text-slate-900">
                          {Number(sub.subtotal).toLocaleString('fr-CD')} {sub.currency}
                        </p>
                        <p className="text-slate-500">
                          {ORDER_STATUS_LABELS[sub.status as OrderStatus] || sub.status}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
              <div className="flex items-center gap-2 text-[#0F3D8A]">
                <BookOpenCheck className="w-5 h-5" />
                <h2 className="text-base font-bold text-slate-900">
                  Fonctionnement de la commission unique de {LUBUMARKET_COMMISSION_PERCENT} %
                </h2>
              </div>
              <ul className="space-y-2 text-xs text-slate-600 leading-relaxed list-disc list-inside">
                <li>Publication illimitée de produits : <strong>0 USD / 0 FC</strong>.</li>
                <li>À chaque commande client : <strong>0 % prélevé</strong>.</li>
                <li>Commande annulée ou non livrée : <strong>0 % prélevé</strong>.</li>
                <li>
                  Lorsque la commande est livrée (code client validé) et payée, <strong>100 % du prix produit</strong> entre dans votre solde disponible.
                </li>
                <li>
                  Uniquement lors d’une demande de retrait : <strong>5 % de commission Lubumarket</strong> et <strong>95 % nets pour vous</strong> (retrait total ou partiel).
                </li>
              </ul>
            </div>
          </div>
        </div>
      )}

      {/* ONGLET 2 : PUBLICATION & MODIFICATION DE PRODUITS */}
      {activeTab === 'products' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <form
            onSubmit={handleSaveProduct}
            className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-6 space-y-4"
          >
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <PackagePlus className="w-4 h-4 text-[#0F3D8A]" />
                <span>{editingProductId ? 'Modifier le produit' : 'Publier un produit'}</span>
              </h2>
              {editingProductId && (
                <button
                  type="button"
                  onClick={resetProductForm}
                  className="text-xs font-semibold text-slate-500 hover:text-slate-900"
                >
                  Annuler l’édition
                </button>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Nom de l’article *</label>
              <input
                type="text"
                required
                value={prodTitle}
                onChange={(e) => setProdTitle(e.target.value)}
                placeholder="Ex: Samsung Galaxy A55 256 Go Dual SIM"
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Catégorie *</label>
              <select
                value={prodCategoryId}
                onChange={(e) => setProdCategoryId(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              >
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Prix *</label>
                <input
                  type="number"
                  step="any"
                  min="0.1"
                  required
                  value={prodPrice}
                  onChange={(e) => setProdPrice(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-mono min-h-[42px]"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Devise *</label>
                <select
                  value={prodCurrency}
                  onChange={(e) => setProdCurrency(e.target.value as CurrencyCode)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-bold min-h-[42px]"
                >
                  <option value="USD">USD ($)</option>
                  <option value="CDF">CDF (FC)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Stock *</label>
                <input
                  type="number"
                  min="0"
                  step="1"
                  required
                  value={prodStock}
                  onChange={(e) => setProdStock(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-mono min-h-[42px]"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Description détaillée</label>
              <textarea
                rows={3}
                value={prodDesc}
                onChange={(e) => setProdDesc(e.target.value)}
                placeholder="Caractéristiques techniques, état neuf, garantie..."
                className="w-full rounded-xl border border-slate-200 p-3 text-sm"
              />
            </div>

            <div className="space-y-2">
              <label className="block text-xs font-semibold text-slate-700">
                Photos du produit (Firebase Storage ou galerie studio)
              </label>
              <div className="flex items-center gap-3">
                <div className="w-16 h-16 rounded-xl bg-slate-100 border border-slate-200 overflow-hidden shrink-0">
                  <SafeImage src={prodImages[0]} alt="Aperçu produit" className="w-full h-full object-cover" />
                </div>
                <label className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 cursor-pointer min-h-[40px]">
                  <Upload className="w-3.5 h-3.5 text-[#0F3D8A]" />
                  <span>{uploadingProdImg ? 'Envoi...' : 'Téléverser une photo'}</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handleUploadProductPhoto}
                    className="hidden"
                  />
                </label>
              </div>

              <div className="grid grid-cols-2 gap-2 pt-1">
                {PRESET_PRODUCT_GALLERY.map((preset) => (
                  <button
                    key={preset.label}
                    type="button"
                    onClick={() => setProdImages([preset.url])}
                    className={`text-left rounded-lg border px-2.5 py-1.5 text-[11px] font-medium truncate ${
                      prodImages[0] === preset.url
                        ? 'border-[#0F3D8A] bg-blue-50 text-[#0F3D8A]'
                        : 'border-slate-200 text-slate-600'
                    }`}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Visibilité catalogue</label>
              <select
                value={prodStatus}
                onChange={(e) => setProdStatus(e.target.value as 'active' | 'draft')}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              >
                <option value="active">Publié dans le catalogue actif</option>
                <option value="draft">Brouillon (masqué aux clients)</option>
              </select>
            </div>

            <button
              type="submit"
              disabled={savingProduct || !isApproved}
              className="w-full rounded-xl bg-[#0F3D8A] py-3 text-sm font-bold text-white hover:bg-[#0A2B63] disabled:opacity-50 min-h-[46px]"
            >
              {savingProduct
                ? 'Enregistrement...'
                : editingProductId
                  ? 'Mettre à jour le produit'
                  : 'Publier dans ma boutique'}
            </button>
          </form>

          {/* Liste des produits de la boutique */}
          <div className="lg:col-span-7 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Mes produits en boutique ({myProducts.length})
            </h2>
            {myProducts.length === 0 ? (
              <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center text-sm text-slate-500">
                Aucun produit dans votre boutique pour le moment.
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {myProducts.map((p) => (
                  <div
                    key={p.id}
                    className="bg-white rounded-2xl border border-slate-200 p-4 flex flex-col justify-between gap-3"
                  >
                    <div className="flex gap-3">
                      <div className="w-20 h-20 rounded-xl bg-slate-100 overflow-hidden shrink-0">
                        <SafeImage src={p.imageUrls?.[0]} alt={p.title} className="w-full h-full object-cover" />
                      </div>
                      <div className="min-w-0 flex-1 space-y-1">
                        <p className="text-xs text-slate-500">
                          {p.categoryName} · {p.status === 'active' ? 'Actif' : 'Brouillon'}
                        </p>
                        <h3 className="text-sm font-bold text-slate-900 line-clamp-2">{p.title}</h3>
                        <p className="text-sm font-extrabold font-mono tabular-nums text-[#0F3D8A]">
                          {Number(p.price).toLocaleString('fr-CD')} {p.currency}
                        </p>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs">
                      <span className="text-slate-600 font-mono tabular-nums">
                        Stock : {p.stock} · Vendus : {p.soldCount || 0}
                      </span>
                      <button
                        type="button"
                        onClick={() => startEditProduct(p)}
                        className="inline-flex items-center gap-1 font-bold text-[#0F3D8A] hover:underline"
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                        <span>Modifier</span>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ONGLET 3 : GESTION DES COMMANDES BOUTIQUE */}
      {activeTab === 'orders' && (
        <div className="space-y-4">
          <h2 className="text-base font-bold text-slate-900">
            Commandes clients adressées à ma boutique ({mySubOrders.length})
          </h2>
          {mySubOrders.length === 0 ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center text-sm text-slate-500">
              Aucune commande enregistrée pour votre boutique.
            </div>
          ) : (
            <div className="space-y-4">
              {mySubOrders.map((sub) => (
                <div
                  key={sub.id}
                  className="bg-white rounded-2xl border border-slate-200 p-5 flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="space-y-1">
                    <p className="text-xs text-slate-500">
                      {formatFsTimestamp(sub.createdAt) || 'Récent'} · Client : {sub.customerName} · Zone : {sub.zoneName}
                    </p>
                    <h3 className="text-base font-bold text-slate-900 font-mono tabular-nums">
                      {sub.orderNumber} · {Number(sub.subtotal).toLocaleString('fr-CD')} {sub.currency} (0 % commission à la vente)
                    </h3>
                    <p className="text-sm text-slate-700">{sub.itemsSummary}</p>
                    <p className="text-xs text-slate-600">
                      Statut :{' '}
                      <strong className="text-[#0F3D8A]">
                        {ORDER_STATUS_LABELS[sub.status as OrderStatus] || sub.status}
                      </strong>{' '}
                      · Paiement :{' '}
                      <strong>
                        {PAYMENT_STATUS_LABELS[sub.paymentStatus as PaymentStatus] || sub.paymentStatus}
                      </strong>
                      {sub.courierName ? ` · Livreur : ${sub.courierName}` : ' · En attente d’attribution livreur'}
                      {sub.fundsReleased ? ' · Crédité au solde disponible' : ''}
                    </p>
                  </div>

                  {sub.status !== 'delivered' &&
                    sub.status !== 'in_delivery' &&
                    sub.status !== 'cancelled' &&
                    sub.status !== 'refunded' && (
                      <div className="flex flex-wrap gap-2 shrink-0">
                        {(sub.status === 'pending' || sub.status === 'payment_pending' || sub.status === 'paid') && (
                          <button
                            type="button"
                            disabled={updatingSubId === sub.id}
                            onClick={() => handleUpdateSubOrderStatus(sub, 'accepted')}
                            className="rounded-xl bg-[#0F3D8A] px-3.5 py-2 text-xs font-bold text-white min-h-[40px]"
                          >
                            1. Accepter
                          </button>
                        )}
                        {(sub.status === 'accepted' || sub.status === 'paid') && (
                          <button
                            type="button"
                            disabled={updatingSubId === sub.id}
                            onClick={() => handleUpdateSubOrderStatus(sub, 'preparing')}
                            className="rounded-xl bg-slate-900 px-3.5 py-2 text-xs font-bold text-white min-h-[40px]"
                          >
                            2. En préparation
                          </button>
                        )}
                        {(sub.status === 'accepted' || sub.status === 'preparing') && (
                          <button
                            type="button"
                            disabled={updatingSubId === sub.id}
                            onClick={() => handleUpdateSubOrderStatus(sub, 'ready_for_pickup')}
                            className="rounded-xl bg-orange-600 px-3.5 py-2 text-xs font-bold text-white min-h-[40px]"
                          >
                            3. Prêt pour le livreur
                          </button>
                        )}
                      </div>
                    )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ONGLET 4 : PORTEFEUILLE COMPTABLE INTERNE & RETRAITS (COMMISSION 5 %) */}
      {activeTab === 'wallet' && (
        <div className="space-y-8">
          <div className="bg-slate-900 text-white rounded-2xl p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="space-y-1">
              <p className="text-xs font-semibold text-orange-400 uppercase">
                Registre comptable interne Lubumarket
              </p>
              <h2 className="text-lg font-bold">
                Portefeuille Vendeur — Commission unique de 5 % au retrait
              </h2>
              <p className="text-xs text-slate-300 max-w-2xl leading-relaxed">
                Ce portefeuille trace avec exactitude vos créances commerciales vérifiées. Une vente n’entre dans votre solde disponible qu’après livraison confirmée ET paiement confirmé. Aucun versement bancaire ou Mobile Money automatique n’est simulé sans exécution réelle par l’administration.
              </p>
            </div>
            <button
              type="button"
              disabled={syncingWallet}
              onClick={() => handleSyncWallet(false)}
              className="inline-flex items-center gap-2 rounded-xl bg-white text-slate-900 px-4 py-2.5 text-xs font-bold hover:bg-slate-100 shrink-0 min-h-[44px]"
            >
              <RefreshCw className={`w-4 h-4 ${syncingWallet ? 'animate-spin' : ''}`} />
              <span>Actualiser mes ventes livrées</span>
            </button>
          </div>

          {/* Tableau récapitulatif USD & CDF */}
          <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-bold text-slate-600 uppercase">
                <tr>
                  <th className="py-3.5 px-5">Indicateur comptable</th>
                  <th className="py-3.5 px-5 text-right">Portefeuille USD</th>
                  <th className="py-3.5 px-5 text-right">Portefeuille CDF</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-mono tabular-nums text-xs sm:text-sm">
                <tr>
                  <td className="py-3.5 px-5 font-sans font-medium text-slate-700">
                    Solde en attente (commandes en cours / COD non rapproché)
                  </td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.pendingBalanceUSD || 0).toFixed(2)} USD</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.pendingBalanceCDF || 0).toLocaleString('fr-CD')} FC</td>
                </tr>
                <tr className="bg-blue-50/40">
                  <td className="py-3.5 px-5 font-sans font-bold text-slate-900">
                    Solde disponible retirable (100 % des ventes vérifiées)
                  </td>
                  <td className="py-3.5 px-5 text-right font-extrabold text-[#0F3D8A]">
                    {Number(myWallet?.availableBalanceUSD || 0).toFixed(2)} USD
                  </td>
                  <td className="py-3.5 px-5 text-right font-extrabold text-[#0F3D8A]">
                    {Number(myWallet?.availableBalanceCDF || 0).toLocaleString('fr-CD')} FC
                  </td>
                </tr>
                <tr>
                  <td className="py-3.5 px-5 font-sans font-medium text-slate-700">
                    Retraits en attente d’approbation administrative
                  </td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.lockedWithdrawalUSD || 0).toFixed(2)} USD</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.lockedWithdrawalCDF || 0).toLocaleString('fr-CD')} FC</td>
                </tr>
                <tr>
                  <td className="py-3.5 px-5 font-sans font-medium text-slate-700">Total cumulé des ventes réglées</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.totalSalesUSD || 0).toFixed(2)} USD</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.totalSalesCDF || 0).toLocaleString('fr-CD')} FC</td>
                </tr>
                <tr>
                  <td className="py-3.5 px-5 font-sans font-medium text-slate-700">Total des retraits approuvés (brut)</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.totalWithdrawalsUSD || 0).toFixed(2)} USD</td>
                  <td className="py-3.5 px-5 text-right">{Number(myWallet?.totalWithdrawalsCDF || 0).toLocaleString('fr-CD')} FC</td>
                </tr>
                <tr>
                  <td className="py-3.5 px-5 font-sans font-medium text-slate-700">
                    Total des commissions Lubumarket (5 % au retrait)
                  </td>
                  <td className="py-3.5 px-5 text-right text-orange-600 font-bold">
                    {Number(myWallet?.totalCommissionsUSD || 0).toFixed(2)} USD
                  </td>
                  <td className="py-3.5 px-5 text-right text-orange-600 font-bold">
                    {Number(myWallet?.totalCommissionsCDF || 0).toLocaleString('fr-CD')} FC
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Formulaire de demande de retrait total ou partiel + Historique */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <form
              onSubmit={handleRequestWithdrawal}
              className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-6 space-y-4"
            >
              <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                <ArrowDownToLine className="w-4 h-4 text-orange-600" />
                <h3 className="text-base font-bold text-slate-900">Demander un retrait (total ou partiel)</h3>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Devise du retrait</label>
                  <select
                    value={wdrCurrency}
                    onChange={(e) => setWdrCurrency(e.target.value as CurrencyCode)}
                    className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-bold min-h-[42px]"
                  >
                    <option value="USD">USD ($)</option>
                    <option value="CDF">CDF (FC)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Montant brut demandé
                  </label>
                  <input
                    type="number"
                    step="any"
                    min="1"
                    required
                    value={wdrAmount}
                    onChange={(e) => setWdrAmount(e.target.value)}
                    placeholder={wdrCurrency === 'USD' ? 'Ex: 100 ou 20' : 'Ex: 50000'}
                    className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-mono min-h-[42px]"
                  />
                </div>
              </div>

              {availableForSelectedWdr > 0 && (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setWdrAmount(String(availableForSelectedWdr))}
                    className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Tout retirer ({availableForSelectedWdr} {wdrCurrency})
                  </button>
                  {availableForSelectedWdr >= 20 && wdrCurrency === 'USD' && (
                    <button
                      type="button"
                      onClick={() => setWdrAmount('20')}
                      className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                    >
                      Retrait partiel 20 USD
                    </button>
                  )}
                </div>
              )}

              {/* Simulation exacte 5 % */}
              {liveWithdrawalPreview && (
                <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-1.5 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-600">Montant brut demandé :</span>
                    <span className="font-mono font-bold text-slate-900">
                      {liveWithdrawalPreview.grossAmount.toLocaleString('fr-CD')} {wdrCurrency}
                    </span>
                  </div>
                  <div className="flex justify-between text-orange-700">
                    <span>Commission Lubumarket (5 %) :</span>
                    <span className="font-mono font-bold">
                      - {liveWithdrawalPreview.commissionAmount.toLocaleString('fr-CD')} {wdrCurrency}
                    </span>
                  </div>
                  <div className="flex justify-between pt-1.5 border-t border-slate-200 text-sm font-bold text-emerald-800">
                    <span>Montant net vendeur (95 %) :</span>
                    <span className="font-mono">
                      {liveWithdrawalPreview.netAmount.toLocaleString('fr-CD')} {wdrCurrency}
                    </span>
                  </div>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Canal de réception souhaité</label>
                <select
                  value={wdrChannel}
                  onChange={(e) => setWdrChannel(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                >
                  <option value="M-Pesa Vodacom RDC">M-Pesa (Vodacom RDC)</option>
                  <option value="Airtel Money RDC">Airtel Money RDC</option>
                  <option value="Orange Money RDC">Orange Money RDC</option>
                  <option value="Afrimoney">Afrimoney</option>
                  <option value="Rawbank Virement">Rawbank (Compte / Illicocash)</option>
                  <option value="Equity BCDC">Equity BCDC</option>
                  <option value="TMB">TMB</option>
                  <option value="Guichet Lubumbashi">Retrait espèces guichet Lubumbashi</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Numéro Mobile Money ou IBAN bénéficiaire (ne saisissez jamais de code PIN)
                </label>
                <input
                  type="text"
                  required
                  value={wdrRecipient}
                  onChange={(e) => setWdrRecipient(e.target.value)}
                  placeholder="Ex: +243 81 000 0000 — Nom du titulaire"
                  className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
                />
              </div>

              <button
                type="submit"
                disabled={submittingWdr || !isApproved || availableForSelectedWdr <= 0}
                className="w-full rounded-xl bg-orange-600 py-3 text-sm font-bold text-white hover:bg-orange-700 disabled:opacity-50 min-h-[46px]"
              >
                {submittingWdr ? 'Calcul serveur en cours...' : 'Soumettre la demande de retrait'}
              </button>
            </form>

            {/* Historique des retraits, commissions et journal comptable */}
            <div className="lg:col-span-7 space-y-6">
              <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
                <h3 className="text-base font-bold text-slate-900">
                  Historique de mes demandes de retrait & commissions ({myWithdrawals.length})
                </h3>
                {myWithdrawals.length === 0 ? (
                  <p className="text-xs text-slate-500">Aucune demande de retrait effectuée.</p>
                ) : (
                  <div className="divide-y divide-slate-100">
                    {myWithdrawals.map((w) => (
                      <div key={w.id} className="py-3.5 space-y-1 text-xs">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-slate-900">
                            {w.payoutChannel} · {w.payoutRecipient}
                          </span>
                          <span className="font-bold text-[#0F3D8A]">
                            {w.status === 'approved'
                              ? 'Approuvé'
                              : w.status === 'rejected'
                                ? 'Refusé (recrédité)'
                                : 'En attente'}
                          </span>
                        </div>
                        <p className="font-mono tabular-nums text-slate-700">
                          Brut : {w.grossAmount} {w.currency} · Commission 5 % : {w.commissionAmount} {w.currency} ·{' '}
                          <strong>Net vendeur : {w.netAmount} {w.currency}</strong>
                        </p>
                        <p className="text-slate-500">
                          {formatFsTimestamp(w.createdAt) || 'Récent'} · {w.adminNote}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
                <h3 className="text-base font-bold text-slate-900">
                  Journal des écritures comptables ({myTransactions.length})
                </h3>
                {myTransactions.length === 0 ? (
                  <p className="text-xs text-slate-500">Aucune écriture comptable enregistrée.</p>
                ) : (
                  <div className="divide-y divide-slate-100 max-h-72 overflow-y-auto">
                    {myTransactions.map((tx) => (
                      <div key={tx.id} className="py-2.5 flex items-start justify-between gap-3 text-xs">
                        <div>
                          <p className="font-semibold text-slate-800">{tx.description}</p>
                          <p className="text-slate-400">{formatFsTimestamp(tx.createdAt)}</p>
                        </div>
                        <span className="font-mono tabular-nums font-bold text-slate-900 shrink-0">
                          {tx.amount} {tx.currency}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ONGLET 5 : PARAMÈTRES DE LA BOUTIQUE */}
      {activeTab === 'shop' && (
        <form
          onSubmit={handleUpdateShopProfile}
          className="max-w-2xl bg-white rounded-2xl border border-slate-200 p-6 space-y-4"
        >
          <h2 className="text-base font-bold text-slate-900">Paramètres de ma boutique</h2>
          <div className="space-y-2">
            <label className="block text-xs font-semibold text-slate-700">Logo de la boutique</label>
            <div className="flex flex-wrap items-center gap-4">
              <div className="w-16 h-16 rounded-xl border border-slate-200 bg-slate-50 overflow-hidden shrink-0">
                <SafeImage src={regLogoUrl} alt="Logo boutique" className="w-full h-full object-cover" />
              </div>
              <label className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 cursor-pointer min-h-[42px]">
                <Upload className="w-4 h-4 text-[#0F3D8A]" />
                <span>{uploadingLogo ? 'Traitement...' : 'Modifier le logo'}</span>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleUploadShopLogo}
                  className="hidden"
                />
              </label>
            </div>
            {logoUploadNote && <p className="text-xs text-slate-500">{logoUploadNote}</p>}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Nom de la boutique</label>
            <input
              type="text"
              required
              value={regShopName}
              onChange={(e) => setRegShopName(e.target.value)}
              className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Téléphone boutique</label>
              <input
                type="tel"
                required
                value={regPhone}
                onChange={(e) => setRegPhone(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Zone d’activité</label>
              <input
                type="text"
                required
                value={regZone}
                onChange={(e) => setRegZone(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Adresse de récupération</label>
            <input
              type="text"
              required
              value={regAddress}
              onChange={(e) => setRegAddress(e.target.value)}
              className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[42px]"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Description</label>
            <textarea
              rows={3}
              value={regDescription}
              onChange={(e) => setRegDescription(e.target.value)}
              className="w-full rounded-xl border border-slate-200 p-3 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={submittingShop || uploadingLogo}
            className="rounded-xl bg-[#0F3D8A] px-5 py-2.5 text-xs font-bold text-white min-h-[44px]"
          >
            {submittingShop ? 'Mise à jour...' : 'Enregistrer ma boutique'}
          </button>
        </form>
      )}
    </div>
  );
};
