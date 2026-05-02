import contextlib
import io
import json
import os
import unittest
from unittest import mock
from urllib.error import HTTPError, URLError

from scripts.deployment import send_resend_email


class SendResendEmailTest(unittest.TestCase):
  def test_parse_recipients_accepts_json_array(self):
    self.assertEqual(
        ["ops@example.com", "dev@example.com"],
        send_resend_email.parse_recipients('["ops@example.com", " dev@example.com ", ""]'),
    )

  def test_parse_recipients_accepts_comma_list(self):
    self.assertEqual(
        ["ops@example.com", "dev@example.com"],
        send_resend_email.parse_recipients("ops@example.com, dev@example.com,"),
    )

  def test_build_payload_uses_branded_sender_and_html(self):
    payload = json.loads(
        send_resend_email.build_payload(
            "SupaWave Deploy <noreply@supawave.ai>",
            ["ops@example.com"],
            "SupaWave deployed: abc123",
            "<h2>Deploy succeeded</h2>",
        )
    )

    self.assertEqual("SupaWave Deploy <noreply@supawave.ai>", payload["from"])
    self.assertEqual(["ops@example.com"], payload["to"])
    self.assertEqual("SupaWave deployed: abc123", payload["subject"])
    self.assertEqual("<h2>Deploy succeeded</h2>", payload["html"])

  def test_send_resend_email_returns_message_id_response(self):
    response = mock.MagicMock()
    response.__enter__.return_value.read.return_value = b'{"id":"email_123"}'

    with mock.patch("urllib.request.urlopen", return_value=response) as urlopen:
      result = send_resend_email.send_resend_email(
          api_key="test-key",
          api_url="https://api.resend.com/emails",
          sender="SupaWave Deploy <noreply@supawave.ai>",
          recipients=["ops@example.com"],
          subject="subject",
          html="<p>body</p>",
      )

    self.assertEqual({"id": "email_123"}, result)
    request = urlopen.call_args.args[0]
    self.assertEqual("Bearer test-key", request.headers["Authorization"])

  def test_send_resend_email_rejects_non_resend_url_before_request(self):
    with mock.patch("urllib.request.urlopen") as urlopen:
      with self.assertRaisesRegex(RuntimeError, "Unsupported Resend API URL"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://example.com/emails",
            sender="SupaWave Deploy <noreply@supawave.ai>",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

    urlopen.assert_not_called()

  def test_send_resend_email_reports_http_body_on_failure(self):
    error = HTTPError(
        "https://api.resend.com/emails",
        403,
        "Forbidden",
        hdrs=None,
        fp=mock.MagicMock(read=mock.MagicMock(return_value=b'{"message":"domain not verified"}')),
    )

    with mock.patch("urllib.request.urlopen", side_effect=error):
      with self.assertRaisesRegex(RuntimeError, "domain not verified"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://api.resend.com/emails",
            sender="noreply@supawave.ai",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

  def test_send_resend_email_reports_network_failure(self):
    with mock.patch("urllib.request.urlopen", side_effect=URLError("timeout")):
      with self.assertRaisesRegex(RuntimeError, "timeout"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://api.resend.com/emails",
            sender="noreply@supawave.ai",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

  def test_main_fails_when_api_key_missing(self):
    with mock.patch.dict(os.environ, {"NOTIFICATION_RECIPIENTS": "ops@example.com"}, clear=True):
      with mock.patch("sys.argv", ["send_resend_email.py", "--subject", "subject", "--html", "<p>body</p>"]):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
          result = send_resend_email.main()

    self.assertEqual(1, result)
    self.assertIn("RESEND_API_KEY is not configured", stderr.getvalue())

  def test_main_skips_empty_recipients(self):
    with mock.patch.dict(os.environ, {"RESEND_API_KEY": "test-key"}, clear=True):
      with mock.patch("sys.argv", ["send_resend_email.py", "--subject", "subject", "--html", "<p>body</p>"]):
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
          result = send_resend_email.main()

    self.assertEqual(0, result)
    self.assertIn("No deploy notification recipients configured", stdout.getvalue())

  def test_main_logs_accepted_response_without_id(self):
    response = mock.MagicMock()
    response.__enter__.return_value.read.return_value = b'{"status":"queued"}'
    env = {
        "RESEND_API_KEY": "test-key",
        "NOTIFICATION_RECIPIENTS": "ops@example.com",
    }

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch("urllib.request.urlopen", return_value=response):
        with mock.patch("sys.argv", ["send_resend_email.py", "--subject", "subject", "--html", "<p>body</p>"]):
          stdout = io.StringIO()
          with contextlib.redirect_stdout(stdout):
            result = send_resend_email.main()

    self.assertEqual(0, result)
    self.assertIn("accepted by Resend without an id", stdout.getvalue())


if __name__ == "__main__":
  unittest.main()
