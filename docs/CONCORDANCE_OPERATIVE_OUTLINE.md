# Concordance — Operative Outline: Autonomous Research System

**Goal.** Make every component (on-device LLM, self-hosted agent, Cloudflare edge, Supabase community) interoperate autonomously, keep the system productive, produce papers locally, generate hypotheses, and use a decision model at each step to stay on track.

**Guiding principles (from 2026 autonomous-research literature).**
- Hypothesis-Tree Refinement (Arbor/HTR): a persistent tree binding *hypothesis → artifact → evidence → insight*; merge only on held-out improvement; backpropagate insights.
- Separation of proposal and execution (Organizational Control Layer): an LLM proposes, a policy gate approves/blocks/escalates before any state change.
- Specialized roles with traceable artifacts (MetaGPT-style): planner, executor, critic/verifier, archivist; every stage writes a file so blame is assignable.
- Productivity discipline: diversified search, budgeted moonshots, explicit forks, regime-aware re-validation; avoid greedy hill-climbing and action loops.
- Human-in-the-loop at phase transitions only; tactical decisions are autonomous; every intervention is recorded.
- Local-first: papers, hypotheses, experiments, and evidence live on the device/agent; nothing leaves unless explicitly published.

---

## 0. Implementation status (this pass)

The operating core is now code, not just a plan:

| Piece | Module | Surface |
|---|---|---|
| Deterministic decision model | `app/decision.py` | `GET /api/decision/formula`, `POST /api/decision/score`, `GET /api/decision/ledger`, `GET /api/decision/ledger/verify` |
| Claim–evidence critic | `app/critic.py` | `POST /api/critic/check` |
| Local paper artifact chain | `app/paper_chain.py` | `GET /api/research/runs`, `GET /api/research/runs/{id}`, `GET /api/research/runs/{id}/verify` |
| Research-loop orchestrator | `app/research_loop.py` | `POST /api/research` |
| Prioritisation fallback | `app/autonomy.py` | autonomy cycle now scores candidates deterministically when Jev is absent |

**What happens on one `POST /api/research`:**

1. Model produces the report (or a deterministic scaffold if the model is rate-limited/unset — `llm.ok=false`, no retry).
2. Every hypothesis and experiment is scored and gated (`proceed | revise | fork | prune | escalate | block`).
3. Accepted candidates become experiment-tree nodes and registry entries.
4. The critic checks claim–evidence alignment (`pass | revise | withhold`); a withheld report is retained for audit but marked `presentable_as_result=false`.
5. A local chain is written under `data/research/<run_id>/`: `idea.md`, `hypothesis.md`, `experiment_plan.md`, `results.json`, `analysis.md`, `paper_outline.md`, `paper.md`.
6. `manifest.json` hashes every artifact and carries an HMAC-signed receipt; re-hashing verifies the run.
7. A decision record is appended to the separate hash-chained `data/decision_ledger.jsonl`.

**Verification:** `528 passed` in the agent test suite, including 10 new tests in `tests/test_decision_loop.py`.

---

## 1. Component map and ownership

| Component | Owns | Talks to |
|---|---|---|
| Android app (on-device) | UI, voice I/O, local LLM (Qwen3.8 4B / Ministral 8B), Kokoro TTS, local extraction, deep-research engine, paper store | Agent (via Cloudflare tunnel), edge (submit/list/live) |
| Self-hosted agent (FastAPI) | Adjudication, research, experiment design, LLM routing, OCR, receipts/audit, ledger | Cloudflare AI Worker, edge worker, Supabase |
| Cloudflare edge worker | D1, DO JobHub, Queue+DLQ, Workflow, R2 archive, Vectorize, cron, budget guard | Agent, app |
| Cloudflare AI worker | Workers AI (chat, image, STT, TTS), AI Gateway cache, neuron budget (9,500/day) | Agent |
| Supabase | Community website, auth, RLS-protected tables | Web app, agent (publish) |

**Rule:** each component owns its state; cross-component calls go through typed clients with provenance recording (existing `ProvenanceRecorder`).

---

## 2. Decision model (used at every step)

A single **scoring function** ranks candidate actions. It is the "decision model" referenced at each gate.

```
score(candidate) =
    evidence_weight * (held_out_improvement)        # did it actually improve?
  + novelty_weight  * (distance from prior work)     # avoid greedy hill-climbing
  + feasibility_weight * (resource_cost_inverse)      # budget-aware
  + risk_penalty   * (policy_violation_flag)          # OCL gate
```

- **Inputs:** hypothesis, artifact version, prior evidence, budget remaining, policy constraints.
- **Outputs:** `proceed | revise | fork | prune | escalate`.
- **Thresholds:** `merge` requires strict held-out improvement (Arbor merge gate); `escalate` triggers when confidence < 0.6 or policy ambiguity.
- **Audit:** every decision is appended to the ledger (`/api/ledger`) with the score breakdown and the inputs, so the trajectory is replayable.

### Per-step decision gates

| Step | Gate | Decision |
|---|---|---|
| Hypothesis generation | Novelty + feasibility score | proceed / revise wording / discard |
| Literature triage | Relevance score vs. query | keep / drop / flag for human |
| Experiment design | Policy + resource check (OCL) | approve / revise / block |
| Execution | Precondition check (no invalid actions, no loops) | run / skip / escalate |
| Result interpretation | Held-out improvement + significance | merge / prune / fork |
| Paper drafting | Claim-evidence alignment check | draft / withhold / request more data |

---

## 3. Autonomous research loop (core pipeline)

Modeled on Hypothesis-Tree Refinement + the 6-agent scientific workflow, adapted to this stack.

```
┌─────────────────────────────────────────────────────────────┐
│  COORDINATOR (agent) — maintains the hypothesis tree        │
│  - Expands tree, dispatches executors, refines on feedback  │
└────────┬────────────────────────────────────────────────────┘
         │
   ┌─────┴─────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │  Planner  │  │ Executor │  │  Critic  │  │ Archivist│
   │  (ideas)  │  │ (runs)   │  │(verify)  │  │ (papers) │
   └───────────┘  └──────────┘  └──────────┘  └──────────┘
```

**Loop (one round):**

1. **Ingest** — voice/text question → transcript (on-device STT, never leaves device).
2. **Plan** — agent `/api/research` generates hypotheses + literature queries + experiment designs (uses Workers AI, non-PHI).
3. **Triage** — local deep-research engine grades papers, extracts counts (Qwen3.8, `enable_thinking=false` for extraction).
4. **Decide (gate 1)** — decision model scores each hypothesis; selects top-k, prunes the rest, logs to ledger.
5. **Design** — agent designs experiments (controls, endpoints, duration, risk). OCL gate checks policy/resource.
6. **Execute** — runs locally where possible (on-device LLM, local data); heavy work goes through the agent/edge (queued, retried, DLQ).
7. **Verify** — critic checks claim-evidence alignment, metric calculations, and reproduces numbers; refusal is a normal outcome, not an error.
8. **Merge** — held-out improvement required before a result becomes "current best" (Arbor merge gate).
9. **Backpropagate** — insights flow up the hypothesis tree; failures are recorded as negative cases (`/api/negative-cases`).
10. **Archive** — archivist writes the paper outline + manuscript locally (on device / agent host), with provenance and receipt.
11. **Publish (optional)** — only on explicit user action: community website (Supabase) or edge (R2/Vectorize).

**Termination / productivity guards:**
- Diversified search: never repeat the same hypothesis class two rounds in a row.
- Budgeted moonshots: at most 1 high-risk experiment per 5 rounds.
- Explicit forks: branch the tree when a hypothesis is ambiguous, don't collapse.
- Regime-aware re-validation: re-check merged results when the data distribution changes.
- Anti-collapse guard (already in code): abort a learning round if diversity drops.
- Stall detection: watchdog (`/api/watchdog`) flags jobs stuck > N minutes.

---

## 4. Checks and balances

| Layer | Mechanism | Where |
|---|---|---|
| Policy gate | OCL: approve / revise / block / escalate before any state change | agent middleware |
| Refusal as result | Agent refuses when evidence insufficient; UI shows it honestly | `AgentOutcome.Refused` |
| Held-out merge gate | Candidate merges only on strict held-out improvement | research loop |
| Critic/verifier | Independent check of claim-evidence alignment and metric math | `/api/correction`, `explain_job` |
| Blame assignment | Each artifact tagged with producing agent + step + input hash | provenance records |
| Audit trail | Append-only ledger with hash chain | `/api/ledger`, `/api/audit/verify` |
| Receipts | Signed receipts for published claims | `/api/receipts/verify` |
| Budget guard | Hard 9,500 neurons/day on Workers AI; queue + DLQ on edge | concordance-ai worker, edge worker |
| PHI boundary | `is_phi` check refuses to send clinical PHI to managed AI unless override | agent + app |
| Human gate | Phase transitions and final publication require explicit user action | app UI |
| Anti-collapse | Learning round aborted if diversity metric trips | `DeepResearchSystem` |
| Stall watchdog | Flags jobs stuck beyond timeout | `/api/watchdog/events` |

---

## 5. Local paper production

Papers are produced **locally** (on device / agent host), never on a managed platform.

**Artifact chain (each step writes a file, provenance-recorded):**

```
idea.md  →  hypothesis.md  →  experiment_plan.md  →  results.json
   →  analysis.md  →  paper_outline.md  →  paper.md (manuscript)
```

- **On-device:** `runDeepResearch()` grades papers, proposes experiments; `SciencePaperManager` ingests live literature; `LocalExtractionClient` extracts structured claims (Qwen3.8, no-think).
- **Agent:** `/api/research` produces research report + experiment designs; `/api/correction` adjudicates claims; `/api/explain/*` produces plain-language versions.
- **Storage:** local filesystem + R2 archive (edge) for capsules; Supabase only for published community claims.
- **Reproducibility:** every paper carries the model id, prompt, seed, and input hashes; `paper_receipt` endpoint verifies.

---

## 6. Hypothesis generation

- **Sources:** literature (Europe PMC, OpenAlex, arXiv), on-device claim store, INDRA prior knowledge, negative cases.
- **Method:** portfolio approach (multiple hypotheses per round, not one); paper-mashing for idea generation; chess-style Elo ranking for hypothesis competition (Nature Methods 2026).
- **Generation rules:**
  - At least 3 candidate hypotheses per round.
  - One "moonshot" (high risk) per 5 rounds, budgeted.
  - No duplicate hypothesis class within 2 rounds (diversified search).
  - Each hypothesis must state: claim, mechanism, predicted effect size, required data, falsification criterion.
- **Output:** structured `Hypothesis` objects stored in the tree, linked to parent and to resulting experiments.

---

## 7. Interconnection map (who calls whom)

```
Voice ──► App (transcript) ──► Agent /api/research ──► Cloudflare AI (LLM)
                                    │
                                    ├──► /api/llm/complete (chat)
                                    ├──► /api/llm/transcribe (STT)
                                    └──► /api/llm/speak (TTS)
                                    │
                                    ▼
                              Edge worker (submit/list/live, R2, Vectorize, D1)
                                    │
                                    ▼
                              Supabase (published claims, auth, RLS)
```

- **App ↔ Agent:** HTTPS via Cloudflare tunnel; API key auth; `x-api-key`.
- **Agent ↔ Edge:** `_publish_edge` queues jobs, archives to R2, indexes in Vectorize.
- **App ↔ Edge (direct, future):** submit/list/live updates via `x-edge-key`.
- **App ↔ Supabase:** anon key + RLS; auth via Supabase Auth.

---

## 8. Operative runbook (one round, end to end)

1. **Speak** the research question (on-device STT → transcript box).
2. **Review/edit** the transcript (human checkpoint).
3. **Research** — tap "Research & design experiments" → agent `/api/research` → findings + experiments.
4. **Triage** — local engine grades papers, extracts claims (Qwen3.8).
5. **Decide** — decision model scores hypotheses; selects top-k; logs to ledger.
6. **Design** — experiments with controls/endpoints; OCL gate approves.
7. **Execute** — local where possible; heavy work queued on edge.
8. **Verify** — critic checks alignment; refusal shown honestly.
9. **Merge** — held-out improvement required.
10. **Backpropagate** — insights + negative cases into hypothesis tree.
11. **Archive** — paper outline + manuscript written locally with provenance.
12. **Publish** (manual) — to community site / edge if user chooses.

---

## 9. Open items

- [ ] Apply `apply_rls_idempotent.sql` in the Supabase SQL Editor; verify `pg_policies` (37 policies).
- [ ] Rotate the Supabase service-role key after RLS is verified.
- [ ] Install the rebuilt APK once the phone reconnects (`adb` currently shows no device).
- [ ] Re-import `concordance_config.json` on the phone: the quick-tunnel URL changed to
      `https://complicated-nobody-nuts-leadership.trycloudflare.com`.
- [ ] Replace the quick tunnel with a named Cloudflare Tunnel (stable hostname).
- [ ] Wire the app directly to edge submit/list/live (currently via the agent).
- [ ] Add explicit fork/branch UI in the hypothesis tree (the backend now emits `fork`).
- [ ] Decide whether the autonomy cycle may auto-run `POST /api/research` with `use_llm=true`
      (cost guard: default stays `use_llm=false` / scaffold).
- [ ] Mistral LLM chat remains rate-limited; do not retry in a loop.

---

## 10. File references

- `oncology-agent\app\decision.py` — deterministic scoring + gates + decision ledger.
- `oncology-agent\app\critic.py` — claim–evidence critic (`pass | revise | withhold`).
- `oncology-agent\app\paper_chain.py` — local artifact chain + signed manifest + verification.
- `oncology-agent\app\research_loop.py` — orchestrator wiring report → gates → tree → critic → paper → ledger.
- `oncology-agent\app\main.py` — `/api/research`, `/api/decision/*`, `/api/critic/check`, `/api/research/runs*`.
- `oncology-agent\app\autonomy.py` — deterministic prioritisation fallback.
- `oncology-agent\app\ledger.py` — now verifies across `AGENT_RECEIPT_KEY_PREVIOUS` rotation.
- `oncology-agent\start-agent.ps1` — starts the agent; reads secrets from disk, never embeds them.
- `oncology-agent\tests\test_decision_loop.py` — 10 tests for the new core.
- `Concordance-main\...\integration\agent\AgentClient.kt` — parses decisions, critic, paper metadata; sends `use_llm`.
- `Concordance-main\...\ui\screens\AgentScreen.kt` — research card shows gates, critic findings, local paper chain; "Use cloud model" toggle.
- `Concordance-main\...\ui\ConcordanceViewModel.kt` — `runAgentResearch(useLlm)`.
- `Concordance-main\...\local\LocalExtractionClient.kt` — extraction client (no-think + envelope fix).
- `Oncology-Ecosystem-main\...\apply_rls_idempotent.sql` — idempotent RLS script.
- `Downloads\local-models\MODELS.md`, `README-phone.md` — model docs.
- `concordance-audit-2026-10-19.md` — full audit.
- `Downloads\opencode api.txt` — API keys, including the agent receipt key.
- `C:\Users\tap45\CONCORDANCE_BENCHMARK_PLAN.md` — benchmark/bounty system, research ingestion, evaluation harness, open source + privacy. **Finalized.**
- `C:\Users\tap45\Downloads\deep-research-report.md` — 15 research questions + 50 bounties (the benchmark seed set).
- `C:\Users\tap45\CONCORDANCE_AUTONOMY_PLAN.md` — rigorous variant (Room/outbox/FTS5), kept for reference.