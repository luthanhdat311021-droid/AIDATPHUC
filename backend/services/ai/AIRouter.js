import dotenv from 'dotenv';
import { GeminiProvider } from './GeminiProvider.js';
import { GroqProvider } from './GroqProvider.js';
import { aiCache, AICache } from './AICache.js';
import { PromptManager, PROMPT_VERSIONS } from './PromptManager.js';
import { resolveDepth } from './DepthProfiles.js';
dotenv.config();

export class AIRouter {
  constructor() {
    this.gemini = new GeminiProvider();
    this.groq = new GroqProvider();
    this.mode = process.env.AI_PROVIDER || 'auto';
  }

  /**
   * Determine primary provider based on task and document type
   */
  selectPrimaryProvider(task, options = {}) {
    if (this.mode === 'gemini') return this.gemini;
    if (this.mode === 'groq') return this.groq;

    // Auto Routing rules: use Gemini if vision/image analysis, otherwise use Groq
    if ((task === 'image_analysis' || options.isVision) && this.gemini) {
      return this.gemini;
    }

    if (this.groq && this.groq.groq) {
      return this.groq;
    }

    return this.gemini;
  }

  getSecondaryProvider(primaryProvider) {
    return primaryProvider === this.gemini ? this.groq : this.gemini;
  }

  /**
   * Generic execution wrapper with Multi-Provider Fallback
   */
  async executeWithFallback(task, executionFn, options = {}) {
    const primary = this.selectPrimaryProvider(task, options);
    const secondary = this.getSecondaryProvider(primary);

    console.log(`🧭 [AI Router] Routing task "${task}" -> Primary: ${primary.name}`);

    // Try Primary Provider with retry
    try {
      return await executionFn(primary);
    } catch (primaryErr) {
      console.warn(`⚠️ [AI Router] Primary provider ${primary.name} failed for task "${task}": ${primaryErr.message}`);
      console.log(`🔄 [AI Router] Falling back to Secondary Provider: ${secondary.name}`);
      
      // Try Secondary Provider
      try {
        return await executionFn(secondary);
      } catch (secondaryErr) {
        console.error(`❌ [AI Router] Secondary provider ${secondary.name} also failed: ${secondaryErr.message}`);
        throw secondaryErr;
      }
    }
  }

  /**
   * Analyze document with caching & fallback
   */
  async analyzeDocument(content, metadata = {}) {
    // Downstream prompts (notes/mindmap/flashcards/quiz) read analysisDepth from the knowledge base
    const depthKey = resolveDepth(metadata.depth).key;
    metadata = { ...metadata, depth: depthKey };

    const cacheKey = AICache.generateKey(content, `document_analysis_${depthKey}`, this.mode, PROMPT_VERSIONS.DOCUMENT_ANALYSIS);
    const cached = aiCache.get(cacheKey);
    if (cached) return cached;

    const taskType = metadata.isVision ? 'image_analysis' : (content.length > 8000 ? 'long_document_analysis' : 'quick_summary');

    try {
      const result = await this.executeWithFallback(taskType, (provider) => provider.analyzeDocument(content, metadata), metadata);
      result.analysisDepth = depthKey;
      aiCache.set(cacheKey, result);
      return result;
    } catch (err) {
      console.warn("⚠️ [AI Router] All AI Providers failed. Generating Intelligent Domain Knowledge Base.");
      const fallback = this.generateDomainFallbackKnowledge(metadata.title || "Tài liệu học tập", content);
      fallback.analysisDepth = depthKey;
      return fallback;
    }
  }

  /**
   * Generate Notes from Knowledge Base JSON
   */
  async generateNotes(knowledgeJson) {
    const cacheKey = AICache.generateKey(JSON.stringify(knowledgeJson), 'notes', this.mode, PROMPT_VERSIONS.NOTES_GENERATION);
    const cached = aiCache.get(cacheKey);
    if (cached) return cached;

    try {
      const result = await this.executeWithFallback('quick_summary', (provider) => provider.generateNotes(knowledgeJson));
      aiCache.set(cacheKey, result);
      return result;
    } catch (err) {
      return {
        summaryTitle: knowledgeJson.title || "Tóm tắt bài học",
        summary: knowledgeJson.summary || "Nội dung tóm tắt cốt lõi.",
        keyTakeaways: knowledgeJson.keyTakeaways || ["Nội dung học tập trọng tâm"],
        sections: (knowledgeJson.sections || []).map(s => ({
          heading: (s.title || "MỤC BÀI HỌC").toUpperCase(),
          items: (s.keyPoints || ["Chi tiết bài học"]).map(pt => ({ label: "Ý chính", text: pt }))
        }))
      };
    }
  }

  /**
   * Generate Mindmap from Knowledge Base JSON
   */
  async generateMindmap(knowledgeJson) {
    const cacheKey = AICache.generateKey(JSON.stringify(knowledgeJson), 'mindmap', this.mode, PROMPT_VERSIONS.MINDMAP_GENERATION);
    const cached = aiCache.get(cacheKey);
    if (cached) return cached;

    try {
      const rawResult = await this.executeWithFallback('long_document_analysis', (provider) => provider.generateMindmap(knowledgeJson));
      const validatedResult = this.normalizeAndValidateMindmap(rawResult, knowledgeJson);
      // A near-empty AI answer is worse than the knowledge-base skeleton
      if (validatedResult.nodes.length < 5) throw new Error(`Mindmap too small (${validatedResult.nodes.length} nodes)`);
      aiCache.set(cacheKey, validatedResult);
      return validatedResult;
    } catch (err) {
      console.warn("⚠️ [AI Router] Deep Mindmap generation fallback activated:", err.message);
      return this.generateDomainFallbackMindmap(knowledgeJson);
    }
  }

  /**
   * Incremental AI Node Expansion
   */
  async expandNode(targetNode, docContext = "") {
    try {
      const prompt = PromptManager.getNodeExpansionPrompt(targetNode, docContext);
      const resText = await this.executeWithFallback('quick_summary', (provider) => provider.generateText(prompt));
      const parsed = typeof resText === 'string' ? JSON.parse(resText.replace(/```json|```/g, '').trim()) : resText;
      return parsed;
    } catch (err) {
      console.warn("⚠️ [AI Router] Node expansion fallback activated:", err.message);
      return {
        expandedNodes: [
          {
            id: `sub-${Date.now()}-1`,
            parentId: targetNode.id,
            label: `Chi tiết 1 về ${targetNode.label}`,
            shortLabel: "Chi tiết 1",
            type: "concept",
            summary: `Giải thích chi tiết kiến thức liên quan đến ${targetNode.label}.`,
            importance: 3,
            level: (targetNode.level || 2) + 1,
            children: []
          },
          {
            id: `sub-${Date.now()}-2`,
            parentId: targetNode.id,
            label: `Ứng dụng thực tế của ${targetNode.label}`,
            shortLabel: "Ứng dụng",
            type: "application",
            summary: `Ví dụ và ứng dụng thực tiễn trong bài học.`,
            importance: 2,
            level: (targetNode.level || 2) + 1,
            children: []
          }
        ],
        expandedEdges: [
          { id: `edge-exp-${Date.now()}-1`, source: targetNode.id, target: `sub-${Date.now()}-1`, type: "contains" },
          { id: `edge-exp-${Date.now()}-2`, source: targetNode.id, target: `sub-${Date.now()}-2`, type: "contains" }
        ]
      };
    }
  }

  /**
   * Mindmap Schema Validator, Repair & Tiering Engine
   * The hierarchy is rebuilt from parentId only (AI "level" values are unreliable), then the depth tier is applied:
   * prune below the tier's maxDepth, cap the node count (breadth first), and backfill leaf concepts from the knowledge base.
   */
  normalizeAndValidateMindmap(rawMindmap, knowledgeJson = {}) {
    if (!rawMindmap) return this.generateDomainFallbackMindmap(knowledgeJson);

    const profile = resolveDepth(knowledgeJson.analysisDepth);
    const { maxDepth, maxNodes, enrichChildren, defaultVisibleLevel } = profile.mindmap;
    const defaultTitle = knowledgeJson.title || "TÀI LIỆU HỌC TẬP";
    const ROOT = 'root-node';

    const rootLabel = rawMindmap.root?.label || rawMindmap.rootLabel || defaultTitle.toUpperCase();
    const root = {
      id: ROOT,
      label: rootLabel,
      shortLabel: rawMindmap.root?.shortLabel || rootLabel.slice(0, 20),
      type: 'topic',
      summary: rawMindmap.root?.summary || knowledgeJson.summary || `Sơ đồ tư duy tổng quan bài học: ${rootLabel}`,
      importance: 5,
      level: 0,
      children: []
    };
    const rawRootId = rawMindmap.root?.id || ROOT;
    const toId = (id) => (!id || id === rawRootId ? ROOT : String(id));

    // 1. Collect nodes with unique ids
    const nodesById = new Map();
    (Array.isArray(rawMindmap.nodes) ? rawMindmap.nodes : []).forEach((n, idx) => {
      if (!n || !n.label || toId(n.id) === ROOT) return;
      let id = n.id ? String(n.id) : `node-${idx + 1}`;
      if (nodesById.has(id)) id = `${id}-${idx + 1}`;
      const label = String(n.label).trim();
      nodesById.set(id, {
        id,
        parentId: toId(n.parentId),
        label,
        shortLabel: n.shortLabel || label.slice(0, 25),
        type: n.type || 'concept',
        summary: n.summary || n.detail || '',
        importance: typeof n.importance === 'number' ? Math.min(5, Math.max(1, n.importance)) : 3,
        subDetails: Array.isArray(n.subDetails) ? n.subDetails : [],
        source: n.source || { section: "Bài học" },
        children: []
      });
    });

    // 2. Orphans go under the root; siblings with the same label are merged (their children move to the survivor)
    const alias = new Map();
    const resolve = (id) => {
      let cur = id;
      for (let i = 0; i < 20 && alias.has(cur); i++) cur = alias.get(cur);
      return cur;
    };
    const siblingKeys = new Map();
    for (const n of nodesById.values()) {
      if (n.parentId !== ROOT && !nodesById.has(n.parentId)) n.parentId = ROOT;
    }
    for (const n of Array.from(nodesById.values())) {
      const key = `${resolve(n.parentId)}::${n.label.toLowerCase()}`;
      if (siblingKeys.has(key)) {
        alias.set(n.id, siblingKeys.get(key));
        nodesById.delete(n.id);
      } else {
        siblingKeys.set(key, n.id);
      }
    }
    for (const n of nodesById.values()) {
      n.parentId = resolve(n.parentId);
      if (n.parentId === n.id || (n.parentId !== ROOT && !nodesById.has(n.parentId))) n.parentId = ROOT;
    }

    // 3. Breadth-first walk from the root assigns true levels; nodes unreachable because of cycles are re-attached to the root
    const childrenOf = new Map();
    for (const n of nodesById.values()) {
      if (!childrenOf.has(n.parentId)) childrenOf.set(n.parentId, []);
      childrenOf.get(n.parentId).push(n);
    }
    const levelOf = new Map([[ROOT, 0]]);
    const order = [];
    const walk = (startId) => {
      const queue = [startId];
      while (queue.length) {
        const id = queue.shift();
        if (id !== ROOT) order.push(id);
        for (const child of childrenOf.get(id) || []) {
          if (levelOf.has(child.id)) continue;
          levelOf.set(child.id, levelOf.get(id) + 1);
          queue.push(child.id);
        }
      }
    };
    walk(ROOT);
    for (const n of nodesById.values()) {
      if (levelOf.has(n.id)) continue;
      n.parentId = ROOT;
      levelOf.set(n.id, 1);
      walk(n.id);
    }

    // 4. Apply the tier: depth limit, then node budget (breadth first, so a kept node always has its parent)
    let kept = order
      .map(id => nodesById.get(id))
      .filter(n => levelOf.get(n.id) <= maxDepth)
      .map(n => ({ ...n, level: levelOf.get(n.id) }));
    if (kept.length > maxNodes) {
      kept = kept
        .map((n, i) => ({ n, i }))
        .sort((a, b) => a.n.level - b.n.level || b.n.importance - a.n.importance || a.i - b.i)
        .slice(0, maxNodes)
        .sort((a, b) => a.i - b.i)
        .map(({ n }) => n);
    }

    // 5. Backfill leaf concepts with their details/examples from the knowledge base
    if (enrichChildren > 0) {
      kept = this.enrichMindmapFromKnowledge(kept, knowledgeJson, { maxDepth, maxNodes, perNode: enrichChildren });
    }

    // 6. Children pointers and edges
    const nodesList = [root, ...kept];
    const byId = new Map(nodesList.map(n => [n.id, n]));
    nodesList.forEach(n => { n.children = []; });
    kept.forEach(n => byId.get(n.parentId)?.children.push(n.id));

    const edgesList = kept.map(n => ({ id: `edge-${n.parentId}-${n.id}`, source: n.parentId, target: n.id, type: 'contains' }));
    const edgeSet = new Set(edgesList.map(e => `${e.source}->${e.target}`));
    const rawLinks = [
      ...(Array.isArray(rawMindmap.crossLinks) ? rawMindmap.crossLinks : []),
      ...(Array.isArray(rawMindmap.edges) ? rawMindmap.edges.filter(e => e && e.type && e.type !== 'contains') : [])
    ];
    rawLinks.forEach(e => {
      if (!e) return;
      const source = resolve(toId(e.source));
      const target = resolve(toId(e.target));
      if (source === target || !byId.has(source) || !byId.has(target)) return;
      if (edgeSet.has(`${source}->${target}`) || edgeSet.has(`${target}->${source}`)) return;
      edgeSet.add(`${source}->${target}`);
      edgesList.push({ id: `edge-x-${source}-${target}`, source, target, type: e.type || 'related_to' });
    });

    return {
      rootLabel: root.label,
      root,
      nodes: nodesList,
      edges: edgesList,
      metadata: {
        totalNodes: nodesList.length,
        maxDepth: kept.reduce((m, n) => Math.max(m, n.level), 0),
        depth: profile.key,
        depthLabel: profile.label,
        defaultVisibleLevel
      }
    };
  }

  /**
   * Attach concept details/examples from the knowledge base under matching leaf nodes
   */
  enrichMindmapFromKnowledge(nodes, knowledgeJson, { maxDepth, maxNodes, perNode }) {
    const concepts = Array.isArray(knowledgeJson.concepts) ? knowledgeJson.concepts : [];
    if (concepts.length === 0 || nodes.length >= maxNodes) return nodes;

    const norm = (s) => String(s || '').toLowerCase().replace(/\s+/g, ' ').trim();
    const hasChildren = new Set(nodes.map(n => n.parentId));
    const existingLabels = new Set(nodes.map(n => norm(n.label)));
    const result = [...nodes];

    for (const node of nodes) {
      if (result.length >= maxNodes) break;
      if (hasChildren.has(node.id) || node.level >= maxDepth) continue;

      const label = norm(node.label);
      const concept = concepts.find(c => {
        const name = norm(c.name);
        return name.length >= 3 && (name === label || (name.length >= 6 && label.includes(name)));
      });
      if (!concept) continue;

      const extras = [
        ...(concept.details || []).map(text => ({ text, type: 'fact' })),
        ...(concept.examples || []).map(text => ({ text, type: 'example' }))
      ].filter(x => typeof x.text === 'string' && x.text.trim() && !existingLabels.has(norm(x.text)));

      extras.slice(0, perNode).forEach((x, i) => {
        if (result.length >= maxNodes) return;
        const text = x.text.trim();
        existingLabels.add(norm(text));
        result.push({
          id: `${node.id}-kb${i + 1}`,
          parentId: node.id,
          label: text.length > 70 ? `${text.slice(0, 67)}...` : text,
          shortLabel: text.slice(0, 25),
          type: x.type,
          summary: text,
          importance: Math.max(1, node.importance - 1),
          level: node.level + 1,
          subDetails: [],
          source: node.source,
          children: []
        });
      });
    }
    return result;
  }

  /**
   * Fallback mindmap built straight from the knowledge base (used when every AI provider fails).
   * Goes through the same normalizer so it respects the depth tier as well.
   */
  generateDomainFallbackMindmap(knowledgeJson = {}) {
    const profile = resolveDepth(knowledgeJson.analysisDepth);
    const title = (knowledgeJson.title || "BÀI HỌC TỔNG HỢP").toUpperCase();
    const concepts = Array.isArray(knowledgeJson.concepts) ? knowledgeJson.concepts : [];
    const sections = (Array.isArray(knowledgeJson.sections) && knowledgeJson.sections.length > 0)
      ? knowledgeJson.sections
      : (knowledgeJson.topics || ["Khái niệm chính", "Cấu trúc & Nguyên lý", "Ứng dụng & Bài tập"]).map(t => ({ title: t, keyPoints: [] }));

    const nodes = [];
    const conceptNodeId = new Map();
    const lower = (s) => String(s || '').toLowerCase();

    // Assign each concept to the section it came from, otherwise spread them evenly
    const buckets = sections.map(() => []);
    concepts.forEach((c, i) => {
      const sec = lower(c.source?.section);
      let idx = sec ? sections.findIndex(s => lower(s.title).includes(sec) || sec.includes(lower(s.title))) : -1;
      if (idx === -1) idx = i % sections.length;
      buckets[idx].push(c);
    });

    sections.forEach((s, sIdx) => {
      const secId = `n${sIdx + 1}`;
      const secTitle = s.title || `Mục ${sIdx + 1}`;
      nodes.push({ id: secId, parentId: 'root-node', label: secTitle, type: 'subtopic', summary: s.summary || `Chủ đề chính: ${secTitle}`, importance: 4, source: { section: secTitle } });

      let secConcepts = buckets[sIdx];
      if (profile.key === 'quick') {
        secConcepts = [...secConcepts].sort((a, b) => (b.importance || 3) - (a.importance || 3)).slice(0, 3);
      }
      secConcepts.forEach((c, cIdx) => {
        const id = `${secId}-${cIdx + 1}`;
        conceptNodeId.set(c.id, id);
        nodes.push({ id, parentId: secId, label: c.name || `Ý chính ${cIdx + 1}`, type: c.type || 'concept', summary: c.description || '', importance: c.importance || 3, source: c.source || { section: secTitle } });
      });

      const pointLimit = profile.key === 'quick' ? (secConcepts.length ? 0 : 3) : (profile.key === 'deep' ? 8 : (secConcepts.length ? 2 : 5));
      (s.keyPoints || []).slice(0, pointLimit).forEach((pt, pIdx) => {
        nodes.push({ id: `${secId}-p${pIdx + 1}`, parentId: secId, label: String(pt).length > 70 ? `${String(pt).slice(0, 67)}...` : String(pt), type: 'fact', summary: String(pt), importance: 2, source: { section: secTitle } });
      });
    });

    const misconceptions = Array.isArray(knowledgeJson.misconceptions) ? knowledgeJson.misconceptions : [];
    if (profile.key !== 'quick' && misconceptions.length > 0) {
      nodes.push({ id: 'n-warn', parentId: 'root-node', label: 'Bẫy thường gặp & Lưu ý', type: 'warning', summary: 'Những hiểu sai phổ biến và cách hiểu đúng', importance: 4 });
      misconceptions.forEach((m, i) => {
        nodes.push({ id: `n-warn-${i + 1}`, parentId: 'n-warn', label: m.misconception, type: 'warning', summary: m.correction || '', importance: 3 });
      });
    }

    const crossLinks = (knowledgeJson.relationships || [])
      .filter(r => conceptNodeId.has(r.source) && conceptNodeId.has(r.target))
      .map(r => ({ source: conceptNodeId.get(r.source), target: conceptNodeId.get(r.target), type: r.type || 'related_to' }));

    return this.normalizeAndValidateMindmap(
      { root: { id: 'root-node', label: title, summary: knowledgeJson.summary || `Sơ đồ tư duy bài học ${title}` }, nodes, crossLinks },
      knowledgeJson
    );
  }

  /**
   * Generate Flashcards from Knowledge Base JSON (Supporting User Controls & Source Grounding)
   */
  async generateFlashcards(knowledgeJson, userSettings = {}) {
    const targetCount = userSettings.flashcardCount || resolveDepth(userSettings.depth || knowledgeJson?.analysisDepth).flashcards.count;
    const cacheKey = AICache.generateKey(JSON.stringify(knowledgeJson) + JSON.stringify(userSettings), 'flashcards', this.mode, PROMPT_VERSIONS.FLASHCARD_GENERATION);
    const cached = aiCache.get(cacheKey);
    if (cached && Array.isArray(cached) && cached.length >= 5) return cached;

    try {
      const result = await this.executeWithFallback('flashcards', (provider) => provider.generateFlashcards(knowledgeJson, userSettings));
      if (Array.isArray(result) && result.length >= 5) {
        aiCache.set(cacheKey, result);
        return result;
      }
    } catch (err) {
      console.warn("⚠️ [AI Router] Flashcards generation AI fallback activated:", err.message);
    }

    // Dynamic Source-Grounded Fallback Generator (Ensuring Front & Back are distinct and not redundant)
    const concepts = knowledgeJson.concepts || [];
    const title = knowledgeJson.title || "Tài liệu học tập";
    const cards = [];

    concepts.slice(0, targetCount).forEach((c, i) => {
      const name = (c.name || `Thuật ngữ ${i + 1}`).trim();
      const desc = (c.description || name).trim();
      
      // Determine if name is just a substring of desc
      const isSubstring = desc.toLowerCase().startsWith(name.toLowerCase());
      const remainingDesc = isSubstring ? desc.slice(name.length).replace(/^[\s\:\-\=]+/, '').trim() : desc;

      let frontText = "";
      let backText = "";

      if (i % 3 === 0 && desc.length > 20) {
        // Cloze / Fill-in-the-blank type card
        const words = desc.split(' ');
        const pickIdx = Math.floor(words.length / 2);
        const targetWord = words[pickIdx] || "replace me";
        const clozeSentence = words.map((w, idx) => idx === pickIdx ? "______" : w).join(' ');

        frontText = `Điền từ thích hợp vào chỗ trống trong bài học:\n"${clozeSentence}"`;
        backText = `Từ cần điền: "${targetWord}"\n\nNội dung đầy đủ: ${desc}`;
      } else if (remainingDesc.length > 10) {
        // Definition / Explanation card
        frontText = `Khái niệm / Quy định: "${name}"`;
        backText = remainingDesc.charAt(0).toUpperCase() + remainingDesc.slice(1);
      } else {
        // General Concept Question
        frontText = `Ý nghĩa và ứng dụng của "${name}" trong tài liệu "${title}" là gì?`;
        backText = desc;
      }

      cards.push({
        id: `fc_${String(i + 1).padStart(3, '0')}`,
        type: i % 3 === 0 ? "cloze" : (i % 2 === 0 ? "definition" : "concept"),
        topicId: knowledgeJson.topics?.[0] || "topic_001",
        conceptId: c.id || `concept_${i + 1}`,
        front: frontText,
        back: backText,
        hint: c.type || "Kiến thức trọng tâm",
        difficulty: userSettings.difficulty && userSettings.difficulty !== 'mixed' ? userSettings.difficulty : (i % 3 === 0 ? "hard" : (i % 2 === 0 ? "medium" : "easy")),
        importance: c.importance || 3,
        tags: [title.toLowerCase().slice(0, 15), "study-mind"],
        source: c.source || { documentId: knowledgeJson.id || 'doc-1', page: 1, section: title }
      });
    });

    while (cards.length < Math.min(10, targetCount)) {
      const idx = cards.length + 1;
      cards.push({
        id: `fc_${String(idx).padStart(3, '0')}`,
        type: "concept",
        topicId: "topic_001",
        conceptId: `concept_${idx}`,
        front: `Điểm trọng tâm số ${idx} cần lưu ý trong tài liệu "${title}" là gì?`,
        back: `Kiến thức cốt lõi mục ${idx} hỗ trợ củng cố sự hiểu biết tổng quan về bài học.`,
        hint: "Gợi ý ôn tập",
        difficulty: "medium",
        importance: 3,
        tags: ["study-mind"],
        source: { documentId: knowledgeJson.id || 'doc-1', page: 1, section: "Tổng quan" }
      });
    }

    aiCache.set(cacheKey, cards);
    return cards;
  }

  /**
   * Generate Quiz from Knowledge Base JSON (Supporting User Controls & Source Grounding)
   */
  async generateQuiz(knowledgeJson, userSettings = {}) {
    const targetCount = userSettings.quizCount || resolveDepth(userSettings.depth || knowledgeJson?.analysisDepth).quiz.count;
    const cacheKey = AICache.generateKey(JSON.stringify(knowledgeJson) + JSON.stringify(userSettings), 'quiz', this.mode, PROMPT_VERSIONS.QUIZ_GENERATION);
    const cached = aiCache.get(cacheKey);
    if (cached && cached.questions && cached.questions.length >= 5) return cached;

    try {
      const result = await this.executeWithFallback('quiz', (provider) => provider.generateQuiz(knowledgeJson, userSettings));
      if (result && Array.isArray(result.questions) && result.questions.length >= 5) {
        aiCache.set(cacheKey, result);
        return result;
      }
    } catch (err) {
      console.warn("⚠️ [AI Router] Quiz generation AI fallback activated:", err.message);
    }

    // Dynamic Source-Grounded Quiz Fallback Generator
    const concepts = knowledgeJson.concepts || [];
    const title = knowledgeJson.title || "Tài liệu học tập";
    const questions = [];

    concepts.slice(0, targetCount).forEach((c, i) => {
      const name = (c.name || `Khái niệm ${i + 1}`).trim();
      const desc = (c.description || name).trim();
      questions.push({
        id: `q_${String(i + 1).padStart(3, '0')}`,
        type: "multiple_choice",
        topicId: knowledgeJson.topics?.[0] || "topic_001",
        conceptId: c.id || `concept_${i + 1}`,
        questionNumber: i + 1,
        questionText: `Nhận định nào sau đây mô tả ĐÚNG nhất về "${name}" trong bài học?`,
        options: [
          `A. ${desc.length > 110 ? desc.slice(0, 107) + '...' : desc}`,
          `B. ${name} không có vai trò nào được đề cập trong nội dung tài liệu.`,
          `C. ${name} thuộc về một cấu trúc/quy trình thử nghiệm khác.`,
          `D. Tất cả các phương án trên đều sai.`
        ],
        correctIndex: 0,
        explanation: `Đáp án A chính xác! Dựa trên tài liệu "${title}": ${desc}`,
        difficulty: userSettings.difficulty && userSettings.difficulty !== 'mixed' ? userSettings.difficulty : (i % 3 === 0 ? "hard" : (i % 2 === 0 ? "medium" : "easy")),
        importance: c.importance || 3,
        source: c.source || { documentId: knowledgeJson.id || 'doc-1', page: 1, section: title }
      });
    });

    while (questions.length < Math.min(10, targetCount)) {
      const idx = questions.length + 1;
      questions.push({
        id: `q_${String(idx).padStart(3, '0')}`,
        type: "multiple_choice",
        topicId: "topic_001",
        conceptId: `concept_${idx}`,
        questionNumber: idx,
        questionText: `Trong bài học "${title}", nhận định nào sau đây là ĐÚNG về mục ${idx}?`,
        options: [
          `A. Kiến thức mục ${idx} đóng vai trò cốt lõi trong việc hình thành hiểu biết tổng quan bài học.`,
          `B. Mục ${idx} không được đề cập trong nội dung tài liệu.`,
          `C. Mục ${idx} chỉ áp dụng cho bài tập lý thuyết đơn giản.`,
          `D. Cả B và C đều đúng.`
        ],
        correctIndex: 0,
        explanation: `Đáp án A chính xác! Đây là phần kiến thức quan trọng trích xuất từ tài liệu học tập.`,
        difficulty: "medium",
        importance: 3,
        source: { documentId: knowledgeJson.id || 'doc-1', page: 1, section: "Tổng quan" }
      });
    }

    const quizObj = {
      title: `Đề kiểm tra trắc nghiệm AI: ${title}`,
      subject: knowledgeJson.topics?.[0] || title,
      timeLimitMinutes: Math.min(60, Math.max(5, Math.ceil(questions.length * 1.2))),
      questions
    };

    aiCache.set(cacheKey, quizObj);
    return quizObj;
  }

  /**
   * Read the text out of an image / scanned PDF with Gemini vision. Returns '' when unavailable.
   */
  async transcribeFile(filePath, mimeType) {
    if (!this.gemini?.genAI) return '';
    try {
      return await this.gemini.transcribeFile(filePath, mimeType);
    } catch (err) {
      console.warn("⚠️ [AI Router] Gemini transcription failed:", err.message);
      return '';
    }
  }

  /**
   * Prerequisite analysis ("Kiến thức Tiên quyết") — reads the raw document so it can spot what the text assumes
   * without explaining. Falls back to the knowledge base's prerequisite list when every provider fails.
   */
  async generatePrerequisites(title, content, depth, knowledgeJson = null) {
    const depthKey = resolveDepth(depth || knowledgeJson?.analysisDepth).key;
    const text = String(content || '').trim() || knowledgeJson?.summary || '';
    const cacheKey = AICache.generateKey(`${title}\n${text}`, `prerequisites_${depthKey}`, this.mode, PROMPT_VERSIONS.PREREQUISITE_ANALYSIS);
    const cached = aiCache.get(cacheKey);
    if (cached) return cached;

    try {
      const result = await this.executeWithFallback('long_document_analysis', (provider) => provider.generatePrerequisites(title, text, depthKey));
      result.analysisDepth = depthKey;
      aiCache.set(cacheKey, result);
      return result;
    } catch (err) {
      console.warn("⚠️ [AI Router] Prerequisite analysis fallback activated:", err.message);
      return this.generateFallbackPrerequisites(title, knowledgeJson, depthKey);
    }
  }

  generateFallbackPrerequisites(title, knowledgeJson, depthKey) {
    const names = Array.isArray(knowledgeJson?.prerequisites) ? knowledgeJson.prerequisites.filter(Boolean) : [];
    const prerequisites = names.map((name, i) => ({
      id: `pre-${i + 1}`,
      concept: String(name),
      priority: i < 2 ? 'critical' : 'recommended',
      level: 'Kiến thức nền',
      whyNeeded: `Tài liệu "${title}" sử dụng kiến thức này mà không giải thích lại.`,
      consequenceIfMissing: 'Có thể khó theo kịp các phần sử dụng kiến thức này.'
    }));
    const core = [...(knowledgeJson?.concepts || [])].sort((a, b) => (b.importance || 3) - (a.importance || 3))[0];
    const sections = (knowledgeJson?.sections || []).map(s => s.title).filter(Boolean);

    return {
      isFallback: true,
      analysisDepth: depthKey,
      documentTitle: title,
      subjectArea: knowledgeJson?.topics?.[0] || 'Chưa xác định',
      targetAudienceLevel: 'Chưa xác định',
      overallReadinessNote: prerequisites.length
        ? 'Phân tích sơ bộ từ nội dung tài liệu. Bấm "Phân tích lại" để AI đánh giá chi tiết và tạo bài test chẩn đoán.'
        : 'Chưa phân tích được kiến thức tiên quyết. Bấm "Phân tích lại" để thử lại.',
      prerequisites,
      dependencyGraph: [],
      cognitiveBottleneck: core ? { concept: core.name, description: core.description || '', advice: 'Dành thời gian đọc kỹ phần này trước khi đi tiếp.' } : null,
      learningPath: [
        { step: 1, type: 'prerequisite', title: 'Ôn lại kiến thức nền', description: prerequisites.length ? prerequisites.map(p => p.concept).join(', ') : 'Rà soát lại các thuật ngữ chưa quen trong tài liệu.' },
        { step: 2, type: 'core_learning', title: 'Học nội dung trọng tâm', description: sections.slice(0, 4).join(' → ') || 'Đọc lần lượt các mục chính của tài liệu.' },
        { step: 3, type: 'advanced_application', title: 'Vận dụng và kiểm tra', description: 'Làm flashcard và trắc nghiệm để củng cố.' }
      ],
      quickBridgeSummary: [],
      diagnosticPreTest: { title: 'Bài test chẩn đoán độ sẵn sàng đầu vào', instructions: '', passScore: 75, questions: [] }
    };
  }

  /**
   * Chat assistant query
   */
  async chat(docTitle, docContext, userQuestion, chatHistory = []) {
    return await this.executeWithFallback('chat', (provider) => provider.chat(docTitle, docContext, userQuestion, chatHistory));
  }

  /**
   * Perform Multi-Document Knowledge Fusion with AI & Fallback
   */
  async analyzeFusion(documents) {
    const docIds = documents.map(d => d.id).sort().join('_');
    const cacheKey = AICache.generateKey(docIds, 'knowledge_fusion', this.mode, PROMPT_VERSIONS.KNOWLEDGE_FUSION);
    const cached = aiCache.get(cacheKey);
    if (cached) return cached;

    try {
      const result = await this.executeWithFallback('long_document_analysis', (provider) => provider.analyzeFusion(documents));
      if (result && result.unifiedSummary && Array.isArray(result.commonConcepts)) {
        aiCache.set(cacheKey, result);
        return result;
      }
    } catch (err) {
      console.warn("⚠️ [AI Router] Knowledge Fusion AI fallback activated:", err.message);
    }

    // Dynamic Document-Grounded Fallback Generator
    const fallbackResult = this.generateDomainFallbackFusion(documents);
    aiCache.set(cacheKey, fallbackResult);
    return fallbackResult;
  }

  generateDomainFallbackFusion(documents) {
    if (!Array.isArray(documents) || documents.length === 0) {
      return {
        fusionTitle: "Báo cáo Hợp nhất & Đối chiếu Đa Tài liệu",
        comparedDocs: [],
        unifiedSummary: "Vui lòng chọn ít nhất 2 tài liệu để tiến hành hợp nhất tri thức.",
        commonConcepts: [],
        uniqueInsights: [],
        conflicts: [],
        mergedMindmap: { rootLabel: "Mạng lưới Hợp nhất", nodes: [] }
      };
    }

    // Helper: normalize and extract structured data from each doc
    const parsedDocs = documents.map((doc, idx) => {
      const note = doc.studyPack?.note || doc.studyPack?.notes || {};
      const keyConcepts = Array.isArray(note.keyConcepts) ? note.keyConcepts : [];
      const sections = Array.isArray(note.sections) ? note.sections : [];
      const flashcards = Array.isArray(doc.studyPack?.flashcards) ? doc.studyPack.flashcards : [];
      const title = doc.title || `Tài liệu #${idx + 1}`;
      const summary = note.summary || doc.rawText || doc.summary || `Nội dung tổng quan tài liệu ${title}`;

      // Detect Academic Domain
      const combinedMeta = (title + ' ' + summary + ' ' + (doc.tags || []).join(' ') + ' ' + keyConcepts.map(c => c.term).join(' ')).toLowerCase();
      let domain = "Khoa học & Kỹ năng Tổng quát";
      let domainKey = "general";
      if (combinedMeta.includes('hóa học') || combinedMeta.includes('ester') || combinedMeta.includes('este') || combinedMeta.includes('nguyên tố') || combinedMeta.includes('bảng tuần hoàn') || combinedMeta.includes('phản ứng')) {
        domain = "Hóa học & Khoa học Vật chất";
        domainKey = "chemistry";
      } else if (combinedMeta.includes('tiếng anh') || combinedMeta.includes('thì') || combinedMeta.includes('tenses') || combinedMeta.includes('verb') || combinedMeta.includes('ngữ pháp') || combinedMeta.includes('english')) {
        domain = "Ngôn ngữ & Ngữ pháp Tiếng Anh";
        domainKey = "english";
      } else if (combinedMeta.includes('sinh học') || combinedMeta.includes('tế bào') || combinedMeta.includes('ty thể') || combinedMeta.includes('krebs') || combinedMeta.includes('adn') || combinedMeta.includes('chuyển hóa')) {
        domain = "Sinh học & Khoa học Đời sống";
        domainKey = "biology";
      } else if (combinedMeta.includes('vật lý') || combinedMeta.includes('sóng cơ') || combinedMeta.includes('năng lượng') || combinedMeta.includes('dao động') || combinedMeta.includes('cơ học')) {
        domain = "Vật lý & Cơ học Năng lượng";
        domainKey = "physics";
      } else if (combinedMeta.includes('linux') || combinedMeta.includes('vi') || combinedMeta.includes('nano') || combinedMeta.includes('terminal') || combinedMeta.includes('chmod') || combinedMeta.includes('tập tin')) {
        domain = "Công nghệ Thông tin & Hệ thống Linux";
        domainKey = "tech";
      }

      return {
        id: doc.id,
        title,
        summary,
        keyConcepts,
        sections,
        flashcards,
        domain,
        domainKey
      };
    });

    const isSameDomain = parsedDocs.every(d => d.domainKey === parsedDocs[0].domainKey);
    const domainNames = Array.from(new Set(parsedDocs.map(d => d.domain)));
    const docTitles = parsedDocs.map(d => `"${d.title}"`);

    // ==========================================
    // 1. COMMON CONCEPTS & INTERDISCIPLINARY BRIDGES
    // ==========================================
    const commonConcepts = [];

    if (isSameDomain) {
      // INTRA-DOMAIN SYNTHESIS
      const allTerms = parsedDocs.flatMap(d => d.keyConcepts.map(c => ({ ...c, docTitle: d.title })));
      const topConcepts = allTerms.slice(0, 3);

      commonConcepts.push({
        concept: `Quy luật Hệ thống & Khung Lý thuyết Cốt lõi (${domainNames[0]})`,
        definition: `Các tài liệu đều tập trung xây dựng nền tảng từ "${topConcepts[0]?.term || 'Khái niệm chính'}" và các định luật liên quan. Kiến thức được hệ thống hóa theo các bậc từ nhận biết định nghĩa nền tảng đến vận dụng giải bài tập thực tiễn.`,
        sources: parsedDocs.map(d => d.title)
      });

      commonConcepts.push({
        concept: `Nguyên lý Phân loại & Mối tương quan Cấu trúc`,
        definition: `Hệ thống phân tách kiến thức thành các phân nhóm rõ ràng (như các bảng phân loại, chu kỳ, hoặc các mốc quy tắc). Cấu trúc này giúp người học không bị học vẹt mà nắm chắc logic liên kết giữa từng thành phần.`,
        sources: parsedDocs.map(d => d.title)
      });

      commonConcepts.push({
        concept: `Quy trình Nhận diện & Phương pháp Vận dụng Thực hành`,
        definition: `Sự kết hợp giữa lý thuyết và các dấu hiệu nhận biết (qua công thức, ví dụ minh họa và từ khóa mẫu) giúp củng cố phản xạ suy luận logic khi gặp các dạng câu hỏi vận dụng cao.`,
        sources: parsedDocs.map(d => d.title)
      });
    } else {
      // CROSS-DISCIPLINARY / INTERDISCIPLINARY SYNTHESIS (e.g. English vs Chemistry, Biology vs Tech)
      commonConcepts.push({
        concept: `Mô hình Phân loại Ma trận 2 Chiều (Two-Dimensional Matrix Taxonomy)`,
        definition: `Trong ${parsedDocs[0].domain} (${parsedDocs[0].title}), kiến thức được tổ chức theo trục tọa độ logic (ví dụ: mốc thời gian × thể hành động; hoặc chu kỳ × nhóm nguyên tố). Tương tự, ${parsedDocs[1].domain} (${parsedDocs[1].title}) cũng phân loại các phần tử dựa trên các thuộc tính giao cắt. Cả hai đều đòi hỏi người học tư duy theo bảng ma trận để suy luận tính chất mà không cần ghi nhớ đơn lẻ.`,
        sources: parsedDocs.map(d => d.title)
      });

      commonConcepts.push({
        concept: `Nguyên lý Cú pháp Hình thức & Mã hóa Ký hiệu (Formal Syntax & Symbolic Notation)`,
        definition: `Cả hai lĩnh vực đều áp dụng hệ thống ký hiệu chuẩn hóa nghiêm ngặt: Một bên sử dụng công thức ngữ pháp trừu tượng (như Chủ ngữ S + Trợ động từ Aux + Động từ V3/ed + Tân ngữ O); bên còn lại dùng công thức phân tử, ký hiệu nguyên tố và phương trình phản ứng hóa/sinh. Sự sai lệch của một ký tự đều làm biến đổi hoàn toàn ngữ nghĩa hoặc bản chất phản ứng.`,
        sources: parsedDocs.map(d => d.title)
      });

      commonConcepts.push({
        concept: `Cầu nối Thuật ngữ Khoa học Song ngữ (Bilingual STEM Terminology Integration)`,
        definition: `Tích hợp năng lực ngôn ngữ với kiến thức khoa học chuyên sâu: Việc đối chiếu các thuật ngữ tiếng Anh học thuật với các khái niệm chuyên ngành khoa học tự nhiên (như 'Periodic Table' - Bảng tuần hoàn, 'Atomic Number' - Số hiệu nguyên tử, 'Hydrolysis' - Thủy phân, 'Synthesis' - Hợp nhất) giúp học viên phát triển tư duy đọc hiểu tài liệu quốc tế.`,
        sources: parsedDocs.map(d => d.title)
      });

      commonConcepts.push({
        concept: `Tư duy Logic Điều kiện & Hệ quả (Causality & Prerequisite Rules)`,
        definition: `Cả hai môn học đều vận hành theo cấu trúc điều kiện tiên quyết: Trong ngôn ngữ, mốc thời gian quá khứ kích hoạt quy tắc lùi thì và trợ động từ; trong khoa học, điều kiện xúc tác, nhiệt độ và cấu hình electron quyết định chiều hướng và vận tốc của quá trình biến đổi.`,
        sources: parsedDocs.map(d => d.title)
      });
    }

    // ==========================================
    // 2. SIDE-BY-SIDE UNIQUE INSIGHTS
    // ==========================================
    const uniqueInsights = parsedDocs.map((doc, idx) => {
      const insights = [];

      // Extract from keyConcepts
      if (doc.keyConcepts.length > 0) {
        doc.keyConcepts.slice(0, 3).forEach(c => {
          insights.push(`Khái niệm độc quyền: [${c.term}] — ${c.definition}`);
        });
      }

      // Extract from sections
      if (doc.sections.length > 0) {
        doc.sections.slice(0, 2).forEach(s => {
          const pt = (s.points || s.keyPoints || [])[0] || s.content;
          if (pt) {
            insights.push(`Trọng tâm chuyên đề [${s.heading}]: ${pt}`);
          }
        });
      }

      if (insights.length < 3) {
        insights.push(`Đặc thù lĩnh vực: Thuộc chuyên đề ${doc.domain}, cung cấp hệ thống bài tập và phương pháp tư duy độc lập.`);
      }

      return {
        docId: doc.id,
        docTitle: doc.title,
        domain: doc.domain,
        insights: insights.slice(0, 4)
      };
    });

    // ==========================================
    // 3. CONFLICT RADAR & PEDAGOGICAL PITFALLS
    // ==========================================
    const conflicts = [];
    const docA = parsedDocs[0];
    const docB = parsedDocs[1] || parsedDocs[0];

    if (isSameDomain) {
      conflicts.push({
        id: "conf-1",
        topic: `Chênh lệch Phạm vi Tiếp cận: Tổng quan Định tính vs Chi tiết Định lượng`,
        docA: {
          id: docA.id,
          title: docA.title,
          statement: `Tập trung vào hệ thống nguyên lý nền tảng và các nhận diện cơ bản: "${docA.summary.slice(0, 90)}..."`
        },
        docB: {
          id: docB.id,
          title: docB.title,
          statement: `Mở rộng vào các nhánh chuyên sâu, quy tắc mở rộng và dạng bài phức tạp: "${docB.summary.slice(0, 90)}..."`
        },
        explanation: `Tài liệu ${docA.title} đóng vai trò đặt nền móng kiến thức tổng quát, trong khi tài liệu ${docB.title} đòi hỏi kỹ năng vận dụng ở cấp độ cao hơn. Người học dễ cảm thấy số lượng quy tắc bị 'quá tải' nếu không phân tầng thứ tự tiếp cận.`,
        recommendation: `Ôn tập chắc các khái niệm nhận biết ở "${docA.title}" trước, sau đó mới dùng "${docB.title}" để luyện bài tập tổng hợp và bẫy ngoại lệ.`
      });
    } else {
      conflicts.push({
        id: "conf-1",
        topic: `Cảnh báo Bẫy Xung đột Ký hiệu học (Symbolic Ambiguity Hazard)`,
        docA: {
          id: docA.id,
          title: docA.title,
          statement: `Sử dụng các ký hiệu quy ước theo hệ thống ${docA.domain} (ví dụ: các chữ cái viết tắt đại diện cho thành phần câu hoặc nguyên tố).`
        },
        docB: {
          id: docB.id,
          title: docB.title,
          statement: `Sử dụng cùng các ký tự chữ cái nhưng đại diện cho đại lượng vật chất hoặc quy tắc hoàn toàn khác trong ${docB.domain}.`
        },
        explanation: `Sự trùng lặp hình thức ký hiệu giữa các môn học (ví dụ: 'S' trong Tiếng Anh là Subject/Chủ ngữ, còn 'S' trong Hóa học là Sulfur/Lưu huỳnh; 'V' trong Tiếng Anh là Verb/Động từ, còn 'V' trong Khoa học là Thể tích hoặc Vận tốc) là bẫy nhận thức phổ biến khi ghi chép cùng một cuốn sổ tay học tập.`,
        recommendation: `Sử dụng quy ước màu mực hoặc ký hiệu tiền tố riêng biệt cho từng môn học để tránh nhầm lẫn vô thức khi tra cứu nhanh.`
      });

      conflicts.push({
        id: "conf-2",
        topic: `Khác biệt Bản chất: Quy ước Nhân tạo (Ngôn ngữ) vs Quy luật Tự nhiên (Khoa học)`,
        docA: {
          id: docA.id,
          title: docA.title,
          statement: `Vận hành dựa trên quy ước thói quen ngôn ngữ, luôn tồn tại các trường hợp bất quy tắc và ngoại lệ cần ghi nhớ theo ngữ cảnh.`
        },
        docB: {
          id: docB.id,
          title: docB.title,
          statement: `Vận hành dựa trên quy luật tự nhiên khách quan và cấu trúc nguyên tử bất biến, tuân thủ chặt chẽ định luật bảo toàn.`
        },
        explanation: `Tư duy học ngữ pháp đòi hỏi khả năng cảm thụ ngữ cảnh và ghi nhớ phản xạ (do ngôn ngữ phát triển theo văn hóa con người). Ngược lại, khoa học tự nhiên đòi hỏi tư duy nguyên nhân - kết quả và hiểu rõ bản chất cơ chế electron/phân tử. Nhầm lẫn phương pháp học sẽ khiến người học dễ nản lòng.`,
        recommendation: `Áp dụng phương pháp ghi nhớ ngắt quãng (Spaced Repetition) cho các cấu trúc ngôn ngữ và phương pháp giải thích bản chất Feynman cho các định luật khoa học.`
      });
    }

    // ==========================================
    // 4. MERGED MINDMAP NODES
    // ==========================================
    const mergedNodes = [
      { id: "m-root", label: `Mạng lưới Hợp nhất Kiến thức (${parsedDocs.length} tài liệu)`, type: "topic", importance: 5, level: 0 },
      { id: "m-common", label: `Kiến thức Chung & Giao thoa Phương pháp`, type: "subtopic", importance: 5, level: 1, parentId: "m-root" }
    ];

    commonConcepts.forEach((c, cIdx) => {
      mergedNodes.push({
        id: `m-c-${cIdx + 1}`,
        label: c.concept,
        type: "concept",
        importance: 4,
        level: 2,
        parentId: "m-common",
        detail: c.sources.join(', ')
      });
    });

    parsedDocs.forEach((d, dIdx) => {
      const docSubId = `m-doc-${dIdx + 1}`;
      mergedNodes.push({
        id: docSubId,
        label: `Trục Chuyên sâu: ${d.title}`,
        type: "subtopic",
        importance: 4,
        level: 1,
        parentId: "m-root"
      });

      if (d.keyConcepts.length > 0) {
        d.keyConcepts.slice(0, 3).forEach((kc, kcIdx) => {
          mergedNodes.push({
            id: `m-kc-${dIdx + 1}-${kcIdx + 1}`,
            label: `${kc.term}`,
            type: "fact",
            level: 2,
            parentId: docSubId,
            detail: kc.definition.slice(0, 45) + '...'
          });
        });
      } else if (d.sections.length > 0) {
        d.sections.slice(0, 3).forEach((sc, scIdx) => {
          mergedNodes.push({
            id: `m-sc-${dIdx + 1}-${scIdx + 1}`,
            label: `${sc.heading}`,
            type: "fact",
            level: 2,
            parentId: docSubId,
            detail: (sc.points || [])[0] || d.title
          });
        });
      }
    });

    mergedNodes.push({
      id: "m-conflicts",
      label: `⚠️ Radar Lưu ý & Bẫy Tư duy (${conflicts.length} cờ cảnh báo)`,
      type: "warning",
      importance: 5,
      level: 1,
      parentId: "m-root"
    });

    conflicts.forEach((conf, cfIdx) => {
      mergedNodes.push({
        id: `m-cf-${cfIdx + 1}`,
        label: conf.topic,
        type: "warning",
        level: 2,
        parentId: "m-conflicts",
        detail: conf.recommendation.slice(0, 50) + '...'
      });
    });

    // ==========================================
    // 5. UNIFIED SUMMARY REPORT
    // ==========================================
    const unifiedSummary = isSameDomain
      ? `Báo cáo phân tích đối chiếu chuyên sâu giữa các tài liệu trong cùng lĩnh vực ${domainNames.join(', ')} (${docTitles.join(', ')}). Hệ thống đã tiến hành gộp chuẩn hóa các quy luật nền tảng, bóc tách ${commonConcepts.length} trục tri thức cốt lõi và làm rõ sự phân cấp phạm vi kiến thức. Các điểm chênh lệch về độ sâu định lượng và góc nhìn vận dụng đã được đưa vào Radar Cảnh báo để hỗ trợ học viên xây dựng lộ trình tiếp cận logic nhất.`
      : `Báo cáo tổng hợp tri thức liên môn (Interdisciplinary Synthesis) đối chiếu giữa các lĩnh vực ${domainNames.join(' và ')} (${docTitles.join(', ')}). Mặc dù thuộc các chuyên ngành khác biệt, hệ thống đã phát hiện ${commonConcepts.length} cầu nối tư duy sâu sắc về mô hình ma trận phân loại, nguyên lý chuẩn hóa cú pháp ký hiệu và sự tương thích của thuật ngữ song ngữ STEM. Các bẫy nhầm lẫn nhận thức và sự khác biệt giữa quy ước nhân tạo và định luật tự nhiên đã được định danh rõ trong Radar Cảnh báo giúp người học tối ưu hóa khả năng liên kết tri thức đa chiều.`;

    return {
      fusionTitle: `Báo cáo Hợp nhất & Đối chiếu Đa Tài liệu`,
      comparedDocs: parsedDocs.map(d => ({ id: d.id, title: d.title, domain: d.domain })),
      unifiedSummary,
      commonConcepts,
      uniqueInsights,
      conflicts,
      mergedMindmap: {
        rootLabel: `Mạng lưới Hợp nhất (${parsedDocs.length} tài liệu)`,
        nodes: mergedNodes
      }
    };
  }

  /**
   * Intelligent fallback knowledge generation when offline or rate-limited
   */
  generateDomainFallbackKnowledge(title, content) {
    const rawLines = content
      ? content.split('\n').map(l => l.trim()).filter(l => l.length > 5 && !l.startsWith('PK') && !l.startsWith('[Tài liệu'))
      : [];

    const topicHeading = rawLines.find(l => l.length > 5 && l.length < 90) || title;

    // Extract dynamic concepts directly from the document lines with smart title parsing
    const concepts = rawLines.slice(0, 15).map((line, idx) => {
      let conceptType = 'concept';
      if (line.toLowerCase().includes('định nghĩa') || line.toLowerCase().includes('khái niệm')) conceptType = 'definition';
      else if (line.toLowerCase().includes('ví dụ') || line.toLowerCase().includes('minh họa')) conceptType = 'example';
      else if (line.toLowerCase().includes('công thức') || line.includes('=')) conceptType = 'formula';
      else if (line.toLowerCase().includes('quy trình') || line.toLowerCase().includes('bước')) conceptType = 'process';

      let label = "";
      let description = line;

      // Smart title extraction: split on colon or dash if present
      if (line.includes(':')) {
        const parts = line.split(':');
        label = parts[0].replace(/^[\d\.\-\*\#\s\:\•\–]+/, '').trim();
        description = parts.slice(1).join(':').trim() || line;
      } else if (line.includes(' - ')) {
        const parts = line.split(' - ');
        label = parts[0].replace(/^[\d\.\-\*\#\s\:\•\–]+/, '').trim();
        description = parts.slice(1).join(' - ').trim() || line;
      } else {
        // Extract clean noun phrase or key title
        label = line.replace(/^[\d\.\-\*\#\s\:\•\–]+/, '').trim();
        if (label.length > 35) {
          // Take first 5 words as concept name
          const words = label.split(' ');
          label = words.slice(0, 5).join(' ');
        }
      }

      if (!label || label.length < 3) label = `Nội dung bài học ${idx + 1}`;

      return {
        id: `c-${idx + 1}`,
        name: label,
        type: conceptType,
        description,
        importance: idx < 3 ? 5 : (idx < 7 ? 4 : 3),
        source: { section: topicHeading }
      };
    });

    if (concepts.length === 0) {
      concepts.push({
        id: 'c-1',
        name: title,
        type: 'concept',
        description: `Tài liệu ${title} chứa thông tin bài học cốt lõi.`,
        importance: 5,
        source: { section: title }
      });
    }

    // Extract potential subtopics from headings
    const headings = Array.from(new Set(
      rawLines
        .filter(l => (l.length < 60 && (l.match(/^[\d\.\#\–\-]/) || l.includes(':') || l.toUpperCase() === l)))
        .map(l => l.replace(/^[\d\.\#\–\-\:\s]+/, '').trim())
        .filter(l => l.length > 3)
    )).slice(0, 5);

    const topics = headings.length > 0 ? headings : [topicHeading, "Kiến thức trọng tâm"];

    const relationships = concepts.slice(1).map((c, idx) => ({
      source: concepts[idx].id,
      target: c.id,
      type: 'depends_on'
    }));

    return {
      title,
      language: "Tiếng Việt",
      summary: `Tóm tắt nội dung trích xuất từ tài liệu "${title}": ${topicHeading}`,
      difficulty: "medium",
      topics,
      concepts,
      relationships,
      sections: topics.map(t => ({
        title: t,
        summary: `Tóm tắt chi tiết cho mục ${t}`,
        keyPoints: concepts.filter(c => c.description.toLowerCase().includes(t.toLowerCase())).map(c => c.description).slice(0, 3)
      })),
      keyTakeaways: concepts.slice(0, 5).map(c => c.description.slice(0, 90)),
      sources: [{ documentId: "doc-1", page: 1, section: "Trích xuất tài liệu" }]
    };
  }
}

export const aiRouter = new AIRouter();

