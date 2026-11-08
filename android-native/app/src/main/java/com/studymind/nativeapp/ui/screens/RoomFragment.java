package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.util.Locale;

/**
 * Silent group Pomodoro. Presence is simply this screen being visible: leaving it stops the heartbeat,
 * and 90 seconds later the server breaks the session for the whole group.
 */
public class RoomFragment extends BaseFragment {
    private static final long BEAT_MS = 20_000;

    private SharedPreferences prefs;
    private JsonObject room;
    private String codeInput = "";
    private long endsAt;
    private TextView clock;
    private final Runnable beat = this::beat;
    private final Runnable tick = this::tick;

    @Override
    protected Object watchKey() { return "room"; }

    @Override
    public void onStart() {
        prefs = requireContext().getSharedPreferences("studymind_prefs", Context.MODE_PRIVATE);
        super.onStart();
        beat();
    }

    @Override
    public void onStop() {
        Async.cancel(beat);
        Async.cancel(tick);
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onStop();
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        clock = null;
        root.addView(Ui.text(c, "Phòng học nhóm", 24, R.color.text, true));
        if (room == null) renderHome(root); else renderRoom(root);
    }

    private void renderHome(LinearLayout root) {
        Context c = requireContext();
        Ui.add(root, Ui.text(c, "Cả nhóm cùng tập trung 25 phút. Ai rời màn hình quá 90 giây thì phiên của cả nhóm bị hỏng.", 14, R.color.text_secondary, false), 6);
        Ui.add(root, Ui.primary(c, "Tạo phòng mới", v -> call("/api/v1/rooms")), 16);
        EditText code = Ui.field(c, "Nhập mã phòng 8 ký tự", false);
        code.setText(codeInput);
        code.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { codeInput = s.toString().trim(); }
        });
        Ui.add(root, code, 20);
        Ui.add(root, Ui.outline(c, "Vào phòng", v -> {
            if (codeInput.matches("[A-Za-z0-9]{8}")) call("/api/v1/rooms/" + codeInput + "/join");
            else toast("Mã phòng gồm 8 ký tự.");
        }), 8);
    }

    private void renderRoom(LinearLayout root) {
        Context c = requireContext();
        String code = room.get("code").getAsString();
        boolean running = room.get("running").getAsBoolean();
        Ui.add(root, Ui.text(c, "Mã phòng: " + code, 15, R.color.text_secondary, true), 6);

        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        clock = Ui.text(c, running ? clockText() : "25:00", 44, R.color.brand, true);
        clock.setGravity(android.view.Gravity.CENTER);
        in.addView(clock);
        JsonElement last = room.get("lastResult");
        String note = running ? "Giữ màn hình này mở. Đừng rời đi nhé!"
                : last.isJsonNull() ? "Sẵn sàng khi cả nhóm đã vào phòng."
                : "done".equals(last.getAsString()) ? "🎉 Cả nhóm đã hoàn thành phiên!"
                : "💔 Phiên bị hỏng vì " + room.get("brokenBy").getAsString() + " đã rời đi.";
        TextView n = Ui.text(c, note, 14, R.color.text_secondary, false);
        n.setGravity(android.view.Gravity.CENTER);
        Ui.add(in, n, 8);
        Ui.add(root, card, 12);

        Ui.add(root, Ui.text(c, "🔥 Chuỗi nhóm: " + room.get("streak").getAsInt() + " ngày · " + room.get("sessionsDone").getAsInt() + " phiên hoàn thành", 14, R.color.text, true), 14);
        for (JsonElement e : room.getAsJsonArray("members")) {
            JsonObject m = e.getAsJsonObject();
            Ui.add(root, Ui.text(c, (m.get("active").getAsBoolean() ? "🟢 " : "⚪ ") + m.get("name").getAsString(), 14, R.color.text, false), 8);
        }
        if (!running) {
            Ui.add(root, Ui.primary(c, "Bắt đầu 25 phút", v -> call("/api/v1/rooms/" + code + "/start")), 16);
            Ui.add(root, Ui.outline(c, "Mời bạn", v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Vào phòng học cùng mình trên StudyMind! Mục Phòng học nhóm, mã phòng: " + code), "Mời bạn"))), 8);
        }
        Ui.add(root, Ui.outline(c, "Rời phòng", v -> leave(code)), 8);
    }

    private String clockText() {
        long left = Math.max(0, endsAt - android.os.SystemClock.elapsedRealtime()) / 1000;
        return String.format(Locale.US, "%02d:%02d", left / 60, left % 60);
    }

    private void tick() {
        if (clock == null || room == null || !room.get("running").getAsBoolean()) return;
        clock.setText(clockText());
        // The session ends on the server; ask as soon as the clock runs out instead of waiting for the next beat
        if (endsAt <= android.os.SystemClock.elapsedRealtime()) { Async.cancel(beat); beat(); return; }
        Async.mainDelayed(tick, 1000);
    }

    private void beat() {
        String code = prefs.getString("room_code", null);
        Async.cancel(beat);
        if (code == null) return;
        Async.mainDelayed(beat, BEAT_MS);
        call("/api/v1/rooms/" + code + "/beat");
    }

    private void leave(String code) {
        call("/api/v1/rooms/" + code + "/leave");
    }

    private void call(String path) {
        Async.run(() -> state.api().post(path, null), new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                if (!isAdded()) return;
                room = r.get("room").isJsonObject() ? r.getAsJsonObject("room") : null;
                if (room == null) {
                    prefs.edit().remove("room_code").apply();
                    Async.cancel(beat);
                } else {
                    boolean first = !room.get("code").getAsString().equals(prefs.getString("room_code", null));
                    prefs.edit().putString("room_code", room.get("code").getAsString()).apply();
                    if (first) { Async.cancel(beat); Async.mainDelayed(beat, BEAT_MS); }
                }
                boolean running = room != null && room.get("running").getAsBoolean();
                if (running) endsAt = android.os.SystemClock.elapsedRealtime() + room.get("secondsLeft").getAsInt() * 1000L;
                if (running) requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                rerender();
                Async.cancel(tick);
                tick();
            }
            @Override public void onError(String m) { if (!path.endsWith("/beat")) toast(m); }
        });
    }
}
