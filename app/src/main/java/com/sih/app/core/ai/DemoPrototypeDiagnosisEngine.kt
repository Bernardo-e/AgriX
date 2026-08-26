package com.sih.app.core.ai

import android.graphics.Bitmap
import kotlin.math.abs

class DemoPrototypeDiagnosisEngine(
    private val advisoryRepository: AdvisoryRepository,
    val realClassifier: PlantDiseaseClassifier? = null,
) : DiagnosisEngine {

    override val modeName: String = "DEMO_PROTOTYPE"

    // 5 Priority Crops with their exact 5 SIH Demo diseases
    private val demoCatalogByCrop: Map<String, List<DemoDiseaseEntry>> = mapOf(
        "tomato" to listOf(
            DemoDiseaseEntry(54, "Tomato Early Blight", "Tomato"),
            DemoDiseaseEntry(55, "Tomato Late Blight", "Tomato"),
            DemoDiseaseEntry(59, "Tomato Leaf Curl", "Tomato"),
            DemoDiseaseEntry(53, "Tomato Bacterial Spot", "Tomato"),
            DemoDiseaseEntry(105, "Tomato Fusarium Wilt", "Tomato"),
        ),
        "chilli" to listOf(
            DemoDiseaseEntry(111, "Chilli Anthracnose / Fruit Rot", "Chilli"),
            DemoDiseaseEntry(112, "Chilli Leaf Curl", "Chilli"),
            DemoDiseaseEntry(113, "Chilli Powdery Mildew", "Chilli"),
            DemoDiseaseEntry(10, "Chilli Bacterial Leaf Spot", "Chilli"),
            DemoDiseaseEntry(115, "Chilli Fusarium Wilt", "Chilli"),
        ),
        "rice" to listOf(
            DemoDiseaseEntry(43, "Rice Blast", "Rice"),
            DemoDiseaseEntry(132, "Rice Bacterial Leaf Blight", "Rice"),
            DemoDiseaseEntry(44, "Rice Brown Spot", "Rice"),
            DemoDiseaseEntry(133, "Rice Sheath Blight", "Rice"),
            DemoDiseaseEntry(136, "Rice Tungro", "Rice"),
        ),
        "wheat" to listOf(
            DemoDiseaseEntry(62, "Wheat Leaf / Brown Rust", "Wheat"),
            DemoDiseaseEntry(67, "Wheat Stripe / Yellow Rust", "Wheat"),
            DemoDiseaseEntry(66, "Wheat Stem Rust", "Wheat"),
            DemoDiseaseEntry(63, "Wheat Loose Smut", "Wheat"),
            DemoDiseaseEntry(68, "Wheat Karnal Bunt", "Wheat"),
        ),
        "sugarcane" to listOf(
            DemoDiseaseEntry(151, "Sugarcane Red Rot", "Sugarcane"),
            DemoDiseaseEntry(152, "Sugarcane Smut", "Sugarcane"),
            DemoDiseaseEntry(153, "Sugarcane Wilt", "Sugarcane"),
            DemoDiseaseEntry(154, "Sugarcane Grassy Shoot Disease", "Sugarcane"),
            DemoDiseaseEntry(156, "Sugarcane Pokkah Boeng", "Sugarcane"),
        ),
    )

    override suspend fun diagnose(
        bitmap: Bitmap,
        selectedCrop: String?,
    ): Result<DiagnosisEngineResult> {
        // Step 1: Image Relevance Assessment Gate
        val assessment = PlantRelevanceAssessor.assess(bitmap)

        if (assessment == ImageAssessment.IRRELEVANT_IMAGE) {
            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.IRRELEVANT_IMAGE,
                    diagnosticResult = null,
                    source = DiagnosisSource.IRRELEVANT_IMAGE,
                    isIrrelevant = true,
                )
            )
        }

        val normCrop = selectedCrop?.let { normalizeCrop(it) }
        val cropDisplayName = selectedCrop?.replaceFirstChar { it.uppercase() } ?: "Crop"

        // Step 2: Healthy Crop Detection (Strict & Conservative)
        if (assessment == ImageAssessment.HEALTHY_CROP) {
            val healthyDiag = DiagnosticResult(
                primaryPrediction = DiseasePrediction(
                    diseaseName = "Healthy $cropDisplayName",
                    confidence = 1.0f,
                    classId = -1,
                    crop = cropDisplayName,
                    rank = 1,
                ),
                topPredictions = emptyList(),
                cropCompatiblePredictions = emptyList(),
                status = DiagnosticStatus.CONFIDENT,
                message = "No visible disease detected. Continue regular monitoring and good crop management.",
                selectedCrop = cropDisplayName,
                confidenceBand = ConfidenceBand.HIGH,
                isPrototypeFallback = false,
            )

            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.HEALTHY_CROP,
                    diagnosticResult = healthyDiag,
                    source = DiagnosisSource.HEALTHY_ASSESSMENT,
                    isHealthy = true,
                )
            )
        }

        // Step 3: Real TFLite Inference (if confident and crop-compatible)
        if (realClassifier != null && realClassifier.isAvailable()) {
            val realRes = realClassifier.classify(bitmap, selectedCrop)
            val diag = realRes.getOrNull()
            if (diag != null &&
                diag.status != DiagnosticStatus.UNKNOWN_OR_UNCERTAIN &&
                diag.status != DiagnosticStatus.PROTOTYPE_FALLBACK &&
                diag.primaryPrediction != null &&
                diag.primaryPrediction.confidence >= 0.35f
            ) {
                return Result.success(
                    DiagnosisEngineResult(
                        assessment = ImageAssessment.DISEASE_SUSPECTED,
                        diagnosticResult = diag,
                        source = DiagnosisSource.REAL_TFLITE,
                    )
                )
            }
        }

        // Step 4: SIH Demo Fallback Layer for Uncertain / Outside-Distribution Crop Images
        val sampleSize = 64
        val scaled = if (bitmap.width == sampleSize && bitmap.height == sampleSize) {
            bitmap
        } else {
            try {
                Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, true)
            } catch (t: Throwable) {
                bitmap
            }
        }
        val pixels = IntArray(sampleSize * sampleSize)
        try {
            scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        } catch (t: Throwable) {
            // JVM fallback
        }

        return executeFallback(pixels, normCrop, cropDisplayName, assessment)
    }

    fun diagnosePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        selectedCrop: String?,
        mockRealDiagnosticResult: DiagnosticResult? = null,
    ): Result<DiagnosisEngineResult> {
        // Step 1: Image Relevance Assessment Gate
        val assessment = if (pixels.isEmpty()) {
            ImageAssessment.IRRELEVANT_IMAGE
        } else {
            PlantRelevanceAssessor.assessPixels(pixels, width, height)
        }

        if (assessment == ImageAssessment.IRRELEVANT_IMAGE) {
            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.IRRELEVANT_IMAGE,
                    diagnosticResult = null,
                    source = DiagnosisSource.IRRELEVANT_IMAGE,
                    isIrrelevant = true,
                )
            )
        }

        val normCrop = selectedCrop?.let { normalizeCrop(it) }
        val cropDisplayName = selectedCrop?.replaceFirstChar { it.uppercase() } ?: "Crop"

        // Step 2: Healthy Crop Detection
        if (assessment == ImageAssessment.HEALTHY_CROP) {
            val healthyDiag = DiagnosticResult(
                primaryPrediction = DiseasePrediction(
                    diseaseName = "Healthy $cropDisplayName",
                    confidence = 1.0f,
                    classId = -1,
                    crop = cropDisplayName,
                    rank = 1,
                ),
                topPredictions = emptyList(),
                cropCompatiblePredictions = emptyList(),
                status = DiagnosticStatus.CONFIDENT,
                message = "No visible disease detected. Continue regular monitoring and good crop management.",
                selectedCrop = cropDisplayName,
                confidenceBand = ConfidenceBand.HIGH,
                isPrototypeFallback = false,
            )

            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.HEALTHY_CROP,
                    diagnosticResult = healthyDiag,
                    source = DiagnosisSource.HEALTHY_ASSESSMENT,
                    isHealthy = true,
                )
            )
        }

        // Step 3: Real TFLite Check
        if (mockRealDiagnosticResult != null &&
            mockRealDiagnosticResult.status != DiagnosticStatus.UNKNOWN_OR_UNCERTAIN &&
            mockRealDiagnosticResult.status != DiagnosticStatus.PROTOTYPE_FALLBACK &&
            mockRealDiagnosticResult.primaryPrediction != null &&
            mockRealDiagnosticResult.primaryPrediction.confidence >= 0.35f
        ) {
            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.DISEASE_SUSPECTED,
                    diagnosticResult = mockRealDiagnosticResult,
                    source = DiagnosisSource.REAL_TFLITE,
                )
            )
        }

        // Step 4: SIH Demo Fallback Layer
        return executeFallback(pixels, normCrop, cropDisplayName, assessment)
    }

    private fun executeFallback(
        pixels: IntArray,
        normCrop: String?,
        cropDisplayName: String,
        assessment: ImageAssessment,
    ): Result<DiagnosisEngineResult> {
        val priorityCropList = demoCatalogByCrop[normCrop] ?: demoCatalogByCrop["tomato"]!!

        val fingerprint = computePixelFingerprint(pixels)
        val index = abs(fingerprint) % priorityCropList.size
        val chosen = priorityCropList[index]

        val fallbackDiag = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = chosen.diseaseName,
                confidence = 0.0f, // Honest prototype representation
                classId = chosen.diseaseId,
                crop = chosen.cropName,
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.PROTOTYPE_FALLBACK,
            message = "Crop-specific possible diagnosis for agricultural guidance.",
            selectedCrop = chosen.cropName,
            confidenceBand = ConfidenceBand.PROTOTYPE_FALLBACK,
            isPrototypeFallback = true,
            fallbackNotice = "Prototype Guidance",
        )

        return Result.success(
            DiagnosisEngineResult(
                assessment = assessment,
                diagnosticResult = fallbackDiag,
                source = DiagnosisSource.DEMO_PROTOTYPE,
            )
        )
    }

    private fun normalizeCrop(crop: String): String {
        val lower = crop.trim().lowercase().replace("_", " ")
        return when (lower) {
            "chili", "bell pepper", "pepper" -> "chilli"
            "sugar cane" -> "sugarcane"
            "paddy" -> "rice"
            else -> lower
        }
    }

    private fun computePixelFingerprint(pixels: IntArray): Int {
        var hash = 17
        for (i in pixels.indices step 4) {
            hash = 31 * hash + (pixels[i] and 0xFFFFFF)
        }
        return hash
    }

    data class DemoDiseaseEntry(
        val diseaseId: Int,
        val diseaseName: String,
        val cropName: String,
    )
}
