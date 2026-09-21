package com.ahmadarif.gdface.sample.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ahmadarif.gdface.sample.EngineStatus
import com.ahmadarif.gdface.sample.EnrollResult
import com.ahmadarif.gdface.sample.gdApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Enrolls a face from the camera or the gallery. With [personId], the photo is added to that person. */
@Composable
fun EnrollScreen(nav: NavController, personId: String) {
    val context = LocalContext.current
    val app = context.gdApp
    val scope = rememberCoroutineScope()
    val existing = app.faces.persons.firstOrNull { it.id == personId }

    var tab by remember { mutableIntStateOf(0) }
    var front by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf<Bitmap?>(null) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val grabber = remember { FrameGrabber() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                picked = withContext(Dispatchers.IO) { context.loadBitmap(uri) }
                error = if (picked == null) "That photo could not be opened" else null
            }
        }
    }

    val ready = app.service.status is EngineStatus.Ready
    val hasPhoto = if (tab == 0) true else picked != null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("Enroll Face", onBack = { nav.popBackStack() })
        Gap(6)
        SegmentedTabs(listOf("Camera", "Gallery"), tab, { tab = it; error = null })
        Gap(14)

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            if (tab == 0) {
                val shot = captured
                if (shot != null) {
                    // A front camera preview is mirrored, so the still is mirrored too.
                    Image(
                        shot.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = if (front) -1f else 1f }
                    )
                } else {
                    CameraPermissionGate { CameraView(front, grabber.analyzer, Modifier.fillMaxSize()) }
                }
                FaceGuideOverlay(oval = true)
                HintPill(
                    if (shot != null) "Photo taken. Save it or retake." else "Position your face within the frame",
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            } else {
                val photo = picked
                if (photo != null) {
                    Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Gd.TextSecondary, modifier = Modifier.size(44.dp))
                        Gap(8)
                        Text("Choose a photo with one clear face", color = Gd.TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (tab == 0) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                CaptureButton(
                    retake = captured != null,
                    onClick = {
                        if (captured != null) {
                            captured = null
                        } else {
                            scope.launch {
                                captured = grabber.grab()
                                if (captured == null) error = "The camera gave no picture, try again"
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { front = !front; captured = null },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) { Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Gd.TextSecondary) }
            }
        } else {
            Gap(14)
            SecondaryButton(
                if (picked == null) "Choose photo" else "Choose another photo",
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            Gap(14)
        }

        Text("Name", color = Gd.TextSecondary, fontSize = 14.sp)
        Gap(6)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            singleLine = true,
            enabled = existing == null,
            placeholder = { Text("Who is this?") },
            trailingIcon = {
                if (name.isNotEmpty() && existing == null) {
                    IconButton(onClick = { name = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Gd.TextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = gdTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (existing != null) {
            Gap(6)
            Text(
                "Adding another photo of ${existing.name}. More photos help recognition.",
                color = Gd.TextSecondary,
                fontSize = 12.sp
            )
        }
        Gap(14)

        PrimaryButton(
            text = "Save Face",
            enabled = ready && name.isNotBlank() && hasPhoto,
            loading = busy,
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    val photo = if (tab == 0) captured ?: grabber.grab() else picked
                    if (photo == null) {
                        error = "There is no photo to save"
                    } else {
                        when (val result = app.faces.enroll(photo, name, personId.ifEmpty { null })) {
                            is EnrollResult.Saved -> nav.navigate("enrolled/${result.person.id}") {
                                popUpTo("enroll?personId={personId}") { inclusive = true }
                            }
                            EnrollResult.NoFace -> error = "No face was found. Face the camera in good light and try again."
                            EnrollResult.Failed -> error = "The face could not be registered, try another photo."
                        }
                    }
                    busy = false
                }
            }
        )
        if (!ready) {
            Gap(8)
            Text("The face engine is still starting.", color = Gd.TextSecondary, fontSize = 12.sp)
        }
        error?.let {
            Gap(8)
            Text(it, color = Gd.Danger, fontSize = 13.sp)
        }
        Gap(24)
    }
}

@Composable
fun EnrolledScreen(nav: NavController, personId: String) {
    val faces = LocalContext.current.gdApp.faces
    val person = faces.persons.firstOrNull { it.id == personId }
    val photos = person?.images?.size ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenTitle("", onBack = { nav.navigate("home") { popUpTo("home") } })
        Gap(20)
        Box(Modifier.size(210.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(6.dp, Gd.Success, CircleShape)
                    .padding(10.dp)
            ) {
                FaceImageView(
                    person?.images?.lastOrNull()?.let { faces.photoFile(it) },
                    Modifier.fillMaxSize(),
                    maxSizePx = 512
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Gd.Success)
                    .border(4.dp, Gd.Background, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White) }
        }
        Gap(26)
        Text("Face Enrolled Successfully!", color = Gd.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Gap(10)
        Text(
            when {
                person == null -> "The face has been added to your face database."
                photos > 1 -> "A photo was added to “${person.name}”, who now has $photos photos."
                else -> "“${person.name}” has been added to your face database."
            },
            color = Gd.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Gap(32)
        PrimaryButton("Enroll Another Face", { nav.navigate("enroll") { popUpTo("home") } })
        Gap(12)
        SecondaryButton("View Face List", { nav.navigate("faces") { popUpTo("home") } })
        Gap(12)
        SecondaryButton("Back to Home", { nav.navigate("home") { popUpTo("home") } })
        Gap(24)
    }
}
