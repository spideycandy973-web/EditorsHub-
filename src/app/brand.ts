/**
 * SINGLE CUSTOMISATION POINT — "master base" configuration.
 *
 * The on-screen identity below is a plain text label taken from the reference
 * screenshots the user supplied; every glyph and pixel of artwork in this
 * repository is drawn from scratch. All branding stays editable in-app
 * (About → Customise app) and persists in localStorage. No decompiled code,
 * third-party bitmap/font/audio asset or re-signed package is included.
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

/** Visible identity from the 28 reference JPGs supplied by the user. The
 *  package id stays `com.editorshub` as requested; this only changes what is
 *  drawn on screen. Everything is editable in About → Customise app. */
export const REFERENCE_LOOK: BrandOverride = {
  wordmark: 'ULTRA MOTION',
  domain: 'ultramotionofficial.com',
  accent: '#00ffa8',
  lavender: '#d8dcff',
  home: '#121215',
};

export const BASE_BRAND: Brand = {
  appName: 'EditorsHub',
  wordmark: 'ULTRA MOTION',
  domain: 'ultramotionofficial.com',
  packageName: 'com.editorshub',
  version: '4.5.0',
  website: 'https://github.com/spideycandy973-web/EditorsHub-',
  links: [
    { label: 'Website', url: 'https://github.com/spideycandy973-web/EditorsHub-', icon: 'globe' },
    { label: 'Telegram', url: 'https://t.me/', icon: 'send' },
    { label: 'TikTok', url: 'https://www.tiktok.com/', icon: 'music' },
  ],
  /* Exact decoded resource tokens (A10/A9/S8/A7/Y1/P5/W3). These are struct facts
     from the APK's colours.xml, not copied artwork. */
  palette: {
    home: '#121215', card: '#1c1c1f', input: '#262629', line: '#2e2e33',
    text: '#f2f3f5', muted: '#8c8e93', lavender: '#d8dcff',
    accent: '#00ffa8', pink: '#ff3b7f',
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
