/**
 * Abstract AIProvider Interface
 * Enterprise abstraction for AI providers (Gemini, Groq, OpenAI, Anthropic, etc.)
 */
export class AIProvider {
  constructor(name) {
    this.name = name;
  }

  /**
   * Analyze raw document content and generate canonical Knowledge Base JSON
   */
  async analyzeDocument(content, metadata = {}) {
    throw new Error(`analyzeDocument() not implemented in provider ${this.name}`);
  }

  /**
   * Generate structured study notes from Knowledge Base JSON
   */
  async generateNotes(knowledgeJson) {
    throw new Error(`generateNotes() not implemented in provider ${this.name}`);
  }

  /**
   * Generate structured mindmap tree from Knowledge Base JSON
   */
  async generateMindmap(knowledgeJson) {
    throw new Error(`generateMindmap() not implemented in provider ${this.name}`);
  }

  /**
   * Generate spaced repetition flashcards from Knowledge Base JSON
   */
  async generateFlashcards(knowledgeJson) {
    throw new Error(`generateFlashcards() not implemented in provider ${this.name}`);
  }

  /**
   * Generate exam quiz questions from Knowledge Base JSON
   */
  async generateQuiz(knowledgeJson) {
    throw new Error(`generateQuiz() not implemented in provider ${this.name}`);
  }

  /**
   * Interactive study assistant chat over document context
   */
  async chat(docTitle, docContext, userQuestion, chatHistory = []) {
    throw new Error(`chat() not implemented in provider ${this.name}`);
  }
}
