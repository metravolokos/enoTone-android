# ROADMAP — enoTone Android

**Актуально на 2026-11-13.** Действующая база — [CURRENT_DECISIONS](CURRENT_DECISIONS.md); принятые [ADR-0066](DECISIONS/ADR-0066-indexing-finalization.md) и [ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md). [REVISION_2026-11-10](archive/REVISION_2026-11-10.md), нижние разделы о #235 и ранние Draft — **исторические этапы**, не текущее описание. Журнал аппаратных сообщений — [STATUS](STATUS.md). Не смешивать успешный CI, базовое «Работает» на Huawei и подробные непроведённые тесты.

## Текущий этап — M6-0: технический долг (с 2026-11-11)

[ADR-0074](DECISIONS/ADR-0074-m6-0-tech-debt-plan.md). Визуальное направление заморожено на [ADR-0073](DECISIONS/ADR-0073-graphite-material3.md) (Graphite Material 3, в `main` с PR #78, ждёт Huawei-приёмки). Новые функции — после завершения этапа.

| Шаг | Содержание | Состояние |
|---|---|---|
| M6-0/1 | Порядок в документации: короткие STATUS/KNOWN_ISSUES/TESTING, архив, индекс ADR, AGENTS.md | слит (#79) |
| M6-0/2 | Один `EnoToneDatabase` на процесс; `WAKE_MODE_NETWORK` | слит (#80); Huawei: воспроизведение плавное, без зависаний (владелец, 2026-11-12) |
| M6-0/3 | Сохранение очереди, позиции, shuffle/repeat | слит (#82); Huawei REST-01..04 PASS (см. SCENARIOS) |
| M6-0/4a | `AppContainer`, `LibraryRepository`, `PlaybackConnection`, `LibraryViewModel` медиатеки | слит (#92); NAV-02, REST-01 PASS |
| M6-0/4b-1 | Постоянный вход и полный выход (ADR-0080), `AccountSettingsViewModel`, перенос текущего экрана без изменения вёрстки | слит (#100); Б-1 PASS 0.6.1 |
| M6-0/4b-2 | ViewModel индексации, обложек и диагностики, gateway/AppContainer, единый BackgroundWorkLauncher, разделение экрана ≤400 строк/файл, Preview и Kotlin-тесты | слит (#104); следующий M6-0/4c |
| M6-0/4c | Тонкие foreground-службы; runner-ы и поведенческие JUnit; удаление мёртвого кода тегов | слит (#114); Huawei: обновление PASS 0.6.2-preview.487, пауза/остановка — только JUnit |
| M6-0/5 | 5a: поведенческие тесты Robolectric/Room/Compose (#118); 5b: поэтапный перенос в `:feature:*`/`:core:model`, удалены 5 пустых модулей (ADR-0083, #119) | слит; Huawei smoke PASS 0.6.2-preview.522 (2026-11-13). D2 слит (#122–#124, 2026-11-14); **открыт D3** |
| M6-0/6 | Room v5: длительность, модель плейлистов | слит (#125), Huawei см. UPD-04; нужно для D4 |
| M6-0/7 | FTS4, Collator ru, ё/е; Paging — только замер | Draft feature/m6-0-7-search-sort, ADR-0087; CI/Huawei ожидают, Paging — решение владельца |
| M6-0/8 | Gradle Wrapper, detekt, ktlint, Dependabot, LICENSE | слит (#81); `LICENSE` ждёт решения владельца |
| M6-0/R (= D1) | Ресурсы дизайна Android v2 без изменения экранов: иконка, значки, логотип, Бандит, цвета, шрифты ([ADR-0079](DECISIONS/ADR-0079-design-resources-v2.md)) | слит (#98); иконка — PASS в 0.6.0 |

**Дизайн Android v2 (решение владельца 2026-11-12):** шаги D2–D6 — тема и компоненты, каркас навигации, медиатека, «Сейчас играет», состояния с Бандитом — начинаются после M6-0/5; D4 также ждёт M6-0/6 и M6-0/7. Порядок, предусловия и источники — [DESIGN_PLAN](DESIGN_PLAN.md).

**Обложки (ADR-0067):** мастер до 1200 px в постоянном `filesDir`, одна обложка альбома на всех экранах; версия файла — ADR-0082.

**Процесс (2026-11-13):** [AUDIT_PROTOCOL](AUDIT_PROTOCOL.md), каталог [SCENARIOS](SCENARIOS.md), ревью «что удалить» [REDUCTION_REVIEWS](REDUCTION_REVIEWS.md). Ближайший PR «Качество»: detekt блокирующий с базовой линией, тесты Keystore, удаление Gson и неиспользуемого кода.

**После M6-0:** единый сетевой слой с абстракцией `MusicSource` (R-4.2, критерий — единая обработка ошибок) и экспериментом gzip/даты коллекции для списка; UI плейлистов, экран очереди, экспорт диагностики; затем ИИ (M8) и офлайн/DocumentsProvider (M9).

История визуальных итераций ADR-0068…0072 — заменены ADR-0073, см. [индекс ADR](DECISIONS/README.md).

Исторические разделы M1/M2 и контрольной точки #235 — [archive/ROADMAP_history_2026-11-10_11.md](archive/ROADMAP_history_2026-11-10_11.md).
