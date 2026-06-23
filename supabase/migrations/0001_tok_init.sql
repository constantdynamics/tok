-- ============================================================================
-- Spraak-naar-Bullets ("tok") — initieel schema
-- ----------------------------------------------------------------------------
-- Dit schema draait in een GEDEELD Supabase-project (samen met "eten-avontuur").
-- Daarom heeft ELK object de prefix `tok_` zodat het nooit botst met andere apps.
-- Beveiliging: geen accounts, maar een koppelcode-mechanisme via Row Level
-- Security (RLS). Clients sturen een `x-pairing-token` header mee; RLS laat alleen
-- reads/writes toe als dat token geldig en niet verlopen is.
-- ============================================================================

create extension if not exists pgcrypto;

-- ============================================================================
-- TABELLEN
-- ============================================================================

-- Losse bullets (opmerkingen / actiepunten / ideeën / ...). Eén doorlopende lijst.
create table if not exists public.tok_bullets (
  id          uuid primary key default gen_random_uuid(),
  text        text not null default '',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  -- double precision i.p.v. integer: herordenen kan dan met de "midpoint tussen
  -- buren"-truc zonder alle rijen te hernummeren.
  sort_order  double precision not null default 0,
  is_archived boolean not null default false
);

-- Door de gebruiker zelf beheerde labels (naam + neon-hexkleur).
create table if not exists public.tok_labels (
  id         uuid primary key default gen_random_uuid(),
  name       text not null,
  color      text not null default '#39FF14',
  created_at timestamptz not null default now()
);

-- Many-to-many koppeling bullets <-> labels (multi-label per bullet).
create table if not exists public.tok_bullet_labels (
  bullet_id uuid not null references public.tok_bullets(id) on delete cascade,
  label_id  uuid not null references public.tok_labels(id)  on delete cascade,
  primary key (bullet_id, label_id)
);

-- Gekoppelde devices: elk geldig (niet-verlopen) pairing_token geeft toegang.
create table if not exists public.tok_paired_devices (
  id            uuid primary key default gen_random_uuid(),
  device_name   text,
  pairing_token text not null unique,
  is_owner      boolean not null default false,
  created_at    timestamptz not null default now(),
  expires_at    timestamptz not null default (now() + interval '90 days')
);

-- Kortlevende 6-cijferige koppelcodes; worden ingewisseld voor een pairing_token.
create table if not exists public.tok_pairing_codes (
  code        text primary key,
  device_name text,
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default (now() + interval '10 minutes'),
  redeemed    boolean not null default false
);

-- ============================================================================
-- INDEXEN (snel filteren/sorteren)
-- ============================================================================
create index if not exists tok_bullets_sort_order_idx  on public.tok_bullets(sort_order);
create index if not exists tok_bullets_is_archived_idx on public.tok_bullets(is_archived);
create index if not exists tok_bullets_created_at_idx  on public.tok_bullets(created_at);
create index if not exists tok_bullet_labels_label_idx on public.tok_bullet_labels(label_id);

-- ============================================================================
-- updated_at trigger
-- ----------------------------------------------------------------------------
-- Zet updated_at op now() bij een update, TENZIJ de client zelf een nieuwe
-- updated_at meestuurt (nodig voor last-write-wins bij offline sync vanuit de
-- Android-app: dan telt de lokale bewerk-tijd, niet de servertijd).
-- ============================================================================
create or replace function public.tok_set_updated_at()
returns trigger
language plpgsql
as $$
begin
  if new.updated_at is not distinct from old.updated_at then
    new.updated_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists tok_bullets_set_updated_at on public.tok_bullets;
create trigger tok_bullets_set_updated_at
  before update on public.tok_bullets
  for each row execute function public.tok_set_updated_at();

-- ============================================================================
-- PAIRING-HELPERS
-- ============================================================================

-- Leest het pairing-token uit de request-header (of null).
create or replace function public.tok_current_token()
returns text
language sql
stable
as $$
  select nullif(current_setting('request.headers', true)::json ->> 'x-pairing-token', '');
$$;

-- True als de request een geldig, niet-verlopen pairing-token draagt.
-- SECURITY DEFINER: moet tok_paired_devices kunnen lezen ondanks RLS.
create or replace function public.tok_is_paired()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.tok_paired_devices d
    where d.pairing_token = public.tok_current_token()
      and d.expires_at > now()
  );
$$;

-- ============================================================================
-- RLS
-- ============================================================================
alter table public.tok_bullets        enable row level security;
alter table public.tok_labels         enable row level security;
alter table public.tok_bullet_labels  enable row level security;
alter table public.tok_paired_devices enable row level security;
alter table public.tok_pairing_codes  enable row level security;

-- Datatabellen: volledige toegang alleen met een geldig pairing-token.
drop policy if exists tok_bullets_rw on public.tok_bullets;
create policy tok_bullets_rw on public.tok_bullets
  for all to anon, authenticated
  using (public.tok_is_paired()) with check (public.tok_is_paired());

drop policy if exists tok_labels_rw on public.tok_labels;
create policy tok_labels_rw on public.tok_labels
  for all to anon, authenticated
  using (public.tok_is_paired()) with check (public.tok_is_paired());

drop policy if exists tok_bullet_labels_rw on public.tok_bullet_labels;
create policy tok_bullet_labels_rw on public.tok_bullet_labels
  for all to anon, authenticated
  using (public.tok_is_paired()) with check (public.tok_is_paired());

-- tok_paired_devices en tok_pairing_codes krijgen GEEN policies → RLS weigert
-- alle directe anon/authenticated-toegang. Alleen de SECURITY DEFINER-functies
-- hieronder mogen die tabellen aanraken.

-- ============================================================================
-- PAIRING-RPC's
-- ============================================================================

-- Wissel een 6-cijferige code in → geeft een nieuw pairing_token (90 dagen).
-- Anon-aanroepbaar ZONDER token (bootstrap): de code zelf is de "secret".
create or replace function public.tok_redeem_pairing_code(p_code text, p_device_name text default null)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_token text;
begin
  perform 1 from public.tok_pairing_codes
    where code = p_code and redeemed = false and expires_at > now()
    for update;
  if not found then
    raise exception 'invalid_or_expired_code';
  end if;

  update public.tok_pairing_codes set redeemed = true where code = p_code;

  -- Token = 64 hex-tekens uit twee UUID's. gen_random_uuid() zit in Postgres-core
  -- (pg_catalog, altijd op de search_path), dus dit werkt binnen search_path=public.
  -- We vermijden bewust pgcrypto's gen_random_bytes(): die staat in Supabase in het
  -- schema `extensions` en is hier dus niet vindbaar.
  v_token := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  insert into public.tok_paired_devices (device_name, pairing_token, is_owner, expires_at)
    values (coalesce(nullif(p_device_name, ''), 'Webpagina'), v_token, false, now() + interval '90 days');

  return v_token;
end;
$$;

-- Maak een nieuwe 6-cijferige code aan. Vereist een reeds geldig token
-- (zo kunnen alleen de app / al-gekoppelde clients nieuwe devices uitnodigen).
create or replace function public.tok_create_pairing_code(p_device_name text default null)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
begin
  if not public.tok_is_paired() then
    raise exception 'not_paired';
  end if;

  loop
    v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
    exit when not exists (
      select 1 from public.tok_pairing_codes where code = v_code and expires_at > now()
    );
  end loop;

  insert into public.tok_pairing_codes (code, device_name, expires_at)
    values (v_code, nullif(p_device_name, ''), now() + interval '10 minutes');

  return v_code;
end;
$$;

-- Trek een gekoppeld device in (kan de owner niet verwijderen). Vereist token.
create or replace function public.tok_revoke_device(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.tok_is_paired() then
    raise exception 'not_paired';
  end if;
  delete from public.tok_paired_devices where id = p_id and is_owner = false;
end;
$$;

-- Lijst gekoppelde devices (zonder de tokens prijs te geven). Vereist token.
create or replace function public.tok_list_devices()
returns table (id uuid, device_name text, is_owner boolean, created_at timestamptz, expires_at timestamptz)
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.tok_is_paired() then
    raise exception 'not_paired';
  end if;
  return query
    select d.id, d.device_name, d.is_owner, d.created_at, d.expires_at
    from public.tok_paired_devices d
    order by d.is_owner desc, d.created_at;
end;
$$;

grant execute on function public.tok_redeem_pairing_code(text, text) to anon, authenticated;
grant execute on function public.tok_create_pairing_code(text)       to anon, authenticated;
grant execute on function public.tok_revoke_device(uuid)             to anon, authenticated;
grant execute on function public.tok_list_devices()                  to anon, authenticated;

-- ============================================================================
-- REALTIME (postgres_changes) voor de datatabellen
-- ----------------------------------------------------------------------------
-- REPLICA IDENTITY FULL zorgt dat UPDATE/DELETE-payloads de volledige rij bevatten.
-- ============================================================================
alter table public.tok_bullets       replica identity full;
alter table public.tok_labels        replica identity full;
alter table public.tok_bullet_labels replica identity full;

do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;

  if not exists (select 1 from pg_publication_tables
                 where pubname='supabase_realtime' and schemaname='public' and tablename='tok_bullets') then
    alter publication supabase_realtime add table public.tok_bullets;
  end if;
  if not exists (select 1 from pg_publication_tables
                 where pubname='supabase_realtime' and schemaname='public' and tablename='tok_labels') then
    alter publication supabase_realtime add table public.tok_labels;
  end if;
  if not exists (select 1 from pg_publication_tables
                 where pubname='supabase_realtime' and schemaname='public' and tablename='tok_bullet_labels') then
    alter publication supabase_realtime add table public.tok_bullet_labels;
  end if;
end $$;
