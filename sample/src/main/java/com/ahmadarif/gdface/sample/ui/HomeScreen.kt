package com.ahmadarif.gdface.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.gdApp

@Composable
fun HomeScreen(nav: NavController) {
    val app = LocalContext.current.gdApp
    val service = app.service

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                GdLogo(34.sp)
                Text("Your face recognition on device", color = Gd.TextSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = { nav.navigate("settings") }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Gd.TextPrimary)
            }
        }
        Gap(14)

        if (service.status !is EngineStatus.Ready) {
            EngineStatusCard(service.status, onRetry = { service.start() })
            Gap(14)
        }

        // The two tiles of a row are as tall as the taller one, so no text is ever cut.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuTile(Icons.Default.PersonAdd, "Enroll Face", "Add new face to database", { nav.navigate("enroll") }, Modifier.weight(1f))
            MenuTile(Icons.Default.Image, "Recognize Image", "Find a match from photo or gallery", { nav.navigate("recognize") }, Modifier.weight(1f))
        }
        Gap(12)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuTile(Icons.Default.Videocam, "Real-time Detection", "Detect and recognize a face live from the camera", { nav.navigate("realtime") }, Modifier.weight(1f))
            MenuTile(Icons.Default.Groups, "Face Database", "Manage enrolled faces", { nav.navigate("faces") }, Modifier.weight(1f))
        }
        Gap(14)

        GdCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatItem(app.faces.persons.size.toString(), "Enrolled Faces", Modifier.weight(1f))
                VerticalRule()
                StatItem(app.settings.recognitionsToday.toString(), "Recognitions Today", Modifier.weight(1f))
                VerticalRule()
                StatItem("100%", "Local Processing", Modifier.weight(1f))
            }
        }
        Gap(14)

        GdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Quick Tips", color = Gd.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Tip(Icons.Default.WbSunny, Gd.Success, "Use good lighting")
                Tip(Icons.Default.Visibility, Gd.TextSecondary, "Look at the camera")
                Tip(Icons.Default.Warning, Gd.Danger, "Remove mask or glasses for better results")
            }
        }
        Gap(20)
    }
}

/** Says what the SDK is doing while it is not ready: the first launch downloads ~170 MB. */
@Composable
fun EngineStatusCard(status: EngineStatus, onRetry: () -> Unit) {
    GdCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (status) {
                EngineStatus.Starting -> {
                    Text("Starting the face engine…", color = Gd.TextPrimary, fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gd.Primary, trackColor = Gd.SurfaceHigh)
                }
                is EngineStatus.Downloading -> {
                    Text(
                        "Downloading models ${status.index + 1}/${status.count} · ${status.percent}%",
                        color = Gd.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        progress = { status.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Gd.Primary,
                        trackColor = Gd.SurfaceHigh
                    )
                    Text(
                        "The first launch on a device downloads the models (~170 MB). After that the app works offline.",
                        color = Gd.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                is EngineStatus.Failed -> {
                    Text("The face engine could not start", color = Gd.Danger, fontWeight = FontWeight.SemiBold)
                    Text(status.message, color = Gd.TextSecondary, fontSize = 13.sp)
                    SecondaryButton("Retry", onRetry)
                }
                EngineStatus.Ready -> Unit
            }
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF15225E))
            .border(1.dp, Color(0xFF2A3D99), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        Gap(12)
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Gap(4)
        Text(subtitle, color = Gd.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Gd.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Gd.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VerticalRule() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Gd.Outline)
    ) { Spacer(Modifier.size(1.dp, 40.dp)) }
}

@Composable
private fun Tip(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Gd.TextPrimary, fontSize = 14.sp)
    }
}
