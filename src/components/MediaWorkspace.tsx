import React, { useState, useRef, useEffect, useCallback } from 'react';
import { MediaItem, EnhancerSettings } from '../types/enhancer';
import { disposeCanvas, getSafeDisplayDimensions, getMaxSafeDimension } from '../utils/canvasCleanup';
import { 
  Split, 
  ZoomIn, 
  Upload, 
  RotateCcw, 
  Camera, 
  Eye, 
  SlidersHorizontal,
  Image as ImageIcon,
  Film,
  Sparkles,
  Maximize2
} from 'lucide-react';

interface MediaWorkspaceProps {
  currentMedia: MediaItem;
  originalCanvas: HTMLCanvasElement | null;
  enhancedCanvas: HTMLCanvasElement | null;
  isProcessing: boolean;
  settings: EnhancerSettings;
  onSelectSample: (sampleId: string) => void;
  onFileUpload: (file: File) => void;
  onCaptureFrame: () => void;
  onSeekVideo?: (time: number) => void;
  videoTime?: number;
  videoDuration?: number;
}

export const MediaWorkspace: React.FC<MediaWorkspaceProps> = ({
  currentMedia,
  originalCanvas,
  enhancedCanvas,
  isProcessing,
  settings,
  onSelectSample,
  onFileUpload,
  onCaptureFrame,
  onSeekVideo,
  videoTime = 0,
  videoDuration = 0,
}) => {
  // Split-View Slider Overlay State
  const [isSplitSliderEnabled, setIsSplitSliderEnabled] = useState<boolean>(true);
  const [sliderPos, setSliderPos] = useState<number>(50); // percentage 0 to 100
  const [splitOrientation, setSplitOrientation] = useState<'vertical' | 'horizontal'>('vertical');
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [holdOriginal, setHoldOriginal] = useState<boolean>(false);

  // 400% Sub-pixel Loupe inspection
  const [loupeActive, setLoupeActive] = useState<boolean>(false);
  const [loupePos, setLoupePos] = useState<{ x: number; y: number } | null>(null);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const loupeCanvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const animFrameIdRef = useRef<number | null>(null);

  // Source & Destination Dimensions
  const srcW = currentMedia.width || 1280;
  const srcH = currentMedia.height || 720;
  const dstW = srcW * settings.scaleFactor;
  const dstH = srcH * settings.scaleFactor;
  const srcMegapixels = (srcW * srcH) / 1_000_000;
  const dstMegapixels = (dstW * dstH) / 1_000_000;
  const addedPixels = dstW * dstH - srcW * srcH;

  // Safe display dimensions (Capped dynamically to protect mobile GPU VRAM from crashing Chrome)
  const maxSafeDim = getMaxSafeDimension(false);
  const { width: displayW, height: displayH } = getSafeDisplayDimensions(dstW, dstH, maxSafeDim);

  // Proper Cleanup mechanism on unmount
  useEffect(() => {
    return () => {
      if (animFrameIdRef.current) {
        cancelAnimationFrame(animFrameIdRef.current);
      }
      disposeCanvas(canvasRef.current);
      disposeCanvas(loupeCanvasRef.current);
    };
  }, []);

  // Cleanup canvas contexts when switching media
  useEffect(() => {
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
      }
    }
  }, [currentMedia.id]);

  // Clean up loupe canvas when toggled off
  useEffect(() => {
    if (!loupeActive && loupeCanvasRef.current) {
      disposeCanvas(loupeCanvasRef.current);
    }
  }, [loupeActive]);

  // High-performance Viewport Rendering
  const renderViewport = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    if (canvas.width !== displayW || canvas.height !== displayH) {
      canvas.width = displayW;
      canvas.height = displayH;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, displayW, displayH);

    const origSource = originalCanvas;
    const enhSource = enhancedCanvas;

    // Full original if holding original button or if enhanced frame is not ready
    if (holdOriginal || !enhSource) {
      if (origSource) {
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(origSource, 0, 0, srcW, srcH, 0, 0, displayW, displayH);
      }
      return;
    }

    // Full enhanced if split slider overlay is disabled
    if (!isSplitSliderEnabled) {
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'medium';
      ctx.drawImage(enhSource, 0, 0, enhSource.width, enhSource.height, 0, 0, displayW, displayH);
      return;
    }

    // Split-view rendering
    if (splitOrientation === 'vertical') {
      const splitX = Math.round((sliderPos / 100) * displayW);

      // Left: Original
      if (splitX > 0 && origSource) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(0, 0, splitX, displayH);
        ctx.clip();
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(origSource, 0, 0, srcW, srcH, 0, 0, displayW, displayH);
        ctx.restore();
      }

      // Right: Enhanced
      if (splitX < displayW) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(splitX, 0, displayW - splitX, displayH);
        ctx.clip();
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(enhSource, 0, 0, enhSource.width, enhSource.height, 0, 0, displayW, displayH);
        ctx.restore();
      }
    } else {
      // Horizontal split
      const splitY = Math.round((sliderPos / 100) * displayH);

      // Top: Original
      if (splitY > 0 && origSource) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(0, 0, displayW, splitY);
        ctx.clip();
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(origSource, 0, 0, srcW, srcH, 0, 0, displayW, displayH);
        ctx.restore();
      }

      // Bottom: Enhanced
      if (splitY < displayH) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(0, splitY, displayW, displayH - splitY);
        ctx.clip();
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(enhSource, 0, 0, enhSource.width, enhSource.height, 0, 0, displayW, displayH);
        ctx.restore();
      }
    }
  }, [
    originalCanvas,
    enhancedCanvas,
    sliderPos,
    isSplitSliderEnabled,
    splitOrientation,
    holdOriginal,
    srcW,
    srcH,
    displayW,
    displayH,
  ]);

  // RequestAnimationFrame debounced render
  useEffect(() => {
    if (animFrameIdRef.current) {
      cancelAnimationFrame(animFrameIdRef.current);
    }
    animFrameIdRef.current = requestAnimationFrame(() => {
      renderViewport();
    });
    return () => {
      if (animFrameIdRef.current) cancelAnimationFrame(animFrameIdRef.current);
    };
  }, [renderViewport]);

  // Helper to compute slider percentage from pointer coordinates
  const updateSplitFromCoordinates = useCallback((clientX: number, clientY: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;

    if (splitOrientation === 'vertical') {
      const offsetX = clientX - rect.left;
      const pct = Math.max(0, Math.min(100, (offsetX / rect.width) * 100));
      setSliderPos(pct);
    } else {
      const offsetY = clientY - rect.top;
      const pct = Math.max(0, Math.min(100, (offsetY / rect.height) * 100));
      setSliderPos(pct);
    }
  }, [splitOrientation]);

  // Window-level dragging listener ensures the slider never sticks or stops when moving rapidly
  useEffect(() => {
    if (!isDragging) return;

    const handleWindowPointerMove = (e: PointerEvent) => {
      e.preventDefault();
      updateSplitFromCoordinates(e.clientX, e.clientY);
    };

    const handleWindowPointerUp = () => {
      setIsDragging(false);
    };

    window.addEventListener('pointermove', handleWindowPointerMove, { passive: false });
    window.addEventListener('pointerup', handleWindowPointerUp);
    window.addEventListener('pointercancel', handleWindowPointerUp);

    return () => {
      window.removeEventListener('pointermove', handleWindowPointerMove);
      window.removeEventListener('pointerup', handleWindowPointerUp);
      window.removeEventListener('pointercancel', handleWindowPointerUp);
    };
  }, [isDragging, updateSplitFromCoordinates]);

  const handlePointerDown = (e: React.PointerEvent) => {
    if (!isSplitSliderEnabled) return;
    e.preventDefault();
    setIsDragging(true);
    updateSplitFromCoordinates(e.clientX, e.clientY);
  };

  // Loupe pointer tracking
  const handleContainerPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (loupeActive && containerRef.current && canvasRef.current) {
      const cRect = canvasRef.current.getBoundingClientRect();
      const contRect = containerRef.current.getBoundingClientRect();

      setLoupePos({
        x: Math.max(0, Math.min(e.clientX - contRect.left, contRect.width)),
        y: Math.max(0, Math.min(e.clientY - contRect.top, contRect.height)),
      });

      const loupeCanvas = loupeCanvasRef.current;
      const mainCanvas = canvasRef.current;
      if (loupeCanvas && mainCanvas && cRect.width > 0 && cRect.height > 0) {
        if (loupeCanvas.width !== 180 || loupeCanvas.height !== 180) {
          loupeCanvas.width = 180;
          loupeCanvas.height = 180;
        }
        const lCtx = loupeCanvas.getContext('2d');
        if (lCtx) {
          const normX = (e.clientX - cRect.left) / cRect.width;
          const normY = (e.clientY - cRect.top) / cRect.height;
          const sampleX = normX * mainCanvas.width;
          const sampleY = normY * mainCanvas.height;
          const sampleRadius = 24;

          lCtx.imageSmoothingEnabled = false;
          lCtx.clearRect(0, 0, 180, 180);
          lCtx.drawImage(
            mainCanvas,
            sampleX - sampleRadius,
            sampleY - sampleRadius,
            sampleRadius * 2,
            sampleRadius * 2,
            0,
            0,
            180,
            180
          );
        }
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col gap-3 min-w-0">
      {/* Top Media Bar & Real Resolution Readout */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3 bg-neutral-900/60 border border-neutral-800 rounded-lg text-xs">
        <div className="flex flex-wrap items-center gap-2.5">
          <span className="font-semibold text-white truncate max-w-xs">{currentMedia.name}</span>
          <span className="text-neutral-500" aria-hidden="true">·</span>
          <span className="text-neutral-400 font-mono tabular-nums">
            {srcW}×{srcH} ({srcMegapixels.toFixed(2)} MP)
          </span>
          <span className="text-neutral-500" aria-hidden="true">→</span>
          <span className="text-white font-mono tabular-nums font-semibold flex items-center gap-1">
            <Sparkles className="w-3 h-3 text-amber-400 inline" />
            {dstW}×{dstH} ({dstMegapixels.toFixed(2)} MP)
          </span>
          <span className="text-neutral-500" aria-hidden="true">·</span>
          <span className="text-emerald-400 font-mono tabular-nums font-medium">
            +{addedPixels.toLocaleString()} real pixels
          </span>
        </div>

        {/* Workspace Toolbar Controls */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Split-View Slider Toggle Button */}
          <button
            onClick={() => setIsSplitSliderEnabled(!isSplitSliderEnabled)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
              isSplitSliderEnabled
                ? 'bg-white text-neutral-950 border-white shadow-sm'
                : 'bg-neutral-900 border-neutral-800 text-neutral-400 hover:text-white'
            }`}
            title="Toggle the interactive split-view slider overlay"
          >
            <Split className="w-3.5 h-3.5" />
            <span>Split-View:</span>
            <span
              className={`px-1.5 py-0.5 rounded text-[10px] uppercase font-mono font-bold ${
                isSplitSliderEnabled ? 'bg-neutral-900 text-white' : 'bg-neutral-800 text-neutral-400'
              }`}
            >
              {isSplitSliderEnabled ? 'ON' : 'OFF'}
            </span>
          </button>

          {/* Orientation Toggle (Vertical / Horizontal) */}
          {isSplitSliderEnabled && (
            <button
              onClick={() => setSplitOrientation((prev) => (prev === 'vertical' ? 'horizontal' : 'vertical'))}
              className="flex items-center gap-1.5 px-2.5 py-1.5 bg-neutral-900 hover:bg-neutral-800 text-neutral-300 text-xs rounded border border-neutral-800 transition-colors"
              title="Toggle split orientation between Vertical and Horizontal"
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              <span>{splitOrientation === 'vertical' ? 'Vertical ↔' : 'Horizontal ↕'}</span>
            </button>
          )}

          {/* Hold for Original Button */}
          <button
            onMouseDown={() => setHoldOriginal(true)}
            onMouseUp={() => setHoldOriginal(false)}
            onTouchStart={() => setHoldOriginal(true)}
            onTouchEnd={() => setHoldOriginal(false)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs border transition-colors select-none ${
              holdOriginal
                ? 'bg-amber-400 text-neutral-950 font-bold border-amber-300'
                : 'bg-neutral-900 border-neutral-800 text-neutral-300 hover:text-white'
            }`}
            title="Press and hold to temporarily view original unenhanced frame"
          >
            <Eye className="w-3.5 h-3.5" />
            <span>Hold for Original</span>
          </button>

          {/* 400% Loupe Inspection */}
          <button
            onClick={() => setLoupeActive(!loupeActive)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border transition-colors ${
              loupeActive
                ? 'bg-white text-neutral-950 border-white font-medium'
                : 'bg-neutral-900 border-neutral-800 text-neutral-300 hover:border-neutral-700'
            }`}
            title="Inspect real pixel sub-pixel grid at 400% zoom"
          >
            <ZoomIn className="w-3.5 h-3.5" />
            <span>400% Loupe</span>
          </button>
        </div>
      </div>

      {/* Accessible Precision Slider Bar (Active when Split-View is ON) */}
      {isSplitSliderEnabled && (
        <div className="flex items-center gap-3 px-3.5 py-2 bg-neutral-900/50 border border-neutral-800 rounded-lg text-xs">
          <span className="text-[11px] font-mono text-neutral-400 uppercase font-semibold shrink-0">
            Split Divider:
          </span>

          <span className="text-[11px] font-mono text-neutral-400 shrink-0">Original</span>

          <input
            type="range"
            min="0"
            max="100"
            step="1"
            value={Math.round(sliderPos)}
            onChange={(e) => setSliderPos(parseFloat(e.target.value))}
            className="flex-1 accent-white bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
            aria-label="Split-view comparison position"
          />

          <span className="text-[11px] font-mono text-neutral-200 shrink-0">Enhanced</span>

          <div className="flex items-center gap-1 shrink-0 font-mono text-[11px]">
            <button
              onClick={() => setSliderPos(25)}
              className="px-2 py-0.5 bg-neutral-800 hover:bg-neutral-700 text-neutral-300 rounded"
              title="25% Original / 75% Enhanced"
            >
              25%
            </button>
            <button
              onClick={() => setSliderPos(50)}
              className="px-2 py-0.5 bg-neutral-800 hover:bg-neutral-700 text-white font-bold rounded"
              title="50% Center Split"
            >
              50%
            </button>
            <button
              onClick={() => setSliderPos(75)}
              className="px-2 py-0.5 bg-neutral-800 hover:bg-neutral-700 text-neutral-300 rounded"
              title="75% Original / 25% Enhanced"
            >
              75%
            </button>
          </div>
        </div>
      )}

      {/* Main Viewport Container */}
      <div
        ref={containerRef}
        onPointerMove={handleContainerPointerMove}
        className="relative flex-1 min-h-[460px] max-h-[72vh] bg-neutral-950 border border-neutral-800 rounded-lg overflow-hidden flex items-center justify-center p-2 select-none"
        style={{ touchAction: 'none' }}
      >
        {/* Loading overlay if image is initializing */}
        {!originalCanvas && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-neutral-950/90 z-30 gap-3">
            <div className="w-8 h-8 border-2 border-white/20 border-t-white rounded-full animate-spin" />
            <span className="text-xs font-mono text-neutral-400">Loading High-Fidelity Frame...</span>
          </div>
        )}

        {/* Tightly Bound Responsive Canvas Wrapper */}
        <div
          className="relative inline-block overflow-hidden rounded shadow-2xl max-w-full max-h-full"
          onPointerDown={handlePointerDown}
          style={{ touchAction: 'none' }}
        >
          {/* Main Viewport Canvas (Safe VRAM Allocation, Zero Leak) */}
          <canvas
            ref={canvasRef}
            className={`block max-w-full max-h-[66vh] object-contain ${
              isSplitSliderEnabled
                ? splitOrientation === 'vertical'
                  ? 'cursor-ew-resize'
                  : 'cursor-ns-resize'
                : 'cursor-default'
            }`}
            style={{ touchAction: 'none' }}
          />

          {/* Interactive Split-View Floating Handle Overlay */}
          {isSplitSliderEnabled && !holdOriginal && (
            <>
              {splitOrientation === 'vertical' ? (
                <div
                  className="absolute top-0 bottom-0 pointer-events-none z-20 flex items-center justify-center"
                  style={{
                    left: `${sliderPos}%`,
                    transform: 'translateX(-50%)',
                    width: '36px',
                  }}
                >
                  {/* Subtle Glow & Divider Line */}
                  <div className="w-[2px] h-full bg-white shadow-[0_0_8px_rgba(255,255,255,0.8)]" />

                  {/* Tactile Grab Knob */}
                  <div
                    className="absolute w-8 h-8 rounded-full bg-white text-neutral-950 shadow-2xl border-2 border-neutral-900 flex items-center justify-center text-xs font-bold cursor-ew-resize pointer-events-auto active:scale-95 transition-transform"
                    onPointerDown={handlePointerDown}
                    title="Drag to compare original vs enhanced"
                  >
                    ↔
                  </div>
                </div>
              ) : (
                <div
                  className="absolute left-0 right-0 pointer-events-none z-20 flex items-center justify-center"
                  style={{
                    top: `${sliderPos}%`,
                    transform: 'translateY(-50%)',
                    height: '36px',
                  }}
                >
                  {/* Subtle Glow & Horizontal Divider Line */}
                  <div className="h-[2px] w-full bg-white shadow-[0_0_8px_rgba(255,255,255,0.8)]" />

                  {/* Tactile Grab Knob */}
                  <div
                    className="absolute w-8 h-8 rounded-full bg-white text-neutral-950 shadow-2xl border-2 border-neutral-900 flex items-center justify-center text-xs font-bold cursor-ns-resize pointer-events-auto active:scale-95 transition-transform"
                    onPointerDown={handlePointerDown}
                    title="Drag to compare original vs enhanced"
                  >
                    ↕
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Readout Badges */}
        <div className="absolute top-4 left-4 px-2.5 py-1 bg-black/85 backdrop-blur-md rounded text-[11px] font-mono text-neutral-300 pointer-events-none z-10 border border-white/10 shadow-lg">
          Original: {srcW}×{srcH}
        </div>

        <div className="absolute top-4 right-4 px-2.5 py-1 bg-black/85 backdrop-blur-md rounded text-[11px] font-mono text-white pointer-events-none z-10 border border-white/10 flex items-center gap-1.5 shadow-lg">
          <span className="w-1.5 h-1.5 bg-emerald-400 rounded-full animate-pulse" />
          <span>Enhanced: {dstW}×{dstH} ({settings.scaleFactor}× Super-Res)</span>
        </div>

        {/* Bottom Split readout badge */}
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 px-3 py-1 bg-black/85 backdrop-blur-md rounded-full text-[11px] font-mono text-neutral-200 pointer-events-none z-10 border border-white/10 shadow-lg">
          {holdOriginal ? (
            <span className="text-amber-400 font-bold">REVEALING ORIGINAL</span>
          ) : isSplitSliderEnabled ? (
            <span>
              Original {Math.round(sliderPos)}% · Enhanced {100 - Math.round(sliderPos)}%
            </span>
          ) : (
            <span className="text-neutral-400">Full Enhanced View</span>
          )}
        </div>

        {/* 400% Zoom Loupe Lens */}
        {loupeActive && loupePos && (
          <div
            className="absolute pointer-events-none z-30 rounded-full border-2 border-white shadow-2xl overflow-hidden bg-neutral-900"
            style={{
              width: '180px',
              height: '180px',
              left: `${loupePos.x - 90}px`,
              top: `${loupePos.y - 90}px`,
            }}
          >
            <canvas
              ref={loupeCanvasRef}
              width={180}
              height={180}
              className="w-full h-full object-cover"
            />
            <div className="absolute bottom-2 inset-x-0 text-center text-[10px] font-mono text-white/90 bg-black/75 py-0.5">
              4× Sub-pixel Loupe
            </div>
          </div>
        )}
      </div>

      {/* Video Playhead Scrub Bar (Only rendered for video items) */}
      {currentMedia.type === 'video' && videoDuration > 0 && (
        <div className="flex items-center gap-4 px-4 py-2.5 bg-neutral-900/60 border border-neutral-800 rounded-lg text-xs">
          <button
            onClick={() => {
              if (onSeekVideo) onSeekVideo(0);
            }}
            className="p-1 hover:text-white text-neutral-400 transition-colors"
            title="Rewind to start"
          >
            <RotateCcw className="w-4 h-4" />
          </button>

          <span className="font-mono tabular-nums text-neutral-300 w-24 shrink-0">
            {Math.floor(videoTime / 60)}:{(videoTime % 60).toFixed(2).padStart(5, '0')} /{' '}
            {Math.floor(videoDuration / 60)}:{(videoDuration % 60).toFixed(1).padStart(4, '0')}
          </span>

          <input
            type="range"
            min="0"
            max={videoDuration || 1}
            step="0.033"
            value={videoTime}
            onChange={(e) => {
              if (onSeekVideo) onSeekVideo(parseFloat(e.target.value));
            }}
            className="flex-1 accent-white bg-neutral-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />

          <button
            onClick={onCaptureFrame}
            className="flex items-center gap-1.5 px-3 py-1 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 text-xs rounded transition-colors whitespace-nowrap"
            title="Export this exact frame at full super-resolution"
          >
            <Camera className="w-3.5 h-3.5" />
            <span>Capture Frame</span>
          </button>
        </div>
      )}

      {/* Showcase Presets & Custom File Upload Tray */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3 bg-neutral-900/40 border border-neutral-800/80 rounded-lg">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-neutral-400 font-medium mr-1">Benchmark Media:</span>

          <button
            onClick={() => onSelectSample('car')}
            className={`px-3 py-1.5 text-xs rounded-md border transition-colors flex items-center gap-1.5 ${
              currentMedia.id === 'sample_vintage_car'
                ? 'bg-neutral-800 border-neutral-600 text-white'
                : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-white'
            }`}
          >
            <Film className="w-3.5 h-3.5" />
            <span>Sports Car Drift</span>
          </button>

          <button
            onClick={() => onSelectSample('portrait')}
            className={`px-3 py-1.5 text-xs rounded-md border transition-colors flex items-center gap-1.5 ${
              currentMedia.id === 'sample_urban_portrait'
                ? 'bg-neutral-800 border-neutral-600 text-white'
                : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-white'
            }`}
          >
            <ImageIcon className="w-3.5 h-3.5" />
            <span>Documentary Portrait</span>
          </button>

          <button
            onClick={() => onSelectSample('cityscape')}
            className={`px-3 py-1.5 text-xs rounded-md border transition-colors flex items-center gap-1.5 ${
              currentMedia.id === 'sample_cyber_cityscape'
                ? 'bg-neutral-800 border-neutral-600 text-white'
                : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-white'
            }`}
          >
            <ImageIcon className="w-3.5 h-3.5" />
            <span>Cyber Cityscape</span>
          </button>

          <button
            onClick={() => onSelectSample('anime')}
            className={`px-3 py-1.5 text-xs rounded-md border transition-colors flex items-center gap-1.5 ${
              currentMedia.id === 'sample_anime_mecha'
                ? 'bg-neutral-800 border-neutral-600 text-white'
                : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-white'
            }`}
          >
            <Film className="w-3.5 h-3.5" />
            <span>Anime Action Mecha</span>
          </button>
        </div>

        {/* Upload Custom Photo or Video */}
        <div>
          <input
            type="file"
            ref={fileInputRef}
            accept="image/*,video/*"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onFileUpload(file);
            }}
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-neutral-300 bg-neutral-800 hover:bg-neutral-700 rounded-md transition-colors"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>Upload Photo or Video</span>
          </button>
        </div>
      </div>
    </div>
  );
};
