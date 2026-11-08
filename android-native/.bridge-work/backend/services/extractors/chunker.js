/**
 * Long Document Chunker & Knowledge Synthesizer
 * Splits large documents into manageable sections, analyzes chunks independently, and synthesizes into a single Knowledge Base JSON.
 */
export class DocumentChunker {
  static splitIntoChunks(text, chunkSize = 6000) {
    if (!text || text.length <= chunkSize) {
      return [text];
    }

    const chunks = [];
    let start = 0;

    while (start < text.length) {
      let end = start + chunkSize;
      if (end < text.length) {
        // Try to break at newline or paragraph boundary
        const lastNewline = text.lastIndexOf('\n', end);
        if (lastNewline > start + chunkSize * 0.7) {
          end = lastNewline;
        }
      }
      chunks.push(text.slice(start, end).trim());
      start = end;
    }

    console.log(`[Document Chunker] Split document (${text.length} chars) into ${chunks.length} chunks.`);
    return chunks;
  }

  /**
   * Merge multiple chunk Knowledge JSONs into a unified Knowledge Base
   */
  static mergeKnowledgeBases(baseList, title) {
    if (!baseList || baseList.length === 0) return null;
    if (baseList.length === 1) return baseList[0];

    const merged = {
      title,
      language: baseList[0].language || "Tiếng Việt",
      summary: baseList.map(b => b.summary).filter(Boolean).join(' '),
      difficulty: baseList[0].difficulty || "medium",
      topics: Array.from(new Set(baseList.flatMap(b => b.topics || []))),
      concepts: [],
      relationships: [],
      sections: [],
      keyTakeaways: Array.from(new Set(baseList.flatMap(b => b.keyTakeaways || []))),
      misconceptions: [],
      prerequisites: Array.from(new Set(baseList.flatMap(b => b.prerequisites || []))),
      sources: baseList.flatMap(b => b.sources || []),
      analysisDepth: baseList[0].analysisDepth
    };

    // Every chunk numbers its concepts from "concept-1", so ids are namespaced per chunk. A concept that
    // appears in several chunks keeps its first id, and each chunk's links are re-pointed through `remap`.
    const idByName = new Map();
    const seenLinks = new Set();
    baseList.forEach((b, chunkIdx) => {
      const remap = new Map();
      (b.concepts || []).forEach(c => {
        const key = String(c.name || '').trim().toLowerCase();
        if (!idByName.has(key)) {
          idByName.set(key, `c${chunkIdx + 1}-${c.id}`);
          merged.concepts.push({ ...c, id: idByName.get(key) });
        }
        remap.set(c.id, idByName.get(key));
      });
      const mapId = (id) => remap.get(id) ?? `c${chunkIdx + 1}-${id}`;

      (b.sections || []).forEach(s => merged.sections.push(s));
      (b.relationships || []).forEach(r => {
        const link = { ...r, source: mapId(r.source), target: mapId(r.target) };
        const key = `${link.source}|${link.target}|${link.type}`;
        // Merging duplicates can turn a link into a self-loop or repeat one another chunk already added
        if (link.source === link.target || seenLinks.has(key)) return;
        seenLinks.add(key);
        merged.relationships.push(link);
      });
      (b.misconceptions || []).forEach(m => merged.misconceptions.push(m.conceptId ? { ...m, conceptId: mapId(m.conceptId) } : m));
    });

    return merged;
  }
}

// Self-check: node backend/services/extractors/chunker.js
if (process.argv[1]?.endsWith('chunker.js')) {
  const assert = await import('node:assert/strict');
  const chunk = (names, rel, mis) => ({
    concepts: names.map((name, i) => ({ id: `concept-${i + 1}`, name })),
    relationships: rel.map(([source, target]) => ({ source, target, type: 'depends_on' })),
    misconceptions: mis.map(conceptId => ({ misconception: 'x', conceptId }))
  });
  const merged = DocumentChunker.mergeKnowledgeBases([
    chunk(['Ester', 'Acid'], [['concept-1', 'concept-2']], ['concept-1']),
    chunk(['Alcohol', 'Ester'], [['concept-2', 'concept-1'], ['concept-1', 'concept-1']], ['concept-2'])
  ], 'T');
  const id = (name) => merged.concepts.find(c => c.name === name).id;

  assert.deepEqual(merged.concepts.map(c => c.id), ['c1-concept-1', 'c1-concept-2', 'c2-concept-1']);
  assert.equal(new Set(merged.concepts.map(c => c.id)).size, merged.concepts.length, 'concept ids must be unique');
  // Chunk 2's "concept-2" is Ester, which must resolve to chunk 1's Ester, not chunk 1's concept-2 (Acid)
  assert.deepEqual(merged.relationships.map(r => [r.source, r.target]), [[id('Ester'), id('Acid')], [id('Ester'), id('Alcohol')]]);
  assert.deepEqual(merged.misconceptions.map(m => m.conceptId), [id('Ester'), id('Ester')]);
  console.log('chunker merge self-check passed');
}
