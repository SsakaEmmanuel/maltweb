-- Independent E-Logbook schema. Run only in a NEW, dedicated Supabase project.
-- Never run this in the main MALTWEB project.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  role text not null default 'student' check (role in ('student','field_supervisor','institution_supervisor','admin')),
  school_name text,
  student_number text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.placements (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  organization_name text not null,
  department text,
  start_date date,
  end_date date,
  field_supervisor_id uuid references public.profiles(id),
  institution_supervisor_id uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.daily_entries (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  placement_id uuid references public.placements(id) on delete set null,
  entry_date date not null,
  work_done text not null default '',
  tools_used text not null default '',
  skills_learned text not null default '',
  challenges text not null default '',
  recommendations text not null default '',
  supervisor_comments text not null default '',
  supervisor_signed_at timestamptz,
  client_updated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (student_id, entry_date)
);

create table if not exists public.weekly_summaries (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  placement_id uuid references public.placements(id) on delete set null,
  week_start date not null,
  summary text not null default '',
  skills_learned text not null default '',
  challenges text not null default '',
  supervisor_comments text not null default '',
  supervisor_signed_at timestamptz,
  client_updated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (student_id, week_start)
);

-- Assignments are explicit; students cannot appoint their own supervisors.
create table if not exists public.supervisor_assignments (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  supervisor_id uuid not null references public.profiles(id) on delete cascade,
  assignment_type text not null check (assignment_type in ('field_supervisor','institution_supervisor')),
  created_at timestamptz not null default now(),
  unique (student_id, supervisor_id, assignment_type)
);

alter table public.profiles enable row level security;
alter table public.placements enable row level security;
alter table public.daily_entries enable row level security;
alter table public.weekly_summaries enable row level security;
alter table public.supervisor_assignments enable row level security;

-- A profile is visible/editable only by its owner in the initial version.
create policy "profile owner reads own profile" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profile owner creates own profile" on public.profiles for insert to authenticated with check ((select auth.uid()) = id and role = 'student');
create policy "student profile owner updates own profile" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id and role = 'student');

-- Students own their records. Supervisor review policies are added only after
-- trusted admin-managed assignments and role provisioning are configured.
create policy "student reads own placements" on public.placements for select to authenticated using (student_id = (select auth.uid()));
create policy "student creates own placements" on public.placements for insert to authenticated with check (student_id = (select auth.uid()));
create policy "student updates own placements" on public.placements for update to authenticated using (student_id = (select auth.uid())) with check (student_id = (select auth.uid()));
create policy "student deletes own placements" on public.placements for delete to authenticated using (student_id = (select auth.uid()));

create policy "student reads own daily entries" on public.daily_entries for select to authenticated using (student_id = (select auth.uid()));
create policy "student creates own daily entries" on public.daily_entries for insert to authenticated with check (student_id = (select auth.uid()));
create policy "student updates own daily entries" on public.daily_entries for update to authenticated using (student_id = (select auth.uid())) with check (student_id = (select auth.uid()));
create policy "student deletes own daily entries" on public.daily_entries for delete to authenticated using (student_id = (select auth.uid()));

create policy "student reads own weekly summaries" on public.weekly_summaries for select to authenticated using (student_id = (select auth.uid()));
create policy "student creates own weekly summaries" on public.weekly_summaries for insert to authenticated with check (student_id = (select auth.uid()));
create policy "student updates own weekly summaries" on public.weekly_summaries for update to authenticated using (student_id = (select auth.uid())) with check (student_id = (select auth.uid()));
create policy "student deletes own weekly summaries" on public.weekly_summaries for delete to authenticated using (student_id = (select auth.uid()));

create policy "user reads assignments involving them" on public.supervisor_assignments for select to authenticated using (student_id = (select auth.uid()) or supervisor_id = (select auth.uid()));

-- IMPORTANT: never expose service_role/secret keys in website or APK.
-- Do not let users self-assign supervisor/admin roles. Configure privileged role
-- provisioning separately before enabling supervisor review and admin policies.
