# enoTone — дизайн

Весь дизайн плеера **enoTone** и маскота **Бандита**: решения, генераторы, эталонные экраны, исходники холста.
С 2026-11-15 живёт здесь, в репозитории приложения. Отдельный репозиторий `metravolokos/enotone-design`
архивный: в нём только история до переноса (последний коммит `a2afd4c`), новых изменений туда не вносить.

## Если вы новый чат и продолжаете дизайн — читайте в этом порядке

1. [`AGENTS.md`](AGENTS.md) — правила работы с дизайном (коротко).
2. [`docs/DECISIONS_ANDROID_V2_UI.md`](docs/DECISIONS_ANDROID_V2_UI.md) — журнал решений по экранам Android v2. **Более поздняя строка важнее.**
3. [`docs/DESIGN_DECISIONS.md`](docs/DESIGN_DECISIONS.md) — бренд, палитра, Бандит, значки, логотип.
4. [`docs/UI_ANDROID_V2.md`](docs/UI_ANDROID_V2.md) и PNG в [`assets/ui-screens/android-v2/`](assets/ui-screens/android-v2/) — утверждённые экраны. Их смотреть глазами, а не вспоминать.
5. [`canvas/README.md`](canvas/README.md) — как пересобрать и показать холст.
6. Для переноса в код — `../docs/DESIGN_PLAN.md` (шаги D1–D6) и открытые issue с меткой `design-needed`.

Владелец общается по-русски, проверяет на Huawei Nova 15 Pro (377 × 816 dp, радиус углов ≈ 56 dp) и
часто работает с телефона: показывать картинки, а не описывать словами.

## Что утверждено

| Что | Документ | Файлы | Источник |
| --- | --- | --- | --- |
| Палитра «Глянцевый енот» v1.5, темы «Графит» и «Фарфор» | [DESIGN_DECISIONS §4](docs/DESIGN_DECISIONS.md) | `tokens/colors.json`, `assets/tokens/android/` | `tools/build_tokens.py` |
| Иконка приложения v2.0 | [APP_ICON_V2](docs/APP_ICON_V2.md) | `assets/icons/v2.0/` | `tools/build_app_icon.py` |
| Текстовый логотип Wordmark v2.0 | [WORDMARK_V2](docs/WORDMARK_V2.md) | `assets/wordmark/v2/` | `tools/build_wordmark.py` |
| Значки интерфейса UI Icons v1 | [UI_ICONS_V1](docs/UI_ICONS_V1.md) | `assets/ui-icons/v1/` | `tools/build_ui_icons.py` |
| Бандит: позы и назначение по экранам | [BANDIT_SET_V1](docs/BANDIT_SET_V1.md) | `assets/bandit/` | `tools/clean_bandit.py`, `tools/convert_bandit.py` |
| Экраны Android UI v2 | [UI_ANDROID_V2](docs/UI_ANDROID_V2.md), [журнал](docs/DECISIONS_ANDROID_V2_UI.md) | `assets/ui-screens/android-v2/` | `canvas/` |
| Буферизация на «Сейчас играет» | [SPEC_NOW_PLAYING_BUFFERING](docs/SPEC_NOW_PLAYING_BUFFERING.md) | — | — |
| Перенос в приложение | [ANDROID_INTEGRATION_PLAN](docs/ANDROID_INTEGRATION_PLAN.md), `../docs/DESIGN_PLAN.md` | — | `tools/sync_android.py` |
| Desktop — рабочее направление (отложено) | [UI_BASELINE_V1](docs/UI_BASELINE_V1.md) | — | — |

Шрифты: **Unbounded** — заголовки, **Onest** — всё остальное (в приложении — `:core:ui`, OFL).

## Как дизайн попадает в приложение

```text
design/tools/build_*.py  →  design/assets/**/android  →  design/tools/sync_android.py  →  core/ui, app (res)
                                                                         └→ design/android-resources.sha256
```

Ресурсы `ic_eno_*`, `bandit_*`, `eno_colors`, `wordmark*`, `ic_launcher*` в приложении руками не правят и не
рисуют. После изменения генератора: `python3 design/tools/<генератор>`, затем
`python3 design/tools/sync_android.py` из корня. Проверка — `python3 design/tools/sync_android.py --check`.

## Нужен новый элемент — `design-needed`

Если для задачи нужен значок, поза Бандита, цвет, состояние или экран, которого нет в утверждённом дизайне,
**разработка этой части останавливается**:

1. Открыть issue с меткой `design-needed`: что нужно, где показывается, какой экран/PR ждёт.
2. В отчёте и в PR написать «заблокировано дизайном: #N»; остальную часть задачи можно делать.
3. Дизайн элемента: черновик на холсте (`canvas/`, для значков — `EXTRA` в `gen.py`) → показать владельцу →
   после утверждения перенести в генератор, собрать, `sync_android.py`, строка в журнале решений.
4. Закрыть issue ссылкой на PR с ресурсом; заблокированная работа продолжается.

Временные значки Material, нарисованные «похоже» пути или заглушки вместо элемента не допускаются.

## Структура

```text
assets/
  bandit/originals/      изображения Бандита от владельца — не изменять
  bandit/clean/          те же с исправленной прозрачностью
  bandit/masters/        прозрачный мастер для иконки — не изменять
  icons/v2.0/            иконка приложения и Android-ресурсы
  ui-icons/v1/           значки: SVG и Android VectorDrawable
  wordmark/v2/           логотип: SVG, PNG, Android drawable
  tokens/android/        палитра eno_colors.xml
  ui-screens/android-v2/ эталонные экраны Android (PNG, SHA256SUMS)
canvas/                  исходники холста Android v2 (скрипты + project/*.dc.html)
docs/                    решения, описания, журнал Android v2
tokens/colors.json       палитра
tools/                   генераторы и перенос в приложение
android-resources.sha256 манифест дизайн-ресурсов приложения (генерирует sync_android.py)
```
