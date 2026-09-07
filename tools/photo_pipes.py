# -*- coding: utf-8 -*-
"""
Photo pipeline: real photos -> seamless 512px PBR texture sets for the
Atta Ultra-Real pack. All transforms are numpy-side so every emitted map
(color / height / MERS) stays perfectly in register.
"""
import os, json
import numpy as np
from PIL import Image, ImageFilter

SIZE = 512
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "image-search")
AI = os.path.join(HERE, "..", "textures-ai")


# ---------------------------------------------------------------- io -------
def load(fname):
    """AI-generated sources win over scraped web photos."""
    for base in (AI, SRC):
        p = os.path.join(base, fname)
        if os.path.exists(p):
            # exact stem match for the AI dir too (ai:foo.png referenced bare)
            return Image.open(p).convert("RGB")
    p = os.path.join(SRC, fname)
    if not os.path.exists(p):
        raise FileNotFoundError(p)
    return Image.open(p).convert("RGB")


def center_square(img, frac=1.0, dx=0.0, dy=0.0):
    """Center square crop; frac<1 crops tighter, dx/dy shift in [-0.5,0.5]."""
    w, h = img.size
    s = min(w, h) * frac
    cx = w / 2 + dx * (w - s)
    cy = h / 2 + dy * (h - s)
    x0 = int(max(0, min(w - s, cx - s / 2)))
    y0 = int(max(0, min(h - s, cy - s / 2)))
    return img.crop((x0, y0, x0 + int(s), y0 + int(s)))


def to512(img):
    return np.asarray(img.resize((SIZE, SIZE), Image.LANCZOS), np.float32)


def photo(fname, size=SIZE, frac=1.0, dx=0.0, dy=0.0, tile=True, feather=0,
          rot=0):
    """Load photo -> square crop -> resize -> optional seamless-ify."""
    img = load(fname)
    if rot:
        img = img.rotate(rot, expand=True)
    img = center_square(img, frac, dx, dy)
    arr = to512(img)
    if tile:
        arr = make_seamless(arr, feather or size // 10)
    return arr


def make_seamless(arr, feather=None):
    """Blend borders with a half-offset copy so the texture tiles."""
    n = SIZE
    f = feather or n // 10
    rolled = np.roll(np.roll(arr, n // 2, 0), n // 2, 1)
    t = (np.arange(n, dtype=np.float32) + 0.5) / n
    # weight 1 at the very border -> 0 after feather zone
    wl = np.clip((f / n - t) * n / f, 0, 1)
    wr = np.clip((t - (1 - f / n)) * n / f, 0, 1)
    w1 = wl + wr
    w = np.maximum(w1[None, :], w1[:, None])[..., None]
    w = w * w * (3 - 2 * w)
    return arr * (1 - w) + rolled * w


# ------------------------------------------------------------- grading -----
def grade(arr, mult=(1, 1, 1), sat=1.0, gamma=1.0, bright=0.0, warmth=0.0,
          contrast=1.0):
    """Photographic grade. arr float 0-255."""
    a = np.clip(arr / 255.0, 0, 1)
    if gamma != 1.0:
        a = np.power(a + 1e-6, gamma)
    a = (a - 0.5) * contrast + 0.5 + bright
    lum = a.mean(-1, keepdims=True)
    a = lum + (a - lum) * sat
    if warmth:
        a[:, :, 0] += warmth
        a[:, :, 2] -= warmth
    a = a * np.array(mult, np.float32)[None, None, :]
    return np.clip(a * 255.0, 0, 255)


def luminance(arr):
    return (0.299 * arr[:, :, 0] + 0.587 * arr[:, :, 1] +
            0.114 * arr[:, :, 2]) / 255.0


def height_from(arr, blur=4, lo=None, hi=None):
    """Photo luminance -> centered heightmap field 0..1."""
    L = luminance(arr)
    if blur:
        L = np.asarray(Image.fromarray((L * 255).astype(np.uint8),
                                       "L").filter(
            ImageFilter.GaussianBlur(blur)), np.float32) / 255.0
    a, b = (L.min(), L.max()) if lo is None else (lo, hi)
    h = (L - a) / (b - a + 1e-9)
    return np.clip(h, 0, 1).astype(np.float32)


def rough_from(arr, base=0.9, vary=0.12, blur=3):
    """Darker crevices get rougher; bright tops slightly glossier."""
    L = luminance(arr)
    if blur:
        L = np.asarray(Image.fromarray((L * 255).astype(np.uint8),
                                       "L").filter(
            ImageFilter.GaussianBlur(blur)), np.float32) / 255.0
    r = base + (0.5 - L) * 2 * vary
    return np.clip(r, 0, 1).astype(np.float32)


# ------------------------------------------------------------- effects -----
def blend_noise(base, over, seed, amt=0.5, thresh=None, softness=0.08):
    """Noise-masked overlay of `over` onto `base` (green moss over stones)."""
    from texengine import fbm
    m = fbm(5, seed)
    if thresh is None:
        thresh = 1 - amt
    mask = np.clip((m - (thresh - softness)) / (2 * softness), 0, 1)
    m2 = fbm(14, seed + 1)
    mask = np.clip(mask * (0.55 + m2 * 0.7), 0, 1)
    return base * (1 - mask[..., None]) + over * mask[..., None], mask


def crack_overlay(arr, seed, count=5, dark=0.55, height=None):
    """Random-walk hairline cracks (for cracked stone bricks)."""
    from texengine import N
    rng = np.random.default_rng(seed)
    m = np.zeros((N, N), np.float32)
    img = Image.fromarray(np.zeros((N, N), np.uint8), "L")
    from PIL import ImageDraw
    dr = ImageDraw.Draw(img)
    pix = max(2, N // 170)
    for _ in range(count):
        x, y = rng.random(2) * N
        ang = rng.random() * 6.28
        pts = []
        for _s in range(int(N * 0.6)):
            x += np.cos(ang) * 2 + (rng.random() - 0.5) * 3
            y += np.sin(ang) * 2 + (rng.random() - 0.5) * 3
            ang += (rng.random() - 0.5) * 0.7
            pts.append((x % N, y % N))
        dr.line(pts, fill=200, width=pix)
        for ox, oy in ((-N, 0), (N, 0), (0, -N), (0, N)):
            dr.line([(px + ox, py + oy) for px, py in pts], fill=200, width=pix)
    m = np.asarray(img, np.float32) / 255.0
    out = arr * (1 - m[..., None] * dark)
    if height is not None:
        height = np.clip(height - m * 0.25, 0, 1)
    return out, height, m


def draw_blade_strokes(arr, seed, palette, count=160, lip_y=None,
                       maxlen_frac=0.09):
    """Hanging grass blades over a side-texture lip."""
    from texengine import N, PIX, to_u8
    img = Image.fromarray(to_u8(arr)).convert("RGBA")
    from PIL import ImageDraw
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for x in range(0, N, max(1, PIX)):
        ln = rng.integers(N * 0.05, N * (0.05 + maxlen_frac))
        bend = (rng.random() - 0.5) * 6 * PIX
        col = palette[int(rng.integers(0, len(palette)))]
        tone = 0.75 + rng.random() * 0.4
        colA = tuple(int(np.clip(v * tone, 0, 255)) for v in col) + (235,)
        y0 = 0 if lip_y is None else max(1, int(lip_y[x] * 0.4))
        pts = [(x + bend * t * t, y0 + t * ln) for t in np.linspace(0, 1, 7)]
        dr.line(pts, fill=colA, width=PIX)
    return np.asarray(img, np.float32)[:, :, :3]


# ------------------------------------------------------------- output ------
def save_color(arr, path_noext, quality=90):
    """Opaque color -> jpg; RGBA with transparency -> tga/png auto."""
    a = np.clip(arr, 0, 255).astype(np.uint8)
    if a.shape[2] == 4 and np.any(a[:, :, 3] < 250):
        Image.fromarray(a, "RGBA").save(path_noext + ".tga")
        return "tga"
    Image.fromarray(a[:, :, :3] if a.shape[2] == 4 else a, "RGB").save(
        path_noext + ".jpg", quality=quality)
    return "jpg"


def save_gray(field, path_noext, base=0.5, amp=0.5, quality=88, base_map=0.0):
    h = np.asarray(field, np.float32)
    span = h.max() - h.min() + 1e-9
    hh = (h - h.min()) / span
    v = np.clip(base + (hh - 0.5) * 2 * amp, 0, 1)
    Image.fromarray((v * 255).astype(np.uint8), "L").save(
        path_noext + ".jpg", quality=quality)


def save_mers(metal, emissive, rough, sub, path_noext, quality=90):
    """4ch -> png when subsurface used, otherwise RGB jpg."""
    mer = np.stack([metal, emissive, rough], -1)
    if np.max(sub) > 0.001:
        rgba = np.concatenate([mer, np.clip(sub, 0, 1)[..., None]], -1)
        Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8),
                        "RGBA").save(path_noext + ".png", optimize=True)
        return "png"
    Image.fromarray((np.clip(mer, 0, 1) * 255).astype(np.uint8),
                    "RGB").save(path_noext + ".jpg", quality=quality)
    return "jpg"
