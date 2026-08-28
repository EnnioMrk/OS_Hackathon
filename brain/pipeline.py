"""
Full pipeline: instruction text -> AI decides commands -> send over socket,
one at a time -- and if one fails, tell the AI what went wrong and let it
replan, instead of just giving up. Retries a bounded number of times before
reporting failure back to the caller (the player, via voice/listen.py).

This is what voice/listen.py's output should eventually feed into.

Run standalone:
    python pipeline.py "mine 5 iron ore then come back to 0 64 0"
"""

import sys

from decide import decide
from socket_client import BridgeConnection

MAX_ATTEMPTS = 3  # 1 initial try + up to 2 replans


def _looks_like_failure(reply: dict) -> bool:
    if reply.get("status") == "error":
        return True
    # Bridge reports "done" even when nothing really happened (goal already
    # satisfied or unreachable) -- these substrings are a best guess at that
    # wording pending a live test; tune once we've seen the real messages.
    message = (reply.get("message") or "").lower()
    return any(s in message for s in ("no activity", "no baritone activity", "unreachable"))


def run_instruction(instruction: str, conn: BridgeConnection | None = None) -> None:
    """Full pipeline for one instruction: decide -> execute -> replan on failure."""
    own_connection = conn is None
    if own_connection:
        conn = BridgeConnection()

    context = None
    try:
        for attempt in range(1, MAX_ATTEMPTS + 1):
            plan = decide(instruction, context=context)

            print(f"\nAttempt {attempt} -- to-do list:")
            for item in plan["todo"]:
                print(f"  - {item}")

            failure = None
            for cmd in plan["commands"]:
                reply = conn.run(cmd)
                if _looks_like_failure(reply):
                    failure = (cmd, reply)
                    break

            if failure is None:
                print("\nDone -- all commands completed.")
                return

            cmd, reply = failure
            print(f"\n'{cmd}' didn't work ({reply.get('status')}: {reply.get('message')})")

            if attempt < MAX_ATTEMPTS:
                print("Asking the AI to adjust the plan...")
                context = f"Attempted '{cmd}', which failed with: {reply.get('message')}"
            else:
                print("Giving up after repeated failures -- try rephrasing the instruction.")
    finally:
        if own_connection:
            conn.close()


if __name__ == "__main__":
    instruction = " ".join(sys.argv[1:]) or input("Instruction: ")
    run_instruction(instruction)
