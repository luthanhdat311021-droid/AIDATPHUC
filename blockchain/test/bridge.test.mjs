import express from 'express';
import { once } from 'node:events';
import { generateKeyPairSync } from 'node:crypto';
import { registerRewardsBridgeInternal } from '../../backend/routes/rewardsBridgeRoutes.js';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { signEnvelope, verifyEnvelope, bridgeCall, bridgeSource, bridgeTarget, TOKEN_CONTRACT } from '../../backend/services/rewardsBridge.js';
import { allowedRewardRoute } from '../../backend/routes/rewardsBridgeRoutes.js';
const A='00000000-0000-4000-8000-000000000001', B='00000000-0000-4000-8000-000000000002';
const JOB='00000000-0000-4000-8000-000000000003', TX='0x'+'a'.repeat(64), HASH='0x'+'b'.repeat(64);
const secret='bridge-test-secret-'.repeat(3);
test('HMAC binds payload, direction, expiry and nonce; rejects malformed or tampered envelopes', () => {
 const now=Date.now(), e=signEnvelope('forward',{user:A},secret,now);
 assert.equal(verifyEnvelope(e,'forward',secret,now).payload.user,A);
 for (const altered of [ {...e,signature:'ff'}, {...e,message:e.message.replace(A,B)},
   {...e,signature:'0'.repeat(64)}, {message:'null',signature:e.signature} ]) {
  assert.throws(()=>verifyEnvelope(altered,'forward',secret,now));
 }
 assert.throws(()=>verifyEnvelope(e,'grant',secret,now));
 assert.throws(()=>verifyEnvelope(e,'forward',secret,now+120001));
 assert.throws(()=>verifyEnvelope(e,'forward','',now));
 assert.throws(()=>signEnvelope('forward',{},'short'));
});
test('Proxy can reach reward routes only, never billing/worker or arbitrary URLs', () => {
 for (const [m,p] of [['GET','/me'],['POST','/biometric/challenge'],['POST',`/missions/${A}/claim`],['POST','/admin/actions']]) assert.equal(allowedRewardRoute(m,p),true);
 for (const [m,p] of [['POST','/../internal/rewards/run'],['GET','https://evil.invalid'],['DELETE','/me'],['GET','/billing/me'],['GET','/me?user=other']]) assert.equal(allowedRewardRoute(m,p),false);
});
test('Bridge mode is pinned to the correct Supabase project', () => {
 const saved={url:process.env.SUPABASE_URL,mode:process.env.REWARDS_BRIDGE_MODE};
 try {
  process.env.REWARDS_BRIDGE_MODE='source';process.env.SUPABASE_URL='https://urnlxgyqmvcbkonysizy.supabase.co';
  assert.equal(bridgeSource(),false);process.env.REWARDS_BRIDGE_MODE='target';assert.equal(bridgeTarget(),true);
 } finally {
  for (const [k,v] of [['SUPABASE_URL',saved.url],['REWARDS_BRIDGE_MODE',saved.mode]]) v===undefined?delete process.env[k]:process.env[k]=v;
 }
});
test('Transport is pinned, forbids redirects, hides upstream failures and preserves application errors',async()=>{
 process.env.REWARDS_BRIDGE_SECRET=secret;
 const result=await bridgeCall('forward',{identity:{id:A}},async(url,options)=>{
  assert.equal(url,'https://urnlxgyqmvcbkonysizy.supabase.co/functions/v1/studymind-api/api/internal/rewards/bridge/forward');
  assert.equal(options.redirect,'error');assert.ok(options.signal);
  assert.equal(verifyEnvelope(JSON.parse(options.body),'forward',secret).payload.identity.id,A);
  return {ok:true,json:async()=>({success:true,data:{enabled:true}})};
 });
 assert.equal(result.enabled,true);
 await assert.rejects(bridgeCall('forward',{},async()=>({ok:false,status:500,json:async()=>({error:'SECRET SQL'})})), e=>!e.message.includes('SECRET'));
 await assert.rejects(bridgeCall('forward',{},async()=>({ok:false,status:409,json:async()=>({code:'PRICE_CHANGED',error:'price changed'})})),e=>e.code==='PRICE_CHANGED'&&e.status===409);
 delete process.env.REWARDS_BRIDGE_SECRET;
});
async function init(target) {
 const db=new PGlite();
 await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
 create schema auth;create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),
 email_confirmed_at timestamptz,updated_at timestamptz,aud text,role text,banned_until timestamptz,raw_app_meta_data jsonb,raw_user_meta_data jsonb);
 create function auth.uid() returns uuid language sql as $$select null::uuid$$;`);
 for(const name of ['002_studymind_pro.sql',...(target?['005_blockchain_rewards.sql','006_reward_question_bank.sql','007_reward_admin.sql','008_reward_sepolia.sql','009_lesson_reward_evidence.sql']:[])])
  await db.exec(await fs.readFile(new URL('../../supabase/migrations/'+name,import.meta.url),'utf8'));
 await db.exec(await fs.readFile(new URL('../../supabase/bridge/'+(target?'staging':'primary')+'.sql',import.meta.url),'utf8'));
 return db;
}
const one=async(db,sql,args=[]) => (await db.query(sql,args)).rows[0];
test('Staging mapping is isolated, idempotent, login-banned and preserves signup date/verification',async()=>{
 const db=await init(true);
 try {
  await db.query('insert into auth.users(id) values($1)',[A]);
  const sql='select studymind_bridge_identity($1,$2,$3,$4) id', created='2026-01-01T00:00:00Z';
  const {id}=await one(db,sql,[A,created,true,true]);
  assert.notEqual(id,A);assert.equal((await one(db,sql,[A,created,true,true])).id,id);
  const u=await one(db,`select email,banned_until::text,created_at,email_confirmed_at from auth.users where id=$1`,[id]);
  assert.equal(u.email,null);assert.equal(u.banned_until,'infinity');assert.equal(new Date(u.created_at).toISOString(),'2026-01-01T00:00:00.000Z');assert.ok(u.email_confirmed_at);
  assert.equal((await one(db,'select role from studymind_accounts where id=$1',[id])).role,'admin');
  await one(db,sql,[A,created,false,false]);
  assert.equal((await one(db,'select role from studymind_accounts where id=$1',[id])).role,'user');
  assert.equal((await one(db,'select email_confirmed_at from auth.users where id=$1',[id])).email_confirmed_at,null);
  await db.query('select studymind_bridge_accept_nonce($1)',[JOB]);
  await assert.rejects(db.query('select studymind_bridge_accept_nonce($1)',[JOB]),/duplicate/);
  await db.exec('set role authenticated');
  await assert.rejects(db.query(sql,[B,created,true,true]),/permission denied/);
  await assert.rejects(db.query('select * from studymind_bridge_identities'),/permission denied/);
 } finally {await db.close();}
});
test('Only confirmed mapped burns enter durable outbox; retries cannot duplicate grants',async()=>{
 const db=await init(true);
 try {
  const {id}=await one(db,'select studymind_bridge_identity($1,now(),true,false) id',[A]);
  await db.query('insert into studymind_reward_wallets(user_id,chain_id,token_address,address) values($1,11155111,$2,$3)',[id,TOKEN_CONTRACT,'0x'+'2'.repeat(40)]);
  await db.query(`insert into studymind_reward_jobs(id,user_id,kind,amount,days,source_key,label,chain_id,token_address,wallet_address)
   values($1,$2,'burn',100,1,'test-burn','Pro',11155111,$3,$4)`,[JOB,id,TOKEN_CONTRACT,'0x'+'2'.repeat(40)]);
  assert.equal(Number((await one(db,'select count(*) n from studymind_bridge_grants')).n),0);
  for(let i=0;i<2;i++) await db.query('select studymind_settle_token_job($1,$2,100,$3,0)',[JOB,TX,HASH]);
  const g=await one(db,'select * from studymind_bridge_grants');
  assert.equal(g.source_user_id,A);assert.equal(g.days,1);assert.equal(g.delivered_at,null);
  assert.equal(Number((await one(db,'select count(*) n from studymind_bridge_grants')).n),1);
 } finally {await db.close();}
});
test('Primary grants extend existing Pro once, reject altered replay/duplicate event and preserve role',async()=>{
 const db=await init(false);
 try {
  await db.query('insert into auth.users(id) values($1)',[A]);
  await db.query("update studymind_accounts set role='admin',plan='pro',pro_until=now()+interval '10 days' where id=$1",[A]);
  const before=new Date((await one(db,'select pro_until from studymind_accounts where id=$1',[A])).pro_until);
  const sql='select studymind_apply_bridge_grant($1,$2,$3,$4,$5,$6,$7) r', args=[JOB,A,7,11155111,TOKEN_CONTRACT,TX,0];
  assert.equal((await one(db,sql,args)).r.applied,true);assert.equal((await one(db,sql,args)).r.applied,false);
  const after=await one(db,'select pro_until,role from studymind_accounts where id=$1',[A]);
  assert.equal(new Date(after.pro_until)-before,7*86400000);assert.equal(after.role,'admin');
  await assert.rejects(one(db,sql,[JOB,A,30,...args.slice(3)]),/CONFLICT/);
  await assert.rejects(one(db,sql,[B,...args.slice(1)]),/duplicate/);
  await assert.rejects(one(db,sql,[B,A,7,80002,TOKEN_CONTRACT,TX,1]),/INVALID/);
  await db.exec('set role authenticated');await assert.rejects(one(db,sql,args),/permission denied/);
 } finally {await db.close();}
});


test('Target HTTP bridge rejects unsigned requests and uses mapped user for biometric enrollment', async () => {
 const keys=['SUPABASE_URL','SUPABASE_SERVICE_ROLE_KEY','REWARDS_BRIDGE_MODE','REWARDS_BRIDGE_SECRET'];
 const saved=Object.fromEntries(keys.map(k=>[k,process.env[k]]));
 Object.assign(process.env,{SUPABASE_URL:'https://urnlxgyqmvcbkonysizy.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'test-service-key',REWARDS_BRIDGE_MODE:'target',REWARDS_BRIDGE_SECRET:secret});
 const realFetch=globalThis.fetch, calls=[];
 const {publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
 const publicBase64=publicKey.export({type:'spki',format:'der'}).toString('base64');
 globalThis.fetch=async(input,options)=>{
  const url=String(input instanceof Request?input.url:input);
  if(!url.startsWith('https://urnlxgyqmvcbkonysizy.supabase.co/')) return realFetch(input,options);
  const body=options?.body?JSON.parse(options.body):null;calls.push({url,body});
  if(url.includes('/rpc/studymind_bridge_accept_nonce')) return Response.json(null);
  if(url.includes('/rpc/studymind_bridge_identity')) return Response.json(B);
  if(url.includes('/studymind_biometric_keys')) return Response.json(options?.method==='POST'?null:null);
  throw Error('Unexpected database operation');
 };
 const app=express();app.use(express.json());registerRewardsBridgeInternal(app);
 app.use((err,req,res,next)=>res.status(err.status||500).json({success:false,code:err.code,error:err.message}));
 const server=app.listen(0,'127.0.0.1');await once(server,'listening');
 const endpoint='http://127.0.0.1:'+server.address().port+'/api/internal/rewards/bridge/forward';
 const send=body=>realFetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
 try {
  assert.equal((await send({identity:{id:A,isAdmin:true}})).status,401);assert.equal(calls.length,0);
  const payload={action:'route',method:'POST',path:'/biometric/enroll',identity:{id:A,createdAt:'2026-01-01T00:00:00Z',emailVerified:true,isAdmin:false},body:{publicKey:publicBase64}};
  const response=await send(signEnvelope('forward',payload,secret));assert.equal(response.status,200,JSON.stringify(await response.clone().json()));
  assert.equal((await response.json()).data.active,true);
  const write=calls.find(c=>c.url.includes('/studymind_biometric_keys')&&c.body);assert.equal(write.body.user_id,B);
  assert.equal(write.body.public_key,publicBase64);
  const admin=await send(signEnvelope('forward',{...payload,path:'/admin/actions',body:{action:'rule_amount',id:'x',value:10}},secret));assert.equal(admin.status,403);
 } finally {
  globalThis.fetch=realFetch;server.closeAllConnections();await new Promise(resolve=>server.close(resolve));
  for(const k of keys) saved[k]===undefined?delete process.env[k]:process.env[k]=saved[k];
 }
});
