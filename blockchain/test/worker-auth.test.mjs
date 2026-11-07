import { test } from 'node:test';
import assert from 'node:assert/strict';
import { registerRewardWorkerRoute } from '../../backend/routes/rewardWorkerRoute.js';
import { deriveManagedAddress } from '../../backend/services/rewardsChain.js';

test('scheduler rejects absent, incorrect and Unicode secrets; malformed xpub is sanitized',async()=>{
 let handler;
 registerRewardWorkerRoute({post:(route,fn)=>{assert.equal(route,'/api/internal/rewards/run');handler=fn;}});
 const old=process.env.REWARDS_WORKER_SECRET;
 try{
  process.env.REWARDS_WORKER_SECRET='a'.repeat(32);
  for(const supplied of ['', 'wrong', 'é'.repeat(32)]){
   let status;
   await handler({get:()=>supplied},{status(n){status=n;return this;},json(){return this;}});
   assert.equal(status,401);
  }
  assert.throws(()=>deriveManagedAddress('invalid-sensitive-value',0),e=>e.code==='WALLET_CONFIG'&&!e.message.includes('invalid-sensitive-value'));
 }finally{if(old===undefined)delete process.env.REWARDS_WORKER_SECRET;else process.env.REWARDS_WORKER_SECRET=old;}
});
