import type { Anim, Easing, Keyframe, Layer } from './types';
import { EASINGS, PROP_MAP } from './types';
import { clamp, lerp, round, uid } from './utils';

/* ------------------------------- easing ---------------------------------- */

export function ease(t: number, type: Easing): number {
  t = clamp(t, 0, 1);
  if (type.startsWith('cubic(')) {
    const nums = type.slice(6, -1).split(',').map(Number);
    if (nums.length === 4 && nums.every(Number.isFinite)) {
      const [x1, y1, x2, y2] = nums.map(n => clamp(n, 0, 4));
      const bez = (a: number, b: number, u: number) =>
        3 * a * u * (1 - u) * (1 - u) + 3 * b * u * u * (1 - u) + u * u * u;
      let lo = 0, hi = 1, u = t;
      for (let i = 0; i < 24; i++) {
        u = (lo + hi) / 2;
        if (bez(x1, x2, u) < t) lo = u; else hi = u;
      }
      return bez(y1, y2, u);
    }
  }
  switch (type) {
    case 'linear': return t;
    case 'hold': return 0;
    case 'easeIn': return t * t * t;
    case 'easeOut': return 1 - Math.pow(1 - t, 3);
    case 'easeInOut': return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    case 'easeInBack': {
      const c1 = 1.70158, c3 = c1 + 1;
      return c3 * t * t * t - c1 * t * t;
    }
    case 'easeOutBack': {
      const c1 = 1.70158, c3 = c1 + 1;
      return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }
    case 'elastic': {
      if (t === 0 || t === 1) return t;
      const c4 = (2 * Math.PI) / 3;
      return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
    }
    case 'bounce': {
      const n1 = 7.5625, d1 = 2.75;
      if (t < 1 / d1) return n1 * t * t;
      if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
      if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
      return n1 * (t -= 2.625 / d1) * t + 0.984375;
    }
    case 'spring': {
      if (t === 0 || t === 1) return t;
      return 1 - Math.cos(t * 5 * Math.PI) * Math.exp(-5 * t);
    }
    default: return t;
  }
}

export const anim = (value: number, keyframes: Keyframe[] = []): Anim => ({ value, keyframes });

export function sortedKeyframes(a: Anim): Keyframe[] {
  return [...a.keyframes].sort((x, y) => x.time - y.time);
}

/** Evaluate an animatable value at a layer-local time (seconds). */
export function valueAt(a: Anim | undefined, time: number, fallback = 0): number {
  if (!a) return fallback;
  const kfs = a.keyframes;
  if (!kfs || kfs.length === 0) return a.value;
  const sorted = sortedKeyframes(a);
  if (time <= sorted[0].time) return sorted[0].value;
  if (time >= sorted[sorted.length - 1].time) return sorted[sorted.length - 1].value;
  let i = 0;
  while (i < sorted.length - 1 && sorted[i + 1].time <= time) i++;
  const k0 = sorted[i], k1 = sorted[i + 1];
  const span = k1.time - k0.time;
  if (span <= 0) return k1.value;
  if (k0.easing === 'hold') return k0.value;
  return lerp(k0.value, k1.value, ease((time - k0.time) / span, k0.easing));
}

export function prop(layer: Layer, key: string, localTime: number, fallback?: number): number {
  const def = PROP_MAP[key];
  return valueAt(layer.props[key], localTime, fallback ?? def?.default ?? 0);
}

export function effectParam(effect: { params: Record<string, Anim> }, key: string, localTime: number, fallback = 0): number {
  return valueAt(effect.params[key], localTime, fallback);
}

/* -------------------------- keyframe operations --------------------------- */

export function makeKeyframe(time: number, value: number, easing: Easing = 'easeInOut'): Keyframe {
  return { id: uid('kf'), time: round(Math.max(0, time), 4), value: round(value, 4), easing };
}

export function setKeyframe(a: Anim, time: number, value: number, easing: Easing = 'easeInOut'): Anim {
  const t = round(Math.max(0, time), 4);
  const existing = a.keyframes.find(k => Math.abs(k.time - t) < 0.0005);
  if (existing) {
    return { value: a.value, keyframes: a.keyframes.map(k => k.id === existing.id ? { ...k, value: round(value, 4), easing } : k) };
  }
  const kf = makeKeyframe(t, value, easing);
  return { value: a.value, keyframes: [...a.keyframes, kf].sort((x, y) => x.time - y.time) };
}

export function upsertKeyframe(a: Anim, time: number, value: number): Anim {
  return setKeyframe(a, time, value, a.keyframes.find(k => k.time < time)?.easing ?? 'easeInOut');
}

export function removeKeyframe(a: Anim, id: string): Anim {
  return { value: a.value, keyframes: a.keyframes.filter(k => k.id !== id) };
}

export function clearKeyframes(a: Anim, time: number): Anim {
  return { value: valueAt(a, time), keyframes: [] };
}

export function isValidEasing(v: string): Easing {
  return (EASINGS as string[]).includes(v) || /^cubic\(-?[\d.]+,-?[\d.]+,-?[\d.]+,-?[\d.]+\)$/.test(v)
    ? v as Easing : 'linear';
}

/** Snap a time to the project frame grid. */
export const snapTime = (t: number, fps: number): number => Math.round(t * fps) / fps;
