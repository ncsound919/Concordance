# Coverage and Defect Audit — 2026-10-22

**Scope:** the three Concordance repos and the Referee.

| Repo | Before | After |
|---|---|---|
| `Referee-main` (Python) | 48 tests, **83%** coverage | **272 tests, 100% statements and branches** |
| `Referee-main` (TypeScript) | 89 tests | **233 tests** |
| `concordance-oss` (Android) | 292 tests, **31.6%** lines / 28.7% branches | **736 tests, 57.1% lines / 39.6% branches** |
| `concordance-website` (Next.js) | 198 tests, **16.6%** lines | **305 tests, 20.0% lines** |
| `oncology-agent` (Python) | 671 tests, 1910 statements missed | **716 tests, 1768 statements missed, 402 branches missed** |

Every repo also gained a working coverage measurement and a CI gate that
reports it. All four suites are green.

> **Note on attribution.** A second agent was working in these same four
> repositories concurrently with this work and committed some of it. The
> per-repo test counts above include that agent's tests. The changes listed
> under *Defects found and fixed* and the new test files named below are the
> ones made in this pass.

---

## 1. Defects found and fixed

Four real defects surfaced. Three of them were found by the new tests failing
on a first run, which is the point of writing them.

### 1.1 `StatisticalEngine.normPpf` was wrong in both tails (Android app)

**Severity: high.** The app computes the sample size a study needs.

`normPpf` is a probit function built from Peter Acklam's coefficients. Two
faults in the tail branch:

1. `sqrt(-ln r)` where Acklam's coefficients need `sqrt(-2 ln r)`;
2. the tail sign was inverted — the code negated the lower tail and kept the
   upper tail, where Acklam does the opposite.

The function was also discontinuous at `|p - 0.5| = 0.42`, the boundary
between its central and tail branches. `normPpf(0.975)` returned **-1.0022**
where the true value is **+1.959964**.

`requiredSample` computes `z = normPpf(1 - alpha/2) + normPpf(power)`. With
the broken probit that gave `z = -0.16` instead of `+2.80`, so `z²` was 0.026
instead of 7.85. **The required sample size was understated by a factor of
about 300.** The app would tell a researcher they need ten true positives per
site when they need thousands.

Fixed in
`app/src/main/java/com/example/concordance/engine/StatisticalEngine.kt`.
The maximum error against a reference implementation over the supported range
is now 5.3e-07. `StatisticalEngineTest` carries the known-answer vectors and a
continuity check so it cannot regress.

### 1.2 The sign-in page was an open redirect (website)

`GET /login?next=...` was checked with
`next.startsWith("/") && !next.startsWith("//")`. The WHATWG URL parser
treats `"/\"` as the start of an authority, so `?next=/\evil.example` passed
that check and resolved to `https://evil.example/`. A visitor who followed a
crafted sign-in link would be sent to an attacker's site after authenticating.

Fixed in `app/[locale]/login/page.tsx`: the redirect target is now resolved
with the URL parser and rejected if it does not land on the current origin.
`app/[locale]/login/page.test.tsx` covers the relative path, the
protocol-relative path, the absolute URL, and the backslash form.

### 1.3 The ORCID endpoints refused every real iD (agent)

`app/orcid.py` validated an ORCID iD with `"/" not in oid`. A canonical ORCID
iD is `0000-0002-1825-0097` — sixteen digits and dashes, **no slash**. Every
valid iD was therefore rejected with "invalid ORCID iD", so
`GET /api/integrations/orcid/{orcid_id}/person` could never succeed. The
website calls the same record with a bare iD.

Fixed with a proper iD pattern and a `normalize_orcid_id` helper that accepts
either a bare iD or a full `https://orcid.org/...` URL.

### 1.4 The pending-decision queue never deduplicated (agent)

`app/pending_decisions.py` builds a dedup key of `run_id:candidate`, but
seeded its "already seen" set from the stored entries' **ids** rather than
from their dedup keys. The comparison therefore never matched, and the set
only worked within a single call. A retried run enqueued the same decision
again, and a human saw it twice in the queue.

Fixed by building the existing-key set the same way the key is built.

### 1.5 The public evidence-levels catalog was unreachable (Android)

Found while writing the adapter tests. `OncoKBAdapter.fetchLevels` is documented as "always available once the license is acknowledged, regardless of token", but it called the shared `IntegrationGate`, which demands a key for a `TOKEN` provider. The free public catalog could therefore never be fetched. The key check is now made by the adapter, which tolerates a `NeedsKey` for that one public endpoint and still refuses an unacknowledged license.

---

## 2. Referee

### 2.1 Python core — 48 to 272 tests, 83% to 100% coverage

New files under `tests/`:

| File | What it pins |
|---|---|
| `test_phase1_hardening.py` | The hardening items the audit plans called for and that had no test: patient-ID normalisation, NaN label and split rejection, the `max(a, 1-a)` inversion guard, the chance-model guard, `claimed_auc_ci` overlap, the negative control, canonical-JSON float rounding, the Ed25519 signature, `verify_report` re-auditing the table, and the completeness of the `Policy` defaults that get hashed into every report. |
| `test_baselines_contract.py` | The baseline comparison, which was at 27% and had never run. Includes `FAILS_BASELINE` — a verdict the core could produce but no test could reach. |
| `test_engine_contract.py` | The engine registry, the provenance digests, artifact verification, and the refusal to fabricate a simulation. |
| `test_metrics_contract.py` | Every metric refusal, and that the bootstrap is deterministic. |
| `test_leakage_contract.py` | Patient overlap, the nuisance rules, and the advisory patterns. |
| `test_api_contract.py` | Fail-closed without a key, all three accepted credential headers, the rate limiter, `/audit_batch`, `/verify` with a table, and the pre-check licence block. |
| `test_faults_contract.py` | The seeded-fault suite, the two sensitivity sweeps, and the CLI. |

Module coverage went from 83% to **100% of statements and 100% of branches**
(873 statements, 234 branches, none missed). `baselines.py` went from 27% to
100%, and `audit.py`, `api.py`, `metrics.py`, `leakage.py`, `schema.py`,
`faults.py`, `cli.py` and every module under `engine/` to 100% as well.

That is worth being precise about: the 83% baseline was measured with
statement coverage only. The 100% figure is statement *and* branch coverage,
so it is a stricter measure than the one it is being compared against. The
statements that were uncovered are the ones that were listed as missing in
the original report — the non-converging Brentq, a signature that cannot be
produced, a version lookup that cannot fail — and they are now reached by
tests that provoke them.

### 2.2 The web layer — 89 to 233 tests

New files: `tests/api-routes-uncovered.test.ts` (the liveness probe, the
operator session exchange, and the `/api/audit` fail-closed paths),
`lib/crypto-utils.test.ts` (published SHA-256 vectors, because the previous
hand-rolled implementation was mathematically wrong), `lib/skills-library.test.ts`,
`lib/paper-ingestion.test.ts`, `lib/ledger-store.test.ts`,
`lib/hypothesis-skeptic.test.ts` and `lib/integrations/referee-core-client.test.ts`.

The `/api/audit` route is the one that matters: it must never answer with a
simulated verdict. The new tests assert it refuses without a table (409),
without an engine (503), and with an engine that is down (502).

### 2.3 Tooling

`pyproject.toml` gained `[tool.coverage.run]` / `[tool.coverage.report]`,
`pytest-cov` was added to the dev extra, and the CI workflow now reports
coverage and uploads the XML.

---

## 3. Android app (`concordance-oss`)

### 3.1 Coverage tooling

There was no coverage measurement at all. The build now applies the JaCoCo
core plugin to every `Test` task and registers a `jacocoTestReport` task:

A shared `MockHttpHarness` stands up a `MockWebServer` and rewrites the
scheme and host of every request to it, keeping the path and query intact.
The rewrite interceptor is appended to the production client, so the research
User-Agent and `Accept` headers are still applied and can be asserted. The
same harness drives the PostgREST and GoTrue clients, where the base URL is
a compile-time constant.

```bash
./gradlew :app:jacocoTestReport
```

The class directories are globbed rather than hard-coded, because the AGP
output layout changed between 8 and 9 (`tmp/kotlin-classes` became
`intermediates/built_in_kotlinc`). CI runs the report and uploads it.

### 3.2 New tests

| File | What it pins |
|---|---|
| `ResearchGraderTest.kt` | The ten grading dimensions, their clamps, and the composite arithmetic. The `research` package went from 0% to 100%. |
| `InsightsAnalyzerTest.kt` | Breakthrough, gap, trend, contradiction and optimisation signals, and the empty case. |
| `ExperimentPlannerTest.kt` | One proposal per insight kind, the refinement cap, and the priority-weighted ordering. |
| `DeepResearchSystemTest.kt` | The round as a whole, including the empty round. |
| `JobQueueTest.kt` | The job id is a **content hash**: the same inputs always give the same id, and any change to the data class, routing policy, model revision, parameters or inputs gives a different one. Plus the stuck-task recovery. The `integration/queue` package went from 0% to 78%. |
| `StatisticalEngineTest.kt` | The primitives, the feasibility rules, and all five verdicts. This is where the probit bug was found. |
| `LearningContractTest.kt` | The bounded parameter grid (121 candidates, `z95` never searched), the revision lifecycle, and the evaluator. |
| `TelemetryManagerTest.kt` | The bounded ring, the aggregates, the latency percentiles, and the suggestions. `telemetry` went from 64% to 89%. |
| `ResponseCacheTest.kt` | The TTL behaviour against a fake DAO, including that an expired row is deleted rather than served. |
| `RuntimeTuningStoreTest.kt` | Every clamp on load. |
| `SyncConflictBusTest` (in `IntegrationSupportTest.kt`) | The conflict queue and its resolutions. |
| `ZoteroSettingsTest.kt` | The id normalisation and the absent cases. |
| `ConflictResolutionScreenTest.kt` | The four resolutions the conflict screen offers, and that each reports the conflict it belongs to. |
| `HttpJsonClientTest.kt` | The shared JSON client: the happy path, the research headers, the bearer token, POST and PUT, every error class, the provenance log and its digest, the rotation pool, and the cache rules (keyless GETs are cached; token-authenticated and extra-header GETs and all POSTs are not). |
| `AdapterHttpTest.kt` | Four adapters that had no test at all (ClinicalTrials.gov, Europe PMC, OpenAlex, OncoKB), driven against a local server, so the path each adapter asks for is pinned too. |
| `SupabaseSyncTest.kt` | The PostgREST client, the GoTrue auth client, and the sync manager: pulling, conflicts, the four resolutions, pushing, and publishing. The base URL is a compile-time constant, so the harness rewrites the host and keeps the path. |

### 3.3 What is still uncovered

The Compose screens. `ui.screens` and `ui.components` are the largest
remaining gap: eight of the nine screens take the `ConcordanceViewModel`,
which is an `AndroidViewModel` over a Room database, so rendering them needs
a real in-memory database and a set of fakes. The two screens that do not
need it — `ConflictResolutionScreen` and `AuthScreen` — are the ones covered
here.

---

## 4. Website (`concordance-website`)

### 4.1 Tooling

The vitest config only collected `lib/**`, so nothing under `app/` or
`components/` was ever executed. It now collects all three and measures
coverage with the v8 provider:

```bash
npm run test:coverage
```

This needed three additions: `@vitest/coverage-v8`, `jsdom`, and
`@testing-library/react`. The project's tsconfig sets `"jsx": "preserve"`,
which is right for Next.js but means the test runner has to be told to
transform JSX; vitest 5 runs on rolldown-vite, where that is the `oxc`
option rather than the old `esbuild` one. The component tests opt into jsdom
with a per-file `@vitest-environment jsdom` docblock, so the library and
route tests do not pay for a DOM.

The thresholds in `vitest.config.mts` are a **floor**, not a target, and they
say so. They are set at the current level so that a change which quietly
deletes a test fails the build.

### 4.2 New tests

- `app/api/route-guards.test.ts` — every protected API route must refuse an
  unauthenticated request, and must do so **before** it touches the database.
  The Supabase client is replaced with a stub whose `from()` throws, so a
  route that queries first fails loudly rather than passing by accident.
- `app/api/v1/referee/audit/route.test.ts` — the referee audit route: the
  body cap, the JSON guard, the unconfigured case, and that the route
  forwards the claim and the table rather than inventing a verdict.
- `app/metadata-routes.test.ts` — `robots.txt` and `sitemap.xml`.
- `app/[locale]/login/page.test.tsx` — the sign-in form, including the open
  redirect.
- `components/referee-audit-widget.test.tsx` — the widget that runs an audit
  from the browser.
- `components/ui/verdict-pill.test.tsx`, `evidence-tier-legend.test.tsx`,
  `ci-bar.test.tsx`, `primitives.test.tsx` — the presentational components.

### 4.3 What is still uncovered

The `app/[locale]` pages and the API routes, which need a Supabase test
database. Rendering them needs `createServerSupabase()` to return a client
backed by a real (or faithfully faked) database, and the pages are React
Server Components that read at render time. Until there is a test database,
the honest thing is to say so rather than to mock the database away.

---

## 5. Agent (`oncology-agent`)

### 5.1 Tooling

`pyproject.toml` gained a `[tool.coverage.run]` / `[tool.coverage.report]`
section with branch coverage on, and the CI workflow now reports coverage and
uploads the XML.

### 5.2 New tests

`tests/test_boundary_contracts.py` (45 tests) covers the modules that sit on
a boundary and had no direct test: the pending-decision queue, ORCID, gnomAD,
Zenodo, provenance, and DynamoDB. The pattern throughout is that a boundary
must **refuse clearly** when the thing is missing and must never invent a
result.

### 5.3 What is still uncovered

`app/main.py` is 67% and accounts for 486 of the uncovered statements: it is
one very large FastAPI module. `app/supabase_store.py` is at 0% because it
needs a database. The rest of the gaps are distributed.

---

## 6. Recommendations

1. **Re-record the Android screenshot goldens** if the theme changes again;
   `ScreenshotGalleryTest` compares against images, so a colour change fails
   the build until they are re-recorded.
2. **The agent's CI swallows integration-test failures** with `|| true` on the
   `pytest -m integration` step. The Referee had the same pattern and it was
   removed there; the agent's step should be treated the same way.
3. **Give the website a Supabase test database.** It is the only way to cover
   the 40 pages and 95 routes that are currently unreachable, and it is the
   single biggest remaining opportunity in the estate.
4. **Consider splitting `app/main.py`** in the agent. 1488 statements in one
   module is why it is the least-covered file.
5. **Ratchet the thresholds as the suites grow.** They are set at the current
   level; each new test batch should raise them.
