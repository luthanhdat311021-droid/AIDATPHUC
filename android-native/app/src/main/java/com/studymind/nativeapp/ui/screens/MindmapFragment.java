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
        root.setPadding(0, 0, 0, Ui.dp(c, 24));

        // ---- Toolbar ----
        LinearLayout bar = Ui.vertical(c);
        bar.setBackgroundColor(Ui.color(c, R.color.surface));
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        LinearLayout title = Ui.horizontal(c);
        title.addView(Ui.iconBox(c, R.drawable.ic_mindmap, R.color.brand, android.R.color.white, 32, 16, 12));
        LinearLayout tt = Ui.vertical(c);
        LinearLayout tl = Ui.horizontal(c);
        tl.addView(Ui.text(c, "Deep Knowledge Mindmap AI", 14, R.color.text, true));
        TextView count = Ui.text(c, (byId.size() - 1) + " Nút phân cấp", 10, R.color.brand, true);
        count.setBackground(Ui.rounded(c, R.color.brand_soft, 999, R.color.brand_soft));
        count.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
        Ui.addWrap(tl, count, 6);
        tt.addView(tl);
        String depthLabel = mm != null && mm.metadata != null ? mm.metadata.depthLabel : null;
        tt.addView(Ui.text(c, (depthLabel != null ? "Chế độ " + depthLabel + " • " : "") + maxLevel + " tầng kiến thức • AI đào sâu nhánh • Căn đối xứng Trái-Phải", 11, R.color.text_muted, false));
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ttl.setMarginStart(Ui.dp(c, 10));
        title.addView(tt, ttl);
        bar.addView(title);

        LinearLayout tools = Ui.horizontal(c);
        EditText searchField = Ui.field(c, "Tìm nút (VD: ATP...)...", false);
        searchField.setTextSize(12);
        searchField.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 10), Ui.dp(c, 7));
        searchField.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchField.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.slate_400)));
        searchField.setCompoundDrawablePadding(Ui.dp(c, 6));
        searchField.setText(search);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a1, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { search = s.toString(); refreshGraph(true); }
        });
        tools.addView(searchField, new LinearLayout.LayoutParams(Ui.dp(c, 180), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (maxLevel > 1) {
            LinearLayout tiers = Ui.horizontal(c);
            tiers.setBackground(Ui.rounded(c, R.color.slate_50, 8, R.color.border));
            tiers.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
            List<Integer> lv = new ArrayList<>();
            for (int i = 1; i <= Math.min(maxLevel - 1, 5); i++) lv.add(i);
            lv.add(-1);
            for (int l : lv) {
                boolean on = visibleLevel == l;
                TextView t = Ui.text(c, l == -1 ? "Tất cả" : "Tầng " + l, 11, on ? android.R.color.white : R.color.text_secondary, true);
                t.setPadding(Ui.dp(c, 8), Ui.dp(c, 5), Ui.dp(c, 8), Ui.dp(c, 5));
                if (on) t.setBackground(Ui.rounded(c, R.color.brand, 6, 0));
                t.setOnClickListener(v -> { visibleLevel = l; applyTier(); keepView = false; rerender(); });
                tiers.addView(t);
            }
            Ui.addWrap(tools, tiers, 8);
        }
        String[] filterLabels = new String[FILTERS.length];
        int fsel = 0;
        for (int i = 0; i < FILTERS.length; i++) { filterLabels[i] = FILTERS[i][1]; if (FILTERS[i][0].equals(filter)) fsel = i; }
        android.widget.Spinner fs = Ui.select(c, filterLabels, fsel, i -> { filter = FILTERS[i][0]; keepView = false; rerender(); });
        Ui.addWrap(tools, fs, 8);
        LinearLayout add = Ui.pillButton(c, R.drawable.ic_plus, "Thêm nhánh", R.color.brand, android.R.color.white, 0, v -> nodeDialog(null, selectedId));
        add.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        add.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        Ui.addWrap(tools, add, 8);
        LinearLayout export = Ui.pillButton(c, R.drawable.ic_download, "Xuất ảnh", R.color.surface, R.color.text_secondary, R.color.border, v -> exportImage(rootLabel));
        export.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 10), Ui.dp(c, 7));
        Ui.addWrap(tools, export, 8);
        HorizontalScrollView ts = new HorizontalScrollView(c);
        ts.setHorizontalScrollBarEnabled(false);
        ts.addView(tools);
        Ui.add(bar, ts, 10);
        root.addView(bar);
        View rule = new View(c);
        rule.setBackgroundColor(Ui.color(c, R.color.border));
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));

        // ---- Canvas ----
        FrameLayout canvas = new FrameLayout(c);
        canvas.setBackgroundColor(Ui.color(c, R.color.bg));
        graph = new GraphView(c);
        graph.setDotColor(0xFFCBD5E1);
        graph.setOnNodeClick(n -> { selectedId = n.id; keepView = true; rerender(); });
        canvas.addView(graph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout hintBox = Ui.iconText(c, R.drawable.ic_sparkles, "Chạm nút để xem chi tiết • 1 ngón kéo • 2 ngón phóng to", 11, Ui.color(c, R.color.text_muted), false);
        ((ImageView) hintBox.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        hintBox.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        hintBox.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        hl.setMargins(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), 0);
        canvas.addView(hintBox, hl);
        LinearLayout zoom = Ui.vertical(c);
        zoom.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        for (String[] z : new String[][]{{"+", "1.25"}, {"−", "0.8"}, {"⤢", "fit"}}) {
            TextView b = Ui.text(c, z[0], 16, R.color.text_secondary, true);
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> { if (z[1].equals("fit")) graph.fit(); else graph.zoom(Float.parseFloat(z[1])); });
            zoom.addView(b, new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34)));
        }
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        zl.setMargins(Ui.dp(c, 10), 0, 0, Ui.dp(c, 10));
        canvas.addView(zoom, zl);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.58);
        root.addView(canvas, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        refreshGraph(keepView);
        keepView = false;

        View rule2 = new View(c);
        rule2.setBackgroundColor(Ui.color(c, R.color.border));
        root.addView(rule2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));
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
        r.fill = 0xFF0F766E;
        r.stroke = 0x995EEAD4;
        r.strokeWidth = 4;
        r.text = 0xFFFFFFFF;
        r.title = byId.get(ROOT).label.toUpperCase(new Locale("vi"));
        r.subtitle = "NÚT GỐC TRUNG TÂM (ROOT)";
        r.titleSp = 13;
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
            g.fill = match ? 0xFFFFFBEB : Ui.color(c, R.color.surface);
            g.stroke = match ? 0xFFF59E0B : sel ? 0xFF0F766E : Ui.color(c, R.color.slate_200);
            g.strokeWidth = match || sel ? 2.5f : 2f;
            g.text = Ui.color(c, R.color.text);
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

    private void renderInspector(LinearLayout root, String rootLabel) {
        Context c = requireContext();
        MindmapNode n = byId.get(selectedId);
        LinearLayout panel = Ui.vertical(c);
        panel.setBackgroundColor(Ui.color(c, R.color.surface));
        panel.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        root.addView(panel);

        LinearLayout head = Ui.horizontal(c);
        LinearLayout ht = Ui.vertical(c);
        ht.addView(Ui.text(c, "NÚT ĐANG CHỌN TRÊN SƠ ĐỒ", 10, R.color.slate_400, true));
        ht.addView(Ui.text(c, n.label, 15, R.color.text, true));
        Ui.addWeighted(head, ht, 0);
        View edit = Ui.iconBox(c, R.drawable.ic_edit, R.color.slate_100, R.color.text_secondary, 30, 14, 8);
        edit.setOnClickListener(v -> nodeDialog(n, null));
        Ui.addWrap(head, edit, 6);
        View del = Ui.iconBox(c, R.drawable.ic_trash, R.color.rose_50, R.color.rose, 30, 14, 8);
        del.setOnClickListener(v -> {
            if (n.id.equals(ROOT)) { toast("Không thể xóa nút gốc trung tâm!"); return; }
            new MaterialAlertDialogBuilder(c).setMessage("Xóa nút \"" + n.label + "\" khỏi sơ đồ tư duy?")
                    .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> state.deleteMindmapNode(n.id)).show();
        });
        Ui.addWrap(head, del, 6);
        panel.addView(head);
        View hr = new View(c);
        hr.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams hrl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        hrl.topMargin = Ui.dp(c, 12);
        panel.addView(hr, hrl);

        LinearLayout chips = Ui.horizontal(c);
        TextView type = Ui.text(c, "Loại: " + TYPE_LABEL.getOrDefault(n.type != null ? n.type : "concept", "Khái niệm"), 11, R.color.brand, true);
        type.setBackground(Ui.rounded(c, R.color.teal_50, 999, R.color.brand_soft));
        type.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        chips.addView(type);
        TextView imp = Ui.text(c, "Tầm quan trọng: " + (n.importance != null ? n.importance : 3) + "/5", 11, R.color.amber_700, true);
        imp.setBackground(Ui.rounded(c, R.color.amber_50, 999, R.color.amber_soft));
        imp.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addWrap(chips, imp, 8);
        Ui.add(panel, chips, 12);

        Ui.add(panel, Ui.text(c, "MÔ TẢ & GIẢI THÍCH AI:", 10, R.color.slate_400, true), 14);
        String desc = n.summary != null && !n.summary.isEmpty() ? n.summary : n.detail != null && !n.detail.isEmpty() ? n.detail : "Không có mô tả bổ sung cho nút này.";
        TextView d = Ui.text(c, desc, 12, R.color.text_secondary, false);
        d.setBackground(Ui.rounded(c, R.color.teal_50, 12, R.color.brand_soft));
        d.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        Ui.add(panel, d, 6);

        if (n.subDetails != null && !n.subDetails.isEmpty()) {
            Ui.add(panel, Ui.text(c, "TỪ KHÓA & Ý PHỤ:", 10, R.color.slate_400, true), 14);
            LinearLayout subs = Ui.horizontal(c);
            for (String s : n.subDetails) {
                TextView t = Ui.text(c, s, 11, R.color.text_secondary, false);
                t.setBackground(Ui.rounded(c, R.color.slate_100, 6, R.color.border));
                t.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
                Ui.addWrap(subs, t, subs.getChildCount() > 0 ? 6 : 0);
            }
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(subs);
            Ui.add(panel, hs, 6);
        }

        View hr2 = new View(c);
        hr2.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams hr2l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        hr2l.topMargin = Ui.dp(c, 16);
        panel.addView(hr2, hr2l);
        LinearLayout expand = Ui.pillButton(c, 0, "Mở rộng & Đào sâu nút này", R.color.brand, android.R.color.white, 0, v -> expand(n));
        expand.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        Ui.add(panel, expand, 12);
        if (children.containsKey(n.id) && !n.id.equals(ROOT)) {
            boolean folded = collapsed.contains(n.id);
            LinearLayout fold = Ui.pillButton(c, folded ? R.drawable.ic_chevron_right : R.drawable.ic_chevron_down,
                    folded ? "Mở rộng " + children.get(n.id).size() + " nút con" : "Thu gọn các nút con", R.color.surface, R.color.text_secondary, R.color.border,
                    v -> { if (folded) collapsed.remove(n.id); else collapsed.add(n.id); keepView = true; rerender(); });
            Ui.add(panel, fold, 8);
        }
        LinearLayout row = Ui.horizontal(c);
        LinearLayout fc = Ui.pillButton(c, R.drawable.ic_layers, "Tạo Flashcard", R.color.brand_soft, R.color.brand, 0, v -> state.navigate(Screen.FLASHCARD));
        fc.setBackground(Ui.rounded(c, R.color.brand_soft, 12, 0));
        Ui.addWeighted(row, fc, 0);
        LinearLayout qz = Ui.pillButton(c, R.drawable.ic_quiz, "Tạo Trắc nghiệm", R.color.surface, R.color.text_secondary, R.color.border, v -> state.navigate(Screen.QUIZ));
        ((ImageView) qz.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.addWeighted(row, qz, 8);
        Ui.add(panel, row, 8);
        LinearLayout child = Ui.pillButton(c, R.drawable.ic_plus, "Thêm nút con từ nhánh này", R.color.surface, R.color.brand, R.color.brand, v -> nodeDialog(null, n.id));
        Ui.add(panel, child, 8);
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
