# Concordance — Phase A Integrations (Implemented)

This document records the first implemented slice of the integrations plan: a
provider-agnostic adapter layer plus five real, key-free integrations
(Europe PMC, OpenAlex, ClinicalTrials.gov, CIViC, cBioPortal).

> Positioning reminder: Concordance is a **research workflow tool**, not a
> medical device. These integrations retrieve public evidence; they do not
> diagnose, treat, or recommend therapy.

---

## 1. What was built

| Layer | File | Purpose |
|---|---|---|
| Adapter contract | `integration/api/IntegrationModels.kt` | `IntegrationAdapter`, `AdapterDescriptor`, `IntegrationQuery`, `NormalizedRecord`, `Provenance`, `RateLimitSpec`, `AuthType`, `Capability` |
| Registry | `integration/api/IntegrationRegistry.kt` | Registers adapters, fans out literature search, de-dupes by DOI/PMID/title |
| HTTP | `integration/api/HttpJsonClient.kt` | Shared OkHttp JSON client (GET + POST for GraphQL) on `Dispatchers.IO`, provenance hash, success/failure reporting |
| Rate limiting | `integration/api/ProviderRateLimiter.kt` | Per-provider token bucket (suspend-based, never exceeds published limits) |
| Provenance | `integration/api/ProvenanceRecorder.kt` | Newest-first audit log (SHA-256 of response body, UTC time, status, latency) |
| Europe PMC | `integration/api/EuropePmcAdapter.kt` | Keyless literature + abstract search |
| OpenAlex | `integration/api/OpenAlexAdapter.kt` | Keyless works index, inverted-index abstract reconstruction, polite-pool `mailto` |
| ClinicalTrials.gov | `integration/api/ClinicalTrialsGovAdapter.kt` | Keyless v2 study search mapped to `ClinicalTrialStudy` |
| Curated models | `integration/api/CuratedModels.kt` | `CuratedVariantEvidence`, `CohortStudy` + mapping to `NormalizedRecord` |
| CIViC | `integration/api/CivicAdapter.kt` | Keyless GraphQL curated variant evidence for a gene symbol |
| cBioPortal | `integration/api/CBioPortalAdapter.kt` | Keyless REST cohort/study search |

Plus wiring:

- `ClinicalTrialsManager.queryClinicalTrialsGov` now performs a **live** registry
  query, with a bundled-corpus fallback when offline or when nothing matches.
- `SciencePaperManager.searchLivePapers` fans out to Europe PMC + OpenAlex and
  merges results into the in-memory corpus.
- `ConcordanceViewModel` exposes `searchSciencePapers`, `provenanceRecords`, and
  `clearProvenanceLog`.
- `IntegrationsScreen` has live-search buttons and a provenance panel, and the
  duplicated tab index bug is fixed.

---

## 2. Request lifecycle

```
UI action
  -> ViewModel (viewModelScope)
    -> Manager (SciencePaperManager / ClinicalTrialsManager)
      -> IntegrationRegistry
        -> Adapter (Europe PMC / OpenAlex / ClinicalTrials.gov)
          -> ProviderRateLimiter.acquire(providerId, spec)   // suspend
          -> HttpJsonClient.getJson(...)                     // Dispatchers.IO
             -> ApiRotationManager.recordSuccess/Failure
             -> ProvenanceRecorder.record(Provenance)
        -> NormalizedRecord (or ClinicalTrialStudy)
      -> merge into StateFlow corpus
    -> uiNotice snackbar + recomposition
```

Key properties:

- **No API keys required** for this phase; all five providers are `AuthType.NONE`.
- **Offline-safe**: failures return empty lists and the bundled benchmark corpus
  remains visible.
- **Auditable**: each request stores provider, endpoint, query, HTTP status,
  UTC timestamp, SHA-256 of the body, license class, and elapsed time.
- **Rate-limit compliant**: Europe PMC 3 rps, OpenAlex 5 rps with `mailto`,
  ClinicalTrials.gov 5 rps, CIViC 3 rps, cBioPortal 2 rps.

---

## 3. Licensing model

`LicenseClass` lives in `integration/rotation/ApiRotationManager.kt`:

- `OPEN` — usable without restriction (current three providers).
- `ACADEMIC_ONLY` — research use only; UI must gate and require acknowledgment.
- `PAID` — requires a user-provided key/license.

`ApiProvider` now carries `licenseClass` (default `OPEN`). This is the hook for
the next phase (OncoKB, commercial indexes).

---

## 4. How to exercise it

From the **Integrations** screen:

1. **Papers & arXiv** tab
   - Type a query (e.g. `ESR1 brain metastasis plasma discordance`).
   - Tap **Search Live APIs (Europe PMC + OpenAlex)**.
   - Results are merged into the corpus; tap **Analyze Paper** to run the local
     extraction engine and import a claim.
2. **Clinical Trials** tab
   - Type a query (e.g. `brain metastases breast cancer ESR1`).
   - Tap **Query Live ClinicalTrials.gov v2 API**.
   - Results replace the corpus; offline falls back to the bundled studies.
3. **API Rotation** tab
   - Scroll to **LIVE REQUEST PROVENANCE** to inspect recent calls and the
     SHA-256 of each response.
4. **Variant Evidence** tab
   - Enter a gene symbol (e.g. `ESR1`). Tap **CIViC Evidence** to pull curated
     variant-level interpretations (significance, level, therapies, disease).
   - Enter a cohort keyword (e.g. `breast`, `brain metastasis`). Tap
     **cBioPortal Cohorts** to list public cancer-genomics studies with sample
     counts and reference genome.

### Tests

```bash
# In Android Studio, or with a Gradle wrapper if you add one:
./gradlew testDebugUnitTest --tests "com.example.IntegrationAdapterTest"
```

`IntegrationAdapterTest` verifies adapter/provider id alignment, keyless/open
licensing, documentation URLs, and that the token bucket throttles past burst.

> Note: this environment has no JDK or Android SDK, so the project was not
> compiled here. Review the diff in Android Studio before shipping.

---

## 5. Deliberate scope boundaries

Not included in Phase A:

- BYOK key storage / UI (Phase B).
- License-gated providers such as OncoKB.
- Persisting provenance into Room and embedding it in capsules (current log is
  in-memory; capsule embedding is the next provenance step).
- Pagination / cursor handling beyond page 0.
- PubChem, arXiv, Crossref adapters (same pattern, add one file each).
- cBioPortal mutation-level data (`POST /api/mutations/fetch`); the current
  adapter covers study/cohort search only.
- Response caching on disk.

---

## 6. Suggested Phase B

1. **Persist provenance** to Room and include its digest in `CapsuleEngine`
   capsules, so a capsule can prove which source responses it was built from.
2. **BYOK settings** with `EncryptedSharedPreferences`, and a license gate that
   blocks `ACADEMIC_ONLY`/`PAID` adapters until acknowledged.
3. **cBioPortal mutation-level adapter**: POST `/api/mutations/fetch` to pull
   per-sample variant calls for a chosen cohort, so a cohort can seed the paired
   tissue-vs-plasma matrix directly.
4. **Cursor pagination** for OpenAlex and Europe PMC.
5. **Disk cache** (Room or file) with TTL keyed by `(providerId, query, page)`.
6. **Provider health circuit breaker**: after N consecutive failures, mark
   `OFFLINE` for a cool-down window instead of retrying every request.
7. **Auto-route CIViC hits into claims**: convert a curated evidence item into a
   draft `Anchor` with its CIViC URL attached as the source.

---

## Phase B.1 — Provenance persisted to Room (implemented)

This slice makes the audit trail durable and links it to capsules.

### Schema (Room v2, migrated 1→2)
- New table `provenance_records` (`pk`, `providerId`, `endpoint`, `query`,
  `httpStatus`, `retrievedUtc`, `responseSha256`, `licenseClass`, `elapsedMs`,
  `capsuleId`).
- `capsules` gained `provenanceDigest` and `provenanceRecordIds` columns.

### Plumbing
- `ProvenanceDao` (insert, flow, by-capsule, link-to-capsule, delete-all).
- `ConcordanceRepository.saveProvenance(...)`, `getAllProvenanceFlow()`,
  `getProvenanceForCapsule(...)`, and `saveCapsule(capsule, recordIds)` which
  stamps the linked rows with the capsule id.
- `ProvenanceRecorder` now persists via a registered suspend callback, tracks
  the returned row ids in `recordIds`, and exposes them through the ViewModel.
- `CapsuleEngine.buildCapsule(..., provenanceRecordIds)` computes a
  `provenanceDigest` (SHA-256 of the sorted row ids) and writes
  `provenance_digest` + `provenance_record_ids` into the capsule JSON and
  markdown. The canonical SHA-256 over inputs/results/paired is unchanged, so
  existing capsules and `verifyJson` stay valid.
- `CapsulesScreen` shows the provenance digest and linked-response count when
  present.

### How it behaves
1. Every live API request persists a `provenance_records` row (in addition to
   the in-memory log used by the Integrations screen).
2. When a capsule is generated, the rows recorded since the last clear are
   linked to that capsule id, and their digest is embedded in the capsule.
3. `repository.getProvenanceForCapsule(id)` returns exactly the source
   responses a capsule was built from.

## Phase B.2-B.4 + follow-ups (implemented)

- B.2 CIViC mutation detail brought through Phase B.3 BYOK (KeyStore /
  LicenseStore / IntegrationGate) and the OncoKB adapter (ACADEMIC_ONLY,
  token-gated; Settings & Keys tab).
- B.4 completed: append-style pagination ("Load more" for papers and trials),
  Room-backed response cache (migration v3, 10-minute TTL), circuit breaker
  (3 failures -> 60s cooldown -> auto-recovery), and CIViC -> draft-claim import
  (ABSTRACT_ONLY anchors from the Variant Evidence tab).
- Follow-ups: CapsulesScreen provenance viewer (linked source responses) and
  runtime tuning rows in Settings & Keys (circuit-breaker threshold/cooldown,
  cache TTL).

## Audit (2026-10-18)

Issues found and fixed after the audit pass:
1. ClinicalTrials.gov adapter ignored query.page - "Load more" re-fetched
   page 1 every time. Fixed: &page= in the fetch URL.
2. Cache hits skipped ProvenanceRecorder, leaving a gap in the capsule
   provenance digest chain. Fixed: cache hits are now recorded as provenance
   (elapsedMs = 0 marks a cache-served response) and credit the provider.
3. Cache scope hardened: only keyless GET responses are cached, on both read
   and write paths - token-authenticated (OncoKB, academic-only) responses can
   no longer leak into the shared cache, and useless POST writes were removed.
4. ApiRotationManager circuit-breaker maps switched to ConcurrentHashMap
   (multi-coroutine access).
5. CIViC draft-claim dateAdded now uses the real current UTC date.
6. Expired cache rows are pruned opportunistically on put.

Verified green: ssembleDebug + 	estDebugUnitTest (27 tests, 0 failures).
Pagination math is uniform across adapters: IntegrationQuery.page is 0-based,
each live adapter maps it to 1-based API pages (page = query.page + 1).
