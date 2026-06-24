import { getClient } from './supabaseClient.js';
import { notifyChanged } from './realtime.js';
import {
  state, emit,
  upsertBullet, removeBullet, upsertLabel, removeLabel, addLink, removeLink,
} from './store-core.js';

// Re-export de pure kern zodat de rest van de app (ui.js, export.js, app.js) één
// stabiel importpunt houdt: './store.js'.
export {
  state, subscribe, emit,
  upsertBullet, removeBullet, upsertLabel, removeLabel, addLink, removeLink,
  labelsForBullet, visibleBullets,
} from './store-core.js';

const linkKey = (l) => `${l.bullet_id}|${l.label_id}`;

// ---- initial load ----
export async function loadAll() {
  const sb = getClient();
  const [b, l, bl] = await Promise.all([
    sb.from('tok_bullets').select('*').order('sort_order', { ascending: true }),
    sb.from('tok_labels').select('*').order('created_at', { ascending: true }),
    sb.from('tok_bullet_labels').select('*'),
  ]);
  if (b.error) throw b.error;
  if (l.error) throw l.error;
  if (bl.error) throw bl.error;
  state.bullets = b.data;
  state.labels = l.data;
  state.links = bl.data;
  emit();
}

// ============================================================================
// MUTATIES (optimistisch + persisteren naar Supabase)
// ----------------------------------------------------------------------------
// Elke mutatie past de state eerst optimistisch toe en draait die terug als de
// server-call faalt, zodat de UI nooit blijft hangen in een onjuiste staat.
// ============================================================================
export async function addBullet(text) {
  const maxOrder = state.bullets.reduce((m, b) => Math.max(m, b.sort_order), 0);
  const { data, error } = await getClient()
    .from('tok_bullets')
    .insert({ text, sort_order: maxOrder + 1 })
    .select()
    .single();
  if (error) throw error;
  upsertBullet(data); emit(); notifyChanged();
  return data;
}

export async function updateBulletText(id, text) {
  const prev = state.bullets.find(b => b.id === id);
  const old = prev ? prev.text : undefined;
  upsertBullet({ id, text }); emit();
  try {
    const { error } = await getClient().from('tok_bullets').update({ text }).eq('id', id);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    if (old !== undefined) { upsertBullet({ id, text: old }); emit(); }
    throw e;
  }
}

export async function setArchived(ids, isArchived) {
  const prev = new Map(ids.map(id => [id, state.bullets.find(b => b.id === id)?.is_archived]));
  ids.forEach(id => upsertBullet({ id, is_archived: isArchived })); emit();
  try {
    const { error } = await getClient().from('tok_bullets').update({ is_archived: isArchived }).in('id', ids);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    prev.forEach((v, id) => { if (v !== undefined) upsertBullet({ id, is_archived: v }); });
    emit();
    throw e;
  }
}

export async function deleteBullets(ids) {
  const removed = state.bullets.filter(b => ids.includes(b.id)).map(b => ({ ...b }));
  const removedLinks = state.links.filter(l => ids.includes(l.bullet_id)).map(l => ({ ...l }));
  ids.forEach(removeBullet); emit();
  try {
    const { error } = await getClient().from('tok_bullets').delete().in('id', ids);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    removed.forEach(upsertBullet); removedLinks.forEach(addLink); emit();
    throw e;
  }
}

export async function reorderBullet(id, newOrder) {
  const prev = state.bullets.find(b => b.id === id);
  const old = prev ? prev.sort_order : undefined;
  upsertBullet({ id, sort_order: newOrder }); emit();
  try {
    const { error } = await getClient().from('tok_bullets').update({ sort_order: newOrder }).eq('id', id);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    if (old !== undefined) { upsertBullet({ id, sort_order: old }); emit(); }
    throw e;
  }
}

// Herstel eerder verwijderde bullets (+ hun labelkoppelingen). Voor "Ongedaan maken".
export async function restoreBullets(bullets, links) {
  bullets.forEach(upsertBullet); links.forEach(addLink); emit();
  try {
    const sb = getClient();
    const { error: e1 } = await sb.from('tok_bullets').upsert(bullets);
    if (e1) throw e1;
    if (links.length) {
      const { error: e2 } = await sb.from('tok_bullet_labels')
        .upsert(links, { onConflict: 'bullet_id,label_id', ignoreDuplicates: true });
      if (e2) throw e2;
    }
    notifyChanged();
  } catch (e) {
    bullets.forEach(b => removeBullet(b.id)); emit();
    throw e;
  }
}

export async function createLabel(name, color) {
  const { data, error } = await getClient()
    .from('tok_labels').insert({ name, color }).select().single();
  if (error) throw error;
  upsertLabel(data); emit(); notifyChanged();
  return data;
}

export async function updateLabel(id, fields) {
  const prev = state.labels.find(l => l.id === id);
  const old = prev ? { ...prev } : undefined;
  upsertLabel({ id, ...fields }); emit();
  try {
    const { error } = await getClient().from('tok_labels').update(fields).eq('id', id);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    if (old) { upsertLabel(old); emit(); }
    throw e;
  }
}

export async function deleteLabel(id) {
  const removedLabel = state.labels.find(l => l.id === id);
  const removedLinks = state.links.filter(l => l.label_id === id).map(l => ({ ...l }));
  removeLabel(id); emit();
  try {
    const { error } = await getClient().from('tok_labels').delete().eq('id', id);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    if (removedLabel) upsertLabel(removedLabel);
    removedLinks.forEach(addLink); emit();
    throw e;
  }
}

export async function assignLabel(bulletIds, labelId) {
  const rows = bulletIds.map(bid => ({ bullet_id: bid, label_id: labelId }));
  const before = new Set(state.links.map(linkKey));
  rows.forEach(addLink); emit();
  try {
    const { error } = await getClient()
      .from('tok_bullet_labels')
      .upsert(rows, { onConflict: 'bullet_id,label_id', ignoreDuplicates: true });
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    rows.forEach(r => { if (!before.has(linkKey(r))) removeLink(r); }); emit();
    throw e;
  }
}

export async function unassignLabel(bulletIds, labelId) {
  const removed = state.links
    .filter(l => l.label_id === labelId && bulletIds.includes(l.bullet_id))
    .map(l => ({ ...l }));
  bulletIds.forEach(bid => removeLink({ bullet_id: bid, label_id: labelId })); emit();
  try {
    const { error } = await getClient()
      .from('tok_bullet_labels').delete().eq('label_id', labelId).in('bullet_id', bulletIds);
    if (error) throw error;
    notifyChanged();
  } catch (e) {
    removed.forEach(addLink); emit();
    throw e;
  }
}
