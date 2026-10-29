# Experiment Runtime — Plan for the Major Capability Upgrade

**Status:** proposal. No code changes yet.
**Date:** 2026-10-28
**Supersedes nothing.** Companion to `CONCORDANCE_OPERATIVE_OUTLINE.md` (the autonomous
loop design), `CONCORDANCE_BENCHMARK_PLAN.md` (the gold set and bounties),
`CONCORDANCE_AUTONOMY_PLAN.md` (scheduling mechanics), and `ECOSYSTEM_PLAN.md`
(the community layer).

> **Rescoped 2026-10-28 - cloud + phone only.** See
> `ECOSYSTEM_RESCOPE_CLOUD_PHONE.md`. The separate `oncology-agent` HTTP service and the
> Next.js web app leave the critical path. Adjudication runs on the phone; experiments run
> on Kaggle; Cloudflare only dispatches and stores. Every `AgentExecutor` reference below
> now means `CloudExecutor` (the `concordance-edge` Worker), and the endpoints in section 6
> are Cloudflare routes, not Python routes.

---

## 1. Thesis

Concordance is already good at one thing nothing else does: deciding whether a claimed
oncological difference survives its own study design, and what it becomes once the
assay's blind spot is subtracted. That engine is validated, refuses what it cannot
estimate, and produces tamper-evident capsules.

What it cannot do is **run an experiment**. Everything downstream of "the agent proposed
an experiment" is currently a label: the catalog says `ready`, the UI says launch on
Kaggle, and the compute layer returns `FAILED: this build does not implement Kaggle job
submission`.

The upgrade is therefore not "more features". It is one capability with a long tail:

> **Turn the experiment catalog into an experiment runtime: typed protocols, a compiler
> to pinned runnable units, a real execution mesh, an adjudicator that consumes results,
> and an autonomy policy that decides what to run next under a budget.**

Everything that makes this a *breakthrough* rather than a *demo* is a consequence of
doing that honestly:

| Today | After |
|---|---|
| 35 anchors described in JSON | 35 experiments with typed specs, most still non-executable, and the UI says which |
| "Launch on Kaggle" -> `FAILED` | real `kaggle kernels push/status/output`, real artifacts, real cost |
| Grade = heuristics over in-memory lists | Grade computed from run records, decision rules, and held-out checks |
| Autonomy = index PDFs every 15 min | Autonomy = pre-registered confirmatory runs within a spend and capability budget |
| Claim update = user edits counts | Claim update = a pre-registered run whose result the engine can adjudicate, or a refusal |
| Capsule = verdict + provenance | Capsule = verdict + replication package (`concordance replicate <id>`) |

**The wedge.** Not "AI discovers a drug". The credible, defensible, citable claim is:
*anyone can re-run a published oncology analysis, on public data, with the analysis plan
locked before results, and get a citable verdict or an explicit refusal — in minutes.*

That is a real gap. OSF stops accepting code/data submissions in Sept 2026; no platform
today does quantitative, design-corrected claim adjudication; and oncology's
reproducibility problem is well documented. It is also the only version of this product
that a reviewer, a journal, or a PI will take seriously.

---

## 2. Evidence: what actually exists today

Read from the tree, not from the docs.

| Capability | Reality | File |
|---|---|---|
| 15 research anchors, 10 cross-domain claims | loaded from bundled JSON into the claim table | `assets/research-claims.json`, `assets/cross-domain-claims.json`, `seedResearchQuestions()`, `seedCrossDomainExperiments()` |
| 10 programs x 4 sub-experiments | metadata only: title, `computeTarget` string, `status`, `durationWeeks` | `assets/cross-domain-experiments.json` |
| 25 sub-experiments | `seedSubExperiments()` counts `ready` vs `planned` and emits a notice. Nothing is persisted. | `ConcordanceViewModel.seedSubExperiments()` |
| "Launch on Kaggle" panel | hardcoded `listOf(...)` of 12 strings | `ui/screens/ResearchScreen.kt:98` |
| Kaggle execution | returns `KaggleJobStatus.FAILED`, summary "this build does not implement Kaggle job submission" | `integration/kaggle/KaggleComputeManager.kt:171-203` |
| Local job queue | submissions are simulated; `isSample = true`, summary prefixed `SIMULATED RESULT` | `integration/queue/JobQueueManager.kt` |
| Deep research round | pure functions over in-memory lists; grades/proposals never persisted | `research/DeepResearchSystem.kt`, `ResearchGrader.kt`, `InsightsAnalyzer.kt` |
| Experiment proposals | five templated rules with hardcoded `estimatedImpact` (0.7/0.9/0.5/0.8/0.6) | `research/ExperimentPlanner.kt` |
| Agent-designed experiments | `title/design/controls/endpoints/duration/risk/routing`. No id, params, data, or decision rule. | `integration/agent/AgentModels.kt:199` |
| Routing | maps `routing` string to a `WorkerRoutingPolicy`; `local` is explicitly not schedulable | `autonomy/ResearchRouting.kt` |
| Slow autonomy | indexes the local corpus in batches of 20 PDFs | `autonomy/SlowResearchWorker.kt` |
| Python job types | `sensitivity_curve`, `permutation_test`, `bootstrap_sweep`, `paired_analysis`, `figure_ocr_vlm`; queue is an in-memory dict | `src/concordance/jobs.py` |

What is genuinely strong and should not be rebuilt: the statistical engine and its
refusal semantics, `PairedAnalyzer`, capsule digest/integrity, `AgentClient`'s
`Refused` modelling, the data-class boundary, `ProvenanceRecorder`, `AutonomyActivity`,
`LocalCorpus`, and the ~25 live integration clients.

**Conclusion:** the missing subsystem is not AI, not UI, not auth. It is the execution
and adjudication layer between "a plan" and "a capsule".

---

## 3. Design

### 3.1 The five new nouns

Everything else follows from getting these five right.

```
ExperimentSpec  --(compiler)-->  RunPlan  --(executor)-->  RunRecord  --(adjudicator)-->  ClaimUpdate
                                      |                        |
                                      +--> preregistration     +--> artifacts + hashes
                                           hash, locked         +--> ledger entry
```

#### `ExperimentSpec` (IR, versioned, hashable)

The thing that makes an experiment an experiment rather than a wish.

```jsonc
{
  "id": "rp-esr1-cns-001",
  "version": 1,
  "class": "REPLICATION",            // see taxonomy, section 4
  "title": "Replicate ESR1 Y537S ctDNA/tissue discordance in CNS vs non-CNS",
  "question": {
    "hypothesis": "Plasma ctDNA under-detects ESR1 variants in CNS metastases",
    "direction": "discordance_higher_in_cns",
    "threshold": { "metric": "retention_ratio_r", "max": 0.40 }
  },
  "design": {
    "kind": "paired_diagnostic_accuracy",
    "unit": "patient",
    "contrast": "CNS vs non-CNS",
    "outcome": "tissue_positive_plasma_negative",
    "confounders": ["ctdna_fraction", "limit_of_detection", "assay_platform"],
    "blocking": ["assay_platform"]
  },
  "data": [
    { "ref": "cbioportal:esr1_paired_2026",
      "version": "2026-07-01",
      "sha256": "<file hash>",
      "license": "CC-BY-4.0",
      "data_class": "aggregate",
      "access": "PUBLIC" }
  ],
  "method": {
    "pipeline": "concordance.paired",
    "version": "0.3.0",
    "image_digest": "sha256:<container>",
    "params": { "correction": "delta_method", "ci": 0.95 },
    "seed": 20261028
  },
  "controls": {
    "negative": ["non_cns_stratum"],
    "positive": ["known_discordant_site"],
    "null_simulation": { "n": 1000, "effect": 0.0 }
  },
  "decision_rule": {
    "metric": "r",
    "comparison": "r < r_star",
    "alpha": 0.05,
    "power_target": 0.80,
    "multiplicity": "holm"
  },
  "preregistration": { "status": "required" },
  "budget": { "cpu_min": 15, "gpu_min": 0, "usd": 0.00, "network_calls": 2 },
  "ethics": { "data_class": "aggregate", "irb": "not_required_public" },
  "expected_artifacts": ["results.json", "spec_curve.csv", "session_info.json"]
}
```

Rules enforced by the type, not by convention:

- no spec without a `decision_rule` may produce a claim update;
- no spec without `preregistration: locked` may be `CONFIRMATORY`;
- `data[].access = CONTROLLED` cannot be auto-fetched (dbGaP/EGA) - it requires a
  user-supplied local file and stays on-device;
- `data_class != aggregate` cannot leave the host;
- a spec hash covers every field above, so any edit is a new version.

#### `RunPlan` (compiled)

What the executor actually receives, with nothing left to interpretation:

```
resolved inputs (url + sha256) | image digest | entrypoint | argv | env lock
seeds | reference build (GRCh38.p14) | tool versions | resource request
determinism class (BITWISE | STATISTICAL) | validation checks
estimated cost | approval requirements | expected artifact names + hashes
```

The compiler **fails closed**: unresolved dataset, missing image digest, unpinned
version, or a budget over the policy cap produces a plan of `Unsupported`, never a
best-effort run. A refusal is a result, so it is stored and shown, not hidden.

#### `RunRecord` (append-only state machine)

```
DRAFT -> PREREGISTERED -> PLANNED -> APPROVED -> QUEUED -> RUNNING
      -> COMPLETED | FAILED | REFUSED | CANCELLED -> ADJUDICATED
```

Each transition appends a hash-chained entry to the same ledger discipline the app
already uses for capsules. Mutation is not allowed; a correction is a new record that
supersedes. This is what makes "what did the system actually do at 3am" answerable.

#### `ClaimUpdate`

The only path from compute to knowledge. Either:

- `supported` / `contradicted` / `inconclusive`, with effect, CI, decision-rule result,
  deviation flags, and the run hash; or
- `refused`, with the reason (`zero_event_group`, `missing_denominator`,
  `plan_deviated`, `exploratory_run`, `data_class_blocked`, ...).

Exploratory runs cannot produce a `ClaimUpdate`. This single rule is what separates the
product from an automated p-hacking machine.

### 3.2 Executor mesh

One interface, four honest implementations.

```kotlin
interface ExperimentExecutor {
    suspend fun capabilities(): CapabilityMatrix   // per-class, per-data-access
    suspend fun submit(plan: RunPlan): ExecutionHandle
    suspend fun poll(handle: ExecutionHandle): RunProgress
    suspend fun fetch(handle: ExecutionHandle): RunArtifacts
    suspend fun cancel(handle: ExecutionHandle): Unit
}
```

| Executor | Runs | Replaces |
|---|---|---|
| `CloudExecutor` (`concordance-edge` + `mobile-gateway`) | run state, dispatch, artifact storage, LLM/OCR via Workers AI | the agent calls scattered across `AgentClient` |
| `KaggleExecutor` | real kernel push/status/output, GPU pipelines | the `FAILED` stub and the list-only `kaggle.ts` |
| `LocalDeterministicExecutor` | exact arithmetic on small CSVs, capsule verify, hashing, **adjudication** | phone-side stats |
| `UnsupportedExecutor` | nothing; returns a reason | silent simulation |

There is no `AgentExecutor`: with the rescope, nothing in the critical path runs on a laptop.
The phone adjudicates, Kaggle computes, Cloudflare routes.

`CapabilityMatrix` is rendered in the UI. "Kaggle: not configured" must look different
from "Kaggle: running". The existing `isSample` discipline is the right instinct; this
generalises it.

The phone stays a **controller**. It does not run scRNA-seq. It runs the deterministic
math, orchestrates, verifies hashes, and narrates.

### 3.3 Adjudicator

Inputs: `RunArtifacts`. Outputs: `ClaimUpdate` or a refusal.

1. Verify artifact hashes against the plan.
2. Recompute the planned metric from raw artifacts (never trust a self-reported number).
3. Check preregistration: executed pipeline + params + seed vs locked plan. Divergence
   -> `plan_deviated`, and the result cannot be `research-grade`.
4. Hand counts to the existing engine (`PairedAnalyzer` / correction). Do not
   re-implement the statistics here.
5. Emit a capsule if and only if the decision rule resolves. Otherwise `inconclusive`
   with the interval and the reason.
6. Record a negative case on every prune/failure/refusal, keyed by spec hash, so the
   planner cannot re-propose a dead branch.

### 3.4 Autonomy policy engine

This is the "greater autonomy" ask, made safe enough to leave running.

**Autonomy ladder**, chosen per experiment class, not globally:

| Level | Behaviour | Default for |
|---|---|---|
| A0 manual | user taps every run | wet-lab design, controlled-access data |
| A1 assist | prepares `RunPlan`, user approves each run | anything over the spend cap |
| A2 supervised | runs unattended; claim updates need approval | confirmatory replications |
| A3 delegated | runs and updates private claims unattended | robustness, negative controls, dataset builds |
| A4 (never) | - | public publishing, PHI, wet-lab execution, spend above cap |

**Budget governor** - daily ledgers per `usd`, `cpu_min`, `gpu_min`, `network_calls`,
`tokens`, plus battery/thermal gates already in `BatteryGate`. Hard stop, not a warning.
Every debit is an append-only row.

**Approval matrix**

| Action | Unattended |
|---|---|
| read public dataset, hash it | yes |
| run confirmatory replication under cap | yes |
| draft / grade / index / propose | yes |
| spend over cap, or new paid provider | no - queue approval |
| controlled-access or non-aggregate data | no |
| update a claim that is public or shared | no |
| publish to registry | no |
| execute an LLM-written script with network access | never |

**Scheduler.** Reuse `AutonomyModeManager` + `WorkManager` + `AutonomyActivity`. The
slow worker stops being "index PDFs" and becomes "ask the policy engine for the single
next allowed action, do it, log it". `LiveWorkCard` gains the approval queue.

### 3.5 Planner 2.0 (portfolio, not templates)

Replace the five templated rules with an expected-value-of-information score:

```
score(e) = expected_information_gain(e) * p_success(e) / cost(e)
```

- `expected_information_gain`: does this run discriminate between competing verdicts on
  the claim graph? Contested claims score high; already-certain claims score ~0.
- `p_success`: feasibility from `CapabilityMatrix`, data availability, and the negative-
  case table.
- `cost`: from the plan, not from `durationWeeks`.

Portfolio constraints made code (some are already stated in the operative outline):

- at least one replication-class experiment per five runs;
- at most one moonshot per ten;
- no repeated class in consecutive runs;
- a pruned branch is not re-proposed unless its data or regime changed;
- stop a branch after three consecutive nulls and write the negative case.

---

## 4. Wider scope: the experiment taxonomy

The current catalog mixes computational analyses with wet-lab work and treats them
identically. Eight classes, each with its own required fields, default autonomy level,
and default decision rule:

| # | Class | Artifact | Autonomy default | Notes |
|---|---|---|---|---|
| 1 | **Replication** | verdict capsule | A2 | highest value per unit cost; runs on public data; the methods-paper engine |
| 2 | **Robustness / multiverse** | spec curve, stability score | A3 | vary thresholds, normalization, batch correction, model class; reports how fragile a verdict is |
| 3 | **Dataset construction** | versioned dataset + manifest | A3 | e.g. build a curated CTC/ctDNA cohort from public sources; the dataset is the deliverable |
| 4 | **Negative control / calibration** | null distribution, false-positive rate | A3 | proves the pipeline can find nothing when there is nothing - the cheapest credibility |
| 5 | **Method benchmark** | estimator comparison on synthetic truth | A3 | which correction estimator is calibrated; directly feeds the engine's own validation |
| 6 | **Simulation / digital twin** | calibrated model + trajectories | A2 | PhysiCell, clonal dynamics; unmeasured parameters stay unmeasured |
| 7 | **Hypothesis genesis** | ranked hypotheses + rationale | A1 | literature + claim graph; **never** produces a claim update |
| 8 | **Wet-lab design** | protocol + power calc + ethics checklist | A0 | *design only*. The system never schedules bench work, animals, or patients. |

This widens scope without inflating claims: classes 1-5 are genuinely runnable by a
phone user with public data; 6 needs a cluster; 7 is already half-built; 8 is a document
generator and is labelled as such.

### 4.1 From catalog to specs

The existing JSON stays as the *narrative* layer (programs, milestones, ethics). A new
`experiment-specs.json` holds the executable subset. Migration is a one-way export:
`cross-domain-experiments.json` -> specs where a public dataset and a method exist;
everything else keeps `class` + `unsupported_reason` and renders as "design only".

That is not a downgrade. It is the first time the UI tells the truth about what is
rummable, and it converts 25 decorative sub-experiments into a roadmap with owners.

---

## 5. Scientific integrity invariants

Non-negotiable, each with a test that fails the build:

1. **No claim update from an exploratory run.** (`ExploratoryRunTest`)
2. **Preregistration before execution.** Spec hash + timestamp recorded before submit; a
   mutated plan invalidates the run. (`PreregistrationTest`)
3. **Recompute, never trust self-reported metrics.** Artifact hashes verified; the metric
   is recomputed from raw output. (`AdjudicatorRecomputeTest`)
4. **Refusal is a result.** Every executor returns typed `Unsupported`/`Refused` with a
   reason; nothing maps to zero. (`ExecutorHonestyTest`)
5. **Determinism is declared.** `BITWISE` runs must reproduce artifact hashes exactly;
   `STATISTICAL` runs must reproduce the verdict across N seeds within a stated
   tolerance. (`DeterminismTest`)
6. **PHI and controlled data never leave the host.** Same boundary as today, now checked
   at plan-compile time as well as at push time. (`PlanBoundaryTest`)
7. **Negative cases are remembered.** A pruned branch cannot re-enter the portfolio
   without new data. (`NegativeCaseTest`)
8. **Full audit.** Every state transition is a hash-chained ledger row, exportable.
   (`RunLedgerTest`)

These are the tests that make the autonomy claim defensible. They are also the
differentiator: every competitor can add a "run experiment" button; almost none can show
what the system is forbidden to do.

---

## 6. Data model

### Phone (Room, version 7 -> 8)

| Table | Purpose |
|---|---|
| `experiment_specs` | IR + spec hash + class + status (`DESIGN_ONLY`, `READY`, `UNSUPPORTED`) |
| `run_plans` | compiled plans, approval state, cost estimate |
| `runs` | `RunRecord` state machine, executor, handle, timings, refusal reason |
| `run_artifacts` | name, sha256, size, storage ref |
| `run_ledger` | append-only transitions + hash chain |
| `negative_cases` | spec hash, reason, data/regime version at time of prune |
| `budget_ledger` | daily debits per dimension |
| `goals` | research question, parent tree node, status |
| `approvals` | queued unattended actions awaiting a human |

Migration is additive; existing claim/capsule tables are untouched.

### Engine (`src/concordance/`)

- `spec.py` - pydantic `ExperimentSpec` + `RunPlan`, same validation discipline as
  `schema.py` (`extra="forbid"`, explicit refusals for missing 2x2 counts).
- `compile.py` - spec -> plan, fails closed.
- `adjudicate.py` - artifacts -> `ClaimUpdate` | refusal.
- `executors/` - `kaggle.py`, `local.py`, `cloud.py`, `unsupported.py`.
- `budget.py`, `prereg.py`.

### Cloudflare (`concordance-edge`, D1 + R2 + Queues)

Routes, on the Worker that already owns the job lifecycle:

- `POST /api/experiments/compile`
- `POST /api/experiments/{id}/preregister`
- `POST /api/experiments/{id}/runs` (submit to Kaggle), `GET /api/runs/{id}`, `POST /api/runs/{id}/cancel`
- `GET /api/runs/{id}/artifacts`
- `POST /api/runs/{id}/adjudicate` (accepts a capsule minted on the phone; stores, never computes)
- `GET /api/negative-cases`

The one missing capability today is in `kaggle.ts`: it implements `kernels/list` and
`kernels/status` but not `POST /kernels/push` or `kernels/output`. That is the first thing
to build, not the last.

---

## 7. Phases

Each phase ships independently and is testable. The credibility gate is Phase 1: if one
real experiment cannot run end to end, nothing after it matters.

### Phase 0 - Foundations and honesty (3-4 days)

- `ExperimentSpec` / `RunPlan` / `RunRecord` types + spec hashing + unit tests.
- Room v8 with the tables above (additive).
- Replace the hardcoded `readySubs` list and the `KaggleComputeManager` stub with a
  `status` read from the persisted specs (`READY` / `DESIGN_ONLY` / `UNSUPPORTED` +
  reason). Remove duplicated claims that an experiment is launchable when it is not.
- `CapabilityMatrix` + UI surfacing (configured / not configured / unsupported).

**Acceptance:** every experiment in the app reports a truthful status; no code path can
return a simulated success for a real run.

### Phase 1 - One real experiment, end to end (2 weeks)

Pick the flagship: **replication of the ESR1 CNS ctDNA discordance claim** (class 1).
It uses the existing engine, a public dataset, and it is the product's own core example.

- Real dataset pin (source, version, sha256, license) + local copy path.
- Container image with `concordance` pinned; `session_info.json` capture.
- `CloudExecutor` submit/poll/fetch against `concordance-edge`; real results.
- **The 60 missing lines in `kaggle.ts`:** `POST /kernels/push`, `GET /kernels/output`,
  per-run token, timeout, cancel. Without them no experiment can run.
- Adjudicator -> capsule with real numbers, or a real refusal. The capsule is minted on the
  phone and stored by the edge, not the other way round.
- Re-run reproduces the verdict; artifact hashes match.

**Acceptance:** a user taps once and gets a capsule whose numbers a third party can
reproduce from `replication.zip`. Re-running yields the same verdict (bitwise for the
stats package; statistical tolerance 0 for exact arithmetic).

### Phase 2 - Compiler and executor mesh (2.5 weeks)

- `compile.py` fail-closed rules + tests for each refusal.
- `KaggleExecutor` using the real API (`kernels push`, status polling, `kernels output`),
  with token rotation via the existing `ApiRotationManager`.
- `LocalDeterministicExecutor` for exact stats, verify, hashing.
- Cancel, retry, timeout, heartbeat; stuck-run recovery mirroring `JobQueue`'s 5-minute
  rule.
- Cost estimation from plan -> budget check before submit.

**Acceptance:** the same spec produces an identical `ClaimUpdate` whether adjudicated on the
phone or in the Kaggle kernel; a deliberately broken plan is refused, not run.

### Phase 3 - Autonomy policy engine (2 weeks)

- Ladder A0-A4 per class; approval matrix as data, not `if` statements.
- Budget governor with hard stops; `budget_ledger`.
- `SlowResearchWorker` becomes "one allowed action per wake", not "index PDFs".
- Approval queue in `LiveWorkCard`; kill switch; every decision logged.
- Autonomy tests: a policy-violating run is blocked, not silently downgraded.

**Acceptance:** leave the phone idle for a day on A3 and it completes confirmatory runs
within cap, never publishes, never touches PHI, and the log explains every step.

### Phase 4 - Planner 2.0 and wider scope (2.5 weeks)

- Claim graph (nodes: claims/verdicts; edges: challenges, supersedes, cites).
- EVOI scoring + portfolio constraints.
- Classes 2-6 implemented as spec templates with required fields.
- Negative-case retrieval wired into scoring (PRAXIS-MD pattern).
- Class 7 (hypothesis genesis) re-pointed at the claim graph instead of templates.

**Acceptance:** given three contested claims and a $0 budget, the planner picks
robustness and negative-control runs over new data acquisition, and says why.

### Phase 5 - Trust surface and replication packaging (1.5 weeks)

- `replication.zip` export: spec, plan, env lock, data manifest, seeds, scripts,
  expected outputs, `concordance.yaml`.
- `concordance verify <capsule>` and `concordance replicate <capsule>` CLI on the
  `concordance` Python package; container image published.
- GitHub Action: `concordance verify` on a capsule in CI.
- Capsule screen: "pre-registered", "deviated", "exploratory", "reproduced N times".

**Acceptance:** an outsider clones the replication package on a machine that has never
seen this app and gets the same verdict.

### Phase 6 - Ecosystem (later, gated on Phase 5 being real)

Publish to registry, team review, bounties, DOIs. Deliberately last: publishing an
unreproducible capsule is worse than publishing nothing.

**Effort:** roughly 10-11 focused weeks for one engineer, with Phases 0-1 (about 3 weeks)
being the point at which the product's claims become true.

---

## 8. How we will know it worked

Falsifiable, not vibes.

| Metric | Target |
|---|---|
| Replication verdict concordance vs published result, 30-claim public benchmark | >= 80% |
| Refusal precision on the same benchmark (human reviewer agrees with a refusal) | >= 90% |
| Claim updates originating from exploratory runs | 0 (invariant) |
| Re-run reproduces artifact hashes for `BITWISE` pipelines | 100% |
| Median time from tap to verdict, public-data replication | <= 20 min |
| Deviation detection (mutate the plan after preregistration) | 100% caught |
| External: `pip install concordance-engine` reproductions of a published capsule | >= 3 independent labs |
| External: capsules cited in a preprint or methods paper | >= 1 |

The benchmark is the same gold set already specified in `CONCORDANCE_BENCHMARK_PLAN.md`;
this plan supplies the machinery that makes those bounties resolvable rather than
aspirational.

---

## 9. Risks and mitigations

| Risk | Why it is real | Mitigation |
|---|---|---|
| Fake-compute theatre | the current build already contains a stub that "succeeds" elsewhere | Phase 0 makes status truthful; `ExecutorHonestyTest` |
| Autonomy becomes automated p-hacking | the loop can generate and select analyses faster than a human can audit | preregistration gate, exploratory/confirmatory split, multiplicity in the decision rule |
| Cost blowout | GPU runs are cheap per hour, expensive at volume | budget ledger with hard stops, EVOI cost term, default A2 |
| Nondeterminism in bioinformatics | R/Python drift, unpinned references | image digests, env lock, reference build pins, declared determinism class |
| Controlled-access data illusion | dbGaP/EGA cannot be auto-fetched | `access` field; those specs render as "bring your own file, stays local" |
| LLM writes code that runs with network access | prompt injection or plain error | never carry out of the allowlist; sandboxed, reviewed, no network |
| Over-claiming to users | "breakthrough" language | verdict or refusal, never a score without a decision rule; regulatory dialog unchanged |
| Scope creep into wet lab | the catalog contains IACUC/IRB experiments | class 8 is design-only, A0, and cannot be submitted to an executor |

### What we will not build

- No wet-lab, animal, or patient-facing execution. Design artifacts only.
- No LLM-authored analysis code executed unattended.
- No "autonomous discovery" claims without a preregistered decision rule.
- No simulation presented as measurement; `EvidenceTier` discipline applies to runs too.
- No second backend: Cloudflare dispatches, Kaggle computes, the phone adjudicates. No
  laptop service in the critical path.

---

## 10. Decisions needed before Phase 0

1. **Execution substrate.** Rescoped to phone + Cloudflare + Kaggle. The phone runs
   adjudication and orchestration; Kaggle runs every experiment; no laptop service is
   required. (Superseded the earlier agent-dependent wording.)
2. **Flagship class for Phase 1.** Replication of a published paired-concordance claim
   (recommended: uses the existing validated engine, public data, becomes the methods
   paper), or the XD-01 exosome/spatial program (higher narrative value, far heavier data
   engineering, no existing engine behind it).

Secondary, not blocking: which Kaggle token and whether a small Modal/AWS budget exists
for Phase 2 GPU runs. Phase 1 needs neither.

---

## 11. Compute topology (decision + verified quotas, 2026-10-28)

**Decision: heavy compute stays on Kaggle. Cloudflare is the control plane, never the
analysis engine.** This is not a preference; the included allowance makes it arithmetically
impossible to run bioinformatics on the Workers Paid plan.

Workers Paid ($5/mo) included compute, read from the account (`a7ce29a9...`) and the
official pricing pages:

| Service | Included per month | Headroom to a real experiment |
|---|---|---|
| Workers | 10M requests, 30M CPU-ms | fine for API/orchestration; a single scanpy run would eat the whole CPU budget |
| Workers AI | 10,000 neurons/day (~300k/mo) | good for triage, extraction, plain-language; not for analysis |
| Containers | **375 vCPU-min**, 25 GiB-h RAM, 200 GB-h disk | ~1.5 h at 4 vCPU, ~3 h at 2 vCPU. One small DESeq2 job, no GPU |
| D1 | 25B rows read, 50M rows written, 5 GB | ample for run/ledger state |
| R2 | 10 GB storage, 1M Class A, 10M Class B | ample; artifacts and replication packages fit |
| Queues | 1M operations | ample for job fan-out |
| Vectorize | 50M queried / 10M stored dims | ample for corpus + claim graph |
| Durable Objects | 1M requests, 400k GB-s | ample for `JobHub` |

Measured usage over the last 30 days: 18,289 Worker requests (0.18% of allowance), 6,280
AI neurons (2.1%), 1.13M D1 rows read (0.005%), 46,408 rows written (0.09%). The only
thing over its free allowance is R2 storage at 55.5 GB (`ecosystem-models`), about
**$0.68/month**. Everything else is effectively unused.

Consequences for the runtime:

| Component | Lives on | Why |
|---|---|---|
| `ExperimentSpec` / `RunPlan` / `RunRecord` | Phone (Room) + D1 mirror | state is tiny; must survive offline |
| Compiler, adjudicator, exact stats | Phone | deterministic, no GPU, no PHI egress |
| Class 1-5 experiments (replication, robustness, dataset, negative control, benchmark) | **Kaggle kernels** | free GPU/CPU hours are the entire reason this is affordable |
| Class 6 (simulation) | Kaggle GPU | same |
| Queue, retries, DLQ, cost guard | Cloudflare Queues + DO | already deployed (`concordance-edge`) |
| Artifacts + `replication.zip` | R2 | cheap, no egress fees, presigned links |
| Triage, OCR, plain-language | Workers AI (within 10k neurons/day) | cheap and already wired |

The self-imposed 9,500 neurons/day cap in `concordance-ai` sits just under the free
10,000/day allocation - keep it. The binding constraint is Kaggle's own weekly GPU
quota, not Cloudflare.

## 12. Open questions for later phases

- Do capsule IDs become `conc:` only, or DataCite DOIs at Phase 6?
- Should robustness/multiverse specs share one decision rule per family, or is each
  spec's rule independent? (Affects multiplicity correction.)
- Federated runs (per-site, weights only) - Phase 3 or Phase 6?
- How much of the planner's scoring is exposed to the user as an editable policy?
