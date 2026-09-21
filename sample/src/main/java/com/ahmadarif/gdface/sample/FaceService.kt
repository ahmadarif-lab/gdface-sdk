package com.ahmadarif.gdface.sample

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahmadarif.gdface.sdk.GdFaceDownloadProgressListener
import com.ahmadarif.gdface.sdk.GdFaceEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the SDK is, for the screens that have to wait for it. */
sealed interface EngineStatus {
    data object Starting : EngineStatus
    data class Downloading(val fileName: String, val index: Int, val count: Int, val percent: Int) : EngineStatus
    data object Ready : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

/**
 * Owns the [GdFaceEngine]. The engine is not thread safe, so every call goes through
 * [call] and runs on the one thread of [executor], which is also where the camera hands
 * its frames.
 */
class FaceService(context: Context, private val settings: AppSettings) {

    val engine = GdFaceEngine(context.applicationContext)
    val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "gdface-engine") }

    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    var status: EngineStatus by mutableStateOf(EngineStatus.Starting)
        private set

    /** False when the mask model could not be fetched: everything else still works. */
    var maskAvailable by mutableStateOf(false)
        private set

    fun start() {
        status = EngineStatus.Starting
        scope.launch {
            try {
                engine.livenessThreshold = settings.livenessThreshold
                var lastPercent = -1
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
                        status = EngineStatus.Downloading(fileName, fileIndex, fileCount, percent)
                    }
                })
                status = EngineStatus.Ready
            } catch (e: Exception) {
                Log.e(TAG, "init failed: ${e.javaClass.simpleName}: ${e.message}", e)
                status = EngineStatus.Failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            try {
                engine.initMaskDetection()
                maskAvailable = true
            } catch (e: Exception) {
                Log.w(TAG, "Mask detection is not available: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Runs [block] on the engine thread. */
    suspend fun <T> call(block: (GdFaceEngine) -> T): T = withContext(dispatcher) { block(engine) }

    private companion object {
        const val TAG = "GdFaceService"
    }
}
