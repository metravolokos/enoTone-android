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

| Дата | Что собрано | Вшитая ревизия | `app-release-unsigned.apk` | Отчёты R8 | Статус |
|---|---|---|---|---|---|
| 2026-11-17 | локально на ветке `ci/r-a1-release-build`, до коммита (`2a425b28`) | `2a425b28…` | **4 915 809 байт** | `mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt` | `BUILD SUCCESSFUL` за 2 м 32 с, `Missing class` не встречается |
| 2026-11-17 | CI [run 44183251686](https://github.com/metravolokos/enoTone-android/actions/runs/44183251686), merge-ref `0237cdfd` (родители `2a425b28` + `44fc10ce`) | `0237cdfd…` | **4 915 813 байт** | те же четыре файла в артефакте | job `android` — success за 10 м 45 с |
| 2026-11-17 | CI [run 44185473883](https://github.com/metravolokos/enoTone-android/actions/runs/44185473883), merge-ref `fe09279a` (родители `2a425b28` + `84224969`) | `fe09279a…` | **4 915 813 байт** | те же четыре файла в артефакте | job `android` — success за 14 м 10 с |

Размеры различаются на 4 байта, и это ожидаемо, а не расхождение сборок. APK несёт `META-INF/version-control-info.textproto` с полем `revision`: в него вшивается SHA коммита, собранного в момент сборки. Локально это был `2a425b28` (коммита ещё не существовало), в CI — merge-коммиты `0237cdfd` и `fe09279a`. Различаются ровно `version-control-info.textproto` (120 байт во всех трёх случаях, сжатие 117 против 118) и `AndroidManifest.xml` (9 564 байта во всех трёх, сжатие 2 568 против 2 571). Состав APK совпадает: 278 записей, иных отличий `Compare-Object` по `FullName|Length|CompressedLength` не нашёл. Два разных merge-ref дали одинаковые 4 915 813 байт — то есть число зависит от сжимаемости вшитой hex-строки, а не от содержимого сборки; поэтому байт-в-байт сверять размер между локальной сборкой и CI нельзя, сверять нужно состав APK и отчёты R8.

Практический вывод для R-A3: в артефакте записан SHA **merge-коммита**, а не HEAD ветки, поэтому вшитую ревизию нужно сверять с merge-ref, а не с `headRefOid`.

Ограничения, которые нельзя замалчивать:

- артефакт **не подписан** и не является дистрибутивом; установка на устройство и запуск не проверяются — это R-A3 (release smoke на Gradle Managed Device);
- `proguard-rules.pro` остаётся пустым: осмысленные keep-правила — предмет R-A2, а не этой проверки;
- R8 печатает предупреждения `An error occurred when parsing kotlin metadata` из-за Kotlin 2.4.21 при Gradle 8.13 (см. [KNOWN_ISSUES](KNOWN_ISSUES.md)); они **не** являются `Missing class` и сборку не блокируют, но означают, что совместимость R8 с Kotlin metadata ещё предстоит обновить вместе с Gradle/AGP;
- размер APK меняется от коммита к коммиту, в том числе из-за вшитой ревизии в `version-control-info.textproto`: числа выше — срез конкретного прогона, а не порог и не бюджет.

- Последняя подтверждённая на Huawei — **Stable Preview #301** после [PR #71](https://github.com/metravolokos/enoTone-android/pull/71), `main` SHA `a5c8962be704af1657ac5eb6346c36ee945a4640`. CI [#301](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110) **success, 23/23 этапа**; [прямая ссылка на подписанный артефакт](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110/artifacts/13833891904) (`enotone-stable-preview-apk`). После установки владелец сообщил «Работает», это базовая аппаратная приёмка.
- **Не использовать** `enotone-preview-debug-apk` / `.preview.rN` для обновления основного приложения: у них другой package ID/подпись и они не заменят Stable Preview с её сохранёнными данными. Никогда не советовать удалять рабочий APK или очищать данные ради нового теста.
- В Stable Preview #301 уже приняты постоянные обложки **до 1200 px в `filesDir`** ([ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md)); это не добавляет постоянный offline-аудиокэш и не является выпуском production-плеера.
- Производственная релизная подпись/публичная дистрибуция и лицензия собственного кода — отдельные незавершённые этапы. Закрытые ключи/пароли нельзя помещать в git, чаты, логи или APK.

Историческая первоначальная инструкция по настройке ключа и правила проверки — [STABLE_PREVIEW_CHANNEL](STABLE_PREVIEW_CHANNEL.md). Действующее состояние и оставшиеся проверки — [CURRENT_DECISIONS](CURRENT_DECISIONS.md), [TESTING](TESTING.md).
