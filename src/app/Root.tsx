import { useEffect, useRef, useState } from 'react';
import { editor, useEditor } from '../editor/store';
import { Renderer } from '../editor/renderer';
import { clamp } from '../editor/utils';
import { BRAND_CHANGED, applyBrand } from './brand';
import { useUI } from './context';
import { GestureBar, StatusBar } from './Chrome';
import { Splash } from './Splash';
import { AboutPage } from './AboutPage';
import { EffectBrowser } from './EffectBrowser';
import { PresetsDialog } from './PresetsDialog';
import { Flows, detectMissingFonts } from './Flows';
import { BrandSheet } from './BrandSheet';
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
  const [splash, setSplash] = useState(true);
  const [, forceBrand] = useState(0);

  /* Automation handle, mirroring window.__editor in main.tsx. */
  useEffect(() => {
    (window as unknown as { __ui?: typeof ui }).__ui = ui;
  }, [ui]);

  /* The in-app brand editor changes the running identity; re-render every
   * screen that reads BRAND and refresh the CSS custom properties. */
  useEffect(() => {
    const onChange = () => { applyBrand(); forceBrand(v => v + 1); };
    window.addEventListener(BRAND_CHANGED, onChange);
    return () => window.removeEventListener(BRAND_CHANGED, onChange);
  }, []);

  useEffect(() => { void editor.hydrateStoredAssets(); }, []);

  /* A newly imported project can reference fonts this device does not have.
     Report them honestly once per project instead of silently substituting. */
  const seenProject = useRef('');
  useEffect(() => {
    if (seenProject.current === e.project.id) return;
    seenProject.current = e.project.id;
    const missing = detectMissingFonts();
    if (missing.length) { ui.setMissingFonts(missing); ui.setFlow('missingFonts'); }
  }, [e.project.id, ui]);

  /* Every fresh pointer/focus interaction starts a new undo snapshot. */
  useEffect(() => {
    const start = () => editor.markInteractionStart();
    window.addEventListener('pointerdown', start, true);
    window.addEventListener('focusin', start, true);
    return () => {
      window.removeEventListener('pointerdown', start, true);
      window.removeEventListener('focusin', start, true);
    };
  }, []);

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
    editor.startEditClock();

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
      // Measured from the reference editor JPGs: 269x480 at x=80, y=113.
      const availW = Math.max(60, (stageW - 8) * 0.635);
      const availH = Math.max(60, stageH);
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
      renderer.activeCameraView = editor.activeCameraView;
      renderer.forceBlur = editor.forceBlur;

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

      const minGap = 1000 / (editor.playing ? previewFps() : 24);
      let due = dirty && now - lastRender >= minGap;
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
        due = now - lastRender >= minGap;
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
      editor.stopEditClock();
    };
  }, [inEditor, lowQuality]);

  /* Keyboard shortcuts — all bound to real editor operations. */
  useEffect(() => {
    const handler = (ev: KeyboardEvent) => {
      const el = ev.target as HTMLElement | null;
      const mod = ev.ctrlKey || ev.metaKey;
      // Text entry keeps native undo; sliders, number fields and canvases use
      // the editor shortcuts even while focused.
      const type = el instanceof HTMLInputElement ? el.type : '';
      const isText = el && (el.tagName === 'TEXTAREA' || el.isContentEditable ||
        (el.tagName === 'INPUT' && !['range', 'number', 'color', 'checkbox', 'radio'].includes(type)));
      if (isText && !mod) return;
      if (isText && mod && ev.key.toLowerCase() === 'z') return;
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
        {ui.prefs.showDeviceChrome && <StatusBar />}
        {inEditor
          ? <Editor canvasRef={canvasRef} stageRef={stageRef} rendererRef={rendererRef} />
          : <Home />}
        <Overlays />
        {ui.aboutOpen && <AboutPage />}
        {ui.effectBrowser && <EffectBrowser />}
        {ui.presetsDialog && <PresetsDialog />}
        {ui.sheet === 'brand' && <BrandSheet />}
        <Flows />
        {ui.prefs.showDeviceChrome && <GestureBar />}
        {splash && <Splash onDone={() => setSplash(false)} />}
        {e.toast && <div className={`um-toast ${e.toast.kind}`} key={e.toast.id}>{e.toast.text}</div>}
      </div>
    </div>
  );
}
