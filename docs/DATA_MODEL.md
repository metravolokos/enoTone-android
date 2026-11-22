# Модель данных enoTone Android

> **Актуально на 2026-11-22**, `main` `0f5e9881`, версия `0.6.2`. Room **v7**. Действующие решения — [CURRENT_DECISIONS](CURRENT_DECISIONS.md), миграции — [ADR-0085](DECISIONS/ADR-0085-room-v5-duration-playlist-model.md) и [ADR-0091](DECISIONS/ADR-0091-no-fts-memory-search.md).

## Room v7 — текущая схема

Экспортированные схемы лежат в `core/database/schemas/1.json … 7.json` и коммитятся. Миграции выполняются только без потери данных.

### `remote_tracks`

| Поле | Тип | Смысл |
|---|---|---|
| `id` | TEXT | SHA-256 от `sourceKey + encodedPath`; устойчив к переименованию только при неизменном пути |
| `sourceKey` | TEXT | хеш аккаунта; по нему ограничены все операции выхода |
| `encodedPath` | TEXT | WebDAV-путь внутри `/AllMusic` |
| `remoteEtag`, `remoteSize`, `remoteModified` | — | версия файла; изменение любого признака инвалидирует теги и обложку |
| `title`, `artist`, `album`, `albumArtist`, `genre`, `year`, `trackNumber`, `discNumber` | TEXT/INT | локальные теги |
| `durationMs` | INTEGER? | `null` значит неизвестно; CBR-MP3 без счётчика кадров остаётся `null` |
| `firstSeenEpochMs` | INTEGER? | когда трек впервые появился; `null` у строк до v5 и у первого импорта |
| `lastSeenEpoch`, `missingScans` | — | признак «файл ещё в источнике»; при `missingScans > 0` запрос возвращает `present = false` |

Уникальный индекс — `(sourceKey, encodedPath)`.

### `playlists` и `playlist_items`

- `playlists`: `id` (UUID), `sourceKey`, `name`, `kind` (`USER` / `FAVORITES`), `createdEpochMs`, `updatedEpochMs`. Один `FAVORITES` на аккаунт.
- `playlist_items`: `playlistId`, `trackId`, `position`, `addedEpochMs`; составной ключ `(playlistId, trackId)` не допускает дублей; элементы каскадно удаляются вместе с плейлистом.
- Элемент сохраняется при `missingScans > 0` и показывается как `present = false`. Выход из аккаунта удаляет плейлисты этого `sourceKey` каскадом; данные другого аккаунта не затрагиваются.

### Поиск

FTS4 **отменён** ([ADR-0091](DECISIONS/ADR-0091-no-fts-memory-search.md)). Таблица `track_search` удалена миграцией 6→7. Поиск идёт по нормализованным ключам, подготовленным при загрузке медиатеки, с русской сортировкой (`Collator`) и правилами ё/е.

### Группировка

Album, ARTIST и ALBUM ARTIST — виртуальные представления локальных тегов, а не отдельные таблицы. «Неизвестный альбом» не смешивает треки без тегов с реальным альбомом, который называется похоже.

## Обложки и аудиокэш — вне Room

- **Обложки:** постоянный `filesDir/enotone-artwork-v2`, WebP-мастера **до 1200 px**, имена `art-<sha256>.webp`; ссылки `ref-<hash>.txt` (аккаунт) и `album-<hash>.txt` (альбом) хранят только хеши. Режимы `ON_DEMAND` / `AUTO_AFTER_IMPORT`, ручная очистка с показом объёма. Автоматического LRU и удаления нет ([ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md)).
- **Аудиокэш:** временный, отдельный от обложек, ограничен 512 МиБ через LRU и очищается при старте службы. Это не постоянное offline-хранилище.

## Журналы вне Room

| Хранилище | Содержимое |
|---|---|
| `noBackupFilesDir/enotone-queue-v1.json` + `enotone-position-v1.json` | очередь и позиция; пишутся парой (сначала структура, затем позиция), при обрыве пара восстанавливается по предыдущей структуре |
| `noBackupFilesDir/enotone-signout-<sourceKey>.keys` | хеши ссылок на обложки для идемпотентного повтора выхода |
| `SharedPreferences enotone_account_state` | `firstRunCompleted`, `reauthenticationRequired`, флаг незавершённого выхода и его доказательства |
| `SharedPreferences enotone_private_auth_v1` | только AES-GCM шифротекст входа; ключ — в AndroidKeyStore ([ADR-0080](DECISIONS/ADR-0080-permanent-login-sign-out.md)) |

Плейлисты и обложки плейлистов не имеют пользовательского интерфейса: модель данных готова, экрана нет.

## Историческая модель Room v1–v4

---

# M1 — локальная медиатека /AllMusic

Источник: плоская папка /AllMusic, подтверждённая владельцем; на Huawei через Depth:1 получено **3590** файлов (все прямо внутри).

## Room v1 (Draft)
`RemoteTrackEntity`:
- `id` — SHA-256 (`sourceKey + NUL + encodedPath`), стабильный при обновлениях ETag/тегов, но **не** при переименовании.
- `sourceKey` — SHA-256 от `email.trim().lowercase()`, **сам email не сохраняется в БД**. Это идентификатор для разделения источников, не криптографическая защита e-mail.
- `encodedPath` и `fileName` — приватные значения на устройстве; никогда не логировать.
- `remoteEtag`, `remoteSize`, `remoteModified` — признаки версии; ETag конкретных файлов в XML ещё не проверен на устройстве.
- `title/artist/album/albumArtist` — пока nullable, будут заполняться из аудиотегов, а не папок.
- `lastSeenEpoch` и `missingScans` — будущее безопасное обнаружение отсутствий; **без SQL DELETE** в первом срезе.

`LibrarySnapshotStore.save()` применяет полную выданную страницу атомарно, сохраняет теги только при совпадающей версии файла и не отмечает отсутствия при некорректных элементах. Не считать это полной инкрементальной синхронизацией: пока нет UI-кнопки сохранения, массового чтения тегов, проверенных миграций и удаления.

## Следующие шаги
Room schema v1 экспортирована и зафиксирована; добавить инструментальные тесты БД и тест на 3590 элементов. Затем подключить кнопку безопасного импорта, проверить на Huawei и добавить метаданные аудио.

## Теги: план следующего среза
Для существующей Room v1 предусмотрены nullable поля `title`, `artist`, `album`, `albumArtist`. Аудиотеги извлекаются через системный MediaMetadataRetriever после внедрения ограниченного WebDAV random-access `MediaDataSource`; имя файла используется только как запасное название. Не выводить исполнителя или альбом из пути /AllMusic. Теги записывать только если ETag/size/mtime не изменились между выдачей списка и чтением — иначе переизвлечь на следующем проходе. Схему Room v1 на этом этапе не меняем.

## Сохранение тегов одного файла (ADR-0024)
В базовую таблицу Room v1 добавлен SQL UPDATE с проверкой совпадения источника, пути, ETag, размера, modified и missingScans=0. Сама схема таблицы не изменяется; значения title/artist/album/albumArtist уже существовали. Проверка одного файла на Huawei перед реализацией дала title/artist/album=1, но автоматическая обработка коллекции не разрешена до оптимизации 50 GET / 3,125 МиБ на один пробный файл.

## Подтверждение записанных тегов
Существующие nullable поля title/artist/album/albumArtist проверяются агрегатным SQLite SELECT по sourceKey и present-записям. Возвращаются только количества, без приватных значений. Room v1 неизменна, никаких массовых скачиваний. На Huawei Preview #47 однофайловый UPDATE подтвердил результат 1; тест чтения после перезапуска — отдельный следующий этап (ADR-0025).

## M1 Pilot — Room v2
В таблицу `remote_tracks` добавляется nullable `tagScanCode` (без новой таблицы/удалений). Коды успешных и неподдерживаемых разборов означают завершённую проверку для текущей версии (ETag или size+mtime); `RETRY_NETWORK` остаётся ожидающим повторной попытки. Снимок следующего PROPFIND сохраняет checkpoint только у неизменённого remote version, иначе обнуляет вместе с тегами. SQL UPDATE условно сравнивает id, sourceKey, path, ETag, size, modified и missingScans=0.
