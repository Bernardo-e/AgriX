package com.sih.app.core.ai

import android.graphics.Bitmap

interface PlantDiseaseClassifier {
    fun isAvailable(): Boolean
    suspend fun classify(bitmap: Bitmap, selectedCrop: String? = null): Result<DiagnosticResult>
    fun getSupportedCrops(): List<String>
    fun close()
}
