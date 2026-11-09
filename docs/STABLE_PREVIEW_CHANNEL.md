# Стабильная тестовая версия enoTone: настройка подписи

**Один раз** настроить отдельный обновляемый канал `app.enotone.android.preview`. Он не заменяет старые `app.enotone.android.preview.rN`: эти пакеты не могут делить данные Android. В новой стабильной версии импорт /AllMusic потребуется **только при первой установке**; следующие обновления с тем же ключом сохранят Room. Для простого перезапуска приложения Room уже сохраняется, но email/password пока вводятся вручную (RAM-only).

## 1. Создать PKCS12 локально (никогда не публиковать)
На собственном Windows/Linux/macOS-компьютере с JDK вызвать:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore enotone-preview.p12 -alias enotone-preview -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=enoTone Preview, O=enoTone"
```

Придумать и сохранить уникальный пароль keystore; не использовать пароль Mail.ru. Ключ и пароль хранить также в безопасной offline backup копии, **вне GitHub**.

Получить публичный SHA-256 сертификата:

```bash
keytool -list -v -keystore enotone-preview.p12 -alias enotone-preview
```

Нужен только текст из строки `SHA256:` (не приватный ключ).

## 2. Настроить GitHub (потребуется действие владельца)
В `metravolokos/enoTone-android` → **Settings → Secrets and variables → Actions** установить:

- **Repository secrets:** `ENOTONE_PREVIEW_KEYSTORE_BASE64` — base64 бинарного `enotone-preview.p12` одной строкой; `ENOTONE_PREVIEW_KEYSTORE_PASSWORD` — пароль из шага 1.
- **Repository variables:** `ENOTONE_PREVIEW_CERT_SHA256` — скопированная строка SHA256 (двоеточия допустимы); `ENOTONE_STABLE_PREVIEW_ENABLED` — `true` **только после** создания предыдущих значений.

На Windows PowerShell сформировать base64 в буфере обмена, не печатая бинарный ключ в публичный терминальный лог:

```powershell
[Convert]::ToBase64String([System.IO.File]::ReadAllBytes((Resolve-Path './enotone-preview.p12'))) | Set-Clipboard
```

На Linux: `base64 -w0 enotone-preview.p12`, на macOS: `base64 -i enotone-preview.p12 | tr -d '\n'`. Не вставлять это значение в чат, issue или коммит; только в поле Repository Secret.

## 3. Автоматические сборки
После merge этого PR и включения переменной push в `main` автоматически соберёт обычный CI и затем **проверенную стабильную Preview** (только если все gates зелёные). Первую сборку также можно запустить вручную в Actions → Android M0 → Run workflow → main.

В итогах CI скачать `enotone-stable-preview-apk`. При следующих обновлениях ставить его **поверх того же приложения**, не удаляя. Проверить, что данные Room сохранились и импорт /AllMusic повторно не требуется.

## Ограничения
- Смена сертификата, идентификатора пакета, удаление приложения или очистка данных приведёт к потере совместимости либо данных; не экспериментировать с этим на основной Preview.
- Отдельные `.preview.rN` пакеты останутся на устройстве, но не смогут передать свои Room.
- Пароль пока **не сохраняется**, он не включён ни в APK, ни в базу. Шифрованное сохранение через Android Keystore — отдельный последующий PR.
- Оригинальный Android плеер, фоновая служба, Room schema, Media3 и WebDAV этим PR не меняются.
