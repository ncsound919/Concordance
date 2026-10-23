Secrets manager in use: none

# Infisical

Local development runs through [Infisical](https://infisical.com/docs/cli/overview) so no
`.env` file has to live on a developer's disk. `infisical run --env=dev -- <command>` starts
`<command>` with the project's *dev* environment injected as environment variables; the app
and the Gradle build keep reading the environment as they always have.

## Commands

| Purpose | Command |
|---|---|
| Local dev loop (build + install the debug app on the connected device/emulator) | `scripts/dev.ps1` on Windows, `bash scripts/dev.sh` elsewhere |
| Same thing, spelled out | `infisical run --env=dev -- ./gradlew :app:installDebug` |
| Unit tests (unchanged) | `./gradlew :app:testDebugUnitTest` |
| Debug APK (unchanged) | `./gradlew :app:assembleDebug` |
| Release build / Play upload signing (unchanged) | `./gradlew :app:bundleRelease` |

Only the local dev command runs through Infisical. Release builds, CI and the Play
pipeline are untouched: they need a machine identity, not a developer's CLI login.

## Environment variables

Names only — values never belong in this repository.

Read at build time by the Secrets Gradle Plugin (`app/build.gradle.kts`, `propertiesFileName = ".env"`,
`defaultPropertiesFileName = ".env.example"`) and surfaced as `BuildConfig` fields:

- `GEMINI_API_KEY`
- `KAGGLE_USERNAME`
- `KAGGLE_KEY`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_DEFAULT_REGION`
- `AWS_S3_BUCKET`
- `NCBI_API_KEY`
- `SEMANTIC_SCHOLAR_API_KEY`

Read from the process environment by the release signing config in `app/build.gradle.kts`:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

Deliberately ignored by the Secrets Gradle Plugin (`ignoreList`), read from the environment
by the Firebase App Check debug provider only:

- `FIREBASE_APPCHECK_DEBUG_TOKEN`

Nothing else is read at runtime: the agent URL and API key, the Kaggle token and the local
LLM settings live in the app's own encrypted preferences, entered on the device.

> The Secrets Gradle Plugin reads files, not the environment. `infisical run` fills the
> environment of the Gradle build (which is where `KEYSTORE_PATH`, `STORE_PASSWORD` and
> `KEY_PASSWORD` come from), while `BuildConfig` values still come from `.env` when that file
> is present. Keep `.env` out of git either way — see below.

## Finish setup

Run these once. One command per line, in PowerShell (5.1 has no `&&`).

1. Create the project for this app at https://app.infisical.com — one project, and the
   environment slug is `dev` (new projects ship with Development, Staging and Production).
2. Install the CLI:

   ```powershell
   scoop bucket add org https://github.com/Infisical/scoop-infisical.git
   scoop install infisical
   ```

   macOS: `brew install infisical/get-cli/infisical`. Any OS with Node: `npm install -g @infisical/cli`.
3. Log in (a browser window opens; on WSL 2, Codespaces or SSH with no browser use `infisical login -i`):

   ```powershell
   infisical login
   ```
4. Link this folder to the project — writes `.infisical.json` with the project id. It holds no
   secrets and is safe to commit:

   ```powershell
   infisical init
   ```
5. On the project's **Secrets Overview** page, pick the `dev` environment and drag your `.env`
   onto the page: every key is imported at once. Do the same for `staging`/`prod` when you have
   values for them.
6. Start the app through Infisical:

   ```powershell
   .\scripts\dev.ps1
   ```
7. Prove the values come from Infisical: move `.env` **out of this repository folder** (not to a
   backup name inside it — git would not ignore that), then run step 6 again. For this Android
   module the Secrets Gradle Plugin then falls back to `.env.example` placeholders, which is
   expected; the app reads no secrets from the environment at runtime.

For CI and production, don't use the developer CLI: create a **machine identity** with Universal
Auth in the project and expose `INFISICAL_UNIVERSAL_AUTH_CLIENT_ID` and
`INFISICAL_UNIVERSAL_AUTH_CLIENT_SECRET` from the platform's secret store, then call the API or
`infisical run` with those credentials. The release build keeps using `STORE_PASSWORD`,
`KEY_PASSWORD` and the upload keystore from its existing secret store.

Any secret that was ever committed to git is still in history and must be **rotated** in the
project (and at the provider); deleting the file is not enough. `infisical scan` finds leaked
secrets in the working tree and in history.
