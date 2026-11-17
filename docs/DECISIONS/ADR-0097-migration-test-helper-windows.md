# ADR-0097 — Тест-локальный обход дефекта `androidx.sqlite`, из-за которого `MigrationTestHelper` не работает на Windows

**Дата:** 2026-11-17. **Статус:** реализовано в ветке `test/f-01-migration-helper-windows`, ожидает приёмки владельцем. **Связано:** ADR-0076 (quality tooling, `MigrationTestHelper` и схемы как `debug` assets), ADR-0085 (цепочка миграций Room v1→v5), находка F-01 [аудита 2026-11-17](../audit/AUDIT_2026-11-17_local-state.md), Issue #191.

## Контекст

`EnoToneMigrationTest` — единственная проверка миграций Room v1→v7, то есть самого опасного класса изменений: потеря библиотеки из 3590 треков. Класс состоит из 7 тестов, все они идут через `androidx.room.testing.MigrationTestHelper`.

На Windows **все 7 из 7 падают** на первом же `helper.createDatabase(...)`, ещё до выполнения миграций:

```text
java.lang.IllegalArgumentException: This driver is configured to open a database named
'test-migrate-six-to-seven' but 'C:\Users\...\robolectric-...\
app.enotone.core.database.test-dataDir\databases\test-migrate-six-to-seven' was requested.
  at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:48)
```

Причина установлена по исходникам, а не по симптому.

1. `room-testing` 2.8.5 (`SupportTestConnectionManager.openConnection()`) запрашивает у драйвера **абсолютный путь**: `configuration.context.getDatabasePath(name).absolutePath`, где `context = instrumentation.targetContext`.
2. Драйвер создаётся с **коротким именем**: `openHelperConfig` получает `configuration.name` (например `test-migrate-six-to-seven`).
3. `androidx.sqlite:sqlite-framework` 2.6.2 (`SupportSQLiteDriver.open()`) сверяет их так:

```kotlin
require(
    openHelperDatabaseName == fileName ||
        openHelperDatabaseName.substringAfterLast('/') ==
            fileName.substringAfterLast('/')
) { "This driver is configured to open a database named ... but '$fileName' was requested." }
```

На Linux `absolutePath` содержит `/`, последний сегмент совпадает с именем — проверка проходит, CI зелёный. На Windows путь использует `\`, поэтому `substringAfterLast('/')` возвращает **весь путь целиком**, равенство с коротким именем ложно, и драйвер отказывает.

**Апгрейд библиотеки задачу не решает.** Та же строка с `substringAfterLast('/')` присутствует в `sources` `androidx.sqlite` 2.7.1 и 2.8.0-alpha03 (последняя версия на Google Maven). Это дефект переносимости в самой библиотеке.

Локальное следствие: без обхода ни одна правка миграций не верифицируется до пуша, локальный прогон `:core:database` красный, а зелёный CI на Linux не подтверждается локально.

## Решение — варианты

1. **Тест-локальная фабрика `SupportSQLiteOpenHelper.Factory`, сообщающая драйверу тот же абсолютный путь, который он получит при открытии (выбрано).** Передаём её в существующий конструктор `MigrationTestHelper(instrumentation, databaseClass, specs, openFactory)`. Драйверу сообщается `getDatabasePath(name).absolutePath` (сравнение попадает в ветку точного равенства `openHelperDatabaseName == fileName`, не зависящую от разделителя), а реальное открытие делегируется настоящему `FrameworkSQLiteOpenHelper` с исходным коротким именем. Плюсы: продукционные миграции, схема и файл БД не затрагиваются; поведение на Linux идентично исходному (при совпадении имён ветка та же); обход локализован в `src/test` модуля `:core:database`, публичный API приложения не меняется; утверждения всех 7 тестов сохранены дословно. Минусы: тестовая обвязка из ~40 строк, которую нужно удалить, когда дефект исправят в библиотеке.
2. **Апгрейд `androidx.sqlite` / `androidx.room` до версии с исправлением.** Плюс: не нужен обход в коде. Минус: исправления нет — та же логика в 2.6.2, 2.7.1 и 2.8.0-alpha03; вслепую тянуть alpha-зависимость в проект, где Room 2.8.5 стабилен, ради теста — несоразмерный риск. Отклонено (проверено по исходникам трёх версий).
3. **Подменить `targetContext` контекстом-обёрткой с `dataDir` без абсолютного пути.** Плюс: не нужна обёртка `SupportSQLiteOpenHelper`. Минус: хелпер берёт `instrumentation.targetContext` внутри себя (`MigrationTestHelper.android.kt:455,555`); чтобы подменить его, требуется глобальная правка Robolectric-окружения, влияющая и на другие тесты модуля и на загрузку схем из assets. Поверхность риска шире решаемой проблемы. Отклонено.
4. **Объявить прогон миграций только Linux CI и записать ограничение в `docs/TESTING.md`.** Плюс: ноль изменений в коде. Минус: прямо противоречит требованию аудита «проверка на Windows должна оставаться возможной»; локальная правка миграций не верифицируется до пуша — ровно та проблема, ради которой заведена F-01. Отклонено.

## Границы решения

- Обход живёт только в тестовом исходном наборе `:core:database` (`core/database/src/test/java/app/enotone/database/PathAwareSQLiteOpenHelperFactory.kt`).
- Продукционные `EnoToneDatabase.MIGRATION_*`, JSON-схемы `core/database/schemas/**` и версия БД не меняются.
- Версии `androidx.room` и `androidx.sqlite` в `gradle/libs.versions.toml` не меняются; изменение версий — отдельная задача про зависимости.
- Утверждения и состав `EnoToneMigrationTest` сохранены полностью: правка касается только инициализации правила `helper`.

## Последствия

- `:core:database:testDebugUnitTest` на Windows: **14 тестов, 0 падений** (до правки 7 падений из 14).
- `EnoToneMigrationTest`: **7/7**, `testsuite failures=0 errors=0`.
- Когда `androidx.sqlite` исправит сравнение путей, обход удаляется одним коммитом без изменения тестов: достаточно вернуть конструктор `MigrationTestHelper(instrumentation, databaseClass)`.
- Ограничение, ранее записанное в `docs/REQUIREMENTS.md` (R-8.5) как «на Windows не запускается», снимается.

## Проверка

- До исправления: `gradlew.bat :core:database:testDebugUnitTest --tests "app.enotone.database.EnoToneMigrationTest"` → `7 tests completed, 7 failed`, исключение `SupportSQLiteDriver.open`.
- После исправления: тот же прогон → `7/7`, `failures=0 errors=0`, время 3.43 s; весь модуль → `tests=14 failures=0 errors=0 skipped=0`.
- `tools/check_detekt_baseline.py main` → `429 -> 429`, роста нет.
- Linux-совместимость: обход не меняет поведение, когда имя и запрашиваемый путь совпадают (ветка `openHelperDatabaseName == fileName` выполнялась и раньше); на Linux дополнительной ветки не добавляется.
