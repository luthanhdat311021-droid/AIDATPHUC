import React from 'react';
import { X, Flame, Award, CheckCircle2, Lock } from 'lucide-react';
import { ALL_BADGES, GamificationData } from '../../utils/gamification';

interface StreakModalProps {
  isOpen: boolean;
  onClose: () => void;
  gamificationData: GamificationData;
}

export function StreakModal({ isOpen, onClose, gamificationData }: StreakModalProps) {
  if (!isOpen) return null;

  const unlockedIds = gamificationData.unlockedBadgeIds || [];

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl border border-slate-100 dark:border-slate-700 relative space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-700 pb-3">
          <div className="flex items-center gap-2">
            <span className="text-xl">🔥</span>
            <h3 className="font-bold text-base text-slate-800 dark:text-slate-100">Chuỗi ngày học & Thành tích</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Big Streak Card Hero */}
        <div className="bg-gradient-to-br from-amber-500 via-orange-500 to-rose-500 rounded-2xl p-6 text-white text-center shadow-lg relative overflow-hidden">
          <div className="absolute top-0 right-0 -mr-6 -mt-6 w-32 h-32 bg-white/10 rounded-full blur-xl pointer-events-none" />
          <div className="relative z-10 space-y-2">
            <div className="w-16 h-16 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center mx-auto text-3xl shadow-inner animate-bounce">
              🔥
            </div>
            <h4 className="text-3xl font-black tracking-tight">
              {gamificationData.streak} Ngày Liên Tiếp!
            </h4>
            <p className="text-xs text-orange-100 max-w-xs mx-auto">
              {gamificationData.streak >= 7
                ? 'Tuyệt vời! Bạn đang duy trì kỷ luật học tập phi thường.'
                : 'Hãy duy trì thói quen học mỗi ngày để mở khóa thêm các huy hiệu vinh danh!'}
            </p>
          </div>
        </div>

        {/* Badges Grid */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold text-slate-700 dark:text-slate-200 uppercase tracking-wider flex items-center gap-1.5">
              <Award className="w-4 h-4 text-amber-500" />
              <span>Huy hiệu học tập ({unlockedIds.length}/{ALL_BADGES.length})</span>
            </h4>
          </div>

          <div className="grid grid-cols-2 gap-3 max-h-60 overflow-y-auto pr-1">
            {ALL_BADGES.map((b) => {
              const isUnlocked = unlockedIds.includes(b.id);
              return (
                <div
                  key={b.id}
                  className={`p-3 rounded-2xl border transition-all flex items-start gap-2.5 ${
                    isUnlocked
                      ? 'bg-amber-50/50 dark:bg-amber-950/20 border-amber-200 dark:border-amber-800/40 text-slate-800 dark:text-slate-100 shadow-2xs'
                      : 'bg-slate-50 dark:bg-slate-900/40 border-slate-200/60 dark:border-slate-800 text-slate-400 dark:text-slate-500 opacity-60'
                  }`}
                >
                  <div className={`text-2xl p-1.5 rounded-xl shrink-0 ${isUnlocked ? 'bg-amber-100 dark:bg-amber-900/40' : 'bg-slate-200 dark:bg-slate-800 grayscale'}`}>
                    {b.icon}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between">
                      <p className="font-bold text-xs truncate">{b.name}</p>
                      {isUnlocked ? (
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                      ) : (
                        <Lock className="w-3 h-3 text-slate-400 shrink-0" />
                      )}
                    </div>
                    <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug line-clamp-2">
                      {b.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Motivation Footer */}
        <div className="bg-slate-50 dark:bg-slate-900/50 p-3 rounded-2xl border border-slate-100 dark:border-slate-700/60 text-center">
          <p className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">
            💡 Mỗi bài học, flashcard và câu trắc nghiệm bạn hoàn thành đều tích lũy vào thành tích cá nhân!
          </p>
        </div>
      </div>
    </div>
  );
}
