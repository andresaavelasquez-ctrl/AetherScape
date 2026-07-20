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

W, H, SCALE = 2400, 1000, 2
SW, SH = W * SCALE, H * SCALE
random.seed(5060)


def transparent(size=(SW, SH)):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def pts(values):
    return [(int(x * SCALE), int(y * SCALE)) for x, y in values]


def save_layer(image: Image.Image, name: str):
    image.resize((W, H), Image.Resampling.LANCZOS).save(LAYER / name, optimize=True)


def draw_peak(draw, cx, base, height, half, base_color, light_color, dark_color, alpha=255):
    # A mountain with shoulders and a non-symmetric silhouette, not a single triangle.
    silhouette = [
        (cx - half, base),
        (cx - half * 0.74, base - height * 0.20),
        (cx - half * 0.52, base - height * 0.42),
        (cx - half * 0.30, base - height * 0.55),
        (cx - half * 0.12, base - height * 0.80),
        (cx, base - height),
        (cx + half * 0.13, base - height * 0.79),
        (cx + half * 0.30, base - height * 0.60),
        (cx + half * 0.55, base - height * 0.40),
        (cx + half * 0.76, base - height * 0.18),
        (cx + half, base),
        (cx + half, H),
        (cx - half, H),
    ]
    draw.polygon(pts(silhouette), fill=(*base_color, alpha))
    left_facet = [
        (cx, base - height),
        (cx - half * 0.12, base - height * 0.80),
        (cx - half * 0.38, base - height * 0.43),
        (cx - half * 0.18, base - height * 0.26),
        (cx, base),
    ]
    right_facet = [
        (cx, base - height),
        (cx + half * 0.16, base - height * 0.74),
        (cx + half * 0.46, base - height * 0.32),
        (cx + half * 0.20, base - height * 0.18),
        (cx, base),
    ]
    draw.polygon(pts(left_facet), fill=(*light_color, int(alpha * 0.38)))
    draw.polygon(pts(right_facet), fill=(*dark_color, int(alpha * 0.34)))


def generated_mountains(name, base, count, height_range, width_range, colors, seed):
    random.seed(seed)
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    x = -180
    for _ in range(count):
        half = random.randint(*width_range)
        height = random.randint(*height_range)
        cx = x + half
        draw_peak(draw, cx, base + random.randint(-16, 16), height, half,
                  colors[0], colors[1], colors[2], colors[3])
        x += int(half * random.uniform(1.25, 1.75))
    save_layer(image, name)


generated_mountains(
    "mountains_far.png", 690, 9, (125, 230), (160, 260),
    ((118, 108, 136), (210, 178, 183), (55, 53, 75), 210), 5101)
generated_mountains(
    "mountains_mid.png", 760, 7, (210, 330), (220, 330),
    ((90, 82, 110), (194, 157, 169), (42, 42, 62), 235), 5102)

# Hero layer is composed by hand so portrait always has a recognizable central peak.
hero = transparent()
hd = ImageDraw.Draw(hero, "RGBA")
draw_peak(hd, 1200, 805, 365, 440, (67, 61, 87), (187, 151, 164), (25, 27, 45), 255)
draw_peak(hd, 575, 820, 245, 300, (75, 68, 95), (168, 139, 155), (31, 31, 50), 238)
draw_peak(hd, 1840, 820, 275, 340, (63, 59, 84), (165, 137, 153), (24, 26, 43), 245)
draw_peak(hd, 2230, 835, 190, 245, (72, 66, 91), (154, 130, 147), (28, 29, 47), 230)
save_layer(hero, "mountains_hero.png")

generated_mountains(
    "mountains_near.png", 875, 8, (135, 240), (160, 265),
    ((45, 44, 64), (122, 101, 121), (17, 21, 36), 250), 5103)

# Separate snow maps.
snow = transparent()
sd = ImageDraw.Draw(snow, "RGBA")

def snow_cap(cx, base, height, half):
    top = base - height
    cap = [
        (cx, top),
        (cx - half * 0.11, top + height * 0.17),
        (cx - half * 0.05, top + height * 0.13),
        (cx - half * 0.01, top + height * 0.24),
        (cx + half * 0.05, top + height * 0.15),
        (cx + half * 0.11, top + height * 0.27),
        (cx + half * 0.16, top + height * 0.31),
    ]
    sd.polygon(pts(cap), fill=(234, 228, 234, 215))

snow_cap(1200, 805, 365, 440)
snow_cap(575, 820, 245, 300)
snow_cap(1840, 820, 275, 340)
save_layer(snow, "snow_caps.png")

# Pine drawing with irregular tiers and small dead branches.
def draw_pine(draw, cx, ground, height, width, color, alpha=255, sparse=False, dead=False, seed=0):
    rnd = random.Random(seed)
    cx *= SCALE
    ground *= SCALE
    height *= SCALE
    width *= SCALE
    trunk = max(4 * SCALE, int(width * 0.075))
    draw.rectangle((cx - trunk / 2, ground - height * 0.76, cx + trunk / 2, ground), fill=(*color, alpha))
    if dead:
        for frac, side, length in [(0.32, -1, .34), (.45, 1, .28), (.58, -1, .42), (.69, 1, .25)]:
            y = ground - height * frac
            draw.line((cx, y, cx + side * width * length, y - height * .07),
                      fill=(*color, alpha), width=max(2 * SCALE, int(width * .022)))
        return
    tiers = 7 if not sparse else 5
    for i in range(tiers):
        t = i / max(1, tiers - 1)
        y = ground - height * (.18 + t * .68)
        half = width * (.50 - t * .34) * rnd.uniform(.86, 1.08)
        tier_h = height * (.19 - t * .014)
        if sparse and i % 2:
            half *= .58
        poly = [
            (cx, y - tier_h * .76),
            (cx - half, y + tier_h * .30),
            (cx - half * .26, y + tier_h * .10),
            (cx + half * .08, y + tier_h * .24),
            (cx + half, y + tier_h * .32),
        ]
        draw.polygon(poly, fill=(*color, alpha))
    # two tiny branch stubs make silhouettes less artificial
    for frac, side in [(0.39, -1), (0.53, 1)]:
        y = ground - height * frac
        draw.line((cx, y, cx + side * width * .22, y - height * .035),
                  fill=(*color, alpha), width=max(2 * SCALE, int(width * .018)))


def forest_layer(name, ground, count, heights, color, alpha, seed):
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    rnd = random.Random(seed)
    step = W / count
    for i in range(count + 4):
        x = (i - 1) * step + rnd.uniform(-step * .28, step * .28)
        h = rnd.randint(*heights)
        w = h * rnd.uniform(.22, .31)
        draw_pine(draw, x, ground + rnd.randint(-10, 10), h, w, color, alpha,
                  sparse=rnd.random() < .20, seed=seed + i)
    save_layer(image, name)

forest_layer("forest_far.png", 845, 53, (82, 165), (51, 50, 70), 126, 5201)
forest_layer("forest_mid.png", 910, 38, (128, 238), (29, 32, 50), 205, 5202)

# Terrain layers match the Java analytic functions.
def mid_y(x):
    local = (x % W) / W * math.tau
    return 205 + math.sin(local + .35) * 34 + math.sin(local * 2 + 1.15) * 18


def front_y(x):
    local = (x % W) / W * math.tau
    return 86 + math.sin(local + 1.05) * 25 + math.sin(local * 2 + .25) * 12


def terrain_layer(name, function, color, secondary=None):
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    boundary = [(x, H - function(x)) for x in range(0, W + 1, 12)]
    draw.polygon(pts(boundary + [(W, H), (0, H)]), fill=(*color, 255))
    if secondary:
        offset = [(x, H - function(x) + 34 + math.sin(x * .009) * 7) for x in range(0, W + 1, 12)]
        draw.polygon(pts(offset + [(W, H), (0, H)]), fill=(*secondary, 120))
    save_layer(image, name)

terrain_layer("hill_mid.png", mid_y, (28, 31, 48), (53, 50, 68))
terrain_layer("hill_front.png", front_y, (9, 16, 29), (23, 27, 42))

# Fog bands.
fog = transparent()
fd = ImageDraw.Draw(fog, "RGBA")
rnd = random.Random(5301)
for _ in range(12):
    x = rnd.randint(-350, W)
    y = rnd.randint(500, 800)
    rw = rnd.randint(520, 980)
    rh = rnd.randint(50, 120)
    fd.ellipse((x * SCALE, y * SCALE, (x + rw) * SCALE, (y + rh) * SCALE),
               fill=(208, 196, 210, rnd.randint(18, 44)))
fog = fog.filter(ImageFilter.GaussianBlur(40 * SCALE))
save_layer(fog, "fog_valley.png")

# Cloud layers with long soft silhouettes.
for name, count, yrange, alpha, blur, seed in [
    ("clouds_far.png", 10, (150, 480), 50, 18, 5401),
    ("clouds_near.png", 8, (100, 610), 78, 12, 5402),
]:
    image = transparent()
    draw = ImageDraw.Draw(image, "RGBA")
    rnd = random.Random(seed)
    for _ in range(count):
        x = rnd.randint(-250, W)
        y = rnd.randint(*yrange)
        rw = rnd.randint(260, 520)
        rh = rnd.randint(38, 82)
        color = (194, 188, 202, alpha)
        draw.ellipse((x * SCALE, y * SCALE, (x + rw) * SCALE, (y + rh) * SCALE), fill=color)
        draw.ellipse(((x + rw * .14) * SCALE, (y - rh * .38) * SCALE,
                      (x + rw * .54) * SCALE, (y + rh * .56) * SCALE), fill=color)
        draw.ellipse(((x + rw * .43) * SCALE, (y - rh * .22) * SCALE,
                      (x + rw * .82) * SCALE, (y + rh * .62) * SCALE), fill=color)
    image = image.filter(ImageFilter.GaussianBlur(blur * SCALE))
    save_layer(image, name)

# Stars.
stars = transparent()
st = ImageDraw.Draw(stars, "RGBA")
rnd = random.Random(5501)
for _ in range(220):
    x = rnd.randrange(SW)
    y = rnd.randrange(int(SH * .60))
    radius = rnd.choice([1, 1, 1, 2, 2, 3]) * SCALE / 2
    st.ellipse((x - radius, y - radius, x + radius, y + radius),
               fill=(244, 239, 225, rnd.randint(65, 205)))
save_layer(stars, "stars.png")

# Object textures.
def make_pine(name, width, height, color, sparse=False, dead=False, seed=0):
    image = Image.new("RGBA", (width * SCALE, height * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image, "RGBA")
    draw_pine(draw, width / 2, height - 2, height * .92, width * .88,
              color, 255, sparse=sparse, dead=dead, seed=seed)
    image.resize((width, height), Image.Resampling.LANCZOS).save(OBJ / name, optimize=True)

make_pine("pine_tall.png", 270, 620, (8, 16, 28), seed=5601)
make_pine("pine_medium.png", 220, 440, (12, 20, 33), seed=5602)
make_pine("pine_sparse.png", 230, 520, (10, 18, 30), sparse=True, seed=5603)
make_pine("pine_dead.png", 170, 410, (12, 19, 30), dead=True, seed=5604)

# Lantern base/emission.
lw, lh = 110, 320
lantern = Image.new("RGBA", (lw * SCALE, lh * SCALE), (0, 0, 0, 0))
ld = ImageDraw.Draw(lantern, "RGBA")
ld.rectangle((51 * SCALE, 90 * SCALE, 59 * SCALE, 318 * SCALE), fill=(8, 12, 19, 255))
ld.rectangle((36 * SCALE, 72 * SCALE, 74 * SCALE, 128 * SCALE), fill=(22, 22, 27, 255))
ld.rectangle((42 * SCALE, 82 * SCALE, 68 * SCALE, 119 * SCALE), fill=(250, 207, 130, 255))
ld.line((36 * SCALE, 72 * SCALE, 55 * SCALE, 54 * SCALE, 74 * SCALE, 72 * SCALE),
        fill=(15, 16, 22, 255), width=4 * SCALE)
lantern.resize((lw, lh), Image.Resampling.LANCZOS).save(OBJ / "lantern.png", optimize=True)

emission = Image.new("RGBA", (lw * SCALE, lh * SCALE), (0, 0, 0, 0))
ep = emission.load()
cx, cy = 55 * SCALE, 100 * SCALE
for y in range(lh * SCALE):
    for x in range(lw * SCALE):
        distance = math.hypot(x - cx, y - cy) / (90 * SCALE)
        if distance < 1:
            ep[x, y] = (255, 205, 125, int((1 - distance) ** 2 * 215))
emission = emission.filter(ImageFilter.GaussianBlur(7 * SCALE)).resize((lw, lh), Image.Resampling.LANCZOS)
emission.save(OBJ / "lantern_emission.png", optimize=True)

# Campfire.
cw, ch = 160, 150
fire = Image.new("RGBA", (cw * SCALE, ch * SCALE), (0, 0, 0, 0))
fr = ImageDraw.Draw(fire, "RGBA")
fr.line((42 * SCALE, 130 * SCALE, 118 * SCALE, 105 * SCALE), fill=(34, 24, 24, 255), width=13 * SCALE)
fr.line((42 * SCALE, 105 * SCALE, 118 * SCALE, 130 * SCALE), fill=(34, 24, 24, 255), width=13 * SCALE)
fr.polygon(pts([(80, 20), (48, 100), (78, 84), (92, 120), (120, 75), (98, 52)]), fill=(255, 139, 76, 255))
fr.polygon(pts([(80, 46), (65, 98), (83, 86), (92, 108), (105, 76)]), fill=(255, 227, 139, 255))
fire.resize((cw, ch), Image.Resampling.LANCZOS).save(OBJ / "campfire.png", optimize=True)

cem = Image.new("RGBA", (cw * SCALE, ch * SCALE), (0, 0, 0, 0))
cp = cem.load()
cx, cy = 80 * SCALE, 82 * SCALE
for y in range(ch * SCALE):
    for x in range(cw * SCALE):
        distance = math.hypot((x - cx) * .8, y - cy) / (105 * SCALE)
        if distance < 1:
            cp[x, y] = (255, 151, 82, int((1 - distance) ** 2 * 195))
cem = cem.filter(ImageFilter.GaussianBlur(8 * SCALE)).resize((cw, ch), Image.Resampling.LANCZOS)
cem.save(OBJ / "campfire_emission.png", optimize=True)

# Radial glow and celestial bodies.
size = 256
glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
gp = glow.load()
center = (size - 1) / 2
for y in range(size):
    for x in range(size):
        radius = math.hypot(x - center, y - center) / center
        if radius <= 1:
            gp[x, y] = (255, 255, 255, int(max(0, 1 - radius) ** 2 * 255))
glow.save(OBJ / "glow.png", optimize=True)

sun = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
sd2 = ImageDraw.Draw(sun, "RGBA")
sd2.ellipse((8, 8, 120, 120), fill=(255, 255, 255, 255))
sun = sun.filter(ImageFilter.GaussianBlur(.35))
sun.save(OBJ / "sun_disc.png", optimize=True)

moon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
md = ImageDraw.Draw(moon, "RGBA")
md.ellipse((12, 8, 116, 120), fill=(255, 255, 255, 255))
md.ellipse((44, -2, 130, 100), fill=(0, 0, 0, 0))
moon.save(OBJ / "moon_crescent.png", optimize=True)

# Soft grain.
noise = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
npix = noise.load()
rnd = random.Random(5701)
for y in range(256):
    for x in range(256):
        value = rnd.randint(0, 255)
        npix[x, y] = (value, value, value, 72)
noise = noise.filter(ImageFilter.GaussianBlur(2.5))
noise.save(OBJ / "noise_soft.png", optimize=True)

# Compose previews using the exact new layer order.
def make_preview(width, height, landscape=False):
    output = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    pixels = output.load()
    top, middle, bottom = (31, 36, 66), (101, 84, 121), (226, 155, 140)
    for y in range(height):
        t = y / max(1, height - 1)
        if t < .62:
            q = t / .62
            color = tuple(int(top[i] * (1 - q) + middle[i] * q) for i in range(3))
        else:
            q = (t - .62) / .38
            color = tuple(int(middle[i] * (1 - q) + bottom[i] * q) for i in range(3))
        for x in range(width):
            pixels[x, y] = (*color, 255)

    scale = height / H
    def place(name, alpha=1.0, shift=0):
        image = Image.open(LAYER / name).convert("RGBA")
        image = image.resize((int(W * scale), height), Image.Resampling.LANCZOS)
        if alpha < 1:
            channel = image.getchannel("A").point(lambda value: int(value * alpha))
            image.putalpha(channel)
        x = (width - image.width) // 2 + shift
        output.alpha_composite(image, (x, 0))

    place("stars.png", .62)
    place("clouds_far.png", .40)
    # sun
    sx, sy = int(width * .73), int(height * .27)
    radial = Image.open(OBJ / "glow.png").resize((int(height * .18), int(height * .18)), Image.Resampling.LANCZOS)
    tint = Image.new("RGBA", radial.size, (255, 167, 112, 0))
    tint.putalpha(radial.getchannel("A").point(lambda value: int(value * .42)))
    output.alpha_composite(tint, (sx - radial.width // 2, sy - radial.height // 2))
    disc = Image.open(OBJ / "sun_disc.png").resize((int(height * .038), int(height * .038)), Image.Resampling.LANCZOS)
    colored = Image.new("RGBA", disc.size, (255, 240, 191, 0))
    colored.putalpha(disc.getchannel("A"))
    output.alpha_composite(colored, (sx - disc.width // 2, sy - disc.height // 2))
    for layer, alpha in [
        ("mountains_far.png", .72), ("fog_valley.png", .35), ("mountains_mid.png", .84),
        ("mountains_hero.png", 1.0), ("snow_caps.png", .72), ("mountains_near.png", 1.0),
        ("forest_far.png", .82), ("clouds_near.png", .28), ("forest_mid.png", .95),
        ("hill_mid.png", 1.0), ("hill_front.png", 1.0),
    ]:
        place(layer, alpha)

    # A few world-like objects.
    objects = [
        ("pine_tall.png", .06, .53, .22),
        ("pine_sparse.png", .18, .64, .16),
        ("pine_medium.png", .82, .70, .13),
    ]
    for name, rx, ry, rw in objects:
        image = Image.open(OBJ / name)
        target_w = int(width * rw)
        image = image.resize((target_w, int(image.height * target_w / image.width)), Image.Resampling.LANCZOS)
        output.alpha_composite(image, (int(width * rx), int(height * ry)))
    for rx, ry in [(.14, .70), (.60, .78)]:
        for name in ("lantern_emission.png", "lantern.png"):
            image = Image.open(OBJ / name)
            target_h = int(height * .12)
            image = image.resize((int(image.width * target_h / image.height), target_h), Image.Resampling.LANCZOS)
            output.alpha_composite(image, (int(width * rx), int(height * ry)))

    # lower vignette
    vignette = Image.new("RGBA", output.size, (0, 0, 0, 0))
    vp = vignette.load()
    for y in range(height):
        alpha = int(max(0, (y / height - .57) / .43) * 135)
        for x in range(width):
            vp[x, y] = (4, 8, 17, alpha)
    return Image.alpha_composite(output, vignette).convert("RGB")

portrait = make_preview(1080, 1920)
portrait.save(DOCS / "renderer-v4-interactive-preview.png", quality=94)
portrait.resize((540, 960), Image.Resampling.LANCZOS).save(RES / "wallpaper_thumb.png", optimize=True)
landscape = make_preview(1920, 1080, True)
landscape.save(DOCS / "renderer-v4-interactive-preview-landscape.png", quality=94)
print("Generated AetherScape v0.5 visual assets")
