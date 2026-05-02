#!/usr/bin/env python3
"""Send a deploy notification email through Resend."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from urllib.parse import urlparse


DEFAULT_FROM = "noreply@supawave.ai"
DEFAULT_API_URL = "https://api.resend.com/emails"


def parse_recipients(raw: str) -> list[str]:
  raw = raw.strip()
  if not raw:
    return []
  if raw.startswith("["):
    try:
      parsed = json.loads(raw)
    except json.JSONDecodeError:
      parsed = None
    if isinstance(parsed, list):
      return [item.strip() for item in parsed if isinstance(item, str) and item.strip()]
  return [item.strip() for item in raw.split(",") if item.strip()]


def build_payload(sender: str, recipients: list[str], subject: str, html: str) -> bytes:
  return json.dumps(
      {
          "from": sender,
          "to": recipients,
          "subject": subject,
          "html": html,
      }
  ).encode("utf-8")


def send_resend_email(
    *,
    api_key: str,
    api_url: str,
    sender: str,
    recipients: list[str],
    subject: str,
    html: str,
) -> dict[str, object]:
  parsed = urlparse(api_url)
  if parsed.scheme != "https" or parsed.netloc != "api.resend.com":
    raise RuntimeError(f"Unsupported Resend API URL: {api_url}")
  # Pass the Authorization header via stdin config to keep the API key out of
  # the process argv (visible via ps/proc on shared runners).
  curl_config = f'header = "Authorization: Bearer {api_key}"\n'
  try:
    result = subprocess.run(
        [
            "curl",
            "-q",
            "-sS",
            "--fail-with-body",
            "--max-time",
            "20",
            "--config",
            "-",
            "-X",
            "POST",
            api_url,
            "-H",
            "Content-Type: application/json",
            "-d",
            build_payload(sender, recipients, subject, html).decode("utf-8"),
        ],
        input=curl_config,
        capture_output=True,
        text=True,
        check=False,
    )
  except (FileNotFoundError, OSError) as exc:
    raise RuntimeError(f"curl is not available: {exc}") from exc
  body = result.stdout
  if result.returncode != 0:
    parts = [p for p in (body.strip(), result.stderr.strip()) if p]
    error_detail = " | ".join(parts) if parts else f"curl exited {result.returncode}"
    raise RuntimeError(f"Resend API request failed: {error_detail}")

  try:
    parsed = json.loads(body)
  except json.JSONDecodeError as exc:
    raise RuntimeError(f"Resend API returned non-JSON response: {body}") from exc
  if not isinstance(parsed, dict):
    raise RuntimeError(f"Resend API returned unexpected response: {body}")
  return parsed


def read_html(args: argparse.Namespace) -> str:
  if args.html_file:
    with open(args.html_file, encoding="utf-8") as handle:
      return handle.read()
  return args.html


def main() -> int:
  parser = argparse.ArgumentParser()
  parser.add_argument("--subject", required=True)
  parser.add_argument("--html", default="")
  parser.add_argument("--html-file")
  parser.add_argument("--label", default="deploy")
  args = parser.parse_args()

  api_key = os.getenv("RESEND_API_KEY", "").strip()
  if not api_key:
    print("RESEND_API_KEY is not configured; cannot send deploy notification.", file=sys.stderr)
    return 1

  recipients = parse_recipients(os.getenv("NOTIFICATION_RECIPIENTS", ""))
  if not recipients:
    print("No deploy notification recipients configured; skipping email notification.")
    return 0

  sender = os.getenv("WAVE_EMAIL_FROM", "").strip() or DEFAULT_FROM
  api_url = os.getenv("RESEND_API_URL", DEFAULT_API_URL).strip() or DEFAULT_API_URL
  try:
    response = send_resend_email(
        api_key=api_key,
        api_url=api_url,
        sender=sender,
        recipients=recipients,
        subject=args.subject,
        html=read_html(args),
    )
  except RuntimeError as exc:
    print(f"Email notification failed: {exc}", file=sys.stderr)
    return 1

  message_id = response.get("id")
  if message_id:
    print(
        f"{args.label} notification sent via Resend: id={message_id} "
        f"recipients={len(recipients)}"
    )
  else:
    print(
        f"{args.label} notification accepted by Resend without an id: "
        f"recipients={len(recipients)} response={json.dumps(response, sort_keys=True)}"
    )
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
