import { zipSync } from 'fflate';
import type { Asset, Project } from './types';
import { MediaLibrary } from './media';
import { Renderer } from './renderer';
import { clamp, safeFileName } from './utils';

export interface VideoExportOptions {
  scale: number;             // 0.5 / 0.75 / 1
  bitrate: number;           // bits per second
  includeAudio: boolean;
  /** Preferred audio sampling rate for the Web Audio mix (Hz). */
  sampleRate?: number;
}

export interface ExportProgress {
  phase: string;
  ratio: number;
}

export interface VideoMime { mime: string; ext: string }

export function pickVideoMime(): VideoMime {
  if (typeof MediaRecorder === 'undefined') return { mime: '', ext: 'webm' };
  const candidates: { mime: string; ext: string }[] = [
    { mime: 'video/mp4;codecs=avc1.42E01E', ext: 'mp4' },
    { mime: 'video/mp4', ext: 'mp4' },
    { mime: 'video/webm;codecs=vp9', ext: 'webm' },
    { mime: 'video/webm;codecs=vp8', ext: 'webm' },
    { mime: 'video/webm', ext: 'webm' },
  ];
  for (const c of candidates) {
    try { if (MediaRecorder.isTypeSupported(c.mime)) return c; } catch { /* ignore */ }
  }
  return { mime: '', ext: 'webm' };
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(b => b ? resolve(b) : reject(new Error('Canvas encoding failed')), type, quality);
  });
}

/** Real-time render + record. The compositor draws every frame; MediaRecorder
 *  encodes the canvas stream (and mixed audio when requested). */
export async function recordVideo(
  project: Project,
  assets: Record<string, Asset>,
  opts: VideoExportOptions,
  onProgress: (p: ExportProgress) => void,
): Promise<{ blob: Blob; ext: string }> {
  if (typeof MediaRecorder === 'undefined' || !HTMLCanvasElement.prototype.captureStream) {
    throw new Error('This browser does not support canvas recording. Try Chrome, Edge or Safari.');
  }
  const { mime, ext } = pickVideoMime();
  const canvas = document.createElement('canvas');
  const media = new MediaLibrary(assets);
  const renderer = new Renderer(canvas, project, media, opts.scale);
  onProgress({ phase: 'Preparing composition…', ratio: 0 });

  const fps = project.fps;
  const stream = canvas.captureStream(fps);
  let audioCtx: AudioContext | null = null;
  let audioDest: MediaStreamAudioDestinationNode | null = null;
  const sources = new WeakMap<HTMLMediaElement, MediaElementAudioSourceNode>();

  if (opts.includeAudio) {
    try {
      audioCtx = new AudioContext(opts.sampleRate ? { sampleRate: opts.sampleRate } : undefined);
      audioDest = audioCtx.createMediaStreamDestination();
      await audioCtx.resume();
      const audioLayers = project.layers.filter(l =>
        l.visible && l.sourceId && (l.type === 'audio' || (l.type === 'video' && l.style.audio)));
      for (const l of audioLayers) {
        const asset = assets[l.sourceId!];
        if (!asset) continue;
        const el = l.type === 'video' ? media.video(asset, l.id) : media.audio(asset, l.id);
        if (!el) continue;
        let node = sources.get(el);
        if (!node) {
          node = audioCtx.createMediaElementSource(el);
          sources.set(el, node);
        }
        const gain = audioCtx.createGain();
        gain.gain.value = 0.85;
        node.connect(gain);
        gain.connect(audioDest);
        gain.connect(audioCtx.destination);
      }
      for (const track of audioDest.stream.getAudioTracks()) stream.addTrack(track);
    } catch (e) {
      console.warn('Audio capture unavailable, exporting video only', e);
    }
  }

  const chunks: BlobPart[] = [];
  let recorder: MediaRecorder;
  try {
    recorder = mime
      ? new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: opts.bitrate })
      : new MediaRecorder(stream, { videoBitsPerSecond: opts.bitrate });
  } catch (e) {
    throw new Error(`Could not start recorder: ${(e as Error).message}`);
  }
  recorder.ondataavailable = e => { if (e.data && e.data.size > 0) chunks.push(e.data); };
  const stopped = new Promise<void>((resolve, reject) => {
    recorder.onstop = () => resolve();
    recorder.onerror = () => reject(new Error('Recording failed'));
  });

  // Prime the first frame so the capture stream has a real frame to encode.
  await renderer.render(0, true);
  media.sync(project, 0, false);
  await new Promise(r => setTimeout(r, 120));
  recorder.start(250);

  const start = performance.now();
  await new Promise<void>(resolve => {
    const tick = (now: number) => {
      const t = (now - start) / 1000;
      const clamped = Math.min(t, project.duration);
      media.sync(project, clamped, true);
      void renderer.render(clamped, false);
      onProgress({ phase: `Rendering ${clamped.toFixed(1)}s / ${project.duration.toFixed(1)}s`, ratio: clamp(clamped / project.duration, 0, 1) });
      if (t >= project.duration) resolve();
      else requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });

  media.pauseAll();
  await new Promise(r => setTimeout(r, 180));
  if (recorder.state !== 'inactive') recorder.stop();
  await stopped;
  stream.getTracks().forEach(tr => tr.stop());
  if (audioCtx) void audioCtx.close();
  media.dispose();
  onProgress({ phase: 'Finalizing…', ratio: 1 });

  const actualMime = recorder.mimeType || mime || 'video/webm';
  const finalExt = actualMime.includes('mp4') ? 'mp4' : ext;
  const blob = new Blob(chunks, { type: actualMime });
  if (blob.size < 100) throw new Error('Recording produced an empty file. Check that your browser allows canvas capture.');
  return { blob, ext: finalExt };
}

/** Deterministic frame-by-frame export. Each frame seeks videos exactly. */
export async function exportFrameSequence(
  project: Project,
  assets: Record<string, Asset>,
  opts: { scale: number; format: 'png' | 'jpeg'; quality?: number; maxFrames?: number },
  onProgress: (p: ExportProgress) => void,
): Promise<{ blob: Blob; frames: number }> {
  const canvas = document.createElement('canvas');
  const media = new MediaLibrary(assets);
  const renderer = new Renderer(canvas, project, media, opts.scale);
  const total = Math.min(Math.round(project.duration * project.fps), opts.maxFrames ?? 900);
  const files: Record<string, Uint8Array> = {};
  const mime = opts.format === 'png' ? 'image/png' : 'image/jpeg';
  const extension = opts.format === 'png' ? 'png' : 'jpg';
  for (let i = 0; i < total; i++) {
    const t = i / project.fps;
    await renderer.render(t, true);
    const blob = await canvasToBlob(canvas, mime, opts.quality ?? 0.92);
    const buf = new Uint8Array(await blob.arrayBuffer());
    files[`frame_${String(i).padStart(5, '0')}.${extension}`] = buf;
    onProgress({ phase: `Frame ${i + 1} / ${total}`, ratio: (i + 1) / total });
    if (i % 8 === 0) await new Promise(r => setTimeout(r, 0));
  }
  media.dispose();
  onProgress({ phase: 'Building ZIP…', ratio: 1 });
  const zipped = zipSync(files, { level: 0 });
  return { blob: new Blob([zipped as BlobPart], { type: 'application/zip' }), frames: total };
}

/** Export a single frame as PNG/JPEG (used for "Save current frame"). */
export async function renderStill(
  project: Project,
  assets: Record<string, Asset>,
  time: number,
  scale = 1,
  format: 'png' | 'jpeg' = 'png',
): Promise<Blob> {
  const canvas = document.createElement('canvas');
  const media = new MediaLibrary(assets);
  const renderer = new Renderer(canvas, project, media, scale);
  await renderer.render(time, true);
  const blob = await canvasToBlob(canvas, format === 'png' ? 'image/png' : 'image/jpeg', 0.95);
  media.dispose();
  return blob;
}

export const videoFileName = (project: Project, ext: string): string =>
  `${safeFileName(project.name)}.${ext}`;

export const framesFileName = (project: Project): string =>
  `${safeFileName(project.name)}_frames.zip`;
