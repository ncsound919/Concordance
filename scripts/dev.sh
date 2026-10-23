#!/usr/bin/env bash
# Local development loop: build the debug APK and install it on the connected
# device or emulator, with the Infisical project's `dev` environment injected as
# environment variables. No .env file is needed — see INFISICAL.md.
#
#   bash scripts/dev.sh
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

exec infisical run --env=dev -- ./gradlew :app:installDebug "$@"
