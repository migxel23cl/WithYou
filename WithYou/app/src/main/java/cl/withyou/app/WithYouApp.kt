package cl.withyou.app

import android.app.Application
import cl.withyou.app.di.AppContainer

class WithYouApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
