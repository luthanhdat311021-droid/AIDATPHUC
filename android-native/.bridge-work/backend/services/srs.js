// SM-2 spaced repetition, mapped onto the app's three rating buttons.
const QUALITY = { hard: 2, medium: 3, easy: 5 };
const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;
const RELEARN_MINUTES = 10;
const MIN_EASE = 1.3;
const DEFAULT_EASE = 2.5;

export const isValidRating = (rating) => Object.hasOwn(QUALITY, rating);

/** A card with no valid nextReview has never been scheduled, so it is due. */
export function isDue(card, now = Date.now()) {
  const due = Date.parse(card?.nextReview);
  return !Number.isFinite(due) || due <= now;
}

/** The card's new schedule fields. A failed card restarts its repetitions and comes back the same session. */
export function scheduleReview(card, rating, now = Date.now()) {
  const q = QUALITY[rating];
  const oldEase = Number.isFinite(card?.easeFactor) ? card.easeFactor : DEFAULT_EASE;
  const easeFactor = Math.max(MIN_EASE, Math.round((oldEase + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)) * 100) / 100);

  if (q < 3) {
    return { easeFactor, repetitions: 0, intervalDays: 0, nextReview: new Date(now + RELEARN_MINUTES * MINUTE).toISOString() };
  }
  const done = Number.isInteger(card?.repetitions) && card.repetitions > 0 ? card.repetitions : 0;
  const intervalDays = done === 0 ? 1 : done === 1 ? 6 : Math.round((card.intervalDays || 6) * easeFactor);
  return { easeFactor, repetitions: done + 1, intervalDays, nextReview: new Date(now + intervalDays * DAY).toISOString() };
}

export const describeInterval = ({ intervalDays }) => (intervalDays > 0 ? `${intervalDays} ngày` : `${RELEARN_MINUTES} phút`);
