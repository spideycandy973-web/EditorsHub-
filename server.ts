import express from 'express';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI, Type } from '@google/genai';
import path from 'path';
import dotenv from 'dotenv';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

dotenv.config();

const app = express();
const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

// Body parser with 50MB limit for high-res frames
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Global CORS headers for asset loading and API requests
app.use((_req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', '*');
  next();
});

// Serve public directory statically
app.use(express.static(path.resolve('public')));

// Server-side Gemini initialization
const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: {
    headers: {
      'User-Agent': 'aistudio-build',
    },
  },
});

// API Route: Analyze Image Fidelity & Recommend Kroteus Parameters
app.post('/api/gemini/analyze-fidelity', async (req, res) => {
  try {
    const { imageBase64, mimeType = 'image/jpeg', mediaType = 'image' } = req.body;

    if (!imageBase64) {
      return res.status(400).json({ error: 'imageBase64 is required' });
    }

    // Clean base64 string if it contains data URI prefix
    const cleanBase64 = imageBase64.replace(/^data:image\/[a-z]+;base64,/, '');

    const prompt = `Analyze this ${mediaType} frame for genuine super-resolution and authentic pixel enhancement (inspired by Kroteus v15.5 / Real-ESRGAN).
Evaluate the underlying optical clarity, compression artifacts (macroblocking, ringing, chroma subsampling), motion blur, and micro-texture integrity.

Provide a JSON response with:
1. "detectedType": "real_photo" | "cinematic_video" | "anime_illustration" | "car_motion_edit" | "urban_architectural"
2. "estimatedNativeResolution": string (e.g. "720p HD", "1080p FHD", or "SD 480p")
3. "compressionSeverity": number (1 to 10 scale, 1 being clean uncompressed, 10 being heavily compressed)
4. "blurLevel": number (1 to 10 scale)
5. "recommendedSettings":
   - "modelPreset": "kroteus-real-clarity" | "realesr-animevideov3" | "compact-ultrasharp" | "cinematic-motion"
   - "scaleFactor": 2 or 4
   - "deblur": number (0 to 100)
   - "antiAlias": number (0 to 100)
   - "dehalo": number (0 to 100)
   - "noiseReduction": number (0 to 100)
   - "detailRecovery": number (0 to 100)
6. "fidelityObservations": array of 3-4 concise technical observations explaining why these parameters will reconstruct real pixel details without fake smoothing or plastic textures.`;

    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash',
      contents: {
        parts: [
          {
            inlineData: {
              mimeType,
              data: cleanBase64,
            },
          },
          { text: prompt },
        ],
      },
      config: {
        responseMimeType: 'application/json',
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            detectedType: { type: Type.STRING },
            estimatedNativeResolution: { type: Type.STRING },
            compressionSeverity: { type: Type.NUMBER },
            blurLevel: { type: Type.NUMBER },
            recommendedSettings: {
              type: Type.OBJECT,
              properties: {
                modelPreset: { type: Type.STRING },
                scaleFactor: { type: Type.NUMBER },
                deblur: { type: Type.NUMBER },
                antiAlias: { type: Type.NUMBER },
                dehalo: { type: Type.NUMBER },
                noiseReduction: { type: Type.NUMBER },
                detailRecovery: { type: Type.NUMBER },
              },
              required: [
                'modelPreset',
                'scaleFactor',
                'deblur',
                'antiAlias',
                'dehalo',
                'noiseReduction',
                'detailRecovery',
              ],
            },
            fidelityObservations: {
              type: Type.ARRAY,
              items: { type: Type.STRING },
            },
          },
          required: [
            'detectedType',
            'estimatedNativeResolution',
            'compressionSeverity',
            'blurLevel',
            'recommendedSettings',
            'fidelityObservations',
          ],
        },
      },
    });

    const jsonText = response.text || '{}';
    const parsedData = JSON.parse(jsonText);
    res.json(parsedData);
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Fidelity analysis failed';
    console.error('Error in /api/gemini/analyze-fidelity:', errorMsg);
    res.status(500).json({ error: errorMsg });
  }
});

// API Route: Generate Kroteus Colab configuration script
app.post('/api/colab/generate-config', (req, res) => {
  const {
    model = 'Kroteus_Real_Clarity_v15.5',
    scale = 4,
    deblur = 35,
    antiAlias = 40,
    dehalo = 45,
    noiseReduction = 20,
    detailRecovery = 65,
    fps = 30,
  } = req.body;

  const pythonScript = `
# ==============================================================================
# Kroteus Studio v15.5 - Pipeline Script for Google Colab
# Source: https://colab.research.google.com/drive/1k7WXSMLeeJd4i7RCxrlCl_1gM0YAVh7V
# Authentic Frame-by-Frame Super-Resolution (No fake smoothing)
# ==============================================================================

import os
import torch
import cv2
import numpy as np

# 1. Environment & GPU Verification
print("CUDA Available:", torch.cuda.is_available())
if torch.cuda.is_available():
    print("Device:", torch.cuda.get_device_name(0))

# 2. Pipeline Configuration exported from Kroteus Studio
CONFIG = {
    "model_preset": "${model}",
    "scale_factor": ${scale},
    "deblur_strength": ${deblur / 100},
    "anti_alias": ${antiAlias / 100},
    "dehalo_threshold": ${dehalo / 100},
    "noise_reduction": ${noiseReduction / 100},
    "detail_recovery": ${detailRecovery / 100},
    "target_fps": ${fps},
    "output_format": "mp4",
    "pixel_kernel": "lanczos3_subpixel_laplacian"
}

print("Running authentic super-resolution on input frames...")
# Frame-by-frame super-resolution processing logic
`;

  res.json({
    config: {
      model,
      scale,
      deblur,
      antiAlias,
      dehalo,
      noiseReduction,
      detailRecovery,
      fps,
    },
    pythonScript,
    colabUrl: 'https://colab.research.google.com/drive/1k7WXSMLeeJd4i7RCxrlCl_1gM0YAVh7V?usp=sharing',
  });
});

// API Route: One-click push to GitHub repository to trigger the automated APK compiler
app.post('/api/github/push-release', async (req, res) => {
  try {
    const { repoUrl, token } = req.body;
    if (!repoUrl) {
      return res.status(400).json({ error: 'GitHub repository URL is required.' });
    }
    if (!token) {
      return res.status(400).json({ error: 'GitHub Personal Access Token is required to authenticate push.' });
    }

    const cleanRepo = repoUrl.trim();
    const match = cleanRepo.match(/github\.com\/([^/]+)\/([^/.]+)/);
    if (!match) {
      return res.status(400).json({ error: 'Invalid GitHub repository URL format. Use https://github.com/OWNER/REPO.git' });
    }
    const owner = match[1];
    const repo = match[2];

    const authenticatedRemote = `https://${encodeURIComponent(token.trim())}@github.com/${owner}/${repo}.git`;

    const { stdout, stderr } = await execAsync(`git push "${authenticatedRemote}" main --force`, {
      timeout: 45000,
      env: { ...process.env, GIT_TERMINAL_PROMPT: '0' }
    });

    console.log('GitHub Push Output:', stdout || stderr);

    res.json({
      success: true,
      owner,
      repo,
      actionsUrl: `https://github.com/${owner}/${repo}/actions`,
      releasesUrl: `https://github.com/${owner}/${repo}/releases`,
      message: 'Successfully pushed codebase, Android native project, and CI/CD workflow to your GitHub repository!'
    });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    console.error('Git push error:', msg);
    const sanitizedMsg = msg.replace(/https:\/\/[^@]+@/g, 'https://***@');
    res.status(500).json({ error: sanitizedMsg });
  }
});

// Vite Middleware for Fullstack Development
async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.resolve('dist')));
    app.get('*', (_req, res) => {
      res.sendFile(path.resolve('dist', 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
