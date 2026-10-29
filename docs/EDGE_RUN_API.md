# Edge Run API — the app half of the execution contract

**Status:** contract only. The app-side types exist (`ExperimentSpec`, `RunPlan`, `RunRecord`,
`Adjudicator`); `concordance-edge` does not implement these routes yet.

The app never holds a Kaggle key. It submits a compiled plan to the edge, which owns the
credential, the Kaggle push, the polling and the artifact download.

---

## 1. Auth

`Authorization: Bearer <run-token>` on every call. Same token model as the existing edge.
The per-run token handed to a kernel is **different**: scoped to one run, write-only, and
expires when the run reaches a terminal state.

---

## 2. Routes

### `POST /api/runs`

Body: the compiled `RunPlan` (see `RunPlan.kt`), plus the originating spec hash.

```jsonc
{
  "specId": "rp-esr1-cns-001",
  "specVersion": 1,
  "specHash": "<64 hex>",
  "planHash": "<64 hex>",
  "backendId": "kaggle",
  "kernelRef": "concordance/rp-esr1-cns-001",
  "datasets": [{ "ref": "...", "version": "...", "sha256": "...", "mountPath": "/kaggle/input/..." }],
  "seeds": [20261028],
  "determinism": "BITWISE",
  "estimatedCpuMinutes": 15,
  "estimatedGpuMinutes": 0,
  "requiredArtifacts": ["results.json", "session_info.json"]
}
```

Responses:

| Status | Meaning |
|---|---|
| `201` | `{ "runId": "...", "state": "QUEUED", "quota": { "gpuHoursRemaining": 27.4 } }` |
| `409` | `{ "code": "DUPLICATE_PLAN", "runId": "<existing>" }` — same `planHash` already run |
| `422` | `{ "code": "QUOTA_EXHAUSTED" \| "GPU_SESSION_LIMIT" \| "KAGGLE_UNCONFIGURED", "detail": "..." }` |
| `503` | Kaggle unreachable; retryable |

`GPU_SESSION_LIMIT` is the important one: Kaggle allows **2 concurrent batch GPU sessions**
and its CLI exits 0 when the third push is rejected. The edge must detect this and return
`422`, never a success.

### `GET /api/runs/{runId}`

```jsonc
{
  "runId": "...",
  "state": "RUNNING",
  "kernelVersion": 3,
  "startedAt": "...",
  "progress": 0.4,
  "quota": { "gpuHoursUsed": 2.6, "gpuHoursRemaining": 27.4, "refreshesAt": "..." },
  "ledger": ["PLANNED", "APPROVED", "QUEUED", "RUNNING"]
}
```

`state` is the same `RunState` enum the app already models (`RunRecord.kt`). The edge owns
the authoritative copy; the phone reconciles.

### `POST /api/runs/{runId}/cancel`

Idempotent. `202` with the resulting state. Cancelling a run in `COMPLETED` returns `409`.

### `GET /api/runs/{runId}/artifacts`

```jsonc
{
  "artifacts": [
    { "name": "results.json", "sha256": "<64 hex>", "bytes": 1834, "url": "<presigned R2 GET>" },
    { "name": "session_info.json", "sha256": "<64 hex>", "bytes": 512, "url": "..." }
  ]
}
```

The app downloads, re-hashes each artifact, and only then sets `RunArtifact.verified = true`.
An unverified artifact is refused by `Adjudicator` before a claim can change.

### `POST /api/runs/{runId}/adjudicate`

The app mints the capsule and posts the **result**, not the request for a result:

```jsonc
{ "claimUpdate": { "status": "SUPPORTED", "metric": "...", "value": 0.2575, "runHash": "..." } }
```

The edge stores it and returns the capsule id. It never recomputes a verdict.

### `GET /api/experiments/catalog`

Returns the same `experiment-specs.json` the app bundles, so a phone can detect that its
catalog is stale. Content-addressed: `{ "sha256": "...", "specs": [...] }`.

---

### Serialisation note

The app builds request bodies with `org.json`, which escapes forward slashes (`\/kaggle\/input\/`).
That is valid JSON and any parser unescapes it, but the edge must **parse** the body — a
substring check for `/kaggle/input/` will fail. The app-side test asserts on parsed JSON for
exactly this reason.

## 3. Kernel contractA kernel receives:

| Input | Where |
|---|---|
| the spec | `/kaggle/working/spec.json` |
| the seed list | inside the spec's method block |
| datasets | `/kaggle/input/<ref>/<version>` (mounted read-only) |
| the run token | environment variable, never in the notebook body |
| the report URL | environment variable |

It must write `results.json` and the declared `expectedArtifacts`, plus a
`session_info.json` capturing Python version, package versions, image digest, and the
spec hash. Without `session_info.json` the run is `ARTIFACT_MISSING` and cannot be
adjudicated.

A kernel that cannot run (dataset missing, quota, unsupported accelerator) exits non-zero
**and** writes `refusal.json` with `{ "code", "reason" }`. The edge maps that to
`RunOutcome.Refused`, which is a result, not an error.

---

## 4. What the phone does not do

- It does not hold Kaggle credentials.
- It does not poll Kaggle directly.
- It does not compute a verdict for a remote run; it adjudicates the artifact set locally and
  posts the outcome.
- It does not submit a run whose `CompileResult` is `Refused`.

---

## 6. Verified against live Kaggle (2026-10-29)

Three kernels were pushed and completed against account `tap919`. What the live API actually
does, correcting assumptions in this document:

| Assumption | Reality |
|---|---|
| `POST /kernels/push` takes form fields | the current client posts **JSON** (`ApiSaveKernelRequest`: `new_title`, `kernel_type`, `is_private`, `enable_gpu`, `enable_tpu`, `enable_internet`, `dataset_data_sources`, `machine_shape`, `session_timeout_seconds`, `kernel_execution_type`) via `save_kernel`. The legacy form contract returns `400 A language must be specified` |
| the metadata `id` selects the owner | it does not. A token pushed `tap4500/concordance-smoke-cpu` into **`tap919`**. Identity comes from the token, so the edge must name the pool it is spending, not trust the slug |
| quota is a float of hours | it is `{seconds, nanos}` objects plus `timeReserved`, `minimumTimeAllowed`, `isPayToScaleEnabled` |
| one account | two: `ncsound919` (13.5 h GPU left) and `tap919` (~30 h). Two pools, four concurrent GPU sessions |
| UTF-8 with BOM is fine | the CLI rejects a BOM (`Unexpected UTF-8 BOM`). Generated metadata must be BOM-free |

Runtime observed on a CPU kernel: Python 3.13.15, glibc 2.39, Linux 6.18.55. GPU kernel: Tesla T4,
`torch 2.11.0+cu128`, `cuda.is_available() == true`.

**Determinism check that matters:** the smoke kernel hashed a fixed string to
`72f5838b3ca8b141…` on Kaggle, and the same hash was computed on the host. That is the first
end-to-end evidence that a `BITWISE` run can be reproduced off-device.

## 7. Open questions for the edge implementation

1. Does the edge keep `RunRecord` transitions in D1 (`concordance-edge` already has a `jobs`
   table) or in a new `runs` table? Recommendation: new table, same database, because the
   existing `jobs` rows are the adjudication jobs, not experiment runs.
2. Is `GET /api/runs/{id}/artifacts` served from R2 signed links (current pattern) or streamed
   through the Worker? Recommendation: signed links; the phone is the only consumer.
3. Two Kaggle accounts: schedule as two pools with independent concurrency limits, or a single
   pool keyed by account? Recommendation: two pools, because the 2-session cap is per account.
