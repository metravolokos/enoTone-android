#!/usr/bin/env python3
"""Каждый файл ADR покрыт строкой индекса docs/DECISIONS/README.md.

Строка индекса может описывать один номер («| 0081 |») или диапазон
(«| 0044–0049 |»). Статус ADR определяет индекс (AUDIT_PROTOCOL §4.4),
поэтому ADR без строки индекса — ошибка документации.
"""
import pathlib
import re
import sys

DECISIONS = pathlib.Path("docs/DECISIONS")


def covered(index: str) -> set[int]:
    numbers: set[int] = set()
    for first, last in re.findall(r"^\| (\d{4})(?:[–-](\d{4}))? \|", index, re.M):
        start = int(first)
        numbers.update(range(start, int(last or first) + 1))
    return numbers


def main() -> int:
    index = covered((DECISIONS / "README.md").read_text(encoding="utf-8"))
    files = sorted(int(m.group(1)) for p in DECISIONS.glob("ADR-*.md")
                   if (m := re.match(r"ADR-(\d{4})", p.name)))
    missing = [n for n in files if n not in index]
    if missing:
        print("::error::ADR without index row: " + ", ".join(f"{n:04d}" for n in missing))
        return 1
    print(f"ADR index covers {len(files)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
