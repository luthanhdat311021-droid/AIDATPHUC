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

    private PomodoroDialog(AppCompatActivity activity) {
        this.activity = activity;
        Context c = activity;
        box = Ui.vertical(c);
        box.setBackground(Ui.rounded(c, R.color.surface, 24, R.color.slate_100));
        int p = Ui.dp(c, 22);
        box.setPadding(p, p, p, p);
        dialog = new Dialog(c);
        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.9), ViewGroup.LayoutParams.WRAP_CONTENT);
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

        LinearLayout head = Ui.horizontal(c);
        Ui.addWeighted(head, Ui.text(c, "⏱️  Đồng hồ Pomodoro", 14, R.color.text, true), 0);
        ImageView vol = Ui.icon(c, R.drawable.ic_volume, 18, Ui.color(c, sound ? R.color.brand : R.color.slate_400));
        vol.setOnClickListener(v -> { sound = !sound; render(); });
        Ui.addWrap(head, vol, 8);
        ImageView close = Ui.icon(c, R.drawable.ic_close, 18, Ui.color(c, R.color.slate_400));
        close.setOnClickListener(v -> dialog.dismiss());
        Ui.addWrap(head, close, 12);
        box.addView(head);

        LinearLayout tabs = Ui.horizontal(c);
        tabs.setBackground(Ui.rounded(c, R.color.slate_100, 12, 0));
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int i = 0; i < MODES.length; i++) {
            int idx = i;
            TextView t = Ui.text(c, LABELS[i], 11, mode == i ? R.color.brand : R.color.text_muted, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
            if (mode == i) t.setBackground(Ui.rounded(c, R.color.surface, 8, 0));
            t.setOnClickListener(v -> switchMode(idx));
            Ui.addWeighted(tabs, t, 0);
        }
        Ui.add(box, tabs, 18);

        FrameLayout ring = new FrameLayout(c);
        Ring r = new Ring(c, (float) (total - left) / total, Ui.color(c, mode == 0 ? R.color.brand : R.color.amber), Ui.color(c, R.color.slate_100));
        ring.addView(r, new FrameLayout.LayoutParams(Ui.dp(c, 192), Ui.dp(c, 192), Gravity.CENTER));
        LinearLayout center = Ui.vertical(c);
        center.setGravity(Gravity.CENTER);
        TextView clock = Ui.text(c, String.format("%02d:%02d", left / 60, left % 60), 38, R.color.text, true);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        center.addView(clock);
        center.addView(Ui.text(c, mode == 0 ? "🎯 Tập trung cao độ" : "☕ Thư giãn đầu óc", 12, R.color.text_muted, true));
        ring.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        Ui.add(box, ring, 20);

        LinearLayout actions = Ui.horizontal(c);
        FrameLayout reset = Ui.iconBox(c, R.drawable.ic_reset, R.color.slate_100, R.color.text_secondary, 52, 20, 16);
        reset.setOnClickListener(v -> { stop(); timeLeftMs = SECONDS[mode] * 1000L; render(); });
        actions.addView(reset);
        LinearLayout play = Ui.pillButton(c, running ? R.drawable.ic_pause : R.drawable.ic_play,
                running ? "Tạm dừng" : left == total ? "Bắt đầu ngay" : "Tiếp tục", R.color.brand, android.R.color.white, 0, v -> toggle());
        play.setBackground(Ui.rounded(c, R.color.brand, 16, 0));
        play.setMinimumHeight(Ui.dp(c, 52));
        Ui.addWeighted(actions, play, 14);
        Ui.add(box, actions, 20);

        LinearLayout stats = Ui.horizontal(c);
        stats.setBackground(Ui.rounded(c, R.color.slate_50, 16, R.color.slate_100));
        stats.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        Ui.addWeighted(stats, Ui.text(c, "Phiên hoàn thành hôm nay:", 12, R.color.text_muted, false), 0);
        Ui.addWrap(stats, Ui.iconText(c, R.drawable.ic_check_circle, sessionsToday + " phiên (" + sessionsToday * 25 + " phút)", 12, Ui.color(c, R.color.brand), true), 8);
        Ui.add(box, stats, 18);
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
    }

    private void start() {
        running = true;
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
