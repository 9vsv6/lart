package app.lart.mobile

import android.app.Application
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Tmdb
import app.lart.mobile.ui.LartDisplay
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class LartApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        // Look-and-feel settings reach every screen, the player's too, through LartDisplay.
        val main = MainScope()
        main.launch {
            repository.settings.collect {
                LartDisplay.settings = it
                Tmdb.languageOverride = it.metadataLanguage
            }
        }
        main.launch { repository.rpdbKey.collect { LartDisplay.rpdbKey = it } }
    }
}
