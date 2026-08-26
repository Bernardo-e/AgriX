from fastapi import APIRouter

router = APIRouter(
    prefix="/api/v1/system",
    tags=["System"],
)


@router.get("/status")
def get_system_status():
    return {
        "service": "AgriX Backend",
        "status": "online",
        "api_version": "v1",
        "ai_mode": "offline_first",
        "supported_crops": 29,
        "supported_diseases": 71,
    }
