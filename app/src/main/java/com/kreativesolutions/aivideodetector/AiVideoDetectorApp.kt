package com.kreativesolutions.aivideodetector

import android.app.Application
import com.kreativesolutions.aivideodetector.api.LinkAnalysisApi
import com.kreativesolutions.aivideodetector.ml.DeepfakeDetector
import com.kreativesolutions.aivideodetector.prefs.ApiSettings

class AiVideoDetectorApp : Application() {
    val deepfakeDetector: DeepfakeDetector by lazy { DeepfakeDetector(this) }
    val apiSettings: ApiSettings by lazy { ApiSettings(this) }
    val linkAnalysisApi: LinkAnalysisApi by lazy { LinkAnalysisApi() }
}
