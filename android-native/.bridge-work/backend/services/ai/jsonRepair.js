/**
 * Lenient JSON parser for LLM output.
 * Large outputs (e.g. a 100-node mindmap) are sometimes cut off at the token limit. Instead of
 * throwing away the whole answer, keep everything up to the last fully-closed object/array and
 * close the remaining open brackets.
 */
export function parseAIJson(text) {
  const clean = String(text || '').replace(/```json/gi, '').replace(/```/g, '').trim();
  try {
    return JSON.parse(clean);
  } catch (err) {
    const repaired = repairTruncatedJson(clean);
    if (repaired !== null) {
      console.warn(`⚠️ [JSON Repair] Recovered truncated AI output (${clean.length} chars).`);
      return repaired;
    }
    throw err;
  }
}

function repairTruncatedJson(text) {
  const start = text.search(/[{[]/);
  if (start === -1) return null;

  const stack = [];
  let inString = false;
  let escaped = false;
  // Positions right after a closed container, with the brackets still open at that point
  const cutPoints = [];

  for (let i = start; i < text.length; i++) {
    const ch = text[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    else if (ch === '{' || ch === '[') stack.push(ch);
    else if (ch === '}' || ch === ']') {
      stack.pop();
      if (stack.length === 0) return safeParse(text.slice(start, i + 1));
      cutPoints.push({ end: i + 1, open: stack.slice() });
    }
  }

  // Try the latest cut point first; fall back to earlier ones if that still does not parse
  for (let k = cutPoints.length - 1; k >= 0 && k >= cutPoints.length - 50; k--) {
    const { end, open } = cutPoints[k];
    const closers = open.slice().reverse().map(b => (b === '{' ? '}' : ']')).join('');
    const parsed = safeParse(text.slice(start, end) + closers);
    if (parsed !== null) return parsed;
  }
  return null;
}

function safeParse(s) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}
