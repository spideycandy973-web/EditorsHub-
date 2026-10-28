import React from 'react';
import { EnhancerSettings, ModelPreset, QualityAnalysisResult } from '../types/enhancer';
import { Sliders, Sparkles, RefreshCw, Wand2, ShieldAlert, CheckCircle2 } from 'lucide-react';

interface EnhancerControlsProps {
  settings: EnhancerSettings;
  onChangeSettings: (newSettings: EnhancerSettings) => void;
  onApplyPreset: (preset: ModelPreset) => void;
  isProcessing: boolean;
  onRunEnhance: () => void;
  mediaType: 'image' | 'video';
  qualityResult?: QualityAnalysisResult | null;
  onAnalyzeQuality: () => void;
  onApplySuggestedQuality: () => void;
}

export const EnhancerControls: React.FC<EnhancerControlsProps> = ({
  settings,
  onChangeSettings,
  onApplyPreset,
  isProcessing,
  onRunEnhance,
  mediaType,
  qualityResult,
  onAnalyzeQuality,
  onApplySuggestedQuality,
}) => {
  const updateSetting = <K extends keyof EnhancerSettings>(key: K, value: EnhancerSettings[K]) => {
    onChangeSettings({
      ...settings,
      [key]: value,
    });
  };

  return (
    <div className="w-full lg:w-84 shrink-0 bg-neutral-900/60 border border-neutral-800 rounded-lg p-4 flex flex-col gap-5 text-neutral-200">
      {/* Title & Reset */}
      <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
        <div className="flex items-center gap-2">
          <Sliders className="w-4 h-4 text-neutral-300" />
          <h2 className="text-sm font-semibold text-white tracking-wide">Kroteus Pipeline Engine</h2>
        </div>
        <button
          onClick={() => onApplyPreset(settings.modelPreset)}
          className="text-xs text-neutral-400 hover:text-white transition-colors flex items-center gap-1"
          title="Reset sliders to preset defaults"
        >
          <RefreshCw className="w-3 h-3" />
          <span>Reset</span>
        </button>
      </div>

      {/* Auto Quality Analyzer & Preset Suggestion Card */}
      <div className="p-3.5 bg-neutral-950/70 border border-neutral-800 rounded-lg flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-white flex items-center gap-1.5">
            <Wand2 className="w-3.5 h-3.5 text-neutral-300" />
            <span>Automatic Quality Analysis</span>
          </span>
          <button
            onClick={onAnalyzeQuality}
            className="text-[11px] text-neutral-400 hover:text-white transition-colors underline"
          >
            Re-analyze
          </button>
        </div>

        {qualityResult ? (
          <div className="space-y-2">
            {/* Real metrics (noise level & sharpness) */}
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="p-2 bg-neutral-900/70 rounded border border-neutral-800">
                <span className="text-[11px] text-neutral-500 block">Noise Level</span>
                <span className="font-mono text-white tabular-nums font-medium">
                  {qualityResult.noiseLevel}% <span className="text-neutral-400 text-[10px]">({qualityResult.noiseCategory})</span>
                </span>
              </div>
              <div className="p-2 bg-neutral-900/70 rounded border border-neutral-800">
                <span className="text-[11px] text-neutral-500 block">Sharpness</span>
                <span className="font-mono text-white tabular-nums font-medium">
                  {qualityResult.sharpnessScore}% <span className="text-neutral-400 text-[10px]">({qualityResult.sharpnessCategory})</span>
                </span>
              </div>
            </div>

            {/* Suggested Preset callout */}
            <div className="p-2.5 bg-neutral-900/90 rounded border border-neutral-700/80 flex flex-col gap-1.5">
              <div className="flex items-center justify-between text-xs">
                <span className="text-neutral-400">Suggested Optimal Preset:</span>
                <span className="font-mono font-semibold text-white text-[11px] bg-neutral-800 px-1.5 py-0.5 rounded">
                  {qualityResult.suggestedPreset}
                </span>
              </div>
              <p className="text-[11px] text-neutral-400 leading-snug">
                {qualityResult.reason}
              </p>
              <button
                onClick={onApplySuggestedQuality}
                className="mt-1 w-full py-1.5 px-2 bg-neutral-200 hover:bg-white text-neutral-950 font-semibold text-[11px] rounded transition-colors flex items-center justify-center gap-1.5"
              >
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>Apply Optimal Suggestion</span>
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={onAnalyzeQuality}
            className="w-full py-2 px-3 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 text-xs font-medium rounded transition-colors flex items-center justify-center gap-1.5"
          >
            <Wand2 className="w-3.5 h-3.5" />
            <span>Analyze Noise & Sharpness</span>
          </button>
        )}
      </div>

      {/* Model Preset Selector */}
      <div className="space-y-2">
        <label className="text-xs font-medium text-neutral-400 block">Neural Model Architecture</label>
        <div className="grid grid-cols-1 gap-1.5">
          <button
            onClick={() => onApplyPreset('kroteus-real-clarity')}
            className={`p-2.5 text-left rounded-md text-xs font-medium border transition-colors flex flex-col gap-0.5 ${
              settings.modelPreset === 'kroteus-real-clarity'
                ? 'bg-neutral-800 border-neutral-600 text-white shadow-sm'
                : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Kroteus Real-Clarity v15.5</span>
              {qualityResult?.suggestedPreset === 'kroteus-real-clarity' && (
                <span className="text-[10px] text-emerald-400 font-mono">Suggested</span>
              )}
            </div>
            <span className="text-[11px] text-neutral-400">Natural textures, real pores, optical deblur</span>
          </button>

          <button
            onClick={() => onApplyPreset('denoise-heavy')}
            className={`p-2.5 text-left rounded-md text-xs font-medium border transition-colors flex flex-col gap-0.5 ${
              settings.modelPreset === 'denoise-heavy'
                ? 'bg-neutral-800 border-neutral-600 text-white shadow-sm'
                : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Denoise-Heavy (Low-Light / Grain)</span>
              {qualityResult?.suggestedPreset === 'denoise-heavy' && (
                <span className="text-[10px] text-emerald-400 font-mono">Suggested</span>
              )}
            </div>
            <span className="text-[11px] text-neutral-400">Bilateral noise suppression, zero grain amplification</span>
          </button>

          <button
            onClick={() => onApplyPreset('compact-ultrasharp')}
            className={`p-2.5 text-left rounded-md text-xs font-medium border transition-colors flex flex-col gap-0.5 ${
              settings.modelPreset === 'compact-ultrasharp'
                ? 'bg-neutral-800 border-neutral-600 text-white shadow-sm'
                : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Compact 4x-UltraSharp</span>
              {qualityResult?.suggestedPreset === 'compact-ultrasharp' && (
                <span className="text-[10px] text-emerald-400 font-mono">Suggested</span>
              )}
            </div>
            <span className="text-[11px] text-neutral-400">Extreme micro-edge recovery for cars & architecture</span>
          </button>

          <button
            onClick={() => onApplyPreset('realesr-animevideov3')}
            className={`p-2.5 text-left rounded-md text-xs font-medium border transition-colors flex flex-col gap-0.5 ${
              settings.modelPreset === 'realesr-animevideov3'
                ? 'bg-neutral-800 border-neutral-600 text-white shadow-sm'
                : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Real-ESRGAN AnimeVideoV3</span>
              {qualityResult?.suggestedPreset === 'realesr-animevideov3' && (
                <span className="text-[10px] text-emerald-400 font-mono">Suggested</span>
              )}
            </div>
            <span className="text-[11px] text-neutral-400">Crisp vector-grade ink lines, zero ringing</span>
          </button>

          <button
            onClick={() => onApplyPreset('cinematic-motion')}
            className={`p-2.5 text-left rounded-md text-xs font-medium border transition-colors flex flex-col gap-0.5 ${
              settings.modelPreset === 'cinematic-motion'
                ? 'bg-neutral-800 border-neutral-600 text-white shadow-sm'
                : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:border-neutral-700 hover:text-neutral-200'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Cinematic Motion SR</span>
              {qualityResult?.suggestedPreset === 'cinematic-motion' && (
                <span className="text-[10px] text-emerald-400 font-mono">Suggested</span>
              )}
            </div>
            <span className="text-[11px] text-neutral-400">Temporal coherence and 35mm grain preservation</span>
          </button>
        </div>
      </div>

      {/* Scale Factor Selector */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs">
          <label className="font-medium text-neutral-400">Super-Resolution Multiplier</label>
          <span className="text-neutral-400 font-mono text-[11px]">
            {settings.scaleFactor === 2 ? '4× Real Pixels' : '16× Real Pixels'}
          </span>
        </div>
        <div className="grid grid-cols-2 gap-2 p-1 bg-neutral-950/60 rounded-md border border-neutral-800">
          <button
            onClick={() => updateSetting('scaleFactor', 2)}
            className={`py-1.5 text-xs font-medium rounded transition-colors ${
              settings.scaleFactor === 2
                ? 'bg-neutral-800 text-white shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            2× Super-Res
          </button>
          <button
            onClick={() => updateSetting('scaleFactor', 4)}
            className={`py-1.5 text-xs font-medium rounded transition-colors ${
              settings.scaleFactor === 4
                ? 'bg-neutral-800 text-white shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            4× Super-Res (4K/8K)
          </button>
        </div>
      </div>

      {/* Kroteus Fine-Tuning Sliders */}
      <div className="space-y-4 pt-2 border-t border-neutral-800">
        <div className="text-xs font-medium text-neutral-400 flex items-center justify-between">
          <span>Kroteus Parameter Sliders</span>
          <span className="text-[11px] text-neutral-400">Authentic Synthesis</span>
        </div>

        {/* Deblur Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">Deblur (PSF Inversion)</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.deblur}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.deblur}
            onChange={(e) => updateSetting('deblur', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Anti-Alias Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">Anti-Alias (Directional Edge)</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.antiAlias}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.antiAlias}
            onChange={(e) => updateSetting('antiAlias', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* De-Halo Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">De-Halo (Ringing Suppressor)</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.dehalo}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.dehalo}
            onChange={(e) => updateSetting('dehalo', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Noise Reduction Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">Noise Reduction (Bilateral)</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.noiseReduction}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.noiseReduction}
            onChange={(e) => updateSetting('noiseReduction', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Detail Recovery Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">Detail Recovery (Micro-Textures)</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.detailRecovery}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.detailRecovery}
            onChange={(e) => updateSetting('detailRecovery', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Color Clarity Slider */}
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs">
            <span className="text-neutral-300">Chroma Dynamic Range</span>
            <span className="font-mono text-neutral-400 tabular-nums">{settings.colorClarity}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={settings.colorClarity}
            onChange={(e) => updateSetting('colorClarity', parseInt(e.target.value, 10))}
            className="w-full accent-neutral-200 bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Organic 35mm Grain Toggle */}
        <div className="flex items-center justify-between pt-1">
          <span className="text-xs text-neutral-300">Preserve 35mm Film Grain</span>
          <button
            type="button"
            role="switch"
            aria-checked={settings.preserveGrain}
            onClick={() => updateSetting('preserveGrain', !settings.preserveGrain)}
            className={`w-9 h-5 rounded-full transition-colors relative flex items-center p-0.5 ${
              settings.preserveGrain ? 'bg-neutral-200' : 'bg-neutral-800'
            }`}
          >
            <span
              className={`w-4 h-4 rounded-full transition-transform bg-neutral-950 ${
                settings.preserveGrain ? 'translate-x-4' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      </div>

      {/* Main Process Action Button */}
      <button
        onClick={onRunEnhance}
        disabled={isProcessing}
        className="w-full mt-2 py-3 px-4 bg-white hover:bg-neutral-200 text-neutral-950 text-xs font-semibold rounded-md transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
      >
        <Sparkles className="w-4 h-4" />
        <span>
          {mediaType === 'video'
            ? 'Process Video Frame-by-Frame'
            : 'Synthesize Super-Resolution Pixels'}
        </span>
      </button>
    </div>
  );
};
