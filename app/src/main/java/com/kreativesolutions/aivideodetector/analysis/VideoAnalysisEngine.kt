package com.kreativesolutions.aivideodetector.analysis

import android.content.Context
import android.net.Uri
import com.kreativesolutions.aivideodetector.ml.DeepfakeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoAnalysisEngine(
    private val context: Context,
    private val detector: DeepfakeDetector,
    private val frameExtractor: VideoFrameExtractor = VideoFrameExtractor()
) {
    suspend fun analyze(
        uri: Uri,
        fileName: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): VideoAnalysisResult = withContext(Dispatchers.Default) {
        val extracted = frameExtractor.extractFrames(context, uri)
        if (extracted.frames.isEmpty()) {
            error("Could not read any frames from this video.")
        }

        val frameResults = extracted.frames.mapIndexed { index, frame ->
            onProgress(index + 1, extracted.frames.size)
            val probability = detector.fakeProbability(frame.bitmap)
            frame.bitmap.recycle()
            FrameAnalysisResult(frame.index, frame.timestampMs, probability)
        }

        val averageFake = frameResults.map { it.fakeProbability }.average().toFloat()
        val fakeRatio = frameResults.count { it.fakeProbability >= FAKE_FRAME_THRESHOLD } /
            frameResults.size.toFloat()
        val verdict = determineVerdict(averageFake, fakeRatio)
        val confidence = confidencePercent(verdict, averageFake, fakeRatio)

        VideoAnalysisResult(
            fileName = fileName,
            durationMs = extracted.durationMs,
            framesAnalyzed = frameResults.size,
            averageFakeProbability = averageFake,
            fakeFrameRatio = fakeRatio,
            verdict = verdict,
            confidencePercent = confidence,
            frameResults = frameResults
        )
    }

    private fun determineVerdict(averageFake: Float, fakeRatio: Float): VideoVerdict {
        return when {
            averageFake >= AI_THRESHOLD || fakeRatio >= 0.6f -> VideoVerdict.LIKELY_AI
            averageFake <= REAL_THRESHOLD && fakeRatio <= 0.25f -> VideoVerdict.LIKELY_REAL
            else -> VideoVerdict.UNCERTAIN
        }
    }

    private fun confidencePercent(
        verdict: VideoVerdict,
        averageFake: Float,
        fakeRatio: Float
    ): Int {
        val raw = when (verdict) {
            VideoVerdict.LIKELY_AI -> maxOf(averageFake, fakeRatio)
            VideoVerdict.LIKELY_REAL -> maxOf(1f - averageFake, 1f - fakeRatio)
            VideoVerdict.UNCERTAIN -> 1f - kotlin.math.abs(averageFake - 0.5f) * 2f
        }
        return (raw * 100f).toInt().coerceIn(5, 99)
    }

    companion object {
        private const val FAKE_FRAME_THRESHOLD = 0.5f
        private const val AI_THRESHOLD = 0.62f
        private const val REAL_THRESHOLD = 0.38f
    }
}
