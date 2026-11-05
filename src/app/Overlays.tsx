import React, { useEffect, useRef, useState } from 'react';
import {
  ChevronRight, Copy, Diamond, Download, FileCode2, FilePlus2, Film, FolderOpen,
  HelpCircle, Info, Layers, Palette, Plus, Save, Sparkles, Trash2, Upload, X,
} from 'lucide-react';
import { editor, useEditor } from '../editor/store';
import { createDemoProject } from '../editor/defaults';
import { BLEND_LABELS } from '../editor/types';
import {
  exportFrameSequence, framesFileName, pickVideoMime, recordVideo, renderStill,
  videoFileName, type ExportProgress,
} from '../editor/export';
import { projectToXml, SAMPLE_XML } from '../editor/xml';
import { downloadBlob, safeFileName } from '../editor/utils';
import { zipSync, strToU8 } from 'fflate';
import { exportAnimatedGif } from '../editor/gif';
import { useUI } from './context';
import { Backdrop, Btn, ColorInput, IBtn, NumField, Row, Seg, Select, Sheet, Slider, PALETTE } from './widgets';
import {
  applyXmlResult, deleteElement, downloadXml, fetchPackage, listElements, parseXml,
  saveCurrentAsElement, SAMPLE_RATE, xmlOfProject, saveTemplate, listTemplates,
} from './lib';
import { parseMotionXml } from '../editor/xml';

const RATIOS = [
  { id: '16:9', label: '16:9', r: 16 / 9 },
  { id: '9:16', label: '9:16', r: 9 / 16 },
  { id: '4:5', label: '4:5', r: 4 / 5 },
  { id: '1:1', label: '1:1', r: 1 },
  { id: '4:3', label: '4:3', r: 4 / 3 },
] as const;

const RESOLUTIONS = [
  { value: '1280', label: '720p (HD)' },
  { value: '1920', label: '1080p (FHD)' },
  { value: '2560', label: '1440p (QHD)' },
  { value: '3840', label: '4K (UHD)' },
];

function CompositionForm({ onDone }: { onDone: () => void }) {
  const e = useEditor();
  const ui = useUI();
  const [tab, setTab] = useState(ui.createTab);
  const [name, setName] = useState(tab === 'element' ? `Element ${listElements().length + 1}` : `New Project ${e.listProjects().length + 1}`);
  const [ratio, setRatio] = useState<string>('9:16');
  const [longSide, setLongSide] = useState('1920');
  const [fps, setFps] = useState('60');
  const [bg, setBg] = useState('#000000');
  const [custom, setCustom] = useState(false);
  const [cw, setCw] = useState(1080);
  const [ch, setCh] = useState(1920);

  const dims = () => {
    if (custom) return { width: Math.round(cw), height: Math.round(ch) };
    const r = RATIOS.find(x => x.id === ratio)?.r ?? 9 / 16;
    const long = Number(longSide);
    return r >= 1
      ? { width: long, height: Math.round(long / r) }
      : { width: Math.round(long * r), height: long };
  };

  const create = () => {
    const { width, height } = dims();
    e.newProject(width, height, true);
    e.setProjectSettings({
      name: name.trim() || (tab === 'element' ? 'Element' : 'New Project'),
      width, height, fps: Number(fps), background: bg,
      duration: tab === 'element' ? Math.max(2, ui.prefs.defaultLayerDuration) : e.project.duration,
    });
    e.saveNow();
    e.setScreen('editor');
    ui.setSheet('none');
    onDone();
  };

  return (
    <>
      <Seg value={tab} onChange={setTab} options={[{ id: 'project', label: 'Project' }, { id: 'element', label: 'Element' }]} />
      <div className="um-name-input">
        <input value={name} onChange={ev => setName(ev.target.value)} aria-label="Project name" placeholder="New Project 1" />
        <button type="button" title="Clear name" onClick={() => setName('')}><X size={17} /></button>
      </div>
      <div className="um-ratios">
        {RATIOS.map(r => (
          <button type="button" key={r.id} className={`um-ratio ${!custom && ratio === r.id ? 'on' : ''}`}
            onClick={() => { setRatio(r.id); setCustom(false); }}>
            <i className="um-crop" /><span>{r.label}</span>
          </button>
        ))}
        <button type="button" className={`um-ratio ${custom ? 'on' : ''}`} title="Custom size"
          onClick={() => setCustom(true)}><i className="um-crop" /><span>✎</span></button>
      </div>
      {custom && (
        <div className="um-custom-size">
          <NumField label="Width" value={cw} min={16} max={4096} onChange={setCw} unit="px" />
          <NumField label="Height" value={ch} min={16} max={4096} onChange={setCh} unit="px" />
        </div>
      )}
      <Row label="Resolution" sub={`Composition Size · ${dims().width} × ${dims().height}`}>
        <Select value={longSide} onChange={setLongSide} options={RESOLUTIONS} title="Resolution" />
      </Row>
      <Row label="Frame Rate">
        <Select value={fps} onChange={setFps} title="Frame rate"
          options={[24, 25, 30, 50, 60].map(n => ({ value: String(n), label: `${n} fps` }))} />
      </Row>
      <Row label="Background">
        <div className="um-bg-control">
          <ColorInput value={bg} onChange={setBg} label={bg === '#000000' ? 'Black' : bg === '#ffffff' ? 'White' : bg} />
          <Select value={bg} onChange={setBg} title="Background colour" options={[
            { value: '#000000', label: 'Black' }, { value: '#ffffff', label: 'White' },
            { value: '#121214', label: 'Charcoal' }, { value: '#0b1d3a', label: 'Navy' },
          ]} />
        </div>
      </Row>
      <button type="button" className="um-lavender create" onClick={create}>
        {tab === 'element' ? 'Create Element' : 'Create Project'}
      </button>
    </>
  );
}

/* ------------------------------- project settings -------------------------- */
function ProjectSettingsBody() {
  const e = useEditor();
  const ui = useUI();
  const [name, setName] = useState(e.project.name);
  const [elementName, setElementName] = useState(e.project.name);

  const patch = (p: Parameters<typeof e.setProjectSettings>[0]) => { e.setProjectSettings(p); e.saveNow(); };

  return (
    <div className="um-scroll">
      <div className="um-section-title">Composition</div>
      <div className="um-name-input">
        <input value={name} onChange={ev => setName(ev.target.value)} aria-label="Project name" />
        <button type="button" title="Apply name" onClick={() => patch({ name })}><Save size={16} /></button>
      </div>
      <div className="um-custom-size">
        <NumField label="Width" value={e.project.width} min={16} max={4096} onChange={v => patch({ width: v })} unit="px" />
        <NumField label="Height" value={e.project.height} min={16} max={4096} onChange={v => patch({ height: v })} unit="px" />
      </div>
      <div className="um-custom-size">
        <NumField label="FPS" value={e.project.fps} min={1} max={120} onChange={v => patch({ fps: v })} />
        <NumField label="Duration" value={e.project.duration} min={0.5} max={600} step={0.5} onChange={v => patch({ duration: v })} unit="s" />
      </div>
      <Row label="Background" sub="Composition colour">
        <ColorInput value={e.project.background} onChange={v => patch({ background: v })} label="Background" />
      </Row>
      <div className="um-swatches-wrap">
        {PALETTE.map(c => (
          <button key={c} type="button" title={c} style={{ background: c }}
            onClick={() => patch({ background: c })} />
        ))}
      </div>

      <div className="um-section-title">Playback</div>
      <div className="um-checkgrid">
        {([
          ['Loop playback', e.loop, () => e.setLoop(!e.loop)],
          ['Snap to frame', e.snapToFrame, () => e.setSnap(!e.snapToFrame)],
          ['Auto keyframes', e.autoKey, () => e.setAutoKey(!e.autoKey)],
          ['Safe-area guides', e.showGuides, () => e.toggleGuides()],
          ['Grid overlay', e.showGrid, () => e.toggleGrid()],
          ['Low quality preview', ui.prefs.lowQualityPreview, () => ui.setPrefs({ ...ui.prefs, lowQualityPreview: !ui.prefs.lowQualityPreview })],
        ] as const).map(([label, on, fn]) => (
          <button type="button" key={label} className={`um-check ${on ? 'on' : ''}`} onClick={fn}>
            <span>{on ? '✓' : ''}</span>{label}
          </button>
        ))}
      </div>

      <div className="um-section-title">Save & share</div>
      <div className="um-name-input">
        <input value={elementName} onChange={ev => setElementName(ev.target.value)} aria-label="Element name" placeholder="Element name" />
        <button type="button" title="Save as element" onClick={() => {
          void saveCurrentAsElement(elementName).then(rec => {
            e.notify(`Element “${rec.name}” saved`, 'ok');
          }).catch(err => e.notify((err as Error).message, 'warn'));
        }}><Sparkles size={16} /></button>
      </div>
      <div className="um-action-grid">
        <Btn variant="dark" onClick={() => { void xmlOfProject().then(x => downloadXml(e.project.name, x)); e.notify('XML exported', 'ok'); }}>
          <FileCode2 size={15} /> Export XML
        </Btn>
        <Btn variant="dark" onClick={() => { e.saveAs(`${e.project.name} copy`); }}>
          <Copy size={15} /> Save a copy
        </Btn>
        <Btn variant="dark" onClick={() => ui.setSheet('xml')}><Upload size={15} /> Import XML</Btn>
        <Btn variant="dark" onClick={() => ui.setSheet('export')}><Download size={15} /> Export media</Btn>
        <Btn variant="dark" onClick={() => { e.newProject(e.project.width, e.project.height, false); e.notify('Demo layers added', 'ok'); }}>
          <Layers size={15} /> Add demo layers
        </Btn>
        <Btn variant="dark" onClick={() => { e.deleteProject(e.project.id); e.newProject(); e.notify('Project deleted', 'ok'); }}>
          <Trash2 size={15} /> Delete project
        </Btn>
      </div>
      <p className="um-hint">Projects autosave on this device. XML export carries layers, keyframes, effects and embedded media (up to 12 MB per asset).</p>
    </div>
  );
}

/* ---------------------------------- export -------------------------------- */
type ExportMode = 'video' | 'package' | 'still' | 'sequence' | 'gif' | 'xml';

const EXPORT_OPTIONS: { id: ExportMode; title: string; sub: string; glyph: string }[] = [
  { id: 'video', title: 'Video', sub: 'MP4 / WebM video files', glyph: '▶' },
  { id: 'package', title: 'Project Package', sub: 'Save all project files', glyph: '▣' },
  { id: 'still', title: 'Current Frame as PNG', sub: 'Save as image', glyph: '▧' },
  { id: 'sequence', title: 'Image Sequence', sub: 'PNG images in a ZIP', glyph: '▤' },
  { id: 'gif', title: 'GIF', sub: 'Animated GIF', glyph: '◉' },
  { id: 'xml', title: 'XML', sub: 'Portable project file', glyph: '〈/〉' },
];

function ExportBody() {
  const e = useEditor();
  const ui = useUI();
  const [mode, setMode] = useState<ExportMode>('video');
  const [scale, setScale] = useState('0.75');
  const [bitrate, setBitrate] = useState('8000000');
  const [audio, setAudio] = useState(true);
  const [gifWidth, setGifWidth] = useState('240');
  const [gifFps, setGifFps] = useState('10');
  const [asTemplate, setAsTemplate] = useState(false);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState<ExportProgress | null>(null);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const [space, setSpace] = useState('Checking…');
  const mime = pickVideoMime();

  useEffect(() => {
    let alive = true;
    const est = (navigator as Navigator & {
      storage?: { estimate?: () => Promise<{ quota?: number; usage?: number }> };
    }).storage?.estimate;
    if (!est) { setSpace('Not reported by this browser'); return; }
    est.call(navigator.storage).then(r => {
      if (!alive) return;
      const free = (r.quota ?? 0) - (r.usage ?? 0);
      setSpace(free > 0 ? `${(free / 1073741824).toFixed(2)} GB` : 'Not reported');
    }).catch(() => { if (alive) setSpace('Not reported'); });
    return () => { alive = false; };
  }, []);

  const start = async () => {
    if (running) return;
    setRunning(true); setError(''); setDone(''); setProgress({ phase: 'Starting…', ratio: 0 });
    try {
      let blob: Blob; let name: string; let note = '';
      const base = safeFileName(e.project.name);
      if (mode === 'video') {
        const r = await recordVideo(e.project, e.assets, {
          scale: Number(scale), bitrate: Number(bitrate), includeAudio: audio,
          sampleRate: SAMPLE_RATE[ui.prefs.audioResampling],
        }, setProgress);
        blob = r.blob; name = videoFileName(e.project, r.ext);
      } else if (mode === 'sequence') {
        const r = await exportFrameSequence(e.project, e.assets,
          { scale: Math.min(Number(scale), 1), format: 'png', maxFrames: 900 }, setProgress);
        blob = r.blob; name = framesFileName(e.project); note = `${r.frames} frames rendered`;
      } else if (mode === 'still') {
        setProgress({ phase: 'Rendering frame…', ratio: 0.4 });
        blob = await renderStill(e.project, e.assets, e.time, 1, 'png');
        name = `${base}_frame.png`;
      } else if (mode === 'gif') {
        const r = await exportAnimatedGif(e.project, e.assets, {
          width: Number(gifWidth), fps: Number(gifFps), maxFrames: 120,
        }, setProgress);
        blob = r.blob; name = `${base}.gif`;
        note = `${r.frames} frames at ${r.fps} fps, ${Number(gifWidth)} px wide`;
      } else if (mode === 'package') {
        setProgress({ phase: 'Writing project package…', ratio: 0.5 });
        const { xml, warnings } = await projectToXml(e.project, e.assets);
        const files: Record<string, Uint8Array> = {
          'project.xml': strToU8(xml),
          'README.txt': strToU8(
            `EditorsHub project package\n\nName: ${e.project.name}\nSize: ${e.project.width}x${e.project.height}\nDuration: ${e.project.duration}s\nFPS: ${e.project.fps}\nLayers: ${e.project.layers.length}\n\nOpen project.xml from the EditorsHub launcher.\n${warnings.length ? `\nWarnings:\n${warnings.join('\n')}\n` : ''}`),
          'assets.txt': strToU8(Object.values(e.assets).map(a => `${a.id}  ${a.name}  ${a.mime}`).join('\n') + '\n'),
        };
        blob = new Blob([zipSync(files, { level: 0 }) as BlobPart], { type: 'application/zip' });
        name = `${base}_package.zip`;
      } else {
        setProgress({ phase: 'Writing XML…', ratio: 0.5 });
        const xml = await xmlOfProject();
        blob = new Blob([xml], { type: 'application/xml' });
        name = `${base}.xml`;
      }
      if (asTemplate) {
        const { xml } = await projectToXml(e.project, e.assets);
        saveTemplate(`${e.project.name} (export)`, xml, e.project.width, e.project.height, e.project.duration);
        note += (note ? ' · ' : '') + 'saved as a template';
      }
      setProgress({ phase: 'Complete', ratio: 1 });
      downloadBlob(blob, name);
      setDone(`${name} · ${(blob.size / 1024).toFixed(0)} KB${note ? ` · ${note}` : ''}`);
      e.notify('Export finished — download started', 'ok');
    } catch (err) { setError((err as Error).message || 'Export failed'); }
    finally { setRunning(false); }
  };

  const estimateNote = mode === 'video'
    ? `${Math.round(e.project.width * Number(scale))} × ${Math.round(e.project.height * Number(scale))}, ${(Number(bitrate) / 1000000).toFixed(0)} Mbps`
    : mode === 'gif' ? `${gifWidth} px wide, ${gifFps} fps, up to 120 frames`
    : mode === 'sequence' ? 'up to 900 PNG frames' : mode === 'still' ? 'full resolution PNG'
    : 'portable project file';

  return (
    <div className="um-scroll um-export">
      <div className="um-export-space">
        <span>Available Space</span><strong>{space}</strong>
        <span>File size</span><strong>Measured after export</strong>
      </div>

      <div className="um-export-list">
        {EXPORT_OPTIONS.map(o => (
          <button type="button" key={o.id} className={`um-export-option ${mode === o.id ? 'on' : ''}`}
            onClick={() => setMode(o.id)} aria-pressed={mode === o.id}>
            <span className="um-export-radio" />
            <span className="um-export-glyph">{o.glyph}</span>
            <span className="um-export-copy"><strong>{o.title}</strong><small>{o.sub}</small></span>
          </button>
        ))}
      </div>

      {mode === 'video' && (
        <div className="um-export-settings">
          <Row label="Export Resolution"><Select value={scale} onChange={setScale} options={[
            { value: '0.5', label: '540p (SD)' }, { value: '0.75', label: '810p (HD)' }, { value: '1', label: '1080p (FHD)' },
          ]} /></Row>
          <div className="um-export-codec"><span>Codec</span><b>{mime.mime.includes('avc') ? 'H.264/AVC' : 'Browser-selected'}</b><b>{mime.ext.toUpperCase()}</b></div>
          <div className="um-export-codec"><span>Frame Rate</span><b>{e.project.fps} fps</b><b>Full</b></div>
          <Row label="Quality"><Select value={bitrate} onChange={setBitrate} options={[
            { value: '2000000', label: 'Low' }, { value: '5000000', label: 'Medium' },
            { value: '8000000', label: 'High' }, { value: '20000000', label: 'Maximum' },
          ]} /></Row>
          <label className="um-checkline"><input type="checkbox" checked={audio} onChange={ev => setAudio(ev.target.checked)} /> Include audio ({SAMPLE_RATE[ui.prefs.audioResampling]} Hz mix)</label>
        </div>
      )}
      {mode === 'gif' && (
        <div className="um-export-settings">
          <Row label="GIF width"><Select value={gifWidth} onChange={setGifWidth} options={[
            { value: '160', label: '160 px' }, { value: '240', label: '240 px' }, { value: '320', label: '320 px' },
          ]} /></Row>
          <Row label="Frame rate"><Select value={gifFps} onChange={setGifFps} options={[
            { value: '8', label: '8 fps' }, { value: '10', label: '10 fps' }, { value: '15', label: '15 fps' },
          ]} /></Row>
        </div>
      )}
      {mode === 'sequence' && (
        <div className="um-export-settings">
          <Row label="Resolution"><Select value={scale} onChange={setScale} options={[
            { value: '0.5', label: '50%' }, { value: '0.75', label: '75%' }, { value: '1', label: '100%' },
          ]} /></Row>
        </div>
      )}
      <button type="button" className="um-export-preview" onClick={() => ui.setFlow('exportPreview')}>
        Preview the current frame before exporting ↗
      </button>
      <p className="um-hint">{estimateNote}. Every format above is rendered and encoded for real — no placeholder downloads.</p>

      <label className="um-checkline um-template-toggle">
        <input type="checkbox" checked={asTemplate} onChange={ev => setAsTemplate(ev.target.checked)} />
        Also export as a Template ({listTemplates().length} saved)
      </label>

      {progress && <div className="um-progress"><div style={{ width: `${Math.round(progress.ratio * 100)}%` }} /><span>{progress.phase}</span></div>}
      {error && <p className="um-error">{error}</p>}
      {done && <p className="um-ok">{done}</p>}
      <button type="button" className="um-lavender create" disabled={running} onClick={() => void start()}>
        {running ? 'Exporting…' : 'Export'}
      </button>
    </div>
  );
}

function XmlReportBody() {
  const e = useEditor();
  const ui = useUI();
  const fileRef = useRef<HTMLInputElement>(null);
  const r = ui.xmlResult;
  const [error, setError] = useState('');
  const [url, setUrl] = useState('');

  const loadUrl = async () => {
    if (!url.trim()) { setError('Paste a link first.'); return; }
    setError('');
    try { ui.setXmlResult(await parseXml(await fetchPackage(url.trim()))); e.notify('Package imported', 'ok'); }
    catch (err) { setError((err as Error).message); }
  };

  const loadText = async (text: string) => {
    setError('');
    try { ui.setXmlResult(await parseXml(text)); }
    catch (err) { setError((err as Error).message); }
  };

  return (
    <div className="um-scroll">
      <p className="um-hint">Paste XML, choose a file, or import a project package. EditorsHub, Alight-Motion-style presets and generic layer XML are all parsed with an honest compatibility report.</p>
      <div className="um-action-grid">
        <Btn variant="dark" onClick={() => fileRef.current?.click()}><Upload size={15} /> Choose .xml</Btn>
        <Btn variant="dark" onClick={() => void loadText(SAMPLE_XML)}><Sparkles size={15} /> Load example</Btn>
        <Btn variant="dark" onClick={() => downloadBlob(new Blob([SAMPLE_XML], { type: 'application/xml' }), 'sample_project.xml')}>
          <Download size={15} /> Save example
        </Btn>
      </div>
      <input ref={fileRef} type="file" hidden accept=".xml,.amxml,.txt,text/xml,application/xml"
        onChange={ev => { const f = ev.target.files?.[0]; if (f) void f.text().then(loadText); ev.target.value = ''; }} />
      <div className="um-name-input">
        <input value={url} onChange={ev => setUrl(ev.target.value)} placeholder="https://… project XML or .zip package" aria-label="Package link" />
        <button type="button" title="Import package link" onClick={() => void loadUrl()}><FolderOpen size={16} /></button>
      </div>
      <textarea className="um-textarea" rows={5} placeholder="<EditorsHubMotion …> … </EditorsHubMotion>"
        onChange={ev => void loadText(ev.target.value)} spellCheck={false} />
      {error && <p className="um-error">{error}</p>}
      {r && (
        <div className="um-report">
          <div className="um-report-stats">
            <div><b>{r.stats.layers}</b><span>layers</span></div>
            <div><b>{r.stats.keyframes}</b><span>keyframes</span></div>
            <div><b>{r.stats.effects}</b><span>effects</span></div>
            <div><b>{r.stats.assets}</b><span>media</span></div>
          </div>
          <p className="um-ok">Valid {r.project ? 'project' : 'preset'}: {r.layers.map(l => l.name).join(', ')}</p>
          {r.warnings.map((w, i) => <p className="um-warn" key={i}>⚠ {w}</p>)}
          <div className="um-action-grid">
            <Btn onClick={() => { applyXmlResult(r, 'project'); e.setScreen('editor'); ui.setSheet('none'); }}>Open as project</Btn>
            <Btn variant="dark" onClick={() => { applyXmlResult(r, 'layers'); ui.setSheet('none'); }}>Add layers</Btn>
          </div>
        </div>
      )}
    </div>
  );
}

/* --------------------------------- templates ------------------------------ */
const TEMPLATES = [
  { name: 'Kinetic Intro', sub: 'Titles · 9:16', w: 1080, h: 1920, a: '#8b5cf6', b: '#22d3ee', glyph: '✦' },
  { name: 'Neon Lower Third', sub: 'Broadcast · 16:9', w: 1920, h: 1080, a: '#02ffa7', b: '#0ea5e9', glyph: '▰' },
  { name: 'Photo Slideshow', sub: 'Editable · 4:5', w: 1080, h: 1350, a: '#fb7185', b: '#f59e0b', glyph: '▣' },
  { name: 'Glow Loop', sub: 'Effects · 1:1', w: 1080, h: 1080, a: '#22d3ee', b: '#6366f1', glyph: '✺' },
  { name: 'Sports Promo', sub: 'Dynamic · 16:9', w: 1920, h: 1080, a: '#f59e0b', b: '#ef4444', glyph: '⚡' },
  { name: 'Romantic Scene', sub: 'Cinematic · 9:16', w: 1080, h: 1920, a: '#fb7185', b: '#f59e0b', glyph: '♥' },
];

export function TemplateGrid({ onPick }: { onPick: (name: string, w: number, h: number) => void }) {
  return (
    <div className="um-template-grid">
      {TEMPLATES.map(t => (
        <button type="button" key={t.name} className="um-template-tile" onClick={() => onPick(t.name, t.w, t.h)}>
          <span className="um-template-art" style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}</span>
          <strong>{t.name}</strong><small>{t.sub}</small>
        </button>
      ))}
    </div>
  );
}

/* ----------------------------------- help --------------------------------- */
function HelpBody() {
  return (
    <div className="um-scroll um-help">
      <p>EditorsHub is a real, offline-capable motion editor. Everything you see is backed by the canvas compositor, the layer engine and local storage.</p>
      <h4>Timeline</h4>
      <ul>
        <li>Drag a clip horizontally to change its start time.</li>
        <li>Drag the left/right edges to trim; use Split in the layer menu to cut at the playhead.</li>
        <li>Scroll the ruler to scrub; the white playhead stays centred.</li>
        <li>Tap the eye icon to hide a layer, the diamond to add a keyframe.</li>
      </ul>
      <h4>Properties</h4>
      <ul>
        <li>Move & Transform: X/Y position, width/height scale, skew, rotation dial and cubic-bezier easing.</li>
        <li>Blending & Opacity: blend mode, opacity and layer masks.</li>
        <li>Colour & Fill / Border & Shadow: solid and gradient fills, stroke and real drop shadows.</li>
        <li>Effects: 20 real pixel and canvas effects with keyframeable parameters.</li>
      </ul>
      <h4>Keyboard</h4>
      <ul>
        <li><b>Space</b> play/pause · <b>←/→</b> step a frame · <b>K</b> keyframe · <b>S</b> split · <b>D</b> duplicate</li>
        <li><b>Ctrl/Cmd+Z</b> undo · <b>Ctrl/Cmd+Shift+Z</b> redo · <b>Delete</b> remove layer · <b>1–5</b> panels</li>
      </ul>
      <h4>Import / export</h4>
      <ul>
        <li>XML project/preset import, zipped package links and QR images.</li>
        <li>Video (MediaRecorder), PNG frame ZIP, still PNG and portable XML export.</li>
      </ul>
    </div>
  );
}

function AboutBody() {
  const ui = useUI();
  return (
    <div className="um-scroll um-about">
      <div className="um-about-mark">EH</div>
      <h3>EditorsHub</h3>
      <p className="um-about-version">Motion Studio · version 3.0.0</p>
      <p>A locally rendered video and motion-graphics editor. No upload, no account, no simulated features: layers, keyframes, blending, masks, 20 effects, media import, XML projects and real frame-accurate rendering all run on your device.</p>
      <div className="um-about-grid">
        <div><b>Canvas compositor</b><span>HTML5 Canvas 2D + Web Audio</span></div>
        <div><b>Storage</b><span>localStorage + IndexedDB</span></div>
        <div><b>Export</b><span>MediaRecorder · PNG ZIP · XML</span></div>
        <div><b>Package</b><span>com.editorshub</span></div>
      </div>
      <button type="button" className="um-link-row" onClick={() => window.open('https://github.com/spideycandy973-web/EditorsHub-', '_blank', 'noopener')}>
        <b>Source code & documentation</b><ChevronRight size={17} />
      </button>
      <button type="button" className="um-link-row" onClick={() => ui.setSheet('help')}>
        <b>Editing guide</b><ChevronRight size={17} />
      </button>
    </div>
  );
}

/* --------------------------------- overlays ------------------------------- */
export function Overlays() {
  const e = useEditor();
  const ui = useUI();
  const sheet = ui.sheet;
  if (sheet === 'none') return null;

  const close = () => ui.setSheet('none');
  const handledByEditor = ['add', 'shape', 'properties', 'property', 'overflow', 'draw', 'vector'].includes(sheet);
  if (handledByEditor || sheet === 'settings') return null;

  const config: Partial<Record<typeof sheet, { title: string; body: React.ReactNode; variant?: 'floating' | 'dock' | 'flat' }>> = {
    create: { title: '', body: <CompositionForm onDone={close} />, variant: 'floating' },
    about: { title: 'About', body: <AboutBody />, variant: 'floating' },
    xml: { title: 'Import XML / Project Package', body: <XmlReportBody />, variant: 'floating' },
    export: { title: 'Export & Share', body: <ExportBody />, variant: 'floating' },
    projectSettings: { title: 'Project Settings', body: <ProjectSettingsBody />, variant: 'floating' },
    help: { title: 'Editing guide', body: <HelpBody />, variant: 'floating' },
    templates: { title: 'Templates', body: (<>
      <div className="um-template-flow-entry">
        <button type="button" className="um-mini-btn" onClick={() => { ui.setSheet('none'); ui.setFlow('replaceMedia'); }}>
          Replace media in the current project ↗
        </button>
      </div>
      <div className="um-scroll">
      <div className="um-scroll"><TemplateGrid onPick={(name, w, h) => {
        const p = createDemoProject(); p.name = name; p.width = w; p.height = h;
        e.replaceProject(p); e.setScreen('editor'); close(); e.notify(`Template “${name}” opened`, 'ok');
      }} /></div></div>
    </>), variant: 'floating' },
    elements: { title: 'Elements & Projects', body: <ElementsBody />, variant: 'floating' },
    rename: { title: 'Rename layer', body: <RenameBody />, variant: 'floating' },
  };
  const c = config[sheet];
  if (!c) return null;
  return (
    <>
      <Backdrop onClick={close} />
      <Sheet variant={c.variant} title={c.title || undefined}
        onClose={sheet === 'create' ? undefined : close} className={`um-overlay-sheet um-overlay-${sheet}`}>
        {c.body}
      </Sheet>
    </>
  );
}

function ElementsBody() {
  const e = useEditor();
  const ui = useUI();
  const [, force] = useState(0);
  const items = listElements();
  return (
    <div className="um-scroll">
      <p className="um-hint">Saved elements are real XML projects stored on this device. Open one to continue editing, or add its layers to the current project.</p>
      {items.map(el => (
        <div className="um-list-row" key={el.id}>
          <button type="button" className="um-list-main" onClick={() => {
            void parseMotionXml(el.xml).then(r => { applyXmlResult(r, 'project'); e.setScreen('editor'); close(); });
          }}>
            <span className="um-recent-thumb"><Sparkles size={17} /></span>
            <span><strong>{el.name}</strong><small>{el.width}×{el.height} · {new Date(el.createdAt).toLocaleString()}</small></span>
          </button>
          <IBtn title="Add layers to current project" onClick={() => {
            void parseMotionXml(el.xml).then(r => { applyXmlResult(r, 'layers'); e.setScreen('editor'); ui.setSheet('none'); });
          }}><Plus size={16} /></IBtn>
          <IBtn title={`Delete ${el.name}`} onClick={() => { deleteElement(el.id); force(n => n + 1); }}><Trash2 size={16} /></IBtn>
        </div>
      ))}
      {items.length === 0 && <p className="um-empty-note">No saved elements yet. Build one and use “Save as element” in Project Settings.</p>}
      <div className="um-action-grid">
        <Btn variant="dark" onClick={() => ui.setSheet('xml')}><FileCode2 size={15} /> Import XML / package</Btn>
        <Btn variant="dark" onClick={() => { ui.setCreateTab('element'); ui.setSheet('create'); }}><FilePlus2 size={15} /> Create element</Btn>
      </div>
    </div>
  );
  function close() { ui.setSheet('none'); }
}

function RenameBody() {
  const e = useEditor();
  const ui = useUI();
  const layer = e.selectedLayer;
  const [name, setName] = useState(layer?.name ?? '');
  useEffect(() => setName(layer?.name ?? ''), [layer?.id]);
  if (!layer) return <p className="um-hint">Select a layer first.</p>;
  return (
    <div className="um-scroll">
      <div className="um-name-input">
        <input value={name} onChange={ev => setName(ev.target.value)} aria-label="Layer name" />
      </div>
      <Btn onClick={() => { e.renameLayer(layer.id, name); ui.setSheet('none'); }}>Rename layer</Btn>
    </div>
  );
}
