# Concordance v1.0.0 — Release Notes

## Installed systems
- **Timestral** (Mistral time-series foundation models): agent `/api/forecast` + trends
  forecasting; app on-device sparkline forecasting client (baseline fallback when
  weights absent).
- **trim-1** (context pruner): agent `/api/context/prune`; app client; model-facing
  context only, adjudication inputs untouched.
- **HyperFrames** (HeyGen): website deterministic MP4 explainer pipeline.

## Interconnections
- Timestral wired into the trends engine (`/api/trends` now carries forecasts).
- trim-1 exposed as an agent endpoint and a client; usable by model-facing prompts.
- HyperFrames media assets served from the public assets bucket convention.

## Tests
- App: 246 unit tests, 0 failures.
- Agent: timestral/trim tests added (7 passed).
- Website: 100 tests, 0 failures; production build succeeds.

## Artifacts
- Android debug APK: `app/build/outputs/apk/debug/app-debug.apk` (v1.0.0, code 2).
- Website: Next.js production build (`.next`).

## Repos
- App (open source): `concordance-oss` (Apache-2.0), tag `v1.0.0`.
- Website (private): `concordance-website`, tag `v1.0.0`.

## GitHub release link
- https://github.com/<owner>/concordance/releases/tag/v1.0.0
