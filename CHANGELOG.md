Warning: truncated output (original token count: 27868)
Total output lines: 584

# CHANGELOG

## Q1-NOTICE — убрать сообщение при запуске из длинного списка (Issue #208, Draft PR #210, 2026-11-18)

Удалены `QueueStartNotice`, поле `notice` у сообщения очереди, две ветки Snackbar и строки RU/EN. Запуск из контекста длиннее 100 песен по-прежнему создаёт очередь из 100 по прежним правилам, без сообщения о сокращении. Обратная связь ручного добавления, заполненной очереди и ошибок сохранена.

В `LibraryViewModelTest` удалены проверки notice и добавлена проверка запуска из контекста больше 100 песен: очередь содержит 100, сообщение не отправляется. Тесты лимита 100 и QED-вытеснения не менялись.

Обновлены ADR-0099, O-23, R-6.6c, QUE-05 и PLAY-10. Huawei PASS в этой задаче не заявляется.

## D4c — «Недавно добавленные» (Issue #201, Draft PR #205, 2026-11-18)

На «Домой» добавлены 10 альбомов и 5 песен, выбранных по `firstSeenEpochMs` из
текущего локального снимка; дата альбома — самая новая дата его песен. Пустые
секции и «Неизвестный альбом» скрываются. «Все» открывает отдельную сетку до 50
альбомов или список до 100 песен; нажатия используют очередь соответствующего
списка. Новый маршрут совместим с прежним форматом сохранённого стека. Room,
сортировки вкладок и сетевое получение списка не менялись.

Добавлены `RecentlyAddedPlannerTest`, `RecentlyAddedUiTest`, тесты маршрута и
`RecentlyAddedScreenshotTest` для двух тем. Восемь Roborazzi эталонов записаны
в CI и включены в PR; exact-HEAD CI и Roborazzi verify отслеживаются в PR #205.
Huawei-проверки HOME-REC-01…05 не заявлены как пройденные. Дизайн использует
утверждённый `09_home` и существующие элементы.


## #179 — D5: альфа статичной точки LOADING (PR #204 слит; Issue #179 ждёт device-test)

При масштабе системной анимации 0 точка LOADING остаётся статичной с alpha 0,6; при масштабе 1 пульсирует, READY остаётся непрозрачной. `NowPlayingUiTest` покрывает оба режима. В PR #204 обновлены ровно два Roborazzi-эталона точки; другие пиксели и PNG не менялись.

PR #204 слит в `main` 2026-11-18 (`d2ca15d`). CI #1065 прошёл на exact HEAD `e59123be052dd9c4aad02a2f07df0233f911d48e`. Issue #179 остаётся открытой с меткой `state:device-test`; Huawei PLAY-10 не проверен.

## F-04 — мягкий отказ при обрезанном маршруте WebDAV (PR #206 слит, Issue #202 закрыта)

Media3 мог создать период по старому timeline после удаления маршрута из активной очереди. В #206 `retainPeriod` перестал бросать исключение и учитывать отсутствующий маршрут; обёртка учитывает конкретные периоды, а `RouteLifetimeMediaSource` использует штатный `WrappingMediaSource`. Предел 100, вытеснение, сохранение очереди и UI не менялись.

`WebDavMedia3DataSourceFactoryTest.truncatedRoutePeriodIsIgnoredWithoutChangingOtherReferences` падал до исправления с `IllegalStateException` в `createPeriod` и проходит после него. CI #1043 прошёл на exact HEAD `17170365cd3ef43f7e7ab728475855603a9b9f47`; PR #206 слит в `main` 2026-11-18.

## DOC-02 — актуализация оперативной документации (PR #207, Issue #190, 2026-11-18)

- Срез и статусы синхронизированы с `main` `d2ca15d`; отражены слияния #187/#189/#194/#196/#198/#199/#206/#204, закрытие #202 и ожидание device-test по Issue #179.
- Записаны решения по #188, очереди Q1/Q2, TAG-EDIT и DEPS-01; добавлены открытые вопросы #200/#201 и правила WORKFLOW для общих журналов.
- Только Markdown; Huawei не требуется, дизайн не менялся.

## R-A1 — релизная сборка в CI (PR #198 слит, Issue #197 закрыта)

`isMinifyEnabled = true` стоит в `debug`, `release` и `stablePreview` (последний наследует `release` через `initWith`, то есть канал приёмки тоже минифицируется), поэтому R8 и до R-A1 запускался: для `debug` — задачей `:app:minifyDebugWithR8` внутри `assembleDebug`, для `stablePreview` — при каждом пуше в `main`. Новым стал не сам факт работы R8, а **блокирующая проверка варианта `release`** с `isShrinkResources = true` на каждом PR, отчёты R8 и ворота по `Missing class`. Теперь job `android` выполняет шаг `Release build (minified, unsigned)`: он падает, если APK отсутствует, если задача `:app:minifyReleaseWithR8` не выполнялась, если нет отчётов R8 (`mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt`) или если в логе встречается `Missing class`. Артефакт `enotone-release-unsigned-apk` публикуется с `if-no-files-found: error` и явной пометкой «не подписан и не является дистрибутивом»; установка и запуск на устройстве — предмет R-A3.

Проверено локально на ветке от `2a425b28`: `BUILD SUCCESSFUL` за 2 м 32 с, `app-release-unsigned.apk` — **4 915 809 байт**, все четыре отчёта R8 на месте, `Missing class` отсутствует. Повторный прогон в режиме `UP-TO-DATE` подтверждает, что проверка `Task :app:minifyReleaseWithR8` в логе не ложноположительная. CI на ветке — success. В CI APK весит **4 915 813 байт**: собирается merge-коммит PR, а `META-INF/version-control-info.textproto` вшивает ревизию; состав APK тот же (278 записей), разбор — в [RELEASE](docs/RELEASE.md).

Сжатие проверяется по содержимому, а не по названию задачи. Сжатие кода: в `configuration.txt` нет `-dontshrink`/`-dontoptimize` и есть `-allowaccessmodification` (иначе выключена оптимизация). Сжатие ресурсов надёжного следа в файлах не оставляет — `configuration.txt` и `resources.txt` побайтово совпадают при `isShrinkResources = true` и `false`, — поэтому ворота смотрят на задачу `:app:convertShrunkResourcesToBinaryRelease` в логе сборки; при выключенном флаге её нет, APK вырастает с 4 915 809 до 5 256 574 байт, и шаг падает. Негативный тест ворот выполнен на логе сборки с выключенным флагом.

Keep-правила R8 сознательно не трогались (R-A2), предупреждения `kotlin metadata` при Kotlin 2.4.21 / Gradle 8.13 отделены от `Missing class`.

## F-03 — резервное зеркало компилятора в CI (PR #196 слит, Issue #195 закрыта)

Шаг CI предзагружал `kotlin-compiler-embeddable:2.3.10`, тогда как проект собирается Kotlin 2.4.21, поэтому зеркало `ENOTONE_CI_COMPILER_MIRROR` никогда не могло быть использовано и не подстраховывало сборку при сбое Maven Central. Версия теперь читается из `gradle/libs.versions.toml`, SHA-256 берётся из таблицы пинов (для 2.4.21 — `ef19419c…f1d02`), а неизвестная версия роняет шаг с `::error::` до сетевых запросов вместо молчаливой подмены артефакта — на этом и держится инвариант, отдельный тест на текст `ci.yml` не нужен (AGENTS.md §4). Решение и отклонённые варианты — [ADR-0098](docs/DECISIONS/ADR-0098-ci-compiler-mirror-version-from-catalog.md).

## F-01 — тесты миграций Room на Windows (PR #194 слит, Issue #191 закрыта)

`EnoToneMigrationTest` падал на Windows всеми 7 тестами на первом же `helper.createDatabase(...)`: `androidx.sqlite` 2.6.2 (`SupportSQLiteDriver.open`) сверяет имя базы и запрошенный файл через `substringAfterLast('/')`, а `room-testing` передаёт абсолютный путь с разделителем `\`, поэтому обе ветки проверки ложны. На Linux путь содержит `/` и хвост совпадает — отсюда зелёный CI при красном локальном прогоне. Тестовая фабрика `PathAwareSQLiteOpenHelperFactory` сообщает драйверу ровно тот же `absolutePath`, а открытие делегирует настоящему `FrameworkSQLiteOpenHelper`; миграции, схемы и утверждения тестов не изменены. Решение и отклонённые варианты — [ADR-0097](docs/DECISIONS/ADR-0097-migration-test-helper-windows.md). Результат: `EnoToneMigrationTest` 7/7, `:core:database` 14 тестов без падений, detekt 429 → 429.

## #192 — очередь максимум 100 песен и экран очереди (Q1; PR #199 слит, 2026-11-18)

Счётчик `N / M` открывает лист с полным playback-order, переходом к выбранной песне
и выделением текущей строки `surface2`; заголовок листа показывает `N из M`.
Отложенная цель staged-перехода отменяется ручными Next/Previous/seek и обычной
сменой трека (Media3 transition reason ≠ `SEEK`). Активная очередь ограничена 100: обычный длинный контекст берёт выбранную
песню и следующих до 99, Play-all — первые 100, Shuffle выбирает 100 случайных из
всего контекста. В полной очереди для вставки удаляется старая история до текущей.
Сохранённый v1 checkpoint до 10 000 не отвергается: восстановление сокращает его к
текущей песне и до 99 следующим, пересобирает shuffle и сразу перезаписывает файл в
прежнем формате. Структура `StagedPlaylistPlan` оставлена без упрощений.

Добавлены тесты обычного и shuffle-восстановления файла из 3 647 песен, окна у начала,
середины и конца, редактора на пределе, выборки для длинного списка и шести Roborazzi
снимков (middle_long: 100 песен, текущая 50). Локальные playback/app unit-тесты и
Roborazzi verify прошли. Полный Android CI [#1033](https://github.com/metravolokos/enoTone-android/actions/runs/44203151181)
прошёл на кодовом HEAD `046cf92fe3b9c56d1dfeb4b71a4328d912849cac`; итоговый PR #199
слит 2026-11-18 (`6091ce3`). Issue #192 закрыта владельцем после общего принятия сборки
1038 — это не поимённый Huawei PASS. По решению владельца 10:30 второго повторного ревью
не было; первое повторное ревью Claude принято.

## #188 — переход восстановленного плеера (PR #189 слит, 2026-11-17)

Добавлена «Диагностика → Медиатека»: только числа групп дублей/затронутых альбомов
и числа строк/ID последнего открытого альбома, без HTTP и изменения данных.
Исправляется подтверждённый отказ перехода из плеера поверх Home: деталь сохраняет
исходную вкладку, запрос ждёт каталог, плеер закрывается после принятого перехода;
при отказе остаётся открыт с коротким сообщением. Владелец проверил облачную папку
`/AllMusic` и подтвердил, что повторные файлы есть в источнике; A не требует объединения
или автоочистки со стороны приложения. PR #189 слит 2026-11-17 (`1349657`), владелец
закрыл #188 23:04 после общего принятия сборки 1019; поимённый Huawei PASS не выводится.
Наблюдение о prefetch осталось вне изменений playback/cache.

## DOC-01 — актуализация оперативной документации (PR #187, 2026-11-17)

Срез синхронизирован с `main` `61ced66b`: Room v7 без FTS4, актуальные модули/ADR, D3.5 и D5. PR #183 (D5-C) слит в `main`; сохранены его тесты и изменения документов (включая восемь Roborazzi PNG и проверку Snackbar). Владелец 2026-11-17 общим сообщением подтвердил ранее принятые PR, ожидавшие Huawei, **без списка сценариев и замеров**; индивидуальные PASS в `docs/SCENARIOS.md` не менялись. Новые элементы после #183 проверяются отдельно. Код, ресурсы и тесты этим PR не затронуты.

## D5-C — PR #183 слит в main (Issue #177, 2026-11-17, squash `61ced66`)

- N-1: восемь PNG восстановлены из журнала ручного CI #1000 на `fe514cb86791b241372709c801d5bbf9e82d7057`, SHA-256 проверены. Просмотрены обе темы: размеры 28/321/64/84/28 dp и розовая пауза соответствуют заданию. Выявлены отличия вертикального расположения и заход фона shuffle в область скругления 56 dp; перечислены в PR, геометрия не менялась.
- После записи #1000 выявил восемь замечаний detekt: вынесены Snackbar и повторяемые проверки/фикстура в функции, названа константа ограничения обложки, исправлены импорты и перенос строки. Проверки тестов и baseline не ослаблены.

- Артист окрашен акцентом, под ним добавлена строка «Альбом · год», «⋮» открывает общий лист действий; кнопки управления круговые и выверены по размерам/глифам артборда. В «Фарфоре» пауза/играть — розовая `accentFill` с тёмным `onAccent`. Заголовка внутри экрана нет, как и на артборде; `now_playing_title` задаёт `paneTitle` для TalkBack. Решение владельца оставить `N / M` записано в `design/docs/DECISIONS_ANDROID_V2_UI.md`.
- R-1 проверяется `NowPlayingActionNavigationUiTest`: красный прогон до исправления — CI #987; тест навигации и Back прошёл на точном HEAD в Build/JUnit/Lint шаге CI #998. R-2: после красного #993 оставлен один корневой SnackbarHost над плеером; фикстура очереди теперь содержит подготовленный `QueueTrack`. В #998 прошли поведенческие R-1/R-2 и тест N-4 на нижние/боковые отступы Snackbar.
- N-3: `LibraryViewModel` остаётся созданным и в настройках, чтобы исходный маршрут медиатеки не исчезал при переходе/выходе плеера. R-3/R-5: размеры глифов, поля, обложка и кнопки рассчитаны по артборду; R-4: для «Фарфора» пауза/играть использует `accentFill`/`onAccent`.
- CI #1001 (попытка 2) на `09c0d98b7bb3eb084fd601444383f8d521fb261d` зелёный: Build/JUnit/Lint, Roborazzi verify, detekt/ktlint, baseline, ADR, Room, GMS и APK прошли. Первая попытка упала на существующем QueueLayoutPendingDestroyTest; повтор на том же SHA прошёл. Локально detekt во всех модулях и `git diff --check` прошли. Huawei PASS не заявлен.
- В PR #183 приложена таблица замеров артборда для обеих тем и перечислено отличие синтетического fixture: вместо демонстрационной картинки используется иконка-заглушка обложки, текст и счётчик идут из тестовых данных. Других найденных расхождений вне списка исправлений нет.

## D5/D3.5 — PR #176 слит (2026-11-16), хвосты ревью

После commit Back плеера больше не запускает cancel-settle: прогресс и сохранённое
preview-смещение сбрасываются по завершению exit шторки. Добавлен Compose UI тест
повторного открытия по реальным пикселям; RED до исправления — `pull_request` CI #918.
LOADING-точка проверяется по кадрам при обычном и нулевом масштабе анимации;
восстановлена проверка подписи seek, удалены два устаревших PNG, добавлен комментарий
к компенсации бегунка. Робоэталоны не перезаписывались; Huawe…17868 tokens truncated…от play/pause, read-only cache при воспроизведении, LRU 512 МиБ.
- Один файл до 256 МиБ при резерве 128 МиБ; ETag-versioned opaque cache keys, локальный прогресс в KiB, очистка на закрытии службы/новом запуске.
- Stream fallback при проблемах кэша; предзагрузка следующего и бесшовный switch уже открытого соединения — не здесь.
- JVM тесты границ/ключей, Python service contract, ADR-0036. Реальное поведение на Huawei ещё не проверено.

- M5-A startup performance fix (Draft): фоновой полнофайловый CacheWriter больше не запускается до первого Media3 READY; отложен на 900 мс и отменяется при BUFFERING/смене трека. Пауза не блокирует фоновой download. Регрессия Huawei #96 ~6 с подключения + 1 с буферизации, новая версия требует проверки на устройстве.

- M5-A A/B startup diagnostic: после неудачной попытки ускорить Preview #102 добавлены контроль cache-off по умолчанию (без SimpleCache), эксперимент cache-on и пять числовых длительностей до первого звука. Перенесено повторное чтение Room/валидация 3590 путей с каждого нажатия на открытие медиатеки. Пока не испытано на Huawei.

- M5-A startup investigation (#111, Huawei): на 3590 записях ~3,3 с синхронного Media3 setup и лишь ~0,44–0,59 с до READY; cache-off и cache-on почти одинаковы. Эксперимент: стартовать Media3 с выбранного ±2, после READY добавлять все остальные треки порциями по 32 без изменения итоговой последовательности. Добавлены цифровые фазы синхронного setup и прогресс постановки очереди, синтетические JVM тесты на 3590 элементов. До CI/Huawei — Draft.

### M5-B single next preload (Draft)
- После 100% текущего файла один serial Media3 CacheWriter скачивает следующий MediaItem согласно native Media3 order (shuffle/repeat, staged timeline); Pause не отменяет загрузку.
- Статусы current/next независимы, Stop и выбор другого трека отменяют оба worker, лимиты диска и строгий WebDAV GMS-free источник сохранены.
- JVM выбор следующего по Media3 ID (3590 синтетических записей), security contract, ADR-0037. До CI/Huawei — Draft.

### M6-A handoff and clean UI with default Material colors (Draft)
- Private anonymous measurement of track changes (button/auto/MediaSession), with numeric time-to-READY and time-to-isPlaying plus pre-cached-next boolean; no identifiers, telemetry or logs.
- Folded Room/Media3 debug traces behind optional diagnostics; default cache-on; added two current/next progress bars.
- After Huawei Preview #129 passed functional smoke, owner rejected carrying the old project's palette into Android: restored unmodified standard MaterialTheme colors; custom palette is deferred pending explicit Android design approval.
- Kotlin tracker tests, private service/static contract, default-theme contract, ADR-0038. Huawei #129 functions confirmed; exact-HEAD CI after palette revert pending.

### docs: сверка оригинального ТЗ и актуальной дорожной карты (2026-11-09)
- Обновлён ROADMAP по подтверждённым Huawei/CI этапам M0–M6-A, явно отделены принятые срезы от неполных требований M1/M2/M3/M4/M5 и будущих M7–M9.
- Исправлены устаревшие вопросы об ожидании приёмки M0, зафиксировано ожидание аппаратной проверки seek в отдельном Draft PR #30 (CI #138 зелёный). Никаких изменений runtime, APK или сохранённой Room.

### M6-B current-track seek (Draft)
- Доступный Material3 Slider для перемотки внутри текущего Media3 трека, позиция и длительность с обновлением раз в 500 мс.
- Сервис принимает только ограниченную цель seek при известной длительности и доступной Media3 seek command; единственный seek отправляется после отпускания ползунка. Existing GMS-free HTTPS Range + temporary cache и быстрый staged-плейлист не изменены.
- Pure Kotlin policy/JVM tests, Python security/UI contract, ADR-0039; Huawei smoke pending. Остаются стандартные цвета Material Design, прежние палитры не импортируются.

- fix(m6-b): после отрицательного Huawei Preview #138 кнопки Next/Previous возобновляют Media3 воспроизведение, даже если предыдущий был на Pause/ENDED; при неожиданном ENDED добавлено одно отложенное guarded auto-next без нарушения явного Pause, аудиофокуса или Repeat One. Новые unit/static контракты. Проверка на Huawei после нового CI **ожидается**.

- fix(m6-b): исправлена ложная надпись «Пауза» при Media3 BUFFERING/ENDED: now UI status derived from player.playbackState/playerError. Сохраняется стандартная палитра Material Design и безличная диагностика.

- fix(m6-b): Media3 DataSource разрешает точный EOF (`position == remoteSize`), без HTTP-запроса, и возвращает `RESULT_END_OF_INPUT` при чтении; запрос за пределами источника выдаёт стандартную ошибку IO 2008. Сохранены строгие WebDAV Range/ETag проверки. Добавлены `PlaybackReadWindowTest` и whitelist-only диагностические этапы HTTP/Range/read без приватных сообщений. Ошибка 2000 Huawei #147 требует повторной проверки — не считать исправленной до аппаратного smoke (ADR-0040).

## M6-B — принята на Huawei (2026-11-09)
- Перемотка и воспроизведение на границах треков подтверждены в Preview #157; работают обычные/shuffle автопереходы, Next после Pause, ручной Next в shuffle и repeat-one.
- CI #157 зелёный на точном HEAD `7c058e5e257982a38d021aff76e639f927f7f2d6`, PR #30 объединён (`6d14ee81d3da50c611f854ddaf140930dfb745d3`). Не заявлены неподтверждённые zero-GET, gapless или окончательная первопричина прежнего IO 2000.

### opt-in stable Preview update channel (Draft)
- Separate same-package `app.enotone.android.preview` signed build for in-place upgrades preserving Room; only main branch with explicit secret-backed PKCS12, fingerprint pin and opt-in flag. Default isolated per-run debug APK unchanged; playback/Room/network code unchanged. First stable installation starts with a new private Room; previous `.preview.rN` data cannot be transferred. CI and Huawei update test required.

## Hardware verification — stable Preview #167 (2026-11-09)
- Owner installed first signed stable Preview from successful CI #167; Huawei startup and preservation of imported `/AllMusic` Room list after app restart confirmed. **No update-in-place acceptance yet.** Historical 30 tags from isolated preview packages not assumed present. Documentation-only follow-up triggers a subsequent main-signed build for overwrite test; runtime and Room schema unchanged.

## Huawei acceptance — stable Preview update #169 (2026-11-09)
- Signed #169 installed over #167 without uninstalling; existing Room music list remained accessible and playback works after re-entering password. Persistent update channel accepted on Huawei. Encrypted credential saving is a separate opt-in Draft change.

### M0-K optional credential vault (Draft)
- AES-256-GCM key generated in AndroidKeyStore; only encrypted email/password stored in non-backed-up private SharedPreferences after explicit button press, with restore on next activity launch and explicit erase. Failure falls back to manual sign-in; Room, GMS-free WebDAV and Media3 unchanged.

## Huawei smoke — opt-in secure sign-in #172 (2026-11-09)
- Owner confirmed that saving and restoring sign-in works on signed Stable Preview #172, and the existing local library and playback remain accessible. **Credential deletion smoke remains open**; full feature acceptance is pending this separate check. No additional code changes.

## Accepted on Huawei — M0-K secure credentials (2026-11-09)
- Owner confirmed both opt-in saved-login restore and explicit removal after restart in Stable Preview #172, with Room music list preserved and no new import. Safe sign-in lifecycle accepted; no runtime changes in this docs PR.

## Huawei acceptance — M2-A and first stable 10-file tag pilot (2026-11-09)
- On-device #177: user confirmed both group tabs populated after bounded 10/10 MP3/FLAC scans (12 HTTP GET, 786432 bytes, zero errors); tracks play/skip in grouped context. Full library indexing not performed.

### M1 bounded manual resume (Draft)
- New explicit Resume button for up to 100 next unprocessed candidates, still using existing per-run ceilings of 60 GET, 4 MiB, ~180 seconds, and unchanged per-file Room checkpoint. No background scan or bulk import, and existing 10/20 pilot behavior unchanged. Huawei smoke pending.

## Huawei M1 bounded resume #179 and M2 Album Artist precedence (2026-11-09)
- Owner reported 52/52 saved tags, 60 GET / 3932160 bytes, zero errors, stop NETWORK_BUDGET; 62/3590 indexed total. Existing checkpoints continue to survive upgrades; multi-run stop/resume acceptance pending.
- Correct artist navigation to prioritize Album Artist over per-track Artist; retain both independent fields, accept common FLAC Vorbis Album Artist tag aliases. Existing Room schema and WebDAV/Media3 unchanged; on-device grouping smoke still pending.

### M1 managed indexing (Issue #40, Draft 2026-11-09)
- Foreground-only opt-in managed tag indexing over four capped existing pilot passes, up to 240 GET / 16 MiB per session (each <=60 GET / 4 MiB), Wi‑Fi by default, explicit cellular consent, pause/resume/stop, onStop cancellation and Room checkpoint reuse. No change to playback/Room schema/Keystore/parser; Huawei acceptance pending.
- Huawei accepted #181 Album Artist navigation and #179 bounded 52/52 tag indexing (62 total); no automatic 3590-file scan.

## Draft — M1 Background indexing (2026-11-09, ADR-0047)
- Индексация по нажатию теперь запускается отдельной службой `dataSync` и продолжается при закрытом экране, если ОС не остановила службу. Постоянное уведомление с действиями Пауза/Стоп.
- Снят Wi-Fi-only фильтр и удалён чекбокс разрешения мобильной сети. Любой доступный тип сети; сетевые ошибки не обходят бюджет.
- Конкретные лимиты 60 GET/4 МиБ на порцию и 240 GET/16 МиБ на сессию, SQL checkpoint по каждому файлу, отсутствие повторного PROPFIND, приоритет Album Artist и безопасное хранение credentials **без изменений**.
- Статус Draft: не объявлять аппаратную проверку Huawei завершённой до exact-HEAD CI и Stable Preview.


## Draft — M1 Parallel metadata reads & bounded retries (ADR-0048, 2026-11-09)
- 4 параллельных MP3/FLAC файла в управляемой фоновой индексации, до 1 последовательного HTTP Range внутри каждого файла; общий атомарный budget ≤60 GET/4 МиБ за порцию.
- При единичном RETRY_NETWORK/RETRY_HTTP — до 2 повторов с паузой, не останавливать всю сессию; при каскадных сбоях/429 и 401/403 — защищённая остановка.
- Отображать безопасную классификацию последней временной ошибки; сохранять Room checkpoint, существующий Album Artist, GMS-free и отсутствие Wi-Fi-ограничений.
- Не изменять исторические пилоты 10/20/100. До Huawei и зелёного CI Draft не считается принятым.


## Draft — M1 serial Room collector for parallel readers (2026-11-09)
- После Huawei #202 INDEX_BATCH/STATE: чтение MP3/FLAC остаётся четырёхпоточным, но Room CAS, счётчики и публикация прогресса теперь сериализованы отдельным consumer через Channel(4). Убран полный запрос списка ожидающих файлов после каждого ответа, сохранён запрос сверки в конце порции.
- Внутренние исключения получают фиксированный безопасный этап (DB_SELECT / DB_WRITE / PROGRESS / DB_FINAL / POLICY) без PII. Нет изменений Room schema, packageId/подписи, логики воспроизведения, Wi-Fi policy и глобального лимита GET/байт.
- До точного зелёного CI и аппаратного теста — Draft; причина предыдущего STATE не установлена абсолютно.


## Draft — M1 Continuous background indexing (2026-11-09, ADR-0049)
- Вслед за успешным Huawei #205 (207/207 файлов, 240 GET, 0 ошибок, 3274 осталось) добавлена отдельная явная кнопка «Индексировать все оставшиеся MP3/FLAC».
- Повторные ограниченные порции до 80 × (60 GET/4 МиБ), не более 4800 GET/320 МиБ, 4 часа wall-clock; сохранение Room после каждого файла, один последовательный collector при 4 параллельных сетевых запросах.
- Безопасный backoff 30/60 сек при каскадной временной ошибке; остановка после третьей волны, при отказе авторизации, времени, квоте или решении пользователя. Без новой Room schema, PROPFIND, смены идентификатора приложения, Wi-Fi gate или GMS.
- Пока Draft до exact-HEAD CI и аппаратного теста продолжения после четвёртой порции.


## Draft — M1 metadata audit, reindex & incremental catalogue refresh (2026-11-10)
- После успешного Huawei #211: очередь 0, 3590/3590 сохранённых тегов, один альбом NULL и 143 Album Artist NULL. Остальные форматы отложены по решению владельца (реальные MP3/FLAC).
- Добавлены локальный просмотр и поиск записей с пустыми album/Album Artist, выбор файла и постановка его в очередь повторной индексации; передача имён файлов/метаданных на сервер и в GitHub исключена.
- Повторное чтение всех MP3/FLAC возможно после отдельного подтверждения. `RETRY_REFRESH` не удаляет старые теги, а сетевые/неподдерживаемые ошибки при повторе оставляют последнюю успешно сохранённую метаинформацию.
- Операция обновления одноуровневой медиатеки теперь явно названа «Обновить каталог /AllMusic»: один Depth:1 PROPFIND, неизменённые файлы сохраняют идентичность/теги, новые/изменённые индексируются существующей фоновой службой. Не меняем Room schema, GMS, Wi-Fi, 4 воркера, Media3, сертификат или packageId.
- Draft до exact-HEAD CI и теста на устройстве.

## Draft — M2 separate Artists / Album Artists + Unknown Album (2026-11-10)
- Split performer views: Artists uses only ARTIST, Album Artists uses only ALBUM ARTIST. Existing album identity retains Album Artist preference, with ARTIST fallback when missing.
- Added localizable virtual Unknown Album for scanned tracks whose ALBUM is absent, with a stable key distinct from an embedded album literally named Unknown Album. Unscanned tracks remain under Tracks.
- Four page IDs and a safe visibility policy prepare future Settings hide/show without changing Room schema, WebDAV background indexer, Media3, signed package identity or standard Material colors. New UI settings toggles deferred.

## Draft — M2 library page visibility settings (2026-11-10)
- Добавлено диалоговое окно настроек видимости четырёх страниц медиатеки; переключатели независимы, выбранные значения сохраняются локально после перезапуска.
- Последнюю видимую страницу нельзя скрыть; если текущая скрыта, навигация переключается на доступную без остановки музыки. Идентификаторы страниц стабильны и не зависят от локализации.
- Используется стандартный Material 3; Room, 3590 тегов, WebDAV, GMS, Media3, авторизация и подпись не меняются. Доступно для CI и Huawei smoke после слияния.

## Draft — M2 music-first UI cleanup (2026-11-10)
- Library opens automatically after restoring an explicitly saved login, instead of starting on an oversized diagnostic page. First-time entry retains both fields throughout typing; save/forget remain opt-in.
- Compact Home: playback/library shortcut, optional connection and technical tests, catalogue refresh, managed indexing with collapsed details. Network tests and Room probes are still available on demand.
- Library: smaller header, scrollable track/album list remains the central element, compact mini player below it. Full transport, seek and extra options moved into a scrollable dialog; telemetry only inside Diagnostics toggle. Standard Material colors, stable package ID and signing retained; no migration, reindex or Media3 changes.
