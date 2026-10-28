# Security policy — Concordance

## Reporting

Report a vulnerability privately to the repository owner. Do not open a public
issue containing a token, a signing key or a live endpoint credential.

## What the app handles

- Adjudication claims and their supporting anchors.
- A bearer token for the agent endpoint, stored on the device.
- An optional deep link that carries an anchor id.

## Controls in place

- Deep links accept only `ACTION_VIEW` and extract a validated anchor id; the
  raw URI is not stored or forwarded.
- Session material is kept in app-private storage, not in logs.
- Release builds do not enable cleartext traffic to arbitrary hosts.
- The unit suite covers deep-link parsing and navigation state.

## Known gaps

- **Release signing is a placeholder.** Do not distribute a build until the real
  upload certificate is recorded and the APK is verified with `apksigner`.
- **No certificate pinning.** The agent connection relies on platform TLS.
- **App Link association is unverified on device**, so a hostile app could
  potentially claim the same host until the `assetlinks.json` is validated.
- **No device-level secure storage test.** Unit tests use fakes for the
  keystore boundary.

## Secrets

No API keys, keystore passwords or signing keys belong in this repository.
Configuration reads credentials from environment variables or local files that
are git-ignored.
