import React, { useState } from 'react';
import { EnhancerSettings, FidelityAudit } from '../types/enhancer';
import { Sparkles, Check, ArrowRight, ShieldCheck, AlertCircle, RefreshCw } from 'lucide-react';

interface FidelityAuditPanelProps {
  currentCanvas: HTMLCanvasElement | null;
  onApplySettings: (settings: Partial<EnhancerSettings>) => void;
}

export const FidelityAuditPanel: React.FC<FidelityAuditPanelProps> = ({
  currentCanvas,
  onApplySettings,
}) => {
  const [loading, setLoading] = useState(false);
  const [auditResult, setAuditResult] = useState<FidelityAudit | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);

  const runFidelityAnalysis = async () => {
    if (!currentCanvas) return;
    setLoading(true);
    setError(null);
    setApplied(false);

    try {
      const imageBase64 = currentCanvas.toDataURL('image/jpeg', 0.85);

      const response = await fetch('/api/gemini/analyze-fidelity', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          imageBase64,
          mimeType: 'image/jpeg',
          mediaType: 'frame',
        }),
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }

      const data: FidelityAudit = await response.json();
      setAuditResult(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Analysis failed');
    } finally {
      setLoading(false);
    }
  };

  const handleApply = () => {
    if (auditResult?.recommendedSettings) {
      onApplySettings(auditResult.recommendedSettings);
      setApplied(true);
      setTimeout(() => setApplied(false), 2500);
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto p-6 bg-neutral-900/60 border border-neutral-800 rounded-xl my-4 text-neutral-200 flex flex-col gap-6">
      {/* Title */}
      <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
        <div>
          <h2 className="text-base font-semibold text-white">Neural Fidelity & Artifact Audit</h2>
          <p className="text-xs text-neutral-400 mt-0.5">
            Evaluates underlying optical clarity, compression macroblocking, and micro-textures to recommend optimal super-resolution parameters.
          </p>
        </div>

        <button
          onClick={runFidelityAnalysis}
          disabled={loading || !currentCanvas}
          className="flex items-center gap-2 px-4 py-2 bg-white text-neutral-950 font-semibold text-xs rounded-md hover:bg-neutral-200 transition-colors disabled:opacity-50"
        >
          {loading ? (
            <>
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              <span>Analyzing Frame...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5" />
              <span>Run Forensic Analysis</span>
            </>
          )}
        </button>
      </div>

      {error && (
        <div className="p-3 bg-red-950/40 border border-red-800 rounded-lg text-xs text-red-200 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {!auditResult && !loading && (
        <div className="py-12 flex flex-col items-center justify-center text-center gap-3 text-neutral-500">
          <ShieldCheck className="w-10 h-10 text-neutral-600" />
          <p className="text-xs max-w-md">
            Click <strong>Run Forensic Analysis</strong> above to examine the current frame for compression noise, motion blur, and optimal Kroteus settings.
          </p>
        </div>
      )}

      {auditResult && (
        <div className="flex flex-col gap-6">
          {/* Key Metrics Grid */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="p-3 bg-neutral-950 border border-neutral-800 rounded-lg">
              <span className="text-[11px] text-neutral-500 block uppercase font-mono">Classified Type</span>
              <span className="font-semibold text-white text-xs mt-0.5 block capitalize">
                {auditResult.detectedType.replace(/_/g, ' ')}
              </span>
            </div>

            <div className="p-3 bg-neutral-950 border border-neutral-800 rounded-lg">
              <span className="text-[11px] text-neutral-500 block uppercase font-mono">Native Resolution</span>
              <span className="font-semibold text-white text-xs mt-0.5 block">
                {auditResult.estimatedNativeResolution}
              </span>
            </div>

            <div className="p-3 bg-neutral-950 border border-neutral-800 rounded-lg">
              <span className="text-[11px] text-neutral-500 block uppercase font-mono">Compression Severity</span>
              <span className="font-semibold text-white text-xs mt-0.5 font-mono tabular-nums block">
                {auditResult.compressionSeverity} / 10
              </span>
            </div>

            <div className="p-3 bg-neutral-950 border border-neutral-800 rounded-lg">
              <span className="text-[11px] text-neutral-500 block uppercase font-mono">Blur Level</span>
              <span className="font-semibold text-white text-xs mt-0.5 font-mono tabular-nums block">
                {auditResult.blurLevel} / 10
              </span>
            </div>
          </div>

          {/* Technical Observations */}
          <div className="space-y-2">
            <span className="text-xs font-medium text-neutral-300 block">Forensic Observations:</span>
            <ul className="space-y-1.5">
              {auditResult.fidelityObservations.map((obs, idx) => (
                <li key={idx} className="text-xs text-neutral-400 flex items-start gap-2">
                  <span className="text-neutral-500" aria-hidden="true">·</span>
                  <span>{obs}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Recommended Parameters Box */}
          <div className="p-4 bg-neutral-950 border border-neutral-800 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <span className="text-xs font-semibold text-white block">Recommended Kroteus Parameters:</span>
              <div className="flex flex-wrap items-center gap-3 text-xs text-neutral-400 font-mono tabular-nums">
                <span>Model: <strong className="text-white">{auditResult.recommendedSettings.modelPreset}</strong></span>
                <span aria-hidden="true">·</span>
                <span>Scale: <strong className="text-white">{auditResult.recommendedSettings.scaleFactor}×</strong></span>
                <span aria-hidden="true">·</span>
                <span>Deblur: <strong className="text-white">{auditResult.recommendedSettings.deblur}%</strong></span>
                <span aria-hidden="true">·</span>
                <span>Anti-Alias: <strong className="text-white">{auditResult.recommendedSettings.antiAlias}%</strong></span>
                <span aria-hidden="true">·</span>
                <span>De-Halo: <strong className="text-white">{auditResult.recommendedSettings.dehalo}%</strong></span>
              </div>
            </div>

            <button
              onClick={handleApply}
              className="px-4 py-2 bg-neutral-200 text-neutral-950 font-semibold text-xs rounded hover:bg-white transition-colors shrink-0 flex items-center gap-1.5"
            >
              {applied ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                  <span>Settings Applied</span>
                </>
              ) : (
                <>
                  <span>Apply Recommended</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
