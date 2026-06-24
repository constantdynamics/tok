import Sortable from 'https://cdn.jsdelivr.net/npm/sortablejs@1.15.2/+esm';
import {
  state, visibleBullets, labelsForBullet,
  addBullet, updateBulletText, setArchived, deleteBullets, reorderBullet,
  createLabel, updateLabel, deleteLabel, assignLabel, unassignLabel,
  triageSelectedAsHandled,
} from './store.js';
import { createPairingCode } from './pairing.js';
import { createDictation, speechSupported } from './speech.js';

const $ = (s) => document.querySelector(s);

const NEON = ['#FF3CAC', '#FF6AD5', '#C774E8', '#AD8CFF', '#8795E8', '#94D0FF',
  '#39FF14', '#00F0FF', '#FFD93D', '#FF8E00', '#FF2E63', '#7CFF6B'];

// Mini hyperscript-helper.
function h(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'style') e.style.cssText = v;
    else if (k === 'dataset') Object.assign(e.dataset, v);
    else if (k === 'checked' || k === 'value' || k === 'disabled') e[k] = v;
    else if (k.startsWith('on') && typeof v === 'function') e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    e.append(kid.nodeType ? kid : document.createTextNode(kid));
  }
  return e;
}

let editingId = null;     // bullet dat nu bewerkt wordt (re-render uitstellen)
let pendingRender = false;
let marqueeActive = false; // tijdens sleep-selectie de list niet opnieuw tekenen
let sortable = null;
let pickerCb = null;
let toastT = null;

const fmtDate = (iso) => {
  const d = new Date(iso);
  return d.toLocaleDateString('nl-NL', { day: 'numeric', month: 'short' }) + ' ' +
    d.toLocaleTimeString('nl-NL', { hour: '2-digit', minute: '2-digit' });
};
const autoGrow = (ta) => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; };

export function showToast(msg, type = 'info') {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast ' + type;
  clearTimeout(toastT);
  toastT = setTimeout(() => t.classList.add('hidden'), 3200);
}
const showError = (e) => { console.error(e); showToast(e?.message || 'Er ging iets mis', 'error'); };

// ============================================================================
// RENDER
// ============================================================================
export function render() {
  $('#sort-select').value = state.sort;
  $('#select-btn').textContent = state.selectMode ? 'Klaar' : 'Selecteren';
  $('#select-btn').classList.toggle('active', state.selectMode);
  renderFilters();
  renderBulkBar();
  if (!$('#label-modal').classList.contains('hidden')) renderLabelModal();
  if (editingId || marqueeActive) { pendingRender = true; return; }
  renderList();
}

function renderList() {
  const list = $('#bullet-list');
  list.innerHTML = '';
  const items = visibleBullets();
  $('#empty-state').classList.toggle('hidden', items.length > 0);
  items.forEach((b) => list.append(renderBullet(b)));
  setupSortable();
}

function setupSortable() {
  if (sortable) { sortable.destroy(); sortable = null; }
  if (state.sort !== 'manual' || state.selectMode) return;
  sortable = new Sortable($('#bullet-list'), {
    handle: '.drag-handle', animation: 150, onEnd: onDragEnd,
  });
}

async function onDragEnd(evt) {
  const id = evt.item.dataset.id;
  const rows = [...$('#bullet-list').children];
  const idx = rows.indexOf(evt.item);
  const orderOf = (el) => state.bullets.find((b) => b.id === el?.dataset.id)?.sort_order ?? 0;
  const prev = rows[idx - 1], next = rows[idx + 1];
  let newOrder;
  if (!prev && !next) newOrder = 0;
  else if (!prev) newOrder = orderOf(next) - 1;
  else if (!next) newOrder = orderOf(prev) + 1;
  else newOrder = (orderOf(prev) + orderOf(next)) / 2;
  reorderBullet(id, newOrder).catch(showError);
}

function renderBullet(b) {
  const selected = state.selection.has(b.id);
  const chips = labelsForBullet(b.id).map((l) =>
    labelChip(l, state.selectMode ? null : () => unassignLabel([b.id], l.id).catch(showError)));

  const ta = h('textarea', {
    class: 'bullet-text', rows: 1,
    onfocus: () => { editingId = b.id; },
    onblur: (e) => onTextBlur(b, e.target),
    oninput: (e) => autoGrow(e.target),
  });
  ta.value = b.text;

  const row = h('div', { class: `bullet${selected ? ' selected' : ''}${b.is_archived ? ' archived' : ''}`, dataset: { id: b.id } },
    state.sort === 'manual' && !state.selectMode ? h('span', { class: 'drag-handle', title: 'Sleep om te herordenen' }, '⠿') : null,
    state.selectMode ? h('input', { type: 'checkbox', class: 'check', checked: selected, onchange: () => toggleSelect(b.id) }) : null,
    h('div', { class: 'bullet-main' },
      ta,
      h('div', { class: 'bullet-meta' },
        h('div', { class: 'chips' }, ...chips,
          !state.selectMode ? h('button', { class: 'chip-add', title: 'Label toevoegen',
            onclick: () => openPicker('Label toevoegen', (lid) => assignLabel([b.id], lid).catch(showError)) }, '+') : null),
        h('span', { class: 'date' }, fmtDate(b.created_at)),
      ),
    ),
  );
  requestAnimationFrame(() => autoGrow(ta));
  if (state.selectMode) {
    row.addEventListener('click', (e) => {
      if (e.target.tagName !== 'TEXTAREA' && e.target.type !== 'checkbox') toggleSelect(b.id);
    });
  }
  return row;
}

function onTextBlur(b, ta) {
  editingId = null;
  const v = ta.value.trim();
  if (v !== b.text) updateBulletText(b.id, v).catch(showError);
  if (pendingRender && !marqueeActive) { pendingRender = false; renderList(); }
}

function labelChip(l, onRemove) {
  return h('span', { class: 'chip', style: `--c:${l.color}` },
    h('span', { class: 'chip-dot' }), l.name,
    onRemove ? h('button', { class: 'chip-x', onclick: (e) => { e.stopPropagation(); onRemove(); } }, '×') : null);
}

function toggleSelect(id) {
  state.selection.has(id) ? state.selection.delete(id) : state.selection.add(id);
  render();
}

function renderBulkBar() {
  $('#bulk-bar').classList.toggle('hidden', !state.selectMode);
  $('#bulk-count').textContent = `${state.selection.size} geselecteerd`;
}

function renderFilters() {
  const wrap = $('#filter-labels');
  wrap.innerHTML = '';
  if (state.labels.length === 0) wrap.append(h('span', { class: 'muted' }, 'Nog geen labels'));
  for (const l of state.labels) {
    const on = state.filters.labelIds.has(l.id);
    wrap.append(h('button', { class: `chip toggle${on ? ' on' : ''}`, style: `--c:${l.color}`,
      onclick: () => { on ? state.filters.labelIds.delete(l.id) : state.filters.labelIds.add(l.id); render(); } },
      h('span', { class: 'chip-dot' }), l.name));
  }
  for (const btn of $('#filter-archived').children)
    btn.classList.toggle('active', btn.dataset.val === state.filters.archived);
}

// ---- Label-beheer modal ----
function renderLabelModal() {
  const list = $('#label-list');
  list.innerHTML = '';
  if (state.labels.length === 0) list.append(h('p', { class: 'muted' }, 'Nog geen labels.'));
  for (const l of state.labels) {
    list.append(h('div', { class: 'label-item' },
      h('span', { class: 'chip', style: `--c:${l.color}` }, h('span', { class: 'chip-dot' }), l.name),
      h('input', { type: 'color', class: 'color-input', value: l.color,
        onchange: (e) => updateLabel(l.id, { color: e.target.value }).catch(showError) }),
      h('input', { class: 'label-name', value: l.name,
        onchange: (e) => { const n = e.target.value.trim(); if (n) updateLabel(l.id, { name: n }).catch(showError); } }),
      h('button', { class: 'btn btn-danger small',
        onclick: () => { if (confirm(`Label "${l.name}" verwijderen?`)) deleteLabel(l.id).catch(showError); } }, 'Verwijder'),
    ));
  }
}

// ---- Label-kiezer (popover) ----
function openPicker(title, cb) {
  pickerCb = cb;
  $('#picker-title').textContent = title;
  const chips = $('#picker-chips');
  chips.innerHTML = '';
  if (state.labels.length === 0)
    chips.append(h('p', { class: 'muted' }, 'Nog geen labels. Maak er eerst een via "Labels".'));
  for (const l of state.labels)
    chips.append(h('button', { class: 'chip', style: `--c:${l.color}`,
      onclick: () => { cb(l.id); closePicker(); } }, h('span', { class: 'chip-dot' }), l.name));
  $('#label-picker').classList.remove('hidden');
}
function closePicker() { $('#label-picker').classList.add('hidden'); pickerCb = null; }

// ============================================================================
// INIT (statische handlers, één keer)
// ============================================================================
export function initUI() {
  $('#sort-select').addEventListener('change', (e) => { state.sort = e.target.value; render(); });
  $('#filter-btn').addEventListener('click', () => $('#filter-panel').classList.toggle('hidden'));
  $('#labels-btn').addEventListener('click', () => { renderLabelModal(); $('#label-modal').classList.remove('hidden'); });
  $('#select-btn').addEventListener('click', () => {
    state.selectMode = !state.selectMode;
    if (!state.selectMode) state.selection.clear();
    render();
  });

  let dictation = null;
  const add = () => {
    const v = $('#add-input').value.trim();
    if (!v) return;
    $('#add-input').value = '';
    dictation?.reset(); // volgende gesproken zin begint vers
    addBullet(v).catch(showError);
  };
  $('#add-btn').addEventListener('click', add);
  $('#add-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') add(); });

  // ---- Microfoon: spraak-naar-bullet (Web Speech API) ----
  const micBtn = $('#mic-btn');
  const addInput = $('#add-input');
  if (!speechSupported()) {
    micBtn.disabled = true;
    micBtn.title = 'Spraakherkenning wordt niet ondersteund in deze browser — gebruik Chrome of Edge.';
  } else {
    dictation = createDictation({
      cutWord: 'tak',
      commands: [{ re: /(kopieer|copieer|kopiëer) tekst uit bullets?/i, name: 'copyHandled' }],
      onText: (text) => { addInput.value = text; },
      onCommit: (text) => { addBullet(text).catch(showError); }, // signaalwoord "tak"
      onCommand: (name) => {
        if (name === 'copyHandled') copyHandled();
        dictation.reset();
        addInput.value = '';
      },
      onState: (on) => {
        micBtn.classList.toggle('listening', on);
        addInput.classList.toggle('dictating', on);
        micBtn.title = on ? 'Stop met inspreken' : 'Inspreken';
        if (on) addInput.focus();
      },
      onError: (err) => {
        if (err === 'not-allowed' || err === 'service-not-allowed')
          showToast('Geef de microfoon toestemming in je browser.', 'error');
        else if (err === 'network')
          showToast('Spraakherkenning vereist internet.', 'error');
        else showToast('Spraakfout: ' + err, 'error');
      },
    });
    micBtn.addEventListener('click', () => {
      if (dictation.isListening()) dictation.stop();
      else dictation.start(addInput.value.trim());
    });
  }

  setupMarquee();

  $('#filter-from').addEventListener('change', (e) => { state.filters.from = e.target.value || null; render(); });
  $('#filter-to').addEventListener('change', (e) => { state.filters.to = e.target.value || null; render(); });
  for (const btn of $('#filter-archived').children)
    btn.addEventListener('click', () => { state.filters.archived = btn.dataset.val; render(); });
  $('#clear-filters').addEventListener('click', () => {
    state.filters = { labelIds: new Set(), from: null, to: null, archived: 'active' };
    $('#filter-from').value = ''; $('#filter-to').value = '';
    render();
  });

  // Bulk-acties
  const sel = () => [...state.selection];
  $('#bulk-select-all').addEventListener('click', () => { visibleBullets().forEach((b) => state.selection.add(b.id)); render(); });
  $('#bulk-assign').addEventListener('click', () => { if (sel().length) openPicker('Label toekennen', (lid) => assignLabel(sel(), lid).catch(showError)); });
  $('#bulk-unassign').addEventListener('click', () => { if (sel().length) openPicker('Label verwijderen', (lid) => unassignLabel(sel(), lid).catch(showError)); });
  $('#bulk-archive').addEventListener('click', () => { if (sel().length) setArchived(sel(), true).then(() => { state.selection.clear(); render(); }).catch(showError); });
  $('#bulk-unarchive').addEventListener('click', () => { if (sel().length) setArchived(sel(), false).then(() => { state.selection.clear(); render(); }).catch(showError); });
  $('#bulk-delete').addEventListener('click', () => {
    const ids = sel();
    if (ids.length && confirm(`${ids.length} bullet(s) definitief verwijderen?`))
      deleteBullets(ids).then(() => { state.selection.clear(); render(); }).catch(showError);
  });
  $('#bulk-handled').addEventListener('click', copyHandled);

  // Modals
  $('#label-modal-close').addEventListener('click', () => $('#label-modal').classList.add('hidden'));
  $('#picker-close').addEventListener('click', closePicker);
  $('#label-modal').addEventListener('click', (e) => { if (e.target.id === 'label-modal') e.target.classList.add('hidden'); });
  $('#label-picker').addEventListener('click', (e) => { if (e.target.id === 'label-picker') closePicker(); });

  // Apparaat koppelen: genereer een code en toon 'm.
  $('#device-btn').addEventListener('click', async () => {
    $('#device-code').textContent = '…';
    $('#device-modal').classList.remove('hidden');
    try {
      $('#device-code').textContent = await createPairingCode();
    } catch (e) {
      $('#device-modal').classList.add('hidden');
      showError(e);
    }
  });
  $('#device-modal-close').addEventListener('click', () => $('#device-modal').classList.add('hidden'));
  $('#device-modal').addEventListener('click', (e) => { if (e.target.id === 'device-modal') e.target.classList.add('hidden'); });

  const newLabel = () => {
    const name = $('#new-label-name').value.trim();
    if (!name) return;
    createLabel(name, $('#new-label-color').value).then(() => { $('#new-label-name').value = ''; }).catch(showError);
  };
  $('#new-label-btn').addEventListener('click', newLabel);
  $('#new-label-name').addEventListener('keydown', (e) => { if (e.key === 'Enter') newLabel(); });

  const palette = $('#neon-palette');
  NEON.forEach((c) => palette.append(h('button', { class: 'swatch', style: `background:${c}`,
    title: c, onclick: () => { $('#new-label-color').value = c; } })));
}

// ============================================================================
// SLEEP-SELECTIE (marquee): sleep met de muis een kader over de bullets
// ============================================================================
function setupMarquee() {
  const list = $('#bullet-list');
  let startX = 0, startY = 0, dragging = false, marqueeEl = null, marqueeSel = new Set();

  list.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    // niet starten op knoppen/chips/checkbox/sleep-greep
    if (e.target.closest('button, input, .chip, .chip-add, .chip-x, .drag-handle, select, a')) return;
    startX = e.clientX; startY = e.clientY; dragging = false; marqueeSel = new Set();
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });

  function onMove(e) {
    if (!dragging) {
      if (Math.abs(e.clientX - startX) < 6 && Math.abs(e.clientY - startY) < 6) return;
      dragging = true;
      marqueeActive = true;
      document.body.classList.add('selecting');
      document.activeElement?.blur?.();
      marqueeEl = h('div', { class: 'marquee' });
      document.body.append(marqueeEl);
    }
    window.getSelection()?.removeAllRanges();
    const x1 = Math.min(startX, e.clientX), y1 = Math.min(startY, e.clientY);
    const x2 = Math.max(startX, e.clientX), y2 = Math.max(startY, e.clientY);
    Object.assign(marqueeEl.style, { left: `${x1}px`, top: `${y1}px`, width: `${x2 - x1}px`, height: `${y2 - y1}px` });
    marqueeSel = new Set();
    for (const row of list.children) {
      const r = row.getBoundingClientRect();
      const hit = !(r.right < x1 || r.left > x2 || r.bottom < y1 || r.top > y2);
      row.classList.toggle('selected', hit);
      if (hit && row.dataset.id) marqueeSel.add(row.dataset.id);
    }
  }

  function onUp() {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    if (!dragging) return;
    dragging = false;
    marqueeActive = false;
    document.body.classList.remove('selecting');
    if (marqueeEl) { marqueeEl.remove(); marqueeEl = null; }
    state.selection = marqueeSel;
    if (marqueeSel.size > 0) state.selectMode = true;
    render();
  }
}

// ============================================================================
// "Afgehandeld": kopieer tekst van de selectie + label 'Afgehandeld',
// alle overige actieve bullets 'nog af te handelen'. (Knop én spraakcommando.)
// ============================================================================
async function copyHandled() {
  const ids = [...state.selection];
  if (!ids.length) { showToast('Selecteer eerst bullets (sleep eroverheen of vink aan).', 'error'); return; }
  const texts = ids.map((id) => state.bullets.find((b) => b.id === id)?.text || '').filter(Boolean);
  try { await navigator.clipboard.writeText(texts.join('\n')); } catch (_) { /* clipboard kan geweigerd worden */ }
  try {
    await triageSelectedAsHandled(ids);
    showToast(`${ids.length}x gekopieerd + 'Afgehandeld'; de rest 'nog af te handelen'.`);
    state.selection.clear(); state.selectMode = false; render();
  } catch (e) { showError(e); }
}
