package com.studymind.nativeapp.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.studymind.nativeapp.R;
import com.studymind.nativeapp.ui.Ui;

/** Web-style centered modal: rounded-2xl white box, icon + title header with ✕, content, optional full-width button. */
public final class Modal {
    private Modal() {}

    public interface Content { void build(LinearLayout body, Dialog dialog); }

    public static Dialog show(Context c, int icon, int iconColorRes, String title, String closeLabel, Content content) {
        Dialog dialog = new Dialog(c);
        LinearLayout box = Ui.vertical(c);
        box.setBackground(Ui.rounded(c, R.color.surface, 20, R.color.border));
        int p = Ui.dp(c, 22);
        box.setPadding(p, p, p, p);

        LinearLayout head = Ui.horizontal(c);
        if (icon != 0) head.addView(Ui.icon(c, icon, 20, Ui.color(c, iconColorRes)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(Ui.dp(c, icon != 0 ? 8 : 0));
        head.addView(Ui.text(c, title, 16, R.color.text, true), tl);
        ImageView x = Ui.icon(c, R.drawable.ic_close, 20, Ui.color(c, R.color.slate_400));
        x.setOnClickListener(v -> dialog.dismiss());
        head.addView(x);
        box.addView(head);
        View rule = new View(c);
        rule.setBackgroundColor(Ui.color(c, R.color.slate_100));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        rl.topMargin = Ui.dp(c, 12);
        box.addView(rule, rl);

        LinearLayout body = Ui.vertical(c);
        Ui.add(box, body, 14);
        content.build(body, dialog);

        if (closeLabel != null) {
            LinearLayout btn = Ui.pillButton(c, 0, closeLabel, R.color.brand, android.R.color.white, 0, v -> dialog.dismiss());
            btn.setBackground(Ui.rounded(c, R.color.brand, 12, 0));
            Ui.add(box, btn, 16);
        }

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.92), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        return dialog;
    }
}
