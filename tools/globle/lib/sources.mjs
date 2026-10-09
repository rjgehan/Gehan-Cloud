// Turns Natural Earth admin-0 features (via the world-atlas package) into one
// geometry per game code.
//
// Natural Earth is close to what the game wants but not quite: it lumps some
// overseas territories into their sovereign (French Guiana into France, which
// would make France Brazil's neighbour), splits off slivers nobody would ever
// guess (sovereign base areas, a UN buffer zone, disputed reefs), and leaves a
// handful of features without an ISO code at all. The rules below are the whole
// of that cleanup; everything else maps straight across by ISO numeric id.

import fs from 'node:fs';
import { createRequire } from 'node:module';
import { feature } from 'topojson-client';
import { geoArea, geoCentroid } from 'd3-geo';
import countries from 'i18n-iso-countries';

const require = createRequire(import.meta.url);

/** Features matched by Natural Earth name, ahead of the id lookup. */
const BY_NAME = {
  // No ISO code: Kosovo, an answer, gets a stable made-up code.
  'Kosovo': { code: 'XKX' },
  // UN-recognised borders, as in real Globle: Natural Earth draws Somaliland
  // and Northern Cyprus as countries of their own, which would leave Somalia
  // 600 km from Djibouti and Cyprus without its north.
  'Somaliland': { merge: 'SOM' },
  'N. Cyprus': { merge: 'CYP' },
  // Christmas Island and the Cocos (Keeling) Islands share one feature but have
  // separate ISO codes, so they are split by longitude.
  'Indian Ocean Ter.': { split: ([lng]) => (lng > 100 ? 'CXR' : 'CCK') },
  // Slivers folded into the country they sit in, so the map has no holes and no
  // country picks up a spurious neighbour (the UK bases would otherwise make
  // the United Kingdom border Cyprus).
  'Akrotiri': { merge: 'CYP' },
  'Dhekelia': { merge: 'CYP' },
  'Cyprus U.N. Buffer Zone': { merge: 'CYP' },
  'Siachen Glacier': { merge: 'IND' },
  'Baikonur': { merge: 'KAZ' },
  'USNB Guantanamo Bay': { merge: 'CUB' },
  // Uninhabited reefs and scattered dependencies that would only be noise in
  // a guessing game (and, for the U.S. Minor Outlying Islands, a single
  // "country" spread across the whole Pacific).
  'Ashmore and Cartier Is.': { drop: true },
  'Coral Sea Is.': { drop: true },
  'Spratly Is.': { drop: true },
  'Clipperton I.': { drop: true },
  'Bajo Nuevo Bank': { drop: true },
  'Serranilla Bank': { drop: true },
  'Scarborough Reef': { drop: true },
  'U.S. Minor Outlying Is.': { drop: true },
};

const inBox = ([lng, lat], west, south, east, north) => lng >= west && lng <= east && lat >= south && lat <= north;

/**
 * Polygons of a country that really belong to another code, decided by the
 * polygon's centroid. Returning the country's own code keeps the polygon,
 * another code moves it, null drops it.
 */
const SPLITS = {
  FRA: (c) =>
    inBox(c, -55, 1.5, -51, 6.5) ? 'GUF'
    : inBox(c, -62, 15.7, -60.9, 16.6) ? 'GLP'
    : inBox(c, -61.4, 14.3, -60.7, 15) ? 'MTQ'
    : inBox(c, 55, -21.6, 56, -20.6) ? 'REU'
    : inBox(c, 44.8, -13.2, 45.4, -12.4) ? 'MYT'
    : 'FRA',
  // Svalbard (and Bjørnøya) north of 73.5°N, Jan Mayen out in the Greenland Sea;
  // Bouvet Island (54°S) is dropped: an uninhabited dependency the 50m globe
  // does not draw, which would put Norway 2,500 km from South Africa.
  NOR: (c) => (c[1] < 0 ? null : c[1] > 73.5 || inBox(c, -10, 70.5, -7, 71.5) ? 'SJM' : 'NOR'),
  // Bonaire, Sint Eustatius and Saba.
  NLD: (c) => (c[0] < -60 ? 'BES' : 'NLD'),
  // Ceuta, Melilla and the plazas de soberanía sit on the Moroccan coast; left
  // in, they would make Spain and Morocco neighbours instead of a strait apart.
  // They are a few square kilometres, so they are dropped rather than given
  // codes of their own. Llívia (the exclave inside France) stays.
  ESP: (c) => (inBox(c, -6, 35, -2, 36) ? null : 'ESP'),
  // Tokelau is a separate ISO territory that Natural Earth files under New Zealand.
  NZL: (c) => (c[0] < -170 && c[1] > -10 ? 'TKL' : 'NZL'),
  // Natural Earth's default view draws its de facto lines, which put Crimea in
  // Russia. The game follows the UN General Assembly position instead (see
  // also Western Sahara below). Crimea is its own polygon, so it simply moves;
  // Russia and Ukraine touch either way, so no distance changes.
  RUS: (c) => (inBox(c, 32, 44, 36.7, 46.3) ? 'UKR' : 'RUS'),
};

// Natural Earth's de facto view also hands Morocco everything west of the
// berm, leaving "W. Sahara" as a strip along the Algerian and Mauritanian
// borders. The UN treats the whole territory south of 27°40′N as Western
// Sahara, so Morocco is cut along that parallel and the southern piece joins
// Western Sahara.
const WESTERN_SAHARA_LAT = 27 + 40 / 60;

/**
 * Splits a ring that crosses a parallel exactly twice into the pieces north
 * and south of it. Both pieces keep the original ring's direction, so their
 * winding stays right. The cut follows the parallel in 0.25° steps so it is
 * drawn as a parallel rather than as the great circle between its ends.
 */
function cutAtParallel(ring, lat) {
  const crossings = [];
  for (let i = 0; i < ring.length - 1; i++) {
    const [a, b] = [ring[i], ring[i + 1]];
    if ((a[1] - lat) * (b[1] - lat) < 0) {
      const t = (lat - a[1]) / (b[1] - a[1]);
      crossings.push({ i, point: [a[0] + t * (b[0] - a[0]), lat] });
    }
  }
  if (crossings.length !== 2) throw new Error(`expected 2 crossings of ${lat}, found ${crossings.length}`);
  const [p, q] = crossings;
  const along = (from, to) => {
    const steps = Math.ceil(Math.abs(to[0] - from[0]) / 0.25);
    return Array.from({ length: steps - 1 }, (_, k) => [from[0] + ((k + 1) / steps) * (to[0] - from[0]), lat]);
  };
  // ring[p.i+1 .. q.i] lies on one side, the rest (wrapping round) on the other.
  const inner = [p.point, ...ring.slice(p.i + 1, q.i + 1), q.point, ...along(q.point, p.point), p.point];
  const outer = [q.point, ...ring.slice(q.i + 1, -1), ...ring.slice(0, p.i + 1), p.point, ...along(p.point, q.point), q.point];
  return ring[p.i + 1][1] > lat ? { north: inner, south: outer } : { north: outer, south: inner };
}

function moveWesternSahara(byCode) {
  const morocco = byCode.get('MAR');
  if (morocco.length !== 1 || morocco[0].length !== 1) throw new Error('Morocco is no longer one simple polygon');
  const { north, south } = cutAtParallel(morocco[0][0], WESTERN_SAHARA_LAT);
  byCode.set('MAR', [[north]]);
  byCode.get('ESH').push([south]);
}

/** Expected homes for split-off polygons, to catch a rule matching the wrong thing. */
const SPLIT_SANITY = {
  FRA: (c) => inBox(c, -6, 41, 10, 52),
};

/** Reads a world-atlas topology ("10m" or "50m"). */
export function readAtlas(resolution) {
  const path = require.resolve(`world-atlas/countries-${resolution}.json`);
  return JSON.parse(fs.readFileSync(path, 'utf8'));
}

/**
 * One MultiPolygon per code for the given resolution, as
 * Map<code, Array<polygon coordinates>>.
 */
export function countryPolygons(resolution) {
  const topology = readAtlas(resolution);
  const fc = feature(topology, topology.objects.countries);
  const byCode = new Map();
  const add = (code, polygon) => {
    if (!byCode.has(code)) byCode.set(code, []);
    byCode.get(code).push(polygon);
  };

  for (const f of fc.features) {
    const name = f.properties.name;
    const rule = BY_NAME[name] ?? {};
    if (rule.drop) continue;
    const polygons = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;

    let code = rule.code ?? rule.merge;
    if (!code && !rule.split) {
      if (f.id == null) throw new Error(`${resolution}: feature "${name}" has no id and no rule`);
      code = countries.numericToAlpha3(f.id);
      if (!code) throw new Error(`${resolution}: no alpha-3 for numeric ${f.id} (${name})`);
    }
    for (const polygon of polygons) {
      const centroid = geoCentroid({ type: 'Polygon', coordinates: polygon });
      let target = code;
      if (rule.split) target = rule.split(centroid);
      else if (SPLITS[code]) target = SPLITS[code](centroid);
      if (target === null) continue;
      if (target === code && SPLIT_SANITY[code] && !SPLIT_SANITY[code](centroid)) {
        throw new Error(`${resolution}: unexpected ${code} polygon at ${centroid.map((v) => v.toFixed(2))}`);
      }
      add(target, polygon);
    }
  }
  moveWesternSahara(byCode);
  return byCode;
}

/**
 * Returns the polygons with their orientation repaired. Natural Earth rings are
 * wound for d3, but quantisation can turn a tiny islet inside out, and d3 then
 * reads it as "the whole globe except this islet" — which wrecks areas and
 * containment tests. No country polygon covers a hemisphere, so a polygon that
 * does is reversed; one that encloses nothing either way has collapsed to a
 * line or a point and is dropped.
 */
export function fixWinding(polygons) {
  const out = [];
  for (const polygon of polygons) {
    for (const candidate of [polygon, polygon.map((ring) => ring.slice().reverse())]) {
      const a = geoArea({ type: 'Polygon', coordinates: candidate });
      if (a > 1e-14 && a < 2 * Math.PI) {
        out.push(candidate);
        break;
      }
    }
  }
  return out;
}
