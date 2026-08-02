package com.fusionlancers.grafusion

import android.app.Application
import com.fusionlancers.grafusion.data.AppContainer

class GrafusionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
