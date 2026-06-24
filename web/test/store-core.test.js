import { beforeEach, describe, expect, it } from 'vitest';
import {
  state,
  upsertBullet,
  removeBullet,
  upsertLabel,
  removeLabel,
  addLink,
  removeLink,
  labelsForBullet,
  visibleBullets,
} from '../js/store-core.js';

function reset() {
  state.bullets = [];
  state.labels = [];
  state.links = [];
  state.filters = { labelIds: new Set(), from: null, to: null, archived: 'active' };
  state.sort = 'manual';
  state.selection = new Set();
  state.selectMode = false;
}

beforeEach(reset);

describe('idempotente state-helpers', () => {
  it('upsertBullet voegt toe en werkt bij op id', () => {
    upsertBullet({ id: 'a', text: 'hoi', sort_order: 1 });
    expect(state.bullets).toHaveLength(1);
    upsertBullet({ id: 'a', text: 'aangepast' });
    expect(state.bullets).toHaveLength(1);
    expect(state.bullets[0].text).toBe('aangepast');
    expect(state.bullets[0].sort_order).toBe(1); // merge behoudt bestaande velden
  });

  it('removeBullet verwijdert ook koppelingen en selectie', () => {
    upsertBullet({ id: 'a', text: 'x' });
    addLink({ bullet_id: 'a', label_id: 'L' });
    state.selection.add('a');
    removeBullet('a');
    expect(state.bullets).toHaveLength(0);
    expect(state.links).toHaveLength(0);
    expect(state.selection.has('a')).toBe(false);
  });

  it('addLink is idempotent, removeLink verwijdert', () => {
    addLink({ bullet_id: 'a', label_id: 'L' });
    addLink({ bullet_id: 'a', label_id: 'L' });
    expect(state.links).toHaveLength(1);
    removeLink({ bullet_id: 'a', label_id: 'L' });
    expect(state.links).toHaveLength(0);
  });

  it('removeLabel verwijdert label en bijbehorende koppelingen', () => {
    upsertLabel({ id: 'L', name: 'werk', color: '#fff' });
    addLink({ bullet_id: 'a', label_id: 'L' });
    removeLabel('L');
    expect(state.labels).toHaveLength(0);
    expect(state.links).toHaveLength(0);
  });

  it('labelsForBullet geeft alleen gekoppelde labels', () => {
    upsertLabel({ id: 'L1', name: 'a', color: '#1' });
    upsertLabel({ id: 'L2', name: 'b', color: '#2' });
    addLink({ bullet_id: 'b1', label_id: 'L1' });
    const result = labelsForBullet('b1');
    expect(result.map((l) => l.id)).toEqual(['L1']);
  });
});

describe('visibleBullets — filteren', () => {
  beforeEach(() => {
    upsertBullet({ id: 'a', text: 'actief', created_at: '2026-01-01', sort_order: 2, is_archived: false });
    upsertBullet({ id: 'b', text: 'archief', created_at: '2026-02-01', sort_order: 1, is_archived: true });
  });

  it('toont standaard alleen actieve bullets', () => {
    expect(visibleBullets().map((b) => b.id)).toEqual(['a']);
  });

  it('archived-filter toont alleen gearchiveerde', () => {
    state.filters.archived = 'archived';
    expect(visibleBullets().map((b) => b.id)).toEqual(['b']);
  });

  it('all-filter toont alles, gesorteerd op sort_order (manual)', () => {
    state.filters.archived = 'all';
    expect(visibleBullets().map((b) => b.id)).toEqual(['b', 'a']);
  });

  it('label-filter vereist minstens één geselecteerd label', () => {
    state.filters.archived = 'all';
    addLink({ bullet_id: 'a', label_id: 'L' });
    state.filters.labelIds = new Set(['L']);
    expect(visibleBullets().map((b) => b.id)).toEqual(['a']);
  });

  it('datumfilter from/to begrenst op created_at', () => {
    state.filters.archived = 'all';
    state.filters.from = '2026-01-15';
    expect(visibleBullets().map((b) => b.id)).toEqual(['b']);
  });
});

describe('visibleBullets — sorteren', () => {
  beforeEach(() => {
    upsertBullet({ id: 'oud', text: '1', created_at: '2026-01-01', updated_at: '2026-01-01', sort_order: 5 });
    upsertBullet({ id: 'nieuw', text: '2', created_at: '2026-03-01', updated_at: '2026-02-01', sort_order: 1 });
  });

  it('manual sorteert op sort_order oplopend', () => {
    state.sort = 'manual';
    expect(visibleBullets().map((b) => b.id)).toEqual(['nieuw', 'oud']);
  });

  it('created_desc zet nieuwste eerst', () => {
    state.sort = 'created_desc';
    expect(visibleBullets().map((b) => b.id)).toEqual(['nieuw', 'oud']);
  });

  it('created_asc zet oudste eerst', () => {
    state.sort = 'created_asc';
    expect(visibleBullets().map((b) => b.id)).toEqual(['oud', 'nieuw']);
  });

  it('updated_desc sorteert op updated_at', () => {
    state.sort = 'updated_desc';
    expect(visibleBullets().map((b) => b.id)).toEqual(['nieuw', 'oud']);
  });
});
