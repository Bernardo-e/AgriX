package com.sih.app.core.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpDiagnosisApiClient(
    private val baseUrl: String = "http://10.0.2.2:8000",
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000,
) : DiagnosisApiClient {

    override suspend fun recordDiagnosis(request: BackendDiagnosisRequest): Result<BackendDiagnosisResponse> =
        withContext(Dispatchers.IO) {
            try {
                val cleanBaseUrl = baseUrl.trimEnd('/')
                val url = URL("$cleanBaseUrl/api/v1/diagnoses")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                }

                val payload = JSONObject().apply {
                    put("crop_id", request.cropId)
                    put("disease_id", request.diseaseId)
                    put("confidence", request.confidence.toDouble())
                    put("diagnostic_status", request.diagnosticStatus)
                    put("source", request.source)
                    if (request.imageId != null) {
                        put("image_id", request.imageId)
                    }
                    if (request.createdAt != null) {
                        put("created_at", request.createdAt)
                    }
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseBody = readStream(connection.inputStream)
                    val json = JSONObject(responseBody)
                    val cropObj = json.getJSONObject("crop")
                    val diseaseObj = json.getJSONObject("disease")

                    val response = BackendDiagnosisResponse(
                        id = json.getString("id"),
                        status = json.optString("status", "recorded"),
                        crop = BackendCropRef(
                            id = cropObj.getString("id"),
                            name = cropObj.getString("name"),
                        ),
                        disease = BackendDiseaseRef(
                            id = diseaseObj.getInt("id"),
                            name = diseaseObj.getString("name"),
                        ),
                        confidence = json.getDouble("confidence").toFloat(),
                        diagnosticStatus = json.getString("diagnostic_status"),
                        source = json.optString("source", "on_device_tflite"),
                        imageId = if (json.isNull("image_id")) null else json.optString("image_id"),
                        createdAt = json.getString("created_at"),
                    )
                    Result.success(response)
                } else {
                    val errorBody = connection.errorStream?.let { readStream(it) } ?: "HTTP $responseCode"
                    Result.failure(ApiException(responseCode, "Failed to record diagnosis: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listDiagnoses(
        cropId: String?,
        limit: Int,
    ): Result<BackendDiagnosisListResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val queryParams = mutableListOf("limit=$limit")
            if (!cropId.isNullOrBlank()) {
                queryParams.add("crop_id=$cropId")
            }
            val urlString = "$cleanBaseUrl/api/v1/diagnoses?${queryParams.joinToString("&")}"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = readStream(connection.inputStream)
                val json = JSONObject(responseBody)
                val total = json.getInt("total")
                val diagnosesArray = json.getJSONArray("diagnoses")
                val list = mutableListOf<BackendDiagnosisResponse>()

                for (i in 0 until diagnosesArray.length()) {
                    val item = diagnosesArray.getJSONObject(i)
                    val cropObj = item.getJSONObject("crop")
                    val diseaseObj = item.getJSONObject("disease")
                    list.add(
                        BackendDiagnosisResponse(
                            id = item.getString("id"),
                            status = item.optString("status", "recorded"),
                            crop = BackendCropRef(
                                id = cropObj.getString("id"),
                                name = cropObj.getString("name"),
                            ),
                            disease = BackendDiseaseRef(
                                id = diseaseObj.getInt("id"),
                                name = diseaseObj.getString("name"),
                            ),
                            confidence = item.getDouble("confidence").toFloat(),
                            diagnosticStatus = item.getString("diagnostic_status"),
                            source = item.optString("source", "on_device_tflite"),
                            imageId = if (item.isNull("image_id")) null else item.optString("image_id"),
                            createdAt = item.getString("created_at"),
                        )
                    )
                }

                Result.success(BackendDiagnosisListResponse(total = total, diagnoses = list))
            } else {
                val errorBody = connection.errorStream?.let { readStream(it) } ?: "HTTP $responseCode"
                Result.failure(ApiException(responseCode, "Failed to list diagnoses: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDiagnosis(diagnosisId: String): Result<BackendDiagnosisResponse> =
        withContext(Dispatchers.IO) {
            try {
                val cleanBaseUrl = baseUrl.trimEnd('/')
                val url = URL("$cleanBaseUrl/api/v1/diagnoses/$diagnosisId")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseBody = readStream(connection.inputStream)
                    val json = JSONObject(responseBody)
                    val cropObj = json.getJSONObject("crop")
                    val diseaseObj = json.getJSONObject("disease")

                    val response = BackendDiagnosisResponse(
                        id = json.getString("id"),
                        status = json.optString("status", "recorded"),
                        crop = BackendCropRef(
                            id = cropObj.getString("id"),
                            name = cropObj.getString("name"),
                        ),
                        disease = BackendDiseaseRef(
                            id = diseaseObj.getInt("id"),
                            name = diseaseObj.getString("name"),
                        ),
                        confidence = json.getDouble("confidence").toFloat(),
                        diagnosticStatus = json.getString("diagnostic_status"),
                        source = json.optString("source", "on_device_tflite"),
                        imageId = if (json.isNull("image_id")) null else json.optString("image_id"),
                        createdAt = json.getString("created_at"),
                    )
                    Result.success(response)
                } else {
                    val errorBody = connection.errorStream?.let { readStream(it) } ?: "HTTP $responseCode"
                    Result.failure(ApiException(responseCode, "Failed to get diagnosis: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readStream(inputStream: java.io.InputStream): String {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            return sb.toString()
        }
    }
}

class ApiException(val statusCode: Int, message: String) : Exception("Status $statusCode: $message")
