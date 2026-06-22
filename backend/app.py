"""FastAPI service: paste a YouTube/Instagram URL, get an AI-vs-real estimate."""

from __future__ import annotations

import os
import shutil
import tempfile
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, HttpUrl, field_validator

from analyzer import analyze_video_file
from downloader import DownloadError, UnsupportedUrlError, download_video, is_supported_url

API_KEY = os.environ.get("LINK_ANALYSIS_API_KEY", "").strip()
TEMP_ROOT = Path(os.environ.get("TEMP_ROOT", tempfile.gettempdir())) / "ai-video-detector"


class AnalyzeUrlRequest(BaseModel):
    url: HttpUrl

    @field_validator("url")
    @classmethod
    def validate_supported_host(cls, value: HttpUrl) -> HttpUrl:
        if not is_supported_url(str(value)):
            raise ValueError("Only YouTube and Instagram URLs are supported.")
        return value


def _check_api_key(provided: str | None) -> None:
    if API_KEY and provided != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key.")


@asynccontextmanager
async def lifespan(_: FastAPI):
    TEMP_ROOT.mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(
    title="AI Video Check Link API",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/analyze-url")
def analyze_url(
    body: AnalyzeUrlRequest,
    x_api_key: str | None = Header(default=None),
) -> dict:
    _check_api_key(x_api_key)
    url = str(body.url)
    job_dir = TEMP_ROOT / str(uuid.uuid4())
    job_dir.mkdir(parents=True, exist_ok=True)
    video_path: Path | None = None

    try:
        video_path, title, _duration_ms = download_video(url, job_dir)
        result = analyze_video_file(video_path, title, url)
        return result.to_dict()
    except UnsupportedUrlError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except DownloadError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Analysis failed: {exc}",
        ) from exc
    finally:
        shutil.rmtree(job_dir, ignore_errors=True)
