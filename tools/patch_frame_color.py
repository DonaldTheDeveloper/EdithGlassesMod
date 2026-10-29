import os
p = os.path.expanduser("~/EdithGlassesMod/tools/gen_model_from_glb.py")
s = open(p, encoding="utf-8").read()


def rep(a, b):
    global s
    if a not in s:
        raise SystemExit("missing anchor: " + a[:80])
    s = s.replace(a, b, 1)


# frame metal becomes its own colour so we can decide per voxel: silver only on the thin brow bar, black everywhere else
rep('    "lens_hi": ((205, 226, 255, 205), 1),', '    "fmetal":  ((24, 25, 30, 255), 4),      # frame metal: resolved to black/silver after voxelising\n    "lens_hi": ((205, 226, 255, 205), 1),')
rep('        if n == "Frame" and m != "Glow":\n            keep.append((q, "silver" if m == "gloss-metal" else "black"))',
    '        if n == "Frame" and m != "Glow":\n            keep.append((q, "fmetal" if m == "gloss-metal" else "black"))')
rep('        elif n == "Nose=Pads":\n            keep.append((q, "silver" if m == "gloss-metal" else "dark"))',
    '        elif n == "Nose=Pads":\n            keep.append((q, "silver" if m == "gloss-metal" else "black"))')

# post-process: fmetal -> silver in the top band (brow bar), otherwise black; no darker-silver patches
rep('''        elif c == "silver":
            f = (k[1] - lo_y) / max(1, hi_y - lo_y)
            if f < 0.3:
                occ[k] = "silver_dk"''', '''        elif c == "fmetal":
            fy = [kk[1] for kk, cc in occ.items() if cc in ("fmetal", "black", "lens", "lens_hi", "lens_lo")]
            top = max(fy)
            span = max(1, top - min(fy))
            occ[k] = "silver" if (top - k[1]) / span <= 0.16 else "black"''')
open(p, "w", encoding="utf-8").write(s)
print("patched")
