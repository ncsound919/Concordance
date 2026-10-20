"""Job queue, specification, content-hashing, and stuck-task recovery."""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import threading
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field

from .router import BackendTarget, DataClass, Router, RoutingPolicyError

STUCK_TASK_TIMEOUT_SECONDS = 300  # 5 minutes


class TaskType(str, Enum):
    SENSITIVITY_CURVE = "sensitivity_curve"
    PERMUTATION_TEST = "permutation_test"
    BOOTSTRAP_SWEEP = "bootstrap_sweep"
    PAIRED_ANALYSIS = "paired_analysis"
    FIGURE_OCR_VLM = "figure_ocr_vlm"


class JobState(str, Enum):
    PENDING = "pending"
    QUEUED = "queued"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


def compute_job_id(payload: dict) -> str:
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256(canonical.encode()).hexdigest()[:16]


class JobSubmissionRequest(BaseModel):
    task_type: TaskType
    data_class: Optional[DataClass] = None  # defaults to PHI if omitted
    target_backend: BackendTarget = BackendTarget.LOCAL
    anchor_id: Optional[str] = "giannoudis2024_pooled"
    params: Dict[str, Any] = Field(default_factory=dict)
    model_revision: str = "concordance-v0.1.0"


class JobRecord(BaseModel):
    job_id: str
    task_type: TaskType
    data_class: DataClass
    target_backend: BackendTarget
    state: JobState
    created_utc: str
    started_utc: Optional[str] = None
    completed_utc: Optional[str] = None
    last_heartbeat_utc: Optional[str] = None
    queue_position: int = 0
    estimated_wait_sec: int = 0
    progress_percent: int = 0
    attempts: int = 0
    params: Dict[str, Any] = Field(default_factory=dict)
    result: Optional[Dict[str, Any]] = None
    error_message: Optional[str] = None


class JobQueue:
    def __init__(self):
        self._lock = threading.Lock()
        self._jobs: Dict[str, JobRecord] = {}

    def submit(self, req: JobSubmissionRequest) -> JobRecord:
        effective_data_class = Router.validate_submission(req.data_class, req.target_backend)

        hash_payload = {
            "task_type": req.task_type.value,
            "data_class": effective_data_class.value,
            "target_backend": req.target_backend.value,
            "anchor_id": req.anchor_id,
            "params": req.params,
            "model_revision": req.model_revision,
        }
        job_id = compute_job_id(hash_payload)

        with self._lock:
            if job_id in self._jobs:
                existing = self._jobs[job_id]
                if existing.state not in (JobState.FAILED, JobState.COMPLETED):
                    return existing

            now = dt.datetime.now(dt.timezone.utc).isoformat()
            pending_count = sum(1 for j in self._jobs.values() if j.state == JobState.PENDING)

            record = JobRecord(
                job_id=job_id,
                task_type=req.task_type,
                data_class=effective_data_class,
                target_backend=req.target_backend,
                state=JobState.PENDING,
                created_utc=now,
                last_heartbeat_utc=now,
                queue_position=pending_count + 1,
                estimated_wait_sec=max(5, (pending_count + 1) * 6),
                progress_percent=0,
                attempts=0,
                params=req.params,
            )
            self._jobs[job_id] = record
            return record

    def get(self, job_id: str) -> Optional[JobRecord]:
        with self._lock:
            self._recover_stuck_tasks_locked()
            return self._jobs.get(job_id)

    def list_all(self) -> List[JobRecord]:
        with self._lock:
            self._recover_stuck_tasks_locked()
            return list(self._jobs.values())

    def claim_next(self) -> Optional[JobRecord]:
        with self._lock:
            self._recover_stuck_tasks_locked()
            for job in self._jobs.values():
                if job.state == JobState.PENDING:
                    now = dt.datetime.now(dt.timezone.utc).isoformat()
                    job.state = JobState.PROCESSING
                    job.started_utc = now
                    job.last_heartbeat_utc = now
                    job.attempts += 1
                    job.progress_percent = 20
                    return job
            return None

    def update_progress(self, job_id: str, progress: int) -> None:
        with self._lock:
            if job_id in self._jobs:
                job = self._jobs[job_id]
                job.progress_percent = progress
                job.last_heartbeat_utc = dt.datetime.now(dt.timezone.utc).isoformat()

    def complete(self, job_id: str, result: dict) -> None:
        with self._lock:
            if job_id in self._jobs:
                job = self._jobs[job_id]
                job.state = JobState.COMPLETED
                job.completed_utc = dt.datetime.now(dt.timezone.utc).isoformat()
                job.progress_percent = 100
                job.queue_position = 0
                job.estimated_wait_sec = 0
                job.result = result

    def fail(self, job_id: str, error: str) -> None:
        with self._lock:
            if job_id in self._jobs:
                job = self._jobs[job_id]
                job.state = JobState.FAILED
                job.completed_utc = dt.datetime.now(dt.timezone.utc).isoformat()
                job.error_message = error

    def _recover_stuck_tasks_locked(self) -> int:
        """Automatic stuck-task recovery: reset tasks stuck in PROCESSING > 5 min to PENDING."""
        recovered = 0
        now = dt.datetime.now(dt.timezone.utc)
        for job in self._jobs.values():
            if job.state == JobState.PROCESSING and job.last_heartbeat_utc:
                try:
                    last_hb = dt.datetime.fromisoformat(job.last_heartbeat_utc)
                    if (now - last_hb).total_seconds() > STUCK_TASK_TIMEOUT_SECONDS:
                        job.state = JobState.PENDING
                        job.progress_percent = 0
                        job.started_utc = None
                        job.last_heartbeat_utc = now.isoformat()
                        recovered += 1
                except Exception:
                    pass
        return recovered


GLOBAL_JOB_QUEUE = JobQueue()
