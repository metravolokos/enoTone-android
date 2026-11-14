# MODULE_MAP — быстрый вход в код enoTone

**Назначение:** найти входную точку задачи за минуты, не перечитывая историю проекта. Это навигация, а не исчерпывающее описание архитектуры. Структура проверена на main от 2026-11-14; после переноса классов карту обновлять в соответствующем PR.

| Подсистема | Стартовые классы / модули | Правила / сценарии |
| --- | --- | --- |
| Android shell, переходы и стек деталей | app/.../AppShell.kt, AppNavigator.kt, MainActivity.kt | ADR-0086, NAV-* |
| Медиатека, фильтры и группировка | app/.../LibraryViewModel.kt, LibraryScreenContent.kt, data/library/.../OfflineLibraryGrouping.kt | ADR-0045, ADR-0051, R-6.2/R-6.3, SCENARIOS |
| Страница альбома и действия песни | app/.../AlbumDetailHeader.kt, TrackActionsHost.kt, ArtistDetailScreen.kt; core/ui/.../EnoTrackRow.kt | DESIGN_PLAN D4, ACT-*, ARS-* |
| Обложки и постоянный мастер | app/.../ArtworkPreviewRepository.kt, AlbumArtworkTile.kt, TrackArtworkThumbnail.kt; feature/library/.../AlbumArtworkCatalog.kt | ADR-0067, ADR-0082, ART-* |
| Вход, аккаунты, безопасность | app/.../LocalCredentialVault.kt, AccountSettingsViewModel.kt, AndroidAccountSignOutPort.kt | ADR-0080, ACC-*, OUT-* |
| Импорт/теги/индексация | data/library/.../LibraryRepository.kt, ManagedTagIndexSession.kt, ParallelTagIndexer.kt; app/.../IndexingRunner.kt | ADR-0066, IDX-* |
| Room и миграции | core/database/.../EnoToneDatabase.kt, RemoteTrackDao.kt; core/database/schemas/ | ADR-0085 и миграционные тесты, UPD-* |
| WebDAV PROPFIND и диагностика сети | core/network/.../FlatLibraryWebDavClient.kt, WebDavDiagnostics.kt | R-4.2/R-5.4, TESTING mock WebDAV |
| WebDAV Range-теги | data/library/.../WebDavRangeReader.kt, FastAudioTagReader.kt | R-5.4/R-6.2, тесты Range |
| Media3, очередь, shuffle/repeat | playback/.../EnoTonePlaybackService.kt, PreviewAudioPlayback.kt, QueueEditPlanner.kt, PersistedQueueState.kt | ADR-0075, ADR-0089, PLAY-*, QED-*, REST-* |
| Аудиокэш | playback/.../TransientMediaCache.kt, TransientCachePolicy.kt | R-6.5, PLAY-* |
| Плейлисты (модель) | data/playlist/.../PlaylistRepository.kt, core/database (Playlist DAO) | ADR-0085, PL-*; UI ещё не завершён |
| Дизайн Android v2 | core/ui/src/main/java/.../theme и component; docs/DESIGN_PLAN.md | ADR-0084, 0086, 0088, design-repo |
| Now Playing | feature/player/.../FeatureNowPlayingScreen.kt, app/.../PlayerViewModel.kt | DESIGN_PLAN D5, PLAY-* |
| Настройки и диагностика | app/.../SettingsScreen.kt, DiagnosticsViewModel.kt, SettingsIndexingSection.kt | ADR-0081, ACC-*, ART-* |
| Сборка и качество | .github/workflows/ci.yml, gradle, tools/, config/detekt | TESTING, AUDIT_PROTOCOL |

Все краткие пути вида app/... обозначают относительные пути в соответствующем Gradle-модуле. Чтобы получить точный полный путь, открыть указанное имя файла в дереве репозитория; не угадывать API/методы по карте.

## Проверка области перед работой

1. Прочитать GitHub Issue, содержащую идентификаторы требуемых сценариев и запреты.
2. Открыть только соответствующую строку карты и связанный ADR/раздел SCENARIOS.
3. Проследить путь действия: UI → ViewModel → служба/репозиторий → хранилище → обновление UI и восстановление.
4. Расширить изучение только при реальной зависимости. Не менять смежную подсистему без отражения в Issue.

**Общие ограничения:** не трогать пользовательскую Room или Stable Preview, не копировать мастер-обложки, не добавлять GMS/телеметрию, не читать и не логировать секреты, не запускать полный импорт без разрешения владельца. Полный перечень — AGENTS.md.
