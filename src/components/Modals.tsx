import React, { useEffect, useRef, useState } from 'react';
import {
  AlertTriangle, Check, Copy, Diamond, Download, Eye, EyeOff, FileCode2, FileInput,
  FilePlus2, Film, FolderOpen, Keyboard, Lock, Music2, Plus, Scissors, Sparkles,
  Trash2, Unlock, Upload, X,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import {
  exportFrameSequence, framesFileName, pickVideoMime, recordVideo, renderStill,
  videoFileName, type ExportProgress,
} from '../editor/export';
import { parseMotionXml, SAMPLE_XML, type XmlImportResult } from '../editor/xml';
import { createDemoProject, createProject } from '../editor/defaults';
import { BLEND_LABELS, type BlendMode } from '../editor/types';
import { BLEND_MODES } from '../editor/defaults';
import { downloadBlob, safeFileName } from '../editor/utils';
import { Btn, IconBtn, Modal, Progress, SelectField } from './ui';

/* ------------------------------- Export modal ------------------------------ */

type ExportMode = 'video' | 'frames' | 'still';

export function ExportModal() {
  const e = useEditor();
  const [mode, setMode] = useState<ExportMode>('video');
  const [scale, setScale] = useState(0.75);
  const [bitrate, setBitrate] = useState(8_000_000);
  const [audio, setAudio] = useState(true);
  const [frameFormat, setFrameFormat] = useState<'png' | 'jpeg'>('png');
  const [progress, setProgress] = useState<ExportProgress | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<{ url: string; name: string; size: number } | null>(null);
  const urlRef = useRef<string | null>(null);
  const mime = pickVideoMime();

  useEffect(() => () => { if (urlRef.current) URL.revokeObjectURL(urlRef.current); }, []);

  const finish = (blob: Blob, name: string) => {
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    const url = URL.createObjectURL(blob);
    urlRef.current = url;
    setResult({ url, name, size: blob.size });
    downloadBlob(blob, name);
    e.notify('Export finished — download started', 'ok');
  };

  const start = async () => {
    if (running) return;
    setRunning(true); setError(''); setResult(null);
    setProgress({ phase: 'Starting…', ratio: 0 });
    try {
      if (mode === 'video') {
        const { blob, ext } = await recordVideo(e.project, e.assets, { scale, bitrate, includeAudio: audio }, setProgress);
        finish(blob, videoFileName(e.project, ext));
      } else if (mode === 'frames') {
        const { blob } = await exportFrameSequence(
          e.project, e.assets,
          { scale: Math.min(scale, 1), format: frameFormat, maxFrames: 900 },
          setProgress,
        );
        finish(blob, framesFileName(e.project));
      } else {
        setProgress({ phase: 'Rendering still…', ratio: 0.3 });
        const blob = await renderStill(e.project, e.assets, e.time, 1, 'png');
        setProgress({ phase: 'Done', ratio: 1 });
        finish(blob, `${safeFileName(e.project.name)}_frame.png`);
      }
    } catch (err) {
      setError((err as Error).message || 'Export failed');
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal title="Export" onClose={() => e.setModal(null)} wide footer={
      <>
        <Btn variant="ghost" onClick={() => e.setModal(null)}>Close</Btn>
        <Btn variant="primary" onClick={() => void start()} disabled={running}>
          <Download size={15} /> {running ? 'Exporting…' : 'Start export'}
        </Btn>
      </>
    }>
      <div className="mode-tabs">
        <button type="button" className={mode === 'video' ? 'on' : ''} onClick={() => setMode('video')}><Film size={16} /> Video</button>
        <button type="button" className={mode === 'frames' ? 'on' : ''} onClick={() => setMode('frames')}><Copy size={16} /> Frames (ZIP)</button>
        <button type="button" className={mode === 'still' ? 'on' : ''} onClick={() => setMode('still')}><Download size={16} /> Still PNG</button>
      </div>

      <div className="export-options">
        {mode !== 'still' && (
          <SelectField label="Render resolution" value={String(scale)} options={[
            { value: '0.5', label: `Half (${Math.round(e.project.width * 0.5)}×${Math.round(e.project.height * 0.5)})` },
            { value: '0.75', label: `75% (${Math.round(e.project.width * 0.75)}×${Math.round(e.project.height * 0.75)})` },
            { value: '1', label: `Full (${e.project.width}×${e.project.height})` },
          ]} onChange={v => setScale(Number(v))} />
        )}
        {mode === 'video' && (
          <>
            <SelectField label="Quality / bitrate" value={String(bitrate)} options={[
              { value: '2000000', label: 'Low · 2 Mbps' }, { value: '5000000', label: 'Medium · 5 Mbps' },
              { value: '8000000', label: 'High · 8 Mbps' }, { value: '12000000', label: 'Very high · 12 Mbps' },
              { value: '20000000', label: 'Lossless-ish · 20 Mbps' },
            ]} onChange={v => setBitrate(Number(v))} />
            <label className="check-row"><input type="checkbox" checked={audio} onChange={ev => setAudio(ev.target.checked)} /> Include layer audio (Web Audio mix)</label>
            <p className="hint block">
              Browser codec: <b>{mime.mime || 'default WebM'}</b> → .{mime.ext}. Recording runs in real time
              ({e.project.duration.toFixed(1)}s) while the compositor draws each frame.
            </p>
          </>
        )}
        {mode === 'frames' && (
          <>
            <SelectField label="Image format" value={frameFormat} options={[{ value: 'png', label: 'PNG (lossless)' }, { value: 'jpeg', label: 'JPEG (smaller)' }]} onChange={v => setFrameFormat(v as 'png' | 'jpeg')} />
            <p className="hint block">Renders {Math.min(Math.round(e.project.duration * e.project.fps), 900)} exact frames, seeking videos frame-by-frame, then packs them into a ZIP.</p>
          </>
        )}
        {mode === 'still' && <p className="hint block">Renders the frame at the current playhead ({e.time.toFixed(2)}s) at full resolution as a PNG.</p>}
      </div>

      {progress && <Progress ratio={progress.ratio} label={progress.phase} />}
      {error && <div className="error-box"><AlertTriangle size={15} /> {error}</div>}
      {result && (
        <div className="success-box">
          <Check size={16} />
          <div><strong>{result.name}</strong><small>{(result.size / 1024 / 1024).toFixed(2)} MB · download started</small></div>
          <a className="btn btn-soft" href={result.url} download={result.name}>Download again</a>
        </div>
      )}
    </Modal>
  );
}

/* ------------------------------ XML import modal --------------------------- */

export function XmlImportModal() {
  const e = useEditor();
  const [text, setText] = useState('');
  const [result, setResult] = useState<XmlImportResult | null>(null);
  const [error, setError] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const parse = async (src: string) => {
    setError(''); setResult(null);
    try { setResult(await parseMotionXml(src)); }
    catch (err) { setError((err as Error).message); }
  };

  const readFile = async (file: File) => {
    const content = await file.text();
    setText(content);
    void parse(content);
  };

  const openAsProject = () => {
    if (!result) return;
    const p = result.project ?? (() => {
      const np = createProject('Imported Preset', e.project.width, e.project.height);
      np.layers = result.layers;
      np.duration = Math.max(np.duration, ...result.layers.map(l => l.start + l.duration));
      return np;
    })();
    e.replaceProject(p, result.assets);
    e.setModal(null);
    e.setPanel('edit');
  };

  const addToCurrent = () => {
    if (!result) return;
    e.addImportedLayers(result.layers, result.assets);
    e.setModal(null);
    e.setPanel('edit');
  };

  return (
    <Modal title="Import XML project / preset" onClose={() => e.setModal(null)} wide footer={
      <>
        <Btn variant="ghost" onClick={() => e.setModal(null)}>Cancel</Btn>
        <Btn variant="soft" onClick={() => void parse(text)} disabled={!text.trim()}><FileCode2 size={15} /> Validate XML</Btn>
        <Btn variant="primary" onClick={openAsProject} disabled={!result}>Open as project</Btn>
        <Btn variant="soft" onClick={addToCurrent} disabled={!result}>Add layers to current</Btn>
      </>
    }>
      <p className="hint block">Paste or choose an <b>.xml</b> project/preset. EditorsHubMotion XML, Alight-Motion-style presets and generic layer XML are all parsed; embedded <code>data:</code> media is restored automatically.</p>
      <div className="row-buttons">
        <Btn variant="soft" onClick={() => fileRef.current?.click()}><Upload size={15} /> Choose .xml file</Btn>
        <Btn variant="soft" onClick={() => { setText(SAMPLE_XML); void parse(SAMPLE_XML); }}><Sparkles size={15} /> Load example</Btn>
        <Btn variant="soft" onClick={() => downloadBlob(new Blob([SAMPLE_XML], { type: 'application/xml' }), 'sample_project.xml')}><Download size={15} /> Save example</Btn>
      </div>
      <input ref={fileRef} type="file" hidden accept=".xml,.txt,.amxml,text/xml,application/xml" onChange={ev => { const f = ev.target.files?.[0]; if (f) void readFile(f); ev.target.value = ''; }} />
      <textarea className="xml-textarea" value={text} onChange={ev => setText(ev.target.value)} placeholder="<EditorsHubMotion …> … </EditorsHubMotion>" spellCheck={false} rows={9} />

      {error && <div className="error-box"><AlertTriangle size={15} /> {error}</div>}
      {result && (
        <div className="xml-report">
          <div className="report-stats">
            <div><b>{result.stats.layers}</b><span>layers</span></div>
            <div><b>{result.stats.keyframes}</b><span>keyframes</span></div>
            <div><b>{result.stats.effects}</b><span>effects</span></div>
            <div><b>{result.stats.assets}</b><span>media</span></div>
          </div>
          <p className="ok-line"><Check size={14} /> Valid {result.project ? 'project' : 'preset'}: {result.layers.map(l => l.name).join(', ')}</p>
          {result.warnings.map((w, i) => <p className="warn-line" key={i}><AlertTriangle size={14} /> {w}</p>)}
        </div>
      )}
    </Modal>
  );
}

/* ------------------------------ projects modal ---------------------------- */

export function ProjectsModal() {
  const e = useEditor();
  const list = e.listProjects();
  return (
    <Modal title="Projects" onClose={() => e.setModal(null)} wide footer={
      <Btn variant="primary" onClick={() => e.setModal('newProject')}><FilePlus2 size={15} /> New project</Btn>
    }>
      <p className="hint block">Projects are saved in this browser (localStorage) together with media in IndexedDB. Use XML export to move a project to another device.</p>
      <div className="projects-list">
        {list.length === 0 && <p className="hint">No saved projects yet.</p>}
        {list.map(p => (
          <div className={`project-row ${p.id === e.project.id ? 'current' : ''}`} key={p.id}>
            <div className="project-icon"><FolderOpen size={18} /></div>
            <div className="project-meta">
              <strong>{p.name}</strong>
              <small>{p.width}×{p.height} · {p.duration}s · {p.layerCount} layers · {new Date(p.updatedAt).toLocaleString()}</small>
            </div>
            {p.id === e.project.id && <span className="tag">Current</span>}
            <Btn variant="soft" onClick={() => { e.openProject(p.id); e.setModal(null); }}>Open</Btn>
            <IconBtn danger title="Delete" onClick={() => e.deleteProject(p.id)}><Trash2 size={14} /></IconBtn>
          </div>
        ))}
      </div>
    </Modal>
  );
}

export function NewProjectModal() {
  const e = useEditor();
  const [width, setWidth] = useState(1080);
  const [height, setHeight] = useState(1920);
  const [demo, setDemo] = useState(false);
  const presets = [
    { label: '9:16 Story', w: 1080, h: 1920 }, { label: '1:1', w: 1080, h: 1080 },
    { label: '16:9', w: 1920, h: 1080 }, { label: '4:5', w: 1080, h: 1350 },
  ];
  const create = () => {
    if (demo) {
      const p = createDemoProject();
      p.width = width; p.height = height;
      e.replaceProject(p);
    } else e.newProject(width, height, true);
    e.setModal(null);
    e.setPanel('add');
    e.notify('Project created', 'ok');
  };
  return (
    <Modal title="New project" onClose={() => e.setModal(null)} footer={
      <><Btn variant="ghost" onClick={() => e.setModal(null)}>Cancel</Btn><Btn variant="primary" onClick={create}>Create</Btn></>
    }>
      <div className="chip-row">
        {presets.map(p => (
          <button type="button" key={p.label} className={`chip ${width === p.w && height === p.h ? 'on' : ''}`} onClick={() => { setWidth(p.w); setHeight(p.h); }}>
            {p.label}<small>{p.w}×{p.h}</small>
          </button>
        ))}
      </div>
      <div className="field-grid">
        <label className="field"><span>Width</span><input type="number" value={width} onChange={ev => setWidth(Number(ev.target.value))} /></label>
        <label className="field"><span>Height</span><input type="number" value={height} onChange={ev => setHeight(Number(ev.target.value))} /></label>
      </div>
      <label className="check-row"><input type="checkbox" checked={demo} onChange={ev => setDemo(ev.target.checked)} /> Start with the animated demo composition</label>
      <p className="hint block">Your current project stays saved and can be re-opened from the Projects list.</p>
    </Modal>
  );
}

/* ----------------------------- layer menu modal ---------------------------- */

export function LayerMenuModal() {
  const e = useEditor();
  const l = e.selectedLayer;
  return (
    <Modal title="Layer options" onClose={() => e.setModal(null)} footer={<Btn variant="ghost" onClick={() => e.setModal(null)}>Close</Btn>}>
      <div className="subhead">Select layer</div>
      <div className="layer-picker">
        {e.project.layers.map(x => (
          <button type="button" key={x.id} className={x.id === e.selectedLayerId ? 'on' : ''} onClick={() => e.selectLayer(x.id)}>
            <span className={`type-tag t-${x.type}`}>{x.type}</span> {x.name}
          </button>
        ))}
      </div>
      {l && (
        <>
          <div className="subhead">Actions for “{l.name}”</div>
          <div className="action-grid">
            <button type="button" onClick={() => e.duplicateLayer(l.id)}><Copy size={15} /> Duplicate</button>
            <button type="button" onClick={() => e.splitLayer(l.id)}><Scissors size={15} /> Split</button>
            <button type="button" onClick={() => e.copyLayer()}><Copy size={15} /> Copy</button>
            <button type="button" onClick={() => e.pasteLayer()}><Plus size={15} /> Paste</button>
            <button type="button" onClick={() => e.toggleVisible(l.id)}>{l.visible ? <EyeOff size={15} /> : <Eye size={15} />} {l.visible ? 'Hide' : 'Show'}</button>
            <button type="button" onClick={() => e.toggleLock(l.id)}>{l.locked ? <Unlock size={15} /> : <Lock size={15} />} {l.locked ? 'Unlock' : 'Lock'}</button>
            <button type="button" onClick={() => e.moveLayer(l.id, 1)}>Move up ↑</button>
            <button type="button" onClick={() => e.moveLayer(l.id, -1)}>Move down ↓</button>
            <button type="button" className="danger" onClick={() => { e.deleteLayer(l.id); e.setModal(null); }}><Trash2 size={15} /> Delete</button>
          </div>
          <SelectField label="Blend mode" value={l.blend} options={BLEND_MODES.map(b => ({ value: b, label: BLEND_LABELS[b] }))} onChange={v => e.setBlend(l.id, v as BlendMode)} />
        </>
      )}
    </Modal>
  );
}

/* -------------------------------- help modal ------------------------------- */

export function HelpModal() {
  const e = useEditor();
  const shortcuts = [
    ['Space', 'Play / pause'], ['← / →', 'Step one frame'], ['K', 'Add keyframe'],
    ['S', 'Split layer'], ['D', 'Duplicate layer'], ['Delete', 'Delete layer'],
    ['Ctrl/⌘ + Z', 'Undo'], ['Ctrl/⌘ + Y', 'Redo'], ['1 – 5', 'Switch panels'], ['E', 'Export'],
  ];
  return (
    <Modal title="Quick start & shortcuts" onClose={() => e.setModal(null)} wide footer={<Btn variant="primary" onClick={() => e.setModal(null)}>Got it</Btn>}>
      <div className="help-grid">
        <div>
          <h3><Keyboard size={16} /> Keyboard</h3>
          <table className="shortcuts">{shortcuts.map(([k, v]) => <tr key={k}><td><kbd>{k}</kbd></td><td>{v}</td></tr>)}</table>
        </div>
        <div>
          <h3><Sparkles size={16} /> Editing</h3>
          <ol className="help-list">
            <li><b>Add</b> media, text, shapes or solids from the Add tab.</li>
            <li><b>Drag</b> layer bars in the timeline to move them; drag their edges to trim.</li>
            <li><b>Edit</b> values in the Edit tab. Press the ◇ diamond to set a keyframe at the playhead, or turn on Auto-keyframe.</li>
            <li><b>Animate</b> by moving the playhead and changing values; pick easing in the Keys tab.</li>
            <li><b>Effects</b> stack on the selected layer and can also be keyframed.</li>
            <li><b>Export</b> a real video (MediaRecorder), a PNG frame ZIP, an XML project or a still frame.</li>
          </ol>
        </div>
      </div>
      <div className="tip-card"><FileInput size={15} /><span>XML importing accepts EditorsHubMotion projects, Alight-Motion-style presets, and generic layer XML. See <code>XML_FORMAT.md</code> in the repository for the schema.</span></div>
      <div className="tip-card"><Music2 size={15} /><span>Video/audio layers play from your local files. Audio is mixed into exported videos via Web Audio.</span></div>
    </Modal>
  );
}
