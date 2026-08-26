from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field
from app.models.diagnosis import CropRef, DiseaseRef, DiagnosticStatusEnum


class SeverityEnum(str, Enum):
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    CRITICAL = "critical"


class UrgencyEnum(str, Enum):
    ROUTINE = "routine"
    PROMPT = "prompt"
    URGENT = "urgent"


class AdvisoryResponse(BaseModel):
    crop: CropRef = Field(..., description="Crop information")
    disease: DiseaseRef = Field(..., description="Disease information")
    severity: SeverityEnum = Field(..., description="General agronomic severity level of the disease")
    urgency: UrgencyEnum = Field(..., description="Advisory management priority (not model confidence)")
    summary: str = Field(..., description="Concise agronomic summary of the disease symptoms and nature")
    immediate_actions: List[str] = Field(..., description="Immediate cultural and non-destructive management actions")
    prevention: List[str] = Field(..., description="Preventive cultural and field management practices")
    monitoring: List[str] = Field(..., description="Scouting and disease progression monitoring guidelines")
    expert_escalation: str = Field(..., description="Guidance on when and how to consult local agricultural extension specialists")
    disclaimer: str = Field(..., description="Standard agronomic and chemical safety disclaimer")
    diagnostic_context: Optional[str] = Field(
        default=None,
        description="Optional diagnostic confidence or verification guidance note if diagnostic context was supplied",
    )


class AdvisoryDiseaseItem(BaseModel):
    id: int = Field(..., ge=0, le=70, description="Disease class ID (0..70)")
    name: str = Field(..., description="Disease display name")
    severity: SeverityEnum = Field(..., description="Agronomic severity")
    urgency: UrgencyEnum = Field(..., description="Agronomic urgency")


class AdvisoryCatalogSummaryResponse(BaseModel):
    total_advisories: int = Field(..., description="Total number of supported disease advisories", examples=[71])
    covered_diseases: int = Field(..., description="Total number of disease classes covered", examples=[71])


class CropAdvisoryListResponse(BaseModel):
    crop_id: str = Field(..., description="Crop identifier", examples=["tomato"])
    crop_name: str = Field(..., description="Display name of the crop", examples=["Tomato"])
    total_advisories: int = Field(..., description="Total advisories for this crop", examples=[7])
    covered_diseases: int = Field(..., description="Covered diseases for this crop", examples=[7])
    diseases: List[AdvisoryDiseaseItem] = Field(..., description="List of diseases and advisory attributes for this crop")
