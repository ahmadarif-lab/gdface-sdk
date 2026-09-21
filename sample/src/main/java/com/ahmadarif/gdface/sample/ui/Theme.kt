package com.ahmadarif.gdface.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The app's palette: a dark navy surface with one blue accent. */
object Gd {
    val Background = Color(0xFF0B1020)
    val BackgroundEnd = Color(0xFF0F1836)
    val Surface = Color(0xFF141B2E)
    val SurfaceHigh = Color(0xFF1C2540)
    val Outline = Color(0xFF2B3557)
    val Primary = Color(0xFF4F6BF6)
    val TextPrimary = Color(0xFFF2F5FF)
    val TextSecondary = Color(0xFF9AA6C4)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
}

@Composable
fun GdFaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gd.Primary,
            onPrimary = Color.White,
            background = Gd.Background,
            onBackground = Gd.TextPrimary,
            surface = Gd.Surface,
            onSurface = Gd.TextPrimary,
            surfaceVariant = Gd.SurfaceHigh,
            onSurfaceVariant = Gd.TextSecondary,
            outline = Gd.Outline,
            error = Gd.Danger
        ),
        content = content
    )
}

/** True while a screen wants the whole window white: the front camera's torch. */
val LocalLightScreen = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** The gradient every screen sits on, or plain white while [LocalLightScreen] is on. */
@Composable
fun GdBackground(content: @Composable () -> Unit) {
    val paint = if (LocalLightScreen.current.value) {
        Modifier.background(Color.White)
    } else {
        Modifier.background(Brush.verticalGradient(listOf(Gd.Background, Gd.BackgroundEnd)))
    }
    Box(Modifier.fillMaxSize().then(paint)) { content() }
}
