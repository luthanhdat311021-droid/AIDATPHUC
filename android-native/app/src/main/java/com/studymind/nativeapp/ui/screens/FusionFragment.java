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

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        List<DocumentItem> docs = state.history();
        if (!initSelection && docs.size() >= 2) {
            initSelection = true;
            selected.add(docs.get(0).id);
            selected.add(docs.get(1).id);
        }

        // ---- Hero: from-[#0B4F48] to-[#0F766E] ----
        LinearLayout hero = Ui.vertical(c);
        hero.setBackground(Ui.gradient(c, 24, 0xFF0B4F48, 0xFF0F766E));
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        hero.setElevation(Ui.dp(c, 3));
        LinearLayout kicker = Ui.iconText(c, R.drawable.ic_compare, "Tính năng Nâng cao (AI Fusion)" + (state.isPro() ? "" : " · 🔒 Pro"), 12, 0xFFCCFBF1, true);
        android.graphics.drawable.GradientDrawable kb = new android.graphics.drawable.GradientDrawable();
        kb.setColor(0x26FFFFFF);
        kb.setStroke(Ui.dp(c, 1), 0x4DFFFFFF);
        kb.setCornerRadius(Ui.dp(c, 999));
        kicker.setBackground(kb);
        kicker.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        Ui.addInline(hero, kicker, 0);
        TextView h = Ui.text(c, "Hợp nhất & Đối chiếu Kiến thức Đa Tài liệu", 22, R.color.text, true);
        h.setTextColor(Color.WHITE);
        Ui.add(hero, h, 12);
        TextView p = Ui.text(c, "Chọn từ 2 bài học trở lên để AI tự động gộp các khái niệm trùng lặp, phân tách chi tiết độc quyền và phát hiện cờ cảnh báo ⚠️ mâu thuẫn số liệu giữa các tài liệu.", 13, R.color.text, false);
        p.setTextColor(0xE6CCFBF1);
        Ui.add(hero, p, 8);
        boolean ready = selected.size() >= 2 && !loading;
        LinearLayout go = Ui.pillButton(c, R.drawable.ic_sparkles, loading ? "Đang phân tích đối chiếu..." : "Bắt đầu Hợp nhất AI (" + selected.size() + " tài liệu)",
                android.R.color.white, R.color.brand, 0, v -> start());
        go.setBackground(ready ? Ui.rounded(c, android.R.color.white, 16, 0) : glass(c));
        ((TextView) go.getChildAt(1)).setTextColor(ready ? 0xFF0F766E : 0x99CCFBF1);
        ((ImageView) go.getChildAt(0)).setColorFilter(ready ? 0xFF0F766E : 0x99CCFBF1);
        go.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 14));
        Ui.add(hero, go, 18);
        root.addView(hero);

        // ---- Document selection ----
        MaterialCardView pick = Ui.card(c);
        pick.setRadius(Ui.dp(c, 24));
        LinearLayout pi = Ui.inner(pick);
        pi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout ph = Ui.iconText(c, R.drawable.ic_book, "Chọn các bài học cần đối chiếu:", 14, Ui.color(c, R.color.text), true);
        ((ImageView) ph.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        pi.addView(ph);
        Ui.add(pi, Ui.text(c, "(Đã chọn " + selected.size() + " tệp)", 12, R.color.text_muted, false), 2);
        if (selected.size() < 2) {
            TextView warn = Ui.text(c, "Vui lòng chọn ít nhất 2 bài học", 12, R.color.amber_700, true);
            warn.setBackground(Ui.rounded(c, R.color.amber_50, 8, R.color.amber_soft));
            warn.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            Ui.addInline(pi, warn, 8);
        }
        for (DocumentItem d : docs) {
            boolean on = selected.contains(d.id);
            LinearLayout row = Ui.horizontal(c);
            row.setGravity(Gravity.TOP);
            row.setBackground(Ui.rounded(c, on ? R.color.teal_50 : R.color.surface, 16, on ? R.color.brand : R.color.border));
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
            TextView box = Ui.text(c, on ? "✓" : "", 13, android.R.color.white, true);
            box.setGravity(Gravity.CENTER);
            box.setBackground(Ui.rounded(c, on ? R.color.brand : R.color.surface, 4, on ? R.color.brand : R.color.slate_300));
            row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
            LinearLayout t = Ui.vertical(c);
            TextView title = Ui.text(c, d.title, 12, R.color.text, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(title);
            LinearLayout meta = Ui.horizontal(c);
            TextView ft = Ui.text(c, d.fileType, 10, R.color.text_secondary, true);
            ft.setBackground(Ui.rounded(c, R.color.slate_100, 4, 0));
            ft.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
            meta.addView(ft);
            Ui.addWrap(meta, Ui.text(c, Ui.dateTime(d.updatedAt), 11, R.color.text_muted, false), 8);
            Ui.add(t, meta, 4);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 12));
            row.addView(t, tl);
            row.setOnClickListener(v -> {
                if (on) { if (selected.size() > 2) selected.remove(d.id); } else selected.add(d.id);
                rerender();
            });
            Ui.add(pi, row, 10);
        }
        if (docs.size() < 2) Ui.add(pi, Ui.text(c, "Cần ít nhất 2 bài học trong lịch sử để hợp nhất.", 12, R.color.text_muted, false), 10);
        Ui.add(root, pick, 18);

        FusionResult r = state.fusionResult();
        if (r == null) {
            MaterialCardView empty = Ui.card(c);
            empty.setRadius(Ui.dp(c, 24));
            LinearLayout ei = Ui.inner(empty);
            ei.setGravity(Gravity.CENTER_HORIZONTAL);
            ei.setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 36));
            ei.addView(Ui.iconBox(c, R.drawable.ic_compare, R.color.teal_50, R.color.brand, 64, 32, 24));
            Ui.addInline(ei, Ui.text(c, "Sẵn sàng Hợp nhất Kiến thức", 16, R.color.text, true), 14);
            TextView m = Ui.text(c, "Chọn ít nhất 2 bài học ở danh sách phía trên và nhấn nút \"Bắt đầu Hợp nhất AI\" để kích hoạt công cụ phân tích đối chiếu.", 12, R.color.text_muted, false);
            m.setGravity(Gravity.CENTER);
            Ui.add(ei, m, 6);
            Ui.add(root, empty, 18);
            return;
        }

        // ---- KPI metrics ----
        kpi(root, R.drawable.ic_folder, R.color.teal_50, R.color.brand, "Tài liệu đã hợp nhất", r.comparedDocs.size() + " tệp");
        kpi(root, R.drawable.ic_check_circle, R.color.emerald_50, R.color.emerald, "Khái niệm gộp chung", r.commonConcepts.size() + " nhóm");
        kpi(root, R.drawable.ic_shield, R.color.amber_50, R.color.amber, "Mâu thuẫn phát hiện", r.conflicts.size() + " cờ cảnh báo");

        // ---- Sub tabs ----
        String[] labels = {"1. Tổng quan Hợp nhất", "2. So sánh Chi tiết", "3. Radar Cảnh báo Mâu thuẫn (" + r.conflicts.size() + ")"};
        int[] icons = {R.drawable.ic_layers, R.drawable.ic_compare, R.drawable.ic_alert_tri};
        View[] tabViews = new View[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            tabViews[i] = Ui.underlineTab(c, icons[i], labels[i], tab == i, i == 2 ? 0xFFB45309 : Ui.color(c, R.color.brand), v -> { tab = idx; rerender(); });
        }
        Ui.add(root, Ui.tabBar(c, tabViews), 20);

        if (tab == 0) renderUnified(root, r);
        else if (tab == 1) renderCompare(root, r);
        else renderConflicts(root, r);
    }

    private void renderUnified(LinearLayout root, FusionResult r) {
        Context c = requireContext();
        LinearLayout sum = Ui.vertical(c);
        android.graphics.drawable.GradientDrawable sb = Ui.gradient(c, 24, Ui.color(c, R.color.teal_50), Ui.color(c, R.color.surface), Ui.color(c, R.color.slate_50));
        sb.setStroke(Ui.dp(c, 1), Ui.color(c, R.color.brand_soft));
        sum.setBackground(sb);
        sum.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        sum.addView(Ui.iconText(c, R.drawable.ic_sparkles, "BÁO CÁO TÓM TẮT HỢP NHẤT DỮ LIỆU", 12, Ui.color(c, R.color.brand), true));
        LinearLayout copy = Ui.pillButton(c, copied ? R.drawable.ic_check_circle : R.drawable.ic_copy, copied ? "Đã sao chép báo cáo!" : "Sao chép toàn bộ báo cáo",
                R.color.surface, R.color.brand, R.color.brand_soft, v -> copyReport(r));
        copy.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        Ui.addInline(sum, copy, 10);
        Ui.add(sum, Ui.text(c, r.unifiedSummary.isEmpty() ? "Đã tổng hợp thành công nội dung giữa các bài học." : r.unifiedSummary, 13, R.color.text_secondary, false), 12);
        Ui.add(root, sum, 18);

        LinearLayout ch = Ui.iconText(c, R.drawable.ic_check_circle, "Các Khái niệm dùng chung (Đã gộp chuẩn hóa)", 14, Ui.color(c, R.color.text), true);
        ((ImageView) ch.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.add(root, ch, 20);
        for (CommonConcept cc : r.commonConcepts) {
            MaterialCardView card = Ui.card(c);
            LinearLayout in = Ui.inner(card);
            LinearLayout head = Ui.horizontal(c);
            Ui.addWeighted(head, Ui.text(c, cc.concept, 13, R.color.text, true), 0);
            TextView n = Ui.text(c, cc.sources.size() + " Nguồn", 10, R.color.brand, true);
            n.setBackground(Ui.rounded(c, R.color.teal_50, 6, 0));
            n.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
            Ui.addWrap(head, n, 8);
            in.addView(head);
            Ui.add(in, Ui.text(c, cc.definition, 12, R.color.text_secondary, false), 8);
            if (!cc.sources.isEmpty()) {
                LinearLayout srcs = Ui.horizontal(c);
                for (String s : cc.sources) {
                    TextView t = Ui.text(c, "📌 " + s, 10, R.color.text_secondary, true);
                    t.setBackground(Ui.rounded(c, R.color.slate_100, 6, 0));
                    t.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
                    Ui.addWrap(srcs, t, srcs.getChildCount() > 0 ? 6 : 0);
                }
                HorizontalScrollView hs = new HorizontalScrollView(c);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(srcs);
                Ui.add(in, hs, 10);
            }
            Ui.add(root, card, 12);
        }

        List<MindmapNode> merged = r.mergedMindmap != null ? r.mergedMindmap.nodes : new ArrayList<>();
        if (!merged.isEmpty()) {
            MaterialCardView card = Ui.card(c);
            card.setRadius(Ui.dp(c, 24));
            LinearLayout in = Ui.inner(card);
            in.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
            LinearLayout mh = Ui.iconText(c, R.drawable.ic_layers, "Cấu trúc Sơ đồ Tư duy Hợp nhất", 14, Ui.color(c, R.color.text), true);
            ((ImageView) mh.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
            in.addView(mh);
            LinearLayout list = Ui.vertical(c);
            list.setBackground(Ui.rounded(c, R.color.slate_50, 16, R.color.border));
            list.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            for (MindmapNode n : merged) {
                int lvl = n.level != null ? n.level : 1;
                boolean warn = "warning".equals(n.type);
                LinearLayout row = Ui.horizontal(c);
                row.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
                int bg = warn ? R.color.amber_50 : lvl == 0 ? R.color.brand : lvl == 1 ? R.color.surface : R.color.slate_100;
                int fg = warn ? R.color.amber_700 : lvl == 0 ? android.R.color.white : lvl == 1 ? R.color.text : R.color.text_secondary;
                row.setBackground(Ui.rounded(c, bg, 12, warn ? R.color.amber_soft : lvl == 1 ? R.color.border : 0));
                Ui.addWeighted(row, Ui.text(c, "•  " + n.label + (n.detail != null && !n.detail.isEmpty() ? "  (" + n.detail + ")" : ""), 12, fg, lvl <= 1), 0);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = list.getChildCount() > 0 ? Ui.dp(c, 8) : 0;
                if (lvl >= 2) rl.setMarginStart(Ui.dp(c, 16));
                list.addView(row, rl);
            }
            Ui.add(in, list, 14);
            Ui.add(root, card, 18);
        }
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
        loading = true;
        rerender();
        state.fuse(new ArrayList<>(selected), ok -> { loading = false; tab = 0; if (isAdded()) rerender(); });
    }

    /** Same plain-text report as handleCopyReport on the web */
    private void copyReport(FusionResult r) {
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
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("StudyMind Fusion", sb.toString()));
        copied = true;
        rerender();
        Async.mainDelayed(() -> { copied = false; if (isAdded()) rerender(); }, 3000);
    }
}
