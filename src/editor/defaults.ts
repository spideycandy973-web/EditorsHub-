import type {
  Anim, BlendMode, Easing, Effect, EffectType, Layer, LayerStyle, LayerType, Project, ShapeKind,
} from './types';
import { EFFECT_MAP, PROP_DEFS } from './types';
import { anim, makeKeyframe, setKeyframe } from './animation';
import { uid } from './utils';

const defaultProps = (): Record<string, Anim> =>
  Object.fromEntries(PROP_DEFS.map(p => [p.key, anim(p.default)]));

export const defaultStyle = (): LayerStyle => ({
  text: 'Your text',
  fontFamily: 'Inter, system-ui, sans-serif',
  fontWeight: 800,
  italic: false,
  align: 'center',
  uppercase: false,
  fillType: 'solid',
  color: '#ffffff',
  color2: '#8b5cf6',
  strokeColor: '#000000',
  shape: 'rectangle',
  fit: 'cover',
  audio: false,
  loop: true,
});

export function createEffect(type: EffectType): Effect {
  const def = EFFECT_MAP[type];
  return {
    id: uid('fx'),
    type,
    enabled: true,
    params: def
      ? Object.fromEntries(def.params.map(p => [p.key, anim(p.default)]))
      : {},
  };
}

export interface CreateLayerOptions {
  name?: string;
  start?: number;
  duration?: number;
  sourceId?: string;
  shape?: ShapeKind;
  text?: string;
  color?: string;
  width?: number;
  height?: number;
  fit?: LayerStyle['fit'];
}

export function createLayer(type: LayerType, opts: CreateLayerOptions = {}): Layer {
  const props = defaultProps();
  const style = defaultStyle();
  const W = opts.width ?? 1080;
  const H = opts.height ?? 1920;
  const minSide = Math.min(W, H);
  props.shapeWidth.value = Math.round(minSide * 0.6);
  props.shapeHeight.value = Math.round(minSide * 0.6);
  props.gradientAngle.value = 45;

  const layer: Layer = {
    id: uid('layer'),
    name: opts.name ?? defaultName(type),
    type,
    start: opts.start ?? 0,
    duration: opts.duration ?? 5,
    inPoint: 0,
    visible: true,
    locked: false,
    blend: 'source-over',
    parentId: null,
    sourceId: opts.sourceId,
    props,
    style,
    effects: [],
    mask: { type: 'none', feather: 0, invert: false },
  };

  switch (type) {
    case 'solid':
      layer.name = opts.name ?? 'Background';
      layer.style.fillType = 'linear';
      layer.style.color = opts.color ?? '#191a35';
      layer.style.color2 = '#5b2a86';
      props.shapeWidth.value = W;
      props.shapeHeight.value = H;
      break;
    case 'shape':
      layer.style.shape = opts.shape ?? 'rectangle';
      layer.style.color = opts.color ?? '#8b5cf6';
      layer.style.color2 = '#22d3ee';
      layer.style.fillType = 'linear';
      props.cornerRadius.value = layer.style.shape === 'rectangle' ? 40 : 0;
      break;
    case 'text':
      layer.style.text = opts.text ?? 'Your text';
      layer.style.fontFamily = 'Inter, system-ui, sans-serif';
      layer.style.fontWeight = 800;
      layer.style.color = '#ffffff';
      props.fontSize.value = 100;
      break;
    case 'image':
    case 'video':
    case 'audio':
      layer.style.fit = opts.fit ?? 'cover';
      layer.duration = opts.duration ?? 5;
      break;
    case 'group':
      layer.name = opts.name ?? 'Group';
      break;
  }
  return layer;
}

export function defaultName(type: LayerType): string {
  return ({
    solid: 'Solid', shape: 'Shape', text: 'Text', image: 'Image',
    video: 'Video', audio: 'Audio', group: 'Group',
  } as Record<LayerType, string>)[type];
}

export function createProject(name = 'Untitled Project', width = 1080, height = 1920): Project {
  const now = Date.now();
  return {
    id: uid('proj'),
    name,
    width,
    height,
    fps: 30,
    duration: 10,
    background: '#e9e9ec',
    layers: [],
    createdAt: now,
    updatedAt: now,
  };
}

/* ------------------------- motion / animation presets --------------------- */

export interface MotionPreset {
  id: string;
  label: string;
  description: string;
  apply: (layer: Layer, duration: number) => void;
}

const K = (p: Record<string, Anim>, key: string, pairs: [number, number][], easing: Easing = 'easeInOut') => {
  let a = p[key] ?? anim(0);
  for (const [t, v] of pairs) a = setKeyframe(a, t, v, easing);
  p[key] = a;
};

export const MOTION_PRESETS: MotionPreset[] = [
  { id: 'fadeIn', label: 'Fade In', description: 'Opacity 0 → 100 over 0.8s', apply: (l, d) => K(l.props, 'opacity', [[0, 0], [Math.min(0.8, d), 100]]) },
  { id: 'fadeOut', label: 'Fade Out', description: 'Opacity 100 → 0 at the end', apply: (l, d) => K(l.props, 'opacity', [[Math.max(0, d - 0.8), 100], [d, 0]]) },
  { id: 'fadeInOut', label: 'Fade In + Out', description: 'Fade at both ends', apply: (l, d) => K(l.props, 'opacity', [[0, 0], [0.6, 100], [d - 0.6, 100], [d, 0]]) },
  { id: 'slideUp', label: 'Slide Up', description: 'Rises into place with a spring', apply: (l, d) => K(l.props, 'y', [[0, 400], [Math.min(0.9, d), 0]], 'spring') },
  { id: 'slideLeft', label: 'Slide In', description: 'Enters from the right', apply: (l, d) => K(l.props, 'x', [[0, 700], [Math.min(0.9, d), 0]], 'easeOut') },
  { id: 'zoomIn', label: 'Zoom In', description: 'Scales up from 0%', apply: (l, d) => { K(l.props, 'scaleX', [[0, 0], [Math.min(1, d), 100]], 'easeOutBack'); K(l.props, 'scaleY', [[0, 0], [Math.min(1, d), 100]], 'easeOutBack'); } },
  { id: 'pop', label: 'Pop', description: 'Overshooting scale bounce', apply: (l, d) => { K(l.props, 'scaleX', [[0, 40], [0.25, 115], [0.5, 100]], 'easeOutBack'); K(l.props, 'scaleY', [[0, 40], [0.25, 115], [0.5, 100]], 'easeOutBack'); K(l.props, 'opacity', [[0, 0], [0.18, 100]]); } },
  { id: 'spin', label: 'Spin', description: 'Full rotation over the layer', apply: (l, d) => K(l.props, 'rotation', [[0, 0], [d, 360]], 'linear') },
  { id: 'swing', label: 'Swing', description: 'Rocking rotation', apply: (l, d) => K(l.props, 'rotation', [[0, -12], [d / 2, 12], [d, -12]], 'easeInOut') },
  { id: 'pulse', label: 'Pulse', description: 'Repeating scale pulse', apply: (l, d) => {
      const pairs: [number, number][] = [];
      for (let t = 0; t <= d; t += 0.5) pairs.push([Number(t.toFixed(2)), t % 1 === 0 ? 100 : 112]);
      K(l.props, 'scaleX', pairs); K(l.props, 'scaleY', pairs);
    } },
  { id: 'float', label: 'Float', description: 'Gentle vertical drift', apply: (l, d) => K(l.props, 'y', [[0, 0], [d / 2, -40], [d, 0]], 'easeInOut') },
  { id: 'typewriter', label: 'Typewriter', description: 'Animated width reveal', apply: (l, d) => K(l.props, 'shapeWidth', [[0, 0], [Math.min(1.5, d), l.props.shapeWidth.value]], 'linear') },
];

export function applyPreset(layer: Layer, presetId: string, duration: number): void {
  const p = MOTION_PRESETS.find(x => x.id === presetId);
  if (p) p.apply(layer, Math.max(0.2, duration));
}

/* --------------------------- built-in demo project ------------------------- */

export function createDemoProject(): Project {
  const p = createProject('Welcome to EditorsHub', 1080, 1920);
  p.duration = 8;
  p.background = '#080810';

  const bg = createLayer('solid', { width: p.width, height: p.height, duration: 8 });
  bg.name = 'Gradient Background';
  bg.style.color = '#12132f';
  bg.style.color2 = '#581c87';
  bg.props.gradientAngle.value = 135;
  bg.locked = true;

  const orb = createLayer('shape', {
    width: p.width, height: p.height, duration: 8, shape: 'ellipse', name: 'Glow Orb',
  });
  orb.style.color = '#7c3aed';
  orb.style.color2 = '#ec4899';
  orb.style.fillType = 'radial';
  orb.props.shapeWidth.value = 900;
  orb.props.shapeHeight.value = 900;
  orb.props.y.value = -260;
  orb.props.opacity.value = 70;
  orb.blend = 'screen';
  orb.effects = [createEffect('blur')];
  orb.effects[0].params.amount.value = 90;
  K(orb.props, 'scaleX', [[0, 80], [4, 115], [8, 80]], 'easeInOut');
  K(orb.props, 'scaleY', [[0, 80], [4, 115], [8, 80]], 'easeInOut');

  const star = createLayer('shape', {
    width: p.width, height: p.height, duration: 8, shape: 'star', name: 'Spinning Star',
  });
  star.style.color = '#22d3ee';
  star.style.color2 = '#a78bfa';
  star.props.shapeWidth.value = 620;
  star.props.shapeHeight.value = 620;
  star.props.y.value = -180;
  star.props.innerRadius.value = 50;
  star.props.rotation.value = 12;
  star.effects = [createEffect('glow')];
  star.effects[0].params.radius.value = 45;
  star.effects[0].params.hue.value = 270;
  K(star.props, 'rotation', [[0, 0], [8, 360]], 'linear');

  const title = createLayer('text', {
    width: p.width, height: p.height, duration: 8, text: 'EDITORS\nHUB MOTION', name: 'Title',
  });
  title.style.fontWeight = 900;
  title.style.fillType = 'linear';
  title.style.color = '#ffffff';
  title.style.color2 = '#c084fc';
  title.props.fontSize.value = 132;
  title.props.lineHeight.value = 1.1;
  title.props.y.value = 250;
  title.props.letterSpacing.value = 4;
  title.effects = [createEffect('dropShadow')];
  title.effects[0].params.blur.value = 30;
  title.effects[0].params.opacity.value = 70;
  K(title.props, 'opacity', [[0.2, 0], [1.1, 100], [7, 100], [8, 0]]);
  K(title.props, 'y', [[0.2, 350], [1.1, 250]], 'easeOutBack');
  K(title.props, 'scaleX', [[0.2, 70], [1.1, 100]], 'easeOutBack');
  K(title.props, 'scaleY', [[0.2, 70], [1.1, 100]], 'easeOutBack');

  const sub = createLayer('text', {
    width: p.width, height: p.height, duration: 8, text: 'Layers · Keyframes · Effects · XML', name: 'Subtitle',
  });
  sub.props.fontSize.value = 42;
  sub.props.y.value = 520;
  sub.style.color = '#cbd5f5';
  sub.style.fontWeight = 500;
  sub.props.letterSpacing.value = 2;
  K(sub.props, 'opacity', [[0.8, 0], [1.8, 100], [7, 100], [8, 0]]);

  const bar = createLayer('shape', {
    width: p.width, height: p.height, duration: 8, shape: 'rectangle', name: 'Progress Bar',
  });
  bar.props.shapeWidth.value = 620;
  bar.props.shapeHeight.value = 12;
  bar.props.y.value = 650;
  bar.props.cornerRadius.value = 6;
  bar.style.fillType = 'linear';
  bar.style.color = '#22d3ee';
  bar.style.color2 = '#f472b6';
  K(bar.props, 'scaleX', [[0, 0], [7.5, 100]], 'linear');

  const badge = createLayer('shape', {
    width: p.width, height: p.height, duration: 8, shape: 'rectangle', name: 'Pill',
  });
  badge.props.shapeWidth.value = 420;
  badge.props.shapeHeight.value = 96;
  badge.props.cornerRadius.value = 48;
  badge.props.y.value = -650;
  badge.style.color = '#ffffff';
  badge.style.fillType = 'solid';
  badge.props.opacity.value = 16;

  const badgeText = createLayer('text', {
    width: p.width, height: p.height, duration: 8, text: '100% IN YOUR BROWSER', name: 'Badge Text',
  });
  badgeText.props.fontSize.value = 34;
  badgeText.props.y.value = -650;
  badgeText.props.letterSpacing.value = 3;
  badgeText.style.fontWeight = 700;
  badgeText.style.color = '#ffffff';

  p.layers = [badgeText, badge, bar, sub, title, star, orb, bg];
  return p;
}

/** A few ready-made color palettes for the color panel. */
export const PALETTES: { name: string; colors: string[] }[] = [
  { name: 'Neon', colors: ['#22d3ee', '#8b5cf6', '#f472b6', '#facc15', '#4ade80'] },
  { name: 'Sunset', colors: ['#fb7185', '#f97316', '#fbbf24', '#7c3aed', '#1e293b'] },
  { name: 'Pastel', colors: ['#fecdd3', '#fed7aa', '#d9f99d', '#a5f3fc', '#ddd6fe'] },
  { name: 'Mono', colors: ['#ffffff', '#d4d4d8', '#71717a', '#27272a', '#000000'] },
];

export const BLEND_MODES: BlendMode[] = [
  'source-over', 'multiply', 'screen', 'overlay', 'darken', 'lighten',
  'color-dodge', 'color-burn', 'hard-light', 'soft-light', 'difference',
  'exclusion', 'hue', 'saturation', 'color', 'luminosity', 'lighter',
];
