# OSS Rescan, October 2026: Cost Cuts and Rewirings

**Date:** 2026-10-28
**Scope:** what changed since `OSS_EVAL.md` (2026-10-20) that can cut cost, remove a
component, or improve efficiency for a **phone + Cloudflare + Kaggle** system.
**Method:** vendor docs, pricing pages, and the tools' own release notes. Every claim
carries a source. Where sources disagree or a number is account-dependent, that is stated.

This document does **not** re-open the decision-model, INDRA, or free-LLM-tier calls in
`OSS_EVAL.md`. Those stand.

---

## 1. The five findings that change the plan

### 1.1 Chaquopy 17.1 - run the real Python engine on the phone (biggest win)

Chaquopy 17.1.0 shipped **2026-10-12**, is **MIT**, **free since 12.0.1** (sponsored by
Anaconda), bundles CPython **3.10-3.15**, and supports SciPy, NumPy, Pandas and
Pillow. It integrates as a Gradle plugin.

Why this matters more than anything else on this list: the repo currently has **two
implementations of the same statistics** - `src/concordance/*.py` and the Kotlin engine
in the app. They must agree or the product is lying. Chaquopy removes the fork:

- the phone runs `concordance.correction` directly, so a phone-minted capsule and a
  Kaggle-kernel capsule are computed by **the same code**;
- the Kotlin engine stops being the reference and becomes the fast path, tested against
  Python, not instead of it;
- no laptop agent, no container, no network.

Cost: APK size (one CPython + wheels per ABI; ship `arm64-v8a` only) and a known native-lib
collision risk - see §6.

Sources: `chaquo.com/chaquopy/news` (17.1.0), `chaquo.com/chaquopy/license`,
`github.com/chaquo/chaquopy` (MIT).

### 1.2 Kaggle exposes the quota API - the budget governor can be honest

`GET /api/v1/kernels/quota` and `kaggle quota` return `used`, `remaining`, `total` and
`refresh_time` for GPU and TPU. Published allowance is **~30 GPU hours/week** (sometimes
higher), **~20 TPU hours/week**, **12-hour** CPU/GPU sessions, ~30 GB RAM, 20 GB working
disk. Accelerators as of Sept 2026: `NvidiaTeslaT4` (2x T4, default), `NvidiaL4`,
`NvidiaTeslaA100`, `NvidiaH100`, `NvidiaRtxPro6000`, `TpuV5E8`, `TpuV6E8`.

Two operational facts that must be in code, not in a doc:

- **Maximum 2 concurrent batch GPU sessions.** A third push fails with
  `Maximum batch GPU session count of 2 reached.` **and the CLI still exits 0.**
  A naive "run 5 experiments" loop silently drops three.
- Quota is per **account**, shared across everything. Registering two Kaggle accounts
  (as `ecosystem-control` does) doubles the pool, which is legitimate but must be
  scheduled as two pools, not one.

Sources: `kaggle.com/page/GPU-tips-and-tricks`, `kaggle-cli/docs/kernels.md`,
`kaggle-cli/skills/references/quota.md`, `kaggle-sdk-python/.../kernels_api_service.py`.

### 1.3 Kaggle Datasets replace paid object storage for the heavy bytes

**200 GB per dataset, 200 GB private quota, free, versioned, mountable read-only into
kernels.** That is strictly better than R2 for anything a kernel needs to read:

| Today | After |
|---|---|
| `ecosystem-models`, 51.7 GiB in R2, 556% of the free tier, ~$0.68/mo | a versioned Kaggle Dataset (or HF Hub public), mounted at `/kaggle/input` - $0 and no download step |
| datasets fetched by URL at run time | `DatasetRef{ref, version, sha256}` in `ExperimentSpec`, satisfied by a mounted dataset |

R2 keeps what only R2 should hold: capsules, artifacts, replication packages - kilobytes
to a few megabytes each. That is comfortably inside 10 GB.

HF Hub is the alternative for public weights (public storage is free/best-effort, 100 GB
private on free accounts), but Kaggle is one fewer account and the mount is native.

Sources: `kaggle.com/docs/datasets`, `huggingface.co/docs/hub/storage-limits`.

### 1.4 Durable orchestration is already inside the $5

Cloudflare Workflows on Workers Paid includes **500,000 steps/month**, **1 GB-month
state**, **unlimited wall time per step**, and `step.sleep` does not consume a step. A
polling loop over a 40-minute Kaggle run costs a handful of steps.

This retires two things: the hand-rolled D1 cron in `ecosystem-control` and any
Temporal/Inngest dependency. It is the correct place for
`submit -> poll -> fetch -> adjudicate -> store`, with retries and timeouts as primitives.

Sources: `developers.cloudflare.com/workflows/reference/pricing`,
`developers.cloudflare.com/workflows/reference/limits`.

### 1.5 RO-Crate is the replication package format; stop inventing one

**RO-Crate 1.3** plus the **Workflow Run RO-Crate (WRROC)** profiles are the standard for
recording execution provenance (W3C PROV-compatible, accepted by Zenodo, supported by
WorkflowHub). They describe exactly what `replication.zip` needs: the workflow, the
inputs by hash, the environment, the outputs, and the run metadata.

Adopting it costs an afternoon and buys interoperability with the archival ecosystem -
the difference between "a zip we made" and "a crate a reviewer can open". Keep the app's
own hash chain; make the manifest a valid RO-Crate.

Sources: `researchobject.org/ro-crate/specification/1.3/workflows.html`,
`about.workflowhub.eu/Workflow-RO-Crate`, PLOS One 2024 WRROC paper.

---

## 2. Free capacity, ranked by usefulness to this system

| Lane | What you get | Cost | Use it for |
|---|---|---|---|
| **Kaggle** | ~30 GPU h/wk + ~20 TPU h/wk, 12 h sessions, 200 GB datasets | $0 | every experiment, class 1-6 |
| **Cloudflare Workflows** | 500k steps/mo, 1 GB state | included in $5 | durable run orchestration |
| **GitHub Actions (public repo)** | unlimited standard-runner minutes | $0 | `concordance verify` on every capsule and PR |
| **Modal Starter** | **$30/mo credits**, 100 containers, 10 GPU concurrency; academics up to $10k | $0 within credits | burst CPU when Kaggle quota is exhausted |
| **HF Jobs** | T4 `$0.40/h`, 16-vCPU CPU `$1.00/h`; Spaces CPU Basic free | pay-per-minute | a second opinion on a GPU result |
| **Workers AI** | 10,000 neurons/day (~300k/mo) | $0 under 10k/day | triage, OCR, plain-language |
| **OVHcloud AI Endpoints** | 2 RPM, no key, EU-hosted | $0 | zero-friction non-PHI text |

Modal is the one worth reconsidering despite the earlier "cut Modal" call: **$30/month of
CPU is roughly 100+ core-hours**, which is more CPU-bioinformatics than the Kaggle
weekly quota in practice. Keep it as a fallback lane with its own ledger, not as a
primary.

GitHub Actions on a **public** repo is the cheapest possible credibility: a reviewer
clicks a badge, the capsule re-verifies. That requires open-sourcing the verifier, which
is the plan anyway.

---

## 3. Rewirings that remove a component

| Remove | Replace with | Saving |
|---|---|---|
| `ecosystem-control` cron + health probes (17 services, 1 online) | Cloudflare Workflows for runs; **UptimeRobot free** (50 monitors) if you still want uptime alerts | one Worker, 1,348 runs of noise |
| Chroma / `chromadb` in the agent | **sqlite-vec 0.1.9** in the app's existing SQLite | a 1.5.9 dependency and a second store; exact scan ~40 ms at 100k vectors |
| `requirements.txt` pip install inside kernels | **pixi** (conda + PyPI + system libs, one `pixi.lock`) | deterministic, faster starts, no compiler on the kernel |
| custom `concordance.yaml` | **RO-Crate 1.3** manifest | interoperability, no bespoke parser |
| `concordance-web` (38.9 MB Worker) | nothing - the phone is the client | a deploy nobody reads |
| `/v1/debug/raw` on `concordance-ai` | delete | an unaudited surface in production |

`sqlite-vec` is pre-v1 and takes breaking changes; pin the version and keep embeddings
re-derivable. It is nonetheless the right call for <100k chunks on a phone, which is the
actual scale.

`pixi` over `uv`: uv is Python-only and faster; pixi locks **conda + PyPI + system
libraries**, which is what bioinformatics actually needs (compiled deps, BLAS, htslib).
Use pixi for the kernel image, uv for quick Python-only scripting.

---

## 4. The second phone app question

**Verdict: do not build one.** The reason is technical, not aesthetic.

- Android 15+ aggressively kills background terminal processes even with
  `termux-wake-lock` and battery optimisation disabled - an open Termux issue from
  June 2026. A spare phone cannot host a dependable worker.
- Chaquopy puts CPython **inside the existing app**, under a foreground service the app
  already knows how to run, with no second install and no IPC.
- The one legitimate second-app case - a minimal "Lab Node" that captures instrument
  output on an old phone - is a different product. It does not share the research
  runtime and it does not cut cost.

If a spare phone is ever used for compute, treat it as **best-effort telemetry**, never
as a scheduler dependency.

Sources: `github.com/termux/termux-app/issues/5150`, `chaquo.com/chaquopy`.

---

## 5. What this does to the monthly bill

| Item | Now | After |
|---|---|---|
| Cloudflare Workers Paid | $5.00 | $5.00 |
| R2 storage (51.7 GiB) | ~$0.68 | $0.00 (models -> Kaggle Dataset) |
| Kaggle | $0 | $0 |
| Modal | $0 (unused) | $0 (inside $30 credits) |
| GitHub Actions | $0 | $0 (public repo) |
| **Total** | **$5.68** | **$5.00** |

The rescope does not need to save money - it already costs $5.68. What it needs to save
is **components**. This list removes four (web app, fleet cron, Chroma, the laptop agent)
and adds one (Chaquopy), for a net simplification.

---

## 6. Risks introduced

| Risk | Severity | Mitigation |
|---|---|---|
| Chaquopy's `libpython` / `libc++_shared` collides with the existing ONNX Runtime and llama.cpp natives | **high** - this repo already fixed a duplicate `libonnxruntime.so` once | `packaging { jniLibs { pickFirsts += ... } }`, one ABI (`arm64-v8a`), and a device test that loads both Python and ONNX in one session |
| APK growth | medium | Python + `numpy`/`scipy` only; do not pip-install `scanpy` into the app |
| Kaggle 2-concurrent-GPU cap returns exit 0 | high (silent data loss) | parse the error text, never trust the exit code; queue in Workflows |
| Kaggle quota model is account- and demand-dependent | medium | read `/kernels/quota` before submit; display remaining hours in the UI |
| `sqlite-vec` pre-v1 breaking changes | low | pin the version, store the embedding model id with the vectors |
| Moving 51.7 GiB of models to a public Kaggle Dataset | medium | check licences first; private datasets are allowed (200 GB) and stay free |
| RO-Crate 1.3 tooling lags 1.2 | low | emit 1.2-compatible if a consumer rejects 1.3 |
| Assuming Chaquopy runs SciPy well on every phone | medium | benchmark `correction.py` on the target device; keep the Kotlin fast path as fallback |

---

## 7. Ranked actions

| # | Action | Effort | Impact |
|---|---|---|---|
| 1 | **Chaquopy lane**: run `concordance.correction` on the phone; test parity against capsule `5e8d66a42322fc40` | 3-5 d | High - deletes the dual implementation |
| 2 | **`kernels/push` + `kernels/quota`** in `kaggle.ts`, with the 2-session cap handled | 3-4 d | High - unblocks every experiment |
| 3 | Move model weights to a Kaggle Dataset / HF Hub | 0.5 d | Low cost, medium ops |
| 4 | Replace the fleet cron with Workflows; delete `concordance-web` and `/v1/debug/raw` | 1 d | Medium - four fewer surfaces |
| 5 | `pixi.lock` in the kernel image | 1 d | Medium - deterministic runs |
| 6 | RO-Crate manifest for `replication.zip` | 1 d | High for credibility, low effort |
| 7 | sqlite-vec for the local corpus | 1-2 d | Medium - one less store |
| 8 | `concordance verify` GitHub Action on a public repo | 0.5 d | High - free external proof |
| 9 | Modal as a budgeted fallback lane | 1 d | Low now, useful at quota exhaustion |

---

## 8. What I would not adopt

- **A second phone app.** See §4.
- **Termux as a compute node.** Killed by Android 15 background policy.
- **LanceDB / a vector service.** Wrong scale; sqlite-vec is enough under 100k chunks.
- **Temporal / Inngest.** Workflows already covers durable execution inside the $5.
- **Self-hosted Nextflow/nf-core control plane.** Useful for real sequencing data;
  overhead for a single-user adjudication engine. Revisit only if the product starts
  ingesting raw FASTQ.
- **HF ZeroGPU.** 3.5 minutes/day is a demo allowance, not a batch lane.
- **On-device LLM expansion.** The app already runs llama.cpp; nothing in this scan
  changes the cost of that, and the 10k neurons/day cloud lane is cheaper for burst.

---

## Sources

- Chaquopy: `chaquo.com/chaquopy/news`, `/license`, `github.com/chaquo/chaquopy`
- Kaggle: `kaggle.com/docs/datasets`, `kaggle.com/page/GPU-tips-and-tricks`,
  `github.com/Kaggle/kaggle-cli` (`docs/kernels.md`, `skills/references/quota.md`)
- Cloudflare: `developers.cloudflare.com/workflows/reference/{pricing,limits}`,
  `developers.cloudflare.com/workers/platform/pricing`
- Modal: `modal.com/pricing`, `modal.com/academics`
- Hugging Face: `huggingface.co/docs/hub/{jobs-pricing,storage-limits}`, `hf.co/pricing`
- GitHub Actions: `docs.github.com/billing/concepts/product-billing/github-actions`
- sqlite-vec: `github.com/asg017/sqlite-vec`; benchmarks from `finngarden.com` (2026-09-25,
  sqlite-vec 0.1.9) and `llms.blog` (2026-08-21)
- pixi / uv: `pixi.prefix.dev/latest/`, `docs.astral.sh/uv`
- RO-Crate: `researchobject.org/ro-crate/specification/1.3/workflows.html`,
  `about.workflowhub.eu/Workflow-RO-Crate`
- Termux: `github.com/termux/termux-app/issues/5150`
