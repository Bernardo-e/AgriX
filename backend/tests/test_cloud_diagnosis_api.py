import io
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.cloud_ai.factory import set_custom_cloud_provider
from app.services.cloud_ai.mock_provider import MockDiagnosisProvider


@pytest.fixture(autouse=True)
def reset_provider():
    # Set default mock provider before each test, clean up after
    set_custom_cloud_provider(MockDiagnosisProvider(mode="success"))
    yield
    set_custom_cloud_provider(None)


@pytest.fixture
def client():
    return TestClient(app)


def create_mock_image(size_bytes: int = 1024, mime: str = "image/jpeg") -> tuple:
    fake_bytes = b"\xFF\xD8\xFF" + b"\x00" * (size_bytes - 3)
    return ("test_leaf.jpg", io.BytesIO(fake_bytes), mime)


# 1. Valid cloud diagnosis
def test_valid_cloud_diagnosis(client):
    img = create_mock_image(1024, "image/jpeg")
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "local_disease_id": 54,
            "local_confidence": 0.65,
            "local_status": "MODERATE_CONFIDENCE",
            "language": "en",
            "state": "Maharashtra",
            "district": "Pune",
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["provider"] == "mock"
    assert data["diagnosis"]["crop"]["id"] == "tomato"
    assert data["diagnosis"]["disease"]["id"] == 54
    assert data["diagnosis"]["diagnostic_status"] in ["CONFIDENT", "MODERATE_CONFIDENCE"]
    assert data["visual_reasoning"] != ""
    assert len(data["advisory"]["symptoms"]) >= 1
    assert len(data["advisory"]["immediate_actions"]) >= 1
    assert len(data["advisory"]["prevention"]) >= 1
    assert len(data["advisory"]["monitoring"]) >= 1
    assert data["advisory"]["expert_escalation"] != ""
    assert data["advisory"]["safety_note"] != ""


# 2. Invalid image MIME rejection
def test_invalid_image_mime_rejection(client):
    img = ("test_doc.pdf", io.BytesIO(b"%PDF-1.4 fake content"), "application/pdf")
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "tomato"},
    )
    assert response.status_code == 400
    data = response.json()
    assert data["status"] == "error"
    assert data["error_code"] == "INVALID_IMAGE_MIME"
    assert data["fallback_to_local"] is True


# 3. Image size > 4 MB rejection
def test_image_exceeds_4mb_rejection(client):
    huge_bytes = b"\xFF\xD8\xFF" + b"\x00" * (4 * 1024 * 1024 + 100)
    img = ("large.jpg", io.BytesIO(huge_bytes), "image/jpeg")
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "tomato"},
    )
    assert response.status_code == 400
    data = response.json()
    assert data["error_code"] == "IMAGE_TOO_LARGE"
    assert data["fallback_to_local"] is True


# 4. Invalid crop ID
def test_invalid_crop_id_rejection(client):
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "dragonfruit_unsupported"},
    )
    assert response.status_code == 400
    data = response.json()
    assert data["error_code"] == "INVALID_CROP"
    assert data["fallback_to_local"] is True


# 5. Invalid local disease ID out of range
def test_invalid_local_disease_id_rejection(client):
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "local_disease_id": 999,
        },
    )
    assert response.status_code == 400
    data = response.json()
    assert data["error_code"] == "INVALID_LOCAL_DISEASE_ID"
    assert data["fallback_to_local"] is True


# 6. Invalid confidence range
def test_invalid_confidence_rejection(client):
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "local_confidence": 1.5,
        },
    )
    assert response.status_code == 400
    data = response.json()
    assert data["error_code"] == "INVALID_CONFIDENCE"
    assert data["fallback_to_local"] is True


# 7. Missing API key / Provider unavailable returns 503 with fallback
def test_missing_api_key_provider_unavailable(client):
    set_custom_cloud_provider(MockDiagnosisProvider(is_available_override=False))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "local_disease_id": 54,
            "local_confidence": 0.55,
        },
    )
    assert response.status_code == 503
    data = response.json()
    assert data["status"] == "error"
    assert data["error_code"] == "PROVIDER_UNAVAILABLE"
    assert data["fallback_to_local"] is True
    assert data["local_diagnosis_retained"]["local_disease_id"] == 54


# 8. Provider timeout returns 504 with fallback
def test_provider_timeout_fallback(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="timeout"))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "rice"},
    )
    assert response.status_code == 504
    data = response.json()
    assert data["error_code"] == "PROVIDER_TIMEOUT"
    assert data["fallback_to_local"] is True


# 9. Provider 500 error returns 500 with fallback
def test_provider_internal_error_fallback(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="server_error"))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "wheat"},
    )
    assert response.status_code == 500
    data = response.json()
    assert data["error_code"] == "PROVIDER_ERROR"
    assert data["fallback_to_local"] is True


# 10. Unknown disease ID from cloud AI rejected by validator
def test_cloud_unknown_disease_id_rejection(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="invalid_disease"))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "tomato"},
    )
    assert response.status_code == 422
    data = response.json()
    assert data["error_code"] == "INVALID_CLOUD_RESPONSE"
    assert data["fallback_to_local"] is True


# 11. Cloud disease/crop mismatch rejected by validator
def test_cloud_crop_disease_mismatch_rejection(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="crop_mismatch"))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "tomato"},
    )
    assert response.status_code == 422
    data = response.json()
    assert data["error_code"] == "INVALID_CLOUD_RESPONSE"
    assert data["fallback_to_local"] is True


# 12. Valid cloud response structure and 7-section advisory completeness
def test_cloud_advisory_structure_integrity(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="success", crop_id_override="rice", disease_id_override=43))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={"crop_id": "rice"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["diagnosis"]["crop"]["id"] == "rice"
    assert data["diagnosis"]["disease"]["id"] == 43
    assert data["diagnosis"]["disease"]["name"].lower() == "rice blast"
    assert data["advisory"]["severity"] in ["low", "moderate", "high", "critical"]
    assert data["advisory"]["urgency"] in ["routine", "prompt", "urgent"]
    assert len(data["advisory"]["symptoms"]) >= 1
    assert len(data["advisory"]["immediate_actions"]) >= 1
    assert len(data["advisory"]["prevention"]) >= 1
    assert len(data["advisory"]["monitoring"]) >= 1
    assert data["advisory"]["expert_escalation"] != ""
    assert data["advisory"]["safety_note"] != ""


# 13. Fallback retains local context
def test_fallback_retains_local_context(client):
    set_custom_cloud_provider(MockDiagnosisProvider(mode="timeout"))
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "local_disease_id": 54,
            "local_confidence": 0.58,
            "local_status": "MODERATE_CONFIDENCE",
        },
    )
    assert response.status_code == 504
    data = response.json()
    retained = data["local_diagnosis_retained"]
    assert retained["crop_id"] == "tomato"
    assert retained["local_disease_id"] == 54
    assert retained["local_confidence"] == 0.58
    assert retained["local_status"] == "MODERATE_CONFIDENCE"


# 14. Existing endpoints remain fully functional
def test_existing_endpoints_remain_functional(client):
    # Health
    assert client.get("/health").status_code == 200
    # Crops
    assert client.get("/api/v1/crops").status_code == 200
    # Diseases
    assert client.get("/api/v1/diseases").status_code == 200
    # Diagnoses
    assert client.get("/api/v1/diagnoses").status_code == 200
    # Advisory catalog
    assert client.get("/api/v1/advisory").status_code == 200
    # Crop-specific advisory
    assert client.get("/api/v1/advisory/tomato/54").status_code == 200


# 15. OpenAPI / Swagger Schema Verification
def test_openapi_schema_contains_cloud_diagnosis(client):
    response = client.get("/openapi.json")
    assert response.status_code == 200
    openapi = response.json()
    paths = openapi.get("paths", {})
    assert "/api/v1/cloud-diagnosis" in paths
    assert "post" in paths["/api/v1/cloud-diagnosis"]


# 16. Language forwarding and regional context
def test_language_and_regional_forwarding(client):
    img = create_mock_image()
    response = client.post(
        "/api/v1/cloud-diagnosis",
        files={"image": img},
        data={
            "crop_id": "tomato",
            "language": "hi",
            "state": "Maharashtra",
            "district": "Nashik",
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
