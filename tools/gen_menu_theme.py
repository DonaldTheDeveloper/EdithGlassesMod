"""Builds the optional "EDITH menu theme" resource pack: every menu texture (inventory, containers, buttons, tabs,
sliders, advancements...) recoloured into dark holographic glass with bright cyan edges. The layouts stay exactly
vanilla, so all menus keep working. Source: the game's own GUI textures, recoloured locally (personal use).
usage: python gen_menu_theme.py <path to minecraft-client.jar>
"""
import io, os, sys, zipfile
import numpy as np
from PIL import Image

jar = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "resourcepacks", "edith_menu")

SKIP = ("gui/sprites/hud/", "gui/sprites/icon/", "gui/sprites/boss_bar/", "gui/title/", "gui/realms/", "gui/sprites/notification/")

# luminance -> colour, piecewise linear. Dark glass panels, mid tones teal, only the brightest pixels are glowing cyan.
STOPS = [
    (0.00, (0, 0, 0)),
    (0.20, (2, 10, 18)),
    (0.33, (4, 18, 30)),
    (0.55, (10, 40, 62)),
    (0.78, (22, 78, 108)),
    (0.90, (70, 170, 205)),
    (1.00, (170, 245, 255)),
]
xs = np.array([s[0] for s in STOPS])
cols = np.array([s[1] for s in STOPS], dtype=np.float64)


def recolor(img):
    a = np.asarray(img.convert("RGBA")).astype(np.float64)
    rgb, alpha = a[..., :3], a[..., 3]
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]) / 255.0
    outc = np.stack([np.interp(lum, xs, cols[:, c]) for c in range(3)], axis=-1)
    # keep a hint of the original hue for strongly coloured pixels (progress bars, enchant glints...)
    sat = rgb.max(-1) - rgb.min(-1)
    hint = np.clip(sat / 255.0 - 0.25, 0, 1)[..., None] * 0.35
    outc = outc * (1 - hint) + rgb * hint
    res = np.concatenate([np.clip(outc, 0, 255), alpha[..., None]], axis=-1).astype(np.uint8)
    return Image.fromarray(res, "RGBA")


count = 0
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if not name.startswith("assets/minecraft/textures/gui/") or not name.endswith(".png"):
            continue
        rel = name[len("assets/minecraft/"):]
        if any(s in name for s in SKIP):
            continue
        img = Image.open(io.BytesIO(z.read(name)))
        dest = os.path.join(out, "assets", "minecraft", *rel.split("/"))
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        recolor(img).save(dest)
        count += 1
        mcmeta = name + ".mcmeta"
        if mcmeta in z.namelist():  # nine-slice / animation info must stay
            open(dest + ".mcmeta", "wb").write(z.read(mcmeta))

# the menu background tile and separators live outside gui/sprites in some versions: covered above (gui/*.png)
os.makedirs(out, exist_ok=True)
open(os.path.join(out, "pack.mcmeta"), "w").write(
    '{ "pack": { "description": "EDITH holographic menus", "pack_format": 88, "min_format": [88, 0], "max_format": [88, 0] } }')
print("recoloured", count, "textures into", os.path.abspath(out))
