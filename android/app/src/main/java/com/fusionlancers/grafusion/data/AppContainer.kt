package com.fusionlancers.grafusion.data

import android.content.Context
import androidx.room.Room
import com.fusionlancers.grafusion.data.api.GrafanaApiFactory
import com.fusionlancers.grafusion.data.db.AppDatabase
import com.fusionlancers.grafusion.data.prefs.ThemePreferences
import com.fusionlancers.grafusion.data.repo.AccountRepository
import com.fusionlancers.grafusion.data.repo.AlertRepository
import com.fusionlancers.grafusion.data.repo.DashboardRepository
import com.fusionlancers.grafusion.data.security.TokenVault

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
}
