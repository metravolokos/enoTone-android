#!/usr/bin/env python3
"""Обновляет срез main в шапке оперативных документов.

Меняет ТОЛЬКО SHA в позиции среза — исторические SHA коммитов в том же абзаце
не трогает. ADR и docs/archive не меняются: ADR историчны по правилу проекта.

Запуск:
  python tools/update_doc_snapshots.py --dry-run
  python tools/update_doc_snapshots.py
"""
from __future__ import annotations

import re
import subprocess
import sys
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

TARGETS = [
    "docs/ROADMAP.md",
    "docs/STATUS.md",
    "docs/KNOWN_ISSUES.md",
    "docs/REQUIREMENTS.md",
    "docs/TESTING.md",
    "docs/ARCHITECTURE.md",
    "docs/CURRENT_DECISIONS.md",
    "docs/DECISIONS/README.md",
]

# Срез начинается датой: «**Срез:** 2026-11-21 · `main` `<sha>`».
# Заменяется только первый SHA после маркера main в этой строке.
SNAPSHOT = re.compile(
    r"(\*\*(?:Фактический )?[Сс]рез:?\*\*\s*)(\d{4}-\d{2}-\d{2})([^\n]*?`main`\s*`)([0-9a-f]{7,40})(`)"
)

# Второй формат: «Сверено с `main` `<sha>` 2026-11-19».
SYNCED = re.compile(r"(Сверено с\s+`main`\s+`)([0-9a-f]{7,40})")

# Третий формат: «срез 2026-11-19, `main` `<sha>`» (TESTING.md).
INLINE = re.compile(r"(срез\s+)(\d{4}-\d{2}-\d{2})(,\s*`main`\s+`)([0-9a-f]{7,40})(`)")


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=REPO, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    ).stdout.strip()


def main() -> int:
    dry = "--dry-run" in sys.argv
    full_sha = git("rev-parse", "origin/main")
    if not full_sha:
        print("Не удалось определить origin/main", file=sys.stderr)
        return 1
    today = date.today().isoformat()
    print(f"origin/main: {full_sha[:8]}, дата: {today}")
    print()

    for relative in TARGETS:
        path = REPO / relative
        if not path.is_file():
            print(f"  {relative}: нет файла")
            continue
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        head = "".join(lines[:15])
        tail = "".join(lines[15:])
        separator = ""

        original = head
        head = SNAPSHOT.sub(lambda m: f"{m.group(1)}{today}{m.group(3)}{full_sha}{m.group(5)}", head)
        head = SYNCED.sub(lambda m: f"{m.group(1)}{full_sha}", head)
        head = INLINE.sub(lambda m: f"{m.group(1)}{today}{m.group(3)}{full_sha}{m.group(5)}", head)

        if head == original:
            print(f"  {relative}: срез актуален")
            continue
        if dry:
            print(f"  {relative}: срез обновится -> {full_sha[:8]} / {today}")
            continue
        path.write_text(head + separator + tail, encoding="utf-8")
        print(f"  {relative}: обновлён -> {full_sha[:8]} / {today}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
