#!/usr/bin/env python3
"""Удаляет remote-ветки enoTone после создания тегов archive/.

Требует, чтобы тег archive/<branch> уже существовал на origin: без него ветка
не удаляется, чтобы не потерять единственную копию коммита.

Запуск:
  python tools/delete_branches.py --dry-run   — показать план
  python tools/delete_branches.py             — удалить
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
PREFIX = "archive/"


def git(*args: str, check: bool = False) -> tuple[str, int]:
    result = subprocess.run(
        ["git", "-c", "http.sslBackend=openssl", *args],
        cwd=REPO, capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if check and result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)}: {result.stderr.strip()}")
    return result.stdout.strip(), result.returncode


def remote_branches() -> list[str]:
    out, _ = git("ls-remote", "--heads", "origin")
    names = []
    for line in out.splitlines():
        parts = line.split("\t", 1)
        if len(parts) != 2:
            continue
        name = parts[1].removeprefix("refs/heads/")
        if name != "main":
            names.append(name)
    return names


def archived_tags() -> set[str]:
    out, _ = git("ls-remote", "--tags", "origin", PREFIX + "*")
    tags = set()
    for line in out.splitlines():
        parts = line.split("\t", 1)
        if len(parts) != 2:
            continue
        tags.add(parts[1].removeprefix("refs/tags/").removesuffix("^{}"))
    return tags


def main() -> int:
    dry = "--dry-run" in sys.argv
    branches = remote_branches()
    tags = archived_tags()
    print(f"remote-веток: {len(branches)}, тегов archive: {len(tags)}")

    protected: list[str] = []
    doomed: list[str] = []
    for name in branches:
        if PREFIX + name in tags:
            doomed.append(name)
        else:
            protected.append(name)

    if protected:
        print(f"\nБЕЗ ТЕГА — не удаляются ({len(protected)}):")
        for name in protected:
            print(f"  {name}")

    if dry:
        print(f"\nбудет удалено: {len(doomed)}")
        for name in doomed[:10]:
            print(f"  {name}")
        if len(doomed) > 10:
            print(f"  ... и ещё {len(doomed) - 10}")
        print("\n--dry-run: ветки не удалены")
        return 0

    deleted = 0
    failed: list[str] = []
    for name in doomed:
        _, code = git("push", "--quiet", "origin", "--delete", name)
        if code == 0:
            deleted += 1
            if deleted % 25 == 0:
                print(f"  удалено: {deleted}")
        else:
            failed.append(name)

    print(f"\nудалено веток: {deleted}")
    if failed:
        print(f"НЕ УДАЛОСЬ ({len(failed)}):")
        for name in failed:
            print(f"  {name}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
