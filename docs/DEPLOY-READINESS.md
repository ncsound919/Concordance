# Deploy readiness — Concordance

**Status: unit-test verified, not device verified.**
Last verified: 2026-10-28.

## What was verified

| Check | Version | Result |
|---|---|---|
| Unit tests | `sh gradlew :app:testDebugUnitTest` | 388 tests, 0 failures |
| Navigation invariants | `NavigationStateTest` | 5 primary + 3 secondary cover every destination |

Covered by the suite: back-press precedence and the More sheet, destination
uniqueness, deep-link anchor handling, foreground-service types, theming and
the adjudication/data paths that do not require a device.

## What was not verified

- No APK was built or installed on a phone in this run.
- Release signing is still a placeholder; a release build must not be treated
  as shippable until the real certificate fingerprint is recorded.
- Deep links were tested at the parsing layer, not by tapping an App Link.
- No store distribution is planned, so listing and metadata were not checked.

## Beta gate remaining

1. Build a debug APK, install it, and complete one adjudication end to end
   against the agent endpoint.
2. Follow one App Link into an anchor and confirm it opens the right claim.
3. Write both results as one sentence each in this file.

## Reproduce

```bash
cd "eco/Concordance-main"
sh gradlew :app:testDebugUnitTest --console=plain
```
