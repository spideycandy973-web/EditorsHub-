import React, { useEffect, useRef, useState, type RefObject } from 'react';
import {
  ArrowLeft, ArrowLeftToLine, ArrowRightToLine, ChevronLeft, Copy, Diamond, Expand,
  Grid2X2, ImagePlus, Layers3, Minus, MoreHorizontal, Pause, Play, Plus, Redo2,
  ScanLine, Settings2, Trash2, Undo2, Upload, VideoOff, Volume2, ZoomIn,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import type { Renderer } from '../editor/renderer';
import type { Layer } from '../editor/types';
import { useUI } from './context';
import { IBtn } from './widgets';
import { PROPERTY_TITLES, PropertyPane } from './PropertySheets';
import { EditorSheets } from './EditorSheets';
import { addMediaFiles } from './lib';

const timecode = (t: number, fps: number) => {
  const total = Math.max(0, t);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = Math.floor(total % 60);
  const f = Math.floor((total - Math.floor(total)) * fps);
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

/* --------------------------------- top bar -------------------------------- */
function TopBar() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const propertyOpen = ui.sheet === 'property';

  if (propertyOpen) {
    return (
      <header className="um-editor-top">
        <IBtn title="Back to layer properties" onClick={() => ui.setSheet('properties')}><ArrowLeft size={20} strokeWidth={1.8} /></IBtn>
        <strong className="um-top-title">{PROPERTY_TITLES[ui.property]}</strong>
        <div className="um-top-spacer" />
        <IBtn title="Add keyframe" onClick={() => {
          if (!layer) { e.notify('Select a layer first', 'warn'); return; }
          e.addKeyframe(layer.id, ui.activePropKey);
          e.notify('Keyframe added', 'ok');
        }}><Diamond size={18} strokeWidth={1.7} /></IBtn>
      </header>
    );
  }

  return (
    <header className="um-editor-top">
      <IBtn title={layer ? 'Deselect layer' : 'Back to projects'} onClick={() => {
        if (layer) { e.selectLayer(null); ui.setSheet('none'); }
        else { e.saveNow(); e.setScreen('home'); e.setPlaying(false); ui.setSheet('none'); }
      }}><ArrowLeft size={20} strokeWidth={1.8} /></IBtn>
      <button type="button" className="um-top-title" title="Rename"
        onClick={() => { if (layer) ui.setSheet('rename'); else ui.setSheet('projectSettings'); }}>
        {layer ? layer.name : e.project.name}
      </button>
      <div className="um-top-spacer" />
      {layer ? (
        <>
          <IBtn title="Duplicate layer" onClick={() => e.duplicateLayer(layer.id)} className="um-dup">
            <Copy size={18} strokeWidth={1.7} /><i />
          </IBtn>
          <IBtn title="Delete layer" onClick={() => e.deleteLayer(layer.id)}><Trash2 size={18} strokeWidth={1.7} /></IBtn>
          <IBtn title="Layer options" onClick={() => ui.setSheet('overflow')} className="um-round-dark">
            <MoreHorizontal size={19} strokeWidth={1.8} />
          </IBtn>
        </>
      ) : (
        <>
          <IBtn title="Project settings" onClick={() => ui.setSheet('projectSettings')}><Settings2 size={19} strokeWidth={1.7} /></IBtn>
          <IBtn title="Export" onClick={() => ui.setSheet('export')}><Upload size={19} strokeWidth={1.7} /></IBtn>
        </>
      )}
    </header>
  );
}

/* --------------------------------- stage ---------------------------------- */
function RightToolbar() {
  const e = useEditor();
  const ui = useUI();
  const [muted, setMuted] = useState(false);
  const toggleMute = () => {
    const next = !muted;
    for (const el of e.media.allElements()) el.muted = next;
    setMuted(next);
    e.notify(next ? 'Media audio muted' : 'Media audio on', 'ok');
  };
  return (
    <div className="um-right-tools">
      <IBtn title="Fit preview" onClick={() => e.setPreviewZoom(1)}><Expand size={18} strokeWidth={1.6} /></IBtn>
      <IBtn title="Toggle grid" active={e.showGrid} onClick={() => e.toggleGrid()}><Grid2X2 size={18} strokeWidth={1.6} /></IBtn>
      <IBtn title="Layer properties" onClick={() => { if (e.selectedLayer) ui.setSheet('properties'); else ui.openAdd(); }}>
        <Layers3 size={18} strokeWidth={1.6} />
      </IBtn>
      <IBtn title={muted ? 'Unmute media' : 'Mute media'} active={muted} onClick={toggleMute}>
        {muted ? <VideoOff size={18} strokeWidth={1.6} /> : <Volume2 size={18} strokeWidth={1.6} />}
      </IBtn>
      <div className="um-zoom-box">
        <IBtn title="Zoom in" onClick={() => e.setPreviewZoom(e.previewZoom + 0.1)}><Plus size={17} /></IBtn>
        <span>{Math.round(e.previewZoom * 100)}%</span>
        <IBtn title="Zoom out" onClick={() => e.setPreviewZoom(e.previewZoom - 0.1)}><Minus size={17} /></IBtn>
      </div>
    </div>
  );
}

function Stage({ canvasRef, stageRef, rendererRef }: {
  canvasRef: RefObject<HTMLCanvasElement>; stageRef: RefObject<HTMLDivElement>;
  rendererRef: React.MutableRefObject<Renderer | null>;
}) {
  const e = useEditor();
  const ui = useUI();
  const fileRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const drag = useRef<{ id: string; x: number; y: number; px: number; py: number } | null>(null);

  const onPointerDown = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current, r = rendererRef.current;
    if (!canvas || !r) return;
    const rect = canvas.getBoundingClientRect();
    const x = (ev.clientX - rect.left) / rect.width * e.project.width;
    const y = (ev.clientY - rect.top) / rect.height * e.project.height;
    const hit = r.hitTest(x, y, e.time);
    if (hit) {
      e.selectLayer(hit);
      const l = e.layer(hit);
      if (l) {
        e.beginBatch();
        drag.current = { id: hit, x: ev.clientX, y: ev.clientY, px: e.getLayerProp(l, 'x'), py: e.getLayerProp(l, 'y') };
        (ev.target as HTMLElement).setPointerCapture(ev.pointerId);
      }
    } else e.selectLayer(null);
  };
  const onPointerMove = (ev: React.PointerEvent<HTMLCanvasElement>) => {
    const d = drag.current, canvas = canvasRef.current;
    if (!d || !canvas) return;
    const rect = canvas.getBoundingClientRect();
    const dx = (ev.clientX - d.x) * e.project.width / rect.width;
    const dy = (ev.clientY - d.y) * e.project.height / rect.height;
    e.setProp(d.id, 'x', d.px + dx, false);
    e.setProp(d.id, 'y', d.py + dy, false);
  };
  const onPointerUp = () => { if (drag.current) { drag.current = null; e.endBatch(); } };

  const cameraSelected = e.selectedLayer?.type === 'camera';

  return (
    <div className={`um-stage ${dragOver ? 'drag-over' : ''}`} ref={stageRef}
      onDragOver={ev => { ev.preventDefault(); setDragOver(true); }}
      onDragLeave={() => setDragOver(false)}
      onDrop={ev => {
        ev.preventDefault(); setDragOver(false);
        void addMediaFiles(ev.dataTransfer.files).then(n => e.notify(`Imported ${n} file(s)`, 'ok'))
          .catch(err => e.notify((err as Error).message, 'warn'));
      }}>
      <button type="button" className="um-magnifier" title="Reset preview zoom / import media"
        onClick={() => e.setPreviewZoom(1)} onDoubleClick={() => fileRef.current?.click()}>
        <ZoomIn size={19} strokeWidth={1.7} />
      </button>
      <div className="um-canvas-holder" style={{ transform: `scale(${e.previewZoom})` }}>
        <canvas ref={canvasRef} onPointerDown={onPointerDown} onPointerMove={onPointerMove}
          onPointerUp={onPointerUp} onPointerCancel={onPointerUp} />
        {e.showGuides && <div className="um-safe-area"><div /></div>}
        {cameraSelected && <svg className="um-camera-x" viewBox="0 0 100 100" preserveAspectRatio="none">
          <line x1="0" y1="0" x2="100" y2="100" /><line x1="100" y1="0" x2="0" y2="100" />
        </svg>}
      </div>
      {e.showGrid && <div className="um-grid-overlay" />}
      {dragOver && <div className="um-drop-hint"><ImagePlus size={30} /> Drop media to import</div>}
      <RightToolbar />
      <input ref={fileRef} type="file" hidden multiple accept="image/*,video/*,audio/*,.ttf,.otf,.woff,.woff2"
        onChange={ev => {
          if (ev.target.files) void addMediaFiles(ev.target.files).then(n => e.notify(`Imported ${n} file(s)`, 'ok'))
            .catch(err => e.notify((err as Error).message, 'warn'));
          ev.target.value = '';
        }} />
      {ui.prefs.lowQualityPreview && <span className="um-lowq">LOW QUALITY PREVIEW</span>}
    </div>
  );
}

/* ------------------------------- transport -------------------------------- */
function Transport() {
  const e = useEditor();
  return (
    <div className="um-transport">
      <IBtn title="Undo (Ctrl+Z)" onClick={() => e.undo()}><Undo2 size={19} strokeWidth={1.6} /></IBtn>
      <IBtn title="Redo (Ctrl+Shift+Z)" onClick={() => e.redo()}><Redo2 size={19} strokeWidth={1.6} /></IBtn>
      <IBtn title="Go to start" onClick={() => e.goStart()}><ArrowLeftToLine size={19} strokeWidth={1.6} /></IBtn>
      <IBtn title={e.playing ? 'Pause' : 'Play'} className="play" onClick={() => e.togglePlay()}>
        {e.playing ? <Pause size={20} fill="currentColor" strokeWidth={1.4} /> : <Play size={20} fill="currentColor" strokeWidth={1.4} />}
      </IBtn>
      <IBtn title="Go to end" onClick={() => e.goEnd()}><ArrowRightToLine size={19} strokeWidth={1.6} /></IBtn>
      <IBtn title="Duplicate layer" onClick={() => {
        if (e.selectedLayer) e.duplicateLayer(e.selectedLayer.id);
        else e.notify('Select a layer to duplicate', 'warn');
      }}><Copy size={19} strokeWidth={1.6} /></IBtn>
      <IBtn title="Toggle safe-area frame" active={e.showGuides} onClick={() => e.toggleGuides()}>
        <ScanLine size={19} strokeWidth={1.6} />
      </IBtn>
    </div>
  );
}

/* -------------------------------- timeline -------------------------------- */
type DragMode = 'scrub' | 'move' | 'trimLeft' | 'trimRight';
interface DragState { mode: DragMode; id?: string; x: number; start: number; duration: number; inPoint: number; }

function Timeline() {
  const e = useEditor();
  const ui = useUI();
  const trackRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(390);
  const drag = useRef<DragState | null>(null);
  const zoom = e.timelineZoom;

  useEffect(() => {
    const el = trackRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el); setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  const half = width / 2;
  const visibleSecs = half / zoom + 1;
  const from = Math.max(0, e.time - visibleSecs);
  const to = Math.min(e.project.duration + 2, e.time + visibleSecs);
  const ticks: { t: number; major: boolean }[] = [];
  const step = zoom > 90 ? 0.25 : zoom > 40 ? 0.5 : 1;
  for (let t = Math.ceil(from / step) * step; t <= to; t += step) {
    ticks.push({ t: Math.round(t * 1000) / 1000, major: Math.abs(t % 1) < 0.001 });
  }

  const begin = (mode: DragMode, ev: React.PointerEvent, l?: Layer) => {
    ev.stopPropagation();
    (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
    drag.current = {
      mode, id: l?.id, x: ev.clientX,
      start: l?.start ?? 0, duration: l?.duration ?? 0, inPoint: l?.inPoint ?? 0,
    };
    if (mode !== 'scrub' && l) e.beginBatch();
  };
  const move = (ev: React.PointerEvent) => {
    const d = drag.current;
    if (!d) return;
    const dt = (ev.clientX - d.x) / zoom;
    if (d.mode === 'scrub') { e.setTime(e.time - dt, false); d.x = ev.clientX; return; }
    const id = d.id!;
    if (d.mode === 'move') e.setTiming(id, { start: d.start + dt }, false);
    else if (d.mode === 'trimLeft') {
      const ns = Math.max(0, Math.min(d.start + d.duration - 0.1, d.start + dt));
      e.setTiming(id, { start: ns, duration: d.duration - (ns - d.start), inPoint: d.inPoint + (ns - d.start) }, false);
    } else e.setTiming(id, { duration: Math.max(0.1, d.duration + dt) }, false);
  };
  const end = () => {
    const d = drag.current;
    if (!d) return;
    if (d.mode !== 'scrub') e.endBatch();
    drag.current = null;
  };

  const layerColor = (l: Layer) => l.type === 'camera' ? '#ff3b7f'
    : l.type === 'audio' ? '#a78bfa'
      : l.type === 'text' ? '#f5b642'
        : '#26d5dd';

  return (
    <div className="um-timeline" ref={trackRef}
      onPointerDown={ev => begin('scrub', ev)} onPointerMove={move} onPointerUp={end} onPointerCancel={end}>
      <div className="um-ruler">
        {ticks.map(({ t, major }) => (
          <span key={t} className={`um-tick ${major ? 'major' : ''}`}
            style={{ left: half + (t - e.time) * zoom }} />
        ))}
        {ui.prefs.showOffTimeline && Array.from({ length: Math.ceil(width / 16) }, (_, i) => (
          <span key={`d${i}`} className="um-dot" style={{ left: i * 16 }} />
        ))}
      </div>
      <div className="um-time-pill">{timecode(e.time, e.project.fps)}</div>
      <div className="um-playhead" />

      <div className="um-layer-rows">
        {e.project.layers.length === 0 && (
          <div className="um-layer-empty">No layers yet — tap + to add one</div>
        )}
        {e.project.layers.slice(0, 4).map(l => {
          const left = half + (l.start - e.time) * zoom;
          const w = Math.max(24, l.duration * zoom);
          const hasKf = Object.values(l.props).some(a => a.keyframes.length > 0);
          const selected = e.selectedLayerId === l.id || ui.selectedIds.includes(l.id);
          return (
            <div className={`um-layer-row ${selected ? 'selected' : ''}`} key={l.id}>
              <div className="um-layer-fixed">
                <IBtn title={l.visible ? 'Hide layer' : 'Show layer'} onClick={() => e.toggleVisible(l.id)}>
                  {l.visible ? '◉' : '◌'}
                </IBtn>
                <span className="um-layer-thumb" style={{ background:
                  ['shape','text','solid'].includes(l.type) ? l.style.color
                    : l.type === 'camera' ? '#ff3b7f'
                      : l.type === 'audio' ? '#a78bfa' : '#2c8f9b' }} />
              </div>
              <div className="um-clip-track">
                <div className={`um-clip ${selected ? 'on' : ''}`} style={{ left, width: w, background: layerColor(l) }}
                  onPointerDown={ev => { e.selectLayer(l.id); begin('move', ev, l); }}
                  onPointerMove={move} onPointerUp={end} onPointerCancel={end}>
                  <span className="um-clip-stripes" />
                  <span className="um-clip-name">{l.name}</span>
                  <button type="button" className="um-clip-trim left" title="Trim start"
                    onPointerDown={ev => begin('trimLeft', ev, l)} onPointerMove={move} onPointerUp={end} />
                  <button type="button" className="um-clip-trim right" title="Trim end"
                    onPointerDown={ev => begin('trimRight', ev, l)} onPointerMove={move} onPointerUp={end} />
                </div>
                {hasKf && (
                  <button type="button" className="um-clip-kf" title="Add keyframe"
                    style={{ left: half }}
                    onClick={() => { e.selectLayer(l.id); e.addKeyframe(l.id, ui.activePropKey); }}>◆</button>
                )}
                <button type="button" className="um-clip-back" title="Layer properties"
                  style={{ left: Math.max(6, left - 26) }}
                  onClick={() => { e.selectLayer(l.id); ui.setSheet('properties'); }}>
                  <ChevronLeft size={16} />
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ----------------------------------- FAB ---------------------------------- */
function Fab() {
  const ui = useUI();
  const docked = ui.sheet === 'property' || ui.sheet === 'properties';
  if (docked) return null;
  return (
    <button type="button" className="um-fab" title="Add layer" onClick={() => ui.openAdd()}>
      <Plus size={27} strokeWidth={2.1} />
    </button>
  );
}

/* --------------------------------- editor --------------------------------- */
export function Editor({ canvasRef, stageRef, rendererRef }: {
  canvasRef: RefObject<HTMLCanvasElement>; stageRef: RefObject<HTMLDivElement>;
  rendererRef: React.MutableRefObject<Renderer | null>;
}) {
  const ui = useUI();
  const docked = ui.sheet === 'property' || ui.sheet === 'properties';
  return (
    <div className={`um-screen um-editor ${docked ? 'docked' : ''}`}>
      <TopBar />
      <Stage canvasRef={canvasRef} stageRef={stageRef} rendererRef={rendererRef} />
      <Transport />
      <Timeline />
      {docked && <PropertyPane />}
      <Fab />
      <EditorSheets />
    </div>
  );
}
