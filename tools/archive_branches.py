#!/usr/bin/env python3
"""Создаёт теги archive/<branch> для веток перед удалением.

Скрипт МЕНЯЕТ состояние: создаёт и пушит теги. Использовать только после
решения владельца об уборке.

Порядок:
  1. для каждой remote-ветки создать локальный тег archive/<name> на её tip;
  2. запушить все теги;
  3. только после этого удалять ветки.

Запуск:
  python tools/archive_branches.py --dry-run   — показать план
  python tools/archive_branches.py             — создать и запушить теги
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


def remote_branches() -> list[tuple[str, str]]:
    out, _ = git("ls-remote", "--heads", "origin")
    branches = []
    for line in out.splitlines():
        parts = line.split("\t", 1)
        if len(parts) != 2:
            continue
        sha, ref = parts
        name = ref.removeprefix("refs/heads/")
        if name != "main":
            branches.append((name, sha))
    return branches


def main() -> int:
    dry = "--dry-run" in sys.argv
    branches = remote_branches()
    print(f"remote-веток (без main): {len(branches)}")

    created = 0
    skipped = 0
    for name, sha in branches:
        tag = PREFIX + name
        _, exists = git("rev-parse", "--verify", f"refs/tags/{tag}")
        if exists == 0:
            skipped += 1
            continue
        if dry:
            print(f"  would tag {tag} -> {sha[:8]}")
            created += 1
            continue
        _, code = git("tag", tag, sha)
        if code != 0:
            print(f"  FAILED tag {tag}", file=sys.stderr)
            continue
        created += 1
        if created % 20 == 0:
            print(f"  создано тегов: {created}")

    print(f"создано тегов: {created}, уже были: {skipped}")

    if dry:
        print("\n--dry-run: теги не созданы")
        return 0

    print("\nпуш тегов в origin...")
    out, code = git("push", "--quiet", "origin", "--tags")
    if code != 0:
        print(f"  push FAILED: {out}", file=sys.stderr)
        return 1
    print("  теги запушены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
