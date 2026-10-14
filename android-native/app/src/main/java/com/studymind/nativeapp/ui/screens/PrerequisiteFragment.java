package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.BridgeItem;
import com.studymind.nativeapp.data.model.Models.DiagnosticQuestion;
import com.studymind.nativeapp.data.model.Models.DiagnosticPreTest;
import com.studymind.nativeapp.data.model.Models.LearningStep;
import com.studymind.nativeapp.data.model.Models.Prerequisite;
import com.studymind.nativeapp.data.model.Models.PrerequisiteAnalysis;
import com.studymind.nativeapp.data.model.Models.PrerequisiteDependency;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** "Kiến thức Tiên quyết" — PrerequisiteView.tsx (mobile order: header, test, bottleneck, bridge, prerequisites, graph, path). */
public class PrerequisiteFragment extends BaseFragment {
    private static final String[] DEPTHS = {"Tóm lược nhanh", "Tiêu chuẩn", "Chuyên sâu"};
    private static final Map<String, String> DEPTH_LABEL = new HashMap<>();
    static {
        DEPTH_LABEL.put("quick", "Tóm lược nhanh");
        DEPTH_LABEL.put("standard", "Tiêu chuẩn");
        DEPTH_LABEL.put("deep", "Chuyên sâu");
    }

    private String depth;
    private boolean loading;
    private final Map<String, Integer> answers = new HashMap<>();
    private boolean submitted;
    private String testKey;
    private String highlightId;
    private final Map<String, View> anchors = new HashMap<>();

    @Override
    protected Object watchKey() { return state.activeDoc() + ":" + state.isPro(); }

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "phân tích kiến thức tiên quyết")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        PrerequisiteAnalysis data = a.pack.prerequisites();
        anchors.clear();
        if (depth == null) depth = DEPTH_LABEL.getOrDefault(data != null && data.analysisDepth != null ? data.analysisDepth : "standard", "Tiêu chuẩn");

        // Reset the test only when the questions change (re-analysis), not when the saved result updates
        String key = data == null ? "" : questionsKey(data.diagnosticPreTest);
        if (!key.equals(testKey)) {
            testKey = key;
            answers.clear();
            submitted = false;
        }

        if (data == null) {
            MaterialCardView card = Ui.card(c);
            LinearLayout in = Ui.inner(card);
            in.setGravity(Gravity.CENTER_HORIZONTAL);
            in.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 28));
            in.addView(Ui.icon(c, R.drawable.ic_compass, 40, Ui.color(c, R.color.brand)));
            TextView t = Ui.text(c, "Kiến thức Tiên quyết", 18, R.color.text, true);
            t.setGravity(Gravity.CENTER);
            Ui.add(in, t, 12);
            TextView m = Ui.text(c, "AI sẽ tìm những kiến thức nền mà \"" + a.document.title + "\" mặc định bạn đã biết, chỉ ra điểm nghẽn nhận thức, "
                    + "vạch lộ trình học và soạn bài test chẩn đoán để kiểm tra bạn đã sẵn sàng chưa.", 12, R.color.text_secondary, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(in, m, 8);
            Ui.addInline(in, controls(c, false), 16);
            Ui.add(root, card, 24);
            return;
        }

        // ---- Header ----
        MaterialCardView head = Ui.card(c);
        LinearLayout hi = Ui.inner(head);
        hi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout title = Ui.horizontal(c);
        title.setGravity(Gravity.TOP);
        title.addView(Ui.iconBox(c, R.drawable.ic_compass, R.color.brand, android.R.color.white, 40, 20, 12));
        LinearLayout tt = Ui.vertical(c);
        tt.addView(Ui.text(c, "Kiến thức Tiên quyết", 17, R.color.text, true));
        TextView sub = Ui.text(c, data.documentTitle.isEmpty() ? a.document.title : data.documentTitle, 12, R.color.text_muted, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tt.addView(sub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        title.addView(tt, tl);
        hi.addView(title);
        Ui.add(hi, controls(c, true), 14);

        int critical = 0, recommended = 0;
        for (Prerequisite p : data.prerequisites) if ("critical".equals(p.priority)) critical++; else recommended++;
        LinearLayout pills = Ui.horizontal(c);
        if (!data.subjectArea.isEmpty()) addPill(pills, data.subjectArea, R.color.teal_50, R.color.brand, R.color.brand_soft);
        if (!data.targetAudienceLevel.isEmpty()) addPill(pills, data.targetAudienceLevel, R.color.slate_100, R.color.text_secondary, R.color.border);
        if (data.analysisDepth != null) addPill(pills, "Mức: " + DEPTH_LABEL.getOrDefault(data.analysisDepth, data.analysisDepth), R.color.slate_100, R.color.text_secondary, R.color.border);
        addPill(pills, critical + " bắt buộc", R.color.rose_50, R.color.rose, R.color.rose_soft);
        addPill(pills, recommended + " nên biết", R.color.amber_50, R.color.amber_700, R.color.amber_soft);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pills);
        Ui.add(hi, hs, 14);
        if (!data.overallReadinessNote.isEmpty()) Ui.add(hi, Ui.text(c, data.overallReadinessNote, 12, R.color.text_secondary, false), 12);
        if (data.isFallback) {
            LinearLayout warn = Ui.horizontal(c);
            warn.setGravity(Gravity.TOP);
            warn.setBackground(Ui.rounded(c, R.color.amber_50, 8, R.color.amber_soft));
            warn.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            warn.addView(Ui.icon(c, R.drawable.ic_alert_tri, 16, 0xFF92400E));
            TextView w = Ui.text(c, "Đây là phân tích sơ bộ vì dịch vụ AI chưa phản hồi. Bấm \"Phân tích lại\" để có kết quả đầy đủ và bài test chẩn đoán.", 11, R.color.text, false);
            w.setTextColor(0xFF78350F);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wl.setMarginStart(Ui.dp(c, 8));
            warn.addView(w, wl);
            Ui.add(hi, warn, 12);
        }
        root.addView(head);

        // ---- Diagnostic test ----
        LinearLayout test = card(root, R.drawable.ic_clipboard, data.diagnosticPreTest.title.isEmpty() ? "Bài test chẩn đoán" : data.diagnosticPreTest.title, R.color.surface, R.color.border);
        renderTest(test, data);

        // ---- Cognitive bottleneck ----
        if (data.cognitiveBottleneck != null && !data.cognitiveBottleneck.concept.isEmpty()) {
            LinearLayout b = card(root, R.drawable.ic_target, "Điểm nghẽn nhận thức", R.color.rose_50, R.color.rose_soft);
            TextView concept = Ui.text(c, data.cognitiveBottleneck.concept, 14, R.color.text, true);
            concept.setTextColor(0xFF881337);
            b.addView(concept);
            if (!data.cognitiveBottleneck.description.isEmpty()) Ui.add(b, Ui.text(c, data.cognitiveBottleneck.description, 12, R.color.text_secondary, false), 6);
            if (!data.cognitiveBottleneck.advice.isEmpty()) {
                SpannableStringBuilder sb = new SpannableStringBuilder("Lời khuyên: ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                sb.append(data.cognitiveBottleneck.advice);
                TextView adv = Ui.text(c, sb, 12, R.color.text, false);
                adv.setTextColor(0xFF881337);
                adv.setBackground(Ui.rounded(c, R.color.surface, 8, R.color.rose_soft));
                adv.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
                Ui.add(b, adv, 8);
            }
        }

        // ---- Bridge summary ----
        if (!data.quickBridgeSummary.isEmpty()) {
            LinearLayout br = card(root, R.drawable.ic_zap, "Cầu nối kiến thức — đọc trong 2 phút", R.color.surface, R.color.border);
            for (BridgeItem b : data.quickBridgeSummary) {
                boolean hl = b.prerequisiteId != null && b.prerequisiteId.equals(highlightId);
                LinearLayout item = Ui.vertical(c);
                item.setBackground(Ui.rounded(c, hl ? R.color.amber_50 : R.color.slate_50, 12, hl ? R.color.amber : R.color.slate_100));
                item.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
                item.addView(Ui.text(c, b.concept, 12, R.color.text, true));
                Ui.add(item, Ui.text(c, b.quickReview, 12, R.color.text_secondary, false), 4);
                Ui.add(br, item, br.getChildCount() > 0 ? 10 : 0);
                if (b.prerequisiteId != null) anchors.put("bridge-" + b.prerequisiteId, item);
            }
        }

        // ---- Prerequisites ----
        LinearLayout pre = card(root, R.drawable.ic_book, "Kiến thức nền cần có", R.color.surface, R.color.border);
        if (data.prerequisites.isEmpty()) pre.addView(Ui.text(c, "Chưa xác định được kiến thức tiên quyết.", 12, R.color.text_muted, false));
        for (Prerequisite p : data.prerequisites) {
            boolean crit = "critical".equals(p.priority);
            LinearLayout item = Ui.vertical(c);
            item.setBackground(leftEdge(c, crit ? 0xFFF43F5E : 0xFFFBBF24, crit ? R.color.rose_soft : R.color.amber_soft, p.id.equals(highlightId)));
            item.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            LinearLayout line = Ui.horizontal(c);
            line.addView(Ui.text(c, p.concept, 14, R.color.text, true));
            Ui.addWrap(line, Ui.pill(c, crit ? "Bắt buộc" : "Nên biết", crit ? R.color.rose_soft : R.color.amber_soft, crit ? R.color.rose : R.color.amber_700), 6);
            if (!p.level.isEmpty()) Ui.addWrap(line, Ui.pill(c, p.level, R.color.slate_100, R.color.text_secondary), 6);
            HorizontalScrollView ls = new HorizontalScrollView(c);
            ls.setHorizontalScrollBarEnabled(false);
            ls.addView(line);
            item.addView(ls);
            if (!p.whyNeeded.isEmpty()) {
                SpannableStringBuilder sb = new SpannableStringBuilder("Vì sao cần: ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                sb.append(p.whyNeeded);
                Ui.add(item, Ui.text(c, sb, 12, R.color.text_secondary, false), 6);
            }
            if (!p.consequenceIfMissing.isEmpty()) {
                LinearLayout cons = Ui.horizontal(c);
                cons.setGravity(Gravity.TOP);
                cons.addView(Ui.icon(c, R.drawable.ic_alert_tri, 14, 0xFFF59E0B));
                TextView ct = Ui.text(c, p.consequenceIfMissing, 12, R.color.text_muted, false);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cl.setMarginStart(Ui.dp(c, 6));
                cons.addView(ct, cl);
                Ui.add(item, cons, 4);
            }
            Ui.add(pre, item, pre.getChildCount() > 0 ? 12 : 0);
            anchors.put("pre-" + p.id, item);
        }

        // ---- Dependency graph ----
        if (!data.dependencyGraph.isEmpty()) {
            LinearLayout g = card(root, R.drawable.ic_fork, "Đồ thị phụ thuộc", R.color.surface, R.color.border);
            LinearLayout legend = Ui.horizontal(c);
            legend.addView(legendDot(c, 0xFFFCD34D, "Nền tảng"));
            Ui.addWrap(legend, legendDot(c, 0xFF5EEAD4, "Trong bài"), 12);
            Ui.addWrap(legend, legendDot(c, 0xFFA5B4FC, "Ứng dụng"), 12);
            g.addView(legend);
            for (PrerequisiteDependency d : data.dependencyGraph) {
                LinearLayout row = Ui.horizontal(c);
                row.addView(nodeChip(c, d.from, d.fromType));
                TextView rel = Ui.text(c, " " + d.relation + " → ", 11, R.color.text_muted, false);
                rel.setTypeface(null, Typeface.ITALIC);
                row.addView(rel);
                row.addView(nodeChip(c, d.to, d.toType));
                HorizontalScrollView rs = new HorizontalScrollView(c);
                rs.setHorizontalScrollBarEnabled(false);
                rs.addView(row);
                Ui.add(g, rs, 10);
            }
        }

        // ---- Learning path timeline ----
        if (!data.learningPath.isEmpty()) {
            LinearLayout lp = card(root, R.drawable.ic_route, "Lộ trình học đề xuất", R.color.surface, R.color.border);
            for (LearningStep s : data.learningPath) {
                LinearLayout row = Ui.horizontal(c);
                row.setGravity(Gravity.TOP);
                TextView num = Ui.text(c, String.valueOf(s.step), 12, android.R.color.white, true);
                num.setGravity(Gravity.CENTER);
                GradientDrawable circle = Ui.rounded(c, R.color.brand, 999, 0);
                circle.setStroke(Ui.dp(c, 2), Ui.color(c, R.color.surface));
                num.setBackground(circle);
                row.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
                LinearLayout body = Ui.vertical(c);
                LinearLayout head2 = Ui.horizontal(c);
                head2.addView(Ui.text(c, s.title, 12, R.color.text, true));
                String[] badge = s.type.equals("prerequisite") ? new String[]{"Nền tảng"} : s.type.equals("advanced_application") ? new String[]{"Vận dụng"} : new String[]{"Trọng tâm"};
                int bg = s.type.equals("prerequisite") ? R.color.amber_soft : s.type.equals("advanced_application") ? R.color.indigo_50 : R.color.brand_soft;
                int fg = s.type.equals("prerequisite") ? R.color.amber_700 : s.type.equals("advanced_application") ? R.color.indigo : R.color.brand;
                Ui.addWrap(head2, Ui.pill(c, badge[0], bg, fg), 6);
                body.addView(head2);
                if (!s.description.isEmpty()) Ui.add(body, Ui.text(c, s.description, 12, R.color.text_muted, false), 2);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                bl.setMarginStart(Ui.dp(c, 12));
                bl.topMargin = Ui.dp(c, 3);
                row.addView(body, bl);
                Ui.add(lp, row, lp.getChildCount() > 0 ? 16 : 0);
            }
        }
    }

    private View controls(Context c, boolean fill) {
        LinearLayout row = Ui.horizontal(c);
        String[] labels = new String[DEPTHS.length];
        int sel = 1;
        for (int i = 0; i < DEPTHS.length; i++) {
            labels[i] = DEPTHS[i] + (DEPTHS[i].equals("Chuyên sâu") && !state.isPro() ? " 🔒 Pro" : "");
            if (DEPTHS[i].equals(depth)) sel = i;
        }
        android.widget.Spinner sp = Ui.select(c, labels, sel, i -> {
            if (DEPTHS[i].equals("Chuyên sâu") && !state.isPro()) {
                state.openUpgrade("Phân tích tiên quyết \"Chuyên sâu\" dành cho thành viên StudyMind Pro.");
                rerender();
            } else {
                depth = DEPTHS[i];
            }
        });
        sp.setEnabled(!loading);
        if (fill) Ui.addWeighted(row, sp, 0); else row.addView(sp);
        LinearLayout run = Ui.pillButton(c, R.drawable.ic_refresh, loading ? "Đang phân tích..." : state.activeDoc().pack.prerequisites() != null ? "Phân tích lại" : "Phân tích ngay",
                R.color.brand, android.R.color.white, 0, v -> analyze());
        run.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        run.setAlpha(loading ? 0.6f : 1f);
        Ui.addWrap(row, run, 8);
        return row;
    }

    private void analyze() {
        if (loading) return;
        loading = true;
        rerender();
        state.analyzePrerequisites(depth, ok -> { loading = false; if (isAdded()) rerender(); });
    }

    // ---------- diagnostic test ----------

    private static String questionsKey(DiagnosticPreTest t) {
        StringBuilder sb = new StringBuilder();
        if (t != null) for (DiagnosticQuestion q : t.questions) sb.append(q.id).append(q.question).append('|');
        return sb.toString();
    }

    private String conceptOf(PrerequisiteAnalysis data, String id) {
        if (id == null) return null;
        for (Prerequisite p : data.prerequisites) if (p.id.equals(id)) return p.concept;
        return null;
    }

    private void renderTest(LinearLayout body, PrerequisiteAnalysis data) {
        Context c = requireContext();
        DiagnosticPreTest test = data.diagnosticPreTest;
        if (test.questions.isEmpty()) {
            TextView t = Ui.text(c, "Chưa có câu hỏi chẩn đoán. Bấm \"Phân tích lại\" để AI soạn bài test đầu vào.", 12, R.color.text_muted, false);
            t.setBackground(Ui.rounded(c, R.color.slate_50, 12, R.color.border));
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            body.addView(t);
            return;
        }
        if (!test.instructions.isEmpty()) body.addView(Ui.text(c, test.instructions, 12, R.color.text_secondary, false));

        int correct = 0;
        Set<String> weak = new LinkedHashSet<>();
        for (int qi = 0; qi < test.questions.size(); qi++) {
            DiagnosticQuestion q = test.questions.get(qi);
            Integer chosen = answers.get(q.id);
            boolean ok = chosen != null && chosen == q.correctIndex;
            if (ok) correct++;
            else if (q.testedPrerequisiteId != null) weak.add(q.testedPrerequisiteId);

            LinearLayout box = Ui.vertical(c);
            box.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
            box.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            box.addView(Ui.text(c, (qi + 1) + ". " + q.question, 12, R.color.text, true));
            for (int oi = 0; oi < q.options.size(); oi++) {
                int idx = oi;
                int bg = R.color.surface, stroke = R.color.border, fg = R.color.text_secondary;
                boolean bold = false;
                if (!submitted && chosen != null && chosen == oi) { bg = R.color.teal_50; stroke = R.color.brand; fg = R.color.brand; bold = true; }
                if (submitted && oi == q.correctIndex) { bg = R.color.emerald_50; stroke = R.color.emerald; fg = R.color.emerald_700; bold = true; }
                else if (submitted && chosen != null && chosen == oi) { bg = R.color.rose_50; stroke = R.color.rose; fg = R.color.rose; }
                else if (submitted) { fg = R.color.slate_400; }
                TextView opt = Ui.text(c, q.options.get(oi), 12, fg, bold);
                opt.setBackground(Ui.rounded(c, bg, 8, stroke));
                opt.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
                if (!submitted) opt.setOnClickListener(v -> { answers.put(q.id, idx); rerender(); });
                Ui.add(box, opt, 8);
            }
            if (submitted) {
                LinearLayout fb = Ui.vertical(c);
                fb.setBackground(Ui.rounded(c, ok ? R.color.emerald_50 : R.color.rose_50, 8, 0));
                fb.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
                String concept = conceptOf(data, q.testedPrerequisiteId);
                fb.addView(Ui.text(c, (ok ? "✓ Chính xác" : "✗ Chưa đúng") + (concept != null ? "  • Kiểm tra: " + concept : ""), 11, ok ? R.color.emerald_700 : R.color.rose, true));
                if (!q.explanation.isEmpty()) Ui.add(fb, Ui.text(c, q.explanation, 11, R.color.text_secondary, false), 2);
                if (!ok && q.testedPrerequisiteId != null) {
                    TextView rv = Ui.text(c, "Ôn nhanh kiến thức này →", 11, R.color.rose, true);
                    rv.setPaintFlags(rv.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                    rv.setOnClickListener(v -> review(q.testedPrerequisiteId));
                    Ui.add(fb, rv, 4);
                }
                Ui.add(box, fb, 10);
            }
            Ui.add(body, box, 12);
        }

        boolean all = answers.size() >= test.questions.size();
        if (!submitted) {
            LinearLayout grade = Ui.pillButton(c, 0, all ? "Chấm điểm" : "Trả lời đủ " + test.questions.size() + " câu để chấm điểm",
                    R.color.brand, android.R.color.white, 0, v -> {
                        if (!all) return;
                        submitted = true;
                        state.submitDiagnostic(new HashMap<>(answers));
                        rerender();
                    });
            grade.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
            grade.setAlpha(all ? 1f : 0.4f);
            Ui.add(body, grade, 14);
            return;
        }

        int score = Math.round(correct * 100f / test.questions.size());
        boolean passed = score >= test.passScore;
        LinearLayout res = Ui.vertical(c);
        res.setBackground(Ui.rounded(c, passed ? R.color.emerald_50 : R.color.amber_50, 12, passed ? R.color.emerald_soft : R.color.amber_soft));
        res.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
        LinearLayout top = Ui.horizontal(c);
        Ui.addWeighted(top, Ui.text(c, passed ? "Bạn đã sẵn sàng học bài này!" : "Nên ôn lại nền tảng trước", 14, passed ? R.color.emerald_700 : R.color.amber_700, true), 0);
        Ui.addWrap(top, Ui.text(c, score + "%", 18, passed ? R.color.emerald_700 : R.color.amber_700, true), 8);
        res.addView(top);
        Ui.add(res, Ui.text(c, "Đúng " + correct + "/" + test.questions.size() + " câu • Ngưỡng đạt " + test.passScore + "%", 11, R.color.text_muted, false), 6);
        if (!passed && !weak.isEmpty()) {
            LinearLayout chips = Ui.horizontal(c);
            for (String id : weak) {
                String concept = conceptOf(data, id);
                if (concept == null) continue;
                TextView chip = Ui.text(c, concept, 11, R.color.amber_700, true);
                chip.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.amber));
                chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
                chip.setOnClickListener(v -> review(id));
                Ui.addWrap(chips, chip, chips.getChildCount() > 0 ? 6 : 0);
            }
            HorizontalScrollView cs = new HorizontalScrollView(c);
            cs.setHorizontalScrollBarEnabled(false);
            cs.addView(chips);
            Ui.add(res, cs, 10);
        }
        LinearLayout btns = Ui.horizontal(c);
        LinearLayout redo = Ui.pillButton(c, 0, "Làm lại", R.color.surface, R.color.text_secondary, R.color.border, v -> { answers.clear(); submitted = false; rerender(); });
        Ui.addWeighted(btns, redo, 0);
        LinearLayout next = passed
                ? Ui.pillButton(c, R.drawable.ic_arrow_right, "Vào học ngay", R.color.brand, android.R.color.white, 0, v -> state.navigate(Screen.WORKSPACE))
                : Ui.pillButton(c, R.drawable.ic_zap, "Ôn cầu nối 2 phút", R.color.amber, android.R.color.white, 0, v -> review(weak.isEmpty() ? null : weak.iterator().next()));
        Ui.addWeighted(btns, next, 8);
        Ui.add(res, btns, 12);
        if (!passed) {
            LinearLayout gaps = Ui.pillButton(c, R.drawable.ic_network, "Xem bản đồ lỗ hổng kiến thức", R.color.surface, R.color.rose, R.color.rose_soft, v -> state.navigate(Screen.GAPS));
            Ui.add(res, gaps, 8);
        }
        Ui.add(body, res, 14);
    }

    /** Scroll to the bridge item (or the prerequisite card) for a weak prerequisite and flash it */
    private void review(String prerequisiteId) {
        if (prerequisiteId == null) return;
        highlightId = prerequisiteId;
        rerender();
        View target = anchors.containsKey("bridge-" + prerequisiteId) ? anchors.get("bridge-" + prerequisiteId) : anchors.get("pre-" + prerequisiteId);
        if (target != null) scroll.post(() -> scroll.smoothScrollTo(0, absTop(target) - Ui.dp(requireContext(), 80)));
        Async.mainDelayed(() -> { highlightId = null; if (isAdded()) rerender(); }, 2200);
    }

    private int absTop(View v) {
        int y = 0;
        View cur = v;
        while (cur != null && cur != scroll) {
            y += cur.getTop();
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return y;
    }

    // ---------- helpers ----------

    /** White card with the web SectionTitle (teal uppercase label with icon) */
    private LinearLayout card(LinearLayout root, int icon, String title, int bg, int stroke) {
        Context c = requireContext();
        MaterialCardView card = Ui.tintedCard(c, bg, stroke);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        in.addView(Ui.iconText(c, icon, title.toUpperCase(new java.util.Locale("vi")), 12, Ui.color(c, R.color.brand), true));
        LinearLayout body = Ui.vertical(c);
        Ui.add(in, body, 12);
        Ui.add(root, card, 18);
        return body;
    }

    private void addPill(LinearLayout row, String s, int bg, int fg, int stroke) {
        Context c = requireContext();
        TextView t = Ui.text(c, s, 11, fg, true);
        t.setBackground(Ui.rounded(c, bg, 999, stroke));
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addWrap(row, t, row.getChildCount() > 0 ? 6 : 0);
    }

    private static android.graphics.drawable.Drawable leftEdge(Context c, int edgeArgb, int stroke, boolean highlight) {
        GradientDrawable fill = Ui.rounded(c, highlight ? R.color.amber_50 : R.color.surface, 12, stroke);
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(edgeArgb);
        float r = Ui.dp(c, 12);
        edge.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{fill, edge});
        ld.setLayerWidth(1, Ui.dp(c, 4));
        ld.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
        return ld;
    }

    private View legendDot(Context c, int argb, String label) {
        LinearLayout row = Ui.horizontal(c);
        View dot = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(argb);
        dot.setBackground(d);
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)));
        Ui.addWrap(row, Ui.text(c, label, 10, R.color.text_muted, true), 4);
        return row;
    }

    /** NODE_STYLE: prerequisite amber / core teal / application indigo */
    private TextView nodeChip(Context c, String label, String type) {
        int bg = "prerequisite".equals(type) ? R.color.amber_50 : "application".equals(type) ? R.color.indigo_50 : R.color.teal_50;
        int stroke = "prerequisite".equals(type) ? R.color.amber_soft : "application".equals(type) ? R.color.indigo_50 : R.color.brand_soft;
        int fg = "prerequisite".equals(type) ? R.color.amber_700 : "application".equals(type) ? R.color.indigo : R.color.teal_900;
        TextView t = Ui.text(c, label, 12, fg, true);
        t.setBackground(Ui.rounded(c, bg, 8, stroke));
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        return t;
    }
}
