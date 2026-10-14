package com.studymind.nativeapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Daily streak, Pomodoro / flashcard / quiz counters and badges, kept on the device —
 * Java port of src/utils/gamification.ts (same rules, same badges).
 */
public final class Gamification {
    private Gamification() {}

    public static class Badge {
        public final String id, name, description, icon;

        Badge(String id, String name, String description, String icon) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.icon = icon;
        }
    }

    public static class Data {
        public int streak = 1;
        public String lastActiveDate = today();
        public int pomodoroSessions;
        public int cardsReviewed;
        public int perfectQuizzes;
        public List<String> unlockedBadgeIds = new ArrayList<>();
    }

    public static final Badge[] ALL_BADGES = {
            new Badge("first_step", "Khởi đầu nan", "Bắt đầu bài học đầu tiên trên StudyMind", "🎯"),
            new Badge("streak_3", "Ngọn lửa bùng cháy", "Duy trì chuỗi học tập 3 ngày liên tiếp", "🔥"),
            new Badge("streak_7", "Kỷ luật thép", "Duy trì chuỗi học tập 7 ngày liên tiếp", "⚡"),
            new Badge("pomodoro_master", "Bậc thầy Tập trung", "Hoàn thành 4 phiên Pomodoro trong ngày", "⏱️"),
            new Badge("card_master", "Trí nhớ siêu phàm", "Đã ôn tập hơn 20 lượt thẻ Flashcard", "🃏"),
            new Badge("quiz_ace", "Điểm 10 hoàn hảo", "Đạt điểm tuyệt đối 100% trong bài trắc nghiệm", "🏆")
    };

    private static final String KEY = "studymind_gamification_v1";
    private static final Gson GSON = new Gson();
    private static SharedPreferences prefs;

    public static void init(Context c) { prefs = c.getSharedPreferences("studymind_prefs", Context.MODE_PRIVATE); }

    private static String day(int offset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    private static String today() { return day(0); }

    public static Data load() {
        String raw = prefs.getString(KEY, null);
        if (raw != null) {
            try {
                Data d = GSON.fromJson(raw, Data.class);
                if (d.unlockedBadgeIds == null) d.unlockedBadgeIds = new ArrayList<>();
                return d;
            } catch (RuntimeException ignored) {}
        }
        Data initial = new Data();
        initial.unlockedBadgeIds.add("first_step");
        save(initial);
        return initial;
    }

    private static void save(Data d) { prefs.edit().putString(KEY, GSON.toJson(d)).apply(); }

    /** Call once per app open; returns badges unlocked by this call */
    public static List<Badge> updateActivityStreak() {
        Data d = load();
        String today = today();
        if (!today.equals(d.lastActiveDate)) {
            d.streak = day(-1).equals(d.lastActiveDate) ? d.streak + 1 : 1;
            d.lastActiveDate = today;
        }
        return commit(d);
    }

    public static List<Badge> recordPomodoroSessionCompleted() {
        Data d = load();
        d.pomodoroSessions++;
        return commit(d);
    }

    public static List<Badge> recordCardReviewed() {
        Data d = load();
        d.cardsReviewed++;
        return commit(d);
    }

    public static List<Badge> recordQuizCompleted(int scorePct) {
        Data d = load();
        if (scorePct >= 100) d.perfectQuizzes++;
        return commit(d);
    }

    private static List<Badge> commit(Data d) {
        List<Badge> unlocked = new ArrayList<>();
        tryUnlock(d, unlocked, "first_step", true);
        tryUnlock(d, unlocked, "streak_3", d.streak >= 3);
        tryUnlock(d, unlocked, "streak_7", d.streak >= 7);
        tryUnlock(d, unlocked, "pomodoro_master", d.pomodoroSessions >= 4);
        tryUnlock(d, unlocked, "card_master", d.cardsReviewed >= 20);
        tryUnlock(d, unlocked, "quiz_ace", d.perfectQuizzes >= 1);
        save(d);
        return unlocked;
    }

    private static void tryUnlock(Data d, List<Badge> out, String id, boolean condition) {
        if (!condition || d.unlockedBadgeIds.contains(id)) return;
        d.unlockedBadgeIds.add(id);
        for (Badge b : ALL_BADGES) if (b.id.equals(id)) out.add(b);
    }
}
