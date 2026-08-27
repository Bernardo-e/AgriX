import logging
import time
from fastapi import APIRouter, HTTPException, status
from fastapi.responses import JSONResponse

from app.models.sensor import SensorAnalysisRequest, SensorAnalysisResponse
from app.services.cloud_ai.factory import get_cloud_ai_provider
from app.services.cloud_ai.provider import (
    CloudDiagnosisException,
    CloudProviderTimeoutException,
    CloudProviderUnavailableException,
    CloudResponseInvalidException,
)

logger = logging.getLogger("agrix.sensor")

router = APIRouter(prefix="/api/v1", tags=["Sensor Analysis"])


@router.post(
    "/sensor-analysis",
    response_model=SensorAnalysisResponse,
    responses={
        200: {"description": "Successful agricultural sensor telemetry analysis"},
        400: {"description": "Invalid sensor telemetry values"},
        503: {"description": "Cloud AI provider unavailable (use local fallback)"},
        504: {"description": "Cloud AI provider timed out (use local fallback)"},
    },
    summary="Agronomic Sensor Telemetry AI Analysis",
    description="Analyzes soil and environmental sensor telemetry (Temperature, Humidity, Moisture, pH) via Cloud AI to provide agronomic guidance, risk analysis, and irrigation recommendations.",
)
async def perform_sensor_analysis(request: SensorAnalysisRequest):
    start_time = time.perf_counter()
    provider = get_cloud_ai_provider()

    try:
        raw_result = await provider.analyze_sensor(request)
        latency_ms = int((time.perf_counter() - start_time) * 1000)

        return SensorAnalysisResponse(
            status="success",
            provider=raw_result.provider_name,
            model=raw_result.model_name,
            soil_interpretation=raw_result.soil_interpretation,
            crop_implications=raw_result.crop_implications,
            irrigation_advice=raw_result.irrigation_advice,
            possible_risks=raw_result.possible_risks,
            recommended_next_action=raw_result.recommended_next_action,
            farmer_summary=raw_result.farmer_summary,
            latency_ms=latency_ms,
            overall_condition=raw_result.overall_condition,
            priority=raw_result.priority or "LOW",
            watering_decision=raw_result.watering_decision,
            watering_explanation=raw_result.watering_explanation,
            watering_timing=raw_result.watering_timing,
            watering_action=raw_result.watering_action,
            environment_assessment=raw_result.environment_assessment,
            disease_prevention=raw_result.disease_prevention,
            crop_growth_guidance=raw_result.crop_growth_guidance,
            action_now_summary=raw_result.action_now_summary or raw_result.farmer_summary,
        )
    except CloudProviderUnavailableException as e:
        logger.warning("Cloud provider unavailable for sensor analysis: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "status": "error",
                "error_code": "PROVIDER_UNAVAILABLE",
                "message": str(e.message),
                "fallback_to_local": True,
            },
        )
    except CloudProviderTimeoutException as e:
        logger.warning("Cloud provider timeout for sensor analysis: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            content={
                "status": "error",
                "error_code": "PROVIDER_TIMEOUT",
                "message": str(e.message),
                "fallback_to_local": True,
            },
        )
    except CloudResponseInvalidException as e:
        logger.warning("Cloud response invalid for sensor analysis: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={
                "status": "error",
                "error_code": "INVALID_RESPONSE",
                "message": str(e.message),
                "fallback_to_local": True,
            },
        )
    except CloudDiagnosisException as e:
        logger.error("Cloud provider error in sensor analysis: %s", str(e))
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "status": "error",
                "error_code": e.error_code,
                "message": str(e.message),
                "fallback_to_local": True,
            },
        )
    except Exception as e:
        logger.error("Unexpected error in sensor analysis: %s", str(e), exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "status": "error",
                "error_code": "INTERNAL_ERROR",
                "message": "An unexpected error occurred during sensor telemetry analysis.",
                "fallback_to_local": True,
            },
        )
