# AI Video Check

Android app that scans videos and estimates whether they look AI-generated or authentic.

**Permanent download link (always latest):**  
https://github.com/beanbr173/ai-video-detector/releases/latest/download/ai-video-detector.apk

## Two ways to scan

### 1. Local video (on-device, private)

1. Tap **Choose video** and pick a file from your gallery or file manager.
2. The app extracts up to 12 evenly spaced frames.
3. Each frame is scored by a bundled TensorFlow Lite model (~4.5 MB, INT8).
4. Nothing is uploaded.

### 2. YouTube / Instagram link (via Link API)

1. Deploy the [`backend/`](backend/) FastAPI service (Docker or local).
2. In the app, open **Link API settings** and enter your API base URL (and optional API key).
3. Paste a YouTube or Instagram URL, or **Share** a link to AI Video Check from another app.
4. The server downloads the video with yt-dlp, runs the same frame ML, returns JSON, and **deletes the file immediately**.

See [backend/README.md](backend/README.md) for API details and deployment.

## Install (sideload)

1. Download [ai-video-detector.apk](https://github.com/beanbr173/ai-video-detector/releases/latest/download/ai-video-detector.apk) on your Android phone (Android 10+).
2. Allow installs from your browser or file app if prompted.
3. For link analysis, deploy the backend and configure **Link API settings** in the app.

## Configure Link API URL at build time (optional)

In [`gradle.properties`](gradle.properties):

```properties
linkApiBaseUrl=https://your-api.example.com
linkApiKey=your-secret-if-set-on-server
```

You can also set these in the app without rebuilding.

## Cloud build (no Android Studio required)

Pushes to `main` trigger [GitHub Actions](.github/workflows/build-apk.yml):

- JDK 17, Android SDK 34, `./gradlew assembleDebug`
- Debug APK artifact (30 days) + GitHub Release with stable filename `ai-video-detector.apk`

Manual run: **Actions → Build APK → Run workflow**.

## Accuracy and limitations

- Results are **probabilistic estimates**, not forensic proof.
- The model is trained for deepfake / synthetic **image** cues; full AI video (Sora, Runway, etc.) may behave differently.
- Social platforms re-encode uploads, which weakens signals.
- Link analysis uses third-party extractors (yt-dlp) that **can break** when YouTube or Instagram change; automated downloading may conflict with platform Terms of Service.
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
| Link API | Python FastAPI + yt-dlp in [`backend/`](backend/) |

## License

App source: MIT (see repository). Third-party model subject to its upstream license.
