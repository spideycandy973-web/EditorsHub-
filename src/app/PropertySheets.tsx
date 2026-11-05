import React, { useRef, useState } from 'react';
import { AlignCenter, AlignLeft, AlignRight, ArrowLeft, Blend, ChevronRight, Circle, Diamond, Droplets, Expand, Eye, Grid2X2, Layers, MoreHorizontal, Move, Palette, RotateCcw, RotateCw, Ruler, Sparkles, Square, Star, Type, Volume2, VolumeX, Waves, ZoomIn } from 'lucide-react';
import { useEditor } from '../editor/store';
import {
  BLEND_LABELS, EASINGS, EFFECT_DEFS, SHAPE_PICKER,
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
    for (const [k, v] of [['x', 0], ['y', 0], ['rotation', 0], ['skewX', 0], ['scaleX', 100], ['scaleY', 100]] as const) {
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
function LeftRail() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  return (
    <div className="um-rail um-rail-left">
      <IBtn title="Back to properties" onClick={() => ui.setSheet('properties')}><ArrowLeft size={20} strokeWidth={1.7} /></IBtn>
      <IBtn title="Add keyframe" onClick={() => {
        if (!layer) { e.notify('Select a layer first', 'warn'); return; }
        e.addKeyframe(layer.id, ui.activePropKey);
        e.notify(`Keyframe on ${ui.activePropKey}`, 'ok');
      }}><Diamond size={19} strokeWidth={1.7} /></IBtn>
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
function Readout({ values, unit = '' }: { values: number[]; unit?: string }) {
  return (
    <div className="um-readout">
      {values.map((v, i) => <b key={i}>{v.toFixed(2)}{unit}</b>)}
    </div>
  );
}

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
      <span className="um-xypad-h" /><span className="um-xypad-v" />
      <span className="um-swipe-hint">Swipe here to move layer</span>
      <i className="um-xypad-dot" style={{ left: `${Math.min(100, Math.max(0, 50 + x / 20))}%`, top: `${Math.min(100, Math.max(0, 50 + y / 20))}%` }} />
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
  const R = 44;
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

function EasingGraph({ layer, propKey }: { layer: Layer | null; propKey: string }) {
  const e = useEditor();
  const [p1, setP1] = useState({ x: 0.25, y: 0.1 });
  const [p2, setP2] = useState({ x: 0.75, y: 0.9 });
  const ref = useRef<SVGSVGElement>(null);
  const dragging = useRef<1 | 2 | null>(null);
  const keyframeCount = layer ? e.getAnim(layer, propKey).keyframes.length : 0;

  const save = (a: typeof p1, b: typeof p2) => {
    if (!layer) return;
    const eas = `cubic(${a.x.toFixed(2)},${a.y.toFixed(2)},${b.x.toFixed(2)},${b.y.toFixed(2)})` as Easing;
    let kfs = e.getAnim(layer, propKey).keyframes;
    if (!kfs.length) { e.addKeyframe(layer.id, propKey); kfs = e.getAnim(layer, propKey).keyframes; }
    const local = e.localTime;
    const kf = [...kfs].sort((x, y) => x.time - y.time).filter(k => k.time <= local).pop() ?? kfs[0];
    if (kf) e.updateKeyframe(layer.id, propKey, kf.id, { easing: eas });
  };
  const point = (ev: React.PointerEvent<SVGSVGElement>) => {
    const r = ref.current!.getBoundingClientRect();
    return {
      x: Math.min(1, Math.max(0, (ev.clientX - r.left) / r.width)),
      y: Math.min(1.5, Math.max(-0.5, 1 - (ev.clientY - r.top) / r.height)),
    };
  };
  const onMove = (ev: React.PointerEvent<SVGSVGElement>) => {
    if (!dragging.current) return;
    const p = point(ev);
    if (dragging.current === 1) { setP1(p); save(p, p2); }
    else { setP2(p); save(p1, p); }
  };
  const path = `M 0 0 C ${p1.x} ${1 - p1.y} ${p2.x} ${1 - p2.y} 1 1`;

  return (
    <div className="um-easing">
      {keyframeCount < 2 && (
        <div className="um-keyframes-hint">
          <strong>Keyframes Required</strong>
          <p>Before adjusting animation easing, you must add at least two keyframes. If you already have keyframes on another property, select that property before adjusting easing.</p>
        </div>
      )}
      <svg ref={ref} viewBox="0 0 1 1" preserveAspectRatio="none" className="um-bezier"
        onPointerMove={onMove}
        onPointerUp={() => { if (dragging.current) { dragging.current = null; e.notify('Cubic bezier easing applied', 'ok'); } }}
        onPointerLeave={() => { dragging.current = null; }}>
        {Array.from({ length: 11 }, (_, i) => <line key={`v${i}`} x1={i / 10} y1="0" x2={i / 10} y2="1" className="um-grid-line" />)}
        {Array.from({ length: 11 }, (_, i) => <line key={`h${i}`} x1="0" y1={i / 10} x2="1" y2={i / 10} className="um-grid-line" />)}
        <path d={path} className="um-bezier-path" />
        <line x1="0" y1="0" x2={p1.x} y2={1 - p1.y} className="um-bezier-handle" />
        <line x1="1" y1="1" x2={p2.x} y2={1 - p2.y} className="um-bezier-handle" />
        <circle cx={p1.x} cy={1 - p1.y} r="0.03"
          onPointerDown={ev => { dragging.current = 1; (ev.target as Element).setPointerCapture(ev.pointerId); }} />
        <circle cx={p2.x} cy={1 - p2.y} r="0.03"
          onPointerDown={ev => { dragging.current = 2; (ev.target as Element).setPointerCapture(ev.pointerId); }} />
      </svg>
      <div className="um-easing-presets">
        {EASINGS.map(eas => (
          <button type="button" key={eas} onClick={() => {
            if (!layer) return;
            let kfs = e.getAnim(layer, propKey).keyframes;
            if (!kfs.length) { e.addKeyframe(layer.id, propKey); kfs = e.getAnim(layer, propKey).keyframes; }
            const kf = [...kfs].sort((x, y) => x.time - y.time).filter(k => k.time <= e.localTime).pop() ?? kfs[0];
            if (kf) { e.updateKeyframe(layer.id, propKey, kf.id, { easing: eas }); e.notify(`Easing: ${eas}`, 'ok'); }
          }}>{eas}</button>
        ))}
      </div>
      <span className="um-easing-caption">CUBIC BEZIER EASING</span>
    </div>
  );
}

function MoveBody({ sub }: { sub: number }) {
  const { layer, get, set } = useProp();
  const ui = useUI();
  if (sub === 0) return (
    <div className="um-prop-body">
      <Readout values={[get('x'), get('y'), get('z')]} />
      <div className="um-num-row">
        <NumField label="X" value={get('x')} onChange={v => set('x', v)} unit="px" />
        <NumField label="Y" value={get('y')} onChange={v => set('y', v)} unit="px" />
      </div>
      <XyPad x={get('x')} y={get('y')} onChange={(x, y) => { set('x', x); set('y', y); }} />
      <span className="um-prop-caption">POSITION</span>
    </div>
  );
  if (sub === 1) return (
    <div className="um-prop-body">
      <Readout values={[get('shapeWidth'), get('shapeHeight')]} unit="px" />
      <div className="um-num-row">
        <NumField label="W" value={get('scaleX')} min={0} max={500} onChange={v => set('scaleX', v)} unit="%" />
        <NumField label="H" value={get('scaleY')} min={0} max={500} onChange={v => set('scaleY', v)} unit="%" />
      </div>
      <RulerSlider label="Width" value={get('shapeWidth')} min={4} max={2000} onChange={v => set('shapeWidth', v)} unit="px" accent />
      <RulerSlider label="Height" value={get('shapeHeight')} min={4} max={2000} onChange={v => set('shapeHeight', v)} unit="px" />
      <span className="um-prop-caption">SIZE &amp; SCALE</span>
    </div>
  );
  if (sub === 2) return (
    <div className="um-prop-body">
      <Readout values={[get('skewX'), get('rotation')]} unit="°" />
      <RulerSlider label="Skew X" value={get('skewX')} min={-80} max={80} onChange={v => set('skewX', v)} unit="°" accent />
      <RulerSlider label="Rotation" value={get('rotation')} min={-360} max={360} onChange={v => set('rotation', v)} unit="°" />
      <span className="um-prop-caption">SKEW</span>
    </div>
  );
  if (sub === 3) return (
    <div className="um-prop-body">
      <RotationDial value={Math.round(get('rotation'))} onChange={v => set('rotation', v)} />
      <NumField label="Rotation" value={get('rotation')} min={-1440} max={1440} onChange={v => set('rotation', v)} unit="°" />
    </div>
  );
  return (
    <div className="um-prop-body">
      <EasingGraph layer={layer} propKey={ui.activePropKey} />
      <span className="um-prop-caption">EASING · {ui.activePropKey}</span>
    </div>
  );
}

/* ------------------------------ blend & opacity --------------------------- */
const BLEND_GROUPS: { label: string; modes: string[]; hue: number }[] = [
  { label: 'Contrast', modes: ['normal', 'overlay', 'soft-light', 'hard-light'], hue: 268 },
  { label: 'Difference', modes: ['difference', 'exclusion', 'color-dodge', 'color-burn'], hue: 190 },
  { label: 'Color', modes: ['hue', 'saturation', 'color', 'luminosity'], hue: 330 },
  { label: 'Mask', modes: ['darken', 'lighten', 'multiply', 'screen'], hue: 152 },
];
function BlendBody({ sub }: { sub: number }) {
  const e = useEditor();
  const { layer, get, set } = useProp();
  const [open, setOpen] = useState<string | null>(null);
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  if (sub === 1) return (
    <div className="um-prop-body">
      <div className="um-prop-heading">Mask</div>
      <Seg value={layer.mask.type} onChange={v => e.setMask(layer.id, { type: v })}
        options={[{ id: 'none', label: 'None' }, { id: 'rect', label: 'Rect' }, { id: 'ellipse', label: 'Ellipse' }, { id: 'linear', label: 'Linear' }]} />
      <RulerSlider label="Feather" value={layer.mask.feather} min={0} max={200} onChange={v => e.setMask(layer.id, { feather: v })} unit="px" />
      <label className="um-checkline"><input type="checkbox" checked={layer.mask.invert}
        onChange={ev => e.setMask(layer.id, { invert: ev.target.checked })} /> Invert mask</label>
    </div>
  );
  return (
    <div className="um-prop-body um-blend-panel">
      <div className="um-blend-opacity">
        <small>OPTIONS</small>
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
            {open === g.label && (
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
            )}
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
const STROKE_STYLES = ['Solid', 'Dashed', 'Dotted', 'Dash dot', 'Long dash'];
function BorderBody({ sub }: { sub: number }) {
  const e = useEditor();
  const { layer, get, set } = useProp();
  if (!layer) return <p className="um-hint center">Select a layer.</p>;
  const stroke = get('strokeWidth');
  const hasBorder = stroke > 0;

  if (sub === 0) {
    return (
      <div className="um-prop-body um-stroke-panel">
        <div className="um-stroke-head">
          <strong>Stroke</strong>
          <button type="button" className="um-stroke-color" title="Stroke colour"
            onClick={() => { e.setStyle(layer.id, { strokeColor: layer.style.strokeColor === '#ffffff' ? '#000000' : '#ffffff' }); }} />
        </div>
        <RulerSlider label="Width" value={stroke} min={0} max={200}
          onChange={v => set('strokeWidth', v)} unit="px" accent />
        <div className="um-stroke-val">{stroke.toFixed(1)}</div>
        <div className="um-stroke-styles">
          {STROKE_STYLES.map((label, i) => (
            <button type="button" key={label} title={label}
              className={Math.round(get('strokeDash')) === i ? 'on' : ''}
              onClick={() => set('strokeDash', i)}>
              <i style={{ background: ['linear-gradient(90deg,#ddd 0 100%)',
                'repeating-linear-gradient(90deg,#ddd 0 7px,transparent 7px 11px)',
                'repeating-linear-gradient(90deg,#ddd 0 2px,transparent 2px 6px)',
                'repeating-linear-gradient(90deg,#ddd 0 9px,transparent 9px 12px,#ddd 12px 14px,transparent 14px 18px)',
                'repeating-linear-gradient(90deg,#ddd 0 12px,transparent 12px 16px)'][i] }} />
            </button>
          ))}
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
  }
  if (sub === 2) {
    return (
      <div className="um-prop-body">
        <div className="um-prop-heading">Rounded corners</div>
        <RulerSlider label="Corner radius" value={get('cornerRadius')} min={0} max={500} onChange={v => set('cornerRadius', v)} unit="px" accent />
        <RulerSlider label="Border width" value={stroke} min={0} max={200} onChange={v => set('strokeWidth', v)} unit="px" />
        <div className="um-color-row">
          <ColorInput value={layer.style.strokeColor} onChange={v => e.setStyle(layer.id, { strokeColor: v })} label="Border" />
        </div>
      </div>
    );
  }
  if (!hasBorder) {
    return (
      <div className="um-prop-body um-add-border">
        <button type="button" className="um-add-border-btn" onClick={() => set('strokeWidth', 4)}>
          + Add Border
        </button>
        <p className="um-hint">Add a stroke to this layer, then tune its width, style and trim here.</p>
      </div>
    );
  }
  const fx = layer.effects.find(f => f.type === 'dropShadow');
  const ensure = () => {
    const fresh = e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
    if (fresh) return fresh;
    e.addEffect('dropShadow');
    return e.selectedLayer?.effects.find(f => f.type === 'dropShadow');
  };
  const param = (key: string, v: number) => { const f = ensure(); if (f) e.setEffectParam(f.id, key, v); };
  const value = (key: string, fallback: number) => fx ? e.getEffectValue(fx.id, key) : fallback;
  return (
    <div className="um-prop-body um-shadow-panel">
      <div className="um-stroke-head"><strong>Shadow</strong>
        <button type="button" className="um-stroke-color" title="Shadow colour" onClick={() => param('blur', Math.max(1, value('blur', 0)))} />
      </div>
      <RulerSlider label="Size" value={value('blur', 0)} min={0} max={120} onChange={v => param('blur', v)} unit="px" accent />
      <RulerSlider label="Alpha" value={value('opacity', 0)} min={0} max={100} onChange={v => param('opacity', v)} unit="%" />
      <RulerSlider label="Position X" value={value('offsetX', 0)} min={-200} max={200} onChange={v => param('offsetX', v)} unit="px" />
      <RulerSlider label="Position Y" value={value('offsetY', 0)} min={-200} max={200} onChange={v => param('offsetY', v)} unit="px" />
      <div className="um-shadow-pos">X: <b>{Math.round(value('offsetX', 0))}</b> Y: <b>{Math.round(value('offsetY', 0))}</b></div>
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
  if (sub === 1) return (
    <div className="um-prop-body">
      <RulerSlider label="Width" value={get('shapeWidth')} min={4} max={4000} onChange={v => set('shapeWidth', v)} unit="px" accent />
      <RulerSlider label="Height" value={get('shapeHeight')} min={4} max={4000} onChange={v => set('shapeHeight', v)} unit="px" />
      <RulerSlider label="Corner radius" value={get('cornerRadius')} min={0} max={500} onChange={v => set('cornerRadius', v)} unit="px" />
    </div>
  );
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
  blend: [
    { icon: <Circle size={19} strokeWidth={1.6} />, title: 'Opacity & blend' },
    { icon: <span className="um-glyph dashed-square">▧</span>, title: 'Mask' },
  ],
  color: [
    { icon: <Droplets size={19} strokeWidth={1.6} />, title: 'Fill' },
    { icon: <Type size={19} strokeWidth={1.6} />, title: 'Text style' },
  ],
  border: [
    { icon: <Circle size={19} strokeWidth={1.6} />, title: 'Border' },
    { icon: <Square size={19} strokeWidth={1.6} />, title: 'Shadow' },
    { icon: <span className="um-glyph">▢</span>, title: 'Rounded corners' },
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
  return (
    <div className="um-rail um-rail-right">
      {items.map((it, i) => (
        <IBtn key={i} title={it.title} active={sub === i} onClick={() => setSub(i)}>{it.icon}</IBtn>
      ))}
    </div>
  );
}

function PropertyDetail() {
  const ui = useUI();
  const [sub, setSub] = useState(0);
  const key = ui.property;
  return (
    <div className="um-prop-detail" key={key}>
      <LeftRail />
      <div className="um-prop-center">
        {key === 'move' && <MoveBody sub={Math.min(sub, 4)} />}
        {key === 'blend' && <BlendBody sub={Math.min(sub, 1)} />}
        {key === 'color' && <ColorBody sub={Math.min(sub, 1)} />}
        {key === 'border' && <BorderBody sub={Math.min(sub, 2)} />}
        {key === 'editShape' && <EditShapeBody sub={Math.min(sub, 2)} />}
        {key === 'presets' && <PresetsBody />}
        {key === 'effects' && <EffectsBody sub={Math.min(sub, 1)} />}
        {key === 'camera' && <CameraBody sub={Math.min(sub, 1)} />}
      </div>
      <RightRail sub={sub} setSub={setSub} />
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
