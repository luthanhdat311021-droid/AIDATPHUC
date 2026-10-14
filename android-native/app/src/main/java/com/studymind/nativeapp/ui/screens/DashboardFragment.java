package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.UserStats;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.PomodoroDialog;
import com.studymind.nativeapp.ui.dialogs.StreakDialog;

import java.util.Arrays;
import java.util.List;

/** "Tổng quan" — DashboardView.tsx laid out for a phone (cards stack instead of the 3-column grid). */
public class DashboardFragment extends BaseFragment {
    private static final List<String> ENGINE_TAGS = Arrays.asList("AI Analysis Engine", "YouTube Speech-to-Text", "Web Article Extractor");

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        UserStats s = state.stats();
        Gamification.Data g = Gamification.load();

        // ---- Top banner hero: from-[#0B4F48] to-[#0F766E] ----
        LinearLayout hero = Ui.vertical(c);
        hero.setBackground(Ui.gradient(c, 16, 0xFF0B4F48, 0xFF0F766E));
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        hero.setElevation(Ui.dp(c, 3));
        TextView h = Ui.text(c, "Hôm nay bạn muốn học gì mới?", 20, R.color.text, true);
        h.setTextColor(Color.WHITE);
        hero.addView(h);
        TextView p = Ui.text(c, "Tải lên tài liệu của bạn (PDF, PPT, link video...) để StudyMind tự động tạo ghi chú, sơ đồ tư duy, flashcard và bộ câu hỏi trắc nghiệm ngay lập tức.", 13, R.color.text, false);
        p.setTextColor(0xE6CCFBF1);
        p.setLineSpacing(0, 1.35f);
        Ui.add(hero, p, 10);

        LinearLayout create = Ui.pillButton(c, R.drawable.ic_plus, "Tạo bộ học liệu mới", android.R.color.white, R.color.brand, 0, v -> state.navigate(Screen.IMPORT));
        create.setBackground(Ui.rounded(c, android.R.color.white, 8, 0));
        ((TextView) create.getChildAt(1)).setTextColor(0xFF0F766E);
        ((android.widget.ImageView) create.getChildAt(0)).setColorFilter(0xFF0F766E);
        addWrapRow(hero, create, 18);

        LinearLayout pomodoro = Ui.pillButton(c, R.drawable.ic_timer, "Bật Pomodoro Focus (25p)", android.R.color.white, android.R.color.white, 0,
                v -> PomodoroDialog.show((androidx.appcompat.app.AppCompatActivity) requireActivity()));
        pomodoro.setBackground(stroked(c, 0xCC115E59, 0x800D9488, 8));
        ((android.widget.ImageView) pomodoro.getChildAt(0)).setColorFilter(0xFF99F6E4);
        addWrapRow(hero, pomodoro, 10);

        LinearLayout streak = Ui.pillButton(c, R.drawable.ic_flame, "Chuỗi " + g.streak + " ngày", android.R.color.white, android.R.color.white, 0,
                v -> StreakDialog.show((androidx.appcompat.app.AppCompatActivity) requireActivity()));
        streak.setBackground(stroked(c, 0xCCF97316, 0x80FB923C, 8));
        ((android.widget.ImageView) streak.getChildAt(0)).setColorFilter(Color.WHITE);
        addWrapRow(hero, streak, 10);
        root.addView(hero);

        // ---- 3 stat cards ----
        statCard(root, "Tài liệu đã tải", s != null ? String.valueOf(s.totalDocuments) : "0",
                plain(c, "+" + (s != null ? s.weeklyDocAdded : 0) + " tài liệu tuần này", 0xFF10B981, true),
                R.drawable.ic_file, R.color.teal_50, R.color.brand);

        SpannableStringBuilder ret = new SpannableStringBuilder("Tỷ lệ ghi nhớ: ");
        if (s != null && s.retentionRatePercentage != null) {
            int st = ret.length();
            ret.append(s.retentionRatePercentage + "%");
            ret.setSpan(new ForegroundColorSpan(0xFFF59E0B), st, ret.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ret.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), st, ret.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            int st = ret.length();
            ret.append("chưa có dữ liệu");
            ret.setSpan(new ForegroundColorSpan(0xFF94A3B8), st, ret.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        statCard(root, "Flashcard đã học", s != null ? s.flashcardProgress : "0/0", plainSpan(c, ret, 0xFF64748B),
                R.drawable.ic_layers, R.color.amber_50, R.color.amber);

        String quizSub = s != null && s.quizScoreDiff != null ? s.quizScoreDiff
                : s != null && s.quizAttempts > 0 ? s.quizAttempts + " lượt làm bài" : "Chưa làm bài trắc nghiệm nào";
        statCard(root, "Điểm trắc nghiệm TB", s != null && s.averageQuizScore != null ? s.averageQuizScore : "—",
                plain(c, quizSub, 0xFF10B981, true), R.drawable.ic_award, R.color.emerald_50, R.color.emerald);

        // ---- Gamification & daily focus highlights bar ----
        MaterialCardView gam = Ui.card(c);
        LinearLayout gi = Ui.inner(gam);
        gi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout top = Ui.horizontal(c);
        TextView fire = Ui.text(c, "🔥", 22, R.color.text, false);
        fire.setGravity(Gravity.CENTER);
        fire.setBackground(Ui.rounded(c, R.color.orange_100, 16, 0));
        top.addView(fire, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        LinearLayout tt = Ui.vertical(c);
        SpannableStringBuilder line = new SpannableStringBuilder("Chuỗi ngày học liên tục: ");
        int st = line.length();
        line.append(g.streak + " ngày");
        line.setSpan(new ForegroundColorSpan(0xFFF97316), st, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView lt = Ui.text(c, line, 14, R.color.text, true);
        tt.addView(lt);
        Ui.addInline(tt, Ui.pill(c, "Đang kích hoạt", R.color.orange_100, R.color.orange_700), 4);
        Ui.add(tt, Ui.text(c, "Đã hoàn thành " + g.pomodoroSessions + " phiên Pomodoro • Ôn tập " + g.cardsReviewed + " lượt thẻ ghi nhớ", 12, R.color.text_muted, false), 4);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 14));
        top.addView(tt, tl);
        gi.addView(top);

        LinearLayout badges = Ui.horizontal(c);
        for (Gamification.Badge b : Gamification.ALL_BADGES) {
            boolean unlocked = g.unlockedBadgeIds.contains(b.id);
            TextView bt = Ui.text(c, b.icon, 14, R.color.text, false);
            bt.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable bg = Ui.rounded(c, unlocked ? R.color.amber_soft : R.color.slate_200, 999, 0);
            bg.setStroke(Ui.dp(c, 2), Ui.color(c, R.color.surface));
            bt.setBackground(bg);
            bt.setAlpha(unlocked ? 1f : 0.4f);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
            if (badges.getChildCount() > 0) bl.setMarginStart(-Ui.dp(c, 6));
            badges.addView(bt, bl);
        }
        TextView all = Ui.text(c, "Xem tất cả huy hiệu →", 12, R.color.brand, true);
        all.setOnClickListener(v -> StreakDialog.show((androidx.appcompat.app.AppCompatActivity) requireActivity()));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.setMarginStart(Ui.dp(c, 12));
        badges.addView(all, al);
        Ui.add(gi, badges, 16);
        Ui.add(root, gam, 16);

        // ---- Recent study materials ----
        LinearLayout recent = Ui.section(root, "Bộ học liệu gần đây", "Xem tất cả", v -> state.navigate(Screen.HISTORY), 24);
        if (s == null || s.recentDocuments.isEmpty()) {
            recent.addView(Ui.muted(c, s == null ? "Đang tải..." : "Chưa có bộ học liệu nào. Bấm \"Tạo bộ học liệu mới\" để bắt đầu."));
        } else {
            for (DocumentItem d : s.recentDocuments) recent.addView(docRow(c, d), lp(c, recent.getChildCount() > 0 ? 12 : 0));
        }

        // ---- Weekly goals ----
        LinearLayout goals = Ui.section(root, "Tiến độ mục tiêu tuần", null, null, 24);
        int cur = s != null ? s.weeklyQuizCount.current : 0;
        int target = s != null && s.weeklyQuizCount.target > 0 ? s.weeklyQuizCount.target : 50;
        LinearLayout gl = Ui.horizontal(c);
        Ui.addWeighted(gl, Ui.text(c, "Số câu trắc nghiệm", 12, R.color.text_secondary, true), 0);
        Ui.addWrap(gl, Ui.text(c, cur + " / " + target + " câu", 12, R.color.emerald, true), 8);
        goals.addView(gl);
        Ui.add(goals, Ui.track(c, Math.min(100, cur * 100 / target), R.color.emerald, 10), 8);

        LinearLayout tip = Ui.horizontal(c);
        tip.setBackground(Ui.rounded(c, R.color.teal_50, 8, R.color.brand_soft));
        tip.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        tip.addView(Ui.icon(c, R.drawable.ic_flame, 20, 0xFFF59E0B));
        SpannableStringBuilder tipText = new SpannableStringBuilder();
        if (s != null && s.streakDays > 0) {
            tipText.append("Bạn đã duy trì chuỗi học ");
            int ts = tipText.length();
            tipText.append(s.streakDays + " ngày liên tục!");
            tipText.setSpan(new ForegroundColorSpan(Ui.color(c, R.color.brand)), ts, tipText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tipText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), ts, tipText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tipText.append(" Cố lên nào!");
        } else {
            tipText.append("Học hoặc làm trắc nghiệm hôm nay để bắt đầu chuỗi ngày học liên tục.");
        }
        TextView tipView = Ui.text(c, tipText, 12, R.color.teal_900, false);
        LinearLayout.LayoutParams tvl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvl.setMarginStart(Ui.dp(c, 12));
        tip.addView(tipView, tvl);
        Ui.add(goals, tip, 20);
    }

    /** Stat card: label / big number / coloured sub-line on the left, tinted icon tile on the right */
    private void statCard(LinearLayout root, String label, String value, TextView sub, int icon, int iconBg, int iconFg) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        card.setRadius(Ui.dp(c, 12));
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout row = Ui.horizontal(c);
        LinearLayout left = Ui.vertical(c);
        left.addView(Ui.text(c, label, 12, R.color.text_secondary, false));
        Ui.add(left, Ui.text(c, value, 24, R.color.text, true), 4);
        Ui.add(left, sub, 4);
        Ui.addWeighted(row, left, 0);
        FrameLayout box = Ui.iconBox(c, icon, iconBg, iconFg, 40, 20, 12);
        row.addView(box);
        in.addView(row);
        Ui.add(root, card, 16);
    }

    private View docRow(Context c, DocumentItem d) {
        LinearLayout row = Ui.vertical(c);
        row.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.slate_100));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        row.addView(Ui.text(c, d.title, 14, R.color.text, true));
        String meta = d.fileType + (d.pageCount != null && d.pageCount > 0 ? " • " + d.pageCount + " trang" : d.duration != null ? " • " + d.duration : "")
                + " • Cập nhật " + Ui.dateTime(d.updatedAt);
        Ui.add(row, Ui.text(c, meta, 12, R.color.text_muted, false), 4);
        LinearLayout tags = Ui.horizontal(c);
        if (d.tags != null) {
            for (String tag : d.tags) {
                boolean subject = !ENGINE_TAGS.contains(tag);
                TextView t = subject
                        ? Ui.pill(c, "📁 " + tag, R.color.emerald_soft, R.color.emerald_700)
                        : tag.contains("Mindmap") ? Ui.pill(c, tag, R.color.teal_50, R.color.brand)
                        : tag.contains("Quiz") ? Ui.pill(c, tag, R.color.emerald_50, R.color.emerald_700)
                        : Ui.pill(c, tag, R.color.amber_50, R.color.amber_700);
                Ui.addWrap(tags, t, tags.getChildCount() > 0 ? 6 : 0);
            }
        }
        tags.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        tags.addView(Ui.icon(c, R.drawable.ic_arrow_right, 16, Ui.color(c, R.color.slate_400)));
        Ui.add(row, tags, 10);
        row.setOnClickListener(v -> state.openDocument(d.id, Screen.WORKSPACE));
        return row;
    }

    private static TextView plain(Context c, String s, int argb, boolean bold) {
        TextView t = Ui.text(c, s, 11, R.color.text, bold);
        t.setTextColor(argb);
        return t;
    }

    private static TextView plainSpan(Context c, CharSequence s, int argb) {
        TextView t = Ui.text(c, s, 11, R.color.text, false);
        t.setTextColor(argb);
        return t;
    }

    private static android.graphics.drawable.GradientDrawable stroked(Context c, int fill, int stroke, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setStroke(Ui.dp(c, 1), stroke);
        d.setCornerRadius(Ui.dp(c, radius));
        return d;
    }

    private static void addWrapRow(LinearLayout parent, View v, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(parent.getContext(), top);
        parent.addView(v, lp);
    }

    private static LinearLayout.LayoutParams lp(Context c, int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = Ui.dp(c, top);
        return l;
    }
}
