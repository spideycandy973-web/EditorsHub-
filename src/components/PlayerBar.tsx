import React from 'react';
import {
  Copy, Maximize2, Pause, Play, Redo2, SkipBack, SkipForward, Undo2,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { IconBtn } from './ui';

/** Alight-Motion-style control bar: seven transport icons, single row.
 *  The ruler + timecode live on the timeline itself (see Timeline.tsx). */
export function PlayerBar() {
  const e = useEditor();
  return (
    <div className="playerbar">
      <div className="am-transport">
        <IconBtn title="Undo (Ctrl+Z)" disabled={!e.canUndo} onClick={() => e.undo()}><Undo2 size={17} /></IconBtn>
        <IconBtn title="Redo (Ctrl+Y)" disabled={!e.canRedo} onClick={() => e.redo()}><Redo2 size={17} /></IconBtn>
        <span className="am-transport-sep" />
        <IconBtn title="Go to start" onClick={() => e.goStart()}><SkipBack size={17} /></IconBtn>
        <button type="button" className={`play-btn ${e.playing ? 'playing' : ''}`} onClick={() => e.togglePlay()} title="Play / Pause (Space)">
          {e.playing ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" />}
        </button>
        <IconBtn title="Go to end" onClick={() => e.goEnd()}><SkipForward size={17} /></IconBtn>
        <span className="am-transport-sep" />
        <IconBtn title="Duplicate selected layer (D)" onClick={() => e.selectedLayer && e.duplicateLayer(e.selectedLayer.id)}><Copy size={16} /></IconBtn>
        <IconBtn title="Fit preview / reset zoom" onClick={() => e.setPreviewZoom(1)}><Maximize2 size={16} /></IconBtn>
      </div>
    </div>
  );
}
