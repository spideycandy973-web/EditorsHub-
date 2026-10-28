import React from 'react';
import { ProcessingStatus } from '../types/enhancer';
import { Play, Pause, XCircle, Download, CheckCircle, RefreshCw } from 'lucide-react';

interface VideoBatchProgressProps {
  status: ProcessingStatus;
  currentPreviewCanvas?: HTMLCanvasElement | null;
  onPauseToggle: () => void;
  onAbort: () => void;
  onDownload: () => void;
  downloadUrl?: string | null;
}

export const VideoBatchProgress: React.FC<VideoBatchProgressProps> = ({
  status,
  currentPreviewCanvas,
  onPauseToggle,
  onAbort,
  onDownload,
  downloadUrl,
}) => {
  const pct = status.totalFrames > 0 
    ? Math.min(100, (status.currentFrame / status.totalFrames) * 100) 
    : 0;

  const formatTime = (ms: number) => {
    const totalSecs = Math.max(0, Math.floor(ms / 1000));
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const previewDataUrl = currentPreviewCanvas ? currentPreviewCanvas.toDataURL('image/jpeg', 0.8) : null;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl w-full max-w-2xl overflow-hidden shadow-2xl flex flex-col text-neutral-200">
        {/* Header */}
        <div className="px-6 py-4 border-b border-neutral-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-neutral-200"></span>
            <h3 className="text-sm font-semibold text-white tracking-wide">
              {status.stage === 'completed'
                ? 'Super-Resolution Synthesis Completed'
                : 'Frame-by-Frame Super-Resolution Engine'}
            </h3>
          </div>
          <span className="text-xs text-neutral-400 font-mono tabular-nums">
            {status.stage.toUpperCase()}
          </span>
        </div>

        {/* Content Body */}
        <div className="p-6 flex flex-col gap-6">
          {/* Live Frame Preview Monitor */}
          {previewDataUrl && (
            <div className="relative w-full h-48 bg-neutral-950 rounded-lg border border-neutral-800 overflow-hidden flex items-center justify-center">
              <img
                src={previewDataUrl}
                alt="Active rendering frame"
                className="max-h-full max-w-full object-contain"
                referrerPolicy="no-referrer"
              />
              <div className="absolute bottom-2 left-2 px-2 py-1 bg-black/70 backdrop-blur-sm rounded text-[11px] font-mono text-neutral-300 border border-white/10">
                Frame {status.currentFrame} / {status.totalFrames} · Live Render Buffer
              </div>
            </div>
          )}

          {/* Progress bar */}
          <div className="space-y-2">
            <div className="flex justify-between items-center text-xs">
              <span className="text-neutral-300 font-medium">Processing Progress</span>
              <span className="font-mono tabular-nums text-white font-semibold">
                {status.currentFrame} / {status.totalFrames} ({pct.toFixed(1)}%)
              </span>
            </div>
            <div className="w-full bg-neutral-800 h-2.5 rounded-full overflow-hidden">
              <div
                className="bg-white h-full transition-all duration-150 ease-out"
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>

          {/* Data Grid / Telemetry Matrix */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-neutral-950/70 border border-neutral-800 rounded-lg text-xs">
            <div>
              <span className="text-neutral-500 block text-[11px]">Processing Speed</span>
              <span className="font-mono font-medium text-white tabular-nums">
                {status.fps} FPS
              </span>
            </div>

            <div>
              <span className="text-neutral-500 block text-[11px]">Elapsed Time</span>
              <span className="font-mono font-medium text-neutral-300 tabular-nums">
                {formatTime(status.elapsedMs)}
              </span>
            </div>

            <div>
              <span className="text-neutral-500 block text-[11px]">Est. Remaining</span>
              <span className="font-mono font-medium text-neutral-300 tabular-nums">
                {status.stage === 'completed' ? '00:00' : formatTime(status.estimatedRemainingMs)}
              </span>
            </div>

            <div>
              <span className="text-neutral-500 block text-[11px]">Physical Pixels Added</span>
              <span className="font-mono font-medium text-white tabular-nums truncate block">
                +{status.addedPixelCount.toLocaleString()}
              </span>
            </div>
          </div>

          {/* Resolution transition notice */}
          <div className="text-xs text-neutral-400 bg-neutral-950/40 p-3 rounded-lg border border-neutral-800/80">
            <span className="font-medium text-neutral-300">Authentic Synthesis: </span>
            Every frame is physically resampled to{' '}
            <span className="text-white font-mono">{status.enhancedResolution.width}×{status.enhancedResolution.height}</span>{' '}
            with Catmull-Rom sinc interpolation, deconvolution deblur, and ringing suppression. No fake frame blending or artificial motion interpolation.
          </div>
        </div>

        {/* Footer Actions */}
        <div className="px-6 py-4 border-t border-neutral-800 bg-neutral-950/40 flex items-center justify-between">
          {status.stage === 'completed' ? (
            <div className="flex items-center justify-between w-full">
              <div className="flex items-center gap-2 text-xs text-emerald-400">
                <CheckCircle className="w-4 h-4" />
                <span>All frames successfully super-resolved and encoded.</span>
              </div>
              <button
                onClick={onDownload}
                className="flex items-center gap-2 px-5 py-2.5 bg-white text-neutral-950 font-semibold text-xs rounded-md hover:bg-neutral-200 transition-colors shadow-sm"
              >
                <Download className="w-4 h-4" />
                <span>Download Enhanced Master Video</span>
              </button>
            </div>
          ) : (
            <div className="flex items-center justify-between w-full">
              <button
                onClick={onAbort}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-neutral-400 hover:text-white transition-colors"
              >
                <XCircle className="w-4 h-4" />
                <span>Cancel Processing</span>
              </button>

              <button
                onClick={onPauseToggle}
                className="flex items-center gap-2 px-4 py-2 bg-neutral-800 hover:bg-neutral-700 text-white text-xs font-medium rounded-md transition-colors"
              >
                {status.isPaused ? (
                  <>
                    <Play className="w-3.5 h-3.5 fill-white" />
                    <span>Resume</span>
                  </>
                ) : (
                  <>
                    <Pause className="w-3.5 h-3.5" />
                    <span>Pause</span>
                  </>
                )}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
