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
        com.studymind.nativeapp.data.Gamification.Data g = com.studymind.nativeapp.data.Gamification.load();

        // ---- Chips + title ----
        LinearLayout chips = Ui.horizontal(c);
        LinearLayout doc = Ui.iconText(c, R.drawable.ic_sparkles, a.document.title, 13, Ui.color(c, R.color.m3_on_secondary_container), false);
        ((TextView) doc.getChildAt(1)).setMaxLines(1);
        ((TextView) doc.getChildAt(1)).setEllipsize(android.text.TextUtils.TruncateAt.END);
        doc.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        doc.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        Ui.addWeighted(chips, doc, 0);
        LinearLayout streak = Ui.iconText(c, R.drawable.ic_flame, "Chuỗi " + g.streak + " ngày", 13, Ui.color(c, R.color.m3_tertiary), false);
        streak.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        streak.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        Ui.addWrap(chips, streak, 10);
        root.addView(chips);
        LinearLayout title = Ui.horizontal(c);
        Ui.addWeighted(title, Ui.text(c, "Học tập trung cùng Flashcard", 22, R.color.text, true), 0);
        LinearLayout algo = Ui.horizontal(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(c, R.color.brand_dark, 999, 0));
        algo.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7)));
        Ui.addWrap(algo, Ui.text(c, "Thuật toán SM-2", 13, R.color.text_secondary, false), 6);
        Ui.addWrap(title, algo, 8);
        Ui.add(root, title, 12);

        // ---- Actions ----
        LinearLayout tools = Ui.horizontal(c);
        LinearLayout gen = Ui.iconText(c, R.drawable.ic_sparkles, generating ? "Đang tạo 10–12 thẻ..." : "+ Tạo 10–12 thẻ bằng AI", 15, 0xFFFFFFFF, true);
        gen.setGravity(Gravity.CENTER);
        gen.setBackground(Ui.gradient(c, 16, 0xFF005C55, 0xFF0F766E));
        gen.setMinimumHeight(Ui.dp(c, 54));
        gen.setAlpha(generating ? 0.6f : 1f);
        gen.setOnClickListener(v -> regenerate());
        tools.addView(gen, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        LinearLayout add = Ui.iconText(c, R.drawable.ic_plus_circle, "Tạo thẻ mới", 15, Ui.color(c, R.color.text), false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 16, 0));
        add.setMinimumHeight(Ui.dp(c, 54));
        add.setOnClickListener(v -> addDialog());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        al.setMarginStart(Ui.dp(c, 10));
        tools.addView(add, al);
        Ui.add(root, tools, 16);

        // ---- Card viewer ----
        MaterialCardView viewer = Ui.card(c);
        viewer.setRadius(Ui.dp(c, 28));
        viewer.setCardElevation(Ui.dp(c, 4));
        viewer.setStrokeWidth(0);
        LinearLayout vi = Ui.inner(viewer);
        vi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 18));
        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.pill(c, "Thẻ " + (cards.isEmpty() ? 0 : index + 1) + " / " + cards.size(), R.color.m3_primary_fixed, R.color.brand_dark));
        int session = g.cardsReviewed / Math.max(1, cards.size()) + 1;
        TextView sess = Ui.text(c, "Phiên ôn tập #" + session, 13, R.color.text_secondary, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMarginStart(Ui.dp(c, 10));
        head.addView(sess, sl);
        ImageView exp = Ui.icon(c, R.drawable.ic_download, 20, Ui.color(c, R.color.text_secondary));
        exp.setOnClickListener(v -> exportAnki(a, cards));
        head.addView(exp);
        if (card != null) {
            ImageView speak = Ui.icon(c, R.drawable.ic_volume, 20, Ui.color(c, speaking ? R.color.brand : R.color.text_secondary));
            speak.setOnClickListener(v -> speak(flipped ? card.back : card.front));
            Ui.addWrap(head, speak, 14);
            ImageView del = Ui.icon(c, R.drawable.ic_trash, 20, Ui.color(c, R.color.m3_error));
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(c).setMessage("Bạn có chắc muốn xóa thẻ ghi nhớ này?")
                    .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> { state.deleteFlashcard(card.id); index = 0; }).show());
            Ui.addWrap(head, del, 14);
        }
        vi.addView(head);

        LinearLayout face = Ui.vertical(c);
        face.setMinimumHeight(Ui.dp(c, 220));
        face.setOnClickListener(v -> { flipped = !flipped; rerender(); });
        LinearLayout side = Ui.horizontal(c);
        View sd = new View(c);
        sd.setBackground(Ui.rounded(c, flipped ? R.color.brand_dark : R.color.m3_tertiary, 999, 0));
        side.addView(sd, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
        TextView sideT = Ui.text(c, flipped ? "MẶT SAU: ĐÁP ÁN CHÍNH XÁC" : "MẶT TRƯỚC: CÂU HỎI", 13, flipped ? R.color.brand_dark : R.color.m3_tertiary, true);
        sideT.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stl.setMarginStart(Ui.dp(c, 8));
        side.addView(sideT, stl);
        if (card != null) {
            String diff = "hard".equals(card.difficulty) ? "Khó" : "easy".equals(card.difficulty) ? "Dễ" : "Vừa";
            side.addView(Ui.pill(c, "Độ khó: " + diff, R.color.m3_secondary_container, R.color.m3_on_secondary_container));
        }
        Ui.add(face, side, 18);

        if (card == null) {
            TextView none = Ui.text(c, "Chưa có thẻ nào. Bấm \"Tạo 10–12 thẻ bằng AI\" để AI soạn thẻ.", 16, R.color.text_muted, false);
            none.setGravity(Gravity.CENTER);
            Ui.add(face, none, 40);
        } else if (!flipped) {
            TextView q = Ui.text(c, card.front, 20, R.color.text, true);
            q.setLineSpacing(0, 1.35f);
            q.setGravity(Gravity.CENTER);
            q.setPadding(Ui.dp(c, 8), Ui.dp(c, 28), Ui.dp(c, 8), Ui.dp(c, 28));
            Ui.add(face, q, 10);
        } else {
            String[] parts = card.back.split("\\n+|(?<=[.;])\\s+(?=\\p{Lu})");
            LinearLayout box = Ui.vertical(c);
            box.setBackground(Ui.rounded(c, R.color.m3_surface_low, 18, 0));
            box.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            if (parts.length <= 1) {
                TextView t = Ui.text(c, card.back, 16, R.color.text, false);
                t.setLineSpacing(0, 1.35f);
                box.addView(t);
            } else {
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].trim().isEmpty()) continue;
                    LinearLayout li = Ui.horizontal(c);
                    li.setGravity(Gravity.TOP);
                    TextView n = Ui.text(c, String.valueOf(i + 1), 12, android.R.color.white, true);
                    n.setTextColor(0xFFFFFFFF);
                    n.setGravity(Gravity.CENTER);
                    n.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
                    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
                    nl.topMargin = Ui.dp(c, 1);
                    li.addView(n, nl);
                    TextView t = Ui.text(c, parts[i].trim(), 16, R.color.text, false);
                    t.setLineSpacing(0, 1.3f);
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tl.setMarginStart(Ui.dp(c, 12));
                    li.addView(t, tl);
                    Ui.add(box, li, box.getChildCount() > 0 ? 12 : 0);
                }
            }
            Ui.add(face, box, 14);
            if (card.difficulty != null) {
                LinearLayout tip = Ui.iconText(c, R.drawable.ic_lightbulb, "Mẹo: đọc to đáp án và tự giải thích lại bằng lời của bạn để nhớ lâu hơn.", 13, Ui.color(c, R.color.brand_dark), true);
                ((TextView) tip.getChildAt(1)).setMaxLines(3);
                tip.setBackground(Ui.rounded(c, R.color.teal_50, 18, 0));
                tip.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
                Ui.add(face, tip, 12);
            }
        }
        LinearLayout hint = Ui.iconText(c, R.drawable.ic_flip, flipped ? "Chạm vào thẻ để lật xem câu hỏi" : "Chạm vào thẻ để lật xem đáp án", 13, Ui.color(c, R.color.text_secondary), false);
        hint.setGravity(Gravity.CENTER);
        Ui.add(face, hint, 18);
        Ui.add(vi, face, 4);
        Ui.add(root, viewer, 20);

        // ---- Rating ----
        LinearLayout rh = Ui.horizontal(c);
        TextView rl = Ui.text(c, "ĐÁNH GIÁ MỨC ĐỘ NHỚ", 13, R.color.text_secondary, true);
        rl.setLetterSpacing(0.06f);
        Ui.addWeighted(rh, rl, 0);
        rh.addView(Ui.text(c, "Khoảng cách lặp kế tiếp", 13, R.color.text_secondary, false));
        Ui.add(root, rh, 20);
        LinearLayout rates = Ui.horizontal(c);
        rateButton(rates, R.drawable.ic_close, "Chưa thuộc", "Sau 1 phút", "hard", R.color.rose_soft, 0xFFBA1A1A, card);
        rateButton(rates, R.drawable.ic_refresh, "Tương đối", "Sau 10 phút", "medium", R.color.orange_100, 0xFF734700, card);
        rateButton(rates, R.drawable.ic_check_circle, "Đã thuộc", "Sau 4 ngày", "easy", R.color.teal_200, 0xFF005C55, card);
        Ui.add(root, rates, 10);

        // ---- Deck list ----
        LinearLayout dh = Ui.horizontal(c);
        LinearLayout dt = Ui.iconText(c, R.drawable.ic_layers, "Các bộ thẻ ghi nhớ của bạn", 18, Ui.color(c, R.color.text), true);
        Ui.addWeighted(dh, dt, 0);
        TextView newDeck = Ui.text(c, "+ Tạo bộ mới", 14, R.color.brand, true);
        newDeck.setOnClickListener(v -> state.navigate(Screen.IMPORT));
        dh.addView(newDeck);
        Ui.add(root, dh, 28);
        boolean any = false;
        boolean alt = false;
        for (DocumentItem d : state.history()) {
            List<Flashcard> fl = new StudyPack(d.studyPack).flashcards();
            int n = fl.size();
            if (n == 0) continue;
            any = true;
            int mastered = 0;
            for (Flashcard f : fl) if ("easy".equals(f.difficulty)) mastered++;
            int pct = mastered * 100 / n;
            boolean active = d.id.equals(a.document.id);
            int accent = alt ? 0xFF734700 : 0xFF005C55;
            boolean teal = !alt;
            alt = !alt;
            LinearLayout deck = Ui.vertical(c);
            deck.setBackground(Ui.rounded(c, active ? R.color.m3_surface_container : R.color.m3_surface_low, 22, 0));
            deck.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16));
            LinearLayout top = Ui.horizontal(c);
            top.setGravity(Gravity.TOP);
            View ddot = new View(c);
            android.graphics.drawable.GradientDrawable dd = new android.graphics.drawable.GradientDrawable();
            dd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dd.setColor(accent);
            ddot.setBackground(dd);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8));
            dl.topMargin = Ui.dp(c, 10);
            top.addView(ddot, dl);
            LinearLayout tx = Ui.vertical(c);
            tx.addView(Ui.text(c, d.title, 18, R.color.text, true));
            String subj = null;
            if (d.tags != null) for (String t : d.tags) if (!t.contains("Engine") && !t.contains("Speech") && !t.contains("Extractor")) { subj = t; break; }
            Ui.add(tx, Ui.text(c, n + " thẻ ghi nhớ • " + (subj != null ? subj : "Tự động xếp lịch ôn"), 14, R.color.text_secondary, false), 4);
            LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            txl.setMarginStart(Ui.dp(c, 12));
            top.addView(tx, txl);
            top.addView(Ui.pill(c, pct + "%", teal ? R.color.m3_primary_fixed : R.color.m3_tertiary_fixed, teal ? R.color.brand_dark : R.color.m3_on_tertiary_fixed));
            deck.addView(top);
            View track = Ui.track(c, pct, teal ? R.color.brand_dark : R.color.m3_tertiary, 10);
            Ui.add(deck, track, 14);
            LinearLayout foot = Ui.horizontal(c);
            Ui.addWeighted(foot, Ui.iconText(c, R.drawable.ic_timer, "Hôm nay: " + (n - mastered) + " thẻ cần lặp lại", 13, Ui.color(c, R.color.text_secondary), false), 0);
            LinearLayout go = Ui.iconText(c, 0, active ? "Đang ôn" : "Ôn tập ngay", 14, accent, true);
            go.addView(Ui.icon(c, R.drawable.ic_arrow_right, 16, accent));
            foot.addView(go);
            Ui.add(deck, foot, 12);
            if (!active) deck.setOnClickListener(v -> state.openDocument(d.id, Screen.FLASHCARD));
            Ui.add(root, deck, 12);
        }
        if (!any) Ui.add(root, Ui.text(c, "Chưa có bộ thẻ nào. Bấm \"Tạo 10–12 thẻ bằng AI\" để AI soạn thẻ.", 13, R.color.text_muted, false), 12);
    }

    private void rateButton(LinearLayout row, int icon, String label, String sub, String rating, int bg, int fg, Flashcard card) {
        Context c = requireContext();
        LinearLayout b = Ui.vertical(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(c, bg, 18, 0));
        b.setPadding(Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12));
        b.addView(Ui.icon(c, icon, 24, fg));
        TextView l = Ui.text(c, label, 15, R.color.text, true);
        l.setTextColor(fg);
        Ui.add(b, l, 4).setGravity(Gravity.CENTER);
        TextView s = Ui.text(c, sub, 13, R.color.text, false);
        s.setTextColor(fg);
        s.setAlpha(0.85f);
        s.setGravity(Gravity.CENTER);
        b.addView(s);
        b.setAlpha(card == null ? 0.5f : 1f);
        b.setOnClickListener(v -> {
            if (card == null) return;
            flipped = false;
            index = index + 1; // next card, wraps in render
            state.reviewFlashcard(card.id, rating);
        });
        Ui.addWeighted(row, b, row.getChildCount() > 0 ? 10 : 0);
    }

    private View rule(Context c, int top) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        lp.topMargin = Ui.dp(c, top);
        v.setLayoutParams(lp);
        return v;
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
