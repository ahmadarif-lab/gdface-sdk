package com.ahmadarif.gdface.sdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.seeta.sdk.FaceAntiSpoofing
import com.seeta.sdk.FaceDatabase
import com.seeta.sdk.FaceDetector
import com.seeta.sdk.FaceLandmarker
import com.seeta.sdk.SeetaDevice
import com.seeta.sdk.SeetaImageData
import com.seeta.sdk.SeetaModelSetting
import com.seeta.sdk.SeetaPointF
import com.seeta.sdk.SeetaRect
import java.io.File
import org.json.JSONObject

/**
 * On-device face detection, liveness and 1:N face search for Android.
 *
 * Built on the open-source SeetaFace6 engine (BSD-2-Clause), compiled from source with
 * the NDK and bridged to Java by the vendored `com.seeta.sdk` wrapper. Copyright and
 * license notices are in the NOTICE file and must be kept when redistributing.
 *
 * The models (~170MB) are NOT bundled. [init] is `suspend`: on first use per device it
 * downloads them through [GdFaceModelProvider], gated by an API key plus the calling
 * app's package ID (see API_CONTRACT.md); afterwards they are cached and no network is
 * needed. [authorizeUrl] and [apiKey] default to whatever was set on [GdFaceSDK], which
 * itself defaults to the hosted GdFace service and its free public key ([GdFaceConfig]).
 *
 * STATUS: the native pipeline (detect + landmark + liveness + match) was verified on a
 * real device with the same "full" recognizer model this path downloads. The server
 * side of the download flow is verified end to end; the Android download client
 * ([GdFaceModelProvider]) has not been exercised on a device yet.
 */
class GdFaceEngine(
    private val context: Context,
    authorizeUrl: String = GdFaceSDK.authorizeUrl,
    apiKey: String = GdFaceSDK.apiKey
) {

    companion object {
        private const val TAG = "GdFaceEngine"

        /** NOT strictly calibrated yet: 84% similarity was measured for one face on one
         * device (CPH2651) with the "full" model. It needs more samples before it can be
         * trusted as a production default. */
        const val DEFAULT_RECOGNIZE_THRESHOLD = 0.62f
    }

    /** The result of one [recognize] call. Four distinct outcomes so a caller can tell
     * "no face", "a spoof was rejected", "an unknown person" and "a match" apart. */
    sealed class RecognizeResult {
        data object NoFace : RecognizeResult()
        data class LivenessFailed(val rect: Rect) : RecognizeResult()
        data class NotRecognized(val rect: Rect) : RecognizeResult()
        data class Matched(val faceId: String, val score: Float, val rect: Rect) : RecognizeResult()
    }

    private val modelProvider = GdFaceModelProvider(context, authorizeUrl, apiKey)

    private var detector: FaceDetector? = null
    private var landmarker: FaceLandmarker? = null
    private var antiSpoofing: FaceAntiSpoofing? = null
    private var database: FaceDatabase? = null

    private val faceIdByIndex = mutableMapOf<Long, String>()
    private val indexByFaceId = mutableMapOf<String, Long>()

    private val modelsDir: File by lazy { File(context.filesDir, "gdface_models") }
    private val databaseFile: File by lazy { File(modelsDir, "gdface.db") }
    private val indexMapFile: File by lazy { File(modelsDir, "gdface_index_map.json") }

    /**
     * Makes sure the models are available (downloading them if needed, see
     * [GdFaceModelProvider]), then initializes the 4 native objects (FaceDetector,
     * FaceLandmarker, FaceAntiSpoofing, FaceDatabase) and loads the face database saved
     * earlier. Must be called once, and finish without throwing, before enroll() or
     * recognize(). Idempotent: calling it again after a success does nothing.
     *
     * It is `suspend` because the first run on a new device NEEDS the network (the model
     * download can be ~170MB). Call it from a suitable coroutine scope (NOT the main
     * thread) and consider showing progress to the user through [progressListener],
     * since it can take a while on a slow connection.
     *
     * @throws GdFaceLicenseException if authorization or the download fails. See its
     *   subclasses for the specific cause (wrong API key, package ID not authorized,
     *   checksum mismatch, ...). Callers should handle it explicitly (not as a generic
     *   exception) so they can show the right message to the user, e.g. "this app is
     *   not activated" versus "no internet connection".
     */
    suspend fun init(progressListener: GdFaceDownloadProgressListener? = null) {
        if (detector != null) return

        val dir = modelProvider.ensureModelsAvailable(progressListener)
        discardDatabaseIfRecognizerModelChanged()

        detector = FaceDetector(
            SeetaModelSetting(0, arrayOf(File(dir, "face_detector.csta").absolutePath), SeetaDevice.SEETA_DEVICE_CPU)
        )
        landmarker = FaceLandmarker(
            SeetaModelSetting(0, arrayOf(File(dir, "face_landmarker_pts5.csta").absolutePath), SeetaDevice.SEETA_DEVICE_CPU)
        )
        antiSpoofing = FaceAntiSpoofing(
            SeetaModelSetting(
                0,
                arrayOf(File(dir, "fas_first.csta").absolutePath, File(dir, "fas_second.csta").absolutePath),
                SeetaDevice.SEETA_DEVICE_CPU
            )
        )
        database = FaceDatabase(
            SeetaModelSetting(0, arrayOf(File(dir, "face_recognizer.csta").absolutePath), SeetaDevice.SEETA_DEVICE_CPU)
        )

        loadIndexMap()
        if (databaseFile.exists()) {
            database?.Load(databaseFile.absolutePath)
        }
    }

    /** Enrolls one face from a full photo. Detection and landmarks are run here; the
     * caller does not have to crop or align the face first. Enrolling the same [faceId]
     * again replaces the previous face. Returns false if no face was found or the face
     * could not be registered. */
    fun enroll(faceId: String, fullPhotoBitmap: Bitmap): Boolean {
        val db = database ?: return false
        val detection = detectAndMark(fullPhotoBitmap) ?: run {
            Log.e(TAG, "enroll($faceId): no face detected")
            return false
        }
        val (image, _, points) = detection
        return try {
            val index = db.Register(image, points)
            if (index < 0) {
                Log.e(TAG, "enroll($faceId): Register failed, index=$index")
                return false
            }
            indexByFaceId[faceId]?.let { oldIndex -> faceIdByIndex.remove(oldIndex) }
            faceIdByIndex[index] = faceId
            indexByFaceId[faceId] = index
            persist()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not enroll the face for $faceId", e)
            false
        }
    }

    /** Recognizes one frame. This is the heavy call, unlike [detectFaceRect] (cheap, can
     * run on every frame), so the caller should throttle it. */
    fun recognize(bitmap: Bitmap, requireLiveness: Boolean = true): RecognizeResult {
        val db = database ?: return RecognizeResult.NoFace
        val (image, nativeRect, points) = detectAndMark(bitmap) ?: return RecognizeResult.NoFace
        val rect = nativeRect.toAndroidRect()

        if (requireLiveness) {
            val status = try {
                antiSpoofing?.Predict(image, nativeRect, points)
            } catch (e: Exception) {
                Log.e(TAG, "Could not run the liveness check", e)
                return RecognizeResult.NotRecognized(rect)
            }
            if (status != FaceAntiSpoofing.Status.REAL) {
                return RecognizeResult.LivenessFailed(rect)
            }
        }

        return try {
            val similarity = FloatArray(1)
            val index = db.Query(image, points, similarity)
            val faceId = if (index >= 0) faceIdByIndex[index] else null
            val score = similarity[0]
            if (faceId == null || score < DEFAULT_RECOGNIZE_THRESHOLD) {
                RecognizeResult.NotRecognized(rect)
            } else {
                RecognizeResult.Matched(faceId, score, rect)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not query the FaceDatabase", e)
            RecognizeResult.NotRecognized(rect)
        }
    }

    /** Face detection ONLY (no landmarks, liveness or matching). Cheap enough to run on
     * every frame, e.g. to keep a face overlay smooth. Returns the largest face, or null. */
    fun detectFaceRect(bitmap: Bitmap): Rect? {
        val det = detector ?: return null
        val image = bitmap.toNativeImageBgr()
        val faces = try {
            det.Detect(image)
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector.Detect failed (detectFaceRect)", e)
            return null
        }
        if (faces.isNullOrEmpty()) return null
        val rect = faces.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: return null
        return rect.toAndroidRect()
    }

    fun unenroll(faceId: String) {
        val index = indexByFaceId.remove(faceId) ?: return
        faceIdByIndex.remove(index)
        try {
            database?.Delete(index)
            persist()
        } catch (e: Exception) {
            Log.e(TAG, "Could not unenroll the face for $faceId", e)
        }
    }

    fun clearAll() {
        try {
            database?.Clear()
            faceIdByIndex.clear()
            indexByFaceId.clear()
            persist()
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear all GdFace face data", e)
        }
    }

    /** Whether [faceId] already has a feature stored in the SDK's internal FaceDatabase.
     * A consuming app can use it to avoid the state where an enrollment is recorded in
     * the app's own table while the SDK's database is empty. */
    fun isEnrolled(faceId: String): Boolean = indexByFaceId.containsKey(faceId)

    fun enrolledCount(): Int = try {
        (database?.Count() ?: 0L).toInt()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the number of enrolled faces", e)
        0
    }

    fun dispose() {
        detector?.dispose(); detector = null
        landmarker?.dispose(); landmarker = null
        antiSpoofing?.dispose(); antiSpoofing = null
        database?.dispose(); database = null
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    private data class Detection(val image: SeetaImageData, val rect: SeetaRect, val points: Array<SeetaPointF>)

    private fun detectAndMark(bitmap: Bitmap): Detection? {
        val det = detector ?: return null
        val mark = landmarker ?: return null
        val image = bitmap.toNativeImageBgr()
        val faces = try {
            det.Detect(image)
        } catch (e: Exception) {
            Log.e(TAG, "FaceDetector.Detect failed", e)
            return null
        }
        if (faces.isNullOrEmpty()) return null
        val rect = faces.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: return null
        val points = Array(mark.number()) { SeetaPointF() }
        try {
            mark.mark(image, rect, points)
        } catch (e: Exception) {
            Log.e(TAG, "FaceLandmarker.mark failed", e)
            return null
        }
        return Detection(image, rect, points)
    }

    /** Uses the SHA-256 of the recognizer model (from the manifest written by
     * [GdFaceModelProvider]) as its fingerprint. Comparing sizes would miss two different
     * models that happen to have the same size. If the recognizer changed, the old face
     * database is discarded (the feature vector dimension can differ), so features from
     * two different models are never mixed, which would produce wrong or random
     * similarities without any exception. */
    private fun discardDatabaseIfRecognizerModelChanged() {
        val manifestFile = File(modelsDir, "manifest.json")
        val markerFile = File(modelsDir, "recognizer_model.marker")
        val currentSha256 = try {
            val manifest = JSONObject(manifestFile.readText())
            val files = manifest.getJSONArray("files")
            var found: String? = null
            for (i in 0 until files.length()) {
                val entry = files.getJSONObject(i)
                if (entry.getString("name") == "face_recognizer.csta") {
                    found = entry.getString("sha256")
                }
            }
            found
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the model manifest, skipping the recognizer change check", e)
            return
        } ?: return

        val previousSha256 = markerFile.takeIf { it.exists() }?.readText()
        if (previousSha256 == currentSha256) return

        if (previousSha256 != null) {
            Log.i(TAG, "The recognizer model changed, deleting the old face database")
            databaseFile.delete()
            indexMapFile.delete()
        }
        markerFile.writeText(currentSha256)
    }

    private fun persist() {
        try {
            modelsDir.mkdirs()
            database?.Save(databaseFile.absolutePath)
            saveIndexMap()
        } catch (e: Exception) {
            Log.e(TAG, "Could not persist the GdFace database / index map", e)
        }
    }

    private fun saveIndexMap() {
        val json = JSONObject()
        for ((index, faceId) in faceIdByIndex) json.put(index.toString(), faceId)
        indexMapFile.writeText(json.toString())
    }

    private fun loadIndexMap() {
        faceIdByIndex.clear()
        indexByFaceId.clear()
        if (!indexMapFile.exists()) return
        try {
            val json = JSONObject(indexMapFile.readText())
            json.keys().forEach { key ->
                val index = key.toLong()
                val faceId = json.getString(key)
                faceIdByIndex[index] = faceId
                indexByFaceId[faceId] = index
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the GdFace index map, starting empty", e)
        }
    }
}

private fun SeetaRect.toAndroidRect(): Rect = Rect(x, y, x + width, y + height)

private fun Bitmap.toNativeImageBgr(): SeetaImageData {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, 0, 0, w, h)
    val image = SeetaImageData(w, h, 3)
    val data = image.data
    var di = 0
    for (px in pixels) {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        data[di++] = b.toByte()
        data[di++] = g.toByte()
        data[di++] = r.toByte()
    }
    return image
}
