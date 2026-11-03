import React, { useState } from 'react';
import {
  ArrowRight, BookOpen, ChevronRight, Clock, FileInput, Film, FolderOpen, Grid2X2,
  HelpCircle, Home, LayoutGrid, Layers3, Maximize2, Menu, MoreHorizontal, Music2,
  Palette, Plus, Settings, Shapes, Sparkles, Trash2, Type, Upload, X,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { createDemoProject } from '../editor/defaults';
import type { ProjectMeta } from '../editor/storage';

const TEMPLATES = [
  { name: 'For You', sub: 'Trending', w: 1080, h: 1920, a: '#f472b6', b: '#8b5cf6', glyph: '✦' },
  { name: 'Romantic Scene', sub: 'Cinematic', w: 1920, h: 1080, a: '#fb7185', b: '#f59e0b', glyph: '♥' },
  { name: 'Intro', sub: 'Titles', w: 1080, h: 1080, a: '#8b5cf6', b: '#22d3ee', glyph: '▶' },
  { name: 'Glow', sub: 'Effects', w: 1080, h: 1920, a: '#22d3ee', b: '#6366f1', glyph: '✺' },
  { name: 'Sports', sub: 'Dynamic', w: 1920, h: 1080, a: '#10e0a0', b: '#0ea5e9', glyph: '⚡' },
  { name: 'Slideshow', sub: 'Photos', w: 1080, h: 1350, a: '#f59e0b', b: '#ef4444', glyph: '▣' },
];

function openTemplateWith(
  e: ReturnType<typeof useEditor>, name: string, w: number, h: number,
): void {
  const p = createDemoProject();
  p.name = name;
  p.width = w; p.height = h;
  e.replaceProject(p);
  e.setScreen('editor');
  e.setPanel('add');
  e.notify(`Template “${name}” opened`, 'ok');
}

function ProjectCard({ p, onOpen }: { p: ProjectMeta; onOpen: () => void }) {
  const e = useEditor();
  const [menu, setMenu] = useState(false);
  const ratio = `${p.width}×${p.height}`;
  return (
    <div className="am-project-card">
      <button type="button" className="am-project-thumb" onClick={onOpen} title={`Open ${p.name}`}
        style={{ background: 'linear-gradient(135deg,#2b3350,#151a2b)' }}>
        <span className="am-thumb-glyph">{p.name.charAt(0).toUpperCase()}</span>
        <span className="am-thumb-time"><Clock size={9} /> {p.duration.toFixed(1)}s</span>
      </button>
      <div className="am-project-info">
        <strong title={p.name}>{p.name}</strong>
        <div className="am-project-meta">
          <span><Maximize2 size={11} /> {p.width % p.height === 0 ? `${p.width / p.height}:1` : ratio}</span>
          <span><Clock size={11} /> {p.duration.toFixed(1)}s</span>
          <span><Grid2X2 size={11} /> {ratio}</span>
          <span><Layers3 size={11} /> {p.layerCount} layers</span>
        </div>
        <small>{new Date(p.updatedAt).toLocaleDateString()} · {p.id === e.project.id ? 'Current project' : 'Saved locally'}</small>
      </div>
      <div className="am-project-menu">
        <button type="button" title="Project options" onClick={() => setMenu(!menu)}><MoreHorizontal size={18} /></button>
        {menu && (
          <div className="am-menu-pop">
            <button type="button" onClick={() => { setMenu(false); onOpen(); }}><FolderOpen size={14} /> Open</button>
            <button type="button" className="danger" onClick={() => { setMenu(false); e.deleteProject(p.id); }}><Trash2 size={14} /> Delete</button>
          </div>
        )}
      </div>
    </div>
  );
}

export function HomeScreen() {
  const e = useEditor();
  const [drawer, setDrawer] = useState(false);
  const [filter, setFilter] = useState<'projects' | 'templates' | 'elements'>('projects');
  const list = e.listProjects().slice().sort((a, b) => b.updatedAt - a.updatedAt);

  const open = (id: string) => { e.openProject(id); e.setScreen('editor'); };
  const create = () => e.setModal('newProject');

  return (
    <div className="am-home">
      <header className="am-home-top">
        <button type="button" className="am-icon" title="Menu" onClick={() => setDrawer(true)}><Menu size={22} /></button>
        <div className="am-home-brand">
          <span className="am-home-mark">EH</span>
          <span className="am-home-word">EditorsHub</span>
        </div>
        <button type="button" className="am-avatar" title="Settings" onClick={() => e.setModal('settings')}><Settings size={18} /></button>
      </header>

      <main className="am-home-body">
        {e.homeTab === 'home' && (
          <>
            <section className="am-section">
              <div className="am-section-head"><h2>Explore Templates</h2><button type="button" onClick={() => e.setHomeTab('templates')}>View all <ChevronRight size={14} /></button></div>
              <div className="am-template-carousel">
                {TEMPLATES.map((t, i) => (
                    <button type="button" key={i} className={`am-template-circle ${i === 0 ? 'first' : ''}`} onClick={() => openTemplateWith(e, t.name, t.w, t.h)} title={`Use ${t.name} template`}>
                      <span className="am-circle-art" style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}</span>
                      <small>{t.name}</small>
                    </button>
                ))}
              </div>
            </section>

            <section className="am-section">
              <div className="am-section-head"><h2>Latest projects</h2><button type="button" onClick={() => e.setHomeTab('projects')}>View all <ChevronRight size={14} /></button></div>
              <button type="button" className="am-create-card" onClick={create}>
                <span className="am-create-plus"><Plus size={22} /></span>
                <strong>Create New Project</strong>
              </button>
              {list.slice(0, 3).map(p => <ProjectCard key={p.id} p={p} onOpen={() => open(p.id)} />)}
              {list.length === 0 && <p className="am-empty-note">No projects yet. Tap the + button to start.</p>}
            </section>

            <section className="am-section">
              <div className="am-section-head"><h2>Get started</h2></div>
              <div className="am-get-started">
                <button type="button" className="am-start-card blue" onClick={create}>
                  <Sparkles size={22} />
                  <strong>Create a Project</strong>
                  <small>Start with a blank composition, set the ratio, resolution and frame rate.</small>
                </button>
                <button type="button" className="am-start-card yellow" onClick={() => e.setHomeTab('templates')}>
                  <LayoutGrid size={22} />
                  <strong>Use a Template</strong>
                  <small>Open an editable sample project with layers, keyframes and effects.</small>
                </button>
              </div>
            </section>

            <section className="am-section">
              <div className="am-welcome-card">
                <div className="am-welcome-brand"><span className="am-welcome-mark">EH</span> EditorsHub</div>
                <h3>Welcome to EditorsHub</h3>
                <p>A real motion-graphics editor: layers, keyframes, effects, blending, masks,
                  XML projects and frame-accurate export — everything runs on your device.</p>
                <button type="button" className="am-pill" onClick={() => e.setModal('help')}>GETTING STARTED – PART 1</button>
                <button type="button" className="am-pill" onClick={() => e.setHomeTab('templates')}>GETTING STARTED – PART 2</button>
                <button type="button" className="am-pill" onClick={() => e.setModal('xml')}>IMPORT AN XML PROJECT</button>
              </div>
              <button type="button" className="am-tutorial-card" onClick={() => e.setModal('help')}>
                <span>ECHO KEYFRAMES + UNSHARP MASK</span>
                <small>Open the built-in editing guide →</small>
              </button>
            </section>
          </>
        )}

        {e.homeTab === 'projects' && (
          <>
            <div className="am-upload-card">
              <span className="am-new-badge">NEW</span>
              <p>Upload an XML file from your device</p>
              <button type="button" onClick={() => e.setModal('xml')}>Upload</button>
            </div>
            <div className="am-filter-row">
              {([['projects', 'Projects'], ['templates', 'Your Templates'], ['elements', 'Elements']] as const).map(([id, label]) => (
                <button type="button" key={id} className={filter === id ? 'on' : ''} onClick={() => setFilter(id)}>{label}</button>
              ))}
            </div>
            {filter === 'projects' && (
              <>
                <div className="am-section-head"><h2>Your projects</h2><span className="am-count">{list.length}</span></div>
                {list.map(p => <ProjectCard key={p.id} p={p} onOpen={() => open(p.id)} />)}
                {list.length === 0 && <p className="am-empty-note">No saved projects yet.</p>}
              </>
            )}
            {filter === 'templates' && (
              <>
                <div className="am-section-head"><h2>Your Templates</h2></div>
                <div className="am-template-grid">
                  {TEMPLATES.map((t, i) => (
                      <button type="button" key={i} className="am-template-tile" onClick={() => openTemplateWith(e, t.name, t.w, t.h)}>
                        <span style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}</span>
                        <strong>{t.name}</strong><small>{t.sub} · {t.w}×{t.h}</small>
                      </button>
                  ))}
                </div>
              </>
            )}
            {filter === 'elements' && (
              <>
                <div className="am-section-head"><h2>Elements</h2></div>
                <div className="am-element-grid">
                  {[
                    { icon: <Shapes size={20} />, label: 'Shapes', action: () => { e.newProject(1080, 1920, true); e.setScreen('editor'); e.setAddCategory('shape'); e.setPanel('add'); } },
                    { icon: <Type size={20} />, label: 'Text', action: () => { e.newProject(1080, 1920, true); e.setScreen('editor'); e.setAddCategory('text'); e.setPanel('add'); } },
                    { icon: <Music2 size={20} />, label: 'Audio', action: () => { e.newProject(1080, 1920, true); e.setScreen('editor'); e.setAddCategory('audio'); e.setPanel('add'); } },
                    { icon: <Film size={20} />, label: 'Media', action: () => { e.newProject(1080, 1920, true); e.setScreen('editor'); e.setAddCategory('media'); e.setPanel('add'); } },
                  ].map((x, i) => (
                    <button type="button" key={i} className="am-element-tile" onClick={x.action}>{x.icon}<strong>{x.label}</strong></button>
                  ))}
                </div>
              </>
            )}
          </>
        )}

        {e.homeTab === 'templates' && (
          <>
            <div className="am-section-head"><h2>Templates</h2><span className="am-count">{TEMPLATES.length}</span></div>
            <p className="am-subnote">Each template opens a real, fully editable project with layers, keyframes and effects.</p>
            <div className="am-template-grid large">
              {TEMPLATES.map((t, i) => (
                  <button type="button" key={i} className="am-template-tile" onClick={() => openTemplateWith(e, t.name, t.w, t.h)}>
                    <span className="am-template-art" style={{ background: `linear-gradient(145deg,${t.a},${t.b})` }}>{t.glyph}<em>{t.w}×{t.h}</em></span>
                    <strong>{t.name}</strong><small>{t.sub}</small>
                  </button>
              ))}
            </div>
          </>
        )}

        {e.homeTab === 'tutorials' && (
          <>
            <div className="am-section-head"><h2>Tutorials</h2></div>
            <div className="am-tutorial-list">
              {[
                { icon: <Sparkles size={20} />, title: 'Quick start & shortcuts', sub: 'Learn the timeline, panels and keyboard shortcuts.', action: () => e.setModal('help') },
                { icon: <FileInput size={20} />, title: 'Import an XML preset', sub: 'Bring in Alight-Motion-style XML projects and presets.', action: () => e.setModal('xml') },
                { icon: <Shapes size={20} />, title: 'Add shapes, text & media', sub: 'Open the editor with the Add panel ready.', action: () => { e.newProject(1080, 1920, true); e.setScreen('editor'); e.setPanel('add'); } },
                { icon: <Palette size={20} />, title: 'Effects & keyframes', sub: 'Animate properties and build an effect stack.', action: () => { e.newProject(1080, 1920, false); e.setScreen('editor'); e.setPanel('effects'); } },
              ].map((t, i) => (
                <button type="button" key={i} className="am-tutorial-row" onClick={t.action}>
                  <span className="am-tutorial-icon">{t.icon}</span>
                  <span><strong>{t.title}</strong><small>{t.sub}</small></span>
                  <ArrowRight size={18} />
                </button>
              ))}
            </div>
          </>
        )}
      </main>

      <nav className="am-home-nav">
        <button type="button" className={e.homeTab === 'home' ? 'on' : ''} onClick={() => e.setHomeTab('home')}><Home size={20} /><span>Home</span></button>
        <button type="button" className={e.homeTab === 'tutorials' ? 'on' : ''} onClick={() => e.setHomeTab('tutorials')}><BookOpen size={20} /><span>Tutorials</span></button>
        <button type="button" className="am-nav-plus" onClick={create} title="Create new project"><Plus size={28} /></button>
        <button type="button" className={e.homeTab === 'projects' ? 'on' : ''} onClick={() => e.setHomeTab('projects')}><FolderOpen size={20} /><span>Projects</span></button>
        <button type="button" className={e.homeTab === 'templates' ? 'on' : ''} onClick={() => e.setHomeTab('templates')}><LayoutGrid size={20} /><span>Templates</span><i>NEW</i></button>
      </nav>

      {drawer && (
        <div className="am-drawer-backdrop" onClick={() => setDrawer(false)}>
          <aside className="am-drawer" onClick={ev => ev.stopPropagation()}>
            <div className="am-drawer-head">
              <div className="am-home-brand"><span className="am-home-mark">EH</span><span className="am-home-word">EditorsHub</span></div>
              <button type="button" onClick={() => setDrawer(false)}><X size={20} /></button>
            </div>
            <button type="button" onClick={() => { setDrawer(false); e.setHomeTab('home'); }}><Home size={18} /> Home</button>
            <button type="button" onClick={() => { setDrawer(false); create(); }}><Plus size={18} /> New project</button>
            <button type="button" onClick={() => { setDrawer(false); e.setModal('projects'); }}><FolderOpen size={18} /> Projects</button>
            <button type="button" onClick={() => { setDrawer(false); e.setModal('xml'); }}><FileInput size={18} /> Import XML</button>
            <button type="button" onClick={() => { setDrawer(false); e.setModal('export'); }}><Upload size={18} /> Export current project</button>
            <button type="button" onClick={() => { setDrawer(false); e.setModal('settings'); }}><Settings size={18} /> Settings</button>
            <button type="button" onClick={() => { setDrawer(false); e.setModal('help'); }}><HelpCircle size={18} /> Help</button>
          </aside>
        </div>
      )}
    </div>
  );
}
