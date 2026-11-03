import React, { useRef, useState } from 'react';
import {
  Circle, Diamond, Eye, EyeOff, Film, Folder, Image as ImageIcon, Lock, Minus, Music2,
  MoreVertical, Plus, Scissors, Square, Trash2, Type, Unlock, Copy, ClipboardPaste,
  ChevronUp, ChevronDown, PlusCircle, Shapes, Move, WandSparkles,
} from 'lucide-react';
import { useEditor, usePlayhead } from '../editor/store';
import { PROP_MAP, type Layer } from '../editor/types';
import { clamp, formatShortTime, round } from '../editor/utils';
import { IconBtn } from './ui';

const LABEL_W = 128;

const LAYER_ICONS: Record<Layer['type'], React.ReactNode> = {
  solid: <Square size={13} />, shape: <Circle size={13} />, text: <Type size={13} />,
  image: <ImageIcon size={13} />, video: <Film size={13} />, audio: <Music2 size={13} />,
  group: <Folder size={13} />,
};

type DragMode = 'move' | 'trimLeft' | 'trimRight';

interface ClipDrag {
  id: string;
  mode: DragMode;
  x: number;
  start: number;
  duration: number;
  inPoint: number;
  speed: number;
}

export function Timeline() {
  const e = useEditor();
  const time = usePlayhead();
  const [expanded, setExpanded] = useState<string | null>(null);
  const [drag, setDrag] = useState<ClipDrag | null>(null);
  const scrubbing = useRef(false);
  const kfDrag = useRef<{ layerId: string; key: string; kfId: string; x: number; time: number } | null>(null);

  const zoom = e.timelineZoom;
  const W = e.project.duration * zoom + 60;
  const contentW = LABEL_W + W;
  const tickStep = zoom >= 110 ? 0.25 : zoom >= 70 ? 0.5 : zoom >= 34 ? 1 : zoom >= 18 ? 2 : 5;
  const ticks: number[] = [];
  for (let t = 0; t <= e.project.duration + tickStep; t += tickStep) ticks.push(Number(t.toFixed(3)));

  const scrubFromEvent = (ev: React.PointerEvent, el: HTMLElement) => {
    const rect = el.getBoundingClientRect();
    e.setTime((ev.clientX - rect.left) / zoom);
  };

  const startClipDrag = (ev: React.PointerEvent, l: Layer, mode: DragMode) => {
    ev.stopPropagation();
    if (l.locked) { e.notify('Layer is locked', 'warn'); return; }
    (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
    e.selectLayer(l.id);
    e.beginBatch();
    setDrag({
      id: l.id, mode, x: ev.clientX, start: l.start, duration: l.duration,
      inPoint: l.inPoint, speed: e.getLayerProp(l, 'speed'),
    });
  };

  const moveClipDrag = (ev: React.PointerEvent) => {
    if (!drag) return;
    ev.stopPropagation();
    let dt = (ev.clientX - drag.x) / zoom;
    if (e.snapToFrame) dt = Math.round(dt * e.project.fps) / e.project.fps;
    const l = e.layer(drag.id);
    if (!l) return;
    if (drag.mode === 'move') {
      e.setTiming(l.id, { start: clamp(drag.start + dt, 0, Math.max(0, e.project.duration - 0.1)) }, false);
    } else if (drag.mode === 'trimLeft') {
      const max = drag.duration - 0.1;
      const d = clamp(dt, -drag.start, max);
      e.setTiming(l.id, {
        start: drag.start + d,
        duration: drag.duration - d,
        inPoint: drag.inPoint + d * drag.speed,
      }, false);
    } else {
      e.setTiming(l.id, { duration: clamp(drag.duration + dt, 0.1, e.project.duration) }, false);
    }
  };

  const endClipDrag = () => {
    if (!drag) return;
    setDrag(null);
    e.endBatch();
  };

  const startKfDrag = (ev: React.PointerEvent, l: Layer, key: string, kfId: string, kfTime: number) => {
    ev.stopPropagation();
    (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
    e.selectLayer(l.id);
    e.selectProperty(key);
    e.setTime(l.start + kfTime);
    e.beginBatch();
    kfDrag.current = { layerId: l.id, key, kfId, x: ev.clientX, time: kfTime };
  };

  const moveKfDrag = (ev: React.PointerEvent) => {
    const d = kfDrag.current;
    if (!d) return;
    ev.stopPropagation();
    let t = d.time + (ev.clientX - d.x) / zoom;
    if (e.snapToFrame) t = Math.round(t * e.project.fps) / e.project.fps;
    const l = e.layer(d.layerId);
    e.updateKeyframe(d.layerId, d.key, d.kfId, { time: clamp(t, 0, l?.duration ?? 10) });
  };

  const endKfDrag = () => {
    if (!kfDrag.current) return;
    kfDrag.current = null;
    e.endBatch();
  };

  const propsWithKeys = (l: Layer) => Object.entries(l.props).filter(([, a]) => a.keyframes.length > 0)
    .sort((a, b) => (PROP_MAP[a[0]]?.label ?? a[0]).localeCompare(PROP_MAP[b[0]]?.label ?? b[0]));

  return (
    <div className="timeline">
      <div className="tl-toolbar">
        <span className="tl-heading">TIMELINE</span>
        <div className="tl-zoom">
          <IconBtn title="Zoom out" onClick={() => e.setTimelineZoom(zoom - 12)}><Minus size={14} /></IconBtn>
          <input type="range" min={12} max={220} value={zoom} onChange={ev => e.setTimelineZoom(Number(ev.target.value))} />
          <IconBtn title="Zoom in" onClick={() => e.setTimelineZoom(zoom + 12)}><Plus size={14} /></IconBtn>
        </div>
        <div className="tl-actions">
          <IconBtn title="Add layer" onClick={() => e.setPanel('add')}><PlusCircle size={16} /></IconBtn>
          <IconBtn title="Split at playhead (S)" onClick={() => e.selectedLayer && e.splitLayer(e.selectedLayer.id)}><Scissors size={15} /></IconBtn>
          <IconBtn title="Duplicate (D)" onClick={() => e.selectedLayer && e.duplicateLayer(e.selectedLayer.id)}><Copy size={15} /></IconBtn>
          <IconBtn title="Paste layer" onClick={() => e.pasteLayer()}><ClipboardPaste size={15} /></IconBtn>
          <IconBtn title="Move layer up" onClick={() => e.selectedLayer && e.moveLayer(e.selectedLayer.id, 1)}><ChevronUp size={16} /></IconBtn>
          <IconBtn title="Move layer down" onClick={() => e.selectedLayer && e.moveLayer(e.selectedLayer.id, -1)}><ChevronDown size={16} /></IconBtn>
          <IconBtn title="Layer options" onClick={() => e.setModal('layerMenu')}><MoreVertical size={16} /></IconBtn>
          <IconBtn title="Delete layer" danger onClick={() => e.selectedLayer && e.deleteLayer(e.selectedLayer.id)}><Trash2 size={15} /></IconBtn>
        </div>
      </div>

      <div className="tl-scroll">
        <div className="tl-content" style={{ width: contentW }}>
          <div className="tl-ruler">
            <div className="tl-ruler-label" style={{ width: LABEL_W }}>
              <Diamond size={12} /> {e.project.layers.length} layers
            </div>
            <div
              className="tl-ruler-track" style={{ width: W }}
              onPointerDown={ev => {
                scrubbing.current = true;
                (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
                e.setPlaying(false);
                scrubFromEvent(ev, ev.currentTarget);
              }}
              onPointerMove={ev => { if (scrubbing.current) scrubFromEvent(ev, ev.currentTarget); }}
              onPointerUp={() => { scrubbing.current = false; }}
              onPointerCancel={() => { scrubbing.current = false; }}
            >
              {ticks.map(t => (
                <div key={t} className={`ruler-tick ${Number.isInteger(t) ? 'major' : ''}`} style={{ left: t * zoom }}>
                  <span>{formatShortTime(t)}</span>
                </div>
              ))}
            </div>
          </div>

          {e.project.layers.length === 0 && (
            <div className="tl-no-layers">No layers yet — tap <PlusCircle size={13} /> to add text, shapes or media.</div>
          )}

          {e.project.layers.map((l, idx) => {
            const isSel = e.selectedLayerId === l.id;
            const isOpen = expanded === l.id || (expanded === null && isSel);
            const keyProps = propsWithKeys(l);
            return (
              <React.Fragment key={l.id}>
                <div className={`tl-row ${isSel ? 'selected' : ''}`}>
                  <div className="tl-label" style={{ width: LABEL_W }} onClick={() => e.selectLayer(l.id)}>
                    <span className="layer-index">{idx + 1}</span>
                    <span className={`layer-icon t-${l.type}`}>{LAYER_ICONS[l.type]}</span>
                    <span className="layer-name" title={l.name}>{l.name}</span>
                    <div className="layer-toggles">
                      <button type="button" title={l.visible ? 'Hide' : 'Show'} onClick={ev => { ev.stopPropagation(); e.toggleVisible(l.id); }}>
                        {l.visible ? <Eye size={13} /> : <EyeOff size={13} />}
                      </button>
                      <button type="button" title={l.locked ? 'Unlock' : 'Lock'} onClick={ev => { ev.stopPropagation(); e.toggleLock(l.id); }}>
                        {l.locked ? <Lock size={12} /> : <Unlock size={12} />}
                      </button>
                      <button type="button" title="Expand keyframes" onClick={ev => { ev.stopPropagation(); setExpanded(isOpen ? '__none__' : l.id); }}>
                        <Diamond size={11} fill={keyProps.length ? 'currentColor' : 'none'} />
                      </button>
                    </div>
                  </div>
                  <div
                    className="tl-track" style={{ width: W }}
                    onPointerDown={ev => { e.setPlaying(false); scrubFromEvent(ev, ev.currentTarget); }}
                  >
                    <div
                      className={`tl-clip t-${l.type} ${isSel ? 'selected' : ''} ${!l.visible ? 'hidden-layer' : ''}`}
                      style={{ left: l.start * zoom, width: Math.max(6, l.duration * zoom) }}
                      onPointerDown={ev => startClipDrag(ev, l, 'move')}
                      onPointerMove={moveClipDrag}
                      onPointerUp={endClipDrag}
                      onPointerCancel={endClipDrag}
                    >
                      <span className="clip-name">{l.name}</span>
                      <span className="trim-handle left" onPointerDown={ev => startClipDrag(ev, l, 'trimLeft')} onPointerMove={moveClipDrag} onPointerUp={endClipDrag} onPointerCancel={endClipDrag} />
                      <span className="trim-handle right" onPointerDown={ev => startClipDrag(ev, l, 'trimRight')} onPointerMove={moveClipDrag} onPointerUp={endClipDrag} onPointerCancel={endClipDrag} />
                      {isSel && Object.entries(l.props).flatMap(([key, a]) => a.keyframes.map(k => (
                        <span key={k.id} className="clip-kf" style={{ left: k.time * zoom }} title={`${PROP_MAP[key]?.label ?? key} @ ${k.time}s`} />
                      )))}
                    </div>
                  </div>
                </div>

                {isOpen && keyProps.length > 0 && (
                  <div className="tl-prop-lanes">
                    {keyProps.map(([key, a]) => (
                      <div className="tl-prop-row" key={key}>
                        <div className="tl-prop-label" style={{ width: LABEL_W }} onClick={() => e.selectProperty(key)}>
                          <span className="prop-dot" /> {PROP_MAP[key]?.label ?? key}
                        </div>
                        <div className="tl-track prop" style={{ width: W }}>
                          <div className="prop-line" style={{ left: l.start * zoom, width: l.duration * zoom }} />
                          {a.keyframes.map(k => (
                            <button
                              key={k.id} type="button" className={`kf-marker ${k.easing === 'hold' ? 'hold' : ''}`}
                              style={{ left: (l.start + k.time) * zoom }}
                              title={`${PROP_MAP[key]?.label ?? key}: ${round(k.value, 2)} at ${round(l.start + k.time, 2)}s (${k.easing})`}
                              onPointerDown={ev => startKfDrag(ev, l, key, k.id, k.time)}
                              onPointerMove={moveKfDrag}
                              onPointerUp={endKfDrag}
                              onPointerCancel={endKfDrag}
                              onClick={() => { e.selectProperty(key); e.setTime(l.start + k.time); }}
                            />
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </React.Fragment>
            );
          })}

          <div className="tl-playhead" style={{ left: LABEL_W + time * zoom }}>
            <div className="playhead-cap" />
          </div>
        </div>
      </div>
      <button type="button" className="am-fab" title="Add layer (1)" onClick={() => e.setPanel('add')}>
        <Plus size={26} />
      </button>
    </div>
  );
}

export function BottomNav() {
  const e = useEditor();
  const tabs = [
    { id: 'add' as const, label: 'Add', icon: <Shapes size={19} /> },
    { id: 'edit' as const, label: 'Move', icon: <Move size={19} /> },
    { id: 'effects' as const, label: 'Effects', icon: <WandSparkles size={19} /> },
    { id: 'keyframes' as const, label: 'Keys', icon: <Diamond size={19} /> },
    { id: 'project' as const, label: 'Project', icon: <Folder size={19} /> },
  ];
  return (
    <nav className="bottomnav">
      {tabs.map(t => (
        <button key={t.id} type="button" className={e.panel === t.id && e.sheetOpen ? 'on' : ''} onClick={() => {
          if (e.panel === t.id && e.sheetOpen) e.toggleSheet();
          else e.setPanel(t.id);
        }}>
          {t.icon}<span>{t.label}</span>
        </button>
      ))}
    </nav>
  );
}
