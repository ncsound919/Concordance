# Resolving Oncology Pain Points Through Software and AI

**Companion to:** `oncology_pain_points_report.md`
**Question:** How can the pain points in modern oncology (breast cancer, neuro-oncology, tumor heterogeneity, mutations/targeted therapy, metastasis) be resolved through software solutions and AI?
**Date:** October 2026

---

## 0. Executive Summary

AI and software cannot cure cancer alone, but they can attack the **information, speed, scale, and equity bottlenecks** that make oncology pain points so stubborn:

- **Heterogeneity/resistance** → multimodal AI that integrates imaging + pathology + genomics + clinical data to stratify patients, predict resistance, and guide adaptive therapy.
- **Metastasis** → AI detection/segmentation of metastases, risk prediction from multi-omics, and anti-metastatic drug discovery.
- **Breast cancer inequities** → AI screening that raises detection in dense breasts, risk models from routine imaging, and community-facing navigation tools.
- **Neuro-oncology** → FDA-cleared AI volumetrics/segmentation, surgical planning AI, and molecular classification support.
- **Mutations/targeted therapy** → AI variant interpretation, mutation-to-therapy matching, and trial matching at scale.
- **Cross-cutting** → workflow automation (44% screen-reading workload reduction), clinical decision support, survivorship/PRO capture, and agentic AI systems (AACR 2026).

Market context: AI-in-oncology market ~$2.35B (2025) → ~$10.25B (2030), CAGR ~34.5%. The field is shifting from standalone models to **integrated, agentic, multi-modal systems** (AACR 2026).

---

## 1. The AI/Software Landscape in Oncology (Pillars)

| Pillar | What it does | Examples from research |
|---|---|---|
| **Data integration platforms** | Unify EHR, imaging, omics, trials | Synapse (AACR 2026), NCI Genomic Data Commons, Cancer Imaging Archive |
| **Imaging AI** | Detection, segmentation, volumetrics, risk | Lunit, iCAD ProFound, Hologic Genius AI, NeuroQuant Brain Tumor, TumorTrace AI, MetAssist 2.0 |
| **Digital pathology AI** | Metastasis detection, grading, spatial heterogeneity | Visiopharm Metastasis Detection, MetAssist 2.0 |
| **Genomic/precision-oncology platforms** | Variant calling, VUS interpretation, therapy matching | GenomOncology, Tempus, MutationProjector (UCSD), ThinkBio TheraBluePrint |
| **Clinical decision support / trial matching** | Match mutations to therapies/trials | Tempus, GenomOncology, "oncologist's assistant" (Damon Runyon) |
| **Workflow/EMR AI** | Reduce admin burden, automate documentation | athenaOne, DoctorsApp oncology EMR |
| **Patient-facing / survivorship** | PRO capture, navigation, symptom management | (emerging; less mature) |
| **Agentic AI systems** | Multi-agent orchestration across the above | AACR 2026 platforms (conversational + multi-agent tools) |

---

## 2. Pain Point → Software/AI Resolution Map

### 2.1 Breast cancer

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| Dense-breast screening gaps / interval cancers | AI mammography (Lunit INSIGHT MMG, iCAD ProFound, Hologic Genius AI) augments detection, especially in dense breasts | Lunit: supports earlier detection in dense breasts; iCAD: rapid analysis of 2D/3D mammograms |
| Radiologist workload | AI as concurrent/second reader reduces screen-reading workload by ~44% while maintaining/increasing detection | Hernstrom 2025 RCT: 44.2% workload reduction; proportion ratio invasive cancer 1.24 |
| Risk prediction before symptoms | AI predicts breast cancer risk from normal mammograms (AUC up to 0.90) and polygenic risk scores | Yala 2021: C-index 0.81, AUC 0.90; Arefan 2020: AUC 0.73 |
| Recurrence risk assessment | FDA-cleared **TumorSight Risk**: MRI-derived AI assessing long-term recurrence risk from MRI + clinical + pathology | FDA 510(k) cleared (2026) |
| Mortality inequities (Black women) | AI risk stratification + community navigation (BLACC-style) + diverse training data + satellite telehealth | Stanford: lay health navigators; AI must be trained on diverse cohorts to avoid amplifying bias |
| TNBC / hereditary subtypes | Mutation-matched therapy platforms; ctDNA-guided de-escalation/intensification trials | Stanford ctDNA trials; Tempus mutation-to-trial matching |
| Survivorship burden | PRO capture, survivorship care coordination, late-effects (cardiovascular) risk models | Riaz 2025 review: AI for survivorship tailoring |

### 2.2 Neuro-oncology

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| >100 CNS tumor entities / diagnostic complexity | AI-assisted classification integrated with WHO CNS molecular criteria; radiomics for grading | Nature AI-in-neuro-oncology review (s41698-024-00575-0) |
| Non-curative surgery / extent of resection | AI segmentation/volumetrics (NeuroQuant Brain Tumor, FDA-cleared) for objective tumor change quantification pre/post-treatment | Cortechs.ai: automates segmentation and volumetric analysis of high/low-grade diffuse gliomas |
| Longitudinal monitoring subjectivity | Automated, reproducible tumor tracking across time (TumorTrace AI) | TumorTrace AI: precision tracking across time |
| Surgical planning in eloquent areas | Multimodal imaging AI + DTI tractography integration; AI reads scans to predict aggressiveness pre-surgery | Nature (2026): "AI Reads Brain Scans to Predict Tumor Aggressiveness Before Surgery" |
| BBB-limited drug delivery | AI-optimized focused ultrasound targeting; nanomedicine design via ML | Delaidelli 2024: focused ultrasound + BBB disruption |
| Clinical trial roadblocks | AI trial matching by molecular profile; federated data sharing | Nature AI review; oncodaily Tempus trial matching |

### 2.3 Tumor heterogeneity

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| Single-biopsy inadequacy / spatial heterogeneity | Spatial transcriptomics + single-cell omics analysis pipelines; digital pathology for tumor microenvironment mapping | PMC13036062: AI for precision oncology phenotyping; Visiopharm spatial analysis |
| Temporal evolution / resistance | Liquid biopsy (ctDNA) + AI for real-time resistance mutation detection; longitudinal monitoring dashboards | MDPI 2026: AI in tumor evolution; ScienceDirect 2025: AI-powered precision |
| Multimodal data silos | Multimodal AI integrating WSI histopathology, radiology, bulk/scRNA-seq, spatial transcriptomics, clinical variables | PMC13036062 schematic; Nature Cancer "Hallmarks of AI contributions" (10 hallmarks) |
| Adaptive therapy design | AI models predicting response and recommending dose/modality adjustments; digital twins | Frontiers 2026: AI + anti-cancer drugs |
| Clonal evolution modeling | Deep learning on single-cell data to model tumor evolution trajectories | MDPI 2026: AI in tumor evolution |

### 2.4 Mutations and targeted therapy

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| Variant interpretation / VUS | AI platforms for somatic variant calling and clinical significance (GenomOncology, GDC tools) | GDC & AI page; GenomOncology precision platform |
| Mutation-to-therapy matching at scale | AI "oncologist's assistant" matching mutations to 100+ approved targeted therapies and trials | Damon Runyon: AI-based assistant (2026); Tempus platform |
| Predicting treatment response from mutations | **MutationProjector** (UCSD): trained on 30,000+ tumor samples, connects mutations to predicted therapy outcomes | UCSD Ideker Lab (2026) |
| Undruggable targets / drug discovery | AI for target identification, drug design, degrader (PROTAC) discovery; virtual screening | Frontiers 2026: AI in anti-cancer drug pipeline; ThinkBio TheraBluePrint |
| Resistance monitoring | Serial liquid biopsy + AI to detect emerging resistance mutations in real time | Nature Cancer hallmarks: response biomarkers |
| Context-dependent efficacy (BRAF in CRC) | AI multi-omics models integrating tumor context, not just mutation presence | Nature Cancer: treatment outcome prediction |
| Trial matching by mutation | AI platforms identifying trials likely to benefit specific patients | Tempus: mutation DB + outcomes → trial matching |

### 2.5 Metastasis

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| Metastasis detection workload | AI pathology: Visiopharm Metastasis Detection (lymph nodes), MetAssist 2.0 (generalizable across 7 cancer types, 14 cohorts, 8,144 slides) | Visiopharm; MetAssist 2.0 (2026) |
| Radiology metastasis detection | Deep learning on imaging with sensitivity/specificity comparable to or better than clinicians | Lancet eClinicalMedicine (2020): AI performance in detecting tumor metastasis from medical imaging |
| Predicting future metastasis | **EmitGCL** deep-learning framework predicting metastasis and biomarkers from single-cell data | Nature Communications (2026) |
| Multimodal metastasis risk prediction | Multimodal AI integrating imaging + pathology + genomics + clinical for metastasis risk & survival | PMC10203594; ResearchGate AI metastasis risk review (2024) |
| Anti-metastatic drug discovery | ML for toxicity prediction, drug repositioning, virtual screening, bioactivity prediction | ScienceDirect 2023: ML in metastatic cancer research |
| CTC/cfDNA analysis | AI-driven analysis of circulating tumor cells and cell-free DNA for MRD and resistance | (emerging; referenced in metastasis AI literature) |

### 2.6 Cross-cutting pain points

| Pain point | Software/AI solution | Evidence |
|---|---|---|
| Cancer pain undertreatment | AI prognostic modeling for pain risk; symptom-management decision support; guideline-embedding in CDS | Riaz 2025: AI for supportive care; NCCN guideline digitization |
| Access/cost/reimbursement pressure | Workflow automation (44% reading workload reduction); cloud-based SaaS platforms; telehealth integration | Expert Market Research: cloud-based software trend; athenaOne |
| Early detection delays | AI risk models from EMR (lung cancer AUC 0.90); pancreatic cancer from disease trajectories (AUC 0.88); AI colonoscopy adenoma detection (39.9% vs 32.4%) | Riaz 2025 Table 1 (Yeh 2021, Placido 2023, Xu 2023) |
| Survivorship coordination gaps | PRO capture, survivorship care plans, late-effects risk models, navigation apps | Riaz 2025: AI for survivorship and EOL care |
| Evidence dissemination lag | AI-assisted literature synthesis, guideline summarization, tumor-board decision support | Riaz 2025: AI streamlines dissemination of up-to-date evidence |
| Data fragmentation | Federated learning, common data models (OMOP/FHIR), platforms like Synapse (AACR 2026) | AACR 2026: Synapse for large-scale data coordination |

---

## 3. The Ten AI Hallmarks in Precision Oncology (Nature Cancer, 2025)

Organized into three groups — a useful framework for builders:

1. **Cancer prevention & diagnosis:** screening, detection, profiling
2. **Optimizing current treatments:** outcome prediction, treatment planning/monitoring, clinical trial design & matching, response biomarkers
3. **Advancing new treatments:** treatment combinations, discovering cancer vulnerabilities, drug design

Plus AI in interventional clinical trials. Key challenges to adoption: **data quality/quantity, model accuracy, clinical relevance, patient benefit** — with actionable solutions for each.

---

## 4. The 2026 Shift: From Standalone Models to Agentic Systems

AACR 2026 highlights a paradigm shift:
- **Synapse** platform enables large-scale data coordination → interoperable, reproducible research.
- **Conversational and multi-agent AI tools** orchestrate across imaging, genomics, EHR, and trials.
- Implication: the next generation of oncology software should be **agentic** — agents that can plan, retrieve, reason, and act across tools (e.g., an agent that pulls a patient's mutation profile, matches therapies, finds trials, drafts a tumor-board summary, and flags resistance risk).

---

## 5. Challenges to AI/Software Adoption (Be Honest)

1. **Validation & generalization:** models trained on narrow cohorts fail elsewhere; need external validation and diverse training data (or disparities worsen).
2. **Data quality/quantity:** missing, biased, or non-interoperable data; EHR noise; imaging protocol variation.
3. **Regulatory:** FDA 510(k)/PMA pathways for SaMD; need clear intended-use and performance claims.
4. **Workflow integration:** tools that don't fit tumor-board/clinic workflows won't be used; need EHR/PACS integration.
5. **Explainability:** black-box models reduce trust; need interpretable outputs and uncertainty quantification.
6. **Privacy/security:** genomic and imaging data are sensitive; federated learning and strict governance required.
7. **Human-in-the-loop:** AI augments, not replaces, clinicians; liability and oversight frameworks needed.
8. **Equity:** if AI is trained on non-representative data, it amplifies existing disparities (e.g., Black women's mortality).

---

## 6. Actionable Roadmap for Building Oncology Software/AI

If you're building solutions, prioritize:

1. **Interoperability first** — FHIR/OMOP, PACS/DICOM, genomic VCF; design for data ingestion from day one.
2. **Multimodal by design** — fuse imaging + pathology + genomics + clinical + PROs (the highest-value AI is multimodal).
3. **Diverse, representative cohorts** — stratify validation by race/ethnicity, age, geography, tumor subtype.
4. **Clinical workflow fit** — embed in EHR/tumor board; minimize clicks; return actionable, explainable outputs.
5. **Longitudinal & adaptive** — support serial monitoring (ctDNA, imaging) and adaptive therapy, not just one-shot prediction.
6. **Human-in-the-loop + audit trails** — clinician override, provenance, and performance monitoring in production.
7. **Equity & access** — pricing/community deployment plans; partner with community oncology and navigators.
8. **Regulatory & evidence plan** — early FDA engagement; prospective validation; publish performance by subgroup.
9. **Agentic architecture** — design agents that can orchestrate across data sources and tools (the 2026 direction).

---

## 7. Sources

1. Riaz IB, Khan MA, Osterman TJ. *Artificial intelligence across the cancer care continuum.* Cancer. 2025. PMC12351523.
2. Chang TG et al. *Hallmarks of artificial intelligence contributions to precision oncology.* Nature Cancer. 2025.
3. AACR 2026 meeting coverage: emerging AI advances in oncology (agentic systems, Synapse). Springer 2026.
4. Nature: *Artificial intelligence in neuro-oncology: advances and challenges* (s41698-024-00575-0).
5. ScienceDirect/PMC: AI in tumor heterogeneity; multimodal AI in oncology (PMC13036062, PMC13547827).
6. DuckDuckGo search results: AI oncology software platforms, AI breast cancer screening (Lunit, iCAD, Hologic), AI metastasis detection (Visiopharm, MetAssist 2.0, EmitGCL), AI mutation matching (GenomOncology, Tempus, MutationProjector), neuro-oncology AI (NeuroQuant, TumorTrace AI).
7. NCI GDC & AI; NCI AI in Cancer Research Resources.
8. Expert Market Research / Fortune Business Insights: AI in oncology market size and trends.

---

*This report is a research overview of software/AI approaches to oncology pain points, not clinical guidance. Specific tools mentioned are examples from the literature and should be evaluated for regulatory status and local fit before adoption.*