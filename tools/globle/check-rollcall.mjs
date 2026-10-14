#!/usr/bin/env node
// Checks Name them all's typing rules against the country list:
//
//   npm run check:rollcall    (from tools/globle; part of npm run check; needs no npm install)
//
// The rules are in static/globle/js/rollcall.js (what counts, the lenient spell
// check and its thresholds) and match.js (the fold and distances it measures with).
// The thresholds were calibrated against a corpus of 1,100+ realistic misspellings
// of all 197 countries in English and Spanish and 350+ things that are not
// countries; the rates are in docs/globle.md. This keeps the cases that must hold:
//
//   - every name in rollcall.js NOT_COUNTING is still an alias of its country, is
//     not its main name in either language, and belongs to one of the 197; and the
//     classic modes' matcher still finds the country from it ("England" is the UK);
//   - every one of those names is in the server's copy of the list,
//     NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL (read from the Java source), so
//     a name-change request for it never closes by itself;
//   - England, Persia, Antarctica, America... are explained, never counted, nor is
//     the second half of a country's name (Tobago, Barbuda, Herzegovina);
//   - a nationality ("Russian", "chino") is offered, never counted at once;
//   - "Comoro Islands" or "islas Filipinas" counts as the country inside it;
//   - the user's examples: "kyrigistan" counts at once as Kyrgyzstan, "naruto"
//     offers Nauru; Spanish ones; and the common learner errors;
//   - cities, continents, regions, historical states and random words never count,
//     and very short gibberish gets no suggestion at all;
//   - no single slip (a letter dropped, doubled, or swapped with the next) in any
//     name or alias of the 197 ever counts as a different country;
//   - the study page (study-logic.js) places the countries a box retune could get
//     wrong in the usual part of their continent: Thailand in the south-east of
//     Asia, Pakistan in the south, Kazakhstan in the middle, Nigeria in the west of
//     Africa, Denmark in the north of Europe.
//
// match.js and i18n.js import nothing, rollcall.js only match.js, and study-logic.js
// only i18n.js and format.js (which imports i18n.js), so all load as data: URLs with
// their imports pointed at each other's, ESM on any Node 20.

import fs from 'node:fs';
import path from 'node:path';

import { REPO } from './lib/common.mjs';

const STATIC_JS = path.join(REPO, 'src/main/resources/static/globle/js');
const COUNTRIES = path.join(REPO, 'src/main/resources/globle/countries.json');

const failures = [];
const fail = (msg) => failures.push(msg);
const check = (ok, msg) => { if (!ok) fail(msg); };

const asUrl = (code) => 'data:text/javascript,' + encodeURIComponent(code);
const matchUrl = asUrl(fs.readFileSync(path.join(STATIC_JS, 'match.js'), 'utf8'));
const rollcallSource = fs.readFileSync(path.join(STATIC_JS, 'rollcall.js'), 'utf8');
check(/from "\.\/match\.js";/.test(rollcallSource), 'rollcall.js no longer imports "./match.js" alone: update how this loads it');
const match = await import(matchUrl);
const rc = await import(asUrl(rollcallSource.replace(/from "\.\/match\.js";/g, `from "${matchUrl}";`)));

const raw = JSON.parse(fs.readFileSync(COUNTRIES, 'utf8'));
// The /globle/api/countries payload, as CountryView sends it.
const countries = raw.map((c) => ({
  code: c.code, name: c.name, nameEs: c.nameEs, iso2: c.iso2, aliases: c.aliases, aliasesEs: c.aliasesEs,
  region: c.region, canBeAnswer: c.answer, capital: c.capital,
}));
const byCode = new Map(countries.map((c) => [c.code, c]));
const indexes = { en: match.buildIndex(countries, 'country', 'en'), es: match.buildIndex(countries, 'country', 'es') };
const world = rc.poolOf(countries, 'world');
const ctx = (lang, region = 'world', named = []) => ({
  index: indexes[lang], pool: rc.poolOf(countries, region), named: new Set(named), byCode,
});
const enter = (lang, text, region, named) => rc.decideEnter(text, ctx(lang, region, named));
const counted = (d) => d.kind === 'accept' || d.kind === 'confident';
const show = (d) => JSON.stringify({ kind: d.kind, code: d.code, codes: d.codes, term: d.term });
let cases = 0;

// ---------------------------------------------------------------- NOT_COUNTING

const squeeze = (s) => match.normalize(s).replace(/ /g, '');
for (const [kind, list] of Object.entries(rc.NOT_COUNTING)) {
  for (const [code, names] of Object.entries(list)) {
    const c = byCode.get(code);
    check(!!c && c.canBeAnswer, `NOT_COUNTING.${kind}: ${code} is not one of the 197`);
    if (!c) continue;
    for (const name of names) {
      cases++;
      const aliases = [...c.aliases, ...c.aliasesEs].map(squeeze);
      check(aliases.includes(squeeze(name)), `NOT_COUNTING.${kind}: "${name}" is no longer an alias of ${code}`);
      check(![c.name, c.nameEs].map(squeeze).includes(squeeze(name)), `NOT_COUNTING.${kind}: "${name}" is ${code}'s own name`);
      for (const lang of ['en', 'es']) {
        check(match.findExact(indexes[lang], name) === code, `"${name}" no longer finds ${code} in the classic modes (page in ${lang})`);
        const d = rc.decide(name, ctx(lang));
        check(d.kind === kind && d.code === code, `"${name}" in Name them all (page in ${lang}): ${show(d)}, expected ${kind} ${code}`);
      }
    }
  }
}

// The server's copy (NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL) keeps a
// name-change request for one of these names open; it must hold every one, or a
// request for it closes by itself although Name them all still refuses the name.
const SERVER_LIST = 'src/main/java/cloud/gehan/globle/service/NameRequestService.java';
const javaSource = fs.readFileSync(path.join(REPO, SERVER_LIST), 'utf8');
const listStart = javaSource.indexOf('NOT_COUNTED_IN_NAME_THEM_ALL = Map.ofEntries(');
const listEnd = listStart < 0 ? -1 : javaSource.indexOf('));', listStart);
check(listStart >= 0 && listEnd > listStart, `${SERVER_LIST}: NOT_COUNTED_IN_NAME_THEM_ALL not found: update how this reads it`);
const serverList = new Map();
for (const m of javaSource.slice(listStart, listEnd + 3).matchAll(/Map\.entry\("([A-Z]{3})",\s*List\.of\(([^)]*)\)\)/g)) {
  serverList.set(m[1], [...m[2].matchAll(/"([^"]+)"/g)].map((x) => squeeze(x[1])));
}
check(serverList.size > 0, `${SERVER_LIST}: no entries read from NOT_COUNTED_IN_NAME_THEM_ALL`);
for (const [kind, list] of Object.entries(rc.NOT_COUNTING)) {
  for (const [code, names] of Object.entries(list)) {
    for (const name of names) {
      cases++;
      check((serverList.get(code) || []).includes(squeeze(name)),
        `NOT_COUNTING.${kind}: "${name}" (${code}) is missing from NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL, `
        + 'so a name-change request for it would close by itself');
    }
  }
}

// ---------------------------------------------------------------- what Enter does

const EXPECT = [
  // [text, kind, code or codes]: the user's examples first.
  ['kyrigistan', 'confident', 'KGZ'],
  ['naruto', 'suggest', ['NRU']],
  ['England', 'part', 'GBR'], ['Inglaterra', 'part', 'GBR'], ['Wales', 'part', 'GBR'], ['Gaza', 'part', 'PSE'],
  ['englnd', 'part', 'GBR'],
  ['Persia', 'former', 'IRN'], ['Zaire', 'former', 'COD'], ['Rhodesia', 'former', 'ZWE'], ['Siam', 'former', 'THA'],
  ['Islas Ellice', 'former', 'TUV'], ['Formosa', 'former', 'TWN'],
  ['Antarctica', 'territory', 'ATA'], ['Greenland', 'territory', 'GRL'], ['grenland', 'territory', 'GRL'],
  ['Puerto Rico', 'territory', 'PRI'], ['Hong Kong', 'territory', 'HKG'],
  ['America', 'suggest', ['USA']], ['américa', 'suggest', ['USA']],
  ['United Kingdom', 'accept', 'GBR'], ['Great Britain', 'accept', 'GBR'], ['Holland', 'accept', 'NLD'], ['Burma', 'accept', 'MMR'],
  ['nigera', 'suggest', ['NER', 'NGA']], ['iram', 'suggest', ['IRN', 'IRQ']], ['slovania', 'suggest', ['SVK', 'SVN']],
  ['dominicaa', 'suggest', ['DMA', 'DOM']], ['Marshall', 'suggest', ['MHL']], ['Saudi', 'suggest', ['SAU']],
  ['Costa', 'unknown', null], ['North', 'unknown', null],
  // The second half of a country's name is a part of it, not one of its names.
  ['Tobago', 'part', 'TTO'], ['Barbuda', 'part', 'ATG'], ['Herzegovina', 'part', 'BIH'], ['Nevis', 'part', 'KNA'],
  ['Nieves', 'part', 'KNA'], ['Príncipe', 'part', 'STP'], ['the Grenadines', 'part', 'VCT'], ['las Granadinas', 'part', 'VCT'],
  // A country's name with the word for islands around it; a group of islands that is no country.
  ['Comoro Islands', 'confident', 'COM'], ['Fiji Islands', 'confident', 'FJI'], ['islas Filipinas', 'confident', 'PHL'],
  ['Cook Islands', 'territory', 'COK'], ['Great Britain island', 'unknown', null],
];
for (const lang of ['en', 'es']) {
  for (const [text, kind, want] of EXPECT) {
    cases++;
    const d = enter(lang, text);
    const got = d.kind === 'suggest' ? [...d.codes].sort() : d.code;
    check(d.kind === kind && JSON.stringify(got) === JSON.stringify(want),
      `"${text}" (page in ${lang}): ${show(d)}, expected ${kind} ${JSON.stringify(want)}`);
  }
  cases += 4;
  check(enter(lang, 'kyrigistan', 'world', ['KGZ']).kind === 'already', `${lang}: kyrigistan with Kyrgyzstan named should say so`);
  check(enter(lang, 'kenyaa', 'europe').kind === 'outside', `${lang}: kenyaa in Europe should be outside`);
  check(enter(lang, 'America', 'world', ['USA']).kind === 'already', `${lang}: America with the US named`);
  check(enter(lang, 'nigera', 'world', ['NGA']).codes?.join() === 'NER', `${lang}: nigera with Nigeria named should offer Niger alone`);
  cases += 2;
  check(enter(lang, 'comoro islands', 'world', ['COM']).kind === 'already', `${lang}: comoro islands with Comoros named should say so`);
  check(enter(lang, 'islas filipinas', 'europe').kind === 'outside', `${lang}: islas filipinas in Europe should be outside`);
}

// The word for a country's people is offered, never counted at once ("Did you mean
// Russia?"), in English and Spanish.
const NATIONALITY = {
  russian: 'RUS', german: 'DEU', indian: 'IND', kenyan: 'KEN', honduran: 'HND', 'sri lankan': 'LKA', yemeni: 'YEM',
  qatari: 'QAT', swiss: 'CHE', swede: 'SWE', somali: 'SOM', chino: 'CHN', suizo: 'CHE', argentino: 'ARG',
  uruguayo: 'URY', uruguaya: 'URY', francés: 'FRA', húngaro: 'HUN', rumana: 'ROU', sueca: 'SWE', checa: 'CZE',
  filipina: 'PHL', alemana: 'DEU', dominicano: 'DOM',
};
for (const lang of ['en', 'es']) {
  for (const [text, code] of Object.entries(NATIONALITY)) {
    cases++;
    const d = enter(lang, text);
    check(d.kind === 'suggest' && d.codes.includes(code), `"${text}" (page in ${lang}) should only offer ${code}: ${show(d)}`);
  }
}

// Misspellings that count at once, as their country: the learner errors the spec
// lists, and Spanish ones.
const COUNT = {
  phillipines: 'PHL', phillipenes: 'PHL', columbia: 'COL', kazakstan: 'KAZ', kyrgystan: 'KGZ', azerbajan: 'AZE',
  lichtenstein: 'LIE', tadjikistan: 'TJK', zimbabwae: 'ZWE', madagaskar: 'MDG', equador: 'ECU', venezuala: 'VEN',
  guatamala: 'GTM', hondoras: 'HND', belguim: 'BEL', sweeden: 'SWE', norwey: 'NOR', portugual: 'PRT', lithuana: 'LTU',
  ukrane: 'UKR', romainia: 'ROU', bulgeria: 'BGR', albainia: 'ALB', greese: 'GRC', turky: 'TUR', isreal: 'ISR',
  lebenon: 'LBN', jordon: 'JOR', kuwiat: 'KWT', quatar: 'QAT', bahrian: 'BHR', yeman: 'YEM', afganistan: 'AFG',
  'shri lanka': 'LKA', thialand: 'THA', camboida: 'KHM', veitnam: 'VNM', malasia: 'MYS', singapor: 'SGP',
  'papa new guinea': 'PNG', 'soloman islands': 'SLB', vanuato: 'VUT', tuvalo: 'TUV', naru: 'NRU',
  'marshal islands': 'MHL', 'new zeland': 'NZL', austrailia: 'AUS', mauritious: 'MUS', djibuti: 'DJI', eritria: 'ERI',
  ethopia: 'ETH', somolia: 'SOM', luxemburg: 'LUX', finnland: 'FIN', irland: 'IRL', trinadad: 'TTO',
  barbadoes: 'BRB', seychells: 'SYC', mozambiqe: 'MOZ',
  kirguistan: 'KGZ', kirgistan: 'KGZ', quirguistán: 'KGZ', kazajistan: 'KAZ', kasajistan: 'KAZ', kazajastan: 'KAZ',
  filipnas: 'PHL', filpinas: 'PHL', fillipinas: 'PHL', felipinas: 'PHL', volivia: 'BOL', ungary: 'HUN', katar: 'QAT',
};
// Never counted: cities, continents, regions, historical states, random words.
const NEVER = [
  'prussia', 'kurdistan', 'paris', 'london', 'tokyo', 'texas', 'california', 'hawaii', 'siberia', 'tibet', 'catalonia',
  'bavaria', 'africa', 'europe', 'asia', 'oceania', 'ussr', 'yugoslavia', 'czechoslovakia', 'pizza', 'hello', 'asdf',
  'brittany', 'tunis', 'venice', 'berlin', 'lagos', 'perth', 'norte', 'perro', 'roman', 'nature', 'islands', 'central',
  'london', 'madrid', 'moscow', 'beijing', 'cairo', 'sydney', 'florida', 'scandinavia', 'balkans', 'middle east',
  'south america', 'sudamérica', 'europa', 'áfrica', 'unión soviética', 'yugoslavia', 'checoslovaquia', 'prusia', 'londres',
];
const GIBBERISH = ['x', 'qq', 'ab', 'abc', 'xyz', 'zzz', 'jkl', 'qwe', 'dfg'];
for (const lang of ['en', 'es']) {
  for (const [text, code] of Object.entries(COUNT)) {
    cases++;
    const d = enter(lang, text);
    check(counted(d) && d.code === code, `"${text}" (page in ${lang}) should count as ${code}: ${show(d)}`);
  }
  for (const text of NEVER) {
    cases++;
    const d = enter(lang, text);
    check(!counted(d), `"${text}" (page in ${lang}) is not a country but counted as ${d.code}`);
  }
  for (const text of GIBBERISH) {
    cases++;
    check(enter(lang, text).kind === 'unknown', `"${text}" (page in ${lang}) should get nothing at all`);
  }
}

// ---------------------------------------------------------------- one slip never names another country

let slips = 0;
let slipsCounted = 0;
{
  const c = ctx('en');
  for (const country of countries.filter((x) => x.canBeAnswer)) {
    for (const name of new Set([country.name, country.nameEs, ...country.aliases, ...country.aliasesEs])) {
      const s = name.toLowerCase();
      if (s.replace(/[^a-z]/g, '').length < 5 || name === name.toUpperCase()) continue;
      const variants = new Set();
      for (let i = 1; i < s.length; i++) {
        variants.add(s.slice(0, i) + s.slice(i + 1));
        if (i < s.length - 1) variants.add(s.slice(0, i) + s[i + 1] + s[i] + s.slice(i + 2));
        variants.add(s.slice(0, i) + s[i] + s.slice(i));
      }
      for (const v of variants) {
        if (match.findExact(c.index, v)) continue;
        slips++;
        const d = rc.decideEnter(v, c);
        if (d.kind !== 'confident') continue;
        if (d.code === country.code) slipsCounted++;
        else fail(`"${v}" (one slip from "${name}", ${country.code}) counts as ${d.code}`);
      }
    }
  }
}
check(slips > 10000, `only ${slips} one-slip variants were tried`);
check(world.size === 197, `the world pool has ${world.size} countries, not 197`);

// ---------------------------------------------------------------- the study page's compass

// study-logic.js places each missed country in its continent ("In the south-east of
// Asia"). These pin the phrases a box retune could quietly get wrong: Thailand is not
// South Asia, Kazakhstan not North Asia, Pakistan not Central Asia, Nigeria is West Africa.
const i18nUrl = asUrl(fs.readFileSync(path.join(STATIC_JS, 'i18n.js'), 'utf8'));
const formatUrl = asUrl(fs.readFileSync(path.join(STATIC_JS, 'format.js'), 'utf8')
  .replace(/from "\.\/i18n\.js";/g, `from "${i18nUrl}";`));
const studySource = fs.readFileSync(path.join(STATIC_JS, 'study-logic.js'), 'utf8');
check(/from "\.\/i18n\.js";/.test(studySource) && /from "\.\/format\.js";/.test(studySource),
  'study-logic.js no longer imports "./i18n.js" and "./format.js": update how this loads it');
const i18n = await import(i18nUrl);
const study = await import(asUrl(studySource
  .replace(/from "\.\/i18n\.js";/g, `from "${i18nUrl}";`)
  .replace(/from "\.\/format\.js";/g, `from "${formatUrl}";`)));
const COMPASS = {
  'south-east': ['THA', 'LAO', 'MMR', 'KHM', 'VNM', 'IDN', 'PHL'],
  south: ['IND', 'PAK', 'NPL', 'BTN', 'LKA'],
  centre: ['KAZ', 'KGZ', 'UZB', 'TKM', 'TJK'],
  east: ['TWN', 'JPN', 'CHN', 'MNG', 'KOR'],
  'south-west': ['SAU', 'YEM', 'OMN', 'ESP', 'PRT'],
  west: ['NGA', 'BEN', 'TUR', 'ARM'],
  north: ['DNK', 'NOR', 'SWE'],
  'north-west': ['MAR', 'SEN'],
};
const places = new Map(raw.map((c) => [c.code, c]));   // with their label points
i18n.setLang('en');
for (const [want, codes] of Object.entries(COMPASS)) {
  const key = `study.compass.${want.replace(/-(\w)/, (_, l) => l.toUpperCase())}`;
  for (const code of codes) {
    cases++;
    const c = places.get(code);
    const area = study.areaOf(code, c.region);
    const expected = i18n.t(key, { area: i18n.t(`study.area.${area}`) });
    const got = study.wherePhrase({ code, region: c.region, lat: c.lat, lng: c.lng });
    check(got === expected, `study: ${code} reads "${got}", expected "${expected}"`);
  }
}
cases++;
check(study.wherePhrase({ code: 'NGA', region: 'africa', lat: null, lng: null }) === null,
  'study: an entry with no point is no place at all, even one with an override');

// ---------------------------------------------------------------- report

console.log(`rollcall: ${cases} cases in both page languages; ${slips} one-slip variants of the 197's names, `
  + `${slipsCounted} counted at once (${((100 * slipsCounted) / slips).toFixed(1)}%), none as another country`);
if (failures.length) {
  console.error(`\n${failures.length} FAILURES:`);
  for (const f of failures.slice(0, 80)) console.error(`  ✗ ${f}`);
  process.exit(1);
}
console.log('all roll call checks passed');
