#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Starting full mobile build pipeline..."
chmod +x "$SCRIPT_DIR"/*.sh

"$SCRIPT_DIR/build-android.sh"
"$SCRIPT_DIR/build-ios.sh"

echo "All platforms built successfully!"
