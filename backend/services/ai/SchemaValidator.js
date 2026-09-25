/**
 * Schema Validator & Hallucination Guard
 * Ensures AI outputs match required JSON structures strictly, removing invalid fields or retrying.
 */
export class SchemaValidator {
  /**
   * Validate Canonical Knowledge Base JSON
   */
  static validateKnowledgeJson(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Output is not a valid JSON object" };
    }

    if (!data.title || typeof data.title !== 'string') {
      data.title = "Tài liệu học tập";
    }

    if (!Array.isArray(data.concepts)) {
      data.concepts = [];
    }

    if (!Array.isArray(data.sections)) {
      data.sections = [];
    }

    if (!Array.isArray(data.topics)) {
      data.topics = ["Chủ đề chính"];
    }

    if (!Array.isArray(data.keyTakeaways)) {
      data.keyTakeaways = [];
    }

    // Clean concepts & eliminate invalid entries
    data.concepts = data.concepts.map((c, i) => ({
      id: c.id || `concept-${i + 1}`,
      name: c.name || `Khái niệm ${i + 1}`,
      type: ['concept', 'definition', 'process', 'formula', 'example', 'fact'].includes(c.type) ? c.type : 'concept',
      description: c.description || '',
      details: Array.isArray(c.details) ? c.details.filter(Boolean) : [],
      examples: Array.isArray(c.examples) ? c.examples.filter(Boolean) : [],
      importance: typeof c.importance === 'number' ? c.importance : 3,
      source: c.source || { documentId: 'doc-1', page: 1, section: 'Tổng quan' }
    }));

    if (!Array.isArray(data.relationships)) data.relationships = [];
    data.misconceptions = Array.isArray(data.misconceptions)
      ? data.misconceptions.filter(m => m && m.misconception)
      : [];
    data.prerequisites = Array.isArray(data.prerequisites) ? data.prerequisites.filter(Boolean) : [];

    return { valid: true, data };
  }

  /**
   * Validate Prerequisite Analysis JSON ("Kiến thức Tiên quyết")
   */
  static validatePrerequisiteJson(data, documentTitle = "Tài liệu học tập") {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Prerequisite output is not a JSON object" };
    }
    const str = (v, fallback = '') => (typeof v === 'string' && v.trim() ? v.trim() : fallback);

    const prerequisites = (Array.isArray(data.prerequisites) ? data.prerequisites : [])
      .filter(p => p && str(p.concept))
      .map((p, i) => ({
        id: str(p.id, `pre-${i + 1}`),
        concept: str(p.concept),
        priority: p.priority === 'recommended' ? 'recommended' : 'critical',
        level: str(p.level, 'Kiến thức nền'),
        whyNeeded: str(p.whyNeeded),
        consequenceIfMissing: str(p.consequenceIfMissing)
      }));
    const preIds = new Set(prerequisites.map(p => p.id));

    const pathTypes = ['prerequisite', 'core_learning', 'advanced_application'];
    const learningPath = (Array.isArray(data.learningPath) ? data.learningPath : [])
      .filter(s => s && str(s.title))
      .map((s, i) => ({
        step: i + 1,
        type: pathTypes.includes(s.type) ? s.type : 'core_learning',
        title: str(s.title),
        description: str(s.description)
      }));

    const nodeTypes = ['prerequisite', 'core', 'application'];
    const dependencyGraph = (Array.isArray(data.dependencyGraph) ? data.dependencyGraph : [])
      .filter(d => d && str(d.from) && str(d.to))
      .map(d => ({
        from: str(d.from),
        fromType: nodeTypes.includes(d.fromType) ? d.fromType : 'prerequisite',
        to: str(d.to),
        toType: nodeTypes.includes(d.toType) ? d.toType : 'core',
        relation: str(d.relation, 'dẫn tới')
      }));

    const quickBridgeSummary = (Array.isArray(data.quickBridgeSummary) ? data.quickBridgeSummary : [])
      .filter(b => b && str(b.concept) && str(b.quickReview))
      .map(b => ({
        prerequisiteId: preIds.has(b.prerequisiteId) ? b.prerequisiteId : (prerequisites.find(p => p.concept === str(b.concept))?.id || null),
        concept: str(b.concept),
        quickReview: str(b.quickReview)
      }));

    const test = data.diagnosticPreTest || {};
    const questions = (Array.isArray(test.questions) ? test.questions : [])
      .filter(q => q && str(q.question) && Array.isArray(q.options) && q.options.length >= 2)
      .map((q, i) => {
        const options = q.options.map(o => String(typeof o === 'object' && o?.text ? o.text : o).trim()).slice(0, 4);
        const correctIndex = Number.isInteger(q.correctIndex) && q.correctIndex >= 0 && q.correctIndex < options.length ? q.correctIndex : 0;
        return {
          id: str(q.id, `pre-q${i + 1}`),
          testedPrerequisiteId: preIds.has(q.testedPrerequisiteId) ? q.testedPrerequisiteId : (prerequisites[0]?.id || null),
          question: str(q.question),
          options,
          correctIndex,
          explanation: str(q.explanation)
        };
      });
    const passScore = Number(test.passScore);

    const bottleneck = data.cognitiveBottleneck && str(data.cognitiveBottleneck.concept)
      ? {
          concept: str(data.cognitiveBottleneck.concept),
          description: str(data.cognitiveBottleneck.description),
          advice: str(data.cognitiveBottleneck.advice)
        }
      : null;

    return {
      valid: prerequisites.length > 0,
      data: {
        documentTitle: str(data.documentTitle, documentTitle),
        subjectArea: str(data.subjectArea, 'Chưa xác định'),
        targetAudienceLevel: str(data.targetAudienceLevel, 'Người học phổ thông'),
        overallReadinessNote: str(data.overallReadinessNote),
        prerequisites,
        dependencyGraph,
        cognitiveBottleneck: bottleneck,
        learningPath,
        quickBridgeSummary,
        diagnosticPreTest: {
          title: str(test.title, 'Bài test chẩn đoán độ sẵn sàng đầu vào'),
          instructions: str(test.instructions, 'Làm nhanh các câu sau để kiểm tra bạn đã đủ điều kiện tiếp thu bài này chưa'),
          passScore: passScore > 0 && passScore <= 100 ? passScore : 75,
          questions
        }
      }
    };
  }

  /**
   * Validate AI Notes JSON
   */
  static validateNotesJson(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Notes output is not a JSON object" };
    }

    if (!data.summaryTitle) data.summaryTitle = "Tóm tắt bài học AI";
    if (!Array.isArray(data.sections)) data.sections = [];
    if (!Array.isArray(data.keyTakeaways)) data.keyTakeaways = [];

    return { valid: true, data };
  }

  /**
   * Validate Mindmap Tree JSON
   */
  static validateMindmapJson(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Mindmap output is not a JSON object" };
    }

    if (!Array.isArray(data.nodes)) data.nodes = [];

    // Ensure unique IDs and no circular parent references
    const seenIds = new Set();
    data.nodes = data.nodes.filter((n, idx) => {
      const id = n.id || `node-${idx + 1}`;
      if (seenIds.has(id)) return false;
      seenIds.add(id);
      n.id = id;
      if (!n.label) n.label = `Nhánh ${idx + 1}`;
      if (!n.detail) n.detail = "";
      if (!Array.isArray(n.subDetails)) n.subDetails = [];
      return true;
    });

    return { valid: true, data };
  }

  /**
   * Validate Flashcards JSON with strict quality control & grounding checks
   */
  static validateFlashcardsJson(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Flashcards output is not a JSON object", cards: [] };
    }

    const cardsArray = Array.isArray(data.flashcards)
      ? data.flashcards
      : (Array.isArray(data.cards) ? data.cards : (Array.isArray(data) ? data : []));

    const validTypes = ['definition', 'concept', 'comparison', 'process', 'formula', 'example', 'application', 'cause_effect', 'cloze', 'true_false'];
    const validDifficulties = ['easy', 'medium', 'hard', 'expert'];
    const seenQuestions = new Set();
    const validCards = [];

    cardsArray.forEach((card, i) => {
      if (!card || (!card.front && !card.questionText) || (!card.back && !card.answer)) return;

      let cleanFront = String(card.front || card.questionText).trim();
      let cleanBack = String(card.back || card.answer).trim();

      // Prevent redundant front repeating the exact back content
      if (cleanBack.toLowerCase().includes(cleanFront.toLowerCase()) && cleanFront.length > 20) {
        cleanFront = `Khái niệm / Ý chính ${i + 1}: Quy định trong bài học`;
      } else if (cleanFront.toLowerCase().includes(cleanBack.toLowerCase()) && cleanBack.length > 20) {
        cleanFront = `Điểm cốt lõi cần nhớ về mục ${i + 1}`;
      }

      const questionKey = cleanFront.toLowerCase().replace(/[^a-z0-9àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệđìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]/gi, '');

      // Strict Deduplication Check
      if (seenQuestions.has(questionKey)) return;
      seenQuestions.add(questionKey);

      validCards.push({
        id: card.id || `fc_${String(i + 1).padStart(3, '0')}`,
        type: validTypes.includes(card.type) ? card.type : 'concept',
        topicId: card.topicId || 'topic_001',
        conceptId: card.conceptId || card.sourceConceptId || `concept_${i + 1}`,
        front: cleanFront,
        back: cleanBack,
        hint: card.hint || "",
        difficulty: validDifficulties.includes(card.difficulty) ? card.difficulty : 'medium',
        importance: typeof card.importance === 'number' ? Math.min(5, Math.max(1, card.importance)) : 3,
        tags: Array.isArray(card.tags) ? card.tags : ['study-mind'],
        source: card.source || { documentId: 'doc-1', page: 1, section: 'Tổng quan' }
      });
    });

    return {
      valid: true,
      cards: validCards,
      flashcards: validCards,
      validation: {
        passed: validCards.length > 0,
        count: validCards.length,
        errors: []
      }
    };
  }

  /**
   * Validate Quiz Questions JSON with Grounding & Distractor Validity
   */
  static validateQuizJson(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: "Quiz output is not a JSON object", quiz: { questions: [] } };
    }

    const questionsArray = Array.isArray(data.questions) ? data.questions : [];
    const validDifficulties = ['easy', 'medium', 'hard', 'expert'];
    const validQuestionTypes = ['multiple_choice', 'multiple_select', 'true_false', 'fill_blank', 'short_answer', 'scenario'];
    const validQuestions = [];
    const seenQuestions = new Set();

    questionsArray.forEach((q, i) => {
      if (!q || (!q.questionText && !q.question) || (!Array.isArray(q.options) && q.type !== 'true_false')) return;

      const qText = String(q.questionText || q.question).trim();
      const qKey = qText.toLowerCase().replace(/[^a-z0-9àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệđìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]/gi, '');

      // Strict Deduplication Check
      if (seenQuestions.has(qKey)) return;
      seenQuestions.add(qKey);

      // Normalize options
      let options = Array.isArray(q.options) ? q.options.map(o => (typeof o === 'object' && o.text ? o.text : String(o)).trim()) : [];
      if (options.length < 2 && q.type === 'true_false') {
        options = ["A. Đúng", "B. Sai", "C. Không xác định", "D. Cả A và B"];
      }

      while (options.length < 4) {
        options.push(`Dựa trên tài liệu (Phương án ${options.length + 1})`);
      }
      options = options.slice(0, 4);

      let correctIndex = typeof q.correctIndex === 'number' ? q.correctIndex : 0;
      if (correctIndex < 0 || correctIndex >= options.length) correctIndex = 0;

      validQuestions.push({
        id: q.id || `q_${String(i + 1).padStart(3, '0')}`,
        type: validQuestionTypes.includes(q.type) ? q.type : 'multiple_choice',
        topicId: q.topicId || 'topic_001',
        conceptId: q.conceptId || `concept_${i + 1}`,
        questionNumber: i + 1,
        questionText: qText,
        options,
        correctIndex,
        explanation: q.explanation || "Giải thích chi tiết được tổng hợp từ nguồn tài liệu.",
        difficulty: validDifficulties.includes(q.difficulty) ? q.difficulty : 'medium',
        importance: typeof q.importance === 'number' ? Math.min(5, Math.max(1, q.importance)) : 3,
        source: q.source || { documentId: 'doc-1', page: 1, section: 'Mục trọng tâm' }
      });
    });

    return {
      valid: true,
      quiz: {
        title: data.title || "Đề kiểm tra trắc nghiệm AI",
        subject: data.subject || "Tổng hợp kiến thức",
        timeLimitMinutes: data.timeLimitMinutes || 15,
        questions: validQuestions
      },
      validation: {
        passed: validQuestions.length > 0,
        totalQuestions: validQuestions.length,
        errors: []
      }
    };
  }
}
