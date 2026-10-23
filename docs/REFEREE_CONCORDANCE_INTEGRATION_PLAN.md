# Referee Auditor ↔ Concordance Integration Plan

**Date:** 2026-10-20  
**Status:** Draft plan (no code changes yet)  
**Scope:** Integrate the Referee deterministic audit engine as a callable tool in the Concordance/oncology-agent stack, deploy the Referee Python service to AWS, and wire Cloudflare (edge + Workers AI) to it.

---

## 0. Summary

Today Referee (`Downloads/eco/ref/Referee-main`) is a standalone FastAPI + Next.js product. Concordance (`oncology-agent` + `concordance-edge` + Android app) already has a tool registry, an MCP server, a Cloudflare edge queue, and PHI-aware routing, but it cannot audit a prediction table. This plan adds **one new capability** — `referee_audit` — that any Concordance client (agent, MCP, Android, edge workflow) can call, and deploys the Referee Python core as a **separate, authenticated AWS service** that the agent and edge talk to over HTTPS.

The Referee Next.js UI is **not** part of this integration. Only the Python core (`referee_core/`) is deployed; the web UI's simulated metrics must not be reused (see `Referee Repo Audit and Refinement Plan.md`, findings A1–A5).

---

## 1. Goals and non-goals

### Goals
1. A Concordance tool named `referee_audit` that accepts a prediction CSV + a claim spec and returns a Referee report (verdict, dimensions, evidence, hashes).
2. The tool is available through:
   - the in-process `ToolRegistry` (`app/tools.py`),
   - the MCP server (`app/mcp_server.py`),
   - a FastAPI route (`POST /api/referee/audit`),
   - the Cloudflare edge job queue (`POST /api/jobs` with `tool: referee_audit`),
   - the Android `AgentClient`.
3. The Referee Python service runs on AWS (containerized, autoscaled, authenticated) and is the **single source of truth** for audit numbers.
4. Cloudflare edge can submit referee jobs asynchronously and receive completed reports via the existing publish-back contract.
5. PHI-aware routing: patient-level prediction tables are treated as PHI by default and may only reach `local` or `aws_baa` targets; managed Cloudflare inference is never used on raw audit payloads.
6. Reports remain tamper-evident: the Referee `report_sha256` / `decision_sha256` are stored and re-verified on retrieval.

### Non-goals
- Porting the Referee Next.js UI into Concordance.
- Training baselines inside Referee (still `not_assessed` unless features are supplied).
- Replacing the Concordance correction/evidence engines.
- Multi-tenant SaaS billing for Referee (single-tenant self-hosted first).

---

## 2. Current-state inventory

| Component | Location | Role |
|---|---|---|
| Referee Python core | `Downloads/eco/ref/Referee-main/referee_core/` | Deterministic audit engine, FastAPI in `api.py` |
| Oncology agent | `C:/Users/tap45/oncology-agent` | FastAPI agent, tool registry, MCP, PHI routing |
| Cloudflare edge | `C:/Users/tap45/concordance-edge` | D1/Queue/DO/R2/Vectorize front door |
| Workers AI proxy | `C:/Users/tap45/concordance-ai-worker` | LLM/vision/STT/TTS offload |
| Android client | `Downloads/eco/Concordance-main` | AgentClient HTTP contract |
| AWS account | (not yet configured) | Target host for Referee service |

Key existing contracts to reuse:
- Edge job lifecycle: `POST /api/jobs` → queue → agent `/api/adjudicate` → agent publishes to `/api/jobs/:edge_id/state` (see `concordance-edge/README.md`).
- Agent tool registry: `ToolSpec`, `ToolRegistry`, `_seed_builtins()` in `app/tools.py`.
- MCP exposure: `list_tools()` / `call_tool()` in `app/mcp_server.py`.
- PHI routing: `app/concordance/router.py`, enforced in `app/main.py`.
- Cloudflare AI client: `app/cloudflare_ai.py`.

---

## 3. Target architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│ Android app (Concordance)                                            │
│   AgentClient.refereeAudit(...) ─────────────────────────┐           │
└──────────────────────────────────────────────────────────┼───────────┘
                                                           │ HTTPS
┌──────────────────────────────────────────────────────────▼───────────┐
│ Cloudflare edge (concordance-edge worker)                            │
│   /api/jobs  /api/capsules  /api/evidence  /health                  │
│   - D1 job state, R2 capsules, Vectorize evidence                    │
│   - queues referee_audit jobs to oncology-agent                      │
│   - caches non-PHI LLM calls via concordance-ai worker              │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTPS (EDGE_KEY / AGENT_KEY)
┌────────────────────────────▼────────────────────────────────────────┐
│ Oncology agent (AWS or self-hosted)                                  │
│   /api/referee/audit  /api/referee/verify  /api/referee/pre_check    │
│   ToolRegistry: referee_audit  (builtin, active)                    │
│   MCP: referee_audit tool                                           │
│   PHI router + audit trail + store                                  │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTPS (REFEREE_API_KEY)  [VPC-internal ideally]
┌────────────────────────────▼────────────────────────────────────────┐
│ Referee service (AWS: ECS Fargate or EC2, containerized)            │
│   FastAPI referee_core.api:app                                      │
│   POST /audit  POST /verify  POST /pre_check  POST /run_experiment   │
│   Stateless; CPU-heavy (bootstraps/permutations)                    │
└──────────────────────────────────────────────────────────────────────┘
```

Principles carried over:
- Referee is the only component that computes audit numbers.
- Agent is the only component that decides PHI routing and persists results.
- Edge is the durable front door and never sees raw PHI unless explicitly allowed.
- Cloudflare Workers AI is never used on raw audit payloads.

---

## 4. Tool contract: `referee_audit`

### 4.1 Tool spec (ToolRegistry / MCP)

```json
{
  "name": "referee_audit",
  "description": "Deterministically audit an oncology ML claim from a patient-level prediction CSV using the Referee engine. Returns verdict, dimensions, evidence, and cryptographic report hashes.",
  "input_schema": {
    "type": "object",
    "properties": {
      "claim": {
        "type": "object",
        "properties": {
          "claim_id": { "type": "string" },
          "title": { "type": "string" },
          "claimed_auc": { "type": "number" },
          "claimed_auc_ci": { "type": "array", "items": { "type": "number" }, "minItems": 2, "maxItems": 2 },
          "eval_on": { "type": "string", "enum": ["test", "all"], "default": "test" },
          "score_is_probability": { "type": "boolean", "default": false },
          "split_description": { "type": "string" }
        },
        "required": ["claim_id", "claimed_auc"]
      },
      "csv_text": { "type": "string", "description": "Prediction table CSV (patient_id,label,split,score,...)" },
      "policy": { "type": "object", "description": "Optional Referee Policy overrides" },
      "data_class": { "type": "string", "enum": ["aggregate", "deidentified", "phi"], "default": "phi" },
      "target_backend": { "type": "string", "enum": ["local", "aws_baa", "aws_public", "cloudflare"], "default": "local" }
    },
    "required": ["claim", "csv_text"]
  }
}
```

### 4.2 Output (passthrough of Referee `/audit`)

```json
{
  "verdict": "REPRODUCED | NOT_REPRODUCED | FAILS_BASELINE | LEAKAGE_DETECTED | UNVERIFIABLE | OUT_OF_SCOPE",
  "reasons": ["..."],
  "dimensions": { "reproducibility": "...", "data_integrity": "...", ... },
  "evidence": { "reproduced_auc": {...}, "patient_overlap": {...}, ... },
  "input_sha256": "...",
  "decision_sha256": "...",
  "report_sha256": "...",
  "software": { "referee_core": "...", "numpy": "...", "scipy": "...", "scikit-learn": "..." },
  "disposition": "complete | incomplete"
}
```

### 4.3 Example call (MCP / tool)

```json
{
  "tool": "referee_audit",
  "arguments": {
    "claim": {
      "claim_id": "esr1-brainmet-2024",
      "title": "ESR1 Y537S brain metastasis AUC",
      "claimed_auc": 0.812,
      "claimed_auc_ci": [0.74, 0.88],
      "eval_on": "test",
      "score_is_probability": true
    },
    "csv_text": "patient_id,label,split,score\nP1,1,test,0.91\n...",
    "data_class": "phi",
    "target_backend": "local"
  }
}
```

---

## 5. Service boundaries and endpoints

### 5.1 Referee service (AWS) — `referee_core.api:app`

Existing endpoints reused as-is (after hardening in §8):

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/audit` | Run a full audit (CSV + claim + optional policy) |
| `POST` | `/verify` | Re-verify a report hash (optionally against CSV) |
| `POST` | `/pre_check` | Hypothesis pre-check (skills + license gate) |
| `POST` | `/run_experiment` | Engine snapshot execution |
| `GET` | `/health` | Liveness (to be added) |

### 5.2 Oncology agent additions

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/referee/audit` | Proxy + PHI-gate + audit-trail + persistence |
| `POST` | `/api/referee/verify` | Re-verify a stored report |
| `GET` | `/api/referee/health` | Referee service health (proxied) |
| `GET` | `/api/tools` | Already lists `referee_audit` after seeding |

### 5.3 Cloudflare edge

Reuse existing job contract; add `tool: "referee_audit"` as a recognized job type. No new edge routes required for v1, but `/api/jobs/:id/state` already accepts arbitrary result payloads.

---

## 6. AWS server deployment

### 6.1 Packaging prerequisite (from Referee audit plan, Phase 1)
Fix `pyproject.toml` packaging first:
```toml
[tool.setuptools.packages.find]
where = ["."]
include = ["referee_core*"]
```
and add a `[project.scripts]` entry point `referee-core = "referee_core.cli:main"` (verify).  
Smoke test in a clean venv: `pip install . && referee-core --help && uvicorn referee_core.api:app`.

### 6.2 Container image
Create `Downloads/eco/ref/Referee-main/Dockerfile`:
```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml ./
COPY referee_core ./referee_core
RUN pip install --no-cache-dir .
ENV REFEREE_MAX_CSV_MB=25
EXPOSE 8000
CMD ["uvicorn", "referee_core.api:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
```

### 6.3 AWS target
Recommended: **ECS Fargate** behind an **Application Load Balancer** with HTTPS (ACM cert), in a private subnet, reachable from the agent via VPC peering or public HTTPS with API key. Alternative for v1: a single **EC2** instance (t3.large) with Docker + Caddy/TLS, simpler and cheaper for a single tenant.

Sizing: Referee runs 1000 bootstraps + 999 permutations per audit. Empirically the suite takes ~2 min for 18 tests; a single audit on a modest table is typically 2–10 s. Start with 2 vCPU / 4 GB per task, concurrency limit 4 in-process (semaphore), autoscale on CPU > 60%.

### 6.4 Auth & network
- `REFEREE_API_KEY` required on every request (`X-Referee-Key` header). Agent holds it in AWS Secrets Manager / env.
- ALB security group: allow 443 from agent IPs / Cloudflare IPs only (or use a private ALB + VPC peering).
- Request body limit 25 MB (already enforced in `api.py`).
- Timeouts: ALB idle timeout 120 s; agent HTTP client read timeout 180 s.

### 6.5 Infrastructure-as-code
Add a `infra/` folder (CDK or Terraform) in the Referee repo:
- ECR repository
- ECS cluster + Fargate service + task definition
- ALB + target group + HTTPS listener
- Secrets Manager secret `referee/api-key`
- CloudWatch log group + alarms (5xx rate, latency p95, CPU)
- Optional: API Gateway in front for throttling/WAF.

Deployment pipeline: GitHub Actions → build image → push ECR → `aws ecs update-service --force-new-deployment`.

---

## 7. Cloudflare wiring

### 7.1 Edge (concordance-edge)
No new bindings required for v1. Add to `wrangler.toml` vars (plain, not secret):
```toml
REFEREE_ENABLED = "true"
```
Secrets (set via `wrangler secret put`):
- `REFEREE_URL` (e.g. `https://referee.internal.example.com`)
- `REFEREE_KEY`

The edge already proxies job submission to the agent. A referee audit job flows:
1. Client `POST /api/jobs` `{ "tool": "referee_audit", "claim": {...}, "csv_text": "...", "data_class": "phi" }`.
2. Edge enqueues to `adjudication` queue.
3. Queue consumer POSTs to agent `/api/adjudicate` (or new `/api/jobs/run`); agent runs `referee_audit` tool.
4. Agent publishes report to `/api/jobs/:edge_id/state`; edge stores in D1 + R2 capsule.
5. Client polls `GET /api/jobs/:id` or WebSocket.

### 7.2 Workers AI guard
Referee payloads are never sent to `concordance-ai`. The agent's referee route must reject `target_backend: cloudflare` when `data_class` is PHI (reuse `cloudflare_ai.is_phi`).

### 7.3 Evidence / RAG (optional v2)
Index Referee reports into Vectorize (`/api/evidence`) for literature-style retrieval of past audits. Defer to v2.

---

## 8. Code changes

### 8.1 Referee repo (`Downloads/eco/ref/Referee-main`)
- `pyproject.toml`: fix packaging (§6.1).
- `Dockerfile` (new).
- `infra/` (new, CDK or Terraform).
- `referee_core/api.py`: add `GET /health`; add API-key middleware; add request timeout; keep existing endpoints.
- `referee_core/audit.py`: apply Phase 1 fixes from the audit plan (ID normalization, NaN labels, nuisance `max(a,1-a)`, `claimed_auc_ci` usage, policy completeness, hashing). At minimum for v1: ID normalization + NaN rejection + health endpoint.
- `.github/workflows/deploy.yml` (new).

### 8.2 Oncology agent (`C:/Users/tap45/oncology-agent`)
- `app/referee_client.py` (new): async HTTP client with retries, circuit breaker, PHI-aware base URL selection, timeouts.
- `app/tools.py`: add `referee_audit` to `_seed_builtins()`; add handler binding (active only when `REFEREE_URL` set, else proposed).
- `app/mcp_server.py`: add `referee_audit` to `_TOOLS` and `call_tool()`.
- `app/main.py`: add `/api/referee/audit`, `/api/referee/verify`, `/api/referee/health` routes with auth + rate limits + PHI gate.
- `app/models.py`: add `RefereeAuditRequest` / `RefereeAuditResponse` (or reuse dict).
- `tests/test_referee_tool.py` (new): contract + PHI-gate + hash-verification tests.

### 8.3 Android (`Downloads/eco/Concordance-main`)
- `AgentClient.kt`: add `refereeAudit(config, claim, csvText)` returning `AgentOutcome<RefereeReport>`.
- Data classes for `RefereeReport` (verdict, reasons, dimensions, evidence, hashes).
- UI: new "Audit" action in Agent screen; show verdict + dimensions + "SIMULATED/REFERENCE" banner rules (only computed reports).

### 8.4 Cloudflare edge
- Document `REFEREE_URL` / `REFEREE_KEY` secrets.
- Optional: add `tool` validation in job submission (accept `referee_audit`).

---

## 9. Configuration

### Agent env (additions)
```
REFEREE_URL=https://referee.example.com
REFEREE_API_KEY=...
REFEREE_TIMEOUT_SEC=180
REFEREE_MAX_CONCURRENCY=4
REFEREE_ENABLED=1
```

### Referee service env
```
REFEREE_API_KEY=...
REFEREE_MAX_CSV_MB=25
REFEREE_WORKERS=2
REFEREE_LOG_LEVEL=INFO
```

### Edge secrets
```
REFEREE_URL
REFEREE_KEY
```

### Android
No new config; uses existing agent base URL. Report rendered from agent response.

---

## 10. Security and compliance (PHI)

1. **Classification:** a prediction CSV with `patient_id` + `label` + `split` is PHI unless explicitly de-identified and approved. Default `data_class = phi`.
2. **Routing gate:** `target_backend` must be `local` or `aws_baa` for PHI. `aws_public` and `cloudflare` are rejected with 422 (reuse existing router).
3. **Managed platforms:** Referee on AWS can be BAA-covered; Cloudflare Workers AI must never receive raw audit payloads.
4. **Auth:** every hop authenticated (edge key, agent key, referee key). Constant-time compare.
5. **Audit trail:** agent logs `referee_audit` calls (subject, claim_id, input_sha256, verdict, timestamp) to the append-only audit table.
6. **Data retention:** CSV text may be stored for re-verification; apply existing retention/erasure endpoints. Reports store hashes, not raw CSV, where possible.
7. **Size/time limits:** 25 MB CSV, 180 s timeout, concurrency cap.

---

## 11. Observability

- Referee: CloudWatch metrics (requests, latency p50/p95, 4xx/5xx, CPU), structured logs with `claim_id` and `input_sha256` (never CSV contents).
- Agent: OpenTelemetry span `gen_ai.tool.call` for `referee_audit` (already instrumented in `call_tool`).
- Edge: existing D1 job events + dashboard.
- Alerts: 5xx > 1%, p95 > 60 s, CPU > 80% for 5 min.

---

## 12. Testing plan

### Unit / contract
- `tests/test_referee_tool.py`: schema validation, PHI gate, missing fields → refusal, hash presence.
- Referee: existing 18 tests + new ID-normalization / NaN-label / nuisance tests.
- Golden-file test: fixed CSV + claim → expected `verdict` and `report_sha256` (regression).

### Integration
- Agent → Referee (local Docker) round-trip with a 50-row table.
- `/verify` re-run matches after edit detection (tamper test).
- Edge job submission → agent → publish-back → D1/R2.

### Load
- 10 concurrent audits on 5k-row CSV; measure p95 and CPU; tune workers.

### Security
- Unauthenticated requests → 401.
- PHI + `target_backend=cloudflare` → 422.
- Oversized CSV → 413.
- Tampered report → `verify` returns false.

### E2E
- Android emulator: submit audit, receive verdict, render dimensions.

---

## 13. Rollout phases

| Phase | Duration | Deliverables |
|---|---|---|
| **0. Prep** | 1–2 days | Fix Referee packaging; clean-venv smoke test; `GET /health`; API-key middleware; Dockerfile |
| **1. Referee on AWS (dev)** | 2–3 days | ECR image, ECS/EC2 dev service, TLS, secrets, CloudWatch |
| **2. Agent integration** | 3–4 days | `referee_client`, tool + MCP + routes + tests |
| **3. PHI + audit hardening** | 2 days | Routing gate, audit trail, retention, tamper tests |
| **4. Cloudflare wiring** | 2 days | Secrets, job flow docs, end-to-end edge test |
| **5. Android client** | 3–4 days | AgentClient method, data classes, UI |
| **6. Referee core hardening** | 1 week | ID normalization, NaN, nuisance, hashing (from audit plan Phase 1) |
| **7. Production cutover** | 1–2 days | Prod AWS service, DNS, monitoring, runbook |

Total: ~3–4 weeks for one developer.

---

## 14. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Referee audit is CPU-heavy and slow | Concurrency cap + async job path for large tables; autoscale |
| PHI leakage via Cloudflare | Hard gate in agent route; never send raw CSV to Workers AI |
| Report hash fragility across numpy versions | Pin `referee_core` + numpy/scipy/sklearn versions in container; store `software` block |
| Referee packaging currently broken | Fix in Phase 0 before any deployment |
| Android read timeout too short | Set 180 s read timeout for referee calls |
| Edge queue retries re-running expensive audits | Idempotency key = `input_sha256`; agent caches last result by hash |
| Referee service downtime blocks audits | Circuit breaker → `UNVERIFIABLE`-style refusal with clear reason; no fabricated defaults |

---

## 15. Acceptance criteria (v1)

1. `pip install .` + `referee-core --help` works in a clean venv.
2. Referee container runs locally and `POST /audit` returns a report with all three hashes.
3. Agent `POST /api/referee/audit` with a 100-row PHI table returns a verdict; `target_backend=cloudflare` returns 422.
4. Edge job submission of type `referee_audit` completes and stores report in R2/D1.
5. Android app can submit an audit and render verdict + dimensions.
6. `verify` returns `false` after any field of a stored report is edited.
7. No `Math.random`, numeric fallback literals, or simulated metrics reach the ledger.
8. CloudWatch dashboard shows latency and error rate for the Referee service.

---

## 16. Open questions

1. **AWS compute choice:** ECS Fargate (managed, costlier) vs EC2 (cheaper, more ops)? Recommend EC2 for v1 single-tenant, Fargate for v2.
2. **BAA:** Is the AWS account under a BAA? If yes, PHI tables may live in AWS; if no, keep audits `local` only.
3. **Idempotency cache size:** how long to cache by `input_sha256`? Recommend 24 h.
4. **Report storage:** store full CSV for re-verification or only hashes? Recommend hashes + optional encrypted CSV in R2 with retention.
5. **Baseline features:** when will Referee receive `features.csv`? Defer until Phase 2 of the Referee audit plan.
6. **MCP tool approval:** should `referee_audit` be `active` by default or `proposed` requiring human approval? Recommend `proposed` in prod, `active` in dev.

---

## 17. References

- `Downloads/eco/ref/Referee-main/README.md`
- `Downloads/eco/ref/Referee-main/ARCHITECTURE.md`
- `Downloads/eco/ref/Referee Repo Audit and Refinement Plan.md`
- `C:/Users/tap45/oncology-agent/README.md`
- `C:/Users/tap45/concordance-edge/README.md`
- `C:/Users/tap45/concordance-ai-worker/README.md`
- `C:/Users/tap45/concordance-oss/ECOSYSTEM_PLAN.md`
