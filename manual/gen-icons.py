#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تولید آیکون‌های PNG برنامه (مکعب ماینکرفتی) — بدون نیاز به PIL"""
import zlib, struct, os

def png(w, h, rows):
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in rows)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))

def cube(size, cy_frac=0.40, r_frac=0.30):
    """مکعب سه‌وجهی هم‌محور (مانند بلوک ماینکرفت)"""
    rows = [[(0, 0, 0, 0)] * size for _ in range(size)]
    cx, cy, r = size / 2.0, size * cy_frac, size * r_frac

    def inside(x, y, pts):
        n = len(pts)
        ins = False
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if ((y1 > y) != (y2 > y)) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                ins = not ins
        return ins

    top = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    left = [(cx - r, cy), (cx, cy + r), (cx, cy + r * 2.0), (cx - r, cy + r)]
    right = [(cx, cy + r), (cx + r, cy), (cx + r, cy + r), (cx, cy + r * 2.0)]

    for y in range(size):
        for x in range(size):
            if inside(x + .5, y + .5, top):
                rows[y][x] = (156, 190, 88, 255)      # رویه روشن
            elif inside(x + .5, y + .5, left):
                rows[y][x] = (105, 143, 62, 255)      # وجه چپ
            elif inside(x + .5, y + .5, right):
                rows[y][x] = (71, 106, 45, 255)       # وجه راست
    return rows

def white_glyph(size=48):
    """مکعب سفید برای آیکون اعلان/کاشی"""
    return [[(r, g, b, a) if a == 0 else (238, 240, 242, 255) for (r, g, b, a) in row]
            for row in cube(size, 0.45, 0.27)]

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, 'res')

DENS = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
for d, s in DENS.items():
    p = os.path.join(RES, d)
    os.makedirs(p, exist_ok=True)
    with open(os.path.join(p, 'ic_launcher.png'), 'wb') as f:
        f.write(png(s, s, cube(s)))

os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
with open(os.path.join(RES, 'drawable', 'ic_stat.png'), 'wb') as f:
    f.write(png(48, 48, white_glyph()))

print('icons generated under', RES)
