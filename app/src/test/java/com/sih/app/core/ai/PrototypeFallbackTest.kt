package com.sih.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PrototypeFallbackTest {

    private lateinit var advisoryRepository: AdvisoryRepository
    private lateinit var advisoryJson: String
    private lateinit var prototypeCatalogJson: String
    private lateinit var demoEngine: DemoPrototypeDiagnosisEngine

    // Helper functions creating pure RGB int arrays (0x00RRGGBB)
    private fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun createGreenPlantPixels(size: Int = 64): IntArray {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            pixels[i] = rgb(40, 180, 50) // Vibrant uniform green leaf
        }
        return pixels
    }

    private fun createDiseasedPlantPixels(size: Int = 64): IntArray {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            if (i % 5 == 0) {
                pixels[i] = rgb(140, 70, 20) // Necrotic brown spots
            } else if (i % 4 == 0) {
                pixels[i] = rgb(210, 190, 30) // Chlorotic yellow halo
            } else {
                pixels[i] = rgb(50, 160, 40) // Green leaf
            }
        }
        return pixels
    }

    private fun createLaptopGreyPixels(size: Int = 64): IntArray {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            pixels[i] = rgb(45, 48, 52) // Metallic grey laptop keyboard
        }
        return pixels
    }

    private fun createPhoneScreenPixels(size: Int = 64): IntArray {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            pixels[i] = rgb(15, 30, 85) // Blue-lit screen glow
        }
        return pixels
    }

    private fun createRandomObjectPixels(size: Int = 64): IntArray {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            pixels[i] = rgb(220, 220, 225) // White paper / wall document
        }
        return pixels
    }

    @Before
    fun setup() {
        val advisoryFile = sequenceOf(
            File("src/main/assets/disease_advisories.json"),
            File("app/src/main/assets/disease_advisories.json"),
            File("D:/SIH/app/src/main/assets/disease_advisories.json"),
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("disease_advisories.json not found")

        val protoCatalogFile = sequenceOf(
            File("src/main/assets/prototype_fallback_catalog.json"),
            File("app/src/main/assets/prototype_fallback_catalog.json"),
            File("D:/SIH/app/src/main/assets/prototype_fallback_catalog.json"),
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("prototype_fallback_catalog.json not found")

        advisoryJson = advisoryFile.readText()
        prototypeCatalogJson = protoCatalogFile.readText()

        advisoryRepository = AdvisoryRepository(
            initialJsonString = advisoryJson,
            initialPrototypeCatalogJson = prototypeCatalogJson,
        )

        demoEngine = DemoPrototypeDiagnosisEngine(advisoryRepository)
    }

    // 1. Laptop -> IRRELEVANT_IMAGE
    @Test
    fun test1_LaptopImageReturnsIrrelevant() {
        val pixels = createLaptopGreyPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()

        assertTrue("Must be marked irrelevant", result.isIrrelevant)
        assertEquals(ImageAssessment.IRRELEVANT_IMAGE, result.assessment)
        assertEquals(DiagnosisSource.IRRELEVANT_IMAGE, result.source)
        assertNull("Irrelevant image must have no diagnostic disease result", result.diagnosticResult)
    }

    // 2. Phone -> IRRELEVANT_IMAGE
    @Test
    fun test2_PhoneScreenImageReturnsIrrelevant() {
        val pixels = createPhoneScreenPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Chilli").getOrThrow()

        assertTrue("Phone screen must be irrelevant", result.isIrrelevant)
        assertEquals(ImageAssessment.IRRELEVANT_IMAGE, result.assessment)
        assertEquals(DiagnosisSource.IRRELEVANT_IMAGE, result.source)
    }

    // 3. Random unrelated object -> IRRELEVANT_IMAGE
    @Test
    fun test3_RandomUnrelatedObjectReturnsIrrelevant() {
        val pixels = createRandomObjectPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()

        assertTrue("Unrelated object must be irrelevant", result.isIrrelevant)
        assertEquals(ImageAssessment.IRRELEVANT_IMAGE, result.assessment)
    }

    // 4. Healthy Tomato -> HEALTHY_ASSESSMENT
    @Test
    fun test4_HealthyTomatoReturnsHealthyAssessment() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()

        assertTrue("Must be healthy", result.isHealthy)
        assertEquals(ImageAssessment.HEALTHY_CROP, result.assessment)
        assertEquals(DiagnosisSource.HEALTHY_ASSESSMENT, result.source)
        assertNotNull(result.diagnosticResult)
        assertTrue(result.diagnosticResult!!.primaryPrediction?.diseaseName?.contains("Healthy") == true)
    }

    // 5. Healthy Chilli -> HEALTHY_ASSESSMENT
    @Test
    fun test5_HealthyChilliReturnsHealthyAssessment() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Chilli").getOrThrow()

        assertTrue(result.isHealthy)
        assertEquals(DiagnosisSource.HEALTHY_ASSESSMENT, result.source)
        assertEquals("Healthy Chilli", result.diagnosticResult!!.primaryPrediction?.diseaseName)
    }

    // 6. Healthy Rice -> HEALTHY_ASSESSMENT
    @Test
    fun test6_HealthyRiceReturnsHealthyAssessment() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()

        assertTrue(result.isHealthy)
        assertEquals(DiagnosisSource.HEALTHY_ASSESSMENT, result.source)
        assertEquals("Healthy Rice", result.diagnosticResult!!.primaryPrediction?.diseaseName)
    }

    // 7. Healthy Wheat -> HEALTHY_ASSESSMENT
    @Test
    fun test7_HealthyWheatReturnsHealthyAssessment() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Wheat").getOrThrow()

        assertTrue(result.isHealthy)
        assertEquals(DiagnosisSource.HEALTHY_ASSESSMENT, result.source)
        assertEquals("Healthy Wheat", result.diagnosticResult!!.primaryPrediction?.diseaseName)
    }

    // 8. Healthy Sugarcane -> HEALTHY_ASSESSMENT
    @Test
    fun test8_HealthySugarcaneReturnsHealthyAssessment() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Sugarcane").getOrThrow()

        assertTrue(result.isHealthy)
        assertEquals(DiagnosisSource.HEALTHY_ASSESSMENT, result.source)
        assertEquals("Healthy Sugarcane", result.diagnosticResult!!.primaryPrediction?.diseaseName)
    }

    // 9. Diseased Tomato + low TFLite confidence -> DEMO_PROTOTYPE
    @Test
    fun test9_DiseasedTomatoLowConfidenceReturnsDemoPrototype() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()

        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        val disease = result.diagnosticResult!!.primaryPrediction?.diseaseName
        assertNotNull(disease)
        assertTrue(disease!!.startsWith("Tomato "))
    }

    // 10. Diseased Chilli + low TFLite confidence -> DEMO_PROTOTYPE
    @Test
    fun test10_DiseasedChilliLowConfidenceReturnsDemoPrototype() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Chilli").getOrThrow()

        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        val disease = result.diagnosticResult!!.primaryPrediction?.diseaseName
        assertNotNull(disease)
        assertTrue(disease!!.startsWith("Chilli "))
    }

    // 11. Diseased Rice + low TFLite confidence -> DEMO_PROTOTYPE
    @Test
    fun test11_DiseasedRiceLowConfidenceReturnsDemoPrototype() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()

        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        val disease = result.diagnosticResult!!.primaryPrediction?.diseaseName
        assertNotNull(disease)
        assertTrue(disease!!.startsWith("Rice "))
    }

    // 12. Diseased Wheat + low TFLite confidence -> DEMO_PROTOTYPE
    @Test
    fun test12_DiseasedWheatLowConfidenceReturnsDemoPrototype() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Wheat").getOrThrow()

        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        val disease = result.diagnosticResult!!.primaryPrediction?.diseaseName
        assertNotNull(disease)
        assertTrue(disease!!.startsWith("Wheat "))
    }

    // 13. Diseased Sugarcane + low TFLite confidence -> DEMO_PROTOTYPE
    @Test
    fun test13_DiseasedSugarcaneLowConfidenceReturnsDemoPrototype() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Sugarcane").getOrThrow()

        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        val disease = result.diagnosticResult!!.primaryPrediction?.diseaseName
        assertNotNull(disease)
        assertTrue(disease!!.startsWith("Sugarcane "))
    }

    // 14. Same image + same crop -> same prototype disease
    @Test
    fun test14_SameImageSameCropReturnsDeterministicSameDisease() {
        val pixels1 = createDiseasedPlantPixels()
        val res1 = demoEngine.diagnosePixels(pixels1, 64, 64, "Tomato").getOrThrow()

        val pixels2 = createDiseasedPlantPixels()
        val res2 = demoEngine.diagnosePixels(pixels2, 64, 64, "Tomato").getOrThrow()

        assertEquals(res1.diagnosticResult!!.primaryPrediction!!.diseaseName, res2.diagnosticResult!!.primaryPrediction!!.diseaseName)
        assertEquals(res1.diagnosticResult!!.primaryPrediction!!.classId, res2.diagnosticResult!!.primaryPrediction!!.classId)
    }

    // 15. Different crop -> never returns another crop's disease
    @Test
    fun test15_DifferentCropNeverReturnsAnotherCropDisease() {
        val pixels = createDiseasedPlantPixels()

        val tomatoRes = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()
        val tomatoName = tomatoRes.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertFalse(tomatoName.contains("Rice") || tomatoName.contains("Wheat") || tomatoName.contains("Sugarcane") || tomatoName.contains("Chilli"))

        val riceRes = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()
        val riceName = riceRes.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertFalse(riceName.contains("Tomato") || riceName.contains("Wheat") || riceName.contains("Sugarcane") || riceName.contains("Chilli"))
    }

    // 16. Tomato fallback -> ONLY Tomato diseases
    @Test
    fun test16_TomatoFallbackOnlyTomatoDiseases() {
        val allowedTomato = setOf(
            "Tomato Early Blight",
            "Tomato Late Blight",
            "Tomato Leaf Curl",
            "Tomato Bacterial Spot",
            "Tomato Fusarium Wilt",
        )
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()
        val diseaseName = result.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertTrue("Disease '$diseaseName' must be in $allowedTomato", allowedTomato.contains(diseaseName))
    }

    // 17. Chilli fallback -> ONLY Chilli diseases
    @Test
    fun test17_ChilliFallbackOnlyChilliDiseases() {
        val allowedChilli = setOf(
            "Chilli Anthracnose / Fruit Rot",
            "Chilli Leaf Curl",
            "Chilli Powdery Mildew",
            "Chilli Bacterial Leaf Spot",
            "Chilli Fusarium Wilt",
        )
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Chilli").getOrThrow()
        val diseaseName = result.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertTrue("Disease '$diseaseName' must be in $allowedChilli", allowedChilli.contains(diseaseName))
    }

    // 18. Rice fallback -> ONLY Rice diseases
    @Test
    fun test18_RiceFallbackOnlyRiceDiseases() {
        val allowedRice = setOf(
            "Rice Blast",
            "Rice Bacterial Leaf Blight",
            "Rice Brown Spot",
            "Rice Sheath Blight",
            "Rice Tungro",
        )
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()
        val diseaseName = result.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertTrue("Disease '$diseaseName' must be in $allowedRice", allowedRice.contains(diseaseName))
    }

    // 19. Wheat fallback -> ONLY Wheat diseases
    @Test
    fun test19_WheatFallbackOnlyWheatDiseases() {
        val allowedWheat = setOf(
            "Wheat Leaf / Brown Rust",
            "Wheat Stripe / Yellow Rust",
            "Wheat Stem Rust",
            "Wheat Loose Smut",
            "Wheat Karnal Bunt",
        )
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Wheat").getOrThrow()
        val diseaseName = result.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertTrue("Disease '$diseaseName' must be in $allowedWheat", allowedWheat.contains(diseaseName))
    }

    // 20. Sugarcane fallback -> ONLY Sugarcane diseases
    @Test
    fun test20_SugarcaneFallbackOnlySugarcaneDiseases() {
        val allowedSugarcane = setOf(
            "Sugarcane Red Rot",
            "Sugarcane Smut",
            "Sugarcane Wilt",
            "Sugarcane Grassy Shoot Disease",
            "Sugarcane Pokkah Boeng",
        )
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Sugarcane").getOrThrow()
        val diseaseName = result.diagnosticResult!!.primaryPrediction!!.diseaseName
        assertTrue("Disease '$diseaseName' must be in $allowedSugarcane", allowedSugarcane.contains(diseaseName))
    }

    // 21. Existing real TFLite confident result -> REAL_TFLITE
    @Test
    fun test21_ConfidentRealTfliteResultReturnsRealTflite() {
        val pixels = createDiseasedPlantPixels()
        val mockRealDiag = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "Tomato Early Blight",
                confidence = 0.88f,
                classId = 54,
                crop = "Tomato",
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.CONFIDENT,
            message = "High confidence diagnosis.",
            selectedCrop = "Tomato",
            confidenceBand = ConfidenceBand.HIGH,
            isPrototypeFallback = false,
        )

        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato", mockRealDiagnosticResult = mockRealDiag).getOrThrow()
        assertEquals(DiagnosisSource.REAL_TFLITE, result.source)
        assertFalse(result.diagnosticResult!!.isPrototypeFallback)
        assertEquals(0.88f, result.diagnosticResult!!.primaryPrediction!!.confidence, 0.001f)
    }

    // 22. Existing crop-aware gating remains intact
    @Test
    fun test22_ExistingCropAwareGatingRemainsIntact() {
        val mockIncompatibleDiag = DiagnosticResult(
            primaryPrediction = null,
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.UNKNOWN_OR_UNCERTAIN,
            message = "Top prediction belongs to another crop.",
            selectedCrop = "Tomato",
            confidenceBand = ConfidenceBand.UNCERTAIN,
            isPrototypeFallback = false,
        )

        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato", mockRealDiagnosticResult = mockIncompatibleDiag).getOrThrow()
        // Incompatible TFLite prediction falls back to prototype demo diagnosis
        assertEquals(DiagnosisSource.DEMO_PROTOTYPE, result.source)
        assertTrue(result.diagnosticResult!!.isPrototypeFallback)
        assertTrue(result.diagnosticResult!!.primaryPrediction!!.crop.equals("Tomato", ignoreCase = true))
    }

    // 23. All 25 prototype diseases have complete 7-section advisories
    @Test
    fun test23_All25DiseasesHaveComplete7SectionAdvisories() {
        val list = PrototypeCatalogParser.parse(prototypeCatalogJson)
        assertEquals(25, list.size)

        for (advisory in list) {
            assertTrue("Overview valid for ${advisory.diseaseName}", advisory.overview.length >= 30)
            assertTrue("Symptoms valid for ${advisory.diseaseName}", advisory.symptoms.size >= 3)
            assertTrue("Actions valid for ${advisory.diseaseName}", advisory.immediateActions.size >= 2)
            assertTrue("Prevention valid for ${advisory.diseaseName}", advisory.prevention.size >= 2)
            assertTrue("Monitoring valid for ${advisory.diseaseName}", advisory.monitoring.size >= 2)
            assertTrue("Escalation valid for ${advisory.diseaseName}", advisory.expertEscalation.isNotBlank())
            assertTrue("Safety note valid for ${advisory.diseaseName}", advisory.safetyNote.isNotBlank())
        }
    }

    // 24. Empty/invalid image rejection
    @Test
    fun test24_EmptyOrInvalidImageRejection() {
        val result = demoEngine.diagnosePixels(IntArray(0), 0, 0, "Tomato").getOrThrow()
        assertTrue("Empty image must be irrelevant", result.isIrrelevant)
        assertEquals(ImageAssessment.IRRELEVANT_IMAGE, result.assessment)
    }

    // 25. Advisory integrity for Fallback (No verification banner, no uncertainty notice)
    @Test
    fun test25_AdvisoryIntegrityForFallback() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()
        val advResult = advisoryRepository.getAdvisoryForDiagnosticResult(result.diagnosticResult!!)

        assertTrue("Advisory must be available", advResult is AdvisoryResult.Available)
        val available = advResult as AdvisoryResult.Available
        assertTrue(available.presentation.isPrototypeFallback)
        assertNull("Prototype fallback must NOT display uncertainty notice message", available.presentation.noticeMessage)
        assertEquals("Prototype Guidance", available.presentation.fallbackNotice)
        assertFalse("Prototype fallback must NOT contain 'Verification Recommended'", available.presentation.fallbackNotice?.contains("Verification Recommended") == true)
    }

    // 26. Healthy crop provides no immediate action monitoring
    @Test
    fun test26_HealthyCropProvidesNoImmediateActionMonitoring() {
        val pixels = createGreenPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Tomato").getOrThrow()
        val advResult = advisoryRepository.getAdvisoryForDiagnosticResult(result.diagnosticResult!!)

        assertTrue(advResult is AdvisoryResult.Healthy)
        val healthy = advResult as AdvisoryResult.Healthy
        assertTrue(healthy.monitoringGuidance.isNotEmpty())
    }

    // 27. Prototype diagnosis does NOT contain 'Verification Recommended'
    @Test
    fun test27_PrototypeDiagnosisDoesNotContainVerificationRecommended() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Rice").getOrThrow()
        val advResult = advisoryRepository.getAdvisoryForDiagnosticResult(result.diagnosticResult!!)

        assertTrue(advResult is AdvisoryResult.Available)
        val pres = (advResult as AdvisoryResult.Available).presentation
        assertEquals("Prototype Guidance", pres.fallbackNotice)
        assertFalse(pres.fallbackNotice?.contains("Verification Recommended") == true)
        assertEquals("Possible Diagnosis", pres.title)
    }

    // 28. Prototype diagnosis does NOT expose internal uncertainty explanation
    @Test
    fun test28_PrototypeDiagnosisDoesNotExposeUncertaintyExplanation() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Wheat").getOrThrow()
        val advResult = advisoryRepository.getAdvisoryForDiagnosticResult(result.diagnosticResult!!)

        assertTrue(advResult is AdvisoryResult.Available)
        val pres = (advResult as AdvisoryResult.Available).presentation
        assertNull(pres.noticeMessage)
    }

    // 29. Prototype diagnosis shows Prototype Guidance badge and complete advisory
    @Test
    fun test29_PrototypeDiagnosisShowsPrototypeGuidanceAndCompleteAdvisory() {
        val pixels = createDiseasedPlantPixels()
        val result = demoEngine.diagnosePixels(pixels, 64, 64, "Chilli").getOrThrow()
        val advResult = advisoryRepository.getAdvisoryForDiagnosticResult(result.diagnosticResult!!)

        assertTrue(advResult is AdvisoryResult.Available)
        val pres = (advResult as AdvisoryResult.Available).presentation
        assertTrue(pres.isPrototypeFallback)
        assertEquals("Prototype Guidance", pres.fallbackNotice)
        assertTrue(pres.symptoms.isNotEmpty())
        assertTrue(pres.immediateActions.isNotEmpty())
        assertTrue(pres.prevention.isNotEmpty())
        assertTrue(pres.monitoring.isNotEmpty())
        assertTrue(pres.expertEscalation?.isNotBlank() == true)
        assertTrue(pres.safetyNote?.isNotBlank() == true)
    }
}
