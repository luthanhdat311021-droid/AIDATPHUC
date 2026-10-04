import React, { useState, useEffect, useRef } from 'react';
import { Play, Pause, RotateCcw, Volume2, VolumeX, X, Minimize2, Maximize2, Sparkles, CheckCircle2 } from 'lucide-react';
import { recordPomodoroSessionCompleted } from '../../utils/gamification';
import { notificationService } from '../../utils/notificationService';

interface PomodoroModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSessionComplete?: () => void;
}

type Mode = 'work' | 'short_break' | 'long_break';

const MODE_DURATIONS: Record<Mode, number> = {
  work: 25 * 60,
  short_break: 5 * 60,
  long_break: 15 * 60
};

// Pure Web Audio API chime bell
function playChimeSound() {
  try {
    const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.type = 'sine';
    osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
    osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.25); // A5
    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 1.2);
    osc.start();
    osc.stop(ctx.currentTime + 1.2);
  } catch (e) {
    console.warn('Audio chime error:', e);
  }
}

export function PomodoroModal({ isOpen, onClose, onSessionComplete }: PomodoroModalProps) {
  const [mode, setMode] = useState<Mode>('work');
  const [timeLeft, setTimeLeft] = useState<number>(MODE_DURATIONS.work);
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [isMinimized, setIsMinimized] = useState<boolean>(false);
  const [sessionsCompletedToday, setSessionsCompletedToday] = useState<number>(0);
  const [soundEnabled, setSoundEnabled] = useState<boolean>(true);

  const timerRef = useRef<any>(null);

  // Timer countdown
  useEffect(() => {
    if (isRunning) {
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            handleComplete();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isRunning, mode]);

  const handleComplete = () => {
    setIsRunning(false);
    if (soundEnabled) {
      playChimeSound();
    }

    if (mode === 'work') {
      const { newBadges } = recordPomodoroSessionCompleted();
      setSessionsCompletedToday((c) => c + 1);
      if (onSessionComplete) onSessionComplete();

      notificationService.scheduleNotification({
        title: 'StudyMind AI - Phiên tập trung hoàn tất! 🎉',
        body: 'Tuyệt vời! Bạn đã hoàn thành 25 phút tập trung. Hãy nghỉ ngơi 5 phút nhé!',
        delaySeconds: 1
      });

      // Switch to break
      setMode('short_break');
      setTimeLeft(MODE_DURATIONS.short_break);
    } else {
      notificationService.scheduleNotification({
        title: 'StudyMind AI - Hết giờ nghỉ giải lao ⏰',
        body: 'Đã sẵn sàng cho phiên tập trung tiếp theo chưa? Bắt đầu ngay nhé!',
        delaySeconds: 1
      });
      setMode('work');
      setTimeLeft(MODE_DURATIONS.work);
    }
  };

  const handleSwitchMode = (newMode: Mode) => {
    setIsRunning(false);
    setMode(newMode);
    setTimeLeft(MODE_DURATIONS[newMode]);
  };

  const handleReset = () => {
    setIsRunning(false);
    setTimeLeft(MODE_DURATIONS[mode]);
  };

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  const total = MODE_DURATIONS[mode];
  const progressPercent = ((total - timeLeft) / total) * 100;

  if (!isOpen) return null;

  // Minimized floating bubble
  if (isMinimized) {
    return (
      <div className="fixed bottom-6 right-6 z-50 animate-in fade-in slide-in-from-bottom-4">
        <div className="bg-[#0F766E] text-white p-3 rounded-2xl shadow-xl flex items-center gap-3 border border-teal-500/30">
          <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center font-bold text-xs">
            {mode === 'work' ? '🎯' : '☕'}
          </div>
          <div>
            <p className="text-[10px] text-teal-100 font-medium leading-none">
              {mode === 'work' ? 'Đang tập trung' : 'Nghỉ giải lao'}
            </p>
            <p className="text-base font-extrabold font-mono leading-tight">{formattedTime}</p>
          </div>
          <button
            onClick={() => setIsRunning(!isRunning)}
            className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition-colors"
            title={isRunning ? 'Tạm dừng' : 'Tiếp tục'}
          >
            {isRunning ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
          </button>
          <button
            onClick={() => setIsMinimized(false)}
            className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition-colors"
            title="Phóng to"
          >
            <Maximize2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-slate-800 rounded-3xl max-w-sm w-full p-6 shadow-2xl border border-slate-100 dark:border-slate-700 relative text-center space-y-6">
        {/* Header Controls */}
        <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-700 pb-3">
          <div className="flex items-center gap-2">
            <span className="text-lg">⏱️</span>
            <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100">Đồng hồ Pomodoro</h3>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setSoundEnabled(!soundEnabled)}
              className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
              title={soundEnabled ? 'Tắt âm thanh' : 'Bật âm thanh chuông'}
            >
              {soundEnabled ? <Volume2 className="w-4 h-4 text-[#0F766E]" /> : <VolumeX className="w-4 h-4" />}
            </button>
            <button
              onClick={() => setIsMinimized(true)}
              className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
              title="Thu nhỏ thành bong bóng"
            >
              <Minimize2 className="w-4 h-4" />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
              title="Đóng"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Mode Selector Tabs */}
        <div className="flex bg-slate-100 dark:bg-slate-900/60 p-1 rounded-xl">
          {[
            { id: 'work' as Mode, label: 'Tập trung (25p)' },
            { id: 'short_break' as Mode, label: 'Nghỉ ngắn (5p)' },
            { id: 'long_break' as Mode, label: 'Nghỉ dài (15p)' }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => handleSwitchMode(tab.id)}
              className={`flex-1 py-1.5 text-[11px] font-bold rounded-lg transition-all ${
                mode === tab.id
                  ? 'bg-white dark:bg-slate-800 text-[#0F766E] dark:text-teal-400 shadow-xs'
                  : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-300'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Big Circular Timer Display */}
        <div className="relative w-48 h-48 mx-auto flex items-center justify-center">
          <svg className="w-full h-full transform -rotate-90" viewBox="0 0 100 100">
            <circle
              cx="50"
              cy="50"
              r="44"
              className="text-slate-100 dark:text-slate-700"
              strokeWidth="6"
              stroke="currentColor"
              fill="transparent"
            />
            <circle
              cx="50"
              cy="50"
              r="44"
              className={mode === 'work' ? 'text-[#0F766E]' : 'text-amber-500'}
              strokeWidth="6"
              strokeDasharray={276.46}
              strokeDashoffset={276.46 - (276.46 * progressPercent) / 100}
              strokeLinecap="round"
              stroke="currentColor"
              fill="transparent"
              style={{ transition: 'stroke-dashoffset 0.5s ease' }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-4xl font-black font-mono tracking-tight text-slate-900 dark:text-white">
              {formattedTime}
            </span>
            <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 mt-1">
              {mode === 'work' ? '🎯 Tập trung cao độ' : '☕ Thư giãn đầu óc'}
            </span>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center justify-center gap-4">
          <button
            onClick={handleReset}
            className="p-3 rounded-2xl bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 transition-all hover:scale-105"
            title="Đặt lại thời gian"
          >
            <RotateCcw className="w-5 h-5" />
          </button>

          <button
            onClick={() => setIsRunning(!isRunning)}
            className="flex-1 py-3.5 px-6 rounded-2xl bg-[#0F766E] hover:bg-[#0D645E] text-white font-bold text-sm shadow-lg shadow-[#0F766E]/20 flex items-center justify-center gap-2 hover:scale-[1.02] active:scale-95 transition-all"
          >
            {isRunning ? (
              <>
                <Pause className="w-5 h-5" />
                <span>Tạm dừng</span>
              </>
            ) : (
              <>
                <Play className="w-5 h-5 fill-white" />
                <span>{timeLeft === total ? 'Bắt đầu ngay' : 'Tiếp tục'}</span>
              </>
            )}
          </button>
        </div>

        {/* Daily Stats Counter */}
        <div className="bg-slate-50 dark:bg-slate-900/40 p-3 rounded-2xl border border-slate-100 dark:border-slate-700/60 flex items-center justify-between text-xs">
          <span className="text-slate-500 dark:text-slate-400 font-medium">Phiên hoàn thành hôm nay:</span>
          <span className="font-bold text-[#0F766E] dark:text-teal-400 flex items-center gap-1">
            <CheckCircle2 className="w-4 h-4" />
            <span>{sessionsCompletedToday} phiên ({(sessionsCompletedToday * 25)} phút)</span>
          </span>
        </div>
      </div>
    </div>
  );
}
