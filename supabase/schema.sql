-- MALTWEB SUPABASE DATABASE
-- Run this entire file in Supabase > SQL Editor.

create extension if not exists pgcrypto;

create table if not exists public.listings (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references auth.users(id) on delete set null,
  name text not null,
  category text not null,
  type text not null default 'Service Provider',
  location text not null,
  description text not null,
  phone text,
  website text,
  verified boolean not null default false,
  status text not null default 'pending' check (status in ('pending','active','rejected')),
  created_at timestamptz not null default now()
);

create table if not exists public.events (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category text,
  location text not null,
  date date not null,
  description text not null,
  status text not null default 'active' check (status in ('pending','active','cancelled')),
  created_at timestamptz not null default now()
);

create table if not exists public.opportunities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  type text not null,
  location text not null,
  description text not null,
  status text not null default 'active' check (status in ('pending','active','closed')),
  created_at timestamptz not null default now()
);

alter table public.listings enable row level security;
alter table public.events enable row level security;
alter table public.opportunities enable row level security;

-- Public visitors can discover active listings/events/opportunities.
create policy "public can read active listings" on public.listings for select using (status='active');
create policy "public can read active events" on public.events for select using (status='active');
create policy "public can read active opportunities" on public.opportunities for select using (status='active');

-- Logged-in users can submit their own listings for review.
create policy "users can submit listings" on public.listings for insert to authenticated with check (owner_id=auth.uid());

-- Users can see their own submissions.
create policy "users can read own listings" on public.listings for select to authenticated using (owner_id=auth.uid());
