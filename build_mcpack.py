#!/usr/bin/env python3
"""
Ultra Realistic Vibrant Pack Builder for Minecraft Bedrock 1.26+
Generates: Textures + Shaders + Sounds → .mcpack
Target: Poco X6 Pro / Android 16 / No Ray Tracing / Vibrant Visuals
"""

import numpy as np
from PIL import Image
import os, struct, wave, zipfile, json, math, random

# ═══════════════════════════════════════════════════
#  CONFIG
# ═══════════════════════════════════════════════════
TEX = 512          # texture resolution - ultra quality
SR  = 44100        # sound sample rate
OUT = "/home/user/Atta/VibrantRealisticPack.mcpack"
TMP = "/tmp/_mc_pack_build"

random.seed(42)
np.random.seed(42)

# ═══════════════════════════════════════════════════
#  HELPERS
# ═══════════════════════════════════════════════════

def clamp(a):  return np.clip(a, 0, 255).astype(np.uint8)

def perlin(size=TEX, scale=4.0, oct=4, seed=0):
    r = np.zeros((size, size))
    amp, freq, mx = 1.0, 1.0, 0.0
    for i in range(oct):
        rng = np.random.RandomState(seed + i*137)
        g = max(2, int(size * freq / scale))
        grid = rng.randn(g+2, g+2)
        x = np.linspace(0, g, size, False)
        X, Y = np.meshgrid(x, x)
        xi, yi = X.astype(int), Y.astype(int)
        xf, yf = X - xi, Y - yi
        u = xf*xf*xf*(xf*(xf*6-15)+10)
        v = yf*yf*yf*(yf*(yf*6-15)+10)
        xi, yi = np.clip(xi,0,g), np.clip(yi,0,g)
        n00=grid[yi,xi]; n10=grid[yi,xi+1]; n01=grid[yi+1,xi]; n11=grid[yi+1,xi+1]
        r += (n00*(1-u)+n10*u)*(1-v)*amp + (n01*(1-u)+n11*u)*v*amp
        mx += amp; amp *= .5; freq *= 2
    r /= mx
    r = (r - r.min())/(r.max()-r.min()+1e-8)*2-1
    return r

def tileable(arr):
    h = arr.shape[0]; b = 16; out = arr.copy()
    for i in range(b):
        a = i/b
        if arr.ndim==3:
            out[i] = arr[i]*a + arr[h-b+i]*(1-a)
            out[h-1-i] = arr[h-1-i]*a + arr[b-1-i]*(1-a)
            out[:,i] = arr[:,i]*a + arr[:,h-b+i]*(1-a)
            out[:,h-1-i] = arr[:,h-1-i]*a + arr[:,b-1-i]*(1-a)
        else:
            out[i] = arr[i]*a + arr[h-b+i]*(1-a)
            out[h-1-i] = arr[h-1-i]*a + arr[b-1-i]*(1-a)
    return out

def grain(a, n=8): return clamp(a + np.random.randint(-n, n+1, a.shape))

def rgb(r,g,b): return np.stack([clamp(r),clamp(g),clamp(b)],-1)
def rgba(r,g,b,a): return np.stack([clamp(r),clamp(g),clamp(b),np.clip(a,0,255).astype(np.uint8)],-1)

def save(arr, p):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    Image.fromarray(arr).save(p)

def save_wav(path, samples, sr=SR):
    """Save float samples [-1,1] as 16-bit WAV"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    s = np.clip(samples, -1, 1)
    s = (s * 32767).astype(np.int16)
    with wave.open(path, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(s.tobytes())

# ═══════════════════════════════════════════════════
#  TEXTURE GENERATORS
# ═══════════════════════════════════════════════════

def tex_noise(base, var, sc=4., oct=4, sd=0, gn=8):
    n = perlin(TEX, sc, oct, sd)
    img = rgb(base[0]+n*var[0], base[1]+n*var[1], base[2]+n*var[2])
    return grain(tileable(img), gn)

def tex_stone(base, var, crack, sd=0):
    n = perlin(TEX,6,4,sd); n2 = perlin(TEX,12,3,sd+100)
    r,g,b = base[0]+n*var[0], base[1]+n*var[1], base[2]+n*var[2]
    m = np.abs(n2)<0.08
    r=np.where(m,crack[0],r); g=np.where(m,crack[1],g); b=np.where(m,crack[2],b)
    return grain(tileable(rgb(r,g,b)),6)

def tex_ore(stone, ore_c, ore_d, ore_l, dens=.15, sd=0):
    n=perlin(TEX,6,4,sd); n2=perlin(TEX,8,3,sd+200); n3=perlin(TEX,16,2,sd+250)
    r,g,b = stone[0]+n*20, stone[1]+n*20, stone[2]+n*20
    m = n2>(1-dens*2.2)
    oc = np.where(n3>0.1,ore_l[0],np.where(n3<-0.1,ore_d[0],ore_c[0]))
    og = np.where(n3>0.1,ore_l[1],np.where(n3<-0.1,ore_d[1],ore_c[1]))
    ob = np.where(n3>0.1,ore_l[2],np.where(n3<-0.1,ore_d[2],ore_c[2]))
    r=np.where(m,oc+np.random.randint(-8,8,(TEX,TEX)),r)
    g=np.where(m,og+np.random.randint(-8,8,(TEX,TEX)),g)
    b=np.where(m,ob+np.random.randint(-8,8,(TEX,TEX)),b)
    return grain(tileable(rgb(r,g,b)),5)

def tex_plank(light, dark, sd=0):
    n=perlin(TEX,3,4,sd); n2=perlin(TEX,20,2,sd+10)
    img=np.zeros((TEX,TEX,3),dtype=np.uint8); ph=TEX//4
    for py in range(TEX):
        pi=py//ph; base=light if pi%2==0 else dark
        gl=(n[py]*25+n2[py]*10).astype(int)
        img[py]=rgb(base[0]+gl+np.random.randint(-3,4,TEX),
                     base[1]+gl*0.8+np.random.randint(-3,4,TEX),
                     base[2]+gl*0.6+np.random.randint(-3,4,TEX))
        if py%ph<2: img[py]=clamp(img[py].astype(int)-28)
    return grain(tileable(img),5)

def tex_bark(bark, dark, sd=0):
    n=perlin(TEX,3,5,sd); n2=perlin(TEX,8,3,sd+50)
    r=bark[0]+n*30+n2*20; g=bark[1]+n*25+n2*15; b=bark[2]+n*20+n2*10
    m=n2<-0.3; r=np.where(m,dark[0],r); g=np.where(m,dark[1],g); b=np.where(m,dark[2],b)
    return grain(tileable(rgb(r,g,b)),6)

def tex_logtop(sap, heart, sd=0):
    cx=TEX//2; Y,X=np.mgrid[0:TEX,0:TEX]
    d=np.sqrt((X-cx)**2+(Y-cx)**2)/(TEX/2)
    n=perlin(TEX,4,2,sd); ring=np.sin(d*25+n*3)
    r=np.where(d<.85,np.where(ring>0,sap[0]+ring*15,heart[0]+ring*15),80)
    g=np.where(d<.85,np.where(ring>0,sap[1]+ring*12,heart[1]+ring*12),60)
    b=np.where(d<.85,np.where(ring>0,sap[2]+ring*8,heart[2]+ring*8),40)
    return rgb(r+np.random.randint(-3,4,(TEX,TEX)),
               g+np.random.randint(-3,4,(TEX,TEX)),
               b+np.random.randint(-3,4,(TEX,TEX)))

def tex_leaf(base, dk, lt, sd=0):
    n=perlin(TEX,6,4,sd); n2=perlin(TEX,12,3,sd+30)
    r=np.where(n>0.2,lt[0],np.where(n<-0.2,dk[0],base[0]))+np.random.randint(-8,9,(TEX,TEX))
    g=np.where(n>0.2,lt[1],np.where(n<-0.2,dk[1],base[1]))+np.random.randint(-8,9,(TEX,TEX))
    b=np.where(n>0.2,lt[2],np.where(n<-0.2,dk[2],base[2]))+np.random.randint(-5,6,(TEX,TEX))
    a=np.full((TEX,TEX),230,np.uint8); a[n2>0.4]=255; a[n2<-0.4]=180
    return tileable(rgba(r,g,b,a))

def tex_wool(color, sd=0):
    n=perlin(TEX,10,3,sd)
    return grain(tileable(rgb(color[0]+n*15,color[1]+n*12,color[2]+n*10)),4)

def tex_concrete(color, sd=0):
    n=perlin(TEX,4,3,sd); n2=perlin(TEX,20,2,sd+50)
    return grain(tileable(rgb(color[0]+n*10+n2*5,color[1]+n*8+n2*4,color[2]+n*7+n2*4)),3)

def tex_terracotta(base, pat, sd=0):
    n=perlin(TEX,5,4,sd); n2=perlin(TEX,3,2,sd+80)
    m=n2>0.3
    r=base[0]+n*15; g=base[1]+n*12; b=base[2]+n*10
    r=np.where(m,pat[0]+n*12,r); g=np.where(m,pat[1]+n*10,g); b=np.where(m,pat[2]+n*8,b)
    return grain(tileable(rgb(r,g,b)),5)

def tex_bricks(br, mg, sd=0):
    n=perlin(TEX,8,3,sd); img=np.zeros((TEX,TEX,3),np.uint8)
    bw,bh,mr=32,16,3; Y,X=np.mgrid[0:TEX,0:TEX]
    row=Y//bh; off=np.where(row%2,bw//2,0); bx=(X+off)%bw; by=Y%bh
    mm=(bx<mr)|(by<mr)
    img[...,0]=np.where(mm,clamp(mg[0]+n*20),clamp(br[0]+n*25))
    img[...,1]=np.where(mm,clamp(mg[1]+n*18),clamp(br[1]+n*15))
    img[...,2]=np.where(mm,clamp(mg[2]+n*15),clamp(br[2]+n*12))
    return grain(tileable(img),5)

def tex_sbricks(sd=0):
    n=perlin(TEX,6,4,sd); img=np.zeros((TEX,TEX,3),np.uint8)
    bw,bh,mr=32,32,2; Y,X=np.mgrid[0:TEX,0:TEX]
    row=Y//bh; off=np.where(row%2,bw//2,0); bx=(X+off)%bw; by=Y%bh
    mm=(bx<mr)|(by<mr); base=140+n*25
    img[...,0]=np.where(mm,clamp(120+n*15),clamp(base))
    img[...,1]=np.where(mm,clamp(118+n*14),clamp(base-2))
    img[...,2]=np.where(mm,clamp(115+n*12),clamp(base-5))
    return grain(tileable(img),5)

def tex_nether_bricks(sd=0, red=False):
    n=perlin(TEX,6,3,sd); img=np.zeros((TEX,TEX,3),np.uint8)
    bw,bh,mr=32,16,2; Y,X=np.mgrid[0:TEX,0:TEX]
    row=Y//bh; off=np.where(row%2,bw//2,0); bx=(X+off)%bw; by=Y%bh
    mm=(bx<mr)|(by<mr)
    if red: mr_,mg_,mb_=45+n*10,18+n*5,18+n*5; br_,bg_,bb_=100+n*25,30+n*12,30+n*12
    else:   mr_,mg_,mb_=35+n*10,25+n*8,25+n*8;  br_,bg_,bb_=55+n*20,30+n*12,30+n*12
    img[...,0]=np.where(mm,clamp(mr_),clamp(br_))
    img[...,1]=np.where(mm,clamp(mg_),clamp(bg_))
    img[...,2]=np.where(mm,clamp(mb_),clamp(bb_))
    return grain(tileable(img),5)

def tex_glass(sd=0):
    r=np.full((TEX,TEX),200,np.uint8); g=np.full((TEX,TEX),210,np.uint8)
    b=np.full((TEX,TEX),220,np.uint8); a=np.full((TEX,TEX),20,np.uint8)
    Y,X=np.mgrid[0:TEX,0:TEX]; e=np.minimum(np.minimum(X,TEX-1-X),np.minimum(Y,TEX-1-Y))
    em=e<3; a[em]=(150-e[em]*30).astype(np.uint8)
    return rgba(r,g,b,a)

def tex_colored_glass(c):
    r=np.full((TEX,TEX),c[0],np.uint8); g=np.full((TEX,TEX),c[1],np.uint8)
    b=np.full((TEX,TEX),c[2],np.uint8); a=np.full((TEX,TEX),100,np.uint8)
    Y,X=np.mgrid[0:TEX,0:TEX]; e=np.minimum(np.minimum(X,TEX-1-X),np.minimum(Y,TEX-1-Y))
    em=e<3; a[em]=(180-e[em]*40).astype(np.uint8)
    return rgba(r,g,b,a)

def tex_sandstone_side(sd=0):
    n=perlin(TEX,5,3,sd); Y,_=np.mgrid[0:TEX,0:TEX]
    layers=np.sin(Y/TEX*12+n*2)*.5+.5
    return grain(tileable(rgb(210+n*20+layers*15,185+n*18+layers*12,135+n*15+layers*10)),5)

def tex_farmland(moist, sd=0):
    n=perlin(TEX,8,3,sd); Y,_=np.mgrid[0:TEX,0:TEX]
    fur=np.sin(Y/TEX*12+n*2)*.5+.5
    br,bg,bb=(100,75,50) if moist else (130,95,60)
    return grain(tileable(rgb(br+n*20-fur*15,bg+n*15-fur*10,bb+n*12-fur*8)),5)

def tex_furnace_front(on, sd=0):
    n=perlin(TEX,5,4,sd); img=rgb(130+n*25,130+n*25,130+n*25)
    cx,cy=TEX//2,TEX//2+10; Y,X=np.mgrid[0:TEX,0:TEX]
    m=(np.abs(X-cx)<30)&(np.abs(Y-cy)<25)
    if on:
        fn=perlin(TEX,8,3,sd+200)
        img[m,0]=clamp(200+fn[m]*55); img[m,1]=clamp(120+fn[m]*40); img[m,2]=clamp(30+fn[m]*20)
    else: img[m]=np.array([20,18,15],np.uint8)
    return img

def tex_craft_top(sd=0):
    n=perlin(TEX,3,3,sd); Y,X=np.mgrid[0:TEX,0:TEX]
    img=rgb(160+n*20,120+n*15,70+n*10)
    gs=TEX//4; gm=(X%gs<2)|(Y%gs<2)
    img[gm]=clamp(img[gm].astype(int)-28)
    return grain(img,4)

def tex_lava(sd=0):
    n=perlin(TEX,4,4,sd); n2=perlin(TEX,2,3,sd+100)
    h=(n+1)*.5+(n2+1)*.25
    r=np.where(h>.7,255,np.where(h>.4,220+h*35,150+h*50))
    g=np.where(h>.7,200+h*55,np.where(h>.4,100+h*60,30+h*30))
    b=np.where(h>.7,50+h*30,np.where(h>.4,20,10))
    return tileable(rgb(r,g,b))

def tex_bookshelf(sd=0):
    n=perlin(TEX,3,2,sd); img=np.zeros((TEX,TEX,3),np.uint8)
    sh=TEX//3; Y,X=np.mgrid[0:TEX,0:TEX]; by=Y%sh
    sm=(by<3)|(by>=sh-3)
    img[sm,0]=clamp(160+n[sm]*20); img[sm,1]=clamp(120+n[sm]*15); img[sm,2]=clamp(70+n[sm]*10)
    rng=np.random.RandomState(sd); books=rng.randint(30,200,(TEX,TEX,3))
    bm=~sm; img[bm]=np.clip(books[bm],0,255)
    return grain(img,4)

def tex_melon_side(sd=0):
    n=perlin(TEX,6,3,sd); X,_=np.mgrid[0:TEX,0:TEX]; st=np.sin(X/TEX*20)*.5+.5
    return grain(tileable(rgb(80+n*20+st*30,130+n*25+st*40,30+n*10+st*10)),5)

def tex_pumpkin_side(sd=0):
    n=perlin(TEX,5,3,sd); X,_=np.mgrid[0:TEX,0:TEX]; sg=np.sin(X/TEX*16)*.5+.5
    return grain(tileable(rgb(200+n*25+sg*20,120+n*20+sg*15,30+n*10+sg*5)),5)

def tex_hay(sd=0):
    n=perlin(TEX,8,4,sd); n2=perlin(TEX,3,2,sd+10)
    return grain(tileable(rgb(170+n*30+n2*20,140+n*25+n2*15,40+n*15+n2*8)),5)

def tex_amethyst(sd=0):
    n=perlin(TEX,6,4,sd); n2=perlin(TEX,12,2,sd+40)
    return grain(tileable(rgb(130+n*30+n2*20,80+n*20+n2*15,180+n*35+n2*25)),5)

def tex_sculk(sd=0):
    n=perlin(TEX,6,4,sd)
    return grain(tileable(rgb(15+n*15,25+n*20,30+n*22)),5)

def tex_lava_still(sd=0): return tex_lava(sd)

# ═══════════════════════════════════════════════════
#  SOUND GENERATORS  (procedural .wav)
# ═══════════════════════════════════════════════════

def noise_burst(dur, sr=SR):
    return np.random.uniform(-1,1,int(dur*sr))

def tone(freq, dur, sr=SR):
    t=np.linspace(0,dur,int(dur*sr),False)
    return np.sin(2*np.pi*freq*t)

def pad_to(a, n):
    """Pad array to length n"""
    if len(a) >= n: return a[:n]
    return np.concatenate([a, np.zeros(n - len(a))])

def env(samples, attack=0.005, decay=0.05, sustain=0.3, release=0.1, sr=SR):
    n=len(samples); a=int(attack*sr); d=int(decay*sr); r=int(release*sr)
    s_env=np.ones(n)
    for i in range(min(a,n)): s_env[i]=i/a
    for i in range(a,min(a+d,n)): s_env[i]=1-(1-sustain)*((i-a)/d)
    for i in range(max(0,n-r),n): s_env[i]=sustain*(1-(i-(n-r))/r)
    return samples*s_env

def snd_stone_break():
    dur = 0.6; n = int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.5 + pad_to(tone(200,dur),n)*0.25 + pad_to(tone(80,dur),n)*0.2
    s = env(s, 0.002, 0.05, 0.15, 0.35)
    # Multiple debris crackles
    for _ in range(5):
        t = np.random.uniform(0, 0.4)
        crack = noise_burst(0.04)*0.4 * np.exp(-np.linspace(0,10,int(0.04*SR)))
        st = int(t*SR)
        if st+len(crack)<n: s[st:st+len(crack)] += crack
    # Reverb tail
    reverb = noise_burst(0.2)*0.1 * np.exp(-np.linspace(0,5,int(0.2*SR)))
    s[len(s)//2:len(s)//2+len(reverb)] += reverb[:n-len(s)//2]
    return np.clip(s*.75,-1,1)

def snd_dirt_break():
    dur = 0.5; n = int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.7 + pad_to(tone(60,dur),n)*0.2 + pad_to(tone(40,dur),n)*0.15
    s = env(s, 0.008, 0.08, 0.25, 0.3)
    # Dirt crumble
    for _ in range(8):
        t = np.random.uniform(0, 0.3)
        crumble = noise_burst(0.03)*0.2
        st = int(t*SR)
        if st+len(crumble)<n: s[st:st+len(crumble)] += crumble
    return np.clip(s*.6,-1,1)

def snd_grass_break():
    dur = 0.45; n = int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.4 + pad_to(tone(300,dur),n)*0.2 + pad_to(tone(150,dur),n)*0.15 + pad_to(tone(500,0.2),n)*0.1
    s = env(s, 0.005, 0.06, 0.2, 0.25)
    # Grass rustle
    rustle = noise_burst(0.15)*0.25 * np.sin(np.linspace(0,6*np.pi,int(0.15*SR)))*0.5+0.5
    s[:len(rustle)] += rustle
    return np.clip(s*.55,-1,1)

def snd_wood_break():
    dur = 0.55; n = int(dur*SR)
    s = pad_to(tone(150,dur),n)*0.35 + pad_to(tone(250,dur),n)*0.25 + pad_to(tone(100,dur),n)*0.15 + pad_to(noise_burst(dur),n)*0.35
    s = env(s, 0.003, 0.06, 0.18, 0.3)
    for _ in range(3):
        t = np.random.uniform(0.02, 0.25)
        cdur=0.08; cn=int(cdur*SR)
        crack = (pad_to(tone(600,cdur),cn)*0.3 + pad_to(tone(900,0.06),cn)*0.2 + pad_to(noise_burst(cdur),cn)*0.4) * np.exp(-np.linspace(0,12,cn))
        st = int(t*SR)
        if st+cn<n: s[st:st+cn] += crack
    return np.clip(s*.7,-1,1)

def snd_glass_break():
    dur = 0.8; n = int(dur*SR)
    s = (pad_to(tone(2000,0.5),n)*0.35 + pad_to(tone(3500,0.5),n)*0.25 +
         pad_to(tone(5000,0.4),n)*0.2 + pad_to(tone(7000,0.3),n)*0.1 +
         pad_to(noise_burst(dur),n)*0.45)
    s = env(s, 0.001, 0.03, 0.12, 0.5)
    # Many shatter fragments
    for _ in range(15):
        f = np.random.uniform(800, 8000)
        t = np.random.uniform(0, 0.5)
        frag = tone(f, 0.12)*0.12 * np.exp(-np.linspace(0,8,int(0.12*SR)))
        st = int(t*SR)
        if st+len(frag)<n: s[st:st+len(frag)] += frag
    # Tinkle tail
    for _ in range(5):
        f = np.random.uniform(3000, 6000)
        t = np.random.uniform(0.3, 0.7)
        tinkle = tone(f, 0.05)*0.05 * np.exp(-np.linspace(0,15,int(0.05*SR)))
        st = int(t*SR)
        if st+len(tinkle)<n: s[st:st+len(tinkle)] += tinkle
    return np.clip(s*.7,-1,1)

def snd_sand_step():
    dur=0.15; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.7 + pad_to(tone(50,dur),n)*0.1
    s = env(s, 0.01, 0.03, 0.4, 0.08)
    return np.clip(s*.4,-1,1)

def snd_gravel_step():
    dur=0.12; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.6 + pad_to(tone(80,dur),n)*0.2 + pad_to(tone(120,dur),n)*0.15
    s = env(s, 0.005, 0.02, 0.3, 0.06)
    return np.clip(s*.45,-1,1)

def snd_stone_step():
    dur=0.1; n=int(dur*SR)
    s = pad_to(tone(180,dur),n)*0.3 + pad_to(tone(300,dur),n)*0.2 + pad_to(noise_burst(dur),n)*0.4
    s = env(s, 0.002, 0.015, 0.2, 0.05)
    return np.clip(s*.5,-1,1)

def snd_dirt_step():
    dur=0.12; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.5 + pad_to(tone(40,dur),n)*0.2
    s = env(s, 0.008, 0.02, 0.35, 0.06)
    return np.clip(s*.35,-1,1)

def snd_grass_step():
    dur=0.1; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.3 + pad_to(tone(250,dur),n)*0.15 + pad_to(tone(400,dur),n)*0.1
    s = env(s, 0.005, 0.02, 0.2, 0.05)
    return np.clip(s*.3,-1,1)

def snd_wood_step():
    dur=0.1; n=int(dur*SR)
    s = pad_to(tone(200,dur),n)*0.3 + pad_to(tone(350,dur),n)*0.2 + pad_to(noise_burst(dur),n)*0.3
    s = env(s, 0.003, 0.015, 0.2, 0.04)
    return np.clip(s*.45,-1,1)

def snd_metal_step():
    dur=0.15; n=int(dur*SR)
    s = (pad_to(tone(600,dur),n)*0.3 + pad_to(tone(900,dur),n)*0.2 +
         pad_to(tone(1200,0.1),n)*0.15 + pad_to(noise_burst(dur),n)*0.2)
    s = env(s, 0.001, 0.02, 0.15, 0.08)
    return np.clip(s*.45,-1,1)

def snd_snow_step():
    dur=0.15; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.4 + pad_to(tone(30,dur),n)*0.1
    s = env(s, 0.015, 0.03, 0.4, 0.08)
    return np.clip(s*.3,-1,1)

def snd_ice_step():
    dur=0.12; n=int(dur*SR)
    s = pad_to(tone(800,dur),n)*0.3 + pad_to(tone(1500,dur),n)*0.2 + pad_to(noise_burst(dur),n)*0.3
    s = env(s, 0.001, 0.015, 0.15, 0.06)
    return np.clip(s*.4,-1,1)

def snd_wool_step():
    dur=0.1; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.3 + pad_to(tone(20,dur),n)*0.1
    s = env(s, 0.02, 0.03, 0.4, 0.06)
    return np.clip(s*.25,-1,1)

def snd_rain():
    dur=2.0; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.3 + pad_to(tone(100,dur),n)*0.05
    for _ in range(20):
        t = np.random.uniform(0,1.8)
        drop = tone(np.random.uniform(800,2000), 0.03)*0.1 * np.exp(-np.linspace(0,8,int(0.03*SR)))
        st = int(t*SR)
        if st+len(drop)<len(s): s[st:st+len(drop)] += drop
    s = env(s, 0.3, 0.2, 0.7, 0.5)
    return np.clip(s*.4,-1,1)

def snd_thunder():
    dur=3.0; n=int(dur*SR)
    t = np.linspace(0,dur,n,False)
    s = pad_to(noise_burst(dur),n)*0.7 * np.exp(-t*1.5)
    s += pad_to(tone(40,dur),n)*0.4 * np.exp(-t*0.8)
    s += pad_to(tone(20,dur),n)*0.3 * np.exp(-t*0.5)
    return np.clip(s*.8,-1,1)

def snd_place_stone():
    dur=0.15; n=int(dur*SR)
    s = pad_to(tone(220,dur),n)*0.35 + pad_to(tone(120,dur),n)*0.25 + pad_to(noise_burst(dur),n)*0.4
    return np.clip(env(s,0.002,0.02,0.2,0.08)*.5,-1,1)

def snd_place_wood():
    dur=0.15; n=int(dur*SR)
    s = pad_to(tone(180,dur),n)*0.3 + pad_to(tone(280,dur),n)*0.25 + pad_to(noise_burst(dur),n)*0.35
    return np.clip(env(s,0.003,0.02,0.2,0.07)*.5,-1,1)

def snd_place_dirt():
    dur=0.2; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.6 + pad_to(tone(50,dur),n)*0.2
    return np.clip(env(s,0.008,0.03,0.3,0.12)*.4,-1,1)

def snd_place_glass():
    dur=0.2; n=int(dur*SR)
    s = pad_to(tone(1800,dur),n)*0.3 + pad_to(tone(2500,0.15),n)*0.2 + pad_to(noise_burst(dur),n)*0.3
    return np.clip(env(s,0.001,0.02,0.15,0.1)*.45,-1,1)

def snd_fall_stone():
    dur=0.2; n=int(dur*SR)
    s = pad_to(tone(150,dur),n)*0.3 + pad_to(noise_burst(dur),n)*0.5
    return np.clip(env(s,0.001,0.03,0.2,0.1)*.4,-1,1)

def snd_fall_dirt():
    dur=0.25; n=int(dur*SR)
    s = pad_to(noise_burst(dur),n)*0.5 + pad_to(tone(40,dur),n)*0.2
    return np.clip(env(s,0.01,0.04,0.3,0.15)*.35,-1,1)

def snd_fall_wood():
    dur=0.2; n=int(dur*SR)
    s = pad_to(tone(180,dur),n)*0.3 + pad_to(noise_burst(dur),n)*0.4
    return np.clip(env(s,0.002,0.03,0.2,0.1)*.4,-1,1)

# ═══════════════════════════════════════════════════
#  SHADER FILES
# ═══════════════════════════════════════════════════

SHADER_VERTEX = """// Ultra Vibrant Visuals Shader - Vertex
// Bedrock Mobile Optimized (No Ray Tracing)
#version 300 es
precision highp float;

in vec4 a_position;
in vec4 a_color;
in vec2 a_uv0;
in vec2 a_uv1;
in vec4 a_normal;

uniform mat4 WORLDVIEWPROJ;
uniform mat4 WORLD;
uniform vec3 VIEW_POS;
uniform vec4 LIGHT_COLOR;
uniform vec4 FOG_COLOR;
uniform vec3 SUN_DIR;
uniform vec3 MOON_DIR;
uniform float TIME;

out vec4 v_color;
out vec2 v_uv0;
out vec2 v_uv1;
out vec3 v_normal;
out vec3 v_worldPos;
out float v_fog;
out vec3 v_sunDir;
out vec3 v_moonDir;

float fogFactor(float d, float start, float end) {
    return clamp((end-d)/(end-start), 0.0, 1.0);
}

void main() {
    gl_Position = WORLDVIEWPROJ * a_position;
    v_uv0 = a_uv0;
    v_uv1 = a_uv1;
    v_color = a_color;
    v_normal = normalize((WORLD * vec4(a_normal.xyz, 0.0)).xyz);
    v_worldPos = (WORLD * a_position).xyz;
    v_sunDir = normalize(SUN_DIR);
    v_moonDir = normalize(MOON_DIR);

    float dist = length((WORLDVIEWPROJ * a_position).xyz);
    v_fog = fogFactor(dist, 50.0, 110.0);
}
"""

SHADER_FRAGMENT = """// Ultra Vibrant Visuals Shader - Fragment
// Vibrant Visuals / PBR-Inspired / Mobile Optimized
#version 300 es
precision highp float;

in vec4 v_color;
in vec2 v_uv0;
in vec2 v_uv1;
in vec3 v_normal;
in vec3 v_worldPos;
in float v_fog;
in vec3 v_sunDir;
in vec3 v_moonDir;

uniform sampler2D TEXTURE_0;
uniform sampler2D TEXTURE_1;
uniform vec4 FOG_COLOR;
uniform vec4 CURRENT_COLOR;
uniform float TIME;
uniform vec3 VIEW_POS;

out vec4 fragColor;

const float PI = 3.14159265;
const float INV_GAMMA = 0.4545;

// Vibrance
vec3 vibrance(vec3 c, float str) {
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float sat = mx - mn;
    return mix(vec3(luma), c, 1.0 + str * (1.0 - sat));
}

// ACES Tone Map
vec3 aces(vec3 x) {
    return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0);
}

void main() {
    vec4 tex = texture(TEXTURE_0, v_uv0);
    if (tex.a < 0.5) discard;

    vec2 lm = v_uv1;
    vec4 lc = texture(TEXTURE_1, lm);
    vec3 base = tex.rgb * v_color.rgb;
    vec3 N = normalize(v_normal);
    vec3 V = normalize(VIEW_POS - v_worldPos);

    // Time of day
    float tod = sin(TIME * 0.0003) * 0.5 + 0.5;

    // Sun color: warm at sunrise/sunset, white midday
    vec3 sunCol = mix(vec3(1.0, 0.6, 0.3), vec3(1.0, 0.97, 0.95), smoothstep(0.2, 0.5, tod));
    sunCol = mix(vec3(0.15, 0.15, 0.3), sunCol, smoothstep(0.0, 0.3, tod));
    vec3 moonCol = vec3(0.4, 0.5, 0.7) * 0.25;

    // Lighting
    float sunDot = max(dot(N, v_sunDir), 0.0);
    float moonDot = max(dot(N, v_moonDir), 0.0);
    vec3 direct = sunCol * sunDot * 1.4 + moonCol * moonDot * 0.3;

    // Ambient
    vec3 ambDay = vec3(0.4, 0.45, 0.5);
    vec3 ambNight = vec3(0.06, 0.08, 0.12);
    vec3 ambSunset = vec3(0.3, 0.2, 0.15);
    vec3 ambient = mix(ambNight, ambDay, smoothstep(0.0, 0.3, tod));
    ambient = mix(ambient, ambSunset, smoothstep(0.15, 0.25, tod) * (1.0 - smoothstep(0.25, 0.35, tod)));

    // Block light (torches, lava)
    vec3 blockLight = vec3(1.0, 0.7, 0.4) * lm.x * 1.8;
    vec3 skyLight = ambient * lm.y;

    // Shadow
    float shadow = smoothstep(-10.0, 64.0, v_worldPos.y) * smoothstep(-0.1, 0.3, dot(vec3(0,1,0), v_sunDir));

    vec3 totalLight = direct * shadow + skyLight + blockLight;
    totalLight = max(totalLight, vec3(0.08));
    vec3 lit = base * totalLight;

    // Specular for shiny blocks
    vec3 H = normalize(v_sunDir + V);
    float spec = pow(max(dot(N, H), 0.0), 32.0);
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float refl = smoothstep(0.65, 0.9, luma) * 0.25;
    lit += refl * spec * sunCol;

    // Vibrance + contrast + saturation
    lit = vibrance(lit, 0.45);
    lit = (lit - 0.5) * 1.18 + 0.5;
    float l = dot(lit, vec3(0.2126, 0.7152, 0.0722));
    lit = mix(vec3(l), lit, 1.35);

    // Warm color grading
    lit.r *= 1.06;
    lit.b *= 0.96;

    // ACES tone mapping
    lit = aces(lit * 1.2);
    lit = pow(lit, vec3(INV_GAMMA));

    // Fog
    vec3 fog = pow(FOG_COLOR.rgb, vec3(INV_GAMMA));
    vec3 final = mix(lit, fog, v_fog);

    // Subtle film grain
    float grain = fract(sin(dot(v_uv0 * 1000.0 + TIME, vec2(12.9898, 78.233))) * 43758.5453) * 0.02 - 0.01;
    final += grain;

    fragColor = vec4(final, tex.a);
}
"""

SHADER_MATERIAL = """{
  "materials": {
    "version": "1.0.0",
    "vibrant_realistic:terrain": {
      "+defines": ["FANCY"],
      "+states": ["BlendedAlphaTest", "EnableStencilTest"],
      "vertex": "vibrant_realistic:renderchunk.vertex",
      "fragment": "vibrant_realistic:renderchunk.fragment",
      "vertexFields": [
        {"field":"Position"},{"field":"Color"},{"field":"UV0"},{"field":"UV1"},{"field":"Normal"}
      ],
      "variants": [
        {"+defines":["ALPHA_TEST"],"+states":["EnableAlphaTest"]},
        {"+defines":["BLENDED"],"+states":["EnableBlend"]}
      ]
    }
  }
}
"""

SUNNY_FOG = """// Ultra Vibrant - Sunny Fog
#version 300 es
precision highp float;

in vec2 v_uv;
out vec4 fragColor;

uniform vec4 FOG_COLOR;
uniform float TIME;
uniform float FAR;

void main() {
    float tod = sin(TIME * 0.0003) * 0.5 + 0.5;
    vec3 dayFog = vec3(0.75, 0.82, 0.92);
    vec3 sunsetFog = vec3(0.85, 0.55, 0.35);
    vec3 nightFog = vec3(0.05, 0.06, 0.12);

    vec3 fog = mix(nightFog, dayFog, smoothstep(0.0, 0.3, tod));
    fog = mix(fog, sunsetFog, smoothstep(0.15, 0.25, tod) * (1.0 - smoothstep(0.25, 0.35, tod)));

    fog = mix(fog, FOG_COLOR.rgb, 0.3);
    fragColor = vec4(fog, 1.0);
}
"""

# ═══════════════════════════════════════════════════
#  JSON CONFIGS
# ═══════════════════════════════════════════════════

def make_manifest():
    return {
        "format_version": 2,
        "header": {
            "name": "§e§lUltra §a§lVibrant §b§lRealistic",
            "description": "§7Ultra Realistic Textures + Vibrant Shaders + HD Sounds\\n§aOptimized for Android | No Ray Tracing",
            "uuid": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "version": [2, 0, 0],
            "min_engine_version": [1, 21, 0]
        },
        "modules": [{
            "type": "resources",
            "uuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "version": [2, 0, 0]
        }],
        "metadata": {
            "authors": ["VibrantRealistic"],
            "license": "MIT"
        }
    }

# ═══════════════════════════════════════════════════
#  BUILD
# ═══════════════════════════════════════════════════

def main():
    print("╔══════════════════════════════════════════════════╗")
    print("║   Ultra Vibrant Realistic Pack Builder v2.0     ║")
    print("║   Bedrock 1.26+ | Android | No Ray Tracing      ║")
    print("╚══════════════════════════════════════════════════╝")
    print()

    # Clean temp
    os.system(f"rm -rf {TMP}")
    pack_dir = TMP
    tb = f"{pack_dir}/textures/blocks"
    te = f"{pack_dir}/textures/environment"
    tc = f"{pack_dir}/textures/colormap"
    tp = f"{pack_dir}/textures/particle"
    tu = f"{pack_dir}/textures/ui"
    sg = f"{pack_dir}/shaders/glsl"
    sm = f"{pack_dir}/shaders/materials"

    for d in [tb, te, tc, tp, tu, sg, sm]:
        os.makedirs(d, exist_ok=True)

    # ─── MANIFEST ───
    print("[1/7] Writing manifest & configs...")
    with open(f"{pack_dir}/manifest.json", 'w') as f: json.dump(make_manifest(), f, indent=2)

    # ─── TEXTURES ───
    print("[2/7] Generating textures...")
    tc_map = {}

    def reg(name, path=None):
        tc_map[name] = path or f"textures/blocks/{name}"

    # Copy AI-generated photorealistic textures first (they override procedural)
    ai_dir = "/home/user/Atta/ai_textures"
    if os.path.exists(ai_dir):
        ai_count = 0
        for fn in os.listdir(ai_dir):
            if fn.endswith('.png'):
                src = os.path.join(ai_dir, fn)
                dst = os.path.join(tb, fn)
                img = Image.open(src)
                if img.size[0] != TEX or img.size[1] != TEX:
                    img = img.resize((TEX, TEX), Image.LANCZOS)
                img.save(dst, optimize=True, compress_level=9)
                ai_count += 1
        print(f"   ✅ {ai_count} AI photorealistic textures loaded (resized to {TEX}x{TEX})")

    # ---- TERRAIN ----
    # Helper functions defined before defs
    def _grass_side():
        n=perlin(TEX,5,4,1)
        Y,_=np.mgrid[0:TEX,0:TEX]
        r=np.where(Y<TEX//3, 65+n*20, 130+n*25)
        g=np.where(Y<TEX//3, 135+n*25, 90+n*18)
        b=np.where(Y<TEX//3, 35+n*15, 55+n*12)
        return grain(tileable(rgb(r,g,b)),6)

    def _glass_pane():
        r=np.zeros((TEX,TEX),np.uint8); g=np.zeros((TEX,TEX),np.uint8)
        b=np.zeros((TEX,TEX),np.uint8); a=np.zeros((TEX,TEX),np.uint8)
        X,_=np.mgrid[0:TEX,0:TEX]; d=np.abs(X-TEX//2)
        m1=d<4; m2=(d>=4)&(d<6)
        r[m1]=200;g[m1]=210;b[m1]=220;a[m1]=150
        r[m2]=210;g[m2]=215;b[m2]=225;a[m2]=80
        return rgba(r,g,b,a)

    def _tnt_side(sd):
        n=perlin(TEX,3,2,sd); Y,X=np.mgrid[0:TEX,0:TEX]
        r=clamp(180+n*20); g=clamp(50+n*10); b=clamp(40+n*8)
        bm=np.abs(Y-TEX//2)<15
        r[bm]=clamp(220+n[bm]*15); g[bm]=clamp(215+n[bm]*15); b[bm]=clamp(200+n[bm]*12)
        return rgb(r,g,b)

    defs = {
        # name: generator
        "grass_top":        lambda: tex_noise((65,135,35),(30,35,20),7,4,1),
        "grass_side":       lambda: _grass_side(),
        "dirt":             lambda: tex_noise((130,90,55),(30,25,20),7,4,2),
        "coarse_dirt":      lambda: tex_noise((130,90,55),(30,25,20),7,4,10),
        "stone":            lambda: tex_stone((140,140,140),(25,25,25),(110,110,110),3),
        "cobblestone":      lambda: tex_stone((125,125,125),(30,30,30),(95,95,95),4),
        "sand":             lambda: tex_noise((215,195,145),(20,18,15),5,4,5),
        "red_sand":         lambda: tex_noise((195,120,55),(25,18,12),5,4,6),
        "gravel":           lambda: tex_noise((140,130,120),(28,25,22),6,4,7),
        "bedrock":          lambda: tex_stone((35,35,35),(25,25,25),(20,20,20),50),
        "netherrack":       lambda: tex_noise((100,40,35),(40,20,15),6,5,230,8),
        "soul_sand":        lambda: tex_noise((80,60,40),(25,20,15),6,4,233),
        "soul_soil":        lambda: tex_noise((70,55,38),(22,18,12),5,4,234),
        "glowstone":        lambda: tex_noise((210,180,80),(40,35,25),4,4,235),
        "obsidian":         lambda: tex_noise((15,12,25),(15,12,20),5,4,236,3),
        "crying_obsidian":  lambda: tex_noise((50,20,80),(40,15,60),5,4,237,3),
        "end_stone":        lambda: tex_noise((210,215,180),(20,22,15),5,4,240),
        "clay":             lambda: tex_noise((160,155,165),(20,18,22),5,4,210),
        "snow":             lambda: tex_noise((235,240,245),(15,15,18),6,4,200),
        "ice":              lambda: tex_noise((180,195,225),(25,28,35),4,4,201),
        "packed_ice":       lambda: tex_noise((160,180,215),(20,25,30),4,4,202),
        "blue_ice":         lambda: tex_noise((130,155,220),(25,30,35),4,4,203),
        "farmland":         lambda: tex_farmland(False,190),
        "farmland_moist":   lambda: tex_farmland(True,191),
        "mycelium_side":    lambda: tex_noise((110,90,115),(25,20,28),6,4,220),
        "mycelium_top":     lambda: tex_noise((110,90,115),(30,25,35),7,4,221),
        "calcite":          lambda: tex_noise((210,210,205),(15,15,12),4,4,412),
        "tuff":             lambda: tex_stone((110,110,100),(18,18,15),(85,85,78),413),
        "dripstone_block":  lambda: tex_stone((135,120,100),(20,18,15),(105,92,78),414),
        "mud":              lambda: tex_noise((80,65,50),(20,18,15),5,4,390),
        "packed_mud":       lambda: tex_noise((130,100,65),(18,15,12),4,4,392),
        "sculk":            lambda: tex_sculk(393),
        "sculk_catalyst_top": lambda: tex_noise((20,40,45),(12,18,20),5,4,394),
        "sculk_catalyst_side":lambda: tex_noise((18,35,40),(10,15,18),5,4,395),
        "suspicious_gravel":lambda: tex_noise((145,135,125),(25,22,18),6,4,420),
        "suspicious_sand":  lambda: tex_noise((210,195,150),(20,18,15),5,4,421),
        "copper_block":     lambda: tex_noise((175,115,65),(20,15,10),3,4,400),
        "weathered_copper": lambda: tex_noise((85,140,100),(18,22,15),3,4,402),
        "oxidized_copper":  lambda: tex_noise((60,150,120),(15,20,15),3,4,404),
        "exposed_copper":   lambda: tex_noise((140,125,85),(18,15,12),3,4,406),
        "lava_still":       lambda: tex_lava(280),
        "lava_flow":        lambda: tex_lava(281),
        "water_still":      lambda: tex_noise((40,80,140),(15,25,35),4,4,900),
        "water_flow":       lambda: tex_noise((35,75,135),(12,22,30),3,4,901),
        # Dirt podzol
        "dirt_podzol_side": lambda: tex_noise((130,90,55),(25,20,15),5,4,11),
        "dirt_podzol_top":  lambda: tex_noise((100,85,50),(30,25,20),6,4,12),
        # Stone variants
        "stone_granite":        lambda: tex_stone((180,140,120),(30,25,20),(140,100,80),20),
        "stone_granite_smooth": lambda: tex_stone((190,150,130),(15,12,10),(160,120,100),21),
        "stone_diorite":        lambda: tex_stone((190,190,195),(25,25,25),(160,160,165),22),
        "stone_diorite_smooth": lambda: tex_stone((200,200,205),(12,12,12),(180,180,185),23),
        "stone_andesite":       lambda: tex_stone((140,140,140),(20,20,20),(110,110,110),24),
        "stone_andesite_smooth":lambda: tex_stone((150,150,150),(10,10,10),(130,130,130),25),
        "cobblestone_mossy":    lambda: tex_noise((100,110,85),(30,35,25),6,4,30),
        # Ores
        "coal_ore":     lambda: tex_ore((140,140,140),(30,30,30),(15,15,15),(50,50,50),sd=42),
        "iron_ore":     lambda: tex_ore((140,140,140),(185,165,145),(150,130,110),(210,190,170),sd=41),
        "gold_ore":     lambda: tex_ore((140,140,140),(220,190,50),(180,150,30),(255,220,80),sd=40),
        "diamond_ore":  lambda: tex_ore((140,140,140),(80,210,220),(50,170,180),(120,240,250),sd=43),
        "redstone_ore": lambda: tex_ore((140,140,140),(180,30,30),(140,15,15),(220,50,50),sd=44),
        "lapis_ore":    lambda: tex_ore((140,140,140),(30,50,160),(20,35,130),(50,70,200),sd=45),
        "emerald_ore":  lambda: tex_ore((140,140,140),(40,180,80),(25,140,55),(60,220,110),sd=46),
        "copper_ore":   lambda: tex_ore((140,140,140),(170,110,60),(140,85,40),(200,140,80),sd=47),
        # Deepslate
        "deepslate":            lambda: tex_stone((60,60,65),(15,15,18),(40,40,45),370),
        "deepslate_top":        lambda: tex_stone((65,65,70),(12,12,15),(45,45,50),371),
        "deepslate_coal_ore":   lambda: tex_ore((60,60,65),(25,25,25),(12,12,12),(40,40,40),sd=372),
        "deepslate_iron_ore":   lambda: tex_ore((60,60,65),(185,165,145),(150,130,110),(210,190,170),sd=373),
        "deepslate_gold_ore":   lambda: tex_ore((60,60,65),(220,190,50),(180,150,30),(255,220,80),sd=374),
        "deepslate_diamond_ore":lambda: tex_ore((60,60,65),(80,210,220),(50,170,180),(120,240,250),sd=375),
        "deepslate_redstone_ore":lambda:tex_ore((60,60,65),(180,30,30),(140,15,15),(220,50,50),sd=376),
        "deepslate_lapis_ore":  lambda: tex_ore((60,60,65),(30,50,160),(20,35,130),(50,70,200),sd=377),
        "deepslate_emerald_ore":lambda: tex_ore((60,60,65),(40,180,80),(25,140,55),(60,220,110),sd=378),
        "deepslate_copper_ore": lambda: tex_ore((60,60,65),(170,110,60),(140,85,40),(200,140,80),sd=379),
        "cobbled_deepslate":    lambda: tex_stone((55,55,60),(20,20,22),(35,35,40),380),
        "deepslate_bricks":     lambda: tex_sbricks(381),
        "deepslate_tiles":      lambda: tex_sbricks(382),
        # Nether
        "nether_bricks":    lambda: tex_nether_bricks(231),
        "red_nether_bricks":lambda: tex_nether_bricks(232,True),
        # End
        "end_stone_bricks": lambda: tex_sbricks(241),
        "purpur_block":     lambda: tex_noise((150,90,150),(25,15,25),4,4,242),
        "purpur_pillar":    lambda: tex_noise((155,95,155),(20,12,20),3,4,243),
        # Amethyst
        "amethyst_block":   lambda: tex_amethyst(410),
        "amethyst_cluster": lambda: tex_amethyst(411),
        # Decorated
        "decorated_pot_side":lambda: tex_noise((155,110,75),(22,18,15),4,4,428),
    }

    # Planks
    for name, l, d, sd in [
        ("oak_planks",(160,120,70),(140,100,58),60),("spruce_planks",(95,65,35),(80,55,30),61),
        ("birch_planks",(195,175,125),(180,160,110),62),("jungle_planks",(150,110,65),(130,95,55),63),
        ("acacia_planks",(160,90,50),(140,78,42),64),("dark_oak_planks",(65,42,22),(55,35,18),65),
        ("mangrove_planks",(120,65,50),(100,55,42),66),("cherry_planks",(220,180,170),(200,160,150),67),
        ("bamboo_planks",(170,155,70),(150,135,60),68),("crimson_planks",(100,48,55),(85,38,45),69),
        ("warped_planks",(50,100,95),(40,85,80),69),
    ]:
        defs[name] = lambda l=l,d=d,sd=sd: tex_plank(l,d,sd)

    # Log sides
    for name, b, dk, sd in [
        ("oak_log_side",(130,100,60),(80,60,35),70),("spruce_log_side",(60,45,25),(35,25,15),71),
        ("birch_log_side",(210,210,200),(160,140,120),72),("jungle_log_side",(85,65,30),(55,40,20),73),
        ("acacia_log_side",(100,100,95),(65,65,60),74),("dark_oak_log_side",(45,35,20),(25,18,10),75),
        ("mangrove_log_side",(80,55,35),(50,35,20),76),("cherry_log_side",(170,130,120),(120,85,75),77),
        ("bamboo_block",(140,145,60),(100,105,40),78),
        ("stripped_oak_log_side",(170,140,85),(130,100,60),80),
        ("stripped_spruce_log_side",(110,85,55),(80,60,35),81),
        ("stripped_birch_log_side",(190,180,160),(150,140,120),82),
        ("stripped_jungle_log_side",(140,115,70),(105,85,50),83),
        ("stripped_acacia_log_side",(150,100,60),(115,75,45),84),
        ("stripped_dark_oak_log_side",(75,60,35),(50,40,22),85),
        ("stripped_mangrove_log_side",(100,75,50),(70,50,32),86),
        ("stripped_cherry_log_side",(195,160,148),(155,120,108),87),
    ]:
        defs[name] = lambda b=b,dk=dk,sd=sd: tex_bark(b,dk,sd)

    # Log tops
    for name, s, h, sd in [
        ("oak_log_top",(170,140,80),(120,90,50),70),("spruce_log_top",(140,110,65),(90,65,35),71),
        ("birch_log_top",(200,190,170),(170,155,130),72),("jungle_log_top",(120,90,50),(80,60,35),73),
        ("acacia_log_top",(160,130,70),(110,85,50),74),("dark_oak_log_top",(85,65,30),(55,40,20),75),
        ("mangrove_log_top",(110,75,45),(75,50,30),76),("cherry_log_top",(200,160,145),(150,110,95),77),
        ("bamboo_block_top",(170,170,80),(130,130,55),78),
    ]:
        defs[name] = lambda s=s,h=h,sd=sd: tex_logtop(s,h,sd)

    # Leaves
    for name, b, dk, lt, sd in [
        ("oak_leaves",(60,120,30),(35,85,20),(80,150,45),100),
        ("spruce_leaves",(30,70,35),(15,50,20),(45,90,50),101),
        ("birch_leaves",(70,130,40),(45,95,25),(95,160,60),102),
        ("jungle_leaves",(45,110,25),(25,75,15),(65,140,40),103),
        ("acacia_leaves",(55,105,35),(30,70,20),(75,135,50),104),
        ("dark_oak_leaves",(40,90,25),(20,60,15),(55,115,35),105),
        ("mangrove_leaves",(50,100,30),(30,65,18),(70,130,45),106),
        ("cherry_leaves",(220,170,180),(190,130,145),(240,200,210),107),
        ("azalea_leaves",(50,110,35),(30,75,22),(70,140,50),108),
        ("azalea_leaves_flowers",(180,130,170),(150,100,140),(210,160,200),109),
    ]:
        defs[name] = lambda b=b,dk=dk,lt=lt,sd=sd: tex_leaf(b,dk,lt,sd)

    # Glass
    defs["glass"] = lambda: tex_glass(120)
    defs["glass_pane_top"] = lambda: _glass_pane()
    for i,(n,c) in enumerate([
        ("white",(230,230,230)),("orange",(230,150,50)),("magenta",(180,70,180)),
        ("light_blue",(80,170,230)),("yellow",(230,220,50)),("lime",(100,220,50)),
        ("pink",(230,130,170)),("gray",(100,100,100)),("light_gray",(160,160,160)),
        ("cyan",(50,160,180)),("purple",(120,50,180)),("blue",(50,70,200)),
        ("brown",(130,90,50)),("green",(60,140,50)),("red",(200,50,50)),("black",(30,30,30)),
    ]):
        defs[f"{n}_glass"] = lambda c=c: tex_colored_glass(c)

    # Sandstone
    defs["sandstone_top"]     = lambda: tex_noise((215,195,145),(20,18,15),4,4,150)
    defs["sandstone_bottom"]  = lambda: tex_noise((200,180,130),(25,20,18),5,4,151)
    defs["sandstone_side"]    = lambda: tex_sandstone_side(152)
    defs["red_sandstone_top"] = lambda: tex_noise((195,120,55),(25,18,12),4,4,153)
    defs["red_sandstone_bottom"]=lambda: tex_noise((180,110,50),(28,20,15),5,4,154)
    defs["red_sandstone_side"]= lambda: tex_sandstone_side(155)

    # Bricks & stone bricks
    defs["bricks"]             = lambda: tex_bricks((165,80,60),(180,175,165),160)
    defs["stone_bricks"]       = lambda: tex_sbricks(161)
    defs["stone_bricks_mossy"] = lambda: tex_sbricks(162)
    defs["stone_bricks_cracked"]=lambda: tex_sbricks(163)
    defs["stone_bricks_carved"]= lambda: tex_sbricks(164)
    defs["mud_bricks"]         = lambda: tex_bricks((130,100,65),(160,145,120),391)

    # Crafting table
    defs["crafting_table_top"]  = lambda: tex_craft_top(170)
    defs["crafting_table_front"]= lambda: tex_plank((140,100,55),(120,85,45),171)
    defs["crafting_table_side"] = lambda: tex_plank((140,100,55),(120,85,45),172)

    # Furnace
    defs["furnace_front_off"]= lambda: tex_furnace_front(False,180)
    defs["furnace_front_on"] = lambda: tex_furnace_front(True,181)
    defs["furnace_side"]     = lambda: tex_noise((130,130,130),(25,25,25),5,4,182)
    defs["furnace_top"]      = lambda: tex_noise((135,135,135),(20,20,20),4,4,183)

    # TNT
    defs["tnt_side"]   = lambda: _tnt_side(250)
    defs["tnt_top"]    = lambda: tex_noise((180,50,40),(20,10,10),3,4,251)
    defs["tnt_bottom"] = lambda: tex_noise((100,90,80),(15,12,10),3,4,252)

    # Melon & Pumpkin
    defs["melon_side"]   = lambda: tex_melon_side(260)
    defs["melon_top"]    = lambda: tex_noise((70,120,25),(20,30,10),4,4,261)
    defs["pumpkin_side"] = lambda: tex_pumpkin_side(262)
    defs["pumpkin_top"]  = lambda: tex_noise((180,140,50),(25,20,12),4,4,263)
    defs["pumpkin_face"] = lambda: tex_pumpkin_side(264)

    # Hay, bone, misc
    defs["hay_block_side"]  = lambda: tex_hay(270)
    defs["hay_block_top"]   = lambda: tex_noise((165,135,38),(30,25,15),4,4,271)
    defs["bone_block_side"] = lambda: tex_noise((210,205,190),(25,22,18),5,4,272)
    defs["bone_block_top"]  = lambda: tex_noise((215,210,195),(20,18,15),4,4,273)
    defs["dried_kelp_side"] = lambda: tex_noise((50,60,35),(20,25,15),5,4,274)
    defs["dried_kelp_top"]  = lambda: tex_noise((45,55,30),(18,22,12),4,4,275)
    defs["bookshelf"]       = lambda: tex_bookshelf(290)
    defs["mangrove_roots_side"]=lambda: tex_bark((90,70,40),(55,40,25),415)
    defs["mangrove_roots_top"] =lambda: tex_logtop((110,85,50),(75,55,35),416)
    defs["muddy_mangrove_roots_side"]=lambda: tex_bark((80,65,40),(50,38,22),417)
    defs["muddy_mangrove_roots_top"]=lambda: tex_logtop((90,72,45),(60,48,30),418)

    # Sculk
    defs["sculk_shrieker_top"]=lambda: tex_noise((30,55,60),(18,22,25),4,4,399)
    defs["sculk_sensor_top"] =lambda: tex_noise((25,50,55),(15,20,22),4,4,396)
    defs["sculk_sensor_side"]=lambda: tex_noise((20,42,48),(12,18,20),5,4,397)
    defs["sculk_vein"]       =lambda: tex_sculk(398)

    # Copper cuts
    defs["cut_copper"]           =lambda: tex_sbricks(401)
    defs["weathered_cut_copper"] =lambda: tex_sbricks(403)
    defs["oxidized_cut_copper"]  =lambda: tex_sbricks(405)
    defs["exposed_cut_copper"]   =lambda: tex_sbricks(407)

    # Wool
    for i,(n,c) in enumerate([
        ("white",(230,230,230)),("orange",(220,130,40)),("magenta",(170,70,170)),
        ("light_blue",(60,160,220)),("yellow",(220,210,40)),("lime",(90,210,40)),
        ("pink",(220,130,160)),("gray",(80,80,80)),("silver",(140,140,140)),
        ("cyan",(40,150,170)),("purple",(110,45,170)),("blue",(40,55,190)),
        ("brown",(120,80,45)),("green",(55,130,40)),("red",(180,45,40)),("black",(25,25,25)),
    ]):
        defs[f"wool_colored_{n}"] = lambda c=c,sd=300+i: tex_wool(c,sd)

    # Concrete
    for i,(n,c) in enumerate([
        ("white",(205,210,210)),("orange",(215,120,35)),("magenta",(160,55,155)),
        ("light_blue",(45,150,200)),("yellow",(210,195,40)),("lime",(85,190,35)),
        ("pink",(200,110,145)),("gray",(65,65,65)),("silver",(130,130,130)),
        ("cyan",(25,130,150)),("purple",(95,35,155)),("blue",(35,45,170)),
        ("brown",(100,65,35)),("green",(50,110,35)),("red",(160,35,30)),("black",(15,15,15)),
    ]):
        defs[f"concrete_{n}"] = lambda c=c,sd=320+i: tex_concrete(c,sd)

    # Terracotta
    defs["terracotta"]    = lambda: tex_noise((150,100,70),(25,18,15),4,4,340)
    defs["hardened_clay"] = lambda: tex_noise((150,100,70),(25,18,15),4,4,341)
    for i,(n,b,p) in enumerate([
        ("white",(210,195,175),(190,175,155)),("orange",(160,90,40),(140,75,30)),
        ("magenta",(140,70,90),(120,55,75)),("light_blue",(80,110,140),(65,95,125)),
        ("yellow",(180,160,60),(160,140,45)),("lime",(100,130,50),(85,115,38)),
        ("pink",(160,100,100),(140,85,85)),("gray",(60,50,45),(50,40,35)),
        ("silver",(130,110,95),(115,95,80)),("cyan",(70,100,105),(55,85,90)),
        ("purple",(110,65,85),(95,52,70)),("blue",(65,65,110),(50,50,95)),
        ("brown",(110,75,45),(95,62,35)),("green",(80,100,55),(65,85,42)),
        ("red",(140,60,45),(120,48,35)),("black",(35,25,22),(25,18,15)),
    ]):
        defs[f"terracotta_{n}"] = lambda b=b,p=p,sd=350+i: tex_terracotta(b,p,sd)

    # Generate all textures
    total = 0
    for name, gen in defs.items():
        path = f"{tb}/{name}.png"
        if not os.path.exists(path):
            img = gen()
            save(img, path)
            total += 1
    print(f"   ✅ {total} block textures generated")

    # ─── ENVIRONMENT ───
    print("   Generating environment...")
    # Sun
    Y,X=np.mgrid[0:256,0:256]; d=np.sqrt((X-128)**2+(Y-128)**2)
    r=255; g=250; b=220; a=np.where(d<85,255,np.where(d<128,np.clip((1-(d-85)/43)*100,0,255).astype(np.uint8),0))
    img=rgba(np.where(d<85,255,0).astype(np.uint8),np.where(d<85,250,0).astype(np.uint8),np.where(d<85,220,0).astype(np.uint8),a)
    save(img, f"{te}/sun.png")
    # Moon
    Y,X=np.mgrid[0:256,0:256]; d=np.sqrt((X-128)**2+(Y-128)**2)
    n=perlin(256,8,3,501); m=d<85
    r=np.zeros((256,256),np.uint8); g=np.zeros_like(r); b=np.zeros_like(r); a=np.zeros_like(r)
    moon_base=(200+n*30).astype(int)
    r[m]=clamp(moon_base[m]+np.random.randint(-5,6,r[m].shape))
    g[m]=clamp(moon_base[m]-5+np.random.randint(-5,6,g[m].shape))
    b[m]=clamp(moon_base[m]+5+np.random.randint(-5,6,b[m].shape))
    a[m]=255
    save(rgba(r,g,b,a), f"{te}/moon_phases.png")
    # Clouds
    n=perlin(256,4,5,502); n2=perlin(256,8,3,530); cl=n*.7+n2*.3
    r=np.zeros((256,256),np.uint8); g=np.zeros_like(r); b=np.zeros_like(r); a=np.zeros_like(r)
    cm=cl>0.1; br=235+cl*20
    r[cm]=clamp(br[cm]); g[cm]=clamp(br[cm]+2); b[cm]=clamp(br[cm]+8)
    a[cm]=clamp(np.minimum(1,(cl[cm]-.1)*3)*220)
    save(rgba(r,g,b,a), f"{te}/clouds.png")

    # Colormaps
    Y,X=np.mgrid[0:256,0:256]; t=X/256; h=Y/256
    save(rgb(50+t*60+np.where(h<.5,50,0),100+h*100-t*30,30+t*30), f"{tc}/foliage.png")
    save(rgb(60+t*80-h*20,100+h*100-t*20,30+t*40-h*10), f"{tc}/grass.png")

    # Particles
    for n,c in [("generic_0",(200,200,200)),("flame",(255,160,40)),("smoke",(150,150,150)),
                ("splash",(100,150,200)),("heart",(220,50,50)),("crit",(255,255,255)),
                ("dust",(180,150,100)),("falling_dust",(160,140,110))]:
        save(np.full((8,8,4),(*c,200),np.uint8), f"{tp}/{n}.png")

    # UI
    hb=np.full((22,182,4),[30,30,30,150],np.uint8)
    hb[:2]=0;hb[-2:]=0;hb[:,:2]=0;hb[:,-2:]=0
    hb[0]=[0,0,0,180];hb[-1]=[0,0,0,180]
    save(hb, f"{tu}/hotbar.png")
    inv=np.full((256,256,4),[45,45,45,230],np.uint8)
    inv[:3]=[20,20,20,240];inv[-3:]=[20,20,20,240];inv[:,:3]=[20,20,20,240];inv[:,-3:]=[20,20,20,240]
    save(inv, f"{tu}/inventory.png")

    # Pack icon
    pi=np.full((256,256,4),[15,15,25,255],np.uint8)
    pi[70:100,48:208]=[60,140,30,255]  # grass strip
    pi[100:220,48:208]=[130,90,55,255]  # dirt
    save(pi, f"{pack_dir}/pack_icon.png")

    # ─── SHADERS ───
    print("[3/7] Writing shaders...")
    with open(f"{sg}/renderchunk.vertex", 'w') as f: f.write(SHADER_VERTEX)
    with open(f"{sg}/renderchunk.fragment", 'w') as f: f.write(SHADER_FRAGMENT)
    with open(f"{sg}/sunny.fog", 'w') as f: f.write(SUNNY_FOG)
    os.makedirs(sm, exist_ok=True)
    with open(f"{sm}/terrain.material", 'w') as f: f.write(SHADER_MATERIAL)

    # ─── SOUNDS ───
    print("[4/7] Generating sounds...")
    sb = f"{pack_dir}/sounds"
    sound_defs = {}

    def reg_sound(event, files, category="block"):
        sound_defs[event] = {"category": category, "sounds": [{"name": f, "load_on_low_memory": True} for f in files]}

    # Block break sounds
    break_sounds = {
        "stone": snd_stone_break, "dirt": snd_dirt_break, "grass": snd_grass_break,
        "wood": snd_wood_break, "glass": snd_glass_break,
    }
    step_sounds = {
        "stone": snd_stone_step, "dirt": snd_dirt_step, "grass": snd_grass_step,
        "wood": snd_wood_step, "glass": snd_ice_step, "sand": snd_sand_step,
        "gravel": snd_gravel_step, "metal": snd_metal_step, "snow": snd_snow_step,
        "ice": snd_ice_step, "wool": snd_wool_step,
    }

    generated_sounds = 0
    for mat, gen in break_sounds.items():
        d = f"{sb}/block/{mat}"
        os.makedirs(d, exist_ok=True)
        files = []
        for i in range(4):
            fname = f"sounds/block/{mat}/break{i+1}"
            save_wav(f"{pack_dir}/{fname}.wav", gen())
            files.append(fname)
            generated_sounds += 1
        reg_sound(f"dig.{mat}", files)

    for mat, gen in step_sounds.items():
        d = f"{sb}/block/{mat}"
        os.makedirs(d, exist_ok=True)
        files = []
        for i in range(4):
            fname = f"sounds/block/{mat}/step{i+1}"
            save_wav(f"{pack_dir}/{fname}.wav", gen())
            files.append(fname)
            generated_sounds += 1
        reg_sound(f"step.{mat}", files)

    # Place sounds (reuse break at lower volume)
    for mat in ["stone", "dirt", "grass", "wood", "glass", "sand", "gravel", "metal", "snow", "ice", "wool"]:
        files = [f"sounds/block/{mat}/break{i+1}" for i in range(3)]
        reg_sound(f"place.{mat}", files)

    # Fall sounds
    for mat in ["stone", "dirt", "grass", "wood", "glass", "sand", "gravel", "metal", "snow", "ice", "wool"]:
        files = [f"sounds/block/{mat}/step{i+1}" for i in range(2)]
        reg_sound(f"fall.{mat}", files, "player")

    # Weather
    os.makedirs(f"{sb}/weather", exist_ok=True)
    save_wav(f"{pack_dir}/sounds/weather/rain1.wav", snd_rain())
    save_wav(f"{pack_dir}/sounds/weather/rain2.wav", snd_rain())
    save_wav(f"{pack_dir}/sounds/weather/thunder1.wav", snd_thunder())
    save_wav(f"{pack_dir}/sounds/weather/thunder2.wav", snd_thunder())
    generated_sounds += 4
    reg_sound("ambient.weather.rain", ["sounds/weather/rain1","sounds/weather/rain2"], "ambient")
    reg_sound("ambient.weather.thunder", ["sounds/weather/thunder1","sounds/weather/thunder2"], "ambient")

    with open(f"{pack_dir}/sounds.json", 'w') as f: json.dump(sound_defs, f, indent=2)
    print(f"   ✅ {generated_sounds} sound files generated")

    # ─── BLOCKS.JSON ───
    print("[5/7] Writing blocks.json...")
    blocks = {}
    block_defs = [
        ("grass_block","grass",{"up":"grass_top","down":"dirt","north":"grass_side","south":"grass_side","east":"grass_side","west":"grass_side"}),
        ("dirt","gravel","dirt"),("coarse_dirt","gravel","coarse_dirt"),
        ("stone","stone","stone"),("cobblestone","stone","cobblestone"),
        ("sand","sand","sand"),("red_sand","sand","red_sand"),("gravel","gravel","gravel"),
        ("bedrock","stone","bedrock"),
    ]
    for entry in block_defs:
        name, snd, tex = entry
        blocks[f"minecraft:{name}"] = {"sound": snd, "textures": tex}

    # Multi-face blocks
    multi = [
        ("oak_log","wood",{"up":"oak_log_top","down":"oak_log_top","north":"oak_log_side","south":"oak_log_side","east":"oak_log_side","west":"oak_log_side"}),
        ("spruce_log","wood",{"up":"spruce_log_top","down":"spruce_log_top","north":"spruce_log_side","south":"spruce_log_side","east":"spruce_log_side","west":"spruce_log_side"}),
        ("birch_log","wood",{"up":"birch_log_top","down":"birch_log_top","north":"birch_log_side","south":"birch_log_side","east":"birch_log_side","west":"birch_log_side"}),
        ("jungle_log","wood",{"up":"jungle_log_top","down":"jungle_log_top","north":"jungle_log_side","south":"jungle_log_side","east":"jungle_log_side","west":"jungle_log_side"}),
        ("acacia_log","wood",{"up":"acacia_log_top","down":"acacia_log_top","north":"acacia_log_side","south":"acacia_log_side","east":"acacia_log_side","west":"acacia_log_side"}),
        ("dark_oak_log","wood",{"up":"dark_oak_log_top","down":"dark_oak_log_top","north":"dark_oak_log_side","south":"dark_oak_log_side","east":"dark_oak_log_side","west":"dark_oak_log_side"}),
        ("mangrove_log","wood",{"up":"mangrove_log_top","down":"mangrove_log_top","north":"mangrove_log_side","south":"mangrove_log_side","east":"mangrove_log_side","west":"mangrove_log_side"}),
        ("cherry_log","wood",{"up":"cherry_log_top","down":"cherry_log_top","north":"cherry_log_side","south":"cherry_log_side","east":"cherry_log_side","west":"cherry_log_side"}),
        ("sandstone","stone",{"up":"sandstone_top","down":"sandstone_bottom","north":"sandstone_side","south":"sandstone_side","east":"sandstone_side","west":"sandstone_side"}),
        ("red_sandstone","stone",{"up":"red_sandstone_top","down":"red_sandstone_bottom","north":"red_sandstone_side","south":"red_sandstone_side","east":"red_sandstone_side","west":"red_sandstone_side"}),
        ("crafting_table","wood",{"up":"crafting_table_top","down":"oak_planks","north":"crafting_table_front","south":"crafting_table_side","east":"crafting_table_side","west":"crafting_table_side"}),
        ("furnace","stone",{"up":"furnace_top","down":"furnace_top","north":"furnace_front_off","south":"furnace_side","east":"furnace_side","west":"furnace_side"}),
        ("tnt","grass",{"up":"tnt_top","down":"tnt_bottom","north":"tnt_side","south":"tnt_side","east":"tnt_side","west":"tnt_side"}),
        ("melBlock","wood",{"up":"melon_top","down":"melon_top","north":"melon_side","south":"melon_side","east":"melon_side","west":"melon_side"}),
        ("pumpkin","wood",{"up":"pumpkin_top","down":"pumpkin_top","north":"pumpkin_face","south":"pumpkin_side","east":"pumpkin_side","west":"pumpkin_side"}),
        ("hay_block","grass",{"up":"hay_block_top","down":"hay_block_top","north":"hay_block_side","south":"hay_block_side","east":"hay_block_side","west":"hay_block_side"}),
        ("bone_block","bone",{"up":"bone_block_top","down":"bone_block_top","north":"bone_block_side","south":"bone_block_side","east":"bone_block_side","west":"bone_block_side"}),
        ("deepslate","stone",{"up":"deepslate_top","down":"deepslate_top","north":"deepslate","south":"deepslate","east":"deepslate","west":"deepslate"}),
        ("bookshelf","wood",{"up":"oak_planks","down":"oak_planks","north":"bookshelf","south":"bookshelf","east":"bookshelf","west":"bookshelf"}),
        ("farmland","gravel",{"up":"farmland","down":"dirt","north":"dirt","south":"dirt","east":"dirt","west":"dirt"}),
        ("mycelium","grass",{"up":"mycelium_top","down":"dirt","north":"mycelium_side","south":"mycelium_side","east":"mycelium_side","west":"mycelium_side"}),
    ]
    for name, snd, tex in multi:
        blocks[f"minecraft:{name}"] = {"sound": snd, "textures": tex}

    # Simple single-texture blocks
    simple = [
        ("oak_planks","wood"),("spruce_planks","wood"),("birch_planks","wood"),
        ("jungle_planks","wood"),("acacia_planks","wood"),("dark_oak_planks","wood"),
        ("mangrove_planks","wood"),("cherry_planks","wood"),("bamboo_planks","wood"),
        ("oak_leaves","grass"),("spruce_leaves","grass"),("birch_leaves","grass"),
        ("jungle_leaves","grass"),("acacia_leaves","grass"),("dark_oak_leaves","grass"),
        ("mangrove_leaves","grass"),("cherry_leaves","grass"),
        ("glass","glass"),("bricks","stone"),("stone_bricks","stone"),
        ("mossy_stone_bricks","stone"),("cracked_stone_bricks","stone"),("chiseled_stone_bricks","stone"),
        ("gold_ore","stone"),("iron_ore","stone"),("coal_ore","stone"),("diamond_ore","stone"),
        ("redstone_ore","stone"),("lapis_ore","stone"),("emerald_ore","stone"),("copper_ore","stone"),
        ("snow","snow"),("ice","glass"),("packed_ice","glass"),("blue_ice","glass"),
        ("clay","gravel"),("netherrack","nether_bricks"),("nether_bricks","nether_bricks"),
        ("red_nether_bricks","nether_bricks"),("soul_sand","soul_soil"),("soul_soil","soul_soil"),
        ("glowstone","glass"),("obsidian","stone"),("crying_obsidian","stone"),
        ("end_stone","stone"),("end_stone_bricks","stone"),("purpur_block","stone"),
        ("mud","mud"),("packed_mud","stone"),("mud_bricks","stone"),
        ("sculk","sculk"),("calcite","stone"),("tuff","stone"),("dripstone_block","stone"),
        ("copper_block","copper"),("weathered_copper","copper"),("oxidized_copper","copper"),
        ("exposed_copper","copper"),
        ("amethyst_block","stone"),("granite","stone"),("diorite","stone"),("andesite","stone"),
        ("polished_granite","stone"),("polished_diorite","stone"),("polished_andesite","stone"),
    ]
    for name, snd in simple:
        blocks[f"minecraft:{name}"] = {"sound": snd, "textures": name}

    with open(f"{pack_dir}/blocks.json", 'w') as f:
        json.dump({"format_version":"1.21.40", **blocks}, f, indent=2)

    # ─── TERRAIN_TEXTURE.JSON ───
    print("[6/7] Writing terrain_texture.json...")
    tl = [{"path": f"textures/blocks/{n}"} for n in sorted(defs.keys())]
    with open(f"{pack_dir}/textures/terrain_texture.json", 'w') as f:
        json.dump({"resource_pack_name":"vibrant_realistic","texture_name":"atlas.terrain",
                    "padding":8,"num_mip_levels":4,"texture_list":tl}, f, indent=2)

    # ─── PACKAGE ───
    print("[7/7] Packaging .mcpack...")
    if os.path.exists(OUT): os.remove(OUT)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(pack_dir):
            for fn in files:
                fp = os.path.join(root, fn)
                arcname = os.path.relpath(fp, pack_dir)
                zf.write(fp, arcname)

    size_mb = os.path.getsize(OUT) / 1024 / 1024
    print()
    print("╔══════════════════════════════════════════════════╗")
    print(f"║  ✅ Pack built successfully!                    ║")
    print(f"║  📦 File: {OUT:<39s} ║")
    print(f"║  📊 Size: {size_mb:.1f} MB{' '*(37-len(f'{size_mb:.1f} MB'))} ║")
    print(f"║  🖼️  Textures: {len(defs):<34d} ║")
    print(f"║  🔊 Sounds: {generated_sounds:<36d} ║")
    print(f"║  🎨 Shaders: Vibrant Visuals (No RT)           ║")
    print("╚══════════════════════════════════════════════════╝")

if __name__ == "__main__":
    main()
