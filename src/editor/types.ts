/* ------------------------------------------------------------------------- *
 * EditorsHub — project data model
 * All times are seconds. Keyframe times are LAYER-LOCAL (0 = layer start).
 * All positions are in composition pixels, relative to the composition centre.
 * ------------------------------------------------------------------------- */

export type EasingName =
  | 'linear' | 'hold' | 'easeIn' | 'easeOut' | 'easeInOut'
  | 'easeInBack' | 'easeOutBack' | 'elastic' | 'bounce' | 'spring';

/** Named easing or a real cubic-bezier curve: cubic(p1x,p1y,p2x,p2y). */
export type Easing = EasingName | `cubic(${number},${number},${number},${number})`;

export const EASINGS: EasingName[] = [
  'linear', 'hold', 'easeIn', 'easeOut', 'easeInOut',
  'easeInBack', 'easeOutBack', 'elastic', 'bounce', 'spring',
];

export interface Keyframe {
  id: string;
  time: number;      // layer-local seconds
  value: number;
  easing: Easing;    // easing used on the segment leaving this keyframe
}

export interface Anim {
  value: number;        // static value (used when there are no keyframes)
  keyframes: Keyframe[];
}

export type LayerType = 'solid' | 'shape' | 'text' | 'image' | 'video' | 'audio' | 'group' | 'camera';

export type BlendMode =
  | 'source-over' | 'multiply' | 'screen' | 'overlay' | 'darken' | 'lighten'
  | 'color-dodge' | 'color-burn' | 'hard-light' | 'soft-light' | 'difference'
  | 'exclusion' | 'hue' | 'saturation' | 'color' | 'luminosity' | 'lighter';

export const BLEND_LABELS: Record<BlendMode, string> = {
  'source-over': 'Normal', multiply: 'Multiply', screen: 'Screen', overlay: 'Overlay',
  darken: 'Darken', lighten: 'Lighten', 'color-dodge': 'Color Dodge',
  'color-burn': 'Color Burn', 'hard-light': 'Hard Light', 'soft-light': 'Soft Light',
  difference: 'Difference', exclusion: 'Exclusion', hue: 'Hue', saturation: 'Saturation',
  color: 'Color', luminosity: 'Luminosity', lighter: 'Add',
};

export type ShapeKind =
  | 'rectangle' | 'rounded' | 'ellipse' | 'triangle' | 'diamond' | 'pentagon' | 'hexagon'
  | 'star' | 'sparkle' | 'burst' | 'heart' | 'arrow' | 'line' | 'cross' | 'ring' | 'arc'
  | 'crescent' | 'halfCircle' | 'pie' | 'callout' | 'flower' | 'trapezoid' | 'beam'
  | 'flag' | 'seal' | 'polygon' | 'customPath';

/** The 18 shapes shown in the shape picker, in picker order. */
export const SHAPE_PICKER: { kind: ShapeKind; label: string }[] = [
  { kind: 'ellipse', label: 'Circle' },
  { kind: 'rounded', label: 'Rounded rectangle' },
  { kind: 'cross', label: 'Plus' },
  { kind: 'arc', label: 'Arc' },
  { kind: 'triangle', label: 'Triangle' },
  { kind: 'callout', label: 'Callout' },
  { kind: 'pie', label: 'Pie' },
  { kind: 'hexagon', label: 'Hexagon' },
  { kind: 'flower', label: 'Flower' },
  { kind: 'arrow', label: 'Arrow' },
  { kind: 'trapezoid', label: 'Trapezoid' },
  { kind: 'crescent', label: 'Crescent' },
  { kind: 'rectangle', label: 'Rectangle' },
  { kind: 'star', label: 'Star' },
  { kind: 'line', label: 'Line' },
  { kind: 'beam', label: 'Beam' },
  { kind: 'flag', label: 'Curve' },
  { kind: 'seal', label: 'Burst seal' },
];

export type FillType = 'solid' | 'linear' | 'radial';

export type EffectType =
  | 'blur' | 'sharpen' | 'colorAdjust' | 'glow' | 'dropShadow' | 'pixelate'
  | 'vignette' | 'grayscale' | 'invert' | 'sepia' | 'noise' | 'outline'
  | 'tint' | 'shake' | 'chromatic' | 'chromaKey' | 'wave' | 'pulse' | 'zoomBlur';

export interface EffectParamDef {
  key: string;
  label: string;
  min: number;
  max: number;
  step: number;
  default: number;
  unit?: string;
}

export interface EffectDef {
  type: EffectType;
  label: string;
  group: string;
  description: string;
  params: EffectParamDef[];
}

export interface Effect {
  id: string;
  type: EffectType;
  enabled: boolean;
  params: Record<string, Anim>;
}

export interface LayerStyle {
  /* strings / enums only — numbers live in `props` so they can be keyframed */
  text: string;
  fontFamily: string;
  fontWeight: number;
  italic: boolean;
  align: 'left' | 'center' | 'right';
  uppercase: boolean;
  fillType: FillType;
  color: string;
  color2: string;
  strokeColor: string;
  shape: ShapeKind;
  fit: 'cover' | 'contain' | 'stretch' | 'none';
  audio: boolean;   // include sound from a video source
  loop: boolean;    // loop media shorter than the layer
  /** SVG path data in a normalized 0..1 box, used by the vector pen tool. */
  pathData: string;
}

export interface Mask {
  type: 'none' | 'rect' | 'ellipse' | 'linear';
  feather: number;
  invert: boolean;
}

export interface Layer {
  id: string;
  name: string;
  type: LayerType;
  start: number;
  duration: number;
  inPoint: number;      // offset into the source media
  visible: boolean;
  locked: boolean;
  blend: BlendMode;
  parentId: string | null;
  sourceId?: string;    // asset id for image / video / audio
  props: Record<string, Anim>;
  style: LayerStyle;
  effects: Effect[];
  mask: Mask;
}

export interface Project {
  id: string;
  name: string;
  width: number;
  height: number;
  fps: number;
  duration: number;
  background: string;
  layers: Layer[];      // index 0 = topmost
  createdAt: number;
  updatedAt: number;
}

export type AssetType = 'image' | 'video' | 'audio' | 'font';

export interface Asset {
  id: string;
  name: string;
  type: AssetType;
  mime: string;
  width: number;
  height: number;
  duration: number;
  url: string;               // blob: or data: URL
  blob?: Blob;
  element?: HTMLImageElement | HTMLVideoElement | HTMLAudioElement;
  thumbnail?: string;
  fontFamily?: string;
  builtin?: boolean;
}

export type PanelId = 'add' | 'edit' | 'effects' | 'keyframes' | 'project';

export interface PropDef {
  key: string;
  label: string;
  min: number;
  max: number;
  step: number;
  default: number;
  unit?: string;
  group: 'transform' | 'appearance' | 'media';
}

/** Numeric properties; everything here is animatable with keyframes. */
export const PROP_DEFS: PropDef[] = [
  { key: 'x', label: 'Position X', min: -4000, max: 4000, step: 1, default: 0, unit: 'px', group: 'transform' },
  { key: 'y', label: 'Position Y', min: -4000, max: 4000, step: 1, default: 0, unit: 'px', group: 'transform' },
  { key: 'scaleX', label: 'Scale X', min: 0, max: 500, step: 1, default: 100, unit: '%', group: 'transform' },
  { key: 'scaleY', label: 'Scale Y', min: 0, max: 500, step: 1, default: 100, unit: '%', group: 'transform' },
  { key: 'rotation', label: 'Rotation', min: -1440, max: 1440, step: 1, default: 0, unit: '°', group: 'transform' },
  { key: 'opacity', label: 'Opacity', min: 0, max: 100, step: 1, default: 100, unit: '%', group: 'transform' },
  { key: 'anchorX', label: 'Anchor X', min: -200, max: 200, step: 1, default: 0, unit: '%', group: 'transform' },
  { key: 'anchorY', label: 'Anchor Y', min: -200, max: 200, step: 1, default: 0, unit: '%', group: 'transform' },
  { key: 'skewX', label: 'Skew X', min: -80, max: 80, step: 1, default: 0, unit: '°', group: 'transform' },
  { key: 'shapeWidth', label: 'Width', min: 4, max: 4000, step: 1, default: 500, unit: 'px', group: 'appearance' },
  { key: 'shapeHeight', label: 'Height', min: 4, max: 4000, step: 1, default: 500, unit: 'px', group: 'appearance' },
  { key: 'cornerRadius', label: 'Corner radius', min: 0, max: 500, step: 1, default: 0, unit: 'px', group: 'appearance' },
  { key: 'strokeWidth', label: 'Stroke width', min: 0, max: 200, step: 1, default: 0, unit: 'px', group: 'appearance' },
  { key: 'fontSize', label: 'Font size', min: 8, max: 600, step: 1, default: 96, unit: 'px', group: 'appearance' },
  { key: 'letterSpacing', label: 'Letter spacing', min: -20, max: 100, step: 0.5, default: 0, unit: 'px', group: 'appearance' },
  { key: 'lineHeight', label: 'Line height', min: 0.5, max: 3, step: 0.05, default: 1.2, unit: '×', group: 'appearance' },
  { key: 'gradientAngle', label: 'Gradient angle', min: 0, max: 360, step: 1, default: 45, unit: '°', group: 'appearance' },
  { key: 'starPoints', label: 'Star points', min: 3, max: 20, step: 1, default: 5, unit: '', group: 'appearance' },
  { key: 'innerRadius', label: 'Inner radius', min: 5, max: 95, step: 1, default: 45, unit: '%', group: 'appearance' },
  { key: 'polygonSides', label: 'Polygon sides', min: 3, max: 20, step: 1, default: 6, unit: '', group: 'appearance' },
  { key: 'volume', label: 'Volume', min: 0, max: 200, step: 1, default: 100, unit: '%', group: 'media' },
  { key: 'speed', label: 'Speed', min: 0.1, max: 4, step: 0.05, default: 1, unit: '×', group: 'media' },
];

export const PROP_MAP: Record<string, PropDef> = Object.fromEntries(PROP_DEFS.map(p => [p.key, p]));

export const EFFECT_DEFS: EffectDef[] = [
  { type: 'blur', label: 'Gaussian Blur', group: 'Blur & Sharpen', description: 'Softens the layer.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 0.5, default: 12, unit: 'px' }] },
  { type: 'sharpen', label: 'Sharpen', group: 'Blur & Sharpen', description: 'Increases local contrast (unsharp mask).', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 50, unit: '%' }] },
  { type: 'zoomBlur', label: 'Radial Zoom Blur', group: 'Blur & Sharpen', description: 'Stacks scaled copies outward from the centre.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 40, step: 1, default: 12, unit: 'px' }, { key: 'steps', label: 'Steps', min: 2, max: 12, step: 1, default: 5, unit: '' }] },
  { type: 'colorAdjust', label: 'Color & Light', group: 'Color', description: 'Brightness, contrast, saturation, hue, exposure.', params: [
    { key: 'brightness', label: 'Brightness', min: 0, max: 300, step: 1, default: 100, unit: '%' },
    { key: 'contrast', label: 'Contrast', min: 0, max: 300, step: 1, default: 100, unit: '%' },
    { key: 'saturation', label: 'Saturation', min: 0, max: 400, step: 1, default: 100, unit: '%' },
    { key: 'hue', label: 'Hue shift', min: -180, max: 180, step: 1, default: 0, unit: '°' },
    { key: 'exposure', label: 'Exposure', min: 0, max: 300, step: 1, default: 100, unit: '%' },
  ] },
  { type: 'grayscale', label: 'Black & White', group: 'Color', description: 'Desaturate the layer.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 100, unit: '%' }] },
  { type: 'sepia', label: 'Sepia', group: 'Color', description: 'Warm vintage tone.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 80, unit: '%' }] },
  { type: 'invert', label: 'Invert', group: 'Color', description: 'Invert colors.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 100, unit: '%' }] },
  { type: 'tint', label: 'Colorize / Tint', group: 'Color', description: 'Overlay a color using a blend mode.', params: [
    { key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 40, unit: '%' },
    { key: 'hue', label: 'Hue', min: 0, max: 360, step: 1, default: 210, unit: '°' },
  ] },
  { type: 'glow', label: 'Glow', group: 'Light', description: 'Colored halo around the layer.', params: [
    { key: 'radius', label: 'Radius', min: 0, max: 150, step: 1, default: 35, unit: 'px' },
    { key: 'intensity', label: 'Intensity', min: 0, max: 100, step: 1, default: 70, unit: '%' },
    { key: 'hue', label: 'Hue', min: 0, max: 360, step: 1, default: 280, unit: '°' },
  ] },
  { type: 'dropShadow', label: 'Drop Shadow', group: 'Light', description: 'Offset shadow behind the layer.', params: [
    { key: 'blur', label: 'Blur', min: 0, max: 120, step: 1, default: 24, unit: 'px' },
    { key: 'offsetX', label: 'Offset X', min: -200, max: 200, step: 1, default: 12, unit: 'px' },
    { key: 'offsetY', label: 'Offset Y', min: -200, max: 200, step: 1, default: 18, unit: 'px' },
    { key: 'opacity', label: 'Opacity', min: 0, max: 100, step: 1, default: 65, unit: '%' },
  ] },
  { type: 'outline', label: 'Stroke / Outline', group: 'Stylize', description: 'Draws a silhouette outline.', params: [
    { key: 'width', label: 'Width', min: 1, max: 80, step: 1, default: 10, unit: 'px' },
    { key: 'hue', label: 'Hue', min: 0, max: 360, step: 1, default: 190, unit: '°' },
  ] },
  { type: 'pixelate', label: 'Mosaic / Pixelate', group: 'Stylize', description: 'Chunky pixel blocks.', params: [{ key: 'size', label: 'Block size', min: 2, max: 120, step: 1, default: 16, unit: 'px' }] },
  { type: 'noise', label: 'Film Grain', group: 'Stylize', description: 'Animated random noise.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 25, unit: '%' }] },
  { type: 'vignette', label: 'Vignette', group: 'Stylize', description: 'Darken the edges.', params: [
    { key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 55, unit: '%' },
    { key: 'size', label: 'Size', min: 10, max: 100, step: 1, default: 65, unit: '%' },
  ] },
  { type: 'chromatic', label: 'Chromatic Aberration', group: 'Distort', description: 'Split the red and blue channels.', params: [{ key: 'amount', label: 'Amount', min: 0, max: 60, step: 1, default: 12, unit: 'px' }] },
  { type: 'chromaKey', label: 'Chroma Key', group: 'Keying', description: 'Remove a solid green/blue screen by hue.', params: [
    { key: 'hue', label: 'Key hue', min: 0, max: 360, step: 1, default: 120, unit: '°' },
    { key: 'threshold', label: 'Threshold', min: 1, max: 100, step: 1, default: 32, unit: '%' },
    { key: 'softness', label: 'Edge softness', min: 0, max: 100, step: 1, default: 18, unit: '%' },
  ] },
  { type: 'wave', label: 'Wave Distortion', group: 'Distort', description: 'Horizontal sine-wave displacement.', params: [
    { key: 'amplitude', label: 'Amplitude', min: 0, max: 120, step: 1, default: 20, unit: 'px' },
    { key: 'frequency', label: 'Frequency', min: 1, max: 30, step: 0.5, default: 6, unit: '' },
    { key: 'speed', label: 'Speed', min: 0, max: 10, step: 0.1, default: 2, unit: '' },
  ] },
  { type: 'shake', label: 'Camera Shake', group: 'Motion', description: 'Random positional jitter.', params: [
    { key: 'amount', label: 'Amount', min: 0, max: 150, step: 1, default: 18, unit: 'px' },
    { key: 'frequency', label: 'Frequency', min: 1, max: 30, step: 1, default: 12, unit: 'Hz' },
  ] },
  { type: 'pulse', label: 'Pulse / Beat', group: 'Motion', description: 'Rhythmic scale oscillation.', params: [
    { key: 'amount', label: 'Amount', min: 0, max: 100, step: 1, default: 12, unit: '%' },
    { key: 'bpm', label: 'Tempo', min: 20, max: 240, step: 1, default: 120, unit: 'BPM' },
  ] },
];

export const EFFECT_MAP: Record<string, EffectDef> = Object.fromEntries(EFFECT_DEFS.map(d => [d.type, d]));

export const SHAPE_LABELS: Record<ShapeKind, string> = {
  rectangle: 'Rectangle', rounded: 'Rounded rectangle', ellipse: 'Ellipse',
  triangle: 'Triangle', diamond: 'Diamond', pentagon: 'Pentagon', hexagon: 'Hexagon',
  star: 'Star', sparkle: 'Sparkle', burst: 'Burst', heart: 'Heart', arrow: 'Arrow',
  line: 'Line', cross: 'Plus', ring: 'Ring', arc: 'Arc', crescent: 'Crescent',
  halfCircle: 'Half circle', pie: 'Pie', callout: 'Callout', flower: 'Flower',
  trapezoid: 'Trapezoid', beam: 'Beam', flag: 'Curve', seal: 'Burst seal',
  polygon: 'Polygon', customPath: 'Vector path',
};

export const FONT_FAMILIES = [
  'Inter, system-ui, sans-serif', 'Arial, Helvetica, sans-serif', 'Georgia, serif',
  '"Times New Roman", serif', '"Courier New", monospace', 'Impact, fantasy',
  'Verdana, sans-serif', '"Trebuchet MS", sans-serif', 'monospace', 'serif', 'sans-serif',
];
