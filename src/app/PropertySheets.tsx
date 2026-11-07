import React, { useEffect, useRef, useState } from 'react';
import { AlignCenter, AlignLeft, AlignRight, ArrowLeft, ArrowLeftRight, Blend, ChevronLeft, ChevronRight, Circle, Diamond, Droplets, Expand, Eye, Grid2X2, Layers, Link2, MoreHorizontal, Move, Palette, RotateCcw, RotateCw, Ruler, Sparkles, Square, Star, Type, Volume2, VolumeX, Waves, ZoomIn } from 'lucide-react';
import { useEditor } from '../editor/store';
import {
  BLEND_LABELS, EFFECT_DEFS, SHAPE_PICKER,
  type Easing, type Layer,
} from '../editor/types';
import { MOTION_PRESETS, BLEND_MODES } from '../editor/defaults';
import { useUI, type PropertyId } from './context';
import { Btn, ColorInput, IBtn, NumField, RulerSlider, Select, Seg, Slider, Swatches, PALETTE } from './widgets';
import { ShapeGlyph } from './ShapeGlyph';
import { saveSelectedLayerPreset } from './lib';

export const PROPERTY_TITLES: Record<PropertyId, string> = {
  move: 'Move & Transform', blend: 'Blending & Opacity', color: 'Color & Fill',
  border: 'Border & Shadow', editShape: 'Edit Shape', presets: 'Presets',
  effects: 'Effects', camera: 'Camera Options',
};

const TILE_ICONS: Record<PropertyId, React.ReactNode> = {
  move: <Move size={24} strokeWidth={1.5} />,
  camera: <ZoomIn size={24} strokeWidth={1.5} />,
  presets: <Star size={24} strokeWidth={1.5} />,
  effects: <Sparkles size={24} strokeWidth={1.5} />,
  blend: <Blend size={24} strokeWidth={1.5} />,
  color: <Palette size={24} strokeWidth={1.5} />,
  border: <Square size={24} strokeWidth={1.5} />,
  editShape: <Ruler size={24} strokeWidth={1.5} />,
};

/** Reference layout: three wide tiles, then four narrow ones. A camera layer
 *  instead shows its own four-tile row (Move / Camera / Presets / Effects). */
function tilesFor(camera: boolean): PropertyId[] {
  return camera
    ? ['move', 'camera', 'presets', 'effects']
    : ['color', 'border', 'blend', 'move', 'editShape', 'presets', 'effects'];
}

/* ----------------------------- root properties ---------------------------- */
function PropertiesRoot() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const [muted, setMuted] = React.useState(false);
  const resetTransform = () => {
    if (!layer) return;
    e.beginBatch();
    for (const [k, v] of [['x', 0], ['y', 0], ['z', 0], ['rotation', 0], ['skewX', 0], ['skewY', 0], ['scaleX', 100], ['scaleY', 100]] as const) {
      e.setProp(layer.id, k, v, false);
    }
    e.endBatch();
  };
  const align = (x: number) => { if (layer) e.setProp(layer.id, 'x', x); };
  const toggleMute = () => {
    const next = !muted;
    for (const el of e.media.allElements()) el.muted = next;
    setMuted(next);
  };
  return (
    <div className="um-prop-root">
      <div className="um-quick-tools">
        <IBtn title="Reset transform" onClick={resetTransform}><RotateCcw size={18} /></IBtn>
        <IBtn title="Align left" onClick={() => align(-e.project.width / 4)}><AlignLeft size={18} /></IBtn>
        <IBtn title="Align centre" onClick={() => align(0)}><AlignCenter size={18} /></IBtn>
        <IBtn title="Align right" onClick={() => align(e.project.width / 4)}><AlignRight size={18} /></IBtn>
        <IBtn title={muted ? 'Unmute media' : 'Mute media'} active={muted} onClick={toggleMute}>
          {muted ? <VolumeX size={18} /> : <Volume2 size={18} />}
        </IBtn>
      </div>
      {!layer && <p className="um-hint center">Select a layer to edit its properties.</p>}
      {layer && (
        <div className={`um-prop-tiles ${layer?.type === 'camera' ? 'camera' : ''}`}>
          {tilesFor(layer?.type === 'camera').map(id => (
            <button type="button" key={id} className="um-prop-tile" onClick={() => ui.openProperty(id)}>
              {TILE_ICONS[id]}<span>{PROPERTY_TITLES[id]}</span>
              {id === 'presets' && <em className="um-tile-new">NEW</em>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/* ------------------------------ property detail --------------------------- */
function LeftRail({ sub, setSub }: { sub: number; setSub: (n: number) => void }) {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const addKeyframe = () => {
    if (!layer) { e.notify('Select a layer first', 'warn'); return; }
    e.addKeyframe(layer.id, ui.activePropKey);
    e.notify(`Keyframe on ${ui.activePropKey}`, 'ok');
  };
  if (ui.property === 'editShape') return (
    <div className="um-rail um-rail-left um-edit-shape-rail">
      <IBtn title="Back to properties" onClick={() => ui.setSheet('properties')}><ArrowLeft size={20} strokeWidth={1.7} /></IBtn>
      <IBtn title="Add keyframe" onClick={addKeyframe}><Diamond size={19} strokeWidth={1.7} /></IBtn>
      <IBtn title="Shape picker" active={sub === 0} onClick={() => setSub(0)}><Grid2X2 size={19} strokeWidth={1.7} /></IBtn>
      <IBtn title="Dimensions" active={sub === 1} onClick={() => setSub(1)}><Ruler size={19} strokeWidth={1.7} /></IBtn>
      <IBtn title="Points & radius" active={sub === 2} onClick={() => setSub(2)}><Star size={19} strokeWidth={1.7} /></IBtn>
    </div>
  );
  return (
    <div className="um-rail um-rail-left">
      <IBtn title="Back to properties" onClick={() => ui.setSheet('properties')}><ArrowLeft size={20} strokeWidth={1.7} /></IBtn>
      <IBtn title="Add keyframe" onClick={addKeyframe}><Diamond size={19} strokeWidth={1.7} /></IBtn>
      <IBtn title="Blending & Opacity" onClick={() => ui.openProperty('blend')}><span className="um-glyph dashed-square">▧</span></IBtn>
      <IBtn title="Edit Shape" onClick={() => ui.openProperty('editShape')}><Grid2X2 size={19} strokeWidth={1.7} /></IBtn>
      <IBtn title="Layer options" onClick={() => ui.setSheet('overflow')}><MoreHorizontal size={20} /></IBtn>
    </div>
  );
}

function useProp() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const get = (key: string) => layer ? e.getLayerProp(layer, key) : 0;
  const set = (key: string, v: number) => {
    if (!layer) return;
    if (ui.activePropKey !== key) ui.setActivePropKey(key);
    e.setProp(layer.id, key, v);
  };
  return { layer, get, set };
}

/* ------------------------------ move & transform -------------------------- */
function XyPad({ x, y, onChange }: { x: number; y: number; onChange: (x: number, y: number) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const drag = useRef(false);
  const handle = (ev: React.PointerEvent<HTMLDivElement>) => {
    const r = ref.current!.getBoundingClientRect();
    onChange(
      Math.round(((ev.clientX - r.left) / r.width - 0.5) * 2000),
      Math.round(((ev.clientY - r.top) / r.height - 0.5) * 2000),
    );
  };
  return (
    <div className="um-xypad" ref={ref}
      onPointerDown={ev => { drag.current = true; ev.currentTarget.setPointerCapture(ev.pointerId); handle(ev); }}
      onPointerMove={ev => { if (drag.current) handle(ev); }}
      onPointerUp={() => { drag.current = false; }}>
      <i className="um-crop" />
      <i className="um-corner top-right" /><i className="um-corner bottom-left" />
      <span className="um-swipe-hint">Swipe here to move layer</span>
    </div>
  );
}

function RotationDial({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const drag = useRef(false);
  const handle = (ev: React.PointerEvent<HTMLDivElement>) => {
    const r = ref.current!.getBoundingClientRect();
    const a = Math.atan2(ev.clientY - (r.top + r.height / 2), ev.clientX - (r.left + r.width / 2));
    onChange(Math.round(a * 180 / Math.PI));
  };
  const rad = (value * Math.PI) / 180;
  const R = 84;
  return (
    <div className="um-dial-wrap">
      <div className="um-dial" ref={ref}
        onPointerDown={ev => { drag.current = true; ev.currentTarget.setPointerCapture(ev.pointerId); handle(ev); }}
        onPointerMove={ev => { if (drag.current) handle(ev); }}
        onPointerUp={() => { drag.current = false; }}>
        <span className="um-dial-value">{value}°</span>
        <i style={{ left: `calc(50% + ${Math.cos(rad) * R}px)`, top: `calc(50% + ${Math.sin(rad) * R}px)` }} />
      </div>
    </div>
  );
}

type CurvePoint = { x: number; y: number };
type CurveControls = { p1: CurvePoint; p2: CurvePoint };

const NAMED_CURVES: Record<string, CurveControls> = {
  linear: { p1: { x: 0.25, y: 0.25 }, p2: { x: 0.75, y: 0.75 } },
  hold: { p1: { x: 0.02, y: 0 }, p2: { x: 0.04, y: 0 } },
  easeIn: { p1: { x: 0.42, y: 0 }, p2: { x: 1, y: 1 } },
  easeOut: { p1: { x: 0, y: 0 }, p2: { x: 0.58, y: 1 } },
  easeInOut: { p1: { x: 0.42, y: 0 }, p2: { x: 0.58, y: 1 } },
  easeInBack: { p1: { x: 0.6, y: -0.28 }, p2: { x: 0.735, y: 0.045 } },
  easeOutBack: { p1: { x: 0.175, y: 0.885 }, p2: { x: 0.32, y: 1.275 } },
  elastic: { p1: { x: 0.68, y: -0.55 }, p2: { x: 0.265, y: 1.55 } },
  bounce: { p1: { x: 0.34, y: 1.15 }, p2: { x: 0.64, y: 1 } },
  spring: { p1: { x: 0.34, y: 1.35 }, p2: { x: 0.64, y: 1 } },
};

function controlsForEasing(easing: Easing): CurveControls {
  const match = String(easing).match(/^cubic\((-?[\d.]+),(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)\)$/);
  if (match) return {
    p1: { x: Number(match[1]), y: Number(match[2]) },
    p2: { x: Number(match[3]), y: Number(match[4]) },
  };
  return NAMED_CURVES[String(easing)] ?? NAMED_CURVES.linear;
}

function easingAtCurrentTime(e: ReturnType<typeof useEditor>, layer: Layer | null, propKey: string): Easing {
  if (!layer) return 'linear';
  const frames = [...e.getAnim(layer, propKey).keyframes].sort((a, b) => a.time - b.time);
  return frames.filter(k => k.time <= e.localTime).pop()?.easing ?? frames[0]?.easing ?? 'linear';
}

function applyEasingToLayer(e: ReturnType<typeof useEditor>, layer: Layer | null, propKey: string, easing: Easing) {
  if (!layer) return;
  let frames = e.getAnim(layer, propKey).keyframes;
  if (!frames.length) { e.addKeyframe(layer.id, propKey); frames = e.getAnim(layer, propKey).keyframes; }
  const frame = [...frames].sort((a, b) => a.time - b.time).filter(k => k.time <= e.localTime).pop() ?? frames[0];
  if (frame) e.updateKeyframe(layer.id, propKey, frame.id, { easing });
}

function reverseEasing(easing: Easing): Easing {
  const match = String(easing).match(/^cubic\((-?[\d.]+),(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)\)$/);
  if (match) return `cubic(${(1 - Number(match[3])).toFixed(2)},${(1 - Number(match[4])).toFixed(2)},${(1 - Number(match[1])).toFixed(2)},${(1 - Number(match[2])).toFixed(2)})` as Easing;
  const flip: Record<string, Easing> = {
    linear: 'linear', hold: 'hold', easeIn: 'easeOut', easeOut: 'easeIn', easeInOut: 'easeInOut',
    easeInBack: 'easeOutBack', easeOutBack: 'easeInBack', elastic: 'elastic', bounce: 'bounce', spring: 'spring',
  };
  return flip[String(easing)] ?? 'linear';
}

const EASING_PRESETS: { label: string; easing: Easing; path: string }[] = [
  { label: 'Linear', easing: 'linear', path: 'M5 23 L23 5' },
  { label: 'Ease in', easing: 'easeIn', path: 'M5 23 C13 23 18 17 23 5' },
  { label: 'Ease out', easing: 'easeOut', path: 'M5 23 C10 11 15 5 23 5' },
  { label: 'Ease in out', easing: 'easeInOut', path: 'M5 23 C8 22 9 18 14 14 C19 10 20 6 23 5' },
];
const EASING_MODES: { label: string; easing: Easing; path: string; kind: 'curve' | 'spring' | 'steps' }[] = [
  { label: 'Cubic Bezier', easing: 'cubic(0.25,0.25,0.75,0.75)', path: 'M5 23 C9 20 19 8 23 5', kind: 'curve' },
  { label: 'Spring', easing: 'spring', path: 'M5 23 C12 23 10 3 16 8 C22 14 17 19 23 5', kind: 'spring' },
  { label: 'Hold', easing: 'hold', path: 'M5 23 L5 16 L14 16 L14 9 L23 9 L23 5', kind: 'steps' },
];

function EasingCurveIcon({ path, active, kind = 'curve' }: { path: string; active: boolean; kind?: 'curve' | 'spring' | 'steps' }) {
  return (
    <svg viewBox="0 0 28 28" className={`um-easing-icon ${active ? 'on' : ''}`} aria-hidden="true">
      <rect x="3" y="3" width="22" height="22" rx="1" />
      <path d={path} />
      {kind === 'curve' && <><circle cx="5" cy="23" r="1.4" /><circle cx="23" cy="5" r="1.4" /></>}
    </svg>
  );
}

function EasingGraph({ layer, propKey }: { layer: Layer | null; propKey: string }) {
  const e = useEditor();
  const currentEasing = easingAtCurrentTime(e, layer, propKey);
  const initial = controlsForEasing(currentEasing);
  const [p1, setP1] = useState<CurvePoint>(initial.p1);
  const [p2, setP2] = useState<CurvePoint>(initial.p2);
  const ref = useRef<SVGSVGElement>(null);
  const dragging = useRef<1 | 2 | null>(null);

  useEffect(() => {
    const curve = controlsForEasing(currentEasing);
    setP1(curve.p1); setP2(curve.p2);
  }, [layer?.id, propKey, currentEasing]);

  const save = (a: CurvePoint, b: CurvePoint) => {
    if (!layer) return;
    const easing = `cubic(${a.x.toFixed(2)},${a.y.toFixed(2)},${b.x.toFixed(2)},${b.y.toFixed(2)})` as Easing;
    applyEasingToLayer(e, layer, propKey, easing);
  };
  const point = (ev: React.PointerEvent<SVGSVGElement>): CurvePoint => {
    const r = ref.current!.getBoundingClientRect();
    const x = (ev.clientX - r.left) / r.width;
    const y = (ev.clientY - r.top) / r.height;
    return {
      x: Math.min(1, Math.max(0, (x - 0.02) / 0.96)),
      y: Math.min(1.5, Math.max(-0.5, 1 - (y - 0.02) / 0.96)),
    };
  };
  const onMove = (ev: React.PointerEvent<SVGSVGElement>) => {
    if (!dragging.current) return;
    const p = point(ev);
    if (dragging.current === 1) { setP1(p); save(p, p2); }
    else { setP2(p); save(p1, p); }
  };
  const px = (p: CurvePoint) => 2 + p.x * 196;
  const py = (p: CurvePoint) => 98 - p.y * 96;
  const apply = (easing: Easing) => {
    if (!layer) return;
    const curve = controlsForEasing(easing);
    setP1(curve.p1); setP2(curve.p2);
    applyEasingToLayer(e, layer, propKey, easing);
    e.notify(`Easing: ${easing}`, 'ok');
  };
  const step = (direction: -1 | 1) => {
    const index = EASING_PRESETS.findIndex(item => item.easing === currentEasing);
    const next = EASING_PRESETS[(Math.max(0, index) + direction + EASING_PRESETS.length) % EASING_PRESETS.length];
    apply(next.easing);
  };

  return (
    <div className="um-easing">
      <div className="um-easing-graph">
        <svg ref={ref} viewBox="0 0 200 100" preserveAspectRatio="none" className="um-bezier"
          onPointerMove={onMove} onPointerUp={() => { dragging.current = null; }} onPointerCancel={() => { dragging.current = null; }}>
          {Array.from({ length: 21 }, (_, i) => <line key={`v${i}`} x1={2 + i * 9.8} y1="2" x2={2 + i * 9.8} y2="98" className="um-grid-line" />)}
          {Array.from({ length: 11 }, (_, i) => <line key={`h${i}`} x1="2" y1={2 + i * 9.6} x2="198" y2={2 + i * 9.6} className="um-grid-line" />)}
          <rect x="2" y="2" width="196" height="96" className="um-easing-boundary" />
          <line x1="2" y1="98" x2={px(p1)} y2={py(p1)} className="um-easing-handle" />
          <line x1="198" y1="2" x2={px(p2)} y2={py(p2)} className="um-easing-handle" />
          <line x1={px(p1)} y1={py(p1)} x2={px(p2)} y2={py(p2)} className="um-easing-control-line" />
          <circle cx="2" cy="98" r="3" className="um-easing-endpoint" />
          <circle cx="198" cy="2" r="3" className="um-easing-endpoint" />
          <circle cx={px(p1)} cy={py(p1)} r="10.8" className="um-easing-control" onPointerDown={ev => {
            dragging.current = 1; (ev.target as Element).setPointerCapture(ev.pointerId);
          }} />
          <circle cx={px(p2)} cy={py(p2)} r="10.8" className="um-easing-control" onPointerDown={ev => {
            dragging.current = 2; (ev.target as Element).setPointerCapture(ev.pointerId);
          }} />
        </svg>
      </div>
      <div className="um-easing-footer">
        <button type="button" className="um-easing-step" title="Previous easing" onClick={() => step(-1)}><ChevronLeft size={20} /></button>
        <span className="um-easing-caption">CUBIC BEZIER EASING</span>
        <button type="button" className="um-easing-step" title="Next easing" onClick={() => step(1)}><ChevronRight size={20} /></button>
      </div>
    </div>
  );
}

function EasingLeftRail() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  return (
    <div className="um-rail um-rail-left um-easing-left-rail">
      <IBtn title="Back to Move & Transform" onClick={() => ui.setMoveView('pos')}><ArrowLeft size={20} strokeWidth={1.7} /></IBtn>
      <IBtn title="Reverse easing direction" onClick={() => {
        if (!layer) return;
        const key = ui.activePropKey || 'x';
        applyEasingToLayer(e, layer, key, reverseEasing(easingAtCurrentTime(e, layer, key)));
      }}><ArrowLeftRight size={20} strokeWidth={1.7} /></IBtn>
      <IBtn title="More options" onClick={() => ui.setSheet('overflow')}><MoreHorizontal size={20} /></IBtn>
    </div>
  );
}

function EasingPresetRail({ layer, propKey, current }: { layer: Layer | null; propKey: string; current: Easing }) {
  const e = useEditor();
  return (
    <div className="um-rail um-rail-right um-easing-preset-rail">
      {EASING_PRESETS.map(item => <IBtn key={item.label} title={item.label} active={current === item.easing}
        onClick={() => { applyEasingToLayer(e, layer, propKey, item.easing); e.notify(`Easing: ${item.label}`, 'ok'); }}>
        <EasingCurveIcon path={item.path} active={current === item.easing} />
      </IBtn>)}
    </div>
  );
}

function EasingModeRail({ layer, propKey, current }: { layer: Layer | null; propKey: string; current: Easing }) {
  const e = useEditor();
  return (
    <div className="um-rail um-rail-right um-easing-mode-rail">
      {EASING_MODES.map(item => {
        const active = item.kind === 'curve' ? String(current).startsWith('cubic(') : current === item.easing;
        return <IBtn key={item.label} title={item.label} active={active} onClick={() => {
          applyEasingToLayer(e, layer, propKey, item.easing);
          e.notify(`Easing: ${item.label}`, 'ok');
        }}><EasingCurveIcon path={item.path} active={active} kind={item.kind} /></IBtn>;
      })}
    </div>
  );
}

function PositionField({ label, value, accent = false, digits = 2, unit = '', onChange }: {
  label: string; value: number; accent?: boolean; digits?: number; unit?: string; onChange: (value: number) => void;
}) {
  return (
    <label className={`um-position-field ${accent ? 'accent' : ''}`}>
      <span className="um-position-value">
        <input type="number" value={value.toFixed(digits)} step={digits === 1 ? 0.1 : 0.01}
          onChange={ev => { const n = Number(ev.target.value); if (Number.isFinite(n)) onChange(n); }} />
        {unit && <em>{unit}</em>}
      </span>
      <small>{label}</small>
    </label>
  );
}

function TickRuler({ label, value, min, max, accent = false, onChange }: {
  label: string; value: number; min: number; max: number; accent?: boolean; onChange: (value: number) => void;
}) {
  return (
    <div className={`um-tick-ruler ${accent ? 'accent' : ''}`} title={label}>
      <span className="um-tick-ruler-center" />
      <input type="range" aria-label={label} min={min} max={max} step="0.1" value={value}
        onChange={ev => onChange(Number(ev.target.value))} />
    </div>
  );
}

function MoveBody({ sub }: { sub: number }) {
  const e = useEditor();
  const { layer, get, set } = useProp();
  const ui = useUI();
  const linked = ui.shapeRatioLocked;
  const setLinked = ui.setShapeRatioLocked;
  if (sub === 0) return (
    <div className="um-prop-body um-move-position">
      <div className="um-position-fields">
        <div className="um-position-xy">
          <PositionField label="X" value={get('x') + e.project.width / 2} accent
            onChange={v => set('x', v - e.project.width / 2)} />
          <PositionField label="Y" value={get('y') + e.project.height / 2} accent
            onChange={v => set('y', v - e.project.height / 2)} />
        </div>
        <PositionField label="Z" value={get('z')} onChange={v => set('z', v)} />
      </div>
      <XyPad x={get('x')} y={get('y')} onChange={(x, y) => { set('x', x); set('y', y); }} />
    </div>
  );
  if (sub === 1) {
    const w = get('shapeWidth'), h = get('shapeHeight');
    const ratio = w / Math.max(1, h);
    const setWidth = (v: number) => { set('shapeWidth', v); if (linked) set('shapeHeight', v / ratio); };
    const setHeight = (v: number) => { set('shapeHeight', v); if (linked) set('shapeWidth', v * ratio); };
    return (
      <div className="um-prop-body um-move-size">
        <div className="um-size-fields">
          <PositionField label="Width" value={w} digits={1} accent onChange={setWidth} />
          <button type="button" className={`um-size-link ${linked ? 'on' : ''}`} title={linked ? 'Unlock aspect ratio' : 'Lock aspect ratio'}
            aria-pressed={linked} onClick={() => setLinked(!linked)}><Link2 size={17} strokeWidth={1.8} /></button>
          <PositionField label="Height" value={h} digits={1} accent onChange={setHeight} />
        </div>
        <TickRuler label="Scale width and height" value={w} min={4} max={2000} accent
          onChange={v => { if (linked) { set('shapeWidth', v); set('shapeHeight', v / ratio); } else set('shapeWidth', v); }} />
      </div>
    );
  }
  if (sub === 2) return (
    <div className="um-prop-body um-move-skew">
      <div className="um-size-fields um-skew-fields">
        <PositionField label="X Skew" value={get('skewX')} digits={1} unit="°" accent onChange={v => set('skewX', v)} />
        <PositionField label="Y Skew" value={get('skewY')} digits={1} unit="°" onChange={v => set('skewY', v)} />
      </div>
      <TickRuler label="X Skew" value={get('skewX')} min={-80} max={80} accent onChange={v => set('skewX', v)} />
      <TickRuler label="Y Skew" value={get('skewY')} min={-80} max={80} onChange={v => set('skewY', v)} />
    </div>
  );
  if (sub === 3) return (
    <div className="um-prop-body um-rotation-body">
      <RotationDial value={Math.round(get('rotation'))} onChange={v => set('rotation', v)} />
    </div>
  );
  return <EasingGraph layer={layer} propKey={ui.activePropKey} />;
}

/* ------------------------------ blend & opacity --------------------------- */
const BLEND_GROUPS: { label: string; modes: string[]; hue: number }[] = [
  { label: 'Contrast', modes: ['normal', 'overlay', 'soft-light', 'hard-light'], hue: 268 },
  { label: 'Difference', modes: ['difference', 'exclusion', 'color-dodge', 'color-burn'], hue: 190 },
  { label: 'Color', modes: ['hue', 'saturation', 'color', 'luminosity'], hue: 330 },
  { label: 'Mask', modes: ['darken', 'lighten', 'multiply', 'screen'], hue: 152 },
];
function BlendBody() {
  const e = useEditor();
  const { layer, get, set } = useProp();
  const [open, setOpen] = useState<string | null>(null);
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  return (
    <div className="um-prop-body um-blend-panel">
      <div className="um-blend-opacity">
        <Blend size={17} strokeWidth={1.7} aria-hidden="true" />
        <input type="range" min={0} max={100} value={get('opacity')} aria-label="Opacity"
          onChange={ev => set('opacity', Number(ev.target.value))} />
        <b>{Math.round(get('opacity'))}%</b>
      </div>
      <div className="um-blend-groups">
        {BLEND_GROUPS.map(g => (
          <div key={g.label} className={`um-blend-group ${open === g.label ? 'open' : ''}`}>
            <button type="button" onClick={() => setOpen(open === g.label ? null : g.label)}>
              <span className="um-blend-arrow">▶</span><strong>{g.label}</strong>
              <span className="um-blend-preview" style={{ background: `linear-gradient(120deg,hsl(${g.hue} 70% 55%),hsl(${g.hue + 60} 60% 25%))` }} />
            </button>
            {open === g.label && (g.label === 'Mask' ? (
              <div className="um-mask-group">
                <Seg value={layer.mask.type} onChange={v => e.setMask(layer.id, { type: v })}
                  options={[{ id: 'none', label: 'None' }, { id: 'rect', label: 'Rect' }, { id: 'ellipse', label: 'Ellipse' }, { id: 'linear', label: 'Linear' }]} />
                <RulerSlider label="Feather" value={layer.mask.feather} min={0} max={200}
                  onChange={v => e.setMask(layer.id, { feather: v })} unit="px" />
                <label className="um-checkline"><input type="checkbox" checked={layer.mask.invert}
                  onChange={ev => e.setMask(layer.id, { invert: ev.target.checked })} /> Invert mask</label>
              </div>
            ) : (
              <div className="um-blend-thumbs">
                {g.modes.map(m => (
                  <button type="button" key={m} className={layer.blend === m ? 'on' : ''}
                    title={BLEND_LABELS[m as keyof typeof BLEND_LABELS] ?? m}
                    onClick={() => e.setBlend(layer.id, m as typeof layer.blend)}>
                    <span style={{ background: `radial-gradient(circle at 30% 30%,hsl(${g.hue} 80% 65%),hsl(${g.hue + 90} 65% 22%))` }} />
                    <small>{BLEND_LABELS[m as keyof typeof BLEND_LABELS] ?? m}</small>
                  </button>
                ))}
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

/* --------------------------------- color ---------------------------------- */
function ColorBody({ sub }: { sub: number }) {
  const e = useEditor();
  const { layer, get, set } = useProp();
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  if (sub === 0) return (
    <div className="um-prop-body">
      <div className="um-prop-heading">Fill</div>
      <Seg value={layer.style.fillType} onChange={v => e.setStyle(layer.id, { fillType: v })}
        options={[{ id: 'solid', label: 'Solid' }, { id: 'linear', label: 'Linear' }, { id: 'radial', label: 'Radial' }]} />
      <div className="um-color-row">
        <ColorInput value={layer.style.color} onChange={v => e.setStyle(layer.id, { color: v })} label="Primary" />
        <ColorInput value={layer.style.color2} onChange={v => e.setStyle(layer.id, { color2: v })} label="Secondary" />
      </div>
      <Swatches colors={PALETTE} value={layer.style.color} onChange={v => e.setStyle(layer.id, { color: v })} />
      <RulerSlider label="Gradient angle" value={get('gradientAngle')} min={0} max={360} onChange={v => set('gradientAngle', v)} unit="°" />
    </div>
  );
  return (
    <div className="um-prop-body">
      <div className="um-prop-heading">Text style</div>
      <input className="um-input" value={layer.style.text} aria-label="Text content"
        onChange={ev => e.setStyle(layer.id, { text: ev.target.value })} />
      <div className="um-num-row">
        <NumField label="Size" value={get('fontSize')} min={8} max={600} onChange={v => set('fontSize', v)} unit="px" />
        <NumField label="Spacing" value={get('letterSpacing')} min={-20} max={100} onChange={v => set('letterSpacing', v)} unit="px" />
      </div>
      <div className="um-num-row">
        <NumField label="Weight" value={layer.style.fontWeight} min={100} max={900} step={100}
          onChange={v => e.setStyle(layer.id, { fontWeight: v })} />
        <NumField label="Line height" value={get('lineHeight')} min={0.5} max={3} step={0.05} onChange={v => set('lineHeight', v)} unit="×" />
      </div>
      <div className="um-action-grid">
        <button type="button" className="um-mini-btn" onClick={() => e.setStyle(layer.id, { align: 'left' })}>Left</button>
        <button type="button" className="um-mini-btn" onClick={() => e.setStyle(layer.id, { align: 'center' })}>Centre</button>
        <button type="button" className="um-mini-btn" onClick={() => e.setStyle(layer.id, { align: 'right' })}>Right</button>
        <button type="button" className="um-mini-btn" onClick={() => e.setStyle(layer.id, { italic: !layer.style.italic })}>Italic</button>
      </div>
      <label className="um-checkline"><input type="checkbox" checked={layer.style.uppercase}
        onChange={ev => e.setStyle(layer.id, { uppercase: ev.target.checked })} /> Uppercase</label>
    </div>
  );
}

/* ------------------------------ border & shadow --------------------------- */
const STROKE_CAPS = ['Butt cap', 'Round cap', 'Square cap'];
const STROKE_JOINS = ['Miter join', 'Round join', 'Bevel join'];
function BorderBody({ sub, onAdded }: { sub: number; onAdded: () => void }) {
  const e = useEditor();
  const { layer, get, set } = useProp();
  const lastStroke = useRef(4);
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  const stroke = get('strokeWidth');
  const hasBorder = layer.style.strokeEnabled ?? stroke > 0;
  if (stroke > 0) lastStroke.current = stroke;
  const toggleStroke = () => {
    if (hasBorder) e.setStyle(layer.id, { strokeEnabled: false });
    else {
      if (stroke <= 0) set('strokeWidth', lastStroke.current || 4);
      e.setStyle(layer.id, { strokeEnabled: true });
    }
  };

  const strokePanel = (title: string) => (
    <div className="um-prop-body um-stroke-panel">
      <div className="um-stroke-head um-stroke-panel-head">
        <ColorInput value={layer.style.strokeColor}
          onChange={v => e.setStyle(layer.id, { strokeColor: v })} />
        <strong>{title}</strong>
        <button type="button" className={`um-switch ${hasBorder ? 'on' : ''}`} role="switch"
          aria-checked={hasBorder} aria-label="Enable stroke" onClick={toggleStroke} />
      </div>
      <RulerSlider label="Width" value={stroke} min={0} max={200}
        onChange={v => set('strokeWidth', v)} accent precision={1} />
      <div className="um-stroke-styles">
        <div className="um-stroke-style-group" role="group" aria-label="Stroke caps">
          {STROKE_CAPS.map((label, i) => (
            <button type="button" key={label} title={label} aria-label={label}
              aria-pressed={Math.round(get('strokeCap')) === i}
              className={Math.round(get('strokeCap')) === i ? 'on' : ''}
              onClick={() => set('strokeCap', i)}>
              <svg viewBox="0 0 48 24" className="um-stroke-mark" aria-hidden="true">
                <line x1="11" y1="12" x2="37" y2="12"
                  style={Math.round(get('strokeCap')) === i ? { stroke: 'var(--um-teal)' } : undefined}
                  strokeLinecap={i === 1 ? 'round' : i === 2 ? 'square' : 'butt'} />
                {Math.round(get('strokeCap')) === i && <circle cx="11" cy="12" r="2.2" />}
              </svg>
            </button>
          ))}
        </div>
        <div className="um-stroke-style-group" role="group" aria-label="Stroke joins">
          {STROKE_JOINS.map((label, i) => (
            <button type="button" key={label} title={label} aria-label={label}
              aria-pressed={Math.round(get('strokeJoin')) === i}
              className={Math.round(get('strokeJoin')) === i ? 'on' : ''}
              onClick={() => set('strokeJoin', i)}>
              <svg viewBox="0 0 48 24" className="um-stroke-mark" aria-hidden="true">
                <path d="M13 21V8H35"
                  style={Math.round(get('strokeJoin')) === i ? { stroke: 'var(--um-teal)' } : undefined}
                  strokeLinejoin={i === 1 ? 'round' : i === 2 ? 'bevel' : 'miter'} />
                {Math.round(get('strokeJoin')) === i && <circle cx="13" cy="8" r="2.2" />}
              </svg>
            </button>
          ))}
        </div>
      </div>
      <div className="um-stroke-ends">
        <label>Start<select value={Math.round(get('strokeStart'))} aria-label="Stroke start"
          onChange={ev => set('strokeStart', Number(ev.target.value))}>
          {[0, 10, 25, 50].map(n => <option key={n} value={n}>{n}%</option>)}
        </select></label>
        <label>End<select value={Math.round(get('strokeEnd'))} aria-label="Stroke end"
          onChange={ev => set('strokeEnd', Number(ev.target.value))}>
          {[50, 75, 90, 100].map(n => <option key={n} value={n}>{n}%</option>)}
        </select></label>
      </div>
    </div>
  );
  if (sub === 0) return strokePanel('Stroke');
  if (sub === 1) {
    if (hasBorder) return strokePanel('Border');
    return (
      <div className="um-prop-body um-add-border">
        <button type="button" className="um-add-border-btn" onClick={() => {
          if (stroke <= 0) set('strokeWidth', lastStroke.current || 4);
          e.setStyle(layer.id, { strokeEnabled: true });
          onAdded();
        }}>
          + Add Border
        </button>
      </div>
    );
  }
  const fx = layer.effects.find(f => f.type === 'dropShadow');
  const ensure = () => {
    const fresh = e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
    if (fresh) return fresh;
    e.addEffect('dropShadow');
    const created = e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
    if (created) {
      e.setEffectParam(created.id, 'blur', 0, false);
      e.setEffectParam(created.id, 'opacity', 100, false);
      e.setEffectParam(created.id, 'offsetX', 3, false);
      e.setEffectParam(created.id, 'offsetY', 0, false);
    }
    return e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
  };
  const param = (key: string, v: number) => { const f = ensure(); if (f) e.setEffectParam(f.id, key, v); };
  const value = (key: string, fallback: number) => fx ? e.getEffectValue(fx.id, key) : fallback;
  const toggleShadow = () => {
    const current = e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
    if (current) e.toggleEffect(current.id);
    else ensure();
  };
  return (
    <div className="um-prop-body um-shadow-panel">
      <div className="um-stroke-head">
        <strong>Shadow</strong>
        <button type="button" className={`um-switch ${fx?.enabled ? 'on' : ''}`} role="switch"
          aria-checked={!!fx?.enabled} aria-label="Enable shadow" onClick={toggleShadow} />
      </div>
      <RulerSlider label="Size" value={value('blur', 0)} min={0} max={120} onChange={v => param('blur', v)} accent />
      <RulerSlider label="Alpha" value={value('opacity', 100)} min={0} max={100} onChange={v => param('opacity', v)} unit="%" />
      <div className="um-shadow-position">
        <strong>Position</strong>
        <NumField label="X" value={value('offsetX', 3)} min={-200} max={200} onChange={v => param('offsetX', v)} />
        <NumField label="Y" value={value('offsetY', 0)} min={-200} max={200} onChange={v => param('offsetY', v)} />
      </div>
    </div>
  );
}

/* -------------------------------- edit shape ------------------------------ */
function EditShapeBody({ sub }: { sub: number }) {
  const e = useEditor();
  const ui = useUI();
  const { layer, get, set } = useProp();
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  if (sub === 0) return (
    <div className="um-prop-body">
      <div className="um-shape-mini-grid">
        {SHAPE_PICKER.map(s => (
          <button type="button" key={s.kind} title={s.label} className={layer.style.shape === s.kind ? 'on' : ''}
            onClick={() => e.setShape(layer.id, s.kind)}>
            <ShapeGlyph kind={s.kind} size={24} />
          </button>
        ))}
      </div>
      <span className="um-prop-caption">SHAPE</span>
    </div>
  );
  if (sub === 1) {
    const scaleX = get('scaleX');
    const scaleY = get('scaleY');
    const updateScale = (axis: 'scaleX' | 'scaleY', value: number) => {
      const own = get(axis);
      const otherAxis = axis === 'scaleX' ? 'scaleY' : 'scaleX';
      const other = get(otherAxis);
      e.beginBatch();
      if (ui.shapeRatioLocked && own !== 0) set(otherAxis, value * other / own);
      set(axis, value);
      e.endBatch();
    };
    const sizeValue = (scaleX + scaleY) / 2;
    return (
      <div className="um-prop-body um-edit-shape-fields">
        <div className="um-edit-size-row">
          <strong className="um-edit-row-label active">Size</strong>
          <TickRuler label="Shape size" value={sizeValue} min={0} max={200} accent
            onChange={v => updateScale('scaleX', v)} />
          <div className="um-edit-scale-values">
            <label><span>X:</span><input aria-label="Shape scale X" type="number" min={0} max={1000} step={0.1}
              value={Number(scaleX.toFixed(1))} onChange={ev => { const v = Number(ev.target.value); if (Number.isFinite(v)) updateScale('scaleX', v); }} /></label>
            <label><span>Y:</span><input aria-label="Shape scale Y" type="number" min={0} max={1000} step={0.1}
              value={Number(scaleY.toFixed(1))} onChange={ev => { const v = Number(ev.target.value); if (Number.isFinite(v)) updateScale('scaleY', v); }} /></label>
          </div>
        </div>
        <div className="um-edit-radius-row">
          <strong className="um-edit-row-label">Radius</strong>
          <TickRuler label="Corner radius" value={get('cornerRadius')} min={0} max={50}
            onChange={v => set('cornerRadius', v)} />
          <output className="um-edit-radius-value">{Math.round(get('cornerRadius'))}</output>
        </div>
      </div>
    );
  }
  return (
    <div className="um-prop-body">
      <RulerSlider label="Star points" value={get('starPoints')} min={3} max={20} onChange={v => set('starPoints', v)} accent />
      <RulerSlider label="Inner radius" value={get('innerRadius')} min={5} max={95} onChange={v => set('innerRadius', v)} unit="%" />
      <RulerSlider label="Polygon sides" value={get('polygonSides')} min={3} max={20} onChange={v => set('polygonSides', v)} />
      {layer.style.shape === 'customPath' && (
        <Btn variant="dark" onClick={() => ui.setSheet('vector')}>Draw a new vector path</Btn>
      )}
    </div>
  );
}

/* ---------------------------------- presets ------------------------------- */
function PresetsBody() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  return (
    <div className="um-prop-body um-prop-scroll">
      <div className="um-prop-heading">Motion presets
        <button type="button" className="um-mini-btn" onClick={() => ui.setPresetsDialog(true)}>Presets library ↗</button>
      </div>
      <div className="um-preset-grid">
        {MOTION_PRESETS.map(p => (
          <button type="button" key={p.id} title={p.description} disabled={!layer}
            onClick={() => { e.applyMotionPreset(p.id); e.notify(`Preset “${p.label}” applied`, 'ok'); }}>
            <strong>{p.label}</strong><small>{p.description}</small>
          </button>
        ))}
      </div>
      <Btn variant="dark" onClick={() => {
        void saveSelectedLayerPreset().then(() => e.notify('Preset XML downloaded', 'ok'))
          .catch(err => e.notify((err as Error).message, 'warn'));
      }}>Save selected layer as preset</Btn>
      {!layer && <p className="um-hint center">Select a layer to apply a preset.</p>}
    </div>
  );
}

/* ---------------------------------- effects ------------------------------- */
function EffectsBody({ sub }: { sub: number }) {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const [group, setGroup] = useState('All');
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  const groups = ['All', ...Array.from(new Set(EFFECT_DEFS.map(d => d.group)))];
  if (sub === 0) return (
    <div className="um-prop-body um-prop-scroll">
      <div className="um-prop-heading">Effect Browser
        <button type="button" className="um-mini-btn" onClick={() => ui.setEffectBrowser(true)}>Full screen ↗</button>
      </div>
      <div className="um-browser-grid">
        {groups.filter(g => g !== 'All').map((g, i) => (
          <button type="button" key={g} className={group === g ? 'on' : ''}
            onClick={() => setGroup(group === g ? 'All' : g)}>
            <span style={{ background: `linear-gradient(145deg, hsl(${(i * 47) % 360} 60% 42%), hsl(${(i * 47 + 70) % 360} 55% 18%))` }} />
            <strong>{g}</strong>
          </button>
        ))}
      </div>
      <div className="um-group-row">
        {groups.map(g => <button type="button" key={g} className={group === g ? 'on' : ''} onClick={() => setGroup(g)}>{g}</button>)}
      </div>
      <div className="um-effect-list">
        {EFFECT_DEFS.filter(d => group === 'All' || d.group === group).map(d => (
          <button type="button" key={d.type} onClick={() => e.addEffect(d.type)}>
            <strong>{d.label}</strong><small>{d.description}</small><em>+</em>
          </button>
        ))}
      </div>
    </div>
  );
  return (
    <div className="um-prop-body um-prop-scroll">
      <div className="um-prop-heading">Effect stack</div>
      {layer.effects.length === 0 && <p className="um-hint">No effects yet. Use the browse tab.</p>}
      {layer.effects.map(fx => {
        const def = EFFECT_DEFS.find(d => d.type === fx.type);
        return (
          <div className="um-effect-card" key={fx.id}>
            <div className="um-effect-head">
              <strong>{def?.label ?? fx.type}</strong>
              <IBtn title={fx.enabled ? 'Disable effect' : 'Enable effect'} onClick={() => e.toggleEffect(fx.id)}><Eye size={15} /></IBtn>
              <IBtn title="Remove effect" onClick={() => e.removeEffect(fx.id)}><span className="um-glyph">×</span></IBtn>
            </div>
            {def?.params.map(p => (
              <Slider key={p.key} label={p.label} value={e.getEffectValue(fx.id, p.key)}
                min={p.min} max={p.max} step={p.step} unit={p.unit}
                onChange={v => { e.setEffectParam(fx.id, p.key, v); }} />
            ))}
            <button type="button" className="um-mini-btn" onClick={() => {
              e.addEffectKeyframe(fx.id, def?.params[0]?.key ?? 'amount');
              e.notify('Effect keyframe added', 'ok');
            }}>Add keyframe</button>
          </div>
        );
      })}
    </div>
  );
}

/* ---------------------------------- camera -------------------------------- */
function CameraBody({ sub }: { sub: number }) {
  const e = useEditor();
  const ui = useUI();
  const cameras = e.project.layers.filter(l => l.type === 'camera');
  const camera = cameras[0];
  if (!camera) return (
    <div className="um-prop-body">
      <p className="um-hint center">A camera layer pans, zooms and rotates the whole composition.</p>
      <Btn onClick={() => {
        e.addLayer('camera', { name: 'Camera 1', duration: e.project.duration });
        e.notify('Camera layer added', 'ok');
      }}>Add camera layer</Btn>
    </div>
  );
  const get = (k: string) => e.getLayerProp(camera, k);
  const set = (k: string, v: number) => { if (ui.activePropKey !== k) ui.setActivePropKey(k); e.setProp(camera.id, k, v); };
  if (sub === 0) return (
    <div className="um-prop-body um-camera-panel">
      <div className="um-prop-heading">Camera View</div>
      <label className="um-checkline um-camera-toggle">
        <input type="checkbox" checked={e.activeCameraView}
          onChange={ev => e.setActiveCameraView(ev.target.checked)} />
        Active Camera View
      </label>
      <p className="um-hint">Renders the composition through the active camera's point of view.</p>
      <RulerSlider label="Force Blur" value={e.forceBlur} min={0} max={100}
        onChange={v => e.setForceBlur(v)} unit="%" accent />
      <div className="um-camera-grid">
        <div><small>FIELD OF VIEW</small><b>{get('scaleX').toFixed(0)}°</b></div>
        <div><small>ZOOM</small><b>{get('scaleY').toFixed(0)}</b></div>
        <div><small>PAN X</small><b>{get('x').toFixed(0)}</b></div>
        <div><small>PAN Y</small><b>{get('y').toFixed(0)}</b></div>
      </div>
      <RulerSlider label="Zoom" value={get('scaleX')} min={20} max={400} onChange={v => { set('scaleX', v); e.setProp(camera.id, 'scaleY', v); }} unit="%" />
      <RulerSlider label="Pan X" value={get('x')} min={-2000} max={2000} onChange={v => set('x', v)} unit="px" />
      <RulerSlider label="Pan Y" value={get('y')} min={-2000} max={2000} onChange={v => set('y', v)} unit="px" />
      <RulerSlider label="Rotation" value={get('rotation')} min={-360} max={360} onChange={v => set('rotation', v)} unit="°" />
    </div>
  );
  return (
    <div className="um-prop-body">
      <div className="um-prop-heading">Composition view</div>
      <label className="um-checkline"><input type="checkbox" checked={e.showGuides} onChange={() => e.toggleGuides()} /> Safe-area guides</label>
      <label className="um-checkline"><input type="checkbox" checked={e.showGrid} onChange={() => e.toggleGrid()} /> Grid overlay</label>
      <label className="um-checkline"><input type="checkbox" checked={e.loop} onChange={() => e.setLoop(!e.loop)} /> Loop playback</label>
      <label className="um-checkline"><input type="checkbox" checked={e.snapToFrame} onChange={() => e.setSnap(!e.snapToFrame)} /> Snap to frame</label>
      <RulerSlider label="Preview zoom" value={e.previewZoom * 100} min={40} max={300} onChange={v => e.setPreviewZoom(v / 100)} unit="%" />
      <RulerSlider label="Timeline zoom" value={e.timelineZoom} min={12} max={220} onChange={v => e.setTimelineZoom(v)} />
    </div>
  );
}

/* ------------------------------ right rail defs --------------------------- */
interface RailItem { icon: React.ReactNode; title: string; }
const RAILS: Record<PropertyId, RailItem[]> = {
  move: [
    { icon: <Move size={19} strokeWidth={1.6} />, title: 'Position' },
    { icon: <Expand size={19} strokeWidth={1.6} />, title: 'Size & scale' },
    { icon: <span className="um-glyph">╱</span>, title: 'Skew' },
    { icon: <RotateCw size={19} strokeWidth={1.6} />, title: 'Rotation dial' },
    { icon: <Waves size={19} strokeWidth={1.6} />, title: 'Cubic bezier easing' },
  ],
  blend: [],
  color: [
    { icon: <Droplets size={19} strokeWidth={1.6} />, title: 'Fill' },
    { icon: <Type size={19} strokeWidth={1.6} />, title: 'Text style' },
  ],
  border: [
    { icon: <Circle size={19} strokeWidth={1.6} />, title: 'Stroke' },
    { icon: <Square size={19} strokeWidth={1.6} />, title: 'Add Border' },
    { icon: <Layers size={19} strokeWidth={1.6} />, title: 'Shadow' },
  ],
  editShape: [
    { icon: <Grid2X2 size={19} strokeWidth={1.6} />, title: 'Shape picker' },
    { icon: <Ruler size={19} strokeWidth={1.6} />, title: 'Dimensions' },
    { icon: <Star size={19} strokeWidth={1.6} />, title: 'Points & radius' },
  ],
  presets: [{ icon: <Star size={19} strokeWidth={1.6} />, title: 'Motion presets' }],
  effects: [
    { icon: <Sparkles size={19} strokeWidth={1.6} />, title: 'Browse effects' },
    { icon: <Layers size={19} strokeWidth={1.6} />, title: 'Effect stack' },
  ],
  camera: [
    { icon: <ZoomIn size={19} strokeWidth={1.6} />, title: 'Camera transform' },
    { icon: <Expand size={19} strokeWidth={1.6} />, title: 'View options' },
  ],
};

function RightRail({ sub, setSub }: { sub: number; setSub: (n: number) => void }) {
  const ui = useUI();
  const items = RAILS[ui.property];
  const select = (next: number) => {
    setSub(next);
    if (ui.property === 'move') {
      ui.setMoveView((['pos', 'size', 'skew', 'rotation', 'easing'] as const)[next]);
    }
  };
  return (
    <div className="um-rail um-rail-right">
      {items.map((it, i) => (
        <IBtn key={i} title={it.title} active={sub === i} onClick={() => select(i)}>{it.icon}</IBtn>
      ))}
    </div>
  );
}

function PropertyDetail() {
  const ui = useUI();
  const e = useEditor();
  const key = ui.property;
  const viewIndex = (['pos', 'size', 'skew', 'rotation', 'easing'] as const).indexOf(ui.moveView);
  const initialSub = key === 'move' ? Math.max(0, viewIndex) : key === 'editShape' ? 1 : 0;
  const [sub, setSub] = useState(initialSub);
  const isEasing = key === 'move' && ui.moveView === 'easing';
  const easingLayer = e.selectedLayer;
  const easingKey = ui.activePropKey || 'x';
  const currentEasing = easingAtCurrentTime(e, easingLayer, easingKey);

  useEffect(() => {
    setSub(key === 'move' ? Math.max(0, viewIndex) : key === 'editShape' ? 1 : 0);
  }, [key, ui.moveView]);

  return (
    <div className={`um-prop-detail prop-${key} ${isEasing ? 'prop-easing' : ''}`}>
      {isEasing ? <EasingLeftRail /> : <LeftRail sub={sub} setSub={setSub} />}
      <div className="um-prop-center">
        {key === 'move' && <MoveBody sub={Math.min(sub, 4)} />}
        {key === 'blend' && <BlendBody />}
        {key === 'color' && <ColorBody sub={Math.min(sub, 1)} />}
        {key === 'border' && <BorderBody sub={Math.min(sub, 2)} onAdded={() => setSub(0)} />}
        {key === 'editShape' && <EditShapeBody sub={Math.min(sub, 2)} />}
        {key === 'presets' && <PresetsBody />}
        {key === 'effects' && <EffectsBody sub={Math.min(sub, 1)} />}
        {key === 'camera' && <CameraBody sub={Math.min(sub, 1)} />}
      </div>
      {isEasing ? <>
        <EasingPresetRail layer={easingLayer} propKey={easingKey} current={currentEasing} />
        <EasingModeRail layer={easingLayer} propKey={easingKey} current={currentEasing} />
      </> : key !== 'blend' && key !== 'editShape' && <RightRail sub={sub} setSub={setSub} />}
    </div>
  );
}

const TABS: { id: PropertyId; label: string }[] = [
  { id: 'color', label: 'Color & Fill' }, { id: 'border', label: 'Border & Shadow' },
  { id: 'blend', label: 'Blending & Opacity' }, { id: 'move', label: 'Move & Transform' },
  { id: 'editShape', label: 'Edit Shape' }, { id: 'presets', label: 'Presets' },
  { id: 'effects', label: 'Effects' },
];

export function PropertyPane() {
  const ui = useUI();
  const showTabs = ui.sheet === 'property' || ui.sheet === 'properties';
  return (
    <div className={`um-prop-pane ${showTabs ? 'with-tabs' : ''}`}>
      <div className="um-prop-main">
        {ui.sheet === 'properties' ? <PropertiesRoot /> : <PropertyDetail />}
      </div>
      {showTabs && (
        <div className="um-prop-tabs">
          {TABS.map(t => (
            <button type="button" key={t.id} className={ui.property === t.id ? 'on' : ''}
              title={t.label} onClick={() => t.id === 'presets'
                ? (ui.openProperty('presets'), ui.setPresetsDialog(true))
                : ui.openProperty(t.id)}>
              {TILE_ICONS[t.id]}<small>{t.label.replace(' & ', ' / ')}</small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
