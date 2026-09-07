# -*- coding: utf-8 -*-
"""
Atta Ultra-Real v2 — 512x REAL-photo PBR texture generator.

Dominant world blocks are built from real photographs (seamless-ified,
graded per material), procedural synthesis is kept only for fantasy
blocks (nether stems, glowstone, glass...) and for overlays
(ore gems, nylium patches, moss blending...).
"""
import os, json, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
import texengine as te
te.set_resolution(512)
from texengine import (fbm, ridged, value_noise, voronoi, ramp, emboss,
                       add_speckle, draw_lines, to_u8)

import photo_pipes as pp
from photo_pipes import (photo as PH, grade, height_from, rough_from,
                         blend_noise, crack_overlay, draw_blade_strokes,
                         save_color, save_gray, save_mers, luminance)

N = 512
PIX = N // 128
ROOT = os.path.join(os.path.dirname(__file__), "..", "packs",
                    "AttaUltraReal", "textures", "blocks")
os.makedirs(ROOT, exist_ok=True)
REG = []

# ------------------------------------------------------- photo sources -----
P = {
    "grass": "seamless-green-grass-ground-texture-top--1.jpg",
    "grass2": "seamless-green-grass-ground-texture-top--2.jpg",
    "grass3": "seamless-green-grass-ground-texture-top--3.jpg",
    "dirt": "seamless-dirt-soil-texture-top-view-phot-2.jpg",
    "dirt_ram": "seamless-dirt-soil-texture-top-view-phot-1.jpg",
    "dirt_crack": "seamless-dirt-soil-texture-top-view-phot-3.jpg",
    "sand": "seamless-beach-sand-texture-top-view-gol-1.jpg",
    "sand2": "seamless-beach-sand-texture-top-view-gol-3.jpg",
    "gravel": "seamless-gravel-pebbles-texture-top-view-2.jpg",
    "gravel2": "seamless-gravel-pebbles-texture-top-view-3.jpg",
    "stone": "seamless-grey-stone-rock-texture-tileabl-1.jpg",
    "stone2": "seamless-grey-stone-rock-texture-tileabl-2.jpg",
    "stone3": "seamless-grey-stone-rock-texture-tileabl-3.jpg",
    "cobble": "seamless-cobblestone-texture-top-view-ol-2.jpg",
    "cobble2": "seamless-cobblestone-texture-top-view-ol-3.jpg",
    "cobble3": "seamless-cobblestone-texture-top-view-ol-1.jpg",
    "sbrick": "seamless-grey-stone-bricks-wall-texture--3.jpg",
    "sbrick2": "seamless-grey-stone-bricks-wall-texture--1.jpg",
    "sbrick3": "seamless-grey-stone-bricks-wall-texture--2.jpg",
    "brick": "seamless-red-clay-bricks-wall-texture-ph-1.jpg",
    "brick2": "seamless-red-clay-bricks-wall-texture-ph-2.jpg",
    "brick3": "seamless-red-clay-bricks-wall-texture-ph-3.jpg",
    "slate": "seamless-dark-grey-slate-rock-texture-ph-1.jpg",
    "slate2": "seamless-dark-grey-slate-rock-texture-ph-2.jpg",
    "slate3": "seamless-dark-grey-slate-rock-texture-ph-3.jpg",
    "sandst": "seamless-sandstone-texture-light-beige-r-1.png",
    "sandst2": "seamless-sandstone-texture-light-beige-r-2.png",
    "sandst3": "seamless-sandstone-texture-light-beige-r-3.png",
    "dunes": "seamless-red-rock-desert-texture-photore-1.jpg",
    "basalt": "basalt-stone-columns-texture-dark-grey-p-1.png",
    "basalt2": "basalt-stone-columns-texture-dark-grey-p-2.png",
    "basalt3": "basalt-stone-columns-texture-dark-grey-p-3.png",
    "mud": "seamless-wet-mud-texture-dark-brown-eart-1.png",
    "mud2": "seamless-wet-mud-texture-dark-brown-eart-2.png",
    "mud3": "seamless-wet-mud-texture-dark-brown-eart-3.png",
    "snow": "seamless-snow-texture-sparkling-white-ph-1.jpg",
    "snow3": "seamless-snow-texture-sparkling-white-ph-3.jpg",
    "bark": "seamless-oak-tree-bark-texture-close-up--1.jpg",
    "bark2": "seamless-oak-tree-bark-texture-close-up--2.jpg",
    "bark3": "seamless-oak-tree-bark-texture-close-up--3.jpg",
    "birch": "birch-tree-bark-texture-white-with-black-1.jpg",
    "birch3": "birch-tree-bark-texture-white-with-black-3.jpg",
    "pine": "dark-spruce-pine-tree-bark-texture-close-1.jpg",
    "pine2": "dark-spruce-pine-tree-bark-texture-close-2.jpg",
    "plank": "seamless-wooden-planks-texture-top-view--3.jpg",
    "plank2": "seamless-wooden-planks-texture-top-view--2.jpg",
    "rings": "tree-trunk-cross-section-texture-wood-ri-2.jpg",
    "rings3": "tree-trunk-cross-section-texture-wood-ri-3.jpg",
    "moss": "seamless-green-moss-texture-photorealist-3.jpg",
    "mossy_stone": "seamless-green-moss-texture-photorealist-2.jpg",
    "moss2": "seamless-green-moss-texture-photorealist-1.jpg",
    "redtuff": "seamless-red-volcanic-rock-texture-dark--3.jpg",
    "volc": "seamless-red-volcanic-rock-texture-dark--2.jpg",
    "lime": "seamless-pale-yellow-limestone-rock-text-1.png",
    "lime2": "seamless-pale-yellow-limestone-rock-text-2.png",
    "lime3": "seamless-pale-yellow-limestone-rock-text-3.jpg",
    "ice": "seamless-ice-texture-frozen-lake-surface-1.png",
    "ice2": "seamless-ice-texture-frozen-lake-surface-2.png",
    "ice3": "seamless-ice-texture-frozen-lake-surface-3.png",
    "leaves": "dense-green-leaves-foliage-texture-top-v-1.jpg",
    "leaves2": "dense-green-leaves-foliage-texture-top-v-2.jpg",
    "leaves3": "dense-green-leaves-foliage-texture-top-v-3.jpg",
    "forest": "forest-floor-fallen-leaves-ground-textur-1.jpg",
    "forest2": "forest-floor-fallen-leaves-ground-textur-2.jpg",
    "forest3": "forest-floor-fallen-leaves-ground-textur-3.jpg",
    "needle": "dark-green-spruce-pine-needles-texture-c-3.jpg",
    "marble": "seamless-white-marble-texture-smooth-sto-3.jpg",
    "marble2": "seamless-white-marble-texture-smooth-sto-2.jpg",
}


def emit(name, color, height=None, rough=0.9, metal=0.0, emissive=0.0,
         sub=0.0, metal_map=None, emissive_map=None, rough_map=None,
         sub_map=None, subdir="", height_amp=0.5, hq=90):
    rel = os.path.join(subdir, name) if subdir else name
    out = os.path.join(ROOT, rel)
    if subdir:
        os.makedirs(os.path.dirname(out), exist_ok=True)
    ext = save_color(color, out, quality=hq)
    m = metal_map if metal_map is not None else np.full((N, N), metal, np.float32)
    e = (emissive_map if emissive_map is not None
         else np.full((N, N), emissive, np.float32))
    r = rough_map if rough_map is not None else np.full((N, N), rough, np.float32)
    s = sub_map if sub_map is not None else np.full((N, N), sub, np.float32)
    save_mers(m, e, r, s, out + "_mers")
    if height is not None:
        save_gray(height, out + "_heightmap", amp=height_amp)
    ts = {"format_version": "1.21.30",
          "minecraft:texture_set": {
              "color": name,
              "metalness_emissive_roughness_subsurface": name + "_mers"}}
    if height is not None:
        ts["minecraft:texture_set"]["heightmap"] = name + "_heightmap"
    with open(out + ".texture_set.json", "w") as f:
        json.dump(ts, f, indent=2)
    REG.append(rel)


# ------------------------------------------------------- composites --------
def lip_side(base, lip, seed, depth=110, span=70, blades=False,
             blade_pal=None):
    """Full-block side: base photo + wavy photo lip coming from the top."""
    yy = np.arange(N)[:, None].astype(np.float32)
    L = depth + (ridged(12, seed + 2) - 0.5) * 2 * span
    band = np.clip((L - yy) / (4 * PIX), 0, 1)
    c = base * (1 - band[..., None]) + lip * band[..., None]
    if blades:
        pal = blade_pal or [(80, 132, 44), (102, 156, 54), (122, 176, 66),
                            (66, 114, 38)]
        c = draw_blade_strokes(c, seed + 8, pal, lip_y=L[0].astype(np.float32),
                               maxlen_frac=0.10)
    return c


def farmland(base, seed, wet=0.0):
    yy = np.arange(N, dtype=np.float32)[:, None]
    r = (np.sin(yy / N * 6.28 * 8) + 1) / 2
    r = np.broadcast_to(r, (N, N)).astype(np.float32)
    bank = np.clip(r, 0.25, 1)
    c = base * (0.62 + 0.38 * bank[..., None])
    h = height_from(base, blur=6) * 0.4 + r * 0.6
    if wet:
        c = grade(c, mult=(0.82, 0.80, 0.86), sat=0.9, bright=-0.05)
    rough = np.clip(0.97 - wet * 0.5 + (r - 0.5) * 0.06, 0, 1)
    return c, h, rough.astype(np.float32)


def roots_overlay(base, seed):
    c = draw_lines(base, seed + 2, [(226, 214, 196), (202, 190, 172)],
                   count=60 * PIX, vertical=True, width=max(1, PIX // 2),
                   waviness=0.04, alpha=185)
    return c


def nylium_top(base, seed, mult, sat, dot, dot2):
    zone = fbm(3, seed + 10)
    m = np.clip((zone - 0.26) * 3.0, 0, 1) * 0.92
    tinted = grade(base, mult=mult, sat=sat, contrast=1.06)
    c = base * (1 - m[..., None]) + tinted * m[..., None]
    m2 = fbm(2, seed + 12)
    m2 = np.clip((m2 - 0.5) * 4, 0, 1) * 0.9
    deep = grade(base, mult=tuple(v * 0.55 for v in mult), sat=sat, bright=-0.05)
    c = c * (1 - m2[..., None] * 0.5) + deep * m2[..., None] * 0.5
    c = add_speckle(c, seed + 12, [dot, dot2], count=230 * PIX,
                    size=(PIX // 2, PIX + 2), alpha=0.9)
    return c, height_from(base, blur=5)


def ore_overlay(base, seed, gems, chance=0.26, cells=20, metal=0.6,
                emissive=0.0):
    """Gemstone clusters embedded in rock photo. Returns (c, h, mmap, emap)."""
    rng = np.random.default_rng(seed)
    pick = rng.random((cells, cells)) < chance
    img = Image.fromarray(to_u8(base), "RGB")
    dr = ImageDraw.Draw(img)
    sub = N / cells
    for py in range(cells):
        for px in range(cells):
            if not pick[py, px]:
                continue
            cx = (px + 0.5) * sub + (rng.random() - 0.5) * sub * 0.4
            cy = (py + 0.5) * sub + (rng.random() - 0.5) * sub * 0.4
            n_cl = int(rng.integers(2, 5))
            for _ in range(n_cl):
                ox = cx + (rng.random() - 0.5) * sub * 0.55
                oy = cy + (rng.random() - 0.5) * sub * 0.55
                s = rng.integers(6, 15)
                tone = 0.8 + rng.random() * 0.35
                colg = gems[int(rng.integers(0, len(gems)))]
                colF = tuple(int(np.clip(v * tone, 0, 255)) for v in colg)
                pts = [(ox - s, oy), (ox, oy - s), (ox + s, oy), (ox, oy + s)]
                # AO halo under the gem
                dr.ellipse([ox - s - 3, oy - s - 3, ox + s + 3, oy + s + 3],
                           fill=tuple(int(v * 0.55) for v in colF))
                for dx, dy in ((0, 0), (-N, 0), (N, 0), (0, -N), (0, N)):
                    dr.polygon([(a + dx, b + dy) for a, b in pts], fill=colF)
                hil = tuple(min(255, int(v * 1.3)) for v in colg)
                dr.polygon([(ox - s + 4, oy - 2), (ox, oy - s + 2),
                            (ox + 2, oy - 2), (ox + 2, oy - s + 4)], fill=hil)
    c = np.asarray(img, np.float32)
    # masks for material maps
    yy = (np.arange(N)[:, None] // 1 * cells // N)
    gy = np.minimum((np.arange(N)[:, None] * cells // N), cells - 1)
    gx = np.minimum((np.arange(N)[None, :] * cells // N), cells - 1)
    ov = fbm(4, seed + 24)
    gm = pick[gy, gx].astype(np.float32) * np.clip(ov * 2 - 0.55, 0, 1)
    gm_img = Image.fromarray((gm * 255).astype(np.uint8), "L").filter(
        ImageFilter.GaussianBlur(PIX * 2))
    gm = np.asarray(gm_img, np.float32) / 255.0
    h = height_from(base, blur=5) + gm * 0.12
    mmap = gm * metal
    emap = gm * emissive * np.clip(fbm(10, seed + 30) * 1.4 - 0.2, 0, 1)
    rmap = rough_from(base, 0.86, 0.12) * (1 - gm) + np.clip(gm, 0, 1) * 0.34
    return c, np.clip(h, 0, 1), mmap, emap, np.clip(rmap, 0, 1)


def gem_emit(kind, source_arrays, seed, gems, metal, emissive, chance=0.32):
    base = source_arrays
    c, h, mm, em, rm = ore_overlay(base, seed, gems, chance=chance,
                                   metal=metal, emissive=emissive)
    return dict(color=c, height=h, metal_map=mm, emissive_map=em,
                rough_map=rm)


def log_top(rings_arr, bark_arr, rim_frac=0.055):
    """Photo rings center; thin bark rim around the square edge."""
    n = N
    rim = int(n * rim_frac)
    img = Image.fromarray(to_u8(rings_arr), "RGB")
    bark = Image.fromarray(to_u8(bark_arr), "RGB").resize((n, n), Image.LANCZOS)
    out = bark.copy()
    inner = img.resize((n - 2 * rim, n - 2 * rim), Image.LANCZOS)
    out.paste(inner, (rim, rim))
    return np.asarray(out, np.float32)


def leaves_rgba(base, seed, gap=0.42):
    """Photo foliage + physics holes -> RGBA for .tga"""
    hole = fbm(7, seed)
    m = np.clip((hole - (1 - gap)) / 0.10, 0, 1)
    fine = fbm(30, seed + 4)
    m = np.clip(m * (0.75 + fine * 0.5), 0, 1)
    alpha = (1 - m * 0.97) * 255
    a4 = np.concatenate([base, alpha[..., None].astype(np.float32)], -1)
    return a4


# =========================================================== BUILD =========
def main():
    print("Atta 512 REAL photo texture build…")

    # ---------------- base photo cache
    cache = {}
    def ph(key, **kw):
        sig = (key, tuple(sorted(kw.items())))
        if sig not in cache:
            cache[sig] = PH(P[key], **kw)
        return cache[sig]

    dirt = ph("dirt")
    slate = ph("slate")
    slate2 = ph("slate2")

    # ================================================================ stone
    stone_soft = np.asarray(Image.fromarray(to_u8(ph("stone"))).filter(
        ImageFilter.GaussianBlur(PIX // 2)), np.float32)
    stone = grade(stone_soft, sat=0.55, contrast=0.9, bright=0.05,
                  gamma=1.05)
    emit("stone", stone, height_from(stone, blur=5), rough=0.86,
         rough_map=rough_from(stone, 0.86, 0.10))
    andes = grade(ph("stone"), sat=0.6, contrast=0.95, bright=0.02)
    emit("stone_andesite", andes, height_from(andes, blur=5), rough=0.87,
         rough_map=rough_from(andes, 0.87, 0.08))
    dio = grade(ph("lime2"), sat=0.35, bright=0.04, contrast=0.92)
    emit("stone_diorite", dio, height_from(dio, blur=5), rough=0.86,
         rough_map=rough_from(dio, 0.86, 0.08))
    gran = grade(ph("redtuff"), sat=0.45, bright=0.02, mult=(1.06, 0.9, 0.82))
    emit("stone_granite", gran, height_from(gran, blur=5), rough=0.86,
         rough_map=rough_from(gran, 0.86, 0.1))
    for nm, src, tint in (
        ("stone_andesite_smooth", andes, None),
        ("stone_diorite_smooth", dio, None),
        ("stone_granite_smooth", gran, None)):
        sm = np.asarray(Image.fromarray(to_u8(src)).filter(
            ImageFilter.GaussianBlur(PIX)), np.float32)
        emit(nm, sm, height_from(sm, blur=6), rough=0.52)
    smst = np.asarray(Image.fromarray(to_u8(grade(
        ph("stone2"), sat=0.7, bright=0.03))).filter(
        ImageFilter.GaussianBlur(PIX // 2)), np.float32)
    emit("stone_slab_top", smst, height_from(smst, blur=6), rough=0.58)
    print("stone family ok")

    cobble = ph("cobble")
    emit("cobblestone", cobble, height_from(cobble, blur=4), rough=0.94,
         rough_map=rough_from(cobble, 0.94, 0.12))
    mossy = ph("mossy_stone")
    emit("cobblestone_mossy", mossy, height_from(mossy, blur=4), rough=0.97,
         rough_map=rough_from(mossy, 0.96, 0.10))
    sbrick = ph("sbrick2")
    emit("stonebrick", sbrick, height_from(sbrick, blur=4), rough=0.9,
         rough_map=rough_from(sbrick, 0.9, 0.1))
    cr, chh, _ = crack_overlay(sbrick, 7, count=5)
    emit("stonebrick_cracked", cr, chh if chh is not None else
         height_from(cr, blur=4), rough=0.92)
    moss = ph("moss")
    sbm, mmask = blend_noise(sbrick, moss, 41, amt=0.45, softness=0.1)
    emit("stonebrick_mossy", sbm, height_from(sbm, blur=4) + mmask * 0.05,
         rough=0.95)
    brick = ph("brick")
    emit("brick", brick, height_from(brick, blur=4), rough=0.9,
         rough_map=rough_from(brick, 0.9, 0.12))
    bedrock = grade(slate2, sat=0.35, bright=-0.16, contrast=1.15,
                    mult=(0.9, 0.9, 0.95))
    emit("bedrock", bedrock, height_from(bedrock, blur=3), rough=0.96,
         rough_map=rough_from(bedrock, 0.96, 0.08))
    obs = grade(slate, sat=0.6, bright=-0.30, mult=(0.72, 0.62, 0.95),
                contrast=1.1)
    emit("obsidian", obs, height_from(obs, blur=8), rough=0.10,
         height_amp=0.25)
    print("cobble/brick/bedrock ok")

    # ================================================================ deepslate
    dpt = grade(ph("slate3"), sat=0.7, mult=(0.88, 0.92, 1.1), contrast=1.04,
                bright=-0.02)
    emit("deepslate", dpt, height_from(dpt, blur=4), rough=0.93,
         rough_map=rough_from(dpt, 0.93, 0.1), subdir="deepslate")
    cob_deep = grade(ph("cobble2"), sat=0.5, bright=-0.16, mult=(0.82, 0.9, 1.1))
    emit("cobbled_deepslate", cob_deep, height_from(cob_deep, blur=4),
         rough=0.95, subdir="deepslate")
    dbr = grade(ph("sbrick2"), sat=0.5, bright=-0.16, mult=(0.8, 0.88, 1.1))
    emit("deepslate_bricks", dbr, height_from(dbr, blur=4), rough=0.92,
         subdir="deepslate")
    dtl = grade(ph("sbrick3"), sat=0.5, bright=-0.14, mult=(0.8, 0.88, 1.08))
    emit("deepslate_tiles", dtl, height_from(dtl, blur=4), rough=0.92,
         subdir="deepslate")
    dtop = grade(ph("slate3", dx=0.12, dy=-0.1), sat=0.65,
                 mult=(0.86, 0.9, 1.08), contrast=1.05, bright=-0.03)
    emit("deepslate_top", dtop, height_from(dtop, blur=4), rough=0.93,
         subdir="deepslate")
    pdeep = np.asarray(Image.fromarray(to_u8(dpt)).filter(
        ImageFilter.GaussianBlur(PIX)), np.float32)
    emit("polished_deepslate", pdeep, height_from(pdeep, blur=6), rough=0.5,
         subdir="deepslate")
    tuff = grade(ph("stone"), sat=0.45, bright=0.03, mult=(0.96, 1.02, 0.9),
                 contrast=0.95)
    emit("tuff", tuff, height_from(tuff, blur=4), rough=0.92)
    tb = grade(ph("sbrick3"), sat=0.4, bright=0.02, mult=(0.94, 1.0, 0.9))
    emit("tuff_bricks", tb, height_from(tb, blur=4), rough=0.9)
    calc = grade(ph("lime2"), sat=0.25, bright=0.10, contrast=0.9)
    emit("calcite", calc, height_from(calc, blur=5), rough=0.72)
    drip = grade(ph("sandst2"), sat=0.85, mult=(1.12, 0.92, 0.72),
                 bright=-0.06, contrast=1.05)
    emit("dripstone_block", drip, height_from(drip, blur=4), rough=0.92)
    bls = ph("volc")
    emit("blackstone", bls, height_from(bls, blur=4), rough=0.92,
         rough_map=rough_from(bls, 0.92, 0.1))
    bt = grade(slate, sat=0.55, bright=-0.18, mult=(0.9, 0.92, 1.05))
    emit("blackstone_top", bt, height_from(bt, blur=4), rough=0.92)
    pbl = np.asarray(Image.fromarray(to_u8(bt)).filter(
        ImageFilter.GaussianBlur(PIX)), np.float32)
    emit("polished_blackstone", pbl, height_from(pbl, blur=6), rough=0.5)
    bbr = grade(ph("sbrick2"), sat=0.35, bright=-0.24, mult=(0.85, 0.85, 1.0))
    emit("polished_blackstone_bricks", bbr, height_from(bbr, blur=4),
         rough=0.88)
    emit("basalt_side", ph("basalt"), height_from(ph("basalt"), blur=4),
         rough=0.92, rough_map=rough_from(ph("basalt"), 0.92, 0.12))
    b2 = grade(ph("stone"), sat=0.4, bright=-0.08, mult=(0.9, 0.92, 1.0),
               contrast=1.02)
    emit("basalt_top", b2, height_from(b2, blur=4), rough=0.92)
    sbt = np.asarray(Image.fromarray(to_u8(grade(
        ph("slate3"), sat=0.5, bright=-0.04, mult=(0.95, 0.97, 1.05)))).filter(
        ImageFilter.GaussianBlur(PIX)), np.float32)
    emit("smooth_basalt", sbt, height_from(sbt, blur=5), rough=0.85)
    print("deepslate family ok")

    # ================================================================ ground
    emit("dirt", dirt, height_from(dirt, blur=4), rough=0.98,
         rough_map=rough_from(dirt, 0.97, 0.06))
    coarse = grade(ph("dirt_ram"), sat=0.8, mult=(1.02, 0.95, 0.88),
                   contrast=1.02)
    emit("coarse_dirt", coarse, height_from(coarse, blur=4), rough=0.98)
    emit("dirt_with_roots", roots_overlay(dirt.copy(), 46),
         height_from(dirt, blur=4), rough=0.98, sub=0.05)
    gtop = grade(ph("grass"), sat=1.12, mult=(0.94, 1.02, 0.86))
    emit("grass_top", gtop, height_from(gtop, blur=4), rough=0.9, sub=0.12,
         rough_map=rough_from(gtop, 0.9, 0.06))
    emit("grass_side", lip_side(dirt, gtop, 36, blades=True),
         height_from(dirt, blur=4), rough=0.96, sub=0.06)
    snowph = ph("snow")
    snowg = grade(ph("snow3"), sat=0.8, bright=0.02)
    emit("snow", snowg, height_from(snowg, blur=5), rough=0.62,
         rough_map=rough_from(snowg, 0.62, 0.1))
    emit("grass_block_snow", snowg, height_from(snowg, blur=5), rough=0.62)
    emit("grass_side_snowed", lip_side(dirt, snowg, 37),
         height_from(dirt, blur=4), rough=0.97)
    podz = grade(ph("forest"), sat=1.0, mult=(1.04, 0.95, 0.85), contrast=1.03)
    emit("dirt_podzol_top", podz, height_from(podz, blur=4), rough=0.98)
    emit("dirt_podzol_side", lip_side(dirt, podz, 39),
         height_from(dirt, blur=4), rough=0.98)
    myc = grade(moss, sat=0.35, mult=(0.9, 0.82, 0.98), bright=0.02)
    emit("mycelium_top", myc, height_from(myc, blur=5), rough=0.95, sub=0.12)
    emit("mycelium_side", lip_side(dirt, myc, 41), height_from(dirt, blur=4),
         rough=0.98)
    mud = ph("mud")
    emit("mud", mud, height_from(mud, blur=5), rough=0.78,
         rough_map=rough_from(mud, 0.75, 0.2))
    pmud = ph("mud2")
    emit("packed_mud", pmud, height_from(pmud, blur=4), rough=0.95)
    mbrick = grade(ph("brick2"), sat=0.45, mult=(0.85, 0.75, 0.6) , bright=-0.03)
    emit("mud_bricks", mbrick, height_from(mbrick, blur=4), rough=0.94)
    cfd, hfd, rfd = farmland(dirt, 50, wet=0.0)
    emit("farmland_dry", cfd, hfd, rough_map=rfd)
    cfw, hfw, rfw = farmland(grade(dirt, bright=-0.06, mult=(0.9, 0.86, 0.88)),
                             51, wet=1.0)
    emit("farmland_wet", cfw, hfw, rough_map=rfw)
    path = grade(ph("dirt_ram"), bright=0.05, sat=0.75, mult=(1.06, 0.98, 0.85))
    emit("grass_path_top", path, height_from(path, blur=5), rough=0.97)
    emit("grass_path_side", lip_side(dirt, path, 42, depth=60, span=30),
         height_from(dirt, blur=4), rough=0.98)
    clay_src = np.asarray(Image.fromarray(to_u8(ph("sandst2"))).filter(
        ImageFilter.GaussianBlur(PIX * 2)), np.float32)
    clay = grade(clay_src, sat=0.25, mult=(0.82, 0.88, 0.98), bright=0.03,
                 contrast=0.88)
    emit("clay", clay, height_from(clay, blur=4), rough=0.85)
    emit("moss_block", moss, height_from(moss, blur=4), rough=0.99, sub=0.22)
    pmoss = grade(moss, sat=0.45, bright=0.16, mult=(1.02, 1.0, 0.9))
    emit("pale_moss_block", pmoss, height_from(pmoss, blur=4), rough=0.99,
         sub=0.25)
    gravel = ph("gravel")
    emit("gravel", gravel, height_from(gravel, blur=3), rough=0.99,
         rough_map=rough_from(gravel, 0.99, 0.05), height_amp=0.65)
    sand = ph("sand")
    emit("sand", sand, height_from(sand, blur=4), rough=0.96,
         rough_map=rough_from(sand, 0.96, 0.05))
    rsand = grade(ph("sand2"), sat=0.9, mult=(1.32, 0.78, 0.55), warmth=0.05,
                  bright=-0.02)
    emit("red_sand", rsand, height_from(rsand, blur=4), rough=0.96)
    print("ground ok")

    # ================================================================ sandstone
    # banded sedimentary photo reads exactly like real sandstone
    sed = grade(ph("dirt_ram"), sat=0.88, bright=0.05, mult=(1.12, 1.0, 0.78),
                contrast=0.97)
    st2 = grade(ph("sandst2"), sat=0.9, mult=(1.08, 0.99, 0.8), bright=0.04,
                contrast=0.94)
    emit("sandstone_top", sed, height_from(sed, blur=5), rough=0.95)
    emit("sandstone_normal", sed, height_from(sed, blur=5), rough=0.96)
    emit("sandstone_bottom", st2, height_from(st2, blur=5), rough=0.96)
    ssm = np.asarray(Image.fromarray(to_u8(sed)).filter(
        ImageFilter.GaussianBlur(PIX)), np.float32)
    emit("sandstone_smooth", ssm, height_from(ssm, blur=6), rough=0.9)
    rs_mult = (1.35, 0.72, 0.5)
    rsed = grade(sed, sat=0.45, mult=rs_mult, bright=0.02)
    rst2 = grade(st2, sat=0.45, mult=rs_mult, bright=0.02)
    emit("red_sandstone_top", rsed, height_from(rsed, blur=5), rough=0.95)
    emit("red_sandstone_normal", rsed, height_from(rsed, blur=5), rough=0.96)
    emit("red_sandstone_bottom", rst2, height_from(rst2, blur=5), rough=0.96)
    rssm = np.asarray(Image.fromarray(to_u8(rsed)).filter(
        ImageFilter.GaussianBlur(PIX)), np.float32)
    emit("red_sandstone_smooth", rssm, height_from(rssm, blur=6), rough=0.9)
    print("sandstone ok")

    # ================================================================ nether/end
    nr = grade(ph("redtuff"), sat=1.05, mult=(1.28, 0.5, 0.4), contrast=1.1,
               bright=-0.05)
    emit("netherrack", nr, height_from(nr, blur=4), rough=0.95,
         rough_map=rough_from(nr, 0.95, 0.08))
    nbrick = grade(ph("brick3"), sat=0.55, bright=-0.12, mult=(1.0, 0.58, 0.62),
                   contrast=1.06)
    emit("nether_brick", nbrick, height_from(nbrick, blur=4), rough=0.92)
    rnbrick = grade(brick, sat=0.7, bright=-0.06, mult=(1.05, 0.42, 0.38),
                    contrast=1.04)
    emit("red_nether_brick", rnbrick, height_from(rnbrick, blur=4), rough=0.92)
    # nylium tops = organic moss layer growing over netherrack
    crimson_moss = grade(moss, sat=0.85, bright=-0.03, mult=(1.5, 0.38, 0.5),
                         contrast=1.05)
    warped_moss = grade(moss, sat=1.05, bright=-0.06, mult=(0.28, 1.12, 1.0),
                        contrast=1.04)
    cc, mask_c = blend_noise(nr, crimson_moss, 168, amt=0.58, softness=0.12)
    cc = add_speckle(cc, 170, [(250, 170, 150), (120, 30, 40)],
                     count=200 * PIX, size=(PIX // 2, PIX + 1), alpha=0.85)
    emit("crimson_nylium_top", cc, height_from(nr, blur=4) + mask_c * 0.08,
         rough=0.96)
    wc, mask_w = blend_noise(nr, warped_moss, 169, amt=0.58, softness=0.12)
    wc = add_speckle(wc, 171, [(150, 250, 220), (10, 90, 80)],
                     count=200 * PIX, size=(PIX // 2, PIX + 1), alpha=0.85)
    emit("warped_nylium_top", wc, height_from(nr, blur=4) + mask_w * 0.08,
         rough=0.96)
    emit("crimson_nylium_side", lip_side(nr, cc, 70, depth=80, span=50),
         height_from(nr, blur=4), rough=0.96)
    emit("warped_nylium_side", lip_side(nr, wc, 71, depth=80, span=50),
         height_from(nr, blur=4), rough=0.96)
    soul = grade(ph("mud2"), sat=0.5, bright=-0.1, mult=(0.95, 0.85, 0.72)
                 , contrast=1.05)
    emit("soul_sand", soul, height_from(soul, blur=4), rough=0.95)
    soul2 = grade(ph("mud3"), sat=0.45, bright=-0.14, mult=(0.9, 0.82, 0.7))
    emit("soul_soil", soul2, height_from(soul2, blur=4), rough=0.97)
    ww = grade(ph("needle"), sat=0.9, mult=(0.28, 1.0, 0.9), bright=-0.06,
               contrast=1.02)
    emit("warped_wart_block", ww, height_from(ww, blur=4), rough=0.9, sub=0.3)
    est = grade(ph("lime"), sat=0.9, mult=(1.05, 1.02, 0.72), bright=0.04)
    emit("end_stone", est, height_from(est, blur=4), rough=0.9)
    ebr = grade(ph("sbrick"), sat=0.7, bright=0.06, mult=(1.08, 1.05, 0.78))
    emit("end_bricks", ebr, height_from(ebr, blur=4), rough=0.9)

    # glowstone stays procedural (its emissive structure is the point)
    f1, ed, cid, cr = voronoi(24, 104)
    gc = ramp(cr * 0.5 + (1 - f1) * 0.5,
              [(0, (132, 76, 38)), (0.45, (196, 124, 60)), (0.8, (244, 176, 88)),
               (1, (255, 224, 138))])
    edge = 1 - np.clip(ed * 10 * PIX, 0, 1)
    gc *= (1 - edge[..., None] * 0.55)
    gi = (fbm(160, 107) - 0.5) * 24
    gc += gi[..., None]
    bump = 1 - f1
    gc = emboss(gc, bump, 1.0 * PIX / 2)
    gemis = np.clip(cr * 1.4 - 0.08, 0, 1) * np.clip(bump * 1.3, 0, 1)
    emit("glowstone", gc, bump, emissive_map=np.clip(gemis * 1.4, 0, 1),
         rough=0.5, height_amp=0.7)
    print("nether/end ok")

    # ================================================================ ores
    ORE_SPECS = [
        ("coal",     [(30, 30, 32), (22, 22, 24)],     0.25, 0.0),
        ("iron",     [(216, 196, 178), (196, 160, 130)], 0.55, 0.0),
        ("gold",     [(252, 208, 76), (246, 186, 50)], 0.7, 0.0),
        ("copper",   [(224, 112, 66), (92, 196, 168)], 0.65, 0.0),
        ("redstone", [(252, 44, 32), (232, 24, 20)],   0.2, 0.55),
        ("lapis",    [(48, 84, 196), (36, 66, 168)],   0.25, 0.0),
        ("diamond",  [(122, 232, 224), (90, 210, 208)], 0.35, 0.08),
        ("emerald",  [(64, 210, 96), (40, 172, 72)],   0.3, 0.06),
    ]
    for i, (nm, gems, mt, emi) in enumerate(ORE_SPECS):
        o = gem_emit(nm, stone, 110 + i, gems, mt, emi)
        emit(f"{nm}_ore", **o)
        d = gem_emit(nm, dpt, 120 + i, gems, mt, emi)
        emit(f"deepslate_{nm}_ore", subdir="deepslate", **d)
    g2 = gem_emit("gold", nr, 128, [(252, 208, 76), (246, 186, 50)], 0.7, 0.0,
                  chance=0.38)
    emit("nether_gold_ore", **g2)
    q2 = gem_emit("quartz", nr, 129, [(238, 230, 220), (214, 202, 190)], 0.15,
                  0.0, chance=0.36)
    emit("quartz_ore", **q2)
    print("ores ok")

    # ================================================================ wood
    barkOak = ph("bark")
    bark2 = ph("bark2")
    bark3 = ph("bark3")
    birch = ph("birch")
    pine = ph("pine")
    pine2 = ph("pine2")
    rings = ph("rings", frac=0.62)
    plank = ph("plank")

    # bark -> species
    def barkvar(src, **kw):
        return grade(src, **kw)

    LOG_SIDES = [
        ("log_oak", grade(barkOak, sat=0.95, mult=(1.05, 0.9, 0.72),
                          bright=-0.02)),
        ("log_spruce", grade(pine, sat=0.75, bright=-0.06, mult=(0.85, 0.75, 0.65),
                             contrast=1.06)),
        ("log_birch", grade(birch, sat=0.75, bright=0.03)),
        ("log_jungle", grade(bark3, sat=0.9, mult=(1.02, 0.9, 0.75))),
        ("log_acacia", grade(bark2, sat=0.35, bright=0.0, contrast=1.02)),
        ("log_big_oak", grade(pine2, sat=0.5, bright=-0.12, mult=(0.72, 0.62, 0.5))),
        ("mangrove_log_side", grade(barkOak, sat=0.95, bright=-0.04,
                                    mult=(1.15, 0.5, 0.42))),
        ("cherry_log_side", grade(bark2, sat=0.55, bright=0.06,
                                  mult=(1.0, 0.8, 0.82))),
        ("pale_oak_log_side", grade(barkOak, sat=0.22, bright=0.10,
                                    contrast=0.9)),
    ]
    # horizontal lenticels for cherry bark
    def lenticels(arr, seed=5):
        img = Image.fromarray(to_u8(arr), "RGB")
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        for _ in range(14):
            y = rng.random() * N
            x = rng.random() * N
            w = rng.integers(int(N * 0.04), int(N * 0.13))
            h_ = max(PIX, int(rng.integers(PIX, 2 * PIX)))
            col = (88, 58, 52)
            for ddx in (0, -N, N):
                dr.rounded_rectangle([x + ddx - w / 2, y, x + ddx + w / 2, y + h_],
                                     h_, fill=col)
        return np.asarray(img, np.float32)

    LOG_SIDES[7] = ("cherry_log_side", lenticels(LOG_SIDES[7][1]))
    for nm, arr in LOG_SIDES:
        emit(nm, arr, height_from(arr, blur=3), rough=0.88,
             rough_map=rough_from(arr, 0.88, 0.08), height_amp=0.6)
    print("log sides ok")

    def ringvar(**kw):
        inner = grade(rings, **kw)
        return inner

    RING_GRADES = {
        "log_oak_top":      dict(sat=0.85, mult=(1.0, 0.88, 0.7)),
        "log_spruce_top":   dict(sat=0.75, bright=-0.05, mult=(0.85, 0.72, 0.58)),
        "log_birch_top":    dict(sat=0.7, bright=0.06, mult=(1.0, 0.94, 0.78)),
        "log_jungle_top":   dict(sat=0.85, bright=0.02, mult=(1.02, 0.9, 0.72)),
        "log_acacia_top":   dict(sat=1.0, mult=(1.15, 0.72, 0.5)),
        "log_big_oak_top":  dict(sat=0.7, bright=-0.1, mult=(0.75, 0.62, 0.48)),
        "mangrove_log_top": dict(sat=0.95, bright=-0.03, mult=(1.18, 0.55, 0.4)),
        "cherry_log_top":   dict(sat=0.8, bright=0.04, mult=(1.05, 0.85, 0.82)),
        "pale_oak_log_top": dict(sat=0.25, bright=0.1),
    }
    bark_by_top = {
        "log_oak_top": LOG_SIDES[0][1], "log_spruce_top": LOG_SIDES[1][1],
        "log_birch_top": LOG_SIDES[2][1], "log_jungle_top": LOG_SIDES[3][1],
        "log_acacia_top": LOG_SIDES[4][1], "log_big_oak_top": LOG_SIDES[5][1],
        "mangrove_log_top": LOG_SIDES[6][1], "cherry_log_top": LOG_SIDES[7][1],
        "pale_oak_log_top": LOG_SIDES[8][1],
    }
    for nm, kw in RING_GRADES.items():
        lt = log_top(ringvar(**kw), bark_by_top[nm])
        emit(nm, lt, height_from(lt, blur=4), rough=0.85, height_amp=0.4)
    print("log tops ok")

    # stripped = smooth inner wood: real boards photo, blurred past the joints
    plank2_smooth = np.asarray(Image.fromarray(to_u8(plank)).filter(
        ImageFilter.GaussianBlur(PIX * 3)), np.float32)

    def stripped(key_kw):
        return grade(plank2_smooth, **key_kw)

    STRIPPED = [
        ("stripped_oak_log",        dict(sat=0.85, mult=(1.02, 0.9, 0.72))),
        ("stripped_spruce_log",     dict(sat=0.7, bright=-0.06, mult=(0.85, 0.72, 0.58))),
        ("stripped_birch_log",      dict(sat=0.7, bright=0.08, mult=(1.02, 0.96, 0.8))),
        ("stripped_jungle_log",     dict(sat=0.85, bright=0.02, mult=(1.02, 0.9, 0.72))),
        ("stripped_acacia_log",     dict(sat=1.0, mult=(1.18, 0.72, 0.5))),
        ("stripped_dark_oak_log",   dict(sat=0.7, bright=-0.1, mult=(0.75, 0.62, 0.48))),
    ]
    for nm, kw in STRIPPED:
        arr = stripped(kw)
        emit(nm, arr, height_from(arr, blur=6), rough=0.78)
    STRIP_TOP = {
        "stripped_oak_log_top":      dict(sat=0.85, bright=0.04, mult=(1.02, 0.9, 0.72)),
        "stripped_spruce_log_top":   dict(sat=0.7, bright=0.0, mult=(0.85, 0.72, 0.58)),
        "stripped_birch_log_top":    dict(sat=0.7, bright=0.1, mult=(1.02, 0.96, 0.8)),
        "stripped_jungle_log_top":   dict(sat=0.85, bright=0.05, mult=(1.02, 0.9, 0.72)),
        "stripped_acacia_log_top":   dict(sat=1.0, bright=0.04, mult=(1.18, 0.72, 0.5)),
        "stripped_dark_oak_log_top": dict(sat=0.7, bright=-0.04, mult=(0.75, 0.62, 0.48)),
    }
    for nm, kw in STRIP_TOP.items():
        lt = log_top(ringvar(**kw), stripped(kw))
        emit(nm, lt, height_from(lt, blur=4), rough=0.8, height_amp=0.4)
    # mangrove / cherry / pale-oak stripped (separate names)
    for nm_side, nm_top, kw in (
        ("stripped_mangrove_log_side", "stripped_mangrove_log_top",
         dict(sat=0.65, bright=-0.02, mult=(1.05, 0.68, 0.55))),
        ("stripped_cherry_log_side", "stripped_cherry_log_top",
         dict(sat=0.8, bright=0.08, mult=(1.06, 0.86, 0.84))),
        ("stripped_pale_oak_log_side", "stripped_pale_oak_log_top",
         dict(sat=0.25, bright=0.12))):
        arr = stripped(kw)
        emit(nm_side, arr, height_from(arr, blur=6), rough=0.78)
        lt = log_top(ringvar(**kw), arr)
        emit(nm_top, lt, height_from(lt, blur=4), rough=0.8, height_amp=0.4)
    print("stripped ok")

    # nether stems stay procedural (alien patterns)
    def stem_side(seed, lo, mid, hi, dots):
        f1_, ed_, cid_, cr_ = voronoi(20, seed)
        bump = 1 - f1_
        flow = fbm(60, seed + 1, 3)
        c = ramp(bump * 0.5 + flow * 0.5, [(0, lo), (0.5, mid), (1, hi)])
        edg = 1 - np.clip(ed_ * 10 * PIX, 0, 1)
        c *= (1 - edg[..., None] * 0.4)
        c = add_speckle(c, seed + 3, [dots, tuple(int(v * 0.7) for v in dots)],
                        count=60 * PIX, size=(PIX // 2, 2 * PIX), alpha=0.8)
        return emboss(c, bump, 0.7), bump

    def stem_top(seed, heart, ring, glow=None):
        yy, xx = np.mgrid[0:N, 0:N]
        r = np.sqrt((yy - N / 2) ** 2 + (xx - N / 2) ** 2) / (N / 2)
        rj = r + (fbm(20, seed) - 0.5) * 0.25
        rings_ = np.abs(np.sin(rj * 52)) ** 0.7
        c = ramp(rings_ * 0.6 + rj * 0.4, [(0, heart), (1, ring)])
        if glow:
            c = add_speckle(c, seed + 9, [glow], count=40 * PIX,
                            size=(PIX // 2, PIX), alpha=0.9)
        h = rings_ * 0.5 + 0.2
        return emboss(c, h, 0.5), h

    emit("crimson_log_side", *stem_side(240, (66, 16, 42), (104, 32, 64), (138, 48, 86), (186, 92, 96)), subdir="huge_fungus", rough=0.92)
    emit("crimson_log_top", *stem_top(241, (86, 24, 52), (130, 44, 76), (230, 130, 120)), subdir="huge_fungus", rough=0.9)
    emit("warped_stem_side", *stem_side(242, (14, 56, 56), (32, 92, 86), (52, 128, 114), (46, 196, 180)), subdir="huge_fungus", rough=0.92)
    emit("warped_stem_top", *stem_top(243, (22, 74, 70), (46, 116, 106), (80, 224, 204)), subdir="huge_fungus", rough=0.9)
    emit("stripped_crimson_stem_side", *stem_side(298, (86, 26, 56), (124, 42, 78), (150, 60, 98), (186, 92, 96)), subdir="huge_fungus", rough=0.9)
    emit("stripped_crimson_stem_top", *stem_top(299, (102, 34, 64), (140, 54, 86)), subdir="huge_fungus", rough=0.9)
    emit("stripped_warped_stem_side", *stem_side(300, (24, 76, 72), (44, 110, 100), (64, 142, 126), (46, 196, 180)), subdir="huge_fungus", rough=0.9)
    emit("stripped_warped_stem_top", *stem_top(301, (30, 90, 84), (56, 128, 118)), subdir="huge_fungus", rough=0.9)
    print("stems ok")

    # ================================================================ planks
    PLANK_GRADES = [
        ("planks_oak",        dict(sat=0.85, mult=(1.06, 0.94, 0.74))),
        ("planks_spruce",     dict(sat=0.7, bright=-0.05, mult=(0.85, 0.72, 0.6))),
        ("planks_birch",      dict(sat=0.7, bright=0.07, mult=(1.04, 0.98, 0.8))),
        ("planks_jungle",     dict(sat=0.85, bright=0.01, mult=(1.05, 0.92, 0.72))),
        ("planks_acacia",     dict(sat=1.05, mult=(1.2, 0.74, 0.52))),
        ("planks_big_oak",    dict(sat=0.7, bright=-0.1, mult=(0.72, 0.6, 0.48))),
        ("mangrove_planks",   dict(sat=0.95, mult=(1.2, 0.6, 0.45))),
        ("cherry_planks",     dict(sat=0.8, bright=0.06, mult=(1.08, 0.88, 0.86))),
        ("pale_oak_planks",   dict(sat=0.25, bright=0.1)),
    ]
    for nm, kw in PLANK_GRADES:
        arr = grade(plank, **kw)
        emit(nm, arr, height_from(arr, blur=3), rough=0.8,
             rough_map=rough_from(arr, 0.8, 0.08))
    cpl = grade(plank, sat=1.2, mult=(1.25, 0.45, 0.75), bright=-0.05)
    emit("crimson_planks", cpl, height_from(cpl, blur=3), rough=0.82,
         subdir="huge_fungus")
    wpl = grade(plank, sat=1.15, mult=(0.45, 1.1, 0.95), bright=-0.03)
    emit("warped_planks", wpl, height_from(wpl, blur=3), rough=0.82,
         subdir="huge_fungus")
    bpl = grade(plank, sat=1.0, bright=0.05, mult=(1.15, 1.05, 0.6))
    emit("bamboo_planks", bpl, height_from(bpl, blur=3), rough=0.78)
    print("planks ok")

    # ================================================================ leaves
    leaf_photo = ph("leaves")
    leaf_photo2 = ph("leaves2")
    LEAF_GRADES = [
        ("leaves_oak",      leaf_photo,  dict(sat=1.1, mult=(0.9, 1.02, 0.8) )),
        ("leaves_birch",    leaf_photo,  dict(sat=1.15, mult=(0.95, 1.05, 0.7), bright=0.03)),
        ("leaves_spruce",   ph("needle"), dict(sat=0.8, bright=-0.04, mult=(0.75, 0.95, 0.85), contrast=1.05)),
        ("leaves_jungle",   leaf_photo2, dict(sat=1.2, bright=0.02, mult=(0.85, 1.05, 0.75))),
        ("leaves_acacia",   leaf_photo,  dict(sat=1.0, mult=(0.98, 1.02, 0.75))),
        ("leaves_big_oak",  leaf_photo,  dict(sat=1.0, bright=-0.05, mult=(0.85, 1.0, 0.78), contrast=1.05)),
        ("mangrove_leaves", leaf_photo2, dict(sat=0.95, mult=(0.8, 1.02, 0.72), bright=-0.03)),
        ("cherry_leaves",   leaf_photo2, dict(sat=0.85, mult=(1.35, 0.85, 1.0), bright=0.1)),
        ("pale_oak_leaves", leaf_photo,  dict(sat=0.2, bright=0.08)),
        ("azalea_leaves",   leaf_photo,  dict(sat=1.25, mult=(0.82, 1.06, 0.7))),
        ("azalea_leaves_flowers", leaf_photo, dict(sat=1.15, mult=(0.85, 1.04, 0.72))),
    ]
    for i, (nm, src, kw) in enumerate(LEAF_GRADES):
        arr = grade(src, **kw)
        if nm == "azalea_leaves_flowers":
            arr = add_speckle(arr, 900, [(230, 170, 210), (210, 140, 190)],
                              count=70 * PIX, size=(PIX, 3 * PIX), alpha=0.9)
        if nm == "azalea_leaves":
            arr = add_speckle(arr, 901, [(200, 160, 190), (180, 140, 170)],
                              count=30 * PIX, size=(PIX, 2 * PIX), alpha=0.8)
        gap = 0.4 if "spruce" not in nm else 0.28
        rgba = leaves_rgba(arr, 360 + i * 7, gap=gap)
        hq_ = luminance(arr) * (rgba[:, :, 3] / 255.0)
        emit(nm, rgba, hq_, rough=0.88, sub=0.45, height_amp=0.4)
        emit(nm + "_opaque", arr, luminance(arr), rough=0.88, sub=0.45,
             height_amp=0.4)
    print("leaves ok")

    # ================================================================ small plants
    # procedural but resolution-scaled
    def blades(seed, height=1.0, palette=((96, 150, 46), (120, 176, 60),
                                          (70, 120, 36), (140, 196, 76)),
               count=42, spread=1.0):
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        for i in range(count * PIX // 2):
            x0 = rng.random() * N
            ln = N * height * (0.3 + rng.random() * 0.62)
            bend = (rng.random() - 0.5) * 30 * spread * PIX
            col = palette[int(rng.integers(0, len(palette)))]
            tone = 0.75 + rng.random() * 0.4
            colA = tuple(int(np.clip(v * tone, 0, 255)) for v in col) + (255,)
            w = max(PIX, int(rng.integers(PIX, 3 * PIX)))
            curve = 1.4 + rng.random() * 1.2
            pts = []
            for t in np.linspace(0, 1, 12):
                pts.append((x0 + bend * (t ** curve) +
                            np.sin(t * 3 + i) * 2 * PIX, N - 1 - t * ln))
            dr.line(pts, fill=colA, width=w)
            dr.line([(p_[0] - N, p_[1]) for p_ in pts], fill=colA, width=w)
        return np.asarray(img, np.float32)

    def fern(seed, tall=False):
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
            pts = [(x0 + bend * t * t, N - 1 - t * (N - 1 - tip))
                   for t in np.linspace(0, 1, 16)]
            dr.line(pts, fill=col + (255,), width=2 * PIX)
            for t in np.linspace(0.15, 0.95, 9 if tall else 7):
                x = x0 + bend * t * t
                y = N - 1 - t * (N - 1 - tip)
                ll = N * 0.09 * (1 - t) * (1.4 if tall else 1.1) + 3 * PIX
                for sd in (-1, 1):
                    dr.line([(x, y), (x + sd * ll, y + ll * 0.35)],
                            fill=col + (255,),
                            width=max(PIX, int(3 * (1 - t)) * PIX + PIX))
        return np.asarray(img, np.float32)

    def vine(seed):
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        greens = [(40, 92, 30), (52, 110, 38), (64, 128, 46)]
        for i in range(9):
            x0 = rng.random() * N
            ln = N * (0.35 + rng.random() * 0.65)
            sway = (rng.random() - 0.5) * 10 * PIX
            col = greens[i % 3]
            pts = [(x0 + np.sin(t * 6) * sway * t, -2 + t * ln)
                   for t in np.linspace(0, 1, 12)]
            dr.line(pts, fill=col + (255,), width=max(2 * PIX, N // 48))
            for t in np.linspace(0.15, 0.95, 7):
                x = x0 + np.sin(t * 6) * sway * t
                y = t * ln
                rr = (3 + rng.random() * 3) * PIX
                dr.ellipse([x - rr, y - rr, x + rr, y + rr],
                           fill=greens[int(rng.integers(0, 3))] + (255,))
        return np.asarray(img, np.float32)

    def reeds(seed):
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        for i in range(7):
            x0 = (i + 0.5) / 7 * N + (rng.random() - 0.5) * 6 * PIX
            w = int(rng.integers(7 * PIX, 11 * PIX))
            tone = 0.85 + rng.random() * 0.25
            base = (int(150 * tone), int(200 * tone), int(90 * tone))
            dr.rectangle([x0 - w / 2, 0, x0 + w / 2, N], fill=base + (255,))
            dr.rectangle([x0 - w / 2, 0, x0 - w / 2 + 3 * PIX, N],
                         fill=tuple(int(v * 0.7) for v in base) + (255,))
            seg = int(rng.integers(4, 7))
            for s in range(seg):
                y = int((s + 0.5) / seg * N)
                dr.line([x0 - w / 2, y, x0 + w / 2, y],
                        fill=tuple(int(v * 0.72) for v in base) + (255,),
                        width=2 * PIX)
        return np.asarray(img, np.float32)

    def waterlily(seed):
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        cx, cy = N / 2, N / 2
        rng = np.random.default_rng(seed)
        npts = 30
        pts = []
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
        dr.polygon([(cx, cy), (cx + N * .45, cy - N * .18),
                    (cx + N * .45, cy + N * .04)], fill=(0, 0, 0, 0))
        for a in np.linspace(-2.4, 2.6, 10):
            ax = cx + np.cos(a) * N * 0.4
            ay = cy + np.sin(a) * N * 0.4
            mx = cx + np.cos(a) * N * 0.2 - np.sin(a) * 6 * PIX
            my = cy + np.sin(a) * N * 0.2 + np.cos(a) * 6 * PIX
            dr.line([(cx, cy), (mx, my), (ax, ay)],
                    fill=(46, 98, 40, 160), width=PIX)
        dr.ellipse([cx - 4 * PIX, cy - 4 * PIX, cx + 4 * PIX, cy + 4 * PIX],
                   fill=(240, 234, 130, 255))
        return np.asarray(img, np.float32)

    def flower(seed, petals, center, petal_n=6, petal_r=None, stem_h=None,
               leaf=True, style="round", cy=None):
        p = PIX
        petal_r = (petal_r or 9) * p
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        cx = N // 2
        if cy is None:
            cy = int(N * 0.30)
        if stem_h is None:
            stem_h = N - cy - 4 * p
        gshade = [(46, 110, 36), (60, 132, 44), (72, 150, 52)]
        dr.line([cx, cy, cx, cy + stem_h], fill=gshade[1] + (255,),
                width=max(2 * p, N // 64))
        if leaf:
            ly = cy + stem_h * 0.55
            dr.ellipse([cx - 14 * p, ly - 4 * p, cx + 4 * p, ly + 6 * p],
                       fill=gshade[0] + (255,))
            dr.ellipse([cx - 4 * p, ly + 8 * p, cx + 14 * p, ly + 16 * p],
                       fill=gshade[2] + (255,))
        if style == "ball":
            rr = petal_r + 3 * p
            for _ in range(90 * p // 2):
                a = rng.random() * 6.28
                d = (rng.random() ** 0.5) * rr
                x = cx + np.cos(a) * d
                y = cy + np.sin(a) * d
                tone = 0.8 + rng.random() * 0.35
                col = petals[int(rng.integers(0, len(petals)))]
                dr.ellipse([x - 2 * p, y - 2 * p, x + 2 * p, y + 2 * p],
                           fill=tuple(int(v * tone) for v in col) + (255,))
            if center:
                dr.ellipse([cx - 3 * p, cy - 3 * p, cx + 3 * p, cy + 3 * p],
                           fill=tuple(center) + (255,))
        elif style == "cup":
            dr.polygon([(cx - petal_r, cy + 6 * p),
                        (cx - petal_r + 2 * p, cy - petal_r),
                        (cx + petal_r - 2 * p, cy - petal_r),
                        (cx + petal_r, cy + 6 * p)], fill=petals[0] + (255,))
            for sd in (-1, 1):
                dr.ellipse([cx + sd * petal_r - petal_r // 2,
                            cy - petal_r - 3 * p,
                            cx + sd * petal_r + petal_r // 2, cy + 3 * p],
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
                dr.ellipse([cx - 4 * p, cy - 4 * p, cx + 4 * p, cy + 4 * p],
                           fill=tuple(center) + (255,))
                dr.ellipse([cx - 2 * p, cy - 2 * p, cx + 2 * p, cy + 2 * p],
                           fill=tuple(int(min(255, v * 1.15)) for v in center) + (255,))
        return np.asarray(img, np.float32)

    emit("tallgrass", blades(400, 0.85), None, rough=0.95, sub=0.5)
    emit("fern", fern(401), None, rough=0.95, sub=0.5)
    emit("double_plant_grass_top", blades(402, 0.75, count=40), None,
         rough=0.95, sub=0.5)
    emit("double_plant_grass_bottom", blades(403, 1.0, count=46), None,
         rough=0.95, sub=0.5)
    emit("double_plant_fern_top", fern(404, True), None, rough=0.95, sub=0.5)
    emit("double_plant_fern_bottom", fern(405, True), None, rough=0.95, sub=0.5)
    emit("vine", vine(406), None, rough=0.95, sub=0.5)
    emit("reeds", reeds(407), None, rough=0.85, sub=0.4)
    emit("waterlily", waterlily(408), None, rough=0.7, sub=0.5)
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
        emit(mcn, flower(420 + i * 3, **cfg), None, rough=0.9, sub=0.55)
    emit("double_plant_paeonia_top", flower(460, petals=[(236, 132, 158), (246, 164, 184)], center=(252, 224, 120), petal_n=7, petal_r=12, cy=int(N * .35)), None, rough=0.9, sub=0.55)
    emit("double_plant_paeonia_bottom", blades(461, 1.0, count=26), None, rough=0.95, sub=0.5)
    emit("double_plant_rose_top", flower(462, petals=[(196, 32, 32), (220, 52, 44)], center=(90, 20, 16), petal_n=6, petal_r=12, cy=int(N * .35)), None, rough=0.9, sub=0.55)
    emit("double_plant_rose_bottom", blades(463, 1.0, count=26), None, rough=0.95, sub=0.5)
    emit("double_plant_sunflower_front", flower(464, petals=[(252, 200, 40), (252, 224, 80)], center=(112, 76, 32), petal_n=10, petal_r=13, cy=int(N * .4)), None, rough=0.9, sub=0.55)
    emit("double_plant_sunflower_back", flower(465, petals=[(110, 140, 60), (96, 126, 52)], center=(96, 70, 30), petal_n=8, petal_r=11, cy=int(N * .4)), None, rough=0.9, sub=0.55)
    emit("double_plant_sunflower_top", flower(466, petals=[(252, 200, 40)], center=(112, 76, 32), petal_n=6, petal_r=8, cy=int(N * .3)), None, rough=0.9, sub=0.55)
    emit("double_plant_sunflower_bottom", blades(467, 1.0, count=24), None, rough=0.95, sub=0.5)
    dry = ((196, 172, 92), (176, 152, 78), (146, 124, 60))
    emit("short_dry_grass", blades(468, 0.6, palette=dry, count=30), None,
         rough=0.98)
    emit("tall_dry_grass", blades(469, 0.9, palette=dry, count=36), None,
         rough=0.98)
    print("plants ok")

    # ================================================================ misc
    def glass():
        fr = max(4 * PIX, N // 24)
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        dr.rectangle([0, 0, N - 1, N - 1], outline=(220, 236, 242, 205), width=fr)
        f = fbm(20, 500)
        arr = np.zeros((N, N, 4), np.float32)
        arr[:, :, :3] = np.array([240, 250, 253], np.float32)
        yy, xx = np.mgrid[0:N, 0:N]
        streak = (np.abs(np.sin((yy + xx) / N * 6.28 * 2)) ** 12)
        arr[:, :, 3] = np.clip((f - 0.8) * 255 * 4, 0, 15) + streak * 12
        a = np.asarray(img, np.float32)[:, :, 3]
        arr[:, :, 3] = np.clip(arr[:, :, 3] + a, 0, 255)
        return arr

    emit("glass", glass(), None, rough=0.04)
    ice1 = grade(ph("ice"), sat=0.9, mult=(0.8, 0.92, 1.05), bright=0.05)
    emit("ice", ice1, height_from(ice1, blur=5), rough=0.08)
    ice2 = grade(ph("ice2"), sat=0.95, mult=(0.72, 0.85, 1.08), bright=0.0)
    emit("ice_packed", ice2, height_from(ice2, blur=5), rough=0.12)
    ice3 = grade(ph("ice3"), sat=1.15, mult=(0.6, 0.78, 1.15), bright=-0.02)
    emit("blue_ice", ice3, height_from(ice3, blur=5), rough=0.10)

    # melon & pumpkin stay procedural (their stripe language is unique)
    def melon(seed):
        f = fbm(20, seed)
        c = ramp(f, [(0, (84, 124, 40)), (1, (110, 152, 54))])
        stripes = np.abs(np.sin(np.arange(N)[None, :] / N * 6.28 * 5 +
                                fbm(20, seed + 1) * 3)) ** 0.6
        c *= (0.72 + stripes[..., None] * 0.38)
        return emboss(c, stripes, 0.5), stripes * 0.4 + f * 0.6

    def pumpkin(seed):
        f = fbm(16, seed)
        ribs = np.abs(np.sin(np.arange(N)[None, :] / N * 6.28 * 4 +
                             fbm(16, seed + 1) * 2.2)) ** 0.5
        c = ramp(f * 0.3 + ribs * 0.7, [(0, (162, 84, 24)), (0.5, (196, 108, 32)),
                                        (1, (224, 134, 44))])
        return emboss(c, ribs, 0.7), ribs * 0.5 + f * 0.5

    mc, mh = melon(500)
    emit("melon_side", mc, mh, rough=0.85, sub=0.25)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.sqrt((yy - N / 2) ** 2 + (xx - N / 2) ** 2)
    ring = np.abs(np.sin(d / N * 20)) ** 0.6
    mct = mc * (0.85 + ring[..., None] * 0.15)
    emit("melon_top", mct, mh, rough=0.85, sub=0.25)
    pc, phh = pumpkin(502)
    emit("pumpkin_side", pc, phh, rough=0.85, sub=0.2)
    stem = np.clip(1 - d / (N * 0.09), 0, 1)
    pct = pc * (1 - stem[..., None] * 0.9) + \
        np.array((96, 108, 52), np.float32) * stem[..., None] * 0.9
    emit("pumpkin_top", pct, phh, rough=0.85, sub=0.2)
    print("misc ok")

    with open(os.path.join(os.path.dirname(ROOT), "blocks_build_log.json"),
              "w") as f:
        json.dump(REG, f, indent=1)
    print("TOTAL", len(REG), "textures")


if __name__ == "__main__":
    main()
