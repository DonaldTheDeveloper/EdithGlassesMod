"""Composes the E.D.I.T.H. Glasses mod logo and banner from the Blender render (glasses_2048.png, transparent)."""
import math
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

SRC = Image.open("glasses_2048.png").convert("RGBA")
SRC = SRC.crop(SRC.getbbox())
FONT = r"C:\Windows\Fonts\bahnschrift.ttf"
CYAN = (92, 208, 240)
BRIGHT = (216, 248, 255)


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except Exception:
        return ImageFont.load_default()


def radial_bg(w, h):
    y, x = np.mgrid[0:h, 0:w]
    cx, cy = w * 0.5, h * 0.42
    d = np.sqrt(((x - cx) / (w * 0.62)) ** 2 + ((y - cy) / (h * 0.62)) ** 2)
    d = np.clip(d, 0, 1)
    inner = np.array([10, 52, 86], dtype=float)
    outer = np.array([2, 8, 16], dtype=float)
    t = (d ** 1.15)[..., None]
    img = inner * (1 - t) + outer * t
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def hud_rings(w, h, cx, cy, scale):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    random.seed(4)

    def arc(r, a0, a1, width, alpha):
        box = [cx - r, cy - r, cx + r, cy + r]
        d.arc(box, a0, a1, fill=CYAN + (alpha,), width=width)

    def ticks(r, a0, a1, step, length, alpha):
        a = a0
        while a <= a1:
            rad = math.radians(a)
            x0, y0 = cx + r * math.cos(rad), cy + r * math.sin(rad)
            x1, y1 = cx + (r + length) * math.cos(rad), cy + (r + length) * math.sin(rad)
            d.line([x0, y0, x1, y1], fill=CYAN + (alpha,), width=max(1, int(2 * scale)))
            a += step

    base = (0.46 if w <= h * 1.2 else 0.34) * min(w, h)
    arc(base, 200, 340, int(3 * scale), 170)
    arc(base, 20, 160, int(3 * scale), 120)
    arc(base * 1.12, 250, 290, int(6 * scale), 200)
    arc(base * 1.12, 70, 110, int(6 * scale), 200)
    arc(base * 0.86, 0, 360, max(1, int(1 * scale)), 70)
    ticks(base * 1.0, 200, 340, 4, 10 * scale, 130)
    ticks(base * 1.0, 20, 160, 4, 10 * scale, 90)
    ticks(base * 0.86, 0, 359, 15, 6 * scale, 80)
    # corner brackets
    m, L = int(34 * scale), int(70 * scale)
    for (x, y, sx, sy) in [(m, m, 1, 1), (w - m, m, -1, 1), (m, h - m, 1, -1), (w - m, h - m, -1, -1)]:
        d.line([x, y, x + sx * L, y], fill=CYAN + (190,), width=max(2, int(3 * scale)))
        d.line([x, y, x, y + sy * L], fill=CYAN + (190,), width=max(2, int(3 * scale)))
    # scan lines
    for yy in range(0, h, max(3, int(4 * scale))):
        d.line([0, yy, w, yy], fill=(160, 230, 255, 9), width=1)
    return layer


def lens_mask(img):
    a = np.asarray(img).astype(int)
    r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    m = (al > 200) & (b > r + 35) & (b > 120)
    return Image.fromarray((m * 255).astype(np.uint8), "L")


def glasses_layer(width):
    g = SRC.resize((width, int(SRC.height * width / SRC.width)), Image.LANCZOS)
    mask = lens_mask(g).filter(ImageFilter.GaussianBlur(1.2))
    # lens: diagonal gradient from bright cyan-white to deep blue, plus a soft sweep of light
    w, h = g.size
    yy, xx = np.mgrid[0:h, 0:w]
    t = ((xx / w) * 0.7 + (yy / h) * 0.6)
    t = np.clip(t, 0, 1)
    top = np.array([150, 225, 255], dtype=float)
    bot = np.array([18, 92, 178], dtype=float)
    grad = top * (1 - t[..., None]) + bot * t[..., None]
    sweep = (np.clip(1 - np.abs((xx / w) * 1.0 - (yy / h) * 0.55 - 0.42) / 0.07, 0, 1) + 0.8 * np.clip(1 - np.abs((xx / w) * 1.0 - (yy / h) * 0.55 - 0.12) / 0.045, 0, 1))[..., None] * 90
    lens = Image.fromarray(np.clip(grad + sweep, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    out = g.copy()
    out.paste(lens, (0, 0), mask)
    # keep the original shading a little
    return out, mask


def compose(w, h, title_size, name):
    bg = radial_bg(w, h)
    scale = min(w, h) / 1024
    gw = int(min(w, h) * 0.76) if w <= h * 1.2 else int(h * 0.80)
    glasses, mask = glasses_layer(gw)
    cx = w // 2
    cy = int(h * 0.37)
    bg.alpha_composite(hud_rings(w, h, cx, cy, scale))

    # glow: blurred lens colour + frame highlights behind the glasses
    gl = Image.new("RGBA", glasses.size, (0, 0, 0, 0))
    gl.paste(Image.new("RGBA", glasses.size, (40, 170, 255, 255)), (0, 0), mask)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gx, gy = cx - glasses.width // 2, cy - glasses.height // 2
    glow.paste(gl, (gx, gy))
    big = glow.filter(ImageFilter.GaussianBlur(40 * scale))
    mid = glow.filter(ImageFilter.GaussianBlur(14 * scale))
    for layer, k in ((big, 0.9), (mid, 0.55)):
        a = np.asarray(layer).astype(float)
        a[..., 3] *= k
        bg.alpha_composite(Image.fromarray(a.astype(np.uint8), "RGBA"))

    # drop shadow then glasses
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sh.paste(Image.new("RGBA", glasses.size, (0, 0, 0, 160)), (gx + int(10 * scale), gy + int(22 * scale)), glasses.split()[3])
    bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14 * scale)))
    bg.alpha_composite(glasses, (gx, gy))

    # title
    d = ImageDraw.Draw(bg)
    f = font(title_size)
    title = "E.D.I.T.H."
    spacing = int(title_size * 0.16)
    tw = sum(d.textlength(c, font=f) for c in title) + spacing * (len(title) - 1)
    ty = int(h * 0.67) if w <= h * 1.2 else int(h * 0.70)
    x = (w - tw) / 2
    glow_t = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_t)
    xx = x
    for c in title:
        gd.text((xx, ty), c, font=f, fill=CYAN + (255,))
        xx += d.textlength(c, font=f) + spacing
    bg.alpha_composite(glow_t.filter(ImageFilter.GaussianBlur(10 * scale)))
    xx = x
    for c in title:
        d.text((xx, ty), c, font=f, fill=BRIGHT + (255,))
        xx += d.textlength(c, font=f) + spacing
    # subtitle with side lines
    f2 = font(int(title_size * 0.40))
    sub = "G L A S S E S"
    sw = d.textlength(sub, font=f2)
    sy = ty + int(title_size * 1.22)
    d.text(((w - sw) / 2, sy), sub, font=f2, fill=CYAN + (255,))
    ly = sy + int(title_size * 0.2)
    gap = int(title_size * 0.3)
    d.line([(w - sw) / 2 - gap - tw * 0.18, ly, (w - sw) / 2 - gap, ly], fill=CYAN + (200,), width=max(2, int(3 * scale)))
    d.line([(w + sw) / 2 + gap, ly, (w + sw) / 2 + gap + tw * 0.18, ly], fill=CYAN + (200,), width=max(2, int(3 * scale)))
    f3 = font(int(title_size * 0.2))
    tag = "VOICE AI  -  HOLOGRAPHIC HUD  -  CAMERA  -  FOR MINECRAFT"
    tgw = d.textlength(tag, font=f3)
    d.text(((w - tgw) / 2, sy + int(title_size * 0.62)), tag, font=f3, fill=(150, 200, 225, 255))

    # vignette
    y, x2 = np.mgrid[0:h, 0:w]
    dd = np.sqrt(((x2 - w / 2) / (w * 0.75)) ** 2 + ((y - h / 2) / (h * 0.75)) ** 2)
    v = np.clip(1 - 0.5 * dd ** 2.2, 0.55, 1)[..., None]
    arr = np.asarray(bg.convert("RGB")).astype(float) * v
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")
    out.save(name)
    return out


if __name__ == "__main__":
    big = compose(1024, 1024, 132, "edith_logo_1024.png")
    big.resize((512, 512), Image.LANCZOS).save("edith_logo_512.png")
    compose(1920, 1080, 150, "edith_banner_1920x1080.png")
    print("ok")
