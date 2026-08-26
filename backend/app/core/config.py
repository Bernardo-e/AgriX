import os
from pathlib import Path
from typing import List
from dotenv import load_dotenv
from pydantic import BaseModel, Field

# Load backend/.env if present
env_path = Path(__file__).resolve().parent.parent.parent / ".env"
load_dotenv(dotenv_path=env_path)


class Settings(BaseModel):
    cloud_ai_provider: str = Field(
        default_factory=lambda: os.getenv("AGRIX_CLOUD_AI_PROVIDER", "gemini").lower()
    )
    cloud_ai_model: str = Field(
        default_factory=lambda: os.getenv("AGRIX_CLOUD_AI_MODEL", "gemini-3.1-flash-lite")
    )
    cloud_ai_key: str = Field(
        default_factory=lambda: os.getenv("AGRIX_CLOUD_AI_KEY", "").strip()
    )
    cloud_ai_timeout_sec: float = Field(
        default_factory=lambda: float(os.getenv("AGRIX_CLOUD_AI_TIMEOUT_SEC", "8.0"))
    )
    max_image_size_bytes: int = 4 * 1024 * 1024  # 4 MB
    allowed_mime_types: List[str] = ["image/jpeg", "image/png", "image/webp"]
    supported_languages: List[str] = ["en", "hi", "te", "ta", "kn", "ml"]


settings = Settings()
