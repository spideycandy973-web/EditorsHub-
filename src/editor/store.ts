import { useSyncExternalStore } from 'react';
import type {
  Anim, Asset, BlendMode, EffectType, Layer, LayerStyle, PanelId, Project, ShapeKind,
} from './types';
import {
  anim, clearKeyframes, removeKeyframe as rmKeyframe, setKeyframe, snapTime, valueAt,
} from './animation';
import {
  applyPreset, createDemoProject, createEffect, createLayer, createProject,
  type CreateLayerOptions,
} from './defaults';
import { EFFECT_MAP, PROP_MAP, type Easing } from './types';
import { clamp, round, uid } from './utils';
import { MediaLibrary, createBuiltinAssets, rehydrateAsset } from './media';
import {
  deleteAsset, deleteProject, lastProjectId, listProjects,
  loadProject, loadAssets, saveProject, setLastProject,
  type ProjectMeta,
} from './storage';

type Listener = () => void;
export type ModalId = 'export' | 'xml' | 'projects' | 'settings' | 'help' | 'layerMenu' | 'newProject' | null;

const clone = <T,>(v: T): T => structuredClone(v);

class EditorStore {
  project: Project;
  assets: Record<string, Asset> = {};
  media: MediaLibrary;

  selectedLayerId: string | null = null;
  selectedPropertyKey = 'x';
  selectedEffectId: string | null = null;
  panel: PanelId = 'add';
  sheetOpen = typeof window === 'undefined' ? true : window.innerWidth >= 1024;
  modal: ModalId = null;
  toast: { id: number; text: string; kind: 'info' | 'ok' | 'warn' } | null = null;

  time = 0;
  playing = false;
  loop = true;
  autoKey = false;
  timelineZoom = 46;       // pixels per second
  previewZoom = 1;
  showGuides = false;
  showGrid = false;
  snapToFrame = true;

  version = 0;
  timeVersion = 0;

  private listeners = new Set<Listener>();
  private timeListeners = new Set<Listener>();
  private undoStack: Project[] = [];
  private redoStack: Project[] = [];
  private batching = false;
  private saveTimer: number | undefined;
  private clipboard: Layer | null = null;
  private toastTimer: number | undefined;

  constructor() {
    const id = lastProjectId();
    const stored = id ? loadProject(id) : null;
    this.project = stored && stored.layers ? stored : createDemoProject();
    // Start the demo composition at a frame where all its animated layers are visible.
    if (!stored) this.time = 1.5;
    this.media = new MediaLibrary(this.assets);
    const builtins = createBuiltinAssets();
    for (const a of builtins) this.assets[a.id] = a;
    this.selectedLayerId = this.project.layers[0]?.id ?? null;
  }

  /* ------------------------------ subscriptions ---------------------------- */
  subscribe = (cb: Listener): (() => void) => { this.listeners.add(cb); return () => this.listeners.delete(cb); };
  getVersion = (): number => this.version;
  subscribeTime = (cb: Listener): (() => void) => { this.timeListeners.add(cb); return () => this.timeListeners.delete(cb); };
  getTimeVersion = (): number => this.timeVersion;

  emit(): void { this.version++; this.listeners.forEach(l => l()); }
  emitTime(): void { this.timeVersion++; this.timeListeners.forEach(l => l()); }

  /* -------------------------------- history -------------------------------- */
  private pushUndo(): void {
    this.undoStack.push(clone(this.project));
    if (this.undoStack.length > 80) this.undoStack.shift();
    this.redoStack = [];
  }

  beginBatch(): void {
    if (this.batching) return;
    this.pushUndo();
    this.batching = true;
  }

  endBatch(): void {
    if (!this.batching) return;
    this.batching = false;
    this.project.updatedAt = Date.now();
    this.emit();
    this.scheduleSave();
  }

  mutate(fn: (p: Project) => void, record = true): void {
    if (record && !this.batching) this.pushUndo();
    fn(this.project);
    this.project.updatedAt = Date.now();
    this.emit();
    if (!this.batching) this.scheduleSave();
  }

  undo(): void {
    const prev = this.undoStack.pop();
    if (!prev) { this.notify('Nothing to undo', 'warn'); return; }
    this.redoStack.push(clone(this.project));
    this.project = prev;
    if (!this.selectedLayerId || !this.project.layers.some(l => l.id === this.selectedLayerId)) {
      this.selectedLayerId = this.project.layers[0]?.id ?? null;
    }
    this.emit(); this.scheduleSave();
  }

  redo(): void {
    const next = this.redoStack.pop();
    if (!next) { this.notify('Nothing to redo', 'warn'); return; }
    this.undoStack.push(clone(this.project));
    this.project = next;
    this.emit(); this.scheduleSave();
  }

  get canUndo(): boolean { return this.undoStack.length > 0; }
  get canRedo(): boolean { return this.redoStack.length > 0; }

  private scheduleSave(): void {
    window.clearTimeout(this.saveTimer);
    this.saveTimer = window.setTimeout(() => {
      try { saveProject(clone(this.project)); } catch { /* storage full */ }
    }, 600);
  }

  saveNow(): void { saveProject(clone(this.project)); }

  /* --------------------------------- UI state ------------------------------ */
  setPanel(p: PanelId): void { this.panel = p; this.sheetOpen = true; this.emit(); }
  toggleSheet(): void { this.sheetOpen = !this.sheetOpen; this.emit(); }
  setModal(m: ModalId): void { this.modal = m; this.emit(); }
  notify(text: string, kind: 'info' | 'ok' | 'warn' = 'info'): void {
    this.toast = { id: Date.now(), text, kind };
    this.emit();
    window.clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => { this.toast = null; this.emit(); }, 3200);
  }
  selectLayer(id: string | null): void {
    this.selectedLayerId = id;
    this.selectedEffectId = null;
    this.emit();
  }
  selectProperty(key: string): void { this.selectedPropertyKey = key; this.emit(); }
  selectEffect(id: string | null): void { this.selectedEffectId = id; this.emit(); }
  setAutoKey(v: boolean): void { this.autoKey = v; this.emit(); }
  setLoop(v: boolean): void { this.loop = v; this.emit(); }
  setSnap(v: boolean): void { this.snapToFrame = v; this.emit(); }
  setTimelineZoom(v: number): void { this.timelineZoom = clamp(v, 12, 220); this.emit(); }
  setPreviewZoom(v: number): void { this.previewZoom = clamp(v, 0.4, 3); this.emit(); }
  toggleGuides(): void { this.showGuides = !this.showGuides; this.emit(); }
  toggleGrid(): void { this.showGrid = !this.showGrid; this.emit(); }

  /* --------------------------------- playback ------------------------------ */
  setTime(t: number, snap = true): void {
    const n = clamp(t, 0, this.project.duration);
    const v = this.snapToFrame && snap ? snapTime(n, this.project.fps) : n;
    if (Math.abs(v - this.time) < 0.00001) return;
    this.time = v;
    this.emitTime();
  }

  setPlaying(v: boolean): void {
    if (v && this.time >= this.project.duration - 0.001) this.setTime(0, false);
    this.playing = v;
    if (!v) this.media.pauseAll();
    this.emit();
  }
  togglePlay(): void { this.setPlaying(!this.playing); }
  stepFrame(dir: number): void {
    this.setPlaying(false);
    this.setTime(this.time + dir / this.project.fps, false);
  }
  goStart(): void { this.setPlaying(false); this.setTime(0, false); }
  goEnd(): void { this.setPlaying(false); this.setTime(this.project.duration, false); }
  get localTime(): number {
    const l = this.selectedLayer;
    return l ? clamp(this.time - l.start, 0, l.duration) : 0;
  }

  /* ------------------------------ access helpers --------------------------- */
  get selectedLayer(): Layer | null {
    return this.project.layers.find(l => l.id === this.selectedLayerId) ?? null;
  }
  layer(id: string): Layer | undefined { return this.project.layers.find(l => l.id === id); }
  get selectedEffect() {
    const l = this.selectedLayer;
    return l?.effects.find(e => e.id === this.selectedEffectId) ?? null;
  }
  getLayerProp(layer: Layer, key: string): number {
    return valueAt(layer.props[key], this.time - layer.start, PROP_MAP[key]?.default ?? 0);
  }
  getAnim(layer: Layer, key: string): Anim { return layer.props[key] ?? anim(PROP_MAP[key]?.default ?? 0); }

  /* ------------------------------- assets ---------------------------------- */
  addAsset(asset: Asset): void {
    this.assets[asset.id] = asset;
    this.media.assets = this.assets;
    this.emit();
  }
  addAssets(list: Asset[]): void {
    for (const a of list) this.assets[a.id] = a;
    this.media.assets = this.assets;
    this.emit();
  }

  async hydrateStoredAssets(): Promise<void> {
    const records = await loadAssets();
    for (const rec of records) {
      if (this.assets[rec.id]) continue;
      try {
        const asset = rehydrateAsset(rec);
        if (asset.type === 'font' && asset.fontFamily && asset.blob) {
          try {
            const face = new FontFace(asset.fontFamily, await asset.blob.arrayBuffer());
            await face.load();
            document.fonts.add(face);
          } catch { /* unsupported font */ }
        }
        this.assets[rec.id] = asset;
      } catch (e) { console.warn('Asset restore failed', e); }
    }
    this.media.assets = this.assets;
    this.emit();
  }

  /* --------------------------- layer operations ---------------------------- */
  addLayer(type: Layer['type'], opts: CreateLayerOptions = {}): string {
    const start = opts.start ?? this.time;
    const maxDur = Math.max(0.5, this.project.duration - start);
    const layer = createLayer(type, {
      ...opts,
      width: this.project.width, height: this.project.height,
      duration: Math.min(opts.duration ?? 5, maxDur),
      start,
    });
    this.mutate(p => { p.layers.unshift(layer); });
    this.selectedLayerId = layer.id;
    this.selectedEffectId = null;
    this.panel = type === 'shape' || type === 'text' || type === 'solid' ? 'edit' : this.panel;
    this.emit();
    this.notify(`${layer.name} added`, 'ok');
    return layer.id;
  }

  addMediaLayer(assetId: string): string | null {
    const asset = this.assets[assetId];
    if (!asset || asset.type === 'font') return null;
    const type: Layer['type'] = asset.type === 'video' ? 'video' : asset.type === 'audio' ? 'audio' : 'image';
    const suggested = asset.duration > 0 && asset.type !== 'image'
      ? Math.min(asset.duration, Math.max(0.5, this.project.duration - this.time))
      : Math.min(5, Math.max(0.5, this.project.duration - this.time));
    const id = this.addLayer(type, { sourceId: assetId, name: asset.name, duration: suggested });
    if (asset.type === 'video') {
      this.mutate(() => {
        const l = this.layer(id);
        if (l) l.style.audio = true;
      }, false);
    }
    return id;
  }

  deleteLayer(id: string): void {
    const idx = this.project.layers.findIndex(l => l.id === id);
    if (idx < 0) return;
    this.mutate(p => {
      p.layers = p.layers.filter(l => l.id !== id);
      for (const l of p.layers) if (l.parentId === id) l.parentId = null;
    });
    if (this.selectedLayerId === id) this.selectedLayerId = this.project.layers[Math.min(idx, this.project.layers.length - 1)]?.id ?? null;
    this.emit();
    this.notify('Layer deleted');
  }

  duplicateLayer(id: string): void {
    const src = this.layer(id);
    if (!src) return;
    const copy = clone(src);
    copy.id = uid('layer');
    copy.name = `${src.name} copy`;
    copy.effects = copy.effects.map(e => ({ ...e, id: uid('fx') }));
    for (const a of Object.values(copy.props)) a.keyframes = a.keyframes.map(k => ({ ...k, id: uid('kf') }));
    this.mutate(p => {
      const i = p.layers.findIndex(l => l.id === id);
      p.layers.splice(i, 0, copy);
    });
    this.selectedLayerId = copy.id;
    this.emit();
    this.notify('Layer duplicated', 'ok');
  }

  splitLayer(id: string, at?: number): void {
    const src = this.layer(id);
    if (!src) return;
    const t = clamp(at ?? this.time, src.start + 0.05, src.start + src.duration - 0.05);
    if (t <= src.start || t >= src.start + src.duration) { this.notify('Playhead is outside the layer', 'warn'); return; }
    const first = clone(src);
    const second = clone(src);
    second.id = uid('layer');
    second.name = `${src.name} (2)`;
    second.start = t;
    second.duration = src.start + src.duration - t;
    second.inPoint = src.inPoint + (t - src.start) * (src.props.speed?.value ?? 1);
    first.duration = t - src.start;
    // shift keyframes into layer-local space for the second half
    const shift = t - src.start;
    for (const key of Object.keys(second.props)) {
      const a = second.props[key];
      a.keyframes = a.keyframes.filter(k => k.time >= shift).map(k => ({ ...k, id: uid('kf'), time: round(k.time - shift, 4) }));
      if (a.keyframes.length === 0) a.value = valueAt(src.props[key] ?? a, shift);
    }
    for (const e of second.effects) {
      e.id = uid('fx');
      for (const a of Object.values(e.params)) {
        a.keyframes = a.keyframes.filter(k => k.time >= shift).map(k => ({ ...k, id: uid('kf'), time: round(k.time - shift, 4) }));
      }
    }
    first.effects = first.effects.map(e => ({ ...e, id: uid('fx') }));
    this.mutate(p => {
      const i = p.layers.findIndex(l => l.id === id);
      p.layers.splice(i, 1, first, second);
    });
    this.selectedLayerId = second.id;
    this.emit();
    this.notify('Layer split at playhead', 'ok');
  }

  moveLayer(id: string, dir: -1 | 1): void {
    this.mutate(p => {
      const i = p.layers.findIndex(l => l.id === id);
      const j = i - dir; // index 0 = top: dir 1 means move up (towards 0)
      if (i < 0 || j < 0 || j >= p.layers.length) return;
      const [l] = p.layers.splice(i, 1);
      p.layers.splice(j, 0, l);
    });
  }

  reorderLayer(id: string, toIndex: number): void {
    this.mutate(p => {
      const i = p.layers.findIndex(l => l.id === id);
      if (i < 0) return;
      const [l] = p.layers.splice(i, 1);
      p.layers.splice(clamp(toIndex, 0, p.layers.length), 0, l);
    });
  }

  copyLayer(): void {
    const l = this.selectedLayer;
    if (!l) return;
    this.clipboard = clone(l);
    this.notify('Layer copied', 'ok');
  }

  pasteLayer(): void {
    if (!this.clipboard) { this.notify('Nothing copied', 'warn'); return; }
    const copy = clone(this.clipboard);
    copy.id = uid('layer');
    copy.start = clamp(this.time, 0, Math.max(0, this.project.duration - 0.5));
    copy.name = `${copy.name} paste`;
    copy.effects = copy.effects.map(e => ({ ...e, id: uid('fx') }));
    for (const a of Object.values(copy.props)) a.keyframes = a.keyframes.map(k => ({ ...k, id: uid('kf') }));
    this.mutate(p => p.layers.unshift(copy));
    this.selectedLayerId = copy.id;
    this.emit();
    this.notify('Layer pasted', 'ok');
  }

  patchLayer(id: string, patch: Partial<Layer>): void {
    this.mutate(() => {
      const l = this.layer(id);
      if (l) Object.assign(l, patch);
    });
  }

  renameLayer(id: string, name: string): void { this.patchLayer(id, { name: name || 'Layer' }); }
  toggleVisible(id: string): void {
    const l = this.layer(id); if (l) this.patchLayer(id, { visible: !l.visible });
  }
  toggleLock(id: string): void {
    const l = this.layer(id); if (l) this.patchLayer(id, { locked: !l.locked });
  }
  setBlend(id: string, blend: BlendMode): void { this.patchLayer(id, { blend }); }
  setParent(id: string, parentId: string | null): void {
    if (id === parentId) return;
    // prevent cycles
    let p = parentId;
    while (p) {
      if (p === id) { this.notify('Circular parent link', 'warn'); return; }
      p = this.layer(p)?.parentId ?? null;
    }
    this.patchLayer(id, { parentId });
  }
  setTiming(id: string, patch: Partial<Pick<Layer, 'start' | 'duration' | 'inPoint'>>, record = true): void {
    this.mutate(() => {
      const l = this.layer(id);
      if (!l) return;
      if (patch.start !== undefined) l.start = clamp(patch.start, 0, Math.max(0, this.project.duration - 0.1));
      if (patch.duration !== undefined) l.duration = clamp(patch.duration, 0.1, this.project.duration);
      if (patch.inPoint !== undefined) l.inPoint = Math.max(0, patch.inPoint);
    }, record);
  }
  setStyle(id: string, patch: Partial<LayerStyle>): void {
    this.mutate(() => {
      const l = this.layer(id);
      if (l) Object.assign(l.style, patch);
    });
  }
  setShape(id: string, shape: ShapeKind): void { this.setStyle(id, { shape }); }
  setMask(id: string, patch: Partial<Layer['mask']>): void {
    this.mutate(() => {
      const l = this.layer(id);
      if (l) Object.assign(l.mask, patch);
    });
  }

  /* ------------------------------ properties ------------------------------- */
  private animTarget(layerId: string, key: string): { a: Anim; local: number } | null {
    const l = this.layer(layerId);
    if (!l) return null;
    const a = l.props[key] ?? anim(PROP_MAP[key]?.default ?? 0);
    return { a, local: clamp(this.time - l.start, 0, l.duration) };
  }

  setProp(layerId: string, key: string, value: number, record = true): void {
    const t = this.animTarget(layerId, key);
    if (!t) return;
    const v = round(value, 4);
    const animated = this.autoKey || t.a.keyframes.length > 0;
    this.mutate(() => {
      const l = this.layer(layerId)!;
      const a = l.props[key] ?? anim(PROP_MAP[key]?.default ?? 0);
      if (animated) l.props[key] = setKeyframe(a, t.local, v, lastEasing(a, t.local));
      else l.props[key] = { ...a, value: v };
    }, record);
  }

  addKeyframe(layerId: string, key: string, at?: number): void {
    const t = this.animTarget(layerId, key);
    if (!t) return;
    this.mutate(() => {
      const l = this.layer(layerId)!;
      const a = l.props[key] ?? anim(PROP_MAP[key]?.default ?? 0);
      const time = at ?? t.local;
      l.props[key] = setKeyframe(a, time, valueAt(a, time, a.value), lastEasing(a, time));
    });
  }
  removeKeyframe(layerId: string, key: string, kfId: string): void {
    this.mutate(() => {
      const l = this.layer(layerId); if (!l) return;
      l.props[key] = rmKeyframe(l.props[key] ?? anim(0), kfId);
    });
  }
  clearPropertyKeyframes(layerId: string, key: string): void {
    const t = this.animTarget(layerId, key);
    if (!t) return;
    this.mutate(() => {
      const l = this.layer(layerId)!;
      l.props[key] = clearKeyframes(l.props[key] ?? anim(0), t.local);
    });
  }
  updateKeyframe(layerId: string, key: string, kfId: string, patch: Partial<{ time: number; value: number; easing: Easing }>): void {
    this.mutate(() => {
      const l = this.layer(layerId); if (!l || !l.props[key]) return;
      l.props[key] = {
        ...l.props[key],
        keyframes: l.props[key].keyframes.map(k => k.id === kfId ? { ...k, ...patch } : k).sort((a, b) => a.time - b.time),
      };
    });
  }

  /* -------------------------------- effects -------------------------------- */
  addEffect(type: EffectType): void {
    const l = this.selectedLayer;
    if (!l) { this.notify('Select a layer first', 'warn'); return; }
    const fx = createEffect(type);
    this.mutate(() => { this.layer(l.id)?.effects.push(fx); });
    this.selectedEffectId = fx.id;
    this.emit();
    this.notify(`${EFFECT_MAP[type]?.label ?? type} added`, 'ok');
  }
  removeEffect(effectId: string): void {
    const l = this.selectedLayer; if (!l) return;
    this.mutate(() => {
      const layer = this.layer(l.id);
      if (layer) layer.effects = layer.effects.filter(e => e.id !== effectId);
    });
    if (this.selectedEffectId === effectId) this.selectedEffectId = null;
    this.emit();
  }
  toggleEffect(effectId: string): void {
    const l = this.selectedLayer; if (!l) return;
    this.mutate(() => {
      const e = this.layer(l.id)?.effects.find(x => x.id === effectId);
      if (e) e.enabled = !e.enabled;
    });
  }
  moveEffect(effectId: string, dir: -1 | 1): void {
    const l = this.selectedLayer; if (!l) return;
    this.mutate(() => {
      const layer = this.layer(l.id); if (!layer) return;
      const i = layer.effects.findIndex(e => e.id === effectId);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= layer.effects.length) return;
      const [e] = layer.effects.splice(i, 1);
      layer.effects.splice(j, 0, e);
    });
  }
  setEffectParam(effectId: string, key: string, value: number, record = true): void {
    const l = this.selectedLayer; if (!l) return;
    const local = clamp(this.time - l.start, 0, l.duration);
    this.mutate(() => {
      const e = this.layer(l.id)?.effects.find(x => x.id === effectId);
      if (!e) return;
      const a = e.params[key] ?? anim(EFFECT_MAP[e.type]?.params.find(p => p.key === key)?.default ?? 0);
      const animated = this.autoKey || a.keyframes.length > 0;
      e.params[key] = animated
        ? setKeyframe(a, local, round(value, 4), lastEasing(a, local))
        : { ...a, value: round(value, 4) };
    }, record);
  }
  addEffectKeyframe(effectId: string, key: string): void {
    const l = this.selectedLayer; if (!l) return;
    const local = clamp(this.time - l.start, 0, l.duration);
    this.mutate(() => {
      const e = this.layer(l.id)?.effects.find(x => x.id === effectId);
      if (!e) return;
      const a = e.params[key] ?? anim(EFFECT_MAP[e.type]?.params.find(p => p.key === key)?.default ?? 0);
      e.params[key] = setKeyframe(a, local, valueAt(a, local, a.value), lastEasing(a, local));
    });
  }
  updateEffectKeyframe(effectId: string, key: string, kfId: string, patch: Partial<{ time: number; value: number; easing: Easing }>): void {
    const l = this.selectedLayer; if (!l) return;
    this.mutate(() => {
      const e = this.layer(l.id)?.effects.find(x => x.id === effectId);
      if (!e || !e.params[key]) return;
      e.params[key] = {
        ...e.params[key],
        keyframes: e.params[key].keyframes.map(k => k.id === kfId ? { ...k, ...patch } : k).sort((a, b) => a.time - b.time),
      };
    });
  }
  removeEffectKeyframe(effectId: string, key: string, kfId: string): void {
    const l = this.selectedLayer; if (!l) return;
    this.mutate(() => {
      const e = this.layer(l.id)?.effects.find(x => x.id === effectId);
      if (e && e.params[key]) e.params[key] = rmKeyframe(e.params[key], kfId);
    });
  }
  getEffectValue(effectId: string, key: string): number {
    const l = this.selectedLayer;
    const e = l?.effects.find(x => x.id === effectId);
    const def = e ? EFFECT_MAP[e.type]?.params.find(p => p.key === key)?.default ?? 0 : 0;
    return valueAt(e?.params[key], l ? clamp(this.time - l.start, 0, l.duration) : 0, def);
  }

  /* -------------------------------- presets -------------------------------- */
  applyMotionPreset(presetId: string): void {
    const l = this.selectedLayer;
    if (!l) { this.notify('Select a layer first', 'warn'); return; }
    this.mutate(() => {
      const layer = this.layer(l.id);
      if (layer) applyPreset(layer, presetId, layer.duration);
    });
    this.setTime(l.start + 0.01);
    this.notify('Animation preset applied', 'ok');
  }

  /* ------------------------------- projects -------------------------------- */
  setProjectSettings(patch: Partial<Pick<Project, 'name' | 'width' | 'height' | 'fps' | 'duration' | 'background'>>): void {
    this.mutate(p => {
      Object.assign(p, patch);
      p.width = clamp(Math.round(p.width), 16, 4096);
      p.height = clamp(Math.round(p.height), 16, 4096);
      p.fps = clamp(Math.round(p.fps), 1, 120);
      p.duration = clamp(p.duration, 0.5, 600);
      p.background = patch.background ?? p.background;
    });
    this.setTime(Math.min(this.time, this.project.duration));
  }

  newProject(width = 1080, height = 1920, blank = true): void {
    this.pushUndo();
    this.project = blank ? createProject('Untitled Project', width, height) : createDemoProject();
    this.selectedLayerId = this.project.layers[0]?.id ?? null;
    this.time = 0; this.playing = false;
    this.media.pauseAll();
    saveProject(clone(this.project));
    this.emit(); this.emitTime();
    this.notify('New project created', 'ok');
  }

  openProject(id: string): void {
    const p = loadProject(id);
    if (!p) { this.notify('Project not found', 'warn'); return; }
    this.pushUndo();
    this.project = p;
    this.selectedLayerId = p.layers[0]?.id ?? null;
    this.time = 0; this.playing = false;
    this.media.pauseAll();
    setLastProject(id);
    this.emit(); this.emitTime();
    this.notify(`Opened “${p.name}”`, 'ok');
  }

  replaceProject(project: Project, assets: Asset[] = []): void {
    this.pushUndo();
    this.addAssets(assets);
    this.project = project;
    this.selectedLayerId = project.layers[0]?.id ?? null;
    this.selectedEffectId = null;
    this.time = 0; this.playing = false;
    this.media.pauseAll();
    saveProject(clone(project));
    this.emit(); this.emitTime();
  }

  /** Insert layers parsed from an XML preset into the current project. */
  addImportedLayers(layers: Layer[], assets: Asset[] = []): void {
    this.addAssets(assets);
    const idMap = new Map<string, string>();
    const clones = layers.map(l => {
      const c = clone(l);
      idMap.set(l.id, c.id = uid('layer'));
      return c;
    });
    for (const c of clones) {
      if (c.parentId) c.parentId = idMap.get(c.parentId) ?? null;
      c.effects = c.effects.map(e => ({ ...e, id: uid('fx') }));
      for (const a of Object.values(c.props)) a.keyframes = a.keyframes.map(k => ({ ...k, id: uid('kf') }));
    }
    this.mutate(p => { p.layers.unshift(...clones); });
    this.selectedLayerId = clones[0]?.id ?? this.selectedLayerId;
    this.emit();
    this.notify(`${clones.length} layer${clones.length === 1 ? '' : 's'} imported`, 'ok');
  }

  saveAs(name: string): void {
    const p = clone(this.project);
    p.id = uid('proj');
    p.name = name.trim() || p.name;
    p.createdAt = Date.now();
    p.updatedAt = Date.now();
    this.project = p;
    saveProject(clone(p));
    this.emit();
    this.notify(`Saved as “${p.name}”`, 'ok');
  }

  listProjects(): ProjectMeta[] { return listProjects(); }
  deleteProject(id: string): void {
    deleteProject(id);
    this.emit();
    this.notify('Project deleted');
  }

  async removeAsset(id: string): Promise<void> {
    const used = this.project.layers.some(l => l.sourceId === id);
    if (used) { this.notify('Asset is used by a layer', 'warn'); return; }
    const a = this.assets[id];
    if (a?.url.startsWith('blob:')) URL.revokeObjectURL(a.url);
    delete this.assets[id];
    this.media.assets = this.assets;
    await deleteAsset(id);
    this.emit();
  }
}

function lastEasing(a: Anim, time: number): Easing {
  const before = [...a.keyframes].filter(k => k.time <= time).sort((x, y) => y.time - x.time)[0];
  return before?.easing ?? 'easeInOut';
}

export const editor = new EditorStore();

/** Re-render on any editor/project/selection change. */
export function useEditor(): EditorStore {
  useSyncExternalStore(editor.subscribe, editor.getVersion, editor.getVersion);
  return editor;
}

/** Re-render on playhead changes only (cheap, used by the timeline/player). */
export function usePlayhead(): number {
  useSyncExternalStore(editor.subscribeTime, editor.getTimeVersion, editor.getTimeVersion);
  return editor.time;
}
