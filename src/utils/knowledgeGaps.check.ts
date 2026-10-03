// Self-check: node src/utils/knowledgeGaps.check.ts
import assert from 'node:assert/strict';
import { analyzeKnowledgeGaps } from './knowledgeGaps.ts';
import type { QuizHistoryRecord, StudyPack } from '../types';

const pack = {
  knowledgeBase: {
    concepts: [
      { id: 'c-xa', name: 'Xà phòng hóa', importance: 5 },
      { id: 'c-thuy', name: 'Thủy phân ester', importance: 4 },
      { id: 'c-ester', name: 'Ester', importance: 5 }
    ],
    relationships: [
      { source: 'c-xa', target: 'c-thuy', type: 'depends_on' },
      { source: 'c-ester', target: 'c-thuy', type: 'prerequisite_of' }
    ],
    misconceptions: [{ misconception: 'Thủy phân kiềm là thuận nghịch', correction: 'Một chiều', conceptId: 'c-xa' }]
  },
  prerequisites: {
    prerequisites: [
      { id: 'pre-1', concept: 'Cân bằng hóa học', priority: 'critical', level: 'Hóa lớp 10' },
      { id: 'pre-2', concept: 'Acid carboxylic', priority: 'critical', level: 'Hóa lớp 11' }
    ],
    // Free-text ends, written without diacritics on purpose
    dependencyGraph: [
      { from: 'Can bang hoa hoc', to: 'Thuy phan ester' },
      { from: 'Acid carboxylic', to: 'Ester' }
    ],
    quickBridgeSummary: [{ prerequisiteId: 'pre-1', concept: 'Cân bằng hóa học', quickReview: 'Le Chatelier...' }],
    diagnosticPreTest: { questions: [] },
    lastDiagnostic: {
      completedAt: '',
      results: [
        { questionId: 'd1', prerequisiteId: 'pre-1', correct: false },
        { questionId: 'd2', prerequisiteId: 'pre-2', correct: true }
      ]
    }
  },
  quiz: { questions: [{ id: 'q2', questionText: 'Phản ứng xà phòng hóa tạo ra sản phẩm gì?' }] },
  flashcards: [{ id: 'f1', front: 'Ester là gì?', back: '', difficulty: 'easy', lastRating: 'easy' }]
} as unknown as StudyPack;

const history: QuizHistoryRecord[] = [
  // newest first: q1 was wrong before, right now → must count as right
  { id: 'a2', score: 0, correctCount: 0, totalQuestions: 2, completedAt: '', results: [{ questionId: 'q1', conceptId: 'c-ester', correct: true }] },
  { id: 'a1', score: 0, correctCount: 0, totalQuestions: 2, completedAt: '', results: [
    { questionId: 'q1', conceptId: 'c-ester', correct: false },
    // no conceptId: resolved from the question text
    { questionId: 'q2', conceptId: null, correct: false }
  ] }
];

// 1. The user's example: wrong on "xà phòng hóa" traces through "thủy phân ester" to "cân bằng hóa học"
const a = analyzeKnowledgeGaps(pack, history);
assert.equal(a.nodes.get('c-ester')!.status, 'mastered', 'latest attempt wins + flashcard easy');
assert.equal(a.nodes.get('c-xa')!.status, 'weak', 'question resolved by name from its text');
assert.deepEqual(a.roots.map(r => r.node.id), ['pre-1']);
// pre-1 explains both the propagated error and its own wrong diagnostic answer
assert.deepEqual(a.roots[0].explains.map(n => n.id), ['c-xa', 'pre-1']);
assert.deepEqual(a.roots[0].paths, [['c-xa', 'c-thuy', 'pre-1'], ['pre-1']]);
assert.equal(a.roots[0].bridge, 'Le Chatelier...');

// 2. Mastered prerequisite is ruled out; untested ones are only suspects: the failed concept is the root
const noWeakPrereq = structuredClone(pack);
noWeakPrereq.prerequisites!.lastDiagnostic!.results = [{ questionId: 'd1', prerequisiteId: 'pre-1', correct: true }];
const b = analyzeKnowledgeGaps(noWeakPrereq, history);
assert.deepEqual(b.roots.map(r => r.node.id), ['c-xa']);
assert.deepEqual(b.roots[0].suspects.map(s => s.id), ['c-thuy']);
assert.equal(b.roots[0].misconceptions.length, 1);

// 3. No evidence at all: nothing to report
assert.equal(analyzeKnowledgeGaps(pack, []).roots.length, 1, 'diagnostic alone still finds pre-1');
const empty = structuredClone(pack);
delete empty.prerequisites!.lastDiagnostic;
empty.flashcards = [];
assert.equal(analyzeKnowledgeGaps(empty, []).roots.length, 0);

console.log('knowledge gap self-check passed');
