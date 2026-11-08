-- Staging only. Store the existing REWARDS_WORKER_SECRET in Vault first as studymind_rewards_worker.
begin;
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;
create or replace function public.studymind_tick_reward_worker()
returns bigint language plpgsql security definer set search_path='' as $$
declare worker_secret text; request_id bigint;
begin
 if not exists(select 1 from public.studymind_reward_jobs where status in ('queued','prepared','submitted'))
 and not exists(select 1 from public.studymind_bridge_grants where delivered_at is null)
 and not exists(select 1 from public.studymind_referrals where status in ('learning','waiting')) then return null; end if;
 select decrypted_secret into strict worker_secret from vault.decrypted_secrets where name='studymind_rewards_worker';
 if length(worker_secret)<32 then raise exception 'WORKER_SECRET_MISSING'; end if;
 select net.http_post(
  url:='https://urnlxgyqmvcbkonysizy.supabase.co/functions/v1/studymind-api/api/internal/rewards/run',
  headers:=jsonb_build_object('Content-Type','application/json','x-rewards-worker-secret',worker_secret),
  body:='{}'::jsonb,timeout_milliseconds:=120000) into request_id;
 return request_id;
end $$;
revoke all on function public.studymind_tick_reward_worker() from public,anon,authenticated;
grant execute on function public.studymind_tick_reward_worker() to service_role;
select cron.schedule('studymind-rewards-sepolia','* * * * *','select public.studymind_tick_reward_worker();');
commit;
