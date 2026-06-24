import { getClient } from './supabaseClient.js';
import { notifyChanged } from './realtime.js';

// In-memory state. Mutaties werken optimistisch én via realtime; alle update-helpers
// zijn idempotent op id, dus dubbel toepassen kan geen kwaad.
export const state = {
  bullets: [],          // {id, text, created_at, updated_at, sort_order, is_archived}
  labels: [],           // {id, name, color, created_at}
  links: [],            // {bullet_id, label_id}
  filters: { labelIds: new Set(), from: null, to: null, archived: 'active' }, // active|archived|all
  sort: 'manual',       // manual|created_desc|created_asc|updated_desc
  selection: new Set(),
  selectMode: false,
};

let onChange = () => {};
export function subscribe(fn) { onChange = fn; }
export function emit() { onChange(); }

// ---- idempotente state-helpers (gebruikt door optimistische updates én realtime) ----
export function upsertBullet(row) {
  const i = state.bullets.findIndex(b => b.id === row.id);
  if (i === -1) state.bullets.push(row); else state.bullets[i] = { ...state.bullets[i], ...row };
}
export function removeBullet(id) {
  state.bullets = state.bullets.filter(b => b.id !== id);
  state.links = state.links.filter(l => l.bullet_id !== id);
  state.selection.delete(id);
}
export function upsertLabel(row) {
  const i = state.labels.findIndex(l => l.id === row.id);
  if (i === -1) state.labels.push(row); else state.labels[i] = { ...state.labels[i], ...row };
}
export function removeLabel(id) {
  state.labels = state.labels.filter(l => l.id !== id);
  state.links = state.links.filter(l => l.label_id !== id);
}
export function addLink(link) {
  if (!state.links.some(l => l.bullet_id === link.bullet_id && l.label_id === link.label_id)) {
    state.links.push({ bullet_id: link.bullet_id, label_id: link.label_id });
  }
}
export function removeLink(link) {
  state.links = state.links.filter(l => !(l.bullet_id === link.bullet_id && l.label_id === link.label_id));
}

export function labelsForBullet(bulletId) {
  const ids = new Set(state.links.filter(l => l.bullet_id === bulletId).map(l => l.label_id));
  return state.labels.filter(l => ids.has(l.id));
}

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

// ---- afgeleide lijst (filter + sort) ----
export function visibleBullets() {
  const f = state.filters;
  let list = state.bullets.filter(b => {
    if (f.archived === 'active' && b.is_archived) return false;
    if (f.archived === 'archived' && !b.is_archived) return false;
    if (f.from && new Date(b.created_at) < new Date(f.from)) return false;
    if (f.to && new Date(b.created_at) > new Date(f.to + 'T23:59:59')) return false;
    if (f.labelIds.size > 0) {
      const own = new Set(state.links.filter(l => l.bullet_id === b.id).map(l => l.label_id));
      // bullet moet ten minste één van de geselecteerde labels hebben
      if (![...f.labelIds].some(id => own.has(id))) return false;
    }
    return true;
  });
  const by = {
    manual: (a, b) => a.sort_order - b.sort_order,
    created_desc: (a, b) => new Date(b.created_at) - new Date(a.created_at),
    created_asc: (a, b) => new Date(a.created_at) - new Date(b.created_at),
    updated_desc: (a, b) => new Date(b.updated_at) - new Date(a.updated_at),
  }[state.sort];
  return list.sort(by);
}

// ============================================================================
// MUTATIES (optimistisch + persisteren naar Supabase)
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
  upsertBullet({ id, text }); emit();
  const { error } = await getClient().from('tok_bullets').update({ text }).eq('id', id);
  if (error) throw error;
  notifyChanged();
}

export async function setArchived(ids, isArchived) {
  ids.forEach(id => upsertBullet({ id, is_archived: isArchived })); emit();
  const { error } = await getClient().from('tok_bullets').update({ is_archived: isArchived }).in('id', ids);
  if (error) throw error;
  notifyChanged();
}

export async function deleteBullets(ids) {
  ids.forEach(removeBullet); emit();
  const { error } = await getClient().from('tok_bullets').delete().in('id', ids);
  if (error) throw error;
  notifyChanged();
}

export async function reorderBullet(id, newOrder) {
  upsertBullet({ id, sort_order: newOrder }); emit();
  const { error } = await getClient().from('tok_bullets').update({ sort_order: newOrder }).eq('id', id);
  if (error) throw error;
  notifyChanged();
}

export async function createLabel(name, color) {
  const { data, error } = await getClient()
    .from('tok_labels').insert({ name, color }).select().single();
  if (error) throw error;
  upsertLabel(data); emit(); notifyChanged();
  return data;
}

export async function updateLabel(id, fields) {
  upsertLabel({ id, ...fields }); emit();
  const { error } = await getClient().from('tok_labels').update(fields).eq('id', id);
  if (error) throw error;
  notifyChanged();
}

export async function deleteLabel(id) {
  removeLabel(id); emit();
  const { error } = await getClient().from('tok_labels').delete().eq('id', id);
  if (error) throw error;
  notifyChanged();
}

export async function assignLabel(bulletIds, labelId) {
  const rows = bulletIds.map(bid => ({ bullet_id: bid, label_id: labelId }));
  rows.forEach(addLink); emit();
  const { error } = await getClient()
    .from('tok_bullet_labels')
    .upsert(rows, { onConflict: 'bullet_id,label_id', ignoreDuplicates: true });
  if (error) throw error;
  notifyChanged();
}

export async function unassignLabel(bulletIds, labelId) {
  bulletIds.forEach(bid => removeLink({ bullet_id: bid, label_id: labelId })); emit();
  const { error } = await getClient()
    .from('tok_bullet_labels').delete().eq('label_id', labelId).in('bullet_id', bulletIds);
  if (error) throw error;
  notifyChanged();
}
