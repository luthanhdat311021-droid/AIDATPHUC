import express from 'express';
import { HttpError } from '../services/billingService.js';
import { registerRewardRoutes } from './rewardRoutes.js';
import { recordLessonActivity } from '../services/rewardsService.js';
import { bridgeSource, bridgeTarget, bridgeDb, bridgeChecked, bridgeCall, verifyEnvelope,
  identityFor, validIdentity, uuid, TOKEN_CHAIN, TOKEN_CONTRACT } from '../services/rewardsBridge.js';

const denied = () => new HttpError(403, 'Yêu cầu StudyCoin không hợp lệ.', 'BRIDGE_FORBIDDEN');
export function allowedRewardRoute(method, path) {
  if (method === 'GET') return /^\/(me|admin|sessions\/[0-9a-f-]{36})$/.test(path);
  return method === 'POST' && /^\/(redemptions|biometric\/(enroll|challenge)|sessions\/start|sessions\/[0-9a-f-]{36}\/(submit|reveal|review|heartbeat|finish)|missions\/[0-9a-f-]{36}\/claim|referrals\/bind|admin\/actions)$/.test(path);
}

// Registered BEFORE normal JWT middleware. HMAC never accepts a client-supplied user or role unsigned.
export function registerRewardsBridgeInternal(app) {
  const router = express.Router();
  registerRewardRoutes(router, (req, res, next) => req.access?.isAdmin ? next() : next(denied()));
  app.post('/api/internal/rewards/bridge/forward', async (req, res, next) => {
    if (!bridgeTarget()) throw denied();
    const e = verifyEnvelope(req.body, 'forward', process.env.REWARDS_BRIDGE_SECRET);
    const p = e.payload;
    if (!validIdentity(p.identity) || !['route', 'lesson'].includes(p.action)) throw denied();
    if (p.action === 'route' && !allowedRewardRoute(p.method, p.path)) throw denied();
    if (p.action === 'lesson' && (!['review', 'quiz'].includes(p.body?.kind)
        || typeof p.body.key !== 'string' || p.body.key.length > 500
        || typeof p.body.topic !== 'string' || p.body.topic.length > 1000
        || !(p.body.score == null || Number.isInteger(p.body.score) && p.body.score >= 0 && p.body.score <= 100))) throw denied();
    const db = bridgeDb();
    bridgeChecked(await db.rpc('studymind_bridge_accept_nonce', { p_nonce: e.nonce }));
    const uid = bridgeChecked(await db.rpc('studymind_bridge_identity', { p_source_user: p.identity.id,
      p_created: p.identity.createdAt, p_verified: p.identity.emailVerified, p_admin: p.identity.isAdmin }));
    if (p.action === 'lesson') {
      return res.json({ success: true, data: await recordLessonActivity(uid, p.body.kind, p.body.topic, p.body.key, p.body.score) });
    }
    req.user = { id: uid }; req.access = { isAdmin: p.identity.isAdmin };
    req.body = p.body || {}; req.method = p.method; req.url = '/api/v1/rewards' + p.path;
    router.handle(req, res, next);
  });
  app.post('/api/internal/rewards/bridge/grant', async (req, res) => {
    if (!bridgeSource()) throw denied();
    const { payload: p } = verifyEnvelope(req.body, 'grant', process.env.REWARDS_BRIDGE_SECRET);
    if (!uuid(p.job) || !uuid(p.user) || !Number.isInteger(p.days) || p.days < 1 || p.days > 365
        || Number(p.chain) !== TOKEN_CHAIN || p.contract !== TOKEN_CONTRACT
        || !/^0x[0-9a-f]{64}$/.test(p.tx) || !Number.isInteger(p.log) || p.log < 0) throw denied();
    const data = bridgeChecked(await bridgeDb().rpc('studymind_apply_bridge_grant', {
      p_job: p.job, p_user: p.user, p_days: p.days, p_chain: p.chain,
      p_contract: p.contract, p_tx: p.tx, p_log: p.log }));
    res.json({ success: true, data });
  });
}

// Registered AFTER production JWT/role verification and BEFORE the existing local reward routes.
export function registerRewardsBridgeProxy(app) {
  app.use('/api/v1/rewards', async (req, res, next) => {
    if (!bridgeSource()) return next();
    if (!allowedRewardRoute(req.method, req.path)) throw denied();
    if (req.path.startsWith('/admin') && !req.access?.isAdmin) throw denied();
    const data = await bridgeCall('forward', { action: 'route', method: req.method, path: req.path,
      identity: identityFor(req.user, req.access), body: req.method === 'GET' ? {} : req.body });
    res.json({ success: true, data });
  });
}

