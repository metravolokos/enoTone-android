"""Пути для скриптов холста: всё относительно папки design/ репозитория enoTone-android.

Шрифты для локального рендера (Playwright): `npm i @fontsource/onest @fontsource/unbounded`
в этой папке или путь в переменной окружения FONTSOURCE.
"""
import os
from pathlib import Path

CANVAS = Path(__file__).resolve().parent
DESIGN = CANVAS.parent
ICONS = DESIGN / 'assets/ui-icons/v1/svg'
BANDIT = DESIGN / 'assets/bandit/clean'
FONTS = Path(os.environ.get('FONTSOURCE', CANVAS / 'node_modules/@fontsource'))
os.chdir(CANVAS)  # скрипты пишут project/*.dc.html и PNG относительно этой папки
