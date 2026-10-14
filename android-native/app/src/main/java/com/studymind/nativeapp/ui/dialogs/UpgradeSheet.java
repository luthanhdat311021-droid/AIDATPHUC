package com.studymind.nativeapp.ui.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.StudyMindApp;
import com.studymind.nativeapp.data.AppState;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.BillingInfo;
import com.studymind.nativeapp.data.model.Models.PaymentOrder;
import com.studymind.nativeapp.data.model.Models.PlanInfo;
import com.studymind.nativeapp.ui.ImageLoader;
import com.studymind.nativeapp.ui.Ui;

/**
 * StudyMind Pro checkout: pick plan → SePay VietQR → the SePay webhook marks the order paid on the
 * server → this sheet sees it by polling the order every 3 seconds and confirms the upgrade.
 * Closing the sheet never cancels the order: the user may already have sent the money.
 */
public final class UpgradeSheet {
    public static final String[] PRO_FEATURES = {
            "Phân tích \"Chuyên sâu\" (sơ đồ tư duy tới 6 tầng)",
            "Hợp nhất kiến thức đa tài liệu (Knowledge Fusion)",
            "Tạo tài liệu AI gần như không giới hạn mỗi ngày",
            "Gia sư AI trò chuyện không giới hạn"
    };
    private static final long POLL_MS = 3000;
    private static BottomSheetDialog openSheet;

    private final AppCompatActivity activity;
    private final AppState state = StudyMindApp.state();
    private final BottomSheetDialog dialog;
    private final LinearLayout body;
    private final String reason;
    private PaymentOrder order;
    private boolean creating;
    private String error;
    private boolean closed;
    private boolean billingFailed;

    private final Runnable poll = this::pollOrder;
    private final Runnable countdown = this::tickCountdown;
    private TextView waitView;

    public static void show(AppCompatActivity activity, String reason) {
        if (openSheet != null && openSheet.isShowing()) return;
        new UpgradeSheet(activity, reason);
    }

    private UpgradeSheet(AppCompatActivity activity, String reason) {
        this.activity = activity;
        this.reason = reason;
        Context c = activity;
        dialog = new BottomSheetDialog(c);
        ScrollView scroll = new ScrollView(c);
        body = Ui.vertical(c);
        int p = Ui.dp(c, 20);
        body.setPadding(p, p, p, Ui.dp(c, 32));
        body.setBackgroundColor(Ui.color(c, R.color.surface));
        scroll.addView(body);
        dialog.setContentView(scroll);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.setOnDismissListener(d -> {
            closed = true;
            Async.cancel(poll);
            Async.cancel(countdown);
            openSheet = null;
        });
        openSheet = dialog;
        render();
        dialog.show();
        loadPlans();
    }

    private void loadPlans() {
        billingFailed = false;
        state.refreshBilling(ok -> {
            billingFailed = !ok && state.billing() == null;
            if (!closed && order == null) render();
        });
    }

    private void render() {
        if (closed) return;
        Async.cancel(countdown);
        Context c = activity;
        body.removeAllViews();
        BillingInfo b = state.billing();

        // from-[#0F766E] to-[#134E4A] header, amber "StudyMind Pro" kicker (UpgradeModal.tsx)
        LinearLayout hi = Ui.vertical(c);
        hi.setBackground(Ui.gradient(c, 20, 0xFF0F766E, 0xFF134E4A));
        hi.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22));
        LinearLayout kicker = Ui.iconText(c, R.drawable.ic_crown, "STUDYMIND PRO", 12, 0xFFFCD34D, true);
        hi.addView(kicker);
        TextView title = Ui.text(c, order != null && "paid".equals(order.status) ? "Nâng cấp thành công!" : "Học sâu hơn, không giới hạn", 20, R.color.text, true);
        title.setTextColor(0xFFFFFFFF);
        Ui.add(hi, title, 4);
        if (reason != null && order == null) {
            TextView r = Ui.text(c, reason, 12, R.color.text, false);
            r.setTextColor(0xFFCCFBF1);
            Ui.add(hi, r, 8);
        }
        body.addView(hi);

        if (order == null) renderPlans(b);
        else if ("paid".equals(order.status)) renderPaid(b);
        else renderQr();
    }

    private void renderPlans(BillingInfo b) {
        Context c = activity;
        if (b != null && b.isPro) {
            MaterialCardView info = Ui.tintedCard(c, R.color.emerald_soft, R.color.emerald);
            Ui.inner(info).addView(Ui.body(c, "Bạn đang dùng Pro" + (b.proUntil != null ? " tới " + Ui.date(b.proUntil) : "") + ". Thanh toán thêm sẽ cộng dồn thời hạn."));
            Ui.add(body, info, 12);
        }
        for (String f : PRO_FEATURES) Ui.add(body, Ui.text(c, "✓ " + f, 14, R.color.text, false), 8);

        if (b != null && !b.enabled) {
            MaterialCardView warn = Ui.tintedCard(c, R.color.amber_soft, R.color.amber);
            Ui.inner(warn).addView(Ui.body(c, "Thanh toán chưa được bật trên máy chủ. Vui lòng thử lại sau."));
            Ui.add(body, warn, 12);
        }
        if (b == null && billingFailed) {
            // e.g. the backend without the billing API is still deployed, or no network
            TextView err = Ui.text(c, "Chưa tải được thông tin gói Pro từ máy chủ. Hệ thống thanh toán có thể chưa được bật, vui lòng thử lại sau.", 13, R.color.text, false);
            err.setTextColor(0xFF92400E);
            err.setBackground(Ui.rounded(c, R.color.amber_50, 12, R.color.amber_soft));
            err.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
            Ui.add(body, err, 16);
            LinearLayout retry = Ui.pillButton(c, R.drawable.ic_refresh, "Thử lại", R.color.surface, R.color.brand, R.color.brand, v -> { loadPlans(); render(); });
            Ui.add(body, retry, 10);
            return;
        }
        if (b == null) {
            Ui.add(body, Ui.spinner(c), 16);
            return;
        }
        for (PlanInfo plan : b.plans) {
            MaterialCardView card = Ui.tintedCard(c, R.color.brand_soft, R.color.brand);
            card.setStrokeWidth(Ui.dp(c, 2));
            LinearLayout row = Ui.horizontal(c);
            LinearLayout left = Ui.vertical(c);
            left.addView(Ui.text(c, plan.name, 15, R.color.text, true));
            left.addView(Ui.muted(c, plan.days + " ngày · " + (plan.badge != null ? plan.badge : "Thanh toán bằng chuyển khoản QR")));
            Ui.addWeighted(row, left, 0);
            Ui.addWrap(row, Ui.text(c, creating ? "…" : Ui.vnd(plan.amount), 18, R.color.brand, true), 8);
            Ui.inner(card).addView(row);
            card.setEnabled(b.enabled && !creating);
            card.setOnClickListener(v -> startPayment(plan.id));
            Ui.add(body, card, 16);
        }
        if (error != null) Ui.add(body, Ui.text(c, error, 13, R.color.rose, false), 8);
    }

    private void startPayment(String planId) {
        if (creating) return;
        creating = true;
        error = null;
        render();
        state.createOrder(planId, new Async.Callback<PaymentOrder>() {
            @Override public void onSuccess(PaymentOrder o) {
                creating = false;
                order = o;
                render();
                Async.mainDelayed(poll, POLL_MS);
            }
            @Override public void onError(String m) { creating = false; error = m; render(); }
        });
    }

    private void pollOrder() {
        if (closed || order == null) return;
        state.getOrder(order.id, new Async.Callback<PaymentOrder>() {
            @Override public void onSuccess(PaymentOrder o) {
                if (closed) return;
                boolean changed = !o.status.equals(order.status);
                order.status = o.status;
                if ("paid".equals(o.status)) {
                    host().toast("🎉 Thanh toán thành công! Chào mừng bạn đến với StudyMind Pro.");
                    render();
                    return;
                }
                if (changed) render();
                if ("pending".equals(o.status)) Async.mainDelayed(poll, POLL_MS);
            }
            @Override public void onError(String m) { if (!closed) Async.mainDelayed(poll, POLL_MS); }
        });
    }

    private void renderQr() {
        Context c = activity;
        long secondsLeft = Math.max(0, (Ui.epochMillis(order.expiresAt) - System.currentTimeMillis()) / 1000);
        boolean expired = "expired".equals(order.status) || "cancelled".equals(order.status) || secondsLeft == 0;
        if (expired) {
            TextView t = Ui.body(c, "Mã thanh toán đã hết hạn. Nếu bạn đã chuyển khoản, Pro vẫn sẽ được kích hoạt khi tiền về.");
            t.setGravity(Gravity.CENTER);
            Ui.add(body, t, 16);
            Ui.add(body, Ui.outline(c, "Tạo mã mới", v -> { order = null; Async.cancel(poll); render(); }), 12);
            return;
        }

        ImageView qr = new ImageView(c);
        qr.setAdjustViewBounds(true);
        qr.setContentDescription("Mã QR chuyển khoản " + order.code);
        ImageLoader.load(qr, order.qrUrl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 240), Ui.dp(c, 240));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(c, 16);
        body.addView(qr, lp);
        TextView hint = Ui.muted(c, "Mở app ngân hàng → Quét QR → Xác nhận");
        hint.setGravity(Gravity.CENTER);
        Ui.add(body, hint, 6);

        MaterialCardView info = Ui.card(c);
        LinearLayout ii = Ui.inner(info);
        if (order.bank != null) {
            row(ii, "Ngân hàng", order.bank.bankCode, null);
            row(ii, "Số tài khoản", order.bank.accountNumber, "số tài khoản");
            if (order.bank.accountName != null && !order.bank.accountName.isEmpty()) row(ii, "Chủ tài khoản", order.bank.accountName, null);
        }
        row(ii, "Số tiền", Ui.vnd(order.amount), null);
        row(ii, "Nội dung", order.code, "nội dung chuyển khoản");
        Ui.add(body, info, 12);

        waitView = Ui.text(c, "", 13, R.color.brand, true);
        waitView.setGravity(Gravity.CENTER);
        Ui.add(body, waitView, 12);
        TextView keep = Ui.muted(c, "Giữ nguyên nội dung " + order.code + " để hệ thống tự kích hoạt Pro trong vài giây.");
        keep.setGravity(Gravity.CENTER);
        Ui.add(body, keep, 4);
        tickCountdown();
    }

    /** Only the countdown text changes each second; the sheet is rebuilt when the code expires */
    private void tickCountdown() {
        if (closed || order == null || waitView == null) return;
        long left = Math.max(0, (Ui.epochMillis(order.expiresAt) - System.currentTimeMillis()) / 1000);
        if (left == 0) { render(); return; }
        waitView.setText("⏳ Đang chờ thanh toán · còn " + left / 60 + ":" + String.format("%02d", left % 60));
        Async.mainDelayed(countdown, 1000);
    }

    private void row(LinearLayout parent, String label, String value, String copyLabel) {
        Context c = activity;
        LinearLayout r = Ui.horizontal(c);
        Ui.addWeighted(r, Ui.muted(c, label), 0);
        Ui.addWrap(r, Ui.text(c, value, 14, R.color.text, true), 8);
        if (copyLabel != null) {
            TextView copy = Ui.text(c, "  Sao chép", 12, R.color.brand, true);
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(copyLabel, value));
                host().toast("Đã sao chép " + copyLabel);
            });
            Ui.addWrap(r, copy, 4);
        }
        Ui.add(parent, r, 8);
    }

    private void renderPaid(BillingInfo b) {
        Context c = activity;
        TextView party = Ui.text(c, "🎉", 48, R.color.text, false);
        party.setGravity(Gravity.CENTER);
        Ui.add(body, party, 16);
        TextView t = Ui.body(c, "Đã nhận " + Ui.vnd(order.amount) + ". Tài khoản của bạn đã được nâng cấp lên StudyMind Pro"
                + (b != null && b.proUntil != null ? " tới " + Ui.date(b.proUntil) : "") + ".");
        t.setGravity(Gravity.CENTER);
        Ui.add(body, t, 8);
        Ui.add(body, Ui.primary(c, "Bắt đầu học", v -> dialog.dismiss()), 16);
    }

    private AppState.Host host() { return (AppState.Host) activity; }
}
