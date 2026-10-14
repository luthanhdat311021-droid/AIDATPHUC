-- StudyMind native app without Vercel: files go to Supabase Storage, AI runs in the "studymind-api" Edge Function.
-- Chạy 1 lần trong SQL Editor (sau 002).
begin;

-- Progress of a lesson being built in steps (start → analyze parts → finish); null once the lesson is ready.
alter table public.learning_sessions add column if not exists processing jsonb;

-- Uploaded source files (private, ≤ 50 MB); the Edge Function deletes each file once its text is extracted.
insert into storage.buckets (id, name, public, file_size_limit)
values ('study-uploads', 'study-uploads', false, 52428800)
on conflict (id) do update set public = false, file_size_limit = 52428800;

-- Profile pictures (public read so the URL can be shown anywhere, ≤ 5 MB).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = true, file_size_limit = 5242880;

-- Each user may only touch objects inside a folder named after their own user id: "<uid>/file.pdf".
drop policy if exists study_uploads_insert_own on storage.objects;
create policy study_uploads_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'study-uploads' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists study_uploads_select_own on storage.objects;
create policy study_uploads_select_own on storage.objects for select to authenticated
  using (bucket_id = 'study-uploads' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists study_uploads_delete_own on storage.objects;
create policy study_uploads_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'study-uploads' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists avatars_select_own on storage.objects;
create policy avatars_select_own on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

commit;
