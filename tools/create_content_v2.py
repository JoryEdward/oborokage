"""1.0.0-beta play-test round: art and models (procedural, original): the kazuuchi tachi (the raikiri's shapes on
plain steel, black lacquer and brown cord), the 樱下鬼 and 落英剑主 skins, their spawn eggs and the
剑魄 icon. Reuses the helpers of create_content_v1.

Run from the project root: python3 tools/create_content_v2.py
"""
import json, math, random, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from create_content_v1 import ROOT, TEX, Canvas, read_png, write_png, shade, mix, part, generated, item_def  # noqa: E402

# ------------------------------------------------------------------ the kazuuchi

# Each raikiri texture is re-inked along a ramp from its darkest to its lightest tone.
RAMPS = {
    'steel': (0x3c4048, 0xe6e9ee), 'edge': (0x8a8f99, 0xffffff), 'spine': (0x30343a, 0xb8bcc4),
    'ink': (0x202226, 0x6a6e76), 'wrap': (0x140f0c, 0x5a4434), 'silk': (0x18181e, 0x55555f),
    'metal': (0x231f1b, 0x8a8070), 'saya': (0x0b0b0d, 0x3c3c43), 'inlay': (0x272320, 0x7a7266),
    'glow': (0x2a2c30, 0x6a6e76), 'crystal': (0x231f1b, 0x7d7468), 'icon': (0x0e0f12, 0xf0f2f6),
}


def recolor(src, dst, lo, hi):
    w, h, px = read_png(TEX / f'item/{src}.png')
    out = bytearray(px)
    for i in range(0, len(px), 4):
        r, g, b, a = px[i:i + 4]
        if not a:
            continue
        c = mix(lo, hi, (.299 * r + .587 * g + .114 * b) / 255)
        out[i:i + 3] = bytes([(c >> 16) & 255, (c >> 8) & 255, c & 255])
    write_png(TEX / f'item/{dst}.png', w, h, out)


def kazuuchi():
    for part_name, (lo, hi) in RAMPS.items():
        recolor(f'raikiri_{part_name}', f'kazuuchi_{part_name}', lo, hi)
    for shape in ('tachi', 'sheathed', 'long_scabbard'):
        model = json.loads((ROOT / f'models/item/raikiri_{shape}.json').read_text())
        model['textures'] = {k: v.replace('raikiri_', 'kazuuchi_') for k, v in model['textures'].items()}
        (ROOT / f'models/item/kazuuchi_{shape}.json').write_text(json.dumps(model, indent=1) + '\n')
        if shape != 'tachi':
            item_def(f'kazuuchi_{shape}', f'oborokage:item/kazuuchi_{shape}')
    generated('kazuuchi_icon', ['oborokage:item/kazuuchi_icon'])
    (ROOT / 'items/kazuuchi.json').write_text(json.dumps({'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': 'gui', 'model': {'type': 'minecraft:model', 'model': 'oborokage:item/kazuuchi_icon'}}],
        'fallback': {'type': 'minecraft:model', 'model': 'oborokage:item/kazuuchi_tachi'}}}, indent=2) + '\n')


# ------------------------------------------------------------------ skins (64x64 player layout)

def humanoid(name, skin, hair, kimono, hakama, sash, collar, eyes, sclera, seed, extras=None):
    c = Canvas(64, 64)
    head = part(c, skin, 0, 0, 8, 8, 8, {'top': hair, 'back': hair}, seed)
    fx, fy, _, _ = head['front']
    c.rect(fx, fy, fx + 8, fy + 2, hair)
    for x in (1, 5):
        c.set(fx + x, fy + 4, sclera)
        c.set(fx + x + 1, fy + 4, eyes)
        c.rect(fx + x, fy + 3, fx + x + 2, fy + 4, shade(hair, .1))
    c.rect(fx + 3, fy + 6, fx + 5, fy + 7, shade(skin, -.35))
    for side in ('right', 'left'):
        x, y, w, h = head[side]
        c.rect(x, y, x + w, y + 4, hair)
    hat = part(c, 0, 32, 0, 8, 8, 8, {}, seed + 1)
    for _, (x, y, w, h) in hat.items():
        for i in range(x, x + w):
            for j in range(y, y + h):
                c.set(i, j, 0, 0)
    tx, ty, _, _ = hat['top']
    c.rect(tx + 3, ty + 2, tx + 5, ty + 6, hair)
    body = part(c, kimono, 16, 16, 8, 12, 4, {}, seed + 2)
    x, y, w, h = body['front']
    for j in range(8):
        c.set(x + j // 2 + 1, y + j, collar)
        c.set(x + 6 - j // 2, y + j, collar)
    c.rect(x, y + 8, x + 8, y + 10, sash)
    for n in ('right', 'left', 'back'):
        bx, by, bw, bh = body[n]
        c.rect(bx, by + 8, bx + bw, by + 10, sash)
    arms = []
    for (u, v) in [(40, 16), (32, 48)]:
        arm = part(c, kimono, u, v, 4, 12, 4, {'bottom': skin}, seed + u + v)
        for n in ('right', 'front', 'left', 'back'):
            ax, ay, aw, ah = arm[n]
            c.rect(ax, ay + 9, ax + aw, ay + ah, skin)
        arms.append(arm)
    legs = []
    for (u, v) in [(0, 16), (16, 48)]:
        leg = part(c, hakama, u, v, 4, 12, 4, {'bottom': 0x2a2018}, seed + u + v + 5)
        for n in ('right', 'front', 'left', 'back'):
            lx, ly, lw, lh = leg[n]
            for k in range(1, lw, 2):
                for j in range(lh - 2):
                    c.set(lx + k, ly + j, shade(hakama, -.15))
            c.rect(lx, ly + lh - 2, lx + lw, ly + lh, 0x2a2018)
        legs.append(leg)
    if extras:
        extras(c, head, body, arms, legs)
    c.save(f'entity/{name}.png')


def petals(c, regions, color, count, seed):
    r = random.Random(seed)
    for _ in range(count):
        x, y, w, h = regions[r.randrange(len(regions))]
        px, py = x + r.randrange(w), y + r.randrange(h)
        c.set(px, py, color)
        if r.random() < .5:
            c.set(px + 1, py, shade(color, -.15))


def blossom_oni():
    """樱下鬼: grey, bloodless skin, long ash hair, hollow eyes with a pink glow, a faded and torn
    kimono with petals still caught in it, all of it half-seen (see ghostly)."""
    def extras(c, head, body, arms, legs):
        fx, fy, _, _ = head['front']
        for x in (1, 5):
            c.rect(fx + x, fy + 3, fx + x + 2, fy + 5, 0x241a26)     # hollow sockets
            c.set(fx + x + 1, fy + 4, 0xff7ab8)                       # the glow
        for n in ('front', 'right', 'left', 'back'):                  # long hair over the face sides
            x, y, w, h = head[n]
            if n != 'front':
                c.rect(x, y, x + w, y + h - 1, 0xd2ccd6, .1, 7)
        r = random.Random(41)
        regions = [body[n] for n in ('front', 'back')] + [a[n] for a in arms for n in ('front', 'right', 'left')]
        for _ in range(26):                                            # torn, darker patches
            x, y, w, h = regions[r.randrange(len(regions))]
            px, py = x + r.randrange(w), y + r.randrange(h)
            c.set(px, py, 0x3e3846)
        petals(c, regions + [l[n] for l in legs for n in ('front', 'right')], 0xf4a9c8, 22, 43)
    humanoid('blossom_oni', skin=0xb4bcc6, hair=0xd8d3dc, kimono=0x5d586c, hakama=0x34303c,
             sash=0x7c5c6c, collar=0xcfc8d4, eyes=0xff7ab8, sclera=0x241a26, seed=31, extras=extras)
    ghostly('blossom_oni', 37)


def ghostly(name, seed):
    """Make a skin half-seen: translucent all over, fading toward the hem of the body, torn through
    with holes that grow toward the hem, the sleeve ends ragged. Legs and the left arm are not drawn
    by the renderer, so they are left alone."""
    w, h, px = read_png(TEX / f'entity/{name}.png')
    r = random.Random(seed)
    out = bytearray(px)

    def alpha(x, y, a):
        i = (y * w + x) * 4
        if out[i + 3]:
            out[i + 3] = max(0, min(255, a))

    for y in range(16):                       # head and its hair: the most solid part
        for x in range(32):
            alpha(x, y, 225)
    for (u, v) in [(16, 16)]:                 # body: 200 at the collar down to ~40 at the hem
        for y in range(v, v + 16):
            for x in range(u, u + 24):
                depth = max(0, y - (v + 4)) / 12
                a = int(200 - 160 * depth)
                if r.random() < .05 + .45 * depth ** 2:
                    a = 0                     # torn through
                alpha(x, y, a)
    for (u, v) in [(40, 16)]:                 # the right arm: translucent, ragged at the cuff
        for y in range(v, v + 16):
            for x in range(u, u + 16):
                depth = max(0, y - (v + 8)) / 8
                a = int(185 - 60 * depth)
                if y >= v + 4 and r.random() < .04 + .3 * depth ** 2:
                    a = 0
                alpha(x, y, a)
    write_png(TEX / f'entity/{name}.png', w, h, out)


def relic_poses():
    """Relics are now carried by the 樱下鬼: hold them like blades, not like flat items."""
    for relic in ['tachi', 'wakizashi', 'raikiri', 'frostmoon', 'voidstring']:
        path = ROOT / f'models/item/broken_{relic}.json'
        m = json.loads(path.read_text())
        m['parent'] = 'minecraft:item/handheld'
        path.write_text(json.dumps(m, indent=2) + '\n')


def petal_lord():
    """落英剑主: a white kimono strewn with cherry petals, a crimson collar and hakama, a gold sash,
    black hair with one white streak and gold eyes."""
    def extras(c, head, body, arms, legs):
        x, y, w, h = head['top']
        c.rect(x + 5, y, x + 6, y + h, 0xeeeaf2)                    # the white streak
        fx, fy, _, _ = head['front']
        c.set(fx + 5, fy, 0xeeeaf2)
        c.set(fx + 5, fy + 1, 0xeeeaf2)
        regions = [body[n] for n in ('front', 'back', 'right', 'left')] + [
            a[n] for a in arms for n in ('front', 'right', 'left', 'back')]
        petals(c, regions, 0xf29ab9, 70, 57)
        petals(c, regions, 0xffd0e0, 30, 58)
        for a in arms:                                                 # crimson sleeve cuffs
            for n in ('right', 'front', 'left', 'back'):
                ax, ay, aw, ah = a[n]
                c.rect(ax, ay + 8, ax + aw, ay + 9, 0x8c1f36)
    humanoid('petal_lord', skin=0xdcab86, hair=0x141018, kimono=0xf1ebee, hakama=0x5a1a2c,
             sash=0xd9b45a, collar=0x8c1f36, eyes=0xffd27a, sclera=0xf6f0e8, seed=51, extras=extras)


# ------------------------------------------------------------------ icons (32x32)

def spawn_egg(name, base, spot, seed):
    c = Canvas(32, 32)
    r = random.Random(seed)
    for y in range(32):
        for x in range(32):
            if ((x - 15.5) / 10) ** 2 + ((y - 17) / 13.5) ** 2 <= 1:
                c.set(x, y, shade(base, (r.random() - .5) * .2))
    for cx, cy, rad in [(12, 12, 3), (20, 18, 2.5), (13, 23, 2.2), (19, 9, 1.8)]:
        for y in range(32):
            for x in range(32):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad and c.get(x, y)[3]:
                    c.set(x, y, spot)
    c.save(f'item/{name}_spawn_egg.png')
    generated(f'{name}_spawn_egg', [f'oborokage:item/{name}_spawn_egg'])
    item_def(f'{name}_spawn_egg', f'oborokage:item/{name}_spawn_egg')


def blade_soul():
    """剑魄: a wisp of pale fire with a white core, pink at its heart and violet at its edges."""
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            # A teardrop: round at the bottom, drawn up into a flickering point.
            t = (y - 4) / 24
            if not 0 <= t <= 1:
                continue
            width = 8.5 * math.sin(math.pi * min(1, t * 1.15) / 2) ** 1.4 * (1 - .15 * t)
            if t > .75:
                width *= math.sqrt(max(0, 1 - ((t - .75) / .25) ** 2))
            sway = 1.6 * math.sin(t * 5.5) * (1 - t)
            d = abs(x - 15.5 - sway)
            if d > width:
                continue
            k = d / max(width, .01)
            core = (1 - k) * (.4 + .6 * t)
            rgb = mix(0x7a4fd6, 0xf28ac0, min(1, core * 1.4))
            rgb = mix(rgb, 0xffffff, max(0, core - .55) * 2)
            alpha = int(255 * min(1, .35 + core * 1.2))
            c.set(x, y, rgb, alpha)
    c.save('item/blade_soul.png')
    generated('blade_soul', ['oborokage:item/blade_soul'])
    item_def('blade_soul', 'oborokage:item/blade_soul')


if __name__ == '__main__':
    kazuuchi()
    blossom_oni()
    relic_poses()
    petal_lord()
    spawn_egg('blossom_oni', 0x8d8597, 0xf4a9c8, 71)
    spawn_egg('petal_lord', 0xf1ebee, 0xc0283f, 73)
    blade_soul()
    print('assets written')
