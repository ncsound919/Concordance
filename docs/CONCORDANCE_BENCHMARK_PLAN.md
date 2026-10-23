# Concordance Benchmark & Bounty System Plan (FINAL)

**Status:** finalized. Decisions locked (see §10). 15 research questions + 50 community
bounties defined in `C:\Users\tap45\Downloads\deep-research-report.md` are the seed set.

**Vision:** 50–100 research benchmarks/bounties on the website, each with milestones, contributions,
leadership, research, and breakthroughs. The evaluation harness has *purpose*: it measures whether
the autonomous research tool actually advances real research, and the community uses open-source
customization to wire up home labs, wet labs, and backend systems — privately if they choose.
Research begins where the user wants.

---

## 0. Core principles

1. **Start where the user wants.** No prescribed starting point. A family member with a cancer
   diagnosis, an oncologist on rounds, a wet-lab PI, and a data scientist all begin from their
   own question, their own data, their own constraints. The system ingests whatever they bring
   and finds the best-case experiments and a founded starting point from there.
2. **Private by default, communal by choice.** Research can run entirely on-device or on a
   self-hosted agent. Community features (benchmarks, leaderboards, collaboration) are opt-in.
   No data leaves the user's machine unless they publish.
3. **Open source, customizable.** The app, agent, and web platform are open source. Users wire
   up their own labs, instruments, and backends via a plugin/adapter interface.
4. **Benchmarks are real research questions, not toy tasks.** Each benchmark is a genuine
   oncology research question with a published rubric, milestones, and a bounty for completion.

---

## 1. Personas

| Persona | Need | How the system serves them |
|---|---|---|
| **Family/friend of a cancer patient** | Understand the disease, options, and research landscape; feel agency | Ingest their documents (pathology reports, papers, notes). Get plain-language insights, hypotheses, and a starting point for questions to ask their oncologist. No clinical advice — research framing only. |
| **Oncologist / scientist (on the go)** | Autonomous research + remote reporting | Run the full loop from a phone. Get 3×/day detailed reports. Delegate heavy compute to cloud. Speak reports via Kokoro TTS. |
| **Home / wet lab** | Wire up instruments, run experiments, feed results back | Adapter interface for lab instruments (LIMS, sequencers, plate readers). Results flow into the claim ledger and next-experiment design. |
| **Community contributor** | Contribute to open benchmarks, earn bounties, build reputation | Solve benchmarks, submit capsules, earn credits/bounties, climb leaderboards. |
| **Data scientist / engineer** | Customize the pipeline, add models, integrate backends | Open-source codebase, plugin API, CI evaluation harness. |

---

## 2. Research ingestion and analysis system

The "start where the user wants" engine. Ingests arbitrary documents and produces a founded
starting point for autonomous research.

### 2.1 Ingestion
- **Input types:** PDF (papers, protocols, pathology reports), CSV/TSV (datasets), Markdown,
  plain text, JSON, images (figures, scans via existing OCR).
- **Sources:** local files (phone `/sdcard/Concordance/`), agent `data/` dir, URL fetch
  (PubMed, arXiv, ClinicalTrials.gov), manual paste.
- **Processing:** extract text (existing `imaging_ocr.py` + PDF text extraction), chunk,
  store in `LocalCorpus` (phone) or `corpus.json` (agent).

### 2.2 Analysis
- **Entity extraction:** diseases, genes, drugs, biomarkers, endpoints, populations.
- **Claim extraction:** "paper X reports Y about Z" → claim ledger entries.
- **Gap identification:** what's missing, what's contradictory, what's underpowered.
- **Best-case experiment identification:** given the gaps, what experiment would most reduce
  uncertainty? Uses the decision model (specificity + novelty + feasibility + evidence).
- **Founded starting point:** a seed hypothesis + a ranked list of candidate experiments,
  each with a routing recommendation and a pre-registered decision rule.

### 2.3 Output
- A "research seed" document: `seed.md` with the question, extracted claims, identified gaps,
  proposed experiments, and routing. This seed feeds `/api/research` as the `context` parameter
  and seeds the claim ledger.

### 2.4 Implementation
- Agent: `app/ingestion.py` — `ingest_document(path)`, `analyze_corpus()`, `generate_seed()`.
- App: file picker → upload to agent → display seed → "Start autonomous research" button.
- Reuses: `LocalCorpus`, `claims.py`, `decision.py`, `literature.py`.

---

## 3. Benchmark / bounty system (50–100)

Builds on the existing sprint/challenge infrastructure (`sprints`, `sprint_submissions`,
`sprint_scores`, `challenges`, `leaderboard`). A **benchmark** is a sprint with a published
rubric and a bounty.

### 3.1 Seed set: the 50 bounties (from `deep-research-report.md`)

The file defines 50 bounties, each with: Objective, Deliverables, Difficulty, Datasets/Tools,
Acceptance Criteria, and 3–5 Milestones. These map directly onto the benchmark schema:

| File field | Benchmark schema |
|---|---|
| Objective | `goal` |
| Deliverables (code/data/protocol/report) | milestone artifact types |
| Difficulty | `difficulty` (Low/Medium/High) |
| Datasets/Tools | `data_sources` |
| Acceptance Criteria | `rubric` + acceptance criteria |
| Milestones (steps/time) | `milestones[]` |

Difficulty mapping: Low → beginner (10 credits), Medium → intermediate (25 credits), High →
advanced (50 credits). Expert tier (100 credits) reserved for open questions with no known path.

**Example (Bounty #1):** "Benchmark prognostic gene signatures in breast cancer" — Medium —
TCGA-BRCA + METABRIC — acceptance: ≥3 external validations, TRIPOD checklist, AUC ≥ 0.75 —
milestones: data wrangling (2w) → implement signatures (4w) → validate (3w) → document (1w).

The 15 research questions in the file are the **research agenda** (top-level); the 50 bounties
are the **executable benchmarks** under them.

### 3.2 Benchmark schema

```sql
alter table public.sprints add column if not exists
  is_benchmark boolean not null default false;
alter table public.sprints add column if not exists
  bounty_credits int not null default 0;
alter table public.sprints add column if not exists
  category text;  -- 'diagnosis','treatment','biomarker','mechanism','epidemiology','methods'
alter table public.sprints add column if not exists
  difficulty text check (difficulty in ('beginner','intermediate','advanced','expert'));
alter table public.sprints add column if not exists
  tags text[] default '{}';
alter table public.sprints add column if not exists
  research_question text;  -- links to the 15 research questions
alter table public.sprints add column if not exists
  acceptance_criteria text;  -- human-readable acceptance criteria
```

### 3.3 Milestone categories

| Category | Example benchmarks |
|---|---|
| **Diagnosis** | Bounty #16 (FDA oncology drug/biomarker registry), Bounty #45 (variant annotation) |
| **Treatment** | Bounty #1 (prognostic signatures), Bounty #3 (endocrine resistance), Bounty #5 (neoantigen pipeline) |
| **Biomarker** | Bounty #33 (IDH mutation from MRI), Bounty #27 (blood biomarker recurrence) |
| **Mechanism** | Bounty #6 (mutual exclusivity), Bounty #42 (metastatic pathways), Bounty #49 (protein stability) |
| **Epidemiology** | Bounty #44 (climate + lung cancer), Bounty #34 (DIPG meta-analysis) |
| **Methods** | Bounty #7 (TMB standardization), Bounty #8 (SV callers), Bounty #47 (FAIR checker), Bounty #24 (TRIPOD/PROBAST tutorial) |

### 3.4 Benchmark lifecycle
```
draft → open → running → judging → closed
```
- **draft:** organizer creates, sets rubric, milestones, bounty.
- **open:** published, community can join (solo or team).
- **running:** participants ingest docs, run autonomous research, submit capsules/papers.
- **judging:** deterministic judge scores submissions (reuses `sprint-judge.ts`).
- **closed:** winners announced, bounties paid, results published.

### 3.5 Bounty mechanism (credits)
- Bounties are **credits** (existing credits system in the web app).
- Scaled by: difficulty (beginner=10, intermediate=25, advanced=50, expert=100) × novelty
  (0–1) × milestone completion (0–1).
- Top 3 submissions per benchmark share the bounty (60/30/10 split).
- Bonus bounties for: first submission, reproducibility (provenance verified), community
  contribution (dataset/tool).

### 3.6 Submission kinds (reuse existing)
- `capsule` — a verified capsule (claim + evidence + provenance)
- `tool` — a new tool/skill the agent learned
- `dataset` — a dataset or corpus contribution
- `report` — a research report/paper

Each submission is scored against the rubric's milestones. The agent's autonomous run
produces a `report` submission with the paper chain + manifest.

### 3.7 Leaderboard
- Global + per-category leaderboards (existing `leaderboard` infra).
- Metrics: total credits, benchmarks completed, breakthroughs (claims that reached
  `supported` status), contributions (datasets/tools submitted).

---

## 4. Evaluation harness (the purpose)

The evaluation harness runs the autonomous research tool against the 50 bounties and scores its
output. This is how we measure whether the tool actually aids research progression.

### 4.1 Harness architecture
```
┌─────────────────────────────────────────────────────────────┐
│  Evaluation Harness (runs on CI or on-demand)                │
│                                                              │
│  For each benchmark:                                         │
│    1. Ingest the benchmark's seed documents into the agent   │
│    2. Run /api/research (autonomous loop)                    │
│    3. Collect: hypotheses, experiments, claims, paper chain  │
│    4. Score against the benchmark rubric (deterministic)     │
│    5. Record: score, time, neurons used, artifacts           │
│                                                              │
│  Output: scorecard per benchmark, aggregate metrics, trend    │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 What the harness measures

| Metric | What it captures | Source |
|---|---|---|
| **Gap identification** | Did the tool identify the real research gap? | Claim ledger: does a claim match the benchmark's expected gap? |
| **Hypothesis quality** | Are hypotheses specific, falsifiable, novel? | Decision model specificity + novelty scores |
| **Experiment feasibility** | Are proposed experiments realistic? | Feasibility score + human review |
| **Critic pass rate** | Does the tool catch its own overclaims? | Critic verdict distribution |
| **Novelty** | Does it propose something not in the literature? | Decision model novelty vs retrieved abstracts |
| **Convergence** | Does it stop appropriately? | Stop rules triggered correctly |
| **Efficiency** | Neurons/experiments per unit progress | Cost tracking |
| **Provenance** | Is the output reproducible? | Manifest verification |
| **Human usefulness** | Would a researcher find this valuable? | Feedback ratings |

### 4.3 Gold set = the 50 bounties
The 50 bounties from `deep-research-report.md` are the gold set. Each has acceptance criteria
that can be translated into a rubric. Start with 10 of them (the pilot in the Gantt chart:
bounties 1–10) to validate the harness, then expand to all 50.

### 4.4 Benchmark creation workflow
1. **Organizer creates a benchmark** from a bounty definition (the file provides 50; community
   can add more).
2. **Peer review** (decision 3): at least 2 reviewers approve the rubric, milestones, and
   acceptance criteria before it opens.
3. **Benchmark opens**; participants (humans or agents) submit.
4. **Deterministic judge** scores; human reviewers can override.
5. **Results published** to leaderboard.

---

## 5. Research ingestion and analysis system (the "start where you want" engine)

### 5.1 Ingestion pipeline
```
User drops files → /sdcard/Concordance/{Datasets,Papers,Research}
  or uploads via web app
  or pastes text / URL
    ↓
Text extraction (PDF text, OCR for images, CSV parse)
    ↓
Chunking (1000-char, 100 overlap) → LocalCorpus (phone) or corpus.json (agent)
    ↓
Entity extraction (diseases, genes, drugs, biomarkers, endpoints)
    ↓
Claim extraction ("paper X reports Y about Z")
    ↓
Gap identification (missing, contradictory, underpowered)
    ↓
Best-case experiment identification (decision model)
    ↓
Research seed document (seed.md)
    ↓
Feeds /api/research as context → autonomous loop
```

### 5.2 Agent endpoints
- `POST /api/ingest` — accept a file or URL, extract text, store in corpus.
- `POST /api/analyze` — analyze the current corpus, return entities, claims, gaps.
- `POST /api/seed` — generate a research seed from the analysis.
- `GET /api/corpus` — list indexed documents.
- `GET /api/corpus/search?q=...` — keyword search.

### 5.3 App integration
- File picker → upload to agent → "Analyzing..." → display seed → "Start autonomous research"
  button (calls `/api/research` with `context=seed`).
- Offline: ingest + analyze + seed all run on-device using the local model + LocalCorpus.

---

## 6. Open source & privacy model

### 6.1 What's open
- App (Kotlin/Android) — MIT or Apache 2.0
- Agent (Python/FastAPI) — MIT or Apache 2.0
- Web platform (Next.js) — MIT or Apache 2.0
- Evaluation harness + benchmark definitions — CC-BY 4.0 (data) + MIT (code)

### 6.2 Plugin/adapter interface
Users wire up their own systems via a simple adapter:

```python
class LabAdapter:
    def list_instruments(self) -> list[dict]: ...
    def submit_experiment(self, spec: dict) -> str: ...  # returns job id
    def get_result(self, job_id: str) -> dict | None: ...
```
Implementations: `HomeLabAdapter`, `LIMSAdapter`, `SequencerAdapter`, `PlateReaderAdapter`.
The app's `JobQueueManager` already has `WorkerRoutingPolicy` — add `LAB_ADAPTER` as a routing
target.

### 6.3 Privacy (decision 4)
- **Prompt on launch:** first launch asks what the user wants to keep private vs. contribute.
  The choice is **toggleable in settings** at any time.
- **Parse-level control:** the user can select individual claims/papers/runs to keep private
  and contribute only what they like (e.g., contribute a reproducible method but not a
  patient's pathology report).
- **Default:** all data stays on-device. No telemetry, no cloud calls unless the user opts in.
- **Opt-in community:** publish capsules/reports to the website for benchmarks/leaderboards.
- **Private research groups:** invite-only sprints (already supported by `visibility='invite'`).
- **Data export:** full export of all local data (claims, papers, runs) as a zip.

### 6.4 Peer review (decision 3)
- Every community-submitted benchmark goes to **peer review** before opening.
- At least 2 reviewers (with no conflict of interest) approve the rubric, milestones, and
  acceptance criteria.
- Reviewers are credited for the review itself (small bounty).

---

## 7. Implementation order

### Phase A: Research ingestion (agent + app) — 1 week
1. `app/ingestion.py` — text extraction, chunking, corpus storage.
2. `app/analyze.py` — entity + claim extraction, gap identification.
3. `app/seed.py` — research seed generation.
4. Endpoints: `/api/ingest`, `/api/analyze`, `/api/seed`, `/api/corpus`, `/api/corpus/search`.
5. App: file picker → upload → display seed → "Start research" button.
6. Offline: ingest + analyze + seed on-device.

### Phase B: Benchmark system (web) — 1 week
1. DB migration: add benchmark fields to `sprints` table.
2. `/api/v1/benchmarks` CRUD (create, list, get, update status).
3. Benchmark creation UI (organizer flow) + import from `deep-research-report.md`.
4. Benchmark browser (public listing with filters: category, difficulty, tags).
5. Submission flow (reuse sprint submit, add benchmark-specific rubric).
6. Deterministic judge (reuse `sprint-judge.ts`, add benchmark criteria).
7. Leaderboard (reuse existing, add benchmark metrics).
8. Peer review flow for community-submitted benchmarks.

### Phase C: Evaluation harness — 3–4 days
1. Harness script (Python): runs agent against each benchmark, collects results, scores.
2. Gold set: pilot 10 bounties (1–10) from the file.
3. CI integration: run on PR, track trends.
4. Dashboard: scorecard per benchmark, aggregate metrics, trend charts.

### Phase D: Open source + plugins — 1 week
1. License + CONTRIBUTING.md + issue templates.
2. Plugin/adapter interface (`LabAdapter`).
3. First adapter: `HomeLabAdapter` (file-based, simplest).
4. Documentation: "How to wire up your lab" guide.
5. Community benchmark submission flow.

### Phase E: Polish — 3–4 days
1. Privacy settings (prompt on launch, toggleable in settings, parse-level control, data export).
2. Onboarding flow for each persona.
3. Mobile-responsive web for benchmark browsing.

---

## 8. Benchmark expansion: 50 → 100

| Phase | Count | Source |
|---|---|---|
| Seed | 50 | `deep-research-report.md` (15 questions + 50 bounties) |
| Community | +30 | Community submissions via the web app (peer-reviewed) |
| Literature-derived | +20 | Auto-generated from PubMed/arXiv (agent extracts "open questions") |

Total: 100 benchmarks. Categories: diagnosis (15%), treatment (25%), biomarker (20%), mechanism
(15%), epidemiology (10%), methods (15%).

---

## 9. Finalized decisions (§10)

| Question | Decision |
|---|---|
| Licensing | MIT/Apache 2.0 code, CC-BY 4.0 benchmark data |
| Bounty funding | **Credits** (existing system) |
| Benchmark review | **Peer review** (≥2 reviewers before opening) |
| Privacy default | **Prompt on launch**, toggleable in settings, parse-level control of what to keep private vs. contribute |
| Gold set | **The 15 research questions + 50 bounties** in `deep-research-report.md` |

---

## 10. Open questions

1. **Licensing:** MIT for code, CC-BY 4.0 for benchmark data — confirmed.
2. **Bounty funding:** credits — confirmed.
3. **Benchmark review:** peer review — confirmed.
4. **Privacy default:** prompt on launch, toggleable in settings, parse what to keep private and contribute what you like — confirmed.
5. **Gold set:** `deep-research-report.md` (15 questions + 50 bounties) — confirmed.

All five answered. Phase A (research ingestion) starts immediately.
