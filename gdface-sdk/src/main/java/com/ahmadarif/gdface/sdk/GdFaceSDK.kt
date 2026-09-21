package com.ahmadarif.gdface.sdk

/**
 * SDK-level configuration. Call it once, before creating a [GdFaceEngine], for example in
 * `Application.onCreate`:
 *
 * ```kotlin
 * GdFaceSDK.setApiKey("gdsdk_...")            // optional, defaults to the free public key
 * val engine = GdFaceEngine(applicationContext)
 * engine.init()                               // downloads the models on first use
 * ```
 *
 * Every [GdFaceEngine] created afterwards uses these values unless it is given its own
 * `apiKey` / `authorizeUrl` explicitly.
 */
object GdFaceSDK {

    @Volatile
    var apiKey: String = GdFaceConfig.PUBLIC_API_KEY
        private set

    @Volatile
    var authorizeUrl: String = GdFaceConfig.AUTHORIZE_URL
        private set

    /**
     * Stores the API key used to authorize the model download. Nothing is sent over the
     * network here: a key the server does not accept is reported by [GdFaceEngine.init]
     * as [GdFaceLicenseException.InvalidApiKey].
     *
     * @throws IllegalArgumentException if [apiKey] is blank.
     */
    fun setApiKey(apiKey: String) {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        this.apiKey = apiKey.trim()
    }

    /**
     * Points the SDK at a different authorization endpoint (see API_CONTRACT.md), for
     * example a backend of your own.
     *
     * @throws IllegalArgumentException if [url] is not an http(s) URL.
     */
    fun setAuthorizeUrl(url: String) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "authorizeUrl must be an http(s) URL" }
        this.authorizeUrl = url.trim()
    }
}
