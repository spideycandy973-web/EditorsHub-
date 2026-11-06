import { useState } from 'react';
import { X } from 'lucide-react';
import { useUI } from './context';
import {
  BASE_BRAND, BRAND_CHANGED, REFERENCE_LOOK, clearBrandOverride, getBrand, loadBrandOverride,
  saveBrandOverride, applyBrand, type BrandOverride,
} from './brand';

const FIELDS: { key: keyof BrandOverride; label: string; placeholder: string }[] = [
  { key: 'wordmark', label: 'Wordmark', placeholder: BASE_BRAND.wordmark },
  { key: 'appName', label: 'App name', placeholder: BASE_BRAND.appName },
  { key: 'domain', label: 'Domain / subtitle', placeholder: BASE_BRAND.domain },
  { key: 'website', label: 'Website URL', placeholder: BASE_BRAND.website },
  { key: 'legal', label: 'About text', placeholder: BASE_BRAND.legal },
];
const COLORS: { key: keyof BrandOverride; label: string }[] = [
  { key: 'accent', label: 'Accent (buttons, FAB, readouts)' },
  { key: 'lavender', label: 'Primary button' },
  { key: 'home', label: 'Screen background' },
];

/** A real, persisted brand editor. Every field changes the running app. */
export function BrandSheet() {
  const ui = useUI();
  const [draft, setDraft] = useState<BrandOverride>(loadBrandOverride);
  const current = getBrand();

  const commit = (patch: BrandOverride) => {
    const next = { ...draft, ...patch };
    setDraft(next);
    saveBrandOverride(next);
    applyBrand();
    window.dispatchEvent(new Event(BRAND_CHANGED));
  };
  const reset = () => {
    clearBrandOverride();
    setDraft({});
    applyBrand();
    window.dispatchEvent(new Event(BRAND_CHANGED));
  };

  return (
    <div className="um-brand-sheet" aria-label="Customise app">
      <header className="um-fx-head">
        <button type="button" title="Close customise app" onClick={() => ui.setSheet('none')}><X size={22} /></button>
        <strong>Customise app</strong>
        <button type="button" className="um-flow-next" onClick={reset}>Reset</button>
      </header>
      <div className="um-brand-body">
        <div className="um-brand-quick">
          <button type="button" onClick={() => commit(REFERENCE_LOOK)}>Use reference look</button>
          <button type="button" onClick={() => commit({ wordmark: 'EDITORS HUB', domain: 'editorshub.app' })}>EditorsHub identity</button>
          <button type="button" onClick={() => { void (window as unknown as { __clearUiCache: () => Promise<void> }).__clearUiCache?.(); }}>Clear cached UI</button>
        </div>
        <div className="um-brand-preview" style={{ background: current.palette.home }}>
          <strong style={{ color: current.palette.text }}>{current.wordmark}</strong>
          <span style={{ color: current.palette.accent }}>{current.domain}</span>
          <i style={{ background: current.palette.lavender }} />
        </div>
        <p className="um-hint">Changes apply immediately and persist on this device. Set any name, wordmark, domain and colours you like.</p>
        {FIELDS.map(f => (
          <label className="um-brand-field" key={f.key}>
            <span>{f.label}</span>
            <input value={(draft[f.key] as string) ?? ''} placeholder={f.placeholder}
              onChange={ev => commit({ [f.key]: ev.target.value })} />
          </label>
        ))}
        {COLORS.map(c => (
          <label className="um-brand-field color" key={c.key}>
            <span>{c.label}</span>
            <input type="color" value={(draft[c.key] as string) ?? (current.palette as unknown as Record<string, string>)[
              c.key === 'accent' ? 'accent' : c.key === 'lavender' ? 'lavender' : 'home']}
              onChange={ev => commit({ [c.key]: ev.target.value })} />
          </label>
        ))}
        <div className="um-brand-links">
          <strong>Settings links</strong>
          <p className="um-hint">Shown in the Settings card. Editable in <code>src/app/brand.ts</code>.</p>
          {current.links.map(l => <div key={l.label}><b>{l.label}</b><span>{l.url}</span></div>)}
        </div>
      </div>
    </div>
  );
}
