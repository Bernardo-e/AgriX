import logging
import time
from typing import Optional
from fastapi import APIRouter, File, Form, HTTPException, UploadFile, status
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.data.metadata import get_crop_by_id, get_disease_by_id
from app.models.cloud_diagnosis import (
    CloudDiagnosisErrorResponse,
    CloudDiagnosisResponse,
)
from app.services.cloud_ai.factory import get_cloud_ai_provider
from app.services.cloud_ai.provider import (
    CloudDiagnosisException,
    CloudDiagnosisRequest,
    CloudProviderTimeoutException,
    CloudProviderUnavailableException,
    CloudResponseInvalidException,
)
from app.services.cloud_ai.validator import validate_and_build_response

logger = logging.getLogger("agrix.cloud_diagnosis")

router = APIRouter(prefix="/api/v1", tags=["Cloud AI Diagnosis"])


@router.post(
    "/cloud-diagnosis",
    response_model=CloudDiagnosisResponse,
    responses={
        200: {"description": "Successful enhanced cloud AI diagnosis"},
        400: {"model": CloudDiagnosisErrorResponse, "description": "Invalid input image or parameters"},
        422: {"model": CloudDiagnosisErrorResponse, "description": "Cloud AI output failed verification"},
        503: {"model": CloudDiagnosisErrorResponse, "description": "Cloud AI provider unavailable (fallback to local)"},
        504: {"model": CloudDiagnosisErrorResponse, "description": "Cloud AI provider timed out (fallback to local)"},
    },
    summary="Enhanced Multimodal Cloud AI Plant Disease Diagnosis",
    description="Analyzes crop leaf image using companion Cloud AI to provide deeper diagnostic reasoning, symptom explanations, and localized IPM agricultural guidance.",
)
async def perform_cloud_diagnosis(
    image: UploadFile = File(..., description="Crop leaf photo (JPEG/PNG/WEBP, max 4MB)"),
    crop_id: str = Form(..., description="Target crop identifier matching AgriX metadata"),
    local_disease_id: Optional[int] = Form(None, description="Local TFLite predicted disease class ID (0..70)"),
    local_confidence: Optional[float] = Form(None, description="Local on-device confidence score (0.0..1.0)"),
    local_status: Optional[str] = Form(None, description="Local diagnostic status string"),
    language: Optional[str] = Form("en", description="Target localization language (en, hi, te, ta, kn, ml)"),
    state: Optional[str] = Form(None, description="Farmer state region"),
    district: Optional[str] = Form(None, description="Farmer district region"),
):
    start_time = time.perf_counter()
    clean_crop_id = crop_id.strip().lower()

    # 1. Validate Crop existence
    crop_data = get_crop_by_id(clean_crop_id)
    if not crop_data:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="INVALID_CROP",
                message=f"Crop '{crop_id}' is not supported in the AgriX catalog.",
                fallback_to_local=True,
            ).model_dump(),
        )

    # 2. Validate Local Disease ID if supplied
    if local_disease_id is not None:
        if local_disease_id < 0 or local_disease_id > 70 or not get_disease_by_id(local_disease_id):
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content=CloudDiagnosisErrorResponse(
                    status="error",
                    error_code="INVALID_LOCAL_DISEASE_ID",
                    message=f"Local disease ID {local_disease_id} is out of range (0..70).",
                    fallback_to_local=True,
                ).model_dump(),
            )

    # 3. Validate Local Confidence if supplied
    if local_confidence is not None:
        if local_confidence < 0.0 or local_confidence > 1.0:
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content=CloudDiagnosisErrorResponse(
                    status="error",
                    error_code="INVALID_CONFIDENCE",
                    message=f"Local confidence score must be between 0.0 and 1.0, got {local_confidence}.",
                    fallback_to_local=True,
                ).model_dump(),
            )

    # 4. Validate Language if supplied
    clean_lang = (language or "en").strip().lower()
    if clean_lang not in settings.supported_languages:
        clean_lang = "en"

    # 5. Validate Image MIME type
    content_type = image.content_type or ""
    if content_type.lower() not in settings.allowed_mime_types:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="INVALID_IMAGE_MIME",
                message=f"Image MIME type '{content_type}' is not supported. Allowed: {settings.allowed_mime_types}",
                fallback_to_local=True,
            ).model_dump(),
        )

    # 6. Read and validate image size
    try:
        image_bytes = await image.read()
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="IMAGE_READ_ERROR",
                message=f"Failed to read uploaded image bytes: {str(e)}",
                fallback_to_local=True,
            ).model_dump(),
        )

    if len(image_bytes) > settings.max_image_size_bytes:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="IMAGE_TOO_LARGE",
                message=f"Image size ({len(image_bytes)} bytes) exceeds maximum limit of {settings.max_image_size_bytes} bytes (4 MB).",
                fallback_to_local=True,
            ).model_dump(),
        )

    if len(image_bytes) < 64:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="IMAGE_CORRUPT",
                message="Uploaded image payload is too small or corrupt.",
                fallback_to_local=True,
            ).model_dump(),
        )

    # 7. Prepare internal request
    diag_request = CloudDiagnosisRequest(
        image_bytes=image_bytes,
        mime_type=content_type,
        crop_id=crop_data["id"],
        local_disease_id=local_disease_id,
        local_confidence=local_confidence,
        local_status=local_status,
        language=clean_lang,
        state=state.strip() if state else None,
        district=district.strip() if district else None,
    )

    # 8. Obtain provider and execute diagnosis
    provider = get_cloud_ai_provider()

    local_context = {
        "crop_id": crop_data["id"],
        "local_disease_id": local_disease_id,
        "local_confidence": local_confidence,
        "local_status": local_status,
    }

    try:
        raw_result = await provider.diagnose(diag_request)
        latency_ms = int((time.perf_counter() - start_time) * 1000)
        response = validate_and_build_response(
            raw_result=raw_result,
            requested_crop_id=clean_crop_id,
            latency_ms=latency_ms,
        )
        return response
    except CloudProviderUnavailableException as e:
        logger.warning("Cloud provider unavailable: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="PROVIDER_UNAVAILABLE",
                message=e.message,
                fallback_to_local=True,
                local_diagnosis_retained=local_context,
            ).model_dump(),
        )
    except CloudProviderTimeoutException as e:
        logger.warning("Cloud provider timeout: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="PROVIDER_TIMEOUT",
                message=e.message,
                fallback_to_local=True,
                local_diagnosis_retained=local_context,
            ).model_dump(),
        )
    except CloudResponseInvalidException as e:
        logger.warning("Cloud provider response validation failed: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="INVALID_CLOUD_RESPONSE",
                message=e.message,
                fallback_to_local=True,
                local_diagnosis_retained=local_context,
            ).model_dump(),
        )
    except CloudDiagnosisException as e:
        logger.error("Cloud provider error: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code=e.error_code,
                message=e.message,
                fallback_to_local=True,
                local_diagnosis_retained=local_context,
            ).model_dump(),
        )
    except Exception as e:
        logger.error("Unexpected error in cloud diagnosis: %s", str(e), exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=CloudDiagnosisErrorResponse(
                status="error",
                error_code="INTERNAL_ERROR",
                message="An unexpected error occurred during cloud AI analysis.",
                fallback_to_local=True,
                local_diagnosis_retained=local_context,
            ).model_dump(),
        )
