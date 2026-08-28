# Baritone Socket Bridge — Command Reference for AI Clients

This document is written for AI agents that control a Minecraft player through
the Baritone Socket Bridge mod. It lists **every command** the bridge accepts,
with exact syntax and examples.

A human-readable list of Baritone's own in-game commands lives in
`Commands.md`. Only the commands documented below are reachable through the
socket.

---

## 1. Connecting

The bridge runs a TCP server on the Minecraft client (defaults shown):

| Setting    | Default    |
|------------|------------|
| address    | 127.0.0.1  |
| port       | 5555       |

(Both are configurable in `config/baritonesocket.properties`; the server only
runs while the player is in a world.)

The protocol is **newline-delimited JSON**: one JSON object per line, each
line terminated with `\n`.

### Session flow

```text
1. Connect TCP.
2. Server sends:        {"status":"hello","message":"baritone socket bridge ready","queue":0}
3. You send commands.   {"cmd":"goto 100 64 200"}\n
4. Server replies per command (see below).
```

### Request format (client → server)

```json
{"cmd": "<command text>"}
```

| Field | Required | Meaning                                                        |
|-------|----------|----------------------------------------------------------------|
| `id`  | no       | Optional correlation number. If you send it, every reply for that command echoes it back. If you omit it, the server assigns one for you (1, 2, 3, ...) and the reply carries that. |
| `cmd` | yes      | Command name + arguments, exactly as documented below.         |

Rules:

- One command per line. Lines longer than **1024 characters** are rejected.
- Unknown command names are rejected with an `error` reply listing the allowed set.
- The queue is unbounded — commands are never rejected for "queue full".

### Reply format (server → client)

Every reply is one JSON object per line:

```jsonc
{"id":1,"status":"accepted","queue":2}   // command was taken; queue = how many tasks are now waiting
{"id":1,"status":"done","message":"task finished"}        // task completed successfully
{"id":1,"status":"done","message":"cancelled by 'stop'"}  // task was aborted by a stop/cancel command
{"id":1,"status":"error","message":"..."}                 // command failed; message explains why
```

- `accepted` — the command was validated and queued (control commands are
  accepted the same way and execute within one tick).
- `done` — the task reached its end (success, timeout, no-activity, or cancellation).
- `error` — the command was not executed (bad syntax, Baritone rejected it,
  Baritone not installed, world unloaded).
- Every reply except `hello` contains an `id`: the one you sent, or a
  server-assigned counter value if you omitted it. To correlate replies with
  requests when you skip `id`, match by `status` and ordering (replies arrive
  in command order per connection).

### Execution model (important)

1. **One task runs at a time.** Task commands run strictly in the order you
   sent them, one per Minecraft tick when the previous task is finished.
2. **Control commands never wait.** `stop`, `cancel`, `set` execute
   immediately (next tick), even while a task is running.
3. **Completion detection.** The bridge watches Baritone's process state.
   You get `done` when:
   - the task stops moving and stays idle briefly (normal completion), or
   - no activity was ever observed within ~5 seconds (the goal may already be
     satisfied or unreachable — message says so), or
   - a `stop`/`cancel` command was executed (message: `cancelled by '...'`), or
   - the task ran continuously for 20 minutes (`commandTimeoutTicks` default
     24000 ticks) — you get `error` in that case.
4. Baritone's own chat feedback (e.g. "no path found") is **not** forwarded;
   rely on the `status`/`message` in replies.

---

## 2. Task commands (queued, one at a time)

These start long-running Baritone activities. The next task starts only after
the previous one reports `done`/`error`.

### 2.1 `goto` — walk to a position or a block

Syntax (coordinates support `~` relative offsets):

```text
goto <x> <y> <z>     walk to exact coordinates
goto <x> <z>         walk to x,z at the current Y level
goto <y>             walk to a Y level (e.g. go mining at y=-58)
goto <block>         walk to the nearest block of this type, wherever it is
```

Examples:

```json
{"cmd":"goto 100 64 200"}
{"cmd":"goto ~ ~ -58"}
{"cmd":"goto diamond_ore"}
```

### 2.2 `mine` — find and mine blocks

Syntax — the optional quantity comes **first** (0 or omitted = mine forever):

```text
mine <block> [<block> ...]          mine the named blocks until none are nearby-ish
mine <quantity> <block> [<block> ...]   stop after collecting quantity blocks
```

Block names use Minecraft ids: `iron_ore`, `deepslate_diamond_ore`,
`minecraft:ancient_debris`, ...

Examples:

```json
{"cmd":"mine iron_ore"}
{"cmd":"mine 64 iron_ore"}
{"cmd":"mine 10 diamond_ore deepslate_diamond_ore"}
```

Note: `mine 0 <block>` means unlimited; `mine <block>` also means unlimited.
Always give a quantity if you want the task to end on its own.

### 2.3 `follow` — follow entities

Syntax:

```text
follow players                    follow all nearby players
follow entities                   follow all nearby living entities
follow player <username> [...]    follow specific player(s)
follow entity <entity_id> [...]   follow specific entity type(s), e.g. cow, skeleton
```

Examples:

```json
{"cmd":"follow players"}
{"cmd":"follow player Steve"}
{"cmd":"follow entity skeleton"}
```

Note: following never completes on its own — use `stop` to end it
(otherwise it ends at the 20-minute timeout).

### 2.4 `farm` — harvest and replant nearby crops

Syntax:

```text
farm               farm crops around the current position (default range)
farm <range>       farm crops within <range> blocks
```

Examples:

```json
{"cmd":"farm"}
{"cmd":"farm 64"}
```

### 2.5 `build` — build a schematic

Syntax:

```text
build <file>                  build schematic <file> at your current position
build <file> <x> <y> <z>      build it at specific coordinates
```

The file must exist in the `schematics/` folder of the game directory
(`run/schematics/` in the dev environment) with a supported extension
(`.schem`, `.litematic`, `.schematic`, ...).

Examples:

```json
{"cmd":"build house.schem"}
{"cmd":"build house.schem 120 64 210"}
```

### 2.6 `explore` — go explore unvisited terrain

Syntax:

```text
explore              explore outward from the current position
explore <x> <z>      explore in the direction of the given XZ point
```

Examples:

```json
{"cmd":"explore"}
{"cmd":"explore 5000 5000"}
```

---

## 3. Control commands (never queued, execute immediately)

These bypass the task queue and work **while a task is running**. They reply
with `done` ("executed") within a tick or two.

### 3.1 `stop` — stop everything

Stops Baritone's current activity. If a queued task was running, it is
reported `done` with message `cancelled by 'stop'`.

```json
{"cmd":"stop"}
```

### 3.2 `cancel` — alias of `stop`

```json
{"cmd":"cancel"}
```

### 3.3 `set` — change a Baritone setting at runtime

Syntax:

```text
set <setting> <value>     change a setting, e.g. set allowBreak true
set list                  list all settings (output goes to Minecraft chat)
set modified              list changed settings
```

Examples:

```json
{"cmd":"set allowBreak true"}
{"cmd":"set allowPlace false"}
```

Useful settings for automation: `allowBreak`, `allowPlace`, `allowSprint`,
`allowInventory` (lets Baritone mine blocks it picks up while inventory is
open), `legitMine`.

---

## 4. Common recipes

### Mine 32 iron ore, then come home

Send both lines; the bridge runs them in order:

```json
{"cmd":"mine 32 iron_ore"}
{"cmd":"goto 0 64 0"}
```

### Abort whatever is happening

```json
{"cmd":"stop"}
```

### A full session

```text
C → S: (connect)
S → C: {"status":"hello","message":"baritone socket bridge ready","queue":0}
C → S: {"cmd":"mine 5 diamond_ore"}
S → C: {"id":1,"status":"accepted","queue":1}
S → C: {"id":1,"status":"done","message":"task finished"}
C → S: {"cmd":"goto 120 64 -300"}
S → C: {"id":2,"status":"accepted","queue":1}
C → S: {"cmd":"stop"}
S → C: {"id":3,"status":"done","message":"executed"}
S → C: {"id":2,"status":"done","message":"cancelled by 'stop'"}
```

---

## 5. Testing without Minecraft

```sh
nc 127.0.0.1 5555
{"cmd":"goto 100 64 200"}
```

or:

```sh
python3 tools/test_client.py "mine 5 iron_ore"
python3 tools/test_client.py        # interactive mode
```
