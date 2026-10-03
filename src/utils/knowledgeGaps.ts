import type { QuizHistoryRecord, StudyPack } from '../types';

/**
 * Knowledge Gap Map: trace every wrong answer back through the concept dependency graph to the
 * deepest prerequisite the learner has also shown weakness in — the root gap, not the symptom.
 *
 * Evidence: latest quiz result per question, flashcard self-ratings (hard = wrong, easy = right)
 * and the prerequisite diagnostic test. A node is "mastered" when right > wrong, "weak" when it has
 * any evidence otherwise, "untested" with none. Mastered prerequisites are ruled out as causes.
 */

export type MasteryStatus = 'weak' | 'mastered' | 'untested';

export interface GapNode {
  id: string;
  label: string;
  kind: 'concept' | 'prerequisite';
  level?: string;
  description?: string;
  importance: number;
  wrong: number;
  right: number;
  status: MasteryStatus;
}

export interface RootGap {
  node: GapNode;
  /** Weak concepts whose errors trace back to this root (includes the root when it failed itself) */
  explains: GapNode[];
  /** One path per explained concept: [failed concept, ..., root] */
  paths: string[][];
  /** Untested prerequisites upstream that could also be involved — worth checking */
  suspects: GapNode[];
  misconceptions: Array<{ misconception: string; correction?: string }>;
  /** 2-minute bridge text when the root is a prerequisite */
  bridge?: string;
}

export interface GapAnalysis {
  nodes: Map<string, GapNode>;
  /** `from` needs `to` to be understood */
  requires: Array<{ from: string; to: string }>;
  roots: RootGap[];
  evidenceCount: number;
}

// Diacritic- and punctuation-insensitive key, so "Cân bằng hóa học" matches "can bang hoa hoc"
export const normalizeName = (s: string) =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

export function analyzeKnowledgeGaps(pack: StudyPack, quizHistory: QuizHistoryRecord[] = []): GapAnalysis {
  const nodes = new Map<string, GapNode>();
  const add = (id: string, label: string, kind: GapNode['kind'], extra: Partial<GapNode> = {}) => {
    if (!nodes.has(id)) nodes.set(id, { id, label, kind, importance: 3, wrong: 0, right: 0, status: 'untested', ...extra });
    return nodes.get(id)!;
  };

  const kb = pack.knowledgeBase || {};
  const pre = pack.prerequisites;
  (kb.concepts || []).forEach(c => add(c.id, c.name, 'concept', { description: c.description, importance: c.importance ?? 3 }));
  (pre?.prerequisites || []).forEach(p => add(p.id, p.concept, 'prerequisite', {
    level: p.level, description: p.whyNeeded, importance: p.priority === 'critical' ? 5 : 3
  }));

  // The AI writes dependency-graph ends as free text: exact name first, else the longest overlapping name
  const resolve = (name: string): string => {
    const key = normalizeName(name);
    if (!key) return '';
    let best = '';
    let bestLen = 0;
    for (const n of nodes.values()) {
      const k = normalizeName(n.label);
      if (k === key) return n.id;
      if (k.length >= 4 && key.length >= 4 && (k.includes(key) || key.includes(k)) && k.length > bestLen) {
        best = n.id;
        bestLen = k.length;
      }
    }
    return best || add(`name:${key}`, name, 'concept').id;
  };

  // Fallback for questions/cards without a usable conceptId: the longest concept name found in the text
  const conceptIn = (text: string): string => {
    const hay = ` ${normalizeName(text)} `;
    let best = '';
    let bestLen = 0;
    for (const n of nodes.values()) {
      const k = normalizeName(n.label);
      if (n.kind === 'concept' && k.length >= 3 && hay.includes(` ${k} `) && k.length > bestLen) {
        best = n.id;
        bestLen = k.length;
      }
    }
    return best;
  };

  const requires: GapAnalysis['requires'] = [];
  const linked = new Set<string>();
  const link = (from: string, to: string) => {
    const key = `${from}>${to}`;
    if (from === to || !nodes.has(from) || !nodes.has(to) || linked.has(key)) return;
    linked.add(key);
    requires.push({ from, to });
  };
  (kb.relationships || []).forEach(r => {
    if (r.type === 'depends_on' || r.type === 'type_of') link(r.source, r.target);
    else if (r.type === 'prerequisite_of') link(r.target, r.source);
  });
  (pre?.dependencyGraph || []).forEach(d => link(resolve(d.to), resolve(d.from)));

  let evidenceCount = 0;
  const record = (id: string, correct: boolean) => {
    const n = id ? nodes.get(id) : undefined;
    if (!n) return;
    if (correct) n.right++;
    else n.wrong++;
    evidenceCount++;
  };

  // quiz_history is newest first: the latest outcome per question wins, so re-learning clears a gap
  const answered = new Set<string>();
  const questionText = new Map((pack.quiz?.questions || []).map(q => [q.id, q.questionText]));
  for (const attempt of quizHistory) {
    for (const r of attempt.results || []) {
      if (answered.has(r.questionId)) continue;
      answered.add(r.questionId);
      record(r.conceptId && nodes.has(r.conceptId) ? r.conceptId : conceptIn(questionText.get(r.questionId) || ''), r.correct);
    }
  }
  for (const card of pack.flashcards || []) {
    if (card.lastRating !== 'hard' && card.lastRating !== 'easy') continue;
    record(card.conceptId && nodes.has(card.conceptId) ? card.conceptId : conceptIn(card.front), card.lastRating === 'easy');
  }
  (pre?.lastDiagnostic?.results || []).forEach(r => record(r.prerequisiteId || '', r.correct));

  for (const n of nodes.values()) {
    n.status = n.wrong === 0 && n.right === 0 ? 'untested' : n.right > n.wrong ? 'mastered' : 'weak';
  }

  const prereqsOf = new Map<string, string[]>();
  requires.forEach(e => prereqsOf.set(e.from, [...(prereqsOf.get(e.from) || []), e.to]));

  const found = new Map<string, { explains: Set<string>; paths: string[][]; suspects: Set<string> }>();
  const entry = (id: string) => {
    if (!found.has(id)) found.set(id, { explains: new Set(), paths: [], suspects: new Set() });
    return found.get(id)!;
  };

  for (const failed of [...nodes.values()].filter(n => n.status === 'weak')) {
    // BFS upstream; parent points back toward the failed concept. Mastered prerequisites are ruled out.
    const parent = new Map<string, string>([[failed.id, '']]);
    const queue = [failed.id];
    const weakUp: string[] = [];
    const untestedUp: string[] = [];
    while (queue.length) {
      const id = queue.shift()!;
      for (const p of prereqsOf.get(id) || []) {
        const status = nodes.get(p)!.status;
        if (parent.has(p) || status === 'mastered') continue;
        parent.set(p, id);
        queue.push(p);
        (status === 'weak' ? weakUp : untestedUp).push(p);
      }
    }

    const pathTo = (root: string) => {
      const path = [root];
      while (path[0] !== failed.id) path.unshift(parent.get(path[0])!);
      return path;
    };

    // Deepest weak prerequisites: those not sitting between the failed concept and another weak one
    const shadowed = new Set<string>();
    weakUp.forEach(w => pathTo(w).slice(0, -1).forEach(id => shadowed.add(id)));
    const roots = weakUp.filter(w => !shadowed.has(w));

    if (roots.length === 0) {
      // No upstream weakness on record: the gap is this concept; untested prerequisites stay suspects
      const e = entry(failed.id);
      e.explains.add(failed.id);
      e.paths.push([failed.id]);
      untestedUp.forEach(id => e.suspects.add(id));
    } else {
      roots.forEach(root => {
        const e = entry(root);
        e.explains.add(failed.id);
        e.paths.push(pathTo(root));
      });
    }
  }

  const byId = (ids: Iterable<string>) => [...ids].map(id => nodes.get(id)!);
  const roots: RootGap[] = [...found.entries()].map(([id, e]) => ({
    node: nodes.get(id)!,
    explains: byId(e.explains),
    paths: e.paths,
    suspects: byId(e.suspects).sort((a, b) => b.importance - a.importance).slice(0, 3),
    misconceptions: (kb.misconceptions || []).filter(m => m.conceptId === id),
    bridge: pre?.quickBridgeSummary?.find(b => b.prerequisiteId === id)?.quickReview
  }));
  roots.sort((a, b) =>
    b.explains.length - a.explains.length || b.node.wrong - a.node.wrong || b.node.importance - a.node.importance);

  return { nodes, requires, roots, evidenceCount };
}
