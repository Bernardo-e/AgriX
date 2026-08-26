import asyncio
from typing import Optional
from app.services.cloud_ai.provider import (
    CloudDiagnosisException,
    CloudDiagnosisProvider,
    CloudDiagnosisRequest,
    CloudDiagnosisRawResult,
    CloudProviderTimeoutException,
    CloudProviderUnavailableException,
    CloudResponseInvalidException,
)


class MockDiagnosisProvider(CloudDiagnosisProvider):
    """
    Deterministic Mock Cloud AI Provider for automated tests and CI environments.
    Configurable to simulate success, timeout, errors, and validation edge cases.
    """

    def __init__(
        self,
        mode: str = "success",
        delay_sec: float = 0.05,
        disease_id_override: Optional[int] = None,
        crop_id_override: Optional[str] = None,
        confidence_override: float = 0.92,
        is_available_override: bool = True,
    ):
        self.mode = mode
        self.delay_sec = delay_sec
        self.disease_id_override = disease_id_override
        self.crop_id_override = crop_id_override
        self.confidence_override = confidence_override
        self._is_available = is_available_override

    async def is_available(self) -> bool:
        return self._is_available

    async def diagnose(self, request: CloudDiagnosisRequest) -> CloudDiagnosisRawResult:
        if not self._is_available:
            raise CloudProviderUnavailableException("Mock cloud AI provider is configured as unavailable.")

        if self.delay_sec > 0:
            await asyncio.sleep(self.delay_sec)

        if self.mode == "timeout":
            raise CloudProviderTimeoutException("Mock cloud AI request timed out after 6.0s.")

        if self.mode == "server_error":
            raise CloudDiagnosisException("Mock cloud AI provider encountered an internal 500 server error.", error_code="PROVIDER_ERROR")

        if self.mode == "invalid_disease":
            return CloudDiagnosisRawResult(
                crop_id=request.crop_id,
                disease_id=999,  # Invalid disease ID > 70
                disease_name="Unknown Extraterrestrial Blight",
                confidence=0.88,
                visual_reasoning="Simulated invalid disease test output.",
                provider_name="mock",
                model_name="mock-v1",
            )

        if self.mode == "crop_mismatch":
            # Return a wheat disease when requested crop is tomato
            return CloudDiagnosisRawResult(
                crop_id="wheat",
                disease_id=64,  # Wheat Leaf Rust
                disease_name="Wheat Leaf / Brown Rust",
                confidence=0.85,
                visual_reasoning="Simulated crop mismatch test output.",
                provider_name="mock",
                model_name="mock-v1",
            )

        # Default: Success Mode
        # Determine disease ID based on crop or overrides
        crop_id_norm = (self.crop_id_override or request.crop_id).strip().lower()
        if self.disease_id_override is not None:
            d_id = self.disease_id_override
        elif crop_id_norm == "tomato":
            d_id = request.local_disease_id if (request.local_disease_id in [53, 54, 55, 56, 57, 58, 59]) else 54
        elif crop_id_norm == "rice":
            d_id = 43  # Rice Blast
        elif crop_id_norm == "wheat":
            d_id = 66  # Wheat Stem Rust
        elif crop_id_norm in ["chilli", "pepper bell"]:
            d_id = 10  # Bell Pepper Bacterial Spot
        elif crop_id_norm == "potato":
            d_id = 41  # Potato Early Blight
        else:
            d_id = request.local_disease_id or 0

        # Mapping for display names
        default_names = {
            54: "Tomato Early Blight",
            53: "Tomato Bacterial Spot",
            43: "Rice Blast",
            44: "Rice Sheath Blight",
            66: "Wheat Stem Rust",
            10: "Bell Pepper Bacterial Spot",
            41: "Potato Early Blight",
        }
        d_name = default_names.get(d_id, f"Disease {d_id}")

        return CloudDiagnosisRawResult(
            crop_id=crop_id_norm,
            disease_id=d_id,
            disease_name=d_name,
            confidence=self.confidence_override,
            visual_reasoning=f"High-resolution visual inspection confirms characteristic diagnostic patterns of {d_name} on {crop_id_norm.capitalize()} foliage.",
            symptoms=[
                f"Observable leaf lesions and necrosis matching {d_name}.",
                "Chlorotic margins surrounding visible infection centers.",
            ],
            immediate_actions=[
                "Carefully prune infected lower leaves and safely dispose off-site.",
                "Ensure drip irrigation to prevent moisture on canopy foliage.",
            ],
            prevention=[
                "Maintain adequate plant spacing for optimal aeration.",
                "Practice field sanitation and crop rotation with non-host crops.",
            ],
            monitoring=[
                "Inspect lower leaves weekly for early signs of disease progression.",
            ],
            expert_escalation="If infection spreads to upper canopy, consult your local Krishi Vigyan Kendra (KVK) specialist.",
            safety_note="Always follow integrated pest management (IPM) guidelines. Practice non-destructive cultural methods before applying any agricultural interventions.",
            provider_name="mock",
            model_name="mock-v1",
        )
