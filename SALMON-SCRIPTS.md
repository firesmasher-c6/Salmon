# Salmon Scripts Language Guide

Salmon Scripts (`.slms`) are a small, Luau-inspired language for automating
trusted server tasks. The language includes variables, expressions, functions,
conditionals, loops, console commands, block changes, logging, and delays.
This guide describes the syntax currently implemented by Salmon; it is not a
claim of full Luau compatibility.

> **Security warning:** `run command`, `command(...)`, and scripts generally
> have server-console authority. A script can affect every player and modify
> the world. Only run scripts you wrote or carefully reviewed. Back up worlds
> before running scripts that make changes.

## Quick start

1. Start Salmon once to create the `salmon-scripts/` directory.
2. Create a UTF-8 text file in that directory ending in `.slms`.
3. Add the required `meta(...)` block, then write script statements.
4. Restart to run scripts at startup, or run `/salmon scripts reload` as an
   operator to parse and run them again.

```slms
meta(
    name: "Welcome",
    version: 1.0.0,
    author: "ServerAdmin"
)

local playerMessage = "Welcome to Salmon!"
print(playerMessage)
run command say Welcome to Salmon!
```

## Metadata

Each script must begin with one metadata block. Leading blank lines and
`#` comments are allowed:

```slms
meta(
    name: "Example",
    version: 1.0.0,
    author: "ServerAdmin"
)
```

Required fields, each exactly once:

| Field | Rules |
| --- | --- |
| `name` | Non-empty, double-quoted text. Script names must be unique (case-insensitively) in a load pass. |
| `version` | Three numeric components, such as `1.0.0`; an optional `-suffix` or `+suffix` is allowed. |
| `author` | Non-empty, double-quoted text. |

Field order is flexible. A trailing comma is optional. The closing `)` is on a
line by itself. Invalid or missing metadata rejects that script; Salmon logs
the error and does not rewrite the file or invent replacement metadata.

## File and execution limits

- Only regular files whose names end in `.slms` are read.
- Files load in filename order; statements execute in source order.
- Maximum file size: **1 MiB**.
- Maximum source statements: **4096**.
- Maximum compiled instructions: **16,384**.
- Maximum execution instructions per script: **1,000,000**.
- Salmon runs up to **500 script instructions per server-thread task**, then
  yields for approximately one tick before resuming.
- Function calls may nest up to **32** levels.
- Parsing and file I/O run on `SalmonMC Thread`. Commands and world edits run on
  the Minecraft server thread.
- Runtime errors are logged with the script line. Salmon skips a failed
  ordinary instruction and continues where safe; a failed conditional or
  numeric-loop operation may skip that branch/loop, and numeric-loop overflow
  stops the script. A script that exceeds its global execution budget is
  stopped.

## Syntax overview

Statements are separated by a newline or semicolon. Blocks use `then`/`do`
and close with `end`, like Luau:

```slms
if condition then
    statement()
elseif otherCondition then
    statement()
else
    statement()
end
```

Keywords are case-insensitive. Variable and function names are case-sensitive.
The current implementation is intentionally smaller than Luau: it does not
have tables, indexing, closures, multiple returns, or general-purpose module
loading.

## Variables and values

Use `local name = expression` (or `let name = expression`) to declare a
script variable. Assignment can update a variable or create it if it has not
been set:

```slms
local greeting = "Hello"
local count = 3
count = count + 1
count += 2
```

Supported value types:

- **Number:** `12`, `3.5`, `-2`
- **String:** `"text"` or `'text'`
- **Boolean:** `true`, `false`
- **Nil:** `nil` (used for an absent value)

Strings support these escapes: `\n`, `\r`, `\t`, `\\`, `\"`, and `\'`.
Strings cannot span physical lines.

Assignment also supports `+=`, `-=`, `*=`, `/=`, `%=`, and `..=`. `local` and
`let` are aliases. Variables currently share the script execution environment
rather than having Luau lexical block scopes. Function parameters are
temporary to the function call.

## Operators and expressions

Precedence from highest to lowest:

| Operators | Meaning |
| --- | --- |
| `(...)`, function calls | Parentheses and calls |
| `not`, unary `-`, unary `#` | Logical not, negation, string length |
| `*`, `/`, `%` | Multiply, divide, remainder |
| `+`, `-` | Addition and subtraction |
| `..` | String concatenation |
| `==`, `~=`, `<`, `<=`, `>`, `>=` | Equality and comparisons |
| `and` | Logical and (short-circuiting) |
| `or` | Logical or (short-circuiting) |

Examples:

```slms
local message = "count = " .. tostring(2 + 3)
local allowed = count >= 2 and count ~= 5
local noMessage = not (message == "")
local messageLength = #message
```

Arithmetic operators require numbers. Ordered comparisons accept two numbers
or two strings. `..` converts values to text and concatenates them. In
conditions, only `false` and `nil` are false-like; zero and empty strings are
true-like. Division and remainder by zero are runtime errors.

## Conditionals

```slms
if count > 10 then
    print("large")
elseif count == 10 then
    print("exactly ten")
else
    print("small")
end
```

The first true branch executes; otherwise the `else` body runs if present.
`elseif` may be repeated or omitted.

## Loops and loop control

### While loop

```slms
local counter = 0
while counter < 3 do
    counter = counter + 1
    print(counter)
end
```

The condition is checked before each iteration.

### Numeric for loop

```slms
for i = 1, 5 do
    print(i)
end

for countdown = 10, 0, -2 do
    print(countdown)
end
```

The format is `for name = start, end[, step] do ... end`. The end value is
inclusive. The default step is `1`; a negative step counts down. A zero step
or non-finite value is an error.

### `break` and `continue`

`break` exits the nearest enclosing loop. `continue` skips to the next
iteration of the nearest enclosing loop:

```slms
for i = 1, 10 do
    if i == 2 then
        continue
    end
    if i > 5 then
        break
    end
    print(i)
end
```

Both are syntax errors outside a loop.

## Functions

Functions are defined with `function name(parameters)`, a single `return`
expression, and `end`:

```slms
function double(value)
    return value * 2
end

local result = double(6)
print(result)
```

Function definitions are available throughout their script, including before
the definition appears in source. Parameters are local to the call. Functions
currently return one expression value and are intended for calculations and
text construction; use script statements for commands and world edits.

Duplicate function names and duplicate parameter names are rejected. Calls
must provide exactly the declared number of arguments.

## Built-in functions

### General

| Function | Behavior |
| --- | --- |
| `print(value, ...)` | Writes tab-separated values to the Salmon server log. |
| `tostring(value)` | Converts a value to text (`nil` becomes `"nil"`). |
| `tonumber(value)` | Converts numeric text to a number; returns `nil` if it cannot parse. |
| `type(value)` | Returns `"nil"`, `"boolean"`, `"number"`, or `"string"`. |

### Math

| Function | Arguments | Result |
| --- | --- | --- |
| `math.abs(x)` | One number | Absolute value |
| `math.floor(x)` | One number | Rounded down |
| `math.ceil(x)` | One number | Rounded up |
| `math.sqrt(x)` | One number | Square root |
| `math.min(a, ...)` | One or more numbers | Smallest argument |
| `math.max(a, ...)` | One or more numbers | Largest argument |

### Strings

| Function | Arguments | Result |
| --- | --- | --- |
| `string.lower(text)` | One value | Lowercase text |
| `string.upper(text)` | One value | Uppercase text |
| `string.len(text)` | One value | Text length |

Built-in function names are case-insensitive. Function arguments are
case-sensitive data. Unknown functions and incorrect argument counts are
runtime errors.

Plugins can add callable functions through SalmonAPI. These extension
functions execute on the Minecraft server thread so they may safely interact
with in-game state; keep their work short and never perform blocking network or
file operations there. Register functions during plugin enable and unregister
them automatically when the plugin is disabled.

## Server and world instructions

### Run a console command

The existing line form is retained:

```slms
run command say Hello from Salmon
```

For dynamically assembled commands, call `command(text)` as a statement:

```slms
local announcement = "Server is " .. string.lower("READY")
command("say " .. announcement)
```

Commands execute through the server dispatcher as console. A leading slash is
not needed. Salmon logs whether the dispatcher recognized the command, but a
recognized command may still report its own argument or execution error.
Commands can change settings, players, and worlds.

### Set a block

The original syntax remains available:

```slms
set block minecraft:stone at 0 64 12 in world world
```

The function form accepts dynamic values:

```slms
local x = 4
setBlock("minecraft:gold_block", x, 64, 12, "world")
```

Arguments are material, X, Y, Z, and loaded world name. The material must be
a valid namespaced block (such as `minecraft:stone`); coordinates must be
whole 32-bit integers; and Y must be within world height. Salmon asynchronously
loads or generates the target chunk and its neighboring chunks before applying
the change on the server thread. Chunk generation consumes server resources.
Normal block physics apply and can cause neighbor updates.

Each `set block` / `setBlock(...)` statement waits for its block operation
before the script proceeds.

### Wait

```slms
print("Starting")
wait(20)
print("About one second later")
```

`wait(ticks)` accepts a whole number from **0** to **72,000** (one hour).
Salmon schedules approximately 50 milliseconds per requested tick on its
background worker and resumes the script on the server thread. It is a delay,
not a guarantee of exact Minecraft tick timing.

## Comments and formatting

Blank lines are ignored. Lines whose first non-whitespace character is `#` are
comments:

```slms
# This line is ignored.
local value = 1
```

Inline comments are not supported; keep comments on their own lines.

Most instructions may be separated with `;`, though `run command` is a
line-oriented form and should occupy its own line. Keep one command per line.
`set block` is also traditionally one line; prefer `setBlock(...)` for
expressions and variables.

## Reloading and startup order

Scripts run at startup after their text has been parsed and validated. To
manually reload:

```text
/salmon
/salmon scripts reload
```

Reload **runs** scripts again; it is not a syntax-only validation command.
Console commands and block edits may repeat. Temporarily remove one-time
scripts before reload if repeating their effects would be unsafe.

## Current language boundaries

Salmon's language is inspired by Luau but is not a Luau interpreter. Current
features do not include tables, table literals, indexing, closures, anonymous
functions, multiple return values, `repeat ... until`, generic iteration,
standard Luau libraries, imports, event callbacks, arbitrary Java access, or
asynchronous plugin-specific APIs. Script functions are expression-returning
helpers; commands, waits, and block edits must be statements in the main script.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Script skipped for metadata | Check `meta(`, all three fields, quoting, version, and closing `)`. |
| Unknown variable | Declare it with `local`/`let` or assign it before reading it. |
| Unknown function | Check spelling and capitalization for user-defined functions; verify built-in name and arguments. |
| `break`/`continue` rejected | Ensure the statement is inside a `while` or numeric `for` loop. |
| Numeric error | Use numbers for arithmetic and whole 32-bit coordinates for blocks; avoid division by zero. |
| Command not recognized | Check command spelling, available plugin commands, and required arguments. |
| World not loaded | Use the exact name of a world currently loaded by the server. |
| Chunk load takes time | Block placement may generate nearby chunks; avoid distant coordinates on production worlds. |
| Script stops on execution limit | Reduce loop work or split automation across scripts; the limit prevents endless loops. |

Errors include the script name and source line in the server log. The
server's `salmon-scripts/README.txt` contains a short quick reference.
