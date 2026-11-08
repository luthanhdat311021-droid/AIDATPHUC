-- Apply ONLY to gwynjmlqymojrdlpzukn. Pro is extended once per confirmed staging burn.
begin;
create table if not exists public.studymind_bridge_receipts (
 job_id uuid primary key, user_id uuid not null references auth.users(id),
 days integer not null check(days between 1 and 365),
 chain_id bigint not null check(chain_id=11155111),
 token_address text not null check(token_address='0xa66a22aa9d891d2611e47cfe53d90ceb1e2b375d'),
 tx_hash text not null check(tx_hash ~ '^0x[0-9a-f]{64}$'),log_index integer not null check(log_index>=0),
 pro_until timestamptz,created_at timestamptz not null default now(),
 unique(chain_id,token_address,tx_hash,log_index)
);
alter table public.studymind_bridge_receipts enable row level security;
revoke all on public.studymind_bridge_receipts from public,anon,authenticated;
grant all on public.studymind_bridge_receipts to service_role;
create or replace function public.studymind_apply_bridge_grant(p_job uuid,p_user uuid,p_days integer,p_chain bigint,p_contract text,p_tx text,p_log integer)
returns jsonb language plpgsql security definer set search_path=public as $$
declare prior public.studymind_bridge_receipts; until_at timestamptz;
begin
 if p_job is null or p_user is null or p_days is null or p_days not between 1 and 365
 or p_chain is distinct from 11155111::bigint
 or p_contract is distinct from '0xa66a22aa9d891d2611e47cfe53d90ceb1e2b375d'
 or p_tx is null or p_tx !~ '^0x[0-9a-f]{64}$' or p_log is null or p_log<0 then raise exception 'INVALID_BRIDGE_GRANT'; end if;
 perform pg_advisory_xact_lock(hashtext('studymind-grant:'||p_job::text));
 select * into prior from public.studymind_bridge_receipts where job_id=p_job;
 if found then
  if prior.user_id<>p_user or prior.days<>p_days or prior.chain_id<>p_chain or prior.token_address<>p_contract
     or prior.tx_hash<>p_tx or prior.log_index<>p_log then raise exception 'BRIDGE_GRANT_CONFLICT'; end if;
  return jsonb_build_object('applied',false,'proUntil',prior.pro_until);
 end if;
 insert into public.studymind_bridge_receipts(job_id,user_id,days,chain_id,token_address,tx_hash,log_index)
 values(p_job,p_user,p_days,p_chain,p_contract,p_tx,p_log);
 insert into public.studymind_accounts(id) values(p_user) on conflict(id) do nothing;
 update public.studymind_accounts set plan='pro',
 pro_until=greatest(coalesce(pro_until,now()),now())+make_interval(days=>p_days),updated_at=now()
 where id=p_user returning pro_until into until_at;
 if until_at is null then raise exception 'ACCOUNT_NOT_FOUND'; end if;
 update public.studymind_bridge_receipts set pro_until=until_at where job_id=p_job;
 return jsonb_build_object('applied',true,'proUntil',until_at);
end $$;
revoke all on function public.studymind_apply_bridge_grant(uuid,uuid,integer,bigint,text,text,integer) from public,anon,authenticated;
grant execute on function public.studymind_apply_bridge_grant(uuid,uuid,integer,bigint,text,text,integer) to service_role;
commit;
