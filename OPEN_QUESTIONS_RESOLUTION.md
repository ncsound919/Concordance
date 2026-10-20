# Open Questions — Operational Recommendations

Resolves every open question across `ECOSYSTEM_PLAN.md`, `INNOVATION_RESEARCH.md`, and
`DEEP_RESEARCH_SYNTHESIS.md` with a recommended decision and rationale. These become
the defaults for Phase 1 unless overridden.

## A. Architecture & platform

### Q1. Auth provider — Cognito vs Auth.js vs Clerk/Supabase
**Recommendation: Auth.js (NextAuth) for Phase 1–3; design for portability to Cognito.**
- Fastest to ship with Next.js App Router; supports email/password + OAuth + credentials;
  open source; no vendor lock-in.
- Store user records in our own Postgres with a `provider`/`providerAccountId` column so a
  later migration to Cognito (or Clerk) is a data transform, not a rewrite.
- **Switch trigger:** move to Cognito when (a) enterprise SSO/SAML is required by a
  partner, or (b) we need AWS-native MFA/compliance attestations. Until then, Auth.js.

### Q2. Backend host — extend oncology-agent vs separate service
**Recommendation: Extend the existing FastAPI oncology-agent (one backend).**
- Reuses all integration logic (anchors, adjudication, correction, OCR, jobs, explain);
  single deploy; one DB; one auth middleware.
- Add new routers under `/api/*` for teams/users/registry/publications/revisions/datasets.
- Avoids duplicating business logic and keeps the "one backend" invariant from the
  Oncology Ecosystem README.

### Q3. Data residency — may PHI go to cloud?
**Recommendation: Keep the existing refusal. PHI never leaves the originating host.**
- Only `aggregate`/`derived` artifacts (capsules, counts, hashes) may be published.
- Aligns with the app's existing agent behavior and research ethics; avoids HIPAA
  covered-entity obligations for the platform.
- Federated mode (research-grade) keeps raw data local and shares only derived
  capsules + model deltas.

### Q4. Moderation model
**Recommendation: Designated reviewers + community flags (not pure voting).**
- Open-participation review research shows pure voting favors well-known authors
  (popularity contest) and can suppress minority views.
- Reviewers are promoted by reputation; community can flag; admins handle abuse.
- Suspensions are reversible and audited.

### Q5. Monetization — BYOK free tier vs hosted paid tier
**Recommendation: BYOK-first free tier now; hosted paid tier for institutions later.**
- Maximizes adoption and keeps costs with users (Kaggle for compute, own cloud for storage).
- Hosted tier (managed agent + storage) offered to institutions/partners in Phase 6+.
- Token treasury (bounties/grants) is a separate, optional funding layer.

### Q6. Quorum to publish to public registry
**Recommendation: Team lead + 1 designated reviewer.**
- Lead alone risks abuse; N reviewers creates a bottleneck that kills momentum.
- Research-grade promotion requires 2 reviewers (higher bar, separate action).

## B. Identity & artifacts

### Q7. Real DOIs now or `conc:` IDs?
**Recommendation: `conc:` persistent IDs now; mint DataCite DOIs when funded/partnered.**
- Avoids upfront DataCite membership cost and metadata-maintenance burden.
- Store artifact metadata in DataCite-compatible schema so the upgrade is a
  registration step, not a data migration.
- Partner with a university library or DataCite member for DOI minting in Phase 5.

### Q8. Challenges — anonymous or attributed?
**Recommendation: Attributed by default, with an optional pseudonym.**
- Reputation and accountability require attribution; open-review research shows
  attribution improves review quality and conflict detection.
- Allow a pseudonym (stable per user) for sensitive challenges to protect juniors
  and whistleblowers; pseudonym still carries reputation.

### Q9. Gamification motivators — badges vs co-authorship?
**Recommendation: Real scientific credit (ORCID + co-authorship) is primary;
badges/points are secondary.**
- Foldit/Eterna evidence: players stayed for years because their work appeared in
  Nature/PNAS with named credit. Badges alone decay.
- Badges drive onboarding and short-term engagement; credit drives retention.

### Q10. Host notebooks/code (fill the OSF gap)?
**Recommendation: No. Stay focused on capsules; provide import/export connectors.**
- Hosting code/data is a different product with different liability and cost
  (and OSF's own sunset shows how hard it is).
- Connectors to GitHub/Zenodo/Figshare/Dataverse let users cite code where it
  already lives; our niche is verified, challengeable claims.

### Q11. Federation enforcement vs user choice
**Recommendation: Enforce PHI refusal; user choice for aggregate data.**
- PHI (data class `phi`) can never be published or routed off-host — enforced,
  not advisory.
- Aggregate/derived data: user chooses public/team/private per publication.
- Federated mode (research-grade) keeps raw PHI local and shares derived capsules.

## C. Token & funding

### Q12. How long off-chain before on-chain, and who decides?
**Recommendation: Stay off-chain until ≥10,000 active users AND a favorable legal
opinion; decision by core team + reputation-weighted community vote.**
- Off-chain points + fiat/crypto treasury deliver bounties and grants now.
- On-chain migration is a one-way, high-stakes move; gate it on both scale and
  legal clearance, not a calendar.

### Q13. Reputation decay for inactive users?
**Recommendation: Soft decay after 12 months inactive, recoverable on return,
never below a floor.**
- Keeps governance power with active contributors; avoids permanent exclusion
  for life events.
- Floor ensures returning users keep baseline standing and a path back.

### Q14. IP terms on sponsored challenges — open or sponsor's choice?
**Recommendation: Sponsor chooses (open / licensed / proprietary), but open
challenges receive a treasury subsidy (e.g., 20% match).**
- Flexibility attracts industry funding; subsidy nudges openness without
  mandating it (which would deter sponsors).
- Terms are locked at challenge creation and shown to participants before they
  contribute.

### Q15. Cosmetic lab items — earned, purchased, or both?
**Recommendation: Both.**
- Earned items reward contribution; purchasable items (with tokens) bootstrap
  token demand and let non-contributors participate in the economy.
- Purchased items are cosmetic only — no gameplay or reputation advantage.

### Q16. Co-author credit — collective or named?
**Recommendation: Named individuals above a contribution threshold + collective
"Concordance participants" for the long tail.**
- Threshold: top contributors to a paper's capsules (e.g., ≥1 research-grade
  capsule or top 20% by verified contribution weight).
- Follows Foldit practice ("Foldit players" collectively, named individuals for
  major contributions).

### Q17. Lab fee to publish (spam deterrence)?
**Recommendation: No mandatory fee. Rate-limit free publishing + require skill-tree
verification; offer optional paid fast-track review.**
- Fees create pay-to-publish risk and exclude low-resource contributors.
- Rate limits + verification gate spam without money; fast-track fee funds
  reviewer compensation.

## D. Cross-cutting defaults

- **Free tier always exists** (no paywall to participate or publish).
- **PHI refusal is non-negotiable** (enforced in code, not policy).
- **Rigor is identical for all users** (only explanation depth differs).
- **Reputation is non-transferable** (soulbound) for governance/consensus weight.
- **All scoring events are audited** and reversible only via new revisions.

## Decision log

| # | Question | Decision | Revisit trigger |
|---|---|---|---|
| 1 | Auth | Auth.js → Cognito later | Enterprise SSO / compliance |
| 2 | Backend | Extend oncology-agent | — |
| 3 | PHI residency | Refuse off-host | — |
| 4 | Moderation | Reviewers + flags | Abuse spike |
| 5 | Monetization | BYOK free + hosted later | Institutional demand |
| 6 | Publish quorum | Lead + 1 reviewer | Abuse / bottleneck |
| 7 | DOIs | `conc:` now, DataCite later | Funding/partnership |
| 8 | Challenges | Attributed + pseudonym | Harassment reports |
| 9 | Motivators | Credit primary, badges secondary | Retention data |
| 10 | Notebooks/code | No; connectors | User demand |
| 11 | Federation | Enforce PHI refusal | — |
| 12 | On-chain timing | ≥10k users + legal opinion | Scale/legal |
| 13 | Reputation decay | Soft decay, 12mo, floor | Governance deadlock |
| 14 | Sponsored IP | Sponsor choice + open subsidy | — |
| 15 | Cosmetics | Both earned & purchased | Economy imbalance |
| 16 | Co-author credit | Named threshold + collective | — |
| 17 | Publish fee | None; rate limit + fast-track | Spam surge |