---
name: Нужен элемент дизайна
about: Для задачи нужен значок, поза Бандита, цвет, состояние или экран, которого нет в design/
title: "design: "
labels: design-needed
---
<!-- Правило design-needed (ADR-0090, design/README.md): эта часть работы стоит, пока элемент не утверждён владельцем и не перенесён design/tools/sync_android.py. Временные Material-значки, «похожие» пути и заглушки не допускаются. -->

## Что нужно

<!-- значок / поза / цвет или токен / состояние / экран; одной фразой -->

## Где показывается

<!-- экран, состояние, обе темы; ссылка на макет из design/assets/ui-screens/android-v2/, если близкий есть -->

## Что заблокировано

<!-- PR, задача, сценарии (ID из docs/SCENARIOS.md) -->

## Готово, когда

- [ ] черновик показан владельцу (холст `design/canvas/`) и утверждён;
- [ ] элемент в генераторе `design/tools/`, собран, `sync_android.py` — в PR;
- [ ] строка в `design/docs/DECISIONS_ANDROID_V2_UI.md` (или DESIGN_DECISIONS) и `design/docs/CHANGELOG.md`.
