package com.studymind.nativeapp.ui.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.CommonConcept;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.FusionResult;
import com.studymind.nativeapp.data.model.Models.KnowledgeConflict;
import com.studymind.nativeapp.data.model.Models.MindmapNode;
import com.studymind.nativeapp.data.model.Models.UniqueInsight;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** "Hợp nhất & Đối chiếu Kiến thức Đa Tài liệu" (Pro) — KnowledgeFusionView.tsx. */
public class FusionFragment extends BaseFragment {
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean initSelection;
    private boolean loading;
    private int tab;
    private boolean copied;

    @Override
    protected Object watchKey() { return state.history() + ":" + state.fusionResult() + ":" + state.isPro(); }

    private boolean showMerged;

    @Override
    protected void render(LinearLayout root) {
        loading = state.jobs().isRunning(com.studymind.nativeapp.data.AnalysisJobs.Kind.FUSION);
        Context c = requireContext();
        List<DocumentItem> docs = state.history();
        if (!initSelection && docs.size() >= 2) {
            initSelection = true;
            selected.add(docs.get(0).id);
            selected.add(docs.get(1).id);
        }
        FusionResult r = state.fusionResult();

        // ---- Header ----
        LinearLayout top = Ui.horizontal(c);
        LinearLayout pro = Ui.iconText(c, R.drawable.ic_crown, "TÍNH NĂNG PRO ⭐", 12, Ui.color(c, R.color.m3_on_tertiary_fixed), true);
        pro.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        pro.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        top.addView(pro);
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        android.widget.FrameLayout share = Ui.iconBox(c, R.drawable.ic_network, R.color.m3_surface_container, R.color.text_secondary, 40, 20, 999);
        share.setContentDescription("Chia sẻ báo cáo hợp nhất");
        share.setOnClickListener(v -> {
            if (r == null) { toast("Hãy chạy Hợp nhất AI trước khi chia sẻ báo cáo."); return; }
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(android.content.Intent.EXTRA_TEXT, reportText(r));
            startActivity(android.content.Intent.createChooser(send, "Chia sẻ báo cáo hợp nhất"));
        });
        top.addView(share);
        root.addView(top);
        Ui.add(root, Ui.text(c, "Hợp nhất kiến thức", 24, R.color.text, true), 12);
        Ui.add(root, Ui.text(c, "Hợp nhất đa giáo trình, slide bài giảng và ghi chú rời rạc thành một bức tranh kiến thức thống nhất, tự động phát hiện mâu thuẫn học thuật.", 15, R.color.text_secondary, false), 6);

        // ---- Selection ----
        MaterialCardView pick = Ui.card(c);
        LinearLayout pi = Ui.inner(pick);
        LinearLayout ph = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        ph.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        Ui.addWeighted(ph, Ui.text(c, "Đã chọn " + selected.size() + " tài liệu để hợp nhất", 15, R.color.text, true), 8);
        ph.addView(Ui.pill(c, selected.size() >= 2 ? "Sẵn sàng AI" : "Chọn ≥ 2", selected.size() >= 2 ? R.color.m3_primary_fixed : R.color.m3_tertiary_fixed,
                selected.size() >= 2 ? R.color.brand_dark : R.color.m3_on_tertiary_fixed));
        pi.addView(ph);
        boolean alt = false;
        for (DocumentItem d : docs) {
            boolean on = selected.contains(d.id);
            String type = d.fileType != null ? d.fileType.toUpperCase() : "";
            int icon = type.contains("VIDEO") ? R.drawable.ic_video : type.contains("URL") ? R.drawable.ic_link : type.contains("PPT") ? R.drawable.ic_play : R.drawable.ic_file;
            LinearLayout row = Ui.horizontal(c);
            row.setBackground(Ui.rounded(c, on ? R.color.m3_surface_low : R.color.surface, 18, on ? 0 : R.color.border));
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            row.addView(Ui.iconBox(c, icon, alt ? R.color.m3_tertiary_fixed : R.color.rose_soft, alt ? R.color.m3_tertiary : R.color.m3_error, 44, 22, 12));
            alt = !alt;
            LinearLayout t = Ui.vertical(c);
            TextView title = Ui.text(c, d.title, 16, R.color.text, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(title);
            String subject = null;
            if (d.tags != null) for (String tg : d.tags) if (!tg.contains("Engine") && !tg.contains("Speech") && !tg.contains("Extractor")) { subject = tg; break; }
            String meta = (d.pageCount != null && d.pageCount > 0 ? d.pageCount + " trang" : d.fileType) + (subject != null ? "  •  " + subject : "  •  " + Ui.date(d.updatedAt));
            Ui.add(t, Ui.text(c, meta, 12, R.color.text_secondary, false), 2);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 12));
            row.addView(t, tl);
            row.addView(Ui.icon(c, on ? R.drawable.ic_check_circle : R.drawable.ic_plus_circle, 22, Ui.color(c, on ? R.color.brand_dark : R.color.slate_400)));
            row.setOnClickListener(v -> {
                if (on) { if (selected.size() > 2) selected.remove(d.id); else toast("Cần giữ ít nhất 2 tài liệu để hợp nhất."); }
                else selected.add(d.id);
                rerender();
            });
            Ui.add(pi, row, 10);
        }
        if (docs.size() < 2) Ui.add(pi, Ui.text(c, "Cần ít nhất 2 bài học trong lịch sử để hợp nhất.", 13, R.color.text_muted, false), 10);
        boolean ready = selected.size() >= 2 && !loading;
        LinearLayout go = Ui.iconText(c, R.drawable.ic_sparkles, loading ? "Đang phân tích đối chiếu..." : "✨ Bắt đầu Hợp nhất AI (" + selected.size() + " tài liệu)", 15, 0xFFFFFFFF, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF945D00));
        go.setMinimumHeight(Ui.dp(c, 54));
        go.setAlpha(ready ? 1f : 0.55f);
        go.setOnClickListener(v -> start());
        Ui.add(pi, go, 14);
        Ui.add(root, pick, 18);

        if (r == null) {
            LinearLayout empty = Ui.vertical(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setBackground(Ui.rounded(c, R.color.m3_surface_low, 22, 0));
            empty.setPadding(Ui.dp(c, 22), Ui.dp(c, 28), Ui.dp(c, 22), Ui.dp(c, 28));
            empty.addView(Ui.iconBox(c, R.drawable.ic_compare, R.color.teal_200, R.color.brand_dark, 60, 30, 999));
            Ui.addInline(empty, Ui.text(c, "Sẵn sàng Hợp nhất Kiến thức", 17, R.color.text, true), 14);
            TextView m = Ui.text(c, "Chọn ít nhất 2 bài học ở danh sách phía trên và nhấn \"Bắt đầu Hợp nhất AI\" để kích hoạt công cụ phân tích đối chiếu.", 13, R.color.text_secondary, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(empty, m, 6);
            Ui.add(root, empty, 16);
            return;
        }

        // ---- KPI tiles ----
        LinearLayout kpis = Ui.horizontal(c);
        kpis.setBaselineAligned(false);
        kpiTile(kpis, R.drawable.ic_file, R.color.m3_secondary_container, R.color.m3_secondary, R.color.surface, r.comparedDocs.size(), "Tài liệu đã hợp nhất", R.color.text, 0);
        kpiTile(kpis, R.drawable.ic_network, R.color.teal_200, R.color.brand_dark, R.color.surface, r.commonConcepts.size(), "Khái niệm gộp chung", R.color.text, 8);
        kpiTile(kpis, R.drawable.ic_alert_tri, R.color.m3_error, android.R.color.white, R.color.rose_soft, r.conflicts.size(), "Mâu thuẫn phát hiện", R.color.m3_error, 8);
        Ui.add(root, kpis, 4);

        // ---- Segmented tabs ----
        LinearLayout seg = Ui.horizontal(c);
        seg.setBackground(Ui.rounded(c, R.color.m3_surface_container, 18, 0));
        seg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        String[] labels = {"Tổng quan", "So sánh nguồn", "Radar mâu thuẫn"};
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            boolean on = tab == i;
            LinearLayout t = Ui.horizontal(c);
            t.setGravity(Gravity.CENTER);
            t.addView(Ui.text(c, labels[i], 14, on ? R.color.brand_dark : R.color.text_secondary, on));
            if (i == 2 && !r.conflicts.isEmpty()) {
                View rd = new View(c);
                rd.setBackground(Ui.rounded(c, R.color.m3_error, 999, 0));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7));
                rl.setMarginStart(Ui.dp(c, 6));
                t.addView(rd, rl);
            }
            t.setPadding(Ui.dp(c, 4), Ui.dp(c, 11), Ui.dp(c, 4), Ui.dp(c, 11));
            if (on) t.setBackground(Ui.rounded(c, R.color.surface, 14, 0));
            t.setOnClickListener(v -> { tab = idx; rerender(); });
            Ui.addWeighted(seg, t, i == 0 ? 0 : 4);
        }
        Ui.add(root, seg, 16);

        if (tab == 0) renderUnified(root, r);
        else if (tab == 1) renderCompare(root, r);
        else renderConflicts(root, r);

        // ---- Export ----
        LinearLayout copy = Ui.iconText(c, copied ? R.drawable.ic_check_circle : R.drawable.ic_copy, copied ? "Đã sao chép báo cáo!" : "📋 Sao chép toàn bộ báo cáo hợp nhất", 15, Ui.color(c, R.color.text), false);
        copy.setGravity(Gravity.CENTER);
        copy.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 18, 0));
        copy.setMinimumHeight(Ui.dp(c, 54));
        copy.setOnClickListener(v -> copyReport(r));
        Ui.add(root, copy, 22);
        LinearLayout export = Ui.iconText(c, R.drawable.ic_download, "Xuất file PDF / Markdown tổng hợp", 15, 0xFFFFFFFF, true);
        export.setGravity(Gravity.CENTER);
        export.setBackground(Ui.gradient(c, 18, 0xFF005C55, 0xFF005C55));
        export.setMinimumHeight(Ui.dp(c, 54));
        export.setOnClickListener(v -> exportMarkdown(r));
        Ui.add(root, export, 10);
    }

    private void kpiTile(LinearLayout row, int icon, int iconBg, int iconFg, int bg, int value, String label, int valueColor, int start) {
        Context c = requireContext();
        LinearLayout t = Ui.vertical(c);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Ui.rounded(c, bg, 20, bg == R.color.surface ? R.color.border : 0));
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
        t.addView(Ui.iconBox(c, icon, iconBg, iconFg, 36, 20, 999));
        TextView v = Ui.text(c, String.valueOf(value), 22, valueColor, true);
        v.setGravity(Gravity.CENTER);
        Ui.add(t, v, 6);
        TextView l = Ui.text(c, label, 12, valueColor == R.color.text ? R.color.text_secondary : valueColor, false);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        Ui.add(t, l, 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMarginStart(Ui.dp(c, start));
        row.addView(t, lp);
    }

    private void renderUnified(LinearLayout root, FusionResult r) {
        Context c = requireContext();
        MaterialCardView sum = Ui.card(c);
        LinearLayout si = Ui.inner(sum);
        si.addView(Ui.iconText(c, R.drawable.ic_brain, "Báo cáo tóm tắt cấu trúc", 17, Ui.color(c, R.color.brand_dark), true));
        TextView st = Ui.text(c, r.unifiedSummary.isEmpty() ? "Đã tổng hợp thành công nội dung giữa các bài học." : r.unifiedSummary, 15, R.color.text, false);
        st.setLineSpacing(0, 1.35f);
        Ui.add(si, st, 10);
        Ui.add(root, sum, 4);

        MaterialCardView mc = Ui.card(c);
        LinearLayout mi = Ui.inner(mc);
        LinearLayout mh = Ui.horizontal(c);
        Ui.addWeighted(mh, Ui.text(c, "Cấu trúc sơ đồ tư duy hợp nhất", 16, R.color.text, true), 0);
        mh.addView(Ui.pill(c, "Trích dẫn kép", R.color.m3_tertiary_fixed, R.color.m3_on_tertiary_fixed));
        mi.addView(mh);
        for (CommonConcept cc : r.commonConcepts) {
            LinearLayout item = Ui.vertical(c);
            item.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
            item.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            LinearLayout ih = Ui.horizontal(c);
            ih.setGravity(Gravity.TOP);
            Ui.addWeighted(ih, Ui.text(c, "Khái niệm: " + cc.concept, 17, R.color.text, true), 0);
            Ui.addWrap(ih, Ui.pill(c, cc.sources.size() >= 2 ? "Cốt lõi" : "Bổ trợ", R.color.teal_200, R.color.brand_dark), 6);
            item.addView(ih);
            Ui.add(item, Ui.text(c, cc.definition, 13, R.color.text_secondary, false), 6);
            if (!cc.sources.isEmpty()) {
                com.google.android.material.chip.ChipGroup srcs = new com.google.android.material.chip.ChipGroup(c);
                srcs.setChipSpacingHorizontal(Ui.dp(c, 6));
                srcs.setChipSpacingVertical(Ui.dp(c, 6));
                for (String s : cc.sources) {
                    LinearLayout t = Ui.iconText(c, R.drawable.ic_book, s, 12, Ui.color(c, R.color.text_secondary), false);
                    t.setBackground(Ui.rounded(c, R.color.surface, 999, 0));
                    t.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
                    srcs.addView(t);
                }
                Ui.add(item, srcs, 10);
            }
            Ui.add(mi, item, 12);
        }
        List<MindmapNode> merged = r.mergedMindmap != null ? r.mergedMindmap.nodes : new ArrayList<>();
        if (showMerged && !merged.isEmpty()) {
            LinearLayout list = Ui.vertical(c);
            for (MindmapNode n : merged) {
                int lvl = n.level != null ? n.level : 1;
                boolean warn = "warning".equals(n.type);
                TextView row = Ui.text(c, "•  " + n.label + (n.detail != null && !n.detail.isEmpty() ? "  (" + n.detail + ")" : ""), 13,
                        warn ? R.color.m3_tertiary : lvl == 0 ? android.R.color.white : R.color.text, lvl <= 1);
                if (lvl == 0) row.setTextColor(0xFFFFFFFF);
                row.setBackground(lvl == 0 ? Ui.gradient(c, 12, 0xFF005C55, 0xFF005C55) : Ui.rounded(c, warn ? R.color.amber_50 : lvl == 1 ? R.color.surface : R.color.m3_surface_low, 12, lvl == 1 ? R.color.border : 0));
                row.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = Ui.dp(c, 8);
                if (lvl >= 2) rl.setMarginStart(Ui.dp(c, 16));
                list.addView(row, rl);
            }
            Ui.add(mi, list, 6);
        }
        if (!merged.isEmpty()) {
            LinearLayout view = Ui.iconText(c, 0, showMerged ? "Thu gọn sơ đồ Mindmap hợp nhất" : "Xem sơ đồ Mindmap hợp nhất", 15, Ui.color(c, R.color.text_secondary), false);
            view.addView(Ui.icon(c, R.drawable.ic_arrow_right, 18, Ui.color(c, R.color.text_secondary)));
            view.setGravity(Gravity.CENTER);
            view.setBackground(Ui.rounded(c, R.color.m3_surface_high, 16, 0));
            view.setMinimumHeight(Ui.dp(c, 50));
            view.setOnClickListener(v -> { showMerged = !showMerged; rerender(); });
            Ui.add(mi, view, 14);
        }
        Ui.add(root, mc, 14);

        String tipText = !r.conflicts.isEmpty() && !r.conflicts.get(0).recommendation.isEmpty() ? r.conflicts.get(0).recommendation
                : "Học lý thuyết định nghĩa từ giáo trình trước, sau đó luyện các dạng bài áp dụng từ slide bài giảng để đạt điểm tối đa.";
        LinearLayout tip = Ui.horizontal(c);
        tip.setGravity(Gravity.TOP);
        tip.setBackground(Ui.rounded(c, R.color.teal_50, 20, 0));
        tip.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        tip.addView(Ui.icon(c, R.drawable.ic_lightbulb, 22, Ui.color(c, R.color.brand_dark)));
        TextView tt = Ui.text(c, "Mẹo PRO: " + tipText, 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        tip.addView(tt, tl);
        Ui.add(root, tip, 14);
    }

    private void exportMarkdown(FusionResult r) {
        String title = r.fusionTitle.isEmpty() ? "Bao_cao_hop_nhat" : r.fusionTitle;
        StringBuilder md = new StringBuilder("# ").append(r.fusionTitle.isEmpty() ? "Báo cáo hợp nhất kiến thức" : r.fusionTitle).append("\n\n");
        md.append("## Tóm tắt tổng quan\n\n").append(r.unifiedSummary).append("\n\n## Khái niệm dùng chung\n\n");
        for (CommonConcept cc : r.commonConcepts) md.append("- **").append(cc.concept).append(":** ").append(cc.definition).append(" _(Nguồn: ").append(String.join(", ", cc.sources)).append(")_\n");
        md.append("\n## Chi tiết độc quyền từng tài liệu\n\n");
        for (UniqueInsight u : r.uniqueInsights) {
            md.append("### ").append(u.docTitle).append("\n\n");
            for (String s : u.insights) md.append("- ").append(s).append('\n');
            md.append('\n');
        }
        md.append("## Radar mâu thuẫn\n\n");
        for (KnowledgeConflict k : r.conflicts) {
            md.append("### ").append(k.topic).append("\n\n- **").append(k.docA.title).append(":** ").append(k.docA.statement)
                    .append("\n- **").append(k.docB.title).append(":** ").append(k.docB.statement)
                    .append("\n- **Giải thích:** ").append(k.explanation).append("\n- **Khuyến nghị:** ").append(k.recommendation).append("\n\n");
        }
        md.append("---\n*Xuất từ StudyMind AI*\n");
        if (com.studymind.nativeapp.ui.Share.textFile(requireContext(), com.studymind.nativeapp.ui.Share.safeName(title, 40) + ".md", "text/markdown", md.toString(), "Xuất báo cáo hợp nhất"))
            toast("📄 Đã xuất báo cáo hợp nhất ra file Markdown (.md)! Có thể in ra PDF từ ứng dụng đọc file.");
        else toast("Không thể xuất báo cáo, vui lòng thử lại.");
    }

    private void renderCompare(LinearLayout root, FusionResult r) {
        Context c = requireContext();
        LinearLayout h = Ui.iconText(c, R.drawable.ic_compare, "Đối chiếu các chi tiết Độc quyền & Bổ sung từng nguồn", 14, Ui.color(c, R.color.text), true);
        ((ImageView) h.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.add(root, h, 18);
        for (int i = 0; i < r.uniqueInsights.size(); i++) {
            UniqueInsight u = r.uniqueInsights.get(i);
            MaterialCardView card = Ui.card(c);
            card.setRadius(Ui.dp(c, 24));
            LinearLayout in = Ui.inner(card);
            in.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
            LinearLayout head = Ui.horizontal(c);
            TextView num = Ui.text(c, "#" + (i + 1), 12, R.color.brand, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.rounded(c, R.color.teal_50, 12, 0));
            head.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
            LinearLayout t = Ui.vertical(c);
            t.addView(Ui.text(c, u.docTitle, 12, R.color.text, true));
            LinearLayout tags = Ui.horizontal(c);
            if (u.domain != null && !u.domain.isEmpty()) tags.addView(Ui.pill(c, "📚 " + u.domain, R.color.brand_soft, R.color.teal_900));
            Ui.addWrap(tags, Ui.pill(c, "Chi tiết Độc quyền Nguồn #" + (i + 1), R.color.teal_50, R.color.brand), tags.getChildCount() > 0 ? 6 : 0);
            Ui.add(t, tags, 4);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 12));
            head.addView(t, tl);
            in.addView(head);
            View hr = new View(c);
            hr.setBackgroundColor(Ui.color(c, R.color.slate_100));
            LinearLayout.LayoutParams hrl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
            hrl.topMargin = Ui.dp(c, 12);
            in.addView(hr, hrl);
            for (String s : u.insights) {
                LinearLayout li = Ui.horizontal(c);
                li.setGravity(Gravity.TOP);
                li.addView(Ui.icon(c, R.drawable.ic_arrow_right, 16, Ui.color(c, R.color.brand)));
                TextView tt = Ui.text(c, s, 12, R.color.text_secondary, false);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ll.setMarginStart(Ui.dp(c, 10));
                li.addView(tt, ll);
                Ui.add(in, li, 10);
            }
            Ui.add(root, card, 14);
        }
    }

    private void renderConflicts(LinearLayout root, FusionResult r) {
        Context c = requireContext();
        LinearLayout h = Ui.iconText(c, R.drawable.ic_shield, "Radar Phát hiện Mâu thuẫn & Sai lệch Số liệu", 14, Ui.color(c, R.color.amber_700), true);
        ((ImageView) h.getChildAt(0)).setColorFilter(Ui.color(c, R.color.amber));
        Ui.add(root, h, 18);
        if (r.conflicts.isEmpty()) {
            LinearLayout ok = Ui.vertical(c);
            ok.setGravity(Gravity.CENTER_HORIZONTAL);
            ok.setBackground(Ui.rounded(c, R.color.emerald_50, 24, R.color.emerald_soft));
            ok.setPadding(Ui.dp(c, 20), Ui.dp(c, 24), Ui.dp(c, 20), Ui.dp(c, 24));
            ok.addView(Ui.icon(c, R.drawable.ic_check_circle, 32, Ui.color(c, R.color.emerald)));
            Ui.addInline(ok, Ui.text(c, "Không tìm thấy mâu thuẫn!", 14, R.color.emerald_700, true), 8);
            TextView m = Ui.text(c, "Dữ liệu giữa các tài liệu chọn hoàn toàn đồng nhất và nhất quán.", 12, R.color.emerald, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(ok, m, 4);
            Ui.add(root, ok, 14);
            return;
        }
        for (KnowledgeConflict k : r.conflicts) {
            LinearLayout card = Ui.vertical(c);
            android.graphics.drawable.GradientDrawable cb = Ui.rounded(c, R.color.surface, 24, 0);
            cb.setStroke(Ui.dp(c, 2), 0xCCFDE68A);
            card.setBackground(cb);
            card.setElevation(Ui.dp(c, 3));
            card.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
            LinearLayout topic = Ui.iconText(c, R.drawable.ic_alert_tri, "Chủ đề mâu thuẫn: " + (k.topic.isEmpty() ? "Sai lệch nội dung" : k.topic), 12, Ui.color(c, R.color.amber_700), true);
            topic.setBackground(Ui.rounded(c, R.color.amber_50, 12, 0));
            topic.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            Ui.addInline(card, topic, 0);
            Ui.add(card, source(c, k.docA.title.isEmpty() ? "Nguồn A" : k.docA.title, k.docA.statement), 14);
            Ui.add(card, source(c, k.docB.title.isEmpty() ? "Nguồn B" : k.docB.title, k.docB.statement), 10);
            LinearLayout ai = Ui.vertical(c);
            ai.setBackground(Ui.rounded(c, R.color.teal_50, 16, R.color.brand_soft));
            ai.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            ai.addView(Ui.iconText(c, R.drawable.ic_info, "Giải thích AI & Khuyến nghị ôn tập:", 12, Ui.color(c, R.color.brand), true));
            Ui.add(ai, Ui.text(c, k.explanation, 12, R.color.text_secondary, false), 6);
            if (!k.recommendation.isEmpty()) {
                TextView rec = Ui.text(c, "💡 Khuyên dùng: " + k.recommendation, 12, R.color.brand, true);
                rec.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.brand_soft));
                rec.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
                Ui.add(ai, rec, 8);
            }
            Ui.add(card, ai, 14);
            Ui.add(root, card, 14);
        }
    }

    private View source(Context c, String title, String statement) {
        LinearLayout s = Ui.vertical(c);
        s.setBackground(Ui.rounded(c, R.color.slate_50, 16, R.color.border));
        s.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        s.addView(Ui.text(c, "📌 Nguồn: " + title, 11, R.color.text_muted, true));
        if (statement != null && !statement.isEmpty()) Ui.add(s, Ui.text(c, "\"" + statement + "\"", 12, R.color.text, true), 4);
        return s;
    }

    private void kpi(LinearLayout root, int icon, int bg, int fg, String label, String value) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout row = Ui.horizontal(c);
        row.addView(Ui.iconBox(c, icon, bg, fg, 48, 24, 12));
        LinearLayout t = Ui.vertical(c);
        t.addView(Ui.text(c, label, 12, R.color.text_muted, false));
        t.addView(Ui.text(c, value, 20, R.color.text, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(c, 16));
        row.addView(t, lp);
        Ui.inner(card).addView(row);
        Ui.add(root, card, 12);
    }

    private static android.graphics.drawable.GradientDrawable glass(Context c) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0x33FFFFFF);
        d.setStroke(Ui.dp(c, 1), 0x33FFFFFF);
        d.setCornerRadius(Ui.dp(c, 16));
        return d;
    }

    private void start() {
        if (!state.isPro()) {
            state.openUpgrade("Hợp nhất kiến thức đa tài liệu là tính năng của StudyMind Pro.");
            return;
        }
        if (selected.size() < 2 || loading) return;
        state.fuse(new ArrayList<>(selected));
    }

    @Override
    public void onJobsChanged() {
        boolean running = state.jobs().isRunning(com.studymind.nativeapp.data.AnalysisJobs.Kind.FUSION);
        if (running == loading) return;
        if (!running) tab = 0;
        rerender();
    }

    /** Same plain-text report as handleCopyReport on the web */
    private String reportText(FusionResult r) {
        StringBuilder sb = new StringBuilder("=== ").append(r.fusionTitle.isEmpty() ? "BÁO CÁO HỢP NHẤT KIẾN THỨC" : r.fusionTitle).append(" ===\n\n");
        sb.append("[TÓM TẮT TỔNG QUAN]\n").append(r.unifiedSummary).append("\n\n[CÁC KHÁI NIỆM DÙNG CHUNG / GIAO THOA TRI THỨC]\n");
        for (int i = 0; i < r.commonConcepts.size(); i++) {
            CommonConcept cc = r.commonConcepts.get(i);
            sb.append(i + 1).append(". ").append(cc.concept).append("\n   Định nghĩa: ").append(cc.definition)
                    .append("\n   Nguồn: ").append(String.join(", ", cc.sources)).append("\n\n");
        }
        sb.append("[CHI TIẾT ĐỘC QUYỀN TỪNG TÀI LIỆU]\n");
        for (UniqueInsight u : r.uniqueInsights) {
            sb.append("* ").append(u.docTitle).append(":\n");
            for (String s : u.insights) sb.append("  - ").append(s).append('\n');
            sb.append('\n');
        }
        sb.append("[RADAR CẢNH BÁO MÂU THUẪN & BẪY TƯ DUY]\n");
        for (int i = 0; i < r.conflicts.size(); i++) {
            KnowledgeConflict k = r.conflicts.get(i);
            sb.append(i + 1).append(". ").append(k.topic).append("\n   - Nguồn A: ").append(k.docA.statement)
                    .append("\n   - Nguồn B: ").append(k.docB.statement).append("\n   - Lời khuyên: ").append(k.recommendation).append("\n\n");
        }
        return sb.toString();
    }

    private void copyReport(FusionResult r) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("StudyMind Fusion", reportText(r)));
        copied = true;
        rerender();
        Async.mainDelayed(() -> { copied = false; if (isAdded()) rerender(); }, 3000);
    }
}
