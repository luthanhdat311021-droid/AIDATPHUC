/**
 * Analysis Depth Profiles
 * Maps the 3 UI depth levels ("Tóm lược nhanh" / "Tiêu chuẩn" / "Chuyên sâu") to concrete
 * prompt instructions, output quantities and token budgets used across the whole pipeline.
 */
export const DEPTH_PROFILES = {
  quick: {
    key: 'quick',
    label: 'Tóm lược nhanh',
    maxTokens: 3000,
    goal: `QUICK SUMMARY MODE — the learner wants the essential gist in about 5 minutes of reading.
Keep ONLY the core ideas: the main thesis, the key definitions and the few facts a learner absolutely must remember.
Skip minor details, secondary examples, edge cases and background history.
Every sentence must be short, dense and immediately useful.`,
    analysis: {
      concepts: '6-10',
      conceptDescription: '1-2 concise sentences: what it is and why it matters',
      sections: '2-4',
      keyPointsPerSection: '2-3',
      keyTakeaways: '3-5',
      relationships: '0-5 (only the most essential links)',
      misconceptions: '0-2 (only if the source explicitly warns about them)',
      prerequisites: '0-2'
    },
    notes: {
      sections: '2-4',
      itemsPerSection: '2-4',
      itemText: '1-2 short sentences, no filler',
      summary: '2-3 sentences',
      extra: 'Do NOT add warning/comparison sections unless the source makes them central.'
    },
    mindmap: {
      nodes: '12-25',
      maxDepth: 3,
      levels: `- Level 1: Major topics (3-5)
- Level 2: Key concepts / definitions under each topic
- Level 3 (optional): ONE critical fact, formula or example per concept`,
      crossLinks: '0-3',
      maxNodes: 30,
      defaultVisibleLevel: 3,
      enrichChildren: 0
    },
    flashcards: {
      count: 8,
      types: '"definition", "concept", "cloze", "fact-based true_false"',
      difficultyMix: 'easy 50%, medium 50%',
      cognitive: 'Remember & Understand (Bloom levels 1-2): recall definitions, key facts, core meaning.'
    },
    quiz: {
      count: 8,
      types: '"multiple_choice", "true_false"',
      difficultyMix: 'easy 50%, medium 50%',
      cognitive: 'Remember & Understand (Bloom levels 1-2): recognize correct definitions and key facts.',
      timeLimitMinutes: 10
    }
  },

  standard: {
    key: 'standard',
    label: 'Tiêu chuẩn',
    maxTokens: 5000,
    goal: `STANDARD MODE — the learner wants a solid, exam-ready understanding of the material.
Cover ALL main topics and every important concept. For each concept explain WHAT it is, and WHY / HOW it works,
plus one concrete example whenever the source provides one. Capture the logical flow between ideas.`,
    analysis: {
      concepts: '12-25',
      conceptDescription: '2-4 sentences: definition -> how/why it works -> an example or application from the source',
      sections: '3-7 (follow the source outline)',
      keyPointsPerSection: '3-6',
      keyTakeaways: '5-8',
      relationships: '5-15 (depends_on, causes, contrasts_with, example_of, prerequisite_of...)',
      misconceptions: '2-4',
      prerequisites: '1-4'
    },
    notes: {
      sections: '3-7 (follow the source outline)',
      itemsPerSection: '3-6',
      itemText: '2-4 sentences: explanation + reasoning + a concrete example from the source when available',
      summary: 'one solid paragraph (4-6 sentences) describing the big picture and how the parts connect',
      extra: 'Add a final section "LƯU Ý & LỖI THƯỜNG GẶP" if the knowledge base contains misconceptions.'
    },
    mindmap: {
      nodes: '30-55',
      maxDepth: 4,
      levels: `- Level 1: Major topics (chapters / main sections)
- Level 2: Subtopics
- Level 3: Key concepts & definitions
- Level 4: Details — formulas, process steps, examples, properties`,
      crossLinks: '3-8',
      maxNodes: 65,
      defaultVisibleLevel: 3,
      enrichChildren: 2
    },
    flashcards: {
      count: 12,
      types: '"definition", "concept", "process", "comparison", "cause_effect", "formula", "example", "cloze"',
      difficultyMix: 'easy 30%, medium 50%, hard 20%',
      cognitive: 'Remember, Understand & Apply (Bloom levels 1-3): mix recall with "why/how" and small application cards.'
    },
    quiz: {
      count: 12,
      types: '"multiple_choice", "true_false", "scenario"',
      difficultyMix: 'easy 30%, medium 50%, hard 20%',
      cognitive: 'Remember, Understand & Apply (Bloom levels 1-3): at least 1/3 of the questions must require applying a rule or reasoning, not just recalling.',
      timeLimitMinutes: 15
    }
  },

  deep: {
    key: 'deep',
    label: 'Chuyên sâu',
    maxTokens: 8000,
    goal: `DEEP / EXPERT MODE — the learner wants complete mastery of the material, as if preparing for an advanced exam.
Be EXHAUSTIVE: capture every topic, concept, formula, process step, condition, exception and example in the source.
For each concept go beyond the definition: explain the underlying mechanism (why/how), the conditions under which it holds,
exceptions and limits, how it relates to / differs from neighbouring concepts, typical mistakes, and how it is applied.
Reveal the hidden structure of the document: prerequisite chains, cause -> effect chains, and comparisons between similar ideas.`,
    analysis: {
      concepts: '20-40 (every meaningful concept, formula, process and rule)',
      conceptDescription: '3-6 sentences: precise definition -> mechanism (why/how) -> conditions/exceptions -> concrete example from the source',
      sections: 'one per section/sub-section of the source (do not merge distinct sections)',
      keyPointsPerSection: '4-8',
      keyTakeaways: '8-12',
      relationships: '15-40, using diverse types (depends_on, causes, produces, contrasts_with, type_of, prerequisite_of, used_for, example_of)',
      misconceptions: '4-8 (confusable terms, sign/unit errors, overgeneralizations, exceptions students forget)',
      prerequisites: '2-6'
    },
    notes: {
      sections: 'one per source section/sub-section, plus the 2 analytical sections below',
      itemsPerSection: '4-8',
      itemText: '3-6 sentences: mechanism, conditions, exceptions and a worked example / application from the source',
      summary: '2 paragraphs: (1) the big picture and the logical architecture of the topic, (2) how the key ideas depend on and reinforce each other',
      extra: `MUST add two extra sections at the end:
  - "SO SÁNH & LIÊN HỆ": items comparing easily-confused concepts and explaining cause -> effect chains.
  - "BẪY THƯỜNG GẶP & LƯU Ý": items describing common mistakes/misconceptions and how to avoid them.`
    },
    mindmap: {
      nodes: '55-100',
      maxDepth: 6,
      levels: `- Level 1: Major topics (chapters / main sections)
- Level 2: Subtopics
- Level 3: Key concepts & definitions
- Level 4: Mechanisms, methods, formulas, process steps
- Level 5: Examples, properties, applications, conditions
- Level 6: Exceptions, warnings, common mistakes`,
      crossLinks: '8-20 (use depends_on, causes, contrasts_with, prerequisite_of to connect branches)',
      maxNodes: 110,
      defaultVisibleLevel: 2,
      enrichChildren: 4
    },
    flashcards: {
      count: 20,
      types: '"comparison", "cause_effect", "process", "application", "formula", "concept", "definition", "cloze", "true_false"',
      difficultyMix: 'easy 15%, medium 35%, hard 35%, expert 15%',
      cognitive: 'All Bloom levels with emphasis on Apply, Analyze & Evaluate: "why", "what happens if", "compare X vs Y", "which condition breaks the rule", multi-step processes.'
    },
    quiz: {
      count: 20,
      types: '"multiple_choice", "scenario", "true_false", "fill_blank"',
      difficultyMix: 'easy 10%, medium 35%, hard 35%, expert 20%',
      cognitive: 'Apply, Analyze & Evaluate (Bloom levels 3-5): at least half of the questions must be scenario/reasoning questions. Build distractors from REAL misconceptions and confusable concepts so that only true understanding picks the right answer.',
      timeLimitMinutes: 30
    }
  }
};

/**
 * Prerequisite analysis ("Kiến thức Tiên quyết") targets per depth level
 */
export const PREREQUISITE_TIERS = {
  quick: {
    prerequisites: '2-3, chỉ những kiến thức "critical" thực sự không thể thiếu',
    dependencyLinks: '2-4',
    learningPathSteps: '3',
    bridgeItems: '2-3',
    questions: 3,
    note: 'Ngắn gọn, đi thẳng vào những lỗ hổng chắc chắn khiến người học không đọc được tài liệu.'
  },
  standard: {
    prerequisites: '3-5, gồm cả "critical" và "recommended"',
    dependencyLinks: '4-8',
    learningPathSteps: '3-4',
    bridgeItems: '3-5',
    questions: 4,
    note: 'Cân bằng: đủ để người học tự kiểm tra và tự lấp lỗ hổng trước khi vào bài.'
  },
  deep: {
    prerequisites: '5-8, phủ toàn bộ nền tảng: khái niệm, định lý, công thức, kỹ năng tính toán/tư duy, ký hiệu quy ước',
    dependencyLinks: '8-15, thể hiện rõ chuỗi Nền tảng -> Khái niệm trong bài -> Ứng dụng',
    learningPathSteps: '4-5',
    bridgeItems: '4-6',
    questions: 5,
    note: 'Chuyên sâu: phân tích cả các giả định ngầm, ký hiệu quy ước và kỹ năng phụ trợ mà tài liệu mặc định người học đã có.'
  }
};

const DEPTH_ALIASES = {
  'tóm lược nhanh': 'quick',
  'tom luoc nhanh': 'quick',
  quick: 'quick',
  summary: 'quick',
  'tiêu chuẩn': 'standard',
  'tieu chuan': 'standard',
  standard: 'standard',
  'chuyên sâu': 'deep',
  'chuyen sau': 'deep',
  deep: 'deep',
  expert: 'deep'
};

/**
 * Resolve any depth value (UI label, key or profile object) to a depth profile. Defaults to "standard".
 */
export function resolveDepth(depth) {
  if (depth && typeof depth === 'object' && depth.key) return depth;
  const key = DEPTH_ALIASES[String(depth || '').trim().toLowerCase()];
  return DEPTH_PROFILES[key] || DEPTH_PROFILES.standard;
}

/**
 * Quality rules shared by every depth level — what makes the output "smart" rather than a copy of the source.
 */
export const QUALITY_RULES = `
QUALITY RULES (apply at every depth level):
1. Understand before writing: first work out the document's purpose, its logical outline and which ideas are central vs supporting. Organize output by that logic, not by the order sentences appear.
2. Explain, don't copy: rewrite in clear, natural Vietnamese. Keep technical terms; when the source uses an English/foreign term, keep it in parentheses, e.g. "Khóa chính (Primary Key)".
3. Be specific: use the actual names, numbers, formulas, steps and examples from the source. NEVER write generic filler such as "đóng vai trò quan trọng", "có nhiều ứng dụng", "cần lưu ý" without saying concretely what/why.
4. Grounding: facts, numbers, formulas and examples MUST come from the source. You may add brief standard textbook reasoning only to clarify WHY something in the source is true — never introduce new facts, and when unsure, leave it out.
5. No duplication: each concept appears once; merge near-duplicates.
6. Prioritize by learning value: importance 5 = core idea the rest depends on / very likely to be examined; 1 = minor detail.
`;
