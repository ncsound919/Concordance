"""Kaggle kernel: run the paired analysis on the bundled synthetic cohort.

This is an engineering fixture, not a scientific result. Its data is synthetic, so the
adjudicator refuses to mint a claim from it - see `SYNTHETIC_DATA` in Adjudicator.kt.

It exists to prove the pipeline end to end:
  1. the dataset pin reproduces on a different machine (sha256 of the regenerated CSV),
  2. a real 2x2 analysis runs on Kaggle and returns an odds ratio and a Fisher p,
  3. the artifact is downloadable and re-verifiable.

Measured run: 2026-10-29, account tap919, CPU only, ~30 s. Results in
benchmarks/results/2026-10-29-fixture-run.json.
"""

import json
import hashlib
import sys
import platform
import datetime
import math
import io
import csv

import numpy as np
from scipy.stats import fisher_exact

PIN = "8ae88eb84cbaf61d1f407e4e4a6ad27b5cc599e5d6ec878565426e6b4422d4e2"

# 1. Regenerate the bundled cohort and verify the pin.
lines = ["patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,data_source"]
for i in range(1, 41):
    lines.append("CBP-BRN-%d,brain,FoundationOne-CDx,FoundationOne-Liquid,yes,no,synthetic" % i)
for i in range(41, 71):
    lines.append("CBP-BRN-%d,brain,FoundationOne-CDx,FoundationOne-Liquid,yes,yes,synthetic" % i)
for i in range(1, 26):
    lines.append("CBP-OTH-%d,non_cns,FoundationOne-CDx,FoundationOne-Liquid,yes,no,synthetic" % i)
for i in range(26, 221):
    lines.append("CBP-OTH-%d,non_cns,FoundationOne-CDx,FoundationOne-Liquid,yes,yes,synthetic" % i)
csv_text = "\n".join(lines) + "\n"
actual_pin = hashlib.sha256(csv_text.encode()).hexdigest()
assert actual_pin == PIN, "pin mismatch: %s != %s" % (actual_pin, PIN)

# 2. Counts and the 2x2 analysis.
rows = list(csv.DictReader(io.StringIO(csv_text)))
brain = [r for r in rows if r["site"] == "brain"]
other = [r for r in rows if r["site"] == "non_cns"]


def discordant(r):
    return r["tissue_result"] == "yes" and r["plasma_result"] == "no"


a = sum(1 for r in brain if discordant(r))
n1 = len(brain)
c = sum(1 for r in other if discordant(r))
n2 = len(other)
b = n1 - a
d = n2 - c
odds_ratio = (a * d) / (b * c)
_, fisher_p = fisher_exact([[a, b], [c, d]], alternative="greater")
se = math.sqrt(1 / a + 1 / b + 1 / c + 1 / d)
ci_low = math.exp(math.log(odds_ratio) - 1.96 * se)
ci_high = math.exp(math.log(odds_ratio) + 1.96 * se)
verdict = "supported" if (ci_low > 1.0 or ci_high < 1.0) else "inconclusive"

# 3. Write the artifact set.
result = {
    "spec_id": "rp-esr1-cns-001",
    "data_class": "synthetic",
    "evidence_tier": "synthetic",
    "pin_verified": True,
    "counts": {
        "brain": {"discordant": a, "concordant": b, "total": n1},
        "non_cns": {"discordant": c, "concordant": d, "total": n2},
    },
    "odds_ratio": round(odds_ratio, 4),
    "fisher_p": float(fisher_p),
    "ci_95": [round(ci_low, 4), round(ci_high, 4)],
    "verdict": verdict,
    "reason": "synthetic fixture: exercises the pipeline only; no scientific claim",
}
json.dump(result, open("results.json", "w"), indent=2)
json.dump(
    {
        "python": sys.version.split()[0],
        "numpy": np.__version__,
        "scipy": __import__("scipy").__version__,
        "platform": platform.platform(),
    },
    open("session_info.json", "w"),
    indent=2,
)
print(json.dumps(result))
