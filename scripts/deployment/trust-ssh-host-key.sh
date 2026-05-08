#!/usr/bin/env bash
set -euo pipefail

host="${1:-}"
port="${2:-22}"
expected_fingerprint="${3:-}"

if [ -z "$host" ]; then
  echo "CONTABO_SSH_HOST must be set" >&2
  exit 1
fi

if [ -z "$expected_fingerprint" ]; then
  echo "CONTABO_HOST_FINGERPRINT must be set as a repo variable or secret" >&2
  exit 1
fi

mkdir -p "$HOME/.ssh"
tmp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
mkdir -p "$tmp_dir"
scanned_host_keys="$tmp_dir/contabo_known_hosts"

ssh_keyscan_err="$tmp_dir/ssh_keyscan_err"
ssh_keyscan_exit=0
ssh-keyscan -p "$port" -H "$host" > "$scanned_host_keys" 2>"$ssh_keyscan_err" || ssh_keyscan_exit=$?
if [ "$ssh_keyscan_exit" -ne 0 ]; then
  echo "ssh-keyscan failed (exit $ssh_keyscan_exit) for $host:$port" >&2
  cat "$ssh_keyscan_err" >&2
  exit 1
fi
actual_fingerprints="$(ssh-keygen -lf "$scanned_host_keys" | awk '{print $2}')"

if [ -z "$actual_fingerprints" ]; then
  echo "Failed to read SSH host key fingerprint for $host" >&2
  exit 1
fi

matched_host_keys=""
while IFS= read -r host_key_line; do
  [ -n "$host_key_line" ] || continue
  case "$host_key_line" in
    "#"*) continue ;;
  esac
  key_file="$tmp_dir/contabo_known_host_key"
  printf '%s\n' "$host_key_line" > "$key_file"
  line_fingerprint="$(ssh-keygen -lf "$key_file" | awk '{print $2}')"
  if [ "$line_fingerprint" = "$expected_fingerprint" ]; then
    matched_host_keys="${matched_host_keys}${host_key_line}"$'\n'
  fi
done < "$scanned_host_keys"

if [ -z "$matched_host_keys" ]; then
  echo "SSH host key fingerprint mismatch for $host" >&2
  echo "Expected: $expected_fingerprint" >&2
  echo "Actual fingerprints:" >&2
  printf '%s\n' "$actual_fingerprints" >&2
  exit 1
fi

printf '%s' "$matched_host_keys" >> "$HOME/.ssh/known_hosts"
