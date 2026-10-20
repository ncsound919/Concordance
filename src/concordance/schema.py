"""Data spine: claim anchors and paired tissue/plasma rows, validated on construction."""
from __future__ import annotations

from enum import Enum
from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


class EvidenceGrade(str, Enum):
    COUNTS = "counts"                # 2x2 counts available; full uncertainty propagation
    SUMMARY = "summary"              # OR + CI from a full-text source, no counts
    ABSTRACT_ONLY = "abstract_only"  # methods unseen; never adjudicated
    SYNTHETIC = "synthetic"          # plumbing checks only; never adjudicated


class Anchor(BaseModel):
    """One published observation of a brain-vs-other-site contrast for a mutation."""
    model_config = ConfigDict(extra="forbid")

    id: str = Field(min_length=1)
    label: str
    source: str
    evidence_grade: EvidenceGrade
    brain_events: Optional[int] = Field(default=None, ge=0)
    brain_total: Optional[int] = Field(default=None, gt=0)
    other_events: Optional[int] = Field(default=None, ge=0)
    other_total: Optional[int] = Field(default=None, gt=0)
    odds_ratio: Optional[float] = Field(default=None, gt=0)
    or_ci_low: Optional[float] = Field(default=None, gt=0)
    or_ci_high: Optional[float] = Field(default=None, gt=0)
    reference_prevalence: Optional[float] = Field(default=None, gt=0, lt=1)
    denominator: str = "samples"
    notes: str = ""

    @model_validator(mode="after")
    def _shape(self) -> "Anchor":
        counts = [self.brain_events, self.brain_total, self.other_events, self.other_total]
        has_counts = all(c is not None for c in counts)
        any_counts = any(c is not None for c in counts)
        if any_counts and not has_counts:
            raise ValueError("counts must be given for both groups (events and totals)")
        if has_counts:
            if self.brain_events > self.brain_total or self.other_events > self.other_total:
                raise ValueError("events cannot exceed totals")
            if self.brain_events == 0 or self.other_events == 0:
                raise ValueError("zero-event groups are not supported by the delta-method correction")
            if self.other_events == self.other_total or self.brain_events == self.brain_total:
                raise ValueError("a group with 100% events is not supported")
            return self
        if self.odds_ratio is None or self.or_ci_low is None or self.or_ci_high is None:
            raise ValueError("provide either full 2x2 counts or odds_ratio with or_ci_low/or_ci_high")
        if not (self.or_ci_low <= self.odds_ratio <= self.or_ci_high):
            raise ValueError("odds_ratio must lie inside its CI")
        if self.reference_prevalence is None:
            raise ValueError("summary anchors need reference_prevalence (non-CNS prevalence)")
        if self.evidence_grade == EvidenceGrade.COUNTS:
            raise ValueError("evidence_grade 'counts' requires 2x2 counts")
        return self

    @property
    def has_counts(self) -> bool:
        return self.brain_events is not None


class DataSource(str, Enum):
    REAL = "real"
    SYNTHETIC = "synthetic"


class ModalityPair(BaseModel):
    """One patient sample with tissue and plasma ESR1 calls (the table M3 lacked)."""
    model_config = ConfigDict(extra="forbid")

    patient_id: str = Field(min_length=1)
    site: Literal["brain", "non_cns"]
    tissue_assay: str = Field(min_length=1)
    plasma_assay: str = Field(min_length=1)
    tissue_result: bool
    plasma_result: bool
    ctdna_fraction: Optional[float] = Field(default=None, ge=0, le=1)
    limit_of_detection: Optional[float] = Field(default=None, gt=0, le=1)
    data_source: DataSource = DataSource.REAL

    @model_validator(mode="after")
    def _ctdna_rule(self) -> "ModalityPair":
        f, lod = self.ctdna_fraction, self.limit_of_detection
        if f is not None and lod is not None:
            if self.plasma_result and f < lod:
                raise ValueError("plasma-positive call with ctdna_fraction below limit_of_detection")
            if not self.plasma_result and f >= lod:
                raise ValueError("plasma-negative call with ctdna_fraction at or above limit_of_detection")
        return self


PAIR_COLUMNS = [
    "patient_id", "site", "tissue_assay", "plasma_assay", "tissue_result",
    "plasma_result", "ctdna_fraction", "limit_of_detection", "data_source",
]
