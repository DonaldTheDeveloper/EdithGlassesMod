"""Procedurally generates the EDITH-style glasses texture (original art, nothing copied).

64x64 atlas, four 32x32 quadrants addressed in model UV units (16 = full width):
  top-left  black carbon frame      top-right  blue lens (translucent)
  bottom-left  silver brow bar      bottom-right  bronze arms / pads
"""
import os, random
from PIL import Image

random.seed(7)
S = 64
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px = img.load()

def clamp(v): return max(0, min(255, int(v)))

for y in range(32):
    for x in range(32):
        # black frame with faint carbon weave
        weave = 6 if ((x // 2 + y // 2) % 2) else 0
        n = random.randint(-3, 3)
        v = 22 + weave + n
        px[x, y] = (clamp(v), clamp(v + 1), clamp(v + 4), 255)
        # lens: deep blue, brighter towards a diagonal glint, edge tinted white
        t = (x + y) / 62.0
        r = 170 + 40 * max(0, 1 - abs(t - 0.35) * 4)
        g = 200 + 30 * max(0, 1 - abs(t - 0.35) * 4)
        b = 250 + 5 * max(0, 1 - abs(t - 0.35) * 4)
        edge = min(x, y, 31 - x, 31 - y)
        if edge < 2:
            r, g, b = 200, 225, 255
        px[x + 32, y] = (clamp(r), clamp(g), clamp(b), 225 if edge >= 2 else 240)
        # silver
        s = 170 + 40 * (1 - abs(y - 16) / 16.0) + random.randint(-4, 4)
        px[x, y + 32] = (clamp(s), clamp(s + 3), clamp(s + 8), 255)
        # bronze
        k = 0.75 + 0.25 * (1 - abs(y - 16) / 16.0)
        px[x + 32, y + 32] = (clamp(150 * k), clamp(108 * k), clamp(62 * k), 255)

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "edithglasses", "textures", "block", "edith_glasses.png")
img.save(out)
print("wrote", os.path.abspath(out))
