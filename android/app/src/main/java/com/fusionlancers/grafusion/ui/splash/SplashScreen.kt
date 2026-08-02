package com.fusionlancers.grafusion.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlancers.grafusion.R
import com.fusionlancers.grafusion.ui.theme.FusionBlue
import com.fusionlancers.grafusion.ui.theme.FusionNavy

@Composable
fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splash-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 600),
        label = "splash-scale",
    )
    LaunchedEffect(Unit) { visible = true }

    val ctx = LocalContext.current
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    val gradient = Brush.verticalGradient(listOf(FusionNavy, FusionBlue, Color(0xFF1E3A73)))

    Box(
        Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = 48.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(148.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Grafusion",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Mobile Dashboards for Grafana",
                color = Color(0xFFC7D2FE),
                fontSize = 16.sp,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "by Fusionlancers",
                color = Color(0xFF8FA3D7),
                fontSize = 13.sp,
                modifier = Modifier.alpha(alpha),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(alpha),
                color = Color(0xFFC7D2FE),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (versionName.isNotBlank()) "v$versionName · Loading…" else "Loading…",
                color = Color(0xFF8FA3D7),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
