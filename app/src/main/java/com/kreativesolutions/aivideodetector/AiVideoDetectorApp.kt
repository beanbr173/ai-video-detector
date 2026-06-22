package com.kreativesolutions.aivideodetector

import android.app.Application
import com.kreativesolutions.aivideodetector.ml.DeepfakeDetector

class AiVideoDetectorApp : Application() {
    val deepfakeDetector: DeepfakeDetector by lazy { DeepfakeDetector(this) }
}
