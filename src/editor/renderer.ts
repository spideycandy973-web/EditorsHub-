import type { Effect, Layer, LayerStyle, Project } from './types';
import { EFFECT_MAP } from './types';
import { prop, valueAt } from './animation';
import { clamp, hexToRgb, hsl, normalizeColor, withAlpha } from './utils';
import type { MediaLibrary } from './media';

type PixelOpKind = 'posterize' | 'threshold' | 'solarize' | 'duotone' | 'gradientMap'
  | 'levels' | 'colorBalance' | 'emboss' | 'edgeDetect' | 'halftone' | 'scanlines'
  | 'glitchBlocks' | 'ripple' | 'bulge' | 'twirl';
interface PixelOp { kind: PixelOpKind; a: number; b: number; c: number }
type ProceduralKind = 'lensFlare' | 'starfield' | 'bokeh' | 'rain';
interface Procedural { kind: ProceduralKind; amount: number; hue: number; speed: number }

interface FxState {
  pixelOps: PixelOp[];
  procedurals: Procedural[];
  mirror: number;              // 0 off | 1 x | 2 y | 3 xy
  kaleidoscope: number;        // segments, 0 = off
  motionBlur: { angle: number; distance: number } | null;
  baseFilter: string;
  dropShadows: string[];
  pixelate: number;
  sharpen: number;
  noise: number;
  chromatic: number;
  chromaKey: { hue: number; threshold: number; softness: number } | null;
  vignette: { amount: number; size: number } | null;
  wave: { amplitude: number; frequency: number; speed: number } | null;
  zoomBlur: { amount: number; steps: number } | null;
  tint: { amount: number; hue: number } | null;
  shake: { x: number; y: number };
  pulse: number;
}

const emptyFx = (): FxState => ({
  pixelOps: [], procedurals: [], mirror: 0, kaleidoscope: 0, motionBlur: null,
  baseFilter: '', dropShadows: [], pixelate: 0, sharpen: 0, noise: 0, chromatic: 0, chromaKey: null,
  vignette: null, wave: null, zoomBlur: null, tint: null,
  shake: { x: 0, y: 0 }, pulse: 1,
});

function p(fx: Effect, key: string, t: number, def = 0): number {
  return valueAt(fx.params[key], t, EFFECT_MAP[fx.type]?.params.find(x => x.key === key)?.default ?? def);
}

function buildFx(effects: Effect[], localTime: number, globalTime: number): FxState {
  const s = emptyFx();
  const filters: string[] = [];
  for (const fx of effects) {
    if (!fx.enabled) continue;
    switch (fx.type) {
      case 'blur': {
        const v = p(fx, 'amount', localTime);
        if (v > 0) filters.push(`blur(${v}px)`);
        break;
      }
      case 'colorAdjust': {
        const b = p(fx, 'brightness', localTime, 100);
        const c = p(fx, 'contrast', localTime, 100);
        const sat = p(fx, 'saturation', localTime, 100);
        const hue = p(fx, 'hue', localTime);
        const exp = p(fx, 'exposure', localTime, 100);
        filters.push(`brightness(${(b * exp / 10000 * 100).toFixed(1)}%) contrast(${c}%) saturate(${sat}%)`);
        if (hue) filters.push(`hue-rotate(${hue}deg)`);
        break;
      }
      case 'grayscale': filters.push(`grayscale(${p(fx, 'amount', localTime)}%)`); break;
      case 'sepia': filters.push(`sepia(${p(fx, 'amount', localTime)}%)`); break;
      case 'invert': filters.push(`invert(${p(fx, 'amount', localTime)}%)`); break;
      case 'sharpen': s.sharpen = p(fx, 'amount', localTime); break;
      case 'pixelate': s.pixelate = Math.max(0, p(fx, 'size', localTime)); break;
      case 'noise': s.noise = p(fx, 'amount', localTime); break;
      case 'chromatic': s.chromatic = p(fx, 'amount', localTime); break;
      case 'chromaKey': s.chromaKey = {
        hue: p(fx, 'hue', localTime, 120),
        threshold: p(fx, 'threshold', localTime, 32),
        softness: p(fx, 'softness', localTime, 18),
      }; break;
      case 'vignette': s.vignette = { amount: p(fx, 'amount', localTime), size: p(fx, 'size', localTime, 65) }; break;
      case 'wave': s.wave = { amplitude: p(fx, 'amplitude', localTime), frequency: p(fx, 'frequency', localTime, 6), speed: p(fx, 'speed', localTime, 2) }; break;
      case 'zoomBlur': s.zoomBlur = { amount: p(fx, 'amount', localTime), steps: Math.round(p(fx, 'steps', localTime, 5)) }; break;
      case 'tint': s.tint = { amount: p(fx, 'amount', localTime), hue: p(fx, 'hue', localTime, 210) }; break;
      case 'glow': {
        const r = p(fx, 'radius', localTime), i = p(fx, 'intensity', localTime, 70), h = p(fx, 'hue', localTime, 280);
        if (r > 0 && i > 0) {
          const col = hsl(h, 95, 65, clamp(i / 100, 0, 1));
          s.dropShadows.push(`drop-shadow(0px 0px ${(r / 2).toFixed(1)}px ${col})`);
          s.dropShadows.push(`drop-shadow(0px 0px ${(r / 4).toFixed(1)}px ${col})`);
        }
        break;
      }
      case 'dropShadow': {
        const blur = p(fx, 'blur', localTime), ox = p(fx, 'offsetX', localTime), oy = p(fx, 'offsetY', localTime);
        const op = clamp(p(fx, 'opacity', localTime, 65) / 100, 0, 1);
        s.dropShadows.push(`drop-shadow(${ox}px ${oy}px ${blur}px rgba(0,0,0,${op}))`);
        break;
      }
      case 'outline': {
        const w = p(fx, 'width', localTime), h = p(fx, 'hue', localTime, 190);
        const col = hsl(h, 95, 60, 1);
        const r = Math.max(1, w / 2);
        for (let a = 0; a < Math.PI * 2; a += Math.PI / 4) {
          s.dropShadows.push(`drop-shadow(${(Math.cos(a) * r).toFixed(1)}px ${(Math.sin(a) * r).toFixed(1)}px 0px ${col})`);
        }
        break;
      }
      case 'shake': {
        const a = p(fx, 'amount', localTime), f = p(fx, 'frequency', localTime, 12);
        s.shake.x = Math.sin(globalTime * f * 6.283) * a * 0.6 + Math.sin(globalTime * f * 13.7) * a * 0.4;
        s.shake.y = Math.cos(globalTime * f * 5.17) * a * 0.6 + Math.cos(globalTime * f * 11.3) * a * 0.4;
        break;
      }
      case 'pulse': {
        const a = p(fx, 'amount', localTime), bpm = p(fx, 'bpm', localTime, 120);
        s.pulse = 1 + (a / 100) * 0.5 * (1 + Math.sin(globalTime * bpm / 60 * Math.PI * 2));
        break;
      }
      /* ---------------- v3.6.0 master effect library ---------------------- */
      case 'posterize': s.pixelOps.push({ kind: 'posterize', a: p(fx, 'levels', localTime, 5), b: 0, c: 0 }); break;
      case 'threshold': s.pixelOps.push({ kind: 'threshold', a: p(fx, 'cut', localTime, 128), b: 0, c: 0 }); break;
      case 'solarize': s.pixelOps.push({ kind: 'solarize', a: p(fx, 'cut', localTime, 128), b: p(fx, 'amount', localTime, 100), c: 0 }); break;
      case 'duotone': s.pixelOps.push({ kind: 'duotone', a: p(fx, 'hueA', localTime, 230), b: p(fx, 'hueB', localTime, 35), c: p(fx, 'amount', localTime, 100) }); break;
      case 'gradientMap': s.pixelOps.push({ kind: 'gradientMap', a: p(fx, 'hue', localTime, 300), b: p(fx, 'amount', localTime, 100), c: 0 }); break;
      case 'levels': s.pixelOps.push({ kind: 'levels', a: p(fx, 'black', localTime, 0), b: p(fx, 'white', localTime, 255), c: p(fx, 'gamma', localTime, 100) }); break;
      case 'colorBalance': s.pixelOps.push({ kind: 'colorBalance', a: p(fx, 'r', localTime, 100), b: p(fx, 'g', localTime, 100), c: p(fx, 'b', localTime, 100) }); break;
      case 'emboss': s.pixelOps.push({ kind: 'emboss', a: p(fx, 'amount', localTime, 60), b: 0, c: 0 }); break;
      case 'edgeDetect': s.pixelOps.push({ kind: 'edgeDetect', a: p(fx, 'amount', localTime, 80), b: 0, c: 0 }); break;
      case 'halftone': s.pixelOps.push({ kind: 'halftone', a: p(fx, 'size', localTime, 9), b: p(fx, 'amount', localTime, 100), c: 0 }); break;
      case 'scanlines': s.pixelOps.push({ kind: 'scanlines', a: p(fx, 'spacing', localTime, 4), b: p(fx, 'amount', localTime, 55), c: 0 }); break;
      case 'glitchBlocks': s.pixelOps.push({ kind: 'glitchBlocks', a: p(fx, 'blocks', localTime, 12), b: p(fx, 'amount', localTime, 35), c: 0 }); break;
      case 'ripple': s.pixelOps.push({ kind: 'ripple', a: p(fx, 'amplitude', localTime, 18), b: p(fx, 'frequency', localTime, 8), c: globalTime }); break;
      case 'bulge': s.pixelOps.push({ kind: 'bulge', a: p(fx, 'amount', localTime, 40), b: 0, c: 0 }); break;
      case 'twirl': s.pixelOps.push({ kind: 'twirl', a: p(fx, 'angle', localTime, 90), b: 0, c: 0 }); break;
      case 'mirror': s.mirror = Math.round(p(fx, 'axis', localTime, 0)) + 1; break;
      case 'kaleidoscope': s.kaleidoscope = Math.round(p(fx, 'segments', localTime, 6)); break;
      case 'motionBlur': s.motionBlur = { angle: p(fx, 'angle', localTime, 0), distance: p(fx, 'distance', localTime, 24) }; break;
      case 'directionalBlur': s.motionBlur = { angle: p(fx, 'angle', localTime, 45), distance: p(fx, 'amount', localTime, 35) * 0.5 }; break;
      case 'lensFlare': s.procedurals.push({ kind: 'lensFlare', amount: p(fx, 'intensity', localTime, 60), hue: p(fx, 'hue', localTime, 35), speed: 0 }); break;
      case 'starfield': s.procedurals.push({ kind: 'starfield', amount: p(fx, 'density', localTime, 45), hue: p(fx, 'size', localTime, 3), speed: 0 }); break;
      case 'bokeh': s.procedurals.push({ kind: 'bokeh', amount: p(fx, 'count', localTime, 24), hue: p(fx, 'hue', localTime, 190), speed: 0 }); break;
      case 'rain': s.procedurals.push({ kind: 'rain', amount: p(fx, 'amount', localTime, 50), hue: 0, speed: p(fx, 'speed', localTime, 3) }); break;
    }
  }
  s.baseFilter = filters.join(' ').trim();
  return s;
}

/* --------------------- v3.6.0 real pixel operations ----------------------- */
/** Deterministic pseudo-random in [0,1), stable across frames for a given seed. */
function rnd(n: number): number {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}
function hueRgb(h: number, l: number): [number, number, number] {
  const a = Math.cos(h * Math.PI / 180) * 0.5 + 0.5;
  const b = Math.cos((h - 120) * Math.PI / 180) * 0.5 + 0.5;
  const c = Math.cos((h + 120) * Math.PI / 180) * 0.5 + 0.5;
  return [a * 255 * l, b * 255 * l, c * 255 * l];
}

function applyPixelOp(canvas: HTMLCanvasElement, op: PixelOp): void {
  const w = canvas.width, h = canvas.height;
  if (!w || !h) return;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return;
  const img = ctx.getImageData(0, 0, w, h);
  const d = img.data;
  const src = new Uint8ClampedArray(d);
  const idx = (x: number, y: number) => ((y < 0 ? 0 : y >= h ? h - 1 : y) * w + (x < 0 ? 0 : x >= w ? w - 1 : x)) * 4;
  const cl = (v: number) => v < 0 ? 0 : v > 255 ? 255 : v;

  switch (op.kind) {
    case 'posterize': {
      const levels = Math.max(2, Math.round(op.a));
      const q = 255 / (levels - 1);
      for (let i = 0; i < d.length; i += 4) {
        d[i] = Math.round(d[i] / q) * q; d[i + 1] = Math.round(d[i + 1] / q) * q; d[i + 2] = Math.round(d[i + 2] / q) * q;
      }
      break;
    }
    case 'threshold': {
      for (let i = 0; i < d.length; i += 4) {
        const l = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114);
        const v = l >= op.a ? 255 : 0;
        d[i] = d[i + 1] = d[i + 2] = v;
      }
      break;
    }
    case 'solarize': {
      // Channels brighter than the cut are mirrored back from white.
      const k = op.b / 100;
      for (let i = 0; i < d.length; i += 4) {
        for (let c = 0; c < 3; c++) {
          const v = src[i + c];
          d[i + c] = v > op.a ? cl(v + (255 - 2 * v) * k) : v;
        }
      }
      break;
    }
    case 'duotone': {
      const k = op.c / 100, A = hueRgb(op.a, 0.55), B = hueRgb(op.b, 1);
      for (let i = 0; i < d.length; i += 4) {
        const l = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114) / 255;
        for (let c = 0; c < 3; c++) d[i + c] = cl(d[i + c] * (1 - k) + (A[c] + (B[c] - A[c]) * l) * k);
      }
      break;
    }
    case 'gradientMap': {
      const k = op.b / 100;
      for (let i = 0; i < d.length; i += 4) {
        const l = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114) / 255;
        const C = hueRgb(op.a + l * 120, 0.35 + l * 0.65);
        for (let c = 0; c < 3; c++) d[i + c] = cl(d[i + c] * (1 - k) + C[c] * k);
      }
      break;
    }
    case 'levels': {
      const lo = op.a * 2.55, hi = Math.max(lo + 1, op.b), gam = 100 / Math.max(1, op.c);
      for (let i = 0; i < d.length; i += 4) {
        for (let c = 0; c < 3; c++) {
          const v = cl((d[i + c] - lo) / (hi - lo) * 255);
          d[i + c] = cl(255 * Math.pow(v / 255, gam));
        }
      }
      break;
    }
    case 'colorBalance': {
      const kr = op.a / 100, kg = op.b / 100, kb = op.c / 100;
      for (let i = 0; i < d.length; i += 4) {
        d[i] = cl(d[i] * kr); d[i + 1] = cl(d[i + 1] * kg); d[i + 2] = cl(d[i + 2] * kb);
      }
      break;
    }
    case 'emboss': case 'edgeDetect': {
      const k = op.a / 100;
      const K = op.kind === 'emboss'
        ? [-1, -1, 0, -1, 1, 1, 0, 1, 1]
        : [-1, -1, -1, -1, 8, -1, -1, -1, -1];
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let acc = 0;
        for (let ky = -1; ky <= 1; ky++) for (let kx = -1; kx <= 1; kx++) {
          const j = idx(x + kx, y + ky);
          acc += (src[j] * 0.299 + src[j + 1] * 0.587 + src[j + 2] * 0.114) * K[(ky + 1) * 3 + (kx + 1)];
        }
        const v = op.kind === 'emboss' ? cl(128 + acc * k) : cl(Math.abs(acc) * k);
        const o = (y * w + x) * 4;
        d[o] = d[o + 1] = d[o + 2] = v;
      }
      break;
    }
    case 'halftone': {
      const cell = Math.max(3, Math.round(op.a)), k = op.b / 100;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const cx = Math.floor(x / cell) * cell + cell / 2;
        const cy = Math.floor(y / cell) * cell + cell / 2;
        const j = idx(Math.round(cx), Math.round(cy));
        const l = (src[j] * 0.299 + src[j + 1] * 0.587 + src[j + 2] * 0.114) / 255;
        const dist = Math.hypot(x - cx, y - cy);
        const on = dist < (cell / 2) * (1 - l) ? 0 : 255;
        const o = (y * w + x) * 4;
        for (let c = 0; c < 3; c++) d[o + c] = cl(d[o + c] * (1 - k) + on * k);
      }
      break;
    }
    case 'scanlines': {
      const sp = Math.max(2, Math.round(op.a)), k = op.b / 100;
      for (let y = 0; y < h; y++) {
        const dark = (y % sp) < sp / 2 ? 1 - k : 1;
        for (let x = 0; x < w; x++) {
          const o = (y * w + x) * 4;
          d[o] *= dark; d[o + 1] *= dark; d[o + 2] *= dark;
        }
      }
      break;
    }
    case 'glitchBlocks': {
      const n = Math.max(2, Math.round(op.a)), k = op.b / 100;
      const band = Math.max(2, Math.floor(h / n));
      for (let b = 0; b < n; b++) {
        const y0 = b * band;
        const shift = Math.round((rnd(b * 3.7 + Math.floor(op.c * 4)) - 0.5) * w * 0.3 * k);
        if (!shift) continue;
        for (let y = y0; y < Math.min(h, y0 + band); y++) for (let x = 0; x < w; x++) {
          const o = (y * w + x) * 4, j = idx(x - shift, y);
          d[o] = src[j]; d[o + 1] = src[j + 1]; d[o + 2] = src[j + 2]; d[o + 3] = src[j + 3];
        }
      }
      break;
    }
    case 'ripple': case 'bulge': case 'twirl': {
      const cx = w / 2, cy = h / 2, maxR = Math.hypot(cx, cy);
      const amp = op.a, freq = op.b, t = op.c;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const dx = x - cx, dy = y - cy, r = Math.hypot(dx, dy);
        let sx = x, sy = y;
        if (op.kind === 'ripple') {
          const f = Math.sin(r / Math.max(1, maxR) * freq * 6.283 + t * 2) * amp;
          sx = cx + dx + (r > 0 ? dx / r * f : 0);
          sy = cy + dy + (r > 0 ? dy / r * f : 0);
        } else if (op.kind === 'bulge') {
          const k = 1 - (amp / 100) * (1 - Math.min(1, r / maxR));
          sx = cx + dx / Math.max(0.05, k); sy = cy + dy / Math.max(0.05, k);
        } else {
          const a = (amp * Math.PI / 180) * (1 - r / maxR);
          sx = cx + dx * Math.cos(a) - dy * Math.sin(a);
          sy = cy + dx * Math.sin(a) + dy * Math.cos(a);
        }
        const j = idx(Math.round(sx), Math.round(sy)), o = (y * w + x) * 4;
        d[o] = src[j]; d[o + 1] = src[j + 1]; d[o + 2] = src[j + 2]; d[o + 3] = src[j + 3];
      }
      break;
    }
  }
  ctx.putImageData(img, 0, 0);
}

function applyProcedural(canvas: HTMLCanvasElement, p: Procedural, time: number): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const w = canvas.width, h = canvas.height;
  ctx.save();
  if (p.kind === 'lensFlare') {
    const k = p.amount / 100, x = w * 0.72, y = h * 0.28;
    const g = ctx.createRadialGradient(x, y, 0, x, y, Math.max(w, h) * 0.25);
    g.addColorStop(0, `hsla(${p.hue},100%,92%,${0.85 * k})`);
    g.addColorStop(0.25, `hsla(${p.hue},100%,70%,${0.35 * k})`);
    g.addColorStop(1, 'hsla(0,0%,0%,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'screen';
    ctx.strokeStyle = `hsla(${p.hue},100%,85%,${0.5 * k})`;
    ctx.lineWidth = Math.max(1, w / 500);
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
  } else if (p.kind === 'starfield') {
    const n = Math.round(p.amount * 6), sz = Math.max(1, p.hue);
    ctx.fillStyle = '#ffffff';
    for (let i = 0; i < n; i++) {
      ctx.globalAlpha = 0.2 + rnd(i * 1.7) * 0.8;
      ctx.fillRect(rnd(i * 2.3) * w, rnd(i * 3.1) * h, sz, sz);
    }
  } else if (p.kind === 'bokeh') {
    ctx.globalCompositeOperation = 'screen';
    const n = Math.round(p.amount);
    for (let i = 0; i < n; i++) {
      const x = rnd(i * 2.7) * w, y = rnd(i * 4.1) * h, r = (4 + rnd(i * 5.3) * 18) * (w / 1080 + 0.4);
      const g = ctx.createRadialGradient(x, y, 0, x, y, r);
      g.addColorStop(0, `hsla(${p.hue + rnd(i) * 60},90%,80%,.5)`);
      g.addColorStop(1, 'hsla(0,0%,0%,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, r, 0, 6.283); ctx.fill();
    }
  } else if (p.kind === 'rain') {
    const n = Math.round(p.amount * 3), sp = p.speed;
    ctx.strokeStyle = 'rgba(220,235,255,.35)'; ctx.lineWidth = Math.max(1, w / 800);
    for (let i = 0; i < n; i++) {
      const x = rnd(i * 1.9) * w;
      const y = (rnd(i * 3.3) * h + time * sp * 220) % h;
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x - 6, y + 18); ctx.stroke();
    }
  }
  ctx.restore();
}

function applyGeometry(canvas: HTMLCanvasElement, tmp: HTMLCanvasElement,
  mirror: number, kaleidoscope: number): void {
  if (!mirror && !kaleidoscope) return;
  const w = canvas.width, h = canvas.height;
  tmp.width = w; tmp.height = h;
  const t = tmp.getContext('2d')!;
  t.clearRect(0, 0, w, h);
  t.save();
  if (kaleidoscope > 2) {
    const seg = Math.min(12, Math.round(kaleidoscope));
    for (let i = 0; i < seg; i++) {
      t.save(); t.translate(w / 2, h / 2); t.rotate(i * 2 * Math.PI / seg);
      if (i % 2) t.scale(1, -1);
      t.beginPath(); t.moveTo(0, 0);
      t.arc(0, 0, Math.hypot(w, h), -Math.PI / seg, Math.PI / seg);
      t.closePath(); t.clip();
      t.drawImage(canvas, -w / 2, -h / 2);
      t.restore();
    }
  } else {
    const mx = mirror === 1 || mirror === 3 ? -1 : 1;
    const my = mirror === 2 || mirror === 3 ? -1 : 1;
    t.translate(mx < 0 ? w : 0, my < 0 ? h : 0); t.scale(mx, my);
    t.drawImage(canvas, 0, 0);
  }
  t.restore();
  const c = canvas.getContext('2d')!;
  c.clearRect(0, 0, w, h);
  c.drawImage(tmp, 0, 0);
}

/* --------------------------- pixel processing ----------------------------- */

function applyPixelate(target: HTMLCanvasElement, tmp: HTMLCanvasElement, block: number): void {
  const w = target.width, h = target.height;
  const b = Math.max(2, Math.round(block * (w / 1080) || block));
  const sw = Math.max(1, Math.floor(w / b)), sh = Math.max(1, Math.floor(h / b));
  tmp.width = sw; tmp.height = sh;
  const tctx = tmp.getContext('2d')!;
  tctx.clearRect(0, 0, sw, sh);
  tctx.imageSmoothingEnabled = true;
  tctx.drawImage(target, 0, 0, sw, sh);
  const ctx = target.getContext('2d')!;
  ctx.save();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, w, h);
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(tmp, 0, 0, sw, sh, 0, 0, w, h);
  ctx.restore();
}

function applyChromatic(canvas: HTMLCanvasElement, amount: number, tmp: HTMLCanvasElement): void {
  const w = canvas.width, h = canvas.height;
  const ctx = canvas.getContext('2d')!;
  const img = ctx.getImageData(0, 0, w, h);
  const src = new Uint8ClampedArray(img.data);
  const shift = Math.max(1, Math.round(amount * (w / 1080)));
  const d = img.data;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      const xr = Math.min(w - 1, x + shift), xb = Math.max(0, x - shift);
      d[i] = src[(y * w + xr) * 4];
      d[i + 2] = src[(y * w + xb) * 4 + 2];
    }
  }
  ctx.putImageData(img, 0, 0);
  void tmp;
}

function applyChromaKey(canvas: HTMLCanvasElement, fx: NonNullable<FxState['chromaKey']>): void {
  const w = canvas.width, h = canvas.height;
  const ctx = canvas.getContext('2d')!;
  const img = ctx.getImageData(0, 0, w, h);
  const d = img.data;
  const keyHue = ((fx.hue % 360) + 360) % 360;
  const t = clamp(fx.threshold / 100, 0.01, 1);
  const s = Math.max(0.005, fx.softness / 100);
  const lo = Math.max(0, t - s / 2), hi = Math.min(1, t + s / 2);
  for (let i = 0; i < d.length; i += 4) {
    if (d[i + 3] === 0) continue;
    const r = d[i] / 255, g = d[i + 1] / 255, b = d[i + 2] / 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b), delta = max - min;
    if (delta < 0.02) continue;
    let hue = 0;
    if (max === r) hue = 60 * (((g - b) / delta) % 6);
    else if (max === g) hue = 60 * ((b - r) / delta + 2);
    else hue = 60 * ((r - g) / delta + 4);
    if (hue < 0) hue += 360;
    const raw = Math.abs(hue - keyHue);
    const diff = Math.min(raw, 360 - raw) / 180;
    const sat = max === 0 ? 0 : delta / max;
    if (sat < 0.1) continue;
    const key = diff <= lo ? 1 : diff >= hi ? 0 : (hi - diff) / (hi - lo || 1);
    const satFactor = clamp((sat - 0.08) / 0.2, 0, 1);
    d[i + 3] = Math.round(d[i + 3] * (1 - key * satFactor));
  }
  ctx.putImageData(img, 0, 0);
}

function applyNoise(canvas: HTMLCanvasElement, amount: number, seed: number): void {
  if (amount <= 0) return;
  const w = canvas.width, h = canvas.height;
  const ctx = canvas.getContext('2d')!;
  const img = ctx.getImageData(0, 0, w, h);
  const d = img.data;
  const strength = amount * 1.3;
  const s = Math.floor(seed * 1000) | 0;
  for (let i = 0; i < d.length; i += 4) {
    if (d[i + 3] === 0) continue;
    const r = (((i * 1103515245 + 12345 + s * 7919) >>> 8) & 255) / 255 - 0.5;
    const n = r * strength;
    d[i] = clamp(d[i] + n, 0, 255);
    d[i + 1] = clamp(d[i + 1] + n, 0, 255);
    d[i + 2] = clamp(d[i + 2] + n, 0, 255);
  }
  ctx.putImageData(img, 0, 0);
}

function applySharpen(canvas: HTMLCanvasElement, amount: number): void {
  if (amount <= 0) return;
  const w = canvas.width, h = canvas.height;
  const ctx = canvas.getContext('2d')!;
  const img = ctx.getImageData(0, 0, w, h);
  const src = new Uint8ClampedArray(img.data);
  const d = img.data;
  const k = amount / 100;
  for (let y = 1; y < h - 1; y++) {
    for (let x = 1; x < w - 1; x++) {
      const i = (y * w + x) * 4;
      if (d[i + 3] === 0) continue;
      for (let c = 0; c < 3; c++) {
        const avg = (src[i - 4 + c] + src[i + 4 + c] + src[i - w * 4 + c] + src[i + w * 4 + c]) / 4;
        d[i + c] = clamp(src[i + c] + (src[i + c] - avg) * k * 1.5, 0, 255);
      }
    }
  }
  ctx.putImageData(img, 0, 0);
}

function applyWave(canvas: HTMLCanvasElement, tmp: HTMLCanvasElement, fx: NonNullable<FxState['wave']>, time: number): void {
  const w = canvas.width, h = canvas.height;
  tmp.width = w; tmp.height = h;
  const tctx = tmp.getContext('2d')!;
  tctx.clearRect(0, 0, w, h);
  const amp = fx.amplitude * (w / 1080);
  const step = 3;
  for (let y = 0; y < h; y += step) {
    const off = Math.sin((y / h) * fx.frequency * Math.PI * 2 + time * fx.speed) * amp;
    tctx.drawImage(canvas, 0, y, w, Math.min(step, h - y), off, y, w, Math.min(step, h - y));
  }
  const ctx = canvas.getContext('2d')!;
  ctx.save(); ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, w, h); ctx.drawImage(tmp, 0, 0); ctx.restore();
}

function applyVignette(canvas: HTMLCanvasElement, scale: number, fx: NonNullable<FxState['vignette']>): void {
  const ctx = canvas.getContext('2d')!;
  ctx.save();
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  const w = canvas.width / scale, h = canvas.height / scale;
  const r = Math.max(w, h) * (0.35 + fx.size / 200);
  const g = ctx.createRadialGradient(w / 2, h / 2, r * 0.25, w / 2, h / 2, r);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, `rgba(0,0,0,${clamp(fx.amount / 100, 0, 1)})`);
  ctx.globalCompositeOperation = 'source-atop';
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, h);
  ctx.restore();
}

function applyTint(canvas: HTMLCanvasElement, scale: number, fx: NonNullable<FxState['tint']>): void {
  if (fx.amount <= 0) return;
  const ctx = canvas.getContext('2d')!;
  ctx.save();
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  ctx.globalCompositeOperation = 'source-atop';
  ctx.globalAlpha = clamp(fx.amount / 100, 0, 1);
  ctx.fillStyle = hsl(fx.hue, 90, 55, 1);
  ctx.fillRect(0, 0, canvas.width / scale, canvas.height / scale);
  ctx.restore();
}

function applyMask(canvas: HTMLCanvasElement, scale: number, layer: Layer, project: Project, w: number, h: number): void {
  if (layer.mask.type === 'none') return;
  const ctx = canvas.getContext('2d')!;
  ctx.save();
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  ctx.globalCompositeOperation = layer.mask.invert ? 'destination-out' : 'destination-in';
  if (layer.mask.feather > 0) ctx.filter = `blur(${layer.mask.feather}px)`;
  const W = project.width, H = project.height;
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  if (layer.mask.type === 'linear') {
    const g = ctx.createLinearGradient(0, 0, W, 0);
    g.addColorStop(0, 'rgba(255,255,255,0)');
    g.addColorStop(0.5, '#ffffff');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  } else if (layer.mask.type === 'ellipse') {
    ctx.ellipse(W / 2, H / 2, Math.min(W, w) * 0.45 || W * 0.4, Math.min(H, h) * 0.45 || H * 0.4, 0, 0, Math.PI * 2);
    ctx.fill();
  } else {
    const rw = Math.min(W, w || W) * 0.9, rh = Math.min(H, h || H) * 0.9;
    ctx.roundRect((W - rw) / 2, (H - rh) / 2, rw, rh, 12);
    ctx.fill();
  }
  ctx.restore();
}

/* -------------------------------- renderer -------------------------------- */

export class Renderer {
  canvas: HTMLCanvasElement;
  ctx: CanvasRenderingContext2D;
  private scratch: HTMLCanvasElement;
  private scratch2: HTMLCanvasElement;
  project: Project;
  media: MediaLibrary;
  scale = 0.4;

  constructor(canvas: HTMLCanvasElement, project: Project, media: MediaLibrary, scale = 0.4) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d', { alpha: false })!;
    this.scratch = document.createElement('canvas');
    this.scratch2 = document.createElement('canvas');
    this.project = project;
    this.media = media;
    this.scale = scale;
    this.resize();
  }

  setProject(p: Project): void { this.project = p; this.resize(); }
  setMedia(m: MediaLibrary): void { this.media = m; }
  setScale(s: number): void { this.scale = clamp(s, 0.05, 4); this.resize(); }

  resize(): void {
    const w = Math.max(2, Math.round(this.project.width * this.scale));
    const h = Math.max(2, Math.round(this.project.height * this.scale));
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w; this.canvas.height = h;
    }
    for (const c of [this.scratch, this.scratch2]) {
      if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
    }
  }

  private fontString(style: LayerStyle, fontSize: number): string {
    return `${style.italic ? 'italic ' : ''}${style.fontWeight} ${fontSize}px ${style.fontFamily}`;
  }

  private wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
    const out: string[] = [];
    for (const para of text.split('\n')) {
      if (para === '') { out.push(''); continue; }
      const words = para.split(' ');
      let line = '';
      for (const word of words) {
        const test = line ? `${line} ${word}` : word;
        if (line && ctx.measureText(test).width > maxWidth) { out.push(line); line = word; }
        else line = test;
      }
      out.push(line);
    }
    return out;
  }

  private contentSize(layer: Layer, t: number, lines?: string[], textW = 0): { w: number; h: number } {
    const W = this.project.width, H = this.project.height;
    const sw = Math.max(1, prop(layer, 'shapeWidth', t, W));
    const sh = Math.max(1, prop(layer, 'shapeHeight', t, H));
    switch (layer.type) {
      case 'solid':
      case 'shape': return { w: sw, h: sh };
      case 'text': {
        const fs = Math.max(1, prop(layer, 'fontSize', t, 96));
        const lh = prop(layer, 'lineHeight', t, 1.2);
        void lines;
        return {
          w: Math.max(1, textW || fs * 3) + fs * 0.5,
          h: Math.max(1, (lines?.length ?? 1)) * fs * lh + fs * 0.3,
        };
      }
      case 'image':
      case 'video': {
        const asset = layer.sourceId ? this.media.assets[layer.sourceId] : undefined;
        const iw = asset?.width || W, ih = asset?.height || H;
        const fit = layer.style.fit;
        if (fit === 'stretch') return { w: W, h: H };
        if (fit === 'none') return { w: iw, h: ih };
        const f = fit === 'contain' ? Math.min(W / iw, H / ih) : Math.max(W / iw, H / ih);
        return { w: iw * f, h: ih * f };
      }
      default: return { w: 0, h: 0 };
    }
  }

  private fillStyle(ctx: CanvasRenderingContext2D, layer: Layer, t: number, w: number, h: number): string | CanvasGradient {
    const c1 = normalizeColor(layer.style.color, '#ffffff');
    const c2 = normalizeColor(layer.style.color2, c1);
    if (layer.style.fillType === 'solid') return c1;
    const angle = prop(layer, 'gradientAngle', t, 45) * Math.PI / 180;
    const dx = Math.cos(angle), dy = Math.sin(angle);
    const len = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
    if (layer.style.fillType === 'radial') {
      const g = ctx.createRadialGradient(0, 0, 0, 0, 0, Math.max(w, h) / 2);
      g.addColorStop(0, c1); g.addColorStop(1, c2);
      return g;
    }
    const g = ctx.createLinearGradient(-dx * len, -dy * len, dx * len, dy * len);
    g.addColorStop(0, c1); g.addColorStop(1, c2);
    return g;
  }

  private shapePath(ctx: CanvasRenderingContext2D, layer: Layer, t: number, w: number, h: number): void {
    const r = prop(layer, 'cornerRadius', t, 0);
    const kind = layer.style.shape;
    const W = w / 2, H = h / 2;
    const poly = (n: number, rot = -Math.PI / 2, rx = W, ry = H) => {
      for (let i = 0; i < n; i++) {
        const a = rot + i * Math.PI * 2 / n;
        const x = Math.cos(a) * rx, y = Math.sin(a) * ry;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.closePath();
    };
    const starPoly = (points: number, inner: number) => {
      const count = points * 2;
      for (let i = 0; i < count; i++) {
        const a = -Math.PI / 2 + i * Math.PI / count;
        const rad = i % 2 === 1 ? inner : 1;
        const x = Math.cos(a) * W * rad, y = Math.sin(a) * H * rad;
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.closePath();
    };
    const R = Math.min(W, H);
    ctx.beginPath();
    switch (kind) {
      case 'rectangle':
      case 'rounded':
        ctx.roundRect(-W, -H, w, h, kind === 'rounded' ? Math.max(r, R * 0.28) : Math.min(r, R));
        break;
      case 'ellipse':
        ctx.ellipse(0, 0, W, H, 0, 0, Math.PI * 2);
        break;
      case 'triangle':
        ctx.moveTo(0, -H); ctx.lineTo(W, H); ctx.lineTo(-W, H); ctx.closePath();
        break;
      case 'diamond':
        ctx.moveTo(0, -H); ctx.lineTo(W, 0); ctx.lineTo(0, H); ctx.lineTo(-W, 0); ctx.closePath();
        break;
      case 'pentagon': poly(5); break;
      case 'hexagon': poly(6); break;
      case 'trapezoid':
        ctx.moveTo(-W * 0.62, -H); ctx.lineTo(W * 0.62, -H);
        ctx.lineTo(W, H); ctx.lineTo(-W, H); ctx.closePath();
        break;
      case 'star':
      case 'polygon': {
        const n = kind === 'star'
          ? Math.round(prop(layer, 'starPoints', t, 5))
          : Math.round(prop(layer, 'polygonSides', t, 6));
        const inner = prop(layer, 'innerRadius', t, 45) / 100;
        if (kind === 'star') starPoly(Math.max(3, Math.min(20, n)), inner);
        else poly(Math.max(3, Math.min(20, n)));
        break;
      }
      case 'sparkle': {
        const n = 4;
        for (let i = 0; i < n * 2; i++) {
          const a = -Math.PI / 2 + i * Math.PI / n;
          const rad = i % 2 === 1 ? 0.24 : 1;
          const x = Math.cos(a) * W * rad, y = Math.sin(a) * H * rad;
          if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.closePath();
        break;
      }
      case 'burst': starPoly(12, 0.68); break;
      case 'seal': starPoly(24, 0.9); break;
      case 'line':
        ctx.roundRect(-W, -Math.max(2, H * 0.035), w, Math.max(4, H * 0.07), Math.max(2, H * 0.035));
        break;
      case 'beam': {
        const th = Math.max(4, H * 0.16);
        const dx = W * 0.72, dy = H * 0.72;
        const nx = -dy / Math.hypot(dx, dy) * th / 2, ny = dx / Math.hypot(dx, dy) * th / 2;
        ctx.moveTo(-dx + nx, -dy + ny); ctx.lineTo(dx + nx, dy + ny);
        ctx.lineTo(dx - nx, dy - ny); ctx.lineTo(-dx - nx, -dy - ny); ctx.closePath();
        break;
      }
      case 'cross': {
        const a = W * 0.34, b = H * 0.34;
        ctx.moveTo(-a, -H); ctx.lineTo(a, -H); ctx.lineTo(a, -b);
        ctx.lineTo(W, -b); ctx.lineTo(W, b); ctx.lineTo(a, b);
        ctx.lineTo(a, H); ctx.lineTo(-a, H); ctx.lineTo(-a, b);
        ctx.lineTo(-W, b); ctx.lineTo(-W, -b); ctx.lineTo(-a, -b); ctx.closePath();
        break;
      }
      case 'ring':
        ctx.ellipse(0, 0, W, H, 0, 0, Math.PI * 2);
        ctx.ellipse(0, 0, W * 0.6, H * 0.6, 0, 0, Math.PI * 2);
        break;
      case 'arc':
        ctx.ellipse(0, 0, W, H, 0, Math.PI * 0.22, Math.PI * 1.78);
        ctx.ellipse(0, 0, W * 0.68, H * 0.68, 0, Math.PI * 1.78, Math.PI * 0.22, true);
        ctx.closePath();
        break;
      case 'crescent':
        ctx.ellipse(0, 0, W, H, 0, 0, Math.PI * 2);
        ctx.ellipse(W * 0.52, -H * 0.12, W * 0.88, H * 0.88, 0, 0, Math.PI * 2);
        break;
      case 'halfCircle':
        ctx.ellipse(0, 0, W, H, 0, Math.PI, Math.PI * 2); ctx.closePath();
        break;
      case 'pie':
        ctx.moveTo(0, 0); ctx.lineTo(W, 0);
        ctx.ellipse(0, 0, W, H, 0, 0, Math.PI * 1.5); ctx.closePath();
        break;
      case 'callout': {
        const cr = Math.min(R * 0.18, 40);
        ctx.roundRect(-W, -H, w, h * 0.84, cr);
        ctx.moveTo(W * 0.62, H * 0.58);
        ctx.ellipse(W * 0.62, H * 0.74, R * 0.16, R * 0.16, 0, 0, Math.PI * 2);
        break;
      }
      case 'flower': {
        const petals = 5;
        for (let i = 0; i < petals; i++) {
          const a = -Math.PI / 2 + i * Math.PI * 2 / petals;
          const da = Math.PI / petals;
          const ox = Math.cos(a) * W, oy = Math.sin(a) * H;
          const c1x = Math.cos(a - da) * W * 0.55, c1y = Math.sin(a - da) * H * 0.55;
          const c2x = Math.cos(a + da) * W * 0.55, c2y = Math.sin(a + da) * H * 0.55;
          if (i === 0) ctx.moveTo(c1x, c1y);
          ctx.quadraticCurveTo(ox * 1.3, oy * 1.3, c2x, c2y);
          ctx.quadraticCurveTo(Math.cos(a + da * 2) * W * 0.34, Math.sin(a + da * 2) * H * 0.34,
            Math.cos(a + da * 2 - da) * W * 0.55, Math.sin(a + da * 2 - da) * H * 0.55);
        }
        ctx.closePath();
        break;
      }
      case 'flag':
        ctx.moveTo(-W, -H); ctx.lineTo(W * 0.2, -H);
        ctx.quadraticCurveTo(W * 0.1, 0, W * 0.55, H);
        ctx.lineTo(-W, H); ctx.closePath();
        break;
      case 'heart': {
        const s = R;
        ctx.moveTo(0, s * 0.75);
        ctx.bezierCurveTo(-s * 1.4, -s * 0.1, -s * 0.6, -s * 1.1, 0, -s * 0.35);
        ctx.bezierCurveTo(s * 0.6, -s * 1.1, s * 1.4, -s * 0.1, 0, s * 0.75);
        ctx.closePath();
        break;
      }
      case 'arrow': {
        ctx.moveTo(-W, -H * 0.3);
        ctx.lineTo(W * 0.18, -H * 0.3);
        ctx.lineTo(W * 0.18, -H * 0.76);
        ctx.lineTo(W, 0);
        ctx.lineTo(W * 0.18, H * 0.76);
        ctx.lineTo(W * 0.18, H * 0.3);
        ctx.lineTo(-W, H * 0.3);
        ctx.closePath();
        break;
      }
      case 'customPath':
        break;
    }
  }

  private drawContent(ctx: CanvasRenderingContext2D, layer: Layer, t: number, w: number, h: number, lines: string[]): void {
    const style = layer.style;
    const stroke = prop(layer, 'strokeWidth', t, 0);
    switch (layer.type) {
      case 'solid': {
        ctx.fillStyle = this.fillStyle(ctx, layer, t, w, h);
        ctx.fillRect(-w / 2, -h / 2, w, h);
        break;
      }
      case 'shape': {
        const fill = this.fillStyle(ctx, layer, t, w, h);
        let path: Path2D | null = null;
        if (style.shape === 'customPath' && style.pathData) {
          try {
            const base = new Path2D(style.pathData);
            path = new Path2D();
            path.addPath(base, new DOMMatrix([w, 0, 0, h, -w / 2, -h / 2]));
          } catch { path = null; }
        }
        if (path) {
          ctx.fillStyle = fill; ctx.fill(path, 'evenodd');
        } else {
          this.shapePath(ctx, layer, t, w, h);
          ctx.fillStyle = fill; ctx.fill('evenodd');
        }
        if (stroke > 0) {
          ctx.lineWidth = stroke;
          ctx.lineJoin = 'round'; ctx.lineCap = 'round';
          ctx.strokeStyle = normalizeColor(style.strokeColor, '#000000');
          if (path) ctx.stroke(path); else ctx.stroke();
        }
        break;
      }
      case 'text': {
        const fs = Math.max(1, prop(layer, 'fontSize', t, 96));
        const lh = prop(layer, 'lineHeight', t, 1.2);
        const ls = prop(layer, 'letterSpacing', t, 0);
        ctx.font = this.fontString(style, fs);
        try { (ctx as unknown as { letterSpacing: string }).letterSpacing = `${ls}px`; } catch { /* older browsers */ }
        ctx.textAlign = style.align;
        ctx.textBaseline = 'middle';
        ctx.fillStyle = this.fillStyle(ctx, layer, t, w, h);
        ctx.strokeStyle = normalizeColor(style.strokeColor, '#000000');
        ctx.lineWidth = stroke;
        ctx.lineJoin = 'round';
        const x = style.align === 'left' ? -w / 2 + fs * 0.25 : style.align === 'right' ? w / 2 - fs * 0.25 : 0;
        const totalH = lines.length * fs * lh;
        lines.forEach((line, i) => {
          const y = -totalH / 2 + fs * lh * (i + 0.5);
          const text = style.uppercase ? line.toUpperCase() : line;
          if (stroke > 0) ctx.strokeText(text, x, y);
          ctx.fillText(text, x, y);
        });
        try { (ctx as unknown as { letterSpacing: string }).letterSpacing = '0px'; } catch { /* ignore */ }
        break;
      }
      case 'image':
      case 'video': {
        const asset = layer.sourceId ? this.media.assets[layer.sourceId] : undefined;
        if (!asset) { this.drawMissing(ctx, w, h); break; }
        let el: CanvasImageSource | null = null;
        if (asset.type === 'image') {
          const img = this.media.image(asset);
          if (img?.complete && img.naturalWidth > 0) el = img;
        } else if (asset.type === 'video') {
          const v = this.media.video(asset, layer.id);
          if (v && v.readyState >= 2 && v.videoWidth > 0) el = v;
        }
        if (el) {
          try { ctx.drawImage(el, -w / 2, -h / 2, w, h); }
          catch { this.drawMissing(ctx, w, h); }
        } else {
          this.drawMissing(ctx, w, h);
        }
        break;
      }
      default: break;
    }
  }

  private drawMissing(ctx: CanvasRenderingContext2D, w: number, h: number): void {
    ctx.save();
    ctx.fillStyle = '#24243a';
    ctx.fillRect(-w / 2, -h / 2, w, h);
    ctx.strokeStyle = '#52527a';
    ctx.lineWidth = 3;
    ctx.strokeRect(-w / 2 + 4, -h / 2 + 4, w - 8, h - 8);
    ctx.fillStyle = '#a5a5c5';
    ctx.font = '600 28px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('Media loading…', 0, 0);
    ctx.restore();
  }

  private localMatrix(layer: Layer, t: number, w: number, h: number, fx: FxState): DOMMatrix {
    const x = prop(layer, 'x', t, 0) + fx.shake.x;
    const y = prop(layer, 'y', t, 0) + fx.shake.y;
    const sx = prop(layer, 'scaleX', t, 100) / 100 * fx.pulse;
    const sy = prop(layer, 'scaleY', t, 100) / 100 * fx.pulse;
    const rot = prop(layer, 'rotation', t, 0);
    const skew = Math.tan(prop(layer, 'skewX', t, 0) * Math.PI / 180);
    const ax = -prop(layer, 'anchorX', t, 0) / 100 * w;
    const ay = -prop(layer, 'anchorY', t, 0) / 100 * h;
    let m = new DOMMatrix([1, 0, 0, 1, x, y]);
    if (rot) m = m.rotateSelf(rot);
    m = m.scaleSelf(sx, sy);
    if (skew) m = m.multiply(new DOMMatrix([1, 0, skew, 1, 0, 0]));
    m = m.translateSelf(ax, ay);
    return m;
  }

  private worldMatrix(layer: Layer, time: number, t: number, w: number, h: number, fx: FxState, depth = 0): DOMMatrix {
    const own = this.localMatrix(layer, t, w, h, fx);
    if (layer.parentId && depth < 12) {
      const parent = this.project.layers.find(l => l.id === layer.parentId);
      if (parent) {
        const pt = clamp(time - parent.start, 0, parent.duration);
        const pfx = buildFx(parent.effects, pt, time);
        const psize = this.contentSize(parent, pt);
        const pm = this.worldMatrix(parent, time, pt, psize.w, psize.h, pfx, depth + 1);
        return pm.multiply(own);
      }
    }
    return new DOMMatrix([1, 0, 0, 1, this.project.width / 2, this.project.height / 2]).multiply(own);
  }

  private chainOpacity(layer: Layer, time: number, depth = 0): number {
    let v = clamp(prop(layer, 'opacity', clamp(time - layer.start, 0, layer.duration), 100) / 100, 0, 1);
    if (layer.parentId && depth < 12) {
      const parent = this.project.layers.find(l => l.id === layer.parentId);
      if (parent) v *= this.chainOpacity(parent, time, depth + 1);
    }
    return v;
  }

  private isVisible(layer: Layer, depth = 0): boolean {
    if (!layer.visible) return false;
    if (layer.parentId && depth < 12) {
      const parent = this.project.layers.find(l => l.id === layer.parentId);
      if (parent) return this.isVisible(parent, depth + 1);
    }
    return true;
  }

  /** Render one frame. Set `exact` to await frame-accurate video seeks. */
  async render(time: number, exact = false): Promise<void> {
    this.resize();
    if (exact) await this.media.seekExact(this.project, time);
    const { ctx, canvas, scratch, scratch2, scale } = this;
    const W = this.project.width, H = this.project.height;
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.filter = 'none';
    ctx.fillStyle = normalizeColor(this.project.background, '#0b0c14');
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.restore();

    // Pixel-level effects read back this canvas every frame; request the fast path once.
    const sctx = scratch.getContext('2d', { willReadFrequently: true })!;
    /* A camera layer transforms the entire composition (pan / zoom / rotate). */
    let camera: { x: number; y: number; sx: number; sy: number; rot: number } | null = null;
    for (const l of this.project.layers) {
      if (l.type !== 'camera' || !this.isVisible(l)) continue;
      if (time < l.start - 0.0001 || time > l.start + l.duration) continue;
      const ct = clamp(time - l.start, 0, l.duration);
      camera = {
        x: prop(l, 'x', ct, 0) * scale, y: prop(l, 'y', ct, 0) * scale,
        sx: Math.max(0.05, prop(l, 'scaleX', ct, 100) / 100),
        sy: Math.max(0.05, prop(l, 'scaleY', ct, 100) / 100),
        rot: prop(l, 'rotation', ct, 0) * Math.PI / 180,
      };
      break;
    }
    const camTransform = (c: CanvasRenderingContext2D) => {
      if (!camera) return;
      c.translate(canvas.width / 2 + camera.x, canvas.height / 2 + camera.y);
      c.scale(camera.sx, camera.sy);
      c.rotate(camera.rot);
      c.translate(-canvas.width / 2, -canvas.height / 2);
    };
    // bottom-to-top (index 0 is topmost)
    for (let i = this.project.layers.length - 1; i >= 0; i--) {
      const layer = this.project.layers[i];
      if (!this.isVisible(layer)) continue;
      if (time < layer.start - 0.0001 || time > layer.start + layer.duration) continue;
      if (layer.type === 'group' || layer.type === 'audio' || layer.type === 'camera') continue;

      const t = clamp(time - layer.start, 0, layer.duration);
      const fx = buildFx(layer.effects, t, time);

      // measure text before computing the transform
      let lines: string[] = [];
      let textW = 0;
      if (layer.type === 'text') {
        const fs = Math.max(1, prop(layer, 'fontSize', t, 96));
        sctx.save();
        sctx.font = this.fontString(layer.style, fs);
        try { (sctx as unknown as { letterSpacing: string }).letterSpacing = `${prop(layer, 'letterSpacing', t, 0)}px`; } catch { /* ignore */ }
        lines = this.wrap(sctx, layer.style.uppercase ? layer.style.text.toUpperCase() : layer.style.text, W * 0.92);
        textW = Math.max(1, ...lines.map(l => sctx.measureText(l).width));
        sctx.restore();
      }
      const size = this.contentSize(layer, t, lines, textW);
      const matrix = this.worldMatrix(layer, time, t, size.w, size.h, fx);
      const alpha = this.chainOpacity(layer, time);
      if (alpha <= 0.001) continue;

      // draw the layer into an isolated scratch canvas
      sctx.save();
      sctx.setTransform(1, 0, 0, 1, 0, 0);
      sctx.clearRect(0, 0, scratch.width, scratch.height);
      sctx.restore();
      sctx.save();
      sctx.setTransform(scale, 0, 0, scale, 0, 0);
      sctx.transform(matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f);
      sctx.globalCompositeOperation = 'source-over';
      sctx.globalAlpha = 1;
      sctx.filter = fx.baseFilter || 'none';
      this.drawContent(sctx, layer, t, size.w, size.h, lines);
      sctx.restore();

      // pixel-level effects
      if (fx.pixelate > 1) applyPixelate(scratch, scratch2, fx.pixelate);
      if (fx.wave && fx.wave.amplitude > 0) applyWave(scratch, scratch2, fx.wave, time);
      if (fx.chromatic > 0) applyChromatic(scratch, fx.chromatic, scratch2);
      if (fx.chromaKey) applyChromaKey(scratch, fx.chromaKey);
      if (fx.noise > 0) applyNoise(scratch, fx.noise, time);
      if (fx.sharpen > 0) applySharpen(scratch, fx.sharpen);
      if (fx.vignette && fx.vignette.amount > 0) applyVignette(scratch, scale, fx.vignette);
      if (fx.tint && fx.tint.amount > 0) applyTint(scratch, scale, fx.tint);
      for (const op of fx.pixelOps) applyPixelOp(scratch, op);
      for (const pr of fx.procedurals) applyProcedural(scratch, pr, time);
      applyGeometry(scratch, scratch2, fx.mirror, fx.kaleidoscope);
      if (layer.mask.type !== 'none') applyMask(scratch, scale, layer, this.project, size.w, size.h);

      // composite onto the main canvas
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      camTransform(ctx);
      ctx.globalCompositeOperation = layer.blend || 'source-over';
      ctx.globalAlpha = alpha;
      ctx.filter = fx.dropShadows.length ? fx.dropShadows.join(' ') : 'none';
      if (fx.zoomBlur && fx.zoomBlur.amount > 0 && fx.zoomBlur.steps > 1) {
        const steps = clamp(Math.round(fx.zoomBlur.steps), 2, 12);
        const cx = canvas.width / 2, cy = canvas.height / 2;
        for (let s = steps; s >= 1; s--) {
          const k = 1 + (s / steps) * (fx.zoomBlur.amount / 100);
          ctx.setTransform(k, 0, 0, k, cx * (1 - k), cy * (1 - k));
          ctx.globalAlpha = alpha / (steps + 1);
          ctx.drawImage(scratch, 0, 0);
        }
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        camTransform(ctx);
        ctx.globalAlpha = alpha;
      }
      if (fx.motionBlur && fx.motionBlur.distance > 0) {
        const steps = 6;
        const rad = fx.motionBlur.angle * Math.PI / 180;
        const dx = Math.cos(rad) * fx.motionBlur.distance;
        const dy = Math.sin(rad) * fx.motionBlur.distance;
        for (let s = 1; s <= steps; s++) {
          ctx.globalAlpha = alpha / (steps + 1);
          ctx.drawImage(scratch, dx * s / steps, dy * s / steps);
        }
        ctx.globalAlpha = alpha;
      }
      ctx.drawImage(scratch, 0, 0);
      ctx.restore();
    }
    void W; void H;
  }

  /** Bounding box of one layer in composition coordinates (centre + size).
   *  Used by the on-canvas selection handles so they match the real render. */
  layerBounds(layer: Layer, time: number): { x: number; y: number; w: number; h: number; angle: number } | null {
    if (!this.isVisible(layer)) return null;
    const measure = this.ctx;
    const t = clamp(time - layer.start, 0, layer.duration);
    const fx = buildFx(layer.effects, t, time);
    let lines: string[] = [];
    let textW = 0;
    if (layer.type === 'text') {
      const fs = Math.max(1, prop(layer, 'fontSize', t, 96));
      measure.save();
      measure.font = this.fontString(layer.style, fs);
      lines = this.wrap(measure, layer.style.text, this.project.width * 0.92);
      textW = Math.max(1, ...lines.map(l => measure.measureText(l).width));
      measure.restore();
    }
    const size = this.contentSize(layer, t, lines, textW);
    const m = this.worldMatrix(layer, time, t, size.w, size.h, fx);
    return {
      x: m.e, y: m.f,
      w: Math.max(8, size.w * Math.hypot(m.a, m.b)),
      h: Math.max(8, size.h * Math.hypot(m.c, m.d)),
      angle: Math.atan2(m.b, m.a) * 180 / Math.PI,
    };
  }

  /** Topmost layer whose content box contains a point in composition coordinates. */
  hitTest(px: number, py: number, time: number): string | null {
    const measure = this.ctx;
    for (const layer of this.project.layers) {
      if (!this.isVisible(layer) || layer.type === 'group' || layer.type === 'audio' || layer.type === 'camera') continue;
      if (time < layer.start || time > layer.start + layer.duration) continue;
      const t = clamp(time - layer.start, 0, layer.duration);
      const fx = buildFx(layer.effects, t, time);
      let lines: string[] = [];
      let textW = 0;
      if (layer.type === 'text') {
        const fs = Math.max(1, prop(layer, 'fontSize', t, 96));
        measure.save();
        measure.font = this.fontString(layer.style, fs);
        try { (measure as unknown as { letterSpacing: string }).letterSpacing = `${prop(layer, 'letterSpacing', t, 0)}px`; } catch { /* ignore */ }
        lines = this.wrap(measure, layer.style.text, this.project.width * 0.92);
        textW = Math.max(1, ...lines.map(l => measure.measureText(l).width));
        measure.restore();
      }
      const size = this.contentSize(layer, t, lines, textW);
      const m = this.worldMatrix(layer, time, t, size.w, size.h, fx);
      const local = new DOMPoint(px, py).matrixTransform(m.inverse());
      const pad = Math.max(8, size.w * 0.02);
      if (Math.abs(local.x) <= size.w / 2 + pad && Math.abs(local.y) <= size.h / 2 + pad) return layer.id;
    }
    return null;
  }

  /** Render a frame into a fresh canvas at an arbitrary scale (used for thumbnails). */
  snapshot(scale = 0.2): HTMLCanvasElement {
    const c = document.createElement('canvas');
    const r = new Renderer(c, this.project, this.media, scale);
    void r.render(0, false);
    return c;
  }
}

/** Convert a CSS color into "rgba(r,g,b,a)" with an alpha multiplier. */
export function colorAlpha(color: string, a: number): string {
  const [r, g, b] = hexToRgb(normalizeColor(color));
  return `rgba(${r},${g},${b},${clamp(a, 0, 1)})`;
}

export { withAlpha };
