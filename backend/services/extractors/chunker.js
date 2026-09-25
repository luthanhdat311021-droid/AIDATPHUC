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
      misconceptions: baseList.flatMap(b => b.misconceptions || []),
      prerequisites: Array.from(new Set(baseList.flatMap(b => b.prerequisites || []))),
      sources: baseList.flatMap(b => b.sources || []),
      analysisDepth: baseList[0].analysisDepth
    };

    const seenConcepts = new Set();
    baseList.forEach(b => {
      (b.concepts || []).forEach(c => {
        if (!seenConcepts.has(c.name)) {
          seenConcepts.add(c.name);
          merged.concepts.push(c);
        }
      });
      (b.sections || []).forEach(s => merged.sections.push(s));
      (b.relationships || []).forEach(r => merged.relationships.push(r));
    });

    return merged;
  }
}
