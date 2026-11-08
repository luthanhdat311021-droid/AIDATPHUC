begin;
create function public.studymind_reward_totals()
returns jsonb language sql security definer set search_path=public as $$
 select jsonb_build_object(
  'minted',coalesce(sum(amount) filter(where kind='mint' and status='confirmed'),0),
  'burned',coalesce(sum(amount) filter(where kind='burn' and status='confirmed'),0),
  'proDays',coalesce(sum(days) filter(where kind='burn' and status='confirmed'),0),
  'pending',count(*) filter(where status in ('queued','prepared','submitted')),
  'failed',count(*) filter(where status='failed')) from public.studymind_reward_jobs
$$;
revoke all on function public.studymind_reward_totals() from public,anon,authenticated;
grant execute on function public.studymind_reward_totals() to service_role;
create function public.studymind_admin_reward_action(p_actor uuid,p_action text,p_id text,p_value integer default null,p_reason text default null)
returns jsonb language plpgsql security definer set search_path=public as $$
declare j public.studymind_reward_jobs; total_daily integer;
begin
 if not exists(select 1 from public.studymind_accounts where id=p_actor and role='admin') then raise exception 'ADMIN_REQUIRED'; end if;
 if p_action='rule_amount' then
   if p_value is null or p_value not between 1 and 1000 then raise exception 'INVALID_ADMIN_VALUE'; end if;
   perform 1 from public.studymind_reward_rules order by code for update;
   update public.studymind_reward_rules set amount=p_value,version=version+1 where code=p_id;
   if not found then raise exception 'ADMIN_TARGET_NOT_FOUND'; end if;
   select sum(amount) into total_daily from public.studymind_reward_rules where period='day' and code<>'streak' and active;
   if total_daily>45 then raise exception 'DAILY_BUDGET_EXCEEDED'; end if;
 elsif p_action='catalog_amount' then
   if p_value is null or p_value not between 1 and 10000 then raise exception 'INVALID_ADMIN_VALUE'; end if;
   update public.studymind_reward_catalog set amount=p_value,version=version+1 where id=p_id;
   if not found then raise exception 'ADMIN_TARGET_NOT_FOUND'; end if;
 elsif p_action='reject_referral' then
   if length(coalesce(p_reason,''))<5 then raise exception 'REASON_REQUIRED'; end if;
   update public.studymind_referrals set status='rejected',reason=left(p_reason,300)
    where id=p_id::uuid and status in ('learning','waiting');
   if not found then raise exception 'ADMIN_TARGET_NOT_FOUND'; end if;
 elsif p_action='retry_job' then
   select * into strict j from public.studymind_reward_jobs where id=p_id::uuid for update;
   if j.status<>'failed' or j.error_code not in ('CONTRACT_REJECTED','TRANSACTION_REVERTED') then raise exception 'RETRY_NOT_SAFE'; end if;
   update public.studymind_reward_jobs set status='queued',tx_hash=null,raw_transaction=null,nonce=null,error_code=null where id=j.id;
 else raise exception 'INVALID_ADMIN_ACTION';
 end if;
 insert into public.studymind_reward_audit(actor,action,details)
 values(p_actor,p_action,jsonb_build_object('target',p_id,'value',p_value,'reason',left(p_reason,300)));
 return jsonb_build_object('updated',true);
end $$;
revoke all on function public.studymind_admin_reward_action(uuid,text,text,integer,text) from public,anon,authenticated;
grant execute on function public.studymind_admin_reward_action(uuid,text,text,integer,text) to service_role;
commit;
