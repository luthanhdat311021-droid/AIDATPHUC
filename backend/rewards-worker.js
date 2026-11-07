import 'dotenv/config';
import { runRewardsWorker } from './services/rewardsWorker.js';

// Alternative to the HTTP scheduler: run once per minute on a trusted server.
try { console.log(JSON.stringify(await runRewardsWorker())); }
catch { console.error('StudyCoin worker failed. Check configuration and the server audit log.'); process.exitCode = 1; }
