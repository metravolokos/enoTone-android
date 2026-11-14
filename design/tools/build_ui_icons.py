"""enoTone UI Icons v1 — источник истины набора значков интерфейса.

Сетка 24×24, линия 2, круглые концы и стыки. Каждый значок — список элементов:
  (mode, pathData[, stroke_width])
  mode: 'stroke' — только линия; 'fill' — только заливка; 'both' — заливка + линия
        (скругляет углы залитых фигур); 'evenodd' — заливка с вырезами.
Запуск: python3 tools/build_ui_icons.py assets/ui-icons/v1
Создаёт svg/*.svg (currentColor) и android/drawable/ic_eno_*.xml (VectorDrawable,
цвет #FF000000 — в Compose задаётся через Icon(tint=...)).
Значок «Авторы» (album_artists) построен скриптом с зазором 3,1 вокруг центральной
фигуры (shapely); здесь хранится результат.
"""
import os, sys

def circle(cx, cy, r):
    return f'M{cx-r:g} {cy:g}a{r:g} {r:g} 0 1 0 {2*r:g} 0a{r:g} {r:g} 0 1 0 {-2*r:g} 0z'

def rrect(x, y, w, h, r):
    return (f'M{x+r:g} {y:g}h{w-2*r:g}a{r:g} {r:g} 0 0 1 {r:g} {r:g}v{h-2*r:g}'
            f'a{r:g} {r:g} 0 0 1 {-r:g} {r:g}h{-(w-2*r):g}a{r:g} {r:g} 0 0 1 {-r:g} {-r:g}'
            f'v{-(h-2*r):g}a{r:g} {r:g} 0 0 1 {r:g} {-r:g}z')

HOUSE = ('M4.6 10.4 12 4.3l7.4 6.1V18a2.2 2.2 0 0 1-2.2 2.2h-2.7v-4.6a1.2 1.2 0 0 0-1.2-1.2'
         'h-2.6a1.2 1.2 0 0 0-1.2 1.2v4.6H6.8A2.2 2.2 0 0 1 4.6 18z')
# в залитом доме дверь — вырез снизу; шире, чем в контурном, т.к. линия 2 сужает его на 2
HOUSE_FILL = ('M4.6 10.4 12 4.3l7.4 6.1V18a2.2 2.2 0 0 1-2.2 2.2h-1.9v-3.8a2.2 2.2 0 0 0-2.2-2.2'
              'h-1.4a2.2 2.2 0 0 0-2.2 2.2v3.8H6.8A2.2 2.2 0 0 1 4.6 18z')
NOTE_STEMS = 'M9.2 17.6V6.8l10-2.3v10.8'
MIC_ARC = 'M5.8 11.4a6.2 6.2 0 0 0 12.4 0M12 17.6v3M9.2 20.6h5.6'
REPEAT = ('M4 12.6V11a4 4 0 0 1 4-4h11.3M16.7 4.3 19.4 7l-2.7 2.7'
          'M20 11.4V13a4 4 0 0 1-4 4H4.7M7.3 14.3 4.6 17l2.7 2.7')
GROUP_OUTLINE = 'M15.70 8.60 L15.63 7.88 L15.42 7.18 L15.08 6.54 L14.62 5.98 L14.06 5.52 L13.42 5.18 L12.72 4.97 L12.00 4.90 L11.28 4.97 L10.58 5.18 L9.94 5.52 L9.38 5.98 L8.92 6.54 L8.58 7.18 L8.37 7.88 L8.30 8.60 L8.37 9.32 L8.58 10.02 L8.92 10.66 L9.38 11.22 L9.94 11.68 L10.58 12.02 L11.28 12.23 L12.00 12.30 L12.72 12.23 L13.42 12.02 L14.06 11.68 L14.62 11.22 L15.08 10.66 L15.42 10.02 L15.63 9.32 L15.70 8.60 M4.80 21.00 L4.84 20.26 L4.96 19.52 L5.29 18.45 L5.80 17.45 L6.22 16.83 L6.71 16.25 L7.26 15.73 L7.86 15.27 L8.51 14.88 L9.20 14.55 L9.92 14.30 L10.67 14.12 L11.81 14.00 L12.57 14.02 L13.33 14.12 L14.44 14.42 L15.15 14.70 L15.82 15.07 L16.45 15.49 L17.02 15.98 L17.78 16.83 L18.20 17.45 L18.56 18.11 L18.84 18.80 L19.04 19.52 L19.16 20.26 L19.20 21.00 M5.94 5.51 L5.33 5.40 L4.82 5.43 L4.32 5.55 L3.76 5.84 L3.27 6.25 L2.97 6.66 L2.75 7.12 L2.61 7.75 L2.61 8.25 L2.75 8.88 L3.04 9.44 L3.36 9.84 L3.86 10.23 L4.32 10.45 L4.95 10.59 L5.50 10.58 M0.60 19.60 L0.70 18.37 L0.92 17.49 L1.37 16.39 L1.82 15.67 L2.36 15.04 L2.76 14.68 L3.19 14.38 L3.87 14.05 L4.75 13.83 M21.40 8.00 L21.32 7.37 L21.09 6.77 L20.73 6.25 L20.35 5.91 L19.91 5.65 L19.31 5.45 L18.67 5.40 L18.06 5.51 M18.50 10.58 L19.05 10.59 L19.55 10.49 L20.14 10.23 L20.55 9.93 L20.96 9.44 L21.20 8.99 L21.35 8.51 L21.40 8.00 M19.25 13.83 L20.13 14.05 L20.81 14.38 L21.44 14.85 L22.18 15.67 L22.63 16.39 L23.08 17.49 L23.30 18.37 L23.40 19.60'
GROUP_FILL = 'M15.70 8.42 L15.59 7.70 L15.34 7.02 L14.97 6.40 L14.48 5.86 L13.90 5.43 L13.25 5.12 L12.54 4.94 L11.82 4.90 L11.10 5.01 L10.42 5.26 L9.80 5.63 L9.26 6.12 L8.83 6.70 L8.52 7.35 L8.34 8.06 L8.30 8.78 L8.41 9.50 L8.66 10.18 L9.03 10.80 L9.52 11.34 L10.10 11.77 L10.75 12.08 L11.46 12.26 L12.18 12.30 L12.90 12.19 L13.58 11.94 L14.20 11.57 L14.74 11.08 L15.17 10.50 L15.48 9.85 L15.66 9.14 L15.70 8.42Z M19.20 21.00 L19.16 20.26 L19.04 19.52 L18.84 18.80 L18.56 18.11 L18.20 17.45 L17.78 16.83 L17.02 15.98 L16.14 15.27 L15.49 14.88 L14.80 14.55 L14.08 14.30 L13.33 14.12 L12.19 14.00 L11.43 14.02 L10.67 14.12 L9.56 14.42 L8.85 14.70 L8.18 15.07 L7.55 15.49 L6.98 15.98 L6.22 16.83 L5.80 17.45 L5.44 18.11 L5.16 18.80 L4.96 19.52 L4.84 20.26 L4.80 21.00 L19.20 21.00Z M7.27 6.43 L6.85 5.99 L6.43 5.71 L5.95 5.51 L5.45 5.41 L4.95 5.41 L4.45 5.51 L3.97 5.71 L3.55 5.99 L3.19 6.35 L2.91 6.77 L2.71 7.25 L2.61 7.75 L2.61 8.25 L2.71 8.75 L2.91 9.23 L3.19 9.65 L3.55 10.01 L3.97 10.29 L4.45 10.49 L4.95 10.59 L5.45 10.59 L5.95 10.49 L6.43 10.29 L6.85 10.01 L6.97 9.91 L6.82 9.07 L6.82 8.13 L6.97 7.30 L7.27 6.43Z M6.82 14.17 L6.29 13.97 L5.81 13.85 L5.32 13.80 L4.59 13.85 L3.87 14.05 L3.41 14.26 L2.97 14.53 L2.56 14.85 L2.17 15.24 L1.66 15.90 L1.12 16.93 L0.76 18.07 L0.63 18.98 L0.60 19.60 L3.42 19.60 L3.62 18.71 L3.89 17.93 L4.54 16.63 L5.34 15.53 L5.98 14.87 L6.82 14.17Z M21.39 7.75 L21.25 7.12 L20.96 6.56 L20.64 6.16 L20.14 5.77 L19.55 5.51 L18.93 5.40 L18.29 5.45 L17.69 5.65 L17.15 5.99 L16.73 6.43 L17.03 7.30 L17.18 8.13 L17.18 9.07 L17.03 9.91 L17.36 10.16 L17.69 10.35 L18.42 10.57 L19.18 10.57 L19.91 10.35 L20.55 9.93 L21.03 9.34 L21.32 8.63 L21.39 8.25 L21.39 7.75Z M23.40 19.60 L23.30 18.37 L23.17 17.78 L22.76 16.66 L22.34 15.90 L22.01 15.44 L21.24 14.68 L20.81 14.38 L20.36 14.14 L19.65 13.90 L19.17 13.82 L18.68 13.80 L18.19 13.85 L17.71 13.97 L17.18 14.17 L17.74 14.62 L18.41 15.25 L19.00 15.95 L19.46 16.63 L20.11 17.93 L20.38 18.71 L20.58 19.60 L23.40 19.60Z'

ICONS = {
    # --- навигация: контур (обычное состояние) и заливка (активная вкладка)
    'home': [('stroke', HOUSE)],
    'home_fill': [('both', HOUSE_FILL)],
    'songs': [('stroke', NOTE_STEMS), ('stroke', circle(6.8, 17.6, 2.4)), ('stroke', circle(16.8, 15.3, 2.4))],
    'songs_fill': [('stroke', NOTE_STEMS), ('both', 'M9.2 9.2l10-2.3V4.5l-10 2.3z'),
                   ('fill', circle(6.8, 17.6, 2.7)), ('fill', circle(16.8, 15.3, 2.7))],
    'albums': [('stroke', rrect(3.5, 3.5, 17, 17, 4.8)), ('stroke', circle(12, 12, 3.6)), ('fill', circle(12, 12, 0.9))],
    'albums_fill': [('evenodd', rrect(2.5, 2.5, 19, 19, 5.8) + circle(12, 12, 4.1)), ('fill', circle(12, 12, 1.3))],
    'artists': [('stroke', rrect(9, 3, 6, 11.5, 3)), ('stroke', MIC_ARC)],
    'artists_fill': [('both', rrect(9, 3, 6, 11.5, 3)), ('stroke', MIC_ARC)],
    'album_artists': [('stroke', GROUP_OUTLINE)],
    'album_artists_fill': [('evenodd', GROUP_FILL)],
    # --- управление воспроизведением (один вид)
    'play': [('both', 'M8.4 5.6 18.6 12 8.4 18.4z', 3)],
    'pause': [('fill', rrect(6, 4.8, 4.6, 14.4, 1.9)), ('fill', rrect(13.4, 4.8, 4.6, 14.4, 1.9))],
    'next': [('both', 'M3.8 7.2 11 12l-7.2 4.8zM12.4 7.2l7.2 4.8-7.2 4.8z', 2.6)],
    'previous': [('both', 'M20.2 7.2 13 12l7.2 4.8zM11.6 7.2 4.4 12l7.2 4.8z', 2.6)],
    'shuffle': [('stroke', 'M3.5 7.5h2.2c5.5 0 6.6 9 12.3 9H20M3.5 16.5h2.2c1.8 0 3-.9 4-2.1'
                           'M13.7 9.6c1-1.3 2.4-2.1 4.3-2.1H20M17.6 5l2.5 2.5-2.5 2.5M17.6 14l2.5 2.5-2.5 2.5')],
    'repeat': [('stroke', REPEAT)],
    'repeat_one': [('stroke', REPEAT), ('stroke', 'M11 10.6l1.4-.9v4.6', 1.8)],
    'queue': [('stroke', 'M4 6.5h11.5M4 11.5h11.5M4 16.5h6.5M18.6 18.2v-7.6l2.4.9'), ('fill', circle(16.7, 18.2, 2.2))],
    'more': [('fill', circle(12, 5.5, 1.9)), ('fill', circle(12, 12, 1.9)), ('fill', circle(12, 18.5, 1.9))],
    # служебные (2026-11-14): поиск и «ползунки» — из утверждённых макетов Android v2
    # (Android2-Songs / Android2-Home-C2), «закрыть» — для поля поиска, той же линией
    'search': [('stroke', circle(10.5, 10.5, 6.3)), ('stroke', 'M15.3 15.3L20 20')],
    'tune': [('stroke', 'M4 7h8.5M17.5 7H20M4 17h2.5M11.5 17H20'),
             ('stroke', circle(15, 7, 2.5)), ('stroke', circle(9, 17, 2.5))],
    'close': [('stroke', 'M6.5 6.5L17.5 17.5M17.5 6.5L6.5 17.5')],
    # действия листа песни (2026-11-15) — формы утверждены на артборде Android2-SongActions
    # (canvas/gen.py EXTRA: play_next, add_end); заменили временные значки из M6-0/8a (#134)
    'play_next': [('stroke', 'M4 6.5h10M4 11.5h7M4 16.5h7'), ('both', 'M15.5 11.6 20.5 15l-5 3.4z', 1.6)],
    'queue_add': [('stroke', 'M4 6.5h12M4 11.5h12M4 16.5h6M17.5 13.5v7M14 17h7')],
}

def svg(els):
    parts = []
    for e in els:
        mode, d = e[0], e[1]; w = e[2] if len(e) > 2 else 2
        st = f'stroke="currentColor" stroke-width="{w:g}" stroke-linecap="round" stroke-linejoin="round"'
        if mode == 'stroke': parts.append(f'<path d="{d}" fill="none" {st}/>')
        elif mode == 'fill': parts.append(f'<path d="{d}" fill="currentColor"/>')
        elif mode == 'evenodd': parts.append(f'<path d="{d}" fill="currentColor" fill-rule="evenodd"/>')
        elif mode == 'both': parts.append(f'<path d="{d}" fill="currentColor" {st}/>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
            + ''.join(parts) + '</svg>\n')

def vector(els):
    parts = []
    for e in els:
        mode, d = e[0], e[1]; w = e[2] if len(e) > 2 else 2
        st = (f'\n        android:strokeColor="#FF000000" android:strokeWidth="{w:g}"'
              '\n        android:strokeLineCap="round" android:strokeLineJoin="round"')
        if mode == 'stroke': parts.append(f'    <path android:pathData="{d}"{st} />')
        elif mode == 'fill': parts.append(f'    <path android:pathData="{d}"\n        android:fillColor="#FF000000" />')
        elif mode == 'evenodd': parts.append(f'    <path android:pathData="{d}"\n        android:fillColor="#FF000000" android:fillType="evenOdd" />')
        elif mode == 'both': parts.append(f'    <path android:pathData="{d}"\n        android:fillColor="#FF000000"{st} />')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- enoTone UI Icons v1 — генерируется design/tools/build_ui_icons.py, не править вручную -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            + '\n'.join(parts) + '\n</vector>\n')

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(f'{out}/svg', exist_ok=True); os.makedirs(f'{out}/android/drawable', exist_ok=True)
    for name, els in ICONS.items():
        open(f'{out}/svg/{name}.svg', 'w').write(svg(els))
        open(f'{out}/android/drawable/ic_eno_{name}.xml', 'w').write(vector(els))
    print(len(ICONS), 'icons')
