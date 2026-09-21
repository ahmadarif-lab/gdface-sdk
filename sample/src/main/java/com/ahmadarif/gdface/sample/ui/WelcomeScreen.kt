package com.ahmadarif.gdface.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.gdApp

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val service = LocalContext.current.gdApp.service

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GdLogo(58.sp)
            Gap(10)
            Text(
                "On-Device Face Recognition\nFast. Private. Secure.",
                color = Gd.TextSecondary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            Gap(36)
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                FaceGuideOverlay(oval = false, color = Gd.Primary)
                Icon(Icons.Default.Face, contentDescription = null, tint = Gd.Primary.copy(alpha = 0.85f), modifier = Modifier.size(130.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Feature(Icons.Default.CloudOff, "100% Local\nNo Cloud")
            Feature(Icons.Default.Verified, "High\nAccuracy")
            Feature(Icons.Default.Shield, "Privacy\nFirst")
        }
        Gap(24)
        if (service.status !is EngineStatus.Ready) {
            EngineStatusCard(service.status, onRetry = { service.start() })
            Gap(14)
        }
        PrimaryButton("Get Started", onStart, icon = Icons.AutoMirrored.Filled.ArrowForward)
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Gd.TextSecondary, modifier = Modifier.size(24.dp))
        Gap(6)
        Text(label, color = Gd.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}
