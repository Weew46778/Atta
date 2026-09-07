# -*- coding: utf-8 -*-
"""
Atta v2.1 — REAL recorded block sounds (Kenney Impact Sounds, CC0).
Replaces the v1 synthesized SFX with real field recordings:
  footsteps (grass/snow/wood/concrete/carpet) + impacts
  (glass light/medium/heavy, mining, wood, plank, generic, soft).
Each event keeps 5 variants with slight pitch jitter.
"""
import os, json, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
LIB = os.path.join(REPO, "sounds-lib", "kenney")
OUT = os.path.join(REPO, "packs", "AttaUltraReal", "sounds")

VAR = ["000", "001", "002", "003", "004"]


def setdir(name):
    return os.path.join(OUT, "atta", name)


def emit_set(dirname, src_base):
    """Copy 5 variants of a kenney set into the pack, return rel name list."""
    d = setdir(dirname)
    os.makedirs(d, exist_ok=True)
    names = []
    for i, v in enumerate(VAR):
        src = os.path.join(LIB, f"{src_base}_{v}.ogg")
        if not os.path.exists(src):
            raise FileNotFoundError(src)
        dst = os.path.join(d, f"{i}.ogg")
        shutil.copyfile(src, dst)
        names.append(f"sounds/atta/{dirname}/{i}")
    return names


SETS = {}  # set name -> pack rel names

for d, base in [
    ("step_concrete", "footstep_concrete"),
    ("step_grass", "footstep_grass"),
    ("step_snow", "footstep_snow"),
    ("step_wood", "footstep_wood"),
    ("step_carpet", "footstep_carpet"),
    ("glass_light", "impact_glass_light"),
    ("glass_medium", "impact_glass_medium"),
    ("glass_heavy", "impact_glass_heavy"),
    ("mining", "impact_mining"),
    ("wood_light", "impact_wood_light"),
    ("wood_medium", "impact_wood_medium"),
    ("wood_heavy", "impact_wood_heavy"),
    ("plank", "impact_plank_medium"),
    ("generic", "impact_generic_light"),
    ("soft_heavy", "impact_soft_heavy"),
    ("soft_medium", "impact_soft_medium"),
]:
    SETS[d] = emit_set(d, base)

PITCH = [0.97, 0.99, 1.0, 1.02, 1.04]


def snd(setname, volume=1.0, pitch=1.0):
    return [{"name": n, "volume": volume,
             "pitch": round(p * pitch, 3)} for n, p in
            zip(SETS[setname], PITCH)]


SDEF = {}


def event(key, setname, volume=1.0, pitch=1.0, category="block"):
    SDEF[key] = {"category": category, "sounds": snd(setname, volume, pitch)}


# =========================================================== families =====
HARD = ["stone", "ancient_debris", "deepslate", "deepslate_bricks", "basalt",
        "calcite", "tuff", "dripstone_block", "pointed_dripstone",
        "nether_brick", "mud_bricks"]
for m in HARD:
    event(f"dig.{m}", "mining", 1.0)
    event(f"hit.{m}", "mining", 0.7, 1.08)
    event(f"place.{m}", "generic", 0.9, 0.92)
    event(f"step.{m}", "step_concrete", 1.0)
    event(f"jump.{m}", "step_concrete", 1.0, 1.03)
    event(f"land.{m}", "soft_heavy", 0.9, 0.85)
    event(f"fall.{m}", "step_concrete", 0.85, 0.9)
    event(f"use.{m}", "generic", 0.8, 0.95)

# glass gets the full event family + ambient random.glass
for ev, (s, v, p) in {
    "dig":   ("glass_heavy", 1.0, 1.0),
    "place": ("glass_medium", 0.9, 1.0),
    "hit":   ("glass_light", 0.8, 1.05),
    "step":  ("glass_light", 0.9, 0.98),
    "jump":  ("glass_light", 0.9, 1.0),
    "land":  ("glass_medium", 0.85, 0.95),
    "fall":  ("glass_light", 0.85, 0.95),
    "use":   ("glass_medium", 0.7, 1.0),
}.items():
    event(f"{ev}.glass", s, v, p)
event("random.glass", "glass_light", 0.55, 1.0, category="ambient")

WOODS = {
    "wood": 1.0, "cherry_wood": 1.05, "bamboo_wood": 1.15,
    "nether_wood": 0.85, "stem": 0.85,
}
for m, p in WOODS.items():
    event(f"dig.{m}", "wood_heavy", 1.0, p)
    event(f"hit.{m}", "wood_medium", 0.75, p * 1.05)
    event(f"place.{m}", "wood_medium", 0.9, p)
    event(f"step.{m}", "step_wood", 1.0, p)
    event(f"jump.{m}", "step_wood", 1.0, p * 1.03)
    event(f"land.{m}", "wood_medium", 0.9, p * 0.9)
    event(f"fall.{m}", "step_wood", 0.85, p * 0.92)
    event(f"use.{m}", "plank", 0.8, p)

DIRTS = {"grass": 1.0, "dirt_with_roots": 0.95, "roots": 0.95,
         "nylium": 1.05}
for m, p in DIRTS.items():
    event(f"dig.{m}", "soft_heavy", 1.0, p)
    event(f"hit.{m}", "soft_medium", 0.75, p * 1.05)
    event(f"place.{m}", "soft_medium", 0.9, p)
    event(f"step.{m}", "step_grass", 1.0, p)
    event(f"jump.{m}", "step_grass", 1.0, p * 1.03)
    event(f"land.{m}", "soft_heavy", 0.9, p * 0.9)
    event(f"fall.{m}", "step_grass", 0.85, p * 0.92)
    event(f"use.{m}", "soft_medium", 0.75, p)

MUDS = {"mud": 0.85, "packed_mud": 0.9, "soul_sand": 0.75, "soul_soil": 0.8}
for m, p in MUDS.items():
    event(f"dig.{m}", "soft_heavy", 1.0, p)
    event(f"hit.{m}", "soft_medium", 0.75, p * 1.05)
    event(f"place.{m}", "soft_medium", 0.9, p)
    event(f"step.{m}", "step_carpet", 0.95, p)
    event(f"jump.{m}", "step_carpet", 0.95, p * 1.03)
    event(f"land.{m}", "soft_heavy", 0.9, p * 0.95)
    event(f"fall.{m}", "step_carpet", 0.85, p * 0.95)
    event(f"use.{m}", "soft_medium", 0.75, p)
# modern block.* mud events
for m, p in (("mud", 0.85), ("packed_mud", 0.9), ("mud_bricks", 0.95),
             ("muddy_mangrove_roots", 0.85)):
    event(f"block.{m}.break", "soft_heavy", 1.0, p)
    event(f"block.{m}.hit", "soft_medium", 0.75, p * 1.05)
    event(f"block.{m}.step", "step_carpet", 0.95, p)
    event(f"block.{m}.place", "soft_medium", 0.9, p)
    event(f"block.{m}.fall", "step_carpet", 0.85, p * 0.95)

SANDS = {"sand": 0.95, "red_sand": 0.95}
for m, p in SANDS.items():
    event(f"dig.{m}", "soft_heavy", 0.95, p)
    event(f"hit.{m}", "soft_medium", 0.75, p * 1.05)
    event(f"place.{m}", "soft_medium", 0.9, p)
    event(f"step.{m}", "step_carpet", 0.95, p)
    event(f"jump.{m}", "step_carpet", 0.95, p * 1.02)
    event(f"land.{m}", "soft_heavy", 0.9, p * 0.95)
    event(f"fall.{m}", "step_carpet", 0.85, p)
    event(f"use.{m}", "soft_medium", 0.75, p)
# keep legacy "sand" family events available under their MC keys too

event("dig.gravel", "mining", 0.95, 0.8)
event("hit.gravel", "generic", 0.8, 0.85)
event("place.gravel", "generic", 0.9, 0.82)
for ev in ("step", "jump", "fall"):
    event(f"{ev}.gravel", "generic", 0.9, 0.8)
event("land.gravel", "mining", 0.85, 0.75)
event("use.gravel", "generic", 0.7, 0.9)

for m, p in (("snow", 1.0), ("powder_snow", 1.05)):
    event(f"dig.{m}", "soft_heavy", 0.95, p)
    event(f"hit.{m}", "soft_medium", 0.75, p * 1.05)
    event(f"place.{m}", "soft_medium", 0.9, p)
    event(f"step.{m}", "step_snow", 1.0, p)
    event(f"jump.{m}", "step_snow", 1.0, p * 1.02)
    event(f"land.{m}", "soft_heavy", 0.9, p * 0.95)
    event(f"fall.{m}", "step_snow", 0.85, p)
    event(f"use.{m}", "soft_medium", 0.75, p)

event("dig.moss", "soft_medium", 0.95, 1.0)
event("hit.moss", "soft_medium", 0.75, 1.05)
event("place.moss", "soft_medium", 0.9, 1.0)
for ev in ("step", "jump", "fall"):
    event(f"{ev}.moss", "step_carpet", 0.95, 1.05)
event("land.moss", "soft_medium", 0.9, 0.95)
event("use.moss", "soft_medium", 0.75, 1.0)

for m in ("azalea_leaves", "cherry_leaves"):
    event(f"dig.{m}", "generic", 0.85, 1.1)
    event(f"hit.{m}", "generic", 0.7, 1.15)
    event(f"place.{m}", "generic", 0.85, 1.08)
    for ev in ("step", "jump", "fall"):
        event(f"{ev}.{m}", "step_grass", 0.9, 1.15)
    event(f"land.{m}", "soft_medium", 0.85, 1.05)
    event(f"use.{m}", "generic", 0.7, 1.1)

with open(os.path.join(OUT, "sound_definitions.json"), "w") as f:
    json.dump({"format_version": "1.14.0", "sound_definitions": SDEF},
              f, indent=2)

n_files = sum(len(os.listdir(setdir(d))) for d in
              ("step_concrete", "step_grass", "step_snow", "step_wood",
               "step_carpet", "glass_light", "glass_medium", "glass_heavy",
               "mining", "wood_light", "wood_medium", "wood_heavy", "plank",
               "generic", "soft_heavy", "soft_medium"))
print("events:", len(SDEF), "| ogg files used:", n_files)
