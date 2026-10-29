"""Generates assets/edithglasses/models/item/edith_glasses.json (stepped aviator-style EDITH glasses).

Item model coordinates: 16 units = 1 block. Glasses face north (-z), centred on x = 8.
"""
import json, os

BLACK = [0, 0, 8, 8]
LENS = [10, 2, 12, 4]
SILVER = [0, 8, 8, 16]
BRONZE = [8, 8, 16, 16]

els = []

def box(name, x0, y0, z0, x1, y1, z1, uv, faces=("north", "south", "up", "down", "west", "east")):
    els.append({
        "name": name,
        "from": [round(x0, 3), round(y0, 3), round(z0, 3)],
        "to": [round(x1, 3), round(y1, 3), round(z1, 3)],
        "faces": {f: {"uv": uv, "texture": "#glasses"} for f in faces},
    })

TOP, BOT = 9.6, 6.0          # lens y range
FZ0, FZ1 = 7.0, 8.4           # frame depth
STEP = 0.55

def outer_x(y):               # outer lens edge for the left eye: tapers inward toward the bottom
    return 1.5 + 1.4 * (TOP - y) / (TOP - BOT)

INNER = 7.2                   # nose-side lens edge (left eye)

def eye(mirror):
    def mx(a, b):
        return (a, b) if not mirror else (16 - b, 16 - a)
    side = "r" if mirror else "l"
    y = TOP
    i = 0
    while y - STEP > BOT - 0.01:
        y1, y0 = y, y - STEP
        ox = outer_x((y0 + y1) / 2)
        a, b = mx(ox - 0.6, ox)
        box(f"frame_out_{side}{i}", a, y0, FZ0, b, y1, FZ1, BLACK, ("north", "south", "west", "east"))
        a, b = mx(ox, INNER)
        box(f"lens_{side}{i}", a, y0, 7.15, b, y1, 7.45, LENS, ("north", "south"))
        a, b = mx(INNER, INNER + 0.6)
        box(f"frame_in_{side}{i}", a, y0, FZ0, b, y1, FZ1, BLACK, ("north", "south", "west", "east"))
        y = y0
        i += 1
    # top bar (thick), bottom bar (thinner)
    a, b = mx(outer_x(TOP) - 0.6, INNER + 0.6)
    box(f"frame_top_{side}", a, TOP, FZ0 - 0.1, b, TOP + 0.7, FZ1, BLACK)
    a, b = mx(outer_x(BOT) - 0.6, INNER + 0.6)
    box(f"frame_bot_{side}", a, BOT - 0.5, FZ0, b, BOT, FZ1, BLACK)
    # silver brow bar over the lens + end post
    a, b = mx(outer_x(TOP) - 0.4, INNER + 0.6)
    box(f"brow_{side}", a, TOP + 0.8, FZ0 - 0.6, b, TOP + 1.2, FZ0 - 0.2, SILVER)
    a, b = mx(outer_x(TOP) - 0.4, outer_x(TOP) + 0.2)
    box(f"brow_post_{side}", a, TOP + 0.7, FZ0 - 0.6, b, TOP + 1.3, FZ0 + 0.3, SILVER)
    # bronze temple arm, hinge and nose pad
    a, b = mx(0.0, 0.6)
    box(f"temple_{side}", a, 8.6, 7.6, b, 9.4, 15.6, SILVER)
    a, b = mx(0.3, 1.0)
    box(f"hinge_{side}", a, 8.4, 7.0, b, 9.6, 7.8, SILVER)
    a, b = mx(INNER - 0.1, INNER + 0.9)
    box(f"pad_{side}", a, BOT - 1.7, 6.2, b, BOT - 0.4, 7.0, BRONZE)

eye(False)
eye(True)
# bridge between the lenses (black, slightly higher than centre like the original)
box("bridge", 7.0, 8.2, 7.0, 9.0, 9.2, 8.4, BLACK)
box("brow_low", 1.2, TOP + 0.4, FZ0 - 0.5, 14.8, TOP + 0.7, FZ0 - 0.1, SILVER)
box("bridge_brow", 7.0, 11.0, 6.3, 9.0, 11.4, 6.8, SILVER)

model = {
    "textures": {"glasses": "edithglasses:block/edith_glasses", "particle": "#glasses"},
    "elements": els,
    "gui_light": "front",
    "display": {
        "gui": {"rotation": [14, -30, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "on_shelf": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 1.5, 1.13], "scale": [0.35, 0.35, 0.35]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 1.5, 1.13], "scale": [0.35, 0.35, 0.35]},
        "head": {"rotation": [0, 0, 0], "translation": [0, -0.5, -6.1], "scale": [1.0, 1.0, 1.0]},
    },
}

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "edithglasses", "models", "item", "edith_glasses.json")
with open(out, "w") as f:
    json.dump(model, f, indent=1)
print("wrote", os.path.abspath(out), len(els), "elements")
