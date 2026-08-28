package website.xihan.pbra

import android.app.Application

object AppContext {
    @Volatile
    lateinit var application: Application
        private set

    fun init(app: Application) {
        if (!::application.isInitialized) application = app
    }

    fun isReady(): Boolean = ::application.isInitialized
}
