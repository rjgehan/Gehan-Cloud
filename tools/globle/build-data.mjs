#!/usr/bin/env node
// Builds the three data files the Globle game ships:
//
//   src/main/resources/globle/countries.json   names, aliases, regions, capitals, label points
//   src/main/resources/globle/distances.json   minimum great-circle km between every pair
//   src/main/resources/static/globle/world.json  TopoJSON the browser draws the globe from
//
// Geometry is Natural Earth (public domain) via the world-atlas package: the
// 10m set for anything measured (distances, label points), the hand-generalised
// 50m set for what is drawn, topped up from 10m where 50m leaves a country or
// its capital's island out. Distances count only the 10m pieces of a country
// that the globe actually draws, so no colour comes from an islet the player
// cannot see. Names, regions and capitals are hand-curated in
// lib/catalog.mjs; lib/sources.mjs says how Natural Earth features become
// countries. tools/globle/README.md explains the choices.
//
// Run with `npm run build` from tools/globle, then `npm run check`. The output
// is deterministic, so re-running on unchanged inputs produces no diff.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { geoArea, geoContains } from 'd3-geo';
import countries from 'i18n-iso-countries';
import { topology } from 'topojson-server';
import { feature, mergeArcs } from 'topojson-client';

import { CATALOG, EXTRA_ANSWERS, UN_MEMBERS } from './lib/catalog.mjs';
import { normalise, OUTPUTS, REGIONS, REPO } from './lib/common.mjs';
import { countryPolygons, fixWinding } from './lib/sources.mjs';
import { buildTree, chordToKm, closestChord, densifiedPoints, kmToPolygons, rootsOverlap } from './lib/sphere.mjs';
import { labelPoint } from './lib/label.mjs';

// 1e5 matches world-atlas's own quantisation: the 50m arcs land back on the
// grid they came from, so re-encoding loses nothing.
const QUANTIZATION = 1e5;

const log = (...args) => console.error('[globle]', ...args);

// ---------------------------------------------------------------- geometry

log('reading Natural Earth 10m and 50m');
const raw10 = countryPolygons('10m');
const raw50 = countryPolygons('50m');

const missingFromCatalog = [...raw10.keys()].filter((code) => !CATALOG[code]);
const missingGeometry = Object.keys(CATALOG).filter((code) => !raw10.has(code));
if (missingFromCatalog.length || missingGeometry.length) {
  throw new Error(`catalog/geometry mismatch: no catalog entry for ${missingFromCatalog}; no geometry for ${missingGeometry}`);
}
const extra50 = [...raw50.keys()].filter((code) => !raw10.has(code));
if (extra50.length) throw new Error(`50m has codes 10m lacks: ${extra50}`);
const answers = new Set([...UN_MEMBERS, ...EXTRA_ANSWERS]);
for (const code of answers) if (!CATALOG[code]) throw new Error(`answer ${code} has no catalog entry`);

const fixed10 = new Map([...raw10].map(([code, polys]) => [code, fixWinding(polys)]));
const fixed50 = new Map([...raw50].map(([code, polys]) => [code, fixWinding(polys)]));

// ---------------------------------------------------------------- names

function i18nNames(code) {
  const alpha2 = CATALOG[code].iso2 !== undefined ? CATALOG[code].iso2 : countries.alpha3ToAlpha2(code);
  if (!alpha2) return [];
  const names = countries.getName(alpha2, 'en', { select: 'all' }) ?? [];
  // "Korea, Republic of" and "Netherlands (Kingdom of the)" are list sort keys,
  // not things anyone types.
  return names.filter((n) => !/[,(]/.test(n));
}

const codes = Object.keys(CATALOG).sort((a, b) =>
  CATALOG[a].name.localeCompare(CATALOG[b].name, 'en', { sensitivity: 'base' }));

// Names and hand-written aliases are authoritative: a clash among them is a
// mistake in the catalog. Aliases borrowed from i18n-iso-countries are only
// kept when they point at exactly one country (it calls both Congos "Congo").
const owner = new Map();
const claim = (alias, code, what) => {
  const key = normalise(alias);
  const prev = owner.get(key);
  if (prev && prev !== code) throw new Error(`${what} "${alias}" of ${code} clashes with ${prev}`);
  owner.set(key, code);
};
for (const code of codes) claim(CATALOG[code].name, code, 'name');
for (const code of codes) for (const a of CATALOG[code].aliases) claim(a, code, 'alias');
const borrowed = new Map();
for (const code of codes) {
  for (const a of i18nNames(code)) {
    const key = normalise(a);
    if (!borrowed.has(key)) borrowed.set(key, new Set());
    borrowed.get(key).add(code);
  }
}

function aliasesFor(code) {
  const seen = new Set([normalise(CATALOG[code].name)]);
  const out = [];
  const consider = (a, mustOwn) => {
    const key = normalise(a);
    if (!key || seen.has(key)) return;
    if (mustOwn && ((owner.get(key) ?? code) !== code || borrowed.get(key).size > 1)) return;
    seen.add(key);
    out.push(a);
  };
  CATALOG[code].aliases.forEach((a) => consider(a, false));
  i18nNames(code).forEach((a) => consider(a, true));
  return out;
}

// ---------------------------------------------------------------- countries.json

const round = (v, places) => Number(v.toFixed(places));

log('computing label points');
const entries = codes.map((code) => {
  const cat = CATALOG[code];
  if (!REGIONS.has(cat.region)) throw new Error(`${code}: bad region ${cat.region}`);
  // Vatican City collapses to a line at 10m precision; fall back to 50m.
  const polygons = fixed10.get(code).length ? fixed10.get(code) : fixed50.get(code);
  const [lng, lat] = labelPoint(polygons);
  const iso2 = cat.iso2 !== undefined ? cat.iso2 : (countries.alpha3ToAlpha2(code) ?? null);
  let capital = null;
  if (cat.capital) {
    const [name, cLat, cLng, capAliases = []] = cat.capital;
    capital = { name, aliases: capAliases, lat: cLat, lng: cLng };
  }
  return {
    code,
    name: cat.name,
    iso2,
    aliases: aliasesFor(code),
    answer: answers.has(code),
    region: cat.region,
    lat: round(lat, 3),
    lng: round(lng, 3),
    capital,
  };
});

// ---------------------------------------------------------------- distances.json

/**
 * The geometry a country is measured on: its 10m polygons, but only those the
 * globe draws. 50m leaves out many remote islets (Isla de Aves, San Andrés,
 * the Kermadecs, Minamitorishima, ...), and a distance measured from one of
 * them would colour a guess by land the player cannot see. A 10m polygon
 * counts when it lies within 25 km of the drawn shape; a drawn polygon with no
 * 10m counterpart that close is measured as drawn. Countries drawn from 10m
 * already are measured whole.
 */
function measuredPolygons(code) {
  const { polygons: disp, why } = displayPolygons(code);
  if (why) return raw10.get(code); // the drawn shape is already 10m
  const dTree = buildTree(densifiedPoints(disp));
  const near = (polys, tree) =>
    chordToKm(closestChord(buildTree(densifiedPoints(polys)), tree)) <= 25;
  const keep = raw10.get(code).filter((p) => near([p], dTree));
  const t10 = buildTree(densifiedPoints(raw10.get(code)));
  for (const p of disp) if (!near([p], t10)) keep.push(p); // drawn at 50m only, e.g. the Chesterfield Is. in NCL
  return keep;
}

async function distanceMatrix() {
  log('densifying the drawn parts of the 10m geometry and building k-d trees');
  const trees = codes.map((code) => buildTree(densifiedPoints(measuredPolygons(code))));
  log(`${trees.reduce((s, t) => s + t.pts.length / 3, 0).toLocaleString()} points`);

  const n = codes.length;
  const chord = Array.from({ length: n }, () => new Float64Array(n));
  const threads = Math.max(1, Math.min(os.availableParallelism?.() ?? os.cpus().length, 16));
  log(`measuring ${(n * (n - 1) / 2).toLocaleString()} pairs on ${threads} threads`);

  // Row i has n-1-i pairs, so handing rows out in order gives the longest
  // first; each goes to whichever worker frees up next.
  const queue = Array.from({ length: n }, (_, i) => i);
  let done = 0;
  await Promise.all(Array.from({ length: threads }, () => new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./lib/distance-worker.mjs', import.meta.url), { workerData: { trees } });
    const next = () => {
      if (queue.length) worker.postMessage(queue.shift());
      else worker.terminate().then(resolve, reject);
    };
    worker.on('message', ({ i, row }) => {
      chord[i].set(row);
      if (++done % 25 === 0) log(`  ${done}/${n} rows`);
      next();
    });
    worker.on('error', reject);
    next();
  })));

  // Points can only see borders. A country entirely inside another without
  // sharing a vertex (an island in a lake) would otherwise read as nonzero, so
  // pairs whose bounding boxes overlap also get a point-in-polygon test.
  const firstVertex = (code) => (fixed10.get(code)[0] ?? raw10.get(code)[0])[0][0];
  const km = Array.from({ length: n }, () => new Array(n).fill(0));
  let contained = 0;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      let d = chord[i][j];
      if (d > 0 && rootsOverlap(trees[i], trees[j])) {
        const a = codes[i], b = codes[j];
        if (geoContains({ type: 'MultiPolygon', coordinates: fixed10.get(b) }, firstVertex(a))
          || geoContains({ type: 'MultiPolygon', coordinates: fixed10.get(a) }, firstVertex(b))) {
          d = 0;
          contained++;
        }
      }
      // 0 is reserved for countries that touch; anything else is at least 1 km.
      const v = d === 0 ? 0 : Math.max(1, Math.round(chordToKm(d)));
      km[i][j] = km[j][i] = v;
    }
  }
  if (contained) log(`${contained} pairs touch only by containment`);
  return km;
}

// ---------------------------------------------------------------- world.json

/**
 * Which geometry to draw. 50m is hand-generalised and a fraction of the size,
 * so it is the default. 10m is used where 50m has no shape at all (Tuvalu,
 * Gibraltar) and where 50m leaves out the island the capital stands on
 * (Pitcairn Island, Grand Turk) — the capitals mode would otherwise put a dot
 * in empty sea. Those are all islands or enclaves too small for the extra
 * detail to matter for size.
 */
function displayPolygons(code) {
  const coarse = fixed50.get(code) ?? [];
  if (!coarse.length) return { polygons: fixed10.get(code), why: 'missing from 50m' };
  const cap = CATALOG[code].capital;
  if (cap && kmToPolygons(coarse, cap[2], cap[1]) > 25 && kmToPolygons(fixed10.get(code), cap[2], cap[1]) <= 25) {
    return { polygons: fixed10.get(code), why: `50m lacks ${cap[0]}'s island` };
  }
  return { polygons: coarse };
}

/** Rewrites arc references so arcs nothing uses any more are dropped. */
function pruneArcs(topo) {
  const used = new Map();
  const walk = (arcs) => arcs.map((a) => (Array.isArray(a) ? walk(a) : (() => {
    const i = a < 0 ? ~a : a;
    if (!used.has(i)) used.set(i, used.size);
    return a < 0 ? ~used.get(i) : used.get(i);
  })()));
  for (const g of topo.objects.countries.geometries) g.arcs = walk(g.arcs);
  const arcs = new Array(used.size);
  for (const [old, now] of used) arcs[now] = topo.arcs[old];
  topo.arcs = arcs;
}

function worldTopology() {
  log('building display topology');
  // One feature per polygon first, so that pieces folded into a country (the
  // Cyprus bases, Crimea, southern Western Sahara) can then be merged with
  // mergeArcs — which drops the border between them instead of drawing a
  // line through the middle of the country.
  const pieces = [];
  for (const code of codes) {
    const { polygons, why } = displayPolygons(code);
    if (!polygons.length) throw new Error(`${code}: nothing to draw`);
    if (why) log(`  ${code} drawn from 10m (${why})`);
    for (const polygon of polygons) {
      pieces.push({ type: 'Feature', id: code, properties: {}, geometry: { type: 'Polygon', coordinates: polygon } });
    }
  }
  const topo = topology({ countries: { type: 'FeatureCollection', features: pieces } }, QUANTIZATION);
  const byId = new Map(codes.map((code) => [code, []]));
  for (const g of topo.objects.countries.geometries) byId.get(g.id).push(g);
  topo.objects.countries.geometries = codes.map((code) => {
    const merged = mergeArcs(topo, byId.get(code));
    const geometry = merged.arcs.length === 1
      ? { type: 'Polygon', arcs: merged.arcs[0] }
      : { type: 'MultiPolygon', arcs: merged.arcs };
    return { ...geometry, id: code, properties: { name: CATALOG[code].name } };
  });
  pruneArcs(topo);
  delete topo.bbox; // optional in TopoJSON, and the client never reads it

  // Quantisation and merging must not have collapsed or inverted anything.
  for (const f of feature(topo, topo.objects.countries).features) {
    const a = geoArea(f);
    if (!(a > 0 && a < 2 * Math.PI)) throw new Error(`${f.id}: area ${a} after quantisation`);
  }
  return topo;
}

// ---------------------------------------------------------------- write

function writeFile(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
  log(`wrote ${path.relative(REPO, file)} (${(Buffer.byteLength(text) / 1024).toFixed(0)} KiB)`);
}

const km = await distanceMatrix();
const world = worldTopology();

writeFile(OUTPUTS.countries, `[\n${entries.map((e) => JSON.stringify(e)).join(',\n')}\n]\n`);
writeFile(OUTPUTS.distances,
  `{"codes":${JSON.stringify(codes)},\n"km":[\n${km.map((row) => JSON.stringify(row)).join(',\n')}\n]}\n`);
writeFile(OUTPUTS.world, JSON.stringify(world));
log(`${entries.length} countries, ${entries.filter((e) => e.answer).length} answers`);
