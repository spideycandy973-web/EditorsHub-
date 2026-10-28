import React, { useState } from 'react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { Smartphone, Github, Check, Copy, Terminal, ExternalLink, Sparkles } from 'lucide-react';

interface AndroidReleaseModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidReleaseModal: React.FC<AndroidReleaseModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { isInstalled, install } = usePWAInstall();
  const [repoUrl, setRepoUrl] = useState('https://github.com/spideycandy/EditorsHub.git');
  const [copiedCmd, setCopiedCmd] = useState(false);
  const [installSuccess, setInstallSuccess] = useState(false);

  if (!isOpen) return null;

  const formattedRepoUrl = repoUrl.trim()
    ? repoUrl.trim().endsWith('.git')
      ? repoUrl.trim()
      : `${repoUrl.trim()}.git`
    : 'https://github.com/YOUR_USERNAME/YOUR_REPO.git';

  const gitPushCommand = `# 1. Link your GitHub repository
git remote add origin ${formattedRepoUrl}

# 2. Push to main branch to trigger the automated Android APK compiler
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
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl w-full max-w-2xl overflow-hidden shadow-2xl flex flex-col text-neutral-200 max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-neutral-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Github className="w-5 h-5 text-white" />
            <h3 className="text-sm font-semibold text-white tracking-wide">
              Release APK to Your GitHub Repository
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
        <div className="p-6 flex flex-col gap-5 text-xs overflow-y-auto">
          {/* Section 1: Instant PWA Install on Android */}
          <div className="p-4 bg-neutral-950 rounded-lg border border-neutral-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <span className="font-semibold text-white text-sm flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-emerald-400" />
                <span>Instant Android App Installation (PWA)</span>
              </span>
              <p className="text-neutral-400 text-xs">
                {isInstalled
                  ? 'EditorsHub is already installed on this device.'
                  : 'Install EditorsHub directly to your phone’s home screen as a standalone Android app right now.'}
              </p>
            </div>

            <button
              onClick={handleInstallClick}
              disabled={isInstalled}
              className="px-4 py-2 bg-white text-neutral-950 font-semibold text-xs rounded-md hover:bg-neutral-200 transition-colors shrink-0 disabled:opacity-50 flex items-center justify-center gap-1.5"
            >
              {isInstalled || installSuccess ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Installed</span>
                </>
              ) : (
                <>
                  <Smartphone className="w-3.5 h-3.5" />
                  <span>Install on Android</span>
                </>
              )}
            </button>
          </div>

          {/* Section 2: GitHub Repository Release Configuration */}
          <div className="space-y-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-white flex items-center gap-1.5">
                <Terminal className="w-4 h-4 text-neutral-400" />
                <span>Your GitHub Repository URL:</span>
              </label>
              <input
                type="text"
                value={repoUrl}
                onChange={(e) => setRepoUrl(e.target.value)}
                placeholder="https://github.com/spideycandy/EditorsHub.git"
                className="w-full px-3 py-2 bg-neutral-950 border border-neutral-800 rounded-md text-white font-mono text-xs focus:outline-none focus:border-white transition-colors"
              />
              <span className="text-[11px] text-neutral-400">
                Enter your repository URL to generate the push commands.
              </span>
            </div>

            <div className="flex items-center justify-between pt-1">
              <span className="font-semibold text-white text-xs">
                Push Commands (Automated GitHub Actions Build):
              </span>
              <button
                onClick={handleCopy}
                className="text-neutral-400 hover:text-white transition-colors flex items-center gap-1 px-2.5 py-1 bg-neutral-800 rounded text-xs"
              >
                {copiedCmd ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span className="text-emerald-400 font-medium">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    <span>Copy Commands</span>
                  </>
                )}
              </button>
            </div>

            <pre className="p-3.5 bg-neutral-950 border border-neutral-800 rounded-lg font-mono text-[11px] text-neutral-300 overflow-x-auto leading-relaxed">
              {gitPushCommand}
            </pre>
          </div>

          {/* Section 3: How the Automated Build & Release Works */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="p-3 bg-neutral-950/60 border border-neutral-800 rounded-lg space-y-1">
              <span className="font-semibold text-white block">1. Automated GitHub Workflow</span>
              <p className="text-neutral-400 text-[11px] leading-relaxed">
                The repository includes <code className="text-neutral-300 font-mono">.github/workflows/build-apk.yml</code>. 
                When you push to <span className="font-mono text-white">main</span>, GitHub Actions runs JDK 17 & Android SDK, compiling <span className="text-white font-mono">EditorsHub-v1.0.apk</span>.
              </p>
            </div>
            <div className="p-3 bg-neutral-950/60 border border-neutral-800 rounded-lg space-y-1">
              <span className="font-semibold text-white block">2. Published to GitHub Releases</span>
              <p className="text-neutral-400 text-[11px] leading-relaxed">
                The workflow automatically creates a new Release under your repository and publishes the APK as a downloadable asset ready to install on any Android phone.
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
