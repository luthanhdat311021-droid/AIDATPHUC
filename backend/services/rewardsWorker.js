import { randomUUID } from 'node:crypto';
import { Wallet, Transaction } from 'ethers';
import { checked, rewardsDb, rewardConfig, ensureRewardWallet } from './rewardsService.js';
import { chainClient, confirmationCount, validateOperation } from './rewardsChain.js';

/** Verify the canonical receipt and *all* event fields before any entitlement mutation. */
export async function verifiedReceipt(provider, token, config, job) {
  if (!job.tx_hash) return null;
  const receipt = await provider.getTransactionReceipt(job.tx_hash);
  if (!receipt) return null;
  const latest = await provider.getBlockNumber();
  if (latest - receipt.blockNumber + 1 < confirmationCount(config.chain_id)) return null;
  const block = await provider.getBlock(receipt.blockNumber);
  if (!block || block.hash !== receipt.blockHash) return null;
  if (receipt.status !== 1) return { reverted: true };
  if (receipt.to?.toLowerCase() !== config.token_address.toLowerCase()) throw new Error('RECEIPT_TARGET_MISMATCH');
  for (const log of receipt.logs) {
    if (log.address.toLowerCase() !== config.token_address.toLowerCase()) continue;
    let parsed;
    try { parsed = token.interface.parseLog(log); } catch { continue; }
    if (parsed?.name === 'RewardOperation' && validateOperation(job, parsed.args))
      return { receipt, index: log.index };
  }
  throw new Error('RECEIPT_EVENT_MISMATCH');
}

async function recoverOperationHash(db, provider, token, job, deadline) {
  const latest = await provider.getBlockNumber();
  const origin = Number(job.search_from_block ?? Math.max(0, latest - 2048));
  let start = Math.max(origin, Number(job.search_cursor ?? origin) - 64);
  while (start <= latest && Date.now() < deadline) {
    const end = Math.min(latest, start + 1999);
    const logs = await token.queryFilter(token.filters.RewardOperation(job.operation_id), start, end);
    const match = logs.find(l => validateOperation(job, l.args));
    if (match) return match.transactionHash;
    checked(await db.from('studymind_reward_jobs').update({ search_cursor: end + 1 }).eq('id', job.id));
    start = end + 1;
  }
  return null;
}

export async function runRewardsWorker({ database, configuration, connectChain = chainClient } = {}) {
  const db = database || rewardsDb(), config = configuration || await rewardConfig(db), owner = randomUUID();
  const acquired = checked(await db.rpc('studymind_reward_worker_lease', { p_owner: owner, p_release: false }));
  if (!acquired) return { busy: true, processed: 0 };
  let provider;
  const summary = { processed: 0, submitted: 0, confirmed: 0, failed: 0, pending: 0 };
  const deadline = Date.now() + 45000;
  try {
    if (!process.env.REWARDS_OPERATOR_KEY) throw new Error('OPERATOR_NOT_CONFIGURED');
    const chain = await connectChain(config);
    provider = chain.provider;
    const signer = new Wallet(process.env.REWARDS_OPERATOR_KEY, provider), token = chain.token.connect(signer);
    checked(await db.rpc('studymind_process_referrals'));
    const jobs = checked(await db.from('studymind_reward_jobs').select('*')
      .in('status', ['queued','prepared','submitted']).order('created_at').limit(20));
    for (let job of jobs) {
      if (Date.now() > deadline) break;
      // Renew between jobs. A signer is never shared by overlapping workers.
      if (!checked(await db.rpc('studymind_reward_worker_lease', { p_owner: owner, p_release: false }))) break;
      try {
        if (Number(job.chain_id) !== Number(config.chain_id) || job.token_address !== config.token_address.toLowerCase())
          throw new Error('JOB_NETWORK_MISMATCH');
        const wallet = await ensureRewardWallet(db, job.user_id, config);
        if (wallet.address !== job.wallet_address) throw new Error('JOB_WALLET_MISMATCH');
        // Recover the canonical tx after a replacement/reorg or a lost receipt record, for every state.
        const existing = await token.operations(job.operation_id);
        if (Number(existing.kind) !== 0) {
          if (!validateOperation(job, { operationId: job.operation_id, wallet: existing.wallet,
            amount: existing.amount, daysGranted: existing.daysGranted, kind: existing.kind })) throw new Error('OPERATION_STATE_MISMATCH');
          const known = job.tx_hash ? await provider.getTransactionReceipt(job.tx_hash) : null;
          if (!known || known.status !== 1 || job.status !== 'submitted') {
            const hash = known?.status === 1 ? job.tx_hash : await recoverOperationHash(db, provider, token, job, deadline);
            if (!hash) { summary.pending++; break; }
            job = checked(await db.from('studymind_reward_jobs').update({ status: 'submitted', tx_hash: hash })
              .eq('id', job.id).select('*').single());
          }
        } else if (job.status === 'queued') {
            const data = token.interface.encodeFunctionData(job.kind === 'mint' ? 'reward' : 'redeem',
              job.kind === 'mint' ? [job.operation_id, job.wallet_address, job.amount]
                : [job.operation_id, job.wallet_address, job.amount, job.days]);
            let tx;
            if (!checked(await db.rpc('studymind_reward_worker_lease', { p_owner: owner, p_release: false })))
              throw new Error('WORKER_LEASE_LOST');
            try { tx = await signer.populateTransaction({ to: config.token_address, data }); }
            catch (e) {
              // A contract revert cannot have consumed tokens; RPC failures remain retryable.
              if (e.code !== 'CALL_EXCEPTION') throw e;
              checked(await db.from('studymind_reward_jobs').update({ status: 'failed', error_code: 'CONTRACT_REJECTED' }).eq('id', job.id));
              summary.failed++; continue;
            }
            const raw = await signer.signTransaction(tx), hash = Transaction.from(raw).hash;
            // Persist the signed bytes and nonce BEFORE broadcasting. A timeout can only resend identical bytes.
            job = checked(await db.from('studymind_reward_jobs').update({ status: 'prepared', tx_hash: hash,
              nonce: tx.nonce, raw_transaction: raw, attempts: job.attempts + 1, error_code: null,
              search_from_block: job.search_from_block ?? await provider.getBlockNumber() })
              .eq('id', job.id).eq('status', 'queued').select('*').single());
        }
        if (job.status === 'prepared') {
          if (!checked(await db.rpc('studymind_reward_worker_lease', { p_owner: owner, p_release: false })))
            throw new Error('WORKER_LEASE_LOST');
          const parsed = Transaction.from(job.raw_transaction);
          if (parsed.hash !== job.tx_hash || parsed.from?.toLowerCase() !== signer.address.toLowerCase()
              || parsed.chainId !== BigInt(config.chain_id) || parsed.to?.toLowerCase() !== config.token_address.toLowerCase())
            throw new Error('SIGNED_TRANSACTION_MISMATCH');
          const expected = token.interface.encodeFunctionData(job.kind === 'mint' ? 'reward' : 'redeem',
            job.kind === 'mint' ? [job.operation_id,job.wallet_address,job.amount]
              : [job.operation_id,job.wallet_address,job.amount,job.days]);
          if (parsed.data !== expected) throw new Error('SIGNED_PAYLOAD_MISMATCH');
          let broadcast = false;
          try { await provider.broadcastTransaction(job.raw_transaction); broadcast = true; }
          catch { broadcast = !!(await provider.getTransaction(job.tx_hash)); }
          if (broadcast) {
            job = checked(await db.from('studymind_reward_jobs').update({ status: 'submitted', error_code: null })
              .eq('id', job.id).select('*').single());
            summary.submitted++;
          } else {
            // Do not allocate this nonce to a new job until these exact bytes are known to the node.
            summary.pending++; break;
          }
        }
        // A dropped/reorged submission is rebroadcast from the saved bytes before allocating later nonces.
        if (job.status === 'submitted' && job.raw_transaction && !(await provider.getTransactionReceipt(job.tx_hash))) {
          if (!(await provider.getTransaction(job.tx_hash))) {
            try { await provider.broadcastTransaction(job.raw_transaction); }
            catch {
              // If this nonce was consumed elsewhere, a new tx may reuse the immutable operation ID.
              // A late old tx still cannot mint/burn twice because the contract enforces that ID.
              const operation = await token.operations(job.operation_id);
              if (Number(operation.kind) === 0 && await provider.getTransactionCount(signer.address,'latest') > Number(job.nonce)) {
                checked(await db.from('studymind_reward_jobs').update({ status: 'queued', tx_hash: null,
                  raw_transaction: null, nonce: null, error_code: 'REORG_RETRY' }).eq('id',job.id));
              }
              summary.pending++; break;
            }
          }
        }
        const proof = await verifiedReceipt(provider, token, config, job);
        if (proof?.reverted) {
          checked(await db.from('studymind_reward_jobs').update({ status: 'failed', raw_transaction: null,
            error_code: 'TRANSACTION_REVERTED' }).eq('id', job.id));
          summary.failed++;
        } else if (proof) {
          checked(await db.rpc('studymind_settle_token_job', { p_job: job.id, p_tx: proof.receipt.hash,
            p_block: proof.receipt.blockNumber, p_block_hash: proof.receipt.blockHash, p_log: proof.index }));
          summary.confirmed++;
        } else summary.pending++;
        summary.processed++;
      } catch (e) {
        // Never expose raw RPC errors, transaction bytes or signing material in API responses/logs.
        console.error('[rewards-worker] retry needed', job.id, e.code || 'WORKER_RETRY');
        checked(await db.from('studymind_reward_jobs').update({ error_code: 'WORKER_RETRY' }).eq('id', job.id));
        // Unknown submission state must not permit another nonce allocation.
        break;
      }
    }
    return summary;
  } finally {
    provider?.destroy();
    checked(await db.rpc('studymind_reward_worker_lease', { p_owner: owner, p_release: true }));
  }
}
