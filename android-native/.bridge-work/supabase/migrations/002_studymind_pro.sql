-- StudyMind Pro: hồ sơ (vai trò + gói), đơn thanh toán SePay, nhật ký giao dịch, hạn mức AI theo ngày.
-- Chạy 1 lần: Supabase Dashboard → SQL Editor → dán toàn bộ file → Run (sau 001_lesson_ownership.sql).
--
-- Bảng "profiles" và trigger "mammam_auth_user_created" trong project này thuộc app khác
-- (role customer/admin), nên StudyMind dùng bảng riêng studymind_accounts và không đụng vào chúng.
--
-- Phân quyền:
--   • Người dùng chỉ ĐỌC được hồ sơ và đơn của chính mình; không có policy ghi nào cho client,
--     nên không ai tự nâng mình lên Pro/admin bằng anon key.
--   • Mọi thay đổi gói / vai trò / đơn hàng đi qua backend bằng SUPABASE_SERVICE_ROLE_KEY
--     (bỏ qua RLS) hoặc qua các hàm SECURITY DEFINER bên dưới, chỉ service_role được gọi.
--
-- Cấp quyền quản trị cho một tài khoản (tự chạy riêng, thay email):
--   update public.studymind_accounts set role = 'admin' where email = 'ban@example.com';
begin;

-- ───────────────────────────── Hồ sơ người dùng ─────────────────────────────
create table if not exists public.studymind_accounts (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  role text not null default 'user' check (role in ('user', 'admin')),
  plan text not null default 'free' check (plan in ('free', 'pro')),
  pro_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists studymind_accounts_email_idx on public.studymind_accounts (lower(email));

-- Mỗi tài khoản mới tự có hồ sơ "free / user"
create or replace function public.studymind_handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.studymind_accounts (id, email) values (new.id, new.email)
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists studymind_auth_user_created on auth.users;
create trigger studymind_auth_user_created
  after insert on auth.users
  for each row execute function public.studymind_handle_new_user();

-- Tài khoản đã có từ trước
insert into public.studymind_accounts (id, email)
select id, email from auth.users
on conflict (id) do nothing;

alter table public.studymind_accounts enable row level security;
drop policy if exists studymind_accounts_read_own on public.studymind_accounts;
create policy studymind_accounts_read_own on public.studymind_accounts
  for select to authenticated
  using (id = auth.uid());

-- ───────────────────────────── Đơn thanh toán ─────────────────────────────
create table if not exists public.payment_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  code text not null unique,                 -- nội dung chuyển khoản, ví dụ SM8F3K2A
  plan_id text not null,                     -- pro_1m (xem PLANS trong backend/services/billingService.js)
  days int not null check (days > 0),
  amount bigint not null check (amount > 0), -- VND, do backend định giá, không lấy từ client
  status text not null default 'pending' check (status in ('pending', 'paid', 'expired', 'cancelled')),
  sepay_transaction_id bigint,
  paid_amount bigint,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  paid_at timestamptz
);

create index if not exists payment_orders_user_idx on public.payment_orders (user_id, created_at desc);

alter table public.payment_orders enable row level security;
drop policy if exists payment_orders_read_own on public.payment_orders;
create policy payment_orders_read_own on public.payment_orders
  for select to authenticated
  using (user_id = auth.uid());

-- ───────────────────── Giao dịch SePay (chỉ backend đọc/ghi) ─────────────────────
create table if not exists public.payment_transactions (
  sepay_id bigint primary key,               -- id giao dịch SePay → chống xử lý trùng khi webhook gửi lại
  order_id uuid references public.payment_orders(id) on delete set null,
  amount bigint not null,
  content text,
  gateway text,
  reference_code text,
  transaction_date text,
  result text not null,                      -- paid | already_paid | underpaid | no_order | ignored
  raw jsonb not null,
  received_at timestamptz not null default now()
);

alter table public.payment_transactions enable row level security;
-- Không có policy: chỉ service_role (bỏ qua RLS) truy cập được.

-- ───────────────────── Hạn mức AI theo ngày (giờ Việt Nam) ─────────────────────
create table if not exists public.ai_usage_daily (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  kind text not null,                        -- generate | chat
  count int not null default 0,
  primary key (user_id, day, kind)
);

alter table public.ai_usage_daily enable row level security;
drop policy if exists ai_usage_read_own on public.ai_usage_daily;
create policy ai_usage_read_own on public.ai_usage_daily
  for select to authenticated
  using (user_id = auth.uid());

-- Trừ 1 lượt nếu còn hạn mức; trả về số lượt đã dùng sau khi trừ, hoặc -1 nếu đã hết. Nguyên tử.
create or replace function public.consume_ai_quota(p_user uuid, p_kind text, p_limit int)
returns int language plpgsql security definer set search_path = public as $$
declare
  v_day date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
  v_count int;
begin
  insert into public.ai_usage_daily (user_id, day, kind, count) values (p_user, v_day, p_kind, 0)
  on conflict (user_id, day, kind) do nothing;

  update public.ai_usage_daily
     set count = count + 1
   where user_id = p_user and day = v_day and kind = p_kind and count < p_limit
  returning count into v_count;

  return coalesce(v_count, -1);
end $$;

-- Ghi nhận thanh toán: khóa đơn, kiểm tra số tiền, cộng dồn hạn Pro. Nguyên tử, chống trùng.
create or replace function public.confirm_payment(p_code text, p_amount bigint, p_sepay_id bigint)
returns text language plpgsql security definer set search_path = public as $$
declare
  o public.payment_orders%rowtype;
begin
  select * into o from public.payment_orders where code = upper(p_code) for update;
  if not found then return 'no_order'; end if;
  if o.status = 'paid' then return 'already_paid'; end if;
  if p_amount < o.amount then return 'underpaid'; end if;

  -- Đơn "expired"/"cancelled" vẫn được ghi nhận: tiền đã về thì người dùng phải nhận được Pro
  update public.payment_orders
     set status = 'paid', paid_at = now(), sepay_transaction_id = p_sepay_id, paid_amount = p_amount
   where id = o.id;

  update public.studymind_accounts
     set plan = 'pro',
         pro_until = greatest(coalesce(pro_until, now()), now()) + make_interval(days => o.days),
         updated_at = now()
   where id = o.user_id;

  return 'paid';
end $$;

-- Quản trị viên cấp / thu hồi Pro thủ công (days <= 0 → thu hồi)
create or replace function public.admin_set_pro(p_user uuid, p_days int)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_days <= 0 then
    update public.studymind_accounts set plan = 'free', pro_until = null, updated_at = now() where id = p_user;
  else
    update public.studymind_accounts
       set plan = 'pro',
           pro_until = greatest(coalesce(pro_until, now()), now()) + make_interval(days => p_days),
           updated_at = now()
     where id = p_user;
  end if;
end $$;

revoke all on function public.consume_ai_quota(uuid, text, int) from public, anon, authenticated;
revoke all on function public.confirm_payment(text, bigint, bigint) from public, anon, authenticated;
revoke all on function public.admin_set_pro(uuid, int) from public, anon, authenticated;
grant execute on function public.consume_ai_quota(uuid, text, int) to service_role;
grant execute on function public.confirm_payment(text, bigint, bigint) to service_role;
grant execute on function public.admin_set_pro(uuid, int) to service_role;

commit;
