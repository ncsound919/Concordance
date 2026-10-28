# Changelog

All entries below describe changes that were actually run, not planned work.
Earlier history was not recorded; this file starts at the first verified baseline.

## 2026-10-28 — full unit suite green; navigation test corrected

- Fixed `NavigationStateTest.primaryNavigationIsCappedAtFive`. It asserted two
  secondary destinations, but `NavigationDestination` has three (Integrations,
  Account, Research). The test also now asserts that primary + secondary covers
  every enum entry, so a destination cannot become unreachable without failing.
- `sh gradlew :app:testDebugUnitTest`: 388 tests, 0 failures.
- No production navigation behaviour was changed; the bottom bar still renders
  the five primary destinations and the More sheet holds the rest.

Not covered by this entry: no APK was built or installed, and the release
signing certificate is still a placeholder. See `docs/DEPLOY-READINESS.md`.
