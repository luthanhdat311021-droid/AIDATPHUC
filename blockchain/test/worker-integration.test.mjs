import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import ganache from 'ganache';
import { PGlite } from '@electric-sql/pglite';
import { BrowserProvider,ContractFactory,HDNodeWallet } from 'ethers';
import { compile,root } from '../scripts/compile.mjs';
import { runRewardsWorker } from '../../backend/services/rewardsWorker.js';
import { deriveManagedAddress } from '../../backend/services/rewardsChain.js';
import { pgAdapter } from './helpers/pg-adapter.mjs';

test('end-to-end: SQL queue → signed mint/burn → EVM receipt → Pro; recover after DB failure',async()=>{
 const dbPath=await fs.mkdtemp(path.join(root,'.evm-test-'));
 const engine=ganache.provider({database:{dbPath},wallet:{deterministic:true},chain:{chainId:31337,hardfork:'shanghai'},logging:{quiet:true}});
 const pg=new PGlite();const oldKey=process.env.REWARDS_OPERATOR_KEY,oldXpub=process.env.REWARDS_WALLET_XPUB;
 let provider;
 try{
  provider=new BrowserProvider(engine,undefined,{cacheTimeout:-1});
  const admin=await provider.getSigner(0),operator=await provider.getSigner(1);
  const a=compile(),token=await new ContractFactory(a.abi,a.bytecode,admin).deploy(admin.address,operator.address);
  await token.waitForDeployment();
  const config={chain_id:31337,token_address:(await token.getAddress()).toLowerCase()};
  // Public deterministic test mnemonic; never used outside this isolated EVM.
  const xpub=HDNodeWallet.fromPhrase('test test test test test test test test test test test junk',undefined,"m/44'/60'/0'/0").neuter().extendedKey;
  process.env.REWARDS_WALLET_XPUB=xpub;
  process.env.REWARDS_OPERATOR_KEY=engine.getInitialAccounts()[operator.address.toLowerCase()].secretKey;
  await pg.exec(`create role anon;create role authenticated;create role service_role bypassrls;
   create schema auth;create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz default now());
   create function auth.uid() returns uuid language sql as $$ select null::uuid $$;`);
  for(const file of ['002_studymind_pro.sql','005_blockchain_rewards.sql','006_reward_question_bank.sql','007_reward_admin.sql','008_reward_sepolia.sql','009_lesson_reward_evidence.sql'])
   await pg.exec(await fs.readFile(new URL('../../supabase/migrations/'+file,import.meta.url),'utf8'));
  const uid='00000000-0000-4000-8000-000000000001',address=deriveManagedAddress(xpub,0);
  await pg.query(`insert into auth.users(id,email) values($1,'evm@test.local')`,[uid]);
  await pg.query(`insert into studymind_reward_wallets(user_id,address,chain_id,token_address) values($1,$2,31337,$3)`,[uid,address,config.token_address]);
  await pg.query(`select studymind_queue_token_job($1,'mint',200,0,'eligible-learning','Thưởng học hợp lệ')`,[uid]);
  const db=pgAdapter(pg);
  const options={database:db,configuration:config,connectChain:async()=>{
   const fresh=new BrowserProvider(engine,undefined,{cacheTimeout:-1});
   return {provider:fresh,token:token.connect(fresh)};
  }};
  const mint=await runRewardsWorker(options);
  assert.equal(mint.confirmed,1);
  assert.equal(await token.balanceOf(address),200n);
  await pg.query(`select studymind_queue_redemption($1,'pro_1d',1,gen_random_uuid())`,[uid]);
  db.failSettlementOnce=true;
  await runRewardsWorker(options);
  assert.equal(await token.balanceOf(address),100n,'burn succeeded on chain');
  assert.equal((await pg.query(`select plan from studymind_accounts where id=$1`,[uid])).rows[0].plan,'free','no unverified optimistic Pro');
  // Simulate receipt metadata loss; reconstruct the canonical hash from the operation event.
  await pg.query(`update studymind_reward_jobs set tx_hash=$1 where kind='burn'`,['0x'+'9'.repeat(64)]);
  const recovery=await runRewardsWorker(options);
  assert.equal(recovery.confirmed,1);
  const until=(await pg.query(`select pro_until from studymind_accounts where id=$1`,[uid])).rows[0].pro_until;
  assert.ok(new Date(until)>new Date());
  await runRewardsWorker(options);
  assert.equal(String((await pg.query(`select pro_until from studymind_accounts where id=$1`,[uid])).rows[0].pro_until),String(until));
  assert.equal(await token.balanceOf(address),100n,'recovery must not burn twice');
  assert.equal(Number((await pg.query(`select count(*) n from studymind_reward_jobs where status='confirmed'`)).rows[0].n),2);
 }finally{
  if(oldKey===undefined)delete process.env.REWARDS_OPERATOR_KEY;else process.env.REWARDS_OPERATOR_KEY=oldKey;
  if(oldXpub===undefined)delete process.env.REWARDS_WALLET_XPUB;else process.env.REWARDS_WALLET_XPUB=oldXpub;
  provider?.destroy();await pg.close();await engine.disconnect();
  if(path.resolve(dbPath).startsWith(path.resolve(root)+path.sep+'.evm-test-'))await fs.rm(dbPath,{recursive:true,force:true});
 }
});
