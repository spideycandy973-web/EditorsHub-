import { useEffect, useState } from 'react';
import { ArrowLeft, MoreVertical } from 'lucide-react';
import { useUI } from './context';
import { SwirlMark } from './Splash';
import { BRAND } from './brand';

/** Real values only: nothing on this page is invented. */
function readDeviceInfo() {
  const n = navigator as Navigator & {
    deviceMemory?: number; hardwareConcurrency?: number;
    connection?: { effectiveType?: string };
    userAgentData?: { platform?: string };
  };
  const rows: [string, string][] = [
    ['App', BRAND.appName],
    ['Version', `${BRAND.version} (web/PWA build)`],
    ['Package', BRAND.packageName],
    ['Platform', n.userAgentData?.platform || navigator.platform || 'Unknown'],
    ['Screen', `${window.screen.width} × ${window.screen.height} px`],
    ['Viewport', `${window.innerWidth} × ${window.innerHeight} px`],
    ['Pixel ratio', String(window.devicePixelRatio || 1)],
    ['CPU threads', n.hardwareConcurrency ? String(n.hardwareConcurrency) : 'Not reported'],
    ['Device memory', n.deviceMemory ? `${n.deviceMemory} GB (reported)` : 'Not reported'],
    ['Language', navigator.language || 'Unknown'],
    ['Online', navigator.onLine ? 'Yes' : 'No'],
    ['Standalone', window.matchMedia?.('(display-mode: standalone)').matches ? 'Yes' : 'No'],
  ];
  return rows;
}

export function AboutPage() {
  const ui = useUI();
  const [storage, setStorage] = useState('Measuring…');
  const [menu, setMenu] = useState(false);
  useEffect(() => {
    let alive = true;
    const estimate = (navigator as Navigator & {
      storage?: { estimate?: () => Promise<{ quota?: number; usage?: number }> };
    }).storage?.estimate;
    if (!estimate) { setStorage('Storage API unavailable'); return; }
    estimate.call(navigator.storage).then(e => {
      if (!alive) return;
      const quota = e.quota ? `${(e.quota / 1073741824).toFixed(2)} GB` : 'unknown';
      const usage = e.usage ? `${(e.usage / 1048576).toFixed(1)} MB` : 'unknown';
      setStorage(`${usage} used of ${quota}`);
    }).catch(() => { if (alive) setStorage('Storage estimate unavailable'); });
    return () => { alive = false; };
  }, []);

  return (
    <div className="um-about-page">
      <header className="um-about-top">
        <button type="button" title="Back" aria-label="Back" onClick={() => ui.setAboutOpen(false)}>
          <ArrowLeft size={20} />
        </button>
        <strong>About</strong>
        <button type="button" title="About menu" aria-label="About menu"
          onClick={() => setMenu(v => !v)}><MoreVertical size={19} /></button>
        {menu && (
          <div className="um-about-menu">
            <button type="button" onClick={() => { setMenu(false); window.open(BRAND.website, '_blank', 'noopener'); }}>
              Source code
            </button>
            <button type="button" onClick={() => { setMenu(false); ui.setSheet('help'); }}>
              Editing guide
            </button>
          </div>
        )}
      </header>

      <div className="um-about-scroll">
        <div className="um-about-brand">
          <SwirlMark size={44} />
          <h1>{BRAND.wordmark}</h1>
          <a href={BRAND.website} target="_blank" rel="noopener noreferrer">
            {BRAND.domain}
          </a>
        </div>

        <section className="um-device-card">
          <h2>Device &amp; build information</h2>
          <p className="um-hint">Live values reported by this browser. Nothing here is mocked.</p>
          <dl>
            {readDeviceInfo().map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
            <div><dt>Storage estimate</dt><dd>{storage}</dd></div>
            <div><dt>Build signature</dt><dd>{navigator.userAgent}</dd></div>
          </dl>
        </section>
        <p className="um-about-legal">
          {BRAND.legal}
        </p>
      </div>
    </div>
  );
}
