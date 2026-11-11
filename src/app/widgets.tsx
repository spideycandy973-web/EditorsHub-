import React from 'react';
import { ChevronDown, X } from 'lucide-react';
import type { ReactNode } from 'react';

export function IBtn({ children, title, onClick, active, disabled, className = '' }: {
  children: ReactNode; title: string; onClick?: () => void;
  active?: boolean; disabled?: boolean; className?: string;
}) {
  return (
    <button type="button" title={title} aria-label={title} disabled={disabled}
      className={`um-iconbtn ${active ? 'on' : ''} ${className}`} onClick={onClick}>
      {children}
    </button>
  );
}

export function Backdrop({ onClick, dim = true }: { onClick: () => void; dim?: boolean }) {
  return <div className={`um-backdrop ${dim ? '' : 'clear'}`} onClick={onClick} />;
}

export function Sheet({ children, variant = 'floating', title, onBack, onClose, className = '' }: {
  children: ReactNode; variant?: 'floating' | 'dock' | 'flat'; title?: string;
  onBack?: () => void; onClose?: () => void; className?: string;
}) {
  return (
    <section className={`um-sheet um-sheet-${variant} ${className}`}>
      {(title || onBack) && (
        <header className="um-sheet-head">
          {onBack && <IBtn title="Back" onClick={onBack}><span className="um-arrow">←</span></IBtn>}
          {title && <h2>{title}</h2>}
          {onClose && <IBtn title="Close" onClick={onClose}><X size={18} /></IBtn>}
        </header>
      )}
      <div className="um-sheet-content">{children}</div>
    </section>
  );
}

export function Toggle({ value, onChange, label, hint }: {
  value: boolean; onChange: (v: boolean) => void; label: string; hint?: string;
}) {
  return (
    <div className="um-row">
      <div className="um-row-label">
        <span>{label}</span>
        {hint && <button type="button" className="um-info" title={hint}>ⓘ</button>}
      </div>
      <button type="button" role="switch" aria-checked={value} title={label}
        className={`um-toggle ${value ? 'on' : ''}`} onClick={() => onChange(!value)}>
        <span />
      </button>
    </div>
  );
}

export function Seg<T extends string>({ value, options, onChange }: {
  value: T; options: { id: T; label: string }[]; onChange: (v: T) => void;
}) {
  return (
    <div className="um-seg">
      {options.map(o => (
        <button type="button" key={o.id} className={value === o.id ? 'on' : ''}
          onClick={() => onChange(o.id)}>{o.label}</button>
      ))}
    </div>
  );
}

export function Select({ value, options, onChange, title }: {
  value: string; options: { value: string; label: string }[];
  onChange: (v: string) => void; title?: string;
}) {
  return (
    <label className="um-select" title={title}>
      <select value={value} onChange={ev => onChange(ev.target.value)}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <ChevronDown size={15} />
    </label>
  );
}

export function Row({ label, sub, children }: { label: string; sub?: ReactNode; children: ReactNode }) {
  return (
    <div className="um-field-row">
      <div className="um-field-label"><strong>{label}</strong>{sub && <small>{sub}</small>}</div>
      <div className="um-field-control">{children}</div>
    </div>
  );
}

export function NumField({ label, value, onChange, min = -100000, max = 100000, step = 1, unit = '' }: {
  label: string; value: number; onChange: (v: number) => void;
  min?: number; max?: number; step?: number; unit?: string;
}) {
  return (
    <label className="um-numfield">
      <span>{label}</span>
      <span className="um-numfield-val">
        <input type="number" value={Number.isFinite(value) ? Math.round(value * 100) / 100 : 0}
          min={min} max={max} step={step}
          onChange={ev => {
            const n = Number(ev.target.value);
            if (Number.isFinite(n)) onChange(Math.min(max, Math.max(min, n)));
          }} />
        {unit && <em>{unit}</em>}
      </span>
    </label>
  );
}

/** A real range control drawn over a fine tick ruler, like the reference dials. */
export function RulerSlider({ label, value, min, max, step = 1, onChange, unit = '', accent = false }: {
  label: string; value: number; min: number; max: number; step?: number;
  onChange: (v: number) => void; unit?: string; accent?: boolean;
}) {
  const pct = Math.max(0, Math.min(100, (value - min) / (max - min || 1) * 100));
  return (
    <div className={`um-ruler-row ${accent ? 'accent' : ''}`}>
      <span className="um-ruler-label">{label}</span>
      <div className="um-ruler-track">
        <input type="range" min={min} max={max} step={step} value={value}
          aria-label={label} onChange={ev => onChange(Number(ev.target.value))}
          style={{ ['--pct' as string]: `${pct}%` }} />
      </div>
      <span className="um-ruler-val">{Math.round(value * 100) / 100}{unit}</span>
    </div>
  );
}

export function Slider({ label, value, min, max, step = 1, onChange, unit = '' }: {
  label: string; value: number; min: number; max: number; step?: number;
  onChange: (v: number) => void; unit?: string;
}) {
  return (
    <div className="um-slider-row">
      <div className="um-slider-head"><span>{label}</span><b>{Math.round(value * 100) / 100}{unit}</b></div>
      <input type="range" min={min} max={max} step={step} value={value}
        aria-label={label} onChange={ev => onChange(Number(ev.target.value))} />
    </div>
  );
}

export function ColorInput({ value, onChange, label }: {
  value: string; onChange: (v: string) => void; label?: string;
}) {
  return (
    <label className="um-color-input" title={label ?? 'Choose colour'}>
      <span style={{ background: value }} />
      <input type="color" value={/^#[0-9a-f]{6}$/i.test(value) ? value : '#000000'}
        aria-label={label ?? 'Choose colour'} onChange={ev => onChange(ev.target.value)} />
      {label && <b>{label}</b>}
    </label>
  );
}

export function Btn({ children, onClick, variant = 'primary', disabled, title }: {
  children: ReactNode; onClick?: () => void; variant?: 'primary' | 'dark' | 'ghost' | 'teal';
  disabled?: boolean; title?: string;
}) {
  return (
    <button type="button" title={title} disabled={disabled}
      className={`um-btn um-btn-${variant}`} onClick={onClick}>{children}</button>
  );
}

export function Chip({ children, active, onClick, title }: {
  children: ReactNode; active?: boolean; onClick: () => void; title?: string;
}) {
  return <button type="button" title={title} className={`um-chip ${active ? 'on' : ''}`} onClick={onClick}>{children}</button>;
}

export function Swatches({ colors, value, onChange }: {
  colors: string[]; value: string; onChange: (v: string) => void;
}) {
  return (
    <div className="um-swatches">
      {colors.map(c => (
        <button type="button" key={c} title={c} aria-label={`Colour ${c}`}
          className={value.toLowerCase() === c.toLowerCase() ? 'on' : ''}
          style={{ background: c }} onClick={() => onChange(c)} />
      ))}
    </div>
  );
}

export const PALETTE = [
  '#000000', '#ffffff', '#c8c8f2', '#02ffa7', '#22d3ee', '#8b5cf6',
  '#f472b6', '#fb7185', '#f59e0b', '#eab676', '#84cc16', '#1c1c1e',
  '#3b82f6', '#14b8a6', '#a3a3a3', '#6b7280',
];
