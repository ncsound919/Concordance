# Phase 3 Plan — Phone App ↔ Supabase Sync

**Goal:** Make the Android app a first-class client of the same Supabase backend as the
website, so claims, capsules, challenges, teams, and publications flow between phone
and web. Offline-first is preserved: Room stays the source of truth on-device; sync
reconciles with the server.

**Prerequisite:** Phase 1 (web) + Phase 2 (backend API) + Supabase auth done. ✅

---

## 1. Principles

1. **Offline-first, always.** The app must work with no network. Room remains the
   local source of truth; sync is best-effort and never blocks the UI.
2. **Single identity.** One Supabase user for both app and web (email/password +
   OAuth). Session stored in encrypted prefs.
3. **No second backend.** App talks to Supabase directly (PostgREST + Auth REST) using
   the anon key + the user's JWT, exactly like the web app's server client. RLS
   enforces ownership. No new Kotlin backend.
4. **Conflict = challenge, not silent overwrite.** When two edits race, surface a
   conflict as a challenge/diff rather than clobbering.
5. **Privacy invariant preserved.** `phi`-class data never leaves the device; only
   aggregate/derived artifacts sync. (Existing agent refusal stays.)

## 2. Authentication in the app

### 2.1 New components
- `integration/supabase/SupabaseAuth.kt` — wraps Auth REST:
  - `signIn(email, password)` → POST `/auth/v1/token?grant_type=password`
  - `signUp(...)` → POST `/auth/v1/signup`
  - `refresh()` → POST `/auth/v1/token?grant_type=refresh_token`
  - `getUser()` → GET `/auth/v1/user`
  - `signOut()` → POST `/auth/v1/logout`
- `integration/supabase/SupabaseSessionStore.kt` — persists `access_token`,
  `refresh_token`, `expires_at`, `user_id` in encrypted prefs (reuse `KeyStore`
  pattern / `EncryptedSharedPreferences`). Never in plain prefs.
- `ui/screens/AuthScreen.kt` (or a dialog) — email/password + "continue as guest"
  (offline-only mode) + link to web for OAuth.

### 2.2 Session lifecycle
- On app start: load session; if expired, refresh; if refresh fails, prompt login
  but keep guest mode available.
- All Supabase calls attach `Authorization: Bearer <access_token>` + `apikey`.
- Tokens auto-refresh on 401 with one retry (existing `HttpJsonClient` pattern).

### 2.3 Guest mode
- No sync, no publish, no teams. Local-only. Clearly labeled. Encourages sign-up.

## 3. Sync layer

### 3.1 New components
- `integration/supabase/SupabaseSyncManager.kt` — orchestrates pull/push.
- `integration/supabase/PostgRestClient.kt` — thin wrapper over `HttpJsonClient` for
  PostgREST (GET/POST/PATCH/DELETE with `Prefer: return=representation`).
- `data/SyncState.kt` — per-entity sync metadata.

### 3.2 Local schema changes (Room)
Add to `ClaimEntity`, `CapsuleEntity`, `ChallengeEntity`:
- `syncStatus: String` (`local` | `synced` | `conflict` | `pending_delete`)
- `serverUpdatedAt: Long?` (epoch ms from `updated_at`)
- `version: Int` (optimistic concurrency)
- `deletedAt: Long?` (soft delete for sync)

New table `sync_cursor` (entity, last_pulled_at) for incremental pull.

### 3.3 Pull (server → device)
- On login + periodic (WorkManager, e.g. every 15 min + on network change) +
  manual "Sync now".
- For each entity type: `GET /rest/v1/claims?updated_at=gte.<cursor>&select=*`
  (RLS filters to what the user can see: own + team + public).
- Upsert into Room by id; if local has unsynced changes, mark `conflict` instead of
  overwriting.
- Update `sync_cursor`.

### 3.4 Push (device → server)
- On any local write, enqueue a sync job (WorkManager) with exponential backoff.
- For `local`/`pending` rows: POST (new) or PATCH by id (existing).
- Respect `visibility`: private claims stay local-only unless user publishes.
- PHI-class data is never pushed (guard in push path; assert in tests).

### 3.5 Conflict resolution — routed into self-learning, trends, and an agent repair team
- Server row `updated_at` vs local `serverUpdatedAt` + local dirty flag.
- If both changed: mark `conflict` and route the conflict into three channels:
  1. **Self-learning pipeline:** the conflict becomes a training case for the
     learning engine (a human challenge with two candidate states), feeding
     `TrainingSetBuilder` so future model revisions learn from real disagreements.
  2. **Trends engine:** the conflict is recorded as a trend event (which claims
     flip, which fields diverge, which users/teams are involved) so systemic
     drift is visible on the web dashboard.
  3. **Agent repair team:** an automated repair agent (extending the existing
     adjudication agent) attempts a resolution — proposing a merged claim/capsule
     with a plain-language explanation and a confidence score. Low-confidence or
     PHI-adjacent conflicts escalate to a human reviewer (reviewer role).
- Resolution options presented to the user: accept agent proposal, keep local
  (force push), keep server (overwrite local), or open as a community challenge.
- Every resolution (including agent proposals and refusals) is audited via
  `learning_audit` + `reputation_events`, mirroring the existing learning audit
  discipline (refusal is a result, not an error).
- Never silently drop either side.

### 3.6 Soft delete
- Delete locally sets `deletedAt` + `syncStatus=pending_delete`; push sends DELETE;
  on success remove row. Allows undo + offline delete.

### 3.7 Offline queue
- Reuse existing `JobQueueManager`/`ConcordanceWorkManagerScheduler` pattern for
  sync jobs so retries, backoff, and "stuck recovery" already exist.

## 4. Publish flow (phone → community)

- In `CapsulesScreen` / `RegistryScreen`, add "Publish to community" action.
- Pre-flight: requires sign-in, skill-tree gate (Phase 4), and visibility choice.
- App uploads capsule JSON + hash to `/api/publications` (or directly to Supabase
  with RLS) → server records Publication + flips visibility → returns public URL.
- Deep link to web registry page for the capsule.
- For PHI-class capsules: publish is blocked with an explanation.

## 5. Teams on phone

- Read-only team list + membership for now (Phase 4 adds governance).
- Team-owned claims/capsules sync down via RLS (user sees team-private rows).
- "Create team" deep-links to web (heavy setup stays on web, per plan).

## 6. Deep setup on phone

- No editing of BYOK/AWS/Kaggle/runtime tuning on phone (moved to web).
- Phone shows a read-only "Status" card: signed-in user, sync status, last sync,
  pending uploads, and a "Open web setup" button (deep link).

## 7. Data model mapping (app ↔ Supabase)

| App (Room) | Supabase table | Notes |
|---|---|---|
| ClaimEntity | claims | owner_type/owner_id/visibility added |
| CapsuleEntity | capsules | same |
| ChallengeEntity | challenges | author_id (uuid) |
| ProvenanceEntity | provenance_records | pk (bigserial) |
| ModelRevisionEntity | model_revisions | promoted_by uuid |
| LearningAuditEntity | learning_audit | approver uuid |

Mapping functions in `data/SyncMappers.kt` (to/from Supabase JSON). Reuse existing
`fromAnchor`/`toAnchor` patterns.

## 8. Security

- Anon key + user JWT in all requests; RLS enforces ownership.
- Tokens in `EncryptedSharedPreferences` (AndroidX Security), never logged.
- Certificate pinning for `*.supabase.co` (optional, Phase 3.1).
- PHI guard: assert data class before any push; test covers refusal.

## 9. Testing

- Robolectric unit tests: sync mapper round-trips, conflict detection, PHI guard,
  offline queue retry.
- MockWebServer tests for PostgREST calls (mirrors `AgentClientTest`).
- Instrumented tests: end-to-end sign-in + sync on emulator (later).

## 10. Implementation order (sub-phases)

1. **3.0 Auth (Kotlin):** `SupabaseAuth`, session store, `AuthScreen`, guest mode.
   Tests for token refresh.
2. **3.1 Read sync:** pull claims/capsules for signed-in user; Room upsert; cursor.
3. **3.2 Write sync:** push local writes; offline queue; retry/backoff.
4. **3.3 Conflicts:** detection + resolution UI + challenge creation.
5. **3.4 Publish:** "Publish to community" action + deep link.
6. **3.5 Teams (read):** team list + team-scoped sync.
7. **3.6 Polish:** status card, manual sync, error reporting, tests.

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Offline edits diverge for long periods | Conflict surfacing + challenge flow; never silent overwrite |
| RLS gaps leak other users' data | RLS policies reviewed; integration tests with two users |
| Token leakage in logs | Provenance-style: never log bodies/tokens (existing rule) |
| PHI accidentally synced | Guard + test; data class checked at push boundary |
| Battery/drain from sync | WorkManager constraints (network, charging optional); batched pulls |

## 12. Open decisions — resolved

1. **Supabase client lib vs REST:** Hand-rolled REST via existing `HttpJsonClient`.
   (decided)
2. **Realtime:** Polling via WorkManager. (decided)
3. **Conflict resolution:** Routed into self-learning + trends engines + agent
   repair team (with human escalation). (decided)
4. **Guest → user migration:** Yes, with explicit consent dialog. (decided)