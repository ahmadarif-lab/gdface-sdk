package com.ahmadarif.gdface.sample.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmadarif.gdface.sample.gdApp
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hands out one camera frame on request. The camera keeps delivering frames, but a frame
 * is only turned into a bitmap when [grab] asked for one, so the preview costs nothing.
 */
class FrameGrabber {

    private val pending = AtomicReference<CompletableDeferred<Bitmap>?>(null)

    val analyzer = ImageAnalysis.Analyzer { image ->
        val request = pending.getAndSet(null)
        try {
            request?.complete(image.toBitmap().rotated(image.imageInfo.rotationDegrees))
        } catch (e: Exception) {
            request?.completeExceptionally(e)
        } finally {
            image.close()
        }
    }

    /** The next frame, upright, or null if the camera gives none within three seconds. */
    suspend fun grab(): Bitmap? {
        val request = CompletableDeferred<Bitmap>()
        pending.set(request)
        return try {
            withTimeoutOrNull(3_000) { request.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not read a camera frame", e)
            null
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    private companion object {
        const val TAG = "FrameGrabber"
    }
}

/**
 * A live camera preview whose frames go to [analyzer], on the engine thread. The camera runs
 * only while the screen is resumed: it is released the moment the screen starts to be left,
 * not when its exit animation ends, so it does not keep working during that animation.
 * [torch] lights the flash of a back camera; [onTorchAvailable] says whether the camera in
 * use has one.
 */
@Composable
fun CameraView(
    frontCamera: Boolean,
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
    torch: Boolean = false,
    onTorchAvailable: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val service = context.gdApp.service
    val scope = rememberCoroutineScope()
    // A TextureView, so the preview fades and moves with the screen; the default SurfaceView
    // does not, which shows as a jump when a screen with a camera opens or closes.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LifecycleResumeEffect(frontCamera) {
        val starting = scope.launch {
            try {
                val provider = context.awaitCameraProvider()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(service.executor, analyzer) }
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                onTorchAvailable(camera?.cameraInfo?.hasFlashUnit() == true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CameraView", "Could not start the camera", e)
            }
        }
        onPauseOrDispose {
            starting.cancel()
            camera = null
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
    LaunchedEffect(camera, torch) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torch)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        try {
            cont.resume(future.get())
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }, ContextCompat.getMainExecutor(this))
}

/** Shows [content] once the camera permission is granted, and asks for it until then. */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        content()
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Gd.TextSecondary)
            Text(
                "The camera permission is needed to show the preview.",
                color = Gd.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            SecondaryButton("Allow camera", onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}

/** Decodes a photo picked from the gallery, upright and at most [maxSide] pixels on its long side. */
fun Context.loadBitmap(uri: Uri, maxSide: Int = 1600): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            // The SDK reads the pixels, which a hardware bitmap does not allow.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = maxSide.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (e: Exception) {
    Log.e("loadBitmap", "Could not open the photo", e)
    null
}
