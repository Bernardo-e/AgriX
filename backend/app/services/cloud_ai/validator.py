from typing import Any, Dict, Optional
from app.data.advisory_repository import (
    STANDARD_DISCLAIMER,
    advisory_repo,
)
from app.data.metadata import (
    get_crop_by_id,
    get_disease_by_id,
    validate_crop_disease,
)
from app.models.cloud_diagnosis import (
    CloudAdvisoryInfo,
    CloudDiagnosisInfo,
    CloudDiagnosisResponse,
)
from app.models.diagnosis import CropRef, DiseaseRef
from app.services.cloud_ai.provider import (
    CloudDiagnosisRawResult,
    CloudResponseInvalidException,
)


def validate_and_build_response(
    raw_result: CloudDiagnosisRawResult,
    requested_crop_id: str,
    latency_ms: int,
) -> CloudDiagnosisResponse:
    """
    Strictly validate a raw cloud AI output against AgriX 71-disease / 29-crop metadata
    and assemble a validated, safe CloudDiagnosisResponse with IPM guardrails.
    """
    clean_requested_crop = requested_crop_id.strip().lower()

    # 1. Validate Crop & Disease existence and association
    is_valid, err_msg, crop_ref, disease_ref = validate_crop_disease(
        crop_id=clean_requested_crop,
        disease_id=raw_result.disease_id,
    )

    if not is_valid or not crop_ref or not disease_ref:
        raise CloudResponseInvalidException(
            err_msg or f"Invalid crop-disease combination: requested_crop='{clean_requested_crop}', disease={raw_result.disease_id}"
        )

    # 2. Check if raw result crop matches requested crop
    if raw_result.crop_id.strip().lower() != clean_requested_crop:
        raise CloudResponseInvalidException(
            f"Cloud AI returned disease for crop '{raw_result.crop_id}', but requested crop was '{clean_requested_crop}'."
        )

    # 2. Validate and clamp confidence
    confidence = max(0.0, min(1.0, float(raw_result.confidence)))

    # Determine status band
    if confidence >= 0.75:
        diagnostic_status = "CONFIDENT"
    elif confidence >= 0.40:
        diagnostic_status = "MODERATE_CONFIDENCE"
    else:
        diagnostic_status = "LOW_CONFIDENCE"

    # 3. Retrieve verified advisory baseline from repository
    baseline_advisory = advisory_repo.get_raw_entry(disease_ref["id"])

    # Use baseline or fallback to validated raw advisory if baseline is available
    if baseline_advisory:
        severity = baseline_advisory.get("severity", "moderate")
        urgency = baseline_advisory.get("urgency", "prompt")
        overview = baseline_advisory.get("summary") or baseline_advisory.get("overview", "")
        symptoms = raw_result.symptoms or baseline_advisory.get("symptoms", [])
        immediate_actions = raw_result.immediate_actions or baseline_advisory.get("immediate_actions", [])
        prevention = raw_result.prevention or baseline_advisory.get("prevention", [])
        monitoring = raw_result.monitoring or baseline_advisory.get("monitoring", [])
        expert_escalation = raw_result.expert_escalation or baseline_advisory.get("expert_escalation", "")
        safety_note = baseline_advisory.get("disclaimer", STANDARD_DISCLAIMER)
    else:
        severity = "moderate"
        urgency = "prompt"
        overview = f"Visual symptoms of {disease_ref['name']} detected on {crop_ref['name']} foliage."
        symptoms = raw_result.symptoms or [f"Observable lesions and symptoms matching {disease_ref['name']}."]
        immediate_actions = raw_result.immediate_actions or ["Isolate affected plants and practice good field hygiene."]
        prevention = raw_result.prevention or ["Ensure proper crop spacing and avoid overhead irrigation."]
        monitoring = raw_result.monitoring or ["Monitor crop daily for symptom progression."]
        expert_escalation = raw_result.expert_escalation or "Consult your nearest agricultural officer or KVK."
        safety_note = STANDARD_DISCLAIMER

    advisory_info = CloudAdvisoryInfo(
        severity=severity,
        urgency=urgency,
        overview=overview,
        symptoms=symptoms if symptoms else ["Foliage symptoms characteristic of infection."],
        immediate_actions=immediate_actions if immediate_actions else ["Prune and remove visibly infected plant parts."],
        prevention=prevention if prevention else ["Follow recommended crop rotation and sanitation."],
        monitoring=monitoring if monitoring else ["Regularly scout field for progression."],
        expert_escalation=expert_escalation if expert_escalation else "Consult local KVK for verified diagnosis.",
        safety_note=safety_note,
    )

    diagnosis_info = CloudDiagnosisInfo(
        crop=CropRef(id=crop_ref["id"], name=crop_ref["name"]),
        disease=DiseaseRef(id=disease_ref["id"], name=disease_ref["name"]),
        confidence=confidence,
        diagnostic_status=diagnostic_status,
        is_crop_compatible=True,
    )

    visual_reasoning = raw_result.visual_reasoning.strip()
    if not visual_reasoning:
        visual_reasoning = f"Visual analysis confirms diagnostic patterns consistent with {disease_ref['name']} on {crop_ref['name']} leaf tissue."

    return CloudDiagnosisResponse(
        status="success",
        provider=raw_result.provider_name,
        model=raw_result.model_name,
        latency_ms=latency_ms,
        diagnosis=diagnosis_info,
        visual_reasoning=visual_reasoning,
        advisory=advisory_info,
    )
