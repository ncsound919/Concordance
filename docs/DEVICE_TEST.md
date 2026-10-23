# Device test — Samsung SM-S938U (Android 17 / API 37)

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
Logical screen: 720 × 1560 px @ 300 dpi (~384 × 832 dp), landscape 832 × 384 dp
Build: AGP 9.1.1, Kotlin 2.2.10

## Passed

- Cold launch after `pm clear`, five-tab bottom navigation, tab selection.
- Adjudication of the bundled anchor: protocol **withheld** verdict with its reason, threshold
  curve marked planning-only.
- **Mint capsule**: `00c60e20`, SHA-256 sealed, three provenance records linked, permalink and
  citable reference rendered on the Capsules tab.
- Agent tab on a fresh install: cloud URL pre-filled, key empty, community URL empty.
- **Test connection** with no key and network up: *"The agent rejected the credentials (401).
  Check the API key."* — in ~1 s, no crash.
- **Airplane mode**: app opens, local screens work, the agent call reports
  `UnknownHostException` in ~3 s instead of hanging. Turning the radio back on returns the
  401 copy, so the message tracks the actual network state.
- **Publish** with no website URL while signed out: *"Set the website URL in the Agent tab
  before publishing"* (screenshot `27-publish-url-guard.png`).
- **Copy Markdown** → `Copied.` snackbar; `Copy JSON` and `Sync to Cloud` reachable.
- **File ingest**: SAF picker → `preds.csv` (pushed to `/sdcard/Download`) → file read → upload
  attempted → 401 surfaced. Plumbing works; the outcome needs a key.
- **Dictation**: permission dialog names the app; granting it starts the *on-device*
  recogniser (Samsung Soda); with no speech it reports *"No speech was recognised. Try
  again."* rather than failing silently.
- **Font scale 1.3** and **2.0**, and **landscape** (see fixes 10–11).
- **Live work card**: idle state, live `WORKING` timer, `done`/`skipped`/`failed` rows with
  durations, persistence across reinstall and force-stop.
- **Automation**: Go/Pause/Stop/Run one step, plus WorkManager registration (two app jobs
  visible in `dumpsys jobscheduler`, `SystemJobService`).

## Found on device and fixed

7. **Publish complained about sign-in before configuration.** A fresh install is both signed
   out and unconfigured, and the sign-in message sends the user to a page that cannot fix the
   missing URL. The URL check now runs first.
8. **Transport failures printed the target twice** (`…at https://host (UnknownHostException).
   Is it running? (target: https://host)`) — seven lines on a phone for four lines of
   information. The duplicate is dropped when the message already names the host.
9. **`Copy Markdown` broke mid-word** (`Copy Markdo / wn`): three equal-weight buttons plus the
   delete icon leave ~95 dp each at 384 dp. The capsule actions are now two rows.
10. **The wordmark wrapped and clipped at large font scales** (`CONCORDANC / E` at 1.3×,
    `CONCOR / DANCE` clipped at 2.0×). A brand name is exempt from text resizing (WCAG 1.4.4),
    so its scale is capped at 1.15×, single-line, ellipsized; nav labels ellipsize instead of
    hard-clipping; the `Browse Registry` link no longer stacks vertically.
11. **Landscape showed a centred column of nav items over an empty page.** Two causes: the
    rail measured itself at the row's full width while the content was a weighted
    `fillMaxSize()` child (now an 88 dp box plus `fillMaxHeight`), and a 384 dp-tall screen has
    no room for seven rail destinations (the rail now also requires 480 dp of height, so phones
    in landscape keep the tab bar).
12. **The slow worker claimed an agent check it never made** ("Slow step: agent checked, no new
    report" — there is no report endpoint and no request). It now re-indexes the corpus and says
    so, which is the work it actually does.
13. **The corpus log said "0 files indexed" for every cause.** Unreadable files were counted as
    nothing, and a folder that yields nothing was re-walked on every step (15–28 s per walk on
    this device). The log now distinguishes *no readable files*, *files found but unreadable*
    and *indexed N files*, and a fruitless walk is not repeated for six hours.

## Live work card (new)

`ui/components/LiveWorkCard.kt`, on the Agent tab, fed by `autonomy/AutonomyActivity.kt`.

| Part | What it answers |
|---|---|
| Headline | `WORKING` + the message + a live timer, or `IDLE` + the last outcome and its age |
| Counters | Mode, Step, Queue, Outbox, Battery, Network, current goal |
| Controls | Go, Pause, Stop, Run one step now, Cancel slow worker |
| Recent activity | Up to six rows: source · outcome, duration or age, message, colour-coded dot |

The log is a 40-entry ring buffer persisted to private prefs. Any entry still marked running
when the store is loaded again is rewritten as failed with *"— interrupted before it
finished"*, so a killed run cannot masquerade as live work.

## Automation experiments (2026-10-22)

Run on the device with the release agent URL and **no API key** (the key is the only missing
piece; every failure below is the expected 401).

| # | Action | Expected | Observed |
|---|---|---|---|
| 1 | `Run one step now` while Paused | skip logged, step unchanged | `slow · skipped` "Step skipped: automation is paused", Step stayed 0 |
| 2 | `Go` | mode High power, job running, visible timer | `WORKING` "High power research: research", `running 1s`, Step 1, goal recorded |
| 3 | Wait for the high-power job | 401 recorded as a failure, not a crash | `high-power · failed` "The agent rejected the credentials (401). Check the API key." |
| 4 | Go also schedules the periodic worker | slow step runs and logs | `slow · done` "Slow step: agent checked, no new report" (fixed — see 12) |
| 5 | `Pause`, then `Run one step now` | skip logged | `slow · skipped` "Step skipped: automation is paused" |
| 6 | `Stop` | mode Paused, goal cleared, step reset | Mode Paused, Step 0, goal line gone; log retained |
| 7 | Reinstall / restart the app | log and counters survive | all rows and Mode/Step/Goal restored from prefs |
| 8 | `Go` with an unroutable agent URL, force-stop after 1 s, relaunch | the dead run is not shown as live | row rewritten to `high-power · failed` "… — interrupted before it finished" |
| 9 | `Run one step now` twice in a row | second run is fast (empty-corpus backoff) | 15 s then 2 s, same truthful message, Step incremented |
| 10 | Check the scheduler | periodic work registered | two `com.aistudio.concordance.rkzv` jobs under `SystemJobService` |

## Still to test on the phone

- **Agent key** — items 3–6 and 9 of the cloud checklist below.
- **Kokoro voice files** — the app says they are missing from its files dir; a download path and
  a friendlier message are still needed (the current copy prints the full `/storage/emulated/…`
  path).
- **TalkBack** pass — not done.
- **Font scale 2.0 layout**: the metric columns on the anchor card still break into fragments
  (`Ref. prev alen ce`), and nav labels shorten to `Adj…`/`Cap…`. The chrome no longer breaks,
  but a responsive pass for the card rows is outstanding.

## Known gaps found while testing (not fixed)

- **The local corpus cannot read the phone's Concordance folder.** `/sdcard/Concordance` holds
  8,158 files (7,629 PDFs, 10 CSV, 5 MD, 4 TXT) and the app indexes none of them: it declares no
  storage permission and scoped storage hides the file contents. Even with access, only
  md/txt/json/csv/tsv are indexed, so the PDFs — the format papers actually arrive in — are
  invisible. Needs an `ACTION_OPEN_DOCUMENT_TREE` folder grant (or all-files access) plus PDF
  text extraction before the corpus is useful.
- **Kokoro copy leaks a filesystem path** into the UI.

## Cloud wiring checklist (no laptop)

The release build defaults to the operator's cloud agent
(`https://tap4500--concordance-agent-fastapi-app.us-east.modal.run`). A self-hosted agent is
still supported by editing the base URL; on a physical phone that host must be HTTPS (cleartext
is limited to the loopback aliases).

Run this on a fresh install (clear app data) and record pass/fail per line:

1. Agent tab opens with the cloud URL pre-filled and the key field empty. — **pass**
2. **Test** with no key shows the 401 copy ("rejected the credentials"), not a timeout and not
   a crash. — **pass**
3. Enter the agent key, **Test** returns `/health` OK. — *blocked: no key on this device*
4. **Adjudicate** a fixture anchor with `data_class: aggregate`; the job reaches a terminal
   status and the result renders. — *blocked: needs the key*
5. **Referee audit** with a small prediction CSV returns a verdict plus the three hashes;
   compare them with a direct call to the engine if available. — *blocked: needs the key*
6. **OCR** an image with `data_class: aggregate`; then try `phi` and confirm the refusal is
   shown as a result, not as a retryable error. — *blocked: needs the key*
7. Airplane mode: the app still opens, local screens work, and the agent call reports
   unreachable rather than hanging. — **pass**
8. **Publish** with no website URL shows "Set the website URL in the Agent tab before
   publishing" — not a network error. — **pass**
9. Once the site is hosted: set its HTTPS URL, sign in, publish a capsule, and open the returned
   permalink on a second device. — *blocked: no deployed site*
10. Rotate the phone while work is in flight; the job survives. — **pass**
    (rotated to landscape 1 s after pressing Go: the card kept its mode, goal and step, the
    foreground service kept running — `Background started FGS: Allowed` in logcat — and the
    activity was recreated without losing the run)

Items 3–6 and 9 need a real key and (for 9) a deployed website; until then they are blockers,
not passes.

## Evidence

`screenshots/device/`: `20-fresh-launch`, `21-agent-fresh`, `22-agent-test-nokey`,
`27-publish-url-guard`, `32-airplane-test-fixed`, `43-mic-nospeech`, `48-ingest-401`,
`52-font130-fixed`, `60-landscape-tabs`, `73-working`, `75-activity-log`, `78-step-done`,
`84-corpus-message2`, `92-interrupted`, `95-rotate-during-job`.

## Run 2026-10-23 — crash reporter and Play build

Installed `app-debug.apk` (127,439,992 bytes, 17:00) built from `main` after the
crash-reporter change; the device already held the release key state from the
14:00 APK.

### Passed

- Cold launch, five-tab navigation, no crash; Adjudicate renders the withheld
  verdict for the abstract-only anchor.
- **Copy Quote** → `Copied.` snackbar.
- **Mint Capsule** → `Generated citable capsule a112b41d (SHA-256 sealed over
  every field) … Linked 2 provenance records.` Capsules tab shows
  `Artifacts (2)`, the permalink `https://concordance.app/c/a112b41d`, the
  SHA-256 prefix and the provenance digest.
- **Test connection** with no key → *"The agent rejected the credentials (401).
  Check the API key. The health route is public, so it answers without a key."*
  in about 1 s.
- **Publish to community** with no website URL → *"Publish failed: Set the
  website URL in the Agent tab before publishing"* — the URL guard still runs
  before the sign-in message.
- **Live work / Go** with a research goal → mode `High power`, step 1, then
  `Slow step: no corpus folder chosen — pick one on the Agent tab`. The control
  path works; the corpus is what stopped it.
- Screenshots captured for the Play listing (720×1560, composed to 1080×1920).

### Not run

- Mic + Kokoro, camera/OCR, Supabase sync (needs a signed-in account), and the
  agent research-loop card on hardware. Airplane mode was skipped deliberately:
  the only adb transport is wireless debugging, so it would disconnect the
  device mid-run. Font scale and landscape were not re-run this session.
- The three oversized llama.cpp natives are absent from a cold clone
  (`NATIVE-LIBS.md`), so a runtime-complete build still needs them restored.
