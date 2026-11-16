Warning: truncated output (original token count: 21123)
Total output lines: 408

# CHANGELOG

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

- GOV-REVIEW #156 (инструмент): `review_helper.py check` теперь распознаёт краткую повторную проверку (два SHA, изменение кода/триггеры, таблица статусов, итог), сохраняет полноценную проверку новых замечаний и вердиктов; «замечаний нет» учитывается только в разделе замечаний. Добавлены 22 Python unittest, без изменений Android и без Huawei.

- D5 / Issue #158, часть A (PR #161 слит): в `PlaybackSnapshot` добавлены позиция буфера и состояние следующего трека. Чистая JVM-политика учитывает фактический следующий индекс Media3, repeat-one, завершённость текущего кэша и очереди; FAILED остаётся LOADING-стилем, SKIPPED/IDLE — NONE. Буфер использует существующий цикл опроса; проверка видимости «Сейчас играет» уточнена в части B. Политика кэша и очередь не менялись. Сценарий — PLAY-10 (PLAY-09 занят #160).
- D5 / Issue #158, часть B (Draft PR #167, ветка `feature/d5-now-playing-buffer-b`): слой буфера, точка следующего трека, доступность RU/EN и reduced motion; sampling ограничен видимым/возобновлённым экраном; добавлены JVM/Compose проверки и шесть сценариев Roborazzi. Ручная запись эталонов и CI точного HEAD ещё ожидают; Huawei проверяет владелец.
- Воспроизведение (Issue #159, ADR-0094): старт перестал зависеть от временного кэша.
  `TransientMediaCache` в конструкторе удалял каталог до 512 МиБ и открывал `SimpleCache`
  с SQLite — на главном потоке, из `playQueue`/`restoreQueue`: фриз и риск ANR при холодном
  старте. Теперь кэш готовится в фоне при `onCreate` службы, а сбой кэша больше не лишает
  музыки: `createTransientCacheSafely` возвращает код причины (`TransientCacheFailure`), и очередь
  играет через сеть. Код причины сохраняется в `PreviewAudioPlayback` и пока не выводится в
  «Диагностику» — это отдельная задача.
  Закрытие службы: флаг `destroyed` и `submitIo` с перехватом `RejectedExecutionException` —
  устранён краш отложенной записи позиции после `io.shutdown()`. При незавершённой
  записи структуры отдельная запись позиции не ставится: она уже входит в пару
  layout+position, а лишняя запись прежней ревизии ломала пару и стирала только что
  включённую очередь. Режим кэша фиксируется один раз при сборке очереди
  (`CacheModeDecision`): очередь без кэша не включает предзагрузку, даже если кэш
  появится позже. Новый сценарий `PLAY-09`, тесты `TransientCacheFactoryTest`,
  `CacheModeDecisionTest`, `CacheModeServiceTest`, на Huawei не проверено.

- BUG-action-sheet-insets (#152): лист действий на Huawei открывался в отдельном окне Material3 — статусные значки исчезали, снизу появлялась полоса неверного цвета. Для диалога настроены edge-to-edge, цвета и контраст системных панелей по теме; контент отступает от панели жестов, фон листа тянется до нижнего края, затемнение 40 %. Выбор действия анимированно закрывает лист; штатные свайп и Back сохранены. `EnoActionSheetWindowTest` (обе темы, закрытие, свайп, Back, двойной тап). Повторные нажатия во время анимации закрытия не дублируют действия очереди (ревью #154). Huawei ACT-04 ещё не проверено.
- Воспроизведение (Issue #148, ADR-0093): после ошибки нажатие Play переходит к следующему треку вместо
  тупика. `onPlayerError` оставлял плеер в `STATE_IDLE` с сохранённым `playerError`, а `play()` только
  выставляет `playWhenReady` — музыка не возвращалась до повторного выбора трека. `MediaSession` получал
  сырой `ExoPlayer`, поэтому Play в уведомлении, на гарнитуре и в Bluetooth шёл мимо `resume()`;
  теперь сессии передаётся `ErrorRecoveringPlayer` (`ForwardingPlayer`, перехватывает только `play()`).
  Поведение по решению владельца: следующий трек; при `repeat-one` и на последнем треке — текущий
  с начала. Политика `PlaybackErrorRecoveryPolicy` проверена 6 JUnit-тестами. Новый сценарий `PLAY-08`,
  на Huawei не проверено.

- **BUG поиска — полный отказ от FTS4 (ADR-0091, #143, 2026-11-15):** после диагностики Huawei 677 (SQLite 3.32.2, 3647 песен, индекс 0; «маль» 19 совпадений за 110–130 мс в памяти) по решению владельца удалены FTS4 entity/DAO/index, обновления при импорте/индексации, `SearchProbe` и кнопка диагностики. Нормализованные ключи песен и групп создаются при загрузке, слова запроса нормализуются один раз, поиск остаётся подстрочным по всем пяти тегам, русская сортировка и ё/е сохранены. Room v7 с ручной миграцией 6→7 без удаления медиатеки, плейлистов или очереди; тесты миграции 6→7 и цепочки 1→7. `LibrarySearchTrace` оставлен для Huawei-замера `ms≤20`; CI точного HEAD и Huawei ещё ожидают.

- ADR-0090: шаг CI «Design resources match design/» (`design/tools/sync_android.py --check`) — ресурсы `ic_eno_*`, `bandit_*`, палитра, wordmark и launcher только из `design/`; шаблон issue «Нужен элемент дизайна» и метка `design-needed`, раздел «Дизайн» в шаблоне PR. Значки `ic_eno_play_next` / `ic_eno_queue_add` (временные из #134) заменены собранными `build_ui_icons.py` по формам с холста; UI Icons v1 — 24 значка.
- Дизайн в репозитории приложения: содержимое `enotone-design` (`a2afd4c`) перенесено в `design/` без истории Git — генераторы, эталонные PNG, исходники холста Android v2 (`design/canvas/`), журнал решений `design/docs/DECISIONS_ANDROID_V2_UI.md` и спецификация буферизации (раньше — документы проекта claude.ai). `tools/convert_bandit.py` → `design/tools/`; новый `design/tools/sync_android.py` переносит ресурсы в `:core:ui`/`:app` и пишет манифест `design/android-resources.sha256`. Правило `design-needed` в AGENTS. В ресурсах изменились только шапки-комментарии (путь генератора). Проверка в CI, ADR и значки `play_next`/`queue_add` из генератора — следующим PR.
- Инфраструктура (#144, ADR-0092): атомарная замена файлов состояния — `File.renameTo` отказывает
  при существующей цели на Windows, поэтому второе сохранение чекпоинта очереди, манифеста выхода
  и обложки не выполнялось; замена вынесена в `AtomicFileReplace` (`Files.move` с `REPLACE_EXISTING`,
  откат без `ATOMIC_MOVE` на ФС без атомарного переименования), `ArtworkPreviewRepository.atomicWrite`
  возвращает фактический результат вместо молчаливого отказа. Три теста, падавших на Windows
  (`QueueStateFileStoreTest` ×2, `QueueEditServiceTest`), проходят. Room, поведение плеера, дизайн
  и сетевой слой не менялись.
- CI (#144): шаг «Verify Room v1 schema matches committed JSON» проверял схемы v1–v4 и v6, пропуская
  5.json — v5 добавлена с проверкой `durationMs`/`firstSeenEpochMs` и таблиц `playlists`/`playlist_items`.
- Документы (#144): STATUS, ROADMAP, ARCHITECTURE, KNOWN_ISSUES, REQUIREMENTS и CURRENT_DECISIONS
  сверены с `main` `2761ae9` — D4a #133, M6-0/8a #134 и фикс повтора #132 числились как Draft.

- **Срочное исправление поиска по песням (Huawei, 2026-11-14, сборка 675):** запрос MATCH FTS4 на устройстве не завершался и держал базу — поиск показывал старый список, а следующее чтение медиатеки зависало («Читаем локальную медиатеку»). Песни ищутся в памяти, как альбомы и исполнители (`OfflineTrackCatalog.search`); FTS убран с пути интерфейса до разбора, индекс по-прежнему ведётся. «Проверить поиск» больше не выполняет MATCH — только план запроса (EXPLAIN QUERY PLAN), версию SQLite и поиск в памяти.
- Диагностика поиска по песням (BUG track search, 2026-11-14): в «Настройки → Диагностика» — обезличенный след последнего поиска (длина запроса, ответ FTS, показано/всего, применён ли ответ, время, класс ошибки) и кнопка «Проверить поиск» (версия SQLite, строки индекса, сравнение FTS с JOIN, FTS без JOIN и поиска в памяти на коротких пробах). Поведение поиска не меняется; исправление — после замера на Huawei.
- M6-0/8a (ADR-0089, Draft): чистый планировщик «Играть следующей» / «В конец очереди» с переносом дубликатов и стабильным shuffle; изменение Media3 без `setMediaSources`, обновление маршрутов WebDAV, предзагрузки и двухфайловой ревизии очереди; `LibraryViewModel` отдаёт события для Snackbar. JVM/Robolectric; UI и Huawei — после D4a в 8b. Служебные значки playlist_play/playlist_add; предложено добавить их в `enotone-design/tools/build_ui_icons.py`. CI ожидает.

- M6-0/7 (#128, ADR-0087): локальный FTS4-поиск префиксов и нескольких слов, нормализация ё/е (й сохраняется), сортировка ru Collator и игнорирование The у артистов, debounce 150 мс; Room v6 без потери данных. Paging только измерен, решение владельца ожидается; CI #602 зелёный до синхронизации с D3.3; Huawei и итоговый CI ожидают.
- Bugfix #132 (PLAY-02/PLAY-07): в режиме повтора одного трека при неожиданном `Media3 STATE_ENDED` заново запускается тот же item без изменения индекса очереди; для остальных режимов сохранён переход Next. Регрессия подтверждена падающим тестом на исходном коде в CI #604; добавлены JVM-тесты фейкового Media3-плеера, передачи repeat/shuffle в `SharedPlayerUiState` и Robolectric-тест реальных нажатий. Huawei ожидает проверки; дизайн, Room и формат очереди не менялись.
- D3.3 (ADR-0088, решение владельца после проверки D3.2): переключатель «Прозрачные панели» оставлен и теперь управляет и верхними панелями — закреплённым заголовком вкладок и полоской прокрутки альбома. Выключен — заголовок при прокрутке сплошной с линией снизу, включён — 84 % с размытием. Подпись настройки обновлена.
- D3.2 (ADR-0088, решения владельца по сборке D3.1): на вкладках «Песни», «Альбомы», «Артисты», «Авторы» заголовок (название, поиск, счётчик, на «Песнях» — кнопки) закреплён, список идёт под ним — с размытием и линией при прокрутке; полоска варианта B осталась только у альбома. Поиск: «Найдено N песен / альбомов / артистов / авторов», «Ничего не нашлось» (вместо «Найдено групп»). Новая настройка «Оформление → Прозрачные панели» (по умолчанию выкл.): мини-плеер и нижняя панель на 72 % с размытием 24 dp; отдельный файл настроек, `UiAppearance` не тронут. «Сейчас играет»: ручки нет — закрытие только свайпом вниз; обводка обложки (белая/чёрная 35 %) и крупная тень `Modifier.enoShadow`. Палитра v1.5 и служебные значки — копии `enotone-design` `a2afd4c`. Roborazzi: новые `albums_scrolled_*`, `translucent_dock_*`, `settings_appearance_*`, обновлены «Песни» и «Сейчас играет».
- D3.1: доработка каркаса по макетам Android v2 (ревью сборки 578) — «Играть» (было «Воспроизвести») и «Перемешать» одного вида везде и только на «Песнях» и альбоме; крупный заголовок со значком поиска и счётчиком «3 590 песен / N альбомов / N артистов / N авторов»; поле поиска раскрывается по значку, поиск есть и в полоске прокрутки; полоска — одна строка с круглыми Ø 40; «Артисты», «Авторы альбомов»; «Сейчас играет»: значки перемешивания/повтора без розовой заливки, тонкая линейка; «Домой»: значок «ползунки», Бандит выглядывает из карточки; «⋮» в строках без подложки, статичная «волна» разной высоты. Служебные значки поиска, «ползунков» и «закрыть» — из утверждённых макетов. Снимки экранов `:app` (Roborazzi) для сверки с макетами. Удалены 4 неиспользуемых значка ADR-0073.
- D3 (#126, ADR-0086), Huawei PASS `0.6.2-preview.578`: каркас навигации Android v2 — плавающая нижняя панель «Домой · Песни · Альбомы · Артисты · Авторы», закреплённый мини-плеер v2 (две кнопки, прогресс), «Сейчас играет» сворачивается свайпом вниз и ручкой «Свернуть», «Домой» C2 (вход в «Настройки», «Перемешать всё · N песен» с Бандитом), прокрутка вариант B: заголовок, поиск и кнопки едут со списком, полоска с размытием (Haze 1.7.2) и «Играть»/«Перемешать». Навигация — `AppNavigator` вместо флагов. Удалены `CompactLibraryNavigation`, старый мини-плеер, 3 значка ADR-0073. Huawei — ожидает.
- Закрытие D2: ревью «что удалить» — удалены 10 неиспользуемых значков ADR-0073 (`ic_action_close/expand/pages/search`, `ic_music_add/cast/filter/lyrics/queue/star`); STATUS, DESIGN_PLAN, ROADMAP.
- D2b (#123): stateless-компоненты Android v2 в `:core:ui` (EnoIcons/Icon, Бандит 12 поз, wordmark drawable, пилюли, круглая кнопка, волна проигрывания, строка трека, плитка альбома, детерминированный аватар); Preview обеих тем и Roborazzi-галереи с синтетическими названиями, unit-тест аватара. В экраны не встраиваются до D3–D6. Окантовка обложки поверх картинки; одна галерея-превью вместо восьми.
- D2a (#122, ADR-0084): новый `:core:ui` и 39 перенесённых без изменения данных ресурсов D1; палитра v1.4 из `enotone-design` `907a01c`; общая Material 3 тема «Гра…9123 tokens truncated… по имени файла; загружаются только существующие локальные метаданные, без фоновой индексации.
- JVM тестирование построения и поиска по 3590 синтетическим трекам; ADR-0030 и инструкция проверки.

### M3 one-track playback (Draft)
- По нажатию на трек в офлайн-медиатеке пробный Media3 ExoPlayer GET выбранного файла из Cloud.Mail.ru WebDAV; пароль только RAM и заголовок, редиректы запрещены; после блокировки GMS gate у OkHttp заменено на собственный HttpURLConnection Media3 DataSource со строгой проверкой HTTP 206 / Content-Range.
- Кнопки пауза/продолжить/стоп, безопасные коды ошибок, автоматическая пауза при сворачивании. Foreground-only без очереди и кэша.
- Новый тест валидатора WebDAV URL и ADR-0031; реальная совместимость на Huawei пока не подтверждена.

### M3 background MediaSession (Draft)
- Плеер вынесен из Compose-экрана в `MediaSessionService` с типом foreground mediaPlayback и стандартным медиауведомлением Android.
- Приватный локальный Binder передаёт секреты только в RAM; экран при закрытии отключается от службы, не останавливая воспроизведение.
- Добавлен contract-test AndroidManifest + жизненного цикла для безопасности; фон и медиа-кнопки на Huawei пока не проверены.

### M4-A local context queue (Draft)
- Следующий и предыдущий трек по текущему списку/поиску из Room, счётчик позиции, автоматический переход после завершения.
- Не более 10000 локальных записей, все данные очереди и credentials только в RAM MediaSessionService, без дополнительных PROPFIND и без массового скачивания.
- Синтетические JVM-тесты границ и 3590 треков, расширен contract-test службы; ADR-0033.

### M4-B native Media3 skip (Draft)
- Очередь поиска/списка передаётся ExoPlayer как настоящий плейлист. Next/previous/автопереход обеспечиваются стандартными Player-командами MediaSession и Android, а не вторым вручную изменяемым индексом.
- Для каждого трека новый безопасный источник выбирает его собственные размер и ETag, с прежним HTTPS-only/206/no-redirect и GMS-free gate.
- Синтетический тест маршрутизации 3590 элементов, тест контракта MediaSession и ADR-0034.

### M4-C repeat/shuffle (Draft)
- Режим повтора (выкл. → текущий трек → очередь) и включаемый случайный порядок через native Media3 Player.
- Состояние кнопок синхронизируется через Media3 listener и восстанавливается при возврате к экрану; лишние GET, новый кэш и миграции Room не добавлены.
- JVM-тесты повтора, contract-test и ADR-0035; Huawei ещё не проверен.

### M5-A transient current cache (Draft)
- Media3 SimpleCache/CacheWriter в app-private cacheDir: полная загрузка текущего файла отдельно от play/pause, read-only cache при воспроизведении, LRU 512 МиБ.
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
