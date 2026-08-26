package com.sih.app.core.data.api.cloud

import com.sih.app.core.data.api.ApiException
import com.sih.app.core.data.api.BackendCropRef
import com.sih.app.core.data.api.BackendDiseaseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HttpCloudAiClient(
    private val baseUrl: String = "http://10.0.2.2:8000",
    private val connectTimeoutMs: Int = 4000,
    private val readTimeoutMs: Int = 6000,
) : CloudAiClient {

    override suspend fun performCloudDiagnosis(
        request: CloudDiagnosisRequestData,
    ): Result<CloudDiagnosisResponseData> = withContext(Dispatchers.IO) {
        try {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val url = URL("$cleanBaseUrl/api/v1/cloud-diagnosis")
            val boundary = "===AgriXBoundary${System.currentTimeMillis()}==="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }

            DataOutputStream(connection.outputStream).use { dos ->
                // Helper to write form field
                fun writeFormField(fieldName: String, value: String?) {
                    if (value != null) {
                        dos.writeBytes("$twoHyphens$boundary$lineEnd")
                        dos.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"$lineEnd")
                        dos.writeBytes("Content-Type: text/plain; charset=UTF-8$lineEnd$lineEnd")
                        dos.write(value.toByteArray(Charsets.UTF_8))
                        dos.writeBytes(lineEnd)
                    }
                }

                // Write text parameters
                writeFormField("crop_id", request.cropId)
                if (request.localDiseaseId != null) {
                    writeFormField("local_disease_id", request.localDiseaseId.toString())
                }
                if (request.localConfidence != null) {
                    writeFormField("local_confidence", request.localConfidence.toString())
                }
                if (request.localStatus != null) {
                    writeFormField("local_status", request.localStatus)
                }
                if (request.language != null) {
                    writeFormField("language", request.language)
                }
                if (request.state != null) {
                    writeFormField("state", request.state)
                }
                if (request.district != null) {
                    writeFormField("district", request.district)
                }

                // Write image binary file part
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"leaf_scan.jpg\"$lineEnd")
                dos.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")
                dos.write(request.imageBytes)
                dos.writeBytes(lineEnd)

                // End of multipart payload
                dos.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
                dos.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = readStream(connection.inputStream)
                val json = JSONObject(responseBody)

                val diagObj = json.getJSONObject("diagnosis")
                val cropObj = diagObj.getJSONObject("crop")
                val diseaseObj = diagObj.getJSONObject("disease")
                val advObj = json.getJSONObject("advisory")

                // Parse list helper
                fun parseStringList(key: String): List<String> {
                    val arr = advObj.optJSONArray(key) ?: return emptyList()
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    return list
                }

                val responseData = CloudDiagnosisResponseData(
                    status = json.optString("status", "success"),
                    provider = json.optString("provider", "cloud"),
                    model = json.optString("model", "unknown"),
                    latencyMs = json.optInt("latency_ms", 0),
                    diagnosis = CloudDiagnosisInfoData(
                        crop = BackendCropRef(
                            id = cropObj.getString("id"),
                            name = cropObj.getString("name"),
                        ),
                        disease = BackendDiseaseRef(
                            id = diseaseObj.getInt("id"),
                            name = diseaseObj.getString("name"),
                        ),
                        confidence = diagObj.getDouble("confidence").toFloat(),
                        diagnosticStatus = diagObj.getString("diagnostic_status"),
                        isCropCompatible = diagObj.optBoolean("is_crop_compatible", true),
                    ),
                    visualReasoning = json.optString("visual_reasoning", ""),
                    advisory = CloudAdvisoryInfoData(
                        severity = advObj.optString("severity", "moderate"),
                        urgency = advObj.optString("urgency", "prompt"),
                        overview = advObj.optString("overview", ""),
                        symptoms = parseStringList("symptoms"),
                        immediateActions = parseStringList("immediate_actions"),
                        prevention = parseStringList("prevention"),
                        monitoring = parseStringList("monitoring"),
                        expertEscalation = advObj.optString("expert_escalation", ""),
                        safetyNote = advObj.optString("safety_note", ""),
                    ),
                )
                Result.success(responseData)
            } else {
                val errorBody = connection.errorStream?.let { readStream(it) } ?: "HTTP $responseCode"
                Result.failure(ApiException(responseCode, "Cloud AI diagnosis failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readStream(inputStream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}
