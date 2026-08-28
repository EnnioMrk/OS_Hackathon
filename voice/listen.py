"""
Always-on voice listener.

Continuously listens to the microphone, waits for you to speak, detects
when you stop (silence), transcribes what you said with a local Whisper
model, and prints the text. No push-to-talk button needed.

Run: python listen.py
"""

import truststore

truststore.inject_into_ssl()  # use Windows cert store -- needed on networks that intercept HTTPS

import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel

SAMPLE_RATE = 16000
BLOCK_DURATION = 0.5        # seconds per audio chunk we check
SILENCE_THRESHOLD = 0.0002  # RMS volume below this counts as silence -- tune with mic_test.py
SILENCE_HANGOVER = 1.2      # seconds of silence before we consider the sentence done
MAX_UTTERANCE = 8.0         # hard cap on recording length -- avoids runaway buffers on noisy mics
MODEL_SIZE = "small"        # tiny/base/small/medium/large -- bigger = more accurate, slower
BEAM_SIZE = 5                # higher = more accurate, slower. 1 = greedy/fastest.

# Nudges Whisper towards Minecraft/Baritone vocabulary it wouldn't otherwise expect.
INITIAL_PROMPT = (
    "Minecraft voice commands using Baritone: goto, mine, build, follow, find, "
    "explore, tunnel, waypoints, sethome, home, cancel, pause, resume, come, axis. "
    "Blocks and items: diamond, iron, gold, coal, redstone, obsidian, cobblestone, "
    "stone, dirt, sand, gravel, emerald, lapis, netherite, quartz, torch, chest, "
    "furnace, water, lava, wood, oak log."
)

model = WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8")


def rms(block: np.ndarray) -> float:
    return float(np.sqrt(np.mean(block ** 2)))


def transcribe(audio: np.ndarray) -> str:
    segments, _ = model.transcribe(
        audio, language="en", beam_size=BEAM_SIZE, initial_prompt=INITIAL_PROMPT
    )
    segments = list(segments)
    print(f"  [debug] duration={len(audio) / SAMPLE_RATE:.2f}s segments={len(segments)}")
    for s in segments:
        print(f"  [debug] text={s.text!r} avg_logprob={s.avg_logprob:.2f} "
              f"no_speech_prob={s.no_speech_prob:.2f}")
    return " ".join(segment.text for segment in segments).strip()


def listen_and_transcribe(on_text=None):
    """Listens forever. Calls on_text(transcript) for each utterance (default: just prints it)."""
    if on_text is None:
        on_text = lambda text: print(f"> {text}")

    print("Listening... speak whenever you're ready. Ctrl+C to stop.")
    block_size = int(SAMPLE_RATE * BLOCK_DURATION)

    buffer = []
    speaking = False
    silence_time = 0.0
    speech_time = 0.0

    def finalize():
        nonlocal buffer, speaking, silence_time, speech_time
        audio = np.concatenate(buffer)
        text = transcribe(audio)
        if text:
            on_text(text)
        buffer = []
        speaking = False
        silence_time = 0.0
        speech_time = 0.0

    with sd.InputStream(samplerate=SAMPLE_RATE, channels=1, dtype="float32") as stream:
        while True:
            block, _ = stream.read(block_size)
            block = block.flatten()
            volume = rms(block)

            if volume > SILENCE_THRESHOLD:
                buffer.append(block)
                speaking = True
                silence_time = 0.0
                speech_time += BLOCK_DURATION
            elif speaking:
                buffer.append(block)
                silence_time += BLOCK_DURATION
                speech_time += BLOCK_DURATION
                if silence_time >= SILENCE_HANGOVER:
                    finalize()

            if speaking and speech_time >= MAX_UTTERANCE:
                finalize()


if __name__ == "__main__":
    try:
        listen_and_transcribe()
    except KeyboardInterrupt:
        print("\nStopped.")
