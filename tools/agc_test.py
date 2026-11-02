# Simulates the mod: grammar = target + 40 random Minecraft item names + [unk], at normal and quiet volume, with/without gain.
import json, os, random, sys, wave, array
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
model_dir, wav_dir, names_file = sys.argv[1], sys.argv[2], sys.argv[3]
names = json.load(open(names_file, encoding="utf-8"))
m = Model(model_dir)
def agc(data, state):
    a = array.array("h", data)
    peak = max(1, max(abs(x) for x in a))
    state["peak"] = max(peak, state["peak"] * 0.995)
    gain = min(20.0, 16000.0 / state["peak"])
    return array.array("h", [max(-32768, min(32767, int(x * gain))) for x in a]).tobytes()
def run(path, grammar, gain):
    wf = wave.open(path, "rb"); rec = KaldiRecognizer(m, 16000, grammar) if grammar else KaldiRecognizer(m, 16000)
    st = {"peak": 3000.0}; out = []
    while True:
        d = wf.readframes(1600)
        if not d: break
        if gain: d = agc(d, st)
        if rec.AcceptWaveform(d): out.append(json.loads(rec.Result()).get("text", ""))
        else:
            p = json.loads(rec.PartialResult()).get("partial", "")
            if p and (not out or out[-1] != p): out.append(p)
    out.append("F:" + json.loads(rec.FinalResult()).get("text", ""))
    return out[-4:]
random.seed(1)
for f in sorted(os.listdir(wav_dir)):
    target = f.rsplit("_", 2)[0].replace("_", " ")
    distract = [n.lower() for n in random.sample(names, 40) if n.lower() != target]
    g = json.dumps([target] + distract + ["[unk]"], ensure_ascii=False)
    for gain in (False, True):
        print(f"{f:34s} gain={gain!s:5s} grammar={run(os.path.join(wav_dir, f), g, gain)}  free={run(os.path.join(wav_dir, f), None, gain)}")
