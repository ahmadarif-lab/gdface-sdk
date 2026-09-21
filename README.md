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

- **Public key (free, built in).** `GdFaceConfig.PUBLIC_API_KEY` works for any package
  ID, and it is the default, so you do not have to do anything.
- **Restricted key.** A key scoped to your own package ID(s), if you would rather not
  share a key with every other app.

```kotlin
GdFaceSDK.setApiKey("gdsdk_...")   // only if you use a key of your own
```

Every authorization request is logged with the app's package ID, SDK version and
result. That log is how the maintainers see how many apps use the SDK; nothing about
your users or their faces is sent. The SDK only contacts the server when its local
model cache is empty or invalid (first run, cleared app data, model update).

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
implementation 'com.github.ahmadarif-lab:gdface-sdk:<version>'
```

`<version>` is a release tag of this repository (see the
[releases](https://github.com/ahmadarif-lab/gdface-sdk/releases)); use `main-SNAPSHOT`
for the latest commit of the `main` branch.

**From source.** Clone this repository and install the library into your local Maven
repository:

```
./gradlew :gdface-sdk:publishToMavenLocal
```

```groovy
repositories { mavenLocal() }
dependencies { implementation 'com.greatdayhr.gdface:gdface-sdk:0.1.0-SNAPSHOT' }
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

`init()` is idempotent: calling it again after it succeeded does nothing.

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

The GdFace code (the Kotlin API in `com.greatdayhr.gdface.sdk`, the sample app, the docs
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
