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

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "bắt đầu học")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        if (!a.document.id.equals(docId)) {
            docId = a.document.id;
            socratic = false;
            messages.clear();
            messages.add(greeting());
        }
        root.setPadding(0, 0, 0, Ui.dp(c, 24));

        // ---- Top document header bar ----
        LinearLayout bar = Ui.horizontal(c);
        bar.setBackgroundColor(Ui.color(c, R.color.surface));
        bar.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        bar.addView(Ui.icon(c, R.drawable.ic_file, 16, Ui.color(c, R.color.brand)));
        TextView src = Ui.text(c, "Nguồn: " + a.document.title, 12, R.color.text, true);
        src.setSingleLine(true);
        src.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMarginStart(Ui.dp(c, 8));
        bar.addView(src, sl);
        bar.addView(toolIcon(c, R.drawable.ic_download, v -> shareMarkdown(a, a.pack.notes())));
        bar.addView(toolIcon(c, R.drawable.ic_network, v -> shareLink(a)));
        bar.addView(toolIcon(c, R.drawable.ic_refresh, v -> { state.openDocument(a.document.id, null); toast("Đã làm mới phân tích cho tài liệu!"); }));
        root.addView(bar);
        root.addView(divider(c));

        // ---- Readiness banner → prerequisites ----
        PrerequisiteAnalysis pre = a.pack.prerequisites();
        LinearLayout banner = Ui.horizontal(c);
        banner.setBackgroundColor(Ui.color(c, R.color.amber_50));
        banner.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        banner.addView(Ui.icon(c, R.drawable.ic_compass, 16, 0xFFD97706));
        SpannableStringBuilder bt = new SpannableStringBuilder();
        if (pre != null && !pre.prerequisites.isEmpty()) {
            int critical = 0;
            for (Prerequisite p : pre.prerequisites) if ("critical".equals(p.priority)) critical++;
            bt.append("Bài này cần ");
            int st = bt.length();
            bt.append(critical + " kiến thức nền bắt buộc");
            bt.setSpan(new StyleSpan(Typeface.BOLD), st, bt.length(), 0);
            bt.append(" • Làm bài test chẩn đoán " + pre.diagnosticPreTest.questions.size() + " câu trước khi học");
        } else {
            bt.append("Kiểm tra bạn đã đủ kiến thức nền để học tài liệu này chưa");
        }
        TextView btv = Ui.text(c, bt, 12, R.color.text, false);
        btv.setTextColor(0xFF78350F);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bl.setMarginStart(Ui.dp(c, 8));
        banner.addView(btv, bl);
        banner.addView(Ui.icon(c, R.drawable.ic_chevron_right, 16, 0xFF78350F));
        banner.setOnClickListener(v -> state.navigate(Screen.PREREQUISITE));
        root.addView(banner);
        View amberRule = new View(c);
        amberRule.setBackgroundColor(0xFFFDE68A);
        root.addView(amberRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));

        renderSource(root, a);
        root.addView(divider(c));
        renderNotes(root, a);
        root.addView(divider(c));
        renderChat(root, a);
    }

    private View toolIcon(Context c, int icon, View.OnClickListener l) {
        ImageView iv = Ui.icon(c, icon, 18, Ui.color(c, R.color.text_secondary));
        iv.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
        iv.setOnClickListener(l);
        return iv;
    }

    private static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.border));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));
        return v;
    }

    private LinearLayout column(Context c, LinearLayout root) {
        LinearLayout col = Ui.vertical(c);
        col.setBackgroundColor(Ui.color(c, R.color.surface));
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        root.addView(col);
        return col;
    }

    // ---------- column 1: source reader ----------

    private void renderSource(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        LinearLayout col = column(c, root);
        TextView h = Ui.text(c, a.document.title, 16, R.color.text, true);
        col.addView(h);
        View hr = divider(c);
        ((LinearLayout.LayoutParams) hr.getLayoutParams()).topMargin = Ui.dp(c, 8);
        col.addView(hr);

        String raw = a.document.rawText != null && !a.document.rawText.isEmpty() ? a.document.rawText
                : "1. Nội dung trích xuất từ tài liệu " + a.document.title + "\nĐang cập nhật dữ liệu tài liệu...";
        int count = 0;
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (count++ > 300) { Ui.add(col, Ui.muted(c, "… (đã rút gọn phần còn lại)"), 8); break; }
            TextView t = Ui.text(c, line, 13, R.color.text_secondary, false);
            t.setLineSpacing(0, 1.4f);
            if (line.startsWith("\"") || line.startsWith("“")) {
                t.setTextColor(0xFF451A03);
                t.setBackground(quoteBg(c));
                t.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            }
            Ui.add(col, t, 10);
        }

        View hr2 = divider(c);
        ((LinearLayout.LayoutParams) hr2.getLayoutParams()).topMargin = Ui.dp(c, 16);
        col.addView(hr2);
        LinearLayout quick = Ui.horizontal(c);
        quick.addView(quickPill(c, "Tạo Thẻ ghi nhớ", v -> quickFlashcard(a)));
        Ui.addWrap(quick, quickPill(c, "Giải thích thuật ngữ", v -> send("Giải thích thuật ngữ và công thức chính trong " + a.document.title)), 8);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(c);
        row.addView(quick);
        Ui.addWrap(row, quickPill(c, "Vẽ sơ đồ nhánh", v -> quickMindmap(a)), 8);
        hs.addView(row);
        Ui.add(col, hs, 14);
    }

    /** bg-amber-50 border-l-4 border-amber-400 rounded-r-xl quote block */
    private static android.graphics.drawable.Drawable quoteBg(Context c) {
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(Ui.color(c, R.color.amber_50));
        float r = Ui.dp(c, 12);
        fill.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        android.graphics.drawable.GradientDrawable edge = new android.graphics.drawable.GradientDrawable();
        edge.setColor(0xFFFBBF24);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{fill, edge});
        ld.setLayerWidth(1, Ui.dp(c, 4));
        ld.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
        return ld;
    }

    private View quickPill(Context c, String label, View.OnClickListener l) {
        TextView t = Ui.text(c, label, 11, R.color.brand, true);
        t.setBackground(Ui.rounded(c, R.color.teal_50, 999, R.color.brand_soft));
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
        t.setOnClickListener(l);
        return t;
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

    // ---------- column 2: AI notes ----------

    private void renderNotes(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        LinearLayout col = column(c, root);
        AINotes notes = a.pack.notes();
        col.addView(Ui.iconText(c, R.drawable.ic_sparkles, "GHI CHÚ TÓM TẮT AI", 12, Ui.color(c, R.color.brand), true));
        View hr = divider(c);
        ((LinearLayout.LayoutParams) hr.getLayoutParams()).topMargin = Ui.dp(c, 12);
        col.addView(hr);

        // TTS narration bar
        LinearLayout player = Ui.vertical(c);
        player.setBackground(Ui.rounded(c, R.color.teal_50, 12, R.color.brand_soft));
        player.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        LinearLayout ptop = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, speaking && !paused ? R.color.emerald : R.color.slate_300, 999, 0));
        ptop.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)));
        TextView status = Ui.text(c, speaking ? (paused ? "⏸️ Tạm dừng đọc" : "🔊 Đang đọc giọng nói...") : "🎧 Nghe đọc bài tóm tắt", 12, R.color.teal_900, true);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stl.setMarginStart(Ui.dp(c, 8));
        ptop.addView(status, stl);
        LinearLayout rates = Ui.horizontal(c);
        rates.setBackground(Ui.rounded(c, R.color.surface, 6, R.color.brand_soft));
        rates.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        for (float r : new float[]{1.0f, 1.25f, 1.5f}) {
            boolean on = ttsRate == r;
            TextView rt = Ui.text(c, (r == 1.0f ? "1" : String.valueOf(r)) + "x", 10, on ? android.R.color.white : R.color.teal_900, true);
            rt.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
            if (on) rt.setBackground(Ui.rounded(c, R.color.brand, 4, 0));
            rt.setOnClickListener(v -> { ttsRate = r; if (tts != null) tts.setSpeechRate(r); toast("Tốc độ đọc giọng nói: " + r + "x"); rerender(); });
            rates.addView(rt);
        }
        ptop.addView(rates);
        player.addView(ptop);

        LinearLayout pbtns = Ui.horizontal(c);
        LinearLayout play = Ui.pillButton(c, speaking && !paused ? R.drawable.ic_pause : R.drawable.ic_play,
                speaking && !paused ? "Tạm dừng" : paused ? "Tiếp tục nghe" : "Nghe tóm tắt", R.color.brand, android.R.color.white, 0, v -> toggleSpeech(a, notes));
        play.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        play.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        Ui.addWeighted(pbtns, play, 0);
        if (speaking) {
            LinearLayout stop = Ui.pillButton(c, R.drawable.ic_stop, "", R.color.surface, R.color.rose, R.color.rose_soft, v -> stopSpeech());
            stop.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 10), Ui.dp(c, 7));
            Ui.addWrap(pbtns, stop, 8);
        }
        LinearLayout md = Ui.pillButton(c, R.drawable.ic_download, "Xuất .md", R.color.surface, R.color.text_secondary, R.color.border, v -> shareMarkdown(a, notes));
        md.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        ((ImageView) md.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.addWrap(pbtns, md, 8);
        Ui.add(player, pbtns, 10);
        Ui.add(col, player, 16);

        Ui.add(col, Ui.text(c, notes != null && notes.summaryTitle != null ? notes.summaryTitle : a.document.title, 16, R.color.text, true), 18);
        if (notes == null || notes.sections == null || notes.sections.isEmpty()) {
            Ui.add(col, Ui.muted(c, "Tài liệu này chưa có ghi chú tóm tắt."), 8);
        } else {
            for (NotesSection sec : notes.sections) {
                Ui.add(col, Ui.text(c, sec.heading.toUpperCase(new Locale("vi")), 12, R.color.text_muted, true), 16);
                if (sec.items == null) continue;
                for (NotesItem item : sec.items) {
                    LinearLayout li = Ui.horizontal(c);
                    li.setGravity(Gravity.TOP);
                    View bullet = new View(c);
                    bullet.setBackground(Ui.rounded(c, R.color.brand, 999, 0));
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(c, 6), Ui.dp(c, 6));
                    bp.topMargin = Ui.dp(c, 7);
                    li.addView(bullet, bp);
                    SpannableStringBuilder sb = new SpannableStringBuilder(item.label + ": ");
                    sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
                    sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.text)), 0, sb.length(), 0);
                    sb.append(item.text);
                    TextView t = Ui.text(c, sb, 13, R.color.text_secondary, false);
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tl.setMarginStart(Ui.dp(c, 8));
                    li.addView(t, tl);
                    Ui.add(col, li, 8);
                }
            }
        }

        View hr2 = divider(c);
        ((LinearLayout.LayoutParams) hr2.getLayoutParams()).topMargin = Ui.dp(c, 16);
        col.addView(hr2);
        LinearLayout actions = Ui.horizontal(c);
        TextView mm = Ui.text(c, "Tạo sơ đồ tư duy từ ý này", 12, R.color.brand, true);
        mm.setGravity(Gravity.CENTER);
        mm.setBackground(Ui.rounded(c, R.color.brand_soft, 8, 0));
        mm.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        mm.setOnClickListener(v -> quickMindmap(a));
        Ui.addWeighted(actions, mm, 0);
        TextView qz = Ui.text(c, "Tạo câu hỏi ôn luyện", 12, R.color.text_secondary, true);
        qz.setGravity(Gravity.CENTER);
        qz.setBackground(Ui.rounded(c, R.color.surface, 8, R.color.border));
        qz.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        qz.setOnClickListener(v -> quickQuiz(a));
        Ui.addWeighted(actions, qz, 8);
        Ui.add(col, actions, 14);
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

    private void renderChat(LinearLayout root, ActiveDocument a) {
        Context c = requireContext();
        LinearLayout col = column(c, root);

        LinearLayout modes = Ui.horizontal(c);
        modes.setBackground(Ui.rounded(c, R.color.slate_100, 8, 0));
        modes.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        String[][] ms = {{"qa", "Hỏi đáp"}, {"socratic", "Gia sư Socrates"}};
        for (String[] m : ms) {
            boolean on = socratic == m[0].equals("socratic");
            TextView t = Ui.text(c, m[1], 12, on ? R.color.brand : R.color.text_muted, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
            if (on) t.setBackground(Ui.rounded(c, R.color.surface, 6, 0));
            t.setOnClickListener(v -> switchMode(m[0].equals("socratic")));
            Ui.addWeighted(modes, t, m[0].equals("qa") ? 0 : 4);
        }
        col.addView(modes);
        if (socratic) {
            List<KnowledgeGaps.RootGap> roots = gapRoots();
            SpannableStringBuilder sb = new SpannableStringBuilder("Gia sư không đưa đáp án, chỉ đặt câu hỏi gợi mở để bạn tự suy luận.");
            if (!roots.isEmpty()) {
                List<String> labels = new ArrayList<>();
                for (KnowledgeGaps.RootGap r : roots) labels.add(r.node.label);
                sb.append(" Đang tập trung vào: ");
                int st = sb.length();
                sb.append(String.join(", ", labels));
                sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), 0);
                sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFBE123C), st, sb.length(), 0);
                sb.append(".");
            }
            Ui.add(col, Ui.text(c, sb, 11, R.color.text_muted, false), 8);
        }
        View hr = divider(c);
        ((LinearLayout.LayoutParams) hr.getLayoutParams()).topMargin = Ui.dp(c, 12);
        col.addView(hr);

        for (ChatMessage m : messages) {
            LinearLayout row = Ui.horizontal(c);
            row.setGravity(m.fromUser ? Gravity.END : Gravity.START | Gravity.TOP);
            if (!m.fromUser) {
                TextView av = Ui.text(c, "AI", 10, android.R.color.white, true);
                av.setGravity(Gravity.CENTER);
                av.setBackground(Ui.rounded(c, R.color.brand, 999, 0));
                row.addView(av, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
            }
            TextView bubble = Ui.text(c, m.text, 13, m.fromUser ? android.R.color.white : R.color.text, false);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            float r = Ui.dp(c, 16);
            bg.setCornerRadii(m.fromUser ? new float[]{r, r, r, r, 0, 0, r, r} : new float[]{r, r, r, r, r, r, r, r});
            bg.setColor(Ui.color(c, m.fromUser ? R.color.brand : R.color.slate_100));
            bubble.setBackground(bg);
            bubble.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            bubble.setTextIsSelectable(true);
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!m.fromUser) lp.setMarginStart(Ui.dp(c, 10));
            row.addView(bubble, lp);
            Ui.add(col, row, 12);
        }
        if (sending) Ui.add(col, Ui.text(c, "Đang suy nghĩ...", 12, R.color.slate_400, false), 10);

        if (!socratic) {
            TextView suggestion = Ui.text(c, "\"Hãy tóm tắt ngắn gọn các công thức và kiến thức trọng tâm...\"", 11, R.color.brand, false);
            suggestion.setBackground(Ui.rounded(c, R.color.teal_50, 12, R.color.brand_soft));
            suggestion.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            suggestion.setOnClickListener(v -> send("Hãy tóm tắt ngắn gọn các công thức và khái niệm trọng tâm trong " + a.document.title));
            Ui.add(col, suggestion, 14);
        }

        View hr2 = divider(c);
        ((LinearLayout.LayoutParams) hr2.getLayoutParams()).topMargin = Ui.dp(c, 14);
        col.addView(hr2);
        android.widget.FrameLayout inputBox = new android.widget.FrameLayout(c);
        EditText field = Ui.field(c, "Nhập câu hỏi học tập tại đây...", false);
        field.setBackground(Ui.rounded(c, R.color.slate_50, 999, R.color.border));
        field.setPadding(Ui.dp(c, 16), Ui.dp(c, 11), Ui.dp(c, 48), Ui.dp(c, 11));
        field.setText(draft);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        field.setOnEditorActionListener((t, action, e) -> { draft = ""; send(field.getText().toString()); return true; });
        inputBox.addView(field);
        android.widget.FrameLayout sendBtn = Ui.iconBox(c, R.drawable.ic_send, R.color.brand, android.R.color.white, 30, 14, 999);
        android.widget.FrameLayout.LayoutParams sp = new android.widget.FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.END | Gravity.CENTER_VERTICAL);
        sp.setMarginEnd(Ui.dp(c, 6));
        sendBtn.setLayoutParams(sp);
        sendBtn.setOnClickListener(v -> { draft = ""; send(field.getText().toString()); });
        inputBox.addView(sendBtn);
        Ui.add(col, inputBox, 12);
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
