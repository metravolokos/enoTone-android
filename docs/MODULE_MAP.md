# MODULE_MAP — быстрый вход в код enoTone

Короткий указатель для новых агентов. Он не заменяет [ARCHITECTURE](ARCHITECTURE.md), [CURRENT_DECISIONS](CURRENT_DECISIONS.md), ADR и задачу в GitHub Issue. Начинать с соответствующей строки, а не со всего репозитория.

| Область задачи | Минимальные точки входа | Контракты и тесты |
|---|---|---|
| Приложение и навигация | `:app/AppContainer.kt`, `AppShell.kt`, `AppNavigator.kt`, `MainActivity.kt` | ADR-0077, ADR-0086; NAV/REST |
| Медиатека и страницы | `:app/LibraryViewModel.kt`, `LibraryScreenContent.kt`, `AlbumDetailHeader.kt`; `:data:library` | NAV, LIB, ART; не выполнять полный импорт ради UI |
| Теги, импорт, фоновая индексация | `:data:library/LibraryRepository.kt`, `LibrarySnapshotPlanner.kt`, `ManagedTagIndexSession.kt`; `:app/IndexingRunner.kt` | ADR-0066; IDX; без Wi-Fi only |
| Постоянные обложки | `:app/ArtworkPreviewRepository.kt`, `ArtworkPreparationRunner.kt`, `TrackArtworkThumbnail.kt`; `:feature:library/AlbumArtworkCatalog.kt` | ADR-0067, ADR-0082; ART; мастер ≤1200 px, filesDir |
| Воспроизведение/MediaSession | `:playback/PreviewAudioPlayback.kt`, `EnoTonePlaybackService.kt`, `PlaybackContinuationPolicy.kt`; `:app/PlayerViewModel.kt` | PLAY/REST |
| Редактирование и восстановление очереди | `:playback/QueueEditPlanner.kt`, `QueueStateFileStore.kt`, `PersistedQueueState.kt`; `:app/TrackActionsHost.kt`, `PlaybackConnection.kt` | ADR-0075, ADR-0089; QED/REST |
| Временный аудиокэш | `:playback/TransientMediaCache.kt`, `TransientCachePolicy.kt`, `WebDavMedia3DataSource.kt` | Отличать от постоянного кэша обложек |
| Room и плейлисты | `:core:database` и `:data:playlist/PlaylistRepository.kt` | ADR-0085, миграции без потерь, UI ещё отдельно |
| Сеть и WebDAV | `:core:network/FlatLibraryWebDavClient.kt`, `:data:library/WebDavRangeReader.kt`, `:playback/WebDavMedia3DataSource.kt` | HTTPS; унификация — отдельный ADR |
| Android v2 | `:core:ui`, `:feature:player`, экраны в `:app` | ADR-0084, ADR-0090; design/README.md, design/docs/DECISIONS_ANDROID_V2_UI.md, design/tools/sync_android.py |
| Настройки/секреты | `:app/SettingsScreen.kt`, `AccountSettingsViewModel.kt`, `LocalCredentialVault.kt`, `AndroidAccountSignOutPort.kt` | ADR-0080/0081; секреты не логировать, Keystore последним |
| CI/тесты/выпуск | `.github/workflows/ci.yml`, `tools/`, `docs/TESTING.md`, `src/test` | Exact HEAD, detekt baseline, GMS gate |

**Маршрут:** Issue → AGENTS.md → этот указатель → только связанные ADR и сценарии → классы/тесты → Draft PR. При обнаружении зависимости разрешено изучить дополнительные файлы, чтобы избежать регрессии.
