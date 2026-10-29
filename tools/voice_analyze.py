"""Measures pitch (F0) and tonal balance of a voice recording, so EDITH's TTS can be matched to the film voice.

usage: python voice_analyze.py file.wav [file2.wav ...]
"""
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt, welch

BANDS = [(80, 200), (200, 400), (400, 800), (800, 1600), (1600, 3200), (3200, 6400), (6400, 10000)]


def load(path):
    sr, x = wavfile.read(path)
    x = x.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(1)
    x /= max(1.0, np.abs(x).max())
    return sr, x


def f0_track(x, sr, lo=75, hi=420):
    """Autocorrelation pitch tracker. Returns F0 of voiced frames (Hz)."""
    frame, hop = int(0.045 * sr), int(0.010 * sr)
    sos = butter(4, [60, 900], "bandpass", fs=sr, output="sos")
    y = sosfilt(sos, x)
    out = []
    kmin, kmax = int(sr / hi), int(sr / lo)
    for i in range(0, len(y) - frame, hop):
        seg = y[i:i + frame] * np.hanning(frame)
        if np.sqrt(np.mean(seg ** 2)) < 0.01:
            continue
        ac = np.correlate(seg, seg, "full")[frame - 1:]
        ac /= ac[0] + 1e-12
        k = kmin + np.argmax(ac[kmin:kmax])
        if ac[k] > 0.55:
            out.append(sr / k)
    return np.array(out)


def band_levels(x, sr):
    f, p = welch(x, sr, nperseg=4096)
    lv = []
    for lo, hi in BANDS:
        m = (f >= lo) & (f < hi)
        lv.append(10 * np.log10(p[m].sum() + 1e-18))
    lv = np.array(lv)
    return lv - lv.max()


def centroid(x, sr):
    f, p = welch(x, sr, nperseg=4096)
    m = (f > 80) & (f < 8000)
    return float((f[m] * p[m]).sum() / p[m].sum())


def report(path):
    sr, x = load(path)
    f0 = f0_track(x, sr)
    lv = band_levels(x, sr)
    print("== %s  (%.1fs, %d Hz)" % (path, len(x) / sr, sr))
    if len(f0):
        print("  F0 median %.1f Hz   p10 %.1f   p90 %.1f   voiced frames %d" % (np.median(f0), np.percentile(f0, 10), np.percentile(f0, 90), len(f0)))
    print("  centroid %.0f Hz" % centroid(x, sr))
    print("  band levels dB:", " ".join("%d-%d:%.1f" % (b[0], b[1], l) for b, l in zip(BANDS, lv)))
    return (np.median(f0) if len(f0) else 0.0), lv


if __name__ == "__main__":
    for p in sys.argv[1:]:
        report(p)
