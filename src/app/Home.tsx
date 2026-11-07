import React, { useRef, useState } from 'react';
import {
  Check, ChevronDown, ChevronRight, Copy, Download, Globe2, House,
  MoreHorizontal, Music2, Plus, QrCode, Search, Send, Settings, Sparkles,
  SquarePlay, Trash2, UserRound, Wrench, X,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { createDemoProject } from '../editor/defaults';
import { BRAND } from './brand';
import { useUI } from './context';
import { Backdrop, Btn, IBtn, Seg, Sheet } from './widgets';
import {
  applyXmlResult, decodeQr, deleteElement, deleteTemplate, fetchPackage, listElements,
  listTemplates, parseXml, type SavedElement,
} from './lib';
import { parseMotionXml, projectToXml } from '../editor/xml';
import { loadProject, saveProject } from '../editor/storage';
import { downloadBlob, safeFileName, uid } from '../editor/utils';
import { zipSync, strToU8 } from 'fflate';

function Nav() {
  const ui = useUI();
  return (
    <nav className="um-nav">
      <button type="button" className={ui.homeTab === 'home' ? 'on' : ''}
        onClick={() => { ui.setHomeTab('home'); ui.setSheet('none'); }}>
        <House size={19} strokeWidth={1.9} fill="currentColor" /><span>Home</span>
      </button>
      <button type="button" className={`um-nav-plus ${ui.sheet !== 'none' ? 'close-nav' : ''}`}
        title={ui.sheet !== 'none' ? 'Close panel' : 'Create project or element'}
        onClick={() => {
          if (ui.sheet !== 'none') { ui.setSheet('none'); return; }
          ui.setCreateTab('project'); ui.setSheet('create');
        }}>
        {ui.sheet !== 'none' ? <X size={24} strokeWidth={2.2} /> : <Plus size={26} strokeWidth={2.2} />}
      </button>
      <button type="button" className={ui.homeTab === 'projects' ? 'on' : ''}
        onClick={() => { ui.setHomeTab('projects'); ui.setSheet('none'); }}>
        <SquarePlay size={19} strokeWidth={1.8} /><span>Projects</span>
      </button>
    </nav>
  );
}

function Header() {
  const ui = useUI();
  return (
    <header className="um-home-header">
      <IBtn title="Settings" onClick={() => ui.setSheet('settings')} className="um-gear">
        <Settings size={23} strokeWidth={1.8} />
      </IBtn>
      <div className="um-wordmark">
        <strong>{BRAND.wordmark}</strong>
        <span>{BRAND.domain}</span>
      </div>
      <IBtn title={`About ${BRAND.appName}`} onClick={() => ui.setAboutOpen(true)} className="um-avatar-solid">
        <UserRound size={20} strokeWidth={1.8} />
      </IBtn>
    </header>
  );
}

/* Original SVG glyphs drawn to match the shapes in the 28 reference JPGs:
 * a stack of picture frames with a gold upload arrow, and a hexagon enclosing
 * an isometric cube. All paths are authored here — no bitmap is copied. */
function StackedImagesArrow() {
  return (
    <svg width="34" height="32" viewBox="0 0 34 32" fill="none" aria-hidden="true">
      <rect x="9" y="2.5" width="21" height="17" rx="1" stroke="#f2f2f5" strokeWidth="1.8" />
      <path d="M11 17l5-5 3.5 3.5 4-4 5 5.5" stroke="#f2f2f5" strokeWidth="1.5" strokeLinejoin="round" />
      <circle cx="24.5" cy="8" r="1.7" fill="#f2f2f5" />
      <path d="M4 9.5h3M4 9.5v15.5a2 2 0 0 0 2 2h18" stroke="#f2f2f5" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M16 30V15.5m0 0-5 5m5-5 5 5" stroke="#ffce5c" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function HexCube() {
  return (
    <svg width="30" height="30" viewBox="0 0 30 30" fill="none" aria-hidden="true">
      <path d="M15 1.6 26.5 8.3v13.4L15 28.4 3.5 21.7V8.3L15 1.6Z" stroke="#f2f2f5" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M15 8.2 21 11.6v6.8L15 21.8 9 18.4v-6.8l6-3.4Z" fill="#f2f2f5" fillOpacity=".18" stroke="#f2f2f5" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M15 8.2v6.8m0 0 6-3.4m-6 3.4-6-3.4" stroke="#f2f2f5" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
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
        <span className="um-card-icon um-icon-plain um-icon-xml"><StackedImagesArrow /></span>
        <div className="um-card-copy"><strong>Upload XML file</strong></div>
        <button type="button" className="um-lavender small" disabled={busy}
          onClick={() => fileRef.current?.click()}>Upload</button>
      </div>

      <div className="um-card um-package-card">
        <div className="um-package-title">
          <span className="um-card-icon um-icon-plain"><HexCube /></span>
          <div><strong>Import Project Package</strong><small>Paste a link or scan the QR code.</small></div>
        </div>
        <input className="um-input" placeholder="https://alight..." aria-label="Project package link"
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

type ProjectSort = 'date' | 'name' | 'duration';

function projectThumb(name: string) {
  // Deterministic, original artwork derived from the project name.
  let h = 0; for (const c of name) h = (h * 31 + c.charCodeAt(0)) | 0;
  const a = `hsl(${Math.abs(h) % 360} 62% 34%)`;
  const b = `hsl(${Math.abs(h >> 3) % 360} 68% 16%)`;
  return `linear-gradient(145deg,${a},${b})`;
}

function ProjectsBody() {
  const e = useEditor();
  const ui = useUI();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<ProjectSort>('date');
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [, force] = useState(0);
  const projects = e.listProjects().slice();
  const elements = listElements();
  const q = query.trim().toLowerCase();

  const templates = [
    { name: 'Kinetic Intro', sub: 'Title animation · 9:16', w: 1080, h: 1920, a: '#8b5cf6', b: '#22d3ee', glyph: '✦' },
    { name: 'Neon Lower Third', sub: 'Broadcast · 16:9', w: 1920, h: 1080, a: '#00ffa8', b: '#0ea5e9', glyph: '▰' },
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

  const filteredProjects = projects
    .filter(p => !q || p.name.toLowerCase().includes(q))
    .sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name)
      : sort === 'duration' ? b.duration - a.duration : b.updatedAt - a.updatedAt);
  const filteredTemplates = templates.filter(t => !q || t.name.toLowerCase().includes(q));
  const filteredElements = elements.filter(el => !q || el.name.toLowerCase().includes(q));

  const toggle = (id: string) => setSelected(v => v.includes(id) ? v.filter(x => x !== id) : [...v, id]);
  const open = (id: string) => { e.openProject(id); e.setScreen('editor'); ui.setSheet('none'); };

  const dateLabel = (t: number) => new Date(t).toLocaleDateString('en-GB',
    { day: 'numeric', month: 'long', year: 'numeric' });
  const groups = sort === 'date'
    ? filteredProjects.reduce<Record<string, typeof filteredProjects>>((acc, p) => {
        (acc[dateLabel(p.updatedAt)] ||= []).push(p); return acc;
      }, {})
    : { '': filteredProjects };

  const duplicateSelected = () => {
    let n = 0;
    for (const id of selected) {
      const p = loadProject(id);
      if (!p) continue;
      const copy = { ...p, id: uid('proj'), name: `${p.name} (copy)`,
        createdAt: Date.now(), updatedAt: Date.now() };
      saveProject(copy); n++;
    }
    e.notify(`${n} project${n === 1 ? '' : 's'} duplicated`, 'ok');
    setSelectMode(false); setSelected([]); force(v => v + 1);
  };

  const exportSelected = async () => {
    try {
      const files: Record<string, Uint8Array> = {};
      for (const id of selected) {
        const p = loadProject(id);
        if (!p) continue;
        const { xml, warnings } = await projectToXml(p, e.assets);
        const dir = safeFileName(p.name) || 'project';
        files[`${dir}/project.xml`] = strToU8(xml);
        files[`${dir}/README.txt`] = strToU8(
          `EditorsHub project package\n\nName: ${p.name}\nSize: ${p.width}x${p.height}\nDuration: ${p.duration}s\nLayers: ${p.layers.length}\n\nImport project.xml from the launcher.\n${warnings.length ? `\nWarnings:\n${warnings.join('\n')}\n` : ''}`);
      }
      if (!Object.keys(files).length) { e.notify('Nothing to export', 'warn'); return; }
      const blob = new Blob([zipSync(files)], { type: 'application/zip' });
      downloadBlob(blob, `EditorsHub-projects-${selected.length}.zip`);
      e.notify(`Exported ${selected.length} project package(s)`, 'ok');
      setSelectMode(false); setSelected([]);
    } catch (err) { e.notify((err as Error).message || 'Export failed', 'warn'); }
  };

  const fixSelected = () => {
    let missing = 0;
    for (const id of selected) {
      const p = loadProject(id);
      if (!p) continue;
      missing += p.layers.filter(l => l.sourceId && !e.assets[l.sourceId]).length;
    }
    e.notify(missing
      ? `${missing} missing media reference(s) found. Re-import the media to repair them.`
      : 'No missing media references found.', missing ? 'warn' : 'ok');
  };

  const deleteSelected = () => {
    for (const id of selected) e.deleteProject(id);
    e.notify(`${selected.length} project${selected.length === 1 ? '' : 's'} deleted`, 'ok');
    setConfirmDelete(false); setSelectMode(false); setSelected([]); force(v => v + 1);
  };

  const segment = ui.homeSegment;
  const heading = segment === 'projects' ? 'Your projects' : segment === 'templates' ? 'Your templates' : 'Your elements';

  return (
    <div className="um-projects-body">
      {selectMode ? (
        <div className="um-select-top">
          <button type="button" title="Exit selection" onClick={() => { setSelectMode(false); setSelected([]); }}><X size={18} /></button>
          <strong>{selected.length} selected</strong>
          <button type="button" className="um-select-cancel" onClick={() => { setSelectMode(false); setSelected([]); }}>Cancel</button>
        </div>
      ) : (
        <Seg value={ui.homeSegment} onChange={ui.setHomeSegment} options={[
          { id: 'projects', label: 'Projects' },
          { id: 'templates', label: 'Your Templates' },
          { id: 'elements', label: 'Elements' },
        ]} />
      )}

      <div className="um-list-heading">
        <h3>{heading}</h3>
        {segment !== 'elements' && (
          <label className="um-sort" title="Sort projects">
            <select value={sort} onChange={ev => setSort(ev.target.value as ProjectSort)} aria-label="Sort by">
              <option value="date">Sort by Date</option>
              <option value="name">Sort by Name</option>
              <option value="duration">Sort by Duration</option>
            </select>
            <ChevronDown size={14} />
          </label>
        )}
        {segment === 'projects' && !selectMode && (
          <button type="button" className="um-select-toggle" title="Select projects"
            onClick={() => setSelectMode(true)}><MoreHorizontal size={18} /></button>
        )}
      </div>

      <label className="um-search">
        <Search size={16} strokeWidth={1.7} />
        <input value={query} onChange={ev => setQuery(ev.target.value)}
          placeholder={segment === 'elements' ? 'Search elements' : segment === 'templates' ? 'Search templates' : 'Search projects'}
          aria-label="Search" />
        {query && <button type="button" title="Clear search" onClick={() => setQuery('')}>×</button>}
      </label>

      {segment === 'projects' && (
        <div className="um-list">
          {Object.entries(groups).map(([label, items]) => (
            <div key={label}>
              {label && <div className="um-date-group">{label}</div>}
              {items.map(p => (
                <div className={`um-list-row ${selected.includes(p.id) ? 'picked' : ''}`} key={p.id}>
                  {selectMode && (
                    <button type="button" className="um-pick" title={`Select ${p.name}`}
                      aria-pressed={selected.includes(p.id)} onClick={() => toggle(p.id)}>
                      {selected.includes(p.id) ? <Check size={14} /> : null}
                    </button>
                  )}
                  <button type="button" className="um-list-main"
                    onClick={() => selectMode ? toggle(p.id) : open(p.id)}>
                    <span className="um-recent-thumb" style={{ background: projectThumb(p.name) }}>
                      {p.name.charAt(0).toUpperCase()}
                    </span>
                    <span>
                      <strong>{p.name}</strong>
                      <small>{Math.floor(p.duration / 60).toString().padStart(2, '0')}:{(p.duration % 60).toFixed(0).padStart(2, '0')} · {p.width}×{p.height} · {p.layerCount} layers</small>
                    </span>
                  </button>
                  {!selectMode && <span className="um-row-more" title="Project options"><MoreHorizontal size={17} /></span>}
                </div>
              ))}
            </div>
          ))}
          {filteredProjects.length === 0 && <p className="um-empty-note">No projects{q ? ' match that search' : ' yet'}. Tap + to create one.</p>}
        </div>
      )}

      {segment === 'templates' && (
        <div className="um-template-grid">
          {listTemplates().filter(t => !q || t.name.toLowerCase().includes(q)).map(t => (
            <div key={t.id} className="um-template-tile saved">
              <button type="button" className="um-template-open" onClick={() => {
                void (async () => {
                  try {
                    const r = await parseMotionXml(t.xml);
                    applyXmlResult(r, 'project'); ui.setSheet('none');
                    e.notify(`Template “${t.name}” opened`, 'ok');
                  } catch (err) { e.notify((err as Error).message, 'warn'); }
                })();
              }}>
                <span className="um-template-art" style={{ background: projectThumb(t.name) }}>✦</span>
                <strong>{t.name}</strong><small>{t.width}×{t.height} · {t.duration.toFixed(1)}s</small>
              </button>
              <IBtn title={`Delete ${t.name}`} onClick={() => { deleteTemplate(t.id); force(v => v + 1); }}><Trash2 size={15} /></IBtn>
            </div>
          ))}
          {filteredTemplates.map(t => (
            <button type="button" key={t.name} className="um-template-tile" onClick={() => openTemplate(t.name, t.w, t.h)}>
              <span className="um-template-art" style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}</span>
              <strong>{t.name}</strong><small>{t.sub}</small>
            </button>
          ))}
          {filteredTemplates.length === 0 && listTemplates().length === 0 && <p className="um-empty-note">No templates match that search.</p>}
        </div>
      )}

      {segment === 'elements' && (
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
                  <IBtn title={`Delete ${el.name}`} onClick={() => { deleteElement(el.id); force(v => v + 1); }}><Trash2 size={16} /></IBtn>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {selectMode && (
        <div className="um-select-actions">
          <button type="button" onClick={() => setSelected(filteredProjects.map(p => p.id))}><Check size={17} /><span>Select All</span></button>
          <button type="button" onClick={fixSelected}><Wrench size={17} /><span>Fix</span></button>
          <button type="button" disabled={!selected.length} onClick={() => void exportSelected()}><Download size={17} /><span>Export</span></button>
          <button type="button" disabled={!selected.length} onClick={duplicateSelected}><Copy size={17} /><span>Duplicate</span></button>
          <button type="button" disabled={!selected.length} onClick={() => setConfirmDelete(true)}><Trash2 size={17} /><span>Delete</span></button>
        </div>
      )}

      {confirmDelete && (
        <div className="um-confirm">
          <div className="um-confirm-card">
            <Trash2 size={20} />
            <strong>Delete {selected.length} project{selected.length === 1 ? '' : 's'}?</strong>
            <p>{filteredProjects.filter(p => selected.includes(p.id)).map(p => p.name).slice(0, 3).join(', ')}{selected.length > 3 ? '…' : ''}</p>
            <div><button type="button" onClick={() => setConfirmDelete(false)}>Cancel</button>
              <button type="button" className="danger" onClick={deleteSelected}>Delete</button></div>
          </div>
        </div>
      )}
    </div>
  );
}

export function Home() {
  const ui = useUI();
  return (
    <div className="um-screen um-home">
      <Header />
      {ui.homeTab === 'home' ? <HomeBody key="home-tab" /> : <ProjectsBody key="projects-tab" />}
      <Nav />
      {ui.sheet === 'settings' && <><Backdrop onClick={() => ui.setSheet('none')} /><Sheet variant="floating" title="Settings" className="um-settings-card"><SettingsBody /></Sheet></>}
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
      <div className="um-row"><span>Show Off Timeline <i className="um-info">ⓘ</i></span>
        <button type="button" role="switch" aria-checked={prefs.showOffTimeline} title="Show Off Timeline"
          className={`um-toggle ${prefs.showOffTimeline ? 'on' : ''}`}
          onClick={() => setPrefs({ ...prefs, showOffTimeline: !prefs.showOffTimeline })}><span /></button>
      </div>
      <div className="um-row"><span>Remember Add page <i className="um-info">ⓘ</i></span>
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
      <div className="um-settings-brand"><span /><i className="um-gem">✦</i> {BRAND.wordmark} <i className="um-gem">✦</i><span /></div>
      <div className="um-settings-divider" />
      {BRAND.links.map(link => (
        <button type="button" key={link.label} className="um-link-row"
          onClick={() => window.open(link.url, '_blank', 'noopener')}>
          <span className={`um-link-icon ${link.icon}`}>
            {link.icon === 'globe' ? <Globe2 size={19} /> : link.icon === 'send' ? <Send size={19} /> : <Music2 size={19} />}
          </span><b>{link.label}</b><ChevronRight size={17} />
        </button>
      ))}
      <div className="um-settings-divider" />
      <button type="button" className="um-link-row about" onClick={() => { ui.setSheet('none'); ui.setAboutOpen(true); }}>
        <b>About</b><ChevronRight size={17} />
      </button>
    </div>
  );
}
