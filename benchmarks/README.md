# Kaggle Benchmarks — measured, not asserted

These are real benchmark tasks run on Kaggle's Model Proxy (AI inference), not prompts pasted into
a chat. Each one produces a versioned run, a leaderboard entry, and a downloadable run file.

Kaggle's AI quota is separate from notebook GPU/TPU time: **$10/day, capped at $100/month**, across
8 models (Claude Sonnet 5, Gemini 3 Flash / 3.8 Flash, gpt-5.4-nano, DeepSeek R1, Qwen3-Next,
granite, gpt-oss-120b).

Why this matters to Concordance: the app's design says a model must never own the arithmetic or the
verdict. These benchmarks are how that claim gets tested instead of assumed.

---

## `concordance-odds-ratio-arithmetic`

**Question.** Can a model compute a 2×2 odds ratio exactly — the same arithmetic the engine does
deterministically?

**Method.** Six synthetic 2×2 tables. The model is asked for the odds ratio to two decimals. Gold
values are computed independently in `verify_gold.py`; no model output was used to produce them.
Scoring: |answer − gold| ≤ 0.02.

**Result — 2026-10-29, task version 1** (raw answers in `results/2026-10-29-odds-ratio.json`):

| Model | Passed | Answers (or-01 … or-06) |
|---|---|---|
| `anthropic/claude-sonnet-5@default` | **6/6** | 2.43, 7.00, 0.35, 1.00, 3.00, 3.58 |
| `google/gemini-3-flash-preview` | **6/6** | 2.43, 7.00, 0.35, 1.00, 3.00, 3.58 |
| `google/gemini-3.8-flash` | **6/6** | 2.43, 7.00, 0.35, 1.00, 3.00, 3.58 |
| `openai/gpt-5.4-nano-2026-03-17` | **1/6** | 4.00, 2.80, 1.14, 1.00, 2.25, 9.00 |

**Cost.** $0.03 for four models × six cases. At that rate the monthly $100 is roughly 80,000 calls.

**What it shows.** The three larger models are exact. The smallest model gets the identity case and
nothing else — 9.00 where the answer is 3.58, 4.00 where it is 2.43. Arithmetic correctness is a
function of *which model you happen to call*, which is precisely why the engine owns the arithmetic
and the model only drafts prose.

**What it does not show.** Six tables is a smoke-level sample, not a calibrated benchmark. It says
nothing about clinical validity, and it is not a scientific claim. It is a measurement of one
capability under one prompt.

---

## Reproduce

```bash
# 1. Mint a 2-hour Model Proxy key. Write it OUTSIDE the repo.
kaggle benchmarks auth -y --env-file "$TMPDIR/concordance-proxy.env"

# 2. Push the task (the slug must match the decorator name).
kaggle b t push concordance-odds-ratio-arithmetic -f benchmarks/concordance_odds_ratio_arithmetic.py

# 3. Run it against specific models.
kaggle b t run concordance-odds-ratio-arithmetic \
  -m google/gemini-3-flash-preview \
  -m openai/gpt-5.4-nano-2026-03-17 --wait

# 4. Inspect and download the run files.
kaggle b t status concordance-odds-ratio-arithmetic
kaggle b t download concordance-odds-ratio-arithmetic -o ./runs --force

# 5. Verify the gold values yourself.
python benchmarks/verify_gold.py
```

CLI notes learned the hard way:

- `benchmarks auth|init` write `.env` / example files into the **current directory** unless given
  absolute paths. Never run them from the repo root.
- Generated metadata must be **UTF-8 without BOM**; the CLI rejects a BOM.
- On Windows, set `PYTHONIOENCODING=utf-8` or `tasks status` fails rendering its table.
- `tasks download` takes `-o`, not `-p`.
- The run JSON maps `tuple[int, int]` to `numericResult.value = passed` and
  `confidenceInterval = total`. Read the conversations if you want the actual answers.

## `concordance-fixture-run` (engineering fixture, not a benchmark)

This is **not** a scientific claim. It runs the paired analysis on the bundled synthetic cohort
and exists to prove the pipeline end to end:

- the dataset pin reproduces on a different machine (the regenerated CSV hashes to
  `8ae88eb84cbaf61d1f407e4e4a6ad27b5cc599e5d6ec878565426e6b4422d4e2`),
- a real 2×2 analysis runs on Kaggle (OR 10.40, Fisher p 5.97e-14, CI 5.54–19.54),
- the artifact is downloadable and re-verifiable (Python 3.13.15, NumPy 2.1.3, SciPy 1.16.3).

The verdict is `supported` by the arithmetic. The adjudicator on the phone refuses to mint a
claim from it anyway (`SYNTHETIC_DATA`), because a fixture proves the plumbing, not a
finding. That refusal is the behaviour the whole design is built around.

Measured run 2026-10-29, account `tap919`, CPU only, ~30 s. Source: `concordance-fixture-run.py`,
result: `results/2026-10-29-fixture-run.json`.

## Guardrails for any benchmark added here

1. **Synthetic or fully public inputs only.** No patient data, no PHI, ever.
2. **Gold values must be computed by code that is committed** (`verify_gold.py`), not by a model.
3. **A benchmark result is not a claim.** It measures a capability; it does not adjudicate a study.
4. **Record the model slug and task version** with every result, and re-run before comparing across
   time — the proxy updates models underneath the same family name.
5. **Score what can be verified.** Free-text "looks right" scoring needs the judge model, which
   introduces a second unverified model into the loop; prefer exact or bounded numeric checks.
