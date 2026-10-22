# Concordance (Android)

Detection-corrected claim adjudication for oncology research. The app runs the
statistical engine on-device and can additionally talk to a **self-hosted
adjudication agent** (the `oncology-agent` service) for durable jobs, figure OCR and
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

Default URL is `http://10.0.2.2:8000` (an emulator's route to the host's loopback).
The app permits cleartext only for `10.0.2.2`, `localhost` and `127.0.0.1`; every
other host must use HTTPS. On a physical device, either configure TLS on the agent
and use `https://…`, or run `adb reverse tcp:8000 tcp:8000` and point the app at
`http://127.0.0.1:8000`. Keep the agent on your own network: it is self-hosted
precisely so nothing has to leave it.

The tab does five things: connect, dictate, **adjudicate**, **correct**, and
**referee**. *Adjudicate* asks whether a claimed difference survives the study's
design. *Correct* asks what the difference becomes once the assay's blind spot
is accounted for — site and assay sensitivity go in; the corrected odds ratio,
the plausible range, the reported retention `r`, the threshold `r*` and the
smallest defensible `r` come back. *Referee* hands a claim specification and a
prediction table to the deterministic referee on the agent and returns its
verdict together with the `report_sha256`, `decision_sha256` and `input_sha256`
digests that make the verdict checkable. Either result can be read aloud, and
either one can come back **withheld**, which the screen shows as an answer with
its reason rather than as a zero.

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
5. **A referee verdict is shown with its provenance and its digests.** The referee
   either computes from the table you supplied or says so; the screen shows which,
   and never presents a number without the `report_sha256` it came from.

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

## Appearance

The app has two **skins**, chosen independently of the light/dark setting
(palette button in the top bar):

| Skin | What it is |
|---|---|
| **Clinical** | The original instrument-panel look: deep navy, electric cyan, tight radii, glow. |
| **Website** | The Concordance website's palette and surface treatment — the same CSS custom properties the site defines in `app/globals.css`, plus the site's blueprint-dot backdrop, fade-up entrance and larger corner radii. |

Two tokens are deliberately *not* copied verbatim from the site: the website
uses `--color-text-subtle` and `--color-border-hover` for decoration and
borders, while the app uses them as small text and as an interactive control's
outline, where its own contract is stricter (4.5:1 and 3:1 against every
surface). The website skin uses the next accessible step instead. A palette
test pins the tokens that *are* copied verbatim, so the two products cannot
drift apart silently.

Both skins pass WCAG 2.2 AA in light and dark, for every token against every
surface it can sit on.

## Build and test

The Gradle wrapper is pinned to **Gradle 9.3.1** and the toolchain needs **JDK 17+**
(JDK 21 is what this repo is verified with).

```bash
# Unit tests (Robolectric + MockWebServer). No device or emulator needed.
./gradlew :app:testDebugUnitTest

# Lint. This is a build gate: it must report 0 errors.
./gradlew :app:lintDebug

# Debug APK
./gradlew :app:assembleDebug

# Release APK (R8 + resource shrinking). Needs a signing keystore; see below.
KEYSTORE_PATH=/path/to/upload.jks STORE_PASSWORD=… KEY_PASSWORD=… \
  ./gradlew :app:assembleRelease
```

JDK 21 is requested through the Gradle **toolchain** mechanism
(`app/build.gradle.kts` pins the test JVM's `javaLauncher`), not through a
machine-specific `org.gradle.java.home`, so the build works on any machine and
on CI.

The release build is verified: `:app:assembleRelease` completes with R8
minification, resource shrinking and `lintVitalRelease` all green.

Key test classes:

| Class | What it locks in |
|---|---|
| `StatisticalEngineTest` (52 tests) | The engine the whole product rests on: the Wilson interval, the inverse normal CDF against published quantiles, Fisher, the feasibility rules and the four verdicts |
| `AgentClientTest` (25 tests) | Real HTTP against a mock server: refusal semantics, capsule parsing, multipart uploads with their data class, correction and its plain half, referee adjudication with its digests, and that bodies are never logged |
| `ConcordanceContrastTest` | WCAG 2.2 AA for every palette token, in every skin, against every surface it can sit on |
| `SkinPreferenceTest` | The chosen skin survives a restart, and is independent of the light/dark mode |
| `EasyModeTest` (9 tests) | Mandatory caveats: "no signal" is never proof of absence; assumed numbers are flagged; the spoken summary never invents a number |
| `PcmTest` (7 tests) | The 16-bit WAV encoder used for Kokoro output, checked byte by byte |
| `VoiceAssetsTest` (10 tests) | Phoneme parsing, vocabulary loading, voice decoding, WAV encoding, where the phonemiser is looked for, and the honest "what is missing" reporting |

The module currently runs **556 unit tests** (10 of them live-network smoke
tests, skipped unless credentials are present), all green, and
`:app:lintDebug` reports **0 errors**.

The design gallery can be rendered without a device:

```bash
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
# writes app/build/screenshots/*.png
```

### Not covered by these tests

Kokoro inference itself, audible playback, real dictation, and the screen's rendering
all require a device (and, for Kokoro, the model files above). Everything around them
is unit-tested on the JVM.

### Releasing

The release build is signed with the keystore at `KEYSTORE_PATH` (default
`my-upload-key.jks` in the repository root) using the `upload` alias, with the
passwords taken from the `STORE_PASSWORD` and `KEY_PASSWORD` environment
variables. **No release keystore is committed**: `*.jks` and `release*.keystore`
are gitignored, and the tracked `debug.keystore` is only the standard AOSP
debug key used for local installs.

CI (`.github/workflows/ci.yml`) runs the unit tests, lint and a debug build on
every pull request; `.github/workflows/release.yml` runs the same gates before
publishing the tagged APK.
