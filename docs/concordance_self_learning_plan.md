# Concordance — Recursive Self-Learning System: Research & Plan

> Companion to `concordance_integrations_phase_a_implemented.md`. This document
> plans a *bounded, auditable* recursive self-learning loop for Concordance's
> detection-correction engine. It is a research/plan document — no code is
> changed by it. Implementation is intentionally phased so each step is
> independently verifiable.

## 1. What "self-learning" means here

Concordance adjudicates organotropism claims (e.g. "ESR1 is depleted in brain
metastases") by correcting for detection artifact. Its current engine is
deterministic: given an `Anchor` (counts or odds ratio) and a sensitivity ratio
`r = s_brain / s_other`, it emits a verdict (`WITHHELD`, `DETECTION_ARTIFACT`,
`ORGANOTROPISM`, …) plus a citable argument.

A *recursive* self-learning system lets the engine **improve its own parameters
from accumulated evidence** while staying auditable and reversible:

- Every filed `Challenge` (already supported via `challengeClaim`) is a labeled
  training signal: "verdict X was wrong, here are corrected counts."
- The engine re-derives `rNull`, `rSignificance`, and the reference prevalence
  from the growing corpus of anchors + challenges.
- A new **model revision** is published; capsules built before it remain
  pinned to their original revision (provenance already supports this).
- The loop repeats: new revisions are evaluated against a held-out set of
  capsules; only revisions that strictly improve the metric are promoted.

This is *not* AGI-style recursive self-improvement. It is a bounded,
human-supervised, metric-gated parameter search over a small, interpretable
model. The "recursive" part is that the engine's own output (capsules +
challenges) becomes the training data for the next revision — hence the
explicit model-collapse and alignment guards in §6.

## 2. Research basis (key takeaways)

| Source | Takeaway applied here |
|---|---|
| Wikipedia — *Incremental learning* | Learn continuously from new data without catastrophic forgetting; keep old knowledge reachable (stable representations). |
| Wikipedia — *Recursive self-improvement* | Iterative design of a more capable successor needs an **automated evaluation function** (AlphaEvolve insight) and human review of AI-generated changes. |
| AlphaEvolve (DeepMind, 2025) | Mutate + select against an automated metric; "a key limitation is the need for automated evaluation functions" — so we must define one *before* any learning. |
| STOP (Zelikman et al., 2024) | A fixed base model + a self-improving *scaffolding* is safer than letting the base model rewrite itself. We keep the statistical engine fixed and only tune its parameters. |
| Anthropic alignment-faking study (2024) | Self-improvement can be covertly misaligned; mitigation = human-in-the-loop + audit trail + rollback. |
| Model-collapse critique (Hackaday 2026) | Training on self-generated data degrades quality. Guard: only learn from human-authored challenges or externally sourced paired counts, never from the engine's own capsules. |

## 3. Automated evaluation function (define this first)

Without a metric the loop is unfalsifiable. Define:

```
E(revision) = weighted sum over held-out capsules of:
  1. Verdict stability:      did the revision flip a WITHHELD/ORGANOTROPISM
                            verdict on a capsule whose challenge log is empty?
                            (flips on unchallenged capsules are penalized)
  2. Challenge resolution:  for capsules that received a challenge, does the
                            new revision's verdict match the challenge's implied
                            verdict (derived from corrected counts)?
  3. Calibration:           |predicted probability of detection-artifact |
                            - empirical fraction of challenges labeled
                              DETECTION_ARTIFACT|  (lower is better)
  4. Parsimony:             number of parameters changed vs. baseline (L1)
                            (prevents overfitting to few challenges)
```

Promotion rule: a candidate revision is promoted only if `E(candidate) < E(current) - ε` on the held-out set AND no regression on any single capsule's verdict that a human has not explicitly challenged. ε guards against noise.

## 4. Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    LearningController                       │
│  (orchestrates one "round"; human-approved to run)          │
└──────┬─────────────────────────────────────────────────────┘
       │
       ├──► TrainingSetBuilder
       │      - selects capsules with challenges (positive labels)
       │      - selects unchallenged capsules (negative / stability set)
       │      - EXCLUDES any capsule whose anchor came from a previous
       │        model revision's own output (anti-collapse guard)
       │
       ├──► ParameterSearch
       │      - bounded grid/random search over:
       │          rNull ∈ [0.05, 0.60], step 0.01
       │          rSignificance ∈ [0.01, 0.20], step 0.01
       │          referencePrevalence ∈ [0.05, 0.40], step 0.01
       │      - base engine fixed (STOP scaffolding principle)
       │
       ├──► Evaluator  (computes E on held-out set)
       │
       ├──► RevisionPublisher  (only if promotion rule passes)
       │      - writes a new ModelRevision row (append-only)
       │      - bumps "current revision" pointer atomically
       │
       └──► AuditLog  (every candidate, metric, decision, human approver)
```

### Data model additions

- `ModelRevision` entity: `id`, `parentRevisionId`, `createdAtUtc`, `parametersJson`,
  `metricE`, `promotedBy` (user id / "auto"), `notes`.
- `Capsule` gains `modelRevisionId` (which revision built it). Existing capsules
  backfilled to the baseline revision id.
- `Anchor` gains `modelRevisionId` of the revision that produced its sensitivity
  curve. Anchors are immutable once stored; re-running under a new revision
  creates a *new* anchor linked to the old one via `supersedesAnchorId`.

### Storage

- Revisions are **append-only**. Never overwrite. Rollback = point the
  "current revision" pointer back. This mirrors the existing capsule
  immutability and provenance-digest philosophy.
- A `ModelRevisionDao` + `ModelRevisionRepository`, consistent with the existing
  `ProvenanceDao`/`ResponseCache` pattern.

## 5. Phased implementation plan

### Phase 0 — Foundations (1–2 days)
1. Define and unit-test the evaluation function `E` on the existing 3 benchmark
   anchors + their capsules. No learning yet.
2. Add `ModelRevision` entity + DAO + migration (v4). Backfill existing
   capsules/anchors to baseline revision `rev-0`.
3. Add `LearningController` skeleton with a dry-run mode that prints candidate
   metrics but publishes nothing.

**Exit criterion:** `E(rev-0)` reproducible in a unit test; migration test green.

### Phase 1 — Offline learning round (3–5 days)
1. Implement `TrainingSetBuilder` and `ParameterSearch` (bounded grid).
2. Implement promotion rule + `RevisionPublisher`.
3. Add a "Run learning round" button in a new **Model** tab (index 10), gated
   behind an explicit confirmation dialog ("This will create a new model
   revision. Proceed?").
4. Show before/after metrics and the parameter diff in the UI.

**Exit criterion:** a learning round on the benchmark data produces a revision
with `E(candidate) < E(baseline)` OR correctly reports no improvement.

### Phase 2 — Human-in-the-loop gating (2–3 days)
1. Require human approval for *promotion* even when the metric improves
   (approval stored in `AuditLog` with approver + timestamp).
2. Add rollback: "Revert to previous revision" button, gated by confirmation.
3. Add a "what changed" diff view: verdicts that flipped, parameters that moved.

**Exit criterion:** a promoted revision can be rolled back and capsules
re-render under the prior revision's verdicts.

### Phase 3 — Continual (incremental) mode (3–5 days)
1. Auto-trigger a learning round when N new challenges accumulate (default 5),
   but only in a *proposal* state — never auto-promote.
2. Add a "stability set" monitor: if a revision flips > K% of unchallenged
   capsules, auto-reject it regardless of metric.
3. Surface a "learning health" dashboard: revisions over time, metric trend,
   challenge resolution rate.

**Exit criterion:** 3 consecutive auto-proposed rounds each require human
approval; no auto-promotion ever occurs.

### Phase 4 — Hardening (ongoing)
1. Anti-collapse guard: verify training set contains zero self-generated
   capsules (assertion + test).
2. Parameter bounds enforced at the DB level (CHECK constraints) so a bug
   cannot publish an out-of-range revision.
3. Export/import of revisions for reproducibility (JSON, versioned schema).

## 6. Safety & alignment guards (non-negotiable)

1. **No self-generated training data.** `TrainingSetBuilder` asserts every
   training anchor's `source` is not `"Concordance"` and its
   `modelRevisionId` is the baseline or a human-approved revision. Violation
   → round aborts.
2. **No auto-promotion.** A revision is only current after explicit human
   approval. Auto-proposed rounds stop at "proposal".
3. **Bounded search space.** Parameters live in declared ranges; the publisher
   rejects out-of-range values.
4. **Append-only revisions.** Rollback is always possible; nothing is ever
   overwritten.
5. **Audit everything.** Every candidate (promoted or not), its metric, its
   parameter diff, and the approver (or "rejected") is persisted.
6. **Human-authored challenges only.** The engine never synthesizes its own
   challenges or labels.
7. **Capsule pinning.** A capsule's verdict is immutable; re-running under a
   new revision creates a *new* capsule linked via `supersedesCapsuleId`.
   Old capsules keep their original verdict and provenance digest.

## 7. What we will NOT do

- No neural-network weight updates, no fine-tuning of any model. The statistical
  engine stays a closed-form, interpretable function. This is what makes the
  loop auditable and is consistent with the app's "research workflow tool, not
  medical device" non-clinical framing.
- No autonomous code generation or architecture changes (no STOP-style
  self-rewriting). Only parameter tuning within declared bounds.
- No learning from the engine's own output (anti-collapse).
- No silent promotion. Ever.

## 8. Open questions for the user

1. Should learning rounds be runnable on-device, or only via a future
   "research mode" toggle? (On-device grid search over ~3 parameters × ~50
   steps × ~100 capsules is cheap; recommend on-device.)
2. Should the held-out set be the 3 benchmark anchors, or should we collect a
   larger curated set first? (Recommend: collect ≥ 20 paired-count anchors
   before enabling Phase 1.)
3. Who is the approver identity — a single local user, or should we support
   multi-reviewer sign-off? (Recommend: single user for v1, matching the
   existing `challengeClaim(author, …)` model.)

## 9. Estimated effort

| Phase | Effort | Dependencies |
|---|---|---|
| 0 Foundations | 1–2 days | none |
| 1 Offline round | 3–5 days | Phase 0 |
| 2 HITL gating | 2–3 days | Phase 1 |
| 3 Continual mode | 3–5 days | Phase 2 |
| 4 Hardening | ongoing | Phase 3 |

Total to a usable, safe v1: ~2–3 weeks of focused work, with a usable
"propose a revision and show the diff" milestone at the end of Phase 1.

## 10. Phase 0 status — IMPLEMENTED (2026-10-18)

Delivered:

- learning/ModelRevision.kt — ModelParameters (sn, referencePrevalence, z95)
  with bounds; ModelRevision; ChallengeLabel; EvaluationCase;
  EvaluationResult; DryRunReport.
- learning/LearningEvaluator.kt — the automated evaluation function E:
  verdict-stability penalty (unchallenged flips vs. baseline),
  challenge-resolution miss rate, calibration error, parsimony (L1 from
  baseline); weighted 	otal (lower is better). Adjudicates via the fixed
  StatisticalEngine (STOP scaffolding principle).
- learning/LearningController.kt — dry-run orchestrator: scores a candidate
  against a baseline, applies an epsilon promotion rule plus a 25% stability
  guard, and NEVER promotes (promoted always false). Includes a bounded
  candidate grid generator.
- Persistence: model_revisions table (Room v3 -> v4 migration), append-only
  ModelRevisionDao + repository methods (saveModelRevision,
  getAllModelRevisionsFlow, getLatestPromotedRevision). claims and
  capsules gained a nullable modelRevisionId column (null = baseline).
- Tests: LearningEvaluatorTest (7) covers finite/well-formed baseline E,
  zero stability penalty for identical params, out-of-bounds rejection, dry-run
  non-promotion, bounded grid, and challenge resolution.
  ModelRevisionPersistenceTest (2) covers Room round-trip and the guarantee
  that unpromoted proposals are never treated as current.

Exit criterion met: E("rev-0") is reproducible in a unit test, the v4 schema
works, and no auto-promotion path exists.

Verified: ssembleDebug + 	estDebugUnitTest -> 36 tests, 0 failures.

Next: Phase 1 (offline learning round) — TrainingSetBuilder + bounded
ParameterSearch + human-approved RevisionPublisher + a "Model" tab (index 10).

## 11. Phase 1 status — IMPLEMENTED (2026-10-19)

Delivered:

- learning/TrainingSetBuilder.kt — builds the evaluation set from stored
  anchors + human-authored challenges. Carries the anti-collapse guard
  (plan 6.1): anchors that are Concordance-imported (source prefix
  "Concordance import" / "CIViC EID") or produced under a prior
  modelRevisionId are refused, and ANY violation aborts the round. Anchors
  with neither 2x2 counts nor a summary OR are skipped (not fatal).
- learning/ParameterSearch.kt — bounded grid search. Declared bounds:
  sn 0.50–1.00 step 0.05, referencePrevalence 0.10–0.30 step 0.02. z95 is
  deliberately NOT searched (confidence-interval width must not be
  optimizable). Returns candidates sorted ascending by total.
- learning/RevisionPublisher.kt — pure revision lifecycle:
  propose (always promotedBy = null), pprove (requires non-blank
  approver, in-bounds params, AND a genuine metric improvement), 
evert
  (append-only: publishes a NEW revision carrying the target's params rather
  than mutating or deleting history).
- Repository: getAllChallenges() and getAllClaims() one-shot snapshots.
- ViewModel: 
unLearningRound() (dry run only), promoteCandidate(candidate,
  approver), 
evertToRevision(id, approver), plus state flows for the
  report, candidates, training summary, current revision, and revision history.
- New tab 10 "Self-Learning" (ModelLearningTab.kt): current revision card,
  dry-run trigger, E-score breakdown, approver field, top-10 candidates,
  append-only revision history with revert, and confirmation dialogs on both
  promote and revert.
- Tests: SelfLearningRoundTest (14) covering the anti-collapse guard (both
  the import-prefix and prior-revision paths), skip-not-fatal behavior,
  challenge attachment, grid bounds, best-first ordering, proposal-never-
  promoted, approval refusal on blank approver and on non-improvement,
  append-only revert, and dry-run-never-promotes.

BUG FOUND AND FIXED DURING PHASE 1: the first anti-collapse guard only matched
the literal source string "concordance". The real CIViC importer writes
"CIViC EID<n>: <url>", so genuinely engine-imported anchors would have passed
the guard and been trained on — the exact model-collapse failure the guard
exists to prevent. The guard now matches declared prefixes ("concordance",
"civic eid") and the importer stamps "Concordance import (CIViC EID<n>)".
This was caught by the new test suite, not by inspection.

Verified: ssembleDebug + 	estDebugUnitTest -> 50 tests, 0 failures.

Next: Phase 2 — harden promotion (require the stability guard result to be
surfaced in the approval dialog), add a verdict-flip diff view, and surface
revision id in capsule detail.

## 12. Phase 2 status — IMPLEMENTED (2026-10-19)

Phase 1 already delivered the approval gate (pprove requires a non-blank
approver + genuine improvement) and the append-only revert. Phase 2 adds the
remaining two plan items: the **decision audit** and the **"what changed"
diff view**.

Delivered:

- learning/RevisionDiffer.kt — pure "what changed" report between two
  revisions: ParameterDelta (sn / referencePrevalence / z95 movement),
  VerdictFlip (anchor label + from/to verdict), and RevisionDiff with
  flip count, unchanged count, and lipRate. It is a PREVIEW only — stored
  capsules are never re-adjudicated or mutated (plan §6.7).
- LearningAuditEntity + LearningAuditDao (Room learning_audit, schema
  v4 -> v5, MIGRATION_4_5). Records EVERY decision, promoted or rejected
  (plan §6.5): candidate + baseline params, the full metric breakdown,
  decision (PROPOSED / PROMOTED / REJECTED / REVERTED), approver, revision id,
  timestamp, notes. Newest first.
- Repository: getAllLearningAuditFlow(), saveLearningAudit(), plus shared
  ModelParameters JSON helpers reused by revisions and audit rows.
- ViewModel: previewCandidateDiff(candidate), previewRevisionDiff(id),
  clearRevisionDiff(), 
evisionDiff + learningAudit state flows, and a
  private 
ecordAudit(...). Promotion now writes a PROMOTED or REJECTED audit
  row; revert writes a REVERTED row.
- UI (ModelLearningTab.kt): "Preview diff" on each candidate, "Diff vs
  current" on each history entry, a diff panel (parameter movement + verdict
  flips + % of anchors changed), and an append-only decision-audit list
  colour-coded by decision.
- Tests: RevisionDiffTest (7) and LearningAuditPersistenceTest (1). The
  diff test asserts the differ agrees anchor-for-anchor with an independent
  adjudication comparison across the whole declared grid, so it cannot pass by
  merely reporting "no flips".

NOTE ON EXIT CRITERION: the plan's Phase 2 exit criterion was "a promoted
revision can be rolled back and capsules re-render under the prior revision's
verdicts." The rollback half is met (append-only revert). The "re-render" half
was initially implemented as a DIFF PREVIEW (RevisionDiffer) because a capsule
is permanently pinned to the revision that produced it (plan §6.7) and
mutating it in place would break its SHA-256 provenance digest.

Re-adjudication was then added as an explicit, opt-in action
(reAdjudicateUnder) at the user's request: it re-runs adjudication on every
stored claim under a chosen revision's parameters and publishes NEW capsules
linked via supersedesCapsuleId. Old capsules are never mutated or deleted -
they stay pinned, preserving the provenance digest and audit trail. This is
exactly the mechanism §6.7 prescribes ("re-running under a new revision
creates a new capsule linked via supersedesCapsuleId").

Implementation details:
- Capsule + CapsuleEntity gained `supersedesCapsuleId` (migration folded into
  MIGRATION_4_5: ALTER TABLE capsules ADD COLUMN supersedesCapsuleId TEXT).
- CapsuleEngine.buildCapsule now accepts modelRevisionId + supersedesCapsuleId.
- generateAndSaveCapsule stamps capsules with the current modelRevisionId.
- ViewModel.reAdjudicateUnder(revisionId?) runs the pass and records a
  READJUDICATED audit entry.
- UI: "Re-adjudicate all claims under this revision" button on the current
  revision card.

Verified: ssembleDebug + 	estDebugUnitTest -> 109 tests, 0 failures
(8 new for Phase 2). Note: the repo now also contains unrelated parallel test
suites (EasyMode, PCM, VoiceAssets, AgentClient); all pass and none were
touched by this work.

Next: Phase 3 (proposal queue / batched review) per plan §7.


## 13. Phase 3 status — IMPLEMENTED (2026-10-19)

Continual (incremental) mode. Auto-propose only, never auto-promote.

Delivered:
- RuntimeTuning extended with autoRoundThreshold (default 5), lastAutoRoundChallengeCount,
  and stabilityThreshold (default 0.25). Persisted via RuntimeTuningStore.
- ViewModel.checkAutoTrigger(): when challenge count grows by >= threshold since the
  last auto-round, runs a DRY-RUN round (runLearningRound, which never promotes) and
  updates the persisted counter. Invoked from challengeClaim() and on demand.
- LearningController.dryRun now accepts a configurable stabilityThreshold (default 0.25)
  instead of a hardcoded 0.25, wired to the persisted setting.
- LearningHealth + LearningHealthCalculator: revisions count, promoted count, metric
  trend (promoted revisions' E), total/resolved challenges, challenge resolution rate
  for the current revision.
- learningHealth StateFlow in ViewModel, refreshed on revision changes.
- UI (ModelLearningTab): "LEARNING HEALTH" dashboard section with revision/challenge
  stats, metric trend, continual-mode settings (auto-round N, stability guard %),
  and a "Run auto-trigger check now" button.

Safety: auto-trigger calls runLearningRound (dry-run only) — no auto-promotion path
exists. Stability guard auto-rejects candidates flipping > K% of unchallenged verdicts.

Exit criterion: auto-triggered rounds stop at proposal; human approval still required
for promotion. Met.

Verified: assembleDebug + testDebugUnitTest -> 114 tests, 0 failures.

Next: Phase 4 (hardening: anti-collapse assertion+test, DB CHECK constraints,
export/import of revisions).


## 14. Phase 4 status - IMPLEMENTED (2026-10-19)

Hardening.

1) Anti-collapse assertion. TrainingSetBuilder.assertNoSelfGenerated(anchors)
   throws IllegalStateException if any self-generated anchor is present
   (import-prefix or prior-revision-derived). Belt-and-braces on top of the
   violation-collecting build(). Tests cover clean, imported, and
   revision-derived anchors.

2) Parameter bounds at the write boundary. ConcordanceRepository.saveModelRevision
   is now fail-closed: it returns false and writes nothing if
   parameters.inBounds() is false. This is the DB-access-layer enforcement - an
   out-of-range revision cannot reach the database regardless of caller bugs.

   NOTE on CHECK constraints: Room cannot declare SQL CHECK constraints on an
   @Entity, and a constraint added only in a migration would not exist on fresh
   installs (and vice versa), making enforcement inconsistent and untestable on
   in-memory databases. Enforcing at the repository write boundary is the
   consistent, testable equivalent and is what the plan's intent requires
   ("a bug cannot publish an out-of-range revision"). Tests:
   ModelRevisionBoundsTest (2) proves an out-of-bounds revision is refused and
   never becomes current.

3) Export/import for reproducibility. learning/RevisionArchive.kt: versioned
   JSON (schemaVersion=1, kind="concordance.model-revisions"),
   schemaVersion + kind + bounds validated on import, unknown version / wrong
   kind / missing fields / out-of-bounds all rejected with ArchiveException
   (never a silent partial import). ViewModel: exportRevisions(),
   importRevisions(json). UI: "REVISION ARCHIVE" section with export preview,
   import text field, and buttons.

Verified: assembleDebug + testDebugUnitTest -> 126 tests, 0 failures
(12 new: Phase4HardeningTest 10, ModelRevisionBoundsTest 2).

All four planned phases are now implemented. Remaining known limitation: the
loop's OUTPUT is only as good as the challenge corpus; with zero logged
challenges the challenge-resolution and calibration terms carry no signal and
promotion is (correctly) refused.


## 15. Challenge-logging UX wiring (2026-10-19)

Goal: make the loop's training signal visible and exercise-able end to end. The
challenge dialog itself already existed (Registry screen -> Challenge on a
claim -> ChallengeClaimDialog, prefilled with the claim's counts). What was
missing was any view of the corpus the loop actually learns from.

Added:
- ChallengeDao.getAllChallengesFlow() + repository.getAllChallengesFlow() so the
  corpus is reactive.
- ViewModel: allChallenges StateFlow, and challengeImpliedVerdict(challenge)
  which shows the verdict a challenge implies under the current revision.
- ModelLearningTab "CHALLENGE CORPUS" section: lists every logged challenge with
  its corrected counts, author, and implied verdict; shows an explanatory empty
  state when nothing is logged.
- ModelLearningTab learning-health section now shows "Challenges since last
  round: X / N" so the user can see how close the auto-trigger is.

End-to-end loop now: Registry -> Challenge (log corrected counts) -> corpus
section updates + implied-verdict preview + progress toward auto-round ->
run/auto learning round -> dry-run report + candidate E's -> approve (human) or
reject -> revision history + audit + health trend -> optional re-adjudication
under the new revision.

Verified: assembleDebug + testDebugUnitTest -> 130 tests, 0 failures
(4 new: LearningHealthTest).
