# TESTING — как проверяется enoTone

> Как проверяется enoTone: автоматические проверки и правила ручной проверки. **Что** проверять — каталог сценариев [SCENARIOS](SCENARIOS.md) с последними результатами; **как** вести ревью и аудит — [AUDIT_PROTOCOL](AUDIT_PROTOCOL.md). Журналы ручных проверок: [2026-11-08…11](archive/TESTING_JOURNAL_2026-11-08_11.md), [2026-11-12…13](archive/TESTING_JOURNAL_2026-11-12_13.md). Действующие решения — [CURRENT_DECISIONS](CURRENT_DECISIONS.md).

## 1. Автоматические проверки (CI, `.github/workflows/ci.yml`; срез 2026-11-23, `main` `59dd26a4c3cc9e0bd27c8c0c8470361fca4a92a9`, версия `0.6.2`)

Тулчейн после DEPS-01 шага 3 ([ADR-0100](DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md)): **Gradle 9.9.1** (дистрибутив и SHA-256 проверяются в CI и в `distributionSha256Sum`), **AGP 9.5.1** со встроенным Kotlin 2.4.21 (плагин `org.jetbrains.kotlin.android` не применяется), **compileSdk 37** (`platforms;android-37.0`, `build-tools;36.0.0`). `targetSdk 36` и `minSdk 26` не менялись. DEPS-01 обновляет только Compose BOM `2026.06.00` → `2026.09.00`: Foundation/UI `1.11.3` → `1.12.1`, Material3 остаётся `1.4.0` ([официальная карта](https://developer.android.com/develop/ui/compose/bom/bom-mapping)). KSP 2.3.13, Room 2.8.5, Roborazzi 1.79.0 и detekt 1.23.8 остаются прежними.

| Шаг | Что проверяет |
|---|---|
| `python3 -m unittest discover -s tools -p 'test_*.py'` | mock WebDAV, оставшиеся исторические контракты исходников и проверки tooling; поведение «Сейчас играет» — Robolectric Compose UI и Roborazzi |
| Gradle Wrapper validation | `gradle-wrapper.jar` не подменён |
| `gradle :app:assembleDebug testDebugUnitTest lintDebug` | сборка, JUnit и Android Lint **во всех модулях**; после слитого #118 дополнительно Robolectric (Room DAO/миграции, Compose UI, SignOutMarker) |
| `gradle :app:assembleRelease` (R-A1/R-A2) | минифицированный release-APK: задача `:app:minifyReleaseWithR8` действительно выполнена, отчёты R8 (`mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt`) на месте, `Missing class` в логе отсутствует. Сжатие кода проверяется по `configuration.txt`: нет `-dontshrink`/`-dontoptimize`, есть `-allowaccessmodification`; configuration.txt показывает фактически применённые правила; R-A2 оставляет атрибуты Retrace без enum field-keep правил, поскольку persisted значения читаются из неизменённых DEX-строк `Enum.name()`. Сжатие ресурсов проверяется **только** по задаче `:app:convertShrunkResourcesToBinaryRelease` в логе: при `isShrinkResources = false` `configuration.txt` и `resources.txt` побайтово совпадают с включённым случаем, APK заметно вырастает (замер `2a425b28`: 4 915 809 → 5 256 574 байт; после ADR-0100 базовая точка — 4 776 635 байт на AGP 9.5.1), а задачи в графе нет — ворота шага это ловят. Оба grep'а не зависят от повторности: с `--console=plain` Gradle печатает строку каждой задачи графа, включая `UP-TO-DATE`/`FROM-CACHE`/`SKIPPED`. Артефакт `enotone-release-unsigned-apk` **не подписан** и выгружается и у упавшей сборки (`if: ${{ !cancelled() }}`, `if-no-files-found: warn`); при `Missing class` AGP роняет R8 **до** записи `mapping.txt`, поэтому в артефакт входит `missing_rules.txt` — исходные данные для keep-правил R-A2. В CI собирается merge-коммит PR, и его SHA вшивается в `META-INF/version-control-info.textproto`, поэтому размер APK в CI отличается от локального — это норма, а не расхождение сборок. Установки и запуска APK здесь нет — это R-A3 |
| `gradle :core:ui:verifyRoborazziDebug :app:verifyRoborazziDebug` | сравнение эталонов Android v2 в обеих темах (компоненты и экраны), diff — артефакт `roborazzi-results` |
| `gradle detekt` | статический анализ Kotlin + правила ktlint; **блокирующий** с базовой линией `detekt-baseline.xml` в каждом модуле — [ADR-0076](DECISIONS/ADR-0076-quality-tooling.md) |
| `tools/check_detekt_baseline.py <база>` | базовая линия detekt не выросла относительно базового коммита |
| `tools/check_adr_index.py` | у каждого ADR есть строка в индексе |
| `:app:compileStablePreviewKotlin` | компиляция варианта Stable Preview без секретов |
| Room schema | экспортированные схемы **v1–v7** сверяются с закоммиченными JSON; в v7 нет FTS4, модель каталога/плейлистов сохранена |
| `tools/check_no_gms.py` | дерево зависимостей и байты APK не содержат `com.google.android.gms`, `com.google.firebase`, `com.google.android.play` |
| Stable Preview (только `main`) | подпись постоянным ключом, совпадение отпечатка сертификата и выгрузка `app/build/outputs/mapping/stablePreview/mapping.txt` артефактом `enotone-stable-preview-mapping` на 90 дней для retrace |

Локально (нужны JDK 17+ и Android SDK **37** — `platforms;android-37.0`, `build-tools;36.0.0` — в `ANDROID_HOME`; Gradle скачивает wrapper):

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 design/tools/sync_android.py --check
./gradlew :app:assembleDebug testDebugUnitTest lintDebug detekt
```

Отчёты detekt: `<модуль>/build/reports/detekt/`.

### Окончания строк (`.gitattributes`)

Каноническое содержимое в Git — LF, рабочая копия на любой ОС — тоже LF;
исключения: `*.bat`/`*.cmd`/`*.ps1` — CRLF, бинарные форматы — `binary`.
Без этого `core.autocrlf=true` (значение по умолчанию в Git for Windows)
выгружает файлы с CRLF, и побайтная сверка `sync_android.py --check`
(ADR-0090) падает на неизменённых ресурсах — так было до 2026-11-17
(находка F-02 в [аудите](audit/AUDIT_2026-11-17_local-state.md)).

Если после клонирования с изменёнными локальными настройками Git
`sync_android.py --check` сообщает «не совпадает с манифестом» по файлу,
содержимое которого не правили, рабочую копию пересоздают:

```bash
git rm -r --cached -q . && git reset --hard HEAD
```

Проверка дизайн-ресурсов при этом остаётся побайтной: ослаблять её нельзя,
устраняется только зависимость результата от локальных настроек Git.

Команда снимает всё с индекса, поэтому допустима **только при чистом
`git status`**: `reset --hard` вернёт файлы, а незакоммиченное — потеряется.

Mock-сервер: `python3 tools/mock_webdav.py` (только HTTP на loopback, только для тестов); синтетические WAV с кириллическими NFC/NFD-именами — `python3 tools/generate_fixtures.py <каталог>`.

### Robolectric и Compose UI (начиная с #118, дополнены в D3.5/D5)

- JVM под Robolectric 4.17 (SDK 35), **без эмулятора**: `./gradlew :core:database:testDebugUnitTest :data:playlist:testDebugUnitTest :app:testDebugUnitTest`. Экспортированные схемы Room v1–v5 читаются из `core/database/schemas` как debug assets.
- `EnoToneMigrationTest` начался с проверок миграций через v5 (длительность и модель плейлистов); актуальная схема — **Room v7** после удаления FTS4 (#143). CI сверяет экспортированные v1–v7. **На Windows локальный запуск 7 тестов миграций проходит** после тест-локального обхода дефекта `androidx.sqlite` ([ADR-0097](DECISIONS/ADR-0097-migration-test-helper-windows.md), находка F-01 [аудита](audit/AUDIT_2026-11-17_local-state.md)): `SupportSQLiteDriver` сравнивает пути через `substringAfterLast('/')` и на Windows отвергает абсолютный путь, поэтому тест передаёт `MigrationTestHelper` фабрику `PathAwareSQLiteOpenHelperFactory`, сообщающую драйверу тот же абсолютный путь. Локальный прогон в любом случае не подтверждает поведение v6→v7 на устройстве — это отдельное доказательство.
- `RemoteTrackDaoTest`: изоляция аккаунтов, CAS тегов, fast-scan, очередь выборочной/полной переиндексации без потери тегов, аудит с лимитом 30, удаление по sourceKey.
- M6-0/6: `PlaylistRepositoryTest` проверяет конкурирующий get-or-create «Избранного», отсутствие дублей, порядок и `present`, FK-каскады и изоляцию очистки аккаунта; парсер длительности тестирует синтетические FLAC/MP3-заголовки без дополнительных Range-запросов.
- `NowPlayingUiTest`, `SettingsActionsUiTest`, `HomeNavigationUiTest`, `LibraryVisibilityUiTest`, `MainActivitySettingsUiTest`: Compose-семантика и нажатия через фейковые действия, без WebDAV и реальной переиндексации.
- `LibraryPagePreferencesTest` проверяет реальную запись и восстановление SharedPreferences, `LocalizedUiResourcesTest` — разрешение RU/EN ресурсов через Android, без чтения XML строками; `MiniPlayerUiTest` — появление и открытие мини-плеера.
- `CredentialEnvelopeTest` (ACC-06): формат зашифрованного входа с программным AES-ключом вместо Keystore — повреждение, усечение, чужой ключ и версия отвергаются, IV новый при каждом шифровании.
- `SignOutMarkerTest`, `MainActivityIntentTest`: флаг незавершённого выхода и одноразовое потребление EXTRA_OPEN_NOW_PLAYING. **Реальный Android Keystore не эмулируется** (формат проверяет `CredentialEnvelopeTest`); сценарий восстановления входа с pending-флагом покрыт `AccountSettingsViewModelTest` с фейковым gateway. Проверка интента сама по себе не подтверждает показ Now Playing при восстановленной очереди.
- UI-тесты проверяют доступность элементов и действия, но **не заменяют** визуальное сравнение экрана с эталоном D2. После merge #118 зелёный CI #508 на `main`; новые тесты наследуются в 5b без изменения алгоритмов.

- TEST-01 (#213): `QueueLayoutPendingDestroyTest` синхронизируется не по времени, а по состоянию — ждёт терминации `io` исполнителя записи `EnoTonePlaybackService` через шов `awaitQueueWritesFinished(timeoutMs)` (`@VisibleForTesting`; публичный, потому что `internal` из `:playback` не виден тесту в `:app`). Шов только ждёт: он не вызывает `io.shutdown()` (остановка — в `onDestroy`) и требует `check(destroyed)`, поэтому вызов до `onDestroy` падает, а не выключает сохранение очереди молча. Ожидание по `lastModified`/`Thread.sleep` убрано: метка времени на части ФС секундная, а пара layout+position лежит в двух файлах, поэтому чтение между двумя записями давало `state == null`. Устойчивость доказана 200 прогонами в одной JVM без падений; временный повторный прогон в ветку не входит. Строковых контрактов на текст исходников не добавлялось.

### Roborazzi (исторический этап D2a и текущие экраны Android v2)

Только `:core:ui` и `testImplementation`, не в APK. Robolectric 4.17, `@GraphicsMode(NATIVE)`, Roborazzi 1.79.0. `ThemeScreenshotTest`: синтетический specimen с палитрой, Onest 400/500/600, Unbounded, радиусами; два эталона: `core/ui/src/test/screenshots/theme_graphite.png`, `theme_porcelain.png`. Для начальной записи **в том же CI-окружении**: Actions → Android M0 → Run workflow на ветке D2a с `record_roborazzi=true`; скачать артефакт `roborazzi-results` (или восстановить PNG из строк `ROBORAZZI_PNG <путь> <sha256>` и `ROBORAZZI_B64 <путь> <часть base64>` журнала этого запуска), коммитить PNG; обычный PR запускает `:core:ui:verifyRoborazziDebug` и загружает diff. Пока golden не зафиксированы, проверка не считается завершённой. Ресурсы `tools:keep` проверяются на подписанном Stable Preview (`:app:assembleStablePreview` + `aapt2 dump resources` / `apkanalyzer`); не заявлять PASS без выполнения.

HTTP-тесты в `:core:network` и `:data:library` используют MockWebServer 5.5.1 (`com.squareup.okhttp3:mockwebserver3`, пакет `mockwebserver3`) только через `testImplementation`. Транзитивный модуль `okhttp` исключён и явно выбран `okhttp-jvm:5.5.1` через алиас `libs.okhttp.jvm` (N-1 из ревью #214): Android-вариант `okhttp-android` этой версии требует API 37, а JVM-вариант не тянет Android-зависимостей. После [ADR-0100](DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md) compileSdk уже 37, но выбор JVM-варианта остаётся правильным для JVM unit-тестов и в APK не попадает.

Начиная с D3.1 в `:app` действует `AppScreensScreenshotTest`: экраны «Песни» (верх и прокрутка), «Альбомы», «Домой», «Сейчас играет» на эталонном размере 377 × 816 dp, `ru`, обе темы — `app/src/test/screenshots/*.png`. Их сверяют глазами с PNG-макетами `design/assets/ui-screens/android-v2/` перед коммитом эталона; запись — тем же `record_roborazzi=true`.

D6-F (#228) добавляет `PlaybackErrorSheetUiTest`: лист из AppMotionRoot поверх
«Сейчас играет» в Graphite/Porcelain (`playback_error_graphite.png`,
`playback_error_porcelain.png`), а также поведенческие проверки одного показа на
вход в ERROR, dismiss/retry и текста по группам ошибок. `QueueEditServiceTest`
проверяет повтор того же mediaId и позиции без изменения очереди; `PlayerViewModelTest`
проверяет передачу команды через `PlaybackConnection`. Числовые коды проверяются
в диагностике, а UI мини-плеера и «Сейчас играет» не выводит их.

D6-A (#225) добавляет `LibraryEmptyStatesUiTest`: пустой поиск на каждой вкладке, возврат списка для непустого поиска, действие перехода в «Настройки» и правило скрытия мини-плеера для пустой вкладки медиатеки. Четыре Roborazzi-снимка `d6a_not_found_*` и `d6a_music_missing_*` сверяются с артбордами 12/13 в обеих темах. Эти экраны не показывают `ModalBottomSheet`.

D4c (#201) добавляет `RecentlyAddedPlannerTest`, `RecentlyAddedUiTest` и
`RecentlyAddedScreenshotTest`: планирование датированных строк, скрытие пустых
секций, навигация и очереди из 5/100 песен. Восемь снимков на макете 09_home
покрывают обе темы: `home_recent_both_*`, `home_recent_empty_*`,
`home_recent_all_albums_*` и `home_recent_all_songs_*`. Эталоны записаны через
CI workflow dispatch с `record_roborazzi=true` и включены в PR #205; exact-HEAD
CI и проверка на устройстве отслеживаются отдельно.

Q3 (#302) добавляет `QueueShuffleOrderTest` (полная уникальная перестановка, якорь выбранного/текущего индекса, seed), закрепляет границу 10 000/10 001 литералами в `PlaybackQueueTest`, `PlaybackQueueSnapshotTest`, `WebDavPlaylistRoutesTest`, `PersistedQueueStateTest` и `QueueEditPlannerTest`, проверяет сохранение контекста 3647 в `LibraryViewModelTest`, обычную и shuffle-очередь 3647 в `PersistedQueueStateTest` и обычную очередь 3647 через `PlayerViewModelTest`. `ErrorRecoveringPlayerTest` проверяет внешние MediaSession Next/Previous. `QueueEditServiceTest` проверяет оба пути «текущая первая», staged Next/Previous и seek до полного timeline, полное paused-наполнение без дублей, вытеснение истории с checkpoint на очереди 10 000 и main-thread бюджет <2 с (локально 332 мс). Существующие Roborazzi-эталоны очереди остаются визуальными снимками и не задают лимит очереди.

D2b добавляет `ComponentsScreenshotTest` (две галереи компонентов в обеих темах), `EnoLetterAvatarTest` (Unicode/grapheme/детерминированность), dual-Preview у каждого компонента. PNG-эталоны D2b: `core/ui/src/test/screenshots/components_graphite.png`, `components_porcelain.png`, записываются тем же `record_roborazzi=true` на stacked-ветке после D2a.

Строковые `tools/test_m6_theme_contract.py` и `tools/test_now_playing_contract.py` удалены: первый зависел от устаревшей темы, второй — от наличия вызовов и строк в исходниках. Теперь поведение плеера покрывают `NowPlayingUiTest` и `NowPlayingActionNavigationUiTest`, визуал — восемь снимков `AppScreensScreenshotTest` (`now_playing_*`) в обеих темах. Режимы темы/сохранение проверяет `UiAppearancePersistenceTest`, базовый внешний вид — `ThemeScreenshotTest`.

### Текущий контур motion/Now Playing (D3.5, D5)

После merge #169/#176 страницы используют единый fade-through, полный плеер — вертикальную шторку. JVM/Compose UI тесты `AppMotionTest`, `AppMotionFrameUiTest`, `AppPlayerSheetBackUiTest`, `AppNowPlayingGestureUiTest`, `AppDockOpaqueUiTest` проверяют переходы и отдельные кадры на Robolectric; Roborazzi проверяет зафиксированные PNG для Android v2 и состояний буфера. Это **не** системный predictive Back, FPS, TalkBack или время отклика на физическом устройстве. Владелец 2026-11-17 общим сообщением подтвердил принятые ранее PR (включая NAV-15…17 и PLAY-10), но **отдельных замеров и поимённых PASS нет**: итог сценариев — только [SCENARIOS](SCENARIOS.md).

Резервное зеркало компилятора: с [ADR-0098](DECISIONS/ADR-0098-ci-compiler-mirror-version-from-catalog.md) шаг CI читает версию `kotlin` из `gradle/libs.versions.toml` и берёт SHA-256 из таблицы пинов, поэтому зеркало обслуживает тот компилятор, который запрашивает сборка (2.4.21, пин `ef19419c…f1d02`). Неизвестная версия — падение шага с `::error::` до любых сетевых запросов, а не молчаливая подмена. Проверяется поведением, а не отдельным файлом-контрактом (AGENTS.md §4): подъём Kotlin без пина красит CI на точном HEAD, поэтому дополнительных тестов на текст `ci.yml` нет. Хеш остаётся ручным: Maven Central не публикует `.jar.sha256` для этого артефакта.

### Известный долг тестов

- M6-0/5a (PR #118): из 24 строковых контрактных файлов (64 метода) удалены **6 полностью заменённых** (13 методов): `test_clean_library_ui`, `test_extended_tag_metadata`, `test_library_page_settings`, `test_screenshot_parity`, `test_tag_audit_refresh`, `test_unified_import_genre` (суффикс `_contract.py`). По состоянию после #183 осталось **16 файлов (46 методов)**. `tools/test_now_playing_contract.py` удалён, его поведение покрывают Compose UI/Roborazzi. Прежние замены и непокрытые аспекты перечислены в PR #118; неполное покрытие сохраняется до новых поведенческих тестов. `test_mock_webdav.py`, политика подписи и GMS-гейт остаются; новых строковых контрактов не добавляли.
- С #92 и #100 есть поведенческие JVM/JUnit-тесты `:app` (`LibraryViewModel`, `PlayerViewModel`, `AccountSettingsViewModel`, `AccountSignOut`) и чистого преобразования `:data:library`. В PR #118 добавлены Room DAO/миграции (`MigrationTestHelper`) и Compose UI-тесты; полный набор проверяется на обновлённом HEAD.

### Проверки новых элементов D5-C (#183)

После слияния #183 (squash `61ced66`) поведение общего листа и переходы тестируются `NowPlayingActionNavigationUiTest`, сообщения очереди — `AppQueueSnackbarLayoutUiTest`, визуал — восемью PNG в двух темах. CI #1001 (попытка 2) подтвердил тесты для кодового HEAD #183; это не отчёт об аппаратной приёмке. На устройстве после merge требуется проверить ACT-01/03, сообщения очереди над плеером и доком, вход в «Настройки» без мигания, новые DES-01/O-08 в обеих темах и положение нижнего ряда; вертикальная раскладка принята владельцем как есть.

## 2. Приёмка и проверка на устройстве

**Приёмка выполняется автоматическими проверками** ([ADR-0104](DECISIONS/ADR-0104-auto-acceptance.md)).
Сценарий считается принятым, когда PR слит в `main`, CI на точном HEAD зелёный
(сборка, JUnit, lint, Room schema, GMS scan, detekt, Roborazzi) и независимое ревью
пройдено. Отдельной стадии «проверено на телефоне» нет, и её результат в
[SCENARIOS](SCENARIOS.md) не записывается.

**Проверка на устройстве остаётся инструментом диагностики.** Когда телефон
подключён по ADB, исполнитель может поставить подписанную Stable Preview и
разобрать конкретный дефект:

- устанавливать **подписанную** `app.enotone.android.preview` поверх предыдущей,
  не удалять приложение и данные; `.preview.rN` для этого не используется;
- **не** запускать полную переиндексацию и реальный выход ради проверки — только
  по отдельному решению владельца;
- снимать `logcat` и скриншоты для разбора; в Git и Issue — только обезличенные
  числа и коды ошибок, без имён файлов, путей, ETag и учётных данных.

**Как сообщать о найденном дефекте.** Владелец сообщает о глюке — заводится Issue
с меткой `bug`, ссылками на затронутые сценарии и обычным приоритетом. Исправление
идёт общим порядком: ветка, тесты, CI точного HEAD, ревью, merge. Это не отменяет
приёмку уже слитых сценариев и не требует пересмотра прежних результатов.

Текущая аппаратная база: смартфон без сервисов Google (Android 12), установлена
`app.enotone.android.preview` версии `0.6.2-preview.1161` (2026-11-20). Базовая
приёмка прошла на Stable Preview #301: владелец сообщил «Работает». Это принятие
базовой работоспособности, а не поимённый PASS отдельных сценариев; после
ADR-0104 такие сообщения больше не являются стадией приёмки.

Сценарии с ID — [SCENARIOS](SCENARIOS.md). Каждый PR перечисляет ID затронутых
сценариев; после merge их результат становится «принято по автоматическим
проверкам» с указанием PR и прогона CI.

**Моменты проверки** ([AUDIT_PROTOCOL §1](AUDIT_PROTOCOL.md#1-три-режима)):
- перед слиянием — ревью PR;
- после этапа — аудит взаимодействия подсистем;
- после исправления дефекта — исходный сценарий и соседние.
