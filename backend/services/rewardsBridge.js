import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { createClient } from '@supabase/supabase-js';
import { HttpError } from './billingService.js';

export const PRIMARY_PROJECT = 'gwynjmlqymojrdlpzukn';
export const TOKEN_PROJECT = 'urnlxgyqmvcbkonysizy';
export const TOKEN_CHAIN = 11155111;
export const TOKEN_CONTRACT = '0xa66a22aa9d891d2611e47cfe53d90ceb1e2b375d';
export const uuid = v => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
const unavailable = () => new HttpError(503, 'Kết nối StudyCoin đang gián đoạn. Hãy thử lại sau.', 'REWARDS_BRIDGE_UNAVAILABLE');
export function localProject() {
  try { return new URL(process.env.SUPABASE_URL).hostname.split('.')[0]; } catch { return ''; }
}
export const bridgeSource = () => process.env.REWARDS_BRIDGE_MODE === 'source' && localProject() === PRIMARY_PROJECT;
export const bridgeTarget = () => process.env.REWARDS_BRIDGE_MODE === 'target' && localProject() === TOKEN_PROJECT;
export function bridgeDb() {
  if (!process.env.SUPABASE_URL || !process.env.SUPABASE_SERVICE_ROLE_KEY) throw unavailable();
  return createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false } });
}
export function bridgeChecked(r) { if (r.error) throw unavailable(); return r.data; }

export function signEnvelope(purpose, payload, secret, now = Date.now()) {
  if (typeof secret !== 'string' || secret.length < 32) throw unavailable();
  const message = JSON.stringify({ v: 1, purpose, at: now, nonce: randomUUID(), payload });
  return { message, signature: createHmac('sha256', secret).update(message).digest('hex') };
}
export function verifyEnvelope(body, purpose, secret, now = Date.now()) {
  const deny = () => { throw new HttpError(401, 'Unauthorized', 'BRIDGE_UNAUTHORIZED'); };
  if (typeof secret !== 'string' || secret.length < 32 || typeof body?.message !== 'string'
      || body.message.length > 100_000 || !/^[0-9a-f]{64}$/.test(body?.signature ?? '')) return deny();
  const expected = createHmac('sha256', secret).update(body.message).digest();
  if (!timingSafeEqual(expected, Buffer.from(body.signature, 'hex'))) return deny();
  let e; try { e = JSON.parse(body.message); } catch { return deny(); }
  if (e.v !== 1 || e.purpose !== purpose || !uuid(e.nonce) || !Number.isSafeInteger(e.at)
      || Math.abs(now - e.at) > 120_000 || !e.payload) return deny();
  return e;
}

export async function bridgeCall(purpose, payload, fetcher = fetch) {
  const project = purpose === 'forward' ? TOKEN_PROJECT : PRIMARY_PROJECT;
  const envelope = signEnvelope(purpose, payload, process.env.REWARDS_BRIDGE_SECRET);
  let r;
  try {
    r = await fetcher(`https://${project}.supabase.co/functions/v1/studymind-api/api/internal/rewards/bridge/${purpose}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(envelope),
      signal: AbortSignal.timeout(15_000), redirect: 'error'
    });
  } catch { throw unavailable(); }
  let json; try { json = await r.json(); } catch { throw unavailable(); }
  if (!r.ok || json?.success !== true) {
    // Only known application errors are surfaced; never echo HTML, SQL, RPC details or credentials.
    const safe = r.status >= 400 && r.status < 500 && typeof json.code === 'string' && /^[A-Z_]{3,50}$/.test(json.code);
    throw new HttpError(safe ? r.status : 503, safe ? String(json.error).slice(0, 300) : unavailable().message,
      safe ? json.code : 'REWARDS_BRIDGE_UNAVAILABLE');
  }
  return json.data;
}

export const identityFor = (user, access) => ({ id: user.id, createdAt: user.created_at,
  emailVerified: !!user.email_confirmed_at, isAdmin: access?.isAdmin === true });
export function validIdentity(i) {
  return uuid(i?.id) && typeof i.createdAt === 'string' && Number.isFinite(Date.parse(i.createdAt))
    && Date.parse(i.createdAt) <= Date.now() + 60_000 && typeof i.emailVerified === 'boolean' && typeof i.isAdmin === 'boolean';
}

export async function forwardLessonActivity(uid, kind, topic, key, score) {
  const { data, error } = await bridgeDb().auth.admin.getUserById(uid);
  if (error || !data?.user) throw unavailable();
  return bridgeCall('forward', { identity: identityFor(data.user), action: 'lesson',
    body: { kind, topic, key, score } });
}

export async function syncBridgeGrants() {
  if (!bridgeTarget()) return { delivered: 0, pending: 0 };
  const db = bridgeDb();
  const grants = bridgeChecked(await db.from('studymind_bridge_grants').select('*')
    .is('delivered_at', null).order('created_at').limit(5));
  let delivered = 0;
  const deadline = Date.now() + 15_000;
  // An interrupted/lost acknowledgement retries the same immutable job/event; primary SQL is idempotent.
  for (const g of grants) {
    if (Date.now() >= deadline) break;
    try {
      await bridgeCall('grant', { job: g.job_id, user: g.source_user_id, days: g.days,
        chain: g.chain_id, contract: g.token_address, tx: g.tx_hash, log: g.log_index });
      bridgeChecked(await db.from('studymind_bridge_grants').update({ delivered_at: new Date().toISOString() }).eq('job_id', g.job_id));
      delivered++;
    } catch { break; }
  }
  return { delivered, pending: grants.length - delivered };
}
