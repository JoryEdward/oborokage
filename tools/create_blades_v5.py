"""0.5.0 blade family: original parametric geometry and procedural textures, no external art.

Rebuilds the wakizashi, Raikiri, Frostmoon and Voidstring from scratch (each with its own
silhouette, guard, grip, scabbard and motif) and rewrites the hold transforms of all five blades,
including the unchanged Oborokage tachi. Run from the project root: python3 tools/create_blades_v5.py
"""
import json, math, random, struct, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/oborokage'


def png(rel, w, h, pixels):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(pixels[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def save(rel, data):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


# ---------------------------------------------------------------- hold transforms
# Held-item display frames, measured in game with the mannequin rig (verification ClientShowcase):
#   third person: +X forward, +Y toward the holder's left, +Z up
#   first person: +X right, +Y up, +Z toward the viewer
# Minecraft mirrors the left hand itself (negates x translation and y/z rotation), so both hands
# get identical authored values; a pre-mirrored left hand would be mirrored twice and point inward.
# Model axes: blade runs along +Y, the cutting edge faces -X, the flats face +-Z.
def _norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return [c / l for c in v]


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _orient(tip, edge_hint):
    """Euler XYZ (degrees) of the rotation taking model +Y to tip and model -X toward edge_hint."""
    d = _norm(tip)
    e = [edge_hint[i] - d[i] * sum(edge_hint[k] * d[k] for k in range(3)) for i in range(3)]
    e = _norm(e)
    x, y = [-c for c in e], d
    z = _cross(x, y)
    m = [[x[r], y[r], z[r]] for r in range(3)]       # columns are the images of the model axes
    b = math.asin(max(-1, min(1, m[0][2])))
    a = math.atan2(-m[1][2], m[2][2])
    c = math.atan2(-m[0][1], m[0][0])
    return [round(math.degrees(v), 2) for v in (a, b, c)], m


def _place(m, grip, scale, offset):
    v = [0, (grip - 8) * scale, 0]
    r = [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]
    return [round(-r[i] + offset[i], 3) for i in range(3)]


def _pose(tip, edge, grip, scale, offset=(0, 0, 0), flat=None):
    if flat is not None:                # choose the edge so the blade's flat faces `flat`
        edge = _cross(_norm(flat), _norm(tip))
    rot, m = _orient(tip, edge)
    return {'rotation': rot, 'translation': _place(m, grip, scale, offset), 'scale': [scale] * 3}


# Drawn, third person: the blade leaves the fist straight forward, square to the hanging forearm
# (the arm hangs slightly forward, so "square" lifts the tip a little), edge to the ground. No
# sideways splay, so front, side and back views all agree with the first-person hold.
THIRD_DRAWN = dict(tip=(math.cos(math.radians(14)), 0, math.sin(math.radians(14))), edge=(0, 0, -1), scale=.82)
# Drawn, first person: grip at the lower right, tip up and away toward the centre, the flat of
# the blade turned toward the viewer so it reads as a katana rather than a needle.
FIRST_DRAWN = dict(tip=(-.2, .55, -.81), flat=(.62, .28, .73), scale=.48)
# Sheathed (charging the draw): scabbard runs from the right hand across toward the left hip,
# edge up as a sword is worn; the iaido arm pose turns the body into it.
THIRD_SHEATHED = dict(tip=(-.82, .56, -.16), edge=(0, 0, 1), scale=.82)
FIRST_SHEATHED = dict(tip=(-1, -.04, -.5), edge=(0, 1, 0), scale=.48)


def display(grip, sheathed=False):
    third = THIRD_SHEATHED if sheathed else THIRD_DRAWN
    first = FIRST_SHEATHED if sheathed else FIRST_DRAWN
    d = {}
    for ctx, cfg in (('thirdperson', third), ('firstperson', first)):
        pose = _pose(cfg['tip'], cfg.get('edge'), grip, cfg['scale'], flat=cfg.get('flat'))
        for hand in ('righthand', 'lefthand'):
            d[f'{ctx}_{hand}'] = pose
    d['gui'] = {'rotation': [0, 0, -38], 'translation': [0, -5, 0], 'scale': [.35] * 3}
    d['ground'] = {'rotation': [0, 0, 90], 'translation': [0, 2, 0], 'scale': [.4] * 3}
    d['fixed'] = {'rotation': [0, 180, -35], 'translation': [0, -5, 0], 'scale': [.5] * 3}
    return d


# ---------------------------------------------------------------- geometry helpers
def box(x1, y1, z1, x2, y2, z2, tex, rot=None, light=0, uv=None):
    d = {'from': [round(x1, 3), round(y1, 3), round(z1, 3)], 'to': [round(x2, 3), round(y2, 3), round(z2, 3)],
         'faces': {f: {'texture': '#' + tex, 'uv': list(uv or [0, 0, 16, 16])}
                   for f in ['north', 'south', 'east', 'west', 'up', 'down']}}
    if rot:
        d['rotation'] = {'origin': [round(c, 3) for c in rot[0]], 'axis': rot[1], 'angle': round(rot[2], 3)}
    if light:
        d['light_emission'] = light
    return d


class Blade:
    def __init__(self, b):
        self.b = b

    def spine(self, i):
        b = self.b
        y = b['y0'] + i * b['seg']
        t = y - b['y0']
        return 8 + b['curve'] * t * t + b['width'] / 2, y, -math.degrees(math.atan(2 * b['curve'] * t))

    def build(self):
        b = self.b
        out, edge, glow, sheath, cover = [], [], [], [], []
        n = b['segments']
        for i in range(n):
            sx, y, ang = self.spine(i)
            rot = ([sx, y, 8], 'z', ang)
            # Kissaki: the edge rises to meet a straight spine over the last segments.
            tip = b.get('tip', 9)
            w = b['width'] * (min(1, (n - i) / tip) ** .8)
            th = b['thick'] / 2
            e = box(sx - w, y - .015, 8 - th, sx, y + b['seg'] + .015, 8 + th, 'steel', rot)
            for f in ('north', 'south'):
                e['faces'][f]['uv'] = [0, i * 16 / n, 16, (i + 1) * 16 / n]
            e['faces']['east']['texture'] = '#spine'
            e['faces']['west']['texture'] = '#edge'
            out.append(e)
            ew = min(b.get('edge_w', .08), w * .25)
            edge.append(box(sx - w, y - .015, 8 - th - .005, sx - w + ew, y + b['seg'] + .015, 8 + th + .005, 'edge', rot,
                            b.get('edge_light', 2)))
            if b.get('groove') and i < n - tip - 2 and i > 1:
                gx = sx - b['width'] * .38
                for z in (8 - th - .012, 8 + th + .002):
                    glow.append(box(gx - .11, y - .01, z, gx + .11, y + b['seg'] + .01, z + .01, 'glow', rot,
                                    b['groove']))
            if b.get('string') and i < n - 2:
                glow.append(box(sx + .14, y - .02, 7.97, sx + .22, y + b['seg'] + .02, 8.03, 'glow', rot, 15))
            # Scabbard follows the same curve with a margin.
            m = .17
            sheath.append(box(sx - b['width'] - m, y - .015, 8 - th - .34, sx + m, y + b['seg'] + .015, 8 + th + .34,
                              'saya', rot))
            cover.append((sx - b['width'] - m, sx + m, y, rot, 8 - th - .34, 8 + th + .34, i))
        return out, edge, glow, sheath, cover


def hilt(length, cfg):
    """Tsuka from y=-length to 0: core, crossing wrap, menuki, fuchi and kashira."""
    w = cfg.get('grip_w', 1.16)
    x1, x2 = 8 - w / 2, 8 + w / 2
    els = [box(x1, -length, 7.5, x2, .6, 8.5, 'ink'),
           box(x1 - .12, -length - .1, 7.36, x2 + .12, -length + .45, 8.64, 'metal'),
           box(x1 + .05, -length - .26, 7.46, x2 - .05, -length - .08, 8.54, 'metal')]
    steps = int(length / 1.06)
    for i in range(steps):
        y = -length + .75 + i * 1.06
        for z in (7.39, 8.51):
            els.append(box(x1 + .14, y - .23, z, x2 - .14, y + .23, z + .1, 'silk', ([8, y, z], 'z', 45)))
            for a in (-37, 37):
                els.append(box(x1 - .22, y - .09, z - .025, x2 + .22, y + .09, z + .13, 'wrap', ([8, y, z], 'z', a)))
    for y in (-length * .68, -length * .36):
        for z in (7.28, 8.63):
            els.append(box(7.82, y, z, 8.18, y + .34, z + .08, 'inlay', None, cfg.get('menuki_light', 0)))
    els += [box(x1 - .14, .2, 7.36, x2 + .14, .62, 8.64, 'metal')]
    return els


def guard(kind, y=.62):
    """Tsuba in the XZ plane at height y..y+.36, centred on (8, 8)."""
    els, t = [], .36
    if kind == 'disc':          # Oborotsuki: round pearl disc with a gold crescent inlay
        r, rz = 1.7, 1.42
        for k in range(12):
            z = -rz + (k + .5) * 2 * rz / 12
            half = r * math.sqrt(max(0, 1 - (z / rz) ** 2))
            els.append(box(8 - half, y, 8 + z - rz / 12, 8 + half, y + t, 8 + z + rz / 12, 'metal'))
        for k in range(9):
            a = math.radians(200 + k * 17)
            cx, cz = 8 + math.cos(a) * 1.18, 8 + math.sin(a) * .98
            els.append(box(cx - .2, y + t, cz - .14, cx + .2, y + t + .06, cz + .14, 'inlay', ([cx, y, cz], 'y', -math.degrees(a)), 6))
            els.append(box(cx - .2, y - .06, cz - .14, cx + .2, y, cz + .14, 'inlay', ([cx, y, cz], 'y', -math.degrees(a)), 6))
    elif kind == 'star':        # Raikiri: six jagged thunder spikes around a copper core
        els.append(box(7.15, y, 7.25, 8.85, y + t, 8.75, 'metal'))
        for k in range(6):
            a = k * 60 + 30
            els.append(box(8.4, y + .02, 7.72, 9.75, y + t - .02, 8.28, 'metal', ([8, y, 8], 'y', a)))
            els.append(box(9.6, y + .06, 7.86, 10.55, y + t - .06, 8.14, 'inlay', ([8, y, 8], 'y', a + 9), 9))
    elif kind == 'snowflake':   # Frostmoon: six arms with forked branches
        els.append(box(7.25, y, 7.3, 8.75, y + t, 8.7, 'metal'))
        for k in range(6):
            a = k * 60
            els.append(box(8.3, y + .04, 7.84, 10.3, y + t - .04, 8.16, 'metal', ([8, y, 8], 'y', a)))
            ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
            for side in (-1, 1):
                bx, bz = 8 + ca * 1.35, 8 - sa * 1.35
                els.append(box(bx, y + .07, bz - .09, bx + .62, y + t - .07, bz + .09, 'crystal', ([bx, y, bz], 'y', a + side * 48), 7))
    elif kind == 'halo':        # Voidstring: an open gold halo with two orbiting beads
        r = 1.85
        for k in range(18):
            a = k * 20
            cx, cz = 8 + math.cos(math.radians(a)) * r, 8 + math.sin(math.radians(a)) * r
            els.append(box(cx - .36, y + .05, cz - .13, cx + .36, y + t - .05, cz + .13, 'metal', ([cx, y, cz], 'y', -a - 90)))
        els.append(box(7.35, y, 7.45, 8.65, y + t, 8.55, 'metal'))
        for a in (40, 220):
            cx, cz = 8 + math.cos(math.radians(a)) * 2.45, 8 + math.sin(math.radians(a)) * 2.45
            els.append(box(cx - .19, y + .02, cz - .19, cx + .19, y + .4, cz + .19, 'glow', None, 15))
    els.append(box(7.2, y + t, 7.62, 8.8, y + t + 1.0, 8.38, 'metal'))      # habaki collar
    els.append(box(7.24, y + t + .8, 7.6, 8.76, y + t + 1.0, 8.4, 'edge'))
    return els


def scabbard_motif(kind, cover, length_hint):
    els = []
    n = len(cover)
    for (x1, x2, y, rot, z1, z2, i) in cover:
        if i in (0, 1):
            els.append(box(x1 - .05, y, z1 - .05, x2 + .05, y + .4, z2 + .05, 'metal', rot))
        if i >= n - 3:
            els.append(box(x1 - .04, y, z1 - .04, x2 + .04, y + .5, z2 + .04, 'metal', rot))
        cx = (x1 + x2) / 2
        if kind == 'phases' and i % 7 == 4 and 3 < i < n - 4:
            for z in (z1 - .03, z2):
                els.append(box(cx - .3, y, z, cx + .3, y + .5, z + .03, 'inlay', rot, 5))
        if kind == 'bolts' and 2 < i < n - 4:
            a = 32 if (i // 2) % 2 == 0 else -32
            for z in (z1 - .03, z2):
                els.append(box(cx - .08, y - .1, z, cx + .08, y + .62, z + .03, 'inlay', ([cx, y + .25, z], 'z', a), 4))
        if kind == 'rings' and i % 8 == 5 and i < n - 4:
            for z in (z1 - .03, z2):
                for dx, dy, w, h in ((-.36, 0, .1, .7), (.26, 0, .1, .7), (-.3, -.1, .6, .1), (-.3, .6, .6, .1)):
                    els.append(box(cx + dx, y + dy, z, cx + dx + w, y + dy + h, z + .03, 'inlay', rot, 6))
        if kind == 'strings' and 2 < i < n - 3:
            for dx in (-.32, 0, .32):
                for z in (z1 - .02, z2):
                    els.append(box(cx + dx - .03, y - .01, z, cx + dx + .03, y + .51, z + .02, 'inlay', rot, 7))
            if i % 9 == 3:
                for z in (z1 - .03, z2):
                    els.append(box(cx + .4, y + .2, z, cx + .52, y + .32, z + .03, 'glow', rot, 15))
    # Sageo cord wraps under the mouth.
    for k in range(4):
        x1, x2, y, rot, z1, z2, _ = cover[3]
        els.append(box(x1 - .12, y + k * .4, z1 - .12, x2 + .12, y + k * .4 + .22, z2 + .12, 'wrap', rot))
    return els


# ---------------------------------------------------------------- textures
def steel_texture(name, base, hamon, edge, kind, seed):
    rnd = random.Random(seed)
    w, h, pix = 64, 256, []
    stars = {(rnd.randrange(14, 64), rnd.randrange(256)) for _ in range(90)} if kind == 'void' else set()
    for y in range(h):
        for x in range(w):
            u = x / 63
            if kind == 'moon':
                wave = .28 + .05 * math.sin(y * .09) + .02 * math.sin(y * .31)
            elif kind == 'storm':
                zig = (y % 22) / 22
                wave = .3 + .09 * (zig * 2 if zig < .5 else 2 - zig * 2) - .045
            elif kind == 'frost':
                wave = .32 + .03 * math.sin(y * .5) * math.sin(y * .07)
            else:
                wave = .24 + .02 * math.sin(y * .05)
            shine = math.exp(-((u - .6) / .15) ** 2)
            line = math.exp(-((u - wave) / (.03 if kind != 'storm' else .022)) ** 2)
            if u < .14:
                c = edge
            else:
                c = mix(base, (255, 255, 255), .35 * shine)
                if u < wave:
                    c = mix(c, hamon, .45 if kind != 'void' else .25)
                c = mix(c, hamon, line)
            if kind == 'frost' and u > .2 and (x * 7 + y * 3) % 23 == 0:
                c = mix(c, (255, 255, 255), .7)
            if kind == 'void':
                neb = .5 + .5 * math.sin(y * .045 + x * .07) * math.sin(y * .013)
                c = mix(c, hamon, .22 * neb)
                if (x, y) in stars:
                    c = (250, 244, 255)
            grain = ((x * 5 + y * 13) % 7 - 3) * .6
            pix.extend([clamp(v + grain) for v in c] + [255])
    png(f'textures/item/{name}_steel.png', w, h, pix)


def flat(name, part, color, pattern='grain', alpha=255):
    pix = []
    for y in range(16):
        for x in range(16):
            c = color
            if pattern == 'grain':
                d = ((x * 3 + y * 7) % 5) - 2
                c = tuple(v + d * 2 for v in color)
            elif pattern == 'lacquer':
                g = math.exp(-((x - 5) / 3) ** 2) * 28
                c = tuple(v + g for v in color)
            elif pattern == 'ray':
                c = tuple(v - (14 if (x % 3 == 0 and y % 3 == 1) else 0) for v in color)
            elif pattern == 'glow':
                g = 1 - abs(x - 7.5) / 9
                c = tuple(v * (.75 + .25 * g) + 50 * g for v in color)
            pix.extend([clamp(v) for v in c] + [alpha])
    png(f'textures/item/{name}_{part}.png', 16, 16, pix)


def icon(name, pal, guard_kind, length=1.0):
    n = 64
    pix = [0] * (n * n * 4)

    def dot(x, y, c, r=0):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                X, Y = int(x + dx), int(y + dy)
                if 0 <= X < n and 0 <= Y < n:
                    k = (Y * n + X) * 4
                    pix[k:k + 4] = [clamp(c[0]), clamp(c[1]), clamp(c[2]), 255]

    steps = int(44 * length)
    for i in range(steps):
        t = i / max(1, steps - 1)
        x = 17 + 42 * length * t + 3 * t * t
        y = 47 - 43 * length * t
        dot(x, y, pal['outline'], 1)
    for i in range(steps):
        t = i / max(1, steps - 1)
        x = 17 + 42 * length * t + 3 * t * t
        y = 47 - 43 * length * t
        if t < .97:
            dot(x - 1, y, pal['edge'])
            dot(x, y, pal['steel'])
            if i % 3 == 0 or pal.get('hamon_dense'):
                dot(x, y + 1 if t < .9 else y, pal['hamon'])
    for i in range(14):
        x, y = 16 - i, 48 + i
        dot(x, y, pal['outline'], 1)
        dot(x, y, pal['wrap'] if i % 3 else pal['silk'])
    dot(3, 61, pal['metal'], 1)
    gx, gy = 17, 47
    if guard_kind == 'disc':
        for a in range(0, 360, 12):
            dot(gx + 3.5 * math.cos(math.radians(a)), gy + 3.5 * math.sin(math.radians(a)), pal['metal'])
        for a in range(200, 340, 20):
            dot(gx + 2 * math.cos(math.radians(a)), gy + 2 * math.sin(math.radians(a)), pal['inlay'])
    elif guard_kind == 'star':
        for k in range(6):
            a = math.radians(k * 60 + 15)
            for r in range(1, 6):
                dot(gx + r * math.cos(a), gy + r * math.sin(a), pal['metal'] if r < 4 else pal['inlay'])
    elif guard_kind == 'snowflake':
        for k in range(6):
            a = math.radians(k * 60)
            for r in range(1, 6):
                dot(gx + r * math.cos(a), gy + r * math.sin(a), pal['metal'])
            for s in (-1, 1):
                dot(gx + 3 * math.cos(a) + 1.5 * math.cos(a + s), gy + 3 * math.sin(a) + 1.5 * math.sin(a + s), pal['inlay'])
    elif guard_kind == 'halo':
        for a in range(0, 360, 10):
            dot(gx + 4.5 * math.cos(math.radians(a)), gy + 4.5 * math.sin(math.radians(a)), pal['metal'])
        dot(gx + 6, gy - 4, pal['glow'])
        dot(gx - 6, gy + 4, pal['glow'])
    png(f'textures/item/{name}_icon.png', n, n, pix)


# ---------------------------------------------------------------- the four redesigned blades
BLADES = {
    'wakizashi': dict(models=('wakizashi', 'waki_sheathed', 'short_scabbard', 'waki_icon'), charged=False,
                      blade=dict(y0=1.98, seg=.5, segments=34, width=1.18, curve=.0058, thick=.24, tip=8, edge_light=6),
                      grip=6.4, guard='disc', motif='phases', icon_len=.78, kind='moon', seed=11,
                      pal=dict(steel=(206, 210, 230), hamon=(198, 172, 255), edge=(250, 250, 255), spine=(118, 116, 150),
                               ink=(236, 232, 244), wrap=(118, 92, 178), silk=(170, 150, 222), metal=(220, 222, 236),
                               saya=(232, 228, 242), inlay=(232, 202, 128), glow=(206, 182, 255), crystal=(220, 210, 255),
                               outline=(40, 34, 64))),
    'raikiri': dict(models=('raikiri_tachi', 'raikiri_sheathed', 'raikiri_long_scabbard', 'raikiri_icon'), charged=True,
                    blade=dict(y0=1.98, seg=.5, segments=54, width=1.32, curve=.0026, thick=.28, tip=8, edge_light=9,
                               groove=12),
                    grip=10.0, guard='star', motif='bolts', icon_len=.94, kind='storm', seed=23,
                    pal=dict(steel=(66, 68, 80), hamon=(255, 92, 44), edge=(255, 216, 168), spine=(34, 34, 42),
                             ink=(26, 22, 24), wrap=(170, 26, 40), silk=(214, 62, 50), metal=(208, 138, 74),
                             saya=(136, 16, 28), inlay=(244, 192, 92), glow=(255, 92, 40), crystal=(255, 180, 90),
                             outline=(28, 12, 14))),
    'frostmoon': dict(models=('frostmoon_tachi', 'frostmoon_sheathed', 'frostmoon_long_scabbard', 'frostmoon_icon'),
                      charged=True,
                      blade=dict(y0=1.98, seg=.5, segments=59, width=1.62, curve=.0047, thick=.26, tip=10, edge_light=8),
                      grip=10.6, guard='snowflake', motif='rings', icon_len=1.0, kind='frost', seed=37, shards=True,
                      pal=dict(steel=(178, 214, 236), hamon=(244, 252, 255), edge=(232, 250, 255), spine=(94, 138, 170),
                               ink=(22, 34, 66), wrap=(150, 210, 240), silk=(212, 238, 255), metal=(228, 238, 248),
                               saya=(20, 32, 62), inlay=(222, 242, 255), glow=(128, 230, 255), crystal=(184, 240, 255),
                               outline=(14, 26, 48))),
    'voidstring': dict(models=('voidstring_tachi', 'voidstring_sheathed', 'voidstring_long_scabbard', 'voidstring_icon'),
                       charged=True,
                       blade=dict(y0=1.98, seg=.5, segments=59, width=1.04, curve=.0034, thick=.22, tip=12, edge_light=5,
                                  string=True),
                       grip=10.4, guard='halo', motif='strings', icon_len=1.0, kind='void', seed=53,
                       pal=dict(steel=(30, 22, 46), hamon=(172, 112, 255), edge=(222, 204, 255), spine=(12, 8, 20),
                                ink=(14, 12, 18), wrap=(192, 160, 92), silk=(222, 188, 112), metal=(234, 208, 142),
                                saya=(16, 14, 22), inlay=(228, 198, 122), glow=(188, 122, 255), crystal=(210, 170, 255),
                                outline=(8, 4, 16))),
}


def ice_shards(blade_b):
    """Small ice crystals growing from the base of the Frostmoon blade."""
    els = []
    for k, (dy, a, length) in enumerate([(.4, 34, 1.5), (1.2, -28, 1.2), (2.1, 40, .9), (.8, 150, 1.0)]):
        y = blade_b['y0'] + dy
        x = 8 + (blade_b['width'] / 2 if a < 90 else -blade_b['width'] / 2)
        els.append(box(x - .14, y, 7.86, x + .14, y + length, 8.14, 'crystal', ([x, y, 8], 'z', a), 9))
    return els


def build(name, cfg):
    pal, bcfg = cfg['pal'], cfg['blade']
    tex = lambda part: f'oborokage:item/{name}_{part}'
    steel_texture(name, pal['steel'], pal['hamon'], pal['edge'], cfg['kind'], cfg['seed'])
    flat(name, 'edge', pal['edge'])
    flat(name, 'spine', pal['spine'])
    flat(name, 'ink', pal['ink'], 'ray')
    flat(name, 'wrap', pal['wrap'])
    flat(name, 'silk', pal['silk'])
    flat(name, 'metal', pal['metal'], 'lacquer')
    flat(name, 'saya', pal['saya'], 'lacquer')
    flat(name, 'inlay', pal['inlay'], 'lacquer')
    flat(name, 'glow', pal['glow'], 'glow')
    flat(name, 'crystal', pal['crystal'], 'glow')
    textures = {p: tex(p) for p in ['steel', 'edge', 'spine', 'ink', 'wrap', 'silk', 'metal', 'saya', 'inlay', 'glow',
                                     'crystal']}
    textures['particle'] = tex('metal')
    body, edge, glow, sheath, cover = Blade(bcfg).build()
    grip_els = hilt(cfg['grip'], {'menuki_light': 4 if cfg['kind'] in ('void', 'storm') else 0})
    tsuba = guard(cfg['guard'])
    extra = ice_shards(bcfg) if cfg.get('shards') else []
    motif = scabbard_motif(cfg['motif'], cover, bcfg['segments'])
    drawn = grip_els + tsuba + body + edge + glow + extra
    sheathed = grip_els + tsuba + sheath + motif
    grip_y = -cfg['grip'] * .42
    held, sheath_model, waist, icon_model = cfg['models']
    save(f'models/item/{held}.json', {'textures': textures, 'elements': drawn, 'display': display(grip_y), 'gui_light': 'front'})
    save(f'models/item/{sheath_model}.json',
         {'textures': textures, 'elements': sheathed, 'display': display(grip_y, sheathed=True), 'gui_light': 'front'})
    ground = {'scale': [.78] * 3, 'translation': [0, 0, 0]}
    wd = display(grip_y, sheathed=True)
    wd['ground'] = ground
    save(f'models/item/{waist}.json', {'textures': textures, 'elements': sheath + motif, 'display': wd, 'gui_light': 'front'})
    if cfg['charged']:
        for light in (4, 8, 12, 15):
            ring = []
            for (x1, x2, y, rot, z1, z2, i) in cover:
                ring.append(box(x1 - .2, y, z1 - .02, x1 - .1, y + bcfg['seg'], z2 + .02, 'glow', rot, light))
            save(f'models/item/{name}_charged{light}.json',
                 {'textures': textures, 'elements': sheathed + ring, 'display': display(grip_y, sheathed=True),
                  'gui_light': 'front'})
    save(f'models/item/{icon_model}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'oborokage:item/{icon_model.replace("_icon", "")}_icon' if name != 'wakizashi' else 'oborokage:item/wakizashi_icon'}})
    icon(name, pal, cfg['guard'], cfg['icon_len'])
    return len(drawn), len(sheathed)


def refit_tachi():
    """Keep the Oborokage tachi's own look; only correct how it is held."""
    for model in ['tachi', 'sheathed', 'charged4', 'charged8', 'charged12', 'charged15', 'long_scabbard']:
        p = ROOT / f'models/item/{model}.json'
        j = json.loads(p.read_text())
        d = display(-4.4, sheathed=model != 'tachi')
        if model == 'long_scabbard':
            d['ground'] = j['display'].get('ground', d['ground'])
        j['display'] = d
        save(f'models/item/{model}.json', j)


if __name__ == '__main__':
    for name, cfg in BLADES.items():
        print(name, build(name, cfg))
    refit_tachi()
    save('models/item/waki_icon.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'oborokage:item/wakizashi_icon'}})
