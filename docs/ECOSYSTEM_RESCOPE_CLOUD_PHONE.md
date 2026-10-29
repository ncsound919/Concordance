# Ecosystem Audit and Rescope: Cloud + Phone Only

**Date:** 2026-10-28
**Method:** read the deployed Workers, their D1 databases, R2 buckets, GraphQL usage, and the
live `/health` endpoints. Numbers are from the account (`a7ce29a9...`), not from the plan docs.

**Rescope decision:** the system is the **Android app + Cloudflare + Kaggle**. Everything else
(the Next.js web app, the separate `oncology-agent` HTTP service, the Supabase/AWS/Modal/Gitea
fleet registry) leaves the critical path. This document records what is actually there, what it
has produced, what is worth keeping, and what the system becomes.

---

## 1. What is actually deployed

28 Workers, 5 D1 databases, 7 R2 buckets, 9 container-backed Workers.

| Worker | What it actually does (verified in the bundle) | Verdict |
|---|---|---|
| `concordance-edge` | Job lifecycle in D1 (`jobs`, `events`, `evidence`, `daily`); `/publish`, `/state`; forwards to the adjudication service; writes `capsule.json` to R2 | **keep** - this is the backbone |
| `concordance-ai` | OpenAI-compatible shim over Workers AI: `/v1/chat/completions`, `/v1/audio/{speech,transcriptions}`, `/v1/images/generations`, `/v1/models`, `/v1/usage`, `/v1/debug/raw`. Bearer-auth (401 without key) | **keep**, drop `/v1/debug/raw` |
| `mobile-gateway` | The phone's real entry point: `/v1/llm`, `/v1/chat/completions`, `/v1/route`, `/v1/kaggle/jobs`, `/v1/sieve/*`, `/v1/recourse/*`, `/dream/*`, `/learn/*`, `/self-improvement/*`. Reports `llmLanes: [deepseek, openrouter]`, `kaggle: false` | **keep**, becomes the single phone API |
| `sieve` / `sieve-ingest` | Telemetry ingest (`overlay.ecosystem.event.v1`), source allowlist includes `concordance-android`; 3,842 + 257 requests/30d | **keep** (optional), it is the only live telemetry |
| `ecosystem-control` | Real fleet control plane: `services` registry, cron engine (D1-backed, 1-minute tick), job dispatch with verb allowlists, health probes, run history, MCP endpoint, AWS SigV4 helper, model-file proxy | **park** - correct but pointed at a fleet that does not exist |
| `truth-chain` | Hash-chained ledger, `/verify`, 3 events total | **fold into** the capsule ledger |
| `oncology-scheduler` | Cron-driven, `scheduler-ledger.outcomes`, calls Modal | **cut** |
| `concordance-web` | Next.js/OpenNext bundle, **38,879 KB**, 144 requests in 30 days | **cut** |
| `dev-brain`, `py-ai`, `vec-memory`, `umoe`, `chemlab`, `cureforge`, `oncoforesight`, `biosim-sidecar`, `kg-sidecar`, `browser-run`, `mathx-api`, `math-x-web`, `ncsound-*`, `marketing-signal-gateway`, `news-worker`, `gumroad-landing-page`, `tap45`, `bold-dream-5907` | Unrelated projects sharing the account | **out of scope** |

`ecosystem-control` is the only component with real breadth: 17 registered services, 8 job
definitions (`poll-health`, `fleet-watch`, `onco-probe`, `onco-matrix`, `index-refresh`,
`daily-digest`, `tick-smoke`, `audit-deep`), 416 events, 2,806 metrics, 466 commands.

It is also monitoring nothing:

```
poll-health -> "ok: probed 17, online 1, changed 0"
services    -> 1 online (sieve-container), 1 degraded (gitea), 15 unknown
```

Fifteen of seventeen are `unknown` because its own `adapters.ts` refuses to probe
`*.workers.dev` from a Worker ("not probeable from a Worker (workers.dev subrequests 404); give
it a custom domain to monitor it"). The registry describes a fleet, but the fleet has no
addresses.

---

## 2. What it has actually produced

### The one real result

On **2026-10-20** the edge pipeline adjudicated `giannoudis2024_pooled` end to end and minted
three capsules. From D1 and R2:

```json
verdict:            "supported"
reason:             "corrected CI entirely below 1.0 even without detection correction"
observed_or:        0.2632
corrected_or:       0.2575
corrected_ci:       [0.1940, 0.3418]
fisher_p:           7.0e-29
r_null:             0.3107
min_feasible_r:     0.0716
significance_r:     0.4007
evidence_tier:      "measured"
capsule_hash:       5e8d66a42322fc40
provenance:         python 3.12.10, scipy 1.18.1, numpy 2.3.5, code sha256:952744d3
```

That is a genuine detection-corrected result with full provenance, a real hash, and a real
timestamp. It is the most valuable artifact in the account.

But it is also **the only one**:

| | Count |
|---|---|
| Edge jobs total | 5 |
| Of those, completed with a real capsule | 3 |
| Distinct anchors adjudicated | **1** (`giannoudis2024_pooled`) |
| Experiments ever executed | **0** |
| Papers produced | **0** (`oncology-papers` holds one 22-byte `probe/hello.md`) |
| Published capsules | **0** |
| `daily` rollup rows | 0 |
| Truth-chain events | 3 |
| R2 `concordance-capsules` objects | 5 (3 capsules + 1 probe + 1 hello) |

The three jobs that did not complete are test debris: `edge-test-1` is still `queued`,
`ws-test-2111834374` is still `running`, both from 2026-10-20. Nothing has run since.

### Why it stopped

- 8 `queue_error` events against 4 `queued` and 2 `accepted`. The queue path is the weak link.
- No scheduler drives new work. `oncology-scheduler` fires against Modal, not against the edge.
- **`kaggle.ts` has no submit.** It implements `kernels/list` and `kernels/status` only - 39
  lines, no `POST /kernels/push`. Kaggle is an *observation target*, never a driver.
- The app's own Kaggle path returns `FAILED: this build does not implement Kaggle job submission`.

So both halves of the system can see Kaggle and neither can use it. That is the single gap
between "a working adjudicator" and "an experiment runtime".

### Usage reality (30 days)

| | Used | Included | Share |
|---|---|---|---|
| Worker requests | 18,289 | 10M | 0.18% |
| Workers AI neurons | 6,280 | 10,000/day | 2.1% |
| D1 rows read | 1,133,627 | 25B | 0.005% |
| D1 rows written | 46,408 | 50M | 0.09% |
| R2 storage | 55.5 GB | 10 GB | 556% (+$0.68/mo) |

The account is idle. Nothing here is constrained by Cloudflare; it is constrained by the fact
that no experiment can be submitted.

---

## 3. Value: keep, park, cut

**Keep (the load-bearing 4):**

1. `concordance-edge` - job lifecycle, capsule storage, edge API. It has already produced a
   correct capsule; that code path is the product.
2. `mobile-gateway` - the phone's actual API surface, with LLM lanes already failover-wired.
3. `concordance-ai` - Workers AI behind an OpenAI-shaped contract, auth-gated. Replaces any
   need for a cloud LLM vendor.
4. `sieve-ingest` - cheap, already receiving from `concordance-android`, and the only way to
   see what the phone does in the field.

**Park (do not delete, do not invest):** `ecosystem-control` (genuinely well-built; wrong
fleet), `truth-chain` (fold the ledger into capsules, keep the code as reference),
`oncology-scheduler`, the container sidecars.

**Cut from the critical path:** `concordance-web` (38.9 MB, 144 requests, no users),
Supabase, the AWS/Modal/Gitea registry, and the separate `oncology-agent` service.

The last one is the important call. Right now every edge job forwards to a Python service
running on a laptop. That service is where the numbers are computed. Removing it means the
numbers must be computed somewhere else - and both "somewhere else"s already exist and are
better:

| Work | New owner | Why |
|---|---|---|
| Claim adjudication / correction | **The phone** (Kotlin engine, already tested) | offline, no PHI egress, no service to keep alive |
| Experiment execution | **Kaggle kernel** (the `concordance` Python package) | free compute, pinned image, real artifacts |
| Routing, state, artifacts | **Cloudflare** | $5, already deployed |

That is a strictly simpler system with fewer trust boundaries. It is also the only version that
survives the laptop being closed.

---

## 4. The rescoped system

```
   ┌──────────────────────────────┐
   │  Android app (the instrument)│
   │  - claims, capsules, journal │
   │  - Kotlin statistical engine │
   │  - experiment specs + UI     │
   │  - on-device STT/TTS, corpus │
   └───────┬──────────────────────┘
           │  HTTPS (Bearer)          offline-first: Room is the source of truth
           ▼
   ┌──────────────────────────────┐
   │  Cloudflare                  │
   │  mobile-gateway  ─ routes    │
   │  concordance-edge ─ runs     │
   │  concordance-ai  ─ LLM/OCR   │
   │  D1: runs, events, ledger    │
   │  R2: artifacts, capsules     │
   │  Queues: fan-out, DLQ        │
   └───────┬──────────────────────┘
           │  Kaggle API (username + key)
           ▼
   ┌──────────────────────────────┐
   │  Kaggle kernels (compute)    │
   │  pinned image + concordance  │
   │  writes results + hashes     │
   └──────────────────────────────┘
```

Ownership rules:

1. **The phone is the only client.** No web UI, no second login, no browser state.
2. **The phone computes adjudication.** A capsule is minted on-device and only *stored*
   in R2. Cloudflare never computes a verdict.
3. **Kaggle computes experiments.** Never the phone, never a Worker.
4. **Cloudflare owns dispatch and state, not science.** It may refuse to submit; it may not
   invent a result.
5. **One ledger.** D1 `runs` + R2 `capsule.json` + the phone's Room tables describe the same
   objects with the same hashes.

### What this deletes from the current build

- The `AgentExecutor` concept and every `/api/adjudicate` call from the edge.
- The Next.js worker and its 38.9 MB bundle.
- The fleet registry, cron engine and health probes (park, not delete).
- The app's dependency on a reachable laptop for anything except optional sync.

### What this adds

- `POST /kernels/push` in `kaggle.ts` - the missing 60 lines that make Kaggle usable.
- A run state machine in `concordance-edge` (D1) with
  `queued -> running -> completed|failed|refused`, driven by Cloudflare Workflows.
- `GET /kernels/quota` in the same file - used/remaining weekly GPU hours, so the budget
  governor cannot over-submit. The **2-concurrent-GPU-session** cap must be handled
  explicitly: Kaggle's CLI exits 0 when the third push is rejected.
- Output retrieval: `GET /kernels/output?userName=...&kernelName=...` -> zip -> R2.
- A per-run token so a kernel can report progress without holding R2 credentials.
- `ExperimentSpec` on the phone, compiled to a kernel parameter block.
- Chaquopy, so the phone runs the same Python statistics as the kernel (see
  `OSS_2026_RESCAN.md` §1.1).

---

## 5. Rescoped phase plan

Replaces the agent-dependent phases in `EXPERIMENT_RUNTIME_PLAN.md`.

> **Implementation status (2026-10-28).** The pure layer is built and compiling:
> `ExperimentSpec` (IR + canonical hash + validation), `ExperimentCompiler` (fail-closed
> `RunPlan`), `RunRecord` (append-only state machine with a hash chain), `Adjudicator`
> (artifacts → `ClaimUpdate` or refusal), and `ExperimentCatalog` (truthful statuses,
> bundled `assets/experiment-specs.json`). The Research screen now renders the catalog
> instead of a hardcoded list. 54 of 56 new tests pass on first run; the two failures were
> a modelling bug in dataset pinning and are fixed. Not yet built: Kaggle submission, the
> edge routes in `EDGE_RUN_API.md`, Room persistence, and the Chaquopy lane.

**P0 - Honesty and cleanup (2 days)**
Delete `concordance-web`; remove `/v1/debug/raw`; stop the edge from calling a laptop; mark
every app experiment `READY` / `DESIGN_ONLY` / `UNSUPPORTED` with a reason. Clear the four
stale test jobs.

**P1 - Kaggle submission (3-4 days)** - *the whole rescope hinges on this*
`kaggle.ts`: `push` (base64 kernel source + `enable_gpu`), `status`, `output`, `quota`.
Edge run table, per-run token, timeout, cancel. Handle the **2 concurrent GPU sessions** cap:
Kaggle rejects a third push and the CLI still exits 0. First target: a hello-world kernel that
returns a hash, then the real replication kernel. Acceptance: a run triggered from the phone
completes on Kaggle and its artifact lands in R2 with a matching hash, and the UI shows
remaining weekly GPU hours.

**P2 - ExperimentSpec on the phone (1 week)**
Typed spec + hash + preregistration lock + `RunPlan` compile. One screen: spec, status, cost,
run. Acceptance: a spec that cannot be compiled says why, and nothing is submitted.

**P3 - Adjudication on-device (1 week)**
Mint the capsule with the Kotlin engine **and** with `concordance.correction` running under
Chaquopy 17.1 (MIT, CPython 3.10-3.15, SciPy/NumPy/Pandas supported). The two must agree
bit-for-bit on the reference fixture. The edge only stores the capsule. Acceptance:
adjudicate in airplane mode, then sync; hash is identical to capsule `5e8d66a42322fc40`
computed by the old Python service.

**P4 - The first real experiment (2 weeks)**
ESR1 CNS discordance replication on public data via Kaggle. Acceptance: verdict, artifact hashes,
and a re-run that reproduces them.

**P5 - Autonomy inside the $5 (1 week)**
WorkManager wakes the phone; the phone asks the edge for the next allowed run; budget caps in
D1 (`daily`); approval queue for anything over cap. No new Cloudflare primitives. Workers AI
stays under 9,500 neurons/day.

**P6 - Telemetry and the ledger (3 days)**
Route `sieve-ingest` events into the same run ids; export the run ledger from D1; fold
`truth-chain` verification into capsule verification.

Roughly 6 weeks, with no component outside the phone, the $5 account, and free Kaggle.

---

## 6. Risks introduced by the rescope

| Risk | Mitigation |
|---|---|
| Phone becomes a single point of failure for adjudication | the engine is deterministic and tested; capsules are reproducible from the replication package |
| Kaggle credentials on the edge | store as a Worker secret, never in D1; per-run tokens for kernels; the code already never logs the key |
| Kaggle quota exhaustion mid-run | surface remaining quota; queue and retry, never silently drop |
| Losing the fleet monitor means losing visibility | `sieve-ingest` + `concordance-edge` events are enough for one user; add probes only when there are real custom domains |
| 38.9 MB web worker becomes a forgotten deploy | delete it; R2 `concordance-web-opennext-cache` is empty and can go too |
| Removing the agent removes the Python runtime that produced the 2026-10-20 capsule | keep that capsule and its provenance as the reference fixture; the Kotlin engine must reproduce it exactly |

That last row is the acceptance test for P3. If the phone cannot reproduce
`5e8d66a42322fc40` from the same inputs, the rescope is not done.

---

## 7. Decisions

1. **Confirm the interpretation:** "cloud + phone only" = Android app + Cloudflare + Kaggle,
   with the web app and the laptop agent removed from the critical path. If you instead meant
   something narrower (for example, phone-only with the cloud as pure backup), say so before
   P0 - the phase plan changes at P2.
2. **`concordance-web`:** delete the Worker (it is reproducible from its repo) or leave it
   parked? Deleting frees nothing measurable except confusion, but it removes a 38.9 MB
   artifact nobody is reading.
3. **`ecosystem-models` (51.7 GiB):** still needed? It is the only line item above the $5.
