import { useState } from 'react';
import { X, Copy, Play, SkipBack, SkipForward, Star, Trash2, Upload } from 'lucide-react';
import { useEditor } from '../editor/store';
import { MOTION_PRESETS } from '../editor/defaults';
import { useUI } from './context';
import { applyPreset, deletePreset, listPresets, savePreset } from './lib';

/**
 * The "No presets… yet!" dialog from the reference APK/screenshots, rebuilt with
 * original artwork. Every action is real: presets are stored in localStorage,
 * applied to the selected layer, and the built-in motion gallery applies real
 * keyframes through the editor engine.
 */
export function PresetsDialog() {
  const ui = useUI();
  const e = useEditor();
  const [tab, setTab] = useState<'home' | 'discover'>('home');
  const [, force] = useState(0);
  const presets = listPresets();
  const layer = e.selectedLayer;
  const close = () => ui.setPresetsDialog(false);

  const save = () => {
    if (!layer) { e.notify('Select a layer to save a preset', 'warn'); return; }
    try {
      const p = savePreset(`${layer.name} preset`);
      e.notify(`Preset “${p.name}” saved`, 'ok');
      force(v => v + 1);
    } catch (err) { e.notify((err as Error).message, 'warn'); }
  };
  const apply = (id: string) => {
    const p = listPresets().find(x => x.id === id);
    if (!p) return;
    try { applyPreset(p); e.notify(`Preset “${p.name}” applied`, 'ok'); close(); }
    catch (err) { e.notify((err as Error).message, 'warn'); }
  };

  return (
    <div className="um-presets-backdrop" onClick={close}>
      <div className="um-presets-card" onClick={ev => ev.stopPropagation()} aria-label="Presets">
        <button type="button" className="um-presets-x" title="Close" onClick={close}><X size={22} /></button>

        {tab === 'home' ? (
          <>
            <div className="um-preset-illustration" aria-hidden="true">
              <div className="um-pi-bar">
                <SkipBack size={15} /><Play size={15} /><SkipForward size={15} />
                <span className="um-pi-dup"><Copy size={15} /></span>
              </div>
              <div className="um-pi-time">00:00:00</div>
              <div className="um-pi-clip">Shape 1</div>
              <div className="um-pi-playhead" />
            </div>
            <h2>No presets... yet!</h2>
            <p>Save layers' properties and keyframes as<br />Presets in the editor.</p>
            <p className="um-presets-or">Or import Presets created by others!</p>
            <button type="button" className="um-presets-discover" onClick={() => setTab('discover')}>
              Discover Presets
            </button>

            {presets.length > 0 && (
              <div className="um-presets-list">
                {presets.map(p => (
                  <div className="um-presets-row" key={p.id}>
                    <button type="button" onClick={() => apply(p.id)}><Star size={14} /><strong>{p.name}</strong></button>
                    <button type="button" title={`Delete ${p.name}`} onClick={() => { deletePreset(p.id); force(v => v + 1); }}>
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
              </div>
            )}
            <div className="um-presets-mini">
              <button type="button" disabled={!layer} onClick={save}>Save selected layer</button>
              <button type="button" onClick={() => { close(); ui.setSheet('xml'); }}><Upload size={13} /> Import</button>
            </div>
            <button type="button" className="um-presets-close" onClick={close}>Close</button>
          </>
        ) : (
          <>
            <h2>Discover Presets</h2>
            <p className="um-presets-sub">{MOTION_PRESETS.length} built-in motion presets — all apply real keyframes.</p>
            <div className="um-presets-gallery">
              {MOTION_PRESETS.map(p => (
                <button type="button" key={p.id} disabled={!layer}
                  onClick={() => { e.applyMotionPreset(p.id); e.notify(`Preset “${p.label}” applied`, 'ok'); close(); }}>
                  <strong>{p.label}</strong><small>{p.description}</small>
                </button>
              ))}
            </div>
            <button type="button" className="um-presets-close" onClick={() => setTab('home')}>Back</button>
          </>
        )}
      </div>
    </div>
  );
}
