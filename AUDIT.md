# Concordance audit — 2026-10-28

Scope: the Android client in this checkout. Verdict: **PASS at the unit layer,
not yet device-verified.**

## What was checked

| Area | Result |
|---|---|
| Unit suite | 388 tests, 0 failures (`:app:testDebugUnitTest`) |
| Navigation invariants | 5 primary + 3 secondary cover every destination |
| Back-press precedence | More sheet → home → system exit, tested |
| Deep-link parsing | anchor id extracted from ACTION_VIEW, tested |
| Module coverage | 84.5% of source modules have a matching test |

## Findings

1. **Stale navigation assertion (fixed).** `primaryNavigationIsCappedAtFive`
   expected two secondary destinations while the app defines three. The test now
   asserts three and that primary + secondary cover every enum value, so a
   destination cannot become unreachable silently.
2. **One dead control remains.** `AutonomyActivity.kt` contains a handler that
   does not perform its advertised action. It is not on the adjudication path,
   but it should be implemented or removed before beta.
3. **Release signing is a placeholder.** The release certificate fingerprint in
   the build configuration is not the real upload certificate. A release APK
   must not be treated as shippable.
4. **Deep links are parsing-tested only.** No App Link has been tapped on a
   device, so the manifest/asset-links association is unproven.
5. **No device run.** No claim is made about startup time, offline behaviour or
   recovery after process death.

## Evidence

- `evidence/2026-10-28-unit-tests.md` — command, output, JUnit totals.
- `docs/DEPLOY-READINESS.md` — what remains before beta.
