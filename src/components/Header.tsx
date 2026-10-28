import React from 'react';
import { ExternalLink, Download, Smartphone } from 'lucide-react';

interface HeaderProps {
  activeTab: 'workspace' | 'colab' | 'audit';
  onSelectTab: (tab: 'workspace' | 'colab' | 'audit') => void;
  onOpenColab: () => void;
  onOpenAndroidRelease: () => void;
  onExportCurrent: () => void;
  isProcessing: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  onSelectTab,
  onOpenColab,
  onOpenAndroidRelease,
  onExportCurrent,
  isProcessing,
}) => {
  return (
    <header className="flex items-center justify-between gap-8 px-6 py-3.5 border-b border-neutral-800 bg-neutral-950/90 backdrop-blur-md sticky top-0 z-40">
      {/* Zone 1: Single text element wordmark: EditorsHub */}
      <a
        href="/"
        onClick={(e) => {
          e.preventDefault();
          onSelectTab('workspace');
        }}
        className="text-lg font-bold tracking-tight text-white whitespace-nowrap shrink-0 flex items-center gap-2.5"
      >
        <span className="w-2.5 h-2.5 bg-neutral-100 rounded-sm inline-block"></span>
        <span>EditorsHub</span>
      </a>

      {/* Zone 2: 4-5 single-line clean text navigation links */}
      <nav className="hidden md:flex items-center gap-7 text-sm font-medium text-neutral-400">
        <button
          onClick={() => onSelectTab('workspace')}
          className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
            activeTab === 'workspace' ? 'text-white border-b-2 border-white pb-0.5' : ''
          }`}
        >
          Studio Enhancer
        </button>
        <button
          onClick={() => onSelectTab('audit')}
          className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
            activeTab === 'audit' ? 'text-white border-b-2 border-white pb-0.5' : ''
          }`}
        >
          Fidelity Audit
        </button>
        <button
          onClick={() => onSelectTab('colab')}
          className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
            activeTab === 'colab' ? 'text-white border-b-2 border-white pb-0.5' : ''
          }`}
        >
          Colab Bridge
        </button>
        <button
          onClick={onOpenAndroidRelease}
          className="hover:text-white transition-colors whitespace-nowrap shrink-0 flex items-center gap-1.5 text-neutral-300"
        >
          <Smartphone className="w-3.5 h-3.5 text-neutral-400" />
          <span>Android APK Release</span>
        </button>
        <a
          href="https://colab.research.google.com/drive/1k7WXSMLeeJd4i7RCxrlCl_1gM0YAVh7V?usp=sharing"
          target="_blank"
          rel="noreferrer"
          className="hover:text-white transition-colors whitespace-nowrap shrink-0 flex items-center gap-1.5"
        >
          <span>Kroteus Colab</span>
          <ExternalLink className="w-3.5 h-3.5 text-neutral-500" />
        </a>
      </nav>

      {/* Zone 3: 1 primary action */}
      <div className="flex items-center gap-3 shrink-0">
        <button
          onClick={onExportCurrent}
          disabled={isProcessing}
          className="flex items-center gap-2 px-4 py-2 text-xs font-semibold text-neutral-950 bg-white rounded-md hover:bg-neutral-200 transition-colors whitespace-nowrap shrink-0 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Export High-Res</span>
        </button>
      </div>
    </header>
  );
};
