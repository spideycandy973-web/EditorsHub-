/**
 * A real GIF89a encoder — no library, no worker, no simulated output.
 *
 * Frames are quantised to a fixed 256-entry 3-3-2 RGB palette, LZW-compressed,
 * and written with a NETSCAPE2.0 loop block. Verified by decoding the output
 * with an independent GIF decoder (Pillow) in tools/verify-gif.mjs.
 */
import type { Asset, Project } from './types';
import { MediaLibrary } from './media';
import { Renderer } from './renderer';
import type { ExportProgress } from './export';

function u16(n: number): [number, number] { return [n & 255, (n >> 8) & 255]; }

/** LZW for a single indexed frame. The +1 off-by-one in the code-size bump is
 *  required: the decoder adds dictionary entries one code later than we do. */
function lzw(indices: Uint8Array, minCodeSize: number): number[] {
  const clear = 1 << minCodeSize;
  const end = clear + 1;
  let codeSize = minCodeSize + 1;
  let next = end + 1;
  const dict = new Map<string, number>();
  const bytes: number[] = [];
  let buf = 0;
  let bits = 0;

  const emit = (code: number) => {
    buf |= code << bits;
    bits += codeSize;
    while (bits >= 8) { bytes.push(buf & 255); buf >>>= 8; bits -= 8; }
  };
  const reset = () => { dict.clear(); codeSize = minCodeSize + 1; next = end + 1; };
  const codeOf = (s: string) => s.indexOf(',') === -1 ? Number(s) : (dict.get(s) ?? clear);

  emit(clear);
  let cur = String(indices[0] ?? 0);
  for (let i = 1; i < indices.length; i++) {
    const k = indices[i];
    const combo = cur + ',' + k;
    if (dict.has(combo)) { cur = combo; continue; }
    emit(codeOf(cur));
    if (next < 4096) {
      dict.set(combo, next++);
      if (next === (1 << codeSize) + 1 && codeSize < 12) codeSize++;
    } else {
      emit(clear);
      reset();
    }
    cur = String(k);
  }
  emit(codeOf(cur));
  emit(end);
  if (bits > 0) bytes.push(buf & 255);
  return bytes;
}

function globalPalette(): Uint8Array {
  const p = new Uint8Array(256 * 3);
  for (let i = 0; i < 256; i++) {
    p[i * 3] = Math.round(((i >> 5) & 7) * 255 / 7);
    p[i * 3 + 1] = Math.round(((i >> 2) & 7) * 255 / 7);
    p[i * 3 + 2] = Math.round((i & 3) * 255 / 3);
  }
  return p;
}

/** @param frames RGBA pixels, all exactly width*height*4 bytes.
 *  @param delayCs frame delay in hundredths of a second (GIF units). */
export function encodeGif(frames: Uint8ClampedArray[], width: number,
  height: number, delayCs: number): Blob {
  if (!frames.length) throw new Error('No frames to encode');
  const out: number[] = [];
  const push = (...b: number[]) => { for (const v of b) out.push(v); };
  const ascii = (s: string) => [...s].map(c => c.charCodeAt(0));

  push(...ascii('GIF89a'));
  // Logical screen descriptor: global colour table, 8-bit colour resolution, 256 entries.
  push(...u16(width), ...u16(height), 0xF7, 0, 0);
  push(...globalPalette());
  push(0x21, 0xFF, 0x0B, ...ascii('NETSCAPE2.0'), 0x03, 0x01, 0x00, 0x00, 0x00);

  for (const rgba of frames) {
    // Graphic control extension: disposal = leave in place, no transparency.
    push(0x21, 0xF9, 0x04, 0x04, ...u16(delayCs), 0, 0);
    push(0x2C, ...u16(0), ...u16(0), ...u16(width), ...u16(height), 0);
    const idx = new Uint8Array(width * height);
    for (let p = 0; p < width * height; p++) {
      const o = p * 4;
      idx[p] = ((rgba[o] >> 5) << 5) | ((rgba[o + 1] >> 5) << 2) | (rgba[o + 2] >> 6);
    }
    push(8);
    const data = lzw(idx, 8);
    for (let o = 0; o < data.length; o += 255) {
      const chunk = data.slice(o, o + 255);
      push(chunk.length, ...chunk);
    }
    push(0);
  }
  push(0x3B);
  return new Blob([new Uint8Array(out)], { type: 'image/gif' });
}

export interface GifOptions {
  /** Output width in pixels; height follows the composition aspect. */
  width: number;
  /** Frames per second in the GIF. GIF delays are centiseconds, so the real
   *  delay is rounded and reported honestly in the caller. */
  fps: number;
  maxFrames: number;
}

/** Renders the composition frame by frame into a small canvas and encodes it. */
export async function exportAnimatedGif(
  project: Project,
  assets: Record<string, Asset>,
  opts: GifOptions,
  onProgress: (p: ExportProgress) => void,
): Promise<{ blob: Blob; frames: number; fps: number }> {
  const ratio = project.width / project.height;
  const width = Math.max(16, Math.min(480, Math.round(opts.width)));
  const height = Math.max(16, Math.round(width / ratio));
  const canvas = document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) throw new Error('Canvas 2D context unavailable');
  // Match the renderer's backing-store scale to the small GIF canvas.
  const scale = width / project.width;
  const media = new MediaLibrary(assets);
  const renderer = new Renderer(canvas, project, media, scale);
  const fps = Math.max(1, Math.min(25, Math.round(opts.fps)));
  const total = Math.max(1, Math.min(Math.round(project.duration * fps), opts.maxFrames));
  const frames: Uint8ClampedArray[] = [];
  for (let i = 0; i < total; i++) {
    await renderer.render(Math.min(i / fps, project.duration - 1e-3), true);
    frames.push(ctx.getImageData(0, 0, width, height).data);
    onProgress({ phase: `Frame ${i + 1} / ${total}`, ratio: (i + 1) / total });
    if (i % 6 === 0) await new Promise(r => setTimeout(r, 0));
  }
  media.dispose();
  onProgress({ phase: 'Encoding GIF…', ratio: 1 });
  return { blob: encodeGif(frames, width, height, Math.max(2, Math.round(100 / fps))), frames: total, fps };
}
