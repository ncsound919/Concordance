# Innovation Research — Citizen Science & Open Research Platforms

Companion to `ECOSYSTEM_PLAN.md`. Synthesizes features of leading platforms and
translates them into differentiators for the Concordance ecosystem. Goal: become a
leader that genuinely advances research toward cures.

## 1. Platforms studied

### Zooniverse (zooniverse.org)
- **Project Builder**: anyone uploads a dataset (images/video/sound) + workflow +
  tutorial + field guide + talk forum; adds collaborators/researchers/moderators
  with scoped admin rights.
- **Human-in-the-loop classification** at scale; consensus via many independent
  classifications per item; volunteers discover anomalies (Hanny's Voorwerp).
- **Mobile app** supports only certain project types.
- **Community forum** is a first-class research surface, not an afterthought.
- Lesson: lower the barrier to *creating* projects, not just participating; make
  the forum a discovery engine.

### Galaxy Zoo
- Data deluge solved by distributing pattern recognition to humans; human brain
  beats computers for certain visual tasks.
- Volunteers classified 900k galaxies in months; 60+ peer-reviewed papers.
- Community identity ("Zooites") and recognition drive retention.
- Lesson: give contributors a named identity and credit in publications; make
  "first human to see this" moments explicit.

### Open Science Framework / Center for Open Science (osf.io)
- Pre-registration, reproducibility, structured replication protocols.
- Hosted code/data — but **OSF stops accepting code/data submissions Sept 2026**
  (existing public records go read-only early 2027). This is a market gap.
- Preprint servers + unified search.
- Lesson: a platform that hosts + verifies + cites artifacts has an opening right
  now. Add pre-registration of analysis plans (our "capsule before data" idea).

### Zenodo (zenodo.org)
- General-purpose open repository; mints a **DOI** per submission; embargo support;
  GitHub integration; up to 50 GB uploads; community curation.
- 2026 change: records from unaffiliated/unverified users may be de-ranked and
  not indexed by search engines.
- Lesson: combine DOI-style persistent IDs with our RFC3161 timestamp + hash chain;
  make verification/reputation **transparent** rather than opaque de-ranking.

### eBird / iNaturalist
- eBird: checklists, real-time maps, expert vetting, open data, data-quality
  ratings; volunteers get skill feedback.
- iNaturalist: observations, computer-vision ID suggestions, community
  verification (research-grade), taxon pages, projects/collections.
- Lesson: **research-grade** gating via community/expert verification; skill-based
  reputation; open data exports.

### Foldit / Eterna
- Gamified scientific puzzles (protein folding, RNA design) with leaderboards and
  real scientific outcomes; players co-author papers.
- Lesson: gamify *adjudication challenges* — bounties/leaderboards for resolving
  contested claims; credit contributors as co-authors.

### protocols.io
- Versioned, DOI'd methods; reproducibility; collaboration.
- Lesson: version and DOI our analysis protocols/capsules.

### Kaggle
- Datasets, notebooks, competitions, community discussion; compute credits.
- Lesson: host reusable datasets + notebooks; run "challenges" as competitions with
  leaderboards; integrate compute (we already use Kaggle kernels).

### ResearchGate / Mendeley
- Profiles, publications, Q&A, metrics, "who read my work".
- Lesson: researcher profiles with impact metrics and Q&A on claims.

### Figshare / Dataverse
- Figshare: DOIs, embargoes, private sharing links, metrics.
- Dataverse: data citation, versioning, access controls, terms of use,
  (sub)dataverses for teams.
- Lesson: per-team "dataverses"; private sharing links for pre-publication review;
  versioned datasets with citation.

### SciStarter
- Directory of 2,700+ projects; tool library; onboarding.
- Lesson: an onboarding funnel and a project directory; "start here" path for
  new citizen scientists.

## 2. Cross-cutting patterns that win

1. **Persistent, citable artifacts** (DOI / hash / timestamp) — every contribution
   becomes a citable unit.
2. **Verification tiers** (draft → community-verified → research-grade) — quality
   gating, not just volume.
3. **Consensus + bias correction** — many independent judgments + statistical
   adjustment (Galaxy Zoo rotation bias).
4. **Identity & reputation** — named contributors, credit in papers, skill levels.
5. **Low-friction project creation** — upload data + define workflow in minutes.
6. **Community as research surface** — forums where anomalies are found.
7. **Open data exports** — CSV/API dumps with licenses.
8. **Gamification with real stakes** — leaderboards tied to scientific outcomes.
9. **Pre-registration** — lock analysis plan before seeing results.
10. **Mobile field capture + web analysis** — split by capability (we already do).

## 3. Where Concordance can be #1 (differentiators)

Nobody currently combines all of these. Our unique position:

| Differentiator | Why it's rare |
|---|---|
| **Claim adjudication, not just data collection** | Most platforms collect observations; we test whether a claimed difference survives study design. |
| **Detection-correction (blind-spot accounting)** | We correct for assay/site sensitivity — almost no citizen platform does causal correction. |
| **Citable capsules with provenance + RFC3161 timestamp + hash chain** | Stronger than a DOI alone; tamper-evident, time-stamped, chained. |
| **Living claims with quantitative challenges** | Anyone can challenge a claim with new counts; claim updates transparently. Like Wikipedia talk but numerical. |
| **Human-governed ML (anti-collapse guards, append-only revisions, human approval)** | Most platforms have no model governance; we have it built in. |
| **Privacy/PHI-aware by construction** | Data classes (aggregate vs PHI), on-device stats, refusal of off-host PHI. Rare in citizen science. |
| **Voice-first field instrument** | Accessibility + hands-free capture for lab/field. |
| **Integrated literature/evidence ingestion** | PubMed/OpenAlex/EuropePMC/CIViC/cBioPortal/ClinicalTrials/PubChem already wired. |
| **Agent (plain-language) explanations** | Lowers barrier without dumbing down (numbers never recomputed client-side). |
| **Federated publishing** | Share derived capsules/hashes without moving raw data. |

## 4. Innovation roadmap additions (beyond base plan)

### A. Citable, verifiable artifacts
- Mint a **persistent ID** (DOI-like, e.g. `conc:` + hash) for every capsule and
  revision; publish metadata to DataCite/Crossref when funded.
- Combine with **RFC3161 timestamp** + **hash chain** (already have) → tamper-evident
  citable capsule. Market as "stronger than a DOI."
- **Embargo & versioning** (Zenodo/Figshare style) for pre-publication review.
- **Private sharing links** for reviewer access before publish.

### B. Verification tiers & reputation
- Tiers: `draft` → `community-reviewed` → `research-grade` (expert-vetted).
- **Reputation score** from: verified capsules, resolved challenges, peer reviews,
  dataset quality. Transparent (vs Zenodo's opaque de-ranking).
- **Skill levels** (eBird-style) per domain; higher skill → higher weight in
  consensus.
- **Expert verification** path: designated reviewers can promote to research-grade.

### C. Living, self-correcting knowledge
- **Living systematic reviews**: a claim's verdict auto-recomputes as new
  capsules/challenges arrive; subscribers get diffs.
- **Reproducibility score**: count of independent replications/challenges per claim.
- **Challenge bounties**: gamified resolution with leaderboards and co-authorship
  credit (Foldit/Eterna model).
- **Adversarial collaboration**: paired teams with opposing priors; platform
  mediates pre-registered analysis.

### D. Pre-registration & open methods
- **Pre-register an analysis plan** (hypothesis + method + thresholds) before
  running adjudication; hash + timestamp; results compared to plan →
  transparency metric.
- **Versioned protocols** (protocols.io style) for paired-data analysis templates.

### E. Data quality & consensus
- **Consensus classification** for subjective steps (e.g., figure OCR numbers
  confirmation) with bias correction (Galaxy Zoo rotation lesson).
- **Data quality score** per contribution; surface uncertainty in UI.
- **Multiple independent confirmations** required before a capsule becomes
  research-grade.

### F. Knowledge graph & discovery
- **Claim graph**: nodes = claims/capsules, edges = supersedes/challenges/cites;
  interactive explorer on web.
- **Anomaly feed**: surface claims whose verdict flipped, new challenges, new
  research-grade capsules (like Galaxy Zoo anomaly discoveries).
- **Semantic search** across claims, capsules, papers, curated evidence.

### G. Federation & privacy
- **Federated learning/analysis**: teams run analyses locally on PHI; share only
  derived capsules + model deltas. Unique differentiator.
- **On-prem agent mode** stays first-class; ecosystem works with local-only teams.

### H. Community & engagement
- **Project Builder**: let any team create a "study" (dataset + workflow +
  tutorial + discussion) in minutes (Zooniverse lesson).
- **Forum/talk** as first-class research surface; anomalies surface to science team.
- **Contributor credit** in publications; ORCID integration.
- **Onboarding funnel** (SciStarter-style): "start here" path, skill-building.
- **Notifications & digests**: weekly "what changed in your claims."

### I. Interoperability
- **Import/export**: Zenodo/Figshare/OSF/Dataverse connectors; PubMed/OpenAlex
  (already); FHIR for clinical data (future).
- **Export formats**: JSON-LD, RO-Crate, CSV, Parquet; open API.

### J. Governance & ethics
- **Code of conduct** + moderation (Zooniverse moderators with scoped rights).
- **Ethics review checklist** for human-subjects-adjacent data; PHI refusal enforced.
- **License defaults** (CC-BY-4.0 for derived capsules) with per-publication override.
- **Audit log** public for published artifacts.

## 5. "Become a leader" strategy

1. **Own the niche**: "detection-corrected, citable, challengeable claims" — no one
   else does this. Don't compete with Zooniverse on observation collection;
   compete on *claim verification*.
2. **Move fast on the OSF gap**: code/data hosting sunset in 2026 → position
   Concordance as the home for verified, citable, timestamped research capsules
   with provenance.
3. **Publish the method**: write up the capsule + RFC3161 + hash-chain +
   detection-correction approach as a paper; get it cited.
4. **Partner**: CIViC, cBioPortal, ClinicalTrials.gov, OpenAlex already integrated;
   add ORCID, DataCite, and a journal partnership for "verified capsule" articles.
5. **Open the platform**: Project Builder + open API + open data exports →
   community growth and third-party innovation.
6. **Measure what matters**: track papers citing capsules, challenges resolved,
   claims whose verdict changed — publish an annual "ecosystem impact" report.

## 6. Suggested additions to ECOSYSTEM_PLAN.md

- Add "Verification tiers & reputation" to domain model (Tier enum on Capsule/Publication).
- Add "Pre-registration" entity (planned analysis, hash, timestamp, status).
- Add "Project/Study" entity (Zooniverse Project Builder analogue).
- Add ORCID to User; DataCite DOI minting to Publication.
- Add consensus/bias-correction module to backend.
- Add knowledge-graph service + semantic search.
- Add federated analysis mode to agent.

## 7. Open questions (new)

1. Do we mint real DOIs (DataCite) now or use `conc:` IDs until funded?
2. Should challenges be anonymous or attributed (affects reputation + abuse)?
3. Gamification: badges/points vs co-authorship credit — which motivates our
   (more expert) audience?
4. Should we host notebooks/code (filling OSF gap) or stay focused on capsules?
5. Federation: how much raw-data sharing refusal do we enforce vs user choice?