/* ------------------------------------------------------------------------- *
 * Ultra-style launcher: persisted preferences, the element library, and all
 * import/export helpers. Every helper here performs a real operation.
 * ------------------------------------------------------------------------- */
import { unzipSync } from 'fflate';
import jsQR from 'jsqr';
import { editor } from '../editor/store';
import { createProject } from '../editor/defaults';
import { parseMotionXml, projectToXml, type XmlImportResult } from '../editor/xml';
import { importMany, assetFromDataUrl } from '../editor/media';
import { downloadBlob, safeFileName, uid } from '../editor/utils';
import type { Asset, Project } from '../editor/types';

/* ------------------------------- preferences ------------------------------ */
export interface Preferences {
  lowQualityPreview: boolean;
  showOffTimeline: boolean;
  rememberAddPage: boolean;
  defaultLayerDuration: number;
  audioResampling: 'low' | 'high' | 'ultra';
  addPage: 'add' | 'shape' | 'templates';
  showDeviceChrome: boolean;
}

const PREF_KEY = 'editorshub.ultra.preferences.v1';
export const DEFAULT_PREFS: Preferences = {
  lowQualityPreview: false,
  showOffTimeline: true,
  rememberAddPage: false,
  defaultLayerDuration: 2,
  audioResampling: 'ultra',
  addPage: 'add',
  showDeviceChrome: true,
};

export function loadPrefs(): Preferences {
  try {
    const raw = localStorage.getItem(PREF_KEY);
    if (!raw) return { ...DEFAULT_PREFS };
    return { ...DEFAULT_PREFS, ...JSON.parse(raw) } as Preferences;
  } catch { return { ...DEFAULT_PREFS }; }
}

export function savePrefs(p: Preferences): void {
  try { localStorage.setItem(PREF_KEY, JSON.stringify(p)); } catch { /* storage full / private mode */ }
}

export const SAMPLE_RATE: Record<Preferences['audioResampling'], number> = {
  low: 22050, high: 44100, ultra: 48000,
};

/* ------------------------------ element library ---------------------------- */
export interface SavedElement {
  id: string;
  name: string;
  xml: string;
  createdAt: number;
  width: number;
  height: number;
}
const ELEMENT_KEY = 'editorshub.ultra.elements.v1';

export function listElements(): SavedElement[] {
  try { return JSON.parse(localStorage.getItem(ELEMENT_KEY) ?? '[]') as SavedElement[]; }
  catch { return []; }
}

export async function saveCurrentAsElement(name: string): Promise<SavedElement> {
  const { xml } = await projectToXml(editor.project, editor.assets, { embedMedia: true, pretty: true });
  const items = listElements();
  const rec: SavedElement = {
    id: uid('elem'), name: name.trim() || editor.project.name,
    xml, createdAt: Date.now(), width: editor.project.width, height: editor.project.height,
  };
  try { localStorage.setItem(ELEMENT_KEY, JSON.stringify([rec, ...items])); }
  catch { throw new Error('Local storage is full — delete an element and try again.'); }
  return rec;
}

export function deleteElement(id: string): void {
  try { localStorage.setItem(ELEMENT_KEY, JSON.stringify(listElements().filter(e => e.id !== id))); }
  catch { /* ignore */ }
}

/* --------------------------------- XML I/O -------------------------------- */
export function downloadXml(name: string, xml: string): void {
  downloadBlob(new Blob([xml], { type: 'application/xml' }), `${safeFileName(name)}.xml`);
}

export async function xmlOfProject(project: Project = editor.project, embed = true): Promise<string> {
  const { xml } = await projectToXml(project, editor.assets, { embedMedia: embed, pretty: true });
  return xml;
}

export async function parseXml(source: string): Promise<XmlImportResult> {
  return parseMotionXml(source);
}

/** Turn a parsed XML result into a real project, either replacing or layering. */
export function applyXmlResult(result: XmlImportResult, mode: 'project' | 'layers'): void {
  if (mode === 'project') {
    const p = result.project ?? (() => {
      const np = createProject('Imported Preset', editor.project.width, editor.project.height);
      np.layers = result.layers;
      np.duration = Math.max(np.duration, ...result.layers.map(l => l.start + l.duration));
      return np;
    })();
    editor.replaceProject(p, result.assets);
    editor.setScreen('editor');
  } else {
    editor.addImportedLayers(result.layers, result.assets);
  }
  editor.saveNow();
}

/* --------------------------- project-package import ----------------------- */
const MIME_BY_EXT: Record<string, string> = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif',
  webp: 'image/webp', svg: 'image/svg+xml', mp4: 'video/mp4', webm: 'video/webm',
  mov: 'video/quicktime', mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg',
  m4a: 'audio/mp4', ttf: 'font/ttf', otf: 'font/otf',
};

function bytesToDataUrl(name: string, bytes: Uint8Array): string {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  const mime = MIME_BY_EXT[ext] ?? 'application/octet-stream';
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return `data:${mime};base64,${btoa(binary)}`;
}

/** Convert a zipped .amproj-style package into parseable XML with media embedded. */
export function packageToXml(bytes: Uint8Array): { xml: string; media: number } {
  const files = unzipSync(bytes);
  const names = Object.keys(files);
  const xmlName = names.find(n => /\.(xml|amxml|txt)$/i.test(n));
  if (!xmlName) throw new Error('The package does not contain an .xml project file.');
  let xml = new TextDecoder().decode(files[xmlName]);
  const assets: string[] = [];
  let media = 0;
  for (const n of names) {
    if (n === xmlName || n.endsWith('/') || /\.(xml|amxml|txt|json|DS_Store)$/i.test(n)) continue;
    const ext = n.split('.').pop()?.toLowerCase() ?? '';
    if (!MIME_BY_EXT[ext]) continue;
    const url = bytesToDataUrl(n, files[n]);
    const short = n.split('/').pop() ?? n;
    const type = MIME_BY_EXT[ext].startsWith('video') ? 'video'
      : MIME_BY_EXT[ext].startsWith('audio') ? 'audio'
        : MIME_BY_EXT[ext].startsWith('font') ? 'font' : 'image';
    for (const id of [n, short, short.replace(/\.[^.]+$/, '')]) {
      assets.push(`<Asset id="${escapeAttr(id)}" name="${escapeAttr(short)}" type="${type}" src="${escapeAttr(url)}" />`);
    }
    media++;
  }
  if (assets.length) {
    const insert = `<Assets>${assets.join('')}</Assets>`;
    const m = /<\/([A-Za-z0-9_:-]+)>\s*$/.exec(xml);
    if (m) xml = xml.slice(0, m.index) + insert + xml.slice(m.index);
    else xml += insert;
  }
  return { xml, media };
}

function escapeAttr(v: string): string {
  return v.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** Fetch a remote XML project or zipped package. CORS failures are reported honestly. */
export async function fetchPackage(url: string): Promise<string> {
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error(`Server returned HTTP ${res.status}.`);
  const buf = new Uint8Array(await res.arrayBuffer());
  if (buf[0] === 0x50 && buf[1] === 0x4b) return packageToXml(buf).xml;
  return new TextDecoder().decode(buf);
}

/** Decode a QR image to its contained link using the platform detector or jsQR. */
export async function decodeQr(file: File): Promise<string> {
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas');
  canvas.width = bitmap.width; canvas.height = bitmap.height;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) throw new Error('Canvas is unavailable.');
  ctx.drawImage(bitmap, 0, 0);
  bitmap.close();
  const Detector = (window as unknown as {
    BarcodeDetector?: new (o: { formats: string[] }) => { detect: (src: CanvasImageSource) => Promise<{ rawValue: string }[]> };
  }).BarcodeDetector;
  if (Detector) {
    try {
      const found = await new Detector({ formats: ['qr_code'] }).detect(canvas);
      if (found[0]?.rawValue) return found[0].rawValue;
    } catch { /* fall through to jsQR */ }
  }
  const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const result = jsQR(img.data, img.width, img.height, { inversionAttempts: 'attemptBoth' });
  if (!result?.data) throw new Error('No QR code could be read from that image. Try a sharper screenshot.');
  return result.data;
}

/* ------------------------------ add layer ops ----------------------------- */
export async function addMediaFiles(files: FileList | File[]): Promise<number> {
  const assets = await importMany(files);
  if (!assets.length) throw new Error('No supported media files were found.');
  editor.addAssets(assets);
  for (const a of assets) {
    if (a.type === 'font') editor.notify(`Font “${a.name}” installed`, 'ok');
    else editor.addMediaLayer(a.id);
  }
  return assets.length;
}

export function addDrawing(dataUrl: string, name = 'Freehand drawing'): void {
  const asset = assetFromDataUrl(name, dataUrl, 'image');
  if (!asset) throw new Error('Could not create the drawing asset.');
  editor.addAssets([asset]);
  editor.addMediaLayer(asset.id);
}

export function addVectorPath(pathData: string): void {
  const id = editor.addLayer('shape', {
    name: 'Vector Path',
    duration: loadPrefs().defaultLayerDuration,
    color: '#ffffff',
  });
  editor.setShape(id, 'customPath');
  editor.setStyle(id, { pathData, fillType: 'solid' });
  editor.setProp(id, 'shapeWidth', Math.round(editor.project.width * 0.7));
  editor.setProp(id, 'shapeHeight', Math.round(editor.project.height * 0.7));
  editor.setProp(id, 'strokeWidth', 6);
  editor.setStyle(id, { strokeColor: '#ffffff' });
}

/** Save the selected layer as a standalone, re-importable XML preset. */
export async function saveSelectedLayerPreset(): Promise<void> {
  const layer = editor.selectedLayer;
  if (!layer) throw new Error('Select a layer first.');
  const p = createProject(layer.name, editor.project.width, editor.project.height);
  p.fps = editor.project.fps;
  p.background = editor.project.background;
  p.duration = layer.duration || 2;
  p.layers = [{ ...structuredClone(layer), start: 0 }];
  const xml = await xmlOfProject(p, true);
  downloadXml(`${layer.name} Preset`, xml);
}

export function assetOfLayer(id: string | null): Asset | null {
  if (!id) return null;
  return editor.assets[id] ?? null;
}

/* ---------------------------- saved templates ----------------------------- */
export interface SavedTemplate {
  id: string;
  name: string;
  width: number;
  height: number;
  duration: number;
  xml: string;
  createdAt: number;
}
const TEMPLATE_KEY = 'editorshub.ultra.templates.v1';
export function listTemplates(): SavedTemplate[] {
  try { return JSON.parse(localStorage.getItem(TEMPLATE_KEY) || '[]') as SavedTemplate[]; }
  catch { return []; }
}
export function saveTemplate(name: string, xml: string,
  width: number, height: number, duration: number): SavedTemplate {
  const t: SavedTemplate = {
    id: uid('tmpl'), name: name.trim() || 'Untitled template',
    width, height, duration, xml, createdAt: Date.now(),
  };
  const all = [t, ...listTemplates()].slice(0, 50);
  try { localStorage.setItem(TEMPLATE_KEY, JSON.stringify(all)); } catch { /* storage full */ }
  return t;
}
export function deleteTemplate(id: string): void {
  try { localStorage.setItem(TEMPLATE_KEY, JSON.stringify(listTemplates().filter(t => t.id !== id))); }
  catch { /* storage full */ }
}
