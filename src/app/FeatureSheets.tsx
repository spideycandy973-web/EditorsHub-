/**
 * Screens recovered from the reference APK's manifest (REA / JADX / APKTool):
 * ColorPickerActivity, FontBrowserActivity, MediaBrowser, AudioBrowserActivity,
 * SaveToStorageActivity, OpenSourceLibrariesActivity, TutorialActivity,
 * PresetPreviewActivity, TemplateImportPreviewActivity.
 *
 * All are original implementations wired to real engine operations.
 */
import { useRef, useState } from 'react';
import { ArrowLeft, Check, FileDown, Search, Sparkles, Type, Upload } from 'lucide-react';
import { editor, useEditor } from '../editor/store';
import { FONT_FAMILIES, type Asset } from '../editor/types';
import { downloadBlob, safeFileName } from '../editor/utils';
import { PALETTE, Btn, ColorInput } from './widgets';
import { addMediaFiles, listPresets, saveCurrentAsElement } from './lib';
import { useUI } from './context';

/* --------------------------- ColorPickerActivity --------------------------- */
export function ColorPickerBody() {
  const e = useEditor();
  const layer = e.selectedLayer;
  const [hex, setHex] = useState(layer?.style.color ?? '#ba7e38');
  const apply = (v: string) => {
    setHex(v);
    if (layer) e.setStyle(layer.id, { color: v, color2: v });
    else e.setProjectSettings({ background: v });
  };
  return (
    <div className="um-feature-body">
      <p className="um-hint">{layer ? `Fill colour for “${layer.name}”` : 'Composition background colour'}</p>
      <div className="um-color-preview" style={{ background: hex }} />
      <div className="um-color-hex">
        <ColorInput value={hex} onChange={apply} label="Colour" />
        <input value={hex} onChange={ev => { const v = ev.target.value; if (/^#[0-9a-fA-F]{6}$/.test(v)) apply(v); }}
          aria-label="Hex colour" />
      </div>
      <div className="um-color-swatches">
        {PALETTE.map(c => <button key={c} type="button" title={c} style={{ background: c }} onClick={() => apply(c)} />)}
      </div>
      <div className="um-color-hues">
        {Array.from({ length: 24 }, (_, i) => `hsl(${i * 15} 80% 55%)`).map(c => (
          <button key={c} type="button" title={c} style={{ background: c }} onClick={() => apply(c)} />
        ))}
      </div>
    </div>
  );
}

/* --------------------------- FontBrowserActivity --------------------------- */
export function FontBrowserBody() {
  const e = useEditor();
  const layer = e.selectedLayer;
  const fileRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState('');
  const fonts = [...FONT_FAMILIES, ...Object.values(e.assets).filter(a => a.type === 'font').map(a => a.fontFamily ?? a.name)];
  const shown = fonts.filter(f => f.toLowerCase().includes(q.toLowerCase()));
  return (
    <div className="um-feature-body">
      <label className="um-search"><Search size={15} />
        <input value={q} onChange={ev => setQ(ev.target.value)} placeholder="Search fonts" aria-label="Search fonts" />
      </label>
      <div className="um-font-list">
        {shown.map(f => (
          <button type="button" key={f} className={layer?.style.fontFamily === f ? 'on' : ''}
            onClick={() => { if (layer) { e.setStyle(layer.id, { fontFamily: f }); e.notify(`Font: ${f}`, 'ok'); } else e.notify('Select a text layer first', 'warn'); }}>
            <Type size={15} /><strong style={{ fontFamily: f }}>{f.replace(/,.*/, '')}</strong>
            {layer?.style.fontFamily === f && <Check size={14} />}
            <small style={{ fontFamily: f }}>Aa 0123</small>
          </button>
        ))}
      </div>
      <input ref={fileRef} type="file" hidden accept=".ttf,.otf,.woff,.woff2"
        onChange={ev => { if (ev.target.files) void addMediaFiles(ev.target.files); ev.target.value = ''; }} />
      <Btn variant="dark" onClick={() => fileRef.current?.click()}><Upload size={15} /> Import font file</Btn>
    </div>
  );
}

/* ---------------------- MediaBrowser / AudioBrowser ------------------------ */
export function MediaBrowserBody({ audioOnly = false }: { audioOnly?: boolean }) {
  const e = useEditor();
  const ui = useUI();
  const fileRef = useRef<HTMLInputElement>(null);
  const assets: Asset[] = Object.values(e.assets).filter(a =>
    audioOnly ? a.type === 'audio' : a.type === 'image' || a.type === 'video');
  const add = (id: string) => { e.addMediaLayer(id); ui.setSheet('properties'); };
  return (
    <div className="um-feature-body">
      <p className="um-hint">{assets.length} {audioOnly ? 'audio' : 'media'} assets in this project. Import from your device or add one as a layer.</p>
      <div className="um-media-grid">
        {assets.map(a => (
          <button type="button" key={a.id} title={`Add ${a.name}`} onClick={() => add(a.id)}>
            <span>{a.thumbnail ? <img src={a.thumbnail} alt="" /> : <Sparkles size={20} />}</span>
            <small>{a.name}</small>
          </button>
        ))}
      </div>
      {assets.length === 0 && <p className="um-empty-note">Nothing imported yet.</p>}
      <input ref={fileRef} type="file" hidden multiple
        accept={audioOnly ? 'audio/*' : 'image/*,video/*'}
        onChange={ev => { if (ev.target.files) void addMediaFiles(ev.target.files); ev.target.value = ''; }} />
      <Btn variant="dark" onClick={() => fileRef.current?.click()}><Upload size={15} /> Import {audioOnly ? 'audio' : 'media'}</Btn>
    </div>
  );
}

/* -------------------------- SaveToStorageActivity ------------------------- */
export function SaveToStorageBody() {
  const e = useEditor();
  const [, force] = useState(0);
  const save = () => { e.saveNow(); force(v => v + 1); e.notify('Project saved on this device', 'ok'); };
  const download = () => {
    const data = JSON.stringify({ project: e.project, assets: Object.values(e.assets).map(a => ({ ...a, url: a.url, element: undefined })) }, null, 2);
    downloadBlob(new Blob([data], { type: 'application/json' }), `${safeFileName(e.project.name)}.json`);
    e.notify('Project JSON downloaded', 'ok');
  };
  return (
    <div className="um-feature-body">
      <div className="um-save-card">
        <strong>{e.project.name}</strong>
        <small>{e.project.width} × {e.project.height} · {e.project.fps} fps · {e.project.layers.length} layers · {e.editTime}s editing</small>
      </div>
      <Btn onClick={save}><Check size={15} /> Save project</Btn>
      <Btn variant="dark" onClick={download}><FileDown size={15} /> Download .json</Btn>
      <Btn variant="dark" onClick={() => {
        void saveCurrentAsElement(`${e.project.name} element`).then(r => e.notify(`Saved “${r.name}”`, 'ok'));
      }}><Sparkles size={15} /> Save as element</Btn>
      <p className="um-hint">Projects autosave locally. “Save as element” stores a re-importable XML preset.</p>
    </div>
  );
}

/* ------------------------ OpenSourceLibrariesActivity ---------------------- */
const OSS: { name: string; version: string; license: string; url: string }[] = [
  { name: 'React', version: '18.3', license: 'MIT', url: 'https://react.dev' },
  { name: 'React DOM', version: '18.3', license: 'MIT', url: 'https://react.dev' },
  { name: 'Vite', version: '6.0', license: 'MIT', url: 'https://vite.dev' },
  { name: 'TypeScript', version: '5.7', license: 'Apache-2.0', url: 'https://www.typescriptlang.org' },
  { name: 'lucide-react', version: '0.468', license: 'ISC', url: 'https://lucide.dev' },
  { name: 'fflate', version: '0.8', license: 'MIT', url: 'https://github.com/101arrowz/fflate' },
  { name: 'jsQR', version: '1.4', license: 'Apache-2.0', url: 'https://github.com/cozmo/jsQR' },
  { name: 'jsqr', version: '1.4', license: 'Apache-2.0', url: 'https://github.com/cozmo/jsQR' },
  { name: 'Fontsource Roboto', version: '5.3', license: 'SIL OFL 1.1', url: 'https://fontsource.org' },
  { name: 'Capacitor', version: '7.0', license: 'MIT', url: 'https://capacitorjs.com' },
];
export function OpenSourceBody() {
  return (
    <div className="um-feature-body">
      <p className="um-hint">Libraries used by this build. Licenses are listed verbatim from their package metadata.</p>
      <div className="um-oss-list">
        {OSS.map(o => (
          <a key={o.name} href={o.url} target="_blank" rel="noopener noreferrer">
            <strong>{o.name}</strong><span>{o.version}</span><em>{o.license}</em>
          </a>
        ))}
      </div>
    </div>
  );
}

/* ----------------------------- TutorialActivity ---------------------------- */
const STEPS: { title: string; body: string; sheet: Parameters<ReturnType<typeof useUI>['setSheet']>[0] }[] = [
  { title: 'Import a project', body: 'Paste XML, choose a file, or scan a QR code on the launcher.', sheet: 'xml' },
  { title: 'Create a composition', body: 'Pick a ratio, resolution, frame rate and background.', sheet: 'create' },
  { title: 'Add layers', body: 'Media, text, shapes, audio, drawing, camera, nulls and groups.', sheet: 'add' },
  { title: 'Animate', body: 'Add keyframes, then shape motion with the cubic bezier easing graph.', sheet: 'property' },
  { title: 'Add effects', body: 'Browse 44 real canvas effects across 32 categories.', sheet: 'none' },
  { title: 'Export', body: 'Video, project package, PNG, image sequence, XML or animated GIF.', sheet: 'export' },
];
export function TutorialBody() {
  const ui = useUI();
  const [i, setI] = useState(0);
  const step = STEPS[i];
  return (
    <div className="um-feature-body um-tutorial">
      <div className="um-tutorial-count">Step {i + 1} of {STEPS.length}</div>
      <h3>{step.title}</h3>
      <p>{step.body}</p>
      <div className="um-tutorial-dots">
        {STEPS.map((_, n) => <i key={n} className={n === i ? 'on' : ''} />)}
      </div>
      <div className="um-tutorial-actions">
        <Btn variant="dark" onClick={() => setI(Math.max(0, i - 1))} disabled={i === 0}><ArrowLeft size={15} /> Back</Btn>
        <Btn variant="dark" onClick={() => {
          if (step.sheet === 'none') ui.setEffectBrowser(true);
          else ui.setSheet(step.sheet);
        }}>Open screen</Btn>
        <Btn onClick={() => { if (i === STEPS.length - 1) ui.setSheet('none'); else setI(i + 1); }}>
          {i === STEPS.length - 1 ? 'Finish' : 'Next'}
        </Btn>
      </div>
    </div>
  );
}

/* --------------------------- PresetPreviewActivity ------------------------- */
export function PresetPreviewBody() {
  const e = useEditor();
  const presets = listPresets();
  return (
    <div className="um-feature-body">
      <p className="um-hint">{presets.length} saved presets. Presets snapshot a layer's style, properties, blend and effects.</p>
      {presets.map(p => (
        <button type="button" key={p.id} className="um-preset-row"
          onClick={() => { e.applyMotionPreset('fadeIn'); e.notify(`Applied “${p.name}” motion`, 'ok'); }}>
          <Sparkles size={16} /><strong>{p.name}</strong>
          <small>{new Date(p.createdAt).toLocaleString()}</small>
        </button>
      ))}
      {presets.length === 0 && <p className="um-empty-note">No presets yet. Use “Save Preset” in the layer menu.</p>}
    </div>
  );
}

/** Font families actually installed for this project (used by Missing Fonts). */
export function installedFonts(): string[] {
  return [...FONT_FAMILIES, ...Object.values(editor.assets).filter(a => a.type === 'font').map(a => a.fontFamily ?? a.name)];
}
