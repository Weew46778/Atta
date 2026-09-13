# -*- coding: utf-8 -*-
# تولید res/mipmap-*‌ و res/drawable/ic_stat.png از iconwork/icon-512.png
# پارسر/نویسندهٔ PNG خالص — بدون وابستگی (PIL لازم نیست)
import zlib, struct, os

def read_png(path):
    d = open(path,'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', 'not png'
    i, pos = {}, 8
    idat = b''
    while pos < len(d):
        ln, typ = struct.unpack('>I4s', d[pos:pos+8]); pos += 8
        chunk = d[pos:pos+ln]; pos += ln + 4
        if typ == b'IHDR':
            w,h,bd,ct,comp,filt,inter = struct.unpack('>IIBBBBB', chunk)
            assert bd == 8 and ct in (2,6) and inter == 0, f'unsupported bd={bd} ct={ct} inter={inter}'
            i = (w,h,ct)
        elif typ == b'IDAT': idat += chunk
        elif typ == b'IEND': break
    w,h,ct = i
    nch = 3 if ct == 2 else 4
    raw = zlib.decompress(idat)
    stride = w*nch
    out = bytearray(h*stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for x in range(nch, stride): line[x] = (line[x] + line[x-nch]) & 255
        elif f == 2:
            for x in range(stride): line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x-nch] if x >= nch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x-nch] if x >= nch else 0
                b = prev[x]
                c = prev[x-nch] if x >= nch else 0
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out[y*stride:(y+1)*stride] = line
        prev = line
    return w, h, nch, bytes(out)

def area_scale(w, h, nch, px, tw, th):
    out = bytearray(tw*th*nch)
    for ty in range(th):
        y0, y1 = ty*h/th, (ty+1)*h/th
        for tx in range(tw):
            x0, x1 = tx*w/tw, (tx+1)*w/tw
            r=g=b=a=n=0
            for sy in range(int(y0), min(int(y1)+1, h)):
                cy = min(sy+1, y1) - max(sy, y0)
                if cy <= 0: continue
                for sx in range(int(x0), min(int(x1)+1, w)):
                    cx = min(sx+1, x1) - max(sx, x0)
                    if cx <= 0: continue
                    wgt = cx*cy
                    o = (sy*w+sx)*nch
                    r += px[o]*wgt; g += px[o+1]*wgt; b += px[o+2]*wgt
                    a += (px[o+3] if nch==4 else 255)*wgt
                    n += wgt
            o = (ty*tw+tx)*nch
            out[o] = int(r/n); out[o+1] = int(g/n); out[o+2] = int(b/n)
            if nch == 4: out[o+3] = int(a/n)
    return tw, th, nch, bytes(out)

def write_png(path, w, h, nch, px):
    def chunk(t, data):
        return struct.pack('>I', len(data)) + t + data + struct.pack('>I', zlib.crc32(t+data) & 0xffffffff)
    raw = b''
    stride = w*nch
    for y in range(h):
        raw += b'\x00' + px[y*stride:(y+1)*stride]
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 2 if nch==3 else 6, 0,0,0)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def main():
    src = read_png('iconwork/icon-512.png')
    print('src', src[0], 'x', src[1], 'ch', src[2])
    for dpi, sz in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        os.makedirs(f'res/mipmap-{dpi}', exist_ok=True)
        t = area_scale(src[0], src[1], src[2], src[3], sz, sz)
        write_png(f'res/mipmap-{dpi}/ic_launcher.png', *t)
    os.makedirs('res/drawable', exist_ok=True)
    t = area_scale(src[0], src[1], src[2], src[3], 24, 24)
    write_png('res/drawable/ic_stat.png', *t)
    print('icons ok')

if __name__ == '__main__':
    main()
