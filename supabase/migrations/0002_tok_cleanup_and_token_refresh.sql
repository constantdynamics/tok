-- ============================================================================
-- tok — onderhoud & token-verlenging (vervolg op 0001)
-- ----------------------------------------------------------------------------
-- #3  Ruimt verlopen koppelcodes en verlopen apparaten op (anders groeien die
--     tabellen oneindig). Gebeurt opportunistisch bij elke pairing-RPC, dus er
--     is geen pg_cron/extensie nodig.
-- #6  Verlengt een token "bij gebruik": clients roepen tok_touch_token() aan;
--     als het token bijna verloopt schuift expires_at 90 dagen op. Zo raken
--     actieve apparaten niet onverwacht ontkoppeld. We houden ook last_seen_at
--     bij, handig om inactieve apparaten te tonen/intrekken.
-- Idempotent: deze migratie kan veilig opnieuw worden toegepast.
-- ============================================================================

-- ---- #6: laatst-gezien-stempel op gekoppelde apparaten -----------------------
alter table public.tok_paired_devices
  add column if not exists last_seen_at timestamptz not null default now();

-- Index voor cleanup-query's op verlopen apparaten.
create index if not exists tok_paired_devices_expires_at_idx
  on public.tok_paired_devices(expires_at);

-- ============================================================================
-- #3: opruimen van verlopen/ingewisselde koppelcodes en verlopen apparaten.
-- SECURITY DEFINER zodat deze de RLS-loze tabellen mag aanraken. Wordt vanuit de
-- pairing-RPC's aangeroepen (zie hieronder).
-- ============================================================================
create or replace function public.tok_cleanup_pairing()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.tok_pairing_codes
    where expires_at < now() or redeemed = true;
  delete from public.tok_paired_devices
    where expires_at < now();
end;
$$;

-- ============================================================================
-- #6: token verlengen bij gebruik. Vereist een geldig token (dat van de caller).
-- Verlengt expires_at naar now()+90d als het binnen 60 dagen verloopt, en zet
-- last_seen_at. Geeft de (nieuwe) expires_at terug.
-- ============================================================================
create or replace function public.tok_touch_token()
returns timestamptz
language plpgsql
security definer
set search_path = public
as $$
declare
  v_token   text := public.tok_current_token();
  v_expires timestamptz;
begin
  if v_token is null then
    raise exception 'not_paired';
  end if;

  update public.tok_paired_devices
     set last_seen_at = now(),
         expires_at = case
           when expires_at < now() + interval '60 days' then now() + interval '90 days'
           else expires_at
         end
   where pairing_token = v_token
     and expires_at > now()
  returning expires_at into v_expires;

  if v_expires is null then
    raise exception 'not_paired';
  end if;
  return v_expires;
end;
$$;

grant execute on function public.tok_touch_token() to anon, authenticated;

-- ============================================================================
-- Pairing-RPC's opnieuw definiëren met een opportunistische cleanup-aanroep.
-- (Bodies gelijk aan 0001, alleen een `perform public.tok_cleanup_pairing();`
-- toegevoegd, plus last_seen_at bij het aanmaken van een apparaat.)
-- ============================================================================
create or replace function public.tok_redeem_pairing_code(p_code text, p_device_name text default null)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_token text;
begin
  perform public.tok_cleanup_pairing();

  perform 1 from public.tok_pairing_codes
    where code = p_code and redeemed = false and expires_at > now()
    for update;
  if not found then
    raise exception 'invalid_or_expired_code';
  end if;

  update public.tok_pairing_codes set redeemed = true where code = p_code;

  v_token := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  insert into public.tok_paired_devices (device_name, pairing_token, is_owner, expires_at, last_seen_at)
    values (coalesce(nullif(p_device_name, ''), 'Webpagina'), v_token, false, now() + interval '90 days', now());

  return v_token;
end;
$$;

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

  perform public.tok_cleanup_pairing();

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
