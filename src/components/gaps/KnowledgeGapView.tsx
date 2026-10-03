import React, { useEffect, useMemo, useState } from 'react';
import { ReactFlow, Background, Controls, MarkerType, Position, BackgroundVariant } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from '@dagrejs/dagre';
import {
  Network,
  Target,
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  Layers,
  HelpCircle,
  BookOpen,
  CircleDashed
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { analyzeKnowledgeGaps, GapAnalysis, GapNode, RootGap } from '../../utils/knowledgeGaps';

const STATUS_STYLE: Record<GapNode['status'], { bg: string; border: string; text: string; label: string }> = {
  weak: { bg: '#FFFBEB', border: '#F59E0B', text: '#78350F', label: 'Đang yếu' },
  mastered: { bg: '#ECFDF5', border: '#10B981', text: '#064E3B', label: 'Đã nắm' },
  untested: { bg: '#F8FAFC', border: '#94A3B8', text: '#334155', label: 'Chưa kiểm tra' }
};
const ROOT_STYLE = { bg: '#FFF1F2', border: '#E11D48', text: '#881337' };

const kindLabel = (n: GapNode) => (n.kind === 'prerequisite' ? `Kiến thức nền${n.level ? ` · ${n.level}` : ''}` : 'Khái niệm trong bài');

// Prerequisites on the left, the concepts that need them on the right
function layoutGraph(analysis: GapAnalysis, selected: RootGap | null) {
  const shown = new Set<string>();
  analysis.roots.forEach(r => {
    r.paths.flat().forEach(id => shown.add(id));
    r.suspects.forEach(s => shown.add(s.id));
  });
  const highlighted = new Set(selected ? [...selected.paths.flat(), selected.node.id] : []);
  const rootIds = new Set(analysis.roots.map(r => r.node.id));
  const edges = analysis.requires.filter(e => shown.has(e.from) && shown.has(e.to));

  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'LR', nodesep: 30, ranksep: 90 });
  shown.forEach(id => g.setNode(id, { width: 200, height: 64 }));
  edges.forEach(e => g.setEdge(e.to, e.from));
  dagre.layout(g);

  const dim = (id: string) => (selected && !highlighted.has(id) ? 0.35 : 1);
  const nodes = [...shown].map(id => {
    const n = analysis.nodes.get(id)!;
    const s = rootIds.has(id) ? ROOT_STYLE : STATUS_STYLE[n.status];
    const pos = g.node(id);
    return {
      id,
      position: { x: pos.x - 100, y: pos.y - 32 },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: false,
      data: {
        label: (
          <div className="text-left leading-tight">
            <div className="text-[11px] font-bold">{n.label}</div>
            <div className="text-[9px] opacity-75 mt-0.5">
              {rootIds.has(id) ? 'Lỗ hổng gốc' : STATUS_STYLE[n.status].label}
              {n.wrong + n.right > 0 && ` · ${n.wrong} sai / ${n.right} đúng`}
            </div>
          </div>
        )
      },
      style: {
        width: 200,
        background: s.bg,
        color: s.text,
        border: `${rootIds.has(id) ? 2 : 1.5}px ${n.status === 'untested' && !rootIds.has(id) ? 'dashed' : 'solid'} ${s.border}`,
        borderRadius: 12,
        padding: 8,
        opacity: dim(id)
      }
    };
  });

  const flowEdges = edges.map(e => {
    const onPath = selected && highlighted.has(e.from) && highlighted.has(e.to);
    return {
      id: `${e.to}-${e.from}`,
      source: e.to,
      target: e.from,
      type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed, color: onPath ? '#E11D48' : '#94A3B8' },
      style: { stroke: onPath ? '#E11D48' : '#94A3B8', strokeWidth: onPath ? 2.5 : 1.5, opacity: selected && !onPath ? 0.35 : 1 }
    };
  });

  return { nodes, edges: flowEdges };
}

function PathChain({ path, analysis }: { path: string[]; analysis: GapAnalysis }) {
  return (
    <div className="flex flex-wrap items-center gap-1 text-[11px]">
      {path.map((id, i) => {
        const n = analysis.nodes.get(id)!;
        const isRoot = i === path.length - 1;
        return (
          <React.Fragment key={id}>
            {i > 0 && <span className="text-slate-400 flex items-center">cần<ChevronRight className="w-3 h-3" /></span>}
            <span className={`px-2 py-0.5 rounded-md border font-semibold ${
              isRoot ? 'bg-rose-50 border-rose-300 text-rose-800' : n.status === 'weak' ? 'bg-amber-50 border-amber-200 text-amber-900' : 'bg-slate-50 border-slate-200 text-slate-600'
            }`}>
              {n.label}
            </span>
          </React.Fragment>
        );
      })}
    </div>
  );
}

function RootGapCard({ gap, rank, analysis, selected, onSelect }: {
  gap: RootGap; rank: number; analysis: GapAnalysis; selected: boolean; onSelect: () => void;
}) {
  const { setActiveTab } = useStudy();
  const others = gap.explains.filter(n => n.id !== gap.node.id);
  const failedItself = gap.explains.some(n => n.id === gap.node.id);
  const tracedPaths = gap.paths.filter(p => p.length > 1);

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(); } }}
      className={`bg-white rounded-2xl border p-4 space-y-3 cursor-pointer transition-all outline-none focus-visible:ring-4 focus-visible:ring-rose-100 ${
        selected ? 'border-rose-400 ring-2 ring-rose-100 shadow-md' : 'border-slate-200 hover:border-rose-200 shadow-2xs'
      }`}
    >
      <div className="flex items-start gap-3">
        <span className="w-7 h-7 rounded-full bg-rose-600 text-white text-xs font-extrabold flex items-center justify-center shrink-0">{rank}</span>
        <div className="min-w-0 space-y-0.5">
          <p className="text-[10px] font-bold uppercase tracking-wide text-rose-700">{kindLabel(gap.node)}</p>
          <h4 className="text-sm font-extrabold text-[#111827] leading-snug">{gap.node.label}</h4>
          <p className="text-xs text-slate-600 leading-relaxed">
            {others.length > 0
              ? <>Là nguyên nhân gốc của lỗi ở <strong>{others.length} khái niệm</strong>: {others.map(n => n.label).join(', ')}.</>
              : <>Bạn trả lời sai trực tiếp phần này ({gap.node.wrong} sai / {gap.node.right} đúng) và chưa có dấu hiệu yếu ở kiến thức nền.</>}
            {others.length > 0 && failedItself && ' Bạn cũng trả lời sai trực tiếp phần này.'}
          </p>
        </div>
      </div>

      {tracedPaths.length > 0 && (
        <div className="space-y-1.5">
          <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Đường truy nguyên</p>
          {tracedPaths.map((p, i) => <PathChain key={i} path={p} analysis={analysis} />)}
        </div>
      )}

      {gap.misconceptions.length > 0 && (
        <div className="bg-amber-50 border border-amber-100 rounded-xl p-2.5 space-y-1">
          <p className="text-[10px] font-bold uppercase tracking-wide text-amber-800 flex items-center gap-1">
            <AlertTriangle className="w-3 h-3" /> Hiểu sai thường gặp
          </p>
          {gap.misconceptions.map((m, i) => (
            <p key={i} className="text-xs text-amber-950 leading-relaxed">
              <span className="line-through decoration-amber-400">{m.misconception}</span>
              {m.correction && <> → <strong>{m.correction}</strong></>}
            </p>
          ))}
        </div>
      )}

      {gap.bridge && (
        <div className="bg-teal-50 border border-teal-100 rounded-xl p-2.5">
          <p className="text-[10px] font-bold uppercase tracking-wide text-[#0F766E] mb-0.5">Ôn nhanh 2 phút</p>
          <p className="text-xs text-slate-700 leading-relaxed whitespace-pre-line">{gap.bridge}</p>
        </div>
      )}

      {!gap.bridge && gap.node.description && (
        <p className="text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-xl p-2.5 leading-relaxed">{gap.node.description}</p>
      )}

      {gap.suspects.length > 0 && (
        <p className="text-[11px] text-slate-500 flex items-start gap-1">
          <CircleDashed className="w-3.5 h-3.5 shrink-0 mt-px" />
          <span>Kiến thức nền chưa được kiểm tra, nên kiểm tra thêm: {gap.suspects.map(s => s.label).join(', ')}</span>
        </p>
      )}

      <div className="flex flex-wrap gap-2 pt-1" onClick={(e) => e.stopPropagation()}>
        {gap.node.kind === 'prerequisite' ? (
          <button onClick={() => setActiveTab('prerequisite')} className="text-xs font-bold px-3 py-1.5 rounded-lg bg-[#0F766E] hover:bg-[#0D5C53] text-white flex items-center gap-1.5">
            <ClipboardCheck className="w-3.5 h-3.5" /> Làm lại bài test chẩn đoán
          </button>
        ) : (
          <>
            <button onClick={() => setActiveTab('flashcard')} className="text-xs font-bold px-3 py-1.5 rounded-lg bg-[#0F766E] hover:bg-[#0D5C53] text-white flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5" /> Ôn bằng thẻ ghi nhớ
            </button>
            <button onClick={() => setActiveTab('quiz')} className="text-xs font-bold px-3 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 flex items-center gap-1.5">
              <HelpCircle className="w-3.5 h-3.5" /> Làm lại trắc nghiệm
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export function KnowledgeGapView() {
  const { activeDocData, setActiveTab } = useStudy();
  const pack = activeDocData?.studyPack;
  const analysis = useMemo(
    () => (pack ? analyzeKnowledgeGaps(pack, activeDocData?.quizHistory || []) : null),
    [pack, activeDocData?.quizHistory]
  );
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Open on the most impactful gap; keep the user's choice while it still exists
  useEffect(() => {
    if (!analysis?.roots.some(r => r.node.id === selectedId)) setSelectedId(analysis?.roots[0]?.node.id ?? null);
  }, [analysis]);

  const selected = analysis?.roots.find(r => r.node.id === selectedId) || null;
  const graph = useMemo(() => (analysis ? layoutGraph(analysis, selected) : null), [analysis, selected]);

  if (!activeDocData?.document) {
    return (
      <div className="max-w-xl mx-auto my-16 text-center space-y-3 p-6">
        <Network className="w-10 h-10 text-[#0F766E] mx-auto" />
        <h2 className="font-extrabold text-lg text-[#111827]">Chưa có tài liệu nào được mở</h2>
        <p className="text-xs text-slate-500">Mở một bài học để xem bản đồ lỗ hổng kiến thức của bạn.</p>
        <button onClick={() => setActiveTab('history')} className="bg-[#0F766E] text-white text-xs font-bold px-4 py-2 rounded-lg">
          Mở lịch sử bài học
        </button>
      </div>
    );
  }

  const weakCount = analysis ? [...analysis.nodes.values()].filter(n => n.status === 'weak').length : 0;
  const practice = [
    { tab: 'quiz' as const, icon: HelpCircle, label: 'Làm trắc nghiệm' },
    { tab: 'flashcard' as const, icon: Layers, label: 'Ôn thẻ ghi nhớ' },
    { tab: 'prerequisite' as const, icon: ClipboardCheck, label: 'Làm bài test chẩn đoán' }
  ];

  return (
    <div className="max-w-[1400px] mx-auto p-4 sm:p-6 space-y-5">
      {/* Header */}
      <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-2xs space-y-3">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-rose-600 text-white flex items-center justify-center shrink-0">
            <Network className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <h2 className="font-extrabold text-base sm:text-lg text-[#111827]">Bản đồ lỗ hổng kiến thức</h2>
            <p className="text-xs text-slate-500 truncate">{activeDocData.document.title}</p>
          </div>
        </div>
        <p className="text-xs text-slate-600 leading-relaxed">
          Mỗi câu sai được lần ngược theo chuỗi kiến thức phụ thuộc để tìm <strong>chỗ hổng gốc</strong>, phần mà nếu lấp lại sẽ sửa được nhiều lỗi cùng lúc. Kiến thức bạn đã chứng minh là nắm vững được loại khỏi danh sách nghi vấn.
        </p>
        {analysis && analysis.evidenceCount > 0 && (
          <div className="flex flex-wrap gap-1.5">
            <span className="bg-slate-100 text-slate-700 border border-slate-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{analysis.evidenceCount} lượt luyện tập được phân tích</span>
            <span className="bg-amber-50 text-amber-800 border border-amber-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{weakCount} phần đang yếu</span>
            <span className="bg-rose-50 text-rose-700 border border-rose-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full">{analysis.roots.length} lỗ hổng gốc</span>
          </div>
        )}
      </div>

      {!analysis || analysis.nodes.size === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-6 text-center space-y-2">
          <AlertTriangle className="w-8 h-8 text-amber-500 mx-auto" />
          <p className="text-sm font-bold text-[#111827]">Bài học này chưa có đồ thị khái niệm</p>
          <p className="text-xs text-slate-500">Hãy chuyển hóa lại tài liệu để hệ thống phân tích các khái niệm và quan hệ phụ thuộc giữa chúng.</p>
        </div>
      ) : analysis.evidenceCount === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 p-6 text-center space-y-4">
          <Target className="w-8 h-8 text-[#0F766E] mx-auto" />
          <div className="space-y-1">
            <p className="text-sm font-bold text-[#111827]">Chưa có dữ liệu luyện tập để phân tích</p>
            <p className="text-xs text-slate-500">Làm trắc nghiệm, ôn thẻ ghi nhớ (đánh giá Khó/Dễ) hoặc làm bài test chẩn đoán. Bản đồ sẽ tự cập nhật theo kết quả của bạn.</p>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            {practice.map(p => (
              <button key={p.tab} onClick={() => setActiveTab(p.tab)} className="text-xs font-bold px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 flex items-center gap-1.5">
                <p.icon className="w-3.5 h-3.5 text-[#0F766E]" /> {p.label}
              </button>
            ))}
          </div>
        </div>
      ) : analysis.roots.length === 0 ? (
        <div className="bg-emerald-50 rounded-2xl border border-emerald-200 p-6 text-center space-y-2">
          <CheckCircle2 className="w-8 h-8 text-emerald-600 mx-auto" />
          <p className="text-sm font-bold text-emerald-900">Chưa phát hiện lỗ hổng nào</p>
          <p className="text-xs text-emerald-800">Mọi phần bạn đã luyện tập đều đang ổn. Tiếp tục làm trắc nghiệm để bản đồ bao phủ thêm khái niệm.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
          <div className="lg:col-span-5 space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-rose-700 flex items-center gap-1.5">
              <Target className="w-4 h-4" /> Lỗ hổng gốc, xếp theo mức ảnh hưởng
            </h3>
            {analysis.roots.map((gap, i) => (
              <RootGapCard
                key={gap.node.id}
                gap={gap}
                rank={i + 1}
                analysis={analysis}
                selected={gap.node.id === selectedId}
                onSelect={() => setSelectedId(gap.node.id)}
              />
            ))}
          </div>

          <div className="lg:col-span-7 lg:sticky lg:top-4 space-y-2">
            <h3 className="font-bold text-xs uppercase tracking-wider text-[#0F766E] flex items-center gap-1.5">
              <BookOpen className="w-4 h-4" /> Chuỗi phụ thuộc: kiến thức nền → kiến thức dựa trên nó
            </h3>
            <div className="h-[420px] bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <ReactFlow
                nodes={graph!.nodes}
                edges={graph!.edges}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                nodesConnectable={false}
                minZoom={0.3}
                onNodeClick={(_, node) => {
                  if (analysis.roots.some(r => r.node.id === node.id)) setSelectedId(node.id);
                }}
                proOptions={{ hideAttribution: true }}
              >
                <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="#E2E8F0" />
                <Controls showInteractive={false} />
              </ReactFlow>
            </div>
            <div className="flex flex-wrap gap-3 text-[10px] font-semibold text-slate-500 px-1">
              <span className="flex items-center gap-1"><span className="w-3 h-3 rounded border-2 border-rose-600 bg-rose-50" />Lỗ hổng gốc</span>
              <span className="flex items-center gap-1"><span className="w-3 h-3 rounded border border-amber-500 bg-amber-50" />Đang yếu</span>
              <span className="flex items-center gap-1"><span className="w-3 h-3 rounded border border-emerald-500 bg-emerald-50" />Đã nắm</span>
              <span className="flex items-center gap-1"><span className="w-3 h-3 rounded border border-dashed border-slate-400 bg-slate-50" />Chưa kiểm tra</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
