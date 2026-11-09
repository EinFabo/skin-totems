"""Erzeugt Referenz-Totems mit der ORIGINALEN Funktion generate_totem_image aus
EinFabo/Custom-Totem-Generator (app.py). Der Java-Port wird pixelgenau dagegen getestet.

Aufruf: python3 tools/make_reference_totems.py
"""
import ast
import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "tools" / "original_app.py"
OUTLINE_DIR = ROOT / "src" / "client" / "resources" / "assets" / "skintotems" / "textures" / "totem"
SKINS = ROOT / "src" / "test" / "resources" / "skins"
OUT = ROOT / "src" / "test" / "resources" / "reference"

tree = ast.parse(APP.read_text(encoding="utf-8"))
func = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == "generate_totem_image")
ns = {"Image": Image, "Path": Path}
exec(compile(ast.Module(body=[func], type_ignores=[]), str(APP), "exec"), ns)
generate_totem_image = ns["generate_totem_image"]

OUT.mkdir(parents=True, exist_ok=True)
os.chdir(OUTLINE_DIR)  # das Original lädt "outline.png" relativ zum Arbeitsverzeichnis
for skin in sorted(SKINS.glob("*.png")):
    for overlay in (True, False):
        img = generate_totem_image(Image.open(skin), overlay)
        name = f"{skin.stem}_{'overlay' if overlay else 'nooverlay'}.png"
        img.save(OUT / name)
        print("wrote", name, img.size, img.mode)
