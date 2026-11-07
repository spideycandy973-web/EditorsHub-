import React from 'react';
import type { ShapeKind } from '../editor/types';

/* Filled silhouettes for the 18-shape picker, drawn as real SVG paths. */
const D: Record<ShapeKind, string> = {
  ellipse: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z',
  rounded: 'M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z',
  cross: 'M10 3h4v7h7v4h-7v7h-4v-7H3v-4h7V3Z',
  arc: 'M12 3a9 9 0 1 0 8.5 6h-3.3A5.8 5.8 0 1 1 12 6.2V3Z',
  triangle: 'M12 3 21 20H3L12 3Zm0 0v17M3 20h18',
  callout: 'M5 3h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H8.5l-2.2 4-.8-4H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z',
  pie: 'M12 12V3a9 9 0 1 1-9 9h9Z',
  hexagon: 'M12 2.5 20.5 7v10L12 21.5 3.5 17V7L12 2.5Z',
  flower: 'M12 2.5c2.7 0 4.2 2.1 4.2 4.4 2.2-1.8 4.7-1.6 6.1.2 1.7 2.2.4 4.8-1.8 6 2.3.6 3.7 2.9 2.6 5.1-1.1 2.3-3.8 2.5-5.8 1.1-.1 2.5-1.7 4.2-4 4.2s-3.9-1.7-4-4.2c-2 1.4-4.7 1.2-5.8-1.1-1.1-2.2.3-4.5 2.6-5.1-2.2-1.2-3.5-3.8-1.8-6 1.4-1.8 3.9-2 6.1-.2 0-2.3 1.5-4.4 4.2-4.4Z',
  arrow: 'M3 10h11V5l7 7-7 7v-5H3v-4Z',
  trapezoid: 'M6 3h12l3 17H3L6 3Zm0 0L3 20m15-17 3 17M3 20h18',
  crescent: 'M15.5 3A9 9 0 1 0 21 14.5 7.4 7.4 0 0 1 15.5 3Z',
  rectangle: 'M3 3h18v18H3V3Z',
  star: 'M12 2.5 14.9 9l7.1.6-5.4 4.7 1.7 7-6.3-3.8-6.3 3.8 1.7-7L2 9.6 9.1 9 12 2.5Z',
  line: 'M4 17.5 19.5 3l1.5 1.6L5.5 19.1 4 17.5Z',
  beam: 'M3.5 16.8 15.5 4.8l3.7 3.7-12 12-3.7-3.7Zm0 0-1.2 1.2m14.4-14.4 1.2 1.2',
  flag: 'M4 3h10c0 6-2 9-6 18H4V3Zm10 0c-1 5-2 8-4 11',
  seal: 'M3 3h2v2h2V3h2v2h2V3h2v2h2V3h2v2h2V3h2v2h-2v2h2v2h-2v2h2v2h-2v2h2v2h-2v2h-2v-2h-2v2h-2v-2h-2v2h-2v-2H7v2H5v-2H3v-2h2v-2H3v-2h2v-2H3V9h2V7H3V5h2V3H3Z',
  diamond: 'M12 2 22 12 12 22 2 12 12 2Z',
  pentagon: 'M12 2 21 9l-3.5 11h-11L3 9 12 2Z',
  sparkle: 'M12 1.5c1 5.5 2.5 7 8 9.5-5.5 2.5-7 4-8 9.5-1-5.5-2.5-7-8-9.5 5.5-2.5 7-4 8-9.5Z',
  burst: 'M12 1.5 13.8 7l5.4-2.2-2.2 5.4 5.5 1.8-5.5 1.8 2.2 5.4L13.8 17 12 22.5 10.2 17l-5.4 2.2 2.2-5.4L1.5 12l5.5-1.8L4.8 4.8 10.2 7 12 1.5Z',
  heart: 'M12 21S2.5 14.4 2.5 8.4A5.4 5.4 0 0 1 12 5.7a5.4 5.4 0 0 1 9.5 2.7C21.5 14.4 12 21 12 21Z',
  ring: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 3.2a5.8 5.8 0 1 1 0 11.6 5.8 5.8 0 0 1 0-11.6Z',
  halfCircle: 'M3 12a9 9 0 0 1 18 0H3Z',
  polygon: 'M12 2.5 20.5 7v10L12 21.5 3.5 17V7L12 2.5Z',
  customPath: 'M3 18 9 6l4 9 3-5 5 8H3Z',
};

const ANCHORS: Partial<Record<ShapeKind, [number, number][]>> = {
  triangle: [[12, 3], [3, 20], [21, 20]],
  callout: [[6.3, 21]],
  arrow: [[4.2, 17], [18.4, 5.6]],
  trapezoid: [[6, 3], [18, 3], [21, 20], [3, 20]],
  beam: [[3.5, 16.8], [15.5, 4.8]],
  flag: [[4, 3], [14, 3], [4, 21]],
};

export function ShapeGlyph({ kind, size = 30, color = '#b9b9be', editPoints = false }: {
  kind: ShapeKind; size?: number; color?: string; editPoints?: boolean;
}) {
  if (kind === 'arc') {
    const points = ANCHORS.arc ?? [];
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill="none">
        <path d="M18 5 A9 9 0 1 0 18 19 M16.4 8.2 A5.8 5.8 0 1 0 16.4 15.8"
          stroke={color} strokeWidth="1.25" strokeLinecap="round" />
        {editPoints && points.map(([cx, cy], i) => <circle key={i} cx={cx} cy={cy} r="1.45"
          fill="#fff" stroke="#72727a" strokeWidth=".55" />)}
      </svg>
    );
  }
  if (kind === 'flower') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill={color}>
        <ellipse cx="12" cy="6.6" rx="3.5" ry="4.4" />
        <ellipse cx="17.4" cy="12" rx="4.4" ry="3.5" />
        <ellipse cx="12" cy="17.4" rx="3.5" ry="4.4" />
        <ellipse cx="6.6" cy="12" rx="4.4" ry="3.5" />
        <circle cx="12" cy="12" r="2.8" />
      </svg>
    );
  }
  if (kind === 'seal') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill={color}>
        <path d={D.seal} />
      </svg>
    );
  }
  if (kind === 'line') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill="none">
        <path d="M4 19 20 4" stroke={color} strokeWidth="2.2" strokeLinecap="round" />
      </svg>
    );
  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true"
      fill={color} fillRule="evenodd" stroke={color} strokeWidth="0.45" strokeLinejoin="round">
      <path d={D[kind] ?? D.rectangle}
        transform={kind === 'arrow' ? 'rotate(-45 12 12)' : undefined} />
      {editPoints && ANCHORS[kind]?.map(([cx, cy], i) => <circle key={i} cx={cx} cy={cy} r="1.45"
        fill="#fff" stroke="#72727a" strokeWidth=".55" />)}
    </svg>
  );
}
