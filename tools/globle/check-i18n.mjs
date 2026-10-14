#!/usr/bin/env node
// Checks that the page's English and Spanish say the same things:
//
//   npm run check:i18n        (from tools/globle; needs no npm install)
//
// The dictionaries are STRINGS.en and STRINGS.es in static/globle/js/i18n.js.
// Fails (non-zero exit) when:
//   - the two languages' key sets differ;
//   - any value is empty (or only spaces);
//   - a key is a plural ({ one, other }) in one language and a string in the
//     other, a plural lacks "other", or names a category Intl.PluralRules has not;
//   - the {placeholders} differ between the languages, or between the forms of
//     one plural (a form that drops {n} would print the wrong sentence);
//   - the template or a script asks for a key that does not exist: every
//     data-i18n* attribute in templates/globle/index.html, and every quoted
//     "area.key" string in static/globle/js whose first part is one of the
//     dictionary's areas ("end.", "board.", ...). Keys built at run time
//     (`preset.${name}.label`) cannot be seen this way; their parts are listed in
//     i18n.js beside each other, and a missing one shows on the page as its key.
//
// i18n.js imports nothing, so it is loaded as a data: URL, like check-data.mjs
// loads match.js: ESM on any Node 20, whatever the file's extension says.

import fs from 'node:fs';
import path from 'node:path';

import { REPO } from './lib/common.mjs';

const STATIC_JS = path.join(REPO, 'src/main/resources/static/globle/js');
const TEMPLATE = path.join(REPO, 'src/main/resources/templates/globle/index.html');

const failures = [];
const fail = (msg) => failures.push(msg);
const check = (ok, msg) => { if (!ok) fail(msg); };

const source = fs.readFileSync(path.join(STATIC_JS, 'i18n.js'), 'utf8');
const { STRINGS, LANGS, I18N_ATTRIBUTES } = await import('data:text/javascript,' + encodeURIComponent(source));

// ---------------------------------------------------------------- the dictionaries

const PLURAL_CATEGORIES = new Set(['zero', 'one', 'two', 'few', 'many', 'other']);
const isPlural = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const placeholders = (text) => [...String(text).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

check(same(LANGS, ['en', 'es']), `LANGS is ${JSON.stringify(LANGS)}, expected ["en","es"]`);
for (const lang of LANGS) check(isPlural(STRINGS[lang]), `STRINGS.${lang} is missing`);

/** A key's placeholders in one language: every form must have the same set. */
function placeholdersOf(lang, key) {
  const value = STRINGS[lang][key];
  if (!isPlural(value)) return placeholders(value);
  const sets = Object.entries(value).map(([form, text]) => [form, placeholders(text)]);
  for (const [form, set] of sets.slice(1)) {
    check(same(set, sets[0][1]), `${lang} "${key}": form "${form}" has {${set.join('} {')}}, `
      + `form "${sets[0][0]}" has {${sets[0][1].join('} {')}}`);
  }
  return sets.length ? sets[0][1] : [];
}

function checkValue(lang, key, value) {
  if (isPlural(value)) {
    check('other' in value, `${lang} "${key}": a plural without an "other" form`);
    for (const [form, text] of Object.entries(value)) {
      check(PLURAL_CATEGORIES.has(form), `${lang} "${key}": "${form}" is not a plural category`);
      check(typeof text === 'string' && text.trim() !== '', `${lang} "${key}".${form} is empty`);
    }
  } else {
    check(typeof value === 'string' && value.trim() !== '', `${lang} "${key}" is empty or not a string`);
  }
}

const en = STRINGS.en || {};
const es = STRINGS.es || {};
const enKeys = new Set(Object.keys(en));
const esKeys = new Set(Object.keys(es));
for (const key of enKeys) check(esKeys.has(key), `"${key}" is in English but not in Spanish`);
for (const key of esKeys) check(enKeys.has(key), `"${key}" is in Spanish but not in English`);

for (const lang of LANGS) {
  for (const [key, value] of Object.entries(STRINGS[lang] || {})) {
    check(/^[a-z][A-Za-z0-9]*(\.[A-Za-z0-9-]+)+$/.test(key), `${lang} "${key}": keys are dotted words ("end.found")`);
    checkValue(lang, key, value);
  }
}
for (const key of enKeys) {
  if (!esKeys.has(key)) continue;
  check(isPlural(en[key]) === isPlural(es[key]), `"${key}" is a plural in one language and a string in the other`);
  const a = placeholdersOf('en', key);
  const b = placeholdersOf('es', key);
  check(same(a, b), `"${key}": English has {${a.join('} {')}}, Spanish has {${b.join('} {')}}`);
}

// ---------------------------------------------------------------- who asks for what

const AREAS = new Set([...enKeys].map((k) => k.split('.')[0]));
let templateRefs = 0;
let codeRefs = 0;

// Comments explain the attributes with examples; only real markup counts.
const template = fs.readFileSync(TEMPLATE, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
const attributes = ['data-i18n', ...I18N_ATTRIBUTES.map((a) => `data-i18n-${a}`)];
for (const m of template.matchAll(/\s(data-i18n(?:-[a-z-]+)?)="([^"]*)"/g)) {
  const [, attribute, key] = m;
  templateRefs++;
  check(attributes.includes(attribute), `index.html: ${attribute} is not filled by applyTranslations `
    + `(it fills ${attributes.join(', ')})`);
  check(enKeys.has(key), `index.html: ${attribute}="${key}" is not a key`);
}

for (const file of fs.readdirSync(STATIC_JS).filter((f) => f.endsWith('.js')).sort()) {
  if (file === 'i18n.js') continue;
  const code = fs.readFileSync(path.join(STATIC_JS, file), 'utf8');
  for (const m of code.matchAll(/["']([a-z][A-Za-z0-9]*(?:\.[A-Za-z0-9-]+)+)["']/g)) {
    const key = m[1];
    if (!AREAS.has(key.split('.')[0])) continue;
    codeRefs++;
    check(enKeys.has(key), `${file}: "${key}" is not a key`);
  }
}

// ---------------------------------------------------------------- report

const plurals = [...enKeys].filter((k) => isPlural(en[k])).length;
console.log(`i18n: ${enKeys.size} keys in English, ${esKeys.size} in Spanish (${plurals} plurals); `
  + `${templateRefs} template references, ${codeRefs} script references checked`);
if (failures.length) {
  console.error(`\n${failures.length} FAILURES:`);
  for (const f of failures) console.error(`  ✗ ${f}`);
  process.exit(1);
}
console.log('all i18n checks passed');
