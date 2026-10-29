"""Converts the BlenderKit "Tony Stark EDITH Eye Glasses" model (by Irfan Ma'ruf, royalty free) into Minecraft item models.

Minecraft item models can only hold boxes, so the mesh is voxelised and merged into few boxes per colour (colours come
from a 16x16 palette texture). Two models are written:
  edith_glasses.json       worn on the head: glasses only, temples swung open
  edith_glasses_case.json  held / inventory / ground: glasses lying in their case, temples folded
Run:  python tools/gen_model_from_glb.py [voxel_size_in_item_units]
"""
import json, math, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import glb_load

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "edithglasses")
VS = float(sys.argv[1]) if len(sys.argv) > 1 else 0.32       # voxel size in item units (16 = 1 block)
WIDTH_UNITS = 14.2                                              # worn: frame width in item units

# name -> (rgba, priority); higher priority wins inside one voxel
PALETTE = {
    "lens":    ((172, 204, 255, 205), 1),
    "black":   ((24, 25, 30, 255), 3),
    "silver":  ((186, 192, 203, 255), 4),
    "dark":    ((70, 74, 84, 255), 3),
    "orange":  ((142, 92, 54, 255), 0),     # case shell: brown (key name kept from the first version)
    "leather": ((92, 58, 34, 255), 0),      # case lining: dark brown
    "hinge":   ((62, 40, 24, 255), 0),      # case hinge: darkest brown
    "fmetal":  ((24, 25, 30, 255), 4),      # frame metal: resolved to black/silver after voxelising
    "lens_hi": ((205, 226, 255, 205), 1),
    "lens_lo": ((132, 170, 238, 205), 1),
    "silver_dk": ((128, 134, 146, 255), 4),
    "orange_hi": ((170, 114, 70, 255), 0),  # lighter brown on the upper edge of the case
}
ORDER = list(PALETTE)
CELL = {name: (i % 4, i // 4) for i, name in enumerate(ORDER)}

j, prims, _ = glb_load.load(os.path.join(HERE, "edith_blenderkit.glb"))
mats = j["materials"]


def mat_name(p):
    return mats[p["material"]]["name"] if p["material"] is not None else ""


def pick(include_case):
    keep = []
    for p in prims:
        n, m = p["node"], mat_name(p)
        q = dict(p, pos=p["pos"].copy())
        if n == "Frame" and m != "Glow":
            keep.append((q, "fmetal" if m == "gloss-metal" else "black"))
        elif n == "Lens":
            keep.append((q, "lens"))
        elif n == "Nose=Pads":
            keep.append((q, "black"))
        elif n in ("left-arm", "left-arm.001"):
            keep.append((q, "black"))
        elif include_case and n in ("Top-Case", "Bottom-Case", "Top-Hinge", "Bottom-Hinge"):
            keep.append((q, {"Metal.001": "orange", "Leather Fabric 01": "leather"}.get(m, "hinge")))
    return keep


def unfold_temples(keep):
    """Swing each folded temple 90 degrees about its hinge end (vertical axis = glTF Z) so it points backwards."""
    for node in ("left-arm", "left-arm.001"):
        parts = [(p, c) for p, c in keep if p["node"] == node]
        allpos = np.vstack([p["pos"] for p, _ in parts])
        block = next(p for p, c in parts if mats[p["material"]]["name"] != "gloss-metal")
        bar = next(p for p, c in parts if mats[p["material"]]["name"] == "gloss-metal")
        hinge_at_min = block["pos"][:, 0].mean() < bar["pos"][:, 0].mean()
        px = allpos[:, 0].min() if hinge_at_min else allpos[:, 0].max()
        py = float(np.median(bar["pos"][:, 1]))
        ang = math.radians(-90 if hinge_at_min else 90)
        ca, sa = math.cos(ang), math.sin(ang)
        for p, _ in parts:
            d = p["pos"][:, :2] - np.array([px, py])
            p["pos"][:, 0] = px + d[:, 0] * ca - d[:, 1] * sa
            p["pos"][:, 1] = py + d[:, 0] * sa + d[:, 1] * ca


def voxelise(keep, to_item, VS):
    allp = np.vstack([to_item(p["pos"]) for p, _ in keep])
    origin = allp.min(0) - 1e-3
    vox = {}
    for p, color in keep:
        P = to_item(p["pos"])
        pr = PALETTE[color][1]
        for a, b, c in P[p["idx"]]:
            e = max(np.linalg.norm(b - a), np.linalg.norm(c - a), np.linalg.norm(c - b))
            n = max(1, int(math.ceil(e / (VS * 0.45))))
            i, k = np.meshgrid(np.arange(n + 1), np.arange(n + 1), indexing="ij")
            m = i + k <= n
            u, v = i[m] / n, k[m] / n
            pts = a + np.outer(u, b - a) + np.outer(v, c - a)
            for q in np.unique(np.floor((pts - origin) / VS).astype(int), axis=0):
                key = tuple(q)
                if key not in vox or vox[key][1] < pr:
                    vox[key] = (color, pr)
    occ = {k: v[0] for k, v in vox.items()}
    ys = [k[1] for k in occ]
    lo_y, hi_y = min(ys), max(ys)
    lens_y = [k[1] for k, c in occ.items() if c == "lens"]
    for k, c in list(occ.items()):
        if c == "lens" and lens_y:
            f = (k[1] - min(lens_y)) / max(1, max(lens_y) - min(lens_y))
            occ[k] = "lens_hi" if f > 0.7 else ("lens_lo" if f < 0.25 else "lens")
        elif c == "fmetal":
            fy = [kk[1] for kk, cc in occ.items() if cc in ("fmetal", "black", "lens", "lens_hi", "lens_lo")]
            top = max(fy)
            span = max(1, top - min(fy))
            occ[k] = "silver" if (top - k[1]) / span <= 0.16 else "black"
        elif c == "orange":
            if (k[1] + 1, ) and (k[0], k[1] + 1, k[2]) not in occ:
                occ[k] = "orange_hi"
    return occ, origin, allp


def merge(occupied):  # voxel size independent
    used, boxes = set(), []
    for key in sorted(occupied, key=lambda t: (t[2], t[1], t[0])):
        if key in used:
            continue
        col = occupied[key]
        x, y, z = key
        ex = 1
        while occupied.get((x + ex, y, z)) == col and (x + ex, y, z) not in used:
            ex += 1
        ey = 1
        while all(occupied.get((x + i, y + ey, z)) == col and (x + i, y + ey, z) not in used for i in range(ex)):
            ey += 1
        ez = 1
        while all(occupied.get((x + i, y + j_, z + ez)) == col and (x + i, y + j_, z + ez) not in used
                  for i in range(ex) for j_ in range(ey)):
            ez += 1
        for i in range(ex):
            for j_ in range(ey):
                for k in range(ez):
                    used.add((x + i, y + j_, z + k))
        boxes.append((col, (x, y, z), (ex, ey, ez)))
    return boxes


def covered(occupied, face, lo, size):
    """True when every voxel next to this face is occupied (the face can never be seen)."""
    x, y, z = lo
    ex, ey, ez = size
    if face in ("west", "east"):
        X = x - 1 if face == "west" else x + ex
        return all((X, y + a, z + b) in occupied for a in range(ey) for b in range(ez))
    if face in ("down", "up"):
        Y = y - 1 if face == "down" else y + ey
        return all((x + a, Y, z + b) in occupied for a in range(ex) for b in range(ez))
    Z = z - 1 if face == "north" else z + ez
    return all((x + a, y + b, Z) in occupied for a in range(ex) for b in range(ey))


LAST = {}


def build(keep, to_item, name, VS):
    occupied, origin, allp = voxelise(keep, to_item, VS)
    LAST[name] = occupied
    boxes = merge(occupied)
    elements = []
    for col, lo, size in boxes:
        cx_, cy_ = CELL[col]
        uv = [cx_ * 4 + 1, cy_ * 4 + 1, cx_ * 4 + 3, cy_ * 4 + 3]
        faces = {f: {"uv": uv, "texture": "#glasses"} for f in ("north", "south", "east", "west", "up", "down")
                 if not covered(occupied, f, lo, size)}
        if not faces:
            continue
        fr = [round(float(origin[i] + lo[i] * VS), 3) for i in range(3)]
        to = [round(float(origin[i] + (lo[i] + size[i]) * VS), 3) for i in range(3)]
        elements.append({"from": fr, "to": to, "faces": faces})
    print("%-22s voxels %5d  boxes %4d  elements %4d  extent %s" % (
        name, len(occupied), len(boxes), len(elements), np.round(allp.max(0) - allp.min(0), 1)))
    return elements


# ---------------------------------------------------------------- worn model (glasses only, temples open)
worn = pick(False)
unfold_temples(worn)
frame = [p for p, c in worn if p["node"] in ("Frame", "Lens")]
fpos = np.vstack([p["pos"] for p in frame])
s = WIDTH_UNITS / (fpos[:, 0].max() - fpos[:, 0].min())
cx = (fpos[:, 0].max() + fpos[:, 0].min()) / 2
lens = next(p for p, c in worn if c == "lens")
cz = (lens["pos"][:, 2].max() + lens["pos"][:, 2].min()) / 2
yfront = fpos[:, 1].max()


def to_item(P):
    return np.c_[8 + (P[:, 0] - cx) * s, 8 + (cz - P[:, 2]) * s, 7.0 + (yfront - P[:, 1]) * s]


worn_elements = build(worn, to_item, "worn (glasses)", 0.18)

# ---------------------------------------------------------------- held model (glasses lying in the case, temples folded)
held = pick(True)
raw = np.vstack([to_item(p["pos"]) for p, _ in held])
lo, hi = raw.min(0), raw.max(0)
f = min(15.0 / (hi - lo).max(), 1.0)
mid = (lo + hi) / 2


def to_item_case(P):
    return 8 + (to_item(P) - mid) * f


held_elements = build(held, to_item_case, "held (glasses + case)", 0.17)

# ---------------------------------------------------------------- palette texture + model files
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for name, (cx_, cy_) in CELL.items():
    for dx in range(4):
        for dy in range(4):
            img.putpixel((cx_ * 4 + dx, cy_ * 4 + dy), PALETTE[name][0])
img.save(os.path.join(RES, "textures", "block", "edith_glasses.png"))

old = json.load(open(os.path.join(RES, "models", "item", "edith_glasses.json")))
worn_display = old["display"]
# eyes sit about 5 px down the head, and the face has a 3D outer layer: lower the glasses and bring them in front of it
worn_display["head"] = {"rotation": [0, 0, 0], "translation": [0, -2.2, -7.3], "scale": [1, 1, 1]}
held_display = {
    "gui": {"rotation": [24, 216, 0], "translation": [0, 0, 0], "scale": [1.15, 1.15, 1.15]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "on_shelf": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 90, 25], "translation": [1.13, 1.5, 1.13], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, -90, -25], "translation": [1.13, 1.5, 1.13], "scale": [0.4, 0.4, 0.4]},
    "head": worn_display["head"],
}
for fname, els, disp in (("edith_glasses", worn_elements, worn_display), ("edith_glasses_case", held_elements, held_display)):
    model = {"textures": {"glasses": "edithglasses:block/edith_glasses", "particle": "#glasses"},
             "elements": els, "gui_light": "front", "display": disp}
    path = os.path.join(RES, "models", "item", fname + ".json")
    with open(path, "w") as fh:
        json.dump(model, fh, separators=(",", ":"))
    print(fname, os.path.getsize(path) // 1024, "KB")

# item definition: worn on the head -> glasses only, everything else -> glasses in their case
definition = {
    "model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": "head", "model": {"type": "minecraft:model", "model": "edithglasses:item/edith_glasses"}}],
        "fallback": {"type": "minecraft:model", "model": "edithglasses:item/edith_glasses_case"},
    }
}
with open(os.path.join(RES, "items", "edith_glasses.json"), "w") as fh:
    json.dump(definition, fh, indent=2)
print("wrote item definition")


# ---------------------------------------------------------------- preview sheet (orthographic views of the voxels)
if "--preview" in sys.argv:
    def view(occ, axes, flip, scale=14):
        pts = sorted(occ.items(), key=lambda kv: kv[0][axes[2]] * flip[2])
        W = max(k[axes[0]] for k in occ) + 1
        H = max(k[axes[1]] for k in occ) + 1
        im = Image.new("RGB", (W * scale, H * scale), (205, 215, 230))
        for k, col in pts:
            x = k[axes[0]] if not flip[0] < 0 else W - 1 - k[axes[0]]
            y = H - 1 - k[axes[1]] if flip[1] > 0 else k[axes[1]]
            rgb = PALETTE[col][0][:3]
            for dx in range(scale):
                for dy in range(scale):
                    im.putpixel((x * scale + dx, y * scale + dy), rgb)
        return im
    for name, occ in LAST.items():
        ims = [view(occ, (0, 1, 2), (1, 1, 1)), view(occ, (0, 1, 2), (-1, 1, -1)), view(occ, (2, 1, 0), (1, 1, 1)), view(occ, (0, 2, 1), (1, 1, 1))]
        wd = sum(i.width for i in ims) + 10 * (len(ims) + 1)
        sheet = Image.new("RGB", (wd, max(i.height for i in ims) + 20), (255, 255, 255))
        x = 10
        for i in ims:
            sheet.paste(i, (x, 10)); x += i.width + 10
        sheet.save(os.path.join(HERE, "preview_" + name.split()[0] + ".png"))
        print("preview written for", name)
