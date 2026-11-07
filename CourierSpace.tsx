import React, { useEffect, useState } from 'react';
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
  CheckCircle2,
  KeyRound,
  MapPin,
  PackageCheck,
  Phone,
  Store,
  Truck,
} from 'lucide-react';
import { DELIVERY_STATUS_LABELS, type DeliveryStatus } from '../shared/domain';
import {
  OperationType,
  apiPost,
  db,
  handleFirestoreError,
} from '../lib/firebase';
import { formatFsTimestamp, useLubumarket } from '../context/LubumarketContext';

export const CourierSpace: React.FC = () => {
  const {
    firebaseUser,
    userDoc,
    courierDoc,
    deliveryZones,
    signInAndEnsureCustomer,
    notify,
  } = useLubumarket();

  const [myDeliveries, setMyDeliveries] = useState<Array<Record<string, any>>>([]);
  const [viewMode, setViewMode] = useState<'active' | 'history'>('active');

  // Inscription livreur
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [zoneName, setZoneName] = useState('Lubumbashi Centre & Makomeno');
  const [city, setCity] = useState('Lubumbashi');
  const [submittingReg, setSubmittingReg] = useState(false);

  // Actions de course
  const [busyDeliveryId, setBusyDeliveryId] = useState<string | null>(null);
  const [codeInputs, setCodeInputs] = useState<Record<string, string>>({});

  useEffect(() => {
    if (firebaseUser && !fullName) {
      setFullName(userDoc?.displayName || firebaseUser.displayName || '');
    }
  }, [firebaseUser, userDoc]);

  useEffect(() => {
    if (!firebaseUser || !courierDoc) {
      setMyDeliveries([]);
      return;
    }

    const uid = firebaseUser.uid;
    const qDels = query(collection(db, 'deliveries'), where('courierId', '==', uid));
    const unsub = onSnapshot(
      qDels,
      (snap) => {
        const list = snap.docs
          .map((d) => d.data())
          .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
        setMyDeliveries(list);
      },
      (err) => handleFirestoreError(err, OperationType.LIST, 'deliveries')
    );

    return () => unsub();
  }, [firebaseUser, courierDoc]);

  const handleRegisterCourier = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firebaseUser) {
      await signInAndEnsureCustomer();
      return;
    }
    if (fullName.trim().length < 2 || phone.trim().length < 6) {
      notify('Veuillez renseigner votre nom complet et votre numéro de téléphone.', 'error');
      return;
    }

    setSubmittingReg(true);
    try {
      const uid = firebaseUser.uid;
      const batch = writeBatch(db);
      batch.set(doc(db, 'couriers', uid), {
        uid,
        fullName: fullName.trim().slice(0, 120),
        phone: phone.trim().slice(0, 32),
        email: (firebaseUser.email || '').toLowerCase(),
        deliveryZone: zoneName.slice(0, 100),
        city: city.slice(0, 80),
        approvalStatus: 'pending',
        isAvailable: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });

      if (userDoc?.role === 'customer') {
        batch.update(doc(db, 'users', uid), {
          role: 'courier',
          phone: phone.trim().slice(0, 32),
          city: city.slice(0, 80),
          updatedAt: serverTimestamp(),
        });
      }

      await batch.commit();
      notify(
        'Candidature livreur enregistrée ! L’administrateur Lubumarket validera votre profil.',
        'success'
      );
    } catch (err) {
      handleFirestoreError(err, OperationType.CREATE, `couriers/${firebaseUser.uid}`);
    } finally {
      setSubmittingReg(false);
    }
  };

  const toggleAvailability = async () => {
    if (!firebaseUser || !courierDoc) return;
    try {
      await updateDoc(doc(db, 'couriers', firebaseUser.uid), {
        isAvailable: !courierDoc.isAvailable,
        updatedAt: serverTimestamp(),
      });
      notify(
        !courierDoc.isAvailable
          ? 'Vous êtes désormais disponible pour recevoir des courses.'
          : 'Vous êtes passé en pause.',
        'info'
      );
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `couriers/${firebaseUser.uid}`);
    }
  };

  const handleConfirmPickup = async (del: Record<string, any>) => {
    setBusyDeliveryId(del.id);
    try {
      const batch = writeBatch(db);
      batch.update(doc(db, 'deliveries', del.id), {
        status: 'picked_up',
        updatedAt: serverTimestamp(),
      });
      batch.update(doc(db, 'sellerSubOrders', del.subOrderId), {
        status: 'in_delivery',
        updatedAt: serverTimestamp(),
      });
      await batch.commit();
      notify(
        `Colis ${del.orderNumber} confirmé récupéré chez ${del.shopName}. En route vers le client !`,
        'success'
      );
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, `deliveries/${del.id}`);
    } finally {
      setBusyDeliveryId(null);
    }
  };

  const handleConfirmHandoverWithCode = async (e: React.FormEvent, del: Record<string, any>) => {
    e.preventDefault();
    const entered = (codeInputs[del.id] || '').trim();
    if (!/^\d{6}$/.test(entered)) {
      notify('Veuillez saisir les 6 chiffres du code secret communiqué par le client.', 'error');
      return;
    }

    setBusyDeliveryId(del.id);
    try {
      await apiPost('/api/deliveries/confirm-handover', {
        deliveryId: del.id,
        code: entered,
      });
      setCodeInputs((prev) => ({ ...prev, [del.id]: '' }));
      notify(
        `Livraison ${del.orderNumber} validée par code unique ! Merci pour votre course.`,
        'success'
      );
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Code de livraison refusé.', 'error');
    } finally {
      setBusyDeliveryId(null);
    }
  };

  if (!firebaseUser) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-12">
        <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4">
          <Truck className="w-12 h-12 text-orange-600 mx-auto" />
          <h1 className="text-2xl font-bold text-slate-900">Espace Livreur Partenaire Lubumarket</h1>
          <p className="text-sm text-slate-600 max-w-xl mx-auto">
            Consultez uniquement les courses qui vous sont attribuées à Lubumbashi, récupérez les marchandises en boutique et validez chaque remise grâce au code client à usage unique.
          </p>
          <button
            type="button"
            onClick={signInAndEnsureCustomer}
            className="rounded-xl bg-[#0F3D8A] px-6 py-3 text-sm font-bold text-white hover:bg-[#0A2B63] min-h-[46px]"
          >
            Se connecter pour accéder à l’Espace Livreur
          </button>
        </div>
      </div>
    );
  }

  if (!courierDoc) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-10 space-y-6">
        <div className="border-b border-slate-200 pb-5">
          <p className="text-xs font-semibold text-orange-600 uppercase">Flotte Lubumbashi · RDC</p>
          <h1 className="mt-1 text-2xl font-bold text-slate-900">Inscription Livreur Partenaire</h1>
          <p className="mt-1 text-sm text-slate-600">
            Votre compte livreur sera activé après vérification par l’administrateur propriétaire.
          </p>
        </div>

        <form
          onSubmit={handleRegisterCourier}
          className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 space-y-4"
        >
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Nom complet *</label>
            <input
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Ex: Patrick Ilunga"
              className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Téléphone mobile joignable en course *
            </label>
            <input
              type="tel"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+243 99 000 0000"
              className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Zone principale de livraison *
            </label>
            <select
              value={zoneName}
              onChange={(e) => setZoneName(e.target.value)}
              className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm min-h-[44px]"
            >
              {deliveryZones.map((z) => (
                <option key={z.id} value={z.name}>
                  {z.name} ({z.city})
                </option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={submittingReg}
            className="w-full rounded-xl bg-orange-600 py-3.5 text-sm font-bold text-white hover:bg-orange-700 min-h-[48px]"
          >
            {submittingReg ? 'Envoi en cours...' : 'Soumettre mon inscription livreur'}
          </button>
        </form>
      </div>
    );
  }

  const isApproved = courierDoc.approvalStatus === 'approved';
  const activeDeliveries = myDeliveries.filter(
    (d) => d.status === 'assigned' || d.status === 'picked_up'
  );
  const completedDeliveries = myDeliveries.filter(
    (d) => d.status === 'delivered' || d.status === 'cancelled'
  );
  const displayedList = viewMode === 'active' ? activeDeliveries : completedDeliveries;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-slate-200 pb-5">
        <div>
          <p className="text-xs text-slate-500">
            Espace Livreur isolé · {courierDoc.deliveryZone} · Statut :{' '}
            <strong className={isApproved ? 'text-emerald-700' : 'text-amber-700'}>
              {isApproved ? 'Validé' : 'En attente de validation'}
            </strong>
          </p>
          <h1 className="text-2xl font-bold text-slate-900">{courierDoc.fullName}</h1>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={toggleAvailability}
            className={`rounded-xl px-3.5 py-2 text-xs font-bold min-h-[40px] border ${
              courierDoc.isAvailable
                ? 'bg-emerald-700 text-white border-emerald-700'
                : 'bg-white text-slate-700 border-slate-200'
            }`}
          >
            {courierDoc.isAvailable ? 'Disponible en course' : 'En pause'}
          </button>
          <button
            type="button"
            onClick={() => setViewMode('active')}
            className={`rounded-xl px-3.5 py-2 text-xs font-bold min-h-[40px] border ${
              viewMode === 'active'
                ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                : 'bg-white text-slate-700 border-slate-200'
            }`}
          >
            Courses actives ({activeDeliveries.length})
          </button>
          <button
            type="button"
            onClick={() => setViewMode('history')}
            className={`rounded-xl px-3.5 py-2 text-xs font-bold min-h-[40px] border ${
              viewMode === 'history'
                ? 'bg-[#0F3D8A] text-white border-[#0F3D8A]'
                : 'bg-white text-slate-700 border-slate-200'
            }`}
          >
            Historique ({completedDeliveries.length})
          </button>
        </div>
      </div>

      {!isApproved && (
        <div className="rounded-2xl bg-amber-50 border border-amber-200 p-5 text-xs text-amber-950">
          Votre profil livreur est en attente de validation par l’administrateur propriétaire avant l’attribution de courses.
        </div>
      )}

      {displayedList.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center space-y-2">
          <Truck className="w-10 h-10 text-slate-400 mx-auto" />
          <p className="text-sm font-semibold text-slate-800">
            {viewMode === 'active'
              ? 'Aucune course active attribuée pour le moment.'
              : 'Aucune livraison terminée dans votre historique.'}
          </p>
        </div>
      ) : (
        <div className="space-y-5">
          {displayedList.map((del) => (
            <div
              key={del.id}
              className="bg-white rounded-2xl border border-slate-200 p-6 space-y-5"
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                <div>
                  <p className="text-xs text-slate-500">
                    {formatFsTimestamp(del.createdAt) || 'Aujourd’hui'} · Zone : {del.zoneName}
                  </p>
                  <h2 className="text-lg font-bold text-slate-900 font-mono tabular-nums">
                    {del.orderNumber} · Frais course : {del.deliveryFee} {del.currency}
                  </h2>
                </div>
                <span className="text-xs font-bold text-[#0F3D8A]">
                  {DELIVERY_STATUS_LABELS[del.status as DeliveryStatus] || del.status}
                </span>
              </div>

              {del.isCod && (
                <div className="rounded-xl bg-orange-50 border border-orange-200 px-4 py-3 text-xs text-orange-950 flex items-center justify-between gap-3">
                  <span>
                    <strong>Paiement à la livraison (Espèces)</strong> — Montant exact à encaisser auprès du client et à reverser pour rapprochement :
                  </span>
                  <span className="text-base font-extrabold font-mono tabular-nums shrink-0">
                    {Number(del.codAmountToCollect).toLocaleString('fr-CD')} {del.currency}
                  </span>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div className="rounded-xl bg-slate-50 p-4 space-y-1.5">
                  <p className="font-bold text-slate-900 flex items-center gap-1.5">
                    <Store className="w-4 h-4 text-[#0F3D8A]" />
                    <span>1. Récupération chez le vendeur</span>
                  </p>
                  <p className="font-semibold text-slate-800">{del.shopName}</p>
                  <p className="text-slate-600">{del.shopAddress}</p>
                  {del.shopPhone && (
                    <p className="text-slate-700 font-mono flex items-center gap-1">
                      <Phone className="w-3.5 h-3.5" /> {del.shopPhone}
                    </p>
                  )}
                  <p className="pt-1 text-slate-600">Colis : {del.itemsSummary}</p>
                </div>

                <div className="rounded-xl bg-slate-50 p-4 space-y-1.5">
                  <p className="font-bold text-slate-900 flex items-center gap-1.5">
                    <MapPin className="w-4 h-4 text-orange-600" />
                    <span>2. Adresse de livraison client</span>
                  </p>
                  <p className="font-semibold text-slate-800">{del.customerName}</p>
                  <p className="text-slate-600">{del.deliveryAddress}</p>
                  <p className="text-slate-700 font-mono flex items-center gap-1">
                    <Phone className="w-3.5 h-3.5" /> {del.customerPhone}
                  </p>
                </div>
              </div>

              {/* Actions terrain sur téléphone Android */}
              {del.status === 'assigned' && (
                <button
                  type="button"
                  disabled={busyDeliveryId === del.id}
                  onClick={() => handleConfirmPickup(del)}
                  className="w-full rounded-xl bg-[#0F3D8A] py-3.5 text-sm font-bold text-white hover:bg-[#0A2B63] min-h-[48px] flex items-center justify-center gap-2"
                >
                  <PackageCheck className="w-4 h-4" />
                  <span>Confirmer la récupération du colis chez le vendeur</span>
                </button>
              )}

              {del.status === 'picked_up' && (
                <form
                  onSubmit={(e) => handleConfirmHandoverWithCode(e, del)}
                  className="pt-2 border-t border-slate-100 flex flex-col sm:flex-row gap-3"
                >
                  <div className="relative flex-1">
                    <KeyRound className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]{6}"
                      maxLength={6}
                      required
                      value={codeInputs[del.id] || ''}
                      onChange={(e) =>
                        setCodeInputs((prev) => ({
                          ...prev,
                          [del.id]: e.target.value.replace(/\D/g, '').slice(0, 6),
                        }))
                      }
                      placeholder="Code unique à 6 chiffres du client"
                      className="w-full rounded-xl border border-slate-300 pl-10 pr-4 py-3 text-base font-mono tracking-widest font-bold min-h-[48px]"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={busyDeliveryId === del.id}
                    className="rounded-xl bg-emerald-700 px-6 py-3 text-sm font-bold text-white hover:bg-emerald-800 min-h-[48px] shrink-0 flex items-center justify-center gap-2"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Valider la remise au client</span>
                  </button>
                </form>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
