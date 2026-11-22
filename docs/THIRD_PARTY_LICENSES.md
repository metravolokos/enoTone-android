# THIRD PARTY LICENSES

Предварительный набор зависимостей и их лицензий. **Не заменяет** полный SPDX-аудит перед публикацией релиза.

Актуально на 2026-11-22, `main` `0f5e9881`, версия `0.6.2`. Версии сверены с `gradle/libs.versions.toml`.

## Runtime — входит в APK

| Компонент | Версия | Лицензия |
|---|---|---|
| Kotlin | 2.4.21 | Apache 2.0 |
| AndroidX Activity / Lifecycle | 1.13.0 / 2.9.4 | Apache 2.0 |
| Jetpack Compose (BOM) | `2026.09.00` | Apache 2.0 |
| Media3 (ExoPlayer, MediaSession) | 1.11.1 | Apache 2.0 |
| Room | 2.8.5 | Apache 2.0 |
| Coroutines | 1.11.0 | Apache 2.0 |
| OkHttp | 5.5.1 | Apache 2.0 |
| Ktor CIO | 3.6.1 | Apache 2.0 |
| Haze (`dev.chrisbanes.haze`) | 1.7.2 | Apache 2.0 |
| `org.json` | `20250517` | Public domain (Android-совместимая реализация) |

**`security-crypto` не используется:** вход хранится в Android Keystore AES-GCM вручную ([ADR-0080](DECISIONS/ADR-0080-permanent-login-sign-out.md)).

## Только сборка и тесты — в APK не входят

| Компонент | Версия | Лицензия |
|---|---|---|
| Android Gradle Plugin | 9.5.1 | Apache 2.0 |
| Gradle Wrapper | 9.9.1 | Apache 2.0 |
| KSP | 2.3.13 | Apache 2.0 |
| detekt + ktlint (detekt-formatting) | 1.23.8 | Apache 2.0 |
| Robolectric | 4.17 | Apache 2.0 |
| Roborazzi | 1.79.0 | Apache 2.0 |
| JUnit | 4.13.2 | Eclipse Public License 1.0 |
| `kotlinx-coroutines-test` | 1.11.0 | Apache 2.0 |
| AndroidX Test (core / junit) | 1.7.0 / 1.3.0 | Apache 2.0 |

## Ресурсы дизайна

- **Шрифты:** Onest (The Onest Project Authors) и Unbounded (The Unbounded Project Authors) — SIL Open Font License 1.1. Вариативные TTF перенесены из `design/` скриптом `design/tools/sync_android.py`.
- **Иконка, значки, логотип, изображения Бандита** — собственные ресурсы проекта из `design/` (до 2026-11-15 — отдельный репозиторий `metravolokos/enotone-design`), [ADR-0090](DECISIONS/ADR-0090-design-in-app-repo.md).

## Удалённые зависимости

- **Gson** — удалён в PR #121: очередь хранится через встроенный `org.json`.

## Открытый вопрос

Лицензия собственного исходного кода проекта ещё не выбрана владельцем; файла `LICENSE` нет. Без него невозможны публикация исходников и F-Droid — см. [OPEN_QUESTIONS](OPEN_QUESTIONS.md) и [KNOWN_ISSUES](KNOWN_ISSUES.md).
