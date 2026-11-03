import React, { useRef, useState } from 'react';
import {
  Download, FileCode2, FileInput, FileJson2, Film, FolderOpen, Image as ImageIcon,
  Plus, Save, Settings2, Trash2, Upload,
} from 'lucide-react';
import { useEditor } from '../../editor/store';
import { projectToXml } from '../../editor/xml';
import { renderStill } from '../../editor/export';
import type { Project } from '../../editor/types';
import { downloadBlob, downloadText, safeFileName } from '../../editor/utils';
import { Btn, ColorField, NumberField, Section, SelectField, Toggle } from '../ui';

const RESOLUTIONS = [
  { label: '9:16 Story', w: 1080, h: 1920 },
  { label: '1:1 Square', w: 1080, h: 1080 },
  { label: '16:9 Video', w: 1920, h: 1080 },
  { label: '4:5 Post', w: 1080, h: 1350 },
  { label: '720p HD', w: 1280, h: 720 },
];

export function ProjectPanel() {
  const e = useEditor();
  const [saveName, setSaveName] = useState('');
  const [embed, setEmbed] = useState(false);
  const [busy, setBusy] = useState('');
  const jsonRef = useRef<HTMLInputElement>(null);

  const exportXml = async () => {
    setBusy('Building XML…');
    try {
      const { xml, warnings } = await projectToXml(e.project, e.assets, { embedMedia: embed });
      downloadText(xml, `${safeFileName(e.project.name)}.xml`);
      if (warnings.length) e.notify(warnings[0], 'warn');
      else e.notify('XML project exported', 'ok');
    } finally { setBusy(''); }
  };

  const exportJson = () => {
    downloadText(JSON.stringify(e.project, null, 2), `${safeFileName(e.project.name)}.json`, 'application/json');
    e.notify('JSON project exported', 'ok');
  };

  const importJson = async (file: File) => {
    try {
      const p = JSON.parse(await file.text()) as Project;
      if (!p.layers || !p.width || !p.height) throw new Error('Missing project fields');
      e.replaceProject(p);
      e.notify(`Imported “${p.name}”`, 'ok');
    } catch (err) {
      e.notify(`Invalid project JSON: ${(err as Error).message}`, 'warn');
    }
  };

  const saveFrame = async () => {
    setBusy('Rendering frame…');
    try {
      const blob = await renderStill(e.project, e.assets, e.time, 1, 'png');
      downloadBlob(blob, `${safeFileName(e.project.name)}_frame.png`);
      e.notify('Frame saved as PNG', 'ok');
    } finally { setBusy(''); }
  };

  return (
    <div className="panel-body">
      {busy && <div className="busy-bar">{busy}</div>}
      <div className="panel-note"><Save size={13} /> Changes are autosaved locally after every edit.</div>

      <Section title="Composition" right={<Settings2 size={14} />}>
        <label className="field">
          <span>Project name</span>
          <input type="text" value={e.project.name} onChange={ev => e.setProjectSettings({ name: ev.target.value })} />
        </label>
        <div className="subhead">Canvas preset</div>
        <div className="chip-row">
          {RESOLUTIONS.map(r => (
            <button key={r.label} type="button" className={`chip ${e.project.width === r.w && e.project.height === r.h ? 'on' : ''}`}
              onClick={() => e.setProjectSettings({ width: r.w, height: r.h })}>
              {r.label}<small>{r.w}×{r.h}</small>
            </button>
          ))}
        </div>
        <div className="field-grid">
          <NumberField label="Width" value={e.project.width} min={16} max={4096} step={1} suffix="px" onChange={v => e.setProjectSettings({ width: v })} />
          <NumberField label="Height" value={e.project.height} min={16} max={4096} step={1} suffix="px" onChange={v => e.setProjectSettings({ height: v })} />
          <NumberField label="Duration" value={e.project.duration} min={0.5} max={600} step={0.5} suffix="s" onChange={v => e.setProjectSettings({ duration: v })} />
        </div>
        <SelectField label="Frame rate" value={String(e.project.fps)} options={['12', '24', '25', '30', '50', '60'].map(v => ({ value: v, label: `${v} fps` }))} onChange={v => e.setProjectSettings({ fps: Number(v) })} />
        <ColorField label="Background" value={e.project.background} onChange={v => e.setProjectSettings({ background: v })} />
        <Toggle label="Grid overlay" checked={e.showGrid} onChange={v => { if (v !== e.showGrid) e.toggleGrid(); }} />
        <Toggle label="Safe area guides" checked={e.showGuides} onChange={v => { if (v !== e.showGuides) e.toggleGuides(); }} />
      </Section>

      <Section title="Projects" right={<span className="hint">{e.listProjects().length} saved</span>}>
        <div className="row-buttons wrap">
          <Btn variant="primary" onClick={() => e.setModal('newProject')}><Plus size={14} /> New project</Btn>
          <Btn variant="soft" onClick={() => e.setModal('projects')}><FolderOpen size={14} /> Open / manage</Btn>
          <Btn variant="soft" onClick={() => { e.saveNow(); e.notify('Project saved', 'ok'); }}><Save size={14} /> Save now</Btn>
        </div>
        <div className="field">
          <span>Save a copy as</span>
          <div className="field-input">
            <input type="text" placeholder={e.project.name} value={saveName} onChange={ev => setSaveName(ev.target.value)} />
            <button type="button" onClick={() => { e.saveAs(saveName || `${e.project.name} copy`); setSaveName(''); }}>Save</button>
          </div>
        </div>
        <div className="saved-list">
          {e.listProjects().slice(0, 4).map(p => (
            <div className="saved-row" key={p.id}>
              <button type="button" onClick={() => e.openProject(p.id)}>
                <strong>{p.name}</strong><small>{p.width}×{p.height} · {p.layerCount} layers</small>
              </button>
              <button type="button" className="icon-btn danger" title="Delete project" onClick={() => e.deleteProject(p.id)}><Trash2 size={13} /></button>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Export & import" right={<Film size={14} />}>
        <Btn variant="primary" className="full" onClick={() => e.setModal('export')}><Download size={15} /> Export video / frames</Btn>
        <div className="row-buttons wrap">
          <Btn variant="soft" onClick={() => void exportXml()}><FileCode2 size={14} /> Export XML</Btn>
          <Btn variant="soft" onClick={() => e.setModal('xml')}><FileInput size={14} /> Import XML</Btn>
          <Btn variant="soft" onClick={exportJson}><FileJson2 size={14} /> Export JSON</Btn>
          <Btn variant="soft" onClick={() => jsonRef.current?.click()}><Upload size={14} /> Import JSON</Btn>
          <Btn variant="soft" onClick={() => void saveFrame()}><ImageIcon size={14} /> Save frame PNG</Btn>
        </div>
        <Toggle label="Embed media in XML" checked={embed} onChange={setEmbed} hint="Base64 images/videos ≤12 MB" />
        <input ref={jsonRef} type="file" hidden accept=".json,application/json" onChange={ev => { const f = ev.target.files?.[0]; if (f) void importJson(f); ev.target.value = ''; }} />
      </Section>

      <Section title="About" defaultOpen={false}>
        <p className="hint block">EditorsHub is a real browser-based compositor. Every frame is drawn from your layers, keyframes and effects by the canvas renderer — nothing is faked. Exports are encoded with MediaRecorder or exported as a PNG frame sequence.</p>
        <div className="info-list">
          <div><span>Layers</span><b>{e.project.layers.length}</b></div>
          <div><span>Assets</span><b>{Object.keys(e.assets).length}</b></div>
          <div><span>Version</span><b>1.0.0</b></div>
        </div>
      </Section>
    </div>
  );
}
