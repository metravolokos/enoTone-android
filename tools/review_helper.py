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
ALT = {"Ссылки": "Ссылк[аи]", "Проверка исправления": "Проверка(?: исправления)?", "Отклонение от утверждённого": "Отклонение(?: от утверждённого)?"}
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


ID_PATTERN = re.compile(r"^\*{0,2}([A-ZА-Я]{1,4}-(?=[\w-]*\d)[\w-]+)\*{0,2}(?=\s|[—–:.,]|$)", re.I)
SHA_PATTERN = re.compile(r"\b[0-9a-f]{40}\b", re.I)
ZERO_FINDINGS = re.compile(r"замечаний нет|нет подтверждённых замечаний", re.I)
STATUS_PATTERN = re.compile(r"^(исправлено|не исправлено|без изменений|открыто)\b", re.I)


def section(text: str, title: str) -> str | None:
    """Только тело указанного ##-раздела, без других разделов второго уровня."""
    head = re.search(rf"^##\s+[^\n]*{re.escape(title)}[^\n]*$", text, re.M | re.I)
    if head is None:
        return None
    next_head = re.search(r"^##\s+", text[head.end():], re.M)
    end = head.end() + next_head.start() if next_head else len(text)
    return text[head.end():end]


def trigger_rows(text: str, errors: list[str]) -> None:
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
        if answer == "да" and not re.search(
            rf"^#{{2,4}}\s+.*Артефакт\w*\s+.*{re.escape(name)}", text, re.M | re.I
        ):
            errors.append(f"{name}: триггер сработал, но нет раздела «Артефакт {name}»")


def read_findings(text: str, recheck: bool, errors: list[str]) -> list[tuple[str, str]]:
    remarks = section(text, "Замечания")
    source = text if recheck else (remarks or "")
    findings: list[tuple[str, str]] = []
    headings = list(re.finditer(r"^###\s+([^\n]+)$", source, re.M))
    for i, head in enumerate(headings):
        heading = head.group(1).strip()
        ident = ID_PATTERN.match(heading)
        if ident:
            end = headings[i + 1].start() if i + 1 < len(headings) else len(source)
            next_section = re.search(r"^##\s+", source[head.end():], re.M)
            if next_section is not None:
                end = min(end, head.end() + next_section.start())
            findings.append((ident.group(1), source[head.end():end]))
        elif not recheck or (remarks is not None and re.search(
            rf"^###\s+{re.escape(heading)}\s*$", remarks, re.M
        )):
            errors.append(f"заголовок не похож на ID замечания: {heading[:70]}")
    if remarks is not None and not any(ID_PATTERN.match(h.group(1).strip()) for h in
                                   re.finditer(r"^###\s+([^\n]+)$", remarks, re.M)):
        if not ZERO_FINDINGS.search(remarks):
            errors.append("не найдено ни одного замечания (### <ID> — название); если замечаний нет, напишите это прямо в разделе 3")
    return findings


def field_value(body: str, field: str) -> str:
    pattern = ALT.get(field, re.escape(field))
    m = re.search(rf"^\s*[-*]?\s*\*{{0,2}}{pattern}\*{{0,2}}\s*[:|]\s*\*{{0,2}}([^\n|]*)", body,
                  re.M | re.I)
    return m.group(1).strip().strip("* ").strip(chr(96)).strip(".,;") if m else ""


def check_findings(findings: list[tuple[str, str]], recheck: bool, errors: list[str]) -> set[str]:
    decisions: set[str] = set()
    for ident, body in findings:
        for field in FIELDS:
            pattern = ALT.get(field, re.escape(field))
            if not re.search(rf"^\s*[-*]?\s*\*{{0,2}}{pattern}\*{{0,2}}\s*[:|]", body, re.M | re.I):
                errors.append(f"{ident}: нет поля «{field}»")
        if field_value(body, "Серьёзность").lower().split(" ")[0] not in SEVERITY:
            errors.append(f"{ident}: серьёзность вне {sorted(SEVERITY)}")
        if not any(field_value(body, "Подтверждение").lower().startswith(c) for c in CONFIRM):
            errors.append(f"{ident}: подтверждение вне {sorted(CONFIRM)}")
        decision = field_value(body, "Решение").split(" ")[0].upper()
        if decision not in DECISION:
            errors.append(f"{ident}: решение вне {sorted(DECISION)}")
        else:
            decisions.add(decision)
        url = field_value(body, "Ссылки")
        if not re.search(r"blob/[0-9a-f]{40}/", body, re.I) or (
            recheck and not re.search(r"blob/[0-9a-f]{40}/[^\s)]+#L\d+", url, re.I)
        ):
            errors.append(f"{ident}: в «Ссылки» нужны строки конкретного коммита blob/<SHA>/путь#Lnn")
    return decisions


def prior_statuses(text: str, errors: list[str]) -> set[str]:
    body = section(text, "Статус прежних замечаний") or ""
    seen = 0
    has_header = False
    open_decisions: set[str] = set()
    for line in body.splitlines():
        if not line.strip().startswith("|") or not line.rstrip().endswith("|"):
            continue
        cells = [c.strip().strip("* ").strip(chr(96)) for c in line.strip().strip("|").split("|")]
        if any("Статус" == cell for cell in cells):
            has_header = True
            continue
        id_pos = next((i for i, c in enumerate(cells) if ID_PATTERN.fullmatch(c)), None)
        if id_pos is None:
            continue
        seen += 1
        status_pos = next((i for i, c in enumerate(cells) if STATUS_PATTERN.match(c)), None)
        if status_pos is None:
            errors.append(f"{cells[id_pos]}: нет допустимого статуса прежнего замечания")
            continue
        reason = " ".join(c for i, c in enumerate(cells) if i not in (id_pos, status_pos)
                          and c.upper() not in DECISION)
        if len(reason.strip()) < 15:
            errors.append(f"{cells[id_pos]}: основание короче 15 символов")
        status = STATUS_PATTERN.match(cells[status_pos]).group(1).lower()
        if status != "исправлено":
            for decision in ("BLOCK", "VERIFY_REQUIRED", "OWNER"):
                if any(re.search(rf"\b{decision}\b", c, re.I) for c in cells):
                    open_decisions.add(decision)
    if not has_header or not seen:
        errors.append("раздел «Статус прежних замечаний»: нужна таблица с ID, статусом и основанием")
    return open_decisions


def verdict_in(text: str) -> str | None:
    """Ищем один конкретный вердикт, но не строку с тремя вариантами."""
    conclusion = section(text, "Итог") or ""
    lines = [line.strip().replace("**", "").replace(chr(96), "") for line in conclusion.splitlines()]
    for i, line in enumerate(lines):
        value = re.sub(r"^[-*]\s+", "", line).strip()
        with_label = re.fullmatch(r"Вердикт\s*:\s*(MERGE_PROPOSED|FIX_REQUIRED|OWNER_DECISION)\.?", value, re.I)
        if with_label:
            return with_label.group(1).upper()
        if re.fullmatch(r"Вердикт\s*:\s*", value, re.I):
            following = next((n for n in lines[i + 1:] if n), "")
            if re.fullmatch(r"(MERGE_PROPOSED|FIX_REQUIRED|OWNER_DECISION)\.?", following):
                return following.rstrip(".")
        if re.fullmatch(r"(MERGE_PROPOSED|FIX_REQUIRED|OWNER_DECISION)\.?", value):
            return value.rstrip(".")
    return None


def check(path: str) -> int:
    with open(path, encoding="utf-8") as report:
        text = report.read()
    errors: list[str] = []
    first = text.splitlines()[0] if text.splitlines() else ""
    recheck = bool(re.search(r"^#\s+.*повторн", first, re.I)) or section(text, "Статус прежних замечаний") is not None
    required = ("Версия и границы", "Что изменилось", "Статус прежних замечаний", "Итог") if recheck else (
        "Версия и границы", "Триггеры", "Новое поведение и тесты", "Замечания",
        "Отклонения от утверждённого", "Что проверено", "Итог"
    )
    for title in required:
        if section(text, title) is None:
            errors.append(f"нет раздела «{title}»")
    if recheck:
        version = section(text, "Версия и границы") or ""
        sha_lines = [(idx, sha.group().lower()) for idx, line in enumerate(version.splitlines())
                     for sha in SHA_PATTERN.finditer(line)]
        if len(set(sha for _, sha in sha_lines)) < 2 or len({line for line, _ in sha_lines}) < 2:
            errors.append("для повторной проверки нужны два разных полных SHA в разных строках раздела «Версия и границы»")
        changed = section(text, "Что изменилось") or ""
        if not changed.strip():
            errors.append("раздел «Что изменилось» пуст")
        if not re.search(r"\bкод(?:а|е|у|ом)?\b", changed, re.I) or not re.search(r"измен|менял|не трог|без прав", changed, re.I):
            errors.append("в разделе «Что изменилось» явно укажите, менялся ли код")
        if not re.search(r"вывод(?:ы)?\s+не\s+меня(?:ется|ются)", changed, re.I):
            trigger_rows(changed, errors)
        prior_decisions = prior_statuses(text, errors) if section(text, "Статус прежних замечаний") is not None else set()
    else:
        prior_decisions = set()
        if not SHA_PATTERN.search(text):
            errors.append("нет полного SHA (40 символов) проверяемого коммита")
        trigger_rows(text, errors)
        if not re.search(r"\|\s*Поведение\s*\|\s*Тест\s*\|\s*Слой", text) and \
                "нового поведения нет" not in text.lower():
            errors.append("нет таблицы «Поведение / Тест / Слой» (правило 12) или фразы «нового поведения нет»")
    findings = read_findings(text, recheck, errors)
    decisions = check_findings(findings, recheck, errors) | prior_decisions
    verdict = verdict_in(text)
    if verdict is None:
        errors.append("нет строки «Вердикт: MERGE_PROPOSED / FIX_REQUIRED / OWNER_DECISION»")
    else:
        blocking = bool(decisions & {"BLOCK", "VERIFY_REQUIRED"})
        asking = "OWNER" in decisions
        answered = bool(re.search(r"^#{2,4}\s+\**Решения владельца", text, re.M))
        if verdict == "MERGE_PROPOSED" and (blocking or asking):
            errors.append("вердикт MERGE_PROPOSED при замечаниях BLOCK/VERIFY_REQUIRED/OWNER")
        if verdict == "FIX_REQUIRED" and not blocking:
            errors.append("вердикт FIX_REQUIRED без замечаний BLOCK/VERIFY_REQUIRED")
        if verdict == "FIX_REQUIRED" and asking and not answered:
            errors.append("FIX_REQUIRED при неотвеченных OWNER: сначала «Вопросы владельцу», вердикт OWNER_DECISION")
        if verdict == "OWNER_DECISION" and not asking:
            errors.append("вердикт OWNER_DECISION без замечаний OWNER")
        if verdict == "OWNER_DECISION" and not re.search(r"^#{2,4}\s+\**Вопросы владельцу", text, re.M):
            errors.append("OWNER_DECISION без раздела «Вопросы владельцу»")
        if verdict == "OWNER_DECISION" and re.search(r"^#{2,4}\s+\**Задание исполнителю", text, re.M):
            errors.append("при OWNER_DECISION задание исполнителю не выдаётся до ответов владельца")
        if verdict == "FIX_REQUIRED" and not re.search(r"^#{2,4}\s+\**Задание исполнителю", text, re.M):
            errors.append("FIX_REQUIRED без раздела «Задание исполнителю»")
        if verdict == "MERGE_PROPOSED" and not re.search(
            r"(?:CI[^\n]*(?:зелён|успешн|success|passed)|(?:зелён|успешн|success|passed)[^\n]*CI)", text, re.I
        ):
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
