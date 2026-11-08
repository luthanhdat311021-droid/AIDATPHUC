// SePay bank-transfer webhook on Supabase Edge Functions.
// Deploy with JWT verification OFF (SePay has no Supabase token); it authenticates with
// "Authorization: Apikey <SEPAY_WEBHOOK_API_KEY>" instead (set the key in Edge Function secrets).
// Answers 200 only once the transfer is recorded, so SePay retries anything else.
import { createClient } from "jsr:@supabase/supabase-js@2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const CODE_RE = /SM[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}/;

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/** Constant-time comparison of the API key */
function verify(header: string | null): boolean {
  const expected = Deno.env.get("SEPAY_WEBHOOK_API_KEY");
  if (!expected) return false;
  const given = (header ?? "").replace(/^Apikey\s+/i, "").trim();
  if (given.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < given.length; i++) diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ success: false, error: "Method not allowed" }, 405);
  if (!verify(req.headers.get("Authorization"))) return json({ success: false, error: "Unauthorized" }, 401);

  // deno-lint-ignore no-explicit-any
  const tx: any = await req.json().catch(() => null);
  const sepayId = Number(tx?.id);
  if (!Number.isFinite(sepayId)) return json({ success: false, error: "Thiếu id giao dịch." }, 400);

  const amount = Math.round(Number(tx.transferAmount) || 0);
  // SePay fills `code` when payment-code detection is configured; otherwise find it in the transfer content
  const code = (String(tx.code ?? "").toUpperCase().match(CODE_RE)
    ?? String(tx.content ?? "").toUpperCase().replace(/[\s.-]/g, "").match(CODE_RE) ?? [])[0];

  let result = "ignored";
  let orderId: string | null = null;
  if (tx.transferType === "in" && code && amount > 0) {
    const { data: order } = await admin.from("payment_orders").select("id").eq("code", code).maybeSingle();
    orderId = order?.id ?? null;
    // Locks the order, checks the amount and extends Pro atomically; idempotent if redelivered
    const { data, error } = await admin.rpc("confirm_payment", { p_code: code, p_amount: amount, p_sepay_id: sepayId });
    if (error) return json({ success: false, error: error.message }, 500);
    result = data;
  } else if (tx.transferType === "in") {
    result = "no_order";
  }

  // Primary key on sepay_id: a redelivered webhook is logged once
  const { error } = await admin.from("payment_transactions").insert({
    sepay_id: sepayId, order_id: orderId, amount, content: tx.content ?? null, gateway: tx.gateway ?? null,
    reference_code: tx.referenceCode ?? null, transaction_date: tx.transactionDate ?? null, result, raw: tx,
  });
  if (error && error.code !== "23505") console.error("[sepay] log failed:", error.message);

  console.log(`[sepay] tx ${sepayId} ${amount}đ code=${code ?? "-"} → ${result}`);
  return json({ success: true, result });
});
