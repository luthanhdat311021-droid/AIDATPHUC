package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState.Screen;
import com.studymind.nativeapp.data.model.Models.ActiveDocument;
import com.studymind.nativeapp.data.model.Models.DocumentItem;
import com.studymind.nativeapp.data.model.Models.Flashcard;
import com.studymind.nativeapp.data.model.Models.StudyPack;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Share;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.Modal;

import java.util.List;
import java.util.Locale;

/** "Học tập trung cùng Flashcard" — FlashcardView.tsx. */
public class FlashcardFragment extends BaseFragment {
    private int index;
    private boolean flipped;
    private boolean generating;
    private String docId;
    private TextToSpeech tts;
    private boolean speaking;

    @Override
    protected Object watchKey() { return state.activeDoc() + ":" + state.history(); }

    @Override
    public void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "ôn tập thẻ ghi nhớ")) return;
        Context c = requireContext();
        ActiveDocument a = state.activeDoc();
        if (!a.document.id.equals(docId)) { docId = a.document.id; index = 0; flipped = false; }
        List<Flashcard> cards = a.pack.flashcards();
        if (index >= cards.size()) index = 0;
        Flashcard card = cards.isEmpty() ? null : cards.get(index);

        // ---- Title header ----
        LinearLayout title = Ui.horizontal(c);
        title.addView(Ui.icon(c, R.drawable.ic_layers, 20, Ui.color(c, R.color.brand)));
        Ui.addWrap(title, Ui.text(c, "Học tập trung cùng Flashcard", 20, R.color.text, true), 8);
        root.addView(title);
        Ui.add(root, Ui.text(c, "Ôn tập thẻ từ vựng và câu hỏi cấu trúc siêu vi tế bào hiệu quả", 13, R.color.text_muted, false), 4);

        LinearLayout tools = Ui.horizontal(c);
        LinearLayout anki = Ui.pillButton(c, R.drawable.ic_download, "Xuất Anki / CSV", R.color.surface, R.color.text_secondary, R.color.border, v -> exportAnki(a, cards));
        ((ImageView) anki.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        anki.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        tools.addView(anki);
        LinearLayout gen = Ui.pillButton(c, 0, generating ? "Đang tạo 10-12 thẻ..." : "Tạo 10-12 Thẻ ghi nhớ", R.color.brand, android.R.color.white, 0, v -> regenerate());
        gen.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        gen.setAlpha(generating ? 0.5f : 1f);
        Ui.addWrap(tools, gen, 8);
        LinearLayout add = Ui.pillButton(c, 0, "Tạo thẻ mới", R.color.surface, R.color.text_secondary, R.color.border, v -> addDialog());
        add.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
        Ui.addWrap(tools, add, 8);
        HorizontalScrollView ts = new HorizontalScrollView(c);
        ts.setHorizontalScrollBarEnabled(false);
        ts.addView(tools);
        Ui.add(root, ts, 16);

        // ---- Card viewer ----
        MaterialCardView viewer = Ui.card(c);
        LinearLayout vi = Ui.inner(viewer);
        vi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        LinearLayout head = Ui.horizontal(c);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Phiên học: ");
        int st = sb.length();
        sb.append(a.document.title);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(c, R.color.text)), st, sb.length(), 0);
        TextView session = Ui.text(c, sb, 12, R.color.text_secondary, false);
        session.setMaxLines(2);
        Ui.addWeighted(head, session, 0);
        TextView counter = Ui.text(c, "Thẻ " + (index + 1) + " / " + Math.max(1, cards.size()), 12, R.color.brand, true);
        counter.setBackground(Ui.rounded(c, R.color.teal_50, 999, R.color.brand_soft));
        counter.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        Ui.addWrap(head, counter, 8);
        if (card != null) {
            ImageView del = Ui.icon(c, R.drawable.ic_trash, 18, Ui.color(c, R.color.slate_400));
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(c).setMessage("Bạn có chắc muốn xóa thẻ ghi nhớ này?")
                    .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> { state.deleteFlashcard(card.id); index = 0; }).show());
            Ui.addWrap(head, del, 10);
        }
        vi.addView(head);
        vi.addView(rule(c, 16));

        LinearLayout face = Ui.vertical(c);
        face.setGravity(Gravity.CENTER);
        face.setMinimumHeight(Ui.dp(c, 260));
        android.graphics.drawable.GradientDrawable fb = Ui.rounded(c, R.color.slate_50, 16, R.color.slate_200);
        fb.setStroke(Ui.dp(c, 2), Ui.color(c, R.color.slate_200));
        face.setBackground(fb);
        face.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        face.setOnClickListener(v -> { flipped = !flipped; rerender(); });
        LinearLayout badge = Ui.horizontal(c);
        badge.setGravity(Gravity.CENTER);
        TextView side = Ui.text(c, flipped ? "ĐÁP ÁN (MẶT SAU)" : "CÂU HỎI (MẶT TRƯỚC)", 11, flipped ? R.color.emerald_700 : R.color.brand, true);
        side.setBackground(Ui.rounded(c, flipped ? R.color.emerald_soft : R.color.brand_soft, 999, 0));
        side.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        badge.addView(side);
        if (card != null) {
            ImageView speak = Ui.icon(c, R.drawable.ic_volume, 16, Ui.color(c, speaking ? R.color.emerald_700 : R.color.slate_400));
            speak.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            speak.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
            speak.setBackground(speaking ? Ui.rounded(c, R.color.emerald_soft, 999, 0) : null);
            speak.setOnClickListener(v -> speak(flipped ? card.back : card.front));
            Ui.addWrap(badge, speak, 6);
        }
        face.addView(badge);
        TextView body = Ui.text(c, card != null ? (flipped ? card.back : card.front) : "Chưa có thẻ nào", flipped ? 15 : 18, R.color.text, true);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.35f);
        Ui.add(face, body, 16);
        LinearLayout hint = Ui.iconText(c, R.drawable.ic_reset, flipped ? "Nhấp để lật về mặt trước" : "Nhấp vào thẻ để lật xem đáp án", 12, Ui.color(c, R.color.slate_400), false);
        ((ImageView) hint.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.addInline(face, hint, 18);
        Ui.add(vi, face, 16);

        vi.addView(rule(c, 18));
        rateButton(vi, "🔴 Chưa thuộc (Khó)", "Ôn lại sau 1 phút", "hard", R.color.rose_50, R.color.rose_soft, R.color.rose, card);
        rateButton(vi, "🟡 Tương đối (Trung bình)", "Ôn lại sau 10 phút", "medium", R.color.amber_50, R.color.amber_soft, R.color.amber_700, card);
        rateButton(vi, "🟢 Đã thuộc (Dễ)", "Ôn lại sau 4 ngày", "easy", R.color.emerald_50, R.color.emerald_soft, R.color.emerald_700, card);
        Ui.add(root, viewer, 16);

        // ---- Deck list ----
        LinearLayout decks = Ui.section(root, "Danh sách các bộ thẻ của bạn", null, null, 20);
        boolean any = false;
        for (DocumentItem d : state.history()) {
            int n = new StudyPack(d.studyPack).flashcards().size();
            if (n == 0) continue;
            any = true;
            boolean active = d.id.equals(a.document.id);
            LinearLayout row = Ui.horizontal(c);
            row.setBackground(Ui.rounded(c, active ? R.color.teal_50 : R.color.surface, 12, active ? R.color.brand_soft : R.color.border));
            row.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            LinearLayout t = Ui.vertical(c);
            t.addView(Ui.text(c, d.title, 13, active ? R.color.brand : R.color.text, true));
            t.addView(Ui.text(c, "Tiến độ: " + n + " thẻ", 11, active ? R.color.brand : R.color.slate_400, false));
            Ui.addWeighted(row, t, 0);
            View play = Ui.iconBox(c, R.drawable.ic_play, active ? R.color.brand : R.color.slate_100, active ? android.R.color.white : R.color.text_muted, 32, 16, 999);
            Ui.addWrap(row, play, 8);
            if (!active) row.setOnClickListener(v -> state.openDocument(d.id, Screen.FLASHCARD));
            Ui.add(decks, row, decks.getChildCount() > 0 ? 10 : 0);
        }
        if (!any) decks.addView(Ui.text(c, "Chưa có bộ thẻ nào. Bấm \"Tạo 10-12 Thẻ ghi nhớ\" để AI soạn thẻ.", 12, R.color.text_muted, false));
    }

    private View rule(Context c, int top) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        lp.topMargin = Ui.dp(c, top);
        v.setLayoutParams(lp);
        return v;
    }

    private void rateButton(LinearLayout parent, String label, String sub, String rating, int bg, int stroke, int fg, Flashcard card) {
        Context c = requireContext();
        LinearLayout b = Ui.vertical(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(c, bg, 12, stroke));
        b.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        b.addView(Ui.text(c, label, 12, fg, true));
        TextView s = Ui.text(c, sub, 10, fg, false);
        s.setAlpha(0.8f);
        b.addView(s);
        b.setOnClickListener(v -> {
            if (card == null) return;
            flipped = false;
            index = index + 1; // next card, wraps in render
            state.reviewFlashcard(card.id, rating);
        });
        Ui.add(parent, b, 10);
    }

    private void speak(String text) {
        if (speaking) {
            if (tts != null) tts.stop();
            speaking = false;
            rerender();
            return;
        }
        Runnable go = () -> {
            tts.setLanguage(new Locale("vi", "VN"));
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onError(String id) { onDone(id); }
                @Override public void onDone(String id) { com.studymind.nativeapp.data.Async.main(() -> { speaking = false; if (isAdded()) rerender(); }); }
            });
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card");
            speaking = true;
            rerender();
        };
        if (tts == null) tts = new TextToSpeech(requireContext(), s -> { if (s == TextToSpeech.SUCCESS) go.run(); else toast("Thiết bị chưa có giọng đọc tiếng Việt."); });
        else go.run();
    }

    private void regenerate() {
        if (generating) return;
        generating = true;
        rerender();
        state.regenerateFlashcards(ok -> { generating = false; index = 0; flipped = false; if (isAdded()) rerender(); });
    }

    /** Same Anki / Quizlet tab-separated deck as exportFlashcardsToAnki on the web */
    private void exportAnki(ActiveDocument a, List<Flashcard> cards) {
        if (cards.isEmpty()) { toast("⚠️ Chưa có thẻ ghi nhớ nào để xuất."); return; }
        String deck = a.document.title;
        StringBuilder sb = new StringBuilder("#separator:tab\n#html:true\n#deck:").append(deck).append("\n#tags column:3\n");
        for (Flashcard f : cards) {
            sb.append('"').append(f.front.replace("\"", "\"\"")).append("\"\t\"").append(f.back.replace("\"", "\"\"")).append("\"\t\"")
                    .append(deck.replaceAll("\\s+", "_")).append('_').append(f.difficulty != null ? f.difficulty : "normal").append("\"\n");
        }
        if (Share.textFile(requireContext(), Share.safeName(deck, 30) + "_anki_deck.txt", "text/tab-separated-values", sb.toString(), "Xuất bộ thẻ Anki / Quizlet")) {
            toast("🗂️ Đã xuất file bộ thẻ (.txt) chuẩn Anki & Quizlet!");
        }
    }

    private void addDialog() {
        Context c = requireContext();
        Modal.show(c, 0, 0, "Tạo Thẻ ghi nhớ mới", null, (body, dialog) -> {
            body.addView(Ui.label(c, "Mặt trước (Câu hỏi/Thuật ngữ):"));
            EditText front = Ui.field(c, "VD: Ty thể tự nhân đôi theo cơ chế nào?", true);
            front.setMinLines(2);
            Ui.add(body, front, 6);
            Ui.add(body, Ui.label(c, "Mặt sau (Đáp án/Giải thích):"), 14);
            EditText back = Ui.field(c, "VD: Phân đôi độc lập tương tự như cơ chế sinh sản ở vi khuẩn.", true);
            back.setMinLines(3);
            Ui.add(body, back, 6);
            Ui.add(body, Ui.label(c, "Đánh giá độ khó ban đầu:"), 14);
            String[] diffs = {"easy", "medium", "hard"};
            int[] chosen = {1};
            Ui.add(body, Ui.select(c, new String[]{"🟢 Dễ (Easy)", "🟡 Trung bình (Medium)", "🔴 Khó (Hard)"}, 1, i -> chosen[0] = i), 6);
            LinearLayout btns = Ui.horizontal(c);
            LinearLayout save = Ui.pillButton(c, R.drawable.ic_check_circle, "Tạo thẻ ngay", R.color.brand, android.R.color.white, 0, v -> {
                String f = front.getText().toString().trim(), b = back.getText().toString().trim();
                if (f.isEmpty() || b.isEmpty()) return;
                state.addFlashcard(f, b, diffs[chosen[0]]);
                dialog.dismiss();
            });
            save.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
            Ui.addWeighted(btns, save, 0);
            Ui.addWeighted(btns, Ui.pillButton(c, 0, "Hủy", R.color.surface, R.color.text_secondary, R.color.border, v -> dialog.dismiss()), 8);
            Ui.add(body, btns, 18);
        });
    }
}
