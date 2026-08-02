package com.fusionlancers.grafusion.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.R
import com.fusionlancers.grafusion.ui.theme.EnergyOrange
import com.fusionlancers.grafusion.ui.theme.FusionBlue
import com.fusionlancers.grafusion.ui.theme.FusionNavy
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onPasswordLogin: suspend (url: String, user: String, pass: String) -> Result<Unit>,
    onTokenLogin: suspend (url: String, token: String) -> Result<Unit>,
) {
    var tab by remember { mutableStateOf(0) } // 0 = password, 1 = token
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val gradient = Brush.verticalGradient(listOf(FusionNavy, FusionBlue, Color(0xFF1E3A73)))

    Box(
        Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(128.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Grafusion",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Mobile Dashboards",
                color = Color(0xFFC7D2FE),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Sign in to your Grafana",
                        color = FusionNavy,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect Grafusion to your self-hosted Grafana instance.",
                        color = Color(0xFF4B5563),
                        fontSize = 13.sp,
                    )

                    Spacer(Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color(0xFFF1F5F9),
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0; error = null }) {
                            Text("Password", Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                        }
                        Tab(selected = tab == 1, onClick = { tab = 1; error = null }) {
                            Text("Service Token", Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FusionNavy,
                        unfocusedTextColor = FusionNavy,
                        disabledTextColor = FusionNavy.copy(alpha = 0.6f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = EnergyOrange,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = EnergyOrange,
                        unfocusedLabelColor = Color(0xFF6B7280),
                        focusedLeadingIconColor = EnergyOrange,
                        unfocusedLeadingIconColor = Color(0xFF6B7280),
                        focusedTrailingIconColor = Color(0xFF6B7280),
                        unfocusedTrailingIconColor = Color(0xFF6B7280),
                        cursorColor = EnergyOrange,
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text("Grafana URL") },
                        placeholder = { Text("https://grafana.example.com") },
                        leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Include http:// or https://. LAN addresses like http://192.168.x.x are fine.",
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                    )

                    Spacer(Modifier.height(12.dp))

                    if (tab == 0) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it; error = null },
                            label = { Text("Username or email") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it; error = null },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPass) "Hide password" else "Show password",
                                    )
                                }
                            },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    } else {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it; error = null },
                            label = { Text("Service account token") },
                            placeholder = { Text("glsa_...") },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPass = !showPass }) {
                                    Icon(
                                        if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPass) "Hide token" else "Show token",
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create at Grafana → Administration → Service accounts → Add service account.",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp,
                        )
                    }

                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    val canSubmit = url.isNotBlank() && when (tab) {
                        0 -> user.isNotBlank() && pass.isNotBlank()
                        else -> token.isNotBlank()
                    }

                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            error = null
                            scope.launch {
                                val result = if (tab == 0) onPasswordLogin(url, user, pass)
                                else onTokenLogin(url, token)
                                result.onFailure { error = describe(it) }
                                busy = false
                            }
                        },
                        enabled = canSubmit && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnergyOrange,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "by Fusionlancers Technologies Pvt. Ltd.",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
            )
        }
    }
}

private fun describe(t: Throwable): String {
    val raw = t.message ?: t::class.simpleName ?: "Unknown error"
    return when {
        raw.contains("401", ignoreCase = true) -> "Sign-in failed (401): username or password is wrong."
        raw.contains("403", ignoreCase = true) -> "Sign-in failed (403): server rejected the credentials. Check the URL, user role, and any SSO/2FA settings."
        raw.contains("404", ignoreCase = true) -> "URL not found (404): double-check the Grafana URL."
        raw.contains("UnknownHost", ignoreCase = true) -> "Can't reach that host. Check the URL and your network."
        raw.contains("timeout", ignoreCase = true) -> "Timed out reaching Grafana. Check the URL and your network."
        raw.contains("CertPath", ignoreCase = true) || raw.contains("SSL", ignoreCase = true) ->
            "TLS/SSL error. If you use a self-signed certificate, use http:// or install the cert on the device."
        else -> raw.take(220)
    }
}
