import React, { useState } from 'react';
import { Plus, FileText, Layers, Award, ArrowRight, CheckCircle2, Flame, Folder, Timer, Bell, Sparkles } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { PomodoroModal } from '../common/PomodoroModal';
import { StreakModal } from '../common/StreakModal';
import { loadGamificationData, ALL_BADGES } from '../../utils/gamification';

export function DashboardView() {
  const { stats, setActiveTab, fetchDocumentDetail } = useStudy();
  const [isPomodoroOpen, setIsPomodoroOpen] = useState(false);
  const [isStreakOpen, setIsStreakOpen] = useState(false);
  const [gamification, setGamification] = useState(loadGamificationData);

  const handleOpenDoc = (docId: string) => {
    fetchDocumentDetail(docId);
    setActiveTab('workspace');
  };

  const refreshGamification = () => {
    setGamification(loadGamificationData());
  };

  return (
    <>
    <div className="p-4 md:p-8 space-y-6 max-w-7xl mx-auto">
      {/* Top Banner Hero */}
      <div className="bg-gradient-to-r from-[#0B4F48] to-[#0F766E] text-white p-6 md:p-8 rounded-2xl shadow-md relative overflow-hidden">
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 bg-white/5 rounded-full blur-2xl pointer-events-none" />
        
        <div className="max-w-2xl space-y-3 relative z-10">
          <h2 className="text-xl md:text-2xl font-bold tracking-tight">
            Hôm nay bạn muốn học gì mới?
          </h2>
          <p className="text-xs md:text-sm text-teal-100/90 leading-relaxed">
            Tải lên tài liệu của bạn (PDF, PPT, link video...) để StudyMind tự động tạo ghi chú, sơ đồ tư duy, flashcard và bộ câu hỏi trắc nghiệm ngay lập tức.
          </p>
          <div className="pt-2 flex flex-wrap items-center gap-3">
            <button
              onClick={() => setActiveTab('import')}
              className="bg-white hover:bg-teal-50 text-[#0F766E] font-bold text-xs md:text-sm px-5 py-2.5 rounded-lg shadow-sm transition-all hover:scale-102 flex items-center gap-2 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Tạo bộ học liệu mới</span>
            </button>

            <button
              onClick={() => setIsPomodoroOpen(true)}
              className="bg-teal-800/80 hover:bg-teal-800 text-white font-bold text-xs md:text-sm px-4 py-2.5 rounded-lg border border-teal-600/50 shadow-sm transition-all hover:scale-102 flex items-center gap-2 cursor-pointer"
            >
              <Timer className="w-4 h-4 text-teal-200" />
              <span>Bật Pomodoro Focus (25p)</span>
            </button>

            <button
              onClick={() => { refreshGamification(); setIsStreakOpen(true); }}
              className="bg-orange-500/80 hover:bg-orange-500 text-white font-bold text-xs md:text-sm px-4 py-2.5 rounded-lg border border-orange-400/50 shadow-sm transition-all hover:scale-102 flex items-center gap-1.5 cursor-pointer"
            >
              <Flame className="w-4 h-4 fill-white" />
              <span>Chuỗi {gamification.streak} ngày</span>
            </button>
          </div>
        </div>
      </div>

      {/* 3 Stat Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Card 1 */}
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-xs font-medium text-[#4B5563]">Tài liệu đã tải</p>
            <h3 className="text-2xl font-bold text-[#111827]">
              {stats?.totalDocuments ?? 0}
            </h3>
            <p className="text-[11px] font-semibold text-[#10B981]">
              +{stats?.weeklyDocAdded ?? 0} tài liệu tuần này
            </p>
          </div>
          <div className="w-10 h-10 rounded-xl bg-teal-50 text-[#0F766E] flex items-center justify-center">
            <FileText className="w-5 h-5" />
          </div>
        </div>

        {/* Card 2 */}
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-xs font-medium text-[#4B5563]">Flashcard đã học</p>
            <h3 className="text-2xl font-bold text-[#111827]">
              {stats?.flashcardProgress ?? '0/0'}
            </h3>
            <p className="text-[11px] font-medium text-slate-500">
              Tỷ lệ ghi nhớ:{' '}
              {stats?.retentionRatePercentage != null
                ? <span className="font-bold text-[#F59E0B]">{stats.retentionRatePercentage}%</span>
                : <span className="text-slate-400">chưa có dữ liệu</span>}
            </p>
          </div>
          <div className="w-10 h-10 rounded-xl bg-amber-50 text-[#F59E0B] flex items-center justify-center">
            <Layers className="w-5 h-5" />
          </div>
        </div>

        {/* Card 3 */}
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-xs font-medium text-[#4B5563]">Điểm trắc nghiệm TB</p>
            <h3 className="text-2xl font-bold text-[#111827]">
              {stats?.averageQuizScore ?? '—'}
            </h3>
            <p className="text-[11px] font-semibold text-[#10B981]">
              {stats?.quizScoreDiff ?? (stats?.quizAttempts ? `${stats.quizAttempts} lượt làm bài` : 'Chưa làm bài trắc nghiệm nào')}
            </p>
          </div>
          <div className="w-10 h-10 rounded-xl bg-emerald-50 text-[#10B981] flex items-center justify-center">
            <Award className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Gamification & Daily Focus Highlights Bar */}
      <div className="bg-white dark:bg-slate-800 p-5 rounded-2xl border border-slate-200/80 dark:border-slate-700 shadow-xs flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-orange-100 dark:bg-orange-950/40 text-orange-600 flex items-center justify-center text-2xl shadow-inner shrink-0">
            🔥
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="font-bold text-sm text-slate-900 dark:text-white">
                Chuỗi ngày học liên tục: <span className="text-orange-500">{gamification.streak} ngày</span>
              </h4>
              <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300">
                Đang kích hoạt
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Đã hoàn thành {gamification.pomodoroSessions} phiên Pomodoro • Ôn tập {gamification.cardsReviewed} lượt thẻ ghi nhớ
            </p>
          </div>
        </div>

        {/* Badges preview */}
        <div className="flex items-center gap-2">
          <div className="flex -space-x-1.5 overflow-hidden">
            {ALL_BADGES.map(b => {
              const isUnlocked = (gamification.unlockedBadgeIds || []).includes(b.id);
              return (
                <div
                  key={b.id}
                  title={`${b.name}: ${b.description}`}
                  className={`w-8 h-8 rounded-full border-2 border-white dark:border-slate-800 flex items-center justify-center text-sm shadow-xs ${
                    isUnlocked ? 'bg-amber-100 dark:bg-amber-900/60' : 'bg-slate-200 dark:bg-slate-700 grayscale opacity-40'
                  }`}
                >
                  {b.icon}
                </div>
              );
            })}
          </div>
          <button
            onClick={() => { refreshGamification(); setIsStreakOpen(true); }}
            className="text-xs font-bold text-[#0F766E] dark:text-teal-400 hover:underline ml-2 cursor-pointer"
          >
            Xem tất cả huy hiệu →
          </button>
        </div>
      </div>

      {/* Middle Row: Recent Materials & Weekly Goals */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Study Materials (2 cols) */}
        <div className="lg:col-span-2 bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="font-bold text-base text-[#111827]">Bộ học liệu gần đây</h3>
            <button
              onClick={() => setActiveTab('history')}
              className="text-xs font-semibold text-[#0F766E] hover:underline"
            >
              Xem tất cả
            </button>
          </div>

          <div className="space-y-3">
            {stats?.recentDocuments?.map((doc) => (
              <div
                key={doc.id}
                onClick={() => handleOpenDoc(doc.id)}
                className="p-4 rounded-xl border border-slate-100 hover:border-slate-300 hover:bg-slate-50/80 transition-all cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3 group"
              >
                <div className="space-y-1">
                  <h4 className="font-semibold text-sm text-[#111827] group-hover:text-[#0F766E] transition-colors">
                    {doc.title}
                  </h4>
                  <p className="text-xs text-slate-500">
                    {doc.fileType} • {doc.pageCount ? `${doc.pageCount} trang` : doc.duration} • Cập nhật {doc.updatedAt}
                  </p>
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  {doc.tags?.map((tag, idx) => {
                    const isSubject = !['AI Analysis Engine', 'YouTube Speech-to-Text', 'Web Article Extractor'].includes(tag);
                    return (
                      <span
                        key={idx}
                        className={`text-[11px] px-2.5 py-1 rounded-full font-semibold flex items-center gap-1 ${
                          isSubject
                            ? 'bg-emerald-100 text-emerald-800 font-bold'
                            : tag.includes('Mindmap')
                            ? 'bg-teal-50 text-[#0F766E]'
                            : tag.includes('Quiz')
                            ? 'bg-emerald-50 text-emerald-700'
                            : 'bg-amber-50 text-amber-700'
                        }`}
                      >
                        {isSubject && <Folder className="w-2.5 h-2.5" />}
                        <span>{tag}</span>
                      </span>
                    );
                  })}
                  <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-[#0F766E] group-hover:translate-x-1 transition-all" />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Weekly Goals Progress (1 col) */}
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-5">
          <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
            Tiến độ mục tiêu tuần
          </h3>

          <div className="space-y-4">
            {/* Weekly quiz goal (study time is not tracked, so there is no hours bar) */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs font-semibold">
                <span className="text-slate-600">Số câu trắc nghiệm</span>
                <span className="text-[#10B981] font-bold">
                  {stats?.weeklyQuizCount?.current ?? 0} / {stats?.weeklyQuizCount?.target ?? 50} câu
                </span>
              </div>
              <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#10B981] h-full rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, ((stats?.weeklyQuizCount?.current ?? 0) / (stats?.weeklyQuizCount?.target || 50)) * 100)}%` }}
                />
              </div>
            </div>
          </div>

          <div className="pt-2">
            <div className="p-3 rounded-lg bg-teal-50/60 border border-teal-100 flex items-center gap-3">
              <Flame className="w-5 h-5 text-amber-500 shrink-0" />
              <p className="text-xs text-teal-900 font-medium leading-snug">
                {stats?.streakDays
                  ? <>Bạn đã duy trì chuỗi học <span className="font-bold text-[#0F766E]">{stats.streakDays} ngày liên tục!</span> Cố lên nào!</>
                  : 'Học hoặc làm trắc nghiệm hôm nay để bắt đầu chuỗi ngày học liên tục.'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Row: Spaced Repetition & Recent Activities */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Spaced Repetition Items (2 cols) */}
        <div className="lg:col-span-2 bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-4">
          <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
            Nội dung cần ôn tập hôm nay (Spaced Repetition)
          </h3>

          <div className="space-y-3">
            {stats?.spacedRepetitionItems?.map((item) => (
              <div
                key={item.id}
                className="p-4 rounded-xl border border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-50/80 transition-colors"
              >
                <div className="space-y-1">
                  <h4 className="font-semibold text-sm text-[#111827]">
                    {item.title}
                  </h4>
                  <p className="text-xs text-slate-500">
                    Mức độ ghi nhớ: <span className="font-semibold text-amber-600">{item.memoryLevel}</span> • {item.actionNeeded}
                  </p>
                </div>
                <button
                  onClick={() => {
                    if (item.buttonText.includes('sơ đồ')) setActiveTab('mindmap');
                    else setActiveTab('flashcard');
                  }}
                  className={`px-4 py-2 rounded-lg text-xs font-bold transition-all shadow-xs shrink-0 ${
                    item.buttonText.includes('sơ đồ')
                      ? 'border border-[#0F766E] text-[#0F766E] hover:bg-teal-50'
                      : 'bg-[#0F766E] hover:bg-[#0D5C53] text-white'
                  }`}
                >
                  {item.buttonText}
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Recent Activity Stream (1 col) */}
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs space-y-4">
          <h3 className="font-bold text-base text-[#111827] border-b border-slate-100 pb-3">
            Hoạt động gần đây
          </h3>

          <div className="space-y-4 relative before:absolute before:inset-0 before:left-2.5 before:w-0.5 before:bg-slate-100">
            {stats?.recentActivities?.map((act) => (
              <div key={act.id} className="flex gap-3 relative z-10 text-xs">
                <div className="w-5 h-5 rounded-full bg-teal-100 text-[#0F766E] flex items-center justify-center shrink-0 ring-4 ring-white">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                </div>
                <div>
                  <p className="font-medium text-[#111827]">{act.text}</p>
                  <p className="text-[11px] text-slate-400 mt-0.5">{act.time}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>

    {/* Pomodoro Focus Timer Modal */}
    <PomodoroModal
      isOpen={isPomodoroOpen}
      onClose={() => setIsPomodoroOpen(false)}
      onSessionComplete={refreshGamification}
    />

    {/* Daily Streak & Badges Modal */}
    <StreakModal
      isOpen={isStreakOpen}
      onClose={() => setIsStreakOpen(false)}
      gamificationData={gamification}
    />
    </>
  );
}
