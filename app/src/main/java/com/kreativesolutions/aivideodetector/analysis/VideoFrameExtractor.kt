package com.kreativesolutions.aivideodetector.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoFrameExtractor {
    suspend fun extractFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = MAX_FRAMES
    ): ExtractedVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 1L

            val frameCount = maxFrames.coerceAtMost(
                (durationMs / MIN_FRAME_SPACING_MS).toInt().coerceAtLeast(1)
            )
            val stepMs = if (frameCount <= 1) 0L else durationMs / (frameCount - 1)

            val frames = buildList {
                for (index in 0 until frameCount) {
                    val timestampMs = (index * stepMs).coerceAtMost(durationMs - 1)
                    val frame = retriever.getFrameAtTime(
                        timestampMs * 1_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: continue
                    add(ExtractedFrame(index, timestampMs, frame))
                }
            }

            ExtractedVideo(durationMs, frames)
        } finally {
            retriever.release()
        }
    }

    data class ExtractedFrame(
        val index: Int,
        val timestampMs: Long,
        val bitmap: Bitmap
    )

    data class ExtractedVideo(
        val durationMs: Long,
        val frames: List<ExtractedFrame>
    )

    companion object {
        const val MAX_FRAMES = 12
        private const val MIN_FRAME_SPACING_MS = 500L
    }
}
