import { useEffect, useState } from 'react';

/**
 * Cold-launch screen from the reference recording (0.75 s – ~3.0 s): a near-black
 * field, a white double-arc swirl that draws itself and rotates once, and a small
 * loading spinner. It is pure presentation — no controls, no simulated loading,
 * and it ends on a real timer. Reduced-motion users get a short static frame.
 */
export function Splash({ onDone }: { onDone: () => void }) {
  const [leaving, setLeaving] = useState(false);
  useEffect(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const total = reduced ? 350 : 2300;
    const t1 = window.setTimeout(() => setLeaving(true), total - 260);
    const t2 = window.setTimeout(onDone, total);
    return () => { window.clearTimeout(t1); window.clearTimeout(t2); };
  }, [onDone]);

  return (
    <div className={`um-splash ${leaving ? 'leaving' : ''}`} aria-label="EditorsHub is starting">
      <svg className="um-splash-logo" viewBox="0 0 120 120" aria-hidden="true">
        <path className="um-splash-arc outer" d="M60 11a49 49 0 1 0 49 49" />
        <path className="um-splash-arc inner" d="M60 27a33 33 0 1 0 33 33" />
        <circle className="um-splash-dot" cx="60" cy="11" r="3.2" />
      </svg>
      <span className="um-splash-spinner" aria-hidden="true" />
    </div>
  );
}

/** The same mark, at rest, for the About page and the launcher wordmark badge. */
export function SwirlMark({ size = 42 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 120 120" aria-hidden="true"
      fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round">
      <path d="M60 11a49 49 0 1 0 49 49" />
      <path d="M60 27a33 33 0 1 0 33 33" />
      <circle cx="60" cy="11" r="3.2" fill="currentColor" stroke="none" />
    </svg>
  );
}
