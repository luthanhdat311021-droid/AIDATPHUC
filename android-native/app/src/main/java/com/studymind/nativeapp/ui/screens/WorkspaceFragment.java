package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.KnowledgeGaps;
import com.studymind.nativeapp.data.model.Models.AINotes;
import com.studymind.nativeapp.data.model.Models.AIMindmap;
import com.studymind.nativeapp.data.model.Models.AIQuiz;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.AppUser;
import com.studymind.nativeapp.data.model.Models.ChatMessage;
import com.studymind.nativeapp.data.model.Models.Flashcard;
import com.studymind.nativeapp.data.model.Models.KbMisconception;
import com.studymind.nativeapp.data.model.Models.NotesItem;
import com.studymind.nativeapp.data.model.Models.NotesSection;
import com.studymind.nativeapp.data.model.Models.Prerequisite;
import com.studymind.nativeapp.data.model.Models.PrerequisiteAnalysis;
import com.studymind.nativeapp.data.model.Models.QuizQuestion;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * "Không gian tài liệu" — WorkspaceView.tsx on a phone: document bar, readiness banner, then the three
 * desktop columns stacked (source text, AI notes with read-aloud, AI tutor chat).
 */
public class WorkspaceFragment extends BaseFragment {
    private String docId;
    private boolean socratic;
    private final List<ChatMessage> messages = new ArrayList<>();
    private boolean sending;
    private String draft = "";

    private TextToSpeech tts;
    private boolean ttsReady, speaking, paused;
    private float ttsRate = 1.0f;
    private String spokenText;
    private int spokenFrom;

    @Override
    protected Object watchKey() { return state.activeDoc(); }

    @Override
    public void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    private boolean showSource;
    /** Set by the Knowledge Gap Map "Hỏi gia sư Socrates" button: next render opens the tutor in Socratic mode */
    public static boolean openInSocraticMode;

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "bắt đầu học")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        if (!a.document.id.equals(docId)) {
            docId = a.document.id;
            socratic = false;
            showSource = false;
            messages.clear();
            messages.add(greeting());
        }
        if (openInSocraticMode) {
            openInSocraticMode = false;
            socratic = true;
            messages.clear();
            messages.add(greeting());
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        AINotes notes = a.pack.notes();

        renderDocHeader(root, a, notes);
        renderTabs(root, a);
        renderGapAlert(root, a);
        renderPlayer(root, a, notes);
        renderQuickActions(root, a);
        renderNotes(root, a, notes);
        renderChat(root, a);
    }

    // ---------- document header ----------

    private void renderDocHeader(LinearLayout root, ActiveDocument a, AINotes notes) {
        Context c = requireContext();
        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        LinearLayout left = Ui.vertical(c);
        LinearLayout chips = Ui.horizontal(c);
        String subject = null;
        if (a.document.tags != null) for (String t : a.document.tags) if (!t.contains("Engine") && !t.contains("Speech") && !t.contains("Extractor")) { subject = t; break; }
        if (subject != null) {
            LinearLayout sp = Ui.iconText(c, R.drawable.ic_folder, subject, 12, 0xFFFFFFFF, true);
            sp.setBackground(Ui.gradient(c, 8, 0xFF0F766E, 0xFF0F766E));
            sp.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
            chips.addView(sp);
        }
        String meta = (a.document.fileType != null ? a.document.fileType : "Tài liệu") + (a.document.fileSize != null ? " • " + a.document.fileSize : "")
                + (a.document.pageCount != null && a.document.pageCount > 0 ? " • " + a.document.pageCount + " trang" : "");
        LinearLayout mp = Ui.iconText(c, R.drawable.ic_file, meta, 12, Ui.color(c, R.color.text_secondary), false);
        mp.setBackground(Ui.rounded(c, R.color.m3_surface_container, 8, 0));
        mp.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addWrap(chips, mp, subject != null ? 6 : 0);
        left.addView(chips);
        LinearLayout synced = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.emerald, 999, 0));
        synced.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 6), Ui.dp(c, 6)));
        Ui.addWrap(synced, Ui.text(c, "Đã đồng bộ AI", 12, R.color.text_secondary, false), 6);
        synced.setBackground(Ui.rounded(c, R.color.m3_surface_low, 8, 0));
        synced.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3));
        Ui.addInline(left, synced, 6);
        TextView title = Ui.text(c, notes != null && notes.summaryTitle != null && !notes.summaryTitle.isEmpty() ? notes.summaryTitle : a.document.title, 22, R.color.text, true);
        Ui.add(left, title, 8);
        Ui.addWeighted(top, left, 0);
        LinearLayout tools = Ui.horizontal(c);
        tools.addView(toolIcon(c, R.drawable.ic_refresh, v -> { state.openDocument(a.document.id, null); toast("Đã làm mới phân tích cho tài liệu!"); }));
        tools.addView(toolIcon(c, R.drawable.ic_network, v -> shareLink(a)));
        tools.addView(toolIcon(c, R.drawable.ic_download, v -> shareMarkdown(a, notes)));
        top.addView(tools);
        root.addView(top);
    }

    private View toolIcon(Context c, int icon, View.OnClickListener l) {
        ImageView iv = Ui.icon(c, icon, 20, Ui.color(c, R.color.text_secondary));
        iv.setPadding(Ui.dp(c, 9), Ui.dp(c, 9), Ui.dp(c, 9), Ui.dp(c, 9));
        iv.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38));
        lp.setMarginStart(Ui.dp(c, 6));
        iv.setLayoutParams(lp);
        iv.setOnClickListener(l);
        return iv;
    }

    /** Ghi chú (current) · Sơ đồ tư duy · Thẻ ghi nhớ N */
    private void renderTabs(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        LinearLayout tabs = Ui.horizontal(c);
        tabs.setBackground(Ui.rounded(c, R.color.m3_surface_low, 999, 0));
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        Object[][] items = {{"Ghi chú", R.drawable.ic_book, null}, {"Sơ đồ tư duy", R.drawable.ic_mindmap, Screen.MINDMAP},
                {"Thẻ ghi nhớ", R.drawable.ic_layers, Screen.FLASHCARD}};
        for (Object[] it : items) {
            boolean active = it[2] == null;
            LinearLayout t = Ui.iconText(c, (int) it[1], (String) it[0], 13, active ? 0xFFFFFFFF : Ui.color(c, R.color.text_secondary), active);
            if (it[2] == Screen.FLASHCARD) {
                TextView n = Ui.pill(c, String.valueOf(a.pack.flashcards().size()), R.color.m3_surface_container, R.color.text_secondary);
                Ui.addWrap(t, n, 4);
            }
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 8), Ui.dp(c, 9));
            if (active) t.setBackground(Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E));
            else t.setOnClickListener(v -> state.navigate((Screen) it[2]));
            Ui.addWeighted(tabs, t, tabs.getChildCount() > 0 ? 2 : 0);
        }
        Ui.add(root, tabs, 14);
    }

    /** "Cảnh báo lỗ hổng nền tảng" → prerequisites + diagnostic test */
    private void renderGapAlert(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        PrerequisiteAnalysis pre = a.pack.prerequisites();
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 20, 0));
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.TOP);
        top.addView(Ui.iconBox(c, R.drawable.ic_compass, R.color.m3_tertiary, android.R.color.white, 40, 22, 12));
        LinearLayout tx = Ui.vertical(c);
        LinearLayout th = Ui.horizontal(c);
        Ui.addWeighted(th, Ui.text(c, "Cảnh báo lỗ hổng nền tảng", 15, R.color.m3_on_tertiary_fixed, true), 0);
        TextView imp = Ui.pill(c, "Quan trọng", R.color.m3_tertiary_container, android.R.color.white);
        imp.setTextColor(0xFFFFFFFF);
        th.addView(imp);
        tx.addView(th);
        SpannableStringBuilder bt = new SpannableStringBuilder();
        int questions = 0;
        if (pre != null && !pre.prerequisites.isEmpty()) {
            List<String> names = new ArrayList<>();
            int critical = 0;
            for (Prerequisite p : pre.prerequisites) if ("critical".equals(p.priority)) { critical++; names.add(p.concept); }
            bt.append("Bài này cần ");
            int st = bt.length();
            bt.append(critical + " kiến thức nền bắt buộc");
            bt.setSpan(new StyleSpan(Typeface.BOLD), st, bt.length(), 0);
            if (!names.isEmpty()) bt.append(": " + String.join(", ", names.subList(0, Math.min(3, names.size()))) + ".");
            questions = pre.diagnosticPreTest != null ? pre.diagnosticPreTest.questions.size() : 0;
        } else {
            bt.append("Kiểm tra bạn đã đủ kiến thức nền để học tài liệu này chưa trước khi bắt đầu.");
        }
        Ui.add(tx, Ui.text(c, bt, 13, R.color.m3_tertiary, false), 4);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 12));
        top.addView(tx, txl);
        card.addView(top);
        LinearLayout foot = Ui.horizontal(c);
        foot.setBackground(Ui.rounded(c, R.color.surface, 14, 0));
        foot.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        LinearLayout fl = Ui.iconText(c, R.drawable.ic_timer, questions > 0 ? "Test nhanh chẩn đoán: " + questions + " câu (" + Math.max(1, questions / 2) + " phút)" : "Phân tích kiến thức tiên quyết bằng AI", 12, Ui.color(c, R.color.text_secondary), false);
        Ui.addWeighted(foot, fl, 0);
        LinearLayout go = Ui.iconText(c, 0, "Kiểm tra ngay", 13, 0xFFFFFFFF, true);
        go.addView(Ui.icon(c, R.drawable.ic_arrow_right, 16, 0xFFFFFFFF));
        go.setBackground(Ui.gradient(c, 999, 0xFF734700, 0xFF945D00));
        go.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        go.setOnClickListener(v -> state.navigate(Screen.PREREQUISITE));
        Ui.addWrap(foot, go, 8);
        Ui.add(card, foot, 12);
        Ui.add(root, card, 16);
    }

    /** "Trình tóm tắt âm thanh StudyMind" */
    private void renderPlayer(LinearLayout root, ActiveDocument a, AINotes notes) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        LinearLayout head = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, speaking && !paused ? R.color.emerald : R.color.brand, 999, 0));
        head.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        TextView ht = Ui.text(c, "Trình tóm tắt âm thanh StudyMind", 14, R.color.text, true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 8));
        head.addView(ht, hl);
        head.addView(Ui.iconText(c, R.drawable.ic_volume, "Giọng AI tiếng Việt", 12, Ui.color(c, R.color.text_muted), false));
        in.addView(head);

        LinearLayout row = Ui.horizontal(c);
        android.widget.FrameLayout play = Ui.iconBox(c, speaking && !paused ? R.drawable.ic_pause : R.drawable.ic_play, R.color.brand_dark, android.R.color.white, 52, 26, 999);
        play.setOnClickListener(v -> toggleSpeech(a, notes));
        row.addView(play);
        LinearLayout wave = Ui.horizontal(c);
        wave.setGravity(Gravity.CENTER_VERTICAL);
        int[] heights = {10, 18, 26, 14, 30, 22, 12, 28, 16, 24, 10, 20, 14, 26, 12, 18, 8, 16};
        int played = speaking && spokenText != null && spokenText.length() > 0 ? heights.length * spokenFrom / spokenText.length() + 1 : 0;
        for (int i = 0; i < heights.length; i++) {
            View b = new View(c);
            b.setBackground(Ui.rounded(c, i < played ? R.color.brand_dark : R.color.m3_outline_variant, 999, 0));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(c, heights[i]), 1f);
            bl.setMarginStart(Ui.dp(c, i == 0 ? 0 : 3));
            wave.addView(b, bl);
        }
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, Ui.dp(c, 34), 1f);
        wl.setMarginStart(Ui.dp(c, 12));
        row.addView(wave, wl);
        TextView rate = Ui.text(c, (ttsRate == 1.0f ? "1" : String.valueOf(ttsRate)) + "x", 13, R.color.text, true);
        rate.setBackground(Ui.rounded(c, R.color.m3_surface_container, 999, 0));
        rate.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        rate.setOnClickListener(v -> {
            ttsRate = ttsRate == 1.0f ? 1.25f : ttsRate == 1.25f ? 1.5f : 1.0f;
            if (tts != null) tts.setSpeechRate(ttsRate);
            toast("Tốc độ đọc giọng nói: " + ttsRate + "x");
            rerender();
        });
        Ui.addWrap(row, rate, 10);
        Ui.add(in, row, 12);

        LinearLayout foot = Ui.horizontal(c);
        foot.setBackground(Ui.rounded(c, R.color.m3_surface_low, 12, 0));
        foot.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        String status = speaking ? (paused ? "Tạm dừng: " : "Đang đọc: ") : "Sẵn sàng đọc: ";
        TextView st = Ui.text(c, status + (notes != null && notes.summaryTitle != null ? notes.summaryTitle : a.document.title), 12, R.color.text_secondary, false);
        st.setMaxLines(2);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.addWeighted(foot, st, 0);
        if (speaking) {
            TextView stop = Ui.text(c, "Dừng đọc", 12, R.color.rose, true);
            stop.setOnClickListener(v -> stopSpeech());
            Ui.addWrap(foot, stop, 10);
        }
        Ui.add(in, foot, 12);
        Ui.add(root, card, 16);
    }

    private void renderQuickActions(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        LinearLayout row = Ui.horizontal(c);
        Object[][] acts = {
                {"Tạo Thẻ nhớ", R.drawable.ic_layers, R.color.brand_dark, (View.OnClickListener) v -> quickFlashcard(a)},
                {"Giải thích từ", R.drawable.ic_lightbulb, R.color.m3_secondary, (View.OnClickListener) v -> send("Giải thích thuật ngữ và công thức chính trong " + a.document.title)},
                {"Vẽ sơ đồ nhánh", R.drawable.ic_mindmap, R.color.m3_tertiary, (View.OnClickListener) v -> quickMindmap(a)}};
        for (Object[] it : acts) {
            LinearLayout t = Ui.vertical(c);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12));
            t.addView(Ui.iconBox(c, (int) it[1], R.color.surface, (int) it[2], 36, 20, 999));
            TextView l = Ui.text(c, (String) it[0], 12, R.color.text, false);
            l.setGravity(Gravity.CENTER);
            Ui.add(t, l, 6);
            t.setOnClickListener((View.OnClickListener) it[3]);
            Ui.addWeighted(row, t, row.getChildCount() > 0 ? 8 : 0);
        }
        Ui.add(root, row, 16);
    }

    /** "GHI CHÚ TÓM TẮT AI": numbered section cards + collapsible source text */
    private void renderNotes(LinearLayout root, ActiveDocument a, AINotes notes) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        LinearLayout head = Ui.horizontal(c);
        View edge = new View(c);
        edge.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        head.addView(edge, new LinearLayout.LayoutParams(Ui.dp(c, 4), Ui.dp(c, 22)));
        TextView ht = Ui.text(c, "GHI CHÚ TÓM TẮT AI", 18, R.color.text, true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 10));
        head.addView(ht, hl);
        head.addView(Ui.pill(c, "Tổng hợp tự động", R.color.m3_primary_fixed, R.color.brand_dark));
        in.addView(head);

        if (notes == null || notes.sections == null || notes.sections.isEmpty()) {
            Ui.add(in, Ui.muted(c, "Tài liệu này chưa có ghi chú tóm tắt."), 12);
        } else {
            int n = 0;
            for (NotesSection sec : notes.sections) {
                n++;
                LinearLayout sc = Ui.vertical(c);
                sc.setBackground(Ui.rounded(c, R.color.surface, 16, R.color.border));
                sc.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
                LinearLayout sh = Ui.horizontal(c);
                sh.setGravity(Gravity.TOP);
                TextView num = Ui.text(c, String.valueOf(n), 13, android.R.color.white, true);
                num.setTextColor(0xFFFFFFFF);
                num.setGravity(Gravity.CENTER);
                num.setBackground(Ui.gradient(c, 8, 0xFF005C55, 0xFF005C55));
                sh.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
                TextView st = Ui.text(c, sec.heading, 17, R.color.text, true);
                LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                stl.setMarginStart(Ui.dp(c, 10));
                sh.addView(st, stl);
                sc.addView(sh);
                if (sec.items != null) {
                    for (NotesItem item : sec.items) {
                        SpannableStringBuilder sb = new SpannableStringBuilder();
                        if (item.label != null && !item.label.isEmpty()) {
                            sb.append(item.label);
                            sb.setSpan(new android.text.style.BackgroundColorSpan(Ui.color(c, R.color.m3_primary_fixed)), 0, sb.length(), 0);
                            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                            sb.append(": ");
                        }
                        sb.append(item.text);
                        TextView t = Ui.text(c, sb, 14, R.color.text, false);
                        t.setLineSpacing(0, 1.35f);
                        Ui.add(sc, t, 10);
                    }
                }
                Ui.add(in, sc, 14);
            }
        }

        // Collapsible source text
        LinearLayout src = Ui.horizontal(c);
        src.setBackground(Ui.rounded(c, R.color.m3_surface_low, 14, 0));
        src.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        LinearLayout sl = Ui.iconText(c, R.drawable.ic_file, "Xem trích xuất văn bản gốc" + (a.document.pageCount != null && a.document.pageCount > 0 ? " (" + a.document.pageCount + " trang)" : ""), 13, Ui.color(c, R.color.brand), false);
        Ui.addWeighted(src, sl, 0);
        ImageView chev = Ui.icon(c, R.drawable.ic_chevron_down, 18, Ui.color(c, R.color.text_secondary));
        chev.setRotation(showSource ? 180 : 0);
        src.addView(chev);
        src.setOnClickListener(v -> { showSource = !showSource; rerender(); });
        Ui.add(in, src, 14);
        if (showSource) {
            String raw = a.document.rawText != null && !a.document.rawText.isEmpty() ? a.document.rawText
                    : "1. Nội dung trích xuất từ tài liệu " + a.document.title + "\nĐang cập nhật dữ liệu tài liệu...";
            int count = 0;
            for (String line : raw.split("\n")) {
                if (line.trim().isEmpty()) continue;
                if (count++ > 300) { Ui.add(in, Ui.muted(c, "… (đã rút gọn phần còn lại)"), 8); break; }
                TextView t = Ui.text(c, line, 13, R.color.text_secondary, false);
                t.setLineSpacing(0, 1.4f);
                Ui.add(in, t, 8);
            }
        }

        LinearLayout actions = Ui.horizontal(c);
        LinearLayout mm = Ui.iconText(c, R.drawable.ic_mindmap, "Tạo sơ đồ từ ý này", 13, Ui.color(c, R.color.brand), true);
        mm.setGravity(Gravity.CENTER);
        mm.setBackground(Ui.rounded(c, R.color.brand_soft, 14, 0));
        mm.setPadding(Ui.dp(c, 8), Ui.dp(c, 11), Ui.dp(c, 8), Ui.dp(c, 11));
        mm.setOnClickListener(v -> quickMindmap(a));
        Ui.addWeighted(actions, mm, 0);
        LinearLayout qz = Ui.iconText(c, R.drawable.ic_quiz, "Tạo câu hỏi ôn luyện", 13, Ui.color(c, R.color.text_secondary), true);
        qz.setGravity(Gravity.CENTER);
        qz.setBackground(Ui.rounded(c, R.color.surface, 14, R.color.border));
        qz.setPadding(Ui.dp(c, 8), Ui.dp(c, 11), Ui.dp(c, 8), Ui.dp(c, 11));
        qz.setOnClickListener(v -> quickQuiz(a));
        Ui.addWeighted(actions, qz, 8);
        Ui.add(in, actions, 14);
        Ui.add(root, card, 16);
    }

    private void quickFlashcard(ActiveDocument a) {
        List<Flashcard> cards = a.pack.flashcards();
        state.addFlashcard(cards.isEmpty() ? "Khái niệm cốt lõi trong " + a.document.title + "?" : cards.get(0).front,
                cards.isEmpty() ? "Giải thích chi tiết kiến thức trọng tâm từ tài liệu." : cards.get(0).back);
        state.navigate(Screen.FLASHCARD);
    }

    private void quickMindmap(ActiveDocument a) {
        AIMindmap mm = a.pack.mindmap();
        String label = mm != null && !mm.nodes.isEmpty() ? mm.nodes.get(0).label : a.document.title;
        String detail = mm != null && !mm.nodes.isEmpty() && mm.nodes.get(0).detail != null ? mm.nodes.get(0).detail : "Chi tiết nội dung sơ đồ tư duy";
        state.addMindmapNode(label, detail, "root");
        state.navigate(Screen.MINDMAP);
    }

    private void quickQuiz(ActiveDocument a) {
        AIQuiz q = a.pack.quiz();
        if (q != null && !q.questions.isEmpty()) {
            QuizQuestion s = q.questions.get(0);
            state.addQuizQuestion(s.questionText, s.options, s.correctIndex, s.explanation);
        } else {
            state.addQuizQuestion("Câu hỏi ôn tập kiến thức tài liệu " + a.document.title + "?",
                    Arrays.asList("A. Phương án 1", "B. Phương án 2", "C. Phương án 3", "D. Phương án 4"), 0, "Giải thích chi tiết từ hệ thống AI.");
        }
        state.navigate(Screen.QUIZ);
    }

    private String notesSpeech(ActiveDocument a, AINotes notes) {
        StringBuilder sb = new StringBuilder(notes != null && notes.summaryTitle != null ? notes.summaryTitle : a.document.title).append(". ");
        if (notes != null && notes.sections != null && !notes.sections.isEmpty()) {
            for (NotesSection s : notes.sections) {
                sb.append(s.heading).append(". ");
                if (s.items != null) for (NotesItem i : s.items) sb.append(i.label).append(": ").append(i.text).append(". ");
            }
        } else if (a.document.rawText != null) {
            sb.append(a.document.rawText, 0, Math.min(1000, a.document.rawText.length()));
        }
        return sb.toString();
    }

    /** Play → pause → resume, like the web ttsService (Android TTS has no pause, so resume re-queues the rest) */
    private void toggleSpeech(ActiveDocument a, AINotes notes) {
        if (speaking && !paused) {
            paused = true;
            if (tts != null) tts.stop();
            rerender();
            return;
        }
        if (speaking) {
            paused = false;
            speak(spokenText, spokenFrom);
            return;
        }
        spokenText = notesSpeech(a, notes);
        spokenFrom = 0;
        if (tts == null) {
            tts = new TextToSpeech(requireContext(), status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) speak(spokenText, 0);
                else toast("Thiết bị chưa có giọng đọc tiếng Việt.");
            });
        } else if (ttsReady) {
            speak(spokenText, 0);
        }
        toast("🔊 Đang phát giọng đọc AI cho bài tóm tắt...");
    }

    private void stopSpeech() {
        if (tts != null) tts.stop();
        speaking = false;
        paused = false;
        rerender();
    }

    private void speak(String text, int from) {
        tts.setLanguage(new Locale("vi", "VN"));
        tts.setSpeechRate(ttsRate);
        // Engines cap one utterance (~4000 chars): queue chunks and remember where we are for "resume"
        int max = TextToSpeech.getMaxSpeechInputLength() - 100;
        List<int[]> chunks = new ArrayList<>();
        for (int i = from; i < text.length(); i += max) chunks.add(new int[]{i, Math.min(text.length(), i + max)});
        if (chunks.isEmpty()) { stopSpeech(); return; }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { spokenFrom = Integer.parseInt(id); }
            @Override public void onError(String id) { onDone(id); }
            @Override public void onDone(String id) {
                if (Integer.parseInt(id) != chunks.get(chunks.size() - 1)[0] || paused) return;
                Async.main(() -> { speaking = false; spokenFrom = 0; if (isAdded()) rerender(); });
            }
        });
        for (int k = 0; k < chunks.size(); k++) {
            int[] ch = chunks.get(k);
            tts.speak(text.substring(ch[0], ch[1]), k == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, String.valueOf(ch[0]));
        }
        speaking = true;
        paused = false;
        rerender();
    }

    private void shareMarkdown(ActiveDocument a, AINotes notes) {
        if (notes == null || notes.sections == null || notes.sections.isEmpty()) {
            toast("⚠️ Không có nội dung ghi chú để xuất.");
            return;
        }
        StringBuilder md = new StringBuilder("# ").append(notes.summaryTitle != null ? notes.summaryTitle : a.document.title).append("\n\n");
        for (NotesSection s : notes.sections) {
            md.append("## ").append(s.heading).append("\n\n");
            if (s.items != null) for (NotesItem i : s.items) md.append("- **").append(i.label).append(":** ").append(i.text).append('\n');
            md.append('\n');
        }
        md.append("---\n*Xuất từ StudyMind AI*\n");
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/markdown");
        send.putExtra(Intent.EXTRA_SUBJECT, a.document.title + ".md");
        send.putExtra(Intent.EXTRA_TEXT, md.toString());
        startActivity(Intent.createChooser(send, "Xuất ghi chú Markdown"));
        toast("📄 Đã xuất toàn bộ ghi chú ra file Markdown (.md)!");
    }

    private void shareLink(ActiveDocument a) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, "Mình đang học \"" + a.document.title + "\" trên StudyMind AI: " + com.studymind.nativeapp.BuildConfig.WEB_URL);
        startActivity(Intent.createChooser(send, "Chia sẻ tài liệu"));
    }

    // ---------- column 3: AI tutor chat ----------

    private List<KnowledgeGaps.RootGap> gapRoots() {
        ActiveDocument a = state.activeDoc();
        List<KnowledgeGaps.RootGap> roots = KnowledgeGaps.analyze(a.pack, a.quizHistory).roots;
        return roots.subList(0, Math.min(3, roots.size()));
    }

    /** Root gaps from the Knowledge Gap Map steer the Socratic tutor (same text as the web app) */
    private String gapFocus() {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeGaps.RootGap r : gapRoots()) {
            List<String> causes = new ArrayList<>();
            for (KnowledgeGaps.GapNode n : r.explains) if (!n.id.equals(r.node.id)) causes.add(n.label);
            List<String> wrong = new ArrayList<>();
            for (KbMisconception m : r.misconceptions) wrong.add("\"" + m.misconception + "\"");
            sb.append("- ").append(r.node.label).append(r.node.level != null && !r.node.level.isEmpty() ? " (" + r.node.level + ")" : "");
            if (!causes.isEmpty()) sb.append(": gây lỗi ở ").append(String.join(", ", causes));
            if (!wrong.isEmpty()) sb.append("; hiểu sai: ").append(String.join(", ", wrong));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private ChatMessage greeting() {
        ActiveDocument a = state.activeDoc();
        AppUser u = state.auth().getUser();
        if (socratic) {
            List<KnowledgeGaps.RootGap> roots = gapRoots();
            return new ChatMessage(false, "Chế độ Gia sư Socrates: mình sẽ không đưa đáp án, mà đặt câu hỏi để bạn tự suy luận ra. "
                    + (!roots.isEmpty() ? "Kết quả luyện tập cho thấy bạn nên củng cố \"" + roots.get(0).node.label + "\". Theo bạn, phần này nói về điều gì?"
                    : "Bạn đang thắc mắc phần nào trong tài liệu?"));
        }
        return new ChatMessage(false, "Chào " + (u != null ? u.fullName : "bạn") + ", tôi là trợ lý StudyMind AI. Tôi đã sẵn sàng hỗ trợ giải đáp thắc mắc và ôn luyện cùng bạn về tài liệu \""
                + a.document.title + "\".");
    }

    /** "Gia sư StudyMind AI" card */
    private void renderChat(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        card.setCardBackgroundColor(Ui.color(c, R.color.m3_surface_low));
        card.setStrokeWidth(0);
        LinearLayout col = Ui.inner(card);

        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.iconBox(c, R.drawable.ic_brain, R.color.brand_dark, android.R.color.white, 40, 22, 999));
        LinearLayout ht = Ui.vertical(c);
        ht.addView(Ui.text(c, "Gia sư StudyMind AI", 17, R.color.text, true));
        ht.addView(Ui.text(c, "Đồng hành cá nhân hóa", 12, R.color.text_muted, false));
        LinearLayout.LayoutParams htl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htl.setMarginStart(Ui.dp(c, 10));
        head.addView(ht, htl);
        LinearLayout modes = Ui.horizontal(c);
        modes.setBackground(Ui.rounded(c, R.color.surface, 999, 0));
        modes.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
        String[][] ms = {{"qa", "Hỏi đáp"}, {"socratic", "Gia sư Socrates"}};
        for (String[] m : ms) {
            boolean on = socratic == m[0].equals("socratic");
            TextView t = Ui.text(c, m[1], 12, on ? android.R.color.white : R.color.text_secondary, on);
            if (on) { t.setTextColor(0xFFFFFFFF); t.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55)); }
            t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            t.setOnClickListener(v -> switchMode(m[0].equals("socratic")));
            modes.addView(t);
        }
        head.addView(modes);
        col.addView(head);

        if (socratic) {
            LinearLayout warn = Ui.horizontal(c);
            warn.setGravity(Gravity.TOP);
            warn.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 14, 0));
            warn.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            warn.addView(Ui.icon(c, R.drawable.ic_alert, 16, Ui.color(c, R.color.m3_tertiary)));
            TextView wt = Ui.text(c, "Chế độ Socrates: AI sẽ không đưa đáp án, mà đặt các câu hỏi gợi mở từng bước để bạn tự suy luận!", 12, R.color.m3_on_tertiary_fixed, false);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wl.setMarginStart(Ui.dp(c, 8));
            warn.addView(wt, wl);
            Ui.add(col, warn, 12);
            List<KnowledgeGaps.RootGap> roots = gapRoots();
            if (!roots.isEmpty()) {
                List<String> labels = new ArrayList<>();
                for (KnowledgeGaps.RootGap r : roots) labels.add(r.node.label);
                LinearLayout ctx = Ui.horizontal(c);
                ctx.addView(Ui.text(c, "Ngữ cảnh:", 12, R.color.text_muted, false));
                LinearLayout chip = Ui.iconText(c, R.drawable.ic_target, "Đang tập trung vào: " + String.join(", ", labels), 12, Ui.color(c, R.color.brand_dark), false);
                chip.setBackground(Ui.rounded(c, R.color.m3_primary_fixed, 999, 0));
                chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
                ((TextView) chip.getChildAt(1)).setMaxLines(2);
                Ui.addWeighted(ctx, chip, 8);
                Ui.add(col, ctx, 10);
            }
        }

        for (ChatMessage m : messages) {
            LinearLayout row = Ui.horizontal(c);
            row.setGravity(m.fromUser ? Gravity.END : Gravity.START | Gravity.TOP);
            if (!m.fromUser) {
                TextView av = Ui.text(c, "AI", 11, android.R.color.white, true);
                av.setTextColor(0xFFFFFFFF);
                av.setGravity(Gravity.CENTER);
                av.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
                row.addView(av, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
            }
            TextView bubble = Ui.text(c, m.text, 14, R.color.text, false);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            float r = Ui.dp(c, 18);
            bg.setCornerRadii(m.fromUser ? new float[]{r, r, r, r, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, r, r});
            bg.setColor(Ui.color(c, m.fromUser ? R.color.m3_secondary_container : R.color.surface));
            bubble.setBackground(bg);
            bubble.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
            bubble.setLineSpacing(0, 1.3f);
            bubble.setTextIsSelectable(true);
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!m.fromUser) lp.setMarginStart(Ui.dp(c, 8));
            row.addView(bubble, lp);
            Ui.add(col, row, 12);
        }
        if (sending) {
            TextView th = Ui.text(c, "• StudyMind đang suy luận từng bước...", 12, R.color.text_muted, false);
            th.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            Ui.add(col, th, 10);
        }

        Ui.add(col, Ui.text(c, "Gợi ý phản hồi nhanh:", 12, R.color.text_muted, false), 14);
        LinearLayout sugg = Ui.horizontal(c);
        String[] suggestions = socratic
                ? new String[]{"Cho mình một gợi ý nhỏ", "Mình nghĩ là do định nghĩa", "Hỏi mình câu tiếp theo"}
                : new String[]{"Tóm tắt công thức trọng tâm", "Giải thích lại dễ hiểu hơn", "Cho mình ví dụ minh họa"};
        for (String s : suggestions) {
            TextView t = Ui.text(c, s, 12, R.color.text, false);
            t.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.border));
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
            t.setOnClickListener(v -> send(s.equals("Tóm tắt công thức trọng tâm") ? "Hãy tóm tắt ngắn gọn các công thức và khái niệm trọng tâm trong " + a.document.title : s));
            Ui.addWrap(sugg, t, sugg.getChildCount() > 0 ? 8 : 0);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(sugg);
        Ui.add(col, hs, 6);

        LinearLayout inputBox = Ui.horizontal(c);
        inputBox.setBackground(Ui.rounded(c, R.color.surface, 999, R.color.border));
        inputBox.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        EditText field = new EditText(c);
        field.setHint("Nhập câu trả lời hoặc thắc mắc...");
        field.setTextSize(14);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setTextColor(Ui.color(c, R.color.text));
        field.setHintTextColor(Ui.color(c, R.color.text_muted));
        field.setText(draft);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        field.setOnEditorActionListener((t, action, e) -> { draft = ""; send(field.getText().toString()); return true; });
        inputBox.addView(field, new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f));
        android.widget.FrameLayout sendBtn = Ui.iconBox(c, R.drawable.ic_send, R.color.brand_dark, android.R.color.white, 40, 18, 999);
        sendBtn.setOnClickListener(v -> { draft = ""; send(field.getText().toString()); });
        inputBox.addView(sendBtn);
        Ui.add(col, inputBox, 12);

        com.studymind.nativeapp.data.model.Models.BillingInfo b = state.billing();
        if (b != null && !state.isPro() && b.limits != null && b.limits.chat > 0) {
            LinearLayout quota = Ui.horizontal(c);
            int left = Math.max(0, b.limits.chat - (b.usage != null ? b.usage.chat : 0));
            Ui.addWeighted(quota, Ui.iconText(c, R.drawable.ic_zap, "Còn " + left + "/" + b.limits.chat + " lượt hỏi miễn phí hôm nay", 12, Ui.color(c, R.color.text_muted), false), 0);
            TextView up = Ui.text(c, "Nâng cấp Pro để không giới hạn →", 12, R.color.m3_tertiary, true);
            up.setOnClickListener(v -> state.openUpgrade(null));
            Ui.addWrap(quota, up, 8);
            Ui.add(col, quota, 10);
        }
        Ui.add(root, card, 16);
    }
    private void switchMode(boolean toSocratic) {
        if (socratic == toSocratic) return;
        // Each mode starts its own conversation, so Q&A answers never leak into a tutoring session
        socratic = toSocratic;
        messages.clear();
        messages.add(greeting());
        rerender();
    }

    private void send(String question) {
        if (question == null || question.trim().isEmpty() || sending) return;
        List<ChatMessage> history = new ArrayList<>(messages);
        messages.add(new ChatMessage(true, question.trim()));
        sending = true;
        rerender();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        state.chat(question.trim(), history, socratic ? gapFocus() : null, reply -> {
            messages.add(new ChatMessage(false, reply));
            sending = false;
            if (isAdded()) {
                rerender();
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}
