import { bridgeSource, forwardLessonActivity } from './rewardsBridge.js';
import { createClient } from '@supabase/supabase-js';
import { HttpError } from './billingService.js';
import { NETWORKS, confirmedBalance, deriveManagedAddress, walletIdentity } from './rewardsChain.js';

const JOB_COLUMNS = 'id,operation_id,kind,amount,days,label,status,chain_id,token_address,wallet_address,tx_hash,block_number,log_index,error_code,created_at,confirmed_at';
const MESSAGES = {
  MISSION_EXPIRED: 'Nhiệm vụ đã hết hạn.', MISSION_NOT_READY: 'Bạn chưa hoàn thành điều kiện nhiệm vụ.',
  PRICE_CHANGED: 'Giá đổi đã thay đổi. Hãy tải lại và xác nhận giá mới.',
  BURN_PENDING: 'Bạn đang có một yêu cầu đổi Pro chờ blockchain xác nhận.',
  IDEMPOTENCY_CONFLICT: 'Yêu cầu gửi lại khác nội dung ban đầu.',
  SESSION_DAILY_LIMIT: 'Bạn đã dùng hết số phiên thử thách hôm nay.', SESSION_EXPIRED: 'Phiên học đã hết hạn.',
  STUDY_TOO_FAST: 'Hãy dành thêm thời gian đọc nội dung trước khi tiếp tục.',
  TOPIC_NOT_READY: 'Chủ đề chưa có đủ câu hỏi.', FOCUS_NOT_READY: 'Cần đủ 25 phút tập trung và ít nhất 2 hoạt động học.',
  REFERRAL_ALREADY_BOUND: 'Tài khoản đã gắn mã giới thiệu.', INVALID_REFERRAL: 'Mã giới thiệu không hợp lệ hoặc là mã của bạn.',
  REFERRAL_TOO_LATE: 'Chỉ nhập mã trong 7 ngày đầu, trước khi bắt đầu học nhận thưởng.',
  WALLET_NOT_READY: 'Ví token đang được khởi tạo.', CATALOG_NOT_FOUND: 'Phần thưởng không còn khả dụng.',
  INVALID_ACTION: 'Thao tác không hợp lệ.', INVALID_ANSWERS: 'Đáp án chưa hợp lệ.', INVALID_SESSION: 'Loại phiên học không hợp lệ.',
  ADMIN_REQUIRED: 'Chỉ quản trị viên được thực hiện.', INVALID_ADMIN_VALUE: 'Giá trị nằm ngoài giới hạn cho phép.',
  ADMIN_TARGET_NOT_FOUND: 'Không còn dữ liệu phù hợp để thay đổi.', DAILY_BUDGET_EXCEEDED: 'Tổng thưởng ngày không được vượt 45 SC.',
  REASON_REQUIRED: 'Hãy nhập lý do cụ thể.', RETRY_NOT_SAFE: 'Giao dịch chưa đủ điều kiện gửi lại an toàn.'
};

export function rewardsDb() {
  const url = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) throw new HttpError(503, 'Chức năng StudyCoin chưa được cấu hình.', 'REWARDS_DISABLED');
  return createClient(url, key, { auth: { persistSession: false } });
}

export function checked(result) {
  if (result.error) {
    const code = Object.keys(MESSAGES).find(k => result.error.message?.includes(k));
    if (code) throw new HttpError(409, MESSAGES[code], code);
    if (result.error.code === 'P0002') throw new HttpError(404, 'Không tìm thấy dữ liệu thuộc tài khoản này.', 'NOT_FOUND');
    if (result.error.code === '23505') throw new HttpError(409, 'Yêu cầu đang được xử lý. Hãy tải lại trạng thái.', 'ALREADY_PENDING');
    console.error('[rewards] database failure', result.error.code);
    throw new HttpError(503, 'Chưa xử lý được StudyCoin. Hãy thử lại sau.', 'REWARDS_UNAVAILABLE');
  }
  return result.data;
}

// Small per-instance caches for rows that change rarely. Prices stay safe: a redemption is still checked
// against the catalog version in the database, and a stale config only delays an admin switch by seconds.
const memo = new Map();
async function cached(key, ttlMs, load) {
  const hit = memo.get(key);
  if (hit && hit.until > Date.now()) return hit.value;
  const value = await load();
  memo.set(key, { value, until: Date.now() + ttlMs });
  return value;
}

export async function rewardConfig(db) {
  if (process.env.REWARDS_ENABLED !== 'true') throw new HttpError(503, 'StudyCoin chưa được bật trên máy chủ.', 'REWARDS_DISABLED');
  const c = await cached('config', 30_000,
    async () => checked(await db.from('studymind_reward_config').select('*').eq('id', true).single()));
  const xpub = process.env.REWARDS_WALLET_XPUB;
  if (!c.enabled || !c.token_address || !xpub || c.wallet_xpub_fingerprint !== walletIdentity(xpub))
    throw new HttpError(503, 'StudyCoin đang chờ cấu hình mạng và ví thử nghiệm.', 'REWARDS_DISABLED');
  return c;
}

export async function ensureRewardWallet(db, uid, config) {
  let w = checked(await db.from('studymind_reward_wallets').select('*').eq('user_id', uid).maybeSingle());
  if (!w) {
    checked(await db.from('studymind_reward_wallets').upsert({ user_id: uid, chain_id: config.chain_id,
      token_address: config.token_address.toLowerCase() }, { onConflict: 'user_id', ignoreDuplicates: true }));
    w = checked(await db.from('studymind_reward_wallets').select('*').eq('user_id', uid).single());
  }
  if (Number(w.chain_id) !== Number(config.chain_id) || w.token_address !== config.token_address.toLowerCase())
    throw new HttpError(503, 'Ví đã gắn với cấu hình mạng khác. Cần đối soát trước khi chuyển mạng.', 'WALLET_NETWORK_MISMATCH');
  const address = deriveManagedAddress(process.env.REWARDS_WALLET_XPUB, w.wallet_index);
  if (w.address && w.address !== address) throw new HttpError(503, 'Ví không khớp bản sao lưu cấu hình.', 'WALLET_MISMATCH');
  if (!w.address) {
    checked(await db.from('studymind_reward_wallets').update({ address }).eq('user_id', uid).is('address', null));
    w = checked(await db.from('studymind_reward_wallets').select('*').eq('user_id', uid).single());
  }
  return w;
}

export async function rewardContext(uid) {
  const db = rewardsDb(), config = await rewardConfig(db);
  const wallet = await ensureRewardWallet(db, uid, config);
  return { db, config, wallet };
}

export function publicSession(s) {
  const result = { id: s.id, kind: s.kind, topic: s.topic, startedAt: s.started_at, expiresAt: s.expires_at,
    finished: !!s.finished_at, result: s.result, seconds: s.focus_seconds, reviewed: s.review_index };
  if (s.finished_at) return result;
  if (s.kind === 'quiz') result.questions = s.questions.map(q => ({ id: q.id, question: q.question, options: q.options }));
  if (s.kind === 'review') {
    const q = s.questions[s.review_index];
    if (q) result.card = { id: q.id, front: q.question, back: s.revealed_at ? q.options[q.answer] : null,
      total: s.questions.length, number: s.review_index + 1 };
  }
  return result;
}

/** Lesson items the learner typed in (manual cards/questions) never count: their answers are self-chosen. */
export function isUserCreated(item) {
  return item?.userCreated === true || /^(fc|q)-\d{12,}$/.test(String(item?.id ?? ''));
}

/**
 * Best-effort mission evidence from ordinary lesson study. Returns true when it counted, false when it was a
 * repeat/too fast, null when rewards are off or unavailable. It never makes the study action itself fail.
 */
export async function recordLessonActivity(uid, kind, topic, key, score = null) {
  if (bridgeSource()) {
    try { return await forwardLessonActivity(uid, kind, topic || 'Bài học', key, score); }
    catch { console.warn('[rewards] bridge lesson evidence unavailable'); return null; }
  }
  if (process.env.REWARDS_ENABLED !== 'true') return null;
  try {
    const r = await rewardsDb().rpc('studymind_record_lesson_event',
      { p_user: uid, p_kind: kind, p_topic: topic || null, p_key: key, p_score: score });
    if (r.error) { console.error('[rewards] lesson evidence failed', r.error.code); return null; }
    return r.data === true;
  } catch { return null; }
}

export const rewardsService = {
  async me(uid) {
    if (process.env.REWARDS_ENABLED !== 'true') return { enabled: false };
    const db = rewardsDb(), config = await rewardConfig(db);
    // Everything below is independent, so it runs concurrently: the screen costs about one database
    // round trip plus the wallet → confirmed-balance chain lookup, instead of a dozen in a row.
    const walletP = ensureRewardWallet(db, uid, config);
    const balanceP = walletP.then(w => confirmedBalance(config, w.address))
      // Keep pending history visible; never substitute a fabricated balance for an RPC failure.
      .catch(() => ({ balance: null, balanceBlock: null }));
    const referralCodeP = (async () => {
      const found = checked(await db.from('studymind_referral_codes').select('code').eq('user_id', uid).maybeSingle());
      if (found) return found.code;
      checked(await db.from('studymind_referral_codes').upsert({ user_id: uid }, { onConflict: 'user_id', ignoreDuplicates: true }));
      return checked(await db.from('studymind_referral_codes').select('code').eq('user_id', uid).single()).code;
    })();
    const [wallet, { balance, balanceBlock }, jobs, active, missions, catalog, topics, referralCode, invited, bound] = await Promise.all([
      walletP, balanceP,
      db.from('studymind_reward_jobs').select(JOB_COLUMNS).eq('user_id', uid)
        .order('created_at', { ascending: false }).limit(100).then(checked),
      db.from('studymind_reward_jobs').select('kind,amount').eq('user_id', uid)
        .in('status', ['queued', 'prepared', 'submitted']).then(checked),
      db.rpc('studymind_missions', { p_user: uid }).then(checked),
      cached('catalog', 30_000, async () =>
        checked(await db.from('studymind_reward_catalog').select('*').eq('active', true).order('amount'))),
      cached('topics', 10 * 60_000, async () =>
        [...new Set(checked(await db.from('studymind_reward_questions').select('topic').eq('active', true)).map(t => t.topic))]),
      referralCodeP,
      // Do not disclose friends' UUIDs, email addresses, wallets, or detailed study history.
      db.from('studymind_referrals').select('id,status,reason,created_at,qualified_at')
        .eq('inviter', uid).order('created_at', { ascending: false }).limit(100).then(checked),
      db.from('studymind_referrals').select('status,reason').eq('invitee', uid).maybeSingle().then(checked)
    ]);
    const pendingMint = active.filter(j => j.kind === 'mint').reduce((n, j) => n + j.amount, 0);
    const pendingBurn = active.filter(j => j.kind === 'burn').reduce((n, j) => n + j.amount, 0);
    return { enabled: true, wallet: wallet.address, custody: 'managed', chainId: config.chain_id,
      contract: config.token_address, balance, balanceBlock, chainAvailable: balance !== null,
      available: balance === null ? null : Math.max(0, balance - pendingBurn), pendingMint, pendingBurn,
      missions, catalog, jobs, topics, referralCode, referrals: invited, bound,
      serverTime: new Date().toISOString(), network: NETWORKS[Number(config.chain_id)]?.name ?? null,
      explorer: NETWORKS[Number(config.chain_id)]?.explorer ?? null };
  },
  async claim(uid, id) {
    const { db } = await rewardContext(uid);
    const j = checked(await db.rpc('studymind_claim_mission', { p_user: uid, p_mission: id }));
    return { id: j.id, status: j.status };
  },
  async redeem(uid, body) {
    const { db, config, wallet } = await rewardContext(uid);
    const previous = checked(await db.from('studymind_reward_redemptions').select('id').eq('user_id', uid)
      .eq('idempotency_key', body.key).maybeSingle());
    if (!previous) {
      const c = checked(await db.from('studymind_reward_catalog').select('*').eq('id', body.catalogId).eq('active', true).maybeSingle());
      if (!c) throw new HttpError(404, 'Không có phần thưởng này.', 'CATALOG_NOT_FOUND');
      if (c.version !== body.version) throw new HttpError(409, MESSAGES.PRICE_CHANGED, 'PRICE_CHANGED');
      const { balance } = await confirmedBalance(config, wallet.address);
      if (balance < c.amount) throw new HttpError(409, 'Chưa đủ token đã xác nhận để đổi Pro.', 'INSUFFICIENT_TOKEN');
    }
    const j = checked(await db.rpc('studymind_queue_redemption', { p_user: uid, p_catalog: body.catalogId,
      p_version: body.version, p_key: body.key }));
    return { id: j.id, status: j.status };
  },
  async start(uid, kind, topic) {
    const { db } = await rewardContext(uid);
    return publicSession(checked(await db.rpc('studymind_start_reward_session', { p_user: uid, p_kind: kind, p_topic: topic || 'focus' })));
  },
  async session(uid, id) {
    const { db } = await rewardContext(uid);
    return publicSession(checked(await db.from('studymind_reward_sessions').select('*').eq('id', id).eq('user_id', uid).single()));
  },
  async step(uid, id, action, answers) {
    const { db } = await rewardContext(uid);
    return checked(await db.rpc('studymind_reward_session_step', { p_user: uid, p_session: id,
      p_action: action, p_answers: answers || null }));
  },
  async bind(uid, code) {
    const { db } = await rewardContext(uid);
    return checked(await db.rpc('studymind_bind_referral', { p_user: uid, p_code: code }));
  },
  async adminOverview() {
    const db = rewardsDb();
    const jobs = checked(await db.from('studymind_reward_jobs').select(JOB_COLUMNS).order('created_at', { ascending: false }).limit(100));
    const referrals = checked(await db.from('studymind_referrals').select('id,status,reason,created_at,qualified_at').in('status', ['learning','waiting','queued']).limit(100));
    return { jobs, referrals, totals: checked(await db.rpc('studymind_reward_totals')),
      rules: checked(await db.from('studymind_reward_rules').select('*').order('amount')),
      catalog: checked(await db.from('studymind_reward_catalog').select('*').order('amount')) };
  },
  async adminAction(uid, body) {
    const db = rewardsDb();
    const result = checked(await db.rpc('studymind_admin_reward_action', { p_actor: uid, p_action: body.action,
      p_id: body.id, p_value: body.value ?? null, p_reason: body.reason ?? null }));
    if (body.action === 'catalog_amount') memo.delete('catalog');
    return result;
  }
};
