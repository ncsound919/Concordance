# Release runbook — Android

The debug build and the 315-test unit suite do not prove a release build. The
release path is different: R8 minifies and shrinks resources, and the APK is
signed with the upload key. That combination is what ships.

## Verified 2026-10-22

| Check | Result |
|---|---|
| `:app:testDebugUnitTest`, `JAVA_HOME` = JDK 17 | 315 tests / 45 classes, 0 failures, 0 errors |
| `:app:bundleRelease` | succeeds: 89 MB AAB, `jar verified`, `CN=Concordance`, `versionCode 2` |
| `verifyReleaseSigning` with no environment | fails in ~6 s, naming the keystore and both passwords |

### Release-only failure this caught

`bundleRelease` failed at `minifyReleaseWithR8` with `Missing class
org.conscrypt.Conscrypt`, `org.openjsse.*`, `org.bouncycastle.jsse.*` and
`com.google.errorprone.annotations.*`. OkHttp probes for those TLS providers at
runtime and falls back to the platform one; they are not on the classpath. The
`-dontwarn` rules in `app/proguard-rules.pro` fix it. Nothing on the debug path
sees this, which is why the release build has to run before an upload, not
during one.

## Prerequisites

| Thing | Where it lives |
|---|---|
| JDK 21 | required for the Robolectric suite (`@Config(sdk = [36])`) |
| Android SDK | `local.properties` (`sdk.dir=…`), or `ANDROID_HOME` on CI |
| Upload keystore | **not in this repository.** `KEYSTORE_PATH` points at it |
| `STORE_PASSWORD`, `KEY_PASSWORD` | the secret store, exported for the build |

`app/build.gradle.kts` falls back to `${rootDir}/my-upload-key.jks` when
`KEYSTORE_PATH` is unset. That file is gitignored, and on a clean checkout it
does not exist — which is why the build fails with a missing-file error rather
than a signing error.

## Versioning

- `versionCode` must increase for **every** upload, including re-uploads to the
  internal track. Play rejects a reused code permanently.
- `versionName` is human-facing: `1.0.0`, `1.0.1`, `1.1.0`.
- Both live in `app/build.gradle.kts` (`defaultConfig`).

## Build

```bash
./gradlew :app:verifyReleaseSigning      # fails fast with what is missing
./gradlew :app:bundleRelease
```

Produces `app/build/outputs/bundle/release/app-release.aab`.

`verifyReleaseSigning` checks the keystore file and both password variables
before Gradle spends minutes compiling, so a misconfigured environment fails in
seconds with a list of the missing pieces instead of a stack trace from the
signing task.

## Verify the artifact before uploading

```bash
# Signed with the upload key, not the debug key?
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | head

# Version and package that Play will see
bundletool dump manifest --bundle app/build/outputs/bundle/release/app-release.aab \
  | grep -E "versionCode|versionName|package"
```

`bundletool` is not required at build time; `keytool` ships with the JDK.

## Play listing

Copy already lives in `fastlane/metadata/android/en-US/`:

| File | Limit | State |
|---|---|---|
| `title.txt` | 30 chars | present |
| `short_description.txt` | 80 chars | present |
| `full_description.txt` | 4000 chars | present |
| `changelogs/<versionCode>.txt` | 500 chars | present for `2` |

Still required in the Play Console, and **not** storable in this repository:

- App icon (512×512) and feature graphic (1024×500).
- At least 2 phone screenshots — these need a device or emulator.
- A privacy policy URL on a public HTTPS host.
- Data safety form (what is collected: account email, claim text; what is not:
  PHI, which never leaves the device).
- Content rating questionnaire.
- Target audience and ads declaration (no ads).

Upload with the console, or with supply once the account is linked:

```bash
fastlane supply --aab app/build/outputs/bundle/release/app-release.aab \
  --track internal --json_key /path/to/play-api-key.json
```

## CI

`.github/workflows/android-ci.yml` runs the suite and a debug APK on every push.
The signed AAB job is gated on the repository variable `RELEASE_SIGNING_READY`,
so it cannot fail every build until the key exists. To turn it on:

1. Add the secrets `ANDROID_UPLOAD_KEYSTORE_BASE64` (base64 of the `.jks`),
   `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD`.
2. Set the repository variable `RELEASE_SIGNING_READY=true`.
3. Tag the commit. The job uploads `app-release-aab` as an artifact; it does not
   publish to Play by itself.

Never commit the keystore. A leaked upload key cannot be rotated without Play
App Signing support, and a wrong password locks the app update path.

## Size

The debug APK is ~122 MB, dominated by the bundled llama.cpp native libraries.
Play's base-module limit is 200 MB compressed, so it fits — but the download
size is a product decision:

- **Bundle the model** — works offline out of the box, large install.
- **Download on first use** — small install, needs a model host and a
  connection once.

Do not "fix" this by excluding native binaries: the audit that tried to trim
them left them in place on purpose, because a wrong exclusion breaks local
inference silently. `packaging { jniLibs { useLegacyPackaging = true } }` is also
deliberate: the app loads the libraries from `nativeLibraryDir`.

## Rollback

Play does not allow reusing a `versionCode`, and a staged rollout can be halted
(not reverted). Halt the rollout, fix forward with a higher `versionCode`, and
keep the previous AAB archived with its signing certificate so an emergency
rebuild reproduces it.
