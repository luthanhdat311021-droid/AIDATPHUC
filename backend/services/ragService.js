// Semantic search over one lesson: split the text, embed it with Gemini, store vectors in pgvector.
// Lessons are indexed on their first question, so lessons created before this feature work too.
const EMBED_MODEL = 'gemini-embedding-001';
const EMBED_DIMENSIONS = 768; // must match the column in migration 011
const EMBED_BATCH = 100;
const CHUNK_CHARS = 1000;
const CHUNK_OVERLAP = 150;
const MAX_CHUNKS = 800;
const TOP_K = 6;
const MIN_SIMILARITY = 0.3;

/** Overlapping passages, cut at a line or sentence end when one is close enough. */
export function splitForRetrieval(text, size = CHUNK_CHARS, overlap = CHUNK_OVERLAP) {
  const clean = String(text || '').replace(/\r/g, '').trim();
  const chunks = [];
  let start = 0;
  while (start < clean.length && chunks.length < MAX_CHUNKS) {
    let end = Math.min(start + size, clean.length);
    if (end < clean.length) {
      const window = clean.slice(start + Math.floor(size * 0.6), end);
      const cut = Math.max(window.lastIndexOf('\n'), window.lastIndexOf('. '), window.lastIndexOf('? '), window.lastIndexOf('! '));
      if (cut >= 0) end = start + Math.floor(size * 0.6) + cut + 1;
    }
    const piece = clean.slice(start, end).trim();
    if (piece) chunks.push(piece);
    if (end >= clean.length) break;
    start = end - overlap;
  }
  return chunks;
}

async function embed(texts, taskType) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) throw new Error('GEMINI_API_KEY not configured');
  const vectors = [];
  for (let i = 0; i < texts.length; i += EMBED_BATCH) {
    const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${EMBED_MODEL}:batchEmbedContents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
      body: JSON.stringify({
        requests: texts.slice(i, i + EMBED_BATCH).map(text => ({
          model: `models/${EMBED_MODEL}`,
          content: { parts: [{ text }] },
          taskType,
          outputDimensionality: EMBED_DIMENSIONS
        }))
      })
    });
    if (!res.ok) throw new Error(`Embedding request failed with status ${res.status}`);
    vectors.push(...(await res.json()).embeddings.map(e => e.values));
  }
  return vectors;
}

const toPgVector = values => `[${values.join(',')}]`;

async function ensureIndexed(db, lesson, userId) {
  const { count, error } = await db.from('studymind_document_chunks')
    .select('id', { count: 'exact', head: true }).eq('lesson_id', lesson.id);
  if (error) throw error;
  if (count > 0) return;
  const chunks = splitForRetrieval(lesson.rawText);
  if (!chunks.length) return;
  const vectors = await embed(chunks, 'RETRIEVAL_DOCUMENT');
  const rows = chunks.map((content, i) => ({
    lesson_id: lesson.id, user_id: userId, chunk_index: i, content, embedding: toPgVector(vectors[i])
  }));
  // One insert, so a failure leaves nothing and the next question retries. A concurrent indexer loses harmlessly.
  const { error: saveError } = await db.from('studymind_document_chunks')
    .upsert(rows, { onConflict: 'lesson_id,chunk_index', ignoreDuplicates: true });
  if (saveError) throw saveError;
}

/** Passages most related to the query, best first. The caller's RLS client limits this to their own lessons. */
export async function retrievePassages(db, lesson, userId, query) {
  await ensureIndexed(db, lesson, userId);
  const [vector] = await embed([query], 'RETRIEVAL_QUERY');
  const { data, error } = await db.rpc('studymind_match_chunks', {
    p_lesson: lesson.id, p_embedding: toPgVector(vector), p_count: TOP_K
  });
  if (error) throw error;
  return (data || []).filter(r => r.similarity >= MIN_SIMILARITY).map((r, i) => ({ n: i + 1, content: r.content }));
}

export const formatPassages = passages => passages.map(p => `[${p.n}] ${p.content}`).join('\n\n');

/** Only the passages the answer actually cites. */
export function citedSources(answer, passages) {
  const cited = new Set([...String(answer).matchAll(/\[(\d+)\]/g)].map(m => Number(m[1])));
  return passages.filter(p => cited.has(p.n)).map(p => ({
    n: p.n,
    excerpt: p.content.length > 280 ? `${p.content.slice(0, 280).trim()}…` : p.content
  }));
}
