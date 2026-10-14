package com.studymind.nativeapp.ui.screens;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.studymind.nativeapp.R;
import com.studymind.nativeapp.data.Async;
import com.studymind.nativeapp.data.model.Models.AdminMember;
import com.studymind.nativeapp.data.model.Models.AdminOverview;
import com.studymind.nativeapp.data.model.Models.AdminTransaction;
import com.studymind.nativeapp.data.model.Models.PaymentOrder;
import com.studymind.nativeapp.ui.BaseFragment;
import com.studymind.nativeapp.ui.Ui;

/**
 * "Quản trị StudyMind" — AdminView.tsx. Listed in the drawer only for role = admin, and every
 * /api/admin call is re-checked on the server, so a modified APK still can't read or change anything.
 */
public class AdminFragment extends BaseFragment {
    private static final int[] DAYS = {7, 30, 90, 365};

    private AdminOverview data;
    private String error;
    private boolean loading, saving;
    private String email = "";
    private int days = 30;

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    @Override
    protected Object watchKey() { return "admin:" + state.isAdmin(); }

    private void load() {
        if (!state.isAdmin() || loading) return;
        loading = true;
        state.adminOverview(new Async.Callback<AdminOverview>() {
            @Override public void onSuccess(AdminOverview o) { data = o; error = null; loading = false; if (isAdded()) rerender(); }
            @Override public void onError(String m) { error = m; loading = false; if (isAdded()) rerender(); }
        });
    }

    @Override
    protected void render(LinearLayout root) {
        Context c = requireContext();
        if (!state.isAdmin()) {
            TextView t = Ui.text(c, "Bạn không có quyền truy cập trang này.", 14, R.color.text_muted, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 64), 0, 0);
            root.addView(t);
            return;
        }
        if (data == null && !loading && error == null) load(); // role arrived after the screen opened

        LinearLayout head = Ui.horizontal(c);
        LinearLayout title = Ui.iconText(c, R.drawable.ic_shield, "Quản trị StudyMind", 20, Ui.color(c, R.color.text), true);
        ((ImageView) title.getChildAt(0)).setColorFilter(Ui.color(c, R.color.brand));
        Ui.addWeighted(head, title, 0);
        LinearLayout refresh = Ui.pillButton(c, R.drawable.ic_refresh, loading ? "Đang tải" : "Làm mới", R.color.surface, R.color.brand, R.color.brand, v -> { load(); rerender(); });
        refresh.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        refresh.setAlpha(loading ? 0.5f : 1f);
        Ui.addWrap(head, refresh, 8);
        root.addView(head);

        if (error != null) {
            TextView e = Ui.text(c, error, 13, R.color.rose, false);
            e.setBackground(Ui.rounded(c, R.color.rose_50, 12, R.color.rose_soft));
            e.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
            Ui.add(root, e, 16);
        }

        // ---- Stat tiles (2 × 2) ----
        int pros = 0;
        if (data != null) for (AdminMember m : data.members) if (!m.isAdmin) pros++;
        LinearLayout r1 = Ui.horizontal(c);
        Ui.addWeighted(r1, tile(c, R.drawable.ic_wallet, data != null ? Ui.vnd(data.revenue) : "—", "Doanh thu"), 0);
        Ui.addWeighted(r1, tile(c, R.drawable.ic_receipt, data != null ? String.valueOf(data.paidCount) : "—", "Đơn đã thanh toán"), 12);
        Ui.add(root, r1, 16);
        LinearLayout r2 = Ui.horizontal(c);
        Ui.addWeighted(r2, tile(c, R.drawable.ic_users, data != null ? String.valueOf(pros) : "—", "Thành viên Pro"), 0);
        Ui.addWeighted(r2, tile(c, R.drawable.ic_bank, data != null ? String.valueOf(data.transactions.size()) : "—", "Giao dịch SePay"), 12);
        Ui.add(root, r2, 12);

        // ---- Manual grant / revoke ----
        MaterialCardView grant = Ui.card(c);
        LinearLayout gi = Ui.inner(grant);
        gi.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20));
        gi.addView(Ui.text(c, "Cấp / thu hồi Pro thủ công", 14, R.color.text, true));
        EditText emailField = Ui.field(c, "email@nguoidung.com", false);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setText(email);
        emailField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { email = s.toString().trim(); }
        });
        Ui.add(gi, emailField, 12);
        String[] labels = new String[DAYS.length];
        int sel = 1;
        for (int i = 0; i < DAYS.length; i++) { labels[i] = DAYS[i] + " ngày"; if (DAYS[i] == days) sel = i; }
        Ui.add(gi, Ui.select(c, labels, sel, i -> days = DAYS[i]), 8);
        LinearLayout btns = Ui.horizontal(c);
        LinearLayout give = Ui.pillButton(c, 0, "Cấp Pro", R.color.brand, android.R.color.white, 0, v -> setPro(days));
        give.setBackground(Ui.rounded(c, R.color.brand, 8, 0));
        give.setAlpha(saving ? 0.5f : 1f);
        Ui.addWeighted(btns, give, 0);
        LinearLayout revoke = Ui.pillButton(c, 0, "Thu hồi", R.color.surface, R.color.rose, R.color.rose_soft, v ->
                new MaterialAlertDialogBuilder(c).setMessage("Thu hồi Pro của " + email + "?")
                        .setNegativeButton("Hủy", null).setPositiveButton("Thu hồi", (d, w) -> setPro(0)).show());
        revoke.setAlpha(saving ? 0.5f : 1f);
        Ui.addWeighted(btns, revoke, 8);
        Ui.add(gi, btns, 12);
        Ui.add(root, grant, 18);

        if (data == null) return;

        // ---- Recent orders ----
        LinearLayout orders = Ui.section(root, "Đơn thanh toán gần đây", null, null, 18);
        if (data.orders.isEmpty()) orders.addView(Ui.text(c, "Chưa có đơn.", 12, R.color.slate_400, false));
        for (PaymentOrder o : data.orders) {
            LinearLayout row = Ui.horizontal(c);
            LinearLayout left = Ui.vertical(c);
            TextView t = Ui.text(c, o.code + " · " + (o.email != null ? o.email : "không rõ"), 12, R.color.text, true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            left.addView(t);
            left.addView(Ui.text(c, Ui.dateTime(o.createdAt), 11, R.color.slate_400, false));
            Ui.addWeighted(row, left, 0);
            LinearLayout right = Ui.vertical(c);
            right.setGravity(Gravity.END);
            right.addView(Ui.text(c, Ui.vnd(o.amount), 12, R.color.text, true));
            right.addView(Ui.text(c, o.status, 11, "paid".equals(o.status) ? R.color.emerald : R.color.slate_400, false));
            Ui.addWrap(row, right, 8);
            addRow(orders, row);
        }

        // ---- SePay webhook log ----
        LinearLayout txs = Ui.section(root, "Webhook SePay", null, null, 16);
        if (data.transactions.isEmpty()) txs.addView(Ui.text(c, "Chưa nhận giao dịch nào.", 12, R.color.slate_400, false));
        for (AdminTransaction tx : data.transactions) {
            String label;
            int color;
            switch (tx.result) {
                case "paid": label = "Kích hoạt Pro"; color = R.color.emerald; break;
                case "already_paid": label = "Trùng (đã xử lý)"; color = R.color.slate_400; break;
                case "underpaid": label = "Thiếu tiền"; color = R.color.amber_700; break;
                case "no_order": label = "Không khớp đơn"; color = R.color.amber_700; break;
                default: label = "Bỏ qua"; color = R.color.slate_400;
            }
            LinearLayout row = Ui.horizontal(c);
            LinearLayout left = Ui.vertical(c);
            left.addView(Ui.text(c, "#" + tx.sepay_id + " · " + (tx.gateway != null ? tx.gateway : ""), 12, R.color.text, true));
            if (tx.content != null) {
                TextView ct = Ui.text(c, tx.content, 11, R.color.slate_400, false);
                ct.setSingleLine(true);
                ct.setEllipsize(android.text.TextUtils.TruncateAt.END);
                left.addView(ct);
            }
            Ui.addWeighted(row, left, 0);
            LinearLayout right = Ui.vertical(c);
            right.setGravity(Gravity.END);
            right.addView(Ui.text(c, Ui.vnd(tx.amount), 12, R.color.text, true));
            right.addView(Ui.text(c, label, 11, color, false));
            Ui.addWrap(row, right, 8);
            addRow(txs, row);
        }

        // ---- Pro members & admins ----
        LinearLayout members = Ui.section(root, "Thành viên Pro & quản trị", null, null, 16);
        if (data.members.isEmpty()) members.addView(Ui.text(c, "Chưa có thành viên Pro.", 12, R.color.slate_400, false));
        for (AdminMember m : data.members) {
            LinearLayout row = Ui.horizontal(c);
            TextView e = Ui.text(c, m.email != null ? m.email : m.id, 12, R.color.text, true);
            e.setSingleLine(true);
            e.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Ui.addWeighted(row, e, 0);
            Ui.addWrap(row, Ui.text(c, m.isAdmin ? "Quản trị viên" : m.proUntil != null ? "Pro tới " + Ui.date(m.proUntil) : "", 12, R.color.text_muted, false), 8);
            row.setOnClickListener(v -> { if (m.email != null) { email = m.email; rerender(); } });
            addRow(members, row);
        }
    }

    private void addRow(LinearLayout parent, View row) {
        Context c = requireContext();
        if (parent.getChildCount() > 0) {
            View sep = new View(c);
            sep.setBackgroundColor(Ui.color(c, R.color.slate_100));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
            sl.topMargin = Ui.dp(c, 8);
            parent.addView(sep, sl);
        }
        Ui.add(parent, row, 8);
    }

    private MaterialCardView tile(Context c, int icon, String value, String label) {
        MaterialCardView card = Ui.card(c);
        LinearLayout in = Ui.inner(card);
        in.addView(Ui.icon(c, icon, 16, Ui.color(c, R.color.brand)));
        Ui.add(in, Ui.text(c, value, 18, R.color.text, true), 6);
        in.addView(Ui.text(c, label, 11, R.color.text_muted, false));
        return card;
    }

    private void setPro(int grantDays) {
        if (email.isEmpty()) { toast("Nhập email tài khoản."); return; }
        if (saving) return;
        saving = true;
        rerender();
        state.adminSetPro(email, grantDays, ok -> {
            saving = false;
            if (ok) load();
            else if (isAdded()) rerender();
        });
    }
}
