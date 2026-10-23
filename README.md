# Concordance (Android)

Detection-corrected claim adjudication for oncology research. The app runs the
statistical engine on-device and can additionally talk to a **deployment of the
adjudication agent** (the `oncology-agent` service) — the operator's cloud
instance by default, or a self-hosted one — for durable jobs, figure OCR and
plain-language explanations.

This app is a research workflow tool, not a clinical decision support device. See the
in-app "Regulatory & scope" dialog.

## Agent tab

The `Agent` destination connects the phone to your own agent instance.

| Concern | Where it lives |
|---|---|
| Base URL, API key, easy-mode default, upload acknowledgement | `integration/agent/AgentSettingsStore.kt` (private prefs) |
| HTTP contract | `integration/agent/AgentClient.kt` |
| Screen | `ui/screens/AgentScreen.kt` |
| Easy-mode policy (caveats, spoken summary) | `agent/EasyMode.kt` |
| Voice in/out | `voice/OnDeviceSpeechRecognizer.kt`, `voice/SpeechSynthesizer.kt`, `voice/KokoroTtsSynthesizer.kt` |

The release default is the operator's cloud agent
(`https://tap4500--concordance-agent-fastapi-app.us-east.modal.run`), so a phone
works with no laptop in the loop. The field stays editable: a self-hosted agent
on the LAN is still supported. The app permits cleartext only for `10.0.2.2`,
`localhost` and `127.0.0.1`; every other host must use HTTPS. For local
development, run `adb reverse tcp:8000 tcp:8000` and point the app at
`http://127.0.0.1:8000`.

Cloud is not a licence to move PHI: the agent still refuses
`data_class: phi` when it would be routed off-host, and the app shows that
refusal as a result. Cloud adjudication is for aggregate or public material;
raw patient data stays on the device. See `docs/DEVICE_TEST.md` for the
on-device checklist.

The tab does four things: connect, dictate, **adjudicate**, and **correct**. *Adjudicate*
asks whether a claimed difference survives the study's design. *Correct* asks what the
difference becomes once the assay's blind spot is accounted for — site and assay
sensitivity go in; the corrected odds ratio, the plausible range, the reported retention
`r`, the threshold `r*` and the smallest defensible `r` come back. Either result can be
read aloud, and either one can come back **withheld**, which the screen shows as an
answer with its reason rather than as a zero.

### What the client is careful about

1. **A refusal is a result.** The agent answers `422` when it will not guess (unknown
   anchor, abstract-only claim, PHI routed off-host). The client models that as
   `AgentOutcome.Refused` and shows it as the answer — never as an error to retry.
2. **Easy mode is a translation, not a simplification.** The plain-language text comes
   from the agent (`/api/jobs/{id}/explain`, `/api/explain/correction`) and is rendered
   as-is next to the raw payload. The client performs no arithmetic on any number, so
   the two views cannot disagree.
3. **Audio never leaves the phone.** Dictation uses the platform's *on-device*
   recogniser. On devices where Android cannot guarantee on-device recognition the app
   reports dictation as unavailable and you type instead — it does not silently fall
   back to a network recogniser.
4. **Figures are read by your agent, and the reading is not a measurement.** Upload
   requires an explicit acknowledgement; the agent OCRs locally and returns
   `evidence_tier: derived`, `human_verified: false`. Nothing is adjudicated from OCR
   text until you confirm the numbers.

### Live work

Background automation is only trustworthy if you can see it, so the Agent tab carries a
**Live work** card next to the controls (`ui/components/LiveWorkCard.kt`):

- the job that is running *now*, with a live timer, or the last thing that finished and how
  long ago;
- the counters the workers keep — mode, step, queue, outbox, battery, network, goal;
- a short log of recent steps, one row per attempt, tagged with the source that acted
  (`slow`, `high-power`, `sync`), its outcome and how long it took.

The log lives in `autonomy/AutonomyActivity.kt` (a 40-entry ring buffer persisted to
private prefs). Workers write to it as they start and finish, so a step that never
completed — app killed, process death — is reported as *interrupted* on the next launch
instead of leaving the screen claiming work is still in flight.

## Sample data and cloud sync

Two parts of the app ship illustrative content, and both say so in the UI:

- **Job queue / worker fleet.** The Async Job Queue tab seeds two completed jobs and four
  worker nodes for demonstration. They carry `isSample = true`, the tab renders a **SAMPLE
  DATA** banner, and a job submitted in the app is simulated locally: no remote worker is
  contacted, the result summary is prefixed `SIMULATED RESULT`, and no capsule hash is shown.
- **Synthetic paired dataset.** `PairedAnalyzer.sampleBenchmarkDataset()` tags every row
  `synthetic`, so any verdict built from it is withheld.

Capsule sync to S3 signs **virtual-hosted-style** requests
(`https://<bucket>.s3.<region>.amazonaws.com/<key>`) — the only style S3 accepts for buckets
created after 2020-09-30 — and returns a real presigned GET link (one hour by default), not a
plain object URL. `AwsCloudManager.updateConfig(..., sessionToken = ...)` accepts STS/Cognito
temporary credentials; long-lived IAM user keys still work but are not the supported
deployment. A failed upload reports its HTTP status instead of being described as a sync.
Credentials are entered in **Integrations → AWS S3 Cloud Vault** (access key, secret, optional
session token, region, bucket, optional custom endpoint) and stored through `KeyStore`, i.e.
`EncryptedSharedPreferences`.

Claims carry their own `data_class` (schema v7). A record classified as anything other than
`aggregate` does not leave the device: `pushClaims` marks it `blocked` and records a conflict
instead of uploading it, and `publishClaim` refuses outright. Records that predate the column
migrate to `aggregate`, which is how they were pushed at the time.

## Voice output: Kokoro

Kokoro-82M is a text-to-speech model, so it covers the agent *speaking back*. It is
**not** bundled in the APK. Two different kinds of file are involved, and they live in
two different places — this matters, because Android 10+ will not execute a binary that
was copied into the app's own storage (W^X).

| File | Where it goes | Why there |
|---|---|---|
| `kokoro.onnx` — model graph (82 M params, ~330 MB) | `<filesDir>/kokoro/kokoro.onnx` | Data, read by ONNX Runtime. App storage is fine. |
| `voices.bin` — voice/style embedding | `<filesDir>/kokoro/voices.bin` | Raw little-endian `float32`. |
| `vocab.json` — phoneme symbol → id table | `<filesDir>/kokoro/vocab.json` | Must come from the **same** Kokoro release as the graph. |
| `libespeakng.so` — the phonemiser (G2P) | `app/src/main/jniLibs/<abi>/libespeakng.so` | It is an **executable**, so it must be extracted from the APK into the native library directory. |

Kokoro consumes phoneme token ids, not letters, so grapheme-to-phoneme conversion needs
`espeak-ng`; there is no dependable pure-Kotlin English G2P. Note that this must be an
**Android binary built for the device ABI** (`arm64-v8a` on essentially all current
phones) — a desktop `espeak-ng` will not run on the device.

Loading model files onto a debug build:

```bash
PKG=com.aistudio.concordance.rkzv
adb shell run-as $PKG mkdir -p files/kokoro
for f in kokoro.onnx voices.bin vocab.json; do
  adb push "$f" /data/local/tmp/"$f"
  adb shell run-as $PKG cp /data/local/tmp/"$f" files/kokoro/"$f"
done
adb shell run-as $PKG ls -l files/kokoro     # confirm all three arrived
```

Then add the phonemiser to `jniLibs/` and rebuild. If anything is missing the app says
**exactly which file** and where it looked, and falls back to the phone's built-in
TextToSpeech engine. Nothing is downloaded automatically — model weights are never
pulled from a managed platform.

## Build and test

The Gradle wrapper is pinned to **Gradle 9.3.1** and the toolchain needs **JDK 21**.
Robolectric's `sdk = [36]` sandbox refuses to start on a lower JDK, so the unit-test JVM is
pinned to 21 in `app/build.gradle.kts`; a JDK 17 `JAVA_HOME` still builds the app, but the
test task will ask Gradle for a 21 toolchain rather than failing with 16 sandbox errors.

```bash
# Unit tests (Robolectric + MockWebServer). No device or emulator needed.
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug
```

Key test classes:

| Class | What it locks in |
|---|---|
| `AgentClientTest` (23 tests) | Real HTTP against a mock server: refusal semantics, capsule parsing, multipart uploads with their data class, correction and its plain half, provenance digests, and that bodies are never logged |
| `EasyModeTest` (9 tests) | Mandatory caveats: "no signal" is never proof of absence; assumed numbers are flagged; the spoken summary never invents a number |
| `PcmTest` (7 tests) | The 16-bit WAV encoder used for Kokoro output, checked byte by byte |
| `VoiceAssetsTest` (17 tests) | Phoneme parsing, vocabulary loading, voice decoding, WAV encoding, where the phonemiser is looked for, and the honest "what is missing" reporting |
| `StatisticalEngineValidationTest` | Refuses anchors the correction cannot estimate (zero-event or 100%-event groups, partial counts, summary anchors without an interval or prevalence) instead of defaulting or emitting `NaN` |
| `CapsuleIntegrityTest` | The v2 digest covers every field (verdict prose, assumptions, timestamps, permalink); legacy v1 capsules still verify |
| `S3SigV4Test` | Presigned URLs match botocore byte for byte; virtual-hosted addressing, percent-encoded keys, session tokens |
| `HardeningTest` | Tasker bridge stays bounded; seeded/simulated jobs are labelled; the loopback model server requires its session key |
| `PairedCsvTest` | Quoted CSV fields and BOMs do not shift columns; a genuinely short row is still rejected |
| `DataClassBoundaryTest` | Only `aggregate` claims may sync or publish; the wire payload carries the record's own class |

The module currently runs **314 unit tests** across 45 classes, all green, and
`:app:lintDebug` reports **0 errors** (51 warnings) with JDK 21.

### Not covered by these tests

Kokoro inference itself, audible playback, real dictation, and the screen's rendering
all require a device (and, for Kokoro, the model files above). Everything around them
is unit-tested on the JVM.
