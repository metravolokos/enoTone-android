# Релиз и тестовый канал enoTone Android

**Состояние 2026-11-11:** полноценного публичного production release по тегу ещё нет. Однако **подписанная Stable Preview уже есть**, её нельзя называть неподписанным debug APK или незавершённой настройкой: пакет `app.enotone.android.preview` подписывается постоянным ключом из GitHub Actions Secrets и обновляется поверх установленной версии с сохранением Room.

- Последняя подтверждённая на Huawei — **Stable Preview #301** после [PR #71](https://github.com/metravolokos/enoTone-android/pull/71), `main` SHA `a5c8962be704af1657ac5eb6346c36ee945a4640`. CI [#301](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110) **success, 23/23 этапа**; [прямая ссылка на подписанный артефакт](https://github.com/metravolokos/enoTone-android/actions/runs/42971807110/artifacts/13833891904) (`enotone-stable-preview-apk`). После установки владелец сообщил «Работает», это базовая аппаратная приёмка.
- **Не использовать** `enotone-preview-debug-apk` / `.preview.rN` для обновления основного приложения: у них другой package ID/подпись и они не заменят Stable Preview с её сохранёнными данными. Никогда не советовать удалять рабочий APK или очищать данные ради нового теста.
- В Stable Preview #301 уже приняты постоянные обложки **до 1200 px в `filesDir`** ([ADR-0067](DECISIONS/ADR-0067-permanent-album-artwork.md)); это не добавляет постоянный offline-аудиокэш и не является выпуском production-плеера.
- Производственная релизная подпись/публичная дистрибуция и лицензия собственного кода — отдельные незавершённые этапы. Закрытые ключи/пароли нельзя помещать в git, чаты, логи или APK.

Историческая первоначальная инструкция по настройке ключа и правила проверки — [STABLE_PREVIEW_CHANNEL](STABLE_PREVIEW_CHANNEL.md). Действующее состояние и оставшиеся проверки — [CURRENT_DECISIONS](CURRENT_DECISIONS.md), [TESTING](TESTING.md).
