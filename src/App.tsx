import { ChangeEvent, CSSProperties, RefObject, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDownToLine,
  ArrowLeft,
  AudioLines,
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

type ProjectKind = 'project' | 'template' | 'element';
type Screen = 'home' | 'library' | 'editor';
type LayerKind = 'image' | 'video' | 'audio' | 'text' | 'shape' | 'camera' | 'null';
type ProjectRatio = '16:9' | '9:16' | '4:5' | '1:1' | '4:3' | 'Custom';
type EasingPreset = 'linear' | 'easeIn' | 'easeOut' | 'easeInOut';
type LayerKeyframe = { time: number; x: number; y: number; scale: number; rotation: number; opacity: number; easing: EasingPreset };

type EditorLayer = {
  id: string;
  name: string;
  kind: LayerKind;
  src?: string;
  text?: string;
  shape?: string;
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

const colorSwatches = ['#0b0c0e', '#d9d9dc', '#f1f1f2', '#85868d', '#fa3f9c', '#18e3ad', '#ffd05b', '#77a8ff', '#8d70ff'];
const ratios: ProjectRatio[] = ['16:9', '9:16', '4:5', '1:1', '4:3', 'Custom'];

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

const fmtTime = (seconds: number) => {
  const safe = Math.max(0, seconds);
  const mins = Math.floor(safe / 60).toString().padStart(2, '0');
  const secs = Math.floor(safe % 60).toString().padStart(2, '0');
  const frames = Math.floor((safe % 1) * 30).toString().padStart(2, '0');
  return `${mins}:${secs}:${frames}`;
};

const makeLayer = (partial: Partial<EditorLayer> & Pick<EditorLayer, 'name' | 'kind'>): EditorLayer => ({
  id: `layer-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
  color: '#f23e98', visible: true, duration: 5, start: 0, x: 50, y: 50, scale: 100, rotation: 0, opacity: 100, effects: [],
  ...partial,
});

function getLayerTransformAt(layer: EditorLayer, time: number) {
  const frames = [...(layer.keyframes ?? [])].sort((a, b) => a.time - b.time);
  if (!frames.length) return { x: layer.x, y: layer.y, scale: layer.scale, rotation: layer.rotation, opacity: layer.opacity };
  if (time <= frames[0].time) return frames[0];
  if (time >= frames[frames.length - 1].time) return frames[frames.length - 1];
  const rightIndex = frames.findIndex((frame) => frame.time >= time);
  const left = frames[Math.max(0, rightIndex - 1)];
  const right = frames[rightIndex];
  const raw = (time - left.time) / Math.max(.0001, right.time - left.time);
  const amount = left.easing === 'easeIn' ? raw * raw : left.easing === 'easeOut' ? 1 - (1 - raw) * (1 - raw) : left.easing === 'easeInOut' ? raw < .5 ? 2 * raw * raw : 1 - Math.pow(-2 * raw + 2, 2) / 2 : raw;
  return {
    x: left.x + (right.x - left.x) * amount,
    y: left.y + (right.y - left.y) * amount,
    scale: left.scale + (right.scale - left.scale) * amount,
    rotation: left.rotation + (right.rotation - left.rotation) * amount,
    opacity: left.opacity + (right.opacity - left.opacity) * amount,
  };
}

const cssFilterForEffects = (effects: string[]) => {
  const filters: string[] = [];
  for (const effect of effects) {
    const name = effect.toLowerCase();
    if (name.includes('blur')) filters.push('blur(3px)');
    else if (name.includes('brightness') || name.includes('exposure')) filters.push('brightness(1.12) contrast(1.08)');
    else if (name.includes('saturation')) filters.push('saturate(1.45)');
    else if (name.includes('sepia') || name.includes('vintage') || name.includes('film grade')) filters.push('sepia(.28) contrast(1.08)');
    else if (name.includes('vhs') || name.includes('glitch')) filters.push('saturate(.78) contrast(1.16) hue-rotate(8deg)');
    else if (name.includes('sharpen') || name.includes('edge')) filters.push('contrast(1.22) saturate(1.06)');
    else if (name.includes('glow')) filters.push('brightness(1.08) saturate(1.16)');
    else if (name.includes('grayscale') || name.includes('mono')) filters.push('grayscale(1)');
  }
  return filters.length ? filters.join(' ') : 'none';
};

async function renderCompositionVideo(project: Project, layers: EditorLayer[], onProgress: (progress: number) => void): Promise<Blob> {
  if (typeof MediaRecorder === 'undefined') throw new Error('Video rendering is not supported by this browser.');
  const longestSide = Math.max(project.width, project.height, 1);
  const renderScale = Math.min(1, 2560 / longestSide);
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
  const stream = canvas.captureStream(Math.max(1, Math.min(project.fps, 60)));
  audioDestination?.stream.getAudioTracks().forEach((track) => stream.addTrack(track));
  const mimeType = typeof MediaRecorder.isTypeSupported === 'function' ? ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type)) : undefined;
  let recorder: MediaRecorder;
  try {
    recorder = mimeType
      ? new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 8_000_000, audioBitsPerSecond: 192_000 })
      : new MediaRecorder(stream);
  } catch {
    stream.getTracks().forEach((track) => track.stop());
    if (audioContext) void audioContext.close();
    throw new Error('This browser does not support a compatible WebM encoder.');
  }

  const duration = Math.max(0.5, project.duration);
  const drawFrame = (time: number) => {
    context.fillStyle = project.background;
    context.fillRect(0, 0, canvas.width, canvas.height);
    for (const layer of layers) {
      if (!layer.visible || time < layer.start || time >= layer.start + layer.duration) continue;
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
        const drawable = resource as CanvasImageSource;
        const filter = cssFilterForEffects(layer.effects);
        if (filter !== 'none') context.filter = filter;
        context.drawImage(drawable, -canvas.width * animated.scale / 200, -canvas.height * animated.scale / 200, canvas.width * animated.scale / 100, canvas.height * animated.scale / 100);
      } else if (layer.kind === 'text' && layer.text) {
        context.fillStyle = layer.color;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.font = `700 ${Math.max(26, canvas.width * .075)}px sans-serif`;
        context.shadowColor = 'rgba(0,0,0,.42)';
        context.shadowBlur = Math.max(0, canvas.width * .012);
        context.fillText(layer.text, 0, 0, canvas.width * .9);
      } else if (layer.kind === 'shape') {
        context.fillStyle = layer.color;
        const side = Math.min(canvas.width, canvas.height) * .26 * animated.scale / 100;
        if (layer.shape === 'Circle' || layer.shape === 'Ring') {
          context.beginPath(); context.arc(0, 0, side / 2, 0, Math.PI * 2); context.fill();
        } else if (layer.shape === 'Triangle') {
          context.beginPath(); context.moveTo(0, -side / 2); context.lineTo(side / 2, side / 2); context.lineTo(-side / 2, side / 2); context.closePath(); context.fill();
        } else if (layer.shape === 'Star') {
          context.beginPath();
          for (let point = 0; point < 10; point++) {
            const radius = point % 2 === 0 ? side / 2 : side / 4;
            const angle = point * Math.PI / 5 - Math.PI / 2;
            const x = Math.cos(angle) * radius; const y = Math.sin(angle) * radius;
            if (point === 0) context.moveTo(x, y); else context.lineTo(x, y);
          }
          context.closePath(); context.fill();
        } else {
          context.beginPath();
          if (typeof context.roundRect === 'function') context.roundRect(-side / 2, -side / 2, side, side, Math.min(28, side * .12));
          else context.rect(-side / 2, -side / 2, side, side);
          context.fill();
        }
      }
      context.restore();
    }
  };

  const syncMedia = (time: number) => {
    for (const layer of soundLayers) {
      const resource = resources.get(layer.id);
      if (!(resource instanceof HTMLMediaElement)) continue;
      const localTime = Math.max(0, time - layer.start) * Math.max(.25, Math.min(4, layer.speed ?? 1));
      const active = layer.visible && time >= layer.start && time < layer.start + layer.duration;
      resource.volume = Math.max(0, Math.min(1, (layer.volume ?? 100) / 100));
      resource.playbackRate = Math.max(.25, Math.min(4, layer.speed ?? 1));
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
    let lastProgress = -1;
    let cleaned = false;
    const startTime = performance.now();
    const cleanup = () => {
      if (cleaned) return;
      cleaned = true;
      cancelAnimationFrame(animation);
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
      const render = (now: number) => {
        const elapsed = Math.min(duration, (now - startTime) / 1000);
        try { syncMedia(elapsed); drawFrame(elapsed); }
        catch (error) { failure = error instanceof Error ? error : new Error('Could not draw the video frame.'); if (recorder.state !== 'inactive') recorder.stop(); else { cleanup(); reject(failure); } return; }
        const percentage = Math.floor(elapsed / duration * 100);
        if (percentage !== lastProgress) { lastProgress = percentage; onProgress(percentage); }
        if (elapsed >= duration) { recorder.stop(); return; }
        animation = requestAnimationFrame(render);
      };
      animation = requestAnimationFrame(render);
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
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [newProjectOpen, setNewProjectOpen] = useState(false);
  const [addSheetOpen, setAddSheetOpen] = useState(false);
  const [shapeSheetOpen, setShapeSheetOpen] = useState(false);
  const [effectBrowserOpen, setEffectBrowserOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [renderProgress, setRenderProgress] = useState<number | null>(null);
  const [renderedVideoUrl, setRenderedVideoUrl] = useState<string | null>(null);
  const [qrOpen, setQrOpen] = useState(false);
  const [packageUrl, setPackageUrl] = useState('');
  const [query, setQuery] = useState('');
  const [sortBy, setSortBy] = useState<'Date' | 'Name' | 'Duration'>('Date');
  const [toast, setToast] = useState('');
  const [showLowQuality, setShowLowQuality] = useState(false);
  const [showOffTimeline, setShowOffTimeline] = useState(true);
  const [rememberAddPage, setRememberAddPage] = useState(false);
  const [audioResampling, setAudioResampling] = useState('Ultra');
  const [layerDuration, setLayerDuration] = useState('2s');

  const [createKind, setCreateKind] = useState<ProjectKind>('project');
  const [createName, setCreateName] = useState('New Project');
  const [createRatio, setCreateRatio] = useState<ProjectRatio>('1:1');
  const [createResolution, setCreateResolution] = useState('1080p (FHD)');
  const [createFps, setCreateFps] = useState(60);
  const [createBackground, setCreateBackground] = useState('#d9d9dc');
  const [customRatio, setCustomRatio] = useState('3:2');

  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const [layers, setLayers] = useState<EditorLayer[]>([]);
  const [selectedLayerId, setSelectedLayerId] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [editorTool, setEditorTool] = useState<string | null>(null);
  const [contextLayerId, setContextLayerId] = useState<string | null>(null);
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

  const selectedLayer = layers.find((layer) => layer.id === selectedLayerId) ?? null;

  const notify = (message: string) => {
    setToast(message);
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => setToast(''), 2800);
  };

  useEffect(() => {
    try {
      const stored = localStorage.getItem('editorshub-projects-v1');
      if (stored) {
        const parsed = JSON.parse(stored) as Project[];
        if (Array.isArray(parsed) && parsed.length) {
          const demos = seedProjects.filter((project) => !parsed.some((saved) => saved.id === project.id));
          setProjects([...parsed, ...demos]);
        }
      }
    } catch (error) {
      console.warn('Could not restore saved project list', error);
    }
  }, []);

  useEffect(() => {
    if (!currentProject) return;
    setProjects((list) => list.map((project) => project.id === currentProject.id ? { ...currentProject, layers } : project));
  }, [currentProject, layers]);

  useEffect(() => {
    try {
      const safeProjects = projects.map((project) => ({
        ...project,
        thumbnail: project.thumbnail?.startsWith('blob:') ? undefined : project.thumbnail,
        layers: project.layers?.filter((layer) => !layer.src?.startsWith('blob:')),
      }));
      localStorage.setItem('editorshub-projects-v1', JSON.stringify(safeProjects));
    } catch (error) {
      console.warn('Could not save project list', error);
    }
  }, [projects]);

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
    }, Math.max(16, 1000 / Math.max(currentProject.fps, 1)));
    return () => window.clearInterval(timer);
  }, [isPlaying, currentProject]);

  useEffect(() => {
    const activeVideo = layers.find((layer) => layer.kind === 'video' && layer.visible && time >= layer.start && time < layer.start + layer.duration);
    const video = videoRef.current;
    if (!video || !activeVideo) { video?.pause(); return; }
    video.playbackRate = Math.max(.25, Math.min(4, activeVideo.speed ?? 1));
    video.volume = Math.max(0, Math.min(1, (activeVideo.volume ?? 100) / 100));
    const targetTime = Math.max(0, time - activeVideo.start) * (activeVideo.speed ?? 1);
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
    setCreateBackground(kind === 'element' ? '#050506' : '#d9d9dc');
    setNewProjectOpen(true);
  };

  const openEditor = (project: Project) => {
    const projectLayers = project.layers?.map((layer) => ({ ...layer })) ?? [];
    setCurrentProject(project);
    setUndoStack([]);
    setRedoStack([]);
    setLayers(projectLayers);
    setSelectedLayerId(projectLayers[0]?.id ?? null);
    setTime(0);
    setIsPlaying(false);
    setScreen('editor');
    setEditorTool(null);
    setContextLayerId(null);
  };

  const commitLayerList = (nextLayers: EditorLayer[]) => {
    setUndoStack((stack) => [...stack, layers].slice(-40));
    setRedoStack([]);
    setLayers(nextLayers);
  };

  const updateLayer = (id: string, patch: Partial<EditorLayer>) => {
    const target = layers.find((layer) => layer.id === id);
    if (!target) return;
    const transformKeys = ['x', 'y', 'scale', 'rotation', 'opacity'] as const;
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
      const { x: _x, y: _y, scale: _scale, rotation: _rotation, opacity: _opacity, ...otherPatch } = patch;
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
    setLayers(previous);
    if (!previous.some((layer) => layer.id === selectedLayerId)) setSelectedLayerId(previous.at(-1)?.id ?? null);
    notify('Edit undone');
  };

  const redoEdit = () => {
    const next = redoStack[redoStack.length - 1];
    if (!next) { notify('Nothing to redo'); return; }
    setUndoStack((stack) => [...stack, layers]);
    setRedoStack((stack) => stack.slice(0, -1));
    setLayers(next);
    if (!next.some((layer) => layer.id === selectedLayerId)) setSelectedLayerId(next.at(-1)?.id ?? null);
    notify('Edit restored');
  };

  const createFromSettings = () => {
    const customParts = customRatio.split(':').map((part) => Number.parseInt(part, 10));
    const customWidth = Number.isFinite(customParts[0]) && customParts[0] > 0 ? customParts[0] : 3;
    const customHeight = Number.isFinite(customParts[1]) && customParts[1] > 0 ? customParts[1] : 2;
    const [baseWidth, baseHeight] = createRatio === 'Custom' ? (customWidth >= customHeight ? [1440, Math.round(1440 * customHeight / customWidth)] : [Math.round(1080 * customWidth / customHeight), 1080]) : ratioDimensions[createRatio];
    const resolutionNumber = Number.parseInt(createResolution, 10) || 1080;
    const scale = resolutionNumber / 1080;
    const width = Math.round(baseWidth * scale);
    const height = Math.round(baseHeight * scale);
    const project: Project = {
      id: `project-${Date.now()}`,
      name: createName.trim() || `Untitled ${createKind}`,
      kind: createKind,
      ratio: createRatio,
      width,
      height,
      resolution: `${resolutionNumber}p`,
      fps: createFps,
      background: createBackground,
      updatedAt: dateLabel,
      duration: 15,
    };
    setProjects((list) => [project, ...list]);
    setCurrentProject(project);
    setUndoStack([]);
    setRedoStack([]);
    setLayers([]);
    setSelectedLayerId(null);
    setTime(0);
    setNewProjectOpen(false);
    setScreen('editor');
    notify(`${createKind === 'element' ? 'Element' : createKind === 'template' ? 'Template' : 'Project'} canvas created`);
  };

  const addLayer = (layer: EditorLayer) => {
    commitLayerList([...layers, layer]);
    setSelectedLayerId(layer.id);
    setEditorTool(null);
    if (currentProject) {
      setCurrentProject((project) => project ? { ...project, duration: Math.max(project.duration, layer.duration) } : project);
    }
  };

  const handleMediaFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    if (file.type === 'application/json' || file.name.toLowerCase().endsWith('.ehproj')) {
      void file.text().then((text) => {
        try {
          const parsed = JSON.parse(text) as Project & { layers?: EditorLayer[] };
          const imported: Project = { ...parsed, id: `import-${Date.now()}`, updatedAt: dateLabel };
          if (!imported.name || !imported.ratio) throw new Error('Invalid project package');
          setProjects((list) => [imported, ...list]);
          setAddSheetOpen(false);
          openEditor(imported);
          notify('Project package imported');
        } catch {
          notify('That file is not a valid EditorsHub project package');
        }
      });
      return;
    }
    const kind: LayerKind = file.type.startsWith('video/') ? 'video' : file.type.startsWith('audio/') ? 'audio' : 'image';
    const src = URL.createObjectURL(file);
    const layer = makeLayer({ name: file.name.replace(/\.[^/.]+$/, ''), kind, src, duration: currentProject?.duration ?? 10 });
    if (screen === 'editor' && currentProject) {
      setAddSheetOpen(false);
      addLayer(layer);
      notify(`${kind === 'audio' ? 'Audio' : 'Media'} added to timeline`);
      return;
    }
    const project: Project = {
      id: `import-${Date.now()}`, name: file.name.replace(/\.[^/.]+$/, ''), kind: 'project', ratio: '9:16', width: 1080,
      height: 1920, resolution: '1080p', fps: 30, background: '#08090c', updatedAt: dateLabel, duration: 10,
      thumbnail: kind === 'image' ? src : undefined, layers: [layer],
    };
    setProjects((list) => [project, ...list]);
    openEditor(project);
    notify('Media opened in a new project');
  };

  const handleXmlFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!file) return;
    void file.text().then((xml) => {
      const titleMatch = xml.match(/(?:name|title)=["']([^"']+)["']/i);
      const project: Project = {
        id: `xml-${Date.now()}`, name: titleMatch?.[1] ?? file.name.replace(/\.xml$/i, ''), kind: 'project', ratio: '9:16', width: 1080,
        height: 1920, resolution: '1080p', fps: 30, background: '#08090c', updatedAt: dateLabel, duration: 15,
      };
      setProjects((list) => [project, ...list]);
      openEditor(project);
      notify('XML project imported — review the timeline before editing');
    });
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
      const packageData = await response.json() as Project & { layers?: EditorLayer[] };
      if (!packageData.name || !packageData.ratio || !packageData.width || !packageData.height) {
        throw new Error('This link does not contain an EditorsHub project package');
      }
      const imported: Project = { ...packageData, id: `link-${Date.now()}`, updatedAt: dateLabel };
      setProjects((list) => [imported, ...list]);
      openEditor(imported);
      setPackageUrl('');
      notify('Project package imported');
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
    const layer = makeLayer({ name: textDraft.trim() || 'Text', kind: 'text', text: textDraft.trim() || 'Your text', color: '#ffffff', duration: defaultDuration });
    addLayer(layer);
    setTextModalOpen(false);
    setAddSheetOpen(false);
    notify('Text layer added');
  };

  const makeShape = (shapeName: string) => {
    const defaultDuration = Math.min(Number.parseInt(layerDuration, 10) || 2, currentProject?.duration ?? 15);
    const layer = makeLayer({ name: shapeName, kind: 'shape', shape: shapeName, color: '#f23e98', duration: defaultDuration });
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
    const left = { ...selectedLayer, duration: splitAt - selectedLayer.start, keyframes: sourceFrames.length ? leftFrames : undefined };
    const right = { ...selectedLayer, ...splitTransform, id: `layer-split-${Date.now()}`, name: `${selectedLayer.name} 2`, start: splitAt, duration: layerEnd - splitAt, keyframes: sourceFrames.length ? rightFrames : undefined };
    const index = layers.findIndex((layer) => layer.id === selectedLayer.id);
    const next = [...layers];
    next.splice(index, 1, left, right);
    commitLayerList(next);
    setSelectedLayerId(right.id);
    notify('Clip split at the playhead');
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

  const removeLayer = (id: string) => {
    commitLayerList(layers.filter((layer) => layer.id !== id));
    if (selectedLayerId === id) setSelectedLayerId(null);
    setContextLayerId(null);
    setEditorTool(null);
    notify('Layer removed');
  };

  const exportProjectPackage = () => {
    if (!currentProject) return;
    const packageData = { ...currentProject, layers, packageVersion: 1, app: 'EditorsHub' };
    const blob = new Blob([JSON.stringify(packageData, null, 2)], { type: 'application/json' });
    const href = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = href;
    anchor.download = `${currentProject.name.replace(/[^a-z0-9-_]+/gi, '_')}.ehproj`;
    anchor.click();
    URL.revokeObjectURL(href);
    notify('Project package saved');
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
    for (const layer of layers.filter((item) => item.visible && time >= item.start && time < item.start + item.duration)) {
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
        context.fillText(layer.text, 0, 0, canvas.width * .9);
        context.restore();
      } else if (layer.kind === 'shape') {
        context.save();
        const animated = getLayerTransformAt(layer, time);
        context.globalAlpha = animated.opacity / 100;
        context.fillStyle = layer.color;
        const size = Math.min(canvas.width, canvas.height) * 0.22 * animated.scale / 100;
        const x = canvas.width * animated.x / 100 - size / 2;
        const y = canvas.height * animated.y / 100 - size / 2;
        context.translate(x + size / 2, y + size / 2);
        context.rotate(animated.rotation * Math.PI / 180);
        if (layer.shape === 'Circle') {
          context.beginPath(); context.arc(0, 0, size / 2, 0, Math.PI * 2); context.fill();
        } else if (layer.shape === 'Triangle') {
          context.beginPath(); context.moveTo(0, -size / 2); context.lineTo(size / 2, size / 2); context.lineTo(-size / 2, size / 2); context.closePath(); context.fill();
        } else {
          context.fillRect(-size / 2, -size / 2, size, size);
        }
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
            drawable = image;
          }
          context.save();
          const animated = getLayerTransformAt(layer, time);
          context.globalAlpha = animated.opacity / 100;
          context.filter = cssFilterForEffects(layer.effects);
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

  const renderVideo = async () => {
    if (!currentProject) return;
    setExportOpen(false);
    setRenderedVideoUrl(null);
    setRenderProgress(0);
    try {
      const blob = await renderCompositionVideo(currentProject, layers, setRenderProgress);
      setRenderedVideoUrl(URL.createObjectURL(blob));
      notify('Video render complete');
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Video render failed');
    } finally {
      setRenderProgress(null);
    }
  };

  const downloadRenderedVideo = () => {
    if (!renderedVideoUrl || !currentProject) return;
    const anchor = document.createElement('a');
    anchor.href = renderedVideoUrl;
    anchor.download = `${currentProject.name.replace(/[^a-z0-9-_]+/gi, '_')}.webm`;
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
          onBack={() => { setScreen('library'); setIsPlaying(false); setContextLayerId(null); }}
          onPlay={() => setIsPlaying((value) => !value)}
          onSeek={(value) => { setTime(value); if (videoRef.current) { const end = Number.isFinite(videoRef.current.duration) ? videoRef.current.duration : value; videoRef.current.currentTime = Math.min(value, end); } }}
          onSelectLayer={setSelectedLayerId}
          onAdd={() => setAddSheetOpen(true)}
          onSetTool={(tool) => setEditorTool((value) => value === tool ? null : tool)}
          onSetContext={setContextLayerId}
          onUpdateLayer={updateLayer}
          onDeleteLayer={removeLayer}
          onSplit={splitSelectedLayer}
          onKeyframe={toggleKeyframe}
          onSetEasing={setKeyframeEasing}
          onDuplicateLayer={(layer) => addLayer({ ...layer, id: `layer-copy-${Date.now()}`, name: `${layer.name} copy`, x: Math.min(90, layer.x + 5), y: Math.min(90, layer.y + 5) })}
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
          <BottomNavigation screen={screen} onHome={() => setScreen('home')} onLibrary={() => { setLibraryTab('project'); setScreen('library'); }} onCreate={() => openCreator('project')} />
        </>
      )}

      <input ref={mediaInputRef} type="file" accept="image/*,video/*,audio/*,.ehproj,application/json" className="visually-hidden" onChange={handleMediaFile} />
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
      {addSheetOpen && (
        <AddSheet
          onClose={() => setAddSheetOpen(false)}
          onMedia={() => mediaInputRef.current?.click()}
          onText={() => { setTextDraft('Your text'); setTextModalOpen(true); }}
          onShape={() => setShapeSheetOpen(true)}
          onAudio={() => { mediaInputRef.current?.click(); notify('Choose an audio file to add to the timeline'); }}
          onAction={(name) => {
            if (name === 'Element / Project') { setAddSheetOpen(false); setScreen('library'); setLibraryTab('element'); return; }
            if (name === 'Template') { setAddSheetOpen(false); setScreen('library'); setLibraryTab('template'); return; }
            const kind: LayerKind = name === 'Camera' ? 'camera' : 'null';
            addLayer(makeLayer({ name: name === 'Camera' ? 'Camera 1' : 'Null 1', kind, color: '#14e6aa', duration: currentProject?.duration ?? 5 }));
            setAddSheetOpen(false);
            notify(`${name} layer added`);
          }}
        />
      )}
      {shapeSheetOpen && <ShapeSheet onClose={() => setShapeSheetOpen(false)} onSelect={makeShape} />}
      {effectBrowserOpen && (
        <EffectBrowser
          query={effectQuery} onQuery={setEffectQuery}
          category={effectCategory} onCategory={setEffectCategory}
          onClose={() => setEffectBrowserOpen(false)}
          onSelect={(effect) => {
            if (!selectedLayer) { notify('Select a layer before adding an effect'); return; }
            updateLayer(selectedLayer.id, { effects: [...selectedLayer.effects, effect] });
            setEffectBrowserOpen(false);
            notify(`${effect} added`);
          }}
        />
      )}
      {exportOpen && (
        <ExportDialog
          project={currentProject}
          onClose={() => setExportOpen(false)}
          onPackage={() => { exportProjectPackage(); setExportOpen(false); }}
          onPreview={() => { void exportPreview(); setExportOpen(false); }}
          onRender={() => { void renderVideo(); }}
        />
      )}
      {renderProgress !== null && <RenderProgressDialog progress={renderProgress} projectName={currentProject?.name ?? 'Untitled project'} />}
      {renderedVideoUrl && currentProject && <RenderedVideoDialog src={renderedVideoUrl} name={currentProject.name} onClose={() => setRenderedVideoUrl(null)} onDownload={downloadRenderedVideo} />}
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
                <span className="duration-pill">{fmtTime(project.duration)}</span>
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
          <SettingToggle label="Low quality preview" note="Use less memory while editing" checked={lowQuality} onChange={onLowQuality} />
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

function NewProjectSheet({
  kind, onKind, name, onName, ratio, onRatio, resolution, onResolution, fps, onFps, background, onBackground,
  customRatio, onCustomRatio, onClose, onCreate,
}: {
  kind: ProjectKind; onKind: (value: ProjectKind) => void; name: string; onName: (value: string) => void;
  ratio: ProjectRatio; onRatio: (value: ProjectRatio) => void; resolution: string; onResolution: (value: string) => void;
  fps: number; onFps: (value: number) => void; background: string; onBackground: (value: string) => void;
  customRatio: string; onCustomRatio: (value: string) => void; onClose: () => void; onCreate: () => void;
}) {
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
          <div className="creation-label"><strong>Resolution</strong><small>Composition size<br />{ratio === 'Custom' ? `${customRatio} · Custom` : ratioDimensions[ratio].join(' × ')}</small></div>
          <select value={resolution} onChange={(event) => onResolution(event.target.value)} aria-label="Resolution"><option>720p (HD)</option><option>1080p (FHD)</option><option>1440p (QHD)</option><option>2160p (4K)</option></select>
        </div>
        <div className="creation-settings compact-creation"><div className="creation-label"><strong>Frame rate</strong></div><select value={fps} onChange={(event) => onFps(Number(event.target.value))} aria-label="Frame rate"><option value={24}>24 fps</option><option value={30}>30 fps</option><option value={60}>60 fps</option><option value={120}>120 fps</option></select></div>
        <div className={`creation-settings compact-creation ${kind === 'element' ? 'disabled-setting' : ''}`}><div className="creation-label"><strong>Background</strong></div><div className="background-select"><span style={{ background: background }} /><select disabled={kind === 'element'} value={background} onChange={(event) => onBackground(event.target.value)} aria-label="Background">{colorSwatches.map((color) => <option key={color} value={color}>{color === '#0b0c0e' ? 'Black' : color === '#f1f1f2' ? 'White' : color === '#d9d9dc' ? 'Light grey' : 'Custom color'}</option>)}</select><ChevronDown size={17} /></div></div>
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

function EffectBrowser({ query, onQuery, category, onCategory, onClose, onSelect }: {
  query: string; onQuery: (value: string) => void; category: string | null; onCategory: (value: string | null) => void;
  onClose: () => void; onSelect: (effect: string) => void;
}) {
  const filteredCategories = initialEffects.map((item) => ({ ...item, effects: item.effects.filter((effect) => effect.toLowerCase().includes(query.toLowerCase())) })).filter((item) => (!category || item.title === category) && (query ? item.effects.length > 0 : true));
  return (
    <div className="effect-browser-overlay">
      <header className="effect-browser-header"><button className="icon-button" onClick={onClose} aria-label="Close effect browser"><X size={24} /></button><div><span className="eyebrow">EDITORS HUB LIBRARY</span><h2>{category ?? 'Effect browser'}</h2></div><label className="effect-search"><Search size={19} /><input value={query} onChange={(event) => onQuery(event.target.value)} placeholder="Search effects" /></label></header>
      <main className="effect-browser-content">
        {category && <button className="effect-back" onClick={() => onCategory(null)}><ArrowLeft size={16} /> All categories</button>}
        <div className="effect-category-grid">{filteredCategories.map((item, index) => (
          <section className={`effect-category ${item.tint}`} key={item.title}>
            {!category && <button className="effect-category-title" onClick={() => onCategory(item.title)}><span>{item.title}</span><ChevronRight size={16} /></button>}
            {category && <h3>{item.title}</h3>}
            {(category || query) && <div className="effect-list">{item.effects.map((effect, effectIndex) => <button key={effect} onClick={() => onSelect(effect)}><span className="effect-mini-symbol">{effectIndex % 2 === 0 ? <Sparkles size={16} /> : <Zap size={16} />}</span><span>{effect}</span><Plus size={16} /></button>)}</div>}
          </section>
        ))}</div>
      </main>
    </div>
  );
}

function ExportDialog({ project, onClose, onPackage, onPreview, onRender }: {
  project: Project | null; onClose: () => void; onPackage: () => void; onPreview: () => void; onRender: () => void;
}) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="export-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">DELIVER YOUR WORK</span><h2>Export</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={21} /></button></div>
        <div className="export-summary"><div className="export-summary-icon"><Film size={22} /></div><div><strong>{project?.name ?? 'Untitled project'}</strong><small>{project?.width} × {project?.height} · {project?.fps} fps · {fmtTime(project?.duration ?? 0)}</small></div></div>
        <button className="export-option" onClick={onPackage}><span className="export-option-icon"><FilePlus2 size={21} /></span><span><strong>Save project package</strong><small>Editable timeline · .ehproj</small></span><ChevronRight size={18} /></button>
        <button className="export-option" onClick={onPreview}><span className="export-option-icon"><ImageIcon size={21} /></span><span><strong>Export frame as PNG</strong><small>Capture the current composition</small></span><ChevronRight size={18} /></button>
        <button className="export-option" onClick={onRender}><span className="export-option-icon"><Video size={21} /></span><span><strong>Render video</strong><small>Render your composition · WebM</small></span><ChevronRight size={18} /></button>
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

function RenderedVideoDialog({ src, name, onClose, onDownload }: { src: string; name: string; onClose: () => void; onDownload: () => void }) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="rendered-video-dialog" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-heading"><div><span className="eyebrow">EXPORT COMPLETE</span><h2>Your video is ready</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={20} /></button></div>
        <video className="rendered-video-preview" src={src} controls playsInline />
        <p>{name}.webm · rendered locally on this device</p>
        <div className="rendered-video-actions"><button className="secondary-button" onClick={onClose}>Close</button><button className="primary-button" onClick={onDownload}><ArrowDownToLine size={17} /> Download WebM</button></div>
      </section>
    </div>
  );
}

function EditorScreen({
  project, layers, selectedLayerId, selectedLayer, time, isPlaying, editorTool, stageStyle, formattedRatio, videoRef, canvasZoom,
  panelSlider, canUndo, canRedo, onUndo, onRedo, onBack, onPlay, onSeek, onSelectLayer, onAdd, onSetTool, onSetContext, onUpdateLayer, onDeleteLayer,
  onDuplicateLayer, onSaveElement, onSplit, onKeyframe, onSetEasing, onOpenEffects, onExport, onZoom, onPanelSlider, onRename, onNotify, contextLayerId,
}: {
  project: Project; layers: EditorLayer[]; selectedLayerId: string | null; selectedLayer: EditorLayer | null; time: number; isPlaying: boolean;
  editorTool: string | null; stageStyle: CSSProperties; formattedRatio: string; videoRef: RefObject<HTMLVideoElement | null>; canvasZoom: number;
  panelSlider: number; canUndo: boolean; canRedo: boolean; onUndo: () => void; onRedo: () => void;
  onBack: () => void; onPlay: () => void; onSeek: (value: number) => void;
  onSelectLayer: (id: string | null) => void; onAdd: () => void; onSetTool: (tool: string) => void; onSetContext: (id: string | null) => void;
  onUpdateLayer: (id: string, patch: Partial<EditorLayer>) => void; onDeleteLayer: (id: string) => void;
  onDuplicateLayer: (layer: EditorLayer) => void; onSaveElement: (layer: EditorLayer) => void; onSplit: () => void;
  onKeyframe: (layer: EditorLayer) => void; onSetEasing: (layer: EditorLayer, easing: EasingPreset) => void; onOpenEffects: () => void;
  onExport: () => void; onZoom: (value: number) => void; onPanelSlider: (value: number) => void; onRename: () => void; onNotify: (message: string) => void;
  contextLayerId: string | null;
}) {
  const [activeWorkspaceTab, setActiveWorkspaceTab] = useState<'timeline' | 'layers'>('timeline');
  const audioRefs = useRef(new Map<string, HTMLAudioElement>());
  const visibleLayers = layers.filter((layer) => layer.visible && time >= layer.start && time < layer.start + layer.duration);
  const stageLayers = [...visibleLayers].sort((a, b) => layers.indexOf(a) - layers.indexOf(b));
  const activeVideo = stageLayers.find((layer) => layer.kind === 'video' && layer.src);
  useEffect(() => {
    for (const layer of layers.filter((item) => item.kind === 'audio' && item.src)) {
      const audio = audioRefs.current.get(layer.id);
      if (!audio) continue;
      const active = layer.visible && time >= layer.start && time < layer.start + layer.duration;
      const target = Math.max(0, time - layer.start) * Math.max(.25, Math.min(4, layer.speed ?? 1));
      audio.volume = Math.max(0, Math.min(1, (layer.volume ?? 100) / 100));
      audio.playbackRate = Math.max(.25, Math.min(4, layer.speed ?? 1));
      if (active && Number.isFinite(audio.duration) && target < audio.duration) {
        if (Math.abs(audio.currentTime - target) > .45) audio.currentTime = target;
        if (isPlaying && audio.paused) void audio.play().catch(() => undefined);
        if (!isPlaying && !audio.paused) audio.pause();
      } else if (!audio.paused) audio.pause();
    }
  }, [layers, time, isPlaying]);
  const stageRatio = project.ratio === 'Custom' ? formattedRatio : project.ratio;

  const addEffect = () => {
    if (selectedLayer) onSetTool('Effects');
    else onNotify('Select a layer first');
  };

  return (
    <div className="editor-screen">
      <header className="editor-header">
        <button className="editor-back" onClick={onBack} aria-label="Back to projects"><ArrowLeft size={21} /></button>
        <button className="editor-project-name" onClick={onRename}><span className="eyebrow">EDITING</span><strong>{project.name}</strong><Pencil size={13} /></button>
        <div className="editor-header-actions"><button className="editor-head-tool" onClick={() => onNotify('Project settings are available from the canvas panel')} aria-label="Project settings"><Settings size={20} /></button><button className="editor-export-button" onClick={onExport}><ArrowDownToLine size={17} /><span>Export</span></button><button className="editor-more" onClick={() => onNotify('Saved locally')} aria-label="More options"><Ellipsis size={20} /></button></div>
      </header>

      <div className="editor-workspace">
        <section className="preview-column">
          <div className="stage-toolbar"><div className="stage-pill"><span className="live-dot" /> Preview <span className="stage-divider" /> {project.resolution} · {project.fps} fps</div><button className="zoom-chip" onClick={() => onZoom(canvasZoom >= 150 ? 75 : canvasZoom + 25)}><Maximize2 size={14} /> {canvasZoom}% <ChevronDown size={13} /></button></div>
          <div className="preview-stage-wrap">
            <div className="composition-stage" style={{ ...stageStyle, transform: `scale(${Math.min(canvasZoom, 125) / 100})` }}>
              <div className="composition-grid" />
              {stageLayers.map((layer) => {
                const animated = getLayerTransformAt(layer, time);
                const layerStyle: CSSProperties = { left: `${animated.x}%`, top: `${animated.y}%`, transform: `translate(-50%, -50%) rotate(${animated.rotation}deg) scale(${animated.scale / 100})`, opacity: animated.opacity / 100 };
                if ((layer.kind === 'image' || layer.kind === 'video') && layer.src) {
                  return <div className={`stage-media-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={layerStyle} key={layer.id} onClick={() => onSelectLayer(layer.id)}>
                    {layer.kind === 'video' ? <video ref={layer.id === activeVideo?.id ? videoRef : undefined} src={layer.src} muted={!isPlaying} playsInline loop className="stage-media-content" style={{ filter: cssFilterForEffects(layer.effects) }} /> : <img src={layer.src} alt={layer.name} className="stage-media-content" style={{ filter: cssFilterForEffects(layer.effects) }} />}
                    {selectedLayerId === layer.id && <SelectionHandles />}
                  </div>;
                }
                if (layer.kind === 'text') return <div className={`stage-text-layer ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={{ ...layerStyle, color: layer.color }} key={layer.id} onClick={() => onSelectLayer(layer.id)}>{layer.text}{selectedLayerId === layer.id && <SelectionHandles />}</div>;
                if (layer.kind === 'shape') return <div className={`stage-shape-layer shape-${(layer.shape ?? 'shape').toLowerCase().replace(/[^a-z]+/g, '-')} ${selectedLayerId === layer.id ? 'selected-layer' : ''}`} style={{ ...layerStyle, background: layer.color }} key={layer.id} onClick={() => onSelectLayer(layer.id)}>{selectedLayerId === layer.id && <SelectionHandles />}</div>;
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
          <div className="timeline-readout"><span className="timecode">{fmtTime(time)}</span><span className="timeline-duration">{fmtTime(project.duration)}</span></div>
          <div className="timeline-ruler"><div className="ruler-labels"><span>00:00</span><span>{fmtTime(project.duration / 2).slice(0, 5)}</span><span>{fmtTime(project.duration).slice(0, 5)}</span></div><div className="ruler-ticks" /></div>
          <div className="timeline-area">
            <div className="timeline-head-row"><div className="timeline-mode-tabs"><button className={activeWorkspaceTab === 'timeline' ? 'selected' : ''} onClick={() => setActiveWorkspaceTab('timeline')}>Timeline</button><button className={activeWorkspaceTab === 'layers' ? 'selected' : ''} onClick={() => setActiveWorkspaceTab('layers')}>Layers <span>{layers.length}</span></button></div><button className="timeline-add" onClick={onAdd}><Plus size={17} /></button></div>
            {layers.length ? (
              <div className="track-list">
                {layers.map((layer, index) => (
                  <div className={`track-row ${selectedLayerId === layer.id ? 'track-selected' : ''}`} key={layer.id} onClick={() => onSelectLayer(layer.id)}>
                    <div className="track-controls"><button className="track-eye" onClick={(event) => { event.stopPropagation(); onUpdateLayer(layer.id, { visible: !layer.visible }); }} aria-label={layer.visible ? 'Hide layer' : 'Show layer'}>{layer.visible ? <Eye size={14} /> : <EyeOff size={14} />}</button><span className="track-swatch" style={{ background: layer.color }}>{layer.kind === 'video' ? <Video size={12} /> : layer.kind === 'audio' ? <Music2 size={12} /> : layer.kind === 'text' ? <Type size={12} /> : layer.kind === 'image' ? <ImageIcon size={12} /> : null}</span></div>
                    <div className={`track-clip clip-${index % 4}`} style={{ marginLeft: `${Math.min(75, layer.start / Math.max(project.duration, 1) * 100)}%`, width: `${Math.min(100, Math.max(12, layer.duration / Math.max(project.duration, 1) * 100))}%` }}>
                      {layer.src && layer.kind !== 'audio' && <span className="clip-thumbnail" style={{ backgroundImage: `url(${layer.src})` }} />}
                      {layer.kind === 'audio' && <span className="waveform"><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /></span>}
                      {layer.keyframes?.map((frame, frameIndex) => <span className="keyframe-diamond" key={`${layer.id}-key-${frameIndex}`} style={{ left: `${Math.max(0, Math.min(100, (frame.time - layer.start) / Math.max(layer.duration, .01) * 100))}%` }} />)}
                      <span className="clip-name">{layer.name}</span>
                      {layer.effects.length > 0 && <span className="clip-effects"><Sparkles size={11} />{layer.effects.length}</span>}
                    </div>
                    <button className="track-more" onClick={(event) => { event.stopPropagation(); onSetContext(contextLayerId === layer.id ? null : layer.id); }} aria-label="Layer actions"><Ellipsis size={17} /></button>
                    {contextLayerId === layer.id && <div className="track-menu"><button onClick={(event) => { event.stopPropagation(); onSelectLayer(layer.id); onSetContext(null); onRename(); }}><Pencil size={14} /> Rename layer</button><button onClick={(event) => { event.stopPropagation(); onSaveElement(layer); onSetContext(null); }}><Layers size={14} /> Save as element</button><button onClick={(event) => { event.stopPropagation(); onDuplicateLayer(layer); onSetContext(null); }}><Copy size={14} /> Duplicate</button><button onClick={(event) => { event.stopPropagation(); onDeleteLayer(layer.id); }}><Trash2 size={14} /> Delete layer</button></div>}
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
            <button className={editorTool === 'Color & Fill' ? 'tool-active' : ''} onClick={() => onSetTool('Color & Fill')}><span><Circle size={21} /></span><small>Color & fill</small></button>
            <button className={editorTool === 'Effects' ? 'tool-active' : ''} onClick={addEffect}><span><Sparkles size={21} /></span><small>Effects</small></button>
            <button className={editorTool === 'Layer settings' ? 'tool-active' : ''} onClick={() => onSetTool('Layer settings')}><span><SlidersHorizontal size={21} /></span><small>Layer settings</small></button>
          </div>
          {editorTool && (
            <div className="inspector-panel">
              <div className="inspector-header"><div><span className="eyebrow">{selectedLayer?.name ?? 'PROJECT'}</span><strong>{editorTool}</strong></div><button className="icon-button" onClick={() => onSetTool(editorTool)} aria-label="Close panel"><X size={18} /></button></div>
              {editorTool === 'Move & Transform' && selectedLayer ? <>
                {(() => {
                  const frameAtPlayhead = selectedLayer.keyframes?.find((frame) => Math.abs(frame.time - time) < .04);
                  return <div className="keyframe-controls"><button className={frameAtPlayhead ? 'keyframe-toggle active' : 'keyframe-toggle'} onClick={() => onKeyframe(selectedLayer)}><Plus size={14} /> {frameAtPlayhead ? 'Remove keyframe' : 'Add keyframe'}</button><span>{selectedLayer.keyframes?.length ?? 0} keyframes</span>{Boolean(selectedLayer.keyframes?.length) && <><select aria-label="Keyframe easing" value={frameAtPlayhead?.easing ?? 'easeInOut'} disabled={!frameAtPlayhead} onChange={(event) => onSetEasing(selectedLayer, event.target.value as EasingPreset)}><option value="linear">Linear</option><option value="easeIn">Ease in</option><option value="easeOut">Ease out</option><option value="easeInOut">Ease in-out</option></select>{frameAtPlayhead && <svg className={`easing-mini-graph easing-${frameAtPlayhead.easing}`} viewBox="0 0 80 42" aria-label="Easing curve"><path d={frameAtPlayhead.easing === 'linear' ? 'M5 37 L75 5' : frameAtPlayhead.easing === 'easeIn' ? 'M5 37 C52 37 67 24 75 5' : frameAtPlayhead.easing === 'easeOut' ? 'M5 37 C13 18 28 5 75 5' : 'M5 37 C30 37 28 5 75 5'} /></svg>}</>}</div>;
                })()}
                <div className="transform-readouts"><label>X <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).x * 10)} onChange={(event) => onUpdateLayer(selectedLayer.id, { x: Number(event.target.value) / 10 })} /></label><label>Y <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).y * 10)} onChange={(event) => onUpdateLayer(selectedLayer.id, { y: Number(event.target.value) / 10 })} /></label><label>Scale <input type="number" value={Math.round(getLayerTransformAt(selectedLayer, time).scale)} onChange={(event) => onUpdateLayer(selectedLayer.id, { scale: Number(event.target.value) })} /></label></div>
                <label className="range-setting">Rotation <input type="range" min={-180} max={180} value={Math.round(getLayerTransformAt(selectedLayer, time).rotation)} onChange={(event) => onUpdateLayer(selectedLayer.id, { rotation: Number(event.target.value) })} /><span>{Math.round(getLayerTransformAt(selectedLayer, time).rotation)}°</span></label>
              </> : editorTool === 'Speed' && selectedLayer ? <label className="range-setting">Playback speed <input type="range" min={25} max={400} value={Math.round((selectedLayer.speed ?? 1) * 100)} onChange={(event) => onUpdateLayer(selectedLayer.id, { speed: Number(event.target.value) / 100 })} /><span>{(selectedLayer.speed ?? 1).toFixed(2)}×</span></label> : editorTool === 'Trim start' && selectedLayer ? <div className="trim-controls"><p>Clip range · {fmtTime(selectedLayer.start)} – {fmtTime(selectedLayer.start + selectedLayer.duration)}</p><label className="range-setting">Start <input type="range" min={0} max={Math.max(0, project.duration - selectedLayer.duration)} step={.01} value={selectedLayer.start} onChange={(event) => onUpdateLayer(selectedLayer.id, { start: Number(event.target.value) })} /><span>{fmtTime(selectedLayer.start)}</span></label><label className="range-setting">Length <input type="range" min={.1} max={Math.max(.1, project.duration - selectedLayer.start)} step={.01} value={Math.min(selectedLayer.duration, project.duration - selectedLayer.start)} onChange={(event) => onUpdateLayer(selectedLayer.id, { duration: Number(event.target.value) })} /><span>{fmtTime(selectedLayer.duration)}</span></label></div> : editorTool === 'Volume' && selectedLayer ? <>{selectedLayer.kind === 'audio' || selectedLayer.kind === 'video' ? <label className="range-setting">Volume <input type="range" min={0} max={100} value={selectedLayer.volume ?? 100} onChange={(event) => onUpdateLayer(selectedLayer.id, { volume: Number(event.target.value) })} /><span>{selectedLayer.volume ?? 100}%</span></label> : <div className="inspector-empty"><Info size={17} /> Volume is available on audio and video layers.</div>}</> : editorTool === 'Color & Fill' && selectedLayer ? <div className="inspector-swatches">{colorSwatches.map((color) => <button key={color} className={selectedLayer.color === color ? 'swatch-selected' : ''} style={{ background: color }} onClick={() => onUpdateLayer(selectedLayer.id, { color })} aria-label={`Set color ${color}`} />)}<label className="color-custom"><Pencil size={15} /><input type="color" value={selectedLayer.color} onChange={(event) => onUpdateLayer(selectedLayer.id, { color: event.target.value })} /></label></div> : editorTool === 'Effects' ? <div className="effect-inspector-list">{selectedLayer?.effects.length ? selectedLayer.effects.map((effect, index) => <div key={`${effect}-${index}`}><Sparkles size={15} /><span>{effect}</span><button onClick={() => selectedLayer && onUpdateLayer(selectedLayer.id, { effects: selectedLayer.effects.filter((_, itemIndex) => itemIndex !== index) })}><X size={15} /></button></div>) : <p>No effects yet. Browse the library to add a look.</p>}<button className="secondary-button" onClick={onOpenEffects}><Plus size={16} /> Browse effects</button></div> : selectedLayer ? <>
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

function SelectionHandles() {
  return <span className="selection-handles" aria-hidden="true"><i /><i /><i /><i /><i /><i /><i /><i /></span>;
}
