import React, { useRef, useState } from 'react';
import {
  ArrowRight, AudioLines, Circle, FileInput, FileVideo2, Film, Heart, Hexagon,
  ImagePlus, Layers, Minus, Music2, Plus, Shapes, Sparkles, Square, Star, Trash2,
  Triangle, Type, Upload, WandSparkles,
} from 'lucide-react';
import { useEditor } from '../../editor/store';
import { createSampleVideo, importMany } from '../../editor/media';
import type { ShapeKind } from '../../editor/types';
import { SHAPE_LABELS } from '../../editor/types';
import { Btn, Empty, Section } from '../ui';

const SHAPE_ICONS: Record<ShapeKind, React.ReactNode> = {
  rectangle: <Square size={18} />, ellipse: <Circle size={18} />, triangle: <Triangle size={18} />,
  star: <Star size={18} />, polygon: <Hexagon size={18} />, line: <Minus size={18} />,
  heart: <Heart size={18} />, arrow: <ArrowRight size={18} />,
};

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

  return (
    <div className="panel-body">
      {busy && <div className="busy-bar">{busy}</div>}

      <Section title="Import media" right={<span className="hint">Images · Video · Audio · Fonts</span>}>
        <div className="import-buttons">
          <Btn variant="primary" onClick={() => mediaRef.current?.click()}><Upload size={15} /> Choose files</Btn>
          <Btn variant="soft" onClick={() => fontRef.current?.click()}><Type size={15} /> Add font</Btn>
        </div>
        <p className="hint block">Media is stored locally in your browser (IndexedDB) and stays in your project after a reload. You can also drag files onto the preview.</p>
        <input ref={mediaRef} type="file" multiple hidden accept="image/*,video/*,audio/*" onChange={ev => ev.target.files && void importFiles(ev.target.files)} />
        <input ref={fontRef} type="file" hidden accept=".ttf,.otf,.woff,.woff2" onChange={ev => ev.target.files && void importFiles(ev.target.files)} />
      </Section>

      <Section title="New layer">
        <div className="tile-grid">
          <button type="button" className="tile" onClick={() => create('text')}><Type size={21} /><span>Text</span></button>
          <button type="button" className="tile" onClick={() => create('shape')}><Shapes size={21} /><span>Shape</span></button>
          <button type="button" className="tile" onClick={() => create('solid')}><Square size={21} /><span>Solid</span></button>
          <button type="button" className="tile" onClick={() => create('group')}><Layers size={21} /><span>Group</span></button>
          <button type="button" className="tile" onClick={() => create('audio')}><Music2 size={21} /><span>Audio</span></button>
          <button type="button" className="tile" onClick={() => void makeSample()}><FileVideo2 size={21} /><span>Demo clip</span></button>
        </div>
        <div className="subhead">Shapes</div>
        <div className="shape-grid">
          {(Object.keys(SHAPE_LABELS) as ShapeKind[]).map(s => (
            <button key={s} type="button" className="shape-btn" title={SHAPE_LABELS[s]} onClick={() => create('shape', s)}>
              {SHAPE_ICONS[s]}<span>{SHAPE_LABELS[s]}</span>
            </button>
          ))}
        </div>
      </Section>

      <Section title="Built-in backgrounds" defaultOpen={false}>
        <div className="builtin-grid">
          {builtins.map(a => (
            <button key={a.id} type="button" className="builtin-card" onClick={() => e.addMediaLayer(a.id)} title={`Add ${a.name}`}>
              <img src={a.thumbnail || a.url} alt={a.name} />
              <span><Plus size={12} /> {a.name}</span>
            </button>
          ))}
        </div>
      </Section>

      <Section title={`Media library (${imported.length})`} defaultOpen={false}>
        {imported.length === 0 ? (
          <Empty icon={<ImagePlus size={26} />} title="Nothing imported yet" text="Import photos, videos or music to use them in your composition." />
        ) : (
          <div className="asset-list">
            {imported.map(a => (
              <div className="asset-row" key={a.id}>
                <div className="asset-thumb">
                  {a.thumbnail ? <img src={a.thumbnail} alt="" /> : a.type === 'audio' ? <AudioLines size={18} /> : <Film size={18} />}
                </div>
                <div className="asset-meta">
                  <strong title={a.name}>{a.name}</strong>
                  <span>{a.type}{a.duration ? ` · ${a.duration.toFixed(1)}s` : ''}{a.width ? ` · ${a.width}×${a.height}` : ''}</span>
                </div>
                <Btn variant="soft" onClick={() => e.addMediaLayer(a.id)} title="Add to timeline"><Plus size={14} /></Btn>
                <IconButtonDelete onClick={() => void e.removeAsset(a.id)} />
              </div>
            ))}
          </div>
        )}
        {fonts.length > 0 && (
          <>
            <div className="subhead">Installed fonts</div>
            {fonts.map(f => <div className="font-row" key={f.id}>{f.name}<small style={{ fontFamily: f.fontFamily }}>Aa</small></div>)}
          </>
        )}
      </Section>

      <Section title="Import / open" defaultOpen={false}>
        <div className="row-buttons">
          <Btn variant="soft" onClick={() => e.setModal('xml')}><FileInput size={15} /> Import XML</Btn>
          <Btn variant="soft" onClick={() => e.setModal('projects')}><FolderIcon /> Projects</Btn>
          <Btn variant="soft" onClick={() => { e.newProject(1080, 1920, false); e.setPanel('add'); }}><Sparkles size={15} /> Demo project</Btn>
        </div>
      </Section>
    </div>
  );
}

function IconButtonDelete({ onClick }: { onClick: () => void }) {
  return <button type="button" className="icon-btn danger" title="Remove asset" onClick={onClick}><Trash2 size={14} /></button>;
}

function FolderIcon() { return <WandSparkles size={15} />; }
