#!/usr/bin/env node
/**
 * Capture the main UI states at 430x956 for a side-by-side visual audit.
 *
 *   npm run build
 *   npm run preview                       # port 4173
 *   node tools/capture-states.mjs [outDir]
 *
 * Requires the optional headless toolchain (see tools/smoke.mjs header):
 *   npm install --no-save --package-lock=false @sparticuz/chromium playwright-core
 *   node tools/frames-libs.mjs
 *
 * Frames settle for 450 ms so entrance animations are finished before the
 * screenshot — an unsettled frame makes the whole UI look translucent and
 * produces meaningless pixel diffs.
 */
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import chromium from '@sparticuz/chromium';
import { chromium as playwright } from 'playwright-core';

const OUT = resolve(process.argv[2] || '/tmp/editorshub-states');
const BASE = process.env.BASE_URL || 'http://127.0.0.1:4173/';
mkdirSync(OUT, { recursive: true });

const exe = await chromium.executablePath();
const browser = await playwright.launch({
  executablePath: exe, headless: true,
  args: [...chromium.args, '--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu',
    '--disable-software-rasterizer', '--use-angle=swiftshader', '--disable-features=Vulkan'],
});
const page = await browser.newPage({ viewport: { width: 430, height: 956 }, deviceScaleFactor: 1 });
await page.goto(BASE, { waitUntil: 'networkidle' });
await page.waitForSelector('.um-splash', { state: 'detached', timeout: 15000 });
await page.waitForTimeout(350);

const shot = async (name) => {
  await page.waitForTimeout(450);
  await page.screenshot({ path: resolve(OUT, `${name}.png`) });
  console.log('captured', name);
};
const ui = (fn, ...a) => page.evaluate(({ fn, a }) => window.__ui[fn](...a), { fn, a });

await shot('home');
await ui('setSheet', 'settings'); await shot('settings');
await ui('setSheet', 'create'); await shot('create');
await page.locator('.um-lavender.create').first().click();
await page.waitForTimeout(600);
await shot('editor');
await ui('openAdd'); await shot('add');
await ui('setSheet', 'shape'); await shot('shape');
await ui('setSheet', 'none');
await ui('setEffectBrowser', true); await shot('fx');
await ui('setEffectBrowser', false);
await ui('setPresetsDialog', true); await shot('presets');
await ui('setPresetsDialog', false);
await ui('setSheet', 'export'); await shot('export');
await browser.close();
