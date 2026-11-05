/**
 * SINGLE CUSTOMISATION POINT — "master base" configuration.
 *
 * This is an original, clean-room implementation. All branding is fully
 * editable from inside the app (About → Customise app) and persists in
 * localStorage, so the base can be renamed/recoloured without touching code.
 * No third-party trademark or asset is baked in.
 */
export interface BrandLink { label: string; url: string; icon: 'globe' | 'send' | 'music' }

export interface Brand {
  appName: string;
  wordmark: string;
  domain: string;
  packageName: string;
  version: string;
  website: string;
  links: BrandLink[];
  palette: {
    home: string; card: string; input: string; line: string;
    text: string; muted: string; lavender: string; accent: string; pink: string;
  };
  legal: string;
}

const KEY = 'editorshub.brand.v1';

export const BASE_BRAND: Brand = {
  appName: 'EditorsHub',
  wordmark: 'EDITORS HUB',
  domain: 'editorshub.app',
  packageName: 'com.editorshub',
  version: '4.3.0',
  website: 'https://github.com/spideycandy973-web/EditorsHub-',
  links: [
    { label: 'Website', url: 'https://github.com/spideycandy973-web/EditorsHub-', icon: 'globe' },
    { label: 'Telegram', url: 'https://t.me/', icon: 'send' },
    { label: 'TikTok', url: 'https://www.tiktok.com/', icon: 'music' },
  ],
  palette: {
    home: '#121214', card: '#1c1c1e', input: '#262628', line: '#2e2e33',
    text: '#f3f3f6', muted: '#92929c', lavender: '#d8dcff',
    accent: '#01ffa7', pink: '#ff3b7f',
  },
  legal: 'EditorsHub is an original, independent motion editor. No assets or code from any other application are included.',
};

/** Editable fields exposed in the Customise app sheet. */
export type BrandOverride = Partial<Pick<Brand, 'appName' | 'wordmark' | 'domain' | 'website' | 'legal'>> & {
  accent?: string; lavender?: string; home?: string;
};

export function loadBrandOverride(): BrandOverride {
  try { return JSON.parse(localStorage.getItem(KEY) || '{}') as BrandOverride; }
  catch { return {}; }
}
export function saveBrandOverride(o: BrandOverride): void {
  try { localStorage.setItem(KEY, JSON.stringify(o)); } catch { /* storage full */ }
}
export function clearBrandOverride(): void {
  try { localStorage.removeItem(KEY); } catch { /* ignore */ }
}
export function getBrand(): Brand {
  const o = loadBrandOverride();
  return {
    ...BASE_BRAND,
    appName: o.appName ?? BASE_BRAND.appName,
    wordmark: o.wordmark ?? BASE_BRAND.wordmark,
    domain: o.domain ?? BASE_BRAND.domain,
    website: o.website ?? BASE_BRAND.website,
    legal: o.legal ?? BASE_BRAND.legal,
    palette: {
      ...BASE_BRAND.palette,
      home: o.home ?? BASE_BRAND.palette.home,
      lavender: o.lavender ?? BASE_BRAND.palette.lavender,
      accent: o.accent ?? BASE_BRAND.palette.accent,
    },
  };
}

/** Live view of the brand. A Proxy keeps every existing `BRAND.foo` read in
 *  sync with the user's saved overrides without touching each component. */
export const BRAND_CHANGED = 'editorshub:brand-changed';
export const BRAND: Brand = new Proxy({} as Brand, {
  get(_t, key: string) { return (getBrand() as unknown as Record<string, unknown>)[key]; },
  has(_t, key: string) { return key in BASE_BRAND; },
  ownKeys() { return Reflect.ownKeys(BASE_BRAND); },
  getOwnPropertyDescriptor() { return { configurable: true, enumerable: true }; },
});

/** Push the palette into the CSS custom properties so a single edit recolours the app. */
export function applyBrand(): void {
  const r = document.documentElement.style;
  const p = getBrand().palette;
  r.setProperty('--um-home', p.home);
  r.setProperty('--um-card', p.card);
  r.setProperty('--um-card-2', p.input);
  r.setProperty('--um-input', p.input);
  r.setProperty('--um-line', p.line);
  r.setProperty('--um-text', p.text);
  r.setProperty('--um-muted', p.muted);
  r.setProperty('--um-lav', p.lavender);
  r.setProperty('--um-teal', p.accent);
  r.setProperty('--um-pink', p.pink);
  document.title = getBrand().appName;
}
