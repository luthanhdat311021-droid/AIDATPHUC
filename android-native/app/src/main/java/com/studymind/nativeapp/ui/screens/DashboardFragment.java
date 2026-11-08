package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.data.model.Models.AppUser;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.QuizHistoryRecord;
import com.studymind.nativeapp.data.model.Models.UserStats;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.PomodoroDialog;
import com.studymind.nativeapp.ui.dialogs.StreakDialog;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** "Tổng quan" — Stitch design/stitch/02_tong_quan. */
public class DashboardFragment extends BaseFragment {
    private static final List<String> ENGINE_TAGS = Arrays.asList("AI Analysis Engine", "YouTube Speech-to-Text", "Web Article Extractor");
    private static final String[] DAY_LABELS = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        UserStats s = state.stats();
        Gamification.Data g = Gamification.load();
        AppUser u = state.auth().getUser();

        hero(root, u);
        actionTiles(root);
        missions(root, g);
        stats(root, s);
        streakWeek(root, g);
        recent(root, s);
        weeklyGoals(root, s, g);
    }

    // ---------- hero ----------

    private void hero(LinearLayout root, AppUser u) {
        Context c = requireContext();
        LinearLayout hero = Ui.vertical(c);
        hero.setBackground(Ui.gradient(c, 20, 0xFFE6F6F3, 0xFFEFF4FF));
        hero.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout chip = Ui.iconText(c, R.drawable.ic_sparkles, "AI Trợ lý học tập", 12, Ui.color(c, R.color.brand), false);
        chip.setBackground(Ui.rounded(c, R.color.brand_soft, 999, 0));
        chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        Ui.addInline(hero, chip, 0);
        String name = u != null && u.fullName != null && !u.fullName.trim().isEmpty() ? firstName(u.fullName) : "bạn";
        Ui.add(hero, Ui.text(c, "Chào " + name + "! 👋", 24, R.color.text, true), 10);
        Ui.add(hero, Ui.text(c, "Hôm nay bạn muốn học gì mới?", 18, R.color.brand, true), 6);
        TextView quote = Ui.text(c, "“Từng bước nhỏ tích lũy mỗi ngày sẽ kiến tạo nên kiến thức vững vàng.”", 13, R.color.text_muted, false);
        quote.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        Ui.add(hero, quote, 8);
        root.addView(hero);
    }

    private static String firstName(String full) {
        String[] parts = full.trim().split("\\s+");
        return parts.length >= 2 ? parts[parts.length - 2] + " " + parts[parts.length - 1] : parts[0];
    }

    private void actionTiles(LinearLayout root) {
        Context c = requireContext();
        LinearLayout row = Ui.horizontal(c);
        row.setBaselineAligned(false);

        LinearLayout create = tile(c, R.drawable.ic_upload, "Tạo bộ học liệu", "Tải lên hoặc AI sinh", true);
        create.setOnClickListener(v -> state.navigate(Screen.IMPORT));
        LinearLayout pomo = tile(c, R.drawable.ic_timer, "Pomodoro Focus", "Phiên 25 phút chuẩn", false);
        pomo.setOnClickListener(v -> PomodoroDialog.show((AppCompatActivity) requireActivity()));

        row.addView(create, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        pl.setMarginStart(Ui.dp(c, 12));
        row.addView(pomo, pl);
        Ui.add(root, row, 20);
    }

    private LinearLayout tile(Context c, int icon, String title, String sub, boolean primary) {
        LinearLayout t = Ui.vertical(c);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
        if (primary) t.setBackground(Ui.gradient(c, 20, 0xFF0F766E, 0xFF0F766E));
        else t.setBackground(Ui.rounded(c, R.color.amber_50, 20, 0));
        FrameLayout box = new FrameLayout(c);
        box.setBackground(primary ? Ui.gradient(c, 12, 0x33FFFFFF, 0x33FFFFFF) : Ui.rounded(c, R.color.m3_tertiary_fixed, 12, 0));
        box.addView(Ui.icon(c, icon, 24, primary ? 0xFFFFFFFF : Ui.color(c, R.color.m3_tertiary)),
                new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
        t.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        TextView tt = Ui.text(c, title, 17, R.color.text, true);
        if (primary) tt.setTextColor(0xFFFFFFFF);
        Ui.add(t, tt, 14);
        TextView ts = Ui.text(c, sub, 13, R.color.m3_tertiary, false);
        if (primary) ts.setTextColor(0xFFA3FAEF);
        Ui.add(t, ts, 2);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    // ---------- daily missions ----------

    private void missions(LinearLayout root, Gamification.Data g) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        FrameLayout coin = new FrameLayout(c);
        coin.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        coin.addView(Ui.icon(c, R.drawable.ic_wallet, 26, Ui.color(c, R.color.m3_tertiary)), new FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER));
        top.addView(coin, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
        LinearLayout tx = Ui.vertical(c);
        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.text(c, "Nhiệm vụ hàng ngày", 15, R.color.m3_tertiary, true));
        TextView sc = Ui.pill(c, "+StudyCoin", R.color.m3_tertiary_container, R.color.surface);
        sc.setTextColor(0xFFFFFFFF);
        Ui.addWrap(head, sc, 8);
        tx.addView(head);
        Ui.add(tx, Ui.text(c, "Hoàn thành nhiệm vụ học tập để đổi gói Pro miễn phí!", 14, R.color.text, false), 4);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 14));
        top.addView(tx, txl);
        in.addView(top);

        int done = 1 + (g.pomodoroSessions > 0 ? 1 : 0) + (g.cardsReviewed > 0 ? 1 : 0);
        LinearLayout prog = Ui.horizontal(c);
        prog.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
        prog.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        LinearLayout pl = Ui.vertical(c);
        LinearLayout pr = Ui.horizontal(c);
        Ui.addWeighted(pr, Ui.text(c, "Tiến độ hôm nay", 13, R.color.text_secondary, false), 0);
        Ui.addWrap(pr, Ui.text(c, done + " / 3 nhiệm vụ", 13, R.color.brand, true), 8);
        pl.addView(pr);
        Ui.add(pl, Ui.track(c, done * 100 / 3, R.color.brand, 8), 8);
        Ui.addWeighted(prog, pl, 0);
        LinearLayout go = Ui.horizontal(c);
        TextView gt = Ui.text(c, "Xem ngay", 14, R.color.brand, true);
        go.addView(gt);
        go.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, Ui.color(c, R.color.brand)));
        go.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), 0, Ui.dp(c, 8));
        Ui.addWrap(prog, go, 12);
        Ui.add(in, prog, 16);
        card.setOnClickListener(v -> state.navigate(Screen.REWARDS));
        Ui.add(root, card, 20);
    }

    // ---------- stats ----------

    private void stats(LinearLayout root, UserStats s) {
        Context c = requireContext();
        sectionHeader(root, "Chỉ số học tập", "Tuần này", null, 24);
        LinearLayout row = Ui.horizontal(c);
        row.setBaselineAligned(false);
        String docs = s != null ? String.valueOf(s.totalDocuments) : "0";
        String cards = s != null ? cardCount(s.flashcardProgress) : "0";
        String score = s != null && s.averageQuizScore != null ? s.averageQuizScore : "—";
        statTile(row, R.drawable.ic_book, R.color.m3_secondary_container, R.color.brand, docs, "Tài liệu lưu",
                R.drawable.ic_arrow_right, "+" + (s != null ? s.weeklyDocAdded : 0) + " tuần", R.color.brand, 0);
        statTile(row, R.drawable.ic_layers, R.color.m3_secondary_container, R.color.m3_secondary, cards, "Flashcard",
                R.drawable.ic_brain, s != null && s.retentionRatePercentage != null ? "Nhớ " + s.retentionRatePercentage + "%" : "Chưa ôn", R.color.text_muted, 10);
        statTile(row, R.drawable.ic_check_circle, R.color.m3_tertiary_fixed, R.color.m3_tertiary, score, "Điểm test TB",
                R.drawable.ic_clipboard, (s != null ? s.quizAttempts : 0) + " bài", R.color.m3_tertiary, 10);
        Ui.add(root, row, 12);
    }

    private static String cardCount(String progress) {
        if (progress == null) return "0";
        int slash = progress.indexOf('/');
        return slash > 0 ? progress.substring(0, slash).trim() : progress;
    }

    private void statTile(LinearLayout row, int icon, int iconBg, int iconFg, String value, String label, int subIcon, String sub, int subColor, int start) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        FrameLayout box = Ui.iconBox(c, icon, iconBg, iconFg, 36, 20, 10);
        in.addView(box);
        Ui.add(in, Ui.text(c, value, 26, R.color.text, true), 10);
        Ui.add(in, Ui.text(c, label, 13, R.color.text_muted, false), 2);
        LinearLayout sl = Ui.iconText(c, subIcon, sub, 12, Ui.color(c, subColor), true);
        Ui.add(in, sl, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(card, lp);
    }

    // ---------- streak week ----------

    private void streakWeek(LinearLayout root, Gamification.Data g) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        LinearLayout top = Ui.horizontal(c);
        FrameLayout flame = Ui.iconBox(c, R.drawable.ic_flame, R.color.m3_tertiary_fixed, R.color.m3_tertiary_container, 44, 22, 999);
        top.addView(flame);
        LinearLayout tx = Ui.vertical(c);
        tx.addView(Ui.text(c, "Chuỗi " + g.streak + " ngày liên tiếp!", 17, R.color.text, true));
        int nextGoal = g.streak < 3 ? 3 : g.streak < 7 ? 7 : 0;
        String hint = nextGoal > 0 ? "Chỉ còn " + (nextGoal - g.streak) + " ngày nữa để mở khóa huy hiệu" : "Bạn đã mở khóa huy hiệu Kỷ luật thép!";
        Ui.add(tx, Ui.text(c, hint, 13, R.color.m3_tertiary, false), 2);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 14));
        top.addView(tx, txl);
        top.addView(Ui.icon(c, R.drawable.ic_award, 24, Ui.color(c, R.color.m3_tertiary)));
        in.addView(top);

        LinearLayout days = Ui.horizontal(c);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            int ago = 6 - i;
            boolean today = ago == 0;
            boolean active = ago < g.streak;
            LinearLayout d = Ui.vertical(c);
            d.setGravity(Gravity.CENTER_HORIZONTAL);
            d.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            d.setBackground(Ui.rounded(c, today ? R.color.m3_tertiary_fixed : R.color.m3_surface_low, 12, 0));
            TextView lbl = Ui.text(c, DAY_LABELS[cal.get(Calendar.DAY_OF_WEEK) - 1], 12, today ? R.color.m3_on_tertiary_fixed : R.color.text_muted, today);
            lbl.setGravity(Gravity.CENTER);
            d.addView(lbl);
            int bg = today ? R.color.m3_tertiary : active ? R.color.brand_dark : R.color.slate_200;
            FrameLayout dot = Ui.iconBox(c, today ? R.drawable.ic_flame : R.drawable.ic_check_circle, bg, android.R.color.white, 28, 16, 999);
            if (!active && !today) ((android.widget.ImageView) dot.getChildAt(0)).setColorFilter(Ui.color(c, R.color.slate_400));
            Ui.addInline(d, dot, 6);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) dl.setMarginStart(Ui.dp(c, 6));
            days.addView(d, dl);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Ui.add(in, days, 16);
        card.setOnClickListener(v -> StreakDialog.show((AppCompatActivity) requireActivity()));
        Ui.add(root, card, 20);
    }

    // ---------- recent study sets ----------

    private void recent(LinearLayout root, UserStats s) {
        Context c = requireContext();
        int total = s != null ? s.totalDocuments : 0;
        sectionHeader(root, "Bộ học liệu gần đây", "Tất cả (" + total + ")", v -> state.navigate(Screen.HISTORY), 24);
        if (s == null || s.recentDocuments.isEmpty()) {
            MaterialCardView empty = Ui.card(c);
            Ui.inner(empty).addView(Ui.muted(c, s == null ? "Đang tải..." : "Chưa có bộ học liệu nào. Bấm \"Nhập tài liệu\" để bắt đầu."));
            Ui.add(root, empty, 12);
            return;
        }
        boolean first = true;
        for (DocumentItem d : s.recentDocuments) {
            Ui.add(root, docCard(c, d, first), 12);
            first = false;
        }
    }

    private View docCard(Context c, DocumentItem d, boolean highlight) {
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        int icon = d.fileType != null && d.fileType.toLowerCase().contains("video") ? R.drawable.ic_video
                : d.fileType != null && d.fileType.toLowerCase().contains("web") ? R.drawable.ic_link : R.drawable.ic_file;
        top.addView(Ui.iconBox(c, icon, R.color.m3_secondary_container, highlight ? R.color.brand : R.color.m3_secondary, 48, 24, 14));
        LinearLayout tx = Ui.vertical(c);
        LinearLayout tagRow = Ui.horizontal(c);
        String subject = subjectOf(d);
        if (subject != null) tagRow.addView(Ui.pill(c, subject, R.color.m3_surface_container, R.color.text_secondary));
        Integer best = bestScore(d);
        if (best != null) Ui.addWrap(tagRow, Ui.text(c, best + "%", 13, highlight ? R.color.brand : R.color.text_muted, true), 8);
        if (tagRow.getChildCount() > 0) tx.addView(tagRow);
        TextView title = Ui.text(c, d.title, 17, R.color.text, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.add(tx, title, 4);
        String meta = d.fileType + (d.pageCount != null && d.pageCount > 0 ? " · " + d.pageCount + " trang" : d.duration != null ? " · " + d.duration : "")
                + " · " + Ui.date(d.updatedAt);
        Ui.add(tx, Ui.text(c, meta, 13, R.color.text_muted, false), 2);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 14));
        top.addView(tx, txl);
        in.addView(top);

        LinearLayout bottom = Ui.horizontal(c);
        View track = Ui.track(c, best != null ? best : 0, highlight ? R.color.brand : R.color.m3_secondary, 8);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.dp(c, 8), 1f);
        tl.setMarginEnd(Ui.dp(c, 60));
        bottom.addView(track, tl);
        LinearLayout go = Ui.iconText(c, 0, "Học tiếp", 14, highlight ? 0xFFFFFFFF : Ui.color(c, R.color.text), true);
        go.addView(Ui.icon(c, R.drawable.ic_play, 16, highlight ? 0xFFFFFFFF : Ui.color(c, R.color.text)));
        ((LinearLayout.LayoutParams) go.getChildAt(1).getLayoutParams()).setMarginStart(Ui.dp(c, 6));
        go.setBackground(highlight ? Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E) : Ui.rounded(c, R.color.m3_surface_container, 999, 0));
        go.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        go.setOnClickListener(v -> state.openDocument(d.id, Screen.WORKSPACE));
        bottom.addView(go);
        Ui.add(in, bottom, 16);
        card.setOnClickListener(v -> state.openDocument(d.id, Screen.WORKSPACE));
        return card;
    }

    private static String subjectOf(DocumentItem d) {
        if (d.tags == null) return null;
        for (String t : d.tags) if (!ENGINE_TAGS.contains(t) && !t.contains("Mindmap") && !t.contains("Quiz")) return t;
        return null;
    }

    private static Integer bestScore(DocumentItem d) {
        if (d.quizHistory == null || d.quizHistory.isEmpty()) return null;
        int best = 0;
        for (QuizHistoryRecord r : d.quizHistory) best = Math.max(best, r.score);
        return best;
    }

    // ---------- weekly goals ----------

    private void weeklyGoals(LinearLayout root, UserStats s, Gamification.Data g) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        float hours = g.pomodoroSessions * 25f / 60f;
        int hourPct = Math.min(100, Math.round(hours * 100f / 8f));
        int cardPct = Math.min(100, g.cardsReviewed * 100 / 120);
        int qCur = s != null ? s.weeklyQuizCount.current : 0;
        int qTarget = s != null && s.weeklyQuizCount.target > 0 ? s.weeklyQuizCount.target : 50;
        int quizPct = Math.min(100, qCur * 100 / qTarget);
        int overall = (hourPct + cardPct + quizPct) / 3;

        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.icon(c, R.drawable.ic_target, 22, Ui.color(c, R.color.brand)));
        TextView ht = Ui.text(c, "Tiến độ mục tiêu tuần", 17, R.color.text, true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 10));
        head.addView(ht, hl);
        head.addView(Ui.pill(c, overall + "% Đạt", R.color.m3_primary_fixed, R.color.brand_dark));
        in.addView(head);

        goalRow(in, R.drawable.ic_timer, "Giờ tập trung học tập", String.format(java.util.Locale.US, "%.1f / 8.0 giờ", hours), hourPct, R.color.brand_dark);
        goalRow(in, R.drawable.ic_layers, "Thẻ flashcard ôn tập", g.cardsReviewed + " / 120 thẻ", cardPct, R.color.brand_dark);
        goalRow(in, R.drawable.ic_quiz, "Câu trắc nghiệm hoàn thành", qCur + " / " + qTarget + " câu", quizPct, R.color.m3_tertiary);
        Ui.add(root, card, 24);
    }

    private void goalRow(LinearLayout in, int icon, String label, String value, int pct, int color) {
        Context c = requireContext();
        LinearLayout row = Ui.horizontal(c);
        row.addView(Ui.icon(c, icon, 18, Ui.color(c, R.color.text_secondary)));
        TextView l = Ui.text(c, label, 14, R.color.text, false);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ll.setMarginStart(Ui.dp(c, 8));
        row.addView(l, ll);
        row.addView(Ui.text(c, value + " (" + pct + "%)", 14, R.color.text, true));
        Ui.add(in, row, 18);
        Ui.add(in, Ui.track(c, pct, color, 10), 8);
    }

    // ---------- shared ----------

    private void sectionHeader(LinearLayout root, String title, String action, View.OnClickListener onAction, int top) {
        Context c = requireContext();
        LinearLayout h = Ui.horizontal(c);
        Ui.addWeighted(h, Ui.text(c, title, 18, R.color.text, true), 0);
        if (action != null) {
            TextView a = Ui.text(c, action, 14, onAction != null ? R.color.brand : R.color.text_muted, onAction != null);
            if (onAction != null) a.setOnClickListener(onAction);
            Ui.addWrap(h, a, 8);
        }
        Ui.add(root, h, top);
    }
}
