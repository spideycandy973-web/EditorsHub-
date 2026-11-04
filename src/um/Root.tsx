import React, { useEffect, useRef } from 'react';
import { editor, useEditor } from '../editor/store';
import { Renderer } from '../editor/renderer';
import { clamp } from '../editor/utils';
import { useUI } from './context';
import { Home } from './Home';
import { Editor } from './Editor';
import { Overlays } from './Overlays';

export function Root() {
  const e = useEditor();
  const ui = useUI();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<Renderer | null>(null);
  const inEditor = e.screen === 'editor';
  const lowQuality = ui.prefs.lowQualityPreview;

  useEffect(() => { void editor.hydrateStoredAssets(); }, []);

  /* The real compositor: fits the canvas, drives playback and renders frames. */
  useEffect(() => {
    if (!inEditor) return;
    const canvas = canvasRef.current;
    const stage = stageRef.current;
    if (!canvas || !stage) return;
    const renderer = new Renderer(canvas, editor.project, editor.media, 0.3);
    rendererRef.current = renderer;

    let dirty = true;
    let lastProject = editor.project;
    let lastZoom = editor.previewZoom;
    let lastSize = '';

    const fit = () => {
      const p = editor.project;
      const rect = stage.getBoundingClientRect();
      const ratio = p.width / p.height;
      const availW = Math.max(60, (rect.width - 54) * 0.72);
      const availH = Math.max(60, rect.height - 18);
      let cssW = Math.min(availW, availH * ratio);
      let cssH = cssW / ratio;
      if (cssH > availH) { cssH = availH; cssW = cssH * ratio; }
      const dpr = Math.min(window.devicePixelRatio || 1, 2) * (lowQuality ? 0.55 : 1);
      renderer.setScale(clamp(cssW * dpr / p.width, 0.03, 1.5));
      canvas.style.width = `${cssW}px`;
      canvas.style.height = `${cssH}px`;
      lastSize = `${rect.width}x${rect.height}x${p.width}x${p.height}`;
      lastZoom = editor.previewZoom;
    };
    fit();

    const ro = new ResizeObserver(() => { dirty = true; });
    ro.observe(stage);
    const unVersion = editor.subscribe(() => { dirty = true; });
    const unTime = editor.subscribeTime(() => { dirty = true; });

    let raf = 0;
    let last = performance.now();
    let lastRender = 0;
    const loop = (now: number) => {
      if (editor.screen === 'home') { raf = requestAnimationFrame(loop); return; }
      if (lastProject !== editor.project) {
        renderer.setProject(editor.project);
        lastProject = editor.project;
        fit(); dirty = true;
      }
      const rect = stage.getBoundingClientRect();
      const sizeKey = `${rect.width}x${rect.height}x${editor.project.width}x${editor.project.height}`;
      if (sizeKey !== lastSize || editor.previewZoom !== lastZoom) { fit(); dirty = true; }
      const dt = Math.min((now - last) / 1000, 0.1);
      last = now;
      if (editor.playing) {
        let t = editor.time + dt;
        if (t >= editor.project.duration) {
          if (editor.loop) t = 0;
          else { t = editor.project.duration; editor.setPlaying(false); }
        }
        editor.setTime(t, false);
      }
      const needRender = editor.playing || dirty || (now - lastRender > 220);
      if (needRender) {
        editor.media.sync(editor.project, editor.time, editor.playing);
        void renderer.render(editor.time, false);
        dirty = false;
        lastRender = now;
      }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);

    const onBeforeUnload = () => editor.saveNow();
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      unVersion(); unTime();
      window.removeEventListener('beforeunload', onBeforeUnload);
      rendererRef.current = null;
    };
  }, [inEditor, lowQuality]);

  /* Keyboard shortcuts — all bound to real editor operations. */
  useEffect(() => {
    const handler = (ev: KeyboardEvent) => {
      const el = ev.target as HTMLElement | null;
      if (el && (['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName) || el.isContentEditable)) return;
      const mod = ev.ctrlKey || ev.metaKey;
      if (mod && ev.key.toLowerCase() === 'z') { ev.preventDefault(); ev.shiftKey ? editor.redo() : editor.undo(); return; }
      if (mod && ev.key.toLowerCase() === 'y') { ev.preventDefault(); editor.redo(); return; }
      if (mod) return;
      switch (ev.key) {
        case 'Escape': ui.setSheet('none'); break;
        case ' ': ev.preventDefault(); editor.togglePlay(); break;
        case 'ArrowLeft': ev.preventDefault(); editor.stepFrame(-1); break;
        case 'ArrowRight': ev.preventDefault(); editor.stepFrame(1); break;
        case 'Delete': case 'Backspace':
          ev.preventDefault();
          if (editor.selectedLayer) editor.deleteLayer(editor.selectedLayer.id);
          break;
        case 'k': case 'K':
          if (editor.selectedLayer) { editor.addKeyframe(editor.selectedLayer.id, ui.activePropKey); editor.notify('Keyframe added', 'ok'); }
          break;
        case 's': case 'S':
          if (editor.selectedLayer) editor.splitLayer(editor.selectedLayer.id);
          break;
        case 'd': case 'D':
          if (editor.selectedLayer) editor.duplicateLayer(editor.selectedLayer.id);
          break;
        case 'e': case 'E': ui.setSheet('export'); break;
        case '?': ui.setSheet('help'); break;
        case '1': ui.openAdd(); break;
        case '2': if (editor.selectedLayer) ui.setSheet('properties'); break;
        case '3': if (editor.selectedLayer) ui.openProperty('effects'); break;
        case '4': if (editor.selectedLayer) ui.openProperty('presets'); break;
        case '5': ui.setSheet('projectSettings'); break;
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [ui]);

  return (
    <div className="um-root">
      <div className={`um-phone ${inEditor ? 'in-editor' : ''}`}>
        {inEditor
          ? <Editor canvasRef={canvasRef} stageRef={stageRef} rendererRef={rendererRef} />
          : <Home />}
        <Overlays />
        {e.toast && <div className={`um-toast ${e.toast.kind}`} key={e.toast.id}>{e.toast.text}</div>}
      </div>
    </div>
  );
}
