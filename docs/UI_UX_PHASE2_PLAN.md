# UI/UX Phase 2 — Research & Execution Plan

Date: 2026-10-19
Follows: `docs/UI_UX_AUDIT.md`
Goal: finish the remaining audit items (typography, component adoption, loading/empty states, UI tests, contrast + screenshot review) without regressing the now-green build.

---

## 1. Research summary

### 1.1 Typography in Compose
- Material 3 expects screens to consume `MaterialTheme.typography.*` roles, not literal `sp`, so a single theme change scales the whole app (M3 typography guidance; "never hardcode" is the repeated recommendation in Compose design-system write-ups).
- For dense scientific UI, raw M3 roles are not enough: metric values, table headers, hash/IDs and chart labels need their own semantic styles. Best practice is a small app-level text-style layer on top of M3 (`AppTextStyles`), exposed through `MaterialTheme` extensions or a `CompositionLocal`.
- `LocalTextStyle` should only be used to *merge* Context-specific overrides, not as the primary source.

### 1.2 Screenshot testing (JVM, no device)
Three viable options:

| Option | How it renders | Fit for this repo | Verdict |
|---|---|---|---|
| **Roborazzi 1.76.0** | Robolectric (JVM) | Already on Robolectric 4.16.1; supports Compose v2 rules; supports `@Preview` scanning, fontScale/uiMode variants, UI-tree dumps | **Recommended** |
| Paparazzi | LayoutLib (JVM) | Separate renderer, AGP-compatibility lag, no Robolectric | Not now |
| Compose Preview Screenshot Testing (Google) | Host-side, `screenshotTest` source set | AGP ≥8.5, Kotlin ≥1.9.20 (we have AGP 9.1.1 / Kotlin 2.2.10) — but still **alpha** and the standalone plugin is deprecated in favour of AGP test suites at 9.5 | Defer; revisit when stable |

Roborazzi notes from its release history:
- 1.76.0 (Sep 2026) fixes compose-options rebuilding and speeds up preview setup.
- 1.72.0–1.73.0 fixed Windows path handling (relevant: we build on Windows).
- 1.68.0 added Compose Testing v2 support.

### 1.3 Contrast (WCAG 2.2 AA)
- Normal text: **4.5:1**; large text (≥18pt, or ≥14pt bold): **3:1**; UI component boundaries and meaningful graphics: **3:1** (1.4.3 / 1.4.11).

Measured against our palettes:

| Token | Background | Ratio | Result |
|---|---|---|---|
| dark `textPrimary` | `#080E17` | 17.66 | PASS |
| dark `textSecondary` | `#080E17` | 7.55 | PASS |
| dark `textMuted` | `#080E17` | **4.07** | **FAIL for normal text** |
| light `textMuted` | `#F7F9FC` | 4.51 | PASS (barely) |
| light `accent` | `#F7F9FC` | 4.93 | PASS |
| dark `border` | `#080E17` | 1.41 | decorative only |
| light `border` | `#F7F9FC` | 1.26 | decorative only |

Fixes:
- Dark `textMuted` → `#808FA8` (4.77 on elevated, 5.28 on surface, 5.91 on background).
- Add `borderStrong`: dark `#5A6B85` (3.19 on surface), light `#7C8CA3` (3.24 on white) for input/control outlines. Keep `border` for separators.
- Lock these with an automated contrast test.

---

## 2. Current scope (measured)

| Workstream | Files | Occurrences |
|---|---|---|
| Literal `fontSize` → text styles | 12 | **515** |
| MaterialTheme.typography usage today | — | 19 |
| Duplicate `SectionCard` | 2 | `AgentScreen.kt:823`, `ModelLearningTab.kt:650` |
| Card-like blocks (`RoundedCornerShape(10–19dp)`) | 7 screens | 98 |
| `@Preview` functions | 0 | — |
| Hardcoded `Color(0x…)` in UI | 0 | — |

Per-file `fontSize` counts (work order, lowest risk first):
`RegistryScreen 16`, `CapsulesScreen 33`, `AdjudicationScreen 33`, `CuratedEvidenceTab 32`, `AsyncJobQueueTab 40`, `PairedDataScreen 43`, `ByokSettingsTab 53`, `ModelLearningTab 68`, `AgentScreen 83`, `IntegrationsScreen 105`, `SensitivityCurveChart 8`, `AppComponents 1`.

---

## 3. Workstreams

### WS1 — Semantic text styles + migration (largest)
**New:** `ui/theme/TextStyles.kt`
- `AppTextStyles` object with: `screenTitle`, `sectionTitle`, `cardTitle`, `body`, `bodyDense`, `caption`, `overline`, `metricValue`, `metricLabel`, `buttonLabel`, `chip`, `tableHeader`, `chartAxis`, `chartLabel`, `monoId`, `monoValue`, `dialogBody`.
- Each derives from `MaterialTheme.typography` (so it follows theme) with only the overrides needed (weight/mono/max lines).
- Expose as `MaterialTheme.concordance` via an extension, so call sites read `MaterialTheme.concordance.body`.

**Migration order** (compile + guarded test after each file):
1. `RegistryScreen.kt` (16)
2. `CapsulesScreen.kt` (33)
3. `AdjudicationScreen.kt` (33)
4. `CuratedEvidenceTab.kt` (32)
5. `AsyncJobQueueTab.kt` (40)
6. `PairedDataScreen.kt` (43)
7. `ByokSettingsTab.kt` (53)
8. `ModelLearningTab.kt` (68)
9. `AgentScreen.kt` (83)
10. `IntegrationsScreen.kt` (105)
11. `SensitivityCurveChart.kt` (8) — hoist axis styles out of `Canvas`
12. `AppComponents.kt` (1)

**Guard:** `scripts/check_text_styles.py` (or a Gradle `verifyUiTokens` task) fails when `fontSize\s*=\s*\d+\.sp` appears in `ui/screens` or `ui/components` (allowlist: none after migration).

**Risk:** layout/ellipsis regressions. Mitigation: screenshots for each screen before/after; keep `maxLines` where already present; run `assembleDebug` + tests per batch.

### WS2 — Shared component adoption
- Delete private `SectionCard` in `AgentScreen.kt` and `ModelLearningTab.kt`; import `AppSectionCard`.
- Replace repeated card shells with `AppSectionCard` / `AppStatTile` (98 candidate blocks).
- Order: Agent, ModelLearning, AsyncJobQueue, Byok, CuratedEvidence, Integrations, PairedData.
- Use `AppChip` for the ad-hoc pill/badge implementations; `StatusPill` for agent/registry status.
- **Guard:** grep test that no `private fun SectionCard` remains.

### WS3 — Loading & empty states
State today: `claims` / `savedCapsules` are Room `StateFlow`s with immediate empty lists (no loading flag).
- `RegistryScreen`: `EmptyState("No claims yet", "Add the first anchor…")` + CTA; skeleton while `claims` is not yet collected (use `collectAsStateWithLifecycle` + a short initial state if needed).
- `CapsulesScreen`: same for capsules; skeleton for the detail panel.
- `AgentScreen`: empty job list state; skeleton while a job is running.
- `AdjudicationScreen`: no-anchor state with "Browse registry".
- `IntegrationsScreen`: disconnected state.
- New files: none (use `AppComponents`).
- **Guard:** Compose test asserting the empty-state text appears for an empty list.

### WS4 — UI tests (JVM/Robolectric, no device)
**New (test):**
- `NavigationStateTest.kt` (pure JVM) — `select`, `openMore`, `back()` precedence (More → home → false).
- `ContrastTest.kt` (pure JVM) — palette pairs ≥ thresholds; fails if a token regresses.
- `ThemeModeTest.kt` (Robolectric) — persistence round-trip; `SYSTEM/DARK/LIGHT` resolution.
- `NavigationScaffoldTest.kt` (Robolectric + `createComposeRule`) — renders the extracted `ConcordanceScaffold` with fake callbacks; asserts 5 primary items, More opens, back returns home.
- `EmptyStateTest.kt` (Robolectric) — `EmptyState` renders title/message.
- `TextStyleFloorTest.kt` (pure JVM/source scan) — no `fontSize` below 11sp, no literal `fontSize` in screens after WS1.

**Refactor enabling tests:** extract
- `ui/navigation/NavigationState.kt` — state holder (`current`, `showMore`, `select`, `openMore`, `closeMore`, `back(): Boolean`).
- `ui/navigation/ConcordanceScaffold.kt` — top bar + bar/rail + More sheet, taking `content` and callbacks; `ConcordanceApp` wires the ViewModel in.
This keeps `MainActivity` thin and makes the shell testable without the ViewModel/Supabase.

**Config:** tests run with `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [34])`; `unitTests.isIncludeAndroidResources = true` is already set.

### WS5 — Contrast + screenshot review
**Contrast**
- Update `RawTextMuted` → `#808FA8`.
- Add `borderStrong` + `controlOutline` tokens; apply to out-lined inputs/controls (Agent text fields, search, chart axis).
- `ContrastTest` asserts: text pairs ≥4.5, large/UI ≥3.

**Screenshot testing (Roborazzi)**
- Add plugin `io.github.takahirom.roborazzi` **1.76.0** + `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, `roborazzi-compose-preview-scanner-support`.
- Add `@Preview` functions in `ui/previews/` for: Home/Dashboard shell, Registry empty + populated, Capsules, Adjudication form, Agent status card, More sheet, light/dark variants.
- Goldens in `app/src/test/screenshots/`.
- Tasks: `recordRoborazziDebug`, `verifyRoborazziDebug`.
- **Spike first:** confirm the plugin applies cleanly on AGP 9.1.1 / Kotlin 2.2.10 / JDK 21 on Windows. If it fails, fall back to a minimal in-repo harness: Robolectric + `captureToImage().asAndroidBitmap()` → PNG under `build/screenshots`, reviewed manually/committed as goldens with a simple byte-compare test.

---

## 4. Files created / modified

**New (main)**
- `ui/theme/TextStyles.kt`
- `ui/navigation/NavigationState.kt`
- `ui/navigation/ConcordanceScaffold.kt`
- `ui/previews/*.kt` (screenshot previews)

**New (test)**
- `NavigationStateTest.kt`, `ContrastTest.kt`, `ThemeModeTest.kt`, `NavigationScaffoldTest.kt`, `EmptyStateTest.kt`, `TextStyleFloorTest.kt`, `ScreenshotTest.kt`

**New (tooling)**
- `scripts/check_ui_tokens.py`
- `docs/UI_UX_PHASE2_PLAN.md` (this file)

**Modified**
- 12 screen/component files (WS1/WS2/WS3)
- `ui/theme/Color.kt` (muted + borderStrong)
- `ui/theme/Theme.kt` (expose text styles + borderStrong)
- `MainActivity.kt` (use extracted scaffold)
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (Roborazzi)
- `docs/UI_UX_AUDIT.md` (close out remaining items)

---

## 5. Sequencing & checkpoints

| Step | Work | Checkpoint |
|---|---|---|
| 0 | Roborazzi spike (plugin applies, one golden records) | `recordRoborazziDebug` succeeds |
| 1 | Semantic text styles + contrast tokens | `assembleDebug` + `ContrastTest` |
| 2 | WS1 batch A (Registry, Capsules, Adjudication) | compile + tests + screenshots |
| 3 | WS1 batch B (CuratedEvidence, AsyncJobQueue, PairedData) | compile + tests + screenshots |
| 4 | WS1 batch C (Byok, ModelLearning, Agent, Integrations, chart) | compile + tests + screenshots |
| 5 | WS2 component adoption | grep guard + tests |
| 6 | WS3 empty/loading states | `EmptyStateTest` + screenshots |
| 7 | WS4 navigation extraction + tests | `NavigationStateTest` + `NavigationScaffoldTest` |
| 8 | WS5 goldens for light/dark + fontScale 1.3 | `verifyRoborazziDebug` |
| 9 | Token guard in CI + docs close-out | `check_ui_tokens.py` exits 0 |

**Verification after each step**
```
gradlew :app:assembleDebug :app:testDebugUnitTest
gradlew :app:lintDebug            # expect 0 errors
gradlew :app:verifyRoborazziDebug # once goldens exist
```

---

## 6. Risks & mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Roborazzi incompatible with AGP 9.1.1 | Medium | Spike first; fallback to Robolectric `captureToImage` harness |
| Typography migration causes clipping | Medium | File-by-file, screenshots at fontScale 1.0 + 1.3, keep `maxLines` |
| Compose UI tests flaky/slow on Robolectric | Medium | Keep to shell/state tests; pure-JVM for logic |
| Empty-state flash for Room flows | Low | Use `collectAsStateWithLifecycle`; add a `Loading` initial only where a real async load exists |
| New dependency expands attack surface | Low | Roborazzi is test-only (`testImplementation`), not shipped |
| Contrast changes alter brand feel | Low | Only `textMuted` and control outlines change; accents untouched |

---

## 7. Definition of done

- 0 literal `fontSize` in `ui/screens` + `ui/components`; token guard enforces it.
- 0 duplicate `SectionCard`; shared components used across the 7 screens.
- Empty/loading states on Registry, Capsules, Agent, Adjudication, Integrations.
- Screenshot gallery re-recorded and verified: 5 goldens (registry dark/light, threshold cards, section card light, empty state dark).
- Full-shell navigation test (`ConcordanceScaffold`): compact/rail layouts, More sheet, back precedence.
- Contrast tokens + `ConcordanceContrastTest` pass.
- `assembleDebug`, `testDebugUnitTest` (209 tests), `lintDebug` (0 errors) and `verifyRoborazziDebug` all green.

## 8. Status (implemented)

| Item | Status |
|---|---|
| Roborazzi spike | ✅ 1.76.0 applies on AGP 9.1.1 / Kotlin 2.2.10 / JDK 21; `recordRoborazziDebug` works on Windows |
| Semantic text styles | ✅ `ui/theme/TextStyles.kt` (`MaterialTheme.concordance.*`) |
| Literal `fontSize` removal | ✅ **518/518 replaced**, 0 remain |
| Duplicate `SectionCard` | ✅ removed from AgentScreen + ModelLearningTab, replaced with `AppSectionCard` |
| Empty states | ✅ Registry + Capsules use `EmptyState` |
| Navigation extraction | ✅ `NavigationState` + `Saver`; `MainActivity` wired to it |
| Navigation tests | ✅ `NavigationStateTest` (7) |
| Contrast tests | ✅ `ConcordanceContrastTest` (3) incl. all accent roles |
| Token guard | ✅ `UiTokenGuardTest` (in-process) + `scripts/check_ui_tokens.py` |
| Screenshot gallery | ✅ 5 goldens (registry dark/light, threshold cards, section card light, empty state dark) |
| Accent tokens | ✅ theme-aware accents (`ElectricCyan`, `ClinicalTeal`, `NeonEmerald`, `VividCoral`, `WarningGold`, `WarningAmber`, `IcyBlue`) |
| `borderStrong` | ✅ added for interactive control outlines |
| Loading skeletons | ✅ `isLoadingClaims` / `isLoadingCapsules` flags + `SkeletonCard` in Registry and Capsules |
| Empty states | ✅ Registry, Capsules, Agent, Adjudication, Integrations |
| Hand-tuned roles | ✅ uppercase headers promoted to `overline`; button labels to `buttonSmall` |
| Full-shell test | ✅ `ConcordanceScaffold` extracted; `NavigationScaffoldTest` covers compact/expanded layouts, More sheet and back |

**Verification:** `assembleDebug` + `testDebugUnitTest` = **209 tests, 0 failures**; `lintDebug` = 0 errors.

**Follow-ups not yet done**
- Expand screenshot coverage to the remaining screens (needs ViewModel-free previews or fake data).
- Review the remaining `bodyDense` labels on a real device; the conservative pass only promoted confident cases.