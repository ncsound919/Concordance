# Concordance hardening — implementation notes (2026-10-19)

This file records the changes made in response to the audit. The audit itself is
in `concordance-audit-2026-10-19.md`.

## 1. Hard-coded secrets removed

### Android

- Deleted the `BuildConfig.DEBUG` seed block in
  `app/src/main/java/com/example/concordance/ui/ConcordanceViewModel.kt` that
  contained the AWS access key/secret, Kaggle token and Cloudflare R2 token.
- Replaced the account-specific default bucket/region (`concordance-capsules-006514117590`,
  `us-east-2`) with neutral defaults (`concordance-capsules`, `us-east-1`).
- Enabled R8 for release (`isMinifyEnabled = true`, `isShrinkResources = true`)
  so future accidental string constants are stripped.
- Verified the rebuilt debug APK contains **none** of the four credentials.

### Website

- Replaced the real service-role key in `.env.example` with a placeholder and
  added the new timestamp/engine variables.
- `.env.local` remains the local, gitignored secret store; **rotate the key**
  because the old value was present in the workspace and in the audit.
- Removed the unused NextAuth credentials stack (`lib/auth.ts`, `lib/users.ts`,
  `app/api/auth/[...nextauth]`) and its `next-auth`/`bcryptjs` dependencies.
- The login page no longer pre-fills demo credentials.

**Action still required:** rotate the AWS, Kaggle, Cloudflare and Supabase
service-role credentials. Removing them from source does not invalidate them.

## 2. Canonical RFC 3161 path integrated

- Added `lib/timestamp.ts`: a thin, fail-closed client for the canonical engine
  `platform/scripts/engines/tsa_prove.py`. It does not reimplement the crypto.
- `POST /api/publications` now:
  - loads the artifact through the user-scoped client (RLS enforced),
  - checks ownership/team/reviewer permission,
  - computes the content hash server-side,
  - for capsules, reproduces Android's canonical body and rejects a mismatched
    `sha256`,
  - requests a timestamp from the canonical engine,
  - stores the token (base64) in `publications.timestamp_token` and
    `capsules.timestamp_token`,
  - fails closed with `503` if the timestamp is unavailable
    (`CONCORDANCE_REQUIRE_TIMESTAMP=1`; set `0` only for local development).
- `POST /api/verify/capsule` now:
  - loads the capsule under RLS (private capsules require the owner),
  - recomputes the canonical capsule hash from `rawJson`,
  - compares both the payload's declared hash and the stored hash,
  - verifies the stored RFC 3161 token through the canonical engine,
  - returns `verified: true` **only** when the hash matches and the timestamp
    verifies; a digest is no longer reported as a timestamp.
- Updated the home-page copy to describe the actual guarantee.
- Updated the registry to show "RFC 3161 token present" vs "not timestamped".

**Set up and exercised.** A `platform/.venv` was created, `rfc3161ng` installed,
and `platform/scripts/engines/freetsa-cacert.pem` downloaded. A real
stamp + verify round-trip against `https://freetsa.org/tsr` succeeded. If any
component is missing on another machine, publication is refused (fail-closed).
Configure:

```env
CONCORDANCE_PLATFORM_ROOT=../../platform
CONCORDANCE_PLATFORM_PYTHON=
CONCORDANCE_TSA_URL=https://freetsa.org/tsr
CONCORDANCE_REQUIRE_TIMESTAMP=1
```

## 3. Roles and authorization

### Platform roles

`citizen < researcher < lead < reviewer < admin`.

- `lib/authz.ts` is the single authorization helper:
  - `getAuthContext()` returns the user, profile and role.
  - `requireUser()`, `requireRole()` throw typed `ApiError`s.
  - `teamRoleFor()`, `assertTeamRole()`, `assertCanPublish()` enforce team and
    publication rules.
- Enforced in:
  - `/moderation` page + middleware (reviewer/admin only),
  - `/api/moderation` GET/PATCH (reviewer/admin only),
  - `/api/teams/[id]/members` POST/DELETE (lead/curator; only a lead can mint
    another lead),
  - `/api/publications` (owner, team lead/curator, reviewer for non-private
    records, or admin),
  - `/api/teams`, `/api/claims`, `/api/capsules`, `/api/challenges`,
    `/api/comments`, `/api/flags`, `/api/settings`, `/api/profile`,
    `/api/notifications` now use the user-scoped Supabase client so RLS is the
    final authority.
- Added `scripts/set-role.mjs` to promote the first admin:
  `node scripts/set-role.mjs you@example.com admin`.
- Added a closed role vocabulary constraint and a trigger that blocks
  self-promotion / reputation tampering.

## 4. RLS fixed

New migration: `supabase/migrations/0004_security_roles_timestamps.sql`.

It is additive and covers:

- **Schema parity:** `version` on claims/capsules/challenges, `data_class` on
  claims/capsules, `timestamp_token` on capsules.
- **Role guards:** `profiles.role` constraint; `platform_role()` helper;
  triggers that prevent a non-admin from changing `role` or `reputation`.
- **Real isolation policies** replacing `using (true)`:
  - claims/capsules: public, owner, team member, or reviewer/admin read;
    owner/team lead/admin write; insert requires `owner_id = auth.uid()` and
    `data_class = 'aggregate'`.
  - challenges: author or claim-readable; insert gated by `can_read_claim`.
  - publications: public/publisher/reviewer; insert requires
    `publisher_id = auth.uid()`.
  - teams/memberships: public/owner/member read; owner/lead write; membership
    insert gated by lead/curator or team ownership.
  - comments/flags: readable only when the underlying artifact is readable;
    flags reviewer/admin only.
  - notifications/settings/profile: self-scoped.
  - provenance/revisions/audit/reputation: read policies added.
- **Atomic counter:** `increment_challenge_count(claim_id)` replaces the
  read-modify-write in the API.
- **Optimistic concurrency:** `bump_version` triggers on claims/capsules/
  challenges.
- **Indexes** on owner/team/visibility/claim/publisher/membership columns.

**Applied.** The migration was executed against the configured Supabase project
(`ktphbqvoycurlyxieurv`) through the Supabase Management API. Verified in
`information_schema` and `pg_policies`:

- `claims.version`, `claims.data_class`
- `capsules.version`, `capsules.data_class`, `capsules.timestamp_token`
- `challenges.version`
- the `claims_*`, `capsules_*`, `challenges_*`, `publications_*`, `teams_*`,
  `team_memberships_*`, `comments_*`, `flags_*`, `provenance_*`,
  `model_revisions_*`, `learning_audit_*`, `reputation_events_*` policies

## 5. Demo data removed

### Website

- Deleted `lib/seed.ts`, `scripts/seed.mjs`, `scripts/create-users.mjs`.
- The registry no longer falls back to seeded demo records.
- Removed the demo accounts and prefilled credentials from the login page.
- Added `scripts/remove-demo.mjs` and ran it against the configured Supabase
  project. Result:
  - `claims: 0`
  - `capsules: 0`
  - `publications: 0`
  - `users: 0`
- The public registry is now empty until real work is published.

### Android

- Removed the invented `current_oncology_2026_case` anchor (the 42/200 counts
  had no source).
- Removed the hard-coded benchmark-paper corpus (`SciencePaperManager`); papers
  now come only from live sources.
- Renamed the "Small Local Model"/"Gemini" options to an honest
  "On-device rule extractor" / "Cloud model (not configured)"; the cloud option
  falls back locally and says so.
- Kaggle kernel buttons no longer fabricate completion/p-values; they return a
  failed job stating that submission is not implemented.
- S3/R2 dry runs are now explicitly flagged `simulated = true` and reported as
  "nothing uploaded" instead of "Synced & Verified".
- All synthetic demo CSVs are tagged `data_source=synthetic`, so the paired
  analyzer withholds verdicts built from them.

## 6. Other hardening in the same pass

- `android:allowBackup="false"`.
- Added `res/xml/network_security_config.xml`: HTTPS-only except the emulator
  loopback aliases (`10.0.2.2`, `localhost`, `127.0.0.1`).
- Agent settings (including the API key) now use `EncryptedSharedPreferences`
  with a legacy migration path.
- Enabled core-library desugaring so `java.time` works on API 24–25.
- Supabase session client uses `getAll`/`setAll`; server-component cookie writes
  are guarded.
- Added CSP, HSTS, COOP/CORP, DNS-prefetch and cross-domain headers; disabled
  the Next.js image optimizer as a temporary mitigation.
- Added Zod validation to the write APIs.
- Replaced the `NEXTAUTH_URL` redirect with the request origin.
- Added `/register`.
- Production build no longer embeds demo secrets.

## 7. Verification performed

| Check | Result |
|---|---|
| Website `npm run typecheck` | pass |
| Website `npm run build` | pass |
| Website headers | CSP, HSTS, COOP, CORP present |
| `/api/moderation` anonymous | `401` |
| `/api/capsules?visibility=all` | returns public rows only / empty |
| `/register` | `200` (was 404) |
| Login page | no demo credentials |
| Android `:app:testDebugUnitTest` | 183/183 pass (before the concurrent UI changes; targeted affected classes pass after) |
| Android `:app:lintDebug` | pass |
| Rebuilt APK secret scan | no AWS/Kaggle/Cloudflare/Supabase-service strings |
| APK manifest | `allowBackup=false`, network security config present |
| Demo DB cleanup | claims/capsules/publications/users all 0 |
| Migration applied | new columns + policies verified in the live project |
| RLS smoke test (`scripts/rls-smoke-test.mjs`) | 6/6 pass: owner insert/read, other user cannot read/update, role self-escalation blocked, normal profile edit allowed |
| API end-to-end | private claim create → own-only read → public publish; capsule create → publish → verify |
| Real RFC 3161 timestamp | publication returned `timestamped:true`; verification returned `verified:true`, `hashMatch:true`, `timestampVerified:true` (FreeTSA) |

> Note: the workspace received concurrent UI work from another process during
> this pass. A full Android test run was interrupted by that process deleting a
> Gradle result file; the targeted tests for the changed classes
> (`AwsCloudManagerTest`, `CloudflareR2ManagerTest`, `AgentClientTest`) pass.

## 8. Remaining follow-ups

1. **Rotate** the credentials that were pasted into `Downloads/opencode api.txt`
   once they are no longer needed; review CloudTrail/S3/R2/Kaggle logs. The
   Supabase service-role key is still live by design for this workspace.
2. **Deployment config:** `.env.local` currently points
   `CONCORDANCE_PLATFORM_PYTHON` at a machine-specific `.venv`. Set the same
   variable (or create `platform/.venv`) on the deployment host and keep
   `CONCORDANCE_REQUIRE_TIMESTAMP=1`.
3. **Upgrade Next.js.** `npm audit` still reports a critical Next.js advisory
   (14.2.35). The upgrade is a breaking migration (React 19 / async APIs); the
   image optimizer is disabled as a temporary mitigation.
4. Add web unit/E2E tests alongside the `rls-smoke-test.mjs` and end-to-end
   checks above.
5. Encrypt BYOK values at rest (they are still stored as JSON in
   `user_settings`).
6. Add rate limiting to the public write/verify endpoints.
