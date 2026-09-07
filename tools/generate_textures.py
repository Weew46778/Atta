# -*- coding: utf-8 -*-
"""
Generates all block color textures + MERS + heightmaps + texture_set.json
for the Atta Ultra-Real resource pack.
"""
import os, json, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
from texengine import (N, fbm, value_noise, ridged, warp, voronoi, ramp,
                       emboss, add_speckle, draw_lines, leaf_cluster, to_u8,
                       save_rgba, save_heightmap)

ROOT = os.path.join(os.path.dirname(__file__), "..", "packs",
                    "AttaUltraReal", "textures", "blocks")
os.makedirs(ROOT, exist_ok=True)

REG = []  # relative base names generated


def finish(name, color, height=None, metal=0.0, emissive=0.0, rough=0.92,
           subsurface=0.0, ext="png", metal_map=None, emissive_map=None,
           rough_map=None, sub_map=None, height_amp=0.5, subdir="", tint=None):
    """Write color, _mers, _heightmap and <name>.texture_set.json"""
    rel = os.path.join(subdir, name) if subdir else name
    out = os.path.join(ROOT, rel)
    if subdir:
        os.makedirs(os.path.dirname(out), exist_ok=True)
    save_rgba(color, f"{out}.{ext}")

    m = (metal_map if metal_map is not None
         else np.full((N, N), metal, np.float32))
    e = (emissive_map if emissive_map is not None
         else np.full((N, N), emissive, np.float32))
    r = (rough_map if rough_map is not None
         else np.full((N, N), rough, np.float32))
    s = (sub_map if sub_map is not None
         else np.full((N, N), subsurface, np.float32))
    mers = np.stack([m, e, r, np.clip(s, 0, 1)], -1)
    Image.fromarray(to_u8(np.clip(mers, 0, 1) * 255), "RGBA").save(
        f"{out}_mers.png", optimize=True)
    has_h = height is not None
    if has_h:
        save_heightmap(height, f"{out}_heightmap.png", amp=height_amp)
    ts = {
        "format_version": "1.21.30",
        "minecraft:texture_set": {
            "color": name,
            "metalness_emissive_roughness_subsurface": f"{name}_mers",
        },
    }
    if has_h:
        ts["minecraft:texture_set"]["heightmap"] = f"{name}_heightmap"
    with open(f"{out}.texture_set.json", "w") as fj:
        json.dump(ts, fj, indent=2)
    REG.append(rel)


def retint(name, mul, subdir=""):
    p = os.path.join(ROOT, subdir, name + ".png")
    img = Image.open(p).convert("RGB")
    a = np.asarray(img, np.float32) * mul
    Image.fromarray(to_u8(a)).save(p, optimize=True)


# ============================================================ FAMILIES =====

def gen_stone(seed=1, base=((125, 126, 128), (96, 97, 100), (146, 147, 149)),
              fine=0.10):
    f = warp(fbm(4, seed, 4), 3, seed + 5, 8)
    g = fbm(12, seed + 2, 5)
    h = f * 0.65 + g * 0.35
    c = ramp(h, [(0.0, base[1]), (0.5, base[0]), (1.0, base[2])])
    grain = (fbm(48, seed + 3, 3) - 0.5) * 255 * fine
    c += grain[..., None]
    c = add_speckle(c, seed + 4, [(70, 70, 72), (168, 168, 170), (110, 108, 100)],
                    count=110, size=(0, 1), alpha=0.8)
    c = emboss(c, h, 0.55)
    return c, h


def gen_granite(seed=10):
    f1, ed, cid, cr = voronoi(14, seed)
    base = np.array([144, 114, 104], np.float32)
    c = np.ones((N, N, 3), np.float32) * base[None, None, :]
    f = fbm(8, seed + 5)
    white = (cr > 0.66) & (f > 0.45)
    dark = (cr < 0.22) & (f < 0.55)
    c[white] = (196, 188, 182)
    c[dark] = (78, 66, 62)
    c *= (0.88 + cr[..., None] * 0.24)
    g = (fbm(48, seed + 1) - 0.5) * 34
    c += g[..., None]
    c = add_speckle(c, seed + 3, [(250, 244, 238), (46, 40, 38)], count=70,
                    size=(0, 1), alpha=0.6)
    return emboss(c, f1, 0.4), 1 - f1


def gen_diorite(seed=11):
    f1, ed, cid, cr = voronoi(6, seed)
    c = np.full((N, N, 3), (206, 207, 209), np.float32)
    dk = (f1 > 0.55) | (cr < 0.3)
    c[dk] = (96, 97, 99)
    c *= (0.9 + cr[..., None] * 0.2)
    g = (fbm(40, seed) - 0.5) * 22
    c += g[..., None]
    return emboss(c, f1, 0.35), 1 - f1


def gen_andesite(seed=12):
    f = warp(fbm(6, seed, 5), 4, seed + 8, 10)
    c = ramp(f, [(0, (118, 119, 121)), (0.55, (136, 137, 138)), (1, (154, 155, 156))])
    f1, ed, cid, cr = voronoi(9, seed + 3)
    c[cr < 0.18] *= 0.86
    g = (fbm(44, seed + 5) - 0.5) * 20
    c += g[..., None]
    return emboss(c, f, 0.45), f


def gen_polished(seed, base_color):
    f = fbm(4, seed, 3)
    v = np.array(base_color, np.float32)
    c = np.ones((N, N, 3), np.float32) * v[None, None, :] * (0.94 + f[..., None] * 0.12)
    g = (fbm(30, seed + 1, 2) - 0.5) * 10
    c += g[..., None]
    return emboss(c, f, 0.25), f


def gen_cobble(seed=20, palette=((150, 150, 148), (120, 121, 120), (96, 97, 96),
                                 (140, 134, 122), (108, 104, 96)), moss=0.0):
    f1, ed, cid, cr = voronoi(5, seed)
    edge = 1 - np.clip(ed * 9, 0, 1)
    h = np.clip(1 - f1, 0, 1) * (1 - edge * 0.85)
    pal = np.array(palette, np.float32)
    idx = (cr * (len(palette) - 0.01)).astype(int)
    tile = pal[idx]
    tile *= (0.82 + np.random.default_rng(seed).random((N, N, 1)) * 0.36)
    mortar = np.clip(1 - ed * 7, 0, 1)
    c = tile * (1 - mortar[..., None] * 0.62)
    g = fbm(24, seed + 7)
    c += ((g - 0.5) * 30)[..., None]
    c = emboss(c, h, 1.0)
    if moss > 0:
        m = fbm(5, seed + 40)
        mmask = (m > (1 - moss * 0.55)).astype(np.float32)
        mgreen = ramp(fbm(14, seed + 41), [(0, (58, 92, 34)), (1, (94, 128, 46))])
        c = c * (1 - mmask[..., None] * 0.75) + mgreen * mmask[..., None] * 0.75
        h = h + mmask * 0.08
    return c, h


def gen_stonebrick(seed=22, cracked=False, moss=False, dark=1.0):
    f = fbm(8, seed)
    c = ramp(f, [(0, (118, 118, 116)), (0.5, (134, 134, 132)), (1, (148, 148, 145))]) * dark
    rng = np.random.default_rng(seed)
    cell = N // 4
    yy, xx = np.mgrid[0:N, 0:N]
    cy = (yy // cell) % 4
    ox = (cy * cell) // 2
    bord_y = (yy % cell < 1) | (yy % cell > cell - 2)
    bx = (xx + ox) % cell
    bord_x = (bx < 1) | (bx > cell - 2)
    groove = (bord_y | bord_x).astype(np.float32)
    br = np.random.default_rng(seed + 1).random((4, 4))
    cx = ((xx + ox) // cell) % 4
    tile_j = (br[cy, cx] - 0.5) * 22
    c += tile_j[..., None]
    g2 = (fbm(24, seed + 4) - 0.5) * 22
    c += g2[..., None]
    c *= (1 - groove[..., None] * 0.22)
    stain = fbm(3, seed + 8)
    c *= (0.88 + stain[..., None] * 0.2)
    h = np.full((N, N), 0.7, np.float32) - groove * 0.55 + (br[cy, cx] - 0.5) * 0.08
    if cracked:
        crmsk = np.zeros((N, N), np.float32)
        rng2 = np.random.default_rng(seed + 9)
        for _ in range(4):
            x, y = rng2.random(2) * N
            ang = rng2.random() * 6.28
            for _s in range(70):
                x += np.cos(ang) + (rng2.random() - 0.5) * 2
                y += np.sin(ang) + (rng2.random() - 0.5) * 2
                ang += (rng2.random() - 0.5) * 0.9
                crmsk[int(y) % N, int(x) % N] = 1
                crmsk[int(y + 1) % N, int(x) % N] = 0.7
        crmsk = np.array(Image.fromarray((crmsk * 255).astype(np.uint8)).filter(
            ImageFilter.MaxFilter(3)), np.float32) / 255.
        c *= (1 - crmsk[..., None] * 0.55)
        h -= crmsk * 0.4
    if moss:
        m = fbm(5, seed + 30)
        mm = (m > 0.62).astype(np.float32)
        mg = ramp(fbm(16, seed + 31), [(0, (60, 96, 36)), (1, (96, 130, 50))])
        c = c * (1 - mm[..., None] * 0.8) + mg * mm[..., None] * 0.8
    return emboss(c, h, 0.75), h


def gen_bricks(seed=24, brick_color=(168, 92, 66), mortar=(150, 144, 138),
               dark_chance=0.12):
    rng = np.random.default_rng(seed)
    bh, bw = N // 8, N // 4
    yy, xx = np.mgrid[0:N, 0:N]
    row = yy // bh
    col = ((xx + (row % 2) * bw // 2) // bw) % 8
    j = rng.random((8, 8))
    base = np.array(brick_color, np.float32)
    var = (np.random.default_rng(seed + 1).random((8, 8, 3)) - 0.5) * 46
    c = base[None, None, :] + var[row, col]
    dark = j < dark_chance
    c[dark[row, col]] = base * 0.62
    groove = ((yy % bh < 3) | ((xx + (row % 2) * bw // 2) % bw < 3)).astype(np.float32)
    mc = np.array(mortar, np.float32)[None, None, :] * (1 + (j[row, col] - .5)[..., None] * .2)
    c = c * (1 - groove[..., None]) + mc * groove[..., None]
    g = (fbm(40, seed + 2) - 0.5) * 16
    c += g[..., None]
    h = 0.75 - groove * 0.5 + (j[row, col] - 0.5) * 0.1
    return emboss(c, h, 0.9), h


def gen_gravel(seed=26):
    c = np.zeros((N, N, 3), np.float32)
    h = np.zeros((N, N), np.float32)
    pal = np.array([(136, 126, 118), (160, 154, 148), (118, 114, 116),
                    (146, 138, 126), (169, 160, 150), (104, 100, 96),
                    (156, 138, 118), (124, 108, 92)],
                   np.float32)
    for cells, amp in [(6, 1.0), (11, 0.55), (20, 0.3)]:
        f1, ed, cid, cr = voronoi(cells, seed + cells)
        idx = (cr * (len(pal) - 0.01)).astype(int)
        tile = pal[idx] * (0.68 + cr[..., None] * 0.65)
        m = np.clip(ed * 6, 0, 1)
        c = tile * amp * m[..., None] + c * (1 - amp * m[..., None])
        edge = 1 - np.clip(ed * 8, 0, 1)
        h = np.maximum(h, (1 - f1) * (1 - edge * 0.6) * amp * 1.6)
    c = emboss(c, h, 0.9)
    return c, h


def gen_sand(seed=28, base=((219, 196, 148), (238, 214, 164), (228, 206, 156),
                            (246, 226, 178))):
    rip = warp(ridged(6, seed, 3), 4, seed + 6, 12)
    f = fbm(10, seed + 1) * 0.7 + rip * 0.3
    c = ramp(f, [(0, base[0]), (0.5, base[1]), (0.75, base[2]), (1, base[3])])
    grain = (fbm(64, seed + 3, 3) - 0.5) * 28
    c += grain[..., None]
    c = add_speckle(c, seed + 5, [(188, 166, 122), (250, 238, 200)], count=90,
                    size=(0, 1), alpha=0.6)
    spark = (np.random.default_rng(seed + 4).random((N, N)) > 0.996)
    c[spark] += 55
    return emboss(c, rip, 0.65), rip * 0.6 + f * 0.4


def gen_dirt(seed=30):
    f = warp(fbm(4, seed, 5), 3, seed + 2, 9)
    g = fbm(9, seed + 5, 4)
    c = ramp(f * 0.6 + g * 0.4,
             [(0.0, (96, 72, 48)), (0.45, (118, 92, 62)), (0.8, (138, 110, 76)),
              (1.0, (108, 82, 54))])
    f1, ed, cid, cr = voronoi(8, seed + 7)
    c[ed < 0.11] *= 0.8
    c[cr < 0.12] *= 0.9 + cr[..., None][cr < 0.12] * 0  # subtle dark clods
    h = f * 0.7 + (1 - f1) * 0.3
    c = add_speckle(c, seed + 9, [(104, 100, 94), (74, 56, 38), (148, 126, 96),
                                  (56, 50, 42), (168, 150, 118)], count=150,
                    size=(0, 2), alpha=0.85)
    c = emboss(c, h, 0.65)
    return c, h


def gen_grass_top(seed=34):
    blades = ridged(10, seed, 4)
    clump = fbm(5, seed + 1, 4)
    f = blades * 0.5 + clump * 0.5
    c = ramp(f, [(0.0, (60, 108, 38)), (0.35, (84, 136, 50)), (0.7, (104, 158, 60)),
                 (0.9, (124, 174, 72)), (1.0, (140, 186, 84))])
    c = draw_lines(c, seed + 2,
                   [(72, 124, 40), (98, 150, 52), (118, 170, 64), (62, 108, 36),
                    (136, 184, 80)],
                   count=140, vertical=True, width=1, waviness=0.14, alpha=95)
    patch = fbm(3, seed + 6)
    soil = (patch > 0.78)
    soilc = ramp(fbm(8, seed + 7), [(0, (116, 90, 58)), (1, (136, 108, 74))])
    sm = soil.astype(np.float32) * 0.45
    c = c * (1 - sm[..., None]) + soilc * sm[..., None]
    c = add_speckle(c, seed + 8, [(150, 198, 92), (240, 224, 148), (118, 164, 58)],
                    count=34, size=(0, 1), alpha=0.65)
    return emboss(c, f, 0.5), f


def gen_grass_side(seed=36, top="grass", snow=False, litter=False, purple=False):
    c, h = gen_dirt(seed)
    L = (16 + ridged(24, seed + 2) * 20).astype(np.float32)
    yy = np.arange(N)[:, None].astype(np.float32)
    band = np.clip((L - yy) / 3.0, 0, 1)
    if snow:
        gcol = ramp(fbm(12, seed + 3), [(0, (225, 232, 240)), (1, (250, 252, 255))])
    elif litter:
        gcol = ramp(fbm(12, seed + 3), [(0, (112, 68, 30)), (1, (140, 96, 42))])
    elif purple:
        gcol = ramp(fbm(12, seed + 3), [(0, (106, 90, 100)), (1, (132, 116, 124))])
    elif top == "crimson":
        gcol = ramp(fbm(12, seed + 3), [(0, (150, 36, 52)), (1, (180, 64, 80))])
    elif top == "warped":
        gcol = ramp(fbm(12, seed + 3), [(0, (28, 128, 110)), (1, (58, 164, 140))])
    else:
        gcol = ramp(fbm(12, seed + 3), [(0, (74, 124, 38)), (0.6, (106, 160, 52)),
                                        (1, (134, 186, 72))])
    c2 = gcol * (band[..., None])
    c = c * (1 - band[..., None]) + c2
    if top == "grass":
        # hanging grass blades dangling from the lip over the dirt
        img = Image.fromarray(to_u8(np.concatenate(
            [c, np.full((N, N, 1), 255, np.float32)], 2).astype(np.uint8)), "RGBA")
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed + 8)
        lips = [(80, 132, 44), (102, 156, 54), (122, 176, 66), (66, 114, 38)]
        for x in range(0, N, 2):
            ln = rng.integers(6, 26)
            bend = (rng.random() - 0.5) * 6
            col = lips[int(rng.integers(0, len(lips)))]
            y0 = max(1, int(L[0, x] * 0.4))
            pts = [(x + bend * t * t, y0 + t * ln) for t in np.linspace(0, 1, 6)]
            dr.line(pts, fill=col + (235,), width=1)
        c = np.asarray(img, np.float32)[:, :, :3]
    return emboss(c, h, 0.55), h


def gen_simple_side_lip(seed, color_fn_dirt, bandcol):
    c, h = color_fn_dirt(seed)
    L = (14 + ridged(24, seed + 2) * 18).astype(np.float32)
    yy = np.arange(N)[:, None].astype(np.float32)
    band = np.clip(L - yy, 0, 1)
    c = c * (1 - band[..., None]) + np.array(bandcol, np.float32)[None, None, :] * band[..., None]
    return emboss(c, h, 0.6), h


def gen_podzol(seed=38):
    c, h = gen_dirt(seed)
    leaf = fbm(4, seed + 10)
    f1, ed, cid, cr = voronoi(7, seed + 11)
    lit = ramp(leaf * 0.5 + cr * 0.5,
               [(0, (112, 68, 30)), (0.5, (140, 96, 42)), (1, (96, 58, 26))])
    m = (leaf > 0.32).astype(np.float32)
    c = c * (1 - m[..., None] * 0.85) + lit * m[..., None] * 0.85
    c = draw_lines(c, seed + 12, [(150, 120, 60), (120, 90, 40)], count=26,
                   vertical=False, width=1, waviness=0.2, alpha=170)
    return emboss(c, h, 0.6), h


def gen_mycelium_top(seed=40):
    f = warp(fbm(6, seed, 5), 4, seed + 1, 10)
    c = ramp(f, [(0, (96, 82, 92)), (0.5, (118, 104, 114)), (1, (136, 122, 130))])
    fibers = ridged(14, seed + 2, 3)
    fm = (fibers > 0.62).astype(np.float32)
    c = c * (1 - fm[..., None] * 0.3) + np.array((146, 134, 142), np.float32) * fm[..., None]
    c = add_speckle(c, seed + 3, [(196, 192, 200), (160, 150, 160)], count=70,
                    size=(0, 1), alpha=0.8)
    return emboss(c, f, 0.5), f


def gen_coarse_dirt(seed=44):
    c, h = gen_dirt(seed)
    f1, ed, cid, cr = voronoi(7, seed + 2)
    pal = np.array([(130, 128, 122), (150, 146, 140), (110, 106, 100)], np.float32)
    idx = (cr * 2.999).astype(int)
    stonesm = (f1 < 0.75).astype(np.float32) * 0.85
    c = c * (1 - stonesm[..., None]) + pal[idx] * stonesm[..., None]
    return emboss(c, h, 0.8), h


def gen_rooted_dirt(seed=46):
    c, h = gen_dirt(seed)
    c = draw_lines(c, seed + 2, [(225, 214, 196), (200, 188, 170)], count=70,
                   vertical=True, width=1, waviness=0.18, alpha=200)
    rl = ridged(12, seed + 3, 3)
    c += ((rl - 0.5) * 18)[..., None]
    return emboss(c, h, 0.6), h


def gen_mud(seed=48, wet=1.0):
    f = fbm(6, seed, 5)
    mot = fbm(12, seed + 7, 4)
    c = ramp(f * 0.55 + mot * 0.45,
             [(0, (88, 66, 48)), (0.5, (102, 78, 58)), (1, (116, 92, 68))])
    g = (fbm(36, seed + 1) - 0.5) * 14
    c += g[..., None]
    puddle = np.clip((fbm(3, seed + 5) - 0.68) * 7, 0, 1) * wet
    c = c * (1 - puddle[..., None] * 0.32)
    rough = 0.9 - puddle * 0.55
    h = f * (1 - puddle * 0.4) + mot * 0.15
    return emboss(c, h, 0.5), h, np.clip(rough, 0, 1)


def gen_farm(seed=50, wet=0.0):
    c, h = gen_dirt(seed)
    yy = np.arange(N)[:, None].astype(np.float32)
    r = (np.sin(yy / N * 6.28 * 7) + 1) / 2
    bank = np.clip(r, 0.35, 1)
    c *= bank[..., None] * 0.5 + 0.5
    h = h * 0.5 + r * 0.5
    if wet > 0:
        c = c * (1 - 0.32 * wet)
    rough = np.full((N, N), 0.95 - wet * 0.45, np.float32)
    return emboss(c, h, 0.85), h, rough


def gen_snow(seed=54):
    f = fbm(6, seed, 4)
    dune = warp(fbm(4, seed + 3, 3), 3, seed + 4, 12)
    f = f * 0.6 + dune * 0.4
    c = ramp(f, [(0, (206, 224, 240)), (0.4, (234, 242, 250)), (0.8, (246, 250, 253)),
                 (1, (252, 253, 255))])
    c = add_speckle(c, seed + 1, [(255, 255, 255), (214, 230, 244)], count=110,
                    size=(0, 1), alpha=0.9)
    rough = 0.55 + (fbm(20, seed + 2) - 0.5) * 0.3
    return emboss(c, f, 0.75), f, np.clip(rough, 0, 1)


def gen_clay(seed=56):
    bands = (np.sin(np.arange(N)[:, None] / N * 6.28 * 5 + fbm(4, seed) * 3) + 1) / 2
    f = fbm(6, seed + 1) * 0.5 + bands * 0.5
    c = ramp(f, [(0, (146, 152, 158)), (0.5, (162, 168, 174)), (1, (176, 182, 188))])
    return emboss(c, f, 0.35), f


def gen_moss(seed=58, bright=1.0):
    f1, ed, cid, cr = voronoi(8, seed)
    bump = 1 - f1
    c = ramp(bump * 0.5 + cr * 0.5,
             [(0, (60, 96, 36)), (0.5, (92, 128, 44)), (1, (120, 152, 58))]) * bright
    edge = 1 - np.clip(ed * 10, 0, 1)
    c *= (1 - edge[..., None] * 0.3)
    g = (fbm(30, seed + 2) - 0.5) * 24
    c += g[..., None]
    return emboss(c, bump, 0.8), bump


def gen_soul_sand(seed=60):
    f = warp(fbm(5, seed, 5), 4, seed + 3, 14)
    c = ramp(f, [(0, (74, 54, 40)), (0.5, (96, 74, 54)), (1, (118, 94, 70))])
    swirl = ridged(7, seed + 4, 4)
    pits = (swirl > 0.68).astype(np.float32)
    c *= (1 - pits[..., None] * 0.45)
    c = add_speckle(c, seed + 5, [(140, 116, 88), (60, 42, 30)], count=130,
                    size=(0, 2), alpha=0.8)
    return emboss(c, f + pits * 0.4, 0.7), f - pits * 0.4


def gen_soul_soil(seed=62):
    f = fbm(6, seed, 5)
    c = ramp(f, [(0, (64, 46, 34)), (0.5, (82, 62, 46)), (1, (98, 78, 58))])
    fib = ridged(18, seed + 1, 3)
    c *= (1 - np.clip(fib - 0.6, 0, 1)[..., None] * 0.4)
    return emboss(c, f, 0.6), f


def gen_netherrack(seed=64):
    f1, ed, cid, cr = voronoi(6, seed)
    f = fbm(7, seed + 1, 5)
    c = ramp(f * 0.5 + cr * 0.5,
             [(0, (90, 40, 36)), (0.5, (116, 58, 50)), (1, (138, 74, 62))])
    vein = 1 - np.clip(ed * 12, 0, 1)
    c *= (1 - vein[..., None] * 0.4)
    c = add_speckle(c, seed + 3, [(150, 100, 88), (70, 28, 26)], count=110,
                    size=(0, 1), alpha=0.8)
    h = 1 - f1
    return emboss(c, h, 0.8), h


def gen_nylium(seed, col_lo, col_hi, dot):
    c, h = gen_netherrack(seed)
    zone = fbm(3, seed + 10)
    zc = ramp(fbm(10, seed + 11), [(0, col_lo), (1, col_hi)])
    m = np.clip((zone - 0.25) * 3, 0, 1) * 0.9
    c = c * (1 - m[..., None]) + zc * m[..., None]
    c = add_speckle(c, seed + 12, [dot, tuple(int(v * 0.6) for v in dot)],
                    count=180, size=(0, 1), alpha=0.95)
    return c, h


def gen_nether_brick(seed, hi=(58, 30, 36), mortar_rgb=(24, 12, 16)):
    c, h = gen_bricks(seed, brick_color=hi, mortar=mortar_rgb, dark_chance=0.15)
    c *= 0.8
    return emboss(c, h, 0.9), h


def gen_blackstone(seed=70):
    f = warp(fbm(5, seed, 5), 4, seed + 2, 12)
    c = ramp(f, [(0, (30, 27, 33)), (0.5, (44, 41, 48)), (1, (58, 55, 62))])
    cracks = ridged(9, seed + 4, 3)
    c *= (1 - np.clip(cracks - 0.66, 0, 1)[..., None] * 0.6)
    g = (fbm(40, seed + 6) - 0.5) * 14
    c += g[..., None]
    return emboss(c, f, 0.7), f


def gen_basalt(seed=74):
    cols = fbm(28, seed, 3)
    colrid = np.abs(np.sin(cols * 12))
    f = fbm(6, seed + 1)
    c = ramp(f * 0.4 + colrid * 0.6,
             [(0, (52, 52, 56)), (0.5, (68, 68, 72)), (1, (84, 84, 88))])
    return emboss(c, colrid, 0.75), colrid * 0.6 + f * 0.4


def gen_basalt_top(seed=76):
    f1, ed, cid, cr = voronoi(5, seed)
    c = ramp(f1 * 0.6 + cr * 0.4, [(0, (70, 70, 74)), (0.5, (82, 82, 86)),
                                   (1, (96, 96, 100))])
    edge = 1 - np.clip(ed * 8, 0, 1)
    c *= (1 - edge[..., None] * 0.5)
    return emboss(c, 1 - f1, 0.8), 1 - f1


def gen_deepslate(seed=80):
    f = ridged(3, seed, 5)
    f = warp(f, 3, seed + 2, 16)
    c = ramp(f, [(0, (44, 46, 54)), (0.5, (58, 60, 68)), (1, (74, 76, 84))])
    band = np.abs(np.sin(fbm(3, seed + 4) * 9))
    c *= (0.88 + band[..., None] * 0.24)
    g = (fbm(36, seed + 6) - 0.5) * 16
    c += g[..., None]
    return emboss(c, f, 0.8), f


def gen_tuff(seed=84):
    f = fbm(5, seed, 5)
    c = ramp(f, [(0, (88, 90, 82)), (0.5, (104, 106, 98)), (1, (120, 122, 114))])
    g = (fbm(40, seed + 1) - 0.5) * 18
    c += g[..., None]
    c = add_speckle(c, seed + 2, [(70, 72, 66), (140, 142, 134)], count=120,
                    size=(0, 1), alpha=0.7)
    return emboss(c, f, 0.5), f


def gen_calcite(seed=86):
    f = fbm(4, seed, 4)
    c = ramp(f, [(0, (224, 224, 216)), (0.5, (238, 238, 232)), (1, (248, 248, 242))])
    vein = 1 - np.abs(np.sin(warp(fbm(3, seed + 2), 3, seed + 3, 10) * 10))
    c *= (1 - np.clip(vein - 0.9, 0, 1)[..., None] * 0.12)
    return emboss(c, f, 0.3), f


def gen_dripstone(seed=88):
    strat = np.abs(np.sin(fbm(4, seed) * 14 + np.arange(N)[None, :] / N * 6.28 * 3))
    f = fbm(5, seed + 1, 5)
    c = ramp(f * 0.4 + strat * 0.6,
             [(0, (106, 82, 56)), (0.5, (130, 104, 72)), (1, (150, 124, 90))])
    return emboss(c, strat, 0.6), strat * 0.6 + f * 0.4


def gen_endstone(seed=90):
    f1, ed, cid, cr = voronoi(7, seed)
    pore = 1 - f1
    c = ramp(pore * 0.5 + cr * 0.5,
             [(0, (206, 210, 152)), (0.5, (224, 228, 172)), (1, (238, 240, 192))])
    edge = 1 - np.clip(ed * 9, 0, 1)
    c *= (1 - edge[..., None] * 0.25)
    return emboss(c, pore, 0.5), pore


def gen_bedrock(seed=92):
    f1, ed, cid, cr = voronoi(4, seed)
    f = fbm(6, seed + 1, 5)
    c = ramp(cr * 0.5 + f * 0.5, [(0, (38, 38, 40)), (0.5, (64, 64, 66)),
                                  (1, (96, 96, 98))])
    return emboss(c, 1 - f1 + f * 0.3, 1.0), 1 - f1


def gen_obsidian(seed=94):
    f = warp(fbm(3, seed, 4), 3, seed + 1, 18)
    c = ramp(f, [(0, (10, 8, 14)), (0.5, (22, 16, 32)), (1, (38, 28, 54))])
    sheen = np.clip(fbm(3, seed + 3) - 0.6, 0, 1) * 2
    c += (np.array([40, 30, 66], np.float32) * sheen[..., None])
    return c, f


def gen_sandstone(seed=96, hue=((216, 199, 152), (199, 180, 134), (232, 216, 174))):
    band = (np.sin(np.arange(N)[:, None] / N * 6.28 * 4 + fbm(4, seed) * 1.5) + 1) / 2
    f = fbm(6, seed + 1, 4)
    mot = fbm(9, seed + 5, 4)
    c = ramp((f * 0.5 + band * 0.16 + mot * 0.34),
             [(0, hue[1]), (0.5, hue[0]), (1, hue[2])])
    g = (fbm(44, seed + 3) - 0.5) * 20
    c += g[..., None]
    c = add_speckle(c, seed + 7, [(180, 160, 118), (244, 230, 190)], count=70,
                    size=(0, 1), alpha=0.5)
    return emboss(c, band * 0.3 + mot * 0.7, 0.35), band * 0.35 + f * 0.65


def gen_ice(seed=100, base=((158, 206, 246), (190, 228, 252))):
    f = fbm(5, seed, 4)
    c = ramp(f, [(0, base[0]), (0.6, base[1]), (1, (235, 250, 255))])
    cr = 1 - np.abs(np.sin(warp(ridged(4, seed + 2, 3), 3, seed + 3, 12) * 8))
    c += (np.clip(cr - 0.85, 0, 1) * 90)[..., None]
    return c, f


def gen_glowstone(seed=104):
    f1, ed, cid, cr = voronoi(7, seed)
    c = ramp(cr * 0.5 + (1 - f1) * 0.5,
             [(0, (132, 76, 38)), (0.45, (196, 124, 60)), (0.8, (244, 176, 88)),
              (1, (255, 224, 138))])
    edge = 1 - np.clip(ed * 10, 0, 1)
    c *= (1 - edge[..., None] * 0.55)
    g = (fbm(40, seed + 3) - 0.5) * 24
    c += g[..., None]
    bump = 1 - f1
    c = emboss(c, bump, 1.0)
    emissive = np.clip(cr * 1.4 - 0.08, 0, 1) * np.clip(bump * 1.3, 0, 1)
    return c, bump, np.clip(emissive, 0, 1)


def gen_warped_wart(seed=106):
    f1, ed, cid, cr = voronoi(7, seed)
    c = ramp(cr * 0.6 + (1 - f1) * 0.4, [(0, (16, 84, 74)), (0.5, (34, 122, 104)),
                                         (1, (58, 150, 124))])
    edge = 1 - np.clip(ed * 9, 0, 1)
    c *= (1 - edge[..., None] * 0.35)
    return emboss(c, 1 - f1, 0.7), 1 - f1


# ---------------------------------------------------------------- wood -----

LOG_PALETTE = {
    "oak":      ((66, 47, 27), (104, 76, 46), (130, 99, 60)),
    "spruce":   ((38, 26, 15), (60, 41, 23), (84, 60, 36)),
    "birch":    ((52, 49, 42), (214, 211, 202), (238, 235, 226)),
    "jungle":   ((84, 62, 38), (120, 92, 58), (144, 114, 74)),
    "acacia":   ((72, 66, 60), (104, 96, 88), (126, 118, 108)),
    "dark_oak": ((28, 19, 12), (50, 36, 22), (70, 52, 32)),
    "mangrove": ((58, 21, 18), (88, 33, 27), (110, 44, 35)),
    "cherry":   ((64, 52, 50), (96, 78, 76), (120, 100, 96)),
    "pale_oak": ((92, 90, 84), (150, 148, 140), (178, 176, 168)),
}

BARK_HEART = {  # (heartwood, sapwood) for log tops
    "oak":      ((120, 92, 58), (158, 128, 88)),
    "spruce":   ((78, 54, 30), (110, 80, 50)),
    "birch":    ((150, 128, 92), (186, 166, 130)),
    "jungle":   ((140, 106, 66), (172, 138, 92)),
    "acacia":   ((168, 90, 52), (196, 120, 72)),
    "dark_oak": ((70, 48, 28), (96, 68, 42)),
    "mangrove": ((122, 64, 36), (150, 88, 50)),
    "cherry":   ((170, 128, 108), (200, 160, 140)),
    "pale_oak": ((168, 162, 150), (192, 186, 174)),
}

STRIPPED_PALETTE = {
    "oak": (None, (150, 118, 74), (172, 138, 90), (192, 158, 108)),
    "spruce": (None, (104, 72, 40), (124, 90, 54), (146, 108, 66)),
    "birch": (None, (188, 166, 126), (202, 182, 144), (218, 200, 164)),
    "jungle": (None, (176, 134, 88), (194, 152, 104), (210, 170, 122)),
    "acacia": (None, (162, 88, 52), (182, 100, 60), (198, 116, 74)),
    "dark_oak": (None, (82, 56, 34), (98, 68, 42), (116, 84, 52)),
    "mangrove": (None, (128, 70, 40), (146, 82, 48), (164, 96, 58)),
    "cherry": (None, (214, 164, 154), (226, 180, 170), (238, 196, 186)),
    "pale_oak": (None, (196, 192, 182), (208, 204, 194), (220, 216, 206)),
}

PLANKS_PALETTE = {
    "oak": (178, 142, 88),
    "spruce": (126, 92, 52),
    "birch": (196, 176, 128),
    "jungle": (184, 142, 96),
    "acacia": (170, 94, 58),
    "dark_oak": (92, 62, 38),
    "mangrove": (140, 78, 46),
    "cherry": (224, 172, 160),
    "pale_oak": (208, 202, 190),
}


def gen_log_side(seed, palette):
    groove, mid, light = palette
    streak = ridged(22, seed, 3) ** 1.4
    warped = warp(streak, 5, seed + 1, 10)
    fiss = np.clip(1 - np.abs(np.sin(warped * 11)), 0, 1) ** 8
    base = fbm(6, seed + 2, 5)
    c = ramp(base * 0.45 + streak * 0.55,
             [(0, mid), (0.55, light), (1, tuple(min(255, int(v * 1.12)) for v in light))])
    c = c * (1 - fiss[..., None] * 0.65) + np.array(groove, np.float32)[None, None, :] * fiss[..., None] * 0.65
    g = (fbm(60, seed + 4) - 0.5) * 20
    c += g[..., None]
    h = base * 0.4 + (1 - fiss) * 0.6
    return emboss(c, h, 0.95), h


def gen_birch_side(seed, palette):
    dark, mid, light = palette
    base = fbm(6, seed, 4)
    c = ramp(base, [(0, mid), (0.5, light), (1, tuple(min(255, int(v * 1.04)) for v in light))])
    rng = np.random.default_rng(seed + 1)
    img = Image.fromarray(to_u8(c))
    dr = ImageDraw.Draw(img)
    for _ in range(26):
        y = rng.random() * N
        x = rng.random() * N
        w = int(rng.integers(6, 22))
        hh = int(rng.integers(2, 5))
        tone = 0.8 + rng.random() * 0.2
        col = tuple(int(v * tone) for v in dark)
        for dx, dy in ((0, 0), (-N, 0), (0, -N)):
            dr.rounded_rectangle([x + dx, y + dy, x + w + dx, y + hh + dy], 2, fill=col)
    c = np.asarray(img, np.float32)
    g = (fbm(40, seed + 3) - 0.5) * 16
    c += g[..., None]
    streak = ridged(20, seed + 5, 2)
    h = base * 0.7 + streak * 0.3
    return emboss(c, h, 0.45), h


def gen_log_top(seed, heart, sap, bark):
    yy, xx = np.mgrid[0:N, 0:N]
    cx = cy = N / 2
    r = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2) / (N / 2)
    rj = r + (fbm(8, seed) - 0.5) * 0.1
    rings = np.abs(np.sin(rj * 24)) ** 0.75
    c = ramp(rings * (1 - rj) * 0.5 + 0.5 * (1 - rj * 0.3), [(0, heart), (1, sap)])
    barkm = np.clip((rj - 0.84) * 6, 0, 1)
    c = c * (1 - barkm[..., None]) + np.array(bark, np.float32)[None, None, :] * barkm[..., None]
    core = np.clip((0.13 - rj) * 5, 0, 1)
    c *= (1 - core[..., None] * 0.3)
    c = np.asarray(Image.fromarray(to_u8(c)).filter(ImageFilter.GaussianBlur(0.4)), np.float32)
    g = (fbm(64, seed + 2) - 0.5) * 16
    c += g[..., None]
    c = add_speckle(c, seed + 3, [(60, 44, 28), (190, 168, 132)], count=60,
                    size=(0, 1), alpha=0.5)
    h = rings * (1 - barkm) * 0.4 + barkm * 0.6
    return emboss(c, h, 0.55), h


def gen_stem_side(seed, lo, mid, hi, dots):
    f1, ed, cid, cr = voronoi(6, seed)
    bump = 1 - f1
    flow = fbm(20, seed + 1, 3)
    c = ramp(bump * 0.5 + flow * 0.5, [(0, lo), (0.5, mid), (1, hi)])
    edge = 1 - np.clip(ed * 8, 0, 1)
    c *= (1 - edge[..., None] * 0.4)
    c = add_speckle(c, seed + 3, [dots, tuple(int(v * 0.7) for v in dots)],
                    count=60, size=(0, 2), alpha=0.8)
    return emboss(c, bump, 0.7), bump


def gen_planks(seed, base):
    rng = np.random.default_rng(seed)
    rows = 8
    rh = N // rows
    yy, xx = np.mgrid[0:N, 0:N]
    row = (yy // rh) % rows
    j = rng.random(rows)
    col = np.array(base, np.float32)
    c = np.ones((N, N, 3), np.float32) * col[None, None, :]
    c *= (0.93 + j[row][..., None] * 0.14)
    gx = fbm(3, seed + 3, 4)
    grain = np.abs(np.sin(gx * 30)) ** 6
    c *= (1 - grain[..., None] * 0.26)
    edge = ((yy % rh) < 1).astype(np.float32)
    c *= (1 - edge[..., None] * 0.30)
    for _ in range(3):
        kx = rng.random() * N
        ky = (rng.integers(0, rows) + 0.5) * rh
        d = np.sqrt(((yy - ky)) ** 2 * 0.35 + ((xx - kx)) ** 2)
        km = np.clip(1 - d / 6, 0, 1) ** 1.5
        c *= (1 - km[..., None] * 0.35)
        # ring around knot
        ring = np.clip(1 - np.abs(d - 8) / 3, 0, 1) * 0.12
        c *= (1 - ring[..., None])
    g = (fbm(60, seed + 5) - 0.5) * 16
    c += g[..., None]
    h = 0.6 - edge * 0.35 + (j[row] - 0.5) * 0.1
    return emboss(c, h, 0.6), h


def gen_stem_top(seed, heart, ring, glow_dots=None):
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.sqrt((yy - N / 2) ** 2 + (xx - N / 2) ** 2) / (N / 2)
    rj = r + (fbm(8, seed) - 0.5) * 0.25
    rings = np.abs(np.sin(rj * 13)) ** 0.7
    c = ramp(rings * 0.6 + rj * 0.4, [(0, heart), (1, ring)])
    if glow_dots:
        c = add_speckle(c, seed + 9, [glow_dots], count=40, size=(0, 1), alpha=0.9)
    h = rings * 0.5 + 0.2
    return emboss(c, h, 0.5), h


# ---------------------------------------------------------------- ores -----

def gen_ore(seed, base_gen, gem_palette, cells=5, chance=0.30,
            sparkle=(255, 255, 255), metal=0.6, emissive=0.0):
    c, h = base_gen(seed)
    rng = np.random.default_rng(seed + 21)
    f1, ed, cid, rr = voronoi(cells, seed + 22)
    pick = rr < chance
    img = Image.fromarray(to_u8(c))
    dr = ImageDraw.Draw(img)
    for py in range(cells):
        for px in range(cells):
            if not pick[py, px]:
                continue
            cx = int((px + 0.5) / cells * N + (rng.random() - 0.5) * 6)
            cy = int((py + 0.5) / cells * N + (rng.random() - 0.5) * 6)
            for _ in range(int(rng.integers(2, 6))):
                ox = cx + int((rng.random() - 0.5) * 10)
                oy = cy + int((rng.random() - 0.5) * 10)
                s = int(rng.integers(3, 7))
                tone = 0.85 + rng.random() * 0.3
                colg = gem_palette[int(rng.integers(0, len(gem_palette)))]
                colF = tuple(int(v * tone) for v in colg)
                pts = [(ox - s, oy), (ox, oy - s), (ox + s, oy), (ox, oy + s)]
                for dx, dy in ((0, 0), (-N, 0), (N, 0), (0, -N), (0, N)):
                    dr.polygon([(px_ + dx, py_ + dy) for px_, py_ in pts], fill=colF)
                hil = tuple(min(255, int(v * 1.25)) for v in colg)
                dr.polygon([(ox - s + 1, oy - 1), (ox, oy - s + 1),
                            (ox + 1, oy - 1), (ox + 1, oy - s + 1)], fill=hil)
                if rng.random() > 0.4:
                    dr.point([(ox - 1 + int(rng.integers(0, 3)), oy - 1)], fill=sparkle)
    c = np.asarray(img, np.float32)
    gy = (np.arange(N)[:, None] * cells // N) % cells
    gx = (np.arange(N)[None, :] * cells // N) % cells
    # map back to original pick grid order (voronoi returns same layout)
    gem_mask = np.zeros((N, N), np.float32)
    sub = N // cells
    rng2 = np.random.default_rng(seed + 23)
    ovs = fbm(4, seed + 24)
    gem_mask = (pick[np.minimum(gy, cells - 1), np.minimum(gx, cells - 1)]).astype(np.float32)
    gem_mask *= np.clip(ovs * 2 - 0.6, 0, 1)
    h = h + gem_mask * 0.12
    mmap = gem_mask * metal
    emap = gem_mask * emissive * np.clip(fbm(10, seed + 30) * 1.4 - 0.2, 0, 1)
    return emboss(c, h, 0.8), h, mmap, emap


# ---------------------------------------------------------------- plants ---

def gen_leaves(seed, palette, density=150, gap=0.2, size=(3, 7)):
    arr = leaf_cluster(seed, palette, density, True, gap, size)
    return arr, arr[:, :, 3] / 255.0


def gen_leaves_opaque(seed, palette):
    arr = leaf_cluster(seed, palette, 300, True, 0.0, size=(3, 7))
    rgb = arr[:, :, :3]
    base = ramp(fbm(8, seed + 99), [(0, palette[0]), (1, palette[-1])])
    a = arr[:, :, 3:4] / 255.0
    rgb = base * (1 - a) + rgb * a
    return rgb, (a[..., 0] * 0.5 + fbm(10, seed + 5) * 0.5)


def draw_flower(seed, petals, center, petal_n=6, petal_r=9, stem_h=None,
                leaf=True, style="round", cy=None):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    cx = N // 2
    if cy is None:
        cy = int(N * 0.30)
    if stem_h is None:
        stem_h = N - cy - 4
    gshade = [(46, 110, 36), (60, 132, 44), (72, 150, 52)]
    dr.line([cx, cy, cx, cy + stem_h], fill=gshade[1] + (255,), width=max(2, N // 64))
    if leaf:
        ly = cy + stem_h * 0.55
        dr.ellipse([cx - 14, ly - 4, cx + 4, ly + 6], fill=gshade[0] + (255,))
        dr.ellipse([cx - 4, ly + 8, cx + 14, ly + 16], fill=gshade[2] + (255,))
    if style == "ball":
        rr = petal_r + 3
        for _ in range(90):
            a = rng.random() * 6.28
            d = (rng.random() ** 0.5) * rr
            x = cx + np.cos(a) * d
            y = cy + np.sin(a) * d
            tone = 0.8 + rng.random() * 0.35
            col = petals[int(rng.integers(0, len(petals)))]
            dr.ellipse([x - 2, y - 2, x + 2, y + 2],
                       fill=tuple(int(v * tone) for v in col) + (255,))
        if center:
            dr.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=tuple(center) + (255,))
    elif style == "cup":
        dr.polygon([(cx - petal_r, cy + 6), (cx - petal_r + 2, cy - petal_r),
                    (cx + petal_r - 2, cy - petal_r), (cx + petal_r, cy + 6)],
                   fill=petals[0] + (255,))
        for sd in (-1, 1):
            dr.ellipse([cx + sd * petal_r - petal_r // 2, cy - petal_r - 3,
                        cx + sd * petal_r + petal_r // 2, cy + 3],
                       fill=petals[-1] + (255,))
    else:
        for i in range(petal_n):
            a = i / petal_n * 6.28 + (seed % 7) * 0.1
            px = cx + np.cos(a) * petal_r
            py = cy + np.sin(a) * petal_r
            tone = 0.85 + ((i * 37 + seed) % 10) / 30
            col = petals[i % len(petals)]
            dr.ellipse([px - petal_r * .75, py - petal_r * .55,
                        px + petal_r * .75, py + petal_r * .55],
                       fill=tuple(int(v * tone) for v in col) + (255,))
        if center:
            dr.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=tuple(center) + (255,))
            dr.ellipse([cx - 2, cy - 2, cx + 2, cy + 2],
                       fill=tuple(int(min(255, v * 1.15)) for v in center) + (255,))
    return np.asarray(img, np.float32)


def gen_blades(seed, height=1.0, palette=((96, 150, 46), (120, 176, 60),
                                          (70, 120, 36), (140, 196, 76)),
               count=42, width=2, base_spread=1.0):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for i in range(count):
        x0 = rng.random() * N
        ln = N * height * (0.35 + rng.random() * 0.6)
        bend = (rng.random() - 0.5) * 30 * base_spread
        col = palette[int(rng.integers(0, len(palette)))]
        tone = 0.75 + rng.random() * 0.4
        colA = tuple(int(v * tone) for v in col) + (255,)
        w = int(rng.integers(1, 4))
        curve = 1.4 + rng.random() * 1.2
        pts = []
        for t in np.linspace(0, 1, 10):
            pts.append((x0 + bend * (t ** curve) +
                        np.sin(t * 3 + i) * 2, N - 1 - t * ln))
        dr.line(pts, fill=colA, width=w)
        dr.line([(p[0] - N, p[1]) for p in pts], fill=colA, width=w)
        if rng.random() > 0.75:  # seed head
            dr.ellipse([pts[-1][0] - 2, pts[-1][1] - 4, pts[-1][0] + 2,
                        pts[-1][1]], fill=tuple(int(v * 1.15) for v in col) + (220,))
    return np.asarray(img, np.float32)


def gen_fern(seed, tall=False):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    greens = [(52, 106, 38), (66, 128, 46), (82, 148, 54), (40, 90, 30)]
    stems = 5 if not tall else 7
    for i in range(stems):
        x0 = N / 2 + (rng.random() - 0.5) * N * 0.55
        tip = N * (0.08 + rng.random() * (0.18 if tall else 0.35))
        bend = (rng.random() - 0.5) * N * 0.3
        col = greens[i % len(greens)]
        pts = []
        for t in np.linspace(0, 1, 16):
            pts.append((x0 + bend * t * t, N - 1 - t * (N - 1 - tip)))
        dr.line(pts, fill=col + (255,), width=2)
        for t in np.linspace(0.15, 0.95, 9 if tall else 7):
            x = x0 + bend * t * t
            y = N - 1 - t * (N - 1 - tip)
            ll = N * 0.09 * (1 - t) * (1.4 if tall else 1.1) + 3
            for sd in (-1, 1):
                dr.line([(x, y), (x + sd * ll, y + ll * 0.35)], fill=col + (255,),
                        width=max(1, int(3 * (1 - t)) + 1))
    return np.asarray(img, np.float32)


def gen_vine(seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    greens = [(40, 92, 30), (52, 110, 38), (64, 128, 46)]
    for i in range(9):
        x0 = rng.random() * N
        ln = N * (0.35 + rng.random() * 0.65)
        sway = (rng.random() - 0.5) * 10
        col = greens[i % 3]
        pts = [(x0 + np.sin(t * 6) * sway * t, -2 + t * ln) for t in np.linspace(0, 1, 12)]
        dr.line(pts, fill=col + (255,), width=max(2, N // 48))
        ndots = 7
        for t in np.linspace(0.15, 0.95, ndots):
            x = x0 + np.sin(t * 6) * sway * t
            y = t * ln
            rr = 3 + rng.random() * 3
            dr.ellipse([x - rr, y - rr, x + rr, y + rr],
                       fill=greens[int(rng.integers(0, 3))] + (255,))
    return np.asarray(img, np.float32)


def gen_reeds(seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for i in range(7):
        x0 = (i + 0.5) / 7 * N + (rng.random() - 0.5) * 6
        w = int(rng.integers(7, 11))
        tone = 0.85 + rng.random() * 0.25
        base = (int(150 * tone), int(200 * tone), int(90 * tone))
        dr.rectangle([x0 - w / 2, 0, x0 + w / 2, N], fill=base + (255,))
        dr.rectangle([x0 - w / 2, 0, x0 - w / 2 + 3, N],
                     fill=tuple(int(v * 0.7) for v in base) + (255,))
        seg = int(rng.integers(4, 7))
        for s in range(seg):
            y = int((s + 0.5) / seg * N)
            dr.line([x0 - w / 2, y, x0 + w / 2, y],
                    fill=tuple(int(v * 0.72) for v in base) + (255,), width=2)
    return np.asarray(img, np.float32)


def gen_waterlily(seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    cx, cy = N / 2, N / 2
    rng = np.random.default_rng(seed)
    # irregular pad silhouette
    pts = []
    npts = 30
    for i in range(npts):
        a = i / npts * 6.28
        rj = N * 0.46 * (0.86 + rng.random() * 0.14)
        pts.append((cx + np.cos(a) * rj, cy + np.sin(a) * rj))
    dr.polygon(pts, fill=(52, 110, 44, 255))
    for i in range(npts):
        a = i / npts * 6.28
        rj = N * 0.38 * (0.86 + rng.random() * 0.14)
        pts[i] = (cx + np.cos(a) * rj, cy + np.sin(a) * rj)
    dr.polygon(pts, fill=(72, 134, 54, 255))
    # notch
    dr.polygon([(cx, cy), (cx + N * .45, cy - N * .18), (cx + N * .45, cy + N * .04)],
               fill=(0, 0, 0, 0))
    # curved veins
    for a in np.linspace(-2.4, 2.6, 10):
        ax = cx + np.cos(a) * N * 0.4
        ay = cy + np.sin(a) * N * 0.4
        mx = cx + np.cos(a) * N * 0.2 - np.sin(a) * 6
        my = cy + np.sin(a) * N * 0.2 + np.cos(a) * 6
        dr.line([(cx, cy), (mx, my), (ax, ay)], fill=(46, 98, 40, 160), width=1)
    dr.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=(240, 234, 130, 255))
    return np.asarray(img, np.float32)


def gen_glass(seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    fr = max(4, N // 24)
    dr.rectangle([0, 0, N - 1, N - 1], outline=(220, 236, 242, 205), width=fr)
    f = fbm(8, seed)
    arr = np.zeros((N, N, 4), np.float32)
    arr[:, :, :3] = np.array([240, 250, 253], np.float32)
    streak = (np.abs(np.sin((np.arange(N)[:, None] + np.arange(N)[None, :]) / N * 6.28 * 2)) ** 12)
    arr[:, :, 3] = np.clip((f - 0.8) * 255 * 4, 0, 15) + streak * 12
    a = np.asarray(img, np.float32)[:, :, 3]
    arr[:, :, 3] = np.clip(arr[:, :, 3] + a, 0, 255)
    return arr


def gen_melon(seed):
    f = fbm(6, seed)
    c = ramp(f, [(0, (84, 124, 40)), (1, (110, 152, 54))])
    stripes = np.abs(np.sin(np.arange(N)[None, :] / N * 6.28 * 5 + fbm(6, seed + 1) * 3)) ** 0.6
    c *= (0.72 + stripes[..., None] * 0.38)
    return emboss(c, stripes, 0.5), stripes * 0.4 + f * 0.6


def gen_pumpkin(seed):
    f = fbm(5, seed)
    ribs = np.abs(np.sin(np.arange(N)[None, :] / N * 6.28 * 4 + fbm(5, seed + 1) * 2.2)) ** 0.5
    c = ramp(f * 0.3 + ribs * 0.7, [(0, (162, 84, 24)), (0.5, (196, 108, 32)),
                                    (1, (224, 134, 44))])
    return emboss(c, ribs, 0.7), ribs * 0.5 + f * 0.5


def gen_pumpkin_top(seed):
    c, h = gen_pumpkin(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.sqrt((yy - N / 2) ** 2 + (xx - N / 2) ** 2)
    stem = np.clip(1 - d / (N * 0.09), 0, 1)
    c = c * (1 - stem[..., None] * 0.9) + np.array((96, 108, 52), np.float32) * stem[..., None] * 0.9
    return emboss(c, h, 0.5), h


def gen_melon_top(seed):
    c, h = gen_melon(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.sqrt((yy - N / 2) ** 2 + (xx - N / 2) ** 2)
    ring = np.abs(np.sin(d / N * 20)) ** 0.6
    c *= (0.85 + ring[..., None] * 0.15)
    return emboss(c, h, 0.5), h


# ============================================================== BUILD ======

def main():
    print("Atta Ultra-Real texture generator — resolution", N)

    # ---- stone family
    finish("stone", *gen_stone(1))
    finish("stone_andesite", *gen_andesite(12))
    finish("stone_diorite", *gen_diorite(11))
    finish("stone_granite", *gen_granite(10))
    finish("stone_andesite_smooth", *gen_polished(13, (138, 139, 140)), rough=0.5, height_amp=0.25)
    finish("stone_diorite_smooth", *gen_polished(14, (198, 199, 201)), rough=0.5, height_amp=0.25)
    finish("stone_granite_smooth", *gen_polished(15, (150, 106, 94)), rough=0.5, height_amp=0.25)
    finish("cobblestone", *gen_cobble(20), rough=0.96)
    finish("cobblestone_mossy", *gen_cobble(21, moss=0.55), rough=0.98)
    finish("stonebrick", *gen_stonebrick(22), rough=0.93)
    finish("stonebrick_cracked", *gen_stonebrick(23, cracked=True), rough=0.94)
    finish("stonebrick_mossy", *gen_stonebrick(24, moss=True), rough=0.96)
    finish("brick", *gen_bricks(25), rough=0.9)
    finish("bedrock", *gen_bedrock(92), rough=0.97)
    finish("obsidian", *gen_obsidian(94), rough=0.10, height_amp=0.2)
    finish("stone_slab_top", *gen_polished(16, (150, 150, 148)), rough=0.6, height_amp=0.2)
    finish("smooth_basalt", *gen_blackstone(75), rough=0.8)

    # ---- deepslate family (subdir)
    finish("deepslate", *gen_deepslate(80), subdir="deepslate", rough=0.95)
    finish("cobbled_deepslate", *gen_cobble(81, palette=((92, 94, 100), (72, 74, 80), (60, 62, 68), (84, 86, 92), (66, 68, 74))), subdir="deepslate", rough=0.97)
    finish("deepslate_bricks", *gen_stonebrick(82, dark=0.45), subdir="deepslate", rough=0.94)
    finish("deepslate_tiles", *gen_stonebrick(83, dark=0.42), subdir="deepslate", rough=0.94)
    finish("deepslate_top", *gen_deepslate(85), subdir="deepslate", rough=0.95)
    finish("polished_deepslate", *gen_polished(86, (70, 72, 78)), subdir="deepslate", rough=0.5)
    finish("tuff", *gen_tuff(84), rough=0.94)
    finish("tuff_bricks", *gen_bricks(85, brick_color=(104, 106, 98), mortar=(72, 74, 68)), rough=0.93)
    finish("calcite", *gen_calcite(86), rough=0.7)
    finish("dripstone_block", *gen_dripstone(88), rough=0.93)
    finish("blackstone", *gen_blackstone(70), rough=0.93)
    finish("blackstone_top", *gen_blackstone(71), rough=0.93)
    finish("polished_blackstone", *gen_polished(72, (52, 48, 56)), rough=0.5)
    finish("polished_blackstone_bricks", *gen_stonebrick(73, dark=0.35), rough=0.92)
    finish("basalt_side", *gen_basalt(74), rough=0.92)
    finish("basalt_top", *gen_basalt_top(76), rough=0.92)
    print("stones done")

    # ---- ground
    finish("dirt", *gen_dirt(30), rough=0.99)
    finish("coarse_dirt", *gen_coarse_dirt(44), rough=0.99)
    finish("dirt_with_roots", *gen_rooted_dirt(46), rough=0.99, subsurface=0.05)
    finish("grass_top", *gen_grass_top(34), rough=0.85, subsurface=0.12)
    finish("grass_side", *gen_grass_side(36), ext="tga", rough=0.95, subsurface=0.06)
    c, h, r = gen_snow(54)
    finish("grass_block_snow", c, h, rough_map=r, rough=0.65)
    finish("snow", c, h, rough_map=r, rough=0.65)
    finish("grass_side_snowed", *gen_grass_side(37, snow=True), ext="tga", rough=0.97)
    finish("dirt_podzol_top", *gen_podzol(38), rough=0.99)
    finish("dirt_podzol_side", *gen_grass_side(39, litter=True), ext="tga", rough=0.99)
    finish("mycelium_top", *gen_mycelium_top(40), rough=0.95, subsurface=0.1)
    finish("mycelium_side", *gen_grass_side(41, purple=True), ext="tga", rough=0.99)
    c, h, r = gen_mud(48, 1.0)
    finish("mud", c, h, rough_map=r)
    c, h, r = gen_mud(49, 0.25)
    finish("packed_mud", c, h, rough_map=r, rough=0.95)
    finish("mud_bricks", *gen_bricks(50, brick_color=(112, 88, 62), mortar=(82, 64, 44)), rough=0.95)
    c, h, r = gen_farm(50, 0.0)
    finish("farmland_dry", c, h, rough_map=r)
    c, h, r = gen_farm(51, 1.0)
    finish("farmland_wet", c, h, rough_map=r)
    finish("grass_path_top", *gen_coarse_dirt(45), rough=0.97)
    finish("grass_path_side", *gen_grass_side(42), ext="tga", rough=0.99)
    finish("clay", *gen_clay(56), rough=0.85)
    finish("moss_block", *gen_moss(58), rough=0.99, subsurface=0.2)
    finish("pale_moss_block", *gen_moss(59, bright=1.15), rough=0.99, subsurface=0.25)
    retint("pale_moss_block", (1.15, 1.1, 0.9))
    finish("gravel", *gen_gravel(26), rough=0.99)
    finish("sand", *gen_sand(28), rough=0.99)
    finish("red_sand", *gen_sand(29, base=((188, 118, 70), (204, 130, 80), (176, 108, 62), (216, 142, 88))), rough=0.99)
    print("ground done")

    # ---- sandstone sets
    sand_hue = ((216, 199, 152), (199, 180, 134), (232, 216, 174))
    rsand_hue = ((186, 102, 58), (168, 90, 50), (200, 116, 68))
    finish("sandstone_top", *gen_sandstone(96, sand_hue), rough=0.96)
    finish("sandstone_normal", *gen_sandstone(97, sand_hue), rough=0.97)
    finish("sandstone_bottom", *gen_sandstone(98, sand_hue), rough=0.97)
    finish("sandstone_smooth", *gen_sandstone(99, sand_hue), rough=0.9)
    finish("red_sandstone_top", *gen_sandstone(100, rsand_hue), rough=0.96)
    finish("red_sandstone_normal", *gen_sandstone(101, rsand_hue), rough=0.97)
    finish("red_sandstone_bottom", *gen_sandstone(102, rsand_hue), rough=0.97)
    finish("red_sandstone_smooth", *gen_sandstone(103, rsand_hue), rough=0.9)

    # ---- nether / end
    finish("netherrack", *gen_netherrack(64), rough=0.96)
    finish("nether_brick", *gen_nether_brick(66), rough=0.92)
    finish("red_nether_brick", *gen_nether_brick(67, hi=(88, 18, 22), mortar_rgb=(40, 8, 10)), rough=0.92)
    finish("crimson_nylium_top", *gen_nylium(68, (140, 30, 46), (170, 56, 70), (250, 170, 150)), rough=0.96)
    finish("warped_nylium_top", *gen_nylium(69, (20, 110, 96), (46, 150, 130), (150, 250, 220)), rough=0.96)
    # nylium sides: netherrack base + colored top fringe
    cs, hs = gen_netherrack(70)
    L = (14 + ridged(22, 70 + 2) * 18).astype(np.float32)
    yyq = np.arange(N)[:, None].astype(np.float32)
    bandq = np.clip(L - yyq, 0, 1)
    colq = ramp(fbm(12, 8), [(0, (150, 36, 52)), (1, (180, 64, 80))])
    cs2 = cs * (1 - bandq[..., None]) + colq * bandq[..., None]
    finish("crimson_nylium_side", emboss(cs2, hs, 0.6), hs, ext="tga", rough=0.96)
    ws, wh = gen_netherrack(71)
    L = (14 + ridged(22, 73) * 18).astype(np.float32)
    bandq = np.clip(L - yyq, 0, 1)
    colq = ramp(fbm(12, 9), [(0, (28, 128, 110)), (1, (58, 164, 140))])
    ws2 = ws * (1 - bandq[..., None]) + colq * bandq[..., None]
    finish("warped_nylium_side", emboss(ws2, wh, 0.6), wh, ext="tga", rough=0.96)
    print("nylium sides rebuilt")

    finish("soul_sand", *gen_soul_sand(60), rough=0.95)
    finish("soul_soil", *gen_soul_soil(62), rough=0.98)
    finish("warped_wart_block", *gen_warped_wart(106), rough=0.9, subsurface=0.3)
    finish("end_stone", *gen_endstone(90), rough=0.9)
    finish("end_bricks", *gen_stonebrick(91, dark=0.95), rough=0.9)
    c, h, e = gen_glowstone(104)
    finish("glowstone", c, h, emissive_map=np.clip(e * 1.4, 0, 1), rough=0.55, height_amp=0.7)
    print("nether/end done")

    # ---- ores (stone + deepslate + nether)
    _ores = [("coal", 110), ("iron", 111), ("gold", 112), ("copper", 113),
             ("redstone", 114), ("lapis", 115), ("diamond", 116), ("emerald", 117)]
    for nm, sd in _ores:
        c, h, mm, em = gen_ore_gen(nm, sd)
        finish(f"{nm}_ore", c, h, metal_map=mm, emissive_map=em, rough=0.94)
    for nm, sd in [(n, s + 10) for n, s in _ores]:
        c, h, mm, em = gen_ore_gen(nm, sd, deep=True)
        finish(f"deepslate_{nm}_ore", c, h, metal_map=mm, emissive_map=em,
               subdir="deepslate", rough=0.95)
    c, h, mm, em = gen_ore_gen("gold", 128, base="nether")
    finish("nether_gold_ore", c, h, metal_map=mm, emissive_map=em, rough=0.95)
    c, h, mm, em = gen_ore_gen("quartz", 129, base="nether")
    finish("quartz_ore", c, h, metal_map=mm, emissive_map=em, rough=0.94)
    print("ores done")

    # ---- wood
    LOG_NAME = {  # mc name prefix -> palette key
        "log_oak": "oak", "log_birch": "birch", "log_spruce": "spruce",
        "log_jungle": "jungle", "log_acacia": "acacia", "log_big_oak": "dark_oak",
    }
    for i, (mcn, key) in enumerate(LOG_NAME.items()):
        pal = LOG_PALETTE[key]
        side_seed = 200 + i * 4
        if key == "birch":
            finish(mcn, *gen_birch_side(side_seed, pal), rough=0.8)
        else:
            finish(mcn, *gen_log_side(side_seed, pal), rough=0.9)
        heart, sap = BARK_HEART[key]
        finish(mcn + "_top", *gen_log_top(side_seed + 1, heart, sap, pal[0]), rough=0.85)
    # separate namings
    finish("mangrove_log_side", *gen_log_side(230, LOG_PALETTE["mangrove"]), rough=0.9)
    finish("mangrove_log_top", *gen_log_top(231, *BARK_HEART["mangrove"], LOG_PALETTE["mangrove"][0]), rough=0.85)
    finish("cherry_log_side", *gen_log_side(234, LOG_PALETTE["cherry"]), rough=0.85)
    finish("cherry_log_top", *gen_log_top(235, *BARK_HEART["cherry"], LOG_PALETTE["cherry"][0]), rough=0.8)
    finish("pale_oak_log_side", *gen_log_side(238, LOG_PALETTE["pale_oak"]), rough=0.85)
    finish("pale_oak_log_top", *gen_log_top(239, *BARK_HEART["pale_oak"], LOG_PALETTE["pale_oak"][0]), rough=0.8)

    # nether stems (huge_fungus subfolder)
    finish("crimson_log_side", *gen_stem_side(240, (66, 16, 42), (104, 32, 64), (138, 48, 86), (186, 92, 96)), subdir="huge_fungus", rough=0.92)
    finish("crimson_log_top", *gen_stem_top(241, (86, 24, 52), (130, 44, 76), (230, 130, 120)), subdir="huge_fungus", rough=0.9)
    finish("warped_stem_side", *gen_stem_side(242, (14, 56, 56), (32, 92, 86), (52, 128, 114), (46, 196, 180)), subdir="huge_fungus", rough=0.92)
    finish("warped_stem_top", *gen_stem_top(243, (22, 74, 70), (46, 116, 106), (80, 224, 204)), subdir="huge_fungus", rough=0.9)

    # stripped logs
    STRIPPED_FILES = {
        "stripped_oak_log": "oak", "stripped_birch_log": "birch",
        "stripped_spruce_log": "spruce", "stripped_jungle_log": "jungle",
        "stripped_acacia_log": "acacia", "stripped_dark_oak_log": "dark_oak",
    }
    for i, (mcn, key) in enumerate(STRIPPED_FILES.items()):
        pal = STRIPPED_PALETTE[key]
        dark = tuple(max(0, v - 55) for v in pal[1])
        finish(mcn, *gen_log_side(260 + i * 4, (dark, pal[1], pal[2])), rough=0.75)
        heart, sap = BARK_HEART[key]
        midp = tuple(int((heart[k] + sap[k]) / 2) for k in range(3))
        finish(mcn + "_top", *gen_log_top(261 + i * 4, heart, sap, midp), rough=0.75)
    finish("stripped_mangrove_log_side", *gen_log_side(288, (tuple(max(0, v - 55) for v in STRIPPED_PALETTE["mangrove"][1]), STRIPPED_PALETTE["mangrove"][1], STRIPPED_PALETTE["mangrove"][2])), rough=0.75)
    finish("stripped_mangrove_log_top", *gen_log_top(289, *BARK_HEART["mangrove"], (150, 100, 64)), rough=0.75)
    finish("stripped_cherry_log_side", *gen_log_side(292, (tuple(max(0, v - 55) for v in STRIPPED_PALETTE["cherry"][1]), STRIPPED_PALETTE["cherry"][1], STRIPPED_PALETTE["cherry"][2])), rough=0.75)
    finish("stripped_cherry_log_top", *gen_log_top(293, *BARK_HEART["cherry"], (210, 170, 155)), rough=0.75)
    finish("stripped_pale_oak_log_side", *gen_log_side(296, (tuple(max(0, v - 55) for v in STRIPPED_PALETTE["pale_oak"][1]), STRIPPED_PALETTE["pale_oak"][1], STRIPPED_PALETTE["pale_oak"][2])), rough=0.75)
    finish("stripped_pale_oak_log_top", *gen_log_top(297, *BARK_HEART["pale_oak"], (200, 198, 190)), rough=0.75)
    finish("stripped_crimson_stem_side", *gen_stem_side(298, (86, 26, 56), (124, 42, 78), (150, 60, 98), (186, 92, 96)), subdir="huge_fungus", rough=0.9)
    finish("stripped_crimson_stem_top", *gen_stem_top(299, (102, 34, 64), (140, 54, 86)), subdir="huge_fungus", rough=0.9)
    finish("stripped_warped_stem_side", *gen_stem_side(300, (24, 76, 72), (44, 110, 100), (64, 142, 126), (46, 196, 180)), subdir="huge_fungus", rough=0.9)
    finish("stripped_warped_stem_top", *gen_stem_top(301, (30, 90, 84), (56, 128, 118)), subdir="huge_fungus", rough=0.9)
    print("wood done")

    # planks
    PLANKS_FILES = {
        "planks_oak": "oak", "planks_birch": "birch", "planks_spruce": "spruce",
        "planks_jungle": "jungle", "planks_acacia": "acacia",
        "planks_big_oak": "dark_oak", "mangrove_planks": "mangrove",
        "cherry_planks": "cherry", "pale_oak_planks": "pale_oak",
    }
    for i, (mcn, key) in enumerate(PLANKS_FILES.items()):
        finish(mcn, *gen_planks(310 + i * 3, PLANKS_PALETTE[key]), rough=0.8)
    finish("crimson_planks", *gen_planks(340, (128, 52, 80)), subdir="huge_fungus", rough=0.82)
    finish("warped_planks", *gen_planks(343, (52, 110, 100)), subdir="huge_fungus", rough=0.82)
    finish("bamboo_planks", *gen_planks(346, (196, 168, 100)), rough=0.78)
    print("planks done")

    # ---- leaves (tga w/ alpha + opaque png)
    LEAVES = {
        "leaves_oak":    [(56, 96, 30), (76, 122, 40), (96, 148, 52), (46, 84, 26), (110, 162, 60)],
        "leaves_birch":  [(92, 130, 44), (110, 150, 54), (128, 170, 66), (76, 116, 38)],
        "leaves_spruce": [(34, 64, 34), (44, 80, 42), (56, 96, 50), (28, 56, 30)],
        "leaves_jungle": [(48, 108, 36), (64, 132, 44), (82, 154, 56), (38, 92, 30)],
        "leaves_acacia": [(72, 124, 40), (88, 144, 50), (104, 162, 60), (58, 106, 34)],
        "leaves_big_oak": [(52, 92, 28), (70, 116, 38), (90, 140, 48), (42, 78, 24)],
        "mangrove_leaves": [(40, 96, 40), (54, 116, 50), (70, 136, 60), (32, 82, 34)],
        "cherry_leaves": [(222, 152, 170), (236, 176, 192), (246, 198, 210), (206, 132, 152)],
        "pale_oak_leaves": [(160, 162, 146), (176, 178, 162), (192, 194, 178), (144, 146, 132)],
        "azalea_leaves": [(58, 104, 38), (72, 124, 46), (88, 142, 56), (46, 88, 32)],
        "azalea_leaves_flowers": [(64, 112, 44), (80, 132, 52), (210, 140, 190), (230, 170, 210)],
    }
    for i, (mcn, pal) in enumerate(LEAVES.items()):
        arr, hq = gen_leaves(360 + i * 7, pal, gap=0.22 if "spruce" not in mcn else 0.12)
        sub = 0.45 if "leaves" in mcn or "azalea" in mcn else 0.2
        finish(mcn, arr, hq, ext="tga", rough=0.9, subsurface=sub, height_amp=0.4)
        arr2, hq2 = gen_leaves_opaque(361 + i * 7, pal)
        finish(mcn + "_opaque", arr2, hq2, ext="png", rough=0.9, subsurface=sub)
    print("leaves done")

    # ---- small plants
    finish("tallgrass", gen_blades(400, 0.85), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("fern", gen_fern(401, False), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("double_plant_grass_top", gen_blades(402, 0.75, count=40), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("double_plant_grass_bottom", gen_blades(403, 1.0, count=46), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("double_plant_fern_top", gen_fern(404, True), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("double_plant_fern_bottom", gen_fern(405, True), None, ext="tga", rough=0.95, subsurface=0.5)
    finish("vine", gen_vine(406), None, ext="png", rough=0.95, subsurface=0.5)
    finish("reeds", gen_reeds(407), None, ext="tga", rough=0.85, subsurface=0.4)
    finish("waterlily", gen_waterlily(408), None, ext="png", rough=0.7, subsurface=0.5)
    # flowers
    FLOWERS = {
        "flower_dandelion": dict(petals=[(250, 214, 60), (252, 226, 92)], center=(236, 200, 60), style="ball"),
        "flower_rose": dict(petals=[(196, 32, 32), (220, 52, 44)], center=(90, 20, 16)),
        "flower_blue_orchid": dict(petals=[(96, 152, 220), (120, 176, 236)], center=(240, 244, 250), petal_n=6),
        "flower_allium": dict(petals=[(156, 96, 196), (176, 124, 214)], center=(140, 84, 180), style="ball"),
        "flower_houstonia": dict(petals=[(244, 244, 248), (236, 238, 248)], center=(252, 236, 120), petal_n=4, petal_r=6),
        "flower_tulip_orange": dict(petals=[(236, 108, 32), (246, 140, 52)], center=None, style="cup", petal_r=10),
        "flower_tulip_pink": dict(petals=[(240, 140, 168), (248, 170, 192)], center=None, style="cup", petal_r=10),
        "flower_tulip_red": dict(petals=[(212, 40, 36), (232, 66, 54)], center=None, style="cup", petal_r=10),
        "flower_tulip_white": dict(petals=[(248, 248, 244), (232, 236, 232)], center=None, style="cup", petal_r=10),
        "flower_oxeye_daisy": dict(petals=[(252, 252, 250), (244, 246, 240)], center=(252, 220, 76), petal_n=9, petal_r=11),
        "flower_cornflower": dict(petals=[(78, 104, 216), (100, 128, 232)], center=(40, 52, 140), petal_n=8),
        "flower_lily_of_the_valley": dict(petals=[(250, 250, 252), (240, 242, 246)], center=(244, 246, 240), petal_n=3, petal_r=5),
        "flower_wither_rose": dict(petals=[(52, 52, 58), (70, 70, 76)], center=(22, 22, 28)),
        "flower_paeonia": dict(petals=[(236, 132, 158), (246, 164, 184)], center=(252, 224, 120), petal_n=7, petal_r=11),
    }
    for i, (mcn, cfg) in enumerate(FLOWERS.items()):
        finish(mcn, draw_flower(420 + i * 3, **cfg), None, ext="png",
               rough=0.9, subsurface=0.55)
    # double flowers
    finish("double_plant_paeonia_top", draw_flower(460, petals=[(236, 132, 158), (246, 164, 184)], center=(252, 224, 120), petal_n=7, petal_r=12, cy=int(N * .35)), None, ext="png", rough=0.9, subsurface=0.55)
    finish("double_plant_paeonia_bottom", gen_blades(461, 1.0, count=26), None, ext="png", rough=0.95, subsurface=0.5)
    finish("double_plant_rose_top", draw_flower(462, petals=[(196, 32, 32), (220, 52, 44)], center=(90, 20, 16), petal_n=6, petal_r=12, cy=int(N * .35)), None, ext="png", rough=0.9, subsurface=0.55)
    finish("double_plant_rose_bottom", gen_blades(463, 1.0, count=26), None, ext="png", rough=0.95, subsurface=0.5)
    finish("double_plant_sunflower_front", draw_flower(464, petals=[(252, 200, 40), (252, 224, 80)], center=(112, 76, 32), petal_n=10, petal_r=13, cy=int(N * .4)), None, ext="png", rough=0.9, subsurface=0.55)
    finish("double_plant_sunflower_back", draw_flower(465, petals=[(110, 140, 60), (96, 126, 52)], center=(96, 70, 30), petal_n=8, petal_r=11, cy=int(N * .4)), None, ext="png", rough=0.9, subsurface=0.55)
    finish("double_plant_sunflower_top", draw_flower(466, petals=[(252, 200, 40)], center=(112, 76, 32), petal_n=6, petal_r=8, cy=int(N * .3)), None, ext="png", rough=0.9, subsurface=0.55)
    finish("double_plant_sunflower_bottom", gen_blades(467, 1.0, count=24), None, ext="png", rough=0.95, subsurface=0.5)
    finish("short_dry_grass", gen_blades(468, 0.6, palette=((196, 172, 92), (176, 152, 78), (146, 124, 60)), count=30), None, ext="png", rough=0.98)
    finish("tall_dry_grass", gen_blades(469, 0.9, palette=((196, 172, 92), (176, 152, 78), (146, 124, 60)), count=36), None, ext="png", rough=0.98)
    print("plants done")

    # ---- misc
    finish("glass", gen_glass(500), None, ext="png", rough=0.04, metal=0.0)
    finish("ice", *gen_ice(100), rough=0.08)
    finish("ice_packed", *gen_ice(102, base=((120, 162, 234), (148, 190, 246))), rough=0.12)
    finish("blue_ice", *gen_ice(103, base=((96, 148, 238), (128, 180, 250))), rough=0.10)
    finish("melon_side", *gen_melon(500), rough=0.85, subsurface=0.25)
    finish("melon_top", *gen_melon_top(501), rough=0.85, subsurface=0.25)
    finish("pumpkin_side", *gen_pumpkin(502), rough=0.85, subsurface=0.2)
    finish("pumpkin_top", *gen_pumpkin_top(503), rough=0.85, subsurface=0.2)
    print("misc done")

    with open(os.path.join(os.path.dirname(ROOT), "blocks_build_log.json"), "w") as f:
        json.dump(REG, f, indent=1)
    print("TOTAL", len(REG), "textures")


ORE_DEF = {
    "coal":     dict(gem=[(30, 30, 32), (22, 22, 24)], metal=0.25, emissive=0.0),
    "iron":     dict(gem=[(216, 196, 178), (196, 160, 130)], metal=0.55, emissive=0.0),
    "gold":     dict(gem=[(252, 208, 76), (246, 186, 50)], metal=0.7, emissive=0.0),
    "copper":   dict(gem=[(224, 112, 66), (92, 196, 168)], metal=0.65, emissive=0.0),
    "redstone": dict(gem=[(252, 44, 32), (232, 24, 20)], metal=0.2, emissive=0.55),
    "lapis":    dict(gem=[(48, 84, 196), (36, 66, 168)], metal=0.25, emissive=0.0),
    "diamond":  dict(gem=[(122, 232, 224), (90, 210, 208)], metal=0.35, emissive=0.08),
    "emerald":  dict(gem=[(64, 210, 96), (40, 172, 72)], metal=0.3, emissive=0.06),
    "quartz":   dict(gem=[(238, 230, 220), (214, 202, 190)], metal=0.15, emissive=0.0),
}


def gen_ore_gen(kind, seed, deep=False, base="stone"):
    cfg = ORE_DEF[kind]
    if base == "nether":
        bg = lambda s: gen_netherrack(s)
    elif deep:
        bg = lambda s: gen_deepslate(s)
    else:
        bg = lambda s: gen_stone(s)
    c, h, mm, em = gen_ore(seed, bg, cfg["gem"], metal=cfg["metal"],
                           emissive=cfg["emissive"],
                           sparkle=(255, 255, 255))
    return c, h, mm, em


# patch: finish needs to accept maps from gen_ore_gen via tuple — handled below


if __name__ == "__main__":
    main()
