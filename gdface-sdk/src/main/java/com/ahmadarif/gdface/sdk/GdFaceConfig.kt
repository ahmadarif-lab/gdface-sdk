package com.ahmadarif.gdface.sdk

/** Defaults for the hosted GdFace model service. Override them to use your own backend. */
object GdFaceConfig {

    /** Endpoint that authorizes the app and returns short-lived model download links. */
    const val AUTHORIZE_URL = "https://gdsupport.greatdayhr.com/api/sdk/gdface/authorize"

    /**
     * Free public API key, accepted for any package ID. It is meant to be shared: every
     * request is logged with its package ID so the maintainers can see how many apps use
     * the SDK. Rotating it would block every app that embeds it, so it is only rotated if
     * the key is abused.
     */
    const val PUBLIC_API_KEY = "gdsdk_durcZZkpXmcU-v9jaCXmhOD7JGxar4uZ"
}
