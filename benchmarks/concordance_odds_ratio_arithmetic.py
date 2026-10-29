"""Kaggle Benchmark: can an LLM compute a 2x2 odds ratio exactly?

This is the arithmetic Concordance's engine performs deterministically and refuses to delegate to
a model. The benchmark measures the gap between "a model can talk about the result" and "a model
can compute it", which is the reason the app never lets a model own the arithmetic.

Run with the Kaggle CLI:

    kaggle b t push concordance-odds-ratio-arithmetic -f benchmarks/concordance_odds_ratio_arithmetic.py
    kaggle b t run  concordance-odds-ratio-arithmetic -m google/gemini-3-flash-preview --wait

Measured results and caveats live in benchmarks/README.md.
"""

import json
import kaggle_benchmarks as kbench

# Gold values are (brain events / brain total) vs (other events / other total).
# The 2x2 odds ratio is (a*d)/(b*c) with b and c the non-events. Computed independently in
# benchmarks/verify_gold.py; no model output was used to produce them.
SCENARIOS = [
    {"id": "or-01", "brain": (12, 40), "other": (6, 40), "gold": 2.43},
    {"id": "or-02", "brain": (35, 40), "other": (20, 40), "gold": 7.00},
    {"id": "or-03", "brain": (8, 50), "other": (14, 40), "gold": 0.35},
    {"id": "or-04", "brain": (18, 40), "other": (18, 40), "gold": 1.00},
    {"id": "or-05", "brain": (45, 60), "other": (30, 60), "gold": 3.00},
    {"id": "or-06", "brain": (9, 40), "other": (3, 40), "gold": 3.58},
]

TOLERANCE = 0.02


def prompt_for(s):
    (a, n1) = s["brain"]
    (c, n2) = s["other"]
    return (
        "You are given a 2x2 table from a paired oncology study.\n"
        f"Group brain: {a} events out of {n1}.\n"
        f"Group non-CNS: {c} events out of {n2}.\n"
        "Compute the odds ratio (brain odds divided by non-CNS odds).\n"
        "Reply with the number only, rounded to two decimal places. No words, no units."
    )


def parse_number(raw):
    cleaned = "".join(ch for ch in str(raw) if (ch.isdigit() or ch in ".-"))
    try:
        return float(cleaned)
    except ValueError:
        return None


@kbench.task(
    name="concordance odds ratio arithmetic",
    description=(
        "Can the model compute a 2x2 odds ratio exactly? Concordance's engine does this "
        "deterministically; a model that cannot is not a substitute for it."
    ),
    version=1,
)
def odds_ratio_arithmetic(llm) -> tuple[int, int]:
    passed = 0
    total = len(SCENARIOS)
    detail = []

    for s in SCENARIOS:
        # A fresh chat per item so earlier answers cannot leak into later arithmetic.
        with kbench.chats.new(s["id"]):
            raw = llm.prompt(prompt_for(s))
        value = parse_number(raw)
        ok = value is not None and abs(value - s["gold"]) <= TOLERANCE
        detail.append({"id": s["id"], "gold": s["gold"], "answer": str(raw)[:80], "ok": ok})
        if ok:
            passed += 1

    print(json.dumps({"detail": detail, "passed": passed, "total": total}, indent=2))

    # One aggregate assertion: a per-item failure must not abort the remaining items.
    kbench.assertions.assert_equal(
        total, passed, expectation="correct odds ratios within 0.02"
    )
    return passed, total


odds_ratio_arithmetic.run(kbench.llm)
