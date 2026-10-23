# Audit remediation log — 2026-10-21

Fixes applied in response to [CODE_AUDIT_2026-10-21.md](CODE_AUDIT_2026-10-21.md). Each entry
names the change and the test that now pins it.

## Verification

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` (JDK 21) | **305 tests / 43 classes, 0 failures, 0 errors** |
| `./gradlew :app:testDebugUnitTest` (JDK 17 launcher, toolchain-pinned) | previously-failing Robolectric classes (`AgentClientTest`, `ExampleUnitTest`, `Phase4HardeningTest`, `StatisticalEngineValidationTest`, `HardeningTest`) all pass |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL, `app-debug.apk` = 122,393,656 bytes (was 122,893,085) |
| S3 presign parity | three fixed-timestamp URLs match `botocore.auth.S3SigV4QueryAuth` (botocore 1.43.117) byte for byte |
| `:app:lintDebug` | not run in this pass |

## Findings closed

### C1 — Engine validation (closed)

- `model/Models.kt`: `Anchor.validationError()` mirrors `src/concordance/schema.py` — counts for
  both groups, `0 < events < total`, complete summary inputs, `or_ci_low <= or <= or_ci_high`,
  `0 < reference_prevalence < 1`, and `evidence_grade = counts` needs counts.
- `engine/StatisticalEngine.kt`: every public entry point calls `requireUsable`; the `?: 0.2075`,
  `?: 0.35`, `?: 1.0`, `?: est * 0.7` fallbacks are gone; `adjudicate` returns a
  `withheldForInputs` result (verdict `WITHHELD`, empty curve, reason in `rationale` and
  `citableArgument`) instead of letting NaN reach a string. `curve` now requires ≥ 2 points.
- `ui/screens/RegistryScreen.kt`: an unusable claim renders "not estimable" and the reason rather
  than printing `0.000`/`NaN`; `ClaimRegistryCard` takes nullable metrics.
- `ui/ConcordanceViewModel.kt`: re-adjudication skips unusable claims and reports how many it
  skipped; power-sweep already tolerated failures.
- Pinned by `StatisticalEngineValidationTest` (16 tests): zero-event group, 100 %-event group,
  partial counts, events > totals, summary anchor without numbers, OR outside CI, prevalence out
  of range, counts-grade-without-counts, reference parity (`OR 0.263`, `r* 0.3107`, corrected OR
  = 1 at `r*`), degenerate curve, power refusal, no-NaN guarantee.

### C2 — Capsule digest covers the whole payload (closed)

- `engine/CapsuleEngine.kt`: `buildCanonicalDocument()` sorts the whole document and removes
  `sha256`; `CAPSULE_VERSION` is now `"2"`; the digest is computed after every field is present.
  `verifyJson` uses v2 semantics for `capsule_version >= 2` and keeps the legacy v1 subset so
  already-published capsules still verify — and says which rule it used.
- `ui/ConcordanceViewModel.kt`: the toast now says "SHA-256 sealed over every field".
- Pinned by `CapsuleIntegrityTest` (10 tests): pristine verify, tampering the verdict prose, the
  timestamp, an assumption, the permalink, and the inputs all fail; a refused anchor still
  produces a verifiable capsule; v1 capsules verify under legacy rules and v1 tampering fails.

### H1 — Fabricated infrastructure is labelled (closed)

- `integration/queue/JobModels.kt`: `isSample` on `JobStatusResponse` and `WorkerNode`.
- `integration/queue/JobQueueManager.kt`: seeded jobs and the whole illustrative fleet are flagged;
  a submitted job is a **local simulation** — flagged `isSample`, its summary is prefixed
  `SIMULATED RESULT (no compute was run and no capsule was produced)`, worker logs say
  `SIMULATED`, and `citableCapsuleSha256` is now `null` instead of a hash of a string.
- `ui/components/AsyncJobQueueTab.kt`: a **SAMPLE DATA** banner counts the flagged jobs/workers
  and states that no worker ran and the hashes are illustrative.
- `README.md`: documents both demo surfaces.
- Pinned by `HardeningTest` (`seededJobsAndWorkersAreFlaggedAsSamples`,
  `submittedJobsAreLabelledAsSimulated`).

### H2 — S3 signing and presigning (closed)

- `integration/aws/S3SigV4.kt` rewritten: virtual-hosted-style by default
  (`https://<bucket>.s3.<region>.amazonaws.com/<key>`), path-style only for custom endpoints
  (R2/MinIO/mock server), SigV4 percent-encoding for bucket/key segments, signed
  `x-amz-security-token`, and a real `presignGet()` (`X-Amz-Algorithm/Credential/Date/Expires/
  SignedHeaders/Signature`, `UNSIGNED-PAYLOAD`, 7-day clamp) with an injectable clock.
- `integration/aws/AwsCloudManager.kt`: uses virtual-hosted style against AWS, returns a real
  presigned GET URL, and records `errorDetail` (`HTTP <code>: <body>`) on a failed PUT instead of
  collapsing it into "simulated".
- `ui/screens/IntegrationsScreen.kt`: unchanged call site; "Copy S3 Presigned URL" now copies a
  link a reviewer can actually open.
- Pinned by `S3SigV4Test` (11 tests), including three signatures cross-checked against botocore.

### H3 — Credential handling (closed for the app; deployment guidance remains)

- `S3SigV4` + `AwsCloudManager` + `ConcordanceViewModel.setAwsConfig` now accept and sign a
  `sessionToken`, so STS/Cognito temporary credentials work; `aws_session_token` is read from the
  config import and stored in the encrypted key store.
- `AwsCloudManager.clearCredentials()` added for sign-out.
- `integration/agent/AgentSettingsStore.kt`: any values left in the legacy plaintext prefs file are
  migrated into `EncryptedSharedPreferences` once and the plaintext copy is cleared.
- Not done: a Cognito Identity Pool / token broker (infrastructure, outside the app), and long-lived
  keys are still accepted for users who configure them deliberately.

### M1 — Reproducible test JVM (closed)

- `app/build.gradle.kts` pins **only the unit-test launcher** to a Java 21 toolchain, so Robolectric
  SDK 36 starts on a JDK 17 machine without touching the app's compile target.
- `README.md` now states JDK 21 is required for tests and why.
- Verified by running the previously-failing Robolectric classes with `JAVA_HOME` = JDK 17.

### M2 — Loopback model server requires a key (closed)

- `local/LocalModels.kt`: per-session `rotateApiKey()`/`currentApiKey`/`clearApiKey` (memory only).
- `local/LocalLlmService.kt`: passes `--api-key <random>` to `llama-server`, sends it on the health
  probe, and clears the key when the server stops.
- `local/LocalExtractionClient.kt`: sends `Authorization: Bearer <key>`.
- Pinned by `HardeningTest` (`localModelKeyIsRotatedAndCleared`,
  `extractionClientSendsTheSessionKey`, `extractionClientOmitsTheKeyWhenNoServerIsRunning`).
- Requires one device run to confirm the bundled llama.cpp build accepts `--api-key` (it is a
  documented `llama-server` flag, but this APK's build was not exercised on hardware here).

### M3 — Tasker bridge bounded (closed)

- `integration/tasker/TaskerBridge.kt`: queue capped at 25 entries (oldest dropped), identical
  repeats within 2 s collapse, extras truncated to 120 chars. Unknown actions are still ignored.
- Deliberately **not** signature-permission-protected: that would break the documented Tasker
  integration. The actual exposure was unbounded prefs growth and repeated work triggers; both are
  now bounded.
- Pinned by `HardeningTest` (queue bound, dedupe, truncation, unknown-action no-op).

### M4 — Unused dependencies removed (partial)

- Removed `firebase-ai`, both `firebase-appcheck` artifacts, `retrofit`, `converter-moshi`,
  `moshi-kotlin` + its KSP processor, `navigation-compose`, and `logging-interceptor`.
  `okhttp` is now declared explicitly (it previously arrived through Retrofit).
- APK: 122.9 MB → 122.4 MB. Native libraries dominate; excluding the llama.cpp bench/perplexity/
  quantize binaries is deferred until it can be device-tested (a wrong exclusion silently breaks
  local inference).

### H3 — The learning set refused nothing the engine could not estimate (closed)

- `learning/TrainingSetBuilder.kt`: `isUsable` is now `anchor.validationError() == null`, not
  "has some numbers". A zero-event group, a 100 %-event group, half a 2×2 table, or a summary OR
  missing its interval/prevalence is skipped **with its reason** (`Result.skipReasons`) instead of
  becoming a case whose verdict is always `WITHHELD`. Challenge labels whose corrected counts are
  not estimable are dropped and counted (`skippedChallenges`). A defensive `check()` asserts no
  admitted case fails the engine gate.
- `learning/LearningEvaluator.kt`: `adjudicate` fails closed — an unestimable anchor returns
  `null`, never a `WITHHELD` verdict. `challengeError`/`challengeAnchor` centralise the challenge
  gate, so a correction with a zero-event group has no implied verdict. An out-of-bounds candidate
  scores `1.0` (worst) rather than a near-zero total a promotion rule could misread as an
  improvement.
- Protocol-frozen cases (abstract-only, verdict withheld whatever the parameters) stay in the set
  for audit but are excluded from the stability, challenge-resolution and calibration
  denominators: a verdict that cannot move carries no information and was diluting the scores.
- `learning/LearningHealth.kt`, `ui/ConcordanceViewModel.kt`: an unestimable correction no longer
  counts as a permanent miss in the resolution rate, and the training summary prints the skip
  reasons instead of only a count.
- Pinned by `SelfLearningRoundTest` (7 new tests: zero-event, saturated, half-table and bare-OR
  anchors skipped with reasons; every admitted case estimable across the whole candidate grid;
  unestimable challenge dropped; frozen case retained but unscored), plus
  `LearningEvaluatorTest` (refused anchor never adjudicated, unestimable challenge has no implied
  verdict) and `LearningHealthTest` (resolution rate ignores it).

## Still open

| Item | Why it is still open |
|---|---|
| M5 — Room at rest unencrypted, `data_class = "aggregate"` asserted on push | Needs a schema column + migration and a product decision on PHI scope |
| L1 — paired `r` CI clamp at 1.5, CSV quoting, 8-hex permalink presented as a DOI | Cosmetic/reporting changes, no integrity impact |
| L2 — `POST_NOTIFICATIONS` never requested, `EncryptedSharedPreferences` deprecation | Needs a UI permission flow and a DataStore migration |
| M4 residual — bench `.so` exclusion, Play Asset Delivery | Requires a device run of local inference |
| Presigned URL UI | The link is real now, but there is no S3 session-token input field in the Integrations screen; the config import path supports `aws_session_token` |
| `:app:lintDebug` | Not executed here (network-bound); the six `java.time` `NewApi` errors noted in the UI/UX audit remain the baseline |

## Second pass — website fixes plus app `L1` (2026-10-21)

### L1 — paired CSV parsing (closed)

- `engine/PairedAnalyzer.kt`: `splitCsvLine()` implements quoted fields (`"a,b"`), the
  `""` escape, and strips a UTF-8 BOM from the header. Rows and headers use it, so an
  assay name containing a comma no longer shifts every column after it.
- Pinned by `PairedCsvTest` (4 tests): quoted names, escaped quotes, BOM, and a genuinely
  short row that is still rejected.

### L1 — relative-sensitivity CI truncation (closed)

- `engine/PairedAnalyzer.kt`: the plotted CI is still capped at 1.5 for the axis, but when
  the untruncated upper bound exceeds it the result carries a warning naming the real
  value, instead of presenting 1.5 as if the data ended there.

### L1 — permalink was labelled a DOI (closed)

- `engine/CapsuleEngine.kt`: the Markdown export now says **Permalink** (not a DOI), and the
  generator comment states that no registration agency assigned it and that 8 hex chars is
  a 32-bit space.

### Verification

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` (JDK 21) | **309 tests / 44 classes, 0 failures, 0 errors** (was 305) |
| `:app:assembleDebug` | not re-run in this pass |

### Still open

`M5` (Room encryption + `data_class` on claims — needs a schema migration and a product
decision on PHI scope), `L2` (`POST_NOTIFICATIONS` flow, DataStore migration), `M4` residual
(bench `.so` exclusion, Play Asset Delivery), the presigned-URL session-token input, and
`:app:lintDebug`.

## Third pass — `M5`, `L2`, presign UI, lint (2026-10-21)

### M5 — data classification is checked, not asserted (closed, except at-rest encryption)

- `data/Entities.kt`: `ClaimEntity.dataClass` (default `aggregate`) with
  `DATA_CLASS_AGGREGATE` / `DATA_CLASS_PHI`; `fromAnchor(..., dataClass = ...)` so any import path
  can classify a record.
- `data/ConcordanceDatabase.kt`: **version 7** + `MIGRATION_6_7`
  (`ALTER TABLE claims ADD COLUMN dataClass TEXT NOT NULL DEFAULT 'aggregate'`). Existing rows keep
  the class they were pushed under.
- `integration/supabase/SupabaseSyncManager.kt`: `mayLeaveDevice()` is the boundary.
  `pushClaims` skips non-aggregate records, marks them `blocked`, and records a `SyncConflict` with
  the class in the reason (one classified record no longer blocks the queue); the hardcoded
  `put("data_class", "aggregate")` is gone. `publishClaim` refuses a non-aggregate record outright.
- `integration/supabase/SyncMappers.kt`: the payload carries the record's own class; remote rows
  without one are read as `aggregate`.
- Pinned by `DataClassBoundaryTest` (5 tests, Robolectric): default classification, held PHI and
  `deidentified` records, wire payload, and remote mapping both ways.
- Still open: the Room file itself is unencrypted on disk (SQLCipher), which is a separate
  at-rest decision.

### L2 — notification permission is requested (closed)

- `ui/components/NotificationPermission.kt`: one request on Android 13+, remembered in
  `concordance_permissions` so a denial is not re-prompted, invoked once from `MainActivity`.
  The foreground research service notification is no longer silently suppressed.
- Still open: the `EncryptedSharedPreferences` deprecation (needs a DataStore + Keystore migration).

### Presigned URL UI (closed)

- `ui/screens/IntegrationsScreen.kt`: the AWS S3 tab now has a credentials panel — access key,
  secret, **session token (STS, optional)**, region, bucket, optional custom endpoint — wired to
  `setAwsConfig(..., sessionToken = ...)`. Before this, credentials could only arrive through a
  config import, so the session-token path was unreachable from the UI.

### `:app:lintDebug` (closed)

- Executed with JDK 21: **BUILD SUCCESSFUL, 0 errors, 51 warnings**. The six `java.time` `NewApi`
  items recorded earlier do not reproduce: `isCoreLibraryDesugaringEnabled = true` plus the
  `desugar.jdk.libs` dependency are already in `app/build.gradle.kts`.

### Verification

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` (JDK 21) | **314 tests / 45 classes, 0 failures, 0 errors** |
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:lintDebug` | BUILD SUCCESSFUL, 0 errors, 51 warnings |
| `:app:assembleDebug` | not re-run in this pass |

### Still open

`M4` residual (bench `.so` exclusion / Play Asset Delivery — needs a device run of local
inference), Room at-rest encryption, DataStore migration for encrypted prefs, and the
`POST_NOTIFICATIONS` behaviour on a real Android 13+ device (the request path compiles and is
unit-reachable, but the system dialog itself needs hardware).
