package com.studymind.nativeapp.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.studymind.nativeapp.MainActivity;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.StudyMindApp;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.AppUser;
import com.studymind.nativeapp.ui.ImageLoader;
import com.studymind.nativeapp.ui.Ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** "Chỉnh sửa hồ sơ cá nhân" — EditProfileModal.tsx: avatar (upload / URL / presets), name, read-only e-mail. */
public final class ProfileDialog {
    private static final String[] PRESETS = {
            "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80"
    };

    private final AppCompatActivity activity;
    private final AppState state = StudyMindApp.state();
    private final Dialog dialog;
    private final LinearLayout box;
    private String name;
    private String avatar;
    private boolean urlMode, uploading, saving;

    public static void show(AppCompatActivity activity) {
        if (StudyMindApp.auth().getUser() != null) new ProfileDialog(activity);
    }

    private ProfileDialog(AppCompatActivity activity) {
        this.activity = activity;
        AppUser u = state.auth().getUser();
        name = u.fullName;
        avatar = u.avatarUrl != null ? u.avatarUrl : PRESETS[0];
        dialog = new Dialog(activity);
        box = Ui.vertical(activity);
        box.setBackground(Ui.rounded(activity, R.color.surface, 24, R.color.border));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        render();
        dialog.show();
    }

    private void render() {
        Context c = activity;
        AppUser u = state.auth().getUser();
        box.removeAllViews();

        LinearLayout head = Ui.horizontal(c);
        head.setPadding(Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20));
        head.addView(Ui.iconBox(c, R.drawable.ic_person, R.color.teal_50, R.color.brand, 40, 20, 16));
        LinearLayout ht = Ui.vertical(c);
        ht.addView(Ui.text(c, "Chỉnh sửa hồ sơ cá nhân", 17, R.color.text, true));
        ht.addView(Ui.text(c, "Tùy chỉnh Họ tên và Ảnh đại diện hiển thị của bạn", 12, R.color.text_muted, false));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginStart(Ui.dp(c, 12));
        head.addView(ht, hl);
        ImageView x = Ui.icon(c, R.drawable.ic_close, 20, Ui.color(c, R.color.slate_400));
        x.setOnClickListener(v -> dialog.dismiss());
        head.addView(x);
        box.addView(head);
        box.addView(rule(c));

        LinearLayout body = Ui.vertical(c);
        body.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22));
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(body);

        FrameLayout av = new FrameLayout(c);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(Ui.rounded(c, R.color.brand_soft, 999, 0));
        int ring = Ui.dp(c, 4);
        img.setPadding(ring, ring, ring, ring);
        Ui.circle(img);
        ImageLoader.load(img, avatar);
        av.addView(img, new FrameLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 96)));
        if (uploading) {
            View dim = new View(c);
            dim.setBackground(Ui.rounded(c, android.R.color.black, 999, 0));
            dim.setAlpha(0.5f);
            av.addView(dim, new FrameLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 96)));
            ProgressBar pb = new ProgressBar(c);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            av.addView(pb, new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER));
        }
        body.addView(av);

        LinearLayout opts = Ui.horizontal(c);
        LinearLayout up = Ui.pillButton(c, R.drawable.ic_upload, "Tải ảnh từ máy", R.color.slate_100, R.color.text_secondary, 0, v -> upload());
        ((ImageView) up.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        up.setBackground(Ui.rounded(c, R.color.slate_100, 12, 0));
        opts.addView(up);
        LinearLayout url = Ui.pillButton(c, R.drawable.ic_link, urlMode ? "Ẩn URL" : "Nhập URL ảnh", R.color.slate_100, R.color.text_secondary, 0, v -> { urlMode = !urlMode; render(); });
        ((ImageView) url.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        url.setBackground(Ui.rounded(c, R.color.slate_100, 12, 0));
        Ui.addWrap(opts, url, 8);
        Ui.addInline(body, opts, 16);

        if (urlMode) {
            EditText f = Ui.field(c, "Dán đường dẫn URL ảnh đại diện của bạn...", false);
            f.setText(avatar);
            f.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void afterTextChanged(android.text.Editable s) { avatar = s.toString().trim(); }
            });
            Ui.add(body, f, 12);
        }

        TextView presetLabel = Ui.text(c, "HOẶC CHỌN MẪU ẢNH CÓ SẴN:", 11, R.color.text_muted, true);
        Ui.addInline(body, presetLabel, 16);
        LinearLayout presets = Ui.horizontal(c);
        presets.setGravity(Gravity.CENTER);
        for (String p : PRESETS) {
            boolean sel = p.equals(avatar);
            FrameLayout f = new FrameLayout(c);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.circle(iv);
            iv.setAlpha(sel ? 1f : 0.7f);
            ImageLoader.load(iv, p);
            f.addView(iv, new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            if (sel) {
                TextView check = Ui.text(c, "✓", 14, android.R.color.white, true);
                check.setGravity(Gravity.CENTER);
                check.setBackground(Ui.rounded(c, R.color.brand, 999, 0));
                check.setAlpha(0.75f);
                f.addView(check, new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            }
            f.setOnClickListener(v -> { avatar = p; render(); });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
            pl.setMarginStart(presets.getChildCount() > 0 ? Ui.dp(c, 12) : 0);
            presets.addView(f, pl);
        }
        Ui.addInline(body, presets, 8);

        LinearLayout fields = Ui.vertical(c);
        fields.addView(Ui.label(c, "Họ và Tên"));
        EditText nameField = Ui.field(c, "Nhập họ và tên của bạn...", false);
        nameField.setText(name);
        nameField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { name = s.toString(); }
        });
        Ui.add(fields, nameField, 6);
        Ui.add(fields, Ui.label(c, "Địa chỉ Email"), 16);
        EditText email = Ui.field(c, "", false);
        email.setText(u != null && u.email != null ? u.email : "");
        email.setEnabled(false);
        email.setTextColor(Ui.color(c, R.color.text_muted));
        email.setBackground(Ui.rounded(c, R.color.slate_100, 12, R.color.border));
        Ui.add(fields, email, 6);
        Ui.add(fields, Ui.text(c, "Gói hiện tại: " + state.tierLabel(), 12, R.color.brand, true), 14);
        Ui.add(body, fields, 22);

        box.addView(rule(c));
        LinearLayout btns = Ui.horizontal(c);
        btns.setPadding(Ui.dp(c, 22), Ui.dp(c, 14), Ui.dp(c, 22), Ui.dp(c, 20));
        LinearLayout cancel = Ui.pillButton(c, 0, "Hủy", R.color.slate_100, R.color.text_secondary, 0, v -> dialog.dismiss());
        cancel.setBackground(Ui.rounded(c, R.color.slate_100, 12, 0));
        cancel.setPadding(0, Ui.dp(c, 13), 0, Ui.dp(c, 13));
        Ui.addWeighted(btns, cancel, 0);
        LinearLayout save = Ui.pillButton(c, 0, saving ? "Đang lưu..." : "Lưu thay đổi", R.color.brand, android.R.color.white, 0, v -> save());
        save.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
        save.setPadding(0, Ui.dp(c, 13), 0, Ui.dp(c, 13));
        Ui.addWeighted(btns, save, 12);
        box.addView(btns);
    }

    private View rule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.slate_100));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));
        return v;
    }

    private void upload() {
        if (!(activity instanceof MainActivity) || uploading) return;
        ((MainActivity) activity).pickImage(uri -> {
            uploading = true;
            render();
            Async.run(() -> {
                // Downscale to a 512px JPEG: avatars never need more and it keeps the upload small
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    Bitmap src = BitmapFactory.decodeStream(in);
                    if (src == null) throw new java.io.IOException("Không đọc được ảnh này.");
                    float s = Math.min(1f, 512f / Math.max(src.getWidth(), src.getHeight()));
                    Bitmap scaled = Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * s), Math.round(src.getHeight() * s), true);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
                    return out.toByteArray();
                }
            }, new Async.Callback<byte[]>() {
                @Override public void onSuccess(byte[] jpeg) {
                    state.uploadAvatar(jpeg, new Async.Callback<String>() {
                        @Override public void onSuccess(String url) { avatar = url; uploading = false; render(); }
                        @Override public void onError(String m) { uploading = false; render(); }
                    });
                }
                @Override public void onError(String m) { uploading = false; render(); ((AppState.Host) activity).toast(m); }
            });
        });
    }

    private void save() {
        if (saving) return;
        if (name.trim().isEmpty()) { ((AppState.Host) activity).toast("Vui lòng nhập họ và tên."); return; }
        saving = true;
        render();
        state.updateProfile(name, avatar, ok -> {
            saving = false;
            if (ok) dialog.dismiss();
            else render();
        });
    }
}
