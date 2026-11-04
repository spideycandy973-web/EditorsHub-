#!/usr/bin/env node
/**
 * Independent structural check of the hand-written GIF encoder in src/editor/gif.ts.
 *
 *   node tools/verify-gif.mjs
 *
 * It strips the browser-only imports/functions, transpiles the pure encoder with
 * esbuild (already present as a Vite dependency), encodes three known frames and
 * verifies the GIF89a header, NETSCAPE loop block, three image descriptors and
 * the 0x3B trailer. A stronger check (Pillow round-trip) is documented in
 * docs/VIDEO_AUDIT.md.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { transformSync } from 'esbuild';
import { pathToFileURL } from 'node:url';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const source = readFileSync('src/editor/gif.ts', 'utf8')
  .split('export interface GifOptions')[0]
  .replace(/^import[^\n]*\n/gm, '');
const tmp = join(tmpdir(), `editorshub-gif-${process.pid}.mjs`);
writeFileSync(tmp, transformSync(source, { loader: 'ts', format: 'esm' }).code);
const { encodeGif } = await import(pathToFileURL(tmp).href);

const w = 64, h = 48, frameCount = 3;
const frames = [0, 1, 2].map(n => {
  const rgba = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4;
    rgba[i] = (x * 4 + n * 90) % 256;
    rgba[i + 1] = (y * 5 + n * 40) % 256;
    rgba[i + 2] = 40 + n * 70;
    rgba[i + 3] = 255;
  }
  return rgba;
});
const blob = encodeGif(frames, w, h, 20);
const b = Buffer.from(await blob.arrayBuffer());

const checks = [
  ['GIF89a header', b.subarray(0, 6).toString() === 'GIF89a'],
  ['logical screen size', b.readUInt16LE(6) === w && b.readUInt16LE(8) === h],
  ['global colour table flag', (b[10] & 0x80) !== 0],
  ['NETSCAPE2.0 loop', b.includes(Buffer.from('NETSCAPE2.0'))],
  ['three image descriptors', (() => {
    const sig = Buffer.from([0x2C, 0, 0, 0, 0, w & 255, w >> 8, h & 255, h >> 8, 0, 8]);
    let found = 0;
    for (let i = 0; i + sig.length <= b.length; i++) if (b.subarray(i, i + sig.length).equals(sig)) found++;
    return found === frameCount;
  })()],
  ['trailer', b[b.length - 1] === 0x3B],
  ['non-trivial payload', b.length > 1500],
];
let ok = true;
for (const [name, pass] of checks) {
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}`);
  ok = ok && pass;
}
console.log(`\n${checks.filter(c => c[1]).length}/${checks.length} checks passed — ${b.length} bytes`);
if (!ok) process.exit(1);
