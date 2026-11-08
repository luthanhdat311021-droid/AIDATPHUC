package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.util.HashMap;
import java.util.Map;

/** 1v1 on the same questions; the server grades, and answers only reach a player after they submit. */
public class DuelFragment extends BaseFragment {
    private JsonObject duel;
    private final Map<String, Integer> answers = new HashMap<>();
    private long startedAt;
    private boolean busy;
    private String codeInput = "";

    @Override
    protected Object watchKey() { return "duel"; }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences p = requireContext().getSharedPreferences("studymind_prefs", Context.MODE_PRIVATE);
        String pending = p.getString("pending_duel", null);
        if (pending != null) {
            p.edit().remove("pending_duel").apply();
            load(pending);
        }
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        root.addView(Ui.text(c, "Đấu trường 1 chọi 1", 24, R.color.text, true));
        if (busy) { Ui.add(root, Ui.spinner(c), 20); return; }
        if (duel == null) renderHome(root);
        else if (!duel.get("played").getAsBoolean()) renderPlay(root);
        else renderResult(root);
    }

    private void renderHome(LinearLayout root) {
        Context c = requireContext();
        Ui.add(root, Ui.text(c, "Thách bạn bè làm cùng bộ câu hỏi. Ai đúng nhiều hơn sẽ thắng, bằng điểm thì ai nhanh hơn thắng.", 14, R.color.text_secondary, false), 6);
        String title = state.activeDoc() != null ? state.activeDoc().document.title : null;
        Ui.add(root, Ui.primary(c, title != null ? "Tạo trận từ \"" + title + "\"" : "Mở một bài học để tạo trận", v -> create()), 16);
        Ui.add(root, Ui.label(c, "Đã có mã trận đấu?"), 20);
        EditText code = Ui.field(c, "Nhập mã 8 ký tự", false);
        code.setText(codeInput);
        code.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { codeInput = s.toString().trim(); }
        });
        Ui.add(root, code, 8);
        Ui.add(root, Ui.outline(c, "Vào trận", v -> { if (codeInput.matches("[A-Za-z0-9]{8}")) load(codeInput); else toast("Mã trận đấu gồm 8 ký tự."); }), 8);
    }

    private void renderPlay(LinearLayout root) {
        Context c = requireContext();
        Ui.add(root, Ui.text(c, duel.get("title").getAsString(), 15, R.color.text_secondary, false), 6);
        if (duel.get("isCreator").getAsBoolean()) renderInvite(root);
        for (JsonElement e : duel.getAsJsonArray("questions")) {
            JsonObject q = e.getAsJsonObject();
            String id = q.get("id").getAsString();
            MaterialCardView card = Ui.card(c);
            LinearLayout in = Ui.inner(card);
            in.addView(Ui.text(c, q.get("questionText").getAsString(), 15, R.color.text, true));
            int i = 0;
            for (JsonElement o : q.getAsJsonArray("options")) {
                int idx = i++;
                Integer picked = answers.get(id);
                Ui.add(in, Ui.choice(c, o.getAsString(), picked != null && picked == idx, v -> { answers.put(id, idx); rerender(); }), 8);
            }
            Ui.add(root, card, 12);
        }
        Ui.add(root, Ui.primary(c, "Nộp bài (" + answers.size() + "/" + duel.get("total").getAsInt() + ")", v -> submit()), 16);
    }

    private void renderResult(LinearLayout root) {
        Context c = requireContext();
        int total = duel.get("total").getAsInt();
        String me = duel.get("creatorName").getAsString();
        boolean theirsIn = !duel.get("opponentScore").isJsonNull() && !duel.get("creatorScore").isJsonNull();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.addView(Ui.text(c, scoreLine(me, "creator", total), 15, R.color.text, true));
        String other = duel.get("opponentName").isJsonNull() ? "Đối thủ" : duel.get("opponentName").getAsString();
        Ui.add(in, Ui.text(c, scoreLine(other, "opponent", total), 15, R.color.text, true), 6);
        Ui.add(in, Ui.text(c, theirsIn ? verdict(me, other) : "Đang chờ đối thủ nộp bài.", 16, R.color.brand, true), 12);
        Ui.add(root, card, 12);
        if (!theirsIn) {
            Ui.add(root, Ui.primary(c, "Cập nhật kết quả", v -> load(duel.get("code").getAsString())), 12);
            if (duel.get("isCreator").getAsBoolean()) renderInvite(root);
        }
        Ui.add(root, Ui.outline(c, "Trận mới", v -> { duel = null; answers.clear(); rerender(); }), 8);
        for (JsonElement e : duel.getAsJsonArray("questions")) {
            JsonObject q = e.getAsJsonObject();
            int ok = q.get("correctIndex").getAsInt();
            MaterialCardView qc = Ui.card(c);
            LinearLayout qi = Ui.inner(qc);
            qi.addView(Ui.text(c, q.get("questionText").getAsString(), 14, R.color.text, true));
            if (ok >= 0 && ok < q.getAsJsonArray("options").size()) Ui.add(qi, Ui.text(c, "Đáp án: " + q.getAsJsonArray("options").get(ok).getAsString(), 13, R.color.brand, false), 6);
            if (q.has("explanation") && !q.get("explanation").isJsonNull()) Ui.add(qi, Ui.muted(c, q.get("explanation").getAsString()), 6);
            Ui.add(root, qc, 10);
        }
    }

    private void renderInvite(LinearLayout root) {
        Context c = requireContext();
        String code = duel.get("code").getAsString();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.addView(Ui.label(c, "Mã trận đấu (chạm để sao chép)"));
        android.widget.TextView codeView = Ui.text(c, code, 28, R.color.brand, true);
        codeView.setLetterSpacing(0.1f);
        codeView.setTextIsSelectable(false);
        Ui.add(in, codeView, 4);
        card.setOnClickListener(v -> {
            ((android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(android.content.ClipData.newPlainText("Mã trận đấu", code));
            toast("Đã sao chép mã " + code);
        });
        Ui.add(in, Ui.outline(c, "Gửi lời thách đấu", v -> share()), 10);
        Ui.add(root, card, 12);
    }

    private String scoreLine(String name, String who, int total) {
        JsonElement s = duel.get(who + "Score");
        if (s.isJsonNull()) return name + ": chưa nộp";
        return name + ": " + s.getAsInt() + "/" + total + " (" + duel.get(who + "Ms").getAsInt() / 1000 + " giây)";
    }

    private String verdict(String creator, String opponent) {
        int a = duel.get("creatorScore").getAsInt(), b = duel.get("opponentScore").getAsInt();
        if (a == b) { a = -duel.get("creatorMs").getAsInt(); b = -duel.get("opponentMs").getAsInt(); }
        return a == b ? "Hòa!" : "🏆 " + (a > b ? creator : opponent) + " thắng!";
    }

    private void share() {
        String code = duel.get("code").getAsString();
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                "Mình thách bạn đấu kiến thức \"" + duel.get("title").getAsString() + "\" trên StudyMind! Vào mục Đấu trường, nhập mã " + code
                        + " hoặc mở: com.studymind.nativeapp://duel?code=" + code);
        startActivity(Intent.createChooser(send, "Gửi lời thách đấu"));
    }

    private void create() {
        if (state.activeDoc() == null) { toast("Hãy mở một bài học trước."); return; }
        JsonObject b = new JsonObject();
        b.addProperty("documentId", state.activeDoc().document.id);
        call(() -> state.api().post("/api/v1/duels", b));
    }

    private void load(String code) {
        call(() -> state.api().get("/api/v1/duels/" + code));
    }

    private void submit() {
        int total = duel.get("total").getAsInt();
        if (answers.size() < total) { toast("Hãy trả lời hết " + total + " câu."); return; }
        JsonObject a = new JsonObject();
        for (Map.Entry<String, Integer> e : answers.entrySet()) a.addProperty(e.getKey(), e.getValue());
        JsonObject b = new JsonObject();
        b.add("answers", a);
        b.addProperty("ms", (int) Math.min(Integer.MAX_VALUE, SystemClock.elapsedRealtime() - startedAt));
        String code = duel.get("code").getAsString();
        call(() -> state.api().post("/api/v1/duels/" + code + "/submit", b));
    }

    private void call(Async.Task<JsonObject> task) {
        busy = true;
        rerender();
        Async.run(task, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                busy = false;
                duel = r.getAsJsonObject("duel");
                if (!duel.get("played").getAsBoolean()) {
                    answers.clear();
                    startedAt = SystemClock.elapsedRealtime();
                }
                rerender();
            }
            @Override public void onError(String m) { busy = false; toast(m); rerender(); }
        });
    }
}
