import asyncio
from typing import Optional
from app.models.sensor import SensorAnalysisRawResult, SensorAnalysisRequest
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

    async def analyze_sensor(self, request: SensorAnalysisRequest) -> SensorAnalysisRawResult:
        if not self._is_available:
            raise CloudProviderUnavailableException("Mock cloud AI provider is configured as unavailable.")

        if self.delay_sec > 0:
            await asyncio.sleep(self.delay_sec)

        if self.mode == "timeout":
            raise CloudProviderTimeoutException("Mock sensor analysis request timed out.")

        if self.mode == "server_error":
            raise CloudDiagnosisException("Mock sensor analysis encountered a server error.", error_code="PROVIDER_ERROR")

        # Synthesize realistic agricultural interpretation
        risks = []
        if request.soil_moisture < 35.0:
            moisture_state = "Critically Dry"
            irrigation_advice = "Immediate irrigation required. Schedule drip watering for 45-60 minutes."
            risks.append("Moisture stress and root dehydration risk.")
        elif request.soil_moisture <= 55.0:
            moisture_state = "Adequate / Moderate"
            irrigation_advice = "Maintain normal irrigation schedule. Moisture levels are currently balanced."
        else:
            moisture_state = "Moist to Saturated"
            irrigation_advice = "Delay scheduled irrigation to prevent root hypoxia and waterlogging."
            risks.append("Saturated soil conditions may promote anaerobic root rot.")

        if request.soil_ph < 5.8:
            ph_state = f"Acidic (pH {request.soil_ph})"
            risks.append("Potential reduction in phosphorus and calcium availability.")
        elif request.soil_ph <= 7.5:
            ph_state = f"Optimal (pH {request.soil_ph})"
        else:
            ph_state = f"Alkaline (pH {request.soil_ph})"
            risks.append("Alkaline condition may restrict micronutrient uptake (Zinc, Iron).")

        if request.temperature > 32.0:
            risks.append(f"High temperature ({request.temperature}°C) elevates evapotranspiration and vegetative heat stress.")

        if request.humidity > 80.0:
            risks.append(f"High relative humidity ({request.humidity}%) increases susceptibility to foliar fungal diseases.")

        crop_str = f" for {request.crop_name}" if request.crop_name else ""
        soil_interp = f"Soil moisture is measured at {request.soil_moisture}% ({moisture_state}) with a pH of {request.soil_ph} ({ph_state})."
        crop_imp = f"Thermal conditions ({request.temperature}°C) and soil chemistry provide favorable vegetative conditions{crop_str}."
        next_action = "Continue regular soil monitoring and ensure mulching to conserve root zone moisture."
        if request.soil_moisture < 35.0:
            next_action = "Initiate drip irrigation immediately to restore root zone moisture."

        farmer_summary = (
            f"Current soil moisture is {request.soil_moisture}% ({moisture_state.lower()}). "
            f"{irrigation_advice} Soil pH ({request.soil_ph}) is within a manageable range."
        )

        return SensorAnalysisRawResult(
            soil_interpretation=soil_interp,
            crop_implications=crop_imp,
            irrigation_advice=irrigation_advice,
            possible_risks=risks if risks else ["No immediate environmental or moisture stress detected."],
            recommended_next_action=next_action,
            farmer_summary=farmer_summary,
            provider_name="mock",
            model_name="mock-v1",
        )
