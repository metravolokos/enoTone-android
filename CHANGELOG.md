# CHANGELOG

## Unreleased — M0
- Создан отдельный Android Kotlin/Compose каркас enoTone с 15 Gradle-модулями.
- Добавлены WebDAV diagnostic UI, opt-in Depth infinity и Range, MockWebServer/Python tests и CI.
- Зафиксированы исходное ТЗ и ADR-0001—0012.
- Исправлены настройка Android SDK runner, переход на Kotlin compilerOptions и совместимая с API36 Compose BOM.
- Промежуточный CI проверил build/tests/lint, но выявил необязательный маркер GMS в APK: запущена проверка R8 shrink без ослабления GMS gate.
- Приложение всё ещё на M0, доступный APK и реальное облако не подтверждены.
- Actions run 42463446577: локализован необязательный Google TLS adapter в OkHttp, который оставался в DEX после R8; GMS gate не ослаблен.
- В M0 начата проверка замены runtime OkHttp на Ktor CIO 3.6.1 (ADR-0013); тестовый MockWebServer по-прежнему использует OkHttp только в test classpath. Проверить CI на новом HEAD.
