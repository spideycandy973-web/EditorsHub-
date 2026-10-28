import { EnhancerSettings, ProcessingStatus } from '../types/enhancer';
import { webglEnhancer } from './webglEnhancer';
import { disposeCanvas } from '../utils/canvasCleanup';

export interface VideoProcessingCallbacks {
  onProgress: (status: ProcessingStatus, currentEnhancedCanvas?: HTMLCanvasElement) => void;
  onComplete: (videoBlob: Blob, downloadUrl: string) => void;
  onError: (err: Error) => void;
}

export class VideoSuperResolutionProcessor {
  private isAborted = false;
  private isPaused = false;
  private videoEl: HTMLVideoElement | null = null;
  private mediaRecorder: MediaRecorder | null = null;
  private recordedChunks: Blob[] = [];

  public abort() {
    this.isAborted = true;
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop();
      } catch (e) {
        console.warn('MediaRecorder stop error:', e);
      }
    }
  }

  public setPaused(paused: boolean) {
    this.isPaused = paused;
  }

  public async processVideo(
    videoUrl: string,
    settings: EnhancerSettings,
    targetFps = 30,
    callbacks: VideoProcessingCallbacks
  ): Promise<void> {
    this.isAborted = false;
    this.isPaused = false;
    this.recordedChunks = [];

    const video = document.createElement('video');
    this.videoEl = video;
    video.crossOrigin = 'anonymous';
    video.src = videoUrl;
    video.muted = true;
    video.playsInline = true;

    let streamCanvas: HTMLCanvasElement | null = null;

    try {
      await new Promise<void>((resolve, reject) => {
        video.onloadedmetadata = () => resolve();
        video.onerror = () => reject(new Error('Failed to load video metadata'));
      });

      const duration = video.duration || 1;
      const srcW = video.videoWidth || 1280;
      const srcH = video.videoHeight || 720;
      const dstW = srcW * settings.scaleFactor;
      const dstH = srcH * settings.scaleFactor;

      const totalFrames = Math.max(1, Math.floor(duration * targetFps));
      const frameDuration = 1 / targetFps;

      // Stream canvas for high-speed hardware-encoded recording
      streamCanvas = document.createElement('canvas');
      streamCanvas.width = dstW;
      streamCanvas.height = dstH;
      const streamCtx = streamCanvas.getContext('2d');
      if (!streamCtx) throw new Error('Could not initialize canvas context');

      // Setup MediaStream & MediaRecorder
      const stream = streamCanvas.captureStream(targetFps);

      const mimeTypes = [
        'video/webm;codecs=vp9',
        'video/webm;codecs=vp8',
        'video/webm',
        'video/mp4',
      ];
      const selectedMime = mimeTypes.find(type => MediaRecorder.isTypeSupported(type)) || '';
      
      const recorder = selectedMime ? new MediaRecorder(stream, { mimeType: selectedMime }) : new MediaRecorder(stream);
      this.mediaRecorder = recorder;

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          this.recordedChunks.push(e.data);
        }
      };

      const recorderFinished = new Promise<Blob>((resolve) => {
        recorder.onstop = () => {
          const blob = new Blob(this.recordedChunks, { type: selectedMime || 'video/webm' });
          resolve(blob);
        };
      });

      recorder.start();

      const startTime = performance.now();
      const originalPixelCount = srcW * srcH;
      const enhancedPixelCount = dstW * dstH;
      const addedPixelsPerFrame = enhancedPixelCount - originalPixelCount;

      for (let frameIdx = 0; frameIdx < totalFrames; frameIdx++) {
        if (this.isAborted) {
          callbacks.onProgress({
            isProcessing: false,
            isPaused: false,
            currentFrame: frameIdx,
            totalFrames,
            fps: 0,
            elapsedMs: performance.now() - startTime,
            estimatedRemainingMs: 0,
            stage: 'aborted',
            originalResolution: { width: srcW, height: srcH },
            enhancedResolution: { width: dstW, height: dstH },
            addedPixelCount: frameIdx * addedPixelsPerFrame,
          });
          recorder.stop();
          return;
        }

        while (this.isPaused && !this.isAborted) {
          await new Promise(r => setTimeout(r, 100));
        }

        const seekTime = Math.min(duration, frameIdx * frameDuration);
        
        await new Promise<void>((resolve) => {
          const onSeeked = () => {
            video.removeEventListener('seeked', onSeeked);
            resolve();
          };
          video.addEventListener('seeked', onSeeked);
          video.currentTime = seekTime;
        });

        // Fast WebGL hardware super-resolution on the GPU
        const enhancedFrameCanvas = webglEnhancer.renderFrame(video, settings);

        // Draw to stream canvas for recorder
        streamCtx.drawImage(enhancedFrameCanvas, 0, 0, dstW, dstH);

        const now = performance.now();
        const elapsed = now - startTime;
        const processedCount = frameIdx + 1;
        const currentFps = (processedCount / (elapsed / 1000));
        const remainingFrames = totalFrames - processedCount;
        const estRemainingMs = (remainingFrames / (currentFps || 1)) * 1000;

        callbacks.onProgress({
          isProcessing: true,
          isPaused: this.isPaused,
          currentFrame: processedCount,
          totalFrames,
          fps: parseFloat(currentFps.toFixed(1)),
          elapsedMs: Math.round(elapsed),
          estimatedRemainingMs: Math.round(estRemainingMs),
          stage: 'enhancing',
          originalResolution: { width: srcW, height: srcH },
          enhancedResolution: { width: dstW, height: dstH },
          addedPixelCount: processedCount * addedPixelsPerFrame,
        }, enhancedFrameCanvas);

        // Keep event loop responsive
        await new Promise(r => setTimeout(r, 2));
      }

      callbacks.onProgress({
        isProcessing: true,
        isPaused: false,
        currentFrame: totalFrames,
        totalFrames,
        fps: targetFps,
        elapsedMs: Math.round(performance.now() - startTime),
        estimatedRemainingMs: 0,
        stage: 'encoding',
        originalResolution: { width: srcW, height: srcH },
        enhancedResolution: { width: dstW, height: dstH },
        addedPixelCount: totalFrames * addedPixelsPerFrame,
      });

      recorder.stop();
      const finalBlob = await recorderFinished;
      const downloadUrl = URL.createObjectURL(finalBlob);

      callbacks.onProgress({
        isProcessing: false,
        isPaused: false,
        currentFrame: totalFrames,
        totalFrames,
        fps: targetFps,
        elapsedMs: Math.round(performance.now() - startTime),
        estimatedRemainingMs: 0,
        stage: 'completed',
        originalResolution: { width: srcW, height: srcH },
        enhancedResolution: { width: dstW, height: dstH },
        addedPixelCount: totalFrames * addedPixelsPerFrame,
      });

      callbacks.onComplete(finalBlob, downloadUrl);
    } catch (err: unknown) {
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      disposeCanvas(streamCanvas);
      if (this.videoEl) {
        this.videoEl.src = '';
        this.videoEl = null;
      }
    }
  }
}

