# ADR-0042 — стабильная обновляемая Preview без потери локальной Room

Дата: 2026-11-09. Статус: Draft до exact-HEAD CI и теста Android install-over-install.

## Почему
Debug CI версии `app.enotone.android.preview.rN` используют новый Android package ID для каждого Actions run и ephemeral debug certificate. Это намеренно защищало базы от ошибочной переустановки несовместимой подписью, но делает каждую новую Preview пустой: пользователь вынужден снова вводить логин/пароль и импортировать один Depth:1 список 3590 файлов, а прежний пилот 10+20=30 тегов НЕ переносится.

Android данные приложения привязаны к package ID (UID) и подписи; без сохранённого приватного ключа обновление APK поверх той же базы невозможно. Эти данные нельзя обещать перенести из старых `.preview.rN` в новый пакет. Изъятие базы из других package ID или автоматическое вытаскивание паролей запрещено.

## Выбор
1. Использовать runner debug key с постоянным package ID. Отвергнуто: новый key на каждом run ломает обновление.
2. Публиковать keystore в GitHub или APK; хранить пароль в Gradle. Отвергнуто: риск компрометации всей подписи.
3. Сборка `stablePreview` только из проверенного `main` с одним приватным PKCS12 в GitHub Actions secrets, отдельным package ID и проверкой сертификата. Выбрано.

## Реализация
- Variant `stablePreview`: `app.enotone.android.preview`, видимая метка `enoTone Preview`, non-debuggable, release-like minification. VersionCode основан на монотонном `GITHUB_RUN_NUMBER`. Обычная debug Preview `.preview.rN` продолжает существовать.
- Workflow проверяет обычные Android/Mock WebDAV/JUnit/lint/Room/GMS-контракты. Только после их success и **только** на push/workflow_dispatch `refs/heads/main` при `ENOTONE_STABLE_PREVIEW_ENABLED=true` (repo variable) требует два secrets: `ENOTONE_PREVIEW_KEYSTORE_BASE64` (один PKCS12) и `ENOTONE_PREVIEW_KEYSTORE_PASSWORD`; alias фиксирован `enotone-preview`.
- Публичная repo variable `ENOTONE_PREVIEW_CERT_SHA256` фиксирует SHA-256 сертификата: при неожиданной ротации workflow блокирует APK. Нет secret/variable → нет stable APK; при включении без ввода secrets workflow FAILS CLOSED. Не загружать key file артефактом, не писать его в Git/логи и удалять после job.
- После полной проверки stable APK выводится отдельным artifact. Старые изолированные debug версии не обновляются и не удаляются.
- Идентификатор Android `app.enotone.android` для будущего релиза не используется и не затрагивается.
- Список треков и уже обработанные теги сохраняются только **в рамках нового** `app.enotone.android.preview` между последующими обновлениями. Для первой установки один новый импорт необходим. Ввод email/password пока каждый запуск (RAM-only M0); будущий encrypted Android Keystore opt-in отдельно.

## Проверка
Exact CI PR без ключа должен пройти, не производя stable artifact и не получая доступ к secrets. Smoke после безопасного включения в Settings: установить stable Preview, импортировать список один раз, индексировать 1 тестовый файл по существующим ограничителям, запомнить количество; запустить вторую main-signed stable Preview с новым versionCode **поверх**, без удаления; увидеть ту же Room и checkpoint, воспроизведение M6-B. Не требовать повторный Depth:1. На Huawei убедиться в стабильной подписи. Не включать надёжное хранение пароля без нового ADR/Keystore tests.

## Риски
Потеря keystore или смена cert предотвращает обновление — хранить защищённую offline backup копию отдельно от Git; только пользователь управляет secrets. Случайное удаление приложения стирает приватную Room, Android Backup выключен. Secret-сканирование и certificate pin нужны независимо от зелёного Gradle.
