# ADR-0013: Временный Ktor CIO для диагностики M0 вместо OkHttp

Дата: 2026-11-08 | Статус: эксперимент, ожидает зелёного CI | Автор: ChatGPT

## Контекст
PR #1, Actions run 42463446577: build/tests/lint успешны, но строгое правило запрета GMS обнаружило в DEX строку `com.google.android.gms.org.conscrypt`. Это известный необязательный адаптер TLS-провайдера Google Play Services внутри OkHttp (`DeferredSocketAdapter`), а не доказательство установленной GMS-библиотеки. R8 shrinking не удалил строку. См. анализ: https://verification.f-droid.org/ru.yanus171.feedexfork_208.apk.diffoscope.html

## Рассмотренные варианты
1. Белый список для строки — **неприемлемо**: прямо нарушает правило о запрещённых строках в APK.
2. Патчить бинарный JAR OkHttp или шейдить его — хрупко, усложняет обновления и контроль безопасности.
3. Заменить только транспорт диагностического M0 на Ktor CIO — стандартный TLS без OkHttp; минусы: дополнительная зависимость, нужно подтвердить GMS-free CI, Android-совместимость и поведение PROPFIND.
4. Ручной клиент на SSLSocket — отвергнут: самостоятельный протокол HTTP/TLS увеличивает риски ошибок и поддержки.

## Решение
Для диагностического спайка M0 пробуем Ktor CIO 3.6.1 (Apache-2.0), при сохранении TLS, запрета redirect, sanitization, HTTP Range, PROPFIND и unit tests. OkHttp остаётся **только testImplementation** как транзитивная часть MockWebServer, не входит в runtime APK. Строгий APK scanner не меняется. В первом Ktor CI #9 с выключенным debug-R8 обнаружен уже другой маркер: `com.google.android.gms.provider.action.PICK_IMAGES` из optional AndroidX Activity Photo Picker. Повторно включён debug-R8 для удаления неиспользуемого кода при неизменном строгом APK gate; CI результата этой комбинации пока нет. Это не решение M1: основной transport для потоковой медиатеки должен быть выбран отдельно через ADR после тестов на устройстве.

## Риски и проверка
Проверить custom HTTP PROPFIND в MockWebServer, GET Range, Unicode, отсутствие redirect, только HTTPS (кроме loopback теста), полную CI и GMS gate по runtime dependencies и APK на точном HEAD. Если CI падает — эксперимент не принят. Не объединять M0 до ручной WebDAV проверки Huawei. Уточнить системный proxy/VPN, streaming и retry при проектировании M1.
