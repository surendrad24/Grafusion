package com.fusionlancers.grafusion.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.ui.auth.LoginScreen
import com.fusionlancers.grafusion.ui.nav.AdaptiveScaffold

@Composable
fun AppRoot(
    container: AppContainer,
    windowSizeClass: WindowSizeClass,
) {
    val activeAccount by container.accountRepository.activeAccount.collectAsState(initial = null)
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (activeAccount == null) {
            LoginScreen(
                onPasswordLogin = { url, user, pass ->
                    container.accountRepository.loginWithPassword(url, user, pass)
                },
                onTokenLogin = { url, token ->
                    container.accountRepository.loginWithToken(url, token)
                },
            )
        } else {
            AdaptiveScaffold(
                navController = navController,
                windowSizeClass = windowSizeClass,
                container = container,
                useNavRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
            )
        }
    }
}
