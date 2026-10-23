"""Detection-artifact correction."""
from __future__ import annotations

import math
from dataclasses import asdict, dataclass
from typing import Optional

from scipy.stats import fisher_exact

from .schema import Anchor

Z95 = 1.959963984540054


@dataclass(frozen=True)
class Corrected:
    r: float
    feasible: bool
    odds_ratio: Optional[float] = None
    ci_low: Optional[float] = None
    ci_high: Optional[float] = None
    pi_brain: Optional[float] = None
    pi_other: Optional[float] = None

    def as_dict(self) -> dict:
        return asdict(self)


def _odds(p: float) -> float:
    return p / (1.0 - p)


def observed_prevalences(a: Anchor) -> tuple[float, float]:
    if a.has_counts:
        return a.brain_events / a.brain_total, a.other_events / a.other_total
    p_n = a.reference_prevalence
    odds_b = a.odds_ratio * _odds(p_n)
    return odds_b / (1.0 + odds_b), p_n


def observed_or(a: Anchor) -> float:
    p_b, p_n = observed_prevalences(a)
    return _odds(p_b) / _odds(p_n)


def observed_or_ci(a: Anchor) -> tuple[float, float, float]:
    if not a.has_counts:
        return a.odds_ratio, a.or_ci_low, a.or_ci_high
    b, nb, o, no = a.brain_events, a.brain_total, a.other_events, a.other_total
    est = (b / (nb - b)) / (o / (no - o))
    se = math.sqrt(1 / b + 1 / (nb - b) + 1 / o + 1 / (no - o))
    return est, math.exp(math.log(est) - Z95 * se), math.exp(math.log(est) + Z95 * se)


def fisher_p(a: Anchor) -> Optional[float]:
    if not a.has_counts:
        return None
    table = [[a.brain_events, a.brain_total - a.brain_events],
             [a.other_events, a.other_total - a.other_events]]
    return float(fisher_exact(table)[1])


def null_crossing_r(a: Anchor) -> float:
    p_b, p_n = observed_prevalences(a)
    return p_b / p_n


def _summary_corrected_or(or_val: float, p_ref: float, r: float, s_n: float) -> Optional[float]:
    odds_b = or_val * _odds(p_ref)
    p_b = odds_b / (1.0 + odds_b)
    pi_b, pi_n = p_b / (r * s_n), p_ref / s_n
    if pi_b >= 1.0 or pi_n >= 1.0:
        return None
    return _odds(pi_b) / _odds(pi_n)


def corrected(a: Anchor, r: float, s_n: float) -> Corrected:
    if not (0 < r <= 1.5):
        raise ValueError("r must be in (0, 1.5]")
    if not (0 < s_n <= 1):
        raise ValueError("s_n must be in (0, 1]")
    p_b, p_n = observed_prevalences(a)
    pi_b, pi_n = p_b / (r * s_n), p_n / s_n
    if pi_b >= 1.0 or pi_n >= 1.0:
        return Corrected(r=r, feasible=False)
    est = _odds(pi_b) / _odds(pi_n)
    if a.has_counts:
        def var_logit(p, n, pi, k):
            var_p = p * (1 - p) / n
            return (var_p / k ** 2) / (pi * (1 - pi)) ** 2
        v = (var_logit(p_b, a.brain_total, pi_b, r * s_n)
             + var_logit(p_n, a.other_total, pi_n, s_n))
        se = math.sqrt(v)
        lo, hi = math.exp(math.log(est) - Z95 * se), math.exp(math.log(est) + Z95 * se)
    else:
        lo = _summary_corrected_or(a.or_ci_low, a.reference_prevalence, r, s_n)
        hi = _summary_corrected_or(a.or_ci_high, a.reference_prevalence, r, s_n)
        if lo is None or hi is None:
            return Corrected(r=r, feasible=False)
    return Corrected(r=r, feasible=True, odds_ratio=est, ci_low=lo, ci_high=hi,
                     pi_brain=pi_b, pi_other=pi_n)


def min_feasible_r(a: Anchor, s_n: float) -> float:
    p_b, _ = observed_prevalences(a)
    if a.has_counts:
        return p_b / s_n
    odds_hi = a.or_ci_high * _odds(a.reference_prevalence)
    return (odds_hi / (1 + odds_hi)) / s_n


def significance_threshold_r(a: Anchor, s_n: float, steps: int = 2000) -> Optional[float]:
    def sig(r: float) -> bool:
        c = corrected(a, r, s_n)
        return c.feasible and c.ci_high < 1.0

    if not sig(1.0):
        return None
    lo_bound = min_feasible_r(a, s_n) * 1.0001
    if lo_bound >= 1.0:
        return 1.0
    prev = 1.0
    for i in range(1, steps + 1):
        r = 1.0 - (1.0 - lo_bound) * i / steps
        if not sig(r):
            a_, b_ = r, prev
            for _ in range(60):
                mid = (a_ + b_) / 2
                if sig(mid):
                    b_ = mid
                else:
                    a_ = mid
            return b_
        prev = r
    return lo_bound


def curve(a: Anchor, s_n: float, points: int = 60) -> list[dict]:
    lo = max(min_feasible_r(a, s_n) * 1.001, 0.02)
    if lo >= 1.0:
        return []
    out = []
    for i in range(points):
        r = lo + (1.0 - lo) * i / (points - 1)
        c = corrected(a, r, s_n)
        if c.feasible:
            out.append(c.as_dict())
    return out
