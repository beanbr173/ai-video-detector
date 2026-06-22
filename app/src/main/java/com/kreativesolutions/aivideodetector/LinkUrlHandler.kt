package com.kreativesolutions.aivideodetector

import android.content.Intent
import java.util.regex.Pattern

object LinkUrlHandler {
    private val URL_PATTERN = Pattern.compile(
        "(https?://(?:www\\.)?(?:youtube\\.com|youtu\\.be|instagram\\.com|instagr\\.am)[^\\s\"']+)",
        Pattern.CASE_INSENSITIVE
    )

    fun extractFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val direct = intent.dataString?.trim()
        if (!direct.isNullOrBlank() && isSupportedUrl(direct)) {
            return direct
        }

        val sharedText = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        return extractFromText(sharedText)
    }

    fun extractFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = URL_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1)?.trimEnd('/', '?', '&') else null
    }

    fun isSupportedUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("youtube.com") ||
            normalized.contains("youtu.be") ||
            normalized.contains("instagram.com") ||
            normalized.contains("instagr.am")
    }
}
