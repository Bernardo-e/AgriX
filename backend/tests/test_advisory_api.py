import pytest
from fastapi.testclient import TestClient

from app.data.advisory_repository import advisory_repo
from app.data.metadata import get_all_diseases
from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


# 1. Tomato advisory (Valid request)
def test_tomato_advisory(client):
    response = client.get("/api/v1/advisory/tomato/53")
    assert response.status_code == 200
    data = response.json()

    assert data["crop"]["id"] == "tomato"
    assert data["crop"]["name"] == "Tomato"
    assert data["disease"]["id"] == 53
    assert data["disease"]["name"] == "tomato bacterial leaf spot"
    assert data["severity"] in ["low", "moderate", "high", "critical"]
    assert data["urgency"] in ["routine", "prompt", "urgent"]
    assert isinstance(data["summary"], str) and len(data["summary"]) > 20
    assert isinstance(data["immediate_actions"], list) and len(data["immediate_actions"]) >= 2
    assert isinstance(data["prevention"], list) and len(data["prevention"]) >= 2
    assert isinstance(data["monitoring"], list) and len(data["monitoring"]) >= 2
    assert isinstance(data["expert_escalation"], str) and len(data["expert_escalation"]) > 10
    assert isinstance(data["disclaimer"], str) and "AgriX advisory" in data["disclaimer"]


# 2. Another crop advisory (Apple & Rice & Wheat)
def test_other_crop_advisories(client):
    # Apple Black Rot (ID 0)
    resp_apple = client.get("/api/v1/advisory/apple/0")
    assert resp_apple.status_code == 200
    apple_data = resp_apple.json()
    assert apple_data["crop"]["id"] == "apple"
    assert apple_data["disease"]["id"] == 0
    assert apple_data["disease"]["name"] == "apple black rot"

    # Rice Blast (ID 43)
    resp_rice = client.get("/api/v1/advisory/rice/43")
    assert resp_rice.status_code == 200
    rice_data = resp_rice.json()
    assert rice_data["crop"]["id"] == "rice"
    assert rice_data["disease"]["id"] == 43
    assert rice_data["disease"]["name"] == "rice blast"

    # Wheat Black Chaff (ID 60)
    resp_wheat = client.get("/api/v1/advisory/wheat/60")
    assert resp_wheat.status_code == 200
    wheat_data = resp_wheat.json()
    assert wheat_data["crop"]["id"] == "wheat"
    assert wheat_data["disease"]["id"] == 60


# 3. Every disease (all 71 classes, 0..70) has a valid advisory entry
def test_all_71_diseases_have_advisories(client):
    all_diseases = get_all_diseases()["diseases"]
    assert len(all_diseases) == 71

    for item in all_diseases:
        d_id = item["id"]
        c_id = item["crop_id"]
        resp = client.get(f"/api/v1/advisory/{c_id}/{d_id}")
        assert resp.status_code == 200, f"Failed for disease {d_id} in crop {c_id}: {resp.text}"
        data = resp.json()
        assert data["crop"]["id"] == c_id
        assert data["disease"]["id"] == d_id
        assert data["disease"]["name"] == item["name"]
        assert len(data["immediate_actions"]) > 0
        assert len(data["prevention"]) > 0
        assert len(data["monitoring"]) > 0


# 4. Invalid crop rejection (404)
def test_invalid_crop_advisory_rejection(client):
    response = client.get("/api/v1/advisory/invalid_crop_xyz/53")
    assert response.status_code == 404
    assert "not found in supported crops" in response.json()["detail"]


# 5. Invalid disease ID rejection (404)
def test_invalid_disease_id_advisory_rejection(client):
    response = client.get("/api/v1/advisory/tomato/999")
    assert response.status_code == 404
    assert "Disease ID 999 is invalid" in response.json()["detail"]


# 6. Crop and disease mismatch rejection (400)
def test_crop_disease_mismatch_advisory_rejection(client):
    # Disease 70 is Zucchini yellow mosaic virus, requesting it under Tomato must fail
    response = client.get("/api/v1/advisory/tomato/70")
    assert response.status_code == 400
    detail = response.json()["detail"]
    assert "belongs to crop 'Zucchini'" in detail
    assert "not 'Tomato'" in detail


# 7. CONFIDENT diagnostic status response
def test_confident_diagnostic_status_advisory(client):
    response = client.get("/api/v1/advisory/tomato/53?confidence=0.92&diagnostic_status=CONFIDENT")
    assert response.status_code == 200
    data = response.json()
    assert data["crop"]["id"] == "tomato"
    assert data["disease"]["id"] == 53
    assert data["diagnostic_context"] is not None
    assert "CONFIDENT" in data["diagnostic_context"]


# 8. MODERATE_CONFIDENCE response explicitly recommends visual verification
def test_moderate_confidence_advisory(client):
    response = client.get("/api/v1/advisory/tomato/53?confidence=0.618&diagnostic_status=MODERATE_CONFIDENCE")
    assert response.status_code == 200
    data = response.json()
    assert data["crop"]["id"] == "tomato"
    assert data["disease"]["id"] == 53
    assert data["diagnostic_context"] is not None
    assert "Visual symptom verification is recommended" in data["diagnostic_context"]


# 9. LOW_CONFIDENCE response provides cautious guidance and multi-angle check
def test_low_confidence_advisory(client):
    response = client.get("/api/v1/advisory/tomato/53?confidence=0.35&diagnostic_status=LOW_CONFIDENCE")
    assert response.status_code == 200
    data = response.json()
    assert data["crop"]["id"] == "tomato"
    assert data["disease"]["id"] == 53
    assert data["diagnostic_context"] is not None
    assert "Cautious guidance provided" in data["diagnostic_context"]
    assert "clearer or multiple images" in data["diagnostic_context"]


# 10. UNKNOWN_OR_UNCERTAIN safe response
def test_unknown_or_uncertain_safe_response(client):
    response = client.get("/api/v1/advisory/tomato/53?confidence=0.10&diagnostic_status=UNKNOWN_OR_UNCERTAIN")
    assert response.status_code == 200
    data = response.json()
    assert data["crop"]["id"] == "tomato"
    assert data["disease"]["id"] == 53
    # Check that disease-specific treatment is not presented as confirmed
    assert "Diagnosis is uncertain. Capture a clearer image" in data["summary"]
    assert any("Do not apply chemical treatments" in action for action in data["immediate_actions"])
    assert "diagnostic_context" in data
    assert "UNKNOWN_OR_UNCERTAIN" in data["diagnostic_context"]


# 11. Implicit status inferred from confidence score when diagnostic_status omitted
def test_implicit_confidence_score_resolution(client):
    # confidence = 0.85 -> inferred CONFIDENT
    resp_conf = client.get("/api/v1/advisory/tomato/53?confidence=0.85")
    assert resp_conf.status_code == 200
    assert "CONFIDENT" in resp_conf.json()["diagnostic_context"]

    # confidence = 0.60 -> inferred MODERATE_CONFIDENCE
    resp_mod = client.get("/api/v1/advisory/tomato/53?confidence=0.60")
    assert resp_mod.status_code == 200
    assert "Visual symptom verification is recommended" in resp_mod.json()["diagnostic_context"]

    # confidence = 0.30 -> inferred LOW_CONFIDENCE
    resp_low = client.get("/api/v1/advisory/tomato/53?confidence=0.30")
    assert resp_low.status_code == 200
    assert "Cautious guidance provided" in resp_low.json()["diagnostic_context"]

    # confidence = 0.0 -> inferred UNKNOWN_OR_UNCERTAIN
    resp_unc = client.get("/api/v1/advisory/tomato/53?confidence=0.0")
    assert resp_unc.status_code == 200
    assert "Diagnosis is uncertain" in resp_unc.json()["summary"]


# 12. Confidence range validation (422)
def test_confidence_range_validation(client):
    # Confidence > 1.0
    resp_high = client.get("/api/v1/advisory/tomato/53?confidence=1.5")
    assert resp_high.status_code == 422

    # Confidence < 0.0
    resp_low = client.get("/api/v1/advisory/tomato/53?confidence=-0.5")
    assert resp_low.status_code == 422


# 13. Advisory catalog count = 71
def test_advisory_catalog_summary(client):
    response = client.get("/api/v1/advisory")
    assert response.status_code == 200
    data = response.json()
    assert data["total_advisories"] == 71
    assert data["covered_diseases"] == 71


# 14. Crop-specific advisory listing
def test_crop_specific_advisory_listing(client):
    response = client.get("/api/v1/advisory?crop_id=tomato")
    assert response.status_code == 200
    data = response.json()
    assert data["crop_id"] == "tomato"
    assert data["crop_name"] == "Tomato"
    assert data["total_advisories"] == 7
    assert data["covered_diseases"] == 7
    assert len(data["diseases"]) == 7

    disease_ids = [d["id"] for d in data["diseases"]]
    assert set(disease_ids) == {53, 54, 55, 56, 57, 58, 59}


# 15. Invalid crop in advisory summary filter (404)
def test_invalid_crop_filter_advisory_summary(client):
    response = client.get("/api/v1/advisory?crop_id=nonexistent_crop")
    assert response.status_code == 404
    assert "not found in supported crops" in response.json()["detail"]


# 16. Repository integrity check
def test_advisory_repo_integrity():
    assert advisory_repo.total_count == 71
    for i in range(71):
        entry = advisory_repo.get_raw_entry(i)
        assert entry is not None
        assert entry["disease_id"] == i
        assert "summary" in entry
        assert "immediate_actions" in entry
        assert "prevention" in entry
        assert "monitoring" in entry
        assert "expert_escalation" in entry


# 17. OpenAPI schema verification
def test_openapi_schema_contains_advisory_tag(client):
    response = client.get("/openapi.json")
    assert response.status_code == 200
    schema = response.json()

    assert "/api/v1/advisory" in schema["paths"]
    assert "/api/v1/advisory/{crop_id}/{disease_id}" in schema["paths"]

    advisory_item_op = schema["paths"]["/api/v1/advisory/{crop_id}/{disease_id}"]["get"]
    assert "Advisory" in advisory_item_op["tags"]
    assert "deterministic" in advisory_item_op["description"].lower()
