import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { publicSession } from '../../backend/services/rewardsService.js';

const A='00000000-0000-4000-8000-000000000001', B='00000000-0000-4000-8000-000000000002';
const tx='0x'+'a'.repeat(64), hash='0x'+'b'.repeat(64);
async function init() {
  const db = new PGlite();
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz default now());
    create function auth.uid() returns uuid language sql as $$ select null::uuid $$;`);
  for (const name of ['002_studymind_pro.sql','005_blockchain_rewards.sql','006_reward_question_bank.sql','007_reward_admin.sql','008_reward_sepolia.sql','009_lesson_reward_evidence.sql'])
    await db.exec(await fs.readFile(new URL('../../supabase/migrations/'+name, import.meta.url),'utf8'));
  await db.query(`insert into auth.users(id,email) values($1,'a@test.local'),($2,'b@test.local')`,[A,B]);
  for (const [i,u] of [A,B].entries()) await db.query(`insert into studymind_reward_wallets(user_id,chain_id,token_address,address)
    values($1,31337,$2,$3)`,[u,'0x'+'1'.repeat(40),'0x'+String(i+2).repeat(40)]);
  return db;
}
async function one(db,sql,args=[]) { return (await db.query(sql,args)).rows[0]; }

test('Postgres: eligibility, one mint per mission, token burn before Pro, settlement replay and RLS',async()=>{
 const db=await init();
 try {
  let missions=(await one(db,`select studymind_missions($1) m`,[A])).m;
  const daily=missions.find(m=>m.code==='daily');
  await assert.rejects(db.query(`select studymind_claim_mission($1,$2)`,[A,daily.id]),/MISSION_NOT_READY/);
  await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic) values($1,'review-1','review','Toán học')`,[A]);
  const j=(await one(db,`select studymind_claim_mission($1,$2) j`,[A,daily.id])).j;
  const replay=(await one(db,`select studymind_claim_mission($1,$2) j`,[A,daily.id])).j;
  assert.equal(j.id,replay.id); assert.equal(j.amount,5); assert.equal(j.status,'queued');
  await assert.rejects(db.query(`select studymind_claim_mission($1,$2)`,[B,daily.id]));
  const key='00000000-0000-4000-8000-000000000003';
  const burn=(await one(db,`select studymind_queue_redemption($1,'pro_1d',1,$2) j`,[A,key])).j;
  assert.equal((await one(db,`select plan from studymind_accounts where id=$1`,[A])).plan,'free');
  assert.equal((await one(db,`select studymind_queue_redemption($1,'pro_1d',1,$2) j`,[A,key])).j.id,burn.id);
  await assert.rejects(db.query(`select studymind_queue_redemption($1,'pro_7d',1,$2)`,[A,key]),/IDEMPOTENCY_CONFLICT/);
  await assert.rejects(db.query(`select studymind_queue_redemption($1,'pro_1d',1,gen_random_uuid())`,[A]),/BURN_PENDING/);
  await db.query(`select studymind_settle_token_job($1,$2,100,$3,2)`,[burn.id,tx,hash]);
  const until=(await one(db,`select pro_until from studymind_accounts where id=$1`,[A])).pro_until;
  assert.ok(new Date(until)>new Date());
  await db.query(`select studymind_settle_token_job($1,$2,100,$3,2)`,[burn.id,tx,hash]);
  assert.equal(String((await one(db,`select pro_until from studymind_accounts where id=$1`,[A])).pro_until),String(until));
  await assert.rejects(db.query(`select studymind_queue_redemption($1,'pro_1d',99,gen_random_uuid())`,[A]),/PRICE_CHANGED/);
  await db.exec('set role authenticated');
  await assert.rejects(db.query(`select * from studymind_reward_questions`),/permission denied/);
  await assert.rejects(db.query(`select studymind_missions($1)`,[A]),/permission denied/);
  await assert.rejects(db.query(`update studymind_reward_jobs set status='confirmed'`),/permission denied/);
 } finally { await db.close(); }
});

test('Postgres: private challenge, server time, card deduplication and focus checks',async()=>{
 const db=await init();
 try {
  const bank=(await one(db,`select count(*) n from studymind_reward_questions`)).n;
  assert.equal(Number(bank),45);
  const s=await one(db,`select (studymind_start_reward_session($1,'quiz','Toán học')).*`,[A]);
  const shown=publicSession(s);
  assert.equal(shown.questions.length,10);
  assert.equal(JSON.stringify(shown).includes('answer'),false);
  const answers=Object.fromEntries(s.questions.map(q=>[q.id,q.answer]));
  await assert.rejects(db.query(`select studymind_reward_session_step($1,$2,'submit',$3)`,[A,s.id,answers]),/STUDY_TOO_FAST/);
  await db.query(`update studymind_reward_sessions set started_at=now()-interval '31 seconds' where id=$1`,[s.id]);
  const result=(await one(db,`select studymind_reward_session_step($1,$2,'submit',$3) r`,[A,s.id,answers])).r;
  assert.equal(result.score,100);
  await db.query(`select studymind_reward_session_step($1,$2,'submit',$3)`,[A,s.id,answers]);
  assert.equal(Number((await one(db,`select count(*) n from studymind_reward_events where kind='quiz'`)).n),1);
  const r=await one(db,`select (studymind_start_reward_session($1,'review','Khoa học')).*`,[A]);
  assert.equal(publicSession(r).card.back,null);
  await assert.rejects(db.query(`select studymind_reward_session_step($1,$2,'review')`,[A,r.id]),/STUDY_TOO_FAST/);
  await db.query(`update studymind_reward_sessions set card_started_at=now()-interval '4 seconds' where id=$1`,[r.id]);
  await db.query(`select studymind_reward_session_step($1,$2,'reveal')`,[A,r.id]);
  await db.query(`update studymind_reward_sessions set revealed_at=now()-interval '3 seconds' where id=$1`,[r.id]);
  await db.query(`select studymind_reward_session_step($1,$2,'review')`,[A,r.id]);
  const f=await one(db,`select (studymind_start_reward_session($1,'focus','focus')).*`,[A]);
  await assert.rejects(db.query(`select studymind_reward_session_step($1,$2,'finish')`,[A,f.id]),/FOCUS_NOT_READY/);
  await db.query(`update studymind_reward_sessions set started_at=now()-interval '26 minutes',focus_seconds=1440,
    last_heartbeat=now()-interval '61 seconds' where id=$1`,[f.id]);
  await db.query(`select studymind_reward_session_step($1,$2,'heartbeat')`,[A,f.id]);
  assert.equal((await one(db,`select studymind_reward_session_step($1,$2,'finish') r`,[A,f.id])).r.completed,true);
 } finally { await db.close(); }
});

test('Postgres: referral attribution, real study criteria, 48-hour hold and mint scheduling',async()=>{
 const db=await init();
 try {
  await db.query(`insert into studymind_referral_codes(user_id,code) values($1,'ABCDEFGHIJKL')`,[A]);
  await assert.rejects(db.query(`select studymind_bind_referral($1,'ABCDEFGHIJKL')`,[A]),/INVALID_REFERRAL/);
  await db.query(`select studymind_bind_referral($1,'ABCDEFGHIJKL')`,[B]);
  await assert.rejects(db.query(`select studymind_bind_referral($1,'ABCDEFGHIJKL')`,[B]),/REFERRAL_ALREADY_BOUND/);
  await db.query(`update studymind_referrals set created_at=now()-interval '3 days'`);
  await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic,created_at)
    select $1,'card-'||n,'review','Toán học',now()-make_interval(days=>case when n<=10 then 1 else 2 end) from generate_series(1,20) n`,[B]);
  await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic,score) values($1,'quiz:bank-session','quiz','Toán học',80)`,[B]);
  await db.query(`select studymind_process_referrals()`);
  assert.equal((await one(db,'select status from studymind_referrals')).status,'waiting');
  await db.query(`select studymind_process_referrals()`);
  assert.equal(Number((await one(db,'select count(*) n from studymind_reward_jobs')).n),0);
  await db.query(`update studymind_referrals set qualified_at=now()-interval '49 hours'`);
  await db.query(`select studymind_process_referrals()`);
  assert.equal((await one(db,'select status from studymind_referrals')).status,'queued');
  assert.deepEqual((await db.query('select amount from studymind_reward_jobs order by amount')).rows.map(r=>r.amount),[30,200]);
  await db.query(`select studymind_process_referrals()`);
  assert.equal(Number((await one(db,'select count(*) n from studymind_reward_jobs')).n),2);
 } finally { await db.close(); }
});

test('Postgres: admin audit, daily budget, pinned rewards and exclusive worker lease',async()=>{
 const db=await init();
 try{
  await assert.rejects(db.query(`select studymind_admin_reward_action($1,'catalog_amount','pro_1d',120)`,[A]),/ADMIN_REQUIRED/);
  await db.query(`update studymind_accounts set role='admin' where id=$1`,[A]);
  const original=(await one(db,`select studymind_missions($1) m`,[A])).m.find(m=>m.code==='quiz');
  await assert.rejects(db.query(`select studymind_admin_reward_action($1,'rule_amount','quiz',25)`,[A]),/DAILY_BUDGET_EXCEEDED/);
  await db.query(`select studymind_admin_reward_action($1,'rule_amount','quiz',15)`,[A]);
  assert.equal((await one(db,`select studymind_missions($1) m`,[A])).m.find(m=>m.code==='quiz').amount,original.amount);
  assert.equal((await one(db,`select studymind_missions($1) m`,[B])).m.find(m=>m.code==='quiz').amount,15);
  await db.query(`select studymind_admin_reward_action($1,'catalog_amount','pro_1d',120)`,[A]);
  await assert.rejects(db.query(`select studymind_queue_redemption($1,'pro_1d',1,gen_random_uuid())`,[A]),/PRICE_CHANGED/);
  assert.equal(Number((await one(db,`select count(*) n from studymind_reward_audit`)).n),2);
  assert.equal((await one(db,`select studymind_reward_worker_lease($1,false) ok`,[A])).ok,true);
  assert.equal((await one(db,`select studymind_reward_worker_lease($1,false) ok`,[B])).ok,false);
  assert.equal((await one(db,`select studymind_reward_worker_lease($1,true) ok`,[B])).ok,false);
  await db.query(`select studymind_reward_worker_lease($1,true)`,[A]);
  assert.equal((await one(db,`select studymind_reward_worker_lease($1,false) ok`,[B])).ok,true);
 }finally{await db.close();}
});

test('Postgres: 7-day streak is not rewarded on overlapping days and referral month cap is enforced',async()=>{
 const db=await init();
 try{
  await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic,created_at)
   select $1,'streak-'||n,'review','Toán học',(((now() at time zone 'Asia/Ho_Chi_Minh')::date-n)+time '00:01') at time zone 'Asia/Ho_Chi_Minh'
   from generate_series(0,6) n`,[A]);
  let missions=(await one(db,`select studymind_missions($1) m`,[A])).m;
  const streak=missions.find(m=>m.code==='streak');assert.equal(streak.status,'ready');
  const job=(await one(db,`select studymind_claim_mission($1,$2) j`,[A,streak.id])).j;
  assert.equal(job.amount,60);
  // Yesterday's completed reward must prevent another 7-day reward today.
  await db.query(`update studymind_user_missions set cycle=cycle-1 where id=$1`,[streak.id]);
  missions=(await one(db,`select studymind_missions($1) m`,[A])).m;
  assert.equal(missions.find(m=>m.code==='streak'&&m.id!==streak.id).status,'learning');
  await db.query(`insert into studymind_referrals(inviter,invitee,status,qualified_at) values($1,$2,'waiting',now()-interval '49 hours')`,[A,B]);
  for(let n=3;n<=5;n++){
   const uid='00000000-0000-4000-8000-'+String(n).padStart(12,'0');
   await db.query(`insert into auth.users(id,email) values($1,$2)`,[uid,'cap'+n+'@test.local']);
   await db.query(`insert into studymind_referrals(inviter,invitee,status,scheduled_at) values($1,$2,'rewarded',now())`,[A,uid]);
  }
  await db.query(`select studymind_process_referrals()`);
  assert.equal((await one(db,`select status from studymind_referrals where invitee=$1`,[B])).status,'limited');
 }finally{await db.close();}
});

test('Postgres: config accepts Amoy, Sepolia and the local chain only',async()=>{
 const db=await init();
 try {
  for (const id of [80002,11155111,31337]) await db.query(`update studymind_reward_config set chain_id=$1`,[id]);
  await assert.rejects(db.query(`update studymind_reward_config set chain_id=1`),/check constraint/);
 }finally{await db.close();}
});
