import express from 'express';
import cors from 'cors';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import dotenv from 'dotenv';
import { randomUUID } from 'crypto';
import { fileURLToPath } from 'url';

import { processFileAndGenerate, generateStudyPackFromText } from './services/aiEngine.js';
import { aiRouter } from './services/ai/AIRouter.js';
import { PromptManager } from './services/ai/PromptManager.js';
import { aiLogger } from './services/ai/AILogger.js';
import { extractFromVideoUrlOrFile, extractFromWebUrl } from './services/textExtractor.js';
import { supabaseService } from './services/supabaseService.js';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Storage setup for uploads (Use /tmp on Vercel Serverless)
const uploadDir = process.env.VERCEL ? path.join('/tmp', 'uploads') : path.join(__dirname, 'uploads');
try {
  if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
  }
} catch (e) {
  console.warn("Upload dir creation warning:", e.message);
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, uniqueSuffix + '-' + file.originalname);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 50 * 1024 * 1024 }
});

// ==================== REST API ENDPOINTS ==================== //

// Healthcheck (public)
app.get('/api/v1/health', (req, res) => {
  res.json({
    status: 'online',
    aiEngines: {
      providerMode: process.env.AI_PROVIDER || 'auto',
      geminiMultimodal: process.env.GEMINI_API_KEY ? 'Active (Connected)' : 'Fallback Mode (No Key)',
      groqFastReasoning: process.env.GROQ_API_KEY ? 'Active (Connected)' : 'Fallback Mode (No Key)'
    }
  });
});

// Serve uploaded avatars statically (public)
app.use('/uploads', express.static(uploadDir));

// Every other API route requires a signed-in Supabase user. req.db acts as that user,
// so Row Level Security limits every read and write to their own lessons.
app.use('/api', async (req, res, next) => {
  const token = req.headers.authorization?.replace(/^Bearer\s+/i, '');
  const user = token ? await supabaseService.getUserFromToken(token) : null;
  if (!user) return res.status(401).json({ success: false, error: 'Vui lòng đăng nhập để tiếp tục.' });
  req.user = user;
  req.db = supabaseService.clientFor(token);
  next();
});

const newLessonId = () => `doc-${randomUUID()}`;

// The caller's lesson, or a 404 response (RLS already hides other users' lessons)
async function loadLesson(req, res, id = req.params.id) {
  const lesson = await supabaseService.getLessonHistoryById(id, req.db);
  if (!lesson) res.status(404).json({ success: false, error: 'Không tìm thấy bài học.' });
  return lesson;
}

const saveLesson = (req, lesson) => supabaseService.saveLessonHistory(lesson, lesson.studyPack, req.db);

// Knowledge base of a lesson; re-analyzes the raw text for lessons saved without one
async function knowledgeOf(lesson) {
  return lesson.studyPack.knowledgeBase
    || (lesson.studyPack.knowledgeBase = await aiRouter.analyzeDocument(lesson.rawText, { title: lesson.title }));
}

const toDocumentItem = (l) => ({
  id: l.id,
  title: l.title,
  fileType: l.fileType,
  fileSize: l.fileSize,
  pageCount: l.pageCount,
  updatedAt: l.updatedAt,
  status: l.status,
  tags: l.tags
});

// Upload Avatar File Endpoint (the URL is stored in the user's Supabase profile by the client)
app.post('/api/v1/user/upload-avatar', upload.single('avatar'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ success: false, error: "Không tìm thấy tệp ảnh tải lên." });
  }
  res.json({ success: true, avatarUrl: `/uploads/${req.file.filename}` });
});

app.get('/api/v1/user/stats', async (req, res) => {
  const docList = (await supabaseService.getAllLessonHistory(req.db)).map(toDocumentItem);
  res.json({
    success: true,
    data: {
      totalDocuments: docList.length,
      weeklyDocAdded: Math.min(docList.length, 3),
      flashcardProgress: "152/240",
      retentionRatePercentage: 78,
      averageQuizScore: "8.5/10",
      quizScoreDiff: "+0.4 điểm so với tháng trước",
      weeklyHours: { current: 0, target: 5 },
      weeklyQuizCount: { current: 0, target: 50 },
      recentDocuments: docList,
      spacedRepetitionItems: [],
      recentActivities: []
    }
  });
});

app.get('/api/v1/documents', async (req, res) => {
  const lessons = await supabaseService.getAllLessonHistory(req.db);
  res.json({ success: true, documents: lessons.map(toDocumentItem) });
});

app.get('/api/v1/documents/:id', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (lesson) res.json({ success: true, document: { ...toDocumentItem(lesson), rawText: lesson.rawText }, studyPack: lesson.studyPack, quizHistory: lesson.quizHistory });
});

// Direct AI Endpoints
app.post('/api/ai/analyze', async (req, res) => {
  const { content, title = "Tài liệu học tập" } = req.body;
  const knowledgeBase = await aiRouter.analyzeDocument(content, { title });
  res.json({ success: true, knowledgeBase });
});

app.post('/api/ai/notes', async (req, res) => {
  const { knowledgeBase } = req.body;
  const notes = await aiRouter.generateNotes(knowledgeBase);
  res.json({ success: true, notes });
});

app.post('/api/ai/mindmap', async (req, res) => {
  const { knowledgeBase } = req.body;
  const mindmap = await aiRouter.generateMindmap(knowledgeBase);
  res.json({ success: true, mindmap });
});

app.post('/api/ai/flashcards', async (req, res) => {
  const { knowledgeBase, userSettings = {} } = req.body;
  const flashcards = await aiRouter.generateFlashcards(knowledgeBase, userSettings);
  res.json({ success: true, flashcards });
});

app.post('/api/ai/quiz', async (req, res) => {
  const { knowledgeBase, userSettings = {} } = req.body;
  const quiz = await aiRouter.generateQuiz(knowledgeBase, userSettings);
  res.json({ success: true, quiz });
});

app.post('/api/v1/documents/:id/regenerate-quiz', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  lesson.studyPack.quiz = await aiRouter.generateQuiz(await knowledgeOf(lesson), req.body?.userSettings || {});
  await saveLesson(req, lesson);
  res.json({ success: true, quiz: lesson.studyPack.quiz });
});

app.post('/api/v1/documents/:id/regenerate-flashcards', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  lesson.studyPack.flashcards = await aiRouter.generateFlashcards(await knowledgeOf(lesson), req.body?.userSettings || {});
  await saveLesson(req, lesson);
  res.json({ success: true, flashcards: lesson.studyPack.flashcards });
});

// Prerequisite analysis ("Kiến thức Tiên quyết") on demand — for older lessons or to re-run at another depth
app.post('/api/v1/documents/:id/prerequisites', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const kb = lesson.studyPack.knowledgeBase;
  lesson.studyPack.prerequisites = await aiRouter.generatePrerequisites(
    lesson.title, lesson.rawText || kb?.summary || '', req.body?.depth || kb?.analysisDepth, kb
  );
  await saveLesson(req, lesson);
  res.json({ success: true, prerequisites: lesson.studyPack.prerequisites });
});

// Diagnostic pre-test is graded server-side and kept on the lesson as Knowledge Gap Map evidence
app.post('/api/v1/documents/:id/prerequisites/diagnostic', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const prerequisites = lesson.studyPack.prerequisites;
  const questions = prerequisites?.diagnosticPreTest?.questions || [];
  if (questions.length === 0) return res.status(400).json({ success: false, error: 'Bài học chưa có bài test chẩn đoán.' });

  const answers = req.body?.answers || {};
  prerequisites.lastDiagnostic = {
    completedAt: new Date().toISOString(),
    results: questions
      .filter(q => answers[q.id] !== undefined)
      .map(q => ({ questionId: q.id, prerequisiteId: q.testedPrerequisiteId, correct: answers[q.id] === q.correctIndex }))
  };
  await saveLesson(req, lesson);
  res.json({ success: true, prerequisites });
});

app.post('/api/ai/chat', async (req, res) => {
  const { documentId, question, chatHistory = [], mode, focus } = req.body;
  const lesson = await loadLesson(req, res, documentId);
  if (!lesson) return;
  // Socratic mode: tutor never gives answers; focus = root gaps summarised by the client's Knowledge Gap Map
  const instructions = mode === 'socratic'
    ? PromptManager.getSocraticInstructions(lesson.title, String(focus || '').slice(0, 1200))
    : null;
  const reply = await aiRouter.chat(lesson.title, lesson.rawText || '', question, chatHistory, instructions);
  res.json({ success: true, answer: reply });
});

// Upload Document
app.post('/api/v1/documents/upload', upload.single('file'), async (req, res) => {
  try {
    const file = req.file;
    const { options, rawText, fileName, depth } = req.body;
    const parsedOptions = { ...(options ? JSON.parse(options) : {}), depth };

    const docTitle = file
      ? file.originalname.replace(/\.[^/.]+$/, "")
      : (fileName
          ? fileName.replace(/\.[^/.]+$/, "")
          : (rawText ? (rawText.trim().slice(0, 30) + '...') : "Tài liệu mới"));

    let result;
    if (file) {
      result = await processFileAndGenerate(file.path, file.originalname, file.mimetype, parsedOptions);
    } else {
      const defaultText = rawText && rawText.trim() ? rawText.trim() : "Tài liệu học tập tổng hợp từ người dùng.";
      result = {
        extractedText: defaultText,
        studyPack: await generateStudyPackFromText(defaultText, docTitle, depth)
      };
    }

    const extFromFileName = fileName ? fileName.split('.').pop().toUpperCase() : 'DOCX';
    const newDoc = {
      id: newLessonId(),
      title: docTitle,
      fileType: file ? file.originalname.split('.').pop().toUpperCase() : extFromFileName,
      fileSize: file ? `${(file.size / (1024 * 1024)).toFixed(1)} MB` : '1.5 MB',
      pageCount: 1,
      updatedAt: 'Vừa xong',
      status: 'COMPLETED',
      tags: ['AI Analysis Engine'],
      rawText: result.extractedText
    };

    await supabaseService.saveLessonHistory(newDoc, result.studyPack, req.db);

    res.json({
      success: true,
      message: "Chuyển hóa tài liệu thành công!",
      document: newDoc,
      studyPack: result.studyPack
    });
  } catch (err) {
    console.error("Upload error:", err);
    res.status(500).json({ success: false, error: err?.message || "Lỗi xử lý tài liệu" });
  } finally {
    // Uploaded binaries are only needed during processing; /tmp on Vercel is small
    if (req.file?.path) fs.promises.unlink(req.file.path).catch(() => {});
  }
});

// Process Video
app.post('/api/v1/documents/process-video', async (req, res) => {
  const { videoUrl } = req.body;
  const extracted = await extractFromVideoUrlOrFile(videoUrl);
  const rawText = typeof extracted === 'object' ? extracted.text : extracted;
  const docTitle = typeof extracted === 'object' ? extracted.title : (videoUrl ? `Video (${videoUrl.slice(0, 25)}...)` : "Video học tập");

  const studyPack = await generateStudyPackFromText(rawText, docTitle, req.body.depth);
  const newDoc = {
    id: newLessonId(),
    title: docTitle,
    fileType: 'VIDEO',
    duration: 'Phân tích tự động',
    updatedAt: 'Vừa xong',
    status: 'COMPLETED',
    tags: ['YouTube Speech-to-Text'],
    rawText: rawText
  };

  await supabaseService.saveLessonHistory(newDoc, studyPack, req.db);
  res.json({ success: true, document: newDoc, studyPack });
});

// Process Web URL
app.post('/api/v1/documents/process-url', async (req, res) => {
  const { url } = req.body;
  const extracted = await extractFromWebUrl(url);
  const rawText = typeof extracted === 'object' ? extracted.text : extracted;
  const docTitle = typeof extracted === 'object' ? extracted.title : (url ? `Web (${url.slice(0, 25)}...)` : "Nghiên cứu Web");

  const studyPack = await generateStudyPackFromText(rawText, docTitle, req.body.depth);
  const newDoc = {
    id: newLessonId(),
    title: docTitle,
    fileType: 'URL',
    updatedAt: 'Vừa xong',
    status: 'COMPLETED',
    tags: ['Web Article Extractor'],
    rawText: rawText
  };

  await supabaseService.saveLessonHistory(newDoc, studyPack, req.db);
  res.json({ success: true, document: newDoc, studyPack });
});

// Admin AI Logs & Metrics Dashboard Endpoints
app.get('/api/ai/logs', (req, res) => {
  res.json({ success: true, logs: aiLogger.getLogs(50) });
});

app.get('/api/ai/stats', (req, res) => {
  res.json({ success: true, stats: aiLogger.getStats() });
});

// ==================== MINDMAP / FLASHCARD / QUIZ CRUD ==================== //

app.post('/api/v1/documents/:id/mindmap/expand', async (req, res) => {
  const { node } = req.body;
  if (!node) {
    return res.status(400).json({ success: false, error: "Missing node parameter" });
  }
  const lesson = await loadLesson(req, res);
  if (!lesson) return;

  const expansion = await aiRouter.expandNode(node, lesson.rawText || "");
  const mindmap = lesson.studyPack.mindmap ||= { rootLabel: lesson.title, nodes: [], edges: [] };
  (mindmap.nodes ||= []).push(...(expansion.expandedNodes || []));
  (mindmap.edges ||= []).push(...(expansion.expandedEdges || []));

  await saveLesson(req, lesson);
  res.json({ success: true, expansion, mindmap });
});

app.post('/api/v1/documents/:id/mindmap/nodes', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const { label, detail, subDetails = [], parentId } = req.body;

  const newNode = {
    id: `node-${Date.now()}`,
    label: label || 'Nút nhánh mới',
    detail: detail || 'Chi tiết nút mới tạo',
    subDetails: subDetails || [],
    parentId: parentId || 'root'
  };

  const mindmap = lesson.studyPack.mindmap ||= { rootLabel: 'NÚT GỐC TRUNG TÂM', nodes: [] };
  (mindmap.nodes ||= []).push(newNode);

  await saveLesson(req, lesson);
  res.json({ success: true, node: newNode, mindmap });
});

app.put('/api/v1/documents/:id/mindmap/nodes/:nodeId', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const { label, detail, subDetails } = req.body;

  const node = lesson.studyPack.mindmap?.nodes?.find(n => n.id === req.params.nodeId);
  if (node) {
    if (label !== undefined) node.label = label;
    if (detail !== undefined) node.detail = detail;
    if (subDetails !== undefined) node.subDetails = subDetails;
    await saveLesson(req, lesson);
  }

  res.json({ success: true, mindmap: lesson.studyPack.mindmap });
});

app.delete('/api/v1/documents/:id/mindmap/nodes/:nodeId', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;

  const mindmap = lesson.studyPack.mindmap;
  if (mindmap?.nodes) {
    mindmap.nodes = mindmap.nodes.filter(n => n.id !== req.params.nodeId);
    await saveLesson(req, lesson);
  }

  res.json({ success: true, mindmap });
});

app.post('/api/v1/documents/:id/flashcards', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const { front, back, difficulty = 'medium' } = req.body;

  const newCard = {
    id: `fc-${Date.now()}`,
    front: front || 'Câu hỏi ghi nhớ mới?',
    back: back || 'Đáp án trả lời.',
    difficulty,
    lastReviewed: null,
    nextReview: new Date().toISOString()
  };

  (lesson.studyPack.flashcards ||= []).push(newCard);
  await saveLesson(req, lesson);
  res.json({ success: true, card: newCard, flashcards: lesson.studyPack.flashcards });
});

app.post('/api/v1/documents/:id/flashcards/:cardId/review', async (req, res) => {
  const intervals = { hard: '1 phút', medium: '10 phút', easy: '4 ngày' };
  const { rating } = req.body;
  if (!intervals[rating]) return res.status(400).json({ success: false, error: 'Đánh giá không hợp lệ.' });
  const lesson = await loadLesson(req, res);
  if (!lesson) return;

  const card = (lesson.studyPack.flashcards || []).find(c => c.id === req.params.cardId);
  if (!card) return res.status(404).json({ success: false, error: 'Không tìm thấy thẻ ghi nhớ.' });
  card.lastRating = rating;
  card.lastReviewed = new Date().toISOString();
  await saveLesson(req, lesson);

  res.json({
    success: true,
    card,
    message: `Đã cập nhật trạng thái thẻ ôn tập: ${rating.toUpperCase()} (Lần ôn tiếp theo: sau ${intervals[rating]})`
  });
});

app.delete('/api/v1/documents/:id/flashcards/:cardId', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  lesson.studyPack.flashcards = (lesson.studyPack.flashcards || []).filter(c => c.id !== req.params.cardId);
  await saveLesson(req, lesson);
  res.json({ success: true, message: "Đã xóa thẻ ghi nhớ thành công" });
});

app.post('/api/v1/documents/:id/quiz/questions', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const { questionText, options, correctIndex, explanation } = req.body;

  const quiz = lesson.studyPack.quiz?.questions
    ? lesson.studyPack.quiz
    : (lesson.studyPack.quiz = { title: "Đề trắc nghiệm AI", subject: "Tổng hợp", timeLimitMinutes: 15, questions: [] });

  const qNumber = quiz.questions.length + 1;
  const newQ = {
    id: `q-${Date.now()}`,
    questionNumber: qNumber,
    questionText: questionText || `Câu hỏi ${qNumber}?`,
    options: options || ["A. Phương án 1", "B. Phương án 2", "C. Phương án 3", "D. Phương án 4"],
    correctIndex: correctIndex !== undefined ? correctIndex : 0,
    explanation: explanation || "Giải thích chi tiết từ AI."
  };

  quiz.questions.push(newQ);
  await saveLesson(req, lesson);
  res.json({ success: true, question: newQ, quiz });
});

app.post('/api/v1/quiz/:id/submit', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (!lesson) return;
  const answers = req.body.answers || {};
  const questions = lesson.studyPack.quiz?.questions || [];

  // Per-question outcome is the evidence the Knowledge Gap Map traces back through the concept graph
  const results = questions
    .filter(q => answers[q.id] !== undefined)
    .map(q => ({ questionId: q.id, conceptId: q.conceptId || null, correct: answers[q.id] === q.correctIndex }));
  const correctCount = results.filter(r => r.correct).length;
  const total = Math.max(questions.length, 1);
  const scorePct = Math.round((correctCount / total) * 100);
  const feedback = scorePct >= 80 ? "Xuất sắc! Bạn đã nắm rất vững kiến thức bài học." : "Khá tốt! Hãy ôn lại các thẻ ghi nhớ màu đỏ nhé.";

  await supabaseService.recordQuizResult(lesson, scorePct, correctCount, total, feedback, results, req.db);

  res.json({
    success: true,
    score: scorePct,
    correctCount,
    totalQuestions: total,
    feedback
  });
});

// ==========================================
// 📚 LESSON HISTORY ENDPOINTS
// ==========================================

app.get('/api/v1/history', async (req, res) => {
  const historyList = await supabaseService.getAllLessonHistory(req.db);
  res.json({
    success: true,
    isSupabaseActive: supabaseService.isConfigured(),
    data: historyList
  });
});

app.get('/api/v1/history/:id', async (req, res) => {
  const lesson = await loadLesson(req, res);
  if (lesson) res.json({ success: true, data: lesson });
});

app.delete('/api/v1/history/:id', async (req, res) => {
  await supabaseService.deleteLessonHistory(req.params.id, req.db);
  res.json({ success: true, message: "Đã xóa bài học khỏi lịch sử thành công!" });
});

// ==========================================
// 🧬 MULTI-DOCUMENT KNOWLEDGE FUSION ENDPOINT
// ==========================================
app.post('/api/v1/fusion/analyze', async (req, res) => {
  const { documentIds } = req.body;
  const allHistory = Array.isArray(documentIds) ? await supabaseService.getAllLessonHistory(req.db) : [];
  const selectedDocs = allHistory.filter(h => documentIds.includes(h.id));
  if (selectedDocs.length < 2) {
    return res.status(400).json({
      success: false,
      error: "Vui lòng chọn ít nhất 2 tài liệu để thực hiện hợp nhất và so sánh."
    });
  }

  const fusionResult = await aiRouter.analyzeFusion(selectedDocs);
  res.json({ success: true, data: fusionResult });
});

// Errors thrown in any route (Express 5 forwards async errors here) come back as JSON instead of an HTML page
app.use((err, req, res, next) => {
  console.error(`[API Error] ${req.method} ${req.path}:`, err);
  res.status(err.status || 500).json({ success: false, error: err.message || 'Lỗi máy chủ' });
});

if (!process.env.VERCEL) {
  app.listen(PORT, () => {
    console.log(`====================================================`);
    console.log(`🚀 StudyMind AI Multi-Provider Engine running on port ${PORT}`);
    console.log(`🤖 Gemini Multimodal: Active for Long Docs, Vision & Structure`);
    console.log(`⚡ Groq LPU Engine: Active for Fast Reasoning & Chat`);
    console.log(`📡 Healthcheck: http://localhost:${PORT}/api/v1/health`);
    console.log(`====================================================`);
  });
}

export default app;
