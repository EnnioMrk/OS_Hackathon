"""
Full pipeline: voice -> AI decides commands -> send over socket -> replan on failure.

Speak an instruction; when you pause, it's transcribed, sent to the AI to
turn into commands (from AI_COMMANDS.md), and those commands are sent to
the game one at a time -- waiting for each to finish before the next. If a
command fails, the AI is told what went wrong and gets to revise the plan
(up to a couple of retries) before giving up and reporting back. Then it
goes back to listening for your next instruction.

Requires:
  - voice/ and brain/ dependencies installed (see their requirements.txt)
  - .env filled in (OPENCODE_API_KEY, SOCKET_HOST, SOCKET_PORT)
  - Minecraft running with the socket bridge mod, in a loaded world

Run: python main.py
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "voice"))
sys.path.insert(0, str(ROOT / "brain"))

from listen import listen_and_transcribe  # noqa: E402
from pipeline import run_instruction  # noqa: E402
from socket_client import BridgeConnection  # noqa: E402


def main():
    conn = BridgeConnection()

    def handle_instruction(text: str):
        print(f"\n=== Heard: {text} ===")
        try:
            run_instruction(text, conn=conn)
        except Exception as e:
            print(f"[pipeline] failed: {e}")
        print("\n=== Listening for the next instruction ===")

    try:
        listen_and_transcribe(on_text=handle_instruction)
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
