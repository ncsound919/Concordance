# Concordance — Integration Plan (2026-10)

12 selected third-party integrations, planned against the existing adapter pattern
(`integration/api/`): one `AdapterDescriptor` + one adapter per provider, normalized
into `NormalizedRecord`, provenance recorded on every request, BYOK gate via
`IntegrationGate`, rate limits from `RateLimitSpec`. Server-side (two-way) flows live
in `concordance-website` (`app/api/v1/`) and mirror data in Supabase.

## Placement summary

| # | Provider | Lives on | Auth | Capabilities |
|---|---|---|---|---|
| 1 | Semantic Scholar (S2 + S2ORC) | device | NONE (BYOK optional — key lifts rate limit) | SEARCH, FETCH_METADATA, FULLTEXT_LINK |
| 2 | Unpaywall | device | NONE (requiresEmail) | FULLTEXT_LINK, FETCH_METADATA |
| 3 | PMC OA (E-utilities) | device | NONE (requiresEmail) | FULLTEXT_LINK |
| 4 | bioRxiv / medRxiv | device | NONE | SEARCH, FETCH_METADATA |
| 5 | PubPeer | device | API_KEY (BYOK) | SEARCH |
| 6 | Retraction Watch | server mirror + device client | NONE (snapshot) | SEARCH (internal) |
| 7 | gnomAD | device | NONE (GraphQL) | FETCH_METADATA |
| 8 | ChEMBL | device | NONE | SEARCH, FETCH_METADATA |
| 9 | UniProt | device | NONE | SEARCH, FETCH_METADATA |
| 10 | NCI GDC | device | NONE | SEARCH, FETCH_METADATA |
| 11 | DataCite | website (`/api/v1/doi/*`) | server credentials | DOI_MINT |
| 12 | ClinVar | device (read) + website (submit) | API_KEY (BYOK, device); server NCBI account (submit) | SEARCH, FETCH_METADATA (read) |
| 13 | Zotero | device | API_KEY (BYOK) | SEARCH, FETCH_METADATA |
| 14 | OSF / Figshare | device | NONE (public) / API_KEY (BYOK, writes) | SEARCH, FETCH_METADATA |

## Integration details

### 1. Semantic Scholar (`integration/api/SemanticScholarAdapter.kt`)
- Graph API `api.semanticscholar.org/graph/v1`. `SEARCH`/`FETCH_METADATA` keyless
  (100 req/5 min); BYOK key unlocks 1 rps. `FULLTEXT_LINK` via `openAccessPdf` and
  S2ORC structured full text where available.
- Deep flow: citation-graph extension on every NormalizedRecord (citations of the
  claim's source paper) → surfaces contradicting/supporting literature during
  adjudication. Feed `externalIds` → DOI/PMID/ArXiv dedup keys.

### 2. Unpaywall (`integration/api/UnpaywallAdapter.kt`)
- `api.unpaywall.org/v2/{doi}` — email required (fits existing `requiresEmail`).
- Deep flow: given any claim's source DOI, returns best OA location → feeds the
  evidence pipeline (agent OCR of figures) with legal full text. Store `oa_status`
  in provenance metadata.

### 3. PMC OA / E-utilities (`integration/api/EUtilsAdapter.kt`)
- `eutils.ncbi.nlm.nih.gov` efetch/efree + `pmc.ncbi.nlm.nih.gov` OA. Keyless;
  3 rps without API key (BYOK key optional → 10 rps).
- Deep flow: PMC ID from PMID → full XML → section-level text chunks for embeddings
  (edge Vectorize) with figure references preserved.

### 4. bioRxiv / medRxiv (`integration/api/BiorxivAdapter.kt`)
- `api.biorxiv.org/details/{biorxiv|medrxiv}/{interval}` + details-by-DOI.
- Deep flow: version history per preprint (`version` field) → claims can be pinned
  to a specific preprint version; when a newer version publishes, flag the claim
  for re-adjudication. Search endpoint for cohort/trial-related preprints.

### 5. PubPeer (`integration/api/PubPeerAdapter.kt`)
- `pubpeer.com/api/v1/publications?pmids=` — API key (BYOK).
- Deep flow: at claim creation, look up the source PMID/DOI on PubPeer. Existing
  community comments are auto-attached as `challenge` seed material with provenance.
  Outbound (phase 2): push linkback so PubPeer threads reference the claim page.

### 6. Retraction Watch (server mirror)
- Crossref hosts the RW database (CSV snapshot). One-time ingester script +
  monthly refresh in `concordance-website/scripts/` → Supabase table
  `public.retractions` (doi, pmid, date, type, country, reason).
- Device side: no new adapter — extend dedup/fetch path to check the mirror table
  (via existing PostgREST client) and tag records `isRetracted`.
- Deep flow: any claim whose evidence set contains a retracted source is flagged
  into the existing re-adjudication/moderation queue automatically.

### 7. gnomAD (`integration/api/GnomadAdapter.kt`)
- GraphQL `gnomad.broadinstitute.org/api` — keyless, generous limits.
- Deep flow: for every variant-scoped claim, attach allele frequency (per ancestry)
  + consequence + ACMG-style flags as structured context. Guards against
  common-variant confounds in genetic association claims.

### 8. ChEMBL (`integration/api/ChemblAdapter.kt`)
- `www.ebi.ac.uk/chembl/api/data` — keyless REST.
- Deep flow: normalize drug/molecule mentions to ChEMBL IDs; pull mechanism-of-action
  and target records → "does the drug actually hit the claimed target" evidence for
  mechanism claims.

### 9. UniProt (`integration/api/UniProtAdapter.kt`)
- REST `rest.uniprot.org` — keyless, 1 rps polite.
- Deep flow: gene/protein normalization (acc IDs, pathway cross-refs) as the
  spine that ChEMBL targets and GDC mutations resolve against.

### 10. NCI GDC (`integration/api/GdcAdapter.kt`)
- `api.gdc.cancer.gov` — keyless public API.
- Deep flow: case-level and molecular cohort queries (project filters, gene
  mutations, survival) for cohort-level claims; complements cBioPortal with
  primary GDC data.

### 11. DataCite (server-side, `concordance-website`)
- New routes: `app/api/v1/doi/mint/route.ts` (and `/[id]/route.ts` for
  metadata updates). DataCite MDS API credentials stored in server env —
  never on device. Client calls the website route after capsule publication.
- Deep flow: each capsule/claim gets a DataCite DOI whose metadata carries the
  content hash + RFC 3161 timestamp reference → the DOI resolves to a landing page
  that self-verifies (hash re-check + token verify via existing verification API).
  DOI stored on the capsule row; `Capability.DOI_MINT` (already in the enum) routes
  the device call. Versioned DOIs (Draft/F individually versioned) when a capsule
  is re-adjudicated.
- Requires: DataCite member/test account. Sandbox: `api.test.datacite.org`.

### 12. ClinVar (two-way)
- Read (device, `integration/api/ClinVarAdapter.kt`): E-utilities
  `esearch/efetch` on ClinVar — clinical assertions (significance, review status)
  for a variant as evidence tier with provenance.
- Write (website, phase 2): submissions are structured XML/SPDI to NCBI —
  `app/api/v1/clinvar/submit/route.ts` batches adjudicated variant claims into a
  submission file, staged for reviewer approval before upload. Full round trip:
  CuratedVariantEvidence (CIViC-style) in → curated claim out.

### 13. Zotero (`integration/zotero/ZoteroManager.kt`)
- Zotero Web API v3 — API key (BYOK), user + group libraries.
- Deep flow: import a team's group collection → NormalizedRecords into an
  adjudication sprint's evidence pool; export a sprint's claim source set back
  as a Zotero collection with tags (`concordance:claim:<id>`). Local cache in
  Room, synced via WorkManager like the Supabase workers.

### 14. OSF / Figshare (`integration/osf/` + `integration/figshare/`)
- OSF API v2 (keyless for public nodes; BYOK for private/write). Figshare API
  (BYOK token).
- Deep flow: attach OSF projects/data sets and Figshare articles to capsules as
  artifact provenance (checksummed links); outbound: publish a sprint's aggregate
  dataset to OSF with the capsule DOI as related identifier.

## Waves

- **Wave 1 — keyless evidence intake (device adapters): ✅ SHIPPED (2026-10-21)**
  SemanticScholarAdapter (+ citation graph, `S2:` fork), EUtilsAdapter
  (PubMed esearch/esummary + PMC efetch full JATS XML, shares the `pubmed_eutils`
  NCBI rate bucket), UnpaywallAdapter, BiorxivAdapter (+ version history),
  ChemblAdapter (+ mechanisms of action), UniProtAdapter (+ human gene
  resolver), GnomadAdapter (GraphQL variant AF, GRCh38 `r4`), GdcAdapter
  (project cohort search). All registered in `IntegrationRegistry` +
  `ApiRotationManager`; fixture tests in `Wave1AdaptersTest`.
  Note: run tests with Java 21 (`JAVA_HOME` → JDK 21) — SDK 36 Robolectric
  requires it.
- **Wave 2 — BYOK + mirror:** PubPeer, Zotero, OSF/Figshare (device BYOK) and
  Retraction Watch mirror (Supabase ingest + claim flagging).
- **Wave 3 — server-side two-way:** DataCite DOI minting (needs DataCite account)
  and ClinVar submission (needs NCBI submission workflow).

## Testing & acceptance
- Every device adapter: MockWebServer tests against recorded fixtures
  (pattern matches existing adapter tests), gate tests for BYOK/license classes,
  provenance emitted with `responseSha256` for capsule embedding.
- DataCite: mint against DataCite sandbox in CI; DOI landing page must pass the
  existing capsule verification API before the route ships.
- Retraction Watch: ingest idempotency test; claim-flag end-to-end test
  (retracted source → re-adjudication queue entry).
