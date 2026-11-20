"""
The kart sounds: original, synthesised placeholders (CTR's recordings are Sony / Naughty Dog's and are not ours to ship).

  python tools/make_sounds.py

Writes src/main/resources/assets/craftteamracing/sounds/*.ogg and sounds.json. Needs numpy and Krita's libogg / libvorbis DLLs
(tools/oggenc.py; set KRITA_BIN if Krita is elsewhere). Every sound is mono 44.1 kHz.

The engine and skid are LOOPS (the game bends the engine's rate with CTR's own engine-sound values, so the base note sits in the
middle of the range); the rest are one-shots. To use your own recordings instead, drop .ogg files with the same names into a
resource pack at assets/craftteamracing/sounds/ (the names are in SOUNDS below); nothing else changes.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oggenc  # noqa: E402

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "craftteamracing")
rng = np.random.default_rng(1996)

SOUNDS = ["engine_loop", "skid_loop", "turbo_loop", "hop", "land", "drift_start", "boost", "boost_fail", "meter_red", "meter_full",
          "hang_time", "wall_hit", "slam", "spin_out",
          "light_count", "light_green", "fruit_pickup", "glass_shatter", "mine_drop", "tnt_hit", "tnt_land", "tnt_tick", "tnt_explode", "crate_break", "bomb_launch", "bomb_roll", "bomb_explode", "shield_on", "shield_pop", "shield_roll", "missile_launch", "missile_fly", "missile_explode", "tracker_beep", "item_roll", "item_get", "item_get_charged", "clock", "wobble_thump", "squish", "squish_restore", "burn", "crash_soft", "crash_mid", "crash_hard", "fruit_arrive", "mask_aku", "mask_uka", "warp_launch", "warp_fly", "warp_end", "lap"]


# the race music's events: empty here (the mod ships no music), a resource pack puts its files in them (see docs/MUSIC.md)
MUSIC_EVENTS = ["music.race_intro", "music.race", "music.final_lap", "music.race_end.first", "music.race_end.other", "music.aku_aku", "music.uka_uka"]


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def bandpass(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = np.exp(-(np.maximum(lo - f, 0) / (0.35 * lo + 1)) ** 2) * np.exp(-(np.maximum(f - hi, 0) / (0.35 * hi + 1)) ** 2)
    return np.fft.irfft(spec * mask, len(x))


def lowpass(x, hi):
    return bandpass(x, 0, hi)


def noise(n):
    return rng.standard_normal(n)


def decay(t, tau):
    return np.exp(-t / tau)


def fade(x, ms=4):
    n = int(SR * ms / 1000)
    x = x.copy()
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x


def norm(x, peak=0.8):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def chirp(t, f0, f1, power=1.0):
    k = (f1 - f0)
    # phase of a frequency sweep from f0 to f1 over the whole duration
    T = t[-1] + 1 / SR
    return np.sin(2 * np.pi * (f0 * t + k * T / (power + 1) * (t / T) ** (power + 1)))


def engine_loop():
    f0, cycles = 90.0, 54
    T = cycles / f0
    n = int(round(T * SR))
    t = np.arange(n) * T / n
    x = np.zeros(n)
    for k in range(1, 12):                       # a rough, buzzy harmonic stack
        x += np.sin(2 * np.pi * k * f0 * t + rng.uniform(0, 6.28)) / (k ** 1.15)
    x *= 0.78 + 0.22 * np.sin(2 * np.pi * 2 * f0 * t)           # the putt-putt of the cylinders
    for k in range(13, 90):                      # periodic "noise": many weak harmonics with random phases
        x += 0.035 * rng.uniform(0.3, 1) * np.sin(2 * np.pi * k * f0 * t + rng.uniform(0, 6.28))
    return norm(x, 0.7)


def skid_loop():
    n = SR * 1
    x = bandpass(noise(n + 8000), 1400, 4200)[: n + 8000]
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 7 * np.arange(len(x)) / SR)
    xf = 4000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.6)


def turbo_loop():
    n = int(SR * 0.65)
    x = lowpass(noise(n + 6000), 2600)
    x *= 0.85 + 0.15 * np.sin(2 * np.pi * 23 * np.arange(len(x)) / SR)
    xf = 3000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.55)


def hop():
    t = t_axis(0.22)
    x = chirp(t, 260, 620, 1.0) * decay(t, 0.09) + 0.35 * noise(len(t)) * decay(t, 0.01)
    return fade(norm(x))


def land():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (60 + 90 * decay(t, 0.04)) * t) * decay(t, 0.07)
    x += 0.5 * lowpass(noise(len(t)), 900) * decay(t, 0.03)
    return fade(norm(x))


def drift_start():
    t = t_axis(0.3)
    x = bandpass(noise(len(t)), 1800, 3800) * (np.sin(np.pi * t / 0.3) ** 0.6)
    x += 0.4 * chirp(t, 1500, 2300) * decay(t, 0.2)
    return fade(norm(x, 0.7))


def boost():
    """A boost (a drift's, a hop's): a speedy exhaust and the kart rearing back: an impact thump, a burst of exhaust whoosh that sweeps from bright to dark, and an engine revving up under it."""
    t = t_axis(0.75)
    attack = np.minimum(t / 0.012, 1.0)
    # the whoosh: noise through a band that falls from 6 kHz to 700 Hz as it dies away
    n = noise(len(t))
    bright = bandpass(n, 2500, 7500) * decay(t, 0.09)
    mid = bandpass(n, 700, 2600) * decay(t, 0.22)
    whoosh = (bright * 0.8 + mid) * attack
    # the rear-up: a low body that drops, and the engine's revs climbing (a rough sawtooth, 90 to 330 Hz)
    thump = np.sin(2 * np.pi * chirp_phase(t, 150, 55)) * decay(t, 0.09)
    revs = np.zeros(len(t))
    for h in range(1, 7):
        revs += np.sin(2 * np.pi * h * chirp_phase(t, 90, 330)) / h
    revs *= np.minimum(t / 0.05, 1.0) * decay(np.maximum(t - 0.05, 0.0), 0.28) * 0.35
    return fade(norm(whoosh + 0.9 * thump + revs, 0.95))

def boost_fail():
    """A failed boost: a short puff of exhaust, soft and airy, a little low thud: no tone."""
    t = t_axis(0.22)
    puff = lowpass(noise(len(t)), 2200) * np.minimum(t / 0.01, 1.0) * decay(t, 0.05)
    air = bandpass(noise(len(t)), 800, 3800) * np.minimum(t / 0.02, 1.0) * decay(t, 0.09) * 0.45
    thud = np.sin(2 * np.pi * chirp_phase(t, 120, 70)) * decay(t, 0.03) * 0.5
    return fade(norm(puff + air + thud, 0.7))

def meter_red():
    t = t_axis(0.12)
    x = np.sin(2 * np.pi * 1318 * t) * decay(t, 0.05)
    return fade(norm(x, 0.55))


def meter_full():
    """The drift meter full and no boost taken in time: modelled on measurements of the original (470 ms). Its waveform is noise (kurtosis 3.35, where a tone is 1.5), not a tone: a heavy low
    rumble (strongest at 110 to 140 Hz) with a hiss over it, a level that jumps at random every 2 ms, steady for 300 ms and then dying over 150 ms, and a brighter puff in the first 100 ms
    before it settles. An engine's sputter, a puff out of the exhaust; no buzzer."""
    dur = 0.47
    t = t_axis(dur)
    n = len(t)
    rumble = bandpass(noise(n), 60, 260)
    rumble = rumble / (np.max(np.abs(rumble)) + 1e-9)
    hiss = bandpass(noise(n), 500, 4200)
    hiss = hiss / (np.max(np.abs(hiss)) + 1e-9) * 0.30
    puff = bandpass(noise(n), 350, 1800) * np.exp(-t / 0.055)
    puff = puff / (np.max(np.abs(puff)) + 1e-9) * 0.9
    # the level jumps at random every 2 ms (between 0.35 and 1.25 of its mean), smoothed a little so it crackles rather than clicks
    steps = np.random.default_rng(17).uniform(0.65, 1.15, int(dur / 0.002) + 2)
    grain = np.interp(t, np.arange(len(steps)) * 0.002, steps)
    env = np.where(t < 0.30, 1.0, np.exp(-(t - 0.30) / 0.075)) * np.minimum(t / 0.006, 1.0)
    return fade(norm((rumble * 1.0 + hiss) * env * grain + puff * np.minimum(t / 0.004, 1.0), 0.8))

def hang_time():
    """The landing boost (hang time): in the original this is the very same sound as the boost (the two files are identical sample for sample), so it is the boost's."""
    return boost()

def wall_hit():
    t = t_axis(0.25)
    x = bandpass(noise(len(t)), 300, 2500) * decay(t, 0.05) + 0.7 * np.sin(2 * np.pi * 130 * t) * decay(t, 0.08)
    return fade(norm(x))


def slam():
    t = t_axis(0.55)
    x = np.sin(2 * np.pi * (45 + 80 * decay(t, 0.06)) * t) * decay(t, 0.18)
    x += 0.8 * lowpass(noise(len(t)), 1800) * decay(t, 0.07)
    x += 0.3 * bandpass(noise(len(t)), 2000, 6000) * decay(t, 0.02)
    return fade(norm(x, 0.9))


def spin_out():
    t = t_axis(0.7)
    x = chirp(t, 900, 260, 0.8) * decay(t, 0.45) * (0.7 + 0.3 * np.sin(2 * np.pi * 14 * t))
    x += 0.25 * bandpass(noise(len(t)), 1500, 3500) * decay(t, 0.3)
    return fade(norm(x, 0.7))


def light_count():
    t = t_axis(0.28)
    x = np.sin(2 * np.pi * 440 * t) * decay(t, 0.14) + 0.3 * np.sin(2 * np.pi * 880 * t) * decay(t, 0.08)
    return fade(norm(x, 0.65))


def light_green():
    t = t_axis(0.6)
    x = np.sin(2 * np.pi * 880 * t) * decay(t, 0.3) + 0.35 * np.sin(2 * np.pi * 1320 * t) * decay(t, 0.2)
    return fade(norm(x, 0.7))


def fruit_pickup():
    """Picking up a fruit: a crunchy slurp, modelled on measurements of the original sound (204 ms), not on a description of it: silence for 40 ms, then a crisp noise burst whose energy is
    at 2 to 3 kHz; a broad resonance in the noise (no steady pitch) that slides down from 3 kHz to about 650 Hz over the next 140 ms; a loudness that builds from the burst to a peak at
    145 ms (about 14 dB over the burst) and dies away by 200 ms; and a grainy, crackling loudness (the "crunch": the level jumps about 30 to 50 per cent every 4 to 8 ms)."""
    dur = 0.204
    t = t_axis(dur)
    n = len(t)
    out = np.zeros(n)
    # the resonance: a state variable band pass whose centre falls exponentially from 3000 Hz at 40 ms to 650 Hz at 190 ms
    src = noise(n)
    low = band = 0.0
    q = 1.0 / 3.2
    for i in range(n):
        ti = t[i]
        if ti < 0.040:
            fc = 3000.0
        else:
            fc = 3000.0 * (650.0 / 3000.0) ** min(1.0, (ti - 0.040) / 0.150)
        f = 2.0 * np.sin(np.pi * fc / SR)
        low += f * band
        high = src[i] - low - q * band
        band += f * high
        out[i] = band
    # the loudness: nothing until 40 ms, the burst (0.6) up to 60 ms, then a build to the peak at 145 ms, then the fall
    env = np.zeros(n)
    for i in range(n):
        ti = t[i]
        if ti < 0.040:
            env[i] = 0.02
        elif ti < 0.052:
            env[i] = 0.02 + 0.58 * (ti - 0.040) / 0.012
        elif ti < 0.060:
            env[i] = 0.60
        elif ti < 0.145:
            env[i] = 0.60 + (3.2 - 0.60) * ((ti - 0.060) / 0.085) ** 1.4
        else:
            env[i] = 3.2 * np.exp(-(ti - 0.145) / 0.030)
    # the grain: irregular jumps of the level every few milliseconds
    grain = lowpass(noise(n), 260.0)
    grain = 1.0 + 0.45 * grain / (np.max(np.abs(grain)) + 1e-9)
    out = out * env * grain
    # the spectrum keeps its weight where the resonance is, but the burst also has a little crisp 2 to 4 kHz noise of its own
    crisp = bandpass(noise(n), 2000, 4200) * np.where((t > 0.040) & (t < 0.075), np.minimum((t - 0.040) / 0.008, 1.0) * np.exp(-(t - 0.045) / 0.02), 0.0) * 0.5
    return fade(norm(out / (np.max(np.abs(out)) + 1e-9) + crisp, 0.76))

def fruit_arrive():
    """A fruit reaching the counter: modelled on a close reading of the original (164 ms), which is a TONE and not noise (its spectral flatness is 0.14 to 0.3): a note at 5.1 kHz that falls to
    3.8 kHz by 55 ms, a second fall from 5.1 kHz to 3.6 kHz between 63 and 104 ms, and then a held note at 3.6 kHz until 135 ms and a quick end by 147 ms. Each note has partials at
    about 0.8, 0.6 and 0.4 of its pitch (5.1 kHz with one at 5.9 kHz and one at 4.0; the held 3.6 kHz with 2.9 and 1.7), and the level peaks at 19 ms, dips to two thirds, and rises again for the held note."""
    dur = 0.164
    t = t_axis(dur)
    n = len(t)
    # the pitch of the main note over time (Hz)
    f = np.interp(t, [0.0, 0.031, 0.055, 0.0555, 0.062, 0.063, 0.104, dur], [5140.0, 5140.0, 3820.0, 3820.0, 3820.0, 5120.0, 3610.0, 3610.0])
    # between 55 and 63 ms the first note has ended and the second has not begun: the level goes down there (see env)
    phase = 2 * np.pi * np.cumsum(f) / SR
    # partial ratios of each stage: A (1, 1.145, 0.78), B (1, 0.61, 0.38), C (1, 0.806, 0.48), crossfaded by time
    def mix(w_a, w_b, w_c):
        out = np.zeros(n)
        for ratio, wa, wb, wc in ((1.0, 1.0, 1.0, 1.0), (1.145, 0.8, 0.0, 0.0), (0.78, 0.45, 0.0, 0.0), (0.61, 0.0, 0.7, 0.0), (0.38, 0.0, 0.35, 0.0), (0.806, 0.0, 0.0, 0.6), (0.48, 0.0, 0.0, 0.38)):
            w = wa * w_a + wb * w_b + wc * w_c
            out += w * np.sin(phase * ratio)
        return out
    a = np.clip((0.060 - t) / 0.004, 0.0, 1.0) * (t < 0.062)
    b = np.clip((t - 0.060) / 0.004, 0.0, 1.0) * np.clip((0.106 - t) / 0.004, 0.0, 1.0)
    c = np.clip((t - 0.102) / 0.008, 0.0, 1.0)
    tone = mix(a, b, c)
    env = np.interp(t, [0.0, 0.007, 0.011, 0.019, 0.035, 0.057, 0.059, 0.063, 0.087, 0.107, 0.135, 0.139, 0.143, 0.147, dur],
                    [0.0, 0.015, 0.061, 0.147, 0.102, 0.095, 0.13, 0.115, 0.113, 0.14, 0.13, 0.045, 0.012, 0.0, 0.0])
    air = lowpass(noise(n), 6000.0) * 0.06 * (env > 0.02)                 # a little breath round the tone (the original is not a pure sine: flatness 0.2)
    return fade(norm(tone * env / 4.0 + air * env, 0.7), 2)

def chirp_phase(t, f0, f1):
    return f0 * t + (f1 - f0) * t * t / (2 * t[-1])


def glass_shatter():
    t = t_axis(0.5)
    x = bandpass(noise(len(t)), 2500, 9000) * decay(t, 0.12) + 0.5 * np.sin(2 * np.pi * 2400 * t) * decay(t, 0.05)
    x += 0.4 * lowpass(noise(len(t)), 700) * decay(t, 0.06)
    return fade(norm(x, 0.8))


def mine_drop():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (260 - 120 * t / 0.2) * t) * decay(t, 0.06) + 0.3 * bandpass(noise(len(t)), 500, 3000) * decay(t, 0.02)
    return fade(norm(x, 0.65))


def tnt_hit():
    t = t_axis(0.22)
    x = np.sin(2 * np.pi * (320 + 200 * t / 0.22) * t) * decay(t, 0.07) + 0.2 * noise(len(t)) * decay(t, 0.02)
    return fade(norm(x, 0.65))


def tnt_land():
    t = t_axis(0.25)
    x = np.sin(2 * np.pi * (140 - 60 * t / 0.25) * t) * decay(t, 0.08) + 0.3 * bandpass(noise(len(t)), 300, 2500) * decay(t, 0.03)
    return fade(norm(x, 0.7))


def tnt_tick():
    t = t_axis(0.18)
    x = (np.sin(2 * np.pi * 880 * t) + 0.4 * np.sin(2 * np.pi * 1320 * t)) * decay(t, 0.07)
    return fade(norm(x, 0.55))


def tnt_explode():
    t = t_axis(0.9)
    x = lowpass(noise(len(t)), 900) * decay(t, 0.25) + 0.8 * np.sin(2 * np.pi * (70 - 30 * t) * t) * decay(t, 0.3)
    x += 0.4 * bandpass(noise(len(t)), 1500, 6000) * decay(t, 0.05)
    return fade(norm(x, 0.9))


def bomb_launch():
    t = t_axis(0.3)
    x = lowpass(noise(len(t)), 1800) * decay(t, 0.07) + 0.7 * np.sin(2 * np.pi * chirp_phase(t, 420, 110)) * decay(t, 0.09)
    return fade(norm(x, 0.8))


def bomb_roll():
    n = int(SR * 0.5)
    x = bandpass(noise(n + 6000), 90, 700)
    x *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 4 * np.arange(len(x)) / SR))      # a heavy ball turning over, four beats a second
    xf = 3000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.5)


def bomb_explode():
    t = t_axis(1.0)
    x = lowpass(noise(len(t)), 1200) * decay(t, 0.3) + 0.9 * np.sin(2 * np.pi * (85 - 40 * t) * t) * decay(t, 0.32)
    x += 0.5 * bandpass(noise(len(t)), 1800, 7000) * decay(t, 0.06)
    return fade(norm(x, 0.95))


def mask_aku():
    """Stand-in for Aku Aku's cry: two rising, vowel-like notes."""
    t = t_axis(0.9)
    f = np.where(t < 0.4, 330 + 180 * t / 0.4, 420 + 120 * (t - 0.4) / 0.5)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)) * np.where(t < 0.4, decay(t, 0.5), decay(t - 0.4, 0.3)) * (0.5 + 0.5 * np.sin(np.pi * np.clip(t / 0.9, 0, 1)))
    return fade(norm(x, 0.7))


def mask_uka():
    """Stand-in for Uka Uka's laugh: a low growl, three falling barks."""
    t = t_axis(0.9)
    f = 170 - 60 * ((t * 3.3) % 1.0)
    ph = 2 * np.pi * np.cumsum(f) / SR
    bark = np.exp(-((t * 3.3) % 1.0) * 5.0)
    x = (np.sign(np.sin(ph)) * 0.5 + np.sin(ph) * 0.5 + 0.3 * lowpass(noise(len(t)), 1500)) * bark * np.clip((0.9 - t) / 0.2, 0, 1)
    return fade(norm(x, 0.75))


def warp_launch():
    """Stand-in for the Warp Orb leaving: a rising electric whine."""
    t = t_axis(0.7)
    x = np.sin(2 * np.pi * chirp_phase(t, 200, 1400)) * 0.5 + 0.4 * bandpass(noise(len(t)), 1500, 7000) * (0.4 + 0.6 * np.sin(2 * np.pi * 40 * t) ** 2)
    return fade(norm(x * decay(t - 0.05, 0.4), 0.7))


def warp_fly():
    """A short crackling hum, played now and then while the orb flies."""
    t = t_axis(0.35)
    x = 0.5 * np.sin(2 * np.pi * 180 * t) + 0.5 * bandpass(noise(len(t)), 800, 5000) * (np.sin(2 * np.pi * 60 * t) > 0.3)
    return fade(norm(x, 0.45))


def lap():
    """Stand-in for the lap sound (a lap was completed): two rising bell notes."""
    t = t_axis(0.6)
    x = np.zeros(len(t))
    for start, f in ((0.0, 660.0), (0.14, 990.0)):
        tt = np.clip(t - start, 0.0, None)
        x += (t >= start) * (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt)) * np.exp(-tt * 7.0)
    return fade(norm(x, 0.6))


def warp_end():
    """The orb closing (the end crystal's hit), modelled on a close reading of the original (393 ms): a RISING electric sweep, but a noisy, torn one and not a clean glide (a clean glide is a
    squeak): spectral flatness 0.3 to 0.4, i.e. as much hiss and crackle as tone. A buzzing, distorted tone climbs from 250 Hz at 30 ms to 450 Hz at 125 ms, 940 Hz at 210 ms, 1.26 kHz at 245 ms,
    2.6 kHz at 275 ms and 3.1 kHz at 311 ms, its pitch shaking at random; the level builds to a peak at 170 ms and falls, jumping every 10 to 20 ms like an arc; crackle and a noise modulated by the
    tone run through it; and a faint ring at 4.5 kHz and a low residue are what is left in the tail."""
    dur = 0.393
    t = t_axis(dur)
    n = len(t)
    f = np.interp(t, [0.0, 0.030, 0.071, 0.095, 0.113, 0.125, 0.150, 0.173, 0.190, 0.209, 0.227, 0.245, 0.263, 0.275, 0.293, 0.311, dur],
                  [230.0, 250.0, 291.0, 320.0, 409.0, 452.0, 592.0, 711.0, 786.0, 937.0, 1163.0, 1260.0, 1906.0, 2584.0, 2950.0, 3080.0, 3200.0])
    shake = lowpass(noise(n), 180.0)
    shake = shake / (np.max(np.abs(shake)) + 1e-9)
    f = f * (1.0 + 0.09 * shake)                                          # the pitch shakes: it is an arc, not a whistle
    phase = 2 * np.pi * np.cumsum(f) / SR
    raw = np.zeros(n)
    for k, w in ((1, 1.0), (2, 0.7), (3, 0.55), (4, 0.4), (5, 0.3), (6, 0.2)):
        raw += w * np.sin(k * phase + 0.4 * k) * (k * f < 0.45 * SR)
    tone = np.tanh(2.2 * raw / 2.0)                                       # driven hard: a buzz, not a sine
    env = np.interp(t, [0.0, 0.017, 0.029, 0.041, 0.065, 0.095, 0.119, 0.137, 0.167, 0.185, 0.200, 0.230, 0.260, 0.290, 0.311, 0.323, 0.341, 0.365, dur],
                    [0.003, 0.019, 0.03, 0.059, 0.077, 0.112, 0.148, 0.189, 0.244, 0.214, 0.12, 0.11, 0.10, 0.09, 0.068, 0.057, 0.03, 0.011, 0.003])
    steps = np.random.default_rng(23).uniform(0.35, 1.2, int(dur / 0.010) + 2)
    arc = np.interp(t, np.arange(len(steps)) * 0.010, steps)                       # the level jumps about every 10 ms, deeply
    gate = np.clip((0.34 - t) / 0.03, 0.0, 1.0)
    crackle = bandpass(noise(n), 600, 3600) * (np.random.default_rng(5).random(n) > 0.15) * gate
    torn = tone * lowpass(noise(n), 2500.0) / 1.5 * gate                           # the tone torn by noise
    hiss = bandpass(noise(n), 1500, 6000) * 0.25 * gate
    ring = np.sin(2 * np.pi * 4480.0 * t) * np.clip((t - 0.31) / 0.02, 0.0, 1.0) * np.exp(-(t - 0.31) / 0.05) * 0.18
    low = np.sin(2 * np.pi * 355.0 * t) * np.clip((t - 0.31) / 0.02, 0.0, 1.0) * np.exp(-(t - 0.31) / 0.06) * 0.25
    body = (tone * gate * 0.30 + torn * 0.45 + crackle * 0.75 + hiss) * env * arc / 0.2 + (ring + low) * env.max()
    return fade(norm(body, 0.8), 3)

def shield_on():
    t = t_axis(0.45)
    x = 0.7 * np.sin(2 * np.pi * chirp_phase(t, 300, 900)) * decay(t, 0.2) + 0.3 * lowpass(noise(len(t)), 3000) * decay(t, 0.1)
    return fade(norm(x, 0.7))


def shield_roll():
    n = int(SR * 0.4)
    x = bandpass(noise(n + 6000), 300, 1800)
    x *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 6 * np.arange(len(x)) / SR))
    xf = 3000
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.4)


def shield_pop():
    t = t_axis(0.25)
    x = bandpass(noise(len(t)), 800, 6000) * decay(t, 0.05) + 0.5 * np.sin(2 * np.pi * chirp_phase(t, 900, 200)) * decay(t, 0.06)
    return fade(norm(x, 0.8))


def missile_launch():
    t = t_axis(0.5)
    x = lowpass(noise(len(t)), 3500) * decay(t, 0.12) + 0.6 * np.sin(2 * np.pi * chirp_phase(t, 260, 900)) * decay(t, 0.2)
    return fade(norm(x, 0.8))


def missile_fly():
    n = int(SR * 0.4)
    x = bandpass(noise(n + 6000), 700, 3500)
    x *= 0.75 + 0.25 * np.sin(2 * np.pi * 25 * np.arange(len(x)) / SR)          # a rocket's rasp
    xf = 3000
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.4)


def missile_explode():
    t = t_axis(0.9)
    x = lowpass(noise(len(t)), 1800) * decay(t, 0.22) + 0.8 * np.sin(2 * np.pi * (110 - 50 * t) * t) * decay(t, 0.25)
    x += 0.5 * bandpass(noise(len(t)), 2000, 8000) * decay(t, 0.05)
    return fade(norm(x, 0.95))


def tracker_beep():
    t = t_axis(0.12)
    return fade(norm(np.sin(2 * np.pi * 1320 * t) * decay(t, 0.08), 0.6))


def crate_break():
    t = t_axis(0.35)
    x = bandpass(noise(len(t)), 400, 4000) * decay(t, 0.07) + 0.6 * np.sin(2 * np.pi * 180 * t) * decay(t, 0.05)
    x += 0.4 * bandpass(noise(len(t)), 2500, 7000) * decay(t, 0.02)
    return fade(norm(x, 0.85))


def item_roll():
    t = t_axis(0.5)
    ticks = (np.sin(2 * np.pi * 18 * t) > 0.6).astype(float)
    x = np.sin(2 * np.pi * chirp_phase(t, 500, 900)) * ticks * 0.8 + 0.1 * noise(len(t)) * ticks
    return fade(norm(x, 0.55))


def item_get():
    t = t_axis(0.4)
    x = np.sin(2 * np.pi * 659 * t) * decay(t, 0.18) + 0.5 * np.sin(2 * np.pi * 988 * t) * decay(t, 0.22)
    return fade(norm(x, 0.65))


def item_get_charged():
    out = np.zeros(int(SR * 0.7))
    for i, f in enumerate((1568.0, 2093.0)):
        t = t_axis(0.5)
        tone = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t)) * decay(t, 0.2)
        s0 = int(i * 0.09 * SR)
        out[s0: s0 + len(tone)] += tone
    return fade(norm(out, 0.65))


def wobble_thump():
    """The kart's bounce while it wobbles (stand-in): a short low thump."""
    t = t_axis(0.12)
    x = np.sin(2 * np.pi * (110 + 70 * decay(t, 0.02)) * t) * decay(t, 0.04) + 0.3 * lowpass(noise(len(t)), 900) * decay(t, 0.02)
    return fade(norm(x, 0.6))


def squish():
    """A kart squashed flat (stand-in): a falling slide whistle and a thud."""
    t = t_axis(0.5)
    x = np.sin(2 * np.pi * np.cumsum(900 * decay(t, 0.18) + 120) / SR) * decay(t, 0.25) * 0.7
    thud = np.sin(2 * np.pi * 70 * t) * decay(t, 0.06)
    return fade(norm(x + 0.6 * thud, 0.7))


def squish_restore():
    """A squashed kart springing back (stand-in): a rising boing."""
    t = t_axis(0.4)
    x = np.sin(2 * np.pi * np.cumsum(200 + 700 * (t / 0.4)) / SR) * decay(t, 0.2)
    return fade(norm(x, 0.65))


def burn():
    """A kart catching fire (stand-in): a whoosh and a crackle."""
    t = t_axis(0.7)
    whoosh = bandpass(noise(len(t)), 300, 2500) * np.minimum(1.0, t / 0.08) * decay(t, 0.3)
    crackle = (rng.random(len(t)) > 0.985) * rng.uniform(-1, 1, len(t)) * decay(t, 0.5)
    return fade(norm(whoosh + 0.8 * crackle, 0.7))


def _click(f, length, hardness):
    """One clock beat: a short dry click (a band of noise) on a wooden resonance at {f} Hz."""
    t = t_axis(length)
    body = np.sin(2 * np.pi * f * t) * decay(t, 0.018) + 0.35 * np.sin(2 * np.pi * f * 2.4 * t) * decay(t, 0.008)
    tick = bandpass(noise(len(t)), 2500, 6500) * decay(t, 0.003) * hardness
    return body + tick


def clock():
    """The Clock's sound (stand-in): a clock's tick-tock, tick-tock: a higher, sharper beat and a lower, rounder one, a quarter of a second apart."""
    out = np.zeros(int(SR * 1.15))
    for i in range(4):
        beat = _click(1900.0, 0.12, 1.1) if i % 2 == 0 else _click(1250.0, 0.14, 0.8)
        s0 = int(i * 0.27 * SR)
        out[s0: s0 + len(beat)] += beat * (1.0 if i < 2 else 0.8)
    return fade(norm(out, 0.7))


def crash_soft():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (90 + 60 * decay(t, 0.03)) * t) * decay(t, 0.06) + 0.4 * lowpass(noise(len(t)), 1200) * decay(t, 0.03)
    return fade(norm(x, 0.6))


def crash_mid():
    t = t_axis(0.3)
    x = np.sin(2 * np.pi * (70 + 80 * decay(t, 0.04)) * t) * decay(t, 0.09) + 0.6 * lowpass(noise(len(t)), 2200) * decay(t, 0.05)
    return fade(norm(x, 0.8))


def crash_hard():
    t = t_axis(0.5)
    x = np.sin(2 * np.pi * (50 + 90 * decay(t, 0.05)) * t) * decay(t, 0.16) + 0.8 * lowpass(noise(len(t)), 3000) * decay(t, 0.07)
    x += 0.3 * bandpass(noise(len(t)), 2500, 7000) * decay(t, 0.025)
    return fade(norm(x, 0.95))


# the explosions: the jar's files are silent (Minecraft's own explosion sound is played by the jar's fx/*.json, which a pack replaces), so a pack's ogg of the same name is the sound
VANILLA = ("tnt_explode", "bomb_explode", "missile_explode")


def write_ogg(name, samples):
    os.makedirs(os.path.join(OUT, "sounds"), exist_ok=True)
    oggenc.write_ogg(os.path.join(OUT, "sounds", name + ".ogg"), np.clip(samples, -1, 1), SR, quality=0.45)


def main():
    import make_jingles
    make_jingles.write_all()
    for name in SOUNDS:
        write_ogg(name, np.zeros(int(SR * 0.05)) if name in VANILLA else globals()[name]())
    sj = {name: {"sounds": ["craftteamracing:" + name]} for name in SOUNDS}
    sj.update({name: {"sounds": []} for name in MUSIC_EVENTS})
    sj["silent"] = {"sounds": []}                      # an event with no sound (ModSounds.SILENT)
    for tag, name in (("race_intro", "standin_race_intro"), ("race_win", "standin_race_win"), ("race_lose", "standin_race_lose")):
        sj["music.standin." + tag] = {"sounds": [{"name": "craftteamracing:music/" + name, "stream": True}]}          # the mod's own jingles (tools/make_jingles.py)
    with open(os.path.join(OUT, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sj, f, indent=2)
    print("%d sounds written" % len(SOUNDS))


if __name__ == "__main__":
    main()
