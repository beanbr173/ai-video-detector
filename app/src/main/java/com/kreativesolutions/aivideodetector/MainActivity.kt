package com.kreativesolutions.aivideodetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kreativesolutions.aivideodetector.analysis.VideoAnalysisResult
import com.kreativesolutions.aivideodetector.analysis.VideoVerdict
import com.kreativesolutions.aivideodetector.ui.theme.AiVideoDetectorTheme
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiVideoDetectorTheme {
                MainScreen(initialSharedUrl = LinkUrlHandler.extractFromIntent(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LinkUrlHandler.extractFromIntent(intent)?.let { url ->
            sharedUrlFromIntent = url
        }
    }

    companion object {
        var sharedUrlFromIntent: String? = null
    }
}

@Composable
private fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    initialSharedUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingSharedUrl by viewModel.pendingSharedUrl.collectAsState()
    val linkApiBaseUrl by viewModel.linkApiBaseUrl.collectAsState(initial = "")
    val linkApiKey by viewModel.linkApiKey.collectAsState(initial = "")
    val context = LocalContext.current
    val appVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    var linkInput by remember { mutableStateOf("") }
    var showApiSettings by remember { mutableStateOf(false) }
    var apiBaseUrlInput by remember { mutableStateOf(linkApiBaseUrl) }
    var apiKeyInput by remember { mutableStateOf(linkApiKey) }

    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            linkInput = initialSharedUrl
            viewModel.setPendingSharedUrl(initialSharedUrl)
        }
    }

    LaunchedEffect(MainActivity.sharedUrlFromIntent) {
        MainActivity.sharedUrlFromIntent?.let { url ->
            linkInput = url
            viewModel.setPendingSharedUrl(url)
            MainActivity.sharedUrlFromIntent = null
        }
    }

    LaunchedEffect(linkApiBaseUrl, linkApiKey) {
        apiBaseUrlInput = linkApiBaseUrl
        apiKeyInput = linkApiKey
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        val name = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "video"
        viewModel.analyzeVideo(uri, name)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI Video Check",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Scan a local video on-device, or paste a YouTube / Instagram link (fetched and analyzed on your Link API server).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            when (val state = uiState) {
                MainViewModel.UiState.Idle -> IdleContent(
                    linkInput = linkInput,
                    onLinkInputChange = { linkInput = it },
                    onAnalyzeLink = { viewModel.analyzeUrl(linkInput) },
                    onPickVideo = { picker.launch(arrayOf("video/*")) },
                    showApiSettings = showApiSettings,
                    onToggleApiSettings = { showApiSettings = !showApiSettings },
                    apiBaseUrlInput = apiBaseUrlInput,
                    onApiBaseUrlChange = { apiBaseUrlInput = it },
                    apiKeyInput = apiKeyInput,
                    onApiKeyChange = { apiKeyInput = it },
                    onSaveApiSettings = {
                        viewModel.saveApiSettings(apiBaseUrlInput, apiKeyInput)
                        showApiSettings = false
                    },
                    linkApiConfigured = linkApiBaseUrl.isNotBlank(),
                    pendingSharedUrl = pendingSharedUrl
                )

                is MainViewModel.UiState.Analyzing -> AnalyzingContent(state)

                is MainViewModel.UiState.Result -> ResultContent(
                    result = state.analysis,
                    onAnalyzeAnother = { viewModel.reset() }
                )

                is MainViewModel.UiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.reset() }
                )
            }

            DisclaimerCard()
            Text(
                text = "Version $appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun IdleContent(
    linkInput: String,
    onLinkInputChange: (String) -> Unit,
    onAnalyzeLink: () -> Unit,
    onPickVideo: () -> Unit,
    showApiSettings: Boolean,
    onToggleApiSettings: () -> Unit,
    apiBaseUrlInput: String,
    onApiBaseUrlChange: (String) -> Unit,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onSaveApiSettings: () -> Unit,
    linkApiConfigured: Boolean,
    pendingSharedUrl: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp)
            )
            Text(
                text = "YouTube or Instagram link",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!pendingSharedUrl.isNullOrBlank() && linkInput.isBlank()) {
                Text(
                    text = "Shared link ready — tap Analyze link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                value = linkInput,
                onValueChange = onLinkInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste link") },
                placeholder = { Text("https://youtube.com/... or instagram.com/reel/...") },
                singleLine = true
            )
            Button(
                onClick = onAnalyzeLink,
                modifier = Modifier.fillMaxWidth(),
                enabled = linkInput.isNotBlank()
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Text("Analyze link", modifier = Modifier.padding(start = 8.dp))
            }
            if (!linkApiConfigured) {
                Text(
                    text = "Configure your Link API URL below before analyzing links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF6C00)
                )
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp)
            )
            Text(
                text = "Local video (on-device)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Nothing is uploaded — analysis runs entirely on your phone.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                Text("Choose video", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onToggleApiSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Text("Link API settings", modifier = Modifier.padding(start = 8.dp))
            }
            if (showApiSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = apiBaseUrlInput,
                    onValueChange = onApiBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API base URL") },
                    placeholder = { Text("https://your-api.example.com") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (optional)") },
                    singleLine = true
                )
                Button(
                    onClick = onSaveApiSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Save settings")
                }
            }
        }
    }
}

@Composable
private fun AnalyzingContent(state: MainViewModel.UiState.Analyzing) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.totalFrames > 0) {
                LinearProgressIndicator(
                    progress = { state.currentFrame.toFloat() / state.totalFrames },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    result: VideoAnalysisResult,
    onAnalyzeAnother: () -> Unit
) {
    val verdictColor = when (result.verdict) {
        VideoVerdict.LIKELY_AI -> Color(0xFFC62828)
        VideoVerdict.LIKELY_REAL -> Color(0xFF2E7D32)
        VideoVerdict.UNCERTAIN -> Color(0xFFEF6C00)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.verdictLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = verdictColor
            )
            Text(
                text = "${result.confidencePercent}% confidence",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = result.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!result.sourceUrl.isNullOrBlank()) {
                Text(
                    text = result.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = if (result.analyzedOnDevice) {
                    "Analyzed on device"
                } else {
                    "Analyzed via Link API (video deleted on server after scan)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricRow("Duration", formatDuration(result.durationMs))
            MetricRow("Frames analyzed", result.framesAnalyzed.toString())
            MetricRow(
                "Avg. AI score",
                "${(result.averageFakeProbability * 100).toInt()}%"
            )
            MetricRow(
                "AI-like frames",
                "${(result.fakeFrameRatio * 100).toInt()}%"
            )
        }
    }

    OutlinedButton(onClick = onAnalyzeAnother, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Refresh, contentDescription = null)
        Text("Analyze another", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Could not analyze video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC62828)
            )
            Text(text = message)
            Button(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Text(
                text = "Results are estimates, not proof. Link analysis downloads video via third-party extractors; videos are deleted after scanning but transit your server. Do not use for legal or forensic decisions.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
