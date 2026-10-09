#!/usr/bin/env node
// Checks the three generated files against the game's data contract and
// against geography we know to be true. Run after every build:
//
//   npm run check        (from tools/globle)
//
// Exits non-zero on any failure. Cross-checks against the world-countries
// package (borders, UN membership, capital names) are printed as notes rather
// than failures: that dataset makes different calls on disputed borders and
// split capitals, and it is only consulted here, never copied into the output.

import fs from 'node:fs';
import { createRequire } from 'node:module';
import { geoArea, geoContains } from 'd3-geo';
import { feature } from 'topojson-client';

import { EXTRA_ANSWERS, UN_MEMBERS } from './lib/catalog.mjs';
import { normalise, OUTPUTS, REGIONS } from './lib/common.mjs';
import { buildTree, chordToKm, closestChord, densifiedPoints, toXYZ } from './lib/sphere.mjs';

const require = createRequire(import.meta.url);
const failures = [];
const notes = [];
const fail = (msg) => failures.push(msg);
const check = (ok, msg) => { if (!ok) fail(msg); };

const sizes = Object.fromEntries(Object.entries(OUTPUTS).map(([k, f]) => [k, fs.statSync(f).size]));
const countries = JSON.parse(fs.readFileSync(OUTPUTS.countries, 'utf8'));
const distances = JSON.parse(fs.readFileSync(OUTPUTS.distances, 'utf8'));
const world = JSON.parse(fs.readFileSync(OUTPUTS.world, 'utf8'));

// ---------------------------------------------------------------- countries.json

const KEYS = ['code', 'name', 'iso2', 'aliases', 'answer', 'region', 'lat', 'lng', 'capital'];
const CAPITAL_KEYS = ['name', 'aliases', 'lat', 'lng'];
const byCode = new Map();
const isLat = (v) => Number.isFinite(v) && v >= -90 && v <= 90;
const isLng = (v) => Number.isFinite(v) && v >= -180 && v <= 180;
const isStrings = (a) => Array.isArray(a) && a.every((s) => typeof s === 'string' && s.trim() === s && s.length > 0);

check(Array.isArray(countries), 'countries.json is not an array');
for (const c of countries) {
  const id = c.code ?? JSON.stringify(c).slice(0, 40);
  check(JSON.stringify(Object.keys(c)) === JSON.stringify(KEYS), `${id}: keys ${Object.keys(c)}`);
  check(/^[A-Z]{3}$/.test(c.code), `${id}: bad code`);
  check(!byCode.has(c.code), `${id}: duplicate code`);
  byCode.set(c.code, c);
  check(typeof c.name === 'string' && c.name.length > 0, `${id}: no name`);
  check(c.iso2 === null || /^[A-Z]{2}$/.test(c.iso2), `${id}: bad iso2 ${c.iso2}`);
  check(isStrings(c.aliases), `${id}: bad aliases`);
  check(typeof c.answer === 'boolean', `${id}: answer not boolean`);
  check(REGIONS.has(c.region), `${id}: bad region ${c.region}`);
  check(isLat(c.lat) && isLng(c.lng), `${id}: bad label point`);
  if (c.capital !== null) {
    const cap = c.capital;
    check(JSON.stringify(Object.keys(cap)) === JSON.stringify(CAPITAL_KEYS), `${id}: capital keys ${Object.keys(cap)}`);
    check(typeof cap.name === 'string' && cap.name.length > 0, `${id}: capital has no name`);
    check(isStrings(cap.aliases), `${id}: bad capital aliases`);
    check(isLat(cap.lat) && isLng(cap.lng), `${id}: bad capital coordinates`);
  }
}
for (let i = 1; i < countries.length; i++) {
  check(countries[i - 1].name.localeCompare(countries[i].name, 'en', { sensitivity: 'base' }) < 0,
    `not sorted by name at ${countries[i - 1].name} / ${countries[i].name}`);
}

const answers = countries.filter((c) => c.answer);
check(answers.length === 197, `answer count ${answers.length}, expected 197`);
check(UN_MEMBERS.length === 193 && new Set(UN_MEMBERS).size === 193, 'UN member list is not 193 distinct codes');
for (const code of [...UN_MEMBERS, ...EXTRA_ANSWERS]) check(byCode.get(code)?.answer === true, `${code} should be an answer`);
for (const c of answers) {
  check(c.capital !== null, `${c.code}: answer country without a capital`);
  check(c.region !== 'antarctica', `${c.code}: answer country in antarctica`);
}

// Every name and alias points at exactly one country, so typing one can never
// be ambiguous.
const owners = new Map();
for (const c of countries) {
  for (const n of [c.name, ...c.aliases]) {
    const key = normalise(n);
    if (owners.has(key) && owners.get(key) !== c.code) fail(`"${n}" names both ${owners.get(key)} and ${c.code}`);
    owners.set(key, c.code);
  }
}

// ---------------------------------------------------------------- distances.json

const codes = distances.codes;
const km = distances.km;
const n = codes.length;
const index = new Map(codes.map((code, i) => [code, i]));
check(JSON.stringify(Object.keys(distances)) === '["codes","km"]', 'distances.json keys');
check(index.size === n, 'distances.json has duplicate codes');
check(n === countries.length && codes.every((code) => byCode.has(code)), 'distances codes differ from countries.json');
check(Array.isArray(km) && km.length === n, 'matrix has the wrong number of rows');
let asymmetric = 0, badCells = 0;
for (let i = 0; i < n; i++) {
  check(Array.isArray(km[i]) && km[i].length === n, `row ${codes[i]} has the wrong length`);
  check(km[i][i] === 0, `diagonal ${codes[i]} is ${km[i][i]}`);
  for (let j = 0; j < n; j++) {
    if (!Number.isInteger(km[i][j]) || km[i][j] < 0 || km[i][j] > 20040) badCells++;
    if (km[i][j] !== km[j][i]) asymmetric++;
  }
}
check(badCells === 0, `${badCells} matrix cells are not ints in 0..20040`);
check(asymmetric === 0, `${asymmetric} matrix cells are not symmetric`);

const dist = (a, b) => km[index.get(a)][index.get(b)];
const EXPECT = [
  ['FRA', 'ESP', 0, 0], ['USA', 'CAN', 0, 0], ['USA', 'MEX', 0, 0], ['ZAF', 'LSO', 0, 0],
  ['ITA', 'VAT', 0, 0], ['ITA', 'SMR', 0, 0], ['CHN', 'RUS', 0, 0], ['GUF', 'BRA', 0, 0],
  ['ESP', 'GIB', 0, 0], ['ISR', 'PSE', 0, 0], ['MAR', 'ESH', 0, 0],
  // Somaliland is part of Somalia and Northern Cyprus part of Cyprus (UN borders).
  ['SOM', 'DJI', 0, 0], ['CYP', 'TUR', 60, 85],
  // French Guiana is split out of France, so France is not Brazil's neighbour.
  ['FRA', 'BRA', 3000, 9000],
  ['GBR', 'FRA', 20, 60], ['JPN', 'KOR', 30, 250], ['CUB', 'USA', 100, 250],
  // Ceuta and Melilla are dropped, so this is the Strait of Gibraltar.
  ['ESP', 'MAR', 5, 30],
  ['RUS', 'USA', 0, 100], ['DNK', 'SWE', 0, 20], ['EGY', 'SAU', 0, 30], ['ISL', 'GRL', 200, 400],
  // Not the 1,300–2,300 km of the Tasman Sea between the mainlands: Macquarie
  // Island (Australia) and the Auckland Islands (New Zealand) are ~620 km
  // apart, and the rule is the minimum over all territory.
  ['AUS', 'NZL', 550, 700],
  // Sub-Antarctic islands aside, the mainland gaps the contract had in mind:
  ['NOR', 'SJM', 300, 700], ['MYS', 'SGP', 1, 5], ['AUS', 'IDN', 50, 400],
  // Bouvet Island is dropped from Norway, so Norway is nowhere near the far south.
  ['NOR', 'ZAF', 8500, 10000], ['NOR', 'ARG', 10000, 11500],
];
for (const [a, b, lo, hi] of EXPECT) {
  const d = dist(a, b);
  check(d >= lo && d <= hi, `${a}-${b} is ${d} km, expected ${lo}..${hi}`);
}

// ---------------------------------------------------------------- world.json

check(world.type === 'Topology', 'world.json is not a Topology');
check(JSON.stringify(Object.keys(world.objects)) === '["countries"]', `world.json objects: ${Object.keys(world.objects)}`);
check(sizes.world < 1.25e6, `world.json is ${sizes.world} bytes`);
const shapes = feature(world, world.objects.countries).features;
const shapeByCode = new Map();
for (const f of shapes) {
  check(byCode.has(f.id), `world.json has unknown id ${f.id}`);
  check(!shapeByCode.has(f.id), `world.json repeats ${f.id}`);
  check(JSON.stringify(Object.keys(f.properties)) === '["name"]', `${f.id}: properties ${Object.keys(f.properties)}`);
  check(f.properties.name === byCode.get(f.id)?.name, `${f.id}: name "${f.properties.name}" differs from countries.json`);
  const a = geoArea(f);
  check(a > 0 && a < 2 * Math.PI, `${f.id}: drawn area ${a} sr (collapsed or inside out)`);
  shapeByCode.set(f.id, f);
}
check(shapeByCode.size === countries.length && countries.every((c) => shapeByCode.has(c.code)),
  'world.json codes differ from countries.json');

// ---------------------------------------------------------------- distances vs drawn shapes

// The colours have to agree with what the player sees: every pair that can
// come up in a game (one side an answer) is measured again on the drawn 50m
// shapes. The tolerance absorbs 10m and 50m drawing land borders differently
// (Somalia–South Sudan is 595 km on 10m, 698 on 50m); an islet measured but
// never drawn is off by far more.
const polygonsOf = (f) => (f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates);
const drawnTrees = new Map([...shapeByCode].map(([code, f]) => [code, buildTree(densifiedPoints(polygonsOf(f)))]));
const offDrawn = [];
for (let i = 0; i < n; i++) {
  for (let j = i + 1; j < n; j++) {
    const [a, b] = [codes[i], codes[j]];
    if (!byCode.get(a)?.answer && !byCode.get(b)?.answer) continue;
    if (!drawnTrees.has(a) || !drawnTrees.has(b)) continue;
    const drawn = Math.round(chordToKm(closestChord(drawnTrees.get(a), drawnTrees.get(b))));
    if (Math.abs(drawn - km[i][j]) > Math.max(150, 0.15 * Math.max(drawn, km[i][j]))) {
      offDrawn.push(`${a}-${b} ${km[i][j]} km (drawn ${drawn})`);
    }
  }
}
check(offDrawn.length === 0,
  `${offDrawn.length} answer pairs differ from the drawn shapes by more than max(150 km, 15%): ${offDrawn.slice(0, 12).join(', ')}`);

// ---------------------------------------------------------------- points vs shapes

const pointsOf = new Map();
/** Great-circle km from a point to a country's drawn shape (0 inside it). */
function kmToShape(code, lat, lng) {
  const f = shapeByCode.get(code);
  if (geoContains(f, [lng, lat])) return 0;
  if (!pointsOf.has(code)) {
    const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
    pointsOf.set(code, densifiedPoints(polys, 1));
  }
  const pts = pointsOf.get(code);
  const [x, y, z] = toXYZ(lng, lat);
  let best = Infinity;
  for (let i = 0; i < pts.length; i += 3) {
    const d = (pts[i] - x) ** 2 + (pts[i + 1] - y) ** 2 + (pts[i + 2] - z) ** 2;
    if (d < best) best = d;
  }
  return chordToKm(Math.sqrt(best));
}

let capitals = 0;
for (const c of countries) {
  if (!shapeByCode.has(c.code)) continue;
  // The label point is computed from 10m geometry; the drawn shape is coarser.
  const labelKm = kmToShape(c.code, c.lat, c.lng);
  check(labelKm <= 25, `${c.code}: label point is ${labelKm.toFixed(0)} km outside the country`);
  if (c.capital) {
    capitals++;
    const capKm = kmToShape(c.code, c.capital.lat, c.capital.lng);
    check(capKm <= 50, `${c.code}: capital ${c.capital.name} is ${capKm.toFixed(0)} km from the country`);
  }
}

const CAPITALS = {
  FRA: [48.86, 2.35], AUS: [-35.28, 149.13], CAN: [45.42, -75.70], BRA: [-15.79, -47.88],
  JPN: [35.68, 139.65], KEN: [-1.29, 36.82], NZL: [-41.29, 174.78],
};
for (const [code, [lat, lng]] of Object.entries(CAPITALS)) {
  const cap = byCode.get(code).capital;
  check(Math.abs(cap.lat - lat) < 0.05 && Math.abs(cap.lng - lng) < 0.05,
    `${code}: capital ${cap.name} at ${cap.lat},${cap.lng}, expected about ${lat},${lng}`);
}

// Label points land on the main landmass, antimeridian countries included.
const inBox = (code, south, north, test) => {
  const c = byCode.get(code);
  check(c.lat >= south && c.lat <= north && test(c.lng), `${code}: label point ${c.lat},${c.lng} is off its main landmass`);
};
inBox('USA', 30, 48, (lng) => lng > -110 && lng < -85);
inBox('FRA', 44, 49, (lng) => lng > -1 && lng < 6);
inBox('NOR', 58, 66, (lng) => lng > 5 && lng < 15);
inBox('RUS', 50, 72, (lng) => lng > 40 && lng < 140);
inBox('CHL', -45, -18, (lng) => lng > -76 && lng < -66);
inBox('IDN', -10, 6, (lng) => lng > 95 && lng < 141);
inBox('KIR', -12, 5, (lng) => lng > 168 || lng < -150);
inBox('FJI', -20, -15, (lng) => lng > 175 || lng < -178);

// ---------------------------------------------------------------- cross-checks (notes only)

const wc = require('world-countries/countries.json');
const ours = (code) => index.has(code);
const touching = new Set();
for (const w of wc) {
  if (!ours(w.cca3)) continue;
  for (const b of w.borders ?? []) {
    if (!ours(b)) continue;
    touching.add([w.cca3, b].sort().join('-'));
    if (dist(w.cca3, b) !== 0) notes.push(`world-countries says ${w.cca3} borders ${b}; ours: ${dist(w.cca3, b)} km`);
  }
}
for (let i = 0; i < n; i++) {
  for (let j = i + 1; j < n; j++) {
    const key = [codes[i], codes[j]].sort().join('-');
    if (km[i][j] === 0 && !touching.has(key)) notes.push(`we have ${key} touching; world-countries has no land border`);
  }
}
for (const w of wc) {
  if (ours(w.cca3) && w.unMember !== UN_MEMBERS.includes(w.cca3)) {
    notes.push(`world-countries unMember=${w.unMember} for ${w.cca3}`);
  }
  const c = byCode.get(w.cca3);
  if (c?.capital && w.capital?.length) {
    const mine = [c.capital.name, ...c.capital.aliases].map(normalise);
    if (!w.capital.some((name) => mine.includes(normalise(name)))) {
      notes.push(`${w.cca3}: our capital ${c.capital.name}, world-countries says ${w.capital.join(' / ')}`);
    }
  }
}

// ---------------------------------------------------------------- report

const kib = (b) => `${(b / 1024).toFixed(0)} KiB`;
console.log(`countries.json  ${kib(sizes.countries)}  ${countries.length} countries, ${answers.length} answers, ${capitals} capitals`);
console.log(`distances.json  ${kib(sizes.distances)}  ${n}x${n}`);
console.log(`world.json      ${kib(sizes.world)}  ${shapes.length} shapes`);
const regionCounts = {};
for (const c of answers) regionCounts[c.region] = (regionCounts[c.region] ?? 0) + 1;
console.log(`answers by region: ${JSON.stringify(regionCounts)}`);
if (notes.length) {
  console.log(`\n${notes.length} notes (differences from world-countries; review, not failures):`);
  for (const note of notes) console.log(`  - ${note}`);
}
if (failures.length) {
  console.error(`\n${failures.length} FAILURES:`);
  for (const f of failures) console.error(`  ✗ ${f}`);
  process.exit(1);
}
console.log('\nall checks passed');
