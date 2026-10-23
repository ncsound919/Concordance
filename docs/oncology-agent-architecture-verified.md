# Oncology Experiment Phone App — Verified Agent Architecture (Oct 18, 2026)

This document verifies and updates the proposed three-layer agent design against the
current (October 2026) tooling landscape. Several names in the original proposal
(`AgentCurie`, `nightsitter`, `Edward`, `rtvoice`) do **not** correspond to
real, findable projects. They are treated as conceptual placeholders below and
replaced with verified, available alternatives.

## Verification Summary

| Proposed Component | Status (Oct 2026) | Verified Replacement |
|---|---|---|
| Pipecat (voice) | ✅ Real — v1.12.0 (2026-09-26), ~16.4k stars, BSD-2 | Use as proposed |
| LiveKit Agents (voice) | ✅ Real — v1.8.4 (2026-10-18), MIT | Use as proposed |
| rtvoice | ❌ Not found as a real project | OpenAI Agents SDK Realtime (gpt-realtime-2.1) or LiveKit Agents |
| AgentCurie | ❌ Not found as a real project | Google A2A protocol (Agent Cards + skills) + LangGraph/CrewAI/AutoGen |
| nightsitter | ❌ Not found as a real project | Temporal durable workflows, or a custom dead-man heartbeat watchdog |
| Edward | ❌ Not found as a real named tool | Temporal + StepShield benchmark concepts; custom temporal watchdog |
| PRAXIS-MD | ✅ Real — published Oct 2026 (ScienceDirect) | Use as proposed |
| BioCodex (RunSpec/async) | ✅ Real — ACM July 2026 | Use as proposed |
| StepShield benchmark | ✅ Real — arXiv Jan 2026, GitHub glo26/stepshield | Use as proposed |

## Layer 1: Voice and Tool Calling

**Pipecat** remains the recommended choice for a self-hosted, PHI-sensitive
research tool. v1.12 ships swappable STT/LLM/TTS providers, VAD, turn-taking,
and barge-in, with a processor pipeline that is easy to reason about.

- Pipecat: https://github.com/pipecat-ai/pipecat (v1.12.0, 2026-09-26)

**LiveKit Agents** (v1.8.4) is the alternative when you want WebRTC transport and
telephony bundled. For a phone-first app this matters if you add real-time
collaboration or calling. For a single research assistant, Pipecat is simpler.

- LiveKit Agents: https://github.com/livekit/agents (v1.8.4, 2026-10-18)

**OpenAI Agents SDK Realtime** is the real successor to the "rtvoice" idea:
`RealtimeAgent` with handoffs, tool-based supervisor delegation, guardrails, and
`gpt-realtime-2.1`. Use it only if you are comfortable being tied to OpenAI's
realtime model and routing PHI through it.

- OpenAI Agents SDK Realtime: https://openai.github.io/openai-agents-js/guides/voice-agents/build/

Recommendation: **Pipecat** for self-hosted/PHI control; keep OpenAI Realtime as
a fallback if you need the lowest-latency single-provider stack.

## Layer 2: Supervisor / Watchdog

There is no off-the-shelf "AgentCurie". The closest real standards are:

- **Google A2A protocol** — the real implementation of "Agent Cards with skills".
  Agents expose `/.well-known/agent-card.json` listing identity, capabilities,
  and skills; supervisors discover and delegate via JSON-RPC tasks. This gives you
  cross-framework coordination (LangGraph, CrewAI, AutoGen, Strands) without a
  custom orchestrator.
  - A2A spec: https://a2aprotocol.ai / https://github.com/a2aproject/a2a-python

- **LangGraph** (or CrewAI / AutoGen) for the supervisor's routing logic between
  `submit_adjudication` and `check_job_status` skills.

- **Temporal** for durable, crash-surviving job supervision: timeouts, retries,
  heartbeats, and dead-man detection are first-class. This replaces both
  "nightsitter" and "Edward". Temporal workflows can wrap your worker process and
  fire interventions on stalls, scope violations, or budget overruns.

  - Temporal Agent Harness: https://temporal.io/blog/temporal-agent-harness-durable-agent-infrastructure
  - Dead-man's-switch pattern: https://www.agentic-patterns.com/patterns/dead-mans-switch-for-scheduled-agent-jobs

- **StepShield** (benchmark, not a tool) provides the "when, not whether to
  intervene" evaluation methodology if you later build a temporal watchdog.

Recommendation: Use **Temporal** as the process-level watchdog + durable job
supervisor, and **A2A + LangGraph** for agent-to-agent routing. Skip "Edward"-style
opinionated wrappers until you have real Capability Foundry runs to evaluate.

## Layer 3: Scientific Brain

**PRAXIS-MD** is real and directly applicable. Its architecture is exactly the
pattern you need: an external long-term brain directory holding memory, skills,
case records, **hard rules**, and **negative-case knowledge** (failed routes /
non-reusable strategies). Separate planning from execution. Published Oct 2026.

- PRAXIS-MD: https://www.sciencedirect.com/science/article/pii/S1093326326003499

**BioCodex** provides the async execution lesson: a planning server enforces
schemas and generates deterministic artifacts; an execution server restricts itself
to scheduler-safe operations; asynchrony is first-class via explicit
**submit / poll / fetch** primitives (RunSpecs). Your existing `job_id` content-hash
design already implements this — keep it.

- BioCodex: https://dl.acm.org/doi/10.1145/3785462.3815873

Your Concordance hard rules (`WITHHELD_GRADES`, `_ctdna_rule`, data-class
enforcement) remain the core product. Add the PRAXIS-MD-style `negative_cases.json`
recording `not_supported` / `inconclusive` adjudications and their reasons so the
supervisor refuses to repeat optimistic mistakes.

## What the Agent Actually Does (unchanged, verified)

1. Voice layer transcribes, classifies intent, calls `submit_adjudication(anchor_id, paired_csv_path)`.
2. Supervisor (Temporal workflow) registers the job ID, starts a bounded poll loop with a deadline.
3. Scientific brain validates against hard rules (data class allowed? anchor exists? counts plausible?) — refuses with reason on failure.
4. Execution server runs adjudication, writes capsule.
5. Watchdog detects completion, fetches capsule, compares verdict to prior run for the same anchor.
6. Voice layer speaks the verdict change and capsule hash.

## Updated Build Order

- **Week 1:** Wrap your existing FastAPI `/api/adjudicate` endpoint with a
  Temporal workflow (or a simpler process watchdog). Prove a wedged job triggers
  an alert and a completed job returns the capsule. (Replaces "nightsitter".)
- **Week 2:** Add an A2A-based supervisor (LangGraph) with two skills:
  `submit_adjudication` and `check_job_status`. No voice yet. Test routing and
  the scientific brain's hard-rule validation.
- **Week 3:** Add Pipecat with a minimal voice pipeline. The LLM in Pipecat's
  pipeline is a small model for intent classification + tool selection only.
  Scientific reasoning stays in the supervisor.
- **Week 4:** Wire the PRAXIS-MD pattern: a `negative_cases.json` recording failed
  adjudications and reasons. Supervisor retrieves from it when validating new
  requests.

## Notes for the Android (phone) side

- The voice agent backend is Python (Pipecat) + your FastAPI adjudication service.
  The Android app is a thin client that streams audio / calls the tool endpoints.
- Android build toolchain (JDK 17, SDK 35, build-tools 35.0.0, platform-tools) is
  already installed and verified on this machine.
- If you need on-device inference later, revisit; for v1, keep inference server-side
  for PHI control and reproducibility.