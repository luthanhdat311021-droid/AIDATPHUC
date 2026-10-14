package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.QuizHistoryRecord;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.Modal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Lịch sử Bài học & Tiến trình Học tập" — HistoryView.tsx. */
public class HistoryFragment extends BaseFragment {
    private static final List<String> ENGINE_TAGS = Arrays.asList("AI Analysis Engine", "YouTube Speech-to-Text", "Web Article Extractor");

    private String query = "";
    private String tag = "ALL";
    private final Set<String> forFusion = new LinkedHashSet<>();
    private boolean fusing;
    private LinearLayout list;

    @Override
    protected Object watchKey() { return state.history(); }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        List<DocumentItem> items = state.history();

        // ---- Header banner + Supabase badge ----
        MaterialCardView head = Ui.card(c);
        LinearLayout hi = Ui.inner(head);
        hi.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22));
        LinearLayout kicker = Ui.iconText(c, R.drawable.ic_history, "LỊCH SỬ & ĐỒNG BỘ DỮ LIỆU", 12, Ui.color(c, R.color.brand), true);
        hi.addView(kicker);
        Ui.add(hi, Ui.text(c, "Lịch sử Bài học & Tiến trình Học tập", 22, R.color.text, true), 6);
        Ui.add(hi, Ui.text(c, "Quản lý toàn bộ các bài học đã chuyển hóa, kết quả trắc nghiệm và sơ đồ tư duy đã tạo.", 13, R.color.text_muted, false), 4);
        LinearLayout badge = Ui.horizontal(c);
        badge.setBackground(Ui.rounded(c, R.color.emerald_50, 12, R.color.emerald_soft));
        badge.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        View db = Ui.iconBox(c, R.drawable.ic_bank, R.color.emerald, android.R.color.white, 36, 20, 8);
        badge.addView(db);
        LinearLayout bt = Ui.vertical(c);
        bt.addView(Ui.text(c, "Supabase Database Active  ●", 12, R.color.emerald_700, true));
        bt.addView(Ui.text(c, "Đã kết nối CSDL Supabase — Bài học được lưu trữ vĩnh viễn", 11, R.color.emerald_700, false));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bl.setMarginStart(Ui.dp(c, 12));
        badge.addView(bt, bl);
        Ui.add(hi, badge, 16);
        root.addView(head);

        // ---- Mode tabs (underline style) ----
        int brand = Ui.color(c, R.color.brand);
        Ui.add(root, Ui.tabBar(c,
                Ui.underlineTab(c, R.drawable.ic_book, "Danh sách Bài học (" + items.size() + ")", true, brand, null),
                Ui.underlineTab(c, R.drawable.ic_compare, "Hợp nhất Kiến thức AI" + (state.fusionResult() != null ? " ✨" : ""), false, brand,
                        v -> state.navigate(Screen.FUSION))), 20);

        if (!forFusion.isEmpty()) renderFusionBar(root);

        // ---- Stats summary ----
        int attempts = 0;
        for (DocumentItem d : items) attempts += d.quizHistory != null ? d.quizHistory.size() : 0;
        statRow(root, R.drawable.ic_book, R.color.teal_50, R.color.brand, "Tổng bài học đã lưu", String.valueOf(items.size()), 24);
        statRow(root, R.drawable.ic_award, R.color.amber_50, R.color.amber, "Lượt hoàn thành Quiz", String.valueOf(attempts), 24);
        String last = items.isEmpty() ? "Chưa có" : Ui.dateTime(items.get(0).updatedAt);
        statRow(root, R.drawable.ic_lightbulb, R.color.indigo_50, R.color.indigo, "Lần học gần nhất", last, 14);

        // ---- Search + filter pills ----
        MaterialCardView filter = Ui.card(c);
        LinearLayout fi = Ui.inner(filter);
        EditText search = Ui.field(c, "Tìm kiếm bài học, môn học...", false);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.slate_400)));
        search.setCompoundDrawablePadding(Ui.dp(c, 8));
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); fillList(); }
        });
        fi.addView(search);
        Set<String> subjects = new LinkedHashSet<>();
        for (DocumentItem d : items) if (d.tags != null) for (String t : d.tags) if (!ENGINE_TAGS.contains(t)) subjects.add(t);
        List<String[]> pills = new ArrayList<>(Arrays.asList(new String[]{"ALL", "Tất cả"}, new String[]{"PDF", "Tài liệu PDF"},
                new String[]{"VIDEO", "Video"}, new String[]{"URL", "Web"}));
        for (String s : subjects) pills.add(new String[]{s, "📁 " + s});
        LinearLayout pillRow = Ui.horizontal(c);
        for (String[] p : pills) {
            boolean active = tag.equals(p[0]);
            TextView t = Ui.text(c, p[1], 12, active ? android.R.color.white : R.color.text_secondary, true);
            t.setBackground(Ui.rounded(c, active ? R.color.brand : R.color.slate_100, 8, 0));
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
            t.setOnClickListener(v -> { tag = p[0]; rerender(); });
            Ui.addWrap(pillRow, t, pillRow.getChildCount() > 0 ? 8 : 0);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pillRow);
        Ui.add(fi, hs, 12);
        Ui.add(root, filter, 16);

        list = Ui.add(root, Ui.vertical(c), 8);
        fillList();
    }

    private void statRow(LinearLayout root, int icon, int bg, int fg, String label, String value, float valueSp) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout row = Ui.horizontal(c);
        row.addView(Ui.iconBox(c, icon, bg, fg, 48, 24, 12));
        LinearLayout t = Ui.vertical(c);
        t.addView(Ui.text(c, label, 12, R.color.text_muted, false));
        t.addView(Ui.text(c, value, valueSp, R.color.text, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(c, 16));
        row.addView(t, lp);
        Ui.inner(card).addView(row);
        Ui.add(root, card, 12);
    }

    private void renderFusionBar(LinearLayout root) {
        Context c = requireContext();
        LinearLayout bar = Ui.vertical(c);
        android.graphics.drawable.GradientDrawable bg = Ui.gradient(c, 16, 0xFFF0FDFA, 0xFFECFDF5, 0xFFF0FDFA);
        bg.setStroke(Ui.dp(c, 2), 0x4D14B8A6);
        bar.setBackground(bg);
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
        LinearLayout top = Ui.horizontal(c);
        top.addView(Ui.iconBox(c, R.drawable.ic_compare, R.color.brand, R.color.brand_soft, 40, 20, 12));
        LinearLayout t = Ui.vertical(c);
        t.addView(Ui.text(c, "Đã chọn " + forFusion.size() + " bài học có nội dung tương tự", 13, R.color.brand, true));
        t.addView(Ui.text(c, "Sẵn sàng hợp nhất thông tin trùng lặp, đối chiếu khác biệt và phát hiện cờ cảnh báo ⚠️", 11, R.color.text_secondary, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(c, 12));
        top.addView(t, lp);
        bar.addView(top);
        LinearLayout actions = Ui.horizontal(c);
        LinearLayout clear = Ui.pillButton(c, 0, "Bỏ chọn", R.color.surface, R.color.text_secondary, R.color.border, v -> { forFusion.clear(); rerender(); });
        actions.addView(clear);
        boolean ready = forFusion.size() >= 2 && !fusing;
        LinearLayout go = Ui.pillButton(c, R.drawable.ic_sparkles, fusing ? "Đang hợp nhất..." : "Hợp nhất AI (" + forFusion.size() + " bài)",
                ready ? R.color.brand : R.color.slate_200, ready ? android.R.color.white : R.color.slate_400, 0, v -> fuse());
        Ui.addWeighted(actions, go, 8);
        Ui.add(bar, actions, 12);
        Ui.add(root, bar, 16);
    }

    private void fuse() {
        if (forFusion.size() < 2 || fusing) return;
        if (!state.isPro()) {
            state.openUpgrade("Hợp nhất kiến thức đa tài liệu là tính năng của StudyMind Pro.");
            return;
        }
        fusing = true;
        rerender();
        state.fuse(new ArrayList<>(forFusion), ok -> {
            fusing = false;
            if (ok) state.navigate(Screen.FUSION);
            else if (isAdded()) rerender();
        });
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
            in.addView(Ui.iconBox(c, R.drawable.ic_history, R.color.slate_100, R.color.slate_400, 48, 24, 999));
            TextView t = Ui.text(c, "Không tìm thấy bài học nào", 16, R.color.text, true);
            t.setGravity(Gravity.CENTER);
            Ui.add(in, t, 12);
            TextView m = Ui.text(c, "Thử thay đổi từ khóa tìm kiếm hoặc nhập bài học mới trong tab \"Nhập tài liệu\".", 12, R.color.text_muted, false);
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
        in.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));

        LinearLayout top = Ui.horizontal(c);
        String type = d.fileType != null && !d.fileType.isEmpty() ? d.fileType.toUpperCase(Locale.ROOT) : "DOC";
        int icon = type.equals("VIDEO") ? R.drawable.ic_video : type.equals("URL") ? R.drawable.ic_link : R.drawable.ic_file;
        int iconColor = type.equals("VIDEO") ? R.color.indigo : type.equals("URL") ? R.color.emerald : R.color.brand;
        top.addView(Ui.iconBox(c, icon, R.color.slate_100, iconColor, 36, 20, 12));
        TextView typePill = Ui.text(c, type, 11, R.color.text_secondary, true);
        typePill.setBackground(Ui.rounded(c, R.color.slate_100, 6, 0));
        typePill.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addWrap(top, typePill, 8);
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout pick = Ui.iconText(c, 0, (selected ? "☑ Đã chọn" : "☐ Hợp nhất"), 11, Ui.color(c, selected ? android.R.color.white : R.color.text_secondary), true);
        pick.setBackground(Ui.rounded(c, selected ? R.color.brand : R.color.slate_50, 8, selected ? R.color.brand : R.color.border));
        pick.setPadding(Ui.dp(c, 8), Ui.dp(c, 5), Ui.dp(c, 8), Ui.dp(c, 5));
        pick.setOnClickListener(v -> { if (selected) forFusion.remove(d.id); else forFusion.add(d.id); rerender(); });
        top.addView(pick);
        ImageView del = Ui.icon(c, R.drawable.ic_trash, 18, Ui.color(c, R.color.slate_400));
        del.setOnClickListener(v -> new MaterialAlertDialogBuilder(c)
                .setTitle("Xóa bài học?")
                .setMessage("\"" + d.title + "\" sẽ bị xóa khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dlg, w) -> state.deleteLesson(d.id))
                .show());
        Ui.addWrap(top, del, 10);
        in.addView(top);

        TextView title = Ui.text(c, d.title, 16, R.color.text, true);
        title.setMaxLines(2);
        Ui.add(in, title, 12);

        if (!tags.isEmpty()) {
            LinearLayout tagRow = Ui.horizontal(c);
            for (String t : tags) {
                boolean subject = !ENGINE_TAGS.contains(t);
                TextView tv = Ui.text(c, (subject ? "📁 " : "") + t, 10, subject ? R.color.emerald_700 : R.color.brand, subject);
                tv.setBackground(Ui.rounded(c, subject ? R.color.emerald_soft : R.color.teal_50, 6, 0));
                tv.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
                Ui.addWrap(tagRow, tv, tagRow.getChildCount() > 0 ? 6 : 0);
            }
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(tagRow);
            Ui.add(in, hs, 10);
        }

        View sep = new View(c);
        sep.setBackgroundColor(Ui.color(c, R.color.slate_100));
        sep.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));
        Ui.add(in, sep, 14);

        List<QuizHistoryRecord> qh = d.quizHistory != null ? d.quizHistory : new ArrayList<>();
        LinearLayout meta = Ui.horizontal(c);
        Ui.addWeighted(meta, Ui.iconText(c, R.drawable.ic_calendar, Ui.date(d.updatedAt), 11, Ui.color(c, R.color.text_muted), false), 0);
        if (!qh.isEmpty()) {
            int best = 0;
            for (QuizHistoryRecord r : qh) best = Math.max(best, r.score);
            LinearLayout bestPill = Ui.iconText(c, R.drawable.ic_award, "Điểm cao nhất: " + best + "%", 11, Ui.color(c, R.color.amber_700), true);
            bestPill.setBackground(Ui.rounded(c, R.color.amber_50, 6, 0));
            bestPill.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
            bestPill.setOnClickListener(v -> showQuizHistory(d.title, qh));
            meta.addView(bestPill);
        }
        Ui.add(in, meta, 12);

        LinearLayout ready = Ui.horizontal(c);
        ready.addView(Ui.iconText(c, R.drawable.ic_check_circle, "Notes AI", 11, Ui.color(c, R.color.emerald), true));
        Ui.addWrap(ready, Ui.iconText(c, R.drawable.ic_check_circle, "Mindmap", 11, 0xFF0D9488, true), 12);
        Ui.addWrap(ready, Ui.iconText(c, R.drawable.ic_check_circle, "Quiz", 11, Ui.color(c, R.color.indigo), true), 12);
        Ui.add(in, ready, 8);

        LinearLayout actions = Ui.horizontal(c);
        LinearLayout go = Ui.pillButton(c, R.drawable.ic_book, "Học tiếp ngay", R.color.brand, android.R.color.white, 0,
                v -> state.openDocument(d.id, Screen.WORKSPACE));
        go.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        Ui.addWeighted(actions, go, 0);
        if (!qh.isEmpty()) {
            View chart = Ui.iconBox(c, R.drawable.ic_award, R.color.slate_100, R.color.text_secondary, 42, 18, 12);
            chart.setOnClickListener(v -> showQuizHistory(d.title, qh));
            Ui.addWrap(actions, chart, 8);
        }
        Ui.add(in, actions, 14);
        return card;
    }

    /** "Lịch sử làm Quiz" modal */
    private void showQuizHistory(String title, List<QuizHistoryRecord> attempts) {
        Context c = requireContext();
        Modal.show(c, R.drawable.ic_award, R.color.amber, "Lịch sử làm Quiz", "Đóng", (body, dialog) -> {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Bài học: ");
            int st = sb.length();
            sb.append(title);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length(), 0);
            body.addView(Ui.text(c, sb, 12, R.color.text_muted, false));
            for (int i = 0; i < attempts.size(); i++) {
                QuizHistoryRecord a = attempts.get(i);
                LinearLayout row = Ui.horizontal(c);
                row.setBackground(Ui.rounded(c, R.color.slate_50, 12, R.color.border));
                row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
                LinearLayout left = Ui.vertical(c);
                left.addView(Ui.text(c, "Lần làm #" + (attempts.size() - i), 12, R.color.text, true));
                left.addView(Ui.text(c, Ui.dateTime(a.completedAt), 10, R.color.slate_400, false));
                if (a.feedback != null && !a.feedback.isEmpty()) {
                    TextView fb = Ui.text(c, "\"" + a.feedback + "\"", 11, R.color.brand, false);
                    fb.setTypeface(null, android.graphics.Typeface.ITALIC);
                    left.addView(fb);
                }
                Ui.addWeighted(row, left, 0);
                LinearLayout right = Ui.vertical(c);
                right.setGravity(Gravity.END);
                right.addView(Ui.text(c, a.score + "%", 16, a.score >= 80 ? R.color.emerald : R.color.amber_700, true));
                right.addView(Ui.text(c, a.correctCount + "/" + a.totalQuestions + " đúng", 10, R.color.text_muted, false));
                Ui.addWrap(row, right, 8);
                Ui.add(body, row, 10);
            }
        });
    }
}
