import type {
  Anim, Asset, BlendMode, Effect, EffectType, Easing, Layer, LayerType, Project, ShapeKind,
} from './types';
import { anim, makeKeyframe, isValidEasing } from './animation';
import { createEffect, createLayer, createProject, defaultStyle } from './defaults';
import { EFFECT_MAP, PROP_MAP } from './types';
import {
  attrNum, attrStr, blobToDataUrl, clamp, dataUrlToBlob, escapeXml, normalizeColor, uid,
} from './utils';
import type { StoredAsset } from './storage';
import { saveAsset } from './storage';

/* ================================ XML EXPORT ================================ */

export interface XmlExportOptions { embedMedia?: boolean; pretty?: boolean }

export async function projectToXml(
  project: Project,
  assets: Record<string, Asset>,
  opts: XmlExportOptions = {},
): Promise<{ xml: string; warnings: string[] }> {
  const warnings: string[] = [];
  const usedIds = new Set(project.layers.map(l => l.sourceId).filter(Boolean) as string[]);
  const usedAssets = [...usedIds].map(id => assets[id]).filter(Boolean) as Asset[];
  const assetLines: string[] = [];

  for (const a of usedAssets) {
    let src = '';
    if (a.url.startsWith('data:')) src = a.url;
    else if (opts.embedMedia && a.blob) {
      if (a.blob.size > 12 * 1024 * 1024) {
        warnings.push(`${a.name} is larger than 12 MB — referenced by name only.`);
      } else {
        try { src = await blobToDataUrl(a.blob); } catch { warnings.push(`Could not embed ${a.name}`); }
      }
    }
    assetLines.push(
      `    <Asset id="${escapeXml(a.id)}" name="${escapeXml(a.name)}" type="${a.type}" ` +
      `mime="${escapeXml(a.mime)}" width="${a.width}" height="${a.height}" duration="${a.duration}" ` +
      `src="${escapeXml(src)}" fontFamily="${escapeXml(a.fontFamily ?? '')}" />`,
    );
  }

  const layerLines = project.layers.map(l => serializeLayer(l, 4));
  const xml = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<EditorsHubMotion version="1.0" name="${escapeXml(project.name)}" width="${project.width}" height="${project.height}" ` +
      `fps="${project.fps}" duration="${project.duration}" background="${escapeXml(project.background)}">`,
    '  <Assets>',
    ...assetLines,
    '  </Assets>',
    '  <Layers>',
    ...layerLines,
    '  </Layers>',
    '</EditorsHubMotion>',
  ].join('\n');
  return { xml, warnings };
}

function serializeLayer(l: Layer, indent: number): string {
  const pad = ' '.repeat(indent);
  const props = Object.entries(l.props)
    .map(([key, a]) => serializeAnim(key, a as Anim, indent + 4))
    .join('\n');
  const effects = l.effects.map(e => {
    const params = Object.entries(e.params)
      .map(([key, a]) => serializeAnim(key, a as Anim, indent + 8))
      .join('\n');
    return [
      `${' '.repeat(indent + 4)}<Effect type="${e.type}" enabled="${e.enabled ? 1 : 0}">`,
      params,
      `${' '.repeat(indent + 4)}</Effect>`,
    ].filter(Boolean).join('\n');
  }).join('\n');
  const style = l.style;
  const styleAttrs = [
    `text="${escapeXml(style.text)}"`,
    `fontFamily="${escapeXml(style.fontFamily)}"`,
    `fontWeight="${style.fontWeight}"`,
    `italic="${style.italic ? 1 : 0}"`,
    `align="${style.align}"`,
    `uppercase="${style.uppercase ? 1 : 0}"`,
    `fillType="${style.fillType}"`,
    `color="${escapeXml(style.color)}"`,
    `color2="${escapeXml(style.color2)}"`,
    `strokeColor="${escapeXml(style.strokeColor)}"`,
    `strokeEnabled="${style.strokeEnabled === false ? 0 : 1}"`,
    `shape="${style.shape}"`,
    `fit="${style.fit}"`,
    `audio="${style.audio ? 1 : 0}"`,
    `loop="${style.loop ? 1 : 0}"`,
    `pathData="${escapeXml(style.pathData ?? '')}"`,
  ].join(' ');
  return [
    `${pad}<Layer id="${escapeXml(l.id)}" name="${escapeXml(l.name)}" type="${l.type}" ` +
      `start="${l.start}" duration="${l.duration}" inPoint="${l.inPoint}" ` +
      `visible="${l.visible ? 1 : 0}" locked="${l.locked ? 1 : 0}" blend="${l.blend}" ` +
      `parentId="${escapeXml(l.parentId ?? '')}" sourceId="${escapeXml(l.sourceId ?? '')}">`,
    `${' '.repeat(indent + 2)}<Properties>`,
    props,
    `${' '.repeat(indent + 2)}</Properties>`,
    `${' '.repeat(indent + 2)}<Style ${styleAttrs} />`,
    `${' '.repeat(indent + 2)}<Effects>`,
    effects,
    `${' '.repeat(indent + 2)}</Effects>`,
    `${' '.repeat(indent + 2)}<Mask type="${l.mask.type}" feather="${l.mask.feather}" invert="${l.mask.invert ? 1 : 0}" />`,
    `${pad}</Layer>`,
  ].join('\n');
}

function serializeAnim(name: string, a: Anim, indent: number): string {
  const pad = ' '.repeat(indent);
  if (!a.keyframes.length) return `${pad}<Property name="${escapeXml(name)}" value="${a.value}" />`;
  const kfs = a.keyframes.map(k =>
    `${' '.repeat(indent + 2)}<Keyframe time="${k.time}" value="${k.value}" easing="${k.easing}" />`).join('\n');
  return [`${pad}<Property name="${escapeXml(name)}" value="${a.value}">`, kfs, `${pad}</Property>`].join('\n');
}

/* ================================ XML IMPORT ================================ */

export interface XmlImportResult {
  project: Project | null;
  layers: Layer[];
  assets: Asset[];
  storedAssets: StoredAsset[];
  warnings: string[];
  stats: { layers: number; keyframes: number; effects: number; assets: number };
}

const tag = (el: Element): string => (el.localName || el.tagName).toLowerCase();
const all = (root: Element | Document): Element[] => Array.from(root.getElementsByTagName('*')) as Element[];
const kids = (el: Element): Element[] => Array.from(el.children) as Element[];
const kidsNamed = (el: Element, names: string[]): Element[] => kids(el).filter(c => names.includes(tag(c)));
const findKid = (el: Element, names: string[]): Element | undefined => kidsNamed(el, names)[0];
const truthy = (s: string | null): boolean => s === '1' || s?.toLowerCase() === 'true' || s?.toLowerCase() === 'yes';

const TYPE_ALIASES: Record<string, LayerType> = {
  solid: 'solid', background: 'solid', color: 'solid', fill: 'solid',
  shape: 'shape', vector: 'shape', rectangle: 'shape', ellipse: 'shape',
  text: 'text', title: 'text', caption: 'text', label: 'text',
  image: 'image', photo: 'image', picture: 'image', bitmap: 'image',
  video: 'video', movie: 'video', clip: 'video', footage: 'video',
  audio: 'audio', sound: 'audio', music: 'audio',
  group: 'group', null: 'group', folder: 'group', parent: 'group',
  camera: 'camera', cam: 'camera',
};

const PROP_ALIASES: Record<string, string> = {
  x: 'x', posx: 'x', positionx: 'x', translationx: 'x', left: 'x',
  y: 'y', posy: 'y', positiony: 'y', translationy: 'y', top: 'y',
  scalex: 'scaleX', scale_x: 'scaleX', widthscale: 'scaleX', sx: 'scaleX',
  scaley: 'scaleY', scale_y: 'scaleY', heightscale: 'scaleY', sy: 'scaleY',
  scale: 'scaleX', zoom: 'scaleX',
  rotation: 'rotation', rotate: 'rotation', angle: 'rotation', zrotation: 'rotation',
  opacity: 'opacity', alpha: 'opacity', transparency: 'opacity',
  anchorx: 'anchorX', anchory: 'anchorY', skewx: 'skewX',
  shapewidth: 'shapeWidth', width: 'shapeWidth', w: 'shapeWidth',
  shapeheight: 'shapeHeight', height: 'shapeHeight', h: 'shapeHeight',
  cornerradius: 'cornerRadius', radius: 'cornerRadius',
  strokewidth: 'strokeWidth', stroke: 'strokeWidth',
  strokecap: 'strokeCap', strokelinecap: 'strokeCap',
  strokejoin: 'strokeJoin', strokelinejoin: 'strokeJoin',
  strokestart: 'strokeStart', starttrim: 'strokeStart',
  strokeend: 'strokeEnd', endtrim: 'strokeEnd',
  fontsize: 'fontSize', size: 'fontSize', textsize: 'fontSize',
  letterspacing: 'letterSpacing', tracking: 'letterSpacing',
  lineheight: 'lineHeight', leading: 'lineHeight',
  gradientangle: 'gradientAngle', angle2: 'gradientAngle',
  starpoints: 'starPoints', innerradius: 'innerRadius', polygonsides: 'polygonSides',
  volume: 'volume', gain: 'volume', speed: 'speed', rate: 'speed', playbackrate: 'speed',
};

const EFFECT_ALIASES: Record<string, EffectType> = {
  blur: 'blur', gaussianblur: 'blur', gaussian: 'blur',
  sharpen: 'sharpen', unsharp: 'sharpen',
  zoomblur: 'zoomBlur', radialblur: 'zoomBlur',
  coloradjust: 'colorAdjust', colorcorrection: 'colorAdjust', brightness: 'colorAdjust', levels: 'colorAdjust',
  grayscale: 'grayscale', blackandwhite: 'grayscale', desaturate: 'grayscale',
  sepia: 'sepia', invert: 'invert', negative: 'invert',
  tint: 'tint', colorize: 'tint', duotone: 'tint',
  glow: 'glow', outerglow: 'glow', bloom: 'glow',
  dropshadow: 'dropShadow', shadow: 'dropShadow',
  outline: 'outline', stroke: 'outline',
  pixelate: 'pixelate', mosaic: 'pixelate',
  noise: 'noise', grain: 'noise', filmgrain: 'noise',
  vignette: 'vignette',
  chromatic: 'chromatic', chromaticaberration: 'chromatic', rgbsplit: 'chromatic',
  chromakey: 'chromaKey', greenscreen: 'chromaKey', bluescreen: 'chromaKey', keying: 'chromaKey',
  wave: 'wave', distort: 'wave', ripple: 'wave',
  shake: 'shake', camerashake: 'shake',
  pulse: 'pulse', beat: 'pulse',
};

const SHAPE_ALIASES: Record<string, ShapeKind> = {
  rectangle: 'rectangle', rect: 'rectangle', square: 'rectangle', roundedrect: 'rectangle',
  ellipse: 'ellipse', circle: 'ellipse', oval: 'ellipse',
  triangle: 'triangle', star: 'star', polygon: 'polygon', hexagon: 'polygon',
  line: 'line', heart: 'heart', arrow: 'arrow',
  rounded: 'rounded', diamond: 'diamond', pentagon: 'pentagon',
  sparkle: 'sparkle', burst: 'burst', cross: 'cross', plus: 'cross', ring: 'ring',
  arc: 'arc', crescent: 'crescent', halfcircle: 'halfCircle', pie: 'pie',
  callout: 'callout', flower: 'flower', trapezoid: 'trapezoid', beam: 'beam',
  flag: 'flag', seal: 'seal', custompath: 'customPath',
};

const norm = (s: string): string => s.toLowerCase().replace(/[\s_\-:]/g, '');

function firstNumber(v: string): number {
  const m = /-?\d+(?:\.\d+)?/.exec(v);
  return m ? Number(m[0]) : NaN;
}

function parseAnim(el: Element, name: string, fps: number, stats: XmlImportResult['stats']): Anim {
  const value = attrNum(el, ['value', 'val', 'v', 'default'], PROP_MAP[name]?.default ?? 0);
  const a = anim(value);
  const kfs: Element[] = [];
  for (const child of kids(el)) {
    const t = tag(child);
    if (['keyframe', 'key', 'kf', 'point', 'frame'].includes(t)) kfs.push(child);
    else if (t === 'keyframes' || t === 'keys') kfs.push(...kidsNamed(child, ['keyframe', 'key', 'kf', 'point', 'frame']));
  }
  // Inline keyframe list: keyframes="0:0;1:100"
  const inline = el.getAttribute('keyframes') || el.getAttribute('keys');
  if (inline) {
    for (const part of inline.split(/[;|]/)) {
      const [ts, vs, es] = part.split(/[:,]/);
      const tm = Number(ts), vm = Number(vs);
      if (Number.isFinite(tm) && Number.isFinite(vm)) {
        a.keyframes.push(makeKeyframe(tm, vm, isValidEasing((es || 'linear').trim())));
        stats.keyframes++;
      }
    }
  }
  for (const kf of kfs) {
    let time = attrNum(kf, ['time', 't', 'at', 'position', 'frame'], NaN);
    if (!Number.isFinite(time)) time = 0;
    if (tag(kf) === 'frame' || kf.hasAttribute('frame')) time = time / fps;
    let val = attrNum(kf, ['value', 'v', 'val', name.toLowerCase()], NaN);
    if (!Number.isFinite(val)) {
      const txt = kf.textContent?.trim() ?? '';
      val = firstNumber(txt);
    }
    if (!Number.isFinite(val)) continue;
    const easing: Easing = isValidEasing(attrStr(kf, ['easing', 'ease', 'interpolation'], 'linear'));
    a.keyframes.push(makeKeyframe(time, val, easing));
    stats.keyframes++;
  }
  a.keyframes.sort((x, y) => x.time - y.time);
  return a;
}

function readProperties(layerEl: Element, layer: Layer, fps: number, stats: XmlImportResult['stats']): void {
  const containers = [layerEl, ...kidsNamed(layerEl, ['properties', 'transform', 'animation', 'params'])];
  for (const container of containers) {
    for (const el of kids(container)) {
      const t = tag(el);
      if (!['property', 'prop', 'param', 'position', 'scale', 'rotation', 'opacity', 'anchor', 'transform'].includes(t)) continue;
      const rawName = attrStr(el, ['name', 'key', 'id', 'property'], t === 'property' || t === 'prop' || t === 'param' ? '' : t);
      const keys = rawName ? [rawName] : [t];
      // shorthand <Position x="..." y="..."/> / <Scale x="..." y="..."/>
      if (t === 'position' || t === 'scale' || t === 'anchor') {
        for (const axis of ['x', 'y']) {
          if (el.hasAttribute(axis) || el.hasAttribute(axis.toUpperCase())) {
            const v = Number(el.getAttribute(axis) ?? el.getAttribute(axis.toUpperCase()));
            if (Number.isFinite(v)) {
              const key = t === 'scale' ? (axis === 'x' ? 'scaleX' : 'scaleY')
                : t === 'anchor' ? (axis === 'x' ? 'anchorX' : 'anchorY') : axis;
              layer.props[key] = anim(v);
            }
          }
        }
      }
      for (const k of keys) {
        const mapped = PROP_ALIASES[norm(k)];
        if (!mapped) continue;
        const parsed = parseAnim(el, mapped, fps, stats);
        if (parsed.keyframes.length || el.hasAttribute('value') || el.hasAttribute('val') || el.hasAttribute('v')) {
          layer.props[mapped] = parsed;
        }
        if (t === 'scale' && mapped === 'scaleX') layer.props.scaleY = { ...parsed, keyframes: parsed.keyframes.map(k => ({ ...k, id: uid('kf') })) };
      }
    }
  }
  // Direct attributes on the layer element (x, y, opacity, rotation…)
  for (const attr of Array.from(layerEl.attributes)) {
    const mapped = PROP_ALIASES[norm(attr.name)];
    if (mapped && !['shapeWidth', 'shapeHeight'].includes(mapped) && Number.isFinite(Number(attr.value))) {
      layer.props[mapped] = anim(Number(attr.value));
    }
  }
}

function readStyle(layerEl: Element, layer: Layer): void {
  const containers = [layerEl, ...kidsNamed(layerEl, ['style', 'appearance', 'text', 'content', 'shape'])];
  const get = (names: string[], fallback = ''): string => {
    for (const c of containers) {
      const v = attrStr(c, names);
      if (v) return v;
    }
    return fallback;
  };
  const textEl = findKid(layerEl, ['text', 'content', 'string', 'value']);
  const text = (textEl?.textContent?.trim() || get(['text', 'content', 'string', 'value'])) || layer.style.text;
  layer.style.text = text;
  layer.style.fontFamily = get(['fontFamily', 'font', 'fontName', 'typeface'], layer.style.fontFamily);
  layer.style.fontWeight = attrNum(layerEl, ['fontWeight', 'weight'], layer.style.fontWeight);
  layer.style.italic = truthy(get(['italic'], layer.style.italic ? '1' : '0'));
  layer.style.uppercase = truthy(get(['uppercase', 'allCaps'], layer.style.uppercase ? '1' : '0'));
  const align = get(['align', 'textAlign', 'alignment'], layer.style.align);
  if (['left', 'center', 'right'].includes(align)) layer.style.align = align as LayerStyleAlign;
  const fillType = get(['fillType', 'fill'], layer.style.fillType);
  if (['solid', 'linear', 'radial'].includes(fillType)) layer.style.fillType = fillType as 'solid' | 'linear' | 'radial';
  layer.style.color = normalizeColor(get(['color', 'fillColor', 'primaryColor', 'backgroundColor'], layer.style.color), layer.style.color);
  layer.style.color2 = normalizeColor(get(['color2', 'secondaryColor', 'gradientColor'], layer.style.color2), layer.style.color2);
  layer.style.strokeColor = normalizeColor(get(['strokeColor', 'outlineColor'], layer.style.strokeColor), layer.style.strokeColor);
  const strokeEnabled = get(['strokeEnabled', 'strokeOn', 'outlineEnabled']);
  if (strokeEnabled !== '') layer.style.strokeEnabled = truthy(strokeEnabled);
  else if (layer.props.strokeWidth.value > 0) layer.style.strokeEnabled = true;
  const shape = get(['shape', 'shapeType', 'kind'], layer.style.shape);
  if (SHAPE_ALIASES[norm(shape)]) layer.style.shape = SHAPE_ALIASES[norm(shape)];
  else if (TYPE_ALIASES[norm(shape)] === 'shape') layer.style.shape = shape as ShapeKind;
  const fit = get(['fit', 'scaleMode', 'fitMode'], layer.style.fit);
  if (['cover', 'contain', 'stretch', 'none'].includes(fit)) layer.style.fit = fit as typeof layer.style.fit;
  const audio = get(['audio', 'includeAudio', 'sound']);
  if (audio) layer.style.audio = truthy(audio);
  const loop = get(['loop', 'looping']);
  if (loop) layer.style.loop = truthy(loop);
  const pathData = get(['pathData', 'vectorPath', 'd'], '');
  if (pathData) layer.style.pathData = pathData;
}

type LayerStyleAlign = 'left' | 'center' | 'right';

function readEffects(layerEl: Element, layer: Layer, fps: number, stats: XmlImportResult['stats'], warnings: string[]): void {
  const containers = [layerEl, ...kidsNamed(layerEl, ['effects', 'filters', 'fx'])];
  for (const c of containers) {
    for (const el of kids(c)) {
      if (!['effect', 'fx', 'filter'].includes(tag(el))) continue;
      const rawType = attrStr(el, ['type', 'name', 'id', 'effect'], '');
      const mapped = EFFECT_ALIASES[norm(rawType)] ?? (EFFECT_MAP[rawType] ? rawType as EffectType : null);
      if (!mapped) { warnings.push(`Unknown effect “${rawType}” — skipped.`); continue; }
      const fx: Effect = createEffect(mapped);
      fx.enabled = !(['0', 'false', 'off'].includes((el.getAttribute('enabled') ?? '').toLowerCase()));
      for (const child of kids(el)) {
        const t = tag(child);
        if (['param', 'property', 'prop', 'parameter'].includes(t) || (child.hasAttribute('name') && child.hasAttribute('value'))) {
          const rawKey = attrStr(child, ['name', 'key', 'id'], '');
          const key = Object.keys(fx.params).find(k => norm(k) === norm(rawKey)) ?? rawKey;
          if (key) fx.params[key] = parseAnim(child, key, fps, stats);
        }
      }
      for (const a of Array.from(el.attributes)) {
        const key = Object.keys(fx.params).find(k => norm(k) === norm(a.name));
        if (key && Number.isFinite(Number(a.value))) fx.params[key] = anim(Number(a.value));
      }
      layer.effects.push(fx);
      stats.effects++;
    }
  }
}

function readMask(layerEl: Element, layer: Layer): void {
  const el = findKid(layerEl, ['mask', 'matte']) ?? layerEl;
  const type = attrStr(el, ['maskType', 'type'], attrStr(el, ['mask'], 'none'));
  if (['none', 'rect', 'ellipse', 'linear'].includes(type)) layer.mask.type = type as typeof layer.mask.type;
  layer.mask.feather = attrNum(el, ['feather', 'maskFeather'], 0);
  layer.mask.invert = truthy(attrStr(el, ['invert', 'maskInvert'], '0'));
}

function parseLayer(el: Element, projectSize: { width: number; height: number }, fps: number, stats: XmlImportResult['stats'], warnings: string[]): Layer | null {
  const rawType = attrStr(el, ['type', 'kind', 'layerType', 'class'], '');
  const type: LayerType = TYPE_ALIASES[norm(rawType)] ?? (Object.keys(TYPE_ALIASES).includes(rawType) ? rawType as LayerType : 'shape');
  const layer = createLayer(type, {
    width: projectSize.width,
    height: projectSize.height,
    duration: attrNum(el, ['duration', 'length', 'outPoint'], 5),
  });
  // XML input may omit stroke settings; imported shapes then start unpainted.
  // An explicit legacy strokeWidth is enabled later by readStyle's migration.
  if (type === 'shape') {
    layer.props.strokeWidth.value = 0;
    layer.style.strokeEnabled = false;
  }
  layer.id = attrStr(el, ['id', 'uuid'], uid('layer'));
  layer.name = attrStr(el, ['name', 'label', 'title'], layer.name);
  layer.start = Math.max(0, attrNum(el, ['start', 'startTime', 'in', 'offset'], 0));
  layer.duration = Math.max(0.1, attrNum(el, ['duration', 'length', 'len', 'end'], layer.duration));
  layer.inPoint = Math.max(0, attrNum(el, ['inPoint', 'trimStart', 'sourceIn', 'sourceOffset'], 0));
  layer.visible = !(['0', 'false', 'hidden'].includes((attrStr(el, ['visible', 'visibility', 'show'], '1')).toLowerCase()));
  layer.locked = truthy(attrStr(el, ['locked', 'lock'], '0'));
  const blend = attrStr(el, ['blend', 'blendMode', 'mix'], 'source-over');
  const blendMapped = blend === 'normal' ? 'source-over' : blend === 'add' ? 'lighter' : blend;
  layer.blend = (Object.keys(BLEND_VALUES).includes(blendMapped) ? blendMapped : 'source-over') as BlendMode;
  layer.parentId = attrStr(el, ['parentId', 'parent'], '') || null;
  layer.sourceId = attrStr(el, ['sourceId', 'assetId', 'mediaId', 'src', 'source'], '') || undefined;

  readProperties(el, layer, fps, stats);
  readStyle(el, layer);
  readEffects(el, layer, fps, stats, warnings);
  readMask(el, layer);
  return layer;
}

const BLEND_VALUES: Record<string, true> = {
  'source-over': true, normal: true, multiply: true, screen: true, overlay: true,
  darken: true, lighten: true, 'color-dodge': true, 'color-burn': true,
  'hard-light': true, 'soft-light': true, difference: true, exclusion: true,
  hue: true, saturation: true, color: true, luminosity: true, lighter: true, add: true,
};

/** Parse an EditorsHubMotion project, an Alight-Motion-style preset, or any layer-ish XML. */
export async function parseMotionXml(source: string): Promise<XmlImportResult> {
  const warnings: string[] = [];
  const stats = { layers: 0, keyframes: 0, effects: 0, assets: 0 };
  const doc = new DOMParser().parseFromString(source, 'application/xml');
  const parseError = doc.querySelector('parsererror');
  if (parseError) throw new Error(`Invalid XML: ${parseError.textContent?.slice(0, 160) ?? 'parse error'}`);
  const root = doc.documentElement;
  if (!root) throw new Error('Empty XML document');

  const rootTag = tag(root);
  const isProjectRoot = ['editorshubmotion', 'project', 'alightmotion', 'amproject', 'motionproject', 'composition', 'comp'].includes(rootTag)
    || root.hasAttribute('fps') || root.hasAttribute('duration') || !!findKid(root, ['layers']);
  const width = attrNum(root, ['width', 'w', 'resolutionX'], 1080);
  const height = attrNum(root, ['height', 'h', 'resolutionY'], 1920);
  const fps = clamp(attrNum(root, ['fps', 'framerate', 'frameRate'], 30), 1, 120);
  const duration = clamp(attrNum(root, ['duration', 'length', 'totalTime'], 10), 0.5, 600);
  const size = { width, height };

  let project: Project | null = null;
  if (isProjectRoot) {
    project = createProject(attrStr(root, ['name', 'title'], 'Imported Project'), width, height);
    project.fps = fps;
    project.duration = duration;
    project.background = normalizeColor(attrStr(root, ['background', 'backgroundColor', 'bg'], '#0b0c14'), '#0b0c14');
  }

  /* assets */
  const assets: Asset[] = [];
  const storedAssets: StoredAsset[] = [];
  const idMap = new Map<string, string>();
  for (const el of all(root)) {
    if (!['asset', 'media', 'file', 'source', 'image', 'photo'].includes(tag(el))) continue;
    const oldId = attrStr(el, ['id', 'name', 'assetId'], '');
    const name = attrStr(el, ['name', 'filename', 'file'], 'media');
    const typeRaw = norm(attrStr(el, ['type', 'kind'], 'image'));
    const type: Asset['type'] = ['video', 'clip', 'movie'].includes(typeRaw) ? 'video'
      : ['audio', 'sound', 'music'].includes(typeRaw) ? 'audio'
        : ['font', 'typeface'].includes(typeRaw) ? 'font' : 'image';
    let src = attrStr(el, ['src', 'url', 'path', 'data', 'href'], '');
    if (!src) src = el.textContent?.trim() ?? '';
    if (src.startsWith('data:')) {
      const blob = dataUrlToBlob(src);
      if (blob) {
        const newId = uid('asset');
        const a: Asset = {
          id: newId, name, type, mime: blob.type || attrStr(el, ['mime'], 'application/octet-stream'),
          width: attrNum(el, ['width'], 0), height: attrNum(el, ['height'], 0),
          duration: attrNum(el, ['duration'], 0), url: src, blob,
          fontFamily: attrStr(el, ['fontFamily'], '') || undefined,
        };
        assets.push(a);
        storedAssets.push({
          id: newId, name, type, mime: a.mime, width: a.width, height: a.height,
          duration: a.duration, blob, fontFamily: a.fontFamily,
        });
        if (oldId) idMap.set(oldId, newId);
        stats.assets++;
      }
    } else if (src) {
      warnings.push(`“${name}” is referenced by path only — re-import the media in the Add panel.`);
    } else {
      warnings.push(`Media “${name}” was not embedded in this XML — re-import it in the Add panel to re-link it.`);
    }
  }

  /* layers */
  const layerEls: Element[] = [];
  for (const el of all(root)) {
    const t = tag(el);
    if (['layer', 'item', 'track', 'clip', 'element', 'object', 'shape', 'text', 'solid', 'image', 'video', 'audio', 'group', 'camera'].includes(t)
      && (el.hasAttribute('type') || el.hasAttribute('start') || el.hasAttribute('duration') || ['layer', 'item', 'track'].includes(t))) {
      layerEls.push(el);
    }
  }
  if (!layerEls.length && ['layer', 'item'].includes(rootTag)) layerEls.push(root);
  if (!layerEls.length) throw new Error('No layers found. Expected <Layers><Layer … /></Layers> or an XML preset containing layer elements.');

  const layers: Layer[] = [];
  for (const el of layerEls) {
    const layer = parseLayer(el, size, fps, stats, warnings);
    if (!layer) continue;
    if (layer.sourceId && idMap.has(layer.sourceId)) layer.sourceId = idMap.get(layer.sourceId);
    layers.push(layer);
    stats.layers++;
  }
  if (project) {
    project.layers = layers;
    project.duration = Math.max(project.duration, ...layers.map(l => l.start + l.duration));
  }
  if (!stats.layers) warnings.push('No usable layers were parsed.');

  for (const rec of storedAssets) void saveAsset(rec);
  return { project, layers, assets, storedAssets, warnings, stats };
}

/** A small, documented example XML project for the Import dialog. */
export const SAMPLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<EditorsHubMotion version="1.0" name="Imported Example" width="1080" height="1920" fps="30" duration="6" background="#0e0b1f">
  <Assets/>
  <Layers>
    <Layer name="Background" type="solid" start="0" duration="6" color="#6d28d9" color2="#db2777" fillType="linear">
      <Properties><Property name="gradientAngle" value="135"/></Properties>
    </Layer>
    <Layer name="Circle" type="shape" shape="ellipse" start="0" duration="6" color="#22d3ee" color2="#a78bfa">
      <Properties>
        <Property name="shapeWidth" value="600"/>
        <Property name="shapeHeight" value="600"/>
        <Property name="scaleX" value="100">
          <Keyframe time="0" value="20" easing="easeOutBack"/>
          <Keyframe time="1" value="100" easing="easeInOut"/>
          <Keyframe time="6" value="20" easing="easeIn"/>
        </Property>
        <Property name="scaleY" value="100">
          <Keyframe time="0" value="20" easing="easeOutBack"/>
          <Keyframe time="1" value="100" easing="easeInOut"/>
          <Keyframe time="6" value="20" easing="easeIn"/>
        </Property>
        <Property name="rotation" value="0">
          <Keyframe time="0" value="0" easing="linear"/>
          <Keyframe time="6" value="360" easing="linear"/>
        </Property>
      </Properties>
      <Effects><Effect type="glow" enabled="1"><Param name="radius" value="45"/><Param name="hue" value="190"/></Effect></Effects>
    </Layer>
    <Layer name="Headline" type="text" text="IMPORTED\nFROM XML" start="0.2" duration="5.8" color="#ffffff">
      <Properties>
        <Property name="fontSize" value="120"/>
        <Property name="opacity" value="100">
          <Keyframe time="0" value="0" easing="easeOut"/>
          <Keyframe time="0.8" value="100" easing="linear"/>
        </Property>
      </Properties>
    </Layer>
  </Layers>
</EditorsHubMotion>`;
