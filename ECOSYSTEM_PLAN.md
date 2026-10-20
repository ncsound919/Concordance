# Concordance Ecosystem — Plan & Brainstorm

**Status:** brainstorm (no code yet). **Phase goal:** design only.

**Companion docs:** `INNOVATION_RESEARCH.md` (platform comparison & differentiation),
`DEEP_RESEARCH_SYNTHESIS.md` (rigor-for-all, gamification, token funding, partnerships).
**Companion web stack:** Next.js (matches `Oncology-Ecosystem-main/platform`).
**Sharing model:** public registry + teams (team workspaces by default, opt-in publish).

## 1. Vision

Turn the single-user, on-device Android research tool ("Concordance") into a
**citizen-science ecosystem** where:

- Researchers and citizen scientists **compile** claims, run adjudication/correction,
  and mint citable capsules.
- They **share** compiled information and data with the community.
- They **form teams** that curate shared claim registries, challenges, and model
  revisions.
- A **website** (Next.js) is the community hub and the "deep setup" surface —
  login, community browsing, team management, provenance verification, and
  configuration that is too heavy for a phone (BYOK keys, agent endpoints, AWS/S3/R2,
  Kaggle, runtime tuning).
- The Android app stays the **field instrument**: on-device statistics, voice,
  offline-first, privacy-preserving (audio never leaves the phone; PHI never leaves
  host).

The web is not a replacement for the app; it is the **ecosystem layer** the app
plugs into.

## 2. What already exists (reuse, don't fork)

| Capability | Owner | Reuse decision |
|---|---|---|
| Statistical engine, capsules, challenges, model revisions | `Concordance-main` (Kotlin) | Port logic to shared TS/Python lib for web; keep Kotlin as reference |
| Adjudication agent (FastAPI) | `oncology-agent` (self-hosted) | Extend into the ecosystem backend API |
| RFC 3161 timestamping | `platform/scripts/engines/tsa_prove.py`, `platform/lib/timestamp.ts` | **One implementation** — web + app clients call it |
| Hash-chained evidence ledger, trust tiers | `evidence/evidence_gate.py` | Reuse for capsule/claim verification |
| Claim extraction + verification | `apps/research-orchestrator/src/claims.ts` | Reuse |
| Append-only run manifests | `apps/research-orchestrator/src/ledger.ts` | Reuse |
| EvidenceTier (measured/derived/configured/unmeasured) | `apps/oncono-agent-main/server/telemetry.ts` | Reuse — unmeasured never renders as value |
| Next.js platform | `Oncology-Ecosystem-main/platform` | Host the new website here |
| Cloud sync (S3/R2) | `AwsCloudManager`, `CloudflareR2Manager` | Reuse as storage backends |

Rule (from Oncology Ecosystem README): **do not build an eighth** of any of these.
Extend the owner.

## 3. Personas & roles

- **Citizen scientist (member):** runs analyses on phone, publishes capsules/claims,
  joins teams, files challenges.
- **Team lead (curator):** creates team, invites members, approves model-revision
  promotions, manages shared registry visibility.
- **Reviewer/moderator:** verifies capsules, flags assumed parameters, reviews
  challenges, enforces evidence tiers.
- **Admin:** platform ops, abuse handling, spend limits (AWS).

Role is per-team; a user can be lead of one team and member of another.

## 4. Core domain model (ecosystem-wide)

Extend the app's Room entities into a shared schema:

- **User** (id, handle, email, avatar, bio, credentials)
- **Team** (id, name, slug, description, visibility, owner)
- **TeamMembership** (team, user, role, joinedAt)
- **Claim / Anchor** (existing) + `ownerType` (user|team), `visibility`, `version`
- **Capsule** (existing) + `ownerType`, `visibility`, `teamId`, `publishedAt`,
  `timestampToken` (RFC3161), `supersedesCapsuleId`
- **Challenge** (existing) + `authorUserId`, `status`
- **ProvenanceRecord** (existing) — now linked to published capsules
- **ModelRevision** (existing) + `teamId`, `promotedByUserId`
- **LearningAudit** (existing)
- **Dataset** (new): paired CSV / imaging uploads metadata, storage key, data class
  (aggregate | phi), consent flags
- **Publication** (new): a "share event" — what was published, by whom, when, to
  which visibility, with hash + timestamp
- **Comment / Discussion** (new): on claims/capsules (moderated)
- **ActivityFeed** (new): per-user and per-team

Key invariants carried from app:
- Append-only model revisions (rollback = new revision).
- Refusal is a result, not an error.
- EvidenceTier discipline: unmeasured never renders as a value.
- PHI data class refuses off-host routing.

## 5. Data sharing & privacy

Chosen model: **public registry + teams**.

- **Team workspaces** are private by default; members collaborate on claims,
  capsules, challenges, revisions.
- **Publish to public registry** is an explicit, reversible (tombstone, not delete)
  action by owner or team lead; records a `Publication` with hash + RFC3161 token.
- **Data classes:**
  - `aggregate` (counts, ORs): shareable.
  - `phi` (raw patient data): never leaves the originating host; only derived
    capsules/hashes may be published; agent refuses off-host PHI routing (existing).
- **Federated option later:** keep raw data local, share only derived capsules +
  hashes. Design API so a "local-only" team can still publish capsules.
- **Consent & licensing:** each publication carries a license (CC-BY-4.0 default for
  derived capsules), provenance digest, and data-class assertion. Mis-assertion is
  an audit event.

## 6. Teams

- Create team, invite by email, roles (lead/curator/member/viewer).
- Shared claim registry (team-owned anchors).
- Shared capsule library with versioning + supersession.
- Shared model revisions with governance: only leads can promote; all members can
  propose dry-runs; audit log visible to team.
- Team-level settings: visibility default, license default, agent endpoint override.
- Team activity feed + notifications.

## 7. Website (Next.js) — community hub + deep setup

Hosted in `Oncology-Ecosystem-main/platform` (or a new `apps/concordance-web`).

### Public (no login)
- Browse public registry: claims, capsules, challenges, papers.
- Capsule viewer + **verify** (paste JSON / hash → verify signature + timestamp).
- Leaderboard / activity feed of recent publications.
- Docs: how to run the app, regulatory scope.

### Authenticated
- Dashboard: my claims/capsules, teams, notifications.
- Team management UI.
- Deep setup (moved out of phone):
  - BYOK keys (Kaggle, Cloudflare R2, AWS, OncoKB, etc.) stored server-side in
    a secrets manager (AWS Secrets Manager / Parameter Store), injected to agent.
  - Agent endpoint & API key configuration; health check.
  - AWS S3/R2 bucket config, region, sync status.
  - Runtime tuning (CB thresholds, cache TTL, stability threshold).
  - Import/export model revision archives.
- Challenge filing UI with diff preview.
- Deep research runner (currently phone-triggered; move heavy orchestration here).
- Profile & reputation (contributions, verified capsules).

### Admin
- Moderation queue, abuse reports, spend-limit visibility.

## 8. Backend API

Extend the self-hosted `oncology-agent` (FastAPI) into the ecosystem API, or add a
new service in `Oncology-Ecosystem-main/apps/`. Recommendation: **extend the agent**
so there is one backend, with auth middleware added.

- Auth: JWT (issued by Cognito or Auth.js), Bearer token; app already sends
  `Authorization: Bearer <key>` — generalize to user tokens.
- Endpoints (new/extended):
  - `/api/auth/*` (or delegate to Cognito)
  - `/api/teams`, `/api/teams/{id}/members`
  - `/api/users/me`
  - `/api/registry/claims`, `/api/registry/capsules` (public + scoped)
  - `/api/publications` (publish/unpublish)
  - `/api/challenges`
  - `/api/revisions`, `/api/revisions/{id}/promote` (gated by role)
  - `/api/datasets` (upload metadata + presigned S3/R2 URLs)
  - `/api/verify/capsule`
  - Existing: `/api/anchors`, `/api/adjudicate`, `/api/correction`, `/api/imaging/ocr`,
    `/api/jobs/*`, `/api/explain/*`
- Storage: Postgres (metadata) + S3/R2 (capsules, datasets, archives). Reuse
  existing sync managers.
- Realtime: optional WebSocket/SSE for team activity feed and job progress.

## 9. Auth

- **Web:** Cognito User Pool (or Auth.js with email+password + OAuth). Cognito fits
  AWS-native and gives JWTs the app/agent can verify.
- **App:** add a login screen; store refresh token in encrypted prefs (already has
  `KeyStore`/`AgentSettingsStore` pattern). App can still run offline with cached
  identity; publish queues until online.
- **Agent:** verify JWT; keep API-key path for self-hosted single-user mode
  (backward compat).

## 10. App ↔ ecosystem integration

- App becomes an **ecosystem client**: sign in, sync claims/capsules/challenges,
  publish, receive team updates.
- Offline-first: Room DB remains source of truth locally; sync layer reconciles
  with server (last-write-wins per entity + version field; conflicts surface as
  challenges, not silent overwrites).
- Publish flow: user taps "Publish" on a capsule → app uploads capsule JSON + hash
  to API → server records Publication + RFC3161 token → returns public URL.
- Deep setup moves to web; app keeps a read-only "status" view and a link to web.
- Voice/OCR stay on-device/on-host; ecosystem only sees derived artifacts.

## 11. Scientific integrity (non-negotiable)

Carry forward all existing guards:
- Refusal as result (422 → `Refused`), never retry-as-error.
- Easy mode is translation, not simplification (numbers never recomputed client-side).
- Audio never leaves phone.
- OCR is derived, human-unverified until confirmed.
- Anti-collapse guard on learning rounds.
- Human approver required for model promotion; audit every decision (incl. refusals).
- EvidenceTier discipline on web too.
- Provenance digests + RFC3161 tokens on every published capsule.
- Append-only revisions; re-adjudication publishes new capsules, pins originals.

## 12. Infrastructure (AWS, single region)

Per project constraints: single region, no cross-region. Proposed:
- **Compute:** ECS Fargate or Lambda for API; App Runner if available in region.
  (Note: App Runner reduced availability in eu-north-1.)
- **DB:** RDS Postgres (or Aurora Serverless v2).
- **Storage:** S3 (capsules, datasets, archives) + CloudFront for public registry
  assets. Reuse existing bucket naming.
- **Auth:** Cognito User Pool + Identity Pool.
- **Secrets:** AWS Secrets Manager for BYOK keys (encrypted, audited).
- **Observability:** CloudWatch (logs/metrics), existing `CloudWatchLogsManager`.
- **Cost guard:** spend limits; free-tier-first; BYOK so users bring their own
  compute (Kaggle) for heavy jobs.

## 13. Roadmap (phased)

**Phase 0 — Brainstorm & spec (this doc).** ✓
**Phase 1 — Web skeleton + auth.** Next.js app in platform, Cognito, login, profile,
  public registry browse (read-only from static seed).
**Phase 2 — Backend API + DB.** Extend agent; teams, memberships, claims/capsules
  CRUD, publish flow, verification endpoint.
**Phase 3 — App sync.** Login in app, sync layer, publish from phone, conflict surfacing.
**Phase 4 — Team governance.** Revisions/promotion with roles, audit UI, activity feed,
  notifications.
**Phase 5 — Deep setup migration.** Move BYOK/AWS/Kaggle/runtime tuning to web.
**Phase 6 — Community features.** Discussions, reputation, moderation, leaderboards,
  deep research orchestration on web.
**Phase 7 — Hardening.** Load testing, abuse prevention, spend limits, backup/restore,
  data export (GDPR-style).

## 14. Open questions — resolved

All open questions (this list plus those in `INNOVATION_RESEARCH.md` and
`DEEP_RESEARCH_SYNTHESIS.md`) are resolved in `OPEN_QUESTIONS_RESOLUTION.md` with
recommended decisions and revisit triggers. Summary of the headline calls:

1. **Auth:** Auth.js now, portable to Cognito later (enterprise SSO trigger).
2. **Backend:** extend the existing FastAPI oncology-agent (one backend).
3. **PHI residency:** refuse off-host (unchanged); only aggregate/derived in cloud.
4. **Moderation:** designated reviewers + community flags (not pure voting).
5. **Monetization:** BYOK free tier now; hosted paid tier for institutions later.
6. **Publish quorum:** team lead + 1 reviewer (research-grade needs 2).
7. **DOIs:** `conc:` IDs now; DataCite DOIs when funded/partnered.
8. **Challenges:** attributed by default, optional pseudonym.
9. **Motivators:** real co-authorship credit primary; badges secondary.
10. **Notebooks/code:** no hosting; connectors to GitHub/Zenodo/etc.
11. **Federation:** enforce PHI refusal; user choice for aggregate data.
12. **On-chain token:** off-chain until ≥10k users + legal opinion.
13. **Reputation decay:** soft decay after 12 months, recoverable, with floor.
14. **Sponsored IP:** sponsor chooses, open challenges get a treasury subsidy.
15. **Cosmetics:** both earned and purchasable (cosmetic only).
16. **Co-author credit:** named above threshold + collective long tail.
17. **Publish fee:** none; rate limits + skill-tree verification + optional fast-track.

1. **Auth provider:** Cognito (AWS-native, fits single-region) vs Auth.js (simpler,
   self-hosted) vs Clerk/Supabase (faster, external)? Recommendation: Cognito for
   AWS alignment, but Auth.js is faster to ship.
2. **Backend host:** extend the existing `oncology-agent` FastAPI service (one
   backend) vs a separate Next.js API routes / tRPC service? Recommendation:
   extend agent (reuse all integration logic).
3. **Data residency:** should PHI-class datasets be allowed to be uploaded to S3 at
   all (even encrypted), or must they stay on-prem? Current app refuses off-host
   PHI. Recommendation: keep refusal; only aggregate/derived artifacts in cloud.
4. **Moderation:** who moderates the public registry — community voting, designated
   reviewers, or admin-only? Recommendation: designated reviewers + community flags.
5. **Monetization:** free tier + BYOK (users pay their own cloud) vs hosted paid
   tier? Recommendation: BYOK-first, hosted tier later.
6. **Reputation:** should model-revision promotions require N reviewers, or just
   team lead? Recommendation: team lead + 1 reviewer for public registry.

## 15. Suggested next step

If you want to proceed, I recommend starting with **Phase 1 (web skeleton + auth)**
because it unblocks everything else and is the smallest vertical slice that proves
the ecosystem. I can scaffold a Next.js app under `Oncology-Ecosystem-main/apps/concordance-web`
with Cognito auth and a read-only public registry page seeded from the app's
default claims. Want me to do that next?