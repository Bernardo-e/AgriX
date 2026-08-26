from fastapi import APIRouter, HTTPException

from app.data.metadata import get_all_crops, get_all_diseases, get_crop_by_id

router = APIRouter(
    prefix="/api/v1/crops",
    tags=["Crops"],
)

diseases_router = APIRouter(
    prefix="/api/v1/diseases",
    tags=["Diseases"],
)


@router.get("", summary="Get all supported crops")
@router.get("/", include_in_schema=False)
def list_crops():
    """Return all supported crops and their disease counts."""
    return get_all_crops()


@router.get("/{crop_id}", summary="Get crop details and associated diseases")
def get_crop_details(crop_id: str):
    """Return details for a specific crop including all associated diseases."""
    crop = get_crop_by_id(crop_id)
    if not crop:
        raise HTTPException(
            status_code=404,
            detail=f"Crop '{crop_id}' not found",
        )
    return crop


@diseases_router.get("", summary="Get all supported diseases")
@diseases_router.get("/", include_in_schema=False)
def list_diseases():
    """Return all 71 supported diseases across all 29 crops."""
    return get_all_diseases()
