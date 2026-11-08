import { HttpError } from '../services/billingService.js';
import { rewardsService } from '../services/rewardsService.js';
import { biometricService } from '../services/biometricService.js';

const uuid = v => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
function valid(ok) { if (!ok) throw new HttpError(400, 'Dữ liệu yêu cầu chưa hợp lệ.', 'INVALID_REQUEST'); }

// Registered after the shared Supabase JWT / role middleware.
export function registerRewardRoutes(app, requireAdmin) {
  const p = '/api/v1/rewards';
  const json = fn => async (req, res) => res.json({ success: true, data: await fn(req) });
  app.get(p + '/me', json(req => rewardsService.me(req.user.id)));
  app.post(p + '/missions/:id/claim', json(req => {
    valid(uuid(req.params.id)); return rewardsService.claim(req.user.id, req.params.id);
  }));
  app.post(p + '/redemptions', json(req => {
    valid(uuid(req.body.key) && Number.isSafeInteger(req.body.version) && req.body.version > 0
      && typeof req.body.catalogId === 'string' && req.body.catalogId.length <= 60);
    return biometricService.assertRedemption(req.user.id, req.body).then(() => rewardsService.redeem(req.user.id, req.body));
  }));
  app.post(p + '/biometric/enroll', json(req => {
    valid(typeof req.body.publicKey === 'string' && req.body.publicKey.length <= 500);
    return biometricService.enroll(req.user.id, req.body.publicKey);
  }));
  app.post(p + '/biometric/challenge', json(req => {
    valid(uuid(req.body.key) && Number.isSafeInteger(req.body.version) && req.body.version > 0
      && typeof req.body.catalogId === 'string' && req.body.catalogId.length <= 60);
    return biometricService.challenge(req.user.id, req.body);
  }));
  app.post(p + '/sessions/start', json(req => {
    valid(['quiz','review','focus'].includes(req.body.kind)
      && (req.body.kind === 'focus' || (typeof req.body.topic === 'string' && req.body.topic.length <= 100)));
    return rewardsService.start(req.user.id, req.body.kind, req.body.topic);
  }));
  app.get(p + '/sessions/:id', json(req => {
    valid(uuid(req.params.id)); return rewardsService.session(req.user.id, req.params.id);
  }));
  app.post(p + '/sessions/:id/:action', json(req => {
    valid(uuid(req.params.id) && ['submit','reveal','review','heartbeat','finish'].includes(req.params.action));
    if (req.params.action === 'submit') valid(req.body.answers && !Array.isArray(req.body.answers)
      && Object.keys(req.body.answers).length <= 10 && Object.entries(req.body.answers).every(([k,v]) => uuid(k) && Number.isInteger(v) && v >= 0 && v <= 3));
    return rewardsService.step(req.user.id, req.params.id, req.params.action, req.body.answers);
  }));
  app.post(p + '/referrals/bind', json(req => {
    valid(typeof req.body.code === 'string' && /^[A-Z0-9]{12}$/i.test(req.body.code));
    return rewardsService.bind(req.user.id, req.body.code);
  }));
  app.get(p + '/admin', requireAdmin, json(() => rewardsService.adminOverview()));
  app.post(p + '/admin/actions', requireAdmin, json(req => {
    valid(['rule_amount','catalog_amount','reject_referral','retry_job'].includes(req.body.action)
      && typeof req.body.id === 'string' && req.body.id.length <= 60
      && (req.body.value == null || Number.isSafeInteger(req.body.value))
      && (req.body.reason == null || (typeof req.body.reason === 'string' && req.body.reason.length <= 300)));
    if (['reject_referral','retry_job'].includes(req.body.action)) valid(uuid(req.body.id));
    return rewardsService.adminAction(req.user.id, req.body);
  }));
}
