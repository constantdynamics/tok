package app.tok

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.tok.di.ServiceLocator
import app.tok.sync.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Applicatie-entry. Initialiseert de [ServiceLocator] en regelt de sync-strategie:
 * - na elke lokale push een realtime-"changed"-nudge naar andere apparaten;
 * - op de voorgrond: realtime verbonden + poll elke 12s (betrouwbare fallback);
 * - op de achtergrond: periodieke WorkManager-sync.
 */
class TokApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.repository.onLocalPushed = { ServiceLocator.realtime.notifyChanged() }
        SyncWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundSync())
    }

    private class ForegroundSync : DefaultLifecycleObserver {
        private var pollJob: Job? = null

        override fun onStart(owner: LifecycleOwner) {
            ServiceLocator.realtime.connect()
            pollJob?.cancel()
            pollJob = ServiceLocator.appScope.launch {
                while (isActive) {
                    ServiceLocator.repository.triggerSync()
                    delay(12_000)
                }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            ServiceLocator.realtime.disconnect()
            pollJob?.cancel()
            pollJob = null
        }
    }
}
