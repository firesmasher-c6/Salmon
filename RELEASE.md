# Salmon 1.21.11 - 6e-005

Salmon `6e-005` fixes built-in command registration after plugin command
lifecycles and server aliases are initialized. `/salmon` and `/smp` are
available on startup and after reload, with tab completion for their
subcommands.

## Download

Download `salmon-1.21.11-6e-005.jar` from this GitHub release.

## Highlights

- Registers `/salmon` and `/smp` after plugin lifecycle commands and aliases,
  then refreshes online players' command trees.
- Adds tab completion for the supported `/salmon` and `/smp` subcommands.
- `/salmon` exposes Salmon status, scripts, and Modrinth plugin services.
- `/smp` manages MOTD reload and maintenance mode / allowlisting.
- Redstone Optimization defaults to Paper's Alternate Current engine, which
  greatly speeds up redstone dust updates in large circuits. It changes dust
  update order, so location-dependent or timing-sensitive contraptions may
  behave differently. Configure `redstone.implementation` in
  `config/salmon-opts.yml` to choose `alternate_current`, `eigencraft`,
  `vanilla`, or `paper`.
- MOTD Builder in `salmon.yml`; configured MOTDs override `server.properties`,
  support `&` color codes and `{online}` / `{max}` placeholders.
- Maintenance mode uses a separate UUID/name allowlist. Add staff before
  enabling it.
- Per-chunk caps, optional persistent-mob cleanup, plugin metrics, SalmonAPI,
  `salmon-plugin.yml`, Chunky setup, and Modrinth plugin installation.

## Install and start

1. Install Java 21 or later.
2. Put `salmon-1.21.11-6e-005.jar` in an empty server directory.
3. Start the server once to create its configuration files.
4. Review `salmon.yml` and `config/salmon-opts.yml`, accept the Minecraft EULA
   in `eula.txt`, then restart.

   ```sh
   java -Xms1G -Xmx4G -jar salmon-1.21.11-6e-005.jar --nogui
   ```

You can confirm command registration at the server console with `salmon` or
`smp`. Before enabling maintenance, add staff with
`/smp maintenance whitelist add <uuid|playername>`.

Back up worlds before enabling entity cleanup or running scripts that change
the world. Modrinth installations add third-party plugin code to the server;
review projects and their dependencies before installing. Newly installed
plugins require a restart.

## Source

https://github.com/firesmasher-c6/Salmon/tree/ver/1.21.11
