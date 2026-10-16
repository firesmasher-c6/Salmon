# Salmon Scripts Language Guide

Salmon Scripts are plain-text `.slms` files that let a server administrator
run console commands and make simple world edits. This page documents the
language currently implemented by Salmon's script parser. It is a small,
strict instruction format—not a general-purpose programming language.

> **Trust warning:** Every `run command` instruction executes as the server
> console with full console permissions. A script can change server settings,
> affect players, or modify worlds. Only use scripts you wrote or have fully
> reviewed. Keep backups before running scripts that change a world.

## Quick start

1. Start Salmon once to create the `salmon-scripts/` directory.
2. Create a UTF-8 text file in that directory with the `.slms` extension.
3. Add the required `meta(...)` block, then one supported instruction per line.
4. Restart Salmon to run scripts at startup, or use `/salmon scripts reload`
   as an operator to read and run them again.

Example file, `salmon-scripts/welcome.slms`:

```slms
meta(
    name: "Welcome",
    version: 1.0.0,
    author: "ServerAdmin"
)

# This command runs as the server console.
run command say Welcome to the server!

# This block change can generate chunks around the target location.
set block minecraft:stone at 0 64 12 in world world
```

## File format at a glance

- The filename must end exactly in `.slms`.
- Salmon reads regular files in the script directory and sorts them by filename.
- Script names in metadata must be unique, ignoring letter case, among scripts
  loaded in the same pass.
- A file must be no larger than **1 MiB**.
- A script may contain at most **4096 instructions**.
- Blank lines and lines whose first non-whitespace character is `#` are ignored.
- Instructions are processed in source order. Scripts run in filename order.
- Salmon parses scripts on `SalmonMC Thread`; commands and world modifications
  run on the Minecraft server thread.
- Invalid metadata or an unknown instruction causes Salmon to skip that file
  and report the error in the server log. Salmon does not rewrite the file or
  invent fallback metadata.
- If an instruction fails during execution, Salmon logs the script and line,
  then continues with the remaining instructions.

## Required metadata

Every script starts with one `meta(...)` block, after any leading blank lines
or comments:

```slms
meta(
    name: "MyScript",
    version: 1.0.0,
    author: "MyName"
)
```

The block must contain these three fields exactly once:

| Field | Meaning | Format |
| --- | --- | --- |
| `name` | Human-readable script name; also used to detect duplicates | Non-empty double-quoted text |
| `version` | Script version label | Three numeric components, such as `1.0.0`; an optional `-suffix` or `+suffix` is accepted |
| `author` | Script author | Non-empty double-quoted text |

Metadata field order does not matter. A comma at the end of a field is
optional. Field names are lowercase and must be exactly `name`, `version`, and
`author`. Each field must occupy its own line, and the closing `)` must be on
its own line.

Examples of accepted version values:

```text
1.0.0
2.4.1-beta
1.2.3+build.7
```

Examples of metadata errors include a missing field, a repeated field, an
unquoted name or author, an empty name or author, a malformed version, or a
missing closing parenthesis. Any of these rejects the script.

## Instruction dictionary

The parser currently supports exactly two instruction forms.

### `run command`

```text
run command <Minecraft or plugin command and its arguments>
```

Runs the text after `run command ` through Salmon's server command dispatcher,
using the console sender. Do not include a leading slash.

Examples:

```slms
run command say The server is ready!
run command time set day
run command gamerule doMobSpawning true
```

**Authority and effects:** Commands run with console authority, not the
script author's or a player’s permissions. Commands may alter the server,
worlds, and players. A successful dispatch only means the command was accepted
by the command dispatcher; command-specific errors and effects are controlled
by the command itself.

**Syntax notes:**

- The instruction prefix is case-insensitive.
- The command text must not be empty.
- Use one instruction per line. Multi-line command bodies are not supported.
- A command can contain spaces; Salmon passes the remaining line as the
  command text.

### `set block`

```text
set block <namespace:block> at <x> <y> <z> in world <world name>
```

Sets one block to the requested Minecraft material at integer block
coordinates in a loaded world. Example:

```slms
set block minecraft:gold_block at 0 64 12 in world world
```

**Arguments:**

| Argument | Description |
| --- | --- |
| `<namespace:block>` | Namespaced block material, for example `minecraft:stone` |
| `<x> <y> <z>` | Signed integer block coordinates; negative coordinates are allowed |
| `<world name>` | Name of a world currently loaded by the server; everything after `in world ` is treated as the name |

Salmon validates that the world exists, the Y coordinate is within that
world's height range, and the material is a block. If the target or nearby
chunks are not loaded, Salmon asynchronously loads or generates the target
chunk and its neighboring chunks before changing the block. Chunk generation
can consume significant CPU, memory, and disk resources.

The change is applied on the Minecraft server thread and uses normal block
physics. A block edit may therefore update neighboring blocks or trigger
redstone and other game behavior. Each script waits for its block operation
to complete before continuing to its next instruction.

**Syntax notes:**

- The instruction keywords are case-insensitive.
- A namespace is required; use `minecraft:stone`, not just `stone`.
- Coordinates must be whole decimal integers within Java's integer range.
- The world name is case-sensitive in practice and must match a loaded world.

## Comments and whitespace

Blank lines are ignored. A line is a comment only when its first non-whitespace
character is `#`:

```slms
# This entire line is ignored.

run command say This line is an instruction.
```

Inline comments are not part of the language. Text after an instruction is
passed as part of that instruction, so put comments on their own lines.
Whitespace around a complete line is ignored. Instructions cannot span
multiple lines.

## Order, startup, and reload behavior

At startup, Salmon discovers and parses `.slms` files on `SalmonMC Thread`.
Valid scripts are queued for execution on the Minecraft server thread in
filename order. Instructions within a script run in their written order.
When a block instruction needs chunk loading, Salmon waits asynchronously for
the chunks, applies the block on the server thread, then continues the script.

Use:

```text
/salmon
/salmon scripts reload
```

Reload reads and **runs** the scripts again; it is not a syntax-only check.
Commands and block edits may therefore repeat. Make a backup and consider
removing or temporarily moving scripts with one-time effects before reloading.

## Unsupported language features

The current parser does not implement variables, expressions, conditions,
loops, functions, imports, event triggers, delays, scheduled/repeating
instructions, arbitrary Java, or a separate permission model for scripts.
Anything other than the metadata block, blank/comment lines, `run command`,
and `set block` is rejected as an unrecognized instruction.

## Troubleshooting

| Log or symptom | What to check |
| --- | --- |
| Script is skipped for invalid metadata | Check the opening `meta(` block, all three fields, quotes, version format, commas, and closing `)` |
| Duplicate script name | Give each script a different metadata `name`; names are compared without regard to case |
| Unrecognized instruction | Use only the exact `run command` or `set block` forms documented above |
| Command is not recognized | Check the command spelling, installed plugins, command availability, and required arguments |
| World is not loaded | Use a currently loaded world's exact name |
| Y coordinate is outside world height | Choose a Y value at or above the world's minimum and below its maximum |
| Material is not a block | Use a valid namespaced block material, such as `minecraft:stone` |
| Chunk loading takes time | The block instruction may generate the target and neighboring chunks; avoid remote coordinates on a production world |

Salmon reports script parsing and execution details in the server log. The
`salmon-scripts/README.txt` file in the server directory contains a short
in-game-server reference.
