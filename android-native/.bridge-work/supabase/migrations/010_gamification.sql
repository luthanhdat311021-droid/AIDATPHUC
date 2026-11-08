-- Chuỗi ngày, bộ đếm và huy hiệu lưu theo tài khoản, thay cho localStorage / SharedPreferences.
-- Người dùng chỉ được đọc; mọi thay đổi đi qua backend bằng service_role.
begin;

create table if not exists public.studymind_gamification (
  user_id uuid primary key references auth.users(id) on delete cascade,
  streak integer not null default 0,
  last_active_date date,
  pomodoro_sessions integer not null default 0,
  last_pomodoro_at timestamptz,
  cards_reviewed integer not null default 0,
  perfect_quizzes integer not null default 0,
  badges text[] not null default '{}',
  frozen_date date,
  freeze_week text,
  updated_at timestamptz not null default now()
);

alter table public.studymind_gamification enable row level security;
drop policy if exists studymind_gamification_select_own on public.studymind_gamification;
create policy studymind_gamification_select_own on public.studymind_gamification
  for select to authenticated using (user_id = auth.uid());
revoke insert, update, delete on public.studymind_gamification from anon, authenticated;

-- Đọc một ngày từ dữ liệu cục bộ cũ; giá trị hỏng hoặc ở tương lai được bỏ qua.
create or replace function public.studymind_safe_past_date(p_value text, p_today date)
returns date language plpgsql immutable set search_path = public as $$
declare v date;
begin
  v := p_value::date;
  return case when v <= p_today then v end;
exception when others then
  return null;
end $$;

-- Đọc một số nguyên trong khoảng cho phép từ dữ liệu cục bộ cũ.
create or replace function public.studymind_safe_count(p_value text, p_max integer)
returns integer language plpgsql immutable set search_path = public as $$
begin
  return least(greatest(coalesce(p_value::integer, 0), 0), p_max);
exception when others then
  return 0;
end $$;

-- p_kind: open | pomodoro | card | quiz_perfect | activity | freeze.
-- p_import: dữ liệu cục bộ, chỉ nhận một lần khi tài khoản chưa có bản ghi.
create or replace function public.studymind_gamification_apply(p_user uuid, p_kind text, p_import jsonb default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_today date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
  v_week text := to_char(v_today, 'IYYY-IW');
  v_known text[] := array['first_step', 'streak_3', 'streak_7', 'pomodoro_master', 'card_master', 'quiz_ace'];
  r public.studymind_gamification;
  v_before text[];
  v_earned text[];
begin
  if p_kind not in ('open', 'pomodoro', 'card', 'quiz_perfect', 'activity', 'freeze') then
    raise exception 'Unknown gamification event: %', p_kind;
  end if;

  insert into public.studymind_gamification
    (user_id, streak, last_active_date, pomodoro_sessions, cards_reviewed, perfect_quizzes, badges)
  values (
    p_user,
    public.studymind_safe_count(p_import->>'streak', 3650),
    public.studymind_safe_past_date(p_import->>'lastActiveDate', v_today),
    public.studymind_safe_count(p_import->>'pomodoroSessions', 100000),
    public.studymind_safe_count(p_import->>'cardsReviewed', 1000000),
    public.studymind_safe_count(p_import->>'perfectQuizzes', 100000),
    coalesce((
      select array_agg(b) from jsonb_array_elements_text(
        case when jsonb_typeof(p_import->'unlockedBadgeIds') = 'array' then p_import->'unlockedBadgeIds' else '[]'::jsonb end
      ) b where b = any(v_known)
    ), '{}')
  )
  on conflict (user_id) do nothing;

  select * into r from public.studymind_gamification where user_id = p_user for update;
  v_before := r.badges;

  if r.last_active_date is distinct from v_today then
    r.streak := case
      when r.last_active_date = v_today - 1 then r.streak + 1
      when r.last_active_date = v_today - 2 and r.frozen_date = v_today - 1 then r.streak + 1
      else 1
    end;
    r.last_active_date := v_today;
  end if;

  if p_kind = 'card' then
    r.cards_reviewed := r.cards_reviewed + 1;
  elsif p_kind = 'quiz_perfect' then
    r.perfect_quizzes := r.perfect_quizzes + 1;
  elsif p_kind = 'pomodoro' and (r.last_pomodoro_at is null or now() - r.last_pomodoro_at >= interval '5 minutes') then
    r.pomodoro_sessions := r.pomodoro_sessions + 1;
    r.last_pomodoro_at := now();
  elsif p_kind = 'freeze' and r.freeze_week is distinct from v_week then
    r.freeze_week := v_week;
    r.frozen_date := v_today + 1;
  end if;

  select array_agg(id) into v_earned from unnest(v_known) id
  where id = any(r.badges)
     or id = 'first_step'
     or (id = 'streak_3' and r.streak >= 3)
     or (id = 'streak_7' and r.streak >= 7)
     or (id = 'pomodoro_master' and r.pomodoro_sessions >= 4)
     or (id = 'card_master' and r.cards_reviewed >= 20)
     or (id = 'quiz_ace' and r.perfect_quizzes >= 1);

  update public.studymind_gamification set
    streak = r.streak, last_active_date = r.last_active_date,
    pomodoro_sessions = r.pomodoro_sessions, last_pomodoro_at = r.last_pomodoro_at,
    cards_reviewed = r.cards_reviewed, perfect_quizzes = r.perfect_quizzes,
    badges = v_earned, frozen_date = r.frozen_date, freeze_week = r.freeze_week, updated_at = now()
  where user_id = p_user;

  return jsonb_build_object(
    'streak', r.streak,
    'lastActiveDate', to_char(r.last_active_date, 'YYYY-MM-DD'),
    'pomodoroSessions', r.pomodoro_sessions,
    'cardsReviewed', r.cards_reviewed,
    'perfectQuizzes', r.perfect_quizzes,
    'unlockedBadgeIds', to_jsonb(v_earned),
    'newBadgeIds', to_jsonb(coalesce((select array_agg(b) from unnest(v_earned) b where not b = any(v_before)), '{}'::text[])),
    'frozenDate', to_char(r.frozen_date, 'YYYY-MM-DD'),
    'canFreeze', r.freeze_week is distinct from v_week
  );
end $$;

revoke all on function public.studymind_gamification_apply(uuid, text, jsonb) from public, anon, authenticated;
grant execute on function public.studymind_gamification_apply(uuid, text, jsonb) to service_role;

commit;
