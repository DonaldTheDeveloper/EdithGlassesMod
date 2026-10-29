import os
p = os.path.expanduser("~/EdithGlassesMod/tools/gen_model_from_glb.py")
s = open(p, encoding="utf-8").read()


def rep(a, b):
    global s
    if a not in s:
        raise SystemExit("missing anchor: " + a[:70])
    s = s.replace(a, b, 1)


# brown case + richer palette (max 16 colours: 4x4 cells of the 16x16 texture)
rep('''    "orange":  ((226, 104, 22, 255), 0),
    "leather": ((120, 58, 24, 255), 0),
    "hinge":   ((92, 52, 14, 255), 0),
}''', '''    "orange":  ((142, 92, 54, 255), 0),     # case shell: brown (key name kept from the first version)
    "leather": ((92, 58, 34, 255), 0),      # case lining: dark brown
    "hinge":   ((62, 40, 24, 255), 0),      # case hinge: darkest brown
    "lens_hi": ((205, 226, 255, 205), 1),
    "lens_lo": ((132, 170, 238, 205), 1),
    "silver_dk": ((128, 134, 146, 255), 4),
    "orange_hi": ((170, 114, 70, 255), 0),  # lighter brown on the upper edge of the case
}''')

# shade lens / silver / case by height after voxelising
rep('''    return {k: v[0] for k, v in vox.items()}, origin, allp''', '''    occ = {k: v[0] for k, v in vox.items()}
    ys = [k[1] for k in occ]
    lo_y, hi_y = min(ys), max(ys)
    lens_y = [k[1] for k, c in occ.items() if c == "lens"]
    for k, c in list(occ.items()):
        if c == "lens" and lens_y:
            f = (k[1] - min(lens_y)) / max(1, max(lens_y) - min(lens_y))
            occ[k] = "lens_hi" if f > 0.7 else ("lens_lo" if f < 0.25 else "lens")
        elif c == "silver":
            f = (k[1] - lo_y) / max(1, hi_y - lo_y)
            if f < 0.3:
                occ[k] = "silver_dk"
        elif c == "orange":
            if (k[1] + 1, ) and (k[0], k[1] + 1, k[2]) not in occ:
                occ[k] = "orange_hi"
    return occ, origin, allp''')

rep('worn_elements = build(worn, to_item, "worn (glasses)", 0.26)', 'worn_elements = build(worn, to_item, "worn (glasses)", 0.18)')
rep('held_elements = build(held, to_item_case, "held (glasses + case)", 0.22)', 'held_elements = build(held, to_item_case, "held (glasses + case)", 0.17)')
open(p, "w", encoding="utf-8").write(s)
print("patched")
