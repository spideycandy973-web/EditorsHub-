import React from 'react';
import { ArrowLeft, Settings2, Share2 } from 'lucide-react';
import { useEditor } from '../editor/store';
import { IconBtn } from './ui';

/** Alight-Motion-style navigation bar: back, project name, settings, export. */
export function TopBar() {
  const e = useEditor();
  return (
    <header className="topbar">
      <IconBtn title="Back to projects" onClick={() => e.setScreen('home')}><ArrowLeft size={20} /></IconBtn>
      <button type="button" className="project-name" onClick={() => e.setModal('projects')} title="Open projects">
        <span>{e.project.name}</span>
        <small>{e.project.width}×{e.project.height} · {e.project.fps}fps</small>
      </button>
      <div className="top-actions">
        <IconBtn title="Editor settings" onClick={() => e.setModal('settings')}><Settings2 size={18} /></IconBtn>
        <IconBtn className="am-export-icon" title="Export video" onClick={() => e.setModal('export')}><Share2 size={18} /></IconBtn>
      </div>
    </header>
  );
}
