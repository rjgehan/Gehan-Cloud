// Great-circle geometry for the distance matrix.
//
// Every vertex becomes a point on the unit sphere in 3-D. Straight-line (chord)
// distance between two such points grows monotonically with the great-circle
// distance, so "closest pair of points" can be answered with an ordinary 3-D
// k-d tree and converted to kilometres once at the end. Working in 3-D also
// makes the antimeridian a non-event: (180°, φ) and (−180°, φ) are the same
// point, so Russia, Fiji, Kiribati and Alaska need no special handling.
//
// Edges are densified so no two consecutive points are more than MAX_STEP_KM
// apart; the closest pair of *points* is then within MAX_STEP_KM of the true
// closest pair of *edges*. Natural Earth 10m vertices are mostly much closer
// than that already — it is the long straight borders (the 49th parallel,
// colonial lines across the Sahara) that need it.

import { geoContains } from 'd3-geo';

export const EARTH_RADIUS_KM = 6371.0088;
export const MAX_STEP_KM = 2;
const LEAF_SIZE = 24;
const D2R = Math.PI / 180;

export function toXYZ(lng, lat) {
  const l = lng * D2R;
  const p = lat * D2R;
  const c = Math.cos(p);
  return [c * Math.cos(l), c * Math.sin(l), Math.sin(p)];
}

/** Chord length between unit vectors → great-circle kilometres. */
export function chordToKm(chord) {
  return 2 * Math.asin(Math.min(1, chord / 2)) * EARTH_RADIUS_KM;
}

/**
 * Every ring of every polygon as unit vectors, with long edges subdivided
 * along the great circle (slerp), flattened into one Float64Array.
 */
export function densifiedPoints(polygons, maxStepKm = MAX_STEP_KM) {
  const maxStep = maxStepKm / EARTH_RADIUS_KM;
  const out = [];
  for (const polygon of polygons) {
    for (const ring of polygon) {
      let prev = null;
      for (const [lng, lat] of ring) {
        const p = toXYZ(lng, lat);
        if (prev) {
          const dot = Math.max(-1, Math.min(1, prev[0] * p[0] + prev[1] * p[1] + prev[2] * p[2]));
          const theta = Math.acos(dot);
          const steps = Math.ceil(theta / maxStep);
          if (steps > 1 && theta < Math.PI - 1e-9) {
            const s = Math.sin(theta);
            for (let k = 1; k < steps; k++) {
              const t = k / steps;
              const a = Math.sin((1 - t) * theta) / s;
              const b = Math.sin(t * theta) / s;
              out.push(a * prev[0] + b * p[0], a * prev[1] + b * p[1], a * prev[2] + b * p[2]);
            }
          }
        }
        out.push(p[0], p[1], p[2]);
        prev = p;
      }
    }
  }
  return Float64Array.from(out);
}

/**
 * A static k-d tree over 3-D points, stored in flat typed arrays so it can be
 * handed to a worker thread as-is. Node i covers pts[start[i]..end[i]) after
 * reordering; left/right are -1 for leaves; box holds min xyz then max xyz.
 */
export function buildTree(points) {
  const n = points.length / 3;
  const order = new Uint32Array(n);
  for (let i = 0; i < n; i++) order[i] = i;
  const start = [], end = [], left = [], right = [], box = [];

  function build(s, e) {
    const id = start.length;
    start.push(s); end.push(e); left.push(-1); right.push(-1);
    const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
    for (let i = s; i < e; i++) {
      const o = order[i] * 3;
      for (let a = 0; a < 3; a++) {
        const v = points[o + a];
        if (v < mn[a]) mn[a] = v;
        if (v > mx[a]) mx[a] = v;
      }
    }
    box.push(mn[0], mn[1], mn[2], mx[0], mx[1], mx[2]);
    if (e - s > LEAF_SIZE) {
      let axis = 0;
      for (let a = 1; a < 3; a++) if (mx[a] - mn[a] > mx[axis] - mn[axis]) axis = a;
      const slice = Array.from(order.subarray(s, e)).sort((p, q) => points[p * 3 + axis] - points[q * 3 + axis]);
      order.set(slice, s);
      const m = (s + e) >> 1;
      left[id] = build(s, m);
      right[id] = build(m, e);
    }
    return id;
  }
  build(0, n);

  const pts = new Float64Array(n * 3);
  for (let i = 0; i < n; i++) {
    pts[i * 3] = points[order[i] * 3];
    pts[i * 3 + 1] = points[order[i] * 3 + 1];
    pts[i * 3 + 2] = points[order[i] * 3 + 2];
  }
  return {
    pts,
    start: Int32Array.from(start),
    end: Int32Array.from(end),
    left: Int32Array.from(left),
    right: Int32Array.from(right),
    box: Float64Array.from(box),
  };
}

function boxGap2(A, a, B, b) {
  let d = 0;
  const ao = a * 6, bo = b * 6;
  for (let k = 0; k < 3; k++) {
    const g = Math.max(A.box[ao + k] - B.box[bo + 3 + k], B.box[bo + k] - A.box[ao + 3 + k], 0);
    d += g * g;
  }
  return d;
}

/**
 * Smallest chord between any point of tree A and any point of tree B, by
 * dual-tree branch and bound: a pair of nodes whose boxes are already further
 * apart than the best pair found so far cannot contain a better pair.
 */
export function closestChord(A, B) {
  let best = Infinity;

  function visit(a, b) {
    if (best === 0 || boxGap2(A, a, B, b) >= best) return;
    const aLeaf = A.left[a] < 0;
    const bLeaf = B.left[b] < 0;
    if (aLeaf && bLeaf) {
      for (let i = A.start[a] * 3, ie = A.end[a] * 3; i < ie; i += 3) {
        const x = A.pts[i], y = A.pts[i + 1], z = A.pts[i + 2];
        for (let j = B.start[b] * 3, je = B.end[b] * 3; j < je; j += 3) {
          const dx = x - B.pts[j], dy = y - B.pts[j + 1], dz = z - B.pts[j + 2];
          const d = dx * dx + dy * dy + dz * dz;
          if (d < best) best = d;
        }
      }
      return;
    }
    const splitA = bLeaf || (!aLeaf && A.end[a] - A.start[a] >= B.end[b] - B.start[b]);
    if (splitA) {
      const l = A.left[a], r = A.right[a];
      if (boxGap2(A, l, B, b) <= boxGap2(A, r, B, b)) { visit(l, b); visit(r, b); }
      else { visit(r, b); visit(l, b); }
    } else {
      const l = B.left[b], r = B.right[b];
      if (boxGap2(A, a, B, l) <= boxGap2(A, a, B, r)) { visit(a, l); visit(a, r); }
      else { visit(a, r); visit(a, l); }
    }
  }

  visit(0, 0);
  return Math.sqrt(best);
}

/** Do the root boxes of two trees overlap (i.e. could one contain the other)? */
export function rootsOverlap(A, B) {
  return boxGap2(A, 0, B, 0) === 0;
}

/**
 * Great-circle km from a point to a set of correctly wound polygons: 0 inside
 * them, otherwise the distance to the nearest edge (densified to 1 km).
 */
export function kmToPolygons(polygons, lng, lat) {
  if (geoContains({ type: 'MultiPolygon', coordinates: polygons }, [lng, lat])) return 0;
  const pts = densifiedPoints(polygons, 1);
  const [x, y, z] = toXYZ(lng, lat);
  let best = Infinity;
  for (let i = 0; i < pts.length; i += 3) {
    const d = (pts[i] - x) ** 2 + (pts[i + 1] - y) ** 2 + (pts[i + 2] - z) ** 2;
    if (d < best) best = d;
  }
  return chordToKm(Math.sqrt(best));
}
