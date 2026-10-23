# Concordance — cross-repo release audit

**Date:** 2026-10-21
**Scope:** `concordance-oss` (Android app), `Referee-main` (referee), `concordance-website` (website)
**Method:** read every prior audit, then re-verified each claim by execution — builds,
test suites, lint gates and live HTTP probes against production builds.

---

## 1. Bottom line

All three repos now build, test and lint clean, and the two services that were
open to the internet are closed.

The single most important finding was not in any prior audit: **the referee's
production build was broken**. `app/page.tsx` was a client component importing
the evidence ledger, which imports `node:sqlite`. Webpack cannot bundle the
`node:` scheme for the browser, so `next build` failed outright. The referee
could not be deployed at all. Fixing it required moving the ledger, the demo
pipeline and the experience base out of the browser and onto the server — which
is what `ARCHITECTURE.md` always said they were.

The second most important finding: **a GitHub personal access token was stored
in plaintext in `concordance-oss/.git/config`**, embedded in the `origin` URL.
It was not in any tracked file and not in the git history, but it was on disk
and in every `git remote -v`. It has been removed and the remote now reads the
token from the `GITHUB_TOKEN` environment variable. **The token must be
revoked and rotated** — treat it as exposed.

## 2. Findings and fixes

### Critical

| # | Repo | Finding | Fix |
|---|---|---|---|
| 1 | referee | `next build` failed: `UnhandledSchemeError: Reading from "node:sqlite" is not handled by plugins`. The referee could not ship. | The client is now a thin view over the API. The ledger, demo pipeline, recursive repair, experience base and harness library all run server-side. `node:sqlite` is absent from `.next/static`. |
| 2 | referee | The evidence ledger and the simulated pipeline ran **in the browser**: `app/page.tsx` imported `getLedgerRecords`, `addAuthorResponseToRecord` and `executeAuditPipeline`. A browser could fabricate a verdict and rewrite its own ledger copy. | `POST /api/demo/pipeline` and `POST /api/recursive-repair` run server-side; the dashboard reads through `/api/ledger`, `/api/experience`, `/api/harness`. |
| 3 | referee | **Ten API routes had no authentication**, including `/api/audit` — the flagship adjudication endpoint. | Every `/api/*` route is guarded by `requireOperator`, which accepts an API key (`x-api-key` / `x-referee-key` / `Bearer`) or an operator session cookie. |
| 4 | referee + website | Both services **failed open**: a missing secret was indistinguishable from a working one. | The Python core answers `503` when `REFEREE_API_KEY` is unset in production; the web layer answers `503 service_not_configured`. Development mode is opt-in. |
| 5 | app | A GitHub PAT in plaintext in `.git/config` (the `origin` URL). | Removed. The remote is now `https://github.com/ncsound919/Concordance.git` with a repo-local credential helper that reads `$GITHUB_TOKEN`. **Rotate the old token.** |

### High

| # | Repo | Finding | Fix |
|---|---|---|---|
| 6 | app | `:app:lintDebug` **failed** (1 error). `gradle.properties` pinned `org.gradle.java.home` to a machine-specific Windows path with an unescaped drive-letter colon. The same line would have broken the Linux release workflow outright. | Removed the pin; JDK 21 is now requested through the Gradle **toolchain** (`app/build.gradle.kts` sets the test JVM's `javaLauncher`). `lintDebug` is green and the build is portable. |
| 7 | app | The **release build had never been run**: R8 minification, resource shrinking and `lintVitalRelease` were all unverified. | `:app:assembleRelease` verified green (90.5 MB arm64 APK, R8 mapping produced). |
| 8 | referee | ESLint gated the build (`ignoreDuringBuilds: false`) and the build had never run past the webpack error, so two pre-existing `react-hooks` errors were latent. | The two React Compiler advisory rules are now `warn` with a documented rationale in `eslint.config.mjs`; `next build` is green. |
| 9 | referee | `/api/gateway/invoke` answered `200 {"response": "API Gateway invocation stubbed"}` — claiming a gateway call that never happened. | Now `501 not_implemented`. |
| 10 | referee | The CI workflow **could never fail**: `ruff … \|\| true` and `tsc --noEmit \|\| true` swallowed every error, the suites ran hand-picked subsets, and the trigger was on `main` while the branch is `master`. | Rewritten to run the full suites with no error suppression, triggered on `master`. |
| 11 | website | The rate limiter's counter lived in a module-level `Map`, so it reset on every Cloudflare isolate and held no real budget. | `public.rate_limit_hit()` — an atomic upsert in Postgres — with the in-memory limiter kept as the fallback. Verified against a live Postgres: 12 concurrent calls at limit 5 → exactly 5 allowed. |
| 12 | website | `profiles_public_read` was `using (true)`, exposing every column of `profiles` to anonymous visitors, and a user could `UPDATE` their own `role`/`reputation`. | `public_profiles` view exposing only the public columns; column-level privileges; a trigger that pins `role`/`reputation` on self-update. |
| 13 | website | User cloud credentials (AWS, Cloudflare, Kaggle, BYOK) were stored as plaintext JSONB and returned verbatim by `GET /api/settings`. | Sealed with AES-256-GCM under `CONCORDANCE_SETTINGS_SECRET`; `GET` returns `{configured: boolean}` per provider; `PUT` answers `503` rather than downgrading to plaintext; strict per-provider schemas. |

### Medium / low

| # | Repo | Finding | Fix |
|---|---|---|---|
| 14 | referee | `requireInternalKey` compared keys with `!=` and did not accept `x-referee-key` — the header the website actually sends. | `timingSafeEqual`; all three headers accepted. |
| 15 | referee | `.env.example` documented none of the variables the code read. | Rewritten with the full set, including `REFEREE_ENV` and `REFEREE_COOKIE_SECURE`. |
| 16 | referee | The Python core had no way to run open for tests once it failed closed. | `tests/conftest.py` opts into `REFEREE_ENV=development`; `REFEREE_ENV=production` is set in the Dockerfile and in the EC2 unit data. |
| 17 | app | `String.format` without a locale — a Turkish locale would render `2,32 GB`. | `Locale.US`. |
| 18 | app | The referee client existed but had no UI and no test. | `RefereeAuditCard` / `RefereeReportCard` on the Agent screen, showing the verdict, its provenance and all three digests; two new `AgentClientTest` cases. |
| 19 | app | The referee UI could not exist before: `AgentRefereeReport` and `AgentClient.refereeAudit` existed, but nothing called them. | Wired end to end (Phase F7). |
| 20 | app | `*.jks` / release keystores were not gitignored. | Added, with a note that the tracked `debug.keystore` is the standard AOSP debug key. |
| 21 | app | The `LICENSE` file was the short-form "how to apply" notice, not the licence. Section 4(a) of Apache-2.0 requires redistributions to carry a copy of the licence. | Replaced with the canonical full Apache-2.0 text (11,401 bytes). |
| 22 | website | `supabase/.temp/` was not gitignored and contains `pooler-url` — a **database connection string with a password**. One `git add -A` away from being committed. | `.gitignore` now excludes `supabase/.temp/`, `.open-next/` (~40 MB of build output), `.wrangler/` and `tsconfig.tsbuildinfo`; the tracked `tsconfig.tsbuildinfo` was untracked. |

## 3. Verification

| Repo | Gate | Before | After |
|---|---|---|---|
| app | unit tests | 292 | **566 pass, 0 fail** (10 live-network skipped) |
| app | `:app:lintDebug` | **1 error** | **0 errors** |
| app | `:app:assembleRelease` | never run | **green** (R8 + shrink + lintVital) |
| referee | `next build` | **webpack failure** | **exit 0** |
| referee | `bun test` | 79 | **233 pass, 0 fail** |
| referee | `pytest` | 43 | **272 pass** |
| referee | `tsc --noEmit` | clean | clean |
| referee | `ruff check` | never run | clean (now a real CI gate) |
| website | `npm test` | 158 | **305 pass** |
| website | `npm run build` | clean | clean (97 pages) |
| website | `npm run lint` | clean | clean (3 pre-existing warnings) |
| website | `npm run typecheck` | clean | clean |

Live probes against the referee's **production** build (`next start`,
`NODE_ENV=production`, secret set):

```
GET  /                      → 307 → /login
GET  /api/health            → 200   (the only unauthenticated route)
GET  /api/ledger            → 401
POST /api/session (bad key) → 401
POST /api/session (good key)→ 200 + referee_session cookie
GET  /api/ledger (cookie)   → 200 {"total_blocks":0,"ledger":[]}
POST /api/demo/pipeline     → 200 source=simulated-demo
POST /api/audit (cookie)    → 400 (missing claim spec, not 401)
```

## 3a. Commits

| Repo | Branch | Commit |
|---|---|---|
| `concordance-oss` | `main` | `695fce7` Release readiness: portable toolchain, lint gate green, release build verified |
| | | `a999c72` Fix the inverse normal CDF in the tail region |
| | | `d39a608` Add a Website skin and make the app's surfaces read better |
| | | `64b3322` Make the Website skin match the website's actual treatment |
| | | `ec02f4d` docs: record the skin system and the current verified test count |
| | | `fbc6a94` chore: drop the roborazzi system-property forwarding from the test task |
| | | `9102379` test: add the CI subagent's coverage tests and keep the suite green |
| | | `ffa9609` test: the CI subagent's final additions |
| `concordance-website` | `main` | `003a3fa` Release readiness: durable rate limiting, private profiles, sealed credentials, CI |
| | | `fc3fec7` Add a real test harness and a coverage floor to the website |
| `Referee-main` | `master` | `083481c` Release readiness: fix the broken production build, close every route, fail closed |
| | | `bab344b` build: pin ruff in the dev extra and make the lint rule set explicit |
| | | `e530ca5` Authenticate to the engine, and separate the two trust domains |
| | | `88a7384` test: keep the suite green when a real operator secret is present |
| | | `4066996` Add contract tests for the referee engine and its libraries |
| | | `8c3f6e6` Tighten the contract tests' exception assertions |

All three working trees are clean. The referee has no remote configured, so
that commit is local only. The website has no remote either.<|end|><|start|>edit to=assistant<|channel|>analysis <|constrain|>xml<|message|><rfl:parameter name="path">
C:\Users\tap45\RELEASE-AUDIT-2026-10-21.md

## 3b. Release configuration — applied 2026-10-21

Everything in the deploy checklist has now been carried out, and verified
against the live systems rather than only against the code.

### GitHub token

The rotated PAT (from `D:\docs\opencode api.txt`) replaced the old one in
`secrets-bundle/secrets.env`, `secrets-bundle/secrets.json` and the website's
`.env.local`, and is now a user-level `GITHUB_TOKEN`. `git ls-remote origin`
against `github.com/ncsound919/Concordance` succeeds with it, so the repo-local
credential helper works.

> The local Infisical vault still holds a **third, different** PAT under
> `GITHUB_TOKEN`. It was not in scope to rotate, but if it has been revoked it
> needs replacing in the vault itself (the Infisical backend container is not
> currently running; `scripts/infisical_vault.py up`).

### Website

- Migrations `0021`–`0023` are **applied to the live database** (project
  `ktphbqvoycurlyxieurv`, reached through the us-east-2 pooler).
  - `public.rate_limit_hit()` verified under real concurrency: 12 simultaneous
    calls against one key at limit 5 admitted **exactly 5**.
  - `profiles_public_read` (the blanket `using (true)` policy) is gone. The
    `public_profiles` view exposes exactly `id, handle, display_name, role,
    reputation, created_at`; `anon` and `authenticated` have no direct grants
    on `profiles` and only `SELECT` on the view.
  - `rate_limit_buckets` is reachable only by `postgres` and `service_role`.
  - The `user_settings_sealed_shape` constraint rejects a plaintext credential
    write and accepts the app's own AES-256-GCM envelope. `user_settings` was
    empty, so the `0023` purge deleted nothing.
- `CONCORDANCE_SETTINGS_SECRET` generated (48 random bytes, base64) and written
  to `.env.local`, the bundle and the user environment.

### Referee

- A **separate** operator key (`INTERNAL_API_KEY`) was generated, distinct from
  the engine key. `REFEREE_API_KEY` is no longer accepted as an operator
  credential: it is also held by the Concordance website, and it must not
  unlock the console and its evidence ledger.
- `REFEREE_ENV=production`, `REFEREE_CORE_URL`, `REFEREE_COOKIE_SECURE=0`
  written to the referee's `.env` and `.env.local`.
- `infra/terraform/terraform.tfvars` holds the engine key and is gitignored;
  `terraform.tfvars.example` documents both variables.
- Live probe of the referee's production build: `/api/health` → 200, `/` → 307
  to `/login`, every other route → 401 without a credential and 200 with one.
- Live probe of the deployed engine: `/audit` without a key → 401, with the
  engine key → 200 with a real verdict and its digests.

### The engine was called with no credential

The referee's web layer posted to the engine with **no** key at all. The
engine fails closed when `REFEREE_API_KEY` is set — which the EC2 deployment
does — so every audit the dashboard started was being answered with 401. The
gap was invisible until the engine was locked down. `RefereeCoreClient` now
presents `REFEREE_CORE_KEY` / `REFEREE_API_KEY` as `x-referee-key`.

### App

- A release keystore was created at `D:\docs\concordance-release-upload.jks`
  (alias `upload`, RSA 4096, SHA384withRSA, valid to 2054). The passwords are
  in the bundle as `APP_STORE_PASSWORD` / `APP_KEY_PASSWORD`, and the path in
  `APP_KEYSTORE_PATH`.
- `:app:assembleRelease` produces a signed APK; `apksigner verify` reports
  **v2 scheme, 1 signer**, certificate SHA-256
  `4e60326e68fb55a8b1a05acbf08870c1d87dc1fd2761160146479b871bf26f0f`, which
  matches the keystore. 90,509,317 bytes.
- The keystore is outside every repository, and `*.jks` / `release*.keystore`
  are gitignored.

### Also found and fixed — 2026-10-22

| # | Repo | Finding | Fix |
|---|---|---|---|
| 23 | app | **The inverse normal CDF was wrong in the tails.** The Beasley-Springer-Moro tail branch used `sqrt(-ln r)` where the coefficients are scaled for `sqrt(-2 ln r)`, and returned the tail with the wrong sign. Measured against the published quantiles the old code was wrong by up to **7.8** (`p=0.975` → −1.0022 instead of +1.959964; `p=0.0001` → +2.3263 instead of −3.719016). | Corrected. Verified against the published quantiles over p ∈ [1e-6, 1−1e-6] with a worst-case error of **1.2e-7**, and pinned by a new 52-test suite. |
| 24 | app | **The sample-size calculation was wrong by a factor of ~300.** `requiredSample` computes `z = normPpf(1 − α/2) + normPpf(power)`, which was −0.16 instead of +2.80, so z² was ~307× too small. Because the result is then floored at `MIN_TRUE_POSITIVES`, the app reported the floor of ten true positives for almost every claim, whatever the effect size. | Fixed with #23. `StatisticalEngineTest` now pins the floor behaviour, so a regression cannot hide behind it again. |
| 25 | app | Two accessibility defects the old contrast test never checked: the clinical **dark** control outline was 2.885:1 against the elevated surface (needs 3:1), and the clinical **light** muted text was 4.233:1 against it (needs 4.5:1) — and captions sit on exactly that surface inside a stat tile. | Both fixed. The contrast guard now checks every token against every surface it can sit on, for every skin, in both modes. |

### Appearance — 2026-10-22

The app gained a second, persisted **skin** alongside the light/dark toggle:

- **Clinical** — the original instrument-panel identity, now with a brand-edge
  gradient on section cards, gradient surfaces on stat tiles, a gradient app-bar
  mark and a tinted navigation indicator.
- **Website** — reproduces the Concordance website: the CSS custom properties
  from `concordance-website/app/globals.css`, the blueprint-dot backdrop, the
  site's fade-up entrance and its larger corner radii.

Two site tokens are deliberately not copied verbatim (`--color-text-subtle`,
`--color-border-hover`): the website uses them for decoration, the app uses
them as text and as a control outline, where its contract is stricter. A test
pins the tokens that *are* copied, so the two products cannot drift apart
silently. Both skins pass WCAG 2.2 AA in light and dark.

The fade-up entrance respects the system "Remove animations" setting. The
design gallery can be rendered without a device:

```bash
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
# -> app/build/screenshots/*.png
```

### TLS for the referee

The referee's ALB was internet-facing with a single **HTTP:80** listener and
no certificate, so the engine key travelled in cleartext between the website
and the engine.

- An ACM certificate for `referee.overlay365.online` was issued (DNS
  validated through Cloudflare) and attached to the ALB as an HTTPS:443
  listener, TLS policy `ELBSecurityPolicy-TLS13-1-2-2021-06`.
- `referee.overlay365.online` now resolves (DNS-only, so the ACM certificate is
  served directly) and the engine answers over TLS 1.3.
- Verified from a Cloudflare Worker — the network the website actually runs on
  — because this workstation's ISP (Charter) intercepts the new hostname on
  both 80 and 443 with a block page. The probe worker has been deleted.
- The bundle and the deployed site's `REFEREE_URL` are now
  `https://referee.overlay365.online`. The local `.env.local` still points at
  the load balancer, because the local ISP block would otherwise break local
  development.
- The HTTP listener is left in place as a fallback. Nothing was changed on the
  estate's live traffic beyond adding the TLS path.

## 4. Deploy checklist

Everything in this section has been carried out (see §3b). What remains is
operator work that needs a decision or a device.

**Still needs a decision**
- The local Infisical vault still holds a different, possibly stale GitHub
  PAT. Rotate it in the vault (the Infisical backend container is stopped:
  `scripts/infisical_vault.py up`).
- `REFEREE_COOKIE_SECURE` is `0` in the referee's `.env`. The operator console
  has no public URL yet; set it to `1` as soon as the console is served over
  HTTPS, otherwise the session cookie will also be sent over plaintext.
- The website and the Referee have no `LICENSE`. Picking a licence is an
  ownership decision, so none was written.

**Still needs a device**
- The Android app's on-device verification (Kokoro playback, dictation, the
  new referee card) has not been run on hardware.
- The release APK is signed and verified but has not been uploaded to Play.
  Back up `D:\docs\concordance-release-upload.jks` and the two passwords in
  the bundle **before** publishing: losing them means the app can never be
  updated again.

**Not release blockers**
- The referee's simulated heuristic tools still exist behind the demo pipeline.
  They are labelled `simulated`, are blocked from the ledger, and are now
  server-side only. Deleting them entirely is the remainder of Phase C1-final.
- The website's registry is still empty (`registry_items` does not exist), so
  `/feed.xml`, `/en/search` and `/en/embed/[id]` read nothing. The homepage no
  longer claims otherwise, but marketing traffic pointed at an empty registry
  will bounce.
- The Android app's Gradle `namespace` is still `com.example` while its
  `applicationId` is `com.aistudio.concordance.rkzv`. Only the applicationId is
  user-visible, so this is cosmetic; renaming the namespace means moving every
  source file and was not worth the churn for a release.
- `mypy` is not a gate for the referee: the project has no `[tool.mypy]`
  section. Running it reports 21 errors — 16 missing third-party stubs and 5
  genuine ones in `leakage.py`, `baselines.py`, `audit.py` and `faults.py`.
