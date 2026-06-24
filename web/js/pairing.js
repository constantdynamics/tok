import { getClient, buildClient, getToken } from './supabaseClient.js';

// Het pairing-token bewaren we in een cookie (geen localStorage), 90 dagen geldig.
const COOKIE = 'tok_token';

export function saveToken(token) {
  const exp = new Date(Date.now() + 90 * 24 * 3600 * 1000).toUTCString();
  document.cookie = `${COOKIE}=${encodeURIComponent(token)}; expires=${exp}; path=/; SameSite=Strict`;
}

export function readToken() {
  const m = document.cookie.match(new RegExp('(?:^|; )' + COOKIE + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}

export function clearToken() {
  document.cookie = `${COOKIE}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; SameSite=Strict`;
}

// Wissel een 6-cijferige code in voor een pairing-token (RPC draait zonder token).
export async function redeemCode(code, deviceName) {
  buildClient(null);
  const { data, error } = await getClient().rpc('tok_redeem_pairing_code', {
    p_code: code,
    p_device_name: deviceName || 'Webpagina',
  });
  if (error) throw error;
  const token = data;
  saveToken(token);
  buildClient(token);
  return token;
}

// Controleer of een bestaand token nog geldig is via een lichte, gegate query.
export async function verifyToken(token) {
  buildClient(token);
  const { error } = await getClient().from('tok_bullets').select('id').limit(1);
  return !error;
}

// Verleng het huidige token bij gebruik ("touch"): de server schuift expires_at op
// als die binnenkort verloopt, zodat actieve apparaten niet onverwacht ontkoppeld
// raken. Best-effort: faalt dit, dan blijft het token gewoon op zijn oude datum staan.
export async function touchToken() {
  try {
    const { error } = await getClient().rpc('tok_touch_token');
    if (error) return;
    const t = getToken();
    if (t) saveToken(t); // verleng ook de browsercookie met 90 dagen
  } catch {
    /* genegeerd: token blijft geldig tot de oorspronkelijke vervaldatum */
  }
}

// Genereer een nieuwe 6-cijferige koppelcode (om een telefoon of ander apparaat
// te koppelen). Vereist een geldig token; de code is ~10 minuten geldig.
export async function createPairingCode() {
  const { data, error } = await getClient().rpc('tok_create_pairing_code', { p_device_name: null });
  if (error) throw error;
  return data;
}
