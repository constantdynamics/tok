package app.tok.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimale Supabase Realtime-client over een **broadcast**-kanaal. We gebruiken
 * bewust geen `postgres_changes`: onze RLS gate't lezen op het pairing-token, en
 * de realtime-RLS-check (anon-rol, geen header) zou dan niets doorlaten. Broadcast
 * is een simpele pub/sub-nudge: na een lokale push sturen we "changed", andere
 * apparaten pullen daarop. Best-effort — de poll-fallback garandeert correctheid.
 */
class RealtimeClient(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val onRemoteChange: () -> Unit,
) {
    private val topic = "realtime:tok-sync"
    private var ws: WebSocket? = null
    private var heartbeat: Job? = null
    private val ref = AtomicInteger(1)

    @Volatile private var wantConnected = false

    fun connect() {
        wantConnected = true
        if (ws == null) open()
    }

    fun disconnect() {
        wantConnected = false
        heartbeat?.cancel()
        heartbeat = null
        ws?.close(1000, null)
        ws = null
    }

    /** Stuur een "changed"-nudge naar de andere apparaten. */
    fun notifyChanged() {
        val socket = ws ?: return
        socket.send(
            JSONObject()
                .put("topic", topic)
                .put("event", "broadcast")
                .put(
                    "payload",
                    JSONObject().put("type", "broadcast").put("event", "changed").put("payload", JSONObject()),
                )
                .put("ref", ref.getAndIncrement().toString())
                .toString(),
        )
    }

    private fun open() {
        val url = "${SupabaseConfig.REALTIME_URL}?apikey=${SupabaseConfig.ANON_KEY}&vsn=1.0.0"
        ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(
                JSONObject()
                    .put("topic", topic)
                    .put("event", "phx_join")
                    .put(
                        "payload",
                        JSONObject().put(
                            "config",
                            JSONObject().put("broadcast", JSONObject().put("self", false).put("ack", false)),
                        ),
                    )
                    .put("ref", ref.getAndIncrement().toString())
                    .toString(),
            )
            heartbeat?.cancel()
            heartbeat = scope.launch {
                while (isActive) {
                    delay(25_000)
                    webSocket.send(
                        JSONObject()
                            .put("topic", "phoenix")
                            .put("event", "heartbeat")
                            .put("payload", JSONObject())
                            .put("ref", ref.getAndIncrement().toString())
                            .toString(),
                    )
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val obj = JSONObject(text)
                if (obj.optString("event") == "broadcast") {
                    val payload = obj.optJSONObject("payload")
                    if (payload?.optString("event") == "changed") onRemoteChange()
                }
            } catch (_: Exception) {
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ws = null
            heartbeat?.cancel()
            if (wantConnected) {
                scope.launch {
                    delay(5_000)
                    if (wantConnected && ws == null) open()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ws = null
            heartbeat?.cancel()
        }
    }
}
