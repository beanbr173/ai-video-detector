"""Frame extraction and TFLite inference (mirrors Android VideoAnalysisEngine)."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path

import cv2
import numpy as np
import tensorflow as tf

MAX_FRAMES = 12
MIN_FRAME_SPACING_MS = 500
FAKE_FRAME_THRESHOLD = 0.5
AI_THRESHOLD = 0.62
REAL_THRESHOLD = 0.38

MODEL_PATH = Path(__file__).resolve().parent / "models" / "deepfake_detector.tflite"


class VideoVerdict(str, Enum):
    LIKELY_AI = "LIKELY_AI"
    LIKELY_REAL = "LIKELY_REAL"
    UNCERTAIN = "UNCERTAIN"


@dataclass
class FrameAnalysisResult:
    frame_index: int
    timestamp_ms: int
    fake_probability: float


@dataclass
class VideoAnalysisResult:
    file_name: str
    source_url: str
    duration_ms: int
    frames_analyzed: int
    average_fake_probability: float
    fake_frame_ratio: float
    verdict: VideoVerdict
    confidence_percent: int
    frame_results: list[FrameAnalysisResult]

    def to_dict(self) -> dict:
        return {
            "fileName": self.file_name,
            "sourceUrl": self.source_url,
            "durationMs": self.duration_ms,
            "framesAnalyzed": self.frames_analyzed,
            "averageFakeProbability": self.average_fake_probability,
            "fakeFrameRatio": self.fake_frame_ratio,
            "verdict": self.verdict.value,
            "confidencePercent": self.confidence_percent,
            "analyzedOnDevice": False,
            "frameResults": [
                {
                    "frameIndex": frame.frame_index,
                    "timestampMs": frame.timestamp_ms,
                    "fakeProbability": frame.fake_probability,
                }
                for frame in self.frame_results
            ],
        }


class DeepfakeDetector:
    def __init__(self, model_path: Path = MODEL_PATH) -> None:
        if not model_path.exists():
            raise FileNotFoundError(
                f"Model not found at {model_path}. "
                "Copy app/src/main/assets/deepfake_detector.tflite to backend/models/."
            )
        self._interpreter = tf.lite.Interpreter(model_path=str(model_path))
        self._interpreter.allocate_tensors()
        input_details = self._interpreter.get_input_details()[0]
        output_details = self._interpreter.get_output_details()[0]
        self._input_index = input_details["index"]
        self._output_index = output_details["index"]
        shape = input_details["shape"]
        self._input_height = int(shape[1])
        self._input_width = int(shape[2])
        self._input_dtype = input_details["dtype"]
        output_shape = output_details["shape"]
        self._single_output = int(output_shape[-1]) == 1

    def fake_probability(self, frame_bgr: np.ndarray) -> float:
        rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        resized = cv2.resize(
            rgb,
            (self._input_width, self._input_height),
            interpolation=cv2.INTER_LINEAR,
        )

        if self._input_dtype == np.float32:
            tensor = (resized.astype(np.float32) / 127.5) - 1.0
            tensor = np.expand_dims(tensor, axis=0)
        elif self._input_dtype == np.uint8:
            tensor = np.expand_dims(resized.astype(np.uint8), axis=0)
        else:
            raise RuntimeError(f"Unsupported model input type: {self._input_dtype}")

        self._interpreter.set_tensor(self._input_index, tensor)
        self._interpreter.invoke()
        output = self._interpreter.get_tensor(self._output_index)[0]

        if self._single_output:
            return float(np.clip(output[0], 0.0, 1.0))

        exp = np.exp(output - np.max(output))
        probs = exp / exp.sum()
        fake_index = 1 if len(probs) == 2 else int(np.argmax(probs))
        return float(np.clip(probs[fake_index], 0.0, 1.0))


def extract_frames(video_path: Path, max_frames: int = MAX_FRAMES) -> tuple[list[tuple[int, int, np.ndarray]], int]:
    capture = cv2.VideoCapture(str(video_path))
    if not capture.isOpened():
        raise RuntimeError("Could not open downloaded video.")

    try:
        fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        duration_ms = int((frame_count / fps) * 1000) if frame_count > 0 else 0
        if duration_ms <= 0:
            duration_ms = 1

        sample_count = min(
            max_frames,
            max(1, duration_ms // MIN_FRAME_SPACING_MS),
        )
        step_ms = 0 if sample_count <= 1 else duration_ms // (sample_count - 1)

        frames: list[tuple[int, int, np.ndarray]] = []
        for index in range(sample_count):
            timestamp_ms = min(index * step_ms, max(duration_ms - 1, 0))
            capture.set(cv2.CAP_PROP_POS_MSEC, timestamp_ms)
            ok, frame = capture.read()
            if not ok or frame is None:
                continue
            frames.append((index, timestamp_ms, frame))

        return frames, duration_ms
    finally:
        capture.release()


def _determine_verdict(average_fake: float, fake_ratio: float) -> VideoVerdict:
    if average_fake >= AI_THRESHOLD or fake_ratio >= 0.6:
        return VideoVerdict.LIKELY_AI
    if average_fake <= REAL_THRESHOLD and fake_ratio <= 0.25:
        return VideoVerdict.LIKELY_REAL
    return VideoVerdict.UNCERTAIN


def _confidence_percent(
    verdict: VideoVerdict, average_fake: float, fake_ratio: float
) -> int:
    if verdict == VideoVerdict.LIKELY_AI:
        raw = max(average_fake, fake_ratio)
    elif verdict == VideoVerdict.LIKELY_REAL:
        raw = max(1.0 - average_fake, 1.0 - fake_ratio)
    else:
        raw = 1.0 - abs(average_fake - 0.5) * 2.0
    return int(np.clip(raw * 100.0, 5, 99))


_detector: DeepfakeDetector | None = None


def get_detector() -> DeepfakeDetector:
    global _detector
    if _detector is None:
        _detector = DeepfakeDetector()
    return _detector


def analyze_video_file(
    video_path: Path,
    file_name: str,
    source_url: str,
) -> VideoAnalysisResult:
    frames, duration_ms = extract_frames(video_path)
    if not frames:
        raise RuntimeError("Could not read any frames from the downloaded video.")

    detector = get_detector()
    frame_results: list[FrameAnalysisResult] = []
    for index, timestamp_ms, frame in frames:
        probability = detector.fake_probability(frame)
        frame_results.append(
            FrameAnalysisResult(index, timestamp_ms, probability)
        )

    average_fake = float(np.mean([frame.fake_probability for frame in frame_results]))
    fake_ratio = sum(
        1 for frame in frame_results if frame.fake_probability >= FAKE_FRAME_THRESHOLD
    ) / len(frame_results)
    verdict = _determine_verdict(average_fake, fake_ratio)
    confidence = _confidence_percent(verdict, average_fake, fake_ratio)

    return VideoAnalysisResult(
        file_name=file_name,
        source_url=source_url,
        duration_ms=duration_ms,
        frames_analyzed=len(frame_results),
        average_fake_probability=average_fake,
        fake_frame_ratio=fake_ratio,
        verdict=verdict,
        confidence_percent=confidence,
        frame_results=frame_results,
    )
