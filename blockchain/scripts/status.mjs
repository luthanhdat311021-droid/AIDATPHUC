import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Contract, JsonRpcProvider, formatEther, getAddress, keccak256 } from 'ethers';
import { compile } from './compile.mjs';
import { NETWORKS } from '../../backend/services/rewardsChain.js';

// Read-only health check for staging monitoring. Needs no private key; exits 2 when an alert is raised
// so a scheduler/uptime tool can notify operations.
export const LOW_GAS_WEI = 10n ** 17n; // fallback for an unknown network; see NETWORKS[].lowGasWei
// Longer than Sepolia's ~13-minute confirmation depth so a normal pending job is not reported as stuck.
export const STUCK_MINUTES = 20;

export async function chainHealth(provider, token, { operator, runtimeBytecode } = {}) {
  const address = await token.getAddress(), alerts = [];
  const [network, paused, supply, minted, cap, admin, pendingAdmin, operatorRole, code] = await Promise.all([
    provider.getNetwork(), token.paused(), token.totalSupply(), token.lifetimeMinted(), token.LIFETIME_CAP(),
    token.defaultAdmin(), token.pendingDefaultAdmin(), token.OPERATOR_ROLE(), provider.getCode(address)]);
  if (runtimeBytecode && keccak256(code) !== keccak256(runtimeBytecode)) alerts.push('BYTECODE_MISMATCH');
  if (paused) alerts.push('CONTRACT_PAUSED');
  if ((cap - minted) * 100n < cap) alerts.push('CAP_NEARLY_REACHED');
  const pending = pendingAdmin.newAdmin === '0x0000000000000000000000000000000000000000' ? null
    : { newAdmin: pendingAdmin.newAdmin, acceptableAfter: new Date(Number(pendingAdmin.schedule) * 1000).toISOString() };
  if (pending) alerts.push('ADMIN_TRANSFER_PENDING');
  const result = { chainId: Number(network.chainId), contract: address, paused, admin, pendingAdmin: pending,
    totalSupply: Number(supply), lifetimeMinted: Number(minted), lifetimeCap: Number(cap) };
  if (operator) {
    const wallet = getAddress(operator);
    const [hasRole, balance, latest, inMempool] = await Promise.all([token.hasRole(operatorRole, wallet),
      provider.getBalance(wallet), provider.getTransactionCount(wallet, 'latest'), provider.getTransactionCount(wallet, 'pending')]);
    if (!hasRole) alerts.push('OPERATOR_ROLE_MISSING');
    if (balance < (NETWORKS[Number(network.chainId)]?.lowGasWei ?? LOW_GAS_WEI)) alerts.push('OPERATOR_LOW_GAS');
    result.operator = { address: wallet, hasRole, gasBalance: formatEther(balance), nonce: latest, unminedTransactions: inMempool - latest };
  }
  return { ...result, alerts };
}

/** Queue health from Postgres. Uses only status/timestamps; never selects raw transactions. */
export async function queueHealth(db, now = Date.now()) {
  const r = await db.from('studymind_reward_jobs').select('status,created_at,error_code')
    .in('status', ['queued', 'prepared', 'submitted', 'failed']).order('created_at').limit(1000);
  if (r.error) throw new Error('Cannot read reward jobs.');
  const counts = { queued: 0, prepared: 0, submitted: 0, failed: 0 }, errors = {}, alerts = [];
  let oldestPending = null;
  for (const job of r.data) {
    counts[job.status]++;
    if (job.error_code) errors[job.error_code] = (errors[job.error_code] || 0) + 1;
    if (job.status !== 'failed' && !oldestPending) oldestPending = job.created_at;
  }
  const ageMinutes = oldestPending ? Math.floor((now - new Date(oldestPending).getTime()) / 60000) : 0;
  if (ageMinutes >= STUCK_MINUTES) alerts.push('JOBS_STUCK');
  if (counts.failed) alerts.push('JOBS_FAILED');
  return { counts, errors, oldestPendingMinutes: ageMinutes, alerts };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await import('dotenv/config');
  const { REWARDS_RPC_URL, REWARDS_TOKEN_ADDRESS, REWARDS_OPERATOR_ADDRESS, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY } = process.env;
  if (!REWARDS_RPC_URL || !REWARDS_TOKEN_ADDRESS) throw new Error('Set REWARDS_RPC_URL and REWARDS_TOKEN_ADDRESS.');
  const provider = new JsonRpcProvider(REWARDS_RPC_URL);
  try {
    if (!NETWORKS[Number((await provider.getNetwork()).chainId)]) throw new Error('Only Amoy (80002), Sepolia (11155111) or the local chain (31337) is permitted.');
    const compiled = compile();
    const report = { chain: await chainHealth(provider, new Contract(getAddress(REWARDS_TOKEN_ADDRESS), compiled.abi, provider),
      { operator: REWARDS_OPERATOR_ADDRESS, runtimeBytecode: compiled.runtimeBytecode }) };
    if (SUPABASE_URL && SUPABASE_SERVICE_ROLE_KEY) {
      const { createClient } = await import('@supabase/supabase-js');
      report.queue = await queueHealth(createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false } }));
    }
    report.alerts = [...report.chain.alerts, ...(report.queue?.alerts || [])];
    console.log(JSON.stringify(report, null, 2));
    if (report.alerts.length) process.exitCode = 2;
  } finally { provider.destroy(); }
}
