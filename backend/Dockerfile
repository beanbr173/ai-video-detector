FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backend/app.py backend/analyzer.py backend/downloader.py ./
COPY app/src/main/assets/deepfake_detector.tflite models/deepfake_detector.tflite

ENV TEMP_ROOT=/tmp/ai-video-detector
EXPOSE 8080

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080"]
