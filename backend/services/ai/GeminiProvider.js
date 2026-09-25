import { GoogleGenerativeAI } from '@google/generative-ai';
import fs from 'fs';
import dotenv from 'dotenv';
import { AIProvider } from './AIProvider.js';
import { PromptManager, PROMPT_VERSIONS } from './PromptManager.js';
import { SchemaValidator } from './SchemaValidator.js';
import { aiLogger } from './AILogger.js';
import { parseAIJson } from './jsonRepair.js';
dotenv.config();

export class GeminiProvider extends AIProvider {
  constructor() {
    super('Gemini');
    const apiKey = process.env.GEMINI_API_KEY;
    this.genAI = apiKey && apiKey.trim() !== '' ? new GoogleGenerativeAI(apiKey) : null;
    // gemini-2.5-flash is closed to new API keys; tried in order, a 503 "high demand" falls through to the next
    this.models = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite"];
  }

  fileToGenerativePart(filePath, mimeType) {
    return {
      inlineData: {
        data: Buffer.from(fs.readFileSync(filePath)).toString("base64"),
        mimeType
      },
    };
  }

  async executeGeminiCall(promptText, filePart = null, jsonMode = true) {
    if (!this.genAI) throw new Error("GEMINI_API_KEY not configured or invalid.");

    let lastError = null;
    for (const modelName of this.models) {
      try {
        const model = this.genAI.getGenerativeModel({
          model: modelName,
          generationConfig: jsonMode ? { responseMimeType: 'application/json', maxOutputTokens: 16384 } : undefined
        });
        const parts = filePart ? [promptText, filePart] : [promptText];
        const result = await model.generateContent(parts);
        const response = await result.response;
        return { text: response.text(), modelName };
      } catch (err) {
        lastError = err;
        console.warn(`⚠️ [GeminiProvider ${modelName}] Attempt failed: ${err.message}`);
        if (err.message?.includes('401') || err.message?.includes('invalid authentication') || err.message?.includes('API_KEY')) {
          break;
        }
      }
    }
    throw new Error(`All Gemini models failed. Last error: ${lastError?.message}`);
  }

  async analyzeDocument(content, metadata = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getDocumentAnalysisPrompt(metadata.title || "Tài liệu học tập", content, metadata.depth);

    try {
      let filePart = null;
      if (metadata.filePath && metadata.mimeType) {
        filePart = this.fileToGenerativePart(metadata.filePath, metadata.mimeType);
      }

      const { text, modelName } = await this.executeGeminiCall(prompt, filePart);
      const rawObj = parseAIJson(text);

      const validation = SchemaValidator.validateKnowledgeJson(rawObj);
      aiLogger.log({
        task: 'document_analysis',
        provider: this.name,
        model: modelName,
        latencyMs: Date.now() - startTime,
        status: 'SUCCESS'
      });

      return validation.data;
    } catch (err) {
      aiLogger.log({
        task: 'document_analysis',
        provider: this.name,
        model: 'gemini-all',
        latencyMs: Date.now() - startTime,
        status: 'ERROR',
        error: err.message
      });
      throw err;
    }
  }

  async generateNotes(knowledgeJson) {
    const startTime = Date.now();
    const prompt = PromptManager.getNotesGenerationPrompt(knowledgeJson);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const rawObj = parseAIJson(text);
    const val = SchemaValidator.validateNotesJson(rawObj);
    aiLogger.log({ task: 'generate_notes', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async generateMindmap(knowledgeJson) {
    const startTime = Date.now();
    const prompt = PromptManager.getMindmapGenerationPrompt(knowledgeJson);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const rawObj = parseAIJson(text);
    const val = SchemaValidator.validateMindmapJson(rawObj);
    aiLogger.log({ task: 'generate_mindmap', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async generateFlashcards(knowledgeJson, userSettings = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getFlashcardGenerationPrompt(knowledgeJson, userSettings);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const rawObj = parseAIJson(text);
    const val = SchemaValidator.validateFlashcardsJson(rawObj);
    aiLogger.log({ task: 'generate_flashcards', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.cards;
  }

  async generateQuiz(knowledgeJson, userSettings = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getQuizGenerationPrompt(knowledgeJson, userSettings);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const rawObj = parseAIJson(text);
    const val = SchemaValidator.validateQuizJson(rawObj);
    aiLogger.log({ task: 'generate_quiz', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.quiz;
  }

  /**
   * Read an image or scanned PDF and return its text verbatim (used instead of OCR)
   */
  async transcribeFile(filePath, mimeType) {
    const startTime = Date.now();
    const prompt = `Chép lại NGUYÊN VĂN toàn bộ chữ có trong tệp này (ảnh chụp tài liệu / sách / bảng / PDF scan).
- Giữ đúng thứ tự đọc, tiêu đề, gạch đầu dòng và xuống dòng.
- Công thức viết dạng văn bản (vd: CH3COOC2H5, x^2 + 2x = 0); bảng viết dạng Markdown.
- Hình vẽ/sơ đồ không có chữ: mô tả ngắn trong ngoặc vuông, vd [Hình: sơ đồ cấu tạo tế bào].
- KHÔNG tóm tắt, KHÔNG bình luận, KHÔNG thêm nội dung không có trong tệp.
- Nếu tệp không có chữ nào đọc được, trả về đúng chuỗi: KHONG_CO_NOI_DUNG`;
    const { text, modelName } = await this.executeGeminiCall(prompt, this.fileToGenerativePart(filePath, mimeType), false);
    aiLogger.log({ task: 'transcribe_file', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    const clean = String(text || '').trim();
    return clean === 'KHONG_CO_NOI_DUNG' ? '' : clean;
  }

  async generatePrerequisites(title, content, depth) {
    const startTime = Date.now();
    const prompt = PromptManager.getPrerequisitePrompt(title, content.slice(0, 30000), depth);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const val = SchemaValidator.validatePrerequisiteJson(parseAIJson(text), title);
    if (!val.valid) throw new Error("Prerequisite analysis returned no prerequisites");
    aiLogger.log({ task: 'prerequisite_analysis', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async chat(docTitle, docContext, userQuestion, chatHistory = []) {
    const startTime = Date.now();
    const prompt = `[Tài liệu]: ${docTitle}\n[Ngữ cảnh]: ${docContext.slice(0, 4000)}\n[Lịch sử]: ${JSON.stringify(chatHistory.slice(-4))}\n[Câu hỏi]: ${userQuestion}\nHãy trả lời bằng Tiếng Việt súc tích, chính xác.`;
    const { text, modelName } = await this.executeGeminiCall(prompt, null, false);
    aiLogger.log({ task: 'chat', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return text;
  }

  async analyzeFusion(documents) {
    const startTime = Date.now();
    const prompt = PromptManager.getKnowledgeFusionPrompt(documents);
    const { text, modelName } = await this.executeGeminiCall(prompt);
    const rawObj = parseAIJson(text);
    aiLogger.log({ task: 'knowledge_fusion', provider: this.name, model: modelName, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return rawObj;
  }
}
