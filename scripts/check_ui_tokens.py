#!/usr/bin/env python3
"""UI token guard.

Fails when UI code bypasses the design system:
  1. literal `fontSize = N.sp` in ui/screens or ui/components
  2. a private `SectionCard` implementation (use AppSectionCard)
  3. text below the 11sp floor (belt and braces)

Escape hatch: add `// ui-token-ignore` to the same line for a deliberate
exception (e.g. a chart canvas label that must scale in pixels).

Usage:
    python scripts/check_ui_tokens.py [repo_root]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

FONT_SIZE = re.compile(r"fontSize\s*=\s*(\d+)(?:\.\d+)?\.sp")
PRIVATE_SECTION_CARD = re.compile(r"private\s+fun\s+SectionCard\s*\(")
IGNORE = "ui-token-ignore"
MIN_SP = 11

TARGET_DIRS = (
    "app/src/main/java/com/example/concordance/ui/screens",
    "app/src/main/java/com/example/concordance/ui/components",
)


def scan(root: Path) -> list[str]:
    problems: list[str] = []
    for rel in TARGET_DIRS:
        directory = root / rel
        if not directory.exists():
            continue
        for path in sorted(directory.glob("*.kt")):
            for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if IGNORE in line:
                    continue
                for match in FONT_SIZE.finditer(line):
                    size = int(match.group(1))
                    if size < MIN_SP:
                        problems.append(
                            f"{path.relative_to(root)}:{lineno}: fontSize {size}sp is below the {MIN_SP}sp floor"
                        )
                    else:
                        problems.append(
                            f"{path.relative_to(root)}:{lineno}: literal fontSize; use MaterialTheme.concordance.*"
                        )
                if PRIVATE_SECTION_CARD.search(line):
                    problems.append(
                        f"{path.relative_to(root)}:{lineno}: private SectionCard; use AppSectionCard"
                    )
    return problems


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    problems = scan(root)
    if problems:
        print(f"UI token guard: {len(problems)} problem(s)\n")
        for p in problems:
            print("  " + p)
        print("\nReplace literals with MaterialTheme.concordance.<role>; see docs/UI_UX_PHASE2_PLAN.md")
        return 1
    print("UI token guard: clean")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
