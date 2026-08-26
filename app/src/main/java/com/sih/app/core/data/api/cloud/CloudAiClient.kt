package com.sih.app.core.data.api.cloud

interface CloudAiClient {
    suspend fun performCloudDiagnosis(
        request: CloudDiagnosisRequestData,
    ): Result<CloudDiagnosisResponseData>
}
