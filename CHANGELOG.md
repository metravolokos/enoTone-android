# CHANGELOG

> Версии — по [ADR-0078](docs/DECISIONS/ADR-0078-versioning.md): новое пишется в `Unreleased`; при приёмке на Huawei секция получает номер принятой сборки.

## Unreleased

- Bugfix #132 (PLAY-02/PLAY-07): в режиме повтора одного трека при неожиданном `Media3 STATE_ENDED` заново запускается тот же item без изменения индекса очереди; для остальных режимов сохранён переход Next. Регрессия подтверждена падающим тестом на исходном коде в CI #602; добавлены JVM-тесты фейкового Media3-плеера, передачи repeat/shuffle в `SharedPlayerUiState` и Robolectric-тест реальных нажатий. Huawei ожидает проверки; дизайн, Room и формат очереди не менялись.
- D3.3 (ADR-0088, решение владельца после проверки D3.2): переключатель «Прозрачные панели» оставлен и теперь управляет и верхними панелями — закреплённым заголовком вкладок и полоской прокрутки альбома. Выключен — заголовок при прокрутке сплошной с линией снизу, включён — 84 % с размытием. Подпись настройки обновлена.
- D3.2 (ADR-0088, решения владельца по сборке D3.1): на вкладках «Песни», «Альбомы», «Артисты», «Авторы» заголовок (название, поиск, счётчик, на «Песнях» — кнопки) закреплён, список идёт под ним — с размытием и линией при прокрутке; полоска варианта B осталась только у альбома. Поиск: «Найдено N песен / альбомов / артистов / авторов», «Ничего не нашлось» (вместо «Найдено групп»). Новая настройка «Оформление → Прозрачные панели» (по умолчанию выкл.): мини-плеер и нижняя панель на 72 % с размытием 24 dp; отдельный файл настроек, `UiAppearance` не тронут. «Сейчас играет»: ручки нет — закрытие только свайпом вниз; обводка обложки (белая/чёрная 35 %) и крупная тень `Modifier.enoShadow`. Палитра v1.5 и служебные значки — копии `enotone-design` `a2afd4c`. Roborazzi: новые `albums_scrolled_*`, `translucent_dock_*`, `settings_appearance_*`, обновлены «Песни» и «Сейчас играет».
- D3.1: доработка каркаса по макетам Android v2 (ревью сборки 578) — «Играть» (было «Воспроизвести») и «Перемешать» одного вида везде и только на «Песнях» и альбоме; крупный заголовок со значком поиска и счётчиком «3 590 песен / N альбомов / N артистов / N авторов»; поле поиска раскрывается по значку, поиск есть и в полоске прокрутки; полоска — одна строка с круглыми Ø 40; «Артисты», «Авторы альбомов»; «Сейчас играет»: значки перемешивания/повтора без розовой заливки, тонкая линейка; «Домой»: значок «ползунки», Бандит выглядывает из карточки; «⋮» в строках без подложки, статичная «волна» разной высоты. Служебные значки поиска, «ползунков» и «закрыть» — из утверждённых макетов. Снимки экранов `:app` (Roborazzi) для сверки с макетами. Удалены 4 неиспользуемых значка ADR-0073.
- D3 (ADR-0086): каркас навигации Android v2 — плавающая нижняя панель «Домой · Песни · Альбомы · Артисты · Авторы», закреплённый мини-плеер v2 (две кнопки, прогресс), «Сейчас играет» сворачивается свайпом вниз и ручкой «Свернуть», «Домой» C2 (вход в «Настройки», «Перемешать всё · N песен» с Бандитом), прокрутка вариант B: заголовок, поиск и кнопки едут со списком, полоска с размытием (Haze 1.7.2) и «Играть»/«Перемешать». Навигация — `AppNavigator` вместо флагов. Удалены `CompactLibraryNavigation`, старый мини-плеер, 3 значка ADR-0073. Huawei — ожидает.
- Закрытие D2: ревью «что удалить» — удалены 10 неиспользуемых значков ADR-0073 (`ic_action_close/expand/pages/search`, `ic_music_add/cast/filter/lyrics/queue/star`); STATUS, DESIGN_PLAN, ROADMAP.
- D2b (#123): stateless-компоненты Android v2 в `:core:ui` (EnoIcons/Icon, Бандит 12 поз, wordmark drawable, пилюли, круглая кнопка, волна проигрывания, строка трека, плитка альбома, детерминированный аватар); Preview обеих тем и Roborazzi-галереи с синтетическими названиями, unit-тест аватара. В экраны не встраиваются до D3–D6. Окантовка обложки поверх картинки; одна галерея-превью вместо восьми.
- D2a (#122, ADR-0084): новый `:core:ui` и 39 перенесённых без изменения данных ресурсов D1; палитра v1.4 из `enotone-design` `907a01c`; общая Material 3 тема «Графит»/«Фарфор», Unbounded/Onest variable, радиусы, тени; Roborazzi с двумя темами. Старый строковой тест Graphite заменён на поведенческий тест `UiAppearance`; layout и user data не менялись. Huawei PASS `0.6.2-preview.566` (DES-01).

- Разработка версии 0.6.2.
- M6-0/6 (Draft `feature/m6-0-6-room-v5`, ADR-0085): Room v5 добавляет версионную длительность и таблицы модели плейлистов; `:data:playlist` реализует FAVORITES без UI. Длительность читается только из уже загруженных FLAC/MP3 заголовков или записывается при воспроизведении; старые треки не дочитываются автоматически. Выход атомарно удаляет плейлисты и элементы аккаунта. Тесты миграции, DAO, парсера и очистки; Huawei-приёмка ожидает владельца.
- Качество (по ревью «что удалить»): detekt **блокирующий** с базовой линией в каждом модуле (429 записей) и CI-проверкой «не растёт»; CI проверяет покрытие индекса ADR. Удалены Gson (очередь — встроенный `org.json`, старые файлы читаются), `ManagedIndexMode`, 15 неиспользуемых строк. Формат зашифрованного входа вынесен в `CredentialEnvelope` с тестами ACC-06. Решения владельца: O-18 (индексация после остановки — только по кнопке), LIB-04 (повторного импорта нет).
- Процесс: `AUDIT_PROTOCOL` (режимы ревью/аудита, формат замечаний, пороги сложности, механизм сокращения, текст для ChatGPT), каталог `SCENARIOS` (~50 сценариев с ID и последними результатами, события жизненного цикла, погрешность позиции), журнал `REDUCTION_REVIEWS` — первое ревью «что удалить». TESTING и ROADMAP сокращены, история — в `docs/archive/`. M6-0/5 закрыт: smoke `0.6.2-preview.522` PASS; открыт D2. Статусы ADR 0044–0049 и 0083 исправлены; R-9.4 (защита `main`) выполнено.
- M6-0/5b (Draft PR): ADR-0083, поэтапное выделение моделей в `:core:model` и независимых классов в `:feature:library/player/settings`, полные реализации Now Playing и Home, а также UI видимости страниц перенесены через прежние ресурсные фасады `:app`, удаление пяти невостребованных пустых модулей; `:data:playlist` резервирован для M6-0/6. Остальные ресурсозависимые экраны и ViewModel по-прежнему в `:app` до решения R-контракта на D2–D6. Kotlin-алгоритмы, Room v4, ресурсы Android v2 и подпись Stable Preview не менялись. CI/ревью ожидаются.
- M6-0/5a (PR #118, слит, `main` `2c3468c`, CI #508): Robolectric/Compose UI/Room миграции и DAO, маршруты настроек/плеера, флаг выхода и интент уведомления. После ревью удалены 6 доказанно заменённых Python contract-файлов, 18 policy/частичных оставлены с пофайловым обоснованием; добавлены реальные SharedPreferences и RU/EN Resources тесты. Room v4, дизайн и сеть без изменений. CI #504 до доработки зелёный; повторный CI нового HEAD — ожидается.
- O-17: владелец подтвердил PASS на `0.6.2-preview.493` (2026-11-13): уведомление открывает «Сейчас играет», в том числе после смахивания; «Назад» и продолжение музыки работают.
- Нажатие на уведомление воспроизведения открывает приложение на «Сейчас играет» (`setSessionActivity`; дефект Huawei 2026-11-13). Смахивание из недавних во время воспроизведения музыку не останавливает — решение владельца 2026-11-13 (O-17).
- M6-0/4c (#114) на Huawei `0.6.2-preview.487`: обновление с новыми файлами PASS; пауза/остановка/выключенный экран — только JUnit до большого импорта. ROADMAP актуализирован (4b-2, 4c, аудит).
- M6-0/4c (#114): службы индексации тегов и подготовки обложек разделены на Android lifecycle, RAM-состояния и тестируемые runner-ы с портами. Удалены неиспользуемые методы слоя данных, сводка пилота и BOUNDED; Room v4, непрерывная индексация, контрольные точки, `NO_ART`, сетевые лимиты, уведомления и дизайн сохранены. Добавлены JUnit с фейками и адаптированы существующие Python-проверки. Exact-HEAD CI и Huawei — ожидаются.
- Зависимости: Kotlin 2.3.10 → 2.4.21, kotlinx-coroutines 1.10.2 → 1.11.0, KSP 2.3.10 → 2.3.13, Gson 2.13.2 → 2.15.0 (вместо Dependabot #95 и #90).
- Внешний аудит 2026-11-13 закрыт: #109–#111 подтверждены на Huawei `0.6.2-preview.475` (владелец, «Всё ок»); защита ветки `main` включена (PR + CI `android`).
- Обложки (внешний аудит п. 5, ADR-0082): после импорта сбрасываются ссылки на обложки только у файлов новой версии (включая изменение лишь mtime), ключи ADR-0067 и сохранённый кэш не меняются; RAM-кэш альбома учитывает набор источников. JUnit планировщика.
- Воспроизведение (внешний аудит п. 4, 6, 7): позиция сохраняется сразу после перемотки, в том числе на паузе и из уведомления/гарнитуры (`onPositionDiscontinuity`); исполнитель и альбом из Room попадают в `MediaMetadata` очереди (мини-плеер, «Сейчас играет», уведомление); чекпоинт очереди хранит предыдущую структуру на диске — гибель процесса между записью структуры и позиции восстанавливает прежнюю очередь целиком. JUnit, включая имитацию гибели процесса.
- Актуальность медиатеки и навигация (внешний аудит п. 2, 3): общая ревизия `LibraryDataRevision` растёт после импорта списка, завершения индексации (и каждые 500 прочитанных треков), подготовки обложек и обновления тегов альбома; открытая медиатека и каталог плеера перечитываются без пересоздания экрана. Переход с «Домой» — пронумерованное событие существующей ViewModel (возврат из «Сейчас играет» выбор не сбрасывает), скрытие страниц применяется к открытой медиатеке. JUnit. Аккаунт (#108) подтверждён на Huawei `0.6.2-preview.464`.
- Аккаунт (внешний аудит 2026-11-13, п. 1 и 8): рабочий вход для настроек, медиатеки и плеера — только подтверждённый (восстановленный из Keystore или успешно проверенный), поля формы до проверки никуда не передаются и не редактируются после входа. Незавершённый выход блокирует импорт, индексацию, обложки, медиатеку и восстановление очереди до успешного повтора «Выйти», в том числе после перезапуска (флаг без личных данных). JUnit. Документы: R-7.3 (`onTimeout` обработан), число замечаний detekt; мини-плеер вне «Настроек» подтверждён на Huawei `0.6.2-preview.462`.
- Мини-плеер убран из «Настроек» и «Диагностики» (O-10 уточнено владельцем 2026-11-13: только экраны музыки). Чистка «Настроек» #106 подтверждена на Huawei `0.6.2-preview.460` (п. 1–6).
- Чистка «Настроек» (#106, ADR-0081, O-16): удалены устаревшие инструменты M0–M3 (пилоты, чтение одного файла, путь к тестовому файлу, Depth infinity, подсчёт файлов, офлайн-проверки, ограниченная сессия, поиск и точечная переиндексация в аудите, переключатель A/B кэша) и ~60 строк ресурсов. Основной экран сгруппирован карточками Graphite: Облако → Медиатека → Страницы → Оформление → Обложки; после паузы/сбоя — понятный текст и «Продолжить»/«Повторить», сетевые ошибки без HTTP-кода. Подэкран «Диагностика»: проверка соединения, коды обновления медиатеки, воспроизведение и очередь, «Треки без тегов» (только просмотр). JUnit обновлены (продолжение, ошибка без кода, санитизация, аудит). Ресурсы дизайна v2 не тронуты.
- M6-0/4b-2 (#104): оставшиеся операции настроек перенесены в LibraryIndexingViewModel, ArtworkSettingsViewModel, DiagnosticsViewModel и gateway через AppContainer; BackgroundWorkLauncher изолирует службы и RAM-handoff. SettingsScreen разделён на небольшие разделы без изменения разметки, Preview, удалён openLibraryDatabase. Kotlin-тесты действий, подтверждения/отмены, повторных нажатий, автообложек/очистки и санитизации небезопасного фейкового ответа; удалены 5 строковых тестов UI, оставшиеся проверки безопасности перенесены на порт. Дизайн, версия, Room, playback и CI сохранены; Huawei smoke ещё не проведён, DESIGN_PLAN не открывает D2.
- Документация после 4b-1: ADR-0080 «Действует», ADR-0043 «Заменено» в индексе; CURRENT_DECISIONS, AGENTS, README, KNOWN_ISSUES без упоминаний «до слияния 4b»; закрытые дефекты #92 убраны из KNOWN_ISSUES, TESTING п. 10–11 обновлены.

## 0.6.1 — 2026-11-12

Принята владельцем на Huawei: `0.6.1-preview.446` (`main` `afa624d`), git-метка `v0.6.1`; проверка Б-1 полностью.

- M6-0/4b-1 (PR #100, 2026-11-12): `AccountSettingsViewModel` + 4 `@Preview`, проверка WebDAV с автоматическим сохранением входа в AES-GCM Keystore, подтверждаемый многоэтапный `AccountSignOut` с удалением записей текущего `sourceKey`, очереди и обложек без затрагивания общих WebP. DAO без изменения схемы Room v4, фейковые JUnit и файловый тест сохранения общих мастеров, `MainActivity` 20 строк; ADR-0080 вместо добровольного ADR-0043. Исправлено замечание ревью: необязательная привязка MediaSession при выходе, общий `AccountSession`/StateFlow очищает медиатеку и плеер без перезапуска; JVM-тесты поведения. Остаток технического экрана выделен в 4b-2, Huawei Б-1/Б-2 не проверены, точный HEAD CI ожидает.
- Документация: STATUS/ROADMAP/REQUIREMENTS сверены после приёмки 0.6.0 (релиз `v0.6.0` с APK), R-9.2 — частично.

## 0.6.0 — 2026-11-12

Принята владельцем на Huawei: `0.6.0-preview.429` (`main` `f6a1359`), git-метка `v0.6.0`. Включает всё ниже.

### нумерация версий (2026-11-12)

- `versionName` = `0.6.0`; Stable Preview — `0.6.0-preview.<номер CI>`. Версия показывается первой строкой в Настройках. ADR-0078, процедура — RELEASE.md.
- После слияния #98 (D1): статусы ADR-0079, ROADMAP, REQUIREMENTS (O-08, O-14; нумерация версий — O-15), STATUS. Дизайн привязан к этапам: M6-0/5 открывает D2, M6-0/6–7 нужны для D4, M6-0/4b–4c — для D6; правило в AGENTS.md; строка в KNOWN_ISSUES о неиспользуемых ресурсах.

### M6-0/R (D1): ресурсы дизайна Android v2 (2026-11-12)

- Иконка приложения v2.0 (adaptive: Бандит на «Графите» + legacy PNG), `android:icon`/`android:roundIcon` в манифесте — единственное видимое изменение.
- Неиспользуемые пока ресурсы для шагов D2–D6: 19 значков `ic_eno_*` (UI Icons v1), `enotone_wordmark` с цветами темы, 12 поз Бандита (`drawable-nodpi/bandit_*.webp`, `tools/convert_bandit.py`), палитра `eno_*`, шрифты Onest/Unbounded (OFL); `tools:keep`. Источник — `enotone-design` `ea2931c`.
- `docs/DESIGN_PLAN.md` — порядок переноса дизайна; [ADR-0079](docs/DECISIONS/ADR-0079-design-resources-v2.md); AGENTS, ROADMAP, CURRENT_DECISIONS, REQUIREMENTS (O-08, O-14), TESTING (п. 13), THIRD_PARTY_LICENSES.

### исправления по Huawei 2026-11-12

- Восстановленный трек до первого Play показывал шкалу на нуле и «--:--»: длительность теперь сохраняется в файле позиции (необязательное поле `durationMs`, старые файлы читаются) и показывается до подготовки плеера.
- Возврат из «Сейчас играет» сбрасывал список медиатеки в начало (регрессия #92): состояния прокрутки сохраняются через `SaveableStateHolder` / `rememberSaveable`.
- Регулятор громкости удалён полностью — код, строки, иконки (решение владельца, REQUIREMENTS O-12); строковые проверки переведены на запрет.

### матрица требований (#93, 2026-11-12)

- `docs/REQUIREMENTS.md`: все требования ТЗ и решения владельца O-01…O-11 со статусом и доказательством; правило в AGENTS.md — каждый PR обновляет затронутые строки. После слияния #92 строки O-10, R-3.9, R-4.3, R-6.6d обновлены.

### M6-0/4a: слой представления медиатеки (#92, слит 2026-11-12)

- Общий Activity-scoped `PlayerViewModel` восстанавливает очередь при запуске на любом экране и ведёт мини-плеер на Home / Library / Settings; строка диагностики checkpoint; JVM-тесты атомарной пары и отката. Проверка на Huawei открыта.

- [ADR-0077](docs/DECISIONS/ADR-0077-presentation-layer.md): `EnoToneApplication`, ручной `AppContainer` с общей Room v4, `LibraryRepository` с IO, `PlaybackConnection` со снимками Media3 и `LibraryViewModel` + одним `StateFlow`.
- `OfflineLibraryScreen.kt` сокращён до 118 строк; неизменённая Graphite Material 3-разметка находится в `LibraryScreenContent.kt`. Добавлены четыре фиктивных @Preview, JVM-тесты состояний/поиска/очереди/восстановления и чистого преобразования каталога.
- Убраны завязанные на старый Kotlin-файл Python-проверки строк; оставлен временный защитный контракт без `stopPlayback` и секретов в `Intent` на новом владельце кода. Mock WebDAV не менялся. Подробное сопоставление — описание PR #92.
- Room-схема, WebDAV-клиенты, `:playback`, внешний вид и правила получения обложек не изменены. CI exact HEAD и Huawei smoke должны быть подтверждены отдельно; никаких PASS заранее.

### M6-0/8: инструменты качества (2026-11-12)

- Gradle Wrapper 8.13 (`./gradlew`) с проверкой SHA-256 дистрибутива; проверка `gradle-wrapper.jar` в CI.
- detekt 1.23.8 + правила ktlint (detekt-formatting) для всех модулей, **только отчёт** (артефакт `detekt-reports`); 546 замечаний — к разбору в M6-0/4–5. ADR-0076.
- CI: JUnit и Android Lint во **всех** модулях (раньше lint только `:app`).
- Dependabot: Gradle еженедельно (группы Kotlin+KSP, Media3, Room), GitHub Actions ежемесячно.

### M6-0/3: сохранение очереди (2026-11-12)

- [ADR-0075](docs/DECISIONS/ADR-0075-queue-persistence.md): два атомарных JSON v1 в noBackupFilesDir с общей ревизией (большая очередь/перестановка пишется только при изменении очереди или shuffle, маленькая позиция/repeat — периодически), Gson через version catalog, восстановление seed из Room с порционным заполнением без сетевого prepare до Play, числовая диагностика restoreSeedMs; поведенческие JUnit-тесты рассинхронизации и очереди 3590. Слито как #82 (squash `130f2f7`); проверка на Huawei — по TESTING.

### M6-0/2: общая база данных и удержание Wi-Fi (2026-11-11)

- `EnoToneDatabase.getInstance()` — один экземпляр Room на процесс вместо создания и закрытия новой базы в 20 местах UI и сервисов; файл базы и схема v4 не изменены, данные на устройстве сохраняются.
- ExoPlayer: `setWakeMode(C.WAKE_MODE_NETWORK)` и разрешение `WAKE_LOCK` — Media3 удерживает Wi-Fi только во время воспроизведения/буферизации.
- Контрактный тест имени файла базы перенаправлен на `EnoToneDatabase.kt`.

### M6-0/1: порядок в документации (2026-11-11)

- Начат этап M6-0 — устранение технического долга по итогам аудита ([ADR-0074](docs/DECISIONS/ADR-0074-m6-0-tech-debt-plan.md)); визуальное направление заморожено на ADR-0073.
- `docs/STATUS.md`, `docs/KNOWN_ISSUES.md`, `docs/TESTING.md` переписаны как описание **текущего** состояния; прежние журналы (включая Huawei #301 «Работает» и все числовые отчёты) перенесены без изменений в `docs/archive/`, туда же — `REVISION_2026-11-10.md` и `UI_REVIEW_2026-11-10.md`.
- Добавлен индекс ADR с фактическими статусами (`docs/DECISIONS/README.md`).
- `AGENTS.md` исправлен: текущий UI — ADR-0073 (ранее ошибочно ADR-0069), добавлены правила ведения документации и запрет новых строковых contract-тестов.
- `ARCHITECTURE.md`: раздел о фактическом состоянии модулей; `ROADMAP.md`, `CURRENT_DECISIONS.md`, `README.md` — этап M6-0, устаревшие UI-разделы сведены к истории.
- Код приложения не менялся.

### Graphite Material3 (2026-11-11, PR #78)

- Убраны цветные пользовательские акценты, настройка акцента и цветные фоны обложек в UI; System/Dark/Light остаются.
- Нативная `NavigationBar`, поднятый `Surface` мини-плеера, действующие разделы без Radio/Search-фикций.
- Now Playing с графитовым тональным переходом, нативными Material 3 `Slider`, `FilledIconButton`, реальными Media3 и STREAM_MUSIC, без Lossless/Lyrics/Cast/Favorites/Queue заглушек.
- Альбомный экран нейтрален, сохранены Play/Shuffle и меню переиндексации. Room v4, WebDAV и постоянный master artwork 1200px без изменений.
- Исторические визуальные ADR-0068–0072 заменены ADR-0073; Huawei-приёмка после подписанной APK.

### screenshot-parity Huawei / ADR-0072 (2026-11-11)

- Apple Music Android reference: toolbar + постоянный поиск, однострочные Play/Shuffle, альбомная страница с цветным фоном и белой play-капсулой, 3 радиальных слоя Now Playing и тонкая полоса перемотки, отдельные более округлые мини/таб капсулы.
- Отключённые заглушки: избранное, добавление, фильтр, lyrics/cast; системная громкость, Media3 и локальный поиск работают.
- Нет изменений Room v4, WebDAV и 1200px постоянных обложек; аппаратное визуальное сравнение обязательно.


### новая визуальная реконструкция Apple Music / ADR-0071 (2026-11-11)

- Нейтральные цвета с подъемом #1C1C1E / #F2F2F7, красный по умолчанию #FA586A / #FA233B, прежняя настройка одного акцента и системной/темной/светлой темы.
- Типографика, подписи табов, отдельный mini-player над навигацией, свайп вверх, большие заголовки, сетка двух альбомов, списки артистов и песен, реальные Play/Shuffle.
- Now Playing с тремя радиальными цветными пятнами в обеих темах, белым контентом, 334dp верхним пределом обложки, тенью/PAUSED 0.84, seek, transport, системной громкостью.
- Без дополнительной сети/Room и фиктивных lyrics/AirPlay; см. ограничения и ручную приёмку в ADR-0071.

### Huawei fixes after Stable Preview #314 (2026-11-11)

- [ADR-0070](docs/DECISIONS/ADR-0070-vivid-artwork-system-bars.md): более насыщенные цвета обложки на трехступенчатом Now Playing градиенте, отсутствие видимых динамических «Пауза»/«Буферизация» и сдвига транспорта, тень с радиальной глубиной.
- Прозрачные status/navigation bars и безопасная edge-to-edge компоновка; фон плеера под системными полосами.
- Без новых WebDAV-запросов, без изменений Room/Media3/постоянных обложек. Новую Huawei-проверку фиксировать отдельно.

### нейтральное оформление Android (2026-11-11)

- [ADR-0069](docs/DECISIONS/ADR-0069-neutral-floating-artwork-ui.md): настраиваемые тёмный/светлый режимы, один сохраняемый цветной акцент и нейтральные серые поверхности.
- Навигация с мини-плеером поверх списков единым закруглённым островом и тенью, без мини-прогресса; Now Playing — локальные цвета обложки, градиент по режиму, тень и анимация PAUSED.
- Без изменений Room v4, WebDAV/Media3 и 1200px постоянных обложек; **ещё не подтверждено на Huawei**.


## 2026-11-11 — принято на Huawei: Stable Preview #301, постоянные обложки и M1

- **PR #71** (merge `a5c8962`, exact-HEAD CI #300: 19/19; подписанная Stable Preview [#301](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110): 23/23). Владелец установил APK и сообщил **«Работает»** — базовая функциональная приёмка, **не** замена отдельным offline/фоновым/perf тестам.
- Вместо исторического дискового кэша **1320 px / 64 МиБ / `cacheDir`** принят **постоянный мастер WebP до 1200 px** в `filesDir/enotone-artwork-v2`, без автоматического дискового LRU. Контентная дедупликация по SHA-256, локальные экранные размеры, ленивая миграция прежнего временного кэша.
- Общая выбранная обложка реального альбома для сетки, деталей, строк треков, мини-плеера и Now Playing. «Неизвестный альбом» не объединяет разные песни одним изображением. В Настройках — **ON_DEMAND** (по умолчанию) и **AUTO_AFTER_IMPORT** (опционально), фоновые прогресс/Pause/Stop, занимаемое место и ручная очистка. Source/album refs инвалидируются при явном обновлении даже при том же ETag.
- **Плейлисты:** принята будущая схема «обложка плейлиста ссылается на сохранённый мастер альбома», **без готового UI и назначения обложки**.
- **PR #70** (merge `ae7e275`, CI #294: 19/19; аппаратная базовая проверка Preview #295) завершает M1: единый инкрементальный импорт/обновление с жанром, дополнительная проверка size/mtime при равном ETag, редкая подтверждаемая полная переиндексация и подготовка scoped reindex альбома/ARTIST/ALBUM ARTIST без контекстных меню.
- Разделено текущее принятое состояние и старые Draft: [CURRENT_DECISIONS](docs/CURRENT_DECISIONS.md) / [ADR-0066](docs/DECISIONS/ADR-0066-indexing-finalization.md) / [ADR-0067](docs/DECISIONS/ADR-0067-permanent-album-artwork.md). Старые записи журнала ниже сохранены по дате.


### M1
- PR #7 merged после зелёного CI #31: системный MediaMetadataRetriever и нормализация тегов (NFC, fallback title).
- WebDAV Range источник и MediaDataSource: только HTTP 206 и Content-Range, 64 КиБ RAM-блок, 8 МиБ/128 запросов для **одного** файла.
- Добавлена ручная кнопка диагностического чтения тегов одного файла без отображения личного содержимого и без записи в Room; тесты MockWebServer, ADR-0018.
- Требуется зелёный CI и проверка на Huawei. Массовое чтение 3590 треков не начато.

## Подтверждённые этапы
- M0 WebDAV 207/206 на Huawei; M1 3590 файлов без пропусков, локальная Room показывает 3590 без обращения к облаку.

- Исправлена несовместимость debug APK при установке на Huawei: для тестов добавлен отдельный `app.enotone.android.preview`, который сохраняет прежнюю установку и Room. Артефакт переименован в `enotone-preview-debug-apk`, CI проверяет package ID и сертификат. Для будущих обновлений нужен постоянный signing key (ADR-0019).

- После ошибки тегов на Huawei Preview #36 добавлены фиксированные обезличенные коды проблем Range (HTTP/status/Content-Range/ETag/лимиты/сеть) и отдельный первый GET перед MediaMetadataRetriever. Сохраняются прежние лимиты, число тестируемых файлов — 1.

- Huawei Preview #38: `RANGE/BAD_CONTENT_RANGE/HTTP 206/1 GET/0 байт`. Добавлена классификация отличий Content-Range и безопасные числовые границы ожидания/ответа, без сырого заголовка и личных данных (ADR-0021). Транспорт и лимиты не ослаблены, ждём CI.

- Huawei Preview #40: `BAD_CONTENT_RANGE/FORMAT`. Исправлен собственный WebDAV parser: теперь поддерживается предусмотренный RFC 9110 `Content-Range bytes start-end/*`, при обязательных точных границах и байтовой длине; тесты отказов для неверных границ. Это ещё не подтверждённое поведение сервера (ADR-0022).

- Huawei Preview #42: `BAD_CONTENT_RANGE/FORMAT_EQUALS_SEPARATOR`. Реализована узкая совместимость с нестандартным `Content-Range: bytes=start-end/total` Cloud.Mail.ru. Начало/конец/размер тела/ETag/лимиты продолжают проверяться; тесты двух блоков и недопустимых ответов (ADR-0023). Требуется CI и проверка одного файла.

- Huawei Preview #45: **первый успешный реальный разбор тегов** одного файла — title/artist/album доступны, 50 Range-запросов, 3 276 800 байт.
- Добавлен ограниченный ручной слой LibraryTagStore для атомарного сохранения тегов одного файла в Room v1 по совпадению версии, без изменения схемы и без массовой загрузки (ADR-0024). CI и проверка на Huawei ещё не проведены.

- Huawei Preview #47: фактическая запись тегов в Room=1; title=1, artist=0, album=0; 39 GET и 2 490 368 байт (2,375 МиБ).
- Добавлена офлайн-кнопка агрегированных счётчиков сохранённых тегов из Room для проверки после перезапуска (ADR-0025). Текст/пути/ETag не выводятся, нет сети и изменений схемы. Ещё требуется CI и Huawei приёмка.

- Huawei Preview #49: без WebDAV получены 3590 сохранённых файлов и 1 набор тегов (title/artist/album/albumArtist присутствуют). Полный перезапуск отдельно не подтверждён.
- Draft perf M1: 4-блочный LRU RAM на 256 КиБ вместо одного 64-КиБ блока, числовые счётчики возвратов/вытеснений, тесты отсутствия повторных GET. Лимиты и безопасность HTTP без изменений (ADR-0026).

- Начат эксперимент fast metadata MP3/FLAC (ADR-0027): читать ID3v2.3/v2.4/ID3v1 и Vorbis Comments напрямую через текущий безопасный WebDAV Range, пропускать крупные обложки без скачивания. Опциональная однофайловая диагностическая кнопка, тесты synthetic MP3/FLAC и 2-GET MockWebServer. Полная индексация 3590 файлов не включена.

- Huawei Preview #54: FLAC Vorbis title/artist/album, **1 GET/65536 байт**, Room saved=1. MP3 ID3v2.4 ранее 3 GET/196608 байт/Room saved=1.
- Draft M1: ручной ограниченный индексатор до 10/20 треков, общий бюджет GET/байт/времени, сохраняемые пофайловые статусы Room v2 с AutoMigration v1→v2 и безопасным пропуском неизменённых файлов. Полный каталог не сканируется, нужна CI/приёмка (ADR-0028).

### M1 pilot fault reporting (Draft PR #20)
- Раздельная офлайн-статистика успешных, временных ошибок, постоянных ошибок и ещё не проверенных файлов без нового WebDAV-запроса.
- Показывается только безопасный этап/код последнего сбоя и HTTP статус; значения исключений/путей/тегов не отображаются.
- Временные сбои можно повторить следующей ручной попыткой; постоянные пропускаются до изменения версии файла. Добавлены синтетические JVM тесты, лимиты пилота без изменений.

### M2 local tracks (Draft)
- Первый read-only экран «Треки»: список из Room без WebDAV, поиск по названию, исполнителю, альбому.
- Файлы без тегов показываются по имени файла; загружаются только существующие локальные метаданные, без фоновой индексации.
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
