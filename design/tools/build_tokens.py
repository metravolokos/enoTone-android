#!/usr/bin/env python3
"""Палитра enoTone: tokens/colors.json → Android-ресурсы eno_colors.xml (обе темы).

Источник истины — tokens/colors.json. Результат — assets/tokens/android/res/values{,-night}/eno_colors.xml,
приложение копирует его без правок. Запуск: python3 tools/build_tokens.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets/tokens/android/res"


def theme_tokens(t, dark):
    accent = t["accent"] if dark else t["accentFill"]
    accent_text = t["accent"] if dark else t["accentText"]
    return [
        ("eno_background", t["background"]),
        ("eno_surface", t["surface"]),
        ("eno_surface2", t["surface2"]),
        ("eno_text", t["text"]),
        ("eno_text_secondary", t["textSecondary"]),
        ("eno_accent", accent),
        ("eno_accent_text", accent_text),
        ("eno_line", t["line"]),
        ("eno_on_accent", t["onAccent"]),
        ("eno_shadow", t["shadow"]),
        ("eno_scrim", t["scrim"]),
        ("eno_cover_rim", t["coverRim"]),
        ("eno_shadow_strong", t["shadowStrong"]),
    ]


def shared_tokens(d):
    s = d["shared"]
    out = [
        ("eno_cover_outline", s["coverOutline"]),
        ("eno_playing_overlay", s["playingOverlay"]),
        ("eno_playing_bar", s["playingBar"]),
    ]
    for i, a in enumerate(d["avatars"], 1):
        out += [(f"eno_avatar_{i}", a["background"]), (f"eno_avatar_on_{i}", a["letter"])]
    return out


def write(path, theme_name, items, version):
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Палитра enoTone v{version}, тема «{theme_name}». Сгенерировано tools/build_tokens.py",
        "     из design/tokens/colors.json — не править руками. -->",
        "<resources>",
    ]
    lines += [f'    <color name="{n}">{v.upper()}</color>' for n, v in items]
    lines += ["</resources>", ""]
    path.write_text("\n".join(lines), encoding="utf-8")


def main():
    d = json.loads((ROOT / "tokens/colors.json").read_text(encoding="utf-8"))
    v = d["version"]
    shared = shared_tokens(d)
    # values = «Фарфор» (светлая, по умолчанию), values-night = «Графит» — как в приложении (ADR-0079).
    write(OUT / "values/eno_colors.xml", d["lightThemeName"], theme_tokens(d["light"], False) + shared, v)
    write(OUT / "values-night/eno_colors.xml", d["darkThemeName"], theme_tokens(d["dark"], True) + shared, v)
    print("ok", OUT)


if __name__ == "__main__":
    main()
