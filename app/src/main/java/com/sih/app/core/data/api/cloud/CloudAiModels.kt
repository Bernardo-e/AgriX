package com.sih.app.core.data.api.cloud

import com.sih.app.core.data.api.BackendCropRef
import com.sih.app.core.data.api.BackendDiseaseRef
import com.sih.app.core.sensor.CloudSensorAnalysis

data class CloudDiagnosisRequestData(
    val imageBytes: ByteArray,
    val cropId: String,
    val localDiseaseId: Int? = null,
    val localConfidence: Float? = null,
    val localStatus: String? = null,
    val language: String? = "en",
    val state: String? = null,
    val district: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CloudDiagnosisRequestData
        return imageBytes.contentEquals(other.imageBytes) && cropId == other.cropId
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + cropId.hashCode()
        return result
    }
}

data class CloudDiagnosisInfoData(
    val crop: BackendCropRef,
    val disease: BackendDiseaseRef,
    val confidence: Float,
    val diagnosticStatus: String,
    val isCropCompatible: Boolean = true,
)

data class CloudAdvisoryInfoData(
    val severity: String,
    val urgency: String,
    val overview: String,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val prevention: List<String>,
    val monitoring: List<String>,
    val expertEscalation: String,
    val safetyNote: String,
)

data class CloudDiagnosisResponseData(
    val status: String,
    val provider: String,
    val model: String,
    val latencyMs: Int,
    val diagnosis: CloudDiagnosisInfoData,
    val visualReasoning: String,
    val advisory: CloudAdvisoryInfoData,
)

data class CloudSensorRequestData(
    val source: String = "SIMULATED_BLE",
    val temperature: Double,
    val humidity: Double,
    val soilMoisture: Double,
    val soilPH: Double,
    val cropName: String? = null,
    val soilType: String? = null,
    val diseaseName: String? = null,
    val diseaseConfidence: Float? = null,
    val diseaseStatus: String? = null,
    val language: String? = "en",
)
