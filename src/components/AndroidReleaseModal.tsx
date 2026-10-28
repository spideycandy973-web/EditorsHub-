import React, { useState } from 'react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { Smartphone, Github, Download, Check, Copy, ExternalLink, ShieldCheck, Terminal } from 'lucide-react';

interface AndroidReleaseModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidReleaseModal: React.FC<AndroidReleaseModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [copiedCmd, setCopiedCmd] = useState(false);
  const [installSuccess, setInstallSuccess] = useState(false);

  if (!isOpen) return null;

  const gitPushCommand = `# 1. Link your GitHub repository
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/EditorsHub.git

# 2. Push to main to trigger automatic Android APK build & GitHub Release
git branch -M main
git push -u origin main`;

  const handleCopy = () => {
    navigator.clipboard.writeText(gitPushCommand);
    setCopiedCmd(true);
    setTimeout(() => setCopiedCmd(false), 2000);
  };

  const handleInstallClick = async () => {
    const success = await install();
    if (success) {
      setInstallSuccess(true);
      setTimeout(() => setInstallSuccess(false), 3000);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl w-full max-w-2xl overflow-hidden shadow-2xl flex flex-col text-neutral-200">
        {/* Header */}
        <div className="px-6 py-4 border-b border-neutral-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Smartphone className="w-5 h-5 text-neutral-300" />
            <h3 className="text-sm font-semibold text-white tracking-wide">
              Android APK & GitHub Release Portal
            </h3>
          </div>
          <button
            onClick={onClose}
            className="text-xs text-neutral-400 hover:text-white transition-colors"
          >
            Close
          </button>
        </div>

        {/* Content */}
        <div className="p-6 flex flex-col gap-5 text-xs">
          {/* Section 1: Instant Android Install (Home screen standalone APK container) */}
          <div className="p-4 bg-neutral-950 rounded-lg border border-neutral-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <span className="font-semibold text-white text-sm block">1. Instant Android Installation</span>
              <p className="text-neutral-400 text-xs">
                {isInstalled
                  ? 'EditorsHub is currently running as an installed standalone app.'
                  : 'Install EditorsHub directly to your Android device home screen with zero configuration.'}
              </p>
            </div>

            <button
              onClick={handleInstallClick}
              disabled={isInstalled}
              className="px-4 py-2 bg-white text-neutral-950 font-semibold text-xs rounded-md hover:bg-neutral-200 transition-colors shrink-0 disabled:opacity-50 flex items-center justify-center gap-1.5"
            >
              {isInstalled ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Installed</span>
                </>
              ) : installSuccess ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                  <span>App Added</span>
                </>
              ) : (
                <>
                  <Smartphone className="w-3.5 h-3.5" />
                  <span>Install on Android</span>
                </>
              )}
            </button>
          </div>

          {/* Section 2: GitHub Actions Automated APK Builder */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white text-xs flex items-center gap-1.5">
                <Github className="w-4 h-4" />
                <span>2. Automated GitHub Actions APK Compiler</span>
              </span>
              <button
                onClick={handleCopy}
                className="text-neutral-400 hover:text-white transition-colors flex items-center gap-1"
              >
                {copiedCmd ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span className="text-emerald-400">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    <span>Copy Commands</span>
                  </>
                )}
              </button>
            </div>

            <p className="text-neutral-400 leading-relaxed">
              The project includes a ready-to-run GitHub Actions workflow at <code className="text-neutral-300 font-mono">.github/workflows/build-apk.yml</code>. 
              Push this repository to your GitHub account to compile the native Android APK and publish it to your GitHub Releases automatically.
            </p>

            <pre className="p-3.5 bg-neutral-950 border border-neutral-800 rounded-lg font-mono text-[11px] text-neutral-300 overflow-x-auto leading-relaxed">
              {gitPushCommand}
            </pre>
          </div>

          {/* Section 3: Release Features & Details */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="p-3 bg-neutral-950/60 border border-neutral-800 rounded-lg">
              <span className="font-semibold text-white block mb-1">Automated Build Steps</span>
              <p className="text-neutral-400 text-[11px]">
                GitHub Actions sets up JDK 17, installs Android SDK, builds with Capacitor, and outputs <span className="font-mono text-white">EditorsHub-v1.0.apk</span>.
              </p>
            </div>
            <div className="p-3 bg-neutral-950/60 border border-neutral-800 rounded-lg">
              <span className="font-semibold text-white block mb-1">GitHub Releases Asset</span>
              <p className="text-neutral-400 text-[11px]">
                Every git push automatically attaches the compiled APK directly under your GitHub repository's Releases page for instant distribution.
              </p>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-neutral-800 bg-neutral-950/60 flex items-center justify-between">
          <span className="text-[11px] text-neutral-500 font-mono">
            Package: com.editorshub.enhancer
          </span>
          <button
            onClick={onClose}
            className="px-4 py-1.5 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 text-xs font-medium rounded transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
