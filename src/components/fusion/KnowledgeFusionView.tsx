import React, { useState } from 'react';
import { 
  GitCompare, 
  Sparkles, 
  CheckSquare, 
  Square, 
  AlertTriangle, 
  CheckCircle2, 
  Layers, 
  BookOpen, 
  ArrowRight, 
  Loader2, 
  Info,
  ShieldAlert,
  FolderSync,
  Copy,
  Check
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';

export function KnowledgeFusionView() {
  const { historyList = [], documents = [], fusionResult, fusionLoading, performKnowledgeFusion } = useStudy();

  // Combine documents from history and active list safely
  const availableDocs = (Array.isArray(historyList) && historyList.length > 0) 
    ? historyList 
    : (Array.isArray(documents) ? documents : []);

  // Selected document IDs state
  const [selectedIds, setSelectedIds] = useState<string[]>(() => {
    return availableDocs.slice(0, 2).map(d => d.id);
  });

  // Active inner view tab
  const [activeSubTab, setActiveSubTab] = useState<'unified' | 'compare' | 'conflicts'>('unified');

  const toggleSelectDoc = (id: string) => {
    if (selectedIds.includes(id)) {
      if (selectedIds.length > 2) {
        setSelectedIds(selectedIds.filter(item => item !== id));
      }
    } else {
      setSelectedIds([...selectedIds, id]);
    }
  };

  const handleStartFusion = () => {
    if (selectedIds.length >= 2) {
      performKnowledgeFusion(selectedIds);
    }
  };

  const [copied, setCopied] = useState<boolean>(false);

  const handleCopyReport = () => {
    if (!fusionResult) return;
    const text = `=== ${fusionResult.fusionTitle || 'BÁO CÁO HỢP NHẤT KIẾN THỨC'} ===\n\n` +
      `[TÓM TẮT TỔNG QUAN]\n${fusionResult.unifiedSummary || ''}\n\n` +
      `[CÁC KHÁI NIỆM DÙNG CHUNG / GIAO THOA TRI THỨC]\n` +
      (fusionResult.commonConcepts || []).map((c: any, i: number) => `${i + 1}. ${c.concept}\n   Định nghĩa: ${c.definition}\n   Nguồn: ${(c.sources || []).join(', ')}`).join('\n\n') +
      `\n\n[CHI TIẾT ĐỘC QUYỀN TỪNG TÀI LIỆU]\n` +
      (fusionResult.uniqueInsights || []).map((u: any) => `* ${u.docTitle}:\n` + (u.insights || []).map((ins: string) => `  - ${ins}`).join('\n')).join('\n\n') +
      `\n\n[RADAR CẢNH BÁO MÂU THUẪN & BẪY TƯ DUY]\n` +
      (fusionResult.conflicts || []).map((cf: any, i: number) => `${i + 1}. ${cf.topic}\n   - Nguồn A: ${cf.docA?.statement || ''}\n   - Nguồn B: ${cf.docB?.statement || ''}\n   - Lời khuyên: ${cf.recommendation || ''}`).join('\n\n');

    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  // Safely extract fusionResult data to prevent TypeError if any field is missing
  const comparedDocs = Array.isArray(fusionResult?.comparedDocs) ? fusionResult.comparedDocs : [];
  const commonConcepts = Array.isArray(fusionResult?.commonConcepts) ? fusionResult.commonConcepts : [];
  const uniqueInsights = Array.isArray(fusionResult?.uniqueInsights) ? fusionResult.uniqueInsights : [];
  const conflicts = Array.isArray(fusionResult?.conflicts) ? fusionResult.conflicts : [];
  const mergedNodes = Array.isArray(fusionResult?.mergedMindmap?.nodes) ? fusionResult.mergedMindmap.nodes : [];
  const unifiedSummary = fusionResult?.unifiedSummary || "Đã tổng hợp thành công nội dung giữa các bài học.";

  return (
    <div className="p-4 md:p-8 space-y-8 max-w-7xl mx-auto animate-in fade-in duration-300">
      
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-[#0B4F48] to-[#0F766E] text-white rounded-3xl p-6 md:p-8 shadow-md relative overflow-hidden">
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 bg-white/10 rounded-full blur-2xl pointer-events-none" />
        
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 relative z-10">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-white/15 border border-white/30 rounded-full text-teal-100 text-xs font-semibold backdrop-blur-xs">
              <GitCompare className="w-3.5 h-3.5" />
              <span>Tính năng Nâng cao (AI Fusion)</span>
            </div>
            <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight text-white">
              Hợp nhất & Đối chiếu Kiến thức Đa Tài liệu
            </h1>
            <p className="text-teal-100/90 text-xs md:text-sm max-w-2xl leading-relaxed">
              Chọn từ 2 bài học trở lên để AI tự động gộp các khái niệm trùng lặp, phân tách chi tiết độc quyền và phát hiện cờ cảnh báo ⚠️ mâu thuẫn số liệu giữa các tài liệu.
            </p>
          </div>

          <button
            onClick={handleStartFusion}
            disabled={selectedIds.length < 2 || fusionLoading}
            className={`px-6 py-3.5 rounded-2xl font-bold text-xs md:text-sm shadow-md flex items-center justify-center gap-2 transition-all ${
              selectedIds.length >= 2 && !fusionLoading
                ? 'bg-white hover:bg-teal-50 text-[#0F766E] shadow-sm hover:scale-105 active:scale-95'
                : 'bg-white/20 text-teal-100/60 cursor-not-allowed border border-white/20'
            }`}
          >
            {fusionLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin text-[#0F766E]" />
                <span>Đang phân tích đối chiếu...</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4 text-[#0F766E]" />
                <span>Bắt đầu Hợp nhất AI ({selectedIds.length} tài liệu)</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Document Selection Grid */}
      <div className="bg-white rounded-3xl p-6 border border-slate-200/80 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-[#111827] flex items-center gap-2">
            <BookOpen className="w-4 h-4 text-[#0F766E]" />
            <span>Chọn các bài học cần đối chiếu:</span>
            <span className="text-xs text-slate-500 font-normal">(Đã chọn {selectedIds.length} tệp)</span>
          </h3>
          {selectedIds.length < 2 && (
            <span className="text-xs text-amber-600 font-semibold bg-amber-50 px-2.5 py-1 rounded-lg border border-amber-200">
              Vui lòng chọn ít nhất 2 bài học
            </span>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {availableDocs.map((doc) => {
            const isSelected = selectedIds.includes(doc.id);
            return (
              <div
                key={doc.id}
                onClick={() => toggleSelectDoc(doc.id)}
                className={`p-4 rounded-2xl border transition-all cursor-pointer flex items-start gap-3 select-none ${
                  isSelected
                    ? 'border-[#0F766E] bg-teal-50/40 shadow-xs'
                    : 'border-slate-200 hover:border-slate-300 bg-white'
                }`}
              >
                <div className="mt-0.5 text-[#0F766E]">
                  {isSelected ? (
                    <CheckSquare className="w-5 h-5 fill-[#0F766E] text-white" />
                  ) : (
                    <Square className="w-5 h-5 text-slate-300" />
                  )}
                </div>
                <div className="overflow-hidden flex-1">
                  <h4 className="text-xs font-bold text-[#111827] truncate">
                    {doc.title}
                  </h4>
                  <div className="flex items-center gap-2 text-[11px] text-slate-500 mt-1">
                    <span className="uppercase px-1.5 py-0.5 bg-slate-100 rounded text-[10px] font-semibold text-slate-600">
                      {doc.fileType}
                    </span>
                    <span>{doc.updatedAt}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Fusion Analysis Display Section */}
      {fusionResult ? (
        <div className="space-y-6 animate-in fade-in duration-300">
          
          {/* Top KPI Metrics Bar */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-teal-50 text-[#0F766E] flex items-center justify-center shrink-0">
                <FolderSync className="w-6 h-6" />
              </div>
              <div>
                <p className="text-xs text-slate-500 font-medium">Tài liệu đã hợp nhất</p>
                <p className="text-xl font-black text-[#111827]">{comparedDocs.length} tệp</p>
              </div>
            </div>

            <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <div>
                <p className="text-xs text-slate-500 font-medium">Khái niệm gộp chung</p>
                <p className="text-xl font-black text-[#111827]">{commonConcepts.length} nhóm</p>
              </div>
            </div>

            <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                <ShieldAlert className="w-6 h-6" />
              </div>
              <div>
                <p className="text-xs text-slate-500 font-medium">Mâu thuẫn phát hiện</p>
                <p className="text-xl font-black text-[#111827]">{conflicts.length} cờ cảnh báo</p>
              </div>
            </div>
          </div>

          {/* Sub Navigation Tabs */}
          <div className="flex border-b border-slate-200 space-x-8">
            <button
              onClick={() => setActiveSubTab('unified')}
              className={`pb-3 text-xs md:text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${
                activeSubTab === 'unified'
                  ? 'border-[#0F766E] text-[#0F766E]'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              <Layers className="w-4 h-4" />
              <span>1. Tổng quan Hợp nhất (Unified View)</span>
            </button>

            <button
              onClick={() => setActiveSubTab('compare')}
              className={`pb-3 text-xs md:text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${
                activeSubTab === 'compare'
                  ? 'border-[#0F766E] text-[#0F766E]'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              <GitCompare className="w-4 h-4" />
              <span>2. So sánh Chi tiết (Side-by-Side)</span>
            </button>

            <button
              onClick={() => setActiveSubTab('conflicts')}
              className={`pb-3 text-xs md:text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${
                activeSubTab === 'conflicts'
                  ? 'border-amber-500 text-amber-700'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              <AlertTriangle className="w-4 h-4 text-amber-500" />
              <span>3. Radar Cảnh báo Mâu thuẫn ({conflicts.length})</span>
            </button>
          </div>

          {/* Sub Tab 1: Unified View */}
          {activeSubTab === 'unified' && (
            <div className="space-y-6 animate-in fade-in duration-200">
              {/* Executive Summary Card */}
              <div className="bg-gradient-to-br from-teal-50/60 via-white to-slate-50 p-6 rounded-3xl border border-teal-100 shadow-xs space-y-3">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2 border-b border-teal-100/60">
                  <h4 className="text-sm font-bold text-[#0F766E] flex items-center gap-2 uppercase tracking-wider">
                    <Sparkles className="w-4 h-4 text-[#0F766E]" />
                    <span>Báo cáo Tóm tắt Hợp nhất Dữ liệu</span>
                  </h4>
                  <button
                    onClick={handleCopyReport}
                    className="self-start sm:self-auto flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white border border-teal-200 text-[#0F766E] hover:bg-teal-50 text-xs font-semibold shadow-2xs transition-all active:scale-95"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5 text-[#0F766E]" />}
                    <span>{copied ? 'Đã sao chép báo cáo!' : 'Sao chép toàn bộ báo cáo'}</span>
                  </button>
                </div>
                <p className="text-xs md:text-sm text-slate-700 leading-relaxed font-normal">
                  {unifiedSummary}
                </p>
              </div>

              {/* Common Concepts List */}
              <div className="space-y-4">
                <h4 className="text-sm font-bold text-[#111827] flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-[#0F766E]" />
                  <span>Các Khái niệm dùng chung (Đã gộp chuẩn hóa)</span>
                </h4>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {commonConcepts.map((item, idx) => {
                    const sources = Array.isArray(item?.sources) ? item.sources : [];
                    return (
                      <div key={idx} className="bg-white p-5 rounded-2xl border border-slate-200 shadow-2xs space-y-3 hover:border-teal-300 transition-colors">
                        <div className="flex items-start justify-between gap-2">
                          <h5 className="text-xs md:text-sm font-bold text-[#111827]">
                            {item.concept}
                          </h5>
                          <span className="px-2 py-0.5 bg-teal-50 text-[#0F766E] text-[10px] font-extrabold rounded-md shrink-0">
                            {sources.length} Nguồn
                          </span>
                        </div>
                        <p className="text-xs text-slate-600 leading-relaxed">
                          {item.definition}
                        </p>
                        <div className="flex flex-wrap gap-1.5 pt-2 border-t border-slate-100">
                          {sources.map((srcName, sIdx) => (
                            <span key={sIdx} className="px-2 py-0.5 bg-slate-100 text-slate-600 text-[10px] font-semibold rounded-md truncate max-w-[200px]">
                              📌 {srcName}
                            </span>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Merged Mindmap Tree Preview */}
              {mergedNodes.length > 0 && (
                <div className="bg-white p-6 rounded-3xl border border-slate-200 shadow-xs space-y-4">
                  <h4 className="text-sm font-bold text-[#111827] flex items-center gap-2">
                    <Layers className="w-4 h-4 text-[#0F766E]" />
                    <span>Cấu trúc Sơ đồ Tư duy Hợp nhất (Merged Mindmap Nodes)</span>
                  </h4>
                  <div className="space-y-2 bg-slate-50 p-4 rounded-2xl border border-slate-200/60 max-h-80 overflow-y-auto">
                    {mergedNodes.map((node) => (
                      <div
                        key={node.id}
                        className={`flex items-center gap-3 p-2.5 rounded-xl text-xs font-semibold ${
                          node.type === 'warning'
                            ? 'bg-amber-50 text-amber-800 border border-amber-200'
                            : node.level === 0
                            ? 'bg-[#0F766E] text-white font-bold'
                            : node.level === 1
                            ? 'bg-white text-[#111827] shadow-2xs border border-slate-200'
                            : 'bg-slate-100/80 text-slate-600 ml-4'
                        }`}
                      >
                        <span>•</span>
                        <span className="flex-1">{node.label}</span>
                        {node.detail && (
                          <span className="text-[10px] opacity-75 font-normal">
                            ({node.detail})
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Sub Tab 2: Side-by-Side Unique Insights */}
          {activeSubTab === 'compare' && (
            <div className="space-y-4 animate-in fade-in duration-200">
              <h4 className="text-sm font-bold text-[#111827] flex items-center gap-2">
                <GitCompare className="w-4 h-4 text-[#0F766E]" />
                <span>Đối chiếu các chi tiết Độc quyền & Bổ sung từng nguồn</span>
              </h4>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {uniqueInsights.map((insightItem, idx) => {
                  const insights = Array.isArray(insightItem?.insights) ? insightItem.insights : [];
                  return (
                    <div key={idx} className="bg-white p-6 rounded-3xl border border-slate-200 shadow-xs space-y-4">
                      <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
                        <div className="w-8 h-8 rounded-xl bg-teal-50 text-[#0F766E] flex items-center justify-center font-bold text-xs">
                          #{idx + 1}
                        </div>
                        <div>
                          <h5 className="text-xs font-bold text-[#111827] truncate max-w-xs">
                            {insightItem.docTitle}
                          </h5>
                          <div className="flex flex-wrap items-center gap-1.5 mt-1">
                            {insightItem.domain && (
                              <span className="text-[10px] font-bold text-teal-800 bg-teal-100/70 px-2 py-0.5 rounded-md">
                                📚 {insightItem.domain}
                              </span>
                            )}
                            <span className="text-[10px] font-semibold text-[#0F766E] bg-teal-50 px-2 py-0.5 rounded-md">
                              Chi tiết Độc quyền Nguồn #{idx + 1}
                            </span>
                          </div>
                        </div>
                      </div>

                      <ul className="space-y-2.5">
                        {insights.map((text, iIdx) => (
                          <li key={iIdx} className="flex items-start gap-2.5 text-xs text-slate-700 leading-relaxed">
                            <ArrowRight className="w-4 h-4 text-[#0F766E] shrink-0 mt-0.5" />
                            <span>{text}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Sub Tab 3: Conflict Radar */}
          {activeSubTab === 'conflicts' && (
            <div className="space-y-4 animate-in fade-in duration-200">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-bold text-amber-800 flex items-center gap-2">
                  <ShieldAlert className="w-4 h-4 text-amber-500" />
                  <span>Radar Phát hiện Mâu thuẫn & Sai lệch Số liệu (Conflict Radar)</span>
                </h4>
              </div>

              {conflicts.length === 0 ? (
                <div className="bg-emerald-50 border border-emerald-200 p-6 rounded-3xl text-center space-y-2">
                  <CheckCircle2 className="w-8 h-8 text-emerald-600 mx-auto" />
                  <p className="text-xs md:text-sm font-bold text-emerald-800">Không tìm thấy mâu thuẫn!</p>
                  <p className="text-xs text-emerald-600">Dữ liệu giữa các tài liệu chọn hoàn toàn đồng nhất và nhất quán.</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {conflicts.map((conf, cIdx) => {
                    const docA = conf?.docA;
                    const docB = conf?.docB;
                    const titleA = typeof docA === 'object' && docA?.title ? docA.title : (typeof docA === 'string' ? docA : 'Nguồn A');
                    const statementA = typeof docA === 'object' && docA?.statement ? docA.statement : '';
                    const titleB = typeof docB === 'object' && docB?.title ? docB.title : (typeof docB === 'string' ? docB : 'Nguồn B');
                    const statementB = typeof docB === 'object' && docB?.statement ? docB.statement : '';

                    return (
                      <div key={conf?.id || cIdx} className="bg-white p-6 rounded-3xl border-2 border-amber-200/80 shadow-md space-y-4">
                        <div className="flex items-center gap-2 text-xs font-extrabold text-amber-800 bg-amber-50 px-3 py-1.5 rounded-xl w-fit">
                          <AlertTriangle className="w-4 h-4 text-amber-600" />
                          <span>Chủ đề mâu thuẫn: {conf?.topic || "Sai lệch nội dung"}</span>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div className="bg-slate-50 p-4 rounded-2xl border border-slate-200 space-y-1.5">
                            <span className="text-[11px] font-bold text-slate-500">
                              📌 Nguồn: {titleA}
                            </span>
                            {statementA && (
                              <p className="text-xs font-semibold text-[#111827]">
                                "{statementA}"
                              </p>
                            )}
                          </div>

                          <div className="bg-slate-50 p-4 rounded-2xl border border-slate-200 space-y-1.5">
                            <span className="text-[11px] font-bold text-slate-500">
                              📌 Nguồn: {titleB}
                            </span>
                            {statementB && (
                              <p className="text-xs font-semibold text-[#111827]">
                                "{statementB}"
                              </p>
                            )}
                          </div>
                        </div>

                        <div className="bg-teal-50/60 p-4 rounded-2xl border border-teal-100 space-y-2 text-xs">
                          <div className="flex items-center gap-2 text-[#0F766E] font-bold">
                            <Info className="w-4 h-4" />
                            <span>Giải thích AI & Khuyến nghị ôn tập:</span>
                          </div>
                          <p className="text-slate-700 leading-relaxed">{conf?.explanation}</p>
                          {conf?.recommendation && (
                            <p className="text-[#0F766E] font-semibold bg-white p-2.5 rounded-xl border border-teal-100">
                              💡 Khuyên dùng: {conf.recommendation}
                            </p>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

        </div>
      ) : (
        /* Placeholder State before initiating fusion */
        <div className="bg-white rounded-3xl p-12 text-center border border-slate-200/80 shadow-xs space-y-4">
          <div className="w-16 h-16 rounded-3xl bg-teal-50 text-[#0F766E] flex items-center justify-center mx-auto">
            <GitCompare className="w-8 h-8" />
          </div>
          <h3 className="text-base font-bold text-[#111827]">Sẵn sàng Hợp nhất Kiến thức</h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto leading-relaxed">
            Chọn ít nhất 2 bài học ở danh sách phía trên và nhấn nút <span className="font-bold text-[#0F766E]">"Bắt đầu Hợp nhất AI"</span> để kích hoạt công cụ phân tích đối chiếu.
          </p>
        </div>
      )}

    </div>
  );
}
