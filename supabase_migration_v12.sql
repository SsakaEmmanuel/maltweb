-- MALTWEB v12: secure admin review helpers
-- Run this ONCE in Supabase SQL Editor after the existing MALTWEB schema.
-- Then make yourself an admin by replacing YOUR_EMAIL below with the email
-- of the account you want to use as the MALTWEB administrator.

create table if not exists public.malt_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

alter table public.malt_admins enable row level security;

create or replace function public.malt_is_admin()
returns boolean
language sql
security definer
set search_path = public
as $$
  select exists(select 1 from public.malt_admins where user_id = auth.uid());
$$;

create or replace function public.malt_set_listing_status(p_listing_id uuid, p_status text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.malt_is_admin() then
    raise exception 'Not authorized';
  end if;
  if p_status not in ('pending','active','rejected') then
    raise exception 'Invalid status';
  end if;
  update public.listings set status=p_status where id=p_listing_id;
  return found;
end;
$$;

revoke all on function public.malt_is_admin() from public;
grant execute on function public.malt_is_admin() to authenticated;
revoke all on function public.malt_set_listing_status(uuid,text) from public;
grant execute on function public.malt_set_listing_status(uuid,text) to authenticated;

-- Replace the email below, then run this INSERT.
-- INSERT INTO public.malt_admins(user_id)
-- SELECT id FROM auth.users WHERE email='YOUR_EMAIL';
