import React, { useMemo, useState } from 'react';
import { ChevronDown, ChevronUp, Diamond, Eye, EyeOff, Plus, Search, Sparkles, Trash2 } from 'lucide-react';
import { useEditor } from '../../editor/store';
import { EFFECT_DEFS, EFFECT_MAP, type EffectDef } from '../../editor/types';
import { Btn, Empty, Section, SliderRow } from '../ui';

export function EffectsPanel() {
  const e = useEditor();
  const [query, setQuery] = useState('');
  const layer = e.selectedLayer;
  const effect = e.selectedEffect;

  const groups = useMemo(() => {
    const map = new Map<string, EffectDef[]>();
    for (const d of EFFECT_DEFS) {
      if (query && !`${d.label} ${d.group} ${d.description}`.toLowerCase().includes(query.toLowerCase())) continue;
      if (!map.has(d.group)) map.set(d.group, []);
      map.get(d.group)!.push(d);
    }
    return [...map.entries()];
  }, [query]);

  if (!layer) {
    return <div className="panel-body"><Empty icon={<Sparkles size={26} />} title="Select a layer" text="Effects are applied to the selected layer." /></div>;
  }

  const local = e.localTime;

  return (
    <div className="panel-body">
      <div className="panel-note">Effects on <b>{layer.name}</b> · rendered bottom-to-top</div>

      <Section title={`Applied effects (${layer.effects.length})`}>
        {layer.effects.length === 0 && <p className="hint block">No effects yet. Add one from the catalog below.</p>}
        <div className="effect-list">
          {layer.effects.map((fx, i) => (
            <div key={fx.id} className={`effect-item ${e.selectedEffectId === fx.id ? 'selected' : ''} ${fx.enabled ? '' : 'off'}`} onClick={() => e.selectEffect(fx.id)}>
              <button type="button" className="fx-toggle" title={fx.enabled ? 'Disable' : 'Enable'} onClick={ev => { ev.stopPropagation(); e.toggleEffect(fx.id); }}>
                {fx.enabled ? <Eye size={14} /> : <EyeOff size={14} />}
              </button>
              <span className="fx-name">{EFFECT_MAP[fx.type]?.label ?? fx.type}</span>
              <span className="fx-count">{Object.values(fx.params).reduce((n, a) => n + a.keyframes.length, 0)} keys</span>
              <button type="button" title="Move up" onClick={ev => { ev.stopPropagation(); e.moveEffect(fx.id, -1); }} disabled={i === 0}><ChevronUp size={13} /></button>
              <button type="button" title="Move down" onClick={ev => { ev.stopPropagation(); e.moveEffect(fx.id, 1); }} disabled={i === layer.effects.length - 1}><ChevronDown size={13} /></button>
              <button type="button" title="Remove effect" className="danger" onClick={ev => { ev.stopPropagation(); e.removeEffect(fx.id); }}><Trash2 size={13} /></button>
            </div>
          ))}
        </div>
      </Section>

      {effect && EFFECT_MAP[effect.type] && (
        <Section title={`${EFFECT_MAP[effect.type].label} settings`} right={<span className="hint">{EFFECT_MAP[effect.type].description}</span>}>
          {EFFECT_MAP[effect.type].params.map(p => {
            const a = effect.params[p.key];
            const keyed = !!a?.keyframes.some(k => Math.abs(k.time - local) < 0.5 / e.project.fps);
            return (
              <SliderRow
                key={p.key} label={p.label} value={e.getEffectValue(effect.id, p.key)}
                min={p.min} max={p.max} step={p.step} unit={p.unit} keyed={keyed}
                onChange={v => e.setEffectParam(effect.id, p.key, v)}
                onKeyframe={() => {
                  const found = a?.keyframes.find(k => Math.abs(k.time - local) < 0.5 / e.project.fps);
                  if (found) e.removeEffectKeyframe(effect.id, p.key, found.id);
                  else e.addEffectKeyframe(effect.id, p.key);
                }}
              />
            );
          })}
          <Btn variant="soft" onClick={() => e.removeEffect(effect.id)}><Trash2 size={14} /> Remove effect</Btn>
        </Section>
      )}

      <Section title="Effect catalog">
        <div className="search-box"><Search size={14} /><input value={query} onChange={ev => setQuery(ev.target.value)} placeholder="Search effects…" /></div>
        {groups.map(([group, defs]) => (
          <div className="effect-group" key={group}>
            <div className="subhead">{group}</div>
            <div className="effect-catalog">
              {defs.map(d => (
                <button key={d.type} type="button" className="effect-add" title={d.description} onClick={() => e.addEffect(d.type)}>
                  <Plus size={13} /><span>{d.label}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
        {groups.length === 0 && <p className="hint block">No effects match “{query}”.</p>}
      </Section>

      <div className="tip-card">
        <Diamond size={15} />
        <span>Turn on <b>Auto keyframe</b> in the top bar, then animate any effect parameter by moving the playhead and changing its value.</span>
      </div>
    </div>
  );
}
