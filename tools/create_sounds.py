"""Original sounds for the 樱下鬼 (Oni Beneath the Blossoms), synthesized from noise and tones:
- ambient: a breathy whisper over a low, wavering moan, with a petal-light chime now and then;
- hurt: a short cracked sob, a brittle crack like old lacquer splitting under it;
- death: a long falling wail that thins into breath, then a last fading shimmer.

Needs numpy and soundfile (libsndfile with Ogg Vorbis), e.g. in a throwaway virtualenv:
  python3 -m venv /tmp/v && /tmp/v/bin/pip install numpy soundfile && /tmp/v/bin/python tools/create_sounds.py
Writes assets/oborokage/sounds/blossom_oni/*.ogg and assets/oborokage/sounds.json.
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/oborokage'
OUT = ROOT / 'sounds/blossom_oni'
RATE = 44100


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def band(x, lo, hi):
    """Band-pass by zeroing the spectrum outside lo..hi Hz, with soft edges."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    w = np.clip((f - lo * .7) / (lo * .3 + 1e-9), 0, 1) * np.clip((hi * 1.3 - f) / (hi * .3), 0, 1)
    return np.fft.irfft(spec * w, len(x))


def env(n, attack, release, shape=1.0):
    e = np.ones(n)
    a, r = int(attack * RATE), int(release * RATE)
    if a:
        e[:a] = np.linspace(0, 1, a) ** shape
    if r:
        e[-r:] *= np.linspace(1, 0, r) ** shape
    return e


def tone(freq, seconds, vibrato=0.0, rate=5.0, seed=0):
    """A voice-like tone: a fundamental with a few softer partials, its pitch wavering."""
    rng = np.random.default_rng(seed)
    tt = t(seconds)
    f = np.broadcast_to(freq, tt.shape) * (1 + vibrato * np.sin(2 * np.pi * rate * tt + rng.uniform(0, 6)))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase) + .45 * np.sin(2 * phase + .3) + .2 * np.sin(3 * phase + 1.1) + .08 * np.sin(5 * phase)


def chime(freq, seconds, decay):
    tt = t(seconds)
    return (np.sin(2 * np.pi * freq * tt) + .35 * np.sin(2 * np.pi * freq * 2.76 * tt)) * np.exp(-tt / decay)


def crack(seconds, seed):
    """A brittle split: a few tight clicks of bright noise."""
    rng = np.random.default_rng(seed)
    n = int(RATE * seconds)
    x = np.zeros(n)
    for start in rng.uniform(0, seconds * .35, 4):
        i = int(start * RATE)
        k = min(n - i, int(.012 * RATE))
        x[i:i + k] += rng.normal(0, 1, k) * np.exp(-np.arange(k) / (k / 5))
    return band(x, 2500, 9000)


def finish(x, gain=.7):
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) or 1
    return (x / peak * gain).astype(np.float32)


def ambient(seed):
    rng = np.random.default_rng(seed)
    d = 1.9
    n = int(RATE * d)
    breath = band(rng.normal(0, 1, n), 500, 2600) * (.55 + .45 * np.sin(2 * np.pi * .9 * t(d) + seed))
    moan = tone(np.linspace(150 + 20 * seed, 118, n), d, vibrato=.025, rate=4.3, seed=seed)
    moan = band(moan, 80, 1400)
    x = .9 * breath / np.max(np.abs(breath)) + .55 * moan / np.max(np.abs(moan))
    x *= env(n, .45, .9, 1.6)
    ping = np.zeros(n)
    at = int(RATE * rng.uniform(.6, 1.1))
    c = chime(1760 + 220 * seed, d - at / RATE, .35)
    ping[at:at + len(c)] = c[:n - at]
    return finish(x + .18 * ping, .55)


def hurt(seed):
    rng = np.random.default_rng(seed)
    d = .42
    n = int(RATE * d)
    sob = tone(np.linspace(420 + 30 * seed, 230, n), d, vibrato=.06, rate=23, seed=seed)
    sob = band(sob, 150, 3000) * (.6 + .4 * np.abs(np.sin(2 * np.pi * 11 * t(d))))
    rasp = band(rng.normal(0, 1, n), 900, 4000) * .5
    x = (sob / np.max(np.abs(sob)) + rasp) * env(n, .01, .3, 1.3) + .8 * crack(d, seed)
    return finish(x, .75)


def death():
    rng = np.random.default_rng(9)
    d = 2.6
    n = int(RATE * d)
    wail = tone(np.geomspace(330, 82, n), d, vibrato=.035, rate=5.5, seed=9)
    wail = band(wail, 60, 2500) * env(n, .05, 1.4, 1.8)
    breath = band(rng.normal(0, 1, n), 400, 2200) * np.linspace(.2, 1, n) * env(n, .3, .8)
    x = wail / np.max(np.abs(wail)) + .6 * breath / np.max(np.abs(breath))
    shimmer = np.zeros(n)
    for k, f in enumerate([2093, 2637, 3136]):
        at = int(RATE * (1.5 + .12 * k))
        c = chime(f, d - at / RATE, .5)
        shimmer[at:at + len(c)] += c[:n - at]
    return finish(x + .22 * shimmer, .7)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = {'ambient': [ambient(i) for i in range(2)], 'hurt': [hurt(i) for i in range(3)],
             'death': [death()]}
    sounds = {}
    for kind, clips in files.items():
        names = []
        for i, clip in enumerate(clips, 1):
            path = OUT / f'{kind}{i}.ogg'
            sf.write(path, clip, RATE, format='OGG', subtype='VORBIS')
            names.append(f'oborokage:blossom_oni/{kind}{i}')
        sounds[f'entity.blossom_oni.{kind}'] = {
            'subtitle': f'subtitles.oborokage.entity.blossom_oni.{kind}',
            'sounds': names}
    (ROOT / 'sounds.json').write_text(json.dumps(sounds, indent=2) + '\n')
    print('sounds written:', sum(len(c) for c in files.values()))


if __name__ == '__main__':
    main()
