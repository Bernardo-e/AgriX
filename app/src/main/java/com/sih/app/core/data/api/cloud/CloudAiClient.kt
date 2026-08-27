package com.sih.app.core.data.api.cloud

import com.sih.app.core.sensor.CloudSensorAnalysis

interface CloudAiClient {
    suspend fun performCloudDiagnosis(
        request: CloudDiagnosisRequestData,
    ): Result<CloudDiagnosisResponseData>

    suspend fun performCloudSensorAnalysis(
        request: CloudSensorRequestData,
    ): Result<CloudSensorAnalysis>
}
