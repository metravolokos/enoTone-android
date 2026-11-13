# TESTING — как проверяется enoTone

> Как проверяется enoTone: автоматические проверки и правила ручной проверки. **Что** проверять — каталог сценариев [SCENARIOS](SCENARIOS.md) с последними результатами; **как** вести ревью и аудит — [AUDIT_PROTOCOL](AUDIT_PROTOCOL.md). Журналы ручных проверок: [2026-11-08…11](archive/TESTING_JOURNAL_2026-11-08_11.md), [2026-11-12…13](archive/TESTING_JOURNAL_2026-11-12_13.md). Действующие решения — [CURRENT_DECISIONS](CURRENT_DECISIONS.md).

## 1. Автоматические проверки (CI, `.github/workflows/ci.yml`)

| Шаг | Что проверяет |
|---|---|
| `python3 -m unittest discover -s tools -p 'test_*.py'` | mock WebDAV (`tools/test_mock_webdav.py`) и «контрактные» тесты исходников (см. ниже) |
| Gradle Wrapper validation | `gradle-wrapper.jar` не подменён |
| `gradle :app:assembleDebug testDebugUnitTest lintDebug` | сборка, JUnit и Android Lint **во всех модулях**; после слитого #118 дополнительно Robolectric (Room DAO/миграции, Compose UI, SignOutMarker) |
| `gradle :core:ui:verifyRoborazziDebug` | сравнение эталонов Android v2 в обеих темах, diff — артефакт `roborazzi-results` |
| `gradle detekt` | статический анализ Kotlin + правила ktlint; **блокирующий** с базовой линией `detekt-baseline.xml` в каждом модуле — [ADR-0076](DECISIONS/ADR-0076-quality-tooling.md) |
| `tools/check_detekt_baseline.py <база>` | базовая линия detekt не выросла относительно базового коммита |
| `tools/check_adr_index.py` | у каждого ADR есть строка в индексе |
| `:app:compileStablePreviewKotlin` | компиляция варианта Stable Preview без секретов |
| Room schema | экспортированные схемы v1–v4 совпадают с закоммиченными |
| `tools/check_no_gms.py` | дерево зависимостей и байты APK не содержат `com.google.android.gms`, `com.google.firebase`, `com.google.android.play` |
| Stable Preview (только `main`) | подпись постоянным ключом и совпадение отпечатка сертификата |

Локально (нужны JDK 17+ и Android SDK 36 в `ANDROID_HOME`; Gradle скачивает wrapper):

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :app:assembleDebug testDebugUnitTest lintDebug detekt
```

Отчёты detekt: `<модуль>/build/reports/detekt/`.

Mock-сервер: `python3 tools/mock_webdav.py` (только HTTP на loopback, только для тестов); синтетические WAV с кириллическими NFC/NFD-именами — `python3 tools/generate_fixtures.py <каталог>`.

### Robolectric и Compose UI (#118 слит, M6-0/5a)

- JVM под Robolectric 4.17 (SDK 35), **без эмулятора**: `./gradlew :core:database:testDebugUnitTest :app:testDebugUnitTest`. Экспортированные схемы Room v1–v4 читаются из `core/database/schemas` как debug assets.
- `EnoToneMigrationTest`: последовательная v1→v2→v3→v4 и прямая v1→v4, реальные строки переживают миграцию, новые nullable-поля остаются NULL.
- `RemoteTrackDaoTest`: изоляция аккаунтов, CAS тегов, fast-scan, очередь выборочной/полной переиндексации без потери тегов, аудит с лимитом 30, удаление по sourceKey.
- `NowPlayingUiTest`, `SettingsActionsUiTest`, `HomeNavigationUiTest`, `LibraryVisibilityUiTest`, `MainActivitySettingsUiTest`: Compose-семантика и нажатия через фейковые действия, без WebDAV и реальной переиндексации.
- `LibraryPagePreferencesTest` проверяет реальную запись и восстановление SharedPreferences, `LocalizedUiResourcesTest` — разрешение RU/EN ресурсов через Android, без чтения XML строками; `MiniPlayerUiTest` — появление и открытие мини-плеера.
- `CredentialEnvelopeTest` (ACC-06): формат зашифрованного входа с программным AES-ключом вместо Keystore — повреждение, усечение, чужой ключ и версия отвергаются, IV новый при каждом шифровании.
- `SignOutMarkerTest`, `MainActivityIntentTest`: флаг незавершённого выхода и одноразовое потребление EXTRA_OPEN_NOW_PLAYING. **Реальный Android Keystore не эмулируется** (формат проверяет `CredentialEnvelopeTest`); сценарий восстановления входа с pending-флагом покрыт `AccountSettingsViewModelTest` с фейковым gateway. Проверка интента сама по себе не подтверждает показ Now Playing при восстановленной очереди.
- UI-тесты проверяют доступность элементов и действия, но **не заменяют** визуальное сравнение экрана с эталоном D2. После merge #118 зелёный CI #508 на `main`; новые тесты наследуются в 5b без изменения алгоритмов.

### Roborazzi (D2a)

Только `:core:ui` и `testImplementation`, не в APK. Robolectric 4.17, `@GraphicsMode(NATIVE)`, Roborazzi 1.76.0. `ThemeScreenshotTest`: синтетический specimen с палитрой, Onest 400/500/600, Unbounded, радиусами; два эталона: `core/ui/src/test/screenshots/theme_graphite.png`, `theme_porcelain.png`. Для начальной записи **в том же CI-окружении**: Actions → Android M0 → Run workflow на ветке D2a с `record_roborazzi=true`; скачать артефакт `roborazzi-results`, коммитить PNG; обычный PR запускает `:core:ui:verifyRoborazziDebug` и загружает diff. Пока golden не зафиксированы, проверка не считается завершённой. Ресурсы `tools:keep` проверяются на подписанном Stable Preview (`:app:assembleStablePreview` + `aapt2 dump resources` / `apkanalyzer`); не заявлять PASS без выполнения.

Старый строковой `tools/test_m6_theme_contract.py` удалён: он жёстко требовал исторические значения Graphite. Режимы темы и их сохранение проверяет `UiAppearancePersistenceTest` (Robolectric), внешний вид — `ThemeScreenshotTest`. Другие исходные/политические проверки не менялись.

### Известный долг тестов

- M6-0/5a (PR #118): из 24 строковых контрактных файлов (64 метода) удалены **6 полностью заменённых** (13 методов): `test_clean_library_ui`, `test_extended_tag_metadata`, `test_library_page_settings`, `test_screenshot_parity`, `test_tag_audit_refresh`, `test_unified_import_genre` (суффикс `_contract.py`). Остались **18 файлов (51 метод)**. Действительные поведенческие замены и непокрытые аспекты каждого файла перечислены в таблице PR #118; неполное покрытие означает сохранение файла до следующих тестов. `test_mock_webdav.py`, политика подписи и GMS-гейт остаются. Новых проверок исходного текста не добавляли.
- С #92 и #100 есть поведенческие JVM/JUnit-тесты `:app` (`LibraryViewModel`, `PlayerViewModel`, `AccountSettingsViewModel`, `AccountSignOut`) и чистого преобразования `:data:library`. В PR #118 добавлены Room DAO/миграции (`MigrationTestHelper`) и Compose UI-тесты; полный набор проверяется на обновлённом HEAD.

## 2. Ручная проверка на Huawei

Правила:
- устанавливать **подписанную** `app.enotone.android.preview` поверх предыдущей, не удалять приложение и данные;
- **не** запускать полную переиндексацию и реальный выход ради проверки — только по отдельному решению владельца;
- в Git и issue — только обезличенные числа и коды, без имён файлов, путей, ETag и учётных данных.

Базовое принятие обложек — Stable Preview #301, ответ владельца «Работает»; детальные сценарии обложек (ART-02..05) им **не** подтверждены.

Сценарии с ID и последними результатами — [SCENARIOS](SCENARIOS.md). Каждый PR перечисляет ID затронутых сценариев. После ответа владельца результат записывается в колонку «Последний результат» с номером сборки и датой.

**Моменты проверки** ([AUDIT_PROTOCOL §1](AUDIT_PROTOCOL.md#1-три-режима)):
- перед слиянием — ревью PR;
- после этапа — аудит взаимодействия подсистем;
- перед контрольной сборкой — согласованный набор сценариев на Huawei;
- после исправления — исходный сценарий и соседние.

## 3. Шаблон отчёта владельца

```
Сборка: 0.6.x-preview.<номер CI>
Сценарии: <ID из SCENARIOS, например NAV-01, REST-02>
Результат: PASS / FAIL по каждому ID (+ обезличенный код ошибки или скриншот без личных данных)
```

Общий ответ «работает» засчитывается только тем сценариям, которые были названы в запросе на проверку.
