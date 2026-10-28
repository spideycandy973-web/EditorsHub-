export type ModelPreset = 
  | 'kroteus-real-clarity' 
  | 'realesr-animevideov3' 
  | 'compact-ultrasharp' 
  | 'cinematic-motion'
  | 'denoise-heavy';

export interface EnhancerSettings {
  modelPreset: ModelPreset;
  scaleFactor: 2 | 4;
  deblur: number; // 0 - 100
  antiAlias: number; // 0 - 100
  dehalo: number; // 0 - 100
  noiseReduction: number; // 0 - 100
  detailRecovery: number; // 0 - 100
  preserveGrain: boolean;
  colorClarity: number; // 0 - 100
}

export interface QualityAnalysisResult {
  noiseLevel: number; // 0 - 100 scale
  sharpnessScore: number; // 0 - 100 scale
  contrastRatio: number; // 0 - 100 scale
  noiseCategory: 'clean' | 'moderate' | 'noisy' | 'severe';
  sharpnessCategory: 'blurry' | 'soft' | 'sharp' | 'ultra-crisp';
  suggestedPreset: ModelPreset;
  suggestedSettings: EnhancerSettings;
  confidence: number; // 0 - 100 scale
  reason: string;
  metrics: {
    rawNoiseSigma: number;
    laplacianVariance: number;
    edgeDensityPct: number;
  };
}

export interface MediaItem {
  id: string;
  name: string;
  type: 'image' | 'video';
  url: string;
  width: number;
  height: number;
  duration?: number; // in seconds for video
  fps?: number;
  fileSize?: number;
  isSample?: boolean;
}

export interface ProcessingStatus {
  isProcessing: boolean;
  isPaused: boolean;
  currentFrame: number;
  totalFrames: number;
  fps: number;
  elapsedMs: number;
  estimatedRemainingMs: number;
  stage: 'idle' | 'extracting' | 'enhancing' | 'encoding' | 'completed' | 'aborted';
  originalResolution: { width: number; height: number };
  enhancedResolution: { width: number; height: number };
  addedPixelCount: number;
}

export interface FidelityAudit {
  detectedType: string;
  estimatedNativeResolution: string;
  compressionSeverity: number;
  blurLevel: number;
  recommendedSettings: Partial<EnhancerSettings>;
  fidelityObservations: string[];
}
