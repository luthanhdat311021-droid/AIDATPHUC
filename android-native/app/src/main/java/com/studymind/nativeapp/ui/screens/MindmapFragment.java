package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.AIMindmap;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.MindmapEdge;
import com.studymind.nativeapp.data.model.Models.MindmapNode;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.GraphView;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.Modal;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Deep Knowledge Mindmap AI" — MindmapView.tsx: toolbar (search, tiers, type filter, add, export),
 * a pan/zoom canvas balanced left/right around the root, and the selected-node inspector below it.
 */
public class MindmapFragment extends BaseFragment {
    private static final String ROOT = "root-node";
    private static final Map<String, String> TYPE_LABEL = new LinkedHashMap<>();
    static {
        String[][] t = {{"topic", "Chủ đề chính"}, {"subtopic", "Chủ đề phụ"}, {"concept", "Khái niệm"}, {"definition", "Định nghĩa"},
                {"process", "Quy trình"}, {"method", "Phương pháp"}, {"formula", "Công thức"}, {"example", "Ví dụ"}, {"fact", "Sự thật"},
                {"comparison", "So sánh"}, {"advantage", "Ưu điểm"}, {"disadvantage", "Nhược điểm"}, {"application", "Ứng dụng"},
                {"warning", "Lưu ý"}, {"important", "Trọng tâm"}};
        for (String[] p : t) TYPE_LABEL.put(p[0], p[1]);
    }
    private static final String[][] FILTERS = {{"all", "Tất cả loại nút"}, {"important", "Mức quan trọng (4-5)"}, {"subtopic", "Chủ đề con"},
            {"concept", "Khái niệm"}, {"definition", "Định nghĩa"}, {"formula", "Công thức"}, {"process", "Quy trình"}, {"example", "Ví dụ"}};
    private static final String[][] EDIT_TYPES = {{"subtopic", "Chủ đề con"}, {"concept", "Khái niệm"}, {"definition", "Định nghĩa"},
            {"formula", "Công thức"}, {"process", "Quy trình"}, {"example", "Ví dụ"}, {"application", "Ứng dụng"}};
    private static final String[] IMPORTANCE = {"Mức 5 (Cực kỳ quan trọng)", "Mức 4 (Quan trọng)", "Mức 3 (Khái niệm chính)", "Mức 2 (Kiến thức phụ)", "Mức 1 (Ví dụ / Chi tiết)"};

    private String docKey;
    private String search = "";
    private String filter = "all";
    private int visibleLevel = -1; // -1 = all
    private final Set<String> collapsed = new HashSet<>();
    private final Set<String> expandedByAi = new HashSet<>();
    private String selectedId;
    private GraphView graph;
    private boolean keepView;

    // derived each render
    private final Map<String, MindmapNode> byId = new LinkedHashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Map<String, Integer> level = new HashMap<>();
    private int maxLevel = 1;

    @Override
    protected Object watchKey() { return state.activeDoc(); }

    private static final String[][] CHIP_FILTERS = {{"all", "Tất cả"}, {"definition", "Định nghĩa"}, {"formula", "Công thức"}, {"process", "Quy trình"},
            {"example", "Ví dụ"}, {"concept", "Khái niệm"}, {"subtopic", "Chủ đề con"}, {"important", "Quan trọng"}};

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "xem sơ đồ tư duy")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        AIMindmap mm = a.pack.mindmap();
        String rootLabel = mm != null && mm.rootLabel != null ? mm.rootLabel : mm != null && mm.root != null ? mm.root.label : a.document.title;
        buildTree(mm, rootLabel);

        // Tier view: deep maps open at level 2 so 100 nodes don't render at once
        if (!a.document.id.equals(docKey)) {
            docKey = a.document.id;
            collapsed.clear();
            expandedByAi.clear();
            keepView = false;
            Integer fromBackend = mm != null && mm.metadata != null ? mm.metadata.defaultVisibleLevel : null;
            visibleLevel = fromBackend != null ? (fromBackend >= maxLevel ? -1 : fromBackend) : (byId.size() > 40 && maxLevel > 2 ? 2 : -1);
            applyTier();
            List<String> first = children.getOrDefault(ROOT, new ArrayList<>());
            selectedId = first.isEmpty() ? ROOT : first.get(0);
        }
        if (!byId.containsKey(selectedId)) selectedId = ROOT;
        root.setPadding(0, 0, 0, 0);

        // ---- Toolbar ----
        LinearLayout bar = Ui.vertical(c);
        bar.setBackgroundColor(Ui.color(c, R.color.m3_surface_low));
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 10));
        LinearLayout title = Ui.horizontal(c);
        title.addView(Ui.icon(c, R.drawable.ic_mindmap, 22, Ui.color(c, R.color.text_secondary)));
        TextView tt = Ui.text(c, "Bản đồ: " + rootLabel, 17, R.color.text, true);
        tt.setMaxLines(1);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ttl.setMarginStart(Ui.dp(c, 8));
        title.addView(tt, ttl);
        LinearLayout export = Ui.iconText(c, R.drawable.ic_download, "Xuất PNG", 14, 0xFFFFFFFF, true);
        export.setBackground(Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E));
        export.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9));
        export.setOnClickListener(v -> exportImage(rootLabel));
        Ui.addWrap(title, export, 8);
        bar.addView(title);

        LinearLayout tools = Ui.horizontal(c);
        LinearLayout sbox = Ui.horizontal(c);
        sbox.setBackground(Ui.rounded(c, R.color.m3_surface_high, 999, 0));
        sbox.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 10), 0);
        sbox.addView(Ui.icon(c, R.drawable.ic_search, 18, Ui.color(c, R.color.text_secondary)));
        EditText searchField = new EditText(c);
        searchField.setHint("Tìm nút, định lý, công thức...");
        searchField.setTextSize(13);
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setTextColor(Ui.color(c, R.color.text));
        searchField.setHintTextColor(Ui.color(c, R.color.text_muted));
        searchField.setText(search);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a1, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { search = s.toString(); refreshGraph(true); }
        });
        LinearLayout.LayoutParams sfl = new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f);
        sfl.setMarginStart(Ui.dp(c, 6));
        sbox.addView(searchField, sfl);
        Ui.addWeighted(tools, sbox, 0);
        LinearLayout add = Ui.iconText(c, R.drawable.ic_plus, "Nhánh", 14, 0xFFFFFFFF, true);
        add.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
        add.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        add.setOnClickListener(v -> nodeDialog(null, selectedId));
        Ui.addWrap(tools, add, 10);
        Ui.add(bar, tools, 10);

        LinearLayout chips = Ui.horizontal(c);
        for (String[] f : CHIP_FILTERS) {
            boolean on = filter.equals(f[0]);
            TextView t = Ui.text(c, f[1], 13, on ? android.R.color.white : R.color.text_secondary, on);
            if (on) t.setTextColor(0xFFFFFFFF);
            t.setBackground(on ? Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55) : Ui.rounded(c, R.color.m3_surface_container, 999, 0));
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
            t.setOnClickListener(v -> { filter = f[0]; keepView = false; rerender(); });
            Ui.addWrap(chips, t, chips.getChildCount() > 0 ? 8 : 0);
        }
        if (maxLevel > 1) {
            View sep = new View(c);
            sep.setBackgroundColor(Ui.color(c, R.color.m3_outline_variant));
            LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(Ui.dp(c, 1), Ui.dp(c, 20));
            spl.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 2), 0);
            chips.addView(sep, spl);
            List<Integer> lv = new ArrayList<>();
            for (int i = 1; i <= Math.min(maxLevel - 1, 5); i++) lv.add(i);
            lv.add(-1);
            for (int l : lv) {
                boolean on = visibleLevel == l;
                TextView t = Ui.text(c, l == -1 ? "Mọi tầng" : "Tầng " + l, 13, on ? R.color.brand : R.color.text_secondary, on);
                t.setBackground(Ui.rounded(c, on ? R.color.m3_primary_fixed : R.color.surface, 999, on ? 0 : R.color.border));
                t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
                t.setOnClickListener(v -> { visibleLevel = l; applyTier(); keepView = false; rerender(); });
                Ui.addWrap(chips, t, 8);
            }
        }
        HorizontalScrollView cs = new HorizontalScrollView(c);
        cs.setHorizontalScrollBarEnabled(false);
        cs.addView(chips);
        Ui.add(bar, cs, 10);
        root.addView(bar);

        LinearLayout info = Ui.horizontal(c);
        info.setBackgroundColor(Ui.color(c, R.color.surface));
        info.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        info.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
        String depthLabel = mm != null && mm.metadata != null ? mm.metadata.depthLabel : null;
        TextView it = Ui.text(c, (byId.size() - 1) + " nút phân cấp • " + maxLevel + " tầng chi tiết" + (depthLabel != null ? " • " + depthLabel : ""), 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams itl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        itl.setMarginStart(Ui.dp(c, 8));
        info.addView(it, itl);
        info.addView(Ui.iconText(c, R.drawable.ic_sparkles, "Đồng bộ AI", 13, Ui.color(c, R.color.brand), false));
        root.addView(info);

        // ---- Canvas ----
        FrameLayout canvas = new FrameLayout(c);
        canvas.setBackgroundColor(Ui.color(c, R.color.bg));
        graph = new GraphView(c);
        graph.setDotColor(Ui.color(c, R.color.m3_outline_variant));
        graph.setOnNodeClick(n -> { selectedId = n.id; keepView = true; rerender(); });
        canvas.addView(graph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout hintBox = Ui.iconText(c, R.drawable.ic_target, "1 ngón kéo • 2 ngón zoom • Chạm nút xem", 12, 0xFFEAF1FF, false);
        hintBox.setBackground(Ui.gradient(c, 999, 0xCC213145, 0xCC213145));
        hintBox.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        hl.setMargins(Ui.dp(c, 12), 0, 0, Ui.dp(c, 12));
        canvas.addView(hintBox, hl);
        LinearLayout zoom = Ui.vertical(c);
        Object[][] zs = {{R.drawable.ic_plus, "1.25"}, {R.drawable.ic_minus, "0.8"}, {R.drawable.ic_fit, "fit"}};
        for (Object[] z : zs) {
            FrameLayout b = Ui.iconBox(c, (int) z[0], R.color.surface, R.color.text, 44, 22, 14);
            b.setElevation(Ui.dp(c, 3));
            String act = (String) z[1];
            b.setOnClickListener(v -> { if (act.equals("fit")) graph.fit(); else graph.zoom(Float.parseFloat(act)); });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44));
            bl.bottomMargin = Ui.dp(c, 10);
            zoom.addView(b, bl);
        }
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        zl.setMargins(0, Ui.dp(c, 12), Ui.dp(c, 12), 0);
        canvas.addView(zoom, zl);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
        root.addView(canvas, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        refreshGraph(keepView);
        keepView = false;

        renderInspector(root, rootLabel);
    }

    // ---------- tree ----------

    private void buildTree(AIMindmap mm, String rootLabel) {
        byId.clear();
        children.clear();
        level.clear();
        MindmapNode r = new MindmapNode();
        r.id = ROOT;
        r.label = rootLabel;
        r.type = "topic";
        r.importance = 5;
        r.summary = mm != null && mm.root != null && mm.root.summary != null ? mm.root.summary : "Sơ đồ tư duy bài học " + rootLabel;
        byId.put(ROOT, r);
        String backendRoot = mm != null && mm.root != null ? mm.root.id : null;
        if (mm != null) for (MindmapNode n : mm.nodes) if (!n.id.equals(ROOT) && !n.id.equals(backendRoot)) byId.put(n.id, n);
        for (MindmapNode n : byId.values()) {
            if (n.id.equals(ROOT)) continue;
            String p = n.parentId != null && byId.containsKey(n.parentId) && !n.parentId.equals(n.id) ? n.parentId : ROOT;
            children.computeIfAbsent(p, k -> new ArrayList<>()).add(n.id);
        }
        // True depth from parent links (stored "level" values can be stale after edits)
        maxLevel = 1;
        ArrayList<String> queue = new ArrayList<>();
        queue.add(ROOT);
        level.put(ROOT, 0);
        for (int i = 0; i < queue.size(); i++) {
            String id = queue.get(i);
            for (String ch : children.getOrDefault(id, new ArrayList<>())) {
                if (level.containsKey(ch)) continue;
                level.put(ch, level.get(id) + 1);
                maxLevel = Math.max(maxLevel, level.get(ch));
                queue.add(ch);
            }
        }
    }

    /** Fold every node at the chosen tier so only levels 1..visibleLevel show */
    private void applyTier() {
        collapsed.clear();
        if (visibleLevel == -1) return;
        for (String id : byId.keySet()) {
            if (level.getOrDefault(id, 1) == visibleLevel && children.containsKey(id)) collapsed.add(id);
        }
    }

    private boolean passesFilter(MindmapNode n) {
        if (n.id.equals(ROOT) || filter.equals("all")) return true;
        if (filter.equals("important")) return (n.importance != null ? n.importance : 3) >= 4;
        return filter.equals(n.type);
    }

    private int countDescendants(String id) {
        int c = 0;
        for (String ch : children.getOrDefault(id, new ArrayList<>())) c += 1 + countDescendants(ch);
        return c;
    }

    // ---------- graph layout ----------

    private static final float W = 240, H = 84, VGAP = 22, HGAP = 70, RW = 280, RH = 96;

    private void refreshGraph(boolean keep) {
        if (graph == null) return;
        Context c = requireContext();
        List<GraphView.Node> nodes = new ArrayList<>();
        List<GraphView.Edge> edges = new ArrayList<>();
        Map<String, GraphView.Node> placed = new HashMap<>();

        GraphView.Node r = new GraphView.Node(ROOT);
        r.w = RW;
        r.h = RH;
        r.x = -RW / 2;
        r.y = -RH / 2;
        r.fill = 0xFF005C55;
        r.stroke = 0xFF0F766E;
        r.strokeWidth = 3;
        r.text = 0xFFFFFFFF;
        r.title = byId.get(ROOT).label;
        r.subtitle = "GỐC • " + children.getOrDefault(ROOT, new ArrayList<>()).size() + " phân nhánh";
        r.titleSp = 14;
        nodes.add(r);
        placed.put(ROOT, r);

        // Level-1 branches go to the lighter side so the map stays balanced left/right
        List<String> first = visibleChildren(ROOT);
        List<String> right = new ArrayList<>(), left = new ArrayList<>();
        int rw = 0, lw = 0;
        for (String id : first) {
            int weight = Math.max(1, leaves(id));
            if (rw <= lw) { right.add(id); rw += weight; } else { left.add(id); lw += weight; }
        }
        layoutSide(right, 1, rw, nodes, placed);
        layoutSide(left, -1, lw, nodes, placed);

        String q = search.trim().toLowerCase(Locale.ROOT);
        for (GraphView.Node g : nodes) {
            if (g.id.equals(ROOT)) continue;
            MindmapNode n = byId.get(g.id);
            boolean match = !q.isEmpty() && n.label.toLowerCase(Locale.ROOT).contains(q);
            boolean sel = g.id.equals(selectedId);
            g.fill = match ? 0xFFFFFBEB : sel ? 0xFF0F766E : Ui.color(c, R.color.surface);
            g.stroke = match ? 0xFFF59E0B : sel ? 0xFF005C55 : Ui.color(c, R.color.border);
            g.strokeWidth = match || sel ? 2.5f : 1.5f;
            g.text = sel && !match ? 0xFFFFFFFF : Ui.color(c, R.color.text);
            g.title = n.label;
            int imp = n.importance != null ? n.importance : 3;
            int hidden = collapsed.contains(g.id) ? countDescendants(g.id) : 0;
            g.subtitle = TYPE_LABEL.getOrDefault(n.type != null ? n.type : "concept", "Khái niệm")
                    + (imp >= 4 ? " • ★ " + imp + "/5" : "") + (hidden > 0 ? " • ▸ " + hidden + " nút con" : "");
            g.titleSp = 12;
        }
        if (selectedId != null && selectedId.equals(ROOT)) r.stroke = 0xFF5EEAD4;

        for (GraphView.Node g : nodes) {
            if (g.id.equals(ROOT)) continue;
            String parent = parentOf(g.id);
            if (!placed.containsKey(parent)) continue;
            GraphView.Edge e = new GraphView.Edge(parent, g.id);
            e.color = 0xFF0F766E;
            e.width = 2.5f;
            edges.add(e);
        }
        AIMindmap mm = state.activeDoc().pack.mindmap();
        if (mm != null) {
            for (MindmapEdge me : mm.edges) {
                if (me.type == null || me.type.equals("contains")) continue;
                if (!placed.containsKey(me.source) || !placed.containsKey(me.target)) continue;
                GraphView.Edge e = new GraphView.Edge(me.source, me.target);
                e.color = 0xFFD97706;
                e.width = 2f;
                e.dashed = true;
                e.arrow = false;
                edges.add(e);
            }
        }
        graph.setGraph(nodes, edges, keep);
        // Search: centre on the first match like the web's setCenter
        if (!q.isEmpty()) {
            for (GraphView.Node g : nodes) if (!g.id.equals(ROOT) && g.title.toLowerCase(Locale.ROOT).contains(q)) { selectedId = g.id; break; }
        }
    }

    private String parentOf(String id) {
        for (Map.Entry<String, List<String>> e : children.entrySet()) if (e.getValue().contains(id)) return e.getKey();
        return ROOT;
    }

    private List<String> visibleChildren(String id) {
        List<String> out = new ArrayList<>();
        if (collapsed.contains(id) && !id.equals(ROOT)) return out;
        for (String ch : children.getOrDefault(id, new ArrayList<>())) if (passesFilter(byId.get(ch)) || filter.equals("all")) out.add(ch);
        if (!filter.equals("all")) {
            // Filtered views keep matching descendants reachable through hidden parents
            out.clear();
            collectMatches(id, out, new HashSet<>());
        }
        return out;
    }

    private void collectMatches(String id, List<String> out, Set<String> seen) {
        for (String ch : children.getOrDefault(id, new ArrayList<>())) {
            if (!seen.add(ch)) continue;
            if (passesFilter(byId.get(ch))) out.add(ch);
            else if (!collapsed.contains(ch)) collectMatches(ch, out, seen);
        }
    }

    private int leaves(String id) {
        List<String> kids = visibleChildren(id);
        if (kids.isEmpty()) return 1;
        int n = 0;
        for (String k : kids) n += leaves(k);
        return n;
    }

    private void layoutSide(List<String> ids, int dir, int totalLeaves, List<GraphView.Node> out, Map<String, GraphView.Node> placed) {
        float y = -(totalLeaves * (H + VGAP) - VGAP) / 2f;
        for (String id : ids) y = place(id, 1, dir, y, out, placed, new HashSet<>());
    }

    /** Places a subtree; returns the next free y. Parents are centred on their children. */
    private float place(String id, int depth, int dir, float y, List<GraphView.Node> out, Map<String, GraphView.Node> placed, Set<String> seen) {
        if (!seen.add(id)) return y;
        List<String> kids = visibleChildren(id);
        GraphView.Node g = new GraphView.Node(id);
        g.w = W;
        g.h = H;
        float x = RW / 2 + HGAP + (depth - 1) * (W + HGAP);
        g.x = dir > 0 ? x : -x - W;
        float startY = y;
        if (kids.isEmpty()) {
            g.y = y;
            y += H + VGAP;
        } else {
            for (String k : kids) y = place(k, depth + 1, dir, y, out, placed, seen);
            g.y = (startY + y - VGAP - H) / 2f;
        }
        out.add(g);
        placed.put(id, g);
        return y;
    }

    // ---------- inspector ----------
    // ---------- inspector (Stitch node bottom sheet) ----------

    private void renderInspector(LinearLayout root, String rootLabel) {
        Context c = requireContext();
        MindmapNode n = byId.get(selectedId);
        LinearLayout panel = Ui.vertical(c);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.color(c, R.color.surface));
        float r = Ui.dp(c, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setElevation(Ui.dp(c, 8));
        panel.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 24));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = -Ui.dp(c, 20);
        root.addView(panel, pl);

        View handle = new View(c);
        handle.setBackground(Ui.rounded(c, R.color.m3_outline_variant, 999, 0));
        LinearLayout.LayoutParams hdl = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4));
        hdl.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, hdl);

        int imp = n.importance != null ? n.importance : 3;
        LinearLayout head = Ui.horizontal(c);
        head.setGravity(Gravity.TOP);
        LinearLayout pills = Ui.vertical(c);
        Ui.addInline(pills, Ui.pill(c, TYPE_LABEL.getOrDefault(n.type != null ? n.type : "concept", "Khái niệm"), R.color.m3_primary_fixed, R.color.brand_dark), 0);
        int lv = level.getOrDefault(n.id, 0);
        Ui.addInline(pills, Ui.pill(c, n.id.equals(ROOT) ? "Nút gốc trung tâm" : "Cấp độ " + imp + " / Tầng " + lv, R.color.m3_surface_container, R.color.text_secondary), 6);
        Ui.addWeighted(head, pills, 0);
        LinearLayout stars = Ui.vertical(c);
        stars.setGravity(Gravity.END);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < imp ? "★" : "☆");
        stars.addView(Ui.text(c, sb.toString(), 18, R.color.m3_tertiary, false));
        stars.addView(Ui.text(c, "Trọng tâm thi cử " + (imp * 20 - (imp == 5 ? 5 : 0)) + "%", 12, R.color.m3_tertiary, false));
        head.addView(stars);
        Ui.add(panel, head, 14);

        Ui.add(panel, Ui.text(c, n.label, 22, R.color.text, true), 10);

        LinearLayout desc = Ui.vertical(c);
        desc.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
        desc.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        LinearLayout dh = Ui.horizontal(c);
        Ui.addWeighted(dh, Ui.iconText(c, R.drawable.ic_brain, "Mô tả & Giải thích AI", 14, Ui.color(c, R.color.brand_dark), true), 0);
        dh.addView(Ui.text(c, "Cập nhật bởi StudyMind AI", 11, R.color.text_muted, false));
        desc.addView(dh);
        String d = n.summary != null && !n.summary.isEmpty() ? n.summary : n.detail != null && !n.detail.isEmpty() ? n.detail : "Không có mô tả bổ sung cho nút này.";
        TextView dt = Ui.text(c, d, 15, R.color.text, false);
        dt.setLineSpacing(0, 1.35f);
        Ui.add(desc, dt, 10);
        if (n.detail != null && !n.detail.isEmpty() && n.summary != null && !n.summary.isEmpty() && !n.detail.equals(n.summary)) {
            TextView formula = Ui.text(c, n.detail, 13, R.color.text, false);
            formula.setTypeface(android.graphics.Typeface.MONOSPACE);
            formula.setGravity(Gravity.CENTER);
            formula.setBackground(Ui.rounded(c, R.color.surface, 12, 0));
            formula.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            Ui.add(desc, formula, 10);
        }
        Ui.add(panel, desc, 14);

        if (n.subDetails != null && !n.subDetails.isEmpty()) {
            com.google.android.material.chip.ChipGroup kw = new com.google.android.material.chip.ChipGroup(c);
            kw.setChipSpacingHorizontal(Ui.dp(c, 6));
            kw.setChipSpacingVertical(Ui.dp(c, 6));
            TextView kl = Ui.text(c, "Từ khóa:", 13, R.color.text_muted, false);
            kl.setPadding(0, Ui.dp(c, 4), Ui.dp(c, 4), 0);
            kw.addView(kl);
            for (String s : n.subDetails) {
                TextView t = Ui.text(c, "#" + s.replace(" ", ""), 13, R.color.text, false);
                t.setBackground(Ui.rounded(c, R.color.m3_surface_low, 8, 0));
                t.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
                kw.addView(t);
            }
            Ui.add(panel, kw, 12);
        }

        LinearLayout expand = Ui.iconText(c, R.drawable.ic_sparkles, "Mở rộng & Đào sâu bằng AI", 15, 0xFFFFFFFF, true);
        TextView gem = Ui.pill(c, "GEMINI", R.color.m3_tertiary_fixed, R.color.m3_on_tertiary_fixed);
        Ui.addWrap(expand, gem, 8);
        expand.setGravity(Gravity.CENTER);
        expand.setBackground(Ui.gradient(c, 18, 0xFF005C55, 0xFF0F766E));
        expand.setMinimumHeight(Ui.dp(c, 54));
        expand.setOnClickListener(v -> expand(n));
        Ui.add(panel, expand, 16);

        LinearLayout row1 = Ui.horizontal(c);
        Ui.addWeighted(row1, gridBtn(c, R.drawable.ic_layers, "Tạo Flashcard", v -> state.navigate(Screen.FLASHCARD)), 0);
        Ui.addWeighted(row1, gridBtn(c, R.drawable.ic_quiz, "Tạo trắc nghiệm", v -> state.navigate(Screen.QUIZ)), 10);
        Ui.add(panel, row1, 10);
        LinearLayout row2 = Ui.horizontal(c);
        if (children.containsKey(n.id) && !n.id.equals(ROOT)) {
            boolean folded = collapsed.contains(n.id);
            int kids = children.get(n.id).size();
            Ui.addWeighted(row2, gridBtn(c, folded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right,
                    folded ? "Mở rộng " + kids + " nút con" : "Thu gọn " + kids + " nút con",
                    v -> { if (folded) collapsed.remove(n.id); else collapsed.add(n.id); keepView = true; rerender(); }), 0);
        } else {
            Ui.addWeighted(row2, gridBtn(c, R.drawable.ic_plus, "Thêm nút con", v -> nodeDialog(null, n.id)), 0);
        }
        Ui.addWeighted(row2, gridBtn(c, R.drawable.ic_edit, "Sửa nội dung", v -> nodeDialog(n, null)), 10);
        Ui.add(panel, row2, 10);

        LinearLayout foot = Ui.horizontal(c);
        if (children.containsKey(n.id) && !n.id.equals(ROOT)) {
            TextView addChild = Ui.text(c, "+ Thêm nút con", 13, R.color.brand, true);
            addChild.setOnClickListener(v -> nodeDialog(null, n.id));
            foot.addView(addChild);
        }
        foot.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout del = Ui.iconText(c, R.drawable.ic_trash, "Xóa nhánh này khỏi sơ đồ", 13, Ui.color(c, R.color.m3_error), false);
        del.setOnClickListener(v -> {
            if (n.id.equals(ROOT)) { toast("Không thể xóa nút gốc trung tâm!"); return; }
            new MaterialAlertDialogBuilder(c).setMessage("Xóa nút \"" + n.label + "\" khỏi sơ đồ tư duy?")
                    .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (dd, w) -> state.deleteMindmapNode(n.id)).show();
        });
        foot.addView(del);
        Ui.add(panel, foot, 16);
    }

    private View gridBtn(Context c, int icon, String label, View.OnClickListener l) {
        LinearLayout b = Ui.iconText(c, icon, label, 14, Ui.color(c, R.color.text), false);
        ((ImageView) b.getChildAt(0)).setColorFilter(Ui.color(c, R.color.text_secondary));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(c, R.color.m3_surface_container, 16, 0));
        b.setMinimumHeight(Ui.dp(c, 50));
        b.setOnClickListener(l);
        return b;
    }

    private void expand(MindmapNode n) {
        if (expandedByAi.contains(n.id) || children.containsKey(n.id)) {
            toast("Đã đào sâu và mở rộng nút \"" + n.label + "\" rồi!");
            return;
        }
        expandedByAi.add(n.id);
        state.expandMindmapNode(n, ok -> { if (!ok) expandedByAi.remove(n.id); });
    }

    /** node == null → add a child of parentId, else edit node (same fields as the web modal) */
    private void nodeDialog(MindmapNode node, String parentId) {
        Context c = requireContext();
        Modal.show(c, 0, 0, node == null ? "Thêm nút sơ đồ tư duy mới" : "Chỉnh sửa nút sơ đồ tư duy", null, (body, dialog) -> {
            body.addView(Ui.label(c, "Tên nút nhánh:"));
            EditText label = Ui.field(c, "VD: Enzyme Chu trình Krebs", false);
            if (node != null) label.setText(node.label);
            Ui.add(body, label, 6);

            String[] typeLabels = new String[EDIT_TYPES.length];
            int tsel = 1;
            for (int i = 0; i < EDIT_TYPES.length; i++) {
                typeLabels[i] = EDIT_TYPES[i][1];
                if (node != null && EDIT_TYPES[i][0].equals(node.type)) tsel = i;
            }
            int[] chosenType = {tsel};
            int[] chosenImp = {node != null && node.importance != null ? 5 - node.importance : 2};
            Ui.add(body, Ui.label(c, "Phân loại node:"), 12);
            Ui.add(body, Ui.select(c, typeLabels, tsel, i -> chosenType[0] = i), 6);
            Ui.add(body, Ui.label(c, "Độ quan trọng (1-5):"), 12);
            Ui.add(body, Ui.select(c, IMPORTANCE, chosenImp[0], i -> chosenImp[0] = i), 6);
            Ui.add(body, Ui.label(c, "Mô tả & Tóm tắt AI:"), 12);
            EditText detail = Ui.field(c, "Mô tả chức năng hoặc khái niệm của nút này...", true);
            detail.setMinLines(3);
            if (node != null) detail.setText(node.summary != null ? node.summary : node.detail != null ? node.detail : "");
            Ui.add(body, detail, 6);

            LinearLayout btns = Ui.horizontal(c);
            LinearLayout save = Ui.pillButton(c, R.drawable.ic_check_circle, "Lưu thay đổi", R.color.brand, android.R.color.white, 0, v -> {
                String l = label.getText().toString().trim();
                if (l.isEmpty()) return;
                String type = EDIT_TYPES[chosenType[0]][0];
                int imp = 5 - chosenImp[0];
                if (node == null) state.addMindmapNode(l, detail.getText().toString().trim(), parentId, type, imp);
                else state.updateMindmapNode(node.id, l, detail.getText().toString().trim(), type, imp);
                dialog.dismiss();
            });
            save.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            Ui.addWeighted(btns, save, 0);
            Ui.addWeighted(btns, Ui.pillButton(c, 0, "Hủy bỏ", R.color.surface, R.color.text_secondary, R.color.border, v -> dialog.dismiss()), 8);
            Ui.add(body, btns, 16);
        });
    }

    /** "Xuất ảnh": render the whole map to PNG and hand it to the share sheet */
    private void exportImage(String rootLabel) {
        if (graph == null) return;
        toast("Đang chuẩn bị xuất ảnh HD...");
        Bitmap bmp = graph.exportBitmap(0xFFF8FAFC);
        if (bmp == null) { toast("Sơ đồ tư duy chưa tải xong!"); return; }
        Context c = requireContext();
        Async.run(() -> {
            File dir = new File(c.getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Không tạo được thư mục xuất.");
            String safe = rootLabel.trim().replaceAll("[^a-zA-Z0-9_\\u00C0-\\u024F\\u1E00-\\u1EFF]", "_");
            File f = new File(dir, "Mindmap_" + safe + ".png");
            try (FileOutputStream out = new FileOutputStream(f)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
            return FileProvider.getUriForFile(c, "com.studymind.nativeapp.files", f);
        }, new Async.Callback<Uri>() {
            @Override public void onSuccess(Uri uri) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Xuất sơ đồ tư duy"));
                toast("Xuất sơ đồ tư duy dạng ảnh PNG thành công!");
            }
            @Override public void onError(String m) { toast("Không thể xuất ảnh: " + m); }
        });
    }
}
