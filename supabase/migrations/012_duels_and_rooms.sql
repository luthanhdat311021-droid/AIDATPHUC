-- Đấu trường 1v1 và phòng học nhóm. Không cấp quyền bảng trực tiếp: mọi truy cập qua hàm bên dưới,
-- nên đáp án của trận đấu không bao giờ tới người chơi trước khi họ nộp bài.
begin;

create or replace function public.studymind_new_code()
returns text language sql volatile set search_path = public as $$
  select upper(substr(md5(gen_random_uuid()::text), 1, 8))
$$;

create table if not exists public.studymind_duels (
  code text primary key,
  title text not null,
  questions jsonb not null,
  creator uuid not null references auth.users(id) on delete cascade,
  creator_name text not null,
  creator_score integer,
  creator_ms integer,
  opponent uuid references auth.users(id) on delete cascade,
  opponent_name text,
  opponent_score integer,
  opponent_ms integer,
  created_at timestamptz not null default now()
);
alter table public.studymind_duels enable row level security;

create or replace function public.studymind_duel_view(d public.studymind_duels, p_user uuid)
returns jsonb language plpgsql stable set search_path = public as $$
declare
  played boolean := (p_user = d.creator and d.creator_score is not null) or (p_user = d.opponent and d.opponent_score is not null);
begin
  return jsonb_build_object(
    'code', d.code, 'title', d.title, 'total', jsonb_array_length(d.questions), 'played', played,
    'isCreator', p_user = d.creator,
    'creatorName', d.creator_name, 'creatorScore', d.creator_score, 'creatorMs', d.creator_ms,
    'opponentName', d.opponent_name, 'opponentScore', d.opponent_score, 'opponentMs', d.opponent_ms,
    'questions', case when played then d.questions else (
      select coalesce(jsonb_agg(q - 'correctIndex' - 'explanation'), '[]'::jsonb) from jsonb_array_elements(d.questions) q
    ) end
  );
end $$;

create or replace function public.studymind_duel_create(p_title text, p_questions jsonb, p_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare d public.studymind_duels;
begin
  if auth.uid() is null then raise exception 'Vui lòng đăng nhập.'; end if;
  insert into public.studymind_duels(code, title, questions, creator, creator_name)
  values (public.studymind_new_code(), p_title, p_questions, auth.uid(), p_name) returning * into d;
  return public.studymind_duel_view(d, auth.uid());
end $$;

create or replace function public.studymind_duel_get(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare d public.studymind_duels;
begin
  select * into d from public.studymind_duels where code = upper(p_code);
  if not found then raise exception 'Không tìm thấy trận đấu với mã này.'; end if;
  if auth.uid() is null then raise exception 'Vui lòng đăng nhập.'; end if;
  return public.studymind_duel_view(d, auth.uid());
end $$;

-- shortcut: thời gian ms do app gửi nên có thể bị sửa, chỉ dùng phân thắng khi hòa điểm; cần tính ở server nếu trận đấu có thưởng
create or replace function public.studymind_duel_submit(p_code text, p_answers jsonb, p_ms integer, p_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  d public.studymind_duels;
  v_score integer;
begin
  if auth.uid() is null then raise exception 'Vui lòng đăng nhập.'; end if;
  select * into d from public.studymind_duels where code = upper(p_code) for update;
  if not found then raise exception 'Không tìm thấy trận đấu với mã này.'; end if;
  select count(*) into v_score from jsonb_array_elements(d.questions) q
    where p_answers -> (q->>'id') = q -> 'correctIndex';

  if auth.uid() = d.creator then
    if d.creator_score is not null then raise exception 'Bạn đã nộp bài trận này.'; end if;
    update public.studymind_duels set creator_score = v_score, creator_ms = greatest(p_ms, 0) where code = d.code returning * into d;
  elsif d.opponent is null or d.opponent = auth.uid() then
    if d.opponent_score is not null then raise exception 'Bạn đã nộp bài trận này.'; end if;
    update public.studymind_duels set opponent = auth.uid(), opponent_name = p_name, opponent_score = v_score, opponent_ms = greatest(p_ms, 0)
      where code = d.code returning * into d;
  else
    raise exception 'Trận đấu này đã đủ hai người.';
  end if;
  return public.studymind_duel_view(d, auth.uid());
end $$;

create table if not exists public.studymind_rooms (
  code text primary key,
  started_at timestamptz,
  participants uuid[] not null default '{}',
  sessions_done integer not null default 0,
  streak integer not null default 0,
  last_done_day date,
  last_result text,
  broken_by text,
  created_at timestamptz not null default now()
);
create table if not exists public.studymind_room_members (
  code text not null references public.studymind_rooms(code) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  last_seen timestamptz not null default now(),
  primary key (code, user_id)
);
alter table public.studymind_rooms enable row level security;
alter table public.studymind_room_members enable row level security;

-- Đánh giá phiên đang chạy mỗi lần có người báo còn online: ai vắng quá 90 giây là phiên hỏng.
create or replace function public.studymind_room_state(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  r public.studymind_rooms;
  v_absent text;
  v_today date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
begin
  select * into r from public.studymind_rooms where code = p_code for update;
  if r.started_at is not null then
    select m.name into v_absent from public.studymind_room_members m
      where m.code = p_code and m.user_id = any(r.participants) and m.last_seen < now() - interval '90 seconds' limit 1;
    if v_absent is not null or (select count(*) from public.studymind_room_members m where m.code = p_code and m.user_id = any(r.participants)) < cardinality(r.participants) then
      update public.studymind_rooms set started_at = null, last_result = 'broken', broken_by = coalesce(v_absent, 'Một thành viên') where code = p_code returning * into r;
    elsif now() >= r.started_at + interval '25 minutes' then
      update public.studymind_rooms set started_at = null, last_result = 'done', broken_by = null, sessions_done = sessions_done + 1,
        streak = case when last_done_day = v_today then streak when last_done_day = v_today - 1 then streak + 1 else 1 end,
        last_done_day = v_today where code = p_code returning * into r;
    end if;
  end if;
  return jsonb_build_object(
    'code', r.code, 'running', r.started_at is not null,
    'secondsLeft', case when r.started_at is null then 0 else greatest(0, extract(epoch from r.started_at + interval '25 minutes' - now())::integer) end,
    'sessionsDone', r.sessions_done,
    -- Chuỗi đã đứt nếu hôm qua và hôm nay đều chưa hoàn thành phiên nào
    'streak', case when r.last_done_day >= v_today - 1 then r.streak else 0 end,
    'lastResult', r.last_result, 'brokenBy', r.broken_by,
    'members', (select coalesce(jsonb_agg(jsonb_build_object('name', m.name, 'active', m.last_seen >= now() - interval '90 seconds') order by m.name), '[]'::jsonb)
                from public.studymind_room_members m where m.code = p_code));
end $$;
revoke execute on function public.studymind_room_state(text) from public, anon, authenticated;

create or replace function public.studymind_room_create(p_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_code text := public.studymind_new_code();
begin
  if auth.uid() is null then raise exception 'Vui lòng đăng nhập.'; end if;
  insert into public.studymind_rooms(code) values (v_code);
  insert into public.studymind_room_members(code, user_id, name) values (v_code, auth.uid(), p_name);
  return public.studymind_room_state(v_code);
end $$;

create or replace function public.studymind_room_join(p_code text, p_name text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_code text := upper(p_code);
begin
  if auth.uid() is null then raise exception 'Vui lòng đăng nhập.'; end if;
  perform 1 from public.studymind_rooms where code = v_code for update;
  if not found then raise exception 'Không tìm thấy phòng với mã này.'; end if;
  if (select count(*) from public.studymind_room_members where code = v_code and user_id <> auth.uid()) >= 8 then
    raise exception 'Phòng đã đủ 8 người.';
  end if;
  insert into public.studymind_room_members(code, user_id, name) values (v_code, auth.uid(), p_name)
    on conflict (code, user_id) do update set name = excluded.name, last_seen = now();
  return public.studymind_room_state(v_code);
end $$;

-- Trả về null khi không còn là thành viên, để app quên mã phòng thay vì báo lỗi mãi.
create or replace function public.studymind_room_beat(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  update public.studymind_room_members set last_seen = now() where code = upper(p_code) and user_id = auth.uid();
  if not found then return null; end if;
  return public.studymind_room_state(upper(p_code));
end $$;

create or replace function public.studymind_room_start(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_code text := upper(p_code);
begin
  update public.studymind_room_members set last_seen = now() where code = v_code and user_id = auth.uid();
  if not found then raise exception 'Bạn không ở trong phòng này.'; end if;
  perform public.studymind_room_state(v_code);
  -- Chỉ tính những người đang online lúc bắt đầu; người vào sau không làm hỏng phiên
  update public.studymind_rooms set started_at = now(), last_result = null, broken_by = null,
    participants = (select array_agg(user_id) from public.studymind_room_members where code = v_code and last_seen >= now() - interval '90 seconds')
    where code = v_code and started_at is null;
  return public.studymind_room_state(v_code);
end $$;

create or replace function public.studymind_room_leave(p_code text)
returns void language plpgsql security definer set search_path = public as $$
begin
  delete from public.studymind_room_members where code = upper(p_code) and user_id = auth.uid();
end $$;

revoke all on public.studymind_duels, public.studymind_rooms, public.studymind_room_members from anon, authenticated;
revoke execute on function public.studymind_duel_view(public.studymind_duels, uuid) from public, anon, authenticated;
revoke execute on function public.studymind_duel_create(text, jsonb, text), public.studymind_duel_get(text), public.studymind_duel_submit(text, jsonb, integer, text),
  public.studymind_room_create(text), public.studymind_room_join(text, text), public.studymind_room_beat(text), public.studymind_room_start(text), public.studymind_room_leave(text)
  from public, anon;
grant execute on function public.studymind_duel_create(text, jsonb, text), public.studymind_duel_get(text), public.studymind_duel_submit(text, jsonb, integer, text),
  public.studymind_room_create(text), public.studymind_room_join(text, text), public.studymind_room_beat(text), public.studymind_room_start(text), public.studymind_room_leave(text)
  to authenticated;

commit;
