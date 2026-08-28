"""
Step 3 of the pipeline: send commands to the Baritone Socket Bridge, one at a
time, waiting for each to finish (done/error) before sending the next -- this
is the "send commands in order, debug after each" part of the pipeline.

Protocol: newline-delimited JSON. See AI_COMMANDS.md for the full spec.

Run standalone:
    python socket_client.py "mine 5 iron_ore" "goto 100 64 200"
"""

import json
import os
import socket
import sys

from dotenv import load_dotenv

load_dotenv()

HOST = os.environ.get("SOCKET_HOST", "127.0.0.1")
PORT = int(os.environ.get("SOCKET_PORT", "5555"))


class BridgeConnection:
    def __init__(self, host: str = HOST, port: int = PORT, timeout: float = 10.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._buf = b""
        self._next_id = 1
        hello = self._read_line()
        print(f"[bridge] {hello}")

    def _read_line(self) -> dict:
        while b"\n" not in self._buf:
            data = self.sock.recv(4096)
            if not data:
                raise ConnectionError("Bridge closed the connection")
            self._buf += data
        line, self._buf = self._buf.split(b"\n", 1)
        return json.loads(line)

    def run(self, cmd: str) -> dict:
        """Send one command and block until it reaches done/error. Returns that reply."""
        cmd_id = self._next_id
        self._next_id += 1
        msg = json.dumps({"id": cmd_id, "cmd": cmd})
        print(f"[bridge] >> {msg}")
        self.sock.sendall((msg + "\n").encode())

        while True:
            reply = self._read_line()
            print(f"[bridge] << {reply}")
            if reply.get("id") == cmd_id and reply.get("status") in ("done", "error"):
                return reply
            # otherwise it's the "accepted" ack for this id -- keep reading for done/error

    def close(self):
        self.sock.close()


def run_commands(commands: list[str]) -> list[dict]:
    """Connect, run each command in order. Stops early if a command errors."""
    conn = BridgeConnection()
    results = []
    try:
        for cmd in commands:
            reply = conn.run(cmd)
            results.append(reply)
            if reply.get("status") == "error":
                print(f"[bridge] '{cmd}' failed: {reply.get('message')} -- stopping remaining commands")
                break
    finally:
        conn.close()
    return results


if __name__ == "__main__":
    commands = sys.argv[1:]
    if not commands:
        print('Usage: python socket_client.py "<command 1>" "<command 2>" ...')
        sys.exit(1)
    run_commands(commands)
