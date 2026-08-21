package com.fusionlancers.grafusion

import android.app.Application
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.push.NotificationChannels
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrafusionApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureCreated(this)
        // Register per-account TLS pins with the API factory so the first request after boot
        // already goes through a pinned client instead of accidentally trusting system CAs.
        appScope.launch { runCatching { container.accountRepository.syncPinsToFactory() } }
        if (BuildConfig.FIREBASE_AVAILABLE) {
            // FirebaseApp auto-inits via the google-services plugin's provider. Grab the
            // current token so first-run devices don't wait for the next rotation to register.
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    container.notificationPreferences.setFcmToken(token)
                    appScope.launch {
                        runCatching { container.notificationsRepository.registerCurrentDevice() }
                    }
                }
            }
        }
    }
}
