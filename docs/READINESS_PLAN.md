# Readiness plan — Concordance ecosystem

> Definition of **total readiness (v1.0.0 GA)**: a clinician or researcher can install
> the Android app, sign in, run an adjudication end-to-end on their own data, publish a
> capsule to the community registry, and the website can serve that registry publicly —
> with PHI never leaving the device, no manual laptop steps, and CI green on all three
> repos. Anything not on that critical path is v1.1+.

## Status (2026-10-20)

- `concordance-oss` (app), `concordance-website`, `oncology-agent` all tagged `v1.0.0`.
- App: **251 tests, 0 failures**, `lintDebug` 0 errors, debug APK installs.
- Website: **331 test files**, production build (SSG/ISR) succeeds.
- Agent: **~464 tests**, untracked remediation docs + compliance/ present.
- **M0 done this session**: reconciled the two app working trees (research UI with
  `use_llm` toggle, gate/critic/paper card, `AgentDecision` model, em-dash fix) into
  `concordance-oss`; build + 251 tests green.

## User-side blockers (unblock first — nothing ships without these)

1. **Apply `supabase/migrations/001_init.sql`** in the Supabase SQL editor (13 tables, 37 RLS
   policies). Without it, sync silently falls back to local JSON.
2. **Rotate the service-role key** (it was exposed in chat).
3. **Replace the quick tunnel with a named tunnel** (`cloudflared tunnel login` + DNS route) so
   the agent URL stops changing on every restart, then re-import `concordance_config.json`.
4. Unlock the test phone so on-device verification can run.

## Milestones

### M1 — Agent structural remediation (blocks everything else; ~3-4 days)

Source: `docs/WEAKNESSES.md` items 1, 2, 4, 10.

- Split `app/main.py` (1,294 lines) into `app/routers/` (`research`, `evolution`, `autonomy`,
  `admin`, `imaging`, `knowledge`, `paper`, `tools`, `skills`, `dream`). Each registers its
  own `APIRouter`; `main.py` only includes them.
- Convert the 18 import-time env captures to runtime resolution (follow `autonomy._telemetry_path()`
  and `evolution._journal_path()`). Fixes test isolation and stale paths.
- Pydantic request/response models for every endpoint with `max_length` constraints; no more
  `dict[str, Any]` bodies.
- `ruff` + `mypy --strict` on `app/`, wired into CI/pre-commit.

**Acceptance:** `main.py` < 200 lines; `mypy` clean; no env captured at import.

### M2 — Sync layer: agent ↔ Supabase ↔ app (~5-7 days)

Source: `docs/SUPABASE_PLAN.md` phases 2-5.

- Dual-write store adapters for all 13 JSON stores (`knowledge`, `experiments`, `insights`,
  `discoveries`, `evolution_journal`, `tools`, `skills`, `hunt_results`, `experiment_tree_nodes`,
  `benchmarks`, `telemetry`, `srt_hypotheses`, `srt_runs`).
- `app/store_factory.py` selects Supabase when `SUPABASE_ENABLED=1`, else local fallback.
- Factory adopted in `dream.py`, `main.py`, and every store call site.
- `scripts/migrate_to_supabase.py` (idempotent upsert, dry-run mode).
- Conftest: `SUPABASE_ENABLED=0` for all unit tests; mock-based Supabase client tests.
- Android: wire sign-in, capsule sync to S3/R2, conflict surfacing (Phase 3 of ECOSYSTEM_PLAN).

**Acceptance:** a capsule created on the phone appears in the website registry after sync;
`SUPABASE_ENABLED=0` keeps every existing test green.

### M3 — Auth & web skeleton (~3-5 days)

Source: `ECOSYSTEM_PLAN.md` phases 1-2.

- Cognito (or Auth.js) sign-in on the website; profile page.
- Public registry browse (read-only from static seed) + authenticated publish flow.
- Backend: teams, memberships, claims/capsules CRUD, publish, verification endpoint.
- App: login screen wired to the same provider; token storage in EncryptedSharedPreferences.

**Acceptance:** a new user can sign up on the web, publish a capsule, and see it in the
registry without a laptop.

### M4 — Remaining integrations (~4-6 days)

From the outstanding list + WEAKNESSES.

- REDCap live data pull + Benchling live sync (currently stubs).
- Stripe finalization for the hosted paid tier.
- Slack/Discord + Zapier webhooks for community events.
- Remaining citizen-science adapters (SciStarter/iNaturalist/CitSci already wired).
- ORCID/Zenodo/Zooniverse already shipped (top-3) — verify end-to-end publish.

**Acceptance:** each integration has a TestClient happy path + error path; no PHI crosses
a managed boundary.

### M5 — Website hardening (~3-4 days)

- WCAG 2.2 AA audit (contrast, focus, 48dp targets, screen-reader labels) — reuse the app's
  `UiTokenGuard` pattern.
- Load tests (k6 or locust) against the registry + agent; document SLOs.
- Staging/preview deploys (Vercel preview + agent on a named host).
- Tailwind color-token refactor so the light theme is a single palette swap (no literal colors).
- Gamification / project-builder (badges, leaderboards) — v1.1 candidate, not GA.

**Acceptance:** Lighthouse accessibility ≥ 95; load test report committed; preview URL live.

### M6 — Agent reliability (~2-3 days)

Source: `WEAKNESSES.md` items 5, 6, 9, 12, 13.

- Integration tests via FastAPI `TestClient` for every router (happy + 404/422/500).
- `tenacity` retry + circuit breaker for Jev/OpenAI/Kaggle; cache availability probes.
- Structured JSON logging (structlog) with request IDs; wire `app/observability.py`.
- SQLite migration for structured stores (knowledge, experiments, insights, discoveries) with
  schema versioning; keep JSON for journals.
- Parallelize tests with `pytest-xdist`; separate slow (PaddleOCR) tests via markers.
  Target: fast suite < 5 min (currently 27 min).
- Backup/restore endpoints (`/api/admin/backup`, `/api/admin/restore`) + scheduled backup via
  dream mode.

**Acceptance:** fast suite < 5 min; integration test file exists per router; structured logs
in CI output.

### M7 — Release (~1-2 days)

- Release APK: verify R8/minification, no cleartext in release, signing config, `versionCode` bump.
- Named tunnel + stable URL; update `concordance_config.json` import flow.
- Final Supabase RLS verification; service-role key rotation confirmed.
- Tag `v1.1.0` on all three repos; release notes.

## Out of scope for v1.0 (explicitly deferred)

- On-chain reputation token, federation, sponsored IP, cosmetics (ECOSYSTEM_PLAN items 11-15).
- Gamification/project-builder (M5 — v1.1).
- Full SQLite migration of the clinical store (`store.py` stays local-only, PHI-safe).
- HyperFrames marketing media (lowest rank).

## Risks

| Risk | Mitigation |
|---|---|
| `main.py` split breaks route registration order | Keep a single `include_router` list; add a smoke test that hits every endpoint |
| Supabase dual-write drift | Idempotent upserts + dry-run migration; `SUPABASE_ENABLED=0` fallback |
| 27-min test suite blocks CI | M6 parallelization + markers before M2 lands |
| Research UI (`use_llm`) assumes agent supports it | Already supported (`research_endpoint` reads `req.use_llm`) — verified |
| Two working trees recur | Canonical repo is `concordance-oss`; delete the stale `Downloads/eco/Concordance-main` copy after M0 verification |

## Suggested sequencing

Week 1: M1 (agent structure) + M0 verification (build green on oss — done).
Week 2: M2 (sync layer) + M6 fast-suite parallelization (unblocks CI).
Week 3: M3 (auth + web skeleton) + M4 integrations.
Week 4: M5 (website hardening) + M7 (release).

Total estimate: **~18-25 engineer-days** for v1.0.0 GA, assuming the user-side blockers
(SQL, tunnel, key rotation, device unlock) are cleared in parallel.
