import type { Effect, Layer, LayerStyle, Project } from './types';
import { EFFECT_MAP } from './types';
import { prop, valueAt } from './animation';
import { clamp, hexToRgb, hsl, normalizeColor, withAlpha } from './utils';
import type { MediaLibrary } from './media';

interface FxState {
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
    }
  }
  s.baseFilter = filters.join(' ').trim();
  return s;
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
    ctx.beginPath();
    switch (kind) {
      case 'rectangle':
        ctx.roundRect(-w / 2, -h / 2, w, h, Math.min(r, Math.min(w, h) / 2));
        break;
      case 'ellipse':
        ctx.ellipse(0, 0, w / 2, h / 2, 0, 0, Math.PI * 2);
        break;
      case 'triangle':
        ctx.moveTo(0, -h / 2); ctx.lineTo(w / 2, h / 2); ctx.lineTo(-w / 2, h / 2); ctx.closePath();
        break;
      case 'star':
      case 'polygon': {
        const n = kind === 'star'
          ? Math.round(prop(layer, 'starPoints', t, 5))
          : Math.round(prop(layer, 'polygonSides', t, 6));
        const inner = prop(layer, 'innerRadius', t, 45) / 100;
        const count = kind === 'star' ? n * 2 : n;
        for (let i = 0; i < count; i++) {
          const a = -Math.PI / 2 + i * Math.PI * 2 / count;
          const rad = kind === 'star' && i % 2 === 1 ? inner : 1;
          const x = Math.cos(a) * w / 2 * rad, y = Math.sin(a) * h / 2 * rad;
          if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.closePath();
        break;
      }
      case 'line':
        ctx.moveTo(-w / 2, 0); ctx.lineTo(w / 2, 0);
        break;
      case 'heart': {
        const s = Math.min(w, h) / 2;
        ctx.moveTo(0, s * 0.75);
        ctx.bezierCurveTo(-s * 1.4, -s * 0.1, -s * 0.6, -s * 1.1, 0, -s * 0.35);
        ctx.bezierCurveTo(s * 0.6, -s * 1.1, s * 1.4, -s * 0.1, 0, s * 0.75);
        ctx.closePath();
        break;
      }
      case 'arrow': {
        ctx.moveTo(-w / 2, -h * 0.15);
        ctx.lineTo(w * 0.18, -h * 0.15);
        ctx.lineTo(w * 0.18, -h * 0.38);
        ctx.lineTo(w / 2, 0);
        ctx.lineTo(w * 0.18, h * 0.38);
        ctx.lineTo(w * 0.18, h * 0.15);
        ctx.lineTo(-w / 2, h * 0.15);
        ctx.closePath();
        break;
      }
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
        this.shapePath(ctx, layer, t, w, h);
        const fill = this.fillStyle(ctx, layer, t, w, h);
        if (style.shape !== 'line') { ctx.fillStyle = fill; ctx.fill(); }
        if (stroke > 0) {
          ctx.lineWidth = stroke;
          ctx.lineJoin = 'round'; ctx.lineCap = 'round';
          ctx.strokeStyle = normalizeColor(style.strokeColor, '#000000');
          ctx.stroke();
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

    const sctx = scratch.getContext('2d')!;
    // bottom-to-top (index 0 is topmost)
    for (let i = this.project.layers.length - 1; i >= 0; i--) {
      const layer = this.project.layers[i];
      if (!this.isVisible(layer)) continue;
      if (time < layer.start - 0.0001 || time > layer.start + layer.duration) continue;
      if (layer.type === 'group' || layer.type === 'audio') continue;

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
      if (layer.mask.type !== 'none') applyMask(scratch, scale, layer, this.project, size.w, size.h);

      // composite onto the main canvas
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
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
        ctx.globalAlpha = alpha;
      }
      ctx.drawImage(scratch, 0, 0);
      ctx.restore();
    }
    void W; void H;
  }

  /** Topmost layer whose content box contains a point in composition coordinates. */
  hitTest(px: number, py: number, time: number): string | null {
    const measure = this.ctx;
    for (const layer of this.project.layers) {
      if (!this.isVisible(layer) || layer.type === 'group' || layer.type === 'audio') continue;
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
