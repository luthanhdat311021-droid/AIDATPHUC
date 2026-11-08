package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.QuizHistoryRecord;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.LineChart;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.Sheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Lịch sử bài học & Tiến trình" — Stitch design/stitch/05_lich_su + 06_lich_su_quiz_sheet. */
public class HistoryFragment extends BaseFragment {
    private static final List<String> ENGINE_TAGS = Arrays.asList("AI Analysis Engine", "YouTube Speech-to-Text", "Web Article Extractor");

    private String query = "";
    private String tag = "ALL";
    private final Set<String> forFusion = new LinkedHashSet<>();
    private boolean fusing;
    private LinearLayout list;
    private FrameLayout fusionDock;

    @Override
    protected Object watchKey() { return state.history(); }

    /** Wrap the scrolling page so the "Hợp nhất AI" bar can float above the bottom navigation */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        View scrollView = super.onCreateView(inflater, container, saved);
        FrameLayout frame = new FrameLayout(requireContext());
        frame.addView(scrollView);
        fusionDock = new FrameLayout(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int m = Ui.dp(requireContext(), 16);
        lp.setMargins(m, 0, m, Ui.dp(requireContext(), 12));
        frame.addView(fusionDock, lp);
        return frame;
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        List<DocumentItem> items = state.history();

        root.addView(Ui.text(c, "Lịch sử bài học & Tiến trình", 26, R.color.text, true));
        Ui.add(root, Ui.text(c, "Quản lý tài liệu đã học, theo dõi điểm số và hợp nhất đa kiến thức", 15, R.color.text_secondary, false), 6);

        LinearLayout sync = Ui.horizontal(c);
        sync.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        sync.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        sync.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        TextView st = Ui.text(c, "Đã kết nối Supabase – Dữ liệu lưu trữ vĩnh viễn và đồng bộ", 13, R.color.text, false);
        st.setMaxLines(1);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMarginStart(Ui.dp(c, 8));
        sync.addView(st, sl);
        sync.addView(Ui.icon(c, R.drawable.ic_check_circle, 18, Ui.color(c, R.color.brand_dark)));
        Ui.add(root, sync, 12);

        // ---- 3 stat tiles ----
        int attempts = 0;
        for (DocumentItem d : items) attempts += d.quizHistory != null ? d.quizHistory.size() : 0;
        LinearLayout stats = Ui.horizontal(c);
        stats.setBaselineAligned(false);
        statTile(stats, R.drawable.ic_book, R.color.teal_200, R.color.brand_dark, String.valueOf(items.size()), "Bài học đã lưu", 26, 0);
        statTile(stats, R.drawable.ic_award, R.color.m3_tertiary_fixed, R.color.m3_tertiary, String.valueOf(attempts), "Lượt Quiz xong", 26, 8);
        String last = items.isEmpty() ? "Chưa có" : lastStudied(items.get(0).updatedAt);
        statTile(stats, R.drawable.ic_history, R.color.m3_secondary_container, R.color.m3_secondary, last, "Lần học gần nhất", 15, 8);
        Ui.add(root, stats, 20);

        // ---- Search ----
        LinearLayout searchBox = Ui.horizontal(c);
        searchBox.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.border));
        searchBox.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 14), 0);
        searchBox.addView(Ui.icon(c, R.drawable.ic_search, 20, Ui.color(c, R.color.text_secondary)));
        EditText search = new EditText(c);
        search.setHint("Tìm bài học, môn học hoặc từ khóa...");
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextColor(Ui.color(c, R.color.text));
        search.setHintTextColor(Ui.color(c, R.color.text_muted));
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); fillList(); }
        });
        LinearLayout.LayoutParams sel = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f);
        sel.setMarginStart(Ui.dp(c, 8));
        searchBox.addView(search, sel);
        searchBox.addView(Ui.icon(c, R.drawable.ic_target, 20, Ui.color(c, R.color.text_secondary)));
        Ui.add(root, searchBox, 20);

        // ---- Filter chips ----
        Set<String> subjects = new LinkedHashSet<>();
        for (DocumentItem d : items) if (d.tags != null) for (String t : d.tags) if (!ENGINE_TAGS.contains(t)) subjects.add(t);
        List<String[]> pills = new ArrayList<>(Arrays.asList(new String[]{"ALL", "Tất cả (" + items.size() + ")"}, new String[]{"PDF", "Tài liệu PDF"},
                new String[]{"VIDEO", "Video"}, new String[]{"URL", "Web"}));
        for (String s : subjects) pills.add(new String[]{s, s});
        LinearLayout pillRow = Ui.horizontal(c);
        for (String[] p : pills) {
            boolean active = tag.equals(p[0]);
            TextView t = Ui.text(c, p[1], 14, active ? android.R.color.white : R.color.text_secondary, true);
            t.setBackground(active ? Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E) : Ui.rounded(c, R.color.m3_surface_low, 999, 0));
            if (active) t.setTextColor(0xFFFFFFFF);
            t.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 10));
            t.setOnClickListener(v -> { tag = p[0]; rerender(); });
            Ui.addWrap(pillRow, t, pillRow.getChildCount() > 0 ? 8 : 0);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pillRow);
        Ui.add(root, hs, 14);

        list = Ui.add(root, Ui.vertical(c), 4);
        fillList();
        // room for the floating fusion bar
        if (!forFusion.isEmpty()) root.addView(new View(c), new LinearLayout.LayoutParams(1, Ui.dp(c, 80)));
        renderFusionBar();
    }

    private static String lastStudied(String iso) {
        long t = Ui.epochMillis(iso);
        if (t == 0) return Ui.date(iso);
        java.util.Calendar now = java.util.Calendar.getInstance(), then = java.util.Calendar.getInstance();
        then.setTimeInMillis(t);
        String hm = new java.text.SimpleDateFormat("HH:mm", Locale.US).format(then.getTime());
        if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR))
            return hm + " Hôm nay";
        return Ui.date(iso);
    }

    private void statTile(LinearLayout row, int icon, int bg, int fg, String value, String label, float valueSp, int start) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        in.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
        in.addView(Ui.iconBox(c, icon, bg, fg, 40, 20, 999));
        TextView v = Ui.text(c, value, valueSp, R.color.text, true);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(1);
        Ui.add(in, v, 8);
        TextView l = Ui.text(c, label, 13, R.color.text_secondary, false);
        l.setGravity(Gravity.CENTER);
        Ui.add(in, l, 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(card, lp);
    }

    /** Dark floating bar: "Đã chọn N bài · Bỏ chọn · Hợp nhất AI PRO" */
    private void renderFusionBar() {
        if (fusionDock == null) return;
        fusionDock.removeAllViews();
        if (forFusion.isEmpty()) return;
        Context c = requireContext();
        LinearLayout bar = Ui.horizontal(c);
        bar.setBackground(Ui.gradient(c, 20, 0xFF213145, 0xFF213145));
        bar.setElevation(Ui.dp(c, 10));
        bar.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.amber, 999, 0));
        bar.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)));
        LinearLayout tx = Ui.vertical(c);
        TextView t1 = Ui.text(c, "Đã chọn " + forFusion.size() + " bài", 13, R.color.text, true);
        t1.setTextColor(0xFFFFFFFF);
        tx.addView(t1);
        TextView t2 = Ui.text(c, forFusion.size() >= 2 ? "Hợp nhất đề thi & kiến thức" : "Chọn thêm ít nhất 1 bài", 11, R.color.text, false);
        t2.setTextColor(0xFFB9C7DF);
        tx.addView(t2);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 8));
        bar.addView(tx, txl);
        TextView clear = Ui.text(c, "Bỏ chọn", 13, R.color.text, true);
        clear.setTextColor(0xFFEAF1FF);
        clear.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        clear.setOnClickListener(v -> { forFusion.clear(); rerender(); });
        bar.addView(clear);
        boolean ready = forFusion.size() >= 2 && !fusing;
        LinearLayout go = Ui.iconText(c, R.drawable.ic_sparkles, fusing ? "Đang hợp nhất..." : "Hợp nhất AI", 14, 0xFFFFFFFF, true);
        if (!state.isPro()) {
            TextView pro = Ui.pill(c, "PRO", R.color.amber, R.color.m3_on_tertiary_fixed);
            pro.setTextColor(0xFF2A1700);
            Ui.addWrap(go, pro, 6);
        }
        go.setBackground(Ui.gradient(c, 999, 0xFF945D00, 0xFF734700));
        go.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        go.setAlpha(ready ? 1f : 0.6f);
        go.setOnClickListener(v -> fuse());
        Ui.addWrap(bar, go, 4);
        fusionDock.addView(bar);
    }

    private void fuse() {
        if (forFusion.size() < 2 || fusing) {
            if (forFusion.size() < 2) toast("Vui lòng chọn ít nhất 2 bài học");
            return;
        }
        if (!state.isPro()) {
            state.openUpgrade("Hợp nhất kiến thức đa tài liệu là tính năng của StudyMind Pro.");
            return;
        }
        // Runs in the background; the Fusion screen shows its progress and result
        state.fuse(new ArrayList<>(forFusion));
        state.navigate(Screen.FUSION);
    }

    private void fillList() {
        Context c = requireContext();
        list.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (DocumentItem d : state.history()) {
            List<String> tags = d.tags != null ? d.tags : new ArrayList<>();
            boolean match = q.isEmpty() || d.title.toLowerCase(Locale.ROOT).contains(q);
            for (String t : tags) if (t.toLowerCase(Locale.ROOT).contains(q)) match = true;
            if (!match) continue;
            if (tag.equals("PDF") && !"PDF".equals(d.fileType)) continue;
            if (tag.equals("VIDEO") && !"VIDEO".equals(d.fileType)) continue;
            if (tag.equals("URL") && !"URL".equals(d.fileType)) continue;
            if (!Arrays.asList("ALL", "PDF", "VIDEO", "URL").contains(tag) && !tags.contains(tag)) continue;
            shown++;
            Ui.add(list, lessonCard(c, d, tags), 16);
        }
        if (shown == 0) {
            MaterialCardView empty = Ui.card(c);
            LinearLayout in = Ui.inner(empty);
            in.setGravity(Gravity.CENTER_HORIZONTAL);
            in.setPadding(Ui.dp(c, 24), Ui.dp(c, 40), Ui.dp(c, 24), Ui.dp(c, 40));
            in.addView(Ui.iconBox(c, R.drawable.ic_history, R.color.m3_surface_container, R.color.text_muted, 52, 26, 999));
            TextView t = Ui.text(c, "Không tìm thấy bài học nào", 16, R.color.text, true);
            t.setGravity(Gravity.CENTER);
            Ui.add(in, t, 12);
            TextView m = Ui.text(c, "Thử thay đổi từ khóa tìm kiếm hoặc nhập bài học mới trong tab \"Nhập tài liệu\".", 13, R.color.text_muted, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(in, m, 6);
            Ui.add(list, empty, 16);
        }
    }

    private View lessonCard(Context c, DocumentItem d, List<String> tags) {
        boolean selected = forFusion.contains(d.id);
        MaterialCardView card = Ui.card(c);
        if (selected) {
            card.setStrokeColor(Ui.color(c, R.color.brand));
            card.setStrokeWidth(Ui.dp(c, 2));
        }
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));

        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        android.widget.CheckBox cb = Ui.checkbox(c, "", selected, (b, checked) -> {
            if (checked) forFusion.add(d.id); else forFusion.remove(d.id);
            rerender();
        });
        top.addView(cb);
        LinearLayout tx = Ui.vertical(c);
        LinearLayout meta = Ui.horizontal(c);
        String subject = null;
        for (String t : tags) if (!ENGINE_TAGS.contains(t)) { subject = t; break; }
        if (subject != null) meta.addView(Ui.pill(c, subject, R.color.brand_soft, R.color.brand_dark));
        String type = d.fileType != null && !d.fileType.isEmpty() ? d.fileType.toUpperCase(Locale.ROOT) : "DOC";
        String typeLabel = type.equals("VIDEO") ? "YouTube Video" : type.equals("URL") ? "Bài báo Web" : type + (d.fileSize != null ? " (" + d.fileSize + ")" : "");
        TextView mt = Ui.text(c, (subject != null ? " • " : "") + typeLabel + " • " + Ui.date(d.updatedAt), 13, R.color.text_secondary, false);
        meta.addView(mt);
        tx.addView(meta);
        TextView title = Ui.text(c, d.title, 18, R.color.text, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.add(tx, title, 6);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 4));
        top.addView(tx, txl);
        ImageView more = Ui.icon(c, R.drawable.ic_more, 22, Ui.color(c, R.color.text_secondary));
        more.setRotation(90);
        more.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(c, v);
            pm.getMenu().add(0, 1, 0, "Học tiếp");
            pm.getMenu().add(0, 2, 1, selected ? "Bỏ chọn hợp nhất" : "Chọn để hợp nhất");
            pm.getMenu().add(0, 3, 2, "Xóa bài học");
            pm.setOnMenuItemClickListener(mi -> {
                if (mi.getItemId() == 1) state.openDocument(d.id, Screen.WORKSPACE);
                else if (mi.getItemId() == 2) { if (selected) forFusion.remove(d.id); else forFusion.add(d.id); rerender(); }
                else confirmDelete(c, d);
                return true;
            });
            pm.show();
        });
        top.addView(more);
        in.addView(top);

        List<QuizHistoryRecord> qh = d.quizHistory != null ? d.quizHistory : new ArrayList<>();
        LinearLayout score = Ui.horizontal(c);
        score.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        score.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        Ui.addWeighted(score, Ui.text(c, "Điểm Quiz cao nhất:", 13, R.color.text_secondary, false), 0);
        if (qh.isEmpty()) {
            score.addView(Ui.pill(c, "Chưa làm", R.color.m3_surface_container, R.color.text_muted));
        } else {
            int best = 0;
            for (QuizHistoryRecord r : qh) best = Math.max(best, r.score);
            String txt = String.format(Locale.US, "%.1f / 10.0", best / 10f) + (best >= 100 ? " (Tuyệt đối)" : "");
            TextView bp = Ui.pill(c, txt, best >= 100 ? R.color.m3_tertiary_fixed : R.color.m3_primary_fixed, best >= 100 ? R.color.m3_on_tertiary_fixed : R.color.brand_dark);
            bp.setTextSize(14);
            score.addView(bp);
        }
        Ui.add(in, score, 14);

        LinearLayout actions = Ui.horizontal(c);
        LinearLayout go = Ui.iconText(c, R.drawable.ic_play, "Học tiếp ngay", 16, 0xFFFFFFFF, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.gradient(c, 16, 0xFF0F766E, 0xFF005C55));
        go.setMinimumHeight(Ui.dp(c, 50));
        go.setOnClickListener(v -> state.openDocument(d.id, Screen.WORKSPACE));
        Ui.addWeighted(actions, go, 0);
        LinearLayout hist = Ui.iconText(c, R.drawable.ic_quiz, "Lịch sử Quiz", 16, Ui.color(c, qh.isEmpty() ? R.color.text_muted : R.color.text_secondary), false);
        hist.setGravity(Gravity.CENTER);
        hist.setBackground(Ui.rounded(c, R.color.m3_surface_high, 16, 0));
        hist.setMinimumHeight(Ui.dp(c, 50));
        hist.setAlpha(qh.isEmpty() ? 0.6f : 1f);
        hist.setOnClickListener(v -> {
            if (qh.isEmpty()) toast("Bài học này chưa có lượt làm Quiz nào.");
            else showQuizHistory(d, qh);
        });
        Ui.addWeighted(actions, hist, 8);
        Ui.add(in, actions, 14);
        return card;
    }

    private void confirmDelete(Context c, DocumentItem d) {
        new MaterialAlertDialogBuilder(c)
                .setTitle("Xóa bài học?")
                .setMessage("\"" + d.title + "\" sẽ bị xóa khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dlg, w) -> state.deleteLesson(d.id))
                .show();
    }

    // ---------- "Lịch sử làm Quiz" bottom sheet ----------

    private static String grade(int pct) {
        return pct >= 100 ? "Tuyệt đối" : pct >= 85 ? "Giỏi" : pct >= 80 ? "Xuất sắc" : pct >= 65 ? "Khá" : pct >= 50 ? "Trung bình" : "Cần cố gắng";
    }

    private void showQuizHistory(DocumentItem d, List<QuizHistoryRecord> attemptsNewestFirst) {
        Context c = requireContext();
        List<QuizHistoryRecord> chrono = new ArrayList<>(attemptsNewestFirst);
        java.util.Collections.reverse(chrono);
        Sheet.show(c, R.drawable.ic_quiz, "Lịch sử làm Quiz", d.title, (body, sheet) -> {
            int n = chrono.size();
            int best = 0, bestIdx = 0, sum = 0;
            for (int i = 0; i < n; i++) {
                int s = chrono.get(i).score;
                sum += s;
                if (s >= best) { best = s; bestIdx = i; }
            }
            LinearLayout stats = Ui.horizontal(c);
            stats.setBaselineAligned(false);
            miniStat(stats, R.drawable.ic_refresh, "Tổng lượt", String.valueOf(n), " lần", R.color.m3_surface_low, R.color.text, 0);
            miniStat(stats, R.drawable.ic_award, "Điểm cao", String.format(Locale.US, "%.1f", best / 10f), " /10", R.color.m3_primary_fixed, R.color.brand_dark, 8);
            miniStat(stats, R.drawable.ic_target, "Trung bình", String.format(Locale.US, "%.1f", sum / 10f / n), " /10", R.color.m3_surface_low, R.color.text, 8);
            body.addView(stats);

            // Improvement chart
            LinearLayout chartCard = Ui.vertical(c);
            chartCard.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
            chartCard.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            LinearLayout ch = Ui.horizontal(c);
            ch.addView(Ui.icon(c, R.drawable.ic_route, 20, Ui.color(c, R.color.brand)));
            TextView ct = Ui.text(c, "Tiến độ cải thiện", 16, R.color.text, true);
            LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ctl.setMarginStart(Ui.dp(c, 8));
            ch.addView(ct, ctl);
            float delta = (chrono.get(n - 1).score - chrono.get(0).score) / 10f;
            ch.addView(Ui.pill(c, (delta >= 0 ? "+" : "") + String.format(Locale.US, "%.1f", delta) + " điểm", R.color.m3_primary_fixed, R.color.brand_dark));
            chartCard.addView(ch);
            float[] vals = new float[n];
            for (int i = 0; i < n; i++) vals[i] = chrono.get(i).score;
            LineChart chart = new LineChart(c, vals, 100, bestIdx);
            Ui.add(chartCard, chart, 10).getLayoutParams().height = Ui.dp(c, 90);
            LinearLayout labels = Ui.horizontal(c);
            int from = Math.max(0, n - 6);
            for (int i = from; i < n; i++) {
                TextView l = Ui.text(c, "Lần " + (i + 1) + "\n(" + String.format(Locale.US, "%.1f", chrono.get(i).score / 10f) + ")", 12,
                        i == bestIdx ? R.color.m3_tertiary : i == n - 1 ? R.color.brand : R.color.text_secondary, false);
                l.setGravity(Gravity.CENTER);
                Ui.addWeighted(labels, l, 0);
            }
            Ui.add(chartCard, labels, 4);
            Ui.add(body, chartCard, 14);

            LinearLayout dh = Ui.horizontal(c);
            Ui.addWeighted(dh, Ui.text(c, "Chi tiết từng lần làm", 18, R.color.text, true), 0);
            dh.addView(Ui.text(c, n + " bản ghi", 13, R.color.text_muted, false));
            Ui.add(body, dh, 22);

            for (int i = n - 1; i >= 0; i--) {
                QuizHistoryRecord a = chrono.get(i);
                boolean latest = i == n - 1;
                boolean top = i == bestIdx;
                LinearLayout row = Ui.vertical(c);
                row.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
                row.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16));
                LinearLayout r1 = Ui.horizontal(c);
                r1.setGravity(Gravity.TOP);
                LinearLayout left = Ui.vertical(c);
                LinearLayout badges = Ui.horizontal(c);
                badges.addView(Ui.text(c, "Lần " + (i + 1), 18, R.color.text, true));
                if (latest) Ui.addWrap(badges, Ui.pill(c, "Gần nhất", R.color.m3_primary_fixed, R.color.brand_dark), 8);
                Ui.addWrap(badges, Ui.pill(c, (top && a.score >= 100 ? "☆ " : "") + grade(a.score), top && a.score >= 100 ? R.color.m3_tertiary_fixed : R.color.m3_secondary_container,
                        top && a.score >= 100 ? R.color.m3_on_tertiary_fixed : R.color.m3_on_secondary_container), 6);
                left.addView(badges);
                Ui.add(left, Ui.iconText(c, R.drawable.ic_calendar, Ui.dateTime(a.completedAt), 13, Ui.color(c, R.color.text_secondary), false), 6);
                Ui.addWeighted(r1, left, 0);
                LinearLayout right = Ui.vertical(c);
                right.setGravity(Gravity.END);
                android.text.SpannableStringBuilder sc = new android.text.SpannableStringBuilder(String.format(Locale.US, "%.1f", a.score / 10f));
                int e = sc.length();
                sc.append("/10");
                sc.setSpan(new android.text.style.RelativeSizeSpan(0.55f), e, sc.length(), 0);
                TextView sv = Ui.text(c, sc, 24, a.score >= 100 ? R.color.m3_tertiary : R.color.brand_dark, true);
                right.addView(sv);
                right.addView(Ui.text(c, a.correctCount + "/" + a.totalQuestions + " câu đúng", 12, R.color.text_secondary, false));
                Ui.addWrap(r1, right, 8);
                row.addView(r1);
                if (latest || top) {
                    LinearLayout foot = Ui.horizontal(c);
                    foot.setBackground(Ui.rounded(c, top && !latest ? R.color.amber_50 : R.color.surface, 14, 0));
                    foot.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                    String msg = top && !latest ? "Kỷ lục cá nhân xuất sắc nhất!" : a.feedback != null && !a.feedback.isEmpty() ? a.feedback : "Đạt chuẩn mục tiêu ôn tập";
                    LinearLayout fm = Ui.iconText(c, top && !latest ? R.drawable.ic_award : R.drawable.ic_check_circle, msg, 13, Ui.color(c, R.color.text_secondary), false);
                    ((TextView) fm.getChildAt(1)).setMaxLines(2);
                    Ui.addWeighted(foot, fm, 0);
                    LinearLayout review = Ui.iconText(c, 0, "Xem lại bài", 13, 0xFFFFFFFF, true);
                    review.addView(Ui.icon(c, R.drawable.ic_chevron_right, 16, 0xFFFFFFFF));
                    review.setBackground(Ui.gradient(c, 10, 0xFF005C55, 0xFF005C55));
                    review.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
                    review.setOnClickListener(v -> { sheet.dismiss(); state.openDocument(d.id, Screen.QUIZ); });
                    Ui.addWrap(foot, review, 8);
                    Ui.add(row, foot, 12);
                }
                Ui.add(body, row, 12);
            }

            int gain = chrono.get(n - 1).score - chrono.get(0).score;
            if (n > 1) {
                LinearLayout tip = Ui.horizontal(c);
                tip.setBackground(Ui.rounded(c, R.color.teal_50, 16, 0));
                tip.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
                tip.addView(Ui.iconBox(c, R.drawable.ic_lightbulb, R.color.teal_200, R.color.brand_dark, 40, 20, 999));
                String msg = gain > 0 ? "Bạn đã nâng cao " + gain + "% độ chuẩn xác so với bài kiểm tra khởi động!"
                        : "Hãy ôn lại phần còn yếu rồi làm thêm một lượt để cải thiện điểm số nhé!";
                TextView tt = Ui.text(c, msg, 14, R.color.text, false);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tl.setMarginStart(Ui.dp(c, 12));
                tip.addView(tt, tl);
                Ui.add(body, tip, 14);
            }

            LinearLayout cta = Ui.iconText(c, R.drawable.ic_play, "Làm bài kiểm tra mới ngay", 16, 0xFFFFFFFF, true);
            cta.setGravity(Gravity.CENTER);
            cta.setBackground(Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E));
            cta.setMinimumHeight(Ui.dp(c, 56));
            cta.setOnClickListener(v -> { sheet.dismiss(); state.openDocument(d.id, Screen.QUIZ); });
            Ui.add(body, cta, 18);
        });
    }

    private void miniStat(LinearLayout row, int icon, String label, String value, String unit, int bg, int fg, int start) {
        Context c = requireContext();
        LinearLayout t = Ui.vertical(c);
        t.setBackground(Ui.rounded(c, bg, 16, 0));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        t.addView(Ui.iconText(c, icon, label, 13, Ui.color(c, fg == R.color.text ? R.color.text_secondary : fg), fg != R.color.text));
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(value);
        int e = sb.length();
        sb.append(unit);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.6f), e, sb.length(), 0);
        Ui.add(t, Ui.text(c, sb, 24, fg, true), 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(t, lp);
    }
}
