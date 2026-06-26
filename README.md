# tok — spraak-naar-bullets

Persoonlijke app om ingesproken gedachten als losse, gelabelde **bullets** vast te
leggen, met **live multi-device sync** via Supabase en een met-koppelcode beveiligde
webpagina.

- **Ronde 1**: de Supabase-backend + de bewerkbare webpagina (live op GitHub Pages).
- **Ronde 2**: de Android-app met offline Vosk-spraakherkenning — zie
  [`android/README.md`](android/README.md) voor bouwen en distributie.

**Live webpagina:** https://constantdynamics.github.io/tok/

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
- `android/` — de Android-app (Kotlin + Compose + Vosk). Zie `android/README.md`.
- `.github/workflows/pages.yml` — publiceert `web/` automatisch naar GitHub Pages.

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

### 3. Koppelen
- **Eerste keer** (nog geen enkel apparaat gekoppeld): zaai een code in de SQL-editor:
  ```sql
  insert into public.tok_pairing_codes (code, expires_at)
  values ('424242', now() + interval '24 hours');
  ```
  Open de webpagina, voer `424242` in → de pagina krijgt een 90-dagen pairing-token
  (cookie) en is gekoppeld.
- **Daarna**: genereer codes vanuit een gekoppeld apparaat — op de webpagina met de knop
  **Koppel**, of in de Android-app via Instellingen → *Nieuwe koppelcode*.

### 4. Hosten
De webpagina staat **live op GitHub Pages** (`.github/workflows/pages.yml` publiceert
`web/` bij elke push; repo is publiek). De map `web/` is een gewone statische site en
werkt ook op **Netlify** (`netlify.toml` staat klaar) of **Vercel** (root op `web`).

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
- **Live sync** loopt via een Realtime **broadcast**-nudge ("er is iets gewijzigd" →
  andere apparaten herladen), met een poll-fallback. Bewust géén `postgres_changes`:
  onze token-gebaseerde RLS laat de anon-realtime-rol niets lezen. Alle **mutaties** zijn
  wél token-gegate. Upgrade-pad: kortlevende JWT met een `tok_paired`-claim om óók
  per-rij realtime te kunnen gaten.
- Een device intrekken: `tok_revoke_device(id)` of verwijder de rij uit
  `tok_paired_devices` → toegang direct weg.

## Ronde 2 — Android
Kotlin + Jetpack Compose, Room (offline-first), WorkManager-sync, Vosk (NL) met een
**gelaagd model** (klein voor directe start → groot voor nauwkeurigheid) en **import én
download**. Distributie via **Google Play Internal testing**. Bouwen en details:
[`android/README.md`](android/README.md).
