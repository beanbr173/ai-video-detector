# AI Video Check — Link Analysis API

Small FastAPI backend that downloads a **YouTube or Instagram** URL with [yt-dlp](https://github.com/yt-dlp/yt-dlp), extracts frames, runs the same TFLite model as the Android app, and returns JSON. **Videos are deleted immediately after analysis.**

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Liveness check |
| POST | `/analyze-url` | Body: `{"url": "https://..."}` |

Optional header when `LINK_ANALYSIS_API_KEY` is set: `X-Api-Key: your-secret`

## Local run

```bash
cd backend
python -m venv venv
# Windows: venv\Scripts\activate
pip install -r requirements.txt
# Model already at models/deepfake_detector.tflite (copy from app assets if missing)
uvicorn app:app --reload --host 0.0.0.0 --port 8080
```

Test:

```bash
curl -X POST http://localhost:8080/analyze-url \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}"
```

## Docker (from repo root)

```bash
docker build -f backend/Dockerfile -t ai-video-check-api .
docker run --rm -p 8080:8080 ai-video-check-api
```

## Deploy (Railway / Fly.io / Render)

1. Build with `backend/Dockerfile` (context = repo root).
2. Set env vars:
   - `LINK_ANALYSIS_API_KEY` (recommended) — app sends this as `X-Api-Key`
   - `TEMP_ROOT` (optional) — temp download directory
3. Note the public HTTPS URL (e.g. `https://your-app.up.railway.app`).
4. In the Android project, set `linkApiBaseUrl` in [`gradle.properties`](../gradle.properties) before building the APK, or enter the URL in the app’s **Link API settings**.

## Limits

- YouTube + Instagram only (via yt-dlp extractors)
- Max duration: **10 minutes**
- Max file size: **200 MB**
- Extractors can break when platforms change — update yt-dlp regularly

## Legal / ToS

Automated downloading may conflict with YouTube or Meta Terms of Service. Use only for personal research. Results are **not** forensic proof. See main [README](../README.md).
