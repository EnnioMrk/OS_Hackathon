"""
Microphone diagnostic. No Whisper involved -- just checks whether the mic
is being captured and what volume level it's picking up.

Run: python mic_test.py
Talk normally and watch the numbers. Ctrl+C to stop.
"""

import numpy as np
import sounddevice as sd

SAMPLE_RATE = 16000
BLOCK_DURATION = 0.3

print("Default input device:", sd.query_devices(kind="input"))
print()
print("Talk now -- watch the volume number change. Ctrl+C to stop.\n")

block_size = int(SAMPLE_RATE * BLOCK_DURATION)

try:
    with sd.InputStream(samplerate=SAMPLE_RATE, channels=1, dtype="float32") as stream:
        while True:
            block, _ = stream.read(block_size)
            volume = float(np.sqrt(np.mean(block.flatten() ** 2)))
            bar = "#" * int(volume * 500)
            print(f"volume: {volume:.5f} {bar}")
except KeyboardInterrupt:
    print("\nStopped.")
