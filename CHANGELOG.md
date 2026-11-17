Warning: truncated output (original token count: 24559)
Total output lines: 503

# CHANGELOG

## #188 — переход восстановленного плеера (2026-11-17)

Добавлена «Диагностика → Медиатека»: только числа групп дублей/затронутых альбомов
и числа строк/ID последнего открытого альбома, без HTTP и изменения данных.
Исправляется подтверждённый отказ перехода из плеера поверх Home: деталь сохраняет
исходную вкладку, запрос ждёт каталог, плеер закрывается после принятого перехода;
при отказе остаётся открыт с коротким сообщением. Владелец проверил облачную папку
`/AllMusic` и подтвердил, что повторные файлы есть в источнике; A не требует объединения
или автоочистки со стороны приложения. Huawei-проверка перехода и exact-HEAD CI —
в [Draft PR #189](https://github.com/metravolokos/enoTone-android/pull/189). Наблюдение о
prefetch осталось вне изменений playback/cache.

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
к компенсации бегунка. Робоэталоны не перезаписывались; Huawei NAV-15…17 и PLAY-10
ожидают владельца.

## F-02 — каноническое LF и `.gitattributes` (2026-11-17)

Локальный аудит ([AUDIT_2026-11-17](docs/audit/AUDIT_2026-11-17_local-state.md),
находка F-02) показал: на Windows `design/tools/sync_android.py --check` падал
на 32 неизменённых дизайн-ресурсах. Причина — `core.autocrlf=true` при
отсутствии `.gitattributes`: файлы выгружались в рабочую копию с CRLF, а
проверка ADR-0090 сравнивает байты и SHA-256 манифеста.

Добавлен [`.gitattributes`](.gitattributes): каноническое содержимое в Git и
рабочая копия — LF, `*.bat`/`*.cmd`/`*.ps1` — CRLF, бинарные форматы — макрос
`binary`. Побайтная сверка дизайн-ресурсов **не ослаблена**: устранена причина
расхождения, а не симптом; `sync_android.py` не менялся.

Индекс изменился только для `gradlew.bat` (хранился с CRLF); остальные 827
файлов уже были с LF, история не перезаписана. Открытые на момент работы ветка
R-A7 и PR #180 не затрагивались; PR #180 влит в `main` отдельно (`797b68b0`).
Проверено локально: `sync_android.py --check` → «совпадают 59 файлов»,
Python-тесты `tools/` — 78 OK, `gradlew.bat --version` → Gradle 8.13 на JVM 17.

## D3.5 — PR #169 слит 2026-11-16; доработка по ревью

Решение Issue #168: детали, вкладки, Настройки, Диагностика используют **один**
fade-through (выход 90 мс, вход 160 мс с задержкой 90 мс, масштаб 0,96→1),
без бокового смещения и просвечивания другой страницы. Теперь существует
единственная `appPageTransition`, `AppMotionRoute` различает только
`STAY/CHANGE`. При неизменном маршруте анимация не запускается.

Мини-плеер и нижняя панель **постоянно смонтированы и неподвижны**
под «Сейчас играет», без AnimatedVisibility/fade и второй копии.
Полный плеер — одна вертикальная шторка без перелёта обложки; сохранён
локальный drag, predictive Back PLAYER без двойного смещения после commit
и с возвратом при cancel. Удалены predictive-слой страниц, прогресс
уходящих маршрутов, таймеры сброса страниц и внешнюю передачу прогресса свайпа.
Приоритет PLAYER→OVERLAY→DETAIL→SEARCH→TAB→EXIT, Album Artist,
TalkBack и системный reduced motion остаются.

**RED до исправлений:** тестовый коммит `974a74b` и автоматический
`pull_request` CI #892 (192 теста, два ожидаемых поведенческих падения,
не компиляция/тайм-аут). Новые проверки: `AppMotionTest` JVM,
`AppMotionFrameUiTest` на настоящих контейнерах с цветными фикстурами
и Bitmap View по кадрам, `AppDockOpaqueUiTest`,
`AppPlayerSheetBackUiTest` и `AppNowPlayingGestureUiTest`.
Roborazzi-эталоны не перезаписывались, D5-B (#167) не переносился.
CI #915 прошёл на кодовом HEAD `b6f3679`; PR #169 слит в `main` (`9a0a4b5`),
ревью завершено. Аппаратная оценка плавности Huawei ожидает владельца.



## D5 — follow-up по дорожке (Issue #173, PR #174 слит 2026-11-16)

По скриншотам владельца бегунок смещён вниз на 2 dp, координаты буферного слоя рассчитаны на всю ширину дорожки. Записаны эталоны частичного, полного, LOADING- и READY-буфера в Graphite и Porcelain; отдельные снимки подтверждают заполнение дорожки до правого края. Huawei PLAY-10 принимает владелец; PASS не заявляется.

## D5 B — PR #167 (слит 2026-11-16)

Экран «Сейчас играет»: буферный диапазон и состояние следующего трека, accessibility RU/EN и reduced motion; JVM/Compose-проверки и шесть Roborazzi-снимков. Эталоны записаны вручную в #877 и подтверждены Roborazzi CI #884 на исходном HEAD. PR слит в `main` коммитом `6a627c3`; Huawei PLAY-10 принимает владелец, PASS не заявляется.

## D3.4 — PR #155 (2026-11-15)

После независимого ревью исправлены маршрут группы «Авторы альбомов» и Back к списку, безопасный снимок для Home/overlay, доступность настроек под полным плеером и быстрые последовательные predictive Back без потери прогресса уходящей страницы. Регрессии проверяются новыми JVM и Compose UI тестами; аппаратные NAV-15…17 остаются за владельцем.

Анимации Android v2 по `design/docs/MOTION_ANDROID_V2.md`: fade through вкладок, сдвиг страниц и Настроек, predictive back Android 14+, `SharedTransitionLayout` с перелётом обложки мини-/полного плеера, движение нижней панели и жест закрытия за пальцем (25% высоты или высокая скорость). Поведение покрывается `AppMotionTest`, `AppBackControllerTest`, `AppMotionRootUiTest` и `AppLibraryRouteTransitionUiTest`; централизован Back, маршрутные снимки изолированы, скрыта семантика медиатеки под плеером; системное отключение анимаций оставлено Compose. Макеты, ресурсы и действия песни не меняются. Аппаратные NAV-15…17 проверяет владелец; без Huawei PASS.

## D4b — Draft PR #149 (2026-11-15)

Новые строки «Артисты» и «Авторы альбомов» с аватарами и подсчётами на готовом индексе; первая группа сборников определяется только по явной метке Album Artist (VA/Various Artists/«Разные исполнители»/«Сборник»). Экран альбома: 240 dp обложка, Onest 600/23, данные год/жанр/песни и длительность только из всех известных тегов, m:ss у треков. Поведенческие JUnit добавлены. Существующая строковая проверка размера обложки обновлена с 232 на утверждённые 240 dp: это не ослабление проверки, а изменение макета D4b. Шесть эталонов Roborazzi в обеих темах записаны из синтетических снимков CI, устранены замечания detekt и конфликт с main, исправлены подписи авторов и строки альбома; по замечанию ревью #149 исключены ложные сборники из одноимённых сольных релизов, добавлен Onest SemiBold для строк артистов/авторов; итоговый CI точного HEAD, ревью Claude и Huawei ещё ожидают. «Недавно добавленные» — D4c, не входит в #147.

## M6-0/8b — действия очереди (2026-11-15)

«Играть следующей» / «В конец очереди» в листах песни, альбома и исполнителя; Snackbar RU/EN, индекс групп и планы исполнителей вне UI, поиск сохраняется при возврате из детали, отдельный результат FAILED. Huawei QED-01…06 ожидает.

## D4a — Draft PR #133 (2026-11-14, не слит)

Лист действий песни по `03_song_actions` (⋮ и долгое нажатие, TalkBack, тактильный отклик), переходы к альбому и исполнителю через общий стек вкладки. Экран исполнителя по `08_artist`: буквенный аватар 132 dp, подпись, лента альбомов с годами, все песни в порядке альбомов/дисков и сворачиваемая полоска. Чистые планировщики с JUnit; `AppNavigator` — ограниченный стек, сохранение и чтение старого формата. `LibraryViewModel.onOpenRequest` удалён как неиспользуемый, его тест заменён тестом действующего выбора вкладок. Без очереди M6-0/8, Room и новых зависимостей. **CI, Roborazzi и Huawei на текущем HEAD ждут проверки.** Ревью Claude и слияние — только по команде владельца.


> Версии — по [ADR-0078](docs/DECISIONS/ADR-0078-versioning.md): новое пишется в `Unreleased`; при приёмке на Huawei секция получает номер принятой сборки.

## Unreleased

- R-A7: корректное чтение тегов и явные ошибки парсера (PR #180 слит). Русские теги в CP1251 больше не превращаются в «кракозябры», в том числе когда рядом стоят типографские кавычки, тире, украинские буквы и латинские слова; латиница с диакритикой при этом не перечитывается как CP1251. ID3v2.4 с tag-level unsync читается полностью: кадры обходятся по сохранённым размерам, де-усинхронизируется только тело кадра, поэтому второй и последующие кадры не теряются. FLAC после ID3-преамбулы (тег впереди файла) находится по размеру ID3-блока, а не по первому вхождению `fLaC`. Для CBR MP3 без Xing/Info длительность выводится только при подтверждении несколькими последовательными кадрами с одинаковым битрейтом; VBR без Xing/Info честно показывает «неизвестно» вместо выдуманного числа. Вместо «тихого» пустого результата отказ разметки теперь даёт постоянную ошибку `FAILED_LAYOUT`, а неподдерживаемое расширение остаётся завершённой проверкой без тегов. Решение владельца по R-2 (вариант 1) — ADR-0029. На устройстве не проверено.

- GOV-REVIEW #156 (инструмент): `review_helper.py check` теперь распознаёт краткую повторную проверку (два SHA, изменение кода/триггеры, таблица статусов, итог), сохраняет полноценную проверку новых замечаний и вердиктов; «замечаний нет» учитывается только в разделе замечаний. Добавлены 22 Python unittest, без изменений Android и без Huawei.

- D5 / Issue #158, часть A (PR #161 слит): в `PlaybackSnapshot` добавлены позиция буфера и состояние следующего трека. Чистая JVM-политика учитывает фактический следующий индекс Media3, repeat-one, завершённость текущего кэша и очереди; FAILED остаётся LOADING-стилем, SKIPPED/IDLE — NONE. Буфер использует существующий цикл опроса; проверка видимости «Сейчас игр…14559 tokens truncated…от play/pause, read-only cache при воспроизведении, LRU 512 МиБ.
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
