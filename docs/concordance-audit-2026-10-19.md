# Concordance — Deep Audit (Android app + concordance-web)

**Audit date:** 2026-10-19 (updated 2026-10-19)  
**Audited artefacts**

| Artefact | Path | Stack |
|---|---|---|
| Android app | `C:\Users\tap45\Downloads\eco\Concordance-main` | Kotlin, Compose, Room, OkHttp, WorkManager |
| Website | `C:\Users\tap45\Downloads\eco\Oncology-Ecosystem-main\Oncology-Ecosystem-main\apps\concordance-web` | Next.js 14 (App Router), TypeScript, Supabase |

**Method**

- Full source review of both apps (all first-party `.kt`, `.ts`, `.tsx`, `.sql`, config, manifests).
- `npm run typecheck` → passes.
- `npm run build` → passes (Next.js 14.2.35).
- `npm ls`, `npm audit` → dependency review.
- Android unit-test report inspected → **183 tests, 0 failures**.
- The website was run locally against the configured Supabase project, and read-only probes were made.

**Severity model:** Critical = immediate credential/data-loss or trust break; High = exploitable or breaks a core promise; Medium = reliability/privacy/quality; Low = polish/docs.

---

## Executive summary

Concordance has been **significantly hardened** since the initial audit. The on-device statistical engine remains the strongest part of the system, and the website's security boundary is now much tighter.

**What's been fixed:**
- Hard-coded secrets removed from Android source; `.env.example` uses placeholders
- S3/R2 uploads return `SIMULATED` instead of fake `SYNCED`
- Kaggle compute returns `FAILED` with honest messages
- Fabricated papers removed (`BENCHMARK_PAPERS = emptyList()`)
- Verify capsule route completely rewritten with proper hash + timestamp verification
- API routes use `getAuthContext()`, `requireUser()`, `assertTeamRole()`, Zod validation
- Middleware protects `/dashboard`, `/lab`, `/settings`, `/moderation`
- Security headers (CSP, HSTS, COOP, CORP) all set
- `allowBackup="false"`, `networkSecurityConfig` declared
- Core-library desugaring enabled
- SyncMappers `version` column removed (schema drift fixed)
- RLS migration `0004_security_roles_timestamps.sql` written with comprehensive policies

**What still needs attention:**
1. **Apply RLS migration 0004** to the database (the policies exist in the migration file but may not be live)
2. **Rotate the Supabase service-role key** (was previously committed in `.env.example`)
3. Some remaining lower-priority items (see below)

---

## Critical findings

### C-1. Production credentials were committed — key rotation still needed

**Status: Partially fixed**

**Fixed:**
- AWS access key, Kaggle token, and Cloudflare R2 token removed from Android source code.
- `.env.example` now uses `YOUR_SERVICE_ROLE_KEY` placeholder.

**Still open:**
- `.env.local` contains the real Supabase service-role key. While `.env.local` is gitignored, this key was previously committed in `.env.example` and should be **rotated**.

**Fix:**
1. Rotate the Supabase service-role key now.
2. Revoke/rotate the Supabase JWT secret and force re-login.
3. Review AWS CloudTrail / S3 access logs, Kaggle activity and R2 logs for the exposure window.
4. Add a CI `gitleaks`/`trufflehog` gate.

---

### C-2. Website authorization — FIXED

**Status: Fixed**

All user-facing API routes now use proper authorization:

| Route | Authorization |
|---|---|
| `api/claims` | `getAuthContext()`, `requireUser()`, Zod validation, PHI guard |
| `api/capsules` | `getAuthContext()`, `requireUser()`, Zod validation, PHI guard |
| `api/publications` | `requireUser()`, `assertCanPublish()` with ownership/team checks |
| `api/teams/[id]/members` | `requireUser()`, `assertTeamRole()` with lead/curator check |
| `api/moderation` | `requireRole("reviewer")` before any data access |
| `api/challenges` | `requireUser()`, RLS checks `can_read_claim` |
| `api/verify/capsule` | `getAuthContext()`, proper hash + timestamp verification |

Remaining `createAdminClient()` usage is limited to:
- Admin-only operations (v1 API routes, billing, webhooks)
- Notification writes (no permissive insert policy on notifications)
- Operations that legitimately need to bypass RLS

---

### C-3. RLS policies — migration written, needs to be applied

**Status: Migration file exists, needs `supabase db push`**

Migration `0004_security_roles_timestamps.sql` contains comprehensive, well-written policies:

- **Claims/Capsules**: owner, team member, public, or reviewer/admin can read; only owner can insert; owner/lead/curator/admin can update; owner/lead/curator/admin can delete
- **Challenges**: author or claim-visible users can read; author can insert with `can_read_claim` check; author/admin can update/delete
- **Publications**: public, owner, or reviewer/admin can read; only publisher can insert
- **Teams**: public, owner, member, or reviewer/admin can read; owner can insert; owner/lead/admin can update/delete
- **Team memberships**: member or reviewer/admin can read; lead/curator/owner can insert/update/delete
- **Comments**: artifact-visibility-based read; author can insert; author/admin can delete
- **Moderation flags**: reviewer/admin can read/update; authenticated users can insert
- **Profiles**: public read; self update; privilege escalation blocked by trigger
- **Privilege escalation guard**: trigger prevents non-admin users from changing `role` or `reputation`
- **Optimistic concurrency**: `bump_version()` trigger on claims/capsules/challenges

**The key question: has this migration been applied to your Supabase project?**

Run this to check:
```sql
-- In Supabase SQL editor
select * from pg_policies where schemaname = 'public' and policyname = 'claims_read';
```

If no rows returned, the migration hasn't been applied. Run:
```bash
cd apps/concordance-web
supabase db push
```

---

### C-4. Capsule verification — FIXED

**Status: Fixed**

The verify capsule route has been completely rewritten:
- `verified = hashMatch === true && timestamp.verified === true`
- Properly distinguishes between a provenance digest and an RFC 3161 timestamp token
- Uses `getAuthContext()` and `createPublicSupabase()` for anonymous users
- Returns honest `reason` strings on failure

---

### C-5. Phone→web sync — partially fixed

**Status: Partially fixed**

**Fixed:**
- `version` column removed from `SyncMappers.claimToSupabase`, `capsuleToSupabase`, and `challengeToSupabase` — the schema drift that caused every push to fail is resolved.

**Still open:**
- RLS: no `UPDATE` policies on claims/capsules/challenges for non-owners (but migration 0004 adds these — needs to be applied)
- Failure masking: `SupabaseSyncWorker.doWork()` still ignores both `Result`s and always returns `Result.success()`
- Cursor timestamps interpolated unencoded
- No PHI/data-class guard at the Android sync push boundary

**Fix:**
- Apply migration 0004 (adds UPDATE/DELETE policies)
- Make the worker return `Result.retry()` when pull/push fails
- URL-encode cursor values

---

### C-6. Simulated integrations — FIXED

**Status: Fixed**

- **S3/R2 uploads**: Both managers return `SIMULATED` status with `simulated = true` when the real call fails
- **Kaggle compute**: Returns `FAILED` with honest message
- **Fabricated papers**: `BENCHMARK_PAPERS = emptyList()`
- **Gemini path**: Removed; `CLOUD_MODEL` engine labeled as "not configured"

---

### C-7. Fabricated or synthetic research data — mostly fixed

**Status: Mostly fixed**

**Fixed:**
- Hard-coded benchmark papers removed
- Seed data files removed from `concordance-web/scripts/`
- Registry page shows "No claims have been published yet" when empty (no silent fallback)
- Kaggle preset datasets properly labeled as `synthetic`

**Still open:**
- The live Supabase database may still contain demo claims/capsules seeded into the public registry. Clean these up after applying migration 0004.

---

## High findings

### H-1 through H-10 — all fixed or partially fixed

| Finding | Status |
|---|---|
| H-1. Cleartext HTTP | Fixed — `networkSecurityConfig` declared |
| H-2. `allowBackup` | Fixed — set to `false` |
| H-3. Page/API exposure | Fixed — middleware protects all sensitive routes |
| H-4. Next.js vulnerabilities | Open — upgrade to patched version |
| H-5. No web tests | Open — add Vitest/Playwright |
| H-6. `java.time` on API 24 | Fixed — core-library desugaring enabled |
| H-7. PHI guard | Partially fixed — web API rejects PHI; Android sync boundary still missing |
| H-8. Input validation | Partially fixed — Zod on most routes; rate limiting still missing |
| H-9. Security headers | Fixed — CSP, HSTS, COOP, CORP all set |
| H-10. Cookie handling | Partially fixed — `getAll`/`setAll` used in middleware |

---

## Medium findings

- **M-1.** Website product surface is mostly placeholder (dashboard stats hard-coded 0, lab "Coming soon")
- **M-2.** No LICENSE despite "Open Source" claim
- **M-3.** Documentation is stale (README says 114 tests, actual is 183)
- **M-4.** `challengeClaim` overwrites original counts
- **M-5.** `notes` grows without bound
- **M-6.** Exported Tasker receiver has no permission
- **M-7.** Certificate pinning not implemented
- **M-8.** Error messages leak internals
- **M-9.** Query interpolation not URL-encoded
- **M-10.** Access token refresh fallback returns expired token
- **M-11.** `z95` is a dead tuning parameter
- **M-12.** No foreground notification for long sync work
- **M-13.** `allowBackup` boilerplate (fixed)
- **M-14.** Registry `revalidate` ineffective (dynamic route)
- **M-15.** Accessibility/UX gaps

---

## What is working well

- **The statistical core** is the real asset: explicit assumptions, delta-method/Wilson intervals, Fisher exact test, feasibility guards, r*/r_sig thresholds, power/sample-size calculations
- **Refusal as a result** honoured in AgentClient and EasyMode
- **Evidence grading** and withholding for abstract-only/synthetic inputs
- **Anti-collapse guard** refuses self-generated anchors
- **Append-only revisions** with revert-as-new-revision
- **Encrypted storage** for Supabase sessions and BYOK keys
- **183/183 Android unit tests green**
- **Website builds and typechecks cleanly**
- **Comprehensive RLS migration** (0004) with owner/team/public/reviewer policies, privilege escalation guards, and optimistic concurrency
- **Properly secured API routes** with `getAuthContext()`, `requireUser()`, `assertTeamRole()`, Zod validation
- **Honest simulation states** — S3/R2 return SIMULATED, Kaggle returns FAILED
- **No fabricated research material** — papers removed, seed data removed, synthetic data properly labeled

---

## Prioritised remediation plan

### P0 — before any further use (same day)

1. **Apply RLS migration 0004** to your Supabase project:
   ```bash
   cd apps/concordance-web
   supabase db push
   ```
2. **Rotate the Supabase service-role key.** Invalidate Supabase refresh tokens.
3. **Verify RLS is live** by running:
   ```sql
   select * from pg_policies where schemaname = 'public' and policyname = 'claims_read';
   ```
4. **Fix the sync worker** to propagate failures (`Result.retry()`).

### P1 — this week

5. Upgrade Next.js and re-run `npm audit`
6. Add rate limiting and atomic counters to the API
7. Add a LICENSE and correct documentation
8. URL-encode cursor values in sync queries
9. Clean demo data from the live registry

### P2 — next sprint

10. Add web unit/E2E/RLS tests and CI
11. Implement or remove placeholder features
12. Generalise the engine beyond ESR1/brain
13. Add observability/audit trails

---

## Verification checklist for the fixes

```bash
# RLS is live
# In Supabase SQL editor:
select * from pg_policies where schemaname = 'public' and policyname = 'claims_read';
-- Should return 1 row with the owner/team/public/reviewer policy

# Secrets
gitleaks detect --source . --verbose

# Web dependency/security
cd apps/concordance-web
npm audit --omit=dev
npm run typecheck && npm run build
curl -I http://localhost:3001 | grep -Ei 'content-security-policy|strict-transport|referrer|x-frame'

# Android
./gradlew :app:testDebugUnitTest
```

---

## Appendix — key file references

| Area | File |
|---|---|
| RLS migration (needs push) | `apps/concordance-web/supabase/migrations/0004_security_roles_timestamps.sql` |
| Auth helpers | `apps/concordance-web/lib/authz.ts` |
| Web API routes | `apps/concordance-web/app/api/**/route.ts` |
| Verify capsule (fixed) | `apps/concordance-web/app/api/verify/capsule/route.ts` |
| Middleware (fixed) | `apps/concordance-web/middleware.ts` |
| Security headers (fixed) | `apps/concordance-web/next.config.mjs` |
| Sync mappers (fixed) | `.../integration/supabase/SyncMappers.kt` |
| Sync manager | `.../integration/supabase/SupabaseSyncManager.kt` |
| S3 manager (fixed) | `.../integration/aws/AwsCloudManager.kt` |
| R2 manager (fixed) | `.../integration/cloudflare/CloudflareR2Manager.kt` |
| Kaggle manager (fixed) | `.../integration/kaggle/KaggleComputeManager.kt` |
| Science papers (fixed) | `.../integration/science/SciencePaperManager.kt` |
| Android manifest (fixed) | `Concordance-main/app/src/main/AndroidManifest.xml` |
| Android build (fixed) | `Concordance-main/app/build.gradle.kts` |

---

*This audit was performed read-only against the local copies and the configured Supabase project. No production data was modified.*

---

## Update — 2026-10-19 (evening): Cloudflare AI offload + agent auth

### Added

| Item | Detail |
|---|---|
| `concordance-ai` Worker | `https://concordance-ai.tap4500.workers.dev`, source `C:\Users\tap45\concordance-ai-worker` |
| Offload routes | `/v1/chat/completions`, `/v1/images/generations`, `/v1/audio/transcriptions`, `/v1/audio/speech`, `/v1/usage` |
| Budget guard | Fails closed at 9,500 Neurons/day (free allowance 10,000; account is Workers Paid, so a guard is mandatory to avoid spend) |
| Agent routes | `GET /api/llm/status`, `POST /api/llm/complete`, `/api/llm/image`, `/api/llm/transcribe`, `/api/llm/speak` (`app/cloudflare_ai.py`) |
| Agent launcher | `C:\Users\tap45\concordance-ai-worker\start_agent.ps1` (sets auth + Cloudflare env) |

### Fixed

- **CRITICAL — agent had no authentication.** `AGENT_API_KEYS` was unset, so the
  publicly tunneled agent accepted unauthenticated writes. Auth is now enforced
  (verified: `POST /api/adjudicate` returns **401** through the public tunnel
  without a key, **422** with the app key).
- **Receipt signing key** was the shared dev default; replaced with a random
  `AGENT_RECEIPT_KEY` (guard in `main.py` now passes).

### Verified end-to-end

| Test | Result |
|---|---|
| Agent → Worker → Qwen3-30B, non-PHI | 200, correct RECIST answer, 0.54 Neurons |
| Agent PHI refusal (`data_class: PHI`) | 403 as designed |
| FLUX.2 Klein 4B `1024x1024` | 200, 104 KB PNG, 125.68 Neurons |
| Whisper large-v3-turbo on MeloTTS output | 200, transcript returned, 4.66 Neurons |
| MeloTTS speech | 200, 392 KB MP3, 1.96 Neurons |
| Public tunnel, no key | 401 |

### Open

- **Credential mismatch (suspected).** `aws_access_key_id` (`9baeda…`, 32 hex) and
  `aws_secret_access_key` (`274d…`, 64 hex) match the **Cloudflare R2** key format,
  not AWS (`AKIA…` + 40-char secret). The S3 upload test will likely fail; confirm
  which bucket these belong to before testing.
- PHI policy: Workers AI is managed; keep `AGENT_ALLOW_MANAGED_PHI` unset.
- Supabase service-role key still pending rotation (user action).


## Cloudflare edge layer (2026-10-20)

The stack now uses Cloudflare beyond inference. New Worker `concordance-edge`
(`https://concordance-edge.tap4500.workers.dev`) adds durable state, queueing,
workflows, object storage and literature retrieval.

| Product | Resource | Status |
|---|---|---|
| D1 | `concordance-edge` | live: jobs, events, evidence, daily counters |
| Durable Object | `JobHub` | live: per-job state + WebSocket fan-out |
| Queues | `adjudication` + `adjudication-dlq` | live: 3 retries then DLQ |
| Workflows | `adjudication-workflow` | live: submit -> poll -> archive |
| R2 | `concordance-capsules` | live: capsule JSON archived on completion |
| Vectorize | `concordance-evidence` (1024d cosine) | live: bge-m3 embeddings |
| AI Gateway | `concordance` | live: cache 3600s, 120 req/min, logs |
| Cron | daily | counters + 90-day event pruning |
| KV | `concordance_edge` | reserved |

AI Gateway is also wired into `concordance-ai`, so repeated chat/image/STT/TTS
requests are served from cache with no neuron charge. Verified: the second
identical chat call returned the cached response byte-for-byte.

### Verified end-to-end

| Test | Result |
|---|---|
| Edge auth, no key | 401 |
| Queue -> agent -> edge state publish | job `completed`, verdict `supported` |
| Capsule archived to R2 | 200, JSON returned via `/api/capsules/...` |
| Durable Workflow, new job id | `complete`, verdict `supported`, D1 row created |
| WebSocket snapshot | connected, `queued` state received |
| Evidence index + search | 1 chunk, cosine score 0.769, metadata intact |
| R2 S3 credentials (SigV4, region `auto`) | PUT 200, GET 200 |

### Credential mismatch resolved

The 32/64-hex pair is a valid **Cloudflare R2** S3 key. It was tested directly
against `https://a7ce29a9eb21fc83bf09dd954b861722.r2.cloudflarestorage.com`
with path-style SigV4 and region `auto`: PUT and GET both returned 200. The app
config therefore points the S3 manager at R2 (`aws_s3_endpoint`), which makes
capsule sync work today. Real AWS S3 (`AKIAQDBCFRPLE3DCWFF2`) still needs its
secret access key; it is not in `opencode api.txt`.

### Agent integration

`start_agent.ps1` now sets `EDGE_URL` and `EDGE_KEY`. Completed jobs publish to
`/api/jobs/:edge_id/state` once; the edge id travels in the job payload as
`edge_job_id` and is excluded from the agent content hash, so dedup is unchanged.
Publishing is a no-op when the env vars are absent.

### Not adopted (deliberately)

- Cloudflare Access on the agent tunnel would require service-token headers in
  every Android request; agent API-key auth already covers this.
- Hyperdrive only helps direct Postgres access; the apps use the Supabase SDK.
- Images/Stream/Browser Rendering/Turnstile add cost or surface without a
  current product need.

## On-device models (Galaxy S25 Ultra)

Installed and verified on 2026-10-19; final model lineup updated 2026-10-20.
Nothing here calls a cloud service.

| Component | Location | Status |
| --- | --- | --- |
| llama.cpp b11748 (Android arm64, CPU-only) | APK `nativeLibraryDir` (adb-era copy in `/data/local/tmp/concordance/llama`) | runs |
| Qwen3.8-4B Q4_K_M (2.78 GB, small tier) | app external files `.../files/models` | verified 2026-10-20 |
| Ministral 3 8B Reasoning 2512 Q4_K_M (5.20 GB, heavy tier) | app external files `.../files/models` | verified 2026-10-20 |
| Kokoro multi-lang v1.0 (54 voices) | app external files `.../files/kokoro` | synthesises on device |
| sherpa-onnx TTS CLI + NDK r28c libc++ | `/data/local/tmp/concordance/sherpa` | runs |

The adb-era Qwen3.5-4B and MedGemma 1.5 4B copies were deleted on 2026-10-20 from
`/data/local/tmp/concordance/models` and `/sdcard/Download/models`.

Benchmarks (`llama-bench`, 64-token prompt / 16-token generation):

| Backend | pp | tg | Quality |
| --- | --- | --- | --- |
| CPU (`-ngl 0`) | 83.7 t/s | 17.3 t/s | correct |
| OpenCL Adreno 830 (`-ngl 99`) | 175.2 t/s (pp128) | 17.1 t/s | corrupt (`888888…`) |

Qwen3.5 is hybrid: `layer_types` alternates three `linear_attention` layers with
one `full_attention` layer (`full_attention_interval: 4`). The OpenCL backend has
no kernels for the linear-attention ops, so every GPU-offloaded token is wrong.
Generation speed is identical on CPU, so the on-device configuration is CPU-only
(`-ngl 0 -t 6`). The Hexagon/HTP backend cannot open a DSP session from
`/data/local/tmp` (`FASTRPC 0x6c` requires a signed skel deployment).

Kokoro: no stock voice is labelled Black/African-American; `VOICES.md` labels by
locale and gender only. Best male US English voices are `am_michael` (sid 16) and
`am_puck` (sid 18), both grade C+; `am_onyx` is deeper but grade D. Comparison WAVs
are in `C:\Users\tap45\Downloads\kokoro-voice-samples`.

The app's original `KokoroTtsSynthesizer` could not use these files: it expects a single
`voices.bin` style vector, a `vocab.json`, and an `espeak-ng` child process, and Android
blocks executing binaries from app storage. It is also not wired into chat (`ollama_url`
was stored but nothing read it). Both gaps are now closed by the packaging work below.

## On-device packaging inside the app (2026-10-19, later)

The runtime and the speech engine now ship **inside the APK**; only the weights stay
outside it.

| Piece | Where it lives | Why |
| --- | --- | --- |
| `libllamaserver.so` + `libllama*.so` + `libggml*.so` (**CPU-only build**) | `app/src/main/jniLibs/arm64-v8a/` | Android only grants execute permission to files under `nativeLibraryDir`; naming the executable `lib*.so` makes the build extract it with mode 0755. The CPU build avoids `libOpenCL.so`, which the app namespace cannot link against. |
| sherpa-onnx 1.13.8 (Kokoro, in-process G2P) + its ONNX Runtime | `app/libs/sherpa-onnx-1.13.8.aar` | Removes the `espeak-ng` child-process requirement. The standalone `onnxruntime-android` dependency was removed: two `libonnxruntime.so` files cannot coexist. |
| `libc++_shared.so` (NDK r28c) | `jniLibs/arm64-v8a/` | Required by the sherpa JNI. |
| GGUF chat model | `Android/data/com.aistudio.concordance.rkzv/files/models/` | 2.3–2.7 GB each; re-importable, never in the APK. |
| Kokoro model/voices/tokens/espeak-ng-data | `Android/data/com.aistudio.concordance.rkzv/files/kokoro/` | 356 MB; no storage permission needed. |

New code:

- `local/LocalModels.kt` — path/layout contract + availability checks.
- `local/LocalLlmService.kt` — foreground service (`specialUse`) that starts
  `libllamaserver.so` with `LD_LIBRARY_PATH = nativeLibraryDir`, tails its log, polls
  `/health`, and publishes a `StateFlow` status (`Stopped`/`Starting`/`Ready`/`Failed`).
  A partial wake lock is held only while the server runs.
- `voice/SherpaKokoroSynthesizer.kt` — Kokoro through sherpa-onnx, speaking **`bm_lewis`**
  (sid 27) via `AudioTrack` (PCM float, static mode). `SpeechOutput.forContext` now returns
  it, else the system voice.
- Settings → BYOK → "ON-DEVICE MODELS": model picker (from the external files dir),
  Start/Stop, live status line, Kokoro file check, and a "Test voice" button.

Two defects found and fixed during packaging:

1. sherpa-onnx aborts with `exit(255)` (process dies with no Java stack trace) when an
   `AssetManager` is passed together with absolute model paths. `OfflineTts(null, config)`
   is required for SD-card weights. Before the fix, tapping "Test voice" silently killed
   the app; `dumpsys activity exit-info` reported `reason=1 (EXIT_SELF)`.
2. Files pushed with `adb` are owned by `shell`; the app can see the directory but
   `canRead()` is false under FUSE, so the model list rendered empty. `chmod -R 777` on the
   two directories fixes it (documented in `README-phone.md`).

Verified on device: health `{"status":"ok"}`; Qwen3.8-4B returned correct extraction JSON
on BRCA2, ESR1 Y537S and negative-control abstracts (12.7–14.6 s each); Ministral 8B
returned correct JSON in 24.2 s; Kokoro `bm_lewis` 61,576 samples in 1.2 s with real
`AudioTrack` playback; Start/Stop toggles the process. The full app path was verified on
2026-10-20: a live search returned 49 records, and the on-device LLM rendered the ESR1
Y537S claim (brain 1/1, other 0/0) in 15.9 s with no regex fallback.

### Model selection (final, 2026-10-20)

`MODELS.md` carries the full comparison and the Hugging Face variants that were evaluated.

| Tier | Model | Size | Why |
| --- | --- | --- | --- |
| Small | **Qwen3.8-4B-Distill Q4_K_M** | 2.78 GB | Full-parameter distill of Qwen3.8 2.4T into the existing Qwen3.5-4B architecture. Publisher lm-eval: MMLU 35.4 → 55.3. Correct structured extraction on all three test abstracts. |
| Heavy | **Ministral 3 8B Reasoning 2512 Q4_K_M** | 5.20 GB | Official Mistral quant. Long reasoning; ~6–7 t/s on CPU, so a full thinking answer takes minutes. |

Rejected: MedGemma 1.5 4B (no general community upgrade; failed the MSI-H question again),
MedGemma-GRPO (unvalidated, MedGemma 1.0 base), Qwen3.5-4B-Medical-Reasoning (2k-sample
LoRA on a Heretic base, no benchmark), MedGemma CRIMSON and narrow LoRAs (single-task),
Gemma-4-E2B medical (PubMedQA-tuned but narrow and larger), and the Heretic/coding
Ministral variants.

Three integration defects fixed:

1. `LocalExtractionClient` did not send `enable_thinking: false`. Qwen3.x spent the entire
   512-token budget on `reasoning_content`, returned empty `content`, and silently fell
   back to the regex extractor. Fixed.
2. The response-envelope reader started at the `"content"` key and expected a quote, but
   the next character is `:`. Every successful reply decoded to `""` and silently fell
   back to the regex extractor. `extractChatContent()` now skips the colon; the new
   `LocalExtractionClientTest` cases cover empty and populated envelopes.
3. `LocalModels.PREFERRED_PREFIXES` now selects `Qwen3.8` first when several GGUFs exist.

Caveat: local models are drafting-only. MedGemma and Ministral both answered "yes" to
adjuvant chemo for stage II MSI-H colon cancer; Qwen3.8 answered "yes" in no-think mode
and only reasoned to the correct answer with thinking enabled. Qwen3.8 also invented a
trial name (DESTINY-Breast03 instead of 04) on a HER2-low question. Every trial name and
number must be verified against the retrieval layer.


Budget note: this adds ~100 MB (compressed APK; ~270 MB of native code on disk,
`useLegacyPackaging = true` so the libraries are extractable). No cloud spend.


