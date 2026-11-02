import React, { useState } from 'react';
import {
  AlignCenter, AlignLeft, AlignRight, Copy, Diamond, Eye, EyeOff, Lock, Music2,
  Scissors, SlidersHorizontal, Trash2, Unlock, WandSparkles,
} from 'lucide-react';
import { useEditor } from '../../editor/store';
import { PROP_DEFS, PROP_MAP, BLEND_LABELS, FONT_FAMILIES, SHAPE_LABELS, type BlendMode, type Layer, type ShapeKind } from '../../editor/types';
import { BLEND_MODES, MOTION_PRESETS, PALETTES } from '../../editor/defaults';
import { clamp, round } from '../../editor/utils';
import { Btn, ColorField, Empty, NumberField, Section, Segmented, SelectField, SliderRow, TextField, Toggle } from '../ui';

type SubTab = 'transform' | 'style' | 'timing';

export function EditPanel() {
  const e = useEditor();
  const [tab, setTab] = useState<SubTab>('transform');
  const layer = e.selectedLayer;

  if (!layer) {
    return <div className="panel-body"><Empty icon={<SlidersHorizontal size={26} />} title="No layer selected" text="Add a layer or tap one in the timeline to edit its properties." /></div>;
  }

  const l = layer as Layer;
  const local = e.localTime;
  const fontOptions = [
    ...FONT_FAMILIES.map(f => ({ value: f, label: f.split(',')[0].replace(/"/g, '') })),
    ...Object.values(e.assets).filter(a => a.type === 'font' && a.fontFamily)
      .map(a => ({ value: a.fontFamily!, label: a.name })),
  ];

  const propSlider = (key: string) => {
    const def = PROP_MAP[key];
    if (!def) return null;
    const a = l.props[key];
    const keyed = !!a?.keyframes.some(k => Math.abs(k.time - local) < 0.5 / e.project.fps);
    return (
      <SliderRow
        key={key} label={def.label} value={e.getLayerProp(l, key)} min={def.min} max={def.max}
        step={def.step} unit={def.unit} keyed={keyed}
        onChange={v => e.setProp(l.id, key, v)}
        onKeyframe={() => {
          const found = a?.keyframes.find(k => Math.abs(k.time - local) < 0.5 / e.project.fps);
          if (found) e.removeKeyframe(l.id, key, found.id);
          else { e.selectProperty(key); e.addKeyframe(l.id, key); }
        }}
      />
    );
  };

  const effectParamSliders = (keys: string[]) => keys.map(propSlider);

  return (
    <div className="panel-body">
      <div className="layer-header">
        <div className="layer-header-main">
          <input className="layer-name-input" value={l.name} onChange={ev => e.renameLayer(l.id, ev.target.value)} />
          <span className={`type-tag t-${l.type}`}>{l.type}</span>
        </div>
        <div className="layer-header-actions">
          <button type="button" title={l.visible ? 'Hide layer' : 'Show layer'} onClick={() => e.toggleVisible(l.id)}>{l.visible ? <Eye size={15} /> : <EyeOff size={15} />}</button>
          <button type="button" title={l.locked ? 'Unlock layer' : 'Lock layer'} onClick={() => e.toggleLock(l.id)}>{l.locked ? <Lock size={14} /> : <Unlock size={14} />}</button>
          <button type="button" title="Duplicate" onClick={() => e.duplicateLayer(l.id)}><Copy size={14} /></button>
          <button type="button" title="Split at playhead" onClick={() => e.splitLayer(l.id)}><Scissors size={14} /></button>
          <button type="button" title="Delete layer" className="danger" onClick={() => e.deleteLayer(l.id)}><Trash2 size={14} /></button>
        </div>
      </div>

      <div className="subtabs">
        {(['transform', 'style', 'timing'] as SubTab[]).map(t => (
          <button key={t} type="button" className={tab === t ? 'on' : ''} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>

      {tab === 'transform' && (
        <>
          <Section title="Transform" right={<span className="hint">◇ = keyframe</span>}>
            <div className="prop-two-col">
              {effectParamSliders(['x', 'y'])}
            </div>
            {effectParamSliders(['scaleX', 'scaleY', 'rotation', 'opacity'])}
            <details className="advanced">
              <summary>Anchor, skew & motion</summary>
              {effectParamSliders(['anchorX', 'anchorY', 'skewX'])}
            </details>
          </Section>

          <Section title="Animation presets" right={<WandSparkles size={14} />}>
            <div className="preset-grid">
              {MOTION_PRESETS.map(p => (
                <button key={p.id} type="button" className="preset-btn" title={p.description} onClick={() => e.applyMotionPreset(p.id)}>
                  <Diamond size={12} /> {p.label}
                </button>
              ))}
            </div>
            <p className="hint block">Presets create real keyframes on this layer. Edit them in the <b>Keys</b> tab.</p>
          </Section>
        </>
      )}

      {tab === 'style' && (
        <>
          {(l.type === 'text') && (
            <Section title="Text">
              <TextField label="Content" value={l.style.text} onChange={v => e.setStyle(l.id, { text: v })} multiline />
              <SelectField label="Font family" value={l.style.fontFamily} options={fontOptions} onChange={v => e.setStyle(l.id, { fontFamily: v })} />
              <div className="field">
                <span>Weight & alignment</span>
                <div className="inline-row">
                  <Segmented small value={String(l.style.fontWeight)} options={['400', '600', '700', '800', '900'].map(w => ({ value: w, label: w }))} onChange={v => e.setStyle(l.id, { fontWeight: Number(v) })} />
                </div>
              </div>
              <div className="inline-row">
                <button type="button" className={`mini-toggle ${l.style.italic ? 'on' : ''}`} onClick={() => e.setStyle(l.id, { italic: !l.style.italic })}><em>I</em> Italic</button>
                <button type="button" className={`mini-toggle ${l.style.uppercase ? 'on' : ''}`} onClick={() => e.setStyle(l.id, { uppercase: !l.style.uppercase })}>AA Caps</button>
              </div>
              <Segmented value={l.style.align} options={[
                { value: 'left', label: 'Left', icon: <AlignLeft size={13} /> },
                { value: 'center', label: 'Center', icon: <AlignCenter size={13} /> },
                { value: 'right', label: 'Right', icon: <AlignRight size={13} /> },
              ]} onChange={v => e.setStyle(l.id, { align: v as 'left' | 'center' | 'right' })} />
              {effectParamSliders(['fontSize', 'letterSpacing', 'lineHeight', 'strokeWidth'])}
            </Section>
          )}

          {(l.type === 'shape' || l.type === 'solid') && (
            <Section title={l.type === 'solid' ? 'Solid' : 'Shape'}>
              {l.type === 'shape' && (
                <>
                  <div className="shape-mini-grid">
                    {(Object.keys(SHAPE_LABELS) as ShapeKind[]).map(s => (
                      <button key={s} type="button" className={l.style.shape === s ? 'on' : ''} onClick={() => e.setShape(l.id, s)}>{SHAPE_LABELS[s]}</button>
                    ))}
                  </div>
                  {['star'].includes(l.style.shape) && effectParamSliders(['starPoints', 'innerRadius'])}
                  {l.style.shape === 'polygon' && effectParamSliders(['polygonSides'])}
                  {effectParamSliders(['cornerRadius', 'strokeWidth'])}
                </>
              )}
              {effectParamSliders(['shapeWidth', 'shapeHeight'])}
            </Section>
          )}

          {(l.type === 'image' || l.type === 'video' || l.type === 'audio') && (
            <Section title="Media">
              <div className="field">
                <span>Source</span>
                <div className="source-info">{e.assets[l.sourceId ?? '']?.name ?? 'No media linked'}</div>
              </div>
              {l.type !== 'audio' && (
                <div className="field">
                  <span>Fit</span>
                  <Segmented small value={l.style.fit} options={['cover', 'contain', 'stretch', 'none'].map(v => ({ value: v, label: v }))} onChange={v => e.setStyle(l.id, { fit: v as 'cover' | 'contain' | 'stretch' | 'none' })} />
                </div>
              )}
              <Toggle label="Loop source" checked={l.style.loop} onChange={v => e.setStyle(l.id, { loop: v })} />
              {l.type === 'video' && <Toggle label="Include video audio" checked={l.style.audio} onChange={v => e.setStyle(l.id, { audio: v })} hint="Mixed into the exported video" />}
              {effectParamSliders(l.type === 'image' ? ['speed'] : ['speed', 'volume'])}
              <Btn variant="soft" onClick={() => e.setPanel('add')}><Music2 size={14} /> Change / add media</Btn>
            </Section>
          )}

          {l.type !== 'audio' && l.type !== 'group' && (
            <Section title="Color & fill">
              <div className="field">
                <span>Fill type</span>
                <Segmented small value={l.style.fillType} options={[
                  { value: 'solid', label: 'Solid' }, { value: 'linear', label: 'Linear' }, { value: 'radial', label: 'Radial' },
                ]} onChange={v => e.setStyle(l.id, { fillType: v as 'solid' | 'linear' | 'radial' })} />
              </div>
              <ColorField label="Primary color" value={l.style.color} onChange={v => e.setStyle(l.id, { color: v })} />
              {l.style.fillType !== 'solid' && (
                <>
                  <ColorField label="Gradient color" value={l.style.color2} onChange={v => e.setStyle(l.id, { color2: v })} />
                  {l.style.fillType === 'linear' && propSlider('gradientAngle')}
                </>
              )}
              <ColorField label="Stroke color" value={l.style.strokeColor} onChange={v => e.setStyle(l.id, { strokeColor: v })} />
              <div className="subhead">Palettes</div>
              {PALETTES.map(pal => (
                <div className="palette-row" key={pal.name}>
                  <span>{pal.name}</span>
                  {pal.colors.map(c => <button key={c} type="button" style={{ background: c }} title={c} onClick={() => e.setStyle(l.id, { color: c })} />)}
                </div>
              ))}
            </Section>
          )}
        </>
      )}

      {tab === 'timing' && (
        <>
          <Section title="Timing">
            <div className="field-grid">
              <NumberField label="Start" value={l.start} min={0} max={e.project.duration} step={1 / e.project.fps} suffix="s" onChange={v => e.setTiming(l.id, { start: v })} />
              <NumberField label="Duration" value={l.duration} min={0.1} max={e.project.duration} step={1 / e.project.fps} suffix="s" onChange={v => e.setTiming(l.id, { duration: v })} />
              <NumberField label="Source in" value={l.inPoint} min={0} step={1 / e.project.fps} suffix="s" onChange={v => e.setTiming(l.id, { inPoint: v })} />
            </div>
            <div className="row-buttons wrap">
              <Btn variant="soft" onClick={() => e.splitLayer(l.id)}><Scissors size={14} /> Split here</Btn>
              <Btn variant="soft" onClick={() => e.setTiming(l.id, { duration: clamp(e.time - l.start, 0.1, e.project.duration) })}>Trim end to playhead</Btn>
              <Btn variant="soft" onClick={() => e.setTiming(l.id, { start: e.time, duration: clamp(l.duration, 0.1, e.project.duration - e.time) })}>Move to playhead</Btn>
            </div>
            <p className="hint block">Drag layer bars in the timeline to move them, or drag their edges to trim.</p>
          </Section>

          <Section title="Blending & hierarchy">
            <SelectField label="Blend mode" value={l.blend} options={BLEND_MODES.map(b => ({ value: b, label: BLEND_LABELS[b] }))} onChange={v => e.setBlend(l.id, v as BlendMode)} />
            <SelectField
              label="Parent (null object)" value={l.parentId ?? ''}
              options={[{ value: '', label: 'None' }, ...e.project.layers.filter(x => x.id !== l.id).map(x => ({ value: x.id, label: x.name }))]}
              onChange={v => e.setParent(l.id, v || null)}
            />
          </Section>

          <Section title="Mask">
            <div className="field">
              <span>Mask shape</span>
              <Segmented small value={l.mask.type} options={[
                { value: 'none', label: 'None' }, { value: 'rect', label: 'Rect' },
                { value: 'ellipse', label: 'Ellipse' }, { value: 'linear', label: 'Fade' },
              ]} onChange={v => e.setMask(l.id, { type: v as 'none' | 'rect' | 'ellipse' | 'linear' })} />
            </div>
            <SliderRow label="Feather" value={l.mask.feather} min={0} max={200} step={1} unit="px" onChange={v => e.setMask(l.id, { feather: v })} />
            <Toggle label="Invert mask" checked={l.mask.invert} onChange={v => e.setMask(l.id, { invert: v })} />
          </Section>

          <Section title="Layer info" defaultOpen={false}>
            <div className="info-list">
              <div><span>ID</span><code>{l.id}</code></div>
              <div><span>Keyframed properties</span><b>{Object.values(l.props).filter(a => a.keyframes.length).length}</b></div>
              <div><span>Effects</span><b>{l.effects.length}</b></div>
              <div><span>Local playhead</span><b>{round(local, 2)}s</b></div>
            </div>
          </Section>
        </>
      )}
    </div>
  );
}
