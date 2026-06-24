package app.tok.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tok_prefs")

/** Pairing-token, apparaatnaam en model-/sync-instellingen. */
class TokPrefs(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("pairing_token")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val LARGE_MODEL_ENABLED = booleanPreferencesKey("large_model_enabled")
        val LARGE_MODEL_READY = booleanPreferencesKey("large_model_ready")
        val SMALL_MODEL_READY = booleanPreferencesKey("small_model_ready")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }
    suspend fun tokenOnce(): String? = token.first()
    suspend fun setToken(value: String?) = context.dataStore.edit {
        if (value == null) it.remove(Keys.TOKEN) else it[Keys.TOKEN] = value
    }

    val deviceName: Flow<String> = context.dataStore.data.map { it[Keys.DEVICE_NAME] ?: defaultDeviceName() }
    suspend fun deviceNameOnce(): String = deviceName.first()
    suspend fun setDeviceName(value: String) = context.dataStore.edit { it[Keys.DEVICE_NAME] = value }

    val largeModelEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.LARGE_MODEL_ENABLED] ?: false }
    suspend fun setLargeModelEnabled(value: Boolean) = context.dataStore.edit { it[Keys.LARGE_MODEL_ENABLED] = value }

    val smallModelReady: Flow<Boolean> = context.dataStore.data.map { it[Keys.SMALL_MODEL_READY] ?: false }
    suspend fun setSmallModelReady(value: Boolean) = context.dataStore.edit { it[Keys.SMALL_MODEL_READY] = value }

    val largeModelReady: Flow<Boolean> = context.dataStore.data.map { it[Keys.LARGE_MODEL_READY] ?: false }
    suspend fun setLargeModelReady(value: Boolean) = context.dataStore.edit { it[Keys.LARGE_MODEL_READY] = value }

    suspend fun setLastSync(epochMillis: Long) = context.dataStore.edit { it[Keys.LAST_SYNC] = epochMillis }

    private fun defaultDeviceName(): String = "Telefoon (${android.os.Build.MODEL})"
}
