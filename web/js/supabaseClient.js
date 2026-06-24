import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { SUPABASE_URL, SUPABASE_ANON_KEY } from './config.js';

// We bouwen de Supabase-client (her)op met een `x-pairing-token` header. Die header
// wordt door PostgREST in `request.headers` gezet en door de RLS-policies gecheckt.
let client = null;
let currentToken = null;

export function buildClient(token) {
  currentToken = token || null;
  const headers = {};
  if (currentToken) headers['x-pairing-token'] = currentToken;
  client = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers },
    auth: { persistSession: false, autoRefreshToken: false },
    realtime: { params: { eventsPerSecond: 10 } },
  });
  return client;
}

export function getClient() {
  if (!client) buildClient(currentToken);
  return client;
}

export function getToken() {
  return currentToken;
}
