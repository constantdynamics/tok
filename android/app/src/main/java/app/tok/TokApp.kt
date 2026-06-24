package app.tok

import android.app.Application
import app.tok.di.ServiceLocator

/** Applicatie-entry. Initialiseert de [ServiceLocator] (database, repo, netwerk). */
class TokApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
