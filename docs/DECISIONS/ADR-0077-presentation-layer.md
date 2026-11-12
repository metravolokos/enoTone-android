# ADR-0077: Слой представления — AppContainer, LibraryViewModel и PlaybackConnection

Дата: 2026-11-12 | Статус: действует (#92, 2026-11-12) | Основание: ADR-0008, ADR-0074.

## Контекст

В M6-0/3 экран медиатеки одновременно выполнял Room-запросы, связывался с MediaSessionService, восстанавливал очередь, группировал 3590 треков и рисовал Material 3. Переходы между страницами, поворот/возврат, отсутствие сохранённого входа и фоновое воспроизведение должны остаться прежними. Дизайн ADR-0073 заморожен. Необходимы JVM-тесты без реального WebDAV, Room и плеера.

## Рассмотренные варианты

### Доступ к AppContainer

1. **`(LocalContext.current.applicationContext as EnoToneApplication).container` (выбрано).** Плюсы: явный источник зависимостей, минимум нового Compose-кода, один контейнер на процесс, нет скрытого CompositionLocal по всему приложению. Минусы: экран привязан к типу Application; в превью и тестах следует передавать чистые `state/actions` отдельно.
2. **`staticCompositionLocalOf<AppContainer>`.** Плюсы: удобно подменять весь граф в Compose-тестах; потребитель не зависит от типа Application. Минусы: дополнительный уровень графа, возможность забыть Provider и избыточная косвенность для одного экрана. Вернуться при массовом переносе экранов в `:feature:*` (M6-0/5).

### Форма связи с плеером

1. **`StateFlow<EnoTonePlaybackService?>`.** Плюсы: минимальный адаптер и копирование существующих вызовов. Минусы: ViewModel привязана к Android Binder и конкретному Media3-сервису, сложно подменить воспроизведение в JVM-тестах.
2. **`StateFlow<PlaybackSnapshot>` и интерфейс `PlaybackConnection` (выбрано).** Плюсы: immutable-снимки и фейк без Android Binder; listener, bind/unbind и 500-мс позиционное обновление находятся вне UI; единственная точка адаптации ошибок и состояния Media3. Минусы: копирование небольшого снимка и ручная синхронизация действий; сервис остаётся источником истины очереди и позиции.

### Декомпозиция UI

1. Оставить всю логику в Composable с `remember`: меньше изменений, но источник технического долга сохраняется.
2. Перенести эффекты и действия в `LibraryViewModel` + `LibraryRepository`, оставить UI с параметрами `LibraryUiState` и `LibraryScreenActions` (выбрано): тестируемость и отсутствие I/O в Compose при сохранении пиксельной разметки.

## Решение

- `EnoToneApplication` создаёт **один** ручной `AppContainer` на процесс. Он предоставляет существующий `EnoToneDatabase.getInstance`, `RoomLibraryRepository`, общий `ArtworkPreviewRepository`, `PlaybackConnection`, настраиваемые `io/main` диспетчеры. Hilt/Koin/GMS и кодогенерация DI не используются.
- `RoomLibraryRepository` в `:data:library` владеет запросом `allForSource`, формированием `OfflineTrackCatalog` и `QueueTrack`, выборочным `AlbumTagRefresher` и чтением checkpoint очереди. Все suspend-операции переключаются на переданный IO.
- `LibraryViewModel` предоставляет `StateFlow<LibraryUiState>` каталога, фильтров и действий медиатеки; **не восстанавливает очередь и не владеет привязкой к службе**. Activity-scoped `PlayerViewModel` — единственный инициатор восстановления и владелец состояния мини-плеера/Now Playing. Восстановление разрешено только с сохранённым подходящим входом через `CredentialsProvider`, при совпадении `sourceKey` и после `QueueStateRestorer.filter`; RAM-only вход не разрешает автоматического восстановления. До первого Play новых сетевых GET нет.
- `AndroidPlaybackConnection` привязывается на срок жизни Activity-scoped `PlayerViewModel`, слушает Media3 и отвязывается без `stopPlayback`: музыка остаётся в MediaSessionService. Единый `EnoToneMiniPlayer` с прежней геометрией Material 3 используется на Home, медиатеке и в Настройках. Принудительный переход Home → Library ради очереди удалён. Минимальные правки `MainActivity` и диагностической панели — исключение из границы M6-0/4b по Huawei-дополнению; остальные настройки и индексаторы остаются для M6-0/4b–4c.
- `OfflineLibraryScreen` — входная точка со `collectAsStateWithLifecycle`; разметка вынесена в `LibraryScreenContent`, поддерживаются четыре Compose Preview на фиктивном состоянии.

## Последствия

Прямые зависимости от Binder и Room исчезают из Composable экрана; логика и допуски отображения проверяются через фейковые интерфейсы. Настройки, сервисы индексации и обложек остаются прежними до следующих PR. Поверхностная проверка строк в прежнем файле не доказывает поведение; заменяется JUnit/Compose UI и аппаратным smoke. Текущий шаг 4a не равнозначен завершению M6-0/4.

## Проверка и пересмотр

JUnit с `kotlinx-coroutines-test`: данные/пусто/ошибка, поиск без учёта регистра, навигация и выбор контекста очереди, отсутствие самостоятельного восстановления в `LibraryViewModel`. JVM-тесты `QueueStateFileStore` проверяют файловый rollback с fault injection и коды инспекции; отдельная регрессия полной цепочки ExoPlayer/Robolectric пока открыта в KNOWN_ISSUES. Чистый JVM-тест конверсии Room-entity → каталог/очередь. CI exact HEAD: сборка, все JUnit, lint, detekt 0 новых замечаний, GMS-gate, APK. Ручной Huawei smoke по `docs/TESTING.md` **ещё не засчитан**. Пересмотреть форму связки при переходе на MediaController или переносе в `:feature:*`.
