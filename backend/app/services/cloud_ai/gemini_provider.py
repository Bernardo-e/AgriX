import base64
import json
import logging
from typing import Any, Dict, Optional
import urllib.error
import urllib.request

from app.core.config import settings
from app.services.cloud_ai.provider import (
    CloudDiagnosisException,
    CloudDiagnosisProvider,
    CloudDiagnosisRequest,
    CloudDiagnosisRawResult,
    CloudProviderTimeoutException,
    CloudProviderUnavailableException,
    CloudResponseInvalidException,
)

logger = logging.getLogger("agrix.cloud_ai.gemini")


class GeminiDiagnosisProvider(CloudDiagnosisProvider):
    """
    Concrete Cloud AI Provider implementing Google Gemini Multimodal Vision REST API.
    Uses standard async-safe HTTP execution with strict timeout and prompt safety guardrails.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: Optional[str] = None,
        timeout_sec: Optional[float] = None,
    ):
        self.api_key = api_key or settings.cloud_ai_key
        self.model_name = model_name or settings.cloud_ai_model
        self.timeout_sec = timeout_sec or settings.cloud_ai_timeout_sec

    async def is_available(self) -> bool:
        return bool(self.api_key and self.api_key.strip())

    async def diagnose(self, request: CloudDiagnosisRequest) -> CloudDiagnosisRawResult:
        if not await self.is_available():
            raise CloudProviderUnavailableException(
                "Gemini Cloud AI API key is not configured on the backend. "
                "Set AGRIX_CLOUD_AI_KEY environment variable."
            )

        # 1. Base64 encode image payload
        b64_image = base64.b64encode(request.image_bytes).decode("utf-8")

        # 2. Build system instructions and prompt
        from app.data.metadata import get_crop_by_id
        crop_meta = get_crop_by_id(request.crop_id)
        crop_name = crop_meta["name"] if crop_meta else request.crop_id.capitalize()
        disease_options = ""
        if crop_meta and "diseases" in crop_meta:
            disease_options = "ALLOWED DISEASE CLASSES FOR " + crop_name.upper() + " (Select disease_id ONLY from this list):\n"
            disease_options += "\n".join(f"• ID {d['id']}: {d['name']}" for d in crop_meta["diseases"])

        system_prompt = (
            f"You are an expert plant pathologist and agricultural intelligence assistant for the AgriX system.\n"
            f"Analyze the provided plant leaf image and identify the disease according to the AgriX catalog.\n\n"
            f"{disease_options}\n\n"
            f"STRICT AGRICULTURAL SAFETY RULES:\n"
            f"1. DO NOT prescribe chemical dosages or invent pesticide brand names.\n"
            f"2. DO NOT claim 100% expert certainty if symptoms are ambiguous.\n"
            f"3. Recommendations must focus on Integrated Pest Management (IPM), cultural hygiene, sanitation, and KVK consultation.\n"
            f"4. Select the disease_id that best matches the visual symptoms from the allowed list above.\n"
            f"5. Return strictly valid JSON with no markdown wrapping.\n"
            f"Response Schema:\n"
            f"{{\n"
            f'  "crop_id": "{request.crop_id}",\n'
            f'  "disease_id": <int matching one of the allowed IDs above>,\n'
            f'  "disease_name": "<exact name matching the selected ID>",\n'
            f'  "confidence": <float 0.0..1.0>,\n'
            f'  "visual_reasoning": "<concise explanation of visible leaf lesions and patterns>",\n'
            f'  "symptoms": ["<symptom 1>", "<symptom 2>"],\n'
            f'  "immediate_actions": ["<cultural action 1>", "<cultural action 2>"],\n'
            f'  "prevention": ["<prevention 1>", "<prevention 2>"],\n'
            f'  "monitoring": ["<monitoring tip 1>"],\n'
            f'  "expert_escalation": "<KVK contact advice>",\n'
            f'  "safety_note": "<safety disclaimer>"\n'
            f"}}"
        )

        user_content = f"Target crop: {crop_name} ({request.crop_id}). Local TFLite hint: {request.local_disease_id} (confidence: {request.local_confidence}). Language: {request.language}."

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent?key={self.api_key}"

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": f"{system_prompt}\n\n{user_content}"},
                        {
                            "inline_data": {
                                "mime_type": request.mime_type,
                                "data": b64_image,
                            }
                        },
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json",
            },
        }

        # 3. Execute HTTP request with timeout
        json_bytes = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url=url,
            data=json_bytes,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            # Run in thread pool to avoid blocking async event loop
            import asyncio
            loop = asyncio.get_running_loop()
            raw_response_bytes = await loop.run_in_executor(
                None,
                lambda: self._execute_http(req, self.timeout_sec),
            )
            resp_json = json.loads(raw_response_bytes.decode("utf-8"))
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore") if e.fp else str(e)
            logger.error("Gemini API HTTP Error %d: %s", e.code, err_body)
            if e.code == 429:
                raise CloudDiagnosisException("Gemini API rate limit exceeded.", error_code="RATE_LIMIT_EXCEEDED")
            elif e.code in (401, 403):
                raise CloudProviderUnavailableException(f"Gemini API authentication failed: {err_body}")
            else:
                raise CloudDiagnosisException(f"Gemini API returned HTTP {e.code}: {err_body}")
        except urllib.error.URLError as e:
            if isinstance(e.reason, TimeoutError) or "timed out" in str(e.reason).lower():
                raise CloudProviderTimeoutException(f"Gemini API timed out after {self.timeout_sec}s")
            raise CloudDiagnosisException(f"Gemini network connection error: {e.reason}")
        except TimeoutError:
            raise CloudProviderTimeoutException(f"Gemini API timed out after {self.timeout_sec}s")
        except Exception as e:
            logger.error("Unexpected error during Gemini diagnosis: %s", str(e), exc_info=True)
            raise CloudDiagnosisException(f"Gemini diagnosis failed: {str(e)}")

        # 4. Parse candidates from Gemini response
        try:
            candidates = resp_json.get("candidates", [])
            if not candidates:
                raise CloudResponseInvalidException("Gemini API returned no candidates.")

            part_text = candidates[0]["content"]["parts"][0]["text"]
            model_data = json.loads(part_text)

            return CloudDiagnosisRawResult(
                crop_id=str(model_data.get("crop_id", request.crop_id)).lower(),
                disease_id=int(model_data["disease_id"]),
                disease_name=str(model_data.get("disease_name", "Identified Condition")),
                confidence=float(model_data.get("confidence", 0.85)),
                visual_reasoning=str(model_data.get("visual_reasoning", "")),
                symptoms=model_data.get("symptoms"),
                immediate_actions=model_data.get("immediate_actions"),
                prevention=model_data.get("prevention"),
                monitoring=model_data.get("monitoring"),
                expert_escalation=model_data.get("expert_escalation"),
                safety_note=model_data.get("safety_note"),
                provider_name=self.model_name,
                model_name=self.model_name,
            )
        except (KeyError, ValueError, json.JSONDecodeError) as e:
            logger.error("Failed to parse Gemini structured JSON: %s", str(e))
            raise CloudResponseInvalidException(f"Gemini response could not be parsed: {str(e)}")

    def _execute_http(self, req: urllib.request.Request, timeout: float) -> bytes:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.read()
