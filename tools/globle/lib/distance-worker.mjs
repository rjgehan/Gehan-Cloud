// One row of the distance matrix at a time: the closest chord from country i to
// every country after it. The main thread hands rows out as workers free up,
// so no thread is left holding a queue of long rows while the others idle.

import { parentPort, workerData } from 'node:worker_threads';
import { closestChord } from './sphere.mjs';

const { trees } = workerData;

parentPort.on('message', (i) => {
  const row = new Float64Array(trees.length);
  for (let j = i + 1; j < trees.length; j++) row[j] = closestChord(trees[i], trees[j]);
  parentPort.postMessage({ i, row }, [row.buffer]);
});
