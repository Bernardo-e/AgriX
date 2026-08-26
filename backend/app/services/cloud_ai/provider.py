from abc import ABC, abstractmethod
from typing import List, Optional
from pydantic import BaseModel, ConfigDict, Field


class CloudDiagnosisException(Exception):
    """Base exception for cloud diagnosis operations."""
    def __init__(self, message: str, error_code: str = "CLOUD_AI_ERROR"):
        super().__init__(message)
        self.message = message
        self.error_code = error_code


class CloudProviderUnavailableException(CloudDiagnosisException):
    """Raised when cloud AI credentials are missing or backend provider is offline."""
    def __init__(self, message: str = "Cloud AI provider is unavailable"):
        super().__init__(message, error_code="PROVIDER_UNAVAILABLE")


class CloudProviderTimeoutException(CloudDiagnosisException):
    """Raised when cloud AI request exceeds configured timeout."""
    def __init__(self, message: str = "Cloud AI request timed out"):
        super().__init__(message, error_code="PROVIDER_TIMEOUT")


class CloudResponseInvalidException(CloudDiagnosisException):
    """Raised when cloud AI produces an invalid or unverifiable disease response."""
    def __init__(self, message: str = "Cloud AI response could not be verified"):
        super().__init__(message, error_code="INVALID_RESPONSE")


class CloudDiagnosisRequest(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    image_bytes: bytes = Field(..., description="Raw image bytes")
    mime_type: str = Field(..., description="Image MIME type (e.g. image/jpeg)")
    crop_id: str = Field(..., description="Target crop identifier")
    local_disease_id: Optional[int] = Field(default=None, description="Local TFLite disease class ID")
    local_confidence: Optional[float] = Field(default=None, description="Local confidence score")
    local_status: Optional[str] = Field(default=None, description="Local diagnostic status")
    language: str = Field(default="en", description="Target language code")
    state: Optional[str] = Field(default=None, description="Farmer state region")
    district: Optional[str] = Field(default=None, description="Farmer district region")


class CloudDiagnosisRawResult(BaseModel):
    crop_id: str
    disease_id: int
    disease_name: str
    confidence: float
    visual_reasoning: str
    symptoms: Optional[List[str]] = None
    immediate_actions: Optional[List[str]] = None
    prevention: Optional[List[str]] = None
    monitoring: Optional[List[str]] = None
    expert_escalation: Optional[str] = None
    safety_note: Optional[str] = None
    provider_name: str
    model_name: str


class CloudDiagnosisProvider(ABC):
    """Abstract interface for multi-provider Cloud AI diagnosis."""

    @abstractmethod
    async def diagnose(self, request: CloudDiagnosisRequest) -> CloudDiagnosisRawResult:
        """Execute multimodal vision diagnosis against the cloud provider."""
        pass

    @abstractmethod
    async def is_available(self) -> bool:
        """Return True if the provider is configured and available."""
        pass
