# TESTING — как проверяется enoTone

> Как проверяется enoTone: автоматические проверки и правила ручной проверки. **Что** проверять — каталог сценариев [SCENARIOS](SCENARIOS.md) с последними результатами; **как** вести ревью и аудит — [AUDIT_PROTOCOL](AUDIT_PROTOCOL.md). Журналы ручных проверок: [2026-11-08…11](archive/TESTING_JOURNAL_2026-11-08_11.md), [2026-11-12…13](archive/TESTING_JOURNAL_2026-11-12_13.md). Действующие решения — [CURRENT_DECISIONS](CURRENT_DECISIONS.md).

## 1. Автоматические проверки (CI, `.github/workflows/ci.yml`; `main` `2a425b28`, 2026-11-17)

| Шаг | Что проверяет |
|---|---|
| `python3 -m unittest discover -s tools -p 'test_*.py'` | mock WebDAV, оставшиеся исторические контракты исходников и проверки tooling; поведение «Сейчас играет» — Robolectric Compose UI и Roborazzi |
| Gradle Wrapper validation | `gradle-wrapper.jar` не подменён |
| `gradle :app:assembleDebug testDebugUnitTest lintDebug` | сборка, JUnit и Android Lint **во всех модулях**; после слитого #118 дополнительно Robolectric (Room DAO/миграции, Compose UI, SignOutMarker) |
| `gradle :app:assembleRelease` (R-A1) | минифицированный release-APK: задача `:app:minifyReleaseWithR8` действительно выполнена, отчёты R8 (`mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt`) на месте, `Missing class` в логе отсутствует. Сжатие кода проверяется по `configuration.txt`: нет `-dontshrink`/`-dontoptimize`, есть `-allowaccessmodification`. Сжатие ресурсов проверяется **только** по задаче `:app:convertShrunkResourcesToBinaryRelease` в логе: при `isShrinkResources = false` `configuration.txt` и `resources.txt` побайтово совпадают с включённым случаем, APK вырастает с 4 915 809 до 5 256 574 байт, а задачи в графе нет — ворота шага это ловят. Артефакт `enotone-release-unsigned-apk` **не подписан**. В CI собирается merge-коммит PR, и его SHA вшивается в `META-INF/version-control-info.textproto`, поэтому размер APK в CI отличается от локального на несколько байт — это норма, а не расхождение сборок. Установки и запуска APK здесь нет — это R-A3 |
| `gradle :core:ui:verifyRoborazziDebug :app:verifyRoborazziDebug` | сравнение эталонов Android v2 в обеих темах (компоненты и экраны), diff — артефакт `roborazzi-results` |
| `gradle detekt` | статический анализ Kotlin + правила ktlint; **блокирующий** с базовой линией `detekt-baseline.xml` в каждом модуле — [ADR-0076](DECISIONS/ADR-0076-quality-tooling.md) |
| `tools/check_detekt_baseline.py <база>` | базовая линия detekt не выросла относительно базового коммита |
| `tools/check_adr_index.py` | у каждого ADR есть строка в индексе |
| `:app:compileStablePreviewKotlin` | компиляция варианта Stable Preview без секретов |
| Room schema | экспортированные схемы **v1–v7** сверяются с закоммиченными JSON; в v7 нет FTS4, модель каталога/плейлистов сохранена |
| `tools/check_no_gms.py` | дерево зависимостей и байты APK не содержат `com.google.android.gms`, `com.google.firebase`, `com.google.android.play` |
| Stable Preview (только `main`) | подпись постоянным ключом и совпадение отпечатка сертификата |

Локально (нужны JDK 17+ и Android SDK 36 в `ANDROID_HOME`; Gradle скачивает wrapper):

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
- `EnoToneMigrationTest` начался с проверок миграций через v5 (длительность и модель плейлистов); актуальная схема — **Room v7** после удаления FTS4 (#143). CI сверяет экспортированные v1–v7. **На Windows локальный запуск 7 тестов миграций проходит** после тест-локального обхода дефекта `androidx.sqlite` ([ADR-0097](DECISIONS/ADR-0097-migration-test-helper-windows.md), находка F-01 [аудита](audit/AUDIT_2026-11-17_local-state.md)): `SupportSQLiteDriver` сравнивает пути через `substringAfterLast('/')` и на Windows отвергает абсолютный путь, поэтому тест передаёт `MigrationTestHelper` фабрику `PathAwareSQLiteOpenHelperFactory`, сообщающую драйверу тот же абсолютный путь. Локальный прогон в любом случае не подтверждает поведение v6→v7 на Huawei — это отдельное доказательство.
- `RemoteTrackDaoTest`: изоляция аккаунтов, CAS тегов, fast-scan, очередь выборочной/полной переиндексации без потери тегов, аудит с лимитом 30, удаление по sourceKey.
- M6-0/6: `PlaylistRepositoryTest` проверяет конкурирующий get-or-create «Избранного», отсутствие дублей, порядок и `present`, FK-каскады и изоляцию очистки аккаунта; парсер длительности тестирует синтетические FLAC/MP3-заголовки без дополнительных Range-запросов.
- `NowPlayingUiTest`, `SettingsActionsUiTest`, `HomeNavigationUiTest`, `LibraryVisibilityUiTest`, `MainActivitySettingsUiTest`: Compose-семантика и нажатия через фейковые действия, без WebDAV и реальной переиндексации.
- `LibraryPagePreferencesTest` проверяет реальную запись и восстановление SharedPreferences, `LocalizedUiResourcesTest` — разрешение RU/EN ресурсов через Android, без чтения XML строками; `MiniPlayerUiTest` — появление и открытие мини-плеера.
- `CredentialEnvelopeTest` (ACC-06): формат зашифрованного входа с программным AES-ключом вместо Keystore — повреждение, усечение, чужой ключ и версия отвергаются, IV новый при каждом шифровании.
- `SignOutMarkerTest`, `MainActivityIntentTest`: флаг незавершённого выхода и одноразовое потребление EXTRA_OPEN_NOW_PLAYING. **Реальный Android Keystore не эмулируется** (формат проверяет `CredentialEnvelopeTest`); сценарий восстановления входа с pending-флагом покрыт `AccountSettingsViewModelTest` с фейковым gateway. Проверка интента сама по себе не подтверждает показ Now Playing при восстановленной очереди.
- UI-тесты проверяют доступность элементов и действия, но **не заменяют** визуальное сравнение экрана с эталоном D2. После merge #118 зелёный CI #508 на `main`; новые тесты наследуются в 5b без изменения алгоритмов.

### Roborazzi (исторический этап D2a и текущие экраны Android v2)

Только `:core:ui` и `testImplementation`, не в APK. Robolectric 4.17, `@GraphicsMode(NATIVE)`, Roborazzi 1.76.0. `ThemeScreenshotTest`: синтетический specimen с палитрой, Onest 400/500/600, Unbounded, радиусами; два эталона: `core/ui/src/test/screenshots/theme_graphite.png`, `theme_porcelain.png`. Для начальной записи **в том же CI-окружении**: Actions → Android M0 → Run workflow на ветке D2a с `record_roborazzi=true`; скачать артефакт `roborazzi-results` (или восстановить PNG из строк `ROBORAZZI_PNG <путь> <sha256>` и `ROBORAZZI_B64 <путь> <часть base64>` журнала этого запуска), коммитить PNG; обычный PR запускает `:core:ui:verifyRoborazziDebug` и загружает diff. Пока golden не зафиксированы, проверка не считается завершённой. Ресурсы `tools:keep` проверяются на подписанном Stable Preview (`:app:assembleStablePreview` + `aapt2 dump resources` / `apkanalyzer`); не заявлять PASS без выполнения.

Начиная с D3.1 в `:app` действует `AppScreensScreenshotTest`: экраны «Песни» (верх и прокрутка), «Альбомы», «Домой», «Сейчас играет» на эталонном размере 377 × 816 dp, `ru`, обе темы — `app/src/test/screenshots/*.png`. Их сверяют глазами с PNG-макетами `design/assets/ui-screens/android-v2/` перед коммитом эталона; запись — тем же `record_roborazzi=true`.

D2b добавляет `ComponentsScreenshotTest` (две галереи компонентов в обеих темах), `EnoLetterAvatarTest` (Unicode/grapheme/детерминированность), dual-Preview у каждого компонента. PNG-эталоны D2b: `core/ui/src/test/screenshots/components_graphite.png`, `components_porcelain.png`, записываются тем же `record_roborazzi=true` на stacked-ветке после D2a.

Строковые `tools/test_m6_theme_contract.py` и `tools/test_now_playing_contract.py` удалены: первый зависел от устаревшей темы, второй — от наличия вызовов и строк в исходниках. Теперь поведение плеера покрывают `NowPlayingUiTest` и `NowPlayingActionNavigationUiTest`, визуал — восемь снимков `AppScreensScreenshotTest` (`now_playing_*`) в обеих темах. Режимы темы/сохранение проверяет `UiAppearancePersistenceTest`, базовый внешний вид — `ThemeScreenshotTest`.

### Текущий контур motion/Now Playing (D3.5, D5)

После merge #169/#176 страницы используют единый fade-through, полный плеер — вертикальную шторку. JVM/Compose UI тесты `AppMotionTest`, `AppMotionFrameUiTest`, `AppPlayerSheetBackUiTest`, `AppNowPlayingGestureUiTest`, `AppDockOpaqueUiTest` проверяют переходы и отдельные кадры на Robolectric; Roborazzi проверяет зафиксированные PNG для Android v2 и состояний буфера. Это **не** системный predictive Back, FPS, TalkBack или время отклика на физическом Huawei. Владелец 2026-11-17 общим сообщением подтвердил принятые ранее PR (включая NAV-15…17 и PLAY-10), но **отдельных замеров и поимённых PASS нет**: итог сценариев — только [SCENARIOS](SCENARIOS.md).

Резервное зеркало компилятора: с [ADR-0098](DECISIONS/ADR-0098-ci-compiler-mirror-version-from-catalog.md) шаг CI читает версию `kotlin` из `gradle/libs.versions.toml` и берёт SHA-256 из таблицы пинов, поэтому зеркало обслуживает тот компилятор, который запрашивает сборка (2.4.21, пин `ef19419c…f1d02`). Неизвестная версия — падение шага с `::error::` до любых сетевых запросов, а не молчаливая подмена. Проверяется поведением, а не отдельным файлом-контрактом (AGENTS.md §4): подъём Kotlin без пина красит CI на точном HEAD, поэтому дополнительных тестов на текст `ci.yml` нет. Хеш остаётся ручным: Maven Central не публикует `.jar.sha256` для этого артефакта.

### Известный долг тестов

- M6-0/5a (PR #118): из 24 строковых контрактных файлов (64 метода) удалены **6 полностью заменённых** (13 методов): `test_clean_library_ui`, `test_extended_tag_metadata`, `test_library_page_settings`, `test_screenshot_parity`, `test_tag_audit_refresh`, `test_unified_import_genre` (суффикс `_contract.py`). По состоянию после #183 осталось **16 файлов (46 методов)**. `tools/test_now_playing_contract.py` удалён, его поведение покрывают Compose UI/Roborazzi. Прежние замены и непокрытые аспекты перечислены в PR #118; неполное покрытие сохраняется до новых поведенческих тестов. `test_mock_webdav.py`, политика подписи и GMS-гейт остаются; новых строковых контрактов не добавляли.
- С #92 и #100 есть поведенческие JVM/JUnit-тесты `:app` (`LibraryViewModel`, `PlayerViewModel`, `AccountSettingsViewModel`, `AccountSignOut`) и чистого преобразования `:data:library`. В PR #118 добавлены Room DAO/миграции (`MigrationTestHelper`) и Compose UI-тесты; полный набор проверяется на обновлённом HEAD.

### Проверки новых элементов D5-C (#183)

После слияния #183 (squash `61ced66`) поведение общего листа и переходы тестируются `NowPlayingActionNavigationUiTest`, сообщения очереди — `AppQueueSnackbarLayoutUiTest`, визуал — восемью PNG в двух темах. CI #1001 (попытка 2) подтвердил тесты для кодового HEAD #183; это не отчёт об аппаратной приёмке. На Huawei после merge требуется проверить ACT-01/03, сообщения очереди над плеером и доком, вход в «Настройки» без мигания, новые DES-01/O-08 в обеих темах и положение нижнего ряда; вертикальная раскладка принята владельцем как есть.

## 2. Ручная проверка на Huawei

Правила:
- устанавливать **подписанную** `app.enotone.android.preview` поверх предыдущей, не удалять приложение и данные;
- **не** запускать полную переиндексацию и реальный выход ради проверки — только по отдельному решению владельца;
- в Git и issue — только обезличенные числа и коды, без имён файлов, путей, ETag и учётных данных.

Базовое принятие обложек — Stable Preview #301, ответ владельца «Работает»; детальные сценарии обложек (ART-02..05) им **не** подтверждены.

2026-11-17 владелец **общим сообщением** подтвердил принятые на тот момент PR, прежде ожидавшие проверки на Huawei (включая NAV-15…17, PLAY-10, SRCH-05/07, миграцию Room и выход). Перечня отдельных сценариев, версии сборки и замеров (в том числе ≤20 мс для SRCH-07) в сообщении не было; в `SCENARIOS.md` поимённые PASS не добавлялись.

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
