import type { Asset, Project } from './types';

/* Projects live in localStorage, binary media lives in IndexedDB. This keeps
 * projects small and makes imported videos/images survive a page reload. */

const DB_NAME = 'editorshub-motion';
const DB_VERSION = 1;
const STORE = 'assets';
const INDEX_KEY = 'ehm.projects';
const LAST_KEY = 'ehm.lastProject';

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

export interface StoredAsset {
  id: string;
  name: string;
  type: Asset['type'];
  mime: string;
  width: number;
  height: number;
  duration: number;
  blob: Blob;
  thumbnail?: string;
  fontFamily?: string;
}

export async function saveAsset(rec: StoredAsset): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).put(rec);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (e) {
    console.warn('Could not persist asset', e);
  }
}

export async function loadAssets(): Promise<StoredAsset[]> {
  try {
    const db = await openDb();
    return await new Promise<StoredAsset[]>((resolve, reject) => {
      const tx = db.transaction(STORE, 'readonly');
      const req = tx.objectStore(STORE).getAll();
      req.onsuccess = () => resolve(req.result as StoredAsset[]);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

export async function deleteAsset(id: string): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve) => {
      const tx = db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  } catch { /* ignore */ }
}

export interface ProjectMeta {
  id: string;
  name: string;
  updatedAt: number;
  width: number;
  height: number;
  duration: number;
  layerCount: number;
}

function readIndex(): ProjectMeta[] {
  try { return JSON.parse(localStorage.getItem(INDEX_KEY) || '[]'); }
  catch { return []; }
}

function writeIndex(list: ProjectMeta[]): void {
  localStorage.setItem(INDEX_KEY, JSON.stringify(list));
}

export function saveProject(p: Project): void {
  try {
    localStorage.setItem(`ehm.project.${p.id}`, JSON.stringify(p));
    const list = readIndex().filter(x => x.id !== p.id);
    list.unshift({
      id: p.id, name: p.name, updatedAt: p.updatedAt, width: p.width,
      height: p.height, duration: p.duration, layerCount: p.layers.length,
    });
    writeIndex(list.slice(0, 50));
    localStorage.setItem(LAST_KEY, p.id);
  } catch (e) {
    console.warn('Autosave failed (storage full?)', e);
  }
}

export function loadProject(id: string): Project | null {
  try {
    const raw = localStorage.getItem(`ehm.project.${id}`);
    return raw ? JSON.parse(raw) as Project : null;
  } catch { return null; }
}

export function lastProjectId(): string | null {
  return localStorage.getItem(LAST_KEY);
}

export function listProjects(): ProjectMeta[] {
  return readIndex().sort((a, b) => b.updatedAt - a.updatedAt);
}

export function deleteProject(id: string): void {
  localStorage.removeItem(`ehm.project.${id}`);
  writeIndex(readIndex().filter(x => x.id !== id));
  if (lastProjectId() === id) localStorage.removeItem(LAST_KEY);
}

export function setLastProject(id: string): void {
  localStorage.setItem(LAST_KEY, id);
}
