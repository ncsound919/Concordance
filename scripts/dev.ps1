# Local development loop: build the debug APK and install it on the connected
# device or emulator, with the Infisical project's `dev` environment injected as
# environment variables. No .env file is needed — see INFISICAL.md.
#
#   .\scripts\dev.ps1
$ErrorActionPreference = "Stop"

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    infisical run --env=dev -- .\gradlew.bat :app:installDebug @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
