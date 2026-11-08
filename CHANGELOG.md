# CHANGELOG

## Unreleased — M0
- Создан отдельный нативный Android Kotlin/Compose каркас enoTone с 15 Gradle-модулями, документами и ADR.
- Реализован WebDAV diagnostic UI, opt-in Depth infinity, GET Range, MockWebServer/Python tests, CI с GMS gate.
- Устранены ошибки первоначальной сборки SDK/Compose/Kotlin. Запрет GMS в runtime APK сохранён без исключений: для M0 использован Ktor CIO и debug-R8 (ADR-0013).
- Для CI добавлены проверяемые резервные загрузки Gradle 8.13 и Kotlin compiler 2.3.10 при ошибках HTTP 504.
- [Actions run #10](https://github.com/metravolokos/enoTone-android/actions/runs/42465355583) на `3ceed82abb446471a7d9b0f1449f90050fb5e1ee`: зелёный build, тесты, lint, GMS gate; artifact `enotone-m0-debug-apk`.
- 2026-11-08: владелец установил диагностический APK на Huawei и подтвердил Cloud.Mail.ru PROPFIND root и /AllMusic (207), GET Range 0-0 (206, ETag в заголовке), и тайм-аут Depth infinity на 15 с. Отчёт без секретов записан в `docs/CLOUD_MAILRU_WEBDAV.md`.
- 2026-11-08: владелец уточнил, что `/AllMusic` — одноуровневая папка без подпапок; план M1 сокращён до одного `PROPFIND Depth: 1` без рекурсии, альбомы/артисты определяются из тегов.
- M0 ожидает формальной приёмки, PR остаётся Draft, M1 не начат.
