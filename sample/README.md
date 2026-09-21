<p align="center">
  <img src="../docs/gdface-icon.png" alt="GDFace" width="128">
</p>

# GDFace sample app

A complete Android app built on the GdFace SDK, in Jetpack Compose (Material 3) with a
single Activity and Navigation. It is meant to be read as much as run: every SDK call is
in two small classes, and the screens only use those.

```
./gradlew :sample:installDebug      # from the repository root, on a connected device
./gradlew :sample:installRelease    # the same, minified: noticeably smoother
```

Compose is much slower in a debug build. Going Back from a camera screen took a frame time of
105 to 121 ms (99th percentile) in the debug build and 34 to 36 ms in the release build, on the
test device. The release build is signed with the debug key so it installs without a keystore.

Requirements: JDK 17, the Android SDK, and a device with a camera. The first launch on a
device downloads the models (~170 MB, plus the 0.9 MB mask model) and shows the progress;
after that the app works offline.

## What it does

| Screen | What you can do |
|---|---|
| Welcome | Shown once. Start the app, and see the model download progress on the first run. |
| Home | The four features, the number of enrolled faces, the recognitions made today. |
| Enroll Face | Take a photo with the camera, or pick one from the gallery, give it a name and save it. Enrolling the same name again adds a photo to that person. |
| Face Enrolled | Confirmation, with the way on to another enrollment or the list. |
| Recognize Image | Check the camera or a gallery photo: who it is and how similar (%), or why not (no match, failed liveness check, no face), plus whether a mask is worn. |
| Real-time Detection | The camera with a box on the largest face and who it is, refreshed live. Pause freezes the frame. The torch lights the flash of a back camera; on the front camera it turns the whole screen white at full brightness, and off puts the brightness back. |
| Face Database | Search, sort, add a photo to a person, rename, delete. |
| Settings | Liveness on/off and its threshold, mask detection on/off and its threshold, clear all faces, export and import the faces. |
| About | The SDK version and what it offers. |

## How it uses the SDK

| File | Role |
|---|---|
| [`FaceService.kt`](src/main/java/com/ahmadarif/gdface/sample/FaceService.kt) | Owns the `GdFaceEngine`. It is not thread safe, so every call goes through `call { }` and runs on one thread, the one the camera frames also arrive on. Runs `init()` with the download progress, then `initMaskDetection()`; the mask model failing to download does not stop the app. |
| [`FaceRepository.kt`](src/main/java/com/ahmadarif/gdface/sample/FaceRepository.kt) | The names and photos of the enrolled people. The SDK's database holds anonymous features only, so the app keeps its own list next to it. |
| [`RealtimeAnalyzer.kt`](src/main/java/com/ahmadarif/gdface/sample/RealtimeAnalyzer.kt) | The real-time view. It follows the largest face with `detectFaceRect()` on every frame it can, and asks `recognize()` who it is every 700 ms, or at once for a new face. On the test device a frame costs about 30 ms to find the face and 170 to 260 ms to identify it. |
| [`ui/`](src/main/java/com/ahmadarif/gdface/sample/ui/) | The screens. `Camera.kt` shows the CameraX preview and turns a frame into a bitmap only when a photo is asked for. |

Three things worth knowing:

- **Several photos per person.** The SDK keeps one face per id, so each photo is enrolled as
  `<personId>#<n>`. A match on any of them is a match on that person.
- **Export and import** write a ZIP with the names and photos and, on import, enroll the
  photos again. So a file also works on another device or after an SDK model update.
  It contains photos of faces: keep it private.
- **The mask threshold is the app's.** The SDK returns a mask score; the app decides with the
  Settings value (0.60 by default). The liveness threshold is the SDK's
  `engine.livenessThreshold` (0.80 by default).

## Not covered

No automated tests. It has been run on one device (OPPO CPH2651, Android 16); the recognition
threshold and the mask threshold have not been calibrated.
