package com.ahmadarif.gdface.sdk

/**
 * Data types and errors for the model authorization + download flow. See
 * API_CONTRACT.md in the repository root for the endpoint a backend must provide. The
 * client (GdFaceModelProvider) assumes the response has exactly the documented shape;
 * if the contract changes, the document and the parsing here must change together.
 */

/** One model file to download, parsed from the authorization response. */
data class GdFaceModelInfo(
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)

/** Download progress, reported per file (not per byte: the models are large, and
 * per-byte reports are too noisy for an ordinary progress bar). */
interface GdFaceDownloadProgressListener {
    fun onFileProgress(fileName: String, fileIndex: Int, fileCount: Int, bytesDownloaded: Long, totalBytes: Long)
}

/**
 * Every failure of the authorization + download flow maps to a subclass of this, so the
 * calling app can use an exhaustive `when` without knowing the HTTP/JSON details behind it.
 */
sealed class GdFaceLicenseException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The API key is missing, unknown, revoked or expired on the server. */
    class InvalidApiKey(message: String = "The API key is invalid, revoked or expired") :
        GdFaceLicenseException(message)

    /** The API key is valid, but the calling app's package ID is not on the list the
     * server allows for that key. */
    class PackageNotAuthorized(val packageId: String) :
        GdFaceLicenseException("Package \"$packageId\" is not authorized to use the GdFace SDK with this API key")

    /** The server refused because of a quota / rate limit. [retryAfterSeconds] comes from
     * the Retry-After header when the server sends one, null otherwise. */
    class RateLimited(val retryAfterSeconds: Int?) :
        GdFaceLicenseException("Too many authorization requests, try again later")

    /** A response outside the codes documented in API_CONTRACT.md (not 200 and not one
     * of the known error codes). */
    class ServerError(val httpCode: Int, message: String) :
        GdFaceLicenseException("The authorization server returned an error ($httpCode): $message")

    /** Could not connect at all (DNS, timeout, SSL, ...). This is NOT a rejection by the
     * server, purely a client-side network problem. */
    class NetworkError(cause: Throwable) :
        GdFaceLicenseException("Could not reach the authorization server: ${cause.message}", cause)

    /** The 200 response body does not match the documented shape (missing field, wrong
     * type). Most likely API_CONTRACT.md and the backend implementation are out of sync. */
    class MalformedResponse(message: String) : GdFaceLicenseException(message)

    /** The SHA-256 of a downloaded file does not match the one promised by the
     * authorization response. The file is discarded and NOT used, so a corrupted or
     * tampered model is never used silently. */
    class ChecksumMismatch(val fileName: String) :
        GdFaceLicenseException("Checksum of \"$fileName\" does not match after download, the file was discarded")

    /** I/O failure while saving a model file to local storage (disk full, ...). */
    class StorageError(fileName: String, cause: Throwable) :
        GdFaceLicenseException("Could not save model \"$fileName\" to local storage: ${cause.message}", cause)
}
