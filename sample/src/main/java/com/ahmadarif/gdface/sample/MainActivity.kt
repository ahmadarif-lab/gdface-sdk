package com.ahmadarif.gdface.sample

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.ahmadarif.gdface.sample.ui.AppRoot
import com.ahmadarif.gdface.sample.ui.GdBackground
import com.ahmadarif.gdface.sample.ui.GdFaceTheme
import com.ahmadarif.gdface.sample.ui.LocalLightScreen

/**
 * A complete app built on the GdFace SDK: enroll faces from the camera or the gallery,
 * recognize them (with liveness and mask detection), follow a face live on the camera,
 * manage the enrolled faces and move them between devices. The SDK is used through [FaceService] and [FaceRepository]; the
 * screens are in the `ui` package.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val lightScreen = remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalLightScreen provides lightScreen) {
                GdFaceTheme {
                    GdBackground { AppRoot() }
                }
            }
        }
    }
}
