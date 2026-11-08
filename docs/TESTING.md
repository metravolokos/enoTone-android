# Тестирование

M0: HTTP mock с 207/401/403/404/429/5xx, Range/без Range, ETag/без ETag, Unicode; unit tests и проверка отсутствия GMS как в Gradle dependency graph, так и в APK/манифесте. Реальный сервер не участвует в CI.

Ручная проверка: Huawei без GMS → пароль внешнего приложения → PROPFIND root и /AllMusic → опциональный Range на явно указанном тестовом файле → обезличенный отчёт. Проверки по факту фиксировать в STATUS.
