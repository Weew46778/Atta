package com.arena.mineva

import android.app.Application
import com.arena.mineva.system.CrashReporter

class MineAvaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        AppPrefs.init(this)
    }
}
