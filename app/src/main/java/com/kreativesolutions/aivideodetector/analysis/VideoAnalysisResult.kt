package com.kreativesolutions.aivideodetector.analysis

enum class VideoVerdict {
    LIKELY_AI,
    LIKELY_REAL,
    UNCERTAIN
}

data class FrameAnalysisResult(
    val frameIndex: Int,
    val timestampMs: Long,
    val fakeProbability: Float
)

data class VideoAnalysisResult(
    val fileName: String,
    val durationMs: Long,
    val framesAnalyzed: Int,
    val averageFakeProbability: Float,
    val fakeFrameRatio: Float,
    val verdict: VideoVerdict,
    val confidencePercent: Int,
    val frameResults: List<FrameAnalysisResult>,
    val sourceUrl: String? = null,
    val analyzedOnDevice: Boolean = true
) {
    val verdictLabel: String
        get() = when (verdict) {
            VideoVerdict.LIKELY_AI -> "Likely AI-generated"
            VideoVerdict.LIKELY_REAL -> "Likely authentic"
            VideoVerdict.UNCERTAIN -> "Uncertain"
        }
}
