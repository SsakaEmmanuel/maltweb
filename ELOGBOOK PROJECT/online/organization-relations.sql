-- E-Logbook organisation-linked account and reporting upgrade.
-- Apply after schema.sql and supervisor-review.sql in the dedicated E-Logbook project.
-- This is additive and is safe for the existing E-Logbook database.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  organization_key_hash text not null unique,
  created_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now()
);
alter table public.profiles
  add column if not exists organization_id uuid references public.organizations(id) on delete set null,
  add column if not exists gender text,
  add column if not exists course text,
  add column if not exists approval_status text not null default 'approved';
alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles add constraint profiles_role_check
  check (role in ('student','field_supervisor','institution_supervisor','organization_admin','admin'));
alter table public.profiles drop constraint if exists profiles_gender_check;
alter table public.profiles add constraint profiles_gender_check
  check (gender is null or gender in ('female','male','prefer_not_to_say'));
alter table public.profiles drop constraint if exists profiles_approval_status_check;
alter table public.profiles add constraint profiles_approval_status_check
  check (approval_status in ('pending','approved','rejected'));

create table if not exists public.attendance_records (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  attendance_date date not null,
  status text not null check (status in ('present','late','absent','excused')),
  remarks text not null default '',
  recorded_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now(),
  unique(student_id, attendance_date)
);
create table if not exists public.performance_marks (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.profiles(id) on delete cascade,
  awarded_by uuid not null references public.profiles(id),
  category text not null default 'General performance',
  score numeric(5,2) not null check (score >= 0 and score <= 100),
  feedback text not null default '',
  created_at timestamptz not null default now()
);

alter table public.organizations enable row level security;
alter table public.attendance_records enable row level security;
alter table public.performance_marks enable row level security;

-- The public selector exposes only organisation ID and name, never the key hash.
drop policy if exists "organization names are discoverable" on public.organizations;
create policy "organization names are discoverable" on public.organizations
  for select to anon, authenticated using (true);
revoke all on public.organizations from anon, authenticated;
grant select (id, name) on public.organizations to anon, authenticated;
create or replace function public.current_logbook_role()
returns text language sql stable security definer set search_path = ''
as $$ select p.role from public.profiles p where p.id = auth.uid() $$;
create or replace function public.current_logbook_organization_id()
returns uuid language sql stable security definer set search_path = ''
as $$ select p.organization_id from public.profiles p where p.id = auth.uid() $$;
create or replace function public.current_logbook_approval_status()
returns text language sql stable security definer set search_path = ''
as $$ select p.approval_status from public.profiles p where p.id = auth.uid() $$;
create or replace function public.current_logbook_course()
returns text language sql stable security definer set search_path = ''
as $$ select p.course from public.profiles p where p.id = auth.uid() $$;
revoke all on function public.current_logbook_role() from public, anon;
revoke all on function public.current_logbook_organization_id() from public, anon;
revoke all on function public.current_logbook_approval_status() from public, anon;
revoke all on function public.current_logbook_course() from public, anon;
grant execute on function public.current_logbook_role() to authenticated;
grant execute on function public.current_logbook_organization_id() to authenticated;
grant execute on function public.current_logbook_approval_status() to authenticated;
grant execute on function public.current_logbook_course() to authenticated;

drop policy if exists "organization admin updates own organization" on public.organizations;
create policy "organization admin updates own organization" on public.organizations
  for update to authenticated
  using (public.current_logbook_role() = 'organization_admin'
    and public.current_logbook_organization_id() = organizations.id)
  with check (public.current_logbook_role() = 'organization_admin'
    and public.current_logbook_organization_id() = organizations.id);
grant update (name) on public.organizations to authenticated;

create or replace function public.protect_logbook_profile_fields()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() = old.id
    and coalesce(pg_catalog.current_setting('app.logbook_privileged_registration', true), 'off') <> 'on' then
    if new.role is distinct from old.role
      or new.organization_id is distinct from old.organization_id
      or new.approval_status is distinct from old.approval_status then
      raise exception 'Role, organisation, and approval status can only be changed through the registration or approval workflow.';
    end if;
  end if;
  new.updated_at := now();
  return new;
end;
$$;
drop trigger if exists protect_logbook_profile_fields_trigger on public.profiles;
create trigger protect_logbook_profile_fields_trigger before update on public.profiles
  for each row execute function public.protect_logbook_profile_fields();

create or replace function public.create_logbook_organization(
  p_full_name text, p_organization_name text, p_gender text default null
) returns table(organization_id uuid, organization_key text)
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_existing public.profiles%rowtype;
  v_org_id uuid;
  v_key text;
begin
  if v_user is null then raise exception 'Sign in before creating an organisation.'; end if;
  if nullif(trim(p_full_name),'') is null or nullif(trim(p_organization_name),'') is null then
    raise exception 'Enter your full name and organisation name.';
  end if;
  if p_gender is not null and p_gender not in ('female','male','prefer_not_to_say') then
    raise exception 'Choose a valid gender option.';
  end if;
  select * into v_existing from public.profiles where id = v_user;
  if found and v_existing.organization_id is not null then
    raise exception 'This account is already linked to an organisation.';
  end if;
  if found and v_existing.role not in ('student') then
    raise exception 'This account cannot create another organisation.';
  end if;
  v_key := upper(encode(extensions.gen_random_bytes(8),'hex'));
  insert into public.organizations(name, organization_key_hash, created_by)
    values(trim(p_organization_name),
      encode(extensions.digest(convert_to(v_key,'UTF8'),'sha256'),'hex'), v_user)
    returning id into v_org_id;
  perform pg_catalog.set_config('app.logbook_privileged_registration','on',true);
  insert into public.profiles(id,full_name,role,school_name,organization_id,gender,approval_status,updated_at)
    values(v_user,trim(p_full_name),'organization_admin',trim(p_organization_name),v_org_id,p_gender,'approved',now())
    on conflict (id) do update set full_name=excluded.full_name, role='organization_admin',
      school_name=excluded.school_name, organization_id=v_org_id, gender=excluded.gender,
      approval_status='approved', updated_at=now();
  return query select v_org_id,v_key;
end;
$;

update public.profiles p set school_name=o.name
from public.organizations o
where p.organization_id=o.id and p.role='organization_admin' and p.school_name is null;

create or replace function public.register_logbook_member(
  p_full_name text, p_role text, p_gender text, p_course text,
  p_student_number text, p_organization_id uuid, p_organization_key text
) returns table(organization_id uuid, assigned_role text, approval_status text)
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_existing public.profiles%rowtype;
  v_org public.organizations%rowtype;
  v_status text;
begin
  if v_user is null then raise exception 'Sign in before completing registration.'; end if;
  if p_role not in ('student','field_supervisor','institution_supervisor') then
    raise exception 'Choose student or supervisor account.';
  end if;
  if nullif(trim(p_full_name),'') is null then raise exception 'Enter your full name.'; end if;
  if p_gender not in ('female','male','prefer_not_to_say') then raise exception 'Choose a gender option.'; end if;
  if nullif(trim(p_course),'') is null then raise exception 'Enter your course or subject.'; end if;
  if p_role = 'student' and nullif(trim(p_student_number),'') is null then
    raise exception 'Students must enter their student/index number.';
  end if;
  select * into v_org from public.organizations
    where id=p_organization_id
      and organization_key_hash=encode(extensions.digest(convert_to(coalesce(p_organization_key,''),'UTF8'),'sha256'),'hex');
  if not found then raise exception 'Organisation or key was not accepted. Check both and try again.'; end if;
  select * into v_existing from public.profiles where id=v_user;
  if found and v_existing.organization_id is not null then
    if v_existing.organization_id=p_organization_id and v_existing.role=p_role then
      return query select v_existing.organization_id,v_existing.role,v_existing.approval_status;
      return;
    end if;
    raise exception 'This account is already registered. Sign in and edit your existing profile instead.';
  end if;
  if found and v_existing.role not in ('student') then
    raise exception 'This account cannot be re-registered with another role.';
  end if;
  v_status := case when p_role='student' then 'approved' else 'pending' end;
  perform pg_catalog.set_config('app.logbook_privileged_registration','on',true);
  insert into public.profiles(id,full_name,role,school_name,student_number,organization_id,gender,course,approval_status,updated_at)
    values(v_user,trim(p_full_name),p_role,v_org.name,nullif(trim(p_student_number),''),p_organization_id,
      p_gender,nullif(trim(p_course),''),v_status,now())
    on conflict(id) do update set full_name=excluded.full_name, role=excluded.role,
      school_name=excluded.school_name,student_number=excluded.student_number,
      organization_id=excluded.organization_id,gender=excluded.gender,course=excluded.course,
      approval_status=excluded.approval_status,updated_at=now();
  return query select p_organization_id,p_role,v_status;
end;
$$;

create or replace function public.organization_review_supervisor(p_supervisor_id uuid, p_approve boolean)
returns void language plpgsql security definer set search_path = ''
as $$
declare v_org uuid;
begin
  select organization_id into v_org from public.profiles
    where id=auth.uid() and role='organization_admin' and approval_status='approved';
  if v_org is null then raise exception 'Only an approved organisation administrator can review supervisors.'; end if;
  update public.profiles set approval_status=case when p_approve then 'approved' else 'rejected' end,updated_at=now()
    where id=p_supervisor_id and organization_id=v_org and role in ('field_supervisor','institution_supervisor');
  if not found then raise exception 'Supervisor was not found in your organisation.'; end if;
end;
$$;

create or replace function public.organization_assign_supervisor(
  p_student_id uuid, p_supervisor_id uuid, p_assignment_type text
) returns void language plpgsql security definer set search_path = ''
as $$
declare v_org uuid;
begin
  select organization_id into v_org from public.profiles
    where id=auth.uid() and role='organization_admin' and approval_status='approved';
  if v_org is null then raise exception 'Only an organisation administrator can assign supervisors.'; end if;
  if p_assignment_type not in ('field_supervisor','institution_supervisor') then raise exception 'Invalid supervisor type.'; end if;
  if not exists(select 1 from public.profiles s where s.id=p_student_id and s.organization_id=v_org and s.role='student' and s.approval_status='approved') then
    raise exception 'Student is not an approved member of your organisation.';
  end if;
  if not exists(select 1 from public.profiles s where s.id=p_supervisor_id and s.organization_id=v_org and s.role=p_assignment_type and s.approval_status='approved') then
    raise exception 'Supervisor must be approved and belong to your organisation.';
  end if;
  insert into public.supervisor_assignments(student_id,supervisor_id,assignment_type)
    values(p_student_id,p_supervisor_id,p_assignment_type)
    on conflict(student_id,supervisor_id,assignment_type) do nothing;
end;
$$;

create or replace function public.rotate_logbook_organization_key()
returns table(organization_id uuid, organization_key text)
language plpgsql security definer set search_path = ''
as $$
declare v_org uuid; v_key text;
begin
  v_org := public.current_logbook_organization_id();
  if auth.uid() is null or public.current_logbook_role() <> 'organization_admin'
    or public.current_logbook_approval_status() <> 'approved' or v_org is null then
    raise exception 'Only an approved organisation administrator can rotate the key.';
  end if;
  v_key := upper(encode(extensions.gen_random_bytes(8),'hex'));
  update public.organizations set organization_key_hash=
    encode(extensions.digest(convert_to(v_key,'UTF8'),'sha256'),'hex') where id=v_org;
  return query select v_org,v_key;
end;
$$;
revoke all on function public.create_logbook_organization(text,text,text) from public, anon;
revoke all on function public.register_logbook_member(text,text,text,text,text,uuid,text) from public, anon;
revoke all on function public.organization_review_supervisor(uuid,boolean) from public, anon;
revoke all on function public.organization_assign_supervisor(uuid,uuid,text) from public, anon;
revoke all on function public.rotate_logbook_organization_key() from public, anon;
grant execute on function public.create_logbook_organization(text,text,text) to authenticated;
grant execute on function public.register_logbook_member(text,text,text,text,text,uuid,text) to authenticated;
grant execute on function public.organization_review_supervisor(uuid,boolean) to authenticated;
grant execute on function public.organization_assign_supervisor(uuid,uuid,text) to authenticated;
grant execute on function public.rotate_logbook_organization_key() to authenticated;

-- Profile directory: an owner sees self, supervisors see assigned students,
-- organisations see their members, and students see members of their same course.
drop policy if exists "profile owner reads own profile" on public.profiles;
drop policy if exists "assigned supervisor reads assigned student profile" on public.profiles;
drop policy if exists "owners and assigned supervisors read profiles" on public.profiles;
drop policy if exists "owners supervisors and organisation read profiles" on public.profiles;
create policy "owners supervisors and organisation read profiles" on public.profiles
  for select to authenticated using (
    id=(select auth.uid())
    or exists (select 1 from public.supervisor_assignments sa where sa.student_id=profiles.id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
    or (public.current_logbook_role()='organization_admin' and public.current_logbook_organization_id()=organization_id)
    or (public.current_logbook_role()='student' and role='student'
      and organization_id=public.current_logbook_organization_id() and course=public.current_logbook_course())
  );

drop policy if exists "student reads own daily entries" on public.daily_entries;
drop policy if exists "assigned supervisors read daily entries" on public.daily_entries;
drop policy if exists "students supervisors and organisation read daily entries" on public.daily_entries;
create policy "students supervisors and organisation read daily entries" on public.daily_entries
  for select to authenticated using (
    student_id=(select auth.uid())
    or exists (select 1 from public.supervisor_assignments sa where sa.student_id=daily_entries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
    or (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
      and exists (select 1 from public.profiles student where student.id=daily_entries.student_id
        and student.organization_id=public.current_logbook_organization_id()))
  );
drop policy if exists "student reads own weekly summaries" on public.weekly_summaries;
drop policy if exists "assigned supervisors read weekly summaries" on public.weekly_summaries;
drop policy if exists "students supervisors and organisation read weekly summaries" on public.weekly_summaries;
create policy "students supervisors and organisation read weekly summaries" on public.weekly_summaries
  for select to authenticated using (
    student_id=(select auth.uid())
    or exists (select 1 from public.supervisor_assignments sa where sa.student_id=weekly_summaries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
    or (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
      and exists (select 1 from public.profiles student where student.id=weekly_summaries.student_id
        and student.organization_id=public.current_logbook_organization_id()))
  );

drop policy if exists "students and assigned supervisors update daily entries" on public.daily_entries;
drop policy if exists "students and approved supervisors update daily entries" on public.daily_entries;
create policy "students and approved supervisors update daily entries" on public.daily_entries
  for update to authenticated using (
    (student_id=(select auth.uid()) and public.current_logbook_role()='student')
    or exists(select 1 from public.supervisor_assignments sa where sa.student_id=daily_entries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
  ) with check (
    (student_id=(select auth.uid()) and public.current_logbook_role()='student')
    or exists(select 1 from public.supervisor_assignments sa where sa.student_id=daily_entries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
  );
drop policy if exists "students and assigned supervisors update weekly summaries" on public.weekly_summaries;
drop policy if exists "students and approved supervisors update weekly summaries" on public.weekly_summaries;
create policy "students and approved supervisors update weekly summaries" on public.weekly_summaries
  for update to authenticated using (
    (student_id=(select auth.uid()) and public.current_logbook_role()='student')
    or exists(select 1 from public.supervisor_assignments sa where sa.student_id=weekly_summaries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
  ) with check (
    (student_id=(select auth.uid()) and public.current_logbook_role()='student')
    or exists(select 1 from public.supervisor_assignments sa where sa.student_id=weekly_summaries.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
  );

-- Attendance: one SELECT policy plus distinct write policies to avoid policy overlap.
drop policy if exists "assigned supervisors manage attendance" on public.attendance_records;
drop policy if exists "organization manage attendance" on public.attendance_records;
drop policy if exists "students view own attendance" on public.attendance_records;
drop policy if exists "members and organisation read attendance" on public.attendance_records;
drop policy if exists "approved staff record attendance" on public.attendance_records;
drop policy if exists "approved staff update attendance" on public.attendance_records;
drop policy if exists "approved staff delete attendance" on public.attendance_records;
create policy "members and organisation read attendance" on public.attendance_records
  for select to authenticated using (
    student_id=(select auth.uid())
    or (public.current_logbook_approval_status()='approved' and exists(
      select 1 from public.supervisor_assignments sa where sa.student_id=attendance_records.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()))
    or (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
      and exists(select 1 from public.profiles student where student.id=attendance_records.student_id
        and student.organization_id=public.current_logbook_organization_id()))
  );
create policy "approved staff record attendance" on public.attendance_records
  for insert to authenticated with check (
    recorded_by=(select auth.uid()) and (
      (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
        and exists(select 1 from public.profiles student where student.id=attendance_records.student_id
          and student.organization_id=public.current_logbook_organization_id()))
      or (public.current_logbook_approval_status()='approved' and exists(
        select 1 from public.supervisor_assignments sa where sa.student_id=attendance_records.student_id
        and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()))
    )
  );
create policy "approved staff update attendance" on public.attendance_records
  for update to authenticated using (
    (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
      and exists(select 1 from public.profiles student where student.id=attendance_records.student_id
        and student.organization_id=public.current_logbook_organization_id()))
    or (public.current_logbook_approval_status()='approved' and exists(
      select 1 from public.supervisor_assignments sa where sa.student_id=attendance_records.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()))
  ) with check (
    recorded_by=(select auth.uid()) and (
      (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
        and exists(select 1 from public.profiles student where student.id=attendance_records.student_id
          and student.organization_id=public.current_logbook_organization_id()))
      or (public.current_logbook_approval_status()='approved' and exists(
        select 1 from public.supervisor_assignments sa where sa.student_id=attendance_records.student_id
        and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()))
    )
  );
create policy "approved staff delete attendance" on public.attendance_records
  for delete to authenticated using (
    (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
      and exists(select 1 from public.profiles student where student.id=attendance_records.student_id
        and student.organization_id=public.current_logbook_organization_id()))
    or (public.current_logbook_approval_status()='approved' and exists(
      select 1 from public.supervisor_assignments sa where sa.student_id=attendance_records.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()))
  );

drop policy if exists "organisation and assigned supervisors award marks" on public.performance_marks;
create policy "organisation and assigned supervisors award marks" on public.performance_marks
  for insert to authenticated with check (
    awarded_by=(select auth.uid()) and (
      (public.current_logbook_role()='organization_admin' and public.current_logbook_approval_status()='approved'
        and exists(select 1 from public.profiles student where student.id=performance_marks.student_id
          and student.organization_id=public.current_logbook_organization_id()))
      or exists(select 1 from public.supervisor_assignments sa where sa.student_id=performance_marks.student_id
        and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
        and public.current_logbook_approval_status()='approved')
    )
  );
drop policy if exists "students view organisation course rankings" on public.performance_marks;
create policy "students view organisation course rankings" on public.performance_marks
  for select to authenticated using (
    student_id=(select auth.uid())
    or (public.current_logbook_role()='student' and exists(
      select 1 from public.profiles student where student.id=performance_marks.student_id and student.role='student'
        and student.organization_id=public.current_logbook_organization_id() and student.course=public.current_logbook_course()))
    or (public.current_logbook_role()='organization_admin' and exists(
      select 1 from public.profiles student where student.id=performance_marks.student_id
        and student.organization_id=public.current_logbook_organization_id()))
    or exists(select 1 from public.supervisor_assignments sa where sa.student_id=performance_marks.student_id
      and sa.supervisor_id=(select auth.uid()) and sa.assignment_type=public.current_logbook_role()
      and public.current_logbook_approval_status()='approved')
  );

grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.attendance_records to authenticated;
grant select, insert on table public.performance_marks to authenticated;
grant select (id, full_name, role, school_name, student_number, created_at, updated_at, organization_id, gender, course, approval_status) on public.profiles to authenticated;
create index if not exists profiles_organization_course_idx on public.profiles(organization_id, course);
create index if not exists profiles_organization_role_status_idx on public.profiles(organization_id, role, approval_status);
create index if not exists organizations_created_by_idx on public.organizations(created_by);
create index if not exists attendance_records_student_date_idx on public.attendance_records(student_id, attendance_date desc);
create index if not exists attendance_records_recorded_by_idx on public.attendance_records(recorded_by);
create index if not exists performance_marks_student_created_idx on public.performance_marks(student_id, created_at desc);
create index if not exists performance_marks_awarded_by_idx on public.performance_marks(awarded_by);

-- Never add a service-role/secret key to the website or Android app.
