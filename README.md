# tok — spraak-naar-bullets

Persoonlijke app om ingesproken gedachten als losse, gelabelde **bullets** vast te
leggen, met **live multi-device sync** via Supabase en een met-koppelcode beveiligde
webpagina.

Dit is **Ronde 1**: de Supabase-backend + de bewerkbare webpagina. De Android-app
(offline Vosk-spraakherkenning) volgt in **Ronde 2** — het databaseschema is daar al
op voorbereid.

## Architectuur
```
Android-app (Ronde 2) ─┐                ┌─ Webpagina (web/, static)
                       ├─► Supabase ◄───┤
   Vosk STT, offline   │  Postgres +    │  supabase-js + anon key
   Room-cache, sync    │  Realtime+RLS  │  + x-pairing-token header
                       └────────────────┘
```
- **Backend**: gedeeld Supabase-project "eten-avontuur". Alle objecten hebben de
  prefix `tok_` zodat ze niet botsen met andere apps in datzelfde project.
- **Beveiliging**: geen accounts. Clients sturen een `x-pairing-token`-header mee; de
  RLS-policies geven alleen toegang bij een geldig, niet-verlopen token
  (`tok_paired_devices`). De webpagina krijgt zo'n token door een 6-cijferige
  koppelcode in te wisselen (`tok_redeem_pairing_code`).

## Mappen
- `supabase/migrations/0001_tok_init.sql` — volledig schema, RLS, pairing-RPC's, realtime.
- `web/` — statische webpagina (geen build-stap; ES-modules + CDN).

## Setup

### 1. Database
Pas de migratie toe op het project "eten-avontuur" (via de Supabase MCP `apply_migration`,
of plak `supabase/migrations/0001_tok_init.sql` in de SQL-editor van het dashboard).

### 2. Webpagina configureren
Vul in `web/js/config.js` de projectgegevens in:
```js
export const SUPABASE_URL = 'https://<project-ref>.supabase.co';
export const SUPABASE_ANON_KEY = '<anon of publishable key>';
```
De anon key is **publiek-by-design**; de beveiliging zit in RLS + pairing-token.

### 3. Koppelen (zolang de app er nog niet is)
Codes worden normaal door de Android-app gegenereerd. Voor nu maak je een code in de
SQL-editor:
```sql
insert into public.tok_pairing_codes (code) values ('123456');
```
Open de webpagina, voer `123456` in → de pagina krijgt een 90-dagen pairing-token
(cookie) en is gekoppeld.

### 4. Hosten
Statische map `web/`. Werkt op elke statische host:
- **Netlify**: `netlify.toml` staat klaar (`publish = "web"`).
- **Vercel**: root directory op `web` zetten, geen build command.
- **GitHub Pages**: alleen bij een publieke repo of betaald plan (zie plan-notities).

## Functionaliteit (webpagina)
- Bullet-overzicht met inline tekst bewerken.
- Multi-select + bulk-acties: label toekennen/verwijderen, archiveren, verwijderen.
- Drag-&-drop herordenen (handmatige sorteervolgorde).
- Filteren op label, datum en archiefstatus (combineerbaar) + los sorteren.
- Labelbeheer met neon-kleurkiezer.
- Realtime: wijzigingen van elk device verschijnen binnen seconden.

## Beveiligingsnotities
- Codes zijn 6-cijferig en 10 minuten geldig (eenmalig inwisselbaar). Voor een
  single-user app met laag-gevoelige notities is dat een bewuste, lichte drempel.
- Realtime-`postgres_changes` luistert via de anon-rol (niet per token gegated). Alle
  **mutaties** lopen wél via de token-gegate RLS. Upgrade-pad: kortlevende JWT via een
  Edge Function om ook realtime per token te gaten.
- Een device intrekken: verwijder de rij uit `tok_paired_devices` → toegang direct weg.

## Ronde 2 — Android (gepland)
Kotlin + Compose, Room (offline-first), WorkManager-sync, Vosk (NL) met **import én
download** van het model. Distributie via **Google Play Internal testing** (alleen voor
de eigenaar). Het schema (incl. `tok_create_pairing_code`, `tok_list_devices`,
`tok_revoke_device`) ondersteunt dit nu al.
