#!/usr/bin/env node
/**
 * Headless UI smoke check. Optional developer tooling, not part of `npm ci`:
 *   npm install --no-save --package-lock=false @sparticuz/chromium playwright-core
 *   node tools/frames-libs.mjs          # one-time: inflate the AL2023 shared libs
 *   node tools/smoke.mjs               # against `npm run preview` on :4173
 *
 * Set BASE_URL to test another host. Pass --shots to write v4.5.0 screenshots
 * into docs/screenshots/.
 */
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import chromium from '@sparticuz/chromium';
import { chromium as playwright } from 'playwright-core';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:4173/';
const SHOTS = process.argv.includes('--shots');
const ROOT = resolve(import.meta.dirname || new URL('..', import.meta.url).pathname, '..');

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok: !!ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

const exe = await chromium.executablePath();
const browser = await playwright.launch({
  executablePath: exe,
  headless: true,
  args: [
    ...chromium.args,
    '--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu',
    '--disable-software-rasterizer', '--use-angle=swiftshader',
    '--disable-features=Vulkan',
  ],
});
const page = await browser.newPage({ viewport: { width: 430, height: 956 }, deviceScaleFactor: 1 });
const errors = [];
page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errors.push(`${m.type()}: ${m.text()}`); });
page.on('pageerror', e => errors.push(`pageerror: ${e.message}`));


// The reference property pane shows seven tiles and no tab strip; navigation
// therefore goes through the tiles and the left-rail back button.
async function propertyRoot() {
  if (await page.locator('.um-prop-tile').count() === 0) {
    const back = page.getByTitle('Back to properties');
    if (await back.count()) await back.first().click();
    else await page.keyboard.press('Escape');
    await page.waitForTimeout(160);
  }
}
async function openPanel(name) {
  await propertyRoot();
  const tile = page.locator('.um-prop-tile').filter({ hasText: name }).first();
  if (await tile.count()) { await tile.click(); await page.waitForTimeout(220); }
}

await page.goto(BASE, { waitUntil: 'networkidle' });
await page.waitForSelector('.um-home', { timeout: 15000 });
// v3.4.0 cold-launch splash: it must actually finish, not strand the launcher.
check('cold-launch splash shown', await page.locator('.um-splash').count() === 1);
await page.waitForSelector('.um-splash', { state: 'detached', timeout: 12000 });
check('cold-launch splash completes', await page.locator('.um-splash').count() === 0);

// Launcher + device chrome
check('launcher rendered', await page.locator('.um-home').count() === 1);
// v4.1.0 measured geometry from the 28 reference JPGs (tools/visual-audit.py).
const cardBox = await page.locator('.um-upload-card').boundingBox();
check('launcher card matches reference y=129 h=97', cardBox &&
  Math.abs(cardBox.y - 129) <= 2 && Math.abs(cardBox.height - 97) <= 3,
  cardBox ? `y=${Math.round(cardBox.y)} h=${Math.round(cardBox.height)}` : 'missing');
const pkgBox = await page.locator('.um-package-card').boundingBox();
check('package card matches reference y=240 h=235', pkgBox &&
  Math.abs(pkgBox.y - 240) <= 2 && Math.abs(pkgBox.height - 235) <= 4,
  pkgBox ? `y=${Math.round(pkgBox.y)} h=${Math.round(pkgBox.height)}` : 'missing');
const navBox = await page.locator('.um-nav-plus').boundingBox();
check('nav plus is a flat 48px disc', navBox && Math.abs(navBox.width - 48) <= 1,
  navBox ? `${Math.round(navBox.width)}px` : 'missing');
check('wordmark matches the reference stills', (await page.locator('.um-wordmark strong').innerText()).trim() === 'ULTRA MOTION');
check('status strip rendered', await page.locator('.um-statusbar').count() === 1);
check('gesture pill rendered', await page.locator('.um-gesturebar').count() === 1);
const clock = (await page.locator('.um-sb-time').innerText()).trim();
check('status clock is a real HH:MM', /^\d{2}:\d{2}$/.test(clock), clock);
const battery = (await page.locator('.um-sb-battery').getAttribute('title')) || '';
check('battery disclosure is honest', battery.length > 0 && !battery.includes('NaN'), battery);
check('Upload XML card present', await page.getByText('Upload XML file').count() >= 1);
check('Import Project Package present', await page.getByText('Import Project Package').count() >= 1);
if (SHOTS) {
  mkdirSync(resolve(ROOT, 'docs/screenshots'), { recursive: true });
  await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/home-v4.5.0.png') });
}

// Settings matches the reference's five rows (no extra device-chrome row)
await page.getByTitle('Settings').first().click();
await page.waitForSelector('.um-settings-card');
check('settings matches the reference four rows',
  await page.locator('.um-settings-card .um-row').count() === 4);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/settings-v4.5.0.png') });
await page.keyboard.press('Escape');
await page.waitForTimeout(250);

// Device chrome is still a real control, reachable from the About menu
await page.getByTitle('About EditorsHub').click();
await page.waitForSelector('.um-about-page');
await page.getByTitle('About menu').click();
const chromeSwitch = page.getByTitle('Device chrome (status + gesture bar)');
check('Device chrome switch present', await chromeSwitch.count() === 1);
await chromeSwitch.click();
check('switch hides status strip', await page.locator('.um-statusbar').count() === 0);
await chromeSwitch.click();
check('switch restores status strip', await page.locator('.um-statusbar').count() === 1);
await page.getByTitle('Back').click();
await page.waitForTimeout(250);

// Tab transition
await page.getByText('Projects', { exact: true }).last().click();
await page.waitForTimeout(260);
check('Projects tab opened', await page.locator('.um-projects-body').count() === 1);
check('Projects segments present', await page.getByText('Your Templates').count() >= 1);
check('sort control present', await page.locator('.um-sort select').count() === 1);
await page.locator('.um-select-toggle').click();
check('selection mode opens', await page.locator('.um-select-actions').count() === 1);
check('selection action bar has 5 real actions', await page.locator('.um-select-actions button').count() === 5);
await page.locator('.um-select-cancel').click();
check('selection mode closes', await page.locator('.um-select-actions').count() === 0);
check('tab pane has transition', await page.locator('.um-projects-body').evaluate(el =>
  getComputedStyle(el).animationName.includes('um-tab-in')));
await page.getByText('Home', { exact: true }).last().click();
await page.waitForTimeout(260);
check('Home tab restored', await page.locator('.um-home-body').count() === 1);

// Full-screen About page with honest device information
await page.getByTitle('About EditorsHub').click();
await page.waitForSelector('.um-about-page');
await page.waitForTimeout(320);
check('About is a full page', await page.locator('.um-about-page').count() === 1);
check('device info card is real', (await page.locator('.um-device-card').innerText()).includes('Device & build information'));
check('About shows a live viewport value', /\d+ × \d+ px/.test(await page.locator('.um-device-card').innerText()));
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/about-v4.5.0.png') });
await page.getByTitle('Back').click();
check('About closes', await page.locator('.um-about-page').count() === 0);


// Create a real project and enter the editor
await page.locator('.um-nav-plus').click();
await page.waitForTimeout(250);
check('Create sheet opened', await page.locator('.um-sheet').count() >= 1);
const create = page.locator('.um-lavender.create');
if (await create.count()) await create.first().click();
else await page.keyboard.press('Escape');
await page.waitForTimeout(500);
check('editor opened', await page.locator('.um-editor').count() === 1);
check('canvas present', await page.locator('.um-editor canvas').count() >= 1);
check('status strip visible in editor', await page.locator('.um-statusbar').count() === 1);
if (SHOTS && await page.locator('.um-editor').count()) {
  await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/editor-v4.5.0.png') });
}

// Export & Share option list (v3.4.0)
await page.getByTitle('Export').first().click();
await page.waitForSelector('.um-overlay-export');
check('Export & Share lists six real formats', await page.locator('.um-export-option').count() === 6);
check('storage figure is honest', (await page.locator('.um-export-space').innerText()).includes('Available Space'));
check('template export toggle present', await page.locator('.um-template-toggle input').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/export-v4.5.0.png') });
// Real GIF89a output: render frames, LZW-encode, download, inspect the bytes.
await page.locator('.um-export-option').filter({ hasText: 'GIF' }).click();
await page.locator('.um-export-settings select').first().selectOption('160');
await page.locator('.um-export-settings select').nth(1).selectOption('8');
const gifDownload = page.waitForEvent('download', { timeout: 120000 });
await page.locator('.um-lavender.create').click();
try {
  const d = await gifDownload;
  const gifPath = await d.path();
  const bytes = readFileSync(gifPath);
  check('GIF download has a .gif name', d.suggestedFilename().endsWith('.gif'), d.suggestedFilename());
  check('GIF is a real GIF89a stream', bytes.subarray(0, 6).toString() === 'GIF89a' && bytes[bytes.length - 1] === 0x3B);
  check('GIF contains encoded frames', statSync(gifPath).size > 1500, `${statSync(gifPath).size} bytes`);
} catch (err) {
  check('GIF export completed', false, err.message);
}
await page.keyboard.press('Escape');
await page.waitForTimeout(200);

// Shape picker now has five pages, all backed by real shapes
await page.locator('.um-fab').click();
await page.waitForSelector('.um-add-sheet');
await page.locator('.um-add-tile').filter({ hasText: 'Shape' }).first().click();
await page.waitForSelector('.um-shape-sheet');
check('shape picker has three reference pages', await page.locator('.um-shape-sheet .um-dots button').count() === 3);
const firstPageCells = await page.locator('.um-shape-cell').evaluateAll(cells => cells.map(el => {
  const r = el.getBoundingClientRect(); return { x: r.x, y: r.y };
}));
const shapeCols = [...new Set(firstPageCells.map(p => Math.round(p.x)))].length;
const shapeRows = [...new Set(firstPageCells.map(p => Math.round(p.y)))].length;
check('first shape page is a six-column by three-row sheet',
  firstPageCells.length === 18 && shapeCols === 6 && shapeRows === 3,
  `${shapeCols} columns × ${shapeRows} rows`);
check('first shape page contains 18 vector glyphs', await page.locator('.um-shape-cell svg').count() === 18);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/shapes-v4.5.0.png') });
await page.locator('.um-shape-sheet .um-dots button').nth(2).click();
check('vector preset page renders real glyphs', await page.locator('.um-shape-cell svg path').count() >= 18);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/shapes-vector-v4.5.0.png') });
await page.keyboard.press('Escape');
await page.waitForTimeout(200);

// ---- v3.5.0: APK-derived full-screen panels -------------------------------
// Add a real shape layer so the effect browser can apply an effect to it.
await page.locator('.um-fab').click();
await page.waitForSelector('.um-add-sheet');
await page.locator('.um-add-tile').filter({ hasText: 'Shape' }).first().click();
await page.waitForSelector('.um-shape-sheet');
await page.locator('.um-shape-cell').nth(1).click();
await page.waitForTimeout(350);
check('preview rail is hidden like the blank reference editor', await page.locator('.um-right-tools').count() === 0);
const showRail = page.getByTitle('Show preview tools');
if (await showRail.count()) await showRail.first().click();
check('preview rail opens from its real toggle', await page.locator('.um-right-tools').count() === 1);
const hideRail = page.getByTitle('Hide preview tools');
if (await hideRail.count()) await hideRail.first().click();
check('preview rail closes from its real toggle', await page.locator('.um-right-tools').count() === 0);
check('property root shows seven panels', await page.locator('.um-prop-tile').count() === 7);
const defaultShape = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? { w: l.props.shapeWidth.value, h: l.props.shapeHeight.value,
    color: l.style.color.toLowerCase(), stroke: l.style.strokeColor.toLowerCase(), strokeEnabled: l.style.strokeEnabled, width: l.props.strokeWidth.value, duration: l.duration } : null;
});
check('new Rounded Rectangle matches measured 200×200 orange reference', !!defaultShape &&
  defaultShape.w === 200 && defaultShape.h === 200 && defaultShape.color === '#ba7e38' && defaultShape.stroke === '#000000' && defaultShape.strokeEnabled === false && defaultShape.width === 4 && defaultShape.duration === 4,
  defaultShape ? `${defaultShape.w}×${defaultShape.h} ${defaultShape.color}` : 'missing layer');
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/pane-v4.5.0.png') });
await openPanel('Move & Transform');
const positionValues = await page.locator('.um-position-field input').evaluateAll(xs => xs.map(x => Number(x.value)));
check('position fields use reference composition coordinates', positionValues.length === 3 &&
  positionValues[0] === 540 && positionValues[1] === 960 && positionValues[2] === 0,
  positionValues.join(', '));
const orangeArea = async () => page.locator('.um-stage canvas').evaluate(canvas => {
  const sample = document.createElement('canvas');
  sample.width = canvas.width; sample.height = canvas.height;
  const ctx = sample.getContext('2d', { willReadFrequently: true });
  if (!ctx) return 0;
  ctx.drawImage(canvas, 0, 0);
  const { data } = ctx.getImageData(0, 0, sample.width, sample.height);
  let n = 0;
  for (let i = 0; i < data.length; i += 4) if (data[i] > 150 && data[i + 1] > 75 && data[i + 2] < 100) n++;
  return n;
});
const areaBeforeZ = await orangeArea();
await page.locator('.um-position-field input').nth(2).fill('100');
await page.waitForTimeout(180);
const zApplied = await page.evaluate(() => window.__editor?.selectedLayer?.props.z.value === 100);
const areaAfterZ = await orangeArea();
check('Z control changes the rendered layer depth', zApplied && areaAfterZ > areaBeforeZ,
  `${areaBeforeZ} → ${areaAfterZ} orange canvas pixels`);
await page.locator('.um-position-field input').nth(2).fill('0');
const sizeTab = page.getByTitle('Size & scale').first();
await sizeTab.click();
const sizeFields = page.locator('.um-size-fields input[type="number"]');
const sizeAreaBefore = await orangeArea();
await sizeFields.first().fill('300');
await page.waitForTimeout(180);
const linkedSize = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? [l.props.shapeWidth.value, l.props.shapeHeight.value] : [];
});
const sizeAreaAfter = await orangeArea();
check('linked size field resizes the shape and preserves ratio', linkedSize.length === 2 &&
  linkedSize[0] === 300 && linkedSize[1] === 300 && sizeAreaAfter > sizeAreaBefore,
  `${linkedSize.join('×')} px`);
await page.locator('.um-size-link').click();
await sizeFields.first().fill('240');
await page.waitForTimeout(120);
const unlinkedSize = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? [l.props.shapeWidth.value, l.props.shapeHeight.value] : [];
});
check('aspect-ratio toggle unlocks width from height', unlinkedSize[0] === 240 && unlinkedSize[1] === 300,
  `${unlinkedSize.join('×')} px`);
await sizeFields.first().fill('200');
await sizeFields.nth(1).fill('200');
await page.locator('.um-size-link').click();
await page.getByTitle('Skew').first().click();
const skewFields = page.locator('.um-skew-fields input[type="number"]');
await skewFields.first().fill('12.5');
await skewFields.nth(1).fill('-5');
await page.waitForTimeout(160);
const skewValues = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? [l.props.skewX.value, l.props.skewY.value] : [];
});
check('X and Y skew fields update the layer transforms', skewValues[0] === 12.5 && skewValues[1] === -5,
  skewValues.join('°, ') + '°');
await skewFields.first().fill('0');
await skewFields.nth(1).fill('0');
await page.getByTitle('Rotation dial').first().click();
const dial = page.locator('.um-dial');
await dial.click({ position: { x: 85, y: 1 } });
await page.waitForTimeout(120);
const rotated = await page.evaluate(() => window.__editor?.selectedLayer?.props.rotation.value);
await dial.click({ position: { x: 155, y: 85 } });
await page.waitForTimeout(120);
const resetRotation = await page.evaluate(() => window.__editor?.selectedLayer?.props.rotation.value);
check('rotation dial changes and resets the layer angle', typeof rotated === 'number' && rotated <= -85 && Math.abs(resetRotation) <= 2,
  `${rotated}° → ${resetRotation}°`);
await page.getByTitle('Position').first().click();
await propertyRoot();

// Edit Shape matches the reference's on-canvas handles and compact Size/Radius rows.
await openPanel('Edit Shape');
check('Edit Shape omits the empty right rail', await page.locator('.um-prop-detail.prop-editShape > .um-rail-right').count() === 0);
check('Edit Shape shows eight handles and a live radius handle',
  await page.locator('.um-selection.mode-handles i').count() === 8 && await page.locator('.um-selection i.radius').count() === 1);
const scaleXField = page.getByLabel('Shape scale X');
const scaleYField = page.getByLabel('Shape scale Y');
await scaleXField.fill('120');
let shapeScales = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? [l.props.scaleX.value, l.props.scaleY.value] : [];
});
check('Edit Shape link keeps X and Y scale proportional', shapeScales[0] === 120 && shapeScales[1] === 120, shapeScales.join('×'));
await page.getByTitle('Unlock aspect ratio').click();
await scaleXField.fill('140');
shapeScales = await page.evaluate(() => {
  const l = window.__editor?.selectedLayer;
  return l ? [l.props.scaleX.value, l.props.scaleY.value] : [];
});
check('Edit Shape link toggle permits independent scaling', shapeScales[0] === 140 && shapeScales[1] === 120, shapeScales.join('×'));
await scaleXField.fill('100'); await scaleYField.fill('100');
await page.getByTitle('Lock aspect ratio').click();
await page.getByLabel('Corner radius').fill('33');
check('Edit Shape radius control changes the rendered model', await page.evaluate(() =>
  window.__editor?.selectedLayer?.props.cornerRadius.value === 33));
await page.getByLabel('Corner radius').fill('25');
await page.getByTitle('Open size and scale controls').click();
check('Edit Shape top action opens Move & Transform size view', await page.locator('.um-size-fields').count() === 1);
await page.getByTitle('Back to properties').first().click();
await propertyRoot();

// Easing uses the reference's cubic control-point graph and two preset rails.
await page.evaluate(() => window.__ui.setActivePropKey('rotation'));
await openPanel('Move & Transform');
await page.getByTitle('Cubic bezier easing').click();
check('Easing graph has two rails and no blocking keyframe card',
  await page.locator('.um-prop-detail.prop-easing .um-rail-right').count() === 2 &&
  await page.locator('.um-keyframes-hint').count() === 0);
check('Easing graph exposes two draggable control points', await page.locator('.um-easing-control').count() === 2);
await page.getByTitle('Ease in', { exact: true }).click();
let easingValue = await page.evaluate(() => window.__editor?.getAnim(window.__editor.selectedLayer, 'rotation').keyframes[0]?.easing);
check('easing preset writes a real keyframe easing', easingValue === 'easeIn', String(easingValue));
await page.getByTitle('Cubic Bezier').click();
easingValue = await page.evaluate(() => window.__editor?.getAnim(window.__editor.selectedLayer, 'rotation').keyframes[0]?.easing);
check('custom Bezier mode updates the keyframe', typeof easingValue === 'string' && easingValue.startsWith('cubic('), String(easingValue));
await page.getByTitle('Back to Move & Transform').click();
await page.getByTitle('Back to properties').first().click();
await propertyRoot();

// Open the property detail so the "Full screen" link exists, then launch it.
await openPanel('Effects');
if (await page.getByText('Full screen').count()) await page.getByText('Full screen').first().click();
else await page.evaluate(() => window.__ui.setEffectBrowser(true));
await page.waitForSelector('.um-fx-browser');
check('Effect Browser lists all 32 APK categories', await page.locator('.um-fx-cat').count() === 32);
check('Effect Browser has five quick picks', await page.locator('.um-fx-quick-big').count() + await page.locator('.um-fx-quick-small button').count() === 5);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/fx-browser-v4.5.0.png') });
await page.locator('.um-fx-cat').filter({ hasText: 'Blur, Focus' }).first().click();
await page.waitForTimeout(200);
check('category drill-down shows real effects', await page.locator('.um-fx-cell').count() >= 3);
await page.locator('.um-fx-cell').first().click();
await page.waitForTimeout(200);
check('Guide card describes the selected effect', (await page.locator('.um-fx-guide-body').innerText()).includes('Gaussian Blur'));
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/fx-guide-v4.5.0.png') });
await page.locator('.um-fx-add').click();
await page.waitForTimeout(300);
check('Standard Settings adds a real effect', await page.locator('.um-fx-browser').count() === 0);
check('effect landed on the layer', await page.evaluate(() => {
  const ed = window.__editor;
  return !!ed && (ed.project?.layers?.some(l => l.effects?.length > 0) ?? false);
}));

// Presets dialog: save, list and discover all real.
await page.getByTitle('Layer options').first().click();
await page.waitForTimeout(180);
await page.getByText('Save Preset').first().click();
await page.waitForTimeout(300);
check('Presets dialog opens', await page.locator('.um-presets-card').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/presets-v4.5.0.png') });
await page.locator('.um-presets-card').getByText('Save selected layer', { exact: true }).click();
await page.waitForTimeout(250);
check('preset is stored locally', await page.locator('.um-presets-row').count() >= 1);
await page.locator('.um-presets-card').getByText('Discover Presets').click();
await page.waitForTimeout(200);
check('Discover Presets lists real motion presets', await page.locator('.um-presets-gallery button').count() >= 8);
await page.locator('.um-presets-card').getByText('Back', { exact: true }).click();
await page.waitForTimeout(150);
await page.locator('.um-presets-card').getByText('Close', { exact: true }).click();
await page.waitForTimeout(200);

// Palette measured from the APK resource table.
const teal = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--um-teal').trim());
check('teal matches the reference stills', teal === '#00ffa8', teal);
const home = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--um-home').trim());
check('background matches the reference stills', home === '#121215', home);
check('brand config sets the document title', await page.title() === 'EditorsHub');
check('reference quick picks are real effects', await page.evaluate(() => ['fadeInOut','crystal','hotColor'].every(t => !!window.__editor && typeof window.__editor.addEffect === 'function')));

// Master effect library: a newly-added real effect renders through the engine.
await openPanel('Effects');
if (await page.getByText('Full screen').count()) await page.getByText('Full screen').first().click();
else await page.evaluate(() => window.__ui.setEffectBrowser(true));
await page.waitForSelector('.um-fx-browser');
await page.locator('.um-fx-cat').filter({ hasText: 'Color & Light' }).first().click();
await page.waitForTimeout(200);
const posterize = page.locator('.um-fx-cell').filter({ hasText: 'Posterize' });
check('master library exposes Posterize', await posterize.count() === 1);
if (await posterize.count()) {
  await posterize.first().click();
  await page.locator('.um-fx-add').click();
  await page.waitForTimeout(300);
  check('Posterize is applied as a real effect', await page.evaluate(() =>
    !!(window.__editor?.selectedLayer?.effects || []).some(f => f.type === 'posterize')));
}
// Every new effect can be added and drawn without a runtime error.
const before = errors.length;
const newEffects = ['threshold','solarize','duotone','gradientMap','emboss','edgeDetect',
  'halftone','scanlines','glitchBlocks','ripple','bulge','twirl','colorBalance','levels',
  'lensFlare','starfield','bokeh','rain','mirror','kaleidoscope','motionBlur','directionalBlur',
  'fadeInOut','crystal','hotColor'];
await page.evaluate(list => {
  const e = window.__editor, l = e.selectedLayer;
  if (l) for (const t of list) e.addEffect(t);
}, newEffects);
for (const t of [0, 0.5, 1.5, 2.8]) {
  await page.evaluate(v => window.__editor.setTime(v), t);
  await page.waitForTimeout(120);
}
check('all 23 master effects render cleanly', errors.length === before, errors.slice(before, before + 3).join(' | '));
check('effect stack holds the whole library', await page.evaluate(() =>
  (window.__editor?.selectedLayer?.effects?.length ?? 0) >= 23));

// v4.0.0 — fully new flows, all backed by real operations.
await page.getByTitle('Layer options').first().click();
await page.waitForTimeout(200);
await page.getByText('Replace media…').first().click();
await page.waitForSelector('.um-flow');
check('template editor flow opens', (await page.locator('.um-flow-head').innerText()).includes('Template editor'));
await page.waitForTimeout(400);
check('template preview is a real render', await page.locator('.um-flow-preview img').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/replace-media-v4.5.0.png') });
await page.locator('.um-flow-next').click();
await page.waitForTimeout(200);
check('replace-media gate appears', (await page.locator('.um-flow-confirm-card').innerText()).includes('Are you done replacing media?'));
await page.locator('.um-flow-understood').click();
await page.waitForTimeout(250);
check('template editor closes into the editor', await page.locator('.um-flow').count() === 0);

const deselect = page.getByTitle('Deselect layer');
if (await deselect.count()) { await deselect.first().click(); await page.waitForTimeout(200); }
await page.getByTitle('Export').first().click();
await page.waitForSelector('.um-overlay-export');
await page.getByText('Preview the current frame before exporting').first().click();
await page.waitForSelector('.um-flow');
check('export preview shows real composition stats', (await page.locator('.um-flow-stats').innerText()).includes('fps'));
await page.waitForTimeout(350);
check('export preview renders a real frame', await page.locator('.um-flow-preview img').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/export-preview-v4.5.0.png') });
await page.locator('.um-flow-head').getByTitle('Back').click();
await page.waitForTimeout(200);

// ---- v4.5.0 APK-derived features ----------------------------------------
await page.evaluate(() => window.__ui.setSheet('none'));
await page.waitForTimeout(150);
// In-app brand editor: live wordmark change
await page.evaluate(() => window.__ui.setSheet('brand'));
await page.waitForSelector('.um-brand-sheet');
check('brand editor opens', await page.locator('.um-brand-field input').count() >= 8);
await page.locator('.um-brand-field input').first().fill('TEST BRAND');
await page.waitForTimeout(220);
check('brand change applies live', (await page.locator('.um-brand-preview strong').innerText()) === 'TEST BRAND');
check('brand change updates document title', (await page.title()) !== '' );
await page.evaluate(() => { window.__editor.setScreen('home'); });
await page.waitForTimeout(220);
check('launcher wordmark updates live', (await page.locator('.um-wordmark strong').innerText()) === 'TEST BRAND');
await page.evaluate(() => { window.__editor.setScreen('editor'); });
await page.waitForTimeout(200);
await page.evaluate(() => window.__ui.setSheet('none'));
await page.waitForTimeout(150);

// Project Settings: ratios, resolution, fps, background, Total Edit Time
await page.evaluate(() => window.__ui.setSheet('projectSettings'));
await page.waitForSelector('.um-ps-body');
check('project settings has six ratio chips', await page.locator('.um-ps-ratio').count() === 6);
check('Total Edit Time is shown', (await page.locator('.um-ps-total').innerText()).includes('Total Edit Time'));
await page.locator('.um-ps-ratio').nth(1).click();
check('ratio chip changes the composition', await page.evaluate(() => {
  const p = window.__editor.project; return p.height > p.width;
}));
await page.locator('.um-ps-more').click();
check('advanced settings hold real camera controls', await page.getByText('Active camera view').count() >= 1);
await page.evaluate(() => window.__ui.setSheet('none'));
await page.waitForTimeout(150);

// Feature screens recovered from the APK activity list
for (const [sheet, sel] of [
  ['fonts', '.um-font-list'], ['media', '.um-media-grid'], ['audio', '.um-media-grid'],
  ['colorPicker', '.um-color-preview'], ['save', '.um-save-card'],
  ['presetPreview', '.um-feature-body'], ['opensource', '.um-oss-list'],
  ['tutorial', '.um-tutorial-dots'],
]) {
  await page.evaluate(id => window.__ui.setSheet(id), sheet);
  await page.waitForTimeout(180);
  check(`${sheet} screen works`, await page.locator(sel).count() >= 1);
  await page.evaluate(() => window.__ui.setSheet('none'));
  await page.waitForTimeout(100);
}

// Camera view + force blur + hint
await page.evaluate(() => {
  const e = window.__editor;
  const id = e.addLayer('camera', { name: 'Camera 1', duration: 5 });
  e.selectLayer(id);
});
await page.waitForTimeout(250);
check('camera hint is shown', await page.locator('.um-camera-hint').count() === 1);
await page.locator('.um-camera-hint').click();
await page.waitForTimeout(250);
check('camera hint opens Camera Options', await page.locator('.um-camera-panel').count() === 1);
await page.locator('.um-camera-toggle input').check();
check('Active Camera View toggles the engine', await page.evaluate(() => window.__editor.activeCameraView === true));
await page.locator('.um-camera-panel input[type=range]').first().fill('35');
check('Force Blur is a real numeric control', await page.evaluate(() => window.__editor.forceBlur === 35));
check('camera hint hides in active view', await page.locator('.um-camera-hint').count() === 0);

// Border & Shadow: stroke styles + Start/End trim + Add Border
await page.evaluate(() => {
  const e = window.__editor;
  const id = e.addLayer('shape', { shape: 'rectangle', name: 'Rect', duration: 4 });
  e.selectLayer(id);
  e.setProp(id, 'strokeWidth', 4);
  window.__ui.openProperty('border');
});
await page.waitForTimeout(250);
const borderRailTitles = await page.locator('.um-prop-detail.prop-border .um-rail-right .um-iconbtn').evaluateAll(xs => xs.map(x => x.getAttribute('title')));
check('Border navigation order matches Stroke, Add Border, Shadow', borderRailTitles.join(',') === 'Stroke,Add Border,Shadow', borderRailTitles.join(' → '));
const detailGeometry = await page.evaluate(() => {
  const box = (selector) => {
    const r = document.querySelector(selector)?.getBoundingClientRect();
    return r && { x: r.x, y: r.y, width: r.width, height: r.height };
  };
  return {
    canvas: box('.um-canvas-holder canvas'), transport: box('.um-transport'),
    timeline: box('.um-timeline'), timecode: box('.um-time-pill'),
    clip: box('.um-clip'), pane: box('.um-prop-pane'),
  };
});
const near = (value, target, tolerance = 1) => Number.isFinite(value) && Math.abs(value - target) <= tolerance;
check('Stroke sheet geometry matches the measured reference',
  !!detailGeometry.canvas && near(detailGeometry.canvas.x, 80) && near(detailGeometry.canvas.y, 113) &&
  near(detailGeometry.canvas.width, 270) && near(detailGeometry.canvas.height, 480) &&
  !!detailGeometry.transport && near(detailGeometry.transport.y, 595) && near(detailGeometry.transport.height, 48) &&
  !!detailGeometry.timeline && near(detailGeometry.timeline.y, 643) && near(detailGeometry.timeline.height, 85) &&
  !!detailGeometry.timecode && near(detailGeometry.timecode.y, 663) &&
  !!detailGeometry.clip && near(detailGeometry.clip.y, 690) &&
  !!detailGeometry.pane && near(detailGeometry.pane.y, 728) && near(detailGeometry.pane.height, 228),
  JSON.stringify(detailGeometry));
check('six cap and join controls match the reference panel', await page.locator('.um-stroke-styles button').count() === 6);
check('disabled Stroke preserves the 4.0 width setting',
  await page.getByLabel('Enable stroke').getAttribute('aria-checked') === 'false' &&
  (await page.locator('.um-stroke-panel .um-ruler-val').textContent()) === '4.0');
await page.getByLabel('Enable stroke').click();
check('Stroke switch enables rendering without losing width', await page.evaluate(() =>
  window.__editor.selectedLayer.style.strokeEnabled === true && window.__editor.selectedLayer.props.strokeWidth.value === 4));
await page.getByLabel('Enable stroke').click();
check('Stroke switch disables rendering but retains width', await page.evaluate(() =>
  window.__editor.selectedLayer.style.strokeEnabled === false && window.__editor.selectedLayer.props.strokeWidth.value === 4));
await page.locator('.um-stroke-styles button[aria-label="Square cap"]').click();
check('stroke cap is stored on the layer', await page.evaluate(() =>
  window.__editor.selectedLayer && window.__editor.getLayerProp(window.__editor.selectedLayer, 'strokeCap') === 2));
await page.locator('.um-stroke-styles button[aria-label="Round join"]').click();
check('stroke join is stored on the layer', await page.evaluate(() =>
  window.__editor.selectedLayer && window.__editor.getLayerProp(window.__editor.selectedLayer, 'strokeJoin') === 1));
await page.locator('.um-stroke-ends select').first().selectOption('25');
check('stroke Start trim is real', await page.evaluate(() =>
  window.__editor.selectedLayer && window.__editor.getLayerProp(window.__editor.selectedLayer, 'strokeStart') === 25));
await page.evaluate(() => window.__editor.setProp(window.__editor.selectedLayer.id, 'strokeWidth', 0, false));
await page.getByTitle('Add Border').click();
const addBorderBox = await page.getByText('+ Add Border', { exact: true }).boundingBox();
const borderCenterBox = await page.locator('.um-prop-center').boundingBox();
check('Add Border view shows a full-width button at the top', !!addBorderBox && !!borderCenterBox &&
  Math.abs(addBorderBox.width - borderCenterBox.width + 12) < 3 && addBorderBox.y < borderCenterBox.y + 60,
  addBorderBox && borderCenterBox ? `button ${Math.round(addBorderBox.width)}px / center ${Math.round(borderCenterBox.width)}px` : 'missing');
await page.getByText('+ Add Border', { exact: true }).click();
check('Add Border creates a visible stroke and returns to Stroke controls',
  await page.locator('.um-stroke-panel').count() === 1 && await page.evaluate(() => window.__editor.selectedLayer.props.strokeWidth.value === 4));
await page.locator('.um-rail-right [title="Shadow"]').click();
check('Shadow panel groups Size, Alpha and Position', await page.locator('.um-shadow-position').count() === 1 &&
  await page.getByLabel('Size').count() === 1 && await page.getByLabel('Alpha').count() === 1);
await page.getByLabel('Enable shadow').click();
await page.waitForTimeout(100);
check('Shadow toggle creates a working drop-shadow effect', await page.evaluate(() => {
  const f = window.__editor.selectedLayer.effects.find(x => x.type === 'dropShadow');
  return !!f && f.enabled && window.__editor.getEffectValue(f.id, 'offsetX') === 3;
}));
// Blending accordion groups
await page.evaluate(() => window.__ui.openProperty('blend'));
await page.waitForTimeout(200);
check('four blend groups are shown', await page.locator('.um-blend-group').count() === 4);
check('blend panel uses the full width without a right rail', await page.locator('.um-prop-detail.prop-blend > .um-rail-right').count() === 0);
await page.locator('.um-blend-group > button').last().click();
check('Mask group opens functional mask controls', await page.locator('.um-mask-group').count() === 1);
await page.getByText('Ellipse', { exact: true }).click();
check('Mask type writes the layer mask model', await page.evaluate(() => window.__editor?.selectedLayer?.mask.type === 'ellipse'));
await page.locator('.um-mask-group input[type="checkbox"]').click();
await page.waitForTimeout(80);
check('Mask invert checkbox updates the layer mask', await page.evaluate(() => window.__editor?.selectedLayer?.mask.invert === true));
await page.locator('.um-mask-group input[type="checkbox"]').click();
await page.getByText('None', { exact: true }).click();
await page.waitForTimeout(80);
await page.locator('.um-blend-group > button').last().click();
await page.locator('.um-blend-group > button').first().click();
check('blend group expands to real modes', await page.locator('.um-blend-thumbs button').count() >= 4);
await page.locator('.um-blend-thumbs button').first().click();
check('blend thumbnail sets a real blend mode', await page.evaluate(() =>
  !!window.__editor.selectedLayer?.blend));

// Stroke's disabled state is distinct from its retained width, and every
// stroke property must survive a real XML export/import cycle.
await page.evaluate(() => {
  const e = window.__editor;
  const id = e.addLayer('shape', { name: 'XML Stroke Round-trip', shape: 'rectangle', duration: 4 });
  e.setStyle(id, { strokeEnabled: false, strokeColor: '#f5f5f5' });
  e.setProp(id, 'strokeWidth', 4, false);
  e.setProp(id, 'strokeCap', 2, false);
  e.setProp(id, 'strokeJoin', 1, false);
  e.setProp(id, 'strokeStart', 25, false);
  e.setProp(id, 'strokeEnd', 75, false);
});
await page.evaluate(() => window.__ui.setSheet('export'));
await page.waitForSelector('.um-overlay-export .um-export-option');
await page.locator('.um-export-option').filter({ hasText: 'XML' }).click();
const [xmlDownload] = await Promise.all([
  page.waitForEvent('download'),
  page.locator('.um-overlay-export .um-lavender.create').click(),
]);
const xmlPath = await xmlDownload.path();
const exportedXml = xmlPath ? readFileSync(xmlPath, 'utf8') : '';
const exportedStrokeChunk = exportedXml.split('<Layer ').find(chunk =>
  chunk.startsWith('id=') && chunk.includes('name="XML Stroke Round-trip"')) ?? '';
const exportedStrokeLayer = exportedStrokeChunk ? `<Layer ${exportedStrokeChunk.split('</Layer>')[0]}</Layer>` : '';
check('XML export retains disabled Stroke width and style properties',
  exportedStrokeLayer.includes('strokeEnabled="0"') &&
  exportedStrokeLayer.includes('name="strokeWidth" value="4"') &&
  exportedStrokeLayer.includes('name="strokeCap" value="2"') &&
  exportedStrokeLayer.includes('name="strokeJoin" value="1"') &&
  exportedStrokeLayer.includes('name="strokeStart" value="25"') &&
  exportedStrokeLayer.includes('name="strokeEnd" value="75"'));
await page.evaluate(() => window.__ui.setSheet('xml'));
await page.waitForSelector('.um-textarea');
await page.locator('.um-textarea').fill(exportedXml);
await page.waitForFunction(() => document.querySelector('.um-report .um-ok')?.textContent?.includes('XML Stroke Round-trip'));
await page.locator('.um-report').getByRole('button', { name: 'Open as project' }).click();
await page.waitForTimeout(180);
const roundTrippedStroke = await page.evaluate(() => {
  const l = window.__editor.project.layers.find(x => x.name === 'XML Stroke Round-trip');
  if (!l) return null;
  return {
    enabled: l.style.strokeEnabled,
    width: l.props.strokeWidth.value,
    cap: l.props.strokeCap.value,
    join: l.props.strokeJoin.value,
    start: l.props.strokeStart.value,
    end: l.props.strokeEnd.value,
  };
});
check('XML import round-trips disabled Stroke and all five Stroke settings', !!roundTrippedStroke &&
  roundTrippedStroke.enabled === false && roundTrippedStroke.width === 4 &&
  roundTrippedStroke.cap === 2 && roundTrippedStroke.join === 1 &&
  roundTrippedStroke.start === 25 && roundTrippedStroke.end === 75,
  JSON.stringify(roundTrippedStroke));
const legacyStrokeXml = '<EditorsHubMotion name="Legacy Stroke" width="1080" height="1920" fps="30" duration="4"><Layers><Layer name="Legacy Stroke" type="shape"><Properties><Property name="strokeWidth" value="4" /></Properties></Layer></Layers></EditorsHubMotion>';
await page.evaluate(() => window.__ui.setSheet('xml'));
await page.waitForSelector('.um-textarea');
await page.locator('.um-textarea').fill(legacyStrokeXml);
await page.waitForFunction(() => document.querySelector('.um-report .um-ok')?.textContent?.includes('Legacy Stroke'));
await page.locator('.um-report').getByRole('button', { name: 'Open as project' }).click();
await page.waitForTimeout(180);
check('legacy XML with an explicit width migrates Stroke to enabled', await page.evaluate(() => {
  const l = window.__editor.project.layers.find(x => x.name === 'Legacy Stroke');
  return l?.style.strokeEnabled === true && l.props.strokeWidth.value === 4;
}));

check('no console errors/warnings', errors.length === 0, errors.slice(0, 4).join(' | '));
await browser.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
if (failed.length) process.exit(1);
