import { getClient } from './supabaseClient.js';
import { loadAll } from './store.js';

// Live sync via een broadcast-"changed"-nudge. Bewust GEEN postgres_changes:
// onze RLS gate't lezen op het pairing-token, en de realtime-RLS-check (anon-rol,
// geen header) zou dan niets doorlaten. Een broadcast is een simpele pub/sub-nudge;
// wie 'm ontvangt, herlaadt de data. Een poll-fallback garandeert de correctheid.
let channel = null;
let pollTimer = null;

export function startRealtime() {
  const sb = getClient();
  channel = sb
    .channel('tok-sync', { config: { broadcast: { self: false } } })
    .on('broadcast', { event: 'changed' }, () => { loadAll().catch(() => {}); })
    .subscribe();

  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => {
    if (document.visibilityState === 'visible') loadAll().catch(() => {});
  }, 12000);
}

// Stuur na een lokale wijziging een nudge naar de andere apparaten.
export function notifyChanged() {
  if (channel) channel.send({ type: 'broadcast', event: 'changed', payload: {} });
}
