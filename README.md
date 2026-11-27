# enoTone — Android

**enoTone** — нативный Android-аудиоплеер на Kotlin и Jetpack Compose для личной музыки из Cloud.Mail.ru через HTTPS WebDAV. Основная проверенная структура — плоская папка `/AllMusic`. Приложение не зависит от Google Play Services и Firebase, работает на смартфонах без сервисов Google. Старый Flutter-прототип не используется.

**Срез: 23 ноября 2026, `main` `59dd26a4`, версия приложения `0.6.2`.** Проект в разработке, это не публичный релиз. Оперативные факты — [STATUS](docs/STATUS.md), известные ограничения — [KNOWN_ISSUES](docs/KNOWN_ISSUES.md), план — [ROADMAP](docs/ROADMAP.md).

## Реализовано в `main`

- **WebDAV:** проверка входа, `PROPFIND Depth: 1`, `Range GET` и инкрементальный импорт/обновление медиатеки. Метаданные MP3/FLAC индексируются в фоне, Room хранит локальный каталог.
- **Медиатека:** «Домой», «Песни», «Альбомы», «Артисты» и «Авторы альбомов», подробности альбомов и исполнителей, поиск по подготовленным в памяти ключам с русской сортировкой. **FTS4 отменён** ([ADR-0091](docs/DECISIONS/ADR-0091-no-fts-memory-search.md)), текущая схема — **Room v7**. Плейлисты и «Избранное» готовы на уровне данных, пользовательского редактора нет.
- **Воспроизведение:** Media3 / MediaSessionService — фоновое воспроизведение, уведомление, очередь из контекста медиатеки, repeat/shuffle, seek, сохранение очереди и позиции, «Играть следующей» / «В конец очереди», предел активной очереди 100 песен ([ADR-0099](docs/DECISIONS/ADR-0099-queue-limit-100.md)). Временный аудиокэш и предзагрузка следующего трека не являются постоянным офлайн-хранилищем.
- **Обложки:** один постоянный WebP-мастер до 1200 px в приватном `filesDir`, общая обложка альбома на всех экранах, два режима загрузки и ручная очистка ([ADR-0067](docs/DECISIONS/ADR-0067-permanent-album-artwork.md)).
- **Интерфейс:** темы «Графит»/«Фарфор», навигация с нижней панелью и мини-плеером, единый fade-through страниц и отдельная вертикальная шторка полного плеера ([ADR-0095](docs/DECISIONS/ADR-0095-simple-motion.md)). Интерфейс полностью переведён на русский и английский.
- **Вход:** постоянно хранится в Android Keystore (AES-GCM); «Выйти» удаляет данные аккаунта в утверждённом порядке ([ADR-0080](docs/DECISIONS/ADR-0080-permanent-login-sign-out.md)). Есть подписанный канал **Stable Preview** для обновления поверх установленной версии без потери данных.

## Что ещё не готово

Вне текущей реализации: UI плейлистов, экран редактирования очереди, источники SAF, постоянный офлайн-аудиокэш, интерфейсы ИИ, теги других форматов. Подробнее — [KNOWN_ISSUES](docs/KNOWN_ISSUES.md).

## Сборка и проверка

Требуются **JDK 17, Android SDK 37**; `minSdk 26`, `targetSdk 36`. Проект — Gradle Kotlin DSL, модули описаны в [ARCHITECTURE](docs/ARCHITECTURE.md).

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest lintDebug detekt
python3 design/tools/sync_android.py --check
```

На Windows используйте `gradlew.bat`. Полный набор проверок и ограничения — в [TESTING](docs/TESTING.md).

**Установка:** `debug` получает отдельный идентификатор `app.enotone.android.preview.rN` и эфемерную подпись, поэтому **не обновляет** Stable Preview. Канал `app.enotone.android.preview` подписан постоянным ключом CI. Учётные данные, имена файлов и личные пути нельзя публиковать в отчётах.

## Документация

- **Русская:** [текущие решения](docs/CURRENT_DECISIONS.md) · [состояние](docs/STATUS.md) · [план](docs/ROADMAP.md) · [ограничения](docs/KNOWN_ISSUES.md) · [сценарии](docs/SCENARIOS.md)
- **English:** [documentation index](docs/en/README.md)
- Архитектура: [ARCHITECTURE](docs/ARCHITECTURE.md) · [карта модулей](docs/MODULE_MAP.md) · [индекс ADR](docs/DECISIONS/README.md)
- Процесс: [тестирование](docs/TESTING.md) · [GitHub-процесс](docs/WORKFLOW.md) · [правила агентов](AGENTS.md) · [дизайн](design/README.md)
- История: [CHANGELOG](CHANGELOG.md) · [исходное ТЗ](docs/ORIGINAL_PROMPT_AllMusic_Player.md) и [уточнения владельца](docs/USER_SPEC_RU.md) — исторические источники, уточняемые более поздними ADR.

**Лицензия собственного кода пока не утверждена владельцем; публичный выпуск не объявлен.**
