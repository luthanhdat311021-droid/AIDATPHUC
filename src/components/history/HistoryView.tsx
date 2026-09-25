import React, { useState } from 'react';
import { 
  History, 
  Search, 
  FileText, 
  Video, 
  Globe, 
  Calendar, 
  Clock, 
  CheckCircle2, 
  Trash2, 
  BookOpen, 
  Award, 
  Database, 
  Sparkles, 
  ExternalLink,
  Filter,
  BarChart3,
  X,
  Layers,
  Brain,
  GitCompare,
  CheckSquare,
  Square,
  Loader2
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { LessonHistoryItem, QuizHistoryRecord } from '../../types';
import { KnowledgeFusionView } from '../fusion/KnowledgeFusionView';

export function HistoryView() {
  const { 
    historyList, 
    isSupabaseActive, 
    deleteHistoryItem, 
    continueLessonFromHistory,
    performKnowledgeFusion,
    fusionLoading,
    fusionResult
  } = useStudy();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTag, setSelectedTag] = useState<string>('ALL');
  const [activeQuizModal, setActiveQuizModal] = useState<QuizHistoryRecord[] | null>(null);
  const [modalTitle, setModalTitle] = useState<string>('');
  const [selectedForFusion, setSelectedForFusion] = useState<string[]>([]);
  const [historySubMode, setHistorySubMode] = useState<'list' | 'fusion'>('list');

  const toggleSelectForFusion = (id: string) => {
    if (selectedForFusion.includes(id)) {
      setSelectedForFusion(selectedForFusion.filter(item => item !== id));
    } else {
      setSelectedForFusion([...selectedForFusion, id]);
    }
  };

  const handleStartFusionFromHistory = async () => {
    if (selectedForFusion.length >= 2) {
      await performKnowledgeFusion(selectedForFusion);
      setHistorySubMode('fusion');
    }
  };

  // Filtered History List
  const safeHistoryList = Array.isArray(historyList) ? historyList : [];
  const filteredList = safeHistoryList.filter(item => {
    if (!item || !item.title) return false;
    const titleMatch = item.title.toLowerCase().includes(searchQuery.toLowerCase());
    const tags = Array.isArray(item.tags) ? item.tags : [];
    const tagMatch = tags.some(t => typeof t === 'string' && t.toLowerCase().includes(searchQuery.toLowerCase()));
    const matchesSearch = titleMatch || tagMatch;
    
    if (selectedTag === 'ALL') return matchesSearch;
    if (selectedTag === 'PDF') return matchesSearch && item.fileType === 'PDF';
    if (selectedTag === 'VIDEO') return matchesSearch && item.fileType === 'VIDEO';
    if (selectedTag === 'URL') return matchesSearch && item.fileType === 'URL';
    return matchesSearch && tags.includes(selectedTag);
  });

  const getFileIcon = (fileType: string) => {
    switch (fileType?.toUpperCase()) {
      case 'VIDEO':
        return <Video className="w-5 h-5 text-indigo-500" />;
      case 'URL':
        return <Globe className="w-5 h-5 text-emerald-500" />;
      default:
        return <FileText className="w-5 h-5 text-[#0F766E]" />;
    }
  };

  const getBestQuizScore = (quizHistory?: QuizHistoryRecord[]) => {
    if (!quizHistory || quizHistory.length === 0) return null;
    return Math.max(...quizHistory.map(q => q.score));
  };

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-7xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-[#0F766E] text-xs font-bold uppercase tracking-wider mb-1">
            <History className="w-4 h-4" />
            <span>Lịch sử & Đồng bộ Dữ liệu</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Lịch sử Bài học & Tiến trình Học tập
          </h1>
          <p className="text-slate-500 text-xs sm:text-sm mt-1">
            Quản lý toàn bộ các bài học đã chuyển hóa, kết quả trắc nghiệm và sơ đồ tư duy đã tạo.
          </p>
        </div>

        {/* Supabase Integration Badge */}
        <div className={`p-4 rounded-xl border flex items-center gap-3 ${
          isSupabaseActive 
            ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
            : 'bg-amber-50 border-amber-200 text-amber-900'
        }`}>
          <div className={`p-2 rounded-lg ${isSupabaseActive ? 'bg-emerald-500 text-white' : 'bg-amber-500 text-white'}`}>
            <Database className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold">
                {isSupabaseActive ? 'Supabase Database Active' : 'Chế độ Lưu trữ In-Memory'}
              </span>
              <span className={`w-2 h-2 rounded-full ${isSupabaseActive ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`} />
            </div>
            <p className="text-[11px] opacity-80 mt-0.5">
              {isSupabaseActive 
                ? 'Đã kết nối CSDL Supabase — Bài học được lưu trữ vĩnh viễn'
                : 'Chưa nhận Supabase Keys trong .env — Đang tự động lưu bộ nhớ tạm'}
            </p>
          </div>
        </div>
      </div>

      {/* Mode Switcher Tabs */}
      <div className="flex border-b border-slate-200 space-x-6">
        <button
          onClick={() => setHistorySubMode('list')}
          className={`pb-3 text-xs md:text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${
            historySubMode === 'list'
              ? 'border-[#0F766E] text-[#0F766E]'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <BookOpen className="w-4 h-4" />
          <span>Danh sách Bài học ({safeHistoryList.length})</span>
        </button>

        <button
          onClick={() => setHistorySubMode('fusion')}
          className={`pb-3 text-xs md:text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${
            historySubMode === 'fusion'
              ? 'border-[#0F766E] text-[#0F766E]'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <GitCompare className="w-4 h-4" />
          <span>Hợp nhất Kiến thức AI {fusionResult ? '✨' : ''}</span>
        </button>
      </div>

      {/* Conditional Rendering: Fusion View vs History List */}
      {historySubMode === 'fusion' ? (
        <div className="space-y-4">
          <button
            onClick={() => setHistorySubMode('list')}
            className="inline-flex items-center gap-2 text-xs font-bold text-[#0F766E] hover:text-[#0D645E] bg-teal-50 px-3 py-1.5 rounded-xl border border-teal-200/60 transition-colors"
          >
            <span>← Quay lại Danh sách bài học</span>
          </button>
          <KnowledgeFusionView />
        </div>
      ) : (
        <>

      {/* Floating / Sticky Multi-Lesson Fusion Action Bar */}
      {selectedForFusion.length > 0 && (
        <div className="bg-gradient-to-r from-teal-50/90 via-emerald-50/90 to-teal-50/90 text-slate-900 p-4 sm:p-5 rounded-2xl shadow-lg border-2 border-teal-500/30 flex flex-col sm:flex-row items-center justify-between gap-4 animate-in slide-in-from-top-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#0F766E] flex items-center justify-center font-bold text-white shrink-0 shadow-md">
              <GitCompare className="w-5 h-5 text-teal-100" />
            </div>
            <div>
              <h4 className="text-xs sm:text-sm font-extrabold text-[#0F766E] flex items-center gap-2">
                <span>Đã chọn {selectedForFusion.length} bài học có nội dung tương tự</span>
                <span className="px-2 py-0.5 bg-[#0F766E] text-white rounded-md text-[10px] font-extrabold">AI Fusion</span>
              </h4>
              <p className="text-[11px] text-slate-600 mt-0.5">
                Sẵn sàng hợp nhất thông tin trùng lặp, đối chiếu khác biệt và phát hiện cờ cảnh báo ⚠️
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <button
              onClick={() => setSelectedForFusion([])}
              className="px-3.5 py-2 bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold rounded-xl border border-slate-200 transition-colors shadow-2xs"
            >
              Bỏ chọn
            </button>

            <button
              onClick={handleStartFusionFromHistory}
              disabled={selectedForFusion.length < 2 || fusionLoading}
              className={`px-5 py-2.5 rounded-xl text-xs font-bold shadow-md flex items-center justify-center gap-2 flex-1 sm:flex-initial transition-all ${
                selectedForFusion.length >= 2 && !fusionLoading
                  ? 'bg-[#0F766E] hover:bg-[#0D645E] text-white shadow-teal-700/20 hover:scale-105 active:scale-95'
                  : 'bg-slate-200 text-slate-400 cursor-not-allowed border border-slate-300'
              }`}
            >
              {fusionLoading ? (
                <Loader2 className="w-4 h-4 animate-spin text-teal-100" />
              ) : (
                <Sparkles className="w-4 h-4 text-teal-100" />
              )}
              <span>Hợp nhất AI ({selectedForFusion.length} bài)</span>
            </button>
          </div>
        </div>
      )}

      {/* Stats Summary Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-teal-50 text-[#0F766E] flex items-center justify-center">
            <BookOpen className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-medium">Tổng bài học đã lưu</p>
            <p className="text-2xl font-bold text-slate-900 mt-0.5">{safeHistoryList.length}</p>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
            <Award className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-medium">Lượt hoàn thành Quiz</p>
            <p className="text-2xl font-bold text-slate-900 mt-0.5">
              {historyList.reduce((acc, curr) => acc + (curr.quizHistory?.length || 0), 0)}
            </p>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <Brain className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-medium">Lần học gần nhất</p>
            <p className="text-sm font-bold text-slate-900 mt-1 truncate">
              {historyList[0] ? new Date(historyList[0].updatedAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) + ' hôm nay' : 'Chưa có'}
            </p>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row items-center justify-between gap-4">
        {/* Search */}
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm kiếm bài học, môn học..."
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20 transition-all"
          />
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-2 overflow-x-auto w-full sm:w-auto pb-1 sm:pb-0">
          <Filter className="w-4 h-4 text-slate-400 flex-shrink-0" />
          {[
            { id: 'ALL', label: 'Tất cả' },
            { id: 'PDF', label: 'Tài liệu PDF' },
            { id: 'VIDEO', label: 'Video' },
            { id: 'URL', label: 'Web' },
          ].map(tag => (
            <button
              key={tag.id}
              onClick={() => setSelectedTag(tag.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-all ${
                selectedTag === tag.id
                  ? 'bg-[#0F766E] text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200/70'
              }`}
            >
              {tag.label}
            </button>
          ))}
        </div>
      </div>

      {/* History Grid */}
      {filteredList.length === 0 ? (
        <div className="bg-white rounded-2xl p-12 text-center border border-slate-200/80 shadow-xs space-y-3">
          <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto">
            <History className="w-6 h-6" />
          </div>
          <h3 className="text-base font-bold text-slate-900">Không tìm thấy bài học nào</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            Thử thay đổi từ khóa tìm kiếm hoặc nhập bài học mới trong tab "Nhập tài liệu".
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredList.map((item) => {
            const bestScore = getBestQuizScore(item.quizHistory);
            const hasQuizAttempts = item.quizHistory && item.quizHistory.length > 0;
            const isSelected = selectedForFusion.includes(item.id);

            return (
              <div 
                key={item.id}
                className={`bg-white rounded-2xl border transition-all p-5 flex flex-col justify-between space-y-4 group ${
                  isSelected 
                    ? 'border-[#0F766E] ring-2 ring-[#0F766E]/20 bg-teal-50/20 shadow-md' 
                    : 'border-slate-200/80 hover:border-slate-300 shadow-xs hover:shadow-md'
                }`}
              >
                {/* Header info */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className="p-2 rounded-xl bg-slate-100 group-hover:bg-teal-50 transition-colors">
                        {getFileIcon(item.fileType)}
                      </div>
                      <span className="text-[11px] font-bold px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-600 uppercase tracking-wide">
                        {item.fileType || 'DOC'}
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => toggleSelectForFusion(item.id)}
                        className={`p-1.5 rounded-lg border transition-all flex items-center gap-1 text-[11px] font-bold ${
                          isSelected 
                            ? 'bg-[#0F766E] text-white border-[#0F766E] shadow-xs' 
                            : 'bg-slate-50 hover:bg-slate-100 text-slate-600 border-slate-200'
                        }`}
                        title="Chọn bài học để hợp nhất AI"
                      >
                        {isSelected ? <CheckSquare className="w-3.5 h-3.5 fill-white text-[#0F766E]" /> : <Square className="w-3.5 h-3.5 text-slate-400" />}
                        <span>{isSelected ? 'Đã chọn' : 'Hợp nhất'}</span>
                      </button>

                      <button
                        onClick={() => deleteHistoryItem(item.id)}
                        className="text-slate-400 hover:text-rose-600 p-1.5 rounded-lg hover:bg-rose-50 transition-colors"
                        title="Xóa khỏi lịch sử"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {/* Title */}
                  <h3 className="font-bold text-slate-900 text-base leading-snug line-clamp-2 group-hover:text-[#0F766E] transition-colors">
                    {item.title}
                  </h3>

                  {/* Tags */}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {item.tags.map((t, idx) => (
                        <span key={idx} className="text-[10px] font-medium bg-teal-50 text-[#0F766E] px-2 py-0.5 rounded-md">
                          #{t}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Status Badges */}
                <div className="pt-3 border-t border-slate-100 space-y-2">
                  <div className="flex items-center justify-between text-xs text-slate-500">
                    <span className="flex items-center gap-1 text-[11px]">
                      <Calendar className="w-3.5 h-3.5" />
                      {new Date(item.updatedAt).toLocaleDateString('vi-VN')}
                    </span>
                    {bestScore !== null && (
                      <button 
                        onClick={() => {
                          setModalTitle(item.title);
                          setActiveQuizModal(item.quizHistory || []);
                        }}
                        className="flex items-center gap-1 text-[11px] font-bold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-md hover:bg-amber-100 transition-colors"
                      >
                        <Award className="w-3 h-3" />
                        <span>Điểm cao nhất: {bestScore}%</span>
                      </button>
                    )}
                  </div>

                  {/* Study Pack Features Ready */}
                  <div className="flex items-center gap-3 text-[11px] text-slate-500 font-medium">
                    <span className="flex items-center gap-1 text-emerald-600">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Notes AI
                    </span>
                    <span className="flex items-center gap-1 text-teal-600">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Mindmap
                    </span>
                    <span className="flex items-center gap-1 text-indigo-600">
                      <CheckCircle2 className="w-3.5 h-3.5" /> Quiz
                    </span>
                  </div>
                </div>

                {/* Actions */}
                <div className="pt-2 flex items-center gap-2">
                  <button
                    onClick={() => continueLessonFromHistory(item.id)}
                    className="flex-1 bg-[#0F766E] hover:bg-[#0D645E] text-white text-xs font-bold py-2.5 px-4 rounded-xl shadow-xs transition-all flex items-center justify-center gap-2"
                  >
                    <BookOpen className="w-4 h-4" />
                    <span>Học tiếp ngay</span>
                  </button>

                  {hasQuizAttempts && (
                    <button
                      onClick={() => {
                        setModalTitle(item.title);
                        setActiveQuizModal(item.quizHistory || []);
                      }}
                      className="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition-colors"
                      title="Xem lịch sử điểm Quiz"
                    >
                      <BarChart3 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Quiz History Detail Modal */}
      {activeQuizModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4 border border-slate-200 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2 text-amber-600">
                <Award className="w-5 h-5" />
                <h3 className="font-bold text-slate-900 text-base">Lịch sử làm Quiz</h3>
              </div>
              <button 
                onClick={() => setActiveQuizModal(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-slate-500 font-medium truncate">
              Bài học: <span className="font-bold text-slate-800">{modalTitle}</span>
            </p>

            <div className="space-y-2.5 max-h-60 overflow-y-auto pr-1">
              {activeQuizModal.map((attempt, index) => (
                <div 
                  key={attempt.id || index}
                  className="bg-slate-50 p-3 rounded-xl border border-slate-200/70 flex items-center justify-between"
                >
                  <div>
                    <p className="text-xs font-bold text-slate-900">
                      Lần làm #{activeQuizModal.length - index}
                    </p>
                    <p className="text-[10px] text-slate-400 mt-0.5">
                      {new Date(attempt.completedAt).toLocaleString('vi-VN')}
                    </p>
                    {attempt.feedback && (
                      <p className="text-[11px] text-teal-700 mt-1 italic">
                        "{attempt.feedback}"
                      </p>
                    )}
                  </div>
                  <div className="text-right">
                    <span className={`text-base font-extrabold ${
                      attempt.score >= 80 ? 'text-emerald-600' : 'text-amber-600'
                    }`}>
                      {attempt.score}%
                    </span>
                    <p className="text-[10px] text-slate-500">
                      {attempt.correctCount}/{attempt.totalQuestions} đúng
                    </p>
                  </div>
                </div>
              ))}
            </div>

            <button
              onClick={() => setActiveQuizModal(null)}
              className="w-full bg-[#0F766E] hover:bg-[#0D645E] text-white font-bold text-xs py-2.5 rounded-xl transition-colors shadow-xs"
            >
              Đóng
            </button>
          </div>
        </div>
      )}
        </>
      )}
    </div>
  );
}
