# Baritone Socket Bridge

A Minecraft **1.21.1** (Forge) client mod that exposes a local TCP socket server
and feeds newline-delimited JSON commands to [Baritone](https://github.com/cabaletta/baritone)
in order. Errors and task completion are reported back to the connected client.

## How it works

```
TCP client ──▶ SocketServer (accept + reader threads)
                 │ parses/validates JSON lines
                 ▼
             CommandQueue (unbounded, thread-safe FIFO)
                 │ drained by ClientTickEvent (1 command at a time)
                 ▼
             Baritone command manager (main client thread only!)
                 │
                 ├─ completion/error ──▶ JSON response back over the socket
```

- Commands are validated and queued as they arrive; execution happens strictly
  in order, one at a time, on the Minecraft client thread.
- Completion is detected by polling Baritone's pathing/process state each tick
  (`isPathing()` + the active states of the mine/follow/farm/build/explore
  processes); a done response is sent after a short settle period.
- A task that shows no activity within the grace period is reported as done
  with a "no baritone activity observed" note (goal likely unreachable or
  already satisfied). Continuously active tasks time out (`commandTimeoutTicks`).

## Protocol

Newline-delimited JSON, one object per line. A machine-oriented command
reference for AI clients is in [`AI_COMMANDS.md`](AI_COMMANDS.md).

```jsonc
// client -> server ('id' is optional; the server assigns one if omitted)
{"cmd": "goto 100 64 200"}

// server -> client (on connect)
{"status":"hello","message":"baritone socket bridge ready","queue":0}

// server -> client (per command)
{"id":1,"status":"accepted","queue":1}
{"id":1,"status":"done","message":"task finished"}
{"id":1,"status":"error","message":"unknown baritone command 'asdf', allowed: [...]"}
```

Recognized commands: `goto`, `mine`, `follow`, `farm`, `build`, `explore`,
`stop`, `cancel`, `set`. Control commands (`stop`, `cancel`, `set`) bypass
the task queue: they execute on the next tick even while a task is running
and are never dropped due to backpressure (`stop`/`cancel`
also report the active task as done with "cancelled by ...").

## Configuration

`config/baritonesocket.properties` (created on first launch):

| Key                  | Default  | Meaning                                        |
|----------------------|----------|------------------------------------------------|
| `port`               | 5555     | TCP port                                       |
| `bindAddress`        | 127.0.0.1| Bind address (keep loopback unless you must)   |
| *(removed)*          | —        | `maxQueueSize` no longer exists; the queue is unbounded |
| `maxConnections`     | 8        | Max simultaneous socket clients                |
| `gracePeriodTicks`   | 100      | Ticks without activity before "no activity"    |
| `settleTicks`        | 5        | Idle ticks required to call a task finished    |
| `commandTimeoutTicks`| 24000    | Ticks of continuous activity before timeout    |
| `debugLog`           | false    | Log raw received lines                         |

## Building

Requires JDK 21.

```sh
./gradlew build          # jar in build/libs/baritonesocket-0.1.0.jar
```

The Baritone API jar (`baritone-api-forge-1.11.2.jar`, the release for MC
1.21/1.21.1) is vendored in `libs/`. Install Baritone 1.11.2 for Forge 1.21.1
into your mods folder.

## Running & debugging

### Dev environment

```sh
./gradlew runClient
```

`libs/baritone-api-forge-1.11.2.jar` is already copied into `run/mods/` (it
is the runnable Baritone jar, sharing the `baritoe` mod id). Start a
single-player world; the socket server starts on world join and stops on
leave.

### Testing the socket without the mod running

```sh
nc 127.0.0.1 5555
{"cmd": "goto 100 64 200"}
```

### Test client

```sh
python3 tools/test_client.py "goto 100 64 200"
python3 tools/test_client.py            # interactive, one command per line
```

### Debug checklist

1. **Nothing connects** — check the log line
   `Baritone socket server listening on 127.0.0.1:5555` (only appears after
   joining a world); try `nc 127.0.0.1 5555`; check the firewall.
2. **Commands rejected with `error`** — the response message tells you why
   (unknown verb, malformed JSON). Set `debugLog=true` to log raw
   received lines.
3. **No Baritone reaction** — Baritone prints its own chat messages; run
   `#set logDebug true` in-game to see its internal decisions. Also verify the
   command works when typed manually in chat with a `#` prefix.
4. **Wrong done/error timing** — tune `gracePeriodTicks` (too short -> false
   "no activity"), `settleTicks` (too short -> premature done for flickery
   tasks like `mine`), `commandTimeoutTicks`.
5. **Mod doesn't load** — `mods.toml` requires Baritone (`mandatory=true`);
   the log names the missing dependency. Note Baritone's Forge mod id is
   **`baritoe`** (upstream typo in 1.11.2), so the dependency uses that id.
6. **Thread crashes (`ConcurrentModificationException`)** — any Baritone call
   must come from the client tick thread; `CommandExecutor` is the only place
   that touches Baritone.

### Known limitations

- Error text from Baritone itself (e.g. "no path found") is shown in the
  Minecraft chat, not forwarded over the socket; the bridge reports completion
  via the process-state polling described above.
- `mine` can finish "mining in place" while `isPathing()` is false; the settle
  period and `getMineProcess().isActive()` check handle most of this.
