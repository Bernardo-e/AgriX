from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field
from app.models.diagnosis import CropRef, DiseaseRef


class CloudDiagnosisInfo(BaseModel):
    crop: CropRef = Field(..., description="Standardized crop reference")
    disease: DiseaseRef = Field(..., description="Standardized disease reference (0..70)")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Confidence probability score")
    diagnostic_status: str = Field(..., description="Diagnostic status band (e.g. CONFIDENT, MODERATE_CONFIDENCE)")
    is_crop_compatible: bool = Field(default=True, description="Whether disease matches crop catalog")


class CloudAdvisoryInfo(BaseModel):
    severity: str = Field(..., description="Agronomic severity level (low, moderate, high, critical)")
    urgency: str = Field(..., description="Management urgency (routine, prompt, urgent)")
    overview: str = Field(..., description="Agronomic overview of the condition")
    symptoms: List[str] = Field(..., description="Observable symptoms on foliage/stem/fruit")
    immediate_actions: List[str] = Field(..., description="Safe immediate cultural/management actions")
    prevention: List[str] = Field(..., description="Preventive field practices and crop rotation")
    monitoring: List[str] = Field(..., description="Ongoing scouting and monitoring instructions")
    expert_escalation: str = Field(..., description="Guidance on consulting local KVK or extension specialist")
    safety_note: str = Field(..., description="Statutory safety disclaimer and IPM guidance")


class CloudDiagnosisResponse(BaseModel):
    status: str = Field(default="success", description="Diagnosis status ('success')")
    provider: str = Field(..., description="Provider name (e.g. 'gemini-1.5-flash', 'mock')")
    model: str = Field(..., description="Underlying model identifier")
    latency_ms: int = Field(..., description="Processing latency in milliseconds")
    diagnosis: CloudDiagnosisInfo = Field(..., description="Validated disease diagnosis")
    visual_reasoning: str = Field(..., description="Detailed visual reasoning and symptom explanation")
    advisory: CloudAdvisoryInfo = Field(..., description="Structured 7-section agronomic advisory")


class CloudDiagnosisErrorResponse(BaseModel):
    status: str = Field(default="error", description="Error status indicator")
    error_code: str = Field(..., description="Standard error code (e.g. 'PROVIDER_UNAVAILABLE', 'INVALID_IMAGE')")
    message: str = Field(..., description="Human-readable explanation")
    fallback_to_local: bool = Field(default=True, description="Whether client should fallback to local AI result")
    local_diagnosis_retained: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Local diagnostic context retained for continuity",
    )
