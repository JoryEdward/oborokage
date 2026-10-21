"""1.0 art and model assets (procedural, original): new item icons, the whetstone block, the
swordsman and training-dummy skins, broken relic blades and the "ancient" variants of the five blades.

Run from the project root: python3 tools/create_content_v1.py
"""
import json, math, random, struct, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/oborokage'
TEX = ROOT / 'textures'


# ------------------------------------------------------------------ PNG in / out

def write_png(path, w, h, px):
    path.parent.mkdir(parents=True, exist_ok=True)
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(px[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def read_png(path):
    data = path.read_bytes()
    pos, idat = 8, b''
    w = h = 0
    while pos < len(data):
        n = struct.unpack('>I', data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and ctype == 6, (path, depth, ctype)
        elif t == b'IDAT':
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = w * 4
    out = bytearray(w * h * 4)
    prev = bytearray(stride)
    i = 0
    for y in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[x] = (line[x] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, out


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = bytearray(w * h * 4)

    def set(self, x, y, rgb, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 4
            self.px[i:i + 4] = bytes([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a])

    def get(self, x, y):
        i = (y * self.w + x) * 4
        return self.px[i:i + 4]

    def rect(self, x0, y0, x1, y1, rgb, noise=0, seed=0):
        r = random.Random(seed)
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, shade(rgb, (r.random() - .5) * noise))

    def save(self, rel):
        write_png(TEX / rel, self.w, self.h, self.px)


def shade(rgb, k):
    """k in [-1, 1]: darker or lighter."""
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    f = lambda v: max(0, min(255, int(v + (255 - v) * k if k > 0 else v * (1 + k))))
    return f(r) << 16 | f(g) << 8 | f(b)


def mix(a, b, t):
    ch = lambda s: [(s >> 16) & 255, (s >> 8) & 255, s & 255]
    ca, cb = ch(a), ch(b)
    r = [int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)]
    return r[0] << 16 | r[1] << 8 | r[2]


# ------------------------------------------------------------------ item icons (32x32)

def intent_crystal():
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            dx, dy = abs(x - 15.5), abs(y - 16)
            # A tall bipyramid: wider at the girdle, faceted left / right.
            if dx / 8.5 + dy / 14 <= 1:
                k = 1 - (dx / 8.5 + dy / 14)
                base = 0x8a6fd6 if x < 16 else 0xbca6f4
                if y < 16:
                    base = shade(base, .25)
                col = mix(base, 0xfff4ff, max(0, k - .55) * 1.6)
                c.set(x, y, col)
    for y in range(6, 26):
        c.set(15, y, 0xffffff, 200)
    for (x, y) in [(13, 9), (12, 10), (18, 21), (19, 20)]:
        c.set(x, y, 0xffffff)
    c.save('item/intent_crystal.png')


def inscription_stone():
    base, glyph = Canvas(32, 32), Canvas(32, 32)
    r = random.Random(7)
    for y in range(5, 28):
        for x in range(7, 25):
            edge = x in (7, 24) or y in (5, 27)
            col = 0x4a4756 if edge else shade(0x77738a, (r.random() - .5) * .25)
            if y < 7:
                col = shade(col, .2)
            base.set(x, y, col)
    # The carved glyph: a stroke and a crescent, left white so the item tint colours it.
    for y in range(9, 24):
        glyph.set(16, y, 0xffffff)
        glyph.set(15, y, 0xffffff, 150)
    for a in range(0, 180, 6):
        t = math.radians(a + 200)
        glyph.set(int(16 + math.cos(t) * 6), int(15 + math.sin(t) * 6), 0xffffff)
    for x in range(12, 21):
        glyph.set(x, 20, 0xffffff)
    base.save('item/inscription_stone.png')
    glyph.save('item/inscription_glyph.png')


def spawn_egg():
    c = Canvas(32, 32)
    r = random.Random(11)
    for y in range(32):
        for x in range(32):
            if ((x - 15.5) / 10) ** 2 + ((y - 17) / 13.5) ** 2 <= 1:
                c.set(x, y, shade(0x2b2440, (r.random() - .5) * .2))
    for cx, cy, rad in [(12, 12, 3), (20, 18, 2.5), (13, 23, 2.2), (19, 9, 1.8)]:
        for y in range(32):
            for x in range(32):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad and c.get(x, y)[3]:
                    c.set(x, y, 0xbca6f4)
    c.save('item/swordsman_spawn_egg.png')


def broken_relics():
    names = {'tachi': 'icon', 'wakizashi': 'wakizashi_icon', 'raikiri': 'raikiri_icon',
             'frostmoon': 'frostmoon_icon', 'voidstring': 'voidstring_icon'}
    for relic, icon in names.items():
        w, h, px = read_png(TEX / f'item/{icon}.png')
        r = random.Random(relic)
        out = bytearray(px)
        # The blade runs from bottom-left (hilt) to top-right (tip): snap it at a jagged line.
        for y in range(h):
            for x in range(w):
                i = (y * w + x) * 4
                if out[i + 3] == 0:
                    continue
                along = (x + (h - 1 - y)) / (w + h)
                cut = .55 + .04 * math.sin(x * 1.7 + y * .9)
                if along > cut:
                    out[i + 3] = 0
                    continue
                rgb = out[i] << 16 | out[i + 1] << 8 | out[i + 2]
                grey = (out[i] + out[i + 1] + out[i + 2]) // 3
                rgb = mix(rgb, grey * 0x010101, .55)
                rgb = shade(rgb, -.25)
                if r.random() < .12:
                    rgb = mix(rgb, 0x7a4a22, .7)
                out[i:i + 3] = bytes([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255])
        write_png(TEX / f'item/broken_{relic}.png', w, h, out)


def ancient(name):
    """Patina: half desaturated, darker, with verdigris and rust specks."""
    src = TEX / f'item/{name}.png'
    w, h, px = read_png(src)
    r = random.Random(name)
    out = bytearray(px)
    for i in range(0, len(out), 4):
        if out[i + 3] == 0:
            continue
        rgb = out[i] << 16 | out[i + 1] << 8 | out[i + 2]
        grey = (out[i] + out[i + 1] + out[i + 2]) // 3
        rgb = shade(mix(rgb, grey * 0x010101, .5), -.18)
        roll = r.random()
        if roll < .08:
            rgb = mix(rgb, 0x5f8a6a, .55)   # verdigris
        elif roll < .14:
            rgb = mix(rgb, 0x7a4a22, .6)    # rust
        out[i:i + 3] = bytes([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255])
    write_png(TEX / f'item/ancient_{name}.png', w, h, out)
    meta = src.with_suffix('.png.mcmeta')
    if meta.exists():
        (TEX / f'item/ancient_{name}.png.mcmeta').write_text(meta.read_text())


# ------------------------------------------------------------------ whetstone block (16x16)

def whetstone_textures():
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, 0x9c9184, .18, 21)
    for y in (4, 9, 13):
        for x in range(16):
            if random.Random(x + y).random() < .7:
                c.set(x, y, 0x7f766b)
    c.save('block/whetstone_stone.png')
    w = Canvas(16, 16)
    w.rect(0, 0, 16, 16, 0x5a3d26, .2, 22)
    for x in (3, 8, 12):
        for y in range(16):
            w.set(x, y, 0x46301e)
    w.save('block/whetstone_wood.png')


def whetstone_model():
    model = {
        'parent': 'minecraft:block/block',
        'textures': {'particle': 'oborokage:block/whetstone_stone',
                     'stone': 'oborokage:block/whetstone_stone',
                     'wood': 'oborokage:block/whetstone_wood'},
        'elements': [
            {'from': [1, 0, 3], 'to': [15, 4, 13],
             'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
            {'from': [3, 4, 5], 'to': [4, 9, 11],
             'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
            {'from': [12, 4, 5], 'to': [13, 9, 11],
             'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
            {'from': [4, 3, 4], 'to': [12, 11, 12],
             'faces': {f: {'texture': '#stone'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
        ],
    }
    p = ROOT / 'models/block/whetstone.json'
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(model, indent=2) + '\n')
    bs = ROOT / 'blockstates/whetstone.json'
    bs.parent.mkdir(parents=True, exist_ok=True)
    bs.write_text(json.dumps({'variants': {'': {'model': 'oborokage:block/whetstone'}}}, indent=2) + '\n')


# ------------------------------------------------------------------ skins (64x64 player layout)

def part(c, base, u, v, w, h, d, cols, seed):
    """Fill one box of the skin: cols = dict face -> rgb (top, bottom, right, front, left, back)."""
    faces = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
    for name, (x, y, fw, fh) in faces.items():
        c.rect(x, y, x + fw, y + fh, cols.get(name, base), .14, seed + hash(name) % 97)
    return faces


def swordsman_skin():
    c = Canvas(64, 64)
    skin, hair, kimono, hakama, sash = 0xd8a27a, 0x1c1720, 0x2c2650, 0x5a5866, 0xbca6f4
    head = part(c, skin, 0, 0, 8, 8, 8, {'top': hair, 'back': hair}, 1)
    fx, fy, _, _ = head['front']
    c.rect(fx, fy, fx + 8, fy + 2, hair)                 # fringe
    for x in (1, 5):
        c.set(fx + x, fy + 4, 0xf2f2f2); c.set(fx + x + 1, fy + 4, 0x231c2e)
        c.rect(fx + x, fy + 3, fx + x + 2, fy + 4, 0x2a2026)   # brows
    c.rect(fx + 3, fy + 6, fx + 5, fy + 7, 0x8a5a44)     # mouth
    for side in ('right', 'left'):
        x, y, w, h = head[side]
        c.rect(x, y, x + w, y + 3, hair)
    hat = part(c, 0, 32, 0, 8, 8, 8, {}, 2)              # hat layer: a topknot only
    for name, (x, y, w, h) in hat.items():
        c.rect(x, y, x + w, y + h, 0, 0)
        for i in range(x, x + w):
            for j in range(y, y + h):
                c.set(i, j, 0, 0)
    tx, ty, _, _ = hat['top']
    c.rect(tx + 3, ty + 2, tx + 5, ty + 6, hair)
    body = part(c, kimono, 16, 16, 8, 12, 4, {}, 3)
    x, y, w, h = body['front']
    for j in range(8):
        c.set(x + j // 2 + 1, y + j, 0xe8e2f2)            # crossed collar
        c.set(x + 6 - j // 2, y + j, 0xe8e2f2)
    c.rect(x, y + 8, x + 8, y + 10, sash)
    for name in ('right', 'left', 'back'):
        bx, by, bw, bh = body[name]
        c.rect(bx, by + 8, bx + bw, by + 10, sash)
    for (u, v) in [(40, 16), (32, 48)]:                  # arms: sleeves, then hands
        arm = part(c, kimono, u, v, 4, 12, 4, {'bottom': skin}, u + v)
        for name in ('right', 'front', 'left', 'back'):
            ax, ay, aw, ah = arm[name]
            c.rect(ax, ay + 9, ax + aw, ay + ah, skin)
    for (u, v) in [(0, 16), (16, 48)]:                   # legs: hakama and sandals
        leg = part(c, hakama, u, v, 4, 12, 4, {'bottom': 0x3a2a1c}, u + v + 5)
        for name in ('right', 'front', 'left', 'back'):
            lx, ly, lw, lh = leg[name]
            for k in range(1, lw, 2):
                for j in range(lh - 2):
                    c.set(lx + k, ly + j, shade(hakama, -.15))
            c.rect(lx, ly + lh - 2, lx + lw, ly + lh, 0x3a2a1c)
    c.save('entity/swordsman.png')


def box_faces(u, v, w, h, d):
    """Minecraft box UV: face name -> (x, y, width, height)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}


def stake_texture():
    """The training stake (see TrainingDummyRenderer.Model for the boxes this layout serves)."""
    c = Canvas(64, 64)
    r = random.Random(77)
    bark, dark, end, straw, straw_dark, rope = 0x6b4a2b, 0x4a321c, 0xa07a4a, 0xd2b058, 0x9c7c30, 0x7a5a32

    def grain(x, y, w, h, base, vertical=True):
        for j in range(h):
            for i in range(w):
                t = (i if vertical else j)
                line = (t * 7 + (j if vertical else i) // 5) % 5 == 0
                col = shade(base, (r.random() - .5) * .18 - (.18 if line else 0))
                c.set(x + i, y + j, col)

    def rings(x, y, w, h):
        cx, cy = x + w / 2 - .5, y + h / 2 - .5
        for j in range(h):
            for i in range(w):
                d = math.hypot(x + i - cx, y + j - cy)
                col = mix(end, dark, .45 if int(d * 1.6) % 2 else 0)
                c.set(x + i, y + j, shade(col, (r.random() - .5) * .1))

    # The log: bark on the sides, rings on the ends.
    for name, (x, y, w, h) in box_faces(0, 0, 6, 28, 6).items():
        if name in ('top', 'bottom'):
            rings(x, y, w, h)
        else:
            grain(x, y, w, h, bark)
    # The straw wrapping: horizontal wraps, a little ragged.
    for name, (x, y, w, h) in box_faces(24, 0, 10, 13, 10).items():
        if name in ('top', 'bottom'):
            for j in range(h):
                for i in range(w):
                    d = math.hypot(i - w / 2 + .5, j - h / 2 + .5)
                    c.set(x + i, y + j, rings_col := (end if d < 3 else shade(straw, (r.random() - .5) * .3)))
        else:
            for j in range(h):
                for i in range(w):
                    k = (j + (i * 3 + 1) // 4) % 3
                    col = straw if k else straw_dark
                    c.set(x + i, y + j, shade(col, (r.random() - .5) * .25))
    # Rope bands: twisted, darker.
    for name, (x, y, w, h) in box_faces(0, 34, 11, 1, 11).items():
        for j in range(h):
            for i in range(w):
                col = rope if (i + j) % 2 else shade(rope, -.25)
                c.set(x + i, y + j, col)
    # The cap and the planks: plain wood.
    for name, (x, y, w, h) in box_faces(24, 23, 8, 2, 8).items():
        if name == 'top':
            rings(x, y, w, h)
        else:
            grain(x, y, w, h, bark, vertical=False)
    for name, (x, y, w, h) in box_faces(0, 46, 16, 2, 4).items():
        grain(x, y, w, h, shade(bark, .08), vertical=False)
    c.save('entity/training_dummy.png')


def training_dummy_icon():
    c = Canvas(32, 32)
    r = random.Random(5)
    bark, straw, rope = 0x6b4a2b, 0xd2b058, 0x7a5a32
    c.rect(6, 27, 26, 29, shade(bark, .08), .15, 1)        # cross base
    c.rect(14, 25, 18, 31, shade(bark, -.1), .15, 2)
    c.rect(13, 3, 19, 28, bark, .2, 3)                     # log
    c.rect(12, 2, 20, 4, shade(bark, .15), .1, 4)          # cap
    for y in range(8, 21):                                 # straw wrap
        for x in range(10, 22):
            k = (y + (x * 3 + 1) // 4) % 3
            c.set(x, y, shade(straw if k else 0x9c7c30, (r.random() - .5) * .25))
    for y in (10, 18):
        c.rect(9, y, 23, y + 1, rope, .1, y)
    c.save('item/training_dummy.png')


# ------------------------------------------------------------------ models and item definitions

def generated(name, layers):
    p = ROOT / f'models/item/{name}.json'
    p.write_text(json.dumps({'parent': 'minecraft:item/generated',
                             'textures': {f'layer{i}': t for i, t in enumerate(layers)}}, indent=2) + '\n')


def item_def(name, model, tints=None):
    m = {'type': 'minecraft:model', 'model': model}
    if tints:
        m['tints'] = tints
    p = ROOT / f'items/{name}.json'
    p.write_text(json.dumps({'model': m}, indent=2) + '\n')


STONES = {'zanshin': 0xd8e0ff, 'focus': 0xbca6f4, 'feather': 0x9ff0c0, 'dew': 0x7fd8ff,
          'crimson': 0xff4a5a}


def models():
    generated('intent_crystal', ['oborokage:item/intent_crystal'])
    item_def('intent_crystal', 'oborokage:item/intent_crystal')
    generated('inscription_stone', ['oborokage:item/inscription_stone', 'oborokage:item/inscription_glyph'])
    for key, rgb in STONES.items():
        item_def(f'inscription_{key}', 'oborokage:item/inscription_stone',
                 [{'type': 'minecraft:constant', 'value': -1},
                  {'type': 'minecraft:constant', 'value': rgb - (1 << 24)}])
    for relic in ['tachi', 'wakizashi', 'raikiri', 'frostmoon', 'voidstring']:
        generated(f'broken_{relic}', [f'oborokage:item/broken_{relic}'])
        item_def(f'broken_{relic}', f'oborokage:item/broken_{relic}')
    generated('training_dummy', ['oborokage:item/training_dummy'])
    item_def('training_dummy', 'oborokage:item/training_dummy')
    generated('swordsman_spawn_egg', ['oborokage:item/swordsman_spawn_egg'])
    item_def('swordsman_spawn_egg', 'oborokage:item/swordsman_spawn_egg')
    item_def('whetstone', 'oborokage:block/whetstone')


def ancient_variants():
    """Copies of each blade's drawn, charged and icon models on patinated textures, and an item
    definition that picks them when the stack's custom_model_data strings start with "ancient"."""
    defs = {'tachi': 'tachi', 'wakizashi': 'wakizashi', 'raikiri': 'raikiri',
            'frostmoon': 'frostmoon', 'voidstring': 'voidstring'}
    textures, done = set(), set()

    def copy_model(model_id):
        name = model_id.split('/')[-1]
        if name in done:
            return f'oborokage:item/ancient_{name}'
        done.add(name)
        src = json.loads((ROOT / f'models/item/{name}.json').read_text())
        for key, tex in list(src.get('textures', {}).items()):
            if tex.startswith('oborokage:item/'):
                t = tex.split('/')[-1]
                textures.add(t)
                src['textures'][key] = f'oborokage:item/ancient_{t}'
        (ROOT / f'models/item/ancient_{name}.json').write_text(json.dumps(src, indent=1) + '\n')
        return f'oborokage:item/ancient_{name}'

    def remap(node):
        if isinstance(node, dict):
            if node.get('type') == 'minecraft:model' and 'model' in node:
                node['model'] = copy_model(node['model'])
            for v in node.values():
                remap(v)
        elif isinstance(node, list):
            for v in node:
                remap(v)

    for item, base in defs.items():
        path = ROOT / f'items/{item}.json'
        d = json.loads(path.read_text())
        if d['model'].get('type') == 'minecraft:select' and d['model'].get('property') == 'minecraft:custom_model_data':
            original = d['model']['fallback']
        else:
            original = d['model']
        ancient_model = json.loads(json.dumps(original))
        remap(ancient_model)
        path.write_text(json.dumps({'model': {
            'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
            'cases': [{'when': 'ancient', 'model': ancient_model}],
            'fallback': original}}, indent=2) + '\n')
    for t in sorted(textures):
        ancient(t)


if __name__ == '__main__':
    random.seed(1)
    intent_crystal()
    inscription_stone()
    training_dummy_icon()
    spawn_egg()
    broken_relics()
    whetstone_textures()
    whetstone_model()
    swordsman_skin()
    stake_texture()
    models()
    ancient_variants()
    print('assets written')
