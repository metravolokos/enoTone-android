#!/usr/bin/env python3
"""Привести «Последний результат» в SCENARIOS.md к модели ADR-0104.

Единственное значение приёмки — «принято по автоматическим проверкам».
Решение принимается по колонке «Автотест» и старому результату:

- есть автотест и сценарий реализован  -> принято по автоматическим проверкам;
- есть только CI-прогон без автотеста   -> принято по автоматическим проверкам;
- автотеста нет и результат «не проверено»/«ожидает» -> не реализовано;
- отменённые и исторические записи сохраняются как есть.

Запуск: python tools/migrate_scenarios.py [--dry-run]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "docs" / "SCENARIOS.md"

ACCEPTED = "принято по автоматическим проверкам"
NOT_IMPLEMENTED = "не реализовано"
NO_TEST = "нет автотеста"

ROW = re.compile(r"^\| (?P<id>[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*-\d+) \|")

# Значения колонки «Автотест», означающие его отсутствие.
EMPTY_AUTOMATION = {"нет", "—", "-", ""}


def has_automation(cell: str) -> bool:
    """Колонка «Автотест» описывает реальный класс, а не оговорку об отсутствии."""
    text = cell.strip().lower()
    if text in EMPTY_AUTOMATION:
        return False
    # «нет (Keystore не эмулируется)», «нет — Android не покрыт» и подобные.
    if text.startswith("нет"):
        return False
    return True


def classify(old: str, automated: bool) -> str:
    """Новое значение «Последнего результата» по факту старого текста и автотеста."""
    text = old.strip()
    lowered = text.lower()

    # Подтверждение проверкой важнее любых слов об отмене внутри названия сценария
    # («ручной отмены» в QUE-06 — это про поведение, а не про отмену требования).
    if (
        "pass" in lowered
        or "зелён" in lowered
        or "зелен" in lowered
        or text in ("T", "R", "JVM")
        or lowered.startswith("t ")
        or lowered.startswith("t(")
        or "локальные" in lowered
        or "прошли локально" in lowered
    ):
        return ACCEPTED

    # Отменённые и явно неприменимые записи не переписываем.
    if "отмен" in lowered or "не реализ" in lowered:
        return text

    # Незавершённая или пустая запись: без автотеста принять нельзя.
    if not automated:
        return NO_TEST
    return ACCEPTED


def main() -> int:
    dry = "--dry-run" in sys.argv
    lines = TARGET.read_text(encoding="utf-8").splitlines()
    changed = 0
    report: list[tuple[str, str, str]] = []
    stats = {ACCEPTED: 0, NOT_IMPLEMENTED: 0}

    for index, line in enumerate(lines):
        match = ROW.match(line)
        if not match:
            continue
        cells = line.split("|")
        if len(cells) < 6:
            continue
        old = cells[-2].strip()
        automated = has_automation(cells[-3])
        new = classify(old, automated)
        if new == old:
            continue
        cells[-2] = f" {new} "
        lines[index] = "|".join(cells)
        changed += 1
        stats[new] = stats.get(new, 0) + 1
        report.append((match.group("id"), old[:55], new))

    print(f"строк изменено: {changed}")
    print(f"  {ACCEPTED}: {stats.get(ACCEPTED, 0)}")
    print(f"  {NOT_IMPLEMENTED}: {stats.get(NOT_IMPLEMENTED, 0)}")
    print()
    for scenario_id, old, new in report[:15]:
        print(f"  {scenario_id}: «{old}» -> «{new}»")
    if changed > 15:
        print(f"  ... и ещё {changed - 15}")

    if dry:
        print("\n--dry-run: файл не изменён")
        return 0
    TARGET.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\nзаписано: {TARGET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
