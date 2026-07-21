from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math
import random

ROOT = Path(__file__).resolve().parent.parent
ASSET = ROOT / "app/src/main/assets/aether"
LAYER = ASSET / "layers"
OBJ = ASSET / "objects"
DOCS = ROOT / "docs"
RES = ROOT / "app/src/main/res/drawable"
for folder in (LAYER, OBJ, DOCS, RES):
    folder.mkdir(parents=True, exist_ok=True)

# Logical world size used by the renderer.
W, H = 2400, 1000
# Render at 2x and keep that higher resolution in the final PNGs.
SCALE = 2
SW, SH = W * SCALE, H * SCALE
random.seed(8011)


def transparent(size=(SW, SH)):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def pts(values):
    return [(int(x * SCALE), int(y * SCALE)) for x, y in values]


def save_layer(image: Image.Image, name: str):
    image.save(LAYER / name, optimize=True)


def draw_peak(draw, cx, base, height, half, base_color, left_color, right_color, alpha=255, ridge_shift=0.0):
    ridge_x = cx + half * ridge_shift
    silhouette = [
        (cx - half, base),
        (cx - half * 0.82, base - height * 0.10),
        (cx - half * 0.56, base - height * 0.34),
        (cx - half * 0.28, base - height * 0.60),
        (ridge_x, base - height),
        (cx + half * 0.14, base - height * 0.79),
        (cx + half * 0.36, base - height * 0.58),
        (cx + half * 0.60, base - height * 0.33),
        (cx + half * 0.84, base - height * 0.12),
        (cx + half, base),
        (cx + half, H),
        (cx - half, H),
    ]
    draw.polygon(pts(silhouette), fill=(*base_color, alpha))
    left_facet = [
        (ridge_x, base - height),
        (cx - half * 0.08, base - height * 0.83),
        (cx - half * 0.32, base - height * 0.48),
        (cx - half * 0.12, base - height * 0.22),
        (ridge_x, base),
    ]
    right_facet = [
        (ridge_x, base - height),
        (cx + half * 0.10, base - height * 0.80),
        (cx + half * 0.40, base - height * 0.36),
        (cx + half * 0.18, base - height * 0.16),
        (ridge_x, base),
    ]
    draw.polygon(pts(left_facet), fill=(*left_color, int(alpha * 0.42)))
    draw.polygon(pts(right_facet), fill=(*right_color, int(alpha * 0.33)))


def mountain_range(name: str, anchors, colors, alpha=255):
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    for cx, base, height, half, shift in anchors:
        draw_peak(draw, cx, base, height, half, colors[0], colors[1], colors[2], alpha, shift)
    save_layer(image, name)


# Wide layered ranges with a clear, readable hero peak.
mountain_range(
    "mountains_far.png",
    [
        (180, 740, 170, 220, -0.04), (520, 720, 180, 250, 0.02),
        (900, 735, 155, 210, -0.03), (1240, 730, 190, 255, 0.02),
        (1620, 728, 165, 230, -0.04), (1990, 742, 150, 210, 0.03),
        (2290, 750, 120, 160, -0.02),
    ],
    ((121, 112, 145), (186, 171, 193), (67, 64, 86)),
    198,
)

mountain_range(
    "mountains_mid.png",
    [
        (260, 800, 245, 300, -0.02), (760, 815, 270, 330, 0.03),
        (1160, 802, 230, 285, -0.03), (1660, 810, 250, 310, 0.03),
        (2140, 820, 210, 250, -0.02),
    ],
    ((90, 84, 118), (176, 155, 181), (39, 42, 63)),
    228,
)

mountain_range(
    "mountains_hero.png",
    [
        (1200, 852, 420, 470, -0.05),
        (720, 845, 280, 355, 0.03),
        (1815, 854, 300, 380, -0.01),
        (320, 876, 180, 230, 0.03),
        (2280, 884, 160, 205, -0.02),
    ],
    ((63, 57, 87), (202, 181, 196), (25, 28, 48)),
    255,
)

mountain_range(
    "mountains_near.png",
    [
        (140, 900, 160, 220, -0.05), (470, 892, 140, 190, 0.02),
        (850, 888, 175, 240, -0.03), (1240, 905, 150, 200, 0.03),
        (1605, 896, 168, 225, -0.02), (2010, 902, 145, 195, 0.02),
        (2335, 912, 120, 150, -0.03),
    ],
    ((42, 42, 66), (121, 104, 136), (15, 19, 34)),
    244,
)

# Snow cap map aligned to hero peaks.
snow = transparent()
sd = ImageDraw.Draw(snow, "RGBA")
for cx, base, height, half, _ in [
    (1200, 852, 420, 470, -0.05), (720, 845, 280, 355, 0.03), (1815, 854, 300, 380, -0.01)
]:
    top = base - height
    cap = [
        (cx, top), (cx - half * 0.13, top + height * 0.17), (cx - half * 0.07, top + height * 0.14),
        (cx - half * 0.01, top + height * 0.28), (cx + half * 0.05, top + height * 0.18),
        (cx + half * 0.12, top + height * 0.35), (cx + half * 0.18, top + height * 0.38),
    ]
    sd.polygon(pts(cap), fill=(237, 232, 237, 222))
save_layer(snow, "snow_caps.png")


def draw_pine(draw, cx, ground, height, width, color, alpha=255, sparse=False, dead=False, seed=0):
    rnd = random.Random(seed)
    cx *= SCALE
    ground *= SCALE
    height *= SCALE
    width *= SCALE
    trunk = max(4 * SCALE, int(width * 0.075))
    draw.rectangle((cx - trunk / 2, ground - height * 0.76, cx + trunk / 2, ground), fill=(*color, alpha))
    if dead:
        for frac, side, length in [(0.30, -1, .34), (.43, 1, .26), (.58, -1, .40), (.68, 1, .24)]:
            y = ground - height * frac
            draw.line((cx, y, cx + side * width * length, y - height * .07),
                      fill=(*color, alpha), width=max(2 * SCALE, int(width * .020)))
        return
    tiers = 8 if not sparse else 5
    for i in range(tiers):
        t = i / max(1, tiers - 1)
        y = ground - height * (.16 + t * .68)
        half = width * (.52 - t * .36) * rnd.uniform(.88, 1.06)
        tier_h = height * (.18 - t * .012)
        if sparse and i % 2:
            half *= .60
        poly = [
            (cx, y - tier_h * .74),
            (cx - half, y + tier_h * .34),
            (cx - half * .22, y + tier_h * .12),
            (cx + half * .05, y + tier_h * .26),
            (cx + half, y + tier_h * .32),
        ]
        draw.polygon(poly, fill=(*color, alpha))
    for frac, side in [(0.42, -1), (0.55, 1)]:
        y = ground - height * frac
        draw.line((cx, y, cx + side * width * .22, y - height * .04),
                  fill=(*color, alpha), width=max(2 * SCALE, int(width * .016)))


def grouped_forest(name: str, ground, color, alpha, back=False):
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    rnd = random.Random(8121 if back else 8122)
    # Strong edge grouping; central area remains lighter to expose the hero mountain.
    clusters = [(0, 360), (410, 770), (1600, 1980), (2030, 2390)] if back else [(0, 280), (330, 640), (1770, 2080), (2130, 2390)]
    for cluster_index, (start_x, end_x) in enumerate(clusters):
        spacing = 42 if back else 60
        x = start_x + rnd.randint(0, 24)
        while x < end_x:
            h = rnd.randint(84, 150) if back else rnd.randint(120, 230)
            w = h * rnd.uniform(.19, .28)
            draw_pine(draw, x, ground + rnd.randint(-9, 9), h, w, color, alpha,
                      sparse=rnd.random() < .22, dead=rnd.random() < (0.04 if not back else 0.02),
                      seed=8200 + cluster_index * 200 + int(x))
            x += spacing + rnd.randint(-16, 20)
    save_layer(image, name)

grouped_forest("forest_far.png", 845, (55, 54, 76), 132, back=True)
grouped_forest("forest_mid.png", 915, (24, 28, 44), 208, back=False)

# Gentle middle ridge and a darker foreground ridge.
def terrain_curve(name, front=False):
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    def f(x):
        local = (x % W) / W * math.tau
        if front:
            return 82 + math.sin(local + 1.06) * 18 + math.sin(local * 2 + .28) * 7 + math.sin(local * 3 + 1.4) * 5
        return 206 + math.sin(local + .38) * 26 + math.sin(local * 2 + 1.1) * 13 + math.sin(local * 3 + .45) * 7
    boundary = [(x, H - f(x)) for x in range(0, W + 1, 8)]
    main = (8, 16, 29, 255) if front else (27, 31, 48, 255)
    draw.polygon(pts(boundary + [(W, H), (0, H)]), fill=main)
    if not front:
        echo = [(x, H - f(x) + 34 + math.sin(x * .010) * 6) for x in range(0, W + 1, 8)]
        draw.polygon(pts(echo + [(W, H), (0, H)]), fill=(58, 54, 74, 96))
    save_layer(image, name)

terrain_curve("hill_mid.png", front=False)
terrain_curve("hill_front.png", front=True)

# Broad valley haze.
fog = transparent()
fd = ImageDraw.Draw(fog, "RGBA")
rnd = random.Random(8301)
for _ in range(16):
    x = rnd.randint(-260, W)
    y = rnd.randint(525, 825)
    rw = rnd.randint(420, 980)
    rh = rnd.randint(38, 105)
    fd.ellipse((x * SCALE, y * SCALE, (x + rw) * SCALE, (y + rh) * SCALE),
               fill=(216, 206, 224, rnd.randint(18, 46)))
fog = fog.filter(ImageFilter.GaussianBlur(44 * SCALE))
save_layer(fog, "fog_valley.png")

# Clouds: softer, wider and more cinematic.
for name, count, yrange, alpha, blur, seed in [
    ("clouds_far.png", 8, (170, 420), 54, 24, 8401),
    ("clouds_near.png", 7, (140, 600), 76, 16, 8402),
]:
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    rnd = random.Random(seed)
    for _ in range(count):
        x = rnd.randint(-260, W)
        y = rnd.randint(*yrange)
        rw = rnd.randint(300, 620)
        rh = rnd.randint(46, 96)
        color = (196, 190, 206, alpha)
        draw.ellipse((x * SCALE, y * SCALE, (x + rw) * SCALE, (y + rh) * SCALE), fill=color)
        draw.ellipse(((x + rw * .12) * SCALE, (y - rh * .34) * SCALE,
                      (x + rw * .46) * SCALE, (y + rh * .52) * SCALE), fill=color)
        draw.ellipse(((x + rw * .40) * SCALE, (y - rh * .20) * SCALE,
                      (x + rw * .80) * SCALE, (y + rh * .58) * SCALE), fill=color)
        draw.rectangle(((x + rw * .08) * SCALE, (y + rh * .18) * SCALE,
                        (x + rw * .84) * SCALE, (y + rh * .52) * SCALE), fill=color)
    image = image.filter(ImageFilter.GaussianBlur(blur * SCALE))
    save_layer(image, name)

# Stars with slightly different sizes.
stars = transparent()
st = ImageDraw.Draw(stars, "RGBA")
rnd = random.Random(8501)
for _ in range(260):
    x = rnd.randrange(SW)
    y = rnd.randrange(int(SH * .60))
    radius = rnd.choice([1, 1, 1, 2, 2, 3]) * SCALE / 2
    st.ellipse((x - radius, y - radius, x + radius, y + radius),
               fill=(245, 240, 228, rnd.randint(50, 210)))
save_layer(stars, "stars.png")


def save_obj(image, name):
    image.save(OBJ / name, optimize=True)

# Higher-detail tree objects.
def make_pine(name, width, height, color, sparse=False, dead=False, seed=0):
    image = Image.new("RGBA", (width * SCALE, height * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image, "RGBA")
    draw_pine(draw, width / 2, height - 3, height * .92, width * .84, color, 255,
              sparse=sparse, dead=dead, seed=seed)
    save_obj(image, name)

make_pine("pine_tall.png", 340, 820, (7, 15, 27), seed=8601)
make_pine("pine_medium.png", 260, 610, (10, 18, 30), seed=8602)
make_pine("pine_sparse.png", 280, 700, (9, 16, 28), sparse=True, seed=8603)
make_pine("pine_dead.png", 190, 470, (10, 17, 28), dead=True, seed=8604)

# Lantern and campfire remain compact but cleaner.
lantern = Image.new("RGBA", (160, 420), (0, 0, 0, 0))
ld = ImageDraw.Draw(lantern, "RGBA")
ld.rectangle((73, 118, 87, 418), fill=(7, 11, 18, 255))
ld.rounded_rectangle((50, 92, 110, 166), radius=10, fill=(23, 24, 30, 255))
ld.rounded_rectangle((58, 104, 102, 154), radius=8, fill=(247, 212, 132, 255))
ld.line((50, 92, 80, 60, 110, 92), fill=(15, 15, 22, 255), width=5)
save_obj(lantern, "lantern.png")

em = Image.new("RGBA", (160, 420), (0, 0, 0, 0))
ep = em.load(); cx, cy = 80, 126
for y in range(420):
    for x in range(160):
        distance = math.hypot(x - cx, y - cy) / 130
        if distance < 1:
            ep[x, y] = (255, 208, 130, int((1 - distance) ** 2 * 215))
em = em.filter(ImageFilter.GaussianBlur(10))
save_obj(em, "lantern_emission.png")

fire = Image.new("RGBA", (200, 190), (0, 0, 0, 0))
fd = ImageDraw.Draw(fire, "RGBA")
fd.line((44, 155, 148, 122), fill=(34, 24, 24, 255), width=16)
fd.line((48, 123, 152, 158), fill=(34, 24, 24, 255), width=16)
fd.polygon([(100, 30), (58, 121), (94, 101), (112, 142), (154, 86), (124, 54)], fill=(255, 140, 76, 255))
fd.polygon([(100, 55), (77, 120), (101, 104), (114, 128), (131, 89)], fill=(255, 227, 140, 255))
save_obj(fire, "campfire.png")

fem = Image.new("RGBA", (200, 190), (0, 0, 0, 0))
fp = fem.load(); cx, cy = 100, 95
for y in range(190):
    for x in range(200):
        distance = math.hypot((x - cx) * .82, y - cy) / 120
        if distance < 1:
            fp[x, y] = (255, 154, 83, int((1 - distance) ** 2 * 205))
fem = fem.filter(ImageFilter.GaussianBlur(11))
save_obj(fem, "campfire_emission.png")

# Generic glow texture.
glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
gp = glow.load(); center = 127.5
for y in range(256):
    for x in range(256):
        radius = math.hypot(x - center, y - center) / center
        if radius <= 1:
            gp[x, y] = (255, 255, 255, int(max(0, 1 - radius) ** 2 * 255))
save_obj(glow, "glow.png")

# Better sun and moon with internal shading.
sun = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
sd = ImageDraw.Draw(sun, "RGBA")
for r, a in [(82, 255), (86, 52), (92, 26)]:
    sd.ellipse((96-r, 96-r, 96+r, 96+r), fill=(255, 239, 190, a))
# subtle limb darkening / roundness
shade = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
sp = shade.load()
for y in range(192):
    for x in range(192):
        dx = (x - 96) / 82
        dy = (y - 96) / 82
        d = dx*dx + dy*dy
        if d <= 1:
            light = max(0.0, 1.15 - math.hypot(dx + 0.25, dy + 0.18))
            shadow = max(0.0, min(1.0, math.hypot(dx - 0.28, dy - 0.18) - 0.10))
            alpha = int(max(0, shadow * 58 - light * 26))
            sp[x, y] = (210, 170, 102, alpha)
sun = Image.alpha_composite(sun, shade).filter(ImageFilter.GaussianBlur(0.6))
save_obj(sun, "sun_disc.png")

moon = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
md = ImageDraw.Draw(moon, "RGBA")
# earthshine disc
md.ellipse((38, 28, 154, 148), fill=(176, 184, 206, 38))
md.ellipse((34, 24, 150, 144), fill=(236, 240, 235, 255))
# shadow body
md.ellipse((76, 16, 184, 132), fill=(0, 0, 0, 165))
moon = moon.filter(ImageFilter.GaussianBlur(0.6))
save_obj(moon, "moon_crescent.png")

noise = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
np = noise.load(); rnd = random.Random(8701)
for y in range(256):
    for x in range(256):
        value = rnd.randint(0, 255)
        np[x, y] = (value, value, value, 66)
noise = noise.filter(ImageFilter.GaussianBlur(2.2))
save_obj(noise, "noise_soft.png")

# Quick preview render for docs and wallpaper thumbnail.
def gradient(size):
    w, h = size
    out = Image.new("RGBA", size)
    px = out.load()
    top = (17, 24, 52); mid = (82, 77, 118); bottom = (214, 150, 136)
    for y in range(h):
        t = y / max(1, h - 1)
        if t < .66:
            q = t / .66
            c = tuple(int(top[i] * (1 - q) + mid[i] * q) for i in range(3))
        else:
            q = (t - .66) / .34
            c = tuple(int(mid[i] * (1 - q) + bottom[i] * q) for i in range(3))
        for x in range(w):
            px[x, y] = (*c, 255)
    return out


def compose_preview(size, landscape=False):
    out = gradient(size)
    w, h = size
    scale = h / H

    def layer(name, alpha=1.0):
        im = Image.open(LAYER / name).convert("RGBA")
        im = im.resize((int(LAYER_WIDTH := W * scale), h), Image.Resampling.LANCZOS)
        if alpha < 1:
            im.putalpha(im.getchannel("A").point(lambda v: int(v * alpha)))
        x = (w - im.width) // 2
        out.alpha_composite(im, (x, 0))

    layer("stars.png", .72)
    # celestial
    sx, sy = int(w * .71), int(h * .26)
    g = Image.open(OBJ / "glow.png").resize((int(h * .22), int(h * .22)), Image.Resampling.LANCZOS)
    tint = Image.new("RGBA", g.size, (245, 182, 120, 0))
    tint.putalpha(g.getchannel("A").point(lambda v: int(v * .36)))
    out.alpha_composite(tint, (sx - g.width // 2, sy - g.height // 2))
    sun_im = Image.open(OBJ / "sun_disc.png").resize((int(h * .06), int(h * .06)), Image.Resampling.LANCZOS)
    out.alpha_composite(sun_im, (sx - sun_im.width // 2, sy - sun_im.height // 2))

    for name, alpha in [
        ("clouds_far.png", .32), ("mountains_far.png", .70), ("fog_valley.png", .24),
        ("mountains_mid.png", .82), ("mountains_hero.png", 1.0), ("snow_caps.png", .72),
        ("mountains_near.png", .93), ("forest_far.png", .68), ("forest_mid.png", .82),
        ("hill_mid.png", 1.0), ("clouds_near.png", .28), ("hill_front.png", 1.0),
    ]:
        layer(name, alpha)

    def place_obj(name, x_ratio, base_ratio, height_ratio):
        im = Image.open(OBJ / name).convert("RGBA")
        th = int(h * height_ratio)
        tw = int(im.width * th / im.height)
        im = im.resize((tw, th), Image.Resampling.LANCZOS)
        x = int(w * x_ratio - tw / 2)
        y = int(h * base_ratio - th)
        out.alpha_composite(im, (x, y))

    # Strong edge framing only.
    for args in [
        ("pine_tall.png", .06, .83, .27), ("pine_sparse.png", .17, .84, .20),
        ("pine_medium.png", .28, .86, .15), ("pine_medium.png", .80, .86, .15),
        ("pine_sparse.png", .90, .84, .19), ("pine_tall.png", .97, .83, .24),
    ]:
        place_obj(*args)
    for xr, br in [(.16, .80), (.63, .87)]:
        g2 = Image.open(OBJ / "lantern_emission.png").convert("RGBA")
        th = int(h * .12); tw = int(g2.width * th / g2.height)
        g2 = g2.resize((tw, th), Image.Resampling.LANCZOS)
        out.alpha_composite(g2, (int(w * xr - tw / 2), int(h * br - th)))
        place_obj("lantern.png", xr, br, .12)

    vig = Image.new("RGBA", size, (0, 0, 0, 0))
    vp = vig.load()
    for y in range(h):
        alpha = int(max(0, (y / h - .58) / .42) * 138)
        for x in range(w):
            vp[x, y] = (4, 8, 18, alpha)
    return Image.alpha_composite(out, vig).convert("RGB")

portrait = compose_preview((1080, 1920))
portrait.save(DOCS / "renderer-v8-organized-preview.png", quality=95)
portrait.resize((540, 960), Image.Resampling.LANCZOS).save(RES / "wallpaper_thumb.png", optimize=True)
landscape = compose_preview((1920, 1080), True)
landscape.save(DOCS / "renderer-v8-organized-preview-landscape.png", quality=95)
print("Generated AetherScape v0.8 visual assets")
