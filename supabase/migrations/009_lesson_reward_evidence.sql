-- Ordinary lesson study (AI-generated flashcards and quizzes) also counts toward StudyCoin missions.
-- Evidence is written only by the trusted API (service_role) after it has checked the lesson content
-- is AI-generated; users still cannot insert events. Run after 008.
begin;

-- p_key identifies the item without the day; the Vietnam date is appended here so each lesson card
-- counts once per day and only the first quiz submission per lesson per day counts (retries do not).
create or replace function public.studymind_record_lesson_event(p_user uuid,p_kind text,p_topic text,p_key text,p_score integer default null)
returns boolean language plpgsql security definer set search_path=public as $$
declare day date := (now() at time zone 'Asia/Ho_Chi_Minh')::date; n integer;
begin
 if p_kind not in ('review','quiz') or p_key !~ '^lesson-(card|quiz):' then raise exception 'INVALID_SESSION'; end if;
 -- Serialize per learner so the reading-time gap below cannot be raced by parallel requests.
 perform pg_advisory_xact_lock(hashtext('studymind-lesson-evidence:'||p_user::text));
 -- At most one counted lesson card every 3 seconds (time to actually read a card).
 if p_kind='review' and exists(select 1 from public.studymind_reward_events where user_id=p_user and kind='review'
   and source_key like 'lesson-card:%' and created_at>now()-interval '3 seconds') then return false; end if;
 insert into public.studymind_reward_events(user_id,source_key,kind,topic,score)
 values(p_user,p_key||':'||day,p_kind,left(coalesce(nullif(trim(p_topic),''),'Bài học'),100),p_score)
 on conflict(source_key) do nothing;
 get diagnostics n = row_count;
 return n>0;
end $$;
revoke all on function public.studymind_record_lesson_event(uuid,text,text,text,integer) from public,anon,authenticated;
grant execute on function public.studymind_record_lesson_event(uuid,text,text,text,integer) to service_role;

-- Same as 005, except "Làm chủ 3 chủ đề" only counts the private question bank (source 'quiz:<session>'),
-- because lesson quizzes are shown with their answers for explanations.
create or replace function public.studymind_missions(p_user uuid)
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
      and source_key like 'quiz:%'
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

-- Same as 005, except the referral quiz requirement only counts the private question bank.
create or replace function public.studymind_process_referrals()
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
      and exists(select 1 from public.studymind_reward_events where user_id=r.invitee and kind='quiz' and score>=80 and source_key like 'quiz:%' and created_at>=r.created_at and created_at<=r.created_at+interval '7 days') then
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

-- create or replace keeps existing grants, but re-assert them for projects that ran this out of order.
revoke all on function public.studymind_missions(uuid) from public,anon,authenticated;
grant execute on function public.studymind_missions(uuid) to service_role;
revoke all on function public.studymind_process_referrals() from public,anon,authenticated;
grant execute on function public.studymind_process_referrals() to service_role;
commit;
