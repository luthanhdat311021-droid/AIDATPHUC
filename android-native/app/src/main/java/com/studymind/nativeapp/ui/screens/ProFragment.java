package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.BillingInfo;
import com.studymind.nativeapp.data.model.Models.PaymentOrder;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;
import com.studymind.nativeapp.ui.dialogs.UpgradeSheet;

import java.util.ArrayList;
import java.util.List;

/**
 * "Tài khoản Pro" — ProView.tsx: plan hero, today's AI quota, Pro benefits, SePay payment history.
 * Everything shown comes from /api/v1/billing/me; the server alone decides plan and role.
 */
public class ProFragment extends BaseFragment {
    private List<PaymentOrder> orders = new ArrayList<>();
    private boolean billingFailed;

    @Override
    public void onStart() {
        super.onStart();
        state.refreshBilling(ok -> { billingFailed = !ok && state.billing() == null; if (isAdded()) rerender(); });
        state.listOrders(new Async.Callback<List<PaymentOrder>>() {
            @Override public void onSuccess(List<PaymentOrder> o) { orders = o; if (isAdded()) rerender(); }
            @Override public void onError(String m) {}
        });
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        BillingInfo b = state.billing();
        boolean pro = state.isPro(), admin = state.isAdmin();

        // ---- Hero: amber→orange when Pro, teal otherwise ----
        LinearLayout hero = Ui.vertical(c);
        hero.setBackground(pro ? Ui.gradient(c, 24, 0xFFF59E0B, 0xFFEA580C) : Ui.gradient(c, 24, 0xFF0F766E, 0xFF134E4A));
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
        hero.setElevation(Ui.dp(c, 4));
        LinearLayout kicker = Ui.iconText(c, R.drawable.ic_crown, "GÓI CỦA BẠN", 12, 0xE6FFFFFF, true);
        hero.addView(kicker);
        TextView title = Ui.text(c, admin ? "Quản trị viên (Pro)" : pro ? "StudyMind Pro" : "Gói Miễn phí", 26, R.color.text, true);
        title.setTextColor(Color.WHITE);
        Ui.add(hero, title, 4);
        TextView sub = Ui.text(c, admin ? "Tài khoản quản trị có toàn bộ quyền Pro."
                : pro && b != null && b.proUntil != null ? "Hiệu lực tới " + Ui.dateTime(b.proUntil)
                : "Nâng cấp để mở khóa phân tích Chuyên sâu, Knowledge Fusion và nhiều lượt AI hơn.", 13, R.color.text, false);
        sub.setTextColor(0xE6FFFFFF);
        Ui.add(hero, sub, 4);
        if (!admin) {
            long price = b != null && !b.plans.isEmpty() ? b.plans.get(0).amount : 2000;
            TextView btn = Ui.text(c, pro ? "Gia hạn Pro" : "Nâng cấp Pro · " + Ui.vnd(price), 14, R.color.text, true);
            btn.setTextColor(0xFF0F172A);
            btn.setBackground(Ui.rounded(c, android.R.color.white, 12, 0));
            btn.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10));
            btn.setElevation(Ui.dp(c, 2));
            btn.setOnClickListener(v -> state.openUpgrade(null));
            Ui.addInline(hero, btn, 16);
        }
        root.addView(hero);

        // ---- Today's AI quota ----
        LinearLayout quota = Ui.section(root, "Hạn mức AI hôm nay", null, null, 20);
        if (b == null) {
            quota.addView(Ui.text(c, billingFailed ? "Chưa tải được hạn mức từ máy chủ (hệ thống Pro có thể chưa được bật)." : "Đang tải...",
                    12, billingFailed ? R.color.amber_700 : R.color.slate_400, false));
        } else {
            usage(quota, R.drawable.ic_sparkles, "Tạo nội dung AI", b.usage.generate, b.limits.generate, 0);
            usage(quota, R.drawable.ic_message, "Trò chuyện với gia sư AI", b.usage.chat, b.limits.chat, 16);
            Ui.add(quota, Ui.text(c, "Hạn mức được làm mới lúc 0h (giờ Việt Nam).", 11, R.color.slate_400, false), 14);
        }

        // ---- Pro benefits ----
        LinearLayout perks = Ui.section(root, "Quyền lợi Pro", null, null, 16);
        for (String f : UpgradeSheet.PRO_FEATURES) {
            LinearLayout row = Ui.horizontal(c);
            row.setGravity(android.view.Gravity.TOP);
            row.addView(Ui.icon(c, R.drawable.ic_check_circle, 16, Ui.color(c, pro ? R.color.emerald : R.color.slate_300)));
            TextView t = Ui.text(c, f, 14, R.color.text_secondary, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 8));
            row.addView(t, tl);
            Ui.add(perks, row, perks.getChildCount() > 0 ? 10 : 0);
        }

        // ---- Payment history ----
        LinearLayout hist = Ui.section(root, "Lịch sử thanh toán", null, null, 16);
        if (orders.isEmpty()) hist.addView(Ui.text(c, "Chưa có giao dịch nào.", 12, R.color.slate_400, false));
        for (int i = 0; i < orders.size(); i++) {
            PaymentOrder o = orders.get(i);
            LinearLayout row = Ui.horizontal(c);
            LinearLayout left = Ui.vertical(c);
            left.addView(Ui.text(c, o.code + " · " + Ui.vnd(o.amount), 14, R.color.text, true));
            left.addView(Ui.text(c, Ui.dateTime(o.createdAt) + " · " + o.days + " ngày", 11, R.color.slate_400, false));
            Ui.addWeighted(row, left, 0);
            Ui.addWrap(row, statusPill(c, o.status), 8);
            if (i > 0) {
                android.view.View sep = new android.view.View(c);
                sep.setBackgroundColor(Ui.color(c, R.color.slate_100));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
                sl.topMargin = Ui.dp(c, 10);
                hist.addView(sep, sl);
            }
            Ui.add(hist, row, 10);
        }
    }

    private void usage(LinearLayout parent, int icon, String label, int used, int limit, int top) {
        Context c = requireContext();
        LinearLayout row = Ui.horizontal(c);
        LinearLayout l = Ui.iconText(c, icon, label, 12, Ui.color(c, R.color.text_secondary), true);
        ((ImageView) l.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.addWeighted(row, l, 0);
        Ui.addWrap(row, Ui.text(c, used + "/" + limit + " hôm nay", 12, R.color.text_muted, false), 8);
        Ui.add(parent, row, top);
        int pct = limit > 0 ? Math.min(100, used * 100 / limit) : 0;
        Ui.add(parent, Ui.track(c, pct, pct >= 90 ? R.color.rose : R.color.brand, 8), 6);
    }

    static TextView statusPill(Context c, String status) {
        int bg, fg, stroke;
        String label;
        switch (status) {
            case "paid": label = "Đã thanh toán"; bg = R.color.emerald_50; fg = R.color.emerald_700; stroke = R.color.emerald_soft; break;
            case "pending": label = "Chờ thanh toán"; bg = R.color.amber_50; fg = R.color.amber_700; stroke = R.color.amber_soft; break;
            case "expired": label = "Hết hạn"; bg = R.color.slate_100; fg = R.color.text_muted; stroke = R.color.border; break;
            default: label = "Đã hủy"; bg = R.color.slate_100; fg = R.color.text_muted; stroke = R.color.border;
        }
        TextView t = Ui.text(c, label, 11, fg, true);
        t.setBackground(Ui.rounded(c, bg, 999, stroke));
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
        return t;
    }
}
