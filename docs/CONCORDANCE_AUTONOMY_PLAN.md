# Concordance Autonomous Operation Plan

**Goal:** the app (and the agent it talks to) runs the research/experiment loop on its own —
high power when the user asks or the app is foreground, slow and idle in the background, and
fully offline using only the models, datasets, and papers already on the phone when there is no
network. The user never has to press "go" for routine work.

This plan is the research-backed design. It is written to be executed in phases; each phase
ships independently and is testable.

---

## 0. What the research says (and why)

Sources: Android developer docs (WorkManager, foreground services, background-task overview,
power management), WorkManager 2.7+ expedited work, Android 14 FGS-type requirements, Android 15
FGS timeouts, and the offline-first patterns used by on-device LLM apps (LM Playground,
PocketPal) with WorkManager + Room.

Key facts that drive every decision below:

1. **WorkManager is the default.** It is the only API that survives process death, reboots,
   Doze, and App Standby, and it respects constraints (network, battery, idle, charging).
   Periodic work has a **15-minute minimum** interval and is *inexact* under Doze.
2. **Expedited work** (`setExpedited()`) runs immediately in the background but consumes a
   per-app quota that only refreshes in the foreground. Use it only for user-initiated,
   short tasks. Implement `getForegroundInfo()` or it crashes on Android < 12.
3. **Foreground services** are the only way to keep a task running for > 10 minutes or while
   the device is idle, and they must show a notification. Android 14 requires a declared
   `foregroundServiceType`; Android 15 adds a 6-hour timeout for `dataSync`/`mediaProcessing`.
   Our local LLM server already uses `specialUse` (exempt from the 6-hour timeout but must be
   declared in Play Console if shipped there — not a concern for a sideloaded research app).
4. **Doze/App Standby** defer jobs; `setRequiresDeviceIdle(true)` + `setRequiresBatteryNotLow(true)`
   make a job run only when the user is not using the device and the battery is healthy.
5. **Battery optimization** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is the only reliable way
   to keep a foreground service alive on OEM skins (Samsung, Xiaomi). It must be requested
   with a user-facing explanation; Play policy restricts it, sideloaded research apps are fine.
6. **Offline-first = Room + outbox.** Reads come from Room; writes land in an outbox table;
   WorkManager syncs when `NetworkType.CONNECTED`. This is the pattern used by every
   offline-first Android app in 2025–2026.
7. **On-device LLM inference** (llama.cpp as a FGS, already implemented) is the offline
   brain. RAG over local papers uses SQLite FTS5 (already available via Room/SQLite) — no
   cloud vector store needed offline. Cloud Vectorize is used only when online.
8. **Thermal/battery safety:** check `BatteryManager` before starting heavy work; pause below
   20%; respect `PowerManager.isPowerSaveMode()`.

---

## 1. Operating modes

Three modes, one state machine in a new `AutonomyModeManager` (Room-backed, observable):

| Mode | Trigger | Behaviour |
|---|---|---|
| **HIGH_POWER** | User taps "Run now", or app is in foreground with an active goal | Foreground service. Full network + local model. Runs the research loop to completion (hypothesis → gate → experiment → critic → paper). Wake lock held. Notification shown. |
| **IDLE_SLOW** | App backgrounded AND device idle AND battery not low | WorkManager periodic, 15 min, `setRequiresDeviceIdle(true)` + `setRequiresBatteryNotLow(true)`. Runs **one** small step per invocation (one hypothesis scored, one paper chunk indexed). No network required. |
| **OFFLINE** | No network (ConnectivityManager) | Disables all cloud calls. Uses on-device model + local corpus + local paper chain. Results are queued in the outbox and synced when connectivity returns. |

Mode transitions are automatic but user-overridable from Settings (force mode, or "never auto").

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose)                                                │
│   Mode badge · "Run now" · "Pause" · Idle toggle · Battery   │
└────────────┬────────────────────────────────────────────────┘
             │
┌────────────┴────────────────────────────────────────────────┐
│  AutonomyModeManager (Room-backed StateFlow)                 │
│   - current mode, goal queue, outbox, last-run timestamps    │
└────┬──────────────┬──────────────────────┬──────────────────┘
     │              │                      │
     ▼              ▼                      ▼
┌─────────┐  ┌──────────────┐  ┌────────────────────────────┐
│ FGS     │  │ WorkManager  │  │ NetworkMonitor             │
│ HighPow │  │ IdleSlow     │  │ (ConnectivityManager)       │
│ Runner  │  │ Worker (15m) │  │ → flips OFFLINE mode       │
└────┬────┘  └──────┬───────┘  └────────────┬───────────────┘
     │              │                       │
     └──────┬───────┴───────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────────┐
│  ResearchEngine (shared)                                     │
│   - mode-aware router: online → agent/edge/cloud; offline →  │
│     local model + local corpus + paper chain                 │
│   - outbox: deferred cloud calls                            │
└────────────┬────────────────────────────────────────────────┘
             │
┌────────────┴────────────────────────────────────────────────┐
│  Local store (Room)                                          │
│   - research_jobs, hypotheses, experiments, papers, corpus,  │
│     outbox, mode_state                                       │
│  Local model (llama.cpp FGS) · Kokoro TTS · local files      │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 New / changed components

| Component | Where | Reuses |
|---|---|---|
| `AutonomyModeManager` | `com.example.concordance.autonomy` | Room, StateFlow |
| `AutonomyMode` enum | same | — |
| `HighPowerResearchService` (FGS) | `com.example.concordance.autonomy` | extends pattern of `LocalLlmService` |
| `IdleSlowResearchWorker` | `com.example.concordance.autonomy` | `CoroutineWorker` + `setForegroundInfo` |
| `OfflineCorpus` (Room + FTS5) | `com.example.concordance.data` | `ConcordanceDatabase` |
| `OutboxRepository` | `com.example.concordance.data` | Room |
| `NetworkMonitor` | `com.example.concordance.util` | ConnectivityManager |
| `BatteryGate` | `com.example.concordance.util` | BatteryManager |
| Settings UI | `ByokSettingsTab` / new `AutonomySettingsCard` | existing card pattern |
| Agent-side: `/api/research` already mode-agnostic; add `/api/sync/outbox` for deferred calls | `app/main.py` | existing |

---

## 3. Phase plan

### Phase 1 — Mode state + scheduling skeleton (no behaviour change)
**Goal:** introduce the mode machine and WorkManager jobs; nothing runs yet.

- Add `AutonomyMode` enum (`HIGH_POWER`, `IDLE_SLOW`, `OFFLINE`, `PAUSED`) and `AutonomyModeManager` (Room table `autonomy_state`, single row, StateFlow).
- Add `NetworkMonitor` (ConnectivityManager callback) → sets OFFLINE automatically; restores previous mode on reconnect.
- Add `BatteryGate` (battery %, power-save, charging).
- Settings card: mode badge, "Force mode" dropdown, "Auto" toggle, "Run now", "Pause".
- Register `IdleSlowResearchWorker` as a unique periodic work (15 min) with constraints:
  `setRequiresDeviceIdle(true)`, `setRequiresBatteryNotLow(true)`, `setRequiredNetworkType(NOT_REQUIRED)`.
  Do **not** enqueue it until the user enables idle mode.
- Permissions: add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (prompt with explanation), `RECEIVE_BOOT_COMPLETED` (re-enqueue after reboot), `ACCESS_NETWORK_STATE` (already present).

**Deliverable:** mode badge moves between ONLINE/OFFLINE automatically; idle worker is scheduled but does nothing yet.

### Phase 2 — Offline corpus + local RAG
**Goal:** the phone can answer research questions with no network.

- Add Room entities: `CorpusChunk(id, sourcePath, title, chunkIndex, text, ts)` with an FTS5 virtual table `corpus_fts` (Room `@Fts4`).
- Indexer: walk `files/models`, `files/kokoro`? No — walk `files/papers`, `files/extracts`, and the agent's pushed `data/research` artifacts. Chunk (512 tokens, 64 overlap), insert. Run as a one-time WorkManager job and on file changes.
- `OfflineResearchEngine.query(q)`:
  1. FTS5 top-k chunks.
  2. Build prompt with chunks as context.
  3. Call on-device LLM (llama.cpp FGS) via `LocalLlmService.baseUrl()`.
  4. Run the same gates (decision model is pure Kotlin? No — port the scoring to Kotlin or call the agent's `/api/decision/score` when online; offline, use a Kotlin port of `decision.py`). For offline, score with the Kotlin port and write the paper chain to local files.
  5. Write the paper chain to `files/research/<run_id>/` (same filenames as the agent).
- Add a "Sync to cloud" button that pushes the local run to the agent when online (outbox).

**Deliverable:** airplane-mode research run produces a verified paper chain locally.

### Phase 3 — High-power foreground runner
**Goal:** one tap (or an automatic trigger) runs the full loop at full speed.

- `HighPowerResearchService` (FGS, `specialUse`): holds a wake lock, shows a progress notification, runs `ResearchEngine.runHighPower(goal)` which drives the entire loop using all connections (agent, edge, Workers AI, Supabase, cloud model). Updates notification with step + progress.
- Triggered by: user "Run now", or an idle slow worker that promoted a goal to high power (only when foreground + charging + not battery-saver).
- On completion: writes paper chain locally AND pushes to the agent (outbox), notifies user.
- Safety: abort if battery < 20% or thermal throttling; resume when conditions recover (checkpoint in Room).

**Deliverable:** "Run now" button completes a full research run with a notification, surviving screen-off.

### Phase 4 — Idle slow background worker
**Goal:** the phone does useful work when the user is not using it.

- `IdleSlowResearchWorker` (CoroutineWorker, 15 min, `setRequiresDeviceIdle(true)`, `setRequiresBatteryNotLow(true)`):
  - If mode is PAUSED or battery < 30%: return `Result.success()` (skip).
  - Pop one queued goal step from Room.
  - Run **one** unit of work: score one hypothesis, index one paper, or draft one section. Never a full run.
  - If a step promotes a goal to "ready for high power", mark it; high power runs only when foreground/charging.
  - `setForegroundInfo` for Android < 12 (notification).
  - Return `Result.retry()` on transient failure with exponential backoff.
- Scheduling: `enqueueUniquePeriodicWork("idle_slow", KEEP, request)` on boot (via `BootCompletedReceiver`) and on user toggle.

**Deliverable:** with the screen off and charger connected, one small step completes every ~15 min and shows a notification.

### Phase 5 — Outbox sync
**Goal:** offline work reaches the cloud when connectivity returns.

- `OutboxRepository` table `outbox(id, kind, payloadJson, createdAt, attempts, lastError)`.
- Kinds: `research_run`, `paper`, `hypothesis`, `experiment`.
- `SyncWorker` (periodic, `NetworkType.CONNECTED`, 15 min, or expedited on reconnect): drains outbox, POSTs to agent endpoints (`/api/research`, `/api/papers`, …), marks synced, retries with backoff.
- On conflict (server has newer): keep both, flag for review (no silent overwrite).

**Deliverable:** airplane-mode runs appear on the agent after reconnect.

### Phase 6 — Settings, battery prompt, and observability
- `AutonomySettingsCard` in BYOK/Integrations: mode badge, force mode, auto toggle, idle toggle, battery threshold slider, "Run now", "Pause", outbox count, last-run timestamps.
- Battery-optimization prompt on first enable of idle mode.
- Telemetry: mode transitions, steps completed, battery at start/end, thermal state — logged to local `logs/` (already in `storage.LOGS_DIR`).

---

## 4. Offline-mode details

- **Detection:** `NetworkMonitor` uses `ConnectivityManager.registerDefaultNetworkCallback`; flips to OFFLINE within ~1 s of losing connectivity, restores mode on regain.
- **No cloud calls in OFFLINE:** `ResearchEngine` checks mode; if OFFLINE, skips agent/edge/Workers AI/Supabase and uses local model + corpus.
- **Local inference:** `LocalLlmService` (llama.cpp FGS) must be started; if not, the idle worker starts it for the duration of the step and stops it (to save battery). High power keeps it running.
- **Corpus:** papers already on phone (`/sdcard/Download/concordance-local/`, `files/papers`), extracted counts, and the agent's pushed `data/research` artifacts. FTS5 over chunks.
- **Papers produced locally** go to `files/research/<run_id>/` with the same filenames and a local signed manifest (reuse `paper_chain.py` logic ported to Kotlin, or call the agent when online). Offline manifest uses a local HMAC key stored in the Keystore.

---

## 5. High-power vs idle vs offline — decision table

| Condition | Mode | Work allowed |
|---|---|---|
| User tapped "Run now" | HIGH_POWER | Full loop, all connections |
| App foreground, goal queued, charging | HIGH_POWER | Full loop |
| App background, device idle, battery ≥ 30% | IDLE_SLOW | One step, no network needed |
| No network | OFFLINE | Local model + corpus only; cloud calls queued |
| Battery < 20% or power-save mode | PAUSED | Nothing; resume when recovered |
| User tapped "Pause" | PAUSED | Nothing until resumed |

---

## 6. Implementation order (suggested)

1. Phase 1 (mode machine + NetworkMonitor + BatteryGate + settings card) — 2–3 days.
2. Phase 4 (idle slow worker) — 1–2 days; reuses Phase 1.
3. Phase 2 (offline corpus + FTS5 + local RAG) — 3–4 days; the biggest piece.
4. Phase 3 (high-power FGS) — 2 days.
5. Phase 5 (outbox sync) — 1–2 days.
6. Phase 6 (polish, prompts, telemetry) — 1 day.

Total ≈ 2 weeks of focused work. Phases 1+4 can ship alone and immediately give "slow background" behaviour.

---

## 7. Risks and mitigations

- **OEM battery killers** (Samsung/Xiaomi) may kill the FGS despite the wake lock. Mitigation: request battery-optimization exemption; document the manual "don't optimize" step; fall back to WorkManager (which is OEM-aware).
- **Doze defers periodic work** by up to 2× the interval. Mitigation: idle mode is best-effort by design; high-power mode uses a FGS which is exempt.
- **Thermal throttling** during inference: pause and retry with backoff; log thermal state.
- **Storage:** local corpus + papers can grow. Add a retention policy (keep last N runs, LRU eviction of corpus chunks).
- **Security:** offline manifest HMAC key in Android Keystore; never in SharedPreferences.

---

## 8. Open questions for you

1. Should idle mode run **only while charging**, or whenever the device is idle + battery ≥ 30%?
2. Should high-power mode auto-start when a goal is queued and the app is foreground, or only on explicit "Run now"?
3. Offline corpus: index **all** files in `Download/concordance-local/` (including models? no — papers only), or let the user pick folders?
4. Notification verbosity: one notification per step, or one persistent "autonomy running" notification with progress?

Answer these and Phase 1 can start immediately.
