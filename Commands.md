# Baritone Commands — Complete Reference (v1.11.2)

Every command registered by Baritone v1.11.2 (Minecraft 1.21.1) — 42 command
names including aliases — cross-checked against the in-game `#help` output and
the Baritone source. In-game, commands are prefixed with `#` or `.b`
(e.g. `#mine 5 iron_ore`).

The **Baritone Socket Bridge** exposes a subset of these over TCP — see
`AI_COMMANDS.md` for the socket protocol. Commands marked with `*socket` are
available through the bridge.

---

## Execution control

| Command      | Aliases              | Description                                    |
|--------------|----------------------|------------------------------------------------|
| `cancel` *socket | `c`, `stop`      | Cancel the current task                        |
| `forcecancel`    | —                | Force-cancel all processes (use when `cancel` doesn't stick) |
| `pause`          | `p`, `paws`      | Pause all Baritone movement until `resume`     |
| `resume`         | `r`, `unpause`, `unpaws` | Resume after `pause`                   |
| `paused`         | —                | Show whether Baritone is currently paused      |

```text
> cancel
> forcecancel
> pause
> resume
> paused
```

## Pathing & goals

| Command        | Aliases          | Description                                     |
|----------------|------------------|-------------------------------------------------|
| `goto` *socket | —                | Go to a coordinate or block                     |
| `path`         | —                | Start pathing to the currently set goal         |
| `goal`         | —                | Set (without pathing) or clear the goal         |
| `come`         | —                | Walk towards the camera's current position      |
| `thisway`      | `forward`        | Make a goal N blocks ahead in your facing direction |
| `tunnel`       | —                | Tunnel forward in a 1x2, or custom size         |
| `surface`      | `top`            | Path out of caves/mines to the surface          |
| `axis`         | `highway`        | Set a goal to the closest world axis/highway    |
| `invert`       | —                | Run away from the current goal                  |
| `explore` *socket | —             | Explore unvisited chunks                        |
| `explorefilter`| —                | Explore chunks listed in a JSON file            |
| `elytra`       | —                | Elytra flying towards the current goal          |

```text
> goto <x> <y> <z>              also: goto <x> <z> | goto <y> | goto <block>
> path
> goal                          goal at your position
> goal <reset|clear|none>       erase the goal
> goal <y>                      goal <x> <z>                goal <x> <y> <z>
> come
> thisway <distance>
> tunnel                        1x2 tunnel
> tunnel <height> <width> <depth>
> surface
> axis
> invert
> explore                       explore [x z]
> explorefilter <path> [invert]
> elytra                        elytra reset | elytra repack | elytra supported
```

Coordinates accept `~` relative offsets everywhere a position is expected.

## Gathering & interaction

| Command       | Aliases | Description                                        |
|---------------|---------|----------------------------------------------------|
| `mine` *socket| —       | Search for and mine blocks                         |
| `farm` *socket| —       | Harvest and replant nearby crops                   |
| `follow` *socket | —    | Follow entities                                    |
| `pickup`      | —       | Walk to and pick up dropped items                  |
| `build` *socket  | —    | Build a schematic file                             |
| `schematica`  | —       | Build the schematic currently loaded in Schematica |
| `litematica`  | —       | Build the schematic currently loaded in Litematica |
| `blacklist`   | —       | Blacklist the closest block so `mine` skips it     |

```text
> mine <block> [<block> ...]            mine until none left (nearby search)
> mine <quantity> <block> [<block> ...] stop after quantity (0 or omitted = unlimited)
> farm                                  farm [range] | farm <range> <waypoint>
> follow players | follow entities
> follow player <username> [<username> ...]
> follow entity <entity_id> [<entity_id> ...]
> pickup                                pickup anything
> pickup <item1> <item2> <...>
> build <file> [x y z]                  schematic from the schematics/ folder
> schematica
> litematica                            litematica <#> (choose between loaded ones)
> blacklist
```

## Selection (WorldEdit-like)

| Command             | Aliases        | Description                    |
|---------------------|----------------|--------------------------------|
| `sel`               | `selection`, `s` | Fill/build shapes in a selection |

```text
> sel pos1/p1/1 [<x> <y> <z>]      set position 1 (here, or relative)
> sel pos2/p2/2 [<x> <y> <z>]      set position 2
> sel clear/c                      clear the selection
> sel undo/u                       undo last action
> sel set/fill/s/f [block]         fill the selection
> sel walls/w [block]              fill only walls
> sel shell/shl [block]            walls + ceiling + floor
> sel sphere/sph [block]           filled sphere in the selection
> sel hsphere/hsph [block]         hollow sphere
> sel cylinder/cyl [block] [axis]  filled cylinder (axis default: y)
```

## Information & status (output goes to chat, not the socket)

| Command   | Aliases | Description                              |
|-----------|---------|------------------------------------------|
| `help`    | `?`     | List commands or show help for one       |
| `proc`    | —       | Show active process state                |
| `eta`     | —       | Show ETA of the current path, if present |
| `find`    | —       | Report known positions of blocks         |
| `version` | —       | Show Baritone version                    |
| `waypoints` | `waypoint`, `wp` | Manage saved waypoints         |

```text
> help                      help <command>
> proc
> eta
> find <block> [<block> ...]
> version
> wp list                   wp list <tag>
> wp save [tag] [name] [pos]
> wp info <tag/name>        wp show <tag/name>
> wp delete <tag/name>      wp restore <n>       wp clear <tag>
> wp goal <tag/name>        wp goto <tag/name>
```

## Settings & utility

| Command     | Aliases               | Description                              |
|-------------|-----------------------|------------------------------------------|
| `set` *socket | `setting`, `settings` | View or change Baritone settings       |
| `modified`  | `mod`, `baritone`, `modifiedsettings` | List modified settings (alias of `set modified`) |
| `reset`     | —                     | Reset all settings or just one (alias of `set reset`) |
| `click`     | —                     | Open the click-aim overlay (GUI)         |
| `gc`        | —                     | Suggest a garbage collection run         |
| `render`    | —                     | Fix glitched chunks                      |
| `repack`    | `rescan`              | Re-cache the chunks around you           |
| `reloadall` | —                     | Reload Baritone's cache for this world   |
| `saveall`   | —                     | Save Baritone's cache for this world     |

```text
> set                       same as `set list`
> set list [page]           view all settings
> set modified [page]       view changed settings
> set reset                 reset all settings
> set reset <setting>       reset one setting
> set <setting> <value>     e.g. set allowBreak true
> click
> gc
> render
> repack
> reloadall
> saveall
```

## Convenience aliases (waypoint shortcuts)

| Command    | Aliases | Description                            |
|------------|---------|----------------------------------------|
| `sethome`  | —       | Save your home waypoint at this position (alias of `waypoints save home`) |
| `home`     | —       | Path back to your home waypoint (alias of `waypoints goto home`) |

```text
> sethome
> home
```

---

### Bridge coverage summary

- **Exposed via socket bridge (`*socket`):** `goto`, `mine`, `follow`, `farm`,
  `build`, `explore` (tasks) and `stop`/`cancel`, `set` (instant control).
- **Not exposed:** everything else above. Passing-through candidates that need
  no extra plumbing: `forcecancel`, `pause`, `resume`, `come`, `thisway`,
  `tunnel`, `surface`, `axis`, `goal`, `path`, `pickup`, `blacklist`,
  `explorefilter`. Info commands (`proc`, `eta`, `find`, `waypoints`, ...)
  print to Minecraft chat, which the bridge does not capture.
