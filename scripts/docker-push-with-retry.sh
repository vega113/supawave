#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <image-ref>" >&2
  exit 64
fi

image_ref="$1"
max_attempts="${DOCKER_PUSH_MAX_ATTEMPTS:-3}"
retry_delay_seconds="${DOCKER_PUSH_RETRY_DELAY_SECONDS:-15}"
transient_pattern='unknown blob|blob upload unknown|received unexpected HTTP status: 5[0-9][0-9]|5[0-9][0-9] Server Error|429 Too Many Requests|TLS handshake timeout|i/o timeout|connection reset by peer|EOF|unexpected EOF|net/http: timeout awaiting response headers'

attempt=1
while [[ "$attempt" -le "$max_attempts" ]]; do
  log_file="$(mktemp)"
  status=0
  docker push "$image_ref" >"$log_file" 2>&1 || status=$?
  cat "$log_file"

  if [[ "$status" -eq 0 ]]; then
    rm -f "$log_file"
    exit 0
  fi

  if grep -Eiq "$transient_pattern" "$log_file"; then
    rm -f "$log_file"
    if [[ "$attempt" -lt "$max_attempts" ]]; then
      echo "Transient docker push failure on attempt $attempt/$max_attempts, retrying in ${retry_delay_seconds}s..." >&2
      sleep "$retry_delay_seconds"
      attempt=$((attempt + 1))
      continue
    fi
    echo "Docker push failed after $max_attempts attempts due to transient registry errors" >&2
    exit "$status"
  fi

  rm -f "$log_file"
  echo "Docker push failed with a non-transient error; failing fast without retry" >&2
  exit "$status"
done
