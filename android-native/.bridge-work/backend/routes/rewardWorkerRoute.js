import { timingSafeEqual } from 'node:crypto';
import { runRewardsWorker } from '../services/rewardsWorker.js';

// Server-to-server scheduler route. Registered BEFORE user JWT middleware.
export function registerRewardWorkerRoute(app) {
  app.post('/api/internal/rewards/run', async (req, res) => {
    const expected = process.env.REWARDS_WORKER_SECRET || '', supplied = req.get('x-rewards-worker-secret') || '';
    const a = Buffer.from(supplied), b = Buffer.from(expected);
    if (b.length < 32 || a.length !== b.length || !timingSafeEqual(a, b))
      return res.status(401).json({ success: false, error: 'Unauthorized' });
    try { res.json({ success: true, data: await runRewardsWorker() }); }
    catch (e) {
      // Only a code reaches logs/the authenticated scheduler: ethers error messages can echo the key material.
      const code = (typeof e?.code === 'string' && /^[A-Z_]{3,40}$/.test(e.code)) ? e.code
        : (typeof e?.message === 'string' && /^[A-Z_]{3,40}$/.test(e.message)) ? e.message : 'WORKER_NOT_READY';
      console.error('[rewards-worker] not ready', code);
      res.status(503).json({ success: false, code, error: 'Worker chưa sẵn sàng. Kiểm tra cấu hình và log máy chủ.' });
    }
  });
}
