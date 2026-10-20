"""Data class enforcement and routing policy.

Rules:
- public: any backend allowed
- de-identified: local or AWS with BAA only
- phi: local only, or AWS with signed BAA in tenant account
- If unlabeled: defaults to phi.
- The router has NO override flag. Enforcement happens at submission time.
"""
from __future__ import annotations

from enum import Enum
from typing import Optional
from pydantic import BaseModel


class DataClass(str, Enum):
    PUBLIC = "public"
    DE_IDENTIFIED = "de_identified"
    PHI = "phi"


class BackendTarget(str, Enum):
    LOCAL = "local"
    AWS_BAA = "aws_baa"
    AWS_PUBLIC = "aws_public"
    KAGGLE_PUBLIC = "kaggle_public"
    EXTERNAL_API = "external_api"


class RoutingPolicyError(ValueError):
    """Raised when a job's target backend violates its data classification."""
    pass


class Router:
    @staticmethod
    def validate_submission(data_class: Optional[DataClass], target: BackendTarget) -> DataClass:
        # If unlabeled, default to PHI
        effective_class = data_class if data_class is not None else DataClass.PHI

        if effective_class == DataClass.PHI:
            if target not in (BackendTarget.LOCAL, BackendTarget.AWS_BAA):
                raise RoutingPolicyError(
                    f"Routing violation: PHI data class cannot be routed to '{target.value}'. "
                    f"Only local workers or AWS accounts with signed BAA are permitted."
                )
        elif effective_class == DataClass.DE_IDENTIFIED:
            if target not in (BackendTarget.LOCAL, BackendTarget.AWS_BAA):
                raise RoutingPolicyError(
                    f"Routing violation: De-identified data class cannot be sent to unvetted '{target.value}'. "
                    f"Only local or AWS BAA workers permitted."
                )
        elif effective_class == DataClass.PUBLIC:
            # Any target permitted for public data
            pass

        return effective_class
