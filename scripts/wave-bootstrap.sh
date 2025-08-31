#!/usr/bin/env bash
set -euo pipefail

# wave-bootstrap.sh — create sane defaults for a first run
# - Creates config/application.conf if missing (HTTP on 127.0.0.1:9898, SSL off)
# - Optionally creates a self-signed dev keystore (JKS, password 'changeme')
# - Copies reference.conf into config/ if not present for convenience
#
# Usage:
#   scripts/wave-bootstrap.sh            # bootstrap defaults (no SSL)
#   scripts/wave-bootstrap.sh --with-ssl # also generate dev keystore and enable SSL in application.conf

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
CONF_DIR="$ROOT_DIR/wave/config"
APP_CONF="$CONF_DIR/application.conf"
REF_CONF_SRC="$ROOT_DIR/wave/config/reference.conf"
REF_CONF_DST="$CONF_DIR/reference.conf"
KS_FILE="$CONF_DIR/dev-keystore.jks"

WITH_SSL=false
for arg in "$@"; do
  case "$arg" in
    --with-ssl) WITH_SSL=true ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$CONF_DIR"

# Copy reference.conf if not present (for easy editing/lookup)
if [[ -f "$REF_CONF_SRC" && ! -f "$REF_CONF_DST" ]]; then
  cp "$REF_CONF_SRC" "$REF_CONF_DST"
  echo "Copied reference.conf to $REF_CONF_DST"
fi

# Create default application.conf if missing
if [[ ! -f "$APP_CONF" ]]; then
  cat > "$APP_CONF" <<'EOF'
core {
  http_frontend_addresses = ["127.0.0.1:9898"]
}

network {
  # Allow JS to read session cookie for WebSocket fallback auth in dev
  session_cookie_http_only = false
}

security {
  enable_ssl = false
  # ssl_keystore_path = "config/dev-keystore.jks"
  # ssl_keystore_password = "changeme"
  # ssl_keystore_password = ${?WAVE_SSL_KEYSTORE_PASSWORD}
}
EOF
  echo "Created $APP_CONF"
fi

# Optionally generate a dev keystore and enable SSL
if $WITH_SSL; then
  if [[ ! -f "$KS_FILE" ]]; then
    echo "Generating self-signed dev keystore at $KS_FILE (password: changeme)"
    keytool -genkeypair -alias wave -keyalg RSA -keysize 2048 \
      -keystore "$KS_FILE" -storepass changeme -keypass changeme \
      -dname "CN=localhost, OU=Dev, O=Wave, L=Local, ST=NA, C=US" -validity 3650 >/dev/null
  fi
  # Enable SSL in application.conf
  if ! rg -q "enable_ssl\s*=\s*true" "$APP_CONF"; then
    perl -0777 -pe 's/enable_ssl\s*=\s*false/enable_ssl = true/;' -i "$APP_CONF"
  fi
  if ! rg -q "ssl_keystore_path" "$APP_CONF"; then
    printf "\n# SSL dev keystore\nssl_keystore_path = \"config/dev-keystore.jks\"\nssl_keystore_password = \"changeme\"\n" >> "$APP_CONF"
  else
    perl -0777 -pe 's#ssl_keystore_path.*#ssl_keystore_path = "config/dev-keystore.jks"#;' -i "$APP_CONF"
    perl -0777 -pe 's#ssl_keystore_password.*#ssl_keystore_password = "changeme"\nssl_keystore_password = ${?WAVE_SSL_KEYSTORE_PASSWORD}#;' -i "$APP_CONF" || true
  fi
  echo "SSL enabled in $APP_CONF"
fi

echo "Bootstrap complete. You can run: ./gradlew :wave:run"

