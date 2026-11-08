-- Xác nhận sinh trắc khi đổi StudyCoin và tìm kiếm ngữ nghĩa (RAG) trong bài học.
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

-- ───────────── RAG ─────────────
create extension if not exists vector with schema extensions;

create table if not exists public.studymind_document_chunks (
  id bigint generated always as identity primary key,
  lesson_id text not null references public.learning_sessions(id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  chunk_index integer not null,
  content text not null,
  embedding extensions.vector(768) not null,
  created_at timestamptz not null default now(),
  unique (lesson_id, chunk_index)
);

alter table public.studymind_document_chunks enable row level security;
drop policy if exists studymind_document_chunks_owner on public.studymind_document_chunks;
create policy studymind_document_chunks_owner on public.studymind_document_chunks
  for all to authenticated
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.learning_sessions l where l.id = lesson_id and l.user_id = auth.uid())
  );

-- Mỗi tìm kiếm chỉ quét các đoạn của một bài học, nên so sánh trực tiếp là đủ nhanh và chính xác.
-- security invoker: RLS của người gọi vẫn áp dụng.
create or replace function public.studymind_match_chunks(p_lesson text, p_embedding extensions.vector(768), p_count integer default 6)
returns table (chunk_index integer, content text, similarity double precision)
language sql stable security invoker set search_path = public, extensions as $$
  select c.chunk_index, c.content, 1 - (c.embedding <=> p_embedding)
  from public.studymind_document_chunks c
  where c.lesson_id = p_lesson
  order by c.embedding <=> p_embedding
  limit least(greatest(p_count, 1), 20);
$$;

revoke all on function public.studymind_match_chunks(text, extensions.vector, integer) from public, anon;
grant execute on function public.studymind_match_chunks(text, extensions.vector, integer) to authenticated, service_role;

commit;
