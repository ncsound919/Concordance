# UI/UX Audit — Concordance Android

Date: 2026-10-19
Scope: `app/src/main/java/com/example/MainActivity.kt`, `ui/screens/**`, `ui/components/**`, `ui/theme/**`
Method: static audit of the Compose tree + current design guidance (Material 3, WCAG 2.2, Android accessibility) and recurring user complaints from Android/accessibility communities.

---

## Research summary — what users actually complain about

Synthesised from current design guidance and community threads (r/Android, r/accessibility, r/GooglePixel, r/UI_Design, Android developer docs):

| Theme | What users say | Design response |
|---|---|---|
| **Navigation sprawl** | "Too many tabs, I can't find anything", bottom bars with 6–7 items force tiny labels | Cap primary navigation at 5; move the rest behind "More" |
| **Tiny text** | Repeated complaints that apps force 9–11sp copy, especially after OS font scaling; older adults abandon apps | Body ≥16sp, labels ≥12sp, never below 11sp |
| **Dark-mode inconsistency** | "Half the app is dark, dialogs are white", jarring flashes | One palette behind a CompositionLocal; dialogs inherit |
| **Forced sign-up / permission walls** | Immediate drop-off when value isn't shown first | Guest path already exists; keep it first-class |
| **Spinners with no context** | "Is it frozen?" | Skeleton placeholders + explicit empty/error states |
| **Lost state on rotation** | Re-navigating after every rotate | `rememberSaveable` for navigation and dialogs |
| **Back button surprises** | Back exits the app from a deep tab | `BackHandler` returns to home tab first |
| **One-handed reach** | Primary actions in top corners are hard to reach | Primary actions in bottom bar / FAB zone; top bar holds only global actions |
| **Inconsistent shapes/spacing** | Apps feel "assembled from parts" | Design tokens for spacing/radius + shared components |
| **Thumb-hostile controls** | Tap targets under 48dp | 48dp minimum interactive size |
| **No reduced-motion respect** | Motion-sensitive users get discomfort | Keep motion short; do not gate content on animation |

Sources consulted:
- Material 3 — structure & touch targets (m3.material.io/foundations/designing/structure)
- Android accessibility guidance (developer.android.com/guide/topics/ui/accessibility/apps)
- WCAG 2.2 mobile guidance (accessibilitychecker.org/guides/mobile-apps-accessibility)
- "Mobile UI/UX Design Mistakes Blocking App Growth in 2026" (sparkouttech.com)
- r/Android "Android is getting confusing for normal users", r/accessibility dark-mode thread, r/GooglePixel font-size threads

---

## Findings (before)

### Critical
1. **7 bottom-navigation destinations.** Labels rendered at 11sp with 7 items; "Concordance" as a tab title is ambiguous. Material guidance is 3–5.
2. **Typography floor violated at scale.** 399 text calls at ≤11sp, 227 at ≤10sp, 77 at ≤9sp, 1 at 8sp. Only 2 at 16sp. This is the single biggest readability failure.
3. **No real light theme.** `LightColorScheme` was literally `darkColorScheme(...)`, and `darkTheme` defaulted to `true`, so the app ignored the system setting.

### High
4. **Navigation state lost on rotation** — `remember` used for `currentScreen`.
5. **Back button exits the app** from any tab; no `BackHandler`.
6. **No adaptive layout** — same phone layout on tablets/foldables; no `NavigationRail`.
7. **Duplicated component implementations** (`SectionCard` defined twice) and 7 different corner radii; no shared card/stat/empty/skeleton primitives.
8. **No loading skeletons** — screens rely on spinners/blank states.

### Medium
9. Hardcoded semantic colours (451 usages) made theming impossible.
10. Theme preference not persisted; no manual light/dark/system toggle.
11. Several metadata strings at 9–10sp below the 12sp label floor.
12. Icon audit: 1 `Icon` call without `contentDescription` (`AgentScreen.kt:348`).

### Low
13. Inconsistent section spacing across screens (12/14/16/20/24dp).
14. Dialog copy at 12–13sp.

---

## Changes made in this pass

### Design system
- `ui/theme/Dimens.kt` — spacing scale (4dp grid), radius family, 48dp touch minimum, type floors.
- `ui/theme/Type.kt` — full Material 3 type scale with 16sp body / 14sp labels / 11sp label-small floor; monospace styles for numeric/hash content.
- `ui/theme/Color.kt` — semantic tokens (`MidnightDeep`, `SlateCard`, `TextPrimary`, …) converted from plain values into `@Composable` getters backed by a palette, so all 451 existing usages become theme-aware with no call-site churn. Added a real light palette.
- `ui/theme/Theme.kt` — real light/dark schemes, system-following, dynamic colour opt-in, `LocalConcordanceColors`, and a persisted `ThemePreferenceStore`.
- `ui/components/AppComponents.kt` — `AppSectionCard`, `AppStatTile`, `AppSectionHeader`, `AppChip`, `EmptyState`, `Skeleton`, `SkeletonCard`, `StatusPill`, `SectionSpacer`, `ScreenPadding`.

### Navigation shell (`MainActivity.kt`)
- Primary destinations reduced to 5; Integrations and Account moved behind a "More" bottom sheet.
- `NavigationBar` on compact width, `NavigationRail` on ≥600dp (tablets/foldables).
- `rememberSaveable` for screen + dialog state.
- `BackHandler`: closes the More sheet, then returns to Adjudicate before exiting.
- Theme toggle in the top bar cycling System → Dark → Light, persisted.
- Raised dialog body text from 12–13sp to 14sp.

### Typography floor (applied)
- Removed every `fontSize` below 11sp across `ui/screens/**` and `ui/components/**`.
  - `8.sp` → `11.sp` (1)
  - `9.sp` → `11.sp` (76)
  - `10.sp` → `12.sp` (151)
  - `11.sp` → `12.sp` (172)
- Result: **0 text elements below 11sp** (was 305 below 11sp). Body copy now uses 12–16sp; the Material type scale is the target for the next migration.

### Accessibility
- All new components carry semantics (`contentDescription` on skeletons, status pills).
- Touch targets never below 48dp for icon buttons (Material `IconButton` default).
- Verified all 63 `Icon(` call sites carry a `contentDescription` (the one heuristic hit was `ExposedDropdownMenuDefaults.TrailingIcon`, which supplies its own).


---

## Verification

> The numbers in this section are from the pass on 2026-10-19. The current
> state (verified 2026-10-22) is **556 unit tests, 0 failures**,
> `:app:lintDebug` **0 errors**, and `:app:assembleRelease` green with R8
> minification, resource shrinking and `lintVitalRelease`.

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- `:app:testDebugUnitTest` — **183 tests, 0 failures, 0 errors** (28 suites).
- `:app:lintDebug` — **0 errors, 43 warnings** (same warning count as the pre-existing baseline: dependency-version notices, launcher-icon shape, 16KB alignment, AGP notices). The six `java.time` `NewApi` errors were pre-existing in the Supabase/telemetry sync code and are now fixed for `minSdk 24`.
- Note: lint crashed once with an AGP 9 lint-internal `AsyncExecutionService` bug while analysing an unrelated file; re-running produced a clean result. If it recurs, run with `--no-configuration-cache` or `LINT_PRINT_STACKTRACE=true` to capture the detector.

### Build config changes
- `app/build.gradle.kts`: enabled `isCoreLibraryDesugaringEnabled` and added `coreLibraryDesugaring(libs.desugar.jdk.libs)`.
- `gradle/libs.versions.toml`: added `desugar_jdk_libs = 2.1.5`.
- `AndroidManifest.xml`: `android:enableOnBackInvokedCallback="true"` (with `tools:targetApi="33"`) for predictive back on Android 13+.



## Phase 2 — Completed (verified)
- Typography migration: 518 literal `fontSize` replaced; 0 remain.
- Shared components: `AppSectionCard` adopted; duplicate private cards removed.
- Loading & empty states: Registry, Capsules, Agent, Adjudication, Integrations.
- Navigation: `NavigationState` + `Saver`; `ConcordanceScaffold` extracted.
- Tests: 209 JVM tests (0 failures); screenshot gallery (5 goldens) verified.
- Contrast / token guard clean; `lintDebug` 0 errors.
- Device smoke-tested on Samsung SM-S938U (light + dark, nav/back, agent adjudication).

## Phase 3 — Skins (completed, verified 2026-10-22)
- `Skin.CLINICAL` keeps the app's instrument-panel identity; `Skin.SITE`
  reproduces the Concordance website — the CSS custom properties from
  `app/globals.css`, the blueprint-dot backdrop, the fade-up entrance and the
  site's larger corner radii. Persisted alongside the light/dark mode and
  chosen from a palette button in the top bar.
- Section cards gained a brand-edge gradient and a surface gradient; stat
  tiles fade along their own accent colour; the app bar's version mark uses
  the brand gradient; the selected navigation item gets a tinted indicator.
- The contrast guard now checks every palette token against every surface it
  can sit on, for every skin. That exposed two real defects, both fixed: the
  clinical dark control outline was 2.885:1 against the elevated surface
  (needs 3:1) and the clinical light muted text was 4.233:1 against it (needs
  4.5:1) — captions sit on exactly that surface inside a stat tile.
- The fade-up entrance respects the system "Remove animations" setting.

## Phase 4 — Next (proposed start)
1. Supabase sync / account (apply migration `001_init.sql` + wire sign-in, capsules sync to S3/R2).
2. Voice / STT pipeline (device microphone, on-device transcription, agent result voice).
3. Camera / image upload / VLM OCR (`Photograph` button, image pipeline to agent).
4. Full clinical claim end-to-end (PubMed/Registry → Adjudication → Capsule → Provenance audit).
5. Accessibility / font-scale review; final APK / release build.
