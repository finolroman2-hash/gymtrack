"""Prepara iconos musculares: fondo negro → transparente; claro/oscuro en drawable-nodpi."""
from __future__ import annotations

import fnmatch
from pathlib import Path

from PIL import Image

ASSETS = Path(r"C:\Users\kenij\.cursor\projects\c-proyectos-GymTrack\assets")
DST = Path(r"c:\proyectos\GymTrack\app\src\main\res\drawable-nodpi")
MAX_PX = 128
BLACK_THRESH = 42

MUSCLES: list[tuple[str, str, str]] = [
    ("muscle_pecho_1.png", "*pecho1modoclaro*", "*pecho1modooscuro*"),
    ("muscle_pecho_2.png", "*pecho2modoclaro*", "*pecho2modooscuro*"),
    ("muscle_cuadriceps_1.png", "*cuadriceps1modoclaro*", "*cuadriceps1modooscuro*"),
    ("muscle_cuadriceps_2.png", "*cuadriceps2modoclaro*", "*cuadriceps2modooscuro*"),
    ("muscle_aductores_1.png", "*aductores1modoclaro*", "*aductores1modooscuro*"),
    ("muscle_aductores_2.png", "*aductores2modoclaro*", "*aductores2modooscuro*"),
    ("muscle_femoral_1.png", "*femoral1modoclaro*", "*femoral1modooscuro*"),
    ("muscle_femoral_2.png", "*femoral2modoclaro*", "*femoral*2modooscuro*"),
    ("muscle_biceps.png", "*bicepsmodoclaro*", "*bicepsmodooscuro*"),
    ("muscle_triceps.png", "*tricepsmodoclaro*", "*tricepsmodooscuro*"),
    ("muscle_hombro.png", "*hombromodoclaro*", "*hombromodooscuro*"),
    ("muscle_cuello.png", "*cuellomodoclaro*", "*cuellomodooscuro*"),
    ("muscle_core.png", "*coremodoclaro*", "*coremodooscuro*"),
    ("muscle_espalda.png", "*espaldamodoclaro*", "*espaldamodooscuro*"),
    ("muscle_pantorrillas.png", "*pantorrillasmodoclaro*", "*pantorrillasmodooscuro*"),
    ("muscle_antebrazos.png", "*antebrazos-6d4be6fa*", "*antebrazosmodooscuro*"),
    ("muscle_gluteos.png", "*gluteosmodoclaro*", "*gluteosmodooscuro*"),
]


def find_asset(pattern: str) -> Path | None:
    matches = [p for p in ASSETS.glob("*.png") if fnmatch.fnmatch(p.name, pattern)]
    if not matches:
        return None
    return max(matches, key=lambda p: p.stat().st_mtime)


def is_red(r: int, g: int, b: int) -> bool:
    return r > 140 and g < 115 and b < 115 and r > g + 30


def is_good_light_source(im: Image.Image) -> bool:
    px = list(im.convert("RGBA").getdata())
    n = len(px)
    if n == 0:
        return False
    trans = sum(1 for r, g, b, a in px if a < 30) / n
    white = sum(1 for r, g, b, a in px if a > 160 and r > 200 and g > 200 and b > 200) / n
    return trans > 0.45 and white < 0.03


def remove_black_background(im: Image.Image) -> Image.Image:
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 10:
                continue
            if is_red(r, g, b):
                continue
            if r <= BLACK_THRESH and g <= BLACK_THRESH and b <= BLACK_THRESH:
                px[x, y] = (0, 0, 0, 0)
    return im


def white_strokes_to_black(im: Image.Image) -> Image.Image:
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 10:
                continue
            if is_red(r, g, b):
                continue
            if r > 175 and g > 175 and b > 175:
                px[x, y] = (0, 0, 0, a)
    return im


def trim_to_content(im: Image.Image, pad_ratio: float = 0.05) -> Image.Image:
    bbox = im.getbbox()
    if not bbox:
        return im
    im = im.crop(bbox)
    pad_w = max(1, int(im.width * pad_ratio))
    pad_h = max(1, int(im.height * pad_ratio))
    out = Image.new("RGBA", (im.width + 2 * pad_w, im.height + 2 * pad_h), (0, 0, 0, 0))
    out.paste(im, (pad_w, pad_h))
    return out


def resize(im: Image.Image) -> Image.Image:
    im = trim_to_content(im)
    w, h = im.size
    scale = min(MAX_PX / w, MAX_PX / h, 1.0)
    if scale < 1.0:
        return im.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.Resampling.LANCZOS)
    return im


def save(im: Image.Image, path: Path) -> float:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, format="PNG", optimize=True)
    return path.stat().st_size / 1024


def build_light(light_src: Path | None, dark_src: Path | None) -> Image.Image:
    # Pecho modoclaro: recortar y convertir desde oscuro para igualar proporciones al modo oscuro
    if light_src and "pecho" in light_src.name and "modoclaro" in light_src.name and dark_src:
        im = Image.open(dark_src).convert("RGBA")
        im = remove_black_background(im)
        return white_strokes_to_black(im)
    if light_src and is_good_light_source(Image.open(light_src)):
        im = Image.open(light_src).convert("RGBA")
        return remove_black_background(im)
    if dark_src is None:
        raise FileNotFoundError("No hay fuente para modo claro")
    im = Image.open(dark_src).convert("RGBA")
    im = remove_black_background(im)
    return white_strokes_to_black(im)


def build_dark(dark_src: Path) -> Image.Image:
    im = Image.open(dark_src).convert("RGBA")
    return remove_black_background(im)


def main() -> None:
    for light_name, light_pat, dark_pat in MUSCLES:
        night_name = light_name.replace(".png", "_night.png")
        light_src = find_asset(light_pat)
        dark_src = find_asset(dark_pat)

        if dark_src is None:
            print(f"SKIP {light_name}: falta modo oscuro ({dark_pat})")
            continue

        try:
            light_im = resize(build_light(light_src, dark_src))
            dark_im = resize(build_dark(dark_src))
        except FileNotFoundError as e:
            print(f"SKIP {light_name}: {e}")
            continue

        lk = save(light_im, DST / light_name)
        dk = save(dark_im, DST / night_name)
        src_note = "modoclaro" if light_src and is_good_light_source(Image.open(light_src)) else "desde oscuro"
        print(f"{light_name} ({src_note}, {lk:.1f} KB) | {night_name} ({dk:.1f} KB)")


if __name__ == "__main__":
    main()
