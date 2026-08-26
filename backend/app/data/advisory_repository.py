import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.data.metadata import (
    _DISEASE_LOOKUP,
    _RAW_CROP_MAP,
    get_crop_by_id,
    get_disease_by_id,
    validate_crop_disease,
)

_DATA_DIR = Path(__file__).resolve().parent
_ADVISORY_FILE = _DATA_DIR / "agrix_advisory_catalog.json"

STANDARD_DISCLAIMER = (
    "AgriX advisory provides general agronomic guidance and cultural management practices. "
    "It does not replace professional agronomic diagnosis. Chemical interventions, dosages, "
    "and pesticide selections must strictly adhere to local agricultural extension regulations "
    "and approved product labels."
)


def _load_and_validate_catalog() -> Dict[int, Dict[str, Any]]:
    """
    Load the advisory catalog JSON and strictly validate its integrity
    against the 71-disease, 29-crop metadata specification.
    """
    if not _ADVISORY_FILE.exists():
        raise FileNotFoundError(f"Advisory catalog file not found at {_ADVISORY_FILE}")

    with open(_ADVISORY_FILE, "r", encoding="utf-8") as f:
        raw_data = json.load(f)

    catalog: Dict[int, Dict[str, Any]] = {}

    # Verify every key is an integer in 0..70
    for key_str, entry in raw_data.items():
        try:
            d_id = int(key_str)
        except ValueError:
            raise ValueError(f"Invalid non-integer disease key '{key_str}' in advisory catalog.")

        if d_id < 0 or d_id > 70:
            raise ValueError(f"Disease ID {d_id} in advisory catalog is out of range (0..70).")

        if d_id in catalog:
            raise ValueError(f"Duplicate advisory entry detected for disease ID {d_id}.")

        catalog[d_id] = entry

    # Verify exactly 71 entries covering 0..70
    if len(catalog) != 71:
        raise ValueError(
            f"Advisory catalog data integrity failure: expected exactly 71 entries, found {len(catalog)}."
        )

    for expected_id in range(71):
        if expected_id not in catalog:
            raise ValueError(
                f"Advisory catalog missing advisory entry for disease ID {expected_id}."
            )

        entry = catalog[expected_id]
        meta_disease = get_disease_by_id(expected_id)
        if not meta_disease:
            raise ValueError(
                f"Disease ID {expected_id} does not exist in the primary metadata repository."
            )

        # Validate crop association match
        if entry.get("crop_id") != meta_disease["crop_id"]:
            raise ValueError(
                f"Advisory entry {expected_id} lists crop '{entry.get('crop_id')}', "
                f"but metadata specifies '{meta_disease['crop_id']}'."
            )

        # Validate required fields
        required_fields = [
            "disease_id",
            "disease_name",
            "crop_id",
            "severity",
            "urgency",
            "summary",
            "immediate_actions",
            "prevention",
            "monitoring",
            "expert_escalation",
        ]
        for field in required_fields:
            if field not in entry:
                raise ValueError(
                    f"Advisory entry for disease ID {expected_id} is missing required field '{field}'."
                )

    return catalog


class AdvisoryRepository:
    """Deterministic repository for crop disease agricultural advisory guidance."""

    def __init__(self) -> None:
        self._catalog: Dict[int, Dict[str, Any]] = _load_and_validate_catalog()

    @property
    def total_count(self) -> int:
        return len(self._catalog)

    def get_raw_entry(self, disease_id: int) -> Optional[Dict[str, Any]]:
        return self._catalog.get(disease_id)

    def get_advisory_summary(self, crop_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """Return overall catalog summary or crop-filtered disease list with advisory metadata."""
        if crop_id is None:
            return {
                "total_advisories": len(self._catalog),
                "covered_diseases": len(self._catalog),
            }

        crop_info = get_crop_by_id(crop_id)
        if not crop_info:
            return None

        crop_normalized_id = crop_info["id"]
        diseases_summary: List[Dict[str, Any]] = []

        for d in crop_info.get("diseases", []):
            d_id = d["id"]
            entry = self._catalog.get(d_id)
            if entry:
                diseases_summary.append({
                    "id": d_id,
                    "name": d["name"],
                    "severity": entry["severity"],
                    "urgency": entry["urgency"],
                })

        return {
            "crop_id": crop_normalized_id,
            "crop_name": crop_info["name"],
            "total_advisories": len(diseases_summary),
            "covered_diseases": len(diseases_summary),
            "diseases": diseases_summary,
        }

    def get_advisory(
        self,
        crop_id: str,
        disease_id: int,
        confidence: Optional[float] = None,
        diagnostic_status: Optional[str] = None,
    ) -> Tuple[Optional[Dict[str, Any]], Optional[str], int]:
        """
        Retrieve deterministic advisory guidance for a verified crop and disease.

        Returns:
            (advisory_dict_or_None, error_message_or_None, http_status_code)
        """
        # Validate crop and disease existence
        crop_data = get_crop_by_id(crop_id)
        if not crop_data:
            return None, f"Crop '{crop_id}' not found in supported crops.", 404

        disease_data = get_disease_by_id(disease_id)
        if not disease_data:
            return None, f"Disease ID {disease_id} is invalid (must be between 0 and 70).", 404

        # Validate crop-disease association
        if disease_data["crop_id"] != crop_data["id"]:
            return (
                None,
                f"Disease ID {disease_id} ('{disease_data['disease_name']}') belongs to crop "
                f"'{disease_data['crop_name']}' ({disease_data['crop_id']}), not "
                f"'{crop_data['name']}' ({crop_data['id']}).",
                400,
            )

        raw_advisory = self._catalog.get(disease_id)
        if not raw_advisory:
            return None, f"Advisory not found for disease ID {disease_id}.", 404

        crop_ref = {"id": crop_data["id"], "name": crop_data["name"]}
        disease_ref = {"id": disease_data["disease_id"], "name": disease_data["disease_name"]}

        # Determine effective diagnostic confidence status
        effective_status = diagnostic_status
        if effective_status is None and confidence is not None:
            if confidence >= 0.75:
                effective_status = "CONFIDENT"
            elif confidence >= 0.50:
                effective_status = "MODERATE_CONFIDENCE"
            elif confidence > 0.0:
                effective_status = "LOW_CONFIDENCE"
            else:
                effective_status = "UNKNOWN_OR_UNCERTAIN"

        # Apply confidence-aware guidance formatting
        if effective_status == "UNKNOWN_OR_UNCERTAIN":
            return (
                {
                    "crop": crop_ref,
                    "disease": disease_ref,
                    "severity": "low",
                    "urgency": "routine",
                    "summary": (
                        "Diagnosis is uncertain. Capture a clearer image of the affected leaf and "
                        "verify the crop and symptoms before taking disease-specific action."
                    ),
                    "immediate_actions": [
                        "Do not apply chemical treatments or aggressive interventions based on uncertain identification.",
                        "Capture high-resolution, well-lit photos of both the upper and lower surfaces of affected leaves.",
                        "Check surrounding plants in the field to observe whether symptoms are localized or widespread.",
                        "Consult a local agricultural extension officer or certified agronomist for on-site confirmation.",
                    ],
                    "prevention": [
                        "Maintain standard field sanitation and avoid overwatering or prolonged leaf wetness.",
                        "Disinfect pruning shears and handling tools between plants.",
                    ],
                    "monitoring": [
                        "Observe the flagged plant daily for symptom progression or spreading.",
                        "Note whether symptoms appear on new growth or older lower leaves.",
                    ],
                    "expert_escalation": (
                        "Take physical leaf samples or high-quality photos to the nearest Krishi Vigyan Kendra "
                        "(KVK) or local agricultural extension center for definitive confirmation."
                    ),
                    "disclaimer": STANDARD_DISCLAIMER,
                    "diagnostic_context": (
                        "Diagnostic status: UNKNOWN_OR_UNCERTAIN. Disease-specific treatment guidance is withheld "
                        "until visual confirmation is obtained."
                    ),
                },
                None,
                200,
            )

        summary = raw_advisory["summary"]
        immediate_actions = list(raw_advisory["immediate_actions"])
        diagnostic_context = None

        if effective_status == "MODERATE_CONFIDENCE":
            diagnostic_context = (
                f"Diagnostic confidence is moderate ({f'{confidence:.1%}' if confidence is not None else 'moderate'}). "
                "Visual symptom verification is recommended before taking intervention steps."
            )
        elif effective_status == "LOW_CONFIDENCE":
            diagnostic_context = (
                f"Diagnostic confidence is low ({f'{confidence:.1%}' if confidence is not None else 'low'}). "
                "Cautious guidance provided. Please capture clearer or multiple images from different angles "
                "and seek in-person expert verification before taking disease-specific action."
            )
        elif effective_status == "CONFIDENT":
            if confidence is not None:
                diagnostic_context = f"Diagnostic confidence: {confidence:.1%} (CONFIDENT)."

        return (
            {
                "crop": crop_ref,
                "disease": disease_ref,
                "severity": raw_advisory["severity"],
                "urgency": raw_advisory["urgency"],
                "summary": summary,
                "immediate_actions": immediate_actions,
                "prevention": list(raw_advisory["prevention"]),
                "monitoring": list(raw_advisory["monitoring"]),
                "expert_escalation": raw_advisory["expert_escalation"],
                "disclaimer": STANDARD_DISCLAIMER,
                "diagnostic_context": diagnostic_context,
            },
            None,
            200,
        )


# Global singleton instance loaded and validated at module import
advisory_repo = AdvisoryRepository()
