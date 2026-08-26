from typing import Optional, Union
from fastapi import APIRouter, HTTPException, Query, status

from app.data.advisory_repository import advisory_repo
from app.models.advisory import (
    AdvisoryCatalogSummaryResponse,
    AdvisoryResponse,
    CropAdvisoryListResponse,
)
from app.models.diagnosis import DiagnosticStatusEnum

router = APIRouter(
    prefix="/api/v1/advisory",
    tags=["Advisory"],
)


@router.get(
    "",
    response_model=Union[AdvisoryCatalogSummaryResponse, CropAdvisoryListResponse],
    summary="Get advisory catalog summary or crop-specific disease coverage",
    description=(
        "Retrieve the overall advisory catalog coverage summary (71 diseases across 29 crops), "
        "or filter by `crop_id` to list advisory details for all diseases belonging to a specific crop.\n\n"
        "**Deterministic Guidance:** This endpoint provides metadata about available agronomic guidance "
        "without performing machine-learning inference."
    ),
    responses={
        200: {"description": "Advisory catalog summary or crop disease advisory listing returned."},
        404: {"description": "Specified crop_id was not found."},
    },
)
@router.get(
    "/",
    include_in_schema=False,
    response_model=Union[AdvisoryCatalogSummaryResponse, CropAdvisoryListResponse],
)
def get_advisory_catalog(
    crop_id: Optional[str] = Query(
        default=None,
        description="Optional crop ID (e.g., 'tomato') to list disease advisory availability for that crop",
        examples=["tomato"],
    ),
):
    """Return catalog summary or crop-specific advisory listing."""
    result = advisory_repo.get_advisory_summary(crop_id=crop_id)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Crop '{crop_id}' not found in supported crops.",
        )
    return result


@router.get(
    "/{crop_id}/{disease_id}",
    response_model=AdvisoryResponse,
    summary="Get deterministic agricultural advisory for a crop and disease",
    description=(
        "Retrieve structured agronomic guidance for an identified crop and disease.\n\n"
        "### Key Principles:\n"
        "- **Deterministic Engine:** Guidance is retrieved from a verified agronomic catalog and does not perform AI inference.\n"
        "- **Confidence vs. Severity:** Model `confidence` represents AI diagnostic certainty, while `severity` represents general agronomic risk.\n"
        "- **Confidence-Aware Responses:**\n"
        "  - `CONFIDENT`: Standard agronomic advisory.\n"
        "  - `MODERATE_CONFIDENCE`: Advisory with explicit recommendation for visual symptom confirmation.\n"
        "  - `LOW_CONFIDENCE`: Cautious guidance recommending multi-angle photos and extension verification.\n"
        "  - `UNKNOWN_OR_UNCERTAIN`: Disease-specific treatments are withheld; returns safe uncertainty re-imaging guidance.\n"
        "- **Agronomic & Safety Boundary:** Recommendations focus on cultural sanitation, moisture control, prevention, and extension escalation. No chemical dosages or curative claims are provided."
    ),
    responses={
        200: {"description": "Agronomic advisory returned successfully."},
        400: {"description": "Crop and disease mismatch (disease belongs to another crop)."},
        404: {"description": "Invalid crop or disease ID out of range (0..70)."},
        422: {"description": "Validation error in query parameters (e.g., confidence out of 0.0..1.0 range)."},
    },
)
def get_disease_advisory(
    crop_id: str,
    disease_id: int,
    confidence: Optional[float] = Query(
        default=None,
        ge=0.0,
        le=1.0,
        description="Optional diagnostic confidence score between 0.0 and 1.0 from on-device inference",
        examples=[0.618],
    ),
    diagnostic_status: Optional[DiagnosticStatusEnum] = Query(
        default=None,
        description="Optional diagnostic confidence band classification (CONFIDENT, MODERATE_CONFIDENCE, LOW_CONFIDENCE, UNKNOWN_OR_UNCERTAIN)",
        examples=["MODERATE_CONFIDENCE"],
    ),
):
    """
    Retrieve structured farmer advisory for a specific crop and disease.
    """
    status_str = diagnostic_status.value if diagnostic_status is not None else None

    advisory_data, error_msg, http_code = advisory_repo.get_advisory(
        crop_id=crop_id,
        disease_id=disease_id,
        confidence=confidence,
        diagnostic_status=status_str,
    )

    if advisory_data is None:
        raise HTTPException(
            status_code=http_code,
            detail=error_msg or "Failed to retrieve advisory.",
        )

    return advisory_data
