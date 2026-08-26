import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.data.diagnosis_repository import diagnosis_repo


@pytest.fixture(autouse=True)
def clean_repository():
    """Ensure clean in-memory storage before every test."""
    diagnosis_repo.clear()
    yield
    diagnosis_repo.clear()


@pytest.fixture
def client():
    return TestClient(app)


# 1. Valid diagnosis creation
def test_valid_diagnosis_creation(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": 0.618,
        "diagnostic_status": "MODERATE_CONFIDENCE",
        "source": "on_device_tflite",
        "image_id": "img_test_123",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["status"] == "recorded"
    assert data["id"].startswith("diag_")
    assert data["crop"]["id"] == "tomato"
    assert data["crop"]["name"] == "Tomato"
    assert data["disease"]["id"] == 53
    assert data["disease"]["name"] == "tomato bacterial leaf spot"
    assert data["confidence"] == 0.618
    assert data["diagnostic_status"] == "MODERATE_CONFIDENCE"
    assert data["source"] == "on_device_tflite"
    assert data["image_id"] == "img_test_123"
    assert "created_at" in data


# 2. Invalid crop rejection
def test_invalid_crop_rejection(client):
    payload = {
        "crop_id": "invalid_crop_xyz",
        "disease_id": 53,
        "confidence": 0.85,
        "diagnostic_status": "CONFIDENT",
        "source": "on_device_tflite",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 400
    assert "not found in supported crops" in response.json()["detail"]


# 3. Invalid disease ID (out of range)
def test_invalid_disease_id_out_of_range(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 999,
        "confidence": 0.85,
        "diagnostic_status": "CONFIDENT",
        "source": "on_device_tflite",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 422  # Pydantic validation error (le=70)


# 4. Disease / Crop mismatch (e.g. disease 70 is Zucchini, but requested with Tomato)
def test_disease_crop_mismatch_rejection(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 70,  # Zucchini yellow mosaic virus
        "confidence": 0.90,
        "diagnostic_status": "CONFIDENT",
        "source": "on_device_tflite",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 400
    detail = response.json()["detail"]
    assert "belongs to crop 'Zucchini'" in detail
    assert "not 'Tomato'" in detail


# 5. Invalid confidence > 1.0
def test_invalid_confidence_greater_than_one(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": 1.5,
        "diagnostic_status": "CONFIDENT",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 422


# 6. Invalid confidence < 0.0
def test_invalid_confidence_less_than_zero(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": -0.2,
        "diagnostic_status": "CONFIDENT",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 422


# 7. Invalid diagnostic status
def test_invalid_diagnostic_status(client):
    payload = {
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": 0.80,
        "diagnostic_status": "SUPER_CERTAIN_INVALID",
    }
    response = client.post("/api/v1/diagnoses", json=payload)
    assert response.status_code == 422


# 8. Diagnosis history
def test_diagnosis_history(client):
    # Record 2 valid diagnoses
    client.post("/api/v1/diagnoses", json={
        "crop_id": "apple",
        "disease_id": 0,
        "confidence": 0.92,
        "diagnostic_status": "CONFIDENT",
    })
    client.post("/api/v1/diagnoses", json={
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": 0.65,
        "diagnostic_status": "MODERATE_CONFIDENCE",
    })

    response = client.get("/api/v1/diagnoses")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 2
    assert len(data["diagnoses"]) == 2
    # Reverse chronological check (tomato is newest)
    assert data["diagnoses"][0]["crop"]["id"] == "tomato"
    assert data["diagnoses"][1]["crop"]["id"] == "apple"


# 9. Crop filtering in diagnosis history
def test_crop_filtering(client):
    client.post("/api/v1/diagnoses", json={
        "crop_id": "apple",
        "disease_id": 0,
        "confidence": 0.92,
        "diagnostic_status": "CONFIDENT",
    })
    client.post("/api/v1/diagnoses", json={
        "crop_id": "tomato",
        "disease_id": 53,
        "confidence": 0.65,
        "diagnostic_status": "MODERATE_CONFIDENCE",
    })

    # Filter for tomato only
    response = client.get("/api/v1/diagnoses?crop_id=tomato")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 1
    assert data["diagnoses"][0]["crop"]["id"] == "tomato"

    # Filter for apple only
    response = client.get("/api/v1/diagnoses?crop_id=apple")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 1
    assert data["diagnoses"][0]["crop"]["id"] == "apple"

    # Filter for crop with no records
    response = client.get("/api/v1/diagnoses?crop_id=potato")
    assert response.status_code == 200
    assert response.json()["total"] == 0


# 10. Diagnosis lookup by ID
def test_diagnosis_lookup_by_id(client):
    create_resp = client.post("/api/v1/diagnoses", json={
        "crop_id": "rice",
        "disease_id": 43,
        "confidence": 0.77,
        "diagnostic_status": "CONFIDENT",
    })
    diag_id = create_resp.json()["id"]

    lookup_resp = client.get(f"/api/v1/diagnoses/{diag_id}")
    assert lookup_resp.status_code == 200
    data = lookup_resp.json()
    assert data["id"] == diag_id
    assert data["crop"]["id"] == "rice"
    assert data["disease"]["id"] == 43
    assert data["confidence"] == 0.77


# 11. Unknown diagnosis ID lookup (404)
def test_unknown_diagnosis_id_404(client):
    response = client.get("/api/v1/diagnoses/diag_nonexistent_999")
    assert response.status_code == 404
    assert "Diagnosis 'diag_nonexistent_999' not found" in response.json()["detail"]


# 12. Existing endpoints remain fully functional
def test_existing_endpoints_remain_functional(client):
    # GET /
    r_root = client.get("/")
    assert r_root.status_code == 200
    assert r_root.json()["status"] == "online"

    # GET /health
    r_health = client.get("/health")
    assert r_health.status_code == 200
    assert r_health.json()["status"] == "ok"

    # GET /api/v1/system/status
    r_sys = client.get("/api/v1/system/status")
    assert r_sys.status_code == 200
    assert r_sys.json()["supported_crops"] == 29
    assert r_sys.json()["supported_diseases"] == 71

    # GET /api/v1/crops
    r_crops = client.get("/api/v1/crops")
    assert r_crops.status_code == 200
    assert r_crops.json()["total_crops"] == 29

    # GET /api/v1/crops/tomato
    r_tomato = client.get("/api/v1/crops/tomato")
    assert r_tomato.status_code == 200
    assert r_tomato.json()["name"] == "Tomato"

    # GET /api/v1/diseases
    r_diseases = client.get("/api/v1/diseases")
    assert r_diseases.status_code == 200
    assert r_diseases.json()["total_diseases"] == 71
