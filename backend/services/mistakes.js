const text = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');

/** AI output → only complete entries, bounded, as new unscheduled flashcards. */
export function normalizeMistakes(raw, now = Date.now()) {
  const list = (key, keep) => (Array.isArray(raw?.[key]) ? raw[key] : []).map(keep).filter(Boolean).slice(0, 10);
  return {
    mistakes: list('mistakes', m => {
      const out = { question: text(m?.question, 500), error: text(m?.error, 800), rootCause: text(m?.rootCause, 300), fix: text(m?.fix, 800) };
      return out.error && out.rootCause ? out : null;
    }),
    flashcards: list('flashcards', (c, i) => {
      const front = text(c?.front, 500);
      const back = text(c?.back, 1000);
      return front && back ? { id: `fc-mistake-${now}-${i}`, front, back, difficulty: 'hard', lastReviewed: null, nextReview: new Date(now).toISOString(), userCreated: true } : null;
    })
  };
}

// Self-check: node backend/services/mistakes.js
if (process.argv[1]?.endsWith('mistakes.js')) {
  const assert = await import('node:assert/strict');
  const r = normalizeMistakes({ mistakes: [{ error: 'e', rootCause: 'r' }, { error: 'only' }], flashcards: [{ front: 'f', back: 'b' }, { front: 'x' }, 'bad'] }, 0);
  assert.equal(r.mistakes.length, 1);
  assert.equal(r.flashcards.length, 1);
  assert.equal(r.flashcards[0].userCreated, true); // must not earn StudyCoin mission credit
  assert.deepEqual(normalizeMistakes(null), { mistakes: [], flashcards: [] });
  console.log('mistakes self-check passed');
}
