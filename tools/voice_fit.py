"""Fits an EQ chain so a Gemini voice has the tonal balance and pitch of the EDITH recording from the film.

usage: python voice_fit.py voice_sample.wav film_edith_a.wav film_edith_b.wav
Prints the ffmpeg filter chain to use (also copied into the mod's default voiceFilter).
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.optimize import minimize
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voice_analyze as va

sample, film_a, film_b = sys.argv[1:4]
CENTERS = [(280, 1.0), (650, 1.0), (1400, 0.8), (3000, 0.8), (5500, 0.8)]


def target():
    bands = []
    f0 = []
    for p in (film_a, film_b):
        sr, x = va.load(p)
        bands.append(va.band_levels(x, sr)[:6])
        f0.append(np.median(va.f0_track(x, sr)))
    return np.mean(bands, axis=0), float(np.mean(f0))


def chain(gains, pitch=1.0):
    parts = ["highpass=f=110"]
    for (fc, q), g in zip(CENTERS, gains):
        parts.append("equalizer=f=%d:t=q:w=%.1f:g=%.1f" % (fc, q, g))
    parts.append("acompressor=threshold=-20dB:ratio=3:attack=5:release=80:makeup=2")
    if abs(pitch - 1.0) > 0.005:
        parts.append("asetrate=%d,aresample=24000,atempo=%.4f" % (int(24000 * pitch), 1.0 / pitch))
    return ",".join(parts)


def apply(gains, pitch=1.0):
    out = os.path.join(tempfile.gettempdir(), "edith_fit.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", sample, "-af", chain(gains, pitch), "-ar", "24000", "-ac", "1", out], check=True)
    sr, x = va.load(out)
    return va.band_levels(x, sr)[:6], float(np.median(va.f0_track(x, sr)))


tgt, f0_target = target()
tgt_shape = tgt - tgt.mean()
print("film target  F0 %.0f Hz  bands %s" % (f0_target, np.round(tgt, 1)))
b0, f00 = apply([0] * 5)
print("raw sample   F0 %.0f Hz  bands %s" % (f00, np.round(b0, 1)))


def cost(g):
    g = np.clip(g, -6, 6)
    b, _ = apply(g)
    return float(np.sqrt(np.mean(((b - b.mean()) - tgt_shape) ** 2)) + 0.02 * np.sum(np.abs(g)))


res = minimize(cost, np.zeros(5), method="Powell", options={"maxiter": 3, "xtol": 0.5, "ftol": 0.05})
g = np.clip(res.x, -6, 6)
pitch = f0_target / f00
pitch = min(1.12, max(0.88, pitch))
b1, f01 = apply(g, pitch)
print("fitted gains", np.round(g, 1), "pitch ratio %.3f" % pitch)
print("after        F0 %.0f Hz  bands %s" % (f01, np.round(b1, 1)))
print("tone distance before %.1f dB, after %.1f dB" % (
    np.sqrt(np.mean(((b0 - b0.mean()) - tgt_shape) ** 2)), np.sqrt(np.mean(((b1 - b1.mean()) - tgt_shape) ** 2))))
print("FILTER:", chain(g, pitch))
