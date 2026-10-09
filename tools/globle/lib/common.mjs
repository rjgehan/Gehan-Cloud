// Shared by build-data.mjs and check-data.mjs.

import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');

export const OUTPUTS = {
  countries: path.join(REPO, 'src/main/resources/globle/countries.json'),
  distances: path.join(REPO, 'src/main/resources/globle/distances.json'),
  world: path.join(REPO, 'src/main/resources/static/globle/world.json'),
};

export const REGIONS = new Set(['africa', 'americas', 'asia', 'europe', 'oceania', 'antarctica']);

/**
 * The comparison the browser is promised it can rely on: case and accents do
 * not count. Punctuation does, so "Ndjamena" stays an alias of "N'Djamena" for
 * a client that only folds case and accents.
 */
export function normalise(s) {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim();
}
