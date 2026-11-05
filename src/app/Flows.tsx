import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Check, Play, Upload, X } from 'lucide-react';
import { editor, useEditor } from '../editor/store';
import { importMany } from '../editor/media';
import { renderStill } from '../editor/export';
import { useUI } from './context';

/**
 * Full-screen flows from the reference recording, rebuilt with original code:
 * template editor / replace media, the "Are you done replacing media?" gate,
 * the missing-fonts report and a real export preview.
 */

/** Fonts that are actually guaranteed by the CSS font stack. */
const SYSTEM_FONTS = ['inter', 'arial', 'helvetica', 'georgia', 'times new roman',
  'courier new', 'impact', 'verdana', 'trebuchet ms', 'monospace', 'serif', 'sans-serif'];

export function detectMissingFonts(): string[] {
  const e = editor;
  const found = new Set<string>();
  for (const l of e.project.layers) {
    if (l.type !== 'text') continue;
    const raw = (l.style.fontFamily || '').split(',')[0].trim().replace(/["']/g, '').toLowerCase();
    if (!raw || SYSTEM_FONTS.includes(raw)) continue;
    let available = false;
    try { available = document.fonts.check(`16px "${raw}"`); } catch { /* unsupported */ }
    // A font uploaded into this project is installed under its own family name.
    const uploaded = Object.values(e.assets).some(a => a.type === 'font' &&
      (a.fontFamily || '').toLowerCase() === raw);
    if (!available && !uploaded) found.add(l.style.fontFamily.split(',')[0].trim().replace(/["']/g, ''));
  }
  return [...found];
}

function ReplaceMediaFlow() {
  const e = useEditor();
  const ui = useUI();
  const [preview, setPreview] = useState('');
  const [confirm, setConfirm] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const targetRef = useRef<string>('');
  const [, force] = useState(0);
  const slots = e.project.layers.filter(l => l.type === 'image' || l.type === 'video' || l.type === 'audio');

  useEffect(() => {
    let alive = true; let url = '';
    void renderStill(e.project, e.assets, e.time, 0.5, 'png').then(blob => {
      if (!alive) return;
      url = URL.createObjectURL(blob);
      setPreview(url);
    }).catch(() => { if (alive) setPreview(''); });
    return () => { alive = false; if (url) URL.revokeObjectURL(url); };
  }, [e.project, e.assets, e.time]);

  const close = () => ui.setFlow('none');
  const pick = (layerId: string) => { targetRef.current = layerId; fileRef.current?.click(); };
  const replace = async (files: FileList | null) => {
    if (!files?.length || !targetRef.current) return;
    try {
      const assets = await importMany(files);
      if (!assets.length) { e.notify('No supported media file found', 'warn'); return; }
      e.addAssets(assets);
      e.patchLayer(targetRef.current, { sourceId: assets[0].id });
      e.notify(`Media replaced with “${assets[0].name}”`, 'ok');
      force(v => v + 1);
    } catch (err) { e.notify((err as Error).message, 'warn'); }
    targetRef.current = '';
  };
  const finish = () => { setConfirm(false); close(); e.setScreen('editor'); ui.setSheet('none'); };

  return (
    <div className="um-flow" aria-label="Template editor">
      <header className="um-flow-head">
        <button type="button" title="Close template editor" onClick={close}><X size={20} /></button>
        <strong>Template editor</strong>
        <button type="button" className="um-flow-next" onClick={() => setConfirm(true)}>Next</button>
      </header>

      <div className="um-flow-preview">
        {preview
          ? <img src={preview} alt="Real render of the current composition" />
          : <div className="um-flow-placeholder"><Play size={34} /><span>Preview unavailable</span></div>}
      </div>

      <div className="um-flow-section">
        <h3>Replace media</h3>
        <p className="um-hint">Swap any media slot with a file from your device. Changes are applied to the real project.</p>
        {slots.length === 0 && <p className="um-empty-note">This project has no media layers to replace.</p>}
        <div className="um-flow-slots">
          {slots.map(l => {
            const a = l.sourceId ? e.assets[l.sourceId] : null;
            return (
              <div className="um-flow-slot" key={l.id}>
                <button type="button" onClick={() => pick(l.id)} title={`Replace ${l.name}`}>
                  <span className="um-flow-thumb">
                    {a?.thumbnail ? <img src={a.thumbnail} alt="" /> : <Play size={15} />}
                  </span>
                  <span><strong>{l.name}</strong><small>{a?.name ?? 'Missing media'}</small></span>
                  <Upload size={15} />
                </button>
              </div>
            );
          })}
        </div>
        <input ref={fileRef} type="file" hidden accept="image/*,video/*,audio/*"
          onChange={ev => { void replace(ev.target.files); ev.target.value = ''; }} />
      </div>

      {confirm && (
        <div className="um-flow-confirm">
          <div className="um-flow-confirm-card">
            <strong>Are you done replacing media?</strong>
            <p>Once you continue, you won't be able to go back. Replace the media to easily replace template files with your own.</p>
            <button type="button" className="um-flow-understood" onClick={finish}>
              <Check size={14} /> I'VE READ &amp; UNDERSTOOD
            </button>
            <button type="button" className="um-flow-gallery" onClick={finish}>VIEW IT IN MY GALLERY →</button>
          </div>
        </div>
      )}
    </div>
  );
}

function MissingFontsFlow() {
  const ui = useUI();
  const close = () => { ui.setMissingFonts([]); ui.setFlow('none'); };
  return (
    <div className="um-flow-backdrop" onClick={close}>
      <div className="um-fonts-card" onClick={ev => ev.stopPropagation()} aria-label="Missing Fonts">
        <strong>Missing Fonts</strong>
        <p>This project uses fonts that are not installed in EditorsHub on your device, so text layers may display incorrectly.</p>
        <p className="um-fonts-label">Missing Fonts:</p>
        <ul>{ui.missingFonts.map(f => <li key={f}>• {f}</li>)}</ul>
        <p className="um-hint">Upload the font from the Add sheet to install it for this project.</p>
        <button type="button" onClick={close}>OK</button>
      </div>
    </div>
  );
}

function ExportPreviewFlow() {
  const e = useEditor();
  const ui = useUI();
  const [preview, setPreview] = useState('');
  useEffect(() => {
    let alive = true; let url = '';
    void renderStill(e.project, e.assets, e.time, 0.6, 'png').then(blob => {
      if (!alive) return; url = URL.createObjectURL(blob); setPreview(url);
    }).catch(() => undefined);
    return () => { alive = false; if (url) URL.revokeObjectURL(url); };
  }, [e.project, e.assets, e.time]);
  return (
    <div className="um-flow" aria-label="Export preview">
      <header className="um-flow-head">
        <button type="button" title="Back" onClick={() => ui.setFlow('none')}><ArrowLeft size={20} /></button>
        <strong>Export Preview</strong>
        <button type="button" className="um-flow-next" onClick={() => { ui.setFlow('none'); ui.setSheet('export'); }}>Export</button>
      </header>
      <div className="um-flow-preview big">
        {preview ? <img src={preview} alt="Real render of the export frame" /> : <div className="um-flow-placeholder"><Play size={34} /></div>}
      </div>
      <div className="um-flow-stats">
        <span>{e.project.width} × {e.project.height}</span>
        <span>{e.project.fps} fps</span>
        <span>{e.project.duration.toFixed(1)} s</span>
        <span>{e.project.layers.length} layers</span>
      </div>
    </div>
  );
}

export function Flows() {
  const ui = useUI();
  if (ui.flow === 'replaceMedia') return <ReplaceMediaFlow />;
  if (ui.flow === 'missingFonts') return <MissingFontsFlow />;
  if (ui.flow === 'exportPreview') return <ExportPreviewFlow />;
  return null;
}
