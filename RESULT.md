# Result — Concordance OSS 1.0.2

Recorded 2026-10-28.

## Structural verification

```text
Kotlin test files:        92
Source modules:           56
Exact test match:         48
Package-fallback match:   84%
Dead controls:            0
```

## What this proves

The app has a real test tree, including the decision model, cloud adapters,
repository sync, conflict resolution and the UI review gate. It does **not**
prove the tests pass: `./gradlew :app:testDebugUnitTest` was not executed in this
pass.

## Ecosystem position

- **Role:** public research/adjudication client.
- **Mesh app id:** `concordance-oss`.
- **Primary model:** `qwen-science-oncology-v2` (adapter present, no GGUF).
- **Decision model:** `overlay-decision-v1`, implemented locally; the mesh
  `/v1/decisions` endpoint can supply a remote answer.
- **Peers:** Sieve, Recourse, Agent Support, AetherDesk, NC Field, Math X via
  `EcosystemLinks`.

## Known gaps

- No Gradle run, no device install, no signed APK in this pass.
- Six Python helper modules have no test beside them.
- No local GGUF.
