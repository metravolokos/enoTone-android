#!/usr/bin/env python3
"""Помощник ревью по AUDIT_PROTOCOL v2: обход триггеров и проверка формы отчёта.

  python3 tools/review_helper.py triggers <BASE> <HEAD>   кандидаты по всем семи триггерам
  python3 tools/review_helper.py check <report.md>        отчёт имеет все обязательные части

Скрипт не оценивает качество: он не даёт пропустить триггер и поле. Совпадение в
diff — только подсказка (AUDIT_PROTOCOL §2, правило 10); решает ответ на поведенческий вопрос.
"""
import re
import subprocess
import sys

TRIGGERS = {
    "T-NAV": r"BackHandler|PredictiveBackHandler|AnimatedContent|AppShell|NavHost|"
             r"clearAndSetSemantics|hideFromAccessibility|enabled\s*=",
    "T-VM": r"ViewModel|StateFlow|collectAsState|collectAsStateWithLifecycle|SavedStateHandle",
    "T-LIFE": r"rememberSaveable|SavedState|onSaveInstanceState|onDestroy|onTaskRemoved|"
              r"Lifecycle|restore|Restore",
    "T-PERSIST": r"@Entity|@Dao|Migration|SharedPreferences|File\(|renameTo|writeText|"
                 r"atomic|Keystore|DataStore|AtomicFile",
    "T-AUTH/NET": r"\b40[13]\b|Authorization|credential|Credential|Keystore|WebDav|"
                  r"HttpURLConnection|OkHttp|password|Log\.[diwev]\(",
    "T-CONC": r"\blaunch\b|\basync\b|\bJob\b|delay\(|cancel|Dispatchers|withContext|Mutex|"
              r"Atomic[A-Z]\w*|@Volatile|synchronized|\bHandler\(|postDelayed|Executor|SideEffect",
    "T-PLAYBACK": r"\bPlayer\b|MediaSession|MediaItem|nextMediaItemIndex|ExoPlayer|"
                  r"PlaybackConnection|PlaybackSnapshot|[Qq]ueue",
}
SHOWN = 6

SEVERITY = {"критично", "высокая", "средняя", "низкая"}
CONFIRM = {"воспроизведено", "подтверждено кодом", "предполагаемый риск"}
DECISION = {"BLOCK", "VERIFY_REQUIRED", "OWNER", "FOLLOWUP", "INFO"}
FIELDS = ("Серьёзность", "Подтверждение", "Решение", "Отклонение от утверждённого",
          "Ссылки", "Проверка исправления")
EMPTY = {"", "-", "—", "ок", "ok", "нет данных"}


def added_lines(base: str, head: str):
    out = subprocess.run(["git", "diff", "-U0", f"{base}..{head}"], capture_output=True,
                         text=True, check=True).stdout
    path, line = "", 0
    for raw in out.splitlines():
        if raw.startswith("+++ "):
            path = raw[6:] if raw.startswith("+++ b/") else raw[4:]
        elif raw.startswith("@@"):
            m = re.search(r"\+(\d+)", raw)
            line = int(m.group(1)) if m else 0
        elif raw.startswith("+") and not raw.startswith("+++"):
            yield path, line, raw[1:].strip()
            line += 1


def triggers(base: str, head: str) -> int:
    lines = list(added_lines(base, head))
    print(f"База {base}, голова {head}: добавленных строк {len(lines)}.")
    print("Подсказки детекторов; каждый триггер нужно закрыть в отчёте строкой «да/нет + что сверено».\n")
    for name, pattern in TRIGGERS.items():
        rx = re.compile(pattern)
        hits = [(p, n, t) for p, n, t in lines if rx.search(t) and "/test/" not in p]
        files = sorted({p for p, _, _ in hits})
        print(f"{name}: совпадений {len(hits)}, файлов {len(files)}")
        for p, n, t in hits[:SHOWN]:
            print(f"    {p}:{n}  {t[:100]}")
        if len(hits) > SHOWN:
            print(f"    … ещё {len(hits) - SHOWN}")
    return 0


def check(path: str) -> int:
    text = open(path, encoding="utf-8").read()
    errors: list[str] = []
    for title in ("Версия и границы", "Триггеры", "Новое поведение и тесты", "Замечания",
                  "Отклонения от утверждённого", "Что проверено", "Итог"):
        if not re.search(rf"^##\s+.*{re.escape(title)}", text, re.M | re.I):
            errors.append(f"нет раздела «{title}»")
    if not re.search(r"blob/[0-9a-f]{40}|\b[0-9a-f]{40}\b", text):
        errors.append("нет полного SHA (40 символов) проверяемого коммита")
    rows = {}
    for m in re.finditer(r"^\|\s*(T-[A-Z/]+)\s*\|([^|]*)\|([^|]*)\|", text, re.M):
        rows[m.group(1)] = (m.group(2).strip().lower(), m.group(3).strip().lower())
    for name in TRIGGERS:
        if name not in rows:
            errors.append(f"в таблице триггеров нет строки {name}")
            continue
        answer, why = rows[name]
        if answer not in ("да", "нет"):
            errors.append(f"{name}: в колонке «Срабатывает?» должно быть «да» или «нет»")
        if why in EMPTY or len(why) < 15:
            errors.append(f"{name}: не указано, что именно сверено (правило 10)")
        if answer == "да" and not re.search(rf"^#{{2,4}}\s+.*Артефакт\s+{re.escape(name)}", text, re.M):
            errors.append(f"{name}: триггер сработал, но нет раздела «Артефакт {name}»")
    if not re.search(r"\|\s*Поведение\s*\|\s*Тест\s*\|\s*Слой", text) and \
            "нового поведения нет" not in text.lower():
        errors.append("нет таблицы «Поведение / Тест / Слой» (правило 12) или фразы «нового поведения нет»")
    sections = re.split(r"^###\s+", text, flags=re.M)[1:]
    findings = [s for s in sections if re.match(r"[A-ZА-Я]{1,3}-\d+", s)]
    for s in findings:
        ident = s.splitlines()[0][:40]
        for field in FIELDS:
            if not re.search(rf"{field}\*{{0,2}}\s*[:|]", s):
                errors.append(f"{ident}: нет поля «{field}»")
        def value(field: str) -> str:
            m = re.search(rf"{field}\*{{0,2}}\s*[:|]\*{{0,2}}\s*([^\n|]*)", s)
            return m.group(1).strip().strip("*").strip().strip(".;,").lower() if m else ""
        if value("Серьёзность").split(" ")[0].strip(".;,") not in SEVERITY:
            errors.append(f"{ident}: серьёзность вне {sorted(SEVERITY)}")
        if not any(value("Подтверждение").startswith(c) for c in CONFIRM):
            errors.append(f"{ident}: подтверждение вне {sorted(CONFIRM)}")
        if value("Решение").split(" ")[0].upper().strip("`.;,") not in DECISION:
            errors.append(f"{ident}: решение вне {sorted(DECISION)}")
        if not re.search(r"blob/[0-9a-f]{40}/", s):
            errors.append(f"{ident}: в «Ссылки» нужны строки конкретного коммита blob/<SHA>/путь#Lnn")
    verdict = re.search(r"Вердикт:\*{0,2}\s*(MERGE_PROPOSED|FIX_REQUIRED|OWNER_DECISION)\b", text)
    if not verdict:
        errors.append("нет строки «Вердикт: MERGE_PROPOSED / FIX_REQUIRED / OWNER_DECISION»")
    else:
        decisions = set()
        for f in findings:
            m = re.search(r"Решение\*{0,2}\s*[:|]\*{0,2}\s*([^\n|]*)", f)
            if m:
                decisions.add(m.group(1).strip().strip("*").strip().split(" ")[0].upper().strip("`.;,"))
        blocking = bool(decisions & {"BLOCK", "VERIFY_REQUIRED"})
        asking = "OWNER" in decisions
        v = verdict.group(1)
        if v == "MERGE_PROPOSED" and (blocking or asking):
            errors.append("вердикт MERGE_PROPOSED при замечаниях BLOCK/VERIFY_REQUIRED/OWNER")
        if v == "FIX_REQUIRED" and not blocking:
            errors.append("вердикт FIX_REQUIRED без замечаний BLOCK/VERIFY_REQUIRED")
        if v == "FIX_REQUIRED" and not re.search(r"^#{2,4}\s+Задание исполнителю", text, re.M):
            errors.append("FIX_REQUIRED без раздела «Задание исполнителю»")
        if v == "MERGE_PROPOSED" and not re.search(r"CI[^\n]*(зелён|success|passed)", text, re.I):
            errors.append("MERGE_PROPOSED без указания зелёного CI точного HEAD")
    for e in errors:
        print("ОШИБКА:", e)
    print(f"Замечаний в отчёте: {len(findings)}. Ошибок формы: {len(errors)}.")
    return 1 if errors else 0


def main() -> int:
    if len(sys.argv) == 4 and sys.argv[1] == "triggers":
        return triggers(sys.argv[2], sys.argv[3])
    if len(sys.argv) == 3 and sys.argv[1] == "check":
        return check(sys.argv[2])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
