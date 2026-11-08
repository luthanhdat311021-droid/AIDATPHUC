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
        LinearLayout chips = Ui.horizontal(c);
        LinearLayout conv = Ui.iconText(c, R.drawable.ic_refresh, "Bộ chuyển đổi thông minh", 12, Ui.color(c, R.color.m3_on_secondary_container), false);
        conv.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
        conv.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        chips.addView(conv);
        chips.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout speed = Ui.iconText(c, R.drawable.ic_zap, "Tốc độ x4", 12, Ui.color(c, R.color.m3_tertiary), false);
        speed.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 999, 0));
        speed.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        chips.addView(speed);
        root.addView(chips);
        Ui.add(root, Ui.text(c, "Nhập tài liệu học tập", 24, R.color.text, true), 12);
        Ui.add(root, Ui.text(c, "Biến tài liệu thô thành bài học tương tác, sơ đồ tư duy & flashcard trong vài giây", 14, R.color.text_secondary, false), 4);

        renderStepper(root);

        sectionTitle(root, "1. Nguồn dữ liệu", "Hỗ trợ OCR tiếng Việt", R.color.brand, 0, 24);
        renderSources(root);
        boolean fileTab = source <= 1;
        if (fileTab) renderDropZone(root);
        else renderTextInput(root);

        renderConfig(root);
        if (!statusState.equals("idle")) renderStatus(root);

        LinearLayout tip = Ui.horizontal(c);
        tip.setGravity(Gravity.TOP);
        tip.setBackground(Ui.rounded(c, R.color.m3_tertiary_fixed, 16, 0));
        tip.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        tip.addView(Ui.icon(c, R.drawable.ic_lightbulb, 20, Ui.color(c, R.color.m3_tertiary)));
        android.text.SpannableStringBuilder tt = new android.text.SpannableStringBuilder("Mẹo học nhanh: ");
        tt.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, tt.length(), 0);
        tt.append("Chọn chế độ Chuyên sâu để hệ thống tạo Mindmap chi tiết tới 6 tầng phân nhánh logic.");
        TextView tv = Ui.text(c, tt, 13, R.color.m3_on_tertiary_fixed, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, 10));
        tip.addView(tv, tl);
        Ui.add(root, tip, 20);

        LinearLayout go = Ui.iconText(c, R.drawable.ic_sparkles, processing ? "Đang xử lý tài liệu..." : "Bắt đầu chuyển hóa tài liệu", 16, 0xFFFFFFFF, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.gradient(c, 16, 0xFF0F766E, 0xFF005C55));
        go.setMinimumHeight(Ui.dp(c, 56));
        go.setElevation(Ui.dp(c, 4));
        go.setAlpha(processing ? 0.6f : 1f);
        go.setOnClickListener(v -> start());
        Ui.add(root, go, 20);
        LinearLayout note = Ui.iconText(c, R.drawable.ic_lock, "Dữ liệu tài liệu được mã hóa riêng tư 256-bit", 12, Ui.color(c, R.color.text_muted), false);
        note.setGravity(Gravity.CENTER);
        Ui.add(root, note, 10);
    }

    /** 1. Chọn nguồn → 2. Cấu hình AI → 3. Chuyển hóa */
    private void renderStepper(LinearLayout root) {
        Context c = requireContext();
        boolean hasInput = source <= 1 ? picked != null : source == 2 ? !videoUrl.trim().isEmpty() : source == 3 ? !webUrl.trim().isEmpty() : !rawText.trim().isEmpty();
        int step = !statusState.equals("idle") ? 3 : hasInput ? 2 : 1;
        LinearLayout box = Ui.vertical(c);
        box.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
        box.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
        LinearLayout row = Ui.horizontal(c);
        String[] labels = {"1. Chọn nguồn", "2. Cấu hình AI", "3. Chuyển hóa"};
        int[] icons = {R.drawable.ic_check_circle, R.drawable.ic_edit, R.drawable.ic_sparkles};
        for (int i = 0; i < 3; i++) {
            int n = i + 1;
            boolean done = n < step || statusState.equals("done");
            boolean active = n == step && !done;
            LinearLayout col = Ui.vertical(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            View dot;
            if (done) dot = Ui.iconBox(c, R.drawable.ic_check_circle, R.color.brand_dark, android.R.color.white, 32, 18, 999);
            else if (active) {
                FrameLayout f = Ui.iconBox(c, icons[i], R.color.m3_primary_fixed, R.color.brand_dark, 32, 18, 999);
                f.setBackground(stroke(c, R.color.m3_primary_fixed, R.color.brand));
                dot = f;
            } else {
                TextView t = Ui.text(c, String.valueOf(n), 13, R.color.text_muted, false);
                t.setGravity(Gravity.CENTER);
                t.setBackground(Ui.rounded(c, R.color.m3_surface_container, 999, 0));
                t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
                dot = t;
            }
            col.addView(dot);
            TextView l = Ui.text(c, labels[i], 12, done || active ? R.color.brand : R.color.text_muted, false);
            l.setGravity(Gravity.CENTER);
            Ui.add(col, l, 6);
            Ui.addWeighted(row, col, 0);
        }
        box.addView(row);
        View bar = Ui.track(c, step == 1 ? 33 : step == 2 ? 66 : statusState.equals("done") ? 100 : 85, R.color.brand_dark, 6);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 6));
        bl.setMargins(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), 0);
        box.addView(bar, bl);
        Ui.add(root, box, 16);
    }

    private android.graphics.drawable.GradientDrawable stroke(Context c, int fill, int strokeColor) {
        android.graphics.drawable.GradientDrawable d = Ui.rounded(c, fill, 999, 0);
        d.setStroke(Ui.dp(c, 2), Ui.color(c, strokeColor));
        return d;
    }

    private void sectionTitle(LinearLayout root, String title, String tag, int tagColor, int tagBg, int top) {
        Context c = requireContext();
        LinearLayout h = Ui.horizontal(c);
        Ui.addWeighted(h, Ui.text(c, title, 18, R.color.text, true), 0);
        TextView t = tagBg != 0 ? Ui.pill(c, tag, tagBg, tagColor) : Ui.text(c, tag, 12, tagColor, false);
        Ui.addWrap(h, t, 8);
        Ui.add(root, h, top);
    }

    private void renderSources(LinearLayout root) {
        Context c = requireContext();
        String[][] tiles = {{"Tệp tài liệu", "PDF, DOCX, PPTX, TXT"}, {"Ảnh & Sách chụp", "Chụp camera, JPG, PNG"},
                {"Video & YouTube", "Tách thoại Whisper AI"}, {"Liên kết bài báo", "Trích xuất nội dung web"}};
        int[] icons = {R.drawable.ic_file, R.drawable.ic_image, R.drawable.ic_video, R.drawable.ic_link};
        int[] bg = {R.color.m3_secondary_container, R.color.m3_tertiary_fixed, R.color.rose_soft, R.color.m3_secondary_container};
        int[] fg = {R.color.brand, R.color.m3_tertiary, R.color.rose, R.color.m3_secondary};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = Ui.horizontal(c);
            row.setBaselineAligned(false);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                boolean active = source == i;
                FrameLayout frame = new FrameLayout(c);
                LinearLayout t = Ui.vertical(c);
                t.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
                t.addView(Ui.iconBox(c, icons[i], bg[i], fg[i], 40, 22, 12));
                Ui.add(t, Ui.text(c, tiles[i][0], 15, R.color.text, true), 12);
                Ui.add(t, Ui.text(c, tiles[i][1], 12, R.color.text_muted, false), 2);
                frame.addView(t);
                frame.setBackground(Ui.rounded(c, R.color.surface, 20, active ? R.color.brand : R.color.border));
                if (active) {
                    View check = Ui.iconBox(c, R.drawable.ic_check_circle, R.color.m3_primary_fixed, R.color.brand, 28, 16, 8);
                    frame.addView(check, new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.TOP | Gravity.END));
                }
                frame.setOnClickListener(v -> { if (!processing) { source = i; rerender(); } });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (k == 1) lp.setMarginStart(Ui.dp(c, 12));
                row.addView(frame, lp);
            }
            Ui.add(root, row, r == 0 ? 12 : 12);
        }
        boolean textActive = source == 4;
        LinearLayout raw = Ui.horizontal(c);
        raw.setBackground(Ui.rounded(c, textActive ? R.color.brand_soft : R.color.m3_surface_low, 16, textActive ? R.color.brand : 0));
        raw.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        raw.addView(Ui.icon(c, R.drawable.ic_text, 20, Ui.color(c, R.color.text_secondary)));
        LinearLayout rt = Ui.vertical(c);
        rt.addView(Ui.text(c, "Nhập nội dung thô / Dán văn bản", 14, R.color.text, true));
        rt.addView(Ui.text(c, "Soạn thảo ghi chú bài giảng trực tiếp vào AI", 12, R.color.text_muted, false));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rl.setMarginStart(Ui.dp(c, 12));
        raw.addView(rt, rl);
        raw.addView(Ui.icon(c, R.drawable.ic_chevron_right, 20, Ui.color(c, R.color.text_muted)));
        raw.setOnClickListener(v -> { if (!processing) { source = 4; rerender(); } });
        Ui.add(root, raw, 12);
    }

    private void renderDropZone(LinearLayout root) {
        Context c = requireContext();
        if (picked == null) {
            LinearLayout zone = Ui.vertical(c);
            zone.setGravity(Gravity.CENTER_HORIZONTAL);
            zone.setBackground(Ui.dashed(c, R.color.surface, 0x660D9488, 20));
            zone.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 22));
            zone.setOnClickListener(v -> openPicker());
            zone.addView(Ui.iconBox(c, R.drawable.ic_upload, R.color.brand_soft, R.color.brand, 52, 26, 999));
            TextView t = Ui.text(c, "Chạm để chọn tệp tin", 15, R.color.text, true);
            t.setGravity(Gravity.CENTER);
            Ui.add(zone, t, 12);
            TextView s = Ui.text(c, source == 1 ? "JPG, PNG, WEBP — ảnh lớn được tự động nén" : "PDF, DOCX, PPTX, TXT, MD — tối đa 50MB", 12, R.color.text_muted, false);
            s.setGravity(Gravity.CENTER);
            Ui.add(zone, s, 4);
            LinearLayout btns = Ui.horizontal(c);
            btns.setGravity(Gravity.CENTER);
            LinearLayout pick = Ui.pillButton(c, R.drawable.ic_folder, "Chọn tệp", R.color.brand, android.R.color.white, 0, v -> openPicker());
            pick.setBackground(Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E));
            btns.addView(pick);
            LinearLayout cam = Ui.pillButton(c, R.drawable.ic_image, "Chụp ảnh", R.color.m3_secondary_container, R.color.text, 0, v -> openCamera());
            cam.setBackground(Ui.rounded(c, R.color.m3_secondary_container, 999, 0));
            Ui.addWrap(btns, cam, 10);
            Ui.add(zone, btns, 14);
            Ui.add(root, zone, 16);
            return;
        }
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.rounded(c, R.color.m3_primary_fixed, 20, 0));
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        LinearLayout row = Ui.horizontal(c);
        if (preview != null) {
            ImageView img = new ImageView(c);
            img.setImageBitmap(preview);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.circle(img);
            img.setClipToOutline(true);
            img.setBackground(Ui.rounded(c, R.color.surface, 14, 0));
            row.addView(img, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        } else {
            row.addView(Ui.iconBox(c, R.drawable.ic_file, R.color.brand_dark, android.R.color.white, 48, 24, 14));
        }
        LinearLayout tx = Ui.vertical(c);
        TextView name = Ui.text(c, pickedName, 15, R.color.text, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tx.addView(name);
        LinearLayout meta = Ui.horizontal(c);
        meta.addView(Ui.text(c, formatSize(pickedSize) + " • ", 12, R.color.text_secondary, false));
        meta.addView(Ui.iconText(c, R.drawable.ic_shield_check, "An toàn", 12, Ui.color(c, R.color.brand), false));
        Ui.add(tx, meta, 2);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        txl.setMarginStart(Ui.dp(c, 12));
        row.addView(tx, txl);
        LinearLayout change = Ui.iconText(c, R.drawable.ic_refresh, "Đổi tệp", 13, Ui.color(c, R.color.brand), true);
        change.setBackground(Ui.rounded(c, R.color.surface, 999, 0));
        change.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        change.setOnClickListener(v -> openPicker());
        row.addView(change);
        card.addView(row);
        View rule = new View(c);
        rule.setBackgroundColor(0x220F766E);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        rl.topMargin = Ui.dp(c, 12);
        card.addView(rule, rl);
        LinearLayout foot = Ui.horizontal(c);
        Ui.addWeighted(foot, Ui.text(c, source == 1 ? "Ảnh tài liệu đã sẵn sàng OCR" : "Định dạng: " + extOf(pickedName), 12, R.color.text_secondary, false), 0);
        int color = statusState.equals("error") ? R.color.rose : R.color.brand;
        String st = statusState.equals("done") ? "Đã chuyển hóa" : statusState.equals("error") ? "Lỗi xử lý" : statusState.equals("working") ? "Đang phân tích" : "Sẵn sàng phân tích";
        Ui.addWrap(foot, Ui.text(c, st, 12, color, true), 8);
        if (!processing) {
            ImageView x = Ui.icon(c, R.drawable.ic_close, 18, Ui.color(c, R.color.text_muted));
            x.setOnClickListener(v -> { picked = null; preview = null; statusState = "idle"; statusMessage = ""; progress = 0; rerender(); });
            Ui.addWrap(foot, x, 10);
        }
        Ui.add(card, foot, 10);
        Ui.add(root, card, 16);
    }

    private static String extOf(String name) {
        return name != null && name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toUpperCase() : "Tệp";
    }

    private void renderTextInput(LinearLayout root) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        String label = source == 2 ? "Nhập liên kết Video / YouTube hoặc tải video bài giảng:"
                : source == 3 ? "Nhập URL Bài báo / Trang web nghiên cứu:" : "Dán nội dung văn bản thô:";
        in.addView(Ui.text(c, label, 13, R.color.text, true));
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
        if (source == 2) Ui.add(in, Ui.text(c, "Hệ thống sẽ tự động tách âm thanh và chuyển thành Speech-to-Text chuẩn xác.", 12, R.color.text_muted, false), 8);
        Ui.add(root, card, 16);
    }

    /** "Tiến trình chuẩn bị AI" card */
    private void renderStatus(LinearLayout root) {
        Context c = requireContext();
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        LinearLayout head = Ui.horizontal(c);
        head.addView(Ui.icon(c, R.drawable.ic_brain, 20, Ui.color(c, R.color.brand)));
        TextView ht = Ui.text(c, "Tiến trình chuẩn bị AI", 16, R.color.text, true);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 10));
        head.addView(ht, hl);
        head.addView(Ui.text(c, statusState.equals("error") ? "Thất bại" : statusState.equals("done") ? "Hoàn tất" : "Tự động hóa", 12, statusState.equals("error") ? R.color.rose : R.color.brand, false));
        in.addView(head);

        String[] steps = {"Đọc tệp và bóc tách ký tự OCR", "Trích xuất cấu trúc & cây kiến thức", "Sinh mindmap & ngân hàng câu hỏi"};
        int[] caps = {30, 80, 100};
        int prev = 0;
        for (int i = 0; i < steps.length; i++) {
            int span = caps[i] - prev;
            int pct = statusState.equals("done") ? 100 : Math.max(0, Math.min(100, (progress - prev) * 100 / span));
            prev = caps[i];
            boolean finished = pct >= 100;
            boolean pending = pct <= 0;
            LinearLayout r = Ui.horizontal(c);
            r.addView(Ui.icon(c, finished ? R.drawable.ic_check_circle : pending ? R.drawable.ic_info : R.drawable.ic_refresh, 18,
                    Ui.color(c, finished ? R.color.brand : pending ? R.color.slate_400 : R.color.text_secondary)));
            TextView st = Ui.text(c, steps[i], 13, pending ? R.color.slate_400 : R.color.text, false);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            sl.setMarginStart(Ui.dp(c, 10));
            r.addView(st, sl);
            r.addView(Ui.text(c, pending ? "Chờ..." : pct + "%", 12, pending ? R.color.slate_400 : R.color.text, !pending));
            Ui.add(in, r, i == 0 ? 14 : 10);
        }
        Ui.add(in, Ui.track(c, statusState.equals("error") ? 100 : progress, statusState.equals("error") ? R.color.rose : R.color.brand_dark, 8), 14);
        if (!statusMessage.isEmpty()) Ui.add(in, Ui.text(c, statusMessage, 12, statusState.equals("error") ? R.color.rose : R.color.text_muted, false), 8);
        Ui.add(root, card, 20);
    }

    private void renderConfig(LinearLayout root) {
        Context c = requireContext();
        sectionTitle(root, "2. Cấu hình AI đầu ra", "Tối ưu kỳ thi", R.color.m3_tertiary, R.color.m3_tertiary_fixed, 28);

        // Subject chips
        Ui.add(root, Ui.text(c, "Môn học / Lĩnh vực", 13, R.color.text_secondary, false), 14);
        LinearLayout chips = Ui.horizontal(c);
        for (int i = 0; i < SUBJECTS.length; i++) {
            int idx = i;
            boolean active = subject == i;
            TextView chip = Ui.text(c, SUBJECTS[i], 13, active ? android.R.color.white : R.color.text_secondary, active);
            chip.setBackground(active ? Ui.gradient(c, 999, 0xFF0F766E, 0xFF0F766E) : Ui.rounded(c, R.color.surface, 999, R.color.border));
            if (active) chip.setTextColor(0xFFFFFFFF);
            chip.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9));
            chip.setOnClickListener(v -> { subject = idx; rerender(); });
            Ui.addWrap(chips, chip, i == 0 ? 0 : 8);
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        Ui.add(root, hs, 8);
        if (subject == SUBJECTS.length - 1) {
            EditText custom = Ui.field(c, "Nhập tên môn học (vd: Lập trình Python, Triết học...)", false);
            custom.setText(customSubject);
            custom.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void afterTextChanged(Editable s) { customSubject = s.toString(); }
            });
            Ui.add(root, custom, 8);
        }

        // Language segmented control
        Ui.add(root, Ui.text(c, "Ngôn ngữ sản phẩm kiến thức", 13, R.color.text_secondary, false), 16);
        String[] langs = {"Tiếng Việt", "English", "Song ngữ"};
        LinearLayout seg = Ui.horizontal(c);
        seg.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
        seg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int i = 0; i < langs.length; i++) {
            int idx = i;
            boolean active = language == i;
            TextView t = Ui.text(c, (active ? "✓ " : "") + langs[i], 14, active ? R.color.brand : R.color.text_secondary, active);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            if (active) t.setBackground(Ui.rounded(c, R.color.surface, 12, 0));
            t.setOnClickListener(v -> { language = idx; rerender(); });
            Ui.addWeighted(seg, t, i == 0 ? 0 : 4);
        }
        Ui.add(root, seg, 8);

        // Outputs
        Ui.add(root, Ui.text(c, "Sản phẩm chuyển hóa (Chọn nhiều mục)", 13, R.color.text_secondary, false), 16);
        String[][] outs = {{"Ghi chú tóm tắt có cấu trúc", "Thuật ngữ cốt lõi, công thức, ví dụ chứng minh"},
                {"Sơ đồ tư duy đa chiều", "Mindmap tương tác thu phóng, xuất ảnh"},
                {"Bộ thẻ ghi nhớ Flashcard Anki", "Kỹ thuật lặp lại ngắt quãng (Spaced Repetition)"},
                {"Đề trắc nghiệm AI & Lời giải chi tiết", "Bộ câu hỏi chẩn đoán mức độ nắm vững bài"}};
        int[] oicons = {R.drawable.ic_book, R.drawable.ic_mindmap, R.drawable.ic_layers, R.drawable.ic_quiz};
        int[] obg = {R.color.m3_secondary_container, R.color.m3_secondary_container, R.color.m3_tertiary_fixed, R.color.m3_secondary_container};
        int[] ofg = {R.color.brand, R.color.m3_secondary, R.color.m3_tertiary, R.color.brand};
        for (int i = 0; i < outs.length; i++) {
            int idx = i;
            LinearLayout row = Ui.horizontal(c);
            row.setBackground(Ui.rounded(c, R.color.surface, 16, R.color.border));
            row.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            android.widget.CheckBox cb = Ui.checkbox(c, "", outputs[i], (b, checked) -> outputs[idx] = checked);
            row.addView(cb);
            row.addView(Ui.iconBox(c, oicons[i], obg[i], ofg[i], 36, 20, 10));
            LinearLayout tx = Ui.vertical(c);
            tx.addView(Ui.text(c, outs[i][0], 14, R.color.text, true));
            TextView d = Ui.text(c, outs[i][1], 12, R.color.text_muted, false);
            d.setMaxLines(1);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(d);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 12));
            row.addView(tx, tl);
            row.setOnClickListener(v -> cb.toggle());
            Ui.add(root, row, 8);
        }

        // Depth
        Ui.add(root, Ui.text(c, "Độ sâu phân tích kiến thức", 13, R.color.text_secondary, false), 16);
        String[] subs = {"Nhanh 30s", "Cân bằng", "Đào sâu 6 tầng"};
        LinearLayout dseg = Ui.horizontal(c);
        dseg.setBaselineAligned(false);
        dseg.setBackground(Ui.rounded(c, R.color.m3_surface_low, 16, 0));
        dseg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int i = 0; i < DEPTHS.length; i++) {
            String d = DEPTHS[i];
            boolean locked = d.equals("Chuyên sâu") && !state.isPro();
            boolean active = depth.equals(d);
            FrameLayout f = new FrameLayout(c);
            LinearLayout col = Ui.vertical(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
            TextView t1 = Ui.text(c, d.equals("Tóm lược nhanh") ? "Tóm lược" : d, 14, active ? R.color.brand : R.color.text_secondary, active);
            t1.setGravity(Gravity.CENTER);
            col.addView(t1);
            TextView t2 = Ui.text(c, subs[i], 12, active ? R.color.brand : R.color.text_muted, false);
            t2.setGravity(Gravity.CENTER);
            col.addView(t2);
            f.addView(col);
            if (active) {
                android.graphics.drawable.GradientDrawable bg = Ui.rounded(c, R.color.surface, 14, 0);
                bg.setStroke(Ui.dp(c, 2), Ui.color(c, R.color.brand_dark));
                f.setBackground(bg);
            }
            if (d.equals("Chuyên sâu")) {
                TextView pro = Ui.pill(c, "PRO", R.color.m3_tertiary, android.R.color.white);
                pro.setTextColor(0xFFFFFFFF);
                pro.setTextSize(9);
                f.addView(pro, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
            }
            f.setOnClickListener(v -> {
                if (locked) state.openUpgrade("Chế độ \"Chuyên sâu\" dành cho thành viên StudyMind Pro.");
                else { depth = d; rerender(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(Ui.dp(c, 4));
            dseg.addView(f, lp);
        }
        Ui.add(root, dseg, 8);
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
