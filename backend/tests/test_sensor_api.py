import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.cloud_ai.factory import set_custom_cloud_provider
from app.services.cloud_ai.mock_provider import MockDiagnosisProvider


@pytest.fixture(autouse=True)
def reset_provider():
    set_custom_cloud_provider(MockDiagnosisProvider(mode="success", delay_sec=0.0))
    yield
    set_custom_cloud_provider(None)


@pytest.fixture
def client():
    return TestClient(app)


def test_sensor_analysis_success(client):
    """Verify successful sensor telemetry agronomic analysis."""
    payload = {
        "source": "SIMULATED_BLE",
        "temperature": 28.5,
        "humidity": 62.0,
        "soil_moisture": 47.0,
        "soil_ph": 6.7,
        "crop_name": "Tomato",
        "soil_type": "Sandy Loam",
        "language": "en",
    }
    response = client.post("/api/v1/sensor-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["provider"] == "mock"
    assert "soil_interpretation" in data
    assert "irrigation_advice" in data
    assert "farmer_summary" in data
    assert len(data["possible_risks"]) >= 0


def test_sensor_analysis_low_moisture_irrigation(client):
    """Verify that low soil moisture triggers urgent irrigation advice."""
    payload = {
        "source": "SIMULATED_BLE",
        "temperature": 31.0,
        "humidity": 48.0,
        "soil_moisture": 22.0,  # Low moisture < 35%
        "soil_ph": 6.5,
        "crop_name": "Wheat",
    }
    response = client.post("/api/v1/sensor-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "irrigation" in data["irrigation_advice"].lower()
    assert any("moisture" in r.lower() for r in data["possible_risks"])


def test_sensor_analysis_invalid_ranges(client):
    """Verify validation rejects physically out-of-range sensor readings."""
    # Invalid moisture > 100%
    res1 = client.post("/api/v1/sensor-analysis", json={
        "temperature": 25.0,
        "humidity": 50.0,
        "soil_moisture": 150.0,
        "soil_ph": 7.0,
    })
    assert res1.status_code == 422

    # Invalid pH < 3.0
    res2 = client.post("/api/v1/sensor-analysis", json={
        "temperature": 25.0,
        "humidity": 50.0,
        "soil_moisture": 45.0,
        "soil_ph": 1.2,
    })
    assert res2.status_code == 422

    # Invalid temperature > 55°C
    res3 = client.post("/api/v1/sensor-analysis", json={
        "temperature": 85.0,
        "humidity": 50.0,
        "soil_moisture": 45.0,
        "soil_ph": 6.5,
    })
    assert res3.status_code == 422


def test_sensor_analysis_provider_unavailable(client):
    """Verify 503 fallback response when provider is offline."""
    set_custom_cloud_provider(MockDiagnosisProvider(is_available_override=False))

    payload = {
        "source": "SIMULATED_BLE",
        "temperature": 26.0,
        "humidity": 60.0,
        "soil_moisture": 50.0,
        "soil_ph": 6.8,
    }
    response = client.post("/api/v1/sensor-analysis", json=payload)
    assert response.status_code == 503
    data = response.json()
    assert data["fallback_to_local"] is True
    assert data["error_code"] == "PROVIDER_UNAVAILABLE"


def test_sensor_analysis_provider_timeout(client):
    """Verify 504 fallback response when provider times out."""
    set_custom_cloud_provider(MockDiagnosisProvider(mode="timeout"))

    payload = {
        "source": "SIMULATED_BLE",
        "temperature": 26.0,
        "humidity": 60.0,
        "soil_moisture": 50.0,
        "soil_ph": 6.8,
    }
    response = client.post("/api/v1/sensor-analysis", json=payload)
    assert response.status_code == 504
    data = response.json()
    assert data["fallback_to_local"] is True
    assert data["error_code"] == "PROVIDER_TIMEOUT"
