import { aiRouter } from './ai/AIRouter.js';
import { parseDocumentContent } from './extractors/documentParser.js';
import { DocumentChunker } from './extractors/chunker.js';

// ponytail: fixed pool of 3 concurrent chunk calls to stay near Groq's per-minute token limit; raise it on a paid plan
const CHUNK_CONCURRENCY = 3;

/**
 * Main Enterprise AI Pipeline for uploaded files
 * Workflow: Input -> Extract -> Chunk -> AI Router -> Knowledge Base JSON -> Derived Notes/Mindmap/Flashcards/Quiz
 */
export async function processFileAndGenerate(filePath, originalName, mimeType, options = {}) {
  console.log(`[AI Pipeline] Starting processing for: "${originalName}"`);
  const rawText = await parseDocumentContent(filePath, originalName, mimeType);

  // Images/scanned PDFs were already transcribed to text by the parser, so only re-send the file when that text is thin
  const isVision = mimeType?.startsWith('image/') || ['.png', '.jpg', '.jpeg', '.webp'].some(ext => originalName.toLowerCase().endsWith(ext));
  const vision = isVision && rawText.trim().length < 200 ? { isVision, filePath, mimeType } : null;

  const studyPack = await generateStudyPackFromText(rawText, originalName.replace(/\.[^/.]+$/, ''), options.depth, vision);
  return { extractedText: rawText, studyPack };
}

/**
 * Knowledge base for text of any length: long text is split into ~8k-char chunks (each provider call only
 * reads ~10k chars), analyzed CHUNK_CONCURRENCY at a time, then merged.
 */
async function analyzeText(text, title, depth) {
  const chunks = DocumentChunker.splitIntoChunks(text, 8000);
  if (chunks.length === 1) return aiRouter.analyzeDocument(text, { title, depth });

  const results = new Array(chunks.length);
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(CHUNK_CONCURRENCY, chunks.length) }, async () => {
    while (next < chunks.length) {
      const i = next++;
      results[i] = await aiRouter.analyzeDocument(chunks[i], { title: `${title} (Part ${i + 1})`, depth });
    }
  }));
  return DocumentChunker.mergeKnowledgeBases(results, title);
}

/**
 * Study pack pipeline for text (pasted or browser-extracted documents, video transcripts, web articles, uploaded files)
 */
export async function generateStudyPackFromText(text, title, depth, vision = null) {
  // Prerequisite analysis only needs the raw text, so it runs alongside the main analysis
  const prereqPromise = startPrerequisiteAnalysis(title, text, depth);
  const knowledgeBase = vision
    ? await aiRouter.analyzeDocument(text, { title, depth, ...vision })
    : await analyzeText(text, title, depth);
  const [notes, mindmap, flashcards, quiz, prerequisites] = await Promise.all([
    aiRouter.generateNotes(knowledgeBase),
    aiRouter.generateMindmap(knowledgeBase),
    aiRouter.generateFlashcards(knowledgeBase),
    aiRouter.generateQuiz(knowledgeBase),
    finishPrerequisiteAnalysis(prereqPromise, title, knowledgeBase)
  ]);
  return { knowledgeBase, notes, mindmap, flashcards, quiz, prerequisites };
}

/**
 * Start the prerequisite analysis early (in parallel with the main analysis).
 * Returns null for near-empty text (e.g. images), which is then analyzed from the knowledge base instead.
 */
export function startPrerequisiteAnalysis(title, rawText, depth) {
  if (!rawText || rawText.trim().length < 50) return Promise.resolve(null);
  return aiRouter.generatePrerequisites(title, rawText, depth).catch(err => {
    console.warn("[AI Pipeline] Prerequisite analysis failed:", err.message);
    return null;
  });
}

/**
 * Await the early prerequisite analysis; when it was skipped or fell back, use the knowledge base.
 */
export async function finishPrerequisiteAnalysis(prereqPromise, title, knowledgeJson) {
  const result = await prereqPromise;
  if (result && !result.isFallback) return result;
  if (!result && knowledgeJson?.summary) {
    return aiRouter.generatePrerequisites(title, knowledgeJson.summary, knowledgeJson.analysisDepth, knowledgeJson);
  }
  return aiRouter.generateFallbackPrerequisites(title, knowledgeJson, knowledgeJson?.analysisDepth || 'standard');
}
