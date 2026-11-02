export const uid = (prefix = 'id'): string =>
  `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 9)}`;

export const clamp = (v: number, min: number, max: number): number =>
  Number.isFinite(v) ? Math.min(max, Math.max(min, v)) : min;

export const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;

export const round = (v: number, digits = 2): number => {
  const f = 10 ** digits;
  return Math.round(v * f) / f;
};

export const formatTime = (t: number, fps = 30): string => {
  const safe = Math.max(0, t);
  const m = Math.floor(safe / 60);
  const s = Math.floor(safe % 60);
  const fr = Math.floor((safe - Math.floor(safe)) * fps);
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}:${String(fr).padStart(2, '0')}`;
};

export const formatShortTime = (t: number): string => {
  if (t < 1) return `${Math.round(t * 100) / 100}s`;
  if (t < 60) return `${t.toFixed(t % 1 === 0 ? 0 : 1)}s`;
  const m = Math.floor(t / 60);
  return `${m}:${String(Math.floor(t % 60)).padStart(2, '0')}`;
};

/** Accepts #rgb, #rrggbb, #rrggbbaa, rgb(), rgba(), hsl() and named colors. */
export function normalizeColor(input: string, fallback = '#ffffff'): string {
  if (!input) return fallback;
  const s = input.trim();
  if (/^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(s)) return s;
  if (/^rgba?\(/i.test(s) || /^hsla?\(/i.test(s)) return s;
  const c = document.createElement('canvas').getContext('2d');
  if (c) {
    c.fillStyle = '#000000';
    c.fillStyle = s;
    const out = c.fillStyle as string;
    if (out && out !== '#000000') return out;
    if (s.toLowerCase() === 'black') return '#000000';
  }
  return fallback;
}

export function hexToRgb(hex: string): [number, number, number] {
  const s = hex.replace('#', '');
  const full = s.length === 3 ? s.split('').map(c => c + c).join('') : s;
  const num = parseInt(full.slice(0, 6) || 'ffffff', 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

export function withAlpha(color: string, alpha: number): string {
  const [r, g, b] = hexToRgb(color);
  return `rgba(${r},${g},${b},${clamp(alpha, 0, 1)})`;
}

export function hsl(h: number, s: number, l: number, a = 1): string {
  return `hsla(${h}, ${s}%, ${l}%, ${a})`;
}

export const escapeXml = (s: string): string =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;');

export const attrNum = (el: Element, names: string[], fallback = 0): number => {
  for (const n of names) {
    const v = el.getAttribute(n);
    if (v != null && v.trim() !== '' && Number.isFinite(Number(v))) return Number(v);
  }
  return fallback;
};

export const attrStr = (el: Element, names: string[], fallback = ''): string => {
  for (const n of names) {
    const v = el.getAttribute(n);
    if (v != null && v !== '') return v;
  }
  return fallback;
};

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export function downloadText(text: string, filename: string, mime = 'application/xml'): void {
  downloadBlob(new Blob([text], { type: mime }), filename);
}

export function dataUrlToBlob(dataUrl: string): Blob | null {
  const m = /^data:([^;,]+)?(;base64)?,(.*)$/s.exec(dataUrl.trim());
  if (!m) return null;
  try {
    const mime = m[1] || 'application/octet-stream';
    if (m[2]) {
      const bin = atob(m[3]);
      const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return new Blob([bytes], { type: mime });
    }
    return new Blob([decodeURIComponent(m[3])], { type: mime });
  } catch {
    return null;
  }
}

export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const fr = new FileReader();
    fr.onload = () => resolve(String(fr.result));
    fr.onerror = () => reject(fr.error);
    fr.readAsDataURL(blob);
  });
}

export function safeFileName(name: string): string {
  return (name || 'project').replace(/[^a-z0-9_\-. ]/gi, '_').slice(0, 80);
}

export function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(text);
  return Promise.reject(new Error('Clipboard unavailable'));
}
