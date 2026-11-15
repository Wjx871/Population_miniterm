"""Container-ready adaptation of D:/ocr-stub/app.py; no local credentials or data.

The provider is Baidu cloud OCR. No local model or GPU is needed.
"""
from __future__ import annotations

import base64
import io
import logging
import os
import re
import threading
import time
import uuid
from datetime import date
from typing import Optional

import requests
from fastapi import FastAPI, File, Form, HTTPException, Request
from PIL import Image, UnidentifiedImageError

log = logging.getLogger("ocr-baidu")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
AK = os.getenv("OCR_AK", "").strip()
SK = os.getenv("OCR_SK", "").strip()
HTTP_PORT = int(os.getenv("OCR_PORT", "8866"))
OCR_TIMEOUT = int(os.getenv("OCR_TIMEOUT", "30"))
MAX_BYTES = 10 * 1024 * 1024
BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
BAIDU_IDCARD_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/idcard"
_token_cache = {"value": None, "expires_at": 0.0}
_token_lock = threading.Lock()
app = FastAPI(title="Population ID card OCR", version="1.1.0", docs_url=None, redoc_url=None)


@app.middleware("http")
async def trace_request(request: Request, call_next):
    supplied = request.headers.get("X-Trace-Id", "")
    request.state.trace_id = supplied if re.fullmatch(r"[A-Za-z0-9_-]{1,64}", supplied) else str(uuid.uuid4())
    response = await call_next(request)
    response.headers["X-Trace-Id"] = request.state.trace_id
    return response


def fetch_access_token() -> str:
    if not AK or not SK:
        raise RuntimeError("OCR credentials are not configured")
    with _token_lock:
        now = time.time()
        if _token_cache["value"] and now < _token_cache["expires_at"] - 300:
            return str(_token_cache["value"])
        response = requests.get(BAIDU_TOKEN_URL, params={
            "grant_type": "client_credentials", "client_id": AK, "client_secret": SK,
        }, timeout=15)
        if response.status_code != 200:
            raise RuntimeError("OCR authentication failed")
        data = response.json()
        if not data.get("access_token"):
            raise RuntimeError("OCR authentication failed")
        _token_cache.update(value=data["access_token"], expires_at=now + int(data.get("expires_in", 2592000)))
        return str(_token_cache["value"])


def to_iso_date(text: str) -> Optional[str]:
    compact = (text or "").strip().replace(" ", "")
    for pattern in (r"(\d{4})年(\d{1,2})月(\d{1,2})日", r"(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})", r"^(\d{4})(\d{2})(\d{2})$"):
        match = re.search(pattern, compact)
        if match:
            try:
                return date(*map(int, match.groups())).isoformat()
            except ValueError:
                return None
    return None


def normalize_baidu_response(data: dict) -> tuple[dict, str]:
    words = data.get("words_result") or {}
    status = data.get("image_status") or "unknown"
    fields = dict.fromkeys(("idCard", "name", "birthDate", "gender", "ethnicity", "address"))
    # Preserves the old adapter's heuristic; this is NOT a measured OCR probability.
    fields.update(confidence=0.95 if status == "normal" else 0.50,
                  _confidence_source="image_status_heuristic")
    if status == "normal":
        def get(key):
            return (words.get(key) or {}).get("words")
        fields.update(idCard=get("公民身份号码"), name=get("姓名"),
                      birthDate=to_iso_date(get("出生")), gender={"男": "M", "女": "F"}.get(get("性别")),
                      ethnicity=get("民族"), address=get("住址"))
    return fields, status


def call_baidu_idcard(file_bytes: bytes, side: str) -> dict:
    token = fetch_access_token()
    response = requests.post(BAIDU_IDCARD_URL, params={"access_token": token},
                             data={"id_card_side": side, "image": base64.b64encode(file_bytes).decode("ascii")},
                             timeout=OCR_TIMEOUT)
    response.raise_for_status()
    data = response.json()
    if "error_code" in data:
        raise RuntimeError("OCR provider rejected the request")
    return data


@app.get("/health")
def health() -> dict:
    # Liveness/configuration only; does not claim the provider is reachable.
    return {"ok": True, "credentials_configured": bool(AK and SK), "engine": "baidu-ocr-idcard-v1"}


# Synchronous requests/Pillow work runs in FastAPI's thread pool, not the event loop.
@app.post("/recognize/idcard")
def recognize(request: Request, file: bytes = File(...), side: str = Form("front")) -> dict:
    if side != "front":
        raise HTTPException(400, "only the ID card front is supported")
    if not file:
        raise HTTPException(400, "file is empty")
    if len(file) > MAX_BYTES:
        raise HTTPException(413, "image exceeds 10 MiB")
    try:
        with Image.open(io.BytesIO(file)) as image:
            image.verify()
    except (UnidentifiedImageError, OSError, SyntaxError, Image.DecompressionBombError):
        raise HTTPException(400, "invalid image") from None
    trace_id = getattr(request.state, "trace_id", "local")
    started = time.monotonic()
    if not AK or not SK:
        raise HTTPException(503, "OCR credentials are not configured")
    try:
        raw = call_baidu_idcard(file, side)
        fields, status = normalize_baidu_response(raw)
    except requests.Timeout:
        log.warning("ocr_failed traceId=%s category=timeout", trace_id)
        raise HTTPException(504, "OCR provider timed out") from None
    except (requests.RequestException, RuntimeError, ValueError, TypeError, AttributeError):
        log.warning("ocr_failed traceId=%s category=provider", trace_id)
        raise HTTPException(502, "OCR provider request failed") from None
    elapsed = int((time.monotonic() - started) * 1000)
    log.info("ocr_complete traceId=%s accepted=%s elapsedMs=%d bytes=%d", trace_id, status == "normal", elapsed, len(file))
    if status != "normal":
        raise HTTPException(422, "image not recognized as ID card front")
    return {"result": {**fields, "_elapsed_ms": elapsed, "_engine": "baidu-ocr-idcard-v1"}}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=HTTP_PORT)
