package com.ahmadarif.gdface.sample.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.Person
import com.ahmadarif.gdface.sample.cropFace
import com.ahmadarif.gdface.sample.gdApp
import com.ahmadarif.gdface.sdk.GdFaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Outcome(
    val result: GdFaceEngine.RecognizeResult,
    val mask: GdFaceEngine.MaskResult?,
    val face: Bitmap?,
    val person: Person?
)

@Composable
fun RecognizeScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.gdApp
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var front by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val grabber = remember { FrameGrabber() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                picked = withContext(Dispatchers.IO) { context.loadBitmap(uri) }
                outcome = null
                error = if (picked == null) "That photo could not be opened" else null
            }
        }
    }

    val ready = app.service.status is EngineStatus.Ready

    fun recognize() {
        scope.launch {
            busy = true
            error = null
            val bitmap = if (tab == 0) grabber.grab() else picked
            if (bitmap == null) {
                error = "There is no picture to check"
                busy = false
                return@launch
            }
            val settings = app.settings
            val useMask = settings.maskEnabled && app.service.maskAvailable
            val (result, mask) = app.service.call { engine ->
                val recognized = engine.recognize(bitmap, requireLiveness = settings.livenessEnabled)
                recognized to if (useMask) engine.detectMask(bitmap) else null
            }
            val rect = when (result) {
                is GdFaceEngine.RecognizeResult.Matched -> result.rect
                is GdFaceEngine.RecognizeResult.NotRecognized -> result.rect
                is GdFaceEngine.RecognizeResult.LivenessFailed -> result.rect
                GdFaceEngine.RecognizeResult.NoFace -> null
            }
            val person = (result as? GdFaceEngine.RecognizeResult.Matched)
                ?.let { app.faces.personForEngineId(it.faceId) }
            if (result is GdFaceEngine.RecognizeResult.Matched) settings.recordRecognition()
            outcome = Outcome(result, mask, rect?.let { bitmap.cropFace(it, 256) }, person)
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("Recognize Image", onBack = { nav.popBackStack() })
        Gap(6)
        SegmentedTabs(listOf("Camera", "Gallery"), tab, { tab = it; outcome = null; error = null })
        Gap(14)

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            if (tab == 0) {
                CameraPermissionGate { CameraView(front, grabber.analyzer, Modifier.fillMaxSize()) }
                FaceGuideOverlay(oval = false)
                HintPill("Look at the camera", Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                IconButton(onClick = { front = !front }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }
            } else {
                val photo = picked
                if (photo != null) {
                    Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    FaceGuideOverlay(oval = false)
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Gd.TextSecondary, modifier = Modifier.size(44.dp))
                        Gap(8)
                        Text("Choose a photo to check", color = Gd.TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Gap(16)

        if (tab == 1) {
            SecondaryButton(
                if (picked == null) "Choose photo" else "Choose another photo",
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            Gap(12)
        }
        PrimaryButton(
            text = "Recognize",
            enabled = ready && (tab == 0 || picked != null),
            loading = busy,
            onClick = { recognize() }
        )
        if (!ready) {
            Gap(8)
            Text("The face engine is still starting.", color = Gd.TextSecondary, fontSize = 12.sp)
        }
        error?.let {
            Gap(8)
            Text(it, color = Gd.Danger, fontSize = 13.sp)
        }
        Gap(18)

        outcome?.let { ResultCard(it, app.settings.maskThreshold) }
        Gap(24)
    }
}

@Composable
private fun ResultCard(outcome: Outcome, maskThreshold: Float) {
    val result = outcome.result
    val title: String
    val subtitle: String
    val badge: Pair<String, Color>?
    when (result) {
        is GdFaceEngine.RecognizeResult.Matched -> {
            title = outcome.person?.name ?: result.faceId
            subtitle = "Similarity"
            badge = "Match" to Gd.Success
        }
        is GdFaceEngine.RecognizeResult.NotRecognized -> {
            title = "Not recognized"
            subtitle = "A live face, but nobody enrolled matches it."
            badge = "No match" to Gd.Warning
        }
        is GdFaceEngine.RecognizeResult.LivenessFailed -> {
            title = "Liveness check failed"
            subtitle = "This could be a photo or a screen."
            badge = "Rejected" to Gd.Danger
        }
        GdFaceEngine.RecognizeResult.NoFace -> {
            title = "No face found"
            subtitle = "Look at the camera and try again."
            badge = null
        }
    }

    Text("Result", color = Gd.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Gap(10)
    GdCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BitmapView(outcome.face, Modifier.size(64.dp), shape = RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = Gd.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Gd.TextSecondary, fontSize = 13.sp)
            }
            if (badge != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Badge(badge.first, badge.second)
                    if (result is GdFaceEngine.RecognizeResult.Matched) {
                        Gap(4)
                        Text(
                            "%.1f%%".format(result.score * 100),
                            color = Gd.Success,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        val mask = outcome.mask
        if (mask is GdFaceEngine.MaskResult.Detected) {
            HorizontalDivider(color = Gd.Outline)
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mask", color = Gd.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val worn = mask.score >= maskThreshold
                Badge(if (worn) "Wearing a mask" else "No mask", if (worn) Gd.Primary else Gd.SurfaceHigh)
                Text(
                    "  score ${(mask.score * 100).toInt()}%",
                    color = Gd.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}
