import React, { useEffect, useMemo, useState } from 'react';
import {
  Compass,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Loader2,
  RefreshCw,
  ArrowRight,
  Zap,
  Route,
  GitBranch,
  ClipboardCheck,
  BookOpen,
  Target
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { PrerequisiteAnalysis, PrerequisiteDependency } from '../../types';

const DEPTH_OPTIONS = ['Tóm lược nhanh', 'Tiêu chuẩn', 'Chuyên sâu'];
const DEPTH_LABEL: Record<string, string> = { quick: 'Tóm lược nhanh', standard: 'Tiêu chuẩn', deep: 'Chuyên sâu' };

const NODE_STYLE: Record<PrerequisiteDependency['fromType'], string> = {
  prerequisite: 'bg-amber-50 text-amber-900 border-amber-200',
  core: 'bg-teal-50 text-teal-900 border-teal-200',
  application: 'bg-indigo-50 text-indigo-900 border-indigo-200'
};

const PATH_BADGE: Record<string, { label: string; cls: string }> = {
  prerequisite: { label: 'Nền tảng', cls: 'bg-amber-100 text-amber-800' },
  core_learning: { label: 'Trọng tâm', cls: 'bg-teal-100 text-teal-800' },
  advanced_application: { label: 'Vận dụng', cls: 'bg-indigo-100 text-indigo-800' }
};

function SectionTitle({ icon: Icon, children }: { icon: any; children: React.ReactNode }) {
  return (
    <h3 className="font-bold text-xs uppercase tracking-wider text-[#0F766E] flex items-center gap-1.5 mb-3">
      <Icon className="w-4 h-4" />
      <span>{children}</span>
    </h3>
  );
}

// Interactive readiness test: scores against passScore and points wrong answers to the matching bridge item
function DiagnosticTest({ data, onReview }: { data: PrerequisiteAnalysis; onReview: (prerequisiteId: string | null) => void }) {
  const { setActiveTab } = useStudy();
  const test = data.diagnosticPreTest;
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    setAnswers({});
    setSubmitted(false);
  }, [data]);

  const conceptOf = (id: string | null) => data.prerequisites.find(p => p.id === id)?.concept;

  if (test.questions.length === 0) {
    return (
      <p className="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-xl p-3">
        Chưa có câu hỏi chẩn đoán. Bấm "Phân tích lại" để AI soạn bài test đầu vào.
      </p>
    );
  }

  const correctCount = test.questions.filter(q => answers[q.id] === q.correctIndex).length;
  const score = Math.round((correctCount / test.questions.length) * 100);
  const passed = score >= test.passScore;
  const allAnswered = test.questions.every(q => answers[q.id] !== undefined);
  const weakIds = Array.from(new Set(
    test.questions.filter(q => answers[q.id] !== q.correctIndex).map(q => q.testedPrerequisiteId)
  ));

  return (
    <div className="space-y-4">
      {test.instructions && <p className="text-xs text-slate-600">{test.instructions}</p>}

      {test.questions.map((q, qIdx) => {
        const chosen = answers[q.id];
        const isCorrect = chosen === q.correctIndex;
        return (
          <div key={q.id} className="border border-slate-200 rounded-xl p-3.5 space-y-2.5">
            <p className="text-xs font-bold text-[#111827] leading-relaxed">
              {qIdx + 1}. {q.question}
            </p>
            <div className="grid gap-1.5">
              {q.options.map((opt, oIdx) => {
                let cls = 'border-slate-200 hover:border-teal-400 hover:bg-teal-50/40 text-slate-700';
                if (!submitted && chosen === oIdx) cls = 'border-[#0F766E] bg-teal-50 text-[#0F766E] font-semibold';
                if (submitted && oIdx === q.correctIndex) cls = 'border-emerald-500 bg-emerald-50 text-emerald-900 font-semibold';
                else if (submitted && chosen === oIdx) cls = 'border-rose-400 bg-rose-50 text-rose-800';
                else if (submitted) cls = 'border-slate-200 text-slate-400';
                return (
                  <button
                    key={oIdx}
                    disabled={submitted}
                    onClick={() => setAnswers(prev => ({ ...prev, [q.id]: oIdx }))}
                    className={`text-left text-xs px-3 py-2 rounded-lg border transition-colors ${cls}`}
                  >
                    {opt}
                  </button>
                );
              })}
            </div>
            {submitted && (
              <div className={`text-[11px] leading-relaxed rounded-lg p-2.5 ${isCorrect ? 'bg-emerald-50 text-emerald-900' : 'bg-rose-50 text-rose-900'}`}>
                <div className="flex items-center gap-1 font-bold mb-0.5">
                  {isCorrect ? <CheckCircle2 className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
                  <span>{isCorrect ? 'Chính xác' : 'Chưa đúng'}</span>
                  {conceptOf(q.testedPrerequisiteId) && (
                    <span className="font-medium opacity-80">• Kiểm tra: {conceptOf(q.testedPrerequisiteId)}</span>
                  )}
                </div>
                {q.explanation}
                {!isCorrect && q.testedPrerequisiteId && (
                  <button onClick={() => onReview(q.testedPrerequisiteId)} className="block mt-1 font-bold underline">
                    Ôn nhanh kiến thức này →
                  </button>
                )}
              </div>
            )}
          </div>
        );
      })}

      {!submitted ? (
        <button
          disabled={!allAnswered}
          onClick={() => setSubmitted(true)}
          className="w-full bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold py-2.5 rounded-xl disabled:opacity-40 transition-colors"
        >
          {allAnswered ? 'Chấm điểm' : `Trả lời đủ ${test.questions.length} câu để chấm điểm`}
        </button>
      ) : (
        <div className={`rounded-xl p-4 space-y-3 border ${passed ? 'bg-emerald-50 border-emerald-200' : 'bg-amber-50 border-amber-200'}`}>
          <div className="flex items-center justify-between">
            <span className={`text-sm font-extrabold ${passed ? 'text-emerald-800' : 'text-amber-900'}`}>
              {passed ? 'Bạn đã sẵn sàng học bài này!' : 'Nên ôn lại nền tảng trước'}
            </span>
            <span className={`text-lg font-extrabold ${passed ? 'text-emerald-700' : 'text-amber-800'}`}>{score}%</span>
          </div>
          <p className="text-[11px] text-slate-600">
            Đúng {correctCount}/{test.questions.length} câu • Ngưỡng đạt {test.passScore}%
          </p>
          {!passed && weakIds.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {weakIds.map(id => conceptOf(id) && (
                <button key={id} onClick={() => onReview(id)} className="text-[11px] font-semibold bg-white border border-amber-300 text-amber-900 px-2.5 py-1 rounded-full hover:bg-amber-100">
                  {conceptOf(id)}
                </button>
              ))}
            </div>
          )}
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => { setAnswers({}); setSubmitted(false); }}
              className="border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-bold py-2 rounded-lg"
            >
              Làm lại
            </button>
            {passed ? (
              <button onClick={() => setActiveTab('workspace')} className="bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold py-2 rounded-lg flex items-center justify-center gap-1">
                Vào học ngay <ArrowRight className="w-3.5 h-3.5" />
              </button>
            ) : (
              <button onClick={() => onReview(weakIds[0] || null)} className="bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold py-2 rounded-lg flex items-center justify-center gap-1">
                Ôn cầu nối 2 phút <Zap className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export function PrerequisiteView() {
  const { activeDocData, analyzePrerequisitesAI, setActiveTab } = useStudy();
  const doc = activeDocData?.document;
  const data = activeDocData?.studyPack?.prerequisites || null;

  const [depth, setDepth] = useState<string>(DEPTH_LABEL[data?.analysisDepth || 'standard']);
  const [isLoading, setIsLoading] = useState(false);
  const [highlightId, setHighlightId] = useState<string | null>(null);

  useEffect(() => {
    if (data?.analysisDepth) setDepth(DEPTH_LABEL[data.analysisDepth]);
  }, [data?.analysisDepth]);

  const runAnalysis = async () => {
    setIsLoading(true);
    try {
      await analyzePrerequisitesAI(depth);
    } finally {
      setIsLoading(false);
    }
  };

  // Scroll to the bridge item (or the prerequisite card) for a weak prerequisite and flash it
  const reviewPrerequisite = (prerequisiteId: string | null) => {
    if (!prerequisiteId) return;
    const target = document.getElementById(`bridge-${prerequisiteId}`) || document.getElementById(`pre-${prerequisiteId}`);
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setHighlightId(prerequisiteId);
    setTimeout(() => setHighlightId(null), 2200);
  };

  const counts = useMemo(() => ({
    critical: data?.prerequisites.filter(p => p.priority === 'critical').length || 0,
    recommended: data?.prerequisites.filter(p => p.priority === 'recommended').length || 0
  }), [data]);

  if (!doc) {
    return (
      <div className="max-w-xl mx-auto my-16 text-center space-y-3 p-6">
        <Compass className="w-10 h-10 text-[#0F766E] mx-auto" />
        <h2 className="font-extrabold text-lg text-[#111827]">Chưa có tài liệu nào được mở</h2>
        <p className="text-xs text-slate-500">Hãy nhập hoặc mở một tài liệu để phân tích kiến thức tiên quyết.</p>
        <button onClick={() => setActiveTab('import')} className="bg-[#0F766E] text-white text-xs font-bold px-4 py-2 rounded-lg">
          Nhập tài liệu
        </button>
      </div>
    );
  }

  const controls = (
    <div className="flex items-center gap-2">
      <select
        value={depth}
        onChange={(e) => setDepth(e.target.value)}
        disabled={isLoading}
        className="bg-slate-50 border border-slate-200 text-slate-700 text-xs font-semibold px-2.5 py-1.5 rounded-lg focus:outline-none focus:border-[#0F766E]"
      >
        {DEPTH_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
      <button
        onClick={runAnalysis}
        disabled={isLoading}
        className="bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold px-3 py-1.5 rounded-lg flex items-center gap-1.5 disabled:opacity-60"
      >
        {isLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
        <span>{isLoading ? 'Đang phân tích...' : data ? 'Phân tích lại' : 'Phân tích ngay'}</span>
      </button>
    </div>
  );

  if (!data) {
    return (
      <div className="max-w-2xl mx-auto my-10 p-6 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-4 text-center">
        <Compass className="w-10 h-10 text-[#0F766E] mx-auto" />
        <h2 className="font-extrabold text-lg text-[#111827]">Kiến thức Tiên quyết</h2>
        <p className="text-xs text-slate-600 leading-relaxed">
          AI sẽ tìm những kiến thức nền mà "{doc.title}" mặc định bạn đã biết, chỉ ra điểm nghẽn nhận thức,
          vạch lộ trình học và soạn bài test chẩn đoán để kiểm tra bạn đã sẵn sàng chưa.
        </p>
        <div className="flex justify-center">{controls}</div>
      </div>
    );
  }

  return (
    <div className="max-w-[1400px] mx-auto p-4 sm:p-6 pb-24 md:pb-6 space-y-5">
      {/* Header */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs space-y-3">
        <div className="flex flex-col md:flex-row md:items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div className="w-10 h-10 rounded-xl bg-[#0F766E] text-white flex items-center justify-center shrink-0">
              <Compass className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 className="font-extrabold text-base sm:text-lg text-[#111827]">Kiến thức Tiên quyết</h2>
              <p className="text-xs text-slate-500 truncate">{data.documentTitle || doc.title}</p>
            </div>
          </div>
          {controls}
        </div>

        <div className="flex flex-wrap gap-1.5">
          <span className="bg-teal-50 text-[#0F766E] border border-teal-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{data.subjectArea}</span>
          <span className="bg-slate-100 text-slate-700 border border-slate-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{data.targetAudienceLevel}</span>
          {data.analysisDepth && (
            <span className="bg-slate-100 text-slate-700 border border-slate-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">Mức: {DEPTH_LABEL[data.analysisDepth]}</span>
          )}
          <span className="bg-rose-50 text-rose-700 border border-rose-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{counts.critical} bắt buộc</span>
          <span className="bg-amber-50 text-amber-800 border border-amber-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{counts.recommended} nên biết</span>
        </div>

        {data.overallReadinessNote && <p className="text-xs text-slate-700 leading-relaxed">{data.overallReadinessNote}</p>}

        {data.isFallback && (
          <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 text-amber-900 text-[11px] rounded-lg p-2.5">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>Đây là phân tích sơ bộ vì dịch vụ AI chưa phản hồi. Bấm "Phân tích lại" để có kết quả đầy đủ và bài test chẩn đoán.</span>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Right column (first on mobile): test, bottleneck, bridge */}
        <div className="lg:col-span-5 lg:order-2 space-y-5">
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs">
            <SectionTitle icon={ClipboardCheck}>{data.diagnosticPreTest.title}</SectionTitle>
            <DiagnosticTest data={data} onReview={reviewPrerequisite} />
          </div>

          {data.cognitiveBottleneck && (
            <div className="bg-rose-50/60 rounded-2xl border border-rose-200 p-5 space-y-2">
              <SectionTitle icon={Target}>Điểm nghẽn nhận thức</SectionTitle>
              <p className="text-sm font-extrabold text-rose-900">{data.cognitiveBottleneck.concept}</p>
              {data.cognitiveBottleneck.description && <p className="text-xs text-slate-700 leading-relaxed">{data.cognitiveBottleneck.description}</p>}
              {data.cognitiveBottleneck.advice && (
                <p className="text-xs text-rose-900 bg-white/70 border border-rose-100 rounded-lg p-2.5 leading-relaxed">
                  <strong>Lời khuyên: </strong>{data.cognitiveBottleneck.advice}
                </p>
              )}
            </div>
          )}

          {data.quickBridgeSummary.length > 0 && (
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs">
              <SectionTitle icon={Zap}>Cầu nối kiến thức — đọc trong 2 phút</SectionTitle>
              <ul className="space-y-2.5">
                {data.quickBridgeSummary.map((b, i) => (
                  <li
                    key={i}
                    id={b.prerequisiteId ? `bridge-${b.prerequisiteId}` : undefined}
                    className={`rounded-xl p-3 border transition-all duration-500 ${
                      highlightId && b.prerequisiteId === highlightId ? 'border-amber-400 bg-amber-50 ring-4 ring-amber-200/60' : 'border-slate-100 bg-slate-50'
                    }`}
                  >
                    <p className="text-xs font-bold text-[#111827] mb-1">{b.concept}</p>
                    <p className="text-xs text-slate-600 leading-relaxed whitespace-pre-line">{b.quickReview}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Left column: prerequisites, dependency chain, learning path */}
        <div className="lg:col-span-7 lg:order-1 space-y-5">
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs">
            <SectionTitle icon={BookOpen}>Kiến thức nền cần có</SectionTitle>
            {data.prerequisites.length === 0 ? (
              <p className="text-xs text-slate-500">Chưa xác định được kiến thức tiên quyết.</p>
            ) : (
              <div className="grid gap-3">
                {data.prerequisites.map(p => {
                  const critical = p.priority === 'critical';
                  return (
                    <div
                      key={p.id}
                      id={`pre-${p.id}`}
                      className={`rounded-xl border-l-4 border p-3.5 space-y-1.5 transition-all duration-500 ${
                        critical ? 'border-l-rose-500 border-rose-100' : 'border-l-amber-400 border-amber-100'
                      } ${highlightId === p.id ? 'ring-4 ring-amber-200/60' : ''}`}
                    >
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="text-sm font-extrabold text-[#111827]">{p.concept}</span>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${critical ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-800'}`}>
                          {critical ? 'Bắt buộc' : 'Nên biết'}
                        </span>
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">{p.level}</span>
                      </div>
                      {p.whyNeeded && <p className="text-xs text-slate-700 leading-relaxed"><strong>Vì sao cần: </strong>{p.whyNeeded}</p>}
                      {p.consequenceIfMissing && (
                        <p className="text-xs text-slate-600 leading-relaxed flex gap-1">
                          <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0 mt-0.5" />
                          <span>{p.consequenceIfMissing}</span>
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {data.dependencyGraph.length > 0 && (
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs">
              <SectionTitle icon={GitBranch}>Đồ thị phụ thuộc</SectionTitle>
              <div className="flex flex-wrap gap-3 text-[10px] font-semibold text-slate-500 mb-3">
                <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-amber-300" />Nền tảng</span>
                <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-teal-300" />Trong bài</span>
                <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-indigo-300" />Ứng dụng</span>
              </div>
              <ul className="space-y-2">
                {data.dependencyGraph.map((d, i) => (
                  <li key={i} className="flex flex-wrap items-center gap-1.5 text-xs">
                    <span className={`px-2.5 py-1 rounded-lg border font-semibold ${NODE_STYLE[d.fromType]}`}>{d.from}</span>
                    <span className="flex items-center gap-1 text-[11px] text-slate-500 italic">
                      {d.relation} <ArrowRight className="w-3.5 h-3.5" />
                    </span>
                    <span className={`px-2.5 py-1 rounded-lg border font-semibold ${NODE_STYLE[d.toType]}`}>{d.to}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {data.learningPath.length > 0 && (
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs">
              <SectionTitle icon={Route}>Lộ trình học đề xuất</SectionTitle>
              <ol className="relative space-y-4 before:absolute before:left-[13px] before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200">
                {data.learningPath.map(s => {
                  const badge = PATH_BADGE[s.type] || PATH_BADGE.core_learning;
                  return (
                    <li key={s.step} className="relative flex gap-3">
                      <span className="relative z-10 w-7 h-7 rounded-full bg-[#0F766E] text-white text-xs font-extrabold flex items-center justify-center shrink-0 border-2 border-white shadow-sm">
                        {s.step}
                      </span>
                      <div className="space-y-0.5 pt-0.5">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="text-xs font-bold text-[#111827]">{s.title}</span>
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${badge.cls}`}>{badge.label}</span>
                        </div>
                        {s.description && <p className="text-xs text-slate-600 leading-relaxed">{s.description}</p>}
                      </div>
                    </li>
                  );
                })}
              </ol>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
