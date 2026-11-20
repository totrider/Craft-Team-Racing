"""
The mod's own stand-in jingles for the race (used only when no resource pack brings the original's): the intro jingle that plays while the camera pans over the grid (about 7.8 s), a winner's
fanfare for the driver who finishes first, and a loser's lament for everybody else. Original compositions, synthesised here (pulse and saw leads, a bass, an arpeggio and drums), stereo.

  python tools/make_jingles.py        (also run by tools/make_sounds.py)

Writes sounds/music/standin_race_intro.ogg, standin_race_win.ogg and standin_race_lose.ogg; tools/make_sounds.py puts them in sounds.json as music.standin.race_intro, .race_win and .race_lose,
which client/RaceMusic plays when the event of the original's jingle has no sound.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oggenc  # noqa: E402

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "craftteamracing", "sounds", "music")
RNG = np.random.default_rng(31)


def hz(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def tx(seconds):
    return np.arange(int(SR * seconds)) / SR


def env(n, attack=0.005, release=0.04, sustain=1.0, decay_to=None):
    e = np.ones(n) * sustain
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e[:a] *= np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r) if r < n else 1
    if decay_to is not None:
        e *= np.linspace(1.0, decay_to, n)
    return e


def pulse(f, dur, duty=0.25, gain=1.0, vib=0.0, slide=0.0):
    t = tx(dur)
    ff = f * (1.0 + vib * np.sin(2 * np.pi * 5.5 * t)) * (1.0 + slide * t / max(dur, 1e-6))
    ph = np.cumsum(ff) / SR
    x = np.where((ph % 1.0) < duty, 1.0, -1.0)
    return x * env(len(t), 0.004, min(0.05, dur * 0.4)) * gain * 0.5


def saw(f, dur, gain=1.0, cut=2500.0, vib=0.0, slide=0.0):
    t = tx(dur)
    ff = f * (1.0 + vib * np.sin(2 * np.pi * 5.0 * t)) * (1.0 + slide * t / max(dur, 1e-6))
    ph = np.cumsum(ff) / SR
    x = 2.0 * (ph % 1.0) - 1.0
    # a one-pole lowpass: a brass-ish tone
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y * env(len(t), 0.02, min(0.08, dur * 0.4)) * gain


def sine_bell(f, dur, gain=1.0):
    t = tx(dur)
    x = np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t) + 0.2 * np.sin(6 * np.pi * f * t)
    return x * np.exp(-t / (dur * 0.5)) * gain * 0.5


def kick(gain=1.0):
    t = tx(0.18)
    return np.sin(2 * np.pi * (45 * t + 75 * (1 - np.exp(-t * 28)) / 28)) * np.exp(-t / 0.07) * gain


def snare(gain=1.0):
    t = tx(0.16)
    n = RNG.standard_normal(len(t))
    return (n * np.exp(-t / 0.05) * 0.7 + np.sin(2 * np.pi * 190 * t) * np.exp(-t / 0.04) * 0.5) * gain


def hat(gain=1.0, open_=False):
    t = tx(0.12 if open_ else 0.04)
    n = RNG.standard_normal(len(t))
    n = n - np.concatenate([[0.0], n[:-1]]) * 0.95                                 # a cheap highpass
    return n * np.exp(-t / (0.06 if open_ else 0.015)) * 0.35 * gain


def crash(dur=1.6, gain=1.0):
    t = tx(dur)
    n = RNG.standard_normal(len(t))
    n = n - np.concatenate([[0.0], n[:-1]]) * 0.9
    return n * np.exp(-t / 0.5) * 0.35 * gain


class Mix:
    def __init__(self, seconds):
        self.n = int(SR * seconds)
        self.l = np.zeros(self.n)
        self.r = np.zeros(self.n)

    def add(self, start, wave, pan=0.0):
        i = int(start * SR)
        if i >= self.n:
            return
        w = wave[: self.n - i]
        self.l[i:i + len(w)] += w * (1.0 - max(pan, 0.0))
        self.r[i:i + len(w)] += w * (1.0 + min(pan, 0.0))

    def out(self, fade_out=0.15, peak=0.8):
        s = np.stack([self.l, self.r], axis=1)
        s = s / (np.max(np.abs(s)) + 1e-9) * peak
        f = int(SR * fade_out)
        s[-f:] *= np.linspace(1, 0, f)[:, None]
        s[: int(SR * 0.004)] *= np.linspace(0, 1, int(SR * 0.004))[:, None]
        return s


def intro():
    """7.8 s at 150 beats a minute (0.4 s a beat): E minor, a driving lead over a pulsing bass and a four on the floor: Em, C, G, D, and a last bar that lands on E with a crash."""
    beat = 0.4
    eighth = beat / 2
    m = Mix(7.8)
    lead = [
        [76, 79, 83, 79, 76, 79, 83, 86],
        [84, 79, 76, 79, 84, 79, 76, 72],
        [79, 83, 86, 83, 79, 83, 86, 91],
        [78, 81, 86, 81, 78, 81, 86, 90],
    ]
    bass = [40, 48, 43, 50]
    for bar in range(4):
        t0 = bar * 4 * beat
        for i in range(8):
            m.add(t0 + i * eighth, pulse(hz(lead[bar][i]), eighth * 0.92, 0.25, 0.9), 0.25)
            m.add(t0 + i * eighth, pulse(hz(lead[bar][i] - 12), eighth * 0.9, 0.5, 0.25), -0.3)          # an octave under, softer, on the other side
            m.add(t0 + i * eighth, saw(hz(bass[bar] + (12 if i % 4 == 3 else 0)), eighth * 0.85, 0.7, 700), 0.0)
            m.add(t0 + i * eighth, hat(0.9, open_=(i % 4 == 3)), 0.4)
        for b in range(4):
            m.add(t0 + b * beat, kick(1.0), 0.0)
            if b % 2 == 1:
                m.add(t0 + b * beat, snare(0.8), 0.0)
    t0 = 4 * 4 * beat                                                                             # the last bar
    m.add(t0, kick(1.1))
    m.add(t0, crash(2.0, 0.9), 0.2)
    for note, g in ((83, 1.0), (88, 0.8), (76, 0.8), (64, 0.7)):
        m.add(t0, pulse(hz(note), 2.2, 0.25, g * 0.8, vib=0.004), 0.2 if note > 80 else -0.2)
    m.add(t0, saw(hz(40), 2.2, 0.9, 700), 0.0)
    return m.out()


def win():
    """About 7 s at 140 beats a minute: a C major fanfare, rising arpeggios and a long, bright final chord with bells and a crash."""
    beat = 60.0 / 140.0
    eighth = beat / 2
    m = Mix(7.2)
    runs = [
        (0, [72, 76, 79, 84, 88, 84, 79, 84]),
        (4, [77, 81, 84, 89, 93, 89, 84, 89]),
        (8, [79, 83, 86, 91, 95, 91, 86, 91]),
    ]
    for start, notes in runs:
        for i, nt in enumerate(notes):
            tt = (start + i * 0.5) * beat
            m.add(tt, pulse(hz(nt), eighth * 0.95, 0.25, 0.9), 0.25)
            m.add(tt, pulse(hz(nt - 12), eighth * 0.9, 0.5, 0.3), -0.3)
            m.add(tt, hat(0.8), 0.4)
        for b in range(4):
            m.add((start + b) * beat, kick(1.0))
            if b % 2 == 1:
                m.add((start + b) * beat, snare(0.9))
        m.add(start * beat, saw(hz({0: 36, 4: 41, 8: 43}[start]), 4 * beat * 0.95, 0.7, 600), 0.0)
    t0 = 12 * beat
    m.add(t0, crash(2.6, 1.0), 0.2)
    m.add(t0, kick(1.2))
    for k, note in enumerate((84, 88, 91, 96)):
        m.add(t0, pulse(hz(note), 3.2, 0.25, 0.8, vib=0.005), -0.3 + 0.2 * k)
        m.add(t0 + 0.3 + k * 0.12, sine_bell(hz(note + 12), 1.8, 0.35), 0.3)
    m.add(t0, saw(hz(36), 3.4, 0.9, 700), 0.0)
    return m.out(0.4)


def lose():
    """About 5 s at 76 beats a minute: A minor, a slow sinking line on a brass tone (three steps down and a long last note that slides off and wobbles), over a low drone and a soft thud."""
    beat = 60.0 / 76.0
    m = Mix(5.6)
    for i, (nt, ln) in enumerate(((76, 0.9), (75, 0.9), (74, 0.9))):
        m.add(i * beat, saw(hz(nt), ln * beat, 0.9, 1800, vib=0.006), 0.15)
    m.add(3 * beat, saw(hz(73), 2.6 * beat, 0.95, 1500, vib=0.012, slide=-0.07), 0.15)
    m.add(0.0, saw(hz(45), 5.0, 0.5, 400), -0.2)
    m.add(0.0, pulse(hz(57), 5.0, 0.5, 0.12, vib=0.002), -0.2)
    m.add(0.0, kick(0.6))
    m.add(3 * beat, kick(0.5))
    return m.out(0.6, 0.75)


def write_all():
    os.makedirs(OUT, exist_ok=True)
    for name, make in (("standin_race_intro", intro), ("standin_race_win", win), ("standin_race_lose", lose)):
        oggenc.write_ogg(os.path.join(OUT, name + ".ogg"), np.clip(make(), -1, 1), SR, quality=0.4)


if __name__ == "__main__":
    write_all()
    print("jingles written")
