import type { Asset, Layer, Project } from './types';
import { prop } from './animation';
import { clamp, uid } from './utils';
import { saveAsset, type StoredAsset } from './storage';

/* ----------------------------- media library ------------------------------ */

export class MediaLibrary {
  private cache = new Map<string, HTMLImageElement | HTMLVideoElement | HTMLAudioElement>();
  private pendingSeeks = new Map<string, number>();

  constructor(public assets: Record<string, Asset>) {}

  private key(assetId: string, layerId: string): string {
    return `${assetId}::${layerId}`;
  }

  image(asset: Asset): HTMLImageElement | null {
    if (asset.type !== 'image') return null;
    const k = `img::${asset.id}`;
    let el = this.cache.get(k) as HTMLImageElement | undefined;
    if (!el) {
      el = new Image();
      el.decoding = 'async';
      el.src = asset.url;
      this.cache.set(k, el);
    } else if (el.src !== asset.url && !asset.url.startsWith('data:')) {
      el.src = asset.url;
    }
    return el;
  }

  video(asset: Asset, layerId: string): HTMLVideoElement | null {
    if (asset.type !== 'video') return null;
    const k = this.key(asset.id, layerId);
    let el = this.cache.get(k) as HTMLVideoElement | undefined;
    if (!el) {
      el = document.createElement('video');
      el.src = asset.url;
      el.muted = true;
      el.playsInline = true;
      el.preload = 'auto';
      el.setAttribute('playsinline', '');
      el.crossOrigin = 'anonymous';
      el.load();
      el.addEventListener('seeked', () => this.pendingSeeks.delete(k));
      this.cache.set(k, el);
    }
    return el;
  }

  audio(asset: Asset, layerId: string): HTMLAudioElement | null {
    if (asset.type !== 'audio' && asset.type !== 'video') return null;
    const k = this.key(asset.id, layerId);
    let el = this.cache.get(k) as HTMLAudioElement | undefined;
    if (!el) {
      el = document.createElement('audio');
      el.src = asset.url;
      el.preload = 'auto';
      el.crossOrigin = 'anonymous';
      el.load();
      this.cache.set(k, el);
    }
    return el;
  }

  sourceTime(layer: Layer, time: number, project: Project): number {
    const asset = layer.sourceId ? this.assets[layer.sourceId] : undefined;
    const speed = clamp(prop(layer, 'speed', time - layer.start, 1), 0.05, 16);
    let t = layer.inPoint + Math.max(0, time - layer.start) * speed;
    const dur = asset?.duration || 0;
    if (dur > 0 && layer.style.loop && (layer.type === 'video' || layer.type === 'audio') && t > dur) {
      t = t % dur;
    }
    return Math.max(0, t);
  }

  /** Keep media elements in sync with the playhead. Called on every frame. */
  sync(project: Project, time: number, playing: boolean): void {
    const active = new Set<string>();
    for (const layer of project.layers) {
      if (!layer.visible || !layer.sourceId) continue;
      const asset = this.assets[layer.sourceId];
      if (!asset) continue;
      if (layer.type !== 'video' && layer.type !== 'audio') continue;
      if (time < layer.start - 0.001 || time >= layer.start + layer.duration) continue;
      const local = this.sourceTime(layer, time, project);
      const speed = clamp(prop(layer, 'speed', time - layer.start, 1), 0.05, 16);
      const volume = clamp(prop(layer, 'volume', time - layer.start, 100) / 100, 0, 1);
      const el = layer.type === 'video'
        ? this.video(asset, layer.id)
        : this.audio(asset, layer.id);
      if (!el) continue;
      const k = this.key(asset.id, layer.id);
      active.add(k);
      el.volume = volume;
      el.playbackRate = speed;
      if (layer.type === 'video') {
        (el as HTMLVideoElement).muted = !layer.style.audio;
      }
      const target = asset.duration > 0 ? Math.min(local, Math.max(0, asset.duration - 0.02)) : local;
      const drift = Math.abs(el.currentTime - target);
      if (!playing || drift > 0.35) {
        if (drift > (playing ? 0.25 : 0.03) && !this.pendingSeeks.has(k)) {
          this.pendingSeeks.set(k, target);
          try { el.currentTime = target; } catch { /* not seekable yet */ }
        }
      }
      if (playing && el.paused) {
        void el.play().catch(() => undefined);
      } else if (!playing && !el.paused) {
        el.pause();
      }
    }
    for (const [k, el] of this.cache) {
      if (k.startsWith('img::')) continue;
      if (!active.has(k) && 'pause' in el && !(el as HTMLMediaElement).paused) {
        (el as HTMLMediaElement).pause();
      }
    }
  }

  /** Frame-accurate seek for rendering a single frame (PNG / frame export). */
  async seekExact(project: Project, time: number): Promise<void> {
    const waits: Promise<void>[] = [];
    for (const layer of project.layers) {
      if (!layer.visible || layer.type !== 'video' || !layer.sourceId) continue;
      if (time < layer.start || time >= layer.start + layer.duration) continue;
      const asset = this.assets[layer.sourceId];
      if (!asset) continue;
      const el = this.video(asset, layer.id);
      if (!el) continue;
      const target = Math.min(this.sourceTime(layer, time, project), Math.max(0, asset.duration - 0.02));
      if (el.readyState < 2) {
        await new Promise<void>(resolve => {
          const done = () => { el.removeEventListener('loadeddata', done); resolve(); };
          el.addEventListener('loadeddata', done);
          setTimeout(done, 1500);
        });
      }
      if (Math.abs(el.currentTime - target) < 0.008) continue;
      waits.push(new Promise<void>(resolve => {
        let done = false;
        const finish = () => {
          if (done) return;
          done = true;
          el.removeEventListener('seeked', finish);
          resolve();
        };
        el.addEventListener('seeked', finish);
        try { el.currentTime = target; } catch { finish(); }
        setTimeout(finish, 900);
      }));
    }
    await Promise.all(waits);
  }

  /** Elements that are carrying audio during playback/export. */
  activeAudioElements(project: Project, time: number): HTMLMediaElement[] {
    const out: HTMLMediaElement[] = [];
    for (const layer of project.layers) {
      if (!layer.visible || !layer.sourceId) continue;
      if (time < layer.start || time >= layer.start + layer.duration) continue;
      const asset = this.assets[layer.sourceId];
      if (!asset) continue;
      if (layer.type === 'audio' || (layer.type === 'video' && layer.style.audio)) {
        const el = layer.type === 'video' ? this.video(asset, layer.id) : this.audio(asset, layer.id);
        if (el) out.push(el);
      }
    }
    return out;
  }

  pauseAll(): void {
    for (const el of this.cache.values()) {
      if ('pause' in el && !(el as HTMLMediaElement).paused) (el as HTMLMediaElement).pause();
    }
  }

  allElements(): (HTMLVideoElement | HTMLAudioElement)[] {
    return [...this.cache.values()].filter(el => 'play' in el) as (HTMLVideoElement | HTMLAudioElement)[];
  }

  dispose(): void {
    this.pauseAll();
    for (const el of this.cache.values()) {
      if (el instanceof HTMLMediaElement) { el.removeAttribute('src'); el.load(); }
    }
    this.cache.clear();
  }
}

/* --------------------------- importing files ------------------------------ */

function typeFromMime(file: File): Asset['type'] | null {
  const m = file.type.toLowerCase();
  const n = file.name.toLowerCase();
  if (m.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|avif|svg)$/.test(n)) return 'image';
  if (m.startsWith('video/') || /\.(mp4|webm|mov|m4v|ogv|mkv)$/.test(n)) return 'video';
  if (m.startsWith('audio/') || /\.(mp3|wav|ogg|m4a|aac|flac|opus)$/.test(n)) return 'audio';
  if (/\.(ttf|otf|woff2?)$/.test(n)) return 'font';
  return null;
}

function probeMedia(url: string, type: 'image' | 'video' | 'audio'): Promise<{ width: number; height: number; duration: number; thumbnail?: string }> {
  return new Promise(resolve => {
    if (type === 'image') {
      const img = new Image();
      img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight, duration: 0, thumbnail: makeThumb(img, img.naturalWidth, img.naturalHeight) });
      img.onerror = () => resolve({ width: 0, height: 0, duration: 0 });
      img.src = url;
      return;
    }
    const el = document.createElement(type);
    el.preload = 'metadata';
    el.muted = true;
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      const v = el as HTMLVideoElement;
      const w = type === 'video' ? v.videoWidth : 0;
      const h = type === 'video' ? v.videoHeight : 0;
      let thumb: string | undefined;
      if (type === 'video' && v.readyState >= 2 && w > 0) {
        try { thumb = makeThumb(v, w, h); } catch { /* tainted or unavailable */ }
      }
      resolve({
        width: w, height: h,
        duration: Number.isFinite(el.duration) && el.duration > 0 ? el.duration : 0,
        thumbnail: thumb,
      });
    };
    el.addEventListener('loadedmetadata', finish, { once: true });
    el.addEventListener('error', finish, { once: true });
    el.src = url;
    setTimeout(finish, 4000);
  });
}

function makeThumb(src: CanvasImageSource, w: number, h: number): string {
  const c = document.createElement('canvas');
  c.width = 96; c.height = 96;
  const ctx = c.getContext('2d');
  if (!ctx) return '';
  const scale = Math.min(96 / (w || 1), 96 / (h || 1));
  const dw = (w || 1) * scale, dh = (h || 1) * scale;
  ctx.fillStyle = '#1a1b26';
  ctx.fillRect(0, 0, 96, 96);
  ctx.drawImage(src, (96 - dw) / 2, (96 - dh) / 2, dw, dh);
  return c.toDataURL('image/jpeg', 0.7);
}

export async function importFile(file: File): Promise<Asset | null> {
  const type = typeFromMime(file);
  if (!type) return null;
  const url = URL.createObjectURL(file);
  const id = uid('asset');

  if (type === 'font') {
    const family = `Custom_${id.slice(-6)}`;
    try {
      const buf = await file.arrayBuffer();
      const face = new FontFace(family, buf);
      await face.load();
      document.fonts.add(face);
    } catch (e) {
      console.warn('Font could not be loaded', e);
      return null;
    }
    const asset: Asset = {
      id, name: file.name, type, mime: file.type, width: 0, height: 0, duration: 0,
      url, blob: file, fontFamily: family,
    };
    await saveAsset({ id, name: file.name, type, mime: file.type, width: 0, height: 0, duration: 0, blob: file, fontFamily: family });
    return asset;
  }

  const probe = await probeMedia(url, type);
  const asset: Asset = {
    id, name: file.name, type, mime: file.type || 'application/octet-stream',
    width: probe.width, height: probe.height, duration: probe.duration,
    url, blob: file, thumbnail: probe.thumbnail,
  };
  const rec: StoredAsset = {
    id, name: asset.name, type, mime: asset.mime, width: probe.width,
    height: probe.height, duration: probe.duration, blob: file, thumbnail: probe.thumbnail,
  };
  await saveAsset(rec);
  return asset;
}

export async function importMany(files: FileList | File[]): Promise<Asset[]> {
  const list = Array.from(files);
  const results = await Promise.all(list.map(f => importFile(f).catch(() => null)));
  return results.filter((a): a is Asset => !!a);
}

export function assetFromDataUrl(name: string, dataUrl: string, type: Asset['type'], extra: Partial<Asset> = {}): Asset | null {
  const blob = dataUrlToBlobSafe(dataUrl);
  if (!blob) return null;
  return {
    id: uid('asset'), name, type, mime: blob.type || 'application/octet-stream',
    width: extra.width ?? 0, height: extra.height ?? 0, duration: extra.duration ?? 0,
    url: dataUrl, blob, thumbnail: extra.thumbnail, builtin: extra.builtin ?? false,
  };
}

function dataUrlToBlobSafe(dataUrl: string): Blob | null {
  const m = /^data:([^;,]+)?(;base64)?,(.*)$/s.exec(dataUrl.trim());
  if (!m) return null;
  try {
    if (m[2]) {
      const bin = atob(m[3]);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return new Blob([bytes], { type: m[1] || 'application/octet-stream' });
    }
    return new Blob([decodeURIComponent(m[3])], { type: m[1] || 'application/octet-stream' });
  } catch { return null; }
}

/* --------------------------- built-in sample media ------------------------- */

export function createBuiltinAssets(): Asset[] {
  const specs: { name: string; a: string; b: string; c: string; kind: number }[] = [
    { name: 'Aurora.webp', a: '#4f46e5', b: '#ec4899', c: '#22d3ee', kind: 0 },
    { name: 'Sunset.webp', a: '#f97316', b: '#7c3aed', c: '#fbbf24', kind: 1 },
    { name: 'Ocean.webp', a: '#0ea5e9', b: '#14b8a6', c: '#1e3a8a', kind: 2 },
    { name: 'Grid.webp', a: '#18181b', b: '#3f3f46', c: '#8b5cf6', kind: 3 },
  ];
  return specs.map(s => {
    const c = document.createElement('canvas');
    c.width = 720; c.height = 1280;
    const ctx = c.getContext('2d')!;
    const g = ctx.createLinearGradient(0, 0, 720, 1280);
    g.addColorStop(0, s.a); g.addColorStop(0.55, s.b); g.addColorStop(1, s.c);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 720, 1280);
    if (s.kind === 3) {
      ctx.strokeStyle = 'rgba(255,255,255,.14)';
      ctx.lineWidth = 2;
      for (let x = 0; x <= 720; x += 60) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, 1280); ctx.stroke(); }
      for (let y = 0; y <= 1280; y += 60) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(720, y); ctx.stroke(); }
    }
    const rg = ctx.createRadialGradient(360, 500, 20, 360, 640, 620);
    rg.addColorStop(0, 'rgba(255,255,255,.32)');
    rg.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = rg;
    ctx.fillRect(0, 0, 720, 1280);
    const url = c.toDataURL('image/jpeg', 0.85);
    return {
      id: `builtin_${s.name}`, name: s.name, type: 'image' as const,
      mime: 'image/jpeg', width: 720, height: 1280, duration: 0,
      url, thumbnail: url, builtin: true,
    };
  });
}

/** Renders a real 3-second sample video with MediaRecorder (no external files). */
export async function createSampleVideo(): Promise<Asset | null> {
  if (typeof MediaRecorder === 'undefined' || !HTMLCanvasElement.prototype.captureStream) return null;
  const mime = ['video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm']
    .find(m => MediaRecorder.isTypeSupported(m));
  if (!mime) return null;

  const c = document.createElement('canvas');
  c.width = 640; c.height = 360;
  const ctx = c.getContext('2d')!;
  const stream = c.captureStream(30);
  const chunks: BlobPart[] = [];
  let recorder: MediaRecorder;
  try {
    recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 2_000_000 });
  } catch { return null; }
  recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data); };
  const done = new Promise<Blob>(resolve => {
    recorder.onstop = () => resolve(new Blob(chunks, { type: 'video/webm' }));
  });
  recorder.start();

  const duration = 3;
  const t0 = performance.now();
  await new Promise<void>(resolve => {
    const draw = (now: number) => {
      const t = (now - t0) / 1000;
      const g = ctx.createLinearGradient(0, 0, 640, 360);
      g.addColorStop(0, '#312e81'); g.addColorStop(0.5, '#9d174d'); g.addColorStop(1, '#0e7490');
      ctx.fillStyle = g; ctx.fillRect(0, 0, 640, 360);
      for (let i = 0; i < 7; i++) {
        const a = t * 1.1 + i * 0.9;
        const x = 320 + Math.cos(a) * (70 + i * 32);
        const y = 180 + Math.sin(a * 1.3) * (45 + i * 17);
        ctx.beginPath();
        ctx.arc(x, y, 16 + i * 7, 0, Math.PI * 2);
        ctx.fillStyle = `hsla(${(i * 55 + t * 90) % 360}, 90%, 65%, .8)`;
        ctx.fill();
      }
      ctx.font = 'bold 34px system-ui, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillStyle = 'rgba(255,255,255,.92)';
      ctx.fillText('SAMPLE CLIP', 320, 195);
      if (t < duration) requestAnimationFrame(draw);
      else resolve();
    };
    requestAnimationFrame(draw);
  });
  recorder.stop();
  stream.getTracks().forEach(tr => tr.stop());
  const blob = await done;
  const url = URL.createObjectURL(blob);
  const asset: Asset = {
    id: uid('asset'), name: 'Sample Clip.webm', type: 'video', mime: 'video/webm',
    width: 640, height: 360, duration, url, blob, builtin: true,
  };
  await saveAsset({
    id: asset.id, name: asset.name, type: 'video', mime: asset.mime,
    width: 640, height: 360, duration, blob,
  });
  return asset;
}

/** Re-create blob URLs + elements for assets restored from IndexedDB. */
export function rehydrateAsset(rec: StoredAsset): Asset {
  return {
    id: rec.id, name: rec.name, type: rec.type, mime: rec.mime,
    width: rec.width, height: rec.height, duration: rec.duration,
    url: URL.createObjectURL(rec.blob), blob: rec.blob,
    thumbnail: rec.thumbnail, fontFamily: rec.fontFamily, builtin: true,
  };
}
