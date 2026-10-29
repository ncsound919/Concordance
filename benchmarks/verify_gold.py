"""Recompute the gold odds ratios used by the benchmark, independently of any model.

Run: python benchmarks/verify_gold.py
"""

SCENARIOS = [
    {"id": "or-01", "brain": (12, 40), "other": (6, 40), "gold": 2.43},
    {"id": "or-02", "brain": (35, 40), "other": (20, 40), "gold": 7.00},
    {"id": "or-03", "brain": (8, 50), "other": (14, 40), "gold": 0.35},
    {"id": "or-04", "brain": (18, 40), "other": (18, 40), "gold": 1.00},
    {"id": "or-05", "brain": (45, 60), "other": (30, 60), "gold": 3.00},
    {"id": "or-06", "brain": (9, 40), "other": (3, 40), "gold": 3.58},
]


def odds_ratio(events_brain, total_brain, events_other, total_other):
    a = events_brain
    b = total_brain - events_brain
    c = events_other
    d = total_other - events_other
    if b == 0 or c == 0:
        raise ValueError("zero non-event cell: odds ratio is undefined")
    return (a * d) / (b * c)


def main():
    for s in SCENARIOS:
        a, n1 = s["brain"]
        c, n2 = s["other"]
        value = odds_ratio(a, n1, c, n2)
        rounded = round(value, 2)
        print(f"{s['id']}: ({a}/{n1}) vs ({c}/{n2}) -> {value:.4f} -> {rounded:.2f} (gold {s['gold']:.2f})")
        assert abs(rounded - s["gold"]) < 1e-9, f"gold mismatch in {s['id']}"
    print("all gold values verified")


if __name__ == "__main__":
    main()
