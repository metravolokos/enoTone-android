#!/usr/bin/env python3
"""Отчёт о ветках enoTone для уборки репозитория.

Авторитетный источник статуса — GitHub API: проект мержит через squash, поэтому
история ветки в main не появляется и локальные эвристики дают ложные результаты.

Категории:
  MERGED      — PR ветки слит: содержимое в main, ветку можно удалить;
  CLOSED      — PR закрыт без слияния: содержимое может быть не в main;
  ACTIVE      — открытый PR;
  NO_PR       — PR никогда не создавался: проверить вручную.

Скрипт read-only.

Запуск:
  python tools/branch_report.py            — сводка
  python tools/branch_report.py --list     — полные списки по категориям
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
GH_REPO = "metravolokos/enoTone-android"


def gh_json(args: list[str]):
    result = subprocess.run(
        ["gh", *args], cwd=REPO, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    if result.returncode != 0 or not result.stdout.strip():
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        return None


def remote_branches() -> list[str]:
    result = subprocess.run(
        ["git", "ls-remote", "--heads", "origin"],
        cwd=REPO, capture_output=True, text=True, encoding="utf-8",
    )
    names = []
    for line in result.stdout.splitlines():
        parts = line.split("\t", 1)
        if len(parts) == 2:
            name = parts[1].removeprefix("refs/heads/")
            if name != "main":
                names.append(name)
    return names


def main() -> int:
    list_mode = "--list" in sys.argv

    branches = remote_branches()
    prs = gh_json([
        "pr", "list", "--repo", GH_REPO, "--state", "all",
        "--limit", "500", "--json", "number,state,headRefName,mergedAt",
    ]) or []

    # Последний PR на каждую ветку определяет её судьбу.
    latest: dict[str, dict] = {}
    for pr in sorted(prs, key=lambda item: item["number"]):
        latest[pr["headRefName"]] = pr

    categories: dict[str, list[str]] = {
        "MERGED": [], "ACTIVE": [], "CLOSED": [], "NO_PR": [],
    }
    for name in branches:
        pr = latest.get(name)
        if pr is None:
            categories["NO_PR"].append(name)
        elif pr["mergedAt"]:
            categories["MERGED"].append(name)
        elif pr["state"] == "OPEN":
            categories["ACTIVE"].append(name)
        else:
            categories["CLOSED"].append(name)

    print(f"remote-веток (без main): {len(branches)}")
    print(f"PR всего: {len(prs)}")
    print()
    print("ИТОГ:")
    for kind in ("MERGED", "ACTIVE", "CLOSED", "NO_PR"):
        print(f"  {kind:8} {len(categories[kind]):3}")

    if list_mode:
        for kind in ("ACTIVE", "CLOSED", "NO_PR", "MERGED"):
            print()
            print(f"=== {kind} ({len(categories[kind])}) ===")
            for name in sorted(categories[kind]):
                print(f"  {name}")
    else:
        print()
        print("Требуют проверки перед удалением:")
        for kind in ("ACTIVE", "CLOSED", "NO_PR"):
            if categories[kind]:
                print(f"  {kind}: {len(categories[kind])}")
                for name in sorted(categories[kind]):
                    print(f"    {name}")
        print()
        print("Безопасны для удаления: MERGED — содержимое уже в main.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
