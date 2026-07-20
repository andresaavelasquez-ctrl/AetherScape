from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
LAYER = ROOT / 'app/src/main/assets/aether/layers'
OBJ = ROOT / 'app/src/main/assets/aether/objects'
DOCS = ROOT / 'docs'
RES = ROOT / 'app/src/main/res/drawable'


def gradient(size):
    w,h=size
    out=Image.new('RGBA', size)
    px=out.load()
    top=(26,31,57); mid=(91,76,112); bottom=(224,157,140)
    for y in range(h):
        t=y/max(1,h-1)
        if t<0.62:
            q=t/0.62; c=tuple(int(top[i]*(1-q)+mid[i]*q) for i in range(3))
        else:
            q=(t-0.62)/0.38; c=tuple(int(mid[i]*(1-q)+bottom[i]*q) for i in range(3))
        for x in range(w): px[x,y]=(*c,255)
    return out


def centered_layer(name, size, alpha=1.0, shift=0):
    w,h=size
    im=Image.open(LAYER/name).convert('RGBA')
    scale=h/1000
    im=im.resize((int(2400*scale),h),Image.Resampling.LANCZOS)
    if alpha<1:
        im.putalpha(im.getchannel('A').point(lambda v:int(v*alpha)))
    canvas=Image.new('RGBA',size,(0,0,0,0))
    canvas.alpha_composite(im,((w-im.width)//2+shift,0))
    return canvas


def place_object(out,name,x_ratio,base_ratio,height_ratio,alpha=1.0):
    w,h=out.size
    im=Image.open(OBJ/name).convert('RGBA')
    th=int(h*height_ratio)
    tw=int(im.width*th/im.height)
    im=im.resize((tw,th),Image.Resampling.LANCZOS)
    if alpha<1:
        im.putalpha(im.getchannel('A').point(lambda v:int(v*alpha)))
    x=int(w*x_ratio-tw/2); y=int(h*base_ratio-th)
    out.alpha_composite(im,(x,y))


def glow(out,x_ratio,y_ratio,radius_ratio,color,alpha):
    w,h=out.size
    r=int(h*radius_ratio)
    g=Image.new('RGBA',(r*2,r*2),(0,0,0,0))
    p=g.load()
    for y in range(r*2):
        for x in range(r*2):
            d=((x-r)**2+(y-r)**2)**0.5/r
            if d<1: p[x,y]=(*color,int((1-d)**2*alpha))
    g=g.filter(ImageFilter.GaussianBlur(max(2,r//12)))
    out.alpha_composite(g,(int(w*x_ratio-r),int(h*y_ratio-r)))


def render(size):
    w,h=size
    out=gradient(size)
    for n,a in [('stars.png',.55),('clouds_far.png',.26),('mountains_far.png',.58),
                ('fog_valley.png',.20),('mountains_mid.png',.75),('mountains_hero.png',1.0),
                ('snow_caps.png',.68),('mountains_near.png',.88),('forest_far.png',.58),
                ('forest_mid.png',.76),('hill_mid.png',1.0)]:
        out=Image.alpha_composite(out, centered_layer(n,size,a))

    glow(out,.72,.24,.095,(255,168,110),100)
    d=ImageDraw.Draw(out,'RGBA')
    r=int(h*.017); cx=int(w*.72); cy=int(h*.24)
    d.ellipse((cx-r,cy-r,cx+r,cy+r),fill=(255,240,190,255))

    # designed edge framing, leaving the mountain center open
    placements=[
        ('pine_tall.png',.045,.79,.31),('pine_sparse.png',.145,.82,.23),
        ('pine_medium.png',.235,.84,.17),('pine_medium.png',.80,.85,.16),
        ('pine_sparse.png',.90,.82,.23),('pine_tall.png',.975,.80,.28),
    ]
    for p in placements: place_object(out,*p)

    out=Image.alpha_composite(out, centered_layer('hill_front.png',size,1.0))
    for xr,br in [(.20,.83),(.66,.87)]:
        glow(out,xr,br-.07,.038,(255,200,126),120)
        place_object(out,'lantern.png',xr,br,.13)

    # lower vignette
    vig=Image.new('RGBA',size,(0,0,0,0)); vp=vig.load()
    for y in range(h):
        a=int(max(0,(y/h-.56)/.44)*155)
        for x in range(w): vp[x,y]=(3,7,16,a)
    return Image.alpha_composite(out,vig).convert('RGB')


portrait=render((1080,1920))
portrait.save(DOCS/'renderer-v5-native-preview.png',quality=95)
portrait.resize((540,960),Image.Resampling.LANCZOS).save(RES/'wallpaper_thumb.png',optimize=True)
landscape=render((1920,1080))
landscape.save(DOCS/'renderer-v5-native-preview-landscape.png',quality=95)
print('Rendered v0.6 previews')
