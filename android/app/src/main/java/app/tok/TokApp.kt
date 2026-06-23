package app.tok

import android.app.Application

/**
 * Applicatie-entry. Houdt de [app.tok.di.ServiceLocator] in leven (wordt in de
 * datalaag-fase gevuld met database, repository en sync-planning).
 */
class TokApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
