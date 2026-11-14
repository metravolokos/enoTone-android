"""Очистка прозрачности изображений Бандита без перерисовки персонажа.

Запуск: python3 tools/clean_bandit.py assets/bandit/originals assets/bandit/clean
- RGBA-исходники: альфа 240–254 → 255 (персонаж слегка просвечивал); шум альфа < 30 вдали от фигуры → 0.
- RGB-исходники с «нарисованной» шахматкой вместо прозрачности: светлый малонасыщенный фон,
  связанный с краем кадра, → прозрачный (контур Бандита тёмный и отделяет фон), край сглажен на 0,7 px.
- Итог обрезается по фигуре. Пиксели персонажа не меняются.
Зависимости: Pillow, numpy, scipy.
"""
import sys, glob, os
from PIL import Image
import numpy as np
from scipy import ndimage

def clean(path):
    src = Image.open(path); a = np.array(src.convert('RGBA')).astype(np.int32)
    rgb, al = a[..., :3], a[..., 3]
    if src.mode == 'RGBA':
        al[al >= 240] = 255
        solid = ndimage.binary_dilation(al >= 128, iterations=3)
        al[(al < 30) & ~solid] = 0
    else:
        mx, mn = rgb.max(-1), rgb.min(-1)
        light = (mn > 185) & ((mx - mn) < 28)
        lab, _ = ndimage.label(light)
        border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        bg = ndimage.binary_dilation(np.isin(lab, list(border)), iterations=1)
        alpha = ndimage.gaussian_filter(np.where(bg, 0, 255).astype(float), 0.7)
        alpha[~ndimage.binary_dilation(bg, iterations=2)] = 255
        al = alpha.clip(0, 255).astype(np.int32)
    a[..., 3] = al
    out = Image.fromarray(a.astype(np.uint8))
    return out.crop(out.getbbox())

if __name__ == '__main__':
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for p in sorted(glob.glob(os.path.join(src_dir, 'bandit_*.png'))):
        clean(p).save(os.path.join(out_dir, os.path.basename(p)), optimize=True)
        print(os.path.basename(p))
