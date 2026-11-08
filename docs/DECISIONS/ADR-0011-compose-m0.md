# ADR-0011: Совместимая версия Compose для M0

Дата: 2026-11-08 | Статус: принято для M0 | Автор: ChatGPT

## Контекст
CI HEAD `2e163b8ebf76887c555baacdc5d0561b0083b792`, run 42462402655: ошибка `:app:checkDebugAarMetadata`, Compose 1.12.1 требует compileSdk 37 и Android Gradle Plugin 9.1+. M0 изначально целится в compileSdk 36 / AGP 8.13.2.

## Альтернативы
1. Обновить AGP до 9.1.1, Gradle до 9.3.1, compileSdk до 37 (targetSdk можно оставить 36): актуальнее, но больше перемен, AGP 9 встроенный Kotlin и переработка сборки.
2. Использовать последний стабильный до Compose 1.12 train 1.11.x с совместимым BOM 2026.06.00: быстрее стабилизирует M0, требует обновления позже.

## Решение
M0: BOM `2026.06.00` (Compose 1.11.3) при compileSdk/targetSdk 36 и AGP 8.13.2; зависимость версии проверяется через CI, а не считается доказанно рабочей заранее. Kotlin 2.3.10 и plugin Compose закреплены явно.

## Последствия
Пока не используем API Compose 1.12, запланировать отдельную обновляющую проверку при переходе на AGP9.

## Как проверить / пересмотр
Зелёный `:app:assembleDebug`, unit tests, Android lint, GMS dependencies + APK, успешный APK на Huawei.
Основания: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler ; https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
