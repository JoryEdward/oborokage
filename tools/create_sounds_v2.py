"""1.1.0: the 朽英剑主's voice, original, synthesized like the 樱下鬼's (create_sounds.py) but lower
and slower, a breath more than a wail:
- ambient: a long, deep breath over a low hum, and now and then a temple bell far off;
- hurt: a short, low grunt of breath with a dull crack of old wood under it;
- death: a long sigh falling away to nothing, then one bell, fading.

Needs numpy and soundfile (see create_sounds.py). Writes assets/oborokage/sounds/withered_lord/*.ogg
and adds its events to assets/oborokage/sounds.json, keeping the others.
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from create_sounds import RATE, ROOT, band, env, finish, t, tone  # noqa: E402

OUT = ROOT / 'sounds/withered_lord'


def bell(freq, seconds, decay):
    """A temple bell: a low strike with inharmonic partials, the higher ones dying first."""
    tt = t(seconds)
    x = np.zeros_like(tt)
    for ratio, amp, d in [(1, 1, 1), (2.01, .5, .55), (2.76, .35, .4), (5.4, .15, .2)]:
        x += amp * np.sin(2 * np.pi * freq * ratio * tt) * np.exp(-tt / (decay * d))
    return x * (1 + .15 * np.sin(2 * np.pi * 1.7 * tt))


def thud(seconds, seed):
    """A dull crack of old wood: a short burst of low, muffled noise."""
    rng = np.random.default_rng(seed)
    n = int(RATE * seconds)
    x = np.zeros(n)
    k = int(.03 * RATE)
    x[:k] = rng.normal(0, 1, k) * np.exp(-np.arange(k) / (k / 4))
    return band(x, 300, 2200)


def ambient(seed):
    rng = np.random.default_rng(100 + seed)
    d = 2.8
    n = int(RATE * d)
    breath = band(rng.normal(0, 1, n), 250, 1600) * (.5 + .5 * np.sin(np.pi * t(d) / d) ** 1.5)
    hum = band(tone(np.linspace(96 - 8 * seed, 78, n), d, vibrato=.015, rate=2.7, seed=seed), 50, 900)
    x = (.85 * breath / np.max(np.abs(breath)) + .5 * hum / np.max(np.abs(hum))) * env(n, .7, 1.1, 1.8)
    toll = np.zeros(n)
    if seed == 0:
        at = int(RATE * 1.2)
        b = bell(196, d - at / RATE, .9)
        toll[at:] = b[:n - at]
    return finish(x + .14 * toll, .5)


def hurt(seed):
    rng = np.random.default_rng(110 + seed)
    d = .5
    n = int(RATE * d)
    grunt = band(tone(np.linspace(190 + 15 * seed, 120, n), d, vibrato=.03, rate=13, seed=seed), 70, 1800)
    rasp = band(rng.normal(0, 1, n), 400, 2500) * .55
    x = (grunt / np.max(np.abs(grunt)) + rasp) * env(n, .015, .35, 1.4) + .7 * thud(d, seed)
    return finish(x, .7)


def death():
    rng = np.random.default_rng(120)
    d = 3.6
    n = int(RATE * d)
    sigh = band(tone(np.geomspace(170, 52, n), d, vibrato=.02, rate=3.5, seed=12), 40, 1500)
    sigh *= env(n, .2, 2.2, 1.6)
    breath = band(rng.normal(0, 1, n), 250, 1800) * np.linspace(1, .15, n) * env(n, .3, 1.5)
    x = sigh / np.max(np.abs(sigh)) + .7 * breath / np.max(np.abs(breath))
    toll = np.zeros(n)
    at = int(RATE * 1.9)
    b = bell(147, d - at / RATE, 1.1)
    toll[at:] = b[:n - at]
    return finish(x + .3 * toll, .7)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = {'ambient': [ambient(i) for i in range(2)], 'hurt': [hurt(i) for i in range(2)],
             'death': [death()]}
    path = ROOT / 'sounds.json'
    sounds = json.loads(path.read_text())
    for kind, clips in files.items():
        names = []
        for i, clip in enumerate(clips, 1):
            sf.write(OUT / f'{kind}{i}.ogg', clip, RATE, format='OGG', subtype='VORBIS')
            names.append(f'oborokage:withered_lord/{kind}{i}')
        sounds[f'entity.withered_lord.{kind}'] = {
            'subtitle': f'subtitles.oborokage.entity.withered_lord.{kind}', 'sounds': names}
    path.write_text(json.dumps(sounds, indent=2) + '\n')
    print('sounds written:', sum(len(c) for c in files.values()))


if __name__ == '__main__':
    main()
