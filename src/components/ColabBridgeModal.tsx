import React, { useState } from 'react';
import { EnhancerSettings } from '../types/enhancer';
import { ExternalLink, Copy, Check, Terminal, Cpu } from 'lucide-react';

interface ColabBridgeModalProps {
  settings: EnhancerSettings;
  onClose?: () => void;
  isInline?: boolean;
}

export const ColabBridgeModal: React.FC<ColabBridgeModalProps> = ({
  settings,
  onClose,
  isInline = false,
}) => {
  const [copied, setCopied] = useState(false);
  const colabUrl = 'https://colab.research.google.com/drive/1k7WXSMLeeJd4i7RCxrlCl_1gM0YAVh7V?usp=sharing';

  const pythonCommand = `!python enhance.py \\
  --model "${settings.modelPreset}" \\
  --scale ${settings.scaleFactor} \\
  --deblur ${(settings.deblur / 100).toFixed(2)} \\
  --anti_alias ${(settings.antiAlias / 100).toFixed(2)} \\
  --dehalo ${(settings.dehalo / 100).toFixed(2)} \\
  --noise_reduction ${(settings.noiseReduction / 100).toFixed(2)} \\
  --detail_recovery ${(settings.detailRecovery / 100).toFixed(2)} \\
  --preserve_grain ${settings.preserveGrain ? 'True' : 'False'} \\
  --input "/content/drive/MyDrive/input_video.mp4" \\
  --output "/content/drive/MyDrive/enhanced_4k.mp4"`;

  const handleCopy = () => {
    navigator.clipboard.writeText(pythonCommand);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const content = (
    <div className="flex flex-col gap-6 text-neutral-200">
      {/* Intro */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <Cpu className="w-5 h-5 text-neutral-300" />
          <h3 className="text-base font-semibold text-white">
            Kroteus v15.5 Google Colab Bridge
          </h3>
        </div>
        <p className="text-xs text-neutral-400 leading-relaxed">
          For large multi-gigabyte video renders at 4K or 8K 60FPS requiring high-VRAM NVIDIA T4/A100 GPUs, 
          you can execute this exact tuned pipeline on the official Kroteus Google Colab notebook with one click.
        </p>
      </div>

      {/* Target Colab Link */}
      <div className="p-4 bg-neutral-950 rounded-lg border border-neutral-800 flex items-center justify-between gap-4">
        <div className="min-w-0">
          <span className="text-[11px] text-neutral-500 block uppercase font-mono tracking-wider">
            Target Notebook
          </span>
          <span className="text-xs font-mono text-white truncate block">
            colab.research.google.com/drive/1k7WXSMLeeJd4i7RCxrlCl_1gM0YAVh7V
          </span>
        </div>
        <a
          href={colabUrl}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-1.5 px-3 py-1.5 bg-neutral-200 text-neutral-950 font-semibold text-xs rounded hover:bg-white transition-colors shrink-0"
        >
          <span>Open Colab</span>
          <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>

      {/* Exported Command Box */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs">
          <span className="text-neutral-400 flex items-center gap-1.5 font-medium">
            <Terminal className="w-3.5 h-3.5" />
            <span>Synchronized Command-Line Arguments</span>
          </span>
          <button
            onClick={handleCopy}
            className="flex items-center gap-1 text-xs text-neutral-300 hover:text-white transition-colors"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-emerald-400">Copied</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" />
                <span>Copy Command</span>
              </>
            )}
          </button>
        </div>

        <pre className="p-4 bg-neutral-950 border border-neutral-800 rounded-lg text-xs font-mono text-neutral-300 overflow-x-auto leading-relaxed">
          {pythonCommand}
        </pre>
      </div>

      {/* 3-Step Execution Guide */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
        <div className="p-3 bg-neutral-950/50 border border-neutral-800 rounded-lg">
          <span className="font-semibold text-white block mb-1">01. Open & Connect</span>
          <p className="text-neutral-400">
            Open the Colab link and click <strong>Connect T4 GPU</strong> in Google Colab runtime options.
          </p>
        </div>

        <div className="p-3 bg-neutral-950/50 border border-neutral-800 rounded-lg">
          <span className="font-semibold text-white block mb-1">02. Paste Parameters</span>
          <p className="text-neutral-400">
            Copy the command above into the execution cell. All Kroteus sliders (Deblur, Dehalo, Anti-Alias) are pre-filled.
          </p>
        </div>

        <div className="p-3 bg-neutral-950/50 border border-neutral-800 rounded-lg">
          <span className="font-semibold text-white block mb-1">03. Run & Download</span>
          <p className="text-neutral-400">
            Run the cell to process frame-by-frame with Real-ESRGAN/Kroteus weights. Download the pristine 4K video.
          </p>
        </div>
      </div>
    </div>
  );

  if (isInline) {
    return (
      <div className="w-full max-w-4xl mx-auto p-6 bg-neutral-900/60 border border-neutral-800 rounded-xl my-4">
        {content}
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl w-full max-w-3xl overflow-hidden shadow-2xl p-6">
        <div className="flex justify-end pb-2">
          {onClose && (
            <button
              onClick={onClose}
              className="text-xs text-neutral-400 hover:text-white"
            >
              Close
            </button>
          )}
        </div>
        {content}
      </div>
    </div>
  );
};
