"""1.1.0 art (procedural, original): the 朽英剑主's skin and spawn egg. Reuses the helpers of
create_content_v1 and create_content_v2.

Run from the project root: PYTHONHASHSEED=0 python3 tools/create_content_v3.py
(the face noise of create_content_v1.part is seeded with Python's string hash.)
"""
import random, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from create_content_v1 import TEX, read_png, write_png  # noqa: E402
from create_content_v2 import humanoid, petals, spawn_egg  # noqa: E402


def withered_lord():
    """朽英剑主: the 落英剑主 after death. The white kimono gone grey as ash, the crimson faded to a
    dried brown, the gold sash tarnished; the black hair all white now, dead petals caught in it;
    bloodless skin and eyes of cold soul fire. Half-seen like the 樱下鬼, but both arms are drawn."""
    def extras(c, head, body, arms, legs):
        fx, fy, _, _ = head['front']
        for x in (1, 5):
            c.rect(fx + x, fy + 3, fx + x + 2, fy + 5, 0x1c2228)       # hollow sockets
            c.set(fx + x + 1, fy + 4, 0x7fe8ff)                          # the soul-fire glow
        for n in ('right', 'left', 'back'):                              # long white hair
            x, y, w, h = head[n]
            c.rect(x, y, x + w, y + h - 1, 0xe4e0e8, .1, 7)
        petals(c, [head[n] for n in ('top', 'back', 'right', 'left')], 0x9a6a6e, 10, 81)
        regions = [body[n] for n in ('front', 'back', 'right', 'left')] + [
            a[n] for a in arms for n in ('front', 'right', 'left', 'back')]
        petals(c, regions, 0x8c6468, 34, 83)                             # withered petals
        r = random.Random(85)
        for _ in range(30):                                              # torn, darker patches
            x, y, w, h = regions[r.randrange(len(regions))]
            c.set(x + r.randrange(w), y + r.randrange(h), 0x4a4650)
        for a in arms:                                                   # the faded cuffs
            for n in ('right', 'front', 'left', 'back'):
                x, y, w, h = a[n]
                c.rect(x, y + 8, x + w, y + 9, 0x5e3a3e)
    humanoid('withered_lord', skin=0xc4cad2, hair=0xe4e0e8, kimono=0xb9b4bf, hakama=0x3e2a30,
             sash=0x8a7a52, collar=0x5e3a3e, eyes=0x7fe8ff, sclera=0x1c2228, seed=61, extras=extras)
    half_seen('withered_lord', 63)


def half_seen(name, seed):
    """Like create_content_v2.ghostly, a little more solid (it is a master) and with both arms
    translucent and ragged at the cuff, since the renderer draws both."""
    w, h, px = read_png(TEX / f'entity/{name}.png')
    r = random.Random(seed)
    out = bytearray(px)

    def alpha(x, y, a):
        i = (y * w + x) * 4
        if out[i + 3]:
            out[i + 3] = max(0, min(255, a))

    for y in range(16):                       # head and hair
        for x in range(32):
            alpha(x, y, 232)
    for y in range(16, 32):                   # body: 215 at the collar down to ~70 at the hem
        for x in range(16, 40):
            depth = max(0, y - 20) / 12
            a = int(215 - 145 * depth)
            if r.random() < .03 + .35 * depth ** 2:
                a = 0
            alpha(x, y, a)
    for (u, v) in [(40, 16), (32, 48)]:       # both arms
        for y in range(v, v + 16):
            for x in range(u, u + 16):
                depth = max(0, y - (v + 8)) / 8
                a = int(200 - 55 * depth)
                if y >= v + 4 and r.random() < .03 + .25 * depth ** 2:
                    a = 0
                alpha(x, y, a)
    write_png(TEX / f'entity/{name}.png', w, h, out)


if __name__ == '__main__':
    withered_lord()
    spawn_egg('withered_lord', 0xb9b4bf, 0x7fe8ff, 75)
    print('assets written')
