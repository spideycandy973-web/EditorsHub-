import React, { useState } from 'react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { Smartphone, Github, Check, Copy, Terminal, ExternalLink, Loader2, ArrowRight, ShieldCheck, AlertCircle } from 'lucide-react';

interface AndroidReleaseModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidReleaseModal: React.FC<AndroidReleaseModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { isInstalled, install } = usePWAInstall();
  const [repoUrl, setRepoUrl] = useState('https://github.com/spideycandy973-web/EditorsHub-.git');
  const [githubToken, setGithubToken] = useState('');
  const [isPushing, setIsPushing] = useState(false);
  const [pushStatus, setPushStatus] = useState<{
    success?: boolean;
    message?: string;
    actionsUrl?: string;
    releasesUrl?: string;
  } | null>(null);

  const [copiedCmd, setCopiedCmd] = useState(false);
  const [installSuccess, setInstallSuccess] = useState(false);

  if (!isOpen) return null;

  const formattedRepoUrl = repoUrl.trim() || 'https://github.com/spideycandy973-web/EditorsHub-.git';

  const gitPushCommand = `# Pull and link the complete codebase & Android native project
git remote set-url origin ${formattedRepoUrl}
git push -u origin main --force`;

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

  const handleTriggerPush = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!githubToken.trim()) return;

    setIsPushing(true);
    setPushStatus(null);

    try {
      const res = await fetch('/api/github/push-release', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repoUrl: formattedRepoUrl,
          token: githubToken.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || 'Failed to push to GitHub repository');
      }

      setPushStatus({
        success: true,
        message: data.message,
        actionsUrl: data.actionsUrl,
        releasesUrl: data.releasesUrl,
      });
    } catch (err: unknown) {
      setPushStatus({
        success: false,
        message: err instanceof Error ? err.message : 'Unknown push error',
      });
    } finally {
      setIsPushing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl w-full max-w-2xl overflow-hidden shadow-2xl flex flex-col text-neutral-200 max-h-[92vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-neutral-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Github className="w-5 h-5 text-white" />
            <h3 className="text-sm font-semibold text-white tracking-wide">
              Release EditorsHub APK to Your GitHub Repository
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
          {/* Section 1: 1-Click Push via GitHub Personal Access Token */}
          <div className="p-4 bg-neutral-950 rounded-lg border border-neutral-800 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white text-xs flex items-center gap-2">
                <Github className="w-4 h-4 text-white" />
                <span>One-Click Push & Trigger APK Compiler</span>
              </span>
              <a
                href="https://github.com/settings/tokens/new?scopes=repo,workflow&description=EditorsHub_APK_Release"
                target="_blank"
                rel="noreferrer"
                className="text-[11px] text-neutral-400 hover:text-white underline flex items-center gap-1"
              >
                <span>Generate GitHub Token</span>
                <ExternalLink className="w-3 h-3" />
              </a>
            </div>

            <form onSubmit={handleTriggerPush} className="flex flex-col gap-2.5">
              <div>
                <label className="text-[11px] text-neutral-400 block mb-1">
                  Target Repository:
                </label>
                <input
                  type="text"
                  value={repoUrl}
                  onChange={(e) => setRepoUrl(e.target.value)}
                  className="w-full px-3 py-1.5 bg-neutral-900 border border-neutral-800 rounded text-neutral-200 font-mono text-xs focus:outline-none focus:border-white"
                />
              </div>

              <div>
                <label className="text-[11px] text-neutral-400 block mb-1">
                  GitHub Personal Access Token (PAT with <code className="text-neutral-300">repo</code> & <code className="text-neutral-300">workflow</code> permissions):
                </label>
                <input
                  type="password"
                  value={githubToken}
                  onChange={(e) => setGithubToken(e.target.value)}
                  placeholder="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                  className="w-full px-3 py-1.5 bg-neutral-900 border border-neutral-800 rounded text-white font-mono text-xs focus:outline-none focus:border-white"
                />
              </div>

              <button
                type="submit"
                disabled={isPushing || !githubToken.trim()}
                className="mt-1 w-full py-2 bg-white text-neutral-950 font-semibold text-xs rounded-md hover:bg-neutral-200 transition-colors disabled:opacity-40 flex items-center justify-center gap-2"
              >
                {isPushing ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Pushing Code, Android Project & Triggering APK Build...</span>
                  </>
                ) : (
                  <>
                    <ArrowRight className="w-4 h-4" />
                    <span>Push to Repository & Build EditorsHub-v1.0.apk</span>
                  </>
                )}
              </button>
            </form>

            {/* Live Push Result */}
            {pushStatus && (
              <div
                className={`p-3 rounded-lg border text-xs flex flex-col gap-2 ${
                  pushStatus.success
                    ? 'bg-emerald-950/40 border-emerald-800/80 text-emerald-200'
                    : 'bg-rose-950/40 border-rose-800/80 text-rose-200'
                }`}
              >
                <div className="flex items-start gap-2">
                  {pushStatus.success ? (
                    <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                  )}
                  <span className="font-medium leading-relaxed">{pushStatus.message}</span>
                </div>

                {pushStatus.success && pushStatus.actionsUrl && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    <a
                      href={pushStatus.actionsUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="px-2.5 py-1 bg-emerald-900/60 hover:bg-emerald-800/80 text-white rounded text-[11px] font-medium flex items-center gap-1 transition-colors"
                    >
                      <span>1. Watch Live APK Build (GitHub Actions)</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                    {pushStatus.releasesUrl && (
                      <a
                        href={pushStatus.releasesUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="px-2.5 py-1 bg-neutral-800 hover:bg-neutral-700 text-white rounded text-[11px] font-medium flex items-center gap-1 transition-colors"
                      >
                        <span>2. Download APK (GitHub Releases)</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Section 2: Instant PWA Install on Android */}
          <div className="p-4 bg-neutral-950 rounded-lg border border-neutral-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <span className="font-semibold text-white text-xs flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-emerald-400" />
                <span>Instant Android Install (Run Right on Your Phone)</span>
              </span>
              <p className="text-neutral-400 text-xs">
                {isInstalled
                  ? 'EditorsHub is already installed on your device.'
                  : 'Install EditorsHub directly to your phone screen right now with offline hardware acceleration.'}
              </p>
            </div>

            <button
              onClick={handleInstallClick}
              disabled={isInstalled}
              className="px-4 py-2 bg-neutral-800 text-neutral-200 font-semibold text-xs rounded-md hover:bg-neutral-700 transition-colors shrink-0 disabled:opacity-50 flex items-center justify-center gap-1.5"
            >
              {isInstalled || installSuccess ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Installed</span>
                </>
              ) : (
                <>
                  <Smartphone className="w-3.5 h-3.5" />
                  <span>Install Web App</span>
                </>
              )}
            </button>
          </div>

          {/* Section 3: Alternative Manual Terminal Commands */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white text-xs flex items-center gap-1.5">
                <Terminal className="w-4 h-4 text-neutral-400" />
                <span>Manual Push Commands (Terminal Alternative):</span>
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

            <pre className="p-3 bg-neutral-950 border border-neutral-800 rounded-lg font-mono text-[11px] text-neutral-300 overflow-x-auto leading-relaxed">
              {gitPushCommand}
            </pre>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-neutral-800 bg-neutral-950/60 flex items-center justify-between">
          <span className="text-[11px] text-neutral-500 font-mono">
            Package: com.editorshub.enhancer · Branch: main
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

