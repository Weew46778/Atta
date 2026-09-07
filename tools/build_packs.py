# -*- coding: utf-8 -*-
"""
Builds the two packs:
  1. AttaUltraReal  — realistic textures + PBR (MERS/heightmaps) + sounds + UI
  2. AttaVibrantShader — Vibrant Visuals tuning (atmospherics, lighting,
     color grading, water)
Then zips both as .mcpack into dist/.
"""
import os, json, uuid, shutil, zipfile, hashlib
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(__file__)
REPO = os.path.abspath(os.path.join(HERE, ".."))
PACKS = os.path.join(REPO, "packs")
DIST = os.path.join(REPO, "dist")
os.makedirs(DIST, exist_ok=True)

UUID_FILE = os.path.join(HERE, "uuids.json")


def load_uuids():
    if os.path.exists(UUID_FILE):
        return json.load(open(UUID_FILE))
    u = {
        "textures_header": str(uuid.uuid4()),
        "textures_module": str(uuid.uuid4()),
        "shader_header": str(uuid.uuid4()),
        "shader_module": str(uuid.uuid4()),
    }
    json.dump(u, open(UUID_FILE, "w"), indent=1)
    return u


U = load_uuids()

MAN_TEX = {
    "format_version": 2,
    "header": {
        "name": "§6Atta UltraReal §7— §b512x Real Photos",
        "description": "§l512x§r real-photo PBR textures + heightmaps, true material sounds. Vibrant Visuals required. Apply ABOVE the shader pack.",
        "uuid": U["textures_header"],
        "version": [2, 1, 0],
        "min_engine_version": [1, 21, 120],
    },
    "modules": [{
        "type": "resources",
        "description": "Atta UltraReal 512x resources",
        "uuid": U["textures_module"],
        "version": [2, 1, 0],
    }],
    "capabilities": ["pbr"],
}

MAN_SH = {
    "format_version": 2,
    "header": {
        "name": "§dAtta Vibrant §bUltra Shader",
        "description": "Realistic lighting, atmosphere, color grading & water for Vibrant Visuals. Place BELOW Atta UltraReal.",
        "uuid": U["shader_header"],
        "version": [2, 1, 0],
        "min_engine_version": [1, 21, 120],
    },
    "modules": [{
        "type": "resources",
        "description": "Atta Vibrant Shader resources",
        "uuid": U["shader_module"],
        "version": [2, 1, 0],
    }],
    "capabilities": ["pbr"],
}

# ------------------------------------------------------------- shader JSONs

LIGHTING = {
    "format_version": "1.26.0",
    "minecraft:lighting_settings": {
        "description": {"identifier": "atta:atta_lighting"},
        "directional_lights": {
            "orbital": {
                "sun": {
                    "illuminance": {
                        "0.0": 130.0, "0.05": 130.0, "0.282": 2.0,
                        "0.2915": 0.01, "0.292": 0.0, "0.709": 0.0,
                        "0.719": 2.0, "0.95": 130.0, "1.0": 130.0
                    },
                    "color": {
                        "0.0": [255, 244, 225],
                        "0.140811": [255, 166, 92],
                        "0.216944": [255, 150, 78],
                        "0.242908": [255, 121, 40],
                        "0.269504": [255, 140, 60],
                        "0.314561": [255, 158, 150],
                        "0.506998": [255, 120, 64],
                        "0.717949": [255, 138, 138],
                        "0.801062": [255, 166, 92],
                        "1.0": [255, 238, 214]
                    }
                },
                "moon": {
                    "illuminance": {
                        "0.0": 0.0, "0.2": 0.0, "0.225": 0.55,
                        "0.735": 0.55, "0.75": 0.0, "1.0": 0.0
                    },
                    "color": {"0.0": [148, 174, 255], "1.0": [148, 174, 255]}
                },
                "orbital_offset_degrees": 0.0
            },
            "flash": {"illuminance": 12.0, "color": [228, 93, 255]}
        },
        "emissive": {"desaturation": 0.05},
        "ambient": {"color": "#EAF2FF", "illuminance": 0.03},
        "sky": {"intensity": 1.0}
    }
}

ATMOSPHERICS = {
    "format_version": "1.21.40",
    "minecraft:atmosphere_settings": {
        "description": {"identifier": "atta:atta_atmospherics"},
        "horizon_blend_stops": {
            "min": {"0.0": 0.0, "1.0": 0.0},
            "start": {
                "0.0": 0.8, "0.25": 0.5, "0.300912": 0.25, "0.75": 0.25,
                "0.827004": 0.5, "1.0": 0.8
            },
            "mie_start": {
                "0.0": 0.5, "0.1": 0.5, "0.2": 1.0, "0.8": 1.0,
                "0.9": 0.5, "1.0": 0.5
            },
            "max": {"0.0": 0.22, "1.0": 0.22}
        },
        "rayleigh_strength": {
            "0.0": 9.0, "0.138743": 12.0, "0.25": 5.0, "0.330402": 5.0,
            "0.640704": 5.0, "0.717412": 5.0, "0.92931": 12.0, "1.0": 9.0
        },
        "sun_mie_strength": {
            "0.0": 0.0, "0.2": 0.0, "0.25": 1.15, "0.4": 0.0, "0.6": 0.0,
            "0.75": 1.15, "0.8": 0.0, "1.0": 0.0
        },
        "moon_mie_strength": {"0.0": 0.12, "1.0": 0.12},
        "sun_glare_shape": {
            "0.0": 0.0, "0.2": 0.0, "0.25": 0.11, "0.4": 0.0, "0.6": 0.0,
            "0.75": 0.09, "0.8": 0.0, "1.0": 0.0
        },
        "sky_zenith_color": {
            "0.0": [88, 122, 196],
            "0.199685": [88, 122, 196],
            "0.35256": [36, 36, 44],
            "0.64488": [36, 36, 44],
            "0.800315": [88, 122, 196],
            "1.0": [88, 122, 196]
        },
        "sky_horizon_color": {
            "0.0": [186, 196, 210],
            "0.167053": [186, 196, 210],
            "0.217114": [250, 176, 128],
            "0.239274": [255, 180, 150],
            "0.276382": [140, 108, 110],
            "0.361464": [168, 164, 240],
            "0.401799": [88, 122, 168],
            "0.616996": [88, 122, 168],
            "0.654508": [148, 148, 240],
            "0.706861": [228, 188, 236],
            "0.748744": [255, 210, 172],
            "0.786432": [230, 214, 190],
            "0.830049": [186, 196, 210],
            "1.0": [186, 196, 210]
        }
    }
}

COLOR_GRADING = {
    "format_version": "1.21.90",
    "minecraft:color_grading_settings": {
        "description": {"identifier": "atta:atta_color_grading"},
        "color_grading": {
            "midtones": {
                "contrast": [1.22, 1.22, 1.22],
                "gain": [1.02, 1.02, 1.02],
                "gamma": [2.2, 2.2, 2.2],
                "offset": [0.0, 0.0, 0.0],
                "saturation": [1.14, 1.14, 1.14]
            },
            "temperature": {
                "enabled": True,
                "temperature": 6400,
                "type": "color_temperature"
            }
        },
        "tone_mapping": {"operator": "generic"}
    }
}

WATER = {
    "format_version": "1.21.120",
    "minecraft:water_settings": {
        "description": {"identifier": "atta:atta_water"},
        "particle_concentrations": {
            "chlorophyll": 0.18,
            "suspended_sediment": 0.06,
            "cdom": 0.12
        },
        "caustics": {"enabled": True, "frame_length": 0.09,
                     "scale": 0.45, "power": 1.15},
        "waves": {
            "enabled": True,
            "frequency": 1.2,
            "octaves": 24,
            "depth": 0.8,
            "speed": 1.6,
            "shape": 1.5,
            "pull": 0.38,
            "mix": 0.2,
            "frequency_scaling": 1.2,
            "speed_scaling": 1.03
        }
    }
}


# --------------------------------------------------------------- colormaps
def save_colormaps(root):
    d = os.path.join(root, "textures", "colormap")
    os.makedirs(d, exist_ok=True)
    W = H = 256
    x = np.linspace(0, 1, W)[None, :]
    y = np.linspace(0, 1, H)[:, None]

    def write(name, rgb):
        arr = np.zeros((H, W, 3), np.float32)
        for c in range(3):
            arr[:, :, c] = rgb[0][c] * (1 - y) + rgb[1][c] * y + \
                (np.sin(x * 3.1 + c) * 2.0)
        Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB").save(
            os.path.join(d, name), optimize=True)

    write("grass.png", ((252, 250, 248), (240, 246, 236)))
    write("foliage.png", ((250, 250, 248), (238, 244, 234)))
    write("birch.png", ((250, 250, 248), (242, 246, 240)))
    write("evergreen.png", ((248, 250, 250), (238, 246, 242)))
    write("swamp_grass.png", ((248, 248, 244), (236, 240, 230)))
    write("swamp_foliage.png", ((248, 248, 244), (236, 240, 230)))
    write("mangrove_swamp_foliage.png", ((248, 250, 248), (238, 244, 238)))
    write("dry_foliage.png", ((252, 250, 244), (246, 242, 232)))


# ---------------------------------------------------------------- panorama
def make_panorama(root):
    d = os.path.join(root, "textures", "ui")
    os.makedirs(d, exist_ok=True)
    S = 1024
    yy = np.linspace(0, 1, S)[:, None]

    def sky_face(seed):
        # vertical gradient sky
        top = np.array([86, 128, 208], np.float32)
        mid = np.array([150, 190, 235], np.float32)
        hor = np.array([252, 214, 170], np.float32)
        z = yy.copy()
        c1 = (np.clip(z / 0.55, 0, 1)) ** 0.8
        sky = hor[None, None, :] * (1 - c1[..., None]) + mid[None, None, :] * c1[..., None]
        c2 = np.clip((z - 0.3) / 0.6, 0, 1) ** 0.7
        sky = sky * (1 - c2[..., None]) + top[None, None, :] * c2[..., None]
        sky = np.repeat(sky, S, axis=1)  # (S,1,3) -> (S,S,3)
        # clouds
        rng = np.random.default_rng(seed)
        cloud = np.zeros((S, S), np.float32)
        for _ in range(7):
            cx, cy = rng.random() * S, rng.random() * S * 0.45 + S * 0.12
            w, h = 90 + rng.random() * 200, 14 + rng.random() * 26
            xs = np.arange(S)[None, :]
            ys = np.arange(S)[:, None]
            dx = np.minimum(np.abs(xs - cx), S - np.abs(xs - cx))
            m = np.exp(-((dx / w) ** 2 + ((ys - cy) / h) ** 2))
            cloud += m * (0.35 + rng.random() * 0.3)
        cloud = np.clip(cloud, 0, 0.75)
        sky = sky * (1 - cloud[..., None]) + np.array(
            [252, 252, 255], np.float32) * cloud[..., None]
        # mountain silhouette
        xs = np.arange(S)[None, :] / S
        sil = (np.abs(np.sin(xs * 9 + seed * 2)) * 0.12 +
               np.abs(np.sin(xs * 23 + seed)) * 0.05 +
               np.abs(np.sin(xs * 47 + seed * 3)) * 0.02)
        horline = S * 0.82
        mh = horline - sil * S
        mask = (np.arange(S)[:, None] > mh)
        mcol = np.array([86, 104, 88], np.float32) * np.clip(1 - sil.T[..., None] * 2, 0.4, 1)
        sky = sky * (~mask)[..., None] + mcol * mask[..., None]
        # blur the silhouette into the distance haze
        img = Image.fromarray(np.clip(sky, 0, 255).astype(np.uint8))
        img = img.filter(ImageFilter.GaussianBlur(1.2))
        return img

    for i in range(4):
        sky_face(11 + i * 7).save(os.path.join(d, f"panorama_{i}.png"),
                                  optimize=True)
    # up face: bright sky + sun glow
    xs, ys = np.meshgrid(np.linspace(-1, 1, S), np.linspace(-1, 1, S))
    r = np.sqrt(xs ** 2 + ys ** 2)
    up = np.zeros((S, S, 3), np.float32)
    up[:] = np.array([96, 140, 218], np.float32)
    glow = np.clip(1 - r / 0.8, 0, 1) ** 2
    up += glow[..., None] * np.array([159, 115, 60], np.float32)
    up[r < 0.09] = (255, 252, 240)
    Image.fromarray(np.clip(up, 0, 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(3)).save(os.path.join(d, "panorama_4.png"),
                                          optimize=True)
    # down face: warm grass-dirt bokeh
    rng = np.random.default_rng(5)
    dn = np.zeros((S, S, 3), np.float32)
    dn[:] = np.array([96, 84, 56], np.float32)
    for _ in range(1600):
        x, y = rng.random() * S, rng.random() * S
        rr = 3 + rng.random() * 14
        col = np.array([(108, 150, 58), (90, 130, 48), (140, 120, 70),
                        (126, 160, 66)][int(rng.integers(0, 4))], np.float32)
        dn[int(y - rr):int(y + rr), int(x - rr):int(x + rr)] = \
            dn[int(y - rr):int(y + rr), int(x - rr):int(x + rr)] * 0.5 + col * 0.5
    Image.fromarray(np.clip(dn, 0, 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(6)).save(os.path.join(d, "panorama_5.png"),
                                          optimize=True)


# -------------------------------------------------------------------- zips
def write_shader_pack():
    root = os.path.join(PACKS, "AttaVibrantShader")
    os.makedirs(root, exist_ok=True)
    json.dump(MAN_SH, open(os.path.join(root, "manifest.json"), "w"), indent=2)
    for path, data in [("atmospherics/atmospherics.json", ATMOSPHERICS),
                       ("lighting/global.json", LIGHTING),
                       ("color_grading/color_grading.json", COLOR_GRADING),
                       ("water/water.json", WATER)]:
        p = os.path.join(root, path)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(data, open(p, "w"), indent=2)
    return root


def zipdir(src, out_zip):
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for base, _, files in os.walk(src):
            for f in files:
                full = os.path.join(base, f)
                rel = os.path.relpath(full, src)
                z.write(full, rel)


def main():
    tex_root = os.path.join(PACKS, "AttaUltraReal")
    json.dump(MAN_TEX, open(os.path.join(tex_root, "manifest.json"), "w"), indent=2)
    save_colormaps(tex_root)
    make_panorama(tex_root)
    sh_root = write_shader_pack()
    for src, name in [(tex_root, "AttaUltraReal.mcpack"),
                      (sh_root, "AttaVibrantShader.mcpack")]:
        out = os.path.join(DIST, name)
        zipdir(src, out)
        print(name, f"{os.path.getsize(out)/1e6:.1f} MB")


if __name__ == "__main__":
    main()
