import { getClient } from './supabaseClient.js';
import {
  state, emit, upsertBullet, removeBullet, upsertLabel, removeLabel, addLink, removeLink,
} from './store.js';

// Abonneer op wijzigingen in de drie datatabellen. Elke wijziging (van welk device
// dan ook) werkt de in-memory state bij en hertekent de UI.
export function startRealtime() {
  const sb = getClient();
  return sb
    .channel('tok-realtime')
    .on('postgres_changes', { event: '*', schema: 'public', table: 'tok_bullets' }, (p) => {
      if (p.eventType === 'DELETE') removeBullet(p.old.id);
      else upsertBullet(p.new);
      emit();
    })
    .on('postgres_changes', { event: '*', schema: 'public', table: 'tok_labels' }, (p) => {
      if (p.eventType === 'DELETE') removeLabel(p.old.id);
      else upsertLabel(p.new);
      emit();
    })
    .on('postgres_changes', { event: '*', schema: 'public', table: 'tok_bullet_labels' }, (p) => {
      if (p.eventType === 'DELETE') removeLink(p.old);
      else addLink(p.new);
      emit();
    })
    .subscribe();
}
