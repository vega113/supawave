#!/usr/bin/env python3
"""Send a deploy notification email through Resend."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from urllib.parse import urlparse


DEFAULT_FROM = "SupaWave Deploy <noreply@supawave.ai>"
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
  request = urllib.request.Request(
      api_url,
      data=build_payload(sender, recipients, subject, html),
      headers={
          "Authorization": f"Bearer {api_key}",
          "Content-Type": "application/json",
      },
      method="POST",
  )
  try:
    with urllib.request.urlopen(request, timeout=20) as response:
      body = response.read().decode("utf-8", errors="replace")
  except urllib.error.HTTPError as exc:
    body = exc.read().decode("utf-8", errors="replace")
    raise RuntimeError(f"Resend API returned HTTP {exc.code}: {body}") from exc
  except urllib.error.URLError as exc:
    raise RuntimeError(f"Resend API request failed: {exc.reason}") from exc

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
