# CHANGELOG

## Unreleased — M1
- PR #4 merged: Huawei подтвердил 3590 прямых аудиофайлов /AllMusic, 0 пропусков и ошибок; зелёный CI #21.
- Начата модель Room v1 (2.8.5, KSP 2.3.10): таблица треков, DAO, консервативный snapshot sync, SHA-256 ID по аккаунту/пути, тесты сравнения ETag и сохранения будущих тегов.
- Добавлено исключение локальных данных из Android Backup и device transfer; схема Room v1 зафиксирована в Git после CI #24 и проверяется на совпадение с KSP output. Компоненты пока не соединены с UI; нужен новый CI.

## M0
- PR #1 merged: Kotlin/Compose, WebDAV на Huawei, без GMS.
