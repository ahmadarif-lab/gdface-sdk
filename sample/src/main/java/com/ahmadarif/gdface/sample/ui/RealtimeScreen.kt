package com.ahmadarif.gdface.sample.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.LiveLabel
import com.ahmadarif.gdface.sample.RealtimeAnalyzer
import com.ahmadarif.gdface.sample.gdApp
import kotlin.math.roundToInt

/** The camera with a box on the largest face and who it is, refreshed live. */
@Composable
fun RealtimeScreen(nav: NavController) {
    val app = LocalContext.current.gdApp
    val analyzer = remember { RealtimeAnalyzer(app.service, app.faces, app.settings) }
    var front by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(paused) { analyzer.paused = paused }
    DisposableEffect(Unit) { onDispose { analyzer.paused = true } }

    // A front camera has no flash: the torch is the screen itself, white at full brightness.
    val screenLight = torch && front
    ScreenLight(screenLight)
    val lightScreen = LocalLightScreen.current
    DisposableEffect(screenLight) {
        lightScreen.value = screenLight
        onDispose { lightScreen.value = false }
    }
    val ink = if (screenLight) Color(0xFF111827) else Gd.TextPrimary
    val inkSoft = if (screenLight) Color(0xFF4B5563) else Gd.TextSecondary

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("Real-time Detection", onBack = { nav.popBackStack() }, contentColor = ink)
        Gap(6)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            CameraPermissionGate {
                CameraView(
                    frontCamera = front,
                    analyzer = analyzer,
                    modifier = Modifier.fillMaxSize(),
                    torch = torch,
                    onTorchAvailable = { hasTorch = it }
                )
            }
            if (paused) PausedFrame(analyzer, front)
            LiveOverlay(analyzer, front)
            DetectionPill(analyzer, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Gap(18)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SideControl(
                icon = if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                label = "Torch",
                enabled = front || hasTorch,
                ink = ink,
                inkSoft = inkSoft
            ) { torch = !torch }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(3.dp, Gd.Primary, CircleShape)
                        .clickable { paused = !paused }
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Gd.Primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        tint = if (screenLight) Gd.Primary else Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Gap(6)
                Text(if (paused) "Resume" else "Pause", color = inkSoft, fontSize = 12.sp)
            }

            SideControl(icon = Icons.Default.Cameraswitch, label = "Switch", enabled = true, ink = ink, inkSoft = inkSoft) {
                front = !front
                torch = false
                hasTorch = false
            }
        }
        Gap(20)
    }
}

@Composable
private fun SideControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    ink: Color,
    inkSoft: Color,
    onClick: () -> Unit
) {
    val tint = if (enabled) ink else inkSoft.copy(alpha = 0.4f)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        Gap(4)
        Text(label, color = tint, fontSize = 12.sp)
    }
}

/** The frame the analysis stopped on, shown still over the preview while paused. */
@Composable
private fun PausedFrame(analyzer: RealtimeAnalyzer, front: Boolean) {
    val frame = analyzer.frame ?: return
    Image(
        frame.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = if (front) -1f else 1f }
    )
}

/** The box on the face, and its label, placed over the preview the way the preview shows the frame. */
@Composable
private fun LiveOverlay(analyzer: RealtimeAnalyzer, front: Boolean) {
    val frame = analyzer.frame ?: return
    val face = frame.face ?: return
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewWidth = with(density) { maxWidth.toPx() }
        val viewHeight = with(density) { maxHeight.toPx() }
        // The preview fills the view and crops what does not fit; a front camera is mirrored.
        val scale = maxOf(viewWidth / frame.width, viewHeight / frame.height)
        val offsetX = (viewWidth - frame.width * scale) / 2f
        val offsetY = (viewHeight - frame.height * scale) / 2f
        fun x(value: Int) = offsetX + (if (front) frame.width - value else value) * scale

        val left = minOf(x(face.rect.left), x(face.rect.right))
        val right = maxOf(x(face.rect.left), x(face.rect.right))
        val top = offsetY + face.rect.top * scale
        val bottom = offsetY + face.rect.bottom * scale

        val color = when (face.label) {
            is LiveLabel.Known -> Gd.Success
            LiveLabel.Unknown -> Gd.Warning
            LiveLabel.NotLive -> Gd.Danger
            LiveLabel.Checking -> Color.White
        }
        val text = when (val label = face.label) {
            is LiveLabel.Known -> "${label.name}  ${"%.1f".format(label.score * 100)}%"
            LiveLabel.Unknown -> "Unknown"
            LiveLabel.NotLive -> "Not live"
            LiveLabel.Checking -> "Checking…"
        }

        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
                .border(3.dp, color, RoundedCornerShape(10.dp))
        ) {
            Text(
                text,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(color, RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun DetectionPill(analyzer: RealtimeAnalyzer, modifier: Modifier = Modifier) {
    val status = LocalContext.current.gdApp.service.status
    val found = analyzer.frame?.face != null
    val text = when {
        status != EngineStatus.Ready -> "Starting…"
        found -> "1 face detected"
        else -> "No face"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (found) Gd.Success else Gd.TextSecondary)
        )
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * While [on], the window is at full brightness and the status and navigation bar icons are
 * dark, to be seen on the white screen; with that screen, this is the front camera's torch.
 * Everything is put back afterwards, also when the screen is left.
 */
@Composable
private fun ScreenLight(on: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    val view = LocalView.current
    DisposableEffect(on, window) {
        if (on && window != null) {
            val bars = WindowCompat.getInsetsController(window, view)
            val brightness = window.attributes.screenBrightness
            val lightStatus = bars.isAppearanceLightStatusBars
            val lightNavigation = bars.isAppearanceLightNavigationBars
            window.attributes = window.attributes.apply { screenBrightness = 1f }
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = true
            onDispose {
                window.attributes = window.attributes.apply { screenBrightness = brightness }
                bars.isAppearanceLightStatusBars = lightStatus
                bars.isAppearanceLightNavigationBars = lightNavigation
            }
        } else {
            onDispose { }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
