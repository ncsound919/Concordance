# Deep Research Synthesis — Rigor for All, Gamified Science, Token Funding, Partnerships

Companion to `ECOSYSTEM_PLAN.md` and `INNOVATION_RESEARCH.md`. Focuses on the four
requirements that must define the ecosystem:

1. **Same rigor for deep scientists and laypeople** — experts and citizens use the
   *same* engine and the *same* numbers; citizens get more explanation and a
   learning path, never a simplified or softened result.
2. **Gamification that feels like being a scientist** — a personal lab, team
   building, progression, quests, and real scientific credit.
3. **A decentralized token that helps obtain funding** — without becoming a
   securities/regulatory or plutocracy problem.
4. **Academic and industrial integration and partnerships** — so the ecosystem
   feeds and is fed by real research and real money.

---

## 1. One rigor, two experiences (progressive disclosure)

**Principle:** The engine is the single source of truth. There is no "easy answer."
Easy mode (already in the app) is a *translation*, not a simplification — numbers
are reformatted, never recomputed. We extend this into a full **learning layer**.

- **Expert view:** raw counts, parameters, decision traces, model revision diffs,
  provenance digests, sensitivity curves, power analyses. Full control.
- **Citizen view:** the same result, rendered as plain-language verdict + "what
  we did" + "what it does not mean" + glossary + "next steps," with every number
  clickable to reveal its source and a short explainer.
- **Progressive disclosure:** each concept has three depths — *headline* →
  *why this number* → *full method + references*. The citizen climbs the ladder
  at their own pace; the numbers never change.
- **Skill tree / tutorials (Eterna-style):** gated learning modules ("What is an
  odds ratio?", "What does r* mean?", "How to read a capsule") that unlock
  features (e.g., filing challenges, publishing) only after completion. This
  protects rigor: a user cannot publish or challenge until they demonstrate
  competence, but the bar is learnable, not credential-gated.
- **Adaptive scaffolding, not adaptive answers:** the system adjusts *explanation
  depth* and *hints* based on skill level, never the underlying computation or
  verdict.
- **Challenge-as-learning:** filing a challenge requires proposing updated counts
  and a justification; the system shows how the verdict would change, teaching
  causal reasoning. (Already: `challengeClaim` recomputes adjudication.)

**Why this makes us a leader:** most platforms either dumb down results for
volunteers (losing rigor) or exclude non-experts (losing scale). Doing both at
once is the differentiator.

---

## 2. Gamification — "your lab, your team, your science"

Draw from Foldit/Eterna/Eyewire (scores, leaderboards, groups, badges, real
scientific credit) and from lab-management games, but tie rewards to *verified
scientific outcomes*, not activity volume.

### 2.1 The Lab (personal)
- **Lab space:** a visual workspace — benches for claims, a vault for capsules, a
  whiteboard for models, a notebook (audit log), a trophy wall (verified
  capsules), a library (papers/evidence).
- **Equipment unlocks:** as skill rises, unlock tools (paired-data analysis,
  imaging OCR, deep research, model revisions). Cosmetics (bench skins, avatar
  gear) purchasable with tokens.
- **Daily/weekly quests:** "Resolve a contested claim," "Replicate a capsule,"
  "Pre-register an analysis," "Review 3 community capsules."
- **Streaks & milestones:** contribution streaks, "first verified capsule,"
  "first challenge that changed a verdict."

### 2.2 Teams (research groups)
- **Create/join groups** with roles (PI/lead, curator, researcher, reviewer) —
  Zooniverse Project Builder model: scoped admin rights.
- **Shared lab** with team benches, shared claim registry, shared model
  revisions, team leaderboard.
- **Group quests & raids:** team challenges that require multiple members
  (e.g., "replicate 5 capsules this month") with shared rewards.
- **Mentorship:** senior members can "sponsor" juniors; reputation transfers
  partially.

### 2.3 Progression & reputation
- **Ranks/titles** gated by *verified contributions*, not points alone:
  Novice → Contributor → Researcher → Senior Researcher → Lab Lead → PI.
- **Reputation is non-transferable** (soulbound-style) and drives: consensus
  weight in community verification, ability to promote model revisions, access to
  research-grade publishing.
- **Skill levels per domain** (eBird-style): a user can be expert in
  "paired-data analysis" but novice in "imaging OCR."
- **Real credit:** ORCID linkage; contributors listed as "Concordance
  participants" on papers; individual authorship for major contributions
  (Foldit players are co-authors). This is the strongest retention driver.

### 2.4 Leaderboards & recognition
- Individual, team, and institution leaderboards: "most impactful capsule,"
  "most challenges resolved," "most verifications," "breakthrough score."
- **Impact metrics**, not volume metrics: a capsule that changes a claim's
  verdict or gets cited counts more than raw uploads.
- **Anomaly discoveries** highlighted (Galaxy Zoo "Hanny's Voorwerp" moment) —
  the platform surfaces and celebrates serendipitous finds.

### 2.5 Anti-gaming safeguards
- Rewards tied to *research-grade* outcomes and downstream impact, not clicks.
- Consensus + expert verification required before a contribution counts.
- Rate limits and diminishing returns on repetitive low-value actions.
- Audit log of all scoring events.

---

## 3. Decentralized token for funding

**Goal:** a token that helps obtain *and direct* funding for research, without
becoming an unregistered security or a plutocracy.

### 3.1 Two-layer design
- **Layer 1 — Reputation (off-chain, non-transferable):** "Concordance Points"
  (soulbound). Earned by verified contributions. Drives governance weight,
  feature unlocks, consensus weight. Never sold, never transferred. This alone
  avoids securities classification and plutocracy.
- **Layer 2 — Funding token (on-chain, optional, later):** "LAB" token, only
  introduced after legal review. Used to: fund bounties, tip contributors,
  stake to propose challenges, access premium compute/data, participate in
  treasury governance. Treated as a *utility + governance* token with a legal
  opinion; if regulators disagree, keep it off-chain or restrict to accredited
  participants.

### 3.2 Start off-chain (recommended)
- Begin with off-chain points + a simple treasury (fiat/crypto donations, grants).
- This delivers 90% of the value (funding flow, bounties, tips) with near-zero
  regulatory risk.
- Bridge to on-chain only when: (a) legal opinion obtained, (b) community
  large enough, (c) treasury meaningful.

### 3.3 Funding mechanics
- **Bounties:** industry/academia posts a funded challenge ("resolve this
  contested claim," "replicate this capsule"); payout on verification.
- **Grants:** quadratic-funding rounds (Gitcoin style) where small donors'
  contributions are matched from a treasury pool — amplifies grassroots support.
- **Milestone funding:** DAO-style milestone releases for research programs,
  but with a legal wrapper (Wyoming DAO LLC or nonprofit) and human oversight.
- **Tipping:** users tip capsules/contributions; tips pool to the contributor.
- **Sponsored labs:** institutions fund a "lab" (team) for a period.
- **Data/compute marketplace:** optional paid access to premium datasets or
  compute credits, revenue to treasury.

### 3.4 Safeguards against known DAO failures
- **No plutocracy:** voting power from non-transferable reputation, not token
  holdings; token holders may vote on treasury allocation but not on scientific
  verdicts.
- **Concentration limits:** cap any single address's voting weight.
- **Inactive-voter problem:** delegate voting; quorum based on active reputation
  holders.
- **Security:** contracts audited; bug bounties; upgrade via multi-sig + timelock;
  no "drainable" fund patterns (learn from The DAO hack).
- **Legal:** counsel before any token sale; restrict to compliant jurisdictions;
  consider a nonprofit + tokenless start.

### 3.5 What the token is NOT
- Not a claim on scientific results, IP, or revenue (that's a security).
- Not required to participate (free tier always exists).
- Not a trading vehicle for speculation (design for utility + funding).

---

## 4. Academic & industrial integration

### 4.1 Academic
- **ORCID** identity; link publications to capsules (DataCite DOI).
- **Course integration:** SciStarter-style onboarding; instructors create
  "class labs" with graded quests; students earn reputation + credit.
- **Dataset donation:** universities donate datasets; donors get recognition +
  a "data steward" badge.
- **Pre-registration registry:** partner with OSF/AsPredicted-style registries;
  our capsules can be a pre-registration artifact.
- **Journal partnerships:** a "verified capsule" article type; reviewers can
  request capsule verification as a condition of review.
- **Ethics/IRB templates:** provide templates for human-subjects-adjacent work;
  enforce PHI refusal (existing).

### 4.2 Industrial
- **Sponsored challenges (Eterna biomedical model):** pharma/biotech funds a
  challenge on a specific target (e.g., "replicate this oncology claim,"
  "find challenges to this capsule"); community solves; IP terms defined upfront
  (open / licensed / proprietary with revenue share).
- **Data partnerships:** license aggregated, de-identified, consented datasets
  to industry with revenue to treasury + contributors.
- **Compute partnerships:** Kaggle (existing) + cloud providers for heavy jobs;
  industry donates compute credits.
- **Validation partnerships:** contract research orgs (CROs) validate
  research-grade capsules in wet lab; results feed back as new capsules.
- **Talent pipeline:** companies recruit from top reputation holders (with
  consent).

### 4.3 Standards & interoperability
- **Identity:** ORCID, CRediT (contributor roles).
- **Artifacts:** DataCite DOI, RO-Crate, PROV-O, schema.org, JSON-LD.
- **Clinical data:** FHIR (future, consented).
- **API:** open REST + GraphQL; webhooks for integrations.
- **Export:** CSV, Parquet, JSON; open data dumps with licenses.

---

## 5. How it comes together (the loop)

```
Layperson or expert opens app/web
        │
        ▼
   Same engine, same numbers
        │
   Citizen sees plain-language + learning path; expert sees full detail
        │
        ▼
   Contributes: claim, capsule, challenge, review, dataset
        │
        ▼
   Reputation (soulbound) + tokens (if enabled) accrue
        │
        ▼
   Verification tiers: draft → community → research-grade
        │
        ▼
   Published capsule (hash + RFC3161 + DOI) → cited in papers
        │
        ▼
   Industry/academia funds bounties & grants via treasury
        │
        ▼
   New challenges → new claims → loop continues
```

Everyone plays the same game with the same rules; the only difference is how
much explanation they want on screen. Funding flows to wherever verified
science happens.

---

## 6. Risks & mitigations (consolidated)

| Risk | Mitigation |
|---|---|
| Gamification gaming metrics | Reward verified outcomes, not volume; consensus + expert gate |
| Token as unregistered security | Start off-chain; legal opinion before on-chain; utility framing |
| Plutocracy | Non-transferable reputation for governance; concentration caps |
| Data quality | Consensus + bias correction + expert verification + EvidenceTier |
| Excluding non-experts | Learnable skill tree instead of credential gates; voice-first app |
| PHI leakage | Data classes + refusal of off-host PHI (existing); federated mode |
| Regulatory overreach (clinical) | "Research tool, not clinical decision support" disclaimer (existing) |
| Security (DAO hacks) | Audited contracts, multi-sig, timelocks, no drainable patterns |

---

## 7. Additions to ECOSYSTEM_PLAN.md

- Domain model: add `Reputation`, `Skill`, `Lab`, `Quest`, `Bounty`, `Grant`,
  `PreRegistration`, `Project/Study` entities.
- Add "Learning layer" as a first-class subsystem (tutorials, skill tree,
  progressive disclosure), not a UI afterthought.
- Add "Token treasury" service with off-chain-first design.
- Add "Sponsor" role (industry/academia) and "Sponsored challenge" flow.
- Add ORCID + DataCite integration to User/Publication.
- Add "Impact metrics" to leaderboard/verification.

## 8. Open questions (new)

1. Off-chain-first token: how long before we even consider on-chain, and who
   decides? (Recommend: not until ≥10k active users + legal opinion.)
2. Reputation decay: should inactive reputation decay to keep governance live?
3. IP on sponsored challenges: open-by-default or sponsor's choice? (Recommend:
   sponsor chooses, but open gets a treasury subsidy.)
4. Should cosmetic lab items be purchasable with tokens, or only earned?
   (Recommend: both, to bootstrap token demand.)
5. How do we credit citizen co-authors on papers — "Concordance participants"
   collective, or named individuals above a contribution threshold?
6. Should there be a "lab fee" (small token cost to publish) to deter spam,
   waived for verified contributors?