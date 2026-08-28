"""
Step 2 of the pipeline: "AI thinks" -> to-do list -> Baritone commands.

Takes a natural-language instruction (normally the text that voice/listen.py
prints) and asks the LLM to break it into a to-do list, then convert that
to-do list into an ordered list of exact commands -- using ONLY commands
from AI_COMMANDS.md (the socket bridge's own command reference, a subset of
Baritone's full command set), via forced tool-calling so the output is
always structured, never free text the AI might hallucinate a bad command in.

Uses OpenCode Zen's OpenAI-compatible endpoint (model: GLM-5.3-Flash by
default). Does not touch the socket yet -- just prints the result.

Run standalone:
    python decide.py "go mine some iron ore for me"
"""

import json
import os
import sys
from pathlib import Path

from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.environ["OPENCODE_API_KEY"]
MODEL = os.environ.get("OPENCODE_MODEL", "glm-5.3-flash")

client = OpenAI(api_key=API_KEY, base_url="https://opencode.ai/zen/go/v1")

COMMANDS_DOC = Path(__file__).resolve().parent.parent / "AI_COMMANDS.md"
COMMANDS_TEXT = COMMANDS_DOC.read_text()

SYSTEM_PROMPT = f"""You control a Minecraft character through a socket bridge to Baritone, a pathfinding/automation mod.
The player cannot use their hands, so all input arrives as spoken instructions converted to text.

Commands reachable through the bridge (use ONLY these, with this exact syntax -- never invent a command that isn't listed):

{COMMANDS_TEXT}

Given a player's instruction:
1. Break it into a short to-do list of steps.
2. Convert each step into one exact command using the syntax above (e.g. "goto 100 64 -200" or "mine 10 iron_ore").
3. Call submit_commands with both lists, in the order they should run.

If the instruction is unclear or needs a command that doesn't exist, still call submit_commands,
but put an explanation in a step instead of guessing an invalid command.
"""

SUBMIT_TOOL = {
    "type": "function",
    "function": {
        "name": "submit_commands",
        "description": "Submit the ordered to-do list and the exact Baritone commands needed to fulfill the player's request.",
        "parameters": {
            "type": "object",
            "properties": {
                "todo": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Short human-readable to-do list, one item per step.",
                },
                "commands": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Exact Baritone command strings, in the order they should run.",
                },
            },
            "required": ["todo", "commands"],
        },
    },
}


def decide(instruction: str) -> dict:
    response = client.chat.completions.create(
        model=MODEL,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": instruction},
        ],
        tools=[SUBMIT_TOOL],
        tool_choice={"type": "function", "function": {"name": "submit_commands"}},
    )

    message = response.choices[0].message
    if not message.tool_calls:
        raise RuntimeError("Model did not call submit_commands")

    return json.loads(message.tool_calls[0].function.arguments)


if __name__ == "__main__":
    instruction = " ".join(sys.argv[1:]) or input("Instruction: ")
    result = decide(instruction)

    print("\nTo-do list:")
    for item in result["todo"]:
        print(f"  - {item}")

    print("\nCommands:")
    for cmd in result["commands"]:
        print(f"  > {cmd}")
