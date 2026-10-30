export type StoredMedia = {
  id: string;
  name: string;
  type: string;
  blob: Blob;
  createdAt: number;
};

const DATABASE_NAME = 'editorshub-media-library';
const DATABASE_VERSION = 1;
const STORE_NAME = 'assets';
let databasePromise: Promise<IDBDatabase> | undefined;

function openDatabase(): Promise<IDBDatabase> {
  if (typeof indexedDB === 'undefined') return Promise.reject(new Error('Persistent media storage is not available in this browser.'));
  if (databasePromise) return databasePromise;
  databasePromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(STORE_NAME)) database.createObjectStore(STORE_NAME, { keyPath: 'id' });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => {
      databasePromise = undefined;
      reject(request.error ?? new Error('Could not open the media library.'));
    };
    request.onblocked = () => {
      databasePromise = undefined;
      reject(new Error('The media library is busy. Close other EditorsHub tabs and try again.'));
    };
  });
  return databasePromise;
}

export async function saveMediaAsset(blob: Blob, name: string): Promise<string> {
  const database = await openDatabase();
  const id = `asset-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`}`;
  const record: StoredMedia = { id, name, type: blob.type || 'application/octet-stream', blob, createdAt: Date.now() };
  await new Promise<void>((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, 'readwrite');
    transaction.objectStore(STORE_NAME).put(record);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('Could not save this media file.'));
    transaction.onabort = () => reject(transaction.error ?? new Error('Saving this media file was cancelled.'));
  });
  return id;
}

export async function getMediaAsset(id: string): Promise<StoredMedia | null> {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const request = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(id);
    request.onsuccess = () => resolve((request.result as StoredMedia | undefined) ?? null);
    request.onerror = () => reject(request.error ?? new Error('Could not read the media library.'));
  });
}

export async function deleteMediaAsset(id: string): Promise<void> {
  const database = await openDatabase();
  await new Promise<void>((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, 'readwrite');
    transaction.objectStore(STORE_NAME).delete(id);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('Could not remove this media file.'));
  });
}

export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('Could not package this media file.'));
    reader.onerror = () => reject(reader.error ?? new Error('Could not package this media file.'));
    reader.readAsDataURL(blob);
  });
}

export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const response = await fetch(dataUrl);
  if (!response.ok) throw new Error('The embedded project media could not be decoded.');
  return response.blob();
}

export function inspectMediaDuration(src: string, kind: 'audio' | 'video'): Promise<number> {
  return new Promise((resolve) => {
    const element = document.createElement(kind);
    const finish = (duration: number) => {
      element.onloadedmetadata = null;
      element.onerror = null;
      element.removeAttribute('src');
      element.load();
      resolve(Number.isFinite(duration) && duration > 0 ? duration : 0);
    };
    element.preload = 'metadata';
    element.onloadedmetadata = () => finish(element.duration);
    element.onerror = () => finish(0);
    element.src = src;
    element.load();
  });
}

export async function buildAudioWaveform(blob: Blob, bars = 96): Promise<number[]> {
  if (blob.size > 48 * 1024 * 1024 || typeof window === 'undefined') return [];
  const AudioContextConstructor = window.AudioContext;
  if (!AudioContextConstructor) return [];
  let context: AudioContext | undefined;
  try {
    context = new AudioContextConstructor();
    const decoded = await context.decodeAudioData(await blob.arrayBuffer());
    const channel = decoded.getChannelData(0);
    const result: number[] = [];
    const samplesPerBar = Math.max(1, Math.floor(channel.length / bars));
    for (let bar = 0; bar < bars; bar++) {
      const start = bar * samplesPerBar;
      const end = Math.min(channel.length, start + samplesPerBar);
      let peak = 0;
      for (let sample = start; sample < end; sample += 4) peak = Math.max(peak, Math.abs(channel[sample] ?? 0));
      result.push(peak);
    }
    const maximum = Math.max(...result, .001);
    return result.map((value) => Math.max(.08, value / maximum));
  } catch {
    return [];
  } finally {
    if (context && context.state !== 'closed') await context.close().catch(() => undefined);
  }
}
