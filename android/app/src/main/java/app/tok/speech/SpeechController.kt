package app.tok.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Bestuurt de Vosk-herkenning. Houdt een live `partial` bij plus de vastgezette
 * `segments` (zinnen) sinds de laatste knip. Start met het beste klaarstaande
 * model en schakelt — als het grote model intussen geladen is — bij de
 * eerstvolgende pauze (onResult) over zonder de buffer te verliezen.
 */
class SpeechController(
    private val modelManager: ModelManager,
    private val scope: CoroutineScope,
) : RecognitionListener {

    private val sampleRate = 16000f

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    @Volatile private var collecting = false
    @Volatile private var wantUpgrade = false
    @Volatile private var activeTier = ModelManager.Tier.SMALL

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _segments = MutableStateFlow<List<String>>(emptyList())
    val segments: StateFlow<List<String>> = _segments.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _tier = MutableStateFlow(ModelManager.Tier.SMALL)
    val tier: StateFlow<ModelManager.Tier> = _tier.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Start de herkenning. Met [preferLarge] wordt naar het grote model toegewerkt. */
    fun start(preferLarge: Boolean) {
        if (_listening.value) return
        val useLarge = preferLarge && modelManager.loaded(ModelManager.Tier.LARGE) != null
        val tierToUse = if (useLarge) ModelManager.Tier.LARGE else ModelManager.Tier.SMALL
        val model = modelManager.loaded(tierToUse)
        if (model == null) {
            _error.value = "Spraakmodel is nog niet geladen."
            return
        }
        wantUpgrade = preferLarge && tierToUse == ModelManager.Tier.SMALL
        startWith(tierToUse)
    }

    private fun startWith(tier: ModelManager.Tier) {
        try {
            val model = modelManager.loaded(tier) ?: return
            val rec = Recognizer(model, sampleRate)
            val svc = SpeechService(rec, sampleRate)
            recognizer = rec
            speechService = svc
            activeTier = tier
            _tier.value = tier
            collecting = true
            svc.startListening(this)
            _listening.value = true
            _error.value = null
        } catch (e: Exception) {
            _error.value = e.message
            _listening.value = false
        }
    }

    /** Stop de opname; de huidige partial wordt als laatste segment vastgezet. */
    fun stop() {
        collecting = false
        flushPartial()
        release()
        _listening.value = false
    }

    fun cancel() {
        collecting = false
        release()
        _segments.value = emptyList()
        _partial.value = ""
        _listening.value = false
    }

    /** Knip: alle tekst (segmenten + partial) terug en de buffer leegmaken. */
    fun cutCurrentText(): String {
        flushPartial()
        val text = _segments.value.joinToString(" ").trim()
        _segments.value = emptyList()
        _partial.value = ""
        return text
    }

    private fun flushPartial() {
        val p = _partial.value.trim()
        if (p.isNotEmpty()) {
            _segments.value = _segments.value + p
            _partial.value = ""
        }
    }

    private fun release() {
        try {
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    // ----------------------------------------------------- RecognitionListener
    override fun onPartialResult(hypothesis: String?) {
        if (!collecting) return
        _partial.value = hypothesis?.let { JSONObject(it).optString("partial") }?.trim().orEmpty()
    }

    override fun onResult(hypothesis: String?) {
        if (!collecting) return
        val text = hypothesis?.let { JSONObject(it).optString("text") }?.trim().orEmpty()
        if (text.isNotEmpty()) _segments.value = _segments.value + text
        _partial.value = ""
        maybeUpgrade()
    }

    override fun onFinalResult(hypothesis: String?) {
        if (!collecting) return
        val text = hypothesis?.let { JSONObject(it).optString("text") }?.trim().orEmpty()
        if (text.isNotEmpty()) _segments.value = _segments.value + text
        _partial.value = ""
    }

    override fun onError(exception: Exception?) {
        _error.value = exception?.message
    }

    override fun onTimeout() {}

    /** Schakel bij een natuurlijke pauze over naar het grote model (off-thread). */
    private fun maybeUpgrade() {
        if (!wantUpgrade) return
        if (modelManager.loaded(ModelManager.Tier.LARGE) == null) return
        if (activeTier == ModelManager.Tier.LARGE) {
            wantUpgrade = false
            return
        }
        wantUpgrade = false
        // Niet op de herkennings-thread shutdownen -> via een coroutine.
        scope.launch(Dispatchers.Default) {
            collecting = false
            release()
            startWith(ModelManager.Tier.LARGE)
        }
    }
}
