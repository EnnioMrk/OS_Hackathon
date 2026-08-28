# Voice input (step 1 of the pipeline)

Always-on microphone listener. No push-to-talk needed — it detects when you
start and stop talking on its own, transcribes locally with Whisper, and
prints the text. That text is what gets handed to the AI step next.

## Setup (one time)

```
cd voice
pip install -r requirements.txt
python download_model.py
```

`download_model.py` downloads the Whisper "small" model (a few hundred MB)
from Hugging Face and caches it locally — after that it works fully offline.
It's safe to run more than once: if the model's already downloaded, it does
nothing and exits immediately instead of re-downloading.

## Run

```
python listen.py
```

Speak a full sentence, then go quiet for a couple of seconds — it needs
~1.2 seconds of silence after you stop talking before it transcribes.
The transcribed text prints to the console (along with debug info: audio
duration, segment count, and confidence per segment).

## If it's not picking up your voice

Run `mic_test.py` first — no Whisper involved, it just prints your live mic
volume so you can see whether it's being captured at all, and how loud your
speech actually is relative to background noise:

```
python mic_test.py
```

Use that to sanity check/tune `SILENCE_THRESHOLD` in `listen.py` — it should
sit somewhere between your room's noise floor and your normal speaking
volume. If speech barely rises above the noise floor, boost your mic's
input volume in Windows Sound settings first (Settings → System → Sound →
Input) rather than just lowering the threshold, since a threshold too close
to the noise floor causes unreliable, overly long recordings.

## Notes

- Model size is set in `listen.py` (`MODEL_SIZE = "small"`). Use `"tiny"` or
  `"base"` for faster/less accurate, `"medium"` for slower/more accurate.
- `beam_size` and `INITIAL_PROMPT` in `listen.py` also affect accuracy.
  `INITIAL_PROMPT` primes Whisper with Baritone command words and Minecraft
  block/item names so it's less likely to mishear them as ordinary English
  words — update it if you add new commands or run into words it keeps
  getting wrong.
- `MAX_UTTERANCE` caps how long one recording can run (default 8s), so a
  noisy mic can't cause a runaway multi-second buffer.
- If you get an SSL error downloading the model on first run, this is
  already handled: both scripts call `truststore.inject_into_ssl()` on
  startup, which fixes certificate verification on networks that intercept
  HTTPS (e.g. some university/corp networks). Don't add `pip-system-certs`
  — it patches SSL globally for every Python process and conflicts with
  the `brain/` component's own SSL handling, causing a crash there.
