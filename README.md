# GdFace SDK

On-device face detection, liveness detection and 1:N face search for Android. Free and
open source, and it works out of the box with a shared public API key.

## Key Features

- Face detection with 5-point landmarks
- Face liveness detection (anti-spoofing)
- Face recognition: enroll faces, then find who is in front of the camera (1:N)
- Runs fully on the device: no face image ever leaves the phone
- Works offline after the first start on a device
- Small AAR (~5 MB): the models are downloaded once, on first use

## Overview

GdFace SDK wraps the [SeetaFace6](https://github.com/seetafaceengine/SeetaFace6) engine
(BSD-2-Clause), compiled from source with the NDK, behind a small Kotlin API. Face
recognition, liveness and detection all run locally.

| Feature | Included |
|---|---|
| Face detection | yes |
| 5-point face landmarks | yes |
| Face liveness detection | yes |
| Face recognition (enroll, 1:N search) | yes |
| Realtime face rectangle for overlays | yes |

Requirements: Android `minSdk 23`, `arm64-v8a` (and `armeabi-v7a`) devices. The SDK
declares the `INTERNET` and `ACCESS_NETWORK_STATE` permissions itself, for the model
download; camera access is up to your app.

## SDK API Key

The SDK downloads its models from a server, gated by an API key and your app's package
ID (see [`API_CONTRACT.md`](API_CONTRACT.md)).

**Free public key, for everyone:**

| | |
|---|---|
| API key | `gdsdk_durcZZkpXmcU-v9jaCXmhOD7JGxar4uZ` |
| Endpoint | `https://gdsupport.greatdayhr.com/api/sdk/gdface/authorize` |
| Accepts | any package ID |

It is already built into the SDK as the default (`GdFaceConfig.PUBLIC_API_KEY`), so
`GdFaceEngine(context)` works with no setup. You only need to copy it if you set the key
explicitly or call the service yourself:

```kotlin
GdFaceSDK.setApiKey("gdsdk_durcZZkpXmcU-v9jaCXmhOD7JGxar4uZ")   // same as the default
```

```
curl -X POST https://gdsupport.greatdayhr.com/api/sdk/gdface/authorize \
  -H "Content-Type: application/json" \
  -H "X-API-Key: gdsdk_durcZZkpXmcU-v9jaCXmhOD7JGxar4uZ" \
  -d '{"packageId":"com.example.myapp"}'
```

The answer lists the model files with short-lived download links.

**Restricted key (optional).** A key scoped to your own package ID(s), if you would
rather not share a key with every other app. Use it the same way, with
`GdFaceSDK.setApiKey(...)`.

Every authorization request is logged with the app's package ID, SDK version and
result. That log is how the maintainers see how many apps use the SDK; nothing about
your users or their faces is sent. The SDK only contacts the server when its local
model cache is empty or invalid (first run, cleared app data, model update).

## Quick start

A complete, minimal screen: it downloads the models on first run, shows the front camera
and writes who is in front of it at the top. The same flow, with an Enroll button and download progress, is
in [`sample/`](sample/src/main/java/com/ahmadarif/gdface/sample/MainActivity.kt) and runs
with `./gradlew :sample:installDebug`.

1. Add the dependency (see [Set up](#1-set-up)).
2. Declare the camera permission in your `AndroidManifest.xml`. The SDK adds `INTERNET`
   itself.
3. Use CameraX and `androidx.activity` for the camera part: `camera-core`,
   `camera-camera2`, `camera-lifecycle`, `camera-view`, `activity-ktx`,
   `lifecycle-runtime-ktx`.

```kotlin
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
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
import com.ahmadarif.gdface.sdk.GdFaceEngine
import com.ahmadarif.gdface.sdk.GdFaceLicenseException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // No configuration needed: the engine uses the free public API key by default.
    private val engine by lazy { GdFaceEngine(applicationContext) }

    // The engine is not thread safe: run everything after init() on this one thread.
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var ready = false
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        setContentView(FrameLayout(this).apply {
            addView(previewView)
            addView(status, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        })

        // 1. Initialize. The first run on a device downloads the models (~170 MB);
        //    later runs use the local copy and work offline.
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Default) { engine.init() }
                ready = true
                status.text = "Ready"
            } catch (e: GdFaceLicenseException) {
                status.text = "Init failed: ${e.message}"
            }
        }

        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // 2. Feed camera frames to the engine.
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(worker, ::analyze) }
            providerFuture.get()
                .bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!ready) return
            val bitmap = image.toBitmap().rotated(image.imageInfo.rotationDegrees)

            // To enroll the face in this frame instead: engine.enroll("EMP001", bitmap)
            val text = when (val result = engine.recognize(bitmap)) {
                is GdFaceEngine.RecognizeResult.Matched -> "${result.faceId} (${(result.score * 100).toInt()}%)"
                is GdFaceEngine.RecognizeResult.NotRecognized -> "Live face, not recognized"
                is GdFaceEngine.RecognizeResult.LivenessFailed -> "Liveness check failed"
                GdFaceEngine.RecognizeResult.NoFace -> "No face"
            }
            runOnUiThread { status.text = text }
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.execute { engine.dispose() }   // 3. Release the native objects.
        worker.shutdown()
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
```

This exact code was built with R8 minification on and run on a real device. Enrolling is
one call, `engine.enroll("EMP001", bitmap)`, made from the same worker thread.

## About SDK

### 1. Set up

**From JitPack** (public, no account needed). In your `settings.gradle` (or root
`build.gradle`) add the repository:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

and in your app's `build.gradle`:

```groovy
implementation 'com.github.ahmadarif-lab:gdface-sdk:v0.1.0'
```

`v0.1.0` is a release tag of this repository (see the
[releases](https://github.com/ahmadarif-lab/gdface-sdk/releases)). `main-SNAPSHOT` is the
latest commit of the `main` branch; its content changes, so prefer a release tag.

**From source.** Clone this repository and install the library into your local Maven
repository:

```
./gradlew :gdface-sdk:publishToMavenLocal
```

```groovy
repositories { mavenLocal() }
dependencies { implementation 'com.ahmadarif.gdface:gdface-sdk:0.1.0-SNAPSHOT' }
```

or copy the `gdface-sdk` folder into your project, add `include ':gdface-sdk'` to your
`settings.gradle` and `implementation project(':gdface-sdk')` to your app.

### 2. Initializing the SDK

- Step One

Set the API key. This is optional: without it the SDK uses the free public key. Do it
once, for example in `Application.onCreate`.

```kotlin
GdFaceSDK.setApiKey("gdsdk_...")
```

Nothing is sent over the network here. A key the server does not accept is reported by
the next step as `GdFaceLicenseException.InvalidApiKey`.

- Step Two

Create the engine and initialize it. On the first run on a device this downloads the
models (~170 MB), so call it from a coroutine and consider showing progress. Later runs
use the local cache and need no network.

```kotlin
val engine = GdFaceEngine(applicationContext)

lifecycleScope.launch {
    try {
        engine.init(progressListener = object : GdFaceDownloadProgressListener {
            override fun onFileProgress(fileName: String, fileIndex: Int, fileCount: Int, bytesDownloaded: Long, totalBytes: Long) {
                // update a progress bar, e.g. "$fileIndex/$fileCount: $fileName"
            }
        })
    } catch (e: GdFaceLicenseException.PackageNotAuthorized) {
        // this app's package ID is not on the (restricted) API key's list
    } catch (e: GdFaceLicenseException.InvalidApiKey) {
        // the key is unknown, revoked or expired
    } catch (e: GdFaceLicenseException.NetworkError) {
        // no connection: the device must be online for its very first init()
    } catch (e: GdFaceLicenseException) {
        // any other failure, see GdFaceLicensing.kt
    }
}
```

`init()` is idempotent: calling it again after it succeeded does nothing. If the first
download is interrupted (`NetworkError`), call `init()` again: the files that were already
downloaded and verified are kept, only the missing ones are fetched. Keep the app in the
foreground during that first download; some manufacturers cut the network of apps that
are in the background or behind the lock screen.

Threading: `init()` is a `suspend` function, so call it from a coroutine on a background
dispatcher (the native models are loaded on the calling thread). After it returns, use
the engine from one thread at a time; it is not thread safe (the quick start above runs
everything on a single worker thread).

### 3. SDK Classes

- GdFaceSDK

  SDK-wide configuration, set before creating an engine.

  | Function | Description |
  |---|---|
  | `setApiKey(apiKey)` | Sets the API key. Defaults to the public key. Throws `IllegalArgumentException` if blank. |
  | `setAuthorizeUrl(url)` | Points the SDK at another authorization endpoint (your own backend). |

- GdFaceEngine

  The main class. `GdFaceEngine(context, authorizeUrl, apiKey)`: both parameters default
  to the values on `GdFaceSDK`.

- GdFaceEngine.RecognizeResult

  The result of one `recognize()` call.

  | Result | Meaning |
  |---|---|
  | `NoFace` | No face was found in the frame. |
  | `LivenessFailed(rect)` | A face was found but did not pass the liveness check. |
  | `NotRecognized(rect)` | A live face was found but it matches nobody enrolled. |
  | `Matched(faceId, score, rect)` | The face matches `faceId` with similarity `score`. |

  A match needs a similarity of at least `GdFaceEngine.DEFAULT_RECOGNIZE_THRESHOLD`
  (0.62). That value has not been calibrated strictly yet: 84% was measured for one face
  on one device.

- GdFaceLicenseException

  Everything that can go wrong while authorizing or downloading the models.

  | Subclass | When |
  |---|---|
  | `InvalidApiKey` | HTTP 401: the key is unknown, revoked or expired. |
  | `PackageNotAuthorized` | HTTP 403: a restricted key is used from a package that is not on its list. |
  | `RateLimited` | HTTP 429: too many requests. |
  | `NetworkError` | The server could not be reached. |
  | `ServerError` | Any other HTTP error. |
  | `MalformedResponse` | The response is not what the contract describes. |
  | `ChecksumMismatch` | A downloaded model does not match its SHA-256 and was discarded. |
  | `StorageError` | A model could not be written to local storage. |

### 4. APIs

#### - Enroll a face

```kotlin
val ok: Boolean = engine.enroll(faceId = "EMP001", fullPhotoBitmap = bitmap)
```

Detects the face in the photo, computes its landmarks and stores it under `faceId`.
Enrolling the same `faceId` again replaces the previous face. It returns `false` if no
face was found or the face could not be registered. The enrolled faces are saved on the
device and survive restarts.

#### - Recognize a face

```kotlin
val result = engine.recognize(bitmap, requireLiveness = true)
when (result) {
    is GdFaceEngine.RecognizeResult.Matched -> { /* result.faceId, result.score, result.rect */ }
    is GdFaceEngine.RecognizeResult.LivenessFailed -> { }
    is GdFaceEngine.RecognizeResult.NotRecognized -> { }
    GdFaceEngine.RecognizeResult.NoFace -> { }
}
```

Runs detection, landmarks, the optional liveness check and the 1:N search on one frame.
It is the heavy call: throttle it (for example once every 600 ms) instead of calling it
on every camera frame.

#### - Detect a face rectangle

```kotlin
val rect: Rect? = engine.detectFaceRect(bitmap)
```

Detection only (no landmarks, liveness or matching), so it is cheap enough to run on
every frame, for example to keep a face overlay smooth. It returns the largest face in
the frame, or `null`.

#### - Manage enrolled faces

```kotlin
engine.isEnrolled("EMP001")   // Boolean
engine.enrolledCount()        // Int
engine.unenroll("EMP001")     // remove one face
engine.clearAll()             // remove every enrolled face
```

#### - Release resources

```kotlin
engine.dispose()
```

Frees the native objects. Call it when you are done with the engine.

## Third-party software

GdFace SDK is built on SeetaFace6 and TenniS, both BSD-2-Clause, copyright SeetaTech.
Their notices are in [`NOTICE`](NOTICE) and are also packaged inside the AAR
(`META-INF/gdface-sdk-NOTICE.txt`). Keep them when you redistribute the library or an
app that contains it.

## Building

Requirements: JDK 17 and the Android SDK (`ANDROID_HOME` set, or `sdk.dir` in
`local.properties`). The native engine is prebuilt, so no NDK is needed.

```
./gradlew :gdface-sdk:assembleRelease        # the library (AAR)
./gradlew :sample:installDebug               # the sample app, on a connected device
```

Repository layout:

| Path | Contents |
|---|---|
| `gdface-sdk/` | The library: Kotlin API, the `com.seeta.sdk` JNI wrapper and the prebuilt native libraries |
| `sample/` | The sample app described above |
| `API_CONTRACT.md` | The authorization protocol, for people who host their own backend |
| `NOTICE` | Third-party notices |

## Publishing

- **JitPack** is set up and verified: it builds this repository on demand (a build of
  `main` succeeds and serves the AAR, POM and sources). Pushing a Git tag such as
  `v0.1.0` publishes that version; nothing else is needed.
- **Maven Central** is not set up. It needs, one time, a Sonatype Central account with a
  verified namespace, GPG-signed artifacts, and `<developers>` and `<scm>` blocks in the
  POM (the license block is already there). It is the better long-term home.

## License

The GdFace code (the Kotlin API in `com.ahmadarif.gdface.sdk`, the sample app, the docs
and the build scripts) is licensed under the [Apache License 2.0](LICENSE).

The face engine itself is not ours: the `com.seeta.sdk` Java wrapper and the native
libraries are SeetaFace6 and TenniS, by SeetaTech, under the BSD 2-Clause License. Their
notices are in [`NOTICE`](NOTICE).

## Status

- Verified on a real device (OPPO CPH2651, Android 16, arm64): the first `init()`
  authorizes against the hosted service with the public key, downloads and
  checksum-verifies the five models, and initializes the engine in about 7 seconds
  including the download. Face detection, landmarks, liveness and the 1:N query then run
  on live camera frames.
- Not covered by automated tests yet: the device checks above were done by hand with the
  sample app.
