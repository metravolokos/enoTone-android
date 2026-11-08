# Состояние на 2026-11-08

**M0 в работе**, Draft PR #1: https://github.com/metravolokos/enoTone-android/pull/1. Main не менялась.

## Реализовано в feature-ветке
- Полное исходное ТЗ, правила AGENTS, ROADMAP, архитектура и ADR-0001—0012.
- Нативный Android/Compose каркас с 15 Gradle-модулями, из которых неиспользуемые пока пустые.
- Экран диагностики WebDAV: root и /AllMusic PROPFIND, опциональные Depth infinity и Range GET, пароли только в RAM, обезличенный результат.
- Mock WebDAV (Python и MockWebServer/JUnit), CI build+lint+tests+запрет GMS.
- GitHub Actions run 42462612361: **Android debug build, unit tests, lint и Python tests прошли**, но итоговый APK **не прошёл** GMS gate: в classes.dex обнаружен маркер com.google.android.gms. Runtime dependency graph без срабатывания; причина в APK требует расследования.
- CI run 42463446577: debug-R8 не убрал GMS-маркер. Выявлена необязательная строка `com.google.android.gms.org.conscrypt` в OkHttp TLS DeferredSocketAdapter. По ADR-0013 испытывается Ktor CIO только для M0; CI #8 не прошёл до компиляции: Maven Central перенаправил Kotlin compiler 2.3.10 на GitHub, где вернулся HTTP 504. Для следующего CI добавлена строго проверяемая по SHA-256 резервная загрузка этого JAR. Результат нового HEAD пока НЕ подтверждён.

- CI #9 (42464862744): Gradle, Kotlin compiler, Android build, тесты, lint и дерево зависимостей прошли; строгий APK gate обнаружил строку `com.google.android.gms.provider.action.PICK_IMAGES`, известный optional fallback AndroidX Activity Photo Picker. Эксперимент Ktor CIO успешно устранил предыдущий TLS marker, но с отключённым debug-R8 проявился другой. Повторно включено R8 для debug без изменения APK gate; CI на новой ревизии не подтверждён.

## Не готово
- Зелёный CI и пригодный для установки APK пока НЕ подтверждены.
- Gradle wrapper jar и исполняемые gradlew/gradlew.bat не добавлены; CI скачивает фиксированный Gradle 8.13 с проверкой SHA-256; Gradle Wrapper отсутствует.
- Не проверен реальный Cloud.Mail.ru WebDAV на Huawei, гипотезы Range/ETag/Depth.
- Постоянный аккаунт/Keystore, индексатор, медиатека, playback, кэш и AI не реализованы.
- Лицензия исходников требует выбора владельца. Полный аудит license/GMS и UX отчёта ожидает результатов.

## Следующий шаг
Проверить Ktor CIO + debug-R8 на точном HEAD строгим GMS gate, build, lint, тестами; при успехе получить диагностический APK и отчёт с реального устройства. При неуспехе — исправлять не отключая gate. M1 не начинать до подтверждения владельца.
