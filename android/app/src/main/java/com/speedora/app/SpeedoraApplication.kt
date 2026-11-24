package com.speedora.app

import android.app.Application
import com.speedora.app.di.AppContainer

class SpeedoraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
