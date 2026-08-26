package com.sih.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AdvisoryRepositoryTest {

    private lateinit var repository: AdvisoryRepository
    private lateinit var jsonContent: String

    @Before
    fun setup() {
        val assetFile = sequenceOf(
            File("src/main/assets/disease_advisories.json"),
            File("app/src/main/assets/disease_advisories.json"),
            File("D:/SIH/app/src/main/assets/disease_advisories.json"),
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("disease_advisories.json asset file not found.")

        jsonContent = assetFile.readText()
        repository = AdvisoryRepository(initialJsonString = jsonContent)
    }

    // A. Advisory JSON loads successfully and contains all 71 entries
    @Test
    fun testAdvisoryJsonLoadsAll71Entries() {
        val all = repository.getAllAdvisories()
        assertEquals(71, all.size)
    }

    // B. Correct disease_id + crop_id returns advisory
    @Test
    fun testValidCropAndDiseaseReturnsAdvisory() {
        val tomatoSpot = repository.getAdvisory(cropId = "tomato", diseaseId = 53)
        assertNotNull("Tomato bacterial spot advisory must exist", tomatoSpot)
        assertEquals("tomato", tomatoSpot?.cropId)
        assertEquals(53, tomatoSpot?.diseaseId)
        assertEquals("tomato bacterial leaf spot", tomatoSpot?.diseaseName)
        assertTrue("Overview must not be blank", tomatoSpot?.overview?.isNotBlank() == true)
        assertTrue("Symptoms must not be empty", tomatoSpot?.symptoms?.isNotEmpty() == true)
        assertTrue("Immediate actions must not be empty", tomatoSpot?.immediateActions?.isNotEmpty() == true)
        assertTrue("Prevention must not be empty", tomatoSpot?.prevention?.isNotEmpty() == true)
        assertTrue("Monitoring must not be empty", tomatoSpot?.monitoring?.isNotEmpty() == true)
        assertTrue("Expert escalation must not be blank", tomatoSpot?.expertEscalation?.isNotBlank() == true)
    }

    // C. Wrong crop + disease combination does not return incorrect advisory
    @Test
    fun testMismatchedCropAndDiseaseReturnsNull() {
        // Disease 53 is tomato bacterial leaf spot, passing 'apple' as cropId must return null
        val mismatch = repository.getAdvisory(cropId = "apple", diseaseId = 53)
        assertNull("Mismatched crop should not return advisory", mismatch)
    }

    // D. Missing / out of range disease does not crash
    @Test
    fun testMissingAdvisoryDoesNotCrash() {
        val invalidNegative = repository.getAdvisory(cropId = "tomato", diseaseId = -1)
        assertNull(invalidNegative)

        val invalidHigh = repository.getAdvisory(cropId = "tomato", diseaseId = 999)
        assertNull(invalidHigh)

        val unknownCrop = repository.getAdvisory(cropId = "dragonfruit", diseaseId = 1)
        assertNull(unknownCrop)
    }

    // E. CONFIDENT result displays full guidance
    @Test
    fun testConfidentDiagnosticResultProducesFullGuidance() {
        val diagResult = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "tomato bacterial leaf spot",
                confidence = 0.88f,
                classId = 53,
                crop = "Tomato",
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.CONFIDENT,
            message = "High confidence identification",
            selectedCrop = "Tomato",
            confidenceBand = ConfidenceBand.HIGH,
        )

        val result = repository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Result must be Available", result is AdvisoryResult.Available)

        val available = result as AdvisoryResult.Available
        val presentation = available.presentation
        assertEquals(AdvisoryConfidenceLevel.CONFIDENT, presentation.confidenceLevel)
        assertEquals("AI-Assisted Diagnosis", presentation.title)
        assertNull("Confident result should not show warning banner", presentation.noticeMessage)
        assertTrue(presentation.isActionable)
        assertTrue(presentation.immediateActions.isNotEmpty())
        assertTrue(presentation.symptoms.isNotEmpty())
    }

    // F. MODERATE_CONFIDENCE displays guidance with verification warning
    @Test
    fun testModerateConfidenceDiagnosticResultProducesVerificationNotice() {
        val diagResult = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "apple black rot",
                confidence = 0.62f,
                classId = 0,
                crop = "Apple",
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.MODERATE_CONFIDENCE,
            message = "Moderate confidence identification",
            selectedCrop = "Apple",
            confidenceBand = ConfidenceBand.MEDIUM,
        )

        val result = repository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Result must be Available", result is AdvisoryResult.Available)

        val available = result as AdvisoryResult.Available
        val presentation = available.presentation
        assertEquals(AdvisoryConfidenceLevel.MODERATE, presentation.confidenceLevel)
        assertTrue(presentation.title.contains("Likely Diagnosis"))
        assertNotNull("Moderate confidence MUST have verification notice", presentation.noticeMessage)
        assertTrue(presentation.isActionable)
        assertTrue(presentation.immediateActions.isNotEmpty())
    }

    // G. LOW_CONFIDENCE displays verification-focused guidance without immediate intervention actions
    @Test
    fun testLowConfidenceDiagnosticResultWithholdsInterventionActions() {
        val diagResult = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "rice blast",
                confidence = 0.42f,
                classId = 43,
                crop = "Rice",
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.LOW_CONFIDENCE,
            message = "Low confidence identification",
            selectedCrop = "Rice",
            confidenceBand = ConfidenceBand.LOW,
        )

        val result = repository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Result must be Available", result is AdvisoryResult.Available)

        val available = result as AdvisoryResult.Available
        val presentation = available.presentation
        assertEquals(AdvisoryConfidenceLevel.LOW, presentation.confidenceLevel)
        assertTrue(presentation.title.contains("Possible Diagnosis"))
        assertNotNull("Low confidence MUST have notice", presentation.noticeMessage)
        assertFalse("Low confidence must not be marked fully actionable", presentation.isActionable)
        assertTrue("Low confidence MUST withhold immediate disease-specific intervention actions", presentation.immediateActions.isEmpty())
        assertTrue("Low confidence must still provide symptom verification cues", presentation.symptoms.isNotEmpty())
    }

    // H. UNKNOWN_OR_UNCERTAIN does NOT display disease-specific intervention guidance
    @Test
    fun testUncertainDiagnosticResultProvidesGeneralUncertaintyGuidance() {
        val diagResult = DiagnosticResult(
            primaryPrediction = null,
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.UNKNOWN_OR_UNCERTAIN,
            message = "Unable to identify plant disease.",
            selectedCrop = null,
            confidenceBand = ConfidenceBand.UNCERTAIN,
        )

        val result = repository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Result must be Uncertain", result is AdvisoryResult.Uncertain)

        val uncertain = result as AdvisoryResult.Uncertain
        assertTrue(uncertain.message.contains("could not confidently identify"))
        assertTrue(uncertain.generalGuidance.isNotEmpty())
        assertTrue("Must include retake photo advice", uncertain.generalGuidance.any { it.contains("photos") })
        assertNotNull(uncertain.safetyNote)
    }

    // I. Unavailable advisory when crop/disease is unsupported in catalog
    @Test
    fun testUnavailableAdvisoryHandling() {
        val diagResult = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "fictional disease",
                confidence = 0.88f,
                classId = 999,
                crop = "UnknownCrop",
                rank = 1,
            ),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.CONFIDENT,
            message = "Confidence high",
            selectedCrop = "UnknownCrop",
            confidenceBand = ConfidenceBand.HIGH,
        )

        val result = repository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Result must be Unavailable", result is AdvisoryResult.Unavailable)
    }
}
