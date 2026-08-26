from datetime import datetime, timezone
from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field


class DiagnosticStatusEnum(str, Enum):
    CONFIDENT = "CONFIDENT"
    MODERATE_CONFIDENCE = "MODERATE_CONFIDENCE"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"
    UNKNOWN_OR_UNCERTAIN = "UNKNOWN_OR_UNCERTAIN"


class CropRef(BaseModel):
    id: str = Field(..., description="Standardized crop identifier")
    name: str = Field(..., description="Display name of the crop")


class DiseaseRef(BaseModel):
    id: int = Field(..., ge=0, le=70, description="Integer class index (0..70) matching TFLite model")
    name: str = Field(..., description="Display name of the diagnosed plant disease")


class DiagnosisCreateRequest(BaseModel):
    crop_id: str = Field(
        ...,
        description="Crop identifier (must correspond to one of the 29 supported crops)",
        examples=["tomato"],
    )
    disease_id: int = Field(
        ...,
        ge=0,
        le=70,
        description="Disease class ID (0..70) matching the 71-class TFLite model",
        examples=[53],
    )
    confidence: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Diagnostic confidence probability score between 0.0 and 1.0",
        examples=[0.618],
    )
    diagnostic_status: DiagnosticStatusEnum = Field(
        ...,
        description="Confidence band classification produced by the on-device AI decision layer",
        examples=["MODERATE_CONFIDENCE"],
    )
    source: str = Field(
        default="on_device_tflite",
        description="Origin of the diagnosis (e.g. 'on_device_tflite')",
        examples=["on_device_tflite"],
    )
    image_id: Optional[str] = Field(
        default=None,
        description="Optional local client image identifier",
        examples=["img_leaf_001"],
    )
    created_at: Optional[str] = Field(
        default=None,
        description="Optional ISO timestamp of when diagnosis was computed",
        examples=["2026-08-26T14:30:00Z"],
    )


class DiagnosisResponse(BaseModel):
    id: str = Field(..., description="Unique diagnosis identifier", examples=["diag_a1b2c3d4e5"])
    status: str = Field(default="recorded", description="Status of recording on backend", examples=["recorded"])
    crop: CropRef = Field(..., description="Crop information")
    disease: DiseaseRef = Field(..., description="Disease information")
    confidence: float = Field(..., description="Diagnostic confidence score", examples=[0.618])
    diagnostic_status: str = Field(..., description="Diagnostic confidence state", examples=["MODERATE_CONFIDENCE"])
    source: str = Field(..., description="Origin of diagnosis", examples=["on_device_tflite"])
    image_id: Optional[str] = Field(default=None, description="Optional image identifier")
    created_at: str = Field(..., description="ISO timestamp of record creation")


class DiagnosisListResponse(BaseModel):
    total: int = Field(..., description="Total number of diagnoses matching query", examples=[1])
    diagnoses: List[DiagnosisResponse] = Field(..., description="List of recorded diagnosis records")
