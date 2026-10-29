# Evidence Backbone — the phone-sized subset of the research schema

**Date:** 2026-10-28
**Status:** domain layer implemented (`com.example.concordance.evidence`), storage next.
**Source design:** the platform schema (PostgreSQL + JSONB + pgvector + FAISS + MLflow).

The design is sound, but most of its service boundary assumes a server fleet. This records what
was adopted, what was translated, and what was refused — so the next person does not re-litigate it.

---

## 1. Adopted, adapted, refused

| Schema element | Decision | What it became |
|---|---|---|
| `source_artifact` + `content_hash UNIQUE` | **adopted** | `SourceArtifact`. The id **is** the hash prefix (`art_<16>`), so dedup is structural, not a constraint someone can skip |
| `lineage_edge` | **adopted** | `LineageEdge` with a closed `DerivedKind` set. One table instead of a column per entity |
| `ingestion_run` | deferred | the existing `ProvenanceEntity` records retrieval; a run table is only needed once ingestion is batch |
| `dataset_record` | **adopted** | `DatasetRecord` with `isPinned`. This is what `ExperimentSpec.datasets[].sha256` refers to |
| `omics_feature` | **refused (for now)** | feature matrices belong in the Kaggle kernel, not in Room. The phone stores the dataset record and the artifact hash, not the values |
| `paper` + `paper_embedding` | partially exists | `LocalCorpus` already indexes papers; embeddings become sqlite-vec when retrieval is actually used |
| `literature_entity` | deferred | canonicalisation (HGNC/RxNorm/MeSH) is valuable but needs a resolver; INDRA and CIViC cover part of it today |
| `kg_edge` | **adopted** | `KgEdge`. `evidence_count` is derived from the evidence list, never stored separately, so it cannot drift |
| `hypothesis` | **adopted** | `Hypothesis` with `reviewStatus`, `reviewer`, `modelVersion` |
| `qc_status` | translated | `EvidenceRules` on the edge + the existing `EvidenceGrade`/`withheld` discipline |
| `review_status` | **adopted and tightened** | forward-only state machine: `UNREVIEWED → IN_REVIEW → REVIEWED \| REJECTED` |
| PostgreSQL 16 | **refused** | Room/SQLite on the phone, D1 as the mirror, R2 for bytes |
| pgvector / FAISS | **refused** | sqlite-vec when the corpus is queried; exact scan is fine below ~100k chunks |
| MLflow | **refused** | `RunRecord` ledger + capsule provenance already do this job |
| MinIO / S3 | **refused** | R2 + Kaggle Datasets |
| Neo4j / recursive CTEs | **refused** | SQLite recursive CTE is enough for one to three hops |

---

## 2. The guardrails that survived translation

1. **Every derived record points at an artifact** (`LineageEdge`), and the artifact is identified
   by its content hash. A claim can always be walked back to the bytes.
2. **An edge with no evidence is not an edge.** `EDGE_NO_EVIDENCE` — refused at write time.
3. **A hypothesis with no supporting edge is not a hypothesis.** `HYP_NO_EDGES`.
4. **Every cited edge must exist.** `HYP_UNKNOWN_EDGE`. This is checked against the store, not
   against the document.
5. **Nothing is publishable before a named human reviews it.** `REVIEW_TRANSITION` and
   `REVIEW_REVIEWER`. There is no path from `UNREVIEWED` to `REVIEWED`.
6. **Every output carries its generator.** `modelVersion` is required; a blank one is refused.
7. **The drug is standardised or the hypothesis is refused.** `drugRxnorm` is required — an
   unstandardised name is not an auditable candidate.
8. **Self-edges are refused.** `EDGE_SELF`.

Each of these has a test in `EvidenceGraphTest`.

---

## 3. Deterministic identity

| Record | Id | Consequence |
|---|---|---|
| artifact | `art_` + sha256 prefix of the bytes | re-ingest is a no-op |
| dataset | `ds_` + sha256(`source|studyId|checksum`) | the same cohort from two paths is one record |
| edge | `kg_` + sha256(`subject.kind|subject.id|predicate|object.kind|object.id`) | evidence merges; the count stays honest |
| hypothesis | `hyp_` + sha256(`cancer|biomarker|drug|modelVersion`) | regenerating the same candidate under the same model updates rather than duplicates |

That last row is why `modelVersion` is part of the id rather than only a field: two model
generations of the same candidate are different claims, and both must remain auditable.

---

## 5. Connectors — how an integration becomes evidence

A connector is pure: it takes an existing integration's result, the timestamp, and returns
records. It never fetches, never writes, never invents a hash.

| Connector | Input (already integrated) | Produces | Refusal |
|---|---|---|---|
| `LiteratureConnector` | `SciencePaper` (Europe PMC / OpenAlex) | one content-addressed `PAPER_XML` artifact | none; identity falls back doi -> pmid -> internal id |
| `CohortConnector` | `CohortStudy` (cBioPortal) | one **unpinned** `DatasetRecord` | `COHORT_NO_SAMPLES` when the study has no sample count |
| `CivicConnector` | `CuratedVariantEvidence` (CIViC) | artifact + `KgEdge` + lineage edge | `CIVIC_NO_GENE`, `CIVIC_NO_OBJECT` |
| `WetLabConnector` | `WetLabRecord` | one `WETLAB_FILE` artifact | `WETLAB_UNVERIFIED` when the record fails its own hash |

Two decisions worth defending:

- **`CohortConnector` does not pin the dataset.** cBioPortal gives no content hash, and inventing
  one would defeat the pin. The record arrives unpinned and stays that way until someone hashes
  real bytes.
- **`CivicConnector` splits sensitivity from resistance.** A resistance result must never be able
  to support a therapy, so `RESISTANT_TO` is a distinct predicate rather than a sign on
  `SENSITIVE_TO`. `ASSOCIATED_WITH` is the fallback only when the significance is neither.

`ConnectorIngest.write` is the only place that touches a store, and it reports what happened:
created vs deduplicated artifacts, created vs merged edges, and every rejection with its code.
Ingesting the same CIViC item twice yields `artifactsCreated = 0`, `artifactsDeduplicated = 1`,
`edgesMerged = 1` — which is the behaviour `evidence_count` depends on.

## 6. Composer — the ranker, and its three refusals

`HypothesisComposer.compose(request, edges)` is the only thing that produces a `Hypothesis`.
It refuses in three situations:

1. **No supporting edge** (`NO_SUPPORTING_EDGE`) — an edge must start at the gene and end at the
   drug or the cancer type. Anything else is a different claim.
2. **Resistance evidence at least as strong as sensitivity** (`RESISTANCE_CONTRADICTS`) — the
   candidate is refused, not merely down-ranked. A resistance result must never be outvoted by
   stacking sensitivity papers.
3. **An incomplete or unstandardised request** (`INCOMPLETE_REQUEST`) — including a blank
   `drugRxnorm`.

The rank is documented and bounded:

```
rank = 0.60 * mean(edge score)
     + 0.25 * min(1, supporting edge count / 4)
     + 0.15 * min(1, distinct evidence kinds / 4)
```

Half the weight is evidence strength; the rest rewards corroboration from independent sources.
A single perfect edge reaches ~0.70, never 1.0. Weaker resistance evidence is kept as a caveat in
the rationale and is **excluded** from the supporting citations, so a hypothesis can never cite
contradictory evidence as support.

Every composed hypothesis is `UNREVIEWED`; the composer has no way to approve one.

## 7. Next: Room v8 (sketch)

Five tables, additive, `exportSchema = false` like the rest:

```sql
CREATE TABLE source_artifacts (
  artifactId TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, originUri TEXT NOT NULL,
  contentHash TEXT NOT NULL, mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL,
  ingestedAtUtc TEXT NOT NULL, ingestionRunId TEXT);
CREATE UNIQUE INDEX idx_artifact_hash ON source_artifacts(contentHash);

CREATE TABLE lineage_edges (
  pk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, derivedKind TEXT NOT NULL,
  derivedId TEXT NOT NULL, artifactId TEXT NOT NULL, transform TEXT NOT NULL,
  transformVersion TEXT NOT NULL, createdAtUtc TEXT NOT NULL);
CREATE INDEX idx_lineage_derived ON lineage_edges(derivedId);

CREATE TABLE datasets (
  datasetId TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, studyId TEXT NOT NULL,
  cancerType TEXT NOT NULL, assayType TEXT NOT NULL, sampleCount INTEGER NOT NULL,
  checksum TEXT NOT NULL, license TEXT NOT NULL, access TEXT NOT NULL, dataClass TEXT NOT NULL);

CREATE TABLE kg_edges (
  edgeId TEXT NOT NULL PRIMARY KEY, subjectKind TEXT NOT NULL, subjectId TEXT NOT NULL,
  subjectLabel TEXT NOT NULL, predicate TEXT NOT NULL, objectKind TEXT NOT NULL,
  objectId TEXT NOT NULL, objectLabel TEXT NOT NULL, evidenceJson TEXT NOT NULL,
  score REAL NOT NULL, createdBy TEXT NOT NULL, createdAtUtc TEXT NOT NULL);

CREATE TABLE hypotheses (
  hypothesisId TEXT NOT NULL PRIMARY KEY, cancerType TEXT NOT NULL,
  biomarkerSymbol TEXT NOT NULL, biomarkerType TEXT NOT NULL, candidateDrug TEXT NOT NULL,
  drugRxnorm TEXT NOT NULL, rationale TEXT NOT NULL, supportingEdgeIdsJson TEXT NOT NULL,
  pubmedIdsJson TEXT NOT NULL, datasetIdsJson TEXT NOT NULL, wetlabIdsJson TEXT NOT NULL,
  rankScore REAL NOT NULL, modelVersion TEXT NOT NULL, generatedAtUtc TEXT NOT NULL,
  reviewStatus TEXT NOT NULL, reviewer TEXT, reviewedAtUtc TEXT);
```

Lists are stored as JSON strings because that is what the existing entities already do for
`provenanceRecordIds`; a converter would be a new mechanism for no gain. A `RoomEvidenceStore`
must reproduce the in-memory semantics exactly, and its tests should reuse `EvidenceGraphTest`'s
cases rather than inventing new ones.

---

## 5. What this unlocks, and what it does not

**Unlocks:** a hypothesis that can cite a real edge, that cites a real dataset checksum, that
cites a real artifact — and that cannot be published until a human signs it. That is the
"structured hypothesis JSON + audit" from the design, without a server.

**Does not unlock yet:** feature-level omics joins, embeddings/semantic retrieval, the ranker's
multi-evidence scoring, the OCR-to-wet-lab parser. Those remain better done in the Kaggle kernel
(compute) or deferred until the corpus exists (retrieval). Building them now would be a schema
with no data — the exact failure mode this project keeps finding.
