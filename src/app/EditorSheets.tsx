import React, { useRef, useState } from 'react';
import {
  ArrowLeft, Camera, ChevronRight, Copy, Diamond, FileCode2, Images,
  FolderOpen, Layers, LayoutTemplate, Music2, PencilLine, PenTool, Plus,
  Scissors, Shapes, Sparkles, Triangle, Trash2, Type, X,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { SHAPE_PICKER, type Layer, type ShapeKind } from '../editor/types';
import { useUI } from './context';
import { Backdrop, Btn, IBtn, PALETTE, Sheet } from './widgets';
import { ShapeGlyph } from './ShapeGlyph';
import {
  addDrawing, addMediaFiles, addVectorPath, saveSelectedLayerPreset, savePreset,
} from './lib';

/* Multi-layer clipboard. Operations are applied to the real project. */
let layerClipboard: Layer[] | null = null;
let clipboardVersion = 0;

const ADD_TILES = [
  { id: 'media', label: 'Media', icon: <Images size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'text', label: 'Text', icon: <Type size={21} strokeWidth={1.5} /> },
  { id: 'shape', label: 'Shape', icon: <Shapes size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'audio', label: 'Audio', icon: <Music2 size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'freehand', label: 'Freehand Drawing', icon: <PencilLine size={21} strokeWidth={1.5} /> },
  { id: 'vector', label: 'Vector Drawing', icon: <PenTool size={21} strokeWidth={1.5} /> },
  { id: 'element', label: 'Element / Project', icon: <Triangle size={21} strokeWidth={1.5} />, arrow: true },
  { id: 'template', label: 'Template', icon: <LayoutTemplate size={21} strokeWidth={1.5} />, arrow: true, badge: 'NEW' },
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
    <Sheet variant="floating" title="Add" className="um-add-sheet">
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
          <Camera size={17} strokeWidth={1.6} /> Camera <i className="um-teal-dot" />
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

/** Original vector silhouettes in the renderer's normalized 0..1 box. */
const VECTOR_PRESETS: { label: string; d: string }[] = [
  { label: 'Chevron', d: 'M0 .15 L.55 .15 L1 .5 L.55 .85 L0 .85 L.45 .5 Z' },
  { label: 'Lightning', d: 'M.58 0 L.12 .55 L.42 .55 L.28 1 L.9 .42 L.56 .42 Z' },
  { label: 'Droplet', d: 'M.5 0 C.78 .38 1 .6 1 .76 A.5 .24 0 0 1 0 .76 C0 .6 .22 .38 .5 0 Z' },
  { label: 'Cloud', d: 'M.22 .78 A.22 .22 0 0 1 .24 .35 A.28 .28 0 0 1 .72 .3 A.24 .24 0 0 1 .8 .78 Z' },
  { label: 'Shield', d: 'M.5 0 L1 .16 L.92 .7 L.5 1 L.08 .7 L0 .16 Z' },
  { label: 'Bookmark', d: 'M.16 0 H.84 V1 L.5 .74 L.16 1 Z' },
  { label: 'Parallelogram', d: 'M.25 0 H1 L.75 1 H0 Z' },
  { label: 'Quarter disc', d: 'M0 0 H1 A1 1 0 0 0 0 1 Z' },
  { label: 'Speech bubble', d: 'M.08 0 H.92 A.08 .08 0 0 1 1 .08 V.72 A.08 .08 0 0 1 .92 .8 H.36 L.16 1 V.8 H.08 A.08 .08 0 0 1 0 .72 V.08 A.08 .08 0 0 1 .08 0 Z' },
  { label: 'Leaf', d: 'M0 1 C0 .35 .4 0 1 0 C1 .6 .6 1 0 1 Z' },
  { label: 'Octagon', d: 'M.3 0 H.7 L1 .3 V.7 L.7 1 H.3 L0 .7 V.3 Z' },
  { label: 'Concave diamond', d: 'M.5 0 L.58 .42 L1 .5 L.58 .58 L.5 1 L.42 .58 L0 .5 L.42 .42 Z' },
  { label: 'Arrowhead', d: 'M0 .3 L.6 .3 L.6 0 L1 .5 L.6 1 L.6 .7 L0 .7 Z' },
  { label: 'Crown', d: 'M0 .28 L.22 .55 L.5 0 L.78 .55 L1 .28 L1 1 L0 1 Z' },
  { label: 'Hourglass', d: 'M0 0 H1 L.58 .5 L1 1 H0 L.42 .5 Z' },
  { label: 'Sector', d: 'M.5 .5 L.5 0 A.5 .5 0 0 1 1 .5 Z' },
  { label: 'Notched square', d: 'M0 0 H1 V1 H.62 L.5 .78 L.38 1 H0 Z' },
  { label: 'Wave', d: 'M0 .28 C.25 0 .35 .62 .6 .34 C.8 .1 .9 .4 1 .28 V1 H0 Z' },
];

function ShapeSheet() {
  const e = useEditor();
  const ui = useUI();
  const [page, setPage] = useState(0);
  const dur = ui.prefs.defaultLayerDuration;

  const labelOf = (shape: ShapeKind) =>
    (SHAPE_PICKER.find(x => x.kind === shape)?.label ?? 'Shape')
      .replace(/\b\w/g, c => c.toUpperCase());
  const nextName = (base: string) =>
    `${base} ${e.project.layers.filter(l => l.name.startsWith(base)).length + 1}`;

  const pick = (shape: ShapeKind, label?: string) => {
    if (shape === 'customPath') { ui.setSheet('vector'); return; }
    const id = e.addLayer('shape', { shape, name: nextName(label ?? labelOf(shape)), duration: dur, color: '#ba7e38' });
    const lineShape = shape === 'line' || shape === 'beam';
    e.setStyle(id, { fillType: 'solid', color: '#ba7e38', strokeColor: '#000000', strokeEnabled: lineShape });
    e.setProp(id, 'cornerRadius', shape === 'rounded' ? 25 : 0);
    e.setProp(id, 'strokeWidth', lineShape ? 8 : 4);
    ui.setSheet('properties');
    ui.setSelectedIds([]);
    ui.setAddPage('shape');
  };

  const pageItems: { shape: ShapeKind; label: string; n?: number; path?: string }[] = page === 0
    ? SHAPE_PICKER.map(x => ({ shape: x.kind, label: x.label }))
    : page === 1
      ? [
          ...EXTRA_SHAPES.filter(s => s !== 'customPath').map(s => ({ shape: s, label: s })),
          ...[3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(n => ({ shape: 'polygon' as ShapeKind, label: `${n}-sided polygon`, n })),
        ]
      : [
          ...[3, 4, 5, 6, 7, 8, 9, 10].map(n => ({ shape: 'star' as ShapeKind, label: `${n}-point star`, n })),
          ...VECTOR_PRESETS.map(v => ({ shape: 'customPath' as ShapeKind, label: v.label, path: v.d })),
        ];

  return (
    <Sheet variant="floating" title="Shape" onBack={() => { ui.setSheet('add'); ui.setAddPage('add'); }}
      className="um-shape-sheet">
      <div className="um-shape-grid">
        {pageItems.map((item, i) => (
          <button type="button" key={`${item.label}-${i}`} title={item.label} className="um-shape-cell"
            onClick={() => {
              if (item.path) {
                const id = e.addLayer('shape', { shape: 'customPath', name: nextName(item.label), duration: dur, color: '#ba7e38' });
                e.setStyle(id, { fillType: 'solid', color: '#ba7e38', strokeColor: '#000000', strokeEnabled: false, pathData: item.path });
                e.setProp(id, 'strokeWidth', 4);
                ui.setSheet('properties'); ui.setSelectedIds([]); ui.setAddPage('shape');
              } else if (item.n !== undefined) {
                const id = e.addLayer('shape', { shape: item.shape, name: nextName(item.label), duration: dur, color: '#ba7e38' });
                e.setStyle(id, { fillType: 'solid', color: '#ba7e38', strokeColor: '#000000', strokeEnabled: false });
                e.setProp(id, 'strokeWidth', 4);
                e.setProp(id, item.shape === 'star' ? 'starPoints' : 'polygonSides', item.n);
                ui.setSheet('properties');
              } else pick(item.shape, item.label.replace(/\b\w/g, c => c.toUpperCase()));
            }}>
            {item.path
              ? <svg width={34} height={34} viewBox="0 0 1 1" aria-hidden="true"><path d={item.path} fill="#b9b9be" /></svg>
              : <ShapeGlyph kind={item.shape} size={48} editPoints />}
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
      run: () => {
        try { const p = savePreset(`${layer!.name} preset`); e.notify(`Preset “${p.name}” saved`, 'ok'); }
        catch (err) { e.notify((err as Error).message, 'warn'); }
        close(); ui.setPresetsDialog(true);
      } },
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
    { label: 'Replace media…', icon: <FolderOpen size={17} />,
      run: () => { close(); ui.setFlow('replaceMedia'); } },
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
    case 'add': return <><Backdrop onClick={() => { ui.setSheet('none'); ui.setAddPage('add'); }} /><AddSheet /></>;
    case 'shape': return <><Backdrop onClick={() => ui.setSheet('none')} /><ShapeSheet /></>;
    case 'draw': return <><Backdrop onClick={() => ui.setSheet('none')} /><DrawSheet /></>;
    case 'vector': return <><Backdrop onClick={() => ui.setSheet('none')} /><VectorSheet /></>;
    case 'overflow': return <OverflowMenu />;
    default: return null;
  }
}
