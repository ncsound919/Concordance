# Data & analytics wiring (2026-10-20)

This round connects the Android app to the website's public registry and adds a
research analytics surface.

## 1. Phone → website publication

### App

- `integration/community/CommunityPublisher.kt` — a small OkHttp client that
  sends a claim or capsule to the website with the user's Supabase bearer token.
- `ConcordanceViewModel.publishClaimToCommunity()` /
  `publishCapsuleToCommunity()` — explicit, user-initiated publication.
- **Publish buttons:**
  - Capsule screen: "Publish to community (timestamped)" on each capsule.
  - Registry screen: "Publish to community (timestamped)" on each claim.
- **Community URL setting:** Agent screen → "Community website URL (publish
  destination)". Default `http://10.0.2.2:3001` (emulator); set the LAN/HTTPS
  address for a physical device.
- The app requires a signed-in Supabase account. The token is refreshed through
  the existing `SupabaseAuth.accessToken()` path.

### Website

- `POST /api/app/publish` (Bearer token):
  1. validates the Supabase access token,
  2. validates the claim/capsule payload with Zod,
  3. refuses `dataClass = phi`,
  4. upserts the artifact under the user's RLS identity,
  5. computes the content hash server-side,
  6. requests a real RFC 3161 timestamp from the canonical engine,
  7. flips the artifact to public and records a `publications` row,
  8. returns the public URL, `timestamped`, and the TSA `genTime`.
- `lib/publication.ts` is shared by `/api/app/publish` and `/api/publications`,
  so both paths use the same hash/stamp/publish logic.
- Re-publishing is idempotent: a unique index on
  `(artifact_type, artifact_id, publisher_id)` plus an existing-publication check
  prevents duplicate share events.

## 2. Analytics & research surface

- `GET /api/analytics` returns a snapshot of public data.
- `GET /api/analytics?format=csv` downloads the same snapshot as CSV.
- `/analytics` has two scopes:
  - **Public registry** — public rows only (anonymous).
  - **My research** — signed-in view over the records you can access (own,
    team-visible, and public). It is not a private-data leak: RLS still filters
    every row.
- `/analytics` page shows:
  - public claims / capsules / challenges / publications,
  - timestamp coverage,
  - contributors,
  - mean artifact-null `r*` and mean `r_sig`,
  - evidence-grade distribution,
  - verdict distribution,
  - 12-month publication and challenge trends,
  - most challenged claims,
  - research signals (contested claims, withheld capsules, untimestamped
    capsules, abstract-only claims, empty registry),
  - recent publications with links.
- `app/claims/[id]/page.tsx` shows a claim, its challenge history, and related
  capsules.
- `app/registry/[id]/page.tsx` (existing) is the capsule detail/verification
  page; publication URLs point here.
- `0005_analytics_indexes.sql` adds read-side indexes and the publication
  uniqueness backstop.

## 3. Verified end-to-end

`scripts/app-publish-smoke.cjs`:

```
publish status 201
timestamped true url http://localhost:3001/registry/app-cap-…
verify 200 verified true hashMatch true timestampVerified true
capsule page 200
analytics status 200 capsules 1 timestamped 1
PASS
```

`scripts/rls-smoke-test.mjs`:

```
PASS owner can insert a private claim
PASS owner can read their private claim
PASS other user cannot read the private claim
PASS other user cannot update the private claim
PASS role self-escalation is blocked
PASS normal profile edit is allowed
```

- The live database was empty after each test (`claims=0`, `capsules=0`,
  `publications=0`, `users=0`).
- A real capsule page returned `200` after publication.
- The analytics snapshot reflected the published, timestamped capsule.

## 4. How to use it

1. Sign in on the Android app (Account tab).
2. In the Agent tab, set **Community website URL** to the site address (for the
   emulator, `http://10.0.2.2:3001`).
3. Mint a capsule or add a claim.
4. Tap **Publish to community (timestamped)**.
5. Open the returned `/registry/<id>` or `/claims/<id>` URL, or `/analytics` on
   the website.

## 5. Issues found while auditing this wiring (and fixed)

- **Publication could leave an artifact public without a share event.** The
  artifact update ran before the `publications` insert. The order is now
  update → insert → compensating rollback, and RLS-filtered updates are checked
  by reading the affected row.
- **Re-publishing rewrote the original publication date.** It now keeps the
  original `published_at`.
- **Changed content could be re-published under an old timestamp.** A content
  hash mismatch now returns `409`; create a new capsule/revision instead.
- **An app publish could take over another owner's artifact id.** The endpoint
  checks the existing owner and rejects a different owner, and it refuses to
  flip a `phi` artifact to `aggregate`.
- **Reviewer RLS access leaked into "My research."** The `mine` analytics scope
  now filters to owner/team rows instead of relying on the reviewer read
  override.
- **CSV formula injection.** Cells starting with `=`, `+`, `-`, `@`, tab or CR
  are prefixed with `'`.
- **Wrong detail links for non-claim/capsule publication types.** The analytics
  table only links claim/capsule rows; other artifact types render as text.
- **Double publish taps.** The Android publish buttons disable and show
  “Publishing…” while a publish is in flight.
- **Failed publish could leave a timestamp token on a private capsule.** The
  rollback restores the previous token as well as visibility.
- **Protected-page redirects lost the destination.** `?next=` is now honoured
  by the login page (with a same-site path check), so
  `/analytics?scope=mine` returns there after sign-in.

## 6. Tests

- Android: **203 unit tests, 0 failures** (including new
  `CommunityPublisherTest` for bearer auth, payload shape, timestamp parsing
  and server refusal).
- Web: `npm run typecheck` and `npm run build` pass.
- Live: app-publish smoke test and RLS smoke test pass; the live DB is left
  empty after each run.

## 7. Notes

- The website must be reachable from the phone. The network security config
  permits cleartext only for `10.0.2.2`, `localhost` and `127.0.0.1`; use HTTPS
  or `adb reverse tcp:3001 tcp:3001` for a physical device.
- The app never publishes automatically. Publication is always an explicit
  action, and the website owns the canonical timestamp.
- Analytics only includes **public** rows. Private and team records are excluded
  by RLS and by the queries.
- The website build and typecheck pass; the Android main sources compile.
  (A full Android test run is currently disturbed by concurrent work adding a
  `NavigationStateTest` whose `NavigationState` class is not yet present.)
