import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# Locate metadata json files within the app/data package
_DATA_DIR = Path(__file__).resolve().parent
_CROP_MAP_FILE = _DATA_DIR / "crop_disease_map.json"
_LABEL_MAP_FILE = _DATA_DIR / "agrix_label_map.json"


def _load_metadata() -> Dict[str, Any]:
    if not _CROP_MAP_FILE.exists():
        raise FileNotFoundError(f"Crop metadata file not found at {_CROP_MAP_FILE}")
    with open(_CROP_MAP_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def _load_label_map() -> Dict[str, str]:
    if not _LABEL_MAP_FILE.exists():
        return {}
    with open(_LABEL_MAP_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


_RAW_CROP_MAP = _load_metadata()
_RAW_LABEL_MAP = _load_label_map()

# Build inverse lookup map: disease_id -> (disease_name, crop_id, crop_display)
_DISEASE_LOOKUP: Dict[int, Dict[str, Any]] = {}
for _c_id, _c_data in _RAW_CROP_MAP.items():
    _c_disp = _c_data.get("crop_display", _c_id.capitalize())
    for _item in _c_data.get("disease_details", []):
        _d_id = _item["class_id"]
        _DISEASE_LOOKUP[_d_id] = {
            "disease_id": _d_id,
            "disease_name": _item["disease_name"],
            "crop_id": _c_id,
            "crop_name": _c_disp,
        }


def _normalize_crop_id(crop_id: str) -> str:
    """Normalize input crop ID (e.g. 'bell_pepper', 'bell-pepper', 'Tomato' -> 'bell pepper', 'tomato')."""
    cleaned = crop_id.strip().lower()
    cleaned = cleaned.replace("_", " ").replace("-", " ")
    return cleaned


def get_all_crops() -> Dict[str, Any]:
    """Return all supported crops with ID, display name, and disease count."""
    crops_list: List[Dict[str, Any]] = []
    for crop_id, data in _RAW_CROP_MAP.items():
        crops_list.append({
            "id": crop_id,
            "name": data["crop_display"],
            "disease_count": data["class_count"],
        })
    return {
        "total_crops": len(crops_list),
        "crops": crops_list,
    }


def get_crop_by_id(crop_id: str) -> Optional[Dict[str, Any]]:
    """Return detailed crop data including its associated diseases, or None if not found."""
    normalized_id = _normalize_crop_id(crop_id)
    data = _RAW_CROP_MAP.get(normalized_id)
    if not data:
        # Fallback check against crop_display
        for k, v in _RAW_CROP_MAP.items():
            if v["crop_display"].lower() == normalized_id:
                data = v
                normalized_id = k
                break

    if not data:
        return None

    diseases = [
        {
            "id": item["class_id"],
            "name": item["disease_name"],
        }
        for item in data.get("disease_details", [])
    ]

    return {
        "id": normalized_id,
        "name": data["crop_display"],
        "disease_count": data["class_count"],
        "diseases": diseases,
    }


def get_all_diseases() -> Dict[str, Any]:
    """Return all 71 supported disease classes ordered by class ID with their associated crop."""
    disease_list: List[Dict[str, Any]] = []
    for crop_id, data in _RAW_CROP_MAP.items():
        crop_display = data["crop_display"]
        for item in data.get("disease_details", []):
            disease_list.append({
                "id": item["class_id"],
                "name": item["disease_name"],
                "crop_id": crop_id,
                "crop_name": crop_display,
            })

    # Sort deterministically by disease ID (0..70)
    disease_list.sort(key=lambda x: x["id"])

    return {
        "total_diseases": len(disease_list),
        "diseases": disease_list,
    }


def get_disease_by_id(disease_id: int) -> Optional[Dict[str, Any]]:
    """Return metadata for a specific disease by its 0..70 class ID."""
    return _DISEASE_LOOKUP.get(disease_id)


def validate_crop_disease(
    crop_id: str,
    disease_id: int,
) -> Tuple[bool, Optional[str], Optional[Dict[str, Any]], Optional[Dict[str, Any]]]:
    """
    Validate that:
    1. crop_id exists in the 29 supported crops.
    2. disease_id exists in 0..70.
    3. disease_id belongs to the specified crop.

    Returns:
        (is_valid, error_message, crop_ref_dict, disease_ref_dict)
    """
    # 1. Validate crop existence
    crop_data = get_crop_by_id(crop_id)
    if not crop_data:
        return False, f"Crop '{crop_id}' not found in supported crops.", None, None

    # 2. Validate disease existence
    disease_data = get_disease_by_id(disease_id)
    if not disease_data:
        return False, f"Disease ID {disease_id} is invalid (must be between 0 and 70).", None, None

    # 3. Validate crop-disease association
    if disease_data["crop_id"] != crop_data["id"]:
        return (
            False,
            f"Disease ID {disease_id} ('{disease_data['disease_name']}') belongs to crop '{disease_data['crop_name']}' ({disease_data['crop_id']}), not '{crop_data['name']}' ({crop_data['id']}).",
            None,
            None,
        )

    crop_ref = {"id": crop_data["id"], "name": crop_data["name"]}
    disease_ref = {"id": disease_data["disease_id"], "name": disease_data["disease_name"]}

    return True, None, crop_ref, disease_ref
