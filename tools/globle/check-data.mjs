#!/usr/bin/env node
// Checks the three generated files against the game's data contract and
// against geography we know to be true. Run after every build:
//
//   npm run check        (from tools/globle)
//
// Exits non-zero on any failure. Cross-checks against the world-countries
// package (borders, UN membership, capital names, Spanish names) are printed
// as notes rather than failures: that dataset makes different calls on
// disputed borders and split capitals, and it is only consulted here, never
// copied into the output.

import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { geoArea, geoContains } from 'd3-geo';
import { feature } from 'topojson-client';

import { EXTRA_ANSWERS, UN_MEMBERS } from './lib/catalog.mjs';
import { normalise, OUTPUTS, REGIONS, REPO } from './lib/common.mjs';
import { buildTree, chordToKm, closestChord, densifiedPoints, EARTH_RADIUS_KM, toXYZ } from './lib/sphere.mjs';

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

const KEYS = ['code', 'name', 'nameEs', 'iso2', 'aliases', 'aliasesEs', 'answer', 'region', 'difficulty', 'smallIsland',
  'lat', 'lng', 'capital'];
const CAPITAL_KEYS = ['name', 'nameEs', 'aliases', 'aliasesEs', 'lat', 'lng'];
const byCode = new Map();
const isLat = (v) => Number.isFinite(v) && v >= -90 && v <= 90;
const isLng = (v) => Number.isFinite(v) && v >= -180 && v <= 180;
const isName = (s) => typeof s === 'string' && s.trim() === s && s.length > 0;
const isStrings = (a) => Array.isArray(a) && a.every(isName);

check(Array.isArray(countries), 'countries.json is not an array');
for (const c of countries) {
  const id = c.code ?? JSON.stringify(c).slice(0, 40);
  check(JSON.stringify(Object.keys(c)) === JSON.stringify(KEYS), `${id}: keys ${Object.keys(c)}`);
  check(/^[A-Z]{3}$/.test(c.code), `${id}: bad code`);
  check(!byCode.has(c.code), `${id}: duplicate code`);
  byCode.set(c.code, c);
  check(typeof c.name === 'string' && c.name.length > 0, `${id}: no name`);
  check(isName(c.nameEs), `${id}: no Spanish name (nameEs)`);
  check(c.iso2 === null || /^[A-Z]{2}$/.test(c.iso2), `${id}: bad iso2 ${c.iso2}`);
  check(isStrings(c.aliases), `${id}: bad aliases`);
  check(isStrings(c.aliasesEs), `${id}: bad Spanish aliases (aliasesEs)`);
  check(typeof c.answer === 'boolean', `${id}: answer not boolean`);
  check(REGIONS.has(c.region), `${id}: bad region ${c.region}`);
  check(Number.isInteger(c.difficulty) && c.difficulty >= 1 && c.difficulty <= 5, `${id}: difficulty ${c.difficulty} is not 1..5`);
  check(typeof c.smallIsland === 'boolean', `${id}: smallIsland not boolean`);
  if (!c.answer) {
    check(c.difficulty === 5, `${id}: never the answer, so difficulty should be 5, not ${c.difficulty}`);
    check(c.smallIsland === false, `${id}: never the answer, so it cannot be a small island nation`);
  }
  check(isLat(c.lat) && isLng(c.lng), `${id}: bad label point`);
  if (c.capital !== null) {
    const cap = c.capital;
    check(JSON.stringify(Object.keys(cap)) === JSON.stringify(CAPITAL_KEYS), `${id}: capital keys ${Object.keys(cap)}`);
    check(typeof cap.name === 'string' && cap.name.length > 0, `${id}: capital has no name`);
    check(isName(cap.nameEs), `${id}: capital has no Spanish name (nameEs)`);
    check(isStrings(cap.aliases), `${id}: bad capital aliases`);
    check(isStrings(cap.aliasesEs), `${id}: bad Spanish capital aliases (aliasesEs)`);
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

// Every name and alias, English and Spanish together, points at exactly one
// country, so typing one can never be ambiguous: the page accepts both languages
// whichever one it shows.
const namesOf = (c) => [c.name, ...c.aliases, c.nameEs, ...c.aliasesEs];
const capitalNamesOf = (cap) => [cap.name, ...cap.aliases, cap.nameEs, ...cap.aliasesEs];
const owners = new Map();
for (const c of countries) {
  for (const n of namesOf(c)) {
    const key = normalise(n);
    if (owners.has(key) && owners.get(key) !== c.code) fail(`"${n}" names both ${owners.get(key)} and ${c.code}`);
    owners.set(key, c.code);
  }
}

// Hard has no list to pick from, so a typed name goes through the client's own
// matcher (match.js findExact). That matcher also drops punctuation and a leading
// article, reads "St" as "Saint" and "Sta"/"Sto" as "Santa"/"Santo", and ignores
// "the"/"of"/"and" and Spanish "de"/"del"/"y"/"el"/"la"/"los"/"las". Every name
// and alias, in both languages, must still find its own place there, with the
// page in either language. It is imported as a data: URL so it loads as ESM on
// any Node 20.
const matchJs = fs.readFileSync(path.join(REPO, 'src/main/resources/static/globle/js/match.js'), 'utf8');
const { buildIndex, findExact, search, displayName, capitalName, normalize } =
  await import('data:text/javascript,' + encodeURIComponent(matchJs));
const listed = countries.map((c) => ({ ...c, canBeAnswer: c.answer }));
const typed = { en: buildIndex(listed, 'country', 'en'), es: buildIndex(listed, 'country', 'es') };
for (const c of countries) {
  for (const n of namesOf(c)) {
    for (const [lang, index] of Object.entries(typed)) {
      const got = findExact(index, n);
      check(got === c.code, `"${n}" cannot be typed in Hard (page in ${lang}): the matcher finds ${got ?? 'no single place'}, not ${c.code}`);
    }
  }
}

// The same for capitals, where Enter picks the one capital the text names. A few
// capitals genuinely share a name once spaces and hyphens are squeezed out; for
// those the list offers both, and these pairs are the only ones allowed.
const CAPITAL_HOMONYMS = [['JAM', 'NFK'], ['CYM', 'GUY'], ['GLP', 'KNA']]; // Kingston, George Town/Georgetown, Basse-Terre/Basseterre
const homonymsFound = new Set();
for (const lang of ['en', 'es']) {
  const capitalIndex = buildIndex(listed, 'capital', lang);
  for (const c of countries) {
    if (!c.capital) continue;
    for (const n of capitalNamesOf(c.capital)) {
      const got = findExact(capitalIndex, n);
      if (got === c.code) continue;
      const named = search(capitalIndex, n, { limit: 50 }).filter((r) => r.exact).map((r) => r.code).sort();
      const pair = CAPITAL_HOMONYMS.find((p) => p.join() === named.join());
      if (got === null && pair) homonymsFound.add(pair.join());
      else fail(`capital "${n}" of ${c.code} cannot be picked with Enter (page in ${lang}): it names ${named.join(', ') || 'nothing'}`);
    }
  }
}
for (const pair of CAPITAL_HOMONYMS) {
  check(homonymsFound.has(pair.join()), `${pair.join(' and ')} are listed as sharing a capital name, but no longer do`);
}

// Spanish names a Spanish speaker would expect, and what they type for them.
const SPANISH = {
  DEU: 'Alemania', USA: 'Estados Unidos', GBR: 'Reino Unido', NLD: 'Países Bajos', FLK: 'Islas Malvinas',
  CIV: 'Costa de Marfil', COD: 'República Democrática del Congo', SAU: 'Arabia Saudita', QAT: 'Catar', DJI: 'Yibuti',
  SWZ: 'Esuatini', FJI: 'Fiyi', TLS: 'Timor Oriental', CZE: 'Chequia', VAT: 'Ciudad del Vaticano', GRL: 'Groenlandia',
};
for (const [code, name] of Object.entries(SPANISH)) {
  check(byCode.get(code)?.nameEs === name, `${code}: Spanish name "${byCode.get(code)?.nameEs}", expected "${name}"`);
}
check(byCode.get('NLD')?.aliasesEs.includes('Holanda'), 'NLD: "Holanda" should be a Spanish alias');
const SPANISH_CAPITALS = { GBR: 'Londres', CHN: 'Pekín', MEX: 'Ciudad de México', USA: 'Washington D. C.', EGY: 'El Cairo' };
for (const [code, name] of Object.entries(SPANISH_CAPITALS)) {
  check(byCode.get(code)?.capital?.nameEs === name, `${code}: Spanish capital "${byCode.get(code)?.capital?.nameEs}", expected "${name}"`);
}
const TYPED_ES = {
  'alemania': 'DEU', 'Germany': 'DEU', 'holanda': 'NLD', 'EEUU': 'USA', 'EE. UU.': 'USA', 'los Estados Unidos': 'USA',
  'el Perú': 'PER', 'peru': 'PER', 'la Argentina': 'ARG', 'la India': 'IND', 'las bahamas': 'BHS', 'Sta. Lucía': 'LCA',
  'Sto Tome y Principe': 'STP', 'guinea bisau': 'GNB', 'japon': 'JPN', 'malvinas': 'FLK', 'españa': 'ESP', 'espana': 'ESP',
  'Arabia Saudí': 'SAU', 'República del Congo': 'COG', 'republica congo': 'COG', 'congo': 'COG', 'corea': 'KOR',
  'Birmania': 'MMR', 'Inglaterra': 'GBR', 'Suiza': 'CHE', 'salvador': 'SLV',
  'eeuu': 'USA', 'ee uu': 'USA', 'estados unidos': 'USA', 'reino unido': 'GBR', 'paises bajos': 'NLD', 'corea del sur': 'KOR',
  'surcorea': 'KOR', 'norcorea': 'PRK', 'costa de marfil': 'CIV', 'republica democratica del congo': 'COD', 'rd congo': 'COD',
  'rd del congo': 'COD', 'zaire': 'COD', 'mexico': 'MEX', 'sudafrica': 'ZAF', 'nueva zelanda': 'NZL', 'el salvador': 'SLV',
  'trinidad y tobago': 'TTO', 'san cristobal y nieves': 'KNA', 'guinea ecuatorial': 'GNQ', 'sudan del sur': 'SSD',
  'suazilandia': 'SWZ', 'swazilandia': 'SWZ', 'emiratos arabes': 'ARE', 'rep. dominicana': 'DOM', 'salomon': 'SLB',
  'congo kinsasa': 'COD', 'rep checa': 'CZE', 'Rep. Checa': 'CZE', 'kazakistan': 'KAZ', 'rep centroafricana': 'CAF',
  'rep. democratica del congo': 'COD',
};
for (const [text, code] of Object.entries(TYPED_ES)) {
  for (const [lang, index] of Object.entries(typed)) {
    const got = findExact(index, text);
    check(got === code, `"${text}" should name ${code} on a page in ${lang}, the matcher finds ${got ?? 'no single place'}`);
  }
}
// Capitals typed the Spanish way, on a page in either language.
const TYPED_CAPITALS_ES = {
  'londres': 'GBR', 'pekin': 'CHN', 'ciudad de mexico': 'MEX', 'mexico df': 'MEX', 'washington dc': 'USA', 'moscu': 'RUS',
  'varsovia': 'POL', 'atenas': 'GRC', 'copenhague': 'DNK', 'estocolmo': 'SWE', 'la haya': 'NLD', 'ciudad del cabo': 'ZAF',
  'el cairo': 'EGY', 'la habana': 'CUB', 'la paz': 'BOL', 'puerto argentino': 'FLK', 'ciudad de san marino': 'SMR', 'riyad': 'SAU', 'kinsasa': 'COD',
};
for (const lang of ['en', 'es']) {
  const capitalIndex = buildIndex(listed, 'capital', lang);
  for (const [text, code] of Object.entries(TYPED_CAPITALS_ES)) {
    const got = findExact(capitalIndex, text);
    check(got === code, `capital "${text}" should name ${code} on a page in ${lang}, the matcher finds ${got ?? 'no single place'}`);
  }
}

// Name them all accepts a typed country at once unless the text is also the start
// of another answer country's name or alias, in either language (Niger, Nigeria),
// and then waits to see whether more is coming. Keep those pairs to the ones the
// names themselves make: an old or rare alias that starts with another answer's
// whole name ("China Taipéi" for Taiwan, "Honduras Británica" for Belize) would
// slow down a common answer for everyone, English page included.
const PREFIX_PAIRS = new Set([
  'COG<COD', 'DMA<DOM', 'GBR<UKR', 'GIN<GNB', 'GIN<GNQ', 'IRL<GBR', 'KOR<PRK', 'NER<NGA', 'SDN<SSD',
]);
{
  const squeezed = (s) => normalize(s).replace(/ /g, '');
  const answerNames = countries.filter((c) => c.answer).map((c) => [c.code, namesOf(c).map(squeezed)]);
  const found = new Set();
  for (const [a, mine] of answerNames) {
    for (const [b, theirs] of answerNames) {
      if (a === b) continue;
      const hit = mine.find((m) => theirs.some((t) => t !== m && t.startsWith(m)));
      if (!hit) continue;
      found.add(`${a}<${b}`);
      check(PREFIX_PAIRS.has(`${a}<${b}`), `"${hit}" (${a}) is the start of a name or alias of ${b}: Name them all would wait after it`);
    }
  }
  for (const pair of PREFIX_PAIRS) check(found.has(pair), `${pair} is listed as a prefix pair, but no longer is`);
}
check(displayName(byCode.get('DEU'), 'es') === 'Alemania' && displayName(byCode.get('DEU'), 'en') === 'Germany',
  'match.js displayName does not localise');
check(capitalName(byCode.get('GBR'), 'es') === 'Londres' && capitalName(byCode.get('ATA'), 'es') === null,
  'match.js capitalName does not localise');
check(search(buildIndex(listed, 'capital', 'es'), 'Londres')[0]?.label === 'Londres — Reino Unido',
  'capitals labels are not in Spanish on a page in Spanish');

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

// No border drawn as coast. The globe draws the arcs only one country uses as
// coastline, so a land border has to be one arc both sides share. Where one
// side lacks a junction vertex, the two sides come out as separate one-country
// arcs lying on top of each other (Algeria against Morocco and Western Sahara
// was a 26 km tick in the Sahara until build-data split T-junctions). So no
// two one-country arcs, of the same country or not, may run along each other:
// within a grid unit of the same line for more than half a grid unit. One grid
// unit, as the build uses: a looser test flags real coasts that leave a point
// at a narrow angle, such as Spain's and Gibraltar's shores of the Bay of
// Gibraltar, 28° apart. Hops across the antimeridian span the whole grid in
// planar terms and are left out.
{
  const [[sx, sy], [tx, ty]] = [world.transform.scale, world.transform.translate];
  const users = new Map();
  const walk = (arcs, id) => {
    for (const a of arcs) {
      if (Array.isArray(a)) { walk(a, id); continue; }
      const i = a < 0 ? ~a : a;
      if (!users.has(i)) users.set(i, []);
      users.get(i).push(id);
    }
  };
  for (const g of world.objects.countries.geometries) walk(g.arcs, g.id);
  const segs = []; // one-country arc segments, in grid units
  world.arcs.forEach((arc, i) => {
    if (users.get(i)?.length !== 1) return;
    let x = 0, y = 0;
    const pts = arc.map(([dx, dy]) => [x += dx, y += dy]);
    for (let k = 1; k < pts.length; k++) {
      const [a, b] = [pts[k - 1], pts[k]];
      const hop = Math.abs(b[0] - a[0]) * sx > 180;
      if (!hop && (a[0] !== b[0] || a[1] !== b[1])) segs.push({ arc: i, id: users.get(i)[0], a, b });
    }
  });
  const CELL = 256;
  const cells = new Map();
  segs.forEach((s, n) => {
    for (let cx = Math.floor((Math.min(s.a[0], s.b[0]) - 1) / CELL); cx <= Math.floor((Math.max(s.a[0], s.b[0]) + 1) / CELL); cx++) {
      for (let cy = Math.floor((Math.min(s.a[1], s.b[1]) - 1) / CELL); cy <= Math.floor((Math.max(s.a[1], s.b[1]) + 1) / CELL); cy++) {
        const key = cx * 1024 + cy;
        if (!cells.has(key)) cells.set(key, []);
        cells.get(key).push(n);
      }
    }
  });
  /** How far t runs along s, within one grid unit of s's line (0 if it strays). */
  const alongside = (s, t) => {
    const dx = s.b[0] - s.a[0], dy = s.b[1] - s.a[1], len = Math.hypot(dx, dy);
    const off = (p) => Math.abs(dx * (p[1] - s.a[1]) - dy * (p[0] - s.a[0])) / len;
    if (off(t.a) > 1 || off(t.b) > 1) return 0;
    const at = (p) => (dx * (p[0] - s.a[0]) + dy * (p[1] - s.a[1])) / len;
    return Math.min(len, Math.max(at(t.a), at(t.b))) - Math.max(0, Math.min(at(t.a), at(t.b)));
  };
  const overlaps = new Map();
  const seen = new Set();
  for (const list of cells.values()) {
    for (let u = 0; u < list.length; u++) {
      for (let v = u + 1; v < list.length; v++) {
        const [s, t] = [segs[list[u]], segs[list[v]]];
        if (s.arc === t.arc) continue;
        const run = Math.max(alongside(s, t), alongside(t, s));
        const pair = `${Math.min(list[u], list[v])}-${Math.max(list[u], list[v])}`;
        if (run <= 0.5 || seen.has(pair)) continue;
        seen.add(pair);
        const key = [s.id, t.id].sort().join('/');
        if (!overlaps.has(key)) overlaps.set(key, { units: 0, at: `${(s.a[1] * sy + ty).toFixed(3)},${(s.a[0] * sx + tx).toFixed(3)}` });
        overlaps.get(key).units += run;
      }
    }
  }
  check(overlaps.size === 0, `world.json draws borders as coast (one-country arcs on top of each other): ${
    [...overlaps].map(([key, o]) => `${key} for ${o.units.toFixed(1)} grid units at ${o.at}`).join(', ')}`);
}

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

// ---------------------------------------------------------------- difficulty

// Hard mode's default globle.hard-weights: how many tickets an answer of each
// tier gets in the draw. The server owns the real setting; this copy only
// prints the mix the tiers give.
const HARD_WEIGHTS = { 1: 0, 2: 1, 3: 3, 4: 6, 5: 8 };
// The default globle.answer-repeat-window-days. Each daily caps it at a third
// of its own pool (DailyPuzzles), so that is 60 days for classic and 55 for Hard.
const REPEAT_WINDOW_DAYS = 60;
// Each tier between these sizes, so none swallows the hard pool or vanishes.
const TIER_SIZE = [20, 55];
// The examples the owner gave for each end of the scale.
const TIER_1 = ['USA', 'CHN', 'RUS', 'BRA', 'IND', 'AUS', 'CAN', 'FRA', 'DEU', 'GBR', 'JPN', 'ITA', 'ESP', 'MEX', 'EGY'];
const TIER_5 = ['AND', 'LIE', 'MCO', 'SMR', 'VAT', 'TON', 'TUV', 'NRU', 'KNA', 'SWZ', 'LSO', 'DJI', 'COM', 'STP',
  'GNQ', 'GNB', 'BTN', 'BRN', 'TLS'];

const tiers = { 1: [], 2: [], 3: [], 4: [], 5: [] };
for (const c of answers) tiers[c.difficulty]?.push(c.code);
for (const [tier, list] of Object.entries(tiers)) {
  check(list.length >= TIER_SIZE[0] && list.length <= TIER_SIZE[1],
    `difficulty ${tier} has ${list.length} answers, expected ${TIER_SIZE.join('..')}`);
}
for (const code of TIER_1) check(byCode.get(code)?.difficulty === 1, `${code} should be difficulty 1, not ${byCode.get(code)?.difficulty}`);
for (const code of TIER_5) check(byCode.get(code)?.difficulty === 5, `${code} should be difficulty 5, not ${byCode.get(code)?.difficulty}`);

// ---------------------------------------------------------------- small island nations

// "Mainland only" means no tiny island nations: Japan still counts, Tonga does
// not. These are the owner's lists, and between them they pin the rule's
// edges: Solomon Islands is under the 30,000 km² cap, Taiwan over it; Haiti,
// Timor-Leste and Brunei are small but share their islands. Microstates on a
// mainland are not islands.
const MAINLAND_POOL = ['JPN', 'GBR', 'IRL', 'ISL', 'CUB', 'NZL', 'MDG', 'LKA', 'PHL', 'IDN', 'TWN', 'HTI', 'DOM',
  'PNG', 'TLS', 'BRN', 'AUS', 'AND', 'VAT', 'MCO', 'SMR', 'LIE', 'LUX'];
const SMALL_ISLANDS = ['TON', 'FJI', 'JAM', 'BHS', 'TTO', 'BRB', 'MLT', 'CYP', 'BHR', 'SGP', 'MDV', 'MUS', 'SYC',
  'COM', 'STP', 'CPV', 'SLB', 'VUT', 'WSM', 'KIR', 'TUV', 'NRU', 'PLW', 'MHL', 'FSM', 'LCA', 'VCT', 'GRD', 'ATG',
  'DMA', 'KNA'];

const smallIslands = answers.filter((c) => c.smallIsland).map((c) => c.code);
for (const code of MAINLAND_POOL) check(byCode.get(code)?.smallIsland === false, `${code} should be in the mainland pool`);
const extraSmall = smallIslands.filter((code) => !SMALL_ISLANDS.includes(code));
const missingSmall = SMALL_ISLANDS.filter((code) => !smallIslands.includes(code));
check(extraSmall.length === 0, `small island nations not on the expected list: ${extraSmall}`);
check(missingSmall.length === 0, `expected small island nations not flagged: ${missingSmall}`);
// The parts of the rule these files can show on their own: no land border
// with another answer, and a drawn shape under the area cap.
const smallKm2 = new Map();
for (const code of smallIslands) {
  const neighbour = answers.find((c) => c.code !== code && dist(code, c.code) === 0);
  check(!neighbour, `${code} is a small island nation but borders ${neighbour?.code}`);
  if (!shapeByCode.has(code)) continue; // already a failure above
  smallKm2.set(code, geoArea(shapeByCode.get(code)) * EARTH_RADIUS_KM ** 2);
  check(smallKm2.get(code) < 30000, `${code} is a small island nation but is drawn ${smallKm2.get(code).toFixed(0)} km²`);
}

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
  // Its Spanish common name should be one a Spanish speaker can type here.
  const spa = w.translations?.spa?.common;
  if (c && spa && findExact(typed.es, spa) !== c.code) {
    notes.push(`${w.cca3}: world-countries' Spanish name "${spa}" does not find it; ours is ${c.nameEs}`);
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
console.log(`answers by difficulty: ${Object.entries(tiers).map(([t, l]) => `${t}: ${l.length}`).join(', ')}`);
const tickets = Object.entries(tiers).map(([t, l]) => [t, l.length, HARD_WEIGHTS[t], l.length * HARD_WEIGHTS[t]]);
const totalTickets = tickets.reduce((sum, [, , , m]) => sum + m, 0);
console.log(`hard pool (default weights ${Object.values(HARD_WEIGHTS).join('/')}): `
  + `${tickets.filter(([, , w]) => w > 0).reduce((sum, [, size]) => sum + size, 0)} answers`);
for (const [t, size, w, m] of tickets) {
  const share = totalTickets ? (100 * m) / totalTickets : 0;
  const each = w > 0 ? `  (${(share / size).toFixed(2)}% each)` : '  (never drawn)';
  console.log(`  tier ${t}  ${String(size).padStart(3)} × ${w} = ${String(m).padStart(3)}  ${share.toFixed(1).padStart(5)}%${each}`);
}

/**
 * The tier mix of the Hard daily in the long run. The shares above are for one
 * draw, which is what Hard practice gets. The daily also skips its own answers
 * from the last window days and the day's classic answer, and the hardest tiers
 * are drawn most, so they spend the most time skipped. This plays the server's
 * rules (DailyPuzzles) day after day with a fixed seed, so the numbers it prints
 * are stable from run to run.
 */
function hardDailyMix(days) {
  let seed = 0x9e3779b9;
  const random = () => { // mulberry32
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const draw = (pool, skip) => {
    const open = pool.filter(([code]) => !skip.has(code));
    let ticket = random() * open.reduce((sum, [, w]) => sum + w, 0);
    for (const [code, w] of open) if ((ticket -= w) < 0) return code;
    return open.at(-1)[0];
  };
  const classicPool = answers.map((c) => [c.code, 1]);
  const hardPool = answers.filter((c) => HARD_WEIGHTS[c.difficulty] > 0).map((c) => [c.code, HARD_WEIGHTS[c.difficulty]]);
  const classicWindow = Math.min(REPEAT_WINDOW_DAYS, Math.floor(classicPool.length / 3));
  const hardWindow = Math.min(REPEAT_WINDOW_DAYS, Math.floor(hardPool.length / 3));
  const classic = [], hard = [];
  const counts = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  for (let day = 0; day < days; day++) {
    classic.push(draw(classicPool, new Set(classic.slice(-classicWindow))));
    hard.push(draw(hardPool, new Set([...hard.slice(-hardWindow), classic.at(-1)])));
    counts[byCode.get(hard.at(-1)).difficulty]++;
  }
  return { hardWindow, counts };
}
const DAYS = 100_000;
const mix = hardDailyMix(DAYS);
console.log(`hard daily over ${DAYS.toLocaleString('en')} simulated days (no answer from its last ${mix.hardWindow}, never the classic one): `
  + Object.entries(mix.counts).filter(([t]) => HARD_WEIGHTS[t] > 0)
    .map(([t, k]) => `tier ${t} ${((100 * k) / DAYS).toFixed(1)}%`).join(', '));
const biggestSmall = [...smallKm2].sort((a, b) => b[1] - a[1])[0];
console.log(`mainland pool: ${answers.length - smallIslands.length} answers; ${smallIslands.length} small island nations left out`
  + (biggestSmall ? ` (largest drawn: ${biggestSmall[0]}, ${Math.round(biggestSmall[1]).toLocaleString('en')} km²)` : ''));
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
