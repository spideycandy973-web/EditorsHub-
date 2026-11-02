import React from 'react';
import {
  ChevronLeft, ChevronRight, Diamond, Pause, Play, Repeat, SkipBack, SkipForward,
} from 'lucide-react';
import { useEditor, usePlayhead } from '../editor/store';
import { formatTime } from '../editor/utils';
import { IconBtn } from './ui';

export function PlayerBar() {
  const e = useEditor();
  const time = usePlayhead();
  return (
    <div className="playerbar">
      <div className="player-left">
        <IconBtn title="Go to start" onClick={() => e.goStart()}><SkipBack size={16} /></IconBtn>
        <IconBtn title="Previous frame (←)" onClick={() => e.stepFrame(-1)}><ChevronLeft size={18} /></IconBtn>
        <button type="button" className={`play-btn ${e.playing ? 'playing' : ''}`} onClick={() => e.togglePlay()} title="Play / Pause (Space)">
          {e.playing ? <Pause size={19} fill="currentColor" /> : <Play size={19} fill="currentColor" />}
        </button>
        <IconBtn title="Next frame (→)" onClick={() => e.stepFrame(1)}><ChevronRight size={18} /></IconBtn>
        <IconBtn title="Go to end" onClick={() => e.goEnd()}><SkipForward size={16} /></IconBtn>
      </div>

      <div className="time-readout">
        <strong>{formatTime(time, e.project.fps)}</strong>
        <span>/ {formatTime(e.project.duration, e.project.fps)}</span>
      </div>

      <div className="player-right">
        <IconBtn title="Loop playback" active={e.loop} onClick={() => e.setLoop(!e.loop)}><Repeat size={16} /></IconBtn>
        <IconBtn
          title="Add keyframe for the selected property (K)" active={e.autoKey}
          onClick={() => {
            const l = e.selectedLayer;
            if (!l) { e.notify('Select a layer first', 'warn'); return; }
            e.addKeyframe(l.id, e.selectedPropertyKey);
            e.notify('Keyframe added', 'ok');
          }}
        ><Diamond size={15} /></IconBtn>
        <IconBtn title="Auto keyframe" active={e.autoKey} onClick={() => e.setAutoKey(!e.autoKey)}>
          <span className="key-text">KEY</span>
        </IconBtn>
      </div>
    </div>
  );
}
