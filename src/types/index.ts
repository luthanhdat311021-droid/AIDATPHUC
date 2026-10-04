export interface User {
  id?: string;
  fullName: string;
  email?: string;
  membershipTier: string;
  avatarUrl: string;
  studyGoalHours?: number;
  currentStudyHours?: number;
  quizTargetCount?: number;
  currentQuizCount?: number;
  createdAt?: string;
}

export interface DocumentItem {
  id: string;
  title: string;
  fileType: string;
  fileSize?: string;
  pageCount?: number;
  duration?: string;
  updatedAt: string;
  status: 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  tags: string[];
  rawText?: string;
}

export interface SpacedRepetitionItem {
  id: string;
  title: string;
  memoryLevel: string;
  actionNeeded: string;
  buttonText: string;
  docId: string;
  lastReviewed?: string;
}

export interface Activity {
  id: string;
  text: string;
  time: string;
}

export interface UserStats {
  totalDocuments: number;
  weeklyDocAdded: number;
  flashcardProgress: string;
  retentionRatePercentage: number | null;
  averageQuizScore: string | null;
  quizScoreDiff: string | null;
  quizAttempts: number;
  weeklyQuizCount: { current: number; target: number };
  streakDays: number;
  recentDocuments: DocumentItem[];
  spacedRepetitionItems: SpacedRepetitionItem[];
  recentActivities: Activity[];
}

export interface NotesItem {
  label: string;
  text: string;
}

export interface NotesSection {
  heading: string;
  items: NotesItem[];
}

export interface AINotes {
  summaryTitle: string;
  sections: NotesSection[];
}

export type MindmapNodeType = 
  | 'topic' 
  | 'subtopic' 
  | 'concept' 
  | 'definition' 
  | 'process' 
  | 'method' 
  | 'formula' 
  | 'example' 
  | 'fact' 
  | 'comparison' 
  | 'advantage' 
  | 'disadvantage' 
  | 'application' 
  | 'warning' 
  | 'important';

export interface MindmapSource {
  documentId?: string;
  page?: number | null;
  section?: string;
  timestamp?: { start: number; end: number };
}

export interface MindmapNode {
  id: string;
  label: string;
  shortLabel?: string;
  type?: MindmapNodeType;
  summary?: string;
  detail?: string;
  importance?: number; // 1 to 5
  level?: number; // 0 (Root) to 6+
  children?: string[];
  subDetails?: string[];
  parentId?: string | null;
  source?: MindmapSource;
  isCollapsed?: boolean;
}

export interface MindmapEdge {
  id: string;
  source: string;
  target: string;
  type: 'contains' | 'depends_on' | 'related_to' | 'causes' | 'produces' | 'example_of' | 'type_of' | 'contrasts_with' | 'prerequisite_of' | 'used_for';
  label?: string;
}

export interface AIMindmap {
  rootLabel?: string;
  root?: MindmapNode;
  nodes: MindmapNode[];
  edges?: MindmapEdge[];
  metadata?: {
    totalNodes: number;
    maxDepth: number;
    nodeTypesCount?: Record<string, number>;
    depth?: 'quick' | 'standard' | 'deep';
    depthLabel?: string;
    defaultVisibleLevel?: number;
  };
}

export interface Flashcard {
  id: string;
  front: string;
  back: string;
  difficulty: 'easy' | 'medium' | 'hard';
  conceptId?: string | null;
  lastRating?: 'easy' | 'medium' | 'hard';
  lastReviewed?: string | null;
  nextReview?: string;
}

export interface QuizQuestion {
  id: string;
  questionNumber: number;
  questionText: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  conceptId?: string | null;
}

export interface AIQuiz {
  title: string;
  subject: string;
  timeLimitMinutes: number;
  questions: QuizQuestion[];
}

export interface Prerequisite {
  id: string;
  concept: string;
  priority: 'critical' | 'recommended';
  level: string;
  whyNeeded: string;
  consequenceIfMissing: string;
}

export interface PrerequisiteDependency {
  from: string;
  fromType: 'prerequisite' | 'core' | 'application';
  to: string;
  toType: 'prerequisite' | 'core' | 'application';
  relation: string;
}

export interface DiagnosticQuestion {
  id: string;
  testedPrerequisiteId: string | null;
  question: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

export interface PrerequisiteAnalysis {
  isFallback?: boolean;
  analysisDepth?: 'quick' | 'standard' | 'deep';
  documentTitle: string;
  subjectArea: string;
  targetAudienceLevel: string;
  overallReadinessNote: string;
  prerequisites: Prerequisite[];
  dependencyGraph: PrerequisiteDependency[];
  cognitiveBottleneck: { concept: string; description: string; advice: string } | null;
  learningPath: Array<{ step: number; type: 'prerequisite' | 'core_learning' | 'advanced_application'; title: string; description: string }>;
  quickBridgeSummary: Array<{ prerequisiteId: string | null; concept: string; quickReview: string }>;
  diagnosticPreTest: {
    title: string;
    instructions: string;
    passScore: number;
    questions: DiagnosticQuestion[];
  };
  lastDiagnostic?: {
    completedAt: string;
    results: Array<{ questionId: string; prerequisiteId: string | null; correct: boolean }>;
  };
}

// The part of the AI Knowledge Base the frontend reads (Knowledge Gap Map)
export interface KnowledgeBase {
  concepts?: Array<{ id: string; name: string; description?: string; importance?: number }>;
  relationships?: Array<{ source: string; target: string; type: string; description?: string }>;
  misconceptions?: Array<{ misconception: string; correction?: string; conceptId?: string | null }>;
}

export interface StudyPack {
  knowledgeBase?: KnowledgeBase | null;
  notes?: AINotes | null;
  mindmap?: AIMindmap | null;
  flashcards?: Flashcard[];
  quiz?: AIQuiz | null;
  prerequisites?: PrerequisiteAnalysis | null;
}

export interface ActiveDocumentData {
  document: DocumentItem;
  studyPack: StudyPack;
  quizHistory?: QuizHistoryRecord[];
}

export interface OutputOptions {
  notes: boolean;
  mindmap: boolean;
  flashcards: boolean;
  quiz: boolean;
}

export interface QuizHistoryRecord {
  id: string;
  score: number;
  correctCount: number;
  totalQuestions: number;
  completedAt: string;
  feedback?: string;
  results?: Array<{ questionId: string; conceptId: string | null; correct: boolean }>;
}

export interface LessonHistoryItem {
  id: string;
  userId?: string;
  title: string;
  fileType: string;
  fileSize?: string;
  pageCount?: number;
  duration?: string;
  updatedAt: string;
  status: 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  tags: string[];
  rawText?: string;
  studyPack?: StudyPack | null;
  quizHistory?: QuizHistoryRecord[];
}

export interface KnowledgeConflict {
  id: string;
  topic: string;
  docA: { id: string; title: string; statement: string };
  docB: { id: string; title: string; statement: string };
  explanation: string;
  recommendation: string;
}

export interface UniqueInsight {
  docId: string;
  docTitle: string;
  insights: string[];
}

export interface CommonConcept {
  concept: string;
  definition: string;
  sources: string[];
}

export interface KnowledgeFusionResult {
  fusionTitle: string;
  unifiedSummary: string;
  commonConcepts: CommonConcept[];
  uniqueInsights: UniqueInsight[];
  conflicts: KnowledgeConflict[];
  mergedMindmap?: AIMindmap;
  comparedDocs: { id: string; title: string }[];
}

export type TabType = 'dashboard' | 'import' | 'workspace' | 'prerequisite' | 'gaps' | 'mindmap' | 'flashcard' | 'quiz' | 'history' | 'fusion' | 'auth';

