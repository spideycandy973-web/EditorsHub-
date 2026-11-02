import React, { useRef, useState } from 'react';
import { ImagePlus, Minus, Plus, Maximize2, Layers3 } from 'lucide-react';
import { useEditor } from '../editor/store';
import { importMany } from '../editor/media';
import type { Renderer } from '../editor/renderer';
import { IconBtn } from './ui';

export function Preview({
  canvasRef, stageRef, rendererRef,
}: {
  canvasRef: React.RefObject<HTMLCanvasElement>;
  stageRef: React.RefObject<HTMLDivElement>;
  rendererRef: React.MutableRefObject<Renderer | null>;
}) {
  const e = useEditor();
  const [dragOver, setDragOver] = useState(false);
  const [importing, setImporting] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const layer = e.selectedLayer;

  const handleFiles = async (files: FileList | File[]) => {
    if (!files || !files.length) return;
    setImporting(true);
    try {
      const assets = await importMany(files);
      if (!assets.length) { e.notify('Unsupported file type', 'warn'); return; }
      e.addAssets(assets);
      for (const a of assets) {
        if (a.type === 'font') e.notify(`Font “${a.name}” installed — select it in Style`, 'ok');
        else e.addMediaLayer(a.id);
      }
      e.notify(`Imported ${assets.length} file${assets.length > 1 ? 's' : ''}`, 'ok');
    } finally {
      setImporting(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const onCanvasPointer = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    const r = rendererRef.current;
    if (!canvas || !r) return;
    const rect = canvas.getBoundingClientRect();
    const x = (ev.clientX - rect.left) / rect.width * e.project.width;
    const y = (ev.clientY - rect.top) / rect.height * e.project.height;
    const hit = r.hitTest(x, y, e.time);
    if (hit) e.selectLayer(hit);
  };

  return (
    <div
      ref={stageRef}
      className={`stage ${dragOver ? 'drag-over' : ''}`}
      onDragOver={ev => { ev.preventDefault(); setDragOver(true); }}
      onDragLeave={() => setDragOver(false)}
      onDrop={ev => { ev.preventDefault(); setDragOver(false); void handleFiles(ev.dataTransfer.files); }}
    >
      <div className="stage-topinfo">
        <span className="res-badge">{e.project.width} × {e.project.height}</span>
        <span className="res-badge subtle">{e.project.fps} FPS</span>
      </div>

      <div className="canvas-holder" style={{ transform: `scale(${e.previewZoom})` }}>
        <canvas ref={canvasRef} onPointerDown={onCanvasPointer} />
      </div>

      {e.showGrid && <div className="overlay-grid" />}
      {e.showGuides && <div className="overlay-guides"><div className="safe-area"><div className="title-safe" /></div></div>}

      {dragOver && <div className="drop-hint"><ImagePlus size={34} /><strong>Drop media to import</strong><span>Images · Videos · Audio · Fonts</span></div>}
      {importing && <div className="importing-badge">Reading media…</div>}

      <div className="stage-bottom-left">
        {layer ? (
          <span className="selected-chip"><Layers3 size={13} /> {layer.name}</span>
        ) : (
          <span className="selected-chip">No layers — add one below</span>
        )}
      </div>

      <div className="stage-zoom">
        <IconBtn title="Zoom out" onClick={() => e.setPreviewZoom(e.previewZoom - 0.1)}><Minus size={15} /></IconBtn>
        <span>{Math.round(e.previewZoom * 100)}%</span>
        <IconBtn title="Zoom in" onClick={() => e.setPreviewZoom(e.previewZoom + 0.1)}><Plus size={15} /></IconBtn>
        <IconBtn title="Reset zoom" onClick={() => e.setPreviewZoom(1)}><Maximize2 size={14} /></IconBtn>
        <IconBtn title="Import media" onClick={() => fileRef.current?.click()}><ImagePlus size={16} /></IconBtn>
      </div>

      <input
        ref={fileRef} type="file" multiple hidden
        accept="image/*,video/*,audio/*,.ttf,.otf,.woff,.woff2"
        onChange={ev => { if (ev.target.files) void handleFiles(ev.target.files); }}
      />
    </div>
  );
}
