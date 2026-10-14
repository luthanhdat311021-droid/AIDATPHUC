import { createClient } from '@supabase/supabase-js';
import { randomInt, timingSafeEqual } from 'crypto';
import dotenv from 'dotenv';

dotenv.config();

const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL || 'https://gwynjmlqymojrdlpzukn.supabase.co';
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

// Service-role client bypasses RLS: only for plan / role / order writes the user must never make themselves
const admin = serviceKey ? createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } }) : null;

// Prices live on the server only; the client sends a plan id, never an amount
export const PLANS = {
  pro_1m: { id: 'pro_1m', name: 'StudyMind Pro 30 ngày', days: 30, amount: 2000 }
};

// Free accounts get this many AI calls per day (Vietnam time); Pro has a high safety cap against abuse
export const LIMITS = {
  free: { generate: 5, chat: 30 },
  pro: { generate: 200, chat: 1000 }
};

export const ORDER_TTL_MINUTES = 30;
const CODE_PREFIX = 'SM';
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I: easy to type into a banking app
const CODE_RE = new RegExp(`${CODE_PREFIX}[${CODE_CHARS}]{6}`);

export class HttpError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const fail = (action, error) => {
  throw new HttpError(500, `Lỗi cơ sở dữ liệu khi ${action}: ${error.message}`);
};

const requireAdminClient = () => {
  if (!admin) throw new HttpError(503, 'Máy chủ chưa cấu hình SUPABASE_SERVICE_ROLE_KEY nên chưa bật thanh toán.', 'BILLING_DISABLED');
  return admin;
};

const newOrderCode = () => CODE_PREFIX + Array.from({ length: 6 }, () => CODE_CHARS[randomInt(CODE_CHARS.length)]).join('');

/** Profile row → what the app needs to decide access */
const toAccess = (row) => {
  const proActive = row?.plan === 'pro' && row.pro_until && new Date(row.pro_until).getTime() > Date.now();
  const role = row?.role === 'admin' ? 'admin' : 'user';
  return {
    role,
    plan: proActive || role === 'admin' ? 'pro' : 'free',
    proUntil: proActive ? row.pro_until : null,
    isPro: Boolean(proActive || role === 'admin'),
    isAdmin: role === 'admin'
  };
};

const toOrder = (o) => ({
  id: o.id,
  code: o.code,
  planId: o.plan_id,
  days: o.days,
  amount: o.amount,
  status: o.status === 'pending' && new Date(o.expires_at).getTime() < Date.now() ? 'expired' : o.status,
  createdAt: o.created_at,
  expiresAt: o.expires_at,
  paidAt: o.paid_at
});

// Receiving account linked to SePay (public info, shown on the QR); env vars can override it
const bankInfo = () => ({
  accountNumber: process.env.SEPAY_BANK_ACCOUNT || '0819883208',
  bankCode: process.env.SEPAY_BANK_CODE || 'MBBank',
  accountName: process.env.SEPAY_ACCOUNT_NAME || ''
});

const qrUrl = (amount, code) => {
  const { accountNumber, bankCode } = bankInfo();
  const q = new URLSearchParams({ acc: accountNumber, bank: bankCode, amount: String(amount), des: code });
  return `https://qr.sepay.vn/img?${q}`;
};

export const billingService = {
  isConfigured: () => Boolean(admin),

  /** Role + plan of a user. Read with their own RLS-scoped client so it works even without the service key. */
  async getAccess(userId, userClient) {
    const { data, error } = await (admin || userClient).from('studymind_accounts').select('*').eq('id', userId).maybeSingle();
    if (error) {
      // Migration 002 not run yet: everybody is a free user, the app keeps working
      console.warn('[billing] profiles unavailable:', error.message);
      return toAccess(null);
    }
    if (!data && admin) await admin.from('studymind_accounts').upsert({ id: userId }, { onConflict: 'id', ignoreDuplicates: true });
    return toAccess(data);
  },

  /** Count one AI call against today's quota, or throw 402 QUOTA_EXCEEDED */
  async consumeQuota(userId, access, kind) {
    if (!admin) return; // quota needs the service key; without it nothing is metered
    const limit = LIMITS[access.plan][kind];
    const { data, error } = await admin.rpc('consume_ai_quota', { p_user: userId, p_kind: kind, p_limit: limit });
    if (error) fail('kiểm tra hạn mức AI', error);
    if (data === -1) {
      throw new HttpError(402, access.isPro
        ? `Bạn đã dùng hết ${limit} lượt AI hôm nay. Hạn mức sẽ được làm mới lúc 0h.`
        : `Bạn đã dùng hết ${limit} lượt AI miễn phí hôm nay. Nâng cấp StudyMind Pro để dùng thêm.`,
        'QUOTA_EXCEEDED');
    }
  },

  async usageToday(userId) {
    if (!admin) return { generate: 0, chat: 0 };
    const day = new Date(Date.now() + 7 * 3600 * 1000).toISOString().slice(0, 10);
    const { data } = await admin.from('ai_usage_daily').select('kind,count').eq('user_id', userId).eq('day', day);
    const usage = { generate: 0, chat: 0 };
    (data || []).forEach(r => { usage[r.kind] = r.count; });
    return usage;
  },

  async summary(userId, access) {
    return {
      ...access,
      enabled: Boolean(admin) && Boolean(bankInfo().accountNumber),
      limits: LIMITS[access.plan],
      usage: await this.usageToday(userId),
      plans: Object.values(PLANS)
    };
  },

  async createOrder(userId, planId) {
    const db = requireAdminClient();
    const plan = PLANS[planId];
    if (!plan) throw new HttpError(400, 'Gói không hợp lệ.');
    if (!bankInfo().accountNumber || !bankInfo().bankCode) {
      throw new HttpError(503, 'Máy chủ chưa cấu hình tài khoản nhận tiền SePay.', 'BILLING_DISABLED');
    }

    // Reuse a still-open order for the same plan so repeated clicks don't spawn many codes
    const { data: open } = await db.from('payment_orders').select('*')
      .eq('user_id', userId).eq('plan_id', plan.id).eq('status', 'pending')
      .gt('expires_at', new Date().toISOString())
      .order('created_at', { ascending: false }).limit(1).maybeSingle();
    if (open) return this.present(open);

    const expiresAt = new Date(Date.now() + ORDER_TTL_MINUTES * 60 * 1000).toISOString();
    for (let attempt = 0; attempt < 5; attempt++) {
      const { data, error } = await db.from('payment_orders').insert({
        user_id: userId, code: newOrderCode(), plan_id: plan.id, days: plan.days, amount: plan.amount, expires_at: expiresAt
      }).select('*').single();
      if (!error) return this.present(data);
      if (error.code !== '23505') fail('tạo đơn thanh toán', error); // 23505 = code collision, try another
    }
    throw new HttpError(500, 'Không tạo được mã thanh toán, vui lòng thử lại.');
  },

  present(o) {
    return { ...toOrder(o), qrUrl: qrUrl(o.amount, o.code), bank: bankInfo(), planName: PLANS[o.plan_id]?.name || o.plan_id };
  },

  /** An order of this user only (RLS-style check done here because the admin client bypasses RLS) */
  async getOrder(userId, orderId) {
    const db = requireAdminClient();
    const { data, error } = await db.from('payment_orders').select('*').eq('id', orderId).eq('user_id', userId).maybeSingle();
    if (error) fail('tải đơn thanh toán', error);
    if (!data) throw new HttpError(404, 'Không tìm thấy đơn thanh toán.');
    return this.present(data);
  },

  async cancelOrder(userId, orderId) {
    const db = requireAdminClient();
    await db.from('payment_orders').update({ status: 'cancelled' })
      .eq('id', orderId).eq('user_id', userId).eq('status', 'pending');
  },

  async listOrders(userId) {
    const db = requireAdminClient();
    const { data, error } = await db.from('payment_orders').select('*').eq('user_id', userId).order('created_at', { ascending: false }).limit(20);
    if (error) fail('tải lịch sử thanh toán', error);
    return data.map(toOrder);
  },

  // ───────────────────────── SePay webhook ─────────────────────────

  /** SePay sends "Authorization: Apikey <key>"; compared in constant time */
  verifyWebhook(header) {
    const expected = process.env.SEPAY_WEBHOOK_API_KEY;
    if (!expected) return false;
    const given = String(header || '').replace(/^Apikey\s+/i, '').trim();
    const a = Buffer.from(given);
    const b = Buffer.from(expected);
    return a.length === b.length && timingSafeEqual(a, b);
  },

  async handleWebhook(tx) {
    const db = requireAdminClient();
    const sepayId = Number(tx?.id);
    if (!Number.isFinite(sepayId)) throw new HttpError(400, 'Thiếu id giao dịch.');

    const amount = Math.round(Number(tx.transferAmount) || 0);
    // SePay fills `code` when "payment code" detection is configured; otherwise find it in the transfer content
    const code = (String(tx.code || '').toUpperCase().match(CODE_RE) || String(tx.content || '').toUpperCase().replace(/[\s.-]/g, '').match(CODE_RE) || [])[0];

    let result = 'ignored';
    let orderId = null;
    if (tx.transferType === 'in' && code && amount > 0) {
      const { data: order } = await db.from('payment_orders').select('id').eq('code', code).maybeSingle();
      orderId = order?.id || null;
      const { data, error } = await db.rpc('confirm_payment', { p_code: code, p_amount: amount, p_sepay_id: sepayId });
      if (error) fail('xác nhận thanh toán', error);
      result = data;
    } else if (tx.transferType === 'in') {
      result = 'no_order';
    }

    // Primary key on sepay_id: a redelivered webhook is recorded once; confirm_payment is already idempotent
    const { error } = await db.from('payment_transactions').insert({
      sepay_id: sepayId,
      order_id: orderId,
      amount,
      content: tx.content || null,
      gateway: tx.gateway || null,
      reference_code: tx.referenceCode || null,
      transaction_date: tx.transactionDate || null,
      result,
      raw: tx
    });
    if (error && error.code !== '23505') console.error('[sepay] log transaction failed:', error.message);

    console.log(`[sepay] tx ${sepayId} ${amount}đ code=${code || '-'} → ${result}`);
    return { result, code };
  },

  // ───────────────────────── Admin ─────────────────────────

  async adminOverview() {
    const db = requireAdminClient();
    const [orders, txs, pros, paidAll] = await Promise.all([
      db.from('payment_orders').select('*').order('created_at', { ascending: false }).limit(50),
      db.from('payment_transactions').select('sepay_id,order_id,amount,content,gateway,result,received_at').order('received_at', { ascending: false }).limit(50),
      db.from('studymind_accounts').select('id,email,role,plan,pro_until').or(`role.eq.admin,pro_until.gt.${new Date().toISOString()}`).order('pro_until', { ascending: false }).limit(100),
      db.from('payment_orders').select('amount,paid_amount').eq('status', 'paid')
    ]);
    for (const [what, r] of [['tải đơn thanh toán', orders], ['tải giao dịch', txs], ['tải danh sách Pro', pros], ['tính doanh thu', paidAll]]) {
      if (r.error) fail(what, r.error);
    }

    const userIds = [...new Set(orders.data.map(o => o.user_id))];
    const { data: owners } = userIds.length
      ? await db.from('studymind_accounts').select('id,email').in('id', userIds)
      : { data: [] };
    const emailOf = Object.fromEntries((owners || []).map(p => [p.id, p.email]));

    return {
      revenue: paidAll.data.reduce((s, o) => s + Number(o.paid_amount || o.amount), 0),
      paidCount: paidAll.data.length,
      orders: orders.data.map(o => ({ ...toOrder(o), userId: o.user_id, email: emailOf[o.user_id] || null })),
      transactions: txs.data,
      members: pros.data.map(p => ({ id: p.id, email: p.email, ...toAccess(p) }))
    };
  },

  async adminSetPro(email, days) {
    const db = requireAdminClient();
    const { data: profile, error } = await db.from('studymind_accounts').select('id,email').ilike('email', String(email || '').trim()).maybeSingle();
    if (error) fail('tìm tài khoản', error);
    if (!profile) throw new HttpError(404, 'Không tìm thấy tài khoản với email này.');
    const { error: rpcError } = await db.rpc('admin_set_pro', { p_user: profile.id, p_days: Math.trunc(Number(days) || 0) });
    if (rpcError) fail('cập nhật gói', rpcError);
    const { data: updated } = await db.from('studymind_accounts').select('*').eq('id', profile.id).single();
    return { id: profile.id, email: profile.email, ...toAccess(updated) };
  }
};
