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
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AiVideoDetectorApp
    private val engine = VideoAnalysisEngine(application, app.deepfakeDetector)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun analyzeVideo(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing(0, 0)
            try {
                val result = engine.analyze(uri, fileName) { current, total ->
                    _uiState.value = UiState.Analyzing(current, total)
                }
                _uiState.value = UiState.Result(result)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    error.message ?: "Analysis failed. Try another video file."
                )
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    sealed interface UiState {
        data object Idle : UiState
        data class Analyzing(val currentFrame: Int, val totalFrames: Int) : UiState
        data class Result(val analysis: VideoAnalysisResult) : UiState
        data class Error(val message: String) : UiState
    }
}
