package com.fusionlancers.grafusion.data

import android.content.Context
import androidx.room.Room
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.db.AppDatabase
import com.fusionlancers.grafusion.data.prefs.AppLockPreferences
import com.fusionlancers.grafusion.data.prefs.NotificationPreferences
import com.fusionlancers.grafusion.data.prefs.ThemePreferences
import com.fusionlancers.grafusion.data.repo.AccountRepository
import com.fusionlancers.grafusion.data.repo.AdminRepository
import com.fusionlancers.grafusion.data.repo.AlertRepository
import com.fusionlancers.grafusion.data.repo.DashboardRepository
import com.fusionlancers.grafusion.data.repo.DatasourceRepository
import com.fusionlancers.grafusion.data.repo.ExploreRepository
import com.fusionlancers.grafusion.data.repo.LokiTailClient
import com.fusionlancers.grafusion.data.repo.NotificationHistoryRepository
import com.fusionlancers.grafusion.data.repo.NotificationsRepository
import com.fusionlancers.grafusion.data.repo.OnCallRepository
import com.fusionlancers.grafusion.data.repo.UserPreferencesRepository
import com.fusionlancers.grafusion.data.security.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual DI container. Constructed once from Application.onCreate.
 * Keeps the app dependency-free of Hilt/Dagger for now.
 */
class AppContainer(context: Context) {

    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "grafana-mobile.db",
    ).fallbackToDestructiveMigration().build()

    private val tokenVault = TokenVault(context.applicationContext)
    private val apiFactory = GrafanaApiFactory()

    val themePreferences = ThemePreferences(context.applicationContext)
    val notificationPreferences = NotificationPreferences(context.applicationContext)
    val appLockPreferences = AppLockPreferences(context.applicationContext)

    val accountRepository = AccountRepository(
        accountDao = db.accountDao(),
        tokenVault = tokenVault,
        apiFactory = apiFactory,
    )

    val dashboardRepository = DashboardRepository(
        dashboardDao = db.dashboardDao(),
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val alertRepository = AlertRepository(
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val notificationsRepository = NotificationsRepository(
        accountRepository = accountRepository,
        notificationPreferences = notificationPreferences,
        apiFactory = apiFactory,
    )

    val onCallRepository = OnCallRepository(
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val exploreRepository = ExploreRepository(
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val lokiTailClient = LokiTailClient(
        accountRepository = accountRepository,
        httpClient = apiFactory.client,
    )

    val notificationHistoryRepository = NotificationHistoryRepository(
        dao = db.notificationHistoryDao(),
    )

    val datasourceRepository = DatasourceRepository(
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val adminRepository = AdminRepository(
        accountRepository = accountRepository,
        apiFactory = apiFactory,
    )

    val userPreferencesRepository = UserPreferencesRepository(
        accountRepository = accountRepository,
        themePreferences = themePreferences,
        apiFactory = apiFactory,
    )

    /** Set by MainActivity when opened via a notification tap; consumed by AlertsScreen. */
    val pendingAlertDeepLink = MutableStateFlow<AlertDeepLink?>(null)

    /**
     * Session-scoped guard so we auto-open the home dashboard at most once per process launch.
     * Flipped by DashboardListScreen after it fires the first navigation.
     */
    val homeAutoOpenConsumed = MutableStateFlow(false)
}

/** [fingerprint] is preferred; [name] is a fallback when the relay omitted the fingerprint. */
data class AlertDeepLink(val fingerprint: String?, val name: String?)
