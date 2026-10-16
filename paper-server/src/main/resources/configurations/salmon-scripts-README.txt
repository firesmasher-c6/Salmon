Salmon Scripts (.slms)
======================

Scripts in this directory run automatically once during server startup, after
the server has loaded its worlds and startup listeners. They run in filename
order. Scripts are parsed on SalmonMC Thread; game changes and commands run
on the Minecraft server thread.

Only .slms files are read. Each script must start with this required metadata
block. Missing or invalid metadata rejects the script; Salmon logs the error
and never edits the script file.

meta(
    name: "MyScript",
    version: 1.0.0,
    author: "MyName"
)

Supported instructions, one per line:

run command say Hello from Salmon
set block minecraft:stone at 0 64 12 in world world

Commands execute as the server console with full console permissions. Only
place scripts in this directory if you trust their author and every command
they contain. Commands can change server settings, worlds, and player state.
Block instructions asynchronously load or generate the target chunk and its
neighbors before changing the block, so scripts can target unloaded chunks
without forcing a synchronous neighbor-chunk load. Generation still costs
server resources. Each script waits for its block instruction to finish
before continuing, and changes apply normal block physics.

Blank lines and lines starting with # are ignored. If an instruction fails,
Salmon logs its file and line and continues with later instructions.

Operators can run /salmon for status or /salmon scripts reload to reload and
run the scripts again. Reloading runs console-authority commands again.
