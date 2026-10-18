package app.lart.mobile

import android.app.Application
import app.lart.mobile.data.Repository

class LartApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
    }
}
