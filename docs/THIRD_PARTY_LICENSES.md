# THIRD PARTY LICENSES

Предварительный набор: AGP, Kotlin/Compose, AndroidX, Ktor CIO (Apache-2.0, только runtime M0), OkHttp/MockWebServer (Apache-2.0, только тесты). Инструменты сборки (не входят в APK): detekt 1.23.8 и detekt-formatting/ktlint (Apache-2.0), Gradle Wrapper 8.13 (Apache-2.0). Добавлены Room 2.8.5 и KSP 2.3.10 (проверить license/SPDX для release). Для **реально использованных** версий до публикации релиза оформить SPDX, copyright и полный dependency audit; этот каркас не заменяет аудита. Лицензия собственного исходного кода ожидает решения владельца.

- **Gson 2.13.2** — Google, Apache License 2.0, библиотека JSON-дерева для независимого от Android JVM-кодека очереди M6-0/3. Используются только `JsonObject`/`JsonArray`/`JsonParser`; рефлексивного связывания классов нет и специальные R8 keep-rules не требуются. Полный license/SPDX audit перед релизом остаётся открытым.
