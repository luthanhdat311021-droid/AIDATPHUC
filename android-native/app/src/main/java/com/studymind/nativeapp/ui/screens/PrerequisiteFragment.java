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
import java.util.List;
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

    private boolean testMode;
    private int testIndex;
    private boolean bridgeOpen;
    private long testStartedAt;

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
            testMode = false;
            testIndex = 0;
        }

        if (data != null && testMode && !data.diagnosticPreTest.questions.isEmpty()) {
            renderTestMode(root, a, data);
            return;
        }

        // ---- Header ----
        LinearLayout top = Ui.horizontal(c);
        String subject = data != null && !data.subjectArea.isEmpty() ? data.subjectArea : a.document.title;
        LinearLayout sp = Ui.iconText(c, R.drawable.ic_book, subject, 12, Ui.color(c, R.color.m3_on_secondary_container), false);
        ((TextView) sp.getChildAt(1)).setMaxLines(1);
        ((TextView) sp.getChildAt(1)).setEllipsize(android.text.TextUtils.TruncateAt.END);
        sp.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        sp.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        Ui.addWeighted(top, sp, 0);
        android.widget.ImageView help = Ui.icon(c, R.drawable.ic_info, 22, Ui.color(c, R.color.text_secondary));
        help.setOnClickListener(v -> toast("AI tìm kiến thức nền mà bài học mặc định bạn đã biết, rồi soạn bài test để kiểm tra bạn đã sẵn sàng chưa."));
        Ui.addWrap(top, help, 40);
        root.addView(top);
        Ui.add(root, Ui.text(c, "Kiến thức tiên quyết", 24, R.color.text, true), 12);
        Ui.add(root, Ui.text(c, "Phân tích nền tảng bắt buộc trước khi học bài mới để phòng tránh lỗ hổng nhận thức do StudyMind AI tổng hợp.", 14, R.color.text_secondary, false), 6);

        // Depth segmented control
        LinearLayout seg = Ui.horizontal(c);
        seg.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
        seg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        String[] labels = {"Tóm lược (1p)", "Tiêu chuẩn ⭐", "Chuyên sâu"};
        for (int i = 0; i < DEPTHS.length; i++) {
            String d = DEPTHS[i];
            boolean on = d.equals(depth);
            boolean locked = d.equals("Chuyên sâu") && !state.isPro();
            LinearLayout t = Ui.horizontal(c);
            t.setGravity(Gravity.CENTER);
            t.addView(Ui.text(c, labels[i], 13, on ? R.color.brand_dark : R.color.text_secondary, on));
            if (d.equals("Chuyên sâu")) {
                TextView pro = Ui.pill(c, "PRO", R.color.m3_tertiary, android.R.color.white);
                pro.setTextColor(0xFFFFFFFF);
                Ui.addWrap(t, pro, 4);
            }
            t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            if (on) t.setBackground(Ui.rounded(c, R.color.surface, 14, 0));
            t.setOnClickListener(v -> {
                if (loading) return;
                if (locked) state.openUpgrade("Phân tích tiên quyết \"Chuyên sâu\" dành cho thành viên StudyMind Pro.");
                else { depth = d; rerender(); }
            });
            Ui.addWeighted(seg, t, i == 0 ? 0 : 4);
        }
        Ui.add(root, seg, 16);

        LinearLayout status = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        status.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
        String note = data == null ? "Chưa phân tích tài liệu này" : data.isFallback ? "Phân tích sơ bộ — AI chưa phản hồi" : "Dữ liệu đã tối ưu hóa cho " + (data.targetAudienceLevel.isEmpty() ? "bài học" : data.targetAudienceLevel);
        TextView st = Ui.text(c, note, 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stl.setMarginStart(Ui.dp(c, 8));
        status.addView(st, stl);
        LinearLayout run = Ui.iconText(c, R.drawable.ic_zap, loading ? "Đang phân tích..." : data != null ? "Phân tích lại" : "Phân tích ngay", 13, Ui.color(c, R.color.brand), true);
        run.setAlpha(loading ? 0.6f : 1f);
        run.setOnClickListener(v -> analyze());
        status.addView(run);
        Ui.add(root, status, 12);

        if (data == null) {
            LinearLayout intro = Ui.vertical(c);
            intro.setGravity(Gravity.CENTER_HORIZONTAL);
            intro.setBackground(Ui.rounded(c, R.color.m3_surface_low, 24, 0));
            intro.setPadding(Ui.dp(c, 22), Ui.dp(c, 26), Ui.dp(c, 22), Ui.dp(c, 22));
            intro.addView(Ui.iconBox(c, R.drawable.ic_compass, R.color.brand_dark, android.R.color.white, 60, 30, 999));
            TextView m = Ui.text(c, "AI sẽ tìm những kiến thức nền mà \"" + a.document.title + "\" mặc định bạn đã biết, chỉ ra điểm nghẽn nhận thức, "
                    + "vạch lộ trình học và soạn bài test chẩn đoán để kiểm tra bạn đã sẵn sàng chưa.", 14, R.color.text_secondary, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(intro, m, 14);
            LinearLayout go = Ui.iconText(c, R.drawable.ic_sparkles, loading ? "Đang phân tích..." : "Phân tích ngay", 15, 0xFFFFFFFF, true);
            go.setGravity(Gravity.CENTER);
            go.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF005C55));
            go.setMinimumHeight(Ui.dp(c, 52));
            go.setOnClickListener(v -> analyze());
            Ui.add(intro, go, 18);
            Ui.add(root, intro, 18);
            return;
        }

        // ---- Readiness hero ----
        int critical = 0, recommended = 0;
        for (Prerequisite p : data.prerequisites) if ("critical".equals(p.priority)) critical++; else recommended++;
        LinearLayout hero = Ui.vertical(c);
        hero.setBackground(Ui.gradient(c, 22, 0xFF213145, 0xFF005C55));
        hero.setPadding(Ui.dp(c, 18), Ui.dp(c, 22), Ui.dp(c, 18), Ui.dp(c, 16));
        TextView ht = Ui.text(c, !data.overallReadinessNote.isEmpty() ? data.overallReadinessNote
                : "Bài học cần " + critical + " kiến thức bắt buộc và " + recommended + " kiến thức nên biết.", 15, R.color.text, false);
        ht.setTextColor(0xFFEAF1FF);
        ht.setLineSpacing(0, 1.3f);
        hero.addView(ht);
        LinearLayout diag = Ui.iconText(c, R.drawable.ic_brain, "Chẩn đoán: " + critical + " bắt buộc • " + recommended + " nên biết", 13, 0xFFFFFFFF, false);
        diag.setBackground(Ui.gradient(c, 999, 0x33FFFFFF, 0x33FFFFFF));
        diag.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        Ui.addInline(hero, diag, 14);
        Ui.add(root, hero, 16);

        // ---- Prerequisite list ----
        sectionHead(root, R.drawable.ic_clipboard, "Danh sách kiến thức nền cần có", data.prerequisites.size() + " mục");
        if (data.prerequisites.isEmpty()) Ui.add(root, Ui.muted(c, "Chưa xác định được kiến thức tiên quyết."), 8);
        int[] icons = {R.drawable.ic_target, R.drawable.ic_route, R.drawable.ic_link, R.drawable.ic_lightbulb};
        int n = 0;
        for (Prerequisite p : data.prerequisites) {
            boolean crit = "critical".equals(p.priority);
            boolean hl = p.id.equals(highlightId);
            LinearLayout item = Ui.horizontal(c);
            item.setGravity(Gravity.TOP);
            item.setBackground(Ui.rounded(c, hl ? R.color.amber_50 : R.color.surface, 20, hl ? R.color.amber : R.color.border));
            item.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            item.addView(Ui.iconBox(c, icons[n++ % icons.length], crit ? R.color.rose_soft : R.color.teal_50, crit ? R.color.m3_error : R.color.brand_dark, 32, 16, 999));
            LinearLayout tx = Ui.vertical(c);
            LinearLayout th = Ui.horizontal(c);
            th.setGravity(Gravity.TOP);
            Ui.addWeighted(th, Ui.text(c, p.concept, 16, R.color.text, true), 0);
            Ui.addWrap(th, Ui.pill(c, crit ? "Bắt buộc" : "Nên biết", crit ? R.color.rose_soft : R.color.m3_secondary_container,
                    crit ? R.color.m3_error : R.color.m3_on_secondary_container), 6);
            tx.addView(th);
            if (!p.whyNeeded.isEmpty()) {
                SpannableStringBuilder sb = new SpannableStringBuilder("Vì sao cần: ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                sb.append(p.whyNeeded);
                Ui.add(tx, Ui.text(c, sb, 13, R.color.text_secondary, false), 6);
            }
            if (!p.consequenceIfMissing.isEmpty()) Ui.add(tx, Ui.iconText(c, R.drawable.ic_alert_tri, p.consequenceIfMissing, 12, Ui.color(c, R.color.text_muted), false), 4);
            LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            txl.setMarginStart(Ui.dp(c, 12));
            item.addView(tx, txl);
            Ui.add(root, item, 10);
            anchors.put("pre-" + p.id, item);
        }

        // ---- Bottleneck ----
        if (data.cognitiveBottleneck != null && !data.cognitiveBottleneck.concept.isEmpty()) {
            LinearLayout b = Ui.horizontal(c);
            b.setGravity(Gravity.TOP);
            b.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 22, 0));
            b.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
            b.addView(Ui.iconBox(c, R.drawable.ic_lightbulb, R.color.m3_tertiary_container, android.R.color.white, 36, 18, 999));
            LinearLayout bt = Ui.vertical(c);
            bt.addView(Ui.text(c, "Điểm nghẽn nhận thức tiềm ẩn", 15, R.color.m3_on_tertiary_fixed, true));
            Ui.add(bt, Ui.text(c, data.cognitiveBottleneck.concept + (data.cognitiveBottleneck.description.isEmpty() ? "" : ": " + data.cognitiveBottleneck.description), 14, R.color.m3_on_tertiary_fixed, false), 4);
            if (!data.cognitiveBottleneck.advice.isEmpty()) {
                SpannableStringBuilder sb = new SpannableStringBuilder("Lời khuyên: ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                sb.append(data.cognitiveBottleneck.advice);
                Ui.add(bt, Ui.text(c, sb, 13, R.color.m3_tertiary, false), 6);
            }
            LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            btl.setMarginStart(Ui.dp(c, 12));
            b.addView(bt, btl);
            Ui.add(root, b, 18);
        }

        // ---- Bridge ----
        if (!data.quickBridgeSummary.isEmpty()) {
            LinearLayout br = Ui.vertical(c);
            br.setBackground(Ui.rounded(c, R.color.teal_200, 22, 0));
            br.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
            br.addView(Ui.iconText(c, R.drawable.ic_book, "Cầu nối kiến thức – Đọc nhanh trong 2 phút", 16, Ui.color(c, R.color.m3_on_primary_fixed), true));
            Ui.add(br, Ui.text(c, "Tóm tắt " + data.quickBridgeSummary.size() + " kiến thức nền then chốt cần nhớ trước khi bước vào bài học mới.", 13, R.color.m3_on_primary_fixed, false), 6);
            if (bridgeOpen || highlightId != null) {
                for (BridgeItem b : data.quickBridgeSummary) {
                    boolean hl = b.prerequisiteId != null && b.prerequisiteId.equals(highlightId);
                    LinearLayout item = Ui.vertical(c);
                    item.setBackground(Ui.rounded(c, hl ? R.color.amber_50 : R.color.surface, 14, hl ? R.color.amber : 0));
                    item.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
                    item.addView(Ui.text(c, b.concept, 14, R.color.text, true));
                    Ui.add(item, Ui.text(c, b.quickReview, 13, R.color.text_secondary, false), 4);
                    Ui.add(br, item, 10);
                    if (b.prerequisiteId != null) anchors.put("bridge-" + b.prerequisiteId, item);
                }
            }
            LinearLayout open = Ui.iconText(c, R.drawable.ic_book, bridgeOpen ? "Thu gọn cầu nối" : "Mở đọc cầu nối (2 phút)", 15, 0xFFFFFFFF, true);
            open.setGravity(Gravity.CENTER);
            open.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF005C55));
            open.setMinimumHeight(Ui.dp(c, 48));
            open.setOnClickListener(v -> { bridgeOpen = !bridgeOpen; rerender(); });
            Ui.add(br, open, 14);
            Ui.add(root, br, 18);
        }

        // ---- Dependency graph (stages) ----
        if (!data.dependencyGraph.isEmpty()) {
            sectionHead(root, R.drawable.ic_fork, "Đồ thị phụ thuộc kiến thức", "Chuỗi tiếp thu");
            LinearLayout g = Ui.vertical(c);
            g.setBackground(Ui.rounded(c, R.color.surface, 20, R.color.border));
            g.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            LinearLayout stages = Ui.horizontal(c);
            java.util.LinkedHashMap<String, String> nodes = new java.util.LinkedHashMap<>();
            for (PrerequisiteDependency d : data.dependencyGraph) { nodes.putIfAbsent(d.from, d.fromType); nodes.putIfAbsent(d.to, d.toType); }
            String[] order = {"prerequisite", "core", "application"};
            String[] stageNames = {"Nền tảng", "Trong bài", "Ứng dụng"};
            int stageNo = 0;
            for (int si = 0; si < order.length; si++) {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (Map.Entry<String, String> e : nodes.entrySet()) if (order[si].equals(e.getValue())) names.add(e.getKey());
                if (names.isEmpty()) continue;
                if (stageNo > 0) stages.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, Ui.color(c, R.color.text_secondary)));
                stageNo++;
                LinearLayout box = Ui.vertical(c);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                boolean core = order[si].equals("core");
                box.setBackground(Ui.rounded(c, core ? R.color.m3_surface_high : R.color.m3_surface_low, 14, 0));
                box.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 10));
                TextView sl = Ui.pill(c, "Giai đoạn " + stageNo, core ? R.color.brand_dark : R.color.m3_surface_container, core ? android.R.color.white : R.color.text_secondary);
                if (core) sl.setTextColor(0xFFFFFFFF);
                box.addView(sl);
                TextView nt = Ui.text(c, String.join(" & ", names.subList(0, Math.min(2, names.size()))), 13, R.color.text, true);
                nt.setGravity(Gravity.CENTER);
                nt.setMaxLines(3);
                Ui.add(box, nt, 6);
                TextView sn = Ui.text(c, stageNames[si], 12, R.color.text_muted, false);
                sn.setGravity(Gravity.CENTER);
                Ui.add(box, sn, 4);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(c, 140), ViewGroup.LayoutParams.WRAP_CONTENT);
                bl.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
                stages.addView(box, bl);
            }
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(stages);
            g.addView(hs);
            for (PrerequisiteDependency d : data.dependencyGraph) {
                LinearLayout rel = Ui.horizontal(c);
                rel.setBackground(Ui.rounded(c, R.color.m3_surface_low, 12, 0));
                rel.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
                rel.addView(Ui.icon(c, R.drawable.ic_route, 16, Ui.color(c, R.color.brand)));
                SpannableStringBuilder rs = new SpannableStringBuilder(d.from);
                rs.setSpan(new StyleSpan(Typeface.BOLD), 0, rs.length(), 0);
                rs.append(" " + d.relation + " ");
                int s0 = rs.length();
                rs.append(d.to);
                rs.setSpan(new StyleSpan(Typeface.BOLD), s0, rs.length(), 0);
                Ui.addWeighted(rel, Ui.text(c, rs, 12, R.color.text_secondary, false), 8);
                Ui.add(g, rel, 8);
            }
            Ui.add(root, g, 10);
        }

        // ---- Learning path ----
        if (!data.learningPath.isEmpty()) {
            sectionHead(root, R.drawable.ic_route, "Lộ trình học đề xuất", data.learningPath.size() + " bước");
            LinearLayout lp = Ui.vertical(c);
            lp.setBackground(Ui.rounded(c, R.color.surface, 20, R.color.border));
            lp.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            for (LearningStep s : data.learningPath) {
                LinearLayout row = Ui.horizontal(c);
                row.setGravity(Gravity.TOP);
                TextView num = Ui.text(c, String.valueOf(s.step), 13, android.R.color.white, true);
                num.setTextColor(0xFFFFFFFF);
                num.setGravity(Gravity.CENTER);
                num.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
                row.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
                LinearLayout body = Ui.vertical(c);
                LinearLayout h2 = Ui.horizontal(c);
                h2.setGravity(Gravity.TOP);
                Ui.addWeighted(h2, Ui.text(c, s.title, 15, R.color.text, true), 0);
                String badge = s.type.equals("prerequisite") ? "Nền tảng" : s.type.equals("advanced_application") ? "Vận dụng" : "Trọng tâm";
                Ui.addWrap(h2, Ui.pill(c, badge, R.color.m3_surface_container, R.color.text_secondary), 6);
                body.addView(h2);
                if (!s.description.isEmpty()) Ui.add(body, Ui.text(c, s.description, 13, R.color.text_secondary, false), 4);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                bl.setMarginStart(Ui.dp(c, 12));
                row.addView(body, bl);
                Ui.add(lp, row, lp.getChildCount() > 0 ? 16 : 0);
            }
            Ui.add(root, lp, 10);
        }

        // ---- CTA ----
        int qn = data.diagnosticPreTest.questions.size();
        LinearLayout cta = Ui.iconText(c, 0, qn > 0 ? (submitted ? "Xem kết quả bài test chẩn đoán" : "Làm bài test chẩn đoán ngay (" + qn + " câu)") : "Phân tích lại để có bài test chẩn đoán", 15, 0xFFFFFFFF, true);
        cta.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, 0xFFFFFFFF));
        ((LinearLayout.LayoutParams) cta.getChildAt(1).getLayoutParams()).setMarginStart(Ui.dp(c, 8));
        cta.setGravity(Gravity.CENTER);
        cta.setBackground(Ui.gradient(c, 18, 0xFF005C55, 0xFF0F766E));
        cta.setMinimumHeight(Ui.dp(c, 56));
        cta.setOnClickListener(v -> {
            if (qn == 0) { analyze(); return; }
            testMode = true;
            if (!submitted) { testIndex = 0; testStartedAt = android.os.SystemClock.elapsedRealtime(); }
            rerender();
            scroll.post(() -> scroll.scrollTo(0, 0));
        });
        Ui.add(root, cta, 22);
        TextView foot = Ui.text(c, "Thời gian làm bài: ~" + Math.max(1, qn) + " phút • Nhận ngay phản hồi từ StudyMind AI", 12, R.color.text_muted, false);
        foot.setGravity(Gravity.CENTER);
        Ui.add(root, foot, 8);
    }

    private void sectionHead(LinearLayout root, int icon, String title, String tag) {
        Context c = requireContext();
        LinearLayout h = Ui.horizontal(c);
        Ui.addWeighted(h, Ui.iconText(c, icon, title, 17, Ui.color(c, R.color.text), true), 0);
        if (tag != null) h.addView(Ui.pill(c, tag, R.color.m3_surface_container, R.color.text_secondary));
        Ui.add(root, h, 22);
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
    /** Stitch "Test chẩn đoán & kết quả": one question at a time, inline explanation, readiness result */
    private void renderTestMode(LinearLayout root, ActiveDocument a, PrerequisiteAnalysis data) {
        Context c = requireContext();
        DiagnosticPreTest test = data.diagnosticPreTest;
        List<DiagnosticQuestion> qs = test.questions;
        if (testIndex >= qs.size()) testIndex = 0;

        LinearLayout top = Ui.horizontal(c);
        LinearLayout back = Ui.iconText(c, R.drawable.ic_chevron_left, "Tổng quan", 13, Ui.color(c, R.color.text), false);
        back.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.border));
        back.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
        back.setOnClickListener(v -> { testMode = false; rerender(); });
        top.addView(back);
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        long secs = testStartedAt > 0 ? (android.os.SystemClock.elapsedRealtime() - testStartedAt) / 1000 : 0;
        LinearLayout tm = Ui.iconText(c, R.drawable.ic_timer, String.format(java.util.Locale.US, "%02d:%02d", secs / 60, secs % 60), 13, Ui.color(c, R.color.text), true);
        tm.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.border));
        tm.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        top.addView(tm);
        Ui.addWrap(top, Ui.pill(c, qs.size() + " câu hỏi", R.color.m3_secondary_container, R.color.m3_on_secondary_container), 6);
        Ui.addWrap(top, Ui.pill(c, "⚡ Tiên quyết", R.color.m3_tertiary_fixed, R.color.m3_on_tertiary_fixed), 6);
        root.addView(top);

        LinearLayout goal = Ui.horizontal(c);
        goal.setGravity(Gravity.TOP);
        goal.setBackground(Ui.rounded(c, R.color.surface, 18, R.color.border));
        goal.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        goal.addView(Ui.iconBox(c, R.drawable.ic_clipboard, R.color.teal_50, R.color.brand_dark, 40, 20, 12));
        LinearLayout gt = Ui.vertical(c);
        gt.addView(Ui.text(c, test.title.isEmpty() ? "Mục tiêu kiểm tra" : test.title, 14, R.color.brand_dark, true));
        gt.addView(Ui.text(c, !test.instructions.isEmpty() ? test.instructions : "Đánh giá mức độ vững vàng của các khái niệm tiên quyết trước khi học bài " + a.document.title + ".", 13, R.color.text_secondary, false));
        LinearLayout.LayoutParams gtl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        gtl.setMarginStart(Ui.dp(c, 12));
        goal.addView(gt, gtl);
        Ui.add(root, goal, 14);

        // ---- Current question ----
        DiagnosticQuestion q = qs.get(testIndex);
        Integer chosen = answers.get(q.id);
        MaterialCardView qc = Ui.card(c);
        LinearLayout qi = Ui.inner(qc);
        LinearLayout qh = Ui.horizontal(c);
        TextView qn = Ui.text(c, "CÂU HỎI " + (testIndex + 1) + "/" + qs.size(), 13, R.color.text_secondary, true);
        qn.setLetterSpacing(0.06f);
        Ui.addWeighted(qh, qn, 0);
        if (chosen != null) qh.addView(Ui.pill(c, submitted ? (chosen == q.correctIndex ? "Chính xác" : "Chưa đúng") : "Đã trả lời",
                submitted && chosen != q.correctIndex ? R.color.rose_soft : R.color.teal_200, submitted && chosen != q.correctIndex ? R.color.m3_error : R.color.brand_dark));
        qi.addView(qh);
        TextView qt = Ui.text(c, q.question, 17, R.color.text, true);
        qt.setLineSpacing(0, 1.3f);
        Ui.add(qi, qt, 12);
        String[] letters = {"A", "B", "C", "D", "E", "F"};
        for (int oi = 0; oi < q.options.size(); oi++) {
            int idx = oi;
            boolean sel = chosen != null && chosen == oi;
            boolean right = oi == q.correctIndex;
            boolean reveal = submitted || sel;
            LinearLayout opt = Ui.vertical(c);
            int bg = R.color.m3_surface_low;
            if (reveal && sel && right) bg = R.color.teal_50;
            else if (submitted && right) bg = R.color.emerald_soft;
            else if (reveal && sel) bg = R.color.rose_soft;
            opt.setBackground(Ui.rounded(c, bg, 16, reveal && (sel || (submitted && right)) ? (right ? R.color.brand : R.color.rose) : 0));
            opt.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            LinearLayout row = Ui.horizontal(c);
            TextView l = Ui.text(c, letters[Math.min(oi, letters.length - 1)], 13, sel ? android.R.color.white : R.color.text_secondary, true);
            l.setGravity(Gravity.CENTER);
            if (sel) { l.setTextColor(0xFFFFFFFF); l.setBackground(Ui.gradient(c, 999, right ? 0xFF005C55 : 0xFFBA1A1A, right ? 0xFF005C55 : 0xFFBA1A1A)); }
            else l.setBackground(Ui.rounded(c, R.color.surface, 999, 0));
            row.addView(l, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
            TextView ot = Ui.text(c, q.options.get(oi).replaceFirst("^[A-F][.)]\\s*", ""), 15, R.color.text, sel);
            LinearLayout.LayoutParams otl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            otl.setMarginStart(Ui.dp(c, 12));
            row.addView(ot, otl);
            if (reveal && sel) row.addView(Ui.iconText(c, right ? R.drawable.ic_check_circle : R.drawable.ic_close, right ? "Chính xác" : "Sai", 13, Ui.color(c, right ? R.color.brand_dark : R.color.m3_error), true));
            opt.addView(row);
            if (reveal && (sel || (submitted && right)) && right && !q.explanation.isEmpty()) {
                LinearLayout ex = Ui.vertical(c);
                ex.setBackground(Ui.rounded(c, R.color.surface, 12, 0));
                ex.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
                ex.addView(Ui.text(c, "Lời giải chi tiết:", 13, R.color.text, true));
                Ui.add(ex, Ui.text(c, q.explanation, 13, R.color.text_secondary, false), 4);
                Ui.add(opt, ex, 10);
            }
            opt.setOnClickListener(v -> { if (!submitted) { answers.put(q.id, idx); rerender(); } });
            Ui.add(qi, opt, 10);
        }
        String concept = conceptOf(data, q.testedPrerequisiteId);
        if (concept != null) {
            LinearLayout rv = Ui.horizontal(c);
            rv.setBackground(Ui.rounded(c, R.color.m3_surface_container, 14, 0));
            rv.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            Ui.addWeighted(rv, Ui.iconText(c, R.drawable.ic_book, "Ôn nhanh kiến thức " + concept, 13, Ui.color(c, R.color.text_secondary), false), 0);
            rv.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, Ui.color(c, R.color.text_secondary)));
            rv.setOnClickListener(v -> { testMode = false; bridgeOpen = true; review(q.testedPrerequisiteId); });
            Ui.add(qi, rv, 12);
        }
        LinearLayout nav = Ui.horizontal(c);
        TextView prev = Ui.text(c, "← Câu trước", 14, R.color.text_secondary, true);
        prev.setAlpha(testIndex == 0 ? 0.4f : 1f);
        prev.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        prev.setOnClickListener(v -> { if (testIndex > 0) { testIndex--; rerender(); } });
        nav.addView(prev);
        nav.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView next = Ui.text(c, "Câu sau →", 14, R.color.brand, true);
        next.setAlpha(testIndex == qs.size() - 1 ? 0.4f : 1f);
        next.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        next.setOnClickListener(v -> { if (testIndex < qs.size() - 1) { testIndex++; rerender(); } });
        nav.addView(next);
        Ui.add(qi, nav, 8);
        Ui.add(root, qc, 14);

        boolean all = answers.size() >= qs.size();
        if (!submitted) {
            LinearLayout grade = Ui.iconText(c, R.drawable.ic_check_circle, all ? "Chấm điểm bài test" : "Trả lời đủ " + qs.size() + " câu để chấm điểm (" + answers.size() + "/" + qs.size() + ")", 15, 0xFFFFFFFF, true);
            grade.setGravity(Gravity.CENTER);
            grade.setBackground(Ui.gradient(c, 18, 0xFF005C55, 0xFF005C55));
            grade.setMinimumHeight(Ui.dp(c, 54));
            grade.setAlpha(all ? 1f : 0.5f);
            grade.setOnClickListener(v -> {
                if (!all) { toast("Hãy trả lời đủ " + qs.size() + " câu trước khi chấm điểm."); return; }
                submitted = true;
                state.submitDiagnostic(new HashMap<>(answers));
                rerender();
            });
            Ui.add(root, grade, 6);
            return;
        }

        // ---- Result ----
        int correct = 0;
        Map<String, int[]> perPre = new java.util.LinkedHashMap<>();
        Set<String> weak = new LinkedHashSet<>();
        for (DiagnosticQuestion dq : qs) {
            Integer ch = answers.get(dq.id);
            boolean ok = ch != null && ch == dq.correctIndex;
            if (ok) correct++;
            else if (dq.testedPrerequisiteId != null) weak.add(dq.testedPrerequisiteId);
            String k = dq.testedPrerequisiteId != null ? dq.testedPrerequisiteId : "_";
            int[] cnt = perPre.computeIfAbsent(k, x -> new int[2]);
            cnt[1]++;
            if (ok) cnt[0]++;
        }
        int score = Math.round(correct * 100f / qs.size());
        boolean passed = score >= test.passScore;
        int needed = (int) Math.ceil(test.passScore * qs.size() / 100f);

        LinearLayout res = Ui.vertical(c);
        res.setBackground(Ui.gradient(c, 26, passed ? 0xFFDCF7F2 : 0xFFFFF1DE, 0xFFF8F9FF));
        res.setPadding(Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 16));
        LinearLayout rh = Ui.horizontal(c);
        rh.setGravity(Gravity.TOP);
        rh.addView(Ui.iconBox(c, passed ? R.drawable.ic_shield_check : R.drawable.ic_alert, passed ? R.color.brand_dark : R.color.m3_tertiary, android.R.color.white, 56, 28, 16));
        LinearLayout rt = Ui.vertical(c);
        rt.addView(Ui.pill(c, "• Kết quả chẩn đoán toàn diện", R.color.surface, R.color.brand_dark));
        Ui.add(rt, Ui.text(c, passed ? "🎉 Bạn đã sẵn sàng học bài này!" : "Nên ôn lại nền tảng trước", 20, R.color.text, true), 6);
        SpannableStringBuilder sb = new SpannableStringBuilder("Điểm số: ");
        int s0 = sb.length();
        sb.append(correct + "/" + qs.size() + " câu đạt chuẩn");
        sb.setSpan(new StyleSpan(Typeface.BOLD), s0, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, passed ? R.color.brand_dark : R.color.m3_tertiary)), s0, sb.length(), 0);
        sb.append(" (Ngưỡng đạt: ≥ " + needed + "/" + qs.size() + " câu)");
        Ui.add(rt, Ui.text(c, sb, 13, R.color.text_secondary, false), 4);
        LinearLayout.LayoutParams rtl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rtl.setMarginStart(Ui.dp(c, 12));
        rh.addView(rt, rtl);
        res.addView(rh);

        LinearLayout comp = Ui.vertical(c);
        comp.setBackground(Ui.rounded(c, R.color.surface, 18, 0));
        comp.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 14));
        comp.addView(Ui.iconText(c, R.drawable.ic_target, "Phân tích năng lực thành phần", 14, Ui.color(c, R.color.text), true));
        for (Map.Entry<String, int[]> e : perPre.entrySet()) {
            String name = e.getKey().equals("_") ? "Kiến thức chung" : conceptOf(data, e.getKey());
            if (name == null) name = "Kiến thức nền";
            int pct = e.getValue()[0] * 100 / e.getValue()[1];
            LinearLayout r = Ui.horizontal(c);
            Ui.addWeighted(r, Ui.text(c, name, 13, R.color.text, false), 0);
            r.addView(Ui.text(c, pct + "%", 13, R.color.text, true));
            Ui.add(comp, r, 12);
            Ui.add(comp, Ui.track(c, pct, pct >= 80 ? R.color.brand_dark : pct >= 50 ? R.color.amber : R.color.rose, 8), 6);
        }
        if (!weak.isEmpty()) {
            String first = conceptOf(data, weak.iterator().next());
            if (first != null) Ui.add(comp, Ui.iconText(c, R.drawable.ic_lightbulb, "Khuyến nghị: Xem lại \"" + first + "\" trước giờ học chính khóa.", 12, Ui.color(c, R.color.m3_tertiary), false), 10);
        }
        Ui.add(res, comp, 14);
        Ui.add(root, res, 10);

        LinearLayout tiles = Ui.horizontal(c);
        long per = qs.isEmpty() ? 0 : secs / qs.size();
        miniTile(tiles, R.drawable.ic_timer, "Tốc độ làm bài", per + "s / câu", R.color.teal_200, R.color.brand_dark, 0);
        miniTile(tiles, R.drawable.ic_award, "Độ tự tin AI", score >= 90 ? "Rất cao (A+)" : score >= 70 ? "Cao (A)" : score >= 50 ? "Trung bình (B)" : "Thấp (C)", R.color.m3_tertiary_fixed, R.color.m3_tertiary, 10);
        Ui.add(root, tiles, 14);

        LinearLayout go = Ui.iconText(c, 0, passed ? "Vào học bài mới ngay 🚀" : "Ôn cầu nối kiến thức 2 phút 📖", 16, 0xFFFFFFFF, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.gradient(c, 18, 0xFF005C55, 0xFF005C55));
        go.setMinimumHeight(Ui.dp(c, 56));
        go.setOnClickListener(v -> {
            if (passed) state.navigate(Screen.WORKSPACE);
            else { testMode = false; bridgeOpen = true; review(weak.isEmpty() ? null : weak.iterator().next()); if (weak.isEmpty()) rerender(); }
        });
        Ui.add(root, go, 16);
        if (passed) {
            TextView bridge = Ui.text(c, "Ôn cầu nối kiến thức 2 phút 📖", 15, R.color.text_secondary, true);
            bridge.setGravity(Gravity.CENTER);
            bridge.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 18, 0));
            bridge.setMinHeight(Ui.dp(c, 52));
            bridge.setOnClickListener(v -> { testMode = false; bridgeOpen = true; rerender(); });
            Ui.add(root, bridge, 10);
        }
        TextView gaps = Ui.text(c, "Xem bản đồ lỗ hổng kiến thức 🗺️", 15, R.color.text, true);
        gaps.setGravity(Gravity.CENTER);
        gaps.setBackground(Ui.rounded(c, R.color.surface, 18, R.color.border));
        gaps.setMinHeight(Ui.dp(c, 52));
        gaps.setOnClickListener(v -> state.navigate(Screen.GAPS));
        Ui.add(root, gaps, 10);
        LinearLayout redo = Ui.iconText(c, R.drawable.ic_reset, "Làm lại bài test chẩn đoán", 14, Ui.color(c, R.color.text_secondary), true);
        redo.setGravity(Gravity.CENTER);
        redo.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        redo.setOnClickListener(v -> { answers.clear(); submitted = false; testIndex = 0; testStartedAt = android.os.SystemClock.elapsedRealtime(); rerender(); });
        Ui.add(root, redo, 8);
    }

    private void miniTile(LinearLayout row, int icon, String label, String value, int bg, int fg, int start) {
        Context c = requireContext();
        LinearLayout t = Ui.horizontal(c);
        t.setBackground(Ui.rounded(c, R.color.surface, 18, R.color.border));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        t.addView(Ui.iconBox(c, icon, bg, fg, 36, 18, 10));
        LinearLayout tx = Ui.vertical(c);
        tx.addView(Ui.text(c, label, 12, R.color.text_secondary, false));
        tx.addView(Ui.text(c, value, 15, R.color.text, true));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 10));
        t.addView(tx, tl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(t, lp);
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
