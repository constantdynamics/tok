package app.tok.di

import android.content.Context
import app.tok.data.local.TokDatabase
import app.tok.data.prefs.TokPrefs
import app.tok.data.remote.SupabaseRest
import app.tok.data.repo.TokRepository
import app.tok.speech.ModelManager
import app.tok.speech.SpeechController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Eenvoudige handmatige DI — geen Hilt, minder bewegende delen. */
object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val prefs: TokPrefs by lazy { TokPrefs(appContext) }
    val rest: SupabaseRest by lazy { SupabaseRest(httpClient, json) }
    private val db: TokDatabase by lazy { TokDatabase.get(appContext) }

    val repository: TokRepository by lazy {
        TokRepository(db.bulletDao(), db.labelDao(), db.crossRefDao(), rest, prefs, appScope)
    }

    val modelManager: ModelManager by lazy { ModelManager(appContext, httpClient, prefs) }
    val speechController: SpeechController by lazy { SpeechController(modelManager, appScope) }
}
