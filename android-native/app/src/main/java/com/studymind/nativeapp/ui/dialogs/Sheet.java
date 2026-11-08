package com.studymind.nativeapp.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.ui.Ui;

/**
 * Stitch modal bottom sheet: 28dp top corners, drag handle, icon + title, subtitle and a round ✕ button.
 */
public final class Sheet {
    private Sheet() {}

    public interface Content { void build(LinearLayout body, BottomSheetDialog sheet); }

    public static BottomSheetDialog show(Context c, int icon, String title, String subtitle, Content content) {
        BottomSheetDialog sheet = new BottomSheetDialog(c);
        LinearLayout box = Ui.vertical(c);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.color(c, R.color.surface));
        float r = Ui.dp(c, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        box.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 24));

        View handle = new View(c);
        handle.setBackground(Ui.rounded(c, R.color.m3_outline_variant, 999, 0));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(handle, hl);

        LinearLayout head = Ui.horizontal(c);
        head.setGravity(Gravity.TOP);
        LinearLayout titles = Ui.vertical(c);
        LinearLayout t = Ui.iconText(c, icon, title, 22, Ui.color(c, R.color.text), true);
        if (icon != 0) {
            android.widget.ImageView iv = (android.widget.ImageView) t.getChildAt(0);
            iv.setColorFilter(Ui.color(c, R.color.brand));
        }
        titles.addView(t);
        if (subtitle != null) {
            TextView s = Ui.text(c, subtitle, 13, R.color.text_muted, false);
            s.setMaxLines(1);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Ui.add(titles, s, 2);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(titles, tl);
        FrameLayout close = Ui.iconBox(c, R.drawable.ic_close, R.color.m3_surface_container, R.color.text_secondary, 44, 22, 999);
        close.setOnClickListener(v -> sheet.dismiss());
        head.addView(close);
        Ui.add(box, head, 14);

        LinearLayout body = Ui.vertical(c);
        Ui.add(box, body, 16);
        content.build(body, sheet);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        sheet.setContentView(scroll);
        View parent = (View) scroll.getParent();
        if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
        return sheet;
    }
}
