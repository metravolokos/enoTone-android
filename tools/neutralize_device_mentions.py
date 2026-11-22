#!/usr/bin/env python3
"""Убирает упоминания Huawei из оперативных документов.

Владелец 2026-11-23: в описаниях и документации должен остаться «смартфон без
сервисов Google», а не конкретная модель. ADR и docs/archive НЕ трогаются:
ADR историчны по правилу проекта (их заменяют новым ADR), архив — летопись.

Скрипт делает только точечные замены формулировок и печатает всё, что осталось
без автоматической замены, — такие места правятся вручную.

Запуск:
  python tools/neutralize_device_mentions.py --dry-run
  python tools/neutralize_device_mentions.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

# Оперативные документы. Исключены осознанно:
#   docs/DECISIONS/ADR-*  — исторические решения, меняются только новым ADR;
#   docs/archive/         — летопись, не переписывается;
#   docs/audit/, docs/tasks/ — снимки прошлых прогонов;
#   docs/ORIGINAL_PROMPT_AllMusic_Player.md — исходное ТЗ владельца (цитата);
#   .github/workflows/ci.yml — huaweicloud там URL зеркала Gradle/Maven, не устройство.
TARGETS = [
    "README.md",
    "docs/ARCHITECTURE.md",
    "docs/CLOUD_MAILRU_WEBDAV.md",
    "docs/CURRENT_DECISIONS.md",
    "docs/DATA_MODEL.md",
    "docs/DESIGN_PLAN.md",
    "docs/KNOWN_ISSUES.md",
    "docs/OPEN_QUESTIONS.md",
    "docs/RELEASE.md",
    "docs/REQUIREMENTS.md",
    "docs/REVIEW_REPORT_TEMPLATE.md",
    "docs/ROADMAP.md",
    "docs/SCENARIOS.md",
    "docs/STABLE_PREVIEW_CHANNEL.md",
    "docs/STATUS.md",
    "docs/TESTING.md",
    "docs/DECISIONS/README.md",
    ".github/pull_request_template.md",
    ".github/ISSUE_TEMPLATE/task.md",
    ".github/dependabot.yml",
    # design/: эталонный экран измерен на конкретной модели; размеры (377 × 816 dp)
    # привязаны к ней, поэтому название модели — технический факт, а не бренд-упоминание.
    "design/AGENTS.md",
    "design/README.md",
    "design/docs/APP_ICON_V2.md",
    "design/docs/CHANGELOG.md",
    "design/docs/DECISIONS_ANDROID_V2_UI.md",
    "design/docs/DESIGN_DECISIONS.md",
    "design/docs/UI_ANDROID_V2.md",
    "design/docs/UI_ICONS_V1.md",
    "design/docs/WORDMARK_V2.md",
]

# Порядок важен: сначала длинные и специфичные конструкции.
REPLACEMENTS: list[tuple[re.Pattern[str], str]] = [
    # «на Huawei» → «на устройстве»
    (re.compile(r"на\s+Huawei\s+KLE-LX9\s*\(Android\s*12\)"), "на смартфоне без сервисов Google (Android 12)"),
    (re.compile(r"на\s+Huawei\b"), "на устройстве"),
    (re.compile(r"На\s+Huawei\b"), "На устройстве"),
    # «Huawei PASS» → «принято на устройстве»
    (re.compile(r"поимённ\w*\s+Huawei\s+PASS"), "поимённый PASS на устройстве"),
    (re.compile(r"Huawei\s+PASS"), "PASS на устройстве"),
    # «Huawei #N» → «сборка #N»
    (re.compile(r"Huawei\s+#(\d+)"), r"сборка #\1"),
    # «Huawei <число>» (например, 677, 3590) → «устройство <число>»
    (re.compile(r"Huawei\s+(\d+)\b"), r"устройство \1"),
    # «Huawei-сценарии», «Huawei-приёмка»
    (re.compile(r"Huawei-(\w+)"), r"Device-\1"),
    # «Huawei не требуется», «Huawei не выполнялся»
    (re.compile(r"Huawei\s+не\s+"), "Устройство не "),
    # «Huawei ожидает»
    (re.compile(r"Huawei\s+ожидает"), "Устройство ожидает"),
    # «Huawei: ...» — подпись в таблицах
    (re.compile(r"\bHuawei\b:"), "Устройство:"),
    # «остаётся за Huawei»
    (re.compile(r"остаётся за Huawei"), "остаётся за проверкой на устройстве"),
    # Оставшееся одиночное упоминание — общая замена
    (re.compile(r"\bHuawei\b"), "устройство"),
]


def replace_line(line: str) -> str:
    result = line
    for pattern, replacement in REPLACEMENTS:
        result = pattern.sub(replacement, result)
    return result


def main() -> int:
    dry = "--dry-run" in sys.argv
    total_changed = 0
    leftovers: list[tuple[str, int, str]] = []

    for relative in TARGETS:
        path = REPO / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        changed = 0
        for index, line in enumerate(lines):
            new = replace_line(line)
            if new != line:
                lines[index] = new
                changed += 1

        updated = "".join(lines)
        if changed:
            total_changed += changed
            if not dry:
                path.write_text(updated, encoding="utf-8")

        # Что осталось без замены — на ручную правку.
        for index, line in enumerate(updated.splitlines(), start=1):
            if re.search(r"huawei", line, re.IGNORECASE):
                leftovers.append((relative, index, line.strip()[:120]))

    print(f"строк изменено: {total_changed}")
    if leftovers:
        print(f"\nОСТАЛОСЬ ВРУЧНУЮ ({len(leftovers)}):")
        for relative, line_number, snippet in leftovers:
            print(f"  {relative}:{line_number}: {snippet}")
    else:
        print("\nупоминаний не осталось")
    if dry:
        print("\n--dry-run: файлы не изменены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
