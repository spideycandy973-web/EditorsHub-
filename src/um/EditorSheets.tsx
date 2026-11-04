import React, { useRef, useState } from 'react';
import {
  ArrowLeft, Brush, Camera, ChevronRight, Circle, Copy, Diamond, FileCode2,
  FolderOpen, Layers, Music2, PenTool, Plus, Scissors, Shapes, Sparkles, Square,
  Trash2, Type, WandSparkles, X,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { SHAPE_PICKER, type Layer, type ShapeKind } from '../editor/types';
import { useUI } from './context';
import { Backdrop, Btn, IBtn, PALETTE, Sheet } from './widgets';
import { ShapeGlyph } from './ShapeGlyph';
import {
  addDrawing, addMediaFiles, addVectorPath, saveSelectedLayerPreset,
} from './lib';

/* Multi-layer clipboard. Operations are applied to the real project. */
let layerClipboard: Layer[] | null = null;
let clipboardVersion = 0;

const ADD_TILES = [
  { id: 'media', label: 'Media', icon: <WandSparkles size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'text', label: 'Text', icon: <Type size={21} strokeWidth={1.5} /> },
  { id: 'shape', label: 'Shape', icon: <Shapes size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'audio', label: 'Audio', icon: <Music2 size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'freehand', label: 'Freehand Drawing', icon: <Brush size={21} strokeWidth={1.5} /> },
  { id: 'vector', label: 'Vector Drawing', icon: <PenTool size={21} strokeWidth={1.5} /> },
  { id: 'element', label: 'Element / Project', icon: <Circle size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'template', label: 'Template', icon: <Square size={21} strokeWidth={1.5} />, arrow: true, badge: 'NEW' },
] as const;

function AddSheet() {
  const e = useEditor();
  const ui = useUI();
  const mediaRef = useRef<HTMLInputElement>(null);
  const audioRef = useRef<HTMLInputElement>(null);
  const dur = ui.prefs.defaultLayerDuration;
  const cameraExists = e.project.layers.some(l => l.type === 'camera');

  const openProps = () => { ui.setSheet('properties'); ui.setSelectedIds([]); };
  const importFiles = (files: FileList | null) => {
    if (!files) return;
    void addMediaFiles(files).then(n => { e.notify(`Imported ${n} file(s)`, 'ok'); openProps(); })
      .catch(err => e.notify((err as Error).message, 'warn'));
  };

  const action = (id: typeof ADD_TILES[number]['id']) => {
    switch (id) {
      case 'media': mediaRef.current?.click(); break;
      case 'audio': audioRef.current?.click(); break;
      case 'text': e.addLayer('text', { duration: dur }); openProps(); break;
      case 'shape': ui.setSheet('shape'); ui.setAddPage('shape'); break;
      case 'freehand': ui.setSheet('draw'); break;
      case 'vector': ui.setSheet('vector'); break;
      case 'element': ui.setSheet('elements'); break;
      case 'template': ui.setSheet('templates'); break;
    }
  };

  return (
    <Sheet variant="floating" title="Add" onClose={() => { ui.setSheet('none'); ui.setAddPage('add'); }} className="um-add-sheet">
      <div className="um-add-grid">
        {ADD_TILES.map(t => (
          <button type="button" key={t.id} className="um-add-tile" onClick={() => action(t.id)}>
            <span className="um-add-icon">{t.icon}</span>
            {'arrow' in t && t.arrow && <ChevronRight size={14} className="um-add-arrow" />}
            {'badge' in t && t.badge && <em className="um-new">NEW</em>}
            <strong>{t.label}</strong>
          </button>
        ))}
      </div>
      <div className="um-add-row">
        <button type="button" className="um-add-pill" onClick={() => {
          e.addLayer('camera', { name: `Camera ${e.project.layers.filter(l => l.type === 'camera').length + 1}`, duration: e.project.duration });
          openProps();
        }}>
          <Camera size={17} strokeWidth={1.6} /> Camera {cameraExists && <i className="um-teal-dot" />}
        </button>
        <button type="button" className="um-add-pill" onClick={() => { e.addLayer('group', { name: 'Null', duration: dur }); openProps(); }}>
          <Plus size={17} strokeWidth={1.6} /> Null
        </button>
        <button type="button" className="um-add-pill" onClick={() => { e.addLayer('group', { name: 'Empty Group', duration: dur }); openProps(); }}>
          <span className="um-dotted-box" /> Empty Group
        </button>
      </div>
      <input ref={mediaRef} type="file" hidden multiple accept="image/*,video/*,.ttf,.otf,.woff,.woff2"
        onChange={ev => { importFiles(ev.target.files); ev.target.value = ''; }} />
      <input ref={audioRef} type="file" hidden multiple accept="audio/*"
        onChange={ev => { importFiles(ev.target.files); ev.target.value = ''; }} />
    </Sheet>
  );
}

/* --------------------------------- shapes --------------------------------- */
const EXTRA_SHAPES: ShapeKind[] = [
  'diamond', 'pentagon', 'sparkle', 'burst', 'heart', 'ring',
  'halfCircle', 'polygon', 'customPath',
];

function ShapeSheet() {
  const e = useEditor();
  const ui = useUI();
  const [page, setPage] = useState(0);
  const dur = ui.prefs.defaultLayerDuration;

  const pick = (shape: ShapeKind) => {
    if (shape === 'customPath') { ui.setSheet('vector'); return; }
    const id = e.addLayer('shape', { shape, duration: dur, color: '#d9a15b' });
    e.setStyle(id, { fillType: 'solid', color: '#d9a15b', strokeColor: '#ffffff' });
    e.setProp(id, 'cornerRadius', shape === 'rounded' || shape === 'rectangle' ? 110 : 60);
    e.setProp(id, 'strokeWidth', shape === 'line' || shape === 'beam' ? 8 : 5);
    ui.setSheet('properties');
    ui.setSelectedIds([]);
    ui.setAddPage('shape');
  };

  const pageItems: { shape: ShapeKind; label: string; n?: number }[] = page === 0
    ? SHAPE_PICKER.map(x => ({ shape: x.kind, label: x.label }))
    : page === 1
      ? EXTRA_SHAPES.filter(s => s !== 'customPath').map(s => ({ shape: s, label: s }))
      : [3, 4, 5, 6, 7, 8].map(n => ({ shape: 'polygon' as ShapeKind, label: `${n}-sided polygon`, n }))
        .concat([3, 4, 6, 8].map(n => ({ shape: 'star' as ShapeKind, label: `${n}-point star`, n })));

  return (
    <Sheet variant="floating" title="Shape" onBack={() => { ui.setSheet('add'); ui.setAddPage('add'); }}
      onClose={() => { ui.setSheet('none'); ui.setAddPage('shape'); }} className="um-shape-sheet">
      <div className="um-shape-grid">
        {pageItems.map((item, i) => (
          <button type="button" key={`${item.label}-${i}`} title={item.label} className="um-shape-cell"
            onClick={() => {
              if (item.n !== undefined) {
                const id = e.addLayer('shape', { shape: item.shape, duration: dur, color: '#d9a15b' });
                e.setStyle(id, { fillType: 'solid', color: '#d9a15b', strokeColor: '#ffffff' });
                e.setProp(id, 'strokeWidth', 5);
                e.setProp(id, item.shape === 'star' ? 'starPoints' : 'polygonSides', item.n);
                ui.setSheet('properties');
              } else pick(item.shape);
            }}>
            <ShapeGlyph kind={item.shape} size={34} />
          </button>
        ))}
      </div>
      <div className="um-dots">
        {[0, 1, 2].map(p => <button type="button" key={p} title={`Shape page ${p + 1}`}
          className={page === p ? 'on' : ''} onClick={() => setPage(p)} />)}
      </div>
    </Sheet>
  );
}

/* --------------------------------- drawing -------------------------------- */
function DrawSheet() {
  const e = useEditor();
  const ui = useUI();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const strokes = useRef<string[]>([]);
  const [color, setColor] = useState('#ffffff');
  const [size, setSize] = useState(8);
  const [, force] = useState(0);

  const ctx = () => canvasRef.current?.getContext('2d') ?? null;
  const pos = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const r = ev.currentTarget.getBoundingClientRect();
    return { x: (ev.clientX - r.left) * ev.currentTarget.width / r.width, y: (ev.clientY - r.top) * ev.currentTarget.height / r.height };
  };
  const redraw = () => {
    const c = ctx(), cv = canvasRef.current;
    if (!c || !cv) return;
    c.clearRect(0, 0, cv.width, cv.height);
    c.fillStyle = '#111114'; c.fillRect(0, 0, cv.width, cv.height);
    for (const s of strokes.current) {
      const pts = JSON.parse(s) as { x: number; y: number; color: string; size: number }[];
      c.strokeStyle = pts[0].color; c.lineWidth = pts[0].size;
      c.lineCap = 'round'; c.lineJoin = 'round'; c.beginPath();
      pts.forEach((p, i) => i ? c.lineTo(p.x, p.y) : c.moveTo(p.x, p.y));
      c.stroke();
    }
  };
  const begin = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    drawing.current = true;
    ev.currentTarget.setPointerCapture(ev.pointerId);
    const p = pos(ev);
    strokes.current.push(JSON.stringify([{ ...p, color, size }]));
    redraw();
  };
  const move = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const p = pos(ev);
    const last = strokes.current[strokes.current.length - 1];
    const pts = JSON.parse(last) as { x: number; y: number }[];
    pts.push(p);
    strokes.current[strokes.current.length - 1] = JSON.stringify(pts);
    redraw();
  };

  return (
    <Sheet variant="floating" title="Freehand Drawing" onBack={() => ui.setSheet('add')}
      onClose={() => ui.setSheet('none')} className="um-draw-sheet">
      <canvas ref={canvasRef} width={640} height={420} className="um-pad"
        onPointerDown={begin} onPointerMove={move}
        onPointerUp={() => { drawing.current = false; force(n => n + 1); }}
        onPointerCancel={() => { drawing.current = false; }} />
      <div className="um-draw-controls">
        <div className="um-swatches">
          {PALETTE.slice(0, 10).map(c => (
            <button type="button" key={c} title={c} style={{ background: c }}
              className={color === c ? 'on' : ''} onClick={() => setColor(c)} />
          ))}
        </div>
        <label className="um-brush">Brush <input type="range" min={2} max={40} value={size}
          aria-label="Brush size" onChange={ev => setSize(Number(ev.target.value))} /><b>{size}</b></label>
      </div>
      <div className="um-action-grid">
        <Btn variant="dark" onClick={() => { strokes.current.pop(); redraw(); force(n => n + 1); }}>Undo stroke</Btn>
        <Btn variant="dark" onClick={() => { strokes.current = []; redraw(); force(n => n + 1); }}>Clear</Btn>
        <Btn onClick={() => {
          const cv = canvasRef.current;
          if (!cv || !strokes.current.length) { e.notify('Draw something first', 'warn'); return; }
          const out = document.createElement('canvas');
          out.width = cv.width; out.height = cv.height;
          const c = out.getContext('2d')!;
          for (const s of strokes.current) {
            const pts = JSON.parse(s) as { x: number; y: number; color: string; size: number }[];
            c.strokeStyle = pts[0].color; c.lineWidth = pts[0].size;
            c.lineCap = 'round'; c.lineJoin = 'round'; c.beginPath();
            pts.forEach((p, i) => i ? c.lineTo(p.x, p.y) : c.moveTo(p.x, p.y));
            c.stroke();
          }
          addDrawing(out.toDataURL('image/png'));
          ui.setSheet('properties');
          e.notify('Drawing added as an image layer', 'ok');
        }}>Add drawing</Btn>
      </div>
    </Sheet>
  );
}

/* -------------------------------- vector pen ------------------------------ */
function VectorSheet() {
  const e = useEditor();
  const ui = useUI();
  const [pts, setPts] = useState<{ x: number; y: number }[]>([]);
  const [closed, setClosed] = useState(true);
  const padRef = useRef<HTMLDivElement>(null);

  const addPoint = (ev: React.MouseEvent<HTMLDivElement>) => {
    const r = padRef.current!.getBoundingClientRect();
    setPts(p => [...p, { x: Math.min(1, Math.max(0, (ev.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (ev.clientY - r.top) / r.height)) }]);
  };
  const pathData = () => pts.length < 3 ? '' :
    `M ${pts.map(p => `${p.x.toFixed(4)} ${p.y.toFixed(4)}`).join(' L ')}${closed ? ' Z' : ''}`;

  return (
    <Sheet variant="floating" title="Vector Drawing" onBack={() => ui.setSheet('shape')}
      onClose={() => ui.setSheet('none')} className="um-draw-sheet">
      <div className="um-pad um-vector-pad" ref={padRef} onClick={addPoint}>
        <svg viewBox="0 0 1 1" preserveAspectRatio="none">
          {pathData() && <path d={pathData()} fill="rgba(200,200,242,.25)" stroke="#e8e8f6" strokeWidth="0.005" />}
          {pts.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r="0.012" fill="#02ffa7" />)}
        </svg>
        {pts.length === 0 && <span>Tap to place points</span>}
      </div>
      <div className="um-draw-controls">
        <label className="um-checkline"><input type="checkbox" checked={closed} onChange={ev => setClosed(ev.target.checked)} /> Close path</label>
        <span className="um-hint">{pts.length} point{pts.length === 1 ? '' : 's'}</span>
      </div>
      <div className="um-action-grid">
        <Btn variant="dark" onClick={() => setPts([])}>Clear points</Btn>
        <Btn variant="dark" onClick={() => setPts(p => p.slice(0, -1))}>Undo point</Btn>
        <Btn onClick={() => {
          const d = pathData();
          if (!d) { e.notify('Place at least 3 points', 'warn'); return; }
          addVectorPath(d);
          ui.setSheet('properties');
          e.notify('Vector shape added', 'ok');
        }}>Add vector shape</Btn>
      </div>
    </Sheet>
  );
}

/* ------------------------------- overflow menu ---------------------------- */
function OverflowMenu() {
  const e = useEditor();
  const ui = useUI();
  const [, force] = useState(0);
  const layer = e.selectedLayer;
  const ids = ui.selectedIds.length ? ui.selectedIds : layer ? [layer.id] : [];
  const count = ids.length;
  const close = () => ui.setSheet('none');

  const items: { label: string; icon: React.ReactNode; run: () => void; badge?: string; disabled?: boolean }[] = [
    { label: 'Select All Layers', icon: <Layers size={17} />, run: () => { ui.setSelectedIds(e.project.layers.map(l => l.id)); e.notify(`${e.project.layers.length} layers selected`, 'ok'); close(); } },
    { label: `Duplicate ${count} Layer${count === 1 ? '' : 's'}`, icon: <Copy size={17} />, disabled: !count,
      run: () => { e.beginBatch(); ids.forEach(id => e.duplicateLayer(id)); e.endBatch(); close(); e.notify(`${count} layer(s) duplicated`, 'ok'); } },
    { label: 'Save Preset', icon: <FileCode2 size={17} />, badge: 'NEW', disabled: !layer,
      run: () => { void saveSelectedLayerPreset().then(() => e.notify('Preset XML downloaded', 'ok')).catch(err => e.notify((err as Error).message, 'warn')); close(); } },
    { label: `Copy ${count} Layer${count === 1 ? '' : 's'}`, icon: <Copy size={17} />, disabled: !count,
      run: () => { layerClipboard = ids.map(id => e.layer(id)).filter(Boolean).map(l => structuredClone(l!)); clipboardVersion++; e.notify(`${layerClipboard.length} layer(s) copied`, 'ok'); force(n => n + 1); close(); } },
    { label: 'Paste Layer', icon: <FolderOpen size={17} />, disabled: !layerClipboard,
      run: () => { if (!layerClipboard) return; e.addImportedLayers(layerClipboard.map(l => structuredClone(l))); clipboardVersion++; close(); e.notify('Layers pasted', 'ok'); } },
    { label: 'Paste Style', icon: <Sparkles size={17} />, disabled: !layerClipboard || !layer,
      run: () => {
        const src = layerClipboard?.[0];
        if (!src || !layer) return;
        e.beginBatch();
        e.setStyle(layer.id, { ...src.style });
        e.setBlend(layer.id, src.blend);
        for (const [k, a] of Object.entries(src.props)) e.setProp(layer.id, k, a.value, false);
        e.patchLayer(layer.id, { effects: src.effects.map(f => ({ ...structuredClone(f), id: `fx-${Date.now()}-${Math.random().toString(36).slice(2)}` })) });
        e.endBatch(); close(); e.notify('Style pasted', 'ok');
      } },
    { label: 'Split at playhead', icon: <Scissors size={17} />, disabled: !layer,
      run: () => { if (layer) e.splitLayer(layer.id); close(); } },
    { label: 'Add keyframe', icon: <Diamond size={17} />, disabled: !layer,
      run: () => { if (layer) { e.addKeyframe(layer.id, ui.activePropKey); e.notify('Keyframe added', 'ok'); } close(); } },
    { label: 'Delete selected', icon: <Trash2 size={17} />, disabled: !count,
      run: () => { e.beginBatch(); ids.forEach(id => e.deleteLayer(id)); e.endBatch(); ui.setSelectedIds([]); close(); } },
    { label: 'Project Settings', icon: <FolderOpen size={17} />, run: () => ui.setSheet('projectSettings') },
    { label: 'Export media / XML', icon: <FileCode2 size={17} />, run: () => ui.setSheet('export') },
    { label: 'Import XML / Package', icon: <FileCode2 size={17} />, run: () => ui.setSheet('xml') },
    { label: 'Editing guide', icon: <Sparkles size={17} />, run: () => ui.setSheet('help') },
  ];

  return (
    <>
      <Backdrop onClick={close} dim={false} />
      <div className="um-overflow">
        {items.map((it, i) => (
          <button type="button" key={i} disabled={it.disabled} onClick={it.run}>
            <span className="um-overflow-icon">{it.icon}</span>
            <span className="um-overflow-label">{it.label}{it.label.includes('Copy') && <small>(including style)</small>}</span>
            {it.badge && <em className="um-new">{it.badge}</em>}
            <ChevronRight size={15} className="um-overflow-chevron" />
          </button>
        ))}
        <span className="um-clipboard-note">{clipboardVersion > 0 && layerClipboard ? `${layerClipboard.length} layer(s) in clipboard` : 'Clipboard is empty'}</span>
      </div>
    </>
  );
}

/* -------------------------------- dispatcher ------------------------------ */
export function EditorSheets() {
  const ui = useUI();
  switch (ui.sheet) {
    case 'add': return <><Backdrop onClick={() => { ui.setSheet('none'); ui.setAddPage('add'); }} dim={false} /><AddSheet /></>;
    case 'shape': return <><Backdrop onClick={() => ui.setSheet('none')} dim={false} /><ShapeSheet /></>;
    case 'draw': return <><Backdrop onClick={() => ui.setSheet('none')} /><DrawSheet /></>;
    case 'vector': return <><Backdrop onClick={() => ui.setSheet('none')} /><VectorSheet /></>;
    case 'overflow': return <OverflowMenu />;
    default: return null;
  }
}
