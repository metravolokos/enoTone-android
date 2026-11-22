# Релиз и тестовый канал enoTone Android

## Версии ([ADR-0078](DECISIONS/ADR-0078-versioning.md))

- Формат: `0.<этап>.<исправление>-preview.<номер прогона CI>`, например `0.6.0-preview.425`. Версия видна в Настройках приложения.
- **Когда владелец принял сборку на устройстве:**
  1. Метка на коммит `main`, из которого собрана **принятая** сборка, с номером этой сборки без суффикса: для `0.6.0-preview.429` — `git tag -a v0.6.0 <sha> -m "Принято на устройстве: <пункты TESTING>"` и `git push origin v0.6.0`.
  2. В отдельном PR: секцию `Unreleased` в CHANGELOG переименовать в принятый номер (`## 0.6.0 — дата`), отметить PASS в TESTING и REQUIREMENTS, повысить `PATCH` в `app/build.gradle.kts` для следующей разработки (`0.6.1`).
- Начало нового этапа роадмапа: `0.7.0`. Первый публичный выпуск: `1.0.0`.

**Состояние 2026-11-18:** полноценного публичного production release по тегу ещё нет. Однако **подписанная Stable Preview уже есть**, её нельзя называть неподписанным debug APK или незавершённой настройкой: пакет `app.enotone.android.preview` подписывается постоянным ключом из GitHub Actions Secrets и обновляется поверх установленной версии с сохранением Room.

- Последняя подтверждённая на устройстве — **Stable Preview #301** после [PR #71](https://github.com/metravolokos/enoTone-android/pull/71), `main` SHA `a5c8962be704af1657ac5eb6346c36ee945a4640`. CI [#301](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110) **success, 23/23 этапа**; [прямая ссылка на подписанный артефакт](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110/artifacts/13833891904) (`enotone-stable-preview-apk`). После установки владелец сообщил «Работает», это базовая аппаратная приёмка.
- **Не использовать** `enotone-preview-debug-apk` / `.preview.rN` для обновления основного приложения: у них другой package ID/подпись и они не заменят Stable Preview с её сохранёнными данными. Никогда не советовать удалять рабочий APK или очищать данные ради нового теста.
- В Stable Preview #301 уже приняты постоянные обложки **до 1200 px в `filesDir`** ([ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md)); это не добавляет постоянный offline-аудиокэш и не является выпуском production-плеера.
- Производственная релизная подпись/публичная дистрибуция и лицензия собственного кода — отдельные незавершённые этапы. Закрытые ключи/пароли нельзя помещать в git, чаты, логи или APK.

## Исторический срез проверки минифицированной сборки (R-A1, Issue #197)

`isMinifyEnabled = true` включён в `debug`, `release` и `stablePreview`, а `stablePreview` наследует `release` через `initWith`. Поэтому R8 работал и до R-A1: для `debug` он запускается внутри `assembleDebug` задачей `:app:minifyDebugWithR8`, а минифицированный `stablePreview` собирается на `main` при каждом пуше. Новое в R-A1 — не сам факт работы R8, а **блокирующая проверка варианта `release`** с `isShrinkResources = true` на каждом PR, его отчёты R8 и ворота по `Missing class`.

Теперь каждый PR выполняет шаг `Release build (minified, unsigned)`. Он падает, если APK отсутствует; если задача `:app:minifyReleaseWithR8` не выполнялась; если R8 сообщил `Missing class`; если в `configuration.txt` есть `-dontshrink` или `-dontoptimize` либо нет `-allowaccessmodification` (то есть сжатие и оптимизация кода выключены); если в логе нет `Task :app:convertShrunkResourcesToBinaryRelease` (то есть `isShrinkResources = true` не действует). Результат публикуется артефактом `enotone-release-unsigned-apk`.

Про маркеры сжатия ресурсов отдельно: `isShrinkResources` не оставляет следов ни в `configuration.txt`, ни в `resources.txt` — оба файла побайтово совпадают при включённом и выключенном флаге (проверено на ветке, где флаг временно ставился в `false`). Отличается только размер APK — 4 915 809 байт против 5 256 574. Единственный надёжный маркер — наличие задачи `:app:convertShrunkResourcesToBinaryRelease` в графе сборки: при выключенном флаге её нет вовсе, включая случай `UP-TO-DATE` (с `--console=plain` пропущенная задача всё равно печатается строкой с суффиксом, а при выключенном флаге задачи в графе нет).

Артефакт `enotone-release-unsigned-apk` выгружается и у **падающей** сборки: `if: ${{ !cancelled() }}` (ручная отмена прогона не выгружает обрывки), `if-no-files-found: warn`. При `Missing class` AGP роняет `:app:minifyReleaseWithR8` **до** записи `mapping.txt`, поэтому в артефакт входит `missing_rules.txt` — именно он служит исходными данными для keep-правил R-A2; `mapping.txt`, `seeds.txt`, `usage.txt` и `configuration.txt` сохранены на случай других отказов, например срабатывания ворот по сжатию.

| База | Вшитая ревизия | `app-release-unsigned.apk` | Отчёты R8 |
|---|---|---|---|
| локально и в CI на `2a425b28` (R-A1) | SHA коммита: локально `2a425b28…`, в CI — merge-ref PR | **4 915 809 байт** локально, **4 915 813 байт** в CI | `mapping.txt`, `seeds.txt`, `usage.txt`, `configuration.txt` — в обоих случаях |
| CI на `4697e5d4` (R-A1-fix, прогон 44391598996) | merge-ref PR #212 | **4 937 465 байт** | те же четыре; `missing_rules.txt` отсутствует — в сборке нет `Missing class` |

Размеры различаются на 4 байта, и это ожидаемо, а не расхождение сборок. APK несёт `META-INF/version-control-info.textproto` с полем `revision`: в него вшивается SHA коммита, собранного в момент сборки. Локально это был `2a425b28` (коммита ещё не существовало), в CI — merge-коммит PR. Различаются ровно `version-control-info.textproto` (120 байт во всех случаях, сжатие 117 против 118) и `AndroidManifest.xml` (9 564 байта во всех случаях, сжатие 2 568 против 2 571). Состав APK совпадает: 278 записей, иных отличий `Compare-Object` по `FullName|Length|CompressedLength` не нашёл. Два разных merge-ref дали одинаковые 4 915 813 байт — то есть число зависит от сжимаемости вшитой hex-строки, а не от содержимого сборки; поэтому байт-в-байт сверять размер между локальной сборкой и CI нельзя, сверять нужно состав APK и отчёты R8.

Размер зависит и от базы: на `4697e5d4` тот же состав (278 записей) весит 4 937 465 байт — после `2a425b28` в `main` вошли семь коммитов (#210, #205, #207, #204, #206, #199 и DOC-02), которые меняют код и ресурсы. Число в таблице — свидетельство прогона на конкретной базе, а не константа; инвариант — состав APK и наличие отчётов R8.

`missing_rules.txt` появляется в каталоге отчётов **только** при `Missing class`: на зелёной сборке его нет, и `if-no-files-found: warn` именно поэтому не роняет шаг. В артефакте зелёного прогона 44391598996 лежат пять файлов — APK и четыре отчёта R8.

Практический вывод для R-A3: в артефакте записан SHA **merge-коммита**, а не HEAD ветки, поэтому вшитую ревизию нужно сверять с merge-ref, а не с `headRefOid`.

## Текущее состояние R8 (R-A2)

В R-A2 для приложения оставлены только `-keepattributes SourceFile,LineNumberTable` и `-renamesourcefileattribute SourceFile`. Поля enum могут переименовываться, но сохранённые значения используют `Enum.name()` и сравниваются с `entries.firstOrNull { it.name == … }`; исходные строки остаются в DEX. Дополнительные enum keep-правила не нужны.

Фактически применённую конфигурацию нужно проверять в `configuration.txt` из артефакта `enotone-release-unsigned-apk` того же GitHub Actions run, что собрал APK. Этот файл включает правила приложения и consumer-правила зависимостей; для Stable Preview mapping скачивайте отдельный артефакт `enotone-stable-preview-mapping` из того же run.

Ограничения, которые нельзя замалчивать:

- артефакт **не подписан** и не является дистрибутивом; установка на устройство и запуск не проверяются — это R-A3 (release smoke на Gradle Managed Device);
- В исторических R-A1 прогонах на Gradle 8.13 / AGP 8.13.2 встречались предупреждения R8 `An error occurred when parsing kotlin metadata` (см. [KNOWN_ISSUES](KNOWN_ISSUES.md)); это не `Missing class` и не описание текущего тулчейна. В `main` уже используются Gradle 9.9.1 / AGP 9.5.1 ([ADR-0100](DECISIONS/ADR-0100-gradle9-agp9-compilesdk37.md)).
- размер APK меняется от коммита к коммиту, в том числе из-за вшитой ревизии в `version-control-info.textproto`: числа выше — срез конкретного прогона, а не порог и не бюджет.

## Расшифровка стек-трейса Stable Preview (R-A2)

Stable Preview минифицирована. Для каждой сборки на `main` CI сохраняет R8 mapping отдельным артефактом `enotone-stable-preview-mapping` на 90 дней. Откройте GitHub Actions run, собравший нужный APK, и скачайте mapping из этого же run: суффикс `-preview.N` в `versionName` равен номеру Actions run, по которому ищется mapping. Mapping от другой сборки использовать нельзя.

Установите Android SDK cmdline-tools и передайте mapping и файл со стек-трейсом в Retrace:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/retrace mapping.txt trace.txt
```

Retrace восстановит исходные имена классов, методов и строки для R8-кадров. В доказательствах PR приведён пример искусственного release-стека и его расшифровки; реальные стеки Stable Preview расшифровывайте mapping из прогона именно этой сборки.

Историческая первоначальная инструкция по настройке ключа и правила проверки — [STABLE_PREVIEW_CHANNEL](STABLE_PREVIEW_CHANNEL.md). Действующее состояние и оставшиеся проверки — [CURRENT_DECISIONS](CURRENT_DECISIONS.md), [TESTING](TESTING.md).
