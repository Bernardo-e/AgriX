from datetime import datetime, timezone
import threading
from typing import Any, Dict, List, Optional
import uuid


class DiagnosisRepository:
    """Thread-safe in-memory repository for storing and retrieving client-submitted diagnoses."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._diagnoses: Dict[str, Dict[str, Any]] = {}
        self._order: List[str] = []

    def record_diagnosis(
        self,
        crop: Dict[str, str],
        disease: Dict[str, Any],
        confidence: float,
        diagnostic_status: str,
        source: str = "on_device_tflite",
        image_id: Optional[str] = None,
        created_at: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Store a new diagnosis record in memory."""
        with self._lock:
            diagnosis_id = f"diag_{uuid.uuid4().hex[:12]}"
            timestamp = created_at or datetime.now(timezone.utc).isoformat()

            record = {
                "id": diagnosis_id,
                "status": "recorded",
                "crop": {
                    "id": crop["id"],
                    "name": crop["name"],
                },
                "disease": {
                    "id": disease["id"],
                    "name": disease["name"],
                },
                "confidence": round(confidence, 4),
                "diagnostic_status": diagnostic_status,
                "source": source,
                "image_id": image_id,
                "created_at": timestamp,
            }

            self._diagnoses[diagnosis_id] = record
            self._order.append(diagnosis_id)
            return record

    def get_diagnosis_by_id(self, diagnosis_id: str) -> Optional[Dict[str, Any]]:
        """Retrieve a single diagnosis record by its unique ID."""
        with self._lock:
            return self._diagnoses.get(diagnosis_id)

    def list_diagnoses(
        self,
        crop_id: Optional[str] = None,
        limit: int = 50,
    ) -> List[Dict[str, Any]]:
        """List diagnoses in reverse chronological order with optional crop filter and limit."""
        # Safe bounding for limit
        safe_limit = max(1, min(limit, 100))

        with self._lock:
            # Iterate in reverse order (newest first)
            results: List[Dict[str, Any]] = []
            for diag_id in reversed(self._order):
                record = self._diagnoses[diag_id]
                if crop_id:
                    normalized_filter = crop_id.strip().lower().replace("_", " ").replace("-", " ")
                    if record["crop"]["id"] != normalized_filter and record["crop"]["name"].lower() != normalized_filter:
                        continue
                results.append(record)
                if len(results) >= safe_limit:
                    break
            return results

    def clear(self) -> None:
        """Clear all stored diagnoses (primarily for unit test isolation)."""
        with self._lock:
            self._diagnoses.clear()
            self._order.clear()


# Global singleton instance
diagnosis_repo = DiagnosisRepository()
