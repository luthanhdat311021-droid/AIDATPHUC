export interface Badge {
  id: string;
  name: string;
  description: string;
  icon: string;
  unlockedAt?: string;
}

export interface GamificationData {
  streak: number;
  lastActiveDate: string;
  pomodoroSessions: number;
  cardsReviewed: number;
  perfectQuizzes: number;
  unlockedBadgeIds: string[];
}

export const ALL_BADGES: Badge[] = [
  { id: 'first_step', name: 'Khởi đầu nan', description: 'Bắt đầu bài học đầu tiên trên StudyMind', icon: '🎯' },
  { id: 'streak_3', name: 'Ngọn lửa bùng cháy', description: 'Duy trì chuỗi học tập 3 ngày liên tiếp', icon: '🔥' },
  { id: 'streak_7', name: 'Kỷ luật thép', description: 'Duy trì chuỗi học tập 7 ngày liên tiếp', icon: '⚡' },
  { id: 'pomodoro_master', name: 'Bậc thầy Tập trung', description: 'Hoàn thành 4 phiên Pomodoro trong ngày', icon: '⏱️' },
  { id: 'card_master', name: 'Trí nhớ siêu phàm', description: 'Đã ôn tập hơn 20 lượt thẻ Flashcard', icon: '🃏' },
  { id: 'quiz_ace', name: 'Điểm 10 hoàn hảo', description: 'Đạt điểm tuyệt đối 100% trong bài trắc nghiệm', icon: '🏆' }
];

const STORAGE_KEY = 'studymind_gamification_v1';

function getTodayString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function getYesterdayString(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function loadGamificationData(): GamificationData {
  if (typeof window === 'undefined') {
    return { streak: 1, lastActiveDate: getTodayString(), pomodoroSessions: 0, cardsReviewed: 0, perfectQuizzes: 0, unlockedBadgeIds: ['first_step'] };
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch (e) {
    console.warn('Error reading gamification data:', e);
  }
  const initial: GamificationData = {
    streak: 1,
    lastActiveDate: getTodayString(),
    pomodoroSessions: 0,
    cardsReviewed: 0,
    perfectQuizzes: 0,
    unlockedBadgeIds: ['first_step']
  };
  saveGamificationData(initial);
  return initial;
}

export function saveGamificationData(data: GamificationData) {
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (e) {
    console.warn('Error saving gamification data:', e);
  }
}

export function updateActivityStreak(): { data: GamificationData; newBadges: Badge[] } {
  const data = loadGamificationData();
  const today = getTodayString();
  const yesterday = getYesterdayString();

  if (data.lastActiveDate === today) {
    // Already updated today
  } else if (data.lastActiveDate === yesterday) {
    // Continued streak!
    data.streak += 1;
    data.lastActiveDate = today;
  } else {
    // Broke streak
    data.streak = 1;
    data.lastActiveDate = today;
  }

  const newBadges = checkAndUnlockBadges(data);
  saveGamificationData(data);
  return { data, newBadges };
}

export function recordPomodoroSessionCompleted(): { data: GamificationData; newBadges: Badge[] } {
  const data = loadGamificationData();
  data.pomodoroSessions = (data.pomodoroSessions || 0) + 1;
  const newBadges = checkAndUnlockBadges(data);
  saveGamificationData(data);
  return { data, newBadges };
}

export function recordCardReviewed(): { data: GamificationData; newBadges: Badge[] } {
  const data = loadGamificationData();
  data.cardsReviewed = (data.cardsReviewed || 0) + 1;
  const newBadges = checkAndUnlockBadges(data);
  saveGamificationData(data);
  return { data, newBadges };
}

export function recordQuizCompleted(scorePct: number): { data: GamificationData; newBadges: Badge[] } {
  const data = loadGamificationData();
  if (scorePct >= 100) {
    data.perfectQuizzes = (data.perfectQuizzes || 0) + 1;
  }
  const newBadges = checkAndUnlockBadges(data);
  saveGamificationData(data);
  return { data, newBadges };
}

function checkAndUnlockBadges(data: GamificationData): Badge[] {
  const newlyUnlocked: Badge[] = [];
  data.unlockedBadgeIds = data.unlockedBadgeIds || [];

  const tryUnlock = (badgeId: string, condition: boolean) => {
    if (condition && !data.unlockedBadgeIds.includes(badgeId)) {
      data.unlockedBadgeIds.push(badgeId);
      const b = ALL_BADGES.find(x => x.id === badgeId);
      if (b) newlyUnlocked.push(b);
    }
  };

  tryUnlock('first_step', true);
  tryUnlock('streak_3', data.streak >= 3);
  tryUnlock('streak_7', data.streak >= 7);
  tryUnlock('pomodoro_master', data.pomodoroSessions >= 4);
  tryUnlock('card_master', data.cardsReviewed >= 20);
  tryUnlock('quiz_ace', data.perfectQuizzes >= 1);

  return newlyUnlocked;
}
