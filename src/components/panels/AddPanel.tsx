import React, { useEffect, useRef, useState } from 'react';
import {
  AudioLines, Circle, FileInput, FileVideo2, Film, Heart, Hexagon, ImagePlus, Layers,
  Minus, Music2, Plus, Shapes, Square, Star, Trash2, Triangle, Type, Upload, X,
  AArrowUp, LayoutGrid, WandSparkles, Brush, Undo2, Check, Diamond, Pentagon, Sparkles,
  Asterisk, CircleDashed, Moon, PieChart, MessageSquare, Flower2, Flag, BadgeCheck, PenTool,
} from 'lucide-react';
import { useEditor } from '../../editor/store';
import { assetFromDataUrl, createSampleVideo, importMany } from '../../editor/media';
import type { ShapeKind } from '../../editor/types';
import { SHAPE_LABELS } from '../../editor/types';
import { Btn, Empty } from '../ui';

const SHAPE_ICONS: Record<ShapeKind, React.ReactNode> = {
  rectangle: <Square size={19} />, ellipse: <Circle size={19} />, triangle: <Triangle size={19} />,
  star: <Star size={19} />, polygon: <Hexagon size={19} />, line: <Minus size={19} />,
  heart: <Heart size={19} />, arrow: <Minus size={19} />,
  rounded: <Square size={19} />, diamond: <Diamond size={19} />, pentagon: <Pentagon size={19} />,
  hexagon: <Hexagon size={19} />, sparkle: <Sparkles size={19} />, burst: <Asterisk size={19} />,
  cross: <Plus size={19} />, ring: <CircleDashed size={19} />, arc: <CircleDashed size={19} />,
  crescent: <Moon size={19} />, halfCircle: <Circle size={19} />, pie: <PieChart size={19} />,
  callout: <MessageSquare size={19} />, flower: <Flower2 size={19} />, trapezoid: <Triangle size={19} />,
  beam: <Minus size={19} />, flag: <Flag size={19} />, seal: <BadgeCheck size={19} />,
  customPath: <PenTool size={19} />,
};

type Cat = 'shape' | 'media' | 'audio' | 'object' | 'element' | 'drawing' | 'text';
const CATS: { id: Cat; label: string; icon: React.ReactNode }[] = [
  { id: 'shape', label: 'Shape', icon: <Shapes size={19} /> },
  { id: 'media', label: 'Media', icon: <ImagePlus size={19} /> },
  { id: 'audio', label: 'Audio', icon: <Music2 size={19} /> },
  { id: 'object', label: 'Object', icon: <Layers size={19} /> },
  { id: 'element', label: 'Element', icon: <WandSparkles size={19} /> },
  { id: 'drawing', label: 'Freehand Drawing', icon: <Brush size={19} /> },
];

export function AddPanel() {
  const e = useEditor();
  const mediaRef = useRef<HTMLInputElement>(null);
  const fontRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState('');

  const importFiles = async (files: FileList | File[]) => {
    if (!files?.length) return;
    setBusy('Importing media…');
    try {
      const assets = await importMany(files);
      if (!assets.length) { e.notify('No supported media files found', 'warn'); return; }
      e.addAssets(assets);
      for (const a of assets) {
        if (a.type === 'font') e.notify(`Font “${a.name}” installed`, 'ok');
        else e.addMediaLayer(a.id);
      }
      e.notify(`${assets.length} asset${assets.length > 1 ? 's' : ''} ready`, 'ok');
    } finally {
      setBusy('');
      if (mediaRef.current) mediaRef.current.value = '';
      if (fontRef.current) fontRef.current.value = '';
    }
  };

  const makeSample = async () => {
    setBusy('Rendering sample clip…');
    try {
      const asset = await createSampleVideo();
      if (!asset) { e.notify('Video generation is not supported in this browser', 'warn'); return; }
      e.addAsset(asset);
      e.addMediaLayer(asset.id);
      e.notify('Sample video clip added', 'ok');
    } finally { setBusy(''); }
  };

  const create = (type: 'text' | 'shape' | 'solid' | 'group' | 'audio', shape?: ShapeKind) => {
    const id = e.addLayer(type, shape ? { shape } : undefined);
    if (shape) e.setShape(id, shape);
    e.setPanel('edit');
  };

  const builtins = Object.values(e.assets).filter(a => a.builtin && a.type === 'image');
  const imported = Object.values(e.assets).filter(a => !a.builtin && a.type !== 'font');
  const fonts = Object.values(e.assets).filter(a => a.type === 'font');
  const cat = e.addCategory;

  return (
    <div className="am-add">
      {busy && <div className="busy-bar">{busy}</div>}
      <div className="am-add-cats">
        {CATS.map(c => (
          <button key={c.id} type="button" className={cat === c.id ? 'on' : ''}
            onClick={() => e.setAddCategory(c.id)} title={c.label}>
            {c.icon}<span>{c.label}</span>
          </button>
        ))}
      </div>

      <div className="am-add-main">
        {cat === 'shape' && (
          <>
            <div className="am-add-label">Shapes</div>
            <div className="shape-grid">
              {(Object.keys(SHAPE_LABELS) as ShapeKind[]).map(s => (
                <button key={s} type="button" className="shape-btn" title={SHAPE_LABELS[s]} onClick={() => create('shape', s)}>
                  {SHAPE_ICONS[s]}<span>{SHAPE_LABELS[s]}</span>
                </button>
              ))}
            </div>
            <div className="am-add-label">Other layers</div>
            <div className="tile-grid">
              <button type="button" className="tile" onClick={() => create('solid')}><Square size={21} /><span>Solid</span></button>
              <button type="button" className="tile" onClick={() => create('group')}><Layers size={21} /><span>Group</span></button>
            </div>
          </>
        )}

        {cat === 'media' && (
          <>
            <div className="am-import-row">
              <Btn variant="primary" onClick={() => mediaRef.current?.click()}><Upload size={15} /> Choose media</Btn>
              <Btn variant="soft" onClick={() => void makeSample()}><FileVideo2 size={15} /> Demo clip</Btn>
            </div>
            <p className="hint block">Images, videos and audio are stored locally in your browser. Files can also be dropped onto the preview.</p>
            <div className="am-add-label">Built-in backgrounds</div>
            <div className="builtin-grid">
              {builtins.map(a => (
                <button key={a.id} type="button" className="builtin-card" onClick={() => e.addMediaLayer(a.id)} title={`Add ${a.name}`}>
                  <img src={a.thumbnail || a.url} alt={a.name} />
                  <span><Plus size={12} /> {a.name}</span>
                </button>
              ))}
            </div>
            <MediaLibrary imported={imported} onAdd={a => e.addMediaLayer(a)} onRemove={id => void e.removeAsset(id)} />
          </>
        )}

        {cat === 'audio' && (
          <>
            <div className="am-import-row">
              <Btn variant="primary" onClick={() => mediaRef.current?.click()}><Upload size={15} /> Choose audio</Btn>
              <Btn variant="soft" onClick={() => create('audio')}><Music2 size={15} /> Audio layer</Btn>
            </div>
            <p className="hint block">Audio layers get real waveforms and are mixed into exported video.</p>
            <MediaLibrary imported={imported.filter(a => a.type === 'audio')} onAdd={a => e.addMediaLayer(a)} onRemove={id => void e.removeAsset(id)} />
          </>
        )}

        {cat === 'object' && (
          <>
            <div className="am-add-label">Objects</div>
            <div className="tile-grid">
              <button type="button" className="tile" onClick={() => create('group')}><Layers size={21} /><span>Group</span></button>
              <button type="button" className="tile" onClick={() => create('solid')}><Square size={21} /><span>Solid</span></button>
              <button type="button" className="tile" onClick={() => void makeSample()}><Film size={21} /><span>Demo clip</span></button>
            </div>
            <div className="am-add-label">Import / open</div>
            <div className="row-buttons">
              <Btn variant="soft" onClick={() => e.setModal('xml')}><FileInput size={15} /> Import XML</Btn>
              <Btn variant="soft" onClick={() => e.setModal('projects')}><LayoutGrid size={15} /> Projects</Btn>
            </div>
          </>
        )}

        {cat === 'element' && (
          <>
            <div className="am-add-label">Elements</div>
            <div className="tile-grid">
              <button type="button" className="tile" onClick={() => create('text')}><Type size={21} /><span>Text</span></button>
              <button type="button" className="tile" onClick={() => create('shape')}><Shapes size={21} /><span>Shape</span></button>
              <button type="button" className="tile" onClick={() => e.newProject(1080, 1920, false)}><WandSparkles size={21} /><span>Demo project</span></button>
            </div>
            <div className="am-add-label">Built-in backgrounds</div>
            <div className="builtin-grid">
              {builtins.map(a => (
                <button key={a.id} type="button" className="builtin-card" onClick={() => e.addMediaLayer(a.id)} title={`Add ${a.name}`}>
                  <img src={a.thumbnail || a.url} alt={a.name} />
                  <span><Plus size={12} /> {a.name}</span>
                </button>
              ))}
            </div>
          </>
        )}

        {cat === 'drawing' && <DrawingPad />}

        {cat === 'text' && (
          <>
            <div className="am-add-label">Text</div>
            <div className="tile-grid">
              <button type="button" className="tile" onClick={() => create('text')}><Type size={21} /><span>Text layer</span></button>
              <button type="button" className="tile" onClick={() => fontRef.current?.click()}><Upload size={21} /><span>Add font</span></button>
            </div>
            {fonts.length > 0 && (
              <>
                <div className="am-add-label">Installed fonts</div>
                {fonts.map(f => <div className="font-row" key={f.id}>{f.name}<small style={{ fontFamily: f.fontFamily }}>Aa</small></div>)}
              </>
            )}
            {fonts.length === 0 && <p className="hint block">Import a .ttf, .otf or .woff font, then select it in the Edit → Style panel.</p>}
          </>
        )}
      </div>

      <div className="am-add-rail">
        <button type="button" title="All categories" onClick={() => e.setAddCategory('media')}><LayoutGrid size={19} /></button>
        <button type="button" title="Add text" onClick={() => create('text')}><Type size={19} /></button>
        <button type="button" title="Add vector shape" onClick={() => create('shape')}><Shapes size={19} /></button>
        <button type="button" title="Add audio" onClick={() => create('audio')}><Music2 size={19} /></button>
        <button type="button" className="close" title="Close add menu" onClick={() => e.toggleSheet()}><X size={19} /></button>
      </div>

      <input ref={mediaRef} type="file" multiple hidden accept="image/*,video/*,audio/*" onChange={ev => ev.target.files && void importFiles(ev.target.files)} />
      <input ref={fontRef} type="file" hidden accept=".ttf,.otf,.woff,.woff2" onChange={ev => ev.target.files && void importFiles(ev.target.files)} />
    </div>
  );
}

/** Real freehand drawing: strokes are rasterised into a PNG image layer. */
function DrawingPad() {
  const e = useEditor();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const history = useRef<string[]>([]);
  const [color, setColor] = useState('#10e0a0');
  const [size, setSize] = useState(8);
  const [hasInk, setHasInk] = useState(false);
  const ratio = e.project.width / e.project.height;
  const H = 640;
  const W = Math.max(120, Math.round(H * ratio));

  useEffect(() => {
    const c = canvasRef.current;
    if (!c) return;
    const ctx = c.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, c.width, c.height);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, c.width, c.height);
  }, []);

  const pos = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const c = canvasRef.current!;
    const r = c.getBoundingClientRect();
    return { x: (ev.clientX - r.left) * c.width / r.width, y: (ev.clientY - r.top) * c.height / r.height };
  };
  const ctx = () => canvasRef.current?.getContext('2d') ?? null;
  const start = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const c = canvasRef.current;
    if (!c) return;
    history.current.push(c.toDataURL('image/png'));
    if (history.current.length > 12) history.current.shift();
    drawing.current = true;
    c.setPointerCapture(ev.pointerId);
    const g = ctx();
    if (!g) return;
    const { x, y } = pos(ev);
    g.beginPath(); g.moveTo(x, y);
    g.strokeStyle = color; g.lineWidth = size; g.lineCap = 'round'; g.lineJoin = 'round';
    g.lineTo(x + 0.1, y + 0.1); g.stroke();
    setHasInk(true);
  };
  const move = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const g = ctx();
    if (!g) return;
    const { x, y } = pos(ev);
    g.lineTo(x, y); g.stroke();
  };
  const end = () => { drawing.current = false; };
  const clear = () => {
    const c = canvasRef.current; const g = ctx();
    if (!c || !g) return;
    history.current.push(c.toDataURL('image/png'));
    g.fillStyle = '#ffffff'; g.fillRect(0, 0, c.width, c.height);
    setHasInk(false);
  };
  const undo = () => {
    const c = canvasRef.current; const g = ctx();
    const prev = history.current.pop();
    if (!c || !g || !prev) return;
    const img = new Image();
    img.onload = () => { g.clearRect(0, 0, c.width, c.height); g.drawImage(img, 0, 0); };
    img.src = prev;
    setHasInk(history.current.length > 0);
  };
  const add = () => {
    const c = canvasRef.current;
    if (!c) return;
    const asset = assetFromDataUrl('Drawing.png', c.toDataURL('image/png'), 'image', { width: c.width, height: c.height });
    if (!asset) { e.notify('Could not create the drawing', 'warn'); return; }
    e.addAsset(asset);
    e.addMediaLayer(asset.id);
    e.setPanel('edit');
    e.notify('Drawing added as an image layer', 'ok');
  };

  return (
    <>
      <div className="am-add-label">Freehand drawing</div>
      <p className="hint block">Draw with your finger or mouse. The strokes become a real image layer.</p>
      <canvas
        ref={canvasRef} width={W} height={H} className="am-draw-canvas"
        style={{ aspectRatio: `${W} / ${H}` }}
        onPointerDown={start} onPointerMove={move} onPointerUp={end} onPointerCancel={end}
      />
      <div className="am-draw-tools">
        <input type="color" value={color} onChange={ev => setColor(ev.target.value)} title="Brush colour" />
        <label>Size <input type="range" min={2} max={40} value={size} onChange={ev => setSize(Number(ev.target.value))} /></label>
        <button type="button" className="btn btn-soft" onClick={undo} title="Undo stroke"><Undo2 size={14} /> Undo</button>
        <button type="button" className="btn btn-soft" onClick={clear} title="Clear canvas">Clear</button>
        <button type="button" className="btn btn-primary" onClick={add} disabled={!hasInk} title="Add drawing to timeline"><Check size={14} /> Add drawing</button>
      </div>
    </>
  );
}

function MediaLibrary({ imported, onAdd, onRemove }: {
  imported: { id: string; name: string; type: string; thumbnail?: string; duration?: number; width?: number }[];
  onAdd: (id: string) => void; onRemove: (id: string) => void;
}) {
  return (
    <>
      <div className="am-add-label">Media library ({imported.length})</div>
      {imported.length === 0 ? (
        <Empty icon={<ImagePlus size={24} />} title="Nothing imported yet" text="Import photos, videos or music to use them in your composition." />
      ) : (
        <div className="asset-list">
          {imported.map(a => (
            <div className="asset-row" key={a.id}>
              <div className="asset-thumb">
                {a.thumbnail ? <img src={a.thumbnail} alt="" /> : a.type === 'audio' ? <AudioLines size={18} /> : <Film size={18} />}
              </div>
              <div className="asset-meta">
                <strong title={a.name}>{a.name}</strong>
                <span>{a.type}{a.duration ? ` · ${a.duration.toFixed(1)}s` : ''}{a.width ? ` · ${a.width}px` : ''}</span>
              </div>
              <Btn variant="soft" onClick={() => onAdd(a.id)} title="Add to timeline"><Plus size={14} /></Btn>
              <button type="button" className="icon-btn danger" title="Remove asset" onClick={() => onRemove(a.id)}><Trash2 size={14} /></button>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
