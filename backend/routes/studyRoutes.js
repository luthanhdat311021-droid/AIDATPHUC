// Every signed-in StudyMind API route, shared by the Node/Vercel server (backend/server.js) and the
// Supabase Edge Function "studymind-api" (supabase/functions/studymind-api). Only runtime-neutral code
// lives here: no multer, no local upload folder. Files reach the step-by-step lesson pipeline through
// Supabase Storage, so their size is not bound by the host's request-body limit.
import fs from 'fs';
import os from 'os';
import path from 'path';
import { randomUUID } from 'crypto';

import { startPrerequisiteAnalysis, finishPrerequisiteAnalysis } from '../services/aiEngine.js';
import { aiRouter } from '../services/ai/AIRouter.js';
import { PromptManager } from '../services/ai/PromptManager.js';
import { aiLogger } from '../services/ai/AILogger.js';
import { resolveDepth } from '../services/ai/DepthProfiles.js';
import { DocumentChunker } from '../services/extractors/chunker.js';
import { parseDocumentContent } from '../services/extractors/documentParser.js';
import { extractFromVideoUrlOrFile, extractFromWebUrl } from '../services/textExtractor.js';
import { supabaseService } from '../services/supabaseService.js';
import { billingService, HttpError } from '../services/billingService.js';

export const UPLOAD_BUCKET = 'study-uploads';
// Each provider call reads ~10k chars, so long text is analysed in ~8k-char parts
const CHUNK_SIZE = 8000;
// Parts analysed per /analyze call (3 at a time) — keeps one request well inside the host's time limit
const PARTS_PER_CALL = 6;
const PART_CONCURRENCY = 3;

export const newLessonId = () => `doc-${randomUUID()}`;

export const toDocumentItem = (l) => ({
  id: l.id,
  title: l.title,
  fileType: l.fileType,
  fileSize: l.fileSize,
  pageCount: l.pageCount,
  updatedAt: l.updatedAt,
  status: l.status,
  tags: l.tags
});

/** Run fn over items with at most `limit` in flight */
async function pool(items, limit, fn) {
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const i = next++;
      await fn(items[i]);
    }
  }));
}

/**
 * Registers the health check, the Supabase session gate and all signed-in routes on `app`
 * (an express app or router). Call before host-specific routes that also need req.user / req.db.
 */
export function registerStudyRoutes(app) {
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

  // SePay bank-transfer webhook (public, authenticated by SePay's API key header, not a user session).
  // A non-2xx answer makes SePay retry, so only answer 200 once the transfer is recorded.
  app.post('/api/sepay/webhook', async (req, res) => {
    if (!billingService.verifyWebhook(req.headers.authorization)) {
      return res.status(401).json({ success: false, error: 'Unauthorized' });
    }
    const { result } = await billingService.handleWebhook(req.body);
    res.json({ success: true, result });
  });

  // Every other API route requires a signed-in Supabase user. req.db acts as that user,
  // so Row Level Security limits every read and write to their own lessons and files.
  app.use('/api', async (req, res, next) => {
    const token = req.headers.authorization?.replace(/^Bearer\s+/i, '');
    const user = token ? await supabaseService.getUserFromToken(token) : null;
    if (!user) return res.status(401).json({ success: false, error: 'Vui lòng đăng nhập để tiếp tục.' });
    req.user = user;
    req.db = supabaseService.clientFor(token);
    // Role (user/admin) and plan (free/pro) come from the database, never from the request
    req.access = await billingService.getAccess(user.id, req.db);
    next();
  });

  // ---- Permissions: Pro features, admin-only routes, daily AI quota ----
  const requireAdmin = (req, res, next) => {
    if (!req.access?.isAdmin) return res.status(403).json({ success: false, error: 'Chỉ quản trị viên được truy cập.', code: 'FORBIDDEN' });
    next();
  };

  const assertPro = (req, feature) => {
    if (!req.access?.isPro) throw new HttpError(402, `${feature} là tính năng của StudyMind Pro.`, 'PRO_REQUIRED');
  };

  // "Chuyên sâu" (deep) analysis is the expensive mode, reserved for Pro
  const assertDepthAllowed = (req, depth) => {
    if (depth && resolveDepth(depth).key === 'deep') assertPro(req, 'Phân tích "Chuyên sâu"');
  };

  const useQuota = (req, kind = 'generate') => billingService.consumeQuota(req.user.id, req.access, kind);

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

  // Dashboard numbers, all derived from the caller's own lessons (a new account shows zeros / "no data yet")
  app.get('/api/v1/user/stats', async (req, res) => {
    const lessons = await supabaseService.getAllLessonHistory(req.db);
    const WEEK = 7 * 24 * 3600 * 1000;
    const now = Date.now();
    const attempts = lessons.flatMap(l => l.quizHistory || []);
    const cards = lessons.flatMap(l => l.studyPack?.flashcards || []);
    const reviewed = cards.filter(c => c.lastRating);
    const avg = (list) => (list.length ? list.reduce((s, a) => s + a.score, 0) / list.length : null);

    // Study streak: consecutive days (Vietnam time) with a quiz attempt or a new lesson, ending today or yesterday
    const dayOf = (iso) => new Date(new Date(iso).getTime() + 7 * 3600 * 1000).toISOString().slice(0, 10);
    const activeDays = new Set([...attempts.map(a => dayOf(a.completedAt)), ...lessons.map(l => dayOf(l.createdAt))]);
    const DAY = 24 * 3600 * 1000;
    let streakDays = 0;
    // Today may still be empty without breaking the streak, so start from yesterday in that case
    let t = activeDays.has(dayOf(new Date(now).toISOString())) ? now : now - DAY;
    while (activeDays.has(dayOf(new Date(t).toISOString()))) { streakDays++; t -= DAY; }

    const recent = attempts.filter(a => now - new Date(a.completedAt).getTime() < WEEK);
    const prior = attempts.filter(a => now - new Date(a.completedAt).getTime() >= WEEK);
    const avgNow = avg(attempts);
    const scoreDiff = avg(recent) !== null && avg(prior) !== null ? (avg(recent) - avg(prior)) / 10 : null;

    res.json({
      success: true,
      data: {
        totalDocuments: lessons.length,
        weeklyDocAdded: lessons.filter(l => now - new Date(l.createdAt).getTime() < WEEK).length,
        flashcardProgress: `${reviewed.length}/${cards.length}`,
        retentionRatePercentage: reviewed.length ? Math.round((reviewed.filter(c => c.lastRating === 'easy').length / reviewed.length) * 100) : null,
        averageQuizScore: avgNow === null ? null : `${(avgNow / 10).toFixed(1)}/10`,
        quizScoreDiff: scoreDiff === null ? null : `${scoreDiff >= 0 ? '+' : ''}${scoreDiff.toFixed(1)} điểm so với trước tuần này`,
        quizAttempts: attempts.length,
        weeklyQuizCount: { current: recent.reduce((s, a) => s + a.totalQuestions, 0), target: 50 },
        streakDays,
        recentDocuments: lessons.map(toDocumentItem),
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

  // ==================== STUDYMIND PRO / BILLING ==================== //

  app.get('/api/v1/billing/me', async (req, res) => {
    res.json({ success: true, data: await billingService.summary(req.user.id, req.access) });
  });

  app.post('/api/v1/billing/orders', async (req, res) => {
    const order = await billingService.createOrder(req.user.id, req.body?.planId);
    res.json({ success: true, order });
  });

  app.get('/api/v1/billing/orders', async (req, res) => {
    res.json({ success: true, orders: await billingService.listOrders(req.user.id) });
  });

  app.get('/api/v1/billing/orders/:id', async (req, res) => {
    const order = await billingService.getOrder(req.user.id, req.params.id);
    // Fresh access too, so the client flips to Pro the moment the webhook lands
    const access = order.status === 'paid' ? await billingService.getAccess(req.user.id, req.db) : req.access;
    res.json({ success: true, order, access });
  });

  app.post('/api/v1/billing/orders/:id/cancel', async (req, res) => {
    await billingService.cancelOrder(req.user.id, req.params.id);
    res.json({ success: true });
  });

  // ==================== ADMIN (role = admin) ==================== //

  app.get('/api/admin/overview', requireAdmin, async (req, res) => {
    res.json({ success: true, data: { ...(await billingService.adminOverview()), aiStats: aiLogger.getStats() } });
  });

  app.post('/api/admin/members', requireAdmin, async (req, res) => {
    const { email, days } = req.body || {};
    const member = await billingService.adminSetPro(email, days);
    res.json({ success: true, member });
  });

  // Admin AI Logs & Metrics (logs include other users' prompts → admins only)
  app.get('/api/ai/logs', requireAdmin, (req, res) => {
    res.json({ success: true, logs: aiLogger.getLogs(50) });
  });

  app.get('/api/ai/stats', requireAdmin, (req, res) => {
    res.json({ success: true, stats: aiLogger.getStats() });
  });

  // ==================== DIRECT AI ENDPOINTS ==================== //

  app.post('/api/ai/analyze', async (req, res) => {
    await useQuota(req);
    const { content, title = "Tài liệu học tập" } = req.body;
    const knowledgeBase = await aiRouter.analyzeDocument(content, { title });
    res.json({ success: true, knowledgeBase });
  });

  app.post('/api/ai/notes', async (req, res) => {
    await useQuota(req);
    const { knowledgeBase } = req.body;
    const notes = await aiRouter.generateNotes(knowledgeBase);
    res.json({ success: true, notes });
  });

  app.post('/api/ai/mindmap', async (req, res) => {
    await useQuota(req);
    const { knowledgeBase } = req.body;
    const mindmap = await aiRouter.generateMindmap(knowledgeBase);
    res.json({ success: true, mindmap });
  });

  app.post('/api/ai/flashcards', async (req, res) => {
    await useQuota(req);
    const { knowledgeBase, userSettings = {} } = req.body;
    const flashcards = await aiRouter.generateFlashcards(knowledgeBase, userSettings);
    res.json({ success: true, flashcards });
  });

  app.post('/api/ai/quiz', async (req, res) => {
    await useQuota(req);
    const { knowledgeBase, userSettings = {} } = req.body;
    const quiz = await aiRouter.generateQuiz(knowledgeBase, userSettings);
    res.json({ success: true, quiz });
  });

  app.post('/api/v1/documents/:id/regenerate-quiz', async (req, res) => {
    const lesson = await loadLesson(req, res);
    if (!lesson) return;
    await useQuota(req);
    lesson.studyPack.quiz = await aiRouter.generateQuiz(await knowledgeOf(lesson), req.body?.userSettings || {});
    await saveLesson(req, lesson);
    res.json({ success: true, quiz: lesson.studyPack.quiz });
  });

  app.post('/api/v1/documents/:id/regenerate-flashcards', async (req, res) => {
    const lesson = await loadLesson(req, res);
    if (!lesson) return;
    await useQuota(req);
    lesson.studyPack.flashcards = await aiRouter.generateFlashcards(await knowledgeOf(lesson), req.body?.userSettings || {});
    await saveLesson(req, lesson);
    res.json({ success: true, flashcards: lesson.studyPack.flashcards });
  });

  // Prerequisite analysis ("Kiến thức Tiên quyết") on demand — for older lessons or to re-run at another depth
  app.post('/api/v1/documents/:id/prerequisites', async (req, res) => {
    const lesson = await loadLesson(req, res);
    if (!lesson) return;
    const kb = lesson.studyPack.knowledgeBase;
    // A lesson analysed "deep" while on Pro re-runs at standard depth once the plan has lapsed
    const depth = req.body?.depth || (req.access.isPro ? kb?.analysisDepth : undefined);
    assertDepthAllowed(req, depth);
    await useQuota(req);
    lesson.studyPack.prerequisites = await aiRouter.generatePrerequisites(
      lesson.title, lesson.rawText || kb?.summary || '', depth, kb
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
    await useQuota(req, 'chat');
    // Socratic mode: tutor never gives answers; focus = root gaps summarised by the client's Knowledge Gap Map
    const instructions = mode === 'socratic'
      ? PromptManager.getSocraticInstructions(lesson.title, String(focus || '').slice(0, 1200))
      : null;
    const reply = await aiRouter.chat(lesson.title, lesson.rawText || '', question, chatHistory, instructions);
    res.json({ success: true, answer: reply });
  });

  // ==================== STEP-BY-STEP LESSON PIPELINE ==================== //
  // start (read the source) → analyze (parts of the text, PARTS_PER_CALL per call) → finish (study pack).
  // Each step is a short request, so long documents never hit a per-request time limit, and files come
  // from Supabase Storage, so they are not bound by the host's request-body limit either.

  app.post('/api/v1/lessons/start', async (req, res) => {
    const { source = 'text', storagePath, fileName, mimeType, rawText, url, depth } = req.body || {};
    assertDepthAllowed(req, depth);
    await useQuota(req);

    let text = '';
    let title = 'Tài liệu mới';
    let fileType = 'TEXT';
    let fileSize = null;
    let tags = ['AI Analysis Engine'];

    if (source === 'file') {
      // Only files inside the caller's own folder; Storage RLS enforces the same for req.db
      if (!storagePath || !String(storagePath).startsWith(`${req.user.id}/`)) {
        throw new HttpError(400, 'Tệp tải lên không hợp lệ.');
      }
      const { data: blob, error } = await req.db.storage.from(UPLOAD_BUCKET).download(storagePath);
      if (error || !blob) throw new HttpError(404, 'Không tìm thấy tệp đã tải lên, vui lòng tải lại.');
      const name = fileName || String(storagePath).split('/').pop();
      const bytes = Buffer.from(await blob.arrayBuffer());
      const tmp = path.join(os.tmpdir(), `${randomUUID()}-${name.replace(/[^\w.\-]/g, '_')}`);
      await fs.promises.writeFile(tmp, bytes);
      try {
        text = await parseDocumentContent(tmp, name, mimeType || blob.type);
      } finally {
        fs.promises.unlink(tmp).catch(() => {});
        // The extracted text is kept on the lesson; the original file is no longer needed
        req.db.storage.from(UPLOAD_BUCKET).remove([storagePath]).catch(() => {});
      }
      title = name.replace(/\.[^/.]+$/, '');
      fileType = (name.split('.').pop() || 'FILE').toUpperCase();
      fileSize = `${(bytes.length / (1024 * 1024)).toFixed(1)} MB`;
    } else if (source === 'video') {
      const extracted = await extractFromVideoUrlOrFile(url);
      text = typeof extracted === 'object' ? extracted.text : extracted;
      title = typeof extracted === 'object' ? extracted.title : `Video (${String(url).slice(0, 25)}...)`;
      fileType = 'VIDEO';
      tags = ['YouTube Speech-to-Text'];
    } else if (source === 'url') {
      const extracted = await extractFromWebUrl(url);
      text = typeof extracted === 'object' ? extracted.text : extracted;
      title = typeof extracted === 'object' ? extracted.title : `Web (${String(url).slice(0, 25)}...)`;
      fileType = 'URL';
      tags = ['Web Article Extractor'];
    } else {
      text = String(rawText || '').trim();
      if (!text) throw new HttpError(400, 'Vui lòng dán hoặc nhập nội dung văn bản!');
      title = fileName ? fileName.replace(/\.[^/.]+$/, '') : `${text.slice(0, 30)}...`;
      fileType = fileName ? (fileName.split('.').pop() || 'TEXT').toUpperCase() : 'TEXT';
    }

    const totalParts = DocumentChunker.splitIntoChunks(text, CHUNK_SIZE).length;
    const lesson = { id: newLessonId(), title, fileType, fileSize, pageCount: 1, status: 'PROCESSING', tags, rawText: text };
    await supabaseService.saveLessonHistory(lesson, {}, req.db);
    const depthKey = resolveDepth(depth).key;
    const { error } = await req.db.from('learning_sessions')
      .update({ processing: { depth: depthKey, title, totalParts, parts: Array(totalParts).fill(null) } })
      .eq('id', lesson.id);
    if (error) throw new HttpError(500, `Lỗi lưu tiến trình: ${error.message}`);

    res.json({ success: true, lessonId: lesson.id, title, totalParts, partsPerCall: PARTS_PER_CALL });
  });

  // Analyse the next parts that are still missing (or those from `from`), PARTS_PER_CALL at most
  app.post('/api/v1/lessons/:id/analyze', async (req, res) => {
    const { data: row, error } = await req.db.from('learning_sessions')
      .select('id,title,raw_text,processing').eq('id', req.params.id).maybeSingle();
    if (error) throw new HttpError(500, error.message);
    if (!row) return res.status(404).json({ success: false, error: 'Không tìm thấy bài học.' });
    const proc = row.processing;
    if (!proc) return res.json({ success: true, done: 0, totalParts: 0, finished: true });

    const chunks = DocumentChunker.splitIntoChunks(row.raw_text || '', CHUNK_SIZE);
    const parts = Array.isArray(proc.parts) && proc.parts.length === chunks.length ? proc.parts : Array(chunks.length).fill(null);
    const todo = parts.map((p, i) => (p ? -1 : i)).filter(i => i >= 0).slice(0, PARTS_PER_CALL);

    await pool(todo, PART_CONCURRENCY, async (i) => {
      parts[i] = chunks.length === 1
        ? await aiRouter.analyzeDocument(row.raw_text, { title: proc.title, depth: proc.depth })
        : await aiRouter.analyzeDocument(chunks[i], { title: `${proc.title} (Part ${i + 1})`, depth: proc.depth });
    });

    const { error: saveErr } = await req.db.from('learning_sessions')
      .update({ processing: { ...proc, totalParts: chunks.length, parts } }).eq('id', row.id);
    if (saveErr) throw new HttpError(500, `Lỗi lưu tiến trình: ${saveErr.message}`);
    const done = parts.filter(Boolean).length;
    res.json({ success: true, done, totalParts: chunks.length, remaining: chunks.length - done });
  });

  // Merge the analysed parts and build the study pack; the lesson becomes a normal, finished lesson
  app.post('/api/v1/lessons/:id/finish', async (req, res) => {
    const { data: row, error } = await req.db.from('learning_sessions')
      .select('id,processing').eq('id', req.params.id).maybeSingle();
    if (error) throw new HttpError(500, error.message);
    if (!row) return res.status(404).json({ success: false, error: 'Không tìm thấy bài học.' });
    const lesson = await supabaseService.getLessonHistoryById(row.id, req.db);
    if (!row.processing) {
      // Already finished (e.g. a retried request): return the lesson as it is
      return res.json({ success: true, document: { ...toDocumentItem(lesson), rawText: lesson.rawText }, studyPack: lesson.studyPack });
    }
    const proc = row.processing;
    const parts = proc.parts || [];
    if (!parts.length || parts.some(p => !p)) throw new HttpError(409, 'Bài học chưa được phân tích xong.');

    const knowledgeBase = DocumentChunker.mergeKnowledgeBases(parts, proc.title);
    const prereqPromise = startPrerequisiteAnalysis(proc.title, lesson.rawText, proc.depth);
    const [notes, mindmap, flashcards, quiz, prerequisites] = await Promise.all([
      aiRouter.generateNotes(knowledgeBase),
      aiRouter.generateMindmap(knowledgeBase),
      aiRouter.generateFlashcards(knowledgeBase),
      aiRouter.generateQuiz(knowledgeBase),
      finishPrerequisiteAnalysis(prereqPromise, proc.title, knowledgeBase)
    ]);
    const studyPack = { knowledgeBase, notes, mindmap, flashcards, quiz, prerequisites };
    lesson.status = 'COMPLETED';
    await supabaseService.saveLessonHistory(lesson, studyPack, req.db);
    const { error: doneErr } = await req.db.from('learning_sessions').update({ processing: null }).eq('id', row.id);
    if (doneErr) throw new HttpError(500, `Lỗi lưu bài học: ${doneErr.message}`);

    res.json({
      success: true,
      message: "Chuyển hóa tài liệu thành công!",
      document: { ...toDocumentItem(lesson), status: 'COMPLETED', rawText: lesson.rawText },
      studyPack
    });
  });

  // ==================== MINDMAP / FLASHCARD / QUIZ CRUD ==================== //

  app.post('/api/v1/documents/:id/mindmap/expand', async (req, res) => {
    const { node } = req.body;
    if (!node) {
      return res.status(400).json({ success: false, error: "Missing node parameter" });
    }
    const lesson = await loadLesson(req, res);
    if (!lesson) return;
    await useQuota(req);

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

  // ==================== LESSON HISTORY ==================== //

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

  // ==================== MULTI-DOCUMENT KNOWLEDGE FUSION ==================== //

  app.post('/api/v1/fusion/analyze', async (req, res) => {
    assertPro(req, 'Hợp nhất kiến thức đa tài liệu');
    const { documentIds } = req.body;
    const allHistory = Array.isArray(documentIds) ? await supabaseService.getAllLessonHistory(req.db) : [];
    const selectedDocs = allHistory.filter(h => documentIds.includes(h.id));
    if (selectedDocs.length < 2) {
      return res.status(400).json({
        success: false,
        error: "Vui lòng chọn ít nhất 2 tài liệu để thực hiện hợp nhất và so sánh."
      });
    }

    await useQuota(req);
    const fusionResult = await aiRouter.analyzeFusion(selectedDocs);
    res.json({ success: true, data: fusionResult });
  });

  return { assertDepthAllowed, useQuota };
}

/** Errors thrown in any route (Express 5 forwards async errors here) come back as JSON instead of an HTML page */
export function jsonErrorHandler(err, req, res, next) {
  console.error(`[API Error] ${req.method} ${req.path}:`, err);
  res.status(err.status || 500).json({ success: false, error: err.message || 'Lỗi máy chủ', code: err.code });
}
