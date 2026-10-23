# Concordance Autonomous Operation — Lite Plan (refined v3)

**Goal:** the app runs the research loop on its own — high power when an experiment needs extra
compute (routed to Kaggle/Cloudflare/AWS), slow in the background when the app is off-screen,
and fully offline using the models, datasets, and papers on the phone. The user gets notified
when an experiment completes and a paper is stored, plus 3 detailed status reports per day
(optionally spoken). The user never has to press "go" for routine work, but retains stop / go /
pause / state / quick-report controls.

This is the *efficient* variant: ~1 week, ~8 new files, no Room, no migrations, no FTS5, no
checkpoint/resume. Trades robustness for speed of delivery.

**v3 changes (advisor review):** added human feedback signal, claim ledger + stop rules,
real literature retrieval, human gates at forks, delta reports, experiment-level provenance,
and an evaluation harness. These are not polish — they are what makes the tool actually
progress research instead of producing volume.

---

## 0. What we are NOT doing (the rigor we drop)

| Full plan | Lite plan | Why it's OK |
|---|---|---|
| Room tables for mode/outbox/corpus | One JSON file `files/autonomy_state.json` | Single user, single device |
| FTS5 + chunked corpus + embeddings | Keyword search over `*.md`/`*.txt`/`*.json`/`*.csv` in `/sdcard/Concordance/` | Corpus is small; top-5 by term frequency is fine |
| Checkpoint/resume mid-step | Restart from last *completed* step recorded in JSON | Steps are short; re-running one is cheap |
| Battery-optimization exemption prompt | Check `%` and power-save mode; skip work if low; document manual toggle | Saves a day of OEM-specific work |
| Thermal monitoring | Skip; rely on llama.cpp's 6-thread cap | Already bounded |
| Outbox sync with conflict resolution | Append to `outbox.jsonl`; drain on reconnect; keep both on conflict | Good enough for one user |
| Mode state machine with transitions | A single `enum` field + one `StateFlow` | |
| Telemetry beyond logs | `Log.d` only | |

What we keep: WorkManager, the existing FGS pattern, `LocalLlmService`, the research loop,
decision model, critic, paper chain, `JobQueueManager` + `WorkerRoutingPolicy`, the agent's
`/api/research`, and the existing recursive learning loop (`learning.py`, `experiment_tree.py`,
`evolution.py`).

---

## 1. Operating modes

| Mode | Trigger | What runs |
|---|---|---|
| **HIGH_POWER** | "Run now", or a goal promoted to high power | Foreground service. Delegates each experiment to the right compute (local / Kaggle / Cloudflare / AWS / combination) based on the scope the agent generated. Runs the loop to completion. |
| **SLOW** | App not in foreground (automatic) | WorkManager periodic, 15 min, battery-not-low. Runs **one small step** per invocation (score one hypothesis, index a few files, draft one section). Stays running in the background. |
| **OFFLINE** | No network | No cloud calls. On-device model + local corpus + local paper chain. Cloud calls queued to outbox. |
| **PAUSED** | Battery < 20% or power-save, or user tapped Pause | Nothing runs until resumed. |

### Controls (always visible in the autonomy card)

- **Go** — start/resume (HIGH_POWER if a goal is queued, else SLOW).
- **Pause** — flip to PAUSED.
- **Stop** — cancel current run + clear the queue.
- **State** — badge + current goal + current step + last-run timestamp.
- **Quick report** — button that pops up a lightweight dialog with the app's state of affairs
  (mode, current goal, current step, queue size, last run, outbox count, battery). **Not** the
  detailed report — that comes from the agent 3×/day.

Mode transitions are automatic but user-overridable (force mode dropdown + "Auto" toggle).

---

## 2. High power = compute routing, driven by self-learning

The agent's `/api/research` returns a `routing` field per experiment:

```json
"experiments": [
  { "title": "...", "design": "...", "routing": "kaggle_gpu",
    "estimated_duration": "2 weeks", "risk": "..." }
]
```

Routing values map to the existing `WorkerRoutingPolicy`:

| `routing` | Policy | Use when |
|---|---|---|
| `local` | `LOCAL_WORKER` | Small, CPU-only, no data transfer |
| `aws_batch` | `AWS_BATCH_CPU` | CPU batch, large data |
| `aws_fargate` | `AWS_FARGATE_GPU` | GPU, medium data |
| `kaggle` | `KAGGLE_CLUSTER_GPU` | GPU, large data, long runs |
| `cloudflare` | edge worker / agent | Quick inference, small data |
| `combo` | multiple | Experiment needs more than one backend |

`HighPowerResearchService` enqueues each experiment via `ConcordanceWorkManagerScheduler` /
`JobQueueManager` with the chosen policy, then polls `JobQueueManager.getJobStatus` and shows
progress in the notification. When all experiments finish, it writes the paper chain and fires
a completion notification.

### 2.1 Auto-classification from the decision model (not a keyword pass)

Routing and the next experiment's design are **not** classified by keywords. They are produced
by feeding prior results into the decision model so the system progresses:

```
completed experiment
   ↓  result + analysis + comparative factors
   ↓  (negative cases, withheld reports, expert overrides, tree children)
   ↓
recursive learning loop  (learning.py: run_learning_cycle)
   ↓  proposes tools / skills / experiments
   ↓
experiment tree          (experiment_tree.py: spawn children from results)
   ↓
decision model          (decision.py: score_candidate with prior results in `existing`)
   ↓  gates: proceed | revise | fork | prune | escalate | block
   ↓
next /api/research call  (hypotheses + experiments seeded with prior results + gaps)
   ↓
agent classifies routing from the scored, gated experiment design
```

Concretely:

- `/api/research` accepts an optional `prior_run_id`. When supplied, the agent loads that run's
  `results.json` + `analysis.md` and injects a "Prior results and gaps" block into the system
  prompt, and seeds `existing` statements from the experiment tree + registry so novelty is
  computed against prior work.
- The decision model's `existing` list includes prior hypotheses *and* prior experiment titles
  *and* prior result summaries, so a near-duplicate of an already-tested idea is pruned.
- `specificity()` is extended to reward candidates that explicitly name a gap being filled
  ("fills the gap in X", "addresses the void in Y").
- `run_learning_cycle()` and `run_evolution_cycle()` run inside the autonomy cycle and feed
  their proposals into the next research run's context.
- The experiment tree spawns child experiments from a parent's result (already implemented in
  `experiment_tree.py`); the lite plan just ensures `/api/research` calls `bootstrap_tree` /
  `spawn` so results drive the next generation.

The point: each next set of experiments and sub-experiments is rooted in a progressive state of
seeking valid cures, answers, breakthroughs, resolves, potential medicines, greater
understanding, and filling gaps/voids in current research — because the decision model has
seen the prior results and the learning loop has proposed the gaps.

The user can still override routing per experiment in the UI before it runs.

---

## 3. Offline corpus

Path on phone (user-created, will be populated):

```
/sdcard/Concordance/
  Datasets/      (csv, json, parquet, txt)
  Papers/        (md, txt, pdf-text)
  Research/      (md, json, prior runs)
```

`LocalCorpus` walks those three folders, chunks `*.md`/`*.txt`/`*.json`/`*.csv` into ~1000-char
pieces (100-char overlap), caches to `files/corpus.json`, and searches by term frequency
(top-5). No embeddings, no vector store. If the corpus grows past ~10 MB, switch to SQLite
FTS5 (one afternoon's work, not now).

In OFFLINE mode, `ResearchEngine` uses `LocalCorpus` + the on-device model instead of the
agent/cloud. Papers are written to `files/research/<run_id>/` (same filenames as the agent).

---

## 4. Notifications and reports

### Completion notifications
When an experiment finishes and its paper is stored on the phone, fire a high-priority
notification (existing `CHANNEL_COMPLETION`) with the experiment title and a tap-to-open
intent pointing at the paper file. One notification per completed experiment.

### Quick report (in-app popup)
The "Quick report" button pops up a lightweight dialog with the **app's state of affairs**:
mode, current goal, current step, queue size, last run, outbox count, battery. **Not** the
detailed report — that comes from the agent 3×/day.

### Detailed report (agent, 3×/day)
The agent gains:
- `POST /api/reports/generate` — produces a structured report:
  - state of research (active goals, queued, paused)
  - progress (steps completed since last report, experiments finished/failed)
  - potential breakthroughs (hypotheses that scored `proceed` with high confidence, or
    critic `pass` on a new result)
  - next steps (top-3 by decision score)
  - issues (failed experiments, withheld reports, outbox backlog, rate limits)
- `GET /api/reports/latest` — returns the most recent report + timestamp.
- Reports persisted to `data/reports/<timestamp>.md` and `data/reports/latest.json`.

Scheduling: in-process `APScheduler` with 3 daily triggers (default 08:00 / 14:00 / 20:00
local, configurable). Each run calls `generate` and stores the result.

### Spoken reports
Option to have reports spoken. Two paths:
- **On the phone:** Kokoro `bm_lewis` (sid 27) reads the report text (already wired via
  `SherpaKokoroSynthesizer`). "Speak report" button on the popup and on the daily report.
- **From the agent:** MeloTTS via the Cloudflare Worker (`/api/tts`) for a server-side
  audio file the app can download and play.

Default: on-device Kokoro. Agent-side TTS is a fallback when offline TTS weights are missing.

---

## 5. Advisor refinements (the things that make it a *greater* tool)

These are not optional polish. They are the difference between a tool that produces volume
and one that produces insight.

### 5.1 Human feedback signal (highest leverage, ~2 days)

Add a one-tap rating on every hypothesis and experiment in the app:
`useful / not useful / already known / out of scope`. Store in `autonomy_state.json` (or a
tiny Room table).

Feed it back two ways:
1. **Generation prompt:** "Previously rated useful: [list]. Previously rated not useful:
   [list]. Avoid the latter patterns."
2. **Decision model:** add a `human_usefulness` component to the score, and let the weights
   drift based on which components correlated with high ratings (running average, not ML).

Without this, the system has no idea whether its output was useful and stays at initial quality
forever.

### 5.2 Claim ledger + stop rules (turns an infinite loop into a converging program, ~3 days)

**Claim ledger:** a structured store (JSON or Room table) of claims:
```
claim | support (list of run_ids) | refute (list) | confidence | open_questions | last_updated
```
Every run updates it. The decision model scores new hypotheses against the *current claims*,
not just prior hypothesis text. The 3×/day report says "claim X moved from 0.4 to 0.7
confidence because of run Y."

**Pre-registration:** before running, state the decision rule explicitly — "if the combination
reduces proliferation by ≥30%, support H1; if <10%, refute." The critic already checks for a
prediction; make the prediction *checkable* and store it.

**Stop conditions:** stop a goal when (a) confidence > 0.85 and critic passes, (b) N consecutive
experiments fail to move any claim's confidence by > 0.05, or (c) the budget (neurons,
experiments, time) is exhausted.

**Dead-end list:** when a hypothesis is pruned, record *why* and add it to a "do not
re-propose" list fed into the generation prompt. Prevents re-proposing variants of pruned ideas.

The 15-minute idle loop with no stop condition is the riskiest part of the plan. Stop rules
are not optional — they are what makes the autonomy safe. Implement them in Phase 1, not
Phase 7.

### 5.3 Real literature retrieval (not just query generation, ~3–4 days)

The agent generates `literature_queries` but never retrieves or reads papers. The phone has a
corpus, but `LocalCorpus` is keyword search over files the user manually drops in. Real
research needs:

- The agent calls PubMed/arXiv/Crossref (the app already has these in the scientific-APIs
  tab) and pulls abstracts.
- A claim-extraction pass: "paper X reports Y about ESR1 Y537S."
- Compare extracted claims against the claim ledger; flag contradictions and gaps.

Even top-5 abstracts per query would transform output quality. This is the biggest *capability*
gap, not just polish.

### 5.4 Human gates at forks and escalations (~2 days)

`fork` and `escalate` actions exist but there's no UI to resolve them — they just get logged.
A fork ("this is ambiguous, here are two branches") is exactly where a human researcher adds
the most value.

**Change:** a "Pending decisions" queue in the app with push notifications. Each item shows
the candidate, the two branches, the scores, and one-tap "pursue branch A / B / both /
neither." The decision model learns from the choice (feeds 5.1).

### 5.5 Delta reports, not full-state reports (~1 day)

The 3×/day report currently describes the full state. After a week of runs, that's noise.
Make it a **delta**: "since the last report, claim X moved, experiment Y completed, hypothesis
Z was pruned because…, the next highest-value action is W." Include a "what changed my mind"
section.

### 5.6 Experiment-level provenance (~1 day)

`provenance.py` captures code version + environment, but it's not tied to individual runs or
experiments. A research tool must be able to answer "what exact model, prompt, temperature,
and inputs produced this result?" Wire `provenance.capture()` into every `run_research` call
and store the hash in the manifest (partially there — make it complete and queryable).

### 5.7 Evaluation harness (~2 days)

How do you know the loop is getting better? Build a small gold set: 5–10 research questions
with a rubric (does it identify the real gap? does it propose a feasible experiment? does the
critic catch the overclaim?). Run the loop on them monthly and score. Without this, you can't
tell whether a change helped.

---

## 6. Architecture (lite)

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose)                                                │
│   Autonomy card: mode badge · Go/Pause/Stop · state ·        │
│   quick report (popup) · outbox count · "Run now"           │
│   Pending decisions queue · feedback ratings (useful/not)    │
└────────────┬────────────────────────────────────────────────┘
             │
┌────────────┴────────────────────────────────────────────────┐
│  AutonomyModeManager  (JSON-backed StateFlow)                │
│   mode, goalQueue, currentGoal, currentStep, outbox,         │
│   lastRunAt, lastReportSeen, claimLedger, deadEnds,          │
│   feedbackRatings, pendingDecisions                          │
└────┬──────────────┬──────────────────────┬──────────────────┘
     │              │                      │
     ▼              ▼                      ▼
┌─────────┐  ┌──────────────┐  ┌────────────────────────────┐
│ FGS     │  │ WorkManager  │  │ NetworkMonitor             │
│ HighPow │  │ SlowWorker   │  │ (flips OFFLINE)            │
│ (route) │  │ (15 min)     │  │                            │
└────┬────┘  └──────┬───────┘  └────────────┬───────────────┘
     │              │                       │
     └──────┬───────┴───────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────────┐
│  ResearchEngine                                              │
│   online → agent (/api/research, /api/decision/score, ...)   │
│   offline → local model + LocalCorpus + paper chain          │
│   outbox: deferred cloud calls (JSONL)                       │
│   claim ledger update + stop-rule check after every run       │
└────────────┬────────────────────────────────────────────────┘
             │
┌────────────┴────────────────────────────────────────────────┐
│  Local: autonomy_state.json, outbox.jsonl, corpus.json,      │
│  files/research/<run_id>/, /sdcard/Concordance/{Datasets,    │
│  Papers, Research}, llama.cpp FGS, Kokoro TTS               │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. Implementation order (≈ 1 week, revised)

1. **Day 1:** `AutonomyState` (with claim ledger, dead-ends, feedback, pending decisions),
   `AutonomyModeManager`, `NetworkMonitor`, `BatteryGate`, settings card with Go/Pause/Stop/
   State/Quick-report. Mode badge flips ONLINE/OFFLINE automatically. **Stop rules in the
   research loop from day 1.**
2. **Day 2:** `SlowResearchWorker` (one step, constraints, notification). Verify with screen off.
   Human feedback rating UI on hypotheses/experiments.
3. **Day 3:** `LocalCorpus` (walk `/sdcard/Concordance/`, chunk, keyword search) + offline
   branch in `ResearchEngine`. Claim ledger update after every run.
4. **Day 4:** agent-side `/api/reports/*` + APScheduler 3×/day. Delta reports. Kokoro
   "Speak report" button. Pending decisions queue UI.
5. **Day 5:** `HighPowerResearchService` (FGS, routing per experiment via `JobQueueManager`,
   completion notifications). Literature retrieval step in `/api/research`.
6. **Day 6:** outbox drain (`SyncWorker`) + "Sync now" button. Wire `prior_run_id` + recursive
   learning loop into `/api/research`. Provenance wiring.
7. **Day 7:** polish, thresholds, airplane-mode end-to-end test, test the self-learning loop
   across 2 runs, run the evaluation harness on the gold set.

---

## 8. What this does NOT do (accepted limitations)

- No guaranteed execution under aggressive OEM battery killers (WorkManager handles most; FGS
  handles the rest, but Xiaomi/Samsung may still kill). Document the manual "don't optimize"
  toggle.
- No thermal throttling awareness (rely on llama.cpp's thread cap).
- No conflict resolution beyond "keep both, log warning".
- No checkpoint/resume mid-step (re-run the step).
- No telemetry beyond logcat.
- No Room, no migrations, no FTS5, no embeddings.
- No multi-user / collaboration (single device).

---

## 9. If you only do three (advisor priority)

1. **Human feedback signal** (5.1) — highest leverage, 2 days.
2. **Claim ledger + stop rules** (5.2) — turns an infinite loop into a converging program, 3 days.
3. **Real literature retrieval** (5.3) — grounds everything else, 3–4 days.

Everything else is polish on top of those.

---

## 10. Open questions (final)

1. SLOW mode runs **only while the app is not in the foreground** — confirm that's the trigger
   you want (vs. "device idle").
2. High-power routing: **auto-classify** from the experiment design (decision model + prior
   results), with **per-experiment override** in the UI before it runs — confirm.
3. The 3×/day report times (default 08:00 / 14:00 / 20:00) — keep or change?
4. Report delivery: notification + cached locally (so "Quick report" works offline) +
   "Speak report" button — confirm. Should the agent also post the detailed report anywhere else?
5. Gold-set evaluation: do you have 5–10 research questions you'd consider "good" that I can use
   as the eval baseline?

Answer and Phase 1 starts immediately. The full rigorous version remains in
`CONCORDANCE_AUTONOMY_PLAN.md` if you want the heavier machinery later.
