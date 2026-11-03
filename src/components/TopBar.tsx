import React from 'react';
import {
  Diamond, Download, FolderOpen, Grid3X3, HelpCircle, Redo2, ScanLine, Undo2,
} from 'lucide-react';
import { useEditor } from '../editor/store';
import { Btn, IconBtn } from './ui';

export function TopBar() {
  const e = useEditor();
  return (
    <header className="topbar">
      <div className="brand" title="EditorsHub — video & motion editor">
        <span className="brand-mark"><b className="brand-e">E</b><b className="brand-h">H</b></span>
        <span className="brand-word">EditorsHub</span>
      </div>
      <button type="button" className="project-name" onClick={() => e.setModal('projects')} title="Open projects">
        <span>{e.project.name}</span>
        <small>{e.project.width}×{e.project.height} · {e.project.fps}fps</small>
      </button>
      <div className="top-actions">
        <IconBtn title="Undo (Ctrl+Z)" disabled={!e.canUndo} onClick={() => e.undo()}><Undo2 size={17} /></IconBtn>
        <IconBtn title="Redo (Ctrl+Y)" disabled={!e.canRedo} onClick={() => e.redo()}><Redo2 size={17} /></IconBtn>
        <IconBtn title="Auto-keyframe: create a keyframe whenever you edit a value" active={e.autoKey} onClick={() => e.setAutoKey(!e.autoKey)}>
          <Diamond size={16} fill={e.autoKey ? 'currentColor' : 'none'} />
        </IconBtn>
        <IconBtn title="Grid overlay" active={e.showGrid} onClick={() => e.toggleGrid()}><Grid3X3 size={16} /></IconBtn>
        <IconBtn title="Safe area guides" active={e.showGuides} onClick={() => e.toggleGuides()}><ScanLine size={16} /></IconBtn>
        <IconBtn className="hide-mobile" title="Help & shortcuts" onClick={() => e.setModal('help')}><HelpCircle size={17} /></IconBtn>
        <IconBtn className="hide-mobile" title="Projects" onClick={() => e.setModal('projects')}><FolderOpen size={17} /></IconBtn>
        <Btn variant="primary" className="export-btn" onClick={() => e.setModal('export')} title="Export video, frames or project">
          <Download size={15} /> <span>Export</span>
        </Btn>
      </div>
    </header>
  );
}
