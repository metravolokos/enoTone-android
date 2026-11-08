# Состояние на 2026-11-08

**M0 в работе**, Draft PR #1: https://github.com/metravolokos/enoTone-android/pull/1. Main не менялась.

## Реализовано в feature-ветке
- Полное исходное ТЗ, правила AGENTS, ROADMAP, архитектура и ADR-0001—0012.
- Нативный Android/Compose каркас с 15 Gradle-модулями, из которых неиспользуемые пока пустые.
- Экран диагностики WebDAV: root и /AllMusic PROPFIND, опциональные Depth infinity и Range GET, пароли только в RAM, обезличенный результат.
- Mock WebDAV (Python и MockWebServer/JUnit), CI build+lint+tests+запрет GMS.
- GitHub Actions run 42462612361: **Android debug build, unit tests, lint и Python tests прошли**, но итоговый APK **не прошёл** GMS gate: в classes.dex обнаружен маркер com.google.android.gms. Runtime dependency graph без срабатывания; причина в APK требует расследования.
- CI run 42463446577: debug-R8 не убрал GMS-маркер. Выявлена необязательная строка `com.google.android.gms.org.conscrypt` в OkHttp TLS DeferredSocketAdapter. По ADR-0013 испытывается Ktor CIO только для M0; результат CI на новом HEAD пока НЕ подтверждён.

## Не готово
- Зелёный CI и пригодный для установки APK пока НЕ подтверждены.
- Gradle wrapper jar и исполняемые gradlew/gradlew.bat не добавлены; CI использует версионированный setup-gradle.
- Не проверен реальный Cloud.Mail.ru WebDAV на Huawei, гипотезы Range/ETag/Depth.
- Постоянный аккаунт/Keystore, индексатор, медиатека, playback, кэш и AI не реализованы.
- Лицензия исходников требует выбора владельца. Полный аудит license/GMS и UX отчёта ожидает результатов.

## Следующий шаг
Проверить эксперимент Ktor CIO на точном HEAD строгим GMS gate, build, lint, тестами; при успехе получить диагностический APK и отчёт с реального устройства. При неуспехе — исправлять не отключая gate. M1 не начинать до подтверждения владельца.
