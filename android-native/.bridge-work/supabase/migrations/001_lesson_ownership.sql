-- StudyMind AI: mỗi bài học thuộc về đúng 1 tài khoản, bảo vệ bằng Row Level Security.
-- Chạy 1 lần: Supabase Dashboard → SQL Editor → dán toàn bộ file → Run.
--
-- Bài học cũ không có chủ sở hữu (user_id rỗng) nên RLS ẩn chúng khỏi MỌI tài khoản.
-- Muốn xóa hẳn chúng (không hoàn tác được), tự chạy riêng:
--   delete from public.learning_sessions where user_id is null;
begin;

alter table public.learning_sessions
  -- auth.uid() lấy từ JWT của người gọi, nên backend không thể ghi bài cho người khác
  add column if not exists user_id uuid default auth.uid() references auth.users(id) on delete cascade,
  -- Lưu đủ để mọi thao tác sau (tạo lại quiz, mở rộng sơ đồ, chat) không phụ thuộc RAM của server
  add column if not exists knowledge_base jsonb,
  add column if not exists raw_text text,
  add column if not exists quiz_history jsonb not null default '[]'::jsonb;

create index if not exists learning_sessions_user_id_idx on public.learning_sessions (user_id);

-- Gỡ mọi policy cũ (policy "cho phép tất cả" sẽ OR với policy mới và mở toang bảng)
do $$
declare p record;
begin
  for p in select policyname from pg_policies where schemaname = 'public' and tablename = 'learning_sessions' loop
    execute format('drop policy %I on public.learning_sessions', p.policyname);
  end loop;
end $$;

alter table public.learning_sessions enable row level security;

create policy learning_sessions_owner on public.learning_sessions
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

commit;
