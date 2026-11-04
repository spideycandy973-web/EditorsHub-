import React, { useRef, useState } from 'react';
import {
  ChevronRight, FileArchive, FolderOpen, Globe2, Home as HomeIcon, Plus,
  QrCode, Search, Settings2, Sparkles, Trash2, Upload, UserRound,
} from 'lucide-react';
import { editor, useEditor } from '../editor/store';
import { createDemoProject } from '../editor/defaults';
import { useUI } from './context';
import { Backdrop, Btn, IBtn, Seg, Sheet } from './widgets';
import {
  applyXmlResult, decodeQr, deleteElement, fetchPackage, listElements, parseXml,
  type SavedElement,
} from './lib';
import { parseMotionXml } from '../editor/xml';

function Nav() {
  const ui = useUI();
  return (
    <nav className="um-nav">
      <button type="button" className={ui.homeTab === 'home' ? 'on' : ''}
        onClick={() => { ui.setHomeTab('home'); ui.setSheet('none'); }}>
        <HomeIcon size={19} strokeWidth={1.8} /><span>Home</span>
      </button>
      <button type="button" className="um-nav-plus" title="Create project or element"
        onClick={() => { ui.setCreateTab('project'); ui.setSheet('create'); }}>
        <Plus size={26} strokeWidth={2.2} />
      </button>
      <button type="button" className={ui.homeTab === 'projects' ? 'on' : ''}
        onClick={() => { ui.setHomeTab('projects'); ui.setSheet('none'); }}>
        <FolderOpen size={19} strokeWidth={1.8} /><span>Projects</span>
      </button>
    </nav>
  );
}

function Header() {
  const ui = useUI();
  return (
    <header className="um-home-header">
      <IBtn title="Settings" onClick={() => ui.setSheet('settings')} className="um-round">
        <Settings2 size={20} strokeWidth={1.7} />
      </IBtn>
      <div className="um-wordmark">
        <strong>EDITORS HUB</strong>
        <span>motion studio · editorshub.app</span>
      </div>
      <IBtn title="About EditorsHub" onClick={() => ui.setSheet('about')} className="um-round um-avatar">
        <UserRound size={20} strokeWidth={1.7} />
      </IBtn>
    </header>
  );
}

function ImportCard() {
  const e = useEditor();
  const ui = useUI();
  const fileRef = useRef<HTMLInputElement>(null);
  const qrRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const handleXmlFile = async (file: File) => {
    setError(''); setBusy(true);
    try {
      const text = await file.text();
      const result = await parseXml(text);
      finish(result);
    } catch (err) { setError((err as Error).message); }
    finally { setBusy(false); }
  };

  /** Open a clean import immediately; show the compatibility report when
   *  the parser produced warnings so missing media is never hidden. */
  const finish = (result: Awaited<ReturnType<typeof parseXml>>) => {
    if (result.warnings.length) { ui.setXmlResult(result); ui.setSheet('xml'); return; }
    applyXmlResult(result, 'project');
    ui.setSheet('none');
    e.notify(`Imported ${result.stats.layers} layer(s)`, 'ok');
  };

  const handleImport = async () => {
    const url = ui.importUrl.trim();
    if (!url) { setError('Paste a link to an XML project or a zipped project package.'); return; }
    setError(''); setBusy(true);
    try {
      const src = await fetchPackage(url);
      const result = await parseXml(src);
      finish(result);
    } catch (err) { setError((err as Error).message); }
    finally { setBusy(false); }
  };

  const handleQr = async (file: File) => {
    setError(''); setBusy(true);
    try {
      const link = await decodeQr(file);
      ui.setImportUrl(link);
      const src = await fetchPackage(link);
      const result = await parseXml(src);
      finish(result);
    } catch (err) { setError((err as Error).message); }
    finally { setBusy(false); }
  };

  return (
    <>
      <div className="um-card um-upload-card">
        <span className="um-card-icon um-icon-xml"><FileArchive size={24} strokeWidth={1.5} /></span>
        <div className="um-card-copy"><strong>Upload XML file</strong><small>Open a project or element preset</small></div>
        <button type="button" className="um-lavender small" disabled={busy}
          onClick={() => fileRef.current?.click()}>Upload</button>
      </div>

      <div className="um-card um-package-card">
        <div className="um-package-title">
          <span className="um-card-icon"><Globe2 size={22} strokeWidth={1.5} /></span>
          <div><strong>Import Project Package</strong><small>Paste a link or scan the QR code.</small></div>
        </div>
        <input className="um-input" placeholder="https://alight…" aria-label="Project package link"
          value={ui.importUrl} onChange={ev => ui.setImportUrl(ev.target.value)} />
        <button type="button" className="um-lavender" disabled={busy} onClick={() => void handleImport()}>
          {busy ? 'Importing…' : 'Import'}
        </button>
        <button type="button" className="um-qr" disabled={busy} onClick={() => qrRef.current?.click()}>
          <QrCode size={17} strokeWidth={2} /> Scan QR image
        </button>
        {error && <p className="um-error">{error}</p>}
      </div>

      <input ref={fileRef} type="file" hidden accept=".xml,.amxml,.txt,text/xml,application/xml"
        onChange={ev => { const f = ev.target.files?.[0]; if (f) void handleXmlFile(f); ev.target.value = ''; }} />
      <input ref={qrRef} type="file" hidden accept="image/*"
        onChange={ev => { const f = ev.target.files?.[0]; if (f) void handleQr(f); ev.target.value = ''; }} />
    </>
  );
}

function HomeBody() {
  const e = useEditor();
  const ui = useUI();
  const projects = e.listProjects().slice().sort((a, b) => b.updatedAt - a.updatedAt);
  return (
    <div className="um-home-body">
      <ImportCard />
      {projects.length > 0 && (
        <section className="um-recent">
          <h3>Recent projects <button type="button" onClick={() => { ui.setHomeTab('projects'); ui.setHomeSegment('projects'); }}>View all <ChevronRight size={13} /></button></h3>
          {projects.slice(0, 3).map(p => (
            <button type="button" key={p.id} className="um-recent-row"
              onClick={() => { e.openProject(p.id); e.setScreen('editor'); ui.setSheet('none'); }}>
              <span className="um-recent-thumb" style={{ background: 'linear-gradient(135deg,#2f2f36,#17171b)' }}>
                {p.name.charAt(0).toUpperCase()}
              </span>
              <span><strong>{p.name}</strong><small>{p.width}×{p.height} · {p.duration.toFixed(1)}s · {p.layerCount} layers</small></span>
              <ChevronRight size={17} />
            </button>
          ))}
        </section>
      )}
    </div>
  );
}

function ProjectsBody() {
  const e = useEditor();
  const ui = useUI();
  const [query, setQuery] = useState('');
  const projects = e.listProjects().slice().sort((a, b) => b.updatedAt - a.updatedAt);
  const elements = listElements();
  const q = query.trim().toLowerCase();

  const templates = [
    { name: 'Kinetic Intro', sub: 'Title animation · 9:16', w: 1080, h: 1920, a: '#8b5cf6', b: '#22d3ee', glyph: '✦' },
    { name: 'Neon Lower Third', sub: 'Broadcast · 16:9', w: 1920, h: 1080, a: '#02ffa7', b: '#0ea5e9', glyph: '▰' },
    { name: 'Photo Slideshow', sub: 'Editable layers · 4:5', w: 1080, h: 1350, a: '#fb7185', b: '#f59e0b', glyph: '▣' },
    { name: 'Glow Loop', sub: 'Effects · 1:1', w: 1080, h: 1080, a: '#22d3ee', b: '#6366f1', glyph: '✺' },
  ];

  const openTemplate = (name: string, w: number, h: number) => {
    const p = createDemoProject();
    p.name = name; p.width = w; p.height = h;
    e.replaceProject(p); e.setScreen('editor'); ui.setSheet('none');
    e.notify(`Template “${name}” opened`, 'ok');
  };

  const openElement = (el: SavedElement) => {
    void (async () => {
      try {
        const r = await parseMotionXml(el.xml);
        applyXmlResult(r, 'project');
        ui.setSheet('none');
        e.notify(`Element “${el.name}” opened`, 'ok');
      } catch (err) { e.notify((err as Error).message, 'warn'); }
    })();
  };

  const filteredProjects = projects.filter(p => !q || p.name.toLowerCase().includes(q));
  const filteredTemplates = templates.filter(t => !q || t.name.toLowerCase().includes(q));
  const filteredElements = elements.filter(el => !q || el.name.toLowerCase().includes(q));

  return (
    <div className="um-projects-body">
      <Seg value={ui.homeSegment} onChange={ui.setHomeSegment} options={[
        { id: 'projects', label: 'Projects' },
        { id: 'templates', label: 'Your Templates' },
        { id: 'elements', label: 'Elements' },
      ]} />
      <label className="um-search">
        <Search size={16} strokeWidth={1.7} />
        <input value={query} onChange={ev => setQuery(ev.target.value)}
          placeholder={ui.homeSegment === 'elements' ? 'Search elements' : ui.homeSegment === 'templates' ? 'Search templates' : 'Search projects'}
          aria-label="Search" />
        {query && <button type="button" title="Clear search" onClick={() => setQuery('')}>×</button>}
      </label>

      {ui.homeSegment === 'projects' && (
        <div className="um-list">
          {filteredProjects.map(p => (
            <div className="um-list-row" key={p.id}>
              <button type="button" className="um-list-main" onClick={() => { e.openProject(p.id); e.setScreen('editor'); ui.setSheet('none'); }}>
                <span className="um-recent-thumb">{p.name.charAt(0).toUpperCase()}</span>
                <span><strong>{p.name}</strong><small>{p.width}×{p.height} · {p.duration.toFixed(1)}s · {p.layerCount} layers</small></span>
              </button>
              <IBtn title={`Delete ${p.name}`} onClick={() => e.deleteProject(p.id)}><Trash2 size={16} /></IBtn>
            </div>
          ))}
          {filteredProjects.length === 0 && <p className="um-empty-note">No projects{q ? ' match that search' : ' yet'}. Tap + to create one.</p>}
        </div>
      )}

      {ui.homeSegment === 'templates' && (
        <div className="um-template-grid">
          {filteredTemplates.map(t => (
            <button type="button" key={t.name} className="um-template-tile"
              onClick={() => openTemplate(t.name, t.w, t.h)}>
              <span className="um-template-art" style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}</span>
              <strong>{t.name}</strong><small>{t.sub}</small>
            </button>
          ))}
          {filteredTemplates.length === 0 && <p className="um-empty-note">No templates match that search.</p>}
        </div>
      )}

      {ui.homeSegment === 'elements' && (
        <>
          {filteredElements.length === 0 ? (
            <div className="um-empty">
              <strong>No elements... yet!</strong>
              <span>Unleash your imagination,<br />create an element.</span>
              <svg width="86" height="116" viewBox="0 0 86 116" aria-hidden="true">
                <path d="M10 6c34 2 50 24 44 52-4 19-22 30-31 36" fill="none" stroke="#e9e9ee" strokeWidth="2.4" strokeLinecap="round" />
                <path d="m14 82 9 12 11-8" fill="none" stroke="#e9e9ee" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <Btn variant="dark" onClick={() => { ui.setCreateTab('element'); ui.setSheet('create'); }}>
                <Sparkles size={15} /> Create an element
              </Btn>
            </div>
          ) : (
            <div className="um-list">
              {filteredElements.map(el => (
                <div className="um-list-row" key={el.id}>
                  <button type="button" className="um-list-main" onClick={() => openElement(el)}>
                    <span className="um-recent-thumb"><Sparkles size={17} /></span>
                    <span><strong>{el.name}</strong><small>{el.width}×{el.height} · saved {new Date(el.createdAt).toLocaleDateString()}</small></span>
                  </button>
                  <IBtn title={`Delete ${el.name}`} onClick={() => deleteElement(el.id)}><Trash2 size={16} /></IBtn>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

export function Home() {
  const ui = useUI();
  return (
    <div className="um-screen um-home">
      <Header />
      {ui.homeTab === 'home' ? <HomeBody /> : <ProjectsBody />}
      <Nav />
      {ui.sheet === 'settings' && <><Backdrop onClick={() => ui.setSheet('none')} /><Sheet variant="floating" title="Settings" onClose={() => ui.setSheet('none')} className="um-settings-card"><SettingsBody /></Sheet></>}
    </div>
  );
}

function SettingsBody() {
  const ui = useUI();
  const { prefs, setPrefs } = ui;
  return (
    <div className="um-settings">
      <div className="um-row"><span>Low Quality Preview</span>
        <button type="button" role="switch" aria-checked={prefs.lowQualityPreview} title="Low Quality Preview"
          className={`um-toggle ${prefs.lowQualityPreview ? 'on' : ''}`}
          onClick={() => setPrefs({ ...prefs, lowQualityPreview: !prefs.lowQualityPreview })}><span /></button>
      </div>
      <div className="um-row"><span>Show Off Timeline ⓘ</span>
        <button type="button" role="switch" aria-checked={prefs.showOffTimeline} title="Show Off Timeline"
          className={`um-toggle ${prefs.showOffTimeline ? 'on' : ''}`}
          onClick={() => setPrefs({ ...prefs, showOffTimeline: !prefs.showOffTimeline })}><span /></button>
      </div>
      <div className="um-row"><span>Remember Add page ⓘ</span>
        <button type="button" role="switch" aria-checked={prefs.rememberAddPage} title="Remember Add page"
          className={`um-toggle ${prefs.rememberAddPage ? 'on' : ''}`}
          onClick={() => setPrefs({ ...prefs, rememberAddPage: !prefs.rememberAddPage })}><span /></button>
      </div>
      <div className="um-row">
        <span>Default Layer Duration</span>
        <label className="um-select compact">
          <select value={String(prefs.defaultLayerDuration)}
            onChange={ev => setPrefs({ ...prefs, defaultLayerDuration: Number(ev.target.value) })}>
            {[1, 2, 3, 4, 5, 8, 10].map(n => <option key={n} value={n}>{n}s</option>)}
          </select>
        </label>
      </div>
      <div className="um-settings-label">Audio Resampling</div>
      <Seg value={prefs.audioResampling} onChange={v => setPrefs({ ...prefs, audioResampling: v })}
        options={[{ id: 'low', label: 'Low' }, { id: 'high', label: 'High' }, { id: 'ultra', label: 'Ultra' }]} />
      <div className="um-settings-brand">✦ EDITORS HUB ✦</div>
      <button type="button" className="um-link-row" onClick={() => window.open('https://github.com/spideycandy973-web/EditorsHub-', '_blank', 'noopener')}>
        <span className="um-link-icon light"><Globe2 size={17} /></span><b>Website</b><ChevronRight size={17} />
      </button>
      <button type="button" className="um-link-row" onClick={() => window.open('https://github.com/spideycandy973-web/EditorsHub-/releases', '_blank', 'noopener')}>
        <span className="um-link-icon blue"><Upload size={17} /></span><b>Downloads & updates</b><ChevronRight size={17} />
      </button>
      <button type="button" className="um-link-row" onClick={() => {
        const url = 'https://github.com/spideycandy973-web/EditorsHub-';
        void navigator.clipboard?.writeText(url).then(() => editor.notify('Project link copied', 'ok'))
          .catch(() => editor.notify(url, 'info'));
      }}>
        <span className="um-link-icon dark"><QrCode size={17} /></span><b>Share this app</b><ChevronRight size={17} />
      </button>
      <div className="um-settings-divider" />
      <button type="button" className="um-link-row about" onClick={() => ui.setSheet('about')}>
        <b>About</b><ChevronRight size={17} />
      </button>
    </div>
  );
}
