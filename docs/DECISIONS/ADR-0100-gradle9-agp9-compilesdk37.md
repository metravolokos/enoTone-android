# ADR-0100 — Переход на Gradle 9 / AGP 9 / compileSdk 37

**Дата:** 2026-11-19. **Статус:** реализовано в PR «Шаг 2 из 3 (бывший шаг 3)», Issue #203 (DEPS-01, шаг 3). **Риск:** R. **Связано:** ADR-0001, ADR-0076, ADR-0098, ADR-0099, Issue #203, ревью #214 (N-1).

## Контекст

Проект собирается на Gradle 8.13 + AGP 8.13.2 + compileSdk 36. Compose 1.12 и более новые версии AndroidX-библиотек, которые уже попадают в граф зависимостей через BOM `2026.06.00`, требуют компиляции против API 37, а поддержка API 37 в AGP начинается с ветки 9.2. Владелец изменил порядок шагов DEPS-01 и поставил обновление тулчейна (бывший шаг 3) перед остальными обновлениями зависимостей — иначе любое обновление AndroidX-библиотек упирается в compileSdk.

Версии, на которые выполнен переход, — только стабильные релизы (альфы и беты в проекте запрещены без отдельного решения владельца):

| Компонент | Было | Стало | Минимум для compileSdk 37 / официальный источник |
|---|---|---|---|
| Gradle | 8.13 | **9.9.1** | требование AGP 9.5.1 — не ниже 9.6.0; [gradle.org/release-checksums](https://gradle.org/release-checksums/) |
| AGP | 8.13.2 | **9.5.1** | API 37 поддерживается с AGP 9.2; [developer.android.com — AGP release notes](https://developer.android.com/build/releases/gradle-plugin) |
| compileSdk | 36 | **37** | `platforms;android-37.0` |
| targetSdk | 36 | **36** (без изменений) | — |
| minSdk | 26 | **26** (без изменений) | — |
| Kotlin (встроенный в AGP) | 2.4.21 | **2.4.21** (без изменений) | Gradle 9.9.1 содержит Kotlin 2.4.21; [docs.gradle.org/9.9.1](https://docs.gradle.org/9.9.1/release-notes.html) |
| KSP | 2.3.13 | 2.3.13 (без изменений) | [github.com/google/ksp/releases](https://github.com/google/ksp/releases) |
| Room | 2.8.5 | 2.8.5 (без изменений) | [developer.android.com/jetpack/androidx/releases/room](https://developer.android.com/jetpack/androidx/releases/room) |
| Roborazzi | 1.79.0 | 1.79.0 (без изменений) | [github.com/takahirom/roborazzi/releases](https://github.com/takahirom/roborazzi/releases) |
| detekt / ktlint | 1.23.8 | 1.23.8 (без изменений) | [github.com/detekt/detekt/releases](https://github.com/detekt/detekt/releases) |
| Compose compiler (KGP-плагин) | 2.4.21 | 2.4.21 (без изменений) | [developer.android.com/jetpack/androidx/releases/compose-compiler](https://developer.android.com/jetpack/androidx/releases/compose-compiler) |

Версии KSP, Room, Roborazzi и detekt в этом PR намеренно не двигаются: шаг касается только тулчейна сборки, а их обновление — предмет отдельных шагов DEPS-01. Это же ограничивает радиус отказа.

## Варианты

1. **Остаться на Gradle 8.13 / AGP 8.13.2 / compileSdk 36 и отложить переход.** Плюсы: нулевой риск в этом PR, ничего не меняется. Минусы: блокирует обновление Compose 1.12+ и AndroidX-библиотек из BOM `2026.06.00`; compileSdk 36 не даёт собрать зависимость, требующую API 37, — следующие шаги DEPS-01 становятся невыполнимыми. Кроме того, отсрочка увеличивает разрыв и цену перехода. **Отклонено.**
2. **Перейти на Gradle 9 + AGP 9 + compileSdk 37 сейчас, версии библиотек не трогать.** Плюсы: снимает блокировку обновлений зависимостей, разрыв минимален, отдельный шаг с ограниченным радиусом. Минусы: AGP 9 несёт встроенный Kotlin и меняет поведение R8, из-за чего артефакт меняется даже без изменения версий библиотек (см. ниже). **Выбрано.**
3. **Перейти на Gradle 9 + AGP 9 и одновременно обновить KSP/Room/Roborazzi/detekt.** Плюсы: одна волна обновлений. Минусы: смешивает причины изменений, затрудняет локализацию регрессий и нарушает порядок шагов, заданный владельцем. **Отклонено.**

## Решение

### 1. Gradle 9.9.1 и проверка дистрибутива

`gradle-wrapper.properties` указывает на `gradle-9.9.1-bin.zip` и содержит `distributionSha256Sum=d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228`. Контрольная сумма сверена с официальной страницей `gradle.org/release-checksums`. Wrapper обновлён штатной задачей `gradle wrapper --gradle-version 9.9.1 --distribution-type bin`, поэтому `gradle-wrapper.jar`, `gradlew` и `gradlew.bat` — официальные файлы 9.9.1, а не частично отредактированные вручную.

CI переведён на тот же дистрибутив: шаг «Gradle 9.9.1 (verified, fallback mirrors)» скачивает ZIP и проверяет ту же SHA-256 с трёх зеркал, шаг «Install Android SDK 37» ставит `'platforms;android-37.0' 'build-tools;36.0.0'`.

### 2. AGP 9.5.1 и встроенный Kotlin

AGP 9 поставляет поддержку Kotlin сам, поэтому плагин `org.jetbrains.kotlin.android` удалён из корневого `build.gradle.kts`, из всех 11 модулей и из `gradle/libs.versions.toml`. Плагин `org.jetbrains.kotlin.plugin.compose` остаётся — это отдельный плагин компилятора Compose, он по-прежнему нужен.

Блоки `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` уже использовали современный DSL во всех модулях, поэтому `kotlinOptions`-миграция не потребовалась. Других изменений DSL AGP 9 не потребовал: модули не используют устаревшие `buildFeatures`-флаги, `packagingOptions` или `android.defaultConfig.targetSdk`, а `namespace` задан явно.

### 3. compileSdk 37

`compileSdk = 37` установлен во всех 11 модулях. `targetSdk = 36` и `minSdk = 26` не менялись — это осознанно: повышение targetSdk меняет поведение платформы и должно быть отдельным решением (ADR-0001, ADR-0002).

### 4. N-1 из ревью #214

В `gradle/libs.versions.toml` добавлен алиас `okhttp-jvm`, а строковые литералы `"com.squareup.okhttp3:okhttp-jvm:${libs.versions.okhttp.get()}"` в `:core:network` и `:data:library` заменены на `libs.okhttp.jvm`.

### 5. Правка теста `:core:ui`

`EnoActionSheetWindowTest` использовал `WindowCompat.getInsetsController` из `androidx.core`. После перехода на compileSdk 37 этот вызов дал ошибку компиляции в тесте, потому что `:core:ui` не объявляет зависимость от AndroidX Core (продакшен-код давно использует платформенный API). Тест переведён на платформенный `window.insetsController` и `systemBarsAppearance` с масками `APPEARANCE_LIGHT_STATUS_BARS` / `APPEARANCE_LIGHT_NAVIGATION_BARS`. Проверяемое поведение не изменилось.

## Наблюдаемые эффекты

**Версии зависимостей не менялись.** Дерево `:app:dependencies --configuration releaseRuntimeClasspath` на базовой и новой сборках совпадает строка в строку (единственное различие — служебные строки предупреждений Gradle). Значит, все изменения артефакта вызваны новым R8/AGP, а не дрейфом версий.

**Release APK уменьшился на 160 826 байт (−3,26 %):** 4 937 461 → 4 776 635 байт. Причины, проверенные по отчётам R8 и `aapt2 dump resources`:

- R8 в AGP 9.5.1 больше не считает ресурс используемым только потому, что его имя совпадает со строковой константой («matches string pool constant notification»). В базовой сборке этот эвристический keep удерживал неиспользуемые ресурсы; в новой они отброшены: 716 → 560 ресурсов (−156, ничего не добавлено). Ушли шаблоны уведомлений `androidx.core`, layout-шаблоны media3, атрибуты `androidx.window` и строки приложения, на которые ссылаются только тесты (`LocalizedUiResourcesTest`), — `resources.arsc` −157 592, `res/` −33 395.
- Новый R8 понимает метаданные Kotlin 2.4: в базовой сборке было множество предупреждений «An error occurred when parsing kotlin metadata», в новой — ноль. Это дало более полную оптимизацию: `classes.dex` −200 920, набор классов 6 239 → 6 013.
- AGP 9 больше не упаковывает `kotlin-tooling-metadata.json` (−723 байта).
- Профили baseline выросли: `baseline.prof` 5 637 → 10 191, `baseline.profm` 678 → 1 224 (+5 100).

Безопасность удаления проверена: `getIdentifier` в продакшен-коде не используется (0 вхождений), продакшен-код нигде не использует `NotificationCompat` и строит уведомления платформенным `Notification.Builder` (`ArtworkIndexingForegroundService`, `IndexingForegroundService`), minSdk 26 исключает устаревшие ветки шаблонов. `DefaultMediaNotificationProvider` и `MediaStyleNotificationHelper` из media3 перечислены как удалённые в `usage.txt` **обеих** сборок — путь уведомлений media3 мёртв в базовой сборке тоже.

## Проверки

Локально на итоговом дереве пройдены: `:app:assembleDebug`, `testDebugUnitTest` (все модули), `lintDebug`, `detekt` (baseline 429 → 429, не расширен), `:app:compileStablePreviewKotlin`, `:core:ui:verifyRoborazziDebug`, `:app:verifyRoborazziDebug`, `tools/check_adr_index.py`, `design/tools/sync_android.py --check`, `tools/check_detekt_baseline.py`. Minified release собирается, отчёты R8 на месте, «Missing class» отсутствует. Room-схема `1.json` не изменилась (3313 байт, SHA-256 `75ff6dad…c9bde`).

## Роллбэк

Откат — один коммит: вернуть `gradle-wrapper.properties` на 8.13 с прежней SHA-256 (и прогнать `gradle wrapper --gradle-version 8.13`), вернуть `agp = "8.13.2"`, `compileSdk = 36` в 11 модулях и `alias(libs.plugins.kotlin.android)` в корне и модулях. Формат кода, ресурсы, схема Room и applicationId не менялись, поэтому откат не требует миграций данных и не затрагивает подпись.

## Ограничения

- detekt 1.23.8 официально не заявляет поддержку Gradle 9 и AGP 9. Эмпирически все задачи detekt на Gradle 9.9.1 + AGP 9.5.1 проходят, новых находок нет; ограничение зафиксировано в `docs/KNOWN_ISSUES.md`. detekt 2.x существует только в альфах и в проекте не используется.
- Установка поверх предыдущей версии (в том числе на Huawei) в этом PR не проверялась — это делает владелец после слияния.
