package com.fusionlancers.grafusion.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.fusionlancers.grafusion.data.AppContainer
import com.fusionlancers.grafusion.ui.theme.EnergyOrange

/**
 * Shows an unlock screen when app lock is enabled and blocks [content] until it succeeds.
 * Uses biometric prompt (if enabled + available on device) with PIN fallback.
 */
@Composable
fun AppLockGate(
    container: AppContainer,
    content: @Composable () -> Unit,
) {
    val cfg by container.appLockPreferences.flow.collectAsState(initial = container.appLockPreferences.current())
    var unlocked by remember { mutableStateOf(false) }

    // If lock has never been enabled OR no PIN set, don't block.
    val locked = cfg.lockEnabled && cfg.pinSet && !unlocked
    if (!locked) {
        content()
        return
    }
    UnlockScreen(
        container = container,
        biometricEnabled = cfg.biometricEnabled && biometricAvailable(LocalContext.current),
        onUnlocked = { unlocked = true },
    )
}

@Composable
private fun UnlockScreen(
    container: AppContainer,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(!biometricEnabled) }
    var biometricAttempted by remember { mutableStateOf(false) }

    // Auto-launch biometric prompt on first composition when enabled.
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricAttempted) {
            biometricAttempted = true
            launchBiometric(
                context = context,
                onSuccess = onUnlocked,
                onFallback = { showPin = true },
                onError = { msg -> error = msg; showPin = true },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = CircleShape,
                color = EnergyOrange.copy(alpha = 0.18f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = EnergyOrange,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Grafusion is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (showPin) "Enter your PIN to continue"
                else "Use your fingerprint or PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))

            if (showPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12); error = null },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (container.appLockPreferences.verifyPin(pin)) {
                            onUnlocked()
                        } else {
                            error = "Incorrect PIN"
                            pin = ""
                        }
                    },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange),
                ) {
                    Text("Unlock", fontWeight = FontWeight.SemiBold)
                }
                if (biometricEnabled) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        launchBiometric(
                            context = context,
                            onSuccess = onUnlocked,
                            onFallback = { /* stay on PIN */ },
                            onError = { msg -> error = msg },
                        )
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = EnergyOrange)
                            Spacer(Modifier.width(6.dp))
                            Text("Use fingerprint instead", color = EnergyOrange)
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = EnergyOrange, strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showPin = true }) {
                    Text("Use PIN instead", color = EnergyOrange, fontSize = 14.sp)
                }
            }
        }
    }
}

private fun biometricAvailable(context: Context): Boolean {
    val bm = BiometricManager.from(context)
    val level = BiometricManager.Authenticators.BIOMETRIC_WEAK
    return bm.canAuthenticate(level) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun launchBiometric(
    context: Context,
    onSuccess: () -> Unit,
    onFallback: () -> Unit,
    onError: (String) -> Unit,
) {
    val activity = context.findFragmentActivity() ?: run {
        onError("Biometric unavailable")
        return
    }
    val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> onFallback()
                    else -> onError(errString.toString())
                }
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Grafusion")
        .setSubtitle("Use your fingerprint to unlock")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
