"""Перенос дизайн-ресурсов из design/ в ресурсы приложения и их сверка.

Единственный путь, которым дизайн попадает в код. Ресурсы в :core:ui и :app руками не правят:
меняется генератор в design/tools → пересобираются assets → этот скрипт копирует их.

  python3 design/tools/sync_android.py            # скопировать и обновить манифест
  python3 design/tools/sync_android.py --bandit   # то же + пересобрать WebP Бандита
  python3 design/tools/sync_android.py --check    # только проверить (CI), ничего не менять

Манифест design/android-resources.sha256 перечисляет каждый дизайн-ресурс приложения и его SHA-256.
WebP Бандита кодировщик собирает не побайтно одинаково, поэтому они сверяются с манифестом,
а не перекодируются при каждой проверке. Остальное сверяется побайтно с источником в design/.

--check падает, если:
  * файл приложения отличается от источника или от манифеста;
  * в приложении есть дизайн-ресурс (ic_eno_*, bandit_*, eno_colors, wordmark*, ic_launcher*),
    которого нет в манифесте — значит, его нарисовали в обход дизайна (см. design/README.md, «design-needed»).
Запускать из корня репозитория. Зависимости: стандартная библиотека; для --bandit — Pillow.
"""
import argparse
import hashlib
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / 'design'
CORE_RES = ROOT / 'core/ui/src/main/res'
APP_RES = ROOT / 'app/src/main/res'
MANIFEST = DESIGN / 'android-resources.sha256'
BANDIT_SRC = DESIGN / 'assets/bandit/clean'
BANDIT_DST = CORE_RES / 'drawable-nodpi'

# (каталог-источник в design/, каталог ресурсов приложения, шаблон файлов)
COPY_TREES = [
    (DESIGN / 'assets/ui-icons/v1/android', CORE_RES, 'drawable/ic_eno_*.xml'),
    (DESIGN / 'assets/tokens/android/res', CORE_RES, '*/eno_colors.xml'),
    (DESIGN / 'assets/wordmark/v2/android/res', CORE_RES, '*/*'),
    (DESIGN / 'assets/icons/v2.0/android/res', APP_RES, '*/*'),
]

# какие файлы приложения считаются дизайн-ресурсами (каталог ресурсов, шаблон)
GUARDED = [
    (CORE_RES, 'drawable*/ic_eno_*'),
    (CORE_RES, 'drawable*/bandit_*'),
    (CORE_RES, 'drawable*/enotone_wordmark*'),
    (CORE_RES, 'values*/eno_colors.xml'),
    (CORE_RES, 'values*/wordmark_colors.xml'),
    (APP_RES, 'mipmap*/ic_launcher*'),
    (APP_RES, 'values*/ic_launcher_background.xml'),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rel(path):
    return path.relative_to(ROOT).as_posix()


def copy_pairs():
    """Пары (источник, назначение) для побайтного копирования."""
    pairs = []
    for src_root, dst_root, pattern in COPY_TREES:
        for src in sorted(src_root.glob(pattern)):
            if src.is_file():
                pairs.append((src, dst_root / src.relative_to(src_root)))
    return pairs


def read_manifest():
    if not MANIFEST.exists():
        return {}
    out = {}
    for line in MANIFEST.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#'):
            digest, path = line.split(maxsplit=1)
            out[path] = digest
    return out


def guarded_files():
    found = set()
    for res, pattern in GUARDED:
        found.update(rel(p) for p in res.glob(pattern) if p.is_file())
    return found


def write_manifest(paths):
    lines = ['# Дизайн-ресурсы приложения. Генерирует design/tools/sync_android.py — руками не править.']
    lines += [f'{sha(ROOT / p)}  {p}' for p in sorted(paths)]
    MANIFEST.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def rebuild_bandit():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import convert_bandit
    with tempfile.TemporaryDirectory() as tmp:
        convert_bandit.main(str(BANDIT_SRC), tmp)
        for f in sorted(Path(tmp).glob('*.webp')):
            shutil.copyfile(f, BANDIT_DST / f.name)


def sync(bandit):
    for src, dst in copy_pairs():
        dst.parent.mkdir(parents=True, exist_ok=True)
        if not dst.exists() or dst.read_bytes() != src.read_bytes():
            shutil.copyfile(src, dst)
            print('обновлено', rel(dst))
    if bandit:
        rebuild_bandit()
    copied = {rel(dst) for _, dst in copy_pairs()}
    bandit_files = {rel(p) for p in BANDIT_DST.glob('bandit_*.webp')}
    write_manifest(copied | bandit_files)
    extra = guarded_files() - copied - bandit_files
    for p in sorted(extra):
        print('НЕ ИЗ ДИЗАЙНА (удалите или добавьте в design/):', p)
    return 1 if extra else 0


def check():
    errors = []
    manifest = read_manifest()
    for src, dst in copy_pairs():
        if not dst.exists():
            errors.append(f'нет в приложении: {rel(dst)} (запустите sync_android.py)')
        elif dst.read_bytes() != src.read_bytes():
            errors.append(f'отличается от design/: {rel(dst)} — ресурс правили руками или не синхронизировали')
    for path, digest in manifest.items():
        f = ROOT / path
        if not f.exists():
            errors.append(f'в манифесте, но нет в приложении: {path}')
        elif sha(f) != digest:
            errors.append(f'не совпадает с манифестом: {path}')
    for path in sorted(guarded_files() - set(manifest)):
        errors.append(f'дизайн-ресурс не из design/: {path} — нужен элемент в design/ '
                      f'(issue с меткой design-needed), см. design/README.md')
    for e in errors:
        print('ОШИБКА:', e)
    if not errors:
        print(f'дизайн-ресурсы совпадают с design/: {len(manifest)} файлов')
    return 1 if errors else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='только проверить')
    parser.add_argument('--bandit', action='store_true', help='пересобрать WebP Бандита')
    args = parser.parse_args()
    sys.exit(check() if args.check else sync(args.bandit))
