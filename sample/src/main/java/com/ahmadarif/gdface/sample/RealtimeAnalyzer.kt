package com.ahmadarif.gdface.sample

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahmadarif.gdface.sample.ui.rotated
import com.ahmadarif.gdface.sdk.GdFaceEngine

/** What the live view says about the face in front of the camera. */
sealed interface LiveLabel {
    /** A face was found and is being looked up. */
    data object Checking : LiveLabel

    /** [person] is null for a face this app did not enroll. */
    data class Known(val name: String, val score: Float, val person: Person?) : LiveLabel

    data object Unknown : LiveLabel
    data object NotLive : LiveLabel
}

/** The largest face of one frame; [rect] is in the pixels of that frame's bitmap. */
data class LiveFace(val rect: Rect, val label: LiveLabel)

class LiveFrame(val bitmap: Bitmap, val face: LiveFace?) {
    val width get() = bitmap.width
    val height get() = bitmap.height
}

/**
 * The real-time view: follows the largest face in the camera frames, and every so often
 * asks the SDK who it is. It runs on the engine thread (the camera hands its frames to
 * that thread), so it may call the engine directly.
 *
 * Finding the face is cheap and done on every frame it can; the identity ([GdFaceEngine.recognize])
 * is heavy, so it is refreshed only every [RECOGNIZE_INTERVAL_MS], or at once for a face that
 * has no label yet. Between those, the label stays on the face as long as the box is still
 * roughly where it was.
 */
class RealtimeAnalyzer(
    private val service: FaceService,
    private val faces: FaceRepository,
    private val settings: AppSettings
) : ImageAnalysis.Analyzer {

    /** The latest frame with its face; null until the first frame. Read by the screen. */
    var frame: LiveFrame? by mutableStateOf(null)
        private set

    /** While true, frames are dropped and [frame] stays as it was. */
    @Volatile var paused = false

    private var lastRect: Rect? = null
    private var label: LiveLabel? = null
    private var lastRecognizeAt = 0L
    private var countedKey: String? = null
    private var framesSinceLog = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (paused || service.status != EngineStatus.Ready) return
            val bitmap = image.toBitmap().rotated(image.imageInfo.rotationDegrees)
            val engine = service.engine
            val now = SystemClock.elapsedRealtime()

            val rect: Rect?
            if (label == null || now - lastRecognizeAt >= RECOGNIZE_INTERVAL_MS) {
                lastRecognizeAt = now
                val result = engine.recognize(bitmap, requireLiveness = settings.livenessEnabled)
                rect = rectOf(result)
                label = rect?.let { labelOf(result) }
                Log.d(TAG, "recognize took ${SystemClock.elapsedRealtime() - now} ms -> $label")
            } else {
                rect = engine.detectFaceRect(bitmap)
                val previous = lastRect
                // A box that jumped is another face: forget the label so it is looked up at once.
                if (rect != null && previous != null && overlap(rect, previous) < SAME_FACE_OVERLAP) label = null
                if (rect == null) label = null
                if (++framesSinceLog >= 60) {
                    framesSinceLog = 0
                    Log.d(TAG, "detect took ${SystemClock.elapsedRealtime() - now} ms")
                }
            }
            lastRect = rect
            if (rect == null) countedKey = null

            val current = label
            if (current is LiveLabel.Known) countOnce(current)
            frame = LiveFrame(bitmap, if (rect != null) LiveFace(rect, current ?: LiveLabel.Checking) else null)
        } catch (e: Exception) {
            Log.e(TAG, "Could not analyze a frame", e)
        } finally {
            image.close()
        }
    }

    /** Counts a recognition once per face that stays in front of the camera, not once per lookup. */
    private fun countOnce(known: LiveLabel.Known) {
        val key = known.person?.id ?: known.name
        if (countedKey == key) return
        countedKey = key
        settings.recordRecognition()
    }

    private fun rectOf(result: GdFaceEngine.RecognizeResult): Rect? = when (result) {
        is GdFaceEngine.RecognizeResult.Matched -> result.rect
        is GdFaceEngine.RecognizeResult.NotRecognized -> result.rect
        is GdFaceEngine.RecognizeResult.LivenessFailed -> result.rect
        GdFaceEngine.RecognizeResult.NoFace -> null
    }

    private fun labelOf(result: GdFaceEngine.RecognizeResult): LiveLabel = when (result) {
        is GdFaceEngine.RecognizeResult.Matched -> {
            val person = faces.personForEngineId(result.faceId)
            LiveLabel.Known(person?.name ?: result.faceId, result.score, person)
        }
        is GdFaceEngine.RecognizeResult.NotRecognized -> LiveLabel.Unknown
        is GdFaceEngine.RecognizeResult.LivenessFailed -> LiveLabel.NotLive
        GdFaceEngine.RecognizeResult.NoFace -> LiveLabel.Checking
    }

    /** Intersection over union of two boxes, from 0 (apart) to 1 (the same). */
    private fun overlap(a: Rect, b: Rect): Float {
        val intersection = Rect()
        if (!intersection.setIntersect(a, b)) return 0f
        val inter = intersection.width().toLong() * intersection.height()
        val union = a.width().toLong() * a.height() + b.width().toLong() * b.height() - inter
        return if (union > 0) inter.toFloat() / union else 0f
    }

    private companion object {
        const val TAG = "RealtimeAnalyzer"
        const val RECOGNIZE_INTERVAL_MS = 700L
        const val SAME_FACE_OVERLAP = 0.3f
    }
}
