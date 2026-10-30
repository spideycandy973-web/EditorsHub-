export type Point3 = [number, number, number];
export type Mesh3D = { vertices: Point3[]; faces: number[][] };

function normalizeMesh(vertices: Point3[], faces: number[][]): Mesh3D {
  if (!vertices.length || !faces.length) throw new Error('The model has no usable mesh geometry.');
  const mins: Point3 = [Infinity, Infinity, Infinity];
  const maxs: Point3 = [-Infinity, -Infinity, -Infinity];
  for (const point of vertices) for (let axis = 0; axis < 3; axis++) {
    mins[axis] = Math.min(mins[axis], point[axis]);
    maxs[axis] = Math.max(maxs[axis], point[axis]);
  }
  const center = mins.map((value, axis) => (value + maxs[axis]) / 2) as Point3;
  const extent = Math.max(maxs[0] - mins[0], maxs[1] - mins[1], maxs[2] - mins[2], .0001);
  return {
    vertices: vertices.map((point) => [
      Number(((point[0] - center[0]) * 2 / extent).toFixed(4)),
      Number(((point[1] - center[1]) * 2 / extent).toFixed(4)),
      Number(((point[2] - center[2]) * 2 / extent).toFixed(4)),
    ]),
    faces: faces.filter((face) => face.length >= 3 && face.every((index) => index >= 0 && index < vertices.length)),
  };
}

export function parseObjMesh(source: string): Mesh3D {
  if (source.length > 24 * 1024 * 1024) throw new Error('OBJ files over 24 MB are not supported in the mobile model importer.');
  const vertices: Point3[] = [];
  const faces: number[][] = [];
  for (const rawLine of source.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const [command, ...values] = line.split(/\s+/);
    if (command === 'v' && vertices.length < 12000) {
      const point = values.slice(0, 3).map(Number);
      if (point.length === 3 && point.every(Number.isFinite)) vertices.push(point as Point3);
    } else if (command === 'f' && faces.length < 18000) {
      const face = values.map((value) => {
        const parsed = Number.parseInt(value.split('/')[0], 10);
        return Number.isFinite(parsed) ? (parsed < 0 ? vertices.length + parsed : parsed - 1) : -1;
      });
      for (let index = 1; index < face.length - 1 && faces.length < 18000; index++) faces.push([face[0], face[index], face[index + 1]]);
    }
  }
  return normalizeMesh(vertices, faces);
}

export function createPrimitiveMesh(kind: 'Cube' | 'Sphere' | 'Pyramid' | 'Plane' | 'Torus'): Mesh3D {
  if (kind === 'Cube') return normalizeMesh(
    [[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]],
    [[0,1,2,3],[4,7,6,5],[0,4,5,1],[3,2,6,7],[0,3,7,4],[1,5,6,2]],
  );
  if (kind === 'Pyramid') return normalizeMesh(
    [[-1,-.8,-1],[1,-.8,-1],[1,-.8,1],[-1,-.8,1],[0,1,0]],
    [[0,3,2,1],[0,1,4],[1,2,4],[2,3,4],[3,0,4]],
  );
  if (kind === 'Plane') return normalizeMesh([[-1,0,-1],[1,0,-1],[1,0,1],[-1,0,1]],[[0,1,2,3]]);

  const vertices: Point3[] = [];
  const faces: number[][] = [];
  if (kind === 'Sphere') {
    const rings = 10; const segments = 18;
    for (let ring = 0; ring <= rings; ring++) {
      const phi = Math.PI * ring / rings;
      for (let segment = 0; segment < segments; segment++) {
        const theta = 2 * Math.PI * segment / segments;
        vertices.push([Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta)]);
      }
    }
    for (let ring = 0; ring < rings; ring++) for (let segment = 0; segment < segments; segment++) {
      const next = (segment + 1) % segments;
      const a = ring * segments + segment; const b = ring * segments + next;
      const c = (ring + 1) * segments + next; const d = (ring + 1) * segments + segment;
      faces.push([a,b,c,d]);
    }
    return normalizeMesh(vertices, faces);
  }
  const majorSegments = 24; const minorSegments = 8; const majorRadius = .68; const minorRadius = .28;
  for (let major = 0; major < majorSegments; major++) for (let minor = 0; minor < minorSegments; minor++) {
    const u = 2 * Math.PI * major / majorSegments; const v = 2 * Math.PI * minor / minorSegments;
    const radius = majorRadius + minorRadius * Math.cos(v);
    vertices.push([radius * Math.cos(u), minorRadius * Math.sin(v), radius * Math.sin(u)]);
  }
  for (let major = 0; major < majorSegments; major++) for (let minor = 0; minor < minorSegments; minor++) {
    const a = major * minorSegments + minor; const b = ((major + 1) % majorSegments) * minorSegments + minor;
    const c = ((major + 1) % majorSegments) * minorSegments + (minor + 1) % minorSegments;
    const d = major * minorSegments + (minor + 1) % minorSegments;
    faces.push([a,b,c,d]);
  }
  return normalizeMesh(vertices, faces);
}

function hexToRgb(color: string): [number, number, number] {
  const match = color.match(/^#([\da-f]{3}|[\da-f]{6})$/i);
  if (!match) return [238, 184, 79];
  const hex = match[1].length === 3 ? [...match[1]].map((part) => part + part).join('') : match[1];
  return [0, 2, 4].map((index) => Number.parseInt(hex.slice(index, index + 2), 16)) as [number, number, number];
}

export function drawMesh3D(
  context: CanvasRenderingContext2D,
  mesh: Mesh3D,
  options: { size: number; color: string; rotationX: number; rotationY: number; rotationZ?: number; depth?: number },
) {
  const { size, color, rotationX, rotationY, rotationZ = 0, depth = 0 } = options;
  const rx = rotationX * Math.PI / 180; const ry = rotationY * Math.PI / 180; const rz = rotationZ * Math.PI / 180;
  const cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
  const cameraDistance = 4.2 - Math.max(-1, Math.min(1, depth / 1000));
  const transformed = mesh.vertices.map(([x0,y0,z0]) => {
    const y1 = y0 * cx - z0 * sx; const z1 = y0 * sx + z0 * cx;
    const x2 = x0 * cy + z1 * sy; const z2 = -x0 * sy + z1 * cy;
    const x3 = x2 * cz - y1 * sz; const y3 = x2 * sz + y1 * cz;
    const perspective = cameraDistance / Math.max(1.2, cameraDistance - z2);
    return { x: x3 * perspective * size / 2, y: -y3 * perspective * size / 2, z: z2, world: [x3,y3,z2] as Point3 };
  });
  const faces = mesh.faces.map((indices) => {
    const points = indices.map((index) => transformed[index]).filter((point): point is typeof transformed[number] => Boolean(point));
    const averageDepth = points.reduce((sum, point) => sum + point.z, 0) / Math.max(1, points.length);
    let light = .58;
    if (points.length >= 3) {
      const a = points[0].world, b = points[1].world, c = points[2].world;
      const ux = b[0]-a[0], uy = b[1]-a[1], uz = b[2]-a[2];
      const vx = c[0]-a[0], vy = c[1]-a[1], vz = c[2]-a[2];
      const nx = uy*vz-uz*vy, ny = uz*vx-ux*vz, nz = ux*vy-uy*vx;
      const length = Math.max(.0001, Math.hypot(nx,ny,nz));
      light = .28 + .72 * Math.max(0, (nx*.35 + ny*-.55 + nz*.76) / length);
    }
    return { points, averageDepth, light };
  }).filter((face) => face.points.length >= 3).sort((a,b) => a.averageDepth - b.averageDepth);
  const [red,green,blue] = hexToRgb(color);
  for (const face of faces) {
    context.beginPath();
    context.moveTo(face.points[0].x, face.points[0].y);
    for (const point of face.points.slice(1)) context.lineTo(point.x, point.y);
    context.closePath();
    context.fillStyle = `rgb(${Math.round(red*face.light)},${Math.round(green*face.light)},${Math.round(blue*face.light)})`;
    context.fill();
    context.strokeStyle = `rgba(255,255,255,${Math.min(.58, .16 + face.light * .2)})`;
    context.lineWidth = Math.max(.65, size * .006);
    context.stroke();
  }
}
