package com.studymind.nativeapp.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.studymind.nativeapp.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Small view factory so every screen is built from the same pieces as the web UI
 * (white rounded cards, teal primary buttons, pill chips) without one XML file per widget.
 */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, @ColorRes int res) { return ContextCompat.getColor(c, res); }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, CharSequence s, float sp, @ColorRes int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView title(Context c, CharSequence s) { return text(c, s, 20, R.color.text, true); }

    public static TextView heading(Context c, CharSequence s) {
        TextView t = text(c, s, 15, R.color.text, true);
        t.setPadding(0, 0, 0, dp(c, 6));
        return t;
    }

    public static TextView body(Context c, CharSequence s) { return text(c, s, 14, R.color.text, false); }

    public static TextView muted(Context c, CharSequence s) { return text(c, s, 12, R.color.text_muted, false); }

    /** White rounded card with a 1dp border; children go into the returned card's inner column */
    public static MaterialCardView card(Context c) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(color(c, R.color.border));
        card.setCardBackgroundColor(color(c, R.color.surface));
        LinearLayout inner = vertical(c);
        int p = dp(c, 16);
        inner.setPadding(p, p, p, p);
        card.addView(inner);
        return card;
    }

    public static LinearLayout inner(MaterialCardView card) { return (LinearLayout) card.getChildAt(0); }

    /** Card tinted with one of the soft palette colours (amber note, rose warning, teal banner …) */
    public static MaterialCardView tintedCard(Context c, @ColorRes int bg, @ColorRes int stroke) {
        MaterialCardView card = card(c);
        card.setCardBackgroundColor(color(c, bg));
        card.setStrokeColor(color(c, stroke));
        return card;
    }

    public static MaterialButton primary(Context c, CharSequence label, View.OnClickListener onClick) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 12));
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.brand)));
        b.setTextColor(color(c, android.R.color.white));
        b.setOnClickListener(onClick);
        return b;
    }

    public static MaterialButton outline(Context c, CharSequence label, View.OnClickListener onClick) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 12));
        b.setTextColor(color(c, R.color.brand));
        b.setStrokeColor(ColorStateList.valueOf(color(c, R.color.brand)));
        b.setOnClickListener(onClick);
        return b;
    }

    public static MaterialButton amber(Context c, CharSequence label, View.OnClickListener onClick) {
        MaterialButton b = primary(c, label, onClick);
        b.setBackgroundTintList(ColorStateList.valueOf(color(c, R.color.amber)));
        b.setTextColor(0xFF111827);
        return b;
    }

    /** Rounded pill label, e.g. the "PRO" badge or a status tag */
    public static TextView pill(Context c, CharSequence s, @ColorRes int bg, @ColorRes int fg) {
        TextView t = text(c, s, 11, fg, true);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 999));
        d.setColor(color(c, bg));
        t.setBackground(d);
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return t;
    }

    /** Selectable pill (segmented control item) */
    public static TextView choice(Context c, CharSequence s, boolean selected, View.OnClickListener onClick) {
        TextView t = text(c, s, 12, selected ? R.color.brand : R.color.text_muted, true);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 10));
        d.setColor(color(c, selected ? R.color.brand_soft : R.color.surface_alt));
        if (selected) d.setStroke(dp(c, 1), color(c, R.color.brand));
        t.setBackground(d);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        t.setOnClickListener(onClick);
        return t;
    }

    public static TextInputLayout input(Context c, String hint, boolean multiline) {
        TextInputLayout til = new TextInputLayout(c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setBoxCornerRadii(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        TextInputEditText et = new TextInputEditText(til.getContext());
        if (multiline) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(4);
            et.setGravity(Gravity.TOP);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setSingleLine(true);
        }
        til.addView(et);
        return til;
    }

    public static String value(TextInputLayout til) {
        return til.getEditText() != null && til.getEditText().getText() != null ? til.getEditText().getText().toString().trim() : "";
    }

    public static ProgressBar spinner(Context c) {
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(color(c, R.color.brand)));
        return p;
    }

    public static ProgressBar bar(Context c, int percent, @ColorRes int tint) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgress(Math.max(0, Math.min(100, percent)));
        p.setProgressTintList(ColorStateList.valueOf(color(c, tint)));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(color(c, R.color.surface_alt)));
        return p;
    }

    /** Add a child with match_parent width and a top margin */
    public static <T extends View> T add(ViewGroup parent, T child, int topMarginDp) {
        // Keep a fixed height the child already asked for (progress tracks, icon tiles)
        ViewGroup.LayoutParams own = child.getLayoutParams();
        int height = own != null && own.height > 0 ? own.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = dp(parent.getContext(), topMarginDp);
        parent.addView(child, lp);
        return child;
    }

    /** Add a child at its natural width (pills, small buttons) on its own line */
    public static <T extends View> T addInline(ViewGroup parent, T child, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), topMarginDp);
        parent.addView(child, lp);
        return child;
    }

    /** Web form label: text-xs font-semibold text-slate-700 */
    public static TextView label(Context c, CharSequence s) { return text(c, s, 12, R.color.text_secondary, true); }

    /** Plain web input / textarea (bg-slate-50 border rounded-lg) */
    public static android.widget.EditText field(Context c, String hint, boolean multiline) {
        android.widget.EditText e = new android.widget.EditText(c);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(color(c, R.color.text));
        e.setHintTextColor(color(c, R.color.slate_400));
        e.setBackgroundResource(R.drawable.bg_field);
        int h = dp(c, 14), v = dp(c, 11);
        e.setPadding(h, v, h, v);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(6);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    public interface OnPick { void pick(int index); }

    /** Web <select>: same box as inputs, opens the system list */
    public static android.widget.Spinner select(Context c, String[] items, int selected, OnPick onPick) {
        android.widget.Spinner sp = new android.widget.Spinner(c, android.widget.Spinner.MODE_DROPDOWN);
        android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convert, parent);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setTextColor(color(c, R.color.text));
                return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setBackgroundResource(R.drawable.bg_field);
        sp.setPadding(dp(c, 6), dp(c, 4), dp(c, 6), dp(c, 4));
        sp.setSelection(selected, false);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { if (pos != selected) onPick.pick(pos); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        return sp;
    }

    public static android.widget.CheckBox checkbox(Context c, String label, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        android.widget.CheckBox cb = new android.widget.CheckBox(c);
        cb.setText(label);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cb.setTextColor(color(c, R.color.text));
        cb.setButtonTintList(ColorStateList.valueOf(color(c, R.color.brand)));
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener(l);
        return cb;
    }

    /** Dashed drop-zone border (border-2 border-dashed border-teal-600/40 rounded-2xl) */
    public static GradientDrawable dashed(Context c, @ColorRes int fill, int strokeArgb, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(c, fill));
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 2), strokeArgb, dp(c, 8), dp(c, 6));
        return d;
    }

    /** Web "border-b-2" tab: icon + label with a 2dp underline drawn as part of the background */
    public static LinearLayout underlineTab(Context c, int icon, CharSequence label, boolean active, int activeArgb, View.OnClickListener onClick) {
        LinearLayout t = iconText(c, icon, label, 13, active ? activeArgb : color(c, R.color.text_muted), true);
        t.setPadding(0, 0, 0, dp(c, 12));
        if (active) {
            GradientDrawable line = new GradientDrawable();
            line.setColor(activeArgb);
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{line});
            ld.setLayerHeight(0, dp(c, 2));
            ld.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
            t.setBackground(ld);
        }
        if (onClick != null) t.setOnClickListener(onClick);
        return t;
    }

    /** Horizontally scrollable row of underline tabs sitting on a 1dp border-b line */
    public static View tabBar(Context c, View... tabs) {
        LinearLayout row = horizontal(c);
        row.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < tabs.length; i++) addWrap(row, tabs[i], i == 0 ? 0 : 24);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        LinearLayout wrap = vertical(c);
        wrap.addView(hs);
        View rule = new View(c);
        rule.setBackgroundColor(color(c, R.color.border));
        wrap.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return wrap;
    }

    /** Clip an image to a circle (avatars) */
    public static void circle(View v) {
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        v.setClipToOutline(true);
    }

    /** Add a child that shares a row equally with its siblings */
    public static <T extends View> T addWeighted(LinearLayout row, T child, int startMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(row.getContext(), startMarginDp));
        row.addView(child, lp);
        return child;
    }

    public static <T extends View> T addWrap(LinearLayout row, T child, int startMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(row.getContext(), startMarginDp));
        row.addView(child, lp);
        return child;
    }

    /** Centered icon-less empty state with an optional call to action */
    public static View empty(Context c, String title, String message, String action, View.OnClickListener onAction) {
        LinearLayout l = vertical(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(c, 24), dp(c, 48), dp(c, 24), dp(c, 24));
        TextView t = title(c, title);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        TextView m = muted(c, message);
        m.setGravity(Gravity.CENTER);
        add(l, m, 8);
        if (action != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 16);
            l.addView(primary(c, action, onAction), lp);
        }
        return l;
    }

    // ---------- web-style building blocks ----------

    /** Rounded rectangle background (Tailwind bg-* + rounded-* + border-*) */
    public static GradientDrawable rounded(Context c, @ColorRes int fill, float radiusDp, @ColorRes int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(c, fill));
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1), color(c, stroke));
        return d;
    }

    /** bg-gradient-to-r from-… to-… */
    public static GradientDrawable gradient(Context c, float radiusDp, int... argb) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, argb);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static android.widget.ImageView icon(Context c, int drawable, float sizeDp, int argb) {
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageResource(drawable);
        iv.setImageTintList(ColorStateList.valueOf(argb));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** Square tinted tile holding an icon, e.g. the stat-card icons (w-10 h-10 rounded-xl bg-teal-50) */
    public static android.widget.FrameLayout iconBox(Context c, int drawable, @ColorRes int bg, @ColorRes int fg, float boxDp, float iconDp, float radiusDp) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(c);
        box.setBackground(rounded(c, bg, radiusDp, 0));
        android.widget.ImageView iv = icon(c, drawable, iconDp, color(c, fg));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp), Gravity.CENTER);
        box.addView(iv, lp);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, boxDp), dp(c, boxDp)));
        return box;
    }

    /** Text with a leading icon, e.g. the small "pill" buttons and tags */
    public static LinearLayout iconText(Context c, int drawable, CharSequence s, float sp, int argb, boolean bold) {
        LinearLayout row = horizontal(c);
        if (drawable != 0) row.addView(icon(c, drawable, sp + 3, argb));
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(argb);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (drawable != 0) lp.setMarginStart(dp(c, 6));
        row.addView(t, lp);
        return row;
    }

    /** Web "button" built from a rounded background + icon + label */
    public static LinearLayout pillButton(Context c, int drawable, CharSequence label, @ColorRes int bg, @ColorRes int fg, @ColorRes int stroke, View.OnClickListener onClick) {
        LinearLayout b = iconText(c, drawable, label, 13, color(c, fg), true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(c, bg, 10, stroke));
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    /**
     * White section card with a bold title, optional right-side action and the web's
     * "border-b border-slate-100 pb-3" header divider. Children go into the returned body.
     */
    public static LinearLayout section(ViewGroup parent, String title, String action, View.OnClickListener onAction, int topMarginDp) {
        Context c = parent.getContext();
        MaterialCardView card = card(c);
        card.setRadius(dp(c, 12));
        LinearLayout in = inner(card);
        in.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 20));
        LinearLayout head = horizontal(c);
        TextView t = text(c, title, 16, R.color.text, true);
        addWeighted(head, t, 0);
        if (action != null) {
            TextView a = text(c, action, 12, R.color.brand, true);
            a.setOnClickListener(onAction);
            addWrap(head, a, 8);
        }
        in.addView(head);
        View divider = new View(c);
        divider.setBackgroundColor(color(c, R.color.slate_100));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        dl.topMargin = dp(c, 12);
        in.addView(divider, dl);
        LinearLayout body = vertical(c);
        add(in, body, 12);
        add(parent, card, topMarginDp);
        return body;
    }

    /** h-2.5 rounded-full progress track */
    public static View track(Context c, int percent, @ColorRes int fill, float heightDp) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.setBackground(rounded(c, R.color.slate_100, 999, 0));
        View bar = new View(c);
        bar.setBackground(rounded(c, fill, 999, 0));
        f.addView(bar, new android.widget.FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        f.post(() -> {
            bar.getLayoutParams().width = f.getWidth() * Math.max(0, Math.min(100, percent)) / 100;
            bar.requestLayout();
        });
        return f;
    }

    // ---------- formatting ----------

    public static String vnd(long amount) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount) + "đ";
    }

    private static Date parseIso(String iso) {
        if (iso == null) return null;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(iso.replace("Z", "+00:00"));
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static long epochMillis(String iso) {
        Date d = parseIso(iso);
        return d != null ? d.getTime() : 0;
    }

    public static String date(String iso) {
        Date d = parseIso(iso);
        return d != null ? new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d) : (iso != null ? iso : "");
    }

    public static String dateTime(String iso) {
        Date d = parseIso(iso);
        return d != null ? new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).format(d) : (iso != null ? iso : "");
    }
}
