package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.net.Uri;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.io.File;

/** Graded test photo → what went wrong at the root, plus flashcards added to the open lesson. */
public class MistakesFragment extends BaseFragment {
    private boolean loading;
    private JsonObject result;
    private Uri cameraUri;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) analyze(uri); });
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> { if (ok && cameraUri != null) analyze(cameraUri); });

    @Override
    protected Object watchKey() { return state.activeDoc() != null ? state.activeDoc().document.id : "none"; }

    @Override
    protected void render(LinearLayout root) {
        if (!requireDocument(root, "phân tích bài làm sai")) return;
        Context c = requireContext();
        root.addView(Ui.text(c, "Chụp bài sai, tìm gốc rễ", 24, R.color.text, true));
        Ui.add(root, Ui.text(c, "Chụp bài kiểm tra đã chấm của \"" + state.activeDoc().document.title + "\". AI tìm kiến thức gốc gây ra lỗi và thêm thẻ ôn vào bài học.", 14, R.color.text_secondary, false), 6);
        if (loading) {
            Ui.add(root, Ui.spinner(c), 20);
            Ui.add(root, Ui.muted(c, "Đang đọc bài làm... Hãy ở lại màn hình này."), 8);
            return;
        }
        Ui.add(root, Ui.primary(c, "Chụp ảnh bài kiểm tra", v -> openCamera()), 16);
        Ui.add(root, Ui.outline(c, "Chọn ảnh hoặc PDF", v -> picker.launch(new String[]{"image/*", "application/pdf"})), 8);
        if (result == null) return;

        int count = 0;
        for (JsonElement e : result.getAsJsonArray("mistakes")) {
            JsonObject m = e.getAsJsonObject();
            MaterialCardView card = Ui.card(c);
            LinearLayout in = Ui.inner(card);
            in.addView(Ui.text(c, "Gốc rễ: " + m.get("rootCause").getAsString(), 15, R.color.brand, true));
            if (!m.get("question").getAsString().isEmpty()) Ui.add(in, Ui.text(c, m.get("question").getAsString(), 13, R.color.text_secondary, false), 6);
            Ui.add(in, Ui.body(c, "Sai ở: " + m.get("error").getAsString()), 6);
            if (!m.get("fix").getAsString().isEmpty()) Ui.add(in, Ui.body(c, "Hiểu đúng: " + m.get("fix").getAsString()), 6);
            Ui.add(root, card, 12);
            count++;
        }
        int cards = result.getAsJsonArray("flashcards").size();
        Ui.add(root, Ui.text(c, count == 0 ? "AI không tìm thấy câu sai nào trong bài này." : "Đã thêm " + cards + " thẻ ôn vào bài học.", 14, R.color.text, true), 16);
        if (cards > 0) Ui.add(root, Ui.outline(c, "Ôn thẻ ngay", v -> state.navigate(AppState.Screen.FLASHCARD)), 8);
    }

    private void openCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException();
            cameraUri = FileProvider.getUriForFile(requireContext(), "com.studymind.nativeapp.files", new File(dir, "bai-sai-" + System.currentTimeMillis() + ".jpg"));
            camera.launch(cameraUri);
        } catch (Exception e) {
            toast("Không thể mở Camera trên thiết bị này.");
        }
    }

    // shortcut: not a background job, leaving the screen hides the result though the cards are still saved; move to AnalysisJobs if users complain
    private void analyze(Uri uri) {
        loading = true;
        result = null;
        rerender();
        state.analyzeMistakes(requireContext().getApplicationContext().getContentResolver(), uri, new Async.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) { loading = false; result = r; rerender(); }
            @Override public void onError(String m) { loading = false; toast(m); rerender(); }
        });
    }
}
