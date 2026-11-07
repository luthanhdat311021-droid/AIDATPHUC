-- Option B: only confirmed on-chain operations settle rewards or grant Pro.
-- Additive; run after 004 on a staging Supabase project first.
begin;

create table public.studymind_reward_config (
  id boolean primary key default true check (id), enabled boolean not null default false,
  chain_id bigint not null default 80002 check (chain_id in (80002,31337)),
  token_address text, wallet_xpub_fingerprint text,
  updated_at timestamptz not null default now()
);
insert into public.studymind_reward_config(id) values(true);

create table public.studymind_reward_wallets (
  user_id uuid primary key references auth.users(id),
  wallet_index integer generated always as identity (minvalue 0 start with 0 maxvalue 2147483647) unique,
  address text unique check (address ~ '^0x[0-9a-f]{40}$'),
  chain_id bigint not null, token_address text not null check(token_address ~ '^0x[0-9a-f]{40}$'),
  created_at timestamptz not null default now()
);

create table public.studymind_reward_jobs (
  id uuid primary key default gen_random_uuid(),
  operation_id text generated always as ('0x' || repeat('0',32) || replace(id::text,'-','')) stored unique,
  user_id uuid not null references public.studymind_reward_wallets(user_id),
  kind text not null check (kind in ('mint','burn')),
  amount integer not null check(amount > 0 and amount <= 10000),
  days integer not null default 0 check(days between 0 and 365),
  source_key text not null unique, label text not null,
  status text not null default 'queued' check(status in ('queued','prepared','submitted','confirmed','failed')),
  chain_id bigint not null, token_address text not null, wallet_address text not null,
  tx_hash text, raw_transaction text, nonce bigint, block_number bigint, block_hash text, log_index integer,
  search_from_block bigint, search_cursor bigint,
  error_code text, attempts integer not null default 0,
  created_at timestamptz not null default now(), confirmed_at timestamptz,
  check ((kind='mint' and days=0) or (kind='burn' and days>0))
);
-- Reserve at most one pending burn; chain balance, not an internal coin balance, is authoritative.
create unique index studymind_one_pending_burn on public.studymind_reward_jobs(user_id)
 where kind='burn' and status in ('queued','prepared','submitted');
create unique index studymind_unique_chain_log on public.studymind_reward_jobs(chain_id,token_address,tx_hash,log_index)
 where status='confirmed';
create index studymind_jobs_pending on public.studymind_reward_jobs(created_at) where status <> 'confirmed';

create table public.studymind_reward_catalog (
  id text primary key, title text not null, amount integer not null check(amount between 1 and 10000),
  days integer not null check(days between 1 and 365), version integer not null default 1,
  active boolean not null default true
);
insert into public.studymind_reward_catalog(id,title,amount,days) values
 ('pro_1d','Pro 1 ngày',100,1),('pro_7d','Pro 7 ngày',600,7),('pro_30d','Pro 30 ngày',2400,30);
create table public.studymind_reward_redemptions (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
  idempotency_key uuid not null, catalog_id text not null, catalog_version integer not null,
  job_id uuid not null unique references public.studymind_reward_jobs(id),
  pro_until timestamptz, created_at timestamptz not null default now(), unique(user_id,idempotency_key)
);

create table public.studymind_reward_questions (
  id uuid primary key default gen_random_uuid(), topic text not null, question text not null,
  options jsonb not null check(jsonb_array_length(options)=4),
  answer integer not null check(answer between 0 and 3), active boolean not null default true
);
create table public.studymind_reward_sessions (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
  kind text not null check(kind in ('quiz','review','focus')), topic text not null,
  questions jsonb not null default '[]', result jsonb,
  started_at timestamptz not null default now(), expires_at timestamptz not null default now()+interval '40 minutes',
  finished_at timestamptz, review_index integer not null default 0,
  card_started_at timestamptz not null default now(), revealed_at timestamptz,
  last_heartbeat timestamptz not null default now(), focus_seconds integer not null default 0
);
create unique index studymind_one_active_session on public.studymind_reward_sessions(user_id,kind)
 where finished_at is null;
create table public.studymind_reward_events (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
  source_key text not null unique, kind text not null check(kind in ('review','quiz','focus')),
  topic text not null, score integer, created_at timestamptz not null default now()
);
create index studymind_reward_events_user_time on public.studymind_reward_events(user_id,created_at);

create table public.studymind_reward_rules (
 code text primary key, title text not null, description text not null, difficulty text not null,
 amount integer not null check(amount between 1 and 1000), goal integer not null check(goal>0),
 period text not null check(period in ('day','week')), version integer not null default 1, active boolean not null default true
);
insert into public.studymind_reward_rules values
 ('daily','Bắt đầu ngày học','Hoàn thành ít nhất một lượt ôn hoặc thử thách hợp lệ.','Dễ',5,1,'day',1,true),
 ('cards','Ôn 10 thẻ thử thách','Ôn 10 thẻ khác nhau trong ngân hàng học tập, không tính thẻ tự tạo.','Dễ',10,10,'day',1,true),
 ('quiz','Vượt thử thách quiz','Làm 10 câu hỏi và đạt từ 80%.','Vừa',20,1,'day',1,true),
 ('focus','Tập trung 25 phút','Giữ phiên học và có ít nhất 2 hoạt động học hợp lệ.','Vừa',10,1,'day',1,true),
 ('streak','Học liên tục 7 ngày','Có hoạt động học mỗi ngày; mỗi 7 ngày nhận một lần.','Khó',60,7,'day',1,true),
 ('mastery','Làm chủ 3 chủ đề','Đạt từ 90% ở 3 chủ đề khác nhau trong tuần.','Khó',40,3,'week',1,true);
create table public.studymind_user_missions (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
 code text not null, cycle date not null, expires_at timestamptz not null,
 rule jsonb not null, job_id uuid references public.studymind_reward_jobs(id),
 unique(user_id,code,cycle)
);

create table public.studymind_referral_codes (
 user_id uuid primary key references auth.users(id), code text not null unique
   default upper(substr(replace(gen_random_uuid()::text,'-',''),1,12))
);
create table public.studymind_referrals (
 id uuid primary key default gen_random_uuid(), inviter uuid not null references auth.users(id),
 invitee uuid not null unique references auth.users(id), created_at timestamptz not null default now(),
 qualified_at timestamptz, scheduled_at timestamptz, status text not null default 'learning'
 check(status in ('learning','waiting','queued','rewarded','rejected','expired','limited')),
 reason text, inviter_job uuid references public.studymind_reward_jobs(id),
 invitee_job uuid references public.studymind_reward_jobs(id), check(inviter<>invitee)
);
create table public.studymind_reward_audit (
 id bigint generated always as identity primary key, actor uuid, action text not null,
 details jsonb not null, created_at timestamptz not null default now()
);
create table public.studymind_reward_worker_lock (
 id boolean primary key default true check(id), owner uuid, until_at timestamptz not null default now()
);
insert into public.studymind_reward_worker_lock(id) values(true);

-- Defense in depth: every new table has RLS, no client mutation policies.
do $$ declare t text; begin
 foreach t in array array['studymind_reward_config','studymind_reward_wallets','studymind_reward_jobs',
 'studymind_reward_catalog','studymind_reward_redemptions','studymind_reward_questions','studymind_reward_sessions',
 'studymind_reward_events','studymind_reward_rules','studymind_user_missions','studymind_referral_codes',
 'studymind_referrals','studymind_reward_audit','studymind_reward_worker_lock'] loop
   execute format('alter table public.%I enable row level security',t);
   execute format('revoke all on public.%I from anon, authenticated',t);
   execute format('grant all on public.%I to service_role',t);
 end loop;
end $$;
grant usage,select on all sequences in schema public to service_role;

-- The trusted API calls these functions only after deriving the authenticated user.
create function public.studymind_queue_token_job(p_user uuid,p_kind text,p_amount integer,p_days integer,p_source text,p_label text)
returns public.studymind_reward_jobs language plpgsql security definer set search_path=public as $$
declare w public.studymind_reward_wallets; j public.studymind_reward_jobs;
begin
 select * into strict w from public.studymind_reward_wallets where user_id=p_user for update;
 if w.address is null then raise exception 'WALLET_NOT_READY'; end if;
 insert into public.studymind_reward_jobs(user_id,kind,amount,days,source_key,label,chain_id,token_address,wallet_address)
 values(p_user,p_kind,p_amount,p_days,p_source,p_label,w.chain_id,w.token_address,w.address)
 on conflict(source_key) do nothing returning * into j;
 if j.id is null then select * into j from public.studymind_reward_jobs where source_key=p_source; end if;
 return j;
end $$;

create function public.studymind_missions(p_user uuid)
returns jsonb language plpgsql security definer set search_path=public as $$
declare d date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
 wk date := date_trunc('week',now() at time zone 'Asia/Ho_Chi_Minh')::date;
 result jsonb := '[]'; m record; progress integer; ready boolean; st text;
begin
 insert into public.studymind_user_missions(user_id,code,cycle,expires_at,rule)
 select p_user,r.code,case when r.period='week' then wk else d end,
 (case when r.period='week' then wk+7 else d+1 end)::timestamp at time zone 'Asia/Ho_Chi_Minh', to_jsonb(r)
 from public.studymind_reward_rules r where r.active
 on conflict(user_id,code,cycle) do nothing;
 for m in select um.*,j.status as token_status from public.studymind_user_missions um
 left join public.studymind_reward_jobs j on j.id=um.job_id
 where um.user_id=p_user and um.expires_at>now() order by (um.rule->>'amount')::int loop
   progress:=0; ready:=false;
   if m.code='daily' then
     select count(*) into progress from public.studymind_reward_events where user_id=p_user
      and created_at >= d::timestamp at time zone 'Asia/Ho_Chi_Minh';
   elsif m.code='cards' then
     select count(*) into progress from public.studymind_reward_events where user_id=p_user and kind='review'
      and created_at >= d::timestamp at time zone 'Asia/Ho_Chi_Minh';
   elsif m.code='quiz' then
     select count(*) into progress from public.studymind_reward_events where user_id=p_user and kind='quiz' and score>=80
      and created_at >= d::timestamp at time zone 'Asia/Ho_Chi_Minh';
   elsif m.code='focus' then
     select count(*) into progress from public.studymind_reward_events where user_id=p_user and kind='focus'
      and created_at >= d::timestamp at time zone 'Asia/Ho_Chi_Minh';
   elsif m.code='mastery' then
     select count(distinct topic) into progress from public.studymind_reward_events where user_id=p_user and kind='quiz' and score>=90
      and created_at >= wk::timestamp at time zone 'Asia/Ho_Chi_Minh';
   elsif m.code='streak' then
     select count(distinct (created_at at time zone 'Asia/Ho_Chi_Minh')::date) into progress
     from public.studymind_reward_events where user_id=p_user
      and created_at >= (d-6)::timestamp at time zone 'Asia/Ho_Chi_Minh';
     if exists(select 1 from public.studymind_user_missions old where old.user_id=p_user and old.code='streak'
       and old.job_id is not null and old.cycle>d-7 and old.id<>m.id) then progress:=0; end if;
   end if;
   ready:=progress >= (m.rule->>'goal')::int;
   st:=coalesce(m.token_status,case when ready then 'ready' else 'learning' end);
   result:=result || jsonb_build_array(jsonb_build_object('id',m.id,'code',m.code,'title',m.rule->>'title',
    'description',m.rule->>'description','difficulty',m.rule->>'difficulty','amount',(m.rule->>'amount')::int,
    'goal',(m.rule->>'goal')::int,'progress',least(progress,(m.rule->>'goal')::int),'period',m.rule->>'period',
    'expiresAt',m.expires_at,'status',st,'jobId',m.job_id));
 end loop;
 return result;
end $$;

create function public.studymind_claim_mission(p_user uuid,p_mission uuid)
returns jsonb language plpgsql security definer set search_path=public as $$
declare m public.studymind_user_missions; snapshot jsonb; j public.studymind_reward_jobs;
begin
 perform 1 from public.studymind_reward_wallets where user_id=p_user for update;
 select * into strict m from public.studymind_user_missions where id=p_mission and user_id=p_user for update;
 if m.job_id is not null then select * into j from public.studymind_reward_jobs where id=m.job_id; return to_jsonb(j)-'raw_transaction'; end if;
 if m.expires_at<=now() then raise exception 'MISSION_EXPIRED'; end if;
 select item into snapshot from jsonb_array_elements(public.studymind_missions(p_user)) item where item->>'id'=m.id::text;
 if snapshot->>'status' is distinct from 'ready' then raise exception 'MISSION_NOT_READY'; end if;
 j:=public.studymind_queue_token_job(p_user,'mint',(m.rule->>'amount')::int,0,'mission:'||m.id,m.rule->>'title');
 update public.studymind_user_missions set job_id=j.id where id=m.id;
 return to_jsonb(j)-'raw_transaction';
end $$;

create function public.studymind_queue_redemption(p_user uuid,p_catalog text,p_version integer,p_key uuid)
returns jsonb language plpgsql security definer set search_path=public as $$
declare r public.studymind_reward_redemptions; c public.studymind_reward_catalog; j public.studymind_reward_jobs;
begin
 perform 1 from public.studymind_reward_wallets where user_id=p_user for update;
 select * into r from public.studymind_reward_redemptions where user_id=p_user and idempotency_key=p_key;
 if r.id is not null then
   if r.catalog_id<>p_catalog or r.catalog_version<>p_version then raise exception 'IDEMPOTENCY_CONFLICT'; end if;
   select * into j from public.studymind_reward_jobs where id=r.job_id;
   return to_jsonb(j)-'raw_transaction';
 end if;
 select * into c from public.studymind_reward_catalog where id=p_catalog and active for share;
 if c.id is null then raise exception 'CATALOG_NOT_FOUND'; end if;
 if c.version<>p_version then raise exception 'PRICE_CHANGED'; end if;
 if exists(select 1 from public.studymind_reward_jobs where user_id=p_user and kind='burn'
  and status in ('queued','prepared','submitted')) then raise exception 'BURN_PENDING'; end if;
 j:=public.studymind_queue_token_job(p_user,'burn',c.amount,c.days,'redeem:'||p_user||':'||p_key,c.title);
 insert into public.studymind_reward_redemptions(user_id,idempotency_key,catalog_id,catalog_version,job_id)
 values(p_user,p_key,c.id,c.version,j.id);
 return to_jsonb(j)-'raw_transaction';
end $$;

-- Receipt verification happens in the worker. This RPC atomically records settlement and Pro.
create function public.studymind_settle_token_job(p_job uuid,p_tx text,p_block bigint,p_block_hash text,p_log integer)
returns jsonb language plpgsql security definer set search_path=public as $$
declare j public.studymind_reward_jobs; until_at timestamptz;
begin
 select * into strict j from public.studymind_reward_jobs where id=p_job for update;
 if j.status='confirmed' then return jsonb_build_object('status','confirmed'); end if;
 if p_tx !~ '^0x[0-9a-fA-F]{64}$' or p_block_hash !~ '^0x[0-9a-fA-F]{64}$' or p_log<0 then raise exception 'INVALID_RECEIPT'; end if;
 if j.kind='burn' then
  update public.studymind_accounts set plan='pro',
   pro_until=greatest(coalesce(pro_until,now()),now())+make_interval(days=>j.days),updated_at=now()
   where id=j.user_id returning pro_until into until_at;
  if until_at is null then raise exception 'ACCOUNT_NOT_FOUND'; end if;
  update public.studymind_reward_redemptions set pro_until=until_at where job_id=j.id;
 end if;
 update public.studymind_reward_jobs set status='confirmed',tx_hash=lower(p_tx),block_number=p_block,
  block_hash=lower(p_block_hash),log_index=p_log,confirmed_at=now(),raw_transaction=null,error_code=null where id=j.id;
 insert into public.studymind_reward_audit(action,details) values('token_settled',jsonb_build_object('job',j.id,'tx',p_tx));
 return jsonb_build_object('status','confirmed','proUntil',until_at);
end $$;

create function public.studymind_start_reward_session(p_user uuid,p_kind text,p_topic text)
returns public.studymind_reward_sessions language plpgsql security definer set search_path=public as $$
declare s public.studymind_reward_sessions; qs jsonb; d timestamptz :=
 ((now() at time zone 'Asia/Ho_Chi_Minh')::date)::timestamp at time zone 'Asia/Ho_Chi_Minh';
begin
 perform 1 from public.studymind_reward_wallets where user_id=p_user for update;
 update public.studymind_reward_sessions set finished_at=now(),result='{"expired":true}'
 where user_id=p_user and finished_at is null and expires_at<=now();
 select * into s from public.studymind_reward_sessions where user_id=p_user and kind=p_kind and finished_at is null;
 if s.id is not null then return s; end if;
 if p_kind not in ('quiz','review','focus') then raise exception 'INVALID_SESSION'; end if;
 if (select count(*) from public.studymind_reward_sessions where user_id=p_user and kind=p_kind and started_at>=d)
   >= (case when p_kind='quiz' then 3 when p_kind='review' then 2 else 3 end) then raise exception 'SESSION_DAILY_LIMIT'; end if;
 qs:='[]';
 if p_kind<>'focus' then
   select jsonb_agg(to_jsonb(q)) into qs from (select id,topic,question,options,answer from public.studymind_reward_questions
     where active and topic=p_topic order by random() limit 10) q;
   if qs is null or jsonb_array_length(qs)<10 then raise exception 'TOPIC_NOT_READY'; end if;
 end if;
 insert into public.studymind_reward_sessions(user_id,kind,topic,questions) values(p_user,p_kind,coalesce(p_topic,'focus'),qs) returning * into s;
 return s;
end $$;

create function public.studymind_reward_session_step(p_user uuid,p_session uuid,p_action text,p_answers jsonb default null)
returns jsonb language plpgsql security definer set search_path=public as $$
declare s public.studymind_reward_sessions; q jsonb; correct integer:=0; score integer; v_result jsonb; elapsed integer;
begin
 select * into strict s from public.studymind_reward_sessions where id=p_session and user_id=p_user for update;
 if s.finished_at is not null then return coalesce(s.result,'{}'); end if;
 if s.expires_at<=now() then raise exception 'SESSION_EXPIRED'; end if;
 if s.kind='quiz' and p_action='submit' then
   if now()<s.started_at+interval '30 seconds' then raise exception 'STUDY_TOO_FAST'; end if;
   if jsonb_typeof(p_answers)<>'object' or p_answers is null then raise exception 'INVALID_ANSWERS'; end if;
   for q in select * from jsonb_array_elements(s.questions) loop
     if p_answers->>(q->>'id')=(q->>'answer') then correct:=correct+1; end if;
   end loop;
   score:=correct*100/jsonb_array_length(s.questions);
   v_result:=jsonb_build_object('score',score,'correct',correct,'total',jsonb_array_length(s.questions));
   insert into public.studymind_reward_events(user_id,source_key,kind,topic,score)
    values(p_user,'quiz:'||s.id,'quiz',s.topic,score) on conflict do nothing;
   update public.studymind_reward_sessions set finished_at=now(),result=v_result where id=s.id;
   return v_result;
 elsif s.kind='review' then
   q:=s.questions->s.review_index;
   if p_action='reveal' then
     if now()<s.card_started_at+interval '3 seconds' then raise exception 'STUDY_TOO_FAST'; end if;
     update public.studymind_reward_sessions set revealed_at=coalesce(revealed_at,now()) where id=s.id;
     return jsonb_build_object('back',q->'options'->(q->>'answer')::int);
   elsif p_action='review' then
     if s.revealed_at is null or now()<s.revealed_at+interval '2 seconds' then raise exception 'STUDY_TOO_FAST'; end if;
     -- A bank card counts once per user per Vietnam day, even across different sessions.
     insert into public.studymind_reward_events(user_id,source_key,kind,topic)
     values(p_user,'card:'||p_user||':'||(q->>'id')||':'||(now() at time zone 'Asia/Ho_Chi_Minh')::date,'review',s.topic)
     on conflict do nothing;
     v_result:=jsonb_build_object('reviewed',s.review_index+1,'total',jsonb_array_length(s.questions));
     update public.studymind_reward_sessions set review_index=review_index+1,card_started_at=now(),revealed_at=null,
      finished_at=case when review_index+1>=jsonb_array_length(questions) then now() else null end,
      result=case when review_index+1>=jsonb_array_length(questions) then v_result else null end where id=s.id;
     return v_result;
   end if;
 elsif s.kind='focus' then
   if p_action='heartbeat' then
     elapsed:=extract(epoch from now()-s.last_heartbeat)::int;
     if elapsed<40 then return jsonb_build_object('seconds',s.focus_seconds); end if;
     update public.studymind_reward_sessions set focus_seconds=focus_seconds+case when elapsed<=90 then least(elapsed,60) else 0 end,
      last_heartbeat=now() where id=s.id returning focus_seconds into elapsed;
     return jsonb_build_object('seconds',elapsed);
   elsif p_action='finish' then
     if s.focus_seconds<1500 or now()<s.started_at+interval '25 minutes' or
      (select count(*) from public.studymind_reward_events where user_id=p_user and kind in ('review','quiz') and created_at>=s.started_at)<2
      then raise exception 'FOCUS_NOT_READY'; end if;
     insert into public.studymind_reward_events(user_id,source_key,kind,topic) values(p_user,'focus:'||s.id,'focus','focus') on conflict do nothing;
     v_result:=jsonb_build_object('seconds',s.focus_seconds,'completed',true);
     update public.studymind_reward_sessions set finished_at=now(),result=v_result where id=s.id;
     return v_result;
   end if;
 end if;
 raise exception 'INVALID_ACTION';
end $$;

create function public.studymind_bind_referral(p_user uuid,p_code text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare inviter_id uuid; created timestamptz;
begin
 perform 1 from public.studymind_reward_wallets where user_id=p_user for update;
 if exists(select 1 from public.studymind_referrals where invitee=p_user) then raise exception 'REFERRAL_ALREADY_BOUND'; end if;
 select user_id into inviter_id from public.studymind_referral_codes where code=upper(trim(p_code));
 if inviter_id is null or inviter_id=p_user then raise exception 'INVALID_REFERRAL'; end if;
 select created_at into created from auth.users where id=p_user;
 if created<now()-interval '7 days' or exists(select 1 from public.studymind_reward_events where user_id=p_user)
   then raise exception 'REFERRAL_TOO_LATE'; end if;
 insert into public.studymind_referrals(inviter,invitee) values(inviter_id,p_user);
 return jsonb_build_object('status','learning');
end $$;

create function public.studymind_process_referrals()
returns integer language plpgsql security definer set search_path=public as $$
declare r public.studymind_referrals; j1 public.studymind_reward_jobs; j2 public.studymind_reward_jobs; processed integer:=0;
begin
 for r in select * from public.studymind_referrals where status in ('learning','waiting','queued') order by created_at for update skip locked limit 100 loop
   if r.status='queued' then
     if (select count(*) from public.studymind_reward_jobs where id in (r.inviter_job,r.invitee_job) and status='confirmed')=2 then
       update public.studymind_referrals set status='rewarded' where id=r.id;
     end if;
     continue;
   end if;
   if r.status='learning' then
     if exists(select 1 from auth.users where id=r.invitee and email_confirmed_at is not null)
      and (select count(distinct (created_at at time zone 'Asia/Ho_Chi_Minh')::date) from public.studymind_reward_events where user_id=r.invitee and created_at>=r.created_at and created_at<=r.created_at+interval '7 days')>=2
      and (select count(*) from public.studymind_reward_events where user_id=r.invitee and kind='review' and created_at>=r.created_at and created_at<=r.created_at+interval '7 days')>=20
      and exists(select 1 from public.studymind_reward_events where user_id=r.invitee and kind='quiz' and score>=80 and created_at>=r.created_at and created_at<=r.created_at+interval '7 days') then
       update public.studymind_referrals set status='waiting',qualified_at=now() where id=r.id;
     elsif now()>r.created_at+interval '7 days' then
       update public.studymind_referrals set status='expired',reason='Chưa hoàn thành điều kiện trong 7 ngày.' where id=r.id;
     end if;
   elsif r.qualified_at<=now()-interval '48 hours' then
     -- Lock both wallets in UUID order, matching all other reward operations.
     perform 1 from public.studymind_reward_wallets where user_id in (r.inviter,r.invitee) order by user_id for update;
     if (select count(*) from public.studymind_referrals where inviter=r.inviter and status in ('queued','rewarded')
       and scheduled_at >= date_trunc('month',now() at time zone 'Asia/Ho_Chi_Minh') at time zone 'Asia/Ho_Chi_Minh')>=3 then
       update public.studymind_referrals set status='limited',reason='Đã đạt giới hạn 3 lượt thưởng trong tháng.' where id=r.id;
       continue;
     end if;
     if (select count(*) from public.studymind_reward_wallets where user_id in (r.inviter,r.invitee) and address is not null)<2 then continue; end if;
     j1:=public.studymind_queue_token_job(r.inviter,'mint',200,0,'referral-inviter:'||r.id,'Giới thiệu bạn học thành công');
     j2:=public.studymind_queue_token_job(r.invitee,'mint',30,0,'referral-invitee:'||r.id,'Thưởng bạn học mới');
     update public.studymind_referrals set status='queued',scheduled_at=now(),inviter_job=j1.id,invitee_job=j2.id where id=r.id;
     processed:=processed+1;
   end if;
 end loop;
 return processed;
end $$;

create function public.studymind_reward_worker_lease(p_owner uuid,p_release boolean default false)
returns boolean language plpgsql security definer set search_path=public as $$
begin
 if p_release then
   update public.studymind_reward_worker_lock set until_at=now(),owner=null where id and owner=p_owner;
 else
   update public.studymind_reward_worker_lock set owner=p_owner,until_at=now()+interval '120 seconds'
    where id and (owner is null or until_at<=now() or owner=p_owner);
 end if;
 return found;
end $$;

-- All reward functions are internal; authenticated users cannot forge evidence/settlement.
do $$ declare f record; begin
 for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public' and p.proname like 'studymind_%' and p.proname in
 ('studymind_queue_token_job','studymind_missions','studymind_claim_mission','studymind_queue_redemption',
 'studymind_settle_token_job','studymind_start_reward_session','studymind_reward_session_step',
 'studymind_bind_referral','studymind_process_referrals','studymind_reward_worker_lease') loop
   execute format('revoke all on function %s from public,anon,authenticated',f.sig);
   execute format('grant execute on function %s to service_role',f.sig);
 end loop;
end $$;
commit;
