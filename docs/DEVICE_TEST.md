# Device test — Samsung SM-S938U (Android 17 / API 37)

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
Logical screen: 720 × 1560 px @ 300 dpi (~384 × 832 dp)
Build: AGP 9.1.1, Kotlin 2.2.10, JDK 21

## Passed
- Cold launch, five-tab bottom navigation, tab selection.
- Back precedence: More sheet → home tab → system exit.
- "More" sheet lists Integrations and Account.
- Theme cycling and persistence across a force-stop (dark stayed dark).
- Light and dark palettes both render readable text and controls.
- Empty states: Capsules, Registry, Agent result, Adjudication (no anchor), Integrations lists.
- Status/navigation bar icons switch with the app theme.

## Found on device and fixed
1. **Adjudication anchor card grew a large vertical gap.** The trailing `EvidenceBadge`
   was measured with zero width, wrapped to many lines, and inflated the row. The title
   now has `Modifier.weight(1f)`; the badge keeps its intrinsic width.
2. **Top bar subtitle wrapped to two lines and then truncated.** Shortened to
   "Detection-Corrected" and fixed to one line with ellipsis.
3. **Dark theme kept a white system navigation bar.** `MyApplicationTheme` now drives
   `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` from the resolved
   app theme, not the system theme.
4. **Capsules S3/R2 status wrapped into four lines** and used raw yellow on light
   surfaces. The two statuses are now stacked, single-line, and mapped to theme-aware
   `NeonEmerald` / `ElectricCyan` / `WarningAmber`.
5. **Paired "RELATIVE SENSITIVITY ESTIMATE (r)" collided with the sample count.**
   The two labels are now stacked.
6. **Text-field outlines were 1.4:1** (decorative border token). `outline` and the
   explicit `unfocusedBorderColor` now use `BorderStrong` (≈3.2:1), meeting the 3:1
   non-text contrast requirement.

## Still to test on the phone
- On-device speech recognition and Kokoro playback (microphone permission).
- Camera / file upload and the local imaging OCR path.
- Live agent connection. **The default `10.0.2.2` is the emulator host alias.** On a
  physical phone enter the workstation's LAN address, e.g. `http://192.168.1.20:8000`,
  and make sure the phone and workstation are on the same network and the agent binds
  to `0.0.0.0`.
- Supabase sign-in and sync (tables must be created first).
- Charts and long titles at system font scale 1.3–2.0.
- Rotate to landscape (rail threshold is 600dp; a 384dp-wide phone stays on the bottom bar).
