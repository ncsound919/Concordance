# Concordance v1.0.1 — Release Notes

1.0.1 folds the OSS integration work into the app and fixes the release
pipeline itself.

## Install

Download `Concordance-v1.0.1.apk`, verify its SHA-256 and signing
fingerprint, then install. Android will ask you to allow installs from this
source.

**Upgrading from v1.0.0:** the 1.0.0 artifact was built by the old release
workflow and signed with the Android debug key. 1.0.1 is signed with the
Concordance upload key, so Android will refuse to install it over 1.0.0.
Uninstall the 1.0.0 preview first; your local data is not migrated by the
uninstall, so export anything you need before removing it.

## Added

- Integration adapters for bioRxiv, ChEMBL, NCBI E-utilities, Figshare, GDC,
  gnomAD, OSF, PubPeer, Semantic Scholar and UniProt, plus an updated OncoKB
  adapter. Keys rotate across providers instead of failing on the first 401.
- Supabase sync conflict handling: conflicts are recorded and surfaced with a
  resolve action instead of silently overwriting the newer row.
- Paper chains from the agent can be listed and opened from the phone.
- A Sieve telemetry lane, enabled by build property rather than hard-coded.
- Crash capture: a bounded JSON line and a telemetry event are written before
  the process dies.

## Changed

- Skin and branding system with a picker; contrast fixes for the new palettes.
- PubChem is now an honest two-part surface: the curated ESR1 bench plus a real
  PUG-REST lookup for anything not in it. Unknown names no longer return the
  first compound in the list.
- The Kaggle panel states that kernel submission is disabled in this build
  instead of creating jobs that immediately fail.

## Removed

- The "Simulate Stuck (>5m)" button from the production async-queue screen.
- The simulated PUG-REST delay that reported a fabricated success.

## Fixed

- GitHub tag releases now build a signed release APK with the upload key,
  publish its SHA-256, and print the signing certificate. The old workflow
  uploaded a debug-signed APK, which could not be installed as an update.
- Release signing and versioning are checked before the build spends minutes
  compiling.
- Search queries no longer produce a result that claims to come from PubChem
  when it came from the local bench.

## Verification

- 736 unit tests, 0 failures (10 live-network smoke tests skipped without
  credentials); `:app:lintDebug` clean.
- Built and signed with the Concordance upload key
  (`4E:60:32:...:6F:0F` SHA-256); verify the APK before installing.
