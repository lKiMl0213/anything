package rpg.android

import android.app.Application
import rpg.android.feedback.CrashLogStore

class AnythingAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
        CrashLogStore.writeEvent(this, "Aplicacao iniciada.")
    }
}

