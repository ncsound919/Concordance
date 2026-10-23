# Concordance Android — code, claims and integrity audit

Date: 2026-10-21
Status: C1, C2, H1, H2 and M1–M3 are fixed and covered by tests; see
[the remediation log](AUDIT_REMEDIATION_2026-10-21.md) for what changed and what remains.
Scope: `Concordance-main` (Android app, ~25.6k LOC Kotlin across 164 files, plus the Python reference engine in `src/concordance/`)
Method: static read of the app, its manifest and build config; cross-check of the Kotlin engine against the Python reference engine (`src/concordance/correction.py`, `schema.py`); execution of the unit-test suite under two JDKs; three temporary JVM probes (added, run, then removed) to confirm behaviour the code claims to prevent. AWS guidance applied where cloud code is involved.

This audit found **two blockers, three high-severity findings and six medium-severity findings**. The most serious are not crashes: they are cases where the app states something stronger than the code supports — a "citable" number containing `NaN`, a "pristine and untampered" hash that does not cover the claim text, and a job queue that shows fabricated AWS runs as live infrastructure. In a tool whose entire value proposition is "refuse to guess", those are the findings that matter.

---

## 1. Verification baseline

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` with `JAVA_HOME` = JDK 21 | **253 tests, 0 failures** (40 classes) |
| `:app:testDebugUnitTest` with the machine's default `JAVA_HOME` = JDK 17 | **16 of 40 classes fail at class level**: `Failed to create a Robolectric sandbox: Android SDK 36 requires Java 21 (have Java 17)` |
| `:app:assembleDebug` | Previously produced `app-debug.apk` = **123 MB** (uncompressed native libs) |
| `:app:lintDebug` | Not run in this audit (network-bound; README's 0-error claim unverified here) |
| Release build | Not runnable from a clean checkout: signing needs `my-upload-key.jks` / `KEYSTORE_PATH` + `STORE_PASSWORD` / `KEY_PASSWORD`, none of which are in the tree |

The 253-test suite is genuinely green — but only on JDK 21. The README's "JDK 17+" is wrong for `@Config(sdk = [36])` under Robolectric, and nothing in the build pins a toolchain, so the same commit fails on a JDK 17 developer machine.

Three probes were written against the app's own classes and executed under Robolectric. Their output is quoted in the findings below; the probe file was deleted afterwards.

---

## 2. Findings

### C1 — Critical: the Kotlin engine dropped the reference engine's input validation, so invalid anchors produce `NaN` "citable" results and crash capsule generation

**Evidence**
- `app/src/main/java/com/example/concordance/engine/StatisticalEngine.kt:26-42` — `observedOrCi` uses the uncorrected Woolf estimator with no zero-cell guard.
- `.../StatisticalEngine.kt:13-24, 50-58, 93-96` — every missing summary input is silently replaced by a default: `oddsRatio ?: 0.35` in `observedPrevalences`, **but `oddsRatio ?: 1.0`** in `observedOrCi`, `referencePrevalence ?: 0.2075`, `orCiLow ?: (est * 0.7)`, `orCiHigh ?: (est * 1.3)`.
- `model/Models.kt` — `Anchor` is a plain data class with no validation (compare `src/concordance/schema.py:44-58`, which rejects zero-event groups, 100 %-event groups, partial counts, and summary anchors without `odds_ratio`/CI/`reference_prevalence`).

**Confirmed by probe**
```
PROBE zero-cell observedOrCi est=0.0 lo=0.0 hi=NaN
PROBE zero-cell verdict=INCONCLUSIVE reading=... at 0.00× the rate ...
     citable=... observed OR = 0.000 (95% CI 0.000–NaN) ...
PROBE zero-cell capsule=org.json.JSONException: Forbidden numeric value: NaN
PROBE bare-summary observedOrCi est=1.0 lo=0.7 hi=1.3
PROBE bare-summary prevalences pb=0.0525 pn=0.2075
```

**Why it matters**
1. A sparse real-world table (one zero cell — the normal case for rare CNS events) yields `observedCiHigh = NaN`. The engine then formats that into the **citable argument** ("95% CI 0.000–NaN") that the user is invited to paste into a paper, and `CapsuleEngine.buildCapsule` throws on it, so capsule generation dies at the end of the workflow. The reference engine refuses these anchors *at construction*; the port dropped the refusal and kept the arithmetic.
2. For a summary anchor with no reported numbers, the two halves of the engine disagree with each other: `observedOrCi` says OR 1.0 (0.7–1.3) while `observedPrevalences` assumes OR 0.35. The result is an internally inconsistent adjudication, issued with `SUMMARY` grade, i.e. **not** withheld.
3. This is precisely the behaviour the project publicly disclaims. `EvidenceGrade`/easy-mode copy promises "refusing to guess is the correct answer"; the engine guesses three separate numbers, silently.

**Fix**
- Port `schema.py`'s validator into `Anchor` (or a `validateAnchor()` called by every entry point): counts for both groups, `0 < events < total`, summary anchors require `odds_ratio`, both CI bounds, `referencePrevalence`, and `or_ci_low <= or <= or_ci_high`.
- Add Haldane–Anscombe (+0.5) correction *and* an explicit "not estimable" outcome instead of NaN for zero cells; never emit a string containing `NaN`.
- Delete the `?:` defaults, or return `Verdict.WITHHELD` with reason `insufficient_inputs`.
- Add `StatisticalEngineTest` covering: zero cell, 100 %-event cell, missing summary fields, OR outside CI, `points = 1` in `curve()` (`StatisticalEngine.kt:117` divides by `points - 1`), and reference-engine parity for the existing fixtures.

---

### C2 — Critical: the capsule digest does not cover the verdict prose, so "Hash verified! Capsule payload is pristine and untampered" is false

**Evidence**
- `engine/CapsuleEngine.kt:35-58` — `buildCanonicalBody` hashes **only** `inputs` + `results` (+ optional `paired`).
- `.../CapsuleEngine.kt:130-152` — `sha256` is the digest of that canonical body, while the capsule JSON additionally carries `question`, `citable_argument`, `plain_language_verdict`, `assumptions`, `limitations`, `created_utc`, `software_version`, `capsule_version`, `permalink`.
- `.../CapsuleEngine.kt:192-215` — `verifyJson` recomputes only the same subset.
- `ui/ConcordanceViewModel.kt:899` — the UI announces "Generated citable capsule … **(SHA-256 verified)**".

**Confirmed by probe** (tampering only the narrative fields):
```
PROBE tampered plain-language text verify valid=true  message=Hash verified! Capsule payload is pristine and untampered.
PROBE tampered created_utc          verify valid=true  message=Hash verified! Capsule payload is pristine and untampered.
```

**Why it matters**
The existing test (`ExampleUnitTest.testCapsuleTamperVerification`) only edits `results.verdict`, which *is* covered, so the suite certifies a property the implementation does not have. Anyone can rewrite the plain-language verdict, the citable argument, the stated assumptions/limitations, or the creation timestamp of a published capsule and the app will still print "pristine and untampered". For an artifact whose purpose is provenance, that is the integrity story itself.

**Fix**
Hash a canonical form of the **entire** capsule minus the digest field (sorted keys, stable number formatting), and verify the same thing. Version it (`capsule_version = "2"`) since old digests will not match. Extend the tamper test to narrative fields, `created_utc`, and `assumptions`, and add a test that a re-serialised-but-unchanged capsule still verifies.

---

### H1 — High: fabricated AWS workers and "completed" benchmark jobs are presented as live infrastructure

**Evidence**
- `integration/queue/JobQueueManager.kt:71, 74-150` — `init { seedInitialBenchmarkJobs() }` inserts two `COMPLETED` jobs with invented capsule hashes (`citableCapsuleSha256 = "c089fe42…"`, `"91a82bc0…"`) and log lines such as "Worker fargate-worker-us-east-1a claimed task", "10,000 bootstrap resamples … r* = 0.401 (95% CI 0.384–0.418)", "Cryptographic capsule artifact generated and verified".
- `.../JobQueueManager.kt:20-55` — `DEFAULT_WORKER_NODES` invents four workers with fake private addresses (`10.0.12.84 (VPC Private)`, `172.31.4.19 (Batch Fleet)`) and heartbeats manufactured as `System.currentTimeMillis() - 12_000`.
- `ui/components/AsyncJobQueueTab.kt` — no "demo"/"seeded"/"simulated" marker anywhere in the tab (the only simulation disclosure in the UI is on the S3/R2 path).

**Why it matters**
A reviewer, auditor or funder opening the Job Queue sees a healthy Fargate/Batch/Kaggle fleet and two completed runs with cryptographic hashes — none of which exist. This is the same failure mode as C1/C2 (claiming more than the system did) and it is the easiest one for an outside party to discover, which makes it disproportionately damaging.

**Fix**
Either label the seeds unmistakably (`demo = true` flag rendered as a "SAMPLE DATA — not a real run" banner and excluded from counts/telemetry), or delete them and render an honest empty state. Never synthesise `citableCapsuleSha256`, timestamps, or worker heartbeats.

---

### H2 — High: the S3 client cannot write to any bucket created after 2020-09-30, and the "presigned URL" is neither presigned nor readable

**Evidence**
- `integration/aws/S3SigV4.kt:28, 37, 73, 82` — path-style addressing only (`https://s3.<region>.amazonaws.com/<bucket>/<key>`, canonical URI `/<bucket>/<key>`).
- `integration/aws/AwsCloudManager.kt:83` — comment claims "Generates a presigned URL valid for independent scientific review".
- `.../AwsCloudManager.kt:179` — `presignedUrl = "https://$bucket.s3.$region.amazonaws.com/$sKey"` — a plain object URL with no `X-Amz-Signature`, `X-Amz-Credential`, or expiry. S3 objects are private by default, so this returns `403 AccessDenied`.
- `ui/screens/IntegrationsScreen.kt:794` — the UI offers "Copy S3 Presigned URL" for that string.
- `.../AwsCloudManager.kt:120-132` — any upload failure (including the signature/endpoint failure above) falls through to a simulated result; the ViewModel does disclose it (`ConcordanceViewModel.kt:912-918`), so the user is told nothing was uploaded — but the underlying misconfiguration is invisible.

**Why it matters**
AWS supports path-style requests only for buckets created on or before 2020-09-30; newer buckets are virtual-hosted-style only ([S3 path deprecation plan](https://aws.amazon.com/blogs/storage/update-to-amazon-s3-path-deprecation-plan/)). So the primary sync path fails for essentially every bucket a user creates today, then reports "simulated". And the artifact offered for "independent scientific review" is a URL that 403s — the opposite of reproducibility.

**Fix**
- Sign virtual-hosted-style URLs (`https://<bucket>.s3.<region>.amazonaws.com/<key>`, `Host` = the bucket host, canonical URI `/<key>`), with URL-encoded key path segments.
- Either implement real presigning (`X-Amz-Algorithm/Credential/Date/Expires/SignedHeaders/Signature` in the query string) or drop the field and the UI affordance, and set a bucket policy expectation (e.g. `s3:GetObject` for a reviewer role) documented in the README.
- Surface the HTTP status and error code from a failed PUT instead of collapsing it into "simulated".

---

### H3 — High: long-lived IAM user access keys live in a distributed mobile client, with no path to short-lived credentials

**Evidence**
- `integration/aws/AwsCloudManager.kt:43-60` — `accessKey` / `secretKey` held in process-wide `MutableStateFlow`s, set by `updateConfig(...)`, never cleared, no session-token field.
- `S3SigV4.kt` — signer accepts only `accessKey`/`secretKey`; there is no `x-amz-security-token` signed header, so STS/Cognito temporary credentials cannot be used at all.
- `.env.example` — ships `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` placeholders for "AWS S3 Cloud Storage & Compute sync".
- `integration/aws/CloudWatchLogsManager.kt` — same pattern for telemetry upload.
- BYOK keys are stored encrypted (`integration/byok/KeyStore.kt`), but `AgentSettingsStore.readString` still falls back to the legacy **plaintext** prefs file, so an older plaintext key remains readable (`integration/agent/AgentSettingsStore.kt:24-45`).

**Why it matters**
Shipping long-lived IAM user keys inside an APK is the pattern AWS guidance exists to prevent: the credential cannot be revoked per device without breaking every install, cannot be scoped per user with `aws:PrincipalTag`, and leaks permanently if one device is rooted or backed up. The alternative (Cognito Identity / STS `AssumeRole` with a scoped role) is unavailable because the signer has no session-token support.

**Fix**
- Add `sessionToken` to the config and sign `x-amz-security-token`.
- Prefer a Cognito Identity Pool or a small self-hosted token broker issuing 15-minute STS credentials scoped to `s3:PutObject` on `arn:aws:s3:::<bucket>/vault/*`; keep long-lived keys as an explicit, warned "advanced" path.
- Document the minimum IAM policy (no `s3:*`), enable bucket versioning + SSE, and add `clearCredentials()` on sign-out.
- Migrate the plaintext prefs fallback: read once, write to the encrypted store, then delete the legacy file.

---

### M1 — Medium: the suite is JDK-21-only while the README documents "JDK 17+"

**Evidence**: 16 of 40 classes fail with `Android SDK 36 requires Java 21 (have Java 17)`; `app/src/test/**` uses `@Config(sdk = [36])`; `gradle.properties` and `app/build.gradle.kts` declare `compileOptions` for Java 11 but no Gradle Java toolchain; `README.md` says "the toolchain needs JDK 17+ (JDK 21 is what this repo is verified with)".

**Fix**: add `kotlin { jvmToolchain(21) }` (or `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`) so the build fails fast with a clear message rather than producing 16 confusing sandbox errors, and correct the README to "JDK 21 required".

---

### M2 — Medium: the on-device llama.cpp server is unauthenticated on loopback

**Evidence**: `local/LocalLlmService.kt:143-151` starts `llama-server --host 127.0.0.1 --port <fixed>` with no `--api-key`; `LocalModels.LLM_PORT` is a constant, and `LocalExtractionClient` talks to it over cleartext localhost (permitted by `res/xml/network_security_config.xml`).

**Why it matters**: on a shared/managed device, any other app (or an ADB shell on a debuggable build) can query the model and read completions — including any PHI pasted into it. The port is predictable.

**Fix**: pass `--api-key` with a per-session random token generated at service start and stored in memory only; hand it to `LocalExtractionClient`; pick an ephemeral port and publish it via the service status.

---

### M3 — Medium: the exported Tasker receiver lets any app drive background work and grow prefs without bound

**Evidence**: `AndroidManifest.xml:62-70` — `TaskerTriggerReceiver` is `exported="true"` with four actions and no `android:permission`; `integration/tasker/TaskerBridge.kt:37-45` appends to a single `SharedPreferences` string with no size cap, and `SYNC_CAPSULES` / `RUN_LEARNING_ROUND` / `RE_ADJUDICATE` can trigger network and compute.

**Why it matters**: any installed app can queue thousands of entries (a large prefs file is loaded synchronously into memory; this is a cheap way to make the app slow or ANR) and can repeatedly trigger sync/learning work. No secrets are exposed, so impact is availability and battery, not disclosure.

**Fix**: protect the receiver with a signature-level `android:permission`, bound the queue (drop oldest beyond ~50), and rate-limit `SYNC_CAPSULES`/`RUN_LEARNING_ROUND`.

---

### M4 — Medium: 123 MB APK, benchmark binaries shipped, and four unused dependency families

**Evidence**
- `app/src/main/jniLibs/arm64-v8a` = **273 MB** in source; `app/libs/sherpa-onnx-1.13.8.aar` = 48 MB; built debug APK = **123 MB**.
- Shipped tool binaries that no shipped feature calls: `libllama-bench-impl.so`, `libllama-batched-bench-impl.so`, `libllama-perplexity-impl.so`, `libllama-quantize-impl.so`, `libllama-completion-impl.so`, `libllama-cli-impl.so`, `libllama-fit-params-impl.so`, `libggml-rpc.so` (~10 MB+ of the extract).
- `app/build.gradle.kts` declares `firebase.ai`, `firebase.appcheck.recaptcha` + `firebase.appcheck.debug`, `retrofit`, `converter.moshi`, `moshi.kotlin` (+ KSP codegen), `androidx.navigation.compose`, `logging.interceptor` — **zero** Kotlin imports of any of them (`grep` over `app/src/main/java`).
- `packaging { jniLibs { useLegacyPackaging = true } }` forces every `.so` to be extracted at install, roughly doubling on-device footprint.

**Why it matters**: Firebase AI/AppCheck pull Play-services code and a data-safety declaration question ("does your app share data with third parties?") for code that never runs; Retrofit/Moshi add refactors' worth of dead surface; the llama bench tools are dead weight in every install. Play's base-module size limits make this unshippable without an AAB split or Play Asset Delivery.

**Fix**: delete the bench/perplexity/quantize/CLI `.so` files, drop the unused dependencies (and the KSP moshi processor), and if the APK must stay small, move the llama runtime to a Play Asset Delivery on-demand module or trim to a single quantization. Keep `useLegacyPackaging` only if the server binary genuinely needs extraction.

---

### M5 — Medium: at-rest data is unencrypted, and PHI classification is asserted rather than checked

**Evidence**: Room database created without an `openHelperFactory`/SQLCipher (`grep` for `openHelperFactory` returns nothing); `integration/supabase/SupabaseSyncManager.kt:135-141, 158-164` unconditionally sets `data_class = "aggregate"` on push, despite the comment "PHI-class records are refused at the boundary" — there is no check.

**Why it matters**: paired tissue/plasma rows and claims are stored in a plaintext SQLite file, readable on a rooted device or via a debuggable build's `run-as`. And a claim whose content is PHI would be uploaded labelled aggregate, defeating server-side routing rules that rely on that field.

**Fix**: add `dataClass` to the claim entity and refuse to push anything not explicitly `aggregate` (or require an explicit per-record acknowledgement); if PHI is in scope at all, use SQLCipher or keep PHI strictly in memory.

---

### L1 — Low: paired-analysis and capsule polish
- `engine/PairedAnalyzer.kt:170` clamps the relative-sensitivity **upper** CI at 1.5, which reads as "we know it is below 1.5" when the data may only support "below 3.1". Clamp for display only, or note the truncation.
- `PairedAnalyzer.parseCsv` splits on `,` with no quote/BOM handling and requires at least `required.size` columns rather than the header's width; a quoted assay name silently shifts every column after it.
- `CapsuleEngine.kt:99` — `permalink = "concordance.app/c/<8 hex>"` is presented as "DOI / Permalink" in the Markdown export, but no DOI is registered and 8 hex chars gives a 32-bit id (birthday collisions at ~77k capsules).

### L2 — Low: Android hygiene
- `POST_NOTIFICATIONS` is declared but never requested (only `RECORD_AUDIO` has a launcher, `AgentScreen.kt:406`), so the foreground-service notifications are suppressed on Android 13+.
- `EncryptedSharedPreferences` is deprecated upstream; plan a migration to DataStore + Keystore.
- `AwsCloudManager.md5Hex` and `S3SigV4.signGet`-adjacent helpers are unused dead code.
- `ui/ConcordanceViewModel.kt:899` says "(SHA-256 verified)" at generation time; it is only a computed digest, and per C2 it covers less than the user will assume.

---

## 3. Claims vs. code

| Claim | Where | Reality |
|---|---|---|
| "The client performs no arithmetic on any number, so the two views cannot disagree" | `README.md` | True for the *agent's* easy mode; the on-device engine's `StatisticalEngine` does all the arithmetic and C1 shows it can emit `NaN` |
| "Refusing to guess is the correct answer here" | `agent/EasyMode.kt` | The Kotlin engine guesses OR 0.35/1.0 prevalence 0.2075 when inputs are missing (C1) |
| "Hash verified! Capsule payload is pristine and untampered" | `engine/CapsuleEngine.kt` | Digest covers `inputs`+`results` only (C2) |
| "Generates a presigned URL valid for independent scientific review" | `integration/aws/AwsCloudManager.kt:83` | Not presigned; returns 403 (H2) |
| "Audio never leaves the phone" | `README.md` | Holds for dictation (on-device recogniser), but the app also offers Gemini/BYOK model paths — the README should say so |
| "183 unit tests, all green" | `README.md` | 253 now, green on JDK 21; 16 classes fail on JDK 17 (M1) |
| "JDK 17+" | `README.md` | JDK 21 required by Robolectric SDK 36 (M1) |

---

## 4. What is genuinely good

Worth stating plainly, because most of this code is careful:

- **Refusal semantics** are modelled properly in `AgentClient` (`422 → AgentOutcome.Refused`, never retried) and mapped through `parseBody` without leaking bodies.
- **No response bodies are logged**: 11 `Log.*` calls in the whole app, none carrying payloads, keys, or URLs with query strings; provenance records a SHA-256 instead.
- **Transport hygiene**: `network_security_config.xml` is HTTPS-only except `10.0.2.2`/`localhost`/`127.0.0.1`; the cleartext failure path explains itself.
- **Secrets in the right place**: `EncryptedSharedPreferences` for agent settings and BYOK keys, `allowBackup="false"`, `dataExtractionRules`, FileProvider not exported.
- **Easy mode is a translation layer** (`EasyMode.kt`) with mandatory absence/disclaimer caveats and no arithmetic — and it is tested (`EasyModeTest`).
- **Refusals are first-class results** in the verdict model (`WITHHELD`, `THRESHOLD_ONLY`) rather than zeros.
- **The statistical model itself is sound**: the correction algebra, delta-method variance, significance-threshold bisection and Fisher test all match the Python reference (`correction.py`) line for line, and the null-crossing identity (`OR = 1` at `r*`) holds exactly.

---

## 5. Remediation plan

**Do first (before any external demo or release)**
1. C2 — hash the whole capsule; version it; extend the tamper test. *(Small change, largest credibility gain.)*
2. C1 — port `schema.py` validation; remove invented defaults; handle zero cells with an explicit "not estimable" outcome; add `StatisticalEngineTest`.
3. H1 — label or remove the seeded jobs/workers.
4. M1 — pin the Java 21 toolchain; fix the README.

**Plan (next milestone)**
5. H2 — virtual-hosted-style signing + real presigning (or remove the affordance) + surface S3 error codes.
6. H3 — session-token support, then STS/Cognito credentials; documented least-privilege bucket policy; purge the legacy plaintext prefs.
7. M4 — strip the bench binaries and unused dependencies; re-measure APK size and decide on Play Asset Delivery.
8. M2/M3 — API key on the local model server; permission-protected, bounded Tasker receiver.

**Delegate / defer**
9. M5 — SQLCipher plus an explicit `dataClass` on claims.
10. L1/L2 — CI clamp disclosure, CSV quoting, notification permission, DataStore migration.

**SMART goals for the next pass**
- 100 % of `StatisticalEngine` public functions covered by tests including the five invalid-input cases, with `./gradlew :app:testDebugUnitTest` green on a fresh checkout with only `JAVA_HOME` set to JDK 21.
- Tampering any field of a capsule JSON causes `verifyJson` to return `false` (property test over all top-level keys).
- Debug APK ≤ 60 MB, with no `.so` whose symbol table lacks a caller in `app/src/main`.
- Zero UI strings that present synthetic data without a visible "sample" marker.

---

## Appendix — how the probe evidence was produced

`app/src/test/java/com/example/AuditProbeTest.kt` was added temporarily with three `@Config(sdk = [36])` Robolectric tests calling `StatisticalEngine` / `CapsuleEngine` directly, run with `JAVA_HOME` = JDK 21 (`:app:testDebugUnitTest`), and deleted after capture. Suite result with the probe present: 256 tests, 1 failure (an assertion in the probe itself, corrected by observation: the bare summary anchor returns OR 1.0, not 0.35). Re-create the probes to reproduce the quoted output.
