import json, os, sys, wave
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
model_dir, wav_dir = sys.argv[1], sys.argv[2]
words_txt = os.path.join(model_dir, "graph", "words.txt")
print("words.txt exists:", os.path.exists(words_txt))
vocab = set()
if os.path.exists(words_txt):
    for l in open(words_txt, encoding="utf-8"):
        vocab.add(l.split(" ")[0])
    print("vocab size", len(vocab))
for d in sorted(os.listdir(model_dir)):
    print("model dir:", d, os.listdir(os.path.join(model_dir, d))[:12] if os.path.isdir(os.path.join(model_dir, d)) else "")
targets = json.loads(sys.argv[3])
for w in targets:
    print(w, "in vocab:", w in vocab)
m = Model(model_dir)
grammar = json.dumps(targets + ["[unk]"], ensure_ascii=False)
for f in sorted(os.listdir(wav_dir)):
    for mode in ("free", "grammar"):
        wf = wave.open(os.path.join(wav_dir, f), "rb")
        rec = KaldiRecognizer(m, 16000, grammar) if mode == "grammar" else KaldiRecognizer(m, 16000)
        partials = []
        while True:
            data = wf.readframes(1600)
            if len(data) == 0: break
            if rec.AcceptWaveform(data):
                partials.append("R:" + json.loads(rec.Result()).get("text", ""))
            else:
                p = json.loads(rec.PartialResult()).get("partial", "")
                if p and (not partials or partials[-1] != "P:" + p): partials.append("P:" + p)
        fin = json.loads(rec.FinalResult()).get("text", "")
        print(f"{f:28s} {mode:8s} final={fin!r} seq={partials[-6:]}")
