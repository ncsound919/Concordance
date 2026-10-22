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

## App features
- Agent tab: connect, dictate, adjudicate, correct, and **referee** (a deterministic
  adjudication of a claim against a prediction table, returning the verdict together
  with its `report_sha256` / `decision_sha256` / `input_sha256` digests and its
  provenance).

## Tests
- App: **556 unit tests, 0 failures** (10 live-network smoke tests skipped without
  credentials), `:app:lintDebug` **0 errors**.
- App release build: `:app:assembleRelease` succeeds with R8 minification, resource
  shrinking and `lintVitalRelease` green.
- Referee: **89 TypeScript tests** and **48 Python tests**, all passing;
  `next build` and `tsc --noEmit` clean.
- Website: **198 tests**, production build succeeds.

## Artifacts
- Android debug APK: `app/build/outputs/apk/debug/app-debug.apk` (v1.0.0, code 2).
- Android release APK: produced by `:app:assembleRelease`, signed with the keystore at
  `KEYSTORE_PATH` (no release keystore is committed to the repository).
- Website: Next.js production build (`.next`), deployable via OpenNext to Cloudflare.

## Repos
- App (open source): `concordance-oss` (Apache-2.0), tag `v1.0.0`.
- Website (private): `concordance-website`.
- Referee: `Referee-main`.

## GitHub release link
- https://github.com/ncsound919/Concordance/releases/tag/v1.0.0

## Release checklist
- [x] Unit tests, lint and build green on all three repos.
- [x] Every API route authenticated; both services fail closed without a secret.
- [x] CI runs the real test suites on every pull request (no `|| true`).
- [x] Release build verified with R8 and `lintVitalRelease`.
- [ ] Apply `supabase/migrations/0021`–`0023` to the database **before** deploying the
      website, and set `CONCORDANCE_SETTINGS_SECRET` (32+ bytes). Without it,
      `PUT /api/settings` answers `503` rather than storing credentials in plaintext.
- [ ] Set `INTERNAL_API_KEY` (or `REFEREE_INTERNAL_KEY` / `REFEREE_API_KEY`) on the
      referee deployment. Without it the service answers `503` on every route.
- [ ] **Rotate the GitHub personal access token** that was embedded in this
      repository's `origin` URL. It has been removed from `.git/config`; the remote
      now reads the token from the `GITHUB_TOKEN` environment variable.
