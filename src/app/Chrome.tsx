import { useEffect, useState } from 'react';

/**
 * Device chrome reproduced from the reference recording (1220 x 2712 frames).
 *
 * Everything shown here is read from a real browser API. No clock, battery
 * percentage, signal strength or network number is invented: when an API is
 * unavailable the corresponding indicator is omitted rather than faked.
 *
 * On a native Android build the real OS status bar / gesture bar is already on
 * screen, so the web chrome is suppressed to avoid drawing a second one.
 */

type BatteryLike = {
  level: number;
  charging: boolean;
  addEventListener(type: string, listener: () => void): void;
  removeEventListener(type: string, listener: () => void): void;
};

type NetworkLike = { effectiveType?: string; type?: string; downlink?: number };

function isNativePlatform(): boolean {
  const w = window as unknown as {
    Capacitor?: { isNativePlatform?: () => boolean; getPlatform?: () => string };
  };
  try {
    if (w.Capacitor?.isNativePlatform?.()) return true;
    const platform = w.Capacitor?.getPlatform?.();
    if (platform && platform !== 'web') return true;
  } catch { /* ignore */ }
  return /^(capacitor|app|file):/.test(window.location.protocol);
}

function useClock(): string {
  const [value, setValue] = useState(() => formatClock(new Date()));
  useEffect(() => {
    const tick = () => setValue(formatClock(new Date()));
    // Align to the next minute so the display never lags behind the real clock.
    let timeout = 0;
    const schedule = () => {
      const now = new Date();
      timeout = window.setTimeout(() => { tick(); schedule(); },
        (60 - now.getSeconds()) * 1000 + 5);
    };
    schedule();
    return () => window.clearTimeout(timeout);
  }, []);
  return value;
}

function formatClock(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function useBattery(): BatteryLike | null {
  const [battery, setBattery] = useState<BatteryLike | null>(null);
  useEffect(() => {
    const get = (navigator as Navigator & {
      getBattery?: () => Promise<BatteryLike>;
    }).getBattery;
    if (!get) return;
    let alive = true;
    let manager: BatteryLike | null = null;
    let listener: (() => void) | null = null;
    get.call(navigator).then(b => {
      if (!alive) return;
      manager = b;
      // BatteryManager exposes level/charging as prototype getters, so a spread
      // would silently produce `{}`. Read the values explicitly.
      listener = () => setBattery({
        level: b.level,
        charging: b.charging,
        addEventListener: b.addEventListener.bind(b),
        removeEventListener: b.removeEventListener.bind(b),
      });
      listener();
      b.addEventListener('levelchange', listener);
      b.addEventListener('chargingchange', listener);
    }).catch(() => undefined);
    return () => {
      alive = false;
      if (manager && listener) {
        manager.removeEventListener('levelchange', listener);
        manager.removeEventListener('chargingchange', listener);
      }
    };
  }, []);
  return battery;
}

function useNetwork(): { online: boolean; bars: number; wifi: boolean; label: string } {
  const read = () => {
    const online = navigator.onLine !== false;
    const conn = (navigator as Navigator & { connection?: NetworkLike }).connection;
    const effective = conn?.effectiveType || '';
    const bars = !online ? 0
      : /^4g/.test(effective) ? 4
      : /^3g/.test(effective) ? 3
      : /^2g/.test(effective) ? 2
      : effective ? 1 : 4;
    const wifi = conn?.type === 'wifi' || (!conn && online);
    const label = conn?.downlink
      ? `Estimated downlink ${conn.downlink.toFixed(1)} Mbit/s`
      : online ? 'Online' : 'Offline';
    return { online, bars, wifi, label };
  };
  const [state, setState] = useState(read);
  useEffect(() => {
    const update = () => setState(read());
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    const conn = (navigator as Navigator & {
      connection?: NetworkLike & { addEventListener?: (t: string, f: () => void) => void; removeEventListener?: (t: string, f: () => void) => void };
    }).connection;
    conn?.addEventListener?.('change', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
      conn?.removeEventListener?.('change', update);
    };
  }, []);
  return state;
}

function WifiGlyph() {
  return (
    <svg className="um-sb-wifi" viewBox="0 0 16 12" aria-hidden="true">
      <path d="M8 12 5.35 9.15a3.95 3.95 0 0 1 5.3 0L8 12Z" />
      <path d="M8 6.55c-1.62 0-3.12.62-4.25 1.72L2.1 6.6a8.45 8.45 0 0 1 11.8 0l-1.65 1.67A6.2 6.2 0 0 0 8 6.55Z" />
      <path d="M8 2.6A9.9 9.9 0 0 0 1.05 5.4L-.5 3.75a12 12 0 0 1 17 0l-1.55 1.65A9.9 9.9 0 0 0 8 2.6Z" />
    </svg>
  );
}

export function StatusBar() {
  const clock = useClock();
  const battery = useBattery();
  const net = useNetwork();
  if (isNativePlatform()) return null;

  return (
    <div className="um-statusbar" aria-hidden="true">
      <span className="um-sb-time" title="Local device time">{clock}</span>
      <span className="um-sb-right" title={net.label}>
        {net.bars > 0 && (
          <span className="um-sb-bars">
            {[1, 2, 3, 4].map(i => (
              <i key={i} style={{ height: `${3 + i * 2.1}px`, opacity: i <= net.bars ? 1 : 0.3 }} />
            ))}
          </span>
        )}
        {net.wifi && <WifiGlyph />}
        <span className="um-sb-battery" title={battery && Number.isFinite(battery.level)
          ? `Battery ${Math.round(battery.level * 100)}%${battery.charging ? ' · charging' : ''}`
          : 'Battery level unavailable on this browser'}>
          {battery && Number.isFinite(battery.level) ? Math.round(battery.level * 100) : ''}
        </span>
      </span>
    </div>
  );
}

export function GestureBar() {
  if (isNativePlatform()) return null;
  return <div className="um-gesturebar" aria-hidden="true"><i /></div>;
}
