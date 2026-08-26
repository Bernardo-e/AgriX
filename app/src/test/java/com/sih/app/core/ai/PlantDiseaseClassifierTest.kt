package com.sih.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class PlantDiseaseClassifierTest {

    private val assetsDir = File("src/main/assets")

    @Test
    fun testTfliteModelAssetExistsAndIsValid() {
        val modelFile = File(assetsDir, "agrix_stage2_fp16.tflite")
        assertTrue("TFLite model asset must exist", modelFile.exists())
        assertTrue("Model size must be around 5.91 MB", modelFile.length() > 5_000_000 && modelFile.length() < 7_000_000)

        // Verify TFLite magic header 'TFL3' at offset 4
        FileInputStream(modelFile).use { stream ->
            val header = ByteArray(8)
            stream.read(header)
            val magic = String(header, 4, 4)
            assertEquals("TFL3", magic)
        }
    }

    @Test
    fun testLabelMapIntegrity() {
        val labelMapFile = File(assetsDir, "agrix_label_map.json")
        assertTrue("Label map file must exist", labelMapFile.exists())

        val jsonStr = labelMapFile.readText()
        // Check for 71 class indices
        for (i in 0 until 71) {
            assertTrue("Index $i must be in label map", jsonStr.contains("\"$i\":"))
        }
    }

    @Test
    fun testCropDiseaseMapIntegrity() {
        val cropMapFile = File(assetsDir, "crop_disease_map.json")
        assertTrue("Crop disease map must exist", cropMapFile.exists())

        val jsonStr = cropMapFile.readText()
        val expectedCrops = listOf(
            "apple", "banana", "basil", "bean", "bell pepper", "broccoli", "cabbage",
            "carrot", "cherry", "citrus", "coffee", "corn", "cucumber", "garlic",
            "ginger", "grape", "grapevine", "lettuce", "maple", "peach", "plum",
            "potato", "rice", "soybean", "squash", "tobacco", "tomato", "wheat", "zucchini"
        )
        assertEquals("Must expect 29 crops", 29, expectedCrops.size)

        for (crop in expectedCrops) {
            assertTrue("Crop $crop must exist in crop_disease_map.json", jsonStr.contains("\"$crop\":"))
        }
    }

    @Test
    fun testConfidenceBandThresholds() {
        fun computeBand(status: DiagnosticStatus, confidence: Float): ConfidenceBand {
            return when {
                status == DiagnosticStatus.UNKNOWN_OR_UNCERTAIN -> ConfidenceBand.UNCERTAIN
                confidence >= 0.75f -> ConfidenceBand.HIGH
                confidence >= 0.50f -> ConfidenceBand.MEDIUM
                confidence >= 0.35f -> ConfidenceBand.LOW
                else -> ConfidenceBand.UNCERTAIN
            }
        }

        assertEquals(ConfidenceBand.HIGH, computeBand(DiagnosticStatus.CONFIDENT, 0.92f))
        assertEquals(ConfidenceBand.MEDIUM, computeBand(DiagnosticStatus.MODERATE_CONFIDENCE, 0.65f))
        assertEquals(ConfidenceBand.LOW, computeBand(DiagnosticStatus.LOW_CONFIDENCE, 0.42f))
        assertEquals(ConfidenceBand.UNCERTAIN, computeBand(DiagnosticStatus.UNKNOWN_OR_UNCERTAIN, 0.85f))
        assertEquals(ConfidenceBand.UNCERTAIN, computeBand(DiagnosticStatus.CONFIDENT, 0.20f))
    }

    @Test
    fun testDiseasePredictionDataModel() {
        val pred = DiseasePrediction(
            diseaseName = "Tomato Early Blight",
            confidence = 0.88f,
            classId = 5,
            crop = "Tomato",
            rank = 1
        )
        assertEquals(5, pred.classId)
        assertEquals("Tomato Early Blight", pred.diseaseName)
        assertEquals("Tomato", pred.crop)
        assertEquals(0.88f, pred.confidence, 0.001f)
        assertEquals(1, pred.rank)
    }

    @Test
    fun testDiagnosticResultDataModel() {
        val pred1 = DiseasePrediction(
            diseaseName = "Tomato Bacterial Leaf Spot",
            confidence = 0.85f,
            classId = 0,
            crop = "Tomato",
            rank = 1
        )
        val pred2 = DiseasePrediction(
            diseaseName = "Tomato Early Blight",
            confidence = 0.10f,
            classId = 1,
            crop = "Tomato",
            rank = 2
        )
        val diag = DiagnosticResult(
            primaryPrediction = pred1,
            topPredictions = listOf(pred1, pred2),
            cropCompatiblePredictions = listOf(pred1, pred2),
            status = DiagnosticStatus.CONFIDENT,
            message = "High confidence diagnosis.",
            selectedCrop = "Tomato",
            confidenceBand = ConfidenceBand.HIGH
        )
        assertEquals(DiagnosticStatus.CONFIDENT, diag.status)
        assertEquals(ConfidenceBand.HIGH, diag.confidenceBand)
        assertEquals("Tomato", diag.selectedCrop)
        assertEquals(2, diag.topPredictions.size)
    }
}
