package com.kreativesolutions.aivideodetector.api

import com.kreativesolutions.aivideodetector.analysis.FrameAnalysisResult
import com.kreativesolutions.aivideodetector.analysis.VideoAnalysisResult
import com.kreativesolutions.aivideodetector.analysis.VideoVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LinkAnalysisApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun analyzeUrl(
        baseUrl: String,
        url: String,
        apiKey: String?
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            error("Link API URL is not configured. Open Link API settings.")
        }

        val body = JSONObject().put("url", url.trim()).toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url("$normalizedBase/analyze-url")
            .post(body)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("X-Api-Key", apiKey.trim())
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = parseErrorDetail(responseBody)
                error(detail ?: "Link analysis failed (HTTP ${response.code}).")
            }
            parseResult(responseBody)
        }
    }

    private fun parseErrorDetail(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            when (val detail = json.opt("detail")) {
                is String -> detail.takeIf { it.isNotBlank() }
                is JSONArray -> detail.optString(0).takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            body.take(200)
        }
    }

    private fun parseResult(body: String): VideoAnalysisResult {
        val json = JSONObject(body)
        val frames = json.optJSONArray("frameResults") ?: JSONArray()
        val frameResults = buildList {
            for (index in 0 until frames.length()) {
                val frame = frames.getJSONObject(index)
                add(
                    FrameAnalysisResult(
                        frameIndex = frame.getInt("frameIndex"),
                        timestampMs = frame.getLong("timestampMs"),
                        fakeProbability = frame.getDouble("fakeProbability").toFloat()
                    )
                )
            }
        }

        return VideoAnalysisResult(
            fileName = json.getString("fileName"),
            durationMs = json.getLong("durationMs"),
            framesAnalyzed = json.getInt("framesAnalyzed"),
            averageFakeProbability = json.getDouble("averageFakeProbability").toFloat(),
            fakeFrameRatio = json.getDouble("fakeFrameRatio").toFloat(),
            verdict = VideoVerdict.valueOf(json.getString("verdict")),
            confidencePercent = json.getInt("confidencePercent"),
            frameResults = frameResults,
            sourceUrl = json.optString("sourceUrl").takeIf { it.isNotBlank() },
            analyzedOnDevice = json.optBoolean("analyzedOnDevice", false)
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
