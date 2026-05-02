import contextlib
import io
import json
import os
import unittest
from unittest import mock

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
    response = mock.MagicMock(returncode=0, stdout='{"id":"email_123"}', stderr="")

    with mock.patch("subprocess.run", return_value=response) as run:
      result = send_resend_email.send_resend_email(
          api_key="test-key",
          api_url="https://api.resend.com/emails",
          sender="SupaWave Deploy <noreply@supawave.ai>",
          recipients=["ops@example.com"],
          subject="subject",
          html="<p>body</p>",
      )

    self.assertEqual({"id": "email_123"}, result)
    command = run.call_args.args[0]
    self.assertEqual("curl", command[0])
    # API key must not appear in argv — it is passed via stdin config instead.
    self.assertNotIn("test-key", " ".join(command))
    stdin_input = run.call_args.kwargs.get("input", "")
    self.assertIn("Authorization: Bearer", stdin_input)
    self.assertIn("test-key", stdin_input)

  def test_send_resend_email_rejects_non_resend_url_before_request(self):
    with mock.patch("subprocess.run") as run:
      with self.assertRaisesRegex(RuntimeError, "Unsupported Resend API URL"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://example.com/emails",
            sender="SupaWave Deploy <noreply@supawave.ai>",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

    run.assert_not_called()

  def test_send_resend_email_reports_http_body_on_failure(self):
    response = mock.MagicMock(
        returncode=22,
        stdout='{"message":"domain not verified"}',
        stderr="curl: (22) The requested URL returned error: 403",
    )

    with mock.patch("subprocess.run", return_value=response):
      with self.assertRaisesRegex(RuntimeError, "domain not verified"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://api.resend.com/emails",
            sender="noreply@supawave.ai",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

  def test_send_resend_email_includes_stderr_in_http_failure(self):
    response = mock.MagicMock(
        returncode=22,
        stdout='{"message":"domain not verified"}',
        stderr="curl: (22) The requested URL returned error: 403",
    )

    with mock.patch("subprocess.run", return_value=response):
      with self.assertRaisesRegex(RuntimeError, r"domain not verified.*curl: \(22\)"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://api.resend.com/emails",
            sender="noreply@supawave.ai",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

  def test_send_resend_email_raises_when_curl_missing(self):
    with mock.patch("subprocess.run", side_effect=FileNotFoundError("No such file: curl")):
      with self.assertRaisesRegex(RuntimeError, "curl is not available"):
        send_resend_email.send_resend_email(
            api_key="test-key",
            api_url="https://api.resend.com/emails",
            sender="noreply@supawave.ai",
            recipients=["ops@example.com"],
            subject="subject",
            html="<p>body</p>",
        )

  def test_send_resend_email_reports_network_failure(self):
    response = mock.MagicMock(returncode=28, stdout="", stderr="curl: (28) timeout")
    with mock.patch("subprocess.run", return_value=response):
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
    response = mock.MagicMock(returncode=0, stdout='{"status":"queued"}', stderr="")
    env = {
        "RESEND_API_KEY": "test-key",
        "NOTIFICATION_RECIPIENTS": "ops@example.com",
    }

    with mock.patch.dict(os.environ, env, clear=True):
      with mock.patch("subprocess.run", return_value=response):
        with mock.patch("sys.argv", ["send_resend_email.py", "--subject", "subject", "--html", "<p>body</p>"]):
          stdout = io.StringIO()
          with contextlib.redirect_stdout(stdout):
            result = send_resend_email.main()

    self.assertEqual(0, result)
    self.assertIn("accepted by Resend without an id", stdout.getvalue())


if __name__ == "__main__":
  unittest.main()
