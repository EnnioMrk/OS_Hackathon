"""
Full pipeline: voice -> AI decides commands -> send over socket.

Speak an instruction; when you pause, it's transcribed, sent to the AI to
turn into commands (from AI_COMMANDS.md), and those commands are sent to
the game one at a time -- waiting for each to finish before the next --
then it goes back to listening for your next instruction.

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
from decide import decide  # noqa: E402
from socket_client import run_commands  # noqa: E402


def handle_instruction(text: str):
    print(f"\n=== Heard: {text} ===")

    try:
        plan = decide(text)
    except Exception as e:
        print(f"[ai] failed to turn that into commands: {e}")
        return

    print("To-do list:")
    for item in plan["todo"]:
        print(f"  - {item}")

    print("Sending commands:")
    try:
        results = run_commands(plan["commands"])
    except Exception as e:
        print(f"[bridge] failed to reach the game: {e}")
        return

    print("Summary:")
    for cmd, result in zip(plan["commands"], results):
        print(f"  {cmd}: {result.get('status')} - {result.get('message', '')}")

    print("\n=== Listening for the next instruction ===")


if __name__ == "__main__":
    try:
        listen_and_transcribe(on_text=handle_instruction)
    except KeyboardInterrupt:
        print("\nStopped.")
