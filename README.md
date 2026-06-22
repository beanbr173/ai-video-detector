# AI Video Check

Android app that scans a video **on your phone** and estimates whether it looks AI-generated or authentic. No cloud upload — frame extraction and ML inference run entirely on-device.

**Permanent download link (always latest):**  
https://github.com/beanbr173/ai-video-detector/releases/latest/download/ai-video-detector.apk

## How it works

1. Tap **Choose video** and pick a file from your gallery or file manager.
2. The app extracts up to 12 evenly spaced frames.
3. Each frame is scored by a bundled TensorFlow Lite model (~4.5 MB, INT8).
4. You get a verdict (**Likely AI-generated**, **Likely authentic**, or **Uncertain**) with a confidence score.

## Install (sideload)

1. Download [ai-video-detector.apk](https://github.com/beanbr173/ai-video-detector/releases/latest/download/ai-video-detector.apk) on your Android phone (Android 10+).
2. Allow installs from your browser or file app if prompted.
3. Open **AI Video Check**, pick a video, and wait for the scan to finish.

## Cloud build (no Android Studio required)

Pushes to `main` trigger [GitHub Actions](.github/workflows/build-apk.yml):

- JDK 17, Android SDK 34, `./gradlew assembleDebug`
- Debug APK artifact (30 days) + GitHub Release with stable filename `ai-video-detector.apk`

Manual run: **Actions → Build APK → Run workflow**.

## Accuracy & limitations

- Results are **probabilistic estimates**, not forensic proof.
- The model is trained for deepfake / synthetic **image** cues; full AI video (Sora, Runway, etc.) may behave differently.
- Heavily compressed, filtered, or low-resolution clips reduce accuracy.
- Do **not** use for legal, compliance, or journalism decisions without independent verification.

## ML model attribution

Bundled model: [`deepfake_detector_mobile_int8.tflite`](https://github.com/shovan-mondal/Deepfake-detection/blob/main/deepfake-backend/models/deepfake_detector_mobile_int8.tflite) from [shovan-mondal/Deepfake-detection](https://github.com/shovan-mondal/Deepfake-detection) (MobileNetV2 dual-stream architecture, 224×224 RGB input).

## Browser preview

Static UI mockup (no ML): open [`preview/index.html`](preview/index.html) in a browser.

## Project layout

| Item | Value |
|------|--------|
| Package | `com.kreativesolutions.aivideodetector` |
| minSdk | 29 |
| targetSdk | 34 |
| Stack | Kotlin, Jetpack Compose, TensorFlow Lite |

## License

App source: MIT (see repository). Third-party model subject to its upstream license.
