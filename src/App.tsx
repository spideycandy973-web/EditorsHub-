import { ChangeEvent, CSSProperties, PointerEvent as ReactPointerEvent, RefObject, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDown,
  ArrowDownToLine,
  ArrowLeft,
  ArrowUp,
  AudioLines,
  Box,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Circle,
  CircleHelp,
  Clock3,
  Copy,
  Crop,
  Ellipsis,
  Eye,
  EyeOff,
  ExternalLink,
  FilePlus2,
  Film,
  FolderOpen,
  Grid2X2,
  Home,
  Image as ImageIcon,
  Info,
  Layers,
  Link2,
  Maximize2,
  Move,
  Music2,
  Pause,
  Pencil,
  Play,
  Plus,
  Redo2,
  RotateCw,
  ScanLine,
  Search,
  Settings,
  Shapes,
  Share2,
  SlidersHorizontal,
  Smartphone,
  Sparkles,
  Square,
  SquareDashed,
  Trash2,
  Triangle,
  Type,
  Undo2,
  Upload,
  UserRound,
  Video,
  X,
  Zap,
} from 'lucide-react';
import sampleCarUrl from './assets/images/sample_vintage_car_1793170788562.jpg';
import samplePortraitUrl from './assets/images/sample_urban_portrait_1793170789024.jpg';
import sampleCityUrl from './assets/images/sample_cyber_cityscape_1793170789915.jpg';
import sampleAnimeUrl from './assets/images/sample_anime_mecha_1793170789185.jpg';
import { blobToDataUrl, buildAudioWaveform, dataUrlToBlob, getMediaAsset, inspectMediaDuration, saveMediaAsset } from './mediaStore';
import { createPrimitiveMesh, drawMesh3D, parseObjMesh, type Mesh3D } from './mesh3d';
import { runEsrganUpscale } from './aiUpscaler';
import { estimateBlockFlow, extrapolateMotionFrame, fingerprintFrame, flowToImageData, MotionWarpRenderer, type BlockFlow } from './opticalFlow';

type ProjectKind = 'project' | 'template' | 'element';
type Screen = 'home' | 'library' | 'editor';
type LayerKind = 'image' | 'video' | 'audio' | 'text' | 'shape' | 'drawing' | '3d' | 'group' | 'camera' | 'null';
type DrawPoint = { x: number; y: number };
type ProjectRatio = '16:9' | '9:16' | '4:5' | '1:1' | '4:3' | 'Custom';
type EasingPreset = 'linear' | 'easeIn' | 'easeOut' | 'easeInOut';
type ExportFormat = 'auto' | 'mp4' | 'webm';
type ExportQuality = 'balanced' | 'high';
type LayerKeyframe = { time: number; x: number; y: number; scale: number; rotation: number; rotationX?: number; rotationY?: number; depth?: number; opacity: number; easing: EasingPreset };
type SpeedKeyframe = { time: number; speed: number; easing: EasingPreset };
type StageDrag = { id: string; pointerId: number; mode: 'move' | 'scale'; startX: number; startY: number; baseX: number; baseY: number; baseScale: number; currentX: number; currentY: number; currentScale: number; stageWidth: number; stageHeight: number; centerX: number; centerY: number; startDistance: number };

type EditorLayer = {
  id: string;
  name: string;
  kind: LayerKind;
  src?: string;
  assetId?: string;
  sourceOffset?: number;
  sourceDuration?: number;
  externalPath?: string;
  waveform?: number[];
  text?: string;
  shape?: string;
  drawPoints?: DrawPoint[];
  brushSize?: number;
  groupId?: string;
  mesh3d?: Mesh3D;
  meshName?: 'Cube' | 'Sphere' | 'Pyramid' | 'Plane' | 'Torus' | 'Imported OBJ';
  rotationX?: number;
  rotationY?: number;
  depth?: number;
  frameBlend?: boolean;
  restoreStrength?: number;
  presetName?: string;
  speedKeyframes?: SpeedKeyframe[];
  color: string;
  visible: boolean;
  duration: number;
  start: number;
  x: number;
  y: number;
  scale: number;
  rotation: number;
  opacity: number;
  effects: string[];
  effectStrengths?: Record<string, number>;
  speed?: number;
  volume?: number;
  keyframes?: LayerKeyframe[];
};

type Project = {
  id: string;
  name: string;
  kind: ProjectKind;
  ratio: ProjectRatio;
  width: number;
  height: number;
  resolution: string;
  fps: number;
  background: string;
  updatedAt: string;
  duration: number;
  thumbnail?: string;
  thumbnailAssetId?: string;
  layers?: EditorLayer[];
};

const today = new Date();
const dateLabel = today.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });

const seedProjects: Project[] = [
  {
    id: 'sample-01', name: 'Midnight in motion', kind: 'project', ratio: '9:16', width: 1080, height: 1920,
    resolution: '1080p', fps: 60, background: '#08090c', updatedAt: dateLabel, duration: 15, thumbnail: sampleCityUrl,
    layers: [{ id: 'sample-layer-01', name: 'City lights', kind: 'image', src: sampleCityUrl, color: '#13e6aa', visible: true, duration: 15, start: 0, x: 50, y: 50, scale: 100, rotation: 0, opacity: 100, effects: [] }],
  },
  {
    id: 'sample-02', name: 'Afterglow / 04', kind: 'project', ratio: '1:1', width: 1080, height: 1080,
    resolution: '1080p', fps: 60, background: '#050506', updatedAt: dateLabel, duration: 4, thumbnail: sampleCarUrl,
    layers: [{ id: 'sample-layer-02', name: 'Afterglow still', kind: 'image', src: sampleCarUrl, color: '#f44393', visible: true, duration: 4, start: 0, x: 50, y: 50, scale: 100, rotation: 0, opacity: 100, effects: [] }],
  },
  {
    id: 'sample-03', name: 'Tokyo — soft focus', kind: 'project', ratio: '16:9', width: 1920, height: 1080,
    resolution: '1080p', fps: 30, background: '#08090c', updatedAt: dateLabel, duration: 8, thumbnail: samplePortraitUrl,
    layers: [{ id: 'sample-layer-03', name: 'Portrait', kind: 'image', src: samplePortraitUrl, color: '#b7b7c0', visible: true, duration: 8, start: 0, x: 50, y: 50, scale: 100, rotation: 0, opacity: 100, effects: [] }],
  },
  {
    id: 'template-01', name: 'Neon title opener', kind: 'template', ratio: '9:16', width: 1080, height: 1920,
    resolution: '1080p', fps: 30, background: '#10101a', updatedAt: dateLabel, duration: 6, thumbnail: sampleAnimeUrl,
  },
  {
    id: 'template-02', name: 'One frame story', kind: 'template', ratio: '4:5', width: 1080, height: 1350,
    resolution: '1080p', fps: 30, background: '#121315', updatedAt: dateLabel, duration: 10, thumbnail: samplePortraitUrl,
  },
];

const ratioDimensions: Record<Exclude<ProjectRatio, 'Custom'>, [number, number]> = {
  '16:9': [1920, 1080],
  '9:16': [1080, 1920],
  '4:5': [1080, 1350],
  '1:1': [1080, 1080],
  '4:3': [1440, 1080],
};

const resolutionScale = (preset: string, width: number, height: number) => /2k/i.test(preset)
  ? 2048 / Math.max(width, height, 1)
  : (Number.parseInt(preset, 10) || 1080) / 1080;

const colorSwatches = ['#0b0c0e', '#d9d1c3', '#fff4dd', '#81745e', '#b8662d', '#e7ad42', '#ffd478', '#9c7a42', '#f0e1bd'];
const ratios: ProjectRatio[] = ['16:9', '9:16', '4:5', '1:1', '4:3', 'Custom'];

const presetRecipes = [
  ['Studio Lift', 'Brightness / Contrast'], ['Sunset Exposure', 'Exposure'], ['Prism Hue', 'Hue / Saturation'],
  ['Golden Glow', 'Glow'], ['Soft Focus', 'Gaussian Blur'], ['Motion Streak', 'Motion Blur'],
  ['Vintage Warmth', 'Sepia'], ['Silver Mono', 'Grayscale'], ['Negative Space', 'Invert'],
  ['Detail Pop', 'Sharpen'], ['Analog Tape', 'VHS'], ['Fine Grain', 'Film Grain'],
  ['Chromatic Shift', 'RGB Split'], ['Soft Shadow', 'Drop Shadow'], ['Rain Glass', 'Rain'],
  ['Winter Dust', 'Snow'], ['Dream Haze', 'Fog'], ['Liquid Ripple', 'Ripple'],
  ['Wide Lens', 'Lens Distortion'], ['Afterimage', 'Echo'], ['Soft Fade', 'Fade In / Out'],
  ['Night Noise', 'Noise'], ['Fluid Warp', 'Turbulent Displace'], ['Cloud Field', 'Fractal Noise'],
  ['Color Harmony', 'Color Balance'], ['Graphic Ink', 'Posterize'], ['Solar Rays', 'Light Rays'],
] as const;
const effectPresets = presetRecipes.flatMap(([title, effect], recipeIndex) => Array.from({ length: 100 }, (_, level) => ({
  id: `eh-preset-${recipeIndex}-${level + 1}`, title: `${title} ${String(level + 1).padStart(2, '0')}`,
  effect, strength: level + 1,
})));

const initialEffects = [
  { title: 'Color & Light', tint: 'effect-color', effects: ['Brightness / Contrast', 'Exposure', 'Curves', 'Hue / Saturation', 'Color Balance'] },
  { title: 'Drawing & Edge', tint: 'effect-edge', effects: ['Outline', 'Find Edges', 'Glow Edge', 'Posterize'] },
  { title: 'Blur', tint: 'effect-blur', effects: ['Gaussian Blur', 'Motion Blur', 'Radial Blur', 'Sharpen'] },
  { title: 'Distortion / Warp', tint: 'effect-warp', effects: ['Turbulent Displace', 'Wave Warp', 'Ripple', 'Lens Distortion'] },
  { title: 'Procedural', tint: 'effect-procedural', effects: ['Fractal Noise', 'Clouds', 'Grid', 'Plasma'] },
  { title: '3D', tint: 'effect-3d', effects: ['Drop Shadow 3D', 'Extrude', 'Light Sweep'] },
  { title: 'Move / Transform', tint: 'effect-move', effects: ['Transform', 'Anchor Point', 'Mirror', 'Tile'] },
  { title: 'Repeat', tint: 'effect-repeat', effects: ['Echo', 'Kaleidoscope', 'Repeat', 'Trail'] },
  { title: 'Matte / Mask / Key', tint: 'effect-mask', effects: ['Chroma Key', 'Luma Key', 'Mask Feather', 'Set Matte'] },
  { title: 'Opacity / Visibility', tint: 'effect-opacity', effects: ['Opacity', 'Fade In / Out', 'Blink', 'Gradient Wipe'] },
  { title: 'Text', tint: 'effect-text', effects: ['Text Animator', 'Tracking', 'Typewriter', 'Text Warp'] },
  { title: 'Other', tint: 'effect-other', effects: ['Time Remap', 'Posterize Time', 'Strobe', 'Channel Shift'] },
  { title: 'Nature & Weather', tint: 'effect-nature', effects: ['Rain', 'Snow', 'Fog', 'Heat Haze'] },
  { title: 'Space & Cosmic', tint: 'effect-space', effects: ['Starfield', 'Nebula', 'Solar Flare', 'Orbit'] },
  { title: 'Flares, Rays & Energy', tint: 'effect-energy', effects: ['Lens Flare', 'Light Rays', 'Energy Beam', 'Glow'] },
  { title: 'Glow & Gradients', tint: 'effect-glow', effects: ['Glow', 'Gradient Ramp', 'Radial Glow', 'Bloom'] },
  { title: 'Grids & Tunnels', tint: 'effect-grid', effects: ['Tunnel', 'Infinite Grid', 'Wireframe', 'Pulse Grid'] },
  { title: 'Backgrounds & Textures', tint: 'effect-texture', effects: ['Paper Texture', 'Film Grain', 'Noise', 'Gradient Background'] },
  { title: 'Glitch & Digital Damage', tint: 'effect-glitch', effects: ['RGB Split', 'Datamosh', 'Digital Noise', 'Scanlines'] },
  { title: 'Retro TV, VHS & Film', tint: 'effect-vhs', effects: ['VHS', 'Film Gate', 'CRT', 'Vintage Film'] },
  { title: 'Warp & Lens', tint: 'effect-warp', effects: ['Fisheye', 'Spherize', 'Twirl', 'Magnify'] },
  { title: 'Water, Glass & Refraction', tint: 'effect-water', effects: ['Glass', 'Water Ripple', 'Refraction', 'Caustics'] },
  { title: '3D Layer & Depth', tint: 'effect-3d', effects: ['Depth Map', 'Parallax', 'Camera Blur'] },
  { title: '3D Shapes & Solids', tint: 'effect-shape3d', effects: ['Cube', 'Sphere', 'Extruded Shape'] },
  { title: '3D Props & Objects', tint: 'effect-props', effects: ['Anvil', 'Arrow', 'Barrel', 'Crystal'] },
  { title: 'Blur, Focus & Sharpen', tint: 'effect-blur', effects: ['Lens Blur', 'Focus Pull', 'Unsharp Mask'] },
  { title: 'Color Grading', tint: 'effect-color', effects: ['LUT', 'Color Wheels', 'Split Tone', 'Film Grade'] },
  { title: 'Color Looks & Tone Maps', tint: 'effect-tone', effects: ['Cinematic Tone', 'Duotone', 'Tonal Map'] },
  { title: 'Ink, Paint & Halftone', tint: 'effect-ink', effects: ['Halftone', 'Ink Bleed', 'Watercolor'] },
  { title: 'Strokes, Bevels & Shadows', tint: 'effect-edge', effects: ['Stroke', 'Inner Shadow', 'Bevel', 'Drop Shadow'] },
  { title: 'Wipes, Keys & Shatter', tint: 'effect-wipe', effects: ['Linear Wipe', 'Shatter', 'Luma Reveal'] },
  { title: 'Shake & Transform', tint: 'effect-move', effects: ['Camera Shake', 'Wiggle', 'Jitter'] },
];

const fmtTime = (seconds: number, fps = 30) => {
  const safe = Math.max(0, seconds);
  const mins = Math.floor(safe / 60).toString().padStart(2, '0');
  const secs = Math.floor(safe % 60).toString().padStart(2, '0');
  const frames = Math.min(Math.max(1, fps) - 1, Math.floor((safe % 1) * Math.max(1, fps))).toString().padStart(2, '0');
  return `${mins}:${secs}:${frames}`;
};

const makeLayer = (partial: Partial<EditorLayer> & Pick<EditorLayer, 'name' | 'kind'>): EditorLayer => ({
  id: `layer-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
  color: '#e5b653', visible: true, duration: 5, start: 0, x: 50, y: 50, scale: 100, rotation: 0, rotationX: 0, rotationY: 0, depth: 0, opacity: 100, effects: [],
  ...partial,
});

function getLayerTransformAt(layer: EditorLayer, time: number) {
  const frames = [...(layer.keyframes ?? [])].sort((a, b) => a.time - b.time);
  const baseline = { x: layer.x, y: layer.y, scale: layer.scale, rotation: layer.rotation, rotationX: layer.rotationX ?? 0, rotationY: layer.rotationY ?? 0, depth: layer.depth ?? 0, opacity: layer.opacity };
  const complete = (frame: LayerKeyframe) => ({ x: frame.x, y: frame.y, scale: frame.scale, rotation: frame.rotation, rotationX: frame.rotationX ?? baseline.rotationX, rotationY: frame.rotationY ?? baseline.rotationY, depth: frame.depth ?? baseline.depth, opacity: frame.opacity });
  if (!frames.length) return baseline;
  if (time <= frames[0].time) return complete(frames[0]);
  if (time >= frames[frames.length - 1].time) return complete(frames[frames.length - 1]);
  const rightIndex = frames.findIndex((frame) => frame.time >= time);
  const leftFrame = frames[Math.max(0, rightIndex - 1)];
  const rightFrame = frames[rightIndex];
  const left = complete(leftFrame);
  const right = complete(rightFrame);
  const raw = (time - leftFrame.time) / Math.max(.0001, rightFrame.time - leftFrame.time);
  const amount = leftFrame.easing === 'easeIn' ? raw * raw : leftFrame.easing === 'easeOut' ? 1 - (1 - raw) * (1 - raw) : leftFrame.easing === 'easeInOut' ? raw < .5 ? 2 * raw * raw : 1 - Math.pow(-2 * raw + 2, 2) / 2 : raw;
  return {
    x: left.x + (right.x - left.x) * amount,
    y: left.y + (right.y - left.y) * amount,
    scale: left.scale + (right.scale - left.scale) * amount,
    rotation: left.rotation + (right.rotation - left.rotation) * amount,
    rotationX: left.rotationX + (right.rotationX - left.rotationX) * amount,
    rotationY: left.rotationY + (right.rotationY - left.rotationY) * amount,
    depth: left.depth + (right.depth - left.depth) * amount,
    opacity: left.opacity + (right.opacity - left.opacity) * amount,
  };
}

function parseXMLProject(xml: string, fileName: string): Project {
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.getElementsByTagName('parsererror').length) throw new Error('This XML file is malformed or unsupported.');
  const elements = Array.from(document.getElementsByTagName('*'));
  const projectNode = elements.find((element) => /project|composition|timeline|movie/i.test(element.localName)) ?? document.documentElement;
  const attribute = (node: Element, ...names: string[]) => {
    for (const name of names) {
      const value = node.getAttribute(name) ?? node.getAttribute(name.toLowerCase());
      if (value?.trim()) return value.trim();
    }
    return '';
  };
  const property = (node: Element, ...names: string[]) => {
    const own = attribute(node, ...names);
    if (own) return own;
    for (const child of Array.from(node.getElementsByTagName('*'))) {
      const childValue = attribute(child, ...names);
      if (childValue) return childValue;
      if (names.some((name) => child.localName.toLowerCase() === name.toLowerCase()) && child.textContent?.trim()) return child.textContent.trim();
    }
    return '';
  };
  const parseNumber = (value: string, fallback: number) => {
    const parsed = Number.parseFloat(value.replace(/[^0-9.+-]/g, ''));
    return Number.isFinite(parsed) ? parsed : fallback;
  };
  const parseTime = (value: string, fps: number, fallback: number) => {
    if (!value) return fallback;
    const parts = value.split(':').map((part) => Number.parseFloat(part));
    if (parts.length >= 2 && parts.every(Number.isFinite)) {
      const [minutes = 0, seconds = 0, frames = 0] = parts;
      return minutes * 60 + seconds + frames / Math.max(1, fps);
    }
    return parseNumber(value, fallback);
  };
  const width = Math.max(1, Math.round(parseNumber(property(projectNode, 'width', 'canvasWidth', 'frameWidth'), 1080)));
  const height = Math.max(1, Math.round(parseNumber(property(projectNode, 'height', 'canvasHeight', 'frameHeight'), 1920)));
  const fps = Math.max(1, Math.min(99, parseNumber(property(projectNode, 'fps', 'frameRate', 'frame_rate'), 30)));
  const aspect = width / height;
  const ratio = (Object.entries(ratioDimensions).find(([, dimensions]) => Math.abs(dimensions[0] / dimensions[1] - aspect) < .025)?.[0] as ProjectRatio | undefined) ?? 'Custom';
  const projectDuration = Math.max(.1, parseTime(property(projectNode, 'duration', 'length', 'totalDuration'), fps, 15));
  const isLayerCandidate = (element: Element) => {
    const tag = element.localName.toLowerCase();
    const explicitType = attribute(element, 'type', 'kind', 'mediaType');
    const hasSource = property(element, 'src', 'source', 'path', 'file', 'url');
    const hasText = attribute(element, 'text', 'content') || tag === 'text';
    const typedLayer = /layer|clip|media|footage|item/.test(tag) && (explicitType || hasSource || hasText);
    return Boolean(typedLayer || /^(image|video|audio|text|shape|solid|rectangle|circle)$/i.test(tag));
  };
  const candidates = elements.filter((element) => {
    if (!isLayerCandidate(element)) return false;
    let parent = element.parentElement;
    while (parent && parent !== projectNode) {
      const parentTag = parent.localName.toLowerCase();
      if (/layer|clip|media|footage|item/.test(parentTag) && attribute(parent, 'type', 'kind', 'mediaType')) return false;
      parent = parent.parentElement;
    }
    return true;
  });
  const layers: EditorLayer[] = [];
  const seen = new Set<Element>();
  for (const node of candidates) {
    if (seen.has(node)) continue;
    const tag = node.localName.toLowerCase();
    const type = `${attribute(node, 'type', 'kind', 'mediaType')} ${tag}`.toLowerCase();
    let kind: LayerKind | null = null;
    if (/audio|music|sound/.test(type)) kind = 'audio';
    else if (/video|movie|footage/.test(type)) kind = 'video';
    else if (/image|photo|bitmap/.test(type)) kind = 'image';
    else if (/text|title|caption/.test(type)) kind = 'text';
    else if (/shape|solid|rectangle|circle|vector/.test(type)) kind = 'shape';
    if (!kind) continue;
    const source = property(node, 'src', 'source', 'path', 'file', 'url', 'href');
    const text = attribute(node, 'text', 'content', 'value') || (kind === 'text' ? node.textContent?.trim() ?? '' : '');
    const rawX = parseNumber(property(node, 'x', 'positionX', 'posX', 'left'), 50);
    const rawY = parseNumber(property(node, 'y', 'positionY', 'posY', 'top'), 50);
    const normalizePosition = (value: number, dimension: number) => value >= 0 && value <= 1 ? value * 100 : value > 100 ? value / dimension * 100 : value;
    const sourceIsRemote = /^(https?:|data:|blob:)/i.test(source);
    const duration = Math.max(.1, parseTime(property(node, 'duration', 'length', 'out'), fps, 5));
    const opacityValue = parseNumber(property(node, 'opacity', 'alpha'), 100);
    const layer = makeLayer({
      id: `xml-layer-${layers.length + 1}`,
      name: attribute(node, 'name', 'label', 'title') || `${kind[0].toUpperCase()}${kind.slice(1)} ${layers.length + 1}`,
      kind,
      src: source && sourceIsRemote ? source : undefined,
      externalPath: source && !sourceIsRemote ? source : undefined,
      text: text || undefined,
      shape: kind === 'shape' ? attribute(node, 'shape', 'shapeType') || tag : undefined,
      color: attribute(node, 'color', 'fill', 'hex') || '#f23e98',
      visible: !/^(false|0|no|hidden)$/i.test(attribute(node, 'visible', 'enabled')),
      start: Math.max(0, parseTime(property(node, 'start', 'in', 'startTime', 'offset'), fps, 0)),
      duration,
      x: normalizePosition(rawX, width),
      y: normalizePosition(rawY, height),
      scale: parseNumber(property(node, 'scale', 'zoom'), 100) <= 4 ? parseNumber(property(node, 'scale', 'zoom'), 1) * 100 : parseNumber(property(node, 'scale', 'zoom'), 100),
      rotation: parseNumber(property(node, 'rotation', 'rotate', 'angle'), 0),
      opacity: Math.max(0, Math.min(100, opacityValue <= 1 ? opacityValue * 100 : opacityValue)),
      effects: Array.from(node.getElementsByTagName('*')).filter((child) => /effect|filter|fx/i.test(child.localName)).map((child) => attribute(child, 'name', 'type', 'id') || child.textContent?.trim() || '').filter(Boolean),
    });
    const keyframeNodes = Array.from(node.getElementsByTagName('*')).filter((child) => /^(keyframe|key|point)$/i.test(child.localName));
    if (keyframeNodes.length) {
      layer.keyframes = keyframeNodes.map((frame) => {
        const easingValue = attribute(frame, 'easing', 'ease').toLowerCase();
        const easing: EasingPreset = easingValue.includes('linear') ? 'linear' : easingValue.includes('inout') || easingValue.includes('in-out') ? 'easeInOut' : easingValue.includes('out') ? 'easeOut' : 'easeIn';
        return {
          time: Math.max(0, parseTime(attribute(frame, 'time', 't'), fps, 0)),
          x: normalizePosition(parseNumber(attribute(frame, 'x', 'positionX'), layer.x), width),
          y: normalizePosition(parseNumber(attribute(frame, 'y', 'positionY'), layer.y), height),
          scale: parseNumber(attribute(frame, 'scale'), layer.scale),
          rotation: parseNumber(attribute(frame, 'rotation', 'angle'), layer.rotation),
          opacity: parseNumber(attribute(frame, 'opacity'), layer.opacity),
          easing,
        };
      }).sort((a, b) => a.time - b.time);
    }
    layers.push(layer);
    seen.add(node);
  }
  const duration = Math.max(projectDuration, ...layers.map((layer) => layer.start + layer.duration));
  return {
    id: `xml-${Date.now()}`,
    name: attribute(projectNode, 'name', 'title', 'label') || fileName.replace(/\.xml$/i, ''),
    kind: 'project', ratio, width, height,
    resolution: `${Math.round(Math.min(width, height) / 120) * 120}p`, fps,
    background: property(projectNode, 'background', 'backgroundColor', 'bgColor') || '#08090c',
    updatedAt: dateLabel, duration, layers,
  };
}

function getLayerSpeedAt(layer: EditorLayer, time: number) {
  const frames = [...(layer.speedKeyframes ?? [])].sort((a, b) => a.time - b.time);
  const fallback = Math.max(.1, Math.min(8, layer.speed ?? 1));
  if (!frames.length) return fallback;
  const ease = (value: number, preset: EasingPreset) => preset === 'easeIn' ? value * value : preset === 'easeOut' ? 1 - (1 - value) ** 2 : preset === 'easeInOut' ? value < .5 ? 2 * value * value : 1 - (-2 * value + 2) ** 2 / 2 : value;
  if (time <= frames[0].time) return frames[0].speed;
  if (time >= frames[frames.length - 1].time) return frames[frames.length - 1].speed;
  const rightIndex = frames.findIndex((frame) => frame.time >= time);
  const left = frames[rightIndex - 1]; const right = frames[rightIndex];
  const amount = ease((time - left.time) / Math.max(.0001, right.time - left.time), left.easing);
  return left.speed + (right.speed - left.speed) * amount;
}

function easingIntegral(value: number, preset: EasingPreset) {
  const t = Math.max(0, Math.min(1, value));
  if (preset === 'easeIn') return t ** 3 / 3;
  if (preset === 'easeOut') return t ** 2 - t ** 3 / 3;
  if (preset === 'easeInOut') return t <= .5 ? 2 * t ** 3 / 3 : -t + 2 * t ** 2 - 2 * t ** 3 / 3 + 1 / 6;
  return t ** 2 / 2;
}

function getLayerSourceTime(layer: EditorLayer, timelineTime: number) {
  const start = layer.start;
  const end = Math.max(start, timelineTime);
  const frames = [...(layer.speedKeyframes ?? [])].sort((a, b) => a.time - b.time);
  const points = [start, ...frames.map((frame) => frame.time).filter((value) => value > start && value < end), end].sort((a, b) => a - b);
  let elapsed = 0;
  for (let index = 1; index < points.length; index++) {
    const leftTime = points[index - 1]; const rightTime = points[index];
    if (rightTime <= leftTime) continue;
    const midpoint = (leftTime + rightTime) / 2;
    const segment = frames.find((frame, frameIndex) => frameIndex < frames.length - 1 && midpoint >= frame.time && midpoint <= frames[frameIndex + 1].time);
    if (!segment) { elapsed += getLayerSpeedAt(layer, midpoint) * (rightTime - leftTime); continue; }
    const next = frames[frames.indexOf(segment) + 1];
    const span = Math.max(.0001, next.time - segment.time);
    const u0 = (leftTime - segment.time) / span; const u1 = (rightTime - segment.time) / span;
    const normalizedArea = (easingIntegral(u1, segment.easing) - easingIntegral(u0, segment.easing)) / Math.max(.0001, u1 - u0);
    const averageSpeed = segment.speed + (next.speed - segment.speed) * normalizedArea;
    elapsed += averageSpeed * (rightTime - leftTime);
  }
  return (layer.sourceOffset ?? 0) + elapsed;
}

function applyLocalDetailRecovery(image: HTMLImageElement, strength: number, outputWidth = image.naturalWidth, outputHeight = image.naturalHeight): HTMLCanvasElement | HTMLImageElement {
  if (strength <= 0) return image;
  const targetWidth = Math.max(1, Math.round(outputWidth));
  const targetHeight = Math.max(1, Math.round(outputHeight));
  const scale = Math.min(1, 2560 / Math.max(targetWidth, targetHeight, 1));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(targetWidth * scale));
  canvas.height = Math.max(1, Math.round(targetHeight * scale));
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) return image;
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  try {
    const frame = context.getImageData(0, 0, canvas.width, canvas.height);
    const source = new Uint8ClampedArray(frame.data);
    const amount = Math.max(0, Math.min(1, strength / 100)) * .72;
    for (let y = 1; y < canvas.height - 1; y++) for (let x = 1; x < canvas.width - 1; x++) {
      const pixel = (y * canvas.width + x) * 4;
      for (let channel = 0; channel < 3; channel++) {
        const center = source[pixel + channel];
        const neighbors = (source[pixel - 4 + channel] + source[pixel + 4 + channel] + source[pixel - canvas.width * 4 + channel] + source[pixel + canvas.width * 4 + channel]) / 4;
        frame.data[pixel + channel] = Math.max(0, Math.min(255, center + (center - neighbors) * amount));
      }
    }
    context.putImageData(frame, 0, 0);
  } catch { return image; }
  return canvas;
}

const isLayerVisible = (layer: EditorLayer, layers: EditorLayer[]) => {
  if (!layer.visible) return false;
  if (!layer.groupId) return true;
  return layers.find((candidate) => candidate.id === layer.groupId)?.visible !== false;
};

function traceCanvasShape(context: CanvasRenderingContext2D, shapeName: string, size: number) {
  const shape = shapeName.toLowerCase();
  context.beginPath();
  if (shape === 'circle' || shape === 'ring') {
    context.arc(0, 0, size / 2, 0, Math.PI * 2);
    if (shape === 'ring') { context.lineWidth = Math.max(2, size * .1); context.stroke(); } else context.fill();
    return;
  }
  if (shape === 'line' || shape === 'wave') {
    context.lineWidth = Math.max(2, size * .07);
    context.lineCap = 'round';
    context.lineJoin = 'round';
    if (shape === 'line') { context.moveTo(-size / 2, 0); context.lineTo(size / 2, 0); }
    else for (let step = 0; step <= 32; step++) { const x = -size / 2 + size * step / 32; const y = Math.sin(step / 32 * Math.PI * 4) * size * .16; if (step === 0) context.moveTo(x, y); else context.lineTo(x, y); }
    context.stroke();
    return;
  }
  if (shape === 'triangle') {
    context.moveTo(0, -size / 2); context.lineTo(size / 2, size / 2); context.lineTo(-size / 2, size / 2);
  } else if (shape === 'star') {
    for (let point = 0; point < 10; point++) { const radius = point % 2 === 0 ? size / 2 : size / 4; const angle = point * Math.PI / 5 - Math.PI / 2; const x = Math.cos(angle) * radius; const y = Math.sin(angle) * radius; if (point === 0) context.moveTo(x, y); else context.lineTo(x, y); }
  } else if (shape === 'hexagon') {
    for (let point = 0; point < 6; point++) { const angle = point * Math.PI / 3 - Math.PI / 6; const x = Math.cos(angle) * size / 2; const y = Math.sin(angle) * size / 2; if (point === 0) context.moveTo(x, y); else context.lineTo(x, y); }
  } else if (shape === 'polygon') {
    for (let point = 0; point < 5; point++) { const angle = point * Math.PI * 2 / 5 - Math.PI / 2; const x = Math.cos(angle) * size / 2; const y = Math.sin(angle) * size / 2; if (point === 0) context.moveTo(x, y); else context.lineTo(x, y); }
  } else if (shape === 'diamond') {
    context.moveTo(0, -size / 2); context.lineTo(size / 2, 0); context.lineTo(0, size / 2); context.lineTo(-size / 2, 0);
  } else if (shape === 'arrow') {
    context.moveTo(-size / 2, -size * .16); context.lineTo(size * .08, -size * .16); context.lineTo(size * .08, -size * .38); context.lineTo(size / 2, 0); context.lineTo(size * .08, size * .38); context.lineTo(size * .08, size * .16); context.lineTo(-size / 2, size * .16);
  } else if (shape === 'speech bubble') {
    context.moveTo(-size * .42, -size * .33); context.lineTo(size * .42, -size * .33); context.lineTo(size * .42, size * .2); context.lineTo(size * .05, size * .2); context.lineTo(-size * .15, size * .43); context.lineTo(-size * .13, size * .2); context.lineTo(-size * .42, size * .2);
  } else {
    context.roundRect(-size / 2, -size / 2, size, size, Math.min(28, size * .12));
  }
  context.closePath();
  context.fill();
}

const cssFilterForEffects = (effects: string[], strengths: Record<string, number> = {}) => {
  const filters: string[] = [];
  for (const effect of effects) {
    const name = effect.toLowerCase();
    const amount = Math.max(0, Math.min(1, (strengths[effect] ?? 75) / 100));
    if (name.includes('brightness')) filters.push(`brightness(${1 + amount * .4}) contrast(${1 + amount * .18})`);
    else if (name.includes('exposure')) filters.push(`brightness(${1 + amount * .85})`);
    else if (name.includes('contrast') || name.includes('curves')) filters.push(`contrast(${1 + amount * .5})`);
    else if (name.includes('hue')) filters.push(`hue-rotate(${Math.round(amount * 180)}deg)`);
    else if (name.includes('saturation') || name.includes('color balance')) filters.push(`saturate(${1 + amount * 1.4})`);
    else if (name.includes('sepia') || name.includes('vintage') || name.includes('film grade')) filters.push(`sepia(${amount * .68}) contrast(${1 + amount * .16})`);
    else if (name.includes('grayscale') || name.includes('mono')) filters.push(`grayscale(${amount})`);
    else if (name.includes('invert')) filters.push(`invert(${amount})`);
    else if (name.includes('blur') || name.includes('focus')) filters.push(`blur(${(amount * (name.includes('motion') ? 5 : 8)).toFixed(1)}px)`);
    else if (name.includes('sharpen') || name.includes('edge') || name.includes('outline') || name.includes('posterize')) filters.push(`contrast(${1 + amount * .72}) saturate(${1 + amount * .22})`);
    else if (name.includes('glow') || name.includes('bloom') || name.includes('flare') || name.includes('light ray')) filters.push(`brightness(${1 + amount * .2}) drop-shadow(0 0 ${(amount * 12).toFixed(1)}px rgba(255,255,255,${(amount * .48).toFixed(2)}))`);
    else if (name.includes('vhs') || name.includes('crt') || name.includes('retro') || name.includes('scanline')) filters.push(`saturate(${1 - amount * .35}) contrast(${1 + amount * .32}) hue-rotate(${Math.round(amount * 12)}deg)`);
    else if (name.includes('glitch') || name.includes('channel') || name.includes('rgb split') || name.includes('datamosh')) filters.push(`saturate(${1 + amount * .55}) contrast(${1 + amount * .25}) hue-rotate(${Math.round(amount * 18)}deg)`);
    else if (name.includes('shadow') || name.includes('bevel')) filters.push(`drop-shadow(0 3px ${(amount * 16).toFixed(1)}px rgba(0,0,0,${(amount * .8).toFixed(2)}))`);
    else if (name.includes('rain') || name.includes('snow') || name.includes('fog') || name.includes('cloud')) filters.push(`contrast(${1 - amount * .12}) saturate(${1 - amount * .2})`);
    else if (name.includes('warp') || name.includes('ripple') || name.includes('lens') || name.includes('fisheye')) filters.push(`saturate(${1 + amount * .2}) contrast(${1 + amount * .12})`);
    else if (name.includes('echo') || name.includes('trail') || name.includes('wipe') || name.includes('fade')) filters.push(`opacity(${1 - amount * .18})`);
    else if (name.includes('grain') || name.includes('noise') || name.includes('texture')) filters.push(`contrast(${1 + amount * .16}) saturate(${1 - amount * .12})`);
    else filters.push(`saturate(${1 + amount * .12}) contrast(${1 + amount * .08})`);
  }
  return filters.length ? filters.join(' ') : 'none';
};

async function renderCompositionVideo(project: Project, layers: EditorLayer[], onProgress: (progress: number) => void, format: ExportFormat = 'auto', quality: ExportQuality = 'balanced'): Promise<Blob> {
  if (typeof MediaRecorder === 'undefined') throw new Error('Video rendering is not supported by this browser.');
  const longestSide = Math.max(project.width, project.height, 1);
  const renderScale = Math.min(1, (quality === 'high' ? 2560 : 2048) / longestSide);
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(2, Math.round(project.width * renderScale));
  canvas.height = Math.max(2, Math.round(project.height * renderScale));
  const context = canvas.getContext('2d', { alpha: false });
  if (!context) throw new Error('Could not create the render canvas.');

  const resources = new Map<string, HTMLImageElement | HTMLVideoElement | HTMLAudioElement>();
  const mediaLayers = layers.filter((layer) => layer.src && ['image', 'video', 'audio'].includes(layer.kind));
  const loadImage = (layer: EditorLayer) => new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Could not load ${layer.name}.`));
    image.src = layer.src!;
  });
  const loadMedia = (layer: EditorLayer) => new Promise<HTMLMediaElement>((resolve, reject) => {
    const element = document.createElement(layer.kind === 'video' ? 'video' : 'audio');
    element.preload = 'auto';
    if (element instanceof HTMLVideoElement) {
      element.playsInline = true;
      element.loop = true;
    }
    element.crossOrigin = layer.src?.startsWith('http') ? 'anonymous' : null;
    element.onloadeddata = () => resolve(element);
    element.onerror = () => reject(new Error(`Could not load ${layer.name}.`));
    element.src = layer.src!;
    element.load();
  });

  try {
    await Promise.all(mediaLayers.map(async (layer) => {
      const resource = layer.kind === 'image' ? await loadImage(layer) : await loadMedia(layer);
      resources.set(layer.id, resource as HTMLImageElement | HTMLVideoElement | HTMLAudioElement);
    }));
  } catch (error) {
    for (const resource of resources.values()) if (resource instanceof HTMLMediaElement) { resource.pause(); resource.removeAttribute('src'); resource.load(); }
    throw error;
  }

  const soundLayers = mediaLayers.filter((layer) => layer.kind === 'audio' || layer.kind === 'video');
  let audioContext: AudioContext | null = null;
  let audioDestination: MediaStreamAudioDestinationNode | null = null;
  if (soundLayers.length && typeof AudioContext !== 'undefined') {
    try {
      audioContext = new AudioContext();
      audioDestination = audioContext.createMediaStreamDestination();
      for (const layer of soundLayers) {
        const resource = resources.get(layer.id);
        if (resource instanceof HTMLMediaElement) {
          audioContext.createMediaElementSource(resource).connect(audioDestination);
        }
      }
      await audioContext.resume();
    } catch {
      audioDestination = null;
    }
  }

  if (typeof canvas.captureStream !== 'function') throw new Error('Canvas video recording is not supported by this browser.');
  const captureRate = Math.max(1, Math.min(project.fps, 99));
  let stream: MediaStream;
  let manualFrameCapture = false;
  try {
    const manual = canvas.captureStream(0);
    const track = manual.getVideoTracks()[0] as CanvasCaptureMediaStreamTrack | undefined;
    if (track && typeof track.requestFrame === 'function') { stream = manual; manualFrameCapture = true; }
    else { manual.getTracks().forEach((item) => item.stop()); stream = canvas.captureStream(captureRate); }
  } catch { stream = canvas.captureStream(captureRate); }
  const videoCaptureTrack = stream.getVideoTracks()[0] as CanvasCaptureMediaStreamTrack | undefined;
  const requestCaptureFrame = () => { if (manualFrameCapture) videoCaptureTrack?.requestFrame(); };
  audioDestination?.stream.getAudioTracks().forEach((track) => stream.addTrack(track));
  const mp4Types = ['video/mp4;codecs=avc1.42E01E,mp4a.40.2', 'video/mp4;codecs=avc1.42E01E', 'video/mp4'];
  const webmTypes = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'];
  const preferredTypes = format === 'mp4' ? [...mp4Types, ...webmTypes] : format === 'webm' ? [...webmTypes, ...mp4Types] : [...mp4Types, ...webmTypes];
  const mimeType = typeof MediaRecorder.isTypeSupported === 'function' ? preferredTypes.find((type) => MediaRecorder.isTypeSupported(type)) : undefined;
  let recorder: MediaRecorder;
  try {
    recorder = mimeType
      ? new MediaRecorder(stream, { mimeType, videoBitsPerSecond: Math.min(40_000_000, Math.round((quality === 'high' ? 14_000_000 : 9_000_000) * captureRate / 30)), audioBitsPerSecond: 192_000 })
      : new MediaRecorder(stream);
  } catch {
    stream.getTracks().forEach((track) => track.stop());
    if (audioContext) void audioContext.close();
    throw new Error('This browser does not support a compatible WebM encoder.');
  }

  const duration = Math.max(0.5, project.duration);
  const restoredImages = new Map<string, CanvasImageSource>();
  type FlowFrameState = { canvas: HTMLCanvasElement; context: CanvasRenderingContext2D; fullCanvas: HTMLCanvasElement; fullContext: CanvasRenderingContext2D; flowMapCanvas: HTMLCanvasElement; flowMapContext: CanvasRenderingContext2D; current: ImageData | null; previous: ImageData | null; currentTime: number; previousTime: number; fingerprint: number; flow: BlockFlow | null; textureKey: string };
  const opticalFlowStates = new Map<string, FlowFrameState>();
  let motionWarpRenderer: MotionWarpRenderer | null = null;
  let motionWarpAttempted = false;
  const getMotionCompensatedFrame = (layer: EditorLayer, video: HTMLVideoElement, timelineTime: number) => {
    if (getLayerSpeedAt(layer, timelineTime) >= .98) return video;
    let state = opticalFlowStates.get(layer.id);
    if (!state) {
      const analysisScale = Math.min(1, 256 / Math.max(video.videoWidth, video.videoHeight, 1));
      const width = Math.max(8, Math.round(video.videoWidth * analysisScale));
      const height = Math.max(8, Math.round(video.videoHeight * analysisScale));
      const flowCanvas = document.createElement('canvas'); flowCanvas.width = width; flowCanvas.height = height;
      const flowContext = flowCanvas.getContext('2d', { willReadFrequently: true });
      const fullCanvas = document.createElement('canvas'); fullCanvas.width = canvas.width; fullCanvas.height = canvas.height;
      const fullContext = fullCanvas.getContext('2d');
      const flowMapCanvas = document.createElement('canvas');
      const flowMapContext = flowMapCanvas.getContext('2d', { willReadFrequently: true });
      if (!flowContext || !fullContext || !flowMapContext) return video;
      state = { canvas: flowCanvas, context: flowContext, fullCanvas, fullContext, flowMapCanvas, flowMapContext, current: null, previous: null, currentTime: 0, previousTime: 0, fingerprint: -1, flow: null, textureKey: '' };
      opticalFlowStates.set(layer.id, state);
    }
    const localTime = getLayerSourceTime(layer, timelineTime);
    const sourceTime = Number.isFinite(video.duration) && video.duration > 0 ? localTime % video.duration : localTime;
    try {
      if (state.current && sourceTime + .02 < state.currentTime) { state.current = null; state.previous = null; state.flow = null; state.fingerprint = -1; }
      state.context.drawImage(video, 0, 0, state.canvas.width, state.canvas.height);
      const frame = state.context.getImageData(0, 0, state.canvas.width, state.canvas.height);
      const fingerprint = fingerprintFrame(frame);
      if (fingerprint !== state.fingerprint) {
        if (state.current && sourceTime > state.currentTime && sourceTime - state.currentTime < 1) {
          state.previous = state.current; state.previousTime = state.currentTime;
          state.current = frame; state.currentTime = sourceTime;
          state.flow = estimateBlockFlow(state.previous, frame, 8, 4);
        } else {
          state.previous = null; state.current = frame; state.currentTime = sourceTime; state.previousTime = sourceTime; state.flow = null;
        }
        state.fingerprint = fingerprint;
        state.fullContext.drawImage(video, 0, 0, state.fullCanvas.width, state.fullCanvas.height);
        if (state.flow) {
          state.flowMapCanvas.width = state.flow.columns; state.flowMapCanvas.height = state.flow.rows;
          state.flowMapContext = state.flowMapCanvas.getContext('2d', { willReadFrequently: true })!;
          state.flowMapContext.putImageData(flowToImageData(state.flow), 0, 0);
          state.textureKey = `${layer.id}:${fingerprint}:${state.currentTime}`;
        }
      }
      const interval = state.currentTime - state.previousTime;
      const amount = interval > 0 ? (sourceTime - state.currentTime) / interval : 0;
      if (state.current && state.flow && getLayerSpeedAt(layer, timelineTime) < .98 && amount > .015 && amount <= 1.15) {
        if (!motionWarpAttempted) {
          motionWarpAttempted = true;
          try { motionWarpRenderer = new MotionWarpRenderer(canvas.width, canvas.height); } catch { motionWarpRenderer = null; }
        }
        if (motionWarpRenderer) {
          try { return motionWarpRenderer.render(state.fullCanvas, state.flowMapCanvas, state.canvas.width, state.canvas.height, amount, state.textureKey); }
          catch { motionWarpRenderer = null; }
        }
        state.context.putImageData(extrapolateMotionFrame(state.current, state.flow, amount), 0, 0);
        return state.canvas;
      }
    } catch {
      // Cross-origin or unsupported frame readback falls back to the decoded video frame.
    }
    return video;
  };
  const drawFrame = (time: number) => {
    context.fillStyle = project.background;
    context.fillRect(0, 0, canvas.width, canvas.height);
    for (const layer of layers) {
      if (!isLayerVisible(layer, layers) || layer.kind === 'group' || time < layer.start || time >= layer.start + layer.duration) continue;
      const resource = resources.get(layer.id);
      const animated = getLayerTransformAt(layer, time);
      const centerX = canvas.width * animated.x / 100;
      const centerY = canvas.height * animated.y / 100;
      const size = Math.min(canvas.width, canvas.height) * .26 * animated.scale / 100;
      context.save();
      context.globalAlpha = Math.max(0, Math.min(1, animated.opacity / 100));
      context.translate(centerX, centerY);
      context.rotate(animated.rotation * Math.PI / 180);
      if ((layer.kind === 'image' || layer.kind === 'video') && resource) {
        if (resource instanceof HTMLVideoElement && resource.readyState < 2) { context.restore(); continue; }
        let drawable = resource as CanvasImageSource;
        if (layer.kind === 'image' && resource instanceof HTMLImageElement && (layer.restoreStrength ?? 0) > 0) {
          const cached = restoredImages.get(layer.id);
          if (cached) drawable = cached;
          else { const restored = applyLocalDetailRecovery(resource, layer.restoreStrength ?? 0, canvas.width, canvas.height); restoredImages.set(layer.id, restored); drawable = restored; }
        }
        const filter = cssFilterForEffects(layer.effects, layer.effectStrengths);
        if (filter !== 'none') context.filter = filter;
        const drawX = -canvas.width * animated.scale / 200; const drawY = -canvas.height * animated.scale / 200;
        const drawWidth = canvas.width * animated.scale / 100; const drawHeight = canvas.height * animated.scale / 100;
        if (layer.kind === 'video' && layer.frameBlend && resource instanceof HTMLVideoElement) {
          context.drawImage(getMotionCompensatedFrame(layer, resource, time), drawX, drawY, drawWidth, drawHeight);
        } else context.drawImage(drawable, drawX, drawY, drawWidth, drawHeight);
      } else if (layer.kind === 'text' && layer.text) {
        context.scale(animated.scale / 100, animated.scale / 100);
        context.fillStyle = layer.color;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.font = `700 ${Math.max(26, canvas.width * .075)}px sans-serif`;
        context.shadowColor = 'rgba(0,0,0,.42)';
        context.shadowBlur = Math.max(0, canvas.width * .012);
        context.fillText(layer.text, 0, 0, canvas.width * .9);
      } else if (layer.kind === 'drawing' && layer.drawPoints?.length) {
        context.scale(animated.scale / 100, animated.scale / 100);
        context.strokeStyle = layer.color;
        context.fillStyle = layer.color;
        context.lineWidth = Math.max(1, (layer.brushSize ?? 8) * renderScale);
        context.lineCap = 'round';
        context.lineJoin = 'round';
        context.beginPath();
        layer.drawPoints.forEach((point, pointIndex) => {
          const x = point.x / 100 * canvas.width - centerX;
          const y = point.y / 100 * canvas.height - centerY;
          if (pointIndex === 0) context.moveTo(x, y); else context.lineTo(x, y);
        });
        if (layer.drawPoints.length === 1) { context.arc(layer.drawPoints[0].x / 100 * canvas.width - centerX, layer.drawPoints[0].y / 100 * canvas.height - centerY, Math.max(1, (layer.brushSize ?? 8) * renderScale / 2), 0, Math.PI * 2); context.fill(); }
        else context.stroke();
      } else if (layer.kind === 'shape') {
        context.fillStyle = layer.color;
        context.strokeStyle = layer.color;
        const side = Math.min(canvas.width, canvas.height) * .26 * animated.scale / 100;
        traceCanvasShape(context, layer.shape ?? 'Rounded Rectangle', side);
      } else if (layer.kind === '3d') {
        const mesh = layer.mesh3d ?? createPrimitiveMesh(layer.meshName && layer.meshName !== 'Imported OBJ' ? layer.meshName : 'Cube');
        drawMesh3D(context, mesh, { size: Math.min(canvas.width, canvas.height) * .38 * animated.scale / 100, color: layer.color, rotationX: animated.rotationX, rotationY: animated.rotationY, depth: animated.depth });
      }
      context.restore();
    }
  };

  const syncMedia = (time: number) => {
    for (const layer of soundLayers) {
      const resource = resources.get(layer.id);
      if (!(resource instanceof HTMLMediaElement)) continue;
      const localTime = getLayerSourceTime(layer, time);
      const active = isLayerVisible(layer, layers) && time >= layer.start && time < layer.start + layer.duration;
      resource.volume = Math.max(0, Math.min(1, (layer.volume ?? 100) / 100));
      resource.playbackRate = Math.max(.1, Math.min(8, getLayerSpeedAt(layer, time)));
      if (resource instanceof HTMLVideoElement) {
        const sourceDuration = Number.isFinite(resource.duration) && resource.duration > 0 ? resource.duration : 1;
        if (active) {
          const target = localTime % sourceDuration;
          if (resource.paused || Math.abs(resource.currentTime - target) > .6) resource.currentTime = target;
          if (resource.paused) void resource.play().catch(() => undefined);
        } else resource.pause();
      } else {
        if (active && localTime < resource.duration) {
          if (resource.paused || Math.abs(resource.currentTime - localTime) > .6) resource.currentTime = localTime;
          if (resource.paused) void resource.play().catch(() => undefined);
        } else resource.pause();
      }
    }
  };

  drawFrame(0);
  onProgress(0);
  return await new Promise<Blob>((resolve, reject) => {
    const chunks: BlobPart[] = [];
    let failure: Error | null = null;
    let animation = 0;
    let timer = 0;
    let lastProgress = -1;
    let cleaned = false;
    const startTime = performance.now();
    const cleanup = () => {
      if (cleaned) return;
      cleaned = true;
      cancelAnimationFrame(animation);
      window.clearTimeout(timer);
      stream.getTracks().forEach((track) => track.stop());
      for (const resource of resources.values()) if (resource instanceof HTMLMediaElement) { resource.pause(); resource.removeAttribute('src'); resource.load(); }
      if (audioContext) void audioContext.close();
    };
    recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
    recorder.onerror = () => { failure = new Error('The video encoder encountered an error.'); if (recorder.state !== 'inactive') recorder.stop(); };
    recorder.onstop = () => {
      cleanup();
      if (failure) reject(failure);
      else {
        const blob = new Blob(chunks, { type: recorder.mimeType || 'video/webm' });
        if (!blob.size) reject(new Error('The render completed without video data.'));
        else { onProgress(100); resolve(blob); }
      }
    };
    try {
      recorder.start(500);
      const frameInterval = 1000 / captureRate;
      let lastFrameTime = -frameInterval;
      const render = () => {
        const now = performance.now();
        const elapsed = Math.min(duration, (now - startTime) / 1000);
        if (now - lastFrameTime >= frameInterval || elapsed >= duration) {
        try { syncMedia(elapsed); drawFrame(elapsed); requestCaptureFrame(); }
        catch (error) { failure = error instanceof Error ? error : new Error('Could not draw the video frame.'); if (recorder.state !== 'inactive') recorder.stop(); else { cleanup(); reject(failure); } return; }
        const percentage = Math.floor(elapsed / duration * 100);
        if (percentage !== lastProgress) { lastProgress = percentage; onProgress(percentage); }
        lastFrameTime = now;
        if (elapsed >= duration) { recorder.stop(); return; }
        }
        if (elapsed >= duration) { recorder.stop(); return; }
        timer = window.setTimeout(render, Math.max(1, frameInterval - (performance.now() - now)));
      };
      drawFrame(0); requestCaptureFrame();
      timer = window.setTimeout(render, frameInterval);
    } catch (error) {
      failure = error instanceof Error ? error : new Error('Could not start video recording.');
      if (recorder.state !== 'inactive') recorder.stop(); else { cleanup(); reject(failure); }
    }
  });
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('home');
  const [libraryTab, setLibraryTab] = useState<ProjectKind>('project');
  const [projects, setProjects] = useState<Project[]>(seedProjects);
  const [projectsHydrated, setProjectsHydrated] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [newProjectOpen, setNewProjectOpen] = useState(false);
  const [addSheetOpen, setAddSheetOpen] = useState(false);
  const [shapeSheetOpen, setShapeSheetOpen] = useState(false);
  const [drawingMode, setDrawingMode] = useState<'freehand' | 'vector' | null>(null);
  const [effectBrowserOpen, setEffectBrowserOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [projectSettingsOpen, setProjectSettingsOpen] = useState(false);
  const [renderProgress, setRenderProgress] = useState<number | null>(null);
  const [renderedVideoUrl, setRenderedVideoUrl] = useState<string | null>(null);
  const [renderedVideoType, setRenderedVideoType] = useState('video/webm');
  const [exportFormat, setExportFormat] = useState<ExportFormat>('auto');
  const [exportQuality, setExportQuality] = useState<ExportQuality>('balanced');
  const [qrOpen, setQrOpen] = useState(false);
  const [packageUrl, setPackageUrl] = useState('');
  const [query, setQuery] = useState('');
  const [sortBy, setSortBy] = useState<'Date' | 'Name' | 'Duration'>('Date');
  const [toast, setToast] = useState('');
  const [showLowQuality, setShowLowQuality] = useState(false);
  const [aiUpscaleBusy, setAiUpscaleBusy] = useState(false);
  const [aiUpscaleProgress, setAiUpscaleProgress] = useState<number | null>(null);
  const [aiUpscaleStatus, setAiUpscaleStatus] = useState('');
  const [showOffTimeline, setShowOffTimeline] = useState(true);
  const [rememberAddPage, setRememberAddPage] = useState(false);
  const [audioResampling, setAudioResampling] = useState('Ultra');
  const [layerDuration, setLayerDuration] = useState('2s');

  const [createKind, setCreateKind] = useState<ProjectKind>('project');
  const [createName, setCreateName] = useState('New Project');
  const [createRatio, setCreateRatio] = useState<ProjectRatio>('1:1');
  const [createResolution, setCreateResolution] = useState('1080p (FHD)');
  const [createFps, setCreateFps] = useState(60);
  const [createBackground, setCreateBackground] = useState('#d9d1c3');
  const [customRatio, setCustomRatio] = useState('3:2');

  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const currentProjectRef = useRef<Project | null>(null);
  const screenRef = useRef<Screen>('home');
  const [layers, setLayers] = useState<EditorLayer[]>([]);
  const layersRef = useRef<EditorLayer[]>([]);
  useEffect(() => { currentProjectRef.current = currentProject; }, [currentProject]);
  useEffect(() => { screenRef.current = screen; }, [screen]);
  const [selectedLayerId, setSelectedLayerId] = useState<string | null>(null);
  const [selectedLayerIds, setSelectedLayerIds] = useState<string[]>([]);
  const [isPlaying, setIsPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [editorTool, setEditorTool] = useState<string | null>(null);
  const [contextLayerId, setContextLayerId] = useState<string | null>(null);
  const [relinkLayerId, setRelinkLayerId] = useState<string | null>(null);
  const [searchLibrary, setSearchLibrary] = useState('');
  const [effectQuery, setEffectQuery] = useState('');
  const [effectCategory, setEffectCategory] = useState<string | null>(null);
  const [selectedEffect, setSelectedEffect] = useState<string | null>(null);
  const [renameLayer, setRenameLayer] = useState(false);
  const [textDraft, setTextDraft] = useState('Your text');
  const [textModalOpen, setTextModalOpen] = useState(false);
  const [panelSlider, setPanelSlider] = useState(75);
  const [canvasZoom, setCanvasZoom] = useState(100);
  const [undoStack, setUndoStack] = useState<EditorLayer[][]>([]);
  const [redoStack, setRedoStack] = useState<EditorLayer[][]>([]);

  const mediaInputRef = useRef<HTMLInputElement>(null);
  const xmlInputRef = useRef<HTMLInputElement>(null);
  const qrInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const toastTimerRef = useRef<number | undefined>(undefined);
  const mediaUrlCacheRef = useRef(new Map<string, string>());

  const selectedLayer = layers.find((layer) => layer.id === selectedLayerId) ?? null;

  const selectLayer = (id: string | null, additive = false) => {
    setSelectedLayerId(id);
    if (!id) { setSelectedLayerIds([]); return; }
    setSelectedLayerIds((current) => additive ? current.includes(id) ? current.filter((item) => item !== id) : [...current, id] : [id]);
  };

  const notify = (message: string) => {
    setToast(message);
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => setToast(''), 2800);
  };

  const getMediaUrl = async (assetId: string) => {
    const cachedUrl = mediaUrlCacheRef.current.get(assetId);
    if (cachedUrl) return cachedUrl;
    const storedAsset = await getMediaAsset(assetId);
    if (!storedAsset) return null;
    const url = URL.createObjectURL(storedAsset.blob);
    mediaUrlCacheRef.current.set(assetId, url);
    return url;
  };

  const hydrateProjectAssets = async (project: Project): Promise<Project> => {
    const reusedAssets = new Map<string, string>();
    const hydrateLayer = async (layer: EditorLayer): Promise<EditorLayer> => {
      let assetId = layer.assetId;
      if (layer.src?.startsWith('data:') && ['image', 'video', 'audio'].includes(layer.kind)) {
        assetId = reusedAssets.get(layer.src) ?? await saveMediaAsset(await dataUrlToBlob(layer.src), layer.name);
        reusedAssets.set(layer.src, assetId);
      }
      const src = assetId ? await getMediaUrl(assetId) : layer.src;
      if (assetId && !src) return { ...layer, src: undefined, assetId: undefined };
      const next = { ...layer, src: src ?? undefined, assetId };
      if (src && (next.kind === 'audio' || next.kind === 'video') && !next.sourceDuration) {
        next.sourceDuration = await inspectMediaDuration(src, next.kind);
      }
      if (next.kind === 'audio' && !next.waveform && src) {
        const media = assetId ? await getMediaAsset(assetId) : null;
        if (media) next.waveform = await buildAudioWaveform(media.blob);
      }
      return next;
    };
    const nextLayers = await Promise.all((project.layers ?? []).map(hydrateLayer));
    let thumbnail = project.thumbnail;
    let thumbnailAssetId = project.thumbnailAssetId;
    if (thumbnail?.startsWith('data:image/')) {
      thumbnailAssetId = reusedAssets.get(thumbnail) ?? await saveMediaAsset(await dataUrlToBlob(thumbnail), `${project.name} thumbnail`);
    }
    if (thumbnailAssetId) thumbnail = await getMediaUrl(thumbnailAssetId) ?? undefined;
    return { ...project, thumbnail, thumbnailAssetId, layers: nextLayers };
  };

  useEffect(() => {
    let cancelled = false;
    const restoreProjects = async () => {
      try {
        const stored = localStorage.getItem('editorshub-projects-v1');
        if (stored) {
          const parsed = JSON.parse(stored) as Project[];
          if (Array.isArray(parsed) && parsed.length) {
            const restored = await Promise.all(parsed.map((project) => hydrateProjectAssets(project)));
            if (!cancelled) {
              const demos = seedProjects.filter((project) => !restored.some((saved) => saved.id === project.id));
              setProjects([...restored, ...demos]);
            }
          }
        }
      } catch (error) {
        console.warn('Could not restore saved project list', error);
      } finally {
        if (!cancelled) setProjectsHydrated(true);
      }
    };
    void restoreProjects();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => () => {
    for (const url of mediaUrlCacheRef.current.values()) URL.revokeObjectURL(url);
    mediaUrlCacheRef.current.clear();
  }, []);

  useEffect(() => {
    if (!projectsHydrated || !currentProject) return;
    setProjects((list) => list.map((project) => project.id === currentProject.id ? { ...currentProject, layers } : project));
  }, [currentProject, layers, projectsHydrated]);

  useEffect(() => {
    if (!projectsHydrated) return;
    try {
      const safeProjects = projects.map((project) => ({
        ...project,
        thumbnail: project.thumbnailAssetId || project.thumbnail?.startsWith('blob:') ? undefined : project.thumbnail,
        layers: project.layers?.map((layer) => layer.assetId ? { ...layer, src: undefined } : layer),
      }));
      localStorage.setItem('editorshub-projects-v1', JSON.stringify(safeProjects));
    } catch (error) {
      console.warn('Could not save project list', error);
    }
  }, [projects, projectsHydrated]);

  useEffect(() => {
    if (!isPlaying || !currentProject) return;
    const timer = window.setInterval(() => {
      setTime((value) => {
        const next = value + 1 / Math.max(currentProject.fps, 1);
        if (next >= currentProject.duration) {
          setIsPlaying(false);
          return 0;
        }
        return next;
      });
    }, Math.max(1, 1000 / Math.max(currentProject.fps, 1)));
    return () => window.clearInterval(timer);
  }, [isPlaying, currentProject]);

  useEffect(() => {
    const activeVideo = layers.find((layer) => layer.kind === 'video' && isLayerVisible(layer, layers) && time >= layer.start && time < layer.start + layer.duration);
    const video = videoRef.current;
    if (!video || !activeVideo) { video?.pause(); return; }
    video.playbackRate = Math.max(.1, Math.min(8, getLayerSpeedAt(activeVideo, time)));
    video.volume = Math.max(0, Math.min(1, (activeVideo.volume ?? 100) / 100));
    const targetTime = getLayerSourceTime(activeVideo, time);
    if (Number.isFinite(video.duration) && video.duration > 0 && Math.abs(video.currentTime - targetTime) > .7) {
      video.currentTime = targetTime % video.duration;
    }
    if (isPlaying) void video.play().catch(() => undefined); else video.pause();
  }, [isPlaying, selectedLayerId, layers, time]);

  useEffect(() => () => {
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
  }, []);

  useEffect(() => () => {
    if (renderedVideoUrl) URL.revokeObjectURL(renderedVideoUrl);
  }, [renderedVideoUrl]);

  const openCreator = (kind: ProjectKind = 'project') => {
    setCreateKind(kind);
    setCreateName(kind === 'element' ? 'New Element' : kind === 'template' ? 'New Template' : 'New Project');
    setCreateRatio('1:1');
    setCreateResolution('1080p (FHD)');
    setCreateFps(60);
    setCreateBackground(kind === 'element' ? '#0b0c0e' : '#d9d1c3');
    setNewProjectOpen(true);
  };

  const openEditor = (project: Project) => {
    screenRef.current = 'editor';
    currentProjectRef.current = project;
    const projectLayers = project.layers?.map((layer) => ({ ...layer })) ?? [];
    setCurrentProject(project);
    setUndoStack([]);
    setRedoStack([]);
    layersRef.current = projectLayers;
    setLayers(projectLayers);
    setSelectedLayerId(projectLayers[0]?.id ?? null);
    setSelectedLayerIds(projectLayers[0]?.id ? [projectLayers[0].id] : []);
    setTime(0);
    setIsPlaying(false);
    setScreen('editor');
    setEditorTool(null);
    setContextLayerId(null);
  };

  const commitLayerList = (nextLayers: EditorLayer[]) => {
    const previousLayers = layersRef.current;
    setUndoStack((stack) => [...stack, previousLayers].slice(-40));
    setRedoStack([]);
    layersRef.current = nextLayers;
    setLayers(nextLayers);
  };

  const updateLayer = (id: string, patch: Partial<EditorLayer>) => {
    const target = layers.find((layer) => layer.id === id);
    if (!target) return;
    const transformKeys = ['x', 'y', 'scale', 'rotation', 'rotationX', 'rotationY', 'depth', 'opacity'] as const;
    const transformPatch: Partial<LayerKeyframe> = {};
    for (const key of transformKeys) if (patch[key] !== undefined) transformPatch[key] = patch[key] as number;
    if (Object.keys(transformPatch).length && target.keyframes?.length) {
      const frames = [...target.keyframes];
      const existing = frames.findIndex((frame) => Math.abs(frame.time - time) < .04);
      if (existing >= 0) frames[existing] = { ...frames[existing], ...transformPatch };
      else {
        const current = getLayerTransformAt(target, time);
        frames.push({ time, ...current, ...transformPatch, easing: 'easeInOut' });
        frames.sort((a, b) => a.time - b.time);
      }
      const { x: _x, y: _y, scale: _scale, rotation: _rotation, rotationX: _rotationX, rotationY: _rotationY, depth: _depth, opacity: _opacity, ...otherPatch } = patch;
      commitLayerList(layers.map((layer) => layer.id === id ? { ...layer, ...otherPatch, keyframes: frames } : layer));
      return;
    }
    commitLayerList(layers.map((layer) => layer.id === id ? { ...layer, ...patch } : layer));
  };

  const undoEdit = () => {
    const previous = undoStack[undoStack.length - 1];
    if (!previous) { notify('Nothing to undo'); return; }
    setRedoStack((stack) => [...stack, layers]);
    setUndoStack((stack) => stack.slice(0, -1));
    layersRef.current = previous;
    setLayers(previous);
    const validSelection = selectedLayerIds.filter((id) => previous.some((layer) => layer.id === id));
    const nextSelectedId = previous.some((layer) => layer.id === selectedLayerId) ? selectedLayerId : validSelection[0] ?? previous.at(-1)?.id ?? null;
    setSelectedLayerId(nextSelectedId);
    setSelectedLayerIds(validSelection.length ? validSelection : nextSelectedId ? [nextSelectedId] : []);
    notify('Edit undone');
  };

  const redoEdit = () => {
    const next = redoStack[redoStack.length - 1];
    if (!next) { notify('Nothing to redo'); return; }
    setUndoStack((stack) => [...stack, layers]);
    setRedoStack((stack) => stack.slice(0, -1));
    layersRef.current = next;
    setLayers(next);
    const validSelection = selectedLayerIds.filter((id) => next.some((layer) => layer.id === id));
    const nextSelectedId = next.some((layer) => layer.id === selectedLayerId) ? selectedLayerId : validSelection[0] ?? next.at(-1)?.id ?? null;
    setSelectedLayerId(nextSelectedId);
    setSelectedLayerIds(validSelection.length ? validSelection : nextSelectedId ? [nextSelectedId] : []);
    notify('Edit restored');
  };

  const createFromSettings = () => {
    const customParts = customRatio.split(':').map((part) => Number.parseInt(part, 10));
    const customWidth = Number.isFinite(customParts[0]) && customParts[0] > 0 ? customParts[0] : 3;
    const customHeight = Number.isFinite(customParts[1]) && customParts[1] > 0 ? customParts[1] : 2;
    const [baseWidth, baseHeight] = createRatio === 'Custom' ? (customWidth >= customHeight ? [1440, Math.round(1440 * customHeight / customWidth)] : [Math.round(1080 * customWidth / customHeight), 1080]) : ratioDimensions[createRatio];
    const resolutionNumber = Number.parseInt(createResolution, 10) || 1080;
    const scale = resolutionScale(createResolution, baseWidth, baseHeight);
    const width = Math.round(baseWidth * scale);
    const height = Math.round(baseHeight * scale);
    const project: Project = {
      id: `project-${Date.now()}`,
      name: createName.trim() || `Untitled ${createKind}`,
      kind: createKind,
      ratio: createRatio,
      width,
      height,
      resolution: /2k/i.test(createResolution) ? '2K' : `${resolutionNumber}p`,
      fps: Math.max(1, Math.min(99, createFps)),
      background: createBackground,
      updatedAt: dateLabel,
      duration: 15,
    };
    setProjects((list) => [project, ...list]);
    currentProjectRef.current = project;
    setCurrentProject(project);
    setUndoStack([]);
    setRedoStack([]);
    layersRef.current = [];
    setLayers([]);
    setSelectedLayerId(null);
    setSelectedLayerIds([]);
    setTime(0);
    setNewProjectOpen(false);
    screenRef.current = 'editor';
    setScreen('editor');
    notify(`${createKind === 'element' ? 'Element' : createKind === 'template' ? 'Template' : 'Project'} canvas created`);
  };

  const saveProjectSettings = (patch: Partial<Project>) => {
    setCurrentProject((project) => project ? { ...project, ...patch, updatedAt: dateLabel } : project);
    if (typeof patch.duration === 'number') {
      setTime((value) => Math.min(value, patch.duration ?? value));
      setIsPlaying(false);
    }
    setProjectSettingsOpen(false);
    notify('Composition settings saved');
  };

  const addLayer = (layer: EditorLayer) => {
    commitLayerList([...layersRef.current, layer]);
    selectLayer(layer.id);
    setEditorTool(null);
    setCurrentProject((project) => project ? { ...project, duration: Math.max(project.duration, layer.start + layer.duration) } : project);
  };

  const enhanceSelectedLayerWithAI = async () => {
    const target = selectedLayer;
    const sourceProjectId = currentProject?.id;
    if (!target || (target.kind !== 'image' && target.kind !== 'video')) { notify('Select a still image or an active video layer first'); return; }
    if (!currentProject || aiUpscaleBusy) return;
    setAiUpscaleBusy(true); setAiUpscaleProgress(0); setAiUpscaleStatus('Preparing local ESRGAN model…');
    const loadAIImage = (src: string, errorMessage: string, crossOrigin = false) => new Promise<HTMLImageElement>((resolve, reject) => {
      const loaded = new Image(); if (crossOrigin) loaded.crossOrigin = 'anonymous';
      loaded.onload = () => resolve(loaded); loaded.onerror = () => reject(new Error(errorMessage)); loaded.src = src;
    });
    try {
      let image: HTMLImageElement;
      if (target.kind === 'image') {
        if (!target.src) throw new Error('This image layer has no readable media source.');
        image = await loadAIImage(target.src, `Could not load ${target.name} for AI upscaling.`, true);
      } else {
        const video = videoRef.current;
        const active = isLayerVisible(target, layers) && time >= target.start && time < target.start + target.duration;
        const expectedSource = target.src ? new URL(target.src, window.location.href).href : '';
        if (!video || !active || (video.currentSrc !== expectedSource && video.src !== expectedSource) || video.readyState < 2) throw new Error('Seek to a frame where this video layer is active, then try AI Frame Restore again.');
        setIsPlaying(false); video.pause();
        const sourceTime = getLayerSourceTime(target, time);
        if (Number.isFinite(video.duration) && video.duration > 0 && Math.abs(video.currentTime - sourceTime) > .04) {
          await new Promise<void>((resolve, reject) => {
            const timeout = window.setTimeout(() => { video.removeEventListener('seeked', onSeeked); reject(new Error('Timed out seeking to the selected video frame.')); }, 2500);
            const onSeeked = () => { window.clearTimeout(timeout); resolve(); };
            video.addEventListener('seeked', onSeeked, { once: true });
            video.currentTime = sourceTime % video.duration;
          });
        }
        const frameCanvas = document.createElement('canvas');
        const scale = Math.min(1, 2048 / Math.max(video.videoWidth, video.videoHeight, 1));
        frameCanvas.width = Math.max(1, Math.round(video.videoWidth * scale)); frameCanvas.height = Math.max(1, Math.round(video.videoHeight * scale));
        const frameContext = frameCanvas.getContext('2d');
        if (!frameContext) throw new Error('Could not extract this video frame.');
        frameContext.drawImage(video, 0, 0, frameCanvas.width, frameCanvas.height);
        image = await loadAIImage(frameCanvas.toDataURL('image/png'), 'Could not read the selected video frame.');
      }
      const preparedCanvas = document.createElement('canvas');
      const inputScale = Math.min(1, 2048 / Math.max(image.naturalWidth, image.naturalHeight, 1), Math.sqrt(2_500_000 / Math.max(1, image.naturalWidth * image.naturalHeight)));
      if (inputScale < 1) {
        preparedCanvas.width = Math.max(1, Math.round(image.naturalWidth * inputScale)); preparedCanvas.height = Math.max(1, Math.round(image.naturalHeight * inputScale));
        const preparedContext = preparedCanvas.getContext('2d');
        if (!preparedContext) throw new Error('Could not prepare this image for the on-device AI model.');
        preparedContext.imageSmoothingEnabled = true; preparedContext.imageSmoothingQuality = 'high';
        preparedContext.drawImage(image, 0, 0, preparedCanvas.width, preparedCanvas.height);
        image = await loadAIImage(preparedCanvas.toDataURL('image/png'), 'Could not prepare the source image.');
      }
      const enhancedDataUrl = await runEsrganUpscale(image, setAiUpscaleProgress, setAiUpscaleStatus);
      const blob = await dataUrlToBlob(enhancedDataUrl);
      const assetId = await saveMediaAsset(blob, `${target.name} · ESRGAN 2x`);
      const src = await getMediaUrl(assetId);
      if (!src) throw new Error('The AI result could not be saved to local media storage.');
      const { id: _oldId, src: _oldSrc, assetId: _oldAssetId, kind: _oldKind, ...copy } = target;
      const videoFrame = target.kind === 'video';
      const duration = videoFrame ? Math.max(1 / currentProject.fps, Math.min(.5, target.start + target.duration - time, currentProject.duration - time)) : target.duration;
      if (currentProjectRef.current?.id !== sourceProjectId || screenRef.current !== 'editor') throw new Error('The editor project changed before AI processing finished, so the result was not added.');
      const enhancedLayer = makeLayer({
        ...copy,
        name: videoFrame ? `${target.name} · AI-restored frame` : `${target.name} · AI 2×`,
        kind: 'image', src, assetId, start: videoFrame ? time : target.start, duration,
        sourceOffset: undefined, sourceDuration: undefined, externalPath: undefined, waveform: undefined,
        speed: undefined, speedKeyframes: undefined, volume: undefined, frameBlend: false,
        restoreStrength: 0, presetName: undefined,
        ...(videoFrame ? { ...getLayerTransformAt(target, time), keyframes: undefined } : {}),
      });
      addLayer(enhancedLayer);
      notify(videoFrame ? 'AI-restored 2× frame added above the video' : 'ESRGAN 2× copy added · original kept unchanged');
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Local AI upscaling failed');
    } finally {
      setAiUpscaleBusy(false); setAiUpscaleProgress(null); setAiUpscaleStatus('');
    }
  };

  const importProjectPackage = async (packageData: Project, idPrefix: string) => {
    if (!packageData.name || !packageData.ratio || !packageData.width || !packageData.height) {
      throw new Error('This file does not contain a valid EditorsHub project package.');
    }
    const imported = await hydrateProjectAssets({ ...packageData, id: `${idPrefix}-${Date.now()}`, updatedAt: dateLabel });
    setProjects((list) => [imported, ...list]);
    setAddSheetOpen(false);
    openEditor(imported);
    notify('Project package imported');
  };

  const handleMediaFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    if (file.type === 'application/json' || file.name.toLowerCase().endsWith('.ehproj')) {
      setRelinkLayerId(null);
      try {
        await importProjectPackage(JSON.parse(await file.text()) as Project, 'import');
      } catch (error) {
        notify(error instanceof Error ? error.message : 'That file is not a valid EditorsHub project package');
      }
      return;
    }

    const extension = file.name.toLowerCase().split('.').pop() ?? '';
    if (extension === 'obj') {
      try {
        const mesh3d = parseObjMesh(await file.text());
        const start = screen === 'editor' ? time : 0;
        const layer = makeLayer({ name: file.name.replace(/\.obj$/i, ''), kind: '3d', mesh3d, meshName: 'Imported OBJ', color: '#e5b653', rotationX: 18, rotationY: 26, start, duration: currentProject?.duration ?? 10 });
        if (screen === 'editor' && currentProject) { addLayer(layer); setAddSheetOpen(false); notify('OBJ model imported into the 3D scene'); return; }
        const project: Project = { id: `model-${Date.now()}`, name: file.name.replace(/\.obj$/i, ''), kind: 'project', ratio: '9:16', width: 1080, height: 1920, resolution: '1080p', fps: 30, background: '#100d08', updatedAt: dateLabel, duration: 10, layers: [layer] };
        setProjects((list) => [project, ...list]); openEditor(project); notify('OBJ model opened in a new 3D project');
      } catch (error) { notify(error instanceof Error ? error.message : 'Could not import this OBJ model'); }
      return;
    }
    const kind: LayerKind = file.type.startsWith('video/') || ['mp4', 'mov', 'webm', 'mkv', 'm4v'].includes(extension)
      ? 'video'
      : file.type.startsWith('audio/') || ['mp3', 'wav', 'aac', 'm4a', 'ogg', 'flac'].includes(extension)
        ? 'audio'
        : file.type.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'avif', 'heic'].includes(extension)
          ? 'image'
          : 'null';
    if (kind === 'null') { notify('Choose an image, video, audio file, .obj model, or .ehproj package'); return; }

    try {
      if (relinkLayerId) {
        const targetLayer = layers.find((layer) => layer.id === relinkLayerId);
        if (targetLayer && targetLayer.kind !== kind) {
          setRelinkLayerId(null);
          notify(`Choose a ${targetLayer.kind} file to relink this layer`);
          return;
        }
        const assetId = await saveMediaAsset(file, file.name);
        const src = await getMediaUrl(assetId);
        if (!src || !targetLayer) throw new Error('The layer could not be relinked.');
        const sourceDuration = kind === 'audio' || kind === 'video' ? await inspectMediaDuration(src, kind) : undefined;
        const waveform = kind === 'audio' ? await buildAudioWaveform(file) : undefined;
        updateLayer(targetLayer.id, { src, assetId, externalPath: undefined, sourceOffset: 0, sourceDuration, duration: sourceDuration && sourceDuration > 0 ? Math.min(targetLayer.duration, sourceDuration) : targetLayer.duration, waveform, name: file.name.replace(/\.[^/.]+$/, '') });
        setRelinkLayerId(null);
        notify('Media source relinked');
        return;
      }
      const assetId = await saveMediaAsset(file, file.name);
      const src = await getMediaUrl(assetId);
      if (!src) throw new Error('The imported file could not be reopened from local storage.');
      const sourceDuration = kind === 'audio' || kind === 'video' ? await inspectMediaDuration(src, kind) : undefined;
      const waveform = kind === 'audio' ? await buildAudioWaveform(file) : undefined;
      const start = screen === 'editor' ? time : 0;
      const duration = sourceDuration && sourceDuration > 0 ? sourceDuration : currentProject?.duration ?? 10;
      const layer = makeLayer({
        name: file.name.replace(/\.[^/.]+$/, ''), kind, src, assetId, start, duration,
        sourceOffset: 0, sourceDuration, waveform,
      });
      if (screen === 'editor' && currentProject) {
        setAddSheetOpen(false);
        addLayer(layer);
        notify(`${kind === 'audio' ? 'Audio' : kind === 'video' ? 'Video' : 'Image'} added to timeline`);
        return;
      }
      const project: Project = {
        id: `import-${Date.now()}`, name: file.name.replace(/\.[^/.]+$/, ''), kind: 'project', ratio: '9:16', width: 1080,
        height: 1920, resolution: '1080p', fps: 30, background: '#08090c', updatedAt: dateLabel,
        duration: Math.max(10, duration), thumbnail: kind === 'image' ? src : undefined,
        thumbnailAssetId: kind === 'image' ? assetId : undefined, layers: [layer],
      };
      setProjects((list) => [project, ...list]);
      openEditor(project);
      notify('Media opened in a new project');
    } catch (error) {
      setRelinkLayerId(null);
      notify(error instanceof Error ? error.message : 'Could not import this media file');
    }
  };

  const handleXmlFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    void file.text().then((xml) => {
      try {
        const project = parseXMLProject(xml, file.name);
        setProjects((list) => [project, ...list]);
        openEditor(project);
        const unresolved = project.layers?.filter((layer) => layer.externalPath).length ?? 0;
        notify(unresolved ? `XML imported with ${project.layers?.length ?? 0} layers · ${unresolved} media path${unresolved === 1 ? '' : 's'} need relinking` : `XML imported · ${project.layers?.length ?? 0} layers found`);
      } catch (error) {
        notify(error instanceof Error ? error.message : 'Could not import this XML project');
      }
    }).catch(() => notify('Could not read that XML file'));
  };

  const importProjectFromLink = async () => {
    if (!packageUrl.trim()) {
      notify('Paste a project package link first');
      return;
    }
    try {
      const url = new URL(packageUrl.trim());
      const response = await fetch(url.toString());
      if (!response.ok) throw new Error(`Request failed (${response.status})`);
      const packageData = await response.json() as Project;
      await importProjectPackage(packageData, 'link');
      setPackageUrl('');
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'The project could not be imported';
      notify(reason.includes('Failed to fetch') ? 'Could not fetch this link. Check its sharing permissions and CORS settings.' : reason);
    }
  };

  const handleQrImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    const Detector = (window as unknown as {
      BarcodeDetector?: new (options?: { formats?: string[] }) => { detect: (source: ImageBitmap) => Promise<Array<{ rawValue: string }>> };
    }).BarcodeDetector;
    if (!Detector) {
      notify('QR image decoding is not supported here. Paste the package link instead.');
      return;
    }
    try {
      const bitmap = await createImageBitmap(file);
      const [code] = await new Detector({ formats: ['qr_code'] }).detect(bitmap);
      bitmap.close();
      if (!code?.rawValue) {
        notify('No project QR code found in that image');
        return;
      }
      setPackageUrl(code.rawValue);
      setQrOpen(false);
      notify('Project link scanned — tap Import package');
    } catch {
      notify('Could not read that QR image');
    }
  };

  const finishTextLayer = () => {
    const defaultDuration = Math.min(Number.parseInt(layerDuration, 10) || 2, currentProject?.duration ?? 15);
    const layer = makeLayer({ name: textDraft.trim() || 'Text', kind: 'text', text: textDraft.trim() || 'Your text', color: '#ffffff', start: time, duration: defaultDuration });
    addLayer(layer);
    setTextModalOpen(false);
    setAddSheetOpen(false);
    notify('Text layer added');
  };

  const makeShape = (shapeName: string) => {
    const defaultDuration = Math.min(Number.parseInt(layerDuration, 10) || 2, currentProject?.duration ?? 15);
    const layer = makeLayer({ name: shapeName, kind: 'shape', shape: shapeName, color: '#e5b653', start: time, duration: defaultDuration });
    addLayer(layer);
    setShapeSheetOpen(false);
    setAddSheetOpen(false);
    notify(`${shapeName} added to the composition`);
  };

  const splitSelectedLayer = () => {
    if (!selectedLayer) { notify('Select a layer before splitting'); return; }
    const splitAt = time;
    const layerEnd = selectedLayer.start + selectedLayer.duration;
    if (splitAt <= selectedLayer.start + .05 || splitAt >= layerEnd - .05) {
      notify('Move the playhead inside the selected clip to split it');
      return;
    }
    const splitTransform = getLayerTransformAt(selectedLayer, splitAt);
    const sourceFrames = selectedLayer.keyframes ?? [];
    const leftFrames = sourceFrames.filter((frame) => frame.time <= splitAt);
    let rightFrames = sourceFrames.filter((frame) => frame.time >= splitAt);
    if (sourceFrames.length && !rightFrames.some((frame) => Math.abs(frame.time - splitAt) < .001)) rightFrames = [{ time: splitAt, ...splitTransform, easing: 'easeInOut' }, ...rightFrames];
    const sourceSpeedFrames = [...(selectedLayer.speedKeyframes ?? [])];
    const splitSpeed = getLayerSpeedAt(selectedLayer, splitAt);
    let leftSpeedFrames = sourceSpeedFrames.filter((frame) => frame.time <= splitAt);
    let rightSpeedFrames = sourceSpeedFrames.filter((frame) => frame.time >= splitAt);
    if (sourceSpeedFrames.length && !rightSpeedFrames.some((frame) => Math.abs(frame.time - splitAt) < .001)) rightSpeedFrames = [{ time: splitAt, speed: splitSpeed, easing: 'easeInOut' }, ...rightSpeedFrames];
    const left = { ...selectedLayer, duration: splitAt - selectedLayer.start, keyframes: sourceFrames.length ? leftFrames : undefined, speedKeyframes: sourceSpeedFrames.length ? leftSpeedFrames : undefined };
    const sourceOffset = selectedLayer.kind === 'audio' || selectedLayer.kind === 'video' ? getLayerSourceTime(selectedLayer, splitAt) : selectedLayer.sourceOffset;
    const right = { ...selectedLayer, ...splitTransform, id: `layer-split-${Date.now()}`, name: `${selectedLayer.name} 2`, start: splitAt, duration: layerEnd - splitAt, sourceOffset, speed: splitSpeed, keyframes: sourceFrames.length ? rightFrames : undefined, speedKeyframes: sourceSpeedFrames.length ? rightSpeedFrames : undefined };
    const index = layers.findIndex((layer) => layer.id === selectedLayer.id);
    const next = [...layers];
    next.splice(index, 1, left, right);
    commitLayerList(next);
    selectLayer(right.id);
    notify('Clip split at the playhead');
  };

  const createGroup = (allowEmpty = false) => {
    const selected = allowEmpty ? [] : layers.filter((layer) => selectedLayerIds.includes(layer.id));
    const selectedGroup = selected.find((layer) => layer.kind === 'group');
    const children = selected.filter((layer) => layer.kind !== 'group');
    if (!selectedGroup && !allowEmpty && children.length < 2) { notify('Select at least two layers to group them'); return; }
    if (selectedGroup && !children.length && !allowEmpty) { notify('Shift-select layers with the group, then choose Group selected'); return; }
    const group = selectedGroup ?? makeLayer({ id: `group-${Date.now()}`, name: `Group ${layers.filter((layer) => layer.kind === 'group').length + 1}`, kind: 'group', color: '#e5b653', start: 0, duration: currentProject?.duration ?? 5 });
    const childIds = new Set(children.map((layer) => layer.id));
    const otherLayers = layers.filter((layer) => !childIds.has(layer.id) && layer.id !== selectedGroup?.id);
    const firstOriginalIndex = Math.min(...[...children.map((layer) => layers.indexOf(layer)), selectedGroup ? layers.indexOf(selectedGroup) : layers.length].filter((index) => index >= 0));
    const insertAt = Math.max(0, Math.min(otherLayers.length, firstOriginalIndex));
    const groupedChildren = children.map((layer) => ({ ...layer, groupId: group.id }));
    const groupStart = groupedChildren.length ? Math.min(...groupedChildren.map((layer) => layer.start)) : 0;
    const groupEnd = groupedChildren.length ? Math.max(...groupedChildren.map((layer) => layer.start + layer.duration)) : currentProject?.duration ?? 5;
    const groupedLayer = { ...group, start: groupStart, duration: Math.max(.1, groupEnd - groupStart) };
    const next = [...otherLayers];
    next.splice(insertAt, 0, groupedLayer, ...groupedChildren);
    commitLayerList(next);
    selectLayer(groupedLayer.id);
    notify(selectedGroup ? `${children.length} layer${children.length === 1 ? '' : 's'} added to group` : allowEmpty ? 'Empty group created' : `${children.length} layers grouped`);
  };

  const ungroup = (id: string) => {
    const group = layers.find((layer) => layer.id === id && layer.kind === 'group');
    if (!group) return;
    const next = layers.filter((layer) => layer.id !== id).map((layer) => layer.groupId === id ? { ...layer, groupId: undefined } : layer);
    commitLayerList(next);
    selectLayer(null);
    notify('Group dissolved; layers kept in the timeline');
  };

  const moveLayer = (id: string, direction: 'up' | 'down') => {
    const layer = layers.find((candidate) => candidate.id === id);
    if (!layer) return;
    const groupId = layer.kind === 'group' ? layer.id : layer.groupId;
    const block = groupId ? layers.filter((candidate) => candidate.id === groupId || candidate.groupId === groupId) : [layer];
    const blockIds = new Set(block.map((candidate) => candidate.id));
    const start = Math.min(...block.map((candidate) => layers.indexOf(candidate)));
    const target = start + (direction === 'up' ? -1 : 1);
    if (target < 0 || target + block.length > layers.length) return;
    const rest = layers.filter((candidate) => !blockIds.has(candidate.id));
    rest.splice(Math.max(0, Math.min(rest.length, target)), 0, ...block);
    commitLayerList(rest);
    notify(direction === 'up' ? 'Layer moved forward' : 'Layer moved backward');
  };

  const setSpeedAtPlayhead = (layer: EditorLayer, speed: number) => {
    const value = Math.max(.1, Math.min(8, speed));
    const frames = [...(layer.speedKeyframes ?? [])];
    if (!frames.length) { updateLayer(layer.id, { speed: value }); return; }
    const existing = frames.findIndex((frame) => Math.abs(frame.time - time) < .04);
    if (existing >= 0) frames[existing] = { ...frames[existing], speed: value };
    else frames.push({ time, speed: value, easing: 'easeInOut' });
    frames.sort((a, b) => a.time - b.time);
    updateLayer(layer.id, { speedKeyframes: frames });
  };

  const toggleSpeedKeyframe = (layer: EditorLayer) => {
    const frames = [...(layer.speedKeyframes ?? [])];
    const existing = frames.findIndex((frame) => Math.abs(frame.time - time) < .04);
    if (existing >= 0) frames.splice(existing, 1);
    else frames.push({ time, speed: getLayerSpeedAt(layer, time), easing: 'easeInOut' });
    frames.sort((a, b) => a.time - b.time);
    updateLayer(layer.id, { speedKeyframes: frames });
    notify(existing >= 0 ? 'Speed keyframe removed' : 'Velocity keyframe added');
  };

  const setSpeedKeyframeEasing = (layer: EditorLayer, easing: EasingPreset) => {
    const frames = (layer.speedKeyframes ?? []).map((frame) => Math.abs(frame.time - time) < .04 ? { ...frame, easing } : frame);
    updateLayer(layer.id, { speedKeyframes: frames });
  };

  const toggleKeyframe = (layer: EditorLayer) => {
    const frames = [...(layer.keyframes ?? [])];
    const existing = frames.findIndex((frame) => Math.abs(frame.time - time) < .04);
    if (existing >= 0) {
      frames.splice(existing, 1);
      updateLayer(layer.id, { keyframes: frames });
      notify('Keyframe removed');
      return;
    }
    frames.push({ time, ...getLayerTransformAt(layer, time), easing: 'easeInOut' });
    frames.sort((a, b) => a.time - b.time);
    updateLayer(layer.id, { keyframes: frames });
    notify('Keyframe added at the playhead');
  };

  const setKeyframeEasing = (layer: EditorLayer, easing: EasingPreset) => {
    const frames = (layer.keyframes ?? []).map((frame) => Math.abs(frame.time - time) < .04 ? { ...frame, easing } : frame);
    updateLayer(layer.id, { keyframes: frames });
  };

  const requestRelink = (id: string) => {
    setRelinkLayerId(id);
    setContextLayerId(null);
    mediaInputRef.current?.click();
  };

  const removeLayer = (id: string) => {
    const target = layers.find((layer) => layer.id === id);
    const next = layers.filter((layer) => layer.id !== id).map((layer) => target?.kind === 'group' && layer.groupId === id ? { ...layer, groupId: undefined } : layer);
    commitLayerList(next);
    if (selectedLayerIds.includes(id) || selectedLayerId === id) selectLayer(null);
    setContextLayerId(null);
    setEditorTool(null);
    notify(target?.kind === 'group' ? 'Group removed; child layers kept' : 'Layer removed');
  };

  const duplicateLayer = (layer: EditorLayer) => {
    const suffix = Date.now();
    if (layer.kind === 'group') {
      const newGroupId = `group-copy-${suffix}`;
      const originalChildren = layers.filter((item) => item.groupId === layer.id);
      const copiedGroup = { ...layer, id: newGroupId, name: `${layer.name} copy` };
      const copiedChildren = originalChildren.map((item, index) => ({ ...item, id: `layer-copy-${suffix}-${index}`, groupId: newGroupId, name: `${item.name} copy`, x: Math.min(90, item.x + 5), y: Math.min(90, item.y + 5) }));
      const afterIndex = Math.max(...[layers.findIndex((item) => item.id === layer.id), ...originalChildren.map((item) => layers.findIndex((child) => child.id === item.id))]);
      const next = [...layers];
      next.splice(afterIndex + 1, 0, copiedGroup, ...copiedChildren);
      commitLayerList(next);
      selectLayer(newGroupId);
    } else if (layer.groupId) {
      const copy = { ...layer, id: `layer-copy-${suffix}`, name: `${layer.name} copy`, x: Math.min(90, layer.x + 5), y: Math.min(90, layer.y + 5) };
      const siblingIndexes = layers.map((item, index) => item.groupId === layer.groupId ? index : -1).filter((index) => index >= 0);
      const next = [...layers];
      next.splice(Math.max(...siblingIndexes) + 1, 0, copy);
      commitLayerList(next);
      selectLayer(copy.id);
    } else {
      addLayer({ ...layer, id: `layer-copy-${suffix}`, name: `${layer.name} copy`, x: Math.min(90, layer.x + 5), y: Math.min(90, layer.y + 5) });
    }
    notify('Layer duplicated');
  };

  const exportProjectPackage = async () => {
    if (!currentProject) return;
    notify('Preparing a portable project package…');
    try {
      const embedLayer = async (layer: EditorLayer): Promise<EditorLayer> => {
        if (!layer.src || !['image', 'video', 'audio'].includes(layer.kind)) return layer;
        let media: Blob | null = null;
        if (layer.assetId) media = (await getMediaAsset(layer.assetId))?.blob ?? null;
        if (!media && layer.src.startsWith('data:')) return { ...layer, assetId: undefined };
        if (!media) {
          try {
            const response = await fetch(layer.src);
            if (response.ok) media = await response.blob();
          } catch { /* external sources without CORS remain linked in the package */ }
        }
        return media ? { ...layer, src: await blobToDataUrl(media), assetId: undefined } : { ...layer, assetId: undefined };
      };
      const packagedLayers = await Promise.all(layers.map(embedLayer));
      let thumbnail = currentProject.thumbnail;
      if (currentProject.thumbnailAssetId) {
        const image = await getMediaAsset(currentProject.thumbnailAssetId);
        if (image) thumbnail = await blobToDataUrl(image.blob);
      }
      const packageData = {
        ...currentProject,
        thumbnail,
        thumbnailAssetId: undefined,
        layers: packagedLayers,
        packageVersion: 2,
        app: 'EditorsHub',
        mediaEmbedded: true,
      };
      const blob = new Blob([JSON.stringify(packageData)], { type: 'application/json' });
      const href = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = href;
      anchor.download = `${currentProject.name.replace(/[^a-z0-9-_]+/gi, '_')}.ehproj`;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(href), 1500);
      notify(`Portable project package saved · ${(blob.size / 1024 / 1024).toFixed(1)} MB`);
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Could not package this project');
    }
  };

  const exportPreview = async () => {
    if (!currentProject) return;
    const canvas = document.createElement('canvas');
    const previewScale = Math.min(1, 2560 / Math.max(currentProject.width, currentProject.height, 1));
    canvas.width = Math.max(1, Math.round(currentProject.width * previewScale));
    canvas.height = Math.max(1, Math.round(currentProject.height * previewScale));
    const context = canvas.getContext('2d');
    if (!context) return;
    context.fillStyle = currentProject.background;
    context.fillRect(0, 0, canvas.width, canvas.height);
    for (const layer of layers.filter((item) => isLayerVisible(item, layers) && item.kind !== 'group' && time >= item.start && time < item.start + item.duration)) {
      if (layer.kind === 'text' && layer.text) {
        context.save();
        const animated = getLayerTransformAt(layer, time);
        context.globalAlpha = animated.opacity / 100;
        context.fillStyle = layer.color;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.font = `700 ${Math.max(34, canvas.width * 0.075)}px sans-serif`;
        context.translate(canvas.width * animated.x / 100, canvas.height * animated.y / 100);
        context.rotate(animated.rotation * Math.PI / 180);
        context.scale(animated.scale / 100, animated.scale / 100);
        context.fillText(layer.text, 0, 0, canvas.width * .9);
        context.restore();
      } else if (layer.kind === 'drawing' && layer.drawPoints?.length) {
        context.save();
        const animated = getLayerTransformAt(layer, time);
        context.globalAlpha = animated.opacity / 100;
        context.strokeStyle = layer.color;
        context.fillStyle = layer.color;
        context.lineWidth = Math.max(1, (layer.brushSize ?? 8) * previewScale);
        context.lineCap = 'round';
        context.lineJoin = 'round';
        context.translate(canvas.width * animated.x / 100, canvas.height * animated.y / 100);
        context.rotate(animated.rotation * Math.PI / 180);
        context.scale(animated.scale / 100, animated.scale / 100);
        context.beginPath();
        layer.drawPoints.forEach((point, pointIndex) => {
          const x = point.x / 100 * canvas.width - canvas.width / 2;
          const y = point.y / 100 * canvas.height - canvas.height / 2;
          if (pointIndex === 0) context.moveTo(x, y); else context.lineTo(x, y);
        });
        if (layer.drawPoints.length === 1) { context.arc(layer.drawPoints[0].x / 100 * canvas.width - canvas.width / 2, layer.drawPoints[0].y / 100 * canvas.height - canvas.height / 2, Math.max(1, (layer.brushSize ?? 8) * previewScale / 2), 0, Math.PI * 2); context.fill(); }
        else context.stroke();
        context.restore();
      } else if (layer.kind === 'shape') {
        context.save();
        const animated = getLayerTransformAt(layer, time);
        context.globalAlpha = animated.opacity / 100;
        context.fillStyle = layer.color;
        context.strokeStyle = layer.color;
        const size = Math.min(canvas.width, canvas.height) * 0.22 * animated.scale / 100;
        context.translate(canvas.width * animated.x / 100, canvas.height * animated.y / 100);
        context.rotate(animated.rotation * Math.PI / 180);
        traceCanvasShape(context, layer.shape ?? 'Rounded Rectangle', size);
        context.restore();
      } else if (layer.kind === '3d') {
        context.save();
        const animated = getLayerTransformAt(layer, time);
        context.globalAlpha = animated.opacity / 100;
        context.translate(canvas.width * animated.x / 100, canvas.height * animated.y / 100);
        context.rotate(animated.rotation * Math.PI / 180);
        const mesh = layer.mesh3d ?? createPrimitiveMesh(layer.meshName && layer.meshName !== 'Imported OBJ' ? layer.meshName : 'Cube');
        drawMesh3D(context, mesh, { size: Math.min(canvas.width, canvas.height) * .38 * animated.scale / 100, color: layer.color, rotationX: animated.rotationX, rotationY: animated.rotationY, depth: animated.depth });
        context.restore();
      } else if (layer.src && (layer.kind === 'image' || layer.kind === 'video')) {
        try {
          let drawable: CanvasImageSource;
          if (layer.kind === 'video' && videoRef.current && videoRef.current.readyState >= 2) {
            drawable = videoRef.current;
          } else {
            const image = new window.Image();
            image.src = layer.src;
            await new Promise<void>((resolve) => { image.onload = () => resolve(); image.onerror = () => resolve(); });
            drawable = layer.kind === 'image' && (layer.restoreStrength ?? 0) > 0 ? applyLocalDetailRecovery(image, layer.restoreStrength ?? 0, canvas.width, canvas.height) : image;
          }
          context.save();
          const animated = getLayerTransformAt(layer, time);
          context.globalAlpha = animated.opacity / 100;
          context.filter = cssFilterForEffects(layer.effects, layer.effectStrengths);
          const width = canvas.width * animated.scale / 100;
          const height = canvas.height * animated.scale / 100;
          context.translate(canvas.width * animated.x / 100, canvas.height * animated.y / 100);
          context.rotate(animated.rotation * Math.PI / 180);
          context.drawImage(drawable, -width / 2, -height / 2, width, height);
          context.restore();
        } catch { /* keep the rest of the frame exportable when a source is unavailable */ }
      }
    }
    canvas.toBlob((blob) => {
      if (!blob) return;
      const href = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = href;
      anchor.download = `${currentProject.name.replace(/[^a-z0-9-_]+/gi, '_')}_preview.png`;
      anchor.click();
      URL.revokeObjectURL(href);
      notify('Preview image exported');
    }, 'image/png');
  };

  const renderVideo = async (format: ExportFormat = exportFormat, quality: ExportQuality = exportQuality) => {
    if (!currentProject) return;
    setExportOpen(false);
    setRenderedVideoUrl(null);
    setRenderProgress(0);
    try {
      const blob = await renderCompositionVideo(currentProject, layers, setRenderProgress, format, quality);
      const mime = blob.type || 'video/webm';
      setRenderedVideoType(mime);
      setRenderedVideoUrl(URL.createObjectURL(blob));
      notify(`Video render complete · ${mime.includes('mp4') ? 'MP4' : 'WebM'}`);
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Video render failed');
    } finally {
      setRenderProgress(null);
    }
  };

  const downloadRenderedVideo = () => {
    if (!renderedVideoUrl || !currentProject) return;
    const extension = renderedVideoType.includes('mp4') ? 'mp4' : 'webm';
    const anchor = document.createElement('a');
    anchor.href = renderedVideoUrl;
    anchor.download = `${currentProject.name.replace(/[^a-z0-9-_]+/gi, '_')}.${extension}`;
    anchor.click();
  };

  const filteredProjects = useMemo(() => {
    const normalized = searchLibrary.toLowerCase();
    const list = projects.filter((project) => project.kind === libraryTab && project.name.toLowerCase().includes(normalized));
    if (sortBy === 'Name') return [...list].sort((a, b) => a.name.localeCompare(b.name));
    if (sortBy === 'Duration') return [...list].sort((a, b) => b.duration - a.duration);
    return list;
  }, [projects, libraryTab, searchLibrary, sortBy]);

  const formattedRatio = currentProject?.ratio === 'Custom' ? customRatio : currentProject?.ratio ?? '9:16';
  const stageStyle: CSSProperties = currentProject ? { aspectRatio: `${currentProject.width} / ${currentProject.height}`, background: currentProject.background } : {};

  return (
    <div className={`app-shell ${screen === 'editor' ? 'editor-shell' : ''}`}>
      {screen === 'editor' && currentProject ? (
        <EditorScreen
          project={currentProject}
          layers={layers}
          selectedLayerId={selectedLayerId}
          selectedLayer={selectedLayer}
          time={time}
          isPlaying={isPlaying}
          editorTool={editorTool}
          stageStyle={stageStyle}
          formattedRatio={formattedRatio}
          videoRef={videoRef}
          canvasZoom={canvasZoom}
          panelSlider={panelSlider}
          canUndo={undoStack.length > 0}
          canRedo={redoStack.length > 0}
          onUndo={undoEdit}
          onRedo={redoEdit}
          onBack={() => { screenRef.current = 'library'; setScreen('library'); setIsPlaying(false); setContextLayerId(null); }}
          onProjectSettings={() => setProjectSettingsOpen(true)}
          onPlay={() => setIsPlaying((value) => !value)}
          onSeek={(value) => { setTime(value); if (videoRef.current) { const end = Number.isFinite(videoRef.current.duration) ? videoRef.current.duration : value; videoRef.current.currentTime = Math.min(value, end); } }}
          selectedLayerIds={selectedLayerIds}
          lowQualityPreview={showLowQuality}
          onSelectLayer={selectLayer}
          onGroup={() => createGroup()}
          onUngroup={ungroup}
          onAdd={() => setAddSheetOpen(true)}
          onSetTool={(tool) => setEditorTool((value) => value === tool ? null : tool)}
          onSetContext={setContextLayerId}
          onUpdateLayer={updateLayer}
          onDeleteLayer={removeLayer}
          onSplit={splitSelectedLayer}
          onMoveLayer={moveLayer}
          onRelink={requestRelink}
          onKeyframe={toggleKeyframe}
          onSpeedValue={setSpeedAtPlayhead}
          onSpeedKeyframe={toggleSpeedKeyframe}
          onSpeedEasing={setSpeedKeyframeEasing}
          onAIUpscale={enhanceSelectedLayerWithAI}
          aiUpscaleBusy={aiUpscaleBusy}
          aiUpscaleProgress={aiUpscaleProgress}
          aiUpscaleStatus={aiUpscaleStatus}
          onSetEasing={setKeyframeEasing}
          onDuplicateLayer={duplicateLayer}
          onSaveElement={(layer) => {
            const element: Project = { id: `element-${Date.now()}`, name: layer.name, kind: 'element', ratio: currentProject?.ratio ?? '9:16', width: currentProject?.width ?? 1080, height: currentProject?.height ?? 1920, resolution: currentProject?.resolution ?? '1080p', fps: currentProject?.fps ?? 30, background: currentProject?.background ?? '#08090c', updatedAt: dateLabel, duration: layer.duration, layers: [{ ...layer }] };
            setProjects((list) => [element, ...list]);
            notify('Layer saved to My Elements');
          }}
          onOpenEffects={() => { setEffectBrowserOpen(true); setEffectCategory(null); setEffectQuery(''); }}
          onExport={() => setExportOpen(true)}
          onZoom={setCanvasZoom}
          onPanelSlider={setPanelSlider}
          onRename={() => setRenameLayer(true)}
          onNotify={notify}
          contextLayerId={contextLayerId}
        />
      ) : (
        <>
          <AppHeader onSettings={() => setSettingsOpen(true)} onProfile={() => notify('Profile sync is coming soon')} />
          <main className={`main-content ${screen === 'home' ? 'home-content' : 'library-content'}`}>
            {screen === 'home' ? (
              <HomeScreen
                onUploadXml={() => xmlInputRef.current?.click()}
                packageUrl={packageUrl}
                setPackageUrl={setPackageUrl}
                onImport={() => { void importProjectFromLink(); }}
                onQr={() => setQrOpen(true)}
                onOpenProject={() => openEditor(seedProjects[0])}
              />
            ) : (
              <LibraryScreen
                projects={filteredProjects}
                tab={libraryTab}
                onTab={setLibraryTab}
                query={searchLibrary}
                onQuery={setSearchLibrary}
                sortBy={sortBy}
                onSort={() => setSortBy((current) => current === 'Date' ? 'Name' : current === 'Name' ? 'Duration' : 'Date')}
                onOpen={openEditor}
                onCreate={() => openCreator(libraryTab === 'project' ? 'project' : libraryTab)}
                onDelete={(project) => { setProjects((list) => list.filter((item) => item.id !== project.id)); notify('Item deleted'); }}
                onDuplicate={(project) => {
                  const copy = { ...project, id: `copy-${Date.now()}`, name: `${project.name} copy`, updatedAt: dateLabel };
                  setProjects((list) => [copy, ...list]); notify('Duplicate created');
                }}
              />
            )}
          </main>
          <BottomNavigation screen={screen} onHome={() => { screenRef.current = 'home'; setScreen('home'); }} onLibrary={() => { setLibraryTab('project'); screenRef.current = 'library'; setScreen('library'); }} onCreate={() => openCreator('project')} />
        </>
      )}

      <input ref={mediaInputRef} type="file" accept="image/*,video/*,audio/*,.ehproj,.obj,application/json,model/obj" className="visually-hidden" onChange={handleMediaFile} />
      <input ref={xmlInputRef} type="file" accept=".xml,text/xml,application/xml" className="visually-hidden" onChange={handleXmlFile} />
      <input ref={qrInputRef} type="file" accept="image/*" className="visually-hidden" onChange={handleQrImage} />

      {settingsOpen && (
        <SettingsDialog
          lowQuality={showLowQuality} onLowQuality={setShowLowQuality}
          showTimeline={showOffTimeline} onShowTimeline={setShowOffTimeline}
          rememberAdd={rememberAddPage} onRememberAdd={setRememberAddPage}
          duration={layerDuration} onDuration={setLayerDuration}
          resampling={audioResampling} onResampling={setAudioResampling}
          onClose={() => setSettingsOpen(false)}
          onNotify={notify}
        />
      )}
      {newProjectOpen && (
        <NewProjectSheet
          kind={createKind} onKind={(value) => { setCreateKind(value); setCreateName((name) => name === 'New Project' || name === 'New Element' || name === 'New Template' ? (value === 'element' ? 'New Element' : 'New Project') : name); }}
          name={createName} onName={setCreateName}
          ratio={createRatio} onRatio={setCreateRatio}
          resolution={createResolution} onResolution={setCreateResolution}
          fps={createFps} onFps={setCreateFps}
          background={createBackground} onBackground={setCreateBackground}
          customRatio={customRatio} onCustomRatio={setCustomRatio}
          onClose={() => setNewProjectOpen(false)} onCreate={createFromSettings}
        />
      )}
      {projectSettingsOpen && currentProject && <ProjectSettingsDialog project={currentProject} onClose={() => setProjectSettingsOpen(false)} onSave={saveProjectSettings} />}
      {addSheetOpen && (
        <AddSheet
          onClose={() => setAddSheetOpen(false)}
          onMedia={() => { setRelinkLayerId(null); mediaInputRef.current?.click(); }}
          onText={() => { setTextDraft('Your text'); setTextModalOpen(true); }}
          onShape={() => setShapeSheetOpen(true)}
          onAudio={() => { setRelinkLayerId(null); mediaInputRef.current?.click(); notify('Choose an audio file to add to the timeline'); }}
          onAction={(name) => {
            if (name === 'Element / Project') { setAddSheetOpen(false); screenRef.current = 'library'; setScreen('library'); setLibraryTab('element'); return; }
            if (name === 'Template') { setAddSheetOpen(false); screenRef.current = 'library'; setScreen('library'); setLibraryTab('template'); return; }
            if (name === 'Freehand Drawing' || name === 'Vector Drawing') {
              setAddSheetOpen(false);
              setDrawingMode(name === 'Freehand Drawing' ? 'freehand' : 'vector');
              return;
            }
            if (name === 'Empty Group') { setAddSheetOpen(false); createGroup(true); return; }
            if (name === '3D Object') {
              addLayer(makeLayer({ name: '3D Cube', kind: '3d', mesh3d: createPrimitiveMesh('Cube'), meshName: 'Cube', color: '#e5b653', rotationX: 22, rotationY: 32, start: time, duration: currentProject?.duration ?? 5 }));
              setAddSheetOpen(false); notify('3D cube added · open 3D Lab to edit'); return;
            }
            const kind: LayerKind = name === 'Camera' ? 'camera' : 'null';
            addLayer(makeLayer({ name: name === 'Camera' ? 'Camera 1' : 'Null 1', kind, color: '#e5b653', start: time, duration: currentProject?.duration ?? 5 }));
            setAddSheetOpen(false);
            notify(`${name} layer added`);
          }}
        />
      )}
      {shapeSheetOpen && <ShapeSheet onClose={() => setShapeSheetOpen(false)} onSelect={makeShape} />}
      {drawingMode && <DrawingDialog mode={drawingMode} projectWidth={currentProject?.width ?? 1080} onClose={() => setDrawingMode(null)} onSave={(points, color, brushSize) => { const duration = Math.max(1, Number.parseInt(layerDuration, 10) || 2); addLayer(makeLayer({ name: drawingMode === 'freehand' ? 'Freehand drawing' : 'Vector drawing', kind: 'drawing', drawPoints: points, brushSize, color, start: time, duration })); setDrawingMode(null); setAddSheetOpen(false); notify('Drawing added to the composition'); }} />}
      {effectBrowserOpen && (
        <EffectBrowser
          query={effectQuery} onQuery={setEffectQuery}
          category={effectCategory} onCategory={setEffectCategory}
          onClose={() => setEffectBrowserOpen(false)}
          onSelectPreset={(preset) => {
            if (!selectedLayer) { notify('Select a layer before applying a look preset'); return; }
            updateLayer(selectedLayer.id, { effects: [...new Set([...selectedLayer.effects, preset.effect])], effectStrengths: { ...selectedLayer.effectStrengths, [preset.effect]: preset.strength }, presetName: preset.title });
            setEffectBrowserOpen(false); notify(`${preset.title} applied`);
          }}
          onSelect={(effect) => {
            if (!selectedLayer) { notify('Select a layer before adding an effect'); return; }
            updateLayer(selectedLayer.id, { effects: [...new Set([...selectedLayer.effects, effect])], effectStrengths: { ...selectedLayer.effectStrengths, [effect]: selectedLayer.effectStrengths?.[effect] ?? 75 } });
            setEffectBrowserOpen(false);
            notify(`${effect} added`);
          }}
        />
      )}
      {exportOpen && (
        <ExportDialog
          project={currentProject}
          onClose={() => setExportOpen(false)}
          onPackage={() => { void exportProjectPackage(); setExportOpen(false); }}
          onPreview={() => { void exportPreview(); setExportOpen(false); }}
          renderFormat={exportFormat} onFormat={setExportFormat}
          renderQuality={exportQuality} onQuality={setExportQuality}
          onRender={(format, quality) => { void renderVideo(format, quality); }}
        />
      )}
      {renderProgress !== null && <RenderProgressDialog progress={renderProgress} projectName={currentProject?.name ?? 'Untitled project'} />}
      {renderedVideoUrl && currentProject && <RenderedVideoDialog src={renderedVideoUrl} name={currentProject.name} format={renderedVideoType.includes('mp4') ? 'MP4' : 'WebM'} onClose={() => setRenderedVideoUrl(null)} onDownload={downloadRenderedVideo} />}
      {qrOpen && (
        <div className="modal-backdrop" onMouseDown={() => setQrOpen(false)}>
          <div className="qr-dialog" onMouseDown={(event) => event.stopPropagation()}>
            <button className="icon-button close-dialog" onClick={() => setQrOpen(false)} aria-label="Close"><X size={21} /></button>
            <div className="qr-placeholder"><ScanLine size={42} /><span>Scan a project QR</span></div>
            <p>Choose a QR image from your device, or paste the project link in the import box.</p>
            <button className="primary-button" onClick={() => { setQrOpen(false); qrInputRef.current?.click(); }}>Choose QR image</button>
          </div>
        </div>
      )}
      {textModalOpen && (
        <div className="modal-backdrop" onMouseDown={() => setTextModalOpen(false)}>
          <div className="text-dialog" onMouseDown={(event) => event.stopPropagation()}>
            <div className="dialog-heading"><div><span className="eyebrow">NEW LAYER</span><h2>Add text</h2></div><button className="icon-button" onClick={() => setTextModalOpen(false)} aria-label="Close"><X size={20} /></button></div>
            <label className="field-label" htmlFor="text-layer-name">Text content</label>
            <textarea id="text-layer-name" value={textDraft} onChange={(event) => setTextDraft(event.target.value)} rows={3} autoFocus />
            <button className="primary-button" onClick={finishTextLayer}>Add to timeline</button>
          </div>
        </div>
      )}
      {renameLayer && selectedLayer && (
        <div className="modal-backdrop" onMouseDown={() => setRenameLayer(false)}>
          <div className="text-dialog compact-dialog" onMouseDown={(event) => event.stopPropagation()}>
            <div className="dialog-heading"><h2>Rename layer</h2><button className="icon-button" onClick={() => setRenameLayer(false)} aria-label="Close"><X size={20} /></button></div>
            <input className="text-input" value={selectedLayer.name} onChange={(event) => updateLayer(selectedLayer.id, { name: event.target.value })} />
            <button className="primary-button" onClick={() => setRenameLayer(false)}>Done</button>
          </div>
        </div>
      )}
      {toast && <div className="toast"><Check size={16} />{toast}</div>}
    </div>
  );
}

function AppHeader({ onSettings, onProfile }: { onSettings: () => void; onProfile: () => void }) {
  return (
    <header className="app-header">
      <button className="header-icon" onClick={onSettings} aria-label="Settings"><Settings size={24} strokeWidth={1.8} /></button>
      <div className="brand-lockup">
        <div className="brand-symbol"><span /><span /><span /></div>
        <div><strong>EDITORSHUB</strong><small>MOTION STUDIO</small></div>
      </div>
      <button className="profile-button" onClick={onProfile} aria-label="Profile"><UserRound size={21} strokeWidth={1.7} /></button>
    </header>
  );
}

function HomeScreen({
  onUploadXml, packageUrl, setPackageUrl, onImport, onQr, onOpenProject,
}: {
  onUploadXml: () => void; packageUrl: string; setPackageUrl: (value: string) => void;
  onImport: () => void; onQr: () => void; onOpenProject: () => void;
}) {
  return (
    <div className="home-screen">
      <section className="welcome-row">
        <div><span className="eyebrow">YOUR CREATIVE SPACE</span><h1>Make every frame<br /><em>feel intentional.</em></h1></div>
        <button className="welcome-create" onClick={onOpenProject}><Play size={16} fill="currentColor" /> Open a demo</button>
      </section>
      <button className="import-card upload-card" onClick={onUploadXml}>
        <span className="import-icon upload-symbol"><Upload size={27} /></span>
        <span className="import-copy"><strong>Upload XML file</strong><small>Bring a project timeline into EditorsHub</small></span>
        <span className="import-button">Choose file <ArrowDownToLine size={17} /></span>
      </button>
      <section className="import-card package-card">
        <div className="package-heading">
          <span className="import-icon package-symbol"><Layers size={26} /></span>
          <div><strong>Import project package</strong><small>Paste a link or scan a QR code.</small></div>
        </div>
        <div className="package-input-row">
          <input aria-label="Project package link" placeholder="https://editorshub.app/share/..." value={packageUrl} onChange={(event) => setPackageUrl(event.target.value)} />
          <button className="package-scan" onClick={onQr} title="Scan QR"><ScanLine size={20} /></button>
        </div>
        <div className="package-actions">
          <button className="primary-button" onClick={onImport}>Import package <ChevronRight size={18} /></button>
          <button className="scan-button" onClick={onQr}><ScanLine size={18} /> Scan QR image</button>
        </div>
      </section>
      <div className="home-footnote"><Sparkles size={15} /><span>Built for the stories you want to tell.</span><span className="footnote-line" /></div>
    </div>
  );
}

function BottomNavigation({ screen, onHome, onLibrary, onCreate }: { screen: Screen; onHome: () => void; onLibrary: () => void; onCreate: () => void }) {
  return (
    <nav className="bottom-nav" aria-label="Main navigation">
      <button className={screen === 'home' ? 'nav-item active' : 'nav-item'} onClick={onHome}><Home size={21} /><span>Home</span></button>
      <button className="nav-create" onClick={onCreate} aria-label="Create"><Plus size={31} strokeWidth={1.7} /></button>
      <button className={screen === 'library' ? 'nav-item active' : 'nav-item'} onClick={onLibrary}><FolderOpen size={21} /><span>Projects</span></button>
    </nav>
  );
}

function LibraryScreen({
  projects, tab, onTab, query, onQuery, sortBy, onSort, onOpen, onCreate, onDelete, onDuplicate,
}: {
  projects: Project[]; tab: ProjectKind; onTab: (tab: ProjectKind) => void; query: string; onQuery: (query: string) => void;
  sortBy: 'Date' | 'Name' | 'Duration'; onSort: () => void; onOpen: (project: Project) => void; onCreate: () => void;
  onDelete: (project: Project) => void; onDuplicate: (project: Project) => void;
}) {
  const [menuId, setMenuId] = useState<string | null>(null);
  const tabs: { key: ProjectKind; label: string }[] = [
    { key: 'project', label: 'Projects' }, { key: 'template', label: 'Your templates' }, { key: 'element', label: 'Elements' },
  ];
  const searchLabel = tab === 'project' ? 'Search projects' : tab === 'template' ? 'Search templates' : 'Search elements';
  return (
    <div className="library-screen">
      <div className="segmented-control library-tabs">
        {tabs.map((item) => <button key={item.key} className={tab === item.key ? 'selected' : ''} onClick={() => onTab(item.key)}>{item.label}</button>)}
      </div>
      <div className="search-field"><Search size={19} /><input value={query} onChange={(event) => onQuery(event.target.value)} placeholder={searchLabel} aria-label={searchLabel} /><button className="search-filter" aria-label="Search filters"><SlidersHorizontal size={17} /></button></div>
      <div className="list-heading"><div><span className="eyebrow">LIBRARY</span><h1>{tab === 'project' ? 'Your projects' : tab === 'template' ? 'Your templates' : 'Your elements'}</h1></div><button className="sort-button" onClick={onSort}>Sort by {sortBy}<ChevronDown size={17} /></button></div>
      {projects.length ? (
        <div className="project-grid">
          {projects.map((project, index) => (
            <article className="project-card" key={project.id}>
              <button className="project-thumb" onClick={() => onOpen(project)} aria-label={`Open ${project.name}`}>
                {project.thumbnail ? <img src={project.thumbnail} alt="" /> : <div className={`thumb-placeholder thumb-${index % 4}`}><Film size={25} /></div>}
                <span className="duration-pill">{fmtTime(project.duration, project.fps)}</span>
                <span className="thumb-kind">{project.kind === 'template' ? 'TEMPLATE' : project.layers?.some((layer) => layer.kind === 'image') ? 'EDIT' : 'PROJECT'}</span>
              </button>
              <div className="project-details">
                <div className="project-title-line"><button className="project-title" onClick={() => onOpen(project)}>{project.name}</button><div className="card-menu-wrap"><button className="card-menu-button" aria-label={`Actions for ${project.name}`} onClick={() => setMenuId(menuId === project.id ? null : project.id)}><Ellipsis size={19} /></button>{menuId === project.id && <div className="card-menu"><button onClick={() => { onDuplicate(project); setMenuId(null); }}><Copy size={15} /> Duplicate</button><button onClick={() => { onDelete(project); setMenuId(null); }}><Trash2 size={15} /> Delete</button></div>}</div></div>
                <div className="project-meta"><span><Crop size={14} />{project.ratio}</span><span><SquareDashed size={14} />{project.resolution}</span><span><AudioLines size={14} />{project.fps} fps</span></div>
                <div className="project-meta secondary-meta"><span><Clock3 size={14} />{project.updatedAt}</span><span>{project.width} × {project.height}</span></div>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-library">
          <div className="empty-orbit"><div className="orbit-dot" /><Layers size={29} /></div>
          <h2>No {tab === 'element' ? 'elements' : tab === 'template' ? 'templates' : 'projects'} yet.</h2>
          <p>{tab === 'element' ? 'Save a layer as an element, then reuse it in any edit.' : tab === 'template' ? 'Turn a project into a template for your next story.' : 'Start with a blank canvas or bring in your own media.'}</p>
          <button className="primary-button" onClick={onCreate}><Plus size={18} /> Create {tab === 'project' ? 'project' : tab}</button>
        </div>
      )}
    </div>
  );
}

function SettingsDialog({
  lowQuality, onLowQuality, showTimeline, onShowTimeline, rememberAdd, onRememberAdd, duration, onDuration,
  resampling, onResampling, onClose, onNotify,
}: {
  lowQuality: boolean; onLowQuality: (value: boolean) => void; showTimeline: boolean; onShowTimeline: (value: boolean) => void;
  rememberAdd: boolean; onRememberAdd: (value: boolean) => void; duration: string; onDuration: (value: string) => void;
  resampling: string; onResampling: (value: string) => void; onClose: () => void; onNotify: (message: string) => void;
}) {
  return (
    <div className="modal-backdrop settings-backdrop" onMouseDown={onClose}>
      <section className="settings-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">EDITOR PREFERENCES</span><h2>Settings</h2></div><button className="icon-button" onClick={onClose} aria-label="Close settings"><X size={21} /></button></div>
        <div className="settings-list">
          <SettingToggle label="Performance preview" note="Lower 3D preview raster and simplify display; exports stay full quality" checked={lowQuality} onChange={onLowQuality} />
          <SettingToggle label="Show off timeline" note="Keep the timeline visible while browsing" checked={showTimeline} onChange={onShowTimeline} />
          <SettingToggle label="Remember Add page" note="Reopen the last add panel" checked={rememberAdd} onChange={onRememberAdd} />
          <div className="setting-row"><div><strong>Default layer duration</strong><small>For new text, shapes and null layers</small></div><select value={duration} onChange={(event) => onDuration(event.target.value)}><option>2s</option><option>5s</option><option>10s</option><option>15s</option></select></div>
          <div className="setting-row audio-setting"><div><strong>Audio resampling</strong><small>Higher quality may take longer to export</small></div><div className="segmented-control three-way">{['Low', 'High', 'Ultra'].map((level) => <button key={level} className={resampling === level ? 'selected' : ''} onClick={() => onResampling(level)}>{level}</button>)}</div></div>
        </div>
        <div className="settings-brand"><div className="mini-brand-mark"><span /><span /><span /></div><strong>EDITORSHUB</strong><small>MAKE SOMETHING MOVE.</small></div>
        <div className="social-links">
          {[
            { label: 'Website', icon: '◎', tone: 'website' }, { label: 'Community', icon: '↗', tone: 'community' }, { label: 'About EditorsHub', icon: '✳', tone: 'about' },
          ].map((item) => <button key={item.label} className="social-row" onClick={() => { if (item.label === 'About EditorsHub') onNotify('EditorsHub Motion Studio · Version 1.0 preview'); else onNotify(`${item.label} link will be available soon`); }}><span className={`social-icon ${item.tone}`}>{item.icon}</span><span>{item.label}</span><ChevronRight size={18} /></button>)}
        </div>
        <div className="settings-footer"><span>EditorsHub Motion Studio</span><span>v1.0 · Preview</span></div>
      </section>
    </div>
  );
}

function SettingToggle({ label, note, checked, onChange }: { label: string; note: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <div className="setting-row"><div><strong>{label}</strong><small>{note}</small></div><button className={`switch ${checked ? 'on' : ''}`} role="switch" aria-checked={checked} onClick={() => onChange(!checked)}><span /></button></div>;
}

function ProjectSettingsDialog({ project, onClose, onSave }: { project: Project; onClose: () => void; onSave: (patch: Partial<Project>) => void }) {
  const [name, setName] = useState(project.name);
  const [ratio, setRatio] = useState<ProjectRatio>(project.ratio);
  const [width, setWidth] = useState(project.width);
  const [height, setHeight] = useState(project.height);
  const [fps, setFps] = useState(project.fps);
  const [duration, setDuration] = useState(project.duration);
  const [background, setBackground] = useState(project.background);
  const [resolution, setResolution] = useState(project.resolution);
  const updateOutputResolution = (preset: string) => {
    setResolution(preset);
    if (preset === 'Custom') return;
    const baseSize: [number, number] = ratio === 'Custom'
      ? (width >= height ? [1440, Math.round(1440 * height / Math.max(width, 1))] : [Math.round(1080 * width / Math.max(height, 1)), 1080])
      : ratioDimensions[ratio];
    const factor = Math.max(.25, resolutionScale(preset, baseSize[0], baseSize[1]));
    setWidth(Math.max(16, Math.round(baseSize[0] * factor))); setHeight(Math.max(16, Math.round(baseSize[1] * factor)));
  };
  const updateRatio = (nextRatio: ProjectRatio) => {
    setRatio(nextRatio);
    if (nextRatio !== 'Custom') {
      const [baseWidth, baseHeight] = ratioDimensions[nextRatio];
      const scale = Math.max(.25, resolutionScale(resolution, baseWidth, baseHeight));
      setWidth(Math.round(baseWidth * scale));
      setHeight(Math.round(baseHeight * scale));
    }
  };
  const apply = () => onSave({
    name: name.trim() || 'Untitled project', ratio, width: Math.min(7680, Math.max(16, Math.round(width))), height: Math.min(7680, Math.max(16, Math.round(height))),
    fps: Math.max(1, Math.min(99, fps)), duration: Math.min(600, Math.max(.1, duration)), background, resolution: resolution === 'Custom' ? 'Custom' : /2k/i.test(resolution) ? '2K' : `${Number.parseInt(resolution, 10) || 1080}p`,
  });
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="project-settings-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">COMPOSITION</span><h2>Project settings</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={20} /></button></div>
        <label className="project-setting-field">Project name<input value={name} maxLength={64} onChange={(event) => setName(event.target.value)} /></label>
        <label className="project-setting-field">Aspect ratio<select value={ratio} onChange={(event) => updateRatio(event.target.value as ProjectRatio)}>{ratios.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label className="project-setting-field">Output resolution<select value={resolution === '2K' ? '2K (2048px long edge)' : resolution === '2160p' || resolution === '4K' ? '2160p (4K)' : resolution === '1440p' ? '1440p (QHD)' : resolution === '720p' ? '720p (HD)' : resolution === '1080p' ? '1080p (FHD)' : resolution} onChange={(event) => updateOutputResolution(event.target.value)}><option>720p (HD)</option><option>1080p (FHD)</option><option>1440p (QHD)</option><option>2K (2048px long edge)</option><option>2160p (4K)</option><option>Custom</option></select></label>
        <div className="project-dimensions"><label className="project-setting-field">Width<input type="number" min={16} max={7680} value={width} onChange={(event) => { setWidth(Number(event.target.value)); setRatio('Custom'); setResolution('Custom'); }} /></label><span>×</span><label className="project-setting-field">Height<input type="number" min={16} max={7680} value={height} onChange={(event) => { setHeight(Number(event.target.value)); setRatio('Custom'); setResolution('Custom'); }} /></label></div>
        <div className="project-settings-row"><label className="project-setting-field">Frame rate (1–99 fps)<input type="number" min={1} max={99} step={1} value={fps} onChange={(event) => setFps(Number(event.target.value))} /></label><label className="project-setting-field">Duration (s)<input type="number" min={.1} max={600} step={.1} value={duration} onChange={(event) => setDuration(Number(event.target.value))} /></label></div>
        <div className="project-settings-row"><label className="project-setting-field">Background color</label><label className="project-background-picker"><span style={{ background }} /><input type="color" value={background} onChange={(event) => setBackground(event.target.value)} /><span>{background.toUpperCase()}</span></label></div>
        <div className="project-settings-actions"><button className="secondary-button" onClick={onClose}>Cancel</button><button className="primary-button" onClick={apply}>Save settings</button></div>
      </section>
    </div>
  );
}

function NewProjectSheet({
  kind, onKind, name, onName, ratio, onRatio, resolution, onResolution, fps, onFps, background, onBackground,
  customRatio, onCustomRatio, onClose, onCreate,
}: {
  kind: ProjectKind; onKind: (value: ProjectKind) => void; name: string; onName: (value: string) => void;
  ratio: ProjectRatio; onRatio: (value: ProjectRatio) => void; resolution: string; onResolution: (value: string) => void;
  fps: number; onFps: (value: number) => void; background: string; onBackground: (value: string) => void;
  customRatio: string; onCustomRatio: (value: string) => void; onClose: () => void; onCreate: () => void;
}) {
  const customParts = customRatio.split(':').map((part) => Number.parseFloat(part));
  const baseSize: [number, number] = ratio === 'Custom'
    ? (customParts[0] >= customParts[1] ? [1440, Math.round(1440 * (customParts[1] || 2) / (customParts[0] || 3))] : [Math.round(1080 * (customParts[0] || 3) / (customParts[1] || 2)), 1080])
    : ratioDimensions[ratio];
  const sizeScale = resolutionScale(resolution, baseSize[0], baseSize[1]);
  const sizeLabel = `${Math.round(baseSize[0] * sizeScale)} × ${Math.round(baseSize[1] * sizeScale)}${/2k/i.test(resolution) ? ' · 2K' : ''}`;
  return (
    <div className="modal-backdrop sheet-backdrop" onMouseDown={onClose}>
      <section className="new-project-sheet" onMouseDown={(event) => event.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="segmented-control create-tabs"><button className={kind !== 'element' ? 'selected' : ''} onClick={() => onKind('project')}>Project</button><button className={kind === 'element' ? 'selected' : ''} onClick={() => onKind('element')}>Element</button></div>
        <div className="new-name-row"><input value={name} onChange={(event) => onName(event.target.value)} aria-label="Name" /><button className="icon-button" onClick={() => onName('')} aria-label="Clear name"><X size={19} /></button></div>
        <div className="ratio-grid" aria-label="Aspect ratio">
          {ratios.map((item) => <button key={item} className={`ratio-option ${ratio === item ? 'selected' : ''}`} onClick={() => onRatio(item)}><span className={`ratio-outline ratio-${item.replace(':', '-')}`} />{item === 'Custom' ? <Pencil size={16} /> : <small>{item}</small>}</button>)}
        </div>
        {ratio === 'Custom' && <div className="custom-ratio-fields"><input value={customRatio.split(':')[0]} onChange={(event) => onCustomRatio(`${event.target.value}:${customRatio.split(':')[1] ?? '2'}`)} aria-label="Ratio width" /><span>:</span><input value={customRatio.split(':')[1] ?? '2'} onChange={(event) => onCustomRatio(`${customRatio.split(':')[0] ?? '3'}:${event.target.value}`)} aria-label="Ratio height" /></div>}
        <div className="creation-settings">
          <div className="creation-label"><strong>Resolution</strong><small>Composition size<br />{sizeLabel}</small></div>
          <select value={resolution} onChange={(event) => onResolution(event.target.value)} aria-label="Resolution"><option>720p (HD)</option><option>1080p (FHD)</option><option>1440p (QHD)</option><option>2K (2048px long edge)</option><option>2160p (4K)</option></select>
        </div>
        <div className="creation-settings compact-creation"><div className="creation-label"><strong>Frame rate</strong><small>Manual · 1–99 fps</small></div><input className="fps-input" aria-label="Frame rate" type="number" min={1} max={99} step={1} value={fps} onChange={(event) => onFps(Number(event.target.value))} /></div>
        <div className={`creation-settings compact-creation ${kind === 'element' ? 'disabled-setting' : ''}`}><div className="creation-label"><strong>Background</strong></div><div className="background-select"><span style={{ background: background }} /><select disabled={kind === 'element'} value={background} onChange={(event) => onBackground(event.target.value)} aria-label="Background">{colorSwatches.map((color) => <option key={color} value={color}>{color === '#0b0c0e' ? 'Black' : color === '#fff4dd' ? 'White' : color === '#d9d1c3' ? 'Light grey' : 'Warm color'}</option>)}</select><ChevronDown size={17} /></div></div>
        <button className="primary-button create-project-button" onClick={onCreate}>Create {kind === 'element' ? 'Element' : kind === 'template' ? 'Template' : 'Project'} <ChevronRight size={18} /></button>
      </section>
    </div>
  );
}

function AddSheet({ onClose, onMedia, onText, onShape, onAudio, onAction }: {
  onClose: () => void; onMedia: () => void; onText: () => void; onShape: () => void; onAudio: () => void; onAction: (name: string) => void;
}) {
  const cells = [
    { label: 'Media', icon: ImageIcon, action: onMedia, subtitle: 'Photo or video' },
    { label: 'Text', icon: Type, action: onText, subtitle: 'Titles & captions' },
    { label: 'Shape', icon: Shapes, action: onShape, subtitle: 'Vector shapes' },
    { label: 'Audio', icon: Music2, action: onAudio, subtitle: 'Music & sound' },
    { label: 'Freehand drawing', icon: Pencil, action: () => onAction('Freehand Drawing'), subtitle: 'Sketch a path' },
    { label: 'Vector drawing', icon: Move, action: () => onAction('Vector Drawing'), subtitle: 'Pen tool' },
    { label: '3D object', icon: Box, action: () => onAction('3D Object'), subtitle: 'Build a 3D primitive' },
    { label: 'Element / Project', icon: Layers, action: () => onAction('Element / Project'), subtitle: 'Reuse a layer' },
    { label: 'Template', icon: Grid2X2, action: () => onAction('Template'), subtitle: 'Start from a design' },
  ];
  return (
    <div className="modal-backdrop sheet-backdrop" onMouseDown={onClose}>
      <section className="add-sheet" onMouseDown={(event) => event.stopPropagation()}>
        <div className="sheet-handle" /><div className="sheet-title-line"><div><span className="eyebrow">BUILD YOUR SCENE</span><h2>Add to project</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={20} /></button></div>
        <div className="add-grid">{cells.map(({ label, icon: Icon, action, subtitle }) => <button className="add-cell" key={label} onClick={action}><span className="add-cell-icon"><Icon size={24} strokeWidth={1.65} /></span><strong>{label}</strong><small>{subtitle}</small>{['Media', 'Shape', 'Audio', 'Template'].includes(label) && <ChevronRight className="add-cell-corner" size={15} />}</button>)}</div>
        <div className="add-quick-row"><button onClick={() => onAction('Camera')}><Video size={18} /> Camera</button><button onClick={() => onAction('Null')}><SquareDashed size={18} /> Null</button><button onClick={() => onAction('Empty Group')}><Layers size={18} /> Empty group</button></div>
      </section>
    </div>
  );
}

function DrawingDialog({ mode, projectWidth, onClose, onSave }: { mode: 'freehand' | 'vector'; projectWidth: number; onClose: () => void; onSave: (points: DrawPoint[], color: string, brushSize: number) => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pointsRef = useRef<DrawPoint[]>([]);
  const activePointerRef = useRef<number | null>(null);
  const [points, setPoints] = useState<DrawPoint[]>([]);
  const [color, setColor] = useState('#e5b653');
  const [brushSize, setBrushSize] = useState(8);
  const [drawing, setDrawing] = useState(false);
  const setPath = (next: DrawPoint[]) => { pointsRef.current = next; setPoints(next); };
  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) return;
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = '#111116';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = 'rgba(255,255,255,.035)';
    context.lineWidth = 1;
    for (let x = 0; x <= canvas.width; x += 60) { context.beginPath(); context.moveTo(x, 0); context.lineTo(x, canvas.height); context.stroke(); }
    for (let y = 0; y <= canvas.height; y += 60) { context.beginPath(); context.moveTo(0, y); context.lineTo(canvas.width, y); context.stroke(); }
    if (!points.length) return;
    context.strokeStyle = color;
    context.fillStyle = color;
    context.lineWidth = brushSize * canvas.width / Math.max(1, projectWidth);
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.beginPath();
    points.forEach((point, index) => { const x = point.x / 100 * canvas.width; const y = point.y / 100 * canvas.height; if (index === 0) context.moveTo(x, y); else context.lineTo(x, y); });
    if (points.length === 1) { context.arc(points[0].x / 100 * canvas.width, points[0].y / 100 * canvas.height, context.lineWidth / 2, 0, Math.PI * 2); context.fill(); }
    else context.stroke();
  }, [points, color, brushSize]);
  const pointFromEvent = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    return { x: Math.max(0, Math.min(100, (event.clientX - bounds.left) / bounds.width * 100)), y: Math.max(0, Math.min(100, (event.clientY - bounds.top) / bounds.height * 100)) };
  };
  const pointerDown = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    activePointerRef.current = event.pointerId;
    const point = pointFromEvent(event);
    if (mode === 'vector') setPath([...pointsRef.current, point]);
    else { setPath([point]); setDrawing(true); }
  };
  const pointerMove = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (mode !== 'freehand' || !drawing || activePointerRef.current !== event.pointerId) return;
    const point = pointFromEvent(event);
    const previous = pointsRef.current.at(-1);
    if (!previous || Math.hypot(point.x - previous.x, point.y - previous.y) > .35) setPath([...pointsRef.current, point]);
  };
  const pointerUp = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (activePointerRef.current !== event.pointerId) return;
    activePointerRef.current = null;
    setDrawing(false);
  };
  const undo = () => mode === 'vector' ? setPath(pointsRef.current.slice(0, -1)) : setPath([]);
  return (
    <div className="modal-backdrop sheet-backdrop" onMouseDown={onClose}>
      <section className="drawing-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="dialog-heading"><div><span className="eyebrow">{mode === 'freehand' ? 'FREEHAND TOOL' : 'VECTOR TOOL'}</span><h2>{mode === 'freehand' ? 'Draw on canvas' : 'Build a vector path'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Close drawing tool"><X size={20} /></button></div>
        <div className="drawing-canvas-wrap"><canvas ref={canvasRef} width={720} height={1280} className="drawing-canvas" onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp} onPointerCancel={pointerUp} aria-label="Drawing canvas" /></div>
        <div className="drawing-tools"><label className="drawing-color">Color<input type="color" value={color} onChange={(event) => setColor(event.target.value)} /></label><label className="drawing-size">Brush {brushSize}px<input type="range" min={1} max={32} value={brushSize} onChange={(event) => setBrushSize(Number(event.target.value))} /></label><button className="secondary-button" onClick={undo}>{mode === 'vector' ? 'Undo point' : 'Clear'}</button></div>
        <p className="drawing-hint">{mode === 'vector' ? 'Tap to place points. EditorsHub connects them into a smooth stroke.' : 'Draw with touch, a mouse, or a stylus.'}</p>
        <button className="primary-button drawing-save" disabled={!points.length} onClick={() => onSave(pointsRef.current, color, brushSize)}><Check size={17} /> Add drawing layer</button>
      </section>
    </div>
  );
}

function ShapeSheet({ onClose, onSelect }: { onClose: () => void; onSelect: (shape: string) => void }) {
  const shapes = [
    { name: 'Circle', icon: Circle }, { name: 'Rounded Rectangle', icon: Square }, { name: 'Triangle', icon: Triangle },
    { name: 'Star', icon: Sparkles }, { name: 'Polygon', icon: Shapes }, { name: 'Line', icon: SlidersHorizontal },
    { name: 'Arrow', icon: Move }, { name: 'Ring', icon: Circle }, { name: 'Speech bubble', icon: Type },
    { name: 'Hexagon', icon: Shapes }, { name: 'Wave', icon: AudioLines }, { name: 'Freeform', icon: Pencil },
  ];
  return (
    <div className="modal-backdrop shape-sheet-backdrop" onMouseDown={onClose}>
      <section className="shape-sheet" onMouseDown={(event) => event.stopPropagation()}>
        <div className="sheet-handle" /><div className="sheet-title-line"><button className="back-link" onClick={onClose}><ArrowLeft size={20} /></button><h2>Choose a shape</h2><span className="shape-count">12</span></div>
        <div className="shape-grid">{shapes.map(({ name, icon: Icon }) => <button key={name} onClick={() => onSelect(name)}><span><Icon size={32} fill={name === 'Line' ? 'none' : 'currentColor'} strokeWidth={name === 'Circle' || name === 'Triangle' ? 1.4 : 1.7} /></span><small>{name}</small></button>)}</div>
      </section>
    </div>
  );
}

function EffectBrowser({ query, onQuery, category, onCategory, onClose, onSelect, onSelectPreset }: {
  query: string; onQuery: (value: string) => void; category: string | null; onCategory: (value: string | null) => void;
  onClose: () => void; onSelect: (effect: string) => void; onSelectPreset: (preset: (typeof effectPresets)[number]) => void;
}) {
  const [catalogTab, setCatalogTab] = useState<'effects' | 'presets'>('effects');
  const [presetPage, setPresetPage] = useState(0);
  const filteredCategories = initialEffects.map((item) => ({ ...item, effects: item.effects.filter((effect) => effect.toLowerCase().includes(query.toLowerCase())) })).filter((item) => (!category || item.title === category) && (query ? item.effects.length > 0 : true));
  const filteredPresets = effectPresets.filter((preset) => `${preset.title} ${preset.effect}`.toLowerCase().includes(query.toLowerCase()));
  const pageSize = 48;
  const visiblePresets = filteredPresets.slice(presetPage * pageSize, (presetPage + 1) * pageSize);
  return (
    <div className="effect-browser-overlay">
      <header className="effect-browser-header"><button className="icon-button" onClick={onClose} aria-label="Close effect browser"><X size={24} /></button><div><span className="eyebrow">EDITORS HUB LIBRARY</span><h2>{catalogTab === 'presets' ? 'Look presets' : category ?? 'Effect browser'}</h2></div><label className="effect-search"><Search size={19} /><input value={query} onChange={(event) => { onQuery(event.target.value); setPresetPage(0); }} placeholder={catalogTab === 'presets' ? 'Search 2,700 presets' : 'Search effects'} /></label></header>
      <main className="effect-browser-content">
        <div className="effect-catalog-tabs"><button className={catalogTab === 'effects' ? 'selected' : ''} onClick={() => setCatalogTab('effects')}>Effects</button><button className={catalogTab === 'presets' ? 'selected' : ''} onClick={() => { setCatalogTab('presets'); onCategory(null); }}>Presets <span>2,700</span></button></div>
        {catalogTab === 'effects' ? <>
          {category && <button className="effect-back" onClick={() => onCategory(null)}><ArrowLeft size={16} /> All categories</button>}
          <div className="effect-category-grid">{filteredCategories.map((item) => (
            <section className={`effect-category ${item.tint}`} key={item.title}>
              {!category && <button className="effect-category-title" onClick={() => onCategory(item.title)}><span>{item.title}</span><ChevronRight size={16} /></button>}
              {category && <h3>{item.title}</h3>}
              {(category || query) && <div className="effect-list">{item.effects.map((effect, effectIndex) => <button key={effect} onClick={() => onSelect(effect)}><span className="effect-mini-symbol">{effectIndex % 2 === 0 ? <Sparkles size={16} /> : <Zap size={16} />}</span><span>{effect}</span><Plus size={16} /></button>)}</div>}
            </section>
          ))}</div>
        </> : <>
          <div className="preset-catalog-heading"><span>{filteredPresets.length.toLocaleString()} looks</span><small>27 effect recipes × 100 intensity variants · effects remain editable</small></div>
          <div className="preset-grid">{visiblePresets.map((preset, index) => <button className={`preset-card preset-tone-${index % 6}`} key={preset.id} onClick={() => onSelectPreset(preset)}><span className="preset-card-icon"><Sparkles size={17} /></span><span><strong>{preset.title}</strong><small>{preset.effect} · {preset.strength}%</small></span><Plus size={15} /></button>)}</div>
          {visiblePresets.length < filteredPresets.length && <button className="preset-load-more" onClick={() => setPresetPage((page) => page + 1)}>Show next {Math.min(pageSize, filteredPresets.length - visiblePresets.length)} presets</button>}
          {!filteredPresets.length && <p className="preset-empty">No presets match that search.</p>}
        </>}
      </main>
    </div>
  );
}

function ExportDialog({ project, onClose, onPackage, onPreview, onRender, renderFormat, onFormat, renderQuality, onQuality }: {
  project: Project | null; onClose: () => void; onPackage: () => void; onPreview: () => void; onRender: (format: ExportFormat, quality: ExportQuality) => void;
  renderFormat: ExportFormat; onFormat: (format: ExportFormat) => void; renderQuality: ExportQuality; onQuality: (quality: ExportQuality) => void;
}) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="export-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">DELIVER YOUR WORK</span><h2>Export</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={21} /></button></div>
        <div className="export-summary"><div className="export-summary-icon"><Film size={22} /></div><div><strong>{project?.name ?? 'Untitled project'}</strong><small>{project?.width} × {project?.height} · {project?.fps} fps · {fmtTime(project?.duration ?? 0, project?.fps ?? 30)}</small></div></div>
        <div className="export-render-settings"><label>Format<select value={renderFormat} onChange={(event) => onFormat(event.target.value as ExportFormat)}><option value="auto">Best supported</option><option value="mp4">Prefer MP4</option><option value="webm">Prefer WebM</option></select></label><label>Quality<select value={renderQuality} onChange={(event) => onQuality(event.target.value as ExportQuality)}><option value="balanced">Balanced · up to 2K</option><option value="high">High · up to 2560px</option></select></label></div>
        <button className="export-option" onClick={onPackage}><span className="export-option-icon"><FilePlus2 size={21} /></span><span><strong>Save project package</strong><small>Editable timeline · .ehproj</small></span><ChevronRight size={18} /></button>
        <button className="export-option" onClick={onPreview}><span className="export-option-icon"><ImageIcon size={21} /></span><span><strong>Export frame as PNG</strong><small>Capture the current composition</small></span><ChevronRight size={18} /></button>
        <button className="export-option" onClick={() => onRender(renderFormat, renderQuality)}><span className="export-option-icon"><Video size={21} /></span><span><strong>Render video</strong><small>MP4 when supported · WebM fallback</small></span><ChevronRight size={18} /></button>
        <p className="export-note"><Info size={15} /> Video export runs on this device. Keep EditorsHub open until the render finishes.</p>
      </section>
    </div>
  );
}

function RenderProgressDialog({ progress, projectName }: { progress: number; projectName: string }) {
  return (
    <div className="modal-backdrop render-progress-backdrop">
      <section className="render-progress-dialog">
        <div className="rendering-emblem"><Film size={25} /></div>
        <span className="eyebrow">EXPORTING COMPOSITION</span>
        <h2>Rendering your video</h2>
        <p>{projectName}</p>
        <div className="render-progress-track"><span style={{ width: `${progress}%` }} /></div>
        <div className="render-progress-label"><span>{progress}%</span><span>Please keep this screen open</span></div>
      </section>
    </div>
  );
}

function RenderedVideoDialog({ src, name, format, onClose, onDownload }: { src: string; name: string; format: 'MP4' | 'WebM'; onClose: () => void; onDownload: () => void }) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="rendered-video-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">EXPORT COMPLETE</span><h2>Your video is ready</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={20} /></button></div>
        <video className="rendered-video-preview" src={src} controls playsInline />
        <p>{name}.{format.toLowerCase()} · rendered locally on this device</p>
        <div className="rendered-video-actions"><button className="secondary-button" onClick={onClose}>Close</button><button className="primary-button" onClick={onDownload}><ArrowDownToLine size={17} /> Download {format}</button></div>
      </section>
    </div>
  );
}

function EditorScreen({
  project, layers, selectedLayerId, selectedLayerIds, selectedLayer, time, isPlaying, editorTool, stageStyle, formattedRatio, videoRef, canvasZoom, lowQualityPreview, onProjectSettings, onGroup, onUngroup,
  panelSlider, canUndo, canRedo, onUndo, onRedo, onBack, onPlay, onSeek, onSelectLayer, onAdd, onSetTool, onSetContext, onUpdateLayer, onDeleteLayer,
  onDuplicateLayer, onSaveElement, onSplit, onMoveLayer, onRelink, onKeyframe, onSetEasing, onSpeedValue, onSpeedKeyframe, onSpeedEasing, onAIUpscale, aiUpscaleBusy, aiUpscaleProgress, aiUpscaleStatus, onOpenEffects, onExport, onZoom, onPanelSlider, onRename, onNotify, contextLayerId,
}: {
  project: Project; layers: EditorLayer[]; selectedLayerId: string | null; selectedLayerIds: string[]; selectedLayer: EditorLayer | null; time: number; isPlaying: boolean; lowQualityPreview: boolean; onProjectSettings: () => void;
  onGroup: () => void; onUngroup: (id: string) => void;
  editorTool: string | null; stageStyle: CSSProperties; formattedRatio: string; videoRef: RefObject<HTMLVideoElement | null>; canvasZoom: number;
  panelSlider: number; canUndo: boolean; canRedo: boolean; onUndo: () => void; onRedo: () => void;
  onBack: () => void; onPlay: () => void; onSeek: (value: number) => void;
  onSelectLayer: (id: string | null, additive?: boolean) => void; onAdd: () => void; onSetTool: (tool: string) => void; onSetContext: (id: string | null) => void;
  onUpdateLayer: (id: string, patch: Partial<EditorLayer>) => void; onDeleteLayer: (id: string) => void;
  onDuplicateLayer: (layer: EditorLayer) => void; onSaveElement: (layer: EditorLayer) => void; onSplit: () => void;
  onMoveLayer: (id: string, direction: 'up' | 'down') => void; onRelink: (id: string) => void; onKeyframe: (layer: EditorLayer) => void; onSetEasing: (layer: EditorLayer, easing: EasingPreset) => void; onSpeedValue: (layer: EditorLayer, speed: number) => void; onSpeedKeyframe: (layer: EditorLayer) => void; onSpeedEasing: (layer: EditorLayer, easing: EasingPreset) => void; onAIUpscale: () => void; aiUpscaleBusy: boolean; aiUpscaleProgress: number | null; aiUpscaleStatus: string; onOpenEffects: () => void;
  onExport: () => void; onZoom: (value: number) => void; onPanelSlider: (value: number) => void; onRename: () => void; onNotify: (message: string) => void;
  contextLayerId: string | null;
}) {
  const [activeWorkspaceTab, setActiveWorkspaceTab] = useState<'timeline' | 'layers'>('timeline');
  const [dragPreview, setDragPreview] = useState<{ id: string; x: number; y: number; scale: number } | null>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const stageDragRef = useRef<StageDrag | null>(null);
  const audioRefs = useRef(new Map<string, HTMLAudioElement>());
  const visibleLayers = layers.filter((layer) => isLayerVisible(layer, layers) && layer.kind !== 'group' && time >= layer.start && time < layer.start + layer.duration);
  const stageLayers = [...visibleLayers].sort((a, b) => layers.indexOf(a) - layers.indexOf(b));
  const activeVideo = stageLayers.find((layer) => layer.kind === 'video' && layer.src);
  useEffect(() => {
    for (const layer of layers.filter((item) => item.kind === 'audio' && item.src)) {
      const audio = audioRefs.current.get(layer.id);
      if (!audio) continue;
      const active = isLayerVisible(layer, layers) && time >= layer.start && time < layer.start + layer.duration;
      const target = getLayerSourceTime(layer, time);
      audio.volume = Math.max(0, Math.min(1, (layer.volume ?? 100) / 100));
      audio.playbackRate = Math.max(.1, Math.min(8, getLayerSpeedAt(layer, time)));
      if (active && Number.isFinite(audio.duration) && target < audio.duration) {
        if (Math.abs(audio.currentTime - target) > .45) audio.currentTime = target;
        if (isPlaying && audio.paused) void audio.play().catch(() => undefined);
        if (!isPlaying && !audio.paused) audio.pause();
      } else if (!audio.paused) audio.pause();
    }
  }, [layers, time, isPlaying]);
  const stageRatio = project.ratio === 'Custom' ? formattedRatio : project.ratio;

  const beginStageDrag = (event: ReactPointerEvent<HTMLDivElement>, layer: EditorLayer, animated: ReturnType<typeof getLayerTransformAt>) => {
    if (event.button !== 0) return;
    const stageBounds = stageRef.current?.getBoundingClientRect();
    const elementBounds = event.currentTarget.getBoundingClientRect();
    if (!stageBounds?.width || !stageBounds.height) return;
    const resizing = Boolean((event.target as HTMLElement).closest('[data-resize-handle]'));
    const centerX = elementBounds.left + elementBounds.width / 2;
    const centerY = elementBounds.top + elementBounds.height / 2;
    const startDistance = Math.max(10, Math.hypot(event.clientX - centerX, event.clientY - centerY));
    stageDragRef.current = {
      id: layer.id, pointerId: event.pointerId, mode: resizing ? 'scale' : 'move',
      startX: event.clientX, startY: event.clientY, baseX: animated.x, baseY: animated.y, baseScale: animated.scale,
      currentX: animated.x, currentY: animated.y, currentScale: animated.scale,
      stageWidth: stageBounds.width, stageHeight: stageBounds.height, centerX, centerY, startDistance,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
    onSelectLayer(layer.id);
  };

  const moveStageDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = stageDragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (drag.mode === 'move') {
      drag.currentX = Math.max(0, Math.min(100, drag.baseX + (event.clientX - drag.startX) / drag.stageWidth * 100));
      drag.currentY = Math.max(0, Math.min(100, drag.baseY + (event.clientY - drag.startY) / drag.stageHeight * 100));
    } else {
      const distance = Math.hypot(event.clientX - drag.centerX, event.clientY - drag.centerY);
      drag.currentScale = Math.max(10, Math.min(600, drag.baseScale * distance / drag.startDistance));
    }
    setDragPreview({ id: drag.id, x: drag.currentX, y: drag.currentY, scale: drag.currentScale });
  };

  const finishStageDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = stageDragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (drag.mode === 'move') onUpdateLayer(drag.id, { x: drag.currentX, y: drag.currentY });
    else onUpdateLayer(drag.id, { scale: drag.currentScale });
    stageDragRef.current = null;
    setDragPreview(null);
  };

  const addEffect = () => {
    if (selectedLayer) onSetTool('Effects');
    else onNotify('Select a layer first');
  };

  return (
    <div className="editor-screen">
      <header className="editor-header">
        <button className="editor-back" onClick={onBack} aria-label="Back to projects"><ArrowLeft size={21} /></button>
        <button className="editor-project-name" onClick={onProjectSettings}><span className="eyebrow">EDITING</span><strong>{project.name}</strong><Pencil size={13} /></button>
        <div className="editor-header-actions"><button className="editor-head-tool" onClick={onProjectSettings} aria-label="Project settings"><Settings size={20} /></button><button className="editor-export-button" onClick={onExport}><ArrowDownToLine size={17} /><span>Export</span></button><button className="editor-more" onClick={() => onNotify('Saved locally')} aria-label="More options"><Ellipsis size={20} /></button></div>
      </header>

      <div className="editor-workspace">
        <section className="preview-column">
          <div className="stage-toolbar"><div className="stage-pill"><span className="live-dot" /> Preview <span className="stage-divider" /> {project.resolution} · {project.fps} fps</div><button className="zoom-chip" onClick={() => onZoom(canvasZoom >= 150 ? 75 : canvasZoom + 25)}><Maximize2 size={14} /> {canvasZoom}% <ChevronDown size={13} /></button></div>
          <div className="preview-stage-wrap">
            <div ref={stageRef} className={`composition-stage ${lowQualityPreview ? 'preview-low-quality' : ''}`} style={{ ...stageStyle, transform: `scale(${Math.min(canvasZoom, 125) / 100})` }}>
              <div className="composition-grid" />
              {stageLayers.map((layer) => {
                const baseAnimated = getLayerTransformAt(layer, time);
                const animated = dragPreview?.id === layer.id ? { ...baseAnimated, x: dragPreview.x, y: dragPreview.y, scale: dragPreview.scale } : baseAnimated;
                const layerStyle: CSSProperties = { left: `${animated.x}%`, top: `${animated.y}%`, transform: `translate(-50%, -50%) translateZ(${animated.depth}px) rotate(${animated.rotation}deg) scale(${animated.scale / 100})`, opacity: animated.opacity / 100 };
                if (layer.kind === '3d') return <div className={`stage-3d-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={layerStyle} key={layer.id} onClick={() => onSelectLayer(layer.id)} onPointerDown={(event) => beginStageDrag(event, layer, animated)} onPointerMove={moveStageDrag} onPointerUp={finishStageDrag} onPointerCancel={finishStageDrag}><StageMeshCanvas layer={layer} transform={animated} lowQuality={lowQualityPreview} />{selectedLayerId === layer.id && <SelectionHandles />}</div>;
                if ((layer.kind === 'image' || layer.kind === 'video') && layer.src) {
                  return <div className={`stage-media-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={layerStyle} key={layer.id} onClick={() => onSelectLayer(layer.id)} onPointerDown={(event) => beginStageDrag(event, layer, animated)} onPointerMove={moveStageDrag} onPointerUp={finishStageDrag} onPointerCancel={finishStageDrag}>
                    {layer.kind === 'video' ? <video ref={layer.id === activeVideo?.id ? videoRef : undefined} src={layer.src} muted={!isPlaying} playsInline loop className="stage-media-content" style={{ filter: cssFilterForEffects(layer.effects, layer.effectStrengths), imageRendering: lowQualityPreview ? 'pixelated' : 'auto' }} /> : <img src={layer.src} alt={layer.name} className="stage-media-content" style={{ filter: `${cssFilterForEffects(layer.effects, layer.effectStrengths)}${(layer.restoreStrength ?? 0) > 0 ? ` contrast(${1 + (layer.restoreStrength ?? 0) / 500}) saturate(${1 + (layer.restoreStrength ?? 0) / 700})` : ''}`, imageRendering: lowQualityPreview ? 'pixelated' : 'auto' }} />}
                    {selectedLayerId === layer.id && <SelectionHandles />}
                  </div>;
                }
                if (layer.kind === 'text') return <div className={`stage-text-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={{ ...layerStyle, color: layer.color }} key={layer.id} onClick={() => onSelectLayer(layer.id)} onPointerDown={(event) => beginStageDrag(event, layer, animated)} onPointerMove={moveStageDrag} onPointerUp={finishStageDrag} onPointerCancel={finishStageDrag}>{layer.text}{selectedLayerId === layer.id && <SelectionHandles />}</div>;
                if (layer.kind === 'shape') return <div className={`stage-shape-layer shape-${(layer.shape ?? 'shape').toLowerCase().replace(/[^a-z]+/g, '-')} ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={{ ...layerStyle, background: layer.shape === 'Ring' ? 'transparent' : layer.color, border: layer.shape === 'Ring' ? `${Math.max(2, Math.round(Math.min(project.width, project.height) * .012))}px solid ${layer.color}` : undefined }} key={layer.id} onClick={() => onSelectLayer(layer.id)} onPointerDown={(event) => beginStageDrag(event, layer, animated)} onPointerMove={moveStageDrag} onPointerUp={finishStageDrag} onPointerCancel={finishStageDrag}>{selectedLayerId === layer.id && <SelectionHandles />}</div>;
                if (layer.kind === 'drawing' && layer.drawPoints?.length) {
                  const path = layer.drawPoints.map((point, pointIndex) => `${pointIndex === 0 ? 'M' : 'L'} ${point.x * project.width / 100} ${point.y * project.height / 100}`).join(' ');
                  return <div className={`stage-drawing-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={{ ...layerStyle, width: '100%', height: '100%' }} key={layer.id} onClick={() => onSelectLayer(layer.id)} onPointerDown={(event) => beginStageDrag(event, layer, animated)} onPointerMove={moveStageDrag} onPointerUp={finishStageDrag} onPointerCancel={finishStageDrag}><svg viewBox={`0 0 ${project.width} ${project.height}`} preserveAspectRatio="none"><path d={path} fill="none" stroke={layer.color} strokeWidth={layer.brushSize ?? 8} strokeLinecap="round" strokeLinejoin="round" /></svg>{selectedLayerId === layer.id && <SelectionHandles />}</div>;
                }
                return null;
              })}
              {!visibleLayers.some((layer) => layer.kind !== 'audio' && layer.kind !== 'null') && <div className="stage-empty"><div className="stage-empty-mark"><span /><span /><span /></div><strong>Your canvas, your rules.</strong><small>Add media, a title or a shape to get started.</small><button onClick={onAdd}><Plus size={17} /> Add your first layer</button></div>}
              {activeVideo && <div className="video-live-indicator"><span /> VIDEO PREVIEW</div>}
            </div>
          </div>
          <div className="stage-footer"><span><Smartphone size={14} /> {stageRatio} composition</span><span>{project.width} × {project.height}</span><button onClick={() => onNotify('Autosaved just now')}><span className="saved-dot" /> Saved</button></div>
        </section>

        <aside className="timeline-column">
          <div className="transport-row">
            <div className="transport-history"><button aria-label="Undo" disabled={!canUndo} onClick={onUndo}><Undo2 size={18} /></button><button aria-label="Redo" disabled={!canRedo} onClick={onRedo}><Redo2 size={18} /></button></div>
            <div className="transport-main"><button aria-label="Go to start" onClick={() => onSeek(0)}><ChevronLeft size={21} /><span className="transport-bar" /></button><button className="play-button" aria-label={isPlaying ? 'Pause' : 'Play'} onClick={onPlay}>{isPlaying ? <Pause size={19} fill="currentColor" /> : <Play size={18} fill="currentColor" />}</button><button aria-label="Go to end" onClick={() => onSeek(project.duration)}><ChevronRight size={21} /><span className="transport-bar" /></button></div>
            <div className="transport-actions"><button aria-label="Duplicate layer" onClick={() => { if (selectedLayer) onDuplicateLayer(selectedLayer); else onNotify('Select a layer to duplicate'); }}><Copy size={18} /></button><button aria-label="Fit preview" onClick={() => onZoom(100)}><Maximize2 size={18} /></button></div>
          </div>
          <div className="timeline-readout"><span className="timecode">{fmtTime(time, project.fps)}</span><span className="timeline-duration">{fmtTime(project.duration, project.fps)}</span></div>
          <div className="timeline-ruler"><div className="ruler-labels"><span>00:00</span><span>{fmtTime(project.duration / 2, project.fps).slice(0, 5)}</span><span>{fmtTime(project.duration, project.fps).slice(0, 5)}</span></div><div className="ruler-ticks" /></div>
          <div className="timeline-area">
            <div className="timeline-head-row"><div className="timeline-mode-tabs"><button className={activeWorkspaceTab === 'timeline' ? 'selected' : ''} onClick={() => setActiveWorkspaceTab('timeline')}>Timeline</button><button className={activeWorkspaceTab === 'layers' ? 'selected' : ''} onClick={() => setActiveWorkspaceTab('layers')}>Layers <span>{layers.length}</span></button></div><div className="timeline-head-actions">{selectedLayer?.kind === 'group' ? <button className="group-action" onClick={() => onUngroup(selectedLayer.id)}>Ungroup</button> : selectedLayerIds.length > 1 && <button className="group-action" onClick={onGroup}><Layers size={13} /> Group</button>}<button className="timeline-add" onClick={onAdd}><Plus size={17} /></button></div></div>
            {layers.length ? (
              <div className="track-list">
                {layers.map((layer, index) => (
                  <div className={`track-row ${layer.kind === 'group' ? 'track-group-row' : ''} ${layer.groupId ? 'track-child-row' : ''} ${selectedLayerIds.includes(layer.id) ? 'track-selected' : ''}`} key={layer.id} onClick={(event) => onSelectLayer(layer.id, event.shiftKey)}>
                    <div className="track-controls"><button className="track-eye" onClick={(event) => { event.stopPropagation(); onUpdateLayer(layer.id, { visible: !layer.visible }); }} aria-label={layer.visible ? 'Hide layer' : 'Show layer'}>{layer.visible ? <Eye size={14} /> : <EyeOff size={14} />}</button><span className="track-swatch" style={{ background: layer.color }}>{layer.kind === 'video' ? <Video size={12} /> : layer.kind === 'audio' ? <Music2 size={12} /> : layer.kind === 'text' ? <Type size={12} /> : layer.kind === 'image' ? <ImageIcon size={12} /> : layer.kind === 'drawing' ? <Pencil size={12} /> : layer.kind === '3d' ? <Box size={12} /> : layer.kind === 'group' ? <Layers size={12} /> : null}</span></div>
                    <div className={`track-clip clip-${index % 4}`} style={{ marginLeft: `${Math.min(75, layer.start / Math.max(project.duration, 1) * 100)}%`, width: `${Math.min(100, Math.max(12, layer.duration / Math.max(project.duration, 1) * 100))}%` }}>
                      {layer.src && layer.kind !== 'audio' && <span className="clip-thumbnail" style={{ backgroundImage: `url(${layer.src})` }} />}{layer.externalPath && <span className="missing-media-chip" title={`Relink media: ${layer.externalPath}`}><Link2 size={11} /></span>}
                      {layer.kind === 'audio' && <span className="waveform">{(layer.waveform?.length ? layer.waveform : [0.18,.34,.72,.4,.85,.48,.62,.26,.94,.39,.7,.25,.8,.48,.32,.72,.42,.9,.36,.62,.2,.74,.48,.82]).map((value, waveIndex) => <i key={waveIndex} style={{ height: `${Math.max(8, value * 100)}%` }} />)}</span>}
                      {layer.keyframes?.map((frame, frameIndex) => <span className="keyframe-diamond" key={`${layer.id}-key-${frameIndex}`} style={{ left: `${Math.max(0, Math.min(100, (frame.time - layer.start) / Math.max(layer.duration, .01) * 100))}%` }} />)}
                      {layer.speedKeyframes?.map((frame, frameIndex) => <span className="speed-keyframe-diamond" key={`${layer.id}-speed-${frameIndex}`} style={{ left: `${Math.max(0, Math.min(100, (frame.time - layer.start) / Math.max(layer.duration, .01) * 100))}%` }} />)}
                      <span className="clip-name">{layer.name}</span>
                      {layer.effects.length > 0 && <span className="clip-effects"><Sparkles size={11} />{layer.effects.length}</span>}
                    </div>
                    <button className="track-more" onClick={(event) => { event.stopPropagation(); onSetContext(contextLayerId === layer.id ? null : layer.id); }} aria-label="Layer actions"><Ellipsis size={17} /></button>
                    {contextLayerId === layer.id && <div className="track-menu">{layer.externalPath && <button onClick={(event) => { event.stopPropagation(); onRelink(layer.id); }}><Link2 size={14} /> Relink media</button>}{layer.kind === 'group' && <button onClick={(event) => { event.stopPropagation(); onUngroup(layer.id); onSetContext(null); }}><Layers size={14} /> Ungroup layers</button>}<button onClick={(event) => { event.stopPropagation(); onSelectLayer(layer.id); onSetContext(null); onRename(); }}><Pencil size={14} /> Rename layer</button><button onClick={(event) => { event.stopPropagation(); onSaveElement(layer); onSetContext(null); }}><Layers size={14} /> Save as element</button><button onClick={(event) => { event.stopPropagation(); onMoveLayer(layer.id, 'up'); onSetContext(null); }} disabled={index === layers.length - 1}><ArrowUp size={14} /> Move forward</button><button onClick={(event) => { event.stopPropagation(); onMoveLayer(layer.id, 'down'); onSetContext(null); }} disabled={index === 0}><ArrowDown size={14} /> Move backward</button><button onClick={(event) => { event.stopPropagation(); onDuplicateLayer(layer); onSetContext(null); }}><Copy size={14} /> Duplicate</button><button onClick={(event) => { event.stopPropagation(); onDeleteLayer(layer.id); }}><Trash2 size={14} /> Delete layer</button></div>}
                  </div>
                ))}
                <div className="playhead-line" style={{ left: `calc(${Math.min(1, time / Math.max(project.duration, 0.01)) * 100}% + ${120 * (1 - Math.min(1, time / Math.max(project.duration, 0.01)))}px)` }}><span /></div>
              </div>
            ) : (
              <button className="timeline-empty" onClick={onAdd}><div className="empty-track-icon"><AudioLines size={18} /></div><span><strong>Timeline is empty</strong><small>Tap + to add your first layer</small></span><Plus size={18} /></button>
            )}
          </div>
          <input className="timeline-scrubber" type="range" min={0} max={Math.max(project.duration, 0.1)} step={0.01} value={time} onChange={(event) => onSeek(Number(event.target.value))} aria-label="Timeline playhead" style={{ '--range-progress': `${time / Math.max(project.duration, 0.1) * 100}%` } as CSSProperties} />
          <div className="editor-tool-grid">
            <button className={editorTool === 'Speed' ? 'tool-active' : ''} onClick={() => onSetTool('Speed')}><span><Clock3 size={20} /></span><small>Speed</small></button>
            <button onClick={() => onSetTool('Trim start')}><span><Crop size={20} /></span><small>Trim</small></button>
            <button onClick={onSplit}><span><SlidersHorizontal size={20} /></span><small>Split</small></button>
            <button onClick={() => onSetTool('Volume')}><span><AudioLines size={20} /></span><small>Volume</small></button>
            <button className={editorTool === 'Move & Transform' ? 'tool-active' : ''} onClick={() => onSetTool('Move & Transform')}><span><Move size={21} /></span><small>Move & transform</small></button>
            <button className={editorTool === '3D Lab' ? 'tool-active' : ''} onClick={() => onSetTool('3D Lab')}><span><Box size={21} /></span><small>3D Lab</small></button>
            <button className={editorTool === 'Color & Fill' ? 'tool-active' : ''} onClick={() => onSetTool('Color & Fill')}><span><Circle size={21} /></span><small>Color & fill</small></button>
            <button className={editorTool === 'Effects' ? 'tool-active' : ''} onClick={addEffect}><span><Sparkles size={21} /></span><small>Effects</small></button>
            <button className={editorTool === 'Enhance' ? 'tool-active' : ''} onClick={() => onSetTool('Enhance')}><span><ScanLine size={21} /></span><small>Enhance</small></button>
            <button className={editorTool === 'Layer settings' ? 'tool-active' : ''} onClick={() => onSetTool('Layer settings')}><span><SlidersHorizontal size={21} /></span><small>Layer settings</small></button>
          </div>
          {editorTool && (
            <div className="inspector-panel">
              <div className="inspector-header"><div><span className="eyebrow">{selectedLayer?.name ?? 'PROJECT'}</span><strong>{editorTool}</strong></div><button className="icon-button" onClick={() => onSetTool(editorTool)} aria-label="Close panel"><X size={18} /></button></div>
              {selectedLayer?.kind === 'group' ? <div className="group-inspector"><Layers size={20} /><strong>{layers.filter((layer) => layer.groupId === selectedLayer.id).length} layers in this group</strong><small>Shift-select additional timeline layers, then use Group to add them. Groups organize the stack; child layers remain independently editable.</small><button className="secondary-button" onClick={() => onUngroup(selectedLayer.id)}>Ungroup layers</button></div> : editorTool === '3D Lab' && selectedLayer?.kind === '3d' ? <div className="model-inspector"><label className="project-setting-field">Primitive / mesh<select value={selectedLayer.meshName ?? 'Cube'} onChange={(event) => { const meshName = event.target.value as 'Cube' | 'Sphere' | 'Pyramid' | 'Plane' | 'Torus'; onUpdateLayer(selectedLayer.id, { meshName, mesh3d: createPrimitiveMesh(meshName) }); }}>{['Cube','Sphere','Pyramid','Plane','Torus'].map((mesh) => <option key={mesh}>{mesh}</option>)}{selectedLayer.meshName === 'Imported OBJ' && <option value="Imported OBJ" disabled>Imported OBJ file</option>}</select></label><p>Import Wavefront `.obj` through Add → Media. Meshes stay local to the project.</p><button className={selectedLayer.keyframes?.some((frame) => Math.abs(frame.time-time) < .04) ? 'keyframe-toggle active' : 'keyframe-toggle'} onClick={() => onKeyframe(selectedLayer)}><Plus size={13} /> {selectedLayer.keyframes?.some((frame) => Math.abs(frame.time-time) < .04) ? 'Remove 3D keyframe' : 'Keyframe 3D pose'}</button><label className="range-setting">Pitch <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotationX)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotationX: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotationX)}°</span></label><label className="range-setting">Yaw <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotationY)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotationY: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotationY)}°</span></label><label className="range-setting">Depth <input type="range" min={-300} max={300} value={Math.round(getLayerTransformAt(selectedLayer, time).depth)} onChange={(event) => onUpdateLayer(selectedLayer.id, { depth: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).depth)}px</span></label></div> : editorTool === 'Enhance' && selectedLayer ? <EnhancePanel layer={selectedLayer} busy={aiUpscaleBusy} progress={aiUpscaleProgress} status={aiUpscaleStatus} onUpscale={onAIUpscale} onUpdateLayer={onUpdateLayer} /> : editorTool === 'Move & Transform' && selectedLayer ? <>
                {(() => {
                  const frameAtPlayhead = selectedLayer.keyframes?.find((frame) => Math.abs(frame.time - time) < .04);
                  return <div className="keyframe-controls"><button className={frameAtPlayhead ? 'keyframe-toggle active' : 'keyframe-toggle'} onClick={() => onKeyframe(selectedLayer)}><Plus size={14} /> {frameAtPlayhead ? 'Remove keyframe' : 'Add keyframe'}</button><span>{selectedLayer.keyframes?.length ?? 0} keyframes</span>{Boolean(selectedLayer.keyframes?.length) && <><select aria-label="Keyframe easing" value={frameAtPlayhead?.easing ?? 'easeInOut'} disabled={!frameAtPlayhead} onChange={(event) => onSetEasing(selectedLayer, event.target.value as EasingPreset)}><option value="linear">Linear</option><option value="easeIn">Ease in</option><option value="easeOut">Ease out</option><option value="easeInOut">Ease in-out</option></select>{frameAtPlayhead && <svg className={`easing-mini-graph easing-${frameAtPlayhead.easing}`} viewBox="0 0 80 42" aria-label="Easing curve"><path d={frameAtPlayhead.easing === 'linear' ? 'M5 37 L75 5' : frameAtPlayhead.easing === 'easeIn' ? 'M5 37 C52 37 67 24 75 5' : frameAtPlayhead.easing === 'easeOut' ? 'M5 37 C13 18 28 5 75 5' : 'M5 37 C30 37 28 5 75 5'} /></svg>}</>}</div>;
                })()}
                <div className="transform-readouts"><label>X <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).x * 10)} onChange={(event) => onUpdateLayer(selectedLayer.id, { x: Number(event.target.value) / 10 })} /></label><label>Y <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).y * 10)} onChange={(event) => onUpdateLayer(selectedLayer.id, { y: Number(event.target.value) / 10 })} /></label><label>Scale <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).scale)} onChange={(event) => onUpdateLayer(selectedLayer.id, { scale: Number(event.target.value) })} /></label></div>
                <label className="range-setting">Rotation <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotation)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotation: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotation)}°</span></label>{selectedLayer.kind === '3d' && <><label className="range-setting">3D Pitch <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotationX)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotationX: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotationX)}°</span></label><label className="range-setting">3D Yaw <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotationY)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotationY: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotationY)}°</span></label><label className="range-setting">Depth <input type="range" min={-300} max={300} value={Math.round(getLayerTransformAt(selectedLayer, time).depth)} onChange={(event) => onUpdateLayer(selectedLayer.id, { depth: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).depth)}px</span></label></>}
              </> : editorTool === 'Speed' && selectedLayer ? <div className="speed-ramp-panel"><div className="speed-ramp-heading"><button className={selectedLayer.speedKeyframes?.some((frame) => Math.abs(frame.time-time) < .04) ? 'keyframe-toggle active' : 'keyframe-toggle'} onClick={() => onSpeedKeyframe(selectedLayer)}><Plus size={13} /> {selectedLayer.speedKeyframes?.some((frame) => Math.abs(frame.time-time) < .04) ? 'Remove speed key' : 'Add speed key'}</button><span>{selectedLayer.speedKeyframes?.length ?? 0} velocity keys</span></div><label className="range-setting">Playback speed <input type="range" min={10} max={800} value={Math.round(getLayerSpeedAt(selectedLayer, time) * 100)} onChange={(event) => onSpeedValue(selectedLayer, Number(event.target.value) / 100)} /><span>{getLayerSpeedAt(selectedLayer, time).toFixed(2)}×</span></label>{selectedLayer.speedKeyframes?.some((frame) => Math.abs(frame.time-time) < .04) && <label className="project-setting-field">Ramp easing<select value={selectedLayer.speedKeyframes.find((frame) => Math.abs(frame.time-time) < .04)?.easing ?? 'easeInOut'} onChange={(event) => onSpeedEasing(selectedLayer, event.target.value as EasingPreset)}><option value="linear">Linear</option><option value="easeIn">Ease in</option><option value="easeOut">Ease out</option><option value="easeInOut">Ease in-out</option></select></label>}{selectedLayer.kind === 'video' && <label className="motion-blend-setting"><input type="checkbox" checked={Boolean(selectedLayer.frameBlend)} onChange={(event) => onUpdateLayer(selectedLayer.id, { frameBlend: event.target.checked })} /><span><strong>Optical-flow slow motion · export</strong><small>Estimates low-resolution block motion on CPU and warps full-resolution export frames with WebGL when available. Experimental slow-motion approximation; not AI/Twixtor-grade interpolation.</small></span></label>}</div> : editorTool === 'Trim start' && selectedLayer ? <div className="trim-controls"><p>Clip range · {fmtTime(selectedLayer.start, project.fps)} – {fmtTime(selectedLayer.start + selectedLayer.duration, project.fps)}</p><label className="range-setting">Position <input type="range" min={0} max={Math.max(0, project.duration - selectedLayer.duration)} step={.01} value={Math.min(selectedLayer.start, Math.max(0, project.duration - selectedLayer.duration))} onChange={(event) => onUpdateLayer(selectedLayer.id, { start: Number(event.target.value) })} /><span>{fmtTime(selectedLayer.start, project.fps)}</span></label>{selectedLayer.sourceDuration && (selectedLayer.kind === 'audio' || selectedLayer.kind === 'video') && <label className="range-setting">Source in <input type="range" min={0} max={Math.max(0, selectedLayer.sourceDuration - selectedLayer.duration * (selectedLayer.speed ?? 1))} step={.01} value={Math.min(selectedLayer.sourceOffset ?? 0, Math.max(0, selectedLayer.sourceDuration - selectedLayer.duration * (selectedLayer.speed ?? 1)))} onChange={(event) => onUpdateLayer(selectedLayer.id, { sourceOffset: Number(event.target.value) })} /><span>{fmtTime(selectedLayer.sourceOffset ?? 0, project.fps)}</span></label>}<label className="range-setting">Length <input type="range" min={.1} max={Math.max(.1, Math.min(project.duration - selectedLayer.start, selectedLayer.sourceDuration ? (selectedLayer.sourceDuration - (selectedLayer.sourceOffset ?? 0)) / Math.max(.25, selectedLayer.speed ?? 1) : project.duration - selectedLayer.start))} step={.01} value={Math.min(selectedLayer.duration, project.duration - selectedLayer.start)} onChange={(event) => onUpdateLayer(selectedLayer.id, { duration: Number(event.target.value) })} /><span>{fmtTime(selectedLayer.duration, project.fps)}</span></label></div> : editorTool === 'Volume' && selectedLayer ? <>{selectedLayer.kind === 'audio' || selectedLayer.kind === 'video' ? <label className="range-setting">Volume <input type="range" min={0} max={100} value={selectedLayer.volume ?? 100} onChange={(event) => onUpdateLayer(selectedLayer.id, { volume: Number(event.target.value) })} /><span>{selectedLayer.volume ?? 100}%</span></label> : <div className="inspector-empty"><Info size={17} /> Volume is available on audio and video layers.</div>}</> : editorTool === 'Color & Fill' && selectedLayer ? <div className="inspector-swatches">{colorSwatches.map((color) => <button key={color} className={selectedLayer.color === color ? 'swatch-selected' : ''} style={{ background: color }} onClick={() => onUpdateLayer(selectedLayer.id, { color })} aria-label={`Set color ${color}`} />)}<label className="color-custom"><Pencil size={15} /><input type="color" value={selectedLayer.color} onChange={(event) => onUpdateLayer(selectedLayer.id, { color: event.target.value })} /></label></div> : editorTool === 'Effects' ? <div className="effect-inspector-list">{selectedLayer?.presetName && <span className="preset-applied-tag">LOOK · {selectedLayer.presetName}</span>}{selectedLayer?.effects.length ? selectedLayer.effects.map((effect, index) => <div className="effect-control" key={`${effect}-${index}`}><div className="effect-control-heading"><Sparkles size={15} /><span>{effect}</span><button onClick={() => selectedLayer && onUpdateLayer(selectedLayer.id, { effects: selectedLayer.effects.filter((_, itemIndex) => itemIndex !== index) })} aria-label={`Remove ${effect}`}><X size={15} /></button></div><label className="effect-strength"><input type="range" min={0} max={100} value={selectedLayer.effectStrengths?.[effect] ?? 75} onChange={(event) => selectedLayer && onUpdateLayer(selectedLayer.id, { effectStrengths: { ...selectedLayer.effectStrengths, [effect]: Number(event.target.value) } })} /><span>{selectedLayer.effectStrengths?.[effect] ?? 75}%</span></label></div>) : <p>No effects yet. Browse the library to add a look.</p>}<button className="secondary-button" onClick={onOpenEffects}><Plus size={16} /> Browse effects</button></div> : selectedLayer ? <>
                <label className="range-setting">Opacity <input type="range" min={0} max={100} value={Math.round(getLayerTransformAt(selectedLayer, time).opacity)} onChange={(event) => onUpdateLayer(selectedLayer.id, { opacity: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).opacity)}%</span></label>
                <label className="range-setting">Intensity <input type="range" min={0} max={100} value={panelSlider} onChange={(event) => onPanelSlider(Number(event.target.value))} /><span>{panelSlider}%</span></label>
                <button className="secondary-button" onClick={onOpenEffects}><Plus size={16} /> Add effect</button>
              </> : <div className="inspector-empty"><Info size={17} /> Select a layer to view its controls.</div>}
            </div>
          )}
          <button className="floating-add" onClick={onAdd}><Plus size={24} /><span>Add layer</span></button>
        </aside>
      </div>
      <div className="media-preload-bin" aria-hidden="true">{layers.filter((layer) => layer.kind === 'audio' && layer.src).map((layer) => <audio key={layer.id} ref={(element) => { if (element) audioRefs.current.set(layer.id, element); else audioRefs.current.delete(layer.id); }} src={layer.src} preload="auto" />)}</div>
    </div>
  );
}

function EnhancePanel({ layer, busy, progress, status, onUpscale, onUpdateLayer }: {
  layer: EditorLayer; busy: boolean; progress: number | null; status: string; onUpscale: () => void;
  onUpdateLayer: (id: string, patch: Partial<EditorLayer>) => void;
}) {
  const supported = layer.kind === 'image' || layer.kind === 'video';
  return <div className="enhance-panel">
    <strong>AI super-resolution</strong>
    {supported ? <>
      <button className="ai-upscale-button" disabled={busy} onClick={onUpscale}><Sparkles size={16} />{busy ? 'Processing locally…' : layer.kind === 'video' ? 'AI restore current frame · 2×' : 'AI upscale image · 2×'}</button>
      <small>Bundled ESRGAN-Slim 2× model, runs on this device, no upload. Large sources are downsampled to a mobile-safe input size. Video action creates a restored still at the playhead; it does not re-render the whole clip.</small>
      {busy && <div className="ai-progress"><div className="ai-progress-heading"><span>{status || 'Preparing model…'}</span><span>{progress ?? 0}%</span></div><div className="ai-progress-track"><span style={{ width: `${progress ?? 2}%` }} /></div></div>}
      {layer.kind === 'image' && <label className="range-setting">Extra local sharpening <input type="range" min={0} max={100} value={layer.restoreStrength ?? 0} onChange={(event) => onUpdateLayer(layer.id, { restoreStrength: Number(event.target.value) })} /><span>{layer.restoreStrength ?? 0}%</span></label>}
    </> : <p>AI super-resolution is available on still images and extracted video frames.</p>}
    <small>The 2× model output is added as a new layer. Original media stays untouched; an optional export-time unsharp pass remains separate from the AI model.</small>
  </div>;
}

function StageMeshCanvas({ layer, transform, lowQuality }: { layer: EditorLayer; transform: ReturnType<typeof getLayerTransformAt>; lowQuality: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) return;
    const resolution = lowQuality ? 240 : 640;
    if (canvas.width !== resolution || canvas.height !== resolution) { canvas.width = resolution; canvas.height = resolution; }
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.save();
    context.translate(canvas.width / 2, canvas.height / 2);
    const mesh = layer.mesh3d ?? createPrimitiveMesh(layer.meshName && layer.meshName !== 'Imported OBJ' ? layer.meshName : 'Cube');
    drawMesh3D(context, mesh, { size: resolution * .78, color: layer.color, rotationX: transform.rotationX, rotationY: transform.rotationY, depth: transform.depth });
    context.restore();
  }, [layer.mesh3d, layer.meshName, layer.color, transform.rotationX, transform.rotationY, transform.depth, lowQuality]);
  return <canvas ref={canvasRef} className={`stage-3d-canvas ${lowQuality ? 'stage-mesh-low' : ''}`} aria-label={`${layer.meshName ?? '3D'} model preview`} />;
}

function SelectionHandles() {
  return <span className="selection-handles" aria-hidden="true"><i data-resize-handle /><i /><i data-resize-handle /><i /><i data-resize-handle /><i /><i data-resize-handle /><i /></span>;
}
