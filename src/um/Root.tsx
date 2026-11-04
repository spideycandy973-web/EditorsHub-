import { useEffect, useRef } from 'react';
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

  /* The real compositor. It is demand driven: no requestAnimationFrame loop
   * runs while the editor is idle, playback is capped at a sustainable preview
   * frame rate, and frames are never rendered twice at the same time. */
  useEffect(() => {
    if (!inEditor) return;
    const canvas = canvasRef.current;
    const stage = stageRef.current;
    if (!canvas || !stage) return;

    const renderer = new Renderer(canvas, editor.project, editor.media, 0.3);
    rendererRef.current = renderer;

    let disposed = false;
    let dirty = true;
    let sizeDirty = true;
    let lastProject = editor.project;
    let lastPlaying = editor.playing;
    let stageW = stage.clientWidth || 430;
    let stageH = stage.clientHeight || 520;
    let raf = 0;
    let lastTime = 0;
    let lastRender = 0;
    let mediaAt = 0;
    let rendering = false;

    const fit = () => {
      const p = editor.project;
      const ratio = p.width / p.height;
      const availW = Math.max(60, (stageW - 8) * 0.72);
      const availH = Math.max(60, stageH - 18);
      let cssW = Math.min(availW, availH * ratio);
      let cssH = cssW / ratio;
      if (cssH > availH) { cssH = availH; cssW = cssH * ratio; }
      const dpr = Math.min(window.devicePixelRatio || 1, 2) * (lowQuality ? 0.55 : 1);
      // Play back at a reduced backing-store resolution to keep phones smooth.
      const quality = editor.playing ? 0.55 : 1;
      renderer.setScale(clamp(cssW * dpr * quality / p.width, 0.03, 1.5));
      canvas.style.width = `${cssW}px`;
      canvas.style.height = `${cssH}px`;
      sizeDirty = false;
      dirty = true;
    };

    const previewFps = () => Math.min(Math.max(editor.project.fps || 30, 1), lowQuality ? 20 : 30);

    function kick() {
      if (!raf && !disposed && !document.hidden) raf = requestAnimationFrame(loop);
    }

    function loop(now: number) {
      raf = 0;
      if (disposed || document.hidden) return;
      if (editor.screen === 'home') return;

      if (lastProject !== editor.project) {
        renderer.setProject(editor.project);
        lastProject = editor.project;
        sizeDirty = true;
      }
      if (editor.playing !== lastPlaying) {
        lastPlaying = editor.playing;
        sizeDirty = true; // re-fit at the playback backing-store resolution
      }
      if (sizeDirty) fit();

      let due = dirty;
      if (editor.playing) {
        const dt = lastTime ? Math.min((now - lastTime) / 1000, 0.1) : 0;
        lastTime = now;
        let t = editor.time + dt;
        if (t >= editor.project.duration) {
          if (editor.loop) t = 0;
          else { t = editor.project.duration; editor.setPlaying(false); }
        }
        editor.setTime(t, false); // time-only notification; no React re-render
        dirty = true;
        due = now - lastRender >= 1000 / previewFps();
      } else {
        lastTime = 0;
      }

      if (due && !rendering) {
        if (now - mediaAt > 90) {
          editor.media.sync(editor.project, editor.time, editor.playing);
          mediaAt = now;
        }
        rendering = true;
        void renderer.render(editor.time, false).catch(() => undefined).finally(() => {
          rendering = false;
          if (dirty || editor.playing) kick();
        });
        dirty = false;
        lastRender = now;
      }
      if (editor.playing || dirty || rendering) kick();
    }

    const ro = new ResizeObserver(entries => {
      const r = entries[0]?.contentRect;
      if (!r) return;
      stageW = r.width;
      stageH = r.height;
      sizeDirty = true;
      kick();
    });
    ro.observe(stage);

    const unVersion = editor.subscribe(() => { dirty = true; kick(); });
    const unTime = editor.subscribeTime(() => { dirty = true; kick(); });
    const onVisibility = () => {
      if (!document.hidden) { lastTime = 0; dirty = true; kick(); }
    };
    document.addEventListener('visibilitychange', onVisibility);

    const onBeforeUnload = () => editor.saveNow();
    window.addEventListener('beforeunload', onBeforeUnload);

    fit();
    kick();

    return () => {
      disposed = true;
      if (raf) cancelAnimationFrame(raf);
      ro.disconnect();
      unVersion();
      unTime();
      document.removeEventListener('visibilitychange', onVisibility);
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
