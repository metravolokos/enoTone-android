#!/usr/bin/env python3
"""Базовая линия detekt может только уменьшаться (AUDIT_PROTOCOL §4.1).

Сравнивает число записей <ID> во всех detekt-baseline.xml рабочего дерева
с тем же числом в базовой ревизии git (аргумент, например origin/main).
Падает, если записей стало больше. Новые файлы baseline тоже считаются.
"""
import pathlib
import re
import subprocess
import sys

ID = re.compile(r"<ID>")


def count_tree(root: pathlib.Path) -> int:
    return sum(len(ID.findall(p.read_text(encoding="utf-8")))
               for p in root.rglob("detekt-baseline.xml") if "build" not in p.parts)


def count_ref(ref: str) -> int | None:
    """None — в базовой ревизии baseline ещё не было (PR, который его вводит)."""
    names = subprocess.run(["git", "ls-tree", "-r", "--name-only", ref],
                           check=True, capture_output=True, text=True).stdout.split()
    files = [n for n in names if n.endswith("detekt-baseline.xml")]
    if not files:
        return None
    total = 0
    for name in files:
        text = subprocess.run(["git", "show", f"{ref}:{name}"], check=True,
                              capture_output=True, text=True).stdout
        total += len(ID.findall(text))
    return total


def grew(before: int | None, after: int) -> bool:
    return before is not None and after > before


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_detekt_baseline.py <base-ref>", file=sys.stderr)
        return 2
    before, after = count_ref(sys.argv[1]), count_tree(pathlib.Path("."))
    print(f"detekt baseline: {before if before is not None else 'нет'} -> {after}")
    if grew(before, after):
        print("::error::detekt baseline grew; fix the new findings instead "
              "(AUDIT_PROTOCOL §4.1)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
