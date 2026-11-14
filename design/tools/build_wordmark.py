"""enoTone Wordmark v2.0 — источник истины текстового логотипа.

Своя геометрия (та же скруглённая линия, что у UI Icons v1): строчные «e», «n», «o» собраны из линии
с круглыми концами; правая половина «one» — точное горизонтальное зеркало «eno» относительно оси «T»;
большая «T» — скруглённые прямоугольники: стойка выше и ниже строчных, перекладина нависает над «o».
Запуск: python3 tools/build_wordmark.py assets/wordmark/v2
Зависимости: стандартная библиотека; для PNG — cairosvg и Pillow (необязательно).
"""
import math, os, sys

# --- утверждённые параметры (2026-11-12)
XH = 100          # высота строчных
SW = 24           # толщина линии букв
GAP = 10          # промежуток между буквами
AP = 75           # раскрытие «e», градусы (вариант 4)
BAR_DY = 0.12     # перекладина «e» выше центра, доля радиуса (вариант 4)
TK = 1.55         # толщина «T» относительно линии букв (вариант 3)
TOP_K, BOT_K = 0.55, 0.32   # выступ стойки «T» над x-высотой и под базовой линией, доли XH
OVER = 0.45       # насколько перекладина «T» нависает над «o», доля ширины «o»
PAD = 16          # зазор между «o» и стойкой «T»
THEMES = {'graphite': ('#1B1A19', '#F4ECE4', '#EBAE9F'), 'porcelain': ('#EDE9E5', '#211E1C', '#A24F3E')}

r = XH / 2 - SW / 2
def f(v): return f'{v:.2f}'.rstrip('0').rstrip('.')

def letters(mirror_cx=None):
    """Пути трёх букв «eno»; при mirror_cx — их зеркало (дают «one»)."""
    def X(x): return x if mirror_cx is None else 2 * mirror_cx - x
    sweep = lambda s: s if mirror_cx is None else 1 - s
    xs = [0, XH + GAP, 2 * (XH + GAP)]
    out = []
    # e
    x0 = xs[0]; cx, cy = x0 + XH / 2, -XH / 2
    a = math.radians(AP); ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
    by = cy - BAR_DY * r; bx = cx + math.sqrt(r * r - (by - cy) ** 2)
    out.append(f'M{f(X(x0 + SW / 2))} {f(by)}H{f(X(bx))}A{f(r)} {f(r)} 0 1 {sweep(0)} {f(X(ex))} {f(ey)}')
    # n
    x0 = xs[1]
    out.append(f'M{f(X(x0 + SW / 2))} 0V{f(-XH + SW / 2)}M{f(X(x0 + SW / 2))} {f(-XH / 2)}'
               f'A{f(r)} {f(r)} 0 0 {sweep(1)} {f(X(x0 + XH - SW / 2))} {f(-XH / 2)}V0')
    # o — две полуокружности
    x0 = xs[2]; cx = x0 + XH / 2
    out.append(f'M{f(X(cx - r))} {f(-XH / 2)}A{f(r)} {f(r)} 0 1 {sweep(1)} {f(X(cx + r))} {f(-XH / 2)}'
               f'A{f(r)} {f(r)} 0 1 {sweep(1)} {f(X(cx - r))} {f(-XH / 2)}Z')
    return ' '.join(out), xs[2] + XH

def rrect(x, y, w, h, rr):
    return (f'M{f(x + rr)} {f(y)}H{f(x + w - rr)}A{f(rr)} {f(rr)} 0 0 1 {f(x + w)} {f(y + rr)}V{f(y + h - rr)}'
            f'A{f(rr)} {f(rr)} 0 0 1 {f(x + w - rr)} {f(y + h)}H{f(x + rr)}A{f(rr)} {f(rr)} 0 0 1 {f(x)} {f(y + h - rr)}'
            f'V{f(y + rr)}A{f(rr)} {f(rr)} 0 0 1 {f(x + rr)} {f(y)}Z')

def geometry():
    left, right_edge = letters()
    tsw = SW * TK; cx = right_edge + PAD + tsw / 2
    right, _ = letters(mirror_cx=cx)
    top, bot = -XH * (1 + TOP_K), XH * BOT_K
    bar_w = tsw + 2 * PAD + 2 * OVER * XH; bar_h = tsw * 0.92
    t = rrect(cx - tsw / 2, top, tsw, bot - top, tsw / 2) + ' ' + rrect(cx - bar_w / 2, top, bar_w, bar_h, bar_h / 2)
    minx = -SW / 2 - 6; maxx = 2 * cx - minx
    vb = (minx, top - 6, maxx - minx, bot - top + 12)
    return left + ' ' + right, t, vb

def svg(theme, background=False):
    letters_d, t_d, (x, y, w, h) = geometry(); bg, fg, ac = THEMES[theme]
    if background:  # поле вокруг логотипа на подложке
        m = 0.35 * XH; x, y, w, h = x - m, y - m, w + 2 * m, h + 2 * m
    rect = f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" fill="{bg}"/>' if background else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{f(x)} {f(y)} {f(w)} {f(h)}" role="img" aria-label="enoTone">'
            f'{rect}<path d="{letters_d}" fill="none" stroke="{fg}" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{t_d}" fill="{ac}"/></svg>\n')

def vector_drawable():
    letters_d, t_d, (x, y, w, h) = geometry()
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- enoTone Wordmark v2.0 — генерируется design/tools/build_wordmark.py, не править вручную.
     Цвета берутся из @color/wordmark_letters и @color/wordmark_accent (values = «Фарфор», values-night = «Графит»). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{f(w * 0.4)}dp" android:height="{f(h * 0.4)}dp"
    android:viewportWidth="{f(w)}" android:viewportHeight="{f(h)}">
    <group android:translateX="{f(-x)}" android:translateY="{f(-y)}">
        <path android:pathData="{letters_d}"
            android:strokeColor="@color/wordmark_letters" android:strokeWidth="{SW}"
            android:strokeLineCap="round" android:strokeLineJoin="round" />
        <path android:pathData="{t_d}" android:fillColor="@color/wordmark_accent" />
    </group>
</vector>
'''

if __name__ == '__main__':
    out = sys.argv[1]
    for d in ('svg', 'png', 'android/res/drawable', 'android/res/values', 'android/res/values-night'):
        os.makedirs(f'{out}/{d}', exist_ok=True)
    for th in THEMES:
        for bgv in (False, True):
            name = f'enotone_wordmark_{th}{"_bg" if bgv else ""}'
            s = svg(th, bgv); open(f'{out}/svg/{name}.svg', 'w').write(s)
            try:
                import cairosvg
                cairosvg.svg2png(bytestring=s.encode(), write_to=f'{out}/png/{name}@2x.png', output_width=1200)
            except ImportError:
                pass
    open(f'{out}/android/res/drawable/enotone_wordmark.xml', 'w').write(vector_drawable())
    for q, th in (('values', 'porcelain'), ('values-night', 'graphite')):
        _, fg, ac = THEMES[th]
        open(f'{out}/android/res/{q}/wordmark_colors.xml', 'w').write(
            f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <!-- enoTone Wordmark v2.0, тема «{"Фарфор" if th == "porcelain" else "Графит"}» -->\n'
            f'    <color name="wordmark_letters">{fg}</color>\n    <color name="wordmark_accent">{ac}</color>\n</resources>\n')
    print('ok')
