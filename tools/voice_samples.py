"""Synthesises one EDITH line with several Gemini voices and measures F0 / tone, to pick the closest to the film.

usage: python voice_samples.py outdir
The key is read from the Translate instance's config/edith-keys.json and is never printed.
"""
import base64, json, os, re, struct, sys, time, urllib.error, urllib.request, wave
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import voice_analyze as va

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
cfg = os.path.expanduser(r"~\curseforge\minecraft\Instances\Translate\config\edith-keys.json")
mk = json.loads(open(cfg).read(), strict=False)["gemini"].strip()
TEXT = "Intercept point determined. Releasing kill vehicle."
VOICES = ["Erinome", "Kore", "Aoede", "Leda", "Sulafat", "Vindemiatrix", "Zephyr", "Achernar", "Despina", "Callirrhoe", "Autonoe", "Laomedeia"]
MODELS = ["gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-3.8-flash-lite-tts"]


def synth(voice):
    for m in MODELS:
        body = {"contents": [{"parts": [{"text": TEXT}]}],
                "generationConfig": {"responseModalities": ["AUDIO"],
                                     "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}}}}
        req = urllib.request.Request("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s" % (m, mk),
                                     data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                j = json.loads(r.read())
            inl = j["candidates"][0]["content"]["parts"][0]["inlineData"]
            raw = base64.b64decode(inl["data"])
            mime = inl.get("mimeType", "")
            if raw[:4] == b"RIFF":
                open(os.path.join(out, voice + ".wav"), "wb").write(raw)
            else:
                rate = int(re.search(r"rate=(\d+)", mime).group(1)) if "rate=" in mime else 24000
                with wave.open(os.path.join(out, voice + ".wav"), "wb") as w:
                    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(raw)
            return m
        except urllib.error.HTTPError as e:
            if e.code not in (429, 404, 503):
                print("  ", voice, m, "HTTP", e.code)
            time.sleep(1)
    return None


res = {}
for v in VOICES:
    m = synth(v)
    if not m:
        print(v, "-> no model answered")
        continue
    sr, x = va.load(os.path.join(out, v + ".wav"))
    f0 = va.f0_track(x, sr)
    lv = va.band_levels(x, sr)
    res[v] = (float(np.median(f0)) if len(f0) else 0.0, lv, va.centroid(x, sr))
    print("%-13s model %-30s F0 %.1f Hz  centroid %.0f  bands %s" % (v, m, res[v][0], res[v][2], " ".join("%.0f" % l for l in lv)))
json.dump({v: [r[0], list(map(float, r[1])), r[2]] for v, r in res.items()}, open(os.path.join(out, "stats.json"), "w"))
