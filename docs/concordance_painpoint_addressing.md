# How Concordance Can Address the Oncology Pain Points

**Question:** Given the researched pain points (breast cancer, neuro-oncology, tumor heterogeneity, mutations/targeted therapy, metastasis) and the AI/software landscape, what concrete features should Concordance add to address them?
**Scope note:** Concordance is a *research adjudication + evidence tool*, not a clinical device. It should attack the **evidence-generation, claim-quality, trial-design, and reproducibility** pain points — not treatment delivery, pain management, or survivorship care (those belong to clinical systems).

---

## 0. The Honest Scope Boundary

| Concordance CAN address | Concordance should NOT try to address |
|---|---|
| Organotropism / mutation-site claim quality | Direct patient treatment or dosing |
| Assay sensitivity confounding in metastasis research | Cancer pain management (opioid/multimodal analgesia) |
| Evidence grading & reproducibility (capsules) | Survivorship care coordination (psycho-oncology, PT) |
| Trial design & power for paired studies | Radiology screening/diagnosis (dense breast AI) |
| Literature synthesis & claim registry | EHR/workflow automation for clinics |
| Mutation-site discordance quantification | Drug discovery / compound screening |
| VUS/site-distribution interpretation (research) | Patient-facing navigation apps |

Keep the "Research Workflow Tool, not a medical device" disclaimer. Your moat is **methodology + provenance**, not clinical care.

---

## 1. Metastasis Pain Points (Strongest Fit)

The app's core is already here. Expand it:

### 1.1 Generalize organotropism adjudication to ANY (mutation × site) pair
- **Pain point:** Metastasis organotropism claims are confounded by assay sensitivity (BBB, tissue vs. plasma). ESR1/brain is one instance of a general problem (PIK3CA liver, HER2 bone, KRAS lung, etc.).
- **Feature:** Make `OrganotropismClaim` generic: `mutation`, `primarySite`, `metastaticSite`, `assayA`, `assayB`, `counts`, `siteSensitivityRatio`. ESR1/brain becomes a preset.
- **Code:** refactor `Anchor`/`AdjudicationResult` domain in `Models.kt`; add a "Claim Builder" screen; seed presets for common pairs.
- **Impact:** 10× TAM; turns a single-purpose tool into a platform.

### 1.2 Multi-site (not just brain vs. non-CNS) comparison
- **Pain point:** Metastases are organ-specific (bone, liver, lung, brain); "non-CNS" lumping loses signal.
- **Feature:** Allow N sites in a single adjudication; pairwise r* matrix; "which site is most confounded?"
- **Code:** extend `StatisticalEngine` to k-site; new chart (heatmap of r* by site pair).

### 1.3 Dormancy & reactivation tracking
- **Pain point:** Disseminated tumor cells dormant for years then reactivate; complicates cure/surveillance.
- **Feature:** Add a "longitudinal claim" type: track a mutation-site pair across timepoints; compute whether depletion persists or emerges. Capsule versioning already supports this — add time-series UI.
- **Code:** extend `Capsule` with `timepoint`; add timeline view in Registry.

### 1.4 Pre-metastatic niche / EMT claim adjudication
- **Pain point:** Claims about EMT, niche preconditioning, organotropism mechanisms.
- **Feature:** A "mechanism claim" adjudication template (not just detection sensitivity): does the evidence (counts/summary) support the mechanistic claim given assay limitations? Reuse the evidence-grade + verdict machinery.
- **Code:** new `ClaimType` enum (DETECTION_ARTIFACT, MECHANISM, PROGNOSTIC); generalize `StatisticalEngine.adjudicate`.

### 1.5 Anti-metastatic trial design module
- **Pain point:** Metastatic trials are hard to run (heterogeneous populations, endpoints, crossover).
- **Feature:** Trial-design wizard: pick mutation-site pair → power calculator (already exists) → minimum detectable r → recommended paired-design template → export protocol appendix. Add endpoint guidance (PFS, OS, ctDNA MRD).
- **Code:** extend `PairedDataScreen` + `requiredSample`/`powerSweep`; add export to markdown/PDF.

---

## 2. Breast Cancer Pain Points

### 2.1 ESR1/PIK3CA/HER2 brain-metastasis discordance atlas
- **Pain point:** ESR1 mutations mediate endocrine resistance; brain metastases show tissue-plasma discordance; Black women benefit less from therapeutic advances.
- **Feature:** Seed the registry with adjudicated claims for ESR1, PIK3CA, HER2, BRCA across brain/liver/bone/lung. Add subgroup stratification (race/ethnicity where reported) to the anchor model.
- **Code:** extend `Anchor` with `subgroup` field; `anchors.py` + Android seed data; Registry filter by subgroup.

### 2.2 ctDNA/liquid-biopsy interpretation (research-grade)
- **Pain point:** Negative plasma ctDNA cannot exclude CNS progression; BBB attenuation.
- **Feature:** A "ctDNA discordance calculator": given tissue VAF and plasma VAF, estimate r and whether depletion is artifact. This is literally your existing math — surface it as a standalone tool with a clear UI.
- **Code:** new screen `CtDNADiscordanceScreen` reusing `StatisticalEngine.nullCrossingR` + `corrected`.

### 2.3 Clinical trial diversity & access tracking
- **Pain point:** Black women under-offered trials; geographic access gaps.
- **Feature:** When ingesting ClinicalTrials.gov studies (already integrated), tag trials by site geography, enrollment demographics (if available), and mutation eligibility. Show "coverage gaps" (e.g., no TNBC trials in a region).
- **Code:** extend `ClinicalTrialsManager` parsing + a new "Trial Coverage" view. Honest caveat: ClinicalTrials.gov demographic data is sparse.

### 2.4 TNBC / hereditary subtype claim library
- **Pain point:** TNBC lacks targeted options; hereditary (BRCA) therapeutics still developing.
- **Feature:** Curate a claim library for TNBC-relevant mutations (BRCA1/2, PIK3CA, TMB) and hereditary risk variants; grade evidence; make citable.
- **Code:** seed `ANCHORS` / benchmark papers for BRCA/PIK3CA/TNBC.

---

## 3. Neuro-Oncology Pain Points

### 3.1 CNS tumor classification support (research-grade)
- **Pain point:** >100 CNS tumor entities; molecular classification (IDH, 1p/19q, MGMT, BRAF) reshaping diagnosis.
- **Feature:** A "mutation-site claim" view specifically for glioma subtypes: e.g., "Is IDH mutation frequency truly lower in glioblastoma vs. lower-grade glioma, or an artifact of sampling/assay?" Apply detection-correction to pathology/biopsy claims.
- **Code:** add `CnsTumorType` to Anchor; seed glioma/meningioma/medulloblastoma claims.

### 3.2 BBB attenuation as a reusable model
- **Pain point:** BBB limits drug delivery and ctDNA shedding.
- **Feature:** Publish your r = s_brain/s_other attenuation model as a standalone, citable calculator (input: tissue vs. plasma VAFs → output: r, r*, verdict). This is your most transferable asset for neuro-oncology.
- **Code:** extract into a `BbbAttenuationCalculator` utility + shareable link/permalink.

### 3.3 Long-term sequelae / survivorship endpoints
- **Pain point:** Permanent neurological deficits, seizures, learning disabilities in brain tumor survivors.
- **Feature:** Not a clinical care feature, but: add "endpoint" tagging to claims/trials (e.g., neurocognitive outcome, seizure freedom) so researchers can filter evidence by survivorship-relevant endpoints.
- **Code:** add `endpoints: List<String>` to `Anchor`/`ClinicalTrialStudy`; filter in Registry.

---

## 4. Tumor Heterogeneity Pain Points

### 4.1 Single-biopsy inadequacy → multi-sample claim adjudication
- **Pain point:** One biopsy may not capture the full clone spectrum; treatment chosen from one site may fail elsewhere.
- **Feature:** A "spatial heterogeneity claim" type: given counts from multiple biopsy sites/regions, adjudicate whether a mutation's apparent site-enrichment is real or sampling artifact. Reuse the same statistical core.
- **Code:** new `ClaimType.SPATIAL_HETEROGENEITY`; `StatisticalEngine` generalization.

### 4.2 Temporal heterogeneity → longitudinal claims
- **Pain point:** Tumors evolve under therapy; repeat biopsy at progression not standard.
- **Feature:** Longitudinal claim tracking: ingest serial samples (pre/post therapy) and adjudicate whether a mutation's frequency change survives detection correction. Capsule versioning handles this.
- **Code:** add `timepoint` to Anchor; timeline visualization.

### 4.3 Resistance mechanism claim library
- **Pain point:** Resistance is inevitable; need to distinguish acquired mutation (real) from detection artifact (assay sensitivity change post-therapy).
- **Feature:** Curate resistance-claim anchors (EGFR T790M/C797S emergence, BRAF reactivation, etc.) with evidence grades and citable capsules.
- **Code:** seed data + `ClaimType.RESISTANCE`.

---

## 5. Mutation & Targeted Therapy Pain Points

### 5.1 VUS / site-distribution interpretation
- **Pain point:** Many mutations (VUS) lack clear actionability; context-dependence (BRAF melanoma vs. colorectal).
- **Feature:** A "mutation site-distribution adjudicator": given a mutation's reported frequency across sites, test whether the distribution is consistent with artifact or real biology. This is your core math applied to a new question.
- **Code:** generalize adjudication; add `Mutation` entity linking to ClinVar/OncoKB (via API).

### 5.2 Mutation-to-therapy matching (research-grade)
- **Pain point:** 100+ approved targeted therapies; clinicians can't keep up.
- **Feature:** You already query ClinicalTrials.gov and PubChem. Add a "therapy landscape" view: for a given mutation, list approved targeted agents + active trials + mechanism, sourced from public APIs (OpenTargets, ChEMBL, OncoKB public). Keep it research-grade (not prescribing).
- **Code:** extend `IntegrationsScreen` with an "Evidence Landscape" tab; new managers for OpenTargets/ChEMBL.

### 5.3 Resistance monitoring claim adjudication
- **Pain point:** Serial ctDNA monitoring for emerging resistance; distinguishing real emergence from assay noise.
- **Feature:** Apply detection-correction to serial ctDNA claims: "Did resistance mutation X truly emerge, or is it below the detection threshold of the later assay?"
- **Code:** reuse `corrected` + `significanceThresholdR` with time-indexed inputs.

---

## 6. Cross-Cutting Pain Points

### 6.1 Evidence quality & reproducibility (your strongest fit)
- **Pain point:** Literature fragmentation, underpowered studies, false claims.
- **Feature:** This is already your core. Double down: add replication/challenge workflows (already have `challengeClaim`), versioned capsules, and a public "Concordance registry" of adjudicated claims with DOIs (Zenodo/Crossref).
- **Code:** `CapsuleEngine` already hashes; add DOI minting integration; `RegistryScreen` becomes a browsable public registry.

### 6.2 Early detection & screening claims
- **Pain point:** Many screening claims (AI mammography, ctDNA) need rigorous evaluation.
- **Feature:** A "screening claim adjudication" template: grade evidence (Counts/Summary/Abstract-only) and verdict for claims like "AI reduces interval cancers." Reuse evidence-grade + verdict machinery.
- **Code:** new `ClaimType.SCREENING`; seed with published AI-screening RCTs.

### 6.3 Cancer pain & survivorship (research-only, honest limits)
- **Pain point:** Breakthrough cancer pain 23–89%; chronic pain in 40–50% of survivors.
- **Feature:** Concordance cannot treat pain. But it CAN adjudicate claims about pain interventions (e.g., "multimodal analgesia reduces breakthrough pain in X population") using the evidence-grade + verdict framework. Add a "Supportive Care Evidence" claim type.
- **Code:** new `ClaimType.SUPPORTIVE_CARE`; seed with cancer pain guideline RCTs.

### 6.4 Access/equity & trial geography
- **Pain point:** Geographic/insurance disparities; underrepresented minorities in trials.
- **Feature:** Trial coverage map: overlay ClinicalTrials.gov studies on a map by mutation eligibility + geography; flag gaps. Add diversity metrics where reported.
- **Code:** extend `ClinicalTrialsManager`; new map view (osmdroid or Google Maps).

---

## 7. AI/Software Pain Points (from the AI report) — How Concordance Can Leverage Them

| AI/software opportunity | How Concordance uses it |
|---|---|
| Multimodal data integration | Fuse paper counts + paired assay data + trial data into one adjudication |
| Real LLM extraction | Replace simulated `LocalModelEngine` with structured JSON extraction from abstracts/full text |
| Agentic AI (AACR 2026) | An agent that: searches literature → extracts counts → runs adjudication → drafts capsule → checks registry for duplicates |
| Liquid biopsy + AI | ctDNA discordance calculator (§2.2) |
| Digital pathology spatial heterogeneity | Ingest spatial omics counts as a new claim type (§4.1) |
| Federated learning / data sharing | Multi-institution registry with privacy-preserving aggregation (future) |

### 7.1 Concrete AI upgrades for Concordance
1. **Real extraction engine:** Swap `LocalModelEngine.analyzeText` for a real LLM call (Gemini/OpenAI) with a JSON schema for `{brain_events, brain_total, other_events, other_total, odds_ratio, ci}`. Keep rule-based fallback for offline.
2. **Agentic literature pipeline:** An agent that takes a query → searches PubMed/OpenAlex → extracts counts → runs adjudication → drafts capsule → checks for duplicates in registry. This is the "Concordance agent" and a strong differentiator.
3. **Uncertainty quantification UI:** Surface confidence intervals and "what would change this" more prominently (already in data model; improve visualization).
4. **Auto-seed registry:** Run the agent over a corpus of organotropism papers to bootstrap 100+ adjudicated claims.

---

## 8. Prioritized Roadmap (What to Build Next)

### Phase 1 — Generalize (weeks 1–4)
- [ ] Refactor domain model: generic `OrganotropismClaim` (mutation × sites × assays)
- [ ] Add `ClaimType` enum: ORGANOTROPISM, SPATIAL_HETEROGENEITY, TEMPORAL, RESISTANCE, SCREENING, SUPPORTIVE_CARE, MECHANISM
- [ ] Seed presets for 5 mutation-site pairs (ESR1/brain, PIK3CA/liver, HER2/bone, KRAS/lung, BRAF/melanoma-brain)
- [ ] Unit tests for `StatisticalEngine` core functions

### Phase 2 — Real data & AI (weeks 4–8)
- [ ] Wire real literature APIs (PubMed, Europe PMC, OpenAlex) into `SciencePaperManager`
- [ ] Add DOI/PMID import + CSV paired-data import
- [ ] Replace simulated extraction with real LLM structured extraction (+ rule fallback)
- [ ] ctDNA discordance calculator screen

### Phase 3 — Registry as a product (weeks 8–12)
- [ ] Public browsable registry with filters (mutation, site, verdict, evidence grade)
- [ ] DOI minting for capsules (Zenodo)
- [ ] Replication/challenge workflow polish (already partially built)
- [ ] Agentic literature pipeline (search → extract → adjudicate → capsule)

### Phase 4 — Trial design & equity (weeks 12–16)
- [ ] Trial-design wizard (power + endpoints + paired template + export)
- [ ] Trial coverage map + diversity metrics
- [ ] Subgroup stratification in anchors
- [ ] Endpoint tagging

---

## 9. What NOT to Build (Scope Guardrails)

- ❌ Treatment recommendations or dosing (SaMD territory)
- ❌ Cancer pain management apps (clinical care)
- ❌ Survivorship care coordination (clinical care)
- ❌ Radiology/imaging AI (incumbents dominate; not your method)
- ❌ Patient-facing symptom trackers (different product)
- ❌ Drug discovery / compound screening (different expertise)

Concordance's defensible value is **claim adjudication + provenance + trial design**. Stay there.

---

## 10. The One-Sentence Strategy

> *"Concordance generalizes its detection-correction engine from ESR1/brain to any mutation-site-assay claim, becomes the citable registry of adjudicated organotropism evidence, and adds an agentic literature pipeline — attacking the evidence-quality and trial-design pain points of metastasis research without drifting into clinical care."*

---

*Roadmap derived from: oncology pain points research, AI/software solutions research, and review of the Concordance codebase (Android + Python worker).*