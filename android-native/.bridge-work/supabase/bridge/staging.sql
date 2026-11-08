-- Apply ONLY to urnlxgyqmvcbkonysizy after migrations 002..009. Includes the biometric tables from 011.
-- Bridge identities have no email/password/identity provider and are permanently banned from login.
begin;


-- ───────────── Sinh trắc: chỉ lưu khóa công khai, không có dữ liệu khuôn mặt / vân tay ─────────────
-- RLS bật và không có policy: chỉ backend (service_role) đọc ghi được.
create table if not exists public.studymind_biometric_keys (
  user_id uuid primary key references auth.users(id) on delete cascade,
  public_key text not null,
  active_from timestamptz not null default now(),
  created_at timestamptz not null default now()
);
alter table public.studymind_biometric_keys enable row level security;

create table if not exists public.studymind_biometric_challenges (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  nonce text not null,
  catalog_id text not null,
  catalog_version bigint not null,
  idempotency_key uuid not null,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists studymind_biometric_challenges_user_idx on public.studymind_biometric_challenges (user_id, created_at desc);
alter table public.studymind_biometric_challenges enable row level security;


revoke all on public.studymind_biometric_keys,public.studymind_biometric_challenges from public,anon,authenticated;
grant all on public.studymind_biometric_keys,public.studymind_biometric_challenges to service_role;

create table if not exists public.studymind_bridge_identities (
 source_user_id uuid primary key,
 local_user_id uuid not null unique references auth.users(id),
 created_at timestamptz not null default now()
);
create table if not exists public.studymind_bridge_nonces (
 nonce uuid primary key, created_at timestamptz not null default now()
);
create table if not exists public.studymind_bridge_grants (
 job_id uuid primary key references public.studymind_reward_jobs(id),
 source_user_id uuid not null references public.studymind_bridge_identities(source_user_id),
 days integer not null check(days between 1 and 365), chain_id bigint not null,
 token_address text not null, tx_hash text not null, log_index integer not null,
 created_at timestamptz not null default now(), delivered_at timestamptz,
 unique(chain_id,token_address,tx_hash,log_index)
);
alter table public.studymind_bridge_identities enable row level security;
alter table public.studymind_bridge_nonces enable row level security;
alter table public.studymind_bridge_grants enable row level security;
revoke all on public.studymind_bridge_identities,public.studymind_bridge_nonces,public.studymind_bridge_grants from public,anon,authenticated;
grant all on public.studymind_bridge_identities,public.studymind_bridge_nonces,public.studymind_bridge_grants to service_role;

create or replace function public.studymind_bridge_accept_nonce(p_nonce uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
 delete from public.studymind_bridge_nonces where created_at < now()-interval '10 minutes';
 insert into public.studymind_bridge_nonces(nonce) values(p_nonce);
end $$;

create or replace function public.studymind_bridge_identity(p_source_user uuid,p_created timestamptz,p_verified boolean,p_admin boolean)
returns uuid language plpgsql security definer set search_path=public as $$
declare local_id uuid;
begin
 if p_source_user is null or p_created is null or p_created>now()+interval '1 minute'
    or p_verified is null or p_admin is null then raise exception 'INVALID_IDENTITY'; end if;
 perform pg_advisory_xact_lock(hashtext('studymind-bridge:'||p_source_user::text));
 select local_user_id into local_id from public.studymind_bridge_identities where source_user_id=p_source_user;
 if local_id is null then
  local_id := gen_random_uuid();
  insert into auth.users(id,aud,role,created_at,updated_at,banned_until,email_confirmed_at,raw_app_meta_data,raw_user_meta_data)
  values(local_id,'authenticated','authenticated',p_created,now(),'infinity',
    case when p_verified then now() else null end,'{"provider":"studymind_bridge","providers":[]}'::jsonb,'{}'::jsonb);
  insert into public.studymind_bridge_identities(source_user_id,local_user_id) values(p_source_user,local_id);
 end if;
 update auth.users set email_confirmed_at=case when p_verified then coalesce(email_confirmed_at,now()) else null end,
   banned_until='infinity',updated_at=now() where id=local_id;
 insert into public.studymind_accounts(id,role) values(local_id,case when p_admin then 'admin' else 'user' end)
 on conflict(id) do update set role=excluded.role,updated_at=now();
 return local_id;
end $$;

create or replace function public.studymind_bridge_queue_grant()
returns trigger language plpgsql security definer set search_path=public as $$
begin
 if new.kind='burn' and new.status='confirmed' and old.status is distinct from 'confirmed' then
  insert into public.studymind_bridge_grants(job_id,source_user_id,days,chain_id,token_address,tx_hash,log_index)
  select new.id,i.source_user_id,new.days,new.chain_id,new.token_address,new.tx_hash,new.log_index
  from public.studymind_bridge_identities i where i.local_user_id=new.user_id
  on conflict(job_id) do nothing;
 end if;
 return new;
end $$;
drop trigger if exists studymind_bridge_settled on public.studymind_reward_jobs;
create trigger studymind_bridge_settled after update of status on public.studymind_reward_jobs
for each row execute function public.studymind_bridge_queue_grant();
revoke all on function public.studymind_bridge_accept_nonce(uuid),public.studymind_bridge_identity(uuid,timestamptz,boolean,boolean),public.studymind_bridge_queue_grant() from public,anon,authenticated;
grant execute on function public.studymind_bridge_accept_nonce(uuid),public.studymind_bridge_identity(uuid,timestamptz,boolean,boolean) to service_role;
commit;
