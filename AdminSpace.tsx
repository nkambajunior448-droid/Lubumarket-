import React, { useEffect, useMemo, useState } from 'react';
import {
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';
import {
  BarChart3,
  CheckCircle2,
  Coins,
  Download,
  FolderKanban,
  Layers,
  Lock,
  MapPin,
  RefreshCw,
  Settings,
  ShieldAlert,
  Sparkles,
  Users,
} from 'lucide-react';
import {
  DELIVERY_STATUS_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  SELLER_APPROVAL_LABELS,
  roundMoney,
  type ApprovalStatus,
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

type AdminTab = 'stats' | 'partners' | 'orders' | 'finance' | 'catalog' | 'settings';

export const AdminSpace: React.FC = () => {
  const {
    firebaseUser,
    isAdmin,
    isBootstrapEligible,
    usdToCdf,
    exchangeRateUpdatedAt,
    signInAndEnsureCustomer,
    notify,
  } = useLubumarket();

  const [activeTab, setActiveTab] = useState<AdminTab>('stats');
  const [adminInitialized, setAdminInitialized] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [seedingDemo, setSeedingDemo] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  // Collections administratives temps réel
  const [allUsers, setAllUsers] = useState<Array<Record<string, any>>>([]);
  const [allSellers, setAllSellers] = useState<Array<Record<string, any>>>([]);
  const [allSellerApps, setAllSellerApps] = useState<Array<Record<string, any>>>([]);
  const [allShops, setAllShops] = useState<Array<Record<string, any>>>([]);
  const [allCouriers, setAllCouriers] = useState<Array<Record<string, any>>>([]);
  const [allCategories, setAllCategories] = useState<Array<Record<string, any>>>([]);
  const [allProducts, setAllProducts] = useState<Array<Record<string, any>>>([]);
  const [allOrders, setAllOrders] = useState<Array<Record<string, any>>>([]);
  const [allDeliveries, setAllDeliveries] = useState<Array<Record<string, any>>>([]);
  const [allWithdrawals, setAllWithdrawals] = useState<Array<Record<string, any>>>([]);
  const [allCommissions, setAllCommissions] = useState<Array<Record<string, any>>>([]);
  const [allRefunds, setAllRefunds] = useState<Array<Record<string, any>>>([]);
  const [allZones, setAllZones] = useState<Array<Record<string, any>>>([]);
  const [allPaymentConfigs, setAllPaymentConfigs] = useState<Array<Record<string, any>>>([]);
  const [adminLogs, setAdminLogs] = useState<Array<Record<string, any>>>([]);
  const [rejectingSellerId, setRejectingSellerId] = useState<string | null>(null);
  const [rejectionReasons, setRejectionReasons] = useState<Record<string, string>>({});

  // Formulaires configuration
  const [rateInput, setRateInput] = useState(String(usdToCdf));
  const [newCatName, setNewCatName] = useState('');
  const [newCatDesc, setNewCatDesc] = useState('');
  const [newZoneCity, setNewZoneCity] = useState('Lubumbashi');
  const [newZoneProvince, setNewZoneProvince] = useState('Haut-Katanga');
  const [newZoneName, setNewZoneName] = useState('');
  const [newZoneCommunes, setNewZoneCommunes] = useState('');
  const [newZoneFee, setNewZoneFee] = useState('3.5');
  const [newZoneTime, setNewZoneTime] = useState('60 - 120 min');
  const [selectedCourierByDelivery, setSelectedCourierByDelivery] = useState<Record<string, string>>({});

  useEffect(() => {
    setRateInput(String(usdToCdf));
  }, [usdToCdf]);

  // Auto-bootstrap transparent lorsque le propriétaire vérifié se connecte
  const handleBootstrapAdmin = async (silent = false) => {
    if (!firebaseUser) return;
    setBootstrapping(true);
    try {
      await apiPost('/api/admin/bootstrap', {});
      setAdminInitialized(true);
      if (!silent) {
        notify(
          'Espace Administrateur propriétaire initialisé avec les 12 catégories, zones de Lubumbashi et moyens de paiement.',
          'success'
        );
      }
    } catch (err) {
      if (!silent) {
        notify(err instanceof Error ? err.message : 'Accès administrateur refusé.', 'error');
      }
    } finally {
      setBootstrapping(false);
    }
  };

  useEffect(() => {
    if (firebaseUser && isBootstrapEligible && !adminInitialized) {
      handleBootstrapAdmin(true);
    }
  }, [firebaseUser, isBootstrapEligible]);

  useEffect(() => {
    if (!firebaseUser || !isAdmin) return;

    const unsubs = [
      onSnapshot(
        collection(db, 'users'),
        (s) => setAllUsers(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'sellers'),
        (s) =>
          setAllSellers(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'sellerApplications'),
        (s) =>
          setAllSellerApps(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'shops'),
        (s) => setAllShops(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'couriers'),
        (s) => setAllCouriers(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'categories'),
        (s) =>
          setAllCategories(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'products'),
        (s) => setAllProducts(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'orders'),
        (s) =>
          setAllOrders(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'deliveries'),
        (s) =>
          setAllDeliveries(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'withdrawals'),
        (s) =>
          setAllWithdrawals(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
      onSnapshot(
        collection(db, 'commissions'),
        (s) => setAllCommissions(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'refunds'),
        (s) => setAllRefunds(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'deliveryZones'),
        (s) => setAllZones(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'paymentConfigs'),
        (s) => setAllPaymentConfigs(s.docs.map((d) => d.data())),
        () => {}
      ),
      onSnapshot(
        collection(db, 'adminLogs'),
        (s) =>
          setAdminLogs(
            s.docs
              .map((d) => d.data())
              .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))
          ),
        () => {}
      ),
    ];

    return () => {
      unsubs.forEach((u) => u());
    };
  }, [firebaseUser, isAdmin]);

  const recordAdminLog = async (
    action: string,
    targetType: string,
    targetId: string,
    details: string
  ) => {
    if (!firebaseUser) return;
    const logId = makeClientId('log');
    await setDoc(doc(db, 'adminLogs', logId), {
      id: logId,
      actorUid: firebaseUser.uid,
      actorEmail: (firebaseUser.email || '').toLowerCase(),
      action: action.slice(0, 100),
      targetType: targetType.slice(0, 60),
      targetId: targetId.slice(0, 120),
      details: details.slice(0, 490),
      createdAt: serverTimestamp(),
    });
  };

  // =========================================================================
  // INITIALISATION FACULTATIVE D'UN CATALOGUE VITRINE DE LUBUMBASHI
  // =========================================================================
  const handleSeedDemoMarketplace = async () => {
    if (!firebaseUser || !isAdmin) return;
    setSeedingDemo(true);
    try {
      await handleBootstrapAdmin(true);
      const batch = writeBatch(db);

      const demoShops = [
        {
          sellerId: 'seller-katanga-tech',
          shopId: 'shop-katanga-tech',
          name: 'Katanga Tech & Mobile',
          description: 'Smartphones neufs, ordinateurs portables professionnels et accessoires garantis à Lubumbashi.',
          phone: '+243 99 100 2000',
          address: 'Av. Kasavubu n°24, Centre-ville, Lubumbashi',
          zone: 'Lubumbashi Centre & Makomeno',
          logoUrl: LUBUMARKET_ASSETS.smartphone,
        },
        {
          sellerId: 'seller-maison-gambela',
          shopId: 'shop-maison-gambela',
          name: 'Maison & Mode Gambela',
          description: 'Prêt-à-porter urbain, chaussures de qualité et électroménager familial.',
          phone: '+243 81 300 4000',
          address: 'Av. Kasaï n°88, Golf / Gambela, Lubumbashi',
          zone: 'Golf, Gambela & Kasapa',
          logoUrl: LUBUMARKET_ASSETS.fashion,
        },
      ];

      for (const ds of demoShops) {
        batch.set(doc(db, 'sellers', ds.sellerId), {
          uid: ds.sellerId,
          shopId: ds.shopId,
          shopName: ds.name,
          phone: ds.phone,
          email: `${ds.sellerId}@lubumarket-partenaire.cd`,
          address: ds.address,
          activityZone: ds.zone,
          city: 'Lubumbashi',
          approvalStatus: 'approved',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });

        batch.set(doc(db, 'shops', ds.shopId), {
          id: ds.shopId,
          sellerId: ds.sellerId,
          name: ds.name,
          logoUrl: ds.logoUrl,
          description: ds.description,
          phone: ds.phone,
          address: ds.address,
          activityZone: ds.zone,
          city: 'Lubumbashi',
          status: 'active',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }

      const demoProducts = [
        {
          id: 'prd-lbm-smartphone-pro',
          sellerId: 'seller-katanga-tech',
          shopId: 'shop-katanga-tech',
          shopName: 'Katanga Tech & Mobile',
          categoryId: 'cat-telephones',
          categoryName: 'Téléphones et accessoires',
          title: 'Smartphone 5G 256 Go Dual SIM + Écouteurs Sans Fil',
          description: 'Écran AMOLED 120 Hz, batterie longue durée 5000 mAh adaptée aux déplacements à Lubumbashi, chargeur rapide inclus.',
          price: 295,
          currency: 'USD',
          stock: 18,
          imageUrls: [LUBUMARKET_ASSETS.smartphone],
        },
        {
          id: 'prd-lbm-laptop-ultrabook',
          sellerId: 'seller-katanga-tech',
          shopId: 'shop-katanga-tech',
          shopName: 'Katanga Tech & Mobile',
          categoryId: 'cat-informatique',
          categoryName: 'Informatique',
          title: 'Ordinateur Portable Pro 15.6" Core i7 · 16 Go RAM · 512 Go SSD',
          description: 'Idéal pour bureaux d’études, entreprises minières, étudiants et gestion comptable.',
          price: 780,
          currency: 'USD',
          stock: 9,
          imageUrls: [LUBUMARKET_ASSETS.laptop],
        },
        {
          id: 'prd-lbm-fashion-pack',
          sellerId: 'seller-maison-gambela',
          shopId: 'shop-maison-gambela',
          shopName: 'Maison & Mode Gambela',
          categoryId: 'cat-vetements',
          categoryName: 'Vêtements et chaussures',
          title: 'Ensemble Urbain Chemise Coton + Baskets Cuir & Sac',
          description: 'Finition soignée, coupe moderne confortable, disponible en plusieurs pointures à Lubumbashi.',
          price: 185000,
          currency: 'CDF',
          stock: 24,
          imageUrls: [LUBUMARKET_ASSETS.fashion],
        },
        {
          id: 'prd-lbm-home-blender',
          sellerId: 'seller-maison-gambela',
          shopId: 'shop-maison-gambela',
          shopName: 'Maison & Mode Gambela',
          categoryId: 'cat-electronique',
          categoryName: 'Électronique',
          title: 'Pack Cuisine : Mixeur Inox Haute Puissance + Bouilloire Électrique',
          description: 'Bol verre renforcé 1.8 L, lames acier inoxydable et protection contre les variations de tension.',
          price: 85,
          currency: 'USD',
          stock: 15,
          imageUrls: [LUBUMARKET_ASSETS.homeElectronics],
        },
      ];

      for (const dp of demoProducts) {
        batch.set(doc(db, 'products', dp.id), {
          ...dp,
          city: 'Lubumbashi',
          soldCount: 0,
          ratingAvg: 0,
          ratingCount: 0,
          status: 'active',
          lastOrderRef: 'none',
          lastReviewRef: 'none',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }

      await batch.commit();
      await recordAdminLog(
        'SEED_DEMO_CATALOG',
        'catalog',
        'lubumbashi-vitrine',
        'Création de 2 boutiques partenaires et 4 produits vitrines (USD & CDF).'
      );
      notify('Catalogue vitrine de Lubumbashi publié avec succès !', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.WRITE, 'demo-seed');
    } finally {
      setSeedingDemo(false);
    }
  };

  // =========================================================================
  // ACTIONS DE GESTION DES PARTENAIRES, COMMANDES, RETRAITS & PARAMÈTRES
  // =========================================================================
  const handleSellerDecision = async (
    seller: Record<string, any>,
    nextApproval: 'approved' | 'rejected' | 'suspended'
  ) => {
    const reason = (rejectionReasons[seller.uid] || '').trim();
    if (nextApproval === 'rejected' && reason.length < 3) {
      notify('Veuillez saisir le motif du refus (au moins 3 caractères).', 'error');
      return;
    }

    setBusyId(seller.uid);
    try {
      const res = await apiPost<{
        ok: boolean;
        statusLabel: string;
        applicationId: string | null;
      }>('/api/admin/sellers/review', {
        sellerId: seller.uid,
        decision: nextApproval,
        reason: nextApproval === 'approved' ? '' : reason,
      });

      setRejectingSellerId(null);
      setRejectionReasons((prev) => ({ ...prev, [seller.uid]: '' }));
      notify(
        nextApproval === 'approved'
          ? `Boutique « ${seller.shopName} » approuvée et activée dans Firestore.`
          : nextApproval === 'rejected'
            ? `Demande « ${seller.shopName} » refusée (statut : ${res.statusLabel}).`
            : `Boutique « ${seller.shopName} » suspendue.`,
        nextApproval === 'approved' ? 'success' : 'info'
      );
    } catch (err) {
      notify(
        err instanceof Error ? err.message : 'Erreur lors de la décision administrative.',
        'error'
      );
    } finally {
      setBusyId(null);
    }
  };

  const handleCourierDecision = async (
    courier: Record<string, any>,
    nextApproval: 'approved' | 'suspended'
  ) => {
    setBusyId(courier.uid);
    try {
      const batch = writeBatch(db);
      batch.update(doc(db, 'couriers', courier.uid), {
        approvalStatus: nextApproval,
        updatedAt: serverTimestamp(),
      });
      const notifId = makeClientId('ntf');
      batch.set(doc(db, 'notifications', notifId), {
        id: notifId,
        recipientId: courier.uid,
        senderId: firebaseUser!.uid,
        eventType: nextApproval === 'approved' ? 'courier_approved' : 'courier_suspended',
        title:
          nextApproval === 'approved'
            ? 'Profil Livreur Lubumarket validé !'
            : 'Profil Livreur suspendu',
        body:
          nextApproval === 'approved'
            ? 'Vous pouvez désormais recevoir des courses de livraison à Lubumbashi.'
            : 'Votre accès aux livraisons a été suspendu.',
        isRead: false,
        channels: ['in_app'],
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      await batch.commit();
      await recordAdminLog(
        nextApproval === 'approved' ? 'APPROVE_COURIER' : 'SUSPEND_COURIER',
        'courier',
        courier.uid,
        `Livreur ${courier.fullName} -> ${nextApproval}`
      );
      notify(`Statut du livreur ${courier.fullName} mis à jour.`, 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `couriers/${courier.uid}`);
    } finally {
      setBusyId(null);
    }
  };

  const handleToggleUserStatus = async (u: Record<string, any>) => {
    if (u.uid === firebaseUser?.uid) {
      notify('Vous ne pouvez pas suspendre votre propre compte propriétaire.', 'error');
      return;
    }
    const nextStatus = u.status === 'suspended' ? 'active' : 'suspended';
    setBusyId(u.uid);
    try {
      await updateDoc(doc(db, 'users', u.uid), {
        status: nextStatus,
        updatedAt: serverTimestamp(),
      });
      await recordAdminLog(
        'TOGGLE_USER_STATUS',
        'user',
        u.uid,
        `${u.email} -> ${nextStatus}`
      );
      notify(`Compte ${u.email} passé à ${nextStatus}.`, 'info');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `users/${u.uid}`);
    } finally {
      setBusyId(null);
    }
  };

  const handleAssignCourier = async (deliveryId: string) => {
    const courierId = selectedCourierByDelivery[deliveryId];
    if (!courierId) {
      notify('Veuillez choisir un livreur validé dans la liste.', 'error');
      return;
    }
    setBusyId(deliveryId);
    try {
      const res = await apiPost<{ courierName: string }>('/api/admin/deliveries/assign', {
        deliveryId,
        courierId,
      });
      notify(`Course attribuée à ${res.courierName}.`, 'success');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur attribution livreur.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleReconcileCod = async (deliveryId: string) => {
    setBusyId(deliveryId);
    try {
      await apiPost('/api/admin/cod/reconcile', { deliveryId });
      notify(
        'Encaissement espèces rapproché : paiement confirmé et solde disponible vendeur crédité à 100 %.',
        'success'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur rapprochement COD.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleAdminCancelOrRefund = async (orderId: string) => {
    setBusyId(orderId);
    try {
      const res = await apiPost<{ status: string }>('/api/orders/cancel-or-refund', {
        orderId,
        reason: 'Décision administrative / remboursement client',
      });
      notify(
        res.status === 'refunded'
          ? 'Commande remboursée (0 % commission prélevée).'
          : 'Commande annulée.',
        'info'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Action impossible.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleReviewWithdrawal = async (
    withdrawalId: string,
    decision: 'approved' | 'rejected'
  ) => {
    setBusyId(withdrawalId);
    try {
      await apiPost('/api/admin/withdrawals/review', {
        withdrawalId,
        decision,
      });
      notify(
        decision === 'approved'
          ? 'Retrait approuvé et commission de 5 % comptabilisée.'
          : 'Retrait refusé — montant brut restitué au solde disponible du vendeur.',
        'success'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Erreur traitement retrait.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleUpdateExchangeRate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser) return;
    const numericRate = roundMoney(Number(rateInput), 'CDF');
    if (numericRate < 100 || numericRate > 100000) {
      notify('Le taux USD -> CDF doit être compris entre 100 et 100 000 FC.', 'error');
      return;
    }
    try {
      await setDoc(doc(db, 'exchangeRates', 'current'), {
        id: 'current',
        usdToCdf: numericRate,
        updatedByUid: firebaseUser.uid,
        updatedAt: serverTimestamp(),
      });
      await recordAdminLog(
        'UPDATE_EXCHANGE_RATE',
        'exchangeRates',
        'current',
        `Nouveau taux : 1 USD = ${numericRate} CDF`
      );
      notify(`Taux de change mis à jour : 1 USD = ${numericRate.toLocaleString('fr-CD')} FC.`, 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.WRITE, 'exchangeRates/current');
    }
  };

  const handleAddCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newCatName.trim().length < 2) return;
    const id = makeClientId('cat');
    const slug = newCatName
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .slice(0, 80);
    try {
      await setDoc(doc(db, 'categories', id), {
        id,
        name: newCatName.trim().slice(0, 100),
        slug: slug || id,
        description: newCatDesc.trim().slice(0, 290),
        sortOrder: allCategories.length + 1,
        isActive: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setNewCatName('');
      setNewCatDesc('');
      notify('Catégorie ajoutée.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.CREATE, `categories/${id}`);
    }
  };

  const handleToggleCategory = async (cat: Record<string, any>) => {
    try {
      await updateDoc(doc(db, 'categories', cat.id), {
        isActive: !cat.isActive,
        updatedAt: serverTimestamp(),
      });
      notify(`Catégorie « ${cat.name} » mise à jour.`, 'info');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `categories/${cat.id}`);
    }
  };

  const handleDeleteCategory = async (catId: string) => {
    try {
      await deleteDoc(doc(db, 'categories', catId));
      notify('Catégorie supprimée.', 'info');
    } catch (err) {
      handleFirestoreError(err, OperationType.DELETE, `categories/${catId}`);
    }
  };

  const handleAddDeliveryZone = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newZoneName.trim().length < 2) return;
    const id = makeClientId('zone');
    try {
      await setDoc(doc(db, 'deliveryZones', id), {
        id,
        city: newZoneCity.trim().slice(0, 80),
        province: newZoneProvince.trim().slice(0, 80),
        name: newZoneName.trim().slice(0, 100),
        communesText: newZoneCommunes.trim().slice(0, 230),
        feeUSD: Math.max(0, roundMoney(Number(newZoneFee), 'USD')),
        estimatedTime: newZoneTime.trim().slice(0, 80),
        isActive: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setNewZoneName('');
      setNewZoneCommunes('');
      notify('Nouvelle zone de livraison créée.', 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.CREATE, `deliveryZones/${id}`);
    }
  };

  const handleToggleZone = async (z: Record<string, any>) => {
    try {
      await updateDoc(doc(db, 'deliveryZones', z.id), {
        isActive: !z.isActive,
        updatedAt: serverTimestamp(),
      });
      await recordAdminLog(
        'TOGGLE_DELIVERY_ZONE',
        'deliveryZone',
        z.id,
        `${z.city} - ${z.name} -> ${!z.isActive ? 'ACTIVE' : 'INACTIVE'}`
      );
      notify(`Zone « ${z.name} » (${z.city}) ${!z.isActive ? 'activée' : 'désactivée'}.`, 'success');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `deliveryZones/${z.id}`);
    }
  };

  const handleTogglePaymentMethod = async (pm: Record<string, any>) => {
    try {
      await updateDoc(doc(db, 'paymentConfigs', pm.id), {
        isEnabled: !pm.isEnabled,
        updatedAt: serverTimestamp(),
      });
      await recordAdminLog(
        'TOGGLE_PAYMENT_METHOD',
        'paymentConfig',
        pm.id,
        `${pm.label} -> ${!pm.isEnabled ? 'ENABLED' : 'DISABLED'}`
      );
      notify(`Moyen de paiement « ${pm.label} » mis à jour.`, 'info');
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `paymentConfigs/${pm.id}`);
    }
  };

  // =========================================================================
  // STATISTIQUES DISTINGUANT COMMANDES / PAIEMENTS CONFIRMÉS / MONTANTS DUS
  // =========================================================================
  const metrics = useMemo(() => {
    const validOrders = allOrders.filter((o) => o.status !== 'cancelled' && o.status !== 'refunded');
    let confirmedUSD = 0;
    let confirmedCDF = 0;
    let dueUSD = 0;
    let dueCDF = 0;

    for (const o of validOrders) {
      const amt = Number(o.grandTotal || 0);
      if (o.paymentStatus === 'confirmed') {
        if (o.currency === 'CDF') confirmedCDF += amt;
        else confirmedUSD += amt;
      } else {
        if (o.currency === 'CDF') dueCDF += amt;
        else dueUSD += amt;
      }
    }

    let collectedCommUSD = 0;
    let collectedCommCDF = 0;
    let pendingCommUSD = 0;
    let pendingCommCDF = 0;

    for (const c of allCommissions) {
      const amt = Number(c.commissionAmount || 0);
      if (c.status === 'collected') {
        if (c.currency === 'CDF') collectedCommCDF += amt;
        else collectedCommUSD += amt;
      } else if (c.status === 'pending_approval') {
        if (c.currency === 'CDF') pendingCommCDF += amt;
        else pendingCommUSD += amt;
      }
    }

    return {
      totalOrdersCount: allOrders.length,
      validOrdersCount: validOrders.length,
      confirmedUSD: roundMoney(confirmedUSD, 'USD'),
      confirmedCDF: roundMoney(confirmedCDF, 'CDF'),
      dueUSD: roundMoney(dueUSD, 'USD'),
      dueCDF: roundMoney(dueCDF, 'CDF'),
      collectedCommUSD: roundMoney(collectedCommUSD, 'USD'),
      collectedCommCDF: roundMoney(collectedCommCDF, 'CDF'),
      pendingCommUSD: roundMoney(pendingCommUSD, 'USD'),
      pendingCommCDF: roundMoney(pendingCommCDF, 'CDF'),
    };
  }, [allOrders, allCommissions]);

  const exportAccountingCsv = () => {
    const headers = [
      'Type',
      'Reference',
      'Date',
      'Acteur',
      'Statut',
      'Statut_Paiement',
      'Montant_Brut',
      'Commission_5_Pct',
      'Montant_Net',
      'Devise',
    ];
    const rows: string[][] = [];

    for (const o of allOrders) {
      rows.push([
        'COMMANDE',
        o.orderNumber,
        formatFsTimestamp(o.createdAt) || '',
        o.customerName,
        o.status,
        o.paymentStatus,
        String(o.grandTotal),
        '0',
        String(o.grandTotal),
        o.currency,
      ]);
    }

    for (const w of allWithdrawals) {
      rows.push([
        'RETRAIT_VENDEUR',
        w.id,
        formatFsTimestamp(w.createdAt) || '',
        w.shopName,
        w.status,
        w.payoutChannel,
        String(w.grossAmount),
        String(w.commissionAmount),
        String(w.netAmount),
        w.currency,
      ]);
    }

    const csv = [headers, ...rows]
      .map((r) => r.map((cell) => `"${String(cell || '').replace(/"/g, '""')}"`).join(','))
      .join('\n');

    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `lubumarket-rapport-comptable-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    notify('Rapport comptable CSV téléchargé.', 'success');
  };

  if (!firebaseUser) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-12">
        <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
          <Lock className="w-12 h-12 text-[#0F3D8A] mx-auto" />
          <h1 className="text-2xl font-bold text-slate-900">
            Administration Propriétaire Lubumarket
          </h1>
          <p className="text-sm text-slate-600">
            Accès strictement réservé au compte propriétaire vérifié côté serveur. Aucune inscription publique ne peut attribuer le rôle Administrateur.
          </p>
          <button
            type="button"
            onClick={signInAndEnsureCustomer}
            className="rounded-xl bg-[#0F3D8A] px-6 py-3 text-sm font-bold text-white min-h-[46px]"
          >
            Connexion Propriétaire Google
          </button>
        </div>
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-12">
        <div className="bg-white rounded-2xl border border-red-200 p-8 text-center space-y-3">
          <ShieldAlert className="w-12 h-12 text-red-600 mx-auto" />
          <h1 className="text-xl font-bold text-slate-900">Accès Administrateur Refusé</h1>
          <p className="text-sm text-slate-600">
            Le compte connecté (<span className="font-mono">{firebaseUser.email}</span>) ne dispose pas des privilèges d’administrateur propriétaire sur Lubumarket.
          </p>
        </div>
      </div>
    );
  }

  const approvedCouriers = allCouriers.filter((c) => c.approvalStatus === 'approved');

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 space-y-8">
      {/* En-tête Administration Propriétaire */}
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <p className="text-xs font-semibold text-[#0F3D8A] uppercase">
            Contrôle global de la marketplace · Lubumbashi & Expansion RDC
          </p>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900">
            Administration Propriétaire Lubumarket
          </h1>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={bootstrapping}
            onClick={() => handleBootstrapAdmin(false)}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-bold text-slate-800 hover:bg-slate-50 min-h-[40px]"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${bootstrapping ? 'animate-spin' : ''}`} />
            <span>Vérifier config initiale</span>
          </button>

          {allProducts.length === 0 && (
            <button
              type="button"
              disabled={seedingDemo}
              onClick={handleSeedDemoMarketplace}
              className="inline-flex items-center gap-1.5 rounded-xl bg-orange-600 px-3.5 py-2 text-xs font-bold text-white hover:bg-orange-700 min-h-[40px]"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>{seedingDemo ? 'Publication...' : 'Publier catalogue vitrine Lubumbashi'}</span>
            </button>
          )}

          <button
            type="button"
            onClick={exportAccountingCsv}
            className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-2 text-xs font-bold text-white hover:bg-slate-800 min-h-[40px]"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Exporter rapport CSV</span>
          </button>
        </div>
      </div>

      {/* Navigation interne Admin */}
      <div className="flex flex-wrap gap-2">
        {(
          [
            { id: 'stats', label: 'Statistiques & Rapports', icon: BarChart3 },
            {
              id: 'partners',
              label: `Vendeurs (${allSellers.length}${allSellers.filter((s) => s.approvalStatus === 'pending').length > 0 ? ` · ${allSellers.filter((s) => s.approvalStatus === 'pending').length} en attente` : ''}) & Livreurs (${allCouriers.length})`,
              icon: Users,
            },
            { id: 'orders', label: `Commandes & Livraisons (${allOrders.length})`, icon: MapPin },
            { id: 'finance', label: `Retraits 5 % (${allWithdrawals.length})`, icon: Coins },
            { id: 'catalog', label: `Catégories & Produits (${allProducts.length})`, icon: Layers },
            { id: 'settings', label: 'Taux USD/CDF, Zones & Paiements', icon: Settings },
          ] as Array<{ id: AdminTab; label: string; icon: any }>
        ).map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`inline-flex items-center gap-2 rounded-xl px-3.5 py-2 text-xs font-bold min-h-[42px] border transition-colors ${
                activeTab === tab.id
                  ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                  : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
              }`}
            >
              <Icon className="w-3.5 h-3.5 shrink-0" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* 1. STATISTIQUES & DISTINCTION STRICTE DES FLUX */}
      {activeTab === 'stats' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Commandes enregistrées (actives / total)</p>
              <p className="text-2xl font-extrabold text-slate-900 font-mono tabular-nums">
                {metrics.validOrdersCount} / {metrics.totalOrdersCount}
              </p>
              <p className="text-xs text-slate-500">
                Boutiques actives : {allShops.filter((s) => s.status === 'active').length} · Clients : {allUsers.length}
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Paiements effectivement confirmés</p>
              <p className="text-xl font-extrabold text-emerald-700 font-mono tabular-nums">
                {metrics.confirmedUSD.toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {metrics.confirmedCDF.toLocaleString('fr-CD')} FC
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Montants encore dus (COD / en attente)</p>
              <p className="text-xl font-extrabold text-amber-700 font-mono tabular-nums">
                {metrics.dueUSD.toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {metrics.dueCDF.toLocaleString('fr-CD')} FC
              </p>
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-1">
              <p className="text-xs text-slate-500">Commissions Lubumarket encaissées (5 % au retrait)</p>
              <p className="text-xl font-extrabold text-[#0F3D8A] font-mono tabular-nums">
                {metrics.collectedCommUSD.toFixed(2)} USD
              </p>
              <p className="text-xs font-mono tabular-nums text-slate-600">
                {metrics.collectedCommCDF.toLocaleString('fr-CD')} FC · En attente : {metrics.pendingCommUSD.toFixed(2)} $
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
              <h2 className="text-base font-bold text-slate-900">Produits les plus vendus</h2>
              {allProducts.length === 0 ? (
                <p className="text-xs text-slate-500">Aucun produit publié.</p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {[...allProducts]
                    .sort((a, b) => Number(b.soldCount || 0) - Number(a.soldCount || 0))
                    .slice(0, 6)
                    .map((p) => (
                      <div key={p.id} className="py-2.5 flex items-center justify-between text-xs">
                        <div>
                          <p className="font-bold text-slate-900">{p.title}</p>
                          <p className="text-slate-500">{p.shopName} · {p.categoryName}</p>
                        </div>
                        <span className="font-mono tabular-nums font-bold text-slate-900">
                          {p.soldCount || 0} vendu(s) · Stock {p.stock}
                        </span>
                      </div>
                    ))}
                </div>
              )}
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
              <h2 className="text-base font-bold text-slate-900">Boutiques partenaires enregistrées</h2>
              {allShops.length === 0 ? (
                <p className="text-xs text-slate-500">Aucune boutique inscrite.</p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {allShops.slice(0, 6).map((sh) => (
                    <div key={sh.id} className="py-2.5 flex items-center justify-between text-xs">
                      <div>
                        <p className="font-bold text-slate-900">{sh.name}</p>
                        <p className="text-slate-500">{sh.activityZone} · {sh.phone}</p>
                      </div>
                      <span className="font-bold text-[#0F3D8A]">{sh.status}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 2. GESTION DES VENDEURS, LIVREURS ET UTILISATEURS */}
      {activeTab === 'partners' && (
        <div className="space-y-8">
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
              <div>
                <h2 className="text-base font-bold text-slate-900">
                  Demandes d’inscription & boutiques vendeurs ({allSellers.length})
                </h2>
                <p className="text-xs text-slate-500">
                  En attente de validation :{' '}
                  <strong className="font-mono text-amber-700">
                    {allSellers.filter((s) => s.approvalStatus === 'pending').length}
                  </strong>{' '}
                  · Approuvées :{' '}
                  <strong className="font-mono text-emerald-700">
                    {allSellers.filter((s) => s.approvalStatus === 'approved').length}
                  </strong>{' '}
                  · Refusées :{' '}
                  <strong className="font-mono text-red-700">
                    {allSellers.filter((s) => s.approvalStatus === 'rejected').length}
                  </strong>
                </p>
              </div>
            </div>

            {allSellers.length === 0 ? (
              <p className="text-xs text-slate-500">Aucune demande vendeur enregistrée dans Firestore.</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {allSellers.map((s) => {
                  const shopRecord = allShops.find((sh) => sh.id === s.shopId);
                  const appRecord =
                    allSellerApps.find((a) => a.id === s.applicationId) ||
                    allSellerApps.find((a) => a.sellerId === s.uid);
                  const uniqueRequestId = s.applicationId || appRecord?.id || s.shopId;
                  const statusKey = (s.approvalStatus || 'pending') as ApprovalStatus;
                  const frenchStatus =
                    s.statusLabel || SELLER_APPROVAL_LABELS[statusKey] || s.approvalStatus;
                  const logoSrc = shopRecord?.logoUrl || appRecord?.logoUrl || '/icon.svg';
                  const descriptionText = shopRecord?.description || appRecord?.description || '';
                  const isRejectBoxOpen = rejectingSellerId === s.uid;

                  return (
                    <div key={s.uid} className="py-5 space-y-3 text-xs">
                      <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
                        <div className="flex items-start gap-3.5 min-w-0 flex-1">
                          <div className="w-14 h-14 rounded-xl border border-slate-200 bg-slate-50 overflow-hidden shrink-0">
                            <SafeImage
                              src={logoSrc}
                              alt={s.shopName}
                              className="w-full h-full object-cover"
                            />
                          </div>
                          <div className="space-y-1 min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2 text-slate-500">
                              <span className="font-mono font-bold text-slate-800">
                                ID Demande : {uniqueRequestId}
                              </span>
                              <span aria-hidden="true">·</span>
                              <span className="font-mono">Boutique : {s.shopId}</span>
                              {formatFsTimestamp(s.createdAt) && (
                                <>
                                  <span aria-hidden="true">·</span>
                                  <span>{formatFsTimestamp(s.createdAt)}</span>
                                </>
                              )}
                            </div>

                            <p className="text-sm font-bold text-slate-900">
                              {s.shopName} ·{' '}
                              <span
                                className={
                                  statusKey === 'approved'
                                    ? 'text-emerald-700'
                                    : statusKey === 'pending'
                                      ? 'text-amber-700'
                                      : 'text-red-700'
                                }
                              >
                                {frenchStatus}
                              </span>
                            </p>

                            <p className="text-slate-600">
                              {s.email} · {s.phone} · {s.address} ({s.activityZone}, {s.city || 'Lubumbashi'})
                            </p>

                            {descriptionText && (
                              <p className="text-slate-500 leading-relaxed">{descriptionText}</p>
                            )}

                            {s.rejectionReason && statusKey !== 'approved' && (
                              <p className="text-red-700 font-medium">
                                Motif enregistré : {s.rejectionReason}
                              </p>
                            )}
                          </div>
                        </div>

                        <div className="flex flex-wrap items-center gap-2 shrink-0">
                          {s.approvalStatus !== 'approved' && (
                            <button
                              type="button"
                              disabled={busyId === s.uid}
                              onClick={() => handleSellerDecision(s, 'approved')}
                              className="rounded-xl bg-emerald-700 px-3.5 py-2 font-bold text-white hover:bg-emerald-800 disabled:opacity-50 min-h-[38px]"
                            >
                              Approuver la boutique
                            </button>
                          )}

                          {s.approvalStatus === 'pending' && (
                            <button
                              type="button"
                              disabled={busyId === s.uid}
                              onClick={() =>
                                setRejectingSellerId(isRejectBoxOpen ? null : s.uid)
                              }
                              className="rounded-xl border border-red-200 px-3.5 py-2 font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50 min-h-[38px]"
                            >
                              Refuser la demande
                            </button>
                          )}

                          {s.approvalStatus === 'approved' && (
                            <button
                              type="button"
                              disabled={busyId === s.uid}
                              onClick={() => handleSellerDecision(s, 'suspended')}
                              className="rounded-xl border border-slate-300 px-3.5 py-2 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 min-h-[38px]"
                            >
                              Suspendre
                            </button>
                          )}
                        </div>
                      </div>

                      {isRejectBoxOpen && (
                        <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                          <input
                            type="text"
                            value={rejectionReasons[s.uid] || ''}
                            onChange={(e) =>
                              setRejectionReasons((prev) => ({
                                ...prev,
                                [s.uid]: e.target.value,
                              }))
                            }
                            placeholder="Motif précis du refus communiqué au vendeur (ex: adresse physique incomplète à Lubumbashi)..."
                            className="flex-1 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs min-h-[40px]"
                          />
                          <div className="flex gap-2 shrink-0">
                            <button
                              type="button"
                              disabled={busyId === s.uid}
                              onClick={() => handleSellerDecision(s, 'rejected')}
                              className="rounded-xl bg-red-700 px-4 py-2 text-xs font-bold text-white hover:bg-red-800 disabled:opacity-50 min-h-[40px]"
                            >
                              Confirmer le refus
                            </button>
                            <button
                              type="button"
                              onClick={() => setRejectingSellerId(null)}
                              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 min-h-[40px]"
                            >
                              Annuler
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Validation et contrôle des livreurs ({allCouriers.length})
            </h2>
            {allCouriers.length === 0 ? (
              <p className="text-xs text-slate-500">Aucun livreur inscrit.</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {allCouriers.map((c) => (
                  <div
                    key={c.uid}
                    className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
                  >
                    <div className="space-y-0.5">
                      <p className="text-sm font-bold text-slate-900">
                        {c.fullName} · <span className="text-[#0F3D8A]">{c.approvalStatus}</span>
                        {c.isAvailable ? ' · Disponible' : ' · En pause'}
                      </p>
                      <p className="text-slate-600">
                        {c.email} · {c.phone} · Zone : {c.deliveryZone}
                      </p>
                    </div>
                    <div className="flex gap-2 shrink-0">
                      {c.approvalStatus !== 'approved' && (
                        <button
                          type="button"
                          disabled={busyId === c.uid}
                          onClick={() => handleCourierDecision(c, 'approved')}
                          className="rounded-xl bg-emerald-700 px-3.5 py-2 font-bold text-white min-h-[38px]"
                        >
                          Valider Livreur
                        </button>
                      )}
                      {c.approvalStatus !== 'suspended' && (
                        <button
                          type="button"
                          disabled={busyId === c.uid}
                          onClick={() => handleCourierDecision(c, 'suspended')}
                          className="rounded-xl border border-red-200 px-3.5 py-2 font-semibold text-red-700 min-h-[38px]"
                        >
                          Suspendre
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Comptes utilisateurs enregistrés ({allUsers.length})
            </h2>
            <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto">
              {allUsers.map((u) => (
                <div key={u.uid} className="py-3 flex items-center justify-between gap-3 text-xs">
                  <div>
                    <p className="font-bold text-slate-900">
                      {u.displayName} · Rôle : {u.role} · Statut : {u.status}
                    </p>
                    <p className="text-slate-500">{u.email} · {u.city}</p>
                  </div>
                  {u.uid !== firebaseUser.uid && (
                    <button
                      type="button"
                      disabled={busyId === u.uid}
                      onClick={() => handleToggleUserStatus(u)}
                      className="rounded-lg border border-slate-200 px-3 py-1.5 font-semibold text-slate-700 hover:bg-slate-50"
                    >
                      {u.status === 'suspended' ? 'Réactiver' : 'Suspendre'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 3. COMMANDES GLOBALES, ATTRIBUTION LIVREUR & RAPPROCHEMENT ESPÈCES COD */}
      {activeTab === 'orders' && (
        <div className="space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Livraisons, attribution de livreurs et rapprochement Paiement à la livraison ({allDeliveries.length})
            </h2>
            {allDeliveries.length === 0 ? (
              <p className="text-xs text-slate-500">Aucune livraison enregistrée.</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {allDeliveries.map((del) => (
                  <div key={del.id} className="py-4 space-y-3 text-xs">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-2">
                      <div>
                        <p className="text-sm font-bold text-slate-900 font-mono tabular-nums">
                          {del.orderNumber} · Boutique {del.shopName} → Client {del.customerName}
                        </p>
                        <p className="text-slate-600">
                          {del.itemsSummary} · Zone : {del.zoneName} · Statut :{' '}
                          <strong>
                            {DELIVERY_STATUS_LABELS[del.status as DeliveryStatus] || del.status}
                          </strong>
                          {del.courierName ? ` · Livreur : ${del.courierName}` : ''}
                        </p>
                        {del.isCod && (
                          <p className="text-orange-700 font-semibold">
                            Paiement à la livraison : {del.codAmountToCollect} {del.currency} ·{' '}
                            {del.codReconciledByAdmin
                              ? 'Rapproché et libéré au vendeur'
                              : del.codCollected
                                ? 'Encaissé par le livreur — En attente de votre rapprochement comptable'
                                : 'À encaisser lors de la remise'}
                          </p>
                        )}
                      </div>

                      <div className="flex flex-wrap items-center gap-2 shrink-0">
                        {del.status !== 'delivered' && del.status !== 'cancelled' && (
                          <>
                            <select
                              value={selectedCourierByDelivery[del.id] || del.courierId || ''}
                              onChange={(e) =>
                                setSelectedCourierByDelivery((prev) => ({
                                  ...prev,
                                  [del.id]: e.target.value,
                                }))
                              }
                              className="rounded-xl border border-slate-200 px-3 py-2 text-xs min-h-[38px]"
                            >
                              <option value="">Choisir un livreur validé...</option>
                              {approvedCouriers.map((c) => (
                                <option key={c.uid} value={c.uid}>
                                  {c.fullName} ({c.deliveryZone})
                                </option>
                              ))}
                            </select>
                            <button
                              type="button"
                              disabled={busyId === del.id}
                              onClick={() => handleAssignCourier(del.id)}
                              className="rounded-xl bg-[#0F3D8A] px-3.5 py-2 font-bold text-white min-h-[38px]"
                            >
                              Attribuer
                            </button>
                          </>
                        )}

                        {del.isCod && del.status === 'delivered' && !del.codReconciledByAdmin && (
                          <button
                            type="button"
                            disabled={busyId === del.id}
                            onClick={() => handleReconcileCod(del.id)}
                            className="rounded-xl bg-emerald-700 px-3.5 py-2 font-bold text-white min-h-[38px]"
                          >
                            Rapprocher espèces COD & Créditer vendeur
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Toutes les commandes clients & remboursements ({allOrders.length})
            </h2>
            <div className="divide-y divide-slate-100">
              {allOrders.map((o) => (
                <div
                  key={o.id}
                  className="py-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
                >
                  <div>
                    <p className="font-bold text-slate-900 font-mono tabular-nums">
                      {o.orderNumber} · {o.grandTotal} {o.currency} · {o.customerName} ({o.customerPhone})
                    </p>
                    <p className="text-slate-600">
                      Statut : {ORDER_STATUS_LABELS[o.status as OrderStatus] || o.status} · Paiement :{' '}
                      {PAYMENT_STATUS_LABELS[o.paymentStatus as PaymentStatus] || o.paymentStatus} ({o.paymentMethod})
                    </p>
                  </div>
                  {o.status !== 'cancelled' && o.status !== 'refunded' && o.status !== 'delivered' && (
                    <button
                      type="button"
                      disabled={busyId === o.id}
                      onClick={() => handleAdminCancelOrRefund(o.id)}
                      className="rounded-xl border border-red-200 px-3 py-1.5 font-semibold text-red-700 hover:bg-red-50 shrink-0 min-h-[38px]"
                    >
                      {o.paymentStatus === 'confirmed' ? 'Rembourser' : 'Annuler'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 4. RETRAITS VENDEURS & COMMISSIONS LUBUMARKET DE 5 % */}
      {activeTab === 'finance' && (
        <div className="space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Demandes de retrait vendeurs — Validation de la commission de 5 % ({allWithdrawals.length})
            </h2>
            {allWithdrawals.length === 0 ? (
              <p className="text-xs text-slate-500">Aucune demande de retrait soumise.</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {allWithdrawals.map((w) => (
                  <div
                    key={w.id}
                    className="py-4 flex flex-col md:flex-row md:items-center justify-between gap-4 text-xs"
                  >
                    <div className="space-y-1">
                      <p className="text-sm font-bold text-slate-900">
                        {w.shopName} · Canal : {w.payoutChannel} ({w.payoutRecipient})
                      </p>
                      <p className="font-mono tabular-nums text-slate-800">
                        Brut demandé : <strong>{w.grossAmount} {w.currency}</strong> · Commission 5 % :{' '}
                        <strong className="text-orange-600">{w.commissionAmount} {w.currency}</strong> · Net à verser :{' '}
                        <strong className="text-emerald-700">{w.netAmount} {w.currency}</strong>
                      </p>
                      <p className="text-slate-500">
                        {formatFsTimestamp(w.createdAt) || 'Récent'} · Statut : <strong>{w.status}</strong>
                      </p>
                    </div>

                    {w.status === 'pending' && (
                      <div className="flex gap-2 shrink-0">
                        <button
                          type="button"
                          disabled={busyId === w.id}
                          onClick={() => handleReviewWithdrawal(w.id, 'approved')}
                          className="rounded-xl bg-emerald-700 px-4 py-2 font-bold text-white min-h-[40px]"
                        >
                          Approuver le retrait
                        </button>
                        <button
                          type="button"
                          disabled={busyId === w.id}
                          onClick={() => handleReviewWithdrawal(w.id, 'rejected')}
                          className="rounded-xl border border-red-200 px-3.5 py-2 font-semibold text-red-700 min-h-[40px]"
                        >
                          Refuser & Recréditer
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {allRefunds.length > 0 && (
            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-3">
              <h3 className="text-base font-bold text-slate-900">Historique des remboursements ({allRefunds.length})</h3>
              <div className="divide-y divide-slate-100">
                {allRefunds.map((r) => (
                  <div key={r.id} className="py-2.5 flex justify-between text-xs">
                    <span>Commande {r.orderId} · Motif : {r.reason}</span>
                    <span className="font-mono font-bold">{r.amount} {r.currency}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 5. GESTION DES CATÉGORIES & MODÉRATION CATALOGUE */}
      {activeTab === 'catalog' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <FolderKanban className="w-4 h-4 text-[#0F3D8A]" />
              <span>Catégories de la marketplace ({allCategories.length})</span>
            </h2>

            <form onSubmit={handleAddCategory} className="space-y-3 border-b border-slate-100 pb-4">
              <input
                type="text"
                required
                value={newCatName}
                onChange={(e) => setNewCatName(e.target.value)}
                placeholder="Nouvelle catégorie..."
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[40px]"
              />
              <input
                type="text"
                value={newCatDesc}
                onChange={(e) => setNewCatDesc(e.target.value)}
                placeholder="Courte description..."
                className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[40px]"
              />
              <button
                type="submit"
                className="w-full rounded-xl bg-[#0F3D8A] py-2.5 text-xs font-bold text-white min-h-[40px]"
              >
                + Créer la catégorie
              </button>
            </form>

            <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
              {allCategories.map((c) => (
                <div key={c.id} className="py-2.5 flex items-center justify-between gap-2 text-xs">
                  <div>
                    <p className="font-bold text-slate-900">
                      {c.name} {!c.isActive ? '(Masquée)' : ''}
                    </p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleToggleCategory(c)}
                      className="text-[#0F3D8A] font-semibold hover:underline"
                    >
                      {c.isActive ? 'Masquer' : 'Activer'}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDeleteCategory(c.id)}
                      className="text-red-600 font-semibold hover:underline"
                    >
                      Suppr.
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Modération globale des produits ({allProducts.length})
            </h2>
            {allProducts.length === 0 ? (
              <p className="text-xs text-slate-500">Aucun produit en ligne.</p>
            ) : (
              <div className="divide-y divide-slate-100 max-h-[520px] overflow-y-auto">
                {allProducts.map((p) => (
                  <div key={p.id} className="py-3 flex items-center justify-between gap-3 text-xs">
                    <div>
                      <p className="font-bold text-slate-900">
                        {p.title} · {p.price} {p.currency}
                      </p>
                      <p className="text-slate-500">
                        Boutique : {p.shopName} · Stock : {p.stock} · Statut : {p.status}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={async () => {
                        const next = p.status === 'active' ? 'suspended' : 'active';
                        await updateDoc(doc(db, 'products', p.id), {
                          status: next,
                          updatedAt: serverTimestamp(),
                        });
                        notify(`Produit passé à ${next}.`, 'info');
                      }}
                      className="rounded-lg border border-slate-200 px-3 py-1.5 font-semibold text-slate-700 shrink-0"
                    >
                      {p.status === 'active' ? 'Suspendre' : 'Réactiver'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 6. TAUX DE CHANGE USD/CDF, ZONES DE LIVRAISON RDC, PAIEMENTS & LOGS */}
      {activeTab === 'settings' && (
        <div className="space-y-8">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Taux USD / CDF */}
            <form
              onSubmit={handleUpdateExchangeRate}
              className="lg:col-span-5 bg-white rounded-2xl border border-slate-200 p-6 space-y-4"
            >
              <h2 className="text-base font-bold text-slate-900">Taux de change officiel USD ↔ CDF</h2>
              <p className="text-xs text-slate-500">
                Dernière mise à jour enregistrée : {exchangeRateUpdatedAt || 'Valeur initiale (2 850 FC)'}
              </p>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Valeur de 1 USD en Francs Congolais (FC)
                </label>
                <input
                  type="number"
                  step="any"
                  required
                  value={rateInput}
                  onChange={(e) => setRateInput(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-base font-mono font-bold min-h-[44px]"
                />
              </div>
              <button
                type="submit"
                className="w-full rounded-xl bg-[#0F3D8A] py-2.5 text-xs font-bold text-white min-h-[42px]"
              >
                Appliquer ce taux sur toute la plateforme
              </button>
            </form>

            {/* Moyens de paiement activables */}
            <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
              <h2 className="text-base font-bold text-slate-900">
                Activation des moyens de paiement ({allPaymentConfigs.length})
              </h2>
              <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto">
                {allPaymentConfigs.map((pm) => (
                  <div key={pm.id} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                    <div>
                      <p className="font-bold text-slate-900">
                        {pm.label} · {pm.isTestMode ? 'Mode Test' : 'Encaissement réel COD'}
                      </p>
                      <p className="text-slate-500">{pm.integrationNote}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleTogglePaymentMethod(pm)}
                      className={`rounded-lg px-3 py-1.5 font-bold shrink-0 ${
                        pm.isEnabled
                          ? 'bg-emerald-700 text-white'
                          : 'bg-slate-200 text-slate-700'
                      }`}
                    >
                      {pm.isEnabled ? 'Activé' : 'Désactivé'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Zones de livraison Lubumbashi + Expansion Kolwezi / Likasi / Kinshasa */}
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-5">
            <h2 className="text-base font-bold text-slate-900">
              Zones tarifaires de livraison — Lubumbashi & Expansion RDC ({allZones.length})
            </h2>

            <form onSubmit={handleAddDeliveryZone} className="grid grid-cols-1 sm:grid-cols-3 gap-3 pb-4 border-b border-slate-100">
              <input
                type="text"
                required
                value={newZoneCity}
                onChange={(e) => setNewZoneCity(e.target.value)}
                placeholder="Ville (ex: Kolwezi)"
                className="rounded-xl border border-slate-200 px-3 py-2 text-xs min-h-[40px]"
              />
              <input
                type="text"
                required
                value={newZoneName}
                onChange={(e) => setNewZoneName(e.target.value)}
                placeholder="Nom de la zone"
                className="rounded-xl border border-slate-200 px-3 py-2 text-xs min-h-[40px]"
              />
              <input
                type="text"
                required
                value={newZoneCommunes}
                onChange={(e) => setNewZoneCommunes(e.target.value)}
                placeholder="Communes / Quartiers couverts"
                className="rounded-xl border border-slate-200 px-3 py-2 text-xs min-h-[40px]"
              />
              <input
                type="number"
                step="0.5"
                required
                value={newZoneFee}
                onChange={(e) => setNewZoneFee(e.target.value)}
                placeholder="Frais en USD"
                className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-mono min-h-[40px]"
              />
              <input
                type="text"
                required
                value={newZoneTime}
                onChange={(e) => setNewZoneTime(e.target.value)}
                placeholder="Délai estimé (ex: 60-90 min)"
                className="rounded-xl border border-slate-200 px-3 py-2 text-xs min-h-[40px]"
              />
              <button
                type="submit"
                className="rounded-xl bg-[#0F3D8A] px-4 py-2 text-xs font-bold text-white min-h-[40px]"
              >
                + Ajouter cette zone RDC
              </button>
            </form>

            <div className="divide-y divide-slate-100">
              {allZones.map((z) => (
                <div key={z.id} className="py-3 flex items-center justify-between gap-3 text-xs">
                  <div>
                    <p className="font-bold text-slate-900">
                      {z.city} ({z.province}) — {z.name} ·{' '}
                      <span className="font-mono">{z.feeUSD} USD</span> ({z.estimatedTime})
                    </p>
                    <p className="text-slate-500">{z.communesText}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleToggleZone(z)}
                    className={`rounded-lg px-3 py-1.5 font-bold shrink-0 ${
                      z.isActive ? 'bg-emerald-700 text-white' : 'bg-slate-200 text-slate-700'
                    }`}
                  >
                    {z.isActive ? 'Zone active' : 'Activer expansion'}
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Journal de sécurité AdminLogs */}
          <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
            <h2 className="text-base font-bold text-slate-900">
              Journal d’audit des opérations sensibles ({adminLogs.length})
            </h2>
            <div className="divide-y divide-slate-100 max-h-64 overflow-y-auto text-xs">
              {adminLogs.map((l) => (
                <div key={l.id} className="py-2 flex items-start justify-between gap-4">
                  <div>
                    <p className="font-mono font-bold text-slate-800">
                      {l.action} · {l.details}
                    </p>
                    <p className="text-slate-500">{l.actorEmail}</p>
                  </div>
                  <span className="text-slate-400 shrink-0">{formatFsTimestamp(l.createdAt)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
