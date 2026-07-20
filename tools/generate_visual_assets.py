from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math, random

ROOT = Path(__file__).resolve().parent
ASSET = ROOT / 'app/src/main/assets/aether'
LAYER = ASSET / 'layers'
OBJ = ASSET / 'objects'
DOCS = ROOT / 'docs'
RES = ROOT / 'app/src/main/res/drawable'
for p in (LAYER, OBJ, DOCS, RES): p.mkdir(parents=True, exist_ok=True)

W,H,S=2048,1024,2
SW,SH=W*S,H*S
random.seed(4045)

def rgba(): return Image.new('RGBA',(SW,SH),(0,0,0,0))
def scpts(points): return [(int(x*S),int(y*S)) for x,y in points]
def save(img,path): img.resize((W,H),Image.Resampling.LANCZOS).save(path,optimize=True)
def col(c,a=255): return (*c,a)

def mountain_layer(name, baseline, peaks, color, facet, haze=0):
    img=rgba(); d=ImageDraw.Draw(img,'RGBA')
    pts=[(0,H)]
    x=0
    edge_y=baseline
    pts.append((0,edge_y))
    while x<W:
        width=random.randint(*peaks['width'])
        peak_h=random.randint(*peaks['height'])
        center=x+int(width*random.uniform(.38,.6))
        left=x+int(width*random.uniform(.12,.25))
        right=x+int(width*random.uniform(.72,.9))
        by=baseline+random.randint(-18,18)
        pts += [(left,by-int(peak_h*.43)),(center,by-peak_h),(right,by-int(peak_h*.31)),(x+width,by)]
        x+=width
    pts += [(W,H)]
    d.polygon(scpts(pts),fill=col(color,255))
    # subtle facets
    x=0
    random.seed(1000+baseline)
    while x<W:
        width=random.randint(*peaks['width'])
        peak_h=random.randint(*peaks['height'])
        center=x+int(width*random.uniform(.38,.6)); by=baseline+random.randint(-18,18)
        left=x+int(width*.17); right=x+int(width*.84)
        d.polygon(scpts([(center,by-peak_h),(center-width*.06,by-peak_h*.40),(left,by),(center,by)]),fill=col(facet,int(70+haze)))
        d.polygon(scpts([(center,by-peak_h),(center+width*.10,by-peak_h*.34),(right,by),(center,by)]),fill=(18,22,38,int(22+haze)))
        x+=width
    save(img,LAYER/name)

mountain_layer('mountains_far.png',620,{'width':(270,430),'height':(150,260)},(112,103,130),(198,174,176),16)
mountain_layer('mountains_mid.png',690,{'width':(300,500),'height':(210,350)},(86,79,106),(185,153,163),8)
mountain_layer('mountains_hero.png',770,{'width':(460,720),'height':(330,520)},(66,61,84),(181,147,157),0)
mountain_layer('mountains_near.png',830,{'width':(250,430),'height':(190,300)},(45,44,63),(119,100,118),0)

# Replace the hero layer with a deliberate, art-directed composition.
hero=rgba(); d=ImageDraw.Draw(hero,'RGBA')
def hero_peak(cx, base, ph, half, base_color, light_color, snow=False):
    poly=[(cx-half,base),(cx-int(half*.52),base-int(ph*.48)),(cx,base-ph),(cx+int(half*.46),base-int(ph*.43)),(cx+half,base),(cx+half,H),(cx-half,H)]
    d.polygon(scpts(poly),fill=(*base_color,255))
    d.polygon(scpts([(cx,base-ph),(cx-int(half*.12),base-int(ph*.48)),(cx-int(half*.40),base),(cx,base)]),fill=(*light_color,82))
    d.polygon(scpts([(cx,base-ph),(cx+int(half*.16),base-int(ph*.38)),(cx+int(half*.48),base),(cx,base)]),fill=(20,23,39,42))
    if snow:
        cap=[(cx,base-ph),(cx-int(half*.10),base-ph+int(ph*.16)),(cx-int(half*.035),base-ph+int(ph*.12)),(cx,base-ph+int(ph*.23)),(cx+int(half*.055),base-ph+int(ph*.14)),(cx+int(half*.13),base-ph+int(ph*.24))]
        d.polygon(scpts(cap),fill=(226,218,219,174))
hero_peak(1000,780,330,390,(67,61,84),(178,146,158),False)
hero_peak(420,790,225,275,(72,66,91),(164,138,153),False)
hero_peak(1590,795,250,310,(63,59,81),(162,136,151),False)
save(hero,LAYER/'mountains_hero.png')

# Snow is a separate layer so seasons can enable or disable it.
snow=rgba(); sd=ImageDraw.Draw(snow,'RGBA')
def snow_cap(cx,base,ph,half):
    cap=[(cx,base-ph),(cx-int(half*.10),base-ph+int(ph*.16)),(cx-int(half*.035),base-ph+int(ph*.12)),(cx,base-ph+int(ph*.23)),(cx+int(half*.055),base-ph+int(ph*.14)),(cx+int(half*.13),base-ph+int(ph*.24))]
    sd.polygon(scpts(cap),fill=(232,226,229,205))
snow_cap(1000,780,330,390)
snow_cap(420,790,225,275)
save(snow,LAYER/'snow_caps.png')

# Forest layers

def pine_poly(cx, ground, height, width, sparse=False):
    trunk=max(3,int(width*.09)); pts=[]
    # trunk
    return trunk

def draw_pine(d,cx,ground,height,width,color,alpha=255,sparse=False,dead=False):
    cx*=S; ground*=S; height*=S; width*=S
    trunk=max(4*S,int(width*.08))
    d.rectangle((cx-trunk/2,ground-height*.73,cx+trunk/2,ground),fill=(*color,alpha))
    if dead:
        for frac,side in [(.35,-1),(.5,1),(.62,-1),(.72,1)]:
            y=ground-height*frac
            d.line((cx,y,cx+side*width*.38,y-height*.08),fill=(*color,alpha),width=max(2*S,int(width*.025)))
        return
    tiers=6 if not sparse else 4
    for i in range(tiers):
        t=i/(tiers-1)
        y=ground-height*(.20+t*.66)
        hw=width*(.54-t*.36)
        th=height*(.20-t*.015)
        if sparse and i%2: hw*=.65
        d.polygon([(cx,y-th*.72),(cx-hw,y+th*.32),(cx-hw*.22,y+th*.12),(cx+hw,y+th*.32)],fill=(*color,alpha))

def forest_layer(name, ground, count, hrange, color, alpha):
    img=rgba(); d=ImageDraw.Draw(img,'RGBA'); random.seed(hash(name)&0xffffffff)
    step=W/count
    for i in range(count+3):
        x=(i-.5)*step+random.uniform(-step*.35,step*.35)
        h=random.randint(*hrange); w=h*random.uniform(.23,.34)
        draw_pine(d,x,ground+random.randint(-12,12),h,w,color,alpha,sparse=random.random()<.18)
    save(img,LAYER/name)

forest_layer('forest_far.png',820,44,(100,190),(47,47,67),125)
forest_layer('forest_mid.png',900,32,(150,270),(29,32,49),205)

# foreground hill and path
img=rgba(); d=ImageDraw.Draw(img,'RGBA')
pts=[(0,H),(0,760),(180,720),(380,755),(590,700),(820,745),(1050,690),(1260,755),(1490,710),(1730,760),(2048,705),(2048,H)]
d.polygon(scpts(pts),fill=(15,21,34,255))
# sloped path glint
d.polygon(scpts([(0,825),(520,735),(1210,850),(2048,790),(2048,880),(1210,920),(520,810),(0,900)]),fill=(28,31,45,150))
save(img,LAYER/'hill_foreground.png')

# fog band: blurred translucent waves
img=rgba(); d=ImageDraw.Draw(img,'RGBA')
for i in range(10):
    x=random.randint(-300,W); y=random.randint(500,820); rw=random.randint(500,900); rh=random.randint(55,130)
    d.ellipse((x*S,y*S,(x+rw)*S,(y+rh)*S),fill=(202,189,203,random.randint(20,45)))
img=img.filter(ImageFilter.GaussianBlur(38*S))
save(img,LAYER/'fog_valley.png')

# Cloud layers, intentionally soft
for name,count,yrange,alpha,blur in [('clouds_far.png',9,(170,520),55,16),('clouds_near.png',7,(110,620),85,10)]:
    img=rgba(); d=ImageDraw.Draw(img,'RGBA'); random.seed(hash(name)&0xffffffff)
    for i in range(count):
        x=random.randint(-200,W); y=random.randint(*yrange); rw=random.randint(220,460); rh=random.randint(45,92)
        c=(192,186,200,alpha)
        d.ellipse((x*S,y*S,(x+rw)*S,(y+rh)*S),fill=c)
        d.ellipse(((x+rw*.15)*S,(y-rh*.35)*S,(x+rw*.56)*S,(y+rh*.55)*S),fill=c)
        d.ellipse(((x+rw*.45)*S,(y-rh*.20)*S,(x+rw*.82)*S,(y+rh*.62)*S),fill=c)
    img=img.filter(ImageFilter.GaussianBlur(blur*S))
    save(img,LAYER/name)

# Stars asset
img=rgba(); d=ImageDraw.Draw(img,'RGBA'); random.seed(991)
for i in range(180):
    x=random.randrange(SW); y=random.randrange(int(SH*.58)); r=random.choice([1,1,1,2,2,3])*S/2
    a=random.randint(70,210)
    d.ellipse((x-r,y-r,x+r,y+r),fill=(243,238,224,a))
save(img,LAYER/'stars.png')

# Individual object assets at 2x then save native sizes

def make_pine(name,w,h,color,sparse=False,dead=False):
    img=Image.new('RGBA',(w*S,h*S),(0,0,0,0)); d=ImageDraw.Draw(img,'RGBA')
    draw_pine(d,w/2,h-2,h*.92,w*.88,color,255,sparse=sparse,dead=dead)
    img.resize((w,h),Image.Resampling.LANCZOS).save(OBJ/name,optimize=True)

make_pine('pine_tall.png',260,620,(10,17,29),False)
make_pine('pine_medium.png',220,430,(13,21,34),False)
make_pine('pine_sparse.png',230,520,(11,18,30),True)
make_pine('pine_dead.png',170,410,(12,19,30),False,True)

# Lantern base and emission
lw,lh=110,320
base=Image.new('RGBA',(lw*S,lh*S),(0,0,0,0)); d=ImageDraw.Draw(base,'RGBA')
d.rectangle((51*S,90*S,59*S,318*S),fill=(9,12,19,255))
d.rectangle((36*S,72*S,74*S,128*S),fill=(22,22,27,255))
d.rectangle((42*S,82*S,68*S,119*S),fill=(250,207,130,255))
d.line((36*S,72*S,55*S,54*S,74*S,72*S),fill=(15,16,22,255),width=4*S)
base.resize((lw,lh),Image.Resampling.LANCZOS).save(OBJ/'lantern.png',optimize=True)
em=Image.new('RGBA',(lw*S,lh*S),(0,0,0,0)); pix=em.load(); cx,cy=55*S,100*S
for y in range(lh*S):
    for x in range(lw*S):
        dist=math.hypot(x-cx,y-cy)/(90*S)
        if dist<1:
            a=int((1-dist)**2*210)
            pix[x,y]=(255,205,125,a)
em=em.filter(ImageFilter.GaussianBlur(7*S)).resize((lw,lh),Image.Resampling.LANCZOS)
em.save(OBJ/'lantern_emission.png',optimize=True)

# campfire
cw,ch=160,150
fire=Image.new('RGBA',(cw*S,ch*S),(0,0,0,0)); d=ImageDraw.Draw(fire,'RGBA')
d.line((42*S,130*S,118*S,105*S),fill=(34,24,24,255),width=13*S)
d.line((42*S,105*S,118*S,130*S),fill=(34,24,24,255),width=13*S)
d.polygon(scpts([(80,20),(48,100),(78,84),(92,120),(120,75),(98,52)]),fill=(255,139,76,255))
d.polygon(scpts([(80,46),(65,98),(83,86),(92,108),(105,76)]),fill=(255,227,139,255))
fire.resize((cw,ch),Image.Resampling.LANCZOS).save(OBJ/'campfire.png',optimize=True)
cem=Image.new('RGBA',(cw*S,ch*S),(0,0,0,0)); pix=cem.load(); cx,cy=80*S,82*S
for y in range(ch*S):
    for x in range(cw*S):
        dist=math.hypot((x-cx)*.8,y-cy)/(105*S)
        if dist<1: pix[x,y]=(255,151,82,int((1-dist)**2*190))
cem=cem.filter(ImageFilter.GaussianBlur(8*S)).resize((cw,ch),Image.Resampling.LANCZOS)
cem.save(OBJ/'campfire_emission.png',optimize=True)

# glow texture
sz=256; glow=Image.new('RGBA',(sz,sz),(0,0,0,0)); px=glow.load(); c=(sz-1)/2
for y in range(sz):
    for x in range(sz):
        r=math.hypot(x-c,y-c)/c
        if r<=1:
            a=int(max(0,(1-r))**2*255)
            px[x,y]=(255,255,255,a)
glow.save(OBJ/'glow.png',optimize=True)

# noise texture
sz=256; noise=Image.new('RGBA',(sz,sz),(0,0,0,0)); p=noise.load(); random.seed(553)
for y in range(sz):
    for x in range(sz):
        v=random.randint(0,255); p[x,y]=(v,v,v,80)
noise=noise.filter(ImageFilter.GaussianBlur(3))
noise.save(OBJ/'noise_soft.png',optimize=True)

# Compose a target preview from the generated layers.
PW,PH=1080,1920
preview=Image.new('RGBA',(PW,PH),(0,0,0,255))
p=preview.load()
top=(34,38,68); mid=(111,91,126); bot=(225,153,139)
for y in range(PH):
    t=y/(PH-1)
    if t<.62:
        q=t/.62; c=tuple(int(top[i]*(1-q)+mid[i]*q) for i in range(3))
    else:
        q=(t-.62)/.38; c=tuple(int(mid[i]*(1-q)+bot[i]*q) for i in range(3))
    for x in range(PW): p[x,y]=(*c,255)

def place_layer(path,alpha=1.0,shift=0):
    im=Image.open(path).convert('RGBA')
    scale=PH/H
    im=im.resize((int(W*scale),PH),Image.Resampling.LANCZOS)
    x=(PW-im.width)//2+shift
    if alpha<1:
        a=im.getchannel('A').point(lambda v:int(v*alpha)); im.putalpha(a)
    preview.alpha_composite(im,(x,0))

place_layer(LAYER/'stars.png',.75)
place_layer(LAYER/'clouds_far.png',.45)
# soft sun glow generated from a radial alpha texture
sx,sy=760,505
g=Image.open(OBJ/'glow.png').resize((330,330),Image.Resampling.LANCZOS)
tint=Image.new('RGBA',g.size,(255,171,120,0)); tint.putalpha(g.getchannel('A').point(lambda v:int(v*.40)))
preview.alpha_composite(tint,(sx-165,sy-165))
ov=Image.new('RGBA',(PW,PH),(0,0,0,0)); od=ImageDraw.Draw(ov,'RGBA')
od.ellipse((sx-30,sy-30,sx+30,sy+30),fill=(255,242,196,255)); preview.alpha_composite(ov)
for layer in ['mountains_far.png','mountains_mid.png','mountains_hero.png','snow_caps.png','mountains_near.png','fog_valley.png','forest_far.png','forest_mid.png','hill_foreground.png']:
    place_layer(LAYER/layer)
# foreground pines and lanterns
for path,x,y,w in [(OBJ/'pine_tall.png',-24,500,260),(OBJ/'pine_sparse.png',150,720,190),(OBJ/'pine_medium.png',860,1050,175)]:
    im=Image.open(path); ratio=w/im.width; im=im.resize((w,int(im.height*ratio)),Image.Resampling.LANCZOS); preview.alpha_composite(im,(x,y))
for x,y in [(115,1210),(600,1420)]:
    for f in ['lantern_emission.png','lantern.png']:
        im=Image.open(OBJ/f); ratio=150/im.height; im=im.resize((int(im.width*ratio),150),Image.Resampling.LANCZOS); preview.alpha_composite(im,(x,y))
# lower vignette
vig=Image.new('RGBA',(PW,PH),(0,0,0,0)); vp=vig.load()
for y in range(PH):
    a=int(max(0,(y/PH-.55)/.45)*150)
    for x in range(PW): vp[x,y]=(4,8,17,a)
preview=Image.alpha_composite(preview,vig).convert('RGB')
preview.save(DOCS/'renderer-v3-gpu-preview.png',quality=93)
preview.resize((540,960),Image.Resampling.LANCZOS).save(RES/'wallpaper_thumb.png',optimize=True)
print('Generated assets in',ASSET)
