package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.os.SystemClock;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.AIQuiz;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.QuizQuestion;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.Modal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Luyện tập trắc nghiệm" — QuizView.tsx: question card, navigator grid, server grading, result modal. */
public class QuizFragment extends BaseFragment {
    private static final int LIMIT_SECONDS = 15 * 60;

    private String quizKey;
    private int current;
    private final Map<String, Integer> answers = new HashMap<>();
    private boolean submitted;
    private boolean generating;
    private long startedAt;
    private TextView timerView;
    private final Runnable tick = this::updateTimer;
    private WebView printView;

    @Override
    protected Object watchKey() { return state.activeDoc(); }

    @Override
    public void onStop() {
        Async.cancel(tick);
        super.onStop();
    }

    private final java.util.Set<String> flagged = new java.util.HashSet<>();
    private boolean showResult;
    private int resultScore, resultCorrect, resultTotal;
    private String resultFeedback;
    private long resultSeconds;

    @Override
    protected void render(LinearLayout root) {
        Async.cancel(tick);
        if (!requireDocument(root, "làm bài trắc nghiệm")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        AIQuiz quiz = a.pack.quiz();
        String key = a.document.id + ":" + (quiz != null ? quiz.questions.size() + ":" + quiz.questions.get(0).id : "none");
        if (!key.equals(quizKey)) { quizKey = key; reset(false); }

        if (submitted && showResult && quiz != null) { renderResult(root, a, quiz); return; }

        // ---- Header card ----
        LinearLayout head = Ui.vertical(c);
        head.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
        head.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 16));
        LinearLayout chips = Ui.horizontal(c);
        String subject = quiz != null && !quiz.subject.isEmpty() ? quiz.subject : "Luyện tập";
        LinearLayout sp = Ui.iconText(c, R.drawable.ic_book, subject, 12, Ui.color(c, R.color.m3_on_secondary_container), false);
        sp.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        sp.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        chips.addView(sp);
        chips.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout ai = Ui.iconText(c, R.drawable.ic_zap, "Chẩn đoán AI", 12, Ui.color(c, R.color.m3_tertiary), false);
        ai.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        ai.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        chips.addView(ai);
        head.addView(chips);
        Ui.add(head, Ui.text(c, quiz != null && !quiz.title.isEmpty() ? quiz.title : "Đề trắc nghiệm: " + a.document.title, 20, R.color.text, true), 10);
        Ui.add(head, Ui.text(c, "Mục tiêu: Đánh giá mức độ nắm vững kiến thức trong \"" + a.document.title + "\"", 13, R.color.text_secondary, false), 6);
        root.addView(head);

        LinearLayout tools = Ui.horizontal(c);
        LinearLayout gen = Ui.iconText(c, R.drawable.ic_sparkles, generating ? "Đang tạo 10–12 câu..." : "Tạo 10–12 câu hỏi bằng AI", 14, 0xFFFFFFFF, true);
        gen.setGravity(Gravity.CENTER);
        gen.setBackground(Ui.gradient(c, 16, 0xFF0F766E, 0xFF0F766E));
        gen.setMinimumHeight(Ui.dp(c, 50));
        gen.setAlpha(generating ? 0.6f : 1f);
        gen.setOnClickListener(v -> regenerate());
        tools.addView(gen, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        LinearLayout add = Ui.iconText(c, R.drawable.ic_plus_circle, "Thêm câu hỏi", 14, Ui.color(c, R.color.text), false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 16, 0));
        add.setMinimumHeight(Ui.dp(c, 50));
        add.setOnClickListener(v -> addDialog());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        al.setMarginStart(Ui.dp(c, 10));
        tools.addView(add, al);
        Ui.add(root, tools, 14);
        LinearLayout tools2 = Ui.horizontal(c);
        LinearLayout print = Ui.iconText(c, R.drawable.ic_file, "In đề thi / PDF", 13, Ui.color(c, R.color.text_secondary), false);
        print.setOnClickListener(v -> printExam(a, quiz));
        tools2.addView(print);
        LinearLayout retake = Ui.iconText(c, R.drawable.ic_reset, "Làm lại bài", 13, Ui.color(c, R.color.text_secondary), false);
        retake.setOnClickListener(v -> reset(true));
        Ui.addWrap(tools2, retake, 20);
        Ui.add(root, tools2, 10);

        if (quiz == null) {
            root.addView(Ui.empty(c, "Chưa có đề trắc nghiệm", "Bấm \"Tạo 10–12 câu hỏi bằng AI\" để AI soạn đề từ tài liệu.", null, null));
            return;
        }
        List<QuizQuestion> qs = quiz.questions;
        if (current >= qs.size()) current = 0;
        QuizQuestion q = qs.get(current);

        // ---- Progress card ----
        MaterialCardView prog = Ui.card(c);
        LinearLayout pi = Ui.inner(prog);
        LinearLayout pr = Ui.horizontal(c);
        LinearLayout timer = Ui.horizontal(c);
        timer.setBackground(Ui.rounded(c, R.color.rose_50, 14, 0));
        timer.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
        timer.addView(Ui.icon(c, R.drawable.ic_timer, 20, Ui.color(c, R.color.m3_error)));
        LinearLayout tcol = Ui.vertical(c);
        tcol.addView(Ui.text(c, "Còn lại", 11, R.color.text_secondary, false));
        timerView = Ui.text(c, "", 18, R.color.m3_error, true);
        tcol.addView(timerView);
        Ui.addWrap(timer, tcol, 8);
        pr.addView(timer);
        LinearLayout pc = Ui.vertical(c);
        pc.setGravity(Gravity.END);
        pc.addView(Ui.text(c, "Câu " + (current + 1) + " / " + qs.size(), 14, R.color.text, true));
        int donePct = Math.round(answers.size() * 100f / Math.max(1, qs.size()));
        pc.addView(Ui.text(c, "Đã hoàn thành " + donePct + "%", 12, R.color.text_secondary, false));
        Ui.addWeighted(pr, pc, 8);
        pi.addView(pr);
        Ui.add(pi, Ui.track(c, donePct, R.color.brand_dark, 8), 14);
        LinearLayout gh = Ui.horizontal(c);
        Ui.addWeighted(gh, Ui.text(c, "Lưới vị trí câu hỏi:", 12, R.color.text_secondary, false), 0);
        gh.addView(Ui.text(c, "Chạm để nhảy câu", 12, R.color.brand, false));
        Ui.add(pi, gh, 14);
        LinearLayout grid = Ui.horizontal(c);
        for (int i = 0; i < qs.size(); i++) {
            int idx = i;
            boolean answered = answers.containsKey(qs.get(i).id), cur = current == i, flag = flagged.contains(qs.get(i).id);
            LinearLayout cell = Ui.vertical(c);
            cell.setGravity(Gravity.CENTER);
            int bg = cur ? R.color.brand_dark : answered ? R.color.teal_200 : R.color.m3_surface_low;
            if (cur) cell.setBackground(Ui.gradient(c, 10, 0xFF005C55, 0xFF005C55));
            else cell.setBackground(Ui.rounded(c, bg, 10, flag ? R.color.amber : 0));
            TextView n = Ui.text(c, String.valueOf(i + 1), 13, cur ? android.R.color.white : answered ? R.color.brand_dark : R.color.text_secondary, cur || answered);
            if (cur) n.setTextColor(0xFFFFFFFF);
            cell.addView(n);
            View d = new View(c);
            d.setBackground(Ui.rounded(c, flag ? R.color.amber : cur ? android.R.color.white : answered ? R.color.brand_dark : android.R.color.transparent, 999, 0));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 5), Ui.dp(c, 5));
            dl.topMargin = Ui.dp(c, 2);
            cell.addView(d, dl);
            cell.setOnClickListener(v -> { current = idx; rerender(); });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 40));
            if (i > 0) cl.setMarginStart(Ui.dp(c, 8));
            grid.addView(cell, cl);
        }
        HorizontalScrollView gs = new HorizontalScrollView(c);
        gs.setHorizontalScrollBarEnabled(false);
        gs.addView(grid);
        Ui.add(pi, gs, 8);
        Ui.add(root, prog, 16);

        // ---- Question ----
        MaterialCardView qc = Ui.card(c);
        LinearLayout qi = Ui.inner(qc);
        LinearLayout qh = Ui.horizontal(c);
        qh.addView(Ui.pill(c, "Câu hỏi " + (current + 1) + " • 1.0 Điểm", R.color.m3_surface_container, R.color.text_secondary));
        qh.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        boolean isFlagged = flagged.contains(q.id);
        ImageView flag = Ui.icon(c, R.drawable.ic_target, 20, Ui.color(c, isFlagged ? R.color.amber : R.color.text_secondary));
        flag.setContentDescription("Đánh dấu câu cần xem lại");
        flag.setOnClickListener(v -> {
            if (isFlagged) flagged.remove(q.id); else flagged.add(q.id);
            toast(isFlagged ? "Đã bỏ đánh dấu câu " + (current + 1) : "Đã đánh dấu câu " + (current + 1) + " để xem lại");
            rerender();
        });
        qh.addView(flag);
        qi.addView(qh);
        TextView qt = Ui.text(c, q.questionText, 17, R.color.text, true);
        qt.setLineSpacing(0, 1.35f);
        Ui.add(qi, qt, 14);
        Ui.add(root, qc, 14);

        Integer chosen = answers.get(q.id);
        String[] letters = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < q.options.size(); i++) {
            int idx = i;
            boolean sel = chosen != null && chosen == i, correct = q.correctIndex == i;
            int bg = R.color.surface, stroke = R.color.border;
            String tag = null;
            int tagBg = 0;
            if (submitted) {
                if (correct) { bg = R.color.emerald_soft; stroke = R.color.emerald; tag = "Chính xác"; tagBg = R.color.brand_dark; }
                else if (sel) { bg = R.color.rose_soft; stroke = R.color.rose; tag = "Sai"; tagBg = R.color.rose; }
            } else if (sel) { bg = R.color.m3_primary_fixed; stroke = R.color.brand; }
            LinearLayout opt = Ui.horizontal(c);
            opt.setGravity(Gravity.TOP);
            android.graphics.drawable.GradientDrawable ob = Ui.rounded(c, bg, 18, 0);
            ob.setStroke(Ui.dp(c, sel || (submitted && correct) ? 2 : 1), Ui.color(c, stroke));
            opt.setBackground(ob);
            opt.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            View letter;
            if (sel || (submitted && correct)) {
                letter = Ui.iconBox(c, submitted && sel && !correct ? R.drawable.ic_close : R.drawable.ic_check_circle,
                        submitted && sel && !correct ? R.color.rose : R.color.brand_dark, android.R.color.white, 30, 18, 999);
            } else {
                TextView l = Ui.text(c, letters[Math.min(i, letters.length - 1)], 13, R.color.text_secondary, true);
                l.setGravity(Gravity.CENTER);
                l.setBackground(Ui.rounded(c, R.color.m3_surface_container, 999, 0));
                l.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
                letter = l;
            }
            opt.addView(letter);
            LinearLayout ot = Ui.vertical(c);
            if (sel || tag != null) {
                LinearLayout oh = Ui.horizontal(c);
                Ui.addWeighted(oh, Ui.text(c, "Lựa chọn " + letters[Math.min(i, letters.length - 1)] + (sel ? " (Đã chọn)" : ""), 12, R.color.brand_dark, true), 0);
                if (tag != null) {
                    TextView tg = Ui.pill(c, tag, tagBg, android.R.color.white);
                    tg.setTextColor(0xFFFFFFFF);
                    oh.addView(tg);
                }
                ot.addView(oh);
            }
            String text = q.options.get(i).replaceFirst("^[A-F][.)]\\s*", "");
            Ui.add(ot, Ui.text(c, text, 14, R.color.text, false), sel || tag != null ? 4 : 4);
            LinearLayout.LayoutParams otl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            otl.setMarginStart(Ui.dp(c, 12));
            opt.addView(ot, otl);
            opt.setOnClickListener(v -> { if (!submitted) { answers.put(q.id, idx); rerender(); } });
            Ui.add(root, opt, 10);
        }

        if (submitted) {
            LinearLayout exp = Ui.vertical(c);
            exp.setBackground(Ui.rounded(c, R.color.m3_surface_container, 22, 0));
            exp.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            LinearLayout eh = Ui.horizontal(c);
            eh.addView(Ui.iconBox(c, R.drawable.ic_brain, R.color.brand_dark, android.R.color.white, 34, 18, 10));
            TextView et = Ui.text(c, "Giải thích chi tiết từ AI", 16, R.color.text, true);
            LinearLayout.LayoutParams etl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            etl.setMarginStart(Ui.dp(c, 10));
            eh.addView(et, etl);
            exp.addView(eh);
            LinearLayout eb = Ui.vertical(c);
            eb.setBackground(Ui.rounded(c, R.color.surface, 16, 0));
            eb.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            TextView ex = Ui.text(c, q.explanation, 14, R.color.text, false);
            ex.setLineSpacing(0, 1.35f);
            eb.addView(ex);
            boolean right = chosen != null && chosen == q.correctIndex;
            Ui.add(eb, Ui.iconText(c, right ? R.drawable.ic_check_circle : R.drawable.ic_alert,
                    right ? "Đáp án chính xác! Bạn đã nắm vững phần này." : "Đáp án đúng: " + (q.correctIndex < q.options.size() ? q.options.get(q.correctIndex) : ""),
                    12, Ui.color(c, right ? R.color.brand_dark : R.color.rose), true), 10);
            Ui.add(exp, eb, 12);
            Ui.add(root, exp, 14);
        }

        LinearLayout nav = Ui.horizontal(c);
        LinearLayout prev = Ui.iconText(c, R.drawable.ic_chevron_left, "Câu trước", 14, Ui.color(c, R.color.text), false);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 16, 0));
        prev.setMinimumHeight(Ui.dp(c, 48));
        prev.setAlpha(current == 0 ? 0.5f : 1f);
        prev.setOnClickListener(v -> { if (current > 0) { current--; rerender(); } });
        Ui.addWeighted(nav, prev, 0);
        LinearLayout next = Ui.iconText(c, 0, "Câu sau", 14, Ui.color(c, R.color.text), false);
        next.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, Ui.color(c, R.color.text)));
        next.setGravity(Gravity.CENTER);
        next.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 16, 0));
        next.setMinimumHeight(Ui.dp(c, 48));
        next.setAlpha(current == qs.size() - 1 ? 0.5f : 1f);
        next.setOnClickListener(v -> { if (current < qs.size() - 1) { current++; rerender(); } });
        Ui.addWeighted(nav, next, 12);
        Ui.add(root, nav, 18);

        LinearLayout submit = Ui.iconText(c, R.drawable.ic_target, submitted ? "Xem kết quả phân tích lỗ hổng" : "Nộp bài & Xem kết quả phân tích lỗ hổng", 15, 0xFFFFFFFF, true);
        submit.setGravity(Gravity.CENTER);
        submit.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF005C55));
        submit.setMinimumHeight(Ui.dp(c, 54));
        submit.setOnClickListener(v -> { if (submitted) { showResult = true; rerender(); } else submit(); });
        Ui.add(root, submit, 12);

        if (submitted) renderPreview(root);
        updateTimer();
    }

    /** "Xem trước kết quả chẩn đoán" card under a submitted quiz */
    private void renderPreview(LinearLayout root) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        LinearLayout h = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        h.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        TextView ht = Ui.text(c, "Xem trước kết quả chẩn đoán", 16, R.color.text, true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 8));
        h.addView(ht, hl);
        in.addView(h);
        LinearLayout box = Ui.horizontal(c);
        box.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        box.addView(ring(c, resultScore, 84, 7, 22));
        LinearLayout tx = Ui.vertical(c);
        tx.addView(Ui.text(c, "Bạn trả lời đúng " + resultCorrect + "/" + resultTotal + " câu", 16, R.color.text, true));
        if (resultFeedback != null && !resultFeedback.isEmpty()) {
            TextView fb = Ui.text(c, resultFeedback, 12, R.color.text_secondary, false);
            fb.setMaxLines(2);
            fb.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Ui.add(tx, fb, 4);
        }
        LinearLayout cnt = Ui.horizontal(c);
        cnt.addView(Ui.iconText(c, R.drawable.ic_check_circle, resultCorrect + " Đúng", 12, Ui.color(c, R.color.brand_dark), false));
        Ui.addWrap(cnt, Ui.iconText(c, R.drawable.ic_close, (resultTotal - resultCorrect) + " Sai", 12, Ui.color(c, R.color.rose), false), 12);
        Ui.add(tx, cnt, 6);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 14));
        box.addView(tx, txl);
        Ui.add(in, box, 12);
        if (resultCorrect < resultTotal) {
            LinearLayout gaps = Ui.iconText(c, R.drawable.ic_search, "Tìm lỗ hổng gốc của các câu sai", 14, Ui.color(c, R.color.m3_on_tertiary_fixed), true);
            gaps.setGravity(Gravity.CENTER);
            gaps.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 16, 0));
            gaps.setMinimumHeight(Ui.dp(c, 48));
            gaps.setOnClickListener(v -> state.navigate(Screen.GAPS));
            Ui.add(in, gaps, 12);
        }
        Ui.add(root, card, 16);
    }

    /** Score ring with "8.5 / 10" in the middle */
    private View ring(Context c, int scorePct, int sizeDp, float strokeDp, float valueSp) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.addView(new com.studymind.nativeapp.ui.ScoreRing(c, scorePct / 100f, Ui.color(c, R.color.m3_surface_high), 0xFF005C55, strokeDp));
        LinearLayout mid = Ui.vertical(c);
        mid.setGravity(Gravity.CENTER);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(String.format(java.util.Locale.US, "%.1f", scorePct / 10f));
        int e = sb.length();
        sb.append(" /10");
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.5f), e, sb.length(), 0);
        mid.addView(Ui.text(c, sb, valueSp, R.color.brand_dark, true));
        f.addView(mid, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return f;
    }

    private View metaRow(Context c, String label, String value, int valueColor) {
        LinearLayout r = Ui.horizontal(c);
        Ui.addWeighted(r, Ui.text(c, label, 12, R.color.text_muted, false), 0);
        Ui.addWrap(r, Ui.text(c, value, 12, valueColor, true), 8);
        return r;
    }

    private View rule(Context c, int top) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        lp.topMargin = Ui.dp(c, top);
        v.setLayoutParams(lp);
        return v;
    }

    private void reset(boolean announce) {
        answers.clear();
        flagged.clear();
        showResult = false;
        submitted = false;
        current = 0;
        startedAt = SystemClock.elapsedRealtime();
        if (announce) {
            toast("Đã reset bài làm trắc nghiệm thành công!");
            rerender();
        }
    }

    private void updateTimer() {
        if (timerView == null || !isAdded()) return;
        int left = (int) Math.max(0, LIMIT_SECONDS - (SystemClock.elapsedRealtime() - startedAt) / 1000);
        timerView.setText(String.format("%02d:%02d", left / 60, left % 60));
        if (left > 0 && !submitted) Async.mainDelayed(tick, 1000);
    }

    private void regenerate() {
        if (generating) return;
        generating = true;
        rerender();
        state.regenerateQuiz(ok -> { generating = false; if (isAdded()) rerender(); });
    }

    private void submit() {
        if (answers.isEmpty()) { toast("Hãy trả lời ít nhất một câu trước khi nộp bài."); return; }
        long seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000;
        state.submitQuiz(new HashMap<>(answers), (score, correct, total, feedback) -> {
            submitted = true;
            showResult = true;
            resultScore = score;
            resultCorrect = correct;
            resultTotal = total;
            resultFeedback = feedback;
            resultSeconds = seconds;
            toast("Nộp bài thành công! Điểm của bạn: " + score + "/100");
            if (!isAdded()) return;
            rerender();
            scroll.post(() -> scroll.scrollTo(0, 0));
        });
    }

    private static String grade(int pct) {
        return pct >= 90 ? "XUẤT SẮC" : pct >= 80 ? "GIỎI" : pct >= 65 ? "KHÁ" : pct >= 50 ? "TRUNG BÌNH" : "CẦN CỐ GẮNG";
    }

    /** Stitch "Kết quả phân tích AI" */
    private void renderResult(LinearLayout root, ActiveDocument a, AIQuiz quiz) {
        Context c = requireContext();
        List<QuizQuestion> qs = quiz.questions;
        List<QuizQuestion> wrong = new ArrayList<>();
        int skipped = 0;
        for (QuizQuestion q : qs) {
            Integer ch = answers.get(q.id);
            if (ch == null) skipped++;
            else if (ch != q.correctIndex) wrong.add(q);
        }

        LinearLayout top = Ui.horizontal(c);
        LinearLayout subj = Ui.iconText(c, R.drawable.ic_shield_check, (quiz.subject.isEmpty() ? "Luyện tập" : quiz.subject) + " • " + a.document.title, 13, Ui.color(c, R.color.text_secondary), false);
        ((TextView) subj.getChildAt(1)).setMaxLines(2);
        Ui.addWeighted(top, subj, 0);
        LinearLayout back = Ui.iconText(c, R.drawable.ic_dashboard, "Về trang chủ", 13, Ui.color(c, R.color.text), false);
        back.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        back.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        back.setOnClickListener(v -> state.navigate(Screen.DASHBOARD));
        Ui.addWrap(top, back, 8);
        root.addView(top);

        // ---- Score card ----
        LinearLayout sc = Ui.vertical(c);
        sc.setGravity(Gravity.CENTER_HORIZONTAL);
        sc.setBackground(Ui.gradient(c, 28, 0xFFFFFFFF, 0xFFF3FBF9));
        sc.setElevation(Ui.dp(c, 4));
        sc.setPadding(Ui.dp(c, 18), Ui.dp(c, 22), Ui.dp(c, 18), Ui.dp(c, 18));
        android.widget.FrameLayout ringBox = new android.widget.FrameLayout(c);
        ringBox.addView(new com.studymind.nativeapp.ui.ScoreRing(c, resultScore / 100f, Ui.color(c, R.color.m3_surface_high), 0xFF005C55, 14));
        LinearLayout mid = Ui.vertical(c);
        mid.setGravity(Gravity.CENTER);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(String.format(java.util.Locale.US, "%.1f", resultScore / 10f));
        int e = sb.length();
        sb.append("/10");
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.45f), e, sb.length(), 0);
        mid.addView(Ui.text(c, sb, 34, R.color.brand_dark, true));
        mid.addView(Ui.text(c, grade(resultScore), 13, R.color.brand_dark, true));
        ringBox.addView(mid, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sc.addView(ringBox, new LinearLayout.LayoutParams(Ui.dp(c, 170), Ui.dp(c, 170)));
        TextView res = Ui.text(c, "Bạn trả lời đúng " + resultCorrect + " / " + resultTotal + " câu", 20, R.color.text, true);
        res.setGravity(Gravity.CENTER);
        Ui.add(sc, res, 16);
        String time = String.format(java.util.Locale.US, "%02d:%02d / %02d:00", resultSeconds / 60, resultSeconds % 60, LIMIT_SECONDS / 60);
        TextView acc = Ui.text(c, "Đạt độ chính xác " + resultScore + "% • Thời gian: " + time, 14, R.color.text_secondary, false);
        acc.setGravity(Gravity.CENTER);
        Ui.add(sc, acc, 6);
        int streak = com.studymind.nativeapp.data.Gamification.load().streak;
        LinearLayout st = Ui.iconText(c, R.drawable.ic_flame, "Chuỗi " + streak + " ngày • Giữ vững phong độ!", 13, Ui.color(c, R.color.m3_on_tertiary_fixed), true);
        st.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        st.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
        Ui.addInline(sc, st, 12);
        LinearLayout counts = Ui.horizontal(c);
        counts.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
        counts.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12));
        countCol(counts, resultCorrect, "Chính xác", R.color.brand_dark);
        countCol(counts, wrong.size(), "Cần sửa", R.color.rose);
        countCol(counts, skipped, "Bỏ qua", R.color.slate_400);
        Ui.add(sc, counts, 16);
        Ui.add(root, sc, 16);

        if (!wrong.isEmpty()) {
            LinearLayout gaps = Ui.horizontal(c);
            gaps.setBackground(Ui.gradient(c, 18, 0xFF945D00, 0xFF0F766E));
            gaps.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12));
            gaps.addView(Ui.icon(c, R.drawable.ic_search, 22, 0xFFFFFFFF));
            TextView gt = Ui.text(c, "Tìm lỗ hổng gốc của " + wrong.size() + " câu sai", 15, R.color.text, true);
            gt.setTextColor(0xFFFFFFFF);
            gt.setGravity(Gravity.CENTER);
            Ui.addWeighted(gaps, gt, 8);
            TextView ds = Ui.text(c, "AI\nDeepScan", 11, R.color.text, false);
            ds.setTextColor(0xFFFFFFFF);
            ds.setGravity(Gravity.CENTER);
            ds.setBackground(Ui.gradient(c, 999, 0x33000000, 0x33000000));
            ds.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
            gaps.addView(ds);
            gaps.setOnClickListener(v -> state.navigate(Screen.GAPS));
            Ui.add(root, gaps, 4);
        }

        LinearLayout acts = Ui.horizontal(c);
        LinearLayout see = Ui.iconText(c, R.drawable.ic_eye, "Xem lời giải", 15, Ui.color(c, R.color.text_secondary), false);
        see.setGravity(Gravity.CENTER);
        see.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 14, 0));
        see.setMinimumHeight(Ui.dp(c, 50));
        see.setOnClickListener(v -> { showResult = false; current = 0; rerender(); });
        Ui.addWeighted(acts, see, 0);
        LinearLayout redo = Ui.iconText(c, R.drawable.ic_reset, "Làm lại đề", 15, Ui.color(c, R.color.text), false);
        redo.setGravity(Gravity.CENTER);
        redo.setBackground(Ui.rounded(c, R.color.m3_surface_container, 14, 0));
        redo.setMinimumHeight(Ui.dp(c, 50));
        redo.setOnClickListener(v -> reset(true));
        Ui.addWeighted(acts, redo, 12);
        Ui.add(root, acts, 14);

        // ---- Diagnosis of wrong answers ----
        if (!wrong.isEmpty()) {
            MaterialCardView dc = Ui.card(c);
            LinearLayout di = Ui.inner(dc);
            LinearLayout dh = Ui.horizontal(c);
            dh.setGravity(Gravity.TOP);
            dh.addView(Ui.iconBox(c, R.drawable.ic_brain, R.color.teal_200, R.color.brand_dark, 34, 18, 10));
            LinearLayout dt = Ui.vertical(c);
            dt.addView(Ui.text(c, "Chẩn đoán từ StudyMind AI", 17, R.color.text, true));
            dt.addView(Ui.text(c, "Phát hiện từ " + wrong.size() + " phương án chọn sai", 13, R.color.text_secondary, false));
            LinearLayout.LayoutParams dtl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            dtl.setMarginStart(Ui.dp(c, 10));
            dh.addView(dt, dtl);
            dh.addView(Ui.pill(c, wrong.size() + " Điểm nghẽn", R.color.m3_secondary_container, R.color.m3_on_secondary_container));
            di.addView(dh);
            for (QuizQuestion q : wrong) {
                int qn = qs.indexOf(q) + 1;
                LinearLayout item = Ui.vertical(c);
                item.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
                item.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
                LinearLayout ih = Ui.horizontal(c);
                View dot = new View(c);
                dot.setBackground(Ui.rounded(c, R.color.rose, 999, 0));
                ih.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
                Ui.addWeighted(ih, Ui.text(c, "Câu #" + qn, 12, R.color.text_secondary, false), 6);
                ih.addView(Ui.pill(c, "Cần ôn lại", R.color.m3_tertiary_fixed, R.color.m3_on_tertiary_fixed));
                item.addView(ih);
                Ui.add(item, Ui.text(c, q.questionText, 16, R.color.text, true), 8);
                Ui.add(item, Ui.text(c, q.explanation, 13, R.color.text_secondary, false), 6);
                String ans = q.correctIndex < q.options.size() ? q.options.get(q.correctIndex) : "";
                Ui.add(item, Ui.iconText(c, R.drawable.ic_book, "Đáp án đúng: " + ans, 12, Ui.color(c, R.color.brand_dark), true), 8);
                Ui.add(di, item, 12);
            }
            LinearLayout mk = Ui.iconText(c, R.drawable.ic_layers, "Tạo " + wrong.size() + " Flashcard ôn tập ngay cho " + wrong.size() + " phần này", 15, 0xFFFFFFFF, true);
            mk.setGravity(Gravity.CENTER);
            mk.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF005C55));
            mk.setMinimumHeight(Ui.dp(c, 52));
            mk.setOnClickListener(v -> {
                for (QuizQuestion q : wrong) {
                    String ans = q.correctIndex < q.options.size() ? q.options.get(q.correctIndex) : "";
                    state.addFlashcard(q.questionText, ans + (q.explanation != null && !q.explanation.isEmpty() ? "\n" + q.explanation : ""), "hard");
                }
                toast("Đã tạo " + wrong.size() + " thẻ ghi nhớ từ các câu sai!");
                state.navigate(Screen.FLASHCARD);
            });
            Ui.add(di, mk, 14);
            Ui.add(root, dc, 16);
        }

        LinearLayout sr = Ui.horizontal(c);
        sr.setBackground(Ui.rounded(c, R.color.m3_surface_container, 22, 0));
        sr.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
        sr.addView(Ui.iconBox(c, R.drawable.ic_award, R.color.surface, R.color.m3_tertiary, 48, 24, 14));
        LinearLayout srt = Ui.vertical(c);
        srt.addView(Ui.text(c, "Ôn tập ngắt quãng (Spaced Repetition)", 16, R.color.text, true));
        srt.addView(Ui.text(c, "Ôn lại các thẻ ghi nhớ vào tối mai để củng cố trí nhớ dài hạn.", 13, R.color.text_secondary, false));
        LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        srl.setMarginStart(Ui.dp(c, 14));
        sr.addView(srt, srl);
        sr.setOnClickListener(v -> state.navigate(Screen.FLASHCARD));
        Ui.add(root, sr, 16);
    }

    private void countCol(LinearLayout row, int n, String label, int color) {
        Context c = requireContext();
        LinearLayout col = Ui.vertical(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout top = Ui.horizontal(c);
        View d = new View(c);
        d.setBackground(Ui.rounded(c, color, 999, 0));
        top.addView(d, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        Ui.addWrap(top, Ui.text(c, String.valueOf(n), 22, R.color.text, true), 4);
        col.addView(top);
        col.addView(Ui.text(c, label, 13, R.color.text_secondary, false));
        Ui.addWeighted(row, col, 0);
    }

    private void addDialog() {
        Context c = requireContext();
        Modal.show(c, 0, 0, "Thêm câu hỏi trắc nghiệm mới", null, (body, dialog) -> {
            body.addView(Ui.label(c, "Nội dung câu hỏi:"));
            EditText text = Ui.field(c, "VD: Ty thể tổng hợp ATP bằng enzyme nào?", true);
            text.setMinLines(2);
            Ui.add(body, text, 6);
            String[] letters = {"A", "B", "C", "D"};
            String[] hints = {"VD: ATP Synthase", "VD: Polymerase", "VD: Ribozyme", "VD: Ligase"};
            List<EditText> opts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Ui.add(body, Ui.text(c, "Phương án " + letters[i] + ":", 11, R.color.text_secondary, true), 10);
                EditText e = Ui.field(c, hints[i], false);
                opts.add(Ui.add(body, e, 4));
            }
            Ui.add(body, Ui.label(c, "Đáp án đúng:"), 12);
            int[] correct = {0};
            Ui.add(body, Ui.select(c, new String[]{"A (Phương án 1)", "B (Phương án 2)", "C (Phương án 3)", "D (Phương án 4)"}, 0, i -> correct[0] = i), 6);
            Ui.add(body, Ui.label(c, "Giải thích chi tiết từ AI:"), 12);
            EditText exp = Ui.field(c, "VD: ATP Synthase là enzyme màng trong thực hiện tổng hợp ATP nhờ gradient proton.", false);
            Ui.add(body, exp, 6);
            LinearLayout btns = Ui.horizontal(c);
            LinearLayout save = Ui.pillButton(c, R.drawable.ic_check_circle, "Thêm câu hỏi", R.color.brand, android.R.color.white, 0, v -> {
                String q = text.getText().toString().trim();
                String a = opts.get(0).getText().toString().trim(), b = opts.get(1).getText().toString().trim();
                if (q.isEmpty() || a.isEmpty() || b.isEmpty()) return;
                String cc = opts.get(2).getText().toString().trim(), d = opts.get(3).getText().toString().trim();
                List<String> options = new ArrayList<>();
                options.add("A. " + a);
                options.add("B. " + b);
                options.add("C. " + (cc.isEmpty() ? "Khác" : cc));
                options.add("D. " + (d.isEmpty() ? "Khác" : d));
                String e = exp.getText().toString().trim();
                state.addQuizQuestion(q, options, correct[0], e.isEmpty() ? "Đáp án đã được kiểm chứng bởi AI." : e);
                dialog.dismiss();
            });
            save.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            Ui.addWeighted(btns, save, 0);
            Ui.addWeighted(btns, Ui.pillButton(c, 0, "Hủy", R.color.surface, R.color.text_secondary, R.color.border, v -> dialog.dismiss()), 8);
            Ui.add(body, btns, 18);
        });
    }

    /** "In đề thi / PDF": A4 exam with an answer key, printed or saved as PDF by the system print service */
    private void printExam(ActiveDocument a, AIQuiz quiz) {
        if (quiz == null || quiz.questions.isEmpty()) { toast("⚠️ Không có câu hỏi nào để xuất đề thi."); return; }
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>body{font-family:sans-serif;font-size:12pt;margin:24px}"
                + "h1{font-size:18pt;margin:0 0 4px}.meta{color:#475569;margin-bottom:18px}.q{margin:14px 0;page-break-inside:avoid}"
                + ".o{margin:4px 0 0 18px}.key{page-break-before:always}</style></head><body>");
        h.append("<h1>").append(Html.escapeHtml(a.document.title)).append("</h1><div class='meta'>")
                .append(Html.escapeHtml(quiz.subject)).append(" • Thời gian: ").append(quiz.timeLimitMinutes).append(" phút • Họ tên: ..............................</div>");
        for (int i = 0; i < quiz.questions.size(); i++) {
            QuizQuestion q = quiz.questions.get(i);
            h.append("<div class='q'><b>Câu ").append(i + 1).append(".</b> ").append(Html.escapeHtml(q.questionText));
            for (String o : q.options) h.append("<div class='o'>").append(Html.escapeHtml(o)).append("</div>");
            h.append("</div>");
        }
        h.append("<div class='key'><h1>Đáp án & giải thích</h1>");
        for (int i = 0; i < quiz.questions.size(); i++) {
            QuizQuestion q = quiz.questions.get(i);
            String ans = q.correctIndex < q.options.size() ? q.options.get(q.correctIndex) : "";
            h.append("<div class='q'><b>Câu ").append(i + 1).append(":</b> ").append(Html.escapeHtml(ans))
                    .append("<div class='o'>").append(Html.escapeHtml(q.explanation)).append("</div></div>");
        }
        h.append("</div><p style='color:#94a3b8;margin-top:24px'>Tạo bởi StudyMind AI</p></body></html>");

        // An off-screen WebView only lays out the generated page for the print service
        printView = new WebView(requireContext());
        printView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) requireContext().getSystemService(Context.PRINT_SERVICE);
                pm.print("StudyMind - " + a.document.title, view.createPrintDocumentAdapter("De_thi_" + a.document.title),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
            }
        });
        printView.loadDataWithBaseURL(null, h.toString(), "text/html", "utf-8", null);
        toast("🖨️ Đang mở trang in đề thi A4 / Lưu file PDF...");
    }
}
