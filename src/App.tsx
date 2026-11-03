import React, { useEffect, useRef } from 'react';
import {
  ChevronDown, Diamond, Folder, Move, PlusCircle, SlidersHorizontal, Sparkles,
} from 'lucide-react';
import { editor, useEditor } from './editor/store';
import { Renderer } from './editor/renderer';
import { clamp } from './editor/utils';
import { TopBar } from './components/TopBar';
import { Preview } from './components/Preview';
import { PlayerBar } from './components/PlayerBar';
import { Timeline, BottomNav } from './components/Timeline';
import { HomeScreen } from './components/HomeScreen';
import { AddPanel } from './components/panels/AddPanel';
import { EditPanel } from './components/panels/EditPanel';
import { EffectsPanel } from './components/panels/EffectsPanel';
import { KeyframesPanel } from './components/panels/KeyframesPanel';
import { ProjectPanel } from './components/panels/ProjectPanel';
import {
  ExportModal, XmlImportModal, ProjectsModal, NewProjectModal, LayerMenuModal, HelpModal,
  SettingsModal,
} from './components/Modals';
import { IconBtn } from './components/ui';

const PANEL_META = {
  add: { title: 'Add layer', icon: <PlusCircle size={15} /> },
  edit: { title: 'Move & Transform', icon: <Move size={15} /> },
  effects: { title: 'Effects', icon: <Sparkles size={15} /> },
  keyframes: { title: 'Keyframes', icon: <Diamond size={15} /> },
  project: { title: 'Project', icon: <Folder size={15} /> },
};

const RAIL = [
  { id: 'add', icon: <PlusCircle size={17} />, label: 'Add' },
  { id: 'edit', icon: <SlidersHorizontal size={17} />, label: 'Move' },
  { id: 'effects', icon: <Sparkles size={17} />, label: 'Effects' },
  { id: 'keyframes', icon: <Diamond size={17} />, label: 'Keys' },
  { id: 'project', icon: <Folder size={17} />, label: 'Project' },
] as const;

export default function App() {
  const e = useEditor();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<Renderer | null>(null);

  /* Restore media stored in IndexedDB from previous sessions. */
  useEffect(() => { void editor.hydrateStoredAssets(); }, []);

  /* The rendering engine: fits the canvas, drives playback and redraws on changes. */
  useEffect(() => {
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
      const availW = Math.max(60, rect.width - 28);
      const availH = Math.max(60, rect.height - 28);
      let cssW = Math.min(availW, availH * ratio);
      let cssH = cssW / ratio;
      if (cssH > availH) { cssH = availH; cssW = cssH * ratio; }
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
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
  }, []);

  /* Keyboard shortcuts */
  useEffect(() => {
    const handler = (ev: KeyboardEvent) => {
      const el = ev.target as HTMLElement | null;
      if (el && (['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName) || el.isContentEditable)) return;
      const mod = ev.ctrlKey || ev.metaKey;
      if (mod && ev.key.toLowerCase() === 'z') { ev.preventDefault(); ev.shiftKey ? editor.redo() : editor.undo(); return; }
      if (mod && ev.key.toLowerCase() === 'y') { ev.preventDefault(); editor.redo(); return; }
      if (mod) return;
      switch (ev.key) {
        case ' ': ev.preventDefault(); editor.togglePlay(); break;
        case 'ArrowLeft': ev.preventDefault(); editor.stepFrame(-1); break;
        case 'ArrowRight': ev.preventDefault(); editor.stepFrame(1); break;
        case 'Delete': case 'Backspace':
          ev.preventDefault();
          if (editor.selectedLayer) editor.deleteLayer(editor.selectedLayer.id);
          break;
        case 'k': case 'K':
          if (editor.selectedLayer) { editor.addKeyframe(editor.selectedLayer.id, editor.selectedPropertyKey); editor.notify('Keyframe added', 'ok'); }
          break;
        case 's': case 'S':
          if (editor.selectedLayer) editor.splitLayer(editor.selectedLayer.id);
          break;
        case 'd': case 'D':
          if (editor.selectedLayer) editor.duplicateLayer(editor.selectedLayer.id);
          break;
        case 'e': case 'E': editor.setModal('export'); break;
        case '?': editor.setModal('help'); break;
        case '1': editor.setPanel('add'); break;
        case '2': editor.setPanel('edit'); break;
        case '3': editor.setPanel('effects'); break;
        case '4': editor.setPanel('keyframes'); break;
        case '5': editor.setPanel('project'); break;
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  const meta = PANEL_META[e.panel];

  return (
    <>
      <div className={`app ${e.sheetOpen ? '' : 'sheet-closed'} ${e.screen === 'home' ? 'app-hidden' : ''} ${e.sheetOpen && e.panel === 'add' ? 'am-add-open' : ''}`}>
        <TopBar />
        <Preview canvasRef={canvasRef} stageRef={stageRef} rendererRef={rendererRef} />
        <PlayerBar />
        <Timeline />

        <div className="dock">
          <BottomNav />
          {e.sheetOpen && (
            <div className="sheet">
              <div className="sheet-head">
                <div className="sheet-title">{meta.icon}<span>{meta.title}</span></div>
                <IconBtn title="Collapse panel" onClick={() => e.toggleSheet()}><ChevronDown size={17} /></IconBtn>
              </div>
              <div className="am-sheet-body">
                <div className="am-rail">
                  {RAIL.map(r => (
                    <button
                      key={r.id} type="button" title={r.label}
                      className={e.panel === r.id ? 'on' : ''}
                      onClick={() => e.setPanel(r.id)}
                    >{r.icon}</button>
                  ))}
                </div>
                <div className="sheet-content">
                  {e.panel === 'add' && <AddPanel />}
                  {e.panel === 'edit' && <EditPanel />}
                  {e.panel === 'effects' && <EffectsPanel />}
                  {e.panel === 'keyframes' && <KeyframesPanel />}
                  {e.panel === 'project' && <ProjectPanel />}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {e.screen === 'home' && <HomeScreen />}

      {e.modal === 'export' && <ExportModal />}
      {e.modal === 'xml' && <XmlImportModal />}
      {e.modal === 'projects' && <ProjectsModal />}
      {e.modal === 'newProject' && <NewProjectModal />}
      {e.modal === 'layerMenu' && <LayerMenuModal />}
      {e.modal === 'help' && <HelpModal />}
      {e.modal === 'settings' && <SettingsModal />}

      {e.toast && <div className={`toast ${e.toast.kind}`}>{e.toast.text}</div>}
    </>
  );
}
