# Релиз и тестовый канал enoTone Android

## Версии ([ADR-0078](DECISIONS/ADR-0078-versioning.md))

- Формат: `0.<этап>.<исправление>-preview.<номер прогона CI>`, например `0.6.0-preview.425`. Версия видна в Настройках приложения.
- **Когда владелец принял сборку на Huawei:**
  1. Метка на коммит `main`, из которого собрана **принятая** сборка, с номером этой сборки без суффикса: для `0.6.0-preview.429` — `git tag -a v0.6.0 <sha> -m "Принято на Huawei: <пункты TESTING>"` и `git push origin v0.6.0`.
  2. В отдельном PR: секцию `Unreleased` в CHANGELOG переименовать в принятый номер (`## 0.6.0 — дата`), отметить PASS в TESTING и REQUIREMENTS, повысить `PATCH` в `app/build.gradle.kts` для следующей разработки (`0.6.1`).
- Начало нового этапа роадмапа: `0.7.0`. Первый публичный выпуск: `1.0.0`.

**Состояние 2026-11-17:** полноценного публичного production release по тегу ещё нет. Однако **подписанная Stable Preview уже есть**, её нельзя называть неподписанным debug APK или незавершённой настройкой: пакет `app.enotone.android.preview` подписывается постоянным ключом из GitHub Actions Secrets и обновляется поверх установленной версии с сохранением Room.

### Минифицированная сборка проверяется в CI (R-A1, Issue #197)

`isMinifyEnabled = true` включён в `debug`, `release` и `stablePreview`, а `stablePreview` наследует `release` через `initWith`, то есть канал приёмки минифицируется. До R-A1 минифицированный APK ни разу не собирался автоматически: CI знал только `assembleDebug`, а `app/proguard-rules.pro` содержал одну строку комментария. Поэтому «минификация работает» было непроверенным утверждением, а не фактом.

Теперь каждый PR выполняет шаг `Release build (minified, unsigned)` и падает, если APK отсутствует, если задача `:app:minifyReleaseWithR8` не выполнялась или если R8 сообщил `Missing class`. Результат публикуется артефактом `enotone-release-unsigned-apk`.

| Дата | Коммит | `app-release-unsigned.apk` | Отчёты R8 | Статус |
|---|---|---|---|---|
| 2026-11-17 | локальный прогон на ветке `ci/r-a1-release-build` от `2a425b28` | **4 915 809 байт** | `mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt` | `BUILD SUCCESSFUL`, `Missing class` не встречается |

Ограничения, которые нельзя замалчивать:

- артефакт **не подписан** и не является дистрибутивом; установка на устройство и запуск не проверяются — это R-A3 (release smoke на Gradle Managed Device);
- `proguard-rules.pro` остаётся пустым: осмысленные keep-правила — предмет R-A2, а не этой проверки;
- R8 печатает предупреждения `An error occurred when parsing kotlin metadata` из-за Kotlin 2.4.21 при Gradle 8.13 (см. [KNOWN_ISSUES](KNOWN_ISSUES.md)); они **не** являются `Missing class` и сборку не блокируют, но означают, что совместимость R8 с Kotlin metadata ещё предстоит обновить вместе с Gradle/AGP;
- размер APK меняется от коммита к коммиту: число выше — срез, а не порог.

- Последняя подтверждённая на Huawei — **Stable Preview #301** после [PR #71](https://github.com/metravolokos/enoTone-android/pull/71), `main` SHA `a5c8962be704af1657ac5eb6346c36ee945a4640`. CI [#301](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110) **success, 23/23 этапа**; [прямая ссылка на подписанный артефакт](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110/artifacts/13833891904) (`enotone-stable-preview-apk`). После установки владелец сообщил «Работает», это базовая аппаратная приёмка.
- **Не использовать** `enotone-preview-debug-apk` / `.preview.rN` для обновления основного приложения: у них другой package ID/подпись и они не заменят Stable Preview с её сохранёнными данными. Никогда не советовать удалять рабочий APK или очищать данные ради нового теста.
- В Stable Preview #301 уже приняты постоянные обложки **до 1200 px в `filesDir`** ([ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md)); это не добавляет постоянный offline-аудиокэш и не является выпуском production-плеера.
- Производственная релизная подпись/публичная дистрибуция и лицензия собственного кода — отдельные незавершённые этапы. Закрытые ключи/пароли нельзя помещать в git, чаты, логи или APK.

Историческая первоначальная инструкция по настройке ключа и правила проверки — [STABLE_PREVIEW_CHANNEL](STABLE_PREVIEW_CHANNEL.md). Действующее состояние и оставшиеся проверки — [CURRENT_DECISIONS](CURRENT_DECISIONS.md), [TESTING](TESTING.md).
