package com.kreativesolutions.aivideodetector

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kreativesolutions.aivideodetector.analysis.VideoAnalysisEngine
import com.kreativesolutions.aivideodetector.analysis.VideoAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AiVideoDetectorApp
    private val engine = VideoAnalysisEngine(application, app.deepfakeDetector)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    val linkApiBaseUrl = app.apiSettings.linkApiBaseUrl
    val linkApiKey = app.apiSettings.linkApiKey

    fun setPendingSharedUrl(url: String?) {
        _pendingSharedUrl.value = url
    }

    fun analyzeVideo(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing(statusMessage = "Reading video…")
            try {
                val result = engine.analyze(uri, fileName) { current, total ->
                    _uiState.value = UiState.Analyzing(
                        currentFrame = current,
                        totalFrames = total,
                        statusMessage = "Analyzing frame $current of $total…"
                    )
                }
                _uiState.value = UiState.Result(result)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    error.message ?: "Analysis failed. Try another video file."
                )
            }
        }
    }

    fun analyzeUrl(url: String) {
        viewModelScope.launch {
            if (!LinkUrlHandler.isSupportedUrl(url)) {
                _uiState.value = UiState.Error(
                    "Only YouTube and Instagram links are supported."
                )
                return@launch
            }

            val baseUrl = app.apiSettings.linkApiBaseUrl.first()
            if (baseUrl.isBlank()) {
                _uiState.value = UiState.Error(
                    "Link API URL is not configured. Set it under Link API settings."
                )
                return@launch
            }

            _uiState.value = UiState.Analyzing(
                statusMessage = "Downloading and analyzing link on server…"
            )
            try {
                val apiKey = app.apiSettings.linkApiKey.first()
                val result = app.linkAnalysisApi.analyzeUrl(baseUrl, url, apiKey)
                _uiState.value = UiState.Result(result)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    error.message ?: "Link analysis failed. Check the URL and API settings."
                )
            }
        }
    }

    fun saveApiSettings(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            app.apiSettings.setLinkApiBaseUrl(baseUrl)
            app.apiSettings.setLinkApiKey(apiKey)
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    sealed interface UiState {
        data object Idle : UiState
        data class Analyzing(
            val currentFrame: Int = 0,
            val totalFrames: Int = 0,
            val statusMessage: String = "Analyzing video…"
        ) : UiState

        data class Result(val analysis: VideoAnalysisResult) : UiState
        data class Error(val message: String) : UiState
    }
}
