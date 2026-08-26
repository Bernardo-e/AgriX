package com.sih.app.core.ai.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.sih.app.core.ai.ConfidenceBand
import com.sih.app.core.ai.DiagnosticResult
import com.sih.app.core.ai.DiagnosticStatus
import com.sih.app.core.ai.DiseasePrediction
import com.sih.app.core.ai.PlantDiseaseClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "AgriX_TfliteClassifier"
private const val MODEL_FILENAME = "agrix_stage2_fp16.tflite"
private const val LABEL_MAP_FILENAME = "agrix_label_map.json"
private const val CROP_MAP_FILENAME = "crop_disease_map.json"

private const val INPUT_SIZE = 224
private const val NUM_CHANNELS = 3
private const val NUM_CLASSES = 71

// Confidence thresholds (Matching Prototype Decision Gates)
private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.50f
private const val UNCERTAIN_THRESHOLD = 0.35f
private const val CROP_COMPATIBLE_MIN_PROB = 0.15f

private data class PrototypeFallbackEntry(
    val diseaseId: Int,
    val diseaseName: String,
    val cropDisplay: String,
)

private val PROTOTYPE_DETERMINISTIC_FALLBACKS = mapOf(
    "tomato" to PrototypeFallbackEntry(54, "Tomato Early Blight", "Tomato"),
    "chilli" to PrototypeFallbackEntry(111, "Chilli Anthracnose / Fruit Rot & Dieback", "Chilli"),
    "chili" to PrototypeFallbackEntry(111, "Chilli Anthracnose / Fruit Rot & Dieback", "Chilli"),
    "bell pepper" to PrototypeFallbackEntry(111, "Chilli Anthracnose / Fruit Rot & Dieback", "Chilli"),
    "pepper" to PrototypeFallbackEntry(111, "Chilli Anthracnose / Fruit Rot & Dieback", "Chilli"),
    "rice" to PrototypeFallbackEntry(43, "Rice Blast", "Rice"),
    "paddy" to PrototypeFallbackEntry(43, "Rice Blast", "Rice"),
    "wheat" to PrototypeFallbackEntry(62, "Wheat Leaf / Brown Rust", "Wheat"),
    "sugarcane" to PrototypeFallbackEntry(151, "Sugarcane Red Rot", "Sugarcane"),
    "sugar cane" to PrototypeFallbackEntry(151, "Sugarcane Red Rot", "Sugarcane"),
)

class TflitePlantDiseaseClassifier(
    private val context: Context,
) : PlantDiseaseClassifier {

    private var interpreter: Interpreter? = null
    private val labelMap: MutableMap<Int, String> = mutableMapOf()
    private val cropDisplayMap: MutableMap<String, String> = mutableMapOf()
    private val cropToClassIds: MutableMap<String, Set<Int>> = mutableMapOf()
    private val classIdToCrop: MutableMap<Int, String> = mutableMapOf()

    private val lock = Any()

    init {
        loadModel()
        loadLabelMap()
        loadCropMap()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd(MODEL_FILENAME)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "Loaded TFLite model from assets: $MODEL_FILENAME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model file $MODEL_FILENAME: ${e.message}", e)
            interpreter = null
        }
    }

    private fun loadLabelMap() {
        try {
            val jsonString = context.assets.open(LABEL_MAP_FILENAME).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val classId = key.toIntOrNull()
                if (classId != null) {
                    labelMap[classId] = jsonObject.getString(key)
                }
            }
            Log.d(TAG, "Loaded ${labelMap.size} classes from $LABEL_MAP_FILENAME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load label map $LABEL_MAP_FILENAME: ${e.message}", e)
        }
    }

    private fun loadCropMap() {
        try {
            val jsonString = context.assets.open(CROP_MAP_FILENAME).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val cropKey = keys.next()
                val cropData = jsonObject.getJSONObject(cropKey)
                val cropDisplay = cropData.optString("crop_display", cropKey.replaceFirstChar { it.uppercase() })
                val classIdsArray = cropData.optJSONArray("class_ids")
                val classIdSet = mutableSetOf<Int>()

                if (classIdsArray != null) {
                    for (i in 0 until classIdsArray.length()) {
                        val cId = classIdsArray.getInt(i)
                        classIdSet.add(cId)
                        classIdToCrop[cId] = cropDisplay
                    }
                }
                val normKey = normalizeCropName(cropKey)
                cropDisplayMap[normKey] = cropDisplay
                cropToClassIds[normKey] = classIdSet
            }
            // Add Sugarcane to display map for prototype scope
            cropDisplayMap["sugarcane"] = "Sugarcane"
            cropDisplayMap["sugar cane"] = "Sugarcane"
            cropToClassIds["sugarcane"] = emptySet()
            cropToClassIds["sugar cane"] = emptySet()

            Log.d(TAG, "Loaded ${cropToClassIds.size} crops from $CROP_MAP_FILENAME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load crop map $CROP_MAP_FILENAME: ${e.message}", e)
        }
    }

    private fun normalizeCropName(crop: String): String {
        return crop.trim().lowercase().replace("_", " ")
    }

    override fun isAvailable(): Boolean {
        synchronized(lock) {
            return interpreter != null && labelMap.isNotEmpty()
        }
    }

    override fun getSupportedCrops(): List<String> {
        synchronized(lock) {
            val crops = cropDisplayMap.values.toMutableSet()
            crops.add("Sugarcane")
            return crops.sorted()
        }
    }

    override suspend fun classify(
        bitmap: Bitmap,
        selectedCrop: String?,
    ): Result<DiagnosticResult> = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val currentInterpreter = interpreter
            if (currentInterpreter == null) {
                return@withContext Result.failure(
                    IllegalStateException("TensorFlow Lite interpreter is not loaded.")
                )
            }

            try {
                // 1. Preprocess Bitmap to 224x224 RGB FloatBuffer [-1, 1]
                val scaledBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
                }

                val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * 4).apply {
                    order(ByteOrder.nativeOrder())
                    rewind()
                }

                val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
                scaledBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

                for (pixel in intValues) {
                    val r = ((pixel shr 16 and 0xFF) / 127.5f) - 1.0f
                    val g = ((pixel shr 8 and 0xFF) / 127.5f) - 1.0f
                    val b = ((pixel and 0xFF) / 127.5f) - 1.0f
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }

                // 2. Output Tensor (1, 71)
                val outputArray = Array(1) { FloatArray(NUM_CLASSES) }

                // 3. Execute TFLite Inference
                inputBuffer.rewind()
                currentInterpreter.run(inputBuffer, outputArray)

                val probabilities = outputArray[0]

                // 4. Form Ranked Global Predictions
                val indexedProbs = probabilities.mapIndexed { index, prob -> index to prob }
                    .sortedByDescending { it.second }

                val topGlobalPredictions = indexedProbs.take(3).mapIndexed { rank, (classId, prob) ->
                    val diseaseName = labelMap[classId] ?: "Unknown Disease (ID: $classId)"
                    val crop = classIdToCrop[classId] ?: "General"
                    DiseasePrediction(
                        diseaseName = formatDiseaseTitle(diseaseName),
                        confidence = prob,
                        classId = classId,
                        crop = crop,
                        rank = rank + 1,
                    )
                }

                val topGlobal = topGlobalPredictions.firstOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("No predictions generated."))

                val maxConfidence = topGlobal.confidence

                // 5. Post-Inference Crop Context Reasoning
                val normSelected = selectedCrop?.let { normalizeCropName(it) }
                val cropClassIds = normSelected?.let { cropToClassIds[it] }
                val selectedCropDisplay = normSelected?.let { cropDisplayMap[it] ?: selectedCrop }

                val cropCompatiblePredictions = if (cropClassIds != null && cropClassIds.isNotEmpty()) {
                    indexedProbs.filter { it.first in cropClassIds }
                        .take(3)
                        .mapIndexed { rank, (classId, prob) ->
                            val diseaseName = labelMap[classId] ?: "Unknown Disease"
                            DiseasePrediction(
                                diseaseName = formatDiseaseTitle(diseaseName),
                                confidence = prob,
                                classId = classId,
                                crop = selectedCropDisplay ?: "Selected Crop",
                                rank = rank + 1,
                            )
                        }
                } else {
                    emptyList()
                }

                var primaryPrediction: DiseasePrediction?
                var status: DiagnosticStatus
                var message: String
                var isPrototypeFallback = false
                var fallbackNotice: String? = null

                if (normSelected == null) {
                    // Mode A: Global Unconstrained Mode
                    primaryPrediction = topGlobal
                    status = when {
                        maxConfidence >= HIGH_CONFIDENCE_THRESHOLD -> DiagnosticStatus.CONFIDENT
                        maxConfidence >= MEDIUM_CONFIDENCE_THRESHOLD -> DiagnosticStatus.MODERATE_CONFIDENCE
                        maxConfidence >= UNCERTAIN_THRESHOLD -> DiagnosticStatus.LOW_CONFIDENCE
                        else -> DiagnosticStatus.UNKNOWN_OR_UNCERTAIN
                    }
                    message = when (status) {
                        DiagnosticStatus.CONFIDENT -> "High confidence detection."
                        DiagnosticStatus.MODERATE_CONFIDENCE -> "Moderate confidence match."
                        DiagnosticStatus.LOW_CONFIDENCE -> "Low confidence candidate. Please verify leaf symptoms."
                        DiagnosticStatus.UNKNOWN_OR_UNCERTAIN -> "Unable to confidently identify the disease."
                        DiagnosticStatus.PROTOTYPE_FALLBACK -> "Prototype guidance."
                    }
                } else {
                    // Mode B: Crop-Aware Diagnosis Mode
                    val isProtoCrop = normSelected in PROTOTYPE_DETERMINISTIC_FALLBACKS

                    if (cropClassIds != null && topGlobal.classId in cropClassIds && maxConfidence >= UNCERTAIN_THRESHOLD) {
                        // Real model result is directly crop compatible with adequate confidence
                        primaryPrediction = topGlobal
                        status = when {
                            maxConfidence >= HIGH_CONFIDENCE_THRESHOLD -> DiagnosticStatus.CONFIDENT
                            maxConfidence >= MEDIUM_CONFIDENCE_THRESHOLD -> DiagnosticStatus.MODERATE_CONFIDENCE
                            else -> DiagnosticStatus.LOW_CONFIDENCE
                        }
                        message = when (status) {
                            DiagnosticStatus.CONFIDENT -> "High confidence match for $selectedCropDisplay."
                            DiagnosticStatus.MODERATE_CONFIDENCE -> "Moderate confidence match for $selectedCropDisplay."
                            else -> "Low confidence match for $selectedCropDisplay."
                        }
                    } else {
                        // Check if a secondary candidate belongs to selected crop with sufficient confidence
                        val topCompatible = cropCompatiblePredictions.firstOrNull()
                        if (topCompatible != null && topCompatible.confidence >= CROP_COMPATIBLE_MIN_PROB) {
                            // Real compatible model prediction
                            primaryPrediction = topCompatible
                            status = if (topCompatible.confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                                DiagnosticStatus.MODERATE_CONFIDENCE
                            } else {
                                DiagnosticStatus.LOW_CONFIDENCE
                            }
                            message = "Visual symptoms match ${topCompatible.diseaseName} on $selectedCropDisplay (Note visual overlap with ${topGlobal.crop})."
                        } else if (isProtoCrop) {
                            // ACTIVATE CROP-AWARE PROTOTYPE FALLBACK (Prototype Crops Only)
                            val fallbackEntry = PROTOTYPE_DETERMINISTIC_FALLBACKS[normSelected]!!
                            primaryPrediction = DiseasePrediction(
                                diseaseName = fallbackEntry.diseaseName,
                                confidence = 0f, // Never fabricate AI confidence
                                classId = fallbackEntry.diseaseId,
                                crop = selectedCropDisplay ?: fallbackEntry.cropDisplay,
                                rank = 1,
                            )
                            status = DiagnosticStatus.PROTOTYPE_FALLBACK
                            isPrototypeFallback = true
                            fallbackNotice = "Prototype Guidance — Verification Recommended"
                            message = "The local AI result was uncertain for this image. Showing a crop-specific possible diagnosis for prototype guidance."
                        } else {
                            // Standard UNKNOWN_OR_UNCERTAIN for other crops
                            primaryPrediction = topGlobal
                            status = DiagnosticStatus.UNKNOWN_OR_UNCERTAIN
                            message = "Unable to confidently identify a disease for $selectedCropDisplay. Symptoms most closely resemble ${topGlobal.crop} (${topGlobal.diseaseName})."
                        }
                    }
                }

                val confidenceBand = when (status) {
                    DiagnosticStatus.PROTOTYPE_FALLBACK -> ConfidenceBand.PROTOTYPE_FALLBACK
                    DiagnosticStatus.UNKNOWN_OR_UNCERTAIN -> ConfidenceBand.UNCERTAIN
                    else -> when {
                        (primaryPrediction?.confidence ?: 0f) >= HIGH_CONFIDENCE_THRESHOLD -> ConfidenceBand.HIGH
                        (primaryPrediction?.confidence ?: 0f) >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceBand.MEDIUM
                        (primaryPrediction?.confidence ?: 0f) >= UNCERTAIN_THRESHOLD -> ConfidenceBand.LOW
                        else -> ConfidenceBand.UNCERTAIN
                    }
                }

                val diagnosticResult = DiagnosticResult(
                    primaryPrediction = primaryPrediction,
                    topPredictions = topGlobalPredictions,
                    cropCompatiblePredictions = cropCompatiblePredictions,
                    status = status,
                    message = message,
                    selectedCrop = selectedCropDisplay,
                    confidenceBand = confidenceBand,
                    isPrototypeFallback = isPrototypeFallback,
                    rawModelTopPrediction = topGlobal,
                    fallbackNotice = fallbackNotice,
                )

                Result.success(diagnosticResult)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun formatDiseaseTitle(name: String): String {
        return name.split(" ", "_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    override fun close() {
        synchronized(lock) {
            try {
                interpreter?.close()
                interpreter = null
            } catch (e: Exception) {
                Log.e(TAG, "Error closing TFLite interpreter: ${e.message}", e)
            }
        }
    }
}
