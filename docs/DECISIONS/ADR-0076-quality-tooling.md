# ADR-0076: Инструменты качества — Gradle Wrapper, detekt + ktlint, lint всех модулей, Dependabot

Дата: 2026-11-12 | Статус: принято | Автор: Claude (шаг M6-0/8 из ADR-0074)

> Номер 0075 зарезервирован за шагом M6-0/3 (сохранение очереди), который делается параллельно.

## Контекст

ТЗ (разделы 3 и 9) требует ktlint/spotless, detekt, Android Lint, Gradle Wrapper и Dependabot/Renovate. Аудит 2026-11-11: Wrapper отсутствовал (только `.properties`), статического анализа Kotlin не было, lint запускался только для `:app`, тесты и lint в CI перечислялись помодульно — новый модуль не проверялся бы без правки CI.

## Рассмотренные варианты

1. **detekt + detekt-formatting (правила ktlint внутри detekt)** — один инструмент, один отчёт, одна конфигурация. Минус: версия ktlint привязана к версии detekt.
2. **detekt + отдельный плагин `org.jlleitschuh.gradle.ktlint`** — свежий ktlint независимо от detekt. Минус: два инструмента, два отчёта, две конфигурации.
3. **Spotless + detekt** — Spotless умеет автоформатирование. Минус: ещё одна зависимость; автоформатирование всего проекта ТЗ запрещает смешивать с функциональными PR.

Для режима проверки:
- **сразу блокировать CI** — сломает все текущие PR (546 замечаний);
- **baseline-файл** — скрывает долг, новые нарушения блокируются; минус — baseline устаревает при переносе кода в модули (M6-0/4–5), и его пришлось бы регулярно пересоздавать;
- **report-only** (`ignoreFailures = true`) — отчёт в CI-артефакте, ничего не блокируется.

## Решение

- Вариант 1: detekt 1.23.8 (Apache-2.0) + detekt-formatting; конфигурация `config/detekt/detekt.yml` поверх стандартной.
- **Report-only** до завершения переноса кода в модули (M6-0/5). Блокирующий режим включается отдельным PR после разбора замечаний.
- Отключены 5 чисто косметических правил ktlint (Indentation, ArgumentListWrapping, Wrapping, MultiLineIfElse, ParameterListWrapping): они дают ~3300 замечаний о стиле переносов, которые исправляются только массовым переформатированием. Остальные правила detekt и ktlint активны.
- Gradle Wrapper 8.13 с `distributionSha256Sum`; в CI — `gradle/actions/wrapper-validation`. CI по-прежнему ставит Gradle собственным шагом с проверкой SHA-256 и запасными зеркалами (так он стабилен при сбоях CDN), wrapper — для локальной сборки.
- CI запускает корневые `testDebugUnitTest` и `lintDebug` — во **всех** модулях. Android Lint в модулях `:core:*`, `:data:*`, `:playback` на 2026-11-12: 0 ошибок (3 предупреждения в `:playback`).
- Dependabot: Gradle — еженедельно, не более 5 PR, группы Kotlin+KSP, Media3, Room; GitHub Actions — ежемесячно. Ничего не сливается автоматически.

## Последствия

- Каждый PR даёт артефакт `detekt-reports`. Замечания — справочно, исправлять при переносе кода в модули.
- Новые модули автоматически покрываются тестами и lint.
- Dependabot будет открывать PR; обновления Media3/Compose/AGP/Kotlin сливать только после CI и проверки на Huawei.

## Как проверить / когда пересмотреть

`./gradlew :app:assembleDebug testDebugUnitTest lintDebug detekt` локально. Пересмотреть после M6-0/5: отключить `ignoreFailures`, решить по косметическим правилам (автоформат всего проекта отдельным PR или оставить выключенными).

## Дополнение: JVM-поведенческие тесты M6-0/5a (Draft #118, 2026-11-13)

- Принята инфраструктура Robolectric 4.17 (SDK 35, JVM), `androidx.test:core` / `ext:junit`, `room-testing` той же версии, что Room, и Compose UI Test из действующего Compose BOM. Эмулятор в CI не требуется.
- Тестовые зависимости подключены через version catalog к `testImplementation` и `debugImplementation` (`ui-test-manifest`). `release` / `stablePreview`, исходники Room, приложение и ресурсы дизайна не меняются.
- **Экспортированные схемы Room v1–v4 подключены только как `debug` assets модуля `:core:database`** (`sourceSets.getByName("debug").assets.srcDir("schemas")`). Вариант `stablePreview` приложения разрешает зависимость от `release`-варианта библиотеки, поэтому JSON-схемы не упаковываются в подписанную Stable Preview и обычный release; `debug` APK получает схемы только для Robolectric/MigrationTestHelper.
- `MigrationTestHelper` прогоняет существующие auto-migrations на JSON-схемах v1–v4; отдельные DAO-тесты создают in-memory Room; Compose тесты нажимают настоящие элементы с фейковыми действиями. Строковые Python-проверки можно удалять только после полной поведенческой замены, с таблицей доказательств.
- Keystore не подменяется фиктивной защитой: сценарий pending sign-out тестируется через порт и фейк, а `SignOutMarker` — в настоящих SharedPreferences Robolectric. Huawei остаётся источником доказательств для реального Keystore и поведения уведомления с очередью.
- Разбор 24 старых `tools/test_*_contract.py` по PR #118: 6 файлов удаляются только после проверки заменяющих JUnit/Robolectric/Compose тестов, 18 сохраняются как явные policy-гейты или частичные проверки; для каждого файла решение и непокрытые аспекты указаны в PR. `test_mock_webdav.py` остаётся. Прежний HEAD `73e2d49` прошёл CI #504 без увеличения времени; окончательный HEAD требует повторного зелёного CI. Вопрос перевода detekt в blocking-режим — отдельный PR после M6-0/5.
