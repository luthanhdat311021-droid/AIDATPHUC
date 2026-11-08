package com.studymind.nativeapp.ui.dialogs;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.StudyMindApp;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Gamification;
import com.studymind.nativeapp.data.model.Models.AppUser;
import com.studymind.nativeapp.ui.Ui;

import java.util.Calendar;

/** "Chuỗi ngày & Thành tích" — Stitch design/stitch/14_chuoi_ngay. */
public final class StreakDialog {
    private StreakDialog() {}

    private static final String[] DAY_LABELS = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
    private static boolean showLocked;

    public static void show(AppCompatActivity activity) {
        Sheet.show(activity, R.drawable.ic_award, "Chuỗi Ngày & Thành Tích", null, (body, sheet) -> build(activity, body, sheet));
    }

    private static void build(AppCompatActivity activity, LinearLayout body, BottomSheetDialog sheet) {
        Context c = activity;
        body.removeAllViews();
        Gamification.Data g = Gamification.load();
        AppState state = StudyMindApp.state();
        AppUser u = state.auth().getUser();

        // ---- Hero ----
        LinearLayout hero = Ui.vertical(c);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Ui.gradient(c, 24, 0xFFFFDDB8, 0xFFFFF4E8, 0xFFEFF4FF));
        hero.setPadding(Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 18));
        FrameLayout flame = Ui.iconBox(c, R.drawable.ic_flame, R.color.surface, R.color.m3_tertiary, 88, 44, 999);
        flame.setElevation(Ui.dp(c, 4));
        hero.addView(flame);
        TextView rec = Ui.text(c, "KỶ LỤC HIỆN TẠI", 13, R.color.m3_tertiary, true);
        rec.setLetterSpacing(0.08f);
        Ui.addInline(hero, rec, 14);
        TextView big = Ui.text(c, g.streak + " NGÀY LIÊN TIẾP!", 28, R.color.text, true);
        big.setGravity(Gravity.CENTER);
        Ui.add(hero, big, 2);
        int next = g.streak < 3 ? 3 : g.streak < 7 ? 7 : 0;
        String badge = g.streak < 3 ? "Ngọn lửa bùng cháy" : "Kỷ luật thép";
        SpannableStringBuilder sb = new SpannableStringBuilder("Tuyệt vời ");
        int s = sb.length();
        sb.append(u != null && u.fullName != null ? u.fullName : "bạn");
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s, sb.length(), 0);
        if (next > 0) {
            sb.append("! Bạn chỉ còn ");
            s = sb.length();
            sb.append((next - g.streak) + " ngày");
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), s, sb.length(), 0);
            sb.setSpan(new ForegroundColorSpan(Ui.color(c, R.color.m3_tertiary)), s, sb.length(), 0);
            sb.append(" nữa để mở khóa huy hiệu ");
            s = sb.length();
            sb.append("\"" + badge + "\"");
            sb.setSpan(new ForegroundColorSpan(Ui.color(c, R.color.brand_dark)), s, sb.length(), 0);
            sb.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), s, sb.length(), 0);
            sb.append(".");
        } else {
            sb.append("! Bạn đang duy trì kỷ luật học tập phi thường.");
        }
        TextView msg = Ui.text(c, sb, 15, R.color.text_secondary, false);
        msg.setGravity(Gravity.CENTER);
        Ui.add(hero, msg, 8);

        LinearLayout week = Ui.horizontal(c);
        week.setBackground(Ui.rounded(c, R.color.surface, 16, 0));
        week.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12));
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            int ago = 6 - i;
            boolean today = ago == 0, active = ago < g.streak;
            LinearLayout col = Ui.vertical(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView l = Ui.text(c, DAY_LABELS[cal.get(Calendar.DAY_OF_WEEK) - 1], 13, today ? R.color.m3_tertiary : R.color.text_secondary, today);
            l.setGravity(Gravity.CENTER);
            col.addView(l);
            FrameLayout dot = Ui.iconBox(c, today ? R.drawable.ic_flame : R.drawable.ic_check_circle,
                    today ? R.color.m3_tertiary : active ? R.color.teal_200 : R.color.m3_surface_container,
                    today ? android.R.color.white : active ? R.color.brand_dark : R.color.slate_400, 36, 20, 999);
            Ui.addInline(col, dot, 6);
            Ui.addWeighted(week, col, 0);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Ui.add(hero, week, 16);

        boolean frozen = Gamification.isTomorrowFrozen();
        boolean can = Gamification.canFreeze();
        LinearLayout freeze = Ui.iconText(c, R.drawable.ic_shield, frozen ? "Đã đóng băng chuỗi cho ngày mai" : "Đóng băng chuỗi ngày ❄", 15, Ui.color(c, R.color.brand_dark), true);
        TextView cost = Ui.pill(c, frozen ? "Đang bảo vệ" : can ? "1 lần/tuần" : "Đã dùng tuần này", R.color.m3_tertiary_fixed, R.color.m3_on_tertiary_fixed);
        Ui.addWrap(freeze, cost, 8);
        freeze.setGravity(Gravity.CENTER);
        freeze.setBackground(Ui.rounded(c, R.color.m3_surface_high, 18, 0));
        freeze.setMinimumHeight(Ui.dp(c, 52));
        freeze.setAlpha(frozen || !can ? 0.75f : 1f);
        freeze.setOnClickListener(v -> {
            if (frozen) return;
            if (Gamification.freezeTomorrow()) {
                state.syncGamification("freeze");
                if (activity instanceof AppState.Host) ((AppState.Host) activity).toast("❄ Đã đóng băng chuỗi ngày: ngày mai bận cũng không mất chuỗi!");
                build(activity, body, sheet);
            } else if (activity instanceof AppState.Host) {
                ((AppState.Host) activity).toast("Bạn đã dùng lượt đóng băng của tuần này rồi.");
            }
        });
        Ui.add(hero, freeze, 14);
        LinearLayout note = Ui.iconText(c, R.drawable.ic_shield_check, "Bảo vệ thành quả học tập nếu bạn bận rộn ngày mai", 12, Ui.color(c, R.color.text_secondary), false);
        note.setGravity(Gravity.CENTER);
        Ui.add(hero, note, 8);
        body.addView(hero);

        // ---- StudyCoin ----
        LinearLayout coin = Ui.horizontal(c);
        coin.setBackground(Ui.rounded(c, R.color.m3_surface_low, 20, 0));
        coin.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
        coin.addView(Ui.iconBox(c, R.drawable.ic_wallet, R.color.m3_tertiary_fixed, R.color.m3_tertiary, 44, 22, 999));
        LinearLayout ct = Ui.vertical(c);
        ct.addView(Ui.text(c, "Tài sản thưởng StudyMind", 13, R.color.text_secondary, false));
        ct.addView(Ui.text(c, "Ví StudyCoin & nhiệm vụ", 17, R.color.text, true));
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ctl.setMarginStart(Ui.dp(c, 12));
        coin.addView(ct, ctl);
        LinearLayout ex = Ui.iconText(c, 0, "Đổi quà", 14, 0xFFFFFFFF, true);
        ex.addView(Ui.icon(c, R.drawable.ic_chevron_right, 16, 0xFFFFFFFF));
        ex.setBackground(Ui.gradient(c, 999, 0xFF005C55, 0xFF005C55));
        ex.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
        ex.setOnClickListener(v -> { sheet.dismiss(); state.navigate(AppState.Screen.REWARDS); });
        coin.addView(ex);
        Ui.add(body, coin, 16);

        // ---- Badges ----
        int unlockedCount = g.unlockedBadgeIds.size();
        int lockedCount = Gamification.ALL_BADGES.length - unlockedCount;
        LinearLayout bh = Ui.horizontal(c);
        Ui.addWeighted(bh, Ui.text(c, "Bộ Sưu Tập Huy Hiệu", 19, R.color.text, true), 0);
        bh.addView(Ui.text(c, Gamification.ALL_BADGES.length + " Danh hiệu", 12, R.color.text_secondary, false));
        Ui.add(body, bh, 24);
        LinearLayout tabs = Ui.horizontal(c);
        tabs.setBackground(Ui.rounded(c, R.color.m3_surface_container, 16, 0));
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        for (int k = 0; k < 2; k++) {
            boolean locked = k == 1;
            boolean on = showLocked == locked;
            LinearLayout t = Ui.iconText(c, locked ? R.drawable.ic_lock : R.drawable.ic_award, (locked ? "Chưa mở (" + lockedCount + ")" : "Đã mở khóa (" + unlockedCount + ")"),
                    15, on ? 0xFFFFFFFF : Ui.color(c, R.color.text_secondary), on);
            t.setGravity(Gravity.CENTER);
            t.setMinimumHeight(Ui.dp(c, 44));
            if (on) t.setBackground(Ui.gradient(c, 14, 0xFF005C55, 0xFF005C55));
            t.setOnClickListener(v -> { showLocked = locked; build(activity, body, sheet); });
            Ui.addWeighted(tabs, t, k == 0 ? 0 : 4);
        }
        Ui.add(body, tabs, 10);

        LinearLayout row = null;
        int shown = 0;
        for (Gamification.Badge b : Gamification.ALL_BADGES) {
            boolean unlocked = g.unlockedBadgeIds.contains(b.id);
            if (unlocked == showLocked) continue;
            if (shown % 2 == 0) {
                row = Ui.horizontal(c);
                row.setBaselineAligned(false);
                Ui.add(body, row, 12);
            }
            LinearLayout cell = Ui.vertical(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setBackground(Ui.rounded(c, R.color.surface, 20, R.color.border));
            cell.setPadding(Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16));
            cell.setAlpha(unlocked ? 1f : 0.65f);
            TextView ic = Ui.text(c, b.icon, 26, R.color.text, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(Ui.rounded(c, unlocked ? R.color.m3_tertiary_fixed : R.color.m3_surface_container, 999, 0));
            cell.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
            TextView n = Ui.text(c, b.name, 16, R.color.text, true);
            n.setGravity(Gravity.CENTER);
            Ui.add(cell, n, 10);
            TextView d = Ui.text(c, b.description, 13, R.color.text_secondary, false);
            d.setGravity(Gravity.CENTER);
            Ui.add(cell, d, 4);
            Ui.addInline(cell, Ui.pill(c, unlocked ? "✓ Đã đạt" : "🔒 Chưa mở", unlocked ? R.color.m3_primary_fixed : R.color.m3_surface_container,
                    unlocked ? R.color.brand_dark : R.color.text_secondary), 8);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (shown % 2 == 1) cl.setMarginStart(Ui.dp(c, 12));
            row.addView(cell, cl);
            shown++;
        }
        if (row != null && shown % 2 == 1) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        if (shown == 0) Ui.add(body, Ui.muted(c, showLocked ? "Bạn đã mở khóa toàn bộ huy hiệu. Xuất sắc!" : "Chưa mở khóa huy hiệu nào."), 12);

        // ---- CTA ----
        LinearLayout cta = Ui.horizontal(c);
        cta.setBackground(Ui.rounded(c, R.color.m3_surface_high, 20, 0));
        cta.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
        cta.addView(Ui.iconBox(c, R.drawable.ic_book, R.color.brand, android.R.color.white, 52, 26, 14));
        LinearLayout tt = Ui.vertical(c);
        tt.addView(Ui.text(c, "Tiếp tục phiên ôn luyện hôm nay", 16, R.color.text, true));
        tt.addView(Ui.text(c, "Giữ chuỗi ngày và tích lũy thêm huy hiệu", 13, R.color.text_secondary, false));
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ttl.setMarginStart(Ui.dp(c, 12));
        cta.addView(tt, ttl);
        cta.addView(Ui.iconBox(c, R.drawable.ic_arrow_right, R.color.surface, R.color.text, 44, 22, 999));
        cta.setOnClickListener(v -> {
            sheet.dismiss();
            state.navigate(state.activeDoc() != null ? AppState.Screen.FLASHCARD : AppState.Screen.HISTORY);
        });
        Ui.add(body, cta, 20);
    }
}
