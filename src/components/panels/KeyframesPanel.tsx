import React, { useMemo } from 'react';
import { ChevronLeft, ChevronRight, Diamond, Trash2, WandSparkles } from 'lucide-react';
import { useEditor } from '../../editor/store';
import { ease } from '../../editor/animation';
import { EASINGS, EFFECT_MAP, PROP_DEFS, PROP_MAP, type Easing, type Keyframe, type Layer } from '../../editor/types';
import { clamp, round } from '../../editor/utils';
import { Btn, Empty, Section, Segmented, SelectField, Toggle } from '../ui';

interface KfEntry {
  id: string;
  label: string;
  property: string;
  effectId?: string;
  kf: Keyframe;
}

function EasingGraph({ easing }: { easing: Easing }) {
  const pts: string[] = [];
  for (let i = 0; i <= 40; i++) {
    const t = i / 40;
    const v = clamp(ease(t, easing), -0.4, 1.4);
    pts.push(`${(t * 100).toFixed(1)},${(58 - v * 52).toFixed(1)}`);
  }
  return (
    <svg className="easing-graph" viewBox="0 0 100 60" preserveAspectRatio="none">
      <line x1="0" y1="58" x2="100" y2="58" className="axis" />
      <line x1="0" y1="6" x2="100" y2="6" className="axis" />
      <polyline points={pts.join(' ')} fill="none" stroke="var(--accent)" strokeWidth="2" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

export function KeyframesPanel() {
  const e = useEditor();
  const layer = e.selectedLayer;
  if (!layer) return <div className="panel-body"><Empty icon={<Diamond size={26} />} title="No layer selected" text="Keyframes belong to layers. Select one first." /></div>;

  const l = layer as Layer;
  const local = e.localTime;
  const entries: KfEntry[] = useMemo(() => {
    const out: KfEntry[] = [];
    for (const [key, a] of Object.entries(l.props)) {
      for (const kf of a.keyframes) out.push({ id: `${key}:${kf.id}`, label: PROP_MAP[key]?.label ?? key, property: key, kf });
    }
    for (const fx of l.effects) {
      for (const [key, a] of Object.entries(fx.params)) {
        for (const kf of a.keyframes) {
          out.push({ id: `${fx.id}:${key}:${kf.id}`, label: `${EFFECT_MAP[fx.type]?.label ?? fx.type} · ${key}`, property: key, effectId: fx.id, kf });
        }
      }
    }
    return out.sort((a, b) => a.kf.time - b.kf.time);
  }, [l]);

  const times = [...new Set(entries.map(x => x.kf.time))].sort((a, b) => a - b);
  const prev = [...times].reverse().find(t => t < local - 0.001);
  const next = times.find(t => t > local + 0.001);
  const selectedProp = e.selectedPropertyKey;
  const propAnim = l.props[selectedProp];
  const activeKf = propAnim?.keyframes.find(k => Math.abs(k.time - local) < 0.5 / e.project.fps);
  const easing = activeKf?.easing ?? propAnim?.keyframes.filter(k => k.time <= local).sort((a, b) => b.time - a.time)[0]?.easing ?? 'linear';

  const update = (entry: KfEntry, patch: Partial<{ time: number; value: number; easing: Easing }>) => {
    if (entry.effectId) e.updateEffectKeyframe(entry.effectId, entry.property, entry.kf.id, patch);
    else e.updateKeyframe(l.id, entry.property, entry.kf.id, patch);
  };
  const remove = (entry: KfEntry) => {
    if (entry.effectId) e.removeEffectKeyframe(entry.effectId, entry.property, entry.kf.id);
    else e.removeKeyframe(l.id, entry.property, entry.kf.id);
  };

  return (
    <div className="panel-body">
      <div className="panel-note">Layer <b>{l.name}</b> · {entries.length} keyframes · playhead {round(local, 2)}s (layer time)</div>

      <Section title="Playhead & keyframes">
        <div className="row-buttons">
          <Btn variant="soft" onClick={() => prev !== undefined && e.setTime(l.start + prev)} disabled={prev === undefined}><ChevronLeft size={14} /> Prev</Btn>
          <Btn variant="primary" onClick={() => e.addKeyframe(l.id, selectedProp)}><Diamond size={14} /> Add key</Btn>
          <Btn variant="soft" onClick={() => next !== undefined && e.setTime(l.start + next)} disabled={next === undefined}>Next <ChevronRight size={14} /></Btn>
        </div>
        <SelectField
          label="Active property" value={selectedProp}
          options={PROP_DEFS.map(p => ({ value: p.key, label: `${p.label} (${p.group})` }))}
          onChange={v => e.selectProperty(v)}
        />
        <Toggle label="Auto keyframe" checked={e.autoKey} onChange={v => e.setAutoKey(v)} hint="Create keys when editing values" />
        <Toggle label="Snap to frames" checked={e.snapToFrame} onChange={v => e.setSnap(v)} />
        <div className="row-buttons">
          <Btn variant="soft" onClick={() => e.clearPropertyKeyframes(l.id, selectedProp)}><Trash2 size={14} /> Clear property keys</Btn>
          <Btn variant="soft" onClick={() => e.copyLayer()}><WandSparkles size={14} /> Copy layer</Btn>
        </div>
      </Section>

      <Section title="Interpolation" right={<span className="hint">{easing}</span>}>
        <EasingGraph easing={easing} />
        <Segmented
          small value={easing}
          options={EASINGS.map(x => ({ value: x, label: x.replace('ease', '') }))}
          onChange={v => {
            const target = activeKf ?? (propAnim?.keyframes.filter(k => k.time <= local).sort((a, b) => b.time - a.time)[0]);
            if (target) e.updateKeyframe(l.id, selectedProp, target.id, { easing: v as Easing });
            else e.notify('Move the playhead to a keyframe first', 'warn');
          }}
        />
        <p className="hint block">Easing is applied to the segment leaving a keyframe. Choose <b>hold</b> for stepped animation.</p>
      </Section>

      <Section title={`All keyframes (${entries.length})`} defaultOpen={entries.length > 0}>
        {entries.length === 0 ? (
          <Empty icon={<Diamond size={24} />} title="No keyframes" text="Press the diamond next to any slider, or use “Add key” above." />
        ) : (
          <div className="kf-list">
            {entries.map(entry => (
              <div className={`kf-row ${Math.abs(entry.kf.time - local) < 0.5 / e.project.fps ? 'active' : ''}`} key={entry.id}>
                <button type="button" className="kf-goto" onClick={() => { e.selectProperty(entry.property); e.setTime(l.start + entry.kf.time); }} title="Jump to keyframe">
                  <Diamond size={12} fill="currentColor" />
                </button>
                <div className="kf-info">
                  <strong>{entry.label}</strong>
                  <span>@ {round(entry.kf.time, 2)}s → {formatTimeAbs(l.start + entry.kf.time)}</span>
                </div>
                <div className="kf-inputs">
                  <input type="number" step={1 / e.project.fps} value={round(entry.kf.time, 3)} title="Layer-local time (s)"
                    onChange={ev => update(entry, { time: clamp(Number(ev.target.value), 0, l.duration) })} />
                  <input type="number" step="0.1" value={round(entry.kf.value, 3)} title="Value"
                    onChange={ev => update(entry, { value: Number(ev.target.value) })} />
                  <select value={entry.kf.easing} onChange={ev => update(entry, { easing: ev.target.value as Easing })}>
                    {EASINGS.map(x => <option key={x} value={x}>{x}</option>)}
                  </select>
                </div>
                <button type="button" className="icon-btn danger" title="Delete keyframe" onClick={() => remove(entry)}><Trash2 size={13} /></button>
              </div>
            ))}
          </div>
        )}
      </Section>
    </div>
  );
}

function formatTimeAbs(t: number): string {
  const m = Math.floor(t / 60), s = t % 60;
  return `${m}:${s.toFixed(2).padStart(5, '0')}`;
}
