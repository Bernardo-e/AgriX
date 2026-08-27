from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class SensorAnalysisRequest(BaseModel):
    """Payload for soil and environmental sensor telemetry analysis."""
    source: str = Field(default="SIMULATED_BLE", description="Sensor telemetry source identifier")
    temperature: float = Field(..., description="Ambient / soil temperature in Celsius (10..55°C)")
    humidity: float = Field(..., description="Relative air humidity percentage (10..100%)")
    soil_moisture: float = Field(..., description="Volumetric soil moisture percentage (0..100%)")
    soil_ph: float = Field(..., description="Soil pH reading (3.0..11.0)")
    crop_name: Optional[str] = Field(default=None, description="Optional target crop context")
    soil_type: Optional[str] = Field(default=None, description="Optional soil classification context")
    language: Optional[str] = Field(default="en", description="Localization language code (en, hi, ta, te, kn, ml)")

    @field_validator("temperature")
    @classmethod
    def validate_temperature(cls, v: float) -> float:
        if not (10.0 <= v <= 55.0):
            raise ValueError(f"Temperature {v}°C is outside valid agricultural range (10..55°C)")
        return round(v, 1)

    @field_validator("humidity")
    @classmethod
    def validate_humidity(cls, v: float) -> float:
        if not (10.0 <= v <= 100.0):
            raise ValueError(f"Humidity {v}% is outside valid range (10..100%)")
        return round(v, 1)

    @field_validator("soil_moisture")
    @classmethod
    def validate_soil_moisture(cls, v: float) -> float:
        if not (0.0 <= v <= 100.0):
            raise ValueError(f"Soil moisture {v}% is outside valid range (0..100%)")
        return round(v, 1)

    @field_validator("soil_ph")
    @classmethod
    def validate_soil_ph(cls, v: float) -> float:
        if not (3.0 <= v <= 11.0):
            raise ValueError(f"Soil pH {v} is outside valid range (3.0..11.0)")
        return round(v, 2)


class SensorAnalysisResponse(BaseModel):
    """Structured agricultural analysis of sensor readings."""
    status: str = Field(default="success")
    provider: str = Field(..., description="Cloud AI provider name")
    model: str = Field(..., description="Model identifier used")
    soil_interpretation: str = Field(..., description="Interpretation of soil condition and moisture state")
    crop_implications: str = Field(..., description="Impact on crop vegetative growth and nutrient uptake")
    irrigation_advice: str = Field(..., description="Clear irrigation recommendations")
    possible_risks: List[str] = Field(default_factory=list, description="Identified agricultural risks")
    recommended_next_action: str = Field(..., description="Immediate practical step for the farmer")
    farmer_summary: str = Field(..., description="Concise, plain-language combined recommendation")
    latency_ms: int = Field(default=0, description="Inference latency in milliseconds")


class SensorAnalysisRawResult(BaseModel):
    """Internal raw analysis result from Cloud AI provider."""
    soil_interpretation: str
    crop_implications: str
    irrigation_advice: str
    possible_risks: List[str]
    recommended_next_action: str
    farmer_summary: str
    provider_name: str
    model_name: str
