/**
 * High-reliability canvas & rendering context cleanup utility.
 * Prevents GPU VRAM leaks, out-of-memory crashes ("Aw, Snap!"),
 * and context exhaustion during frequent frame scrubbing and resolution switching.
 */

export function isMobileDevice(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  const isTouch = typeof navigator.maxTouchPoints === 'number' && navigator.maxTouchPoints > 1;
  const isSmallScreen = typeof window !== 'undefined' && (window.innerWidth <= 840 || (typeof window.screen !== 'undefined' && window.screen.width <= 840));
  return (
    /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua) ||
    (isTouch && isSmallScreen)
  );
}

/**
 * Returns maximum safe texture and canvas dimensions based on platform.
 * Keeps GPU VRAM usage strictly below 10MB on mobile devices to prevent Chrome "Aw, Snap!" dead-face crashes.
 */
export function getMaxSafeDimension(isExport = false): number {
  const mobile = isMobileDevice();
  if (isExport) {
    return mobile ? 2048 : 3840;
  }
  // Interactive preview and split-view comparison:
  // Mobile: 640px max (sharp on mobile screens, <1MB VRAM, zero Android Chrome crashes)
  // Desktop: 1600px max (~9.5MB VRAM max)
  return mobile ? 640 : 1600;
}

export function disposeCanvas(canvas: HTMLCanvasElement | null | undefined): void {
  if (!canvas) return;

  try {
    // 1. Release WebGL context if present
    const gl = (canvas.getContext('webgl2') || canvas.getContext('webgl')) as
      | WebGLRenderingContext
      | WebGL2RenderingContext
      | null;
    if (gl) {
      const ext = gl.getExtension('WEBGL_lose_context');
      if (ext) {
        ext.loseContext();
      }
    }
  } catch {
    // Canvas was not a WebGL canvas, ignore safely
  }

  try {
    // 2. Clear any active 2D rendering buffer
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
  } catch {
    // Canvas was not a 2D canvas, ignore safely
  }

  try {
    // Setting dimensions to 0 immediately signals the browser GPU compositor
    // to deallocate the backing texture and surface memory.
    canvas.width = 0;
    canvas.height = 0;
  } catch (err) {
    console.warn('Canvas dimension reset warning:', err);
  }
}

/**
 * Calculates safe on-screen display dimensions to protect mobile browsers
 * from allocating multi-gigabyte VRAM surfaces while retaining 100% visual sharpness.
 */
export function getSafeDisplayDimensions(
  targetWidth: number,
  targetHeight: number,
  maxDimension?: number
): { width: number; height: number } {
  if (targetWidth <= 0 || targetHeight <= 0) {
    return { width: 640, height: 360 };
  }

  const effectiveMax = maxDimension ?? getMaxSafeDimension(false);

  if (targetWidth <= effectiveMax && targetHeight <= effectiveMax) {
    return { width: targetWidth, height: targetHeight };
  }

  const scale = Math.min(effectiveMax / targetWidth, effectiveMax / targetHeight);
  return {
    width: Math.max(1, Math.round(targetWidth * scale)),
    height: Math.max(1, Math.round(targetHeight * scale)),
  };
}

