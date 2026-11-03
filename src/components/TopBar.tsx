import React from 'react';
import { ArrowLeft, ExternalLink, MoreVertical, Settings } from 'lucide-react';
import { useEditor } from '../editor/store';
import { IconBtn } from './ui';

const PANEL_TITLES: Record<string, string> = {
  edit: 'Move & Transform',
  effects: 'Effects',
  keyframes: 'Keyframes',
  project: 'Project',
};

/**
 * Alight-Motion-style navigation bar.
 * With a property panel open the bar becomes "back / panel name / overflow";
 * otherwise it is the project bar "back / project name / settings / export".
 */
export function TopBar() {
  const e = useEditor();
  const propertyPanel = e.sheetOpen && e.panel !== 'add';
  const title = propertyPanel ? (PANEL_TITLES[e.panel] ?? e.project.name) : e.project.name;

  return (
    <header className="topbar">
      <IconBtn
        title={propertyPanel ? 'Close panel' : 'Back to projects'}
        onClick={() => (propertyPanel ? e.toggleSheet() : e.setScreen('home'))}
      ><ArrowLeft size={20} /></IconBtn>
      <button type="button" className="project-name" onClick={() => (propertyPanel ? e.toggleSheet() : e.setModal('projects'))} title={propertyPanel ? 'Close panel' : 'Open projects'}>
        <span>{title}</span>
        {!propertyPanel && <small>{e.project.width}×{e.project.height} · {e.project.fps}fps</small>}
      </button>
      <div className="top-actions">
        {propertyPanel ? (
          <IconBtn title="Layer options" onClick={() => e.setModal('layerMenu')}><MoreVertical size={18} /></IconBtn>
        ) : (
          <>
            <IconBtn title="Editor settings" onClick={() => e.setModal('settings')}><Settings size={18} /></IconBtn>
            <IconBtn className="am-export-icon" title="Export video" onClick={() => e.setModal('export')}><ExternalLink size={18} /></IconBtn>
          </>
        )}
      </div>
    </header>
  );
}
