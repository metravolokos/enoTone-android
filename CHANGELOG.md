# CHANGELOG

## Unreleased — M0
- Создан отдельный Android Kotlin/Compose каркас enoTone с 15 Gradle-модулями.
- Добавлены WebDAV diagnostic UI, opt-in Depth infinity и Range, MockWebServer/Python tests и CI.
- Зафиксированы исходное ТЗ и ADR-0001—0012.
- Исправлены настройка Android SDK runner, переход на Kotlin compilerOptions и совместимая с API36 Compose BOM.
- Промежуточный CI проверил build/tests/lint, но выявил необязательный маркер GMS в APK: запущена проверка R8 shrink без ослабления GMS gate.
- Приложение всё ещё на M0, доступный APK и реальное облако не подтверждены.
