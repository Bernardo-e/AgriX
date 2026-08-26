package com.sih.app.core.ai

import android.content.Context
import android.util.Log

private const val TAG = "AdvisoryRepository"
private const val ADVISORY_FILENAME = "disease_advisories.json"
private const val PROTOTYPE_CATALOG_FILENAME = "prototype_fallback_catalog.json"

class AdvisoryRepository(
    private val context: Context? = null,
    initialJsonString: String? = null,
    initialPrototypeCatalogJson: String? = null,
) {
    // Lookup key: "${cropId.lowercase().trim()}_${diseaseId}"
    private val advisoryMap: MutableMap<String, Advisory> = mutableMapOf()
    private val prototypeFallbackMap: MutableMap<String, Advisory> = mutableMapOf()
    private var isInitialized = false
    private val lock = Any()

    init {
        if (!initialJsonString.isNullOrBlank()) {
            loadFromJsonString(initialJsonString)
        }
        if (!initialPrototypeCatalogJson.isNullOrBlank()) {
            loadPrototypeCatalogFromJson(initialPrototypeCatalogJson)
        } else if (context != null) {
            loadFromAssets()
        }
    }

    private fun loadFromAssets() {
        if (context == null) return
        synchronized(lock) {
            try {
                val jsonString = context.assets.open(ADVISORY_FILENAME).bufferedReader().use { it.readText() }
                loadFromJsonString(jsonString)
            } catch (e: Throwable) {
                logW("Failed to load advisory assets: ${e.message}")
            }

            try {
                val protoJson = context.assets.open(PROTOTYPE_CATALOG_FILENAME).bufferedReader().use { it.readText() }
                loadPrototypeCatalogFromJson(protoJson)
            } catch (e: Throwable) {
                logW("Failed to load prototype fallback catalog: ${e.message}")
            }
        }
    }

    fun loadFromJsonString(jsonString: String) {
        synchronized(lock) {
            try {
                val list = AdvisoryJsonParser.parse(jsonString)
                for (advisory in list) {
                    val key = makeKey(advisory.cropId, advisory.diseaseId)
                    advisoryMap[key] = advisory
                }
                isInitialized = true
                logD("Loaded ${advisoryMap.size} offline advisories successfully.")
            } catch (e: Throwable) {
                logW("Error parsing advisory JSON: ${e.message}")
            }
        }
    }

    fun loadPrototypeCatalogFromJson(jsonString: String) {
        synchronized(lock) {
            try {
                val list = PrototypeCatalogParser.parse(jsonString)
                for (advisory in list) {
                    val key = makeKey(advisory.cropId, advisory.diseaseId)
                    advisoryMap[key] = advisory
                    if (advisory.isPrototypeFallback) {
                        prototypeFallbackMap[advisory.cropId.lowercase().trim()] = advisory
                    }
                }
                // Also index by crop aliases
                if (prototypeFallbackMap.containsKey("chilli")) {
                    prototypeFallbackMap["chili"] = prototypeFallbackMap["chilli"]!!
                    prototypeFallbackMap["bell pepper"] = prototypeFallbackMap["chilli"]!!
                    prototypeFallbackMap["pepper"] = prototypeFallbackMap["chilli"]!!
                }
                if (prototypeFallbackMap.containsKey("sugarcane")) {
                    prototypeFallbackMap["sugar cane"] = prototypeFallbackMap["sugarcane"]!!
                }
                if (prototypeFallbackMap.containsKey("rice")) {
                    prototypeFallbackMap["paddy"] = prototypeFallbackMap["rice"]!!
                }
                logD("Loaded ${list.size} prototype advisories across ${prototypeFallbackMap.size} fallback crops.")
            } catch (e: Throwable) {
                logW("Error parsing prototype catalog JSON: ${e.message}")
            }
        }
    }

    fun getAdvisory(cropId: String, diseaseId: Int): Advisory? {
        ensureLoaded()
        val normCrop = normalizeCropKey(cropId)
        val key = makeKey(normCrop, diseaseId)
        return advisoryMap[key] ?: advisoryMap[makeKey(cropId, diseaseId)]
    }

    fun getPrototypeFallbackAdvisory(cropId: String): Advisory? {
        ensureLoaded()
        val normCrop = normalizeCropKey(cropId)
        return prototypeFallbackMap[normCrop]
    }

    fun getAllAdvisories(): List<Advisory> {
        ensureLoaded()
        return advisoryMap.values.toList()
    }

    fun isPrototypeCrop(cropId: String?): Boolean {
        if (cropId.isNullOrBlank()) return false
        val norm = normalizeCropKey(cropId)
        return prototypeFallbackMap.containsKey(norm)
    }

    fun getAdvisoryForDiagnosticResult(diagnosticResult: DiagnosticResult): AdvisoryResult {
        ensureLoaded()

        val selectedCropNorm = diagnosticResult.selectedCrop?.let { normalizeCropKey(it) }

        // 0. HEALTHY CROP STATE
        if (diagnosticResult.primaryPrediction?.classId == -1 ||
            diagnosticResult.primaryPrediction?.diseaseName?.startsWith("Healthy", ignoreCase = true) == true
        ) {
            val cropDisplayName = diagnosticResult.selectedCrop?.replaceFirstChar { it.uppercase() } ?: "Crop"
            return AdvisoryResult.Healthy(
                cropName = cropDisplayName,
                message = "No visible disease detected. No immediate action required.",
                monitoringGuidance = listOf(
                    "Continue regular crop monitoring and good agronomic management.",
                    "Maintain balanced fertilization and appropriate irrigation schedules.",
                    "Scout lower canopy leaves weekly for early signs of pest or disease emergence.",
                    "Ensure field borders are clean and weed-free.",
                ),
            )
        }

        // 1. PROTOTYPE FALLBACK / DEMO STATE
        if (diagnosticResult.status == DiagnosticStatus.PROTOTYPE_FALLBACK ||
            (diagnosticResult.isPrototypeFallback && selectedCropNorm != null)
        ) {
            val diseaseId = diagnosticResult.primaryPrediction?.classId ?: -1
            val fallbackAdvisory = (if (selectedCropNorm != null) getAdvisory(selectedCropNorm, diseaseId) else null)
                ?: selectedCropNorm?.let { getPrototypeFallbackAdvisory(it) }

            if (fallbackAdvisory != null) {
                val cropDisplayName = diagnosticResult.selectedCrop?.replaceFirstChar { it.uppercase() } ?: fallbackAdvisory.cropId.replaceFirstChar { it.uppercase() }
                return AdvisoryResult.Available(
                    presentation = AdvisoryPresentation(
                        confidenceLevel = AdvisoryConfidenceLevel.PROTOTYPE_FALLBACK,
                        title = "Possible Diagnosis",
                        noticeMessage = null,
                        cropId = fallbackAdvisory.cropId,
                        cropName = cropDisplayName,
                        diseaseId = fallbackAdvisory.diseaseId,
                        diseaseName = fallbackAdvisory.diseaseName,
                        overview = fallbackAdvisory.overview,
                        symptoms = fallbackAdvisory.symptoms,
                        immediateActions = fallbackAdvisory.immediateActions,
                        prevention = fallbackAdvisory.prevention,
                        monitoring = fallbackAdvisory.monitoring,
                        expertEscalation = fallbackAdvisory.expertEscalation,
                        safetyNote = fallbackAdvisory.safetyNote,
                        isActionable = true,
                        isPrototypeFallback = true,
                        fallbackNotice = "Prototype Guidance",
                    ),
                )
            }
        }

        // 2. UNKNOWN_OR_UNCERTAIN or missing primary prediction for non-prototype crops
        if (diagnosticResult.status == DiagnosticStatus.UNKNOWN_OR_UNCERTAIN ||
            diagnosticResult.primaryPrediction == null ||
            diagnosticResult.confidenceBand == ConfidenceBand.UNCERTAIN
        ) {
            return AdvisoryResult.Uncertain(
                message = "AgriX could not confidently identify the disease. Capture a clearer image showing the affected plant part in good lighting.",
                generalGuidance = listOf(
                    "Do not apply chemical treatments or aggressive interventions based on uncertain identification.",
                    "Capture high-resolution, well-lit photos of both the upper and lower surfaces of affected leaves.",
                    "Check surrounding plants in the field to observe whether symptoms are localized or widespread.",
                    "Consult a local agricultural extension officer (KVK) or certified agronomist for on-site confirmation.",
                ),
                safetyNote = "Verify symptoms in the field before taking action. Consult local agricultural authorities for region-specific advice.",
            )
        }

        val primary = diagnosticResult.primaryPrediction
        val cropId = primary.crop.lowercase().trim()
        val diseaseId = primary.classId
        val advisory = getAdvisory(cropId, diseaseId)

        if (advisory == null) {
            return AdvisoryResult.Unavailable(
                reason = "Advisory information is currently unavailable for this diagnosis. Please verify with an agricultural expert.",
            )
        }

        val cropDisplayName = primary.crop.replaceFirstChar { it.uppercase() }

        // 3. CONFIDENT
        if (diagnosticResult.status == DiagnosticStatus.CONFIDENT) {
            return AdvisoryResult.Available(
                presentation = AdvisoryPresentation(
                    confidenceLevel = AdvisoryConfidenceLevel.CONFIDENT,
                    title = "AI-Assisted Diagnosis",
                    noticeMessage = null,
                    cropId = advisory.cropId,
                    cropName = cropDisplayName,
                    diseaseId = advisory.diseaseId,
                    diseaseName = advisory.diseaseName,
                    overview = advisory.overview,
                    symptoms = advisory.symptoms,
                    immediateActions = advisory.immediateActions,
                    prevention = advisory.prevention,
                    monitoring = advisory.monitoring,
                    expertEscalation = advisory.expertEscalation,
                    safetyNote = advisory.safetyNote,
                    isActionable = true,
                    isPrototypeFallback = false,
                ),
            )
        }

        // 4. MODERATE_CONFIDENCE
        if (diagnosticResult.status == DiagnosticStatus.MODERATE_CONFIDENCE) {
            return AdvisoryResult.Available(
                presentation = AdvisoryPresentation(
                    confidenceLevel = AdvisoryConfidenceLevel.MODERATE,
                    title = "Likely Diagnosis — Verification Recommended",
                    noticeMessage = "Please check visible symptoms on the plant before taking cultural actions.",
                    cropId = advisory.cropId,
                    cropName = cropDisplayName,
                    diseaseId = advisory.diseaseId,
                    diseaseName = advisory.diseaseName,
                    overview = advisory.overview,
                    symptoms = advisory.symptoms,
                    immediateActions = advisory.immediateActions,
                    prevention = advisory.prevention,
                    monitoring = advisory.monitoring,
                    expertEscalation = advisory.expertEscalation,
                    safetyNote = advisory.safetyNote,
                    isActionable = true,
                    isPrototypeFallback = false,
                ),
            )
        }

        // 5. LOW_CONFIDENCE
        return AdvisoryResult.Available(
            presentation = AdvisoryPresentation(
                confidenceLevel = AdvisoryConfidenceLevel.LOW,
                title = "Possible Diagnosis — Low Confidence",
                noticeMessage = "This is a low-confidence possibility. Focus on symptom verification and capture a clearer photo.",
                cropId = advisory.cropId,
                cropName = cropDisplayName,
                diseaseId = advisory.diseaseId,
                diseaseName = advisory.diseaseName,
                overview = advisory.overview,
                symptoms = advisory.symptoms,
                immediateActions = emptyList(), // Withhold disease-specific action as certain
                prevention = advisory.prevention,
                monitoring = advisory.monitoring,
                expertEscalation = advisory.expertEscalation,
                safetyNote = advisory.safetyNote,
                isActionable = false,
                isPrototypeFallback = false,
            ),
        )
    }

    private fun ensureLoaded() {
        if (!isInitialized && context != null) {
            loadFromAssets()
        }
    }

    private fun makeKey(cropId: String, diseaseId: Int): String {
        return "${cropId.lowercase().trim()}_$diseaseId"
    }

    private fun normalizeCropKey(crop: String): String {
        val lower = crop.trim().lowercase().replace("_", " ")
        return when (lower) {
            "chili", "bell pepper", "pepper" -> "chilli"
            "sugar cane" -> "sugarcane"
            "paddy" -> "rice"
            else -> lower
        }
    }

    private fun logD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (t: Throwable) {
            // Test fallback
        }
    }

    private fun logW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (t: Throwable) {
            // Test fallback
        }
    }
}

object AdvisoryJsonParser {
    fun parse(json: String): List<Advisory> {
        val list = mutableListOf<Advisory>()
        val startIdx = json.indexOf("\"advisories\"")
        if (startIdx == -1) return list

        var idx = startIdx
        while (true) {
            val diseaseStart = json.indexOf("\"disease_id\"", idx)
            if (diseaseStart == -1) break

            val objStart = json.lastIndexOf('{', diseaseStart)
            if (objStart == -1) break

            var depth = 0
            var objEnd = -1
            var inString = false
            var escape = false
            for (i in objStart until json.length) {
                val c = json[i]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            objEnd = i
                            break
                        }
                    }
                }
            }
            if (objEnd == -1) break

            val objStr = json.substring(objStart, objEnd + 1)
            val diseaseId = extractInt(objStr, "disease_id")
            val cropId = extractString(objStr, "crop_id")?.lowercase()?.trim()
            val diseaseName = extractString(objStr, "disease_name") ?: ""

            if (diseaseId != null && cropId != null) {
                val advisory = Advisory(
                    diseaseId = diseaseId,
                    cropId = cropId,
                    diseaseName = diseaseName,
                    overview = extractString(objStr, "overview") ?: "",
                    symptoms = extractStringArray(objStr, "symptoms"),
                    immediateActions = extractStringArray(objStr, "immediate_actions"),
                    prevention = extractStringArray(objStr, "prevention"),
                    monitoring = extractStringArray(objStr, "monitoring"),
                    expertEscalation = extractString(objStr, "expert_escalation") ?: "",
                    safetyNote = extractString(objStr, "safety_note") ?: "",
                    isPrototypeFallback = false,
                )
                list.add(advisory)
            }
            idx = objEnd + 1
        }
        return list
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        val sb = StringBuilder()
        var escape = false
        for (i in start until json.length) {
            val c = json[i]
            if (escape) {
                when (c) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(c)
                }
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                return sb.toString()
            }
            sb.append(c)
        }
        return null
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*([0-9]+)".toRegex()
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val pattern = "\"$key\"\\s*:\\s*\\[".toRegex()
        val match = pattern.find(json) ?: return emptyList()
        val start = match.range.last + 1
        val items = mutableListOf<String>()
        var inString = false
        var escape = false
        var currentItem = StringBuilder()

        for (i in start until json.length) {
            val c = json[i]
            if (escape) {
                when (c) {
                    '"' -> currentItem.append('"')
                    '\\' -> currentItem.append('\\')
                    'n' -> currentItem.append('\n')
                    'r' -> currentItem.append('\r')
                    't' -> currentItem.append('\t')
                    else -> currentItem.append(c)
                }
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                if (inString) {
                    items.add(currentItem.toString())
                    currentItem = StringBuilder()
                    inString = false
                } else {
                    inString = true
                }
                continue
            }
            if (inString) {
                currentItem.append(c)
            } else if (c == ']') {
                break
            }
        }
        return items
    }
}

object PrototypeCatalogParser {
    fun parse(json: String): List<Advisory> {
        val advisories = mutableListOf<Advisory>()
        var idx = 0
        while (true) {
            val diseaseStart = json.indexOf("\"disease_id\"", idx)
            if (diseaseStart == -1) break

            val objStart = json.lastIndexOf('{', diseaseStart)
            if (objStart == -1) break

            var depth = 0
            var objEnd = -1
            var inString = false
            var escape = false
            for (i in objStart until json.length) {
                val c = json[i]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            objEnd = i
                            break
                        }
                    }
                }
            }
            if (objEnd == -1) break

            val objStr = json.substring(objStart, objEnd + 1)
            val diseaseId = extractInt(objStr, "disease_id")
            val cropId = extractString(objStr, "crop_id")?.lowercase()?.trim()
            val diseaseName = extractString(objStr, "disease_name") ?: ""
            val isDefault = objStr.contains("\"is_default_fallback\": true")

            if (diseaseId != null && cropId != null) {
                val advisory = Advisory(
                    diseaseId = diseaseId,
                    cropId = cropId,
                    diseaseName = diseaseName,
                    overview = extractString(objStr, "overview") ?: "",
                    symptoms = extractStringArray(objStr, "symptoms"),
                    immediateActions = extractStringArray(objStr, "immediate_actions"),
                    prevention = extractStringArray(objStr, "prevention"),
                    monitoring = extractStringArray(objStr, "monitoring"),
                    expertEscalation = extractString(objStr, "expert_escalation") ?: "",
                    safetyNote = extractString(objStr, "safety_note") ?: "",
                    isPrototypeFallback = isDefault,
                )
                advisories.add(advisory)
            }
            idx = objEnd + 1
        }
        return advisories
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        val sb = StringBuilder()
        var escape = false
        for (i in start until json.length) {
            val c = json[i]
            if (escape) {
                when (c) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(c)
                }
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                return sb.toString()
            }
            sb.append(c)
        }
        return null
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*([0-9]+)".toRegex()
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val pattern = "\"$key\"\\s*:\\s*\\[".toRegex()
        val match = pattern.find(json) ?: return emptyList()
        val start = match.range.last + 1
        val items = mutableListOf<String>()
        var inString = false
        var escape = false
        var currentItem = StringBuilder()

        for (i in start until json.length) {
            val c = json[i]
            if (escape) {
                when (c) {
                    '"' -> currentItem.append('"')
                    '\\' -> currentItem.append('\\')
                    'n' -> currentItem.append('\n')
                    'r' -> currentItem.append('\r')
                    't' -> currentItem.append('\t')
                    else -> currentItem.append(c)
                }
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                if (inString) {
                    items.add(currentItem.toString())
                    currentItem = StringBuilder()
                    inString = false
                } else {
                    inString = true
                }
                continue
            }
            if (inString) {
                currentItem.append(c)
            } else if (c == ']') {
                break
            }
        }
        return items
    }
}
