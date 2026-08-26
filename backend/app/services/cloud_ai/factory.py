from typing import Optional
from app.core.config import settings
from app.services.cloud_ai.gemini_provider import GeminiDiagnosisProvider
from app.services.cloud_ai.mock_provider import MockDiagnosisProvider
from app.services.cloud_ai.provider import CloudDiagnosisProvider

_CUSTOM_PROVIDER: Optional[CloudDiagnosisProvider] = None


def set_custom_cloud_provider(provider: Optional[CloudDiagnosisProvider]) -> None:
    """Allow test suites to inject custom mock or edge-case providers."""
    global _CUSTOM_PROVIDER
    _CUSTOM_PROVIDER = provider


def get_cloud_ai_provider() -> CloudDiagnosisProvider:
    """
    Factory creating configured CloudDiagnosisProvider instance based on environment settings.
    """
    if _CUSTOM_PROVIDER is not None:
        return _CUSTOM_PROVIDER

    provider_type = settings.cloud_ai_provider.strip().lower()
    if provider_type == "mock":
        return MockDiagnosisProvider()

    # Default to Gemini Provider
    return GeminiDiagnosisProvider(
        api_key=settings.cloud_ai_key,
        model_name=settings.cloud_ai_model,
        timeout_sec=settings.cloud_ai_timeout_sec,
    )
