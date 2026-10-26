# Changelog

All notable changes to the Concordance Android app. The website's `/changelog`
reads the same notes from `app_releases.changelog_md`.

## 1.0.1 — 2026-10-26

Version 1.0.1 folds the OSS integration work into the app and removes the last
surfaces that pretended to do work they could not.

### Added

- Integration adapters for bioRxiv, ChEMBL, NCBI E-utilities, Figshare, GDC,
  gnomAD, OSF, PubPeer, Semantic Scholar and UniProt, plus an updated OncoKB
  adapter. Keys rotate across providers instead of failing on the first 401.
- Supabase sync conflict handling: conflicts are recorded and surfaced with a
  resolve action instead of silently overwriting the newer row.
- Paper chains from the agent can be listed and opened from the phone.
- A Sieve telemetry lane, enabled by build property rather than hard-coded.
- Crash capture: a bounded JSON line and a telemetry event are written before
  the process dies.

### Changed

- Skin and branding system with a picker; contrast fixes for the new palettes.
- PubChem is now an honest two-part surface: the curated ESR1 bench plus a real
  PUG-REST lookup for anything not in it. Unknown names no longer return the
  first compound in the list.
- The Kaggle panel states that kernel submission is disabled in this build
  instead of creating jobs that immediately fail.

### Removed

- The "Simulate Stuck (>5m)" button from the production async-queue screen.
- The simulated PUG-REST delay that reported a fabricated success.

### Fixed

- Release signing and versioning are checked before the build spends minutes
  compiling.
- Search queries no longer produce a result that claims to come from PubChem
  when it came from the local bench.
