#!/usr/bin/env node
/**
 * Reference-recording frame extractor.
 *
 * The 10:29 screen recording supplied by the user is 301,626,146 bytes, which
 * is above the 100 MiB (104,857,600 byte) per-file transfer limit of the agent
 * workspace. The user therefore supplies it as 2-4 smaller MP4 parts. This tool
 * joins their timelines back together and produces:
 *
 *   analysis/manifest.json          part durations + timeline offsets
 *   analysis/scenes.json            scene-change timestamps (whole recording)
 *   analysis/frames/part-01/*.jpg   1 fps, 320 px wide review frames
 *   analysis/contact-sheet.html     frame browser with global timestamps
 *
 * Usage:
 *   FFMPEG=/path/to/ffmpeg FFPROBE=/path/to/ffprobe \
 *     node tools/video-frames.mjs part-0.mp4 part-1.mp4 part-2.mp4
 *   node tools/video-frames.mjs --fps 2 --out analysis parts/*.mp4
 *
 * ffmpeg is NOT a project dependency; it is only needed for this offline audit.
 * Any recent ffmpeg build works (apt install ffmpeg, or npm i ffmpeg-static).
 */
import { spawn, spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

const require = createRequire(import.meta.url);

function findBin(envName, fallback, npmPackage) {
  if (process.env[envName] && existsSync(process.env[envName])) return process.env[envName];
  const check = spawnSync(fallback, ['-version'], { encoding: 'utf8' });
  if (check.status === 0) return fallback;
  try {
    const p = require(npmPackage);
    if (typeof p === 'string' && existsSync(p)) return p;
    if (p?.path && existsSync(p.path)) return p.path;
  } catch { /* not installed */ }
  console.error(`Cannot locate ${fallback}. Set ${envName} or install ${npmPackage}.`);
  process.exit(2);
}

const FFMPEG = findBin('FFMPEG', 'ffmpeg', 'ffmpeg-static');
const FFPROBE = findBin('FFPROBE', 'ffprobe', '@ffprobe-installer/ffprobe');

const argv = process.argv.slice(2);
const option = (name, fallback) => {
  const i = argv.indexOf(name);
  if (i === -1) return fallback;
  const v = argv[i + 1];
  argv.splice(i, 2);
  return v;
};
const fps = Math.max(0.25, Number(option('--fps', '1')) || 1);
const outDir = resolve(option('--out', 'analysis'));
const parts = argv.filter(a => !a.startsWith('--'));

if (!parts.length) {
  console.error('No MP4 parts supplied.\nExample: node tools/video-frames.mjs part-0.mp4 part-1.mp4');
  process.exit(2);
}
for (const p of parts) {
  if (!existsSync(p)) { console.error(`Missing part: ${p}`); process.exit(2); }
}

function run(bin, args, label) {
  return new Promise((ok, fail) => {
    const child = spawn(bin, args, { stdio: ['ignore', 'ignore', 'pipe'] });
    let stderr = '';
    child.stderr.on('data', d => { stderr += d.toString(); });
    child.on('error', fail);
    child.on('close', code => code === 0 ? ok(stderr) : fail(new Error(`${label} exited ${code}\n${stderr.slice(-2000)}`)));
  });
}

function durationOf(file) {
  const probe = spawnSync(FFPROBE, [
    '-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', file,
  ], { encoding: 'utf8' });
  const n = Number((probe.stdout || '').trim());
  if (Number.isFinite(n) && n > 0) return n;
  const m = /Duration:\s*(\d+):(\d+):(\d+\.\d+)/.exec(probe.stderr || '');
  if (!m) throw new Error(`Cannot read duration of ${file}`);
  return Number(m[1]) * 3600 + Number(m[2]) * 60 + Number(m[3]);
}

/** Parse `showinfo` pts_time values out of ffmpeg's log. */
function sceneTimes(stderr) {
  const times = [];
  const re = /Parsed_showinfo_\d+ @ [^\n]*n:\s*\d+ pts:\s*\d+ pts_time:([\d.]+)/g;
  let m;
  while ((m = re.exec(stderr))) times.push(Number(m[1]));
  return times;
}

async function main() {
  mkdirSync(outDir, { recursive: true });
  const framesRoot = join(outDir, 'frames');
  mkdirSync(framesRoot, { recursive: true });

  let offset = 0;
  const manifest = { fps, generatedAt: new Date().toISOString(), parts: [], totalDuration: 0 };
  const scenes = [];
  const frames = [];

  for (let i = 0; i < parts.length; i++) {
    const file = resolve(parts[i]);
    const label = `part-${String(i + 1).padStart(2, '0')}`;
    const dir = join(framesRoot, label);
    mkdirSync(dir, { recursive: true });
    const duration = durationOf(file);
    console.log(`${label}: ${basename(file)} — ${duration.toFixed(3)}s, starts at ${offset.toFixed(3)}s`);

    // Uniform review frames, downscaled so the whole recording stays browsable.
    await run(FFMPEG, [
      '-hide_banner', '-loglevel', 'error', '-y', '-i', file,
      '-vf', `fps=${fps},scale=320:-2`, '-q:v', '4',
      join(dir, 'f-%05d.jpg'),
    ], `${label} frame extraction`);

    // Scene-change timestamps from the same part.
    const log = await run(FFMPEG, [
      '-hide_banner', '-y', '-i', file,
      '-vf', "select='gt(scene,0.22)',showinfo", '-an', '-f', 'null', '-',
    ], `${label} scene detection`).catch(() => '');
    const localScenes = sceneTimes(String(log));
    for (const t of localScenes) scenes.push({ time: +(offset + t).toFixed(3), part: label, localTime: t });

    const files = readdirSync(dir).filter(f => f.endsWith('.jpg')).sort();
    files.forEach((f, n) => frames.push({
      part: label, file: `${label}/${f}`, localTime: +(n / fps).toFixed(3),
      time: +(offset + n / fps).toFixed(3),
    }));

    manifest.parts.push({ label, file: basename(file), duration, offset, frames: files.length });
    offset += duration;
  }

  manifest.totalDuration = +offset.toFixed(3);
  writeFileSync(join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  writeFileSync(join(outDir, 'scenes.json'), JSON.stringify({ count: scenes.length, scenes }, null, 2) + '\n');
  writeFileSync(join(outDir, 'frames.json'), JSON.stringify({ count: frames.length, frames }, null, 2) + '\n');

  const cards = frames.map(f => `<figure><img loading="lazy" src="frames/${f.file}"><figcaption>${formatTime(f.time)} · ${f.part}</figcaption></figure>`).join('\n');
  writeFileSync(join(outDir, 'contact-sheet.html'), `<!doctype html>
<meta charset="utf-8"><title>Recording frame audit</title>
<style>body{background:#0b0b0d;color:#eee;font:13px system-ui;margin:16px}
h1{font-size:18px}header{position:sticky;top:0;background:#0b0b0d;padding:8px 0}
main{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px}
figure{margin:0;background:#18181c;border-radius:8px;overflow:hidden}img{width:100%;display:block}
figcaption{padding:5px 7px;font-variant-numeric:tabular-nums;color:#a8a8b2}</style>
<h1>${frames.length} review frames · ${formatTime(manifest.totalDuration)} · ${scenes.length} scene changes</h1>
<header>See <code>manifest.json</code>, <code>scenes.json</code> and <code>frames.json</code> for the exact timeline.</header>
<main>${cards}</main>\n`);

  console.log(`\nDone: ${frames.length} frames, ${scenes.length} scene changes, ${manifest.totalDuration}s total.`);
  console.log(`Open ${join(outDir, 'contact-sheet.html')} to review every distinct UI state.`);
}

function formatTime(t) {
  const m = Math.floor(t / 60), s = t - m * 60;
  return `${String(m).padStart(2, '0')}:${s.toFixed(2).padStart(5, '0')}`;
}

main().catch(err => { console.error(err.message || err); process.exit(1); });
