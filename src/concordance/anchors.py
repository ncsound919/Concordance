"""Anchors taken from the ecosystem register."""
from .schema import Anchor, EvidenceGrade

_GIANNOUDIS_OTHER_PREV = 3220 / 15516

ANCHORS: dict[str, Anchor] = {
    "giannoudis2024_pooled": Anchor(
        id="giannoudis2024_pooled",
        label="Giannoudis 2024 pooled (Foundation Medicine)",
        source="Giannoudis 2024; counts from the ecosystem register (samples denominator)",
        evidence_grade=EvidenceGrade.COUNTS,
        brain_events=53, brain_total=822, other_events=3220, other_total=15516,
        denominator="samples",
        notes="Pooled contrast mixes assays and sample types; consistent with L1-L4 simultaneously.",
    ),
    "annals_106P_meta": Anchor(
        id="annals_106P_meta",
        label="Annals of Oncology abstract 106P meta-analysis",
        source="Annals of Oncology abstract 106P; abstract only, methods unseen",
        evidence_grade=EvidenceGrade.ABSTRACT_ONLY,
        odds_ratio=0.35, or_ci_low=0.24, or_ci_high=0.52,
        reference_prevalence=_GIANNOUDIS_OTHER_PREV,
        denominator="unknown",
        notes=("10 studies. Non-CNS prevalence is borrowed from the Giannoudis pooled counts "
               "as an assumption; the abstract does not report it."),
    ),
}
