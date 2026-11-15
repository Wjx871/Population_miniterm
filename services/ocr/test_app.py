"""Offline contract tests; never call the cloud provider or consume OCR credits."""
import asyncio
import io
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from PIL import Image
from starlette.requests import Request
from starlette.responses import JSONResponse
import requests

import app


class OcrContractTests(unittest.TestCase):
    def setUp(self):
        image = io.BytesIO()
        Image.new("RGB", (16, 16)).save(image, format="PNG")
        self.image = image.getvalue()
        self.request = Request({"type": "http", "headers": []})
        self.request.state.trace_id = "offline-test"

    def invoke(self):
        return app.recognize(self.request, self.image, "front")

    def test_dates_are_calendar_valid(self):
        self.assertEqual(app.to_iso_date("2000年2月29日"), "2000-02-29")
        self.assertIsNone(app.to_iso_date("20010229"))

    def test_normalization_contract(self):
        fields, status = app.normalize_baidu_response({"image_status": "normal", "words_result": {
            "姓名": {"words": "模拟姓名"}, "性别": {"words": "男"}, "出生": {"words": "20000101"}}})
        self.assertEqual((fields["name"], fields["gender"], fields["birthDate"], status),
                         ("模拟姓名", "M", "2000-01-01", "normal"))
        self.assertEqual(fields["_confidence_source"], "image_status_heuristic")

    def test_missing_credentials_are_explicit(self):
        with patch.multiple(app, AK="", SK=""), self.assertRaises(HTTPException) as error:
            self.invoke()
        self.assertEqual(error.exception.status_code, 503)

    def test_invalid_input_never_calls_provider(self):
        with patch.object(app, "call_baidu_idcard") as provider:
            for data, side in [(b"", "front"), (b"garbage", "front"), (self.image, "back")]:
                with self.assertRaises(HTTPException) as error:
                    app.recognize(self.request, data, side)
                self.assertEqual(error.exception.status_code, 400)
            provider.assert_not_called()

    def test_success_preserves_java_response_shape_without_logging_fields(self):
        with patch.multiple(app, AK="fake", SK="fake"), patch.object(app, "call_baidu_idcard", return_value={
            "image_status": "normal", "words_result": {"姓名": {"words": "PRIVATE_NAME"}}}), self.assertLogs(app.log) as logs:
            result = self.invoke()
        self.assertEqual(result["result"]["name"], "PRIVATE_NAME")
        self.assertNotIn("PRIVATE_NAME", " ".join(logs.output))
        self.assertIn("traceId=offline-test", " ".join(logs.output))

    def test_timeout_and_provider_error_never_expose_sensitive_url(self):
        for exception, status in [(requests.Timeout("access_token=secret"), 504),
                                  (requests.HTTPError("access_token=secret"), 502)]:
            with patch.multiple(app, AK="fake", SK="fake"), patch.object(app, "call_baidu_idcard", side_effect=exception), \
                    self.assertLogs(app.log) as logs, self.assertRaises(HTTPException) as error:
                self.invoke()
            self.assertEqual(error.exception.status_code, status)
            self.assertNotIn("secret", str(error.exception.detail) + " ".join(logs.output))

    def test_unrecognized_image_returns_422(self):
        with patch.multiple(app, AK="fake", SK="fake"), patch.object(app, "call_baidu_idcard", return_value={"image_status": "reversed_side"}), \
                self.assertRaises(HTTPException) as error:
            self.invoke()
        self.assertEqual(error.exception.status_code, 422)

    def test_trace_middleware_returns_safe_trace_id(self):
        request = Request({"type": "http", "headers": [(b"x-trace-id", b"bad\nvalue")]})
        async def respond(req):
            return JSONResponse({"ok": True})
        response = asyncio.run(app.trace_request(request, respond))
        self.assertRegex(response.headers["X-Trace-Id"], r"^[a-f0-9-]{36}$")

    def test_health_does_not_expose_credentials(self):
        with patch.multiple(app, AK="private-ak", SK="private-sk"):
            result = app.health()
        self.assertTrue(result["credentials_configured"])
        self.assertNotIn("private", str(result))


if __name__ == "__main__":
    unittest.main()
