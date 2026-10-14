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

    @Override
    protected void render(LinearLayout root) {
        Async.cancel(tick);
        if (!requireDocument(root, "làm bài trắc nghiệm")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        AIQuiz quiz = a.pack.quiz();
        String key = a.document.id + ":" + (quiz != null ? quiz.questions.size() + ":" + quiz.questions.get(0).id : "none");
        if (!key.equals(quizKey)) { quizKey = key; reset(false); }

        // ---- Title header ----
        LinearLayout title = Ui.horizontal(c);
        title.addView(Ui.icon(c, R.drawable.ic_quiz, 20, Ui.color(c, R.color.brand)));
        Ui.addWrap(title, Ui.text(c, "Luyện tập trắc nghiệm", 20, R.color.text, true), 8);
        root.addView(title);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Môn học: ");
        int st = sb.length();
        sb.append(quiz != null && !quiz.subject.isEmpty() ? quiz.subject : a.document.title);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.text)), st, sb.length(), 0);
        Ui.add(root, Ui.text(c, sb, 13, R.color.text_muted, false), 4);

        LinearLayout tools = Ui.horizontal(c);
        LinearLayout print = Ui.pillButton(c, R.drawable.ic_file, "In đề thi / PDF", R.color.surface, R.color.text_secondary, R.color.border, v -> printExam(a, quiz));
        ((ImageView) print.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        print.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        tools.addView(print);
        LinearLayout gen = Ui.pillButton(c, 0, generating ? "Đang tạo 10-12 câu..." : "Tạo 10-12 câu hỏi", R.color.brand, android.R.color.white, 0, v -> regenerate());
        gen.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        gen.setAlpha(generating ? 0.5f : 1f);
        Ui.addWrap(tools, gen, 8);
        LinearLayout retake = Ui.pillButton(c, 0, "Làm lại bài", R.color.surface, R.color.text_secondary, R.color.border, v -> reset(true));
        retake.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        Ui.addWrap(tools, retake, 8);
        LinearLayout add = Ui.pillButton(c, 0, "Thêm câu hỏi", R.color.surface, R.color.text_secondary, R.color.border, v -> addDialog());
        add.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        Ui.addWrap(tools, add, 8);
        HorizontalScrollView ts = new HorizontalScrollView(c);
        ts.setHorizontalScrollBarEnabled(false);
        ts.addView(tools);
        Ui.add(root, ts, 16);

        if (quiz == null) {
            root.addView(Ui.empty(c, "Chưa có đề trắc nghiệm", "Bấm \"Tạo 10-12 câu hỏi\" để AI soạn đề từ tài liệu.", null, null));
            return;
        }
        List<QuizQuestion> qs = quiz.questions;
        if (current >= qs.size()) current = 0;
        QuizQuestion q = qs.get(current);

        // ---- Question box ----
        MaterialCardView box = Ui.card(c);
        LinearLayout bi = Ui.inner(box);
        bi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout bar = Ui.horizontal(c);
        TextView qt = Ui.text(c, quiz.title.isEmpty() ? a.document.title : quiz.title, 12, R.color.text_secondary, true);
        qt.setMaxLines(2);
        Ui.addWeighted(bar, qt, 0);
        LinearLayout timer = Ui.iconText(c, R.drawable.ic_timer, "", 12, Ui.color(c, R.color.rose), true);
        timer.setBackground(Ui.rounded(c, R.color.rose_50, 999, R.color.rose_soft));
        timer.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        timerView = (TextView) timer.getChildAt(1);
        Ui.addWrap(bar, timer, 8);
        bi.addView(bar);
        bi.addView(rule(c, 14));

        Ui.add(bi, Ui.text(c, "Câu hỏi " + (current + 1) + ": " + q.questionText, 16, R.color.text, true), 18);
        Integer chosen = answers.get(q.id);
        for (int i = 0; i < q.options.size(); i++) {
            int idx = i;
            boolean sel = chosen != null && chosen == i, correct = q.correctIndex == i;
            int bg = R.color.surface, stroke = R.color.border, fg = R.color.text;
            String mark = null;
            int markBg = 0;
            if (submitted) {
                if (correct) { bg = R.color.emerald_50; stroke = R.color.emerald; fg = R.color.emerald_700; mark = "✓"; markBg = R.color.emerald; }
                else if (sel) { bg = R.color.rose_50; stroke = R.color.rose; fg = R.color.rose; mark = "✕"; markBg = R.color.rose; }
            } else if (sel) { bg = R.color.teal_50; stroke = R.color.brand; fg = R.color.brand; mark = "✓"; markBg = R.color.brand; }
            LinearLayout opt = Ui.horizontal(c);
            android.graphics.drawable.GradientDrawable ob = Ui.rounded(c, bg, 12, 0);
            ob.setStroke(Ui.dp(c, 2), Ui.color(c, stroke));
            opt.setBackground(ob);
            opt.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            Ui.addWeighted(opt, Ui.text(c, q.options.get(i), 13, fg, sel || (submitted && correct)), 0);
            if (mark != null) {
                TextView m = Ui.text(c, mark, 11, android.R.color.white, true);
                m.setGravity(Gravity.CENTER);
                m.setBackground(Ui.rounded(c, markBg, 999, 0));
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
                ml.setMarginStart(Ui.dp(c, 8));
                opt.addView(m, ml);
            }
            opt.setOnClickListener(v -> { if (!submitted) { answers.put(q.id, idx); rerender(); } });
            Ui.add(bi, opt, 12);
        }
        if (submitted) {
            LinearLayout exp = Ui.vertical(c);
            exp.setBackground(Ui.rounded(c, R.color.teal_50, 12, R.color.brand_soft));
            exp.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            exp.addView(Ui.iconText(c, R.drawable.ic_sparkles, "Giải thích chi tiết từ AI:", 12, Ui.color(c, R.color.brand), true));
            Ui.add(exp, Ui.text(c, q.explanation, 12, R.color.teal_900, false), 6);
            Ui.add(bi, exp, 16);
        }
        bi.addView(rule(c, 18));
        LinearLayout nav = Ui.horizontal(c);
        TextView prev = Ui.text(c, "← Câu trước", 12, R.color.text_secondary, true);
        prev.setAlpha(current == 0 ? 0.3f : 1f);
        prev.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        prev.setOnClickListener(v -> { if (current > 0) { current--; rerender(); } });
        nav.addView(prev);
        nav.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView next = Ui.text(c, "Câu sau →", 12, R.color.brand, true);
        next.setAlpha(current == qs.size() - 1 ? 0.3f : 1f);
        next.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        next.setOnClickListener(v -> { if (current < qs.size() - 1) { current++; rerender(); } });
        nav.addView(next);
        Ui.add(bi, nav, 8);
        Ui.add(root, box, 16);

        // ---- Navigator ----
        LinearLayout navigator = Ui.section(root, "Tiến trình kiểm tra", null, null, 16);
        LinearLayout grid = Ui.vertical(c);
        LinearLayout row = null;
        for (int i = 0; i < qs.size(); i++) {
            if (i % 5 == 0) {
                row = Ui.horizontal(c);
                Ui.add(grid, row, i == 0 ? 0 : 10);
            }
            int idx = i;
            boolean answered = answers.containsKey(qs.get(i).id), cur = current == i;
            TextView n = Ui.text(c, String.valueOf(i + 1), 12, cur ? android.R.color.white : answered ? R.color.brand : R.color.text_secondary, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(Ui.rounded(c, cur ? R.color.brand : answered ? R.color.brand_soft : R.color.slate_100, 8, answered && !cur ? R.color.brand : 0));
            n.setOnClickListener(v -> { current = idx; rerender(); });
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f);
            if (i % 5 != 0) nl.setMarginStart(Ui.dp(c, 10));
            row.addView(n, nl);
        }
        if (row != null) for (int k = qs.size() % 5; k != 0 && k < 5; k++) {
            LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f);
            fill.setMarginStart(Ui.dp(c, 10));
            row.addView(new View(c), fill);
        }
        navigator.addView(grid);
        LinearLayout meta = Ui.vertical(c);
        meta.setBackground(Ui.rounded(c, R.color.slate_50, 12, R.color.border));
        meta.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        meta.addView(metaRow(c, "Đã trả lời:", answers.size() + " / " + qs.size() + " câu", R.color.text));
        Ui.add(meta, metaRow(c, "Tỷ lệ hoàn thành:", Math.round(answers.size() * 100f / Math.max(1, qs.size())) + "%", R.color.emerald), 8);
        Ui.add(navigator, meta, 16);
        LinearLayout submit = Ui.pillButton(c, 0, "Nộp bài và xem kết quả", R.color.brand, android.R.color.white, 0, v -> submit());
        submit.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        submit.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 14));
        submit.setElevation(Ui.dp(c, 3));
        Ui.add(navigator, submit, 18);

        updateTimer();
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
        timerView.setText("Thời gian còn lại: " + String.format("%02d:%02d", left / 60, left % 60));
        if (left > 0 && !submitted) Async.mainDelayed(tick, 1000);
    }

    private void regenerate() {
        if (generating) return;
        generating = true;
        rerender();
        state.regenerateQuiz(ok -> { generating = false; if (isAdded()) rerender(); });
    }

    private void submit() {
        state.submitQuiz(new HashMap<>(answers), (score, correct, total, feedback) -> {
            submitted = true;
            toast("Nộp bài thành công! Điểm của bạn: " + score + "/100");
            if (!isAdded()) return;
            rerender();
            showResult(score, correct, total, feedback);
        });
    }

    /** Score summary modal */
    private void showResult(int score, int correct, int total, String feedback) {
        Context c = requireContext();
        Modal.show(c, 0, 0, "Kết quả bài làm", null, (body, dialog) -> {
            body.setGravity(Gravity.CENTER_HORIZONTAL);
            body.addView(Ui.iconBox(c, R.drawable.ic_award, R.color.emerald_soft, R.color.emerald, 64, 32, 999));
            TextView s = Ui.text(c, "Điểm số: " + score + "/100", 24, R.color.text, true);
            Ui.addInline(body, s, 12);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Bạn trả lời đúng ");
            int st = sb.length();
            sb.append(correct + "/" + total);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length(), 0);
            sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.brand)), st, sb.length(), 0);
            sb.append(" câu hỏi.");
            Ui.addInline(body, Ui.text(c, sb, 12, R.color.text_secondary, false), 6);
            if (feedback != null && !feedback.isEmpty()) {
                TextView fb = Ui.text(c, feedback, 12, R.color.text_muted, false);
                fb.setGravity(Gravity.CENTER);
                fb.setBackground(Ui.rounded(c, R.color.slate_50, 12, R.color.border));
                fb.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
                Ui.add(body, fb, 10);
            }
            if (correct < total) {
                LinearLayout gaps = Ui.pillButton(c, R.drawable.ic_network, "Tìm lỗ hổng gốc của các câu sai", R.color.rose_50, R.color.rose, R.color.rose_soft,
                        v -> { dialog.dismiss(); state.navigate(Screen.GAPS); });
                Ui.add(body, gaps, 16);
            }
            LinearLayout btns = Ui.horizontal(c);
            LinearLayout detail = Ui.pillButton(c, 0, "Xem chi tiết câu trả lời", R.color.brand, android.R.color.white, 0, v -> dialog.dismiss());
            detail.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            Ui.addWeighted(btns, detail, 0);
            Ui.addWeighted(btns, Ui.pillButton(c, 0, "Làm lại bài kiểm tra", R.color.surface, R.color.text_secondary, R.color.border,
                    v -> { dialog.dismiss(); reset(true); }), 8);
            Ui.add(body, btns, 12);
        });
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
