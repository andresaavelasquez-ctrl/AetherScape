from pathlib import Path
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
LAYER = ROOT / 'app/src/main/assets/aether/layers'
OBJ = ROOT / 'app/src/main/assets/aether/objects'
DOCS = ROOT / 'docs'
RES = ROOT / 'app/src/main/res/drawable'
W, H = 2400, 1000


def gradient(size):
    w, h = size
    out = Image.new('RGBA', size)
    px = out.load()
    top = (16, 22, 50); mid = (83, 78, 118); bottom = (214, 149, 136)
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


def compose(size):
    w, h = size
    scale = h / H
    out = gradient(size)

    def layer(name, alpha=1.0):
        im = Image.open(LAYER / name).convert('RGBA')
        im = im.resize((int(W * scale), h), Image.Resampling.LANCZOS)
        if alpha < 1:
            im.putalpha(im.getchannel('A').point(lambda v: int(v * alpha)))
        out.alpha_composite(im, ((w - im.width) // 2, 0))

    for name, alpha in [
        ('stars.png', .72), ('clouds_far.png', .30), ('mountains_far.png', .70),
        ('fog_valley.png', .24), ('mountains_mid.png', .82), ('mountains_hero.png', 1.0),
        ('snow_caps.png', .72), ('mountains_near.png', .92), ('forest_far.png', .66),
        ('forest_mid.png', .84), ('hill_mid.png', 1.0), ('clouds_near.png', .26), ('hill_front.png', 1.0),
    ]:
        layer(name, alpha)

    sx, sy = int(w * .71), int(h * .26)
    glow = Image.open(OBJ / 'glow.png').convert('RGBA').resize((int(h * .22), int(h * .22)), Image.Resampling.LANCZOS)
    tint = Image.new('RGBA', glow.size, (245, 182, 120, 0))
    tint.putalpha(glow.getchannel('A').point(lambda v: int(v * .36)))
    out.alpha_composite(tint, (sx - glow.width // 2, sy - glow.height // 2))
    sun = Image.open(OBJ / 'sun_disc.png').convert('RGBA').resize((int(h * .06), int(h * .06)), Image.Resampling.LANCZOS)
    out.alpha_composite(sun, (sx - sun.width // 2, sy - sun.height // 2))

    def place(name, x_ratio, base_ratio, height_ratio):
        im = Image.open(OBJ / name).convert('RGBA')
        th = int(h * height_ratio)
        tw = int(im.width * th / im.height)
        im = im.resize((tw, th), Image.Resampling.LANCZOS)
        out.alpha_composite(im, (int(w * x_ratio - tw / 2), int(h * base_ratio - th)))

    for args in [
        ('pine_tall.png', .06, .83, .27), ('pine_sparse.png', .17, .84, .20), ('pine_medium.png', .28, .86, .15),
        ('pine_medium.png', .80, .86, .15), ('pine_sparse.png', .90, .84, .19), ('pine_tall.png', .97, .83, .24),
    ]:
        place(*args)
    for xr, br in [(.16, .80), (.63, .87)]:
        glow2 = Image.open(OBJ / 'lantern_emission.png').convert('RGBA')
        th = int(h * .12); tw = int(glow2.width * th / glow2.height)
        glow2 = glow2.resize((tw, th), Image.Resampling.LANCZOS)
        out.alpha_composite(glow2, (int(w * xr - tw / 2), int(h * br - th)))
        place('lantern.png', xr, br, .12)

    vig = Image.new('RGBA', size, (0, 0, 0, 0))
    vp = vig.load()
    for y in range(h):
        alpha = int(max(0, (y / h - .58) / .42) * 138)
        for x in range(w):
            vp[x, y] = (4, 8, 18, alpha)
    return Image.alpha_composite(out, vig).convert('RGB')

portrait = compose((1080, 1920))
portrait.save(DOCS / 'renderer-v8-organized-preview.png', quality=95)
portrait.resize((540, 960), Image.Resampling.LANCZOS).save(RES / 'wallpaper_thumb.png', optimize=True)
landscape = compose((1920, 1080))
landscape.save(DOCS / 'renderer-v8-organized-preview-landscape.png', quality=95)
print('Rendered v0.8 previews')
