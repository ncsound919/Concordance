# Deploy readiness — Concordance OSS

**Decision:** source-complete; the test tree has not been executed in this pass.

## Gate

| Requirement | State |
|---|---|
| App version | 1.0.2 (versionCode 4) |
| Kotlin test files present | yes — 92 |
| `./gradlew :app:testDebugUnitTest` | **not run** |
| Dead controls | none found |
| Decision model tests | present |
| Ecosystem deep links | added (`EcosystemLinks`) |
| Shared mesh client | added; not compiled by Gradle here |
| Signed release APK | absent from this checkout |
| Physical-device run | not done |
| Local GGUF | absent |

## Build and test

```bash
cd phone-apps/concordance-oss
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The build needs `local.properties` pointing at an Android SDK, and a JDK 17+
toolchain. The repository contains `debug.keystore`; do not reuse it for a
release.

## Beta definition

A build is beta when `testDebugUnitTest` is green, a signed APK installs on a
physical phone, one paper is adjudicated end-to-end offline, and the decision
model's rules-vs-local-LLM path is recorded with its fallback reason.
