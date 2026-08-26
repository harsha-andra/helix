#!/usr/bin/env bash
# Karma's CHROME_BIN is pointed at this wrapper (see karma.conf.js) instead of a raw
# Chromium/Chrome binary. It forwards to the real browser but always appends --no-sandbox,
# which is required to launch headless Chrome while running as root (CI containers, this
# sandbox) and is a harmless no-op everywhere else.
set -euo pipefail

REAL_BIN="${HELIX_KARMA_CHROME_BIN:-}"

if [ -z "$REAL_BIN" ]; then
  for candidate in /opt/pw-browsers/chromium google-chrome-stable google-chrome chromium chromium-browser; do
    if command -v "$candidate" >/dev/null 2>&1; then
      REAL_BIN="$(command -v "$candidate")"
      break
    fi
  done
fi

if [ -z "$REAL_BIN" ]; then
  echo "chromium-no-sandbox.sh: could not locate a Chrome/Chromium binary. Set HELIX_KARMA_CHROME_BIN." >&2
  exit 1
fi

exec "$REAL_BIN" --no-sandbox "$@"
