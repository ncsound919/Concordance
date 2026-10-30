# Audit — Concordance OSS

**Date:** 2026-10-28
**Scope:** the public Android edition and its local Python helpers.

## Measured

| Check | Result |
|---|---|
| Kotlin test files | **92** (unit + instrumentation); not executed in this pass |
| Source modules with a test | 48/56 exact, 84% with package fallback |
| Dead controls | none found |
| Typecheck / Gradle | not run in this pass |
| Version | 1.0.2, versionCode 4 |

The audit is structural: it proves a test file exists for a module, not that the
module passes. Run `./gradlew :app:testDebugUnitTest` before quoting a green
build.

## What is strong

- The decision model (`DecisionModel.kt`) is the ecosystem reference: closed
  answer sets, a confidence floor, malformed-answer rejection, rules fallback,
  and an app-written reason sentence. `DecisionModelTest` covers all of those.
- The app has a large Kotlin test tree (92 files), including cloud adapters,
  repository sync, conflict resolution and the decision gate.
- No `TODO()`, empty click handler or "coming soon" remains.

## What is missing

- No changelog/audit/readiness record before this pass. The evidence directory
  now records the audit, but there is no executed test log.
- No physical-device run, no signed release artifact in this checkout.
- No GGUF. The on-device decision path can call a loopback llama.cpp server when
  the operator starts one; otherwise it uses rules.
- The Python helpers under `src/concordance/` have no test beside them; six
  modules is the remaining exact-match gap.

## Risks

- The local model path is HTTP to `127.0.0.1`; Android cleartext policy and the
  llama.cpp port must both be right on the device.
- The shared mesh client is source-complete but not compiled by a Gradle build in
  this checkout.
