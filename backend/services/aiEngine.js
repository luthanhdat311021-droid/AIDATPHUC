import { aiRouter } from './ai/AIRouter.js';
import { parseDocumentContent } from './extractors/documentParser.js';
import { DocumentChunker } from './extractors/chunker.js';

/**
 * Main Enterprise AI Pipeline
 * Workflow: Input -> Extract -> Clean -> Chunk -> AI Router -> Knowledge Base JSON -> Derived Notes/Mindmap/Flashcards/Quiz
 */
export async function processFileAndGenerate(filePath, originalName, mimeType, options = {}, progressCallback = null) {
  console.log(`[AI Pipeline] Starting processing for: "${originalName}"`);
  
  if (progressCallback) progressCallback(10, 'Extracting content');
  
  // 1. Content Extraction
  const rawText = await parseDocumentContent(filePath, originalName, mimeType);

  if (progressCallback) progressCallback(30, 'Chunking & Analyzing');

  // 2. Chunking if document is long (> 8000 chars)
  const isVision = mimeType?.startsWith('image/') || ['.png', '.jpg', '.jpeg', '.webp'].some(ext => originalName.toLowerCase().endsWith(ext));
  const chunks = DocumentChunker.splitIntoChunks(rawText, 8000);

  // Prerequisite analysis only needs the raw text, so it runs alongside the main analysis
  const docTitle = originalName.replace(/\.[^/.]+$/, '');
  const prereqPromise = startPrerequisiteAnalysis(docTitle, rawText, options.depth);

  // 3. AI Analysis via AI Router
  let knowledgeJson = null;
  if (chunks.length === 1) {
    // Images/scanned PDFs were already transcribed to text by the parser, so only re-send the file when that text is thin
    const needsVision = isVision && rawText.trim().length < 200;
    knowledgeJson = await aiRouter.analyzeDocument(chunks[0], needsVision
      ? { title: originalName, isVision, filePath, mimeType, depth: options.depth }
      : { title: originalName, depth: options.depth });
  } else {
    const chunkResults = [];
    for (let i = 0; i < chunks.length; i++) {
      if (progressCallback) progressCallback(30 + Math.round((i / chunks.length) * 30), `Analyzing chunk ${i + 1}/${chunks.length}`);
      const res = await aiRouter.analyzeDocument(chunks[i], { title: `${originalName} (Part ${i + 1})`, depth: options.depth });
      chunkResults.push(res);
    }
    knowledgeJson = DocumentChunker.mergeKnowledgeBases(chunkResults, originalName);
  }

  if (progressCallback) progressCallback(70, 'Generating Derived Study Pack');

  // 4. Generate Derived AI Artifacts (Notes, Mindmap, Flashcards, Quiz) from Knowledge JSON
  const [notes, mindmap, flashcards, quiz, prerequisites] = await Promise.all([
    aiRouter.generateNotes(knowledgeJson),
    aiRouter.generateMindmap(knowledgeJson),
    aiRouter.generateFlashcards(knowledgeJson),
    aiRouter.generateQuiz(knowledgeJson),
    finishPrerequisiteAnalysis(prereqPromise, docTitle, knowledgeJson)
  ]);

  if (progressCallback) progressCallback(100, 'Completed');

  const studyPack = {
    knowledgeBase: knowledgeJson,
    notes,
    mindmap,
    flashcards,
    quiz,
    prerequisites
  };

  return {
    extractedText: rawText,
    studyPack
  };
}

/**
 * Study pack pipeline for already-extracted text (pasted text, video transcripts, web articles)
 */
export async function generateStudyPackFromText(text, title, depth) {
  const prereqPromise = startPrerequisiteAnalysis(title, text, depth);
  const knowledgeBase = await aiRouter.analyzeDocument(text, { title, depth });
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

/**
 * Handle AI Chat queries
 */
export async function answerStudyQuery(docTitle, docText, userQuestion, chatHistory = []) {
  return await aiRouter.chat(docTitle, docText, userQuestion, chatHistory);
}
