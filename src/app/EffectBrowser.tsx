import { useEffect, useMemo, useState } from 'react';
import { Search, X, Star, Plus } from 'lucide-react';
import { useEditor } from '../editor/store';
import { EFFECT_MAP, type EffectType } from '../editor/types';
import { useUI } from './context';

/**
 * Full-screen Effect Browser from the reference APK / screenshots.
 *
 * Category names are the structural facts recovered from the APK's decoded
 * string table (apk-analysis/values.json). All artwork here is original CSS
 * gradients and every tile maps to a real, working canvas effect in
 * EFFECT_DEFS — no effect is displayed that the engine cannot actually apply.
 */
interface Category { name: string; hue: number; effects: EffectType[] }

const C = (hue: number, ...effects: EffectType[]): EffectType[] => effects;
const ALL: EffectType[] = Object.keys(EFFECT_MAP) as EffectType[];

const PRIMARY: Category[] = [
  { name: 'Color & Light', hue: 268, effects: C(268, 'colorAdjust', 'tint', 'glow', 'grayscale', 'sepia', 'invert', 'vignette') },
  { name: 'Drawing & Edge', hue: 42, effects: C(42, 'outline', 'pixelate', 'noise', 'sharpen') },
  { name: 'Blur', hue: 205, effects: C(205, 'blur', 'zoomBlur', 'sharpen', 'glow') },
  { name: 'Distortion/Warp', hue: 190, effects: C(190, 'wave', 'chromatic', 'pixelate', 'zoomBlur') },
  { name: 'Procedural', hue: 320, effects: C(320, 'noise', 'pixelate', 'vignette', 'glow') },
  { name: '3D', hue: 292, effects: C(292, 'dropShadow', 'glow', 'outline', 'wave') },
  { name: 'Move/Transform', hue: 160, effects: C(160, 'shake', 'pulse', 'wave', 'zoomBlur') },
  { name: 'Repeat', hue: 300, effects: C(300, 'pulse', 'shake', 'pixelate', 'noise') },
  { name: 'Matte/Mask/Key', hue: 200, effects: C(200, 'chromaKey', 'vignette', 'outline', 'invert') },
  { name: 'Opacity/Visibility', hue: 215, effects: C(215, 'vignette', 'invert', 'grayscale', 'tint') },
  { name: 'Text', hue: 20, effects: C(20, 'outline', 'dropShadow', 'glow', 'colorAdjust') },
  { name: 'Other', hue: 240, effects: C(240, ...ALL.slice(0, 8), 'fadeInOut', 'crystal', 'hotColor') },
];

const SECONDARY: Category[] = [
  { name: 'Nature & Weather', hue: 172, effects: C(172, 'noise', 'vignette', 'glow', 'wave') },
  { name: 'Space & Cosmic', hue: 202, effects: C(202, 'glow', 'invert', 'chromatic', 'noise') },
  { name: 'Flares, Rays & Energy', hue: 42, effects: C(42, 'glow', 'zoomBlur', 'colorAdjust', 'tint') },
  { name: 'Glow & Gradients', hue: 292, effects: C(292, 'glow', 'tint', 'vignette', 'colorAdjust') },
  { name: 'Grids & Tunnels', hue: 310, effects: C(310, 'pixelate', 'zoomBlur', 'wave', 'outline') },
  { name: 'Backgrounds & Textures', hue: 268, effects: C(268, 'noise', 'pixelate', 'vignette', 'grayscale') },
  { name: 'Glitch & Digital Damage', hue: 338, effects: C(338, 'chromatic', 'pixelate', 'noise', 'wave') },
  { name: 'Retro TV, VHS & Film', hue: 18, effects: C(18, 'noise', 'chromatic', 'sepia', 'vignette') },
  { name: 'Warp & Lens', hue: 186, effects: C(186, 'wave', 'zoomBlur', 'chromatic', 'pixelate') },
  { name: 'Water, Glass & Refraction', hue: 196, effects: C(196, 'wave', 'chromatic', 'glow', 'blur') },
  { name: '3D Layer & Depth', hue: 306, effects: C(306, 'dropShadow', 'outline', 'glow', 'zoomBlur') },
  { name: '3D Shapes & Solids', hue: 268, effects: C(268, 'dropShadow', 'outline', 'pixelate', 'glow') },
  { name: '3D Props & Objects', hue: 220, effects: C(220, 'dropShadow', 'sharpen', 'outline', 'tint') },
  { name: 'Blur, Focus & Sharpen', hue: 210, effects: C(210, 'blur', 'sharpen', 'zoomBlur', 'glow') },
  { name: 'Color Grading', hue: 350, effects: C(350, 'colorAdjust', 'tint', 'sepia', 'grayscale') },
  { name: 'Color Looks & Tone Maps', hue: 24, effects: C(24, 'sepia', 'tint', 'colorAdjust', 'invert') },
  { name: 'Ink, Paint & Halftone', hue: 286, effects: C(286, 'outline', 'pixelate', 'grayscale', 'noise') },
  { name: 'Strokes, Bevels & Shadows', hue: 44, effects: C(44, 'outline', 'dropShadow', 'glow', 'sharpen') },
  { name: 'Wipes, Keys & Shatter', hue: 8, effects: C(8, 'chromaKey', 'wave', 'pixelate', 'noise') },
  { name: 'Shake & Transform', hue: 158, effects: C(158, 'shake', 'pulse', 'wave', 'zoomBlur') },
];

/** v3.6.0 master library: distribute the 23 additional real effects across the
 *  32 APK categories so every category exposes a useful, working set. */
const EXTRA: Record<string, EffectType[]> = {
  'Color & Light': ['posterize', 'threshold', 'solarize', 'duotone', 'gradientMap', 'levels', 'colorBalance'],
  'Drawing & Edge': ['emboss', 'edgeDetect', 'halftone', 'scanlines', 'twirl'],
  'Blur': ['motionBlur', 'directionalBlur', 'ripple'],
  'Distortion/Warp': ['ripple', 'bulge', 'twirl', 'mirror', 'kaleidoscope', 'glitchBlocks'],
  'Procedural': ['starfield', 'bokeh', 'rain', 'lensFlare', 'halftone'],
  '3D': ['emboss', 'lensFlare', 'bulge', 'mirror'],
  'Move/Transform': ['motionBlur', 'mirror', 'kaleidoscope', 'twirl'],
  'Repeat': ['kaleidoscope', 'halftone', 'scanlines', 'glitchBlocks'],
  'Matte/Mask/Key': ['threshold', 'posterize', 'halftone'],
  'Opacity/Visibility': ['levels', 'solarize', 'threshold'],
  'Text': ['emboss', 'edgeDetect', 'scanlines'],
  'Other': ['posterize', 'emboss', 'ripple', 'bulge', 'starfield', 'bokeh'],
  'Nature & Weather': ['rain', 'starfield', 'bokeh', 'ripple'],
  'Space & Cosmic': ['starfield', 'lensFlare', 'bokeh', 'glow'],
  'Flares, Rays & Energy': ['lensFlare', 'bokeh', 'gradientMap', 'solarize'],
  'Glow & Gradients': ['lensFlare', 'gradientMap', 'duotone', 'bokeh'],
  'Grids & Tunnels': ['halftone', 'kaleidoscope', 'twirl', 'scanlines'],
  'Backgrounds & Textures': ['starfield', 'bokeh', 'rain', 'halftone'],
  'Glitch & Digital Damage': ['glitchBlocks', 'scanlines', 'posterize', 'threshold'],
  'Retro TV, VHS & Film': ['scanlines', 'halftone', 'posterize', 'colorBalance'],
  'Warp & Lens': ['bulge', 'twirl', 'ripple', 'motionBlur'],
  'Water, Glass & Refraction': ['ripple', 'bulge', 'bokeh', 'directionalBlur'],
  '3D Layer & Depth': ['emboss', 'bulge', 'lensFlare', 'mirror'],
  '3D Shapes & Solids': ['emboss', 'edgeDetect', 'kaleidoscope'],
  '3D Props & Objects': ['emboss', 'lensFlare', 'levels'],
  'Blur, Focus & Sharpen': ['motionBlur', 'directionalBlur', 'ripple', 'halftone'],
  'Color Grading': ['levels', 'colorBalance', 'duotone', 'gradientMap'],
  'Color Looks & Tone Maps': ['gradientMap', 'duotone', 'posterize', 'solarize'],
  'Ink, Paint & Halftone': ['halftone', 'edgeDetect', 'threshold', 'emboss'],
  'Strokes, Bevels & Shadows': ['emboss', 'edgeDetect', 'levels'],
  'Wipes, Keys & Shatter': ['glitchBlocks', 'threshold', 'kaleidoscope'],
  'Shake & Transform': ['motionBlur', 'twirl', 'mirror', 'glitchBlocks'],
};
for (const cat of [...PRIMARY, ...SECONDARY]) {
  const extra = EXTRA[cat.name] ?? [];
  for (const t of extra) if (!cat.effects.includes(t)) cat.effects.push(t);
}

const QUICK: { name: string; type: EffectType; hue: number }[] = [
  { name: 'Chroma Key', type: 'chromaKey', hue: 152 },
  { name: 'Directional Blur', type: 'directionalBlur', hue: 210 },
  { name: 'Fade In/Out', type: 'fadeInOut', hue: 40 },
  { name: 'Crystal', type: 'crystal', hue: 292 },
  { name: 'Hot Color', type: 'hotColor', hue: 330 },
];

const FAV_KEY = 'editorshub.ultra.effectFavorites.v1';
function readFavorites(): string[] {
  try { return JSON.parse(localStorage.getItem(FAV_KEY) || '[]') as string[]; } catch { return []; }
}
function art(hue: number, seed = 0): string {
  const h2 = (hue + 55 + seed * 13) % 360;
  /* Dark, lightly textured backdrops: the reference category tiles are muted
     photographic plates with white centred labels, not saturated gradients. */
  return `radial-gradient(ellipse at 28% 30%, hsl(${hue} 34% 30% / .78), transparent 58%), ` +
    `radial-gradient(ellipse at 76% 74%, hsl(${h2} 30% 21% / .82), transparent 62%), ` +
    `repeating-linear-gradient(${118 + seed * 23}deg, rgba(255,255,255,.035) 0 1px, transparent 1px 6px), ` +
    `linear-gradient(145deg, hsl(${hue} 18% 12%), hsl(${h2} 16% 8%))`;
}

/** Original hand-drawn vector tiles for the five quick picks. These are SVG
 *  approximations of the shapes in the reference JPGs — no bitmap is copied. */
/** Original abstract artwork behind the effect guide — no photograph is used. */
function guideArt(hue: number): string {
  const h2 = (hue + 118) % 360;
  return `radial-gradient(ellipse at 30% 40%, hsl(${hue} 86% 60% / .92), transparent 46%), ` +
    `radial-gradient(ellipse at 74% 26%, hsl(${h2} 70% 44% / .82), transparent 54%), ` +
    `radial-gradient(ellipse at 54% 84%, hsl(${(hue + 28) % 360} 88% 56% / .85), transparent 50%), ` +
    `linear-gradient(160deg, #10222e, #1b3141 45%, #0a1119)`;
}

function QuickArt({ name }: { name: string }) {
  const common = { className: 'um-fx-art', viewBox: '0 0 100 100', 'aria-hidden': 'true' } as const;
  if (name === 'Chroma Key') return (
    <svg {...common}>
      <defs><pattern id="qck" width="10" height="10" patternUnits="userSpaceOnUse">
        <rect width="10" height="10" fill="#e7e7ea" />
        <rect width="5" height="5" fill="#b6b6be" /><rect x="5" y="5" width="5" height="5" fill="#b6b6be" />
      </pattern></defs>
      <rect width="100" height="100" fill="url(#qck)" /><rect x="50" width="50" height="100" fill="#00a862" />
      <circle cx="57" cy="27" r="7" fill="#101014" />
      <path d="M49 39c7-5 15-4 19 2l6 16-8 3-3-11-2 14 8 20-9 4-8-20-9 17-8-5 11-22-4-12z" fill="#101014" />
    </svg>
  );
  if (name === 'Directional Blur') return (
    <svg {...common}>
      <defs><radialGradient id="qdb">
        <stop offset="0" stopColor="#1f63ff" /><stop offset="42%" stopColor="#4284ff" />
        <stop offset="72%" stopColor="#9ec2ff" stopOpacity=".8" /><stop offset="100%" stopColor="#fff" stopOpacity="0" />
      </radialGradient></defs>
      <rect width="100" height="100" fill="#f4f5f8" /><circle cx="50" cy="50" r="31" fill="url(#qdb)" />
    </svg>
  );
  if (name === 'Fade In/Out') return (
    <svg {...common}>
      <defs><linearGradient id="qfo" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#d9dfe3" /><stop offset="48%" stopColor="#eef0ec" />
        <stop offset="52%" stopColor="#7e8d88" /><stop offset="100%" stopColor="#cfd6d2" />
      </linearGradient></defs>
      <rect width="100" height="100" fill="url(#qfo)" />
      <path d="M0 52h100M12 58h30M44 63h42M20 69h52" stroke="#6f7c77" strokeWidth="1.4" opacity=".55" />
    </svg>
  );
  if (name === 'Crystal') return (
    <svg {...common}>
      <rect width="100" height="100" fill="#19191d" />
      <path d="M50 18 82 76H50Z" fill="#8d8d96" /><path d="M50 18 18 76h32Z" fill="#d7d7de" />
      <path d="M50 18 50 76 18 76Z" fill="#b1b1bb" opacity=".55" />
    </svg>
  );
  return (
    <svg {...common}>
      <rect width="100" height="100" fill="#101014" />
      {['#e8453c','#f59b21','#f4dd3f','#43c97a','#2f8bff','#9a52e0'].map((c, i) =>
        <rect key={c} x={8 + i * 14} y="12" width="11" height="76" rx="2" fill={c} />)}
    </svg>
  );
}

export function EffectBrowser() {
  const ui = useUI();
  const e = useEditor();
  const [category, setCategory] = useState<Category | null>(null);
  const [query, setQuery] = useState('');
  const [picked, setPicked] = useState<EffectType | null>(null);
  const [favorites, setFavorites] = useState<string[]>(readFavorites);
  const [showSearch, setShowSearch] = useState(false);

  useEffect(() => {
    try { localStorage.setItem(FAV_KEY, JSON.stringify(favorites)); } catch { /* storage full */ }
  }, [favorites]);

  const close = () => ui.setEffectBrowser(false);
  const q = query.trim().toLowerCase();
  const cats = useMemo(() => [...PRIMARY, ...SECONDARY].filter(c =>
    !q || c.name.toLowerCase().includes(q) || c.effects.some(t => EFFECT_MAP[t]?.label.toLowerCase().includes(q))
  ), [q]);
  const effects = useMemo(() => {
    if (!category) return [];
    return category.effects.filter(t => !q || EFFECT_MAP[t]?.label.toLowerCase().includes(q));
  }, [category, q]);

  const def = picked ? EFFECT_MAP[picked] : null;
  const toggleFav = () => {
    if (!picked) return;
    setFavorites(v => v.includes(picked) ? v.filter(x => x !== picked) : [...v, picked]);
  };
  const add = () => {
    if (!picked) return;
    const layer = e.selectedLayer;
    if (!layer) { e.notify('Select a layer before adding an effect', 'warn'); return; }
    e.addEffect(picked);
    ui.setEffectBrowser(false);
    ui.setSheet('property');
    ui.openProperty('effects');
    e.notify(`${EFFECT_MAP[picked].label} added`, 'ok');
  };

  return (
    <div className="um-fx-browser" aria-label="Effect Browser">
      <header className="um-fx-head">
        <button type="button" title="Close effect browser" aria-label="Close" onClick={close}><X size={22} /></button>
        <strong>{category ? category.name : 'Effect Browser'}</strong>
        <button type="button" title="Search effects" aria-label="Search" onClick={() => setShowSearch(v => !v)}><Search size={20} /></button>
      </header>
      {showSearch && (
        <div className="um-fx-search">
          <Search size={15} />
          <input autoFocus value={query} onChange={ev => setQuery(ev.target.value)}
            placeholder="Search effects" aria-label="Search effects" />
          {query && <button type="button" title="Clear" onClick={() => setQuery('')}>×</button>}
        </div>
      )}

      <div className="um-fx-scroll">
        {!category && (
          <>
            <div className="um-fx-quick">
              {QUICK.slice(0, 3).map((item, i) => (
                <button type="button" key={item.name} className="um-fx-quick-big"
                  onClick={() => setPicked(item.type)}>
                  <QuickArt name={item.name} />
                  <small>{item.name}</small>
                </button>
              ))}
            </div>
            <div className="um-fx-quick-small">
              {QUICK.slice(3).map((item, i) => (
                <button type="button" key={item.name} onClick={() => setPicked(item.type)}>
                  <QuickArt name={item.name} /><small>{item.name}</small>
                </button>
              ))}
            </div>
            <div className="um-fx-dot" aria-hidden="true"><i /></div>
            <div className="um-fx-cats">
              {cats.map((c, i) => (
                <button type="button" key={c.name} className="um-fx-cat"
                  style={{ background: art(c.hue, i % 7) }} onClick={() => { setCategory(c); setPicked(null); setQuery(''); }}>
                  <strong>{c.name}</strong>
                </button>
              ))}
              {cats.length === 0 && <p className="um-hint center">No categories match that search.</p>}
            </div>
          </>
        )}

        {category && (
          <>
            <button type="button" className="um-fx-back" onClick={() => { setCategory(null); setPicked(null); }}>← All categories</button>
            <div className="um-fx-grid">
              {effects.map((t, i) => (
                <button type="button" key={t} className={`um-fx-cell ${picked === t ? 'on' : ''}`}
                  onClick={() => setPicked(t)}>
                  <span style={{ background: art(category.hue, i) }} />
                  <small>{EFFECT_MAP[t]?.label ?? t}</small>
                </button>
              ))}
              {effects.length === 0 && <p className="um-hint center">No effects match that search.</p>}
            </div>
            <p className="um-fx-honest">{effects.length} real effect{effects.length === 1 ? '' : 's'} in this category · {ALL.length} in the library. Every tile applies a working canvas effect.</p>
          </>
        )}
      </div>

      {def && picked && (
        <div className="um-fx-guide" style={{ background: guideArt(QUICK.find(x => x.type === picked)?.hue ?? category?.hue ?? 205) }}>
          <div className="um-fx-guide-top">
            <button type="button" title="Close guide" onClick={() => setPicked(null)}><X size={19} /></button>
            <strong>Guide</strong>
          </div>
          <div className="um-fx-guide-body">
            <h2>{def.label}</h2>
            <p>{def.description} Adjust its {def.params.length} parameter{def.params.length === 1 ? '' : 's'} in the Effects panel after adding it.</p>
            <div className="um-fx-tags">
              {def.group && <span>{def.group}</span>}
              {def.params.slice(0, 3).map(p => <span key={p.key}>{p.label}</span>)}
            </div>
          </div>
          <div className="um-fx-guide-bar">
            <button type="button" className="um-fx-add" onClick={add}><Plus size={19} /> Standard Settings</button>
            <button type="button" className={`um-fx-star ${favorites.includes(picked) ? 'on' : ''}`}
              title={favorites.includes(picked) ? 'Remove favourite' : 'Add favourite'} onClick={toggleFav}>
              <Star size={21} fill={favorites.includes(picked) ? 'currentColor' : 'none'} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
