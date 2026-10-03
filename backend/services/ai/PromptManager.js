/**
 * Centralized Prompt Manager with Versioning
 * Stores System Prompts and Version Identifiers
 */
import { resolveDepth, QUALITY_RULES, PREREQUISITE_TIERS } from './DepthProfiles.js';

export const PROMPT_VERSIONS = {
  DOCUMENT_ANALYSIS: "DOCUMENT_ANALYSIS_V2_DEPTH",
  NOTES_GENERATION: "NOTES_GENERATION_V2_DEPTH",
  MINDMAP_GENERATION: "MINDMAP_GENERATION_V3_TIERED",
  FLASHCARD_GENERATION: "FLASHCARD_GENERATION_V3_CONCEPT_IDS",
  QUIZ_GENERATION: "QUIZ_GENERATION_V3_CONCEPT_IDS",
  PREREQUISITE_ANALYSIS: "PREREQUISITE_ANALYSIS_V1",
  CHAT_ASSISTANT: "CHAT_ASSISTANT_V1",
  KNOWLEDGE_FUSION: "KNOWLEDGE_FUSION_V2"
};

// Compact JSON keeps the prompt small so more of the token budget goes to the answer
const kbToPrompt = (knowledgeJson) => JSON.stringify(knowledgeJson);

export class PromptManager {
  static getDocumentAnalysisPrompt(title, rawContent, depth) {
    const profile = resolveDepth(depth);
    const a = profile.analysis;
    return `
You are an expert educational document analysis engine and an experienced teacher.
Your task is to deeply analyze learning material and convert it into a structured knowledge representation that a learner can study from.

ANALYSIS DEPTH: ${profile.label.toUpperCase()}
${profile.goal}
${QUALITY_RULES}
ANALYSIS PROCEDURE:
1. Determine the subject, the purpose of the document and its logical outline (chapters -> sections -> ideas).
2. Extract, according to the depth level: key concepts, definitions, processes (with ordered steps), formulas (with meaning of each variable), rules, conditions & exceptions, examples, comparisons, cause -> effect chains, prerequisites and common misconceptions.
3. Link concepts to each other through typed relationships.
4. Rank everything by importance for learning and examination.

QUANTITY TARGETS FOR THIS DEPTH (scale down for very short sources — never pad with invented content):
- concepts: ${a.concepts}; each "description": ${a.conceptDescription}
- sections: ${a.sections}; keyPoints per section: ${a.keyPointsPerSection}
- keyTakeaways: ${a.keyTakeaways}
- relationships: ${a.relationships}
- misconceptions: ${a.misconceptions}
- prerequisites: ${a.prerequisites}

Document Title: "${title}"
Document Content:
---
${rawContent}
---

Allowed concept "type" values: "concept", "definition", "process", "formula", "example", "fact".
Allowed relationship "type" values: "depends_on", "causes", "produces", "contrasts_with", "type_of", "prerequisite_of", "used_for", "example_of", "related_to".
"difficulty" of the whole document: "easy" | "medium" | "hard".

Return ONLY a valid JSON object matching this exact schema (all text in Vietnamese):
{
  "title": "${title}",
  "language": "Tiếng Việt",
  "summary": "Tóm tắt tổng quan: chủ đề, mục đích và cấu trúc logic của tài liệu",
  "difficulty": "medium",
  "topics": ["Tên chủ đề 1", "Tên chủ đề 2"],
  "concepts": [
    {
      "id": "concept-1",
      "name": "Tên khái niệm/công thức/định nghĩa",
      "type": "concept",
      "description": "Giải thích theo đúng độ sâu yêu cầu, cụ thể và chính xác theo tài liệu",
      "details": ["Điều kiện / bước / tính chất / ngoại lệ cụ thể (bỏ trống nếu không có)"],
      "examples": ["Ví dụ cụ thể lấy từ tài liệu (bỏ trống nếu không có)"],
      "importance": 5,
      "source": { "documentId": "doc-main", "page": 1, "section": "Chương 1" }
    }
  ],
  "relationships": [
    {
      "source": "concept-1",
      "target": "concept-2",
      "type": "depends_on",
      "description": "Giải thích ngắn gọn vì sao hai khái niệm liên hệ với nhau"
    }
  ],
  "sections": [
    {
      "title": "Tên mục chính",
      "summary": "Nội dung tóm tắt mục",
      "keyPoints": ["Ý chính 1", "Ý chính 2"]
    }
  ],
  "misconceptions": [
    { "misconception": "Hiểu sai thường gặp", "correction": "Cách hiểu đúng và lý do", "conceptId": "concept-1" }
  ],
  "prerequisites": ["Kiến thức nền cần có trước khi học tài liệu này"],
  "keyTakeaways": ["Điểm cốt lõi 1", "Điểm cốt lõi 2"]
}
`;
  }

  static getNotesGenerationPrompt(knowledgeJson) {
    const profile = resolveDepth(knowledgeJson?.analysisDepth);
    const n = profile.notes;
    return `
You are an expert teacher writing study notes for a student.
Generate clear, well-structured study notes directly from the provided Knowledge Base JSON.
Do not re-invent or hallucinate facts outside the Knowledge Base.

NOTES DEPTH: ${profile.label.toUpperCase()}
${profile.goal}
${QUALITY_RULES}
STRUCTURE TARGETS FOR THIS DEPTH:
- summary: ${n.summary}
- sections: ${n.sections}; items per section: ${n.itemsPerSection}
- each item "text": ${n.itemText}
- Use the concepts' "details", "examples", "relationships" and "misconceptions" from the Knowledge Base to enrich the items.
- ${n.extra}
- keyTakeaways: the most important things to remember, each one a complete, specific statement (not a topic name).

Knowledge Base JSON:
---
${kbToPrompt(knowledgeJson)}
---

Output MUST be a valid JSON object following this exact schema (all text in Vietnamese):
{
  "summaryTitle": "Tiêu đề ghi chú tóm tắt bài học",
  "summary": "Tóm tắt tổng quan",
  "keyTakeaways": ["Điểm ghi nhớ trọng tâm 1", "Điểm ghi nhớ trọng tâm 2"],
  "sections": [
    {
      "heading": "TÊN MỤC CHÍNH (IN HOA)",
      "items": [
        {
          "label": "Tên thuật ngữ/ý chính",
          "text": "Nội dung giải thích theo đúng độ sâu yêu cầu"
        }
      ]
    }
  ]
}
`;
  }

  static getMindmapGenerationPrompt(knowledgeJson) {
    const profile = resolveDepth(knowledgeJson?.analysisDepth);
    const m = profile.mindmap;
    return `
You are an expert AI Knowledge Mindmap Engine.
Transform the provided Knowledge Base JSON into a well-structured, multi-level Knowledge Mindmap.

MINDMAP DEPTH: ${profile.label.toUpperCase()}
${profile.goal}
${QUALITY_RULES}
SIZE TARGETS FOR THIS DEPTH:
- Total nodes: ${m.nodes} (scale down for small knowledge bases — never invent nodes)
- Maximum depth: ${m.maxDepth} levels below the root
- Cross-link edges between different branches: ${m.crossLinks}
- Each branch must be balanced and logically grouped; siblings must be at the same level of abstraction.
- "label" must be specific (e.g. "Công thức ΔG = ΔH − TΔS", not "Công thức"); "summary" explains the node in 1-3 sentences.

Knowledge Base JSON:
---
${kbToPrompt(knowledgeJson)}
---

HIERARCHY RULES:
- Level 0: Root Node (Central subject)
${m.levels}

NODE TYPES MUST BE ONE OF:
- "topic", "subtopic", "concept", "definition", "process", "method", "formula", "example", "fact", "comparison", "advantage", "disadvantage", "application", "warning", "important"

IMPORTANCE SCORE (1 to 5):
- 5: Core critical concepts (Highlight rank)
- 4: Major important topics
- 3: Standard concepts & definitions
- 2: Supporting details & methods
- 1: Minor examples & extra notes

EDGE RELATIONSHIP TYPES:
- "contains" (parent-child default)
- "depends_on", "related_to", "causes", "produces", "example_of", "type_of", "contrasts_with", "prerequisite_of", "used_for"

OUTPUT FORMAT REQUIREMENT (compact on purpose — the hierarchy is defined ONLY by "parentId", levels and parent->child edges are computed by the system, so do NOT output "level", "children" or "contains" edges):
- Output nodes in depth-first order: a parent always appears before its children, and finish one Level-1 branch completely before starting the next.
- "id" pattern mirrors the path: "n1", "n1-2", "n1-2-3"...
- "crossLinks" only connect nodes in DIFFERENT branches with a meaningful relation.

Return ONLY a valid JSON object matching this exact schema (all text in Vietnamese):
{
  "root": { "id": "root-node", "label": "TÊN CHỦ ĐỀ CHÍNH", "summary": "Tổng quan kiến thức toàn bộ bài học" },
  "nodes": [
    { "id": "n1", "parentId": "root-node", "label": "Tên nhánh chính 1", "type": "subtopic", "summary": "Giải thích nhánh này", "importance": 4 },
    { "id": "n1-1", "parentId": "n1", "label": "Tên khái niệm 1.1", "type": "concept", "summary": "Mô tả khái niệm chính xác từ tài liệu", "importance": 3 }
  ],
  "crossLinks": [
    { "source": "n1-1", "target": "n2-1", "type": "depends_on" }
  ]
}
`;
  }

  static getNodeExpansionPrompt(targetNode, docContext = "") {
    return `
You are an expert educational AI Mindmap Expansion Engine.
Your task is to DEEPEN and EXPAND a specific target mindmap node into 3 to 8 sub-branches containing detailed concepts, processes, definitions, and examples derived from the context.

Target Node:
---
${JSON.stringify(targetNode, null, 2)}
---

Document Context:
---
${docContext.slice(0, 4000)}
---

Generate ONLY a valid JSON object containing new child nodes and edges to be attached to the target node:
{
  "expandedNodes": [
    {
      "id": "sub-${Date.now()}-1",
      "parentId": "${targetNode.id}",
      "label": "Tên khái niệm con mở rộng 1",
      "shortLabel": "Khái niệm 1",
      "type": "concept",
      "summary": "Giải thích sâu sắc và chi tiết",
      "importance": 3,
      "level": ${(targetNode.level || 2) + 1},
      "children": []
    }
  ],
  "expandedEdges": [
    {
      "id": "edge-exp-1",
      "source": "${targetNode.id}",
      "target": "sub-${Date.now()}-1",
      "type": "contains"
    }
  ]
}
`;
  }

  static getFlashcardGenerationPrompt(knowledgeJson, userSettings = {}) {
    const profile = resolveDepth(userSettings.depth || knowledgeJson?.analysisDepth);
    const f = profile.flashcards;
    const targetCount = userSettings.flashcardCount || f.count;
    const requestedDifficulty = userSettings.difficulty || "mixed";
    const difficultyRule = requestedDifficulty === 'mixed'
      ? `Distribute difficulty as: ${f.difficultyMix}.`
      : `All cards should be at difficulty "${requestedDifficulty}".`;
    return `
You are the AI Learning Content Generator for StudyMind AI and an expert in spaced-repetition learning.
Your task is to generate high-quality, SOURCE-GROUNDED Flashcards based STRICTLY on the provided Knowledge Base JSON.

FLASHCARD DEPTH: ${profile.label.toUpperCase()}
${profile.goal}
Cognitive focus: ${f.cognitive}

CRITICAL RULES (SOURCE-GROUNDED GENERATION):
1. Use ONLY facts, definitions, formulas, and concepts present in the provided Knowledge Base JSON.
2. DO NOT hallucinate, invent, or bring in outside information not supported by the document.
3. Every flashcard MUST link to a concept: "conceptId" is copied EXACTLY from the "id" of a concept in the Knowledge Base (never invent ids). Add source references (page, section).
4. Do NOT create duplicate flashcards testing the same concept in the same way. Cover the most important concepts first (importance 5 -> 1).
5. Preferred flashcard types for this depth: ${f.types}. Allowed values: "definition", "concept", "comparison", "process", "formula", "example", "application", "cause_effect", "cloze", "true_false".
6. ${difficultyRule} Allowed values: "easy", "medium", "hard", "expert".
7. Target count: EXACTLY ${targetCount} flashcards (or maximum possible high-quality items without hallucinating).
8. Card quality: "front" asks ONE precise question (never just a bare term if a better question exists); "back" answers it completely but concisely and explains the reason when relevant; "hint" nudges toward the answer without revealing it. Front and back must not repeat each other.

Knowledge Base JSON:
---
${kbToPrompt(knowledgeJson)}
---

Output MUST be a valid JSON object matching this exact schema:
{
  "flashcards": [
    {
      "id": "fc_001",
      "type": "definition",
      "topicId": "topic_001",
      "conceptId": "${knowledgeJson.concepts?.[0]?.id || 'concept-1'}",
      "front": "Primary Key là gì?",
      "back": "Primary Key là trường hoặc tập hợp trường dùng để xác định duy nhất mỗi bản ghi trong bảng.",
      "hint": "Gợi ý về đặc tính duy nhất",
      "difficulty": "easy",
      "importance": 5,
      "tags": ["database", "primary-key"],
      "source": {
        "documentId": "${knowledgeJson.id || 'doc-1'}",
        "page": 1,
        "section": "Chủ đề chính"
      }
    }
  ]
}
`;
  }

  static getQuizGenerationPrompt(knowledgeJson, userSettings = {}) {
    const profile = resolveDepth(userSettings.depth || knowledgeJson?.analysisDepth);
    const q = profile.quiz;
    const targetCount = userSettings.quizCount || q.count;
    const requestedDifficulty = userSettings.difficulty || "mixed";
    const difficultyRule = requestedDifficulty === 'mixed'
      ? `Distribute difficulty as: ${q.difficultyMix}.`
      : `All questions should be at difficulty "${requestedDifficulty}".`;
    return `
You are the AI Learning Content Generator for StudyMind AI and an experienced exam designer.
Your task is to generate a comprehensive, SOURCE-GROUNDED Quiz based STRICTLY on the provided Knowledge Base JSON.

QUIZ DEPTH: ${profile.label.toUpperCase()}
${profile.goal}
Cognitive focus: ${q.cognitive}

CRITICAL RULES (SOURCE-GROUNDED GENERATION):
1. AI MUST ONLY use information present in the provided Knowledge Base JSON. No outside facts or unsupported claims.
2. Every quiz question MUST test real concepts from the document: "conceptId" is copied EXACTLY from the "id" of the concept it tests in the Knowledge Base (never invent ids). Cover different concepts — do not test the same fact twice.
3. Every question MUST have:
   - Exactly 1 clear, unambiguous question text
   - Exactly 4 options (labelled A, B, C, D) — for true_false use "A. Đúng", "B. Sai" plus 2 options that qualify the statement
   - Exactly 1 correct index (0 for A, 1 for B, 2 for C, 3 for D); vary the position of the correct answer across questions
   - 3 plausible distractors built from confusable concepts or the Knowledge Base "misconceptions" (never absurd, but unambiguously incorrect based on the source); avoid "Tất cả đều đúng/sai"
   - An educational explanation: why the correct answer is right AND why the tempting distractor is wrong, citing the concept/rule from the source.
4. Preferred question types for this depth: ${q.types}. Allowed values: "multiple_choice", "true_false", "fill_blank", "short_answer", "scenario".
5. ${difficultyRule} Allowed values: "easy", "medium", "hard", "expert".
6. Target count: EXACTLY ${targetCount} questions.

Knowledge Base JSON:
---
${kbToPrompt(knowledgeJson)}
---

Output MUST be a valid JSON object matching this exact schema:
{
  "title": "Đề kiểm tra trắc nghiệm AI: ${knowledgeJson.title || 'Tài liệu học tập'}",
  "subject": "${knowledgeJson.title || 'Trắc nghiệm tổng hợp'}",
  "timeLimitMinutes": ${q.timeLimitMinutes},
  "questions": [
    {
      "id": "q_001",
      "type": "multiple_choice",
      "topicId": "topic_001",
      "conceptId": "${knowledgeJson.concepts?.[0]?.id || 'concept-1'}",
      "questionNumber": 1,
      "questionText": "Mục đích chính của Primary Key trong cơ sở dữ liệu là gì?",
      "options": [
        "A. Xác định duy nhất mỗi bản ghi trong bảng",
        "B. Lưu trữ tất cả dữ liệu dạng văn bản",
        "C. Tự động xóa bảng khi dữ liệu bị lỗi",
        "D. Tạo giao diện người dùng tự động"
      ],
      "correctIndex": 0,
      "explanation": "Primary Key được sử dụng để xác định duy nhất từng dòng/bản ghi trong bảng cơ sở dữ liệu.",
      "difficulty": "easy",
      "importance": 5,
      "source": {
        "documentId": "${knowledgeJson.id || 'doc-1'}",
        "page": 1,
        "section": "Chương 1"
      }
    }
  ]
}
`;
  }

  static getPrerequisitePrompt(documentTitle, documentContent, depth) {
    const profile = resolveDepth(depth);
    const t = PREREQUISITE_TIERS[profile.key];
    const safeTitle = String(documentTitle || 'Tài liệu học tập').replace(/"/g, "'");
    return `
Bạn là Chuyên gia Khoa học Giáo dục và Thiết kế Lộ trình Nhận thức (Cognitive Curriculum Specialist).
Nhiệm vụ của bạn là phân tích sâu tài liệu học tập được cung cấp, bóc tách toàn bộ "Kiến thức Tiên quyết" (Prerequisites) mà người học BẮT BUỘC hoặc NÊN BIẾT TRƯỚC để có thể hiểu trọn vẹn tài liệu này mà không bị quá tải nhận thức (Cognitive Overload).

--- THÔNG TIN TÀI LIỆU ---
Tiêu đề: "${safeTitle}"
Nội dung tài liệu:
"""
${documentContent}
"""
---------------------------

MỨC ĐỘ PHÂN TÍCH: ${profile.label.toUpperCase()}
${t.note}
Chỉ tiêu số lượng cho mức này (tài liệu quá ngắn thì giảm bớt, TUYỆT ĐỐI không bịa thêm cho đủ số):
- prerequisites: ${t.prerequisites}
- dependencyGraph: ${t.dependencyLinks} liên kết
- learningPath: ${t.learningPathSteps} bước
- quickBridgeSummary: ${t.bridgeItems} mục
- diagnosticPreTest.questions: ĐÚNG ${t.questions} câu

HƯỚNG DẪN BÓC TÁCH & PHÂN TÍCH:
1. Xác định "Vùng Kiến thức Nền tảng" (Prerequisite Knowledge):
   - Tìm những khái niệm, định lý, công thức, quy tắc mà tài liệu ĐÃ MẶC ĐỊNH NGƯỜI HỌC BIẾT RỒI và không giải thích lại. Dấu hiệu nhận biết: thuật ngữ được dùng mà không định nghĩa, phép biến đổi/tính toán được làm tắt, ký hiệu dùng mà không chú thích.
   - KHÔNG liệt kê những gì tài liệu đã tự giải thích — đó là nội dung bài học, không phải tiên quyết.
   - Phân loại độ ưu tiên:
     + "critical": Bắt buộc phải hiểu, nếu không biết sẽ hoàn toàn không hiểu tài liệu.
     + "recommended": Nên biết để nắm bản chất nhanh hơn và liên hệ thực tế tốt hơn.
   - Chỉ ra xuất xứ kiến thức (ví dụ: "Toán lớp 10", "Hóa học đại cương", "Lập trình C cơ bản").
   - "whyNeeded" và "consequenceIfMissing" phải chỉ rõ phần/mục/công thức CỤ THỂ trong tài liệu sẽ bị ảnh hưởng, không viết chung chung.

2. Thiết kế "Đồ thị Phụ thuộc" (Dependency Graph):
   - Tạo các mối quan hệ theo thứ tự logic: Khái niệm nền tảng A -> dẫn tới Khái niệm trong bài B -> áp dụng vào Bài toán/Ứng dụng C.
   - Mỗi liên kết trong "dependencyGraph" có "from" (tên khái niệm nguồn), "to" (tên khái niệm đích), "fromType"/"toType" thuộc "prerequisite" | "core" | "application", và "relation" mô tả ngắn quan hệ.
   - Xác định "Điểm nghẽn nhận thức" (Cognitive Bottleneck): Điểm mấu chốt nhất mà nếu người học bị hổng thì toàn bộ các chương/phần sau sẽ sụp đổ.

3. Bộ câu hỏi Khảo sát Nhanh Đầu vào (Diagnostic Pre-test):
   - Soạn ĐÚNG ${t.questions} câu hỏi trắc nghiệm ngắn gọn (4 lựa chọn A, B, C, D).
   - QUAN TRỌNG: Câu hỏi KHÔNG ĐƯỢC hỏi nội dung mới có trong tài liệu, mà PHẢI HỎI kiến thức nền tảng tiên quyết để thẩm định xem học viên đã sẵn sàng đọc bài này chưa.
   - Ưu tiên kiểm tra các prerequisite "critical"; mỗi câu kiểm tra một prerequisite khác nhau khi có thể.
   - Phương án nhiễu phải hợp lý (dựa trên lỗi sai phổ biến), vị trí đáp án đúng thay đổi giữa các câu.
   - Mỗi câu hỏi phải có đáp án, giải thích và chỉ rõ câu này đang kiểm tra khái niệm tiên quyết nào ("testedPrerequisiteId" trùng với "id" trong "prerequisites").

4. Cầu nối Kiến thức Cấp tốc (Quick Bridge Summary):
   - Soạn các mục tóm tắt ngắn giải thích nhanh lại các khái niệm tiên quyết quan trọng nhất, giúp học viên bị hổng kiến thức có thể "đọc nhanh trong 2 phút" là có thể tự tin vào học ngay mà không cần bỏ đi tìm tài liệu khác.
   - Mỗi mục có "prerequisiteId" tương ứng và "quickReview" 2-3 câu kèm công thức/ví dụ mẫu cốt lõi.

QUY TẮC ĐẦU RA:
- Trả về DUY NHẤT một chuỗi JSON hợp lệ (Strict JSON), không bọc trong lời dẫn, không markdown thừa.
- Toàn bộ nội dung dùng Tiếng Việt chuẩn mực sư phạm.
- "priority" chỉ nhận "critical" hoặc "recommended". "type" trong learningPath chỉ nhận "prerequisite" | "core_learning" | "advanced_application".

CẤU TRÚC JSON BẮT BUỘC:
{
  "documentTitle": "${safeTitle}",
  "subjectArea": "Lĩnh vực / Môn học chính",
  "targetAudienceLevel": "Trình độ phù hợp (vd: Học sinh lớp 12, Sinh viên năm 2)",
  "overallReadinessNote": "Nhận định tổng quan về nền tảng cần thiết trước khi đọc bài này",
  "prerequisites": [
    {
      "id": "pre-1",
      "concept": "Tên khái niệm / định lý nền tảng",
      "priority": "critical",
      "level": "Cấp độ kiến thức (vd: Đại số tuyến tính, Hóa THPT)",
      "whyNeeded": "Giải thích vì sao cần biết kiến thức này trước khi học tài liệu hiện tại",
      "consequenceIfMissing": "Hậu quả nếu bị hổng phần này (vd: Sẽ không thể hiểu cách tính đạo hàm riêng ở mục 2)"
    }
  ],
  "dependencyGraph": [
    { "from": "Khái niệm nền tảng A", "fromType": "prerequisite", "to": "Khái niệm trong bài B", "toType": "core", "relation": "là cơ sở để hiểu" }
  ],
  "cognitiveBottleneck": {
    "concept": "Khái niệm điểm nghẽn mấu chốt nhất",
    "description": "Lý do vì sao đây là điểm dễ gây 'ngợp' hoặc nản chí nhất",
    "advice": "Lời khuyên cách tiếp cận để vượt qua"
  },
  "learningPath": [
    { "step": 1, "type": "prerequisite", "title": "Ôn lại kiến thức nền", "description": "Mô tả ngắn việc cần làm trước" },
    { "step": 2, "type": "core_learning", "title": "Bắt đầu học nội dung tài liệu", "description": "Phần trọng tâm nên đọc trước" },
    { "step": 3, "type": "advanced_application", "title": "Vận dụng và liên hệ", "description": "Mục tiêu nâng cao" }
  ],
  "quickBridgeSummary": [
    { "prerequisiteId": "pre-1", "concept": "Tên khái niệm nền", "quickReview": "Giải thích siêu ngắn, dễ hiểu trong 2-3 câu kèm công thức/ví dụ mẫu cốt lõi" }
  ],
  "diagnosticPreTest": {
    "title": "Bài test chẩn đoán độ sẵn sàng đầu vào",
    "instructions": "Làm nhanh ${t.questions} câu sau để kiểm tra bạn đã đủ điều kiện tiếp thu bài này chưa",
    "passScore": 75,
    "questions": [
      {
        "id": "pre-q1",
        "testedPrerequisiteId": "pre-1",
        "question": "Nội dung câu hỏi kiểm tra kiến thức nền?",
        "options": ["A. Lựa chọn 1", "B. Lựa chọn 2", "C. Lựa chọn 3", "D. Lựa chọn 4"],
        "correctIndex": 0,
        "explanation": "Giải thích tại sao đáp án đúng và liên hệ ngắn tới bài học sắp tới"
      }
    ]
  }
}
`;
  }

  static getAdaptiveContentPrompt(knowledgeJson, weakConceptIds = []) {
    return `
You are the Adaptive Learning Generator for StudyMind AI.
The user has demonstrated WEAKNESS in specific concepts during recent quizzes/flashcard sessions.

Target Weak Concept IDs:
${JSON.stringify(weakConceptIds)}

Knowledge Base JSON:
---
${JSON.stringify(knowledgeJson, null, 2)}
---

Generate 5 reinforcement flashcards and 5 reinforcement quiz questions specifically targeting these weak concepts to accelerate mastery.
Output MUST be a valid JSON object:
{
  "reinforcementFlashcards": [
    {
      "id": "fc_reinforce_1",
      "conceptId": "concept-id-here",
      "type": "cloze",
      "front": "Nội dung câu hỏi củng cố...",
      "back": "Giải thích củng cố...",
      "difficulty": "medium",
      "hint": "Gợi ý củng cố"
    }
  ],
  "reinforcementQuestions": [
    {
      "id": "q_reinforce_1",
      "conceptId": "concept-id-here",
      "questionText": "Câu hỏi củng cố khái niệm yếu...",
      "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
      "correctIndex": 0,
      "explanation": "Lời giải chi tiết củng cố kiến thức...",
      "difficulty": "medium"
    }
  ]
}
`;
  }

  static getKnowledgeFusionPrompt(documents) {
    const formattedDocs = documents.map((doc, idx) => {
      const note = doc.studyPack?.note || doc.studyPack?.notes || {};
      const keyConcepts = Array.isArray(note.keyConcepts)
        ? note.keyConcepts.map(c => `- ${c.term}: ${c.definition}`).join('\n')
        : '';
      const sections = Array.isArray(note.sections)
        ? note.sections.map(s => `+ [${s.heading}]: ${(s.points || s.keyPoints || []).join('; ')}`).join('\n')
        : '';
      const flashcards = Array.isArray(doc.studyPack?.flashcards)
        ? doc.studyPack.flashcards.slice(0, 6).map(f => `* Hỏi: ${f.question} -> Đáp: ${f.answer}`).join('\n')
        : '';

      return `
--- TÀI LIỆU #${idx + 1}: ${doc.title} ---
ID: ${doc.id}
Chủ đề / Thẻ: ${(doc.tags || []).join(', ')}
Tóm tắt nội dung:
${note.summary || doc.rawText || doc.summary || "Không có tóm tắt chi tiết"}

Hệ thống Khái niệm Trọng tâm (Key Concepts):
${keyConcepts || "Không có danh sách khái niệm"}

Các Mục chính & Điểm cốt lõi (Sections):
${sections || "Không có mục chi tiết"}

Trích dẫn Thẻ ghi nhớ / Trắc nghiệm tiêu biểu:
${flashcards || "Không có thẻ ghi nhớ"}
`;
    }).join('\n\n');

    return `
You are an expert multi-document educational synthesis & knowledge fusion AI engine.
Your goal is to perform deep cross-analysis across the provided learning documents.
DO NOT provide generic filler, placeholder text, or shallow summaries. Produce an academically rigorous, highly substantive synthesis.

Analyze the documents below:
${formattedDocs}

Tasks:
1. "unifiedSummary": Write a comprehensive, multi-paragraph executive summary (in Vietnamese) synthesizing how the concepts across all documents interlock, complement, or contrast with each other. Mention specific terminology from both documents.
2. "commonConcepts": Identify 3-5 core shared concepts or interdisciplinary bridges:
   - If documents share the same subject: identify true conceptual overlaps and shared scientific or structural principles.
   - If documents belong to different disciplines (e.g. English grammar vs Chemistry/Biology/Physics): identify deep structural, cognitive, or taxonomic parallels (e.g. Two-dimensional Matrix Taxonomy: 12 Tenses matrix vs Periodic Table matrix; Symbolic Syntax Conventions; Bilingual STEM Terminology Bridge connecting English terms to scientific definitions; Causality & Conditionality Rules).
   For each concept:
   - "concept": Academic title of the concept (in Vietnamese)
   - "definition": Clear, substantive synthesized definition with real examples and explanations (in Vietnamese, at least 2-3 sentences)
   - "sources": Array of document titles that contain or connect to this concept
3. "uniqueInsights": For EACH document provided, extract 3-4 specific unique details, formulas, procedural steps, or exclusive rules that appear in that document:
   - "docId": Document ID
   - "docTitle": Document Title
   - "insights": Array of strings (in Vietnamese, containing real terminology and formulas)
4. "conflicts": Identify 2-3 factual, numerical, or perspective discrepancies / contradictions / cognitive pitfalls (e.g. symbolic ambiguity between subjects like 'S' for Subject vs 'S' for Sulfur; artificial grammar conventions with exceptions vs immutable natural laws; reversible vs irreversible processes). For each conflict:
   - "id": "conf-1", "conf-2", etc.
   - "topic": Clear, specific topic of disagreement or potential confusion (in Vietnamese)
   - "docA": { "id": docIdA, "title": docTitleA, "statement": "Specific statement, formula, or rule in Doc A (in Vietnamese)" }
   - "docB": { "id": docIdB, "title": docTitleB, "statement": "Specific statement, formula, or rule in Doc B (in Vietnamese)" }
   - "explanation": In-depth explanation of why the difference or pitfall exists (in Vietnamese)
   - "recommendation": Actionable advice for students to prevent confusion (in Vietnamese)
5. "mergedMindmap": Construct a merged mindmap representation with a root node, subtopics for shared & unique concepts, and warning nodes for conflicts.
   - "rootLabel": "Mạng lưới Kiến thức Hợp nhất"
   - "nodes": Array of { "id": string, "label": string, "type": "topic"|"subtopic"|"concept"|"warning"|"fact", "importance"?: number, "level": number, "parentId"?: string, "detail"?: string }

Output MUST be a valid JSON object matching this exact schema:
{
  "fusionTitle": "Báo cáo Hợp nhất & Đối chiếu Đa Tài liệu",
  "unifiedSummary": "...",
  "commonConcepts": [
    { "concept": "...", "definition": "...", "sources": ["..."] }
  ],
  "uniqueInsights": [
    { "docId": "...", "docTitle": "...", "insights": ["..."] }
  ],
  "conflicts": [
    {
      "id": "conf-1",
      "topic": "...",
      "docA": { "id": "...", "title": "...", "statement": "..." },
      "docB": { "id": "...", "title": "...", "statement": "..." },
      "explanation": "...",
      "recommendation": "..."
    }
  ],
  "mergedMindmap": {
    "rootLabel": "Mạng lưới Kiến thức Hợp nhất",
    "nodes": [
      { "id": "m-root", "label": "Mạng lưới Kiến thức Hợp nhất", "type": "topic", "importance": 5, "level": 0 },
      { "id": "m-common", "label": "Kiến thức Chung (Đã gộp trùng)", "type": "subtopic", "importance": 5, "level": 1, "parentId": "m-root" }
    ]
  }
}
`;
  }
}
