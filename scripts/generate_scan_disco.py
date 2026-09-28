"""Generate the original four-second 120 BPM disco scan jingle (stdlib only).
No samples or excerpts of existing songs. Re-run from the repository root.
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
DURATION = 4
rng = random.Random(70)
samples = []
bass_notes = [110, 110, 164.8138, 220, 130.8128, 130.8128, 195.9977, 261.6256]
for i in range(RATE * DURATION):
    t = i / RATE
    beat = t % .5
    eighth = t % .25
    # Four-on-the-floor kick, offbeat hats and handclap-like snare.
    kick = .42 * math.exp(-beat * 24) * math.sin(2 * math.pi * (48 * beat + 2.4 * (1 - math.exp(-beat * 35))))
    noise = rng.uniform(-1, 1)
    hat = .065 * noise * math.exp(-eighth * 95)
    snare = .13 * noise * math.exp(-beat * 35) if int(t * 2) % 2 else 0
    note = bass_notes[int(t * 4) % len(bass_notes)]
    bass = .20 * (1 - math.exp(-eighth * 150)) * math.exp(-eighth * 9) * (math.sin(2 * math.pi * note * t) + .25 * math.sin(4 * math.pi * note * t))
    chord_time = (t + .25) % .5
    chord = [440, 523.2511, 659.2551] if t < 2 else [523.2511, 659.2551, 783.9909]
    keys = .07 * math.exp(-chord_time * 16) * sum(math.sin(2 * math.pi * f * t) for f in chord)
    fade = min(1, t / .015, (DURATION - t) / .25)
    samples.append(int(max(-1, min(1, (kick + hat + snare + bass + keys) * fade)) * 30000))
output = Path('collectorvision-native/src/main/res/raw/scan_disco.wav')
with wave.open(str(output), 'wb') as wav:
    wav.setnchannels(1)
    wav.setsampwidth(2)
    wav.setframerate(RATE)
    wav.writeframes(struct.pack('<' + 'h' * len(samples), *samples))
print(f'{output}: {len(samples) / RATE:.1f}s original disco groove')
