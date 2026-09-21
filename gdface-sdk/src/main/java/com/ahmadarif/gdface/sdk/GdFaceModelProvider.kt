package com.ahmadarif.gdface.sdk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Android client for the GdFace model authorization + download flow. See
 * API_CONTRACT.md in the repository root for the full endpoint specification a backend
 * must implement. This class is only the client side.
 *
 * In short:
 * 1. Check the local cache (manifest.json). If every model file is there with the
 *    expected size it is used as is, with NO network call at all (a device that has
 *    downloaded the models once keeps working offline).
 * 2. If the cache is invalid or empty: POST to [authorizeUrl] with [apiKey] (header
 *    `X-API-Key`) and the calling app's package ID (`context.packageName`, never
 *    hardcoded; the backend decides whether that package ID may use that key).
 * 3. The server replies with the list of models, their download URLs (ideally
 *    short-lived signed URLs, see API_CONTRACT.md) and the SHA-256 of every file.
 * 4. Every file is streamed to disk (never loaded fully into memory: the largest model
 *    is ~100MB) and hashed with SHA-256 on the way, then compared with what the server
 *    promised. On a mismatch the file is DISCARDED and an exception is thrown, so a
 *    corrupted or tampered model is never used silently.
 */
class GdFaceModelProvider(
    private val context: Context,
    private val authorizeUrl: String,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File by lazy { File(context.filesDir, MODELS_DIR_NAME) }
    private val manifestFile: File by lazy { File(modelsDir, "manifest.json") }

    /** The directory holding all model files, ready for [GdFaceEngine.init]. The download
     * (when needed) happens here and not in the constructor, so the caller controls when
     * network access happens (e.g. wait for Wi-Fi, show a "downloading the models for
     * the first time" dialog). Safe to call again: it does no network work once the
     * cache is valid.
     *
     * [requiredModelNames] is what must be present, both in the cache and in the server's
     * answer. It defaults to [REQUIRED_MODEL_NAMES]; a caller that needs an optional model
     * too (see [MASK_MODEL_NAME]) adds its name. Whatever the server lists is downloaded,
     * required or not. */
    suspend fun ensureModelsAvailable(
        progressListener: GdFaceDownloadProgressListener? = null,
        requiredModelNames: List<String> = REQUIRED_MODEL_NAMES
    ): File = withContext(Dispatchers.IO) {
        if (isCacheValid(requiredModelNames)) {
            Log.d(TAG, "Models are complete in the local cache, skipping the download")
            return@withContext modelsDir
        }

        Log.i(TAG, "Model cache is incomplete or missing, requesting authorization from $authorizeUrl")
        val models = requestAuthorization()

        val missingNames = requiredModelNames - models.map { it.name }.toSet()
        if (missingNames.isNotEmpty()) {
            throw GdFaceLicenseException.MalformedResponse(
                "The authorization response does not include the required models: $missingNames"
            )
        }

        modelsDir.mkdirs()
        models.forEachIndexed { index, model ->
            if (isAlreadyDownloaded(model)) {
                // Left over from an interrupted earlier attempt: no need to fetch it again.
                Log.d(TAG, "Model ${model.name} is already downloaded and verified, skipping")
                progressListener?.onFileProgress(model.name, index, models.size, model.sizeBytes, model.sizeBytes)
            } else {
                downloadAndVerify(model, index, models.size, progressListener)
            }
        }
        saveManifest(models)
        modelsDir
    }

    private fun isCacheValid(requiredModelNames: List<String>): Boolean {
        if (!manifestFile.exists()) return false
        return try {
            val manifest = JSONObject(manifestFile.readText())
            val files = manifest.getJSONArray("files")
            val namesInManifest = mutableSetOf<String>()
            for (i in 0 until files.length()) {
                val entry = files.getJSONObject(i)
                val name = entry.getString("name")
                namesInManifest += name
                val file = File(modelsDir, name)
                if (!file.exists() || file.length() != entry.getLong("sizeBytes")) return false
            }
            namesInManifest.containsAll(requiredModelNames)
        } catch (e: JSONException) {
            Log.e(TAG, "Model manifest is corrupt, treating the cache as invalid", e)
            false
        }
    }

    private fun isAlreadyDownloaded(model: GdFaceModelInfo): Boolean {
        val file = File(modelsDir, model.name)
        if (!file.isFile || file.length() != model.sizeBytes) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var n: Int
                while (input.read(buffer).also { n = it } != -1) digest.update(buffer, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }.equals(model.sha256, ignoreCase = true)
        } catch (e: IOException) {
            false
        }
    }

    private fun requestAuthorization(): List<GdFaceModelInfo> {
        val requestJson = JSONObject().apply {
            put("packageId", context.packageName)
            put("sdkVersion", SDK_VERSION)
            put("modelVariant", "full")
        }
        val request = Request.Builder()
            .url(authorizeUrl)
            .addHeader("X-API-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw GdFaceLicenseException.NetworkError(e)
        }

        response.use {
            val bodyString = it.body?.string().orEmpty()
            return when (it.code) {
                200 -> parseAuthorizedResponse(bodyString)
                401 -> throw GdFaceLicenseException.InvalidApiKey()
                403 -> throw GdFaceLicenseException.PackageNotAuthorized(context.packageName)
                429 -> throw GdFaceLicenseException.RateLimited(it.header("Retry-After")?.toIntOrNull())
                else -> throw GdFaceLicenseException.ServerError(it.code, bodyString.take(500))
            }
        }
    }

    private fun parseAuthorizedResponse(body: String): List<GdFaceModelInfo> {
        try {
            val json = JSONObject(body)
            if (!json.optBoolean("authorized", false)) {
                // A server may answer HTTP 200 with authorized=false (not only through
                // HTTP 401/403). API_CONTRACT.md allows both, so it is checked here too.
                throw GdFaceLicenseException.PackageNotAuthorized(context.packageName)
            }
            val modelsJson: JSONArray = json.getJSONArray("models")
            return (0 until modelsJson.length()).map { i ->
                val m = modelsJson.getJSONObject(i)
                GdFaceModelInfo(
                    name = m.getString("name"),
                    url = m.getString("url"),
                    sha256 = m.getString("sha256"),
                    sizeBytes = m.getLong("sizeBytes")
                )
            }
        } catch (e: JSONException) {
            throw GdFaceLicenseException.MalformedResponse("Could not parse the authorization response: ${e.message}")
        }
    }

    private fun downloadAndVerify(
        model: GdFaceModelInfo,
        index: Int,
        total: Int,
        progressListener: GdFaceDownloadProgressListener?
    ) {
        val request = Request.Builder().url(model.url).get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw GdFaceLicenseException.NetworkError(e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw GdFaceLicenseException.ServerError(resp.code, "Could not download ${model.name}")
            }
            val body = resp.body
                ?: throw GdFaceLicenseException.ServerError(resp.code, "Empty body for ${model.name}")

            val tempFile = File(modelsDir, "${model.name}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesRead = 0L
            try {
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            // A failed read means the connection broke (network problem);
                            // only a failed write is a local storage problem.
                            val n = try {
                                input.read(buffer)
                            } catch (e: IOException) {
                                throw GdFaceLicenseException.NetworkError(e)
                            }
                            if (n == -1) break
                            try {
                                output.write(buffer, 0, n)
                            } catch (e: IOException) {
                                throw GdFaceLicenseException.StorageError(model.name, e)
                            }
                            digest.update(buffer, 0, n)
                            bytesRead += n
                            progressListener?.onFileProgress(model.name, index, total, bytesRead, model.sizeBytes)
                        }
                    }
                }
            } catch (e: GdFaceLicenseException) {
                tempFile.delete()
                throw e
            } catch (e: IOException) {
                tempFile.delete()
                throw GdFaceLicenseException.StorageError(model.name, e)
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha256.equals(model.sha256, ignoreCase = true)) {
                tempFile.delete()
                throw GdFaceLicenseException.ChecksumMismatch(model.name)
            }

            val finalFile = File(modelsDir, model.name)
            finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                throw GdFaceLicenseException.StorageError(model.name, IOException("Could not rename the temporary file"))
            }
            Log.d(TAG, "Model ${model.name} verified and saved (${bytesRead} bytes)")
        }
    }

    private fun saveManifest(models: List<GdFaceModelInfo>) {
        val filesJson = JSONArray()
        models.forEach { m ->
            filesJson.put(
                JSONObject().apply {
                    put("name", m.name)
                    put("sizeBytes", File(modelsDir, m.name).length())
                    put("sha256", m.sha256)
                }
            )
        }
        val manifest = JSONObject().apply {
            put("files", filesJson)
            put("downloadedAtMs", System.currentTimeMillis())
        }
        manifestFile.writeText(manifest.toString())
    }

    companion object {
        private const val TAG = "GdFaceModelProvider"
        private const val MODELS_DIR_NAME = "gdface_models"
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

        /** Sent to the backend with every authorization request. The hosted service logs
         * it; a backend MAY also use it to decide what to send back if the contract ever
         * changes between SDK versions. */
        const val SDK_VERSION = "0.3.0"

        val REQUIRED_MODEL_NAMES = listOf(
            "face_detector.csta",
            "face_recognizer.csta",
            "face_landmarker_pts5.csta",
            "fas_first.csta",
            "fas_second.csta"
        )

        /** Optional sixth model, needed only by [GdFaceEngine.initMaskDetection]. It is
         * NOT in [REQUIRED_MODEL_NAMES] on purpose: a backend that does not list it must
         * keep working for everything else. */
        const val MASK_MODEL_NAME = "mask_detector.csta"
    }
}
