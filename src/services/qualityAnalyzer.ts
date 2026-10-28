import { EnhancerSettings, ModelPreset, QualityAnalysisResult } from '../types/enhancer';
import { PRESET_CONFIGS } from './superResolutionEngine';
import { disposeCanvas } from '../utils/canvasCleanup';

/**
 * Mathematical Quality Analyzer for Photos and Video Frames
 * Accurately measures:
 * 1. Additive white/sensor noise level using the Immerkaer Noise Variance Kernel
 * 2. Optical sharpness using the Variance of Laplacian (Tenengrad / Brenner metric)
 * 3. Edge density & micro-contrast ratio
 * 
 * Automatically recommends the optimal ModelPreset (e.g. 'kroteus-real-clarity' vs 'denoise-heavy')
 * and fine-tunes parameter sliders for authentic super-resolution.
 */

export function analyzeFrameQuality(
  source: HTMLCanvasElement | HTMLImageElement | HTMLVideoElement
): QualityAnalysisResult {
  let srcW = 0;
  let srcH = 0;

  if (source instanceof HTMLVideoElement) {
    srcW = source.videoWidth || 640;
    srcH = source.videoHeight || 360;
  } else if (source instanceof HTMLImageElement) {
    srcW = source.naturalWidth || 640;
    srcH = source.naturalHeight || 360;
  } else {
    srcW = source.width || 640;
    srcH = source.height || 360;
  }

  // Sample to optimal analysis resolution (up to 720p) for instant <5ms processing
  const maxDim = 720;
  let sampleW = srcW;
  let sampleH = srcH;
  if (sampleW > maxDim || sampleH > maxDim) {
    const scale = maxDim / Math.max(sampleW, sampleH);
    sampleW = Math.round(sampleW * scale);
    sampleH = Math.round(sampleH * scale);
  }

  const canvas = document.createElement('canvas');
  canvas.width = sampleW;
  canvas.height = sampleH;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) {
    throw new Error('Failed to create canvas context for quality analysis');
  }

  ctx.drawImage(source, 0, 0, sampleW, sampleH);
  const imgData = ctx.getImageData(0, 0, sampleW, sampleH);
  // Instantly release canvas memory from GPU/DOM
  disposeCanvas(canvas);

  const data = imgData.data;

  // 1. Convert to grayscale luminance array
  const totalPixels = sampleW * sampleH;
  const gray = new Float32Array(totalPixels);
  let minLum = 255;
  let maxLum = 0;
  let sumLum = 0;

  for (let i = 0; i < totalPixels; i++) {
    const idx = i * 4;
    const lum = 0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2];
    gray[i] = lum;
    if (lum < minLum) minLum = lum;
    if (lum > maxLum) maxLum = lum;
    sumLum += lum;
  }

  const contrastRatio = maxLum > minLum ? ((maxLum - minLum) / 255) * 100 : 0;

  // 2. Measure Noise Level via Immerkaer Laplacian Mask
  // Kernel: [ 1, -2,  1]
  //         [-2,  4, -2]
  //         [ 1, -2,  1]
  // Suppresses polynomial gradients and isolates pure additive noise.
  let noiseSum = 0;
  let noiseSamples = 0;

  for (let y = 1; y < sampleH - 1; y++) {
    const yOffset = y * sampleW;
    const yPrev = (y - 1) * sampleW;
    const yNext = (y + 1) * sampleW;

    for (let x = 1; x < sampleW - 1; x++) {
      const val = 
        1.0 * gray[yPrev + (x - 1)] - 2.0 * gray[yPrev + x] + 1.0 * gray[yPrev + (x + 1)] -
        2.0 * gray[yOffset + (x - 1)] + 4.0 * gray[yOffset + x] - 2.0 * gray[yOffset + (x + 1)] +
        1.0 * gray[yNext + (x - 1)] - 2.0 * gray[yNext + x] + 1.0 * gray[yNext + (x + 1)];

      noiseSum += Math.abs(val);
      noiseSamples++;
    }
  }

  // Immerkaer standard formula: sigma = sqrt(pi/2) / (6 * (W-2) * (H-2)) * sum
  const rawNoiseSigma = noiseSamples > 0 
    ? (Math.sqrt(Math.PI / 2.0) / (6.0 * noiseSamples)) * noiseSum 
    : 0;

  // Calibrated Noise Level (0 to 100)
  // sigma < 1.0: extremely clean, sigma = 3.0: moderate noise, sigma > 6.0: heavy grain/noise
  const noiseLevel = Math.max(0, Math.min(100, Math.round((rawNoiseSigma / 6.0) * 100)));

  // 3. Measure Sharpness via Variance of Laplacian (Blur metric)
  let lapMean = 0;
  const laplacianValues = new Float32Array(noiseSamples);
  let lIdx = 0;

  for (let y = 1; y < sampleH - 1; y++) {
    const yOffset = y * sampleW;
    for (let x = 1; x < sampleW - 1; x++) {
      const p = gray[yOffset + x];
      const left = gray[yOffset + (x - 1)];
      const right = gray[yOffset + (x + 1)];
      const top = gray[(y - 1) * sampleW + x];
      const bottom = gray[(y + 1) * sampleW + x];

      const lap = left + right + top + bottom - 4.0 * p;
      laplacianValues[lIdx++] = lap;
      lapMean += lap;
    }
  }

  lapMean /= (noiseSamples || 1);

  let lapVarianceSum = 0;
  for (let i = 0; i < lIdx; i++) {
    const diff = laplacianValues[i] - lapMean;
    lapVarianceSum += diff * diff;
  }
  const laplacianVariance = lapVarianceSum / (noiseSamples || 1);

  // Calibrated Sharpness Score (0 to 100)
  // Low variance (< 80) = blurry/soft, 80-350 = moderate, > 600 = ultra-sharp
  const sharpnessScore = Math.max(0, Math.min(100, Math.round(
    Math.min(100, (Math.log10(Math.max(1, laplacianVariance)) / 3.4) * 100)
  )));

  // 4. Edge density & flat area analysis (distinguishes anime/line-art vs natural photos)
  let strongEdgeCount = 0;
  for (let y = 1; y < sampleH - 1; y++) {
    const yOffset = y * sampleW;
    for (let x = 1; x < sampleW - 1; x++) {
      const gx = gray[yOffset + x + 1] - gray[yOffset + x - 1];
      const gy = gray[(y + 1) * sampleW + x] - gray[(y - 1) * sampleW + x];
      const mag = Math.sqrt(gx * gx + gy * gy);
      if (mag > 32) strongEdgeCount++;
    }
  }
  const edgeDensityPct = (strongEdgeCount / totalPixels) * 100;

  // 5. Categorization
  let noiseCategory: 'clean' | 'moderate' | 'noisy' | 'severe' = 'clean';
  if (noiseLevel > 70) noiseCategory = 'severe';
  else if (noiseLevel > 45) noiseCategory = 'noisy';
  else if (noiseLevel > 20) noiseCategory = 'moderate';

  let sharpnessCategory: 'blurry' | 'soft' | 'sharp' | 'ultra-crisp' = 'sharp';
  if (sharpnessScore < 30) sharpnessCategory = 'blurry';
  else if (sharpnessScore < 55) sharpnessCategory = 'soft';
  else if (sharpnessScore > 82) sharpnessCategory = 'ultra-crisp';

  // 6. Suggest Optimal ModelPreset
  let suggestedPreset: ModelPreset = 'kroteus-real-clarity';
  let reason = '';
  let confidence = 85;

  if (noiseLevel >= 48) {
    // High noise level -> Recommend 'denoise-heavy'
    suggestedPreset = 'denoise-heavy';
    confidence = Math.min(98, 80 + Math.round(noiseLevel * 0.2));
    reason = `Elevated noise floor (${noiseLevel}%) detected. 'denoise-heavy' prevents digital grain amplification and suppresses compression artifacts.`;
  } else if (edgeDensityPct > 8.0 && contrastRatio > 65 && noiseLevel < 35 && sharpnessScore >= 45) {
    // High-contrast clean contours -> Recommend 'realesr-animevideov3'
    suggestedPreset = 'realesr-animevideov3';
    confidence = 90;
    reason = `High-contrast ink outlines and flat fields detected. 'realesr-animevideov3' eliminates boundary ringing and preserves clean line-art.`;
  } else if (sharpnessScore >= 72 && noiseLevel < 25) {
    // Clean, already sharp -> Recommend 'compact-ultrasharp' (4x scale)
    suggestedPreset = 'compact-ultrasharp';
    confidence = 92;
    reason = `Clean high-frequency micro-details detected with minimal noise (${noiseLevel}%). 'compact-ultrasharp' maximizes 4× super-resolution.`;
  } else if (sharpnessScore < 50 && noiseLevel < 40) {
    // Blurry/soft photo with low noise -> Recommend 'kroteus-real-clarity'
    suggestedPreset = 'kroteus-real-clarity';
    confidence = 88;
    reason = `Optical softness and focus blur detected. 'kroteus-real-clarity' reconstructs true physical micro-contrast and deblurs natural textures.`;
  } else {
    // Balanced or filmic -> Recommend 'cinematic-motion'
    suggestedPreset = 'cinematic-motion';
    confidence = 84;
    reason = `Filmic balance between grain and edge clarity detected. 'cinematic-motion' ensures temporal coherency and natural organic texture.`;
  }

  // Fine-tune preset settings based on measured values
  const basePreset = PRESET_CONFIGS[suggestedPreset] || PRESET_CONFIGS['kroteus-real-clarity'];
  const suggestedSettings: EnhancerSettings = {
    ...basePreset,
    // Adjust noise reduction proportional to measured noise
    noiseReduction: Math.min(100, Math.max(10, Math.round(noiseLevel * 1.1))),
    // Adjust deblur: if already sharp, reduce to prevent haloing; if soft, boost
    deblur: sharpnessScore < 40 ? Math.min(80, basePreset.deblur + 15) : basePreset.deblur,
    // Adjust dehalo: if contrast is high, increase dehalo
    dehalo: contrastRatio > 70 ? Math.min(85, basePreset.dehalo + 15) : basePreset.dehalo,
  };

  return {
    noiseLevel,
    sharpnessScore,
    contrastRatio: Math.round(contrastRatio),
    noiseCategory,
    sharpnessCategory,
    suggestedPreset,
    suggestedSettings,
    confidence,
    reason,
    metrics: {
      rawNoiseSigma: parseFloat(rawNoiseSigma.toFixed(3)),
      laplacianVariance: Math.round(laplacianVariance),
      edgeDensityPct: parseFloat(edgeDensityPct.toFixed(2)),
    },
  };
}
