import React, { createContext, useContext, useMemo, useState } from 'react';
import { loadPrefs, savePrefs, type Preferences } from './lib';
import type { XmlImportResult } from '../editor/xml';

export type SheetId =
  | 'none' | 'add' | 'shape' | 'properties' | 'property' | 'settings' | 'create'
  | 'export' | 'projectSettings' | 'xml' | 'about' | 'overflow' | 'draw' | 'vector'
  | 'templates' | 'help' | 'elements' | 'rename' | 'brand' | 'fonts' | 'media' | 'audio' | 'colorPicker' | 'opensource' | 'tutorial'
  | 'save' | 'presetPreview';

export type PropertyId =
  | 'move' | 'blend' | 'color' | 'border' | 'editShape' | 'presets' | 'effects' | 'camera';

export type MoveView = 'pos' | 'size' | 'skew' | 'rotation' | 'easing';
export type FlowId = 'none' | 'replaceMedia' | 'missingFonts' | 'exportPreview';

export interface UIState {
  sheet: SheetId;
  setSheet: (s: SheetId) => void;
  property: PropertyId;
  moveView: MoveView;
  setMoveView: (v: MoveView) => void;
  shapeRatioLocked: boolean;
  setShapeRatioLocked: (v: boolean) => void;
  activePropKey: string;
  setActivePropKey: (k: string) => void;
  homeTab: 'home' | 'projects';
  setHomeTab: (t: 'home' | 'projects') => void;
  homeSegment: 'projects' | 'templates' | 'elements';
  setHomeSegment: (s: 'projects' | 'templates' | 'elements') => void;
  createTab: 'project' | 'element';
  setCreateTab: (t: 'project' | 'element') => void;
  prefs: Preferences;
  setPrefs: (p: Preferences) => void;
  xmlResult: XmlImportResult | null;
  setXmlResult: (r: XmlImportResult | null) => void;
  importUrl: string;
  setImportUrl: (v: string) => void;
  selectedIds: string[];
  setSelectedIds: (ids: string[]) => void;
  toolsOpen: boolean;
  toggleTools: () => void;
  /** Full-screen About page (the reference recording shows a page, not a dialog). */
  aboutOpen: boolean;
  setAboutOpen: (v: boolean) => void;
  /** Full-screen Effect Browser and the Presets dialog from the reference APK. */
  effectBrowser: boolean;
  setEffectBrowser: (v: boolean) => void;
  presetsDialog: boolean;
  setPresetsDialog: (v: boolean) => void;
  /** Full-screen template / import flows from the reference recording. */
  flow: FlowId;
  setFlow: (v: FlowId) => void;
  missingFonts: string[];
  setMissingFonts: (v: string[]) => void;
  openProperty: (p: PropertyId) => void;
  openAdd: () => void;
  setAddPage: (page: Preferences['addPage']) => void;
}

const Ctx = createContext<UIState | null>(null);

export function UIProvider({ children }: { children: React.ReactNode }) {
  const [sheet, setSheet] = useState<SheetId>('none');
  const [property, setProperty] = useState<PropertyId>('move');
  const [moveView, setMoveView] = useState<MoveView>('pos');
  const [shapeRatioLocked, setShapeRatioLocked] = useState(true);
  const [activePropKey, setActivePropKey] = useState('x');
  const [homeTab, setHomeTab] = useState<'home' | 'projects'>('home');
  const [homeSegment, setHomeSegment] = useState<'projects' | 'templates' | 'elements'>('projects');
  const [createTab, setCreateTab] = useState<'project' | 'element'>('project');
  const [prefs, setPrefsState] = useState<Preferences>(() => loadPrefs());
  const [xmlResult, setXmlResult] = useState<XmlImportResult | null>(null);
  const [importUrl, setImportUrl] = useState('');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [toolsOpen, setToolsOpen] = useState(false);
  const [aboutOpen, setAboutOpen] = useState(false);
  const [effectBrowser, setEffectBrowser] = useState(false);
  const [presetsDialog, setPresetsDialog] = useState(false);
  const [flow, setFlow] = useState<FlowId>('none');
  const [missingFonts, setMissingFonts] = useState<string[]>([]);

  const setPrefs = (p: Preferences) => { savePrefs(p); setPrefsState(p); };
  const setAddPage = (page: Preferences['addPage']) => setPrefs({ ...prefs, addPage: page });

  const value = useMemo<UIState>(() => ({
    sheet, setSheet,
    property, moveView, setMoveView, shapeRatioLocked, setShapeRatioLocked,
    activePropKey, setActivePropKey,
    homeTab, setHomeTab, homeSegment, setHomeSegment,
    createTab, setCreateTab, prefs, setPrefs,
    xmlResult, setXmlResult, importUrl, setImportUrl,
    selectedIds, setSelectedIds,
    toolsOpen, toggleTools: () => setToolsOpen(v => !v),
    aboutOpen, setAboutOpen,
    effectBrowser, setEffectBrowser, presetsDialog, setPresetsDialog,
    flow, setFlow, missingFonts, setMissingFonts,
    openProperty: (p) => {
      setProperty(p);
      if (p === 'move') setMoveView('pos');
      setSheet('property');
      setSelectedIds([]);
    },
    openAdd: () => {
      const page = prefs.rememberAddPage ? prefs.addPage : 'add';
      setSheet(page === 'shape' ? 'shape' : page === 'templates' ? 'templates' : 'add');
      setSelectedIds([]);
    },
    setAddPage,
  }), [sheet, property, moveView, shapeRatioLocked, activePropKey, homeTab, homeSegment, createTab, prefs,
    xmlResult, importUrl, selectedIds, toolsOpen, aboutOpen, effectBrowser, presetsDialog, flow, missingFonts]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useUI(): UIState {
  const v = useContext(Ctx);
  if (!v) throw new Error('UIProvider missing');
  return v;
}
