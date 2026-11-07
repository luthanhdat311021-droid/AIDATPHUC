import { test } from 'node:test';
import assert from 'node:assert/strict';
import ganache from 'ganache';
import { BrowserProvider, ContractFactory, id } from 'ethers';
import { compile, root } from '../scripts/compile.mjs';
import fs from 'node:fs';
import path from 'node:path';
import { verifiedReceipt } from '../../backend/services/rewardsWorker.js';

test('real EVM: mint/burn, replay protection, roles, pause and non-transferability', async () => {
  let engine;
  const dbPath=fs.mkdtempSync(path.join(root,'.evm-test-'));
  try {
    engine = ganache.provider({ database: { dbPath }, wallet: { deterministic: true }, logging: { quiet: true }, chain: { chainId:31337, hardfork: 'shanghai' } });
    const p = new BrowserProvider(engine);
    const admin = await p.getSigner(0), operator = await p.getSigner(1), user = await p.getSigner(2);
    const a = compile();
    const token = await new ContractFactory(a.abi, a.bytecode, admin).deploy(await admin.getAddress(), await operator.getAddress());
    await token.waitForDeployment();
    const w = await user.getAddress(), op = token.connect(operator);
    assert.equal(await token.decimals(), 0n);
    const rejects=fn=>assert.rejects(async()=>{await(await fn()).wait();});
    await rejects(()=>token.connect(user).reward(id('unauthorized'), w, 200));
    await (await op.reward(id('reward-1'), w, 200)).wait();
    assert.equal(await token.balanceOf(w), 200n);
    await rejects(()=>op.reward(id('reward-1'), w, 200));
    await rejects(()=>op.redeem(id('reward-1'), w, 100, 1));
    await rejects(()=>token.connect(user).transfer(admin.address, 1));
    await rejects(()=>token.connect(user).approve(operator.address, 1));
    await rejects(()=>op.redeem(id('too-expensive'), w, 600, 7));
    await rejects(()=>op.redeem(id('invalid-days'), w, 100, 0));
    const burnReceipt=await (await op.redeem(id('redemption-1'), w, 100, 1)).wait();
    assert.equal(await token.balanceOf(w), 100n);
    assert.equal((await token.operations(id('redemption-1'))).daysGranted, 1n);
    await rejects(()=>op.redeem(id('redemption-1'), w, 100, 1));
    const config={chain_id:31337,token_address:await token.getAddress()};
    const job={operation_id:id('redemption-1'),wallet_address:w,amount:100,kind:'burn',days:1,tx_hash:burnReceipt.hash};
    const proof=await verifiedReceipt(p,token,config,job);
    assert.equal(proof.receipt.hash,burnReceipt.hash);
    await assert.rejects(verifiedReceipt(p,token,config,{...job,amount:101}),/RECEIPT_EVENT_MISMATCH/);
    await (await op.reward(id('large-1'),w,1000)).wait();
    await (await op.reward(id('large-2'),w,1000)).wait();
    await (await op.reward(id('large-3'),w,500)).wait();
    await (await op.redeem(id('pro-30-days'),w,2400,30)).wait();
    await (await token.pause()).wait();
    await rejects(()=>op.reward(id('paused'), w, 5));
    await rejects(()=>op.redeem(id('paused-burn'), w, 100, 1));
    await rejects(()=>token.connect(user).unpause());
    assert.equal(await token.totalSupply(), 200n);
    assert.equal(await token.lifetimeMinted(), 2700n);
  } finally {
    if(engine) await engine.disconnect();
    if(path.resolve(dbPath).startsWith(path.resolve(root)+path.sep+'.evm-test-'))fs.rmSync(dbPath,{recursive:true,force:true});
  }
});
