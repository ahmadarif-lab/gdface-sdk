package com.ahmadarif.gdface.sample.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.R
import com.ahmadarif.gdface.sample.gdApp
import com.ahmadarif.gdface.sdk.GdFaceModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val app = LocalContext.current.gdApp
    val settings = app.settings
    val service = app.service
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                working = true
                message = try {
                    app.faces.export(uri)
                    "Exported ${app.faces.persons.size} ${if (app.faces.persons.size == 1) "person" else "people"} with their photos."
                } catch (e: Exception) {
                    "The export failed: ${e.message}"
                }
                working = false
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                working = true
                message = try {
                    val summary = app.faces.import(uri)
                    "Imported ${summary.people} ${if (summary.people == 1) "person" else "people"} (${summary.images} photos)." +
                        if (summary.skipped > 0) " ${summary.skipped} photos were skipped: already here, or no face found." else ""
                } catch (e: Exception) {
                    "The import failed: ${e.message}"
                }
                working = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("Settings", onBack = { nav.popBackStack() })
        Gap(8)
        Text("Recognition Options", color = Gd.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
        Gap(8)
        GdCard(Modifier.fillMaxWidth()) {
            SettingSwitch(
                "Liveness Detection",
                "Detects if the face is from a real person (not a photo or video).",
                settings.livenessEnabled,
                enabled = true
            ) { settings.livenessEnabled = it }
            SettingSlider(
                "Liveness Threshold",
                settings.livenessThreshold,
                enabled = settings.livenessEnabled,
                onChange = { settings.livenessThreshold = it },
                onFinished = {
                    val value = settings.livenessThreshold.coerceIn(0f, 1f)
                    scope.launch { service.call { it.livenessThreshold = value } }
                }
            )
            Text(
                "Higher rejects more spoofs, and more real faces too.",
                color = Gd.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            HorizontalDivider(color = Gd.Outline)
            SettingSwitch(
                "Mask Detection",
                if (service.maskAvailable) "Detects if the person is wearing a mask."
                else if (service.status is EngineStatus.Ready) "The mask model could not be downloaded."
                else "Available once the face engine is ready.",
                settings.maskEnabled && service.maskAvailable,
                enabled = service.maskAvailable
            ) { settings.maskEnabled = it }
            SettingSlider(
                "Mask Threshold",
                settings.maskThreshold,
                enabled = settings.maskEnabled && service.maskAvailable,
                onChange = { settings.maskThreshold = it },
                onFinished = {}
            )
            Gap(4)
        }

        Gap(20)
        Text("Application", color = Gd.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
        Gap(8)
        GdCard(Modifier.fillMaxWidth()) {
            ActionRow(Icons.Default.Delete, Gd.Danger, "Clear All Faces") { confirmClear = true }
            HorizontalDivider(color = Gd.Outline)
            ActionRow(Icons.Default.FileUpload, Gd.Primary, "Export Database") {
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                exporter.launch("gdface-faces-$stamp.zip")
            }
            HorizontalDivider(color = Gd.Outline)
            ActionRow(Icons.Default.FileDownload, Gd.Primary, "Import Database") {
                if (service.status is EngineStatus.Ready) importer.launch(arrayOf("application/zip", "application/octet-stream"))
                else message = "The face engine is still starting."
            }
            HorizontalDivider(color = Gd.Outline)
            ActionRow(Icons.Default.Info, Gd.Primary, "About") { nav.navigate("about") }
        }
        Gap(8)
        Text(
            "An export is a ZIP file with the names and the photos of every enrolled face. Keep it private.",
            color = Gd.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Gap(24)
    }

    if (working) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Gd.Surface,
            confirmButton = {},
            text = { Text("Working…", color = Gd.TextPrimary) }
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Gd.Surface,
            text = { Text(it, color = Gd.TextPrimary) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Gd.Surface,
            title = { Text("Clear all faces?", color = Gd.TextPrimary) },
            text = {
                Text(
                    "Every enrolled face and its photos are deleted from this device. It cannot be undone.",
                    color = Gd.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { app.faces.clearAll() }
                }) { Text("Clear all", color = Gd.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Gd.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Gap(2)
            Text(description, color = Gd.TextSecondary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Gd.Primary,
                uncheckedThumbColor = Gd.TextSecondary,
                uncheckedTrackColor = Gd.SurfaceHigh,
                uncheckedBorderColor = Gd.Outline
            )
        )
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (enabled) Gd.TextPrimary else Gd.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%.2f".format(value),
                color = if (enabled) Gd.TextPrimary else Gd.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gd.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        // 0.10 to 1.00 in steps of 0.05, which includes both defaults (0.80 and 0.60).
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = 0.1f..1f,
            steps = 17,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Gd.Primary,
                activeTrackColor = Gd.Primary,
                inactiveTrackColor = Gd.SurfaceHigh,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text("0.1", color = Gd.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("1.0", color = Gd.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = Gd.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gd.TextSecondary)
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("About", onBack = { nav.popBackStack() })
        Gap(10)
        GdCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painterResource(R.drawable.gdface_icon),
                    contentDescription = "GDFace",
                    modifier = Modifier.size(112.dp)
                )
                Gap(16)
                Text("GDFace SDK", color = Gd.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Version ${GdFaceModelProvider.SDK_VERSION}", color = Gd.TextSecondary, fontSize = 14.sp)
                Gap(12)
                Text(
                    "On-Device Face Recognition\nFast. Private. Secure.",
                    color = Gd.TextPrimary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Gap(14)
        GdCard(Modifier.fillMaxWidth()) {
            AboutRow(Icons.Default.CloudOff, "100% Local Processing", "Your data stays on your device.")
            HorizontalDivider(color = Gd.Outline)
            AboutRow(Icons.Default.Settings, "High Accuracy", "Powered by the SeetaFace6 models.")
            HorizontalDivider(color = Gd.Outline)
            AboutRow(Icons.Default.Shield, "Liveness & Mask Detection", "Built-in anti-spoofing features.")
            HorizontalDivider(color = Gd.Outline)
            AboutRow(Icons.Default.Extension, "Easy Integration", "Use the GdFace SDK in your own apps.")
        }
        Gap(20)
        Text(
            "GdFace SDK · Apache License 2.0\n© 2026 The GdFace SDK contributors",
            color = Gd.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Gap(24)
    }
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Gd.Primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Gd.Primary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Gd.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Gd.TextSecondary, fontSize = 13.sp)
        }
        Spacer4()
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
