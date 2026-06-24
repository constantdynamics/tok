// Pure, framework- en netwerkloze kern van de state. Géén imports → makkelijk te
// unit-testen (zie test/store-core.test.js). store.js bouwt hierop de Supabase-
// mutaties. Alle helpers zijn idempotent op id, zodat dubbel toepassen (optimistische
// update + realtime-echo) geen kwaad kan.

// In-memory state.
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

// ---- afgeleide lijst (filter + sort) ----
export function visibleBullets() {
  const f = state.filters;
  const list = state.bullets.filter(b => {
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
