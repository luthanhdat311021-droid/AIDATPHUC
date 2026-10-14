import express from 'express';
import cors from 'cors';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import dotenv from 'dotenv';
import { fileURLToPath } from 'url';

import { processFileAndGenerate, generateStudyPackFromText } from './services/aiEngine.js';
import { extractFromVideoUrlOrFile, extractFromWebUrl } from './services/textExtractor.js';
import { supabaseService } from './services/supabaseService.js';
import { registerStudyRoutes, jsonErrorHandler, newLessonId } from './routes/studyRoutes.js';

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

// Serve uploaded avatars statically (public)
app.use('/uploads', express.static(uploadDir));

// Health check, SePay webhook, sign-in gate and every route shared with the "studymind-api" Edge Function
const { assertDepthAllowed, useQuota } = registerStudyRoutes(app);

// ---- Routes below are only on this Node server (multipart uploads, one-shot processing for the web app) ----

// Upload Avatar File Endpoint (the URL is stored in the user's Supabase profile by the client)
app.post('/api/v1/user/upload-avatar', upload.single('avatar'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ success: false, error: "Không tìm thấy tệp ảnh tải lên." });
  }
  res.json({ success: true, avatarUrl: `/uploads/${req.file.filename}` });
});

// Upload Document
app.post('/api/v1/documents/upload', upload.single('file'), async (req, res) => {
  try {
    const file = req.file;
    const { options, rawText, fileName, depth } = req.body;
    assertDepthAllowed(req, depth);
    await useQuota(req);
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
    res.status(err?.status || 500).json({ success: false, error: err?.message || "Lỗi xử lý tài liệu", code: err?.code });
  } finally {
    // Uploaded binaries are only needed during processing; /tmp on Vercel is small
    if (req.file?.path) fs.promises.unlink(req.file.path).catch(() => {});
  }
});

// Process Video
app.post('/api/v1/documents/process-video', async (req, res) => {
  const { videoUrl } = req.body;
  assertDepthAllowed(req, req.body.depth);
  await useQuota(req);
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
  assertDepthAllowed(req, req.body.depth);
  await useQuota(req);
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

app.use(jsonErrorHandler);

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
