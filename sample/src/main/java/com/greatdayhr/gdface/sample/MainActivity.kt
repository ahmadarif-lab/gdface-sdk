package com.greatdayhr.gdface.sample

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.greatdayhr.gdface.sdk.GdFaceDownloadProgressListener
import com.greatdayhr.gdface.sdk.GdFaceEngine
import com.greatdayhr.gdface.sdk.GdFaceLicenseException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal GdFace SDK demo: downloads the models on first run, shows the front camera and
 * lets you enroll the face in front of it and see it recognized.
 *
 * The engine is created with no configuration at all, so it uses the free public API key
 * and the hosted authorization service that come with the SDK.
 */
class MainActivity : ComponentActivity() {

    private lateinit var engine: GdFaceEngine
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var nameInput: EditText

    // The engine is not thread safe: every call after init() runs on this one thread.
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var ready = false
    @Volatile private var pendingEnrollName: String? = null
    private var lastRecognizeAt = 0L

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else statusText.text = "Camera permission is required"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        nameInput = findViewById(R.id.nameInput)

        engine = GdFaceEngine(applicationContext)

        findViewById<Button>(R.id.enrollButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            when {
                !ready -> toast("The models are not ready yet")
                name.isEmpty() -> toast("Type a name first")
                else -> pendingEnrollName = name
            }
        }
        findViewById<Button>(R.id.clearButton).setOnClickListener {
            if (!ready) return@setOnClickListener
            analysisExecutor.execute {
                engine.clearAll()
                runOnUiThread { showReady() }
            }
        }

        initEngine()
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun initEngine() {
        statusText.text = "Starting. The first run downloads the models (~170 MB)..."
        lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                var lastPercent = -1
                withContext(Dispatchers.Default) {
                    engine.init(object : GdFaceDownloadProgressListener {
                        override fun onFileProgress(
                            fileName: String,
                            fileIndex: Int,
                            fileCount: Int,
                            bytesDownloaded: Long,
                            totalBytes: Long
                        ) {
                            val percent = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                            if (percent == lastPercent) return
                            lastPercent = percent
                            runOnUiThread {
                                statusText.text = "Downloading ${fileIndex + 1}/$fileCount: $fileName  $percent%"
                            }
                        }
                    })
                }
                ready = true
                Log.i(TAG, "SDK ready in ${SystemClock.elapsedRealtime() - startedAt} ms")
                showReady()
            } catch (e: GdFaceLicenseException) {
                Log.e(TAG, "init failed: ${e.javaClass.simpleName}: ${e.message}", e)
                statusText.text = "Init failed (${e.javaClass.simpleName}): ${e.message}"
            }
        }
    }

    private fun showReady() {
        analysisExecutor.execute {
            val count = engine.enrolledCount()
            runOnUiThread {
                statusText.text = "Ready. Enrolled faces: $count. Type a name and tap Enroll while your face is in view."
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!ready) return
            val bitmap = image.toBitmap().rotated(image.imageInfo.rotationDegrees)

            val enrollName = pendingEnrollName
            if (enrollName != null) {
                pendingEnrollName = null
                val ok = engine.enroll(enrollName, bitmap)
                Log.i(TAG, "enroll($enrollName) -> $ok")
                runOnUiThread {
                    toast(if (ok) "Enrolled $enrollName" else "No face found, try again")
                    showReady()
                }
                return
            }

            // recognize() is the heavy call, so it is throttled instead of running on every frame.
            val now = SystemClock.elapsedRealtime()
            if (now - lastRecognizeAt < RECOGNIZE_INTERVAL_MS) return
            lastRecognizeAt = now

            val result = engine.recognize(bitmap, requireLiveness = true)
            Log.i(TAG, "recognize -> $result")
            runOnUiThread { resultText.text = describe(result) }
        } finally {
            image.close()
        }
    }

    private fun describe(result: GdFaceEngine.RecognizeResult): String = when (result) {
        is GdFaceEngine.RecognizeResult.Matched -> "${result.faceId}  (${(result.score * 100).toInt()}%)"
        is GdFaceEngine.RecognizeResult.NotRecognized -> "Live face, not recognized"
        is GdFaceEngine.RecognizeResult.LivenessFailed -> "Liveness check failed"
        GdFaceEngine.RecognizeResult.NoFace -> "No face"
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.execute { engine.dispose() }
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "GdFaceSample"
        const val RECOGNIZE_INTERVAL_MS = 600L
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
