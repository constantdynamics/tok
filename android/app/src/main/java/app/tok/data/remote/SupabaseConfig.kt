package app.tok.data.remote

/**
 * Verbindingsgegevens voor het gedeelde Supabase-project "eten-avontuur".
 * De anon key is publiek-by-design (zit ook in de webpagina); de beveiliging
 * loopt via RLS + het pairing-token dat als `x-pairing-token`-header meegaat.
 */
object SupabaseConfig {
    const val URL = "https://wmdopfocqufsquzvemka.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndtZG9wZm9jcXVmc3F1enZlbWthIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg4NTQ0NDcsImV4cCI6MjA5NDQzMDQ0N30.-NlpzYaBBQajFcwEQODxMj2vkUYOqWoFX4AkMng9_30"

    const val REST_URL = "$URL/rest/v1"
    const val REALTIME_URL = "wss://wmdopfocqufsquzvemka.supabase.co/realtime/v1/websocket"
}
