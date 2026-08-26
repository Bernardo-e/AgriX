from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers.advisory import router as advisory_router
from app.routers.cloud_diagnosis import router as cloud_diagnosis_router
from app.routers.crops import diseases_router
from app.routers.crops import router as crops_router
from app.routers.diagnosis import router as diagnosis_router
from app.routers.system import router as system_router


app = FastAPI(
    title="AgriX Backend",
    version="1.0.0",
    description="Backend API companion for the AgriX agricultural intelligence platform",
)

# Allow Android/web clients to communicate with the backend.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API Routers
app.include_router(system_router)
app.include_router(crops_router)
app.include_router(diseases_router)
app.include_router(diagnosis_router)
app.include_router(advisory_router)
app.include_router(cloud_diagnosis_router)


@app.get("/health", tags=["System"])
def health_check():
    return {
        "status": "ok",
        "service": "AgriX Backend",
        "version": "1.0.0",
    }


@app.get("/", tags=["System"])
def root():
    return {
        "service": "AgriX Backend",
        "status": "online",
        "docs": "/docs",
    }