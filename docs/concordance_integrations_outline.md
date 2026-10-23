# Concordance Integrations: Research & Outline

**Purpose:** Define the external services Concordance should integrate with, how they map to the app's adjudication pain points, and the architecture to build them — before writing code.
**Current state (from code review):** `ApiRotationManager` registers 12 providers across 6 categories, but calls in `SciencePaperManager`, `PubChemManager`, and `ClinicalTrialsManager` are **simulated** (`delay(...)` + mock data). The UI has 8 tabs but a tab-index bug (two tabs share index 5; the `when(selectedTab)` blocks use 0–7). Only 7 curated papers, 5 compounds, and 3 trials are hard-coded.

---

## 0. Guiding Principles

1. **Free/open first.** Build on key-free public APIs (Europe PMC, OpenAlex, ClinicalTrials.gov, PubChem, CIViC, cBioPortal, MyVariant, gnomAD, Zenodo) before paid ones.
2. **Bring-your-own-key (BYOK).** Ship with no embedded secrets. Users supply API keys; store in Android Keystore / `EncryptedSharedPreferences`.
3. **License-aware.** Some resources (OncoKB, COSMIC, DrugBank) restrict programmatic/commercial use. Gate features by license class: `OPEN`, `ACADEMIC_ONLY`, `PAID`.
4. **Offline-first + cache.** Room DB cache with TTL + ETag; the app must work with no network on cached claims.
5. **Provenance in every capsule.** Record provider, query, timestamp, and response hash so capsules are auditable.
6. **Adapter pattern.** One interface per capability; `ApiRotationManager` handles routing/failover.

---

## 1. Integration Categories (Mapped to Pain Points)

| Category | Pain point served | Priority |
|---|---|---|
| Literature & evidence retrieval | Evidence fragmentation, claim sourcing | P0 |
| Variant/mutation knowledge | Mutation interpretation, VUS, organotropism | P0 |
| Clinical trials | Trial matching, coverage/equity | P0 |
| Compounds/drugs | Therapy landscape, BBB penetrance | P1 |
| Publishing/provenance (DOI) | Citable capsules, reproducibility | P1 |
| Compute/storage/AI | Heavy statistics, extraction, cloud vault | P1 |
| Imaging | Brain-met metastasis, neuro-oncology (heavy) | P2 |
| Clinical data standards (FHIR/OMOP) | Future EHR integration | P3 |
| Ontologies | Standardized terms, cross-resource joins | P2 |

---

## 2. Researched API Catalog

### 2.1 Literature & Evidence Retrieval (P0)

| API | Auth | Rate limit (researched) | License | Adds to Concordance |
|---|---|---|---|---|
| **PubMed E-utilities (NCBI)** | Optional API key | **3 rps** without key, **10 rps** with key; use `tool`+`email` params | Open (US Gov) | Reference search + `efetch` abstract → count extraction |
| **Europe PMC REST** | None | Generous, no key | Open | Full-text XML for OA papers; best count-extraction source |
| **OpenAlex** | Optional API key | Free "casual"; polite pool via `mailto`; **API key raises daily budget 10×** and is required for cursor paging past page 1 | Open (CC0) | 250M+ works; citation graph, concepts, OA status |
| **Crossref REST** | None | Polite pool via `mailto` | Open | DOI metadata, canonical citation records |
| **Semantic Scholar Graph API** | Optional API key | Unauthenticated ~**100 req/5 min** (shared); with key ~1 rps documented (sources vary — treat conservatively) | Open (terms) | TLDR summaries, citation contexts, DOI resolution in batches |
| **arXiv API** | None | Reasonable, no key | Open | Preprint methods/math (e.g., BBB attenuation models) |
| **bioRxiv / medRxiv API** | None | Open, no auth | Open | Preprint metadata for latest oncology preprints |
| **Unpaywall** | None (email param required) | Free, key-free | Open | Legal OA full-text links by DOI → feed extraction |
| **CORE** (optional) | API key | Free tier | Open | Aggregated OA full text |

### 2.2 Variant / Mutation Knowledge (P0)

| API | Auth | Rate limit | License | Adds |
|---|---|---|---|---|
| **ClinVar** (via NCBI E-utilities) | Optional key | 3/10 rps | Open | Clinical significance of variants |
| **CIViC** | Optional API key (generate in profile) | Free GraphQL | **Open access, open source** | Community-curated cancer variant interpretations — core for VUS/mutation claims |
| **cBioPortal Web API** | None | Free REST/Swagger (beta) | Open | Cross-study cancer genomics; cohort mutation frequencies by site |
| **OncoKB** | API token required | Token-gated | **Academic research free; paid license for commercial/clinical/programmatic use** | Actionable variant annotations ⚠️ license risk |
| **MyVariant.info** | None | Free REST | Open (aggregated) | One-call aggregation (ClinVar, dbNSFP, gnomAD, etc.) |
| **MyGene.info** | None | Free REST | Open | Gene annotation |
| **gnomAD GraphQL** | None | Free, key-free | Open | Population allele frequencies, constraint (pLI, LOEUF) — germline vs somatic context |
| **Ensembl VEP REST** | None | Free | Open | Variant consequence prediction |
| **Open Targets Platform GraphQL** | None | Free; query depth/complexity limits enforced | Open | Target–disease associations, tractability |
| **ChEMBL API** | None | Free (EBI) | Open (CC BY-SA) | Compound bioactivity, mechanism |
| **DGIdb** | None | Free API | Open | Drug–gene interactions |
| **DepMap** | None | Free via portal API / figshare | Open | Cancer dependencies (context for "undruggable" targets) |
| **NCI GDC API** | None | Free | Open | TCGA/CPTAC genomics |
| **COSMIC** | License | Paid | **Paid** | Somatic mutation catalogue ⚠️ |

### 2.3 Clinical Trials (P0)

| API | Auth | Rate limit | License | Adds |
|---|---|---|---|---|
| **ClinicalTrials.gov v2 REST** | None | Free; be polite | Open (US Gov) | Trial search, status, locations, endpoints (JSON) |
| **EU CTIS** | None | Free-ish, complex | Open | EU trials |
| **WHO ICTRP** | None | Free | Open | Global trial aggregation |

### 2.4 Compounds / Drugs (P1)

| API | Auth | Rate limit | License | Adds |
|---|---|---|---|---|
| **PubChem PUG-REST** | None | **≤5 req/sec, ≤400 req/min**; use identifying `User-Agent`/headers | Open | Compound properties, structure |
| **ChEMBL** | None | Free | Open | Bioactivity, targets |
| **OpenFDA** | None (key optional) | Higher with key | Open | Drug labels, adverse events |
| **DrugBank** | License | Paid | **Paid** | Detailed drug data ⚠️ |

### 2.5 Publishing / Provenance (P1)

| API | Auth | Rate limit | License | Adds |
|---|---|---|---|---|
| **Zenodo REST** | Access token (for deposits) | Free | Open (CERN) | **Mint DOIs for capsules**; archive datasets/software |
| **DataCite** | Membership/agreement | N/A | — | DOI registration (heavier than Zenodo) |
| **ORCID** | OAuth / public API | Free | Open | Author identity, attribution |
| **Crossref** | None (for metadata) | Polite pool | Open | Metadata; DOI **registration requires membership** — use Zenodo instead for minting |

### 2.6 Compute / Storage / AI (P1)

| Service | Auth | Rate limit / cost | Adds |
|---|---|---|---|
| **Kaggle API** | `kaggle.json` key | Free; kernel quotas | Heavy stats (permutation, bootstrap), datasets |
| **Hugging Face Inference API / Spaces** | Token | Free tier | On-demand models (extraction, embeddings) |
| **Google Gemini API** | API key | Free tier | Structured extraction, summarization |
| **OpenAI / Anthropic** | API key | Paid | Higher-quality extraction |
| **GitHub Actions** | Token | Free tier minutes | CI, batch jobs |
| **AWS S3/Lambda/Batch/Fargate** | IAM | Paid | Cloud vault, GPU workers |
| **On-device LLM** | None | Free, offline | llama.cpp / MLC-LLM / MediaPipe LLM Inference (Gemma/Gemini Nano) for offline extraction |

### 2.7 Imaging (P2 — heavy)

| Resource | Access | Notes |
|---|---|---|
| **TCIA** | Free public DICOM; Collection Manager REST API | Legacy NBIA API being retired → migrate to IDC |
| **NCI Imaging Data Commons (IDC)** | Free; **DICOMweb** | Preferred future path for public DICOM |
| **DICOMweb** (QIDO-RS, WADO-RS, STOW-RS) | Standard | Interop for viewers/PACS |
| **Orthanc** | Self-hosted, open | Local DICOM server for dev |
| **OHIF Viewer** | Open source | Web DICOM viewer |
| **MONAI** | Open source | Medical imaging DL (server-side) |

> Mobile note: do **not** attempt on-device DICOM processing at scale. Use DICOMweb metadata queries + link out to a viewer, or offload to cloud (existing `AWS_FARGATE_GPU` routing).

### 2.8 Clinical Data Standards & Ontologies (P2–P3)

| Standard | Use |
|---|---|
| **FHIR R4 / SMART on FHIR** | Future EHR read access (research, with BAA) |
| **OMOP CDM** | Research data model for cohort claims |
| **HPO / MONDO / Uberon / GO** | Standardized phenotype/disease/anatomy/gene terms |
| **BioPortal API / EBI OLS** | Ontology term lookup (free, key for BioPortal) |
| **ICD-10/11, SNOMED CT** | Coding (SNOMED licensing varies by country) |

---

## 3. Architecture Outline

### 3.1 Adapter interface (proposed)
```kotlin
interface IntegrationAdapter {
    val id: String
    val category: ProviderCategory
    val authType: AuthType        // NONE, API_KEY, OAUTH, TOKEN
    val licenseClass: LicenseClass // OPEN, ACADEMIC_ONLY, PAID
    val rateLimit: RateLimitSpec   // rps, burst, daily
    val capabilities: Set<Capability> // SEARCH, FETCH_COUNTS, METADATA, FULLTEXT, DOI_MINT, COMPUTE
    suspend fun search(query: Query): List<NormalizedRecord>
    suspend fun fetch(id: String): NormalizedRecord?
}
```
Every provider normalizes into internal models (`SciencePaper`, `Anchor`, `VariantAnnotation`, `ClinicalTrialStudy`, `Compound`) so downstream engines don't care about the source.

### 3.2 Extend `ApiRotationManager`
- Add **per-provider token bucket** rate limiting (respect 3/10 rps for NCBI, 5/sec for PubChem, etc.).
- Add **exponential backoff + circuit breaker** (currently only a status flag).
- Add **license gate** (block `ACADEMIC_ONLY`/`PAID` when app is in commercial mode).
- Record **provenance** (provider, query, timestamp, response hash) for capsule embedding.

### 3.3 Caching & offline
- Room table `cached_records(provider, externalId, json, etag, fetchedAt, ttl)`.
- Stale-while-revalidate; show cached data offline with a "last updated" badge.
- Deduplicate by DOI/PMID/NCT/CID across providers.

### 3.4 Secrets & privacy
- `EncryptedSharedPreferences` + Android Keystore for API keys; **never** hardcode.
- PHI: prefer on-device processing; if any PHI leaves the device (e.g., AWS), require BAA and de-identification. Keep the "research tool" posture.

### 3.5 Async & compute
- Reuse the existing job queue + WorkManager. Add a `ProviderBatchJob` that fans out across literature APIs and merges results.

### 3.6 Provenance record (for capsules)
```json
{
  "provider": "europepmc",
  "endpoint": "/search",
  "query": "ESR1 brain metastasis",
  "retrieved_utc": "2026-10-18T...",
  "http_status": 200,
  "response_sha256": "...",
  "license": "OPEN"
}
```
Embed this in every capsule so adjudications are traceable to sources.

---

## 4. Recommended Sequencing

### Phase A — Wire the free, key-free core (highest value, lowest friction)
1. Europe PMC (full text + counts)
2. OpenAlex (search + citation graph; add `mailto`)
3. PubMed E-utilities (reference + abstracts)
4. Crossref + bioRxiv/medRxiv + arXiv + Unpaywall
5. ClinicalTrials.gov v2 (replace the 3 hard-coded trials)
6. PubChem PUG-REST (replace the 5 hard-coded compounds; respect 5/sec)
7. MyVariant.info + MyGene.info + gnomAD + Ensembl VEP
8. CIViC + cBioPortal (variant interpretations & cohort frequencies)
9. Open Targets + ChEMBL + DGIdb (therapy/target landscape)
10. Zenodo (DOI minting for capsules)

### Phase B — Add keys (BYOK)
- NCBI API key (10 rps), Semantic Scholar key, CIViC key, Hugging Face token, Kaggle key.

### Phase C — Licensed/paid (gate carefully)
- OncoKB (academic-only research), COSMIC, DrugBank, AWS.

### Phase D — Heavy/future
- TCIA/IDC imaging via DICOMweb, FHIR/SMART on FHIR, OMOP, PRO (Health Connect/HealthKit).

---

## 5. Immediate Code Fixes (before adding providers)

1. **Fix the tab-index bug** in `IntegrationsScreen.kt`: two tabs both set `selectedTab = 5` (Kaggle and AWS S3), and the `when` blocks are numbered 0–7 while `ScrollableTabRow` has 8 entries with mismatched indices. AWS is at index 6, API Rotation at 6/7. Align indices.
2. **Replace `delay()` simulations** with real HTTP (Ktor/OkHttp + kotlinx.serialization) behind the adapter interface.
3. **Add rate limiting** to `ApiRotationManager` (token bucket per provider).
4. **Add caching** (Room) and offline handling.
5. **Add BYOK settings screen** + encrypted storage.
6. **Add a `LicenseClass` field** to `ApiProvider` and gate UI accordingly.

---

## 6. Licensing & Compliance Gotchas (researched)

- **OncoKB:** free for academic research and manual clinical reference, but **paid license required for commercial or programmatic/API use**, incl. clinical reporting. Do not ship an OncoKB API integration in a commercial build.
- **COSMIC / DrugBank:** paid licenses; avoid or gate.
- **NCBI E-utilities:** 3 rps default / 10 rps with key; large jobs recommended off-peak (9 PM–5 AM ET). Store your key and `email`.
- **PubChem:** ≤5 req/sec, ≤400 req/min; identify your app in headers.
- **OpenAlex:** add `mailto` for polite pool; API key needed for cursor paging past page 1.
- **Semantic Scholar:** conservative limits; batch DOI lookups (up to ~500 IDs) to stay efficient.
- **DOI minting:** use Zenodo (free) rather than Crossref (membership required).
- **TCIA:** legacy NBIA API retiring → plan on IDC/DICOMweb.
- **Imaging:** DICOM handling on mobile is heavy; offload or link out.

---

## 7. Owned Data/`*Base` Note (from codebase)

The project references an aggregated "ecosystem register" / `*Base` source for seeded counts (e.g., Giannoudis pooled). If Concordance maintains its own curated evidence register, that should be treated as a **first-class data source** (versioned, citable) alongside external APIs — with the same provenance discipline.

---

## 8. Sources

1. NLM Support / NCBI E-utilities: API key and rate limits (3 rps default; 10 rps with key).
2. OpenAlex Help Center: authentication, polite pool, API key (10× daily budget, cursor paging).
3. Semantic Scholar API docs / CASRAI guide: rate limits (~100 req/5 min unauth; documented keyed limits).
4. OncoKB FAQ/licensing + API docs: academic research free; paid for commercial/programmatic/clinical.
5. CIViC docs: free, open access; GraphQL API; optional API key.
6. cBioPortal docs: free REST API (Swagger/OpenAPI).
7. Open Targets Platform docs: GraphQL API, query complexity limits.
8. Zenodo Developers: REST deposit/publish API, DOI minting.
9. TCIA API guides: Collection Manager REST API, NBIA retirement → IDC.
10. MyVariant.info docs: free aggregated variant annotation.
11. gnomAD: free GraphQL API for population frequencies/constraint.
12. bioRxiv/medRxiv API docs: open, no auth.
13. Unpaywall REST API: free, key-free OA full-text by DOI.
14. PubChem PUG-REST documentation: rate limits and identification.

---

*This is a research/architecture outline for Concordance's integrations, not a commitment to specific vendors. Verify current rate limits and license terms before shipping each integration.*