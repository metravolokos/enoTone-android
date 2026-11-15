"""Поведенческие тесты проверки отчётов ревью (без внешних файлов-образцов)."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import review_helper


HEAD = "a" * 40
PREVIOUS = "b" * 40
SOURCE_LINK = f"blob/{HEAD}/app/src/main/Test.kt#L42"
TRIGGER_ROWS = "\n".join(
    f"| {name} | нет | Сверено поведение всех связанных маршрутов |"
    for name in review_helper.TRIGGERS
)


def finding(ident="R-2026-11-15-01", heading=None, link=SOURCE_LINK, decision="INFO",
            abbreviated=False):
    return f"""### {heading or ident} — Проверка контракта
- **Серьёзность:** низкая
- **Подтверждение:** подтверждено кодом
- **Решение:** {decision}
- **{'Отклонение' if abbreviated else 'Отклонение от утверждённого'}:** нет
- **Условия и шаги:** открыть экран и повторить действие
- **Ожидается / наблюдается:** переход без ошибки
- **{'Ссылка' if abbreviated else 'Ссылки'}:** {link}
- **{'Проверка' if abbreviated else 'Проверка исправления'}:** Compose UI
"""


def full_report(remarks=None, changes=None, verdict="**Вердикт:** MERGE_PROPOSED"):
    if remarks is None:
        remarks = finding()
    if changes is None:
        changes = TRIGGER_ROWS
    return f"""# Ревью PR #200: контракт
## 0. Версия и границы
- HEAD: {HEAD}
## 1. Триггеры
| Триггер | Срабатывает? | Что сверено |
|---|---|---|
{changes}
## 2. Новое поведение и тесты
Нового поведения нет: сверены прежние тесты.
## 3. Замечания
{remarks}
## 4. Отклонения от утверждённого
Нет отклонений.
## 5. Что проверено
CI #120 проверен успешно.
## 6. Итог
{verdict}
CI точного HEAD: SUCCESS, run 120.
"""


def recheck_report(status_rows=None, changes=None, verdict="**Вердикт:** MERGE_PROPOSED", version=None,
                   first="# Повторная проверка PR #161", extra=""):
    if version is None:
        version = f"- Проверяемый HEAD: {HEAD}\n- HEAD предыдущей проверки: {PREVIOUS}"
    if status_rows is None:
        status_rows = "| R-1 | исправлено | По тесту ошибка больше не воспроизводится | BLOCK |"
    if changes is None:
        changes = "Код не менялся; по триггерам вывод не меняется после повторной проверки."
    return f"""{first}
## Версия и границы
{version}
## Что изменилось с предыдущей проверки
{changes}
## Статус прежних замечаний
| ID | Статус | Основание | Решение |
|---|---|---|---|
{status_rows}
{extra}
## Итог
{verdict}
CI точного HEAD: зелёный, run #222.
"""


class ReviewHelperTests(unittest.TestCase):
    def check_text(self, content: str):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "report.md"
            path.write_text(content, encoding="utf-8")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = review_helper.check(str(path))
            return code, out.getvalue()

    def assert_ok(self, text):
        code, out = self.check_text(text)
        self.assertEqual(0, code, out)

    def assert_bad(self, text, reason=None):
        code, out = self.check_text(text)
        self.assertEqual(1, code, out)
        if reason:
            self.assertIn(reason, out)

    def test_full_report_with_long_id(self):
        self.assert_ok(full_report())

    def test_bold_finding_heading(self):
        self.assert_ok(full_report(finding("R-1", heading="**R-1**")))

    def test_abbreviated_field_names(self):
        self.assert_ok(full_report(finding(abbreviated=True)))

    def test_shared_artifact_heading_for_two_triggers(self):
        rows = TRIGGER_ROWS.replace("| T-LIFE | нет |", "| T-LIFE | да |")
        rows = rows.replace("| T-CONC | нет |", "| T-CONC | да |")
        report = full_report(changes=rows).replace(
            "## 2. Новое поведение", "### Артефакты T-LIFE и T-CONC\n| Событие | Результат |\n|---|---|\n| Пересоздание | Состояние не теряется |\n## 2. Новое поведение"
        )
        self.assert_ok(report)

    def test_verdict_on_separate_line(self):
        self.assert_ok(full_report(verdict="**Вердикт:**\nMERGE_PROPOSED"))

    def test_placeholder_verdict_rejected(self):
        self.assert_bad(full_report(verdict="**Вердикт:** MERGE_PROPOSED / FIX_REQUIRED / OWNER_DECISION"), "нет строки «Вердикт")

    def test_r1_unrecognized_heading_cannot_use_zero_findings_in_section_five(self):
        report = full_report(remarks="### Дефект заголовка\nТут потерян ID замечания.\n")
        report = report.replace("## 5. Что проверено", "## 5. Что проверено\nЗамечаний нет в прошлом отчёте.")
        self.assert_bad(report, "заголовок не похож на ID замечания")

    def test_no_findings_phrase_only_inside_section_three(self):
        self.assert_ok(full_report(remarks="Подтверждённых замечаний нет в проверенном объёме."))

    def test_valid_brief_recheck(self):
        self.assert_ok(recheck_report())

    def test_recheck_detected_by_status_section_without_title(self):
        self.assert_ok(recheck_report(first="# Ревью PR 161"))

    def test_recheck_missing_previous_sha(self):
        self.assert_bad(recheck_report(version=f"- Проверяемый HEAD: {HEAD}"), "два разных полных SHA")

    def test_recheck_sha_must_be_different_and_in_different_lines(self):
        self.assert_bad(recheck_report(version=f"- HEAD: {HEAD} и {PREVIOUS}"), "в разных строках")
        self.assert_bad(recheck_report(version=f"- HEAD: {HEAD}\n- Старый HEAD: {HEAD}"), "два разных полных SHA")

    def test_recheck_missing_status_table(self):
        self.assert_bad(recheck_report(status_rows=""), "нужна таблица")

    def test_recheck_missing_conclusion(self):
        self.assert_bad(recheck_report().split("## Итог")[0], "нет раздела «Итог»")

    def test_recheck_new_finding_missing_link(self):
        report = recheck_report(extra="\n## Замечания\n" + finding("N-1").replace("- **Ссылки:** " + SOURCE_LINK + "\n", ""))
        self.assert_bad(report, "N-1: нет поля «Ссылки»")
        # Поля следующего ##-раздела не могут принадлежать новому замечанию.
        report = report.replace("## Итог", f"## Итог\n- **Ссылки:** {SOURCE_LINK}")
        self.assert_bad(report, "N-1: нет поля «Ссылки»")

    def test_recheck_code_changed_requires_trigger_reassessment(self):
        self.assert_bad(recheck_report(changes="Код изменён в контроллере, тесты обновлены."),
                        "в таблице триггеров нет строки T-NAV")

    def test_recheck_code_changed_all_triggers_table_passes(self):
        changes = "Код изменён в контроллере; триггеры пересмотрены.\n| Триггер | Срабатывает? | Что сверено |\n|---|---|---|\n" + TRIGGER_ROWS
        self.assert_ok(recheck_report(changes=changes))

    def test_brief_without_marker_is_not_automatically_selected(self):
        text = recheck_report(first="# PR #161").replace("## Статус прежних замечаний", "## Результат проверки")
        self.assert_bad(text, "нет раздела «Триггеры»")

    def test_open_block_prevents_merge_in_brief(self):
        row = "| R-1 | открыто | Причина существует и требует исправления | BLOCK |"
        self.assert_bad(recheck_report(status_rows=row), "MERGE_PROPOSED при замечаниях BLOCK")

    def test_open_owner_requires_owner_decision(self):
        row = "| R-2 | не исправлено | Нужно решение владельца по альтернативе | OWNER |"
        self.assert_bad(recheck_report(status_rows=row), "MERGE_PROPOSED при замечаниях")

    def test_status_has_at_least_fifteen_characters_of_justification(self):
        self.assert_bad(recheck_report(status_rows="| R-1 | исправлено | кратко | BLOCK |"),
                        "основание короче 15")

    def test_recheck_new_finding_requires_exact_line_link(self):
        report = recheck_report(extra="\n## Замечания\n" + finding("N-1", link=f"blob/{HEAD}/foo.kt"))
        self.assert_bad(report, "в «Ссылки» нужны строки")


if __name__ == "__main__":
    unittest.main()
