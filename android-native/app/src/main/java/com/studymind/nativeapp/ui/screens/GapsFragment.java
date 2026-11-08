package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.KnowledgeGaps;
import com.studymind.nativeapp.data.KnowledgeGaps.GapNode;
import com.studymind.nativeapp.data.KnowledgeGaps.RootGap;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.KbMisconception;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.GraphView;
import com.studymind.nativeapp.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** "Bản đồ lỗ hổng kiến thức" — KnowledgeGapView.tsx: ranked root-gap cards + the dependency graph. */
public class GapsFragment extends BaseFragment {
    private String selectedId;

    @Override
    protected Object watchKey() { return state.activeDoc(); }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        if (a == null) {
            LinearLayout e = Ui.vertical(c);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(0, Ui.dp(c, 60), 0, 0);
            e.addView(Ui.icon(c, R.drawable.ic_network, 40, Ui.color(c, R.color.brand)));
            TextView t = Ui.text(c, "Chưa có tài liệu nào được mở", 18, R.color.text, true);
            Ui.addInline(e, t, 12);
            Ui.addInline(e, Ui.text(c, "Mở một bài học để xem bản đồ lỗ hổng kiến thức của bạn.", 12, R.color.text_muted, false), 6);
            LinearLayout b = Ui.pillButton(c, 0, "Mở lịch sử bài học", R.color.brand, android.R.color.white, 0, v -> state.navigate(Screen.HISTORY));
            b.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            Ui.addInline(e, b, 14);
            root.addView(e);
            return;
        }
        KnowledgeGaps.Analysis an = KnowledgeGaps.analyze(a.pack, a.quizHistory);
        boolean exists = false;
        for (RootGap r : an.roots) if (r.node.id.equals(selectedId)) exists = true;
        if (!exists) selectedId = an.roots.isEmpty() ? null : an.roots.get(0).node.id;

        // ---- Header: AI DeepScan ----
        LinearLayout head = Ui.vertical(c);
        head.setBackground(Ui.gradient(c, 22, 0xFFEFF4FF, 0xFFE6F6F3));
        head.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 16));
        LinearLayout chips = Ui.horizontal(c);
        LinearLayout ds = Ui.iconText(c, R.drawable.ic_brain, "AI DeepScan™", 12, 0xFFFFFFFF, true);
        ds.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
        ds.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        chips.addView(ds);
        TextView doc = Ui.pill(c, a.document.title, R.color.m3_secondary_container, R.color.m3_on_secondary_container);
        doc.setMaxLines(1);
        doc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        doc.setMaxWidth(Ui.dp(c, 200));
        Ui.addWrap(chips, doc, 8);
        head.addView(chips);
        SpannableStringBuilder intro = new SpannableStringBuilder("Cơ chế AI truy nguyên nguồn gốc: ");
        intro.setSpan(new StyleSpan(Typeface.BOLD), 0, intro.length(), 0);
        intro.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.brand_dark)), 0, intro.length(), 0);
        intro.append("Không chỉ nhận diện câu trả lời sai, StudyMind truy vết chính xác khái niệm gốc rễ gây ra lỗi sai liên hoàn trong chuỗi kiến thức của bạn.");
        TextView it = Ui.text(c, intro, 15, R.color.text, false);
        it.setLineSpacing(0, 1.3f);
        Ui.add(head, it, 10);
        root.addView(head);

        if (an.nodes.isEmpty()) {
            root.addView(centerCard(c, R.drawable.ic_alert_tri, R.color.amber, "Bài học này chưa có đồ thị khái niệm",
                    "Hãy chuyển hóa lại tài liệu để hệ thống phân tích các khái niệm và quan hệ phụ thuộc giữa chúng.", R.color.surface, R.color.border));
            return;
        }

        int weak = 0;
        for (GapNode n : an.nodes.values()) if ("weak".equals(n.status)) weak++;
        LinearLayout stats = Ui.horizontal(c);
        stats.setBaselineAligned(false);
        statTile(stats, R.drawable.ic_clipboard, R.color.m3_secondary_container, R.color.brand, R.color.surface, an.evidenceCount, "Lượt AI bóc tách", R.color.text, 0);
        statTile(stats, R.drawable.ic_alert_tri, R.color.m3_tertiary_fixed, R.color.m3_tertiary, R.color.amber_50, weak, "Khái niệm đang yếu", R.color.m3_tertiary, 8);
        statTile(stats, R.drawable.ic_target, R.color.rose_soft, R.color.m3_error, R.color.rose_50, an.roots.size(), "Lỗ hổng gốc then chốt", R.color.m3_error, 8);
        Ui.add(root, stats, 16);

        if (an.evidenceCount == 0) {
            View card = centerCard(c, R.drawable.ic_target, R.color.brand, "Chưa có dữ liệu luyện tập để phân tích",
                    "Làm trắc nghiệm, ôn thẻ ghi nhớ (đánh giá Khó/Dễ) hoặc làm bài test chẩn đoán. Bản đồ sẽ tự cập nhật theo kết quả của bạn.", R.color.surface, R.color.border);
            LinearLayout in = Ui.inner((MaterialCardView) card);
            Object[][] practice = {{Screen.QUIZ, R.drawable.ic_quiz, "Làm trắc nghiệm"}, {Screen.FLASHCARD, R.drawable.ic_layers, "Ôn thẻ ghi nhớ"},
                    {Screen.PREREQUISITE, R.drawable.ic_clipboard, "Làm bài test chẩn đoán"}};
            for (Object[] p : practice) Ui.add(in, actionBtn(c, (int) p[1], (String) p[2], false, v -> state.navigate((Screen) p[0])), 8);
            root.addView(card);
            return;
        }
        if (an.roots.isEmpty()) {
            root.addView(centerCard(c, R.drawable.ic_check_circle, R.color.emerald, "Chưa phát hiện lỗ hổng nào",
                    "Mọi phần bạn đã luyện tập đều đang ổn. Tiếp tục làm trắc nghiệm để bản đồ bao phủ thêm khái niệm.", R.color.emerald_50, R.color.emerald_soft));
            return;
        }

        RootGap selected = null;
        for (RootGap r : an.roots) if (r.node.id.equals(selectedId)) selected = r;

        // ---- Dependency graph card ----
        MaterialCardView gc = Ui.card(c);
        LinearLayout gi = Ui.inner(gc);
        LinearLayout gh = Ui.horizontal(c);
        Ui.addWeighted(gh, Ui.iconText(c, R.drawable.ic_network, "Đồ thị chuỗi phụ thuộc", 17, Ui.color(c, R.color.text), true), 0);
        gh.addView(Ui.pill(c, "Cây phân cấp", R.color.m3_secondary_container, R.color.m3_on_secondary_container));
        gi.addView(gh);
        FrameLayout graphBox = new FrameLayout(c);
        graphBox.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
        graphBox.setClipToOutline(true);
        GraphView graph = new GraphView(c);
        graph.setGraph(buildNodes(an, selected), buildEdges(an, selected), false);
        graph.setOnNodeClick(n -> {
            for (RootGap r : an.roots) if (r.node.id.equals(n.id)) { selectedId = n.id; rerender(); }
        });
        graphBox.addView(graph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout zoom = Ui.vertical(c);
        Object[][] zs = {{R.drawable.ic_plus, "1.25"}, {R.drawable.ic_minus, "0.8"}, {R.drawable.ic_fit, "fit"}};
        for (Object[] z : zs) {
            FrameLayout b = Ui.iconBox(c, (int) z[0], R.color.surface, R.color.text, 36, 18, 10);
            String act = (String) z[1];
            b.setOnClickListener(v -> { if (act.equals("fit")) graph.fit(); else graph.zoom(Float.parseFloat(act)); });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
            bl.bottomMargin = Ui.dp(c, 6);
            zoom.addView(b, bl);
        }
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        zl.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
        graphBox.addView(zoom, zl);
        graphBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 300)));
        Ui.add(gi, graphBox, 12);
        com.google.android.material.chip.ChipGroup legend = new com.google.android.material.chip.ChipGroup(c);
        legend.setChipSpacingHorizontal(Ui.dp(c, 14));
        legend.addView(legendDot(c, 0xFFBA1A1A, "Lỗ hổng gốc"));
        legend.addView(legendDot(c, 0xFF945D00, "Đang yếu"));
        legend.addView(legendDot(c, 0xFF005C55, "Đã vững"));
        legend.addView(legendDot(c, 0xFFBDC9C6, "Chưa test"));
        Ui.add(gi, legend, 10);
        Ui.add(root, gc, 16);

        // ---- Ranked root gaps ----
        LinearLayout rh = Ui.horizontal(c);
        Ui.addWeighted(rh, Ui.text(c, "Lỗ hổng gốc (Ưu tiên cao nhất)", 18, R.color.text, true), 0);
        rh.addView(Ui.pill(c, "Cần xử lý ngay", R.color.rose_soft, R.color.m3_error));
        Ui.add(root, rh, 22);
        for (int i = 0; i < an.roots.size(); i++) Ui.add(root, gapCard(c, an.roots.get(i), i + 1, an), 12);

        LinearLayout note = Ui.horizontal(c);
        note.setGravity(Gravity.TOP);
        note.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
        note.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        note.addView(Ui.icon(c, R.drawable.ic_refresh, 20, Ui.color(c, R.color.brand_dark)));
        TextView nt = Ui.text(c, "Hệ thống AI tự động cập nhật bản đồ tri thức sau mỗi bài kiểm tra, lượt ôn thẻ hoặc bài test chẩn đoán. Điểm khuyết sẽ tự chuyển sang xanh khi bạn làm đúng.", 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nl.setMarginStart(Ui.dp(c, 12));
        note.addView(nt, nl);
        Ui.add(root, note, 16);
    }

    private void statTile(LinearLayout row, int icon, int iconBg, int iconFg, int bg, int value, String label, int valueColor, int start) {
        Context c = requireContext();
        LinearLayout t = Ui.vertical(c);
        t.setBackground(Ui.rounded(c, bg, 20, bg == R.color.surface ? R.color.border : 0));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        t.addView(Ui.iconBox(c, icon, iconBg, iconFg, 34, 18, 10));
        Ui.add(t, Ui.text(c, String.valueOf(value), 24, valueColor, true), 8);
        Ui.add(t, Ui.text(c, label, 12, valueColor == R.color.text ? R.color.text_secondary : valueColor, false), 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(t, lp);
    }

    private View legendDot(Context c, int argb, String label) {
        LinearLayout row = Ui.horizontal(c);
        View dot = new View(c);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(argb);
        dot.setBackground(d);
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 9), Ui.dp(c, 9)));
        Ui.addWrap(row, Ui.text(c, label, 12, R.color.text_secondary, false), 6);
        return row;
    }

    private View actionBtn(Context c, int icon, String label, boolean primary, View.OnClickListener l) {
        LinearLayout b = Ui.iconText(c, icon, label, 15, primary ? 0xFFFFFFFF : Ui.color(c, R.color.text_secondary), primary);
        b.setGravity(Gravity.CENTER);
        b.setBackground(primary ? Ui.gradient(c, 16, 0xFF005C55, 0xFF005C55) : Ui.rounded(c, R.color.m3_surface_high, 16, 0));
        b.setMinimumHeight(Ui.dp(c, 50));
        b.setOnClickListener(l);
        return b;
    }

    // ---------- root gap card ----------

    private View gapCard(Context c, RootGap gap, int rank, KnowledgeGaps.Analysis an) {
        boolean sel = gap.node.id.equals(selectedId);
        MaterialCardView card = Ui.card(c);
        if (sel) {
            card.setStrokeColor(Ui.color(c, R.color.m3_error));
            card.setStrokeWidth(Ui.dp(c, 2));
        }
        card.setOnClickListener(v -> { selectedId = gap.node.id; rerender(); scroll.post(() -> scroll.smoothScrollTo(0, 0)); });
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));

        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        Ui.addWeighted(top, Ui.text(c, "Lỗ hổng #" + rank + ": " + gap.node.label, 19, R.color.text, true), 0);
        TextView lvl = Ui.pill(c, "prerequisite".equals(gap.node.kind) ? "Kiến thức nền" : "Cấp độ " + rank, R.color.m3_error, android.R.color.white);
        lvl.setTextColor(0xFFFFFFFF);
        Ui.addWrap(top, lvl, 8);
        in.addView(top);

        List<String> others = new ArrayList<>();
        boolean failedItself = false;
        for (GapNode n : gap.explains) { if (n.id.equals(gap.node.id)) failedItself = true; else others.add(n.label); }
        String cause = !others.isEmpty() ? "Nguyên nhân gốc của lỗi ở " + others.size() + " khái niệm liên hoàn" + (failedItself ? " • sai trực tiếp" : "")
                : "Sai trực tiếp: " + gap.node.wrong + " sai / " + gap.node.right + " đúng";
        LinearLayout cp = Ui.iconText(c, R.drawable.ic_fork, cause, 13, Ui.color(c, R.color.m3_error), false);
        cp.setBackground(Ui.rounded(c, R.color.rose_50, 999, 0));
        cp.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        Ui.addInline(in, cp, 8);

        // Trace path
        List<List<String>> traced = new ArrayList<>();
        for (List<String> p : gap.paths) if (p.size() > 1) traced.add(p);
        LinearLayout trace = Ui.vertical(c);
        trace.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
        trace.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        TextView tl = Ui.text(c, "CHUỖI TRUY NGUYÊN LỖI SAI (TRACE PATH)", 12, R.color.text_secondary, true);
        tl.setLetterSpacing(0.04f);
        trace.addView(tl);
        LinearLayout rootLine = Ui.horizontal(c);
        View rd = new View(c);
        rd.setBackground(Ui.rounded(c, R.color.m3_error, 999, 0));
        rootLine.addView(rd, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
        Ui.addWeighted(rootLine, Ui.text(c, "Gốc lỗi: " + gap.node.label, 14, R.color.m3_error, true), 8);
        Ui.add(trace, rootLine, 8);
        Set<String> listed = new LinkedHashSet<>();
        for (List<String> p : traced) for (int i = p.size() - 2; i >= 0; i--) listed.add(p.get(i));
        if (listed.isEmpty()) for (String o : others) listed.add(o);
        for (String id : listed) {
            GapNode n = an.nodes.get(id);
            String label = n != null ? n.label : id;
            LinearLayout r = Ui.horizontal(c);
            r.setGravity(Gravity.TOP);
            r.addView(Ui.icon(c, R.drawable.ic_chevron_down, 16, Ui.color(c, R.color.text_muted)));
            Ui.addWeighted(r, Ui.text(c, "Dẫn tới sai " + label, 13, R.color.text_secondary, false), 8);
            Ui.add(trace, r, 6);
        }
        Ui.add(in, trace, 12);

        if (!gap.misconceptions.isEmpty()) {
            LinearLayout box = Ui.vertical(c);
            box.setBackground(Ui.rounded(c, R.color.m3_surface_container, 16, 0));
            box.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            box.addView(Ui.iconText(c, R.drawable.ic_info, "Hiểu sai thường gặp (AI chẩn đoán)", 13, Ui.color(c, R.color.m3_tertiary), true));
            for (KbMisconception m : gap.misconceptions) {
                SpannableStringBuilder mb = new SpannableStringBuilder(m.misconception);
                mb.setSpan(new StrikethroughSpan(), 0, mb.length(), 0);
                if (m.correction != null && !m.correction.isEmpty()) {
                    mb.append(" → ");
                    int s1 = mb.length();
                    mb.append(m.correction);
                    mb.setSpan(new StyleSpan(Typeface.BOLD), s1, mb.length(), 0);
                }
                Ui.add(box, Ui.text(c, mb, 13, R.color.text, false), 6);
            }
            Ui.add(in, box, 12);
        }

        String quick = gap.bridge != null && !gap.bridge.isEmpty() ? gap.bridge : gap.node.description;
        if (quick != null && !quick.isEmpty()) {
            LinearLayout box = Ui.horizontal(c);
            box.setGravity(Gravity.TOP);
            box.setBackground(Ui.rounded(c, R.color.teal_50, 16, 0));
            box.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            box.addView(Ui.icon(c, R.drawable.ic_zap, 20, Ui.color(c, R.color.brand_dark)));
            LinearLayout bt = Ui.vertical(c);
            bt.addView(Ui.text(c, "Ôn nhanh 2 phút", 14, R.color.brand_dark, true));
            Ui.add(bt, Ui.text(c, quick, 13, R.color.text_secondary, false), 2);
            LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            btl.setMarginStart(Ui.dp(c, 10));
            box.addView(bt, btl);
            Ui.add(in, box, 12);
        }

        if (!gap.suspects.isEmpty()) {
            List<String> s = new ArrayList<>();
            for (GapNode n : gap.suspects) s.add(n.label);
            Ui.add(in, Ui.text(c, "◌ Kiến thức nền chưa được kiểm tra, nên kiểm tra thêm: " + String.join(", ", s), 12, R.color.text_muted, false), 10);
        }

        if ("prerequisite".equals(gap.node.kind)) {
            Ui.add(in, actionBtn(c, R.drawable.ic_clipboard, "Làm lại bài test chẩn đoán", true, v -> state.navigate(Screen.PREREQUISITE)), 14);
        } else {
            Ui.add(in, actionBtn(c, R.drawable.ic_layers, "Ôn bằng Flashcard", true, v -> state.navigate(Screen.FLASHCARD)), 14);
            int wrongQ = gap.node.wrong;
            Ui.add(in, actionBtn(c, R.drawable.ic_quiz, wrongQ > 0 ? "Làm lại " + wrongQ + " câu trắc nghiệm sai" : "Làm lại trắc nghiệm", false, v -> state.navigate(Screen.QUIZ)), 8);
        }
        TextView ask = Ui.text(c, "Hỏi gia sư Socrates: Vì sao hay nhầm \"" + gap.node.label + "\"?", 14, R.color.brand_dark, true);
        ask.setGravity(Gravity.CENTER);
        ask.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
        ask.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        ask.setOnClickListener(v -> { WorkspaceFragment.openInSocraticMode = true; state.navigate(Screen.WORKSPACE); });
        Ui.add(in, ask, 8);
        return card;
    }

    private static String kindLabel(GapNode n) {
        return "prerequisite".equals(n.kind) ? "Kiến thức nền" + (n.level != null && !n.level.isEmpty() ? " · " + n.level : "") : "Khái niệm trong bài";
    }

    // ---------- graph (layoutGraph in the web) ----------

    private static final int[][] STATUS = {
            {0xFFFFDDB8, 0xFF945D00, 0xFF2A1700}, // weak
            {0xFF9CF2E8, 0xFF005C55, 0xFF00201D}, // mastered
            {0xFFEFF4FF, 0xFFBDC9C6, 0xFF3E4947}  // untested
    };

    private Set<String> shown(KnowledgeGaps.Analysis an) {
        Set<String> shown = new LinkedHashSet<>();
        for (RootGap r : an.roots) {
            for (List<String> p : r.paths) shown.addAll(p);
            for (GapNode s : r.suspects) shown.add(s.id);
        }
        return shown;
    }

    /** Layered left→right layout: a node sits one column right of its rightmost prerequisite */
    private List<GraphView.Node> buildNodes(KnowledgeGaps.Analysis an, RootGap selected) {
        Set<String> shown = shown(an);
        Set<String> rootIds = new HashSet<>();
        for (RootGap r : an.roots) rootIds.add(r.node.id);
        Set<String> hl = new HashSet<>();
        if (selected != null) { for (List<String> p : selected.paths) hl.addAll(p); hl.add(selected.node.id); }

        Map<String, Integer> rank = new HashMap<>();
        for (String id : shown) rank.put(id, 0);
        // e = {from: dependent, to: prerequisite} → prerequisite drawn left of dependent
        for (int pass = 0; pass < shown.size(); pass++) {
            boolean changed = false;
            for (String[] e : an.requires) {
                if (!shown.contains(e[0]) || !shown.contains(e[1])) continue;
                int want = rank.get(e[1]) + 1;
                if (rank.get(e[0]) < want && want < shown.size()) { rank.put(e[0], want); changed = true; }
            }
            if (!changed) break;
        }
        Map<Integer, Integer> rowsInCol = new HashMap<>();
        List<GraphView.Node> out = new ArrayList<>();
        for (String id : shown) {
            GapNode n = an.nodes.get(id);
            int col = rank.get(id);
            int row = rowsInCol.getOrDefault(col, 0);
            rowsInCol.put(col, row + 1);
            GraphView.Node g = new GraphView.Node(id);
            g.w = 200;
            g.h = 64;
            g.x = col * (200 + 90);
            g.y = row * (64 + 30);
            boolean isRoot = rootIds.contains(id);
            int[] s = isRoot ? new int[]{0xFFFFDAD6, 0xFFBA1A1A, 0xFF93000A}
                    : STATUS["weak".equals(n.status) ? 0 : "mastered".equals(n.status) ? 1 : 2];
            g.fill = s[0];
            g.stroke = s[1];
            g.text = s[2];
            g.strokeWidth = isRoot ? 2f : 1.5f;
            g.dashed = "untested".equals(n.status) && !isRoot;
            g.title = n.label;
            String label = isRoot ? "Lỗ hổng gốc" : "weak".equals(n.status) ? "Đang yếu" : "mastered".equals(n.status) ? "Đã nắm" : "Chưa kiểm tra";
            g.subtitle = label + (n.wrong + n.right > 0 ? " · " + n.wrong + " sai / " + n.right + " đúng" : "");
            g.alpha = selected != null && !hl.contains(id) ? 0.35f : 1f;
            out.add(g);
        }
        // Center shorter columns vertically like dagre
        int maxRows = 0;
        for (int r : rowsInCol.values()) maxRows = Math.max(maxRows, r);
        for (GraphView.Node g : out) {
            int rows = rowsInCol.get(rank.get(g.id));
            g.y += (maxRows - rows) * (64 + 30) / 2f;
        }
        return out;
    }

    private List<GraphView.Edge> buildEdges(KnowledgeGaps.Analysis an, RootGap selected) {
        Set<String> shown = shown(an);
        Set<String> hl = new HashSet<>();
        if (selected != null) { for (List<String> p : selected.paths) hl.addAll(p); hl.add(selected.node.id); }
        List<GraphView.Edge> out = new ArrayList<>();
        for (String[] e : an.requires) {
            if (!shown.contains(e[0]) || !shown.contains(e[1])) continue;
            GraphView.Edge g = new GraphView.Edge(e[1], e[0]); // prerequisite → dependent
            boolean onPath = selected != null && hl.contains(e[0]) && hl.contains(e[1]);
            g.color = onPath ? 0xFFBA1A1A : 0xFF6E7977;
            g.width = onPath ? 2.5f : 1.5f;
            g.alpha = selected != null && !onPath ? 0.35f : 1f;
            out.add(g);
        }
        return out;
    }

    // ---------- small pieces ----------

    private void pill(LinearLayout row, String s, int bg, int fg, int stroke) {
        Context c = requireContext();
        TextView t = Ui.text(c, s, 11, fg, true);
        t.setBackground(Ui.rounded(c, bg, 999, stroke));
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addWrap(row, t, row.getChildCount() > 0 ? 6 : 0);
    }

    private View legendBox(Context c, int fill, int stroke, boolean dashed, String label) {
        LinearLayout row = Ui.horizontal(c);
        View box = new View(c);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(Ui.dp(c, 3));
        if (dashed) d.setStroke(Ui.dp(c, 1), stroke, Ui.dp(c, 2), Ui.dp(c, 2));
        else d.setStroke(Ui.dp(c, 2), stroke);
        box.setBackground(d);
        row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12)));
        Ui.addWrap(row, Ui.text(c, label, 10, R.color.text_muted, true), 4);
        return row;
    }

    private View centerCard(Context c, int icon, int iconColor, String title, String message, int bg, int stroke) {
        MaterialCardView card = Ui.tintedCard(c, bg, stroke);
        LinearLayout in = Ui.inner(card);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        in.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        in.addView(Ui.icon(c, icon, 32, Ui.color(c, iconColor)));
        TextView t = Ui.text(c, title, 14, R.color.text, true);
        t.setGravity(Gravity.CENTER);
        Ui.add(in, t, 10);
        TextView m = Ui.text(c, message, 12, R.color.text_muted, false);
        m.setGravity(Gravity.CENTER);
        Ui.add(in, m, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 18);
        card.setLayoutParams(lp);
        return card;
    }
}
