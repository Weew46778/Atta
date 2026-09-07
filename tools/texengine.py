"""
Atta Ultra-Real texture engine — procedural, seamless (tileable) texture
synthesis for Minecraft Bedrock (Vibrant Visuals / PBR).

All noise functions are periodic so generated maps tile perfectly.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

N = 128  # texture resolution
PIX = 1    # pixel scale factor vs 128px baseline (N//128)


def set_resolution(npx):
    global N, PIX
    N = int(npx)
    PIX = max(1, int(npx) // 128)


# ---------------------------------------------------------------- noise ----
def _lattice(rng):
    def noise2(cells, seed):
        r = np.random.default_rng(seed)
        return r.random((cells, cells))

    return noise2


def value_noise(cells, seed, n=None):
    n = n or N
    """Tileable value noise, returns float32 in [0,1], shape (n,n)."""
    rng = np.random.default_rng(seed)
    g = rng.random((cells, cells))
    # sample coordinates
    t = np.linspace(0, cells, n, endpoint=False)
    x0 = np.floor(t).astype(int) % cells
    x1 = (x0 + 1) % cells
    fr = (t - np.floor(t)).astype(np.float32)
    s = fr * fr * (3 - 2 * fr)  # smoothstep
    gx0 = g[np.ix_(x0, x0)] * (1 - s[None, :]) + g[np.ix_(x0, x1)] * s[None, :]
    gx1 = g[np.ix_(x1, x0)] * (1 - s[None, :]) + g[np.ix_(x1, x1)] * s[None, :]
    out = gx0 * (1 - s[:, None]) + gx1 * s[:, None]
    return out.astype(np.float32)


def fbm(cells, seed, octaves=5, persistence=0.55, n=None):
    n = n or N
    out = np.zeros((n, n), np.float32)
    amp = 1.0
    total = 0.0
    for o in range(octaves):
        out += value_noise(cells * (2 ** o), seed + 101 * o, n) * amp
        total += amp
        amp *= persistence
    out /= total
    out -= out.min()
    out /= (out.max() + 1e-9)
    return out


def ridged(cells, seed, octaves=4, n=None):
    n = n or N
    out = np.zeros((n, n), np.float32)
    amp = 1.0
    total = 0.0
    for o in range(octaves):
        v = value_noise(cells * (2 ** o), seed + 77 * o, n)
        v = 1.0 - np.abs(v * 2 - 1)
        out += v * v * amp
        total += amp
        amp *= 0.5
    out /= total
    return out


def warp(field, cells, seed, strength):
    """Domain warp a field with low-freq noise vectors (keeps tiling approx)."""
    wx = fbm(cells, seed) * 2 - 1
    wy = fbm(cells, seed + 9) * 2 - 1
    yy, xx = np.mgrid[0:field.shape[0], 0:field.shape[1]]
    nx = (xx + wx * strength).astype(int) % field.shape[1]
    ny = (yy + wy * strength).astype(int) % field.shape[0]
    return field[ny, nx]


def voronoi(cells, seed, n=None, jitter=1.0, power=1.3, chunk=64):
    """Periodic voronoi: returns (f1, f2-f1, cell_id, cell_random).

    Row-chunked so high resolutions stay within a sane memory budget.
    """
    n = n or N
    rng = np.random.default_rng(seed)
    pts = (np.mgrid[0:cells, 0:cells].astype(np.float32).transpose(1, 2, 0))
    jit = (rng.random((cells, cells, 2)) - 0.5) * jitter
    pts = ((pts + jit) / cells).astype(np.float32)  # in [0,1)
    rnd = rng.random((cells, cells))
    f1 = np.full((n, n), 1e9, np.float32)
    f2 = np.full((n, n), 1e9, np.float32)
    cid = np.zeros((n, n), np.int32)
    crnd = np.zeros((n, n), np.float32)
    x = (np.arange(n) / n).astype(np.float32)
    for y0 in range(0, n, chunk):
        y1 = min(n, y0 + chunk)
        rows = y1 - y0
        yy = ((np.arange(y0, y1) / n).astype(np.float32))[:, None]
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                d2 = ((x[None, None, None, :] - (pts[:, :, 1] + dx)[:, :, None, None]) ** 2 +
                      (yy[None, None, :, :] - (pts[:, :, 0] + dy)[:, :, None, None]) ** 2)
                flat = d2.reshape(cells * cells, rows, n)
                idx = np.argmin(flat, axis=0)
                dmin = np.take_along_axis(flat, idx[None, :, :], 0)[0]
                cy = (idx // cells)
                cx = (idx % cells)
                upd = dmin < f1[y0:y1]
                f2[y0:y1] = np.where(upd, f1[y0:y1], np.minimum(f2[y0:y1], dmin))
                f1[y0:y1] = np.where(upd, dmin, f1[y0:y1])
                cid[y0:y1] = np.where(upd, cy * cells + cx, cid[y0:y1])
                crnd[y0:y1] = np.where(upd, rnd[cy, cx], crnd[y0:y1])
    f1 = np.sqrt(f1) ** power
    ed = np.clip(np.sqrt(f2) ** power - f1, 0, 1)
    f1n = f1 / (f1.max() + 1e-9)
    return f1n.astype(np.float32), ed.astype(np.float32), cid, crnd


# ---------------------------------------------------------------- color ----
def ramp(field, stops):
    """Map [0,1] field through color stops [(pos,(r,g,b)),...] -> (n,n,3) u8."""
    stops = sorted(stops, key=lambda s: s[0])
    pos = np.array([s[0] for s in stops], np.float32)
    cols = np.array([s[1] for s in stops], np.float32)
    out = np.zeros(field.shape + (3,), np.float32)
    for c in range(3):
        out[:, :, c] = np.interp(field, pos, cols[:, c])
    return out


def emboss(color, height, strength=0.5, az=(1, 1)):
    """Fake bevel: shade rgb by height gradient."""
    gy, gx = np.gradient(height)
    shade = (gx * az[0] + gy * az[1])
    shade = np.clip(shade * strength, -0.6, 0.6)
    out = color * (1 + shade[..., None])
    return np.clip(out, 0, 255)


def add_speckle(color, seed, colors, count=140, size=(0, 2), alpha=1.0):
    rng = np.random.default_rng(seed)
    img = Image.fromarray(color.astype(np.uint8))
    dr = ImageDraw.Draw(img, "RGBA")
    for _ in range(count):
        x = int(rng.random() * img.width)
        y = int(rng.random() * img.height)
        s = int(rng.integers(size[0], size[1] + 1))
        c = colors[int(rng.integers(0, len(colors)))]
        a = int(255 * alpha * (0.5 + rng.random() * 0.5))
        dr.ellipse([x, y, x + s, y + s], fill=(int(c[0]), int(c[1]), int(c[2]), a))
        if s > 12:
            continue
    return np.asarray(img, np.float32)


def draw_lines(color, seed, palette, count=60, vertical=True, width=1,
               waviness=0.12, alpha=200, margin=0):
    """Wavy strokes, drawn on a 2x canvas then cropped for wrap safety."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x = rng.random() * N
        y0 = margin + rng.random() * max(1, N - 2 * margin)
        ln = N * (0.25 + rng.random() * 0.6)
        c = palette[int(rng.integers(0, len(palette)))]
        a = int(alpha * (0.6 + 0.4 * rng.random()))
        w = width if width >= 1 else 1
        pts = []
        steps = 24
        for i in range(steps + 1):
            t = i / steps
            if vertical:
                px = x + np.sin(t * 6.28 * rng.random() + rng.random() * 6) * waviness * N
                py = y0 + t * ln
            else:
                px = x + t * ln
                py = y0 + np.sin(t * 6.28) * waviness * N
            pts.append((px % N, py % N))
        dr.line(pts, fill=(c[0], c[1], c[2], a), width=max(1, int(w)))
    ov = np.asarray(img, np.float32)
    m = ov[:, :, 3:4] / 255.0
    base = color.copy()
    if base.shape[2] == 4:
        out = base.copy()
        out[:, :, :3] = np.clip(base[:, :, :3] * (1 - m) + ov[:, :, :3] * m, 0, 255)
        out[:, :, 3] = np.clip(base[:, :, 3] + ov[:, :, 3] * (1 - base[:, :, 3:4] / 255.0)[:, :, 0], 0, 255)
        return out
    return np.clip(base * (1 - m) + ov[:, :, :3] * m, 0, 255)


def _leaf_sprites(palette, seed):
    """Build a set of small rotated leaf sprites (PIX-aware)."""
    rng = np.random.default_rng(seed)
    p = PIX
    sprites = []
    S = 18 * p
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    db = ImageDraw.Draw(base)
    for c in palette:
        for shade_k in (0.78, 0.92, 1.06):
            sp = base.copy()
            ds = ImageDraw.Draw(sp)
            col = tuple(min(255, max(0, int(v * shade_k))) for v in c) + (255,)
            ds.ellipse([2 * p, 6 * p, 15 * p, 12 * p], fill=col)
            ds.ellipse([5 * p, 3 * p, 12 * p, 9 * p], fill=col)
            ds.line([9 * p, 2 * p, 9 * p, 15 * p],
                    fill=tuple(int(v * 0.8) for v in col[:3]) + (200,),
                    width=p)
            for angk in (0, 45, 90, 135, 20, 70, 110, 160):
                sprites.append(sp.rotate(angk + rng.random() * 20,
                                         resample=Image.BICUBIC, expand=False))
    return sprites


def leaf_cluster(seed, palette, count=150, alpha_img=True, gap=0.22,
                 size=(3, 7), carry_edges=True):
    """Layered overlapping rotated leaves -> RGBA; alpha holes keep depth."""
    p = PIX
    m = 18 * p
    count = count * p * p // 2  # keep leaf density per area roughly constant
    img = Image.new("RGBA", (N + 2 * m, N + 2 * m), (0, 0, 0, 0))
    rng = np.random.default_rng(seed)
    sprites = _leaf_sprites(palette, seed)
    # dark underlayer for depth
    deep = tuple(int(v * 0.45) for v in palette[0]) + (235,)
    dbase = ImageDraw.Draw(img)
    for _ in range(count // 3):
        x = rng.integers(-m, N + m)
        y = rng.integers(-m, N + m)
        w = int(rng.integers(5 * p, 12 * p + 1))
        dbase.ellipse([x, y, x + w, y + max(3 * p, w - 3 * p)], fill=deep)
    for _ in range(count):
        sp = sprites[int(rng.integers(0, len(sprites)))]
        x = int(rng.integers(-m, N + m))
        y = int(rng.integers(-m, N + m))
        img.alpha_composite(sp, (x, y))
    img = img.crop((m, m, m + N, m + N))
    if gap > 0:
        holes = Image.new("L", (N, N), 0)
        dh = ImageDraw.Draw(holes)
        for _ in range(int(45 * gap / 0.22) * p):
            x = rng.random() * N
            y = rng.random() * N
            r = rng.integers(p, 3 * p + 1)
            dh.ellipse([x - r, y - r, x + r, y + r], fill=255)
        a = np.asarray(img)[:, :, 3].astype(np.int16)
        hn = np.asarray(holes, np.int16)
        a = np.clip(a - hn * 2, 0, 255)
        img.putalpha(Image.fromarray(a.astype(np.uint8)))
    return np.asarray(img, np.float32)


def to_u8(a):
    return np.clip(a, 0, 255).astype(np.uint8)


def save_rgba(arr, path, alpha=None):
    """arr: (n,n,3) or (n,n,4) float/u8. alpha: (n,n) 0..255 optional."""
    a = np.asarray(arr)
    if a.shape[2] == 3:
        a = np.concatenate([a, np.full(a.shape[:2] + (1,), 255, a.dtype)], 2)
    if alpha is not None:
        a[:, :, 3] = np.clip(alpha, 0, 255)
    img = Image.fromarray(to_u8(a), "RGBA")
    if path.endswith(".png") and np.all(a[:, :, 3] > 250):
        img.convert("RGB").save(path, optimize=True)
    else:
        img.save(path, optimize=path.endswith(".png"))
    return img


def save_heightmap(height, path, base=0.5, amp=0.5):
    """height in 0..1 -> png grayscale centered at 128."""
    h = np.clip((height - height.min()) / (height.max() - height.min() + 1e-9), 0, 1)
    v = np.clip(base + (h - 0.5) * 2 * amp, 0, 1)
    Image.fromarray((v * 255).astype(np.uint8), "L").save(path, optimize=True)


def save_mer(metal, emissive, rough, path, base_color=None):
    """Save packed metalness/emissive/roughness map (arrays 0..1)."""
    n = metal.shape
    mer = np.stack([metal, emissive, rough], -1)
    Image.fromarray((np.clip(mer, 0, 1) * 255).astype(np.uint8), "RGB").save(path, optimize=True)
