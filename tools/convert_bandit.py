"""Перенос изображений Бандита из enotone-design в ресурсы приложения (шаг D1, ADR-0079).

Запуск: python3 tools/convert_bandit.py <путь к enotone-design>/assets/bandit/clean app/src/main/res/drawable-nodpi
Только масштаб (высота 720 px — самый крупный показ ≈ 240 dp × 3) и формат WebP: цвет с качеством 92
(для плоской контурной графики глазом не отличить от lossless, а размер в ~4 раза меньше), альфа — без потерь.
Персонаж не перерисовывается и не обрезается. Источник истины — enotone-design (docs/BANDIT_SET_V1.md, SHA-256 оригиналов).
Зависимости: Pillow.
"""
import os
import sys

from PIL import Image

TARGET_HEIGHT = 720
WEBP_QUALITY = 92  # проверено сравнением с lossless при 2× увеличении (2026-11-12)
# имя в enotone-design → имя ресурса; позы 08 (указующий жест — только иконка) и 12 (наушники A) в интерфейсе не используются
MAPPING = {
    'bandit_01_books.png': 'bandit_books',
    'bandit_02_reading.png': 'bandit_reading',
    'bandit_03_thinking.png': 'bandit_thinking',
    'bandit_04_peeking.png': 'bandit_peeking',
    'bandit_05_happy.png': 'bandit_happy',
    'bandit_06_relaxing.png': 'bandit_relaxing',
    'bandit_07_sleeping.png': 'bandit_sleeping',
    'bandit_09_sleeping_books.png': 'bandit_sleeping_books',
    'bandit_10_armchair_cool.png': 'bandit_armchair',
    'bandit_11_sitting.png': 'bandit_sitting',
    'bandit_13_idea.png': 'bandit_idea',
    'bandit_14_headphones_b.png': 'bandit_headphones',
}

def main(src, dst):
    os.makedirs(dst, exist_ok=True)
    for name, res in MAPPING.items():
        im = Image.open(os.path.join(src, name)).convert('RGBA')
        if im.height > TARGET_HEIGHT:
            im = im.resize((round(im.width * TARGET_HEIGHT / im.height), TARGET_HEIGHT), Image.LANCZOS)
        im.save(os.path.join(dst, res + '.webp'), 'WEBP', quality=WEBP_QUALITY, alpha_quality=100, method=6)
        print(res, im.size)

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
