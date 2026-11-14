# THIRD PARTY LICENSES

Предварительный набор: AGP, Kotlin/Compose, AndroidX, Ktor CIO (Apache-2.0, только runtime M0), OkHttp/MockWebServer (Apache-2.0, только тесты). Инструменты сборки (не входят в APK): detekt 1.23.8 и detekt-formatting/ktlint (Apache-2.0), Gradle Wrapper 8.13 (Apache-2.0). Добавлены Room 2.8.5 и KSP 2.3.10 (проверить license/SPDX для release). Для **реально использованных** версий до публикации релиза оформить SPDX, copyright и полный dependency audit; этот каркас не заменяет аудита. Лицензия собственного исходного кода ожидает решения владельца.

- **Gson** — удалён в PR «Качество» (#121): очередь хранится через встроенный `org.json`.

- **AndroidX Lifecycle 2.9.4** — `lifecycle-viewmodel-compose` и `lifecycle-runtime-compose` (`collectAsStateWithLifecycle`), Apache License 2.0; использование в M6-0/4a.
- **kotlinx-coroutines-test 1.10.2** — JetBrains, Apache License 2.0, только JUnit/Gradle test-зависимость M6-0/4a; не включается в runtime APK.
- **Onest** (The Onest Project Authors) и **Unbounded** (The Unbounded Project Authors) — SIL Open Font License 1.1; вариативные TTF из `github.com/google/fonts` вшиты в APK (`res/font`), не загружаются из сети. Тексты лицензий — [`docs/licenses/`](licenses/). ADR-0079.
- **Haze 1.7.2** (`dev.chrisbanes.haze:haze`) — Chris Banes, Apache License 2.0; размытие под полоской прокрутки (ADR-0086, решение владельца 2026-11-14). Только `:core:ui`, без сети и GMS.
- **Roborazzi 1.76.0** — Apache License 2.0, только тесты `:core:ui` (ADR-0084); в APK не входит.
- Иконка, значки, логотип и изображения Бандита — собственные ресурсы проекта из `metravolokos/enotone-design`.
