# enoTone — Android

Открытый проект Android-аудиоплеера **enoTone** для папки `/AllMusic` в Cloud.Mail.ru по WebDAV. Работает без Google Play Services. Не является переносом прежнего Flutter-кода.

**Статус: M0, не завершён.** Проверенный APK и реальная проверка WebDAV ещё не получены.

Документация: [AGENTS.md](AGENTS.md), [архитектура](docs/ARCHITECTURE.md), [этапы](docs/ROADMAP.md), [состояние](docs/STATUS.md), [вопросы](docs/OPEN_QUESTIONS.md).

Для сборки требуется JDK 17, Android SDK 36. После добавления Gradle wrapper: `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`.

Документация преимущественно на русском; идентификаторы в коде английские. Индивидуальная разработка с участием ИИ-агентов. Лицензия исходного кода пока не выбрана владельцем.
