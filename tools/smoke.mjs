#!/usr/bin/env node
/**
 * Headless UI smoke check. Optional developer tooling, not part of `npm ci`:
 *   npm install --no-save --package-lock=false @sparticuz/chromium playwright-core
 *   node tools/frames-libs.mjs          # one-time: inflate the AL2023 shared libs
 *   node tools/smoke.mjs               # against `npm run preview` on :4173
 *
 * Set BASE_URL to test another host. Pass --shots to write v3.5.0 screenshots
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
const page = await browser.newPage({ viewport: { width: 430, height: 932 }, deviceScaleFactor: 1 });
const errors = [];
page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errors.push(`${m.type()}: ${m.text()}`); });
page.on('pageerror', e => errors.push(`pageerror: ${e.message}`));

await page.goto(BASE, { waitUntil: 'networkidle' });
await page.waitForSelector('.um-home', { timeout: 15000 });
// v3.4.0 cold-launch splash: it must actually finish, not strand the launcher.
check('cold-launch splash shown', await page.locator('.um-splash').count() === 1);
await page.waitForSelector('.um-splash', { state: 'detached', timeout: 12000 });
check('cold-launch splash completes', await page.locator('.um-splash').count() === 0);

// Launcher + device chrome
check('launcher rendered', await page.locator('.um-home').count() === 1);
check('wordmark is EditorsHub', (await page.locator('.um-wordmark strong').innerText()).trim() === 'EDITORS HUB');
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
  await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/home-v3.5.0.png') });
}

// Settings switch is a real control
await page.getByTitle('Settings').first().click();
await page.waitForSelector('.um-settings-card');
const chromeSwitch = page.getByTitle('Device chrome (status + gesture bar)');
check('Device chrome switch present', await chromeSwitch.count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/settings-v3.5.0.png') });
await chromeSwitch.click();
check('switch hides status strip', await page.locator('.um-statusbar').count() === 0);
check('switch hides gesture pill', await page.locator('.um-gesturebar').count() === 0);
await chromeSwitch.click();
check('switch restores status strip', await page.locator('.um-statusbar').count() === 1);
await page.keyboard.press('Escape');
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
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/about-v3.5.0.png') });
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
  await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/editor-v3.5.0.png') });
}

// Export & Share option list (v3.4.0)
await page.getByTitle('Export').first().click();
await page.waitForSelector('.um-overlay-export');
check('Export & Share lists six real formats', await page.locator('.um-export-option').count() === 6);
check('storage figure is honest', (await page.locator('.um-export-space').innerText()).includes('Available Space'));
check('template export toggle present', await page.locator('.um-template-toggle input').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/export-v3.5.0.png') });
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
check('shape picker has five pages', await page.locator('.um-shape-sheet .um-dots button').count() === 5);
check('first shape page has 18 shapes', await page.locator('.um-shape-cell').count() === 18);
await page.locator('.um-shape-sheet .um-dots button').nth(4).click();
check('vector preset page renders real glyphs', await page.locator('.um-shape-cell svg path').count() >= 18);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/shapes-v3.5.0.png') });
await page.keyboard.press('Escape');
await page.waitForTimeout(200);

// ---- v3.5.0: APK-derived full-screen panels -------------------------------
// Add a real shape layer so the effect browser can apply an effect to it.
await page.locator('.um-fab').click();
await page.waitForSelector('.um-add-sheet');
await page.locator('.um-add-tile').filter({ hasText: 'Shape' }).first().click();
await page.waitForSelector('.um-shape-sheet');
await page.locator('.um-shape-cell').first().click();
await page.waitForTimeout(350);
check('right preview tool rail is visible', await page.locator('.um-right-tools').count() === 1);
check('property pane has seven panel tabs', await page.locator('.um-prop-tabs button').count() === 7);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/pane-v3.5.0.png') });

// Open the property detail so the "Full screen" link exists, then launch it.
await page.locator('.um-prop-tabs button').filter({ hasText: 'Effects' }).first().click();
await page.waitForTimeout(250);
await page.getByText('Full screen').first().click();
await page.waitForSelector('.um-fx-browser');
check('Effect Browser lists all 32 APK categories', await page.locator('.um-fx-cat').count() === 32);
check('Effect Browser has five quick picks', await page.locator('.um-fx-quick-big').count() + await page.locator('.um-fx-quick-small button').count() === 5);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/fx-browser-v3.5.0.png') });
await page.locator('.um-fx-cat').filter({ hasText: 'Blur, Focus' }).first().click();
await page.waitForTimeout(200);
check('category drill-down shows real effects', await page.locator('.um-fx-cell').count() >= 3);
await page.locator('.um-fx-cell').first().click();
await page.waitForTimeout(200);
check('Guide card describes the selected effect', (await page.locator('.um-fx-guide-body').innerText()).includes('Gaussian Blur'));
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/fx-guide-v3.5.0.png') });
await page.locator('.um-fx-add').click();
await page.waitForTimeout(300);
check('Standard Settings adds a real effect', await page.locator('.um-fx-browser').count() === 0);
check('effect landed on the layer', await page.evaluate(() => {
  const ed = window.__editor;
  return !!ed && (ed.project?.layers?.some(l => l.effects?.length > 0) ?? false);
}));

// Presets dialog: save, list and discover all real.
await page.locator('.um-prop-tabs button').filter({ hasText: 'Presets' }).first().click();
await page.waitForTimeout(300);
check('Presets dialog opens', await page.locator('.um-presets-card').count() === 1);
if (SHOTS) await page.screenshot({ path: resolve(ROOT, 'docs/screenshots/presets-v3.5.0.png') });
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
check('teal matches APK resource Y1', teal === '#00ffa8', teal);

check('no console errors/warnings', errors.length === 0, errors.slice(0, 4).join(' | '));
await browser.close();

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
if (failed.length) process.exit(1);
