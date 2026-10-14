package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.FileIngest;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

import java.io.File;
import java.io.InputStream;

/** "Nhập tài liệu học tập" — ImportView.tsx: source tabs, drop zone / inputs, status, AI output config. */
public class ImportFragment extends BaseFragment {
    private static final String[][] SOURCES = {
            {"doc", "PDF / Word / PPT"}, {"image", "Ảnh / Ảnh chụp sách"}, {"video", "Video / YouTube Link"},
            {"url", "Liên kết Web / Bài báo"}, {"text", "Nhập Văn bản thô"}};
    private static final int[] SOURCE_ICONS = {R.drawable.ic_file, R.drawable.ic_image, R.drawable.ic_video, R.drawable.ic_link, R.drawable.ic_edit};
    private static final String[] LANGUAGES = {"Tiếng Việt (Mặc định)", "Tiếng Anh (English)", "Song ngữ (Việt - Anh)"};
    private static final String[] OUTPUTS = {"Ghi chú tóm tắt có cấu trúc", "Sơ đồ tư duy trực quan (Mindmap)",
            "Thẻ ghi nhớ thông minh (Flashcards)", "Đề kiểm tra trắc nghiệm AI (Quiz)"};
    private static final String[] DEPTHS = {"Tóm lược nhanh", "Tiêu chuẩn", "Chuyên sâu"};
    private static final String[] SUBJECTS = {"Chung", "Toán học", "Vật lý", "Hóa học", "Sinh học", "Công nghệ thông tin",
            "Tiếng Anh", "Lịch sử & Địa lý", "Kinh tế & Quản trị", "Y Dược & Sức khỏe", "+ Thêm môn học khác..."};

    private int source = 0;
    private Uri picked;
    private String pickedName;
    private long pickedSize;
    private Bitmap preview;
    private String videoUrl = "", webUrl = "", rawText = "", customSubject = "";
    private int language = 0, subject = 0;
    private String depth = "Tiêu chuẩn";
    private final boolean[] outputs = {true, true, true, true};

    private boolean processing;
    private String statusState = "idle"; // idle | working | done | error
    private String statusMessage = "";
    private int progress;
    /** The creeping bar stays below this until the server reports the next step */
    private int creepCap = 20;
    private final Runnable creep = this::creepProgress;
    private Uri cameraUri;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) accept(uri); });

    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && cameraUri != null) {
                    source = 1;
                    accept(cameraUri);
                    toast("📸 Đã chụp ảnh tài liệu thành công!");
                }
            });

    @Override
    protected Object watchKey() { return state.isPro() ? "pro" : "free"; }

    @Override
    public void onStop() {
        Async.cancel(creep);
        super.onStop();
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        root.addView(Ui.text(c, "Nhập tài liệu học tập", 20, R.color.text, true));
        Ui.add(root, Ui.text(c, "Tải lên bất kỳ tài liệu nào để bắt đầu quá trình trích xuất kiến thức bằng AI", 13, R.color.text_muted, false), 4);

        // ---- Source tabs (scroll horizontally like the web pill row) ----
        LinearLayout tabs = Ui.horizontal(c);
        for (int i = 0; i < SOURCES.length; i++) {
            int idx = i;
            boolean active = source == i;
            LinearLayout tab = Ui.iconText(c, SOURCE_ICONS[i], SOURCES[i][1], 13, Ui.color(c, active ? R.color.brand : R.color.text_secondary), true);
            tab.setBackground(Ui.rounded(c, active ? R.color.brand_soft : R.color.surface, 8, active ? R.color.brand : R.color.border));
            tab.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
            tab.setOnClickListener(v -> { if (!processing) { source = idx; rerender(); } });
            Ui.addWrap(tabs, tab, i == 0 ? 0 : 8);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tabs);
        Ui.add(root, hs, 20);
        View rule = new View(c);
        rule.setBackgroundColor(Ui.color(c, R.color.border));
        rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));
        Ui.add(root, rule, 8);

        boolean fileTab = source <= 1;
        if (fileTab) renderDropZone(root);
        else renderTextInput(root);

        if ((fileTab && picked != null) || !statusState.equals("idle")) renderStatus(root, fileTab);

        renderConfig(root);
    }

    private void renderDropZone(LinearLayout root) {
        Context c = requireContext();
        LinearLayout zone = Ui.vertical(c);
        zone.setGravity(Gravity.CENTER_HORIZONTAL);
        zone.setBackground(Ui.dashed(c, R.color.surface, 0x660D9488, 16));
        zone.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 28));
        zone.setAlpha(processing ? 0.6f : 1f);
        zone.setOnClickListener(v -> openPicker());

        if (preview != null) {
            ImageView img = new ImageView(c);
            img.setImageBitmap(preview);
            img.setAdjustViewBounds(true);
            img.setMaxHeight(Ui.dp(c, 192));
            img.setBackground(Ui.rounded(c, R.color.surface, 12, R.color.border));
            zone.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 192)));
        } else {
            FrameLayout circle = Ui.iconBox(c, picked != null ? R.drawable.ic_file : R.drawable.ic_upload, R.color.teal_50, R.color.brand, 64, 32, 999);
            zone.addView(circle);
        }
        TextView title = Ui.text(c, picked != null ? pickedName : "Chạm để chọn tệp tin", 16, R.color.text, true);
        title.setGravity(Gravity.CENTER);
        Ui.add(zone, title, 16);
        TextView sub = Ui.text(c, picked != null ? formatSize(pickedSize) + " • Bấm để chọn tệp khác"
                : source == 1 ? "JPG, PNG, WEBP — ảnh lớn được tự động nén"
                : "PDF, DOCX, PPTX, TXT, MD — tối đa 50MB", 12, R.color.text_muted, false);
        sub.setGravity(Gravity.CENTER);
        Ui.add(zone, sub, 4);

        LinearLayout pick = Ui.pillButton(c, 0, picked != null ? "Đổi tệp khác" : "Chọn tệp tin từ điện thoại", R.color.brand, android.R.color.white, 0, v -> openPicker());
        pick.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        Ui.addInline(zone, pick, 16);
        LinearLayout cam = Ui.pillButton(c, R.drawable.ic_image, "Chụp ảnh từ Camera", android.R.color.white, android.R.color.white, 0, v -> openCamera());
        cam.setBackground(Ui.gradient(c, 12, 0xFF059669, 0xFF0F766E));
        Ui.addInline(zone, cam, 10);
        Ui.add(root, zone, 20);
    }

    private void renderTextInput(LinearLayout root) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22));
        String label = source == 2 ? "Nhập liên kết Video / YouTube hoặc tải video bài giảng:"
                : source == 3 ? "Nhập URL Bài báo / Trang web nghiên cứu:" : "Dán nội dung văn bản thô:";
        in.addView(Ui.text(c, label, 12, R.color.text, true));
        EditText field = Ui.field(c, source == 2 ? "https://www.youtube.com/watch?v=..." : source == 3 ? "https://wikipedia.org/wiki/Mitochondrion"
                : "Dán bài giảng hoặc ghi chú của bạn vào đây...", source == 4);
        field.setText(source == 2 ? videoUrl : source == 3 ? webUrl : rawText);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                if (source == 2) videoUrl = s.toString(); else if (source == 3) webUrl = s.toString(); else rawText = s.toString();
            }
        });
        Ui.add(in, field, 10);
        if (source == 2) Ui.add(in, Ui.text(c, "Hệ thống sẽ tự động tách âm thanh và chuyển thành Speech-to-Text chuẩn xác.", 11, R.color.slate_400, false), 8);
        Ui.add(root, card, 20);
    }

    private void renderStatus(LinearLayout root, boolean fileTab) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        card.setRadius(Ui.dp(c, 12));
        LinearLayout in = Ui.inner(card);
        LinearLayout row = Ui.horizontal(c);
        String ext = fileTab && pickedName != null && pickedName.contains(".") ? pickedName.substring(pickedName.lastIndexOf('.') + 1).toUpperCase() : null;
        if (ext != null) {
            TextView badge = Ui.text(c, ext.length() > 4 ? ext.substring(0, 4) : ext, 10, R.color.brand, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.rounded(c, R.color.teal_50, 8, 0));
            row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        } else {
            row.addView(Ui.iconBox(c, R.drawable.ic_file, R.color.teal_50, R.color.brand, 36, 16, 8));
        }
        LinearLayout texts = Ui.vertical(c);
        texts.addView(Ui.text(c, fileTab && pickedName != null ? pickedName : "Tài liệu đang xử lý", 13, R.color.text, true));
        int color = statusState.equals("error") ? R.color.rose : statusState.equals("done") ? R.color.emerald : R.color.slate_400;
        String prefix = statusState.equals("done") ? "✓ " : statusState.equals("error") ? "⚠ " : "";
        texts.addView(Ui.text(c, prefix + (fileTab && picked != null ? formatSize(pickedSize) + " • " : "")
                + (statusMessage.isEmpty() ? "Sẵn sàng chuyển hóa" : statusMessage), 11, color, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 12));
        row.addView(texts, tl);
        if (!statusState.equals("idle")) Ui.addWrap(row, Ui.text(c, progress + "%", 12, R.color.brand, true), 8);
        if (fileTab && picked != null && !processing) {
            ImageView x = Ui.icon(c, R.drawable.ic_close, 18, Ui.color(c, R.color.slate_400));
            x.setOnClickListener(v -> { picked = null; preview = null; statusState = "idle"; statusMessage = ""; progress = 0; rerender(); });
            Ui.addWrap(row, x, 8);
        }
        in.addView(row);
        if (!statusState.equals("idle")) {
            Ui.add(in, Ui.track(c, statusState.equals("error") ? 100 : progress, statusState.equals("error") ? R.color.rose : R.color.brand, 8), 10);
        }
        Ui.add(root, card, 16);
    }

    private void renderConfig(LinearLayout root) {
        Context c = requireContext();
        LinearLayout body = Ui.section(root, "Cấu hình đầu ra AI", null, null, 20);

        body.addView(Ui.label(c, "Ngôn ngữ kết quả"));
        Ui.add(body, Ui.select(c, LANGUAGES, language, i -> language = i), 8);

        Ui.add(body, Ui.label(c, "Sản phẩm muốn tạo"), 20);
        for (int i = 0; i < OUTPUTS.length; i++) {
            int idx = i;
            Ui.add(body, Ui.checkbox(c, OUTPUTS[i], outputs[i], (b, checked) -> outputs[idx] = checked), i == 0 ? 6 : 0);
        }

        Ui.add(body, Ui.label(c, "Độ sâu & Chi tiết"), 20);
        LinearLayout pills = Ui.horizontal(c);
        pills.setBackground(Ui.rounded(c, R.color.slate_50, 8, R.color.border));
        pills.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int i = 0; i < DEPTHS.length; i++) {
            String d = DEPTHS[i];
            boolean locked = d.equals("Chuyên sâu") && !state.isPro();
            boolean active = depth.equals(d);
            TextView t = Ui.text(c, d + (locked ? " 🔒" : ""), 11, active ? R.color.brand : R.color.text_secondary, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            if (active) t.setBackground(Ui.rounded(c, R.color.brand_soft, 6, R.color.brand));
            t.setOnClickListener(v -> {
                if (locked) state.openUpgrade("Chế độ \"Chuyên sâu\" dành cho thành viên StudyMind Pro.");
                else { depth = d; rerender(); }
            });
            Ui.addWeighted(pills, t, i == 0 ? 0 : 6);
        }
        Ui.add(body, pills, 8);

        LinearLayout subjHead = Ui.horizontal(c);
        Ui.addWeighted(subjHead, Ui.iconText(c, R.drawable.ic_folder, "Môn học / Thư mục", 12, Ui.color(c, R.color.text_secondary), true), 0);
        Ui.addWrap(subjHead, Ui.text(c, "Phân loại tài liệu", 10, R.color.brand, false), 8);
        ((ImageView) ((LinearLayout) subjHead.getChildAt(0)).getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.add(body, subjHead, 20);
        Ui.add(body, Ui.select(c, SUBJECTS, subject, i -> { subject = i; rerender(); }), 8);
        if (subject == SUBJECTS.length - 1) {
            EditText custom = Ui.field(c, "Nhập tên môn học (vd: Lập trình Python, Triết học...)", false);
            custom.setText(customSubject);
            custom.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void afterTextChanged(Editable s) { customSubject = s.toString(); }
            });
            Ui.add(body, custom, 8);
        }

        LinearLayout go = Ui.pillButton(c, 0, processing ? "Đang xử lý tài liệu..." : "Bắt đầu chuyển hóa tài liệu", R.color.brand, android.R.color.white, 0, v -> start());
        go.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        go.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 14));
        go.setAlpha(processing ? 0.5f : 1f);
        go.setElevation(Ui.dp(c, 3));
        Ui.add(body, go, 24);
    }

    // ---------- actions ----------

    private void openPicker() {
        if (processing) return;
        picker.launch(source == 1 ? new String[]{"image/*"} : new String[]{"application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/*", "application/json"});
    }

    private void openCamera() {
        if (processing) return;
        try {
            File dir = new File(requireContext().getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException();
            File f = new File(dir, "chup-tai-lieu-" + String.valueOf(System.currentTimeMillis()).substring(7) + ".jpg");
            cameraUri = FileProvider.getUriForFile(requireContext(), "com.studymind.nativeapp.files", f);
            camera.launch(cameraUri);
        } catch (Exception e) {
            toast("Không thể mở Camera trên thiết bị này.");
        }
    }

    private void accept(Uri uri) {
        if (processing) { toast("Đang xử lý tài liệu, vui lòng đợi xong rồi thêm tệp mới."); return; }
        picked = uri;
        pickedName = FileIngest.displayName(requireContext().getContentResolver(), uri);
        pickedSize = sizeOf(uri);
        preview = null;
        String mime = requireContext().getContentResolver().getType(uri);
        if (mime != null && mime.startsWith("image/")) {
            source = 1;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                preview = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) {}
        }
        statusState = "idle";
        statusMessage = "";
        progress = 0;
        rerender();
    }

    private long sizeOf(Uri uri) {
        try (android.database.Cursor cur = requireContext().getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        } catch (Exception ignored) {}
        return 0;
    }

    private void start() {
        if (processing) return;
        if (source == 2 && videoUrl.trim().isEmpty()) { toast("Vui lòng nhập đường dẫn Video / YouTube!"); return; }
        if (source == 3 && webUrl.trim().isEmpty()) { toast("Vui lòng nhập liên kết Web!"); return; }
        if (source == 4 && rawText.trim().isEmpty()) { toast("Vui lòng dán hoặc nhập nội dung văn bản!"); return; }
        if (source <= 1 && picked == null) { toast("Vui lòng chọn tệp trước khi bấm chuyển hóa!"); return; }

        String subj = subject == SUBJECTS.length - 1 ? (customSubject.trim().isEmpty() ? "Chung" : customSubject.trim()) : SUBJECTS[subject];
        String lang = LANGUAGES[language];
        processing = true;
        creepCap = 10;
        setStatus("working", "Đang chuẩn bị...", 5);
        Async.mainDelayed(creep, 700);

        com.studymind.nativeapp.data.AppState.Progress steps = (pct, stage) -> {
            if (!processing) return;
            creepCap = pct >= 80 ? 97 : Math.min(97, pct + 12);
            setStatus("working", stage, Math.max(progress, pct));
        };
        com.studymind.nativeapp.data.AppState.Done done = ok -> {
            processing = false;
            Async.cancel(creep);
            if (ok) setStatus("done", "Đã hoàn tất trích xuất kiến thức", 100);
            else setStatus("error", "Xử lý thất bại, vui lòng thử lại.", 0);
        };

        if (source == 2) state.processVideo(videoUrl.trim(), depth, subj, steps, done);
        else if (source == 3) state.processUrl(webUrl.trim(), depth, subj, steps, done);
        else if (source == 4) {
            state.uploadDocument(null, null, null, rawText, lang, depth, outputs.clone(), subj, steps, done);
        } else {
            Uri uri = picked;
            setStatus("working", source == 1 ? "Đang tối ưu ảnh để tải lên..." : "Đang đọc nội dung tệp...", progress);
            Async.run(() -> FileIngest.prepare(requireContext().getContentResolver(), uri), new Async.Callback<FileIngest.Prepared>() {
                @Override public void onSuccess(FileIngest.Prepared p) {
                    state.uploadDocument(p.binary, p.fileName, p.mime, p.text, lang, depth, outputs.clone(), subj, steps, done);
                }
                @Override public void onError(String m) {
                    processing = false;
                    Async.cancel(creep);
                    setStatus("error", m, 0);
                    toast(m);
                }
            });
        }
    }

    /** Creep toward the current step's cap while the server works; each finished step moves the cap on */
    private void creepProgress() {
        if (!processing) return;
        if (progress < creepCap) progress += Math.max(1, Math.round((creepCap - progress) / 10f));
        if (isAdded()) rerender();
        Async.mainDelayed(creep, 700);
    }

    private void setStatus(String s, String message, int pct) {
        statusState = s;
        statusMessage = message;
        progress = pct;
        if (isAdded()) rerender();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024f)) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }
}
