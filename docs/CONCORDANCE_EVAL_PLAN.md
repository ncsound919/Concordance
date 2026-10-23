# Concordance Evaluation Harness & Open Source Plan

**Status:** Phase C + D implementation.

---

## Phase C: Evaluation harness

### C.1 Benchmark definitions (gold set — 10 bounties)

10 benchmarks derived from `deep-research-report.md` bounties 1–10, formatted as
JSON benchmark definitions with rubrics.

### C.2 Harness script

`eval_harness.py` — runs the agent against each benchmark, collects results, scores.

### C.3 CI integration

Run on PR, track trends, output scorecard.

---

## Phase D: Open source + plugins

### D.1 License + community files

MIT for code, CC-BY 4.0 for benchmark data.

### D.2 Plugin/adapter interface

`LabAdapter` base class + `HomeLabAdapter` (file-based).

### D.3 Documentation

"How to wire up your lab" guide.

---
