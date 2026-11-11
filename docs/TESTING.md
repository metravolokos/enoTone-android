# TESTING — как проверяется enoTone

> Текущая стратегия и **открытые** ручные проверки. Журнал всех аппаратных проверок 2026-11-08…11 (Huawei Preview #36…#301 с числами) — [archive/TESTING_JOURNAL_2026-11-08_11.md](archive/TESTING_JOURNAL_2026-11-08_11.md). Действующие решения — [CURRENT_DECISIONS](CURRENT_DECISIONS.md).

## 1. Автоматические проверки (CI, `.github/workflows/ci.yml`)

| Шаг | Что проверяет |
|---|---|
| `python3 -m unittest discover -s tools -p 'test_*.py'` | mock WebDAV (`tools/test_mock_webdav.py`) и «контрактные» тесты исходников (см. ниже) |
| `gradle :app:assembleDebug … :testDebugUnitTest … :app:lintDebug` | сборка, JUnit-тесты `:core:network`, `:core:database`, `:data:library`, `:playback`, Android Lint `:app` |
| `:app:compileStablePreviewKotlin` | компиляция варианта Stable Preview без секретов |
| Room schema | экспортированные схемы v1–v4 совпадают с закоммиченными |
| `tools/check_no_gms.py` | дерево зависимостей и байты APK не содержат `com.google.android.gms`, `com.google.firebase`, `com.google.android.play` |
| Stable Preview (только `main`) | подпись постоянным ключом и совпадение отпечатка сертификата |

Локально (нужны JDK 17, Android SDK 36, Gradle 8.13; Gradle Wrapper пока нет — M6-0/8):

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
gradle --no-daemon :app:assembleDebug :core:network:testDebugUnitTest \
  :data:library:testDebugUnitTest :playback:testDebugUnitTest :app:lintDebug
```

Mock-сервер: `python3 tools/mock_webdav.py` (только HTTP на loopback, только для тестов); синтетические WAV с кириллическими NFC/NFD-именами — `python3 tools/generate_fixtures.py <каталог>`.

### Известный долг тестов

- `tools/test_*_contract.py` (кроме `test_mock_webdav.py`) читают `.kt`/`.md` и проверяют наличие строк. Они **не проверяют поведение** и ломаются при любом рефакторинге. Новые такие тесты **не добавлять**; замена — M6-0/5 ([ADR-0074](DECISIONS/ADR-0074-m6-0-tech-debt-plan.md)).
- Нет тестов `:app`, Room DAO/миграций (`MigrationTestHelper`), Compose UI.

## 2. Ручная проверка на Huawei

Правила: устанавливать **подписанную** `app.enotone.android.preview` поверх предыдущей, не удалять приложение и данные; **не** запускать полную переиндексацию 3590 файлов ради UI-проверки; в Git и issue — только обезличенные числа и коды, без имён файлов, путей, ETag и учётных данных.

Последнее базовое принятие — Stable Preview **#301**, ответ владельца «Работает». Он **не** означает PASS для пунктов ниже.

### Открытые проверки

1. **Graphite Material 3 (ADR-0073, Stable Preview из CI #347):** светлая/тёмная/системная тема, нижняя навигация, мини-плеер, Now Playing (seek, громкость, shuffle/repeat), страница альбома, длинные названия, TalkBack-подписи.
2. **Одна обложка альбома** на всех экранах (сетка, детали, строки треков, мини-плеер, Now Playing); «Неизвестный альбом» не объединяет чужие картинки.
3. **Offline обложек:** показать альбом онлайн → закрыть приложение → выключить сеть → открыть: картинка из `filesDir`, без нового GET.
4. **ON_DEMAND:** не загружает невидимое; повторный показ — без GET.
5. **AUTO_AFTER_IMPORT:** отдельная фоновая подготовка, прогресс, Pause/Stop, checkpoint; не мешает воспроизведению.
6. **Ручная очистка обложек:** размер в Настройках, подтверждение, автоочистки нет.
7. **Переиндексация обновлённого файла** с прежним ETag сбрасывает ссылки на обложку, общий WebP не удаляется.
8. **Воспроизведение с выключенным экраном** 20+ минут по Wi-Fi с переходами между треками (станет обязательным после M6-0/2).

## 3. Шаблон отчёта владельца

```
Сборка: Stable Preview #<номер CI>
Проверено: <номера пунктов из раздела 2>
Результат: PASS / FAIL (+ обезличенный код ошибки или скриншот без личных данных)
```
