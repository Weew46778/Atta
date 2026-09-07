# -*- coding: utf-8 -*-
"""
Synthesizes realistic material-based block sounds for Atta Ultra-Real.
Layered physical-modeling inspired synthesis, exported as OGG Vorbis.
"""
import os, json, wave, subprocess, sys
import numpy as np

SR = 44100
TMP = "/tmp/atta_snd"
os.makedirs(TMP, exist_ok=True)

try:
    import imageio_ffmpeg
    FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
except Exception:
    FFMPEG = "ffmpeg"


# ------------------------------------------------------------- dsp kit -----
def t(dur):
    return np.linspace(0, dur, int(SR * dur), False)


def env_exp(n, attack, decay):
    a = int(SR * attack)
    out = np.ones(n, np.float64)
    out[:a] = np.linspace(0, 1, a)
    out[a:] = np.exp(-np.arange(n - a) / (SR * decay))
    return out


def bpnoise(dur, lo, hi, seed, attack=0.001, decay=0.05):
    """Band-limited noise burst with envelope."""
    n = int(SR * dur)
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(n)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    band = np.exp(-0.5 * ((f - (lo + hi) / 2) / ((hi - lo) / 2 + 1)) ** 4)
    band[(f < lo * 0.5) | (f > hi * 2.2)] *= 0.02
    x = np.fft.irfft(X * band, n)
    x /= np.abs(x).max() + 1e-9
    return x * env_exp(n, attack, decay)


def knock(dur, freq, seed, decay=0.08, drop=0.55, attack=0.0008, vol=1.0):
    """Resonant body knock — decaying sine with downward pitch glide."""
    n = int(SR * dur)
    tt = t(dur)
    rng = np.random.default_rng(seed)
    f = freq * (1 - drop * (1 - np.exp(-tt / (SR * decay) / SR * 0 + 1e-9)) * 0)
    phase = np.cumsum(freq * (1 + drop * np.exp(-tt * 60)) / SR)
    x = np.sin(2 * np.pi * phase)
    # add a second partial
    x += 0.45 * np.sin(2 * np.pi * phase * 2.02 + rng.random())
    x *= env_exp(n, attack, decay)
    return x * vol


def crackle(dur, seed, density=0.6, lo=1500, hi=7000, attack=0.0005,
            decay=0.012):
    """Random micro-clicks scattered in time — breaking crumble."""
    n = int(SR * dur)
    out = np.zeros(n, np.float64)
    rng = np.random.default_rng(seed)
    count = int(dur * density * 120)
    for _ in range(count):
        pos = int(rng.random() * n * 0.9)
        ln = int(SR * (0.004 + rng.random() * 0.014))
        ln = min(ln, n - pos)
        if ln < 8:
            continue
        seg = bpnoise(ln / SR, lo * (0.7 + rng.random() * 0.6),
                      hi * (0.7 + rng.random() * 0.6), seed + int(rng.random() * 9999),
                      attack, decay * (0.5 + rng.random()))
        ln2 = min(len(seg), ln, n - pos)
        out[pos:pos + ln2] += seg[:ln2] * (0.4 + rng.random() * 0.6)
    return out


def glass_shatter(seed):
    n = int(SR * 0.8)
    out = np.zeros(n, np.float64)
    rng = np.random.default_rng(seed)
    out += bpnoise(0.8, 3500, 10000, seed, 0.0005, 0.06) * 1.0
    n_p = int(rng.integers(7, 13))
    for i in range(n_p):
        f = 2400 + rng.random() * 5200
        start = int(rng.random() * SR * 0.06)
        dur = 0.15 + rng.random() * 0.5
        nn = min(int(SR * dur), n - start)
        if nn <= 10:
            continue
        tt = np.arange(nn) / SR
        vib = 1 + 0.012 * np.sin(2 * np.pi * (6 + rng.random() * 10) * tt)
        seg = np.sin(2 * np.pi * f * tt * vib)
        seg *= np.exp(-tt / (0.05 + rng.random() * 0.14))
        out[start:start + nn] += seg * (0.25 + rng.random() * 0.5)
    k = knock(0.2, 170, seed + 5, decay=0.03, vol=0.4)
    out[:len(k)] += k
    return out


def squish(dur, seed, vol=1.0):
    """Wet squish for mud/soul sand."""
    n = int(SR * dur)
    base = bpnoise(dur, 120, 1400, seed, attack=0.01, decay=dur * 0.55)
    tt = t(dur)
    wob = np.sin(2 * np.pi * (80 * np.exp(-tt * 8)) * tt)
    x = base * (1 + wob * 0.5)
    k = knock(dur, 95, seed + 1, decay=0.1, vol=0.8)[:n]
    return (x * 0.9 + k * 0.5) * vol


def rustle(dur, seed):
    """Leaf rustle — fluttering hi noise."""
    n = int(SR * dur)
    x = bpnoise(dur, 2600, 9000, seed, attack=0.004, decay=dur * 0.5)
    tt = t(dur)
    rng = np.random.default_rng(seed)
    fl = np.abs(np.sin(2 * np.pi * (11 + rng.random() * 8) * tt + rng.random() * 3))
    return x * (0.45 + 0.55 * fl)


def normalize(x, peak=0.86):
    m = np.max(np.abs(x)) + 1e-9
    return (x / m * peak).astype(np.float32)


def mix(*xs):
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs:
        out[:len(x)] += x
    return out


# ------------------------------------------------------- material family ---
def stone_family(seed, body=200, lo=1100, hi=5200, crack=0.55, ring=0.2,
                 step_soft=0.9):
    S = {}
    for i in range(4):
        S[f"dig{i+1}"] = mix(
            bpnoise(0.5, lo, hi, seed * 31 + i, 0.0008, 0.05) * 1.0,
            crackle(0.5, seed * 17 + i, density=crack, lo=lo, hi=hi) * 0.8,
            knock(0.35, body * (0.9 + 0.2 * (i % 3) / 2), seed * 7 + i,
                  decay=0.09, vol=0.8)[:int(SR * 0.35)],
        )
    for i in range(6):
        S[f"step{i+1}"] = mix(
            bpnoise(0.16, lo * 0.8, hi * 0.8, seed * 41 + i, 0.001, 0.03), 
            knock(0.16, body * 1.15, seed * 13 + i, decay=0.04, vol=0.9)) * step_soft
    for i in range(3):
        S[f"hit{i+1}"] = mix(
            bpnoise(0.12, lo * 1.2, hi * 1.1, seed * 53 + i, 0.0005, 0.02),
            knock(0.12, body * 1.4, seed * 19 + i, decay=0.03, vol=0.7))
    for i in range(4):
        S[f"place{i+1}"] = mix(
            bpnoise(0.3, lo * 0.9, hi * 0.9, seed * 61 + i, 0.001, 0.04) * 0.9,
            knock(0.3, body, seed * 23 + i, decay=0.07, vol=0.9)[:int(SR * 0.3)] * 0.9,
            crackle(0.25, seed * 29 + i, density=crack * 0.5, lo=lo, hi=hi) * 0.5)
    for i in range(2):
        S[f"land{i+1}"] = mix(
            bpnoise(0.35, lo * 0.7, hi * 0.7, seed * 67 + i, 0.001, 0.06) * 1.1,
            knock(0.35, body * 0.8, seed * 31 + i, decay=0.09, vol=1.1)[:int(SR * 0.35)])
        S[f"fall{i+1}"] = S[f"land{i+1}"]
        S[f"jump{i+1}"] = mix(
            bpnoise(0.14, lo, hi, seed * 71 + i, 0.001, 0.025) * 0.7,
            knock(0.14, body * 1.2, seed * 37 + i, decay=0.035, vol=0.6))
        S[f"use{i+1}"] = mix(
            bpnoise(0.2, lo, hi, seed * 73 + i, 0.001, 0.035) * 0.7,
            knock(0.2, body, seed * 39 + i, decay=0.05, vol=0.8)[:int(SR * 0.2)])
    return S


def wood_family(seed):
    S = {}
    for i in range(4):
        S[f"dig{i+1}"] = mix(
            crackle(0.4, seed + i, density=0.5, lo=900, hi=5500) * 1.0,
            knock(0.35, 210 + i * 18, seed * 3 + i, decay=0.1, vol=0.9)[:int(SR * 0.35)],
            bpnoise(0.4, 700, 3000, seed * 5 + i, 0.001, 0.07) * 0.7)
    for i in range(6):
        S[f"step{i+1}"] = mix(
            knock(0.15, 240 + i * 14, seed * 7 + i, decay=0.05, vol=1.0),
            bpnoise(0.12, 800, 3500, seed * 9 + i, 0.001, 0.03) * 0.5)
    for i in range(3):
        S[f"hit{i+1}"] = mix(
            knock(0.11, 300, seed * 11 + i, decay=0.04, vol=0.8),
            bpnoise(0.08, 1200, 5000, seed * 13 + i, 0.0005, 0.02) * 0.5)
    for i in range(4):
        S[f"place{i+1}"] = mix(
            knock(0.25, 240, seed * 15 + i, decay=0.07, vol=1.0)[:int(SR * 0.25)],
            bpnoise(0.2, 700, 2500, seed * 17 + i, 0.001, 0.05) * 0.6)
    for i in range(2):
        S[f"land{i+1}"] = mix(
            knock(0.3, 190, seed * 19 + i, decay=0.09, vol=1.15)[:int(SR * 0.3)],
            bpnoise(0.25, 600, 2400, seed * 21 + i, 0.001, 0.06) * 0.8)
        S[f"fall{i+1}"] = S[f"land{i+1}"]
        S[f"jump{i+1}"] = mix(knock(0.13, 260, seed * 23 + i, decay=0.04, vol=0.7),
                              bpnoise(0.1, 900, 3200, seed * 25 + i, 0.001, 0.025) * 0.4)
        S[f"use{i+1}"] = mix(knock(0.18, 250, seed * 27 + i, decay=0.05, vol=0.8),
                             bpnoise(0.15, 800, 2800, seed * 29 + i, 0.001, 0.04) * 0.5)
    return S


def dirt_family(seed, hi=2600, body=120, gravelly=0.0):
    S = {}
    for i in range(4):
        S[f"dig{i+1}"] = mix(
            bpnoise(0.45, 250, hi, seed * 3 + i, 0.002, 0.08) * 1.0,
            crackle(0.4, seed * 5 + i, density=0.5 + gravelly, lo=900,
                    hi=hi + 1600) * (0.5 + gravelly),
            knock(0.3, body, seed * 7 + i, decay=0.09, vol=0.8)[:int(SR * 0.3)])
    for i in range(6):
        S[f"step{i+1}"] = mix(
            bpnoise(0.14, 300, hi, seed * 11 + i, 0.002, 0.045) * 0.9,
            crackle(0.1, seed * 13 + i, density=gravelly * 1.2, lo=1400,
                    hi=hi + 2200) * gravelly * 1.6,
            knock(0.12, body * 1.2, seed * 17 + i, decay=0.05, vol=0.5))
    for i in range(3):
        S[f"hit{i+1}"] = mix(
            bpnoise(0.1, 350, hi, seed * 19 + i, 0.001, 0.03),
            crackle(0.08, seed * 23 + i, density=gravelly * 1.3, lo=1500,
                    hi=hi + 2600) * gravelly * 1.6)
    for i in range(4):
        S[f"place{i+1}"] = mix(
            bpnoise(0.25, 280, hi, seed * 27 + i, 0.002, 0.06) * 0.9,
            knock(0.22, body, seed * 29 + i, decay=0.07, vol=0.7)[:int(SR * 0.22)])
    for i in range(2):
        S[f"land{i+1}"] = mix(
            bpnoise(0.3, 220, hi * 0.8, seed * 31 + i, 0.002, 0.08) * 1.1,
            knock(0.3, body * 0.9, seed * 33 + i, decay=0.1, vol=0.9)[:int(SR * 0.3)])
        S[f"fall{i+1}"] = S[f"land{i+1}"]
        S[f"jump{i+1}"] = bpnoise(0.12, 320, hi, seed * 35 + i, 0.002, 0.04) * 0.7
        S[f"use{i+1}"] = bpnoise(0.16, 300, hi, seed * 37 + i, 0.002, 0.05) * 0.7
    return S


def main():
    out_root = os.path.join(os.path.dirname(__file__), "..", "packs",
                            "AttaUltraReal", "sounds", "atta")
    os.makedirs(out_root, exist_ok=True)
    snd = {}

    # ------------------------------------------------- build all sound sets
    groups = {}  # name -> dict of variant arrays

    groups["stone"] = stone_family(100, body=205, lo=1100, hi=5200, crack=0.55)
    groups["deepslate"] = stone_family(200, body=130, lo=800, hi=4200, crack=0.65)
    groups["deepslate_bricks"] = stone_family(300, body=165, lo=1000, hi=4800, crack=0.6)
    groups["basalt"] = stone_family(400, body=190, lo=1600, hi=7000, crack=0.75)
    groups["tuff"] = stone_family(500, body=240, lo=1200, hi=4800, crack=0.45, step_soft=0.85)
    groups["calcite"] = stone_family(600, body=300, lo=1800, hi=6000, crack=0.35, step_soft=0.8)
    groups["dripstone_block"] = stone_family(700, body=230, lo=1400, hi=5600, crack=0.5)
    groups["pointed_dripstone"] = stone_family(800, body=320, lo=2000, hi=8000, crack=0.4, step_soft=0.75)
    groups["netherrack"] = stone_family(900, body=150, lo=900, hi=4600, crack=0.95)
    groups["nether_brick"] = stone_family(1000, body=210, lo=1500, hi=6200, crack=0.5)
    groups["ancient_debris"] = stone_family(1100, body=150, lo=1400, hi=6000, crack=0.5)
    groups["wood"] = wood_family(1200)
    groups["grass"] = dirt_family(1300, hi=2600, body=120, gravelly=0.05)
    groups["gravel"] = dirt_family(1400, hi=3200, body=140, gravelly=0.85)
    groups["sand"] = dirt_family(1500, hi=2200, body=110, gravelly=0.2)
    groups["snow"] = dict()
    groups["soul_soil"] = dirt_family(1700, hi=1800, body=95, gravelly=0.1)
    groups["roots"] = mix_to_sets(dirt_family(1800, hi=2800, body=150, gravelly=0.2),
                                  wood_family(1800), 0.6)
    groups["dirt_with_roots"] = groups["roots"]

    # snow: crunch
    for i in range(4):
        groups["snow"][f"dig{i+1}"] = mix(
            bpnoise(0.4, 900, 4200, 1600 + i, 0.004, 0.08),
            crackle(0.35, 1650 + i, density=0.9, lo=1800, hi=6500) * 0.7,
            knock(0.25, 150, 1690 + i, decay=0.07, vol=0.4)[:int(SR * 0.25)])
    for i in range(6):
        groups["snow"][f"step{i+1}"] = mix(
            bpnoise(0.16, 1000, 4200, 1700 + i, 0.003, 0.05),
            crackle(0.12, 1750 + i, density=0.5, lo=2200, hi=7000) * 0.8)
    for i in range(3):
        groups["snow"][f"hit{i+1}"] = bpnoise(0.1, 1100, 4600, 1780 + i, 0.002, 0.03)
    for i in range(4):
        groups["snow"][f"place{i+1}"] = bpnoise(0.3, 900, 4000, 1790 + i, 0.004, 0.07) * 0.9
    for i in range(2):
        groups["snow"][f"land{i+1}"] = mix(
            bpnoise(0.3, 800, 3600, 1810 + i, 0.004, 0.09),
            crackle(0.25, 1820 + i, density=0.8, lo=1800, hi=6200) * 0.6)
        groups["snow"][f"fall{i+1}"] = groups["snow"][f"land{i+1}"]
        groups["snow"][f"jump{i+1}"] = bpnoise(0.1, 1100, 4200, 1830 + i, 0.002, 0.03) * 0.6
        groups["snow"][f"use{i+1}"] = bpnoise(0.18, 900, 4000, 1840 + i, 0.003, 0.05) * 0.7
    groups["powder_snow"] = {k: v * 0.7 for k, v in groups["snow"].items()}

    # moss: soft fibrous
    moss = {}
    for i in range(4):
        moss[f"dig{i+1}"] = mix(
            bpnoise(0.35, 900, 5200, 1900 + i, 0.004, 0.09) * 0.9,
            rustle(0.3, 1940 + i) * 0.5,
            knock(0.25, 140, 1960 + i, decay=0.08, vol=0.35)[:int(SR * 0.25)])
    for i in range(6):
        moss[f"step{i+1}"] = mix(
            bpnoise(0.13, 1100, 5600, 1910 + i, 0.003, 0.045),
            rustle(0.1, 1950 + i) * 0.4)
    for i in range(3):
        moss[f"hit{i+1}"] = bpnoise(0.1, 1200, 6000, 1930 + i, 0.002, 0.03) * 0.8
    for i in range(4):
        moss[f"place{i+1}"] = mix(bpnoise(0.25, 900, 5000, 1970 + i, 0.004, 0.07),
                                  rustle(0.2, 1980 + i) * 0.4)
    for i in range(2):
        moss[f"land{i+1}"] = bpnoise(0.28, 800, 4600, 1990 + i, 0.004, 0.08) * 0.9
        moss[f"fall{i+1}"] = moss[f"land{i+1}"]
        moss[f"jump{i+1}"] = bpnoise(0.1, 1200, 5400, 2010 + i, 0.002, 0.03) * 0.5
        moss[f"use{i+1}"] = rustle(0.18, 2020 + i) * 0.6
    groups["moss"] = moss

    # nylium: crusty fibers
    nyl = {}
    for i in range(4):
        nyl[f"dig{i+1}"] = mix(
            crackle(0.4, 2100 + i, density=0.8, lo=2400, hi=8000) * 0.9,
            bpnoise(0.4, 500, 2600, 2150 + i, 0.002, 0.07),
            knock(0.3, 160, 2170 + i, decay=0.08, vol=0.5)[:int(SR * 0.3)])
    for i in range(6):
        nyl[f"step{i+1}"] = mix(
            crackle(0.12, 2110 + i, density=0.5, lo=2800, hi=8000) * 0.8,
            bpnoise(0.12, 600, 2800, 2160 + i, 0.002, 0.035) * 0.7)
    for i in range(3):
        nyl[f"hit{i+1}"] = crackle(0.09, 2130 + i, density=0.4, lo=3000, hi=8000)
    for i in range(4):
        nyl[f"place{i+1}"] = mix(
            crackle(0.2, 2180 + i, density=0.6, lo=2400, hi=7600) * 0.7,
            bpnoise(0.2, 500, 2400, 2190 + i, 0.002, 0.05))
    for i in range(2):
        nyl[f"land{i+1}"] = mix(
            bpnoise(0.28, 450, 2400, 2210 + i, 0.002, 0.07),
            crackle(0.2, 2220 + i, density=0.7, lo=2400, hi=7600) * 0.5)
        nyl[f"fall{i+1}"] = nyl[f"land{i+1}"]
        nyl[f"jump{i+1}"] = bpnoise(0.1, 700, 3000, 2230 + i, 0.002, 0.03) * 0.5
        nyl[f"use{i+1}"] = crackle(0.15, 2240 + i, density=0.5, lo=2600, hi=7600) * 0.7
    groups["nylium"] = nyl

    # soul sand / mud: wet squish sets
    def wet_family(base_seed, depth=1.0):
        W = {}
        for i in range(4):
            W[f"dig{i+1}"] = mix(squish(0.5, base_seed + i, 1.0),
                                 crackle(0.3, base_seed + 50 + i, density=0.4,
                                         lo=700, hi=2600) * 0.5)
        for i in range(6):
            W[f"step{i+1}"] = squish(0.2, base_seed + 10 + i, 0.75 * depth)
        for i in range(3):
            W[f"hit{i+1}"] = squish(0.14, base_seed + 20 + i, 0.6 * depth)
        for i in range(4):
            W[f"place{i+1}"] = squish(0.4, base_seed + 30 + i, 0.9 * depth)
        for i in range(2):
            W[f"land{i+1}"] = squish(0.35, base_seed + 40 + i, 1.0 * depth)
            W[f"fall{i+1}"] = W[f"land{i+1}"]
            W[f"jump{i+1}"] = squish(0.14, base_seed + 46 + i, 0.5 * depth)
            W[f"use{i+1}"] = squish(0.2, base_seed + 48 + i, 0.65 * depth)
        W["break1"] = W["dig1"]; W["break2"] = W["dig2"]
        W["break3"] = W["dig3"]; W["break4"] = W["dig4"]
        return W

    groups["soul_sand"] = wet_family(2300, 1.05)
    groups["mud"] = wet_family(2400, 0.9)
    groups["mud_bricks"] = stone_family(2500, body=170, lo=700, hi=3400, crack=0.4)
    groups["packed_mud"] = dirt_family(2600, hi=2000, body=160, gravelly=0.15)
    groups["muddy_mangrove_roots"] = groups["roots"]

    # leaves rustle sets
    def leaf_family(base_seed):
        L = {}
        for i in range(4):
            L[f"break{i+1}"] = mix(rustle(0.35, base_seed + i),
                                   bpnoise(0.25, 400, 2200, base_seed + 40 + i,
                                           0.004, 0.06) * 0.4)
            L[f"dig{i+1}"] = L[f"break{i+1}"]
        for i in range(6):
            L[f"step{i+1}"] = rustle(0.18, base_seed + 10 + i) * 0.8
        for i in range(3):
            L[f"hit{i+1}"] = rustle(0.12, base_seed + 20 + i) * 0.7
        for i in range(4):
            L[f"place{i+1}"] = rustle(0.28, base_seed + 30 + i) * 0.85
        for i in range(2):
            L[f"land{i+1}"] = rustle(0.25, base_seed + 36 + i)
            L[f"fall{i+1}"] = rustle(0.22, base_seed + 38 + i) * 0.8
            L[f"jump{i+1}"] = rustle(0.12, base_seed + 40 + i) * 0.6
        return L

    groups["azalea_leaves"] = leaf_family(2700)
    groups["cherry_leaves"] = leaf_family(2800)

    # glass shatter
    glass = {f"glass{i+1}": glass_shatter(3000 + i * 7) for i in range(4)}

    # ------------------------------------------------- export
    manifest_files = {}

    def export(group, key, data):
        sub = os.path.join(out_root, group)
        os.makedirs(sub, exist_ok=True)
        name = f"atta/{group}/{key}"
        wav_p = os.path.join(TMP, "tmp.wav")
        ogg_p = os.path.join(out_root, group, key + ".ogg")
        x = normalize(data)
        with wave.open(wav_p, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((x * 32767).astype(np.int16).tobytes())
        r = subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", wav_p,
                            "-c:a", "libvorbis", "-q:a", "4", ogg_p])
        if r.returncode != 0 or not os.path.exists(ogg_p):
            subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", wav_p,
                            "-c:a", "vorbis", "-strict", "-2", ogg_p], check=True)
        return name

    count = 0
    for g, set_map in groups.items():
        manifest_files[g] = {}
        for key, data in set_map.items():
            nm = export(g, key, data)
            manifest_files[g][key] = nm
            count += 1
    for key, data in glass.items():
        nm = export("glass", key, data)
        count += 1
    print("exported", count, "ogg files")

    # ------------------------------------------------- sound_definitions
    sd = {}
    stone_like = ["stone", "deepslate", "deepslate_bricks", "basalt", "tuff",
                  "calcite", "dripstone_block", "pointed_dripstone",
                  "netherrack", "nether_brick", "ancient_debris", "mud_bricks"]
    dirt_like = ["grass", "gravel", "sand", "snow", "powder_snow", "soul_soil",
                 "roots", "dirt_with_roots", "moss", "nylium", "packed_mud"]
    wet_like = ["soul_sand", "mud"]
    wood_like = ["wood"]

    def lst(group, prefix, nmax):
        out = []
        i = 1
        while True:
            k = f"{prefix}{i}"
            if k not in manifest_files.get(group, {}) and k not in json_safe(groups[group]):
                break
            out.append(f"sounds/{manifest_files[group][k]}")
            i += 1
            if i > 8:
                break
        return out

    def events(group):
        m = groups[group]
        ev = {}
        alias = {"dig": ["dig", "break"], "step": ["step"], "hit": ["hit"],
                 "place": ["place"], "land": ["land"], "fall": ["fall"],
                 "jump": ["jump"], "use": ["use"]}
        for ev_name, prefs in alias.items():
            files = []
            for p in prefs:
                i = 1
                while f"{p}{i}" in m:
                    files.append(f"sounds/atta/{group}/{p}{i}")
                    i += 1
            if files:
                ev[ev_name] = files
        return ev

    for g in stone_like + dirt_like + wet_like + wood_like:
        ev = events(g)
        for evn in ("dig", "step", "hit", "place", "fall", "land", "jump", "use"):
            fl = ev.get(evn)
            if not fl:
                continue
            sd[f"{evn}.{g}"] = {
                "category": "block",
                "sounds": fl,
            }
    # wood derivates via pitch-shifted reuse
    for derived, pitch, vol in [("bamboo_wood", 1.25, 0.95),
                                ("cherry_wood", 1.12, 0.9),
                                ("nether_wood", 0.9, 1.0)]:
        for evn in ("step", "hit", "place", "fall", "land", "jump", "use"):
            fl = events("wood").get(evn, [])
            if fl:
                sd[f"{evn}.{derived}"] = {
                    "category": "block",
                    "sounds": [{"name": f, "pitch": pitch, "volume": vol} for f in fl],
                }
    for evn in ("dig",):
        fl = events("wood").get(evn, [])
        if fl:
            sd[f"{evn}.nether_wood"] = {"category": "block",
                                        "sounds": [{"name": f, "pitch": 0.92, "volume": 1.0} for f in fl]}
    # stems = wet wood
    for evn in ("dig", "step", "hit", "place", "fall", "land", "jump", "use"):
        fl = events("wood").get(evn, [])
        if fl:
            sd[f"{evn}.stem"] = {"category": "block",
                                 "sounds": [{"name": f, "pitch": 1.05, "volume": 0.85} for f in fl]}

    # mud family uses block.* naming
    for blockname, g in [("mud", "mud"), ("packed_mud", "packed_mud"),
                         ("mud_bricks", "mud_bricks"),
                         ("muddy_mangrove_roots", "muddy_mangrove_roots")]:
        ev = events(g)
        for evn in ("break", "hit", "step", "place", "fall"):
            key = "dig" if evn == "break" else evn
            fl = ev.get(key)
            if fl:
                sd[f"block.{blockname}.{evn}"] = {"category": "block", "sounds": fl}

    # leaves (dig + steps etc.)
    for g in ("azalea_leaves", "cherry_leaves"):
        ev = events(g)
        for evn in ("dig", "step", "hit", "place", "fall", "land"):
            fl = ev.get(evn)
            if fl:
                sd[f"{evn}.{g}"] = {"category": "block", "sounds": fl}

    # glass break
    sd["random.glass"] = {
        "category": "block",
        "sounds": [f"sounds/atta/glass/glass{i+1}" for i in range(4)],
    }

    out_sd = os.path.join(os.path.dirname(out_root), "sound_definitions.json")
    with open(out_sd, "w") as f:
        json.dump({"format_version": "1.14.0", "sound_definitions": sd}, f, indent=1)
    print("sound_definitions.json:", len(sd), "events")


def json_safe(d):
    return d


def mix_to_sets(a, b, w=0.5):  # blend two event sets sample-wise
    out = {}
    for k in a:
        if k in b:
            n = max(len(a[k]), len(b[k]))
            x = np.zeros(n)
            x[:len(a[k])] += a[k] * (1 - w)
            bb = np.zeros(n)
            bb[:len(b[k])] += b[k] * w
            out[k] = x + bb
        else:
            out[k] = a[k]
    return out


def shutil_which():
    # imageio-ffmpeg always provides libvorbis
    return True


if __name__ == "__main__":
    import shutil  # noqa
    main()
