"""
One-time setup: downloads the Whisper model and caches it locally.

Safe to run more than once -- if the model is already downloaded, it does
nothing and exits immediately instead of re-downloading.

Run: python download_model.py
"""

import truststore

truststore.inject_into_ssl()  # use Windows cert store -- needed on networks that intercept HTTPS

from faster_whisper import WhisperModel

MODEL_SIZE = "small"  # must match MODEL_SIZE in listen.py


def already_downloaded() -> bool:
    try:
        WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8", local_files_only=True)
        return True
    except Exception:
        return False


def main():
    if already_downloaded():
        print(f"Whisper model '{MODEL_SIZE}' is already downloaded. Nothing to do.")
        return

    print(f"Downloading Whisper model '{MODEL_SIZE}' (one-time, needs internet)...")
    WhisperModel(MODEL_SIZE, device="cpu", compute_type="int8")
    print("Done. You can now run listen.py offline.")


if __name__ == "__main__":
    main()
