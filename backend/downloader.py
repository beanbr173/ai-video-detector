"""Download video from a social URL using yt-dlp."""

from __future__ import annotations

import re
from pathlib import Path

import yt_dlp

SUPPORTED_HOSTS = (
    "youtube.com",
    "youtu.be",
    "instagram.com",
    "instagr.am",
)

MAX_DURATION_SECONDS = 600
MAX_FILE_BYTES = 200 * 1024 * 1024


class UnsupportedUrlError(ValueError):
    pass


class DownloadError(RuntimeError):
    pass


def is_supported_url(url: str) -> bool:
    normalized = url.strip().lower()
    return any(host in normalized for host in SUPPORTED_HOSTS)


def _safe_title(title: str) -> str:
    cleaned = re.sub(r"[^\w\s.-]", "", title).strip()
    return cleaned[:120] if cleaned else "video"


def download_video(url: str, output_dir: Path) -> tuple[Path, str, int]:
    """Return (video_path, display_name, duration_ms)."""
    if not is_supported_url(url):
        raise UnsupportedUrlError(
            "Only YouTube and Instagram links are supported right now."
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    output_template = str(output_dir / "%(id)s.%(ext)s")

    ydl_opts: dict = {
        "format": "best[ext=mp4]/best[height<=720]/best",
        "outtmpl": output_template,
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "max_filesize": MAX_FILE_BYTES,
        "match_filter": yt_dlp.utils.match_filter_func(
            f"duration <= {MAX_DURATION_SECONDS}"
        ),
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if info is None:
                raise DownloadError("Could not read video metadata.")

            if "entries" in info:
                info = info["entries"][0]

            video_path = Path(ydl.prepare_filename(info))
            if not video_path.exists():
                candidates = list(output_dir.glob(f"{info.get('id', '*')}.*"))
                if not candidates:
                    raise DownloadError("Download finished but file was not found.")
                video_path = candidates[0]

            if video_path.stat().st_size > MAX_FILE_BYTES:
                video_path.unlink(missing_ok=True)
                raise DownloadError("Video exceeds the 200 MB size limit.")

            title = _safe_title(info.get("title") or info.get("id") or "video")
            duration_sec = float(info.get("duration") or 0)
            duration_ms = int(duration_sec * 1000)
            return video_path, title, duration_ms
    except yt_dlp.utils.DownloadError as exc:
        raise DownloadError(str(exc)) from exc
