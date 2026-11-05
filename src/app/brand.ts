/**
 * SINGLE CUSTOMISATION POINT — "master base" configuration.
 *
 * This is an original, clean-room implementation. Change this one file to
 * rebrand, recolour and relink the whole application before customising
 * anything else; every screen reads its identity from here. No value below is
 * copied from the reference application's trademark, package or assets.
 */
export interface BrandLink { label: string; url: string; icon: 'globe' | 'send' | 'music' }

export interface Brand {
  /** Short app name (used in About, titles, file names). */
  appName: string;
  /** Wordmark shown in the launcher header and the About page. */
  wordmark: string;
  /** Small line printed under the wordmark. */
  domain: string;
  /** Android application id — must match the Gradle/Capacitor config when changed. */
  packageName: string;
  /** Human-readable version shown on the About page. */
  version: string;
  /** Source / home page. */
  website: string;
  /** Rows rendered in Settings, in order. */
  links: BrandLink[];
  /** Base palette; applied to CSS custom properties at startup. */
  palette: {
    home: string; card: string; input: string; line: string;
    text: string; muted: string; lavender: string; accent: string; pink: string;
  };
  /** Short legal line on the About page. */
  legal: string;
}

export const BRAND: Brand = {
  appName: 'EditorsHub',
  wordmark: 'EDITORS HUB',
  domain: 'editorshub.app',
  packageName: 'com.editorshub',
  version: '4.1.0',
  website: 'https://github.com/spideycandy973-web/EditorsHub-',
  links: [
    { label: 'Website', url: 'https://github.com/spideycandy973-web/EditorsHub-', icon: 'globe' },
    { label: 'Telegram', url: 'https://t.me/', icon: 'send' },
    { label: 'TikTok', url: 'https://www.tiktok.com/', icon: 'music' },
  ],
  // Recovered from the reference APK's decoded resources.arsc (apk-analysis/values.json).
  palette: {
    home: '#121214', card: '#1c1c1e', input: '#262628', line: '#2e2e33',
    text: '#f3f3f6', muted: '#92929c', lavender: '#d8dcff',
    accent: '#01ffa7', pink: '#ff3b7f',
  },
  legal: 'EditorsHub is an original, independent motion editor. No assets or code from any other application are included.',
};

/** Push the palette into the CSS custom properties so a single edit recolours the app. */
export function applyBrand(): void {
  const r = document.documentElement.style;
  const p = BRAND.palette;
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
  document.title = BRAND.appName;
}
