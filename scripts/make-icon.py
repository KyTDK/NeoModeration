#!/usr/bin/env python3
"""Generate a 512x512 brand icon (mint shield + check) as PNG, no dependencies."""
import struct, zlib, math, sys

S = 512
OUT = sys.argv[1] if len(sys.argv) > 1 else "media/icon.png"

# Palette
BG = (16, 19, 28)        # dark navy
MINT_T = (0, 255, 170)   # brand mint top
MINT_B = (0, 210, 150)   # mint bottom (subtle gradient)
WHITE = (245, 255, 252)

def rounded(x, y, w, h, r):
    # inside rounded rect [0,w)x[0,h) with corner radius r
    cx = min(max(x, r), w - r)
    cy = min(max(y, r), h - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r

def in_shield(x, y):
    cx = 256.0
    y0, y1, y2 = 150.0, 300.0, 402.0   # top, shoulder, tip
    if y < y0 or y > y2:
        return False
    dx = abs(x - cx)
    if y <= y1:
        half = 116.0
        # round the top corners a touch
        if y < y0 + 26:
            k = (y0 + 26 - y) / 26.0
            half -= k * k * 30
        return dx <= half
    t = (y - y1) / (y2 - y1)
    half = 116.0 * (1 - (0.35 * t + 0.65 * t * t))  # curved taper to the tip
    return dx <= half

def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    L2 = vx * vx + vy * vy
    t = 0 if L2 == 0 else max(0, min(1, (wx * vx + wy * vy) / L2))
    dx, dy = px - (ax + t * vx), py - (ay + t * vy)
    return math.hypot(dx, dy)

def in_check(x, y):
    th = 15.0
    d = min(
        seg_dist(x, y, 212, 262, 244, 302),
        seg_dist(x, y, 244, 302, 322, 214),
    )
    return d <= th

def px(x, y):
    # base: rounded-square background (transparent outside)
    if not rounded(x, y, S, S, 96):
        return (0, 0, 0, 0)
    if in_shield(x, y):
        if in_check(x, y):
            return (*WHITE, 255)
        t = min(1.0, max(0.0, (y - 150) / 252.0))
        col = tuple(int(MINT_T[i] * (1 - t) + MINT_B[i] * t) for i in range(3))
        return (*col, 255)
    return (*BG, 255)

# Build raw RGBA with a light 2x supersample for smooth edges.
def sample(x, y):
    acc = [0, 0, 0, 0]
    for oy in (0.25, 0.75):
        for ox in (0.25, 0.75):
            r, g, b, a = px(x + ox, y + oy)
            acc[0] += r * a; acc[1] += g * a; acc[2] += b * a; acc[3] += a
    a = acc[3] / 4
    if a == 0:
        return bytes((0, 0, 0, 0))
    return bytes((round(acc[0] / acc[3]), round(acc[1] / acc[3]), round(acc[2] / acc[3]), round(a)))

raw = bytearray()
for y in range(S):
    raw.append(0)  # filter type 0
    for x in range(S):
        raw += sample(x, y)

def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
png += chunk(b"IEND", b"")
with open(OUT, "wb") as f:
    f.write(png)
print("wrote", OUT, len(png), "bytes")
