// StudyMind Pro billing API on Supabase Edge Functions (independent of the Vercel web deploy).
// Routes (all need the signed-in user's Supabase access token):
//   GET  /me                    plan, role, AI quota, plans
//   POST /orders  {planId}      create (or reuse) a SePay VietQR order
//   GET  /orders                the caller's payment history
//   GET  /orders/:id            one of the caller's orders (+ fresh access once paid)
//   POST /orders/:id/cancel
//   GET  /admin/overview        admin only
//   POST /admin/members {email, days}   admin only: grant (days>0) or revoke (days<=0) Pro
// Same rules as backend/services/billingService.js; prices live here, never in the client.
import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const PLANS: Record<string, { id: string; name: string; days: number; amount: number; badge?: string }> = {
  pro_1m: { id: "pro_1m", name: "StudyMind Pro 30 ngày", days: 30, amount: 2000 },
};
const LIMITS = { free: { generate: 5, chat: 30 }, pro: { generate: 200, chat: 1000 } };
const BANK = {
  accountNumber: Deno.env.get("SEPAY_BANK_ACCOUNT") ?? "0819883208",
  bankCode: Deno.env.get("SEPAY_BANK_CODE") ?? "MBBank",
  accountName: Deno.env.get("SEPAY_ACCOUNT_NAME") ?? "",
};
const ORDER_TTL_MINUTES = 30;
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

class HttpError extends Error {
  constructor(public status: number, message: string, public code?: string) { super(message); }
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

type Access = { role: string; plan: string; proUntil: string | null; isPro: boolean; isAdmin: boolean };

// deno-lint-ignore no-explicit-any
function toAccess(row: any): Access {
  const proActive = row?.plan === "pro" && row?.pro_until && new Date(row.pro_until).getTime() > Date.now();
  const role = row?.role === "admin" ? "admin" : "user";
  return {
    role,
    plan: proActive || role === "admin" ? "pro" : "free",
    proUntil: proActive ? row.pro_until : null,
    isPro: Boolean(proActive || role === "admin"),
    isAdmin: role === "admin",
  };
}

// deno-lint-ignore no-explicit-any
function toOrder(o: any) {
  return {
    id: o.id,
    code: o.code,
    planId: o.plan_id,
    days: o.days,
    amount: o.amount,
    status: o.status === "pending" && new Date(o.expires_at).getTime() < Date.now() ? "expired" : o.status,
    createdAt: o.created_at,
    expiresAt: o.expires_at,
    paidAt: o.paid_at,
  };
}

// deno-lint-ignore no-explicit-any
function present(o: any) {
  const q = new URLSearchParams({ acc: BANK.accountNumber, bank: BANK.bankCode, amount: String(o.amount), des: o.code });
  return { ...toOrder(o), qrUrl: `https://qr.sepay.vn/img?${q}`, bank: BANK, planName: PLANS[o.plan_id]?.name ?? o.plan_id };
}

function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return "SM" + Array.from(bytes, (b) => CODE_CHARS[b % CODE_CHARS.length]).join("");
}

async function getAccess(userId: string): Promise<Access> {
  const { data } = await admin.from("studymind_accounts").select("*").eq("id", userId).maybeSingle();
  if (!data) await admin.from("studymind_accounts").upsert({ id: userId }, { onConflict: "id", ignoreDuplicates: true });
  return toAccess(data);
}

async function usageToday(userId: string) {
  const day = new Date(Date.now() + 7 * 3600 * 1000).toISOString().slice(0, 10);
  const { data } = await admin.from("ai_usage_daily").select("kind,count").eq("user_id", userId).eq("day", day);
  const usage: Record<string, number> = { generate: 0, chat: 0 };
  (data ?? []).forEach((r) => { usage[r.kind] = r.count; });
  return usage;
}

async function handle(req: Request, path: string[]): Promise<Response> {
  // Verify the caller's Supabase session; role/plan always come from the database, never the request
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await createClient(SUPABASE_URL, ANON_KEY).auth.getUser(token);
  if (!auth?.user) throw new HttpError(401, "Vui lòng đăng nhập để tiếp tục.");
  const userId = auth.user.id;
  const access = await getAccess(userId);
  const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};

  // GET /me
  if (req.method === "GET" && path[0] === "me") {
    return json({
      success: true,
      data: { ...access, enabled: true, limits: LIMITS[access.plan as "free" | "pro"], usage: await usageToday(userId), plans: Object.values(PLANS) },
    });
  }

  // POST /orders
  if (req.method === "POST" && path[0] === "orders" && path.length === 1) {
    const plan = PLANS[body?.planId];
    if (!plan) throw new HttpError(400, "Gói không hợp lệ.");
    // Reuse a still-open order for the same plan so repeated taps don't spawn many codes
    const { data: open } = await admin.from("payment_orders").select("*")
      .eq("user_id", userId).eq("plan_id", plan.id).eq("status", "pending").gt("expires_at", new Date().toISOString())
      .order("created_at", { ascending: false }).limit(1).maybeSingle();
    if (open) return json({ success: true, order: present(open) });
    const expiresAt = new Date(Date.now() + ORDER_TTL_MINUTES * 60_000).toISOString();
    for (let attempt = 0; attempt < 5; attempt++) {
      const { data, error } = await admin.from("payment_orders").insert({
        user_id: userId, code: newCode(), plan_id: plan.id, days: plan.days, amount: plan.amount, expires_at: expiresAt,
      }).select("*").single();
      if (!error) return json({ success: true, order: present(data) });
      if (error.code !== "23505") throw new HttpError(500, "Lỗi tạo đơn thanh toán: " + error.message);
    }
    throw new HttpError(500, "Không tạo được mã thanh toán, vui lòng thử lại.");
  }

  // GET /orders
  if (req.method === "GET" && path[0] === "orders" && path.length === 1) {
    const { data, error } = await admin.from("payment_orders").select("*").eq("user_id", userId)
      .order("created_at", { ascending: false }).limit(20);
    if (error) throw new HttpError(500, error.message);
    return json({ success: true, orders: data.map(toOrder) });
  }

  // GET /orders/:id  and  POST /orders/:id/cancel — only the caller's own orders
  if (path[0] === "orders" && path[1]) {
    if (req.method === "POST" && path[2] === "cancel") {
      await admin.from("payment_orders").update({ status: "cancelled" }).eq("id", path[1]).eq("user_id", userId).eq("status", "pending");
      return json({ success: true });
    }
    const { data } = await admin.from("payment_orders").select("*").eq("id", path[1]).eq("user_id", userId).maybeSingle();
    if (!data) throw new HttpError(404, "Không tìm thấy đơn thanh toán.");
    const fresh = data.status === "paid" ? await getAccess(userId) : access;
    return json({ success: true, order: present(data), access: fresh });
  }

  // ---- Admin (role = admin) ----
  if (path[0] === "admin") {
    if (!access.isAdmin) throw new HttpError(403, "Chỉ quản trị viên được truy cập.", "FORBIDDEN");

    if (req.method === "GET" && path[1] === "overview") {
      const [orders, txs, pros, paidAll] = await Promise.all([
        admin.from("payment_orders").select("*").order("created_at", { ascending: false }).limit(50),
        admin.from("payment_transactions").select("sepay_id,order_id,amount,content,gateway,result,received_at").order("received_at", { ascending: false }).limit(50),
        admin.from("studymind_accounts").select("id,email,role,plan,pro_until").or(`role.eq.admin,pro_until.gt.${new Date().toISOString()}`).limit(100),
        admin.from("payment_orders").select("amount,paid_amount").eq("status", "paid"),
      ]);
      const ids = [...new Set((orders.data ?? []).map((o) => o.user_id))];
      const { data: owners } = ids.length ? await admin.from("studymind_accounts").select("id,email").in("id", ids) : { data: [] };
      const emailOf = Object.fromEntries((owners ?? []).map((p) => [p.id, p.email]));
      return json({
        success: true,
        data: {
          revenue: (paidAll.data ?? []).reduce((s, o) => s + Number(o.paid_amount ?? o.amount), 0),
          paidCount: (paidAll.data ?? []).length,
          orders: (orders.data ?? []).map((o) => ({ ...toOrder(o), userId: o.user_id, email: emailOf[o.user_id] ?? null })),
          transactions: txs.data ?? [],
          members: (pros.data ?? []).map((p) => ({ id: p.id, email: p.email, ...toAccess(p) })),
        },
      });
    }

    if (req.method === "POST" && path[1] === "members") {
      const email = String(body?.email ?? "").trim();
      const { data: target } = await admin.from("studymind_accounts").select("id,email").ilike("email", email).maybeSingle();
      if (!target) throw new HttpError(404, "Không tìm thấy tài khoản với email này.");
      const { error } = await admin.rpc("admin_set_pro", { p_user: target.id, p_days: Math.trunc(Number(body?.days) || 0) });
      if (error) throw new HttpError(500, error.message);
      const { data: updated } = await admin.from("studymind_accounts").select("*").eq("id", target.id).single();
      return json({ success: true, member: { id: target.id, email: target.email, ...toAccess(updated) } });
    }
  }

  throw new HttpError(404, "Không tìm thấy API.");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  // /studymind-billing/orders/abc → ["orders", "abc"]
  const parts = new URL(req.url).pathname.split("/").filter(Boolean);
  const i = parts.indexOf("studymind-billing");
  const path = i >= 0 ? parts.slice(i + 1) : parts;
  try {
    return await handle(req, path);
  } catch (e) {
    const err = e instanceof HttpError ? e : new HttpError(500, (e as Error)?.message ?? "Lỗi máy chủ");
    return json({ success: false, error: err.message, code: err.code }, err.status);
  }
});
