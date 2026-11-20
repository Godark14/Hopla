-- =====================================================================
-- Hopla — schéma Supabase
-- À exécuter dans SQL Editor (ou `supabase db push` avec la CLI).
-- =====================================================================

-- ---------- Profils publics ------------------------------------------
create table if not exists public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  handle      text not null unique check (handle ~ '^[a-z0-9._]{3,20}$'),
  name        text not null check (char_length(name) between 1 and 24),
  shape_index int  not null default floor(random() * 12)::int,
  color_index int  not null default floor(random() * 4)::int,
  created_at  timestamptz not null default now()
);

-- ---------- Séances --------------------------------------------------
create table if not exists public.sessions (
  id           uuid primary key,                       -- généré côté app → upsert idempotent
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  started_at   timestamptz not null,
  duration_sec int  not null check (duration_sec between 0 and 86400),
  active_sec   int  not null default 0 check (active_sec between 0 and 86400),
  jumps        int  not null check (jumps between 0 and 200000),
  kcal         numeric(8, 2) not null default 0 check (kcal >= 0),
  mode         text not null check (char_length(mode) <= 40),
  peak_cadence int  not null default 0 check (peak_cadence between 0 and 400),
  created_at   timestamptz not null default now()
);
create index if not exists sessions_user_started_idx on public.sessions (user_id, started_at desc);

-- ---------- Abonnements ----------------------------------------------
create table if not exists public.follows (
  follower_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  followee_id uuid not null references public.profiles (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id)
);
create index if not exists follows_followee_idx on public.follows (followee_id);

-- ---------- Accès Data API (obligatoire depuis mai 2026) ---------------
-- Les nouveaux projets Supabase n'exposent plus automatiquement les tables :
-- sans ces GRANT, l'app reçoit « permission denied » (code 42501).
-- Seul le rôle "authenticated" y a accès ; anon (non connecté) n'a rien.
grant usage on schema public to authenticated;
grant select, insert, update, delete on public.profiles, public.sessions, public.follows to authenticated;

-- ---------- Row Level Security ---------------------------------------
alter table public.profiles enable row level security;
alter table public.sessions enable row level security;
alter table public.follows  enable row level security;

-- Profils : lisibles par tout utilisateur connecté, modifiables par leur propriétaire
drop policy if exists "profiles_read" on public.profiles;
create policy "profiles_read" on public.profiles for select to authenticated using (true);
drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles for insert to authenticated with check (id = (select auth.uid()));
drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Séances : visibles des utilisateurs connectés (classements), écrites par leur auteur
drop policy if exists "sessions_read" on public.sessions;
create policy "sessions_read" on public.sessions for select to authenticated using (true);
drop policy if exists "sessions_insert_own" on public.sessions;
create policy "sessions_insert_own" on public.sessions for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists "sessions_update_own" on public.sessions;
create policy "sessions_update_own" on public.sessions for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
drop policy if exists "sessions_delete_own" on public.sessions;
create policy "sessions_delete_own" on public.sessions for delete to authenticated using (user_id = (select auth.uid()));

-- Abonnements : lisibles, et chacun gère les siens
drop policy if exists "follows_read" on public.follows;
create policy "follows_read" on public.follows for select to authenticated using (true);
drop policy if exists "follows_insert_own" on public.follows;
create policy "follows_insert_own" on public.follows for insert to authenticated with check (follower_id = (select auth.uid()));
drop policy if exists "follows_delete_own" on public.follows;
create policy "follows_delete_own" on public.follows for delete to authenticated using (follower_id = (select auth.uid()));

-- ---------- Vue d'ensemble des athlètes (classement, fiches, recherche) ----
-- Calcule côté serveur, dans le fuseau de l'utilisateur :
-- sauts du jour, semaine (lun→dim), semaine dernière, kcal, minutes, record, série.
create or replace function public.athletes_overview(
  p_tz     text   default 'UTC',
  p_ids    uuid[] default null,
  p_search text   default null,
  p_limit  int    default 50
)
returns table (
  id              uuid,
  name            text,
  handle          text,
  shape_index     int,
  color_index     int,
  today_jumps     int,
  week_jumps      int[],
  last_week_jumps int[],
  week_kcal       int,
  week_minutes    int,
  best_session    int,
  streak          int
)
language sql
stable
security invoker
set search_path = public
as $$
  with b as (
    select (now() at time zone p_tz)::date                     as today,
           date_trunc('week', now() at time zone p_tz)::date   as monday
  ),
  people as (
    select p.*
    from profiles p
    where (p_ids is null or p.id = any (p_ids))
      and (coalesce(p_search, '') = ''
           or p.name ilike '%' || p_search || '%'
           or p.handle ilike '%' || ltrim(p_search, '@') || '%')
    order by p.created_at desc
    limit least(greatest(p_limit, 1), 200)
  ),
  daily as (
    select s.user_id,
           (s.started_at at time zone p_tz)::date as day,
           sum(s.jumps)::int        as jumps,
           sum(s.kcal)              as kcal,
           sum(s.duration_sec)::int as secs
    from sessions s
    join people pp on pp.id = s.user_id
    cross join b
    where s.started_at >= ((b.monday - 400)::timestamp at time zone p_tz)
    group by 1, 2
  ),
  streaks as (
    -- Jours consécutifs avec au moins une séance, en partant d'aujourd'hui
    -- (ou d'hier si rien encore aujourd'hui : la série n'est pas cassée avant minuit).
    select y.user_id, count(*)::int as streak
    from (
      select d.user_id, d.day, s.start_day,
             row_number() over (partition by d.user_id order by d.day desc) as rn
      from daily d
      join (
        select u.user_id,
               case when bool_or(u.day = b.today) then b.today else b.today - 1 end as start_day
        from daily u cross join b
        group by u.user_id, b.today
      ) s on s.user_id = d.user_id
      where d.day <= s.start_day
    ) y
    where y.day = y.start_day - (y.rn - 1)::int
    group by y.user_id
  )
  select p.id, p.name, p.handle, p.shape_index, p.color_index,
         coalesce((select d.jumps from daily d where d.user_id = p.id and d.day = b.today), 0),
         array(select coalesce((select d.jumps from daily d where d.user_id = p.id and d.day = b.monday + i), 0)
               from generate_series(0, 6) as i order by i),
         array(select coalesce((select d.jumps from daily d where d.user_id = p.id and d.day = b.monday - 7 + i), 0)
               from generate_series(0, 6) as i order by i),
         coalesce((select sum(d.kcal) from daily d where d.user_id = p.id and d.day between b.monday and b.monday + 6), 0)::int,
         (coalesce((select sum(d.secs) from daily d where d.user_id = p.id and d.day between b.monday and b.monday + 6), 0) / 60)::int,
         coalesce((select max(s.jumps) from sessions s where s.user_id = p.id), 0),
         coalesce((select st.streak from streaks st where st.user_id = p.id), 0)
  from people p cross join b;
$$;

grant execute on function public.athletes_overview(text, uuid[], text, int) to authenticated;
