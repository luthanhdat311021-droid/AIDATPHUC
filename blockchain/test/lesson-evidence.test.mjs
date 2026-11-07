import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { isUserCreated } from '../../backend/services/rewardsService.js';

const A='00000000-0000-4000-8000-000000000001', B='00000000-0000-4000-8000-000000000002';
async function init() {
  const db = new PGlite();
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz default now());
    create function auth.uid() returns uuid language sql as $$ select null::uuid $$;`);
  for (const name of ['002_studymind_pro.sql','005_blockchain_rewards.sql','006_reward_question_bank.sql','007_reward_admin.sql','008_reward_sepolia.sql','009_lesson_reward_evidence.sql'])
    await db.exec(await fs.readFile(new URL('../../supabase/migrations/'+name, import.meta.url),'utf8'));
  await db.query(`insert into auth.users(id,email) values($1,'a@test.local'),($2,'b@test.local')`,[A,B]);
  return db;
}
const record = async (db,u,kind,key,score=null) =>
  (await db.query(`select studymind_record_lesson_event($1,$2,'Sinh học',$3,$4) ok`,[u,kind,key,score])).rows[0].ok;
// Pretend the previous lesson card was read long enough ago, instead of sleeping in the test.
const age = db => db.query(`update studymind_reward_events set created_at=created_at-interval '5 seconds'`);
const mission = async (db,u,code) => (await db.query(`select studymind_missions($1) m`,[u])).rows[0].m.find(m=>m.code===code);

test('lesson content: manual cards/questions are recognised and never count', () => {
  assert.equal(isUserCreated({ id: 'fc-1' }), false);
  assert.equal(isUserCreated({ id: 'fc_007' }), false);
  assert.equal(isUserCreated({ id: 'q-12' }), false);
  assert.equal(isUserCreated({ id: 'fc-1792313765123' }), true);
  assert.equal(isUserCreated({ id: 'q-1792313765123' }), true);
  assert.equal(isUserCreated({ id: 'fc-3', userCreated: true }), true);
});

test('Postgres: lesson cards count once per day, ≥3 s apart, and feed daily/cards/streak missions',async()=>{
  const db=await init();
  try {
    assert.equal(await record(db,A,'review',`lesson-card:${A}:doc-1:fc-1`),true);
    assert.equal(await record(db,A,'review',`lesson-card:${A}:doc-1:fc-2`),false,'second card within 3 s is too fast');
    await age(db);
    assert.equal(await record(db,A,'review',`lesson-card:${A}:doc-1:fc-1`),false,'same card twice in a day');
    assert.equal(await record(db,A,'review',`lesson-card:${A}:doc-1:fc-2`),true);
    // Another learner is not slowed down by A's reading gap.
    assert.equal(await record(db,B,'review',`lesson-card:${B}:doc-9:fc-1`),true);
    assert.equal((await mission(db,A,'daily')).status,'ready');
    assert.equal((await mission(db,A,'cards')).progress,2);
    assert.equal((await mission(db,A,'streak')).progress,1);
    // Only lesson keys are accepted, so bank/focus evidence cannot be forged through this function.
    await assert.rejects(db.query(`select studymind_record_lesson_event($1,'review','x','card:forged')`,[A]),/INVALID_SESSION/);
    await assert.rejects(db.query(`select studymind_record_lesson_event($1,'focus','x','lesson-card:x')`,[A]),/INVALID_SESSION/);
  } finally { await db.close(); }
});

test('Postgres: lesson quiz counts its first daily submission for "quiz", never for mastery or referrals',async()=>{
  const db=await init();
  try {
    assert.equal(await record(db,A,'quiz',`lesson-quiz:${A}:doc-1`,60),true);
    assert.equal(await record(db,A,'quiz',`lesson-quiz:${A}:doc-1`,100),false,'a retry the same day does not replace the first score');
    assert.equal((await mission(db,A,'quiz')).status,'learning');
    assert.equal(await record(db,A,'quiz',`lesson-quiz:${A}:doc-2`,90),true);
    assert.equal((await mission(db,A,'quiz')).status,'ready');
    for (const doc of ['doc-3','doc-4','doc-5']) {
      await db.query(`update studymind_reward_events set topic=$1 where source_key like $2`,[doc,`lesson-quiz:${A}:doc-2:%`]);
      await record(db,A,'quiz',`lesson-quiz:${A}:${doc}`,100);
    }
    assert.equal((await mission(db,A,'mastery')).progress,0,'mastery only counts the private question bank');
    // Referral: B (invitee) does real lesson study for 2 days + 20 cards, but only a lesson quiz.
    await db.query(`insert into studymind_referrals(inviter,invitee,created_at) values($1,$2,now()-interval '2 days')`,[A,B]);
    for (let i=0;i<20;i++) await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic,created_at)
      values($1,$2,'review','Sinh học',now()-($3::int*interval '1 day'))`,[B,`lesson-card:${B}:doc:fc-${i}:x`,i%2]);
    await record(db,B,'quiz',`lesson-quiz:${B}:doc-1`,100);
    await db.query(`select studymind_process_referrals()`);
    assert.equal((await db.query(`select status from studymind_referrals where invitee=$1`,[B])).rows[0].status,'learning');
    await db.query(`insert into studymind_reward_events(user_id,source_key,kind,topic,score) values($1,'quiz:bank-session','quiz','Toán học',80)`,[B]);
    await db.query(`select studymind_process_referrals()`);
    assert.equal((await db.query(`select status from studymind_referrals where invitee=$1`,[B])).rows[0].status,'waiting');
  } finally { await db.close(); }
});

test('Postgres: lesson evidence is service-only',async()=>{
  const db=await init();
  try {
    const grants=(await db.query(`select grantee from information_schema.routine_privileges
      where routine_name='studymind_record_lesson_event' and privilege_type='EXECUTE'`)).rows.map(r=>r.grantee);
    assert.ok(grants.includes('service_role'));
    assert.ok(!grants.includes('authenticated') && !grants.includes('anon') && !grants.includes('PUBLIC'));
  } finally { await db.close(); }
});
