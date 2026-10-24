-- E-Logbook registration: students select an existing organisation without an organisation key.
-- Supervisors must still prove membership using the organisation key.
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
  if p_role not in ('student','field_supervisor','institution_supervisor') then raise exception 'Choose student or supervisor account.'; end if;
  if nullif(trim(p_full_name),'') is null then raise exception 'Enter your full name.'; end if;
  if p_gender not in ('female','male','prefer_not_to_say') then raise exception 'Choose a gender option.'; end if;
  if nullif(trim(p_course),'') is null then raise exception 'Enter your course or subject.'; end if;
  if p_role='student' and nullif(trim(p_student_number),'') is null then raise exception 'Students must enter their student/index number.'; end if;
  if p_role='student' then
    select * into v_org from public.organizations where id=p_organization_id;
    if not found then raise exception 'Choose an organisation from the list.'; end if;
  else
    select * into v_org from public.organizations
      where id=p_organization_id
        and organization_key_hash=encode(extensions.digest(convert_to(coalesce(p_organization_key,''),'UTF8'),'sha256'),'hex');
    if not found then raise exception 'Organisation or key was not accepted. Check both and try again.'; end if;
  end if;
  select * into v_existing from public.profiles where id=v_user;
  if found and v_existing.organization_id is not null then
    if v_existing.organization_id=p_organization_id and v_existing.role=p_role then
      return query select v_existing.organization_id,v_existing.role,v_existing.approval_status; return;
    end if;
    raise exception 'This account is already registered. Sign in and edit your existing profile instead.';
  end if;
  if found and v_existing.role not in ('student') then raise exception 'This account cannot be re-registered with another role.'; end if;
  v_status := case when p_role='student' then 'approved' else 'pending' end;
  perform pg_catalog.set_config('app.logbook_privileged_registration','on',true);
  insert into public.profiles(id,full_name,role,school_name,student_number,organization_id,gender,course,approval_status,updated_at)
    values(v_user,trim(p_full_name),p_role,v_org.name,nullif(trim(p_student_number),''),p_organization_id,p_gender,nullif(trim(p_course),''),v_status,now())
    on conflict(id) do update set full_name=excluded.full_name,role=excluded.role,school_name=excluded.school_name,
      student_number=excluded.student_number,organization_id=excluded.organization_id,gender=excluded.gender,
      course=excluded.course,approval_status=excluded.approval_status,updated_at=now();
  return query select p_organization_id,p_role,v_status;
end;
$$;
revoke all on function public.register_logbook_member(text,text,text,text,text,uuid,text) from public, anon;
grant execute on function public.register_logbook_member(text,text,text,text,text,uuid,text) to authenticated;
