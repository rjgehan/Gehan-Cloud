// Where to put a country's "you are here" point: the pole of inaccessibility of
// its largest polygon — the spot inside it furthest from any edge.
//
// A plain centroid is the wrong tool: Chile's falls in the Pacific, Norway's in
// Sweden, and a multi-island country's in the sea between the islands. The
// largest polygon keeps the point on the main landmass (contiguous USA rather
// than somewhere between Hawaii and Alaska; mainland France rather than the
// Atlantic between it and French Guiana).
//
// polylabel works on a plane, so the polygon is first projected with an
// azimuthal equidistant projection centred on itself. That keeps distortion low
// across even Russia's mainland, and because the projection is centred on the
// polygon the antimeridian never falls inside it.

import { geoArea, geoAzimuthalEquidistant, geoCentroid } from 'd3-geo';
import polylabel from 'polylabel';
import { EARTH_RADIUS_KM } from './sphere.mjs';

/** [lng, lat] label point for a set of correctly wound polygons. */
export function labelPoint(polygons) {
  let largest = null, largestArea = -1;
  for (const polygon of polygons) {
    const a = geoArea({ type: 'Polygon', coordinates: polygon });
    if (a > largestArea) { largest = polygon; largestArea = a; }
  }
  const center = geoCentroid({ type: 'Polygon', coordinates: largest });
  const projection = geoAzimuthalEquidistant()
    .rotate([-center[0], -center[1]])
    .scale(EARTH_RADIUS_KM) // projected units are kilometres
    .translate([0, 0]);
  const rings = largest.map((ring) => ring.map((p) => projection(p)));
  const sizeKm = Math.sqrt(largestArea) * EARTH_RADIUS_KM;
  const xy = polylabel(rings, Math.max(0.01, sizeKm / 2000));
  return projection.invert(xy);
}
