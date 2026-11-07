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

// Capture the measured selected-shape and property states as well as the
// blank editor states. The default square and its pane geometry are screenshot
// regressions for the reference's 200×200 Rounded Rectangle 1.
await ui('openAdd');
await page.waitForSelector('.um-add-sheet');
await page.locator('.um-add-tile').filter({ hasText: 'Shape' }).first().click();
await page.waitForSelector('.um-shape-sheet');
await page.locator('.um-shape-cell').nth(1).click();
await page.waitForTimeout(300);
await shot('property-root');
for (const [name, label] of [
  ['blend', 'Blending & Opacity'], ['move', 'Move & Transform'],
  ['edit-shape', 'Edit Shape'], ['color', 'Color & Fill'],
  ['border', 'Border & Shadow'], ['effects', 'Effects'],
]) {
  if (name === 'move') await page.evaluate(() => {
    const editor = window.__editor;
    const layer = editor?.selectedLayer;
    if (!editor || !layer) return;
    editor.setProp(layer.id, 'rotation', -3, false);
    editor.addKeyframe(layer.id, 'rotation', 0);
    editor.addKeyframe(layer.id, 'rotation', 1.5);
    const frames = editor.getAnim(layer, 'rotation').keyframes;
    if (frames[0]) editor.updateKeyframe(layer.id, 'rotation', frames[0].id, { value: -3, easing: 'cubic(0.25,0.25,0.75,0.75)' });
    if (frames[1]) editor.updateKeyframe(layer.id, 'rotation', frames[1].id, { value: 3 });
    window.__ui.setActivePropKey('rotation');
  });
  if (name === 'edit-shape') await page.evaluate(() => {
    const editor = window.__editor;
    const layer = editor?.selectedLayer;
    if (!editor || !layer) return;
    editor.clearPropertyKeyframes(layer.id, 'rotation');
    editor.setProp(layer.id, 'rotation', 0, false);
    window.__ui.setActivePropKey('x');
  });
  const tile = page.locator('.um-prop-tile').filter({ hasText: label }).first();
  if (await tile.count()) {
    await tile.click(); await page.waitForTimeout(220); await shot(name);
    if (name === 'move') {
      const size = page.getByTitle('Size & scale').first();
      if (await size.count()) { await size.click(); await page.waitForTimeout(180); await shot('move-size'); }
      const skew = page.getByTitle('Skew').first();
      if (await skew.count()) { await skew.click(); await page.waitForTimeout(180); await shot('move-skew'); }
      const rotation = page.getByTitle('Rotation dial').first();
      if (await rotation.count()) { await rotation.click(); await page.waitForTimeout(180); await shot('move-rotation'); }
      const easing = page.getByTitle('Cubic bezier easing').first();
      if (await easing.count()) { await easing.click(); await page.waitForTimeout(180); await shot('move-easing'); }
      const backToMove = page.getByTitle('Back to Move & Transform').first();
      if (await backToMove.count()) { await backToMove.click(); await page.waitForTimeout(180); }
      const position = page.getByTitle('Position').first();
      if (await position.count()) { await position.click(); await page.waitForTimeout(180); }
    }
    if (name === 'edit-shape') {
      const dimensions = page.getByTitle('Dimensions').first();
      if (await dimensions.count()) { await dimensions.click(); await page.waitForTimeout(180); await shot('edit-shape-dimensions'); }
    }
    if (name === 'border') {
      const addBorderRail = page.getByTitle('Add Border').first();
      if (await addBorderRail.count()) {
        await addBorderRail.click(); await page.waitForTimeout(180); await shot('border-add');
        const addBorder = page.getByText('+ Add Border', { exact: true }).first();
        if (await addBorder.count()) {
          await addBorder.click(); await page.waitForTimeout(180); await shot('border-stroke-enabled');
          const shadow = page.getByTitle('Shadow').first();
          if (await shadow.count()) { await shadow.click(); await page.waitForTimeout(180); await shot('border-shadow'); }
          await page.evaluate(() => {
            const layer = window.__editor?.selectedLayer;
            if (layer) window.__editor.setProp(layer.id, 'strokeWidth', 0, false);
          });
        }
      }
    }
    const back = page.getByTitle('Back to properties').first();
    if (await back.count()) { await back.click(); await page.waitForTimeout(180); }
  }
}
await ui('setSheet', 'none');
await page.evaluate(() => window.__editor.newProject(1080, 1920, true));
await page.waitForTimeout(250);
await ui('setEffectBrowser', true); await shot('fx');
await ui('setEffectBrowser', false);
await ui('setPresetsDialog', true); await shot('presets');
await ui('setPresetsDialog', false);
await ui('setSheet', 'export'); await shot('export');
await browser.close();
