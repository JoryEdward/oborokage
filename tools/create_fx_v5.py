"""0.5.0 particle sprites: moon petal, thunder spark, ice shard and void mote (procedural, original).

Sprites are near-white so the particle class can tint them per blade. Run from the project root.
"""
import json, math, struct, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/oborokage'


def png(rel, w, h, pixels):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(pixels[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def sprite(name, fn, n=32):
    pix = []
    for y in range(n):
        for x in range(n):
            u, v = (x + .5) / n * 2 - 1, (y + .5) / n * 2 - 1
            a, shade = fn(u, v)
            a = max(0, min(1, a))
            c = int(170 + 85 * max(0, min(1, shade)))
            pix.extend([c, c, c, int(255 * a)])
    png(f'textures/particle/{name}.png', n, n, pix)
    (ROOT / 'particles').mkdir(parents=True, exist_ok=True)
    (ROOT / f'particles/{name}.json').write_text(json.dumps({'textures': [f'oborokage:{name}']}) + '\n')


def petal(u, v):
    # A sakura-like petal: rounded body narrowing to the base, a soft notch at the tip.
    r = math.hypot(u * 1.5, v + .1)
    notch = math.exp(-((u) / .12) ** 2) * math.exp(-((v + .95) / .2) ** 2)
    body = 1 - r
    return min(1, body * 4) - notch * 1.5, .6 + .4 * (1 - abs(u))


def spark(u, v):
    # Four-point thunder star with a hot core.
    core = math.exp(-(u * u + v * v) / .02)
    rays = math.exp(-abs(u) / .045) * math.exp(-abs(v) / .55) + math.exp(-abs(v) / .045) * math.exp(-abs(u) / .55)
    return core + rays * .9, core + .5 * rays


def shard(u, v):
    # A long hexagonal ice crystal with a bright edge.
    w = .32 * (1 - abs(v) ** 1.6)
    inside = abs(u) < w
    edge = inside and abs(abs(u) - w) < .1
    return (1.0 if inside else 0) * (.65 + .35 * edge), .5 + .5 * edge


def mote(u, v):
    # A soft void star: round glow with a faint cross flare.
    r = math.hypot(u, v)
    glow = math.exp(-(r / .28) ** 2)
    flare = (math.exp(-abs(u) / .03) + math.exp(-abs(v) / .03)) * math.exp(-r / .5) * .45
    return glow + flare, glow


if __name__ == '__main__':
    for name, fn in (('petal', petal), ('spark', spark), ('shard', shard), ('mote', mote)):
        sprite(name, fn)
    print('sprites written')
