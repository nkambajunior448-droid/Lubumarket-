import { initializeApp } from 'firebase/app';
import {
  GoogleAuthProvider,
  getAuth,
  signInWithPopup,
  signOut,
} from 'firebase/auth';
import {
  doc,
  getDocFromServer,
  getFirestore,
} from 'firebase/firestore';
import {
  getDownloadURL,
  getStorage,
  ref,
  uploadBytes,
} from 'firebase/storage';
import firebaseConfig from '../../firebase-applet-config.json';

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);
export const auth = getAuth(app);
export const storage = getStorage(app);
export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    isAnonymous?: boolean | null;
    tenantId?: string | null;
    providerInfo?: Array<{
      providerId?: string | null;
      email?: string | null;
    }>;
  };
}

export function handleFirestoreError(
  error: unknown,
  operationType: OperationType,
  path: string | null
): never {
  const errInfo: FirestoreErrorInfo = {
    error: error instanceof Error ? error.message : String(error),
    authInfo: {
      userId: auth.currentUser?.uid,
      email: auth.currentUser?.email,
      emailVerified: auth.currentUser?.emailVerified,
      isAnonymous: auth.currentUser?.isAnonymous,
      tenantId: auth.currentUser?.tenantId,
      providerInfo:
        auth.currentUser?.providerData?.map((provider) => ({
          providerId: provider.providerId,
          email: provider.email,
        })) || [],
    },
    operationType,
    path,
  };
  console.error('Firestore Error: ', JSON.stringify(errInfo));
  throw new Error(JSON.stringify(errInfo));
}

let connectionTested = false;
export async function testFirestoreConnection(): Promise<void> {
  if (connectionTested) return;
  connectionTested = true;
  try {
    await getDocFromServer(doc(db, 'exchangeRates', 'current'));
  } catch (error) {
    if (error instanceof Error && error.message.includes('the client is offline')) {
      console.error('Veuillez vérifier votre configuration Firebase (client hors ligne).');
    }
  }
}

export async function loginWithGoogle() {
  const result = await signInWithPopup(auth, googleProvider);
  return result.user;
}

export async function logoutUser() {
  await signOut(auth);
}

export function makeClientId(prefix: string): string {
  const rand = Math.random().toString(36).slice(2, 9);
  return `${prefix}-${Date.now().toString(36)}-${rand}`;
}

export async function apiPost<T = any>(
  endpoint: string,
  body: Record<string, unknown> = {}
): Promise<T> {
  const currentUser = auth.currentUser;
  if (!currentUser) {
    throw new Error('Veuillez vous connecter pour effectuer cette opération.');
  }
  const token = await currentUser.getIdToken();
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || `Erreur serveur (${res.status})`);
  }
  return data as T;
}

export interface StorageStatusResponse {
  bucket: string;
  operational: boolean;
  httpStatus: number;
  reason: string;
}

export interface ImageUploadResult {
  url: string;
  storageMode: 'firebase_storage' | 'firestore_inline_fallback';
  bucketName: string;
  diagnosticMessage: string;
}

export async function checkFirebaseStorageStatus(): Promise<StorageStatusResponse> {
  const bucket = firebaseConfig.storageBucket || '';
  try {
    const res = await fetch('/api/storage/status');
    if (res.ok) {
      return (await res.json()) as StorageStatusResponse;
    }
  } catch {
    // Fallback to direct check if API is unreachable
  }
  if (!bucket) {
    return {
      bucket: '',
      operational: false,
      httpStatus: 0,
      reason: 'Aucun storageBucket configuré dans firebase-applet-config.json.',
    };
  }
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 3500);
    const probe = await fetch(
      `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(bucket)}/o?maxResults=1`,
      { signal: controller.signal }
    );
    clearTimeout(timer);
    if (probe.status === 404) {
      return {
        bucket,
        operational: false,
        httpStatus: 404,
        reason: `Le bucket Firebase Storage (${bucket}) retourne HTTP 404 : il doit être activé manuellement dans la console Firebase -> Storage.`,
      };
    }
    return {
      bucket,
      operational: true,
      httpStatus: probe.status,
      reason: 'Bucket Firebase Storage détecté et accessible.',
    };
  } catch {
    return {
      bucket,
      operational: false,
      httpStatus: 0,
      reason: 'Impossible de joindre le service Firebase Storage.',
    };
  }
}

async function compressImageToDataUrl(
  file: File,
  maxDimension = 300,
  quality = 0.76
): Promise<string> {
  const rawDataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('Impossible de lire le fichier image sélectionné.'));
    reader.readAsDataURL(file);
  });

  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const element = new Image();
    element.onload = () => resolve(element);
    element.onerror = () => reject(new Error('Format d’image non pris en charge par le navigateur.'));
    element.src = rawDataUrl;
  });

  const renderAtScale = (dim: number, q: number): string => {
    const ratio = Math.min(1, dim / Math.max(img.width || 1, img.height || 1));
    const width = Math.max(1, Math.round(img.width * ratio));
    const height = Math.max(1, Math.round(img.height * ratio));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('Contexte graphique Canvas indisponible pour compresser l’image.');
    }
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(img, 0, 0, width, height);
    const webp = canvas.toDataURL('image/webp', q);
    if (webp.startsWith('data:image/webp')) {
      return webp;
    }
    return canvas.toDataURL('image/jpeg', q);
  };

  let out = renderAtScale(maxDimension, quality);
  if (out.length > 82000) {
    out = renderAtScale(200, 0.62);
  }
  if (out.length > 88000) {
    out = renderAtScale(140, 0.52);
  }
  return out;
}

export async function uploadProductOrShopImage(
  file: File,
  folder: 'products' | 'shops'
): Promise<ImageUploadResult> {
  const currentUser = auth.currentUser;
  if (!currentUser) {
    throw new Error('Connexion requise pour téléverser une image.');
  }
  if (!file.type.startsWith('image/')) {
    throw new Error('Veuillez sélectionner un fichier image valide (PNG, JPG, WebP).');
  }
  if (file.size > 4 * 1024 * 1024) {
    throw new Error("L'image ne doit pas dépasser 4 Mo.");
  }

  const bucketName = firebaseConfig.storageBucket || '';
  const storageStatus = await checkFirebaseStorageStatus();

  if (storageStatus.operational) {
    try {
      const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 60);
      const storageRef = ref(
        storage,
        `${folder}/${currentUser.uid}/${Date.now()}_${safeName}`
      );
      await uploadBytes(storageRef, file);
      const url = await getDownloadURL(storageRef);
      return {
        url,
        storageMode: 'firebase_storage',
        bucketName,
        diagnosticMessage: 'Image enregistrée dans Firebase Storage.',
      };
    } catch (err) {
      // Si les règles Firebase Storage refusent ou le bucket échoue, basculer honnêtement vers le repli compressé Firestore
      const compressedDataUrl = await compressImageToDataUrl(file);
      return {
        url: compressedDataUrl,
        storageMode: 'firestore_inline_fallback',
        bucketName,
        diagnosticMessage: `Téléversement Firebase Storage indisponible (${err instanceof Error ? err.message : 'erreur Storage'}) — image compressée enregistrée en repli dans Cloud Firestore.`,
      };
    }
  }

  const compressedDataUrl = await compressImageToDataUrl(file);
  return {
    url: compressedDataUrl,
    storageMode: 'firestore_inline_fallback',
    bucketName,
    diagnosticMessage: `Firebase Storage non initialisé sur le projet (HTTP ${storageStatus.httpStatus || 404}) — image compressée enregistrée directement dans Cloud Firestore en attendant l’activation du bucket Storage.`,
  };
}

