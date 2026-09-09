#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ساخت pack_icon.png برای پک بدراک Atta (256x256) بدون PIL"""
import zlib, struct, os

W = H = 256

def inside_round(x, y, inset, radius):
    if x < inset or y < inset or x >= W - inset or y >= H - inset:
        return False
    # گوشه‌های گرد
    cx = W - inset - radius
    cy = H - inset - radius
    if x < cx and y < cy:
        return (cx - x) ** 2 + (cy - y) ** 2 <= radius ** 2
    if x < cx and y > H - inset - radius:
        return (cx - x) ** 2 + (y - (H - inset - radius)) ** 2 <= radius ** 2
    if x > W - inset - radius and y < cy:
        return (x - (W - inset - radius)) ** 2 + (cy - y) ** 2 <= radius ** 2
    if x > W - inset - radius and y > H - inset - radius:
        return (x - (W - inset - radius)) ** 2 + (y - (H - inset - radius)) ** 2 <= radius ** 2
    return True

def in_letter(x, y):
    # حرف A
    apex = 66
    base = 190
    cx = 152.0
    halfW = 46.0
    thick = 7.5
    if apex <= y <= base:
        t = (y - apex) / (base - apex)
        spread = t * halfW
        lx = cx - spread
        rx = cx + spread
        if abs(x - lx) <= thick or abs(x - rx) <= thick:
            return True
    # ضربدر A
    if 134 <= y <= 147 and abs(x - cx) <= 26:
        return True
    return False

def in_rail(x, y):
    return 44 <= x <= 60

def lerp(a, b, t):
    return int(a + (b - a) * t)

def main():
    rows = []
    for y in range(H):
        row = bytearray()
        t = y / (H - 1)
        # پس‌زمینه گرادیان تیره
        bg = (lerp(0x16, 0x0E, t), lerp(0x1D, 0x14, t), lerp(0x29, 0x1C, t))
        rail = (lerp(0xA9, 0x3F, t), lerp(0xE3, 0x7D, t), lerp(0x7B, 0x28, t))
        for x in range(W):
            if not inside_round(x, y, 8, 52):
                row += bytes((0, 0, 0, 0))
                continue
            if in_letter(x, y):
                row += bytes((0xF2, 0xF6, 0xFA, 255))
            elif in_rail(x, y):
                row += bytes((rail[0], rail[1], rail[2], 255))
            else:
                row += bytes((bg[0], bg[1], bg[2], 255))
        rows.append(bytes(row))

    raw = b''.join(rows)

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    ihdr = struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    out = os.path.join(os.path.dirname(__file__), 'Atta_Operator_BP', 'pack_icon.png')
    with open(out, 'wb') as f:
        f.write(png)
    print('icon ok', out, len(png), 'bytes')

if __name__ == '__main__':
    main()
