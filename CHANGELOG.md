# CHANGELOG

## Unreleased — M1
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

## Unreleased — M1 pilot fault reporting (Draft PR #20)
- Раздельная офлайн-статистика успешных, временных ошибок, постоянных ошибок и ещё не проверенных файлов без нового WebDAV-запроса.
- Показывается только безопасный этап/код последнего сбоя и HTTP статус; значения исключений/путей/тегов не отображаются.
- Временные сбои можно повторить следующей ручной попыткой; постоянные пропускаются до изменения версии файла. Добавлены синтетические JVM тесты, лимиты пилота без изменений.

## Unreleased — M2 local tracks (Draft)
- Первый read-only экран «Треки»: список из Room без WebDAV, поиск по названию, исполнителю, альбому.
- Файлы без тегов показываются по имени файла; загружаются только существующие локальные метаданные, без фоновой индексации.
- JVM тестирование построения и поиска по 3590 синтетическим трекам; ADR-0030 и инструкция проверки.

## Unreleased — M3 one-track playback (Draft)
- По нажатию на трек в офлайн-медиатеке пробный Media3 ExoPlayer GET выбранного файла из Cloud.Mail.ru WebDAV; пароль только RAM и заголовок, редиректы запрещены; после блокировки GMS gate у OkHttp заменено на собственный HttpURLConnection Media3 DataSource со строгой проверкой HTTP 206 / Content-Range.
- Кнопки пауза/продолжить/стоп, безопасные коды ошибок, автоматическая пауза при сворачивании. Foreground-only без очереди и кэша.
- Новый тест валидатора WebDAV URL и ADR-0031; реальная совместимость на Huawei пока не подтверждена.

## Unreleased — M3 background MediaSession (Draft)
- Плеер вынесен из Compose-экрана в `MediaSessionService` с типом foreground mediaPlayback и стандартным медиауведомлением Android.
- Приватный локальный Binder передаёт секреты только в RAM; экран при закрытии отключается от службы, не останавливая воспроизведение.
- Добавлен contract-test AndroidManifest + жизненного цикла для безопасности; фон и медиа-кнопки на Huawei пока не проверены.

## Unreleased — M4-A local context queue (Draft)
- Следующий и предыдущий трек по текущему списку/поиску из Room, счётчик позиции, автоматический переход после завершения.
- Не более 10000 локальных записей, все данные очереди и credentials только в RAM MediaSessionService, без дополнительных PROPFIND и без массового скачивания.
- Синтетические JVM-тесты границ и 3590 треков, расширен contract-test службы; ADR-0033.

## Unreleased — M4-B native Media3 skip (Draft)
- Очередь поиска/списка передаётся ExoPlayer как настоящий плейлист. Next/previous/автопереход обеспечиваются стандартными Player-командами MediaSession и Android, а не вторым вручную изменяемым индексом.
- Для каждого трека новый безопасный источник выбирает его собственные размер и ETag, с прежним HTTPS-only/206/no-redirect и GMS-free gate.
- Синтетический тест маршрутизации 3590 элементов, тест контракта MediaSession и ADR-0034.

## Unreleased — M4-C repeat/shuffle (Draft)
- Режим повтора (выкл. → текущий трек → очередь) и включаемый случайный порядок через native Media3 Player.
- Состояние кнопок синхронизируется через Media3 listener и восстанавливается при возврате к экрану; лишние GET, новый кэш и миграции Room не добавлены.
- JVM-тесты повтора, contract-test и ADR-0035; Huawei ещё не проверен.

## Unreleased — M5-A transient current cache (Draft)
- Media3 SimpleCache/CacheWriter в app-private cacheDir: полная загрузка текущего файла отдельно от play/pause, read-only cache при воспроизведении, LRU 512 МиБ.
- Один файл до 256 МиБ при резерве 128 МиБ; ETag-versioned opaque cache keys, локальный прогресс в KiB, очистка на закрытии службы/новом запуске.
- Stream fallback при проблемах кэша; предзагрузка следующего и бесшовный switch уже открытого соединения — не здесь.
- JVM тесты границ/ключей, Python service contract, ADR-0036. Реальное поведение на Huawei ещё не проверено.

- M5-A startup performance fix (Draft): фоновой полнофайловый CacheWriter больше не запускается до первого Media3 READY; отложен на 900 мс и отменяется при BUFFERING/смене трека. Пауза не блокирует фоновой download. Регрессия Huawei #96 ~6 с подключения + 1 с буферизации, новая версия требует проверки на устройстве.

- M5-A A/B startup diagnostic: после неудачной попытки ускорить Preview #102 добавлены контроль cache-off по умолчанию (без SimpleCache), эксперимент cache-on и пять числовых длительностей до первого звука. Перенесено повторное чтение Room/валидация 3590 путей с каждого нажатия на открытие медиатеки. Пока не испытано на Huawei.

- M5-A startup investigation (#111, Huawei): на 3590 записях ~3,3 с синхронного Media3 setup и лишь ~0,44–0,59 с до READY; cache-off и cache-on почти одинаковы. Эксперимент: стартовать Media3 с выбранного ±2, после READY добавлять все остальные треки порциями по 32 без изменения итоговой последовательности. Добавлены цифровые фазы синхронного setup и прогресс постановки очереди, синтетические JVM тесты на 3590 элементов. До CI/Huawei — Draft.

## Unreleased — M5-B single next preload (Draft)
- После 100% текущего файла один serial Media3 CacheWriter скачивает следующий MediaItem согласно native Media3 order (shuffle/repeat, staged timeline); Pause не отменяет загрузку.
- Статусы current/next независимы, Stop и выбор другого трека отменяют оба worker, лимиты диска и строгий WebDAV GMS-free источник сохранены.
- JVM выбор следующего по Media3 ID (3590 синтетических записей), security contract, ADR-0037. До CI/Huawei — Draft.

## Unreleased — M6-A handoff and clean UI with default Material colors (Draft)
- Private anonymous measurement of track changes (button/auto/MediaSession), with numeric time-to-READY and time-to-isPlaying plus pre-cached-next boolean; no identifiers, telemetry or logs.
- Folded Room/Media3 debug traces behind optional diagnostics; default cache-on; added two current/next progress bars.
- After Huawei Preview #129 passed functional smoke, owner rejected carrying the old project's palette into Android: restored unmodified standard MaterialTheme colors; custom palette is deferred pending explicit Android design approval.
- Kotlin tracker tests, private service/static contract, default-theme contract, ADR-0038. Huawei #129 functions confirmed; exact-HEAD CI after palette revert pending.

## Unreleased — docs: сверка оригинального ТЗ и актуальной дорожной карты (2026-11-09)
- Обновлён ROADMAP по подтверждённым Huawei/CI этапам M0–M6-A, явно отделены принятые срезы от неполных требований M1/M2/M3/M4/M5 и будущих M7–M9.
- Исправлены устаревшие вопросы об ожидании приёмки M0, зафиксировано ожидание аппаратной проверки seek в отдельном Draft PR #30 (CI #138 зелёный). Никаких изменений runtime, APK или сохранённой Room.

## Unreleased — M6-B current-track seek (Draft)
- Доступный Material3 Slider для перемотки внутри текущего Media3 трека, позиция и длительность с обновлением раз в 500 мс.
- Сервис принимает только ограниченную цель seek при известной длительности и доступной Media3 seek command; единственный seek отправляется после отпускания ползунка. Existing GMS-free HTTPS Range + temporary cache и быстрый staged-плейлист не изменены.
- Pure Kotlin policy/JVM tests, Python security/UI contract, ADR-0039; Huawei smoke pending. Остаются стандартные цвета Material Design, прежние палитры не импортируются.

- fix(m6-b): после отрицательного Huawei Preview #138 кнопки Next/Previous возобновляют Media3 воспроизведение, даже если предыдущий был на Pause/ENDED; при неожиданном ENDED добавлено одно отложенное guarded auto-next без нарушения явного Pause, аудиофокуса или Repeat One. Новые unit/static контракты. Проверка на Huawei после нового CI **ожидается**.
