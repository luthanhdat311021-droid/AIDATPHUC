package com.studymind.nativeapp.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.ui.Ui;

/** "Chuỗi ngày học & Thành tích" — StreakModal.tsx: streak hero + the six badges. */
public final class StreakDialog {
    private StreakDialog() {}

    public static void show(AppCompatActivity activity) {
        Context c = activity;
        Gamification.Data g = Gamification.load();
        Dialog dialog = new Dialog(c);

        LinearLayout box = Ui.vertical(c);
        box.setBackground(Ui.rounded(c, R.color.surface, 24, R.color.slate_100));
        int p = Ui.dp(c, 22);
        box.setPadding(p, p, p, p);

        LinearLayout head = Ui.horizontal(c);
        Ui.addWeighted(head, Ui.text(c, "🔥  Chuỗi ngày học & Thành tích", 16, R.color.text, true), 0);
        TextView close = Ui.text(c, "✕", 18, R.color.slate_400, false);
        close.setOnClickListener(v -> dialog.dismiss());
        Ui.addWrap(head, close, 8);
        box.addView(head);

        // bg-gradient-to-br from-amber-500 via-orange-500 to-rose-500
        LinearLayout hero = Ui.vertical(c);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Ui.gradient(c, 16, 0xFFF59E0B, 0xFFF97316, 0xFFF43F5E));
        hero.setPadding(p, p, p, p);
        TextView flame = Ui.text(c, "🔥", 30, R.color.text, false);
        flame.setGravity(Gravity.CENTER);
        flame.setBackground(Ui.rounded(c, android.R.color.transparent, 999, 0));
        flame.getBackground().setTint(0x33FFFFFF);
        hero.addView(flame, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        TextView big = Ui.text(c, g.streak + " Ngày Liên Tiếp!", 28, R.color.text, true);
        big.setTextColor(Color.WHITE);
        big.setGravity(Gravity.CENTER);
        Ui.add(hero, big, 8);
        TextView sub = Ui.text(c, g.streak >= 7
                ? "Tuyệt vời! Bạn đang duy trì kỷ luật học tập phi thường."
                : "Hãy duy trì thói quen học mỗi ngày để mở khóa thêm các huy hiệu vinh danh!", 12, R.color.text, false);
        sub.setTextColor(0xFFFFEDD5);
        sub.setGravity(Gravity.CENTER);
        Ui.add(hero, sub, 6);
        Ui.add(box, hero, 18);

        Ui.add(box, Ui.iconText(c, R.drawable.ic_award, "HUY HIỆU HỌC TẬP (" + g.unlockedBadgeIds.size() + "/" + Gamification.ALL_BADGES.length + ")",
                12, Ui.color(c, R.color.text_secondary), true), 20);

        LinearLayout grid = Ui.vertical(c);
        LinearLayout row = null;
        for (int i = 0; i < Gamification.ALL_BADGES.length; i++) {
            Gamification.Badge b = Gamification.ALL_BADGES[i];
            boolean unlocked = g.unlockedBadgeIds.contains(b.id);
            if (i % 2 == 0) {
                row = Ui.horizontal(c);
                row.setGravity(Gravity.TOP);
                Ui.add(grid, row, i == 0 ? 0 : 10);
            }
            LinearLayout cell = Ui.horizontal(c);
            cell.setGravity(Gravity.TOP);
            cell.setBackground(Ui.rounded(c, unlocked ? R.color.amber_50 : R.color.slate_50, 16, unlocked ? R.color.amber_soft : R.color.border));
            cell.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
            cell.setAlpha(unlocked ? 1f : 0.6f);
            TextView icon = Ui.text(c, b.icon, 20, R.color.text, false);
            icon.setBackground(Ui.rounded(c, unlocked ? R.color.amber_soft : R.color.slate_200, 12, 0));
            icon.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
            cell.addView(icon);
            LinearLayout texts = Ui.vertical(c);
            texts.addView(Ui.text(c, b.name + (unlocked ? "  ✓" : "  🔒"), 12, unlocked ? R.color.text : R.color.slate_400, true));
            texts.addView(Ui.text(c, b.description, 10, R.color.text_muted, false));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 8));
            cell.addView(texts, tl);
            Ui.addWeighted(row, cell, i % 2 == 0 ? 0 : 10);
        }
        Ui.add(box, grid, 10);

        TextView footer = Ui.text(c, "💡 Mỗi bài học, flashcard và câu trắc nghiệm bạn hoàn thành đều tích lũy vào thành tích cá nhân!", 11, R.color.text_muted, false);
        footer.setGravity(Gravity.CENTER);
        footer.setBackground(Ui.rounded(c, R.color.slate_50, 16, R.color.slate_100));
        footer.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        Ui.add(box, footer, 18);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.92), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
}
