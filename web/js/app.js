import { readToken, verifyToken, redeemCode, clearToken } from './pairing.js';
import { loadAll, subscribe } from './store.js';
import { startRealtime } from './realtime.js';
import { initUI, render, showToast } from './ui.js';

const $ = (s) => document.querySelector(s);

async function showApp() {
  $('#pairing-screen').classList.add('hidden');
  $('#app-screen').classList.remove('hidden');
  initUI();
  subscribe(render);
  try {
    await loadAll();
  } catch (e) {
    showToast('Laden mislukt: ' + (e.message || e), 'error');
  }
  render();
  startRealtime();
}

function showPairing(msg) {
  $('#app-screen').classList.add('hidden');
  $('#pairing-screen').classList.remove('hidden');
  if (msg) $('#pair-error').textContent = msg;

  const input = $('#code-input');
  const btn = $('#pair-btn');
  const doPair = async () => {
    const code = input.value.replace(/\D/g, '').slice(0, 6);
    if (code.length !== 6) { $('#pair-error').textContent = 'Voer 6 cijfers in.'; return; }
    btn.disabled = true;
    $('#pair-error').textContent = '';
    try {
      await redeemCode(code, 'Webpagina');
      await showApp();
    } catch (e) {
      $('#pair-error').textContent = 'Ongeldige of verlopen code.';
      btn.disabled = false;
    }
  };
  btn.addEventListener('click', doPair);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') doPair(); });
  input.focus();
}

async function boot() {
  const token = readToken();
  if (token && await verifyToken(token)) { await showApp(); return; }
  if (token) clearToken();
  showPairing('');
}

boot();
