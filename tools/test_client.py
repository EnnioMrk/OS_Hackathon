#!/usr/bin/env python3
"""Test client for the Baritone Socket Bridge.

Usage:
    python3 tools/test_client.py                                  # interactive
    python3 tools/test_client.py "goto 100 64 200" "mine iron_ore"  # send and wait

One JSON object per line is sent; all server responses are printed as they
arrive (accepted / done / error).
"""

import argparse
import json
import socket
import sys
import threading
import time


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("commands", nargs="*")
    args = parser.parse_args()

    sock = socket.create_connection((args.host, args.port), timeout=5)

    def reader() -> None:
        buf = b""
        while True:
            try:
                data = sock.recv(4096)
            except OSError:
                return
            if not data:
                print("<< [disconnected]")
                sys.exit(0)
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                print("<<", line.decode(errors="replace"))

    threading.Thread(target=reader, daemon=True).start()

    next_id = 1

    def send(cmd: str) -> None:
        nonlocal next_id
        msg = json.dumps({"id": next_id, "cmd": cmd})
        next_id += 1
        print(">>", msg)
        sock.sendall((msg + "\n").encode())

    if args.commands:
        for cmd in args.commands:
            send(cmd)
            time.sleep(0.2)
        try:
            while True:  # stay connected to receive done/error responses
                time.sleep(0.5)
        except KeyboardInterrupt:
            pass
    else:
        print("Type baritone commands (e.g. 'goto 100 64 200'), Ctrl-C to quit")
        for line in sys.stdin:
            cmd = line.strip()
            if cmd:
                send(cmd)


if __name__ == "__main__":
    sys.exit(main())
