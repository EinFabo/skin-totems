"""Erzeugt das Mod-Icon: Steve-Plushie vorne, flaches Alex-Skin-Totem hinten rechts, auf
Totem-Hintergrund (Smaragdgrün mit goldenem Schein).

  python3 tools/make_icon.py   ->  release/modrinth/icon.png (512) + assets/skintotems/icon.png (128)
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from render_plushie import render

ROOT = Path(__file__).resolve().parent.parent
N = 512


def background():
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.sqrt((xx - N * 0.5) ** 2 + (yy - N * 0.46) ** 2) / (N * 0.62)
    t = np.clip(d, 0, 1)[..., None]
    inner, mid, outer = np.array([214, 160, 52]), np.array([38, 98, 74]), np.array([12, 40, 34])
    col = np.where(t < 0.45, inner * (1 - t / 0.45) + mid * (t / 0.45), mid * (1 - (t - 0.45) / 0.55) + outer * ((t - 0.45) / 0.55))
    return Image.fromarray(np.dstack([col, np.full((N, N), 255)]).astype(np.uint8), "RGBA")


def soft_shadow(img, strength, blur):
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.putalpha(img.getchannel("A").point(lambda a: int(a * strength)))
    return shadow.filter(ImageFilter.GaussianBlur(blur))


def main():
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, N - 1, N - 1), radius=104, fill=255)
    icon = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    icon.paste(background(), (0, 0), mask)

    # flaches Skin-Totem (Alex, vom Original-Generator) hinten rechts, leicht gedreht
    totem = Image.open(ROOT / "src/test/resources/reference/alex_overlay.png").convert("RGBA").resize((288, 288), Image.NEAREST)
    totem = totem.rotate(-10, resample=Image.NEAREST, expand=True)
    icon.alpha_composite(soft_shadow(totem, 110 / 255, 10), (236, 30))
    icon.alpha_composite(totem, (226, 18))

    # Steve-Plushie vorne links
    plushie = render(str(ROOT / "src/test/resources/skins/steve.png"), 360, -20, 18, False)
    icon.alpha_composite(soft_shadow(plushie, 0.5, 12), (30, 150))
    icon.alpha_composite(plushie, (18, 136))

    icon.putalpha(Image.fromarray(np.minimum(np.array(icon.getchannel("A")), np.array(mask))))
    out = ROOT / "release/modrinth/icon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out)
    icon.resize((128, 128), Image.LANCZOS).save(ROOT / "src/client/resources/assets/skintotems/icon.png")
    print(f"✓ {out}")


if __name__ == "__main__":
    main()
