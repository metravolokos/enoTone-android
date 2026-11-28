# Исследование: расширение источников музыки (папка Cloud.Mail.ru, альтернативные WebDAV, локальные файлы)

> **Статус:** исследование + принятое решение [ADR-0106](DECISIONS/ADR-0106-source-endpoint-model.md) (Вариант 1, 2026-11-28). Первый срез — [Issue #313](https://github.com/metravolokos/enoTone-android/issues/313). Код ещё не изменён.
> **Срез:** `main` `56a4b96b` (2026-11-28).
> **Связанные решения и требования:** [R-6.1b](REQUIREMENTS.md) (настраиваемый корневой путь, ❌), [R-4.2](REQUIREMENTS.md) (`MusicSource`, ❌), [R-3.4](REQUIREMENTS.md) (три HTTP-стека), [ADR-0003](DECISIONS/ADR-0003-webdav.md), [ADR-0010](DECISIONS/ADR-0010-sources.md), [ADR-0014](DECISIONS/ADR-0014-flat-listing.md), [ADR-0018](DECISIONS/ADR-0018-webdav-range-one-file.md), [ADR-0031](DECISIONS/ADR-0031-media3-one-track-playback.md), [ADR-0080](DECISIONS/ADR-0080-permanent-login-sign-out.md).
> **Смета провайдеров:** см. §5; **протокол проверки провайдера перед поддержкой** — §6.

## 1. Что просит владелец

Три роли источника (взаимоисключающие в первой версии), плюс настраиваемая папка:

1. **Cloud.Mail.ru** — музыка в задаваемой пользователем папке (при первом импорте либо после выхода и повторного входа).
2. **Yandex.Disk** — по тому же WebDAV-транспорту; по логике не должно отличаться от Mail.ru (лимиты блоков, одновременное чтение, Range).
3. **Локальные файлы** — устройство, без сети.
4. Если возможно — объединить локальные файлы с одним из облаков.

Инженерный вывод владельца, подтверждённый исследованием: **сетевой код надо отделить от кода медиатеки**.

Обсуждённая рамка (черновая, требует решения владельца):
- **Один активный источник за раз**; объединение локального и облачного — отдельным ADR позже.
- **Первый срез:** настраиваемая папка Mail.ru + вынос общего байтового/сетевого слоя под будущие источники.
- Яндекс — отдельной фазой за пробой; локальные — отдельной ролью.

## 2. Текущее состояние кода

### 2.1 Жёстко зашитое (инвентаризация)

Хост `webdav.cloud.mail.ru` — **17 строк**; путь `/AllMusic` — около **152 строк в коде** (плюс документация и дизайн-исходники). Ключевые сторожевые проверки:

| Файл | Что запрещает сегодня | Что менять |
|---|---|---|
| `core/network/.../FlatLibraryWebDavClient.kt:37,50-57` | HTTPS; путь ровно `/AllMusic`; хост уже инъектируемый | Принимать проверенный endpoint; сравнение корня с каноническим |
| `core/network/.../FlatWebDavListingParser.kt:39-47,152-191` | корень обязан оканчиваться на `/AllMusic/`; origin/prefix; плоские прямые дети | Выводить префикс из конфигурации; сохранить same-origin и flat-child |
| `core/network/.../WebDavDiagnostics.kt:46,74-85` | base URL — только корень хоста; `PROPFIND /AllMusic` захардкожен; тестовый файл только под `/AllMusic` | Строить probe из конфигурации; шаги-идентификаторы вместо строк пути |
| `data/library/.../WebDavRangeReader.kt:79,121-139` | **хост строго `webdav.cloud.mail.ru`**, `rawPath == /AllMusic/`, плоский файл | Убрать allow-list хоста и фиксированный префикс; сохранить redirect/Range/ETag |
| `data/library/.../LibrarySnapshotPlanner.kt:49-53` | плоский префикс `/AllMusic` при сохранении | Проверять против канонической идентичности библиотеки |
| `data/library/.../LibraryTagStore.kt:52-58` | запись одного тега только для `/AllMusic`-строк | Обобщить префикс |
| `playback/.../WebDavPlaybackTarget.kt:12-32` | `ORIGIN`-константа; flat `/AllMusic`; точный host/authority | Строить URL из endpoint; не склеивать base path с уже абсолютным `encodedPath` |
| `data/library/.../LibraryRepository.kt:36-50` | fail маршрута исключает трек из `preparedById` | Использовать тот же конфигурируемый валидатор |
| `playback/.../WebDavMedia3DataSource.kt:49-51,193-195` | повторная проверка origin/root при open | Внедрять route-builder |
| `playback/.../WebDavPlaylistRoutes.kt`, `PreviewAudioPlayback.kt:398-405` | маршруты очереди через `WebDavPlaybackTarget` | Пробросить endpoint |
| `app/.../AccountSettingsViewModel.kt:116-125` | приём входа только по шагу `"PROPFIND /AllMusic"` = 207 | Проверять настроенный корень; идентифицировать шаг по enum |

Отдельно (не хардкод, но propagation): `LibraryIndexingGateway.kt:27-37`, `PilotTagIndexer.kt:202-207`, `ParallelTagIndexer.kt:307-316`, `ArtworkPreviewRepository.kt:442-450`, `ManagedTagIndexSession`, `IndexingRunner`, `BackgroundWorkLauncher`, `ManagedScanRuntime`, `ArtworkScanRuntime`, `ArtworkIndexingForegroundService`, `ArtworkPreparationRunner`, `PlayerViewModel`, `PlaybackConnection`, `EnoTonePlaybackService`.

### 2.2 Уже настраиваемо (не переоценивать объём)

- `FlatLibraryWebDavClient.rootUrl`, `FlatWebDavListingParser.rootUrl`, `WebDavDiagnostics.baseUrl`, `WebDavRangeReader.libraryRoot` — **параметры конструкторов**. Три нижних класса принимают любой HTTPS-хост; приложение просто всегда передаёт дефолты.
- Проверки origin в парсере **относительны** переданному корню, а не глобальному имени Mail.ru.
- Room `encodedPath` — обычный текст без `CHECK` на `/AllMusic`.
- `network_security_config.xml` домены **не** фиксирует, только запрещает cleartext.
- Парсинг Range уже принимает и RFC `bytes ...`, и нестандартный Cloud `bytes=...`, и `*` для неизвестного размера.
- Basic-аутентификация — обычная WebDAV Basic (других схем нет).

### 2.3 Персистентность

| Хранилище | Что лежит | Есть ли хост/папка |
|---|---|---|
| Room `remote_tracks` | ID, `sourceKey`, `encodedPath`, версия файла, теги | Нет колонок; префикс пути внутри `encodedPath` |
| Room `playlists` | `sourceKey`, элементы по `trackId` | Нет |
| Vault (`CredentialEnvelope`) | зашифрованный JSON **ровно из двух полей** `[email, password]` | Нет |
| SharedPreferences аккаунта | флаги первого запуска, доказательства выхода | Нет |
| Очередь/обложки/аудиокэш | трек-ID, размер, ETag-хеши | Нет |

**Ни хост, ни корневая папка нигде не хранятся.** Значит при смене источника/папки приложение не может отличить «другая цель» от «тот же аккаунт».

### 2.4 Идентичность и риск смешивания

- `sourceKey = SHA-256(email.trim().lowercase())` (`LibrarySnapshotPlanner.kt:23-26`).
- `trackId = SHA-256(sourceKey + NUL + encodedPath)` (`:29-31`).
- `(sourceKey, encodedPath)` — уникальный индекс.

Следствия:
- **Тот же email + другая папка:** новые ID, но `sourceKey` общий; старые строки не помечаются отсутствующими (это происходит только при `completeForAbsenceTracking`), `OfflineTrackCatalog` не фильтрует по корню → **обе библиотеки видны вместе**, при возврате «воскресают» старые теги/обложки/очередь.
- **Тот же email + другой хост, совпадающие ETag/size:** тот же `trackId` → **прямая коллизия** метаданных, обложек и очереди.

### 2.5 Что проверяет текущая диагностика и приём входа

`WebDavDiagnostics` делает `PROPFIND /`, `PROPFIND /AllMusic` (Depth 1), опционально infinity и Range; тело **не парсит**. Приём входа (`verifyOutcome`) смотрит только статус шага `"PROPFIND /AllMusic"`: 207 → принято, 401/403 → «неверные данные», иначе → «облако недоступно». `DiagnosticReportSanitizer` пропускает только строки `"PROPFIND /"` и `"PROPFIND /AllMusic"`.

Прямо ломается:
- выбранная папка `/Music` → диагностика всё равно бьёт `/AllMusic` → обычно не 207 → ложный отказ;
- base path провайдера (`/remote.php/dav/…`) → база отклоняется; `endpoint()` заточен под корень origin;
- 401/403 может означать «папка есть, но запрещена», а не «неверный пароль».

## 3. Целевая архитектура (черновик)

Разделить не «провайдера», а **источник байтов**, чтобы локальные файлы не тащили фиктивный HTTP-слой.

```
Источник байтов (random-access)
  ├─ WebDAV Range  → Mail.ru, Яндекс (общий код; разные endpoint/папка/auth)
  └─ Local (SAF content:// / FileDescriptor)
        ▲
Код медиатеки: каталог Room, теги, группировка, очередь, обложки — не знает о транспорте
        ▲
Воспроизведение: маршрут по типу источника
   WebDAV → WebDavMedia3DataSource ; Local → Media3 ContentDataSource (системный)
        ▲
Конфигурация: провайдер (MAILRU | YANDEX | LOCAL) + корневая папка + credentials (envelope v2)
```

Ключевые принципы:
- **Endpoint-модель** вместо констант: `host + basePath + rootFolder + usernameKind + authKind`.
- **Общая window-логика** (`SharedRangeBudget`, `WebDavRangeReader`, `WebDavPlaybackTarget`) — уже провайдер-независима, менять только источник endpoint.
- **Endpoint-aware identity:** `libraryId = SHA-256(normalizedAccount + canonicalOrigin + canonicalRoot)`, формат 64 hex (его проверяют очередь/обложки/выход). Дефолт Mail.ru `/AllMusic` сохраняет **прежний** ID для совместимости существующей установки.
- **Vault envelope v2** с типом провайдера и корнем; v1 читается как Cloud `/AllMusic` (без миграции данных).
- Локальный источник: SAF-дерево, без email/ETag/HTTP-Range; `FileDescriptor` для тегов, `ContentDataSource` для воспроизведения.

## 4. Роли источников: детали и ограничения

### 4.1 Cloud.Mail.ru — настраиваемая папка
- Технически локально выполнимо: хост тот же, новый сетевой слой не нужен.
- Текущее правило — **«файлы непосредственно в выбранной папке», один `Depth: 1`**. Настраиваемая папка ≠ рекурсия: подпапки по-прежнему пропускаются. Рекурсия — отдельное решение (меняет парсер, полноту листинга, UI-сценарии).
- Реализует R-6.1b и служит предпосылкой для Яндекса (там корень — весь диск).

### 4.2 Yandex.Disk
- Endpoint `https://webdav.yandex.ru/`, корень — весь диск, папка задаётся пользователем. Basic: логин Яндекс + **WebDAV app password**.
- **Ограничение тарифа:** с **22.06.2026 WebDAV только на платном Yandex 360**; бесплатный диск WebDAV не даёт.
- Поведение `Range`/`ETag`/`Content-Range` публично **не подтверждено** (UNCONFIRMED). Транспорт тот же WebDAV, но паритет «как Mail.ru» нельзя обещать без пробы (§6).
- Реализационно — не «второй хост» руками, а следствие endpoint-модели.

### 4.3 Локальные файлы
- SAF: `ACTION_OPEN_DOCUMENT_TREE` → персистентный `content://`-URI (take persistable permission).
- Теги: `FileDescriptor` (seekable) → существующий парсер (MediaMetadataRetriever / собственный).
- Воспроизведение: Media3 `ContentDataSource` — **свой WebDAV-DataSource не нужен**.
- Нет email, нет ETag, нет HTTP Range → **другой тип источника**, но тот же каталог Room/группировка/очередь.
- Объединение с облаком в одну медиатеку — **вторая волна**: мульти-источник в каталоге, маршрут по типу (`https://` vs `content://`), ключи обложек, дедуп одинакового трека из двух мест.

## 5. Матрица WebDAV-провайдеров

Контракт enoTone: `206` + **точный `Content-Range`** + стабильный strong ETag + Basic + HTTPS без redirect. «Поддерживает WebDAV» этого не гарантирует.

**Прямые блокеры (не брать):**

| Сервис | Почему |
|---|---|
| Mailbox.org | свежий захват: `206` + `Accept-Ranges` + ETag, но **нет `Content-Range`** |
| CloudMe | `WWW-Authenticate: Digest` (Basic-only клиент несовместим) |
| Icedrive | WebDAV отключается с 15.04.2026 |
| MEGA (через MEGAcmd) | локальный шлюз без auth, не облачный Basic-endpoint |
| Synology C2 | документированного WebDAV endpoint нет |

**Кандидаты (в порядке силы):**

| Сервис | Endpoint | Auth | Range/ETag | Заметки |
|---|---|---|---|---|
| Nextcloud (self-hosted/managed) | `/remote.php/dav/files/USER/` | Basic + app password | SabreDAV: стандартный `206`/`Content-Range`/`If-Range` ✅; `Accept-Ranges` может отсутствовать | лучший кандидат; прокси не должен резать заголовки |
| OpenCloud | `/remote.php/dav/spaces/SPACE/` | Basic + app token | исходники: `206`/`Content-Range`/`Accept-Ranges` ✅; **`If-Range` не реализован** | возможен статус `425` processing |
| Nutstore/Jianguoyun | `dav.jianguoyun.com/dav/` | Basic + app password | офиц. клиент жёстко требует `206`; формат `Content-Range`/ETag UNCONFIRMED | лимит 1500 запросов/30 мин (paid) |
| Koofr | `app.koofr.net/dav/Koofr/` | Basic + app password (обязателен) | UNCONFIRMED | free tier; 429 после неверных паролей |
| pCloud EU/US | `ewebdav|webdav.pcloud.com/` | Basic, **основной пароль** | UNCONFIRMED | free; при 2FA — email-подтверждение (не для unattended) |
| Yandex.Disk | `webdav.yandex.ru/` | Basic + WebDAV app password | UNCONFIRMED | с 22.06.2026 только платный Yandex 360 |
| kDrive, MagentaCLOUD, legacy ownCloud, Seafile, HiDrive (IONOS/STRATO), GMX/WEB.DE, 1&1, Hetzner Box, Disroot | разные | app password/OTP | UNCONFIRMED | второй эшелон; OTP HiDrive 30/60 мин непригоден для постоянного входа |

**Проблемы username/auth, не только хост:**
- username не всегда email: `LibrarySnapshotPlanner.kt:23-26` и `WebDavMedia3DataSource.kt:189-191` требуют `@`;
- Digest / OAuth-OIDC (oCIS выключает Basic по умолчанию) / TOTP-в-пароле / email-подтверждение — требуют новых механизмов.

## 6. Протокол проверки провайдера перед поддержкой (capability probe)

Только на тестовой папке, не на личной библиотеке:

1. `PROPFIND Depth: 1` с именами `space name.mp3`, `plus+name.mp3`, `100%name.mp3`, `Юникод 🎵.flac`.
2. Повторить PROPFIND → стабильность raw path, размера, ETag.
3. `GET Range` `bytes=0-0`, `1-1`, `0-65535`, середина, последний байт.
4. Для каждого Range фиксировать: status, `Content-Range`, `Content-Length`, `Accept-Ranges`, `Content-Encoding`, `Transfer-Encoding`, ETag, redirect.
5. Повторить с `If-Range` и без.
6. Изменить тестовую копию под старым ETag → проверить отсутствие смешивания байтов.
7. Реальный playback + seek вперёд/назад, смена сети.
8. Прогон дольше 30/60 мин (истечение OTP/token).
9. Каталог на тысячи записей → нет молчаливого усечения.
10. Canonical endpoint → ноль redirect для PROPFIND и GET.

**Критерий допуска:** все точные Range → `206` с точным `Content-Range`, identity encoding, ETag = listing. Иначе провайдер остаётся **experimental**, а не «работает через WebDAV».

## 7. Нужные терпимости (сверх endpoint-модели)

| Проблема | Поведение |
|---|---|
| относительный `href` | поддерживать `file.mp3`, `/abs`, `https://same-origin`; origin/prefix после normalize |
| percent-encoding | декодировать ровно один раз для показа; для GET сохранять исходный encoded path; не переусложнять `+`, `%25`, `%23`, `%3F` |
| Unicode NFC/NFD | разные raw path не склеивать |
| trailing slash | канонизировать корень сами |
| absolute href на другой host | отклонять; не переносить Authorization на CDN |
| `200` вместо `206` | по умолчанию отклонять; bounded-fallback только отдельным решением |
| нет `Accept-Ranges` | не блокер (открытый issue Nextcloud) |
| нет `Content-Range` при `206` | считать несовместимым |
| weak/missing ETag | не слать как strong `If-Range`; работать без него, проверяя size, re-PROPFIND при расхождении |
| chunked `206` | принимать; сверять ровно ожидаемые байты, отклонять лишние |
| `425`/processing (OpenCloud) | не включать в готовую очередь; короткий retry |
| `429`/5xx | bounded retry с jitter и `Retry-After` |
| `401` внутри `207` на отдельный ресурс | не считать полный listing успешным |
| нет `getcontentlength` | не угадывать; `Depth:0`/HEAD; иначе не играть strict-Range |

## 8. Фазовый план (принят владельцем 2026-11-28)

Решение — [ADR-0106](DECISIONS/ADR-0106-source-endpoint-model.md) (Вариант 1). Первый срез — [Issue #313](https://github.com/metravolokos/enoTone-android/issues/313).

| Фаза | Содержание | Предусловие |
|---|---|---|
| **A1** | Endpoint-конфигурация + настраиваемая папка Mail.ru + вынос endpoint из 7 сторожей + проброс по цепочкам. Envelope v2. **Идентичность не меняется.** Смена папки — только через выход+вход. | Принят ADR-0106; Issue #313 |
| **A2** | Endpoint-aware library ID (`sha256(аккаунт + origin + корень)`); Mail.ru `/AllMusic` — прежний ключ | Только вместе с D (мульти-библиотека) |
| **B** | Yandex.Disk как второй облачный провайдер | Платный Yandex 360 (аккаунт у владельца есть) + capability-probe (§6) |
| **C** | Локальные файлы (SAF) как отдельная роль | Дизайн экрана выбора папки (`ACTION_OPEN_DOCUMENT_TREE`); при отсутствии макета — `design-needed` |
| **D** | Объединение локального и облачного в одну медиатеку | Отдельный ADR; сюда же A2 (мульти-источник, маршруты `https://`/`content://`, ключи обложек, дедуп) |

Каждая фаза самодостаточна и проверяема автоматическими проверками (ADR-0104).

## 8a. Стратегия развития для следующего агента

> Этот раздел — точка входа для исполнителя, который берёт направление «источники» после этого исследования.

**Что уже решено (не переспрашивать владельца):**
- Вариант 1 (endpoint-модель + фазовость) принят. Один активный облачный источник за раз; объединение локального и облачного — отдельным ADR.
- В A1 идентичность **не меняется** (`sourceKey = sha256(email)`); endpoint-aware ID отложен до A2/D.
- Смена папки/источника — только через выход и повторный вход; при живом входе поле папки недоступно.

**Порядок работы исполнителя (по WORKFLOW §5):**
1. Взять Issue #313 (после того как владелец даст метку `state:ready` и приоритет). Ветка `<type>/sources-a1-...` от свежего `main`; атомарный захват по WORKFLOW §5 (создание ветки через `POST /git/refs`, не `git push`).
2. Прочитать: ADR-0106, это исследование, ADR-0080 (порядок выхода), ADR-0014/0015 (flat-листинг), ADR-0018/0031 (Range/воспроизведение), `docs/MODULE_MAP.md`.
3. Точки входа в код — исследование §2.1 (7 сторожей) и §2.2 (что уже настраиваемо). Правки — маленькими PR; Scope не расширять.
4. Поведенческие тесты вместо строковых контрактов (AUDIT_PROTOCOL §88-89). `tools/mock_webdav.py` параметризовать.
5. Класс риска R: два независимых ревью; exact-HEAD CI зелёный; merge — только по команде владельца.

**Чего НЕ делать в A1:** рекурсия, endpoint-aware ID, Яндекс, локальные файлы, единый HTTP-стек (R-3.4) — у каждого своя фаза/ADR.

**Открытые решения, которые исполнитель не принимает сам:** тариф Яндекса, дизайн SAF-экрана, семантика «сброса» локального источника, единый сетевой слой.

## 9. Риски и открытые вопросы

- **Идентичность/смешивание** при смене источника/папки без endpoint-aware ID — потеря данных и путаница обложек/очереди (§2.4).
- **Три HTTP-стека** (Ktor CIO, HttpURLConnection, HttpsURLConnection) — до единого слоя мультипровайдерность преждевременна ([R-3.4](REQUIREMENTS.md), [KNOWN_ISSUES](KNOWN_ISSUES.md)).
- **Рекурсия vs папка:** настраиваемая папка не даёт вложенных подпапок без отдельного решения.
- **Vault v2:** расширение формата входа требует версионирования (v1 = Cloud `/AllMusic`), без утечек пути в Room/логи/бэкап/Intent.
- **Локальный источник и выход/смена аккаунта:** у локального нет Keystore-входа; семантику «сброса» надо определить.
- **Яндекс:** тариф (платный Yandex 360) и неподтверждённый Range — внешние предпосылки.
