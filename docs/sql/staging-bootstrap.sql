-- ONLY for a new, empty StudyMind STAGING project.
-- Run once, before migrations 001 and 002. Supabase already provides auth/storage.
-- No real users or production data are copied.
begin;
do $$ begin
  if to_regclass('public.learning_sessions') is not null then
    raise exception 'learning_sessions already exists. Skip bootstrap; inspect the existing staging schema instead.';
  end if;
end $$;

create table public.learning_sessions (
  id text primary key,
  title text,
  input_type text,
  source_name text,
  structured_note jsonb not null default '{}'::jsonb,
  mindmap jsonb not null default '{}'::jsonb,
  flashcards jsonb not null default '[]'::jsonb,
  quiz jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.learning_sessions enable row level security;
revoke all on public.learning_sessions from anon;
grant select,insert,update,delete on public.learning_sessions to authenticated;
grant all on public.learning_sessions to service_role;
-- Migration 001 adds owner-scoped policies; until then RLS denies user access.
commit;
