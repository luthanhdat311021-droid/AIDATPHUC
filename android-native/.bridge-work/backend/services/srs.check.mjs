// Run: node backend/services/srs.check.mjs
import assert from 'node:assert/strict';
import { isDue, scheduleReview } from './srs.js';

const now = Date.parse('2026-01-01T00:00:00Z');
const apply = (card, rating) => ({ ...card, ...scheduleReview(card, rating, now) });

let card = apply({}, 'easy');
assert.equal(card.intervalDays, 1);
assert.equal(card.easeFactor, 2.6);
card = apply(card, 'easy');
assert.equal(card.intervalDays, 6);
card = apply(card, 'easy');
assert.equal(card.intervalDays, 17); // 6 days x ease 2.8, rounded
assert.equal(card.easeFactor, 2.8);

const failed = apply(card, 'hard');
assert.equal(failed.repetitions, 0);
assert.equal(failed.nextReview, '2026-01-01T00:10:00.000Z');
assert.ok(failed.easeFactor < card.easeFactor);
assert.equal(apply(failed, 'medium').intervalDays, 1);

let low = {};
for (let i = 0; i < 20; i++) low = apply(low, 'hard');
assert.equal(low.easeFactor, 1.3);

assert.equal(isDue({}, now), true);
assert.equal(isDue({ nextReview: 'garbage' }, now), true);
assert.equal(isDue({ nextReview: '2026-01-01T00:00:01Z' }, now), false);
console.log('srs OK');
