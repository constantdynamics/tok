package app.tok.speech

import android.content.Context
import android.net.Uri
import app.tok.data.prefs.TokPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import java.io.File
import java.io.IOException

/**
 * Beheert de twee Vosk-modellen (klein/groot): downloaden, uitpakken naar
 * filesDir, laden in het geheugen en de voortgang-status bijhouden.
 */
class ModelManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val prefs: TokPrefs,
) {
    enum class Tier(val key: String, val url: String, val approxMb: Int) {
        SMALL("small", "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip", 40),
        LARGE("large", "https://alphacephei.com/vosk/models/vosk-model-nl-0.22.zip", 1400),
    }

    sealed interface State {
        data object Absent : State
        data class Downloading(val percent: Int) : State
        data object Unpacking : State
        data object Loading : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _small = MutableStateFlow<State>(State.Absent)
    val small: StateFlow<State> = _small.asStateFlow()
    private val _large = MutableStateFlow<State>(State.Absent)
    val large: StateFlow<State> = _large.asStateFlow()

    @Volatile var smallModel: Model? = null
        private set

    @Volatile var largeModel: Model? = null
        private set

    fun loaded(tier: Tier): Model? = if (tier == Tier.SMALL) smallModel else largeModel

    fun state(tier: Tier): StateFlow<State> = if (tier == Tier.SMALL) small else large

    private fun flow(tier: Tier) = if (tier == Tier.SMALL) _small else _large
    private fun setState(tier: Tier, s: State) {
        flow(tier).value = s
    }

    private fun modelDir(tier: Tier) = File(context.filesDir, "models/${tier.key}")

    /** Download (indien nodig), pak uit en laad het model. Idempotent. */
    suspend fun ensure(tier: Tier): Boolean = withContext(Dispatchers.IO) {
        if (loaded(tier) != null) {
            setState(tier, State.Ready)
            return@withContext true
        }
        try {
            val root = resolveModelRoot(tier) ?: run {
                download(tier)
                resolveModelRoot(tier) ?: throw IOException("Model niet gevonden na uitpakken")
            }
            setState(tier, State.Loading)
            assign(tier, Model(root.absolutePath))
            setState(tier, State.Ready)
            markReady(tier, true)
            true
        } catch (e: Exception) {
            setState(tier, State.Failed(e.message ?: "Onbekende fout"))
            markReady(tier, false)
            false
        }
    }

    /** Importeer een vooraf gedownloade Vosk-zip via een content-uri. */
    suspend fun importFromZip(tier: Tier, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            setState(tier, State.Unpacking)
            val dir = modelDir(tier).apply { deleteRecursively(); mkdirs() }
            val tmp = File(context.cacheDir, "${tier.key}-import.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Kon bestand niet openen")
            ZipUtil.unzip(tmp, dir)
            tmp.delete()
            setState(tier, State.Loading)
            val root = resolveModelRoot(tier) ?: throw IOException("Geen geldig Vosk-model in de zip")
            assign(tier, Model(root.absolutePath))
            setState(tier, State.Ready)
            markReady(tier, true)
            true
        } catch (e: Exception) {
            setState(tier, State.Failed(e.message ?: "Importeren mislukt"))
            false
        }
    }

    private fun assign(tier: Tier, m: Model) {
        if (tier == Tier.SMALL) smallModel = m else largeModel = m
    }

    private suspend fun markReady(tier: Tier, ready: Boolean) {
        if (tier == Tier.SMALL) prefs.setSmallModelReady(ready) else prefs.setLargeModelReady(ready)
    }

    /** De map met een uitgepakt Vosk-model (bevat `conf`/`am`), of null. */
    private fun resolveModelRoot(tier: Tier): File? {
        val dir = modelDir(tier)
        if (!dir.isDirectory) return null
        if (isModelDir(dir)) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory && isModelDir(it) }
    }

    private fun isModelDir(f: File) = File(f, "conf").isDirectory || File(f, "am").isDirectory

    private fun download(tier: Tier) {
        setState(tier, State.Downloading(0))
        val dir = modelDir(tier).apply { deleteRecursively(); mkdirs() }
        val zip = File(context.cacheDir, "${tier.key}.zip")
        httpClient.newCall(Request.Builder().url(tier.url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download mislukt: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Lege download")
            val total = body.contentLength()
            var done = 0L
            zip.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) setState(tier, State.Downloading((done * 100 / total).toInt()))
                    }
                }
            }
        }
        setState(tier, State.Unpacking)
        ZipUtil.unzip(zip, dir)
        zip.delete()
    }
}
