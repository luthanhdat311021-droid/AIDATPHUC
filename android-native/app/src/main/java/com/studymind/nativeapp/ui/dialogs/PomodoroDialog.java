package com.studymind.nativeapp.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.FocusRewards;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.ui.Ui;

/**
 * "Đồng hồ Pomodoro" — PomodoroModal.tsx: work 25' / short break 5' / long break 15', circular
 * progress ring, chime on completion, completed focus sessions counted toward the badges.
 * The timer keeps running while the dialog is closed (like the web's minimised bubble).
 */
public final class PomodoroDialog {
    private static final String[] MODES = {"work", "short_break", "long_break"};
    private static final String[] LABELS = {"Tập trung (25p)", "Nghỉ ngắn (5p)", "Nghỉ dài (15p)"};
    private static final int[] SECONDS = {25 * 60, 5 * 60, 15 * 60};

    // Shared across openings so a running session survives closing the dialog
    private static int mode;
    private static long timeLeftMs = SECONDS[0] * 1000L;
    private static boolean running;
    private static boolean sound = true;
    private static int sessionsToday;
    private static CountDownTimer timer;
    private static PomodoroDialog visible;

    private final AppCompatActivity activity;
    private final LinearLayout box;
    private Dialog dialog;

    public static void show(AppCompatActivity activity) { new PomodoroDialog(activity); }

    private static final int DAILY_GOAL = 8;

    private PomodoroDialog(AppCompatActivity activity) {
        this.activity = activity;
        Context c = activity;
        box = Ui.vertical(c);
        box.setBackgroundColor(Ui.color(c, R.color.bg));
        int p = Ui.dp(c, 16);
        box.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 24));
        android.widget.ScrollView scroll = new android.widget.ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.color(c, R.color.bg));
        scroll.addView(box);
        // Full-screen window is edge-to-edge: keep the content clear of the status and navigation bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        dialog = new Dialog(c, android.R.style.Theme_Material_Light_NoActionBar);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Ui.color(c, R.color.bg)));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setStatusBarColor(Ui.color(c, R.color.bg));
            new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView())
                    .setAppearanceLightStatusBars(!StudyMindAppDoc.dark());
        }
        dialog.setOnDismissListener(d -> visible = null);
        visible = this;
        render();
        dialog.show();
    }

    private void render() {
        Context c = activity;
        box.removeAllViews();
        int total = SECONDS[mode];
        long left = timeLeftMs / 1000;

        // ---- App bar ----
        LinearLayout head = Ui.horizontal(c);
        head.setMinimumHeight(Ui.dp(c, 56));
        ImageView back = Ui.icon(c, R.drawable.ic_chevron_left, 26, Ui.color(c, R.color.text));
        back.setOnClickListener(v -> dialog.dismiss());
        head.addView(back);
        TextView title = Ui.text(c, "Tập trung Pomodoro", 18, R.color.text, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        head.addView(title, tl);
        LinearLayout streak = Ui.iconText(c, R.drawable.ic_flame, Gamification.load().streak + " Ngày", 13, Ui.color(c, R.color.m3_on_tertiary_fixed), true);
        streak.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        streak.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        head.addView(streak);
        box.addView(head);

        LinearLayout kicker = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        kicker.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 9), Ui.dp(c, 9)));
        TextView kt = Ui.text(c, mode == 0 ? "KHÔNG GIAN HỌC SÂU" : "THỜI GIAN THƯ GIÃN", 14, R.color.brand_dark, true);
        kt.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams ktl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ktl.setMarginStart(Ui.dp(c, 8));
        kicker.addView(kt, ktl);
        FrameLayout vol = Ui.iconBox(c, R.drawable.ic_volume, R.color.m3_surface_container, sound ? R.color.brand_dark : R.color.slate_400, 44, 20, 999);
        vol.setContentDescription(sound ? "Tắt chuông báo" : "Bật chuông báo");
        vol.setOnClickListener(v -> {
            sound = !sound;
            if (activity instanceof AppState.Host) ((AppState.Host) activity).toast(sound ? "🔔 Đã bật chuông báo hết phiên" : "🔕 Đã tắt chuông báo hết phiên");
            render();
        });
        kicker.addView(vol);
        Ui.add(box, kicker, 8);

        LinearLayout tabs = Ui.horizontal(c);
        tabs.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int i = 0; i < MODES.length; i++) {
            int idx = i;
            TextView t = Ui.text(c, LABELS[i], 14, mode == i ? android.R.color.white : R.color.text_secondary, mode == i);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            if (mode == i) { t.setTextColor(0xFFFFFFFF); t.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55)); }
            t.setOnClickListener(v -> switchMode(idx));
            Ui.addWeighted(tabs, t, i == 0 ? 0 : 2);
        }
        Ui.add(box, tabs, 16);

        // ---- Ring ----
        FrameLayout ring = new FrameLayout(c);
        View glow = new View(c);
        glow.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        int size = Ui.dp(c, 270);
        ring.addView(glow, new FrameLayout.LayoutParams(size - Ui.dp(c, 24), size - Ui.dp(c, 24), Gravity.CENTER));
        Ring r = new Ring(c, (float) (total - left) / total, mode == 0 ? 0xFF005C55 : Ui.color(c, R.color.amber), Ui.color(c, R.color.m3_surface_high));
        ring.addView(r, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        LinearLayout center = Ui.vertical(c);
        center.setGravity(Gravity.CENTER);
        LinearLayout chip = Ui.iconText(c, R.drawable.ic_brain, mode == 0 ? "Phiên học sâu #" + (sessionsToday + 1) : "Giải lao hồi phục", 13, Ui.color(c, R.color.brand_dark), true);
        chip.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        chip.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 14), Ui.dp(c, 5));
        center.addView(chip);
        TextView clock = Ui.text(c, String.format("%02d:%02d", left / 60, left % 60), 56, R.color.text, true);
        clock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Ui.addInline(center, clock, 6);
        String studying = StudyMindAppDoc.title();
        LinearLayout sub = Ui.iconText(c, R.drawable.ic_target, mode == 0 ? (studying != null ? "Đang rèn luyện: " + studying : "Tập trung cao độ") : "Thư giãn đầu óc, uống chút nước", 13, Ui.color(c, R.color.text_secondary), false);
        ((TextView) sub.getChildAt(1)).setMaxLines(1);
        ((TextView) sub.getChildAt(1)).setEllipsize(android.text.TextUtils.TruncateAt.END);
        ((TextView) sub.getChildAt(1)).setMaxWidth(Ui.dp(c, 200));
        Ui.addInline(center, sub, 8);
        ring.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size + Ui.dp(c, 8));
        rl.topMargin = Ui.dp(c, 20);
        box.addView(ring, rl);

        // ---- Controls ----
        LinearLayout actions = Ui.horizontal(c);
        actions.setGravity(Gravity.CENTER);
        FrameLayout reset = Ui.iconBox(c, R.drawable.ic_reset, R.color.m3_surface_low, R.color.text_secondary, 56, 24, 999);
        reset.setContentDescription("Đặt lại");
        reset.setOnClickListener(v -> { stop(); timeLeftMs = SECONDS[mode] * 1000L; render(); });
        actions.addView(reset);
        LinearLayout play = Ui.iconText(c, running ? R.drawable.ic_pause : R.drawable.ic_play,
                running ? "TẠM DỪNG" : left == total ? "BẮT ĐẦU" : "TIẾP TỤC", 17, 0xFFFFFFFF, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
        play.setElevation(Ui.dp(c, 6));
        play.setOnClickListener(v -> toggle());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(c, 200), Ui.dp(c, 68));
        pl.setMargins(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        actions.addView(play, pl);
        FrameLayout skip = Ui.iconBox(c, R.drawable.ic_chevron_right, R.color.m3_surface_low, R.color.text_secondary, 56, 26, 999);
        skip.setContentDescription("Bỏ qua sang phiên tiếp theo");
        skip.setOnClickListener(v -> switchMode(mode == 0 ? (sessionsToday > 0 && sessionsToday % 4 == 0 ? 2 : 1) : 0));
        actions.addView(skip);
        Ui.add(box, actions, 18);

        // ---- Daily goal ----
        LinearLayout goal = Ui.vertical(c);
        goal.setBackground(Ui.rounded(c, R.color.surface, 24, R.color.border));
        goal.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16));
        LinearLayout gh = Ui.horizontal(c);
        gh.addView(Ui.iconBox(c, R.drawable.ic_shield_check, R.color.m3_tertiary_fixed, R.color.m3_tertiary, 44, 22, 999));
        LinearLayout gt = Ui.vertical(c);
        gt.addView(Ui.text(c, "Mục tiêu hôm nay", 18, R.color.text, true));
        gt.addView(Ui.text(c, "Đã tích luỹ " + sessionsToday * 25 + " phút tập trung", 13, R.color.text_secondary, false));
        LinearLayout.LayoutParams gtl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        gtl.setMarginStart(Ui.dp(c, 12));
        gh.addView(gt, gtl);
        android.text.SpannableStringBuilder gs = new android.text.SpannableStringBuilder(String.valueOf(sessionsToday));
        int e = gs.length();
        gs.append(" /" + DAILY_GOAL + " phiên");
        gs.setSpan(new android.text.style.RelativeSizeSpan(0.6f), e, gs.length(), 0);
        gs.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.text_secondary)), e, gs.length(), 0);
        gh.addView(Ui.text(c, gs, 28, R.color.brand_dark, true));
        goal.addView(gh);
        int pct = Math.min(100, sessionsToday * 100 / DAILY_GOAL);
        Ui.add(goal, Ui.track(c, pct, R.color.brand_dark, 10), 14);
        LinearLayout gf = Ui.horizontal(c);
        Ui.addWeighted(gf, Ui.text(c, "Tiến độ ngày: " + pct + "%", 13, R.color.text_secondary, false), 0);
        gf.addView(Ui.iconText(c, R.drawable.ic_zap, "Tính vào nhiệm vụ StudyCoin", 13, Ui.color(c, R.color.m3_tertiary), true));
        Ui.add(goal, gf, 10);
        Ui.add(box, goal, 22);

        // ---- Ambient sound ----
        LinearLayout ah = Ui.horizontal(c);
        Ui.addWeighted(ah, Ui.iconText(c, R.drawable.ic_volume, "Âm thanh hỗ trợ sóng não", 18, Ui.color(c, R.color.text), true), 0);
        ah.addView(Ui.text(c, "Tạo trên máy", 12, R.color.text_secondary, false));
        Ui.add(box, ah, 24);
        LinearLayout sounds = Ui.horizontal(c);
        String[][] list = {{"alpha", "Sóng Alpha (10Hz)"}, {"rain", "Mưa rào nhẹ"}, {"brown", "Tiếng ồn nâu"}};
        for (String[] s : list) {
            boolean on = s[0].equals(com.studymind.nativeapp.data.AmbientSound.current());
            LinearLayout chipS = Ui.iconText(c, on ? R.drawable.ic_pause : R.drawable.ic_play, s[1], 14, Ui.color(c, on ? R.color.brand_dark : R.color.text_secondary), on);
            chipS.setBackground(Ui.rounded(c, on ? R.color.teal_200 : R.color.m3_surface_container, 999, 0));
            chipS.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
            chipS.setOnClickListener(v -> { com.studymind.nativeapp.data.AmbientSound.toggle(s[0]); render(); });
            Ui.addWrap(sounds, chipS, sounds.getChildCount() > 0 ? 10 : 0);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(sounds);
        Ui.add(box, hs, 10);

        // ---- Tip ----
        LinearLayout tip = Ui.horizontal(c);
        tip.setGravity(Gravity.TOP);
        tip.setBackground(Ui.rounded(c, R.color.m3_surface_high, 24, 0));
        tip.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        tip.addView(Ui.iconBox(c, R.drawable.ic_lightbulb, R.color.brand_dark, android.R.color.white, 48, 24, 999));
        LinearLayout tt = Ui.vertical(c);
        tt.addView(Ui.text(c, "Lời khuyên từ StudyMind AI", 17, R.color.text, true));
        Ui.add(tt, Ui.text(c, "Theo kỹ thuật Pomodoro, não bộ đạt đỉnh tập trung trong 25 phút đầu. Sau phiên này, hãy rời mắt khỏi màn hình điện thoại trong 5 phút để bảo toàn khả năng ghi nhớ dài hạn!", 14, R.color.text_secondary, false), 6);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ttl.setMarginStart(Ui.dp(c, 14));
        tip.addView(tt, ttl);
        Ui.add(box, tip, 22);
    }

    /** Title of the lesson being studied, shown under the clock */
    private static final class StudyMindAppDoc {
        static boolean dark() {
            try { return com.studymind.nativeapp.StudyMindApp.state().isDarkMode(); } catch (RuntimeException e) { return false; }
        }

        static String title() {
            try {
                com.studymind.nativeapp.data.model.Models.ActiveDocument a = com.studymind.nativeapp.StudyMindApp.state().activeDoc();
                return a != null ? a.document.title : null;
            } catch (RuntimeException e) {
                return null;
            }
        }
    }


    private void switchMode(int m) {
        stop();
        mode = m;
        timeLeftMs = SECONDS[m] * 1000L;
        render();
    }

    private void toggle() {
        if (running) stop();
        else start();
        render();
    }

    private static void stop() {
        if (timer != null) timer.cancel();
        running = false;
        FocusRewards.workPaused();
    }

    private void start() {
        running = true;
        // Focus phases also count toward the StudyCoin focus mission, timed by the server
        if (mode == 0) FocusRewards.workStarted();
        timer = new CountDownTimer(timeLeftMs, 1000) {
            @Override public void onTick(long ms) {
                timeLeftMs = ms;
                if (visible != null) visible.render();
            }
            @Override public void onFinish() { complete(); }
        }.start();
    }

    private void complete() {
        running = false;
        if (sound) {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 600);
        }
        AppState.Host host = activity instanceof AppState.Host ? (AppState.Host) activity : null;
        if (mode == 0) {
            sessionsToday++;
            if (host != null) {
                host.toast("🎉 Phiên tập trung hoàn tất! Hãy nghỉ ngơi 5 phút nhé!");
                host.announceBadges(Gamification.recordPomodoroSessionCompleted());
            }
            com.studymind.nativeapp.StudyMindApp.state().syncGamification("pomodoro");
            FocusRewards.workCompleted(host != null ? host::toast : null);
            mode = 1;
        } else {
            if (host != null) host.toast("⏰ Hết giờ nghỉ giải lao. Bắt đầu phiên tập trung tiếp theo nhé!");
            mode = 0;
        }
        timeLeftMs = SECONDS[mode] * 1000L;
        if (visible != null) visible.render();
    }

    /** SVG circle r=44 stroke 6, rotated -90°: grey track + coloured progress arc */
    private static class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float progress;
        private final RectF rect = new RectF();

        Ring(Context c, float progress, int color, int trackColor) {
            super(c);
            this.progress = progress;
            float w = Ui.dp(c, 11);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(w);
            track.setColor(trackColor);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(w);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(color);
        }

        @Override protected void onDraw(Canvas canvas) {
            float inset = track.getStrokeWidth() / 2 + 2;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawOval(rect, track);
            if (progress > 0) canvas.drawArc(rect, -90, 360 * progress, false, arc);
        }
    }
}
