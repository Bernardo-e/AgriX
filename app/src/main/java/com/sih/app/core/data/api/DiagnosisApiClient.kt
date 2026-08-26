package com.sih.app.core.data.api

data class BackendDiagnosisRequest(
    val cropId: String,
    val diseaseId: Int,
    val confidence: Float,
    val diagnosticStatus: String,
    val source: String = "on_device_tflite",
    val imageId: String? = null,
    val createdAt: String? = null,
)

data class BackendCropRef(
    val id: String,
    val name: String,
)

data class BackendDiseaseRef(
    val id: Int,
    val name: String,
)

data class BackendDiagnosisResponse(
    val id: String,
    val status: String,
    val crop: BackendCropRef,
    val disease: BackendDiseaseRef,
    val confidence: Float,
    val diagnosticStatus: String,
    val source: String,
    val imageId: String?,
    val createdAt: String,
)

data class BackendDiagnosisListResponse(
    val total: Int,
    val diagnoses: List<BackendDiagnosisResponse>,
)

interface DiagnosisApiClient {
    suspend fun recordDiagnosis(request: BackendDiagnosisRequest): Result<BackendDiagnosisResponse>
    suspend fun listDiagnoses(cropId: String? = null, limit: Int = 50): Result<BackendDiagnosisListResponse>
    suspend fun getDiagnosis(diagnosisId: String): Result<BackendDiagnosisResponse>
}
