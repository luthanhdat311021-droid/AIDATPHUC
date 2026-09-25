import Groq from 'groq-sdk';
import dotenv from 'dotenv';
import { AIProvider } from './AIProvider.js';
import { PromptManager } from './PromptManager.js';
import { SchemaValidator } from './SchemaValidator.js';
import { aiLogger } from './AILogger.js';
import { resolveDepth } from './DepthProfiles.js';
import { parseAIJson } from './jsonRepair.js';
dotenv.config();

export class GroqProvider extends AIProvider {
  constructor() {
    super('Groq');
    const apiKey = process.env.GROQ_API_KEY;
    this.groq = apiKey && apiKey.trim() !== '' ? new Groq({ apiKey }) : null;
    this.models = [
      'llama-3.3-70b-versatile',
      'llama-3.1-8b-instant',
      'mixtral-8x7b-32768',
      'gemma2-9b-it',
      'deepseek-r1-distill-llama-70b'
    ];
    this.model = this.models[0];
  }

  async executeGroqCall(promptText, jsonMode = true, maxRetries = 2, maxTokens = 4000) {
    if (!this.groq) throw new Error("GROQ_API_KEY not configured or invalid.");

    const messages = [
      { role: 'system', content: 'You are an expert education AI engine. You MUST respond with strict valid JSON without markdown formatting.' },
      { role: 'user', content: promptText }
    ];

    let lastError = null;
    for (const modelName of this.models) {
      for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          const options = {
            messages,
            model: modelName,
            temperature: 0.3,
            max_tokens: maxTokens
          };

          if (jsonMode) {
            options.response_format = { type: 'json_object' };
          }

          const completion = await this.groq.chat.completions.create(options);
          this.model = modelName;
          return completion.choices[0]?.message?.content || '';
        } catch (err) {
          lastError = err;
          console.warn(`⚠️ [GroqProvider ${modelName}] Attempt ${attempt}/${maxRetries} warning: ${err.message}`);
          if (err.message.includes('404') || err.message.includes('model_not_found') || err.message.includes('429') || err.message.includes('rate_limit') || err.message.includes('401') || err.message.includes('invalid_api_key')) {
            break;
          }
        }
      }
      if (lastError?.message?.includes('401') || lastError?.message?.includes('invalid_api_key')) {
        break;
      }
    }

    throw new Error(`Groq call failed after trying models. Last error: ${lastError?.message}`);
  }

  async analyzeDocument(content, metadata = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getDocumentAnalysisPrompt(metadata.title || "Tài liệu học tập", content.slice(0, 10000), metadata.depth);

    try {
      const rawText = await this.executeGroqCall(prompt, true, 2, resolveDepth(metadata.depth).maxTokens);
      const rawObj = parseAIJson(rawText);
      const val = SchemaValidator.validateKnowledgeJson(rawObj);

      aiLogger.log({
        task: 'document_analysis',
        provider: this.name,
        model: this.model,
        latencyMs: Date.now() - startTime,
        status: 'SUCCESS'
      });

      return val.data;
    } catch (err) {
      aiLogger.log({
        task: 'document_analysis',
        provider: this.name,
        model: this.model,
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
    const rawText = await this.executeGroqCall(prompt, true, 2, resolveDepth(knowledgeJson?.analysisDepth).maxTokens);
    const rawObj = parseAIJson(rawText);
    const val = SchemaValidator.validateNotesJson(rawObj);
    aiLogger.log({ task: 'generate_notes', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async generateMindmap(knowledgeJson) {
    const startTime = Date.now();
    const prompt = PromptManager.getMindmapGenerationPrompt(knowledgeJson);
    // No strict JSON mode here: Groq rejects a mindmap cut off at the token limit outright,
    // while plain mode returns the partial text that parseAIJson can still recover.
    const rawText = await this.executeGroqCall(prompt, false, 2, resolveDepth(knowledgeJson?.analysisDepth).maxTokens);
    const rawObj = parseAIJson(rawText);
    const val = SchemaValidator.validateMindmapJson(rawObj);
    aiLogger.log({ task: 'generate_mindmap', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async generateFlashcards(knowledgeJson, userSettings = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getFlashcardGenerationPrompt(knowledgeJson, userSettings);
    const rawText = await this.executeGroqCall(prompt, true, 2, resolveDepth(knowledgeJson?.analysisDepth).maxTokens);
    const rawObj = parseAIJson(rawText);
    const val = SchemaValidator.validateFlashcardsJson(rawObj);
    aiLogger.log({ task: 'generate_flashcards', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.cards;
  }

  async generateQuiz(knowledgeJson, userSettings = {}) {
    const startTime = Date.now();
    const prompt = PromptManager.getQuizGenerationPrompt(knowledgeJson, userSettings);
    const rawText = await this.executeGroqCall(prompt, true, 2, resolveDepth(knowledgeJson?.analysisDepth).maxTokens);
    const rawObj = parseAIJson(rawText);
    const val = SchemaValidator.validateQuizJson(rawObj);
    aiLogger.log({ task: 'generate_quiz', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.quiz;
  }

  async generatePrerequisites(title, content, depth) {
    const startTime = Date.now();
    const prompt = PromptManager.getPrerequisitePrompt(title, content.slice(0, 10000), depth);
    const rawText = await this.executeGroqCall(prompt, true, 2, resolveDepth(depth).maxTokens);
    const val = SchemaValidator.validatePrerequisiteJson(parseAIJson(rawText), title);
    if (!val.valid) throw new Error("Prerequisite analysis returned no prerequisites");
    aiLogger.log({ task: 'prerequisite_analysis', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return val.data;
  }

  async chat(docTitle, docContext, userQuestion, chatHistory = []) {
    const startTime = Date.now();
    const messages = [
      { role: 'system', content: `Bạn là trợ lý học tập StudyMind AI cho tài liệu "${docTitle}". Trả lời ngắn gọn, dễ hiểu bằng Tiếng Việt.` },
      ...chatHistory.slice(-6).map(m => ({ role: m.sender === 'user' ? 'user' : 'assistant', content: m.text })),
      { role: 'user', content: `[Ngữ cảnh]: ${docContext.slice(0, 4000)}\n\n[Câu hỏi]: ${userQuestion}` }
    ];

    try {
      const completion = await this.groq.chat.completions.create({
        messages,
        model: this.model,
        temperature: 0.5,
        max_tokens: 500
      });
      const reply = completion.choices[0]?.message?.content || "Không thể phản hồi.";
      aiLogger.log({ task: 'chat', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
      return reply;
    } catch (err) {
      console.warn(`[Groq Chat] Error: ${err.message}`);
      return `Dựa trên tài liệu "${docTitle}", câu hỏi "${userQuestion}" liên quan đến các kiến thức trọng tâm. Bạn có muốn tạo thêm bài tập luyện tập không?`;
    }
  }

  async analyzeFusion(documents) {
    const startTime = Date.now();
    const prompt = PromptManager.getKnowledgeFusionPrompt(documents);
    const rawText = await this.executeGroqCall(prompt, true);
    const rawObj = parseAIJson(rawText);
    aiLogger.log({ task: 'knowledge_fusion', provider: this.name, model: this.model, latencyMs: Date.now() - startTime, status: 'SUCCESS' });
    return rawObj;
  }
}
