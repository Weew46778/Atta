package com.arena.mineva

import android.app.Application

class MineAvaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
    }
}
