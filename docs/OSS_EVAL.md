# OSS / external evaluation — decision models, INDRA, free LLM APIs, and tooling

Reviewed **2026-10-20**. Scope: what is worth adopting into the **Concordance app**
(Android) or the **website/agent** (`oncology-agent`), and what is not. Every claim
below is sourced; where a name in the brief was ambiguous or does not exist as
described, that is stated plainly rather than guessed at.

Standing constraints this document respects:

- Research tool, **not** clinical decision support.
- Deterministic engine stays authoritative; models only draft, summarise, rank or gate.
- No auto-runs — anything a model proposes lands in `proposed` for human approval.
- PHI never leaves the device/host unless the operator explicitly routes it.
- Clinical data stays local (SQLite); Supabase holds research metadata only.

---

## A. Decision models — Jev and the open field

### A.1 What Jev actually is

TypeSafe AI shipped **Jev** on 2026-09-15 as the first "System One" model: you send
program *state* plus **typed questions** and get **typed decisions with
probabilities**, never generated text.

| Primitive | Asks | Returns |
|---|---|---|
| `Choice` | which one of ≤255 options | an option + per-option probabilities + confidence |
| `Score` | where on an ordered rubric | distribution + most-likely level + mean level |
| `Noul` | is this true? | a single 0–1 probability (no confidence field) |

**Jev is proprietary and not self-hostable.** The API contract is public; the model
and its training data are not. This matters because our own
`oncology-agent/app/jev.py` + `app/jev_server.py` already speak this contract —
`jev_server.py` is *our* deterministic implementation of it, not Jev.

### A.2 The open reproductions

The community reproduced the *contract* within ~24h. What follows is the practical
shortlist, none of which is a drop-in for Jev's accuracy.

| Project | Shape | License | Notes |
|---|---|---|---|
| **Laya** (Convai) | 421M ModernBERT-large + decision head; 322M multilingual | Apache-2.0 | `pip install laya`; ~66 ms for 3 questions on a laptop CPU. Base checkpoints are near chance zero-shot (0.36/0.34 vs 0.32 random, 0.46 majority) — a *base to fine-tune*, not a drop-in. Weak on large option sets. |
| **Von 1.1** | 395M ModernBERT-large, each option scored at its own marker | Apache-2.0 | **Best open zero-shot** on the independent 49-task benchmark (0.704). Collapses to one mode on unfamiliar domains. |
| **Kev** (Jared Palmer) | rank-16 LoRA + pointer head on Qwen3.5 (0.8B/4B/9B) | Apache-2.0 | Serves TypeSafe's `/v1/systemone` exactly; official SDK works by changing a base URL. Most careful published evals, including where it loses (confident-error 4.0% vs Jev 3.7%). |
| **SemIf** (was OpenJev) | no new weights — reads option logits off a frozen Qwen3.5-4B | MIT | 1.02 s vs 5.33 s for the same 21 decisions via generated JSON. Needs a GPU (has CPU/MLX/WebGPU paths). |
| **NanoJev** | Qwen3-0.6B + decision heads, 4 game environments | MIT | Beats Jev 128/128 to 56/128 on ViZDoom Basic. Control loops only; not a general classifier. |
| **jevlike** | a *trainer*, not a model | MIT | Bring labelled options, train an option-attention head on a laptop. The path to owning our own decisions. |
| **winnow:e4b / pplx-decider:flash / jeb:27b / clef:flash** | 4B–27B decision fine-tunes | mixed | The accuracy leaders in Ollaya's own bench (chance-corrected 73.7 for `pplx-decider:flash`). `winnow:e4b` is the recommended all-rounder. |
| **Ollaya** | Rust daemon: pulls and serves the above behind `/v1/systemone`, `/v1/decisions`, `/v1/models` | Apache-2.0 | "Ollama for decision models". Windows installer exists. Wire-identical, so existing Jev clients switch with one env var. |
| **nimble** (Bespoke Labs) | LoRA on Qwen3.5-9B, up to 255 options | Apache-2.0 (+ non-commercial v3) | The default `JEV_MODEL` name in our own client. |

### A.3 The honest benchmark picture

| System | Macro accuracy, independent 49-task bench (869 cases) |
|---|---|
| TypeSafe Jev (hosted) | **0.966** |
| Von (395M, open) | 0.704 |
| GLiNER2 (~300M, open) | 0.698 |
| Laya (421M, open) | 0.583 |

A **26-point out-of-domain gap**. Open models win on latency, price and control; they
lose on zero-shot generalisation. In-domain, after fine-tuning on our own labels,
they are competitive. Treat every vendor's self-benchmark (Jev's included) as a
hypothesis, not a result.

### A.4 What we have and how to improve it

**Today:** `app/jev_server.py` is a zero-dependency stdlib server that answers our
five real tasks (`mode`, `propose`, `triage`, `novelty`, `priority`) with
deterministic keyword/rule policies mirroring the fallbacks, plus probabilities,
confidence and explicit abstention. It scores 7/8 on our benchmark suite; the miss
(`expert_override`) is genuinely outside the criteria a System One model may answer
inside. It is **auditable line-by-line, offline, free, and cannot regress below the
fallback it replaces** — but it does not generalise beyond its rules.

**Improvement plan (in order, each step independently useful):**

1. **Two-backend server, same contract.** Keep the deterministic policies as the
   always-on backend and *guardrail*; add an optional Ollaya/Laya/Von backend that
   is used only when the daemon is reachable and its answer clears a confidence
   threshold. Below threshold → deterministic answer + `abstained: true`. This gives
   us generalisation without giving up determinism.
2. **Grow the task registry.** Today: 5 tasks. The learning/experiment systems need
   more: contradiction detection between two claims, evidence-tier plausibility,
   correction-request triage, discovery dedup, conflict-resolution routing
   (Phase 3 §3.5 wants conflicts routed into the learning engine).
3. **Calibrate before trusting.** Extend `app/benchmarks.py` from accuracy to
   **calibration** (per-answer confidence vs observed correctness, plus a
   "share of decisions automatable at a 5% error budget" figure — the number Kev
   publishes against Jev). Never promote a backend on accuracy alone.
4. **Close the loop to a fine-tune.** The learning audit + negative-case store
   already accumulate real human disagreements. Those are labelled options. A
   `jevlike`-style head or a LoRA on Qwen3.5-0.8B trained on *our* decisions is the
   natural end state — the plans' "self-learning becomes a decision model".
5. **One interface, many backends.** Ollama 0.40 now also serves nine decision
   models through the same wire format. Our server can therefore front
   `Ollaya | Ollama | deterministic` behind one env var, and callers never change.
6. **Cloudflare is already wired.** `oncology-agent/app/cloudflare_ai.py` exists, and
   Cloudflare both maintains a `typesafe/jev` Workers AI integration and publishes
   its own decision model (`clef`). A hosted decision path is one env var away —
   for **non-PHI** states only.

### A.5 Fit: app vs website

| Surface | Verdict |
|---|---|
| **Website/agent** | **Strong.** Install Ollaya on the agent host (`irm https://ollaya.dev/install.ps1 \| iex`), point `JEV_BASE_URL` at it, keep `jev_server.py` as fallback. Zero code changes to callers; immediate generalisation on triage/gating/ranking. |
| **App (on-device)** | **Viable, not yet clean.** The app already ships ONNX Runtime *inside* `libs/sherpa-onnx-1.13.8.aar` (`libonnxruntime.so` is on the device) — that is exactly the runtime Laya/Von need. The blocker is Java bindings: the sherpa AAR does not expose the ORT API, and adding `onnxruntime-android` reintroduces the duplicate-`libonnxruntime.so` packaging defect we already fixed once (would need `packaging { jniLibs { pickFirsts } }` + an exclusion). Cheapest on-device path is the **122M `decima:small`** or a `laya:en` fp16 export; the pragmatic path is the phone calling Ollaya over LAN, which matches the existing agent-URL pattern. |
| **Client code** | **Adopt kojev's philosophy, not necessarily the dep.** `ItisNoMatter/kojev` (MIT, KMP) returns decisions as *our own Kotlin enums* with no string lookups and no built-in thresholds. That is the correct shape for the app: `AdjudicationEngine` becomes an enum of engines, thresholds live where the decision is made. Our `AnalysisEngine` enum is already heading this way. |

---

## B. INDRA

### B.1 What it is

**INDRA** (Integrated Network and Dynamical Reasoning Assembler) is an automated
model-assembly system: it takes mechanistic/causal statements from **30+ sources**
(text-mining reading systems *and* curated databases like BioGRID, SIGNOR, Pathway
Commons), normalises entities to standard namespaces (HGNC, UniProt, ChEBI, FPLX),
removes redundancy, and **estimates technical reliability** (belief scores).

- **CoGEx** (Context Graph Extension) is the assembled knowledge graph: causal
  relations + ontological relations + properties + data.
- **discovery.indra.bio** is the portal; `indra.sources.indra_db_rest` gives
  `get_statements()` over a REST service; `pip install indra` gives the Python API.
- **INDRA World** is the general-domain causal-reading variant.

### B.2 One correction, stated plainly

**There is no "INDRA OCR."** INDRA's "reading" is *text mining* (NLP over text), not
optical character recognition. OCR in our stack stays **PaddleOCR**, already wired in
`oncology-agent/app/imaging_ocr.py` (2.x/3.x API, oneDNN disabled by default, results
tagged `derived` and gated behind `human_verified`). INDRA would consume the *text
that OCR produces* — the two compose, they do not compete.

### B.3 Fit

| Surface | Verdict |
|---|---|
| **Agent/website** | **Strong.** This is the missing evidence-assembly layer. |
| **App** | **No** — Python, database-backed, server-scale. The app should *display* INDRA results fetched through the agent, never run it. |
| **PHI** | INDRA is public knowledge only. Statements about a **real patient's** variants must never be sent to it — that is the local/agent boundary again, and it should be an explicit check in the routing policy. |

### B.4 Concrete integration seams

1. **Prior-knowledge panel.** For an anchor (gene/disease/drug claim), query INDRA
   statements between the entities and render belief + source provenance beside the
   adjudication result. This is exactly the "research metadata" Supabase is allowed
   to hold, and it makes the app's verdict explainable.
2. **Contradiction detection.** INDRA statements that *oppose* a proposed claim are
   the highest-value signal we can add to the learning loop — they are precisely the
   "human challenge with two candidate states" that Phase 3 §3.5 wants to feed
   `TrainingSetBuilder`.
3. **Reader → proposal.** INDRA's reading systems can pre-fill *proposed* claims from
   a paper, pairing with the structured extraction we just built on the local LLM.
   Keep the human-approval gate: reader output is `derived`, never `verified`.
4. **Bootstrap via REST, not a local DB.** Start with `indra_db_rest` / the CoGEx API.
   A full local INDRA database (Postgres + services) is a real infrastructure
   commitment and should only follow proven use.

---

## C. Free LLM APIs

Permanent free tiers, OpenAI-SDK-compatible unless noted. Current as of review date.

| Provider | Free allowance | Notable models | Fit |
|---|---|---|---|
| **Groq** | 30 RPM, 1,000 RPD | `gpt-oss-120b`, `qwen3.8-27b` | Fastest free inference — best for drafting |
| **Google Gemini** | no published per-model limits; ~15 RPM / 1,500 RPD historically | Gemini 3.5/3.8 Flash, 1M ctx | Long-context paper work; free prompts may train Google unless EEA/UK/CH |
| **OpenRouter** | 20 RPM, **50 RPD per `:free` model** | 17 `:free` models, incl. gemma-4-26b/31b, nemotron-3 | One key, many models; $10 deposit → 1,000 RPD |
| **Cloudflare Workers AI** | 10,000 Neurons/day (shared across all usage) | 60+ models; **maintains `typesafe/jev`** | **Already integrated** in our agent (`cloudflare_ai.py`) |
| **NVIDIA NIM** | 40 RPM, 10,000 RPD | 100+ models incl. nemotron-3-ultra | Generous daily quota |
| **Mistral** | ~$10/month credits, free mode on | Mistral Small/Medium, Ministral 3 3B/8B/14B | Ties to Timestral (§E) |
| **Ollama Cloud** | session/weekly limits (unpublished) | gpt-oss, deepseek-v4, kimi-k3 | Also serves decision models via the same API |
| **OVHcloud AI Endpoints** | **2 RPM anonymous, no key, no signup**, EU-hosted | Qwen3.5-397B, gpt-oss-120b/20b | Best no-friction option; EU data centres |
| **Cohere** | 1,000 calls/month, **non-commercial only** | Command A+, Aya Expanse | Non-commercial licence is a hard blocker for the website |
| Others | Z.AI GLM-4.7-Flash (permanent free), LLM7.io, SiliconFlow (real-name verification), Kilo Code (no key, 200 req/hr) | — | Niche |

### Fit and guardrails

- **App:** replace the single dead "Gemini 3.5 (Cloud — not configured)" chip with a
  **provider list** — Groq (fast drafting), Gemini Flash-Lite (long context),
  Cloudflare (already server-side). Keep the chip disabled and honestly labelled
  until a key is present, exactly as it is today.
- **Website/agent:** `model_router.py` already has a `LOCAL_MODELS` set and
  `route(task)`. Add provider classes with an explicit **data-class policy**:
  free tiers *train on your prompts* — PHI and patient-level states must be
  hard-blocked from every free tier. This extends the existing "PHI cannot be routed
  to `aws_public`" rule rather than inventing a new one.
- **Do not** silently fall back from a local model to a free cloud tier. A fallback
  that changes who can see the data must be a visible, operator-visible event.

---

## D. trim-1 (Caveman)

A **1.2 MB model** that decides *which lines of a context an agent needs for the
current ask and drops the rest*. Shipped as an agent-facing component.

- **Fit: agent/website — good.** Our agent accumulates large tool output (OCR text,
  paper text, INDRA statements) and the app sends OCR text into prompts. trim-1 is a
  *pruner*, not a decision-maker, so it does not touch the authoritative-engine rule.
- **Measurable:** tokens saved vs answer preserved. Adopt only with that measurement
  in place, or it is just silent information loss.
- **Do not** apply it to adjudication inputs that the deterministic engine consumes —
  only to model-facing context.

---

## E. Timestral (Mistral)

Announced 2026-10-15: a family of **four time-series foundation models — 5M, 28M,
123M, 209M parameters** — probabilistic and multivariate, supporting past-only and
known-future covariates, runnable on CPU, modified MIT licence.

- **Fit: website — strong.** The Phase 3 **trends engine** is already specified to
  show which claims flip and which fields diverge. Timestral forecasts that drift
  instead of only describing it: support-count trajectories, capsule/handling
  volumes, lane activity, learning-round metrics.
- **Fit: app — small but real.** 5M/28M variants are small enough to run on-device
  for sparkline forecasting of a claim's support over time, using the same
  model-download UX as the local LLM.
- **Not** applicable to text, extraction, or decisions.

---

## F. Matt Pocock skills

Open-source agent skills (`github.com/mattpocock/skills`): **interviews → specs →
tickets → implementation**, composable, model-agnostic, installable per project.

- **Fit: developer workflow only. Zero runtime product value.**
- The real win here is process: this repo carries very large plan documents
  (`ECOSYSTEM_PLAN`, `PHASE3_PLAN`, `DEEP_RESEARCH_SYNTHESIS`). The Pocock workflow is
  the missing step that turns those plans into *tickets* a set of agents can
  implement in parallel without re-deriving intent.
- Recommend a **pilot on the next phase**, not a retrofit of finished work.

---

## G. context-mode

An open-source MCP/plugin layer (`mksglu/context-mode`) that keeps large tool output
out of the main model context by running tool calls in isolated subprocesses so only
stdout enters context — reported up to **98% context reduction**. It acts at three
points per request: before send, before a tool runs, before a result returns.

- **Fit: developer tooling — good, low effort.** Adopt for the local dev loop if the
  goal is cheaper agent sessions.
- **Fit: agent architecture — conceptually.** We already have `mcp_server.py`; the
  *pattern* (never let raw tool output into the decision context) is the same idea
  as trim-1 and should be the default for our own MCP tools.
- No PHI/data-governance value: it is about volume, not confidentiality.

---

## H. HyperFrames (HeyGen)

Open-source framework turning HTML/CSS/media/seekable animations into **deterministic
MP4** renders, with first-party agent skills (`npx skills add heygen-com/hyperframes`).

- **Fit: website — nice-to-have.** Auto-generated explainer videos ("how this evidence
  grade was computed", method walk-throughs, launch clips) authored by an agent and
  rendered from code, so they stay in sync with the product.
- **Not** a product feature; there is no runtime role in the app or agent. Rank it
  below everything above.

---

## I. Ranked verdict

| Rank | Item | Surface | Effort | Impact |
|---|---|---|---|---|
| 1 | Ollaya as a second backend behind our existing `/v1/systemone` contract | agent | Low | High — generalisation with a deterministic guardrail |
| 2 | INDRA (REST first) for prior-knowledge + contradiction detection | agent → app | Medium | High — the missing evidence-assembly layer |
| 3 | Free-LLM provider registry with a data-class policy | agent + app | Medium | High — removes the "not configured" dead end, safely |
| 4 | Calibration + task registry for decision models | agent | Low | Medium — makes every later claim testable |
| 5 | Timestral in the trends engine | website (later app) | Medium | Medium — forecast instead of describe |
| 6 | trim-1 / context-mode pattern | agent + dev loop | Low | Medium — cost, not correctness |
| 7 | Matt Pocock skills | dev process | Low | Medium — plans → tickets |
| 8 | HyperFrames | website media | Low | Low |

**First three moves I would make:** (1) Ollaya backend behind `jev_server.py` with
the deterministic policies as guardrail; (2) a thin INDRA REST client for
`get_statements()` with a prior-knowledge/contradiction endpoint on the agent; (3) a
provider registry with the PHI block, wired to the app's engine chips.

**What actually shipped** is the phone-side counterpart: the typed decision contract with
rules as guardrail, INDRA called directly from the app, and the free-tier provider list
kept out of the analysis path. The Ollaya daemon could not pull models on this network,
and the app does not depend on it.

## Implemented on the phone (2026-10-20)

Everything below ships in the Android app. Nothing here needs the laptop, a daemon, or a
cloud model.

### Decision model: typed answers, deterministic guardrail

- `local/DecisionModel.kt` asks closed `CHOICE` / `NOUL` / `SCORE` questions. The rules
  backend always answers; the on-device llama.cpp server is consulted only when the
  operator has started it and selected the LLM engine.
- Reasons are composed by the app, not the model: the model may only pick an allowed key
  (`counts|summary|abstract_only`, `accept|review`) and return numbers in `0..1`.
- Every reply is validated against the criteria. A missing field, an unknown key, an
  out-of-range number, a timeout, or a confidence below `0.6` discards the whole answer
  and falls back to rules with `fallback_reason` (`llm_unusable`, `low_confidence`).
- The result is shown on the analysis card as `REVIEW: AUTO-ACCEPT / HUMAN CHECK`, the
  backend that actually answered, and one sentence explaining the gate. The gate routes
  review; it never edits the extracted numbers.

### INDRA: direct from the phone

- `integration/knowledge/IndraClient.kt` calls `https://db.indra.bio` directly with
  OkHttp. Public knowledge only: only short entity tokens are accepted, so free text or
  patient material is refused before any request is made.
- Measured latency from this network: 13-84 s per filtered query (TTFB). The client waits
  up to 150 s, caches results for 15 minutes, and reports an unavailable database as a
  result instead of an error.
- The card in Integrations -> Science shows statement count, type breakdown, top
  statements with belief + PMID, and a `SOURCES DISAGREE` banner when opposing types
  (e.g. Activation vs Inhibition) both clear belief 0.5. It never resolves the conflict.

### Free LLM providers

- Keys and curated free model IDs live in BYOK (`FreeTierModels`, OpenRouter/Gemini).
- They are **not** used for paper or claim analysis. The analysis UI says so explicitly,
  and the dead "Gemini 3.5 (Cloud - not configured)" chip is gone. Two engines remain,
  both on the phone.

## Caveats

- The open decision-model field is **weeks old**; stars measure attention, not
  accuracy, and a figure can go stale in a day.
- Schema-valid output is not a correct decision. Thresholds stay in our code,
  high-impact actions keep their deterministic checks, and every model-proposed item
  stays in `proposed` until a human approves it.
- INDRA adds *public* evidence only; nothing patient-specific may be sent to it.
- Free cloud tiers train on prompts. PHI is local-only, permanently.
