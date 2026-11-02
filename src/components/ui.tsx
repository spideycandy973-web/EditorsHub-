import React, { useEffect, useRef, useState } from 'react';
import { X, ChevronDown, Diamond } from 'lucide-react';
import { editor } from '../editor/store';
import { clamp, hexToRgb, normalizeColor, round } from '../editor/utils';

export function Btn({
  children, onClick, variant = 'ghost', disabled, className = '', title, type = 'button',
}: {
  children: React.ReactNode; onClick?: () => void; variant?: 'ghost' | 'primary' | 'soft' | 'danger';
  disabled?: boolean; className?: string; title?: string; type?: 'button' | 'submit';
}) {
  return (
    <button type={type} className={`btn btn-${variant} ${className}`} onClick={onClick} disabled={disabled} title={title}>
      {children}
    </button>
  );
}

export function IconBtn({
  children, onClick, active, disabled, title, className = '', danger,
}: {
  children: React.ReactNode; onClick?: (e: React.MouseEvent) => void; active?: boolean;
  disabled?: boolean; title?: string; className?: string; danger?: boolean;
}) {
  return (
    <button
      type="button" title={title} disabled={disabled}
      className={`icon-btn ${active ? 'active' : ''} ${danger ? 'danger' : ''} ${className}`}
      onClick={onClick}
    >{children}</button>
  );
}

export function Section({ title, right, children, defaultOpen = true }: {
  title: string; right?: React.ReactNode; children: React.ReactNode; defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className="section">
      <div className="section-head" onClick={() => setOpen(!open)}>
        <span className="section-title">{title}</span>
        <div className="section-right" onClick={e => e.stopPropagation()}>{right}</div>
        <ChevronDown size={15} className={`chev ${open ? 'open' : ''}`} />
      </div>
      {open && <div className="section-body">{children}</div>}
    </section>
  );
}

export function SliderRow({
  label, value, min, max, step = 1, unit, onChange, onKeyframe, keyed, disabled,
}: {
  label: string; value: number; min: number; max: number; step?: number; unit?: string;
  onChange: (v: number) => void; onKeyframe?: () => void; keyed?: boolean; disabled?: boolean;
}) {
  const pct = clamp((value - min) / (max - min || 1), 0, 1) * 100;
  const dragging = useRef(false);
  return (
    <div className={`slider-row ${disabled ? 'disabled' : ''}`}>
      <div className="slider-top">
        <span className="slider-label">{label}</span>
        <div className="slider-value">
          <input
            type="number" className="num-mini" value={round(value, 3)}
            min={min} max={max} step={step} disabled={disabled}
            onChange={e => { const v = Number(e.target.value); if (Number.isFinite(v)) onChange(clamp(v, min, max)); }}
          />
          {unit && <span className="unit">{unit}</span>}
        </div>
      </div>
      <div className="slider-line">
        {onKeyframe && (
          <button
            type="button" className={`kf-diamond ${keyed ? 'on' : ''}`} title="Add / edit keyframe at playhead"
            onClick={onKeyframe}
          ><Diamond size={13} fill={keyed ? 'currentColor' : 'none'} /></button>
        )}
        <input
          type="range" className="range" min={min} max={max} step={step}
          value={clamp(value, min, max)} disabled={disabled}
          style={{ background: `linear-gradient(90deg, var(--accent) ${pct}%, #343646 ${pct}%)` }}
          onPointerDown={() => { dragging.current = true; editor.beginBatch(); }}
          onPointerUp={() => { if (dragging.current) { dragging.current = false; editor.endBatch(); } }}
          onPointerCancel={() => { if (dragging.current) { dragging.current = false; editor.endBatch(); } }}
          onChange={e => onChange(Number(e.target.value))}
        />
      </div>
    </div>
  );
}

export function NumberField({ label, value, min, max, step = 0.1, onChange, suffix }: {
  label: string; value: number; min?: number; max?: number; step?: number;
  onChange: (v: number) => void; suffix?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <div className="field-input">
        <input
          type="number" value={round(value, 3)} min={min} max={max} step={step}
          onChange={e => { const v = Number(e.target.value); if (Number.isFinite(v)) onChange(v); }}
        />
        {suffix && <small>{suffix}</small>}
      </div>
    </label>
  );
}

export function TextField({ label, value, onChange, placeholder, multiline }: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string; multiline?: boolean;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {multiline ? (
        <textarea value={value} placeholder={placeholder} rows={3} onChange={e => onChange(e.target.value)} />
      ) : (
        <input type="text" value={value} placeholder={placeholder} onChange={e => onChange(e.target.value)} />
      )}
    </label>
  );
}

function toHex(color: string): string {
  const c = normalizeColor(color, '#ffffff');
  if (/^#[0-9a-f]{6}$/i.test(c)) return c;
  const [r, g, b] = hexToRgb(c);
  return `#${[r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')}`;
}

export function ColorField({ label, value, onChange, swatches }: {
  label: string; value: string; onChange: (v: string) => void; swatches?: string[];
}) {
  const hex = toHex(value);
  return (
    <div className="color-field">
      <div className="color-top">
        <span>{label}</span>
        <input type="text" value={value} onChange={e => onChange(e.target.value)} spellCheck={false} />
      </div>
      <div className="color-bottom">
        <label className="color-swatch" style={{ background: value }}>
          <input type="color" value={hex} onChange={e => onChange(e.target.value)} />
        </label>
        <div className="swatch-list">
          {(swatches ?? ['#ffffff', '#000000', '#7c6bff', '#22d3ee', '#f472b6', '#fbbf24', '#4ade80', '#fb7185']).map(c => (
            <button key={c} type="button" style={{ background: c }} title={c} onClick={() => onChange(c)} />
          ))}
        </div>
      </div>
    </div>
  );
}

export function Segmented({ value, options, onChange, small }: {
  value: string; options: { value: string; label: string; icon?: React.ReactNode }[];
  onChange: (v: string) => void; small?: boolean;
}) {
  return (
    <div className={`segmented ${small ? 'small' : ''}`}>
      {options.map(o => (
        <button key={o.value} type="button" className={value === o.value ? 'on' : ''} onClick={() => onChange(o.value)} title={o.label}>
          {o.icon}{o.label}
        </button>
      ))}
    </div>
  );
}

export function SelectField<T extends string>({ label, value, options, onChange }: {
  label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={e => onChange(e.target.value as T)}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

export function Toggle({ label, checked, onChange, hint }: {
  label: string; checked: boolean; onChange: (v: boolean) => void; hint?: string;
}) {
  return (
    <button type="button" className="toggle-row" onClick={() => onChange(!checked)}>
      <span>{label}{hint && <small>{hint}</small>}</span>
      <span className={`switch ${checked ? 'on' : ''}`}><i /></span>
    </button>
  );
}

export function Modal({ title, onClose, children, wide, footer }: {
  title: string; onClose: () => void; children: React.ReactNode; wide?: boolean; footer?: React.ReactNode;
}) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`modal ${wide ? 'wide' : ''}`}>
        <div className="modal-head">
          <h2>{title}</h2>
          <IconBtn onClick={onClose} title="Close"><X size={18} /></IconBtn>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
}

export function Progress({ ratio, label }: { ratio: number; label: string }) {
  return (
    <div className="progress">
      <div className="progress-label"><span>{label}</span><span>{Math.round(ratio * 100)}%</span></div>
      <div className="progress-track"><div className="progress-fill" style={{ width: `${clamp(ratio, 0, 1) * 100}%` }} /></div>
    </div>
  );
}

export function Empty({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <strong>{title}</strong>
      <p>{text}</p>
    </div>
  );
}

export function Tag({ children, color }: { children: React.ReactNode; color?: string }) {
  return <span className="tag" style={color ? { borderColor: color, color } : undefined}>{children}</span>;
}
