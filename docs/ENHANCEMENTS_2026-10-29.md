# Enhancements: Jupyter-like Notes, Progressive Hypothesis Engine, Deep Research Deterministic System

**Date:** 2026-10-29
**Status:** design. No code yet.

---

## 1. Autonomous Jupyter-like Notes for Agent Docs

### 1.1 What it is

A notebook surface (Compose screen + Room entity) where each research round's artifacts are
cells with typed outputs, not a single `paper.md`:

| Cell type | Content | Source |
|---|---|---|
| `IDEA` | the research question | voice/text transcript |
| `HYPOTHESIS` | candidate hypotheses + scores | decision model |
| `LITERATURE` | triaged papers + entities | Europe PMC / OpenAlex / INDRA |
| `EXPERIMENT` | spec + run status + verdict | ExperimentSpec + RunRecord |
| `ANALYSIS` | adjudication + correction numbers | PairedAnalyzer |
| `NOTE` | free-form human annotation | user |
| `OUTPUT` | capsule hash + provenance | Capsule |

Each cell is append-only and hash-chained (reuse the `RunRecord` ledger pattern). A cell
can be re-executed (re-run the analysis) and produces a new cell version, never overwriting.

### 1.2 Where it lives

- `data/NotebookEntity.kt` (Room v9) — `notebookId`, `title`, `createdAtUtc`, `cellsJson`, `hash`
- `ui/screens/NotebookScreen.kt` — cell list with expand/collapse, run buttons, markdown render
- `research/notebook/NotebookCell.kt` — sealed class of cell types with typed payloads
- `research/notebook/NotebookExporter.kt` — renders a notebook to a directory of md files

### 1.3 Why it matters

The paper chain (`idea.md` → `hypothesis.md` → ... → `paper.md`) already produces files, but
they are flat text. A notebook preserves which step produced which number, making the
adjudication auditable in a way a single markdown file cannot. It also enables
"re-run from here" without redoing the whole round.

---

## 2. Progressive Hypothesis Engine (multi-domain)

### 2.1 What it is

The current `HypothesisComposer` proposes once. The progressive version runs in rounds:

```
round 0: seed hypotheses from claim graph gaps
round N: for each hypothesis:
  - gather evidence from all domains (genomics, imaging, literature, wetlab, trials)
  - score: information gain × feasibility / cost
  - if supported: promote to experiment (ExperimentSpec)
  - if contradicted: record negative case
  - if ambiguous: fork into sub-hypotheses
round N+1: incorporate results, re-score, propose next
```

Backpropagation: a run result updates the edge scores in the knowledge graph, which re-ranks
all pending hypotheses. This is the "Hypothesis-Tree Refinement" pattern from the operative
outline, made concrete.

### 2.2 Multi-domain analysis

Each hypothesis carries a `domainWeights: Map<Domain, Double>` where Domain is
`GENOMICS`, `IMAGING`, `LITERATURE`, `WETLAB`, `TRIALS`, `OMICS`. The evidence for each
domain comes from a different connector:

| Domain | Connector | Evidence type |
|---|---|---|
| Genomics | `CohortConnector` + cBioPortal | omics_feature, mutations |
| Imaging | `ImagingDatasetManager` | radiomic features, MRI |
| Literature | `LiteratureConnector` + INDRA | papers, entities, kg_edges |
| Wet lab | `WetLabConnector` | wetlab_measurements |
| Trials | ClinicalTrialsGovAdapter | trial endpoints, phases |
| Omics | GDCAdapter | expression, CNA |

The composer's rank formula becomes:

```
rank = 0.40 * mean(edge score)
     + 0.20 * domain coverage (how many domains support it)
     + 0.20 * evidence independence (distinct source types)
     + 0.10 * min(1, evidence count / 5)
     + 0.10 * feasibility inverse cost
```

Domain coverage rewards hypotheses that are corroborated across modalities; a single-domain
hypothesis needs a much stronger effect to rank equally.

### 2.3 Where it lives

- `research/hypothesis/ProgressiveHypothesisEngine.kt` — round orchestrator
- `research/hypothesis/DomainEvidence.kt` — sealed class of domain inputs
- `research/hypothesis/HypothesisTree.kt` — fork/merge/prune with backpropagation
- `research/hypothesis/HypothesisNode.kt` — node with parent, children, status
- Extends `HypothesisComposer` (single-shot) with a `progressive()` mode

### 2.4 Anti-collapse guards

- No hypothesis class repeated in consecutive rounds (diversified search)
- At most 1 moonshot per 10 rounds
- A pruned branch is not re-proposed unless its data or regime changed
- Stop a branch after 3 consecutive nulls → negative case

---

## 3. Deep Research Deterministic System (free search engines → md files)

### 3.1 What it is

The current `DeepResearchSystem.run` grades in-memory papers and produces a report. It does not
write files and it does not search. The deterministic system:

1. **Searches** free engines in parallel: Europe PMC, OpenAlex, Semantic Scholar, bioRxiv,
   PubMed, arXiv (all free, no key for most).
2. **Extracts** structured claims: disease, gene, drug, endpoint, population, effect, p-value,
   CI, sample size.
3. **Deduplicates** by DOI/PMID/title hash.
4. **Grades** each paper with the 10-dimension rubric (existing `ResearchGrader`).
5. **Synthesizes** insights (trends, gaps, contradictions, breakthroughs).
6. **Proposes** next experiments (existing `ExperimentPlanner`, but ranked by EVOI).
7. **Writes a directory of md files** under `files/research/<run_id>/`:

```
idea.md            — the question, in the user's words
hypothesis.md      — ranked hypotheses with rationale and falsification criteria
literature.md      — triaged papers with grades and entities
experiment_plan.md — proposed experiments with controls, endpoints, budgets
results.json       — structured numbers (machine-readable)
analysis.md        — insights, trends, contradictions
paper_outline.md   — section-by-section outline
paper.md           — the manuscript draft
manifest.json      — sha256 of every file + HMAC receipt
```

### 3.2 Determinism guarantees

- Same query + same model + same seed → same file hashes (BITWISE for the extraction step).
- The LLM only drafts prose; extraction, grading, and ranking are deterministic Kotlin/Python.
- Every number in `paper.md` is traceable to a `results.json` entry via the manifest.
- A re-run produces the same `manifest.json` hash (verified by `paper_chain` logic).

### 3.3 Where it lives

- `research/deep/DeepResearchRunner.kt` — orchestrates search + extract + grade + write
- `research/deep/FreeSearchClients.kt` — Europe PMC, OpenAlex, Semantic Scholar, bioRxiv, arXiv
- `research/deep/ClaimExtractor.kt` — structured claim from abstract (rule-based, no LLM)
- `research/deep/ResearchFileWriter.kt` — writes the md directory + manifest
- Extends `DeepResearchSystem.run` with a `writeFiles: Boolean` parameter

### 3.4 Free search engines (verified)

| Engine | API | Key needed? | Rate limit |
|---|---|---|---|
| Europe PMC | REST, JSON | No | 10 req/s |
| OpenAlex | REST, JSON | No (polite pool with email) | 100k/day |
| Semantic Scholar | REST, JSON | No (optional key for higher limits) | 100 req/5min |
| bioRxiv/medRxiv | REST, JSON | No | unpublished |
| arXiv | Atom/XML | No | 3 req/s |
| PubMed (E-utilities) | REST, XML | Optional (higher limits with key) | 3 req/s (10 with key) |

All are already partially integrated (`SciencePaperManager`, `EuropePmcAdapter`, `OpenAlexAdapter`,
`SemanticScholarAdapter`). The work is unifying them into one fan-out client with dedup.

---

## 4. Build order

1. **Notebook model** (domain types + hash, no UI) — 1 day
2. **Deep research file writer** (md directory + manifest) — 1 day
3. **Free search fan-out** (unify existing adapters) — 1 day
4. **Progressive hypothesis engine** (single-shot first, then multi-round) — 2-3 days
5. **Notebook screen** (Compose, read-only first) — 1 day
6. **Wire into ResearchScreen** (replace Step 2 with "run deep research → notebook") — 0.5 day

Total: ~6-7 days, each phase independently testable.

---

## 5. Guardrails carried forward

- The LLM drafts prose only. Extraction, grading, ranking, and file writing are deterministic.
- Every number in every md file traces to a `results.json` entry via the manifest hash.
- A notebook cell is append-only; re-running produces a new version, never overwrites.
- The progressive engine's anti-collapse guards are code, not convention.
- No hypothesis is auto-promoted: the composer emits `UNREVIEWED` always.
