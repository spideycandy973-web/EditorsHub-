import React, { useState, useEffect, useRef, useCallback } from 'react';
import { MediaItem, EnhancerSettings, ModelPreset, ProcessingStatus, QualityAnalysisResult } from './types/enhancer';
import { PRESET_CONFIGS } from './services/superResolutionEngine';
import { webglEnhancer } from './services/webglEnhancer';
import { analyzeFrameQuality } from './services/qualityAnalyzer';
import { disposeCanvas, getSafeDisplayDimensions, getMaxSafeDimension } from './utils/canvasCleanup';
import { VideoSuperResolutionProcessor } from './services/videoProcessor';
import { Header } from './components/Header';
import { EnhancerControls } from './components/EnhancerControls';
import { MediaWorkspace } from './components/MediaWorkspace';
import { VideoBatchProgress } from './components/VideoBatchProgress';
import { ColabBridgeModal } from './components/ColabBridgeModal';
import { FidelityAuditPanel } from './components/FidelityAuditPanel';
import { AndroidReleaseModal } from './components/AndroidReleaseModal';

import sampleCarUrl from './assets/images/sample_vintage_car_1793170788562.jpg';
import samplePortraitUrl from './assets/images/sample_urban_portrait_1793170789024.jpg';
import sampleCityscapeUrl from './assets/images/sample_cyber_cityscape_1793170789915.jpg';
import sampleAnimeUrl from './assets/images/sample_anime_mecha_1793170789185.jpg';

// High-fidelity sample benchmark media
const SAMPLE_MEDIA: Record<string, MediaItem> = {
  car: {
    id: 'sample_vintage_car',
    name: 'GT3_Drift_Twilight_35mm.jpg',
    type: 'image',
    url: sampleCarUrl,
    width: 1920,
    height: 1080,
    isSample: true,
  },
  portrait: {
    id: 'sample_urban_portrait',
    name: 'Authentic_Portrait_WindowLight.jpg',
    type: 'image',
    url: samplePortraitUrl,
    width: 1440,
    height: 1080,
    isSample: true,
  },
  cityscape: {
    id: 'sample_cyber_cityscape',
    name: 'Metropolis_Night_Architecture.jpg',
    type: 'image',
    url: sampleCityscapeUrl,
    width: 1920,
    height: 1080,
    isSample: true,
  },
  anime: {
    id: 'sample_anime_mecha',
    name: 'Mecha_Unit07_Inked_Frame.jpg',
    type: 'image',
    url: sampleAnimeUrl,
    width: 1920,
    height: 1080,
    isSample: true,
  },
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'workspace' | 'colab' | 'audit'>('workspace');
  const [currentMedia, setCurrentMedia] = useState<MediaItem>(SAMPLE_MEDIA.car);
  const [settings, setSettings] = useState<EnhancerSettings>(PRESET_CONFIGS['kroteus-real-clarity']);
  
  const [originalCanvas, setOriginalCanvas] = useState<HTMLCanvasElement | null>(null);
  const [enhancedCanvas, setEnhancedCanvas] = useState<HTMLCanvasElement | null>(null);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [qualityResult, setQualityResult] = useState<QualityAnalysisResult | null>(null);
  const [showAndroidModal, setShowAndroidModal] = useState<boolean>(false);
  
  const [showBatchModal, setShowBatchModal] = useState<boolean>(false);
  const [batchDownloadUrl, setBatchDownloadUrl] = useState<string | null>(null);
  const [videoTime, setVideoTime] = useState<number>(0);
  const [videoDuration, setVideoDuration] = useState<number>(0);

  const [processingStatus, setProcessingStatus] = useState<ProcessingStatus>({
    isProcessing: false,
    isPaused: false,
    currentFrame: 0,
    totalFrames: 0,
    fps: 0,
    elapsedMs: 0,
    estimatedRemainingMs: 0,
    stage: 'idle',
    originalResolution: { width: 1920, height: 1080 },
    enhancedResolution: { width: 3840, height: 2160 },
    addedPixelCount: 0,
  });

  const videoProcessorRef = useRef<VideoSuperResolutionProcessor | null>(null);
  const hiddenVideoRef = useRef<HTMLVideoElement | null>(null);
  const activeImageRef = useRef<HTMLImageElement | null>(null);
  const animFrameIdRef = useRef<number | null>(null);
  const isSeekingRef = useRef<boolean>(false);
  const pendingSeekTimeRef = useRef<number | null>(null);

  // Safely sets original canvas, automatically disposing the previous one to prevent VRAM memory leaks
  const updateOriginalCanvas = useCallback((newCanvas: HTMLCanvasElement | null) => {
    setOriginalCanvas((prev) => {
      if (prev && prev !== newCanvas) {
        disposeCanvas(prev);
      }
      return newCanvas;
    });
  }, []);

  // Fast GPU rendering triggered on settings/media changes with requestAnimationFrame
  const renderCurrentFrameFast = useCallback(() => {
    if (animFrameIdRef.current) {
      cancelAnimationFrame(animFrameIdRef.current);
    }

    animFrameIdRef.current = requestAnimationFrame(() => {
      try {
        if (currentMedia.type === 'image' && activeImageRef.current) {
          const img = activeImageRef.current;
          if (img.complete && img.naturalWidth > 0) {
            const enhCv = webglEnhancer.renderFrame(img, settings, false);
            setEnhancedCanvas(enhCv);
          }
        } else if (currentMedia.type === 'video' && hiddenVideoRef.current) {
          const vid = hiddenVideoRef.current;
          if (vid.readyState >= 2) {
            const enhCv = webglEnhancer.renderFrame(vid, settings, false);
            setEnhancedCanvas(enhCv);
          }
        }
      } catch (err) {
        console.error('GPU Render Error:', err);
      }
    });
  }, [currentMedia.type, settings]);

  // Automatic mathematical quality analyzer
  const triggerQualityAnalysis = useCallback((source?: HTMLCanvasElement | HTMLImageElement | HTMLVideoElement | null) => {
    const target = source || originalCanvas || activeImageRef.current || hiddenVideoRef.current;
    if (!target) return;
    try {
      const result = analyzeFrameQuality(target);
      setQualityResult(result);
    } catch (err) {
      console.error('Quality analysis error:', err);
    }
  }, [originalCanvas]);

  // Queued video frame seeker
  const seekAndRenderVideoFrame = useCallback((time: number) => {
    const vid = hiddenVideoRef.current;
    if (!vid) return;

    if (isSeekingRef.current) {
      pendingSeekTimeRef.current = time;
      return;
    }

    isSeekingRef.current = true;
    vid.currentTime = time;

    vid.onseeked = () => {
      isSeekingRef.current = false;
      setVideoTime(time);
      const w = vid.videoWidth || 1280;
      const h = vid.videoHeight || 720;
      const maxDim = getMaxSafeDimension(false);
      const { width: safeW, height: safeH } = getSafeDisplayDimensions(w, h, maxDim);
      
      const origCv = document.createElement('canvas');
      origCv.width = safeW;
      origCv.height = safeH;
      const ctx = origCv.getContext('2d');
      if (ctx) {
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'medium';
        ctx.drawImage(vid, 0, 0, safeW, safeH);
        updateOriginalCanvas(origCv);
      }

      // Run GPU enhancement at safe preview resolution
      const enhCv = webglEnhancer.renderFrame(vid, settings, false);
      setEnhancedCanvas(enhCv);

      // Handle queued seek if user scrubbed while seeking
      if (pendingSeekTimeRef.current !== null) {
        const nextTime = pendingSeekTimeRef.current;
        pendingSeekTimeRef.current = null;
        seekAndRenderVideoFrame(nextTime);
      }
    };
  }, [settings, updateOriginalCanvas]);

  // Load new media & perform proper cleanup
  useEffect(() => {
    if (currentMedia.type === 'image') {
      const img = new Image();
      if (
        typeof window !== 'undefined' &&
        currentMedia.url.startsWith('http') &&
        !currentMedia.url.startsWith(window.location.origin)
      ) {
        img.crossOrigin = 'anonymous';
      }

      img.onerror = (e) => {
        console.error('Failed to load image:', currentMedia.url, e);
      };

      img.onload = () => {
        activeImageRef.current = img;
        const w = img.naturalWidth || 1920;
        const h = img.naturalHeight || 1080;
        if (currentMedia.width !== w || currentMedia.height !== h) {
          setCurrentMedia((prev) => ({ ...prev, width: w, height: h }));
        }

        // Create original canvas snapshot (scaled to safe preview bounds to prevent mobile GPU OOM crashes)
        const maxDim = getMaxSafeDimension(false);
        const { width: safeW, height: safeH } = getSafeDisplayDimensions(w, h, maxDim);
        const origCv = document.createElement('canvas');
        origCv.width = safeW;
        origCv.height = safeH;
        const ctx = origCv.getContext('2d');
        if (ctx) {
          ctx.imageSmoothingEnabled = true;
          ctx.imageSmoothingQuality = 'medium';
          ctx.drawImage(img, 0, 0, safeW, safeH);
          updateOriginalCanvas(origCv);
        }

        // Run automatic quality assessment
        try {
          const analysis = analyzeFrameQuality(img);
          setQualityResult(analysis);
        } catch (e) {
          console.warn('Initial quality analysis error:', e);
        }

        // Instant GPU super-resolution render
        renderCurrentFrameFast();
      };

      img.src = currentMedia.url;
    } else if (currentMedia.type === 'video') {
      if (!hiddenVideoRef.current) {
        hiddenVideoRef.current = document.createElement('video');
        hiddenVideoRef.current.muted = true;
        hiddenVideoRef.current.playsInline = true;
      }
      const vid = hiddenVideoRef.current;
      if (
        typeof window !== 'undefined' &&
        currentMedia.url.startsWith('http') &&
        !currentMedia.url.startsWith(window.location.origin)
      ) {
        vid.crossOrigin = 'anonymous';
      }
      vid.src = currentMedia.url;
      vid.onloadedmetadata = () => {
        setVideoDuration(vid.duration || 0);
        setCurrentMedia((prev) => ({
          ...prev,
          width: vid.videoWidth || 1280,
          height: vid.videoHeight || 720,
          duration: vid.duration,
        }));
        seekAndRenderVideoFrame(0);
      };
    }

    return () => {
      if (animFrameIdRef.current) {
        cancelAnimationFrame(animFrameIdRef.current);
      }
    };
  }, [currentMedia.url, currentMedia.type, updateOriginalCanvas]);

  // Update in real-time when settings change
  useEffect(() => {
    renderCurrentFrameFast();
  }, [settings, renderCurrentFrameFast]);

  const handleSelectSample = (sampleKey: string) => {
    const sample = SAMPLE_MEDIA[sampleKey];
    if (sample) {
      setCurrentMedia(sample);
    }
  };

  const handleFileUpload = (file: File) => {
    const isVid = file.type.startsWith('video/');
    const url = URL.createObjectURL(file);
    setCurrentMedia({
      id: `upload_${Date.now()}`,
      name: file.name,
      type: isVid ? 'video' : 'image',
      url,
      width: 1280,
      height: 720,
      fileSize: file.size,
      isSample: false,
    });
  };

  const handleApplyPreset = (preset: ModelPreset) => {
    if (PRESET_CONFIGS[preset]) {
      setSettings(PRESET_CONFIGS[preset]);
    }
  };

  const handleApplySuggestedQuality = () => {
    if (qualityResult?.suggestedSettings) {
      setSettings(qualityResult.suggestedSettings);
    }
  };

  // Run full super-resolution video processing frame-by-frame
  const handleStartEnhance = () => {
    if (currentMedia.type === 'image') {
      renderCurrentFrameFast();
      return;
    }

    // Video processing
    if (!videoProcessorRef.current) {
      videoProcessorRef.current = new VideoSuperResolutionProcessor();
    }
    const processor = videoProcessorRef.current;

    setShowBatchModal(true);
    setIsProcessing(true);
    setBatchDownloadUrl(null);

    processor.processVideo(currentMedia.url, settings, 24, {
      onProgress: (status, currentCv) => {
        setProcessingStatus(status);
        if (currentCv) {
          setEnhancedCanvas(currentCv);
        }
      },
      onComplete: (_blob, downloadUrl) => {
        setIsProcessing(false);
        setBatchDownloadUrl(downloadUrl);
      },
      onError: (err) => {
        setIsProcessing(false);
        alert(`Video super-resolution error: ${err.message}`);
      },
    });
  };

  // High-performance authentic super-resolution export (On-demand full scale + immediate disposal)
  const handleExportCurrent = () => {
    const target = currentMedia.type === 'image' ? activeImageRef.current : hiddenVideoRef.current;
    if (!target) {
      if (enhancedCanvas) {
        enhancedCanvas.toBlob((blob) => {
          if (!blob) return;
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `EditorsHub_enhanced_${settings.scaleFactor}x_${currentMedia.name.replace(/\.[^/.]+$/, '')}.png`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        }, 'image/png');
      }
      return;
    }

    try {
      // Render at maximum authentic super-resolution for file saving
      const exportCanvas = webglEnhancer.renderFrame(target, settings, true);
      exportCanvas.toBlob((blob) => {
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `EditorsHub_enhanced_${settings.scaleFactor}x_${currentMedia.name.replace(/\.[^/.]+$/, '')}.png`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }, 'image/png');
    } catch (e) {
      console.error('Export error:', e);
    }
  };

  const handleCaptureFrame = () => {
    const target = hiddenVideoRef.current || activeImageRef.current;
    if (!target) return;
    try {
      const exportCanvas = webglEnhancer.renderFrame(target, settings, true);
      exportCanvas.toBlob((blob) => {
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const timeStr = videoTime.toFixed(2).replace('.', '_');
        a.download = `EditorsHub_frame_${timeStr}s_${currentMedia.name.replace(/\.[^/.]+$/, '')}.png`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }, 'image/png');
    } catch (e) {
      console.error('Capture frame error:', e);
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-sans">
      {/* Universal Top Bar */}
      <Header
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        onOpenColab={() => setActiveTab('colab')}
        onOpenAndroidRelease={() => setShowAndroidModal(true)}
        onExportCurrent={handleExportCurrent}
        isProcessing={isProcessing}
      />

      {/* Main Workspace Frame */}
      <main className="flex-1 max-w-[1560px] w-full mx-auto px-4 sm:px-6 py-6 flex flex-col gap-6">
        {activeTab === 'workspace' && (
          <div className="flex flex-col lg:flex-row items-start gap-6">
            {/* Left/Main Media Comparison Workspace with interactive split-view slider overlay */}
            <MediaWorkspace
              currentMedia={currentMedia}
              originalCanvas={originalCanvas}
              enhancedCanvas={enhancedCanvas}
              isProcessing={isProcessing}
              settings={settings}
              onSelectSample={handleSelectSample}
              onFileUpload={handleFileUpload}
              onCaptureFrame={handleCaptureFrame}
              onSeekVideo={seekAndRenderVideoFrame}
              videoTime={videoTime}
              videoDuration={videoDuration}
            />

            {/* Right Pipeline Controls Panel */}
            <EnhancerControls
              settings={settings}
              onChangeSettings={setSettings}
              onApplyPreset={handleApplyPreset}
              isProcessing={isProcessing}
              onRunEnhance={handleStartEnhance}
              mediaType={currentMedia.type}
              qualityResult={qualityResult}
              onAnalyzeQuality={() => triggerQualityAnalysis()}
              onApplySuggestedQuality={handleApplySuggestedQuality}
            />
          </div>
        )}

        {/* Tab: Fidelity & Artifact Audit */}
        {activeTab === 'audit' && (
          <FidelityAuditPanel
            currentCanvas={originalCanvas}
            onApplySettings={(newSettings) => {
              setSettings(prev => ({ ...prev, ...newSettings }));
              setActiveTab('workspace');
            }}
          />
        )}

        {/* Tab: Colab Bridge Modal & Integration */}
        {activeTab === 'colab' && (
          <ColabBridgeModal
            settings={settings}
            isInline={true}
          />
        )}
      </main>

      {/* Video Frame-by-Frame Modal */}
      {showBatchModal && (
        <VideoBatchProgress
          status={processingStatus}
          currentPreviewCanvas={enhancedCanvas}
          onPauseToggle={() => {
            if (videoProcessorRef.current) {
              const nextState = !processingStatus.isPaused;
              videoProcessorRef.current.setPaused(nextState);
              setProcessingStatus(prev => ({ ...prev, isPaused: nextState }));
            }
          }}
          onAbort={() => {
            if (videoProcessorRef.current) {
              videoProcessorRef.current.abort();
            }
            setShowBatchModal(false);
          }}
          onDownload={() => {
            if (batchDownloadUrl) {
              const a = document.createElement('a');
              a.href = batchDownloadUrl;
              a.download = `EditorsHub_super_resolved_${settings.scaleFactor}x_${currentMedia.name}`;
              a.click();
            }
          }}
          downloadUrl={batchDownloadUrl}
        />
      )}

      {/* Android APK & GitHub Release Modal */}
      <AndroidReleaseModal
        isOpen={showAndroidModal}
        onClose={() => setShowAndroidModal(false)}
      />
    </div>
  );
}
