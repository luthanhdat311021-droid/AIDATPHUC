import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import ganache from 'ganache';
import solc from 'solc';
import { BrowserProvider, ContractFactory, id } from 'ethers';
import { compile, root, standardInput } from '../scripts/compile.mjs';
import { adminCommand } from '../scripts/admin.mjs';
import { chainHealth, queueHealth } from '../scripts/status.mjs';

test('verification input is self-contained and reproduces the deployed bytecode', () => {
  const a = compile(), input = standardInput(a);
  assert.ok(Object.keys(input.sources).some(f => f.startsWith('@openzeppelin/')));
  // No import callback: an explorer must be able to compile it from this file alone.
  const out = JSON.parse(solc.compile(JSON.stringify(input)));
  assert.deepEqual((out.errors || []).filter(e => e.severity === 'error'), []);
  assert.equal('0x' + out.contracts['StudyCoin.sol'].StudyCoin.evm.deployedBytecode.object, a.runtimeBytecode);
});

test('real EVM: per-mint cap, admin commands, operator rotation, 2-day admin transfer and health alerts', async () => {
  let engine;
  const dbPath = fs.mkdtempSync(path.join(root, '.evm-test-'));
  try {
    engine = ganache.provider({ database: { dbPath }, wallet: { deterministic: true }, logging: { quiet: true }, chain: { chainId: 31337, hardfork: 'shanghai' } });
    const p = new BrowserProvider(engine, undefined, { cacheTimeout: -1 });
    const [admin, operator, user, operator2, admin2] = await Promise.all([0, 1, 2, 3, 4].map(i => p.getSigner(i)));
    const a = compile();
    const token = await new ContractFactory(a.abi, a.bytecode, admin).deploy(admin.address, operator.address);
    await token.waitForDeployment();
    const rejects = fn => assert.rejects(async () => { await (await fn()).wait(); });
    const w = user.address;

    await rejects(() => token.connect(operator).reward(id('over-cap'), w, 1001));
    await (await token.connect(operator).reward(id('ok'), w, 50)).wait();
    await rejects(() => token.connect(user).transferFrom(w, admin.address, 1));
    await rejects(() => token.connect(operator).pause());

    let health = await chainHealth(p, token, { operator: operator.address, runtimeBytecode: a.runtimeBytecode });
    assert.deepEqual(health.alerts, []);
    assert.equal(health.operator.hasRole, true);
    assert.equal(health.lifetimeMinted, 50);

    // Dry run sends nothing; non-admins are refused before any transaction.
    const dry = await adminCommand(token, admin, 'pause');
    assert.equal(dry.executed, false);
    assert.equal(await token.paused(), false);
    await assert.rejects(adminCommand(token, operator, 'pause', null, { execute: true }), /not the current contract admin/);
    await adminCommand(token, admin, 'pause', null, { execute: true });
    assert.ok((await chainHealth(p, token)).alerts.includes('CONTRACT_PAUSED'));
    await assert.rejects(adminCommand(token, admin, 'pause', null, { execute: true }), /already paused/);
    await adminCommand(token, admin, 'unpause', null, { execute: true });

    // Rotate a compromised operator: old key loses minting immediately, new key works.
    await assert.rejects(adminCommand(token, admin, 'grant-operator', admin.address, { execute: true }), /separate/);
    await adminCommand(token, admin, 'revoke-operator', operator.address, { execute: true });
    await rejects(() => token.connect(operator).reward(id('revoked'), w, 5));
    assert.ok((await chainHealth(p, token, { operator: operator.address })).alerts.includes('OPERATOR_ROLE_MISSING'));
    await adminCommand(token, admin, 'grant-operator', operator2.address, { execute: true });
    await (await token.connect(operator2).reward(id('rotated'), w, 5)).wait();
    assert.equal(await token.balanceOf(w), 55n);

    // Admin hand-over must wait the 2-day delay and is visible to monitoring meanwhile.
    await assert.rejects(adminCommand(token, admin, 'begin-admin-transfer', operator2.address, { execute: true }), /must not be an operator/);
    await adminCommand(token, admin, 'begin-admin-transfer', admin2.address, { execute: true });
    assert.ok((await chainHealth(p, token)).alerts.includes('ADMIN_TRANSFER_PENDING'));
    await assert.rejects(adminCommand(token, admin2, 'accept-admin', null, { execute: true }), /delay has not passed/);
    await p.send('evm_increaseTime', [2 * 86400 + 1]);
    await p.send('evm_mine', []);
    await assert.rejects(adminCommand(token, user, 'accept-admin', null, { execute: true }), /not the pending admin/);
    await adminCommand(token, admin2, 'accept-admin', null, { execute: true });
    assert.equal(await token.defaultAdmin(), admin2.address);
    await assert.rejects(adminCommand(token, admin, 'pause', null, { execute: true }), /not the current contract admin/);
    health = await chainHealth(p, token, { operator: operator2.address });
    assert.deepEqual(health.alerts, []);
    assert.equal(health.admin, admin2.address);
  } finally {
    if (engine) await engine.disconnect();
    if (path.resolve(dbPath).startsWith(path.resolve(root) + path.sep + '.evm-test-')) fs.rmSync(dbPath, { recursive: true, force: true });
  }
});

test('queue health flags stuck and failed jobs without reading transaction bytes', async () => {
  let selected;
  const rows = [
    { status: 'submitted', created_at: '2026-10-18T00:00:00Z', error_code: 'WORKER_RETRY' },
    { status: 'queued', created_at: '2026-10-18T00:20:00Z', error_code: null },
    { status: 'failed', created_at: '2026-10-18T00:25:00Z', error_code: 'TRANSACTION_REVERTED' }];
  const query = { in: () => query, order: () => query, limit: async () => ({ data: rows, error: null }) };
  const db = { from: () => ({ select: cols => { selected = cols; return query; } }) };
  const report = await queueHealth(db, new Date('2026-10-18T00:30:00Z').getTime());
  assert.ok(!selected.includes('raw_transaction'));
  assert.deepEqual(report.counts, { queued: 1, prepared: 0, submitted: 1, failed: 1 });
  assert.equal(report.oldestPendingMinutes, 30);
  assert.deepEqual(report.alerts, ['JOBS_STUCK', 'JOBS_FAILED']);
  rows.splice(0, 1); rows.splice(1, 1);
  assert.deepEqual((await queueHealth(db, new Date('2026-10-18T00:30:00Z').getTime())).alerts, []);
});
