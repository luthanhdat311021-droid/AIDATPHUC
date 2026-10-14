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

        // ---- Header ----
        MaterialCardView head = Ui.card(c);
        LinearLayout hi = Ui.inner(head);
        hi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout title = Ui.horizontal(c);
        title.addView(Ui.iconBox(c, R.drawable.ic_network, R.color.rose, android.R.color.white, 40, 20, 12));
        LinearLayout tt = Ui.vertical(c);
        tt.addView(Ui.text(c, "Bản đồ lỗ hổng kiến thức", 17, R.color.text, true));
        TextView sub = Ui.text(c, a.document.title, 12, R.color.text_muted, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tt.addView(sub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        title.addView(tt, tl);
        hi.addView(title);
        SpannableStringBuilder intro = new SpannableStringBuilder("Mỗi câu sai được lần ngược theo chuỗi kiến thức phụ thuộc để tìm ");
        int st = intro.length();
        intro.append("chỗ hổng gốc");
        intro.setSpan(new StyleSpan(Typeface.BOLD), st, intro.length(), 0);
        intro.append(", phần mà nếu lấp lại sẽ sửa được nhiều lỗi cùng lúc. Kiến thức bạn đã chứng minh là nắm vững được loại khỏi danh sách nghi vấn.");
        Ui.add(hi, Ui.text(c, intro, 12, R.color.text_secondary, false), 12);
        if (an.evidenceCount > 0) {
            int weak = 0;
            for (GapNode n : an.nodes.values()) if ("weak".equals(n.status)) weak++;
            LinearLayout pills = Ui.horizontal(c);
            pill(pills, an.evidenceCount + " lượt luyện tập được phân tích", R.color.slate_100, R.color.text_secondary, R.color.border);
            pill(pills, weak + " phần đang yếu", R.color.amber_50, R.color.amber_700, R.color.amber_soft);
            pill(pills, an.roots.size() + " lỗ hổng gốc", R.color.rose_50, R.color.rose, R.color.rose_soft);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(pills);
            Ui.add(hi, hs, 12);
        }
        root.addView(head);

        if (an.nodes.isEmpty()) {
            root.addView(centerCard(c, R.drawable.ic_alert_tri, R.color.amber, "Bài học này chưa có đồ thị khái niệm",
                    "Hãy chuyển hóa lại tài liệu để hệ thống phân tích các khái niệm và quan hệ phụ thuộc giữa chúng.", R.color.surface, R.color.border));
            return;
        }
        if (an.evidenceCount == 0) {
            View card = centerCard(c, R.drawable.ic_target, R.color.brand, "Chưa có dữ liệu luyện tập để phân tích",
                    "Làm trắc nghiệm, ôn thẻ ghi nhớ (đánh giá Khó/Dễ) hoặc làm bài test chẩn đoán. Bản đồ sẽ tự cập nhật theo kết quả của bạn.", R.color.surface, R.color.border);
            LinearLayout in = Ui.inner((MaterialCardView) card);
            Object[][] practice = {{Screen.QUIZ, R.drawable.ic_quiz, "Làm trắc nghiệm"}, {Screen.FLASHCARD, R.drawable.ic_layers, "Ôn thẻ ghi nhớ"},
                    {Screen.PREREQUISITE, R.drawable.ic_clipboard, "Làm bài test chẩn đoán"}};
            for (Object[] p : practice) {
                LinearLayout b = Ui.pillButton(c, (int) p[1], (String) p[2], R.color.surface, R.color.text_secondary, R.color.border, v -> state.navigate((Screen) p[0]));
                ((android.widget.ImageView) b.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
                Ui.add(in, b, 8);
            }
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

        // ---- Dependency graph (first on a phone so the selection is visible while scrolling cards) ----
        Ui.add(root, Ui.iconText(c, R.drawable.ic_book, "CHUỖI PHỤ THUỘC: KIẾN THỨC NỀN → KIẾN THỨC DỰA TRÊN NÓ", 11, Ui.color(c, R.color.brand), true), 20);
        FrameLayout graphBox = new FrameLayout(c);
        graphBox.setBackground(Ui.rounded(c, R.color.surface, 16, R.color.border));
        graphBox.setClipToOutline(true);
        GraphView graph = new GraphView(c);
        graph.setGraph(buildNodes(an, selected), buildEdges(an, selected), false);
        graph.setOnNodeClick(n -> {
            for (RootGap r : an.roots) if (r.node.id.equals(n.id)) { selectedId = n.id; rerender(); }
        });
        graphBox.addView(graph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout zoom = Ui.vertical(c);
        zoom.setBackground(Ui.rounded(c, R.color.surface, 8, R.color.border));
        for (String[] z : new String[][]{{"+", "1.25"}, {"−", "0.8"}, {"⤢", "fit"}}) {
            TextView b = Ui.text(c, z[0], 16, R.color.text_secondary, true);
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> { if (z[1].equals("fit")) graph.fit(); else graph.zoom(Float.parseFloat(z[1])); });
            zoom.addView(b, new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
        }
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        zl.setMargins(Ui.dp(c, 10), 0, 0, Ui.dp(c, 10));
        graphBox.addView(zoom, zl);
        graphBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 360)));
        Ui.add(root, graphBox, 8);

        LinearLayout legend = Ui.horizontal(c);
        legend.addView(legendBox(c, 0xFFFFF1F2, 0xFFE11D48, false, "Lỗ hổng gốc"));
        Ui.addWrap(legend, legendBox(c, 0xFFFFFBEB, 0xFFF59E0B, false, "Đang yếu"), 10);
        Ui.addWrap(legend, legendBox(c, 0xFFECFDF5, 0xFF10B981, false, "Đã nắm"), 10);
        Ui.addWrap(legend, legendBox(c, 0xFFF8FAFC, 0xFF94A3B8, true, "Chưa kiểm tra"), 10);
        HorizontalScrollView ls = new HorizontalScrollView(c);
        ls.setHorizontalScrollBarEnabled(false);
        ls.addView(legend);
        Ui.add(root, ls, 8);

        // ---- Ranked root-gap cards ----
        Ui.add(root, Ui.iconText(c, R.drawable.ic_target, "LỖ HỔNG GỐC, XẾP THEO MỨC ẢNH HƯỞNG", 11, Ui.color(c, R.color.rose), true), 22);
        for (int i = 0; i < an.roots.size(); i++) Ui.add(root, gapCard(c, an.roots.get(i), i + 1, an), 12);
    }

    // ---------- root gap card ----------

    private View gapCard(Context c, RootGap gap, int rank, KnowledgeGaps.Analysis an) {
        boolean sel = gap.node.id.equals(selectedId);
        MaterialCardView card = Ui.card(c);
        if (sel) {
            card.setStrokeColor(0xFFFB7185);
            card.setStrokeWidth(Ui.dp(c, 2));
        }
        card.setOnClickListener(v -> { selectedId = gap.node.id; rerender(); scroll.post(() -> scroll.smoothScrollTo(0, 0)); });
        LinearLayout in = Ui.inner(card);

        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        TextView num = Ui.text(c, String.valueOf(rank), 12, android.R.color.white, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.rounded(c, R.color.rose, 999, 0));
        top.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        LinearLayout body = Ui.vertical(c);
        body.addView(Ui.text(c, kindLabel(gap.node).toUpperCase(new java.util.Locale("vi")), 10, R.color.rose, true));
        body.addView(Ui.text(c, gap.node.label, 14, R.color.text, true));
        List<String> others = new ArrayList<>();
        boolean failedItself = false;
        for (GapNode n : gap.explains) { if (n.id.equals(gap.node.id)) failedItself = true; else others.add(n.label); }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!others.isEmpty()) {
            sb.append("Là nguyên nhân gốc của lỗi ở ");
            int s0 = sb.length();
            sb.append(others.size() + " khái niệm");
            sb.setSpan(new StyleSpan(Typeface.BOLD), s0, sb.length(), 0);
            sb.append(": " + String.join(", ", others) + ".");
            if (failedItself) sb.append(" Bạn cũng trả lời sai trực tiếp phần này.");
        } else {
            sb.append("Bạn trả lời sai trực tiếp phần này (" + gap.node.wrong + " sai / " + gap.node.right + " đúng) và chưa có dấu hiệu yếu ở kiến thức nền.");
        }
        Ui.add(body, Ui.text(c, sb, 12, R.color.text_secondary, false), 2);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bl.setMarginStart(Ui.dp(c, 12));
        top.addView(body, bl);
        in.addView(top);

        List<List<String>> traced = new ArrayList<>();
        for (List<String> p : gap.paths) if (p.size() > 1) traced.add(p);
        if (!traced.isEmpty()) {
            Ui.add(in, Ui.text(c, "ĐƯỜNG TRUY NGUYÊN", 10, R.color.slate_400, true), 12);
            for (List<String> p : traced) {
                LinearLayout chain = Ui.horizontal(c);
                for (int i = 0; i < p.size(); i++) {
                    GapNode n = an.nodes.get(p.get(i));
                    boolean isRoot = i == p.size() - 1;
                    if (i > 0) Ui.addWrap(chain, Ui.text(c, " cần › ", 11, R.color.slate_400, false), 0);
                    int bg = isRoot ? R.color.rose_50 : "weak".equals(n.status) ? R.color.amber_50 : R.color.slate_50;
                    int fg = isRoot ? R.color.rose : "weak".equals(n.status) ? R.color.amber_700 : R.color.text_secondary;
                    int stroke = isRoot ? R.color.rose_soft : "weak".equals(n.status) ? R.color.amber_soft : R.color.border;
                    TextView chip = Ui.text(c, n.label, 11, fg, true);
                    chip.setBackground(Ui.rounded(c, bg, 6, stroke));
                    chip.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
                    chain.addView(chip);
                }
                HorizontalScrollView hs = new HorizontalScrollView(c);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(chain);
                Ui.add(in, hs, 6);
            }
        }

        if (!gap.misconceptions.isEmpty()) {
            LinearLayout box = Ui.vertical(c);
            box.setBackground(Ui.rounded(c, R.color.amber_50, 12, R.color.amber_soft));
            box.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            box.addView(Ui.iconText(c, R.drawable.ic_alert_tri, "HIỂU SAI THƯỜNG GẶP", 10, Ui.color(c, R.color.amber_700), true));
            for (KbMisconception m : gap.misconceptions) {
                SpannableStringBuilder mb = new SpannableStringBuilder(m.misconception);
                mb.setSpan(new StrikethroughSpan(), 0, mb.length(), 0);
                if (m.correction != null && !m.correction.isEmpty()) {
                    mb.append(" → ");
                    int s1 = mb.length();
                    mb.append(m.correction);
                    mb.setSpan(new StyleSpan(Typeface.BOLD), s1, mb.length(), 0);
                }
                TextView t = Ui.text(c, mb, 12, R.color.text, false);
                t.setTextColor(0xFF451A03);
                Ui.add(box, t, 4);
            }
            Ui.add(in, box, 12);
        }

        if (gap.bridge != null && !gap.bridge.isEmpty()) {
            LinearLayout box = Ui.vertical(c);
            box.setBackground(Ui.rounded(c, R.color.teal_50, 12, R.color.brand_soft));
            box.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            box.addView(Ui.text(c, "ÔN NHANH 2 PHÚT", 10, R.color.brand, true));
            Ui.add(box, Ui.text(c, gap.bridge, 12, R.color.text_secondary, false), 2);
            Ui.add(in, box, 12);
        } else if (gap.node.description != null && !gap.node.description.isEmpty()) {
            TextView d = Ui.text(c, gap.node.description, 12, R.color.text_secondary, false);
            d.setBackground(Ui.rounded(c, R.color.slate_50, 12, R.color.slate_100));
            d.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            Ui.add(in, d, 12);
        }

        if (!gap.suspects.isEmpty()) {
            List<String> s = new ArrayList<>();
            for (GapNode n : gap.suspects) s.add(n.label);
            Ui.add(in, Ui.text(c, "◌ Kiến thức nền chưa được kiểm tra, nên kiểm tra thêm: " + String.join(", ", s), 11, R.color.text_muted, false), 10);
        }

        LinearLayout actions = Ui.horizontal(c);
        if ("prerequisite".equals(gap.node.kind)) {
            LinearLayout b = Ui.pillButton(c, R.drawable.ic_clipboard, "Làm lại bài test chẩn đoán", R.color.brand, android.R.color.white, 0, v -> state.navigate(Screen.PREREQUISITE));
            b.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            actions.addView(b);
        } else {
            LinearLayout f = Ui.pillButton(c, R.drawable.ic_layers, "Ôn bằng thẻ ghi nhớ", R.color.brand, android.R.color.white, 0, v -> state.navigate(Screen.FLASHCARD));
            f.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            actions.addView(f);
            Ui.addWrap(actions, Ui.pillButton(c, R.drawable.ic_quiz, "Làm lại trắc nghiệm", R.color.surface, R.color.text_secondary, R.color.border, v -> state.navigate(Screen.QUIZ)), 8);
        }
        HorizontalScrollView as = new HorizontalScrollView(c);
        as.setHorizontalScrollBarEnabled(false);
        as.addView(actions);
        Ui.add(in, as, 12);
        return card;
    }

    private static String kindLabel(GapNode n) {
        return "prerequisite".equals(n.kind) ? "Kiến thức nền" + (n.level != null && !n.level.isEmpty() ? " · " + n.level : "") : "Khái niệm trong bài";
    }

    // ---------- graph (layoutGraph in the web) ----------

    private static final int[][] STATUS = {
            {0xFFFFFBEB, 0xFFF59E0B, 0xFF78350F}, // weak
            {0xFFECFDF5, 0xFF10B981, 0xFF064E3B}, // mastered
            {0xFFF8FAFC, 0xFF94A3B8, 0xFF334155}  // untested
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
            int[] s = isRoot ? new int[]{0xFFFFF1F2, 0xFFE11D48, 0xFF881337}
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
            g.color = onPath ? 0xFFE11D48 : 0xFF94A3B8;
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
