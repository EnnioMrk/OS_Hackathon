"""
Full pipeline: instruction text -> AI decides commands -> send over socket,
one at a time, stopping and reporting if any command errors.

This is what voice/listen.py's output should eventually feed into.

Run standalone:
    python pipeline.py "mine 5 iron ore then come back to 0 64 0"
"""

import sys

from decide import decide
from socket_client import run_commands


def main():
    instruction = " ".join(sys.argv[1:]) or input("Instruction: ")
    plan = decide(instruction)

    print("\nTo-do list:")
    for item in plan["todo"]:
        print(f"  - {item}")

    print("\nSending commands:")
    results = run_commands(plan["commands"])

    print("\nSummary:")
    for cmd, result in zip(plan["commands"], results):
        print(f"  {cmd}: {result.get('status')} - {result.get('message', '')}")


if __name__ == "__main__":
    main()
